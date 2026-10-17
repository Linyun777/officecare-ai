package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.Size;

/** 工单负责人调整请求；负责人为空表示取消指派。 */
public class OcWorkOrderAssigneeRequest
{
    private Long assigneeUserId;

    @Size(max = 500, message = "指派说明长度不能超过500个字符")
    private String content;

    public Long getAssigneeUserId()
    {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId)
    {
        this.assigneeUserId = assigneeUserId;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
