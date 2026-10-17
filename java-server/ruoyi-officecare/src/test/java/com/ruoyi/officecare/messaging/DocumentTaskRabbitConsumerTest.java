package com.ruoyi.officecare.messaging;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;
import com.ruoyi.officecare.service.IOcDocumentIndexingService;
import com.ruoyi.officecare.service.IOcDocumentProcessingService;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文档任务RabbitMQ消费者测试。
 *
 * <p>重点验证第二阶段要求的任务路由、手动ACK/NACK、重复消息幂等和
 * Java进程中断后的重新投递恢复。</p>
 */
class DocumentTaskRabbitConsumerTest
{
    private static final long DELIVERY_TAG = 73L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OcDocumentTaskMapper taskMapper;

    private IOcDocumentProcessingService processingService;

    private OcOutboxEventMapper outboxMapper;

    private IOcDocumentIndexingService indexingService;

    private DocumentTaskRetryService retryService;

    private Channel channel;

    private DocumentTaskRabbitConsumer consumer;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(OcDocumentTaskMapper.class);
        processingService = mock(IOcDocumentProcessingService.class);
        outboxMapper = mock(OcOutboxEventMapper.class);
        indexingService = mock(IOcDocumentIndexingService.class);
        retryService = mock(DocumentTaskRetryService.class);
        channel = mock(Channel.class);
        consumer = new DocumentTaskRabbitConsumer(objectMapper, taskMapper,
            outboxMapper,
            processingService, indexingService, retryService);
    }

    @Test
    void shouldConsumeParseTaskAndAckOnlyAfterSuccess() throws Exception
    {
        OcDocumentTask pending = task(1L, 11L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_PENDING, 0, 3);
        OcDocumentTask success = task(1L, 11L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_SUCCESS, 1, 3);
        when(taskMapper.selectTaskById(1L)).thenReturn(pending, success);

        consumer.consume(message(pending, 0, false), channel);

        verify(processingService).executeTask(1L);
        verify(outboxMapper).markConsumeStarted("event-1", false);
        verify(outboxMapper).markConsumeSuccess("event-1");
        verifyNoInteractions(indexingService);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, true);
    }

    @Test
    void shouldRouteIndexTaskToIndexingService() throws Exception
    {
        assertIndexingTaskRoute(DocumentProcessingConstants.TASK_TYPE_INDEX, 2L);
    }

    @Test
    void shouldRouteDeleteIndexTaskToIndexingService() throws Exception
    {
        assertIndexingTaskRoute(
            DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX, 3L);
    }

    @Test
    void shouldAckDuplicateSuccessWithoutExecutingAgain() throws Exception
    {
        OcDocumentTask success = task(4L, 14L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_SUCCESS, 1, 3);
        when(taskMapper.selectTaskById(4L)).thenReturn(success);

        consumer.consume(message(success, 0, true), channel);

        verifyNoInteractions(processingService, indexingService, retryService);
        verify(outboxMapper).markConsumeSuccess("event-4");
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void shouldAckStaleAttemptWithoutExecutingAgain() throws Exception
    {
        OcDocumentTask current = task(5L, 15L,
            DocumentProcessingConstants.TASK_TYPE_INDEX,
            DocumentProcessingConstants.STATUS_PENDING, 2, 3);
        when(taskMapper.selectTaskById(5L)).thenReturn(current);

        consumer.consume(message(current, 0, true), channel);

        verifyNoInteractions(processingService, indexingService, retryService);
        verify(outboxMapper).markConsumeIgnored("event-5",
            "STALE_TASK_ATTEMPT");
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void shouldRecoverRedeliveredRunningTaskAfterJavaRestart() throws Exception
    {
        OcDocumentTask running = task(6L, 16L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_RUNNING, 1, 3);
        OcDocumentTask recoveredPending = task(6L, 16L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_PENDING, 1, 3);
        OcDocumentTask success = task(6L, 16L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_SUCCESS, 2, 3);
        when(taskMapper.selectTaskById(6L))
            .thenReturn(running, recoveredPending, success);
        when(retryService.recoverInterrupted(running)).thenReturn(true);

        consumer.consume(message(running, 0, true), channel);

        verify(retryService).recoverInterrupted(running);
        verify(processingService).executeTask(6L);
        verify(outboxMapper).markConsumeStarted("event-6", true);
        verify(outboxMapper).markConsumeSuccess("event-6");
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void shouldDeadLetterInterruptedTaskWhenAttemptBudgetIsExhausted()
        throws Exception
    {
        OcDocumentTask running = task(12L, 22L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_RUNNING, 3, 3);
        OcDocumentTask failed = task(12L, 22L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_FAILED, 3, 3);
        failed.setErrorCode("TASK_INTERRUPTED");
        failed.setErrorMessage("Java进程中断且任务已达到最大执行次数");
        when(taskMapper.selectTaskById(12L)).thenReturn(running, failed);
        when(retryService.recoverInterrupted(running)).thenReturn(false);
        when(retryService.failInterruptedIfExhausted(running)).thenReturn(true);
        when(retryService.scheduleRetry(failed)).thenReturn(false);

        consumer.consume(message(running, 2, true), channel);

        verify(retryService).failInterruptedIfExhausted(running);
        verify(outboxMapper).markConsumeDead("event-12",
            "TASK_INTERRUPTED: Java进程中断且任务已达到最大执行次数");
        verify(channel).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void shouldAckFailedTaskAfterRetryOutboxWasCreated() throws Exception
    {
        OcDocumentTask failed = task(7L, 17L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_FAILED, 1, 3);
        when(taskMapper.selectTaskById(7L)).thenReturn(failed);
        when(retryService.scheduleRetry(failed)).thenReturn(true);

        consumer.consume(message(failed, 0, false), channel);

        verify(retryService).scheduleRetry(failed);
        verify(outboxMapper).markConsumeRetry("event-7",
            "DOCUMENT_TASK_FAILED: 文档任务执行失败");
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void shouldRejectFailedTaskWhenItCannotBeRetried() throws Exception
    {
        OcDocumentTask failed = task(8L, 18L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_FAILED, 1, 1);
        when(taskMapper.selectTaskById(8L)).thenReturn(failed);
        when(retryService.scheduleRetry(failed)).thenReturn(false);

        consumer.consume(message(failed, 0, false), channel);

        verify(outboxMapper).markConsumeDead("event-8",
            "DOCUMENT_TASK_FAILED: 文档任务执行失败");
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void shouldRejectInvalidMessageWithoutRequeue() throws Exception
    {
        DocumentTaskMessage invalid = new DocumentTaskMessage();
        invalid.setTaskId(9L);
        MessageProperties properties = messageProperties(false);
        properties.setMessageId("event-invalid");
        Message message = new Message(objectMapper.writeValueAsBytes(invalid),
            properties);

        consumer.consume(message, channel);

        verify(outboxMapper).markConsumeDead("event-invalid",
            "INVALID_MESSAGE: 消息缺少必要字段");
        verify(channel).basicReject(DELIVERY_TAG, false);
        verifyNoInteractions(processingService, indexingService, retryService);
    }

    @Test
    void shouldRejectMalformedJsonIntoDeadLetterQueue() throws Exception
    {
        MessageProperties properties = messageProperties(false);
        properties.setMessageId("event-malformed");
        Message malformed = new Message("not-json".getBytes(
            java.nio.charset.StandardCharsets.UTF_8), properties);

        consumer.consume(malformed, channel);

        verify(outboxMapper).markConsumeDead(
            org.mockito.ArgumentMatchers.eq("event-malformed"),
            org.mockito.ArgumentMatchers.startsWith("INVALID_MESSAGE_JSON:"));
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, true);
    }

    @Test
    void shouldRequeueInvalidMessageWhenDeadAuditDatabaseIsUnavailable()
        throws Exception
    {
        DocumentTaskMessage invalid = new DocumentTaskMessage();
        invalid.setTaskId(13L);
        MessageProperties properties = messageProperties(false);
        properties.setMessageId("event-invalid-db");
        Message message = new Message(objectMapper.writeValueAsBytes(invalid),
            properties);
        when(outboxMapper.markConsumeDead(
            org.mockito.ArgumentMatchers.eq("event-invalid-db"),
            org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new IllegalStateException("database unavailable"));

        consumer.consume(message, channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void shouldNackAndRequeueInfrastructureFailure() throws Exception
    {
        OcDocumentTask pending = task(10L, 20L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_PENDING, 0, 3);
        when(taskMapper.selectTaskById(10L))
            .thenThrow(new IllegalStateException("database unavailable"));

        consumer.consume(message(pending, 0, false), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void shouldNackWhenManualAckItselfFails() throws Exception
    {
        OcDocumentTask success = task(11L, 21L,
            DocumentProcessingConstants.TASK_TYPE_PARSE,
            DocumentProcessingConstants.STATUS_SUCCESS, 1, 3);
        when(taskMapper.selectTaskById(11L)).thenReturn(success);
        doThrow(new IOException("connection lost"))
            .when(channel).basicAck(DELIVERY_TAG, false);

        consumer.consume(message(success, 0, false), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }

    private void assertIndexingTaskRoute(String taskType, Long taskId)
        throws Exception
    {
        OcDocumentTask pending = task(taskId, taskId + 10L, taskType,
            DocumentProcessingConstants.STATUS_PENDING, 0, 3);
        OcDocumentTask success = task(taskId, taskId + 10L, taskType,
            DocumentProcessingConstants.STATUS_SUCCESS, 1, 3);
        when(taskMapper.selectTaskById(taskId)).thenReturn(pending, success);

        consumer.consume(message(pending, 0, false), channel);

        verify(indexingService).executeTask(taskId);
        verify(outboxMapper).markConsumeStarted("event-" + taskId, false);
        verify(outboxMapper).markConsumeSuccess("event-" + taskId);
        verifyNoInteractions(processingService);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    private Message message(OcDocumentTask task, int messageAttempt,
                            boolean redelivered) throws Exception
    {
        DocumentTaskMessage payload = new DocumentTaskMessage();
        payload.setEventId("event-" + task.getTaskId());
        payload.setTaskId(task.getTaskId());
        payload.setDocumentVersionId(task.getDocumentVersionId());
        payload.setTaskType(task.getTaskType());
        payload.setTaskAttempt(messageAttempt);
        payload.setTraceId("trace-" + task.getTaskId());
        payload.setOccurredAt("2026-09-04T17:00:00Z");
        return new Message(objectMapper.writeValueAsBytes(payload),
            messageProperties(redelivered));
    }

    private MessageProperties messageProperties(boolean redelivered)
    {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setMessageId("message-test");
        properties.setRedelivered(redelivered);
        return properties;
    }

    private OcDocumentTask task(Long taskId, Long versionId, String taskType,
                                String status, int attemptCount,
                                int maxAttempts)
    {
        OcDocumentTask task = new OcDocumentTask();
        task.setTaskId(taskId);
        task.setDocumentVersionId(versionId);
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setAttemptCount(attemptCount);
        task.setMaxAttempts(maxAttempts);
        task.setTraceId("trace-" + taskId);
        return task;
    }
}
