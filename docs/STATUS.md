# 项目状态

## 现在

M2-2（阶段 05：不可变索引代次）已验收，实现 `32f7815`；结果见[阶段 05](stages/05-index-generations.md#结果)。用户授权自行推进整个 M2，接下来完成发布 / 回退、正式检索选择与管理界面。M2-1 已推送 `495e4b3`，三项 CI 通过；主目录和旧工作区既有改动保留。

M1 已验收并推送 GitHub `main`，实现 `60d931a`、验收文档 `fe5c6f5`，三项 CI 已通过，依据及质量遗留见[阶段 03](stages/03-rag-quality-upgrade.md#2026-11-03-m1-提交与推送)。M0 见[阶段 02](stages/02-ai-turn-reliability.md#2026-10-24-m0-收口自检)；总体路线见[升级路线](stages/00-ai-application-roadmap.md)，P01-03 部署验收仍按用户决定跳过。

## 阶段

| 阶段 | 状态 | 设计文档 | 验收 |
|---|---|---|---|
| 01：工程基线审计与验收 | 开发中 | [01-engineering-baseline-audit](stages/01-engineering-baseline-audit.md) | 原结论暂不通过；修复后的复核待用户决定，P01-03 用户跳过 |
| 02：AI 单轮可靠性与跨存储一致性 | 已验收 | [02-ai-turn-reliability](stages/02-ai-turn-reliability.md) | 按 M0 明示范围验收通过；三项 GitHub CI 已通过 |
| 03：可信 RAG 质量基线（M1） | 已验收 | [03-rag-quality-upgrade](stages/03-rag-quality-upgrade.md) | 按 M1 范围验收通过，已推送 GitHub main；质量遗留保留 |
| 04：文档结构保留与分块预览（M2-1） | 已验收 | [04-document-structure-preview](stages/04-document-structure-preview.md) | 按本节范围验收通过，实现 06e8e37；真实解析质量未验证 |

| 05：不可变索引代次（M2-2） | 已验收 | [05-index-generations](stages/05-index-generations.md) | 构建与隔离存储验收通过，实现 32f7815；不切换正式检索 |
