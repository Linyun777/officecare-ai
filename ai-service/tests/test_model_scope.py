from langchain_core.documents import Document

from app.services.model_scope import resolve_model_scopes


SELECTED = {"dcpl2550dw"}
OTHER = {"mfc7895dw"}


def doc(index, text, heading="错误和维护信息", version=18):
    return Document(
        page_content=text,
        metadata={
            "document_version_id": version,
            "chunk_index": index,
            "heading": heading,
        },
    )


def test_scope_covers_arbitrarily_many_contiguous_table_continuations():
    records = [
        doc(0, "手册首页"),
        doc(1, "DCP-7195DW/MFC-7895DW\n错误 原因 措施"),
        *[doc(index, "续表：故障原因与措施") for index in range(2, 8)],
    ]
    scopes = resolve_model_scopes(reversed(records))
    assert all(not scopes[(18, index)].allows(SELECTED) for index in range(1, 8))
    assert all(scopes[(18, index)].allows(OTHER) for index in range(1, 8))


def test_new_explicit_group_overrides_previous_scope():
    scopes = resolve_model_scopes([
        doc(0, "MFC-7895DW\n传真说明"),
        doc(1, "DCP-L2550DW\n打印说明", heading="新的适用范围"),
        doc(2, "继续打印说明", heading="新的适用范围"),
    ])
    assert scopes[(18, 1)].allows(SELECTED)
    assert scopes[(18, 2)].allows(SELECTED)
    assert not scopes[(18, 1)].allows(OTHER)


def test_concrete_group_precedes_navigation_model_lists():
    scopes = resolve_model_scopes([
        doc(0, ">> DCP-L2550DW\n>> MFC-7895DW\nMFC-7895DW\n错误 原因 措施"),
        doc(1, "续表内容"),
    ])
    assert not scopes[(18, 0)].allows(SELECTED)
    assert not scopes[(18, 1)].allows(SELECTED)


def test_shared_multi_model_list_remains_eligible():
    scopes = resolve_model_scopes([
        doc(0, ">> DCP-L2550DW/MFC-7895DW\n共享使用步骤"),
        doc(1, "继续共享使用步骤"),
    ])
    assert scopes[(18, 1)].allows(SELECTED)
    assert scopes[(18, 1)].allows(OTHER)


def test_mixed_model_groups_are_unresolved_instead_of_unioning_rows():
    scopes = resolve_model_scopes([
        doc(0, "DCP-L2550DW\n仅打印操作\nMFC-7895DW\n仅传真操作"),
        doc(1, "续表"),
    ])
    assert scopes[(18, 0)].origin == "mixed_model_groups"
    assert not scopes[(18, 0)].allows(SELECTED)
    assert not scopes[(18, 1)].allows(OTHER)


def test_explicit_shared_section_ends_narrow_scope():
    scopes = resolve_model_scopes([
        doc(0, "MFC-7895DW\n传真说明"),
        doc(1, "适用于所有型号\n接触内部零件前先关闭电源", heading="安全须知"),
    ])
    assert scopes[(18, 1)].allows(SELECTED)


def test_negated_shared_mention_is_not_a_shared_declaration():
    scopes = resolve_model_scopes([
        doc(0, "MFC-7895DW\n传真说明"),
        doc(1, "此功能不适用于所有型号"),
    ])
    assert not scopes[(18, 1)].allows(SELECTED)


def test_independent_breadcrumb_section_ends_narrow_scope():
    scopes = resolve_model_scopes([
        doc(0, "MFC-7895DW\n主页 > 故障排除 > 传真错误"),
        doc(1, "安全步骤\n主页 > 故障排除 > 卡纸处理", heading="卡纸处理"),
    ])
    assert scopes[(18, 1)].allows(SELECTED)


def test_child_breadcrumb_keeps_parent_model_restriction():
    scopes = resolve_model_scopes([
        doc(0, "MFC-7895DW\n主页 > 故障排除 > 传真错误"),
        doc(1, "下一步\n主页 > 故障排除 > 传真错误 > 拒绝访问", heading="拒绝访问"),
    ])
    assert not scopes[(18, 1)].allows(SELECTED)


def test_heading_change_without_structural_context_is_unresolved():
    scopes = resolve_model_scopes([
        doc(0, "MFC-7895DW\n传真说明"),
        doc(1, "任意新标题不能证明适用所有型号", heading="另一个标题"),
    ])
    assert scopes[(18, 1)].models is None
    assert not scopes[(18, 1)].allows(SELECTED)


def test_missing_block_and_version_boundary_do_not_inherit_or_assume_shared():
    scopes = resolve_model_scopes([
        doc(0, "DCP-L2550DW\n打印说明"),
        doc(2, "缺失前一块的续表"),
        doc(3, "另一个版本中没有上下文", version=19),
        doc(0, "另一个完整文档的共享首页", version=20),
    ])
    assert scopes[(18, 2)].models is None
    assert scopes[(19, 3)].models is None
    assert scopes[(20, 0)].allows(SELECTED)


def test_supply_and_fault_identifiers_do_not_override_narrow_scope():
    scopes = resolve_model_scopes([
        doc(0, "MFC-7895DW\n传真说明"),
        doc(1, "TN-2425 / DR-2425\nOC-MQ-TEST-0904"),
    ])
    assert not scopes[(18, 1)].allows(SELECTED)


def test_toc_boundary_recovers_shared_topic_without_trusting_arbitrary_heading():
    scopes = resolve_model_scopes([
        doc(0, "卡纸处理....10\n清洁设备....20", heading="目录"),
        doc(1, "MFC-7895DW\n专用步骤", heading="卡纸处理"),
        doc(2, "共享清洁步骤", heading="清洁设备"),
        doc(3, "继续共享清洁步骤", heading="清洁设备"),
    ])
    assert not scopes[(18, 1)].allows(SELECTED)
    assert scopes[(18, 2)].allows(SELECTED)
    assert scopes[(18, 3)].allows(SELECTED)


def test_missing_prefix_prevents_toc_from_inventing_shared_scope():
    scopes = resolve_model_scopes([
        doc(5, "清洁设备....20", heading="目录"),
        doc(6, "是否存在未知父章节无法确认", heading="清洁设备"),
    ])
    assert scopes[(18, 6)].models is None


def test_toc_does_not_override_known_breadcrumb_descendant():
    scopes = resolve_model_scopes([
        doc(0, "传真错误....10\n拒绝访问....20", heading="目录"),
        doc(1, "MFC-7895DW\n主页 > 传真错误", heading="传真错误"),
        doc(2, "主页 > 传真错误 > 拒绝访问", heading="拒绝访问"),
    ])
    assert not scopes[(18, 2)].allows(SELECTED)


def test_repeated_breadcrumb_does_not_clear_model_scope():
    scopes = resolve_model_scopes([
        doc(0, "相关信息\n主页 > 故障排除 > 错误和维护信息", heading="相关信息"),
        doc(1, "MFC-7895DW\n错误信息 原因 措施"),
        doc(2, "续表\n主页 > 故障排除 > 错误和维护信息"),
    ])
    assert not scopes[(18, 2)].allows(SELECTED)


def test_structural_outline_recovers_shared_siblings_after_model_table():
    records = [
        doc(0, "MFC-7895DW\n仅此组的重置步骤"),
        doc(1, "日常维护\n• 更换耗材\n• 清洁设备", heading="日常维护"),
        doc(2, "共享耗材说明", heading="更换耗材"),
        doc(3, "共享清洁步骤", heading="清洁设备"),
    ]
    scopes = resolve_model_scopes(records)
    assert scopes[(18, 2)].allows(SELECTED)
    assert scopes[(18, 3)].allows(SELECTED)


def test_model_specific_outline_keeps_restriction_on_siblings():
    records = [
        doc(0, "传真设置\n相关型号: MFC-7895DW\n• 发送设置\n• 接收设置", heading="传真设置"),
        doc(1, "发送步骤", heading="发送设置"),
        doc(2, "接收步骤", heading="接收设置"),
    ]
    scopes = resolve_model_scopes(records)
    assert not scopes[(18, 1)].allows(SELECTED)
    assert not scopes[(18, 2)].allows(SELECTED)
    assert scopes[(18, 2)].allows(OTHER)


def test_bullet_procedure_does_not_become_an_outline_boundary():
    records = [
        doc(0, "MFC-7895DW\n传真说明"),
        doc(1, "检查步骤\n• 关闭电源\n• 取出纸张", heading="检查步骤"),
        doc(2, "后续内容", heading="后续内容"),
    ]
    scopes = resolve_model_scopes(records)
    assert scopes[(18, 1)].models is None
    assert not scopes[(18, 2)].allows(SELECTED)
