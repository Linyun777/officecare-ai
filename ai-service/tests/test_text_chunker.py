from app.schemas.document import BlockType, DocumentBlock
from app.services.text_chunker import chunk_document


def test_chunker_preserves_heading_page_and_source_sequence() -> None:
    blocks = [
        DocumentBlock(
            sequence=1,
            page_number=None,
            block_type=BlockType.HEADING,
            text="故障排除",
        ),
        DocumentBlock(
            sequence=2,
            page_number=3,
            block_type=BlockType.PARAGRAPH,
            text="错误代码 E01 表示发生卡纸。",
        ),
        DocumentBlock(
            sequence=3,
            page_number=3,
            block_type=BlockType.TABLE,
            text="代码\t含义\nE01\t卡纸",
        ),
    ]

    chunks = chunk_document(blocks, max_chars=200, overlap_chars=20)

    assert len(chunks) == 1
    assert chunks[0].heading == "故障排除"
    assert chunks[0].page_number == 3
    assert chunks[0].block_start == 1
    assert chunks[0].block_end == 3
    assert chunks[0].text.startswith("故障排除\n\n")
    assert "E01" in chunks[0].text


def test_chunker_splits_long_text_with_bounded_deterministic_chunks() -> None:
    text = "打印机发生卡纸时，请关闭电源并取出纸张。" * 40
    blocks = [
        DocumentBlock(
            sequence=1,
            page_number=5,
            block_type=BlockType.PAGE,
            text=text,
        )
    ]

    first = chunk_document(blocks, max_chars=200, overlap_chars=20)
    second = chunk_document(blocks, max_chars=200, overlap_chars=20)

    assert len(first) > 1
    assert [chunk.chunk_hash for chunk in first] == [
        chunk.chunk_hash for chunk in second
    ]
    assert all(len(chunk.text) <= 200 for chunk in first)
    assert [chunk.chunk_index for chunk in first] == list(range(len(first)))


def test_chunker_does_not_merge_different_pages() -> None:
    blocks = [
        DocumentBlock(
            sequence=1,
            page_number=1,
            block_type=BlockType.PAGE,
            text="第一页内容",
        ),
        DocumentBlock(
            sequence=2,
            page_number=2,
            block_type=BlockType.PAGE,
            text="第二页内容",
        ),
    ]

    chunks = chunk_document(blocks, max_chars=200, overlap_chars=20)

    assert [chunk.page_number for chunk in chunks] == [1, 2]


def test_chunker_skips_mineru_navigation_only_groups() -> None:
    blocks = [
        DocumentBlock(
            sequence=1,
            page_number=10,
            block_type=BlockType.HEADING,
            text="相关信息",
        ),
        DocumentBlock(
            sequence=2,
            page_number=10,
            block_type=BlockType.PARAGRAPH,
            text="主页 > 故障排除 > 卡纸 > 设备内部卡纸",
        ),
        DocumentBlock(
            sequence=3,
            page_number=11,
            block_type=BlockType.HEADING,
            text="设备内部卡纸",
        ),
        DocumentBlock(
            sequence=4,
            page_number=11,
            block_type=BlockType.PARAGRAPH,
            text="打开前盖，然后慢慢拉出卡住的纸张。",
        ),
    ]

    chunks = chunk_document(blocks, max_chars=200, overlap_chars=20)

    assert len(chunks) == 1
    assert chunks[0].chunk_index == 0
    assert chunks[0].heading == "设备内部卡纸"
    assert "主页 >" not in chunks[0].text
