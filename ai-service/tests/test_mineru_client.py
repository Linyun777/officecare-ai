import json
from io import BytesIO
from zipfile import ZipFile

import pytest
from pypdf import PdfReader, PdfWriter

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.loaders.base import ParsedContent
from app.schemas.document import BlockType, DocumentBlock, SupportedFileType
from app.services.document_parser import parse_document
from app.services.mineru_client import MinerUClient, parse_mineru_archive


class FakeResponse:
    def __init__(self, body: bytes) -> None:
        self._body = BytesIO(body)

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def read(self, size: int = -1) -> bytes:
        return self._body.read(size)


class FakeOpener:
    def __init__(self, responses: list[bytes]) -> None:
        self._responses = iter(responses)
        self.requests = []

    def __call__(self, request, **kwargs) -> FakeResponse:
        self.requests.append(request)
        return FakeResponse(next(self._responses))


class FakeUploadResponse:
    def __init__(self, status_code: int = 200) -> None:
        self.status_code = status_code

    def __enter__(self) -> "FakeUploadResponse":
        return self

    def __exit__(self, *args: object) -> None:
        return None


class FakeUploadSession:
    def __init__(self, status_code: int = 200) -> None:
        self.status_code = status_code
        self.calls: list[tuple[str, dict]] = []

    def put(self, url: str, **kwargs) -> FakeUploadResponse:
        self.calls.append((url, kwargs))
        return FakeUploadResponse(self.status_code)


def _result_archive() -> bytes:
    content_list = [
        {
            "type": "header",
            "text": "Brother User Guide",
            "page_idx": 0,
        },
        {
            "type": "text",
            "text": "Paper Jam",
            "text_level": 1,
            "page_idx": 0,
        },
        {
            "type": "text",
            "text": "Turn off the printer before removing jammed paper.",
            "page_idx": 0,
        },
        {
            "type": "table",
            "table_caption": ["Error codes"],
            "table_body": (
                "<table><tr><th>Code</th><th>Meaning</th></tr>"
                "<tr><td>E01</td><td>Paper jam</td></tr></table>"
            ),
            "table_footnote": [],
            "page_idx": 1,
        },
    ]
    middle = {"pdf_info": [{}, {}, {}], "_backend": "vlm"}
    output = BytesIO()
    with ZipFile(output, "w") as archive:
        archive.writestr(
            "manual/manual_content_list.json",
            json.dumps(content_list).encode("utf-8"),
        )
        archive.writestr(
            "manual/manual_middle.json",
            json.dumps(middle).encode("utf-8"),
        )
    return output.getvalue()


def _part_archive(text: str, page_count: int) -> bytes:
    content_list = [
        {
            "type": "text",
            "text": text,
            "page_idx": 0,
        }
    ]
    middle = {"pdf_info": [{} for _ in range(page_count)], "_backend": "vlm"}
    output = BytesIO()
    with ZipFile(output, "w") as archive:
        archive.writestr(
            "part/part_content_list.json",
            json.dumps(content_list).encode("utf-8"),
        )
        archive.writestr(
            "part/part_middle.json",
            json.dumps(middle).encode("utf-8"),
        )
    return output.getvalue()


def _blank_pdf(page_count: int) -> bytes:
    writer = PdfWriter()
    for _ in range(page_count):
        writer.add_blank_page(width=595, height=842)
    output = BytesIO()
    writer.write(output)
    return output.getvalue()


def _settings(**overrides) -> Settings:
    values = {
        "_env_file": None,
        "document_parser": "mineru",
        "mineru_base_url": "https://mineru.test",
        "mineru_api_token": "test-mineru-token",
        "mineru_request_timeout_seconds": 10,
        "mineru_poll_interval_seconds": 1,
        "mineru_timeout_seconds": 30,
        "mineru_max_result_size_mb": 10,
    }
    values.update(overrides)
    return Settings(**values)


def test_mineru_client_uploads_polls_and_parses_result() -> None:
    ticket = {
        "code": 0,
        "data": {
            "batch_id": "batch-1",
            "file_urls": ["https://upload.test/manual.pdf"],
        },
    }
    pending = {
        "code": 0,
        "data": {
            "extract_result": [
                {
                    "file_name": "manual.pdf",
                    "data_id": "a" * 64,
                    "state": "running",
                }
            ]
        },
    }
    done = {
        "code": 0,
        "data": {
            "extract_result": [
                {
                    "file_name": "manual.pdf",
                    "data_id": "a" * 64,
                    "state": "done",
                    "full_zip_url": "https://cdn.test/result.zip",
                }
            ]
        },
    }
    opener = FakeOpener(
        [
            json.dumps(ticket).encode(),
            json.dumps(pending).encode(),
            json.dumps(done).encode(),
            _result_archive(),
        ]
    )
    upload_session = FakeUploadSession()
    sleeps: list[float] = []
    client = MinerUClient(
        _settings(),
        opener=opener,
        upload_session=upload_session,
        sleeper=sleeps.append,
        monotonic=lambda: 0,
    )

    result = client.parse(
        file_name="manual.pdf",
        file_type=SupportedFileType.PDF,
        content=b"%PDF-test",
        file_hash="a" * 64,
    )

    assert result.page_count == 3
    assert len(result.blocks) == 3
    assert result.blocks[0].block_type == BlockType.HEADING
    assert result.blocks[0].page_number == 1
    assert result.blocks[2].block_type == BlockType.TABLE
    assert "Code\tMeaning" in result.blocks[2].text
    assert sleeps == [1]
    assert [request.get_method() for request in opener.requests] == [
        "POST",
        "GET",
        "GET",
        "GET",
    ]
    submitted = json.loads(opener.requests[0].data.decode("utf-8"))
    assert submitted["files"] == [
        {
            "name": "manual.pdf",
            "data_id": "a" * 64,
            "is_ocr": False,
        }
    ]
    assert submitted["model_version"] == "vlm"
    assert submitted["enable_table"] is True
    assert submitted["enable_formula"] is False
    assert opener.requests[0].get_header("Authorization") == (
        "Bearer test-mineru-token"
    )
    assert opener.requests[3].get_header("Authorization") is None
    assert len(upload_session.calls) == 1
    upload_url, upload_options = upload_session.calls[0]
    assert upload_url == "https://upload.test/manual.pdf"
    assert upload_options["data"] == b"%PDF-test"
    assert "headers" not in upload_options


def test_mineru_archive_keeps_docx_blocks_without_unreliable_page_numbers() -> None:
    result = parse_mineru_archive(
        _result_archive(),
        file_type=SupportedFileType.DOCX,
        max_uncompressed_bytes=10 * 1024 * 1024,
    )

    assert result.page_count is None
    assert all(block.page_number is None for block in result.blocks)
    assert result.blocks[0].block_type == BlockType.HEADING


def test_mineru_client_surfaces_provider_parse_failure() -> None:
    ticket = {
        "code": 0,
        "data": {
            "batch_id": "batch-1",
            "file_urls": ["https://upload.test/manual.pdf"],
        },
    }
    failed = {
        "code": 0,
        "data": {
            "extract_result": [
                {
                    "file_name": "manual.pdf",
                    "state": "failed",
                    "err_msg": "Page limit exceeded",
                }
            ]
        },
    }
    opener = FakeOpener(
        [
            json.dumps(ticket).encode(),
            json.dumps(failed).encode(),
        ]
    )

    with pytest.raises(ServiceError) as error:
        MinerUClient(
            _settings(),
            opener=opener,
            upload_session=FakeUploadSession(),
            sleeper=lambda _: None,
            monotonic=lambda: 0,
        ).parse(
            file_name="manual.pdf",
            file_type=SupportedFileType.PDF,
            content=b"%PDF-test",
            file_hash="a" * 64,
        )

    assert error.value.code == "MINERU_PARSE_FAILED"
    assert error.value.message == "Page limit exceeded"


def test_mineru_client_does_not_report_signed_upload_403_as_token_failure() -> None:
    ticket = {
        "code": 0,
        "data": {
            "batch_id": "batch-1",
            "file_urls": ["https://upload.test/manual.pdf"],
        },
    }

    with pytest.raises(ServiceError) as error:
        MinerUClient(
            _settings(),
            opener=FakeOpener([json.dumps(ticket).encode()]),
            upload_session=FakeUploadSession(status_code=403),
        ).parse(
            file_name="manual.pdf",
            file_type=SupportedFileType.PDF,
            content=b"%PDF-test",
            file_hash="a" * 64,
        )

    assert error.value.code == "MINERU_UPLOAD_REJECTED"
    assert "signed upload URL" in error.value.message


def test_mineru_client_splits_long_pdf_and_restores_original_page_numbers() -> None:
    file_hash = "a" * 64
    part_ids = [f"{file_hash}.part-{index:03d}" for index in range(1, 4)]
    ticket = {
        "code": 0,
        "data": {
            "batch_id": "batch-long-pdf",
            "file_urls": [
                "https://upload.test/part-1.pdf",
                "https://upload.test/part-2.pdf",
                "https://upload.test/part-3.pdf",
            ],
        },
    }
    done = {
        "code": 0,
        "data": {
            "extract_result": [
                {
                    "data_id": part_ids[0],
                    "state": "done",
                    "full_zip_url": "https://cdn.test/part-1.zip",
                },
                {
                    "data_id": part_ids[1],
                    "state": "done",
                    "full_zip_url": "https://cdn.test/part-2.zip",
                },
                {
                    "data_id": part_ids[2],
                    "state": "done",
                    "full_zip_url": "https://cdn.test/part-3.zip",
                },
            ]
        },
    }
    opener = FakeOpener(
        [
            json.dumps(ticket).encode(),
            json.dumps(done).encode(),
            _part_archive("part one", 200),
            _part_archive("part two", 200),
            _part_archive("part three", 1),
        ]
    )
    upload_session = FakeUploadSession()

    result = MinerUClient(
        _settings(),
        opener=opener,
        upload_session=upload_session,
        sleeper=lambda _: None,
        monotonic=lambda: 0,
    ).parse(
        file_name="advanced-manual.pdf",
        file_type=SupportedFileType.PDF,
        content=_blank_pdf(401),
        file_hash=file_hash,
    )

    assert result.page_count == 401
    assert [block.page_number for block in result.blocks] == [1, 201, 401]
    assert [block.sequence for block in result.blocks] == [1, 2, 3]
    assert len(upload_session.calls) == 3
    uploaded_page_counts = [
        len(PdfReader(BytesIO(options["data"])).pages)
        for _, options in upload_session.calls
    ]
    assert uploaded_page_counts == [200, 200, 1]
    submitted = json.loads(opener.requests[0].data.decode("utf-8"))
    assert [item["data_id"] for item in submitted["files"]] == part_ids
    assert "3 page-limited parts" in result.warnings[-1]


def test_mineru_client_requires_token() -> None:
    with pytest.raises(ServiceError) as error:
        MinerUClient(_settings(mineru_api_token="")).parse(
            file_name="manual.pdf",
            file_type=SupportedFileType.PDF,
            content=b"%PDF-test",
            file_hash="a" * 64,
        )

    assert error.value.code == "MINERU_NOT_CONFIGURED"


def test_document_parser_routes_pdf_and_docx_to_mineru() -> None:
    calls: list[SupportedFileType] = []

    class FakeMinerUClient:
        def parse(self, **kwargs) -> ParsedContent:
            calls.append(kwargs["file_type"])
            return ParsedContent(
                blocks=[
                    DocumentBlock(
                        sequence=1,
                        page_number=1,
                        block_type=BlockType.PARAGRAPH,
                        text="MinerU parsed text",
                    )
                ],
                page_count=1,
            )

    settings = _settings()
    pdf_response = parse_document(
        file_name="manual.pdf",
        content=b"%PDF-test",
        settings=settings,
        mineru_client=FakeMinerUClient(),
    )
    docx_response = parse_document(
        file_name="manual.docx",
        content=b"PK-test",
        settings=settings,
        mineru_client=FakeMinerUClient(),
    )

    assert calls == [SupportedFileType.PDF, SupportedFileType.DOCX]
    assert pdf_response.blocks[0].text == "MinerU parsed text"
    assert docx_response.blocks[0].text == "MinerU parsed text"


def test_document_parser_keeps_txt_local_when_mineru_is_enabled() -> None:
    response = parse_document(
        file_name="notes.txt",
        content="本地纯文本".encode(),
        settings=_settings(),
    )

    assert response.file_type == SupportedFileType.TXT
    assert response.blocks[0].text == "本地纯文本"
