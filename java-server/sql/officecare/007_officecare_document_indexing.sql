-- OfficeCare AI 文档向量索引初始化
-- 前置条件：已执行006_officecare_document_processing.sql。
-- 作用：增加文档版本索引状态、补齐索引任务，并增加索引操作权限。
-- 注意：本脚本只修改MySQL元数据，不连接Milvus、不写向量、不删除文档。

-- ----------------------------
-- 1、文档版本索引状态
-- MySQL 8.0不支持ADD COLUMN IF NOT EXISTS，使用information_schema保证可重复执行。
-- ----------------------------
set @officecare_schema := database();

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'index_status') = 0,
    'alter table oc_document_version add column index_status char(1) not null default ''0'' comment ''索引状态（0待处理 1处理中 2成功 3失败）'' after process_message',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'index_message') = 0,
    'alter table oc_document_version add column index_message varchar(500) default null comment ''最近索引结果说明'' after index_status',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'index_content_hash') = 0,
    'alter table oc_document_version add column index_content_hash char(64) default null comment ''文本切片与模型配置摘要'' after index_message',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'indexed_chunk_count') = 0,
    'alter table oc_document_version add column indexed_chunk_count int default null comment ''已写入Milvus的文本切片数'' after index_content_hash',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'embedding_model') = 0,
    'alter table oc_document_version add column embedding_model varchar(128) default null comment ''Embedding模型名称'' after indexed_chunk_count',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'embedding_dimension') = 0,
    'alter table oc_document_version add column embedding_dimension int default null comment ''Embedding向量维度'' after embedding_model',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'milvus_collection') = 0,
    'alter table oc_document_version add column milvus_collection varchar(128) default null comment ''Milvus Collection名称'' after embedding_dimension',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.columns
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and column_name = 'indexed_time') = 0,
    'alter table oc_document_version add column indexed_time datetime default null comment ''最近索引成功时间'' after milvus_collection',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

set @ddl := if(
    (select count(1) from information_schema.statistics
     where table_schema = @officecare_schema
       and table_name = 'oc_document_version'
       and index_name = 'idx_oc_document_index') = 0,
    'alter table oc_document_version add index idx_oc_document_index (index_status, del_flag)',
    'select 1'
);
prepare officecare_stmt from @ddl;
execute officecare_stmt;
deallocate prepare officecare_stmt;

alter table oc_document_task
    modify task_type varchar(20) not null default 'parse'
        comment '任务类型（parse解析 index索引 delete_index删除索引）';

update oc_document_version
set index_message = case
    when process_status = '2' then '等待文档向量化'
    else '等待文档解析完成'
end
where del_flag = '0'
  and index_message is null;

-- ----------------------------
-- 2、为已解析版本补齐索引任务
-- ----------------------------
insert ignore into oc_document_task
(
    document_id, document_version_id, task_type, status,
    attempt_count, max_attempts, trace_id, request_file_hash,
    create_by, create_time
)
select
    v.document_id,
    v.document_version_id,
    'index',
    '0',
    0,
    3,
    concat('index-migration-', v.document_version_id),
    v.file_hash,
    coalesce(v.create_by, 'admin'),
    coalesce(v.create_time, sysdate())
from oc_document_version v
where v.del_flag = '0'
  and v.process_status = '2'
  and v.parsed_object_key is not null;

-- ----------------------------
-- 3、文档索引按钮权限
-- ----------------------------
set @oc_root_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = 0 and path = 'officecare'
    order by menu_id
    limit 1
);

set @oc_knowledge_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_root_menu_id and path = 'knowledge'
    order by menu_id
    limit 1
);

set @oc_document_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_knowledge_menu_id and path = 'document'
    order by menu_id
    limit 1
);

insert into sys_menu
(
    menu_name, parent_id, order_num, path, component, query, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
select
    '文档索引', @oc_document_menu_id, 7, '#', '', '', '',
    1, 0, 'F', '0', '0', 'officecare:document:index', '#',
    'admin', sysdate(), '启动或重试文档向量索引'
where @oc_document_menu_id is not null
  and not exists (
      select 1
      from sys_menu
      where parent_id = @oc_document_menu_id
        and perms = 'officecare:document:index'
  );

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.parent_id = @oc_document_menu_id
 and m.perms = 'officecare:document:index'
where r.role_key = 'oc_knowledge'
  and r.del_flag = '0';
