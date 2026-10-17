import asyncio
from pathlib import Path
import sys


SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

from app.core.config import get_settings
from app.core.event_loop import officecare_event_loop_factory


async def main() -> None:
    settings = get_settings()
    dsn = settings.agent_postgres_connection_string()
    if not dsn:
        raise SystemExit(
            "OFFICECARE_AGENT_POSTGRES_DSN is not configured in ai-service/.env."
        )

    async with AsyncPostgresSaver.from_conn_string(dsn) as checkpointer:
        await checkpointer.setup()
    print("Assistant Agent checkpoint schema is ready.")


if __name__ == "__main__":
    with asyncio.Runner(loop_factory=officecare_event_loop_factory) as runner:
        runner.run(main())
