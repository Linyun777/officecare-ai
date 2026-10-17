from io import BytesIO

from pypdf import PdfReader
from pypdf.errors import PdfReadError

from app.core.exceptions import ServiceError
from app.loaders.base import ParsedContent
from app.schemas.document import BlockType, DocumentBlock


def load_pdf(content: bytes) -> ParsedContent:
    if not content.startswith(b"%PDF-"):
        raise ServiceError(
            status_code=422,
            code="INVALID_PDF",
            message="The uploaded file is not a valid PDF document.",
        )

    try:
        reader = PdfReader(BytesIO(content), strict=False)
        if reader.is_encrypted:
            try:
                # Some manuals only restrict editing/copying and still use an empty
                # user password. Those PDFs are readable and should be parsed.
                unlocked = bool(reader.decrypt(""))
            except Exception:
                unlocked = False
            if not unlocked:
                raise ServiceError(
                    status_code=422,
                    code="PDF_PASSWORD_REQUIRED",
                    message="Password-protected PDF documents are not supported.",
                )

        blocks: list[DocumentBlock] = []
        for page_number, page in enumerate(reader.pages, start=1):
            text = (page.extract_text() or "").strip()
            if text:
                blocks.append(
                    DocumentBlock(
                        sequence=len(blocks) + 1,
                        page_number=page_number,
                        block_type=BlockType.PAGE,
                        text=text,
                    )
                )
    except ServiceError:
        raise
    except (PdfReadError, EOFError, ValueError, TypeError, KeyError) as exc:
        raise ServiceError(
            status_code=422,
            code="INVALID_PDF",
            message="The PDF document is damaged or cannot be parsed.",
        ) from exc
    except Exception as exc:
        raise ServiceError(
            status_code=422,
            code="INVALID_PDF",
            message="The PDF document is damaged or cannot be parsed.",
        ) from exc

    if not blocks:
        raise ServiceError(
            status_code=422,
            code="NO_TEXT_EXTRACTED",
            message=(
                "No extractable text was found. Scanned PDFs require OCR, "
                "which is not supported in this version."
            ),
        )

    return ParsedContent(blocks=blocks, page_count=len(reader.pages))
