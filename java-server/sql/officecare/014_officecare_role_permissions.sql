-- OfficeCare AI 最小角色权限收敛
-- 前置条件：已执行001至012脚本；013仅包含演示型号，不是本脚本前置条件。
-- 作用：将内置超级管理员、知识库管理员、售后工程师收敛为三个系统角色。
-- 边界：不创建业务权限表、不创建用户、不保存密码、不启用多租户或工单行级隔离。
-- 安全：只重建oc_knowledge、oc_service两个角色在OfficeCare菜单树内的关联；
--       不修改admin角色、其他角色、用户角色关联或任何OfficeCare业务数据。
-- 执行前仍建议备份sys_role和sys_role_menu；脚本可重复执行。

start transaction;

-- ----------------------------
-- 1、确保两个OfficeCare业务角色存在
-- 系统超级管理员继续使用若依内置admin角色，不重复创建。
-- data_scope=1表示公司内部共享全部OfficeCare数据，第一版不做行级隔离。
-- ----------------------------
insert into sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly,
     dept_check_strictly, status, del_flag, create_by, create_time, remark)
select
    '知识库管理员', 'oc_knowledge', 10, '1', 1,
    1, '0', '0', 'admin', sysdate(), '维护产品资料、知识库和文档，可使用AI助手'
where not exists (
    select 1 from sys_role
    where role_key = 'oc_knowledge' and del_flag = '0'
);

insert into sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly,
     dept_check_strictly, status, del_flag, create_by, create_time, remark)
select
    '售后工程师', 'oc_service', 20, '1', 1,
    1, '0', '0', 'admin', sysdate(), '维护客户设备和工单，可使用智能报修与AI助手'
where not exists (
    select 1 from sys_role
    where role_key = 'oc_service' and del_flag = '0'
);

update sys_role
set role_name = '知识库管理员',
    role_sort = 10,
    data_scope = '1',
    menu_check_strictly = 1,
    dept_check_strictly = 1,
    status = '0',
    update_by = 'admin',
    update_time = sysdate(),
    remark = '维护产品资料、知识库和文档，可使用AI助手'
where role_key = 'oc_knowledge'
  and del_flag = '0';

update sys_role
set role_name = '售后工程师',
    role_sort = 20,
    data_scope = '1',
    menu_check_strictly = 1,
    dept_check_strictly = 1,
    status = '0',
    update_by = 'admin',
    update_time = sysdate(),
    remark = '维护客户设备和工单，可使用智能报修与AI助手'
where role_key = 'oc_service'
  and del_flag = '0';

-- ----------------------------
-- 2、解析OfficeCare菜单树
-- 当前最深层级为：OfficeCare根目录 -> 业务目录 -> 页面 -> 按钮。
-- 额外保留一级父链，避免后续在现有树下增加一层菜单时遗漏。
-- ----------------------------
set @oc_root_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = 0
      and path = 'officecare'
      and menu_type = 'M'
    order by menu_id
    limit 1
);

drop temporary table if exists tmp_oc_permission_menu;
create temporary table tmp_oc_permission_menu
(
    menu_id bigint not null primary key
);

-- 根菜单不存在时利用NOT NULL约束立即终止，避免提交两个没有菜单授权的空角色。
insert into tmp_oc_permission_menu (menu_id)
values (@oc_root_menu_id);

insert ignore into tmp_oc_permission_menu (menu_id)
select m.menu_id
from sys_menu m
left join sys_menu p1 on p1.menu_id = m.parent_id
left join sys_menu p2 on p2.menu_id = p1.parent_id
left join sys_menu p3 on p3.menu_id = p2.parent_id
left join sys_menu p4 on p4.menu_id = p3.parent_id
where @oc_root_menu_id is not null
  and @oc_root_menu_id in (
      m.menu_id, p1.menu_id, p2.menu_id, p3.menu_id, p4.menu_id
  );

-- ----------------------------
-- 3、清理两个业务角色原有的OfficeCare菜单关联
-- 这是权限收敛所需的受限删除，只影响两个角色和上述临时菜单ID集合。
-- ----------------------------
delete rm
from sys_role_menu rm
join sys_role r on r.role_id = rm.role_id
join tmp_oc_permission_menu t on t.menu_id = rm.menu_id
where r.role_key in ('oc_knowledge', 'oc_service')
  and r.del_flag = '0';

-- ----------------------------
-- 4、写入最终权限矩阵
-- 公共：工作台和AI助手。
-- 知识库管理员：品牌、型号、知识库、文档全部操作。
-- 售后工程师：产品和知识中心只读；客户、设备、工单、智能报修全部操作。
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
cross join tmp_oc_permission_menu t
join sys_menu m on m.menu_id = t.menu_id
where r.role_key in ('oc_knowledge', 'oc_service')
  and r.del_flag = '0'
  and m.status = '0'
  and (
      m.menu_id = @oc_root_menu_id
      or m.perms in (
          'officecare:dashboard:view',
          'officecare:dashboard:query',
          'officecare:ai:chat'
      )
      or (
          r.role_key = 'oc_knowledge'
          and (
              (m.menu_type = 'M' and m.path in ('catalog', 'knowledge'))
              or m.perms like 'officecare:brand:%'
              or m.perms like 'officecare:model:%'
              or m.perms like 'officecare:knowledge-base:%'
              or m.perms like 'officecare:document:%'
          )
      )
      or (
          r.role_key = 'oc_service'
          and (
              (m.menu_type = 'M' and m.path in ('catalog', 'knowledge', 'service'))
              or m.perms in (
                  'officecare:brand:list',
                  'officecare:brand:query',
                  'officecare:model:list',
                  'officecare:model:query',
                  'officecare:knowledge-base:list',
                  'officecare:knowledge-base:query',
                  'officecare:document:list',
                  'officecare:document:query',
                  'officecare:document:download'
              )
              or m.perms like 'officecare:customer:%'
              or m.perms like 'officecare:device:%'
              or m.perms like 'officecare:workorder:%'
              or m.perms like 'officecare:agent:%'
          )
      )
  );

drop temporary table if exists tmp_oc_permission_menu;

commit;

-- ----------------------------
-- 5、执行后验收（只读）
-- admin为若依超级管理员，不依赖sys_role_menu逐条授权，所以这里只展示两个业务角色。
-- ----------------------------
select role_id, role_name, role_key, data_scope, status
from sys_role
where role_key in ('admin', 'oc_knowledge', 'oc_service')
  and del_flag = '0'
order by role_sort, role_id;

select
    r.role_name,
    r.role_key,
    sum(m.menu_type = 'M') as directory_count,
    sum(m.menu_type = 'C') as page_count,
    sum(m.menu_type = 'F') as button_count,
    count(*) as total_menu_count
from sys_role r
join sys_role_menu rm on rm.role_id = r.role_id
join sys_menu m on m.menu_id = rm.menu_id
where r.role_key in ('oc_knowledge', 'oc_service')
  and r.del_flag = '0'
  and (
      m.path = 'officecare'
      or m.perms like 'officecare:%'
      or m.menu_id in (
          select child.menu_id
          from sys_menu child
          join sys_menu root on root.menu_id = child.parent_id
          where root.parent_id = 0 and root.path = 'officecare'
      )
  )
group by r.role_id, r.role_name, r.role_key
order by r.role_sort, r.role_id;

select r.role_name, m.menu_type, m.menu_name, m.path, m.perms
from sys_role r
join sys_role_menu rm on rm.role_id = r.role_id
join sys_menu m on m.menu_id = rm.menu_id
where r.role_key in ('oc_knowledge', 'oc_service')
  and r.del_flag = '0'
  and (
      m.perms like 'officecare:%'
      or m.path in ('officecare', 'catalog', 'knowledge', 'service')
  )
order by r.role_sort, m.parent_id, m.order_num, m.menu_id;
