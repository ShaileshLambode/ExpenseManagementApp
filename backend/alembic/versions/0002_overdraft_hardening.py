"""overdraft guard hardening (schema v0 -> v0.1)

Revision ID: 0002
Revises: 0001
"""

from pathlib import Path

from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

SQL_DIR = Path(__file__).parent / "sql"


def _load(name: str) -> str:
    # psycopg reads a bare % as a parameter placeholder; the SQL uses % in RAISE messages.
    return (SQL_DIR / name).read_text(encoding="utf-8").replace("%", "%%")


def upgrade() -> None:
    op.get_bind().exec_driver_sql(_load("0002_overdraft_hardening.up.sql"))


def downgrade() -> None:
    op.get_bind().exec_driver_sql(_load("0002_overdraft_hardening.down.sql"))
