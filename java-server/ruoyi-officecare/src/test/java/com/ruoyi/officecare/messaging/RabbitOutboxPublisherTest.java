package com.ruoyi.officecare.messaging;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.ruoyi.officecare.domain.OcOutboxEvent;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Outbox 发布确认、延迟重试和最大次数状态转换测试。 */
class RabbitOutboxPublisherTest
{
    private OcOutboxEventMapper outboxMapper;

    private RabbitTemplate rabbitTemplate;

    private OfficeCareRabbitProperties properties;

    private RabbitOutboxPublisher publisher;

    @BeforeEach
    void setUp()
    {
        outboxMapper = mock(OcOutboxEventMapper.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        properties = new OfficeCareRabbitProperties();
        properties.setExchange("officecare.test.exchange");
        properties.setRetryInitialDelaySeconds(30L);
        properties.setRetryMaxDelaySeconds(300L);
        publisher = new RabbitOutboxPublisher(outboxMapper, rabbitTemplate,
            properties);
    }

    @Test
    void shouldMarkSentAfterPublisherConfirmAck()
    {
        OcOutboxEvent event = event(0, 3);
        ready(event);
        completeConfirm(true, null);

        publisher.publishReadyEvents();

        verify(outboxMapper).markSent(1L);
        verify(outboxMapper, never()).markRetry(anyLong(), anyInt(),
            any(Date.class), any());
        verify(outboxMapper, never()).markDead(anyLong(), anyInt(), any());
    }

    @Test
    void shouldScheduleDelayedRetryAfterTemporaryPublishFailure()
    {
        OcOutboxEvent event = event(0, 3);
        ready(event);
        completeConfirm(false, "broker unavailable");
        Date before = new Date();

        publisher.publishReadyEvents();

        ArgumentCaptor<Date> retryTime = ArgumentCaptor.forClass(Date.class);
        verify(outboxMapper).markRetry(eq(1L), eq(1), retryTime.capture(),
            any(String.class));
        assertTrue(retryTime.getValue().getTime() >= before.getTime() + 29_000L);
        verify(outboxMapper, never()).markDead(anyLong(), anyInt(), any());
    }

    @Test
    void shouldMarkDeadAfterMaximumPublishAttempts()
    {
        OcOutboxEvent event = event(2, 3);
        ready(event);
        completeConfirm(false, "broker unavailable");

        publisher.publishReadyEvents();

        verify(outboxMapper).markDead(eq(1L), eq(3), any(String.class));
        verify(outboxMapper, never()).markRetry(anyLong(), anyInt(),
            any(Date.class), any());
    }

    private void ready(OcOutboxEvent event)
    {
        when(outboxMapper.selectReadyEvents(properties.getOutboxBatchSize()))
            .thenReturn(List.of(event));
        when(outboxMapper.claimEvent(1L)).thenReturn(1);
    }

    private void completeConfirm(boolean ack, String reason)
    {
        doAnswer(invocation ->
        {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(
                new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(eq(properties.getExchange()),
            eq(DocumentTaskRouting.PARSE_ROUTING_KEY), any(Message.class),
            any(CorrelationData.class));
    }

    private OcOutboxEvent event(int retryCount, int maxRetryCount)
    {
        OcOutboxEvent event = new OcOutboxEvent();
        event.setOutboxId(1L);
        event.setEventId("event-1");
        event.setTaskId(10L);
        event.setRoutingKey(DocumentTaskRouting.PARSE_ROUTING_KEY);
        event.setPayloadJson("{\"taskId\":10}");
        event.setTraceId("parse-10-test");
        event.setRetryCount(retryCount);
        event.setMaxRetryCount(maxRetryCount);
        return event;
    }
}
