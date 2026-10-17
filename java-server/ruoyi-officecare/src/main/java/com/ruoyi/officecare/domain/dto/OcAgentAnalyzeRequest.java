package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 发起智能报修分析的管理端请求。 */
public class OcAgentAnalyzeRequest
{
    private Long deviceId;

    private String issueDescription;

    @NotNull(message = "设备不能为空")
    public Long getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(Long deviceId)
    {
        this.deviceId = deviceId;
    }

    @NotBlank(message = "报修描述不能为空")
    @Size(min = 2, max = 2000, message = "报修描述长度必须在2到2000个字符之间")
    public String getIssueDescription()
    {
        return issueDescription;
    }

    public void setIssueDescription(String issueDescription)
    {
        this.issueDescription = issueDescription;
    }
}
