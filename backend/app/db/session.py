"""Database engine and the per-request connection.

Rule (CONVENTIONS.md §5.9): ONE request = ONE database transaction.
`get_db` opens a transaction, commits when the endpoint returns normally and
rolls back if it raises. Services never call commit() themselves.
"""

from collections.abc import Iterator

from sqlalchemy import Connection, Engine, create_engine

from app.core.config import get_settings

_engine: Engine | None = None


def get_engine() -> Engine:
    global _engine
    if _engine is None:
        _engine = create_engine(get_settings().database_url, pool_pre_ping=True)
    return _engine


def reset_engine() -> None:
    """Forget the cached engine (used by tests that point the app at another database)."""
    global _engine
    if _engine is not None:
        _engine.dispose()
    _engine = None


def get_db() -> Iterator[Connection]:
    with get_engine().begin() as conn:
        yield conn
