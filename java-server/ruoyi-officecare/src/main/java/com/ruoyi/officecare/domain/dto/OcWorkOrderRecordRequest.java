package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 工单处理记录新增请求。 */
public class OcWorkOrderRecordRequest
{
    @NotBlank(message = "处理内容不能为空")
    @Size(max = 1000, message = "处理内容长度不能超过1000个字符")
    private String content;

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
