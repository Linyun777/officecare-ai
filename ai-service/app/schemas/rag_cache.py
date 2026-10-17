from datetime import datetime
import re

from pydantic import Field, field_validator

from app.schemas.common import ApiModel


class RagCacheInvalidateRequest(ApiModel):
    knowledge_base_id: int = Field(gt=0)
    reason: str = Field(min_length=1, max_length=100)

    @field_validator("reason")
    @classmethod
    def normalize_reason(cls, value: str) -> str:
        normalized = value.strip().upper()
        if not re.fullmatch(r"[A-Z0-9_:-]+", normalized):
            raise ValueError(
                "reason may contain only letters, digits, colon, underscore, or hyphen"
            )
        return normalized


class RagCacheInvalidateResponse(ApiModel):
    knowledge_base_id: int
    revision: int
    reason: str
    invalidated_at: datetime


class RagCacheStatsResponse(ApiModel):
    enabled: bool
    redis_reachable: bool
    namespace: str | None = None
    ttl_seconds: int | None = None
    metrics_scope: str
    knowledge_base_id: int | None = None
    revision: int | None = None
    hits: int = 0
    misses: int = 0
    writes: int = 0
    bypasses: int = 0
    errors: int = 0
    invalid_entries: int = 0
    invalidations: int = 0
    hit_rate: float = 0.0
