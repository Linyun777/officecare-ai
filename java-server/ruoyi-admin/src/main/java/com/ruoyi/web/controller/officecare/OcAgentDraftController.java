package com.ruoyi.web.controller.officecare;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.officecare.domain.OcAgentDraft;
import com.ruoyi.officecare.domain.dto.OcAgentAnalyzeRequest;
import com.ruoyi.officecare.domain.dto.OcAgentDraftEditRequest;
import com.ruoyi.officecare.service.IOcAgentDraftService;
import com.ruoyi.officecare.service.IOcWorkOrderService;

/** 智能报修分析和人工确认草稿。 */
@RestController
@RequestMapping("/officecare/agent")
public class OcAgentDraftController extends BaseController
{
    private final IOcAgentDraftService agentDraftService;

    private final IOcWorkOrderService workOrderService;

    public OcAgentDraftController(IOcAgentDraftService agentDraftService,
        IOcWorkOrderService workOrderService)
    {
        this.agentDraftService = agentDraftService;
        this.workOrderService = workOrderService;
    }

    @PreAuthorize("@ss.hasPermi('officecare:agent:list')")
    @GetMapping("/draft/list")
    public TableDataInfo list(OcAgentDraft filter)
    {
        startPage();
        List<OcAgentDraft> list = agentDraftService.selectDraftList(
            filter, getUserId());
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('officecare:agent:list')")
    @GetMapping("/assignee-options")
    public AjaxResult assigneeOptions()
    {
        return success(workOrderService.selectAssigneeOptions());
    }

    @PreAuthorize("@ss.hasPermi('officecare:agent:query')")
    @GetMapping("/draft/{draftId}")
    public AjaxResult getInfo(@PathVariable Long draftId)
    {
        return success(agentDraftService.selectDraftById(draftId, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:agent:analyze')")
    @Log(title = "OfficeCare智能报修分析", businessType = BusinessType.INSERT)
    @PostMapping("/analyze")
    public AjaxResult analyze(
        @Validated @RequestBody OcAgentAnalyzeRequest request)
    {
        return success(agentDraftService.analyze(
            request, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:agent:edit')")
    @Log(title = "OfficeCare智能报修草稿", businessType = BusinessType.UPDATE)
    @PutMapping("/draft/{draftId}")
    public AjaxResult edit(@PathVariable Long draftId,
        @Validated @RequestBody OcAgentDraftEditRequest request)
    {
        return success(agentDraftService.updateDraft(
            draftId, request, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:agent:confirm')")
    @Log(title = "OfficeCare确认智能报修", businessType = BusinessType.INSERT)
    @PostMapping("/draft/{draftId}/confirm")
    public AjaxResult confirm(@PathVariable Long draftId,
        @Validated @RequestBody OcAgentDraftEditRequest request)
    {
        return success(agentDraftService.confirmDraft(
            draftId, request, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:agent:remove')")
    @Log(title = "OfficeCare取消智能报修草稿", businessType = BusinessType.DELETE)
    @DeleteMapping("/draft/{draftIds}")
    public AjaxResult cancel(@PathVariable Long[] draftIds)
    {
        return toAjax(agentDraftService.cancelDrafts(
            draftIds, getUserId(), getUsername()));
    }
}
