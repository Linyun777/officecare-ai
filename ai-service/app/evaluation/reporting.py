from __future__ import annotations

from datetime import timezone
import json
from pathlib import Path
from uuid import uuid4

from app.evaluation.models import EvaluationReport


def write_evaluation_report(
    report: EvaluationReport, *, output_dir: Path,
) -> tuple[Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = report.generated_at.astimezone(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    report_name = f"rag-evaluation-{timestamp}-{uuid4().hex}"
    json_path = output_dir / f"{report_name}.json"
    markdown_path = output_dir / f"{report_name}.md"
    with json_path.open("x", encoding="utf-8") as output:
        output.write(
            json.dumps(
                report.model_dump(mode="json", by_alias=True),
                ensure_ascii=False,
                indent=2,
            ) + "\n"
        )
    with markdown_path.open("x", encoding="utf-8") as output:
        output.write(_markdown(report))
    return json_path, markdown_path


def _markdown(report: EvaluationReport) -> str:
    lines = [
        "# OfficeCare RAG 评测报告",
        "",
        f"- 生成时间：{report.generated_at.isoformat()}",
        f"- 报告版本：{report.schema_version}",
        f"- 数据集：{report.dataset_path}",
        f"- 知识库：{report.scope.knowledge_base_id}",
        f"- 型号：{report.scope.model_name}",
        f"- Java 授权版本：{report.scope.document_version_ids}",
        f"- TopK / 阈值：{report.scope.top_k} / {report.scope.score_threshold}",
        "",
        "自动结果表示已配置规则的通过情况，不等同于人工语义正确率。",
        "未核实标注单列，不进入质量分母；执行错误进入已核实样本的失败分母。",
        "待复核为独立标记，可与规则未通过同时出现，不替代规则结果。",
    ]
    if report.provenance:
        lines.extend(["", "## 冻结清单", ""])
        lines.extend(
            f"- {key}: {value}"
            for key, value in report.provenance.model_dump(mode="json", by_alias=True).items()
        )
    lines.extend([
        "", "## 汇总", "",
        "| 模式 | 总题数 | 可答/拒答 | 未核实 | 待复核 | 标注覆盖 | 执行成功率 | 规则通过率 | Hit@K | MRR | 可答规则通过率 | 拒答规则通过率 | P95(ms) |",
        "|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ])
    for summary in report.summaries:
        values = [
            summary.mode.value, str(summary.cases),
            f"{summary.positive_cases}/{summary.negative_cases}",
            str(summary.unreviewed_cases), str(summary.pending_review_cases),
            _percent(summary.annotation_coverage),
            _percent(summary.execution_success_rate),
            _percent(summary.pass_rate), _percent(summary.hit_at_k),
            _decimal(summary.mean_reciprocal_rank),
            _percent(summary.positive_rule_pass_rate),
            _percent(summary.refusal_accuracy), f"{summary.p95_latency_ms:.3f}",
        ]
        lines.append("| " + " | ".join(values) + " |")
    lines.extend([
        "", "## 用例结果", "",
        "| 用例 | 分组 | 模式 | 规则结果 | 待复核 | 耗时(ms) | 召回数 | 首个相关排名 | 错误码 |",
        "|---|---|---|---|---|---:|---:|---:|---|",
    ])
    for item in report.observations:
        outcome = (
            "执行错误" if item.error_code else
            "未评分" if item.annotation_status == "needs_review" else
            "规则通过" if item.passed else "规则未通过"
        )
        lines.append("| " + " | ".join([
            item.case_id, item.split, item.mode.value, outcome,
            "是" if item.review_required else "否",
            f"{item.latency_ms:.3f}", str(item.retrieved_count),
            str(item.first_relevant_rank or "-"), item.error_code or "-",
        ]) + " |")
    failures = [item for item in report.observations if not item.passed]
    if failures:
        lines.extend(["", "## 失败与复核详情", ""])
    for item in failures:
        checks = [name for name, passed in item.checks.items() if not passed]
        lines.extend([
            f"### {item.case_id} / {item.mode.value}", "",
            f"- 未通过检查：{', '.join(checks) or '-'}",
            f"- 复核原因：{'; '.join(item.review_reasons) or '-'}",
            f"- 错误码：{item.error_code or '-'}",
        ])
        if item.answer:
            lines.append("- 回答：" + " ".join(item.answer.split())[:800])
        if item.sources:
            sources = ", ".join(
                f"v{source.document_version_id}/p{source.page_number or '-'}/"
                f"#{source.chunk_index}{'*' if source.relevant else ''}"
                for source in item.sources
            )
            lines.append(f"- 来源：{sources}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def _percent(value: float | None) -> str:
    return "-" if value is None else f"{value * 100:.1f}%"


def _decimal(value: float | None) -> str:
    return "-" if value is None else f"{value:.3f}"
