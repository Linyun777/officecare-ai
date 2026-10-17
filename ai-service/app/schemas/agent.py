from datetime import date

from pydantic import BaseModel, Field, field_validator, model_validator

from app.schemas.common import ApiModel
from app.schemas.rag import RagCitation


class AgentDeviceContext(ApiModel):
    """Device snapshot supplied and authorized by the Java service."""

    device_id: int = Field(gt=0)
    customer_org_id: int = Field(gt=0)
    customer_name: str = Field(min_length=1, max_length=150)
    device_name: str = Field(min_length=1, max_length=100)
    serial_number: str = Field(min_length=1, max_length=100)
    brand_id: int = Field(gt=0)
    brand_name: str = Field(min_length=1, max_length=100)
    model_id: int = Field(gt=0)
    model_name: str = Field(min_length=1, max_length=200)
    install_location: str | None = Field(default=None, max_length=255)
    warranty_end_date: date | None = None

    @field_validator(
        "customer_name",
        "device_name",
        "serial_number",
        "brand_name",
        "model_name",
    )
    @classmethod
    def normalize_required_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("must not be blank")
        return normalized


class TroubleshootingAgentRequest(ApiModel):
    issue_description: str = Field(min_length=2, max_length=2000)
    knowledge_base_id: int = Field(gt=0)
    document_version_ids: list[int] = Field(min_length=1, max_length=200)
    device: AgentDeviceContext
    top_k: int = Field(default=5, ge=1, le=10)
    score_threshold: float = Field(default=0.45, ge=-1, le=1)

    @field_validator("issue_description")
    @classmethod
    def normalize_issue_description(cls, value: str) -> str:
        normalized = value.strip()
        if len(normalized) < 2:
            raise ValueError("issue_description must contain at least two characters")
        return normalized

    @model_validator(mode="after")
    def validate_document_versions(self) -> "TroubleshootingAgentRequest":
        if any(version_id <= 0 for version_id in self.document_version_ids):
            raise ValueError("document_version_ids must contain only positive values")
        if len(set(self.document_version_ids)) != len(self.document_version_ids):
            raise ValueError("document_version_ids must be unique")
        return self


class AgentWorkOrderDraft(ApiModel):
    """Editable draft only; it is never persisted by FastAPI."""

    title: str = Field(min_length=2, max_length=150)
    fault_description: str = Field(min_length=2, max_length=2000)
    priority: str = Field(pattern=r"^[0123]$")


class AgentToolTrace(ApiModel):
    sequence: int = Field(ge=1)
    tool_name: str
    success: bool


class TroubleshootingAgentResponse(ApiModel):
    answered: bool
    summary: str
    troubleshooting_steps: list[str]
    safety_warnings: list[str]
    requires_on_site_service: bool
    requires_human_review: bool = True
    refusal_reason: str | None = None
    draft: AgentWorkOrderDraft
    citations: list[RagCitation]
    tool_trace: list[AgentToolTrace]
    model_name: str | None = None


class AgentAnalysis(ApiModel):
    """Structured final analysis emitted after all tools have run."""

    summary: str = Field(min_length=2, max_length=1000)
    troubleshooting_steps: list[str] = Field(default_factory=list, max_length=12)
    safety_warnings: list[str] = Field(default_factory=list, max_length=8)
    requires_on_site_service: bool

    @field_validator("troubleshooting_steps", "safety_warnings")
    @classmethod
    def normalize_text_items(cls, values: list[str]) -> list[str]:
        normalized = [str(value).strip() for value in values if str(value).strip()]
        return normalized


class DraftToolInput(BaseModel):
    title: str = Field(min_length=2, max_length=150)
    fault_description: str = Field(min_length=2, max_length=1600)
    priority: str = Field(pattern=r"^[0123]$")
