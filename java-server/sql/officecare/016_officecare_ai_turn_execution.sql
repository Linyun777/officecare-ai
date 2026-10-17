-- OfficeCare AI单轮执行状态
-- 前置条件：已执行009_officecare_ai_conversation.sql和015_officecare_ai_turn_idempotency.sql。
-- 作用：在调用Agent前原子占用同一会话的同一turnId，保存执行状态和结果补偿快照。
-- 安全：新增独立表，不修改已有会话、消息、引用和向量数据；脚本可重复执行。

create table if not exists oc_ai_turn_execution
(
    execution_id         bigint       not null auto_increment comment '单轮执行ID',
    conversation_id      bigint       not null                comment '会话ID',
    turn_id              char(36)     not null                comment '客户端单轮幂等UUID',
    user_id              bigint       not null                comment '若依用户ID',
    question_hash        char(64)     not null                comment '问题SHA-256，用于冲突检测',
    status               varchar(16)  not null                comment '状态（PROCESSING/READY/PERSISTING/SUCCEEDED/FAILED）',
    owner_token          char(36)     not null                comment '当前执行者令牌',
    attempt_count        int          not null default 1      comment '执行次数',
    lease_until          datetime     not null                comment '执行租约到期时间',
    trace_id             varchar(128) default null            comment '内部调用Trace ID',
    result_payload       json         default null            comment 'Agent结构化结果，用于落库失败后的补偿',
    user_message_id      bigint       default null            comment '落库后的用户消息ID',
    assistant_message_id bigint       default null            comment '落库后的助手消息ID',
    error_code           varchar(64)  default null            comment '最近一次失败错误码',
    error_message        varchar(500) default null            comment '最近一次失败说明',
    create_time          datetime     default null            comment '创建时间',
    update_time          datetime     default null            comment '更新时间',
    primary key (execution_id),
    unique key uk_oc_ai_turn_execution (conversation_id, turn_id),
    key idx_oc_ai_turn_execution_status (status, lease_until)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare AI单轮执行状态表';

select table_name, table_comment
from information_schema.tables
where table_schema = database()
  and table_name = 'oc_ai_turn_execution';