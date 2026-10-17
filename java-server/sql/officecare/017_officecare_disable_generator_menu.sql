-- OfficeCare AI 代码生成模块退役菜单迁移
-- 作用：后端ruoyi-generator移除后，禁用原代码生成页面及其按钮权限。
-- 安全：只修改sys_menu可见性和状态，不删除菜单、权限关联、gen_table或业务数据。
-- 说明：visible='1'为隐藏，status='1'为停用。本脚本可重复执行。

set @oc_generator_menu_id = (
    select menu_id
    from sys_menu
    where menu_type = 'C'
      and path = 'gen'
      and perms = 'tool:gen:list'
    order by menu_id
    limit 1
);

-- 页面和直属按钮全部停用，避免后端模块移除后仍生成动态路由或按钮权限。
update sys_menu
set visible = '1',
    status = '1',
    update_by = 'admin',
    update_time = sysdate(),
    remark = case
        when menu_id = @oc_generator_menu_id then 'OfficeCare已移除若依代码生成模块'
        else remark
    end
where menu_id = @oc_generator_menu_id
   or parent_id = @oc_generator_menu_id;

-- 验收查询：代码生成页面及其按钮应全部为隐藏、停用状态。
select menu_id, menu_name, parent_id, path, perms, menu_type, visible, status, remark
from sys_menu
where menu_id = @oc_generator_menu_id
   or parent_id = @oc_generator_menu_id
order by menu_type, order_num, menu_id;

-- 如需将代码生成模块恢复，必须先恢复ruoyi-generator源码和Maven依赖，再人工执行：
-- update sys_menu set visible = '0', status = '0' where menu_id = @oc_generator_menu_id or parent_id = @oc_generator_menu_id;
