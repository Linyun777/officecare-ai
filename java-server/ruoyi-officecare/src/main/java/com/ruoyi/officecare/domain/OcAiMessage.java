package com.ruoyi.officecare.domain;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnResponse;

/**
 * AI助手消息对象 oc_ai_message。
 *
 * @author officecare
 */
public class OcAiMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long messageId;

    private Long conversationId;

    /** 客户端单轮幂等UUID。 */
    private String turnId;

    /** 消息角色（user/assistant）。 */
    private String role;

    private String content;

    private Boolean answered;

    private String refusalReason;

    private String modelName;

    private String traceId;

    /** 数据库存储的Agent结构化响应JSON，不直接暴露给浏览器。 */
    @JsonIgnore
    private String agentPayload;

    /** 从agentPayload恢复的结构化响应。 */
    private AiAssistantTurnResponse agentResult;

    /** 反馈（0未反馈 1有帮助 2无帮助）。 */
    private String feedbackType;

    private String feedbackComment;

    private List<OcAiMessageCitation> citations = new ArrayList<>();

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public Long getConversationId()
    {
        return conversationId;
    }

    public void setConversationId(Long conversationId)
    {
        this.conversationId = conversationId;
    }

    public String getTurnId()
    {
        return turnId;
    }

    public void setTurnId(String turnId)
    {
        this.turnId = turnId;
    }

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

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public String getAgentPayload()
    {
        return agentPayload;
    }

    public void setAgentPayload(String agentPayload)
    {
        this.agentPayload = agentPayload;
    }

    public AiAssistantTurnResponse getAgentResult()
    {
        return agentResult;
    }

    public void setAgentResult(AiAssistantTurnResponse agentResult)
    {
        this.agentResult = agentResult;
    }

    public String getFeedbackType()
    {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType)
    {
        this.feedbackType = feedbackType;
    }

    public String getFeedbackComment()
    {
        return feedbackComment;
    }

    public void setFeedbackComment(String feedbackComment)
    {
        this.feedbackComment = feedbackComment;
    }

    public List<OcAiMessageCitation> getCitations()
    {
        return citations;
    }

    public void setCitations(List<OcAiMessageCitation> citations)
    {
        this.citations = citations == null ? new ArrayList<>() : citations;
    }
}
