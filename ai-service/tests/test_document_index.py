from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import app
from app.services.document_indexing import (
    DocumentIndexingService,
    get_document_indexing_service,
)
from app.services.embedding import EmbeddingBatch
from app.services.vector_store import VectorRecord


INDEX_URL = "/api/v1/internal/documents/index"


class FakeEmbeddingProvider:
    model_name = "test-embedding-model"
    dimension = 3

    def embed(self, texts: list[str]) -> EmbeddingBatch:
        return EmbeddingBatch(
            vectors=[
                [float(len(text)), float(index), 1.0]
                for index, text in enumerate(texts)
            ],
            token_count=len(texts),
        )


class FakeVectorStore:
    collection_name = "officecare_test_chunks"

    def __init__(self) -> None:
        self.replaced_version_id: int | None = None
        self.records: list[VectorRecord] = []
        self.deleted_version_id: int | None = None

    def replace_version(
        self,
        document_version_id: int,
        records: list[VectorRecord],
    ) -> None:
        self.replaced_version_id = document_version_id
        self.records = records

    def delete_version(self, document_version_id: int) -> int:
        self.deleted_version_id = document_version_id
        return 4


@pytest.fixture
def vector_store() -> FakeVectorStore:
    return FakeVectorStore()


@pytest.fixture
def indexing_service(vector_store: FakeVectorStore) -> DocumentIndexingService:
    return DocumentIndexingService(
        settings=get_settings(),
        embedding_provider=FakeEmbeddingProvider(),
        vector_store=vector_store,
    )


@pytest.fixture
def index_client(
    indexing_service: DocumentIndexingService,
) -> Iterator[TestClient]:
    app.dependency_overrides[get_document_indexing_service] = lambda: indexing_service
    try:
        with TestClient(app, raise_server_exceptions=False) as test_client:
            yield test_client
    finally:
        app.dependency_overrides.pop(get_document_indexing_service, None)


def _request_body() -> dict:
    texts = ["故障排除", "错误代码 E01 表示发生卡纸。", "请关闭电源并取出纸张。"]
    return {
        "knowledgeBaseId": 1,
        "brandId": 10,
        "modelId": 20,
        "documentId": 30,
        "documentVersionId": 40,
        "versionNo": 1,
        "documentTitle": "打印机维修手册",
        "originalFileName": "manual.pdf",
        "fileHash": "a" * 64,
        "blockCount": 3,
        "characterCount": sum(len(text) for text in texts),
        "blocks": [
            {
                "sequence": 1,
                "pageNumber": 3,
                "blockType": "heading",
                "text": texts[0],
            },
            {
                "sequence": 2,
                "pageNumber": 3,
                "blockType": "paragraph",
                "text": texts[1],
            },
            {
                "sequence": 3,
                "pageNumber": 3,
                "blockType": "paragraph",
                "text": texts[2],
            },
        ],
    }


def test_index_endpoint_writes_chunks_with_officecare_metadata(
    index_client: TestClient,
    auth_headers: dict[str, str],
    vector_store: FakeVectorStore,
) -> None:
    response = index_client.post(
        INDEX_URL,
        headers=auth_headers,
        json=_request_body(),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["documentVersionId"] == 40
    assert body["embeddingModel"] == "test-embedding-model"
    assert body["embeddingDimension"] == 3
    assert body["chunkCount"] == 1
    assert len(body["indexContentHash"]) == 64
    assert vector_store.replaced_version_id == 40
    assert vector_store.records[0].knowledge_base_id == 1
    assert vector_store.records[0].brand_id == 10
    assert vector_store.records[0].model_id == 20
    assert vector_store.records[0].page_number == 3
    assert vector_store.records[0].chunk_id


def test_index_endpoint_rejects_mismatched_parse_statistics(
    index_client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    request = _request_body()
    request["blockCount"] = 9

    response = index_client.post(
        INDEX_URL,
        headers=auth_headers,
        json=request,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "REQUEST_VALIDATION_ERROR"


def test_index_endpoint_requires_internal_token(
    index_client: TestClient,
) -> None:
    response = index_client.post(INDEX_URL, json=_request_body())

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_delete_index_is_idempotent(
    index_client: TestClient,
    auth_headers: dict[str, str],
    vector_store: FakeVectorStore,
) -> None:
    response = index_client.delete(
        f"{INDEX_URL}/40",
        headers=auth_headers,
    )

    assert response.status_code == 200
    assert response.json() == {
        "documentVersionId": 40,
        "collectionName": "officecare_test_chunks",
        "deletedCount": 4,
    }
    assert vector_store.deleted_version_id == 40
