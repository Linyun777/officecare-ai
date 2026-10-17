package com.ruoyi.web.controller.officecare;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.officecare.service.IOfficeCareDashboardService;
import com.ruoyi.officecare.service.IOfficeCareMessagingService;

/**
 * OfficeCare工作台
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/dashboard")
public class OfficeCareDashboardController extends BaseController
{
    @Autowired
    private IOfficeCareDashboardService dashboardService;

    @Autowired
    private IOfficeCareMessagingService messagingService;

    /**
     * 获取工作台摘要
     */
    @PreAuthorize("@ss.hasPermi('officecare:dashboard:query')")
    @GetMapping("/summary")
    public AjaxResult getSummary()
    {
        return success(dashboardService.selectDashboardSummary());
    }

    /** 获取最近待人工处理的发布失败或消费死信。 */
    @PreAuthorize("@ss.hasPermi('officecare:dashboard:query')")
    @GetMapping("/mq/dead-letters")
    public AjaxResult getMqDeadLetters()
    {
        return success(messagingService.selectRecentDeadEvents());
    }

    /** 人工确认故障已排除后重新投递指定消息。 */
    @PreAuthorize("@ss.hasPermi('officecare:document:parse')")
    @Log(title = "文档任务异常消息", businessType = BusinessType.UPDATE)
    @PostMapping("/mq/dead-letters/{outboxId}/replay")
    public AjaxResult replayMqDeadLetter(@PathVariable Long outboxId)
    {
        messagingService.replayDeadEvent(outboxId, getUsername());
        return success("消息已重新进入可靠投递链路");
    }
}
