# 阶段 03-01：RAG 跨型号证据治理

## 目标与用户看到的效果

- 选择 DCP-L2550DW 提问“拒绝访问”“禁用双面”时，回答不再引用属于 `DCP-7195DW/MFC-7895DW` 型号组的错误表格内容。
- 同一段证据对 `DCP-7195DW`、`MFC-7895DW` 仍然可用；共享说明、含当前型号的多型号列表、无型号声明的通用安全说明继续被召回。
- 检索范围仍由 Java 下发的知识库、品牌、型号和文档版本控制；不改变权限、接口和业务边界。

## 范围与不做

- 只修正 Chunk 自身的型号范围判断，以及对应回归测试与真实评测对照。
- 不引入 BM25、Reranker、新数据库；不修改 Milvus Schema；不重建或全量重索引向量。
- 不做表格行级拆分、索引 Metadata 扩展；单块多型号混合仅记录风险。
- 不改 Java、前端和部署配置。

## 设计

### 根因

v18 `chunk_index=1095` 正文首行为独立型号组表头 `DCP-7195DW/MFC-7895DW`。`is_applicable_to_model()` 只读取 `declared_model_codes()`，而该函数不识别正文中独立成行的型号组表头，返回空集后默认放行；`_has_conflicting_neighbor_scope()` 只检查前序相邻块，不拦截表头块自身。

### 实现

- `ai-service/app/services/retrieval_quality.py`：`is_applicable_to_model()` 的局部范围判断改用 `section_model_codes()`，使正文独立型号组表头参与过滤。命中范围时仍按“包含所选型号即放行”；未知型号和无法识别的声明不默认冲突。
- 保留其后 DCP/MFC/HL 家族校验逻辑，不改成对任意型号字符串做简单 `not in`。
- `ai-service/app/services/rag_answer_cache.py`：`RETRIEVAL_ALGORITHM_VERSION` 改为 `f10-section-scope-2026-10-17`，避免旧缓存继续命中错误跨型号回答。
- 过滤发生在 RRF 排名与回答生成之前，不通过删除 citation 事后掩盖。

### 已知残余风险

- 一个 Chunk 内同时包含多个型号组的行时，当前按“含所选型号即放行”处理，不做行级拆分；该风险在结果中登记，留待后续阶段。
- 本次只验证现有 v18 数据与 52 条评测集，不扩展到全量型号。

## 内部步骤

1. 修改 `retrieval_quality.py` 并更新缓存版本。
2. 调整 `tests/test_retrieval_quality.py`、`tests/test_rag_retrieval.py` 的旧预期，补充表头块自身被排除、目标型号放行、后续块仍被排除、多型号列表不误杀等用例。
3. 运行 Python 全量测试。
4. 在隔离测试集合与测试库上回归三个关键案例和 52 条评测，与修改前基线对照。

## 验证方式

- `cd ai-service; python -m pytest -q`。
- 测试 FastAPI（端口 8010）连接 `officecare_document_chunks_test_v1`、MySQL/PostgreSQL 测试库和 Redis DB15，运行 `notes/run_rag03_eval.py --phase after`。
- 三个关键案例人工核对最终回答引用的 `chunk_index`、页码与原文型号范围。
- 对照修改前后 KB1/KB2/KB3 的检索通过数、Hit@5、MRR、noise、问答通过数与 P95。

## 结果

### 交付内容与位置

- 现有代码将 `is_applicable_to_model()` 的局部范围判断改为 `section_model_codes()`，保留 DCP/MFC/HL 家族校验；缓存版本为 `f10-section-scope-2026-10-17`。
- 代码及其回归测试保留在独立 worktree `feat/03-rag-quality`，基于 `0e675ff`，尚未提交或合并。本轮仅复核已有改动，没有扩展开发范围。
- 相关源码入口：[型号过滤](../../ai-service/app/services/retrieval_quality.py)、[缓存版本](../../ai-service/app/services/rag_answer_cache.py)、[过滤单测](../../ai-service/tests/test_retrieval_quality.py)、[检索回归](../../ai-service/tests/test_rag_retrieval.py)。本页描述的是上述 worktree 的待验收实现，GitHub `main` 代码仍为 `0e675ff` 中的实现；基线文档提交为 `b8a7772`。
- 未改依赖、锁文件、数据库迁移、Milvus Schema 或持久配置，未重建索引。

### 实际运行的本地自检

- 2026-10-24 使用主仓库已有 Python 虚拟环境，在 RAG worktree 的 `ai-service` 执行 `python -m pytest -p pytest_asyncio.plugin -p no:cacheprovider --assert=plain -o addopts='' -q -s`：**163 passed in 1.51s**。
- 本次关闭插件自动加载与追踪，禁用字节码和 pytest 缓存写入，临时目录使用主仓库忽略的 `notes/rag03-temp`。这些单测没有调用真实模型或真实数据库。
- 历史记录（2026-10-17）：全量 163 passed（修改前 159）；测试覆盖表头块自身排除、目标型号放行、后续块排除、多型号列表保留、耗材编号及故障码不作为型号范围。

### 2026-10-24 真实评测范围

- 用户明确授权将现有 52 条打印机测试问题及对应测试手册检索片段，发送给 SiliconFlow Embedding 与 DeepSeek 回答服务。
- 只读查询 `officecare_ai_test` 中 Java 管理的当前启用文档版本，再将本轮固定范围限制为 KB1 `[13,14,15,17,18]`、KB2 `[19]`、KB3 `[20]`；KB1 另有启用版本 9001，本轮未纳入、未修改。
- 使用隔离集合 `officecare_document_chunks_test_v1`；各知识库抽查向量的模型和维度均匹配当前 Embedding 配置。模型为 `Qwen/Qwen3-VL-Embedding-8B` 与 `deepseek-v4-pro`。
- 直接调用 RAG 检索及回答服务并绕过缓存，没有启动 Java/FastAPI HTTP 服务，没有连接 Redis 或 PostgreSQL。结果不代表 Java 鉴权、HTTP 链路、多轮 Agent 或缓存验收；P95 不与旧 HTTP 基线作性能结论。
- 一次性脚本位于基线 worktree 的 `notes/rag03_readonly_eval.py`；原始输出位于主仓库 `notes/rag03-2026-10-24/`，关闭追踪的复核位于 `notes/rag03-2026-10-24-final/`，单例诊断位于后缀 `-diagnostic` 目录。脚本和原始评测文件均不入 Git。

### 完整首轮结果与历史对照

2026-10-17 修改前基线为检索 **48/52**、问答 **52/52**。2026-10-24 完整首轮实际结果如下；通过数沿用现有评测集的关键词与引用检查规则，不等同于人工语义正确率。

| 知识库 | 检索通过 | 问答通过 | 检索 Hit@5 / MRR | 检索 noise | 问答 P95 |
|---|---|---|---|---|---|
| KB1：DCP-L2550DW | 24/28 | 26/28 | 1.0 / 0.917 | 0.49 | 4131.920ms |
| KB2：DCP-L2535DW | 12/12 | 12/12 | 1.0 / 1.0 | 0.50 | 4526.931ms |
| KB3：MFC-7895DW | 12/12 | 12/12 | 1.0 / 1.0 | 0.571 | 3150.322ms |
| 合计 | **48/52** | **50/52** | — | — | — |

- 完整首轮业务请求错误为 0；19 条预期拒答全部通过。检索未通过的仍为 KB1 的电路板维修、天气、价格、Prompt 注入四条负例：检索返回了资料，回答层仍拒答。
- KB1 所有返回证据及回答引用中，v18 `chunk_index=1095/1096` 的出现次数为 0。
- 首轮追踪组件额外尝试向 LangSmith 上报并收到 HTTP 401，因此该轮只保留为完整实测记录，不作为关闭追踪后的干净验收报告。
- 后续已在评测进程中同时关闭 OfficeCare 与 LangSmith 追踪，预检查确认关闭，复核运行未再出现追踪上报错误；KB1 检索为 24/28、问答为 17/28，其中 **9 条 `CHAT_MODEL_UNAVAILABLE`**，另有与首轮相同的两个质量失败。该轮在 KB1 后停止，KB2/KB3 未继续运行，不能补写成完整 52 条通过。
- 对 `jam.internal` 单例诊断得到异常链 `APIConnectionError → ConnectError → ConnectError`，没有 HTTP 状态码；这说明复核受模型连接错误影响，尚不能确认其网络根因。

### 三个关键案例的证据核对

| 案例 | 真实结果 | 引用与结论 |
|---|---|---|
| `error.memory-full` | PASS | 引用 v18 第 378 页 `1092`，以及第 155/145 页 `518/477`；目标型号组续表保留，没有引用 `1095/1096`。 |
| `error.access-denied` | 现有评测规则 FAIL | 回答 `INSUFFICIENT_EVIDENCE`，无引用。手册中“拒绝访问 / 安全功能锁 3.0”的错误表位于第 380 页 `1095`，其表头为其他型号组；不能恢复跨型号引用来满足旧答案。尚未找到 DCP-L2550DW 的同义故障证据。 |
| `error.duplex-disabled` | FAIL | 引用第 490/495 页 `1376/1387` 的双面菜单，回答检查双面设置，遗漏后盖及双面单元。目标型号对应说明实际在第 378 页 `1091`，故障名称为“不能双面打印”；当前问句“禁用双面”未召回它。 |

- 手册范围核对：`1091` 明确列出 `DCP-7090DW/DCP-L2535DW/DCP-L2550DW/DCP-7190DW`，包含后盖未合上、双面单元未安装的处理；`1095` 明确属于 `DCP-7195DW/MFC-7895DW`，包含“禁用双面”和“拒绝访问”。
- 现有评测的 `containsAny` 关键词可将“启用或禁用双面打印”的菜单判成检索命中，却不能证明它回答了故障处理问题；因此 Hit@5 为 1.0 仍可能存在语义错误。跨型号旧答案与检索同义词问题分开保留，未修改评测预期来增加通过数。

### 阶段结论与遗留

- 本地单测：PASS。关键跨型号块排除：实测符合预期。阶段整体仍为 **开发中、未验收**；真实质量检查未通过，关闭追踪后的完整稳定评测也未完成。
- 待用户确认后续范围：按型号核实评测问题和标准答案；为“不能双面打印 / 禁用双面”等故障语义补足检索与正确引用验证；核实模型连接稳定性后补跑完整评测。以上本轮仅记录，不提前开发。
- 单块多型号混合、缺少表头的续块、四条负例检索噪声仍有风险；未实施表格行级拆分、BM25、Reranker 或全量重索引。
- 阶段 02 仍待用户验收，本轮评测不替代此前阶段的验收。
