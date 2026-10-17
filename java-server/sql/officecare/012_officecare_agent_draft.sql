-- OfficeCare AI 智能报修草稿与人工确认闭环
-- 前置条件：已执行001至011脚本。
-- 作用：保存可审计的Agent分析、人工可编辑草稿、确认后的正式工单关联及菜单权限。
-- 安全边界：Agent不直接创建正式工单；只有当前用户显式确认后才复用Java工单Service建单。
-- 本脚本可重复执行，不修改已有工单、AI会话、文档索引或Milvus数据。

-- ----------------------------
-- 1、智能报修草稿
-- 状态：0待确认 1已确认 2已取消
-- 客户、设备、品牌、型号和知识库名称保存分析时快照，便于后续审计。
-- JSON字段保存Agent步骤、警告、引用和工具调用轨迹，不参与业务条件查询。
-- ----------------------------
create table if not exists oc_agent_draft
(
    draft_id                    bigint        not null auto_increment comment '智能报修草稿ID',
    user_id                     bigint        not null                comment '草稿所属系统用户ID',
    customer_org_id             bigint        not null                comment '客户单位ID',
    device_id                   bigint        not null                comment '设备ID',
    knowledge_base_id           bigint        not null                comment '分析所用知识库ID',
    customer_name               varchar(150)  not null                comment '客户单位名称快照',
    device_name                 varchar(100)  not null                comment '设备名称快照',
    serial_number               varchar(100)  not null                comment '设备序列号快照',
    brand_name                  varchar(100)  not null                comment '品牌名称快照',
    model_name                  varchar(200)  not null                comment '型号名称快照',
    knowledge_base_name         varchar(150)  not null                comment '知识库名称快照',
    issue_description           varchar(2000) not null                comment '用户原始报修描述',
    answered                    tinyint(1)    not null default 0      comment '知识证据是否足以回答',
    summary                     varchar(1000) not null                comment 'Agent分析摘要或拒答说明',
    troubleshooting_steps_json  longtext      default null            comment '排障步骤JSON数组',
    safety_warnings_json        longtext      default null            comment '安全警告JSON数组',
    requires_on_site_service    tinyint(1)    not null default 0      comment '是否建议上门处理',
    refusal_reason              varchar(500)  default null            comment '证据不足等拒答原因',
    draft_title                 varchar(150)  not null                comment '人工可编辑工单标题',
    draft_fault_description     varchar(2000) not null                comment '人工可编辑故障描述',
    draft_priority              char(1)       not null default '1'    comment '人工可编辑优先级（0低 1中 2高 3紧急）',
    assignee_user_id            bigint        default null            comment '人工选择的负责人用户ID',
    work_order_remark           varchar(500)  default null            comment '人工填写的工单备注',
    citations_json              longtext      default null            comment '不可由前端改写的知识引用JSON',
    tool_trace_json             longtext      default null            comment 'Agent受控工具调用轨迹JSON',
    model_name_used             varchar(100)  default null            comment '生成模型名称快照',
    status                      char(1)       not null default '0'    comment '状态（0待确认 1已确认 2已取消）',
    work_order_id               bigint        default null            comment '确认后创建的正式工单ID',
    confirmed_time              datetime      default null            comment '人工确认时间',
    del_flag                    char(1)       not null default '0'    comment '删除标志（0存在 2删除）',
    create_by                   varchar(64)   default ''              comment '创建者',
    create_time                 datetime      not null                comment '创建时间',
    update_by                   varchar(64)   default ''              comment '更新者',
    update_time                 datetime      default null            comment '更新时间',
    remark                      varchar(500)  default null            comment '系统备注',
    primary key (draft_id),
    unique key uk_oc_agent_draft_work_order (work_order_id),
    key idx_oc_agent_draft_user (user_id, status, del_flag, create_time),
    key idx_oc_agent_draft_device (device_id, status, del_flag),
    key idx_oc_agent_draft_knowledge (knowledge_base_id, create_time)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='OfficeCare智能报修草稿表';

-- ----------------------------
-- 2、智能报修菜单
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
    '智能报修', @oc_service_menu_id, 4, 'smart-report',
    'officecare/smartReport/index', '', 'OfficeCareSmartReport', 1, 0,
    'C', '0', '0', 'officecare:agent:list', 'chat-line-round',
    'admin', sysdate(), 'Agent分析、人工编辑与确认建单'
where @oc_service_menu_id is not null
  and not exists (
      select 1 from sys_menu
      where parent_id = @oc_service_menu_id and path = 'smart-report'
  );

set @oc_agent_menu_id := (
    select menu_id from sys_menu
    where parent_id = @oc_service_menu_id and path = 'smart-report'
    order by menu_id limit 1
);

-- ----------------------------
-- 3、智能报修按钮权限
-- ----------------------------
insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '草稿查询', @oc_agent_menu_id, 1, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:agent:query', '#',
       'admin', sysdate(), '查询本人智能报修草稿'
where @oc_agent_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_agent_menu_id
        and perms = 'officecare:agent:query'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '智能分析', @oc_agent_menu_id, 2, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:agent:analyze', '#',
       'admin', sysdate(), '调用受控Agent生成待确认草稿'
where @oc_agent_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_agent_menu_id
        and perms = 'officecare:agent:analyze'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '草稿编辑', @oc_agent_menu_id, 3, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:agent:edit', '#',
       'admin', sysdate(), '编辑本人待确认草稿'
where @oc_agent_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_agent_menu_id
        and perms = 'officecare:agent:edit'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '确认建单', @oc_agent_menu_id, 4, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:agent:confirm', '#',
       'admin', sysdate(), '人工确认并幂等创建正式工单'
where @oc_agent_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_agent_menu_id
        and perms = 'officecare:agent:confirm'
  );

insert into sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, remark)
select '取消草稿', @oc_agent_menu_id, 5, '#', '', '', '', 1, 0,
       'F', '0', '0', 'officecare:agent:remove', '#',
       'admin', sysdate(), '取消本人待确认草稿'
where @oc_agent_menu_id is not null
  and not exists (
      select 1 from sys_menu where parent_id = @oc_agent_menu_id
        and perms = 'officecare:agent:remove'
  );

-- ----------------------------
-- 4、售后人员角色授权
-- ----------------------------
insert ignore into sys_role_menu (role_id, menu_id)
select distinct r.role_id, m.menu_id
from sys_role r
join sys_menu m
  on m.menu_id in (
      @oc_root_menu_id, @oc_service_menu_id, @oc_agent_menu_id
  )
  or m.perms in (
      'officecare:agent:query', 'officecare:agent:analyze',
      'officecare:agent:edit', 'officecare:agent:confirm',
      'officecare:agent:remove'
  )
where r.role_key = 'oc_service'
  and r.del_flag = '0';

-- ----------------------------
-- 5、结构和菜单验收
-- ----------------------------
select table_name, table_comment
from information_schema.tables
where table_schema = database()
  and table_name = 'oc_agent_draft';

select menu_id, menu_name, parent_id, path, component, perms
from sys_menu
where menu_id = @oc_agent_menu_id
   or parent_id = @oc_agent_menu_id
order by parent_id, order_num, menu_id;
