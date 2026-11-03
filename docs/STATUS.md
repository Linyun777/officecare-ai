# 项目状态

## 现在

M1（阶段 03：可信 RAG 质量基线）已验收，并按用户要求推送到 GitHub `main`，实现提交为 [`60d931a`](https://github.com/Linyun777/officecare-ai/commit/60d931afa47b794d35930fbd2bc6a1768907f8c3)；验收依据、质量遗留与交付记录见[阶段 03 结果](stages/03-rag-quality-upgrade.md#2026-11-03-m1-提交与推送)。独立工作区保留在 `codex/m1-rag-quality-baseline`，主目录既有改动保留；未开始 M2，下一阶段启动待用户安排。

M0（阶段 02 收口）已验收并交付到 GitHub `main` 的 `52fa461`，三项 CI 实跑全部通过，范围与结果见[阶段 02](stages/02-ai-turn-reliability.md#2026-10-24-m0-收口自检)。总体路线见[AI 应用升级路线](stages/00-ai-application-roadmap.md)；主目录既有改动保留，P01-03 部署验收仍按用户决定跳过。

## 阶段

| 阶段 | 状态 | 设计文档 | 验收 |
|---|---|---|---|
| 01：工程基线审计与验收 | 开发中 | [01-engineering-baseline-audit](stages/01-engineering-baseline-audit.md) | 原结论暂不通过；修复后的复核待用户决定，P01-03 用户跳过 |
| 02：AI 单轮可靠性与跨存储一致性 | 已验收 | [02-ai-turn-reliability](stages/02-ai-turn-reliability.md) | 按 M0 明示范围验收通过；三项 GitHub CI 已通过 |
| 03：可信 RAG 质量基线（M1） | 已验收 | [03-rag-quality-upgrade](stages/03-rag-quality-upgrade.md) | 按 M1 范围验收通过，已推送 GitHub main；质量遗留保留 |
