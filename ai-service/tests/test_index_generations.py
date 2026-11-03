import re

import pytest

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.main import app
from app.schemas.document import DocumentBlock, DocumentParseResponse
from app.schemas.generations import GenerationIndexRequest
from app.services.embedding import EmbeddingBatch
from app.services.generation_indexing import GenerationIndexingService, get_generation_indexing_service, get_generation_store
from app.services.generation_store import MilvusGenerationStore
from app.services.table_structure import html_table


class Embedding:
    model_name = "deterministic-test"
    dimension = 3

    def embed(self, texts):
        return EmbeddingBatch(vectors=[[1.0, float(len(text)), 0.1] for text in texts])


class Client:
    def __init__(self):
        self.rows = {}
        self.deleted = []
        self.collections = set()
        self.fail_write = False
        self.short_count = False

    def has_collection(self, **kw):
        self.collections.add(kw["collection_name"])
        return True

    def describe_collection(self, **kw):
        names = ["chunk_id", "knowledge_base_id", "brand_id", "model_id", "document_id",
                 "document_version_id", "page_number", "chunk_index", "text", "generation_id",
                 "parse_id", "profile", "provenance"]
        return {"fields": [{"name": name} for name in names] + [{"name": "embedding", "params": {"dim": 3}}]}

    def load_collection(self, **kw):
        pass

    def query(self, **kw):
        generation = re.search(r'"([a-f0-9]+)"', kw["filter"]).group(1)
        count = sum(row["generation_id"] == generation for row in self.rows.values())
        return [{"count(*)": max(0, count - 1) if self.short_count else count}]

    def upsert(self, **kw):
        self.rows.update({row["chunk_id"]: row for row in kw["data"]})
        if self.fail_write:
            raise RuntimeError("simulated partial write")

    def flush(self, **kw):
        pass

    def delete(self, **kw):
        self.deleted.append(kw["filter"])
        generation = re.search(r'"([a-f0-9]+)"', kw["filter"]).group(1)
        ids = [key for key, row in self.rows.items() if row["generation_id"] == generation]
        for key in ids:
            del self.rows[key]
        return {"delete_count": len(ids)}


def request(generation="1" * 32, profile="structured"):
    table = html_table(
        '<table><tr><th>型号</th><th>故障</th></tr>'
        '<tr><td>DCP-L2550DW</td><td>卡纸：打开后盖</td></tr>'
        '<tr><td>MFC-7895DW</td><td>传真：检查电话线</td></tr></table>',
    )
    blocks = [DocumentBlock(sequence=1, block_type="table", text="DCP-L2550DW\nMFC-7895DW", table=table)]
    blocks += [DocumentBlock(sequence=index, block_type="paragraph", text=f"测试说明 {index}。") for index in range(2, 107)]
    parsed = DocumentParseResponse(
        schema_version=2, normalization_version="structure-v2", file_name="synthetic.docx",
        file_type="docx", content_type="application/octet-stream", file_size=12, file_hash="a" * 64,
        blocks=blocks, block_count=len(blocks), character_count=sum(len(block.text) for block in blocks), warnings=[],
    )
    return GenerationIndexRequest(
        generation_id=generation, parse_id="b" * 64, profile=profile,
        knowledge_base_id=1, brand_id=2, model_id=3, document_id=4, document_version_id=5,
        version_no=1, document_title="合成手册", parsed=parsed,
    )


def fixture():
    client = Client()
    settings = Settings(embedding_dimension=3, milvus_collection="m2_test", chunk_size=800, chunk_overlap=100)
    store = MilvusGenerationStore(settings, client_factory=lambda: client)
    service = GenerationIndexingService(settings=settings, embedding_provider=Embedding(), vector_store=store)
    return client, store, service


def test_two_generations_preserve_all_candidates_and_model_row_sources():
    client, store, service = fixture()
    first = service.index(request())
    second = service.index(request("2" * 32))
    assert first.chunk_count == second.chunk_count == 107
    assert len(client.rows) == 214
    assert client.deleted == []
    assert client.collections == {"m2_test_generations"}
    row = next(row for row in client.rows.values() if "卡纸" in row["text"])
    assert row["provenance"]["modelScope"] == ["dcpl2550dw"]
    assert any(source["rowIndex"] == 1 for source in row["provenance"]["sources"])
    assert first.index_content_hash != second.index_content_hash
    with pytest.raises(ServiceError) as error:
        service.index(request())
    assert error.value.code == "GENERATION_ALREADY_EXISTS"
    assert len(client.rows) == 214


def test_failed_write_and_incomplete_count_do_not_remove_another_generation():
    client, store, service = fixture()
    service.index(request())
    client.fail_write = True
    with pytest.raises(ServiceError) as error:
        service.index(request("2" * 32))
    assert error.value.code == "MILVUS_WRITE_FAILED"
    assert {row["generation_id"] for row in client.rows.values()} == {"1" * 32}
    client.fail_write = False
    client.short_count = True
    with pytest.raises(ServiceError) as error:
        service.index(request("3" * 32))
    assert error.value.code == "GENERATION_INCOMPLETE"
    assert len(client.rows) == 107


def test_legacy_profile_keeps_original_chunk_text_and_identity():
    from app.services.text_chunker import chunk_document

    client, store, service = fixture()
    payload = request(profile="legacy")
    response = service.index(payload)
    original = chunk_document(payload.parsed.blocks, max_chars=800, overlap_chars=100)
    assert [row["text"] for row in client.rows.values()] == [chunk.text for chunk in original]
    assert response.profile == "legacy-v1"
    assert all(row["parse_id"] == "b" * 64 for row in client.rows.values())


def test_internal_generation_api_requires_authentication_and_uses_selected_store(client, auth_headers):
    memory, store, service = fixture()
    app.dependency_overrides[get_generation_indexing_service] = lambda: service
    app.dependency_overrides[get_generation_store] = lambda: store
    try:
        body = request().model_dump(mode="json", by_alias=True)
        path = "/api/v1/internal/documents/generations"
        assert client.post(path, json=body).status_code == 401
        response = client.post(path, json=body, headers=auth_headers)
        assert response.status_code == 200
        assert response.json()["generationId"] == "1" * 32
        response = client.delete(path + "/" + "1" * 32, headers=auth_headers)
        assert response.status_code == 200
        assert response.json()["deletedCount"] == 107
        assert not memory.rows
    finally:
        app.dependency_overrides.pop(get_generation_indexing_service, None)
        app.dependency_overrides.pop(get_generation_store, None)
