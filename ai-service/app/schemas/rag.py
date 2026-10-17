from pydantic import Field, field_validator, model_validator

from app.schemas.common import ApiModel


class RagAnswerRequest(ApiModel):
    question: str = Field(min_length=2, max_length=500)
    knowledge_base_id: int = Field(gt=0)
    brand_id: int = Field(gt=0)
    model_id: int = Field(gt=0)
    brand_name: str | None = Field(default=None, max_length=100)
    model_name: str | None = Field(default=None, max_length=200)
    document_version_ids: list[int] = Field(min_length=1, max_length=200)
    top_k: int = Field(default=5, ge=1, le=10)
    score_threshold: float = Field(default=0.45, ge=-1, le=1)

    @field_validator("question")
    @classmethod
    def normalize_question(cls, value: str) -> str:
        normalized = value.strip()
        if len(normalized) < 2:
            raise ValueError("question must contain at least two characters")
        return normalized

    @model_validator(mode="after")
    def validate_document_versions(self) -> "RagAnswerRequest":
        if any(version_id <= 0 for version_id in self.document_version_ids):
            raise ValueError("document_version_ids must contain only positive values")
        if len(set(self.document_version_ids)) != len(self.document_version_ids):
            raise ValueError("document_version_ids must be unique")
        return self


class RagCitation(ApiModel):
    citation_index: int
    knowledge_base_id: int
    brand_id: int
    model_id: int
    document_id: int
    document_version_id: int
    document_title: str
    original_file_name: str
    page_number: int | None = None
    chunk_index: int
    heading: str
    quote: str
    score: float


class RagAnswerResponse(ApiModel):
    answered: bool
    answer: str
    refusal_reason: str | None = None
    citations: list[RagCitation]
    retrieved_count: int
    score_threshold: float
    model_name: str | None = None


class RagRetrievalResponse(ApiModel):
    evidence: list[RagCitation]
    retrieved_count: int
    score_threshold: float
    blocked_reason: str | None = None
