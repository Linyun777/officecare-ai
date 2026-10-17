from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langgraph.checkpoint.memory import InMemorySaver
from pydantic import PrivateAttr

from app.api.assistant import get_assistant_agent_service
from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.main import app
from app.schemas.assistant_agent import (
    AssistantAgentDecision,
    AssistantDiagnosticStatus,
    AssistantResponseType,
    AssistantTurnRequest,
    AssistantTurnResponse,
)
from app.schemas.rag import RagAnswerRequest, RagAnswerResponse, RagCitation
from app.services.assistant_agent import (
    KNOWLEDGE_TOOL,
    LangChainAssistantAgent,
)


ASSISTANT_URL = "/api/v1/internal/assistant/turn"


class ScriptedAssistantModel(BaseChatModel):
    _responses: list[AIMessage | Exception] = PrivateAttr()
    _messages: list[list[object]] = PrivateAttr(default_factory=list)
    _bound_tool_names: list[list[str]] = PrivateAttr(default_factory=list)

    def __init__(self, responses: list[AIMessage | Exception]) -> None:
        super().__init__()
        self._responses = list(responses)

    @property
    def _llm_type(self) -> str:
        return "officecare-scripted-assistant"

    @property
    def messages(self) -> list[list[object]]:
        return self._messages

    @property
    def remaining_response_count(self) -> int:
        return len(self._responses)

    def bind_tools(self, tools, *, tool_choice=None, **kwargs):
        names = []
        for tool in tools:
            if hasattr(tool, "name"):
                names.append(str(tool.name))
            else:
                function = tool.get("function", {}) if isinstance(tool, dict) else {}
                names.append(str(function.get("name") or ""))
        self._bound_tool_names.append(names)
        return self

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        self._messages.append(list(messages))
        response = self._responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return ChatResult(generations=[ChatGeneration(message=response)])


class FakeRagService:
    def __init__(self) -> None:
        self.requests: list[RagAnswerRequest] = []

    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        self.requests.append(request)
        return RagAnswerResponse(
            answered=True,
            answer="每隔94毫米出现重复斑点时，请检查并清洁硒鼓单元。[资料1]",
            citations=[
                RagCitation(
                    citation_index=1,
                    knowledge_base_id=request.knowledge_base_id,
                    brand_id=request.brand_id,
                    model_id=request.model_id,
                    document_id=30,
                    document_version_id=request.document_version_ids[0],
                    document_title="高级说明书",
                    original_file_name="高级说明书.pdf",
                    page_number=402,
                    chunk_index=91,
                    heading="改善打印质量",
                    quote="以94毫米的间隔重复出现斑点时，请清洁硒鼓单元。",
                    score=0.82,
                )
            ],
            retrieved_count=1,
            score_threshold=request.score_threshold,
            model_name="test-chat-model",
        )


class RefusingRagService:
    def __init__(self) -> None:
        self.requests: list[RagAnswerRequest] = []

    def answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        self.requests.append(request)
        return RagAnswerResponse(
            answered=False,
            answer="根据当前知识库中的资料，暂时无法找到足够证据回答这个问题。",
            refusal_reason="INSUFFICIENT_EVIDENCE",
            citations=[],
            retrieved_count=0,
            score_threshold=request.score_threshold,
            model_name="test-chat-model",
        )


def _settings() -> Settings:
    return Settings(
        _env_file=None,
        chat_base_url="http://chat.test/v1",
        chat_api_key="test-key",
        chat_model="test-chat-model",
        agent_recent_message_limit=12,
        agent_summary_trigger_messages=20,
    )


def _request(
    *,
    question: str,
    turn_id: str,
    conversation_id: int = 15,
    knowledge_base_id: int = 1,
) -> AssistantTurnRequest:
    return AssistantTurnRequest.model_validate(
        {
            "conversationId": conversation_id,
            "userId": 7,
            "turnId": turn_id,
            "question": question,
            "knowledgeBaseId": knowledge_base_id,
            "brandId": 2,
            "modelId": 3,
            "brandName": "Brother",
            "modelName": "DCP-L2550DW",
            "documentVersionIds": [11, 12],
            "topK": 5,
            "scoreThreshold": 0.45,
        }
    )


def _tool_call(name: str, args: dict, call_id: str) -> AIMessage:
    return AIMessage(
        content="",
        tool_calls=[
            {"name": name, "args": args, "id": call_id, "type": "tool_call"}
        ],
    )


def _decision_call(
    *,
    response_type: str,
    answer: str,
    diagnostic_status: str,
    requires_user_reply: bool,
    call_id: str,
    issue_summary: str = "打印页面出现重复斑点",
    attempted_steps: list[str] | None = None,
    observations: list[str] | None = None,
) -> AIMessage:
    return _tool_call(
        "AssistantAgentDecision",
        {
            "responseType": response_type,
            "answer": answer,
            "diagnosticStatus": diagnostic_status,
            "issueSummary": issue_summary,
            "attemptedSteps": attempted_steps or [],
            "observations": observations or [],
            "nextQuestion": answer if requires_user_reply else None,
            "requiresUserReply": requires_user_reply,
            "requiresOnSiteService": False,
        },
        call_id,
    )


def _two_turn_responses() -> list[AIMessage]:
    return [
        _decision_call(
            response_type="CLARIFICATION",
            answer="这些重复斑点之间大约相隔多少毫米？",
            diagnostic_status="COLLECTING_INFORMATION",
            requires_user_reply=True,
            call_id="decision-1",
        ),
        _tool_call(
            KNOWLEDGE_TOOL,
            {"query": "打印页面每隔94毫米出现重复斑点怎么办"},
            "knowledge-2",
        ),
        _decision_call(
            response_type="DIAGNOSTIC_STEP",
            answer="请先按说明书检查并清洁硒鼓单元，然后告诉我斑点是否消失。[资料1]",
            diagnostic_status="AWAITING_STEP_RESULT",
            requires_user_reply=True,
            call_id="decision-2",
            attempted_steps=["准备检查并清洁硒鼓单元"],
            observations=["重复斑点间隔约94毫米"],
        ),
    ]


@pytest.mark.asyncio
async def test_assistant_remembers_first_turn_after_service_recreation() -> None:
    checkpointer = InMemorySaver()
    model = ScriptedAssistantModel(_two_turn_responses())
    rag_service = FakeRagService()
    first_service = LangChainAssistantAgent(
        _settings(),
        rag_service,
        checkpointer,
        model=model,
    )

    first = await first_service.run_turn(
        _request(
            question="打印页面出现重复斑点",
            turn_id="3c7485fd-ddf0-4db7-8a11-e15d2418878e",
        )
    )

    assert first.response_type == AssistantResponseType.CLARIFICATION
    assert first.requires_user_reply is True
    assert first.answered is False

    # Rebuild the LangChain Agent while keeping the same checkpointer. This is
    # equivalent to a FastAPI process restart backed by persistent PostgreSQL.
    second_service = LangChainAssistantAgent(
        _settings(),
        rag_service,
        checkpointer,
        model=model,
    )
    second = await second_service.run_turn(
        _request(
            question="大约94毫米",
            turn_id="e1feae17-65b9-46ad-b3dd-356676686a41",
        )
    )

    assert second.response_type == AssistantResponseType.DIAGNOSTIC_STEP
    assert second.diagnostic_status == AssistantDiagnosticStatus.AWAITING_STEP_RESULT
    assert second.answered is True
    assert second.citations[0].page_number == 402
    assert rag_service.requests[0].question == "打印页面每隔94毫米出现重复斑点怎么办"

    second_turn_prompt = "\n".join(
        str(message) for message in model.messages[1]
    )
    assert "打印页面出现重复斑点" in second_turn_prompt
    assert "这些重复斑点之间大约相隔多少毫米" in second_turn_prompt


@pytest.mark.asyncio
async def test_assistant_forces_current_search_when_model_reuses_stale_answer() -> None:
    model = ScriptedAssistantModel(
        [
            _decision_call(
                response_type="DIAGNOSTIC_STEP",
                answer="请继续使用已经删除的测试资料。",
                diagnostic_status="AWAITING_STEP_RESULT",
                requires_user_reply=True,
                call_id="decision-stale-memory",
            )
        ]
    )
    rag_service = RefusingRagService()
    service = LangChainAssistantAgent(
        _settings(),
        rag_service,
        InMemorySaver(),
        model=model,
    )

    response = await service.run_turn(
        _request(
            question="OC-MQ-TEST-0904应该如何处理？",
            turn_id="32f77812-bf9b-416c-aed5-253746190d26",
        )
    )

    assert response.response_type == AssistantResponseType.REFUSAL
    assert response.diagnostic_status == (
        AssistantDiagnosticStatus.INSUFFICIENT_EVIDENCE
    )
    assert response.answered is False
    assert response.refusal_reason == "INSUFFICIENT_EVIDENCE"
    assert response.citations == []
    assert rag_service.requests[0].question == "OC-MQ-TEST-0904应该如何处理？"


@pytest.mark.asyncio
async def test_assistant_replaces_ungrounded_memory_answer_with_fresh_rag_answer() -> None:
    model = ScriptedAssistantModel(
        [
            _decision_call(
                response_type="ANSWER",
                answer="记忆中的旧答案。",
                diagnostic_status="IDLE",
                requires_user_reply=False,
                call_id="decision-refresh-memory",
            )
        ]
    )
    rag_service = FakeRagService()
    service = LangChainAssistantAgent(
        _settings(),
        rag_service,
        InMemorySaver(),
        model=model,
    )

    response = await service.run_turn(
        _request(
            question="打印页面每隔94毫米出现重复斑点怎么办？",
            turn_id="d521cf26-141c-4ece-b7a9-a970a89892aa",
        )
    )

    assert response.answered is True
    assert response.answer == "每隔94毫米出现重复斑点时，请检查并清洁硒鼓单元。[资料1]"
    assert response.citations[0].document_version_id == 11
    assert rag_service.requests[0].question == "打印页面每隔94毫米出现重复斑点怎么办？"


@pytest.mark.asyncio
async def test_assistant_returns_cached_response_for_duplicate_turn_id() -> None:
    model = ScriptedAssistantModel(
        [
            _decision_call(
                response_type="CLARIFICATION",
                answer="请说明斑点之间的距离。",
                diagnostic_status="COLLECTING_INFORMATION",
                requires_user_reply=True,
                call_id="decision-duplicate",
            )
        ]
    )
    service = LangChainAssistantAgent(
        _settings(),
        FakeRagService(),
        InMemorySaver(),
        model=model,
    )
    request = _request(
        question="打印有斑点",
        turn_id="b7161341-9eba-49db-a444-220264273686",
    )

    first = await service.run_turn(request)
    second = await service.run_turn(request)

    assert second == first
    assert model.remaining_response_count == 0
    assert len(model.messages) == 1


@pytest.mark.asyncio
async def test_assistant_rejects_duplicate_turn_id_with_different_question() -> None:
    model = ScriptedAssistantModel(
        [
            _decision_call(
                response_type="CLARIFICATION",
                answer="请说明斑点之间的距离。",
                diagnostic_status="COLLECTING_INFORMATION",
                requires_user_reply=True,
                call_id="decision-turn-conflict",
            )
        ]
    )
    service = LangChainAssistantAgent(
        _settings(),
        FakeRagService(),
        InMemorySaver(),
        model=model,
    )
    turn_id = "abcb3ae2-9b87-4bc4-b4e0-92dc9ff77ec8"
    await service.run_turn(
        _request(question="打印有斑点", turn_id=turn_id)
    )

    with pytest.raises(ServiceError) as captured:
        await service.run_turn(
            _request(question="设备发生卡纸", turn_id=turn_id)
        )

    assert captured.value.code == "ASSISTANT_TURN_ID_CONFLICT"
    assert len(model.messages) == 1


@pytest.mark.asyncio
async def test_assistant_rejects_reusing_thread_with_different_scope() -> None:
    model = ScriptedAssistantModel(
        [
            _decision_call(
                response_type="CLARIFICATION",
                answer="请补充故障现象。",
                diagnostic_status="COLLECTING_INFORMATION",
                requires_user_reply=True,
                call_id="decision-scope",
            )
        ]
    )
    service = LangChainAssistantAgent(
        _settings(),
        FakeRagService(),
        InMemorySaver(),
        model=model,
    )
    await service.run_turn(
        _request(
            question="打印质量异常",
            turn_id="2bf9a711-a256-4e11-8770-e74e3796c71a",
        )
    )

    with pytest.raises(ServiceError) as captured:
        await service.run_turn(
            _request(
                question="继续排查",
                turn_id="1b69de4c-cba4-42e6-a43b-3334e6f9495b",
                knowledge_base_id=9,
            )
        )

    assert captured.value.code == "ASSISTANT_THREAD_SCOPE_MISMATCH"


class FakeAssistantEndpointService:
    def __init__(self) -> None:
        self.deleted_threads: list[tuple[int, int]] = []

    async def run_turn(self, request: AssistantTurnRequest) -> AssistantTurnResponse:
        return AssistantTurnResponse(
            response_type=AssistantResponseType.CLARIFICATION,
            answer="请说明斑点之间的距离。",
            diagnostic_status=AssistantDiagnosticStatus.COLLECTING_INFORMATION,
            issue_summary="打印页面出现斑点",
            attempted_steps=[],
            observations=[],
            next_question="请说明斑点之间的距离。",
            requires_user_reply=True,
            requires_on_site_service=False,
            answered=False,
            citations=[],
            retrieved_count=0,
            score_threshold=request.score_threshold,
            model_name="test-chat-model",
        )

    async def delete_thread(self, user_id: int, conversation_id: int) -> None:
        self.deleted_threads.append((user_id, conversation_id))


@pytest.fixture
def assistant_client() -> Iterator[TestClient]:
    fake_service = FakeAssistantEndpointService()
    app.dependency_overrides[get_assistant_agent_service] = lambda: fake_service
    try:
        with TestClient(app, raise_server_exceptions=False) as test_client:
            yield test_client
    finally:
        app.dependency_overrides.pop(get_assistant_agent_service, None)


def test_assistant_endpoint_returns_structured_turn(
    assistant_client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = assistant_client.post(
        ASSISTANT_URL,
        headers=auth_headers,
        json=_request(
            question="打印页面有斑点",
            turn_id="8a8a2ed8-f48a-4921-a876-18e482457718",
        ).model_dump(mode="json", by_alias=True),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["responseType"] == "CLARIFICATION"
    assert body["requiresUserReply"] is True
    assert body["answered"] is False


def test_assistant_endpoint_deletes_owned_checkpoint_thread(
    assistant_client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    fake_service = app.dependency_overrides[get_assistant_agent_service]()
    response = assistant_client.delete(
        "/api/v1/internal/assistant/threads/7/15",
        headers=auth_headers,
    )

    assert response.status_code == 204
    assert response.content == b""
    assert fake_service.deleted_threads == [(7, 15)]


def test_assistant_endpoint_requires_internal_token(client: TestClient) -> None:
    response = client.post(
        ASSISTANT_URL,
        json=_request(
            question="打印页面有斑点",
            turn_id="e480b8bb-2537-4c44-b080-58515520861b",
        ).model_dump(mode="json", by_alias=True),
    )

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_assistant_endpoint_reports_disabled_memory_service(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        ASSISTANT_URL,
        headers=auth_headers,
        json=_request(
            question="打印页面有斑点",
            turn_id="29797082-6923-4fa3-86ce-3ed2d6c401ff",
        ).model_dump(mode="json", by_alias=True),
    )

    assert response.status_code == 503
    assert response.json()["code"] == "ASSISTANT_MEMORY_UNAVAILABLE"
