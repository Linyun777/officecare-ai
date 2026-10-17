-- OfficeCare AI 会话、消息、引用与反馈初始化
-- 前置条件：已执行001至008脚本。
-- 作用：持久化当前用户的AI问答历史和引用，不修改知识库文档及Milvus数据。
-- 特性：脚本可重复执行；不建立数据库外键，由Java服务维护业务归属。

-- ----------------------------
-- 1、AI会话
-- ----------------------------
create table if not exists oc_ai_conversation
(
    conversation_id   bigint       not null auto_increment comment '会话ID',
    knowledge_base_id bigint       not null                comment '知识库ID',
    user_id           bigint       not null                comment '若依用户ID',
    title             varchar(120) not null                comment '会话标题',
    status            char(1)      not null default '0'    comment '状态（0正常 1归档）',
    last_message_time datetime     default null            comment '最后消息时间',
    del_flag          char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by         varchar(64)  default ''              comment '创建者',
    create_time       datetime     default null            comment '创建时间',
    update_by         varchar(64)  default ''              comment '更新者',
    update_time       datetime     default null            comment '更新时间',
    primary key (conversation_id),
    key idx_oc_ai_conversation_user
        (user_id, status, del_flag, last_message_time),
    key idx_oc_ai_conversation_kb (knowledge_base_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare AI会话表';

-- ----------------------------
-- 2、用户与AI消息
-- ----------------------------
create table if not exists oc_ai_message
(
    message_id        bigint       not null auto_increment comment '消息ID',
    conversation_id   bigint       not null                comment '会话ID',
    turn_id           char(36)     default null            comment '客户端单轮幂等UUID',
    role              varchar(16)  not null                comment '角色（user/assistant）',
    content           text         not null                comment '消息正文',
    answered          tinyint(1)   default null            comment '是否基于证据回答',
    refusal_reason    varchar(64)  default null            comment '拒答原因',
    model_name        varchar(128) default null            comment '生成模型',
    trace_id          varchar(128) default null            comment '内部调用Trace ID',
    agent_payload     json         default null            comment 'Agent结构化响应快照，仅assistant消息使用',
    feedback_type     char(1)      not null default '0'    comment '反馈（0无 1有帮助 2无帮助）',
    feedback_comment  varchar(500) default null            comment '反馈说明',
    create_by         varchar(64)  default ''              comment '创建者',
    create_time       datetime     default null            comment '创建时间',
    update_by         varchar(64)  default ''              comment '更新者',
    update_time       datetime     default null            comment '更新时间',
    primary key (message_id),
    unique key uk_oc_ai_message_turn_role
        (conversation_id, turn_id, role),
    key idx_oc_ai_message_conversation (conversation_id, message_id),
    key idx_oc_ai_message_feedback (feedback_type, create_time)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare AI消息表';

-- ----------------------------
-- 3、AI回答引用快照
-- ----------------------------
create table if not exists oc_ai_message_citation
(
    citation_id          bigint        not null auto_increment comment '引用ID',
    message_id           bigint        not null                comment 'AI消息ID',
    citation_index       int           not null                comment '回答中的资料序号',
    knowledge_base_id    bigint        not null                comment '知识库ID',
    brand_id             bigint        not null                comment '品牌ID',
    model_id             bigint        not null                comment '产品型号ID',
    document_id          bigint        not null                comment '文档ID',
    document_version_id  bigint        not null                comment '文档版本ID',
    document_title       varchar(200)  not null                comment '文档标题快照',
    original_file_name   varchar(255)  not null                comment '原文件名快照',
    page_number          int           default null            comment '原文页码',
    chunk_index          int           not null                comment '版本内切片序号',
    heading              varchar(1000) default null            comment '切片标题',
    quote_text           text          not null                comment '引用原文快照',
    score                decimal(10,6) not null                comment '综合相关度',
    create_time          datetime      default null            comment '创建时间',
    primary key (citation_id),
    unique key uk_oc_ai_message_citation (message_id, citation_index),
    key idx_oc_ai_citation_document (document_version_id, page_number)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare AI消息引用表';

-- ----------------------------
-- 4、结构验收
-- ----------------------------
select table_name, table_comment
from information_schema.tables
where table_schema = database()
  and table_name in (
      'oc_ai_conversation',
      'oc_ai_message',
      'oc_ai_message_citation'
  )
order by table_name;
