from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app
from app.services.document_indexing import get_document_indexing_service
from app.services.rag_answering import get_rag_answering_service
from app.services.troubleshooting_agent import get_troubleshooting_agent_service


@pytest.fixture(autouse=True)
def configure_test_environment(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    monkeypatch.setenv("OFFICECARE_AI_SERVICE_TOKEN", "test-internal-token")
    monkeypatch.setenv("OFFICECARE_ENVIRONMENT", "test")
    monkeypatch.setenv("OFFICECARE_MAX_DOCUMENT_SIZE_MB", "30")
    monkeypatch.setenv("OFFICECARE_DOCUMENT_PARSER", "local")
    monkeypatch.setenv("OFFICECARE_EMBEDDING_BASE_URL", "http://embedding.test/v1")
    monkeypatch.setenv("OFFICECARE_EMBEDDING_API_KEY", "test-embedding-key")
    monkeypatch.setenv("OFFICECARE_EMBEDDING_MODEL", "test-embedding-model")
    monkeypatch.setenv("OFFICECARE_EMBEDDING_DIMENSION", "3")
    monkeypatch.setenv("OFFICECARE_CHAT_BASE_URL", "http://chat.test")
    monkeypatch.setenv("OFFICECARE_CHAT_API_KEY", "test-chat-key")
    monkeypatch.setenv("OFFICECARE_CHAT_MODEL", "test-chat-model")
    monkeypatch.setenv("OFFICECARE_AGENT_MEMORY_ENABLED", "false")
    monkeypatch.setenv("OFFICECARE_REDIS_ENABLED", "false")
    monkeypatch.setenv("OFFICECARE_EMBEDDING_BATCH_SIZE", "2")
    monkeypatch.setenv("OFFICECARE_MILVUS_URI", "http://milvus.test:19530")
    monkeypatch.setenv("OFFICECARE_MILVUS_COLLECTION", "officecare_test_chunks")
    monkeypatch.setenv("OFFICECARE_CHUNK_SIZE", "200")
    monkeypatch.setenv("OFFICECARE_CHUNK_OVERLAP", "20")
    get_settings.cache_clear()
    get_document_indexing_service.cache_clear()
    get_rag_answering_service.cache_clear()
    get_troubleshooting_agent_service.cache_clear()
    yield
    get_settings.cache_clear()
    get_document_indexing_service.cache_clear()
    get_rag_answering_service.cache_clear()
    get_troubleshooting_agent_service.cache_clear()


@pytest.fixture
def client() -> Iterator[TestClient]:
    with TestClient(app, raise_server_exceptions=False) as test_client:
        yield test_client


@pytest.fixture
def auth_headers() -> dict[str, str]:
    return {"X-OfficeCare-Internal-Token": "test-internal-token"}
