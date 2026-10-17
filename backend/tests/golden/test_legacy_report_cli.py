"""The read-only report script: exit codes and privacy."""

import subprocess
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[2]
FIXTURES = BACKEND / "tests" / "fixtures" / "legacy"


import os


def run(name: str):
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    return subprocess.run(
        [
            sys.executable,
            str(BACKEND / "scripts" / "legacy_report.py"),
            str(FIXTURES / f"{name}.json"),
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
        check=False,
    )


def test_clean_backup_exits_zero():
    r = run("pre_profile_v1_sanitized")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "RESULT: OK to import" in r.stdout


def test_negative_balance_backup_exits_one_and_says_why():
    r = run("v226_negative_balance")
    assert r.returncode == 1
    assert "negative_legacy_balance" in r.stdout and "BLOCKED" in r.stdout


def test_report_never_prints_personal_data():
    out = run("v226_multi_profile").stdout
    for secret in ("1990-01-01", "9000000000", "Testville", "Testland"):
        assert secret not in out
    assert "profile.dob" in out  # it names the dropped FIELD, not its value
