package com.ruoyi.officecare.messaging;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.constant.OutboxConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcOutboxEvent;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;

/**
 * 在业务事务中写入文档任务Outbox事件。
 *
 * @author officecare
 */
@Service
public class DocumentTaskOutboxService
{
    private final OcOutboxEventMapper outboxMapper;

    private final ObjectMapper objectMapper;

    private final OfficeCareRabbitProperties properties;

    public DocumentTaskOutboxService(OcOutboxEventMapper outboxMapper,
                                     ObjectMapper objectMapper,
                                     OfficeCareRabbitProperties properties)
    {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean isEnabled()
    {
        return properties.isEnabled();
    }

    public void enqueue(OcDocumentTask task)
    {
        enqueue(task, 0L);
    }

    public void enqueue(OcDocumentTask task, long delaySeconds)
    {
        requireTask(task);
        int taskAttempt = task.getAttemptCount() == null ? 0 : task.getAttemptCount();
        String eventId = UUID.randomUUID().toString();

        DocumentTaskMessage message = new DocumentTaskMessage();
        message.setEventId(eventId);
        message.setTaskId(task.getTaskId());
        message.setDocumentVersionId(task.getDocumentVersionId());
        message.setTaskType(task.getTaskType());
        message.setTaskAttempt(taskAttempt);
        message.setTraceId(task.getTraceId());
        message.setOccurredAt(Instant.now().toString());

        OcOutboxEvent event = new OcOutboxEvent();
        event.setEventId(eventId);
        event.setDedupKey("document-task:" + task.getTaskId()
            + ":attempt:" + taskAttempt);
        event.setTaskId(task.getTaskId());
        event.setDocumentVersionId(task.getDocumentVersionId());
        event.setTaskType(task.getTaskType());
        event.setTaskAttempt(taskAttempt);
        event.setRoutingKey(DocumentTaskRouting.routingKey(task.getTaskType()));
        event.setPayloadJson(toJson(message));
        event.setTraceId(task.getTraceId());
        event.setStatus(OutboxConstants.STATUS_PENDING);
        event.setRetryCount(0);
        event.setMaxRetryCount(properties.getMaxPublishAttempts());
        event.setNextRetryTime(Date.from(Instant.now().plusSeconds(
            Math.max(0L, delaySeconds))));
        if (outboxMapper.insertOutboxEvent(event) == 0)
        {
            // 同轮待发/已发事件保持幂等；只有达到发布上限的事件允许人工重提后复活。
            outboxMapper.reactivateDeadEvent(event);
        }
    }

    private String toJson(DocumentTaskMessage message)
    {
        try
        {
            return objectMapper.writeValueAsString(message);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("文档任务消息序列化失败");
        }
    }

    private void requireTask(OcDocumentTask task)
    {
        if (task == null || task.getTaskId() == null
            || task.getDocumentVersionId() == null
            || task.getTaskType() == null || task.getTraceId() == null)
        {
            throw new ServiceException("文档任务消息缺少必要字段");
        }
    }
}
