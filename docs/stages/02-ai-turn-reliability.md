# 阶段 02：AI 单轮可靠性与跨存储一致性

## 目标与用户看到的效果

- 同一会话重复提交同一个 `turnId` 时，Agent 单轮只执行一次，重复请求等待并复用同一结果；同一个 `turnId` 用在其他问题上直接返回冲突。单轮内部的模型调用次数按实际流程统计。
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
- 实现提交：`0e675ff`，原实现基线 `5330b9e`。2026-10-24 已在线核实该提交位于 GitHub `main`，原实现工作区干净；用户验收仍未完成。当前基线和工作区入口见本文末尾“2026-10-24 基线复核”。

### 2026-10-17 实际运行的测试与结果

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
- 实现的提交状态见本节“交付内容”；代码已进入 GitHub `main` 不等同于用户已验收。本轮基线文档的交付授权与范围见末尾“文档提交与推送”。

### 源码入口

- `java-server/ruoyi-officecare/src/main/java/com/ruoyi/officecare/service/impl/OcAiAssistantServiceImpl.java`
- `java-server/ruoyi-officecare/src/main/java/com/ruoyi/officecare/service/impl/OcAiTurnExecutionService.java`
- `java-server/ruoyi-officecare/src/main/java/com/ruoyi/officecare/service/impl/OcAiTurnPersistenceService.java`
- `java-server/ruoyi-officecare/src/main/resources/mapper/officecare/OcAiTurnExecutionMapper.xml`
- `java-server/sql/officecare/016_officecare_ai_turn_execution.sql`
- `ai-service/app/services/assistant_agent.py`

### 2026-10-24 基线复核

本轮只整理工程基线与验收事实，并对已提交的阶段 02 实现做针对性自检；未新增产品功能，未改生产代码、依赖、锁文件、数据库迁移、Milvus Schema 或全局配置。

#### 远端与工作区

- 实际执行 `git ls-remote github refs/heads/main`，确认 GitHub `main` 为 `0e675ff40527e733cbf18c20a7bc111ab0f2612e`；这与本地 `github/main` 一致。
- 主目录的 `master` 与 GitHub `main` 无共同祖先（`git merge-base master github/main` 退出码 1）。保留原分支及未提交改动，不用旧主目录覆盖 GitHub 版本。
- 后续协作仅使用 `github` 远端；基线核对时未改远端配置、未提交、未推送、未合并，后续文档交付见本节末尾。原 `origin` 仍指向 Gitee，不作为后续推送目标。

| 工作区 | HEAD / 分支 | 本轮开始时状态及用途 |
|---|---|---|
| `D:/Code/shizhan/officecare-ai` | `1a8affb` / `master` | 旧主目录；保留 `.gitignore`、未跟踪文档和三份已暂存的文档删除 |
| `D:/Code/shizhan/officecare-ai-accept-01` | `0e675ff` / `feat/02-ai-turn-reliability` | 干净；阶段 02 原实现工作区 |
| `D:/Code/shizhan/officecare-ai-rag-03` | `0e675ff` / `feat/03-rag-quality` | RAG 源码、测试和状态文档有未提交修改；设计在该工作区的 `docs/stages/03-rag-quality-upgrade.md`，真实评测未完成 |
| `D:/Code/shizhan/officecare-ai-audit-01` | `fb73f81` / detached HEAD | 干净；保留历史审计快照 |
| `C:/Users/Administrator/.codex/worktrees/212b/officecare-ai` | `e4b525e` / detached HEAD | 有旧实现的未提交改动，本轮保留 |
| `C:/Users/Administrator/.codex/worktrees/5ef6/officecare-ai` | `beb5b89` / detached HEAD | 有旧文档的未提交改动，本轮保留 |
| `C:/Users/Administrator/.codex/worktrees/baseline-alignment/officecare-ai` | `0e675ff` / `codex/baseline-alignment` | 本轮独立工作区；只修改本阶段文档、`docs/STATUS.md` 和 `docs/DECISIONS.md` |

#### 本轮实际运行的自检

| 项目 | 实际命令或设置 | 结果 |
|---|---|---|
| GitHub 基线 | `git ls-remote github refs/heads/main` | PASS，远端提交与上表基线一致 |
| Java 对话与执行状态 | `mvn -o -pl ruoyi-officecare -am "-Dtest=OcAiAssistantServiceImplTest,OcAiTurnExecutionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 10 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS |
| Python 助手 | 使用主目录现有 `.venv/Scripts/python.exe` 执行 `tests/test_assistant_agent.py`，参数 `-vv -s --assert=plain -p pytest_asyncio.plugin -p no:cacheprovider` | 11 passed，0.39s |

Python 自检没有安装或变更依赖：仅在测试进程设置 `LANGSMITH_TRACING=false`、`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1`、`PYTHONDONTWRITEBYTECODE=1`，显式加载现有 asyncio 插件，并把 `tempfile.tempdir` 指向本工作区已忽略的 `notes/baseline-temp`。Java 使用离线模式；两组测试通过 mock、fake 与内存检查点隔离外部业务依赖。

首次执行未进入有效测试：沙箱内 Python 报 `ImportError: DLL load failed while importing _ssl`，Java 编译产物报 `could not create parent directories`；切换运行权限后 Python 默认临时目录报 `No usable temporary directory found`。指定临时目录的一次尝试长期无输出后已停止，仅终止了本次测试进程；上表是隔离进程设置后的最终实际结果，不把环境失败记为通过。

#### 验收边界与后续顺序

- 本轮通过的是 GitHub 基线核对和 21 个相关单元测试，不是三端全量回归、真实并发或端到端验收；2026-10-17 的 98 / 159 测试与故障注入记录保留其原日期。
- 未重跑多 JVM 并发、同会话不同 `turnId` 并发、真实超时补偿、浏览器失败恢复或真实中断；未验证基础设施当前是否可达。历史集成结果不等同于本轮重验。
- 当前先收口阶段 02 的验收，用户确认后再处理阶段 03 的真实评测；前端原请求重试、草稿基础版本保护属于后续设计范围，本轮未开发。
- P01-03 部署验收仍按用户决定跳过；本轮未启动服务、未连接业务数据库、未修改既有 RAG 工作区。
- 基线中存在两个以 `016_` 开头的 SQL 脚本。本轮只记录，不重命名已发布迁移，也不执行数据库变更；后续迁移管理在对应阶段设计中处理。

#### 文档提交与推送

- 2026-10-24：用户明确要求继续工作，并将本轮已完成的基线文档提交、推送到 GitHub，以核对新提交邮箱的贡献归属。
- 提交范围仅为 `docs/STATUS.md`、`docs/DECISIONS.md` 和本文；提交标题为 `docs: align GitHub baseline and record reliability checks`，具体 SHA 与远端落点以 Git 日志和推送结果为准。
- 新提交使用用户指定的仓库级邮箱；全局 Git 配置和已有提交历史保持原样。
- 推送目标为 `github` 的 `main`，按普通快进更新执行；原主目录及既有 RAG 工作区保持各自的未提交改动。
- 文档差异、相对链接和提交范围已检查；本次交付未重复运行 Java/Python 测试，实际自检范围仍以上表为准。阶段验收状态以真实验收结果记录。
