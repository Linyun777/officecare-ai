from __future__ import annotations

from dataclasses import dataclass
import re
import unicodedata
from typing import Iterable

from langchain_core.documents import Document

from app.services.retrieval_quality import (
    declared_model_codes, is_navigation_only, section_model_codes,
)


_SHARED_LINE = re.compile(
    r"(?:适用(?:于)?(?:型号)?\s*[:：]?\s*)?(?:所有|全部)型号"
    r"(?:通用|均适用)?[。.:：]?"
)
_SHARED_HEADING = re.compile(r"通用(?:安全说明|安全须知|注意事项)")
_ROUTE_LINE = re.compile(r"^\s*主页\s*[>›»]\s*(.+)$", re.MULTILINE)


@dataclass(frozen=True, slots=True)
class ResolvedModelScope:
    # None means unresolved; an empty set means a verified unscoped section.
    models: frozenset[str] | None
    origin: str

    def allows(self, selected_models: set[str]) -> bool:
        if not selected_models:
            return True
        return self.models is not None and (
            not self.models or not self.models.isdisjoint(selected_models)
        )


def document_key(document: Document) -> tuple[int, int]:
    return (
        int(document.metadata["document_version_id"]),
        int(document.metadata["chunk_index"]),
    )


def _route(document: Document) -> tuple[str, ...]:
    matches = list(_ROUTE_LINE.finditer(document.page_content))
    if not matches:
        return ()
    return tuple(
        part.strip()
        for part in re.split(r"[>›»]", matches[-1].group(1))
        if part.strip()
    )


def _direct_scope(document: Document) -> ResolvedModelScope | None:
    heading = str(document.metadata.get("heading") or "").strip()
    text = unicodedata.normalize("NFKC", document.page_content)
    groups: set[frozenset[str]] = set()
    lines = [heading, *text.splitlines()]
    for line in lines:
        # >> lists are navigation/applicability summaries. A concrete table's
        # own bare/labelled model group takes precedence over that summary.
        if line.strip().startswith(">>"):
            continue
        models = section_model_codes(line)
        if models:
            groups.add(frozenset(models))
    if len(groups) > 1:
        return ResolvedModelScope(None, "mixed_model_groups")
    if groups:
        bare_group = any(
            section_model_codes(line) and not declared_model_codes(line)
            for line in lines
            if not line.strip().startswith(">>")
        )
        return ResolvedModelScope(
            next(iter(groups)), "explicit_group" if bare_group else "explicit",
        )

    lines = [heading, *text.splitlines()]
    if any(_SHARED_LINE.fullmatch(line.strip()) for line in lines):
        return ResolvedModelScope(frozenset(), "shared_declaration")
    if _SHARED_HEADING.fullmatch(heading):
        return ResolvedModelScope(frozenset(), "shared_heading")

    declared = section_model_codes(text, heading=heading)
    if declared:
        return ResolvedModelScope(frozenset(declared), "explicit_list")
    return None


def _title(value: str) -> str:
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", value))


def _toc_titles(documents: list[Document]) -> dict[int, set[str]]:
    titles: dict[int, set[str]] = {}
    for document in documents:
        heading = _title(str(document.metadata.get("heading") or ""))
        if heading not in {"目录", "主页>目录"}:
            continue
        version_id = document_key(document)[0]
        for line in document.page_content.splitlines():
            match = re.fullmatch(r"\s*(.+?)(?:\.{2,}|\s+)\s*(\d+)\s*", line)
            if match and len(match.group(1)) <= 160:
                titles.setdefault(version_id, set()).add(_title(match.group(1).rstrip(" .。…·")))
    return titles


def _outline_children(
    document: Document,
    headings: set[str],
) -> set[str]:
    lines = [
        unicodedata.normalize("NFKC", line).strip()
        for line in document.page_content.splitlines() if line.strip()
    ]
    heading = _title(str(document.metadata.get("heading") or ""))
    children: set[str] = set()
    for line in lines:
        if _title(line) == heading or declared_model_codes(line):
            continue
        if not line.startswith(("•", "●", "·")):
            return set()
        title = _title(line.lstrip("•●· ").strip())
        if title in headings:
            children.add(title)
    return children if len(children) >= 2 else set()


def resolve_model_scopes(
    documents: Iterable[Document],
) -> dict[tuple[int, int], ResolvedModelScope]:
    """Resolve only contiguous scopes inside one Java-approved version.

    Breadcrumb navigation blocks supply actual section boundaries. Merely
    seeing a new free-text heading is insufficient to declare a shared section.
    """
    ordered = sorted(documents, key=document_key)
    contents = _toc_titles(ordered)
    headings: dict[int, set[str]] = {}
    for document in ordered:
        version = document_key(document)[0]
        headings.setdefault(version, set()).add(
            _title(str(document.metadata.get("heading") or ""))
        )
    child_scopes: dict[tuple[int, str], ResolvedModelScope] = {}

    result: dict[tuple[int, int], ResolvedModelScope] = {}
    previous_key: tuple[int, int] | None = None
    previous_heading = ""
    section_route: tuple[str, ...] = ()
    anchor_route: tuple[str, ...] = ()
    complete_prefix = False
    topic = ""
    scope_topic = ""
    group_scope = False
    active = ResolvedModelScope(None, "missing_context")

    for document in ordered:
        key = document_key(document)
        heading = str(document.metadata.get("heading") or "").strip()
        route = _route(document)
        contiguous = previous_key == (key[0], key[1] - 1)
        if not contiguous:
            complete_prefix = key[1] == 0
            active = ResolvedModelScope(
                frozenset() if complete_prefix else None,
                "approved_document_root" if complete_prefix else "missing_context",
            )
            section_route = ()
            anchor_route = ()
            topic = ""
            scope_topic = ""
            group_scope = False

        scope_context_updated = False
        children = _outline_children(document, headings[key[0]])
        parent_scope = child_scopes.get((key[0], _title(heading)))
        returns_to_group_topic = (
            group_scope and scope_topic and _title(heading) == scope_topic
        )
        if (
            complete_prefix and heading != previous_heading
            and parent_scope is not None and not returns_to_group_topic
        ):
            scope_context_updated = True
            active = ResolvedModelScope(parent_scope.models, "outline_parent")
            anchor_route = route
            scope_topic = _title(heading)
            topic = scope_topic
            group_scope = False
        elif (
            complete_prefix and children and not returns_to_group_topic
            and not (
                route and anchor_route and len(route) > len(anchor_route)
                and route[:len(anchor_route)] == anchor_route
            )
        ):
            active = ResolvedModelScope(frozenset(), "outline_section_boundary")
            anchor_route = route
            scope_topic = _title(heading)
            topic = scope_topic
            group_scope = False

        if _title(heading) in contents.get(key[0], set()):
            new_topic = _title(heading)
            if complete_prefix and (
                not scope_topic or new_topic != scope_topic
            ) and not (
                route and anchor_route and len(route) > len(anchor_route)
                and route[:len(anchor_route)] == anchor_route
            ):
                active = ResolvedModelScope(frozenset(), "toc_section_boundary")
                anchor_route = ()
                group_scope = False
            topic = new_topic

        if route:
            independent = (
                bool(anchor_route)
                and route != anchor_route
                and route[:len(anchor_route)] != anchor_route
            )
            root_navigation = (
                complete_prefix
                and not anchor_route
                and is_navigation_only(document.page_content, heading=heading)
            )
            if independent or root_navigation:
                active = ResolvedModelScope(frozenset(), "new_breadcrumb_section")
                anchor_route = route
            section_route = route

        direct = _direct_scope(document)
        if direct is not None:
            active = direct
            anchor_route = route or section_route
            scope_topic = topic
            group_scope = direct.origin == "explicit_group"
        elif (
            active.models
            and not scope_context_updated
            and contiguous
            and heading != previous_heading
            and not (group_scope and topic and _title(heading) == scope_topic)
            and not (route and anchor_route and route[:len(anchor_route)] == anchor_route)
        ):
            active = ResolvedModelScope(None, "unresolved_heading_boundary")
        if children and active.models is not None:
            for child in children:
                child_scopes[(key[0], child)] = active
        result[key] = active

        previous_key = key
        previous_heading = heading
    return result
