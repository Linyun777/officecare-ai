from collections.abc import Iterator, Mapping, MutableMapping, Sequence
from contextlib import ExitStack, contextmanager
from dataclasses import dataclass, field
import logging
import os
import sys
from typing import Any

import langsmith as ls
from langsmith import utils as langsmith_utils

from app.core.config import Settings, get_settings


logger = logging.getLogger(__name__)

# Only operational, non-content fields are allowed in trace metadata. Questions,
# customer/device details, document text and model answers are intentionally absent.
SAFE_METADATA_KEYS = frozenset(
    {
        "answered",
        "brand_id",
        "citation_count",
        "document_version_count",
        "endpoint",
        "environment",
        "knowledge_base_id",
        "model_id",
        "model_name",
        "officecare_trace_id",
        "operation",
        "refusal_reason",
        "requires_on_site_service",
        "retrieved_count",
        "score_threshold",
        "tool_count",
        "top_k",
        "top_score",
    }
)
SAFE_VALUE_TYPES = (str, int, float, bool, type(None))


def configure_langsmith_environment(
    settings: Settings,
    *,
    environ: MutableMapping[str, str] | None = None,
) -> None:
    """Bridge OfficeCare settings to the variables consumed by LangSmith."""

    target = os.environ if environ is None else environ
    target["LANGSMITH_TRACING"] = _boolean_text(settings.langsmith_enabled)
    target["LANGSMITH_ENDPOINT"] = settings.langsmith_endpoint
    target["LANGSMITH_PROJECT"] = settings.langsmith_project
    target["LANGSMITH_HIDE_INPUTS"] = _boolean_text(
        settings.langsmith_hide_inputs
    )
    target["LANGSMITH_HIDE_OUTPUTS"] = _boolean_text(
        settings.langsmith_hide_outputs
    )
    # Correlation and aggregate result fields are deliberately safe metadata.
    target["LANGSMITH_HIDE_METADATA"] = "false"

    api_key = settings.langsmith_key()
    if settings.langsmith_enabled and api_key:
        target["LANGSMITH_API_KEY"] = api_key
    elif environ is not None:
        target.pop("LANGSMITH_API_KEY", None)

    if target is os.environ:
        # LangSmith caches environment lookups. Clear it after loading .env so
        # IDE/debug restarts and tests always see the current OfficeCare values.
        langsmith_utils.get_env_var.cache_clear()
        logger.info(
            "LangSmith tracing configured. enabled=%s project=%s "
            "hide_inputs=%s hide_outputs=%s",
            settings.langsmith_enabled,
            settings.langsmith_project,
            settings.langsmith_hide_inputs,
            settings.langsmith_hide_outputs,
        )


@dataclass(slots=True)
class OfficeCareTrace:
    """Collect safe result metadata for the active LangSmith run."""

    _run: Any | None = None
    _capture_outputs: bool = False
    _result_metadata: dict[str, Any] = field(default_factory=dict)
    _outputs: dict[str, Any] = field(default_factory=dict)

    @property
    def enabled(self) -> bool:
        return self._run is not None

    def set_result(self, **metadata: Any) -> None:
        self._result_metadata.update(_safe_metadata(metadata))

    def set_outputs(self, **outputs: Any) -> None:
        """Attach business outputs only when local content tracing is enabled."""

        if self._capture_outputs:
            self._outputs.update(outputs)


@contextmanager
def observe_operation(
    name: str,
    *,
    trace_id: str | None = None,
    run_type: str = "chain",
    tags: Sequence[str] = (),
    metadata: Mapping[str, Any] | None = None,
    inputs: Mapping[str, Any] | None = None,
) -> Iterator[OfficeCareTrace]:
    """Create an optional, privacy-safe LangSmith span around an operation."""

    settings = get_settings()
    if not settings.langsmith_enabled:
        yield OfficeCareTrace()
        return

    safe_metadata = _safe_metadata(metadata or {})
    safe_metadata.update(
        {
            "environment": settings.environment,
            "operation": name,
        }
    )
    if trace_id:
        safe_metadata["officecare_trace_id"] = trace_id

    safe_tags = ["officecare", settings.environment, *tags]
    trace_inputs: dict[str, Any] = {"operation": name}
    if not settings.langsmith_hide_inputs and inputs:
        trace_inputs.update(inputs)
    stack = ExitStack()
    try:
        stack.enter_context(
            ls.tracing_context(
                enabled=True,
                project_name=settings.langsmith_project,
                tags=safe_tags,
                metadata=safe_metadata,
            )
        )
        run = stack.enter_context(
            ls.trace(
                name=name,
                run_type=run_type,
                inputs=trace_inputs,
                tags=safe_tags,
                metadata=safe_metadata,
                project_name=settings.langsmith_project,
            )
        )
    except Exception:
        _close_trace_stack(stack)
        logger.warning(
            "LangSmith trace setup failed; continuing without tracing. "
            "operation=%s",
            name,
            exc_info=True,
        )
        yield OfficeCareTrace()
        return

    trace = OfficeCareTrace(
        _run=run,
        _capture_outputs=not settings.langsmith_hide_outputs,
    )
    try:
        yield trace
    except BaseException:
        error_info = sys.exc_info()
        try:
            stack.__exit__(*error_info)
        except Exception:
            logger.warning(
                "LangSmith trace error reporting failed. operation=%s",
                name,
                exc_info=True,
            )
        raise
    else:
        try:
            run.end(
                outputs=trace._outputs or {"operationCompleted": True},
                metadata=trace._result_metadata,
            )
            stack.close()
        except Exception:
            logger.warning(
                "LangSmith trace submission failed; business result is unaffected. "
                "operation=%s",
                name,
                exc_info=True,
            )


def _safe_metadata(metadata: Mapping[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in metadata.items()
        if key in SAFE_METADATA_KEYS and isinstance(value, SAFE_VALUE_TYPES)
    }


def _boolean_text(value: bool) -> str:
    return "true" if value else "false"


def _close_trace_stack(stack: ExitStack) -> None:
    try:
        stack.close()
    except Exception:
        logger.debug("Failed to close a partial LangSmith trace stack.", exc_info=True)
