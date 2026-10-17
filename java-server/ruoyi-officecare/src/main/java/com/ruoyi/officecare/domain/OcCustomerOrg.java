package com.ruoyi.officecare.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OfficeCare客户单位对象 oc_customer_org。
 *
 * @author officecare
 */
public class OcCustomerOrg extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 客户单位ID */
    private Long customerOrgId;

    /** 客户单位编码 */
    private String customerCode;

    /** 客户单位名称 */
    private String customerName;

    /** 主要联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 单位地址 */
    private String address;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getCustomerOrgId()
    {
        return customerOrgId;
    }

    public void setCustomerOrgId(Long customerOrgId)
    {
        this.customerOrgId = customerOrgId;
    }

    @NotBlank(message = "客户编码不能为空")
    @Size(max = 50, message = "客户编码长度不能超过50个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "客户编码只能包含字母、数字、下划线和短横线")
    public String getCustomerCode()
    {
        return customerCode;
    }

    public void setCustomerCode(String customerCode)
    {
        this.customerCode = customerCode;
    }

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 150, message = "客户名称长度不能超过150个字符")
    public String getCustomerName()
    {
        return customerName;
    }

    public void setCustomerName(String customerName)
    {
        this.customerName = customerName;
    }

    @Size(max = 50, message = "联系人长度不能超过50个字符")
    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    @Size(max = 30, message = "联系电话长度不能超过30个字符")
    public String getContactPhone()
    {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone)
    {
        this.contactPhone = contactPhone;
    }

    @Size(max = 255, message = "客户地址长度不能超过255个字符")
    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    @NotBlank(message = "客户状态不能为空")
    @Pattern(regexp = "^[01]$", message = "客户状态值不正确")
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
            .append("customerOrgId", getCustomerOrgId())
            .append("customerCode", getCustomerCode())
            .append("customerName", getCustomerName())
            .append("contactName", getContactName())
            .append("contactPhone", getContactPhone())
            .append("address", getAddress())
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
