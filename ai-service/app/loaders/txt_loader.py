import re

from app.core.exceptions import ServiceError
from app.loaders.base import ParsedContent
from app.schemas.document import BlockType, DocumentBlock


def _decode_text(content: bytes) -> tuple[str, list[str]]:
    try:
        return content.decode("utf-8-sig"), []
    except UnicodeDecodeError:
        try:
            return content.decode("gb18030"), [
                "The TXT file was decoded using GB18030 instead of UTF-8."
            ]
        except UnicodeDecodeError as exc:
            raise ServiceError(
                status_code=422,
                code="TEXT_ENCODING_UNSUPPORTED",
                message="The TXT file must use UTF-8 or GB18030 encoding.",
            ) from exc


def load_txt(content: bytes) -> ParsedContent:
    text, warnings = _decode_text(content)
    paragraphs = [
        paragraph.strip()
        for paragraph in re.split(r"(?:\r?\n)[ \t]*(?:\r?\n)+", text)
        if paragraph.strip()
    ]

    if not paragraphs:
        raise ServiceError(
            status_code=422,
            code="NO_TEXT_EXTRACTED",
            message="No extractable text was found in the TXT document.",
        )

    blocks = [
        DocumentBlock(
            sequence=index,
            page_number=None,
            block_type=BlockType.PARAGRAPH,
            text=paragraph,
        )
        for index, paragraph in enumerate(paragraphs, start=1)
    ]
    return ParsedContent(blocks=blocks, warnings=warnings)

