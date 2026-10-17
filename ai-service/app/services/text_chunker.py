import hashlib
import re
from dataclasses import dataclass

from app.schemas.document import BlockType, DocumentBlock
from app.services.retrieval_quality import is_navigation_only


BREAK_CHARACTERS = "\n。！？；.!?;"
WHITESPACE_PATTERN = re.compile(r"[ \t\f\v]+")


@dataclass(frozen=True, slots=True)
class TextChunk:
    chunk_index: int
    page_number: int | None
    block_start: int
    block_end: int
    heading: str
    text: str
    chunk_hash: str


@dataclass(slots=True)
class _ChunkGroup:
    heading: str
    page_number: int | None
    block_start: int
    block_end: int
    bodies: list[str]


def chunk_document(
    blocks: list[DocumentBlock],
    *,
    max_chars: int,
    overlap_chars: int,
) -> list[TextChunk]:
    """Create deterministic, heading-aware chunks from ordered parsed blocks."""
    groups = _group_blocks(blocks)
    chunks: list[TextChunk] = []

    for group in groups:
        heading = _limit_heading(group.heading, max_chars)
        prefix = f"{heading}\n\n" if heading else ""
        available_chars = max(80, max_chars - len(prefix))
        body = "\n\n".join(group.bodies).strip()
        text_to_split = body or heading
        if not text_to_split:
            continue

        for part in _split_text(
            text_to_split,
            max_chars=available_chars if body else max_chars,
            overlap_chars=min(overlap_chars, max(0, available_chars - 1)),
        ):
            text = f"{prefix}{part}".strip() if body else part.strip()
            if not text or is_navigation_only(text, heading=heading):
                continue
            chunks.append(
                TextChunk(
                    chunk_index=len(chunks),
                    page_number=group.page_number,
                    block_start=group.block_start,
                    block_end=group.block_end,
                    heading=heading,
                    text=text,
                    chunk_hash=hashlib.sha256(text.encode("utf-8")).hexdigest(),
                )
            )

    return chunks


def _group_blocks(blocks: list[DocumentBlock]) -> list[_ChunkGroup]:
    groups: list[_ChunkGroup] = []
    current_group: _ChunkGroup | None = None
    current_heading = ""
    heading_sequence: int | None = None

    def flush() -> None:
        nonlocal current_group
        if current_group is not None:
            groups.append(current_group)
            current_group = None

    for block in blocks:
        text = _normalize_text(block.text)
        if not text:
            continue

        if block.block_type == BlockType.HEADING:
            flush()
            current_heading = text
            heading_sequence = block.sequence
            continue

        page_number = block.page_number
        if (
            current_group is None
            or current_group.heading != current_heading
            or current_group.page_number != page_number
        ):
            flush()
            current_group = _ChunkGroup(
                heading=current_heading,
                page_number=page_number,
                block_start=heading_sequence or block.sequence,
                block_end=block.sequence,
                bodies=[],
            )
        current_group.block_end = block.sequence
        current_group.bodies.append(text)

    flush()

    if heading_sequence is not None and (
        not groups or groups[-1].block_end < heading_sequence
    ):
        groups.append(
            _ChunkGroup(
                heading="",
                page_number=None,
                block_start=heading_sequence,
                block_end=heading_sequence,
                bodies=[current_heading],
            )
        )
    return groups


def _normalize_text(text: str) -> str:
    normalized_lines = []
    for line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        normalized = WHITESPACE_PATTERN.sub(" ", line).strip()
        if normalized:
            normalized_lines.append(normalized)
    return "\n".join(normalized_lines)


def _limit_heading(heading: str, max_chars: int) -> str:
    max_heading_chars = max(40, min(300, max_chars // 3))
    if len(heading) <= max_heading_chars:
        return heading
    return heading[:max_heading_chars].rstrip()


def _split_text(
    text: str,
    *,
    max_chars: int,
    overlap_chars: int,
) -> list[str]:
    if len(text) <= max_chars:
        return [text]

    parts: list[str] = []
    start = 0
    text_length = len(text)

    while start < text_length:
        hard_end = min(start + max_chars, text_length)
        end = hard_end
        if hard_end < text_length:
            search_start = start + max(max_chars // 2, 1)
            boundary = max(
                (text.rfind(character, search_start, hard_end) for character in BREAK_CHARACTERS),
                default=-1,
            )
            if boundary >= search_start:
                end = boundary + 1

        part = text[start:end].strip()
        if part:
            parts.append(part)
        if end >= text_length:
            break

        next_start = max(start + 1, end - overlap_chars)
        while next_start < end and text[next_start].isspace():
            next_start += 1
        start = next_start

    return parts
