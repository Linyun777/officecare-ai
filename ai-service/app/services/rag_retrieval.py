from dataclasses import dataclass
import logging
from typing import Any, Callable, Protocol

from langchain_core.documents import Document

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.schemas.rag import RagAnswerRequest
from app.services.embedding import EmbeddingProvider
from app.services.retrieval_quality import (
    is_applicable_to_model,
    is_navigation_only,
    lexical_relevance_scores,
    query_without_selected_scope,
)


LOGGER = logging.getLogger(__name__)
RRF_K = 60
LEXICAL_RRF_WEIGHT = 1.25
NEIGHBOR_RRF_WEIGHT = 0.85
MAX_NEIGHBOR_SEEDS = 20
LEXICAL_RESERVE_THRESHOLD = 0.25
MAX_LEXICAL_RESERVED = 2
HEADING_LEXICAL_WEIGHT = 0.35


@dataclass(frozen=True, slots=True)
class RetrievedEvidence:
    document: Document
    score: float


@dataclass(slots=True)
class _Candidate:
    document: Document
    fusion_score: float = 0.0
    dense_score: float = 0.0
    lexical_score: float = 0.0
    neighbor_score: float = 0.0

    @property
    def display_score(self) -> float:
        return max(self.dense_score, self.lexical_score, self.neighbor_score)


class EvidenceRetriever(Protocol):
    def search(self, request: RagAnswerRequest) -> list[RetrievedEvidence]:
        ...


class MilvusEvidenceRetriever:
    """Hybrid retrieval over only the Java-approved OfficeCare document versions."""

    OUTPUT_FIELDS = [
        "knowledge_base_id",
        "brand_id",
        "model_id",
        "document_id",
        "document_version_id",
        "page_number",
        "chunk_index",
        "document_title",
        "original_file_name",
        "heading",
        "text",
    ]

    def __init__(
        self,
        settings: Settings,
        embedding_provider: EmbeddingProvider,
        *,
        client_factory: Callable[[], Any] | None = None,
    ) -> None:
        self._settings = settings
        self._embedding_provider = embedding_provider
        self._client_factory = client_factory
        self._client: Any | None = None

    def search(self, request: RagAnswerRequest) -> list[RetrievedEvidence]:
        client = self._get_client()
        try:
            if not client.has_collection(
                collection_name=self._settings.milvus_collection,
                timeout=self._settings.milvus_timeout_seconds,
            ):
                return []

            client.load_collection(
                collection_name=self._settings.milvus_collection,
                timeout=self._settings.milvus_timeout_seconds,
            )
            retrieval_question = query_without_selected_scope(
                request.question,
                selected_brand=request.brand_name,
                selected_model=request.model_name,
            )
            query_vector = self._embedding_provider.embed_query(retrieval_question)
            version_filter = ", ".join(
                str(version_id) for version_id in request.document_version_ids
            )
            scalar_filter = (
                f"knowledge_base_id == {request.knowledge_base_id} "
                f"and brand_id == {request.brand_id} "
                f"and model_id == {request.model_id} "
                f"and document_version_id in [{version_filter}]"
            )
            result = client.search(
                collection_name=self._settings.milvus_collection,
                data=[query_vector],
                anns_field="embedding",
                filter=scalar_filter,
                limit=self._settings.rag_dense_candidate_limit,
                output_fields=self.OUTPUT_FIELDS,
                search_params={"metric_type": "COSINE", "params": {}},
                timeout=self._settings.milvus_timeout_seconds,
            )
            hits = result[0] if result else []
            dense_evidence = [
                self._to_evidence(hit)
                for hit in hits
                if float(hit.get("distance", -1)) >= request.score_threshold
            ]
            scanned_documents = self._scan_documents(client, scalar_filter)
            return self._hybrid_rank(
                request,
                retrieval_question=retrieval_question,
                dense_evidence=dense_evidence,
                scanned_documents=scanned_documents,
            )
        except ServiceError:
            raise
        except Exception as exc:
            raise ServiceError(
                status_code=503,
                code="RAG_RETRIEVAL_FAILED",
                message="Milvus could not retrieve OfficeCare knowledge evidence.",
            ) from exc

    def _scan_documents(
        self,
        client: Any,
        scalar_filter: str,
    ) -> list[Document]:
        """Read bounded text metadata for local lexical scoring.

        This is an MVP bridge to Milvus sparse/BM25 retrieval. A failed lexical
        scan must not take down the existing dense retrieval path.
        """
        try:
            records = client.query(
                collection_name=self._settings.milvus_collection,
                filter=scalar_filter,
                output_fields=self.OUTPUT_FIELDS,
                limit=self._settings.rag_lexical_scan_limit,
                timeout=self._settings.milvus_timeout_seconds,
            )
            return [
                self._entity_to_document(record)
                for record in records
                if isinstance(record, dict)
            ]
        except Exception as exc:
            LOGGER.warning(
                "Milvus lexical scan failed; falling back to dense retrieval: %s",
                type(exc).__name__,
            )
            return []

    def _hybrid_rank(
        self,
        request: RagAnswerRequest,
        *,
        retrieval_question: str,
        dense_evidence: list[RetrievedEvidence],
        scanned_documents: list[Document],
    ) -> list[RetrievedEvidence]:
        dense_evidence = [
            item
            for item in dense_evidence
            if self._is_applicable_document(item.document, request.model_name)
        ]
        scanned_documents = [
            document
            for document in scanned_documents
            if self._is_applicable_document(document, request.model_name)
        ]
        document_lookup: dict[tuple[int, int], Document] = {
            self._document_key(document): document
            for document in scanned_documents
        }
        for item in dense_evidence:
            document_lookup.setdefault(
                self._document_key(item.document),
                item.document,
            )

        candidates: dict[tuple[int, int], _Candidate] = {}

        for rank, item in enumerate(dense_evidence, start=1):
            if is_navigation_only(
                item.document.page_content,
                heading=str(item.document.metadata.get("heading") or ""),
            ):
                continue
            candidate = self._candidate(candidates, item.document)
            candidate.dense_score = max(candidate.dense_score, item.score)
            candidate.fusion_score += 1 / (RRF_K + rank)

        lexical_evidence = self._lexical_candidates(
            retrieval_question,
            scanned_documents,
        )
        for rank, item in enumerate(lexical_evidence, start=1):
            candidate = self._candidate(candidates, item.document)
            candidate.lexical_score = max(candidate.lexical_score, item.score)
            candidate.fusion_score += LEXICAL_RRF_WEIGHT / (RRF_K + rank)

        neighbor_seeds = self._neighbor_seeds(dense_evidence, lexical_evidence)
        for seed_rank, seed in enumerate(neighbor_seeds, start=1):
            seed_version_id, seed_chunk_index = self._document_key(seed.document)
            for offset in range(
                -self._settings.rag_neighbor_window,
                self._settings.rag_neighbor_window + 1,
            ):
                if offset == 0:
                    continue
                neighbor = document_lookup.get(
                    (seed_version_id, seed_chunk_index + offset)
                )
                if neighbor is None or is_navigation_only(
                    neighbor.page_content,
                    heading=str(neighbor.metadata.get("heading") or ""),
                ):
                    continue
                candidate = self._candidate(candidates, neighbor)
                candidate.neighbor_score = max(
                    candidate.neighbor_score,
                    seed.score * (0.98 ** abs(offset)),
                )
                candidate.fusion_score += NEIGHBOR_RRF_WEIGHT / (
                    RRF_K + seed_rank + abs(offset)
                )

        ranked = sorted(
            candidates.values(),
            key=lambda item: (item.fusion_score, item.display_score),
            reverse=True,
        )
        selected: list[_Candidate] = []
        selected_keys: set[tuple[int, int]] = set()
        if (
            lexical_evidence
            and lexical_evidence[0].score >= LEXICAL_RESERVE_THRESHOLD
        ):
            for item in lexical_evidence[:min(MAX_LEXICAL_RESERVED, request.top_k)]:
                key = self._document_key(item.document)
                candidate = candidates.get(key)
                if candidate is None:
                    continue
                selected.append(candidate)
                selected_keys.add(key)

        for candidate in ranked:
            key = self._document_key(candidate.document)
            if key in selected_keys:
                continue
            selected.append(candidate)
            selected_keys.add(key)
            if len(selected) >= request.top_k:
                break

        return [
            RetrievedEvidence(
                document=candidate.document,
                score=candidate.display_score,
            )
            for candidate in selected[:request.top_k]
        ]

    def _lexical_candidates(
        self,
        question: str,
        documents: list[Document],
    ) -> list[RetrievedEvidence]:
        usable_documents = [
            document
            for document in documents
            if not is_navigation_only(
                document.page_content,
                heading=str(document.metadata.get("heading") or ""),
            )
        ]
        searchable_texts = [
            "\n".join(
                (
                    str(document.metadata.get("document_title") or ""),
                    str(document.metadata.get("original_file_name") or ""),
                    str(document.metadata.get("heading") or ""),
                    document.page_content,
                )
            )
            for document in usable_documents
        ]
        base_scores = lexical_relevance_scores(question, searchable_texts)
        heading_scores = lexical_relevance_scores(
            question,
            [
                str(document.metadata.get("heading") or "")
                for document in usable_documents
            ],
        )
        scores = [
            min(1.0, base_score + HEADING_LEXICAL_WEIGHT * heading_score)
            for base_score, heading_score in zip(
                base_scores,
                heading_scores,
                strict=True,
            )
        ]
        evidence = [
            RetrievedEvidence(document=document, score=score)
            for document, score in zip(usable_documents, scores, strict=True)
            if score >= self._settings.rag_lexical_score_threshold
        ]
        evidence.sort(key=lambda item: item.score, reverse=True)
        return evidence[:self._settings.rag_lexical_candidate_limit]

    def _neighbor_seeds(
        self,
        dense_evidence: list[RetrievedEvidence],
        lexical_evidence: list[RetrievedEvidence],
    ) -> list[RetrievedEvidence]:
        seeds: list[RetrievedEvidence] = []
        seen: set[tuple[int, int]] = set()
        navigation_seeds = [
            item
            for item in dense_evidence
            if is_navigation_only(
                item.document.page_content,
                heading=str(item.document.metadata.get("heading") or ""),
            )
        ][:5]
        # Exact lexical hits often need the immediately following procedural step.
        # Expanding every dense hit caused unrelated local neighborhoods to crowd
        # out exact specifications, so only navigation pointers and top lexical
        # hits are allowed to seed neighbor retrieval.
        for item in [*navigation_seeds, *lexical_evidence[:10]]:
            key = self._document_key(item.document)
            if key in seen:
                continue
            seen.add(key)
            seeds.append(item)
            if len(seeds) >= MAX_NEIGHBOR_SEEDS:
                break
        return seeds

    @staticmethod
    def _candidate(
        candidates: dict[tuple[int, int], _Candidate],
        document: Document,
    ) -> _Candidate:
        key = MilvusEvidenceRetriever._document_key(document)
        candidate = candidates.get(key)
        if candidate is None:
            candidate = _Candidate(document=document)
            candidates[key] = candidate
        return candidate

    @staticmethod
    def _document_key(document: Document) -> tuple[int, int]:
        return (
            int(document.metadata["document_version_id"]),
            int(document.metadata["chunk_index"]),
        )

    @staticmethod
    def _is_applicable_document(
        document: Document,
        selected_model: str | None,
    ) -> bool:
        return is_applicable_to_model(
            document.page_content,
            heading=str(document.metadata.get("heading") or ""),
            selected_model=selected_model,
        )

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

    def _to_evidence(self, hit: dict[str, Any]) -> RetrievedEvidence:
        entity = hit.get("entity") or {}
        return RetrievedEvidence(
            document=self._entity_to_document(entity),
            score=float(hit.get("distance", 0)),
        )

    def _entity_to_document(self, entity: dict[str, Any]) -> Document:
        metadata = {
            field: entity.get(field)
            for field in self.OUTPUT_FIELDS
            if field != "text"
        }
        return Document(
            page_content=str(entity.get("text") or "").strip(),
            metadata=metadata,
        )
