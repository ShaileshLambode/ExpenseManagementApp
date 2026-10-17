"""Golden tests for the legacy mapper: pure, no database."""

import json
from datetime import date
from decimal import Decimal
from pathlib import Path

import pytest

from app.legacy.mapper import check_plan_invariants, map_backup, reconcile

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures" / "legacy"


def load(name: str) -> dict:
    return json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))


def codes(plan) -> set[str]:
    return {i.code for i in plan.issues}


# --- the REAL pre-profile backup (sanitized) ------------------------------------------------
def test_real_pre_profile_backup_heals_into_one_workspace():
    plan = map_backup(load("pre_profile_v1_sanitized"))
    assert [w.name for w in plan.workspaces] == ["Primary Budget"]
    assert "profile_synthesized" in codes(plan)
    assert plan.blocking_issues == []


def test_real_backup_opening_balances_are_derived_and_reconcile():
    plan = map_backup(load("pre_profile_v1_sanitized"))
    openings = {a.name: a.opening_balance for a in plan.accounts}
    # Bank: legacy balance 525 + 240 + 80 + 100 + 55 spent = 1000 opened with
    assert openings == {"Bank": Decimal("1000.00"), "Cash": Decimal("0.00")}
    assert all(r.ok for r in reconcile(plan))
    assert check_plan_invariants(plan) == []


def test_trailing_space_category_is_normalised_not_duplicated():
    plan = map_backup(load("pre_profile_v1_sanitized"))
    names = [c.name for c in plan.categories]
    assert "Gadgets" in names and "Gadgets " not in names
    assert len(names) == len({n.casefold() for n in names})


# --- synthetic v2.26 backup ------------------------------------------------------------------
@pytest.fixture(scope="module")
def multi():
    return map_backup(load("v226_multi_profile"))


def test_deleted_profile_and_its_rows_are_skipped(multi):
    assert sorted(w.name for w in multi.workspaces) == ["Business", "Personal"]
    assert {"profile_deleted_skipped", "transactions_of_deleted_profiles_skipped"} <= codes(multi)
    assert all(t.legacy_id != 12 for t in multi.transactions)


def test_every_account_reconciles_to_its_legacy_balance(multi):
    rows = reconcile(multi)
    assert len(rows) == 4
    assert [r for r in rows if not r.ok] == []


def test_negative_implied_opening_becomes_an_adjustment_expense(multi):
    cash = next(a for a in multi.accounts if a.name == "Cash" and a.opening_balance == 0)
    adj = [t for t in multi.transactions if t.source == "adjustment"]
    assert len(adj) == 1 and adj[0].account_id == cash.id
    assert (adj[0].kind, adj[0].amount) == ("expense", Decimal("100.00"))
    assert "negative_implied_opening" in codes(multi)


def test_case_variants_of_a_category_merge_into_one(multi):
    personal = next(w for w in multi.workspaces if w.name == "Personal")
    food = [
        c
        for c in multi.categories
        if c.workspace_id == personal.id and c.kind == "expense" and c.name.casefold() == "food"
    ]
    assert len(food) == 1


def test_float_noise_is_cleaned_but_real_extra_decimals_are_reported(multi):
    by_legacy = {t.legacy_id: t for t in multi.transactions if t.legacy_id}
    assert by_legacy[7].amount == Decimal("0.30")  # 0.1 + 0.2 float noise: silent
    assert by_legacy[6].amount == Decimal("20.00")  # 19.999: rounded, with a warning
    messages = [i.message for i in multi.issues if i.code == "amount_rounded"]
    assert len(messages) == 1 and "transaction 6" in messages[0]


def test_goal_contribution_is_flagged(multi):
    assert "goal_contribution_as_expense" in codes(multi)


def test_recurring_rules_keep_the_month_end_anchor(multi):
    hosting = next(r for r in multi.recurring_rules if r.title == "Hosting")
    assert (hosting.frequency, hosting.anchor_on, hosting.ends_on, hosting.status) == (
        "monthly",
        date(2026, 1, 31),
        date(2026, 12, 31),
        "active",
    )
    gym = next(r for r in multi.recurring_rules if r.title == "Gym")
    assert gym.ends_on is None and gym.status == "paused"  # end date before start: dropped
    assert "recurring_end_before_start" in codes(multi)


def test_things_without_a_target_table_are_counted_not_lost_silently(multi):
    assert multi.unmapped == {"pendingPlans": 2, "goals": 1}
    assert {"pendingPlans_not_imported", "goals_not_imported"} <= codes(multi)


def test_invariants_hold_for_the_synthetic_plan(multi):
    assert check_plan_invariants(multi) == []
    assert multi.blocking_issues == []


# --- the strict model cannot hold a negative balance --------------------------------------------
def test_negative_legacy_balance_blocks_the_plan():
    plan = map_backup(load("v226_negative_balance"))
    assert [i.code for i in plan.blocking_issues] == ["negative_legacy_balance"]


# --- privacy, determinism, bad input --------------------------------------------------------------
def test_personal_data_never_reaches_the_plan():
    raw = load("v226_multi_profile")
    plan = map_backup(raw)
    blob = repr(plan)  # every field of every planned row
    for secret in ("1990-01-01", "9000000000", "Testville", "Testland", "content://x", "AAAA"):
        assert secret not in blob
    assert plan.owner_display_name == "Asha Testwala"  # the display name is the one thing kept
    dropped = next(i for i in plan.issues if i.code == "pii_dropped")
    assert "profile.dob" in dropped.message and "1990-01-01" not in dropped.message


def test_mapping_twice_gives_identical_ids():
    a, b = map_backup(load("v226_multi_profile")), map_backup(load("v226_multi_profile"))
    assert [t.id for t in a.transactions] == [t.id for t in b.transactions]
    assert [x.id for x in a.accounts] == [x.id for x in b.accounts]


@pytest.mark.parametrize("bad", [{}, [], {"transactions": []}, {"balances": []}, "text"])
def test_non_backups_are_rejected(bad):
    with pytest.raises(ValueError, match="not a Mulya backup"):
        map_backup(bad)
