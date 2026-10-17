# 阶段 02：AI 单轮可靠性与跨存储一致性

## 目标与用户看到的效果

- 同一会话重复提交同一个 `turnId` 时，模型只推理一次，重复请求等待并复用同一结果；同一个 `turnId` 用在其他问题上直接返回冲突。
- 用户提问期间 MySQL 不再长时间持有事务；Agent 推理成功但消息落库失败时，用同一个 `turnId` 重试只补落库，不重复调用模型。
- Java 调用 FastAPI 超时、但 PostgreSQL 已保存 checkpoint 时，MySQL 保留可恢复的执行状态，重试后消息可补齐。

## 范围与不做

- 只改 AI 助手单轮调用链：`OcAiAssistantServiceImpl`、执行状态表、落库事务、FastAPI 单轮入口。
- 不改会话、消息、引用表结构；不改 Milvus Schema、不重建向量；不改 RAG 检索与回答策略。
- 不引入 Redis 分布式锁、消息队列补偿或新的外部依赖。
- 不改工单、文档任务和前端交互。

## 设计

### 服务端执行状态

新增 `oc_ai_turn_execution`，唯一键 `(conversation_id, turn_id)`，迁移脚本 `java-server/sql/officecare/016_officecare_ai_turn_execution.sql`。

状态机：

```text
PROCESSING -> READY -> PERSISTING -> SUCCEEDED
                    \-> FAILED
```

- `PROCESSING`：当前执行者已取得推理权。
- `READY`：Agent 结果已写入 `result_payload`，等待落库。
- `PERSISTING`：当前执行者已取得落库权。
- `SUCCEEDED`：消息、引用、会话状态与执行状态已在同一短事务内提交。
- `FAILED`：推理失败，允许同一 `turnId` 接管重试。

并发控制由唯一索引和条件 `UPDATE` 完成：新请求先尝试插入；唯一键冲突后读取现有行，按状态返回 `REPLAY`（已完成）、`FINISH`（结果已存在，只需落库）、`BUSY`（其他执行者持有租约）或 `CONFLICT`（`question_hash` 不一致）。`owner_token` 区分执行者，`lease_until` 限制接管时机；落库权限用 `READY -> PERSISTING` 的条件更新抢占。

### 事务边界

- 移除 `answerInConversation()` 上的 `@Transactional`，FastAPI 调用不再位于 MySQL 事务内。
- 新增 `OcAiTurnPersistenceService.persistTurn()`，用独立短事务完成：用户消息、助手消息、引用、会话 `touch`、执行状态 `markSucceeded`。
- Agent 返回后先 `markReady` 保存结构化结果；落库失败时事务回滚并把执行状态重置为 `READY`，错误码和错误信息保留在执行行。

### 超时与故障恢复

- Java 超时或 FastAPI 失败：`markFailed` 置为 `FAILED` 并释放租约；同一 `turnId` 重试通过条件更新接管。
- 进程中断：`PROCESSING` 租约到期后可被接管；`PERSISTING` 租约到期后只接管落库，不重新推理。
- 落库失败：`READY` 下重试只调用 `persistTurn`，结果快照来自 `result_payload`，不再访问 FastAPI。
- Python `LangChainAssistantAgent` 增加进程内 `asyncio.Lock`，键为 `thread_id + turnId`，避免同一进程内并发穿透；跨实例幂等由 Java 执行状态表负责。

### 配置

`officecare.ai` 新增：

| 配置 | 默认 | 说明 |
|---|---|---|
| `turn-wait-timeout` | `60s` | 等待其他执行者完成的最长时间 |
| `turn-lease` | `360s` | 单轮执行租约，必须大于 `agent-read-timeout`（240s） |
| `turn-wait-poll-interval` | `200ms` | 等待轮询间隔 |

同步写入 `application-dev.example.yml` 和 `deploy/production/docker-compose.yml`。

## 内部步骤

1. 新增 016 迁移和 `OcAiTurnExecution` 领域对象、Mapper、Mapper XML。
2. 新增 `OcAiTurnExecutionService`，实现占用、接管、结果保存、落库权迁移和失败状态。
3. 新增 `OcAiTurnPersistenceService`，收敛单轮落库短事务。
4. 改造 `OcAiAssistantServiceImpl`：调用前占用、等待/重放/冲突分支、失败补偿。
5. 在 `assistant_agent.py` 增加进程内单轮锁，并补并发回归测试。
6. 补充 Java 状态机测试、服务测试和配置示例。

## 验证方式

- Java：`mvn -pl ruoyi-officecare -am test`。
- Python：`ai-service/.venv/Scripts/python.exe -m pytest -q`。
- 集成环境：MySQL `officecare_ai_test`、PostgreSQL `officecare_agent_test`、Milvus `officecare_document_chunks_test_v1`、RabbitMQ `/officecare_test`、Redis database 15；Java 8081（正常）与 8082（`agent-read-timeout: 1s`），FastAPI 8010，模型计数代理 8012。
- 集成脚本：`notes/f02_f01_verify.py`、`notes/f01_timeout_verify.py`，结果写入 `notes/f02-f01-verify.json`、`notes/f01-timeout-verify.json`。

## 结果

### 交付内容

- 新增：`016_officecare_ai_turn_execution.sql`、`OcAiTurnExecution.java`、`OcAiTurnExecutionMapper.java`、`OcAiTurnExecutionMapper.xml`、`OcAiTurnExecutionService.java`、`OcAiTurnPersistenceService.java`、`OcAiTurnExecutionServiceTest.java`。
- 修改：`OcAiAssistantServiceImpl.java`、`OfficeCareAiProperties.java`、`OcAiAssistantServiceImplTest.java`、`assistant_agent.py`、`test_assistant_agent.py`、`application-dev.example.yml`、`deploy/production/docker-compose.yml`。
- 状态提交：未提交、未推送、未合并；改动位于 worktree `D:/Code/shizhan/officecare-ai-accept-01` 的 `feat/02-ai-turn-reliability` 分支，基线 `5330b9e`。

### 实际运行的测试与结果

| 项目 | 命令 | 结果 |
|---|---|---|
| Java 单元/模块测试 | `mvn -pl ruoyi-officecare -am test` | 98 tests，0 failures，0 errors，BUILD SUCCESS |
| Python 全量测试 | `python -m pytest -q` | 159 passed |
| F02 单轮基线 | `notes/f02_f01_verify.py` | 1 次 FastAPI 调用、2 次模型调用、HTTP 200 |
| F02 并发 2 路 | 同上 | 2/2 成功，FastAPI 增量 1，模型调用 2，消息 2 行，执行 1 次，同一 `assistantMessageId` |
| F02 并发 5 路 | 同上 | 5/5 成功，FastAPI 增量 1，模型调用 2，消息 2 行，执行 1 次 |
| F02 并发 10 路 | 同上 | 10/10 成功，FastAPI 增量 1，模型调用 2，消息 2 行，执行 1 次 |
| F02 顺序重试 | 同上 | 第二次返回同一 `assistantMessageId`，FastAPI 与模型增量均为 0 |
| F02 问题冲突 | 同上 | 同一 `turnId` 换问题返回业务码 500，未新增 FastAPI 调用 |
| F01 租约接管 | 注入过期 `PROCESSING` 行 | 接管成功，`attempt_count = 2`，最终 `SUCCEEDED`，消息 2 行 |
| F01 长事务 | 推理期间每 200 ms 采样 `innodb_trx` | 未观察到新事务；15 次采样为空，最长事务年龄 0 s |
| F01 Agent 成功 + MySQL 失败 | 注入 `oc_ai_message` 插入触发器 | 首次业务码 500，消息 0 行，checkpoint 4 个 blob，执行状态 `READY` 且保留结果；移除故障后重试成功，FastAPI 与模型增量 0，消息 2 行，`SUCCEEDED` |
| F01 Java 超时 | 8082 实例 `agent-read-timeout: 1s` | 1.05 s 返回业务码 500；checkpoint 4 个 blob；消息 0 行；执行状态 `FAILED`；同 `turnId` 经 8081 重试 0.04 s 成功，模型增量 0，消息 2 行，`SUCCEEDED`、`attempt_count = 2` |

F02 对照：修复前 2/5/10 路并发分别产生 4/10/20 次模型调用且只有 1 个请求成功；修复后模型调用等于单轮基线 2 次，全部请求成功并返回同一结果。

### 未测项与遗留

- 未做多 JVM 同时压测；跨实例正确性依赖唯一索引和条件 `UPDATE`，当前由状态机单元测试和单实例并发覆盖。
- 未做真实 `kill -9` 中断；用注入过期租约模拟进程中断后的接管。
- `awaitTurn` 在持有者租约未过期时最多等待 `turn-wait-timeout`；租约过期后的接管由重试触发，未在等待循环内主动抢占。
- P01-03 部署验收仍按用户决定跳过，不计入本阶段。
- 前端重复点击的真实浏览器行为未验证；服务端幂等不依赖前端。
- 本阶段未提交、未推送、未合并，等待用户验收后再决定提交范围。

### 源码入口

- `java-server/ruoyi-officecare/src/main/java/com/ruoyi/officecare/service/impl/OcAiAssistantServiceImpl.java`
- `java-server/ruoyi-officecare/src/main/java/com/ruoyi/officecare/service/impl/OcAiTurnExecutionService.java`
- `java-server/ruoyi-officecare/src/main/java/com/ruoyi/officecare/service/impl/OcAiTurnPersistenceService.java`
- `java-server/ruoyi-officecare/src/main/resources/mapper/officecare/OcAiTurnExecutionMapper.xml`
- `java-server/sql/officecare/016_officecare_ai_turn_execution.sql`
- `ai-service/app/services/assistant_agent.py`