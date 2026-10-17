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
 * OfficeCare知识库对象 oc_knowledge_base
 *
 * @author officecare
 */
public class OcKnowledgeBase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 知识库ID */
    private Long knowledgeBaseId;

    /** 知识库编码 */
    private String knowledgeBaseCode;

    /** 知识库名称 */
    private String knowledgeBaseName;

    /** 所属品牌ID */
    private Long brandId;

    /** 品牌名称（查询展示字段） */
    private String brandName;

    /** 所属产品型号ID */
    private Long modelId;

    /** 型号名称（查询展示字段） */
    private String modelName;

    /** 知识库说明 */
    private String description;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    @NotBlank(message = "知识库编码不能为空")
    @Size(max = 64, message = "知识库编码长度不能超过64个字符")
    public String getKnowledgeBaseCode()
    {
        return knowledgeBaseCode;
    }

    public void setKnowledgeBaseCode(String knowledgeBaseCode)
    {
        this.knowledgeBaseCode = knowledgeBaseCode;
    }

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称长度不能超过100个字符")
    public String getKnowledgeBaseName()
    {
        return knowledgeBaseName;
    }

    public void setKnowledgeBaseName(String knowledgeBaseName)
    {
        this.knowledgeBaseName = knowledgeBaseName;
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

    @NotNull(message = "所属产品型号不能为空")
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

    @Size(max = 500, message = "知识库说明长度不能超过500个字符")
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

    @NotBlank(message = "知识库状态不能为空")
    @Pattern(regexp = "^[01]$", message = "知识库状态值不正确")
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
            .append("knowledgeBaseId", getKnowledgeBaseId())
            .append("knowledgeBaseCode", getKnowledgeBaseCode())
            .append("knowledgeBaseName", getKnowledgeBaseName())
            .append("brandId", getBrandId())
            .append("brandName", getBrandName())
            .append("modelId", getModelId())
            .append("modelName", getModelName())
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
