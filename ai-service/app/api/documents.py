from typing import Annotated

from fastapi import APIRouter, Depends, File, UploadFile
from starlette.concurrency import run_in_threadpool

from app.core.config import get_settings
from app.core.exceptions import ServiceError
from app.core.security import verify_internal_token
from app.schemas.common import ErrorResponse
from app.schemas.document import DocumentParseResponse
from app.schemas.indexing import (
    DocumentIndexDeleteResponse,
    DocumentIndexRequest,
    DocumentIndexResponse,
)
from app.services.document_parser import (
    normalize_file_name,
    parse_document,
    resolve_format,
)
from app.services.document_indexing import (
    DocumentIndexingService,
    get_document_indexing_service,
)


router = APIRouter(
    prefix="/api/v1/internal/documents",
    tags=["Internal document parsing"],
)


@router.post(
    "/parse",
    response_model=DocumentParseResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        413: {"model": ErrorResponse},
        415: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
        504: {"model": ErrorResponse},
    },
)
async def parse_uploaded_document(
    file: Annotated[UploadFile, File(description="A PDF, DOCX, or TXT document.")],
) -> DocumentParseResponse:
    settings = get_settings()

    try:
        file_name = normalize_file_name(file.filename)
        resolve_format(file_name)
        content = await file.read(settings.max_document_size_bytes + 1)
    finally:
        await file.close()

    if not content:
        raise ServiceError(
            status_code=422,
            code="FILE_EMPTY",
            message="The uploaded file is empty.",
        )
    if len(content) > settings.max_document_size_bytes:
        raise ServiceError(
            status_code=413,
            code="FILE_TOO_LARGE",
            message=(
                "The uploaded file exceeds the configured "
                f"{settings.max_document_size_mb} MB limit."
            ),
            details={"maxSizeBytes": settings.max_document_size_bytes},
        )

    return await run_in_threadpool(
        parse_document,
        file_name=file_name,
        content=content,
        settings=settings,
    )


@router.post(
    "/index",
    response_model=DocumentIndexResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def index_parsed_document(
    request: DocumentIndexRequest,
    service: Annotated[
        DocumentIndexingService,
        Depends(get_document_indexing_service),
    ],
) -> DocumentIndexResponse:
    return await run_in_threadpool(service.index_document, request)


@router.delete(
    "/index/{document_version_id}",
    response_model=DocumentIndexDeleteResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def delete_document_version_index(
    document_version_id: int,
    service: Annotated[
        DocumentIndexingService,
        Depends(get_document_indexing_service),
    ],
) -> DocumentIndexDeleteResponse:
    if document_version_id <= 0:
        raise ServiceError(
            status_code=422,
            code="INVALID_DOCUMENT_VERSION_ID",
            message="documentVersionId must be greater than zero.",
        )
    return await run_in_threadpool(
        service.delete_document_version,
        document_version_id,
    )
