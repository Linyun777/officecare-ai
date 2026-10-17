import pytest
from pydantic import ValidationError

from app.core.config import Settings


def test_embedding_batch_size_defaults_to_provider_safe_size() -> None:
    assert Settings.model_fields["embedding_batch_size"].default == 8


def test_embedding_batch_size_rejects_values_above_provider_safe_size() -> None:
    with pytest.raises(ValidationError):
        Settings(_env_file=None, embedding_batch_size=9)


def test_document_parser_accepts_only_known_providers() -> None:
    assert Settings(_env_file=None, document_parser="mineru").document_parser == "mineru"

    with pytest.raises(ValidationError):
        Settings(_env_file=None, document_parser="unknown")


def test_mineru_model_version_accepts_pipeline_or_vlm() -> None:
    assert Settings(_env_file=None, mineru_model_version="VLM").mineru_model_version == "vlm"

    with pytest.raises(ValidationError):
        Settings(_env_file=None, mineru_model_version="unsupported")


def test_mineru_pdf_part_limit_cannot_exceed_provider_limit() -> None:
    assert Settings(_env_file=None).mineru_max_pages_per_part == 200

    with pytest.raises(ValidationError):
        Settings(_env_file=None, mineru_max_pages_per_part=201)


def test_hybrid_retrieval_limits_are_bounded() -> None:
    settings = Settings(_env_file=None)

    assert settings.rag_dense_candidate_limit == 100
    assert settings.rag_lexical_scan_limit == 5000
    assert settings.rag_lexical_candidate_limit == 100
    assert settings.rag_lexical_score_threshold == 0.18
    assert settings.rag_neighbor_window == 2

    with pytest.raises(ValidationError):
        Settings(_env_file=None, rag_lexical_scan_limit=20000)


def test_agent_step_limit_is_bounded() -> None:
    assert Settings(_env_file=None).agent_max_steps == 8

    with pytest.raises(ValidationError):
        Settings(_env_file=None, agent_max_steps=3)

    with pytest.raises(ValidationError):
        Settings(_env_file=None, agent_max_steps=13)


def test_agent_memory_is_opt_in_and_requires_postgres_dsn() -> None:
    settings = Settings(_env_file=None)

    assert settings.agent_memory_enabled is False
    assert settings.agent_postgres_connection_string() is None
    assert settings.agent_postgres_pool_size == 5
    assert settings.agent_recent_message_limit == 12
    assert settings.agent_summary_trigger_messages == 20

    with pytest.raises(ValidationError):
        Settings(_env_file=None, agent_memory_enabled=True)

    enabled = Settings(
        _env_file=None,
        agent_memory_enabled=True,
        agent_postgres_dsn="postgresql://officecare:test@localhost:5432/agent",
    )
    assert enabled.agent_postgres_connection_string() == (
        "postgresql://officecare:test@localhost:5432/agent"
    )

    with pytest.raises(
        ValidationError,
        match="must use the postgres or postgresql scheme",
    ):
        Settings(_env_file=None, agent_postgres_dsn="mysql://localhost/agent")


def test_agent_summary_must_keep_fewer_messages_than_trigger() -> None:
    with pytest.raises(ValidationError):
        Settings(
            _env_file=None,
            agent_recent_message_limit=20,
            agent_summary_trigger_messages=20,
        )


def test_langsmith_defaults_to_disabled_with_private_payloads() -> None:
    settings = Settings(_env_file=None)

    assert settings.langsmith_enabled is False
    assert settings.langsmith_hide_inputs is True
    assert settings.langsmith_hide_outputs is True
    assert settings.langsmith_project == "officecare-ai-local"


def test_langsmith_requires_a_local_api_key_only_when_enabled() -> None:
    with pytest.raises(ValidationError):
        Settings(_env_file=None, langsmith_enabled=True)

    settings = Settings(
        _env_file=None,
        langsmith_enabled=True,
        langsmith_api_key="test-langsmith-key",
        langsmith_endpoint="https://api.smith.langchain.com/",
        langsmith_project=" officecare-ai-test ",
    )

    assert settings.langsmith_key() == "test-langsmith-key"
    assert settings.langsmith_endpoint == "https://api.smith.langchain.com"
    assert settings.langsmith_project == "officecare-ai-test"


def test_langsmith_rejects_invalid_endpoint_and_project() -> None:
    with pytest.raises(ValidationError):
        Settings(_env_file=None, langsmith_endpoint="smith.langchain.com")

    with pytest.raises(ValidationError):
        Settings(_env_file=None, langsmith_project="officecare ai")


def test_redis_rag_cache_defaults_to_disabled_and_validates_bounds() -> None:
    settings = Settings(_env_file=None)

    assert settings.redis_enabled is False
    assert settings.redis_port == 6381
    assert settings.redis_database == 0
    assert settings.rag_cache_ttl_seconds == 3600

    with pytest.raises(ValidationError):
        Settings(_env_file=None, redis_port=0)

    with pytest.raises(ValidationError):
        Settings(_env_file=None, redis_database=-1)

    with pytest.raises(ValidationError):
        Settings(_env_file=None, rag_cache_ttl_seconds=59)

    with pytest.raises(ValidationError):
        Settings(_env_file=None, rag_cache_namespace="officecare:rag:*")
