package com.ruoyi.officecare.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI助手会话对象 oc_ai_conversation。
 *
 * @author officecare
 */
public class OcAiConversation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long conversationId;

    private Long knowledgeBaseId;

    private String knowledgeBaseName;

    private Long brandId;

    private String brandName;

    private Long modelId;

    private String modelName;

    private Long userId;

    private String title;

    /** 状态（0正常 1归档）。 */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastMessageTime;

    /** 删除标志（0存在 2删除）。 */
    private String delFlag;

    public Long getConversationId()
    {
        return conversationId;
    }

    public void setConversationId(Long conversationId)
    {
        this.conversationId = conversationId;
    }

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getKnowledgeBaseName()
    {
        return knowledgeBaseName;
    }

    public void setKnowledgeBaseName(String knowledgeBaseName)
    {
        this.knowledgeBaseName = knowledgeBaseName;
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

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getLastMessageTime()
    {
        return lastMessageTime;
    }

    public void setLastMessageTime(Date lastMessageTime)
    {
        this.lastMessageTime = lastMessageTime;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
