-- OfficeCare AI 剩余若依开发工具菜单退役迁移
-- 作用：停用已删除的表单构建和系统接口iframe页面。
-- 安全：只修改sys_menu可见性和状态，不删除菜单、角色关联或业务数据。
-- 说明：真正的OfficeCare OpenAPI后端端点继续保留，本脚本只停用若依前端菜单。

update sys_menu child
join sys_menu root on root.menu_id = child.parent_id
set child.visible = '1',
    child.status = '1',
    child.update_by = 'admin',
    child.update_time = sysdate(),
    child.remark = case
        when child.path = 'build' then 'OfficeCare已移除若依表单构建页面'
        when child.path = 'swagger' then 'OfficeCare已移除若依Swagger iframe页面'
        else child.remark
    end
where root.parent_id = 0
  and root.path = 'tool'
  and child.path in ('build', 'swagger');

-- 验收查询：build和swagger页面应全部为隐藏、停用状态。
select child.menu_id, child.menu_name, child.parent_id, child.path, child.component,
       child.perms, child.visible, child.status, child.remark
from sys_menu child
join sys_menu root on root.menu_id = child.parent_id
where root.parent_id = 0
  and root.path = 'tool'
  and child.path in ('build', 'swagger')
order by child.order_num, child.menu_id;

-- 如需恢复对应Vue页面后，可人工执行：
-- update sys_menu child join sys_menu root on root.menu_id = child.parent_id set child.visible = '0', child.status = '0' where root.parent_id = 0 and root.path = 'tool' and child.path in ('build', 'swagger');
