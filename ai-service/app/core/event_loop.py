import asyncio
import sys


def officecare_event_loop_factory() -> asyncio.AbstractEventLoop:
    """Return a psycopg-compatible event loop on Windows."""
    if sys.platform == "win32":
        return asyncio.SelectorEventLoop()
    return asyncio.new_event_loop()
