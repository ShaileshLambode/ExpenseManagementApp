from sqlalchemy import create_engine, text
from sqlalchemy.pool import NullPool

from alembic import command
from tests.conftest import alembic_config

EXPECTED_TABLES = {
    "users",
    "workspaces",
    "workspace_members",
    "accounts",
    "categories",
    "transfers",
    "transactions",
    "recurring_rules",
    "recurring_occurrences",
}


def _objects(url):
    engine = create_engine(url, poolclass=NullPool)
    with engine.connect() as conn:
        rows = conn.execute(
            text(
                "SELECT table_name, table_type FROM information_schema.tables "
                "WHERE table_schema = 'public' AND table_name <> 'alembic_version'"
            )
        ).all()
    engine.dispose()
    return {name: kind for name, kind in rows}


def test_schema_has_nine_tables_and_the_balance_view(migrated_db):
    objs = _objects(migrated_db)
    assert {n for n, k in objs.items() if k == "BASE TABLE"} == EXPECTED_TABLES
    assert objs["v_account_balances"] == "VIEW"


def test_downgrade_to_base_then_back_up(migrated_db, request):
    """Every migration must be reversible. Ends by restoring the revision under test."""
    cfg = alembic_config(migrated_db)
    target = request.config.getoption("--revision")
    command.downgrade(cfg, "base")
    assert _objects(migrated_db) == {}
    command.upgrade(cfg, target)
    assert set(_objects(migrated_db)) == EXPECTED_TABLES | {"v_account_balances"}
