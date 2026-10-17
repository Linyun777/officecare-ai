-- OfficeCare AI 产品资料初始化
-- 前置条件：已执行 001_officecare_foundation.sql。
-- 作用：创建品牌、产品型号表，以及产品资料菜单和角色权限。
-- 特性：脚本可重复执行；执行前仍应在目标环境完成数据库备份。

-- ----------------------------
-- 1、产品资料表
-- ----------------------------
create table if not exists oc_brand
(
    brand_id     bigint       not null auto_increment comment '品牌ID',
    brand_code   varchar(50)  not null                comment '品牌编码',
    brand_name   varchar(100) not null                comment '品牌名称',
    description  varchar(500) default null            comment '品牌说明',
    sort_order   int          not null default 0      comment '显示顺序',
    status       char(1)      not null default '0'    comment '状态（0正常 1停用）',
    del_flag     char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by    varchar(64)  default ''              comment '创建者',
    create_time  datetime     default null            comment '创建时间',
    update_by    varchar(64)  default ''              comment '更新者',
    update_time  datetime     default null            comment '更新时间',
    remark       varchar(500) default null            comment '备注',
    primary key (brand_id),
    unique key uk_oc_brand_code (brand_code),
    key idx_oc_brand_name (brand_name),
    key idx_oc_brand_status (status, del_flag)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='OfficeCare品牌表';

create table if not exists oc_product_model
(
    model_id     bigint       not null auto_increment comment '产品型号ID',
    brand_id     bigint       not null                comment '所属品牌ID',
    model_code   varchar(64)  not null                comment '型号编码',
    model_name   varchar(100) not null                comment '型号名称',
    series_name  varchar(100) default null            comment '产品系列',
    description  varchar(500) default null            comment '型号说明',
    sort_order   int          not null default 0      comment '显示顺序',
    status       char(1)      not null default '0'    comment '状态（0正常 1停用）',
    del_flag     char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by    varchar(64)  default ''              comment '创建者',
    create_time  datetime     default null            comment '创建时间',
    update_by    varchar(64)  default ''              comment '更新者',
    update_time  datetime     default null            comment '更新时间',
    remark       varchar(500) default null            comment '备注',
    primary key (model_id),
    unique key uk_oc_model_brand_code (brand_id, model_code),
    key idx_oc_model_brand_status (brand_id, status, del_flag),
    key idx_oc_model_name (model_name)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='OfficeCare产品型号表';

-- ----------------------------
-- 2、产品资料菜单
-- ----------------------------
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
    '产品资料', @oc_root_menu_id, 2, 'catalog', null, '', 'OfficeCareCatalog', 1, 0,
    'M', '0', '0', '', 'tree-table', 'admin', sysdate(), 'OfficeCare产品资料目录'
where @oc_root_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_root_menu_id and path = 'catalog'
  );

set @oc_catalog_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_root_menu_id and path = 'catalog'
    order by menu_id
    limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '品牌管理', @oc_catalog_menu_id, 1, 'brand', 'officecare/brand/index', '', 'OfficeCareBrand', 1, 0,
    'C', '0', '0', 'officecare:brand:list', 'tree', 'admin', sysdate(), 'OfficeCare品牌管理'
where @oc_catalog_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_catalog_menu_id and path = 'brand'
  );

set @oc_brand_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_catalog_menu_id and path = 'brand'
    order by menu_id
    limit 1
);

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '型号管理', @oc_catalog_menu_id, 2, 'product-model', 'officecare/productModel/index', '',
    'OfficeCareProductModel', 1, 0, 'C', '0', '0', 'officecare:model:list', 'component',
    'admin', sysdate(), 'OfficeCare产品型号管理'
where @oc_catalog_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_catalog_menu_id and path = 'product-model'
  );

set @oc_model_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_catalog_menu_id and path = 'product-model'
    order by menu_id
    limit 1
);

-- ----------------------------
-- 3、按钮权限
-- ----------------------------
insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '品牌查询', @oc_brand_menu_id, 1, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:brand:query', '#', 'admin', sysdate(), '品牌查询权限'
where @oc_brand_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_brand_menu_id and perms = 'officecare:brand:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '品牌新增', @oc_brand_menu_id, 2, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:brand:add', '#', 'admin', sysdate(), '品牌新增权限'
where @oc_brand_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_brand_menu_id and perms = 'officecare:brand:add'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '品牌修改', @oc_brand_menu_id, 3, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:brand:edit', '#', 'admin', sysdate(), '品牌修改权限'
where @oc_brand_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_brand_menu_id and perms = 'officecare:brand:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '品牌删除', @oc_brand_menu_id, 4, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:brand:remove', '#', 'admin', sysdate(), '品牌删除权限'
where @oc_brand_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_brand_menu_id and perms = 'officecare:brand:remove'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '型号查询', @oc_model_menu_id, 1, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:model:query', '#', 'admin', sysdate(), '型号查询权限'
where @oc_model_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_model_menu_id and perms = 'officecare:model:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '型号新增', @oc_model_menu_id, 2, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:model:add', '#', 'admin', sysdate(), '型号新增权限'
where @oc_model_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_model_menu_id and perms = 'officecare:model:add'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '型号修改', @oc_model_menu_id, 3, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:model:edit', '#', 'admin', sysdate(), '型号修改权限'
where @oc_model_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_model_menu_id and perms = 'officecare:model:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '型号删除', @oc_model_menu_id, 4, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:model:remove', '#', 'admin', sysdate(), '型号删除权限'
where @oc_model_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_model_menu_id and perms = 'officecare:model:remove'
  );

-- ----------------------------
-- 4、角色菜单授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (@oc_root_menu_id, @oc_catalog_menu_id, @oc_brand_menu_id, @oc_model_menu_id)
  or m.perms in (
      'officecare:brand:query', 'officecare:brand:add', 'officecare:brand:edit', 'officecare:brand:remove',
      'officecare:model:query', 'officecare:model:add', 'officecare:model:edit', 'officecare:model:remove'
  )
where r.role_key = 'oc_knowledge'
  and r.del_flag = '0';

insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (@oc_root_menu_id, @oc_catalog_menu_id, @oc_brand_menu_id, @oc_model_menu_id)
  or m.perms in ('officecare:brand:query', 'officecare:model:query')
where r.role_key = 'oc_service'
  and r.del_flag = '0';
