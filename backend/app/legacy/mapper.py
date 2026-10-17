"""Map a legacy Mulya (Android v2.x) backup to rows for the new schema.

PURE: no database, no files, no clock. `map_backup(dict) -> ImportPlan`.
It exists for two reasons (MLY-008, and the owner-only import of decision D-19):
  1. golden fixtures: prove real legacy data fits the new schema and reconciles;
  2. later, the one-off import of the owner's own data.

Everything surprising becomes an `Issue` instead of an exception, so a plan can be
inspected before anything is written. Severity "error" means: do NOT import this plan.

Rules (see docs/CONVENTIONS.md):
  * no personal data survives: only a display name is kept (CONVENTIONS §8);
  * ids are DETERMINISTIC (uuid5 of the legacy id) so re-running is idempotent;
  * balances are re-derived: opening = legacy balance - sum(live transactions);
    if that would be negative (new accounts cannot open below zero) the opening
    is 0 and an explicit `adjustment` EXPENSE is added instead (CONVENTIONS §5.3);
  * a legacy account that ends NEGATIVE cannot be represented (strict no-overdraft),
    which is reported as an error issue.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import UTC, date, datetime, time
from decimal import Decimal
from zoneinfo import ZoneInfo

# Fixed namespace: the same legacy row always maps to the same new id.
NAMESPACE = uuid.UUID("6f1d0c1e-5b7a-4c53-9c1e-2f0a7e9d4b10")

MODES = {"BANK": ("Bank", "bank"), "CASH": ("Cash", "cash")}
FREQUENCIES = {"DAILY": "daily", "WEEKLY": "weekly", "MONTHLY": "monthly", "YEARLY": "yearly"}
SYMBOL_TO_CURRENCY = {"₹": "INR", "$": "USD", "€": "EUR", "£": "GBP"}
PII_FIELDS = ("name", "dob", "gender", "mobile", "city", "state", "imageUri")
CENT = Decimal("0.01")
NOISE = Decimal("0.000001")  # float representation noise below this is not a data problem


@dataclass
class Issue:
    severity: str  # "error" | "warning" | "info"
    code: str
    message: str


@dataclass
class PlannedWorkspace:
    id: uuid.UUID
    legacy_id: int
    name: str
    currency_code: str


@dataclass
class PlannedAccount:
    id: uuid.UUID
    workspace_id: uuid.UUID
    legacy_profile_id: int
    legacy_mode: str
    name: str
    type: str
    currency_code: str
    opening_balance: Decimal


@dataclass
class PlannedCategory:
    id: uuid.UUID
    workspace_id: uuid.UUID
    name: str
    kind: str  # "income" | "expense"


@dataclass
class PlannedTransaction:
    id: uuid.UUID
    workspace_id: uuid.UUID
    account_id: uuid.UUID
    category_id: uuid.UUID | None
    kind: str  # "income" | "expense"
    amount: Decimal
    title: str
    occurred_on: date
    occurred_at: datetime
    source: str  # "import" | "adjustment"
    legacy_id: int | None


@dataclass
class PlannedRecurringRule:
    id: uuid.UUID
    workspace_id: uuid.UUID
    account_id: uuid.UUID
    category_id: uuid.UUID | None
    kind: str
    amount: Decimal
    title: str
    frequency: str
    anchor_on: date
    ends_on: date | None
    status: str  # "active" | "paused"
    timezone: str


@dataclass
class ImportPlan:
    owner_display_name: str
    timezone: str
    workspaces: list[PlannedWorkspace] = field(default_factory=list)
    accounts: list[PlannedAccount] = field(default_factory=list)
    categories: list[PlannedCategory] = field(default_factory=list)
    transactions: list[PlannedTransaction] = field(default_factory=list)
    recurring_rules: list[PlannedRecurringRule] = field(default_factory=list)
    unmapped: dict[str, int] = field(default_factory=dict)
    issues: list[Issue] = field(default_factory=list)
    legacy_balances: dict[tuple[int, str], Decimal] = field(default_factory=dict)

    @property
    def blocking_issues(self) -> list[Issue]:
        return [i for i in self.issues if i.severity == "error"]


@dataclass
class ReconRow:
    workspace: str
    account: str
    legacy_balance: Decimal
    derived_balance: Decimal

    @property
    def ok(self) -> bool:
        return self.legacy_balance == self.derived_balance


def _uid(*parts: object) -> uuid.UUID:
    return uuid.uuid5(NAMESPACE, ":".join(str(p) for p in parts))


def _clean(text: object) -> str:
    return " ".join(str(text or "").split())


def _money(value: object, plan: ImportPlan, where: str) -> Decimal:
    """Legacy amounts are floats. Convert via their shortest repr and keep 2 decimals."""
    number = Decimal(str(value))
    rounded = number.quantize(CENT)
    if abs(number - rounded) > NOISE:
        plan.issues.append(
            Issue(
                "warning",
                "amount_rounded",
                f"{where}: {number} has more than 2 decimals; imported as {rounded}",
            )
        )
    return rounded


def map_backup(raw: dict, *, timezone: str = "Asia/Kolkata") -> ImportPlan:
    if (
        not isinstance(raw, dict)
        or not isinstance(raw.get("transactions"), list)
        or not isinstance(raw.get("balances"), list)
    ):
        # ValueError (not TypeError) on purpose: callers treat any unusable input as "not a backup"
        raise ValueError("not a Mulya backup: 'transactions' and 'balances' lists are required")  # noqa: TRY004

    tz = ZoneInfo(timezone)
    prefs = raw.get("preferences") or {}
    profile = raw.get("profile") or {}
    plan = ImportPlan(
        owner_display_name=(_clean(profile.get("name")) or "Owner")[:80],
        timezone=timezone,
    )
    if raw.get("app") not in (None, "Mulya"):
        plan.issues.append(
            Issue("warning", "unexpected_app", f"backup says app={raw.get('app')!r}")
        )

    # -- personal data: report WHICH fields were dropped, never their values ----------
    dropped = [f"profile.{f}" for f in PII_FIELDS if profile.get(f)]
    if raw.get("profileImageBase64"):
        dropped.append("profileImageBase64")
    if dropped:
        plan.issues.append(Issue("info", "pii_dropped", "not imported: " + ", ".join(dropped)))

    # -- currency ------------------------------------------------------------------------
    symbol = prefs.get("CURRENCY_SYMBOL")
    currency = SYMBOL_TO_CURRENCY.get(symbol)
    if currency is None:
        currency = "INR"
        plan.issues.append(
            Issue(
                "warning",
                "currency_defaulted",
                f"currency symbol {symbol!r} not recognised; using INR",
            )
        )

    # -- workspaces (budget profiles) ----------------------------------------------------
    legacy_profiles = {p["id"]: p for p in (raw.get("budgetProfiles") or [])}
    deleted_ids = {pid for pid, p in legacy_profiles.items() if p.get("is_deleted")}

    referenced: set[int] = set()
    for key in ("balances", "transactions", "goals", "recurringTransactions", "pendingPlans"):
        for row in raw.get(key) or []:
            if "budget_profile_id" in row:
                referenced.add(row["budget_profile_id"])
    live_ids = sorted((set(legacy_profiles) | referenced) - deleted_ids)

    for pid in sorted(deleted_ids):
        plan.issues.append(
            Issue(
                "warning",
                "profile_deleted_skipped",
                f"budget profile {pid} is in the recycle bin; its rows are skipped",
            )
        )
    ws_by_legacy: dict[int, PlannedWorkspace] = {}
    for pid in live_ids:
        if pid in legacy_profiles:
            name = _clean(legacy_profiles[pid].get("name")) or f"Budget {pid}"
        else:
            name = "Primary Budget" if len(live_ids) == 1 else f"Budget {pid}"
            plan.issues.append(
                Issue(
                    "info",
                    "profile_synthesized",
                    f"budget profile {pid} is referenced but absent (older backup); created '{name}'",
                )
            )
        ws = PlannedWorkspace(_uid("ws", pid), pid, name, currency)
        ws_by_legacy[pid] = ws
        plan.workspaces.append(ws)

    # -- balances as stated by the legacy app (for reconciliation) -----------------------
    for b in raw["balances"]:
        if b["budget_profile_id"] in ws_by_legacy and b["type"] in MODES:
            plan.legacy_balances[(b["budget_profile_id"], b["type"])] = _money(
                b["amount"], plan, f"balance {b['type']}"
            )

    # -- accounts: one per (profile, mode) that has a balance or a transaction -----------
    def account_for(pid: int, mode: str) -> PlannedAccount | None:
        if mode not in MODES:
            return None
        aid = _uid("acct", pid, mode)
        for a in plan.accounts:
            if a.id == aid:
                return a
        label, atype = MODES[mode]
        acct = PlannedAccount(
            aid, ws_by_legacy[pid].id, pid, mode, label, atype, currency, Decimal(0)
        )
        plan.accounts.append(acct)
        return acct

    for pid, mode in sorted(plan.legacy_balances):
        account_for(pid, mode)

    # -- categories ----------------------------------------------------------------------
    cats: dict[tuple[uuid.UUID, str, str], PlannedCategory] = {}

    def category_for(ws: PlannedWorkspace, kind: str, raw_name: object) -> uuid.UUID | None:
        name = _clean(raw_name)
        if not name:
            return None
        key = (ws.id, kind, name.casefold())
        if key not in cats:
            cats[key] = PlannedCategory(
                _uid("cat", ws.legacy_id, kind, name.casefold()), ws.id, name, kind
            )
        if str(raw_name) != name:
            plan.issues.append(
                Issue(
                    "info",
                    "category_name_normalized",
                    f"category {str(raw_name)!r} imported as {name!r}",
                )
            )
        return cats[key].id

    typed_as_expense = 0
    for c in raw.get("categories") or []:
        for ws in ws_by_legacy.values():
            category_for(ws, "expense", c["name"])
        typed_as_expense += 1
    if typed_as_expense:
        plan.issues.append(
            Issue(
                "info",
                "categories_typed_as_expense",
                f"{typed_as_expense} legacy categories had no type; imported as expense "
                "(income categories are created from how transactions use them)",
            )
        )

    # -- transactions --------------------------------------------------------------------
    skipped_deleted = 0
    goal_contributions = 0
    for t in sorted(raw["transactions"], key=lambda r: (r["timestamp"], r["id"])):
        pid = t["budget_profile_id"]
        if pid not in ws_by_legacy:
            skipped_deleted += 1
            continue
        kind = {"INCOME": "income", "EXPENSE": "expense"}.get(t["transaction_type"])
        acct = account_for(pid, t["mode"])
        if kind is None or acct is None:
            plan.issues.append(
                Issue(
                    "error",
                    "unmappable_transaction",
                    f"transaction {t['id']}: type={t['transaction_type']!r} mode={t['mode']!r}",
                )
            )
            continue
        amount = _money(t["amount"], plan, f"transaction {t['id']}")
        if amount <= 0:
            plan.issues.append(
                Issue(
                    "error",
                    "non_positive_amount",
                    f"transaction {t['id']}: amount {amount} must be greater than 0",
                )
            )
            continue
        ws = ws_by_legacy[pid]
        if kind == "expense" and _clean(t.get("category")).casefold() == "goals":
            goal_contributions += 1
        at = datetime.fromtimestamp(t["timestamp"] / 1000, tz=UTC)
        plan.transactions.append(
            PlannedTransaction(
                id=_uid("tx", pid, t["id"]),
                workspace_id=ws.id,
                account_id=acct.id,
                category_id=category_for(ws, kind, t.get("category")),
                kind=kind,
                amount=amount,
                title=_clean(t.get("title")),
                occurred_on=at.astimezone(tz).date(),
                occurred_at=at,
                source="import",
                legacy_id=t["id"],
            )
        )
    if skipped_deleted:
        plan.issues.append(
            Issue(
                "warning",
                "transactions_of_deleted_profiles_skipped",
                f"{skipped_deleted} transaction(s) belong to deleted budget profiles",
            )
        )
    if goal_contributions:
        plan.issues.append(
            Issue(
                "warning",
                "goal_contribution_as_expense",
                f"{goal_contributions} expense(s) in category 'Goals' were savings-goal contributions; "
                "they are imported as ordinary expenses (goals/envelopes are not imported yet)",
            )
        )

    # -- opening balances: derived so that balance == the legacy balance -------------------
    for acct in plan.accounts:
        mine = [t for t in plan.transactions if t.account_id == acct.id and t.source == "import"]
        effect = sum((t.amount if t.kind == "income" else -t.amount for t in mine), Decimal(0))
        legacy = plan.legacy_balances.get((acct.legacy_profile_id, acct.legacy_mode))
        if legacy is None:
            legacy = Decimal(0)
            plan.legacy_balances[(acct.legacy_profile_id, acct.legacy_mode)] = legacy
            plan.issues.append(
                Issue(
                    "warning",
                    "balance_missing",
                    f"no balance row for {acct.name} of profile {acct.legacy_profile_id}; assumed 0",
                )
            )
        if legacy < 0:
            plan.issues.append(
                Issue(
                    "error",
                    "negative_legacy_balance",
                    f"{acct.name} of profile {acct.legacy_profile_id} ended at {legacy}; the new model blocks "
                    "overdrafts, so this account cannot be imported as-is (fix it in the old app first)",
                )
            )
        implied = legacy - effect
        if implied >= 0:
            acct.opening_balance = implied
            continue
        # Income in the history exceeds the final balance: the old app let the balance be
        # lowered outside the ledger ("Adjust balance"). Open at 0 and record the missing
        # spending as an explicit adjustment EXPENSE so balance == legacy balance.
        acct.opening_balance = Decimal("0.00")
        first_day = min((t.occurred_on for t in mine), default=date(1970, 1, 1))
        plan.transactions.append(
            PlannedTransaction(
                id=_uid("adjust", acct.legacy_profile_id, acct.legacy_mode),
                workspace_id=acct.workspace_id,
                account_id=acct.id,
                category_id=None,
                kind="expense",
                amount=-implied,
                title="Opening balance correction",
                occurred_on=first_day,
                occurred_at=datetime.combine(first_day, time.min, tzinfo=tz).astimezone(UTC),
                source="adjustment",
                legacy_id=None,
            )
        )
        plan.issues.append(
            Issue(
                "warning",
                "negative_implied_opening",
                f"{acct.name} of profile {acct.legacy_profile_id}: the history adds {-implied} more than the "
                f"balance allows (the balance was lowered outside the ledger); opening set to 0 plus an "
                f"adjustment expense of {-implied}",
            )
        )

    # -- recurring rules ------------------------------------------------------------------
    for r in raw.get("recurringTransactions") or []:
        pid = r["budget_profile_id"]
        freq = FREQUENCIES.get(r["frequency"])
        kind = {"INCOME": "income", "EXPENSE": "expense"}.get(r["transaction_type"])
        if pid not in ws_by_legacy:
            continue
        acct = account_for(pid, r["mode"])
        if freq is None or kind is None or acct is None:
            plan.issues.append(
                Issue(
                    "error",
                    "unmappable_recurring",
                    f"recurring rule {r['id']}: {r['frequency']!r}/{r['transaction_type']!r}/{r['mode']!r}",
                )
            )
            continue
        ws = ws_by_legacy[pid]
        anchor = datetime.fromtimestamp(r["start_date"] / 1000, tz=UTC).astimezone(tz).date()
        ends = None
        if r.get("end_date") is not None:
            ends = datetime.fromtimestamp(r["end_date"] / 1000, tz=UTC).astimezone(tz).date()
            if ends < anchor:
                plan.issues.append(
                    Issue(
                        "warning",
                        "recurring_end_before_start",
                        f"recurring rule {r['id']} ends before it starts; end date dropped",
                    )
                )
                ends = None
        plan.recurring_rules.append(
            PlannedRecurringRule(
                id=_uid("rule", pid, r["id"]),
                workspace_id=ws.id,
                account_id=acct.id,
                category_id=category_for(ws, kind, r.get("category")),
                kind=kind,
                amount=_money(r["amount"], plan, f"recurring rule {r['id']}"),
                title=_clean(r.get("title")) or "Recurring",
                frequency=freq,
                anchor_on=anchor,
                ends_on=ends,
                status="active" if r.get("is_active", True) else "paused",
                timezone=timezone,
            )
        )
    if plan.recurring_rules:
        plan.issues.append(
            Issue(
                "info",
                "recurring_not_backposted",
                "recurring rules are imported without generating past occurrences",
            )
        )

    # -- things the new schema cannot hold yet --------------------------------------------
    plans = [p for p in raw.get("pendingPlans") or [] if p.get("budget_profile_id") in ws_by_legacy]
    goals = [g for g in raw.get("goals") or [] if g.get("budget_profile_id") in ws_by_legacy]
    for key, rows, why in (
        ("pendingPlans", plans, "scheduled payments have no target table yet"),
        ("goals", goals, "savings goals have no target table yet"),
    ):
        if rows:
            plan.unmapped[key] = len(rows)
            plan.issues.append(
                Issue("warning", f"{key}_not_imported", f"{len(rows)} row(s) not imported: {why}")
            )

    plan.categories = list(cats.values())
    seen: set[tuple[str, str, str]] = set()
    unique = []
    for issue in plan.issues:  # the same note can be raised once per workspace; keep one
        key = (issue.severity, issue.code, issue.message)
        if key not in seen:
            seen.add(key)
            unique.append(issue)
    plan.issues = unique
    return plan


def reconcile(plan: ImportPlan) -> list[ReconRow]:
    """Recompute every account balance from the plan alone and compare with the legacy balance."""
    ws_name = {w.id: w.name for w in plan.workspaces}
    rows = []
    for a in plan.accounts:
        signed = sum(
            (
                t.amount if t.kind == "income" else -t.amount
                for t in plan.transactions
                if t.account_id == a.id
            ),
            Decimal(0),
        )
        rows.append(
            ReconRow(
                ws_name[a.workspace_id],
                a.name,
                plan.legacy_balances[(a.legacy_profile_id, a.legacy_mode)],
                a.opening_balance + signed,
            )
        )
    return rows


def check_plan_invariants(plan: ImportPlan) -> list[str]:
    """Rules the database will enforce, checked early so a bad plan fails with a readable message."""
    problems: list[str] = []
    ids = [
        x.id
        for coll in (
            plan.workspaces,
            plan.accounts,
            plan.categories,
            plan.transactions,
            plan.recurring_rules,
        )
        for x in coll
    ]
    if len(ids) != len(set(ids)):
        problems.append("duplicate ids")
    names = [(a.workspace_id, a.name.casefold()) for a in plan.accounts]
    if len(names) != len(set(names)):
        problems.append("duplicate account names inside a workspace")
    cat_keys = [(c.workspace_id, c.kind, c.name.casefold()) for c in plan.categories]
    if len(cat_keys) != len(set(cat_keys)):
        problems.append("duplicate categories inside a workspace")
    for a in plan.accounts:
        if a.opening_balance < 0:
            problems.append(f"account {a.name}: negative opening balance")
    for t in plan.transactions:
        if t.amount <= 0 or t.amount != t.amount.quantize(CENT):
            problems.append(
                f"transaction {t.legacy_id}: amount {t.amount} breaks the 2-decimal / positive rule"
            )
    return problems
