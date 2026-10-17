package com.ruoyi.officecare.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** 工作台中的待人工处理异常消息。 */
public class OfficeCareMqDeadEvent
{
    private Long outboxId;

    private String failureStage;

    private Long taskId;

    private Long documentVersionId;

    private String taskType;

    private Integer taskAttempt;

    private Integer retryCount;

    private String errorMessage;

    private String traceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deadTime;

    public Long getOutboxId()
    {
        return outboxId;
    }

    public void setOutboxId(Long outboxId)
    {
        this.outboxId = outboxId;
    }

    public String getFailureStage()
    {
        return failureStage;
    }

    public void setFailureStage(String failureStage)
    {
        this.failureStage = failureStage;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getDocumentVersionId()
    {
        return documentVersionId;
    }

    public void setDocumentVersionId(Long documentVersionId)
    {
        this.documentVersionId = documentVersionId;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(String taskType)
    {
        this.taskType = taskType;
    }

    public Integer getTaskAttempt()
    {
        return taskAttempt;
    }

    public void setTaskAttempt(Integer taskAttempt)
    {
        this.taskAttempt = taskAttempt;
    }

    public Integer getRetryCount()
    {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount)
    {
        this.retryCount = retryCount;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public Date getDeadTime()
    {
        return deadTime;
    }

    public void setDeadTime(Date deadTime)
    {
        this.deadTime = deadTime;
    }
}
