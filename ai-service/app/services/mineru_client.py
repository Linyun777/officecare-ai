import json
import re
import time
from collections.abc import Callable
from dataclasses import dataclass
from html import unescape
from html.parser import HTMLParser
from io import BytesIO
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen
from zipfile import BadZipFile, ZipFile

import requests
from pypdf import PdfReader, PdfWriter
from pypdf.errors import PyPdfError

from app.core.config import Settings
from app.core.exceptions import ServiceError
from app.loaders.base import ParsedContent
from app.schemas.document import BlockType, DocumentBlock, SupportedFileType


_API_RESPONSE_LIMIT_BYTES = 2 * 1024 * 1024
_PENDING_STATES = {"waiting-file", "uploading", "pending", "running", "converting"}
_SKIPPED_TYPES = {"header", "footer", "page_number"}
_HTML_TAG_PATTERN = re.compile(r"<[A-Za-z!/][^>]*>")


@dataclass(frozen=True, slots=True)
class _UploadPart:
    file_name: str
    data_id: str
    content: bytes
    page_offset: int = 0
    page_count: int | None = None


class MinerUClient:
    """Synchronous wrapper around MinerU's asynchronous signed-upload API."""

    def __init__(
        self,
        settings: Settings,
        *,
        opener: Callable[..., Any] | None = None,
        upload_session: requests.Session | None = None,
        sleeper: Callable[[float], None] | None = None,
        monotonic: Callable[[], float] | None = None,
    ) -> None:
        self._settings = settings
        self._base_url = settings.mineru_base_url.rstrip("/")
        self._token = settings.mineru_token()
        self._opener = opener or urlopen
        self._upload_session = upload_session or requests.Session()
        self._sleep = sleeper or time.sleep
        self._monotonic = monotonic or time.monotonic

    def parse(
        self,
        *,
        file_name: str,
        file_type: SupportedFileType,
        content: bytes,
        file_hash: str,
    ) -> ParsedContent:
        self._require_configuration()
        parts, source_page_count = self._prepare_upload_parts(
            file_name=file_name,
            file_type=file_type,
            content=content,
            file_hash=file_hash,
        )
        batch_id, upload_urls = self._create_upload(parts)
        for part, upload_url in zip(parts, upload_urls, strict=True):
            self._upload_file(upload_url, part.content)
        result_urls = self._wait_for_results(batch_id=batch_id, parts=parts)

        parsed_parts: list[ParsedContent] = []
        downloaded_bytes = 0
        for result_url in result_urls:
            archive = self._download_result(result_url)
            downloaded_bytes += len(archive)
            if downloaded_bytes > self._settings.mineru_max_result_size_bytes:
                raise ServiceError(
                    status_code=502,
                    code="MINERU_RESULT_TOO_LARGE",
                    message=(
                        "The combined MinerU parsing result exceeds the "
                        "configured size limit."
                    ),
                )
            parsed_parts.append(
                parse_mineru_archive(
                    archive,
                    file_type=file_type,
                    max_uncompressed_bytes=(
                        self._settings.mineru_max_result_size_bytes
                    ),
                )
            )
        return _merge_parsed_parts(
            parts,
            parsed_parts,
            file_type=file_type,
            source_page_count=source_page_count,
        )

    def _require_configuration(self) -> None:
        parsed_url = urlparse(self._base_url)
        if (
            not self._token
            or parsed_url.scheme not in {"http", "https"}
            or not parsed_url.netloc
        ):
            raise ServiceError(
                status_code=503,
                code="MINERU_NOT_CONFIGURED",
                message="The MinerU base URL or API token is not configured.",
            )

    def _prepare_upload_parts(
        self,
        *,
        file_name: str,
        file_type: SupportedFileType,
        content: bytes,
        file_hash: str,
    ) -> tuple[list[_UploadPart], int | None]:
        original = _UploadPart(
            file_name=file_name,
            data_id=file_hash,
            content=content,
        )
        if file_type != SupportedFileType.PDF:
            return [original], None

        try:
            reader = PdfReader(BytesIO(content), strict=False)
            page_count = len(reader.pages)
        except (OSError, ValueError, PyPdfError):
            return [original], None

        page_limit = self._settings.mineru_max_pages_per_part
        if page_count <= page_limit:
            return [original], page_count

        name_stem = file_name.rsplit(".", 1)[0]
        parts: list[_UploadPart] = []
        try:
            for part_index, start in enumerate(
                range(0, page_count, page_limit),
                start=1,
            ):
                end = min(start + page_limit, page_count)
                writer = PdfWriter()
                for page_index in range(start, end):
                    writer.add_page(reader.pages[page_index])
                output = BytesIO()
                writer.write(output)
                parts.append(
                    _UploadPart(
                        file_name=(
                            f"{name_stem}.part-{part_index:03d}-"
                            f"{start + 1}-{end}.pdf"
                        ),
                        data_id=f"{file_hash}.part-{part_index:03d}",
                        content=output.getvalue(),
                        page_offset=start,
                        page_count=end - start,
                    )
                )
        except (OSError, ValueError, PyPdfError, IndexError) as exc:
            raise ServiceError(
                status_code=422,
                code="MINERU_PDF_SPLIT_FAILED",
                message=(
                    "The PDF exceeds MinerU's page limit and could not be "
                    "split into safe parsing parts."
                ),
            ) from exc
        return parts, page_count

    def _create_upload(
        self,
        parts: list[_UploadPart],
    ) -> tuple[str, list[str]]:
        payload = self._request_json(
            method="POST",
            url=f"{self._base_url}/api/v4/file-urls/batch",
            body={
                "files": [
                    {
                        "name": part.file_name,
                        "data_id": part.data_id,
                        "is_ocr": self._settings.mineru_is_ocr,
                    }
                    for part in parts
                ],
                "model_version": self._settings.mineru_model_version,
                "language": self._settings.mineru_language,
                "enable_table": self._settings.mineru_enable_table,
                "enable_formula": self._settings.mineru_enable_formula,
            },
        )
        data = payload.get("data")
        if not isinstance(data, dict):
            self._invalid_response("MinerU did not return upload task data.")
        batch_id = data.get("batch_id")
        file_urls = data.get("file_urls")
        if (
            not isinstance(batch_id, str)
            or not batch_id.strip()
            or not isinstance(file_urls, list)
            or len(file_urls) != len(parts)
            or any(
                not isinstance(file_url, str) or not file_url.strip()
                for file_url in file_urls
            )
        ):
            self._invalid_response("MinerU returned an invalid signed upload response.")
        return batch_id.strip(), [file_url.strip() for file_url in file_urls]

    def _upload_file(self, upload_url: str, content: bytes) -> None:
        self._validate_remote_url(upload_url, "signed upload")
        try:
            # MinerU's signed upload URL must not receive Authorization or
            # Content-Type. requests leaves Content-Type unset for raw bytes,
            # while urllib would add application/x-www-form-urlencoded.
            with self._upload_session.put(
                upload_url,
                data=content,
                timeout=self._settings.mineru_request_timeout_seconds,
            ) as response:
                if not 200 <= response.status_code < 300:
                    self._raise_signed_url_error(
                        response.status_code,
                        operation="upload",
                    )
        except requests.RequestException as exc:
            raise ServiceError(
                status_code=503,
                code="MINERU_UNAVAILABLE",
                message="The document could not be uploaded to MinerU.",
            ) from exc

    def _wait_for_results(
        self,
        *,
        batch_id: str,
        parts: list[_UploadPart],
    ) -> list[str]:
        deadline = self._monotonic() + self._settings.mineru_timeout_seconds
        encoded_batch_id = quote(batch_id, safe="")

        while True:
            payload = self._request_json(
                method="GET",
                url=(
                    f"{self._base_url}/api/v4/extract-results/batch/"
                    f"{encoded_batch_id}"
                ),
            )
            results = self._extract_results(payload)
            completed_urls: list[str] = []
            all_done = True
            for part in parts:
                result = self._select_result(results, part)
                if result is None:
                    all_done = False
                    continue
                state = str(result.get("state") or "").strip().lower()
                if state == "done":
                    result_url = result.get("full_zip_url")
                    if not isinstance(result_url, str) or not result_url.strip():
                        self._invalid_response(
                            "MinerU completed parsing without a result archive URL."
                        )
                    completed_urls.append(result_url.strip())
                    continue
                all_done = False
                if state == "failed":
                    provider_message = _safe_message(result.get("err_msg"))
                    raise ServiceError(
                        status_code=422,
                        code="MINERU_PARSE_FAILED",
                        message=(
                            provider_message
                            or "MinerU could not parse the document."
                        ),
                    )
                if state not in _PENDING_STATES:
                    self._invalid_response(
                        "MinerU returned an unknown parsing state."
                    )
            if all_done and len(completed_urls) == len(parts):
                return completed_urls

            remaining = deadline - self._monotonic()
            if remaining <= 0:
                raise ServiceError(
                    status_code=504,
                    code="MINERU_PARSE_TIMEOUT",
                    message="MinerU did not finish parsing within the configured timeout.",
                )
            self._sleep(min(self._settings.mineru_poll_interval_seconds, remaining))

    def _extract_results(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        data = payload.get("data")
        results = data.get("extract_result") if isinstance(data, dict) else None
        if not isinstance(results, list):
            self._invalid_response("MinerU did not return a parsing result list.")
        return [item for item in results if isinstance(item, dict)]

    def _select_result(
        self,
        results: list[dict[str, Any]],
        part: _UploadPart,
    ) -> dict[str, Any] | None:
        matching = [
            item
            for item in results
            if (
                item.get("data_id") == part.data_id
                or item.get("file_name") == part.file_name
            )
        ]
        if matching:
            return matching[0]
        return None

    def _download_result(self, result_url: str) -> bytes:
        self._validate_remote_url(result_url, "result archive")
        request = Request(result_url, method="GET")
        limit = self._settings.mineru_max_result_size_bytes
        try:
            with self._opener(
                request,
                timeout=self._settings.mineru_request_timeout_seconds,
            ) as response:
                content = response.read(limit + 1)
        except HTTPError as exc:
            self._raise_http_error(exc, operation="download")
        except (URLError, TimeoutError, OSError) as exc:
            raise ServiceError(
                status_code=503,
                code="MINERU_UNAVAILABLE",
                message="The MinerU parsing result could not be downloaded.",
            ) from exc

        if len(content) > limit:
            raise ServiceError(
                status_code=502,
                code="MINERU_RESULT_TOO_LARGE",
                message="The MinerU parsing result exceeds the configured size limit.",
            )
        return content

    def _request_json(
        self,
        *,
        method: str,
        url: str,
        body: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self._token}",
        }
        encoded_body = None
        if body is not None:
            encoded_body = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = Request(
            url,
            data=encoded_body,
            headers=headers,
            method=method,
        )
        try:
            with self._opener(
                request,
                timeout=self._settings.mineru_request_timeout_seconds,
            ) as response:
                raw = response.read(_API_RESPONSE_LIMIT_BYTES + 1)
        except HTTPError as exc:
            self._raise_http_error(exc, operation="request")
        except (URLError, TimeoutError, OSError) as exc:
            raise ServiceError(
                status_code=503,
                code="MINERU_UNAVAILABLE",
                message="The MinerU API is unavailable or timed out.",
            ) from exc

        if len(raw) > _API_RESPONSE_LIMIT_BYTES:
            self._invalid_response("The MinerU API response is too large.")
        try:
            payload = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ServiceError(
                status_code=502,
                code="MINERU_INVALID_RESPONSE",
                message="MinerU returned an invalid JSON response.",
            ) from exc
        if not isinstance(payload, dict):
            self._invalid_response("MinerU returned an invalid JSON object.")

        provider_code = payload.get("code")
        if provider_code not in {0, "0"}:
            code_text = str(provider_code or "")
            if code_text in {"A0202", "A0211"}:
                raise ServiceError(
                    status_code=503,
                    code="MINERU_AUTH_FAILED",
                    message="The MinerU API token is invalid or expired.",
                )
            raise ServiceError(
                status_code=502,
                code="MINERU_API_ERROR",
                message=_safe_message(payload.get("msg"))
                or "MinerU rejected the parsing request.",
                details={"providerCode": code_text},
            )
        return payload

    def _raise_http_error(self, exc: HTTPError, *, operation: str) -> None:
        if operation == "request" and exc.code in {401, 403}:
            raise ServiceError(
                status_code=503,
                code="MINERU_AUTH_FAILED",
                message="The MinerU API token is invalid or expired.",
            ) from exc
        if exc.code == 429:
            raise ServiceError(
                status_code=503,
                code="MINERU_RATE_LIMITED",
                message="MinerU rate-limited the parsing request.",
            ) from exc
        if operation == "download":
            self._raise_signed_url_error(exc.code, operation=operation)
        raise ServiceError(
            status_code=502,
            code="MINERU_HTTP_ERROR",
            message=f"MinerU returned HTTP {exc.code} during result {operation}.",
        ) from exc

    def _raise_signed_url_error(self, status_code: int, *, operation: str) -> None:
        if operation == "upload":
            raise ServiceError(
                status_code=502,
                code="MINERU_UPLOAD_REJECTED",
                message=(
                    "MinerU created the parsing task, but its signed upload URL "
                    f"rejected the file with HTTP {status_code}."
                ),
            )
        raise ServiceError(
            status_code=502,
            code="MINERU_RESULT_DOWNLOAD_FAILED",
            message=(
                "MinerU finished parsing, but its signed result URL rejected "
                f"the download with HTTP {status_code}."
            ),
        )

    def _validate_remote_url(self, value: str, label: str) -> None:
        parsed = urlparse(value)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            self._invalid_response(f"MinerU returned an invalid {label} URL.")

    def _invalid_response(self, message: str) -> None:
        raise ServiceError(
            status_code=502,
            code="MINERU_INVALID_RESPONSE",
            message=message,
        )


def parse_mineru_archive(
    content: bytes,
    *,
    file_type: SupportedFileType,
    max_uncompressed_bytes: int,
) -> ParsedContent:
    try:
        with ZipFile(BytesIO(content)) as archive:
            content_name = _select_content_list(archive)
            content_items = _read_json_member(
                archive,
                content_name,
                max_uncompressed_bytes=max_uncompressed_bytes,
            )
            page_count = _read_page_count(
                archive,
                max_uncompressed_bytes=max_uncompressed_bytes,
            )
    except ServiceError:
        raise
    except (BadZipFile, OSError, ValueError) as exc:
        raise ServiceError(
            status_code=502,
            code="MINERU_INVALID_RESULT",
            message="MinerU returned a damaged or invalid result archive.",
        ) from exc

    if not isinstance(content_items, list):
        raise ServiceError(
            status_code=502,
            code="MINERU_INVALID_RESULT",
            message="MinerU content_list.json does not contain a list.",
        )

    blocks: list[DocumentBlock] = []
    max_page_number = 0
    for item in content_items:
        if not isinstance(item, dict):
            continue
        converted = _convert_content_item(item, file_type=file_type)
        if converted is None:
            continue
        block_type, text, page_number = converted
        if page_number is not None:
            max_page_number = max(max_page_number, page_number)
        blocks.append(
            DocumentBlock(
                sequence=len(blocks) + 1,
                page_number=page_number,
                block_type=block_type,
                text=text,
            )
        )

    if not blocks:
        raise ServiceError(
            status_code=422,
            code="NO_TEXT_EXTRACTED",
            message="MinerU did not find extractable text in the document.",
        )

    if file_type != SupportedFileType.PDF:
        page_count = None
    elif page_count is None:
        page_count = max_page_number or None
    return ParsedContent(blocks=blocks, page_count=page_count)


def _merge_parsed_parts(
    parts: list[_UploadPart],
    parsed_parts: list[ParsedContent],
    *,
    file_type: SupportedFileType,
    source_page_count: int | None,
) -> ParsedContent:
    if len(parts) != len(parsed_parts):
        raise ServiceError(
            status_code=502,
            code="MINERU_INVALID_RESULT",
            message="MinerU returned an incomplete set of parsing results.",
        )

    blocks: list[DocumentBlock] = []
    warnings: list[str] = []
    for part, parsed in zip(parts, parsed_parts, strict=True):
        warnings.extend(parsed.warnings)
        for block in parsed.blocks:
            page_number = block.page_number
            if file_type == SupportedFileType.PDF and page_number is not None:
                page_number += part.page_offset
            blocks.append(
                DocumentBlock(
                    sequence=len(blocks) + 1,
                    page_number=page_number,
                    block_type=block.block_type,
                    text=block.text,
                )
            )

    if len(parts) > 1:
        warnings.append(
            f"The PDF was parsed by MinerU in {len(parts)} page-limited parts."
        )

    page_count = source_page_count
    if page_count is None and len(parsed_parts) == 1:
        page_count = parsed_parts[0].page_count
    return ParsedContent(
        blocks=blocks,
        page_count=page_count,
        warnings=warnings,
    )


def _select_content_list(archive: ZipFile) -> str:
    candidates = [
        name
        for name in archive.namelist()
        if name.lower().endswith("content_list.json")
        and not name.lower().endswith("content_list_v2.json")
        and not name.endswith("/")
    ]
    if not candidates:
        raise ServiceError(
            status_code=502,
            code="MINERU_INVALID_RESULT",
            message="MinerU result archive does not contain content_list.json.",
        )
    return sorted(candidates, key=lambda value: (value.count("/"), len(value)))[0]


def _read_page_count(
    archive: ZipFile,
    *,
    max_uncompressed_bytes: int,
) -> int | None:
    candidates = [
        name
        for name in archive.namelist()
        if (
            name.lower().endswith("_middle.json")
            or name.lower().endswith("/middle.json")
            or name.lower().endswith("/layout.json")
            or name.lower() == "layout.json"
        )
        and not name.endswith("/")
    ]
    for name in sorted(candidates, key=len):
        payload = _read_json_member(
            archive,
            name,
            max_uncompressed_bytes=max_uncompressed_bytes,
        )
        if isinstance(payload, dict) and isinstance(payload.get("pdf_info"), list):
            return len(payload["pdf_info"])
    return None


def _read_json_member(
    archive: ZipFile,
    name: str,
    *,
    max_uncompressed_bytes: int,
) -> Any:
    info = archive.getinfo(name)
    if info.file_size > max_uncompressed_bytes:
        raise ServiceError(
            status_code=502,
            code="MINERU_RESULT_TOO_LARGE",
            message="A MinerU result file exceeds the configured size limit.",
        )
    try:
        return json.loads(archive.read(info).decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ServiceError(
            status_code=502,
            code="MINERU_INVALID_RESULT",
            message=f"MinerU returned invalid JSON in {name}.",
        ) from exc


def _convert_content_item(
    item: dict[str, Any],
    *,
    file_type: SupportedFileType,
) -> tuple[BlockType, str, int | None] | None:
    content_type = str(item.get("type") or "").strip().lower()
    if content_type in _SKIPPED_TYPES:
        return None

    page_number: int | None = None
    if file_type == SupportedFileType.PDF:
        page_index = item.get("page_idx")
        if not isinstance(page_index, int) or isinstance(page_index, bool) or page_index < 0:
            raise ServiceError(
                status_code=502,
                code="MINERU_INVALID_RESULT",
                message="MinerU returned PDF content without a valid page_idx.",
            )
        page_number = page_index + 1

    block_type = BlockType.PARAGRAPH
    text = ""
    if content_type in {"text", "title"}:
        text = _join_parts(item.get("text"), item.get("content"))
        text_level = item.get("text_level")
        if (
            content_type == "title"
            or (
                isinstance(text_level, int)
                and not isinstance(text_level, bool)
                and text_level > 0
            )
        ):
            block_type = BlockType.HEADING
    elif content_type == "table":
        block_type = BlockType.TABLE
        text = _join_parts(
            item.get("table_caption"),
            _normalize_table_body(_text_value(item.get("table_body"))),
            item.get("table_footnote"),
        )
    elif content_type == "list":
        text = _join_parts(item.get("list_items"))
    elif content_type == "code":
        text = _join_parts(
            item.get("code_caption"),
            item.get("code_body"),
            item.get("code_footnote"),
        )
    elif content_type in {"image", "chart"}:
        text = _join_parts(
            item.get(f"{content_type}_caption"),
            item.get("content"),
            item.get(f"{content_type}_footnote"),
        )
    elif content_type in {
        "equation",
        "aside_text",
        "page_footnote",
        "ref_text",
    }:
        text = _join_parts(item.get("text"), item.get("content"))
    else:
        text = _join_parts(item.get("text"), item.get("content"))

    normalized = text.strip()
    if not normalized:
        return None
    return block_type, normalized, page_number


def _join_parts(*values: Any) -> str:
    parts: list[str] = []
    for value in values:
        if isinstance(value, str):
            normalized = value.strip()
            if normalized:
                parts.append(normalized)
        elif isinstance(value, list):
            for item in value:
                normalized = _text_value(item).strip()
                if normalized:
                    parts.append(normalized)
    return "\n".join(parts)


def _text_value(value: Any) -> str:
    return value if isinstance(value, str) else ""


def _normalize_table_body(value: str) -> str:
    if not value:
        return ""
    if not _HTML_TAG_PATTERN.search(value):
        return value.strip()
    parser = _TableTextExtractor()
    parser.feed(value)
    parser.close()
    return parser.text()


class _TableTextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._parts: list[str] = []

    def handle_data(self, data: str) -> None:
        normalized = " ".join(data.split())
        if normalized:
            self._parts.append(normalized)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() in {"td", "th"}:
            self._parts.append("\t")
        elif tag.lower() == "tr":
            self._parts.append("\n")

    def text(self) -> str:
        value = unescape("".join(self._parts))
        lines = [
            re.sub(r"\t+$", "", re.sub(r"\t{2,}", "\t", line)).strip()
            for line in value.splitlines()
        ]
        return "\n".join(line for line in lines if line)


def _safe_message(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    normalized = " ".join(value.split()).strip()
    return normalized[:300]
