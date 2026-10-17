import asyncio
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from typing import Protocol
import weakref

from langchain.agents import create_agent
from langchain.agents.middleware import SummarizationMiddleware
from langchain.agents.structured_output import StructuredOutputError, ToolStrategy
from langchain.tools import ToolRuntime
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.tools import BaseTool, StructuredTool
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.errors import GraphRecursionError
from starlette.concurrency import run_in_threadpool

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.schemas.assistant_agent import (
    AssistantAgentDecision,
    AssistantAgentState,
    AssistantDiagnosticStatus,
    AssistantKnowledgeSearchInput,
    AssistantResponseType,
    AssistantTurnRequest,
    AssistantTurnResponse,
)
from app.schemas.rag import RagAnswerRequest, RagAnswerResponse
from app.services.rag_answering import REFUSAL_REASON, REFUSAL_TEXT


KNOWLEDGE_TOOL = "search_product_knowledge"
GROUNDED_RESPONSE_TYPES = {
    AssistantResponseType.ANSWER,
    AssistantResponseType.DIAGNOSTIC_STEP,
    AssistantResponseType.RESOLUTION,
}

ASSISTANT_SYSTEM_PROMPT = """你是 OfficeCare 内部售后 AI 助手，服务对象是经过授权的售后人员。
你既能回答当前产品型号的知识问题，也能通过多轮对话逐步排查设备故障。

必须遵守以下规则：
1. 当前会话已经由 Java 绑定到一个品牌、型号和知识库，不得跨型号回答。
2. 需要回答产品事实、故障原因或具体操作步骤时，必须调用 search_product_knowledge。
3. 信息不足时，只提出一个最有价值的澄清问题，responseType 使用 CLARIFICATION。
4. 故障排查每轮最多给出一个新的、能够由用户验证的步骤，responseType 使用 DIAGNOSTIC_STEP。
5. 根据后续用户反馈，更新 attemptedSteps 和 observations；不得声称用户做过其未确认的操作。
6. 知识工具 answered=false 时，不得提供具体拆机、按键、参数或维修步骤。
7. 涉及带电、高温、冒烟、漏电、主板或不确定安全性的操作时，建议停止操作并交由合格人员处理。
8. 你没有创建、修改、指派或关闭正式工单的工具，不得声称已经创建工单。
9. 引用由系统从知识工具结果中返回，不要编造或修改来源。
10. 最终必须返回 AssistantAgentDecision 结构化结果。

diagnosticStatus 可选值：IDLE、COLLECTING_INFORMATION、AWAITING_STEP_RESULT、RESOLVED、ESCALATE、INSUFFICIENT_EVIDENCE。
"""

ASSISTANT_SUMMARY_PROMPT = """请将以下 OfficeCare 售后对话压缩成可继续诊断的中文摘要。
只保留：用户目标、设备故障现象、错误代码、已确认执行的步骤、每步结果、安全风险、尚待确认的问题。
不要把模型推测写成事实，不要补充对话中不存在的维修步骤。
直接输出摘要正文。

<messages>
{messages}
</messages>
"""


class KnowledgeAnswerer(Protocol):
    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        ...


@dataclass(slots=True)
class _AssistantToolState:
    rag_response: RagAnswerResponse | None = None
    search_queries: list[str] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class _AssistantContext:
    request: AssistantTurnRequest
    tool_state: _AssistantToolState


class LangChainAssistantAgent:
    """Conversational LangChain agent with database-backed thread memory."""

    def __init__(
        self,
        settings: Settings,
        rag_service: KnowledgeAnswerer,
        checkpointer: BaseCheckpointSaver,
        *,
        model: BaseChatModel | None = None,
    ) -> None:
        self._settings = settings
        self._rag_service = rag_service
        self._checkpointer = checkpointer
        self._turn_locks: weakref.WeakValueDictionary[
            str, asyncio.Lock
        ] = weakref.WeakValueDictionary()
        self._turn_locks_guard = asyncio.Lock()
        self._model = model or self._create_model()
        self._response_strategy = ToolStrategy(
            AssistantAgentDecision,
            handle_errors=False,
        )
        self._tools = self._build_tools()
        self._agent = create_agent(
            model=self._model,
            tools=self._tools,
            system_prompt=ASSISTANT_SYSTEM_PROMPT,
            middleware=[
                SummarizationMiddleware(
                    model=self._model,
                    trigger=(
                        "messages",
                        self._settings.agent_summary_trigger_messages,
                    ),
                    keep=(
                        "messages",
                        self._settings.agent_recent_message_limit,
                    ),
                    summary_prompt=ASSISTANT_SUMMARY_PROMPT,
                )
            ],
            response_format=self._response_strategy,
            state_schema=AssistantAgentState,
            context_schema=_AssistantContext,
            checkpointer=checkpointer,
            name="officecare_assistant_agent",
        )

    @property
    def model_name(self) -> str:
        return self._settings.chat_model

    async def run_turn(
        self,
        request: AssistantTurnRequest,
    ) -> AssistantTurnResponse:
        config = self._thread_config(request)
        turn_id = str(request.turn_id)
        async with self._turn_lock(config, turn_id):
            return await self._run_turn_locked(request, config, turn_id)

    @asynccontextmanager
    async def _turn_lock(self, config: dict, turn_id: str):
        key = f"{config['configurable']['thread_id']}:{turn_id}"
        async with self._turn_locks_guard:
            lock = self._turn_locks.get(key)
            if lock is None:
                lock = asyncio.Lock()
                self._turn_locks[key] = lock
        async with lock:
            yield

    async def _run_turn_locked(
        self,
        request: AssistantTurnRequest,
        config: dict,
        turn_id: str,
    ) -> AssistantTurnResponse:
        try:
            snapshot = await self._agent.aget_state(config)
        except Exception as exc:
            raise self._memory_unavailable_error() from exc
        existing = dict(snapshot.values or {})
        self._validate_thread_scope(existing, request)

        cached_response = self._duplicate_turn_response(
            existing,
            turn_id,
            request.question,
        )
        if cached_response is not None:
            return cached_response

        tool_state = _AssistantToolState()
        context = _AssistantContext(request=request, tool_state=tool_state)
        messages = self._input_messages(existing, request)
        agent_input = {
            "messages": messages,
            "conversation_id": request.conversation_id,
            "owner_user_id": request.user_id,
            "knowledge_base_id": request.knowledge_base_id,
            "brand_id": request.brand_id,
            "model_id": request.model_id,
        }

        try:
            result = await self._agent.ainvoke(
                agent_input,
                config=config,
                context=context,
            )
        except GraphRecursionError as exc:
            raise ServiceError(
                status_code=502,
                code="ASSISTANT_STEP_LIMIT_EXCEEDED",
                message="The Assistant Agent exceeded its tool step limit.",
            ) from exc
        except StructuredOutputError as exc:
            raise ServiceError(
                status_code=502,
                code="ASSISTANT_INVALID_MODEL_RESPONSE",
                message="The Assistant Agent returned invalid structured output.",
            ) from exc
        except ServiceError:
            raise
        except Exception as exc:
            raise ServiceError(
                status_code=503,
                code="ASSISTANT_MODEL_UNAVAILABLE",
                message="The Assistant Agent is unavailable or timed out.",
            ) from exc

        try:
            decision = AssistantAgentDecision.model_validate(
                result.get("structured_response")
            )
        except Exception as exc:
            raise ServiceError(
                status_code=502,
                code="ASSISTANT_INVALID_MODEL_RESPONSE",
                message="The Assistant Agent returned invalid structured output.",
            ) from exc

        decision = await self._ensure_current_evidence(
            request,
            decision,
            tool_state,
        )
        response = self._build_response(request, decision, tool_state)
        try:
            await self._agent.aupdate_state(
                config,
                {
                    "diagnostic_status": response.diagnostic_status.value,
                    "issue_summary": response.issue_summary,
                    "attempted_steps": response.attempted_steps,
                    "observations": response.observations,
                    "last_turn_id": turn_id,
                    "last_turn_question": request.question,
                    "last_response": response.model_dump(
                        mode="json",
                        by_alias=True,
                    ),
                },
            )
        except Exception as exc:
            raise self._memory_unavailable_error() from exc
        return response

    async def delete_thread(self, user_id: int, conversation_id: int) -> None:
        thread_id = self.thread_id(user_id, conversation_id)
        try:
            await self._checkpointer.adelete_thread(thread_id)
        except Exception as exc:
            raise self._memory_unavailable_error() from exc

    @staticmethod
    def _memory_unavailable_error() -> ServiceError:
        return ServiceError(
            status_code=503,
            code="ASSISTANT_MEMORY_UNAVAILABLE",
            message="The Assistant Agent checkpoint service is unavailable.",
        )

    @staticmethod
    def thread_id(user_id: int, conversation_id: int) -> str:
        return f"officecare:assistant:{user_id}:{conversation_id}"

    def _thread_config(self, request: AssistantTurnRequest) -> dict:
        return {
            "configurable": {
                "thread_id": self.thread_id(
                    request.user_id,
                    request.conversation_id,
                )
            },
            "recursion_limit": self._settings.agent_max_steps * 2 + 2,
        }

    def _input_messages(
        self,
        existing: dict,
        request: AssistantTurnRequest,
    ) -> list[HumanMessage | AIMessage]:
        messages: list[HumanMessage | AIMessage] = []
        if not existing.get("messages"):
            for item in request.bootstrap_messages:
                if item.role == "user":
                    messages.append(HumanMessage(content=item.content))
                else:
                    messages.append(AIMessage(content=item.content))
        messages.append(HumanMessage(content=request.question))
        return messages

    def _validate_thread_scope(
        self,
        existing: dict,
        request: AssistantTurnRequest,
    ) -> None:
        if not existing:
            return
        expected = {
            "conversation_id": request.conversation_id,
            "owner_user_id": request.user_id,
            "knowledge_base_id": request.knowledge_base_id,
            "brand_id": request.brand_id,
            "model_id": request.model_id,
        }
        mismatched = [
            name
            for name, value in expected.items()
            if existing.get(name) is not None and existing.get(name) != value
        ]
        if mismatched:
            raise ServiceError(
                status_code=409,
                code="ASSISTANT_THREAD_SCOPE_MISMATCH",
                message="The Assistant Agent thread scope does not match the request.",
                details={"fields": mismatched},
            )

    def _duplicate_turn_response(
        self,
        existing: dict,
        turn_id: str,
        question: str,
    ) -> AssistantTurnResponse | None:
        if existing.get("last_turn_id") != turn_id:
            return None
        existing_question = existing.get("last_turn_question")
        if existing_question is not None and existing_question != question:
            raise ServiceError(
                status_code=409,
                code="ASSISTANT_TURN_ID_CONFLICT",
                message="The Assistant Agent turn ID was used for another question.",
            )
        cached = existing.get("last_response")
        if not isinstance(cached, dict):
            return None
        try:
            return AssistantTurnResponse.model_validate(cached)
        except Exception:
            return None

    def _build_tools(self) -> list[BaseTool]:
        async def search_product_knowledge(
            query: str,
            runtime: ToolRuntime[_AssistantContext],
        ) -> str:
            """Search the current Java-authorized product knowledge scope."""
            context = runtime.context
            request = context.request
            rag_request = self._rag_request(request, query)
            response = await run_in_threadpool(
                self._rag_service.answer,
                rag_request,
            )
            context.tool_state.rag_response = response
            context.tool_state.search_queries.append(query)
            return response.model_dump_json(by_alias=True)

        return [
            StructuredTool.from_function(
                coroutine=search_product_knowledge,
                name=KNOWLEDGE_TOOL,
                description=(
                    "在当前 Java 已授权的品牌、型号、知识库和文档版本范围内"
                    "检索产品事实与故障排查证据。"
                ),
                args_schema=AssistantKnowledgeSearchInput,
            )
        ]

    async def _ensure_current_evidence(
        self,
        request: AssistantTurnRequest,
        decision: AssistantAgentDecision,
        tool_state: _AssistantToolState,
    ) -> AssistantAgentDecision:
        """Ground a factual response even when the model skips the search tool.

        Conversation memory can contain an answer backed by a document that was
        deleted after an earlier turn. The model may reuse that answer without
        calling the tool again, so enforce a fresh RAG lookup against the current
        Java-authorized document version list before returning it.
        """
        if (
            decision.response_type not in GROUNDED_RESPONSE_TYPES
            or tool_state.rag_response is not None
        ):
            return decision

        rag_response = await run_in_threadpool(
            self._rag_service.answer,
            self._rag_request(request, request.question),
        )
        tool_state.rag_response = rag_response
        tool_state.search_queries.append(request.question)
        if not rag_response.answered:
            return decision
        return decision.model_copy(update={"answer": rag_response.answer})

    @staticmethod
    def _rag_request(
        request: AssistantTurnRequest,
        question: str,
    ) -> RagAnswerRequest:
        return RagAnswerRequest(
            question=question,
            knowledge_base_id=request.knowledge_base_id,
            brand_id=request.brand_id,
            model_id=request.model_id,
            brand_name=request.brand_name,
            model_name=request.model_name,
            document_version_ids=request.document_version_ids,
            top_k=request.top_k,
            score_threshold=request.score_threshold,
        )

    def _build_response(
        self,
        request: AssistantTurnRequest,
        decision: AssistantAgentDecision,
        tool_state: _AssistantToolState,
    ) -> AssistantTurnResponse:
        rag = tool_state.rag_response
        if rag is not None and not rag.answered:
            return AssistantTurnResponse(
                response_type=AssistantResponseType.REFUSAL,
                diagnostic_status=(
                    AssistantDiagnosticStatus.INSUFFICIENT_EVIDENCE
                ),
                answer=REFUSAL_TEXT,
                issue_summary=decision.issue_summary,
                attempted_steps=decision.attempted_steps,
                observations=decision.observations,
                next_question=None,
                requires_user_reply=False,
                requires_on_site_service=True,
                answered=False,
                refusal_reason=rag.refusal_reason or REFUSAL_REASON,
                citations=[],
                retrieved_count=rag.retrieved_count,
                score_threshold=rag.score_threshold,
                model_name=rag.model_name or self.model_name,
            )

        if decision.response_type in GROUNDED_RESPONSE_TYPES and rag is None:
            raise ServiceError(
                status_code=502,
                code="ASSISTANT_EVIDENCE_REQUIRED",
                message=(
                    "The Assistant Agent attempted a grounded response without "
                    "searching product knowledge."
                ),
            )

        answered = bool(rag and rag.answered)
        return AssistantTurnResponse(
            **decision.model_dump(),
            answered=answered,
            refusal_reason=(
                REFUSAL_REASON
                if decision.response_type == AssistantResponseType.REFUSAL
                else None
            ),
            citations=rag.citations if answered and rag else [],
            retrieved_count=rag.retrieved_count if rag else 0,
            score_threshold=rag.score_threshold if rag else request.score_threshold,
            model_name=(rag.model_name if rag else None) or self.model_name,
        )

    def _create_model(self) -> ChatOpenAI:
        api_key = self._settings.chat_key()
        if (
            not self._settings.chat_base_url
            or not self._settings.chat_model
            or not api_key
        ):
            raise ServiceError(
                status_code=503,
                code="ASSISTANT_MODEL_NOT_CONFIGURED",
                message="The chat API URL, key, or model is not configured.",
            )
        return ChatOpenAI(
            api_key=api_key,
            base_url=self._settings.chat_base_url,
            model=self._settings.chat_model,
            temperature=0,
            timeout=self._settings.chat_timeout_seconds,
            max_completion_tokens=self._settings.chat_max_tokens,
            max_retries=1,
            extra_body={"thinking": {"type": "disabled"}},
        )
