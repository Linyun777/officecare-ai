-- OfficeCare AI多轮对话幂等与结构化响应持久化
-- 前置条件：已执行009_officecare_ai_conversation.sql。
-- 作用：为已有oc_ai_message补充单轮UUID和Agent响应快照。
-- 安全：不修改已有消息正文、引用、反馈或会话状态；脚本可重复执行。

set @officecare_schema := database();

-- ----------------------------
-- 1、客户端单轮幂等UUID
-- 历史消息保持NULL；MySQL唯一索引允许多条NULL记录。
-- ----------------------------
set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_ai_message'
       and column_name = 'turn_id') = 0,
    'alter table oc_ai_message add column turn_id char(36) default null comment ''客户端单轮幂等UUID'' after conversation_id',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

-- ----------------------------
-- 2、Agent结构化响应快照
-- 只由assistant消息写入，用于重复请求重放和页面刷新恢复。
-- ----------------------------
set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_ai_message'
       and column_name = 'agent_payload') = 0,
    'alter table oc_ai_message add column agent_payload json default null comment ''Agent结构化响应快照，仅assistant消息使用'' after trace_id',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

-- ----------------------------
-- 3、同一会话、同一Turn、同一角色只允许一条消息
-- ----------------------------
set @ddl := if(
    (select count(1) from information_schema.statistics
     where table_schema = @officecare_schema
       and table_name = 'oc_ai_message'
       and index_name = 'uk_oc_ai_message_turn_role') = 0,
    'alter table oc_ai_message add unique index uk_oc_ai_message_turn_role (conversation_id, turn_id, role)',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

-- ----------------------------
-- 4、只读验收
-- ----------------------------
select column_name, column_type, is_nullable, column_comment
from information_schema.columns
where table_schema = @officecare_schema
  and table_name = 'oc_ai_message'
  and column_name in ('turn_id', 'agent_payload')
order by ordinal_position;

select index_name, non_unique,
       group_concat(column_name order by seq_in_index) as indexed_columns
from information_schema.statistics
where table_schema = @officecare_schema
  and table_name = 'oc_ai_message'
  and index_name = 'uk_oc_ai_message_turn_role'
group by index_name, non_unique;
