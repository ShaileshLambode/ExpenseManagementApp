"""Test fixtures.

Safety: the database is WIPED at the start of a test session, so it must be named exactly
`mulya_test`. Pointing TEST_DATABASE_URL at anything else aborts the run.
"""

import os
from pathlib import Path

import psycopg
import pytest
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url
from sqlalchemy.pool import NullPool

from alembic import command
from app.core.config import get_settings

BACKEND_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = BACKEND_DIR.parent


def pytest_addoption(parser):
    parser.addoption(
        "--revision",
        default="head",
        help="Alembic revision to migrate the test database to (default: head). "
        "Use --revision 0001 to see the schema v0 bugs fail.",
    )


def alembic_config(url: str) -> Config:
    cfg = Config(str(BACKEND_DIR / "alembic.ini"))
    cfg.set_main_option("script_location", str(BACKEND_DIR / "alembic"))
    cfg.attributes["url"] = url  # env.py prefers this over .env
    return cfg


@pytest.fixture(scope="session")
def test_db_url() -> str:
    url = get_settings().test_database_url
    if not url:
        pytest.exit("TEST_DATABASE_URL is not set (copy .env.example to .env)", returncode=2)
    if make_url(url).database != "mulya_test":
        pytest.exit("TEST_DATABASE_URL must point at a database named 'mulya_test'", returncode=2)
    return url


@pytest.fixture(scope="session")
def psycopg_dsn(test_db_url) -> str:
    return make_url(test_db_url).set(drivername="postgresql").render_as_string(hide_password=False)


@pytest.fixture(scope="session")
def migrated_db(test_db_url, request) -> str:
    """Wipe mulya_test, then run the real migrations on it."""
    engine = create_engine(test_db_url, poolclass=NullPool, isolation_level="AUTOCOMMIT")
    with engine.connect() as conn:
        conn.execute(text("DROP SCHEMA public CASCADE"))
        conn.execute(text("CREATE SCHEMA public"))
    engine.dispose()
    command.upgrade(alembic_config(test_db_url), request.config.getoption("--revision"))
    return test_db_url


@pytest.fixture(scope="session")
def client(migrated_db) -> TestClient:
    """A test client whose app is pointed at the migrated test database."""
    from app.db import session

    os.environ["DATABASE_URL"] = migrated_db
    get_settings.cache_clear()
    session.reset_engine()
    from app.main import create_app

    with TestClient(create_app()) as c:
        yield c
    session.reset_engine()


@pytest.fixture()
def pg(psycopg_dsn, migrated_db):
    with psycopg.connect(psycopg_dsn, autocommit=True) as conn:
        yield conn
