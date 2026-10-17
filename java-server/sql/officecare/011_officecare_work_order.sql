-- OfficeCare AI 最小售后工单闭环初始化
-- 前置条件：已执行001至010脚本。
-- 作用：创建售后工单、处理记录表，并补充菜单与售后人员权限。
-- 特性：脚本可重复执行；不执行历史数据迁移，不修改AI会话或Milvus数据。

-- ----------------------------
-- 1、售后工单
-- 状态：0待处理 1处理中 2已完成 3已关闭
-- 优先级：0低 1中 2高 3紧急
-- ----------------------------
create table if not exists oc_work_order
(
    work_order_id      bigint        not null auto_increment comment '工单ID',
    work_order_no      varchar(32)   not null                comment '工单编号',
    customer_org_id    bigint        not null                comment '客户单位ID',
    device_id          bigint        not null                comment '设备ID',
    title              varchar(150)  not null                comment '工单标题',
    fault_description  varchar(2000) not null                comment '故障描述',
    priority           char(1)       not null default '1'    comment '优先级（0低 1中 2高 3紧急）',
    status             char(1)       not null default '0'    comment '状态（0待处理 1处理中 2已完成 3已关闭）',
    assignee_user_id   bigint        default null            comment '负责人用户ID',
    reporter_user_id   bigint        not null                comment '报修登记用户ID',
    completion_time    datetime      default null            comment '完成时间',
    close_time         datetime      default null            comment '关闭时间',
    del_flag           char(1)       not null default '0'    comment '删除标志（0存在 2删除）',
    create_by          varchar(64)   default ''              comment '创建者',
    create_time        datetime      default null            comment '创建时间',
    update_by          varchar(64)   default ''              comment '更新者',
    update_time        datetime      default null            comment '更新时间',
    remark             varchar(500)  default null            comment '备注',
    primary key (work_order_id),
    unique key uk_oc_work_order_no (work_order_no),
    key idx_oc_work_order_customer (customer_org_id, status, del_flag),
    key idx_oc_work_order_device (device_id, status, del_flag),
    key idx_oc_work_order_assignee (assignee_user_id, status, del_flag),
    key idx_oc_work_order_created (create_time)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare售后工单表';

-- ----------------------------
-- 2、工单处理记录
-- 记录采用追加方式，保留创建、修改、指派、状态流转和处理备注时间线。
-- operator_name为操作时账号快照，避免账号改名后历史记录失真。
-- ----------------------------
create table if not exists oc_work_order_record
(
    record_id        bigint        not null auto_increment comment '处理记录ID',
    work_order_id    bigint        not null                comment '工单ID',
    action_type      varchar(20)   not null                comment '动作类型（CREATE/EDIT/ASSIGN/STATUS/COMMENT）',
    from_status      char(1)       default null            comment '变更前状态',
    to_status        char(1)       default null            comment '变更后状态',
    content          varchar(1000) not null                comment '处理内容',
    operator_user_id bigint        not null                comment '操作用户ID',
    operator_name    varchar(64)   not null                comment '操作账号快照',
    create_time      datetime      not null                comment '创建时间',
    primary key (record_id),
    key idx_oc_work_order_record_order (work_order_id, create_time),
    key idx_oc_work_order_record_operator (operator_user_id, create_time)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare工单处理记录表';

-- ----------------------------
-- 3、售后工单菜单
-- ----------------------------
set @oc_root_menu_id := (
    select menu_id from sys_menu
    where parent_id = 0 and path = 'officecare'
    order by menu_id limit 1
);

set @oc_service_menu_id := (
    select menu_id from sys_menu
    where parent_id = @oc_root_menu_id and path = 'service'
    order by menu_id limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select
    '售后工单', @oc_service_menu_id, 3, 'work-order',
    'officecare/workOrder/index', '', 'OfficeCareWorkOrder', 1, 0,
    'C', '0', '0', 'officecare:workorder:list', 'list',
    'admin', sysdate(), 'OfficeCare售后工单闭环'
where @oc_service_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_service_menu_id and path = 'work-order'
  );

set @oc_work_order_menu_id := (
    select menu_id from sys_menu
    where parent_id = @oc_service_menu_id and path = 'work-order'
    order by menu_id limit 1
);

-- ----------------------------
-- 4、工单按钮权限
-- ----------------------------
insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '工单查询', @oc_work_order_menu_id, 1, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:query', '#',
       'admin', sysdate(), '售后工单查询权限'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '工单新增', @oc_work_order_menu_id, 2, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:add', '#',
       'admin', sysdate(), '售后工单新增权限'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:add'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '工单修改', @oc_work_order_menu_id, 3, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:edit', '#',
       'admin', sysdate(), '售后工单修改权限'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '工单删除', @oc_work_order_menu_id, 4, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:remove', '#',
       'admin', sysdate(), '售后工单删除权限'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:remove'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '工单指派', @oc_work_order_menu_id, 5, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:assign', '#',
       'admin', sysdate(), '售后工单负责人调整权限'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:assign'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '状态流转', @oc_work_order_menu_id, 6, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:status', '#',
       'admin', sysdate(), '售后工单状态流转权限'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:status'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '处理记录', @oc_work_order_menu_id, 7, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:record', '#',
       'admin', sysdate(), '售后工单处理记录权限'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:record'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select 'AI处理建议', @oc_work_order_menu_id, 8, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:workorder:ai', '#',
       'admin', sysdate(), '基于设备型号知识库生成AI处理建议'
where @oc_work_order_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_work_order_menu_id
        and perms = 'officecare:workorder:ai'
  );

-- ----------------------------
-- 5、售后人员角色授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (
      @oc_root_menu_id, @oc_service_menu_id, @oc_work_order_menu_id
  )
  or m.perms in (
      'officecare:workorder:query', 'officecare:workorder:add',
      'officecare:workorder:edit', 'officecare:workorder:remove',
      'officecare:workorder:assign', 'officecare:workorder:status',
      'officecare:workorder:record', 'officecare:workorder:ai'
  )
where r.role_key = 'oc_service'
  and r.del_flag = '0';

-- ----------------------------
-- 6、结构和菜单验收
-- ----------------------------
select table_name, table_comment
from information_schema.tables
where table_schema = database()
  and table_name in ('oc_work_order', 'oc_work_order_record')
order by table_name;

select menu_id, menu_name, parent_id, path, component, perms
from sys_menu
where menu_id = @oc_work_order_menu_id
   or parent_id = @oc_work_order_menu_id
order by parent_id, order_num, menu_id;
