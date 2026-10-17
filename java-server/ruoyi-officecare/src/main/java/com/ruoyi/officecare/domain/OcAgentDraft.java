package com.ruoyi.officecare.domain;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.officecare.domain.dto.AiAgentTroubleshootResponse;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;

/** 智能报修分析结果和等待人工确认的工单草稿。 */
public class OcAgentDraft extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long draftId;
    private Long userId;
    private Long customerOrgId;
    private Long deviceId;
    private Long knowledgeBaseId;
    private String customerName;
    private String deviceName;
    private String serialNumber;
    private String brandName;
    private String modelName;
    private String knowledgeBaseName;
    private String issueDescription;
    private Boolean answered;
    private String summary;
    private String troubleshootingStepsJson;
    private String safetyWarningsJson;
    private Boolean requiresOnSiteService;
    private String refusalReason;
    private String draftTitle;
    private String draftFaultDescription;
    private String draftPriority;
    private Long assigneeUserId;
    private String assigneeUserName;
    private String assigneeNickName;
    private String workOrderRemark;
    private String citationsJson;
    private String toolTraceJson;
    private String modelNameUsed;
    private String status;
    private Long workOrderId;
    private String workOrderNo;
    private Date confirmedTime;
    private String delFlag;
    private List<String> troubleshootingSteps;
    private List<String> safetyWarnings;
    private List<AiRagAnswerResponse.Citation> citations;
    private List<AiAgentTroubleshootResponse.ToolTrace> toolTrace;

    public Long getDraftId() { return draftId; }
    public void setDraftId(Long draftId) { this.draftId = draftId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCustomerOrgId() { return customerOrgId; }
    public void setCustomerOrgId(Long customerOrgId) { this.customerOrgId = customerOrgId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getKnowledgeBaseName() { return knowledgeBaseName; }
    public void setKnowledgeBaseName(String knowledgeBaseName) { this.knowledgeBaseName = knowledgeBaseName; }
    public String getIssueDescription() { return issueDescription; }
    public void setIssueDescription(String issueDescription) { this.issueDescription = issueDescription; }
    public Boolean getAnswered() { return answered; }
    public void setAnswered(Boolean answered) { this.answered = answered; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    @JsonIgnore
    public String getTroubleshootingStepsJson() { return troubleshootingStepsJson; }
    public void setTroubleshootingStepsJson(String value) { this.troubleshootingStepsJson = value; }
    @JsonIgnore
    public String getSafetyWarningsJson() { return safetyWarningsJson; }
    public void setSafetyWarningsJson(String value) { this.safetyWarningsJson = value; }
    public Boolean getRequiresOnSiteService() { return requiresOnSiteService; }
    public void setRequiresOnSiteService(Boolean value) { this.requiresOnSiteService = value; }
    public Boolean getRequiresHumanReview() { return Boolean.TRUE; }
    public String getRefusalReason() { return refusalReason; }
    public void setRefusalReason(String refusalReason) { this.refusalReason = refusalReason; }
    public String getDraftTitle() { return draftTitle; }
    public void setDraftTitle(String draftTitle) { this.draftTitle = draftTitle; }
    public String getDraftFaultDescription() { return draftFaultDescription; }
    public void setDraftFaultDescription(String value) { this.draftFaultDescription = value; }
    public String getDraftPriority() { return draftPriority; }
    public void setDraftPriority(String draftPriority) { this.draftPriority = draftPriority; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Long assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getAssigneeUserName() { return assigneeUserName; }
    public void setAssigneeUserName(String value) { this.assigneeUserName = value; }
    public String getAssigneeNickName() { return assigneeNickName; }
    public void setAssigneeNickName(String value) { this.assigneeNickName = value; }
    public String getWorkOrderRemark() { return workOrderRemark; }
    public void setWorkOrderRemark(String value) { this.workOrderRemark = value; }
    @JsonIgnore
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }
    @JsonIgnore
    public String getToolTraceJson() { return toolTraceJson; }
    public void setToolTraceJson(String toolTraceJson) { this.toolTraceJson = toolTraceJson; }
    public String getModelNameUsed() { return modelNameUsed; }
    public void setModelNameUsed(String modelNameUsed) { this.modelNameUsed = modelNameUsed; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getWorkOrderNo() { return workOrderNo; }
    public void setWorkOrderNo(String workOrderNo) { this.workOrderNo = workOrderNo; }
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public Date getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(Date confirmedTime) { this.confirmedTime = confirmedTime; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public List<String> getTroubleshootingSteps() { return troubleshootingSteps; }
    public void setTroubleshootingSteps(List<String> value) { this.troubleshootingSteps = value; }
    public List<String> getSafetyWarnings() { return safetyWarnings; }
    public void setSafetyWarnings(List<String> value) { this.safetyWarnings = value; }
    public List<AiRagAnswerResponse.Citation> getCitations() { return citations; }
    public void setCitations(List<AiRagAnswerResponse.Citation> value) { this.citations = value; }
    public List<AiAgentTroubleshootResponse.ToolTrace> getToolTrace() { return toolTrace; }
    public void setToolTrace(List<AiAgentTroubleshootResponse.ToolTrace> value) { this.toolTrace = value; }
}
