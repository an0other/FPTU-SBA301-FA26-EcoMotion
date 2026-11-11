from secrets import compare_digest

from fastapi import HTTPException, Security
from fastapi.security import APIKeyHeader

from identity_verification.config import get_settings


api_key_header = APIKeyHeader(
    name="X-API-Key",
    auto_error=False,
)


def require_api_key(key: str | None = Security(api_key_header)):
    expected = get_settings().api_key.get_secret_value()

    if key is None or not compare_digest(
        key.encode("utf-8"),
        expected.encode("utf-8"),
    ):
        raise HTTPException(
            status_code=401,
            detail="Invalid API key",
        )