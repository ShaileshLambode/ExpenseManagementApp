"""Money on the wire (CONVENTIONS.md §1).

* Amounts are decimal STRINGS in JSON ("450.00"), never JSON numbers: a JS client would
  turn 450.10 into a binary float and silently lose exactness.
* Python uses Decimal, never float.
* PostgreSQL stores NUMERIC(20,8), but a currency only allows its own number of decimals
  (INR 2, JPY 0, KWD 3). That is enforced HERE, because the database column alone would
  happily store 450.123456789 rupees (found while auditing schema v0).
"""

from decimal import Decimal, InvalidOperation
from typing import Annotated, Any

from pydantic import BeforeValidator, PlainSerializer, WithJsonSchema

CURRENCY_EXPONENT: dict[str, int] = {
    "INR": 2,
    "USD": 2,
    "EUR": 2,
    "GBP": 2,
    "AED": 2,
    "SGD": 2,
    "JPY": 0,
    "KWD": 3,
    "BHD": 3,
}
MAX_AMOUNT = Decimal(100000000000)  # matches the CHECK on transactions.amount


def _parse(value: Any) -> Decimal:
    # Pydantic only converts ValueError (not TypeError) into a clean 422 response,
    # so these deliberately raise ValueError even for wrong types (hence the noqa).
    if isinstance(value, (bool, float)):
        raise ValueError(  # noqa: TRY004
            'send amounts as strings, e.g. "450.00" (JSON numbers can lose precision)'
        )
    if isinstance(value, Decimal):
        number = value
    elif isinstance(value, (str, int)):
        try:
            number = Decimal(str(value).strip())
        except InvalidOperation:
            raise ValueError("not a valid decimal number") from None
    else:
        raise ValueError("amount must be a decimal string")  # noqa: TRY004
    if not number.is_finite():
        raise ValueError("amount must be finite")
    return number


# Use this as the type of every money field in request/response models.
Amount = Annotated[
    Decimal,
    BeforeValidator(_parse),
    PlainSerializer(lambda d: format(d, "f"), return_type=str, when_used="json"),
    WithJsonSchema({"type": "string", "pattern": r"^-?\d+(\.\d+)?$", "examples": ["450.00"]}),
]


def validate_amount_for_currency(amount: Decimal, currency_code: str) -> Decimal:
    """Return the amount quantized to the currency's decimals, or raise ValueError.

    Extra digits are REJECTED, not rounded: silently rounding a user's money is how
    totals stop adding up.
    """
    exponent = CURRENCY_EXPONENT.get(currency_code)
    if exponent is None:
        raise ValueError(f"unsupported currency: {currency_code}")
    if amount <= 0:
        raise ValueError("amount must be greater than zero")
    if amount > MAX_AMOUNT:
        raise ValueError("amount is above the maximum allowed")
    decimals = max(0, -amount.normalize().as_tuple().exponent)
    if decimals > exponent:
        raise ValueError(f"{currency_code} allows at most {exponent} decimal places")
    return amount.quantize(Decimal(1).scaleb(-exponent))
