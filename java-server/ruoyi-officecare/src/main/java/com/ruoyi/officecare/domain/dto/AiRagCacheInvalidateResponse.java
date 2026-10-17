package com.ruoyi.officecare.domain.dto;

/** RAG缓存主动失效响应。 */
public class AiRagCacheInvalidateResponse
{
    private Long knowledgeBaseId;

    private Long revision;

    private String reason;

    private String invalidatedAt;

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getRevision()
    {
        return revision;
    }

    public void setRevision(Long revision)
    {
        this.revision = revision;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getInvalidatedAt()
    {
        return invalidatedAt;
    }

    public void setInvalidatedAt(String invalidatedAt)
    {
        this.invalidatedAt = invalidatedAt;
    }
}
