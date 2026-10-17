import hmac
from typing import Annotated

from fastapi import Header

from app.core.config import get_settings
from app.core.exceptions import ServiceError


async def verify_internal_token(
    supplied_token: Annotated[
        str | None,
        Header(alias="X-OfficeCare-Internal-Token"),
    ] = None,
) -> None:
    configured_token = get_settings().internal_token()
    if configured_token is None:
        raise ServiceError(
            status_code=503,
            code="SERVICE_NOT_CONFIGURED",
            message="Internal service authentication is not configured.",
        )

    if supplied_token is None or not hmac.compare_digest(
        supplied_token.encode("utf-8"),
        configured_token.encode("utf-8"),
    ):
        raise ServiceError(
            status_code=401,
            code="UNAUTHORIZED",
            message="The internal service token is missing or invalid.",
        )
