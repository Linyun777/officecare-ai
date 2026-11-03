"""Deterministic, read-only previews. No indexing or model clients are used."""

from dataclasses import dataclass
import hashlib
import json
import re

from langchain_core.documents import Document

from app.core.config import Settings
from app.schemas.document import BlockType, DocumentBlock
from app.schemas.preview import (
    DocumentPreviewRequest,
    DocumentPreviewResponse,
    PreviewItem,
    PreviewSource,
)
from app.services.model_scope import ResolvedModelScope, resolve_model_scopes
from app.services.retrieval_quality import printer_model_codes, section_model_codes
from app.services.text_chunker import chunk_document


@dataclass
class _BlockContext:
    block: DocumentBlock
    path: list[str]
    scope: ResolvedModelScope
    proof: list[PreviewSource]


def _source(block, **values):
    return PreviewSource(
        block_sequence=block.sequence,
        page_number=block.page_number,
        source_item_index=block.source_item_index,
        source_part_index=block.source_part_index,
        **values,
    )


def _contexts(blocks, version_id):
    documents = []
    paths = {}
    headings = []
    for block in blocks:
        if block.block_type == BlockType.HEADING:
            if block.heading_level is None:
                headings = [(None, block.text)]
            else:
                headings = [
                    (level, text) for level, text in headings
                    if level is not None and level < block.heading_level
                ]
                headings.append((block.heading_level, block.text))
        paths[block.sequence] = [text for _, text in headings]
        documents.append(Document(
            page_content=block.text,
            metadata={
                "document_version_id": version_id,
                "chunk_index": block.sequence - 1,
                "heading": headings[-1][1] if headings else "",
            },
        ))
    scopes = resolve_model_scopes(documents)
    proof = []
    previous_scope = None
    result = []
    for block in blocks:
        scope = scopes[(version_id, block.sequence - 1)]
        if scope != previous_scope:
            proof = (
                [_source(block, role="model_scope")]
                if scope.models is not None and scope.origin != "approved_document_root"
                else []
            )
        result.append(_BlockContext(block, paths[block.sequence], scope, list(proof)))
        previous_scope = scope
    return result


def _windows(text, budget, overlap):
    start = 0
    while start < len(text):
        end = min(start + budget, len(text))
        if end < len(text):
            boundary = max(text.rfind(mark, start + budget // 2, end) for mark in "\n。！？；.!?;")
            if boundary >= start + budget // 2:
                end = boundary + 1
        left, right = start, end
        while left < right and text[left].isspace():
            left += 1
        while right > left and text[right - 1].isspace():
            right -= 1
        if left < right:
            yield left, right
        if end == len(text):
            break
        start = max(start + 1, end - min(overlap, budget - 1))


class _Builder:
    def __init__(self, request, settings):
        self.request = request
        self.settings = settings
        self.items = []
        self.selected = printer_model_codes(request.selected_model)
        self.profile = {
            "blocks": "source-blocks-v1",
            "legacy": "legacy-v1",
            "structured": "structure-preview-v1",
        }[request.view]

    def add(self, context, text, sources, *, kind, scope=None, proof=None, **extra):
        scope = scope or context.scope
        models = sorted(scope.models) if scope.models is not None else None
        eligible = None
        if self.selected and scope.models is not None:
            eligible = scope.allows(self.selected)
        identity = {
            "parseId": self.request.parse_id,
            "profile": self.profile,
            "chunkSize": self.settings.chunk_size,
            "overlap": self.settings.chunk_overlap,
            "sources": [source.model_dump() for source in sources],
            "text": text,
            "models": models,
        }
        digest = hashlib.sha256(
            json.dumps(identity, sort_keys=True, ensure_ascii=False).encode("utf-8")
        ).hexdigest()
        self.items.append(PreviewItem(
            candidate_id=digest,
            ordinal=len(self.items),
            kind=kind,
            text=text,
            page_number=context.block.page_number,
            heading_path=context.path,
            sources=sources,
            model_scope=models,
            scope_status=("unknown" if models is None else "restricted" if models else "shared"),
            scope_origin=scope.origin,
            scope_sources=context.proof if proof is None else proof,
            eligible_for_selected_model=eligible,
            **extra,
        ))

    def split(self, context, text, segments, *, kind, prefix="", context_sources=None, **extra):
        warnings = list(extra.pop("warnings", []))
        limit = self.settings.chunk_size
        if len(prefix) > limit // 2:
            prefix = prefix[:limit // 2]
            warnings.append("上下文较长，文字前缀已截断；完整上下文可由来源核对。")
        prefix = prefix + "\n" if prefix else ""
        budget = max(1, limit - len(prefix))
        for left, right in _windows(text, budget, self.settings.chunk_overlap):
            sources = list(context_sources or [])
            for segment_start, segment_end, source in segments:
                begin, end = max(left, segment_start), min(right, segment_end)
                if begin < end:
                    offset = source.character_start or 0
                    sources.append(source.model_copy(update={
                        "character_start": offset + begin - segment_start,
                        "character_end": offset + end - segment_start,
                    }))
            self.add(
                context, prefix + text[left:right], sources,
                kind=kind, warnings=warnings, **extra,
            )


def _effective_rows(table):
    active = []
    for row in table.rows:
        active = [(origin, cell) for origin, cell in active if origin + cell.row_span > row.row_index]
        active.extend((row.row_index, cell) for cell in row.cells)
        yield row, sorted(active, key=lambda item: item[1].column_index)


def _header_signature(table):
    return tuple(
        (row.row_index, cell.column_index, cell.column_span, cell.text)
        for row in table.rows for cell in row.cells if cell.header
    )


def _structured_table(builder, context, previous):
    block = context.block
    table = block.table
    signature = _header_signature(table)
    width = max(
        (cell.column_index + cell.column_span for row in table.rows for cell in row.cells),
        default=0,
    )
    caption = "\n".join(table.captions)
    continuation_title = re.sub(
        r"[（(]?\s*(?:续表|续|continued)\s*[）)]?", "", caption,
        flags=re.IGNORECASE,
    ).strip()
    explicit_continuation = bool(re.search(r"续|continued", caption, re.IGNORECASE))
    continued = (
        previous is not None and bool(signature)
        and explicit_continuation and bool(continuation_title)
        and continuation_title == previous["caption"]
        and signature == previous["signature"] and width == previous["width"]
        and context.path == previous["path"]
        and block.sequence == previous["sequence"] + 1
        and block.page_number is not None and previous["page"] is not None
        and block.page_number == previous["page"] + 1
        and previous["scope"].models is not None
        and (context.scope.models is None or context.scope.models == previous["scope"].models)
    )
    scope = previous["scope"] if continued else context.scope
    proof = list(previous["proof"]) if continued else list(context.proof)
    header_cells = [
        (row.row_index, cell) for row in table.rows for cell in row.cells if cell.header
    ]
    header_text = " | ".join(cell.text for _, cell in header_cells)
    header_sources = [
        _source(block, row_index=row_index, column_index=cell.column_index, role="table_header")
        for row_index, cell in header_cells
    ]
    caption_sources = [
        _source(block, role="caption", element_index=index)
        for index, _ in enumerate(table.captions)
    ]
    for row, effective in _effective_rows(table):
        values = [(origin, cell) for origin, cell in effective if cell.text.strip()]
        if not values or all(cell.header for _, cell in values):
            continue
        groups = [
            (section_model_codes(cell.text), origin, cell)
            for origin, cell in values if section_model_codes(cell.text)
        ]
        if len(values) == 1 and groups:
            codes, origin, cell = groups[0]
            scope = ResolvedModelScope(frozenset(codes), "table_model_group")
            proof = [_source(block, row_index=origin, column_index=cell.column_index, role="model_scope")]
        row_scope, row_proof = scope, proof
        if groups:
            unique = {frozenset(codes) for codes, _, _ in groups}
            row_scope = ResolvedModelScope(
                next(iter(unique)) if len(unique) == 1 else None,
                "table_row_declaration" if len(unique) == 1 else "mixed_model_groups",
            )
            row_proof = [
                _source(block, row_index=origin, column_index=cell.column_index, role="model_scope")
                for _, origin, cell in groups
            ]
        parts, segments, offset = [], [], 0
        for origin, cell in values:
            separator = " | " if parts else ""
            parts.append(separator + cell.text)
            offset += len(separator)
            segments.append((offset, offset + len(cell.text), _source(
                block, row_index=origin, column_index=cell.column_index,
            )))
            offset += len(cell.text)
        body = "".join(parts)
        prefix = "\n".join([*table.captions, *([header_text] if header_text else [])])
        builder.split(
            context, body, segments, kind="table_row", prefix=prefix,
            context_sources=caption_sources + header_sources,
            scope=row_scope, proof=row_proof,
            group_id=f"{builder.request.parse_id}:b{block.sequence}:r{row.row_index}",
            continuation_of=previous["sequence"] if continued else None,
            warnings=table.warnings,
        )
    for index, text in enumerate(table.footnotes):
        source = _source(block, role="footnote", element_index=index)
        builder.split(
            context, text, [(0, len(text), source)], kind="table_footnote",
            group_id=f"{builder.request.parse_id}:b{block.sequence}",
        )
    return {
        "signature": signature, "width": width, "path": context.path,
        "page": block.page_number, "sequence": block.sequence,
        "scope": scope, "proof": proof,
        "caption": continuation_title,
    }


def build_preview_items(request: DocumentPreviewRequest, settings: Settings):
    builder = _Builder(request, settings)
    contexts = _contexts(request.parsed.blocks, request.document_version_id)
    warnings = list(request.parsed.warnings)
    if request.parsed.schema_version == 1:
        warnings.append("旧解析结果没有结构元数据；原文与现有规则预览仍可使用。")
    if request.view == "legacy":
        by_sequence = {context.block.sequence: context for context in contexts}
        for chunk in chunk_document(
            request.parsed.blocks,
            max_chars=settings.chunk_size,
            overlap_chars=settings.chunk_overlap,
        ):
            context = by_sequence[chunk.block_end]
            sources = [
                _source(item.block)
                for item in contexts if chunk.block_start <= item.block.sequence <= chunk.block_end
            ]
            builder.add(
                context, chunk.text, sources, kind="legacy_chunk",
                warnings=["来源为旧规则的块范围，未提供精确字符区间；并非读取历史索引。"],
            )
    else:
        previous_table = None
        for context in contexts:
            block = context.block
            if request.view == "blocks":
                builder.add(
                    context, block.text, [_source(block)], kind=block.block_type.value,
                    table=block.table,
                    warnings=block.table.warnings if block.table else [],
                )
            elif block.table is not None and block.table.rows:
                previous_table = _structured_table(builder, context, previous_table)
                continue
            else:
                unavailable = (
                    ["缺少可确认的表格结构，按原文字预览，不猜测行列。"]
                    if block.block_type == BlockType.TABLE else []
                )
                builder.split(
                    context, block.text,
                    [(0, len(block.text), _source(block))],
                    kind=block.block_type.value,
                    group_id=f"{request.parse_id}:b{block.sequence}",
                    warnings=unavailable,
                )
            previous_table = None
    return builder, warnings


def preview_document(request: DocumentPreviewRequest, settings: Settings) -> DocumentPreviewResponse:
    builder, warnings = build_preview_items(request, settings)
    start = (request.page_num - 1) * request.page_size
    return DocumentPreviewResponse(
        document_version_id=request.document_version_id,
        parse_id=request.parse_id,
        schema_version=request.parsed.schema_version,
        normalization_version=request.parsed.normalization_version,
        view=request.view,
        profile=builder.profile,
        chunk_size=settings.chunk_size,
        overlap_chars=settings.chunk_overlap,
        total=len(builder.items),
        page_num=request.page_num,
        page_size=request.page_size,
        items=builder.items[start:start + request.page_size],
        warnings=warnings,
    )
