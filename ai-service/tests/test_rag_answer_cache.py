from __future__ import annotations

import pytest

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.schemas.rag import RagAnswerRequest, RagAnswerResponse, RagCitation
from app.schemas.rag_cache import RagCacheInvalidateRequest
from app.services.rag_answer_cache import RagCacheLookup, RedisRagAnswerCache


class FakeRedisClient:
    def __init__(self) -> None:
        self.values: dict[str, str] = {}
        self.set_calls: list[tuple[str, str, int]] = []
        self.deleted: list[str] = []
        self.get_error: Exception | None = None
        self.set_error: Exception | None = None
        self.incr_error: Exception | None = None
        self.ping_error: Exception | None = None

    def get(self, name: str) -> str | None:
        if self.get_error is not None:
            raise self.get_error
        return self.values.get(name)

    def set(self, name: str, value: str, *, ex: int) -> bool:
        if self.set_error is not None:
            raise self.set_error
        self.values[name] = value
        self.set_calls.append((name, value, ex))
        return True

    def delete(self, *names: str) -> int:
        for name in names:
            self.values.pop(name, None)
            self.deleted.append(name)
        return len(names)

    def incr(self, name: str, amount: int = 1) -> int:
        if self.incr_error is not None:
            raise self.incr_error
        current = int(self.values.get(name, "0")) + amount
        self.values[name] = str(current)
        return current

    def ping(self) -> bool:
        if self.ping_error is not None:
            raise self.ping_error
        return True


def _settings(**overrides: object) -> Settings:
    values: dict[str, object] = {
        "redis_enabled": True,
        "redis_host": "redis.test",
        "redis_port": 6381,
        "redis_database": 0,
        "redis_password": "secret-that-must-not-enter-cache-keys",
        "rag_cache_ttl_seconds": 900,
        "chat_base_url": "https://chat.test",
        "chat_model": "test-chat-model",
        "embedding_model": "test-embedding-model",
        "milvus_collection": "test_collection",
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)


def _request(**overrides: object) -> RagAnswerRequest:
    values: dict[str, object] = {
        "question": "打印页面每隔94毫米出现重复斑点怎么办？",
        "knowledgeBaseId": 1,
        "brandId": 10,
        "modelId": 20,
        "documentVersionIds": [41, 40],
        "topK": 5,
        "scoreThreshold": 0.45,
    }
    values.update(overrides)
    return RagAnswerRequest(**values)


def _response(*, answered: bool = True) -> RagAnswerResponse:
    citations = []
    refusal_reason = "INSUFFICIENT_EVIDENCE"
    answer = "根据当前资料无法回答。"
    if answered:
        citations = [
            RagCitation(
                citation_index=1,
                knowledge_base_id=1,
                brand_id=10,
                model_id=20,
                document_id=30,
                document_version_id=40,
                document_title="高级说明书",
                original_file_name="manual.pdf",
                page_number=399,
                chunk_index=7,
                heading="改善打印质量",
                quote="每隔94毫米出现重复斑点时，清洁硒鼓单元。",
                score=0.81,
            )
        ]
        refusal_reason = None
        answer = "请按说明书清洁硒鼓单元。[资料1]"
    return RagAnswerResponse(
        answered=answered,
        answer=answer,
        refusal_reason=refusal_reason,
        citations=citations,
        retrieved_count=1,
        score_threshold=0.45,
        model_name="test-chat-model",
    )


def test_successful_grounded_answer_round_trips_with_configured_ttl() -> None:
    client = FakeRedisClient()
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)
    request = _request()
    response = _response()

    lookup = cache.lookup(request)
    cache.store(lookup, response)

    assert len(client.set_calls) == 1
    key, payload, ttl = client.set_calls[0]
    assert key.startswith("officecare:ai:rag-answer:v1:")
    assert request.question not in key
    assert "secret-that-must-not-enter-cache-keys" not in key
    assert "secret-that-must-not-enter-cache-keys" not in payload
    assert ttl == 900
    assert cache.lookup(request).response == response


def test_cache_key_ignores_document_version_order_but_covers_answer_inputs() -> None:
    cache = RedisRagAnswerCache(
        _settings(),
        prompt="prompt-v1",
        client=FakeRedisClient(),
    )

    original = cache.key_for(_request())

    assert original == cache.key_for(_request(documentVersionIds=[40, 41]))
    assert original != cache.key_for(_request(question="自动进稿器最多放多少张纸？"))
    assert original != cache.key_for(_request(scoreThreshold=0.55))
    assert original != cache.key_for(_request(documentVersionIds=[40]))


def test_prompt_or_model_change_invalidates_the_cache_key() -> None:
    request = _request()
    first = RedisRagAnswerCache(
        _settings(),
        prompt="prompt-v1",
        client=FakeRedisClient(),
    )
    changed_prompt = RedisRagAnswerCache(
        _settings(),
        prompt="prompt-v2",
        client=FakeRedisClient(),
    )
    changed_model = RedisRagAnswerCache(
        _settings(chat_model="another-model"),
        prompt="prompt-v1",
        client=FakeRedisClient(),
    )

    assert first.key_for(request) != changed_prompt.key_for(request)
    assert first.key_for(request) != changed_model.key_for(request)


def test_refusal_is_not_cached() -> None:
    client = FakeRedisClient()
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)

    cache.store(
        RagCacheLookup(cache_key=cache.key_for(_request())),
        _response(answered=False),
    )

    assert client.set_calls == []


def test_invalid_or_unsafe_cached_value_is_deleted_and_treated_as_miss() -> None:
    client = FakeRedisClient()
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)
    request = _request()
    key = cache.key_for(request)
    client.values[key] = "not-json"

    assert cache.lookup(request).response is None
    assert client.deleted == [key]

    client.values[key] = _response(answered=False).model_dump_json(by_alias=True)
    assert cache.lookup(request).response is None
    assert client.deleted == [key, key]


def test_redis_failures_do_not_block_rag_processing() -> None:
    client = FakeRedisClient()
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)
    request = _request()

    client.get_error = TimeoutError("redis read timed out")
    lookup = cache.lookup(request)
    assert lookup.cache_key is None
    assert lookup.response is None

    client.set_error = ConnectionError("redis write failed")
    cache.store(
        RagCacheLookup(cache_key=cache.key_for(request)),
        _response(),
    )

    stats = cache.stats()
    assert stats.errors == 2


def test_invalidation_changes_only_the_knowledge_base_revision() -> None:
    client = FakeRedisClient()
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)
    request = _request()
    other_request = _request(knowledgeBaseId=2)
    old_key = cache.lookup(request).cache_key
    other_key = cache.lookup(other_request).cache_key

    result = cache.invalidate(
        RagCacheInvalidateRequest(
            knowledge_base_id=1,
            reason="DOCUMENT_REINDEXED",
        )
    )

    assert result.revision == 1
    assert cache.lookup(request).cache_key != old_key
    assert cache.lookup(other_request).cache_key == other_key


def test_in_flight_answer_is_written_only_to_its_original_revision() -> None:
    client = FakeRedisClient()
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)
    request = _request()
    old_lookup = cache.lookup(request)

    cache.invalidate(
        RagCacheInvalidateRequest(
            knowledge_base_id=1,
            reason="DOCUMENT_REINDEXED",
        )
    )
    cache.store(old_lookup, _response())

    assert old_lookup.cache_key in client.values
    assert cache.lookup(request).response is None


def test_stats_report_cache_activity_without_exposing_connection_details() -> None:
    client = FakeRedisClient()
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)
    request = _request()

    lookup = cache.lookup(request)
    cache.store(lookup, _response())
    assert cache.lookup(request).response is not None
    cache.record_bypass("NO_EVIDENCE")
    cache.invalidate(
        RagCacheInvalidateRequest(
            knowledge_base_id=1,
            reason="MANUAL",
        )
    )

    stats = cache.stats(knowledge_base_id=1)
    assert stats.enabled is True
    assert stats.redis_reachable is True
    assert stats.revision == 1
    assert stats.hits == 1
    assert stats.misses == 1
    assert stats.writes == 1
    assert stats.bypasses == 1
    assert stats.invalidations == 1
    assert stats.hit_rate == 0.5
    assert not hasattr(stats, "redis_host")


def test_invalidation_failure_is_reported_instead_of_claiming_success() -> None:
    client = FakeRedisClient()
    client.incr_error = ConnectionError("redis unavailable")
    cache = RedisRagAnswerCache(_settings(), prompt="prompt-v1", client=client)

    with pytest.raises(ServiceError) as exc_info:
        cache.invalidate(
            RagCacheInvalidateRequest(
                knowledge_base_id=1,
                reason="MANUAL",
            )
        )

    assert exc_info.value.code == "RAG_CACHE_INVALIDATION_FAILED"
    assert cache.stats().errors == 1
