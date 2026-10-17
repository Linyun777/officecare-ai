-- OfficeCare AI 知识库初始化
-- 前置条件：已执行001、002、003脚本。
-- 作用：创建知识库表、知识中心菜单、知识库权限和单设备MVP知识库。
-- 特性：脚本可重复执行；不覆盖已存在的业务记录。

-- ----------------------------
-- 1、知识库表
-- ----------------------------
create table if not exists oc_knowledge_base
(
    knowledge_base_id    bigint       not null auto_increment comment '知识库ID',
    knowledge_base_code  varchar(64)  not null                comment '知识库编码',
    knowledge_base_name  varchar(100) not null                comment '知识库名称',
    brand_id              bigint       not null                comment '所属品牌ID',
    model_id              bigint       not null                comment '所属产品型号ID',
    description           varchar(500) default null            comment '知识库说明',
    sort_order            int          not null default 0      comment '显示顺序',
    status                char(1)      not null default '0'    comment '状态（0正常 1停用）',
    del_flag              char(1)      not null default '0'    comment '删除标志（0存在 2删除）',
    create_by             varchar(64)  default ''              comment '创建者',
    create_time           datetime     default null            comment '创建时间',
    update_by             varchar(64)  default ''              comment '更新者',
    update_time           datetime     default null            comment '更新时间',
    remark                varchar(500) default null            comment '备注',
    primary key (knowledge_base_id),
    unique key uk_oc_knowledge_base_code (knowledge_base_code),
    key idx_oc_knowledge_base_product (brand_id, model_id, status, del_flag),
    key idx_oc_knowledge_base_name (knowledge_base_name)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='OfficeCare知识库表';

-- ----------------------------
-- 2、单设备MVP知识库
-- ----------------------------
set @officecare_brother_brand_id := (
    select brand_id
    from oc_brand
    where brand_code = 'BROTHER'
      and del_flag = '0'
    order by brand_id
    limit 1
);

set @officecare_l2550dw_model_id := (
    select model_id
    from oc_product_model
    where brand_id = @officecare_brother_brand_id
      and model_code = 'DCP-L2550DW'
      and del_flag = '0'
    order by model_id
    limit 1
);

insert into oc_knowledge_base
    (knowledge_base_code, knowledge_base_name, brand_id, model_id, description,
     sort_order, status, del_flag, create_by, create_time, remark)
select
    'BROTHER_DCP_L2550DW', 'Brother DCP-L2550DW知识库',
    @officecare_brother_brand_id, @officecare_l2550dw_model_id,
    '用于保存DCP-L2550DW官方手册、FAQ和故障处理资料',
    1, '0', '0', 'admin', sysdate(), 'OfficeCare单设备MVP知识库'
where @officecare_brother_brand_id is not null
  and @officecare_l2550dw_model_id is not null
  and not exists (
      select 1
      from oc_knowledge_base
      where knowledge_base_code = 'BROTHER_DCP_L2550DW'
  );

-- ----------------------------
-- 3、知识中心菜单
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
    '知识中心', @oc_root_menu_id, 3, 'knowledge', null, '', 'OfficeCareKnowledge', 1, 0,
    'M', '0', '0', '', 'documentation', 'admin', sysdate(), 'OfficeCare知识中心目录'
where @oc_root_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_root_menu_id and path = 'knowledge'
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
    '知识库管理', @oc_knowledge_menu_id, 1, 'knowledge-base',
    'officecare/knowledgeBase/index', '', 'OfficeCareKnowledgeBase', 1, 0,
    'C', '0', '0', 'officecare:knowledge-base:list', 'tree', 'admin', sysdate(),
    'OfficeCare知识库管理'
where @oc_knowledge_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_knowledge_menu_id and path = 'knowledge-base'
  );

set @oc_knowledge_base_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_knowledge_menu_id and path = 'knowledge-base'
    order by menu_id
    limit 1
);

-- ----------------------------
-- 4、知识库按钮权限
-- ----------------------------
insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '知识库查询', @oc_knowledge_base_menu_id, 1, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:knowledge-base:query', '#', 'admin', sysdate(), '知识库查询权限'
where @oc_knowledge_base_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_knowledge_base_menu_id and perms = 'officecare:knowledge-base:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '知识库新增', @oc_knowledge_base_menu_id, 2, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:knowledge-base:add', '#', 'admin', sysdate(), '知识库新增权限'
where @oc_knowledge_base_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_knowledge_base_menu_id and perms = 'officecare:knowledge-base:add'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '知识库修改', @oc_knowledge_base_menu_id, 3, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:knowledge-base:edit', '#', 'admin', sysdate(), '知识库修改权限'
where @oc_knowledge_base_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_knowledge_base_menu_id and perms = 'officecare:knowledge-base:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_by, create_time, remark)
select
    '知识库删除', @oc_knowledge_base_menu_id, 4, '#', '', '', '', 1, 0,
    'F', '0', '0', 'officecare:knowledge-base:remove', '#', 'admin', sysdate(), '知识库删除权限'
where @oc_knowledge_base_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_knowledge_base_menu_id and perms = 'officecare:knowledge-base:remove'
  );

-- ----------------------------
-- 5、角色菜单授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (@oc_root_menu_id, @oc_knowledge_menu_id, @oc_knowledge_base_menu_id)
  or m.perms in (
      'officecare:knowledge-base:query', 'officecare:knowledge-base:add',
      'officecare:knowledge-base:edit', 'officecare:knowledge-base:remove'
  )
where r.role_key = 'oc_knowledge'
  and r.del_flag = '0';

insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (@oc_root_menu_id, @oc_knowledge_menu_id, @oc_knowledge_base_menu_id)
  or m.perms = 'officecare:knowledge-base:query'
where r.role_key = 'oc_service'
  and r.del_flag = '0';

select knowledge_base_id, knowledge_base_code, knowledge_base_name, brand_id, model_id, status
from oc_knowledge_base
where knowledge_base_code = 'BROTHER_DCP_L2550DW';
