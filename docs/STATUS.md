# 项目状态

## 现在

阶段 02：AI 单轮可靠性与跨存储一致性，状态：开发中，**待用户验收**。

- F02 已修复：同一 `turnId` 在调用 Agent 前通过 `oc_ai_turn_execution` 唯一键原子占用；并发 2/5/10 路全部成功，FastAPI 单轮调用 1 次，模型调用等于单轮基线 2 次，消息只落 1 对，重复请求返回同一 `assistantMessageId`。
- F01 已修复：`answerInConversation()` 不再包在 MySQL 事务内；Agent 结果先存 `result_payload`，消息、引用、会话状态和执行状态在同一短事务提交；推理期间采样未观察到长事务。
- 故障验证：MySQL 落库失败后重试只补落库（模型调用 0 次）；Java 超时但 PG checkpoint 已保存时，执行状态为 `FAILED`，同 `turnId` 重试 0.04 s 命中缓存并补齐消息；过期租约可被接管。
- 回归：Java 98 个测试、Python 159 个测试全部通过。验证环境和脚本见阶段文档。
- 改动未提交、未推送、未合并，位于 worktree `feat/02-ai-turn-reliability`（基线 `5330b9e`）。
- 阶段 01 记录保持原样：P01-05 当时为 FAIL，P01-03 用户决定跳过；阶段 01 是否重新验收由用户决定。

## 阶段

| 阶段 | 状态 | 设计文档 | 验收 |
|---|---|---|---|
| 01：工程基线审计与验收 | 开发中 | [01-engineering-baseline-audit](stages/01-engineering-baseline-audit.md) | 暂不通过（P01-05 已由阶段 02 修复，待复核；P01-03 用户跳过） |
| 02：AI 单轮可靠性与跨存储一致性 | 开发中 | [02-ai-turn-reliability](stages/02-ai-turn-reliability.md) | 待用户验收（F01/F02 自检通过） |