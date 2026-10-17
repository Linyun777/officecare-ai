-- OfficeCare AI 文档解析任务初始化
-- 前置条件：已执行005_officecare_document_storage.sql。
-- 作用：创建文档解析任务表、补齐既有文档任务，并增加解析操作权限。
-- 注意：本脚本只创建任务元数据，不调用FastAPI，不修改或删除原始文件。

-- ----------------------------
-- 1、文档解析任务表
-- ----------------------------
create table if not exists oc_document_task
(
    task_id              bigint       not null auto_increment comment '任务ID',
    document_id          bigint       not null                comment '文档ID',
    document_version_id  bigint       not null                comment '文档版本ID',
    task_type            varchar(20)  not null default 'parse' comment '任务类型（parse解析）',
    status               char(1)      not null default '0'    comment '任务状态（0待处理 1处理中 2成功 3失败）',
    attempt_count        int          not null default 0      comment '已执行次数',
    max_attempts         int          not null default 3      comment '最大执行次数',
    trace_id             varchar(128) not null                comment '跨服务追踪ID',
    request_file_hash    char(64)     not null                comment '请求文件SHA-256',
    result_file_hash     char(64)     default null            comment 'FastAPI返回文件SHA-256',
    page_count           int          default null            comment '解析页数，仅PDF可靠',
    block_count          int          default null            comment '解析文本块数量',
    character_count      int          default null            comment '解析字符数量',
    error_code           varchar(64)  default null            comment '最近错误码',
    error_message        varchar(500) default null            comment '最近错误说明',
    started_time         datetime     default null            comment '最近开始时间',
    finished_time        datetime     default null            comment '最近完成时间',
    create_by            varchar(64)  default ''              comment '创建者',
    create_time          datetime     default null            comment '创建时间',
    update_by            varchar(64)  default ''              comment '更新者',
    update_time          datetime     default null            comment '更新时间',
    primary key (task_id),
    unique key uk_oc_document_task_version_type (document_version_id, task_type),
    key idx_oc_document_task_document (document_id, create_time),
    key idx_oc_document_task_status (status, update_time),
    key idx_oc_document_task_trace (trace_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='OfficeCare文档处理任务表';

-- 为已有且受FastAPI支持的版本补齐待处理任务。
-- insert ignore保证重复执行脚本不会创建重复任务。
insert ignore into oc_document_task
(
    document_id, document_version_id, task_type, status,
    attempt_count, max_attempts, trace_id, request_file_hash,
    create_by, create_time
)
select
    v.document_id,
    v.document_version_id,
    'parse',
    case when v.process_status in ('0', '1') then '0' else v.process_status end,
    0,
    3,
    concat('migration-', v.document_version_id),
    v.file_hash,
    coalesce(v.create_by, 'admin'),
    coalesce(v.create_time, sysdate())
from oc_document_version v
where v.del_flag = '0'
  and v.file_extension in ('pdf', 'docx', 'txt');

-- ----------------------------
-- 2、文档解析按钮权限
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
    '文档解析', @oc_document_menu_id, 6, '#', '', '', '',
    1, 0, 'F', '0', '0', 'officecare:document:parse', '#',
    'admin', sysdate(), '启动或重试文档解析'
where @oc_document_menu_id is not null
  and not exists (
      select 1
      from sys_menu
      where parent_id = @oc_document_menu_id
        and perms = 'officecare:document:parse'
  );

-- 知识管理员可以启动和重试解析；售后人员继续保持只读。
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.parent_id = @oc_document_menu_id
 and m.perms = 'officecare:document:parse'
where r.role_key = 'oc_knowledge'
  and r.del_flag = '0';

