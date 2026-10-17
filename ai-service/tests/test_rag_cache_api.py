from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import app
from app.services.rag_answer_cache import NoOpRagAnswerCache, RedisRagAnswerCache
from app.services.rag_answering import RagAnsweringService, get_rag_answering_service


STATS_URL = "/api/v1/internal/rag/cache/stats"
INVALIDATE_URL = "/api/v1/internal/rag/cache/invalidate"


class FakeRedisClient:
    def __init__(self) -> None:
        self.values: dict[str, str] = {}

    def get(self, name: str) -> str | None:
        return self.values.get(name)

    def set(self, name: str, value: str, *, ex: int) -> bool:
        self.values[name] = value
        return True

    def delete(self, *names: str) -> int:
        for name in names:
            self.values.pop(name, None)
        return len(names)

    def incr(self, name: str, amount: int = 1) -> int:
        current = int(self.values.get(name, "0")) + amount
        self.values[name] = str(current)
        return current

    def ping(self) -> bool:
        return True


class UnusedRetriever:
    def search(self, request):
        raise AssertionError("retriever should not be called")


class UnusedGenerator:
    model_name = "test-model"

    def generate(self, question, evidence):
        raise AssertionError("generator should not be called")


@pytest.fixture
def cache_api_client() -> Iterator[TestClient]:
    settings = Settings(
        _env_file=None,
        redis_enabled=True,
        redis_host="redis.test",
        embedding_model="embedding-model",
        chat_model="chat-model",
    )
    cache = RedisRagAnswerCache(
        settings,
        prompt="prompt-v1",
        client=FakeRedisClient(),
    )
    service = RagAnsweringService(
        retriever=UnusedRetriever(),
        generator=UnusedGenerator(),
        cache=cache,
    )
    app.dependency_overrides[get_rag_answering_service] = lambda: service
    try:
        with TestClient(app, raise_server_exceptions=False) as client:
            yield client
    finally:
        app.dependency_overrides.pop(get_rag_answering_service, None)


def test_cache_stats_endpoint_is_protected_and_does_not_expose_secrets(
    cache_api_client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    unauthorized = cache_api_client.get(STATS_URL)
    assert unauthorized.status_code == 401

    response = cache_api_client.get(
        STATS_URL,
        params={"knowledgeBaseId": 1},
        headers=auth_headers,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["enabled"] is True
    assert body["redisReachable"] is True
    assert body["knowledgeBaseId"] == 1
    assert body["revision"] == 0
    assert body["metricsScope"] == "PROCESS"
    assert "redisHost" not in body
    assert "password" not in body


def test_cache_invalidation_endpoint_increments_revision(
    cache_api_client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = cache_api_client.post(
        INVALIDATE_URL,
        headers=auth_headers,
        json={"knowledgeBaseId": 1, "reason": "document_reindexed"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["knowledgeBaseId"] == 1
    assert body["revision"] == 1
    assert body["reason"] == "DOCUMENT_REINDEXED"
    assert body["invalidatedAt"].endswith("Z")

    stats = cache_api_client.get(
        STATS_URL,
        params={"knowledgeBaseId": 1},
        headers=auth_headers,
    ).json()
    assert stats["revision"] == 1
    assert stats["invalidations"] == 1


def test_cache_invalidation_rejects_invalid_reason(
    cache_api_client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = cache_api_client.post(
        INVALIDATE_URL,
        headers=auth_headers,
        json={"knowledgeBaseId": 1, "reason": "contains spaces"},
    )

    assert response.status_code == 422
    assert response.json()["code"] == "REQUEST_VALIDATION_ERROR"


def test_disabled_cache_reports_disabled_and_rejects_invalidation(
    auth_headers: dict[str, str],
) -> None:
    settings = Settings(_env_file=None, redis_enabled=False)
    service = RagAnsweringService(
        retriever=UnusedRetriever(),
        generator=UnusedGenerator(),
        cache=NoOpRagAnswerCache(settings),
    )
    app.dependency_overrides[get_rag_answering_service] = lambda: service
    try:
        with TestClient(app, raise_server_exceptions=False) as client:
            stats = client.get(STATS_URL, headers=auth_headers)
            invalidation = client.post(
                INVALIDATE_URL,
                headers=auth_headers,
                json={"knowledgeBaseId": 1, "reason": "MANUAL"},
            )
    finally:
        app.dependency_overrides.pop(get_rag_answering_service, None)

    assert stats.status_code == 200
    assert stats.json()["enabled"] is False
    assert invalidation.status_code == 409
    assert invalidation.json()["code"] == "RAG_CACHE_DISABLED"
