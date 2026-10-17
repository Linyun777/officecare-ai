from app.services.retrieval_quality import (
    declared_model_codes,
    has_scope_conflict,
    has_unsupported_model_capability,
    is_applicable_to_model,
    is_navigation_only,
    lexical_relevance_scores,
    query_without_selected_scope,
    section_model_codes,
)


def test_declared_model_codes_returns_only_explicit_labels() -> None:
    assert declared_model_codes(
        "相关型号: MFC-7895DW\n传真设置。"
    ) == {"mfc7895dw"}
    assert declared_model_codes(
        "设备内部卡纸时，请等待内部零件冷却。"
    ) == set()


def test_section_model_codes_recognizes_standalone_group_header() -> None:
    assert section_model_codes(
        "DCP-7195DW/MFC-7895DW\n错误信息 原因 措施"
    ) == {"dcp7195dw", "mfc7895dw"}
    assert section_model_codes(
        "设备内部发生卡纸时，请打开前盖。"
    ) == set()
    assert section_model_codes(
        "Windows 7\n设备设置"
    ) == set()


def test_navigation_classifier_is_conservative() -> None:
    assert is_navigation_only(
        "相关信息\n\n主页 > 故障排除 > 卡纸",
        heading="相关信息",
    )
    assert is_navigation_only("主页 > 故障排除 > 卡纸")
    assert not is_navigation_only(
        "打开前盖，取出墨粉盒和硒鼓单元组件。",
        heading="设备内部卡纸",
    )


def test_navigation_classifier_rejects_long_table_of_contents_breadcrumb() -> None:
    assert is_navigation_only(
        "PC-FAX 218\n" + "目录项目 " * 200,
        heading="主页 > 目录",
    )


def test_lexical_scoring_prioritizes_exact_number_and_fault_phrase() -> None:
    scores = lexical_relevance_scores(
        "打印页面每隔94毫米出现重复斑点怎么办？",
        [
            "设备支持无线网络打印。",
            "打印页面每隔94毫米出现黑色或白色斑点时，请清洁硒鼓。",
            "设备内部发生卡纸时打开前盖。",
        ],
    )

    assert scores[1] > scores[0]
    assert scores[1] > scores[2]
    assert scores[1] >= 0.5


def test_lexical_scoring_boosts_error_label_matching_question() -> None:
    scores = lexical_relevance_scores(
        "设备提示内存已满时应该如何处理？",
        [
            "[内存已满] 降低分辨率。清除内存中的传真。",
            "设备支持无线网络打印。",
        ],
    )

    assert scores[0] >= 0.18
    assert scores[0] > scores[1]


def test_lexical_scoring_ignores_short_menu_label() -> None:
    scores = lexical_relevance_scores(
        "如何维修传真板上的电源芯片？",
        [
            "[传真] [重拨] [暂停] [免提] 用于传真发送。",
            "[打印机] [网络] [测试打印] 设置打印机选项。",
            "设备支持无线网络打印。",
        ],
    )

    assert scores[0] < 0.18
    assert scores[1] < 0.18


def test_lexical_scoring_understands_speed_and_supply_answer_shapes() -> None:
    speed_scores = lexical_relevance_scores(
        "DCP-L2550DW一分钟可以打印多少页？",
        [
            "打印规格：A4单面打印速度最快34页/分钟。",
            "按停止退出键可以取消打印作业。",
        ],
    )
    supply_scores = lexical_relevance_scores(
        "适用的硒鼓和墨粉盒型号是什么？",
        [
            "标准墨粉盒TN-2412，高容量墨粉盒TN-2425，硒鼓单元DR-2450。",
            "更换墨粉盒时请打开前盖。",
        ],
    )

    assert speed_scores[0] > speed_scores[1]
    assert supply_scores[0] > supply_scores[1]


def test_scope_conflict_detects_other_brand_and_model() -> None:
    assert has_scope_conflict(
        "HP打印机发生卡纸怎么办？",
        selected_brand="Brother",
        selected_model="DCP-L2550DW",
    )
    assert has_scope_conflict(
        "MFC-7895DW如何发送传真？",
        selected_brand="Brother",
        selected_model="DCP-L2550DW",
    )
    assert not has_scope_conflict(
        "兄弟DCP-L2550DW设备内部卡纸怎么办？",
        selected_brand="Brother",
        selected_model="DCP-L2550DW",
    )


def test_scope_conflict_ignores_fault_and_consumable_identifiers() -> None:
    assert not has_scope_conflict(
        "故障码OC-MQ-TEST-0904应该如何处理？",
        selected_brand="Brother",
        selected_model="DCP-L2550DW",
    )
    assert not has_scope_conflict(
        "这台设备可以使用TN-2425墨粉盒吗？",
        selected_brand="Brother",
        selected_model="DCP-L2550DW",
    )


def test_model_applicability_rejects_explicit_other_model_section() -> None:
    assert not is_applicable_to_model(
        "相关型号: MFC-7895DW\n按[设置]进入传真菜单。",
        selected_model="DCP-L2550DW",
    )


def test_model_applicability_keeps_bare_group_header_chunk() -> None:
    assert is_applicable_to_model(
        "DCP-7195DW/MFC-7895DW\n错误信息 原因 措施",
        selected_model="DCP-L2550DW",
    )


def test_model_applicability_keeps_selected_and_multi_model_sections() -> None:
    assert is_applicable_to_model(
        "相关型号：DCP-L2550DW\n打开前盖并取出卡纸。",
        selected_model="DCP-L2550DW",
    )
    assert is_applicable_to_model(
        "适用机型：MFC-7895DW / DCP-L2550DW\n等待设备冷却。",
        selected_model="DCP-L2550DW",
    )
    assert is_applicable_to_model(
        "提示\n适用于 DCP-7090DW/DCP-L2535DW/DCP-L2550DW。",
        selected_model="DCP-L2550DW",
    )
    assert is_applicable_to_model(
        ">> DCP-L2550DW/DCP-7190DW\n>> DCP-7195DW/MFC-7895DW",
        selected_model="DCP-L2550DW",
    )


def test_model_applicability_keeps_shared_or_unrecognized_declarations() -> None:
    assert is_applicable_to_model(
        "设备内部卡纸时，请等待内部零件冷却。",
        selected_model="DCP-L2550DW",
    )
    assert is_applicable_to_model(
        "适用型号：本系列全部机型\n请遵循安全说明。",
        selected_model="DCP-L2550DW",
    )
    assert is_applicable_to_model(
        "相关型号: MFC-7895DW\n传真设置。",
        selected_model=None,
    )


def test_model_applicability_understands_model_only_heading_and_family_label() -> None:
    assert not is_applicable_to_model(
        "更改复印设置。",
        heading="DCP-7195DW/MFC-7895DW",
        selected_model="DCP-L2550DW",
    )
    assert not is_applicable_to_model(
        "功能表 (2.7寸触摸面板 MFC 型号)",
        selected_model="DCP-L2550DW",
    )
    assert is_applicable_to_model(
        "功能表包含 DCP 型号与 MFC 型号的公共说明。",
        selected_model="DCP-L2550DW",
    )


def test_unsupported_model_capability_blocks_dcp_or_hl_fax_only() -> None:
    assert has_unsupported_model_capability(
        "DCP-L2550DW如何发送传真？",
        selected_model="DCP-L2550DW",
    )
    assert has_unsupported_model_capability(
        "How do I send a PC-FAX?",
        selected_model="HL-L2350DW",
    )
    assert not has_unsupported_model_capability(
        "MFC-7895DW如何发送传真？",
        selected_model="MFC-7895DW",
    )
    assert not has_unsupported_model_capability(
        "DCP-L2550DW如何清除卡纸？",
        selected_model="DCP-L2550DW",
    )


def test_query_without_selected_scope_keeps_the_actual_operation_terms() -> None:
    assert query_without_selected_scope(
        "Brother MFC‑7895DW如何在Windows中使用PC-FAX发送传真？",
        selected_brand="Brother",
        selected_model="MFC-7895DW",
    ) == "如何在Windows中使用PC-FAX发送传真？"
    assert query_without_selected_scope(
        "DCP-L2550DW",
        selected_brand="Brother",
        selected_model="DCP-L2550DW",
    ) == "DCP-L2550DW"


def test_lexical_scoring_recognizes_english_technical_terms() -> None:
    scores = lexical_relevance_scores(
        "如何在Windows中使用PC-FAX发送传真？",
        [
            "使用 PC-FAX 发送传真 (Windows) 选择 Brother PC-FAX 作为打印机。",
            "使用 AirPrint 接收传真并进行无线网络配置。",
        ],
    )

    assert scores[0] > scores[1]
