from contextlib import contextmanager

from app.core.config import Settings
from app.core import observability


def test_configure_langsmith_environment_keeps_tracing_disabled_by_default() -> None:
    environ = {"LANGSMITH_API_KEY": "stale-key"}

    observability.configure_langsmith_environment(
        Settings(_env_file=None),
        environ=environ,
    )

    assert environ["LANGSMITH_TRACING"] == "false"
    assert environ["LANGSMITH_HIDE_INPUTS"] == "true"
    assert environ["LANGSMITH_HIDE_OUTPUTS"] == "true"
    assert environ["LANGSMITH_HIDE_METADATA"] == "false"
    assert "LANGSMITH_API_KEY" not in environ


def test_configure_langsmith_environment_maps_enabled_officecare_settings() -> None:
    environ: dict[str, str] = {}

    observability.configure_langsmith_environment(
        Settings(
            _env_file=None,
            langsmith_enabled=True,
            langsmith_api_key="test-langsmith-key",
            langsmith_project="officecare-ai-test",
            langsmith_hide_outputs=False,
        ),
        environ=environ,
    )

    assert environ == {
        "LANGSMITH_TRACING": "true",
        "LANGSMITH_ENDPOINT": "https://api.smith.langchain.com",
        "LANGSMITH_PROJECT": "officecare-ai-test",
        "LANGSMITH_HIDE_INPUTS": "true",
        "LANGSMITH_HIDE_OUTPUTS": "false",
        "LANGSMITH_HIDE_METADATA": "false",
        "LANGSMITH_API_KEY": "test-langsmith-key",
    }


def test_observe_operation_is_a_noop_when_langsmith_is_disabled(monkeypatch) -> None:
    monkeypatch.setattr(
        observability,
        "get_settings",
        lambda: Settings(_env_file=None),
    )

    def fail_if_called(*args, **kwargs):
        raise AssertionError("LangSmith must not be called while disabled")

    monkeypatch.setattr(observability.ls, "trace", fail_if_called)

    with observability.observe_operation("officecare.test") as trace:
        trace.set_result(answered=True)

    assert trace.enabled is False


def test_observe_operation_fails_open_when_langsmith_setup_fails(monkeypatch) -> None:
    settings = Settings(
        _env_file=None,
        langsmith_enabled=True,
        langsmith_api_key="test-langsmith-key",
    )
    monkeypatch.setattr(observability, "get_settings", lambda: settings)

    @contextmanager
    def fake_tracing_context(**kwargs):
        yield

    def unavailable_trace(*args, **kwargs):
        raise RuntimeError("LangSmith is unavailable")

    monkeypatch.setattr(observability.ls, "tracing_context", fake_tracing_context)
    monkeypatch.setattr(observability.ls, "trace", unavailable_trace)

    with observability.observe_operation("officecare.rag.answer") as trace:
        business_result = "still-available"

    assert trace.enabled is False
    assert business_result == "still-available"


def test_observe_operation_keeps_only_safe_metadata(monkeypatch) -> None:
    settings = Settings(
        _env_file=None,
        environment="test",
        langsmith_enabled=True,
        langsmith_api_key="test-langsmith-key",
        langsmith_project="officecare-ai-test",
    )
    monkeypatch.setattr(observability, "get_settings", lambda: settings)
    captured: dict[str, object] = {}

    @contextmanager
    def fake_tracing_context(**kwargs):
        captured["context"] = kwargs
        yield

    class FakeRun:
        def end(self, **kwargs) -> None:
            captured["end"] = kwargs

    @contextmanager
    def fake_trace(*args, **kwargs):
        captured["trace_args"] = args
        captured["trace_kwargs"] = kwargs
        yield FakeRun()

    monkeypatch.setattr(observability.ls, "tracing_context", fake_tracing_context)
    monkeypatch.setattr(observability.ls, "trace", fake_trace)

    with observability.observe_operation(
        "officecare.rag.answer",
        trace_id="officecare-trace-001",
        tags=("rag",),
        metadata={
            "knowledge_base_id": 1,
            "model_id": 2,
            "question": "不应上传的客户问题",
        },
    ) as trace:
        trace.set_result(
            answered=True,
            citation_count=2,
            answer="不应上传的模型回答",
        )

    trace_kwargs = captured["trace_kwargs"]
    assert trace.enabled is True
    assert trace_kwargs["inputs"] == {"operation": "officecare.rag.answer"}
    assert trace_kwargs["metadata"]["officecare_trace_id"] == (
        "officecare-trace-001"
    )
    assert trace_kwargs["metadata"]["knowledge_base_id"] == 1
    assert "question" not in trace_kwargs["metadata"]
    assert captured["end"]["metadata"] == {
        "answered": True,
        "citation_count": 2,
    }
    assert "answer" not in captured["end"]["metadata"]


def test_observe_operation_records_content_when_explicitly_enabled(
    monkeypatch,
) -> None:
    settings = Settings(
        _env_file=None,
        environment="test",
        langsmith_enabled=True,
        langsmith_api_key="test-langsmith-key",
        langsmith_project="officecare-ai-test",
        langsmith_hide_inputs=False,
        langsmith_hide_outputs=False,
    )
    monkeypatch.setattr(observability, "get_settings", lambda: settings)
    captured: dict[str, object] = {}

    @contextmanager
    def fake_tracing_context(**kwargs):
        yield

    class FakeRun:
        def end(self, **kwargs) -> None:
            captured["end"] = kwargs

    @contextmanager
    def fake_trace(*args, **kwargs):
        captured["trace_kwargs"] = kwargs
        yield FakeRun()

    monkeypatch.setattr(observability.ls, "tracing_context", fake_tracing_context)
    monkeypatch.setattr(observability.ls, "trace", fake_trace)

    with observability.observe_operation(
        "officecare.rag.answer",
        inputs={"question": "打印机卡纸怎么办？"},
    ) as trace:
        trace.set_outputs(answered=True, answer="请先关闭设备电源。")

    assert captured["trace_kwargs"]["inputs"] == {
        "operation": "officecare.rag.answer",
        "question": "打印机卡纸怎么办？",
    }
    assert captured["end"]["outputs"] == {
        "answered": True,
        "answer": "请先关闭设备电源。",
    }
