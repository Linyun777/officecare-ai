from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from hashlib import sha256
import json
import logging
from threading import Lock
from typing import Protocol

from pydantic import ValidationError
from redis import Redis

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.schemas.rag import RagAnswerRequest, RagAnswerResponse
from app.schemas.rag_cache import (
    RagCacheInvalidateRequest,
    RagCacheInvalidateResponse,
    RagCacheStatsResponse,
)


logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class RagCacheLookup:
    """A cache lookup result pinned to one knowledge-base revision."""

    cache_key: str | None
    response: RagAnswerResponse | None = None


class RagAnswerCache(Protocol):
    """Cache boundary used by the RAG service."""

    def lookup(self, request: RagAnswerRequest) -> RagCacheLookup:
        ...

    def store(
        self,
        lookup: RagCacheLookup,
        response: RagAnswerResponse,
    ) -> None:
        ...

    def record_bypass(self, reason: str) -> None:
        ...

    def stats(self, knowledge_base_id: int | None = None) -> RagCacheStatsResponse:
        ...

    def invalidate(
        self,
        request: RagCacheInvalidateRequest,
    ) -> RagCacheInvalidateResponse:
        ...


class NoOpRagAnswerCache:
    """Disabled cache implementation that keeps local development predictable."""

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings

    def lookup(self, request: RagAnswerRequest) -> RagCacheLookup:
        return RagCacheLookup(cache_key=None)

    def store(
        self,
        lookup: RagCacheLookup,
        response: RagAnswerResponse,
    ) -> None:
        return None

    def record_bypass(self, reason: str) -> None:
        return None

    def stats(self, knowledge_base_id: int | None = None) -> RagCacheStatsResponse:
        return RagCacheStatsResponse(
            enabled=False,
            redis_reachable=False,
            namespace=(
                self._settings.rag_cache_namespace if self._settings else None
            ),
            ttl_seconds=(
                self._settings.rag_cache_ttl_seconds if self._settings else None
            ),
            metrics_scope="PROCESS",
            knowledge_base_id=knowledge_base_id,
        )

    def invalidate(
        self,
        request: RagCacheInvalidateRequest,
    ) -> RagCacheInvalidateResponse:
        raise ServiceError(
            status_code=409,
            code="RAG_CACHE_DISABLED",
            message="The Redis RAG answer cache is disabled.",
        )


class RedisClient(Protocol):
    def get(self, name: str) -> str | bytes | None:
        ...

    def set(
        self,
        name: str,
        value: str,
        *,
        ex: int,
    ) -> object:
        ...

    def delete(self, *names: str) -> object:
        ...

    def incr(self, name: str, amount: int = 1) -> int:
        ...

    def ping(self) -> object:
        ...


class _ProcessMetrics:
    def __init__(self) -> None:
        self._lock = Lock()
        self._values = {
            "hits": 0,
            "misses": 0,
            "writes": 0,
            "bypasses": 0,
            "errors": 0,
            "invalid_entries": 0,
            "invalidations": 0,
        }

    def increment(self, name: str) -> None:
        with self._lock:
            self._values[name] += 1

    def snapshot(self) -> dict[str, int]:
        with self._lock:
            return dict(self._values)


class RedisRagAnswerCache:
    """Revisioned exact-match cache for validated, citation-backed answers."""

    def __init__(
        self,
        settings: Settings,
        *,
        prompt: str,
        client: RedisClient | None = None,
    ) -> None:
        self._settings = settings
        self._prompt_digest = sha256(prompt.encode("utf-8")).hexdigest()
        self._metrics = _ProcessMetrics()
        self._client = client or Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_database,
            password=settings.redis_secret(),
            socket_connect_timeout=settings.redis_connect_timeout_seconds,
            socket_timeout=settings.redis_socket_timeout_seconds,
            socket_keepalive=True,
            decode_responses=True,
        )

    def lookup(self, request: RagAnswerRequest) -> RagCacheLookup:
        revision = self._read_revision(request.knowledge_base_id)
        if revision is None:
            return RagCacheLookup(cache_key=None)

        key = self.key_for(request, revision=revision)
        try:
            cached = self._client.get(key)
        except Exception as exc:
            self._record_failure("read", exc)
            return RagCacheLookup(cache_key=None)

        if cached is None:
            self._metrics.increment("misses")
            logger.debug("Redis RAG cache miss. key=%s", key)
            return RagCacheLookup(cache_key=key)

        try:
            response = RagAnswerResponse.model_validate_json(cached)
        except (ValidationError, ValueError, TypeError):
            self._discard_invalid_entry(key, "invalid")
            return RagCacheLookup(cache_key=key)

        if not response.answered or not response.citations:
            self._discard_invalid_entry(key, "unsafe")
            return RagCacheLookup(cache_key=key)

        self._metrics.increment("hits")
        logger.debug("Redis RAG cache hit. key=%s", key)
        return RagCacheLookup(cache_key=key, response=response)

    def store(
        self,
        lookup: RagCacheLookup,
        response: RagAnswerResponse,
    ) -> None:
        # A missing key means revision lookup or Redis access failed. Do not read
        # a newer revision after generation: an invalidation may have happened
        # while the answer was being generated.
        if (
            lookup.cache_key is None
            or not response.answered
            or not response.citations
        ):
            return

        payload = response.model_dump_json(by_alias=True)
        try:
            self._client.set(
                lookup.cache_key,
                payload,
                ex=self._settings.rag_cache_ttl_seconds,
            )
        except Exception as exc:
            self._record_failure("write", exc)
            return
        self._metrics.increment("writes")

    def record_bypass(self, reason: str) -> None:
        self._metrics.increment("bypasses")
        logger.debug("Redis RAG cache bypass. reason=%s", reason)

    def stats(self, knowledge_base_id: int | None = None) -> RagCacheStatsResponse:
        redis_reachable = False
        revision: int | None = None
        try:
            redis_reachable = bool(self._client.ping())
        except Exception as exc:
            self._record_failure("health", exc)

        if redis_reachable and knowledge_base_id is not None:
            revision = self._read_revision(knowledge_base_id)

        values = self._metrics.snapshot()
        lookups = values["hits"] + values["misses"]
        hit_rate = values["hits"] / lookups if lookups else 0.0
        return RagCacheStatsResponse(
            enabled=True,
            redis_reachable=redis_reachable,
            namespace=self._settings.rag_cache_namespace,
            ttl_seconds=self._settings.rag_cache_ttl_seconds,
            metrics_scope="PROCESS",
            knowledge_base_id=knowledge_base_id,
            revision=revision,
            hits=values["hits"],
            misses=values["misses"],
            writes=values["writes"],
            bypasses=values["bypasses"],
            errors=values["errors"],
            invalid_entries=values["invalid_entries"],
            invalidations=values["invalidations"],
            hit_rate=round(hit_rate, 6),
        )

    def invalidate(
        self,
        request: RagCacheInvalidateRequest,
    ) -> RagCacheInvalidateResponse:
        revision_key = self._revision_key(request.knowledge_base_id)
        try:
            revision = int(self._client.incr(revision_key))
        except Exception as exc:
            self._record_failure("invalidation", exc)
            raise ServiceError(
                status_code=503,
                code="RAG_CACHE_INVALIDATION_FAILED",
                message="The Redis RAG cache revision could not be updated.",
            ) from exc

        self._metrics.increment("invalidations")
        logger.info(
            "Redis RAG cache invalidated. knowledge_base_id=%s revision=%s reason=%s",
            request.knowledge_base_id,
            revision,
            request.reason,
        )
        return RagCacheInvalidateResponse(
            knowledge_base_id=request.knowledge_base_id,
            revision=revision,
            reason=request.reason,
            invalidated_at=datetime.now(UTC),
        )

    def key_for(self, request: RagAnswerRequest, *, revision: int = 0) -> str:
        """Build a non-reversible key from every input that can change an answer."""
        fingerprint = {
            "revision": revision,
            "question": request.question,
            "scope": {
                "knowledgeBaseId": request.knowledge_base_id,
                "brandId": request.brand_id,
                "modelId": request.model_id,
                "documentVersionIds": sorted(request.document_version_ids),
            },
            "request": {
                "topK": request.top_k,
                "scoreThreshold": request.score_threshold,
            },
            "generation": {
                "baseUrl": self._settings.chat_base_url,
                "model": self._settings.chat_model,
                "temperature": self._settings.chat_temperature,
                "maxTokens": self._settings.chat_max_tokens,
                "promptDigest": self._prompt_digest,
            },
            "retrieval": {
                "embeddingBaseUrl": self._settings.embedding_base_url,
                "embeddingModel": self._settings.embedding_model,
                "embeddingDimension": self._settings.embedding_dimension,
                "milvusUri": self._settings.milvus_uri,
                "milvusDatabase": self._settings.milvus_database,
                "milvusCollection": self._settings.milvus_collection,
                "chunkSize": self._settings.chunk_size,
                "chunkOverlap": self._settings.chunk_overlap,
                "denseCandidateLimit": self._settings.rag_dense_candidate_limit,
                "lexicalScanLimit": self._settings.rag_lexical_scan_limit,
                "lexicalCandidateLimit": self._settings.rag_lexical_candidate_limit,
                "lexicalScoreThreshold": self._settings.rag_lexical_score_threshold,
                "neighborWindow": self._settings.rag_neighbor_window,
                "contextMaxChars": self._settings.rag_context_max_chars,
            },
        }
        canonical = json.dumps(
            fingerprint,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        digest = sha256(canonical.encode("utf-8")).hexdigest()
        return f"{self._settings.rag_cache_namespace}:{digest}"

    def _read_revision(self, knowledge_base_id: int) -> int | None:
        try:
            raw_revision = self._client.get(
                self._revision_key(knowledge_base_id)
            )
            revision = int(raw_revision) if raw_revision is not None else 0
            if revision < 0:
                raise ValueError("negative cache revision")
            return revision
        except Exception as exc:
            self._record_failure("revision-read", exc)
            return None

    def _revision_key(self, knowledge_base_id: int) -> str:
        return (
            f"{self._settings.rag_cache_namespace}:revision:"
            f"{knowledge_base_id}"
        )

    def _discard_invalid_entry(self, key: str, entry_type: str) -> None:
        self._metrics.increment("invalid_entries")
        self._metrics.increment("misses")
        logger.warning(
            "Discarding an %s Redis RAG cache entry. key=%s",
            entry_type,
            key,
        )
        try:
            self._client.delete(key)
        except Exception as exc:
            self._record_failure("delete", exc)

    def _record_failure(self, operation: str, exc: Exception) -> None:
        self._metrics.increment("errors")
        # Log only the exception type. Connection strings can contain secrets.
        logger.warning(
            "Redis RAG cache %s failed; continuing safely. error=%s",
            operation,
            type(exc).__name__,
        )
