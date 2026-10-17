"""Test-side loader: writes an ImportPlan into the real schema.

Insert ORDER matters: the overdraft guard checks the running total in insertion order,
so incomes (and everything that adds money) go in before expenses, adjustments included.
Every insert is ON CONFLICT DO NOTHING on the deterministic id, so loading is idempotent.
"""

import uuid

from app.legacy.mapper import NAMESPACE, ImportPlan


def load_plan(conn, plan: ImportPlan) -> uuid.UUID:
    user_id = uuid.uuid5(NAMESPACE, "user:" + plan.owner_display_name)
    conn.execute(
        "INSERT INTO users (id, email, display_name, timezone) VALUES (%s, %s, %s, %s) "
        "ON CONFLICT DO NOTHING",
        (user_id, f"import-{user_id}@example.test", plan.owner_display_name, plan.timezone),
    )
    for w in plan.workspaces:
        conn.execute(
            "INSERT INTO workspaces (id, owner_user_id, name, currency_code) "
            "VALUES (%s, %s, %s, %s) ON CONFLICT DO NOTHING",
            (w.id, user_id, w.name, w.currency_code),
        )
        conn.execute(
            "INSERT INTO workspace_members (workspace_id, user_id) VALUES (%s, %s) "
            "ON CONFLICT DO NOTHING",
            (w.id, user_id),
        )
    for a in plan.accounts:
        conn.execute(
            "INSERT INTO accounts (id, workspace_id, name, type, currency_code, opening_balance) "
            "VALUES (%s, %s, %s, %s, %s, %s) ON CONFLICT DO NOTHING",
            (a.id, a.workspace_id, a.name, a.type, a.currency_code, a.opening_balance),
        )
    for c in plan.categories:
        conn.execute(
            "INSERT INTO categories (id, workspace_id, name, kind) VALUES (%s, %s, %s, %s) "
            "ON CONFLICT DO NOTHING",
            (c.id, c.workspace_id, c.name, c.kind),
        )
    ordered = sorted(
        plan.transactions, key=lambda t: (t.kind != "income", t.occurred_at, t.legacy_id or 0)
    )
    for t in ordered:
        conn.execute(
            "INSERT INTO transactions (id, workspace_id, account_id, kind, amount, category_id, title, "
            "occurred_on, occurred_at, source) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING",
            (
                t.id,
                t.workspace_id,
                t.account_id,
                t.kind,
                t.amount,
                t.category_id,
                t.title,
                t.occurred_on,
                t.occurred_at,
                t.source,
            ),
        )
    for r in plan.recurring_rules:
        conn.execute(
            "INSERT INTO recurring_rules (id, workspace_id, account_id, category_id, kind, amount, title, "
            "frequency, anchor_on, ends_on, timezone, status) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) "
            "ON CONFLICT DO NOTHING",
            (
                r.id,
                r.workspace_id,
                r.account_id,
                r.category_id,
                r.kind,
                r.amount,
                r.title,
                r.frequency,
                r.anchor_on,
                r.ends_on,
                r.timezone,
                r.status,
            ),
        )
    return user_id
