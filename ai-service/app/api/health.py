from fastapi import APIRouter

from app.core.config import get_settings
from app.schemas.document import HealthResponse, SupportedFileType


router = APIRouter(tags=["Health"])


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    settings = get_settings()
    return HealthResponse(
        status="ok",
        service="officecare-ai-service",
        version="0.1.0",
        environment=settings.environment,
        supported_file_types=list(SupportedFileType),
    )

