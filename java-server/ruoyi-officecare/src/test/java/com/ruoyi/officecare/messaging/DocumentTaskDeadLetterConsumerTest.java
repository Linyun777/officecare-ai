package com.ruoyi.officecare.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 死信归档消费者的手动ACK与故障重入队测试。 */
class DocumentTaskDeadLetterConsumerTest
{
    private static final long DELIVERY_TAG = 91L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OcOutboxEventMapper outboxMapper;

    private Channel channel;

    private DocumentTaskDeadLetterConsumer consumer;

    @BeforeEach
    void setUp()
    {
        outboxMapper = mock(OcOutboxEventMapper.class);
        channel = mock(Channel.class);
        consumer = new DocumentTaskDeadLetterConsumer(objectMapper, outboxMapper);
    }

    @Test
    void shouldArchiveValidDeadLetterBeforeAck() throws Exception
    {
        when(outboxMapper.markDeadLetterReceived("dead-event-1")).thenReturn(1);

        consumer.archive(message(validPayload()), channel);

        verify(outboxMapper).markDeadLetterReceived("dead-event-1");
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, true);
    }

    @Test
    void shouldAckMalformedJsonToAvoidPoisonMessageLoop() throws Exception
    {
        consumer.archive(message("not-json"), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, true);
    }

    @Test
    void shouldNackAndRequeueWhenArchiveDatabaseIsUnavailable() throws Exception
    {
        when(outboxMapper.markDeadLetterReceived("dead-event-1"))
            .thenThrow(new IllegalStateException("database unavailable"));

        consumer.archive(message(validPayload()), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    private String validPayload() throws Exception
    {
        DocumentTaskMessage payload = new DocumentTaskMessage();
        payload.setEventId("dead-event-1");
        payload.setTaskId(1L);
        payload.setDocumentVersionId(2L);
        payload.setTaskType("parse");
        payload.setTaskAttempt(0);
        payload.setTraceId("parse-2-test");
        payload.setOccurredAt("2026-09-04T17:00:00Z");
        return objectMapper.writeValueAsString(payload);
    }

    private Message message(String payload)
    {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setMessageId("dead-message-test");
        return new Message(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            properties);
    }
}
