from typing import Literal

from pydantic import Field

from app.schemas.common import ApiModel
from app.schemas.document import DocumentParseResponse
from app.schemas.indexing import DocumentIndexResponse


class GenerationIndexRequest(ApiModel):
    generation_id: str = Field(pattern=r"^[a-f0-9]{32}$")
    parse_id: str = Field(pattern=r"^[a-f0-9]{64}$")
    profile: Literal["legacy", "structured"] = "structured"
    knowledge_base_id: int = Field(gt=0)
    brand_id: int = Field(gt=0)
    model_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    document_version_id: int = Field(gt=0)
    version_no: int = Field(gt=0)
    document_title: str = Field(min_length=1, max_length=200)
    parsed: DocumentParseResponse


class GenerationIndexResponse(DocumentIndexResponse):
    generation_id: str
    parse_id: str
    profile: str
    chunk_size: int
    overlap_chars: int


class GenerationDeleteResponse(ApiModel):
    collection_name: str
    deleted_count: int
