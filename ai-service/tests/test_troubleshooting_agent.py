from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from pydantic import PrivateAttr

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.main import app
from app.schemas.agent import (
    AgentToolTrace,
    AgentWorkOrderDraft,
    TroubleshootingAgentRequest,
    TroubleshootingAgentResponse,
)
from app.schemas.rag import RagAnswerRequest, RagAnswerResponse, RagCitation
from app.services.rag_answering import REFUSAL_REASON, REFUSAL_TEXT
from app.services.troubleshooting_agent import (
    AGENT_SYSTEM_PROMPT,
    DEVICE_TOOL,
    DRAFT_TOOL,
    KNOWLEDGE_TOOL,
    REQUIRED_TOOLS,
    LangChainTroubleshootingAgent,
    get_troubleshooting_agent_service,
)


AGENT_URL = "/api/v1/internal/agent/troubleshoot"


class ScriptedToolModel(BaseChatModel):
    _responses: list[AIMessage | Exception] = PrivateAttr()
    _bound_tool_name_history: list[list[str]] = PrivateAttr(default_factory=list)
    _tool_choices: list[str | None] = PrivateAttr(default_factory=list)
    _messages: list[list[object]] = PrivateAttr(default_factory=list)

    def __init__(self, responses: list[AIMessage | Exception]) -> None:
        super().__init__()
        self._responses = list(responses)

    @property
    def _llm_type(self) -> str:
        return "officecare-scripted-tool-model"

    @property
    def bound_tool_name_history(self) -> list[list[str]]:
        return self._bound_tool_name_history

    @property
    def tool_choices(self) -> list[str | None]:
        return self._tool_choices

    @property
    def messages(self) -> list[list[object]]:
        return self._messages

    def bind_tools(self, tools, *, tool_choice=None, **kwargs):
        self._bound_tool_name_history.append(
            [self._tool_name(tool) for tool in tools]
        )
        self._tool_choices.append(tool_choice)
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        self._messages.append(list(messages))
        response = self._responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return ChatResult(generations=[ChatGeneration(message=response)])

    def _tool_name(self, tool) -> str:
        if hasattr(tool, "name"):
            return str(tool.name)
        if isinstance(tool, dict):
            function = tool.get("function") or {}
            return str(function.get("name") or tool.get("name") or "")
        return str(tool)


class FakeRagService:
    def __init__(self, response: RagAnswerResponse) -> None:
        self.response = response
        self.request: RagAnswerRequest | None = None

    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        self.request = request
        return self.response


def _request() -> TroubleshootingAgentRequest:
    return TroubleshootingAgentRequest.model_validate(
        {
            "issueDescription": "双面打印到第二页时在双面单元发生卡纸。",
            "knowledgeBaseId": 1,
            "documentVersionIds": [40, 41],
            "device": {
                "deviceId": 8,
                "customerOrgId": 3,
                "customerName": "演示科技有限公司",
                "deviceName": "财务室打印机",
                "serialNumber": "SN-DEMO-0001",
                "brandId": 10,
                "brandName": "Brother",
                "modelId": 20,
                "modelName": "DCP-L2550DW",
                "installLocation": "A座3层财务室",
                "warrantyEndDate": "2027-01-09",
            },
            "topK": 5,
            "scoreThreshold": 0.45,
        }
    )


def _citation() -> RagCitation:
    return RagCitation(
        citation_index=1,
        knowledge_base_id=1,
        brand_id=10,
        model_id=20,
        document_id=30,
        document_version_id=40,
        document_title="高级说明书",
        original_file_name="高级说明书.pdf",
        page_number=395,
        chunk_index=88,
        heading="双面单元卡纸",
        quote="设备内部某些零件可能温度很高，请等待设备冷却。",
        score=0.81,
    )


def _rag_response(*, answered: bool = True) -> RagAnswerResponse:
    if not answered:
        return RagAnswerResponse(
            answered=False,
            answer=REFUSAL_TEXT,
            refusal_reason=REFUSAL_REASON,
            citations=[],
            retrieved_count=0,
            score_threshold=0.45,
            model_name=None,
        )
    return RagAnswerResponse(
        answered=True,
        answer="关闭电源并等待设备冷却后，再按手册清除卡纸。[资料1]",
        citations=[_citation()],
        retrieved_count=1,
        score_threshold=0.45,
        model_name="deepseek-v4-pro",
    )


def _tool_call(name: str, args: dict, call_id: str) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[
            {"name": name, "args": args, "id": call_id, "type": "tool_call"}
        ],
    )


def _final_message(*, include_steps: bool = True) -> AIMessage:
    return _tool_call(
        "AgentAnalysis",
        {
            "summary": "双面打印过程中发生卡纸。",
            "troubleshootingSteps": (
                ["关闭电源并等待设备冷却后按手册清除卡纸。"]
                if include_steps
                else []
            ),
            "safetyWarnings": ["不要立即触摸内部高温零件。"],
            "requiresOnSiteService": False,
        },
        "call-analysis",
    )


def _scripted_responses(*, include_steps: bool = True) -> list[AIMessage]:
    return [
        _tool_call(DEVICE_TOOL, {}, "call-device"),
        _tool_call(KNOWLEDGE_TOOL, {}, "call-knowledge"),
        _tool_call(
            DRAFT_TOOL,
            {
                "title": "双面打印卡纸",
                "fault_description": "双面打印时在双面单元发生卡纸。",
                "priority": "2",
            },
            "call-draft",
        ),
        _final_message(include_steps=include_steps),
    ]


def _scripted_model(*, include_steps: bool = True) -> ScriptedToolModel:
    return ScriptedToolModel(_scripted_responses(include_steps=include_steps))


def _settings() -> Settings:
    return Settings(
        _env_file=None,
        chat_base_url="http://chat.test/v1",
        chat_api_key="test-key",
        chat_model="test-agent-model",
        agent_max_steps=8,
    )


def test_agent_uses_required_tools_and_returns_grounded_draft() -> None:
    model = _scripted_model()
    rag_service = FakeRagService(_rag_response())
    service = LangChainTroubleshootingAgent(
        _settings(),
        rag_service,
        model=model,
    )

    response = service.run(_request())

    assert response.answered is True
    assert response.requires_human_review is True
    assert response.citations[0].page_number == 395
    assert response.draft.priority == "2"
    assert _request().issue_description in response.draft.fault_description
    assert [item.tool_name for item in response.tool_trace] == [
        DEVICE_TOOL,
        KNOWLEDGE_TOOL,
        DRAFT_TOOL,
    ]
    assert model.bound_tool_name_history == [
        [DEVICE_TOOL],
        [KNOWLEDGE_TOOL],
        [DRAFT_TOOL],
        ["AgentAnalysis"],
    ]
    assert model.tool_choices == ["any", "any", "any", "any"]
    assert rag_service.request is not None
    assert rag_service.request.knowledge_base_id == 1
    assert rag_service.request.brand_id == 10
    assert rag_service.request.model_id == 20
    assert rag_service.request.document_version_ids == [40, 41]
    assert rag_service.request.question == _request().issue_description
    assert any("关闭电源" in item for item in response.safety_warnings)


def test_agent_forces_human_service_for_high_risk_issue() -> None:
    request = _request()
    request.issue_description = "打印机主板电源芯片冒烟，需要拆机维修。"
    service = LangChainTroubleshootingAgent(
        _settings(),
        FakeRagService(_rag_response()),
        model=_scripted_model(),
    )

    response = service.run(request)

    assert response.requires_on_site_service is True


def test_agent_preserves_exact_numeric_fault_question_for_rag() -> None:
    request = _request()
    request.issue_description = "打印页面每隔94毫米出现重复斑点"
    rag_service = FakeRagService(_rag_response())
    service = LangChainTroubleshootingAgent(
        _settings(),
        rag_service,
        model=_scripted_model(),
    )

    service.run(request)

    assert rag_service.request is not None
    assert rag_service.request.question == "打印页面每隔94毫米出现重复斑点"
    assert "Brother" not in rag_service.request.question
    assert "DCP-L2550DW" not in rag_service.request.question


def test_agent_draft_always_starts_with_original_issue() -> None:
    request = _request()
    organized = (
        "设备：Brother DCP-L2550DW。用户报修："
        f"{request.issue_description}建议进一步排查。"
    )
    model = ScriptedToolModel(
        [
            _tool_call(DEVICE_TOOL, {}, "call-device"),
            _tool_call(KNOWLEDGE_TOOL, {}, "call-knowledge"),
            _tool_call(
                DRAFT_TOOL,
                {
                    "title": "双面打印卡纸",
                    "fault_description": organized,
                    "priority": "2",
                },
                "call-draft",
            ),
            _final_message(),
        ]
    )
    service = LangChainTroubleshootingAgent(
        _settings(), FakeRagService(_rag_response()), model=model
    )

    response = service.run(request)

    assert response.draft.fault_description.startswith(
        f"用户报修描述：{request.issue_description}\nAI整理："
    )


def test_agent_refusal_removes_unverified_steps_and_citations() -> None:
    service = LangChainTroubleshootingAgent(
        _settings(),
        FakeRagService(_rag_response(answered=False)),
        model=_scripted_model(include_steps=True),
    )

    response = service.run(_request())

    assert response.answered is False
    assert response.summary == REFUSAL_TEXT
    assert response.refusal_reason == REFUSAL_REASON
    assert response.troubleshooting_steps == []
    assert response.citations == []
    assert response.requires_on_site_service is True
    assert any("知识库证据不足" in item for item in response.safety_warnings)
    assert response.draft.title == "双面打印卡纸"


def test_agent_falls_back_when_tool_strategy_validation_fails() -> None:
    invalid_final = _tool_call(
        "AgentAnalysis",
        {
            "summary": "x",
            "troubleshootingSteps": "不是数组",
            "safetyWarnings": [],
        },
        "call-invalid-analysis",
    )
    model = ScriptedToolModel(
        [
            _tool_call(DEVICE_TOOL, {}, "call-device"),
            _tool_call(KNOWLEDGE_TOOL, {}, "call-knowledge"),
            _tool_call(
                DRAFT_TOOL,
                {
                    "title": "设备内部卡纸",
                    "fault_description": "设备显示内部卡纸。",
                    "priority": "2",
                },
                "call-draft",
            ),
            invalid_final,
        ]
    )
    rag_response = _rag_response()
    service = LangChainTroubleshootingAgent(
        _settings(), FakeRagService(rag_response), model=model
    )

    response = service.run(_request())

    assert response.summary == rag_response.answer
    assert response.troubleshooting_steps == []
    assert response.citations == rag_response.citations


def test_agent_falls_back_to_grounded_rag_answer_for_invalid_final_output() -> None:
    invalid_final = AIMessage(content="分析完成，但没有返回JSON。")
    model = ScriptedToolModel(
        [
            _tool_call(DEVICE_TOOL, {}, "call-device"),
            _tool_call(KNOWLEDGE_TOOL, {}, "call-knowledge"),
            _tool_call(
                DRAFT_TOOL,
                {
                    "title": "设备内部卡纸",
                    "fault_description": "设备显示内部卡纸。",
                    "priority": "2",
                },
                "call-draft",
            ),
            invalid_final,
        ]
    )
    rag_response = _rag_response()
    service = LangChainTroubleshootingAgent(
        _settings(), FakeRagService(rag_response), model=model
    )

    response = service.run(_request())

    assert response.answered is True
    assert response.summary == rag_response.answer
    assert response.troubleshooting_steps == []
    assert response.citations == rag_response.citations
    assert [item.tool_name for item in response.tool_trace] == list(REQUIRED_TOOLS)


def test_agent_invalid_final_output_keeps_strict_evidence_refusal() -> None:
    invalid_final = AIMessage(content="分析完成，但没有返回JSON。")
    model = ScriptedToolModel(
        [
            _tool_call(DEVICE_TOOL, {}, "call-device"),
            _tool_call(KNOWLEDGE_TOOL, {}, "call-knowledge"),
            _tool_call(
                DRAFT_TOOL,
                {
                    "title": "故障待人工检查",
                    "fault_description": "知识库中没有对应故障依据。",
                    "priority": "1",
                },
                "call-draft",
            ),
            invalid_final,
        ]
    )
    service = LangChainTroubleshootingAgent(
        _settings(), FakeRagService(_rag_response(answered=False)), model=model
    )

    response = service.run(_request())

    assert response.answered is False
    assert response.summary == REFUSAL_TEXT
    assert response.troubleshooting_steps == []
    assert response.citations == []
    assert response.requires_on_site_service is True
    assert response.refusal_reason == REFUSAL_REASON


def test_agent_rejects_missing_required_tools() -> None:
    service = LangChainTroubleshootingAgent(
        _settings(),
        FakeRagService(_rag_response()),
        model=ScriptedToolModel([AIMessage(content="提前结束")]),
    )

    with pytest.raises(ServiceError) as captured:
        service.run(_request())

    assert captured.value.code == "AGENT_TOOL_SEQUENCE_INVALID"
    assert captured.value.details["missingTools"] == [
        DEVICE_TOOL,
        KNOWLEDGE_TOOL,
        DRAFT_TOOL,
    ]


def test_agent_rejects_tools_called_out_of_order() -> None:
    model = ScriptedToolModel(
        [
            _tool_call(KNOWLEDGE_TOOL, {}, "call-knowledge"),
            _tool_call(DEVICE_TOOL, {}, "call-device"),
            _tool_call(
                DRAFT_TOOL,
                {
                    "title": "双面打印卡纸",
                    "fault_description": "双面打印卡纸。",
                    "priority": "2",
                },
                "call-draft",
            ),
            _final_message(),
        ]
    )
    service = LangChainTroubleshootingAgent(
        _settings(), FakeRagService(_rag_response()), model=model
    )

    with pytest.raises(ServiceError) as captured:
        service.run(_request())

    assert captured.value.code == "AGENT_TOOL_SEQUENCE_INVALID"


def test_agent_wraps_model_failure_without_leaking_details() -> None:
    service = LangChainTroubleshootingAgent(
        _settings(),
        FakeRagService(_rag_response()),
        model=ScriptedToolModel([RuntimeError("secret provider response")]),
    )

    with pytest.raises(ServiceError) as captured:
        service.run(_request())

    assert captured.value.code == "AGENT_MODEL_UNAVAILABLE"
    assert "secret provider response" not in captured.value.message


def test_agent_rejects_unknown_tool_before_execution() -> None:
    service = LangChainTroubleshootingAgent(
        _settings(),
        FakeRagService(_rag_response()),
        model=ScriptedToolModel(
            [_tool_call("delete_work_order", {}, "call-disallowed")]
        ),
    )

    with pytest.raises(ServiceError) as captured:
        service.run(_request())

    assert captured.value.code == "AGENT_UNKNOWN_TOOL"
    assert "delete_work_order" in captured.value.message


def test_agent_maps_unsupported_tool_binding_to_configuration_error() -> None:
    class UnsupportedToolModel(ScriptedToolModel):
        def bind_tools(self, tools, *, tool_choice=None, **kwargs):
            raise NotImplementedError("tools are unsupported")

    service = LangChainTroubleshootingAgent(
        _settings(),
        FakeRagService(_rag_response()),
        model=UnsupportedToolModel(_scripted_responses()),
    )

    with pytest.raises(ServiceError) as captured:
        service.run(_request())

    assert captured.value.code == "AGENT_MODEL_NOT_CONFIGURED"
    assert "tools are unsupported" not in captured.value.message


def test_compiled_agent_keeps_runtime_state_isolated_between_requests() -> None:
    model = ScriptedToolModel(
        [*_scripted_responses(), *_scripted_responses(include_steps=False)]
    )
    service = LangChainTroubleshootingAgent(
        _settings(), FakeRagService(_rag_response()), model=model
    )
    first_request = _request()
    second_request = _request()
    second_request.issue_description = "打印页面每隔94毫米出现重复斑点"

    first = service.run(first_request)
    second = service.run(second_request)

    assert [item.sequence for item in first.tool_trace] == [1, 2, 3]
    assert [item.sequence for item in second.tool_trace] == [1, 2, 3]
    assert second_request.issue_description in second.draft.fault_description
    assert len(model.bound_tool_name_history) == 8


def test_agent_maps_graph_recursion_to_step_limit_error() -> None:
    invalid_draft = _tool_call(
        DRAFT_TOOL,
        {
            "title": "x",
            "fault_description": "x",
            "priority": "9",
        },
        "call-invalid-draft",
    )
    model = ScriptedToolModel(
        [
            _tool_call(DEVICE_TOOL, {}, "call-device"),
            _tool_call(KNOWLEDGE_TOOL, {}, "call-knowledge"),
            *[invalid_draft for _ in range(20)],
        ]
    )
    service = LangChainTroubleshootingAgent(
        Settings(
            _env_file=None,
            chat_base_url="http://chat.test/v1",
            chat_api_key="test-key",
            chat_model="test-agent-model",
            agent_max_steps=4,
        ),
        FakeRagService(_rag_response()),
        model=model,
    )

    with pytest.raises(ServiceError) as captured:
        service.run(_request())

    assert captured.value.code == "AGENT_STEP_LIMIT_EXCEEDED"


def test_agent_prompt_forbids_formal_work_order_creation() -> None:
    assert "不得声称已经创建工单" in AGENT_SYSTEM_PROMPT
    assert "没有创建、修改、指派或关闭正式工单的工具" in AGENT_SYSTEM_PROMPT


class FakeAgentEndpointService:
    def run(self, request: TroubleshootingAgentRequest) -> TroubleshootingAgentResponse:
        return TroubleshootingAgentResponse(
            answered=True,
            summary="双面打印过程中发生卡纸。",
            troubleshooting_steps=["关闭电源并等待设备冷却。"],
            safety_warnings=["不要立即触摸内部零件。"],
            requires_on_site_service=False,
            draft=AgentWorkOrderDraft(
                title="双面打印卡纸",
                fault_description=request.issue_description,
                priority="2",
            ),
            citations=[_citation()],
            tool_trace=[
                AgentToolTrace(sequence=1, tool_name=DEVICE_TOOL, success=True),
                AgentToolTrace(sequence=2, tool_name=KNOWLEDGE_TOOL, success=True),
                AgentToolTrace(sequence=3, tool_name=DRAFT_TOOL, success=True),
            ],
            model_name="test-agent-model",
        )


@pytest.fixture
def agent_client() -> Iterator[TestClient]:
    app.dependency_overrides[get_troubleshooting_agent_service] = (
        lambda: FakeAgentEndpointService()
    )
    try:
        with TestClient(app, raise_server_exceptions=False) as test_client:
            yield test_client
    finally:
        app.dependency_overrides.pop(get_troubleshooting_agent_service, None)


def test_agent_endpoint_returns_camel_case_structured_result(
    agent_client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = agent_client.post(
        AGENT_URL,
        headers=auth_headers,
        json=_request().model_dump(by_alias=True, mode="json"),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["answered"] is True
    assert body["requiresHumanReview"] is True
    assert body["draft"]["priority"] == "2"
    assert body["citations"][0]["pageNumber"] == 395
    assert [item["toolName"] for item in body["toolTrace"]] == [
        DEVICE_TOOL,
        KNOWLEDGE_TOOL,
        DRAFT_TOOL,
    ]


def test_agent_endpoint_requires_internal_token(client: TestClient) -> None:
    response = client.post(
        AGENT_URL,
        json=_request().model_dump(by_alias=True, mode="json"),
    )

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_agent_endpoint_rejects_duplicate_document_versions(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    body = _request().model_dump(by_alias=True, mode="json")
    body["documentVersionIds"] = [40, 40]

    response = client.post(AGENT_URL, headers=auth_headers, json=body)

    assert response.status_code == 422
    assert response.json()["code"] == "REQUEST_VALIDATION_ERROR"
