"""Liveness and readiness probes (not versioned: infrastructure talks to these)."""

from pathlib import Path

from alembic.config import Config
from alembic.script import ScriptDirectory
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from app.db.session import get_engine

router = APIRouter(tags=["health"])
BACKEND_DIR = Path(__file__).resolve().parents[2]


def expected_schema_revision() -> str:
    cfg = Config(str(BACKEND_DIR / "alembic.ini"))
    cfg.set_main_option("script_location", str(BACKEND_DIR / "alembic"))
    return ScriptDirectory.from_config(cfg).get_current_head()


@router.get("/health/live")
def live() -> dict[str, str]:
    """The process is up. Never touches the database."""
    return {"status": "ok"}


@router.get("/health/ready")
def ready():
    """Ready = database reachable AND migrated to the revision this code expects."""
    expected = expected_schema_revision()
    try:
        with get_engine().connect() as conn:
            current = conn.execute(text("SELECT version_num FROM alembic_version")).scalar()
    except SQLAlchemyError:
        return JSONResponse(
            status_code=503,
            content={"status": "not_ready", "reason": "database unreachable or not migrated"},
        )
    if current != expected:
        return JSONResponse(
            status_code=503,
            content={
                "status": "not_ready",
                "reason": "schema revision mismatch",
                "expected": expected,
                "current": current,
            },
        )
    return {"status": "ready", "schema_revision": current}
