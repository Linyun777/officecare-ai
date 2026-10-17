from contextlib import asynccontextmanager
import logging
from typing import AsyncIterator

from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

from app.core.config import Settings
from app.services.assistant_agent import LangChainAssistantAgent
from app.services.rag_answering import get_rag_answering_service


logger = logging.getLogger(__name__)


@asynccontextmanager
async def assistant_agent_runtime(
    settings: Settings,
) -> AsyncIterator[LangChainAssistantAgent | None]:
    """Own the PostgreSQL pool and Assistant Agent for the app lifespan."""
    if not settings.agent_memory_enabled:
        logger.info("Assistant Agent memory is disabled.")
        yield None
        return

    dsn = settings.agent_postgres_connection_string()
    if not dsn:
        raise RuntimeError("Assistant Agent PostgreSQL DSN is not configured.")

    pool = AsyncConnectionPool(
        conninfo=dsn,
        min_size=1,
        max_size=settings.agent_postgres_pool_size,
        open=False,
        kwargs={
            "autocommit": True,
            "prepare_threshold": 0,
            "row_factory": dict_row,
        },
        name="officecare-assistant-checkpoint",
    )
    await pool.open(wait=True, timeout=10)
    try:
        checkpointer = AsyncPostgresSaver(pool)
        # Schema creation is deliberately handled by the explicit setup script.
        # Reading a reserved thread verifies both connectivity and migrations.
        await checkpointer.aget_tuple(
            {
                "configurable": {
                    "thread_id": "officecare:assistant:readiness",
                    "checkpoint_ns": "",
                }
            }
        )
        yield LangChainAssistantAgent(
            settings=settings,
            rag_service=get_rag_answering_service(),
            checkpointer=checkpointer,
        )
    finally:
        await pool.close()
