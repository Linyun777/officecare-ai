-- OfficeCare AI 文档任务可靠消息Outbox
-- 作用：保证文档任务记录与待发送RabbitMQ事件在同一MySQL事务中落库。
-- 安全：只新增表，不修改或删除现有文档、任务与索引数据。

create table if not exists oc_outbox_event
(
    outbox_id           bigint         not null auto_increment comment 'Outbox主键',
    event_id            varchar(64)    not null comment '消息事件唯一ID',
    dedup_key           varchar(160)   not null comment '业务去重键：任务ID+执行轮次',
    task_id             bigint         not null comment '文档任务ID',
    document_version_id bigint         not null comment '文档版本ID',
    task_type           varchar(20)    not null comment 'parse/index/delete_index',
    task_attempt        int            not null default 0 comment '创建消息时的任务已执行次数',
    routing_key         varchar(100)   not null comment 'RabbitMQ路由键',
    payload_json        json           not null comment '待发送消息JSON',
    trace_id            varchar(128)   not null comment '跨服务追踪ID',
    status              char(1)        not null default '0' comment '0待发布 1发布中 2已发送 3待重试 4发布失败',
    retry_count         int            not null default 0 comment '消息发布重试次数',
    max_retry_count     int            not null default 10 comment '消息最大发布次数',
    next_retry_time     datetime       not null comment '下次允许发布时间',
    last_error          varchar(500)   null comment '最近发布错误',
    sent_time           datetime       null comment 'RabbitMQ确认时间',
    create_time         datetime       not null default current_timestamp comment '创建时间',
    update_time         datetime       null comment '更新时间',
    primary key (outbox_id),
    unique key uk_oc_outbox_event_id (event_id),
    unique key uk_oc_outbox_dedup_key (dedup_key),
    key idx_oc_outbox_ready (status, next_retry_time, outbox_id),
    key idx_oc_outbox_task (task_id)
) engine=InnoDB comment='OfficeCare文档任务可靠消息Outbox';

-- 验收查询：新环境初次执行应返回0行，提交文档任务后会出现Outbox记录。
select outbox_id, event_id, task_id, document_version_id, task_type,
       task_attempt, routing_key, status, retry_count, max_retry_count,
       next_retry_time, sent_time, create_time, update_time
from oc_outbox_event
order by outbox_id desc
limit 20;
