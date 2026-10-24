# 项目状态

## 现在

总体路线 M0（阶段 02 收口）状态：已验收。按用户“验收后提交推送”指令复核通过，验收范围、实际测试与未测项统一见[阶段 02 M0 结果](stages/02-ai-turn-reliability.md#2026-10-24-m0-收口自检)。改动位于独立分支 `codex/m0-engineering-baseline`，本次交付到 GitHub `main`；CI 远端结果在推送后核对。阶段 03 及主目录既有改动保留，M1 未开始；P01-03 部署验收仍按用户原决定跳过。

总体路线见[AI 应用升级路线](stages/00-ai-application-roadmap.md)；M0 已获启动授权，其余里程碑待逐项确认。

## 阶段

| 阶段 | 状态 | 设计文档 | 验收 |
|---|---|---|---|
| 01：工程基线审计与验收 | 开发中 | [01-engineering-baseline-audit](stages/01-engineering-baseline-audit.md) | 原结论暂不通过；修复后的复核待用户决定，P01-03 用户跳过 |
| 02：AI 单轮可靠性与跨存储一致性 | 已验收 | [02-ai-turn-reliability](stages/02-ai-turn-reliability.md) | 按 M0 明示范围验收通过；本轮推送后核对远端 CI |
| 03：RAG 检索质量升级（已有改动复核） | 开发中 | [03-rag-quality-upgrade](stages/03-rag-quality-upgrade.md) | 单测通过；真实质量未通过，完整稳定复核未完成，未验收 |
