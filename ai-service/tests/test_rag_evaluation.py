from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.evaluation.models import (
    EvaluationCase,
    EvaluationMode,
    EvaluationScope,
    EvaluationProvenance,
    EvaluationObservation,
    RelevantSourceRule,
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
    assert observation.hit_at_k == 1.0
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


def _verified_case(**updates) -> EvaluationCase:
    payload = _positive_case().model_dump()
    citation = _citation(index=1)
    payload.update(
        dataset_version="v2",
        task_group="print-quality",
        annotation_status="verified",
        annotation_note="Synthetic test fixture with exact evidence identity.",
        corpus_fingerprint="a" * 64,
        relevant_sources=[{
            "document_version_ids": [citation.document_version_id],
            "chunk_indexes": [citation.chunk_index],
        }],
        required_answer_patterns=[["清洁.*硒鼓"]],
    )
    payload.update(updates)
    return EvaluationCase.model_validate(payload)


def _frozen_manifest(tmp_path, cases):
    import hashlib

    path = tmp_path / "v2.jsonl"
    path.write_text(
        "\n".join(case.model_dump_json(by_alias=True) for case in cases) + "\n",
        encoding="utf-8",
    )
    manifest = EvaluationProvenance(
        dataset_version="v2",
        dataset_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
        corpus_fingerprint="a" * 64,
        algorithm_version="test-algorithm",
        chat_model="test-chat",
        embedding_model="test-embedding",
        split=cases[0].split,
    )
    return path, manifest


def test_v2_source_anchors_allow_zero_chunk_but_reject_negative_index():
    assert RelevantSourceRule(document_version_ids=[40], chunk_indexes=[0]).chunk_indexes == [0]
    with pytest.raises(ValueError):
        RelevantSourceRule(chunk_indexes=[-1])


def test_v2_verified_positive_rejects_keyword_only_reference():
    with pytest.raises(ValueError, match="exact version"):
        _verified_case(relevant_sources=[{"contains_any": ["硒鼓"]}])


def test_v2_menu_or_wrong_chunk_cannot_replace_correct_evidence():
    response = RagRetrievalResponse(
        evidence=[_citation(index=2, quote="启用或禁用双面打印菜单。")],
        retrieved_count=1,
        score_threshold=0.45,
    )
    observation = evaluate_retrieval(_verified_case(), response, 1)
    assert not observation.passed
    assert observation.hit_at_k == 0.0


def test_v2_other_document_version_cannot_match_correct_chunk():
    citation = _citation(index=1).model_copy(update={"document_version_id": 41})
    response = RagRetrievalResponse(
        evidence=[citation], retrieved_count=1, score_threshold=0.45,
    )
    assert not evaluate_retrieval(_verified_case(), response, 1).passed


def test_v2_negated_keywords_and_actions_cannot_pass():
    response = RagAnswerResponse(
        answered=True,
        answer="这里没有94毫米的说明，也没有清洁或更换硒鼓的步骤。",
        citations=[_citation(index=1)],
        retrieved_count=1,
        score_threshold=0.45,
    )
    observation = evaluate_answer(_verified_case(), response, 1)
    assert not observation.passed
    assert not observation.checks["requiredAnswerTerms"]


def test_v2_known_wrong_source_is_not_cancelled_by_one_correct_citation():
    case = _verified_case(
        forbidden_sources=[{"document_version_ids": [40], "chunk_indexes": [2]}],
    )
    response = RagAnswerResponse(
        answered=True,
        answer="每隔94毫米出现重复斑点时，清洁硒鼓。",
        citations=[_citation(index=1), _citation(index=2)],
        retrieved_count=2,
        score_threshold=0.45,
    )
    observation = evaluate_answer(case, response, 1)
    assert not observation.passed
    assert not observation.checks["noForbiddenSources"]
    assert not observation.review_required


def test_v2_unannotated_source_requires_review_instead_of_being_called_wrong():
    response = RagAnswerResponse(
        answered=True,
        answer="每隔94毫米出现重复斑点时，清洁硒鼓。",
        citations=[_citation(index=1), _citation(index=2)],
        retrieved_count=2,
        score_threshold=0.45,
    )
    observation = evaluate_answer(_verified_case(), response, 1)
    assert observation.review_required
    assert not observation.passed
    assert observation.checks["noForbiddenSources"]


def test_v2_changed_dataset_stops_before_external_requests(tmp_path):
    path, manifest = _frozen_manifest(tmp_path, [_verified_case()])
    path.write_text(path.read_text(encoding="utf-8") + "\n", encoding="utf-8")
    with pytest.raises(ValueError, match="dataset fingerprint"):
        EvaluationRunner(client=object(), scope=_scope()).run(
            [_verified_case()], modes=[EvaluationMode.ANSWER],
            dataset_path=str(path), provenance=manifest,
        )


def test_v2_changed_corpus_stops_before_external_requests(tmp_path):
    path, manifest = _frozen_manifest(tmp_path, [_verified_case()])
    manifest = manifest.model_copy(update={"corpus_fingerprint": "b" * 64})
    with pytest.raises(ValueError, match="provenance"):
        EvaluationRunner(client=object(), scope=_scope()).run(
            [_verified_case()], modes=[EvaluationMode.ANSWER],
            dataset_path=str(path), provenance=manifest,
        )


def test_v2_missing_manifest_stops_before_external_requests(tmp_path):
    path, _ = _frozen_manifest(tmp_path, [_verified_case()])
    with pytest.raises(ValueError, match="frozen run manifest"):
        EvaluationRunner(client=object(), scope=_scope()).run(
            [_verified_case()], modes=[EvaluationMode.ANSWER], dataset_path=str(path),
        )


def test_v2_out_of_scope_annotation_stops_before_external_requests(tmp_path):
    case = _verified_case(
        relevant_sources=[{"document_version_ids": [41], "chunk_indexes": [1]}],
    )
    path, manifest = _frozen_manifest(tmp_path, [case])
    with pytest.raises(ValueError, match="Java-approved"):
        EvaluationRunner(client=object(), scope=_scope()).run(
            [case], modes=[EvaluationMode.ANSWER],
            dataset_path=str(path), provenance=manifest,
        )


def test_v2_unverified_case_is_not_sent_to_model_or_quality_denominator(tmp_path):
    case = _verified_case(annotation_status="needs_review")
    path, manifest = _frozen_manifest(tmp_path, [case])
    report = EvaluationRunner(client=object(), scope=_scope()).run(
        [case], modes=[EvaluationMode.ANSWER],
        dataset_path=str(path), provenance=manifest,
    )
    summary = report.summaries[0]
    assert summary.unreviewed_cases == 1
    assert summary.positive_cases == 0
    assert summary.pending_review_cases == 1
    assert report.observations[0].review_required


def test_v2_execution_error_remains_in_hit_and_rule_denominators(tmp_path):
    from app.evaluation.runner import EvaluationClientError

    class FailingSecondRequest:
        def __init__(self):
            self.calls = 0

        def retrieve(self, request):
            self.calls += 1
            if self.calls == 2:
                raise EvaluationClientError(code="TEST_NETWORK_FAILURE")
            return RagRetrievalResponse(
                evidence=[_citation(index=1)],
                retrieved_count=1, score_threshold=0.45,
            )

    cases = [
        _verified_case(),
        _verified_case(case_id="quality.another-test"),
    ]
    path, manifest = _frozen_manifest(tmp_path, cases)
    report = EvaluationRunner(client=FailingSecondRequest(), scope=_scope()).run(
        cases, modes=[EvaluationMode.RETRIEVAL],
        dataset_path=str(path), provenance=manifest,
    )
    summary = report.summaries[0]
    assert summary.errors == 1
    assert summary.hit_at_k == 0.5
    assert summary.pass_rate == 0.5
    assert summary.execution_success_rate == 0.5


def test_old_report_recall_field_is_read_but_new_report_serializes_hit():
    original = evaluate_retrieval(
        _positive_case(),
        RagRetrievalResponse(
            evidence=[_citation(index=1)], retrieved_count=1, score_threshold=0.45,
        ),
        1,
    ).model_dump()
    original["recallAtK"] = original.pop("hit_at_k")
    observation = EvaluationObservation.model_validate(original)
    assert observation.hit_at_k == 1
    assert "hitAtK" in observation.model_dump(by_alias=True)
    assert "recallAtK" not in observation.model_dump(by_alias=True)


def test_holdout_rejects_same_task_group_or_reworded_existing_question():
    from app.evaluation.runner import validate_dataset_splits

    development = _verified_case()
    overlapping = _verified_case(
        split="holdout", case_id="holdout.reworded", question="另一种提问方式？",
    )
    with pytest.raises(ValueError, match="overlap"):
        validate_dataset_splits([development], [overlapping])
    duplicate = _verified_case(split="holdout", task_group="another-task")
    with pytest.raises(ValueError, match="overlap"):
        validate_dataset_splits([development], [duplicate])


def test_evaluation_cli_numeric_parsers_still_validate_normally():
    import runpy

    module = runpy.run_path(str(Path(__file__).resolve().parents[1] / "scripts/evaluate_rag.py"))
    assert module["_positive_int"]("3") == 3
    assert module["_ratio"]("0.5") == 0.5
    assert module["_positive_int_list"]("40,41") == [40, 41]


def test_v2_datasets_preserve_legacy_case_ids_and_traceable_annotations():
    root = Path(__file__).resolve().parents[1] / "evaluation/datasets"
    for legacy_path in root.glob("*_v1.jsonl"):
        modern_path = legacy_path.with_name(legacy_path.name.replace("_v1.", "_v2."))
        legacy = load_evaluation_cases(legacy_path)
        modern = load_evaluation_cases(modern_path)
        assert {case.case_id for case in modern} == {case.case_id for case in legacy}
        assert all(case.dataset_version == "v2" for case in modern)
        assert all(case.annotation_note and case.task_group for case in modern)
        assert len({case.corpus_fingerprint for case in modern}) == 1
        assert all(
            rule.document_version_ids and rule.chunk_indexes
            for case in modern
            if case.expected_answered and case.annotation_status == "verified"
            for rule in case.relevant_sources
        )


def test_v2_retains_unresolved_standard_and_records_wrong_model_correction():
    root = Path(__file__).resolve().parents[1] / "evaluation/datasets"
    cases = {
        case.case_id: case
        for case in load_evaluation_cases(root / "officecare_printer_v2.jsonl")
    }
    assert cases["drum.reset-counter"].annotation_status == "needs_review"
    assert cases["error.access-denied"].expected_answered is False
    assert "1095" in cases["error.access-denied"].annotation_note
    assert cases["error.duplex-disabled"].question == "设备提示“禁用双面”时需要检查什么？"
    assert cases["error.duplex-disabled"].relevant_sources[0].chunk_indexes == [1091]


def test_holdout_v2_is_disjoint_and_has_twelve_verified_cases():
    from app.evaluation.runner import validate_dataset_splits

    folder = Path(__file__).resolve().parents[1] / "evaluation/datasets"
    development = [
        case
        for path in folder.glob("*_v2.jsonl")
        if "holdout" not in path.name
        for case in load_evaluation_cases(path)
    ]
    holdout = [
        case
        for path in folder.glob("*_holdout_v2.jsonl")
        for case in load_evaluation_cases(path)
    ]
    validate_dataset_splits(development, holdout)
    assert len(holdout) == 12
    assert sum(case.expected_answered for case in holdout) == 6
    assert all(case.annotation_status == "verified" for case in holdout)
    assert {case.corpus_fingerprint for case in holdout} == {
        case.corpus_fingerprint for case in development
    }

def test_report_writes_preserve_existing_reports_with_same_timestamp(tmp_path):
    report = EvaluationRunner(client=FakeEvaluationClient(), scope=_scope()).run(
        [_positive_case()],
        modes=[EvaluationMode.RETRIEVAL],
        dataset_path="first-dataset.jsonl",
    )
    first_paths = write_evaluation_report(report, output_dir=tmp_path)
    original_bytes = [path.read_bytes() for path in first_paths]
    second = report.model_copy(update={"dataset_path": "second-dataset.jsonl"})

    second_paths = write_evaluation_report(second, output_dir=tmp_path)

    assert first_paths != second_paths
    assert [path.read_bytes() for path in first_paths] == original_bytes
    assert "second-dataset.jsonl" in second_paths[0].read_text(encoding="utf-8")
    assert len(list(tmp_path.glob("*.json"))) == 2
    assert len(list(tmp_path.glob("*.md"))) == 2


def test_report_shows_rule_failure_and_citation_review_independently(tmp_path):
    report = EvaluationRunner(client=FakeEvaluationClient(), scope=_scope()).run(
        [_positive_case()],
        modes=[EvaluationMode.ANSWER],
        dataset_path="evaluation/test.jsonl",
    )
    report.observations[0] = report.observations[0].model_copy(update={
        "passed": False,
        "review_required": True,
        "review_reasons": ["unannotated_citation_requires_review"],
        "checks": {"requiredAnswerPatterns": False, "citationReviewComplete": False},
    })
    _, markdown_path = write_evaluation_report(report, output_dir=tmp_path)
    markdown = markdown_path.read_text(encoding="utf-8")
    row = next(line for line in markdown.splitlines() if line.startswith("| quality.repeat-94mm"))

    assert "规则结果 | 待复核" in markdown
    assert "| 规则未通过 | 是 |" in row
    assert "requiredAnswerPatterns" in markdown
    assert report.observations[0].checks["requiredAnswerPatterns"] is False


def test_evaluation_cli_reports_the_scored_denominator(tmp_path, monkeypatch, capsys):
    import runpy
    from types import SimpleNamespace

    module = runpy.run_path(str(Path(__file__).resolve().parents[1] / "scripts/evaluate_rag.py"))
    cli_globals = module["main"].__globals__
    cases = [
        _verified_case(),
        _verified_case(case_id="pending-standard", annotation_status="needs_review"),
    ]
    dataset_path, manifest = _frozen_manifest(tmp_path, cases)
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(manifest.model_dump_json(by_alias=True), encoding="utf-8")

    class LocalClient(FakeEvaluationClient):
        def __init__(self, **kwargs):
            pass

        def close(self):
            pass

    monkeypatch.setitem(cli_globals, "HttpRagEvaluationClient", LocalClient)
    monkeypatch.setitem(
        cli_globals, "get_settings",
        lambda: SimpleNamespace(internal_token=lambda: "local-test-token"),
    )
    exit_code = module["main"]([
        "--dataset", str(dataset_path), "--run-manifest", str(manifest_path),
        "--knowledge-base-id", "1", "--brand-id", "10", "--model-id", "20",
        "--brand-name", "Brother", "--model-name", "DCP-L2550DW",
        "--document-version-ids", "40", "--mode", "retrieval",
        "--output-dir", str(tmp_path / "reports"), "--fail-under", "1",
    ])
    output = capsys.readouterr().out

    assert exit_code == 0
    assert "retrieval: 1/1" in output
    assert "总题数=2，未评分=1" in output
    assert "passRate=100.0%" in output


def test_evaluation_cli_rejects_nan_quality_gate_before_any_requests(tmp_path, monkeypatch):
    import runpy
    from types import SimpleNamespace

    module = runpy.run_path(str(Path(__file__).resolve().parents[1] / "scripts/evaluate_rag.py"))
    cli_globals = module["main"].__globals__
    monkeypatch.setitem(
        cli_globals, "get_settings",
        lambda: SimpleNamespace(internal_token=lambda: "local-test-token"),
    )

    def unexpected_client(**kwargs):
        raise AssertionError("invalid threshold must be rejected before creating a client")

    monkeypatch.setitem(cli_globals, "HttpRagEvaluationClient", unexpected_client)
    with pytest.raises(SystemExit) as error:
        module["main"]([
            "--dataset", str(DATASET_PATH),
            "--knowledge-base-id", "1", "--brand-id", "10", "--model-id", "20",
            "--brand-name", "Brother", "--model-name", "DCP-L2550DW",
            "--document-version-ids", "40", "--fail-under", "nan",
            "--output-dir", str(tmp_path / "reports"),
        ])
    assert error.value.code == 2
