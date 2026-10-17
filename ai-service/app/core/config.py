from functools import lru_cache
from pathlib import Path
import re
from urllib.parse import urlparse

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


SERVICE_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """Runtime settings loaded from environment variables or ai-service/.env."""

    model_config = SettingsConfigDict(
        env_file=SERVICE_ROOT / ".env",
        env_file_encoding="utf-8",
        env_prefix="OFFICECARE_",
        extra="ignore",
    )

    environment: str = "development"
    ai_service_token: SecretStr | None = Field(
        default=None,
        validation_alias="OFFICECARE_AI_SERVICE_TOKEN",
    )
    max_document_size_mb: int = 30
    document_parser: str = "local"
    mineru_base_url: str = "https://mineru.net"
    mineru_api_token: SecretStr | None = None
    mineru_model_version: str = "vlm"
    mineru_language: str = "ch"
    mineru_is_ocr: bool = False
    mineru_enable_table: bool = True
    mineru_enable_formula: bool = False
    mineru_request_timeout_seconds: float = 60
    mineru_poll_interval_seconds: float = 5
    mineru_timeout_seconds: float = 1800
    mineru_max_result_size_mb: int = 200
    mineru_max_pages_per_part: int = 200
    embedding_base_url: str = ""
    embedding_api_key: SecretStr | None = None
    embedding_model: str = ""
    embedding_dimension: int = 1024
    embedding_batch_size: int = 8
    embedding_timeout_seconds: float = 60
    chat_base_url: str = "https://api.deepseek.com"
    chat_api_key: SecretStr | None = None
    chat_model: str = "deepseek-v4-pro"
    chat_temperature: float = 0.1
    chat_timeout_seconds: float = 120
    chat_max_tokens: int = 1200
    agent_max_steps: int = 8
    agent_memory_enabled: bool = False
    agent_postgres_dsn: SecretStr | None = None
    agent_postgres_pool_size: int = 5
    agent_recent_message_limit: int = 12
    agent_summary_trigger_messages: int = 20
    langsmith_enabled: bool = False
    langsmith_api_key: SecretStr | None = None
    langsmith_endpoint: str = "https://api.smith.langchain.com"
    langsmith_project: str = "officecare-ai-local"
    langsmith_hide_inputs: bool = True
    langsmith_hide_outputs: bool = True
    redis_enabled: bool = False
    redis_host: str = "127.0.0.1"
    redis_port: int = 6381
    redis_database: int = 0
    redis_password: SecretStr | None = None
    redis_connect_timeout_seconds: float = 2
    redis_socket_timeout_seconds: float = 2
    rag_cache_ttl_seconds: int = 3600
    rag_cache_namespace: str = "officecare:ai:rag-answer:v1"
    milvus_uri: str = "http://127.0.0.1:19530"
    milvus_token: SecretStr | None = None
    milvus_database: str = "default"
    milvus_collection: str = "officecare_document_chunks_v1"
    milvus_timeout_seconds: float = 10
    chunk_size: int = 800
    chunk_overlap: int = 100
    rag_top_k: int = 5
    rag_score_threshold: float = 0.45
    rag_dense_candidate_limit: int = 100
    rag_lexical_scan_limit: int = 5000
    rag_lexical_candidate_limit: int = 100
    rag_lexical_score_threshold: float = 0.18
    rag_neighbor_window: int = 2
    rag_context_max_chars: int = 8000

    @field_validator("environment")
    @classmethod
    def normalize_environment(cls, value: str) -> str:
        value = value.strip()
        return value or "development"

    @field_validator("max_document_size_mb")
    @classmethod
    def validate_max_document_size(cls, value: int) -> int:
        if not 1 <= value <= 200:
            raise ValueError("must be between 1 and 200")
        return value

    @field_validator(
        "mineru_base_url",
        "mineru_model_version",
        "mineru_language",
        "embedding_base_url",
        "embedding_model",
        "chat_base_url",
        "chat_model",
        "langsmith_endpoint",
        "langsmith_project",
        "redis_host",
        "rag_cache_namespace",
        "milvus_uri",
        "milvus_database",
        "milvus_collection",
    )
    @classmethod
    def normalize_text_setting(cls, value: str) -> str:
        return value.strip()

    @field_validator("redis_host", "rag_cache_namespace")
    @classmethod
    def validate_non_empty_redis_text(cls, value: str) -> str:
        if not value:
            raise ValueError("must not be empty")
        return value

    @field_validator("rag_cache_namespace")
    @classmethod
    def validate_rag_cache_namespace(cls, value: str) -> str:
        if not re.fullmatch(r"[A-Za-z0-9:_-]{1,128}", value):
            raise ValueError(
                "must contain only letters, digits, colon, underscore, or hyphen"
            )
        return value

    @field_validator("document_parser")
    @classmethod
    def validate_document_parser(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized not in {"local", "mineru"}:
            raise ValueError("must be either local or mineru")
        return normalized

    @field_validator("mineru_model_version")
    @classmethod
    def validate_mineru_model_version(cls, value: str) -> str:
        normalized = value.strip().lower()
        if normalized not in {"pipeline", "vlm"}:
            raise ValueError("must be either pipeline or vlm")
        return normalized

    @field_validator("mineru_language")
    @classmethod
    def validate_mineru_language(cls, value: str) -> str:
        normalized = value.strip()
        if not 1 <= len(normalized) <= 32:
            raise ValueError("must contain between 1 and 32 characters")
        return normalized

    @field_validator("mineru_max_result_size_mb")
    @classmethod
    def validate_mineru_max_result_size(cls, value: int) -> int:
        if not 1 <= value <= 500:
            raise ValueError("must be between 1 and 500")
        return value

    @field_validator("mineru_max_pages_per_part")
    @classmethod
    def validate_mineru_max_pages_per_part(cls, value: int) -> int:
        if not 1 <= value <= 200:
            raise ValueError("must be between 1 and 200")
        return value

    @field_validator("embedding_dimension")
    @classmethod
    def validate_embedding_dimension(cls, value: int) -> int:
        if not 1 <= value <= 32768:
            raise ValueError("must be between 1 and 32768")
        return value

    @field_validator("embedding_batch_size")
    @classmethod
    def validate_embedding_batch_size(cls, value: int) -> int:
        if not 1 <= value <= 8:
            raise ValueError("must be between 1 and 8")
        return value

    @field_validator(
        "mineru_request_timeout_seconds",
        "mineru_poll_interval_seconds",
        "embedding_timeout_seconds",
        "chat_timeout_seconds",
        "milvus_timeout_seconds",
    )
    @classmethod
    def validate_positive_timeout(cls, value: float) -> float:
        if not 0 < value <= 600:
            raise ValueError("must be greater than 0 and at most 600 seconds")
        return value

    @field_validator("mineru_timeout_seconds")
    @classmethod
    def validate_mineru_timeout(cls, value: float) -> float:
        if not 30 <= value <= 7200:
            raise ValueError("must be between 30 and 7200 seconds")
        return value

    @field_validator("chat_temperature")
    @classmethod
    def validate_chat_temperature(cls, value: float) -> float:
        if not 0 <= value <= 2:
            raise ValueError("must be between 0 and 2")
        return value

    @field_validator("chat_max_tokens")
    @classmethod
    def validate_chat_max_tokens(cls, value: int) -> int:
        if not 128 <= value <= 8192:
            raise ValueError("must be between 128 and 8192")
        return value

    @field_validator("agent_max_steps")
    @classmethod
    def validate_agent_max_steps(cls, value: int) -> int:
        if not 4 <= value <= 12:
            raise ValueError("must be between 4 and 12")
        return value

    @field_validator("agent_postgres_pool_size")
    @classmethod
    def validate_agent_postgres_pool_size(cls, value: int) -> int:
        if not 1 <= value <= 20:
            raise ValueError("must be between 1 and 20")
        return value

    @field_validator("agent_recent_message_limit")
    @classmethod
    def validate_agent_recent_message_limit(cls, value: int) -> int:
        if not 4 <= value <= 40:
            raise ValueError("must be between 4 and 40")
        return value

    @field_validator("agent_summary_trigger_messages")
    @classmethod
    def validate_agent_summary_trigger_messages(cls, value: int) -> int:
        if not 8 <= value <= 200:
            raise ValueError("must be between 8 and 200")
        return value

    @model_validator(mode="after")
    def validate_agent_memory_configuration(self) -> "Settings":
        dsn = self.agent_postgres_connection_string()
        if self.agent_memory_enabled and not dsn:
            raise ValueError(
                "agent_postgres_dsn must be configured when agent_memory_enabled is true"
            )
        if dsn and not dsn.startswith(("postgresql://", "postgres://")):
            raise ValueError(
                "agent_postgres_dsn must use the postgres or postgresql scheme"
            )
        if self.agent_summary_trigger_messages <= self.agent_recent_message_limit:
            raise ValueError(
                "agent_summary_trigger_messages must be greater than "
                "agent_recent_message_limit"
            )
        return self

    @field_validator("langsmith_endpoint")
    @classmethod
    def validate_langsmith_endpoint(cls, value: str) -> str:
        parsed = urlparse(value)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise ValueError("must be an absolute HTTP or HTTPS URL")
        return value.rstrip("/")

    @field_validator("langsmith_project")
    @classmethod
    def validate_langsmith_project(cls, value: str) -> str:
        if not 1 <= len(value) <= 128 or any(char.isspace() for char in value):
            raise ValueError(
                "must contain between 1 and 128 non-whitespace characters"
            )
        return value

    @field_validator("redis_port")
    @classmethod
    def validate_redis_port(cls, value: int) -> int:
        if not 1 <= value <= 65535:
            raise ValueError("must be between 1 and 65535")
        return value

    @field_validator("redis_database")
    @classmethod
    def validate_redis_database(cls, value: int) -> int:
        if not 0 <= value <= 255:
            raise ValueError("must be between 0 and 255")
        return value

    @field_validator(
        "redis_connect_timeout_seconds",
        "redis_socket_timeout_seconds",
    )
    @classmethod
    def validate_redis_timeout(cls, value: float) -> float:
        if not 0.1 <= value <= 30:
            raise ValueError("must be between 0.1 and 30 seconds")
        return value

    @field_validator("rag_cache_ttl_seconds")
    @classmethod
    def validate_rag_cache_ttl(cls, value: int) -> int:
        if not 60 <= value <= 604800:
            raise ValueError("must be between 60 and 604800 seconds")
        return value

    @field_validator("chunk_size")
    @classmethod
    def validate_chunk_size(cls, value: int) -> int:
        if not 200 <= value <= 4000:
            raise ValueError("must be between 200 and 4000")
        return value

    @model_validator(mode="after")
    def validate_chunk_overlap(self) -> "Settings":
        if not 0 <= self.chunk_overlap < self.chunk_size:
            raise ValueError("chunk_overlap must be non-negative and less than chunk_size")
        return self

    @model_validator(mode="after")
    def validate_langsmith_configuration(self) -> "Settings":
        if self.langsmith_enabled and not self.langsmith_key():
            raise ValueError(
                "langsmith_api_key must be configured when langsmith_enabled is true"
            )
        return self

    @field_validator("rag_top_k")
    @classmethod
    def validate_rag_top_k(cls, value: int) -> int:
        if not 1 <= value <= 10:
            raise ValueError("must be between 1 and 10")
        return value

    @field_validator("rag_score_threshold")
    @classmethod
    def validate_rag_score_threshold(cls, value: float) -> float:
        if not -1 <= value <= 1:
            raise ValueError("must be between -1 and 1")
        return value

    @field_validator("rag_dense_candidate_limit")
    @classmethod
    def validate_rag_dense_candidate_limit(cls, value: int) -> int:
        if not 10 <= value <= 500:
            raise ValueError("must be between 10 and 500")
        return value

    @field_validator("rag_lexical_scan_limit")
    @classmethod
    def validate_rag_lexical_scan_limit(cls, value: int) -> int:
        if not 100 <= value <= 16384:
            raise ValueError("must be between 100 and 16384")
        return value

    @field_validator("rag_lexical_candidate_limit")
    @classmethod
    def validate_rag_lexical_candidate_limit(cls, value: int) -> int:
        if not 10 <= value <= 500:
            raise ValueError("must be between 10 and 500")
        return value

    @field_validator("rag_lexical_score_threshold")
    @classmethod
    def validate_rag_lexical_score_threshold(cls, value: float) -> float:
        if not 0 <= value <= 1:
            raise ValueError("must be between 0 and 1")
        return value

    @field_validator("rag_neighbor_window")
    @classmethod
    def validate_rag_neighbor_window(cls, value: int) -> int:
        if not 0 <= value <= 3:
            raise ValueError("must be between 0 and 3")
        return value

    @field_validator("rag_context_max_chars")
    @classmethod
    def validate_rag_context_max_chars(cls, value: int) -> int:
        if not 1000 <= value <= 30000:
            raise ValueError("must be between 1000 and 30000")
        return value

    @property
    def max_document_size_bytes(self) -> int:
        return self.max_document_size_mb * 1024 * 1024

    @property
    def mineru_max_result_size_bytes(self) -> int:
        return self.mineru_max_result_size_mb * 1024 * 1024

    def internal_token(self) -> str | None:
        if self.ai_service_token is None:
            return None
        value = self.ai_service_token.get_secret_value().strip()
        return value or None

    def embedding_key(self) -> str | None:
        if self.embedding_api_key is None:
            return None
        value = self.embedding_api_key.get_secret_value().strip()
        return value or None

    def mineru_token(self) -> str | None:
        if self.mineru_api_token is None:
            return None
        value = self.mineru_api_token.get_secret_value().strip()
        return value or None

    def chat_key(self) -> str | None:
        if self.chat_api_key is None:
            return None
        value = self.chat_api_key.get_secret_value().strip()
        return value or None

    def langsmith_key(self) -> str | None:
        if self.langsmith_api_key is None:
            return None
        value = self.langsmith_api_key.get_secret_value().strip()
        return value or None

    def agent_postgres_connection_string(self) -> str | None:
        if self.agent_postgres_dsn is None:
            return None
        value = self.agent_postgres_dsn.get_secret_value().strip()
        return value or None

    def redis_secret(self) -> str | None:
        if self.redis_password is None:
            return None
        value = self.redis_password.get_secret_value().strip()
        return value or None

    def milvus_auth_token(self) -> str | None:
        if self.milvus_token is None:
            return None
        value = self.milvus_token.get_secret_value().strip()
        return value or None


@lru_cache
def get_settings() -> Settings:
    return Settings()
