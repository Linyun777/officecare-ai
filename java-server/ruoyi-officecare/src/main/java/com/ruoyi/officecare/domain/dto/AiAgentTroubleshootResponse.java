package com.ruoyi.officecare.domain.dto;

import java.util.List;

/** FastAPI智能排障Agent响应。 */
public class AiAgentTroubleshootResponse
{
    private Boolean answered;
    private String summary;
    private List<String> troubleshootingSteps;
    private List<String> safetyWarnings;
    private Boolean requiresOnSiteService;
    private Boolean requiresHumanReview;
    private String refusalReason;
    private WorkOrderDraft draft;
    private List<AiRagAnswerResponse.Citation> citations;
    private List<ToolTrace> toolTrace;
    private String modelName;

    public Boolean getAnswered()
    {
        return answered;
    }

    public void setAnswered(Boolean answered)
    {
        this.answered = answered;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public List<String> getTroubleshootingSteps()
    {
        return troubleshootingSteps;
    }

    public void setTroubleshootingSteps(List<String> troubleshootingSteps)
    {
        this.troubleshootingSteps = troubleshootingSteps;
    }

    public List<String> getSafetyWarnings()
    {
        return safetyWarnings;
    }

    public void setSafetyWarnings(List<String> safetyWarnings)
    {
        this.safetyWarnings = safetyWarnings;
    }

    public Boolean getRequiresOnSiteService()
    {
        return requiresOnSiteService;
    }

    public void setRequiresOnSiteService(Boolean requiresOnSiteService)
    {
        this.requiresOnSiteService = requiresOnSiteService;
    }

    public Boolean getRequiresHumanReview()
    {
        return requiresHumanReview;
    }

    public void setRequiresHumanReview(Boolean requiresHumanReview)
    {
        this.requiresHumanReview = requiresHumanReview;
    }

    public String getRefusalReason()
    {
        return refusalReason;
    }

    public void setRefusalReason(String refusalReason)
    {
        this.refusalReason = refusalReason;
    }

    public WorkOrderDraft getDraft()
    {
        return draft;
    }

    public void setDraft(WorkOrderDraft draft)
    {
        this.draft = draft;
    }

    public List<AiRagAnswerResponse.Citation> getCitations()
    {
        return citations;
    }

    public void setCitations(List<AiRagAnswerResponse.Citation> citations)
    {
        this.citations = citations;
    }

    public List<ToolTrace> getToolTrace()
    {
        return toolTrace;
    }

    public void setToolTrace(List<ToolTrace> toolTrace)
    {
        this.toolTrace = toolTrace;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public static class WorkOrderDraft
    {
        private String title;
        private String faultDescription;
        private String priority;

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public String getFaultDescription()
        {
            return faultDescription;
        }

        public void setFaultDescription(String faultDescription)
        {
            this.faultDescription = faultDescription;
        }

        public String getPriority()
        {
            return priority;
        }

        public void setPriority(String priority)
        {
            this.priority = priority;
        }
    }

    public static class ToolTrace
    {
        private Integer sequence;
        private String toolName;
        private Boolean success;

        public Integer getSequence()
        {
            return sequence;
        }

        public void setSequence(Integer sequence)
        {
            this.sequence = sequence;
        }

        public String getToolName()
        {
            return toolName;
        }

        public void setToolName(String toolName)
        {
            this.toolName = toolName;
        }

        public Boolean getSuccess()
        {
            return success;
        }

        public void setSuccess(Boolean success)
        {
            this.success = success;
        }
    }
}
