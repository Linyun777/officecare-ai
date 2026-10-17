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
import com.ruoyi.officecare.domain.OcWorkOrder;
import com.ruoyi.officecare.domain.dto.OcWorkOrderAssigneeRequest;
import com.ruoyi.officecare.domain.dto.OcWorkOrderRecordRequest;
import com.ruoyi.officecare.domain.dto.OcWorkOrderStatusRequest;
import com.ruoyi.officecare.service.IOcWorkOrderService;

/** OfficeCare售后工单管理。 */
@RestController
@RequestMapping("/officecare/work-order")
public class OcWorkOrderController extends BaseController
{
    private final IOcWorkOrderService workOrderService;

    public OcWorkOrderController(IOcWorkOrderService workOrderService)
    {
        this.workOrderService = workOrderService;
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:list')")
    @GetMapping("/list")
    public TableDataInfo list(OcWorkOrder workOrder)
    {
        startPage();
        List<OcWorkOrder> list = workOrderService.selectWorkOrderList(workOrder);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:list')")
    @GetMapping("/assignee-options")
    public AjaxResult assigneeOptions()
    {
        return success(workOrderService.selectAssigneeOptions());
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:query')")
    @GetMapping("/{workOrderId}")
    public AjaxResult getInfo(@PathVariable Long workOrderId)
    {
        return success(workOrderService.selectWorkOrderById(workOrderId));
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:add')")
    @Log(title = "OfficeCare售后工单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OcWorkOrder workOrder)
    {
        return toAjax(workOrderService.insertWorkOrder(
            workOrder, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:edit')")
    @Log(title = "OfficeCare售后工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OcWorkOrder workOrder)
    {
        return toAjax(workOrderService.updateWorkOrder(
            workOrder, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:assign')")
    @Log(title = "OfficeCare工单指派", businessType = BusinessType.UPDATE)
    @PutMapping("/{workOrderId}/assignee")
    public AjaxResult assign(@PathVariable Long workOrderId,
        @Validated @RequestBody OcWorkOrderAssigneeRequest request)
    {
        return toAjax(workOrderService.assignWorkOrder(workOrderId,
            request, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:status')")
    @Log(title = "OfficeCare工单状态", businessType = BusinessType.UPDATE)
    @PutMapping("/{workOrderId}/status")
    public AjaxResult transition(@PathVariable Long workOrderId,
        @Validated @RequestBody OcWorkOrderStatusRequest request)
    {
        return toAjax(workOrderService.transitionStatus(workOrderId,
            request, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:record')")
    @Log(title = "OfficeCare工单记录", businessType = BusinessType.INSERT)
    @PostMapping("/{workOrderId}/records")
    public AjaxResult addRecord(@PathVariable Long workOrderId,
        @Validated @RequestBody OcWorkOrderRecordRequest request)
    {
        return toAjax(workOrderService.addHandlingRecord(workOrderId,
            request, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:ai')")
    @PostMapping("/{workOrderId}/ai-suggestion")
    public AjaxResult aiSuggestion(@PathVariable Long workOrderId)
    {
        return success(workOrderService.generateAiSuggestion(workOrderId));
    }

    @PreAuthorize("@ss.hasPermi('officecare:workorder:remove')")
    @Log(title = "OfficeCare售后工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workOrderIds}")
    public AjaxResult remove(@PathVariable Long[] workOrderIds)
    {
        return toAjax(workOrderService.deleteWorkOrderByIds(
            workOrderIds, getUsername()));
    }
}
