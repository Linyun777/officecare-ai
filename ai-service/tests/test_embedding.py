import json
from io import BytesIO

import pytest

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.services import embedding
from app.services.embedding import OpenAICompatibleEmbeddingProvider


class FakeHttpResponse:
    def __init__(self, payload: dict) -> None:
        self._body = BytesIO(json.dumps(payload).encode("utf-8"))

    def __enter__(self) -> "FakeHttpResponse":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def read(self) -> bytes:
        return self._body.read()


def _provider() -> OpenAICompatibleEmbeddingProvider:
    return OpenAICompatibleEmbeddingProvider(
        Settings(
            embedding_base_url="http://embedding.test/v1",
            embedding_model="test-model",
            embedding_dimension=3,
        )
    )


def test_embedding_provider_restores_provider_response_order(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "data": [
            {"index": 1, "embedding": [0.4, 0.5, 0.6]},
            {"index": 0, "embedding": [0.1, 0.2, 0.3]},
        ],
        "usage": {"total_tokens": 8},
    }
    captured_request: dict = {}

    def fake_urlopen(request, **kwargs):
        captured_request["body"] = json.loads(request.data.decode("utf-8"))
        return FakeHttpResponse(payload)

    monkeypatch.setattr(embedding, "urlopen", fake_urlopen)

    result = _provider().embed(["第一段", "第二段"])

    assert result.vectors == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]
    assert result.token_count == 8
    assert captured_request["body"] == {
        "model": "test-model",
        "input": ["第一段", "第二段"],
        "dimensions": 3,
        "encoding_format": "float",
    }


def test_embedding_provider_rejects_duplicate_indexes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "data": [
            {"index": 0, "embedding": [0.1, 0.2, 0.3]},
            {"index": 0, "embedding": [0.4, 0.5, 0.6]},
        ]
    }
    monkeypatch.setattr(
        embedding,
        "urlopen",
        lambda *args, **kwargs: FakeHttpResponse(payload),
    )

    with pytest.raises(ServiceError) as error:
        _provider().embed(["第一段", "第二段"])

    assert error.value.code == "EMBEDDING_INVALID_RESPONSE"


def test_embedding_provider_implements_langchain_query_interface(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    payload = {
        "data": [{"index": 0, "embedding": [0.1, 0.2, 0.3]}],
        "usage": {"total_tokens": 3},
    }
    monkeypatch.setattr(
        embedding,
        "urlopen",
        lambda *args, **kwargs: FakeHttpResponse(payload),
    )

    assert _provider().embed_query("卡纸") == [0.1, 0.2, 0.3]
