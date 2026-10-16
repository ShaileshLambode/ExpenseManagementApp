"""baseline: schema v0 (frozen snapshot of db/schema_v0.sql from Day 1)

Revision ID: 0001
Revises:
"""

import re
from pathlib import Path

from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

SQL_DIR = Path(__file__).parent / "sql"


def _load(name: str) -> str:
    """Read a SQL file; drop BEGIN/COMMIT because Alembic already wraps the migration."""
    sql = (SQL_DIR / name).read_text(encoding="utf-8")
    sql = re.sub(r"^\s*(BEGIN|COMMIT);\s*$", "", sql, flags=re.IGNORECASE | re.MULTILINE)
    # psycopg reads a bare % as a parameter placeholder; the SQL uses % in RAISE/format().
    return sql.replace("%", "%%")


def upgrade() -> None:
    conn = op.get_bind()
    has_uuidv7 = conn.exec_driver_sql("SELECT to_regprocedure('uuidv7()') IS NOT NULL").scalar()
    if not has_uuidv7:
        raise RuntimeError(
            "uuidv7() was not found. Mulya needs PostgreSQL 18 or newer "
            "(run SELECT version(); and check you are connected to the right server/port)."
        )
    conn.exec_driver_sql(_load("0001_schema_v0.sql"))


def downgrade() -> None:
    conn = op.get_bind()
    conn.exec_driver_sql(
        """
        DROP VIEW IF EXISTS v_account_balances;
        DROP TABLE IF EXISTS recurring_occurrences, recurring_rules, transactions, transfers,
                             categories, accounts, workspace_members, workspaces, users CASCADE;
        DROP FUNCTION IF EXISTS check_no_overdraft();
        DROP FUNCTION IF EXISTS touch_row();
        """
    )
