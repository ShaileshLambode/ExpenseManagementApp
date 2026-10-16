"""Runs db/schema_v0_tests.sql (the SQL-level suite) from pytest and fails on any FAIL row.

At --revision 0001 this test is EXPECTED to fail on rows 24-31: that is the bug list.
"""

from pathlib import Path

import psycopg

from tests.conftest import REPO_ROOT

SUITE = Path(REPO_ROOT) / "db" / "schema_v0_tests.sql"


def test_sql_suite_all_pass(psycopg_dsn, migrated_db):
    script = SUITE.read_text(encoding="utf-8")
    last = []
    with psycopg.connect(psycopg_dsn, autocommit=True) as conn, conn.cursor() as cur:
        cur.execute(script)
        while True:
            if cur.description is not None:
                last = cur.fetchall()
            if not cur.nextset():
                break
    failures = [row for row in last if row[1] in ("FAIL", "HAS FAILURES")]
    assert last, "the SQL suite returned no result grid"
    assert not failures, "SQL suite failures:\n" + "\n".join(
        f"  #{r[0]} {r[2]}: {r[3]}" for r in failures
    )
