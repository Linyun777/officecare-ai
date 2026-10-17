-- OfficeCare AI 知识助手菜单与权限
-- 前置条件：已执行001至007脚本，且文档当前版本已经成功写入Milvus。
-- 作用：增加AI助手页面及其问答权限，不创建会话表，不修改业务数据。
-- 特性：脚本可重复执行。

set @oc_root_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = 0 and path = 'officecare'
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
    'AI助手', @oc_root_menu_id, 4, 'assistant', 'officecare/assistant/index', '',
    'OfficeCareAssistant', 1, 0, 'C', '0', '0', 'officecare:ai:chat',
    'message', 'admin', sysdate(), 'OfficeCare知识库问答助手'
where @oc_root_menu_id is not null
  and not exists (
      select 1
      from sys_menu
      where parent_id = @oc_root_menu_id
        and path = 'assistant'
  );

set @oc_ai_assistant_menu_id := (
    select menu_id
    from sys_menu
    where parent_id = @oc_root_menu_id
      and path = 'assistant'
    order by menu_id
    limit 1
);

-- 知识运营和售后人员均可使用问答；角色仍由若依统一管理。
insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, @oc_root_menu_id
from sys_role r
where r.role_key in ('oc_knowledge', 'oc_service')
  and r.del_flag = '0'
  and @oc_root_menu_id is not null;

insert ignore into sys_role_menu (role_id, menu_id)
select r.role_id, @oc_ai_assistant_menu_id
from sys_role r
where r.role_key in ('oc_knowledge', 'oc_service')
  and r.del_flag = '0'
  and @oc_ai_assistant_menu_id is not null;

select menu_id, menu_name, path, component, perms, status
from sys_menu
where menu_id = @oc_ai_assistant_menu_id;
