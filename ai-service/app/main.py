import logging
import re
from contextlib import asynccontextmanager
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.core.config import get_settings
from app.core.agent_checkpoint import assistant_agent_runtime
from app.core.observability import configure_langsmith_environment
from app.api.assistant import router as assistant_router
from app.api.documents import router as documents_router
from app.api.agent import router as agent_router
from app.api.health import router as health_router
from app.api.rag import router as rag_router
from app.core.exceptions import ServiceError
from app.schemas.common import ErrorResponse

logger = logging.getLogger(__name__)
TRACE_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    configure_langsmith_environment(settings)
    async with assistant_agent_runtime(settings) as assistant_agent_service:
        app.state.assistant_agent_service = assistant_agent_service
        try:
            yield
        finally:
            app.state.assistant_agent_service = None

app = FastAPI(
    title="OfficeCare AI Service",
    description="Internal document processing, RAG and LangChain agent service for OfficeCare.",
    version="0.4.0",
    lifespan=lifespan,
)


def _trace_id(request: Request) -> str:
    return getattr(request.state, "trace_id", str(uuid4()))


def _error_response(
        request: Request,
        *,
        status_code: int,
        code: str,
        message: str,
        details: object | None = None,
) -> JSONResponse:
    body = ErrorResponse(
        code=code,
        message=message,
        trace_id=_trace_id(request),
        details=details,
    )
    return JSONResponse(
        status_code=status_code,
        content=jsonable_encoder(body.model_dump(by_alias=True)),
    )


@app.middleware("http")
async def attach_trace_id(request: Request, call_next):
    supplied_trace_id = request.headers.get("X-Trace-Id", "")
    request.state.trace_id = (
        supplied_trace_id
        if TRACE_ID_PATTERN.fullmatch(supplied_trace_id)
        else str(uuid4())
    )
    response = await call_next(request)
    response.headers["X-Trace-Id"] = request.state.trace_id
    return response


@app.exception_handler(ServiceError)
async def handle_service_error(
        request: Request,
        exc: ServiceError,
) -> JSONResponse:
    return _error_response(
        request,
        status_code=exc.status_code,
        code=exc.code,
        message=exc.message,
        details=exc.details,
    )


@app.exception_handler(RequestValidationError)
async def handle_validation_error(
        request: Request,
        exc: RequestValidationError,
) -> JSONResponse:
    return _error_response(
        request,
        status_code=422,
        code="REQUEST_VALIDATION_ERROR",
        message="The request parameters are invalid.",
        details={"errors": exc.errors()},
    )


@app.exception_handler(Exception)
async def handle_unexpected_error(
        request: Request,
        exc: Exception,
) -> JSONResponse:
    logger.exception("Unhandled request error. trace_id=%s", _trace_id(request))
    return _error_response(
        request,
        status_code=500,
        code="INTERNAL_ERROR",
        message="An unexpected internal error occurred.",
    )


app.include_router(health_router)
app.include_router(documents_router)
app.include_router(rag_router)
app.include_router(agent_router)
app.include_router(assistant_router)
