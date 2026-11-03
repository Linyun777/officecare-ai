import json
from io import BytesIO
from zipfile import ZipFile

from docx import Document
import pytest

from app.core.config import Settings
from app.loaders.base import ParsedContent
from app.loaders.docx_loader import load_docx
from app.schemas.document import DocumentBlock, DocumentParseResponse, SupportedFileType
from app.schemas.preview import DocumentPreviewRequest
from app.services.document_preview import preview_document
from app.services.mineru_client import (
    _UploadPart, _merge_parsed_parts, parse_mineru_archive,
)
from app.services.table_structure import html_table
from app.services.text_chunker import chunk_document


def block(index, text, kind="paragraph", page=1, **extra):
    return DocumentBlock(
        sequence=index, text=text, block_type=kind, page_number=page, **extra,
    )


def request(blocks, **extra):
    parsed = DocumentParseResponse(
        schema_version=2, normalization_version="structure-v2",
        file_name="synthetic.pdf", file_type="pdf", content_type="application/pdf",
        file_size=12, file_hash="a" * 64, block_count=len(blocks),
        character_count=sum(len(item.text) for item in blocks),
        blocks=blocks, warnings=[],
    )
    return DocumentPreviewRequest(
        document_version_id=18, file_hash="a" * 64, parse_id="b" * 64,
        parsed=parsed, selected_model="DCP-L2550DW", **extra,
    )


def test_html_adapter_preserves_spans_and_does_not_invent_td_headers():
    table = html_table(
        '<table><tr><td rowspan="2">DCP-L2550DW</td>'
        '<td>错误</td><td>措施</td></tr><tr><td colspan="2">检查后盖</td></tr></table>'
    )
    assert table.rows[0].cells[0].row_span == 2
    assert table.rows[1].cells[0].column_index == 1
    assert table.rows[1].cells[0].column_span == 2
    assert not any(cell.header for row in table.rows for cell in row.cells)


def test_mineru_keeps_structure_without_changing_legacy_text():
    items = [
        {"type": "text", "text_level": 2, "text": "故障排除", "page_idx": 0},
        {"type": "table", "page_idx": 1, "table_caption": ["错误表"],
         "table_footnote": ["先核对型号"], "table_body":
         '<table><tr><th>错误</th><th>措施</th></tr>'
         '<tr><td>不能双面打印</td><td>合上后盖</td></tr></table>'},
    ]
    buffer = BytesIO()
    with ZipFile(buffer, "w") as archive:
        archive.writestr("manual_content_list.json", json.dumps(items))
    parsed = parse_mineru_archive(
        buffer.getvalue(), file_type=SupportedFileType.PDF,
        max_uncompressed_bytes=1024 * 1024,
    )
    assert parsed.blocks[0].heading_level == 2
    assert parsed.blocks[1].source_item_index == 1
    assert parsed.blocks[1].text == "错误表\n错误\t措施\n不能双面打印\t合上后盖\n先核对型号"
    assert parsed.blocks[1].table.rows[0].cells[0].header is True
    assert parsed.blocks[1].table.footnotes == ["先核对型号"]


def test_docx_keeps_merged_origins_and_legacy_cell_text():
    document = Document()
    document.add_heading("故障排除", level=2)
    table = document.add_table(rows=2, cols=3)
    table.cell(0, 0).text = "型号"
    table.cell(0, 0).merge(table.cell(1, 0))
    table.cell(0, 1).text = "错误"
    table.cell(0, 2).text = "措施"
    table.cell(1, 1).text = "内存已满"
    table.cell(1, 2).text = "减少数据"
    buffer = BytesIO()
    document.save(buffer)
    parsed = load_docx(buffer.getvalue())
    assert parsed.blocks[0].heading_level == 2
    assert parsed.blocks[1].text == "型号\t错误\t措施\n型号\t内存已满\t减少数据"
    structure = parsed.blocks[1].table
    assert structure.rows[0].cells[0].row_span == 2
    assert [cell.column_index for cell in structure.rows[1].cells] == [1, 2]


def test_pdf_parts_keep_original_page_and_source_part_with_structure():
    original = block(1, "型号\t故障", "table", heading_level=None,
                     source_item_index=7, table=html_table("<table><tr><td>故障</td></tr></table>"))
    merged = _merge_parsed_parts(
        [_UploadPart("part.pdf", "part", b"", page_offset=20)],
        [ParsedContent(blocks=[original], page_count=1)],
        file_type=SupportedFileType.PDF, source_page_count=21,
    )
    assert merged.blocks[0].page_number == 21
    assert merged.blocks[0].source_part_index == 0
    assert merged.blocks[0].source_item_index == 7
    assert merged.blocks[0].table == original.table


def test_legacy_preview_matches_current_chunker_and_marks_missing_precision():
    blocks = [block(1, "故障排除", "heading"), block(2, "检查后盖是否合上。" * 30)]
    parameters = Settings(chunk_size=200, chunk_overlap=20)
    actual = preview_document(request(blocks, view="legacy"), parameters)
    legacy = chunk_document(blocks, max_chars=200, overlap_chars=20)
    assert [item.text for item in actual.items] == [item.text for item in legacy]
    assert actual.items[0].warnings
    assert actual.items[0].sources[0].character_start is None


def test_unknown_schema_and_mismatched_version_hash_fail_before_preview():
    valid = request([block(1, "原文")])
    for updates in [{"file_hash": "c" * 64}, {"schema_version": 99}]:
        parsed = valid.parsed.model_copy(update=updates)
        with pytest.raises(ValueError):
            DocumentPreviewRequest(**{**valid.model_dump(), "parsed": parsed})


def test_heading_paths_and_row_model_groups_have_exact_sources():
    table = html_table(
        '<table><tr><th>故障</th><th>措施</th></tr>'
        '<tr><td colspan="2">DCP-L2550DW</td></tr>'
        '<tr><td>不能双面打印</td><td>合上后盖</td></tr>'
        '<tr><td colspan="2">MFC-7895DW</td></tr>'
        '<tr><td>拒绝访问</td><td>联系管理员</td></tr></table>'
    )
    blocks = [
        block(1, "故障排除", "heading", heading_level=1),
        block(2, "错误", "heading", heading_level=2),
        block(3, "DCP-L2550DW\nMFC-7895DW", "table", table=table),
    ]
    result = preview_document(request(blocks, view="structured"), Settings())
    target = next(item for item in result.items if "合上后盖" in item.text)
    other = next(item for item in result.items if "联系管理员" in item.text)
    assert target.heading_path == ["故障排除", "错误"]
    assert target.eligible_for_selected_model is True
    assert other.eligible_for_selected_model is False
    assert target.scope_sources[0].row_index == 1
    assert any(source.row_index == 2 and source.column_index == 1 for source in target.sources)


def test_long_cell_chunks_are_bounded_and_resolve_exact_character_ranges():
    text = "关闭电源后缓慢取出卡纸，不要触摸高温部件。" * 40
    table = html_table('<table><tr><th>措施</th></tr><tr><td>' + text + '</td></tr></table>')
    blocks = [block(1, text, "table", table=table)]
    parameters = Settings(chunk_size=200, chunk_overlap=20)
    result = preview_document(request(blocks, view="structured", page_size=100), parameters)
    assert len(result.items) > 1
    assert all(len(item.text) <= 200 for item in result.items)
    for item in result.items:
        source = next(source for source in item.sources if source.role == "body")
        assert item.text.endswith(text[source.character_start:source.character_end])
    again = preview_document(request(blocks, view="structured", page_size=100), parameters)
    assert [item.candidate_id for item in again.items] == [item.candidate_id for item in result.items]


def test_pagination_preserves_complete_context_and_identity():
    blocks = [block(1, "MFC-7895DW"), *[block(index, "继续传真步骤") for index in range(2, 12)]]
    whole = preview_document(request(blocks, view="structured", page_size=100), Settings())
    page = preview_document(request(blocks, view="structured", page_size=2, page_num=4), Settings())
    assert page.total == whole.total
    assert page.items == whole.items[6:8]
    assert all(item.eligible_for_selected_model is False for item in page.items)
    assert all(item.scope_sources[0].block_sequence == 1 for item in page.items)


def test_explicit_cross_page_continuation_retains_model_proof_from_original_page():
    first = html_table(
        '<table><tr><th>错误</th><th>措施</th></tr>'
        '<tr><td colspan="2">MFC-7895DW</td></tr>'
        '<tr><td>错误1</td><td>检查1</td></tr></table>',
        captions=["错误表"],
    )
    second = html_table(
        '<table><tr><th>错误</th><th>措施</th></tr>'
        '<tr><td>错误2</td><td>检查2</td></tr></table>',
        captions=["错误表（续表）"],
    )
    blocks = [
        block(1, "MFC-7895DW\n错误表", "table", page=10, table=first),
        block(2, "错误表（续表）", "table", page=11, table=second),
    ]
    result = preview_document(request(blocks, view="structured"), Settings())
    candidate = next(item for item in result.items if "检查2" in item.text)
    assert candidate.continuation_of == 1
    assert candidate.eligible_for_selected_model is False
    assert candidate.scope_sources[0].page_number == 10
    assert any(source.page_number == 11 for source in candidate.sources)


def test_matching_columns_do_not_prove_continuation_or_override_a_new_model():
    first = html_table(
        '<table><tr><th>错误</th></tr><tr><td>MFC-7895DW</td></tr></table>',
        captions=["传真错误"],
    )
    second = html_table(
        '<table><tr><th>错误</th></tr><tr><td>纸张错误</td></tr></table>',
        captions=["纸张错误（续表）"],
    )
    blocks = [
        block(1, "MFC-7895DW", "table", page=10, table=first),
        block(2, "DCP-L2550DW", "table", page=11, table=second),
    ]
    result = preview_document(request(blocks, view="structured"), Settings())
    candidate = next(item for item in result.items if "纸张错误" in item.text)
    assert candidate.continuation_of is None
    assert candidate.eligible_for_selected_model is True


def test_unstructured_legacy_tables_remain_explicitly_unknown():
    value = request([block(1, "型号\t故障\n某型号\t卡纸", "table")], view="structured")
    value.parsed.schema_version = 1
    result = preview_document(value, Settings())
    assert result.warnings
    assert result.items[0].table is None
    assert result.items[0].warnings


def test_malformed_html_keeps_unknown_layout_without_executing_markup():
    table = html_table('<table><tr><td rowspan="999999">text</td></tr></table>')
    assert table.rows == []
    assert table.warnings
    table = html_table('<table><tr><td><script>alert(1)</script>安全文字</td></tr></table>')
    assert table.rows[0].cells[0].text == "安全文字"


def test_preview_endpoint_is_authenticated_and_does_not_use_indexing(client, auth_headers, monkeypatch):
    import app.api.documents as routes

    def forbidden(*args, **kwargs):
        raise AssertionError("preview must not parse, index, embed or generate")

    monkeypatch.setattr(routes, "parse_document", forbidden)
    monkeypatch.setattr(routes, "get_document_indexing_service", forbidden)
    payload = request([block(1, "只读内容")]).model_dump(mode="json", by_alias=True)
    assert client.post("/api/v1/internal/documents/preview", json=payload).status_code == 401
    response = client.post("/api/v1/internal/documents/preview", json=payload, headers=auth_headers)
    assert response.status_code == 200
    assert response.json()["items"][0]["text"] == "只读内容"
    assert response.json()["parseId"] == "b" * 64
