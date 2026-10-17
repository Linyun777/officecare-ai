from dataclasses import dataclass, field

from app.schemas.document import DocumentBlock


@dataclass(slots=True)
class ParsedContent:
    blocks: list[DocumentBlock]
    page_count: int | None = None
    warnings: list[str] = field(default_factory=list)

