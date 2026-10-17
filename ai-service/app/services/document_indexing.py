import hashlib
from datetime import datetime, timezone
from functools import lru_cache

from app.core.config import Settings, get_settings
from app.core.exceptions import ServiceError
from app.schemas.indexing import (
    DocumentIndexDeleteResponse,
    DocumentIndexRequest,
    DocumentIndexResponse,
)
from app.services.embedding import (
    EmbeddingProvider,
    OpenAICompatibleEmbeddingProvider,
)
from app.services.text_chunker import chunk_document
from app.services.vector_store import (
    MilvusVectorStore,
    VectorRecord,
    VectorStore,
)


class DocumentIndexingService:
    def __init__(
        self,
        *,
        settings: Settings,
        embedding_provider: EmbeddingProvider,
        vector_store: VectorStore,
    ) -> None:
        self._settings = settings
        self._embedding_provider = embedding_provider
        self._vector_store = vector_store

    def index_document(self, request: DocumentIndexRequest) -> DocumentIndexResponse:
        chunks = chunk_document(
            request.blocks,
            max_chars=self._settings.chunk_size,
            overlap_chars=self._settings.chunk_overlap,
        )
        if not chunks:
            raise ServiceError(
                status_code=422,
                code="NO_INDEXABLE_TEXT",
                message="The parsed document did not produce any indexable text.",
            )

        vectors: list[list[float]] = []
        batch_size = self._settings.embedding_batch_size
        for start in range(0, len(chunks), batch_size):
            batch = chunks[start:start + batch_size]
            embedding_batch = self._embedding_provider.embed(
                [chunk.text for chunk in batch]
            )
            if len(embedding_batch.vectors) != len(batch):
                raise ServiceError(
                    status_code=502,
                    code="EMBEDDING_INVALID_RESPONSE",
                    message="The embedding response count does not match the chunks.",
                )
            vectors.extend(embedding_batch.vectors)

        records = [
            VectorRecord(
                chunk_id=_chunk_id(request, chunk.chunk_index, chunk.chunk_hash),
                knowledge_base_id=request.knowledge_base_id,
                brand_id=request.brand_id,
                model_id=request.model_id,
                document_id=request.document_id,
                document_version_id=request.document_version_id,
                version_no=request.version_no,
                page_number=chunk.page_number or 0,
                chunk_index=chunk.chunk_index,
                block_start=chunk.block_start,
                block_end=chunk.block_end,
                chunk_hash=chunk.chunk_hash,
                file_hash=request.file_hash.lower(),
                document_title=request.document_title,
                original_file_name=request.original_file_name,
                heading=chunk.heading,
                text=chunk.text,
                embedding_model=self._embedding_provider.model_name,
                embedding=vector,
            )
            for chunk, vector in zip(chunks, vectors, strict=True)
        ]
        self._vector_store.replace_version(request.document_version_id, records)

        index_content_hash = _index_content_hash(
            records,
            model_name=self._embedding_provider.model_name,
            dimension=self._embedding_provider.dimension,
        )
        return DocumentIndexResponse(
            document_id=request.document_id,
            document_version_id=request.document_version_id,
            collection_name=self._vector_store.collection_name,
            embedding_model=self._embedding_provider.model_name,
            embedding_dimension=self._embedding_provider.dimension,
            chunk_count=len(records),
            indexed_character_count=sum(len(record.text) for record in records),
            index_content_hash=index_content_hash,
            indexed_at=datetime.now(timezone.utc),
        )

    def delete_document_version(
        self,
        document_version_id: int,
    ) -> DocumentIndexDeleteResponse:
        deleted_count = self._vector_store.delete_version(document_version_id)
        return DocumentIndexDeleteResponse(
            document_version_id=document_version_id,
            collection_name=self._vector_store.collection_name,
            deleted_count=deleted_count,
        )


def _chunk_id(
    request: DocumentIndexRequest,
    chunk_index: int,
    chunk_hash: str,
) -> str:
    source = (
        f"{request.document_version_id}:{request.file_hash.lower()}:"
        f"{chunk_index}:{chunk_hash}"
    )
    return hashlib.sha256(source.encode("utf-8")).hexdigest()


def _index_content_hash(
    records: list[VectorRecord],
    *,
    model_name: str,
    dimension: int,
) -> str:
    digest = hashlib.sha256()
    digest.update(model_name.encode("utf-8"))
    digest.update(str(dimension).encode("ascii"))
    for record in records:
        digest.update(record.chunk_id.encode("ascii"))
        digest.update(record.chunk_hash.encode("ascii"))
    return digest.hexdigest()


@lru_cache
def get_document_indexing_service() -> DocumentIndexingService:
    settings = get_settings()
    return DocumentIndexingService(
        settings=settings,
        embedding_provider=OpenAICompatibleEmbeddingProvider(settings),
        vector_store=MilvusVectorStore(settings),
    )
