"""authentication tables (schema v0.2 -> v0.3)

Revision ID: 0004
Revises: 0003
"""

from pathlib import Path

from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None

SQL_DIR = Path(__file__).parent / "sql"


def _load(name: str) -> str:
    # psycopg reads a bare % as a parameter placeholder; the SQL uses % in RAISE messages.
    return (SQL_DIR / name).read_text(encoding="utf-8").replace("%", "%%")


def upgrade() -> None:
    op.get_bind().exec_driver_sql(_load("0004_auth_tables.up.sql"))


def downgrade() -> None:
    op.get_bind().exec_driver_sql(_load("0004_auth_tables.down.sql"))
