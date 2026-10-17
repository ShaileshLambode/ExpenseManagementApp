from sqlalchemy import create_engine, text
from sqlalchemy.pool import NullPool

from alembic import command
from tests.conftest import alembic_config

BASE_TABLES = {
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
# Tables present AFTER each revision (cumulative). Add a line with every new migration.
TABLES_AT = {
    "0001": BASE_TABLES,
    "0002": BASE_TABLES,
    "0003": BASE_TABLES | {"currencies"},
    "0004": BASE_TABLES
    | {"currencies", "auth_identities", "email_login_codes", "devices", "refresh_tokens"},
}


def expected_tables(revision: str) -> set[str]:
    return TABLES_AT[max(TABLES_AT) if revision == "head" else revision]


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


def test_schema_has_the_expected_tables_and_the_balance_view(migrated_db, request):
    objs = _objects(migrated_db)
    wanted = expected_tables(request.config.getoption("--revision"))
    assert {n for n, k in objs.items() if k == "BASE TABLE"} == wanted
    assert objs["v_account_balances"] == "VIEW"


def test_downgrade_to_base_then_back_up(migrated_db, request):
    """Every migration must be reversible. Ends by restoring the revision under test."""
    cfg = alembic_config(migrated_db)
    target = request.config.getoption("--revision")
    command.downgrade(cfg, "base")
    assert _objects(migrated_db) == {}
    command.upgrade(cfg, target)
    assert set(_objects(migrated_db)) == expected_tables(target) | {"v_account_balances"}
