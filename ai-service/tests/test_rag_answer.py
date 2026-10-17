from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from langchain_core.documents import Document

from app.main import app
from app.schemas.rag import RagAnswerRequest, RagAnswerResponse, RagCitation
from app.services.rag_answering import (
    REFUSAL_REASON,
    SYSTEM_PROMPT,
    RagAnsweringService,
    get_rag_answering_service,
)
from app.services.rag_answer_cache import RagAnswerCache, RagCacheLookup
from app.services.rag_retrieval import RetrievedEvidence


RAG_URL = "/api/v1/internal/rag/answer"
RAG_RETRIEVAL_URL = "/api/v1/internal/rag/retrieve"
RAG_EVALUATION_ANSWER_URL = "/api/v1/internal/rag/evaluate/answer"


def test_system_prompt_forbids_filling_in_missing_procedure_details() -> None:
    assert "禁止自行补充按键组合、菜单路径、操作时长、型号或数值" in SYSTEM_PROMPT


def test_answer_refuses_explicit_brand_conflict_without_retrieval() -> None:
    retriever = FakeRetriever(_evidence())
    generator = FakeGenerator("不应调用")
    service = RagAnsweringService(retriever=retriever, generator=generator)

    response = service.answer(
        RagAnswerRequest(
            question="HP打印机发生卡纸怎么办？",
            knowledge_base_id=1,
            brand_id=2,
            model_id=3,
            brand_name="Brother",
            model_name="DCP-L2550DW",
            document_version_ids=[40],
        )
    )

    assert response.answered is False
    assert response.refusal_reason == REFUSAL_REASON
    assert retriever.request is None
    assert generator.called is False


class FakeRetriever:
    def __init__(self, evidence: list[RetrievedEvidence]) -> None:
        self.evidence = evidence
        self.request: RagAnswerRequest | None = None

    def search(self, request: RagAnswerRequest) -> list[RetrievedEvidence]:
        self.request = request
        return self.evidence


class FakeGenerator:
    model_name = "deepseek-v4-pro"

    def __init__(self, answer: str) -> None:
        self.answer = answer
        self.called = False

    def generate(
        self,
        question: str,
        evidence: list[RetrievedEvidence],
    ) -> str:
        self.called = True
        assert question == "打印机卡纸后如何处理？"
        assert len(evidence) == 1
        return self.answer


class FakeAnswerCache(RagAnswerCache):
    def __init__(self, response: RagAnswerResponse | None = None) -> None:
        self.response = response
        self.lookup_requests: list[RagAnswerRequest] = []
        self.store_calls: list[tuple[RagCacheLookup, RagAnswerResponse]] = []
        self.bypass_reasons: list[str] = []

    def lookup(self, request: RagAnswerRequest) -> RagCacheLookup:
        self.lookup_requests.append(request)
        return RagCacheLookup(cache_key="test-cache-key", response=self.response)

    def store(
        self,
        lookup: RagCacheLookup,
        response: RagAnswerResponse,
    ) -> None:
        self.store_calls.append((lookup, response))

    def record_bypass(self, reason: str) -> None:
        self.bypass_reasons.append(reason)

    def stats(self, knowledge_base_id: int | None = None):
        raise NotImplementedError

    def invalidate(self, request):
        raise NotImplementedError


def _evidence() -> list[RetrievedEvidence]:
    return [
        RetrievedEvidence(
            document=Document(
                page_content="关闭打印机电源，再打开后盖并缓慢取出卡纸。",
                metadata={
                    "knowledge_base_id": 1,
                    "brand_id": 10,
                    "model_id": 20,
                    "document_id": 30,
                    "document_version_id": 40,
                    "document_title": "打印机维修手册",
                    "original_file_name": "manual.pdf",
                    "page_number": 8,
                    "chunk_index": 2,
                    "heading": "卡纸处理",
                },
            ),
            score=0.91,
        )
    ]


def _body() -> dict:
    return {
        "question": "打印机卡纸后如何处理？",
        "knowledgeBaseId": 1,
        "brandId": 10,
        "modelId": 20,
        "documentVersionIds": [40],
        "topK": 5,
        "scoreThreshold": 0.45,
    }


@pytest.fixture
def rag_client() -> Iterator[tuple[TestClient, FakeRetriever, FakeGenerator]]:
    retriever = FakeRetriever(_evidence())
    generator = FakeGenerator("请先关闭打印机电源，再缓慢取出卡纸。[资料1]")
    service = RagAnsweringService(retriever=retriever, generator=generator)
    app.dependency_overrides[get_rag_answering_service] = lambda: service
    try:
        with TestClient(app, raise_server_exceptions=False) as test_client:
            yield test_client, retriever, generator
    finally:
        app.dependency_overrides.pop(get_rag_answering_service, None)


def test_rag_endpoint_returns_grounded_answer_and_page_citation(
    rag_client: tuple[TestClient, FakeRetriever, FakeGenerator],
    auth_headers: dict[str, str],
) -> None:
    client, retriever, generator = rag_client

    response = client.post(RAG_URL, headers=auth_headers, json=_body())

    assert response.status_code == 200
    body = response.json()
    assert body["answered"] is True
    assert body["answer"].endswith("[资料1]")
    assert body["modelName"] == "deepseek-v4-pro"
    assert body["retrievedCount"] == 1
    assert body["citations"][0]["documentVersionId"] == 40
    assert body["citations"][0]["pageNumber"] == 8
    assert body["citations"][0]["score"] == 0.91
    assert retriever.request is not None
    assert retriever.request.document_version_ids == [40]
    assert generator.called is True


def test_rag_retrieval_endpoint_returns_ranked_evidence_without_generation(
    rag_client: tuple[TestClient, FakeRetriever, FakeGenerator],
    auth_headers: dict[str, str],
) -> None:
    client, retriever, generator = rag_client

    response = client.post(RAG_RETRIEVAL_URL, headers=auth_headers, json=_body())

    assert response.status_code == 200
    body = response.json()
    assert body["retrievedCount"] == 1
    assert body["blockedReason"] is None
    assert body["evidence"][0]["citationIndex"] == 1
    assert body["evidence"][0]["documentVersionId"] == 40
    assert body["evidence"][0]["pageNumber"] == 8
    assert retriever.request is not None
    assert generator.called is False


def test_rag_retrieval_blocks_explicit_scope_conflict() -> None:
    retriever = FakeRetriever(_evidence())
    generator = FakeGenerator("不应调用")
    service = RagAnsweringService(retriever=retriever, generator=generator)

    response = service.retrieve(
        RagAnswerRequest(
            question="HP打印机发生卡纸怎么办？",
            knowledge_base_id=1,
            brand_id=10,
            model_id=20,
            brand_name="Brother",
            model_name="DCP-L2550DW",
            document_version_ids=[40],
        )
    )

    assert response.retrieved_count == 0
    assert response.evidence == []
    assert response.blocked_reason == "SCOPE_CONFLICT"
    assert retriever.request is None
    assert generator.called is False


def test_rag_blocks_unsupported_dcp_fax_before_retrieval_and_generation() -> None:
    retriever = FakeRetriever(_evidence())
    generator = FakeGenerator("不应调用")
    service = RagAnsweringService(retriever=retriever, generator=generator)
    request = RagAnswerRequest(
        question="DCP-L2550DW如何发送传真？",
        knowledge_base_id=1,
        brand_id=10,
        model_id=20,
        brand_name="Brother",
        model_name="DCP-L2550DW",
        document_version_ids=[40],
    )

    retrieval = service.retrieve(request)
    answer = service.answer(request)

    assert retrieval.retrieved_count == 0
    assert retrieval.evidence == []
    assert retrieval.blocked_reason == "UNSUPPORTED_MODEL_CAPABILITY"
    assert answer.answered is False
    assert answer.refusal_reason == REFUSAL_REASON
    assert retriever.request is None
    assert generator.called is False


def test_rag_service_cache_hit_skips_embedding_retrieval_and_chat_generation() -> None:
    cached_response = RagAnswerResponse(
        answered=True,
        answer="缓存中的有效回答。[资料1]",
        citations=[
            RagCitation(
                citation_index=1,
                knowledge_base_id=1,
                brand_id=10,
                model_id=20,
                document_id=30,
                document_version_id=40,
                document_title="打印机维修手册",
                original_file_name="manual.pdf",
                page_number=8,
                chunk_index=2,
                heading="卡纸处理",
                quote="关闭打印机电源。",
                score=0.91,
            )
        ],
        retrieved_count=1,
        score_threshold=0.45,
        model_name="deepseek-v4-pro",
    )
    cache = FakeAnswerCache(cached_response)
    retriever = FakeRetriever(_evidence())
    generator = FakeGenerator("不应调用")
    service = RagAnsweringService(
        retriever=retriever,
        generator=generator,
        cache=cache,
    )

    response = service.answer(RagAnswerRequest(**_body()))

    assert response == cached_response
    assert len(cache.lookup_requests) == 1
    assert retriever.request is None
    assert generator.called is False


def test_evaluation_answer_bypasses_cache_without_changing_cache_state() -> None:
    cached_response = RagAnswerResponse(
        answered=False,
        answer="不应读取该缓存回答。",
        refusal_reason=REFUSAL_REASON,
        citations=[],
        retrieved_count=0,
        score_threshold=0.45,
    )
    cache = FakeAnswerCache(cached_response)
    retriever = FakeRetriever(_evidence())
    generator = FakeGenerator("请先关闭打印机电源。[资料1]")
    service = RagAnsweringService(
        retriever=retriever,
        generator=generator,
        cache=cache,
    )

    response = service.answer_without_cache(RagAnswerRequest(**_body()))

    assert response.answered is True
    assert response.answer == "请先关闭打印机电源。[资料1]"
    assert retriever.request is not None
    assert generator.called is True
    assert cache.lookup_requests == []
    assert cache.store_calls == []
    assert cache.bypass_reasons == []


def test_evaluation_answer_endpoint_uses_uncached_answer_path(
    rag_client: tuple[TestClient, FakeRetriever, FakeGenerator],
    auth_headers: dict[str, str],
) -> None:
    client, retriever, generator = rag_client

    response = client.post(
        RAG_EVALUATION_ANSWER_URL,
        headers=auth_headers,
        json=_body(),
    )

    assert response.status_code == 200
    assert response.json()["answered"] is True
    assert retriever.request is not None
    assert generator.called is True


def test_rag_service_stores_only_the_validated_grounded_response() -> None:
    cache = FakeAnswerCache()
    service = RagAnsweringService(
        retriever=FakeRetriever(_evidence()),
        generator=FakeGenerator("请先关闭打印机电源。[资料1]"),
        cache=cache,
    )

    response = service.answer(RagAnswerRequest(**_body()))

    assert response.answered is True
    assert cache.store_calls == [(
        RagCacheLookup(cache_key="test-cache-key"),
        response,
    )]


def test_rag_service_refuses_without_evidence() -> None:
    retriever = FakeRetriever([])
    generator = FakeGenerator("不应被调用")
    service = RagAnsweringService(retriever=retriever, generator=generator)

    response = service.answer(RagAnswerRequest(**{
        "question": "打印机卡纸后如何处理？",
        "knowledgeBaseId": 1,
        "brandId": 10,
        "modelId": 20,
        "documentVersionIds": [40],
    }))

    assert response.answered is False
    assert response.refusal_reason == REFUSAL_REASON
    assert response.citations == []
    assert generator.called is False


def test_rag_service_honors_model_refusal_marker() -> None:
    service = RagAnsweringService(
        retriever=FakeRetriever(_evidence()),
        generator=FakeGenerator("INSUFFICIENT_EVIDENCE"),
    )

    response = service.answer(RagAnswerRequest(**_body()))

    assert response.answered is False
    assert response.refusal_reason == REFUSAL_REASON
    assert response.retrieved_count == 1
    assert response.model_name == "deepseek-v4-pro"


@pytest.mark.parametrize(
    "answer",
    [
        "这段回答没有任何资料编号。",
        "这段回答引用了不存在的证据。[资料2]",
    ],
)
def test_rag_service_refuses_ungrounded_model_answer(answer: str) -> None:
    service = RagAnsweringService(
        retriever=FakeRetriever(_evidence()),
        generator=FakeGenerator(answer),
    )

    response = service.answer(RagAnswerRequest(**_body()))

    assert response.answered is False
    assert response.refusal_reason == REFUSAL_REASON
    assert response.citations == []


@pytest.mark.parametrize(
    "url",
    [RAG_URL, RAG_RETRIEVAL_URL, RAG_EVALUATION_ANSWER_URL],
)
def test_rag_endpoints_require_internal_token(client: TestClient, url: str) -> None:
    response = client.post(url, json=_body())

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_rag_endpoint_rejects_duplicate_version_ids(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    body = _body()
    body["documentVersionIds"] = [40, 40]

    response = client.post(RAG_URL, headers=auth_headers, json=body)

    assert response.status_code == 422
    assert response.json()["code"] == "REQUEST_VALIDATION_ERROR"
