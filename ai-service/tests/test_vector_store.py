from app.core.config import Settings
from app.services.vector_store import MilvusVectorStore, VectorRecord


class FakeMilvusClient:
    def __init__(self) -> None:
        self.deleted_filters: list[str] = []
        self.upserted: list[dict] = []
        self.flush_count = 0

    def has_collection(self, **kwargs) -> bool:
        return True

    def describe_collection(self, **kwargs) -> dict:
        names = [
            "chunk_id",
            "knowledge_base_id",
            "brand_id",
            "model_id",
            "document_id",
            "document_version_id",
            "page_number",
            "chunk_index",
            "text",
        ]
        fields = [{"name": name, "params": {}} for name in names]
        fields.append({"name": "embedding", "params": {"dim": "3"}})
        return {"fields": fields}

    def delete(self, **kwargs) -> dict:
        self.deleted_filters.append(kwargs["filter"])
        return {"delete_count": 2}

    def upsert(self, **kwargs) -> dict:
        self.upserted.extend(kwargs["data"])
        return {"upsert_count": len(kwargs["data"])}

    def flush(self, **kwargs) -> None:
        self.flush_count += 1


def _settings() -> Settings:
    return Settings(
        embedding_dimension=3,
        milvus_uri="http://milvus.test:19530",
        milvus_collection="officecare_test_chunks",
    )


def _record() -> VectorRecord:
    return VectorRecord(
        chunk_id="c" * 64,
        knowledge_base_id=1,
        brand_id=2,
        model_id=3,
        document_id=4,
        document_version_id=5,
        version_no=1,
        page_number=6,
        chunk_index=0,
        block_start=1,
        block_end=2,
        chunk_hash="a" * 64,
        file_hash="b" * 64,
        document_title="维修手册",
        original_file_name="manual.pdf",
        heading="故障排除",
        text="错误代码 E01 表示卡纸。",
        embedding_model="test-model",
        embedding=[0.1, 0.2, 0.3],
    )


def test_replace_version_deletes_before_upsert() -> None:
    client = FakeMilvusClient()
    store = MilvusVectorStore(_settings(), client_factory=lambda: client)

    store.replace_version(5, [_record()])

    assert client.deleted_filters == ["document_version_id == 5"]
    assert len(client.upserted) == 1
    assert client.upserted[0]["chunk_id"] == "c" * 64
    assert client.flush_count == 1


def test_delete_version_returns_deleted_count() -> None:
    client = FakeMilvusClient()
    store = MilvusVectorStore(_settings(), client_factory=lambda: client)

    deleted_count = store.delete_version(5)

    assert deleted_count == 2
    assert client.deleted_filters == ["document_version_id == 5"]
