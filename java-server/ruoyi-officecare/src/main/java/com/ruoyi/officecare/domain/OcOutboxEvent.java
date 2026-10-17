package com.ruoyi.officecare.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * OfficeCare可靠消息事件对象 oc_outbox_event。
 *
 * @author officecare
 */
public class OcOutboxEvent implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long outboxId;

    private String eventId;

    private String dedupKey;

    private Long taskId;

    private Long documentVersionId;

    private String taskType;

    private Integer taskAttempt;

    private String routingKey;

    private String payloadJson;

    private String traceId;

    private String status;

    private Integer retryCount;

    private Integer maxRetryCount;

    private Date nextRetryTime;

    private String lastError;

    private Date sentTime;

    /** 消费状态（0待消费 1处理中 2成功 3已安排重试 4死信 5忽略 6已人工重投） */
    private String consumeStatus;

    /** 最近消费错误 */
    private String consumeError;

    /** 首次开始消费时间 */
    private Date consumedTime;

    /** 本次消费完成时间 */
    private Date completedTime;

    /** 消息从Rabbit确认到开始消费的等待毫秒数 */
    private Long waitTimeMs;

    /** 消费处理毫秒数 */
    private Long processTimeMs;

    /** RabbitMQ重新投递次数 */
    private Integer redeliveryCount;

    /** 消息进入死信队列并被归档的时间 */
    private Date deadLetterTime;

    /** 人工重新投递时间 */
    private Date replayedTime;

    /** 人工重新投递操作人 */
    private String replayedBy;

    private Date createTime;

    private Date updateTime;

    public Long getOutboxId()
    {
        return outboxId;
    }

    public void setOutboxId(Long outboxId)
    {
        this.outboxId = outboxId;
    }

    public String getEventId()
    {
        return eventId;
    }

    public void setEventId(String eventId)
    {
        this.eventId = eventId;
    }

    public String getDedupKey()
    {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey)
    {
        this.dedupKey = dedupKey;
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

    public String getRoutingKey()
    {
        return routingKey;
    }

    public void setRoutingKey(String routingKey)
    {
        this.routingKey = routingKey;
    }

    public String getPayloadJson()
    {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson)
    {
        this.payloadJson = payloadJson;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getRetryCount()
    {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount)
    {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetryCount()
    {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount)
    {
        this.maxRetryCount = maxRetryCount;
    }

    public Date getNextRetryTime()
    {
        return nextRetryTime;
    }

    public void setNextRetryTime(Date nextRetryTime)
    {
        this.nextRetryTime = nextRetryTime;
    }

    public String getLastError()
    {
        return lastError;
    }

    public void setLastError(String lastError)
    {
        this.lastError = lastError;
    }

    public Date getSentTime()
    {
        return sentTime;
    }

    public void setSentTime(Date sentTime)
    {
        this.sentTime = sentTime;
    }

    public String getConsumeStatus()
    {
        return consumeStatus;
    }

    public void setConsumeStatus(String consumeStatus)
    {
        this.consumeStatus = consumeStatus;
    }

    public String getConsumeError()
    {
        return consumeError;
    }

    public void setConsumeError(String consumeError)
    {
        this.consumeError = consumeError;
    }

    public Date getConsumedTime()
    {
        return consumedTime;
    }

    public void setConsumedTime(Date consumedTime)
    {
        this.consumedTime = consumedTime;
    }

    public Date getCompletedTime()
    {
        return completedTime;
    }

    public void setCompletedTime(Date completedTime)
    {
        this.completedTime = completedTime;
    }

    public Long getWaitTimeMs()
    {
        return waitTimeMs;
    }

    public void setWaitTimeMs(Long waitTimeMs)
    {
        this.waitTimeMs = waitTimeMs;
    }

    public Long getProcessTimeMs()
    {
        return processTimeMs;
    }

    public void setProcessTimeMs(Long processTimeMs)
    {
        this.processTimeMs = processTimeMs;
    }

    public Integer getRedeliveryCount()
    {
        return redeliveryCount;
    }

    public void setRedeliveryCount(Integer redeliveryCount)
    {
        this.redeliveryCount = redeliveryCount;
    }

    public Date getDeadLetterTime()
    {
        return deadLetterTime;
    }

    public void setDeadLetterTime(Date deadLetterTime)
    {
        this.deadLetterTime = deadLetterTime;
    }

    public Date getReplayedTime()
    {
        return replayedTime;
    }

    public void setReplayedTime(Date replayedTime)
    {
        this.replayedTime = replayedTime;
    }

    public String getReplayedBy()
    {
        return replayedBy;
    }

    public void setReplayedBy(String replayedBy)
    {
        this.replayedBy = replayedBy;
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
