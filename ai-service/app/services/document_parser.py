import hashlib
from pathlib import PurePosixPath
from typing import Callable

from app.core.config import Settings, get_settings
from app.core.exceptions import ServiceError
from app.loaders.base import ParsedContent
from app.loaders.docx_loader import load_docx
from app.loaders.pdf_loader import load_pdf
from app.loaders.txt_loader import load_txt
from app.schemas.document import DocumentParseResponse, SupportedFileType
from app.services.mineru_client import MinerUClient


Loader = Callable[[bytes], ParsedContent]

SUPPORTED_FORMATS: dict[str, tuple[SupportedFileType, str, Loader]] = {
    ".pdf": (SupportedFileType.PDF, "application/pdf", load_pdf),
    ".docx": (
        SupportedFileType.DOCX,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        load_docx,
    ),
    ".txt": (SupportedFileType.TXT, "text/plain", load_txt),
}


def normalize_file_name(file_name: str | None) -> str:
    if file_name is None:
        raise ServiceError(
            status_code=422,
            code="INVALID_FILE_NAME",
            message="A file name is required.",
        )

    normalized = PurePosixPath(file_name.replace("\\", "/")).name.strip()
    if not normalized or normalized in {".", ".."} or len(normalized) > 255:
        raise ServiceError(
            status_code=422,
            code="INVALID_FILE_NAME",
            message="The file name is empty or exceeds 255 characters.",
        )
    return normalized


def resolve_format(
    file_name: str,
) -> tuple[SupportedFileType, str, Loader]:
    suffix = PurePosixPath(file_name).suffix.lower()
    document_format = SUPPORTED_FORMATS.get(suffix)
    if document_format is None:
        raise ServiceError(
            status_code=415,
            code="UNSUPPORTED_FILE_TYPE",
            message="Only PDF, DOCX, and TXT files are supported.",
            details={"supportedExtensions": list(SUPPORTED_FORMATS)},
        )
    return document_format


def parse_document(
    *,
    file_name: str,
    content: bytes,
    settings: Settings | None = None,
    mineru_client: MinerUClient | None = None,
) -> DocumentParseResponse:
    file_type, content_type, loader = resolve_format(file_name)
    file_hash = hashlib.sha256(content).hexdigest()
    active_settings = settings or get_settings()
    if (
        active_settings.document_parser == "mineru"
        and file_type in {SupportedFileType.PDF, SupportedFileType.DOCX}
    ):
        client = mineru_client or MinerUClient(active_settings)
        parsed = client.parse(
            file_name=file_name,
            file_type=file_type,
            content=content,
            file_hash=file_hash,
        )
    else:
        parsed = loader(content)

    return DocumentParseResponse(
        file_name=file_name,
        file_type=file_type,
        content_type=content_type,
        file_size=len(content),
        file_hash=file_hash,
        page_count=parsed.page_count,
        block_count=len(parsed.blocks),
        character_count=sum(len(block.text) for block in parsed.blocks),
        blocks=parsed.blocks,
        warnings=parsed.warnings,
    )
