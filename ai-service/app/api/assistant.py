from typing import Annotated

from fastapi import APIRouter, Depends, Request, Response, status

from app.core.exceptions import ServiceError
from app.core.observability import observe_operation
from app.core.security import verify_internal_token
from app.schemas.assistant_agent import AssistantTurnRequest, AssistantTurnResponse
from app.schemas.common import ErrorResponse
from app.services.assistant_agent import LangChainAssistantAgent


router = APIRouter(
    prefix="/api/v1/internal/assistant",
    tags=["Internal Assistant Agent"],
)


def get_assistant_agent_service(request: Request) -> LangChainAssistantAgent:
    service = getattr(request.app.state, "assistant_agent_service", None)
    if service is None:
        raise ServiceError(
            status_code=503,
            code="ASSISTANT_MEMORY_UNAVAILABLE",
            message=(
                "The Assistant Agent PostgreSQL checkpoint service is disabled "
                "or unavailable."
            ),
        )
    return service


@router.post(
    "/turn",
    response_model=AssistantTurnResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        409: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def run_assistant_turn(
    http_request: Request,
    request: AssistantTurnRequest,
    service: Annotated[
        LangChainAssistantAgent,
        Depends(get_assistant_agent_service),
    ],
) -> AssistantTurnResponse:
    with observe_operation(
        "officecare.assistant.turn",
        trace_id=str(getattr(http_request.state, "trace_id", "")),
        tags=("assistant", "agent", "turn"),
        metadata={
            "endpoint": "assistant-turn",
            "conversation_id": request.conversation_id,
            "user_id": request.user_id,
            "knowledge_base_id": request.knowledge_base_id,
            "brand_id": request.brand_id,
            "model_id": request.model_id,
            "document_version_count": len(request.document_version_ids),
            "bootstrap_message_count": len(request.bootstrap_messages),
        },
        inputs={
            "conversationId": request.conversation_id,
            "userId": request.user_id,
            "turnId": str(request.turn_id),
            "question": request.question,
            "knowledgeBaseId": request.knowledge_base_id,
            "brandId": request.brand_id,
            "modelId": request.model_id,
        },
    ) as trace:
        response = await service.run_turn(request)
        trace.set_result(
            response_type=response.response_type.value,
            diagnostic_status=response.diagnostic_status.value,
            answered=response.answered,
            citation_count=len(response.citations),
            retrieved_count=response.retrieved_count,
            refusal_reason=response.refusal_reason,
            requires_user_reply=response.requires_user_reply,
            requires_on_site_service=response.requires_on_site_service,
            model_name=response.model_name,
        )
        trace.set_outputs(**response.model_dump(mode="json", by_alias=True))
        return response


@router.delete(
    "/threads/{user_id}/{conversation_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def delete_assistant_thread(
    user_id: int,
    conversation_id: int,
    service: Annotated[
        LangChainAssistantAgent,
        Depends(get_assistant_agent_service),
    ],
) -> Response:
    if user_id <= 0 or conversation_id <= 0:
        raise ServiceError(
            status_code=422,
            code="REQUEST_VALIDATION_ERROR",
            message="The user and conversation identifiers must be positive.",
        )
    await service.delete_thread(user_id, conversation_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
