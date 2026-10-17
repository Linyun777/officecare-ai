package com.ruoyi.officecare.domain.vo;

import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;

/** 工单AI处理建议及其检索范围。 */
public class OcWorkOrderAiSuggestion
{
    private Long workOrderId;

    private String workOrderNo;

    private Long knowledgeBaseId;

    private String knowledgeBaseName;

    private String brandName;

    private String modelName;

    private AiRagAnswerResponse answer;

    public Long getWorkOrderId()
    {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId)
    {
        this.workOrderId = workOrderId;
    }

    public String getWorkOrderNo()
    {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo)
    {
        this.workOrderNo = workOrderNo;
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

    public AiRagAnswerResponse getAnswer()
    {
        return answer;
    }

    public void setAnswer(AiRagAnswerResponse answer)
    {
        this.answer = answer;
    }
}
