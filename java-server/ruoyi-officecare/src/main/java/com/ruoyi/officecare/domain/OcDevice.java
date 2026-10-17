package com.ruoyi.officecare.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OfficeCare设备档案对象 oc_device。
 *
 * 品牌由产品型号关联取得，数据库不重复保存brand_id。
 *
 * @author officecare
 */
public class OcDevice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 设备ID */
    private Long deviceId;

    /** 所属客户单位ID */
    private Long customerOrgId;

    /** 客户单位编码（查询展示字段） */
    private String customerCode;

    /** 客户单位名称（查询展示字段） */
    private String customerName;

    /** 品牌ID（查询、表单字段，不在设备表重复存储） */
    private Long brandId;

    /** 品牌名称（查询展示字段） */
    private String brandName;

    /** 产品型号ID */
    private Long modelId;

    /** 型号编码（查询展示字段） */
    private String modelCode;

    /** 型号名称（查询展示字段） */
    private String modelName;

    /** 设备名称 */
    private String deviceName;

    /** 设备序列号 */
    private String serialNumber;

    /** 安装位置 */
    private String installLocation;

    /** 购买日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date purchaseDate;

    /** 安装日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date installDate;

    /** 保修截止日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date warrantyEndDate;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(Long deviceId)
    {
        this.deviceId = deviceId;
    }

    @NotNull(message = "所属客户不能为空")
    public Long getCustomerOrgId()
    {
        return customerOrgId;
    }

    public void setCustomerOrgId(Long customerOrgId)
    {
        this.customerOrgId = customerOrgId;
    }

    public String getCustomerCode()
    {
        return customerCode;
    }

    public void setCustomerCode(String customerCode)
    {
        this.customerCode = customerCode;
    }

    public String getCustomerName()
    {
        return customerName;
    }

    public void setCustomerName(String customerName)
    {
        this.customerName = customerName;
    }

    @NotNull(message = "所属品牌不能为空")
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

    @NotNull(message = "产品型号不能为空")
    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
    }

    public String getModelCode()
    {
        return modelCode;
    }

    public void setModelCode(String modelCode)
    {
        this.modelCode = modelCode;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称长度不能超过100个字符")
    public String getDeviceName()
    {
        return deviceName;
    }

    public void setDeviceName(String deviceName)
    {
        this.deviceName = deviceName;
    }

    @NotBlank(message = "设备序列号不能为空")
    @Size(max = 100, message = "设备序列号长度不能超过100个字符")
    public String getSerialNumber()
    {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber)
    {
        this.serialNumber = serialNumber;
    }

    @Size(max = 255, message = "安装位置长度不能超过255个字符")
    public String getInstallLocation()
    {
        return installLocation;
    }

    public void setInstallLocation(String installLocation)
    {
        this.installLocation = installLocation;
    }

    public Date getPurchaseDate()
    {
        return purchaseDate;
    }

    public void setPurchaseDate(Date purchaseDate)
    {
        this.purchaseDate = purchaseDate;
    }

    public Date getInstallDate()
    {
        return installDate;
    }

    public void setInstallDate(Date installDate)
    {
        this.installDate = installDate;
    }

    public Date getWarrantyEndDate()
    {
        return warrantyEndDate;
    }

    public void setWarrantyEndDate(Date warrantyEndDate)
    {
        this.warrantyEndDate = warrantyEndDate;
    }

    @NotBlank(message = "设备状态不能为空")
    @Pattern(regexp = "^[01]$", message = "设备状态值不正确")
    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("deviceId", getDeviceId())
            .append("customerOrgId", getCustomerOrgId())
            .append("customerName", getCustomerName())
            .append("brandId", getBrandId())
            .append("brandName", getBrandName())
            .append("modelId", getModelId())
            .append("modelName", getModelName())
            .append("deviceName", getDeviceName())
            .append("serialNumber", getSerialNumber())
            .append("installLocation", getInstallLocation())
            .append("purchaseDate", getPurchaseDate())
            .append("installDate", getInstallDate())
            .append("warrantyEndDate", getWarrantyEndDate())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
