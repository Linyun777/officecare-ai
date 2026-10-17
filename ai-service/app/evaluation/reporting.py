from __future__ import annotations

from datetime import timezone
import json
from pathlib import Path

from app.evaluation.models import EvaluationReport


def write_evaluation_report(
    report: EvaluationReport,
    *,
    output_dir: Path,
) -> tuple[Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = report.generated_at.astimezone(timezone.utc).strftime(
        "%Y%m%dT%H%M%SZ"
    )
    json_path = output_dir / f"rag-evaluation-{timestamp}.json"
    markdown_path = output_dir / f"rag-evaluation-{timestamp}.md"
    json_path.write_text(
        json.dumps(
            report.model_dump(mode="json", by_alias=True),
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    markdown_path.write_text(_markdown(report), encoding="utf-8")
    return json_path, markdown_path


def _markdown(report: EvaluationReport) -> str:
    lines = [
        "# OfficeCare RAG 评测报告",
        "",
        f"- 生成时间：{report.generated_at.isoformat()}",
        f"- 数据集：`{report.dataset_path}`",
        f"- 知识库 ID：`{report.scope.knowledge_base_id}`",
        f"- 品牌：`{report.scope.brand_name}` (`{report.scope.brand_id}`)",
        f"- 型号：`{report.scope.model_name}` (`{report.scope.model_id}`)",
        f"- 文档版本：`{', '.join(map(str, report.scope.document_version_ids))}`",
        f"- TopK / 阈值：`{report.scope.top_k}` / `{report.scope.score_threshold}`",
        "",
        "## 汇总",
        "",
        "| 模式 | 用例 | 通过率 | Recall@K | MRR | 噪声率 | 回答准确率 | 拒答准确率 | 引用命中率 | P95耗时(ms) |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for summary in report.summaries:
        lines.append(
            "| "
            + " | ".join(
                [
                    summary.mode.value,
                    str(summary.cases),
                    _percent(summary.pass_rate),
                    _percent(summary.recall_at_k),
                    _decimal(summary.mean_reciprocal_rank),
                    _percent(summary.noise_rate),
                    _percent(summary.answer_accuracy),
                    _percent(summary.refusal_accuracy),
                    _percent(summary.citation_hit_rate),
                    f"{summary.p95_latency_ms:.3f}",
                ]
            )
            + " |"
        )

    lines.extend(
        [
            "",
            "## 用例结果",
            "",
            "| 用例 | 模式 | 结果 | 耗时(ms) | 召回数 | 是否回答 | 首个相关排名 | 错误码 |",
            "|---|---|---|---:|---:|---|---:|---|",
        ]
    )
    for item in report.observations:
        lines.append(
            "| "
            + " | ".join(
                [
                    item.case_id,
                    item.mode.value,
                    "通过" if item.passed else "失败",
                    f"{item.latency_ms:.3f}",
                    str(item.retrieved_count),
                    "-" if item.answered is None else ("是" if item.answered else "否"),
                    str(item.first_relevant_rank or "-"),
                    item.error_code or "-",
                ]
            )
            + " |"
        )

    failures = [item for item in report.observations if not item.passed]
    if failures:
        lines.extend(["", "## 失败详情", ""])
        for item in failures:
            failed_checks = [name for name, passed in item.checks.items() if not passed]
            lines.append(f"### {item.case_id} / {item.mode.value}")
            lines.append("")
            lines.append(f"- 失败检查：`{', '.join(failed_checks) or '-'}`")
            lines.append(f"- 错误码：`{item.error_code or '-'}`")
            if item.answer:
                compact_answer = " ".join(item.answer.split())[:800]
                lines.append(f"- 回答：{compact_answer}")
            if item.sources:
                sources = ", ".join(
                    f"{source.document_title}/p{source.page_number or '-'}"
                    f"/#{source.chunk_index}{'*' if source.relevant else ''}"
                    for source in item.sources
                )
                lines.append(f"- 来源：{sources}")
            lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def _percent(value: float | None) -> str:
    return "-" if value is None else f"{value * 100:.1f}%"


def _decimal(value: float | None) -> str:
    return "-" if value is None else f"{value:.3f}"
