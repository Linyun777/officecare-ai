from io import BytesIO
from zipfile import BadZipFile

from docx import Document
from docx.opc.exceptions import PackageNotFoundError
from docx.table import Table
from docx.text.paragraph import Paragraph

from app.core.exceptions import ServiceError
from app.loaders.base import ParsedContent
from app.schemas.document import BlockType, DocumentBlock
from app.services.table_structure import docx_table


def _table_text(table: Table) -> str:
    rows: list[str] = []
    for row in table.rows:
        cells = [" ".join(cell.text.split()) for cell in row.cells]
        if any(cells):
            rows.append("\t".join(cells))
    return "\n".join(rows)


def load_docx(content: bytes) -> ParsedContent:
    try:
        document = Document(BytesIO(content))
        blocks: list[DocumentBlock] = []

        for item in document.iter_inner_content():
            heading_level = None
            table_structure = None
            if isinstance(item, Paragraph):
                text = item.text.strip()
                if not text:
                    continue
                style_name = item.style.name if item.style is not None else ""
                block_type = (
                    BlockType.HEADING
                    if style_name.lower().startswith("heading")
                    else BlockType.PARAGRAPH
                )
                if block_type == BlockType.HEADING:
                    level = style_name.lower().removeprefix("heading").strip()
                    heading_level = int(level) if level.isdigit() and int(level) > 0 else None
            elif isinstance(item, Table):
                text = _table_text(item).strip()
                if not text:
                    continue
                block_type = BlockType.TABLE
                table_structure = docx_table(item)
            else:
                continue

            blocks.append(
                DocumentBlock(
                    sequence=len(blocks) + 1,
                    page_number=None,
                    block_type=block_type,
                    text=text,
                    heading_level=heading_level,
                    source_item_index=len(blocks),
                    table=table_structure,
                )
            )
    except (PackageNotFoundError, BadZipFile, KeyError, ValueError) as exc:
        raise ServiceError(
            status_code=422,
            code="INVALID_DOCX",
            message="The DOCX document is damaged or cannot be parsed.",
        ) from exc
    except Exception as exc:
        raise ServiceError(
            status_code=422,
            code="INVALID_DOCX",
            message="The DOCX document is damaged or cannot be parsed.",
        ) from exc

    if not blocks:
        raise ServiceError(
            status_code=422,
            code="NO_TEXT_EXTRACTED",
            message="No extractable text was found in the DOCX document.",
        )

    return ParsedContent(blocks=blocks)

