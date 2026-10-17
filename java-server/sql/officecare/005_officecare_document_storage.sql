-- OfficeCare AI 知识文档存储初始化
-- 前置条件：已执行004_officecare_knowledge_base.sql。
-- 作用：创建文档、文档版本表，以及文档管理菜单和角色权限。
-- 注意：数据库只保存元数据；原始文件保存在OFFICECARE_STORAGE_ROOT指定的外部目录。

-- ----------------------------
-- 1、文档元数据表
-- ----------------------------
create table if not exists oc_document
(
    document_id         bigint       not null auto_increment comment '文档ID',
    knowledge_base_id   bigint       not null                comment '所属知识库ID',
    document_title      varchar(200) not null                comment '文档标题',
    current_version_id  bigint       default null            comment '当前版本ID',
    status              char(1)      not null default '0'    comment '状态（0正常 1停用）',
    del_flag            char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by           varchar(64)  default ''              comment '创建者',
    create_time         datetime     default null            comment '创建时间',
    update_by           varchar(64)  default ''              comment '更新者',
    update_time         datetime     default null            comment '更新时间',
    remark              varchar(500) default null            comment '备注',
    primary key (document_id),
    key idx_oc_document_kb (knowledge_base_id, status, del_flag),
    key idx_oc_document_current_version (current_version_id),
    key idx_oc_document_title (document_title)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='OfficeCare知识文档表';

create table if not exists oc_document_version
(
    document_version_id  bigint       not null auto_increment comment '文档版本ID',
    document_id          bigint       not null                comment '文档ID',
    version_no           int          not null                comment '版本号',
    storage_type         varchar(20)  not null                comment '存储类型（local/oss）',
    bucket_name          varchar(100) default null            comment '对象存储Bucket，本地存储为空',
    object_key           varchar(500) not null                comment '存储对象键',
    original_file_name   varchar(255) not null                comment '原始文件名',
    file_extension       varchar(20)  not null                comment '文件扩展名',
    content_type         varchar(100) not null                comment 'MIME类型',
    file_size            bigint       not null                comment '文件大小（字节）',
    file_hash            char(64)     not null                comment '文件SHA-256摘要',
    parsed_object_key    varchar(500) default null            comment '解析结果对象键',
    page_count           int          default null            comment '文档页数',
    process_status       char(1)      not null default '0'    comment '处理状态（0待处理 1处理中 2成功 3失败）',
    process_message      varchar(500) default null            comment '处理结果说明',
    del_flag             char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by            varchar(64)  default ''              comment '创建者',
    create_time          datetime     default null            comment '创建时间',
    primary key (document_version_id),
    unique key uk_oc_document_version_no (document_id, version_no),
    unique key uk_oc_document_object_key (object_key),
    key idx_oc_document_version_hash (document_id, file_hash, del_flag),
    key idx_oc_document_process (process_status, del_flag)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='OfficeCare文档版本表';

-- ----------------------------
-- 2、文档管理菜单
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

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '文档管理', @oc_knowledge_menu_id, 2, 'document',
    'officecare/document/index', '', 'OfficeCareDocument', 1, 0,
    'C', '0', '0', 'officecare:document:list', 'upload-filled', 'admin', sysdate(),
    'OfficeCare知识文档管理'
where @oc_knowledge_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_knowledge_menu_id and path = 'document'
  );

set @oc_document_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_knowledge_menu_id and path = 'document'
    order by menu_id
    limit 1
);

-- ----------------------------
-- 3、文档按钮权限
-- ----------------------------
insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '文档查询', @oc_document_menu_id, 1, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:document:query', '#', 'admin', sysdate(), '文档查询权限'
where @oc_document_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_document_menu_id and perms = 'officecare:document:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '文档上传', @oc_document_menu_id, 2, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:document:upload', '#', 'admin', sysdate(), '文档上传权限'
where @oc_document_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_document_menu_id and perms = 'officecare:document:upload'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '文档修改', @oc_document_menu_id, 3, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:document:edit', '#', 'admin', sysdate(), '文档修改权限'
where @oc_document_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_document_menu_id and perms = 'officecare:document:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '文档删除', @oc_document_menu_id, 4, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:document:remove', '#', 'admin', sysdate(), '文档删除权限'
where @oc_document_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_document_menu_id and perms = 'officecare:document:remove'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '文档下载', @oc_document_menu_id, 5, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:document:download', '#', 'admin', sysdate(), '文档下载权限'
where @oc_document_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_document_menu_id and perms = 'officecare:document:download'
  );

-- ----------------------------
-- 4、角色菜单授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (@oc_root_menu_id, @oc_knowledge_menu_id, @oc_document_menu_id)
  or m.perms in (
      'officecare:document:query', 'officecare:document:upload',
      'officecare:document:edit', 'officecare:document:remove',
      'officecare:document:download'
  )
where r.role_key = 'oc_knowledge'
  and r.del_flag = '0';

insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (@oc_root_menu_id, @oc_knowledge_menu_id, @oc_document_menu_id)
  or m.perms in ('officecare:document:query', 'officecare:document:download')
where r.role_key = 'oc_service'
  and r.del_flag = '0';
