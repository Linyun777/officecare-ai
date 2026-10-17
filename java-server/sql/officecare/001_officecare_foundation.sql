-- OfficeCare AI 工程底座初始化
-- 作用：创建知识运营/售后人员角色、OfficeCare根菜单、工作台菜单和查询权限。
-- 特性：脚本可重复执行；执行前仍应在目标环境完成数据库备份。

-- ----------------------------
-- 1、OfficeCare角色
-- ----------------------------
insert into sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
     status, del_flag, create_by, create_time, remark)
select
    '知识运营', 'oc_knowledge', 10, '1', 1, 1, '0', '0', 'admin', sysdate(), 'OfficeCare知识运营角色'
where not exists (
    select 1 from sys_role where role_key = 'oc_knowledge' and del_flag = '0'
);

insert into sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
     status, del_flag, create_by, create_time, remark)
select
    '售后人员', 'oc_service', 20, '1', 1, 1, '0', '0', 'admin', sysdate(), 'OfficeCare售后人员角色'
where not exists (
    select 1 from sys_role where role_key = 'oc_service' and del_flag = '0'
);

-- ----------------------------
-- 2、OfficeCare菜单
-- ----------------------------
insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    'OfficeCare', 0, 1, 'officecare', null, '', 'OfficeCare', 1, 0,
    'M', '0', '0', '', 'dashboard', 'admin', sysdate(), 'OfficeCare根目录'
where not exists (
    select 1 from sys_menu where parent_id = 0 and path = 'officecare'
);

set @oc_root_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = 0 and path = 'officecare'
    order by menu_id
    limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '工作台', @oc_root_menu_id, 1, 'dashboard', 'officecare/dashboard/index', '', 'OfficeCareDashboard', 1, 0,
    'C', '0', '0', 'officecare:dashboard:view', 'dashboard', 'admin', sysdate(), 'OfficeCare工作台'
where @oc_root_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_root_menu_id and path = 'dashboard'
  );

set @oc_dashboard_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_root_menu_id and path = 'dashboard'
    order by menu_id
    limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '工作台查询', @oc_dashboard_menu_id, 1, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:dashboard:query', '#', 'admin', sysdate(), 'OfficeCare工作台查询权限'
where @oc_dashboard_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_dashboard_menu_id and perms = 'officecare:dashboard:query'
  );

set @oc_dashboard_query_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_dashboard_menu_id and perms = 'officecare:dashboard:query'
    order by menu_id
    limit 1
);

-- ----------------------------
-- 3、角色菜单授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select role_id, @oc_root_menu_id
from sys_role
where role_key in ('oc_knowledge', 'oc_service')
  and del_flag = '0'
  and @oc_root_menu_id is not null;

insert ignore into sys_role_menu (role_id, menu_id)
select role_id, @oc_dashboard_menu_id
from sys_role
where role_key in ('oc_knowledge', 'oc_service')
  and del_flag = '0'
  and @oc_dashboard_menu_id is not null;

insert ignore into sys_role_menu (role_id, menu_id)
select role_id, @oc_dashboard_query_menu_id
from sys_role
where role_key in ('oc_knowledge', 'oc_service')
  and del_flag = '0'
  and @oc_dashboard_query_menu_id is not null;
