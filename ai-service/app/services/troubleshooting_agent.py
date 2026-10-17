from dataclasses import dataclass, field
from datetime import date
from functools import lru_cache
import json
from typing import Protocol

from langchain.agents import create_agent
from langchain.agents.middleware import ModelRequest, wrap_model_call
from langchain.agents.structured_output import StructuredOutputError, ToolStrategy
from langchain.tools import ToolRuntime
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.tools import BaseTool, StructuredTool
from langchain_openai import ChatOpenAI
from langgraph.errors import GraphRecursionError

from app.core.config import Settings, get_settings
from app.core.exceptions import ServiceError
from app.schemas.agent import (
    AgentAnalysis,
    AgentToolTrace,
    AgentWorkOrderDraft,
    DraftToolInput,
    TroubleshootingAgentRequest,
    TroubleshootingAgentResponse,
)
from app.schemas.rag import RagAnswerRequest, RagAnswerResponse
from app.services.rag_answering import (
    REFUSAL_REASON,
    REFUSAL_TEXT,
    RagAnsweringService,
    get_rag_answering_service,
)


DEVICE_TOOL = "inspect_device_context"
KNOWLEDGE_TOOL = "search_product_knowledge"
DRAFT_TOOL = "build_work_order_draft"
REQUIRED_TOOLS = (DEVICE_TOOL, KNOWLEDGE_TOOL, DRAFT_TOOL)

AGENT_SYSTEM_PROMPT = """你是OfficeCare内部售后报修Agent，只处理打印机售后故障。
你必须通过工具工作，不能假装已经查询设备或知识库：
1. 调用inspect_device_context确认Java提供的设备快照与保修状态；
2. 调用search_product_knowledge检索该设备型号已授权的知识库版本；
3. 调用build_work_order_draft生成可由用户编辑确认的工单草稿；
4. 最后通过系统提供的AgentAnalysis结构化响应返回分析结果。

知识库工具返回answered=false时，不得提供具体维修、拆机、按键或参数步骤；只能说明证据不足、
给出通用安全提醒，并建议人工核实。不得改变工具返回的引用，不得声称已经创建工单。
FastAPI在本阶段没有创建、修改、指派或关闭正式工单的工具，所有草稿必须由用户在Java系统确认。
涉及带电、高温、拆机或无法确认安全性的操作时，必须建议停止操作并交由合格人员处理。

最终AgentAnalysis结构：
{
  "summary": "故障摘要",
  "troubleshootingSteps": ["只包含有知识库证据支持的步骤"],
  "safetyWarnings": ["安全提示"],
  "requiresOnSiteService": false
}
"""


class KnowledgeAnswerer(Protocol):
    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        ...


@dataclass(slots=True)
class _ToolState:
    rag_response: RagAnswerResponse | None = None
    draft: AgentWorkOrderDraft | None = None
    trace: list[AgentToolTrace] = field(default_factory=list)

    def mark(self, tool_name: str) -> None:
        self.trace.append(
            AgentToolTrace(
                sequence=len(self.trace) + 1,
                tool_name=tool_name,
                success=True,
            )
        )


@dataclass(frozen=True, slots=True)
class _AgentContext:
    request: TroubleshootingAgentRequest
    state: _ToolState


class LangChainTroubleshootingAgent:
    """A bounded create_agent workflow with OfficeCare business guardrails."""

    def __init__(
        self,
        settings: Settings,
        rag_service: KnowledgeAnswerer,
        *,
        model: BaseChatModel | None = None,
    ) -> None:
        self._settings = settings
        self._rag_service = rag_service
        self._model = model
        self._response_strategy = ToolStrategy(
            AgentAnalysis,
            handle_errors=False,
        )
        self._structured_tool_names = {
            item.name for item in self._response_strategy.schema_specs
        }
        self._tools = self._build_tools()
        self._agent = None

    @property
    def model_name(self) -> str:
        return self._settings.chat_model

    def run(self, request: TroubleshootingAgentRequest) -> TroubleshootingAgentResponse:
        state = _ToolState()
        context = _AgentContext(request=request, state=state)
        agent = self._get_agent()

        try:
            result = agent.invoke(
                {
                    "messages": [
                        HumanMessage(content=self._user_message(request))
                    ]
                },
                config={"recursion_limit": self._recursion_limit()},
                context=context,
            )
        except GraphRecursionError as exc:
            raise ServiceError(
                status_code=502,
                code="AGENT_STEP_LIMIT_EXCEEDED",
                message="The troubleshooting agent exceeded its tool step limit.",
            ) from exc
        except StructuredOutputError:
            self._validate_required_tools(state)
            return self._response(
                request,
                state,
                self._fallback_analysis(request, state),
            )
        except ServiceError:
            raise
        except Exception as exc:
            raise ServiceError(
                status_code=502,
                code="AGENT_INVALID_MODEL_RESPONSE",
                message="The agent runtime returned an unsupported response.",
            ) from exc

        self._validate_required_tools(state)
        structured = result.get("structured_response")
        try:
            analysis = AgentAnalysis.model_validate(structured)
        except Exception:
            analysis = self._fallback_analysis(request, state)
        return self._response(request, state, analysis)

    def _get_agent(self):
        if self._agent is None:
            model = self._model or self._create_model()
            self._agent = create_agent(
                model=model,
                tools=self._tools,
                system_prompt=AGENT_SYSTEM_PROMPT,
                middleware=[self._stage_middleware()],
                response_format=self._response_strategy,
                context_schema=_AgentContext,
                name="officecare_troubleshooting_agent",
            )
        return self._agent

    def _stage_middleware(self):
        tool_lookup = {tool.name: tool for tool in self._tools}

        @wrap_model_call(name="OfficeCareAgentStageGuard")
        def enforce_stage(request: ModelRequest, handler):
            context = request.runtime.context
            if not isinstance(context, _AgentContext):
                raise ServiceError(
                    status_code=502,
                    code="AGENT_CONTEXT_INVALID",
                    message="The agent runtime context is missing or invalid.",
                )
            called = [item.tool_name for item in context.state.trace]
            next_tool = self._next_required_tool(called)
            if next_tool is None:
                guarded_request = request.override(
                    tools=[],
                    response_format=self._response_strategy,
                    tool_choice=None,
                )
            else:
                guarded_request = request.override(
                    tools=[tool_lookup[next_tool]],
                    response_format=None,
                    tool_choice="any",
                )
            try:
                response = handler(guarded_request)
                if next_tool is not None:
                    self._validate_stage_response(response, next_tool, called)
                return response
            except StructuredOutputError:
                raise
            except ServiceError:
                raise
            except NotImplementedError as exc:
                raise ServiceError(
                    status_code=503,
                    code="AGENT_MODEL_NOT_CONFIGURED",
                    message=(
                        "The configured chat model does not support agent tools."
                    ),
                ) from exc
            except Exception as exc:
                raise ServiceError(
                    status_code=503,
                    code="AGENT_MODEL_UNAVAILABLE",
                    message=(
                        "The configured chat model is unavailable or timed out."
                    ),
                ) from exc

        return enforce_stage

    def _validate_stage_response(
        self,
        response,
        expected_tool: str,
        called: list[str],
    ) -> None:
        tool_calls: list[dict] = []
        for message in getattr(response, "result", []):
            if isinstance(message, AIMessage):
                tool_calls.extend(message.tool_calls)
        if not tool_calls:
            return

        unknown = [
            str(call.get("name") or "")
            for call in tool_calls
            if str(call.get("name") or "")
            not in set(REQUIRED_TOOLS) | self._structured_tool_names
        ]
        if unknown:
            raise ServiceError(
                status_code=502,
                code="AGENT_UNKNOWN_TOOL",
                message=f"The agent requested a disallowed tool: {unknown[0]}",
            )
        requested = [str(call.get("name") or "") for call in tool_calls]
        if requested != [expected_tool]:
            raise self._sequence_error(called)

    def _next_required_tool(self, called: list[str]) -> str | None:
        expected_prefix = list(REQUIRED_TOOLS[: len(called)])
        if len(called) > len(REQUIRED_TOOLS) or called != expected_prefix:
            raise self._sequence_error(called)
        if len(called) >= len(REQUIRED_TOOLS):
            return None
        return REQUIRED_TOOLS[len(called)]

    def _sequence_error(self, called: list[str]) -> ServiceError:
        missing = [name for name in REQUIRED_TOOLS if name not in called]
        return ServiceError(
            status_code=502,
            code="AGENT_TOOL_SEQUENCE_INVALID",
            message="The agent did not complete all required troubleshooting tools.",
            details={
                "missingTools": missing,
                "requiredOrder": list(REQUIRED_TOOLS),
            },
        )

    def _recursion_limit(self) -> int:
        # Each tool round uses one model node and one tool node. The final
        # structured response uses one additional model node.
        return self._settings.agent_max_steps * 2 + 2

    def _create_model(self) -> ChatOpenAI:
        api_key = self._settings.chat_key()
        if (
            not self._settings.chat_base_url
            or not self._settings.chat_model
            or not api_key
        ):
            raise ServiceError(
                status_code=503,
                code="AGENT_MODEL_NOT_CONFIGURED",
                message="The DeepSeek chat API URL, key, or model is not configured.",
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

    def _build_tools(self) -> list[BaseTool]:
        def inspect_device_context(runtime: ToolRuntime[_AgentContext]) -> str:
            """Read the Java-authorized device snapshot and warranty status."""
            request = runtime.context.request
            state = runtime.context.state
            state.mark(DEVICE_TOOL)
            device = request.device
            return json.dumps(
                {
                    "deviceId": device.device_id,
                    "customerOrgId": device.customer_org_id,
                    "customerName": device.customer_name,
                    "deviceName": device.device_name,
                    "serialNumber": device.serial_number,
                    "brandId": device.brand_id,
                    "brandName": device.brand_name,
                    "modelId": device.model_id,
                    "modelName": device.model_name,
                    "installLocation": device.install_location,
                    "warrantyEndDate": (
                        device.warranty_end_date.isoformat()
                        if device.warranty_end_date
                        else None
                    ),
                    "warrantyStatus": self._warranty_status(
                        device.warranty_end_date
                    ),
                },
                ensure_ascii=False,
            )

        def search_product_knowledge(
            runtime: ToolRuntime[_AgentContext],
        ) -> str:
            """Search only the selected model's Java-authorized knowledge versions."""
            request = runtime.context.request
            state = runtime.context.state
            rag_request = RagAnswerRequest(
                question=self._rag_question(request),
                knowledge_base_id=request.knowledge_base_id,
                brand_id=request.device.brand_id,
                model_id=request.device.model_id,
                brand_name=request.device.brand_name,
                model_name=request.device.model_name,
                document_version_ids=request.document_version_ids,
                top_k=request.top_k,
                score_threshold=request.score_threshold,
            )
            state.rag_response = self._rag_service.answer(rag_request)
            state.mark(KNOWLEDGE_TOOL)
            return state.rag_response.model_dump_json(by_alias=True)

        def build_work_order_draft(
            title: str,
            fault_description: str,
            priority: str,
            runtime: ToolRuntime[_AgentContext],
        ) -> str:
            """Build an editable draft only; never create a formal work order."""
            request = runtime.context.request
            state = runtime.context.state
            candidate = DraftToolInput(
                title=title,
                fault_description=fault_description,
                priority=priority,
            )
            original = request.issue_description
            organized = candidate.fault_description.strip()
            original_prefix = f"用户报修描述：{original}"
            if not organized.startswith(original_prefix):
                organized = f"用户报修描述：{original}\nAI整理：{organized}"
            state.draft = AgentWorkOrderDraft(
                title=candidate.title.strip(),
                fault_description=organized[:2000],
                priority=candidate.priority,
            )
            state.mark(DRAFT_TOOL)
            return state.draft.model_dump_json(by_alias=True)

        return [
            StructuredTool.from_function(
                func=inspect_device_context,
                name=DEVICE_TOOL,
                description=(
                    "读取Java已经授权并传入的当前设备、客户、品牌、型号、"
                    "安装位置和保修状态。不得猜测设备信息。"
                ),
            ),
            StructuredTool.from_function(
                func=search_product_knowledge,
                name=KNOWLEDGE_TOOL,
                description=(
                    "在当前设备型号、知识库和Java批准的文档版本范围内检索故障证据。"
                    "返回answered、回答和不可改写的引用。"
                ),
            ),
            StructuredTool.from_function(
                func=build_work_order_draft,
                name=DRAFT_TOOL,
                description=(
                    "生成等待用户确认的工单草稿。priority只能是0低、1中、2高、3紧急。"
                    "此工具不会写数据库，也不会创建正式工单。"
                ),
                args_schema=DraftToolInput,
            ),
        ]

    def _validate_required_tools(self, state: _ToolState) -> None:
        called = [item.tool_name for item in state.trace]
        missing = [name for name in REQUIRED_TOOLS if name not in called]
        ordered = self._contains_ordered_sequence(called, REQUIRED_TOOLS)
        if (
            missing
            or not ordered
            or state.rag_response is None
            or state.draft is None
        ):
            raise ServiceError(
                status_code=502,
                code="AGENT_TOOL_SEQUENCE_INVALID",
                message="The agent did not complete all required troubleshooting tools.",
                details={
                    "missingTools": missing,
                    "requiredOrder": list(REQUIRED_TOOLS),
                },
            )

    def _contains_ordered_sequence(
        self,
        called: list[str],
        required: tuple[str, ...],
    ) -> bool:
        position = -1
        for tool_name in required:
            try:
                position = called.index(tool_name, position + 1)
            except ValueError:
                return False
        return True

    def _fallback_analysis(
        self,
        request: TroubleshootingAgentRequest,
        state: _ToolState,
    ) -> AgentAnalysis:
        rag = state.rag_response
        assert rag is not None
        summary = (rag.answer or REFUSAL_TEXT).strip()[:1000]
        if len(summary) < 2:
            summary = REFUSAL_TEXT
        return AgentAnalysis(
            summary=summary,
            troubleshooting_steps=[],
            safety_warnings=[],
            requires_on_site_service=(
                not rag.answered
                or self._requires_human_service(request.issue_description)
            ),
        )

    def _response(
        self,
        request: TroubleshootingAgentRequest,
        state: _ToolState,
        analysis: AgentAnalysis,
    ) -> TroubleshootingAgentResponse:
        rag = state.rag_response
        draft = state.draft
        assert rag is not None
        assert draft is not None

        if not rag.answered:
            return TroubleshootingAgentResponse(
                answered=False,
                summary=REFUSAL_TEXT,
                troubleshooting_steps=[],
                safety_warnings=self._safe_refusal_warnings(
                    analysis.safety_warnings
                ),
                requires_on_site_service=True,
                refusal_reason=rag.refusal_reason or REFUSAL_REASON,
                draft=draft,
                citations=[],
                tool_trace=state.trace,
                model_name=self.model_name,
            )

        return TroubleshootingAgentResponse(
            answered=True,
            summary=analysis.summary,
            troubleshooting_steps=analysis.troubleshooting_steps,
            safety_warnings=self._safe_answer_warnings(
                analysis.safety_warnings,
                request.issue_description,
            ),
            requires_on_site_service=(
                analysis.requires_on_site_service
                or self._requires_human_service(request.issue_description)
            ),
            draft=draft,
            citations=rag.citations,
            tool_trace=state.trace,
            model_name=self.model_name,
        )

    def _user_message(self, request: TroubleshootingAgentRequest) -> str:
        return (
            f"请分析以下报修描述并生成待确认工单草稿。\n"
            f"设备：{request.device.brand_name} {request.device.model_name}；"
            f"名称：{request.device.device_name}。\n"
            f"用户报修描述：{request.issue_description}\n"
            f"必须依次使用设备、知识库和草稿工具。不要创建正式工单。"
        )

    def _rag_question(self, request: TroubleshootingAgentRequest) -> str:
        # 品牌、型号、知识库和文档版本已经通过独立字段做严格过滤。
        # 保留用户原始问题可复用AI助手的检索质量，避免型号前缀稀释
        # “94毫米”、错误码、耗材型号等高权重精确词。
        return request.issue_description[:500]

    def _warranty_status(self, warranty_end_date: date | None) -> str:
        if warranty_end_date is None:
            return "UNKNOWN"
        return "IN_WARRANTY" if warranty_end_date >= date.today() else "EXPIRED"

    def _safe_refusal_warnings(self, warnings: list[str]) -> list[str]:
        safe = [warning for warning in warnings if warning.strip()][:3]
        fixed = "知识库证据不足，请停止拆机、带电或高温部件操作并交由合格人员核实。"
        if fixed not in safe:
            safe.append(fixed)
        return safe

    def _safe_answer_warnings(
        self,
        warnings: list[str],
        issue_description: str,
    ) -> list[str]:
        safe = [warning for warning in warnings if warning.strip()][:7]
        if self._contains_operation_risk(issue_description):
            fixed = "检查设备内部前请关闭电源并遵循原厂安全要求；不确定时停止操作。"
            if fixed not in safe:
                safe.append(fixed)
        return safe

    def _contains_operation_risk(self, issue_description: str) -> bool:
        risk_terms = (
            "卡纸",
            "拆机",
            "内部",
            "电源",
            "主板",
            "定影",
            "高温",
            "冒烟",
            "烧焦",
            "漏电",
        )
        return any(term in issue_description for term in risk_terms)

    def _requires_human_service(self, issue_description: str) -> bool:
        high_risk_terms = (
            "拆机",
            "主板",
            "电源芯片",
            "带电",
            "冒烟",
            "烧焦",
            "漏电",
        )
        return any(term in issue_description for term in high_risk_terms)


@lru_cache
def get_troubleshooting_agent_service() -> LangChainTroubleshootingAgent:
    settings = get_settings()
    return LangChainTroubleshootingAgent(
        settings=settings,
        rag_service=get_rag_answering_service(),
    )
