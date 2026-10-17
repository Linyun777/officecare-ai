package com.ruoyi.officecare.messaging;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;
import com.ruoyi.officecare.service.IOcDocumentIndexingService;
import com.ruoyi.officecare.service.IOcDocumentProcessingService;

/**
 * 可靠消费文档任务消息。任务表的状态和attempt_count共同承担幂等校验。
 *
 * @author officecare
 */
@Component
@ConditionalOnProperty(prefix = "officecare.messaging.rabbit", name = "enabled",
    havingValue = "true")
public class DocumentTaskRabbitConsumer
{
    private static final Logger log = LoggerFactory.getLogger(
        DocumentTaskRabbitConsumer.class);

    private final ObjectMapper objectMapper;

    private final OcDocumentTaskMapper taskMapper;

    private final OcOutboxEventMapper outboxMapper;

    private final IOcDocumentProcessingService processingService;

    private final IOcDocumentIndexingService indexingService;

    private final DocumentTaskRetryService retryService;

    public DocumentTaskRabbitConsumer(ObjectMapper objectMapper,
                                      OcDocumentTaskMapper taskMapper,
                                      OcOutboxEventMapper outboxMapper,
                                      IOcDocumentProcessingService processingService,
                                      IOcDocumentIndexingService indexingService,
                                      DocumentTaskRetryService retryService)
    {
        this.objectMapper = objectMapper;
        this.taskMapper = taskMapper;
        this.outboxMapper = outboxMapper;
        this.processingService = processingService;
        this.indexingService = indexingService;
        this.retryService = retryService;
    }

    @RabbitListener(queues = {
        "${officecare.messaging.rabbit.parse-queue}",
        "${officecare.messaging.rabbit.index-queue}",
        "${officecare.messaging.rabbit.delete-index-queue}"
    })
    public void consume(Message amqpMessage, Channel channel) throws IOException
    {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        try
        {
            DocumentTaskMessage message = objectMapper.readValue(
                amqpMessage.getBody(), DocumentTaskMessage.class);
            OcDocumentTask task = validateIdentity(message);
            if (task == null)
            {
                outboxMapper.markConsumeIgnored(message.getEventId(),
                    "TASK_NOT_FOUND");
                acknowledge(channel, deliveryTag);
                return;
            }

            if (DocumentProcessingConstants.STATUS_PENDING.equals(task.getStatus()))
            {
                if (!sameAttempt(task, message, 0))
                {
                    acknowledgeStale(channel, deliveryTag, message, task);
                    return;
                }
                beginConsumption(message, amqpMessage);
                execute(task);
                settleResult(channel, deliveryTag, message);
                return;
            }

            if (!sameAttempt(task, message, 1))
            {
                acknowledgeStale(channel, deliveryTag, message, task);
                return;
            }
            if (DocumentProcessingConstants.STATUS_SUCCESS.equals(task.getStatus()))
            {
                outboxMapper.markConsumeSuccess(message.getEventId());
                acknowledge(channel, deliveryTag);
                return;
            }
            if (DocumentProcessingConstants.STATUS_FAILED.equals(task.getStatus()))
            {
                settleFailed(channel, deliveryTag, message, task);
                return;
            }
            if (DocumentProcessingConstants.STATUS_RUNNING.equals(task.getStatus())
                && amqpMessage.getMessageProperties().isRedelivered())
            {
                beginConsumption(message, amqpMessage);
                if (retryService.recoverInterrupted(task))
                {
                    execute(taskMapper.selectTaskById(task.getTaskId()));
                    settleResult(channel, deliveryTag, message);
                    return;
                }
                if (retryService.failInterruptedIfExhausted(task))
                {
                    settleFailed(channel, deliveryTag, message,
                        taskMapper.selectTaskById(task.getTaskId()));
                    return;
                }
                outboxMapper.markConsumeIgnored(message.getEventId(),
                    "INTERRUPTED_TASK_ALREADY_RECOVERED");
                acknowledge(channel, deliveryTag);
                return;
            }

            acknowledge(channel, deliveryTag);
        }
        catch (JsonProcessingException exception)
        {
            rejectInvalidMessage(amqpMessage, channel, deliveryTag,
                "INVALID_MESSAGE_JSON", exception);
        }
        catch (InvalidDocumentTaskMessageException exception)
        {
            rejectInvalidMessage(amqpMessage, channel, deliveryTag,
                "INVALID_MESSAGE", exception);
        }
        catch (Exception exception)
        {
            log.error("消费文档任务消息发生基础设施异常，消息将重新入队，messageId={}",
                amqpMessage.getMessageProperties().getMessageId(), exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private OcDocumentTask validateIdentity(DocumentTaskMessage message)
    {
        if (message == null || message.getEventId() == null
            || message.getTaskId() == null || message.getDocumentVersionId() == null
            || message.getTaskType() == null || message.getTaskAttempt() == null)
        {
            throw new InvalidDocumentTaskMessageException("消息缺少必要字段");
        }
        try
        {
            DocumentTaskRouting.routingKey(message.getTaskType());
        }
        catch (RuntimeException exception)
        {
            throw new InvalidDocumentTaskMessageException("不支持的任务类型");
        }

        OcDocumentTask task = taskMapper.selectTaskById(message.getTaskId());
        if (task == null)
        {
            return null;
        }
        if (!message.getDocumentVersionId().equals(task.getDocumentVersionId())
            || !message.getTaskType().equals(task.getTaskType()))
        {
            throw new InvalidDocumentTaskMessageException("消息与数据库任务身份不一致");
        }
        return task;
    }

    private boolean sameAttempt(OcDocumentTask task, DocumentTaskMessage message,
                                int claimedIncrement)
    {
        return task.getAttemptCount() != null
            && task.getAttemptCount() == message.getTaskAttempt() + claimedIncrement;
    }

    private void execute(OcDocumentTask task)
    {
        if (DocumentProcessingConstants.TASK_TYPE_PARSE.equals(task.getTaskType()))
        {
            processingService.executeTask(task.getTaskId());
            return;
        }
        indexingService.executeTask(task.getTaskId());
    }

    private void settleResult(Channel channel, long deliveryTag,
                              DocumentTaskMessage message) throws IOException
    {
        OcDocumentTask result = taskMapper.selectTaskById(message.getTaskId());
        if (result == null)
        {
            outboxMapper.markConsumeIgnored(message.getEventId(),
                "TASK_REMOVED_DURING_EXECUTION");
            acknowledge(channel, deliveryTag);
            return;
        }
        if (DocumentProcessingConstants.STATUS_SUCCESS.equals(result.getStatus()))
        {
            outboxMapper.markConsumeSuccess(message.getEventId());
            acknowledge(channel, deliveryTag);
            return;
        }
        if (DocumentProcessingConstants.STATUS_FAILED.equals(result.getStatus()))
        {
            settleFailed(channel, deliveryTag, message, result);
            return;
        }
        throw new IllegalStateException("文档任务执行后没有进入终态");
    }

    private void settleFailed(Channel channel, long deliveryTag,
                              DocumentTaskMessage message,
                              OcDocumentTask failedTask) throws IOException
    {
        if (retryService.scheduleRetry(failedTask))
        {
            outboxMapper.markConsumeRetry(message.getEventId(),
                failureDescription(failedTask));
            log.warn("文档任务执行失败，已写入延迟重试Outbox，taskId={}，attempt={}",
                failedTask.getTaskId(), failedTask.getAttemptCount());
            acknowledge(channel, deliveryTag);
            return;
        }
        outboxMapper.markConsumeDead(message.getEventId(),
            failureDescription(failedTask));
        log.error("文档任务不可重试或已达到最大次数，进入死信队列，taskId={}，errorCode={}",
            failedTask.getTaskId(), failedTask.getErrorCode());
        reject(channel, deliveryTag);
    }

    private void acknowledgeStale(Channel channel, long deliveryTag,
                                  DocumentTaskMessage message,
                                  OcDocumentTask task) throws IOException
    {
        log.info("忽略过期的文档任务消息，taskId={}，messageAttempt={}，currentAttempt={}",
            task.getTaskId(), message.getTaskAttempt(), task.getAttemptCount());
        outboxMapper.markConsumeIgnored(message.getEventId(),
            "STALE_TASK_ATTEMPT");
        acknowledge(channel, deliveryTag);
    }

    /**
     * 非法消息只有在Outbox审计成功后才进入DLQ；数据库暂时不可用时保留消息重试。
     */
    private void rejectInvalidMessage(Message amqpMessage, Channel channel,
                                      long deliveryTag, String errorCode,
                                      Exception cause) throws IOException
    {
        String eventId = amqpMessage.getMessageProperties().getMessageId();
        String error = errorCode + ": " + failureMessage(cause);
        try
        {
            if (eventId != null && !eventId.isBlank())
            {
                outboxMapper.markConsumeDead(eventId, truncate(error));
            }
            log.error("拒绝非法文档任务消息，messageId={}，reason={}",
                eventId, cause.getMessage());
            reject(channel, deliveryTag);
        }
        catch (Exception exception)
        {
            log.error("非法消息死信审计失败，消息将重新入队，messageId={}",
                eventId, exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void beginConsumption(DocumentTaskMessage message, Message amqpMessage)
    {
        boolean redelivered = amqpMessage.getMessageProperties().isRedelivered();
        outboxMapper.markConsumeStarted(message.getEventId(), redelivered);
        log.info("开始消费文档任务，eventId={}，taskId={}，taskType={}，attempt={}，traceId={}，redelivered={}",
            message.getEventId(), message.getTaskId(), message.getTaskType(),
            message.getTaskAttempt(), message.getTraceId(), redelivered);
    }

    private String failureDescription(OcDocumentTask task)
    {
        String code = task == null || task.getErrorCode() == null
            ? "DOCUMENT_TASK_FAILED" : task.getErrorCode();
        String detail = task == null || task.getErrorMessage() == null
            ? "文档任务执行失败" : task.getErrorMessage();
        String value = code + ": " + detail;
        return truncate(value);
    }

    private String failureMessage(Exception exception)
    {
        return exception.getMessage() == null
            ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String truncate(String value)
    {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void acknowledge(Channel channel, long deliveryTag) throws IOException
    {
        channel.basicAck(deliveryTag, false);
    }

    private void reject(Channel channel, long deliveryTag) throws IOException
    {
        channel.basicReject(deliveryTag, false);
    }

    private static class InvalidDocumentTaskMessageException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        InvalidDocumentTaskMessageException(String message)
        {
            super(message);
        }
    }
}
