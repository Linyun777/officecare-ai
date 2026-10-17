package com.ruoyi.officecare.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OfficeCare品牌对象 oc_brand
 *
 * @author officecare
 */
public class OcBrand extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 品牌ID */
    private Long brandId;

    /** 品牌编码 */
    private String brandCode;

    /** 品牌名称 */
    private String brandName;

    /** 品牌说明 */
    private String description;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getBrandId()
    {
        return brandId;
    }

    public void setBrandId(Long brandId)
    {
        this.brandId = brandId;
    }

    @NotBlank(message = "品牌编码不能为空")
    @Size(max = 50, message = "品牌编码长度不能超过50个字符")
    public String getBrandCode()
    {
        return brandCode;
    }

    public void setBrandCode(String brandCode)
    {
        this.brandCode = brandCode;
    }

    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 100, message = "品牌名称长度不能超过100个字符")
    public String getBrandName()
    {
        return brandName;
    }

    public void setBrandName(String brandName)
    {
        this.brandName = brandName;
    }

    @Size(max = 500, message = "品牌说明长度不能超过500个字符")
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    @NotNull(message = "显示顺序不能为空")
    @Min(value = 0, message = "显示顺序不能小于0")
    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    @NotBlank(message = "品牌状态不能为空")
    @Pattern(regexp = "^[01]$", message = "品牌状态值不正确")
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
            .append("brandId", getBrandId())
            .append("brandCode", getBrandCode())
            .append("brandName", getBrandName())
            .append("description", getDescription())
            .append("sortOrder", getSortOrder())
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
