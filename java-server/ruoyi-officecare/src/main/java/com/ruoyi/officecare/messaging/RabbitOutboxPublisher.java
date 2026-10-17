package com.ruoyi.officecare.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.domain.OcOutboxEvent;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;

/**
 * 轮询Transactional Outbox，并在RabbitMQ确认接收后标记事件已发送。
 *
 * @author officecare
 */
@Component
@ConditionalOnProperty(prefix = "officecare.messaging.rabbit", name = "enabled",
    havingValue = "true")
public class RabbitOutboxPublisher
{
    private static final Logger log = LoggerFactory.getLogger(RabbitOutboxPublisher.class);

    private static final int MAX_ERROR_LENGTH = 500;

    private final OcOutboxEventMapper outboxMapper;

    private final RabbitTemplate rabbitTemplate;

    private final OfficeCareRabbitProperties properties;

    public RabbitOutboxPublisher(OcOutboxEventMapper outboxMapper,
                                 RabbitTemplate rabbitTemplate,
                                 OfficeCareRabbitProperties properties)
    {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString =
        "${officecare.messaging.rabbit.outbox-poll-interval-ms:1000}")
    public void publishReadyEvents()
    {
        recoverInterruptedPublishers();
        List<OcOutboxEvent> events = outboxMapper.selectReadyEvents(
            properties.getOutboxBatchSize());
        for (OcOutboxEvent event : events)
        {
            if (outboxMapper.claimEvent(event.getOutboxId()) == 0)
            {
                continue;
            }
            publish(event);
        }
    }

    private void publish(OcOutboxEvent event)
    {
        try
        {
            CorrelationData correlationData = new CorrelationData(event.getEventId());
            Message message = MessageBuilder
                .withBody(event.getPayloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.getEventId())
                .setHeader("officecare_task_id", event.getTaskId())
                .setHeader("officecare_trace_id", event.getTraceId())
                .build();

            rabbitTemplate.send(properties.getExchange(), event.getRoutingKey(),
                message, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                properties.getPublisherConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck())
            {
                throw new IllegalStateException("RabbitMQ拒绝消息："
                    + StringUtils.defaultIfEmpty(confirm.getReason(), "未知原因"));
            }
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null)
            {
                throw new IllegalStateException("RabbitMQ无法路由消息："
                    + returned.getReplyText());
            }
            outboxMapper.markSent(event.getOutboxId());
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            recordFailure(event, exception);
        }
        catch (Exception exception)
        {
            recordFailure(event, exception);
        }
    }

    private void recordFailure(OcOutboxEvent event, Exception exception)
    {
        int retryCount = valueOrZero(event.getRetryCount()) + 1;
        String error = truncate(StringUtils.defaultIfEmpty(
            exception.getMessage(), exception.getClass().getSimpleName()));
        if (retryCount >= event.getMaxRetryCount())
        {
            outboxMapper.markDead(event.getOutboxId(), retryCount, error);
            log.error("Outbox消息达到最大发布次数，eventId={}，taskId={}，error={}",
                event.getEventId(), event.getTaskId(), error);
            return;
        }
        long delay = properties.retryDelaySeconds(retryCount);
        outboxMapper.markRetry(event.getOutboxId(), retryCount,
            Date.from(Instant.now().plusSeconds(delay)), error);
        log.warn("Outbox消息发布失败，稍后重试，eventId={}，taskId={}，retryCount={}，error={}",
            event.getEventId(), event.getTaskId(), retryCount, error);
    }

    private void recoverInterruptedPublishers()
    {
        Date cutoff = Date.from(Instant.now().minusSeconds(
            properties.getPublishingStaleSeconds()));
        int recovered = outboxMapper.recoverStalePublishing(cutoff);
        if (recovered > 0)
        {
            log.warn("恢复{}条因发布进程中断而滞留的Outbox消息", recovered);
        }
    }

    private int valueOrZero(Integer value)
    {
        return value == null ? 0 : value;
    }

    private String truncate(String value)
    {
        return value.length() <= MAX_ERROR_LENGTH
            ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
