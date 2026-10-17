package com.ruoyi.officecare.domain.dto;

import java.util.List;

/**
 * FastAPI知识问答响应，同时作为管理端问答数据返回。
 *
 * @author officecare
 */
public class AiRagAnswerResponse
{
    private Boolean answered;

    private String answer;

    private String refusalReason;

    private List<Citation> citations;

    private Integer retrievedCount;

    private Double scoreThreshold;

    private String modelName;

    public Boolean getAnswered()
    {
        return answered;
    }

    public void setAnswered(Boolean answered)
    {
        this.answered = answered;
    }

    public String getAnswer()
    {
        return answer;
    }

    public void setAnswer(String answer)
    {
        this.answer = answer;
    }

    public String getRefusalReason()
    {
        return refusalReason;
    }

    public void setRefusalReason(String refusalReason)
    {
        this.refusalReason = refusalReason;
    }

    public List<Citation> getCitations()
    {
        return citations;
    }

    public void setCitations(List<Citation> citations)
    {
        this.citations = citations;
    }

    public Integer getRetrievedCount()
    {
        return retrievedCount;
    }

    public void setRetrievedCount(Integer retrievedCount)
    {
        this.retrievedCount = retrievedCount;
    }

    public Double getScoreThreshold()
    {
        return scoreThreshold;
    }

    public void setScoreThreshold(Double scoreThreshold)
    {
        this.scoreThreshold = scoreThreshold;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    /**
     * 单条可核验引用。
     */
    public static class Citation
    {
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
    }
}
