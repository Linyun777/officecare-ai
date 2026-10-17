from typing import Annotated

from fastapi import APIRouter, Depends, Query, Request
from starlette.concurrency import run_in_threadpool

from app.core.security import verify_internal_token
from app.core.observability import observe_operation
from app.schemas.common import ErrorResponse
from app.schemas.rag import (
    RagAnswerRequest,
    RagAnswerResponse,
    RagRetrievalResponse,
)
from app.schemas.rag_cache import (
    RagCacheInvalidateRequest,
    RagCacheInvalidateResponse,
    RagCacheStatsResponse,
)
from app.services.rag_answering import (
    RagAnsweringService,
    get_rag_answering_service,
)


router = APIRouter(
    prefix="/api/v1/internal/rag",
    tags=["Internal RAG"],
)


@router.post(
    "/answer",
    response_model=RagAnswerResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def answer_with_officecare_knowledge(
    http_request: Request,
    request: RagAnswerRequest,
    service: Annotated[
        RagAnsweringService,
        Depends(get_rag_answering_service),
    ],
) -> RagAnswerResponse:
    with _observe_rag_request(
        "officecare.rag.answer",
        http_request,
        request,
        endpoint="answer",
    ) as trace:
        response = await run_in_threadpool(service.answer, request)
        _record_answer_result(trace, response)
        return response


@router.post(
    "/retrieve",
    response_model=RagRetrievalResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def retrieve_officecare_knowledge(
    http_request: Request,
    request: RagAnswerRequest,
    service: Annotated[
        RagAnsweringService,
        Depends(get_rag_answering_service),
    ],
) -> RagRetrievalResponse:
    with _observe_rag_request(
        "officecare.rag.retrieve",
        http_request,
        request,
        endpoint="retrieve",
    ) as trace:
        response = await run_in_threadpool(service.retrieve, request)
        trace.set_result(
            retrieved_count=response.retrieved_count,
            refusal_reason=response.blocked_reason,
        )
        trace.set_outputs(
            **response.model_dump(mode="json", by_alias=True),
        )
        return response


@router.post(
    "/evaluate/answer",
    response_model=RagAnswerResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def evaluate_answer_without_cache(
    http_request: Request,
    request: RagAnswerRequest,
    service: Annotated[
        RagAnsweringService,
        Depends(get_rag_answering_service),
    ],
) -> RagAnswerResponse:
    with _observe_rag_request(
        "officecare.rag.evaluate_answer",
        http_request,
        request,
        endpoint="evaluate-answer",
    ) as trace:
        response = await run_in_threadpool(service.answer_without_cache, request)
        _record_answer_result(trace, response)
        return response


@router.get(
    "/cache/stats",
    response_model=RagCacheStatsResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={401: {"model": ErrorResponse}},
)
async def get_rag_cache_stats(
    service: Annotated[
        RagAnsweringService,
        Depends(get_rag_answering_service),
    ],
    knowledge_base_id: Annotated[
        int | None,
        Query(gt=0, alias="knowledgeBaseId"),
    ] = None,
) -> RagCacheStatsResponse:
    return await run_in_threadpool(service.cache_stats, knowledge_base_id)


@router.post(
    "/cache/invalidate",
    response_model=RagCacheInvalidateResponse,
    dependencies=[Depends(verify_internal_token)],
    responses={
        401: {"model": ErrorResponse},
        409: {"model": ErrorResponse},
        422: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def invalidate_rag_cache(
    request: RagCacheInvalidateRequest,
    service: Annotated[
        RagAnsweringService,
        Depends(get_rag_answering_service),
    ],
) -> RagCacheInvalidateResponse:
    return await run_in_threadpool(service.invalidate_cache, request)


def _observe_rag_request(
    name: str,
    http_request: Request,
    request: RagAnswerRequest,
    *,
    endpoint: str,
):
    return observe_operation(
        name,
        trace_id=str(getattr(http_request.state, "trace_id", "")),
        tags=("rag", endpoint),
        metadata={
            "endpoint": endpoint,
            "knowledge_base_id": request.knowledge_base_id,
            "brand_id": request.brand_id,
            "model_id": request.model_id,
            "document_version_count": len(request.document_version_ids),
            "top_k": request.top_k,
            "score_threshold": request.score_threshold,
        },
        inputs={
            "question": request.question,
            "knowledgeBaseId": request.knowledge_base_id,
            "brandId": request.brand_id,
            "modelId": request.model_id,
            "documentVersionIds": request.document_version_ids,
            "topK": request.top_k,
            "scoreThreshold": request.score_threshold,
        },
    )


def _record_answer_result(trace, response: RagAnswerResponse) -> None:
    trace.set_result(
        answered=response.answered,
        retrieved_count=response.retrieved_count,
        citation_count=len(response.citations),
        refusal_reason=response.refusal_reason,
        model_name=response.model_name,
    )
    trace.set_outputs(
        **response.model_dump(mode="json", by_alias=True),
    )
