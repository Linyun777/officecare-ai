from __future__ import annotations

from collections.abc import Iterable, Sequence
from datetime import datetime, timezone
import json
import math
import re
import hashlib
from pathlib import Path
from time import perf_counter
from typing import Protocol
import unicodedata
from uuid import uuid4

import requests

from app.evaluation.models import (
    EvaluationCase,
    EvaluationMode,
    EvaluationModeSummary,
    EvaluationObservation,
    EvaluationReport,
    EvaluationProvenance,
    EvaluationScope,
    EvaluationSource,
    RelevantSourceRule,
)
from app.schemas.rag import (
    RagAnswerRequest,
    RagAnswerResponse,
    RagCitation,
    RagRetrievalResponse,
)


class RagEvaluationClient(Protocol):
    def retrieve(self, request: RagAnswerRequest) -> RagRetrievalResponse:
        ...

    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        ...


class EvaluationClientError(Exception):
    def __init__(self, *, code: str, http_status: int | None = None) -> None:
        super().__init__(code)
        self.code = code
        self.http_status = http_status


class HttpRagEvaluationClient:
    """Internal-token client that never logs or stores the supplied token."""

    def __init__(
        self,
        *,
        base_url: str,
        internal_token: str,
        timeout_seconds: float = 180.0,
        session: requests.Session | None = None,
    ) -> None:
        normalized_url = base_url.strip().rstrip("/")
        normalized_token = internal_token.strip()
        if not normalized_url:
            raise ValueError("base_url cannot be blank")
        if not normalized_token:
            raise ValueError("internal_token cannot be blank")
        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")
        self._base_url = normalized_url
        self._timeout_seconds = timeout_seconds
        self._session = session or requests.Session()
        self._headers = {
            "X-OfficeCare-Internal-Token": normalized_token,
            "Content-Type": "application/json",
        }

    def retrieve(self, request: RagAnswerRequest) -> RagRetrievalResponse:
        body = self._post("/api/v1/internal/rag/retrieve", request)
        return RagRetrievalResponse.model_validate(body)

    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        body = self._post("/api/v1/internal/rag/evaluate/answer", request)
        return RagAnswerResponse.model_validate(body)

    def close(self) -> None:
        self._session.close()

    def _post(self, path: str, request: RagAnswerRequest) -> dict:
        headers = {
            **self._headers,
            "X-Trace-Id": f"rag-eval-{uuid4().hex}",
        }
        try:
            response = self._session.post(
                self._base_url + path,
                headers=headers,
                json=request.model_dump(by_alias=True),
                timeout=self._timeout_seconds,
            )
        except requests.RequestException as exc:
            raise EvaluationClientError(code="EVALUATION_REQUEST_FAILED") from exc

        try:
            body = response.json()
        except ValueError as exc:
            raise EvaluationClientError(
                code="EVALUATION_INVALID_JSON",
                http_status=response.status_code,
            ) from exc
        if not isinstance(body, dict):
            raise EvaluationClientError(
                code="EVALUATION_INVALID_RESPONSE",
                http_status=response.status_code,
            )
        if response.status_code != 200:
            code = str(body.get("code") or f"HTTP_{response.status_code}")
            raise EvaluationClientError(
                code=code[:100],
                http_status=response.status_code,
            )
        return body


class EvaluationRunner:
    def __init__(self, *, client: RagEvaluationClient, scope: EvaluationScope) -> None:
        self._client = client
        self._scope = scope

    def run(
        self,
        cases: Sequence[EvaluationCase],
        *,
        modes: Sequence[EvaluationMode],
        dataset_path: str,
        provenance: EvaluationProvenance | None = None,
    ) -> EvaluationReport:
        validate_evaluation_run(cases, self._scope, dataset_path, provenance)
        observations: list[EvaluationObservation] = []
        for case in cases:
            request = self._scope.request_for(case)
            for mode in modes:
                observations.append(self._run_case(case, request, mode))
        return EvaluationReport(
            generated_at=datetime.now(timezone.utc),
            dataset_path=dataset_path,
            provenance=provenance,
            scope=self._scope,
            summaries=[
                summarize_mode(mode, observations)
                for mode in modes
            ],
            observations=observations,
        )

    def _run_case(
        self,
        case: EvaluationCase,
        request: RagAnswerRequest,
        mode: EvaluationMode,
    ) -> EvaluationObservation:
        if case.annotation_status == "needs_review":
            return _review_observation(case, mode)
        started = perf_counter()
        try:
            if mode == EvaluationMode.RETRIEVAL:
                response = self._client.retrieve(request)
                latency_ms = (perf_counter() - started) * 1000
                return evaluate_retrieval(case, response, latency_ms)
            response = self._client.answer(request)
            latency_ms = (perf_counter() - started) * 1000
            return evaluate_answer(case, response, latency_ms)
        except EvaluationClientError as exc:
            return _error_observation(
                case,
                mode,
                (perf_counter() - started) * 1000,
                code=exc.code,
                http_status=exc.http_status,
            )
        except Exception as exc:
            return _error_observation(
                case,
                mode,
                (perf_counter() - started) * 1000,
                code=exc.__class__.__name__,
            )


def load_evaluation_cases(path: Path) -> list[EvaluationCase]:
    cases: list[EvaluationCase] = []
    seen_ids: set[str] = set()
    with path.open("r", encoding="utf-8") as source:
        for line_number, raw_line in enumerate(source, start=1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                payload = json.loads(line)
                case = EvaluationCase.model_validate(payload)
            except Exception as exc:
                raise ValueError(
                    f"invalid evaluation case at {path}:{line_number}"
                ) from exc
            if case.case_id in seen_ids:
                raise ValueError(
                    f"duplicate caseId '{case.case_id}' at {path}:{line_number}"
                )
            seen_ids.add(case.case_id)
            cases.append(case)
    if not cases:
        raise ValueError(f"evaluation dataset is empty: {path}")
    return cases


def evaluate_retrieval(
    case: EvaluationCase,
    response: RagRetrievalResponse,
    latency_ms: float,
) -> EvaluationObservation:
    relevance = [
        _is_relevant(citation, case.relevant_sources)
        for citation in response.evidence
    ]
    relevant_ranks = [
        index
        for index, is_relevant in enumerate(relevance, start=1)
        if is_relevant
    ]
    actual_hit = (
        bool(relevant_ranks)
        if case.expected_retrieval_hit
        else bool(response.evidence)
    )
    checks = {
        "retrievalHit": actual_hit == case.expected_retrieval_hit,
    }
    if case.forbidden_sources:
        checks["noForbiddenSources"] = not any(
            _is_relevant(citation, case.forbidden_sources)
            for citation in response.evidence
        )
    first_rank = relevant_ranks[0] if relevant_ranks else None
    noise_rate = None
    if case.expected_retrieval_hit:
        noise_rate = (
            1.0 - (len(relevant_ranks) / len(response.evidence))
            if response.evidence
            else 0.0
        )
    return EvaluationObservation(
        case_id=case.case_id,
        annotation_status=case.annotation_status,
        split=case.split,
        mode=EvaluationMode.RETRIEVAL,
        tags=case.tags,
        passed=all(checks.values()),
        checks=checks,
        latency_ms=round(latency_ms, 3),
        expected_retrieval_hit=case.expected_retrieval_hit,
        expected_answered=case.expected_answered,
        retrieved_count=response.retrieved_count,
        blocked_reason=response.blocked_reason,
        first_relevant_rank=first_rank,
        reciprocal_rank=(round(1.0 / first_rank, 6) if first_rank else 0.0),
        hit_at_k=(1.0 if first_rank else 0.0)
        if case.expected_retrieval_hit
        else None,
        noise_rate=round(noise_rate, 6) if noise_rate is not None else None,
        sources=_sources(response.evidence, relevance),
    )


def evaluate_answer(
    case: EvaluationCase,
    response: RagAnswerResponse,
    latency_ms: float,
) -> EvaluationObservation:
    relevance = [
        _is_relevant(citation, case.relevant_sources)
        for citation in response.citations
    ]
    relevant_ranks = [
        citation.citation_index
        for citation, is_relevant in zip(response.citations, relevance)
        if is_relevant
    ]
    term_groups_ok = all(
        any(_affirmative_term_present(response.answer, term) for term in group)
        for group in case.required_answer_term_groups
    )
    forbidden_terms_ok = not any(
        _affirmative_term_present(response.answer, term)
        for term in case.forbidden_answer_terms
    )
    checks = {
        "answered": response.answered == case.expected_answered,
        "forbiddenTerms": forbidden_terms_ok,
    }
    citation_hit: bool | None = None
    if case.expected_answered:
        citation_hit = bool(relevant_ranks)
        checks.update(
            {
                "minimumCitations": len(response.citations) >= case.min_citations,
                "relevantCitation": citation_hit,
                "requiredAnswerTerms": term_groups_ok,
            }
        )
    else:
        checks["noCitationsOnRefusal"] = not response.citations

    review_reasons = []
    if case.dataset_version != "v1":
        checks["requiredAnswerPatterns"] = _answer_patterns_present(
            response.answer, case.required_answer_patterns,
        )
        checks["noForbiddenSources"] = not any(
            _is_relevant(citation, case.forbidden_sources)
            for citation in response.citations
        )
        if case.annotation_status == "verified" and any(
            not relevant and not _is_relevant(citation, case.forbidden_sources)
            for citation, relevant in zip(response.citations, relevance)
        ):
            review_reasons.append("unannotated_citation_requires_review")
            checks["citationReviewComplete"] = False

    first_rank = min(relevant_ranks) if relevant_ranks else None
    return EvaluationObservation(
        case_id=case.case_id,
        annotation_status=case.annotation_status,
        split=case.split,
        mode=EvaluationMode.ANSWER,
        tags=case.tags,
        passed=all(checks.values()),
        checks=checks,
        latency_ms=round(latency_ms, 3),
        expected_retrieval_hit=case.expected_retrieval_hit,
        expected_answered=case.expected_answered,
        retrieved_count=response.retrieved_count,
        answered=response.answered,
        answer=response.answer[:4000],
        review_required=bool(review_reasons),
        review_reasons=review_reasons,
        first_relevant_rank=first_rank,
        reciprocal_rank=(round(1.0 / first_rank, 6) if first_rank else 0.0),
        citation_hit=citation_hit,
        sources=_sources(response.citations, relevance),
    )


def summarize_mode(
    mode: EvaluationMode,
    observations: Iterable[EvaluationObservation],
) -> EvaluationModeSummary:
    selected = [item for item in observations if item.mode == mode]
    eligible = [item for item in selected if item.annotation_status != "needs_review"]
    latencies = [item.latency_ms for item in eligible]
    hit_values = [
        item.hit_at_k for item in selected if item.hit_at_k is not None
    ]
    reciprocal_values = [
        item.reciprocal_rank
        for item in selected
        if item.expected_retrieval_hit and item.reciprocal_rank is not None
    ]
    noise_values = [
        item.noise_rate for item in selected if item.noise_rate is not None
    ]
    positive_answers = [item for item in eligible if item.expected_answered]
    refusal_answers = [item for item in eligible if not item.expected_answered]
    citation_values = [
        item.citation_hit
        for item in positive_answers
        if item.citation_hit is not None
    ]

    return EvaluationModeSummary(
        mode=mode,
        cases=len(selected),
        positive_cases=len(positive_answers),
        negative_cases=len(refusal_answers),
        unreviewed_cases=sum(item.annotation_status != "verified" for item in selected),
        pending_review_cases=sum(item.review_required for item in selected),
        annotation_coverage=_ratio(sum(item.annotation_status == "verified" for item in selected), len(selected)),
        execution_success_rate=_ratio(
            sum(item.error_code is None for item in eligible),
            len(eligible),
        ),
        positive_rule_pass_rate=(
            _ratio(sum(item.passed for item in positive_answers), len(positive_answers))
            if positive_answers else None
        ),
        passed=sum(item.passed for item in selected),
        pass_rate=_ratio(sum(item.passed for item in eligible), len(eligible)),
        errors=sum(item.error_code is not None for item in selected),
        average_latency_ms=_average(latencies),
        p50_latency_ms=_percentile(latencies, 0.50),
        p95_latency_ms=_percentile(latencies, 0.95),
        hit_at_k=_average(hit_values) if hit_values else None,
        mean_reciprocal_rank=(
            _average(reciprocal_values) if reciprocal_values else None
        ),
        noise_rate=_average(noise_values) if noise_values else None,
        answer_accuracy=(
            _ratio(
                sum(item.checks.get("answered", False) for item in selected),
                len(selected),
            )
            if mode == EvaluationMode.ANSWER
            else None
        ),
        refusal_accuracy=(
            _ratio(
                sum(
                    item.checks.get("answered", False)
                    and item.checks.get("noCitationsOnRefusal", False)
                    for item in refusal_answers
                ),
                len(refusal_answers),
            )
            if mode == EvaluationMode.ANSWER and refusal_answers
            else None
        ),
        citation_hit_rate=(
            _ratio(sum(bool(value) for value in citation_values), len(citation_values))
            if citation_values
            else None
        ),
    )


def _is_relevant(
    citation: RagCitation,
    rules: Sequence[RelevantSourceRule],
) -> bool:
    for rule in rules:
        if rule.document_titles and not any(
            _normalize(title) in _normalize(citation.document_title)
            for title in rule.document_titles
        ):
            continue
        if rule.document_ids and citation.document_id not in rule.document_ids:
            continue
        if (
            rule.document_version_ids
            and citation.document_version_id not in rule.document_version_ids
        ):
            continue
        if rule.page_numbers and citation.page_number not in rule.page_numbers:
            continue
        if rule.chunk_indexes and citation.chunk_index not in rule.chunk_indexes:
            continue
        content = _normalize(f"{citation.heading}\n{citation.quote}")
        if rule.contains_any and not any(
            _normalize(term) in content for term in rule.contains_any
        ):
            continue
        return True
    return False


def _sources(
    citations: Sequence[RagCitation],
    relevance: Sequence[bool],
) -> list[EvaluationSource]:
    return [
        EvaluationSource(
            citation_index=citation.citation_index,
            document_id=citation.document_id,
            document_version_id=citation.document_version_id,
            document_title=citation.document_title,
            page_number=citation.page_number,
            chunk_index=citation.chunk_index,
            score=citation.score,
            relevant=is_relevant,
        )
        for citation, is_relevant in zip(citations, relevance)
    ]


def _error_observation(
    case: EvaluationCase,
    mode: EvaluationMode,
    latency_ms: float,
    *,
    code: str,
    http_status: int | None = None,
) -> EvaluationObservation:
    return EvaluationObservation(
        case_id=case.case_id,
        annotation_status=case.annotation_status,
        split=case.split,
        mode=mode,
        tags=case.tags,
        passed=False,
        checks={"requestSucceeded": False},
        latency_ms=round(latency_ms, 3),
        expected_retrieval_hit=case.expected_retrieval_hit,
        expected_answered=case.expected_answered,
        hit_at_k=(0.0 if case.expected_retrieval_hit and mode == EvaluationMode.RETRIEVAL else None),
        reciprocal_rank=(0.0 if case.expected_retrieval_hit else None),
        citation_hit=(False if case.expected_answered and mode == EvaluationMode.ANSWER else None),
        error_code=code[:100],
        http_status=http_status,
    )


def _normalize(value: str) -> str:
    return "".join(
        unicodedata.normalize("NFKC", value).casefold().split()
    )


def _average(values: Sequence[float]) -> float:
    if not values:
        return 0.0
    return round(sum(values) / len(values), 3)


def _ratio(numerator: int, denominator: int) -> float:
    if denominator == 0:
        return 0.0
    return round(numerator / denominator, 6)


def _percentile(values: Sequence[float], percentile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, math.ceil(percentile * len(ordered)) - 1)
    return round(ordered[index], 3)


def validate_evaluation_run(
    cases: Sequence[EvaluationCase],
    scope: EvaluationScope,
    dataset_path: str,
    provenance: EvaluationProvenance | None,
) -> None:
    modern = [case for case in cases if case.dataset_version != "v1"]
    if not modern:
        return
    if len(modern) != len(cases):
        raise ValueError("legacy and versioned cases cannot share a run")
    if provenance is None:
        raise ValueError("v2 evaluation requires a frozen run manifest")
    actual_hash = hashlib.sha256(Path(dataset_path).read_bytes()).hexdigest()
    if actual_hash != provenance.dataset_sha256:
        raise ValueError("dataset fingerprint differs from the frozen manifest")
    for case in modern:
        if (
            case.corpus_fingerprint != provenance.corpus_fingerprint
            or case.dataset_version != provenance.dataset_version
            or case.split != provenance.split
        ):
            raise ValueError("case provenance differs from the frozen manifest")
        for rule in [*case.relevant_sources, *case.forbidden_sources]:
            if not set(rule.document_version_ids).issubset(scope.document_version_ids):
                raise ValueError("annotated evidence is outside Java-approved versions")


def validate_dataset_splits(
    development: Sequence[EvaluationCase],
    holdout: Sequence[EvaluationCase],
) -> None:
    development_groups = {case.task_group for case in development if case.task_group}
    development_questions = {_normalize(case.question) for case in development}
    if any(case.split != "development" for case in development):
        raise ValueError("development data contains another split")
    if any(case.split != "holdout" for case in holdout):
        raise ValueError("holdout data contains another split")
    if any(
        case.task_group in development_groups
        or _normalize(case.question) in development_questions
        for case in holdout
    ):
        raise ValueError("development and holdout tasks overlap")


_NEGATED_PREFIX = re.compile(
    r"(?:没有|无需|不需要|无须|不要|不应|不可|未提及|未包含|不是|并非)"
    r"[^。；;！!？?，,\n]{0,24}$|(?:不能|不得|禁止|不|勿)\s*$"
)


def _affirmative_term_present(answer: str, term: str) -> bool:
    normalized = _normalize(answer)
    needle = _normalize(term)
    position = normalized.find(needle)
    while position >= 0:
        prefix = normalized[max(0, position - 32):position]
        if not _NEGATED_PREFIX.search(prefix):
            return True
        position = normalized.find(needle, position + 1)
    return False


def _answer_patterns_present(answer: str, groups: Sequence[Sequence[str]]) -> bool:
    return all(
        any(
            not _NEGATED_PREFIX.search(answer[max(0, match.start() - 32):match.start()])
            for pattern in alternatives
            for match in re.finditer(pattern, answer, re.IGNORECASE)
        )
        for alternatives in groups
    )


def _review_observation(
    case: EvaluationCase, mode: EvaluationMode,
) -> EvaluationObservation:
    return EvaluationObservation(
        case_id=case.case_id,
        mode=mode,
        tags=case.tags,
        annotation_status=case.annotation_status,
        split=case.split,
        passed=False,
        review_required=True,
        review_reasons=[case.annotation_note or "annotation_not_verified"],
        checks={"annotationVerified": False},
        latency_ms=0,
        expected_retrieval_hit=case.expected_retrieval_hit,
        expected_answered=case.expected_answered,
    )
