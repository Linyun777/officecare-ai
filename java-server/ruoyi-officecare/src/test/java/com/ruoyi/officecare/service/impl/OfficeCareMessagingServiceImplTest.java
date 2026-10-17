package com.ruoyi.officecare.service.impl;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.constant.OutboxConstants;
import com.ruoyi.officecare.constant.OutboxConsumeConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcOutboxEvent;
import com.ruoyi.officecare.domain.vo.OfficeCareMqDeadEvent;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;
import com.ruoyi.officecare.messaging.DocumentTaskRetryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 异常消息查询与人工重投服务测试。 */
class OfficeCareMessagingServiceImplTest
{
    private OcOutboxEventMapper outboxMapper;

    private OcDocumentTaskMapper taskMapper;

    private DocumentTaskRetryService retryService;

    private OfficeCareMessagingServiceImpl service;

    @BeforeEach
    void setUp()
    {
        outboxMapper = mock(OcOutboxEventMapper.class);
        taskMapper = mock(OcDocumentTaskMapper.class);
        retryService = mock(DocumentTaskRetryService.class);
        service = new OfficeCareMessagingServiceImpl(outboxMapper, taskMapper,
            retryService);
    }

    @Test
    void shouldReturnLatestTwentyDeadEvents()
    {
        OfficeCareMqDeadEvent event = new OfficeCareMqDeadEvent();
        when(outboxMapper.selectRecentDeadEvents(20)).thenReturn(List.of(event));

        assertEquals(List.of(event), service.selectRecentDeadEvents());
    }

    @Test
    void shouldReactivatePublisherFailureWithoutResettingTask()
    {
        OcOutboxEvent event = event(1L, OutboxConstants.STATUS_DEAD,
            OutboxConsumeConstants.STATUS_PENDING, 0);
        OcDocumentTask task = task(DocumentProcessingConstants.STATUS_PENDING, 0);
        when(outboxMapper.selectOutboxEventById(1L)).thenReturn(event);
        when(taskMapper.selectTaskById(11L)).thenReturn(task);
        when(outboxMapper.reactivatePublishDead(1L, "admin")).thenReturn(1);

        service.replayDeadEvent(1L, "admin");

        verify(outboxMapper).reactivatePublishDead(1L, "admin");
        verifyNoInteractions(retryService);
    }

    @Test
    void shouldResetFailedTaskAndCreateNewOutboxForConsumerDeadLetter()
    {
        OcOutboxEvent event = event(2L, OutboxConstants.STATUS_SENT,
            OutboxConsumeConstants.STATUS_DEAD, 0);
        OcDocumentTask task = task(DocumentProcessingConstants.STATUS_FAILED, 1);
        when(outboxMapper.selectOutboxEventById(2L)).thenReturn(event);
        when(taskMapper.selectTaskById(11L)).thenReturn(task);
        when(retryService.scheduleManualRetry(task, "operator")).thenReturn(true);
        when(outboxMapper.markConsumerDeadReplayed(2L, "operator"))
            .thenReturn(1);

        service.replayDeadEvent(2L, "operator");

        verify(retryService).scheduleManualRetry(task, "operator");
        verify(outboxMapper).markConsumerDeadReplayed(2L, "operator");
    }

    @Test
    void shouldRejectReplayWhenTaskAttemptHasChanged()
    {
        OcOutboxEvent event = event(3L, OutboxConstants.STATUS_SENT,
            OutboxConsumeConstants.STATUS_DEAD, 0);
        OcDocumentTask task = task(DocumentProcessingConstants.STATUS_FAILED, 2);
        when(outboxMapper.selectOutboxEventById(3L)).thenReturn(event);
        when(taskMapper.selectTaskById(11L)).thenReturn(task);

        assertThrows(ServiceException.class,
            () -> service.replayDeadEvent(3L, "admin"));

        verifyNoInteractions(retryService);
    }

    private OcOutboxEvent event(Long outboxId, String status,
                                String consumeStatus, int taskAttempt)
    {
        OcOutboxEvent event = new OcOutboxEvent();
        event.setOutboxId(outboxId);
        event.setTaskId(11L);
        event.setTaskAttempt(taskAttempt);
        event.setStatus(status);
        event.setConsumeStatus(consumeStatus);
        return event;
    }

    private OcDocumentTask task(String status, int attemptCount)
    {
        OcDocumentTask task = new OcDocumentTask();
        task.setTaskId(11L);
        task.setStatus(status);
        task.setAttemptCount(attemptCount);
        return task;
    }
}
