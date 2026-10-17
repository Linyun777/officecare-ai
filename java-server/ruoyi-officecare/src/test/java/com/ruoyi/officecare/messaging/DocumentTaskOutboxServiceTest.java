package com.ruoyi.officecare.messaging;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcOutboxEvent;
import com.ruoyi.officecare.domain.vo.OfficeCareMqDeadEvent;
import com.ruoyi.officecare.domain.vo.OfficeCareMqMetrics;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transactional Outbox消息创建测试。 */
class DocumentTaskOutboxServiceTest
{
    @Test
    void shouldPersistSmallIdempotentMessageWithDelay() throws Exception
    {
        CapturingOutboxMapper mapper = new CapturingOutboxMapper();
        OfficeCareRabbitProperties properties = new OfficeCareRabbitProperties();
        properties.setEnabled(true);
        properties.setMaxPublishAttempts(7);
        DocumentTaskOutboxService service = new DocumentTaskOutboxService(
            mapper, new ObjectMapper(), properties);

        OcDocumentTask task = new OcDocumentTask();
        task.setTaskId(11L);
        task.setDocumentVersionId(22L);
        task.setTaskType(DocumentProcessingConstants.TASK_TYPE_INDEX);
        task.setAttemptCount(2);
        task.setTraceId("index-22-retry-test");
        Instant before = Instant.now();

        service.enqueue(task, 60);

        OcOutboxEvent event = mapper.event;
        assertNotNull(event);
        assertEquals("document-task:11:attempt:2", event.getDedupKey());
        assertEquals(DocumentTaskRouting.INDEX_ROUTING_KEY, event.getRoutingKey());
        assertEquals(7, event.getMaxRetryCount());
        assertTrue(event.getNextRetryTime().toInstant()
            .isAfter(before.plusSeconds(59)));

        DocumentTaskMessage message = new ObjectMapper().readValue(
            event.getPayloadJson(), DocumentTaskMessage.class);
        assertEquals(event.getEventId(), message.getEventId());
        assertEquals(11L, message.getTaskId());
        assertEquals(22L, message.getDocumentVersionId());
        assertEquals(2, message.getTaskAttempt());
    }

    @Test
    void shouldReactivateDeadEventWhenSameAttemptIsSubmittedAgain()
    {
        CapturingOutboxMapper mapper = new CapturingOutboxMapper();
        mapper.insertResult = 0;
        DocumentTaskOutboxService service = new DocumentTaskOutboxService(
            mapper, new ObjectMapper(), new OfficeCareRabbitProperties());
        OcDocumentTask task = new OcDocumentTask();
        task.setTaskId(31L);
        task.setDocumentVersionId(41L);
        task.setTaskType(DocumentProcessingConstants.TASK_TYPE_PARSE);
        task.setAttemptCount(0);
        task.setTraceId("document-41-retry-submit");

        service.enqueue(task);

        assertEquals(1, mapper.reactivateCalls);
        assertEquals("document-task:31:attempt:0", mapper.event.getDedupKey());
    }

    private static class CapturingOutboxMapper implements OcOutboxEventMapper
    {
        private OcOutboxEvent event;

        private int insertResult = 1;

        private int reactivateCalls;

        @Override
        public int insertOutboxEvent(OcOutboxEvent event)
        {
            this.event = event;
            event.setOutboxId(1L);
            return insertResult;
        }

        @Override
        public int reactivateDeadEvent(OcOutboxEvent event)
        {
            this.event = event;
            reactivateCalls++;
            return 1;
        }

        @Override
        public List<OcOutboxEvent> selectReadyEvents(int limit)
        {
            return List.of();
        }

        @Override
        public int claimEvent(Long outboxId)
        {
            return 0;
        }

        @Override
        public int markSent(Long outboxId)
        {
            return 0;
        }

        @Override
        public int markRetry(Long outboxId, int retryCount,
                             Date nextRetryTime, String lastError)
        {
            return 0;
        }

        @Override
        public int markDead(Long outboxId, int retryCount, String lastError)
        {
            return 0;
        }

        @Override
        public int recoverStalePublishing(Date cutoffTime)
        {
            return 0;
        }

        @Override
        public OcOutboxEvent selectOutboxEventById(Long outboxId)
        {
            return null;
        }

        @Override
        public int markConsumeStarted(String eventId, boolean redelivered)
        {
            return 0;
        }

        @Override
        public int markConsumeSuccess(String eventId)
        {
            return 0;
        }

        @Override
        public int markConsumeRetry(String eventId, String consumeError)
        {
            return 0;
        }

        @Override
        public int markConsumeDead(String eventId, String consumeError)
        {
            return 0;
        }

        @Override
        public int markConsumeIgnored(String eventId, String consumeError)
        {
            return 0;
        }

        @Override
        public int markDeadLetterReceived(String eventId)
        {
            return 0;
        }

        @Override
        public OfficeCareMqMetrics selectMqMetrics()
        {
            return null;
        }

        @Override
        public List<OfficeCareMqDeadEvent> selectRecentDeadEvents(int limit)
        {
            return List.of();
        }

        @Override
        public int reactivatePublishDead(Long outboxId, String replayedBy)
        {
            return 0;
        }

        @Override
        public int markConsumerDeadReplayed(Long outboxId, String replayedBy)
        {
            return 0;
        }
    }
}
