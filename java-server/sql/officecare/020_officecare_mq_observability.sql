-- OfficeCare AI RabbitMQ消费审计与人工重投
-- 前置条件：已执行019_officecare_document_task_outbox.sql。
-- 作用：在Outbox事件上记录等待/处理耗时、消费结果、死信归档和人工重投信息。
-- 安全：仅增加字段与索引，不删除任务、文档、Outbox事件或RabbitMQ数据；可重复执行。

set @officecare_schema := database();

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'consume_status') = 0,
    'alter table oc_outbox_event add column consume_status char(1) not null default ''0'' comment ''消费状态：0待消费 1处理中 2成功 3已安排重试 4死信 5忽略 6已人工重投'' after sent_time',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'consume_error') = 0,
    'alter table oc_outbox_event add column consume_error varchar(500) default null comment ''最近消费错误'' after consume_status',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'consumed_time') = 0,
    'alter table oc_outbox_event add column consumed_time datetime default null comment ''首次开始消费时间'' after consume_error',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'completed_time') = 0,
    'alter table oc_outbox_event add column completed_time datetime default null comment ''消费完成时间'' after consumed_time',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'wait_time_ms') = 0,
    'alter table oc_outbox_event add column wait_time_ms bigint default null comment ''Rabbit确认至开始消费等待毫秒数'' after completed_time',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'process_time_ms') = 0,
    'alter table oc_outbox_event add column process_time_ms bigint default null comment ''消息消费处理毫秒数'' after wait_time_ms',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'redelivery_count') = 0,
    'alter table oc_outbox_event add column redelivery_count int not null default 0 comment ''RabbitMQ重新投递次数'' after process_time_ms',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'dead_letter_time') = 0,
    'alter table oc_outbox_event add column dead_letter_time datetime default null comment ''死信被Java归档时间'' after redelivery_count',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'replayed_time') = 0,
    'alter table oc_outbox_event add column replayed_time datetime default null comment ''人工重新投递时间'' after dead_letter_time',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and column_name = 'replayed_by') = 0,
    'alter table oc_outbox_event add column replayed_by varchar(64) default null comment ''人工重新投递操作人'' after replayed_time',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.statistics
     where table_schema = @officecare_schema
       and table_name = 'oc_outbox_event'
       and index_name = 'idx_oc_outbox_consume') = 0,
    'alter table oc_outbox_event add index idx_oc_outbox_consume (consume_status, replayed_time, outbox_id)',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

-- 只读验收
select column_name, column_type, is_nullable, column_default, column_comment
from information_schema.columns
where table_schema = @officecare_schema
  and table_name = 'oc_outbox_event'
  and column_name in (
      'consume_status', 'consume_error', 'consumed_time', 'completed_time',
      'wait_time_ms', 'process_time_ms', 'redelivery_count',
      'dead_letter_time', 'replayed_time', 'replayed_by'
  )
order by ordinal_position;

select index_name, non_unique,
       group_concat(column_name order by seq_in_index) as indexed_columns
from information_schema.statistics
where table_schema = @officecare_schema
  and table_name = 'oc_outbox_event'
  and index_name = 'idx_oc_outbox_consume'
group by index_name, non_unique;
