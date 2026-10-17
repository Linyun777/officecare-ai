from typing import Annotated

from fastapi import APIRouter, Depends, Request
from starlette.concurrency import run_in_threadpool

from app.core.security import verify_internal_token
from app.core.observability import observe_operation
from app.schemas.agent import (
    TroubleshootingAgentRequest,
    TroubleshootingAgentResponse,
)
from app.schemas.common import ErrorResponse
from app.services.troubleshooting_agent import (
    LangChainTroubleshootingAgent,
    get_troubleshooting_agent_service,
)


router = APIRouter(
    prefix="/api/v1/internal/agent",
    tags=["Internal Agent"],
)


@router.post(
    "/troubleshoot",
    response_model=TroubleshootingAgentResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def troubleshoot_device_issue(
    http_request: Request,
    request: TroubleshootingAgentRequest,
    service: Annotated[
        LangChainTroubleshootingAgent,
        Depends(get_troubleshooting_agent_service),
    ],
) -> TroubleshootingAgentResponse:
    with observe_operation(
        "officecare.agent.troubleshoot",
        trace_id=str(getattr(http_request.state, "trace_id", "")),
        tags=("agent", "troubleshooting"),
        metadata={
            "endpoint": "troubleshoot",
            "knowledge_base_id": request.knowledge_base_id,
            "brand_id": request.device.brand_id,
            "model_id": request.device.model_id,
            "document_version_count": len(request.document_version_ids),
            "top_k": request.top_k,
            "score_threshold": request.score_threshold,
        },
    ) as trace:
        response = await run_in_threadpool(service.run, request)
        trace.set_result(
            answered=response.answered,
            citation_count=len(response.citations),
            refusal_reason=response.refusal_reason,
            requires_on_site_service=response.requires_on_site_service,
            tool_count=len(response.tool_trace),
            model_name=response.model_name,
        )
        return response
