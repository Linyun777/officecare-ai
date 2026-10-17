from datetime import datetime

from pydantic import Field, field_validator, model_validator

from app.schemas.common import ApiModel
from app.schemas.document import DocumentBlock


class DocumentIndexRequest(ApiModel):
    knowledge_base_id: int = Field(gt=0)
    brand_id: int = Field(gt=0)
    model_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    document_version_id: int = Field(gt=0)
    version_no: int = Field(gt=0)
    document_title: str = Field(min_length=1, max_length=200)
    original_file_name: str = Field(min_length=1, max_length=255)
    file_hash: str = Field(pattern=r"^[0-9a-fA-F]{64}$")
    block_count: int = Field(gt=0)
    character_count: int = Field(gt=0)
    blocks: list[DocumentBlock] = Field(min_length=1)

    @field_validator("document_title", "original_file_name")
    @classmethod
    def normalize_required_text(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("must not be blank")
        return normalized

    @model_validator(mode="after")
    def validate_parse_statistics(self) -> "DocumentIndexRequest":
        if self.block_count != len(self.blocks):
            raise ValueError("block_count does not match blocks")
        actual_character_count = sum(len(block.text) for block in self.blocks)
        if self.character_count != actual_character_count:
            raise ValueError("character_count does not match blocks")
        sequences = [block.sequence for block in self.blocks]
        if sequences != sorted(sequences) or len(set(sequences)) != len(sequences):
            raise ValueError("block sequences must be unique and ascending")
        return self


class DocumentIndexResponse(ApiModel):
    document_id: int
    document_version_id: int
    collection_name: str
    embedding_model: str
    embedding_dimension: int
    chunk_count: int
    indexed_character_count: int
    index_content_hash: str
    indexed_at: datetime


class DocumentIndexDeleteResponse(ApiModel):
    document_version_id: int
    collection_name: str
    deleted_count: int
