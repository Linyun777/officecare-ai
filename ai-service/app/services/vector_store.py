from dataclasses import asdict, dataclass
from typing import Any, Callable, Protocol

from app.core.config import Settings
from app.core.exceptions import ServiceError


@dataclass(frozen=True, slots=True)
class VectorRecord:
    chunk_id: str
    knowledge_base_id: int
    brand_id: int
    model_id: int
    document_id: int
    document_version_id: int
    version_no: int
    page_number: int
    chunk_index: int
    block_start: int
    block_end: int
    chunk_hash: str
    file_hash: str
    document_title: str
    original_file_name: str
    heading: str
    text: str
    embedding_model: str
    embedding: list[float]


class VectorStore(Protocol):
    @property
    def collection_name(self) -> str:
        ...

    def replace_version(
        self,
        document_version_id: int,
        records: list[VectorRecord],
    ) -> None:
        ...

    def delete_version(self, document_version_id: int) -> int:
        ...


class MilvusVectorStore:
    """PyMilvus-backed store with explicit schema and idempotent version replacement."""

    def __init__(
        self,
        settings: Settings,
        *,
        client_factory: Callable[[], Any] | None = None,
    ) -> None:
        self._settings = settings
        self._collection_name = settings.milvus_collection
        self._dimension = settings.embedding_dimension
        self._timeout = settings.milvus_timeout_seconds
        self._client_factory = client_factory
        self._client: Any | None = None

    @property
    def collection_name(self) -> str:
        return self._collection_name

    def replace_version(
        self,
        document_version_id: int,
        records: list[VectorRecord],
    ) -> None:
        if not records:
            raise ServiceError(
                status_code=422,
                code="NO_INDEXABLE_TEXT",
                message="No indexable text chunks were produced.",
            )

        client = self._get_client()
        try:
            self._ensure_collection(client)
            client.delete(
                collection_name=self._collection_name,
                filter=f"document_version_id == {int(document_version_id)}",
                timeout=self._timeout,
            )
            for start in range(0, len(records), 100):
                client.upsert(
                    collection_name=self._collection_name,
                    data=[asdict(record) for record in records[start:start + 100]],
                    timeout=self._timeout,
                )
            client.flush(
                collection_name=self._collection_name,
                timeout=self._timeout,
            )
        except ServiceError:
            raise
        except Exception as exc:
            raise ServiceError(
                status_code=503,
                code="MILVUS_WRITE_FAILED",
                message="Milvus could not replace the document version index.",
            ) from exc

    def delete_version(self, document_version_id: int) -> int:
        client = self._get_client()
        try:
            if not client.has_collection(
                collection_name=self._collection_name,
                timeout=self._timeout,
            ):
                return 0
            result = client.delete(
                collection_name=self._collection_name,
                filter=f"document_version_id == {int(document_version_id)}",
                timeout=self._timeout,
            )
            client.flush(
                collection_name=self._collection_name,
                timeout=self._timeout,
            )
            deleted_count = result.get("delete_count", 0) if isinstance(result, dict) else 0
            return int(deleted_count or 0)
        except Exception as exc:
            raise ServiceError(
                status_code=503,
                code="MILVUS_DELETE_FAILED",
                message="Milvus could not delete the document version index.",
            ) from exc

    def _get_client(self) -> Any:
        if self._client is not None:
            return self._client
        if not self._settings.milvus_uri:
            raise ServiceError(
                status_code=503,
                code="MILVUS_NOT_CONFIGURED",
                message="The Milvus URI is not configured.",
            )
        if self._client_factory is not None:
            self._client = self._client_factory()
            return self._client

        try:
            from pymilvus import MilvusClient

            arguments: dict[str, Any] = {
                "uri": self._settings.milvus_uri,
                "db_name": self._settings.milvus_database,
            }
            token = self._settings.milvus_auth_token()
            if token:
                arguments["token"] = token
            self._client = MilvusClient(**arguments)
            return self._client
        except Exception as exc:
            raise ServiceError(
                status_code=503,
                code="MILVUS_UNAVAILABLE",
                message="Milvus is unavailable or its client cannot be initialized.",
            ) from exc

    def _ensure_collection(self, client: Any) -> None:
        if client.has_collection(
            collection_name=self._collection_name,
            timeout=self._timeout,
        ):
            self._validate_collection(client)
            return

        from pymilvus import DataType

        schema = client.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field(
            field_name="chunk_id",
            datatype=DataType.VARCHAR,
            is_primary=True,
            max_length=64,
        )
        schema.add_field(
            field_name="knowledge_base_id",
            datatype=DataType.INT64,
        )
        schema.add_field(field_name="brand_id", datatype=DataType.INT64)
        schema.add_field(field_name="model_id", datatype=DataType.INT64)
        schema.add_field(field_name="document_id", datatype=DataType.INT64)
        schema.add_field(field_name="document_version_id", datatype=DataType.INT64)
        schema.add_field(field_name="version_no", datatype=DataType.INT64)
        schema.add_field(field_name="page_number", datatype=DataType.INT64)
        schema.add_field(field_name="chunk_index", datatype=DataType.INT64)
        schema.add_field(field_name="block_start", datatype=DataType.INT64)
        schema.add_field(field_name="block_end", datatype=DataType.INT64)
        schema.add_field(
            field_name="chunk_hash",
            datatype=DataType.VARCHAR,
            max_length=64,
        )
        schema.add_field(
            field_name="file_hash",
            datatype=DataType.VARCHAR,
            max_length=64,
        )
        schema.add_field(
            field_name="document_title",
            datatype=DataType.VARCHAR,
            max_length=512,
        )
        schema.add_field(
            field_name="original_file_name",
            datatype=DataType.VARCHAR,
            max_length=512,
        )
        schema.add_field(
            field_name="heading",
            datatype=DataType.VARCHAR,
            max_length=1024,
        )
        schema.add_field(
            field_name="text",
            datatype=DataType.VARCHAR,
            max_length=8192,
        )
        schema.add_field(
            field_name="embedding_model",
            datatype=DataType.VARCHAR,
            max_length=256,
        )
        schema.add_field(
            field_name="embedding",
            datatype=DataType.FLOAT_VECTOR,
            dim=self._dimension,
        )

        index_params = client.prepare_index_params()
        index_params.add_index(
            field_name="embedding",
            index_type="AUTOINDEX",
            metric_type="COSINE",
        )
        try:
            client.create_collection(
                collection_name=self._collection_name,
                schema=schema,
                index_params=index_params,
                consistency_level="Bounded",
                timeout=self._timeout,
            )
        except Exception:
            if not client.has_collection(
                collection_name=self._collection_name,
                timeout=self._timeout,
            ):
                raise
        self._validate_collection(client)

    def _validate_collection(self, client: Any) -> None:
        description = client.describe_collection(
            collection_name=self._collection_name,
            timeout=self._timeout,
        )
        fields = {
            field.get("name"): field
            for field in description.get("fields", [])
            if isinstance(field, dict)
        }
        required = {
            "chunk_id",
            "knowledge_base_id",
            "brand_id",
            "model_id",
            "document_id",
            "document_version_id",
            "page_number",
            "chunk_index",
            "text",
            "embedding",
        }
        if not required.issubset(fields):
            raise ServiceError(
                status_code=503,
                code="MILVUS_SCHEMA_MISMATCH",
                message="The existing Milvus collection schema is incompatible.",
            )
        vector_params = fields["embedding"].get("params", {})
        actual_dimension = vector_params.get("dim")
        if actual_dimension is not None and int(actual_dimension) != self._dimension:
            raise ServiceError(
                status_code=503,
                code="MILVUS_DIMENSION_MISMATCH",
                message=(
                    "The existing Milvus collection vector dimension does not "
                    "match the configured embedding dimension."
                ),
            )
