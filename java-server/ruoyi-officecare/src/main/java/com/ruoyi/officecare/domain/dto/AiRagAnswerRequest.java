package com.ruoyi.officecare.domain.dto;

import java.util.List;

/**
 * Java调用FastAPI的RAG问答请求。
 *
 * @author officecare
 */
public class AiRagAnswerRequest
{
    private String question;

    private Long knowledgeBaseId;

    private Long brandId;

    private Long modelId;

    /** 当前知识库关联的品牌名称，用于阻止跨品牌回答。 */
    private String brandName;

    /** 当前知识库关联的产品型号，用于阻止跨型号回答。 */
    private String modelName;

    /** Java筛选出的当前有效且索引成功的文档版本。 */
    private List<Long> documentVersionIds;

    private Integer topK;

    private Double scoreThreshold;

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
}
