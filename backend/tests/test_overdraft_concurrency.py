"""Two people spend the same last money at the same moment.

Schema v0:   both inserts succeed and the account ends at -600  -> this test FAILS (red).
Schema v0.1: the second insert waits for the first to commit, then is rejected (green).
"""

import threading
import time
import uuid

import psycopg

WS = uuid.uuid4()
ACC = uuid.uuid4()
USER = uuid.uuid4()


def _setup(pg):
    pg.execute("TRUNCATE users CASCADE")
    pg.execute(
        "INSERT INTO users (id, email, display_name) VALUES (%s, 'race@example.test', 'R')", (USER,)
    )
    pg.execute("INSERT INTO workspaces (id, owner_user_id, name) VALUES (%s, %s, 'W')", (WS, USER))
    pg.execute(
        "INSERT INTO accounts (id, workspace_id, name, type, currency_code, opening_balance) "
        "VALUES (%s, %s, 'A', 'bank', 'INR', 1000)",
        (ACC, WS),
    )


def _spend(conn, amount):
    conn.execute(
        "INSERT INTO transactions (workspace_id, account_id, kind, amount, occurred_on) "
        "VALUES (%s, %s, 'expense', %s, '2026-10-14')",
        (WS, ACC, amount),
    )


def test_two_concurrent_expenses_cannot_overdraw_the_account(pg, psycopg_dsn):
    _setup(pg)
    first = psycopg.connect(psycopg_dsn)  # transaction left open on purpose
    second = psycopg.connect(psycopg_dsn)
    second.execute("SET lock_timeout = '10s'")
    outcome = {}

    def second_spends():
        try:
            _spend(second, 800)
            second.commit()
            outcome["second"] = "committed"
        except psycopg.errors.RaiseException as exc:  # SQLSTATE P0001
            second.rollback()
            outcome["second"] = f"rejected:{exc.diag.constraint_name}"

    try:
        _spend(first, 800)  # balance would be 200: fine
        worker = threading.Thread(target=second_spends)
        worker.start()
        time.sleep(1.0)  # let `second` run into the lock (v0.1) or sail through (v0)
        first.commit()
        worker.join(timeout=15)
    finally:
        first.close()
        second.close()

    balance = pg.execute(
        "SELECT balance FROM v_account_balances WHERE account_id = %s", (ACC,)
    ).fetchone()[0]
    assert balance >= 0, f"account overdrawn: balance={balance} outcome={outcome}"
    assert outcome["second"] == "rejected:account_no_overdraft"
