from decimal import Decimal

import pytest
from pydantic import BaseModel, ValidationError

from app.core.money import Amount, validate_amount_for_currency


class Payload(BaseModel):
    amount: Amount


def test_json_number_with_decimals_is_rejected():
    with pytest.raises(ValidationError):
        Payload.model_validate_json('{"amount": 450.10}')  # arrives as a float


def test_decimal_string_is_accepted_and_serialised_as_a_string():
    p = Payload.model_validate_json('{"amount": "450.10"}')
    assert p.amount == Decimal("450.10")
    assert p.model_dump_json() == '{"amount":"450.10"}'


def test_decimal_arithmetic_is_exact():
    assert Decimal("0.1") + Decimal("0.2") == Decimal("0.3")  # floats give 0.30000000000000004


def test_inr_rejects_a_third_decimal():
    with pytest.raises(ValueError, match="at most 2 decimal"):
        validate_amount_for_currency(Decimal("450.123"), "INR")  # schema v0 alone would store this


def test_jpy_has_no_decimals_and_kwd_has_three():
    with pytest.raises(ValueError):
        validate_amount_for_currency(Decimal("450.5"), "JPY")
    assert validate_amount_for_currency(Decimal("1.234"), "KWD") == Decimal("1.234")


def test_quantizes_to_the_currency_exponent():
    assert str(validate_amount_for_currency(Decimal(450), "INR")) == "450.00"
    assert str(validate_amount_for_currency(Decimal("450.1"), "INR")) == "450.10"


@pytest.mark.parametrize("bad", [Decimal(0), Decimal(-1), Decimal("100000000000.01")])
def test_out_of_range_amounts_are_rejected(bad):
    with pytest.raises(ValueError):
        validate_amount_for_currency(bad, "INR")
