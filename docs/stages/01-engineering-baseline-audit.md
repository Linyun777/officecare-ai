# 阶段 01：工程基线审计与验收

状态：开发中

## 目标与用户看到的效果

- 审查现有代码与环境，验证三条核心业务链路，不增加功能、不大规模重构。
- 给出真实可追溯的三端构建/测试结论，明确 PASS、FAIL、BLOCKED 和复现方式。
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
- Commit：`fb73f8113a4d53efbc12f4ea35e0d5c1b8af9563`（2026-10-17）。
- 本地 `master` 的 `1a8affb` 与审计基准的业务代码一致；差异仅为文档和 README。
- 审计在独立 worktree 执行，主工作目录保留文档变更。

### 已完成的静态盘点（S）

- 仓库约 857 个文件，包含 Java 业务、Vue 管理端、FastAPI AI 服务、Docker Compose 配置。
- Python 测试文件 18 个；Java 测试文件 22 个；未发现前端专门测试文件。
- 未发现 `.github/workflows`，GitHub Actions 历史执行记录为 0，当前提交的 combined status 没有状态检查。
- 数据库迁移/初始化：`java-server/sql/officecare/001...020` 共 20 份编号 SQL。
- FastAPI 提供文档处理、RAG、Agent、Assistant、health 端点；配置为内部服务令牌校验。
- Java 侧包含 AI 对话、工单草稿、正式工单、RBAC、RabbitMQ 文档任务 Outbox、Retry、Dead Letter 实现。
- 设计稿形成时尚未获得本阶段的本地测试结果、启动日志或真实模型评测数据；实际执行结果见本文“结果”，本条不再代表当前状态。

### 第一批审计观察

| 编号 | 优先级 | 证据 | 初步判断 | 所需验证 |
|---|---|---|---|---|
| F01 | P0 | `OcAiAssistantServiceImpl.answerInConversation()` 标注 `@Transactional`，中途调用 FastAPI；Agent 使用 PostgreSQL Checkpointer | 外部推理与 MySQL 事务交叠，可能持锁过久，且两处存储无法共同原子提交 | 慢模型、模型成功但 MySQL 失败、同一 turnId 并发双击、重试恢复 |
| F02 | P0 | `selectPersistedTurn()` 先查询后调用 Agent；唯一索引 `(conversation_id, turn_id, role)` 防止最终重复写入 | 具备幂等结果约束，但不能仅凭唯一索引证明并发下只调用一次模型 | 并发请求测试，记录 LLM 调用次数、两端最终状态 |
| F03 | P0 | GitHub 仓库不存在工作流配置及历史 Actions 执行 | 无自动质量门禁 | 本地跑通后新增最小 CI（后续小改造） |
| F04 | P1 | `requirements-dev.txt` 固定 `httpx2==2.9.1`，测试使用 `fastapi.testclient.TestClient` | 全新 venv 安装成功，`pip check` 无冲突，150 条测试通过；不是依赖缺陷 | 已关闭，不再列入改造 |
| F05 | P1 | Python `/health` 返回 `0.1.0`；FastAPI 应用版本 `0.4.0`；Java Dashboard 内版本 `0.8.0` | 版本信息不统一（不代表功能故障） | 统一版本来源的技术方案，当前只登记 |
| F06 | P1 | `rag_retrieval.py` 使用向量检索 + 有界扫描词法排序 + 邻块补充 | 已完成 52 条基线：Hit@5 100%，检索 MRR 0.9646，noise 0.42～0.54；上下文噪声偏高，但不在审计阶段重构 | 阶段 02 结合 F10 一起设计检索范围与噪声治理 |
| F07 | P1 | `DocumentTaskRabbitConsumerTest` 含 ACK、重复消息、重投等测试 | 单元测试覆盖多种分支，尚不能替代真实 MQ/数据库故障演练 | RabbitMQ/DB 集成测试及进程中断演练 |
| F08 | P2 | 未发现 Vue 的独立 test/spec 文件 | 前端以手工/E2E 验证为主 | 构建检查、关键页面手工验收；后续补 E2E |
| F09 | P2 | `application.yml` 的 RabbitMQ 用户名和密码使用 `guest` 作为缺省值 | 本地开发方便，但生产环境漏配变量时会退回弱默认凭据 | 生产 Compose 启动检查必须确认变量已显式覆盖；后续可改为无默认值 |
| F10 | P0 | KB1（DCP-L2550DW）的 `error.memory-full` 用例返回“安装新的墨粉盒才能继续接收传真”，引用 v18 chunk 1326；该块来自 MFC-7895DW 传真章节。v18 有 256 块提及非本型号内容，110 块通过现有守卫，其中 103 块非导航文本；v19 为 94/94（通过守卫/非导航），v20 为 114/114 | 现有 `is_applicable_to_model()` 只拦截带“相关型号/适用型号”或型号标题的块，未标注的多型号说明书内容会跨型号进入回答 | 阻断 P01-04“无跨型号引用”验收；阶段 02 首要修复候选。需要设计块级型号范围、文档拆分或更强的适用性守卫，并为该用例补回归测试 |
| F11 | P2 | 4 条负样本（`negative.board-repair`、`negative.weather`、`negative.price`、`negative.prompt-injection`）在检索层各返回 5 条 evidence；同一批负样本在问答层 19/19 正确拒答 | 检索接口本身没有拒答门禁，当前调用方都经过问答层，因此未形成实际错误回答 | 若后续出现直接调用检索的入口，需要补负样本门禁；当前只登记，不阻断验收 |

### 三条优先核验链路

1. 文档→RAG：Vue → Java 版本/任务 → Outbox → RabbitMQ → FastAPI 解析索引 → Milvus → RAG → 引用结果。
2. AI 多轮会话：Vue → Java MySQL 会话/消息 → FastAPI LangChain Agent → PostgreSQL Checkpoint → MySQL 消息/引用；重点测试跨存储失败和并发。
3. AI 报修→工单：Vue → Java 获取设备与知识范围 → FastAPI Agent 工具 → Java 草稿 → 用户确认 → Java 事务工单与审计。

### 缺陷登记与处置规则

发现问题先记录：编号｜复现步骤｜期望｜实际｜错误日志/TraceId｜影响范围｜优先级｜方案候选｜验收测试。

- P0：破坏数据一致性、越权或关键功能不可用。
- P1：可靠性/可维护性受限，但有替代流程。
- P2：体验和非阻塞优化。

### 当前限制

- 审计运行环境缺少 Docker/Compose，不能执行容器部署，也不能验证生产 Compose 的外部网络、容器内 DNS 与 Nginx。
- 已完成 MySQL、Redis、RabbitMQ、Milvus、PostgreSQL 的协议级只读检查；仍没有 `*_test` 数据库，不能对开发库执行写链路、并发或故障注入。
- 本地 Python 虚拟环境、Java/Maven、Node/npm 可用，P01-02 已实际执行；RAG 检索与问答基线已完成，报告保存在忽略目录。

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
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
python -m pip check
python -m pytest -q
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

阶段性结果（2026-10-17，阶段仍在开发中）。

### P01-01 环境与配置基线（I，部分完成）

- 审计 worktree：`D:\Code\shizhan\officecare-ai-audit-01`，detached HEAD `fb73f8113a4d53efbc12f4ea35e0d5c1b8af9563`，`git status` 干净；仓库未发现 `.github/workflows`。
- 工具链：Java 17（Oracle JDK，`D:\code-tools\java\JDK\jdk17`）、Maven 3.9.4、Python 3.13.12、Node v24.16.0、npm 11.13.0；Docker 与 Compose 未安装。
- 基础设施协议级检查（只读）：
  - MySQL 8.0.46 @ `192.168.100.128:3308`：连接与查询成功；实例内只有 `officecare_ai`，没有 `*_test`。
  - Redis @ `192.168.100.128:6381`：`PING=True`。
  - RabbitMQ 4.1.8 @ `192.168.100.128:5673`，管理端 `15673`：队列数 4。
  - Milvus @ `192.168.100.128:19531`：集合 `officecare_document_chunks_v1` 存在，`row_count=3271`，可查询实体 3270。
  - Agent PostgreSQL @ `192.168.100.128:5433`：`select 1` 成功。
  - localhost 的 `6379`、`19530`、`5432`、`5672` 未监听；实际依赖都在 `192.168.100.128`。
- 现有进程：`8000` FastAPI、`8080` Java、`80` Vite dev server 均来自主工作目录 `D:\Code\shizhan\officecare-ai`，不是审计 worktree 启动。FastAPI `/health` 返回 200；Java `/health` 返回 200，`/actuator/health` 返回 401；`80` 端口是 Vite，不是 Nginx。这些结果只能作为服务级集成证据，不能作为 Compose 部署验收证据。
- 未完成：本机未安装 Docker/Compose，无法验证生产 Compose 的外部网络 `officecare-network`、容器内 DNS 和 Nginx；`deploy/production/.env` 不存在。P01-03 仍为 BLOCKED。
- 配置：仓库未跟踪真实 `.env`；主工作目录存在本地 `ai-service/.env` 与 `application-dev.yml`（均被忽略）；`deploy/production/.env` 不存在。生产 Compose 声明外部网络 `officecare-network`，MySQL/Redis/Milvus/PostgreSQL 为外部依赖，RabbitMQ 由 Compose 启动。
- 敏感信息：公开仓库与示例配置中未发现高置信度硬编码 API Key、私钥或真实密码；`.env.example` 对应值为空，`application-dev.example.yml` 使用 `change-me` 占位。本地未跟踪的 `application-dev.yml` 含开发环境数据库、Redis、RabbitMQ 明文凭据（已被忽略、未进入提交），生产和共享环境必须用环境变量覆盖。未发现可确认的真实客户资料，RuoYi 默认 SQL 中的邮箱与手机号为演示数据。`application.yml` 的 RabbitMQ 凭据存在 `guest` 兜底默认值，登记为 F09。

### P01-02 三端测试与构建（T）

- Python：在 `ai-service` 创建全新 `.venv`，`pip install -r requirements-dev.txt` 成功；`pip check` 输出 `No broken requirements found.`；`pytest` 输出 `150 passed in 1.06s`（pytest 9.1.1）。`httpx2==2.9.1` 可正常安装，F04 不构成依赖故障。
- Java：`mvn -pl ruoyi-officecare -am test` 为 BUILD SUCCESS，`Tests run: 90, Failures: 0, Errors: 0, Skipped: 0`，耗时 10.984s；`mvn -pl ruoyi-admin -am -DskipTests package` 为 BUILD SUCCESS，耗时 18.506s，生成 `ruoyi-admin/target/ruoyi-admin.jar`。
- Vue：`npm ci` 成功，added 528 packages in 14s；`npm run build:prod` 成功，2469 modules transformed，built in 11.88s，生成 `dist/`。
- 说明：以上均在审计 worktree 内实际执行。Python 测试不依赖真实 MySQL/MQ，外部服务以 fake/mock 配置为主；Java 测试中的数据库与 MQ 分支同样使用替身，不代表真实集成通过。

### P01-03 部署与健康检查（BLOCKED）

- 本机未安装 Docker/Compose，无法执行 `docker compose config`、构建镜像或启动容器。
- `deploy/production/.env` 缺失；外部网络 `officecare-network` 与外部依赖的容器内 DNS 无法在本机验证。
- 现有 `8000/8080/80` 是主工作目录的开发进程，只能证明端口与 `/health` 可用，不能替代 Compose 部署验收。

### P01-04 知识库与 RAG 端到端（部分 I，写链路 BLOCKED）

- 只读一致性通过：按 Java `selectCurrentIndexedVersionIds` 的同一条件查询，7 个当前索引版本的 MySQL `indexed_chunk_count` 与 Milvus 实际条数完全一致：13=7、14=49、15=13、17=2、18=1503、19=848、20=848，合计 3270。
- Milvus `row_count=3271`，可查询实体 3270；差值 1 与已删除版本 21 的 1 个 chunk 数量吻合，判断为未压缩 tombstone（推断，未执行 compaction 验证）。
- 跨型号检查失败：v18 有 256 块提及非本型号内容，110 块通过现有守卫，其中 103 块为非导航文本；v19 为 240/94/94，v20 为 145/114/114。实际泄漏案例见 P01-07 的 `error.memory-full`，登记为 F10。
- 未执行：上传、解析、索引、版本更新/删除、无效文件、空内容、模型服务失败、Milvus 不可用、重复任务、重试/死信。原因是没有 `*_test` 数据库，按协作规则不能对 `officecare_ai` 执行写操作。
- 结论：只读一致性达到 I；“文档→RAG”完整主流程和异常流程未达到 I/E，“无跨型号引用”验收不通过。

### P01-05 AI 多轮对话与跨库一致性（BLOCKED）

- 未执行多轮记忆、刷新还原、相同 `turnId` 重试/冲突、2～10 并发提交、慢模型、MySQL 写入失败、checkpoint 成功但 Java 超时、删除会话时 PostgreSQL 不可用等场景。
- F01/F02 仍为静态判断（S）：`answerInConversation()` 在 MySQL 事务内调用 FastAPI Agent，且同一 `turnId` 是先查后调用，唯一索引只能约束最终写入。
- 原因：并发与故障注入会写入开发库；没有 `*_test` 数据库和可注入故障的 Agent PostgreSQL 环境。该链路未达到 I/E。

### P01-06 Agent 草稿、正式工单与权限（BLOCKED）

- 未执行草稿生成、人工编辑、重复确认、并发确认、无权限访问、跨用户草稿访问、非法状态流转和处理记录审计。
- 仅确认静态事实：`confirmDraft()` 使用 `select ... for update`，按 `draftId + userId` 读取；确认更新同时带 `user_id`、`status='0'`、`work_order_id is null` 条件。该保护尚未经过并发实测。
- 原因同上，没有 `*_test` 数据库；该链路未达到 I/E。

### P01-07 真实 RAG 评测与基线（I，完成检索与问答）

- 数据范围：3 份数据集共 52 条（28+12+12）。知识库与当前索引版本：

| 知识库 | 编码 | 型号 | 文档版本 ID | 用例数 |
|---|---|---|---|---|
| 1 | `BROTHER_DCP_L2550DW` | DCP-L2550DW 黑白激光多功能一体机 | 13,14,15,17,18 | 28 |
| 2 | `BROTHER_DCP_L2535DW` | DCP-L2535DW 黑白激光多功能一体机 | 19 | 12 |
| 3 | `BROTHER_MFC_7895DW` | MFC-7895DW 黑白激光多功能一体机 | 20 | 12 |

- 运行配置：Milvus 集合 `officecare_document_chunks_v1`；`top_k=5`、`score_threshold=0.45`；embedding `Qwen/Qwen3-VL-Embedding-8B`；chat `deepseek-v4-pro`；dense candidate 100、lexical scan 5000、lexical candidate 100、neighbor window 2。
- 服务：`http://127.0.0.1:8000`，为审计前已启动的主工作目录开发进程，未在审计提交上重启；同目录业务代码与 `fb73f81` 一致，但不能证明进程内已加载字节码与审计提交完全一致。评测脚本与审计提交内容一致，SHA-256 差异仅为 CRLF。
- 指标口径：`recall_at_k` 对正样本只要命中至少一条来源即记 1，实际含义是 Hit@K，不是严格 Recall@K；`noise_rate = 1 - 相关引用数 / 返回引用数`，仅在正样本上平均；拒绝准确率要求 `answered=false` 且无引用。

| 模式 | 用例 | 通过 | 通过率 | 错误 | P50 | P95 |
|---|---|---|---|---|---|---|
| 检索 | 52 | 48 | 92.3% | 0 | 409.1 ms | 954.1 ms |
| 问答 | 52 | 51 | 98.1% | 0 | 1999.1 ms | 3752.9 ms |

- 检索明细：33 条正样本 Hit@5 = 100%，MRR = 0.9646，noise = 0.4545；19 条负样本中 15 条返回 0 条 evidence，4 条各返回 5 条（`negative.board-repair`、`negative.weather`、`negative.price`、`negative.prompt-injection`）。
- 问答明细：33 条正样本全部回答，19 条负样本全部拒答，引用命中率 100%；唯一失败为 `error.memory-full`。上述 4 条负样本在问答层均正确拒答，因此检索层的 4 条失败不是错误回答，登记为 F11。
- 分组结果：

| 知识库 | 用例 | 检索通过 | 问答通过 | 检索 P95 | 问答 P95 | Hit@5 | 检索 MRR | noise |
|---|---|---|---|---|---|---|---|---|
| 1 | 28 | 24 | 27 | 961.6 ms | 4216.1 ms | 100% | 0.942 | 0.420 |
| 2 | 12 | 12 | 12 | 715.7 ms | 3261.7 ms | 100% | 1.000 | 0.467 |
| 3 | 12 | 12 | 12 | 633.3 ms | 3463.7 ms | 100% | 1.000 | 0.543 |

- 失败案例 `error.memory-full`：期望答案包含“内存已满/内存”与“降低分辨率/清除内存/退出”；实际回答“根据资料，当设备提示内存已满时，需要安装新的墨粉盒，才能继续接收传真。[资料1]”。引用为 v18、page 472、chunk 1326，标题“继续模式下墨粉用尽时”，原文含“内存已满时>，必须安装新的墨粉盒，才能继续接收传真”；同页前文 chunk 1325 明确标注“相关型号: MFC-7895DW”，因此该内容属于 MFC-7895DW 传真场景，不是 DCP-L2550DW 的处理方式。登记为 F10。
- 报告：`ai-service/evaluation-results/audit-01-fb73f81/`（已被 `.gitignore` 忽略），每组检索/问答各一份 JSON 与 Markdown，生成时间为 2026-10-17T04:55Z～04:58Z。
- 复现：按上表参数调用 `ai-service/scripts/evaluate_rag.py`；内部 Token 只从本机运行环境读取，不写入文档或命令行参数。
- 未做：重复运行与方差、严格 Recall@K、52 条答案的逐条人工复核。当前数字是单次运行基线，不预设达标线。

### F10 修复结果（2026-10-17，`fix/f10-model-scope-guard`）

- 代码：`ai-service/app/services/retrieval_quality.py`、`ai-service/app/services/rag_retrieval.py`、`ai-service/app/services/rag_answer_cache.py`；新增 8 条回归用例。Python 全量 `158 passed in 1.96s`。
- 设计：
  1. 块自身有明确型号声明时，按自身声明过滤。
  2. 块自身无声明时，只继承紧邻前一块（lookback=1）的型号组标题或“相关型号”声明；仅当声明与当前型号完全不相交时过滤。
  3. 独立成行的型号组标题（如 `DCP-7195DW/MFC-7895DW`）参与后续块的继承作用域，但表头块自身保留，避免误伤共享错误表行。
  4. 文档中的 `[错误标签]` 与问题文本精确匹配时加权；标签至少 3 字且包含状态/错误词，避免 `[传真]`、`[打印机]` 等菜单名造成误命中。
  5. 检索算法版本写入 RAG 缓存键，部署后不会继续命中旧的跨型号回答。
- 验证：
  - `error.memory-full`：检索 1/1、问答 1/1；回答引用 v18 chunk 1092（DCP 组“降低分辨率/清除内存”），不再引用 1326/1096。
  - KB1 28 条：检索 24/28、问答 28/28（基线 24/28、27/28）。
  - KB2 12 条：检索 12/12、问答 12/12。
  - KB3 12 条：检索 12/12、问答 12/12。前一次运行曾出现 `mfc7895.fax.pc-send` 漏答“黑白传真”，单条重跑 3/3 通过，最终复跑 12/12，判断为生成波动而非检索回归。
  - 合计：问答 52/52（基线 51/52）；检索 48/52，4 条失败全部是负样本，属 F11，不是新增回归。
  - 运行条件：评测服务关闭 Agent 记忆（Windows 下 psycopg 异步池不可用）和 Redis（避免旧缓存影响）；词法阈值保持默认 0.18，未改产品配置。
- 结论：F10 的具体泄漏复现已关闭，`error.memory-full` 达到“无跨型号引用”。全库仍保留两个文本级已知例外：DCP 知识库的 `error.access-denied`、`error.duplex-disabled` 可命中 v18 chunk 1095（MFC 组表），因为该表头块自身保留；对应问题措辞只匹配 MFC 表。彻底消除需要阶段 02 的按型号拆分文档或表行级范围，不在本阶段改数据集期望。
- 遗留：F11（检索层负样本不拒答）仍在；阶段 02 可设计统一的最小相关性门禁。RAG 基线为单次运行，KB3 单条问答存在生成波动。

### 阶段 02 最小改造候选

按用户指定的“Java 后端优先”排序：

1. F01/F02（P0，Java）：把外部 Agent 调用移出 `@Transactional`；为同一 `turnId` 增加服务端幂等占位/锁与冲突返回；设计跨存储失败恢复。验收需要 `*_test` 数据库和并发/故障注入用例。
2. F10（P0，RAG，具体复现已修复）：保留内容级守卫与回归用例；阶段 02 再设计按型号拆分文档或表行级范围，消除 1095 表头块等残余文本级例外。F11 单独设计检索层负样本门禁。
3. F03（P0，CI）：在本地三端命令稳定后新增最小 GitHub Actions，至少覆盖 Python pytest、Java test/package、Vue build。
4. F09（P2）：去掉 RabbitMQ `guest` 兜底默认值，生产启动时缺失变量直接失败。
5. F05（P1）：统一 Python/Java/前端的版本来源。
6. 测试环境（阻塞项）：准备 `*_test` 数据库与可注入故障的 Agent PostgreSQL/MQ 环境，才能关闭 P01-04/05/06 的 BLOCKED。

F04 已证明不是缺陷（`httpx2==2.9.1` 可安装，`pip check` 通过），不再列入改造。

### 未测项与遗留

- P01-03：Compose 外部网络、容器内 DNS、Nginx、容器健康检查未验证。
- P01-04：上传/解析/索引/版本更新/删除、无效文件、空内容、Milvus 不可用、重复任务、重试/死信未执行。
- P01-05：并发 `turnId`、慢模型、跨存储失败注入、删除会话时 PostgreSQL 不可用未执行。
- P01-06：重复/并发确认、越权、跨用户草稿、非法状态流转未执行。
- F06：Hit@5 高但 noise 0.42～0.54，检索上下文噪声偏高；本轮只量化，不重构。
- F07：RabbitMQ 单元测试未替代真实 MQ/DB 故障演练。
- F08：前端没有独立测试，只有生产构建通过。
- RAG 基线为单次运行，未做重复运行方差与人工逐条复核。
