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
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;

/**
 * 将RabbitMQ死信确认归档到Outbox审计记录，随后ACK死信队列中的消息。
 * 人工重投以数据库审计记录为准，避免直接操作队列造成不可选择的重放。
 */
@Component
@ConditionalOnProperty(prefix = "officecare.messaging.rabbit", name = "enabled",
    havingValue = "true")
public class DocumentTaskDeadLetterConsumer
{
    private static final Logger log = LoggerFactory.getLogger(
        DocumentTaskDeadLetterConsumer.class);

    private final ObjectMapper objectMapper;

    private final OcOutboxEventMapper outboxMapper;

    public DocumentTaskDeadLetterConsumer(ObjectMapper objectMapper,
                                          OcOutboxEventMapper outboxMapper)
    {
        this.objectMapper = objectMapper;
        this.outboxMapper = outboxMapper;
    }

    @RabbitListener(queues = "${officecare.messaging.rabbit.dead-queue}")
    public void archive(Message amqpMessage, Channel channel) throws IOException
    {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        try
        {
            DocumentTaskMessage message = objectMapper.readValue(
                amqpMessage.getBody(), DocumentTaskMessage.class);
            String eventId = message == null ? null : message.getEventId();
            if (eventId == null || eventId.isBlank())
            {
                log.error("丢弃无法归档的非法死信，messageId={}",
                    amqpMessage.getMessageProperties().getMessageId());
                channel.basicAck(deliveryTag, false);
                return;
            }
            int updated = outboxMapper.markDeadLetterReceived(eventId);
            if (updated == 0)
            {
                log.warn("死信没有匹配的待处理Outbox记录，eventId={}，将直接确认",
                    eventId);
            }
            else
            {
                log.error("文档任务死信已归档，eventId={}，taskId={}，traceId={}",
                    eventId, message.getTaskId(), message.getTraceId());
            }
            channel.basicAck(deliveryTag, false);
        }
        catch (JsonProcessingException exception)
        {
            log.error("死信消息JSON无法解析，将确认并跳过，messageId={}",
                amqpMessage.getMessageProperties().getMessageId(), exception);
            channel.basicAck(deliveryTag, false);
        }
        catch (Exception exception)
        {
            log.error("死信归档失败，消息将重新入队，messageId={}",
                amqpMessage.getMessageProperties().getMessageId(), exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
