from __future__ import annotations

from datetime import datetime
from enum import StrEnum
import re
from typing import Literal

from pydantic import AliasChoices, Field, field_validator, model_validator

from app.schemas.common import ApiModel
from app.schemas.rag import RagAnswerRequest


class EvaluationMode(StrEnum):
    RETRIEVAL = "retrieval"
    ANSWER = "answer"


class RelevantSourceRule(ApiModel):
    document_titles: list[str] = Field(default_factory=list)
    document_ids: list[int] = Field(default_factory=list)
    document_version_ids: list[int] = Field(default_factory=list)
    page_numbers: list[int] = Field(default_factory=list)
    chunk_indexes: list[int] = Field(default_factory=list)
    contains_any: list[str] = Field(default_factory=list)

    @field_validator("document_titles", "contains_any")
    @classmethod
    def normalize_text_values(cls, values: list[str]) -> list[str]:
        return _normalize_unique_texts(values)

    @field_validator(
        "document_ids",
        "document_version_ids",
        "page_numbers",
    )
    @classmethod
    def validate_positive_values(cls, values: list[int]) -> list[int]:
        if any(value <= 0 for value in values):
            raise ValueError("source identifiers and positions must be positive")
        return list(dict.fromkeys(values))

    @field_validator("chunk_indexes")
    @classmethod
    def validate_chunk_indexes(cls, values: list[int]) -> list[int]:
        if any(value < 0 for value in values):
            raise ValueError("chunk indexes must be non-negative")
        return list(dict.fromkeys(values))

    @model_validator(mode="after")
    def require_at_least_one_constraint(self) -> "RelevantSourceRule":
        if not any(
            (
                self.document_titles,
                self.document_ids,
                self.document_version_ids,
                self.page_numbers,
                self.chunk_indexes,
                self.contains_any,
            )
        ):
            raise ValueError("a relevant source rule must contain a constraint")
        return self


class EvaluationCase(ApiModel):
    dataset_version: str = "v1"
    split: Literal["development", "holdout"] = "development"
    task_group: str = ""
    annotation_status: Literal["legacy", "verified", "needs_review"] = "legacy"
    annotation_note: str = ""
    corpus_fingerprint: str | None = Field(default=None, pattern=r"^[a-f0-9]{64}$")
    forbidden_sources: list[RelevantSourceRule] = Field(default_factory=list)
    required_answer_patterns: list[list[str]] = Field(default_factory=list)
    case_id: str = Field(pattern=r"^[a-z0-9][a-z0-9._-]{1,79}$")
    question: str = Field(min_length=2, max_length=500)
    tags: list[str] = Field(default_factory=list, max_length=20)
    expected_retrieval_hit: bool
    expected_answered: bool
    relevant_sources: list[RelevantSourceRule] = Field(default_factory=list)
    required_answer_term_groups: list[list[str]] = Field(default_factory=list)
    forbidden_answer_terms: list[str] = Field(default_factory=list)
    min_citations: int = Field(default=1, ge=0, le=10)
    top_k: int | None = Field(default=None, ge=1, le=10)
    score_threshold: float | None = Field(default=None, ge=-1, le=1)

    @field_validator("question")
    @classmethod
    def normalize_question(cls, value: str) -> str:
        normalized = value.strip()
        if len(normalized) < 2:
            raise ValueError("question must contain at least two characters")
        return normalized

    @field_validator("tags", "forbidden_answer_terms")
    @classmethod
    def normalize_string_lists(cls, values: list[str]) -> list[str]:
        return _normalize_unique_texts(values)

    @field_validator("required_answer_term_groups")
    @classmethod
    def normalize_term_groups(cls, groups: list[list[str]]) -> list[list[str]]:
        normalized_groups = [_normalize_unique_texts(group) for group in groups]
        if any(not group for group in normalized_groups):
            raise ValueError("required answer term groups cannot be empty")
        return normalized_groups

    @model_validator(mode="after")
    def validate_expectations(self) -> "EvaluationCase":
        if self.expected_retrieval_hit and not self.relevant_sources:
            raise ValueError(
                "positive retrieval cases must define at least one relevant source"
            )
        if self.expected_answered and not self.expected_retrieval_hit:
            raise ValueError("answerable cases must also expect a retrieval hit")
        if self.expected_answered and self.min_citations < 1:
            raise ValueError("answerable cases must require at least one citation")
        if (self.dataset_version == "v1") != (self.annotation_status == "legacy"):
            raise ValueError("legacy annotations and versioned annotations cannot be interchanged")
        if self.dataset_version != "v1":
            if not self.task_group or not self.annotation_note or not self.corpus_fingerprint:
                raise ValueError("v2 cases require a task group, annotation and corpus fingerprint")
            if self.annotation_status == "verified" and self.expected_retrieval_hit:
                if not all(rule.document_version_ids and rule.chunk_indexes for rule in self.relevant_sources):
                    raise ValueError("verified positive cases require exact version and chunk anchors")
        for alternatives in self.required_answer_patterns:
            if not alternatives:
                raise ValueError("answer pattern groups cannot be empty")
            for pattern in alternatives:
                re.compile(pattern)
        return self


class EvaluationScope(ApiModel):
    knowledge_base_id: int = Field(gt=0)
    brand_id: int = Field(gt=0)
    model_id: int = Field(gt=0)
    brand_name: str = Field(min_length=1, max_length=100)
    model_name: str = Field(min_length=1, max_length=200)
    document_version_ids: list[int] = Field(min_length=1, max_length=200)
    top_k: int = Field(default=5, ge=1, le=10)
    score_threshold: float = Field(default=0.45, ge=-1, le=1)

    @field_validator("brand_name", "model_name")
    @classmethod
    def normalize_names(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("scope names cannot be blank")
        return normalized

    @field_validator("document_version_ids")
    @classmethod
    def validate_versions(cls, values: list[int]) -> list[int]:
        if any(value <= 0 for value in values):
            raise ValueError("document version identifiers must be positive")
        if len(set(values)) != len(values):
            raise ValueError("document version identifiers must be unique")
        return values

    def request_for(self, case: EvaluationCase) -> RagAnswerRequest:
        return RagAnswerRequest(
            question=case.question,
            knowledge_base_id=self.knowledge_base_id,
            brand_id=self.brand_id,
            model_id=self.model_id,
            brand_name=self.brand_name,
            model_name=self.model_name,
            document_version_ids=self.document_version_ids,
            top_k=case.top_k or self.top_k,
            score_threshold=(
                case.score_threshold
                if case.score_threshold is not None
                else self.score_threshold
            ),
        )


class EvaluationSource(ApiModel):
    citation_index: int
    document_id: int
    document_version_id: int
    document_title: str
    page_number: int | None = None
    chunk_index: int
    score: float
    relevant: bool


class EvaluationObservation(ApiModel):
    annotation_status: str = "legacy"
    split: str = "development"
    review_required: bool = False
    review_reasons: list[str] = Field(default_factory=list)
    case_id: str
    mode: EvaluationMode
    tags: list[str]
    passed: bool
    checks: dict[str, bool]
    latency_ms: float
    expected_retrieval_hit: bool
    expected_answered: bool
    retrieved_count: int = 0
    answered: bool | None = None
    answer: str | None = None
    blocked_reason: str | None = None
    first_relevant_rank: int | None = None
    reciprocal_rank: float | None = None
    hit_at_k: float | None = Field(
        default=None,
        validation_alias=AliasChoices("hitAtK", "hit_at_k", "recallAtK", "recall_at_k"),
    )
    noise_rate: float | None = None
    citation_hit: bool | None = None
    sources: list[EvaluationSource] = Field(default_factory=list)
    error_code: str | None = None
    http_status: int | None = None


class EvaluationModeSummary(ApiModel):
    positive_cases: int = 0
    negative_cases: int = 0
    unreviewed_cases: int = 0
    pending_review_cases: int = 0
    annotation_coverage: float = 0.0
    execution_success_rate: float = 0.0
    positive_rule_pass_rate: float | None = None
    mode: EvaluationMode
    cases: int
    passed: int
    pass_rate: float
    errors: int
    average_latency_ms: float
    p50_latency_ms: float
    p95_latency_ms: float
    hit_at_k: float | None = Field(
        default=None,
        validation_alias=AliasChoices("hitAtK", "hit_at_k", "recallAtK", "recall_at_k"),
    )
    mean_reciprocal_rank: float | None = None
    noise_rate: float | None = None
    answer_accuracy: float | None = None
    refusal_accuracy: float | None = None
    citation_hit_rate: float | None = None


class EvaluationProvenance(ApiModel):
    dataset_version: str
    dataset_sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    corpus_fingerprint: str = Field(pattern=r"^[a-f0-9]{64}$")
    algorithm_version: str
    chat_model: str
    embedding_model: str
    split: Literal["development", "holdout"]



class EvaluationReport(ApiModel):
    schema_version: int = 2
    provenance: EvaluationProvenance | None = None
    generated_at: datetime
    dataset_path: str
    scope: EvaluationScope
    summaries: list[EvaluationModeSummary]
    observations: list[EvaluationObservation]


def _normalize_unique_texts(values: list[str]) -> list[str]:
    normalized = [value.strip() for value in values if value.strip()]
    return list(dict.fromkeys(normalized))
