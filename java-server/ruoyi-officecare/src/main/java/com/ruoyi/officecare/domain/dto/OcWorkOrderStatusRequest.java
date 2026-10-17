package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 工单状态流转请求。 */
public class OcWorkOrderStatusRequest
{
    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "^[0123]$", message = "目标状态值不正确")
    private String targetStatus;

    @NotBlank(message = "处理说明不能为空")
    @Size(max = 1000, message = "处理说明长度不能超过1000个字符")
    private String content;

    public String getTargetStatus()
    {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus)
    {
        this.targetStatus = targetStatus;
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
