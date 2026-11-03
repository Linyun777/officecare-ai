import hashlib
from datetime import datetime, timezone
from functools import lru_cache

from app.core.config import get_settings
from app.core.exceptions import ServiceError
from app.schemas.preview import DocumentPreviewRequest
from app.schemas.generations import GenerationIndexRequest, GenerationIndexResponse
from app.services.document_indexing import _index_content_hash
from app.services.document_preview import build_preview_items
from app.services.embedding import OpenAICompatibleEmbeddingProvider
from app.services.generation_store import GenerationVectorRecord, MilvusGenerationStore


class GenerationIndexingService:
    def __init__(self, *, settings, embedding_provider, vector_store):
        self.settings = settings
        self.embedding = embedding_provider
        self.store = vector_store

    def index(self, request: GenerationIndexRequest) -> GenerationIndexResponse:
        parsed = request.parsed
        preview = DocumentPreviewRequest(
            document_version_id=request.document_version_id, file_hash=parsed.file_hash,
            parse_id=request.parse_id, parsed=parsed, view=request.profile,
        )
        builder, _ = build_preview_items(preview, self.settings)
        chunks = builder.items
        if not chunks:
            raise ServiceError(status_code=422, code="NO_INDEXABLE_TEXT", message="The parsed document has no indexable text.")
        vectors = []
        size = self.settings.embedding_batch_size
        for start in range(0, len(chunks), size):
            batch = chunks[start:start + size]
            embedded = self.embedding.embed([chunk.text for chunk in batch])
            if len(embedded.vectors) != len(batch):
                raise ServiceError(status_code=502, code="EMBEDDING_INVALID_RESPONSE", message="Embedding count does not match the candidates.")
            vectors.extend(embedded.vectors)
        records = []
        for chunk, vector in zip(chunks, vectors, strict=True):
            identity = f"{request.generation_id}:{chunk.ordinal}:{chunk.candidate_id}"
            sequences = [source.block_sequence for source in chunk.sources]
            records.append(GenerationVectorRecord(
                chunk_id=hashlib.sha256(identity.encode()).hexdigest(),
                knowledge_base_id=request.knowledge_base_id, brand_id=request.brand_id,
                model_id=request.model_id, document_id=request.document_id,
                document_version_id=request.document_version_id, version_no=request.version_no,
                page_number=chunk.page_number or 0, chunk_index=chunk.ordinal,
                block_start=min(sequences), block_end=max(sequences),
                chunk_hash=hashlib.sha256(chunk.text.encode()).hexdigest(), file_hash=parsed.file_hash.lower(),
                document_title=request.document_title, original_file_name=parsed.file_name,
                heading=chunk.heading_path[-1] if chunk.heading_path else "", text=chunk.text,
                embedding_model=self.embedding.model_name, embedding=vector,
                generation_id=request.generation_id, parse_id=request.parse_id, profile=builder.profile,
                provenance=chunk.model_dump(mode="json", by_alias=True, exclude={"text", "table"}),
            ))
        self.store.write_generation(request.generation_id, records)
        return GenerationIndexResponse(
            document_id=request.document_id, document_version_id=request.document_version_id,
            collection_name=self.store.collection_name, embedding_model=self.embedding.model_name,
            embedding_dimension=self.embedding.dimension, chunk_count=len(records),
            indexed_character_count=sum(len(record.text) for record in records),
            index_content_hash=_index_content_hash(records, model_name=self.embedding.model_name, dimension=self.embedding.dimension),
            indexed_at=datetime.now(timezone.utc), generation_id=request.generation_id,
            parse_id=request.parse_id, profile=builder.profile,
            chunk_size=self.settings.chunk_size, overlap_chars=self.settings.chunk_overlap,
        )


@lru_cache
def get_generation_indexing_service():
    settings = get_settings()
    return GenerationIndexingService(
        settings=settings, embedding_provider=OpenAICompatibleEmbeddingProvider(settings),
        vector_store=get_generation_store(),
    )


@lru_cache
def get_generation_store():
    return MilvusGenerationStore(get_settings())
