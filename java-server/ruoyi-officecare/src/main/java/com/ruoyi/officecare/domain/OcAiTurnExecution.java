package com.ruoyi.officecare.domain;

import java.util.Date;

/**
 * AI助手单轮执行状态对象 oc_ai_turn_execution。
 *
 * Java在调用FastAPI前通过唯一索引原子占用同一会话的同一turnId，
 * 并把Agent结果快照保存在这里，用于落库失败后的补偿和重复请求重放。
 *
 * @author officecare
 */
public class OcAiTurnExecution
{
    private Long executionId;

    private Long conversationId;

    private String turnId;

    private Long userId;

    private String questionHash;

    private String status;

    private String ownerToken;

    private Integer attemptCount;

    private Date leaseUntil;

    private String traceId;

    private String resultPayload;

    private Long userMessageId;

    private Long assistantMessageId;

    private String errorCode;

    private String errorMessage;

    private Date createTime;

    private Date updateTime;

    public Long getExecutionId()
    {
        return executionId;
    }

    public void setExecutionId(Long executionId)
    {
        this.executionId = executionId;
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

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getQuestionHash()
    {
        return questionHash;
    }

    public void setQuestionHash(String questionHash)
    {
        this.questionHash = questionHash;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getOwnerToken()
    {
        return ownerToken;
    }

    public void setOwnerToken(String ownerToken)
    {
        this.ownerToken = ownerToken;
    }

    public Integer getAttemptCount()
    {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount)
    {
        this.attemptCount = attemptCount;
    }

    public Date getLeaseUntil()
    {
        return leaseUntil;
    }

    public void setLeaseUntil(Date leaseUntil)
    {
        this.leaseUntil = leaseUntil;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public String getResultPayload()
    {
        return resultPayload;
    }

    public void setResultPayload(String resultPayload)
    {
        this.resultPayload = resultPayload;
    }

    public Long getUserMessageId()
    {
        return userMessageId;
    }

    public void setUserMessageId(Long userMessageId)
    {
        this.userMessageId = userMessageId;
    }

    public Long getAssistantMessageId()
    {
        return assistantMessageId;
    }

    public void setAssistantMessageId(Long assistantMessageId)
    {
        this.assistantMessageId = assistantMessageId;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode(String errorCode)
    {
        this.errorCode = errorCode;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}