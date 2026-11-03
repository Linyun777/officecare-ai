# 阶段 05：不可变索引代次（M2-2）

## 目标与用户看到的效果

为同一文档版本保存多次索引构建结果，明确每次使用的解析结果、分块规则、Embedding 空间和来源锚点。重建失败只留下失败候选，不能覆盖当前可用索引。

用户已授权自行完成整个 M2 的设计、开发、验收与分节推送。M2-1 已验收并推送 `495e4b3`，GitHub CI [41572273909](https://github.com/Linyun777/officecare-ai/actions/runs/41572273909) 的 Java、Python、前端三项通过。本节交付代次构建与台账；下一节再接入发布、回退、检索选择及管理界面，完成总体路线中整个 M2 的验收。

## 范围与不做

- 基于 `495e4b3`，独立分支 `codex/m2-index-publication`；主目录和旧工作区保留。
- 增加 Java 代次台账、只读列表和构建接口；增加 FastAPI 代次索引 / 清理接口，复用 M2-1 的候选分块与来源数据。
- 不在本节切换正式检索，不改旧 Collection Schema、不删除或重建旧向量，不改变旧自动解析 / 索引任务的输入。
- 沿用现有依赖。新增 SQL `020_officecare_document_index_generation.sql`，仅新增代次台账表；不改已有业务表。新 Collection 的名称由现有名称固定追加 `_generations`，无新全局配置。
- 本地联调用合成文档、确定性测试向量、`*_test` 数据库及专用测试 Collection，不调用真实 Embedding、LLM 或 MinerU；不把模拟向量检索结果当回答质量成绩。

## 设计

### 1. 每次构建独立保存

- Java 生成新的 32 位 UUID `generationId`，读取已成功解析的版本，按保存字节计算 `parseId`，固定 `parsedObjectKey`、原文件身份和 Java 确定的知识库 / 品牌 / 型号范围。浏览器只提交版本 ID 和 `legacy|structured` 规则，不接受路径或解析正文。
- 台账状态为 `BUILDING → READY|FAILED`，另有删除状态；每次重试创建新代次，不原地覆盖已完成代次。保存返回的完整构建清单，包括 Collection、Embedding 模型 / 维度、分块参数、数量和内容摘要。
- 新构建接口沿用已有索引请求超时，长耗时调用在数据库事务外执行。构建前落一条台账，完成后条件更新；进程中断留下未完成台账，允许重新构建，不增加新的后台任务框架或恢复状态机。
- 构建结束时版本已删除或解析结果已变化，不能标为 READY；清理该代次的迟到向量。新代次只产生候选，发布权仍在 Java，下一节接入。

### 2. 使用相同候选规则建立新索引

- 原规则仍可作为 `legacy` 对照；`structured` 使用 M2-1 的完整候选结果，保证预览与实际构建使用同一实现。先处理整个文档上下文，不能只索引第一页预览。
- 新 Collection 复用现有向量字段，并增加 `generation_id / parse_id / profile / provenance`。`provenance` 保存候选标识、章节路径、源块 / 页 / 行列 / 字符区间和型号范围依据。
- 新主键绑定代次和候选内容；写入只 upsert 本次代次，不执行按文档版本删除。写入并 flush 后以强一致性核对本代次数量，部分写入不能返回成功；删除按代次或文档版本限定。
- 不改 Embedding 模型和维度。已有新 Collection 的字段或维度不符时明确失败，不能借重建覆盖旧空间。后续检索只允许 Java 明确选择的代次和对应空间。

### 3. Java 接口与生命周期

- `GET /officecare/document/versions/{id}/generations`：查询权限，返回构建状态和清单，不返回存储路径。
- `POST /officecare/document/versions/{id}/generations?profile=structured`：查询与索引权限，构建前验证版本 / 知识库状态。使用已有内部 Token 调用 FastAPI。
- FastAPI 内部增加 `POST /api/v1/internal/documents/generations`、按代次删除、按文档版本删除；不把构建结果自动设为检索来源。
- 原有文档删除任务同步清理新空间中该版本的所有代次，成功后标记台账删除；失败沿用原任务重试。构建迟到也必须再次检查版本，以免删除后补回向量。
- 解析结果的读取与身份校验供预览和构建共用，避免两套口径。权限、范围、版本及原始文件信息仍以 Java 数据库为准。

## 内部步骤

1. 记录设计及授权，完成 Python 代次协议、结构候选复用和新空间存储。
2. 增加 SQL / Java 台账、共享解析读取与构建 / 查询接口，接入删除清理。
3. 在现有套件中补关键场景，运行相应回归；使用隔离 MySQL / Milvus 核对多代次、失败和删除。
4. 按本节边界自行验收，补结果并提交推送 GitHub，再开始发布与检索小节。

## 验证方式

- 核心行为：同版本两代次互不覆盖；结构化行和来源进入实际向量记录；预览分页不丢候选；部分写入失败不成为成功候选；未知 / 跨型号范围不被改成共享。
- Java：v1 解析兼容、权限入口、解析身份绑定、构建前后版本变化、台账状态与失败保留、删除后迟到结果清理。只补实际风险，不规定测试数量。
- 真实隔离资源：应用新表迁移到测试库；使用确定性测试 Embedding 在测试 Collection 写入两代次、核对独立数量、删除指定代次及整个版本。旧索引数量和现有正式检索路径保持不变。
- 不把未运行的端到端鉴权、真实供应商解析或回答评测写成通过；下一节完成发布 / 回退 / 旧版本隔离及 UI 的完整 M2 联调。

## 结果

- 2026-11-03：按用户授权完成设计、开发与本节验收，实现 commit `32f7815e71881a9f1291f982a1fc236e3432f143`，随验收文档交付 GitHub `main`。本节只建立候选，不切换正式检索；整个 M2 仍待发布 / 回退小节完成。
- 交付：共享解析身份读取、Java 构建台账 / 列表 / 构建 API、内部代次构建和清理 API、独立 `_generations` 空间、完整候选与行列 / 型号来源入库。台账固定知识库、品牌和型号；构建结束时解析键或范围变化不能完成。原文档删除任务同时清理新空间和台账。
- 测试：Python 全量 `python -m pytest -p pytest_asyncio.plugin -p no:cacheprovider --assert=plain -o addopts='' -q -s --tb=short`：**225 passed**；后续连接复用小改动后定向运行 `test_index_generations.py / test_document_structure_preview.py / test_vector_store.py`：**20 passed**。Java `mvn.cmd -o -pl ruoyi-admin -am test -DfailIfNoTests=false`：**112 tests，0 failures / errors，BUILD SUCCESS**。新增 Python 4 项、Java 3 项核心测试；未改前端，因此不重复前端测试与构建。
- 隔离 MySQL `officecare_ai_test`：执行新表迁移，并直接运行当前 Mapper 的完成 SQL；正常构建更新 1 行 READY，型号不符和解析键变化均更新 0 行、保持 BUILDING。测试业务行在事务末尾回滚，残留 0 行。
- 隔离 Milvus `officecare_m2_test`：确定性测试向量写入两代，各 **107** 个分块（超过预览第一页）；核对来源 JSON 和型号范围。删除一代 **107** 条，另一代仍 **107**；按版本再删 **107** 条。旧空间分块数前后均 **2**。测试创建的两个 Collection 已清理，没有触碰开发空间或真实手册向量。
- 限制与未测：未调用真实 Embedding / MinerU / LLM，不以模拟向量证明语义质量；Java 真实 HTTP 鉴权及完整前后端生命周期联调留到下一节。进程中断或调用超时可能留下未完成 / 失败台账及不可发布的向量，本节没有自动恢复或定时回收框架；正常返回的过期构建会清理迟到结果，文档删除沿用既有重试。物理清理与后续 Java 发布授权分别负责，不能据此宣称跨存储强事务。
- 源码：[候选复用](../../ai-service/app/services/document_preview.py)、[代次构建](../../ai-service/app/services/generation_indexing.py)、[新空间存储](../../ai-service/app/services/generation_store.py)、[Java 构建台账](../../java-server/ruoyi-officecare/src/main/java/com/ruoyi/officecare/service/impl/OcDocumentGenerationService.java)、[Mapper](../../java-server/ruoyi-officecare/src/main/resources/mapper/officecare/OcDocumentIndexGenerationMapper.xml)、[迁移](../../java-server/sql/officecare/020_officecare_document_index_generation.sql)。
