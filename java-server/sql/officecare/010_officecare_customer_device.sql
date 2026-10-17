-- OfficeCare AI 客户单位与设备档案初始化
-- 前置条件：已执行001至009脚本。
-- 作用：创建最小售后业务所需的客户单位、设备档案表及菜单权限。
-- 特性：脚本可重复执行；不修改知识库文档、AI会话或Milvus数据。

-- ----------------------------
-- 1、客户单位
-- ----------------------------
create table if not exists oc_customer_org
(
    customer_org_id bigint       not null auto_increment comment '客户单位ID',
    customer_code   varchar(50)  not null                comment '客户单位编码',
    customer_name   varchar(150) not null                comment '客户单位名称',
    contact_name    varchar(50)  default null            comment '主要联系人',
    contact_phone   varchar(30)  default null            comment '联系电话',
    address         varchar(255) default null            comment '单位地址',
    status          char(1)      not null default '0'    comment '状态（0正常 1停用）',
    del_flag        char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by       varchar(64)  default ''              comment '创建者',
    create_time     datetime     default null            comment '创建时间',
    update_by       varchar(64)  default ''              comment '更新者',
    update_time     datetime     default null            comment '更新时间',
    remark          varchar(500) default null            comment '备注',
    primary key (customer_org_id),
    unique key uk_oc_customer_org_code (customer_code),
    key idx_oc_customer_org_name (customer_name),
    key idx_oc_customer_org_status (status, del_flag)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare客户单位表';

-- ----------------------------
-- 2、设备档案
-- 品牌通过oc_product_model.brand_id获得，避免设备表重复保存后产生不一致。
-- ----------------------------
create table if not exists oc_device
(
    device_id          bigint       not null auto_increment comment '设备ID',
    customer_org_id    bigint       not null                comment '所属客户单位ID',
    model_id           bigint       not null                comment '产品型号ID',
    device_name        varchar(100) not null                comment '设备名称',
    serial_number      varchar(100) not null                comment '设备序列号',
    install_location   varchar(255) default null            comment '安装位置',
    purchase_date      date         default null            comment '购买日期',
    install_date       date         default null            comment '安装日期',
    warranty_end_date  date         default null            comment '保修截止日期',
    status             char(1)      not null default '0'    comment '状态（0正常 1停用）',
    del_flag           char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by          varchar(64)  default ''              comment '创建者',
    create_time        datetime     default null            comment '创建时间',
    update_by          varchar(64)  default ''              comment '更新者',
    update_time        datetime     default null            comment '更新时间',
    remark             varchar(500) default null            comment '备注',
    primary key (device_id),
    unique key uk_oc_device_serial_number (serial_number),
    key idx_oc_device_customer (customer_org_id, status, del_flag),
    key idx_oc_device_model (model_id, status, del_flag),
    key idx_oc_device_warranty (warranty_end_date)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare设备档案表';

-- ----------------------------
-- 3、售后管理菜单
-- ----------------------------
set @oc_root_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = 0 and path = 'officecare'
    order by menu_id
    limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select
    '售后管理', @oc_root_menu_id, 5, 'service', null, '',
    'OfficeCareService', 1, 0, 'M', '0', '0', '', 'peoples',
    'admin', sysdate(), 'OfficeCare售后业务目录'
where @oc_root_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_root_menu_id and path = 'service'
  );

set @oc_service_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_root_menu_id and path = 'service'
    order by menu_id
    limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select
    '客户单位', @oc_service_menu_id, 1, 'customer-org',
    'officecare/customerOrg/index', '', 'OfficeCareCustomerOrg', 1, 0,
    'C', '0', '0', 'officecare:customer:list', 'peoples',
    'admin', sysdate(), 'OfficeCare客户单位管理'
where @oc_service_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_service_menu_id and path = 'customer-org'
  );

set @oc_customer_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_service_menu_id and path = 'customer-org'
    order by menu_id
    limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select
    '设备档案', @oc_service_menu_id, 2, 'device',
    'officecare/device/index', '', 'OfficeCareDevice', 1, 0,
    'C', '0', '0', 'officecare:device:list', 'monitor',
    'admin', sysdate(), 'OfficeCare设备档案管理'
where @oc_service_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_service_menu_id and path = 'device'
  );

set @oc_device_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_service_menu_id and path = 'device'
    order by menu_id
    limit 1
);

-- ----------------------------
-- 4、按钮权限
-- ----------------------------
insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '客户查询', @oc_customer_menu_id, 1, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:customer:query', '#',
       'admin', sysdate(), '客户单位查询权限'
where @oc_customer_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_customer_menu_id
        and perms = 'officecare:customer:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '客户新增', @oc_customer_menu_id, 2, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:customer:add', '#',
       'admin', sysdate(), '客户单位新增权限'
where @oc_customer_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_customer_menu_id
        and perms = 'officecare:customer:add'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '客户修改', @oc_customer_menu_id, 3, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:customer:edit', '#',
       'admin', sysdate(), '客户单位修改权限'
where @oc_customer_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_customer_menu_id
        and perms = 'officecare:customer:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '客户删除', @oc_customer_menu_id, 4, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:customer:remove', '#',
       'admin', sysdate(), '客户单位删除权限'
where @oc_customer_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_customer_menu_id
        and perms = 'officecare:customer:remove'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '设备查询', @oc_device_menu_id, 1, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:device:query', '#',
       'admin', sysdate(), '设备档案查询权限'
where @oc_device_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_device_menu_id
        and perms = 'officecare:device:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '设备新增', @oc_device_menu_id, 2, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:device:add', '#',
       'admin', sysdate(), '设备档案新增权限'
where @oc_device_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_device_menu_id
        and perms = 'officecare:device:add'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '设备修改', @oc_device_menu_id, 3, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:device:edit', '#',
       'admin', sysdate(), '设备档案修改权限'
where @oc_device_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_device_menu_id
        and perms = 'officecare:device:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '设备删除', @oc_device_menu_id, 4, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:device:remove', '#',
       'admin', sysdate(), '设备档案删除权限'
where @oc_device_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_device_menu_id
        and perms = 'officecare:device:remove'
  );

-- ----------------------------
-- 5、售后人员角色授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (
      @oc_root_menu_id, @oc_service_menu_id,
      @oc_customer_menu_id, @oc_device_menu_id
  )
  or m.perms in (
      'officecare:customer:query', 'officecare:customer:add',
      'officecare:customer:edit', 'officecare:customer:remove',
      'officecare:device:query', 'officecare:device:add',
      'officecare:device:edit', 'officecare:device:remove'
  )
where r.role_key = 'oc_service'
  and r.del_flag = '0';

-- ----------------------------
-- 6、结构和菜单验收
-- ----------------------------
select table_name, table_comment
from information_schema.tables
where table_schema = database()
  and table_name in ('oc_customer_org', 'oc_device')
order by table_name;

select menu_id, menu_name, parent_id, path, component, perms
from sys_menu
where menu_id in (@oc_service_menu_id, @oc_customer_menu_id, @oc_device_menu_id)
   or parent_id in (@oc_customer_menu_id, @oc_device_menu_id)
order by parent_id, order_num, menu_id;
