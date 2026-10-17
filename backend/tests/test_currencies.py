"""The Python currency table and the database catalogue must never drift apart."""

import pytest

from app.core.money import CURRENCY_EXPONENT


def test_python_and_database_currency_exponents_agree(pg, request):
    if request.config.getoption("--revision") in ("0001", "0002"):
        pytest.skip("currencies table is created by migration 0003")
    rows = pg.execute("SELECT code, exponent FROM currencies").fetchall()
    assert {code: exp for code, exp in rows} == CURRENCY_EXPONENT
