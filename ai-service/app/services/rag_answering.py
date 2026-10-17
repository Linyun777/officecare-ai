from functools import lru_cache
import re
from typing import Protocol

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI

from app.core.config import Settings, get_settings
from app.core.exceptions import ServiceError
from app.core.observability import observe_operation
from app.schemas.rag import (
    RagAnswerRequest,
    RagAnswerResponse,
    RagCitation,
    RagRetrievalResponse,
)
from app.schemas.rag_cache import (
    RagCacheInvalidateRequest,
    RagCacheInvalidateResponse,
    RagCacheStatsResponse,
)
from app.services.embedding import OpenAICompatibleEmbeddingProvider
from app.services.rag_answer_cache import (
    NoOpRagAnswerCache,
    RagAnswerCache,
    RedisRagAnswerCache,
)
from app.services.rag_retrieval import (
    EvidenceRetriever,
    MilvusEvidenceRetriever,
    RetrievedEvidence,
)
from app.services.retrieval_quality import (
    has_scope_conflict,
    has_unsupported_model_capability,
)


REFUSAL_TEXT = "根据当前知识库中的资料，暂时无法找到足够证据回答这个问题。"
REFUSAL_REASON = "INSUFFICIENT_EVIDENCE"
CITATION_PATTERN = re.compile(r"\[资料(\d+)]")

SYSTEM_PROMPT = """你是OfficeCare打印机售后知识助手。
你只能依据下面提供的知识库资料回答，禁止补充资料之外的事实、步骤、参数或结论。
如果资料只说明“需要执行某项操作”但没有给出具体步骤，必须明确说明资料未提供
具体步骤；禁止自行补充按键组合、菜单路径、操作时长、型号或数值。
回答应简洁、可执行；涉及操作风险时提醒用户断电并遵循原厂安全要求。
每个关键结论后必须使用[资料1]、[资料2]这样的编号标注依据。
如果资料不足以可靠回答，只输出英文标记 INSUFFICIENT_EVIDENCE，不要猜测。
"""


class AnswerGenerator(Protocol):
    @property
    def model_name(self) -> str:
        ...

    def generate(
        self,
        question: str,
        evidence: list[RetrievedEvidence],
    ) -> str:
        ...


class LangChainAnswerGenerator:
    """LangChain prompt/model/parser chain; no direct LangGraph API is used."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._chain = None

    @property
    def model_name(self) -> str:
        return self._settings.chat_model

    def generate(
        self,
        question: str,
        evidence: list[RetrievedEvidence],
    ) -> str:
        chain = self._get_chain()
        context = self._build_context(evidence)
        try:
            result = chain.invoke({"question": question, "context": context})
        except Exception as exc:
            raise ServiceError(
                status_code=503,
                code="CHAT_MODEL_UNAVAILABLE",
                message="The configured chat model is unavailable or timed out.",
            ) from exc
        normalized = str(result).strip()
        if not normalized:
            raise ServiceError(
                status_code=502,
                code="CHAT_MODEL_EMPTY_RESPONSE",
                message="The configured chat model returned an empty response.",
            )
        return normalized

    def _get_chain(self):
        if self._chain is not None:
            return self._chain
        api_key = self._settings.chat_key()
        if (
            not self._settings.chat_base_url
            or not self._settings.chat_model
            or not api_key
        ):
            raise ServiceError(
                status_code=503,
                code="CHAT_MODEL_NOT_CONFIGURED",
                message="The DeepSeek chat API URL, key, or model is not configured.",
            )

        prompt = ChatPromptTemplate.from_messages(
            [
                ("system", SYSTEM_PROMPT),
                (
                    "human",
                    "用户问题：\n{question}\n\n可用知识库资料：\n{context}",
                ),
            ]
        )
        model = ChatOpenAI(
            api_key=api_key,
            base_url=self._settings.chat_base_url,
            model=self._settings.chat_model,
            temperature=self._settings.chat_temperature,
            timeout=self._settings.chat_timeout_seconds,
            max_completion_tokens=self._settings.chat_max_tokens,
            max_retries=1,
            extra_body={"thinking": {"type": "disabled"}},
        )
        self._chain = prompt | model | StrOutputParser()
        return self._chain

    def _build_context(self, evidence: list[RetrievedEvidence]) -> str:
        sections: list[str] = []
        used_chars = 0
        for index, item in enumerate(evidence, start=1):
            metadata = item.document.metadata
            page_number = int(metadata.get("page_number") or 0)
            page_label = f"第{page_number}页" if page_number > 0 else "页码未知"
            heading = str(metadata.get("heading") or "").strip()
            header = (
                f"[资料{index}] 文档：{metadata.get('document_title') or ''}；"
                f"文件：{metadata.get('original_file_name') or ''}；{page_label}；"
                f"标题：{heading or '无'}；综合相关度：{item.score:.4f}\n"
            )
            remaining = self._settings.rag_context_max_chars - used_chars - len(header)
            if remaining <= 0:
                break
            content = item.document.page_content.strip()[:remaining]
            section = header + content
            sections.append(section)
            used_chars += len(section) + 2
            if used_chars >= self._settings.rag_context_max_chars:
                break
        return "\n\n".join(sections)


class RagAnsweringService:
    def __init__(
        self,
        *,
        retriever: EvidenceRetriever,
        generator: AnswerGenerator,
        cache: RagAnswerCache | None = None,
    ) -> None:
        self._retriever = retriever
        self._generator = generator
        self._cache = cache or NoOpRagAnswerCache()

    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        return self._answer(request, cache=self._cache)

    def answer_without_cache(self, request: RagAnswerRequest) -> RagAnswerResponse:
        """Run a quality evaluation without reading or changing answer cache metrics."""
        return self._answer(request, cache=NoOpRagAnswerCache())

    def retrieve(self, request: RagAnswerRequest) -> RagRetrievalResponse:
        blocked_reason = self._blocked_reason(request)
        if blocked_reason is not None:
            return RagRetrievalResponse(
                evidence=[],
                retrieved_count=0,
                score_threshold=request.score_threshold,
                blocked_reason=blocked_reason,
            )

        evidence = self._retrieve_evidence(request)
        return RagRetrievalResponse(
            evidence=[
                self._citation(index, item)
                for index, item in enumerate(evidence, start=1)
            ],
            retrieved_count=len(evidence),
            score_threshold=request.score_threshold,
        )

    def _answer(
        self,
        request: RagAnswerRequest,
        *,
        cache: RagAnswerCache,
    ) -> RagAnswerResponse:
        blocked_reason = self._blocked_reason(request)
        if blocked_reason is not None:
            cache.record_bypass(blocked_reason)
            return self._refusal(request, retrieved_count=0)

        cache_lookup = cache.lookup(request)
        if cache_lookup.response is not None:
            return cache_lookup.response

        evidence = self._retrieve_evidence(request)
        if not evidence:
            cache.record_bypass("NO_EVIDENCE")
            return self._refusal(request, retrieved_count=0)

        generated_answer = self._generator.generate(request.question, evidence)
        if REFUSAL_REASON in generated_answer.upper():
            cache.record_bypass("MODEL_REFUSAL")
            return self._refusal(
                request,
                retrieved_count=len(evidence),
                model_name=self._generator.model_name,
            )

        citation_indexes = self._valid_citation_indexes(
            generated_answer,
            len(evidence),
        )
        if not citation_indexes:
            cache.record_bypass("INVALID_CITATIONS")
            return self._refusal(
                request,
                retrieved_count=len(evidence),
                model_name=self._generator.model_name,
            )

        citations = [
            self._citation(index, evidence[index - 1])
            for index in citation_indexes
        ]
        response = RagAnswerResponse(
            answered=True,
            answer=generated_answer,
            citations=citations,
            retrieved_count=len(evidence),
            score_threshold=request.score_threshold,
            model_name=self._generator.model_name,
        )
        cache.store(cache_lookup, response)
        return response

    def _retrieve_evidence(
        self,
        request: RagAnswerRequest,
    ) -> list[RetrievedEvidence]:
        with observe_operation(
            "officecare.rag.search_evidence",
            run_type="retriever",
            tags=("rag", "retrieval"),
            metadata={
                "knowledge_base_id": request.knowledge_base_id,
                "brand_id": request.brand_id,
                "model_id": request.model_id,
                "document_version_count": len(request.document_version_ids),
                "top_k": request.top_k,
                "score_threshold": request.score_threshold,
            },
        ) as trace:
            evidence = self._retriever.search(request)
            trace.set_result(
                retrieved_count=len(evidence),
                top_score=evidence[0].score if evidence else None,
            )
            return evidence

    def cache_stats(
        self,
        knowledge_base_id: int | None = None,
    ) -> RagCacheStatsResponse:
        return self._cache.stats(knowledge_base_id)

    def invalidate_cache(
        self,
        request: RagCacheInvalidateRequest,
    ) -> RagCacheInvalidateResponse:
        return self._cache.invalidate(request)

    def _valid_citation_indexes(
        self,
        answer: str,
        evidence_count: int,
    ) -> list[int]:
        indexes: list[int] = []
        for raw_index in CITATION_PATTERN.findall(answer):
            index = int(raw_index)
            if index < 1 or index > evidence_count:
                return []
            if index not in indexes:
                indexes.append(index)
        return indexes

    @staticmethod
    def _blocked_reason(request: RagAnswerRequest) -> str | None:
        if has_scope_conflict(
            request.question,
            selected_brand=request.brand_name,
            selected_model=request.model_name,
        ):
            return "SCOPE_CONFLICT"
        if has_unsupported_model_capability(
            request.question,
            selected_model=request.model_name,
        ):
            return "UNSUPPORTED_MODEL_CAPABILITY"
        return None

    def _refusal(
        self,
        request: RagAnswerRequest,
        *,
        retrieved_count: int,
        model_name: str | None = None,
    ) -> RagAnswerResponse:
        return RagAnswerResponse(
            answered=False,
            answer=REFUSAL_TEXT,
            refusal_reason=REFUSAL_REASON,
            citations=[],
            retrieved_count=retrieved_count,
            score_threshold=request.score_threshold,
            model_name=model_name,
        )

    def _citation(
        self,
        citation_index: int,
        evidence: RetrievedEvidence,
    ) -> RagCitation:
        metadata = evidence.document.metadata
        page_number = int(metadata.get("page_number") or 0)
        return RagCitation(
            citation_index=citation_index,
            knowledge_base_id=int(metadata["knowledge_base_id"]),
            brand_id=int(metadata["brand_id"]),
            model_id=int(metadata["model_id"]),
            document_id=int(metadata["document_id"]),
            document_version_id=int(metadata["document_version_id"]),
            document_title=str(metadata.get("document_title") or ""),
            original_file_name=str(metadata.get("original_file_name") or ""),
            page_number=page_number if page_number > 0 else None,
            chunk_index=int(metadata["chunk_index"]),
            heading=str(metadata.get("heading") or ""),
            quote=evidence.document.page_content.strip()[:1200],
            score=round(evidence.score, 6),
        )


@lru_cache
def get_rag_answering_service() -> RagAnsweringService:
    settings = get_settings()
    embedding_provider = OpenAICompatibleEmbeddingProvider(settings)
    cache: RagAnswerCache = NoOpRagAnswerCache(settings)
    if settings.redis_enabled:
        cache = RedisRagAnswerCache(settings, prompt=SYSTEM_PROMPT)
    return RagAnsweringService(
        retriever=MilvusEvidenceRetriever(settings, embedding_provider),
        generator=LangChainAnswerGenerator(settings),
        cache=cache,
    )
