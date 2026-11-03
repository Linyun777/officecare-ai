from dataclasses import asdict, dataclass
import logging
import re
from typing import Any

from app.core.exceptions import ServiceError
from app.services.vector_store import MilvusVectorStore, VectorRecord


LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class GenerationVectorRecord(VectorRecord):
    generation_id: str
    parse_id: str
    profile: str
    provenance: dict


class MilvusGenerationStore(MilvusVectorStore):
    """A separate collection; no generation write can replace a legacy version."""

    TEXT_MAX_BYTES = 65535
    CONSISTENCY_LEVEL = "Strong"

    def __init__(self, settings, *, client_factory=None):
        super().__init__(settings, client_factory=client_factory)
        self._collection_name = settings.milvus_collection + "_generations"

    def _add_schema_fields(self, schema: Any) -> None:
        from pymilvus import DataType

        schema.add_field(field_name="generation_id", datatype=DataType.VARCHAR, max_length=32)
        schema.add_field(field_name="parse_id", datatype=DataType.VARCHAR, max_length=64)
        schema.add_field(field_name="profile", datatype=DataType.VARCHAR, max_length=64)
        schema.add_field(field_name="provenance", datatype=DataType.JSON)

    def _validate_collection(self, client):
        super()._validate_collection(client)
        fields = {
            field["name"] for field in client.describe_collection(
                collection_name=self.collection_name, timeout=self._timeout,
            ).get("fields", [])
        }
        if not {"generation_id", "parse_id", "profile", "provenance"}.issubset(fields):
            raise ServiceError(
                status_code=503, code="MILVUS_SCHEMA_MISMATCH",
                message="The generation collection does not preserve index identity and sources.",
            )

    @staticmethod
    def _generation_filter(generation_id):
        if not re.fullmatch(r"[a-f0-9]{32}", generation_id):
            raise ServiceError(status_code=422, code="INVALID_GENERATION_ID", message="Invalid generation ID.")
        return f'generation_id == "{generation_id}"'

    def write_generation(self, generation_id: str, records: list[GenerationVectorRecord]) -> None:
        scalar_filter = self._generation_filter(generation_id)
        if not records or any(record.generation_id != generation_id for record in records):
            raise ServiceError(status_code=422, code="INVALID_GENERATION_RECORDS", message="Generation records are empty or inconsistent.")
        client = self._get_client()
        self._ensure_collection(client)
        client.load_collection(collection_name=self.collection_name, timeout=self._timeout)
        if self._count(client, scalar_filter):
            raise ServiceError(status_code=409, code="GENERATION_ALREADY_EXISTS", message="An existing generation cannot be overwritten.")
        try:
            for start in range(0, len(records), 100):
                client.upsert(
                    collection_name=self.collection_name,
                    data=[asdict(record) for record in records[start:start + 100]],
                    timeout=self._timeout,
                )
            client.flush(collection_name=self.collection_name, timeout=self._timeout)
            if self._count(client, scalar_filter) != len(records):
                raise ServiceError(status_code=503, code="GENERATION_INCOMPLETE", message="The generation is not completely stored.")
        except Exception as exc:
            try:
                self.delete_generation(generation_id)
            except Exception as cleanup:
                LOGGER.warning("Failed generation cleanup deferred: %s", type(cleanup).__name__)
            if isinstance(exc, ServiceError):
                raise
            raise ServiceError(status_code=503, code="MILVUS_WRITE_FAILED", message="Milvus could not store the index generation.") from exc

    def _count(self, client, scalar_filter):
        result = client.query(
            collection_name=self.collection_name, filter=scalar_filter,
            output_fields=["count(*)"], consistency_level="Strong", timeout=self._timeout,
        )
        return int(result[0]["count(*)"]) if result else 0

    def delete_generation(self, generation_id):
        scalar_filter = self._generation_filter(generation_id)
        client = self._get_client()
        if not client.has_collection(collection_name=self.collection_name, timeout=self._timeout):
            return 0
        result = client.delete(
            collection_name=self.collection_name, filter=scalar_filter,
            timeout=self._timeout,
        )
        client.flush(collection_name=self.collection_name, timeout=self._timeout)
        return int(result.get("delete_count", 0))
