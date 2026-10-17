package com.ruoyi.officecare.domain.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Java传给FastAPI多轮AI助手的单轮请求。
 *
 * @author officecare
 */
public class AiAssistantTurnRequest
{
    private Long conversationId;

    private Long userId;

    private String turnId;

    private String question;

    private Long knowledgeBaseId;

    private Long brandId;

    private Long modelId;

    private String brandName;

    private String modelName;

    private List<Long> documentVersionIds = new ArrayList<>();

    private Integer topK;

    private Double scoreThreshold;

    private List<BootstrapMessage> bootstrapMessages = new ArrayList<>();

    public Long getConversationId()
    {
        return conversationId;
    }

    public void setConversationId(Long conversationId)
    {
        this.conversationId = conversationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTurnId()
    {
        return turnId;
    }

    public void setTurnId(String turnId)
    {
        this.turnId = turnId;
    }

    public String getQuestion()
    {
        return question;
    }

    public void setQuestion(String question)
    {
        this.question = question;
    }

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getBrandId()
    {
        return brandId;
    }

    public void setBrandId(Long brandId)
    {
        this.brandId = brandId;
    }

    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
    }

    public String getBrandName()
    {
        return brandName;
    }

    public void setBrandName(String brandName)
    {
        this.brandName = brandName;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public List<Long> getDocumentVersionIds()
    {
        return documentVersionIds;
    }

    public void setDocumentVersionIds(List<Long> documentVersionIds)
    {
        this.documentVersionIds = documentVersionIds;
    }

    public Integer getTopK()
    {
        return topK;
    }

    public void setTopK(Integer topK)
    {
        this.topK = topK;
    }

    public Double getScoreThreshold()
    {
        return scoreThreshold;
    }

    public void setScoreThreshold(Double scoreThreshold)
    {
        this.scoreThreshold = scoreThreshold;
    }

    public List<BootstrapMessage> getBootstrapMessages()
    {
        return bootstrapMessages;
    }

    public void setBootstrapMessages(List<BootstrapMessage> bootstrapMessages)
    {
        this.bootstrapMessages = bootstrapMessages;
    }

    public static class BootstrapMessage
    {
        private String role;

        private String content;

        public String getRole()
        {
            return role;
        }

        public void setRole(String role)
        {
            this.role = role;
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
}
