-- OfficeCare AI 第一轮产品化菜单收口
-- 作用：统一平台管理命名，隐藏未使用的若依演示、监控和开发工具入口。
-- 安全：只更新sys_menu展示元数据，不删除菜单、角色、权限或业务数据。
-- 说明：visible='0'为显示，visible='1'为隐藏。本脚本可重复执行。

-- 1、系统管理更名为平台管理，并将其放到OfficeCare业务菜单之后。
update sys_menu
set menu_name = '平台管理',
    order_num = 90,
    update_by = 'admin',
    update_time = sysdate(),
    remark = 'OfficeCare平台用户、权限、配置与审计管理'
where parent_id = 0
  and path = 'system'
  and menu_type = 'M';

-- 2、第一版不展示岗位和通知公告，但保留控制器、表结构和权限数据。
update sys_menu child
join sys_menu root on root.menu_id = child.parent_id
set child.visible = '1',
    child.update_by = 'admin',
    child.update_time = sysdate()
where root.parent_id = 0
  and root.path = 'system'
  and child.path in ('post', 'notice');

-- 3、隐藏监控和开发工具根目录；底层代码继续保留供开发诊断及后续评估。
update sys_menu
set visible = '1',
    update_by = 'admin',
    update_time = sysdate()
where parent_id = 0
  and menu_type = 'M'
  and path in ('monitor', 'tool');

-- 4、隐藏若依官网演示入口，不删除原框架来源和LICENSE说明。
update sys_menu
set visible = '1',
    update_by = 'admin',
    update_time = sysdate(),
    remark = '上游框架入口已从OfficeCare产品导航隐藏'
where parent_id = 0
  and menu_type = 'M'
  and path = 'http://ruoyi.vip';

-- 5、验收查询：OfficeCare应显示，平台管理仅管理员使用，其余根菜单为隐藏状态。
select menu_id, menu_name, parent_id, order_num, path, menu_type, visible, status
from sys_menu
where parent_id = 0
order by order_num, menu_id;

-- 如需回滚本次展示调整，可人工确认后执行以下语句：
-- update sys_menu set menu_name = '系统管理', order_num = 1 where parent_id = 0 and path = 'system' and menu_type = 'M';
-- update sys_menu child join sys_menu root on root.menu_id = child.parent_id set child.visible = '0' where root.parent_id = 0 and root.path = 'system' and child.path in ('post', 'notice');
-- update sys_menu set visible = '0' where parent_id = 0 and menu_type = 'M' and path in ('monitor', 'tool', 'http://ruoyi.vip');
