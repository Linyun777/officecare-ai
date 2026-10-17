# 阶段 01：工程基线审计与验收

状态：开发中（验收暂不通过：P01-05 FAIL；P01-03 用户决定跳过）

## 目标与用户看到的效果

- 审查现有代码与环境，验证三条核心业务链路，不增加功能、不大规模重构。
- 给出真实可追溯的三端构建/测试结论，明确 PASS、FAIL、BLOCKED、SKIPPED 和复现方式。
- 形成 RAG 真实基线报告，以及阶段 02 的最小改造清单。
- 未实际运行的检查不得写成通过；证据等级按 S 静态源码、T 本地自动化测试、I 基础设施集成、E 端到端业务操作区分。

## 范围与不做

本次做：

- 冻结版本与环境清单，核查配置与敏感信息。
- 运行 Python、Java、Vue 三端测试和构建。
- 审核部署配置与健康检查。
- 验证文档→RAG、AI 多轮会话、AI 报修→工单三条链路。
- 运行现有 RAG 评测集，形成基线报告。
- 登记 P0/P1/P2 缺陷，给出阶段 02 最小改造列表。

本次不做：

- 不增加产品功能。
- 不做大规模重构。
- 不在没有复现和验证的情况下修复缺陷。
- 不修改 Milvus Collection Schema、不重建向量空间、不混用旧索引。

## 设计

### 审计基准

- 仓库：`Linyun777/officecare-ai`，`main`。
- 最新核实提交：`cd53c7da9340412b967a10d36815996db22abc59`（2026-10-17）。
- 验收 worktree：`D:\Code\shizhan\officecare-ai-accept-01`，detached HEAD `cd53c7d`。
- 本地主工作目录 `master` 的 `1a8affb` 与 GitHub `main` 非祖先关系，禁止用本地 `master` 覆盖 `main`；主目录仅保留未提交文档变更。
- 本阶段没有修改生产代码；测试脚本、测试配置和结果都在被忽略的 `notes/` 下，未提交、未推送、未合并。

### 测试环境

- MySQL：`officecare_ai_test`（`192.168.100.128:3308`）。
- Agent PostgreSQL：`officecare_agent_test`（`192.168.100.128:5433`）。
- RabbitMQ：vhost `/officecare_test`（`192.168.100.128:5673`）。
- Milvus：集合 `officecare_document_chunks_test_v1`（`192.168.100.128:19531`）。
- Redis：database `15`。
- 文档存储：`D:/officecare-data/p01-test/documents`。
- Java：`8081` 正常测试实例，`8082` Agent 超时实例，`8083` 删除会话故障实例，`8084` 瞬时重试代理实例。
- FastAPI：`8010` 正常测试实例，`8014` 故障代理，`8015` Milvus 不可用实例，`8016` Embedding 不可用实例。
- 测试账号：`octest_a`（100）、`octest_b`（101）为 `oc_service`；`octest_c`（102）为普通角色。文档写接口使用测试库 `admin`。
- 所有写链路只使用上述 `*_test` 资源，未对开发库和开发 Milvus 集合执行破坏性操作。

### 已完成的静态盘点（S）

- 仓库约 857 个文件，包含 Java 业务、Vue 管理端、FastAPI AI 服务、Docker Compose 配置。
- Python 测试文件 18 个；Java 测试文件 22 个；未发现前端专门测试文件。
- 未发现 `.github/workflows`，GitHub Actions 历史执行记录为 0，当前提交的 combined status 没有状态检查。
- 数据库迁移/初始化：`java-server/sql/officecare/001...020` 共 20 份编号 SQL。
- FastAPI 提供文档处理、RAG、Agent、Assistant、health 端点；配置为内部服务令牌校验。
- Java 侧包含 AI 对话、工单草稿、正式工单、RBAC、RabbitMQ 文档任务 Outbox、Retry、Dead Letter 实现。

### 审计观察

| 编号 | 优先级 | 证据 | 当前判断 | 处理 |
|---|---|---|---|---|
| F01 | P0 | `OcAiAssistantServiceImpl.answerInConversation()` 标注 `@Transactional`，中途调用 FastAPI；Agent 使用 PostgreSQL Checkpointer | 已通过超时与写入失败测试确认跨存储不一致风险；当前可恢复但非原子 | 阶段 02 设计改造 |
| F02 | P0 | `selectPersistedTurn()` 先查询后调用 Agent；唯一索引 `(conversation_id, turn_id, role)` 防止最终重复写入 | 已通过 2/5/10 路并发确认：模型被重复调用，部分请求返回重复键或死锁 | 阶段 02 首要修复 |
| F03 | P0 | 无 GitHub Actions 工作流 | 无自动质量门禁 | 后续补最小 CI |
| F04 | P1 | `requirements-dev.txt` 固定 `httpx2==2.9.1` | 全新 venv 安装成功，`pip check` 通过 | 已关闭，不再登记 |
| F05 | P1 | Python `/health` 返回 0.1.0；FastAPI 应用 0.4.0；Java Dashboard 0.8.0 | 版本信息不统一，不是功能故障 | 后续统一版本来源 |
| F06 | P1 | `rag_retrieval.py` 使用向量检索 + 有界词法扫描 + 邻块补充 | 52 条基线 Hit@5 100%，检索 MRR 0.9646，noise 0.42～0.54 | 阶段 02 检索范围与噪声治理 |
| F07 | P1 | `DocumentTaskRabbitConsumerTest` 覆盖 ACK、重复消息、重投 | 单测不能替代真实 MQ/DB 中断演练 | 补必要集成验证 |
| F08 | P2 | 未发现 Vue 独立 test/spec | 前端以构建和手工验证为主 | 后续补关键 E2E |
| F09 | P2 | `application.yml` 的 RabbitMQ 用户名和密码存在 `guest` 缺省值 | 生产漏配变量会退回弱默认凭据 | 后续去掉不安全默认值 |
| F10 | P0 | 跨型号引用具体案例 `error.memory-full` 已修复；v18 仍有 1095 等表头块残余例外 | 具体错误已关闭，两个文本级残余场景仍在 | 保留回归测试，阶段 02 彻底治理 |
| F11 | P2 | 4 条负样本在检索层仍返回 5 条证据；问答层 19/19 正确拒答 | 检索接口自身没有拒答门禁，当前调用方均经过问答层 | 后续设计统一相关性门禁 |

### 三条优先核验链路

1. 文档→RAG：Vue → Java 版本/任务 → Outbox → RabbitMQ → FastAPI 解析索引 → Milvus → RAG → 引用结果。
2. AI 多轮会话：Vue → Java MySQL 会话/消息 → FastAPI LangChain Agent → PostgreSQL Checkpoint → MySQL 消息/引用；重点测试跨存储失败和并发。
3. AI 报修→工单：Vue → Java 获取设备与知识范围 → FastAPI Agent 工具 → Java 草稿 → 用户确认 → Java 事务工单与审计。

### 缺陷登记与处置规则

发现问题先记录：编号｜复现步骤｜期望｜实际｜错误日志/TraceId｜影响范围｜优先级｜方案候选｜验收测试。

- P0：破坏数据一致性、越权或关键功能不可用。
- P1：可靠性/可维护性受限，但有替代流程。
- P2：体验和非阻塞优化。

## 内部步骤

### P01-01：冻结版本与环境清单

范围：`README.md`、`ai-service/.env.example`、`deploy/production/.env.example`、`java-server/ruoyi-admin/src/main/resources/`。

- 记录 Git SHA、Java/Maven、Python/pip、Node/npm、Docker/Compose 版本。
- 列出 MySQL、Redis、Milvus、PostgreSQL、RabbitMQ 的实际运行地址和健康状态（不写密钥）。
- 核查生产 Compose 的外部网络 `officecare-network` 与数据库依赖是否实际存在。
- 检查本地配置和公开仓库中是否有硬编码凭证或真实客户资料。

验收：环境矩阵完整，服务依赖与配置来源明确，无公开敏感信息。

### P01-02：全项目无业务依赖测试和构建

Python（在 `ai-service/`）：

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pip check
.\.venv\Scripts\python.exe -m pytest -q
```

Java（在 `java-server/`）：

```powershell
mvn -version
mvn -pl ruoyi-officecare -am test
mvn -pl ruoyi-admin -am -DskipTests package
```

Vue（在 `web-admin/`）：

```powershell
npm ci
npm run build:prod
```

验收：记录 PASS / FAIL / BLOCKED、执行时间、具体失败日志与环境信息；失败前不得宣称通过。

### P01-03：部署与健康检查

路径：`deploy/production/docker-compose.yml`、`ai-service/app/api/health.py`、Java HealthController、Nginx `default.conf`。

- 审核环境变量，确认 `.env` 在本地且未提交到仓库。
- 在具备依赖的机器上运行 `docker compose --env-file .env -f deploy/production/docker-compose.yml config --quiet`。
- 确保外部数据库、Agent PostgreSQL schema 与 Milvus 已准备好，再启动生产式演示环境。
- 检查 Nginx、Java、FastAPI 健康端点；实际业务可用性须另行验证。

验收：服务成功启动，容器/依赖健康，健康接口可访问，日志无持续错误。

用户决定：本阶段跳过 P01-03，以上命令和检查均未执行。

### P01-04：知识库与 RAG 端到端

路径：`OcDocumentServiceImpl`、`OcDocumentProcessingServiceImpl`、`OcDocumentIndexingServiceImpl`、`DocumentTaskRabbitConsumer`、`document_parser.py`、`document_indexing.py`、`rag_retrieval.py`。

主流程：新建测试知识库 → 上传测试 PDF/DOCX → 解析任务 → 向量索引 → 范围限定检索 → 带引用回答 → 上传新版本 → 更新/删除后检查旧引用。

异常流程：无效文件、空内容、模型服务失败、Milvus 不可用、任务重复提交、重试/死信。

验收：文档任务状态和向量条数一致；无跨型号引用；失败可观察、可恢复；记录耗时与错误码。

### P01-05：AI 多轮对话与跨库一致性

路径：`OcAiAssistantServiceImpl.java`、`OcAiMessageMapper.xml`、`assistant_agent.py`、`agent_checkpoint.py`、SQL 009、015。

- 验证同一会话多轮持续记忆，刷新界面仍能还原消息。
- 相同 `turnId` + 相同问题重试应返回同一结果；同一 `turnId` 不同问题应冲突。
- 并发 2～10 次提交同一 `turnId`，记录实际模型调用次数和数据库行数。
- 注入失败场景：LLM 响应成功而 MySQL 写入失败；FastAPI checkpoint 成功、Java 调用超时；删除会话时 PostgreSQL 不可用。
- 验证引用版本被撤销后，历史记忆不能替代当前证据。

验收：定义并量化预期与实际一致性，提供能复现问题的自动化用例；缺陷不在本阶段无验证地修复。

### P01-06：Agent 草稿、正式工单与权限

路径：`OcAgentDraftServiceImpl.java`、`OcWorkOrderServiceImpl.java`、`OcWorkOrderStatusPolicy.java`、`troubleshooting_agent.py`。

- 分析设备 → 获取模型范围内证据 → 草稿 → 人工编辑 → 确认 → 正式工单 → 处理记录。
- 测试重复确认同一草稿、并发确认、无权限访问、跨用户草稿访问、非法状态流转。
- 验证 AI 不直接写正式工单，且操作日志能追溯请求主体与记录。

验收：人工确认前无正式工单；重复操作不产生多张单；越权和非法流转被拒绝。

### P01-07：真实 RAG 评测与基线报告

路径：`ai-service/evaluation/datasets/*.jsonl`、`app/evaluation/runner.py`、`scripts/evaluate_rag.py`。

- 对三份评测集（28+12+12，共 52 条）逐组运行检索/问答评测。
- 汇总 Hit@K、MRR、引用命中、拒答准确率、时延 P50/P95、失败类型。
- 注意：当前实现的 `recall_at_k` 对正样本只要至少命中一条来源就记为 1，含义接近 Hit@K；报告须标明，不可混称严格 Recall@K。
- 保存非敏感的评测报告、模型与索引配置、数据版本、执行时间，确保可复现。

验收：形成带版本、样本量、指标定义和失败案例的基线报告。不预设必须达到的准确率。

## 验证方式

- 三端构建/测试结论真实可追溯，失败项已分类。
- 三条关键链路跑通或明确列出可复现阻塞，并有解决顺序。
- 数据库/缓存/队列/向量库/Checkpoint 的责任边界和异常恢复情况有记录。
- RAG 至少有真实基线报告；无法运行时列为阶段阻塞，不填写虚假数值。
- 阶段 01 审计结论、问题清单和阶段 02 最小改造列表统一写入本文“结果”，不另建报告文件；未修复的 P0 列为阶段 02 首要任务。

## 结果

阶段性结果（2026-10-17）。本阶段结论：**暂不通过**。P01-05 因同一 `turnId` 并发重复调用模型而 FAIL；P01-03 由用户决定跳过，未验证且不计入本轮阻塞。其余工作包有实际执行证据。

### 工作包结论

| 工作包 | 结论 | 证据等级 | 说明 |
|---|---|---|---|
| P01-01 环境与配置 | PASS（Docker 部分未验证） | S/I | 环境矩阵、测试资源、敏感信息检查完成；P01-03 用户跳过 |
| P01-02 三端测试与构建 | PASS | T | Python 158、Java 90、Vue 生产构建成功 |
| P01-03 部署与健康检查 | SKIPPED（用户决定跳过） | 未验证 | Compose、容器启动、网络、DNS、Nginx、容器健康检查未执行 |
| P01-04 文档与 RAG 闭环 | PASS（F10 残余未关闭） | E/I | 上传、解析、索引、回答、版本、删除、重试、死信、Milvus/Embedding 故障均实测 |
| P01-05 AI 多轮会话与跨库一致性 | FAIL | E/I | 多轮、重试、冲突、故障恢复通过；并发幂等失败，F01/F02 风险确认 |
| P01-06 Agent 草稿与工单 | PASS（工单可见范围待决策） | E | 草稿、人工确认、并发确认、状态机、审计通过 |
| P01-07 RAG 基线 | PASS（F11 未关闭） | I | 问答 52/52，检索 48/52，4 条失败均为负样本 |

### P01-01 环境与配置（S/I）

- 工具链：Java 17（Oracle JDK）、Maven 3.9.4、Python 3.13.12、Node v24.16.0、npm 11.13.0；本机无 Docker/Compose、无 WSL。
- 基础设施协议级检查通过：MySQL 8.0.46、Redis、RabbitMQ 4.1.8、Milvus、PostgreSQL 均可连接；实际依赖位于 `192.168.100.128` 的映射端口。
- 测试资源已隔离到 `officecare_ai_test`、`officecare_agent_test`、`/officecare_test`、`officecare_document_chunks_test_v1`、Redis database `15` 和 `D:/officecare-data/p01-test`。
- 公开仓库与示例配置未发现高置信度硬编码 API Key、私钥或真实密码；本地未跟踪的 `application-dev.yml` 含开发环境凭据，已被忽略。
- `application.yml` 的 RabbitMQ `guest` 缺省值登记为 F09；`deploy/production/.env` 不存在。
- 生产 Compose 声明外部网络 `officecare-network`，MySQL/Redis/Milvus/PostgreSQL 为外部依赖，RabbitMQ 由 Compose 启动；P01-03 用户决定跳过，生产 Compose、外部网络和容器内 DNS 未验证。

### P01-02 三端构建与测试（T）

| 端 | 实际命令 | 结果 |
|---|---|---|
| Python | `ai-service/.venv/Scripts/python.exe -m pytest -q` | `158 passed`；`pip check` 无冲突 |
| Java 测试 | `mvn -pl ruoyi-officecare -am test` | `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0` |
| Java 打包 | `mvn -pl ruoyi-admin -am -DskipTests package` | BUILD SUCCESS，生成 `ruoyi-admin.jar` |
| Vue | `npm ci && npm run build:prod` | 生产构建成功，生成 `dist/` |

说明：Python 与 Java 单测主要使用 fake/mock，不等同于真实 MySQL/MQ 集成；真实集成结果见 P01-04～P01-06。

### P01-03 部署与健康检查（SKIPPED，用户决定跳过）

- 用户决定跳过本项验收，本轮不再要求提供 Docker 主机或 SSH。
- 未执行 `docker compose config`、镜像构建、容器启动、外部网络、容器内 DNS、Nginx 代理和容器健康检查；该项未达到 I，不能写成通过。
- 已调查但未使用的环境：`192.168.100.128:22` 可建立 TCP 连接，root 公钥登录被拒；`10.11.154.223` 的 20056/20064/20084 转发端口均失败；Docker API `2375/2376` 未开放；本机无 Docker/Compose/WSL。
- 现有开发进程的 `/health` 只能证明端口和服务可用，不能代替 Compose 部署验收。
- 将来如需恢复部署验收，再在具备 `officecare-network` 和外部数据库依赖的主机上补 Compose 配置检查、启动、Nginx/Java/FastAPI 健康检查和持续日志观察。

### P01-04 文档与 RAG 闭环（E/I，PASS）

主链路（测试库，文档 `9002`）：

- 上传 `9002 v1` → parse 成功（attempt=1）→ index 成功（attempt=1）→ 回答带引用 `documentId=9002, documentVersionId=9002`。
- `indexed_chunk_count=1`，Milvus 实际 1 条；原始文件位于隔离存储根目录。
- 重复提交 parse/index 均返回业务 500，任务数增量为 0。
- 上传 `v2=9003` 后当前版本切换为 `9003`，回答只引用 `9003`，不引用旧版 `9002`。
- 删除文档后文档与两个版本 `del_flag=2`，两个 `delete_index` 任务成功，Milvus 两版本均降为 0；删除后回答 `answered=false`、0 引用。
- 非法扩展名与空文件均返回业务 500，文档数量不变。

异常与故障（E/I）：

| 场景 | 结果 |
|---|---|
| 永久解析失败 | 文档 `9003` / 版本 `9004`，`TEXT_ENCODING_UNSUPPORTED`，attempt=1，status=failed，Outbox `consume_status=4` 且有 `dead_letter_time`；该类错误不可重试，直接死信，符合策略 |
| 瞬时解析失败自动重试 | 文档 `9006` / 版本 `9007`；故障代理令前 2 次 parse 返回 503，第 3 次成功；parse attempt=3、index attempt=1，Milvus 1 条，Outbox 依次为 retry、retry、success |
| Milvus 不可用 | 隔离实例 `8015` 返回 HTTP 503 `MILVUS_UNAVAILABLE`；恢复正常实例后同一版本索引成功（Milvus 1 条），删除后 0 条 |
| Embedding 服务失败 | 隔离实例 `8016` 返回 HTTP 503 `EMBEDDING_UNAVAILABLE`；恢复正常实例后索引成功，删除后 0 条 |

- 结论：写链路、版本切换、删除、重复提交、自动重试、死信、Milvus/Embedding 故障均有实际证据，P01-04 主验收通过。
- 遗留：F10 具体案例 `error.memory-full` 已关闭，但 v18 的 `error.access-denied`、`error.duplex-disabled` 仍可命中 1095 表头块；这是已知文本级残余，阶段 02 处理。F11 检索层负样本仍返回证据，但问答层正确拒答。

### P01-05 AI 多轮会话与跨库一致性（E/I，FAIL）

| 场景 | 期望 | 实际 | 结论 |
|---|---|---|---|
| 多轮记忆与刷新 | 4 条消息且 ID 稳定 | 4 条，角色 user/assistant/user/assistant，刷新 ID 一致，checkpoint message blob=6 | PASS |
| 同 `turnId` 重试（同问题） | 返回同一 assistant 消息，不重复调模型 | 两次返回 assistantMessageId=18，第二次 0.02s，无新增 Agent/模型调用 | PASS |
| 同 `turnId` 不同问题 | 冲突 | 业务 500，提示“本轮请求ID已用于其他问题”，无新增调用 | PASS |
| 同 `turnId` 并发 5 路 | 全部成功，仅 1 次 Agent、≤2 次模型调用、1 对消息 | 1 成功、4 失败（3 重复键 + 1 死锁），5 次 Agent、10 次模型调用，最终 2 行 | **FAIL（F02 确认）** |
| 同 `turnId` 并发 2 路 | 同上 | 1 成功、1 失败，2 次 Agent、4 次模型调用 | **FAIL（F02 确认）** |
| 同 `turnId` 并发 10 路 | 同上 | 1 成功、9 失败，10 次 Agent、20 次模型调用 | **FAIL（F02 确认）** |
| 慢 Agent 期间 MySQL 事务 | 不长时间持锁 | 单次请求 2.52s，事务 RUNNING 最长约 3s | 风险确认（F01） |
| Agent 成功、MySQL 写入失败 | 请求失败但不是最终重复 | Java 500，MySQL 0 行，checkpoint 已保存；去掉注入触发器后重试 200，无新增模型调用，写入 2 行 | PASS（可恢复，但非原子） |
| checkpoint 成功、Java 调用超时 | 返回失败且可恢复 | 1.24s 超时返回 500，PG 4 个 message blob / 8 行 checkpoint，MySQL 0 行；正常实例重试 200，无新增模型调用，写入 2 行 | **FAIL（F01 跨库不一致已确认）** |
| 删除会话时 AI/PG 不可用 | 删除失败且数据保留 | 业务 500，`del_flag=0`，消息和 checkpoint 保留；恢复后删除成功，`del_flag=2`，checkpoint 清零 | PASS |
| 引用版本撤销 | 历史记忆不能替代当前证据 | `answered=false`，0 引用，未调用 FastAPI | PASS |

- 结论：P01-05 **FAIL**。核心问题是同一 `turnId` 并发下模型被重复调用，且请求失败；唯一索引只能保证最终不重复写入，不能阻止重复推理。超时场景还存在“PG 已保存、MySQL 未保存”的跨存储窗口，但可通过重试恢复。
- F01、F02 已从静态风险升级为测试环境确认的问题；阶段 01 只记录，不在无设计情况下修复。

关键错误输出：

- 并发 5 路：3 个请求返回 `Duplicate entry '12-...-user' for key 'oc_ai_message.uk_oc_ai_message_turn_role'`，1 个请求返回 `Deadlock found when trying to get lock; try restarting transaction`；最终 MySQL 只有 2 行，但只有 1 个请求成功。
- 并发 2 路：`fastapi_turns_delta=2`、`llm_calls_delta=4`、`success_count=1`；并发 10 路：`fastapi_turns_delta=10`、`llm_calls_delta=20`、`success_count=1`。不能仅凭“最终 2 行”判定通过。
- 超时实例：Java 在 1.24s 返回“调用FastAPI多轮AI助手接口失败”，PG 已有 4 个 message blob / 8 行 checkpoint，MySQL 0 行。
- Agent 成功但 MySQL 写入失败：Java 返回 `P01 injected write failure`，MySQL 0 行，checkpoint 已保存；故障移除后重试成功。
- 删除会话时 PG 不可用：返回 `Injected PostgreSQL unavailability for P01-05 delete test.`，会话和 checkpoint 均保留；恢复后删除成功。
- 慢模型事务：单次请求 2.52s，`information_schema.innodb_trx` 观察到 MySQL 事务 RUNNING 最长约 3s；风险是结构性的，模型更慢时持锁时间会继续拉长。

### P01-06 Agent 草稿、正式工单与权限（E，PASS）

- 主流程：设备分析约 6.34s，工具顺序为 `inspect_device_context → search_product_knowledge → build_work_order_draft`；分析后只有草稿（`draft_id=7`），没有正式工单。
- 人工编辑持久化成功；用户 `octest_b` 对 `octest_a` 草稿的获取、编辑、确认均被拒绝；普通角色 `octest_c` 的草稿、工单、分析接口均返回 403，且没有新增 AI 调用或草稿。
- 确认后创建 1 张正式工单（`work_order_id=5`），草稿状态变为已确认；重复确认返回同一工单，不新增工单。
- 非法状态流转被拒绝；状态机 `0→1→2→3` 成功，关闭后重开和追加记录被拒绝。
- 工单记录 7 条：`CREATE/COMMENT/ASSIGN/STATUS/COMMENT/STATUS/STATUS`，操作人都能追溯到 `octest_a`；操作日志增量：分析 1、草稿编辑 1、确认 2、状态 5、记录 3，总计 12。
- 并发确认 5 路全部成功，返回同一工单 `6`，只创建 1 张工单、1 条 CREATE 记录。
- 权限边界：草稿按用户隔离；正式工单当前按 `oc_service` 角色可见，`octest_b` 可读取 `octest_a` 的工单详情。这是当前 RBAC 行为，不登记为越权 Bug，但阶段 02 需要决定是否按用户/组织收紧可见范围。
- 最终测试数据：主流程草稿 7、工单 5；并发草稿 8、工单 6。

### P01-07 RAG 真实基线（I，PASS）

- 数据范围：3 份数据集共 52 条（28+12+12）；知识库 1/2/3 对应 DCP-L2550DW、DCP-L2535DW、MFC-7895DW。
- 运行配置：Milvus 测试集合；`top_k=5`、`score_threshold=0.45`；embedding `Qwen/Qwen3-VL-Embedding-8B`；chat `deepseek-v4-pro`；dense candidate 100、lexical scan 5000、lexical candidate 100、neighbor window 2。
- 指标口径：`recall_at_k` 对正样本至少命中一条来源即记 1，实际含义是 Hit@K；`noise_rate = 1 - 相关引用数 / 返回引用数`；拒绝准确率要求 `answered=false` 且无引用。

| 模式 | 用例 | 通过 | 通过率 | P50 | P95 |
|---|---|---|---|---|---|
| 检索 | 52 | 48 | 92.3% | 409.1 ms | 954.1 ms |
| 问答 | 52 | 52 | 100% | 约 2.0 s | 约 3.8 s |

- 检索：33 条正样本 Hit@5 = 100%，MRR = 0.9646，noise = 0.4545；19 条负样本中 15 条返回 0 条 evidence，4 条各返回 5 条（F11）。
- 问答：33 条正样本全部回答，19 条负样本全部拒答，引用命中率 100%；F10 具体案例已修复，`error.memory-full` 只引用 DCP 的 v18 1092。
- 残余：F10 的两个文本级例外和 F11 的检索层负样本门禁未关闭；RAG 基线为单次运行，未做方差和逐条人工复核。

### 实际执行命令与产物

| 用途 | 命令/产物 |
|---|---|
| P01-02 Python | `ai-service/.venv/Scripts/python.exe -m pytest -q` → 158 passed |
| P01-02 Java | `mvn -pl ruoyi-officecare -am test`；`mvn -pl ruoyi-admin -am -DskipTests package` |
| P01-02 Vue | `npm ci`；`npm run build:prod` |
| P01-04 主链路 | `notes/p01_04_test.py` → `notes/p01-04-results.json` |
| P01-04 故障补测 | `notes/p01_04_faults.py` → `notes/p01-04-faults.json` |
| P01-05 主场景 | `notes/p01_05_test.py` → `notes/p01-05-results.json` |
| P01-05 并发扩展 | `notes/p01_05_concurrency_scale.py` → `notes/p01-05-concurrency-scale.json` |
| P01-05 超时/删除 | `notes/p01_05_timeout.py`、`notes/p01_05_delete.py` |
| P01-06 主流程 | `notes/p01_06_test.py` → `notes/p01-06-results.json` |
| P01-06 工单权限 | `notes/p01_06_workorder_permission.py` → `notes/p01-06-workorder-permission.json` |
| P01-07 RAG | `ai-service/scripts/evaluate_rag.py`，报告在忽略目录 `ai-service/evaluation-results/audit-01-fb73f81/` |

测试脚本、日志和结果均在 `notes/` 和忽略目录；没有进入 Git。测试资源已在验收记录写入后回收：`officecare_ai_test`、`officecare_agent_test`、Milvus 测试集合、RabbitMQ `/officecare_test`、Redis database 15 和 `D:/officecare-data/p01-test` 均已删除或清空；主开发服务 `8000/8080/80` 未受影响。清理后的 `notes/` 结果仍保留，用于复现和阶段 02 对照。

### 阶段 02 最小改造清单

按用户指定的 Java 后端优先排序：

1. **F02（P0，Java）**：为同一 `turnId` 增加服务端幂等占位/锁，在调用 Agent 前原子取得处理权；重复请求返回同一结果或明确冲突，确保模型最多调用一次。需要覆盖 2/5/10 路并发和异常回滚。
2. **F01（P0，Java）**：把 FastAPI Agent 调用移出 MySQL `@Transactional`；拆成短事务读取、外部调用、短事务写入，并设计 PG checkpoint 与 MySQL 消息的补偿/对账。覆盖 Agent 成功但 MySQL 失败、Java 超时、进程中断和重试恢复。
3. **P01-03 部署验收（用户已跳过）**：本轮不作为阻塞项；如以后恢复部署验收，再补 Compose 配置、容器启动、`officecare-network`、容器内 DNS、Nginx 和健康检查，不新建基础设施。
4. **F03（P0，CI）**：增加最小 GitHub Actions，覆盖 Python pytest、Java test/package、Vue build。
5. **F10/F11（RAG，P0/P2）**：保留 F10 回归用例；阶段 02 设计按型号拆分文档或表行级范围，消除 1095 表头块残余；为检索层负样本设计统一门禁。
6. **F09（P2）**：移除 RabbitMQ `guest` 默认值，生产缺变量直接启动失败。
7. **F05/F06/F07/F08（P1/P2）**：统一版本来源、治理检索噪声、补真实 MQ/DB 故障演练、补关键前端 E2E。
8. **工单可见范围（产品决策）**：确认正式工单继续按 `oc_service` 角色可见，还是按用户/组织收紧；在决策前不把当前 RBAC 行为当作越权缺陷修复。

### 未完成项与遗留

- P01-03：用户决定跳过，Compose 外部网络、容器内 DNS、Nginx、容器健康检查未验证；不再作为本轮阻塞。
- P01-05：F01、F02 的正式修复未做，留待阶段 02；当前只完成复现和恢复验证。
- P01-04：F10 两个文本级残余、F11 检索层负样本门禁未关闭。
- P01-07：RAG 为单次运行，未做重复运行方差和人工逐条复核。
- F03/F05/F06/F07/F08/F09 仍未修复，已列入后续阶段。
- 本阶段未提交、未推送、未合并；后续提交和推送需用户明确要求。
