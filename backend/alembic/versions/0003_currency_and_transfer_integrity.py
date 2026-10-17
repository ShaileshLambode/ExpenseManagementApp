"""currency catalogue, decimals per currency, transfer integrity (schema v0.1 -> v0.2)

Revision ID: 0003
Revises: 0002
"""

from pathlib import Path

from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None

SQL_DIR = Path(__file__).parent / "sql"


def _load(name: str) -> str:
    # psycopg reads a bare % as a parameter placeholder; the SQL uses % in RAISE messages.
    return (SQL_DIR / name).read_text(encoding="utf-8").replace("%", "%%")


def upgrade() -> None:
    op.get_bind().exec_driver_sql(_load("0003_currency_and_transfer_integrity.up.sql"))


def downgrade() -> None:
    op.get_bind().exec_driver_sql(_load("0003_currency_and_transfer_integrity.down.sql"))
