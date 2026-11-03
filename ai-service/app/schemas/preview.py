from typing import Literal

from pydantic import Field, model_validator

from app.schemas.common import ApiModel
from app.schemas.document import DocumentParseResponse, DocumentTable


class DocumentPreviewRequest(ApiModel):
    document_version_id: int = Field(gt=0)
    file_hash: str = Field(pattern=r"^[a-fA-F0-9]{64}$")
    parse_id: str = Field(pattern=r"^[a-f0-9]{64}$")
    parsed: DocumentParseResponse
    view: Literal["blocks", "legacy", "structured"] = "blocks"
    page_num: int = Field(default=1, ge=1, le=1000000)
    page_size: int = Field(default=20, ge=1, le=100)
    selected_model: str = Field(default="", max_length=200)

    @model_validator(mode="after")
    def validate_source(self) -> "DocumentPreviewRequest":
        parsed = self.parsed
        if parsed.schema_version not in {1, 2}:
            raise ValueError("unsupported parse schema version")
        if parsed.file_hash.lower() != self.file_hash.lower():
            raise ValueError("parse file hash differs from the Java version")
        if parsed.block_count != len(parsed.blocks):
            raise ValueError("parse block count mismatch")
        if parsed.character_count != sum(len(block.text) for block in parsed.blocks):
            raise ValueError("parse character count mismatch")
        sequences = [block.sequence for block in parsed.blocks]
        if sequences != sorted(set(sequences)) or any(index < 1 for index in sequences):
            raise ValueError("source block sequences must be positive, unique and ascending")
        return self


class PreviewSource(ApiModel):
    block_sequence: int
    page_number: int | None = None
    source_item_index: int | None = None
    source_part_index: int | None = None
    row_index: int | None = None
    column_index: int | None = None
    character_start: int | None = None
    character_end: int | None = None
    role: str = "body"
    element_index: int | None = None


class PreviewItem(ApiModel):
    candidate_id: str
    ordinal: int
    kind: str
    text: str
    page_number: int | None = None
    heading_path: list[str] = Field(default_factory=list)
    sources: list[PreviewSource] = Field(default_factory=list)
    model_scope: list[str] | None = None
    scope_status: Literal["shared", "restricted", "unknown"]
    scope_origin: str
    scope_sources: list[PreviewSource] = Field(default_factory=list)
    eligible_for_selected_model: bool | None = None
    group_id: str | None = None
    continuation_of: int | None = None
    table: DocumentTable | None = None
    warnings: list[str] = Field(default_factory=list)


class DocumentPreviewResponse(ApiModel):
    document_version_id: int
    parse_id: str
    schema_version: int
    normalization_version: str
    view: str
    profile: str
    chunk_size: int
    overlap_chars: int
    total: int
    page_num: int
    page_size: int
    items: list[PreviewItem]
    warnings: list[str] = Field(default_factory=list)
