# Mulya backend

FastAPI + PostgreSQL 18, migrations by Alembic (SQL-first). Requires Python 3.12+.

```powershell
cd backend
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1          # if blocked: Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
pip install -e ".[dev]"
Copy-Item .env.example .env            # then edit the password. NEVER commit .env
alembic upgrade head                   # create/upgrade the schema in mulya_dev
uvicorn app.main:app --reload          # http://127.0.0.1:8000/docs
pytest                                 # wipes + migrates mulya_test, runs everything
```

| Path | Purpose |
|---|---|
| `app/main.py` | builds the FastAPI app |
| `app/core/config.py` | settings from `.env` (no password default on purpose) |
| `app/core/money.py` | `Amount` type (decimal strings) + per-currency validation |
| `app/db/session.py` | engine + one transaction per request |
| `app/api/health.py` | `/health/live`, `/health/ready` (ready also checks the migration revision) |
| `app/api/v1/` | versioned API routers |
| `app/legacy/mapper.py` | pure mapper: legacy Mulya backup → rows for the new schema (+ reconciliation) |
| `alembic/versions/` | 0001 Day 1 schema (frozen) · 0002 overdraft hardening · 0003 currencies + transfer integrity · 0004 auth tables |
| `scripts/legacy_report.py` | read-only: shows what a legacy backup would become (counts, reconciliation, issues) |
| `scripts/sanitize_legacy_backup.py` | strips personal data from a legacy backup so it can become a fixture |
| `tests/golden/`, `tests/fixtures/legacy/` | golden tests and the legacy-backup fixtures |
| `tests/` | pytest suite (see `docs/TEST_MATRIX.md`) |

Rules: see `docs/CONVENTIONS.md` and `docs/ARCHITECTURE.md`.
