package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 人工编辑或确认智能报修草稿时提交的工单字段。 */
public class OcAgentDraftEditRequest
{
    private String title;

    private String faultDescription;

    private String priority;

    private Long assigneeUserId;

    private String workOrderRemark;

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 150, message = "工单标题长度不能超过150个字符")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    @NotBlank(message = "故障描述不能为空")
    @Size(max = 2000, message = "故障描述长度不能超过2000个字符")
    public String getFaultDescription()
    {
        return faultDescription;
    }

    public void setFaultDescription(String faultDescription)
    {
        this.faultDescription = faultDescription;
    }

    @NotBlank(message = "优先级不能为空")
    @Pattern(regexp = "^[0123]$", message = "工单优先级值不正确")
    public String getPriority()
    {
        return priority;
    }

    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    public Long getAssigneeUserId()
    {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId)
    {
        this.assigneeUserId = assigneeUserId;
    }

    @Size(max = 500, message = "工单备注长度不能超过500个字符")
    public String getWorkOrderRemark()
    {
        return workOrderRemark;
    }

    public void setWorkOrderRemark(String workOrderRemark)
    {
        this.workOrderRemark = workOrderRemark;
    }
}
