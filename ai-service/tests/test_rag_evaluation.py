from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.evaluation.models import (
    EvaluationCase,
    EvaluationMode,
    EvaluationScope,
)
from app.evaluation.reporting import write_evaluation_report
from app.evaluation.runner import (
    EvaluationRunner,
    HttpRagEvaluationClient,
    evaluate_answer,
    evaluate_retrieval,
    load_evaluation_cases,
    summarize_mode,
)
from app.schemas.rag import (
    RagAnswerRequest,
    RagAnswerResponse,
    RagCitation,
    RagRetrievalResponse,
)


DATASET_PATH = (
    Path(__file__).resolve().parents[1]
    / "evaluation"
    / "datasets"
    / "officecare_printer_v1.jsonl"
)
ADDITIONAL_DATASETS = [
    (
        DATASET_PATH.parent / "officecare_dcp_l2535dw_v1.jsonl",
        "dcp-l2535dw",
        12,
    ),
    (
        DATASET_PATH.parent / "officecare_mfc_7895dw_v1.jsonl",
        "mfc-7895dw",
        12,
    ),
]


def test_officecare_printer_dataset_is_valid_and_has_positive_and_negative_cases() -> None:
    cases = load_evaluation_cases(DATASET_PATH)

    assert len(cases) == 28
    assert any(case.expected_answered for case in cases)
    assert any(not case.expected_answered for case in cases)
    assert len({case.case_id for case in cases}) == len(cases)


@pytest.mark.parametrize("dataset_path,model_tag,expected_count", ADDITIONAL_DATASETS)
def test_additional_model_dataset_is_valid_and_model_scoped(
    dataset_path: Path,
    model_tag: str,
    expected_count: int,
) -> None:
    cases = load_evaluation_cases(dataset_path)

    assert len(cases) == expected_count
    assert all(model_tag in case.tags for case in cases)
    assert any(case.expected_answered for case in cases)
    assert any(not case.expected_answered for case in cases)
    assert len({case.case_id for case in cases}) == len(cases)


def test_dataset_loader_rejects_duplicate_case_ids(tmp_path: Path) -> None:
    payload = _positive_case().model_dump(mode="json", by_alias=True)
    dataset = tmp_path / "duplicate.jsonl"
    dataset.write_text(
        json.dumps(payload, ensure_ascii=False)
        + "\n"
        + json.dumps(payload, ensure_ascii=False)
        + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="duplicate caseId"):
        load_evaluation_cases(dataset)


def test_retrieval_evaluation_calculates_rank_recall_and_noise() -> None:
    response = RagRetrievalResponse(
        evidence=[
            _citation(
                index=1,
                title="网络安全说明书",
                quote="修改管理员密码。",
                score=0.88,
            ),
            _citation(
                index=2,
                title="高级说明书",
                quote="页面每隔94毫米出现斑点时，请清洁硒鼓单元。",
                score=0.83,
            ),
        ],
        retrieved_count=2,
        score_threshold=0.45,
    )

    observation = evaluate_retrieval(_positive_case(), response, 12.3456)

    assert observation.passed is True
    assert observation.first_relevant_rank == 2
    assert observation.reciprocal_rank == 0.5
    assert observation.recall_at_k == 1.0
    assert observation.noise_rate == 0.5
    assert [source.relevant for source in observation.sources] == [False, True]


def test_negative_retrieval_case_passes_only_when_no_evidence_is_returned() -> None:
    case = _negative_case()

    empty = evaluate_retrieval(
        case,
        RagRetrievalResponse(
            evidence=[],
            retrieved_count=0,
            score_threshold=0.45,
            blocked_reason="SCOPE_CONFLICT",
        ),
        3.0,
    )
    polluted = evaluate_retrieval(
        case,
        RagRetrievalResponse(
            evidence=[_citation(index=1)],
            retrieved_count=1,
            score_threshold=0.45,
        ),
        3.0,
    )

    assert empty.passed is True
    assert empty.blocked_reason == "SCOPE_CONFLICT"
    assert polluted.passed is False


def test_answer_evaluation_checks_terms_refusal_and_relevant_citations() -> None:
    positive = evaluate_answer(
        _positive_case(),
        RagAnswerResponse(
            answered=True,
            answer="请清洁硒鼓，94毫米重复斑点通常与此相关。[资料1]",
            citations=[_citation(index=1)],
            retrieved_count=1,
            score_threshold=0.45,
            model_name="test-chat-model",
        ),
        50.0,
    )
    refusal = evaluate_answer(
        _negative_case(),
        RagAnswerResponse(
            answered=False,
            answer="根据当前知识库中的资料，暂时无法找到足够证据回答这个问题。",
            refusal_reason="INSUFFICIENT_EVIDENCE",
            citations=[],
            retrieved_count=0,
            score_threshold=0.45,
            model_name="test-chat-model",
        ),
        20.0,
    )

    assert positive.passed is True
    assert positive.citation_hit is True
    assert refusal.passed is True
    summary = summarize_mode(EvaluationMode.ANSWER, [positive, refusal])
    assert summary.pass_rate == 1.0
    assert summary.answer_accuracy == 1.0
    assert summary.refusal_accuracy == 1.0
    assert summary.citation_hit_rate == 1.0

    refusal_with_citation = evaluate_answer(
        _negative_case(),
        RagAnswerResponse(
            answered=False,
            answer="证据不足。",
            refusal_reason="INSUFFICIENT_EVIDENCE",
            citations=[_citation(index=1)],
            retrieved_count=1,
            score_threshold=0.45,
        ),
        20.0,
    )
    contaminated_summary = summarize_mode(
        EvaluationMode.ANSWER,
        [refusal_with_citation],
    )
    assert refusal_with_citation.passed is False
    assert contaminated_summary.answer_accuracy == 1.0
    assert contaminated_summary.refusal_accuracy == 0.0


def test_runner_and_report_writer_do_not_persist_source_quotes(tmp_path: Path) -> None:
    report = EvaluationRunner(client=FakeEvaluationClient(), scope=_scope()).run(
        [_positive_case()],
        modes=[EvaluationMode.RETRIEVAL, EvaluationMode.ANSWER],
        dataset_path="evaluation/test.jsonl",
    )

    json_path, markdown_path = write_evaluation_report(report, output_dir=tmp_path)

    assert len(report.observations) == 2
    assert all(item.passed for item in report.observations)
    json_report = json_path.read_text(encoding="utf-8")
    markdown_report = markdown_path.read_text(encoding="utf-8")
    assert "敏感原文不应写入报告" not in json_report
    assert "OfficeCare RAG 评测报告" in markdown_report
    assert "retrieval" in markdown_report
    assert "answer" in markdown_report


def test_http_evaluation_client_uses_dedicated_endpoints_and_internal_header() -> None:
    session = FakeSession()
    client = HttpRagEvaluationClient(
        base_url="http://localhost:8000/",
        internal_token="local-test-token",
        timeout_seconds=9,
        session=session,
    )
    request = _scope().request_for(_positive_case())

    retrieval = client.retrieve(request)
    answer = client.answer(request)
    client.close()

    assert retrieval.retrieved_count == 1
    assert answer.answered is True
    assert [call[0] for call in session.calls] == [
        "http://localhost:8000/api/v1/internal/rag/retrieve",
        "http://localhost:8000/api/v1/internal/rag/evaluate/answer",
    ]
    assert all(
        call[1]["X-OfficeCare-Internal-Token"] == "local-test-token"
        for call in session.calls
    )
    assert all(call[3] == 9 for call in session.calls)
    assert session.closed is True


class FakeEvaluationClient:
    def retrieve(self, request: RagAnswerRequest) -> RagRetrievalResponse:
        return RagRetrievalResponse(
            evidence=[
                _citation(
                    index=1,
                    quote="敏感原文不应写入报告，94毫米重复斑点需要清洁硒鼓。",
                )
            ],
            retrieved_count=1,
            score_threshold=request.score_threshold,
        )

    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        return RagAnswerResponse(
            answered=True,
            answer="请清洁硒鼓并检查94毫米重复斑点。[资料1]",
            citations=[
                _citation(
                    index=1,
                    quote="敏感原文不应写入报告，94毫米重复斑点需要清洁硒鼓。",
                )
            ],
            retrieved_count=1,
            score_threshold=request.score_threshold,
            model_name="test-chat-model",
        )


class FakeResponse:
    status_code = 200

    def __init__(self, body: dict) -> None:
        self._body = body

    def json(self) -> dict:
        return self._body


class FakeSession:
    def __init__(self) -> None:
        self.calls: list[tuple[str, dict, dict, float]] = []
        self.closed = False

    def post(self, url: str, *, headers: dict, json: dict, timeout: float):
        self.calls.append((url, headers, json, timeout))
        citation = _citation(index=1).model_dump(mode="json", by_alias=True)
        if url.endswith("/retrieve"):
            return FakeResponse(
                {
                    "evidence": [citation],
                    "retrievedCount": 1,
                    "scoreThreshold": 0.45,
                    "blockedReason": None,
                }
            )
        return FakeResponse(
            {
                "answered": True,
                "answer": "请清洁硒鼓。[资料1]",
                "refusalReason": None,
                "citations": [citation],
                "retrievedCount": 1,
                "scoreThreshold": 0.45,
                "modelName": "test-chat-model",
            }
        )

    def close(self) -> None:
        self.closed = True


def _positive_case() -> EvaluationCase:
    return EvaluationCase(
        case_id="quality.repeat-94mm",
        question="打印页面每隔94毫米出现重复斑点怎么办？",
        tags=["positive", "print-quality"],
        expected_retrieval_hit=True,
        expected_answered=True,
        relevant_sources=[
            {
                "documentTitles": ["高级说明书"],
                "containsAny": ["94毫米", "硒鼓"],
            }
        ],
        required_answer_term_groups=[["94毫米"], ["清洁", "更换"]],
        min_citations=1,
    )


def _negative_case() -> EvaluationCase:
    return EvaluationCase(
        case_id="negative.hp-jam",
        question="HP打印机发生卡纸怎么办？",
        tags=["negative", "scope"],
        expected_retrieval_hit=False,
        expected_answered=False,
        min_citations=0,
    )


def _scope() -> EvaluationScope:
    return EvaluationScope(
        knowledge_base_id=1,
        brand_id=10,
        model_id=20,
        brand_name="Brother",
        model_name="DCP-L2550DW",
        document_version_ids=[40],
        top_k=5,
        score_threshold=0.45,
    )


def _citation(
    *,
    index: int,
    title: str = "高级说明书",
    quote: str = "页面每隔94毫米出现重复斑点时，请清洁硒鼓单元。",
    score: float = 0.91,
) -> RagCitation:
    return RagCitation(
        citation_index=index,
        knowledge_base_id=1,
        brand_id=10,
        model_id=20,
        document_id=30,
        document_version_id=40,
        document_title=title,
        original_file_name="manual.pdf",
        page_number=403,
        chunk_index=index,
        heading="改善打印质量",
        quote=quote,
        score=score,
    )
