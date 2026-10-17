package com.ruoyi.officecare.domain;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OfficeCare售后工单对象 oc_work_order。
 *
 * @author officecare
 */
public class OcWorkOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long workOrderId;

    private String workOrderNo;

    private Long customerOrgId;

    private String customerName;

    private Long deviceId;

    private String deviceName;

    private String serialNumber;

    private Long brandId;

    private String brandName;

    private Long modelId;

    private String modelName;

    private String title;

    private String faultDescription;

    private String priority;

    private String status;

    private Long assigneeUserId;

    private String assigneeUserName;

    private String assigneeNickName;

    private Long reporterUserId;

    private String reporterUserName;

    private String reporterNickName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date completionTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date closeTime;

    private String delFlag;

    private List<OcWorkOrderRecord> records;

    public Long getWorkOrderId()
    {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId)
    {
        this.workOrderId = workOrderId;
    }

    public String getWorkOrderNo()
    {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo)
    {
        this.workOrderNo = workOrderNo;
    }

    @NotNull(message = "客户单位不能为空")
    public Long getCustomerOrgId()
    {
        return customerOrgId;
    }

    public void setCustomerOrgId(Long customerOrgId)
    {
        this.customerOrgId = customerOrgId;
    }

    public String getCustomerName()
    {
        return customerName;
    }

    public void setCustomerName(String customerName)
    {
        this.customerName = customerName;
    }

    @NotNull(message = "设备不能为空")
    public Long getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(Long deviceId)
    {
        this.deviceId = deviceId;
    }

    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    public String getSerialNumber()
    {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber)
    {
        this.serialNumber = serialNumber;
    }

    public Long getBrandId()
    {
        return brandId;
    }

    public void setBrandId(Long brandId)
    {
        this.brandId = brandId;
    }

    public String getBrandName()
    {
        return brandName;
    }

    public void setBrandName(String brandName)
    {
        this.brandName = brandName;
    }

    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getAssigneeUserId()
    {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId)
    {
        this.assigneeUserId = assigneeUserId;
    }

    public String getAssigneeUserName()
    {
        return assigneeUserName;
    }

    public void setAssigneeUserName(String assigneeUserName)
    {
        this.assigneeUserName = assigneeUserName;
    }

    public String getAssigneeNickName()
    {
        return assigneeNickName;
    }

    public void setAssigneeNickName(String assigneeNickName)
    {
        this.assigneeNickName = assigneeNickName;
    }

    public Long getReporterUserId()
    {
        return reporterUserId;
    }

    public void setReporterUserId(Long reporterUserId)
    {
        this.reporterUserId = reporterUserId;
    }

    public String getReporterUserName()
    {
        return reporterUserName;
    }

    public void setReporterUserName(String reporterUserName)
    {
        this.reporterUserName = reporterUserName;
    }

    public String getReporterNickName()
    {
        return reporterNickName;
    }

    public void setReporterNickName(String reporterNickName)
    {
        this.reporterNickName = reporterNickName;
    }

    public Date getCompletionTime()
    {
        return completionTime;
    }

    public void setCompletionTime(Date completionTime)
    {
        this.completionTime = completionTime;
    }

    public Date getCloseTime()
    {
        return closeTime;
    }

    public void setCloseTime(Date closeTime)
    {
        this.closeTime = closeTime;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public List<OcWorkOrderRecord> getRecords()
    {
        return records;
    }

    public void setRecords(List<OcWorkOrderRecord> records)
    {
        this.records = records;
    }
}
