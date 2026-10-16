from app.db import session


def test_live_does_not_need_a_database(client):
    r = client.get("/health/live")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_ready_reports_the_schema_revision(client, request):
    r = client.get("/health/ready")
    if request.config.getoption("--revision") != "head":
        # Deliberately behind head (red/green demo): readiness must refuse.
        assert r.status_code == 503
        assert r.json()["reason"] == "schema revision mismatch"
    else:
        assert r.status_code == 200
        assert r.json() == {"status": "ready", "schema_revision": "0002"}


def test_ready_is_503_when_the_database_is_unreachable(client, monkeypatch):
    from sqlalchemy import create_engine

    dead = create_engine(
        "postgresql+psycopg://nobody@127.0.0.1:1/none", connect_args={"connect_timeout": 1}
    )
    monkeypatch.setattr(session, "_engine", dead)
    r = client.get("/health/ready")
    assert r.status_code == 503
    assert r.json()["status"] == "not_ready"


def test_api_v1_is_mounted_and_in_openapi(client):
    assert client.get("/api/v1/system/info").json() == {"api_version": "v1"}
    assert "/api/v1/system/info" in client.get("/openapi.json").json()["paths"]
