from enum import StrEnum

from app.schemas.common import ApiModel


class SupportedFileType(StrEnum):
    PDF = "pdf"
    DOCX = "docx"
    TXT = "txt"


class BlockType(StrEnum):
    PAGE = "page"
    PARAGRAPH = "paragraph"
    HEADING = "heading"
    TABLE = "table"


class DocumentBlock(ApiModel):
    sequence: int
    page_number: int | None = None
    block_type: BlockType
    text: str


class DocumentParseResponse(ApiModel):
    file_name: str
    file_type: SupportedFileType
    content_type: str
    file_size: int
    file_hash: str
    page_count: int | None = None
    block_count: int
    character_count: int
    blocks: list[DocumentBlock]
    warnings: list[str]


class HealthResponse(ApiModel):
    status: str
    service: str
    version: str
    environment: str
    supported_file_types: list[SupportedFileType]
