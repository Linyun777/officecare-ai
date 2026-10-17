package com.ruoyi.officecare.messaging;

/**
 * RabbitMQ中传输的轻量文档任务消息，不包含原始文件或解析正文。
 *
 * @author officecare
 */
public class DocumentTaskMessage
{
    private String eventId;

    private Long taskId;

    private Long documentVersionId;

    private String taskType;

    private Integer taskAttempt;

    private String traceId;

    private String occurredAt;

    public String getEventId()
    {
        return eventId;
    }

    public void setEventId(String eventId)
    {
        this.eventId = eventId;
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

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public String getOccurredAt()
    {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt)
    {
        this.occurredAt = occurredAt;
    }
}
