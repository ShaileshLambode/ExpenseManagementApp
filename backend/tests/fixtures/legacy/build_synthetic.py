"""Builds the synthetic legacy-backup fixtures (deterministic: same output every run).

    python tests/fixtures/legacy/build_synthetic.py

Every person/place below is invented. Dates are Asia/Kolkata (UTC+05:30, no DST).
"""

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).parent
IST = timezone(timedelta(hours=5, minutes=30))


def ms(y, m, d, hh=10, mm=0):
    return int(datetime(y, m, d, hh, mm, tzinfo=IST).timestamp() * 1000)


def tx(id_, pid, kind, mode, amount, category, day, title=None):
    return {
        "id": id_,
        "budget_profile_id": pid,
        "title": title or f"{category} #{id_}",
        "amount": amount,
        "transaction_type": kind,
        "mode": mode,
        "category": category,
        "timestamp": day,
        "balance_after": 0.0,
    }


def profile(id_, name, active=False, deleted=False):
    return {
        "id": id_,
        "name": name,
        "color": "#3F51B5",
        "icon": "💰",
        "is_active": active,
        "created_at": ms(2026, 1, 1),
        "is_deleted": deleted,
        "deleted_at": ms(2026, 6, 1) if deleted else None,
    }


def balance(id_, pid, mode, amount):
    return {
        "id": id_,
        "budget_profile_id": pid,
        "type": mode,
        "amount": amount,
        "last_updated": ms(2026, 9, 30),
    }


COMMON = {
    "app": "Mulya",
    "version": 1,
    "timestamp": ms(2026, 9, 30, 20),
    "preferences": {
        "ACTIVE_PROFILE_ID": 1,
        "ALLOW_NEGATIVE_BALANCE": True,
        "AUTO_BACKUP": False,
        "CURRENCY_SYMBOL": "₹",
        "IS_FIRST_LAUNCH": False,
        "LOW_BALANCE_THRESHOLD": 500.0,
        "NOTIF_BUDGET_WARNING": True,
        "THEME_MODE": 1,
    },
    # invented person: proves personal data is dropped, never imported
    "profile": {
        "id": 1,
        "name": "Asha Testwala",
        "dob": "1990-01-01",
        "gender": "F",
        "mobile": "9000000000",
        "city": "Testville",
        "state": "Testland",
        "imageUri": "content://x",
    },
    "profileImageBase64": "AAAA",
}


def multi_profile() -> dict:
    cats = [
        {"id": i, "name": n, "icon": "🏷️"}
        for i, n in enumerate(
            ["Food", "Transport", "Shopping", "Bills", "Gadgets ", "food", "Rent", "Goals"], 1
        )
    ]
    txs = [
        # Profile 1 / BANK: opening comes out positive (4,920.49)
        tx(1, 1, "INCOME", "BANK", 50000.0, "Income", ms(2026, 3, 1)),
        tx(2, 1, "EXPENSE", "BANK", 449.99, "food", ms(2026, 3, 2)),
        tx(3, 1, "EXPENSE", "BANK", 1200.0, "Food ", ms(2026, 3, 3)),  # trailing space, other case
        tx(4, 1, "EXPENSE", "BANK", 250.5, "FOOD", ms(2026, 3, 4)),
        tx(5, 1, "EXPENSE", "BANK", 1000.0, "Goals", ms(2026, 3, 5)),  # savings-goal contribution
        tx(6, 1, "EXPENSE", "BANK", 19.999, "Bills", ms(2026, 3, 6)),  # more than 2 decimals
        tx(
            7, 1, "EXPENSE", "BANK", 0.1 + 0.2, "Bills", ms(2026, 3, 7)
        ),  # float noise: 0.30000000000000004
        # Profile 1 / CASH
        tx(8, 1, "EXPENSE", "CASH", 150.0, "Transport", ms(2026, 3, 8)),
        # Profile 2 / BANK: opening comes out exactly 0
        tx(9, 2, "INCOME", "BANK", 5000.0, "Income", ms(2026, 4, 1)),
        tx(10, 2, "EXPENSE", "BANK", 2000.0, "Rent", ms(2026, 4, 2)),
        # Profile 2 / CASH: income (120) exceeds the final balance (20): balance was lowered outside the ledger
        tx(11, 2, "INCOME", "CASH", 120.0, "Income", ms(2026, 4, 3)),
        # Profile 3 is in the recycle bin: all of this must be skipped
        tx(12, 3, "EXPENSE", "BANK", 99.0, "Food", ms(2026, 5, 1)),
    ]
    return {
        **COMMON,
        "balances": [
            balance(1, 1, "BANK", 52000.0),
            balance(2, 1, "CASH", 800.0),
            balance(3, 2, "BANK", 3000.0),
            balance(4, 2, "CASH", 20.0),
            balance(5, 3, "BANK", 500.0),
        ],
        "categories": cats,
        "transactions": txs,
        "budgetProfiles": [
            profile(1, "Personal", active=True),
            profile(2, "Business"),
            profile(3, "Old trip", deleted=True),
        ],
        "pendingPlans": [
            {
                "id": 1,
                "budget_profile_id": 1,
                "title": "Insurance",
                "amount": 4500.0,
                "mode": "BANK",
                "category": "Bills",
                "plan_type": "PAY",
                "due_date": ms(2026, 11, 5),
                "status": "PENDING",
                "created_at": ms(2026, 9, 1),
                "is_deleted": False,
                "deleted_at": None,
            },
            {
                "id": 2,
                "budget_profile_id": 1,
                "title": "Refund",
                "amount": 300.0,
                "mode": "CASH",
                "category": "Income",
                "plan_type": "RECEIVE",
                "due_date": None,
                "status": "COMPLETED",
                "created_at": ms(2026, 9, 2),
                "is_deleted": False,
                "deleted_at": None,
            },
        ],
        "goals": [
            {
                "id": 1,
                "budget_profile_id": 1,
                "name": "Trip fund",
                "target_amount": 20000.0,
                "current_amount": 1000.0,
                "deadline": ms(2027, 3, 1),
                "icon": "🎯",
                "color": "#3F51B5",
                "is_completed": False,
                "created_at": ms(2026, 3, 1),
                "is_deleted": False,
                "deleted_at": None,
            }
        ],
        "recurringTransactions": [
            {
                "id": 1,
                "budget_profile_id": 2,
                "title": "Hosting",
                "amount": 199.0,
                "mode": "BANK",
                "category": "Bills",
                "transaction_type": "EXPENSE",
                "frequency": "MONTHLY",
                "start_date": ms(2026, 1, 31),
                "end_date": ms(2026, 12, 31),
                "next_due_date": ms(2026, 10, 31),
                "message": None,
                "is_active": True,
                "created_at": ms(2026, 1, 31),
            },
            {
                "id": 2,
                "budget_profile_id": 1,
                "title": "Gym",
                "amount": 700.0,
                "mode": "BANK",
                "category": "Bills",
                "transaction_type": "EXPENSE",
                "frequency": "WEEKLY",
                "start_date": ms(2026, 5, 10),
                "end_date": ms(2026, 5, 1),
                "next_due_date": ms(2026, 5, 17),
                "message": None,
                "is_active": False,
                "created_at": ms(2026, 5, 10),
            },
        ],
    }


def negative_balance() -> dict:
    """The old app allowed negative balances by default; the new model cannot represent one."""
    return {
        **COMMON,
        "balances": [balance(1, 1, "BANK", -250.0)],
        "categories": [{"id": 1, "name": "Food", "icon": "🍔"}],
        "transactions": [tx(1, 1, "EXPENSE", "BANK", 250.0, "Food", ms(2026, 6, 1))],
        "budgetProfiles": [profile(1, "Personal", active=True)],
        "pendingPlans": [],
        "goals": [],
        "recurringTransactions": [],
    }


if __name__ == "__main__":
    for name, data in (
        ("v226_multi_profile.json", multi_profile()),
        ("v226_negative_balance.json", negative_balance()),
    ):
        (HERE / name).write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print("wrote", name)
