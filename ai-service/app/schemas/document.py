from enum import StrEnum

from pydantic import Field, model_validator

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


class TableCell(ApiModel):
    column_index: int = Field(ge=0)
    text: str
    row_span: int = Field(default=1, ge=1, le=1000)
    column_span: int = Field(default=1, ge=1, le=1000)
    header: bool = False


class TableRow(ApiModel):
    row_index: int = Field(ge=0)
    cells: list[TableCell]


class DocumentTable(ApiModel):
    rows: list[TableRow] = Field(default_factory=list)
    captions: list[str] = Field(default_factory=list)
    footnotes: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_layout(self) -> "DocumentTable":
        indexes = [row.row_index for row in self.rows]
        if indexes != sorted(set(indexes)):
            raise ValueError("table rows must be unique and ascending")
        occupied: set[tuple[int, int]] = set()
        for row in self.rows:
            columns = [cell.column_index for cell in row.cells]
            if columns != sorted(set(columns)):
                raise ValueError("table columns must be unique and ascending")
            for cell in row.cells:
                if len(occupied) + cell.row_span * cell.column_span > 20000:
                    raise ValueError("table layout exceeds the supported cell budget")
                for row_index in range(row.row_index, row.row_index + cell.row_span):
                    for column in range(cell.column_index, cell.column_index + cell.column_span):
                        position = (row_index, column)
                        if position in occupied:
                            raise ValueError("table cells overlap")
                        occupied.add(position)
        return self


class DocumentBlock(ApiModel):
    sequence: int
    page_number: int | None = None
    block_type: BlockType
    text: str
    heading_level: int | None = Field(default=None, ge=1)
    source_item_index: int | None = Field(default=None, ge=0)
    source_part_index: int | None = Field(default=None, ge=0)
    table: DocumentTable | None = None


class DocumentParseResponse(ApiModel):
    schema_version: int = 1
    normalization_version: str = "legacy-v1"
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
