import json
import math
from dataclasses import dataclass
from typing import Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from langchain_core.embeddings import Embeddings

from app.core.config import Settings
from app.core.exceptions import ServiceError


@dataclass(frozen=True, slots=True)
class EmbeddingBatch:
    vectors: list[list[float]]
    token_count: int | None = None


class EmbeddingProvider(Protocol):
    @property
    def model_name(self) -> str:
        ...

    @property
    def dimension(self) -> int:
        ...

    def embed(self, texts: list[str]) -> EmbeddingBatch:
        ...

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        ...

    def embed_query(self, text: str) -> list[float]:
        ...


class OpenAICompatibleEmbeddingProvider(Embeddings):
    """Minimal OpenAI-compatible /embeddings client without provider lock-in."""

    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.embedding_base_url.rstrip("/")
        self._api_key = settings.embedding_key()
        self._model_name = settings.embedding_model
        self._dimension = settings.embedding_dimension
        self._timeout = settings.embedding_timeout_seconds

    @property
    def model_name(self) -> str:
        return self._model_name

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed(self, texts: list[str]) -> EmbeddingBatch:
        if not self._base_url or not self._model_name:
            raise ServiceError(
                status_code=503,
                code="EMBEDDING_NOT_CONFIGURED",
                message="The embedding API URL and model are not configured.",
            )
        if not texts:
            return EmbeddingBatch(vectors=[])

        body = json.dumps(
            {
                "model": self._model_name,
                "input": texts,
                "dimensions": self._dimension,
                "encoding_format": "float",
            },
            ensure_ascii=False,
        ).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"
        request = Request(
            f"{self._base_url}/embeddings",
            data=body,
            headers=headers,
            method="POST",
        )

        try:
            with urlopen(request, timeout=self._timeout) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            raise ServiceError(
                status_code=502,
                code="EMBEDDING_HTTP_ERROR",
                message=f"The embedding service returned HTTP {exc.code}.",
            ) from exc
        except (URLError, TimeoutError) as exc:
            raise ServiceError(
                status_code=503,
                code="EMBEDDING_UNAVAILABLE",
                message="The embedding service is unavailable or timed out.",
            ) from exc
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ServiceError(
                status_code=502,
                code="EMBEDDING_INVALID_RESPONSE",
                message="The embedding service returned an invalid JSON response.",
            ) from exc

        data = payload.get("data")
        if (
            not isinstance(data, list)
            or len(data) != len(texts)
            or not all(
                isinstance(item, dict)
                and isinstance(item.get("index"), int)
                and not isinstance(item.get("index"), bool)
                for item in data
            )
        ):
            raise ServiceError(
                status_code=502,
                code="EMBEDDING_INVALID_RESPONSE",
                message="The embedding response count does not match the request.",
            )

        ordered = sorted(data, key=lambda item: item["index"])
        if [item["index"] for item in ordered] != list(range(len(texts))):
            raise ServiceError(
                status_code=502,
                code="EMBEDDING_INVALID_RESPONSE",
                message="The embedding response indexes are invalid.",
            )
        vectors = [item.get("embedding") for item in ordered]
        if not all(self._valid_vector(vector) for vector in vectors):
            raise ServiceError(
                status_code=502,
                code="EMBEDDING_DIMENSION_MISMATCH",
                message=(
                    "The embedding service returned a vector with an unexpected "
                    f"dimension; expected {self._dimension}."
                ),
            )

        usage = payload.get("usage")
        token_count = usage.get("total_tokens") if isinstance(usage, dict) else None
        return EmbeddingBatch(
            vectors=[[float(value) for value in vector] for vector in vectors],
            token_count=token_count if isinstance(token_count, int) else None,
        )

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """LangChain Embeddings interface used by the RAG layer."""
        return self.embed(texts).vectors

    def embed_query(self, text: str) -> list[float]:
        """Embed one retrieval query in the same vector space as document chunks."""
        return self.embed([text]).vectors[0]

    def _valid_vector(self, vector: object) -> bool:
        return (
            isinstance(vector, list)
            and len(vector) == self._dimension
            and all(
                isinstance(value, (int, float)) and math.isfinite(value)
                for value in vector
            )
        )
