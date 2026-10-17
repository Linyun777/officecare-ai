# OfficeCare AI

OfficeCare AI 是一个面向办公设备售后场景的智能知识与服务系统。项目以打印机售后为首个演示领域，贯通知识文档处理、向量检索、可追溯问答、智能报修和人工确认工单。

## 核心能力

- 品牌、产品型号、知识库与文档版本管理
- PDF、Word、TXT 等文档解析和处理任务跟踪
- MinerU 文档解析、Embedding 与 Milvus 向量索引
- 基于 LangChain 的 RAG 问答、来源引用和证据不足拒答
- AI 多轮售后诊断、短期记忆与人工确认
- 客户单位、设备档案、售后工单和处理记录
- Redis 缓存、幂等控制、LangSmith 链路追踪
- RabbitMQ可靠异步任务、延迟重试、死信归档、人工重投与工作台指标
- 基于用户、角色、菜单和数据权限的后台管理

## 项目结构

```text
officecare-ai
├─ java-server   Spring Boot 3 后端和 OfficeCare 业务模块
├─ web-admin     Vue 3、Vite、Element Plus 管理前端
├─ ai-service    FastAPI、LangChain、RAG 与 Agent 服务
└─ deploy        基础设施和部署配置
```

## 技术栈

- Java 17、Spring Boot 3、Spring Security、MyBatis、MySQL、Redis
- Python、FastAPI、LangChain、LangGraph PostgreSQL Checkpointer
- MinerU、Qwen3-VL-Embedding、Milvus、DeepSeek、LangSmith
- Vue 3、Vite、Element Plus、Pinia

## 开源框架说明

Java 管理后端基于 RuoYi-Vue Spring Boot 3 分支进行二次开发，Vue 管理前端基于 RuoYi-Vue3。项目保留原框架许可证与必要的源码归属说明；OfficeCare 业务功能集中在 `ruoyi-officecare`、`web-admin/src/views/officecare` 和 `ai-service` 中。

## 配置原则

真实数据库密码、Redis密码和第三方 API Key 均通过本地环境变量提供，不应提交到 Git。各服务的示例配置只描述变量名和非敏感默认值。

本地开发与部署过程文档仅保留在开发环境，不随公开仓库发布。

## 容器化部署

生产式演示环境采用 Vue/Nginx、Spring Boot、FastAPI 与 RabbitMQ，复用现有 MySQL、Redis、Milvus 和 Agent PostgreSQL 基础设施。文档解析、索引和删索引通过 Transactional Outbox、持久化队列、手动 ACK、指数退避重试和死信队列异步执行；工作台可查看积压、失败、耗时与死信，并在排除故障后人工重投。部署过程文档仅保留在开发环境。
