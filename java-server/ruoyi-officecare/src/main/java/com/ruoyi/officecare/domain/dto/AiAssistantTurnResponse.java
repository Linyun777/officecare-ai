package com.ruoyi.officecare.domain.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * FastAPI多轮AI助手的结构化单轮响应。
 *
 * @author officecare
 */
public class AiAssistantTurnResponse
{
    private String responseType;

    private String answer;

    private String diagnosticStatus;

    private String issueSummary;

    private List<String> attemptedSteps = new ArrayList<>();

    private List<String> observations = new ArrayList<>();

    private String nextQuestion;

    private Boolean requiresUserReply;

    private Boolean requiresOnSiteService;

    private Boolean answered;

    private String refusalReason;

    private List<AiRagAnswerResponse.Citation> citations = new ArrayList<>();

    private Integer retrievedCount;

    private Double scoreThreshold;

    private String modelName;

    public String getResponseType()
    {
        return responseType;
    }

    public void setResponseType(String responseType)
    {
        this.responseType = responseType;
    }

    public String getAnswer()
    {
        return answer;
    }

    public void setAnswer(String answer)
    {
        this.answer = answer;
    }

    public String getDiagnosticStatus()
    {
        return diagnosticStatus;
    }

    public void setDiagnosticStatus(String diagnosticStatus)
    {
        this.diagnosticStatus = diagnosticStatus;
    }

    public String getIssueSummary()
    {
        return issueSummary;
    }

    public void setIssueSummary(String issueSummary)
    {
        this.issueSummary = issueSummary;
    }

    public List<String> getAttemptedSteps()
    {
        return attemptedSteps;
    }

    public void setAttemptedSteps(List<String> attemptedSteps)
    {
        this.attemptedSteps = attemptedSteps;
    }

    public List<String> getObservations()
    {
        return observations;
    }

    public void setObservations(List<String> observations)
    {
        this.observations = observations;
    }

    public String getNextQuestion()
    {
        return nextQuestion;
    }

    public void setNextQuestion(String nextQuestion)
    {
        this.nextQuestion = nextQuestion;
    }

    public Boolean getRequiresUserReply()
    {
        return requiresUserReply;
    }

    public void setRequiresUserReply(Boolean requiresUserReply)
    {
        this.requiresUserReply = requiresUserReply;
    }

    public Boolean getRequiresOnSiteService()
    {
        return requiresOnSiteService;
    }

    public void setRequiresOnSiteService(Boolean requiresOnSiteService)
    {
        this.requiresOnSiteService = requiresOnSiteService;
    }

    public Boolean getAnswered()
    {
        return answered;
    }

    public void setAnswered(Boolean answered)
    {
        this.answered = answered;
    }

    public String getRefusalReason()
    {
        return refusalReason;
    }

    public void setRefusalReason(String refusalReason)
    {
        this.refusalReason = refusalReason;
    }

    public List<AiRagAnswerResponse.Citation> getCitations()
    {
        return citations;
    }

    public void setCitations(List<AiRagAnswerResponse.Citation> citations)
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
}
