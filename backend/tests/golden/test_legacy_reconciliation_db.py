"""Load mapped legacy data through the REAL schema and check the database agrees with the legacy balances."""

import json
from decimal import Decimal
from pathlib import Path

import psycopg
import pytest

from app.legacy.mapper import map_backup, reconcile
from tests.golden.loader import load_plan

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures" / "legacy"


def plan_of(name: str):
    return map_backup(json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8")))


@pytest.fixture()
def db(pg):
    pg.execute("TRUNCATE users CASCADE")
    return pg


def balances(db) -> dict[tuple[str, str], Decimal]:
    rows = db.execute(
        "SELECT w.name, v.name, v.balance FROM v_account_balances v "
        "JOIN workspaces w ON w.id = v.workspace_id"
    ).fetchall()
    return {(w, a): bal for w, a, bal in rows}


def test_real_backup_balances_match_in_the_database(db):
    load_plan(db, plan_of("pre_profile_v1_sanitized"))
    assert balances(db) == {
        ("Primary Budget", "Bank"): Decimal(525),
        ("Primary Budget", "Cash"): Decimal(0),
    }
    assert db.execute("SELECT count(*) FROM transactions").fetchone()[0] == 4


def test_every_synthetic_account_matches_its_legacy_balance_in_the_database(db):
    plan = plan_of("v226_multi_profile")
    load_plan(db, plan)  # also proves the overdraft guard accepts the import order
    in_db = balances(db)
    assert len(in_db) == 4
    for row in reconcile(plan):
        assert in_db[(row.workspace, row.account)] == row.legacy_balance


def test_loading_the_same_plan_twice_is_a_no_op(db):
    plan = plan_of("v226_multi_profile")
    load_plan(db, plan)
    first = db.execute(
        "SELECT (SELECT count(*) FROM transactions), (SELECT count(*) FROM accounts), "
        "(SELECT count(*) FROM categories), (SELECT count(*) FROM recurring_rules)"
    ).fetchone()
    load_plan(db, plan)
    second = db.execute(
        "SELECT (SELECT count(*) FROM transactions), (SELECT count(*) FROM accounts), "
        "(SELECT count(*) FROM categories), (SELECT count(*) FROM recurring_rules)"
    ).fetchone()
    assert first == second


def test_the_database_agrees_that_a_negative_legacy_balance_cannot_be_imported(db):
    plan = plan_of("v226_negative_balance")
    assert plan.blocking_issues  # the mapper says no ...
    with pytest.raises(psycopg.errors.RaiseException) as err:
        load_plan(db, plan)  # ... and so does the overdraft guard
    assert err.value.diag.constraint_name == "account_no_overdraft"
