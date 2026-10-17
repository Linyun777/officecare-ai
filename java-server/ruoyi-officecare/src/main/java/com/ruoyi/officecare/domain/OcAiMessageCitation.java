package com.ruoyi.officecare.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AI消息引用对象 oc_ai_message_citation。
 *
 * @author officecare
 */
public class OcAiMessageCitation implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long citationId;

    private Long messageId;

    private Integer citationIndex;

    private Long knowledgeBaseId;

    private Long brandId;

    private Long modelId;

    private Long documentId;

    private Long documentVersionId;

    private String documentTitle;

    private String originalFileName;

    private Integer pageNumber;

    private Integer chunkIndex;

    private String heading;

    private String quote;

    private Double score;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getCitationId()
    {
        return citationId;
    }

    public void setCitationId(Long citationId)
    {
        this.citationId = citationId;
    }

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public Integer getCitationIndex()
    {
        return citationIndex;
    }

    public void setCitationIndex(Integer citationIndex)
    {
        this.citationIndex = citationIndex;
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

    public Long getDocumentId()
    {
        return documentId;
    }

    public void setDocumentId(Long documentId)
    {
        this.documentId = documentId;
    }

    public Long getDocumentVersionId()
    {
        return documentVersionId;
    }

    public void setDocumentVersionId(Long documentVersionId)
    {
        this.documentVersionId = documentVersionId;
    }

    public String getDocumentTitle()
    {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle)
    {
        this.documentTitle = documentTitle;
    }

    public String getOriginalFileName()
    {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName)
    {
        this.originalFileName = originalFileName;
    }

    public Integer getPageNumber()
    {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber)
    {
        this.pageNumber = pageNumber;
    }

    public Integer getChunkIndex()
    {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex)
    {
        this.chunkIndex = chunkIndex;
    }

    public String getHeading()
    {
        return heading;
    }

    public void setHeading(String heading)
    {
        this.heading = heading;
    }

    public String getQuote()
    {
        return quote;
    }

    public void setQuote(String quote)
    {
        this.quote = quote;
    }

    public Double getScore()
    {
        return score;
    }

    public void setScore(Double score)
    {
        this.score = score;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
