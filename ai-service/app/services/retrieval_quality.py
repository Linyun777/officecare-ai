import math
import re
import unicodedata


_CJK_SEQUENCE_PATTERN = re.compile(r"[\u3400-\u9fff]+")
_IDENTIFIER_PATTERN = re.compile(
    r"[a-z]*\d+(?:[-._/][a-z0-9]+)*[a-z0-9]*",
    re.IGNORECASE,
)
_NUMBER_WITH_UNIT_PATTERN = re.compile(
    r"\d+(?:\.\d+)?(?:毫米|厘米|页|张|秒|分钟|mm|cm|ppm)",
    re.IGNORECASE,
)
_SUPPLY_MODEL_PATTERN = re.compile(r"(?:tn|dr)\d{3,}[a-z0-9]*")
_MODEL_CODE_PATTERN = re.compile(
    r"(?<![a-z0-9])[a-z]{2,6}"
    r"(?:[\s._/-]?[a-z]{1,3})?[\s._/-]?\d{3,5}[a-z]{0,3}"
    r"(?![a-z0-9])",
    re.IGNORECASE,
)
_SEARCH_TEXT_PATTERN = re.compile(r"[^0-9a-z\u3400-\u9fff]+")
_BREADCRUMB_PATTERN = re.compile(r"(?:^|\n)\s*主页\s*[>›»]")
_APPLICABLE_MODEL_PATTERN = re.compile(
    r"(?:相关型号|适用型号|适用机型)\s*[:：]\s*([^\n\r]+)"
    r"|(?:仅)?适用于\s+([^\n\r]+)",
    re.IGNORECASE,
)
_MODEL_FAMILY_PATTERN = re.compile(
    r"(?<![a-z0-9])(dcp|mfc|hl)\s*型号(?![a-z0-9])",
    re.IGNORECASE,
)
_PRINTER_MODEL_FAMILIES = ("dcp", "mfc", "hl")
_MODEL_LIST_LINE_PATTERN = re.compile(r"(?:^|\n)\s*>>\s*([^\n\r]+)")
_ERROR_LABEL_PATTERN = re.compile(r"[\[\u3010]([^\]\u3011]{2,40})[\]\u3011]")
_ERROR_LABEL_HINTS = (
    "错误",
    "失败",
    "无法",
    "不能",
    "未",
    "已",
    "用尽",
    "卡纸",
    "拒绝",
    "禁用",
    "满",
    "中断",
    "超",
    "限制",
    "异常",
    "警告",
    "更换",
    "关闭",
    "打开",
    "请",
)
_QUOTED_PHRASE_PATTERN = re.compile(
    r"[\u201c\u201d\"\u0027\u2018\u2019\u300c\u300d\u300e\u300f]"
    r"([^\u201c\u201d\"\u0027\u2018\u2019\u300c\u300d\u300e\u300f]{2,40})"
    r"[\u201c\u201d\"\u0027\u2018\u2019\u300c\u300d\u300e\u300f]"
)
_ASCII_TECH_TERM_PATTERN = re.compile(
    r"(?<![a-z0-9])[a-z]{2,}(?:[-._/][a-z0-9]{2,})*(?![a-z0-9])",
    re.IGNORECASE,
)

_NAVIGATION_HEADINGS = frozenset(
    {
        "相关信息",
        "相关主题",
        "相关内容",
        "另请参阅",
        "另请参考",
        "参见",
        "主页",
    }
)
_QUESTION_FILLERS = (
    "请问",
    "我想知道",
    "应该如何",
    "应该怎么",
    "怎么办",
    "怎么处理",
    "如何处理",
    "如何",
    "怎么",
    "是否可以",
    "是否",
    "可以吗",
    "是什么",
    "表示什么",
    "什么",
)
_GENERIC_TERMS = frozenset(
    {
        "打印机",
        "打印",
        "设备",
        "页面",
        "问题",
        "处理",
        "如何",
        "怎么",
    }
)
_PRINTER_BRAND_ALIASES = {
    "brother": ("brother", "兄弟"),
    "hp": ("hp", "惠普"),
    "canon": ("canon", "佳能"),
    "epson": ("epson", "爱普生"),
    "lenovo": ("lenovo", "联想"),
    "pantum": ("pantum", "奔图"),
    "fuji": ("fujifilm", "fuji xerox", "富士胶片", "富士施乐"),
    "xerox": ("xerox", "施乐"),
    "ricoh": ("ricoh", "理光"),
    "kyocera": ("kyocera", "京瓷"),
    "lexmark": ("lexmark", "利盟"),
}


def is_navigation_only(text: str, *, heading: str = "") -> bool:
    """Return True only for clear MinerU navigation/cross-reference fragments."""
    normalized_heading = heading.strip().rstrip("：:")
    if normalized_heading in _NAVIGATION_HEADINGS:
        return True
    if _BREADCRUMB_PATTERN.search(normalized_heading):
        return True

    normalized_text = _normalize_lines(text)
    if not normalized_text:
        return False

    first_line = normalized_text.splitlines()[0].strip().rstrip("：:")
    if first_line in _NAVIGATION_HEADINGS:
        return True
    return len(normalized_text) <= 600 and bool(
        _BREADCRUMB_PATTERN.search(normalized_text)
    )


def lexical_relevance_scores(
    question: str,
    documents: list[str],
) -> list[float]:
    """Score exact terms with corpus-aware weights; no tokenizer dependency required."""
    if not documents:
        return []

    terms = _query_terms(question)
    if not terms:
        return [0.0] * len(documents)

    normalized_documents = [_compact_search_text(document) for document in documents]
    document_frequency = {
        term: sum(term in document for document in normalized_documents)
        for term in terms
    }
    corpus_size = len(normalized_documents)
    weighted_terms = {
        term: weight
        * (math.log((corpus_size + 1) / (document_frequency[term] + 1)) + 1)
        for term, weight in terms.items()
    }
    denominator = sum(weighted_terms.values())
    if denominator <= 0:
        return [0.0] * len(documents)

    scores: list[float] = []
    compact_question = _compact_search_text(question)
    for document, normalized_document in zip(documents, normalized_documents):
        matched_weight = sum(
            weight
            for term, weight in weighted_terms.items()
            if term in normalized_document
        )
        base_score = matched_weight / denominator
        anchor_bonus = min(
            0.35,
            _quoted_phrase_bonus(question, normalized_document)
            + _error_label_bonus(compact_question, document),
        )
        scores.append(
            min(
                1.0,
                base_score
                + _intent_bonus(question, normalized_document)
                + anchor_bonus,
            )
        )
    return scores


def has_scope_conflict(
    question: str,
    *,
    selected_brand: str | None,
    selected_model: str | None,
) -> bool:
    """Detect explicit brand/model text that contradicts the selected KB scope."""
    question_brands = _mentioned_brands(question)
    selected_brands = _mentioned_brands(selected_brand or "")
    if question_brands and selected_brands and not question_brands.issubset(
        selected_brands
    ):
        return True

    # Scope validation must only compare actual printer model identifiers.
    # Fault codes and consumable identifiers (for example OC-MQ-TEST-0904 or
    # TN-2425) also match the broad pattern used by lexical search, but they do
    # not represent a conflicting device model.
    question_models = _printer_model_codes(question)
    selected_models = _printer_model_codes(selected_model or "")
    return bool(
        question_models
        and selected_models
        and question_models.isdisjoint(selected_models)
    )


def has_unsupported_model_capability(
    question: str,
    *,
    selected_model: str | None,
) -> bool:
    """Block known product-family capability conflicts before retrieval.

    Brother DCP and HL product families do not provide fax operation. A
    multi-model manual can still contain MFC fax chapters, so relying only on
    document-level model metadata would expose inapplicable procedures.
    """
    selected_families = {
        _model_family(model)
        for model in _model_codes(selected_model or "")
    }
    selected_families.discard(None)
    if not selected_families.intersection({"dcp", "hl"}):
        return False
    compact_question = _compact_search_text(question)
    return "传真" in compact_question or "fax" in compact_question


def declared_model_codes(
    text: str,
    *,
    heading: str = "",
) -> set[str]:
    """Return only the model codes explicitly declared as applicable."""
    normalized_text = unicodedata.normalize("NFKC", f"{heading}\n{text}")
    declared_models: set[str] = set()
    for match in _APPLICABLE_MODEL_PATTERN.finditer(normalized_text):
        declaration = match.group(1) or match.group(2) or ""
        declared_models.update(_model_codes(declaration))
    for match in _MODEL_LIST_LINE_PATTERN.finditer(normalized_text):
        declared_models.update(_model_codes(match.group(1)))
    declared_models.update(_model_only_heading_codes(heading))
    return declared_models


def section_model_codes(
    text: str,
    *,
    heading: str = "",
) -> set[str]:
    """Return model codes declared by labels or standalone group headers.

    Manuals sometimes continue a model-specific table on the next page with a
    bare line such as ``DCP-7195DW/MFC-7895DW``. Such a line is a section
    scope, not a mention, so it must participate in model filtering.
    """
    normalized_text = unicodedata.normalize("NFKC", text)
    declared_models = declared_model_codes(normalized_text, heading=heading)
    for line in normalized_text.splitlines():
        declared_models.update(
            model_code
            for model_code in _model_only_heading_codes(line)
            if _model_family(model_code) is not None
        )
    return declared_models


def printer_model_codes(text: str) -> set[str]:
    """Return printer model identifiers, excluding supplies and fault codes."""
    return _printer_model_codes(text)


def is_applicable_to_model(
    text: str,
    *,
    heading: str = "",
    selected_model: str | None,
) -> bool:
    """Reject only chunks that explicitly declare a different applicable model.

    Multi-model manuals often mark model-specific sections with labels such as
    ``相关型号: MFC-7895DW``. The whole document still belongs to one knowledge
    base in the MVP schema, so this content-level guard prevents those chunks
    from leaking into another selected model. Chunks without a recognizable
    declaration remain eligible because most shared instructions have no label.
    """
    selected_models = _model_codes(selected_model or "")
    if not selected_models:
        return True

    declared_models = declared_model_codes(text, heading=heading)
    if declared_models:
        return not selected_models.isdisjoint(declared_models)

    normalized_text = unicodedata.normalize("NFKC", f"{heading}\n{text}")
    selected_families = {_model_family(model) for model in selected_models}
    selected_families.discard(None)
    declared_families = {
        match.group(1).casefold()
        for match in _MODEL_FAMILY_PATTERN.finditer(normalized_text)
    }
    if declared_families and selected_families:
        return not selected_families.isdisjoint(declared_families)
    return True


def query_without_selected_scope(
    question: str,
    *,
    selected_brand: str | None,
    selected_model: str | None,
) -> str:
    """Remove already-filtered brand/model labels from the semantic query.

    Milvus scalar filters already enforce the selected brand and model. Keeping
    a model code such as ``MFC-7895DW`` in lexical scoring makes every
    model-specific manual chunk look equally relevant because most of them
    repeat the same applicability label. The remaining fault or operation text
    is a better retrieval query. If stripping would leave no useful text, keep
    the original question.
    """
    original = question.strip()
    stripped = original
    for scope_value in (selected_model, selected_brand):
        pattern = _scope_value_pattern(scope_value or "")
        if pattern is not None:
            stripped = pattern.sub(" ", stripped)
    stripped = re.sub(r"\s+", " ", stripped).strip()
    return stripped if len(_compact_search_text(stripped)) >= 2 else original


def _query_terms(question: str) -> dict[str, float]:
    normalized = unicodedata.normalize("NFKC", question).casefold()
    terms: dict[str, float] = {}

    for match in _NUMBER_WITH_UNIT_PATTERN.finditer(normalized):
        _add_term(terms, _compact_search_text(match.group()), 5.0)
    for match in _IDENTIFIER_PATTERN.finditer(normalized):
        identifier = _compact_search_text(match.group())
        _add_term(terms, identifier, 4.0 if len(identifier) >= 3 else 3.0)
    for match in _ASCII_TECH_TERM_PATTERN.finditer(normalized):
        raw_term = match.group()
        term = _compact_search_text(raw_term)
        _add_term(terms, term, 3.5 if re.search(r"[-._/]", raw_term) else 2.5)

    for sequence in _CJK_SEQUENCE_PATTERN.findall(normalized):
        cleaned = sequence
        for filler in _QUESTION_FILLERS:
            cleaned = cleaned.replace(filler, "")
        if len(cleaned) < 2:
            continue

        if len(cleaned) <= 12:
            _add_term(terms, cleaned, 3.0)
        for size, weight in ((4, 2.0), (3, 1.5), (2, 1.0)):
            if len(cleaned) < size:
                continue
            for start in range(0, len(cleaned) - size + 1):
                term = cleaned[start:start + size]
                adjusted_weight = (
                    weight * 0.25 if term in _GENERIC_TERMS else weight
                )
                _add_term(terms, term, adjusted_weight)

    # Keep the local scan predictable even for a very long natural-language question.
    return dict(
        sorted(terms.items(), key=lambda item: (-item[1], -len(item[0]), item[0]))[
            :64
        ]
    )


def _add_term(terms: dict[str, float], term: str, weight: float) -> None:
    if len(term) < 2:
        return
    terms[term] = max(weight, terms.get(term, 0.0))


def _normalize_lines(text: str) -> str:
    return "\n".join(
        line.strip()
        for line in unicodedata.normalize("NFKC", text)
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .split("\n")
        if line.strip()
    )


def _compact_search_text(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    return _SEARCH_TEXT_PATTERN.sub("", normalized)


def _mentioned_brands(text: str) -> set[str]:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    mentions: set[str] = set()
    for brand, aliases in _PRINTER_BRAND_ALIASES.items():
        for alias in aliases:
            normalized_alias = alias.casefold()
            if normalized_alias.isascii():
                found = re.search(
                    rf"(?<![a-z0-9]){re.escape(normalized_alias)}(?![a-z0-9])",
                    normalized,
                )
            else:
                found = normalized_alias in normalized
            if found:
                mentions.add(brand)
                break
    return mentions


def _model_codes(text: str) -> set[str]:
    normalized = unicodedata.normalize("NFKC", text).casefold()
    for dash in "‐‑‒–—−":
        normalized = normalized.replace(dash, "-")
    return {
        _compact_search_text(match.group())
        for match in _MODEL_CODE_PATTERN.finditer(normalized)
    }


def _printer_model_codes(text: str) -> set[str]:
    """Return model identifiers that are safe to use for scope enforcement."""
    return {
        model_code
        for model_code in _model_codes(text)
        if _model_family(model_code) is not None
    }


def _model_only_heading_codes(heading: str) -> set[str]:
    normalized = unicodedata.normalize("NFKC", heading).casefold()
    codes = _model_codes(normalized)
    if not codes:
        return set()
    remainder = _MODEL_CODE_PATTERN.sub("", normalized)
    remainder = re.sub(r"[\s,，、/|&和及._()（）-]+", "", remainder)
    return codes if not remainder else set()


def _model_family(model_code: str) -> str | None:
    for family in _PRINTER_MODEL_FAMILIES:
        if model_code.startswith(family):
            return family
    return None


def _scope_value_pattern(value: str) -> re.Pattern[str] | None:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    for dash in "‐‑‒–—−":
        normalized = normalized.replace(dash, "-")
    parts = re.findall(r"[a-z]+|\d+|[\u3400-\u9fff]+", normalized)
    if not parts:
        return None
    body = r"[\s._/\\‐‑‒–—−-]*".join(re.escape(part) for part in parts)
    return re.compile(rf"(?<![a-z0-9]){body}(?![a-z0-9])", re.IGNORECASE)


def _error_label_bonus(compact_question: str, document: str) -> float:
    """Boost a chunk whose UI error label appears verbatim in the question."""
    bonus = 0.0
    for match in _ERROR_LABEL_PATTERN.finditer(document):
        label = _compact_search_text(match.group(1))
        if (
            len(label) >= 3
            and any(hint in label for hint in _ERROR_LABEL_HINTS)
            and label in compact_question
        ):
            bonus += 0.2
    return min(bonus, 0.3)


def _quoted_phrase_bonus(question: str, compact_document: str) -> float:
    """Boost documents that contain a UI message quoted in the question."""
    normalized_question = unicodedata.normalize("NFKC", question)
    bonus = 0.0
    for match in _QUOTED_PHRASE_PATTERN.finditer(normalized_question):
        phrase = _compact_search_text(match.group(1))
        if len(phrase) >= 2 and phrase in compact_document:
            bonus += 0.2
    return min(bonus, 0.3)


def _intent_bonus(question: str, compact_document: str) -> float:
    """Boost technical answer shapes that query wording often does not share."""
    compact_question = _compact_search_text(question)
    bonus = 0.0

    asks_speed = (
        "一分钟" in compact_question
        or "每分钟" in compact_question
        or "打印速度" in compact_question
        or "多少页" in compact_question
    )
    if asks_speed and (
        "页分钟" in compact_document
        or "打印速度" in compact_document
        or "ppm" in compact_document
    ):
        bonus += 0.35

    asks_supply_model = (
        "型号" in compact_question
        and (
            "墨粉盒" in compact_question
            or "硒鼓" in compact_question
            or "耗材" in compact_question
        )
    )
    if asks_supply_model and _SUPPLY_MODEL_PATTERN.search(compact_document):
        bonus += 0.35

    asks_drum_reset = (
        "重置" in compact_question
        and "硒鼓" in compact_question
        and "计数器" in compact_question
    )
    if asks_drum_reset and all(
        term in compact_document for term in ("重置", "硒鼓", "计数器")
    ):
        bonus += 0.25

    return bonus
