from enum import Enum
from typing import Any, Literal
from uuid import UUID

from langchain.agents import AgentState
from pydantic import BaseModel, Field, field_validator, model_validator
from typing_extensions import NotRequired

from app.schemas.common import ApiModel
from app.schemas.rag import RagCitation


class AssistantResponseType(str, Enum):
    ANSWER = "ANSWER"
    CLARIFICATION = "CLARIFICATION"
    DIAGNOSTIC_STEP = "DIAGNOSTIC_STEP"
    RESOLUTION = "RESOLUTION"
    ESCALATION = "ESCALATION"
    REFUSAL = "REFUSAL"


class AssistantDiagnosticStatus(str, Enum):
    IDLE = "IDLE"
    COLLECTING_INFORMATION = "COLLECTING_INFORMATION"
    AWAITING_STEP_RESULT = "AWAITING_STEP_RESULT"
    RESOLVED = "RESOLVED"
    ESCALATE = "ESCALATE"
    INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"


class AssistantBootstrapMessage(ApiModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)

    @field_validator("content")
    @classmethod
    def normalize_content(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("content must not be blank")
        return normalized


class AssistantTurnRequest(ApiModel):
    conversation_id: int = Field(gt=0)
    user_id: int = Field(gt=0)
    turn_id: UUID
    question: str = Field(min_length=2, max_length=500)
    knowledge_base_id: int = Field(gt=0)
    brand_id: int = Field(gt=0)
    model_id: int = Field(gt=0)
    brand_name: str = Field(min_length=1, max_length=100)
    model_name: str = Field(min_length=1, max_length=200)
    document_version_ids: list[int] = Field(min_length=1, max_length=200)
    top_k: int = Field(default=5, ge=1, le=10)
    score_threshold: float = Field(default=0.45, ge=-1, le=1)
    bootstrap_messages: list[AssistantBootstrapMessage] = Field(
        default_factory=list,
        max_length=24,
    )

    @field_validator("question", "brand_name", "model_name")
    @classmethod
    def normalize_required_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("must not be blank")
        return normalized

    @model_validator(mode="after")
    def validate_document_versions(self) -> "AssistantTurnRequest":
        if any(version_id <= 0 for version_id in self.document_version_ids):
            raise ValueError("document_version_ids must contain only positive values")
        if len(set(self.document_version_ids)) != len(self.document_version_ids):
            raise ValueError("document_version_ids must be unique")
        return self


class AssistantKnowledgeSearchInput(BaseModel):
    query: str = Field(min_length=2, max_length=500)

    @field_validator("query")
    @classmethod
    def normalize_query(cls, value: str) -> str:
        normalized = value.strip()
        if len(normalized) < 2:
            raise ValueError("query must contain at least two characters")
        return normalized


class AssistantAgentDecision(ApiModel):
    response_type: AssistantResponseType
    answer: str = Field(min_length=2, max_length=2000)
    diagnostic_status: AssistantDiagnosticStatus
    issue_summary: str = Field(default="", max_length=500)
    attempted_steps: list[str] = Field(default_factory=list, max_length=12)
    observations: list[str] = Field(default_factory=list, max_length=12)
    next_question: str | None = Field(default=None, max_length=500)
    requires_user_reply: bool
    requires_on_site_service: bool

    @field_validator("answer", "issue_summary")
    @classmethod
    def normalize_text(cls, value: str) -> str:
        return value.strip()

    @field_validator("attempted_steps", "observations")
    @classmethod
    def normalize_text_items(cls, values: list[str]) -> list[str]:
        return [str(value).strip() for value in values if str(value).strip()]

    @field_validator("next_question")
    @classmethod
    def normalize_optional_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class AssistantTurnResponse(AssistantAgentDecision):
    answered: bool
    refusal_reason: str | None = None
    citations: list[RagCitation] = Field(default_factory=list)
    retrieved_count: int = Field(default=0, ge=0)
    score_threshold: float = Field(ge=-1, le=1)
    model_name: str | None = None


class AssistantAgentState(AgentState[AssistantAgentDecision]):
    conversation_id: NotRequired[int]
    owner_user_id: NotRequired[int]
    knowledge_base_id: NotRequired[int]
    brand_id: NotRequired[int]
    model_id: NotRequired[int]
    diagnostic_status: NotRequired[str]
    issue_summary: NotRequired[str]
    attempted_steps: NotRequired[list[str]]
    observations: NotRequired[list[str]]
    last_turn_id: NotRequired[str]
    last_turn_question: NotRequired[str]
    last_response: NotRequired[dict[str, Any]]
