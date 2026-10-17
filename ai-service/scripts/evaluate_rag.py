from __future__ import annotations

import argparse
from pathlib import Path
import sys


PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.core.config import get_settings
from app.evaluation.models import EvaluationMode, EvaluationScope
from app.evaluation.reporting import write_evaluation_report
from app.evaluation.runner import (
    EvaluationRunner,
    HttpRagEvaluationClient,
    load_evaluation_cases,
)


DEFAULT_DATASET = PROJECT_ROOT / "evaluation" / "datasets" / "officecare_printer_v1.jsonl"
DEFAULT_OUTPUT_DIR = PROJECT_ROOT / "evaluation-results"


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    internal_token = (get_settings().internal_token() or "").strip()
    if not internal_token:
        parser.error(
            "请先在本机环境变量中设置 OFFICECARE_AI_SERVICE_TOKEN；"
            "不要通过命令行参数传入真实 Token。"
        )

    try:
        version_ids = _positive_int_list(args.document_version_ids)
        cases = load_evaluation_cases(args.dataset.resolve())
        cases = _filter_cases(cases, tags=args.tag, limit=args.limit)
        if not cases:
            parser.error("筛选后没有可执行的评测用例。")
        scope = EvaluationScope(
            knowledge_base_id=args.knowledge_base_id,
            brand_id=args.brand_id,
            model_id=args.model_id,
            brand_name=args.brand_name,
            model_name=args.model_name,
            document_version_ids=version_ids,
            top_k=args.top_k,
            score_threshold=args.score_threshold,
        )
    except ValueError as exc:
        parser.error(str(exc))

    modes = _modes(args.mode)
    client = HttpRagEvaluationClient(
        base_url=args.base_url,
        internal_token=internal_token,
        timeout_seconds=args.timeout_seconds,
    )
    try:
        report = EvaluationRunner(client=client, scope=scope).run(
            cases,
            modes=modes,
            dataset_path=str(args.dataset.resolve()),
        )
    finally:
        client.close()

    json_path, markdown_path = write_evaluation_report(
        report,
        output_dir=args.output_dir.resolve(),
    )
    print(f"评测用例：{len(cases)}")
    for summary in report.summaries:
        print(
            f"{summary.mode.value}: {summary.passed}/{summary.cases} "
            f"通过，passRate={summary.pass_rate:.1%}，"
            f"P95={summary.p95_latency_ms:.1f}ms"
        )
    print(f"JSON 报告：{json_path}")
    print(f"Markdown 报告：{markdown_path}")

    if args.fail_under is not None and any(
        summary.pass_rate < args.fail_under for summary in report.summaries
    ):
        print(f"评测未达到通过率门槛：{args.fail_under:.1%}", file=sys.stderr)
        return 1
    return 0


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="运行 OfficeCare RAG 固定数据集评测并生成 JSON/Markdown 报告。"
    )
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument(
        "--mode",
        choices=("retrieval", "answer", "both"),
        default="both",
    )
    parser.add_argument("--knowledge-base-id", type=_positive_int, required=True)
    parser.add_argument("--brand-id", type=_positive_int, required=True)
    parser.add_argument("--model-id", type=_positive_int, required=True)
    parser.add_argument("--brand-name", required=True)
    parser.add_argument("--model-name", required=True)
    parser.add_argument(
        "--document-version-ids",
        required=True,
        help="Java 当前有效文档版本 ID，使用英文逗号分隔，例如 11,12,13。",
    )
    parser.add_argument("--top-k", type=int, default=5, choices=range(1, 11))
    parser.add_argument("--score-threshold", type=float, default=0.45)
    parser.add_argument("--base-url", default="http://localhost:8000")
    parser.add_argument("--timeout-seconds", type=float, default=180.0)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument(
        "--tag",
        action="append",
        default=[],
        help="只运行包含指定标签的用例；可重复使用，多个标签按同时包含处理。",
    )
    parser.add_argument("--limit", type=_positive_int)
    parser.add_argument(
        "--fail-under",
        type=_ratio,
        help="任一模式通过率低于该值时返回退出码 1，例如 0.8。",
    )
    return parser


def _positive_int(raw: str) -> int:
    try:
        value = int(raw)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("必须是正整数。") from exc
    if value <= 0:
        raise argparse.ArgumentTypeError("必须是正整数。")
    return value


def _ratio(raw: str) -> float:
    try:
        value = float(raw)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("必须是 0 到 1 之间的小数。") from exc
    if value < 0 or value > 1:
        raise argparse.ArgumentTypeError("必须是 0 到 1 之间的小数。")
    return value


def _positive_int_list(raw: str) -> list[int]:
    values = [item.strip() for item in raw.split(",") if item.strip()]
    if not values:
        raise ValueError("--document-version-ids 不能为空。")
    try:
        parsed = [int(item) for item in values]
    except ValueError as exc:
        raise ValueError("--document-version-ids 只能包含正整数和英文逗号。") from exc
    if any(value <= 0 for value in parsed):
        raise ValueError("--document-version-ids 只能包含正整数。")
    if len(set(parsed)) != len(parsed):
        raise ValueError("--document-version-ids 不能包含重复值。")
    return parsed


def _filter_cases(cases, *, tags: list[str], limit: int | None):
    normalized_tags = {tag.strip() for tag in tags if tag.strip()}
    selected = [
        case
        for case in cases
        if not normalized_tags or normalized_tags.issubset(set(case.tags))
    ]
    return selected[:limit] if limit is not None else selected


def _modes(raw: str) -> list[EvaluationMode]:
    if raw == "both":
        return [EvaluationMode.RETRIEVAL, EvaluationMode.ANSWER]
    return [EvaluationMode(raw)]


if __name__ == "__main__":
    raise SystemExit(main())
