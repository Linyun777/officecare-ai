package com.ruoyi.officecare.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.service.impl.OcDocumentIndexTaskStateService;
import com.ruoyi.officecare.service.impl.OcDocumentTaskStateService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Java进程中断后，运行中任务恢复到待处理状态的测试。 */
class DocumentTaskRetryServiceTest
{
    private OcDocumentTaskMapper taskMapper;

    private OcDocumentTaskStateService parseStateService;

    private OcDocumentIndexTaskStateService indexStateService;

    private DocumentTaskOutboxService outboxService;

    private DocumentTaskRetryService retryService;

    private OfficeCareRabbitProperties properties;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(OcDocumentTaskMapper.class);
        parseStateService = mock(OcDocumentTaskStateService.class);
        indexStateService = mock(OcDocumentIndexTaskStateService.class);
        outboxService = mock(DocumentTaskOutboxService.class);
        properties = new OfficeCareRabbitProperties();
        retryService = new DocumentTaskRetryService(taskMapper,
            parseStateService, indexStateService, outboxService,
            properties);
    }

    @Test
    void shouldRecoverInterruptedParseTask()
    {
        OcDocumentTask task = runningTask(1L,
            DocumentProcessingConstants.TASK_TYPE_PARSE, 1, 3);
        when(parseStateService.resetInterruptedTask(eq(task),
            matches("parse-101-retry-[0-9a-f]{32}"), eq("rabbitmq")))
            .thenReturn(true);

        assertTrue(retryService.recoverInterrupted(task));

        verify(parseStateService).resetInterruptedTask(eq(task),
            matches("parse-101-retry-[0-9a-f]{32}"), eq("rabbitmq"));
        verifyNoInteractions(indexStateService, outboxService);
    }

    @Test
    void shouldRecoverInterruptedIndexTask()
    {
        OcDocumentTask task = runningTask(2L,
            DocumentProcessingConstants.TASK_TYPE_INDEX, 1, 3);
        when(indexStateService.resetInterruptedIndexTask(eq(task),
            matches("index-102-retry-[0-9a-f]{32}"), eq("rabbitmq")))
            .thenReturn(true);

        assertTrue(retryService.recoverInterrupted(task));

        verify(indexStateService).resetInterruptedIndexTask(eq(task),
            matches("index-102-retry-[0-9a-f]{32}"), eq("rabbitmq"));
        verifyNoInteractions(parseStateService, outboxService);
    }

    @Test
    void shouldRecoverInterruptedDeleteIndexTask()
    {
        OcDocumentTask task = runningTask(3L,
            DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX, 1, 3);
        when(indexStateService.resetInterruptedDeleteTask(eq(task),
            matches("delete_index-103-retry-[0-9a-f]{32}"), eq("rabbitmq")))
            .thenReturn(true);

        assertTrue(retryService.recoverInterrupted(task));

        verify(indexStateService).resetInterruptedDeleteTask(eq(task),
            matches("delete_index-103-retry-[0-9a-f]{32}"), eq("rabbitmq"));
        verifyNoInteractions(parseStateService, outboxService);
    }

    @Test
    void shouldNotRecoverTaskWhenAttemptBudgetIsExhausted()
    {
        OcDocumentTask task = runningTask(4L,
            DocumentProcessingConstants.TASK_TYPE_PARSE, 3, 3);

        assertFalse(retryService.recoverInterrupted(task));

        verifyNoInteractions(parseStateService, indexStateService,
            outboxService, taskMapper);
    }

    @Test
    void shouldScheduleRetryWithExponentialDelay()
    {
        properties.setRetryInitialDelaySeconds(30L);
        OcDocumentTask failed = failedTask(5L,
            DocumentProcessingConstants.TASK_TYPE_PARSE, 2, 4,
            "AI_SERVICE_UNAVAILABLE");
        OcDocumentTask pending = failedTask(5L,
            DocumentProcessingConstants.TASK_TYPE_PARSE, 2, 4, null);
        pending.setStatus(DocumentProcessingConstants.STATUS_PENDING);
        when(parseStateService.resetFailedTask(eq(failed),
            matches("parse-105-retry-[0-9a-f]{32}"), eq("rabbitmq")))
            .thenReturn(true);
        when(taskMapper.selectTaskById(5L)).thenReturn(pending);

        assertTrue(retryService.scheduleRetry(failed));

        verify(outboxService).enqueue(pending, 60L);
    }

    @Test
    void shouldNotScheduleAutomaticRetryForPermanentFailure()
    {
        OcDocumentTask failed = failedTask(6L,
            DocumentProcessingConstants.TASK_TYPE_INDEX, 1, 3,
            "EMBEDDING_TOKEN_INVALID");

        assertFalse(retryService.scheduleRetry(failed));

        verifyNoInteractions(parseStateService, indexStateService,
            outboxService, taskMapper);
    }

    @Test
    void shouldAllowOneExplicitManualRetryAfterAttemptsAreExhausted()
    {
        OcDocumentTask failed = failedTask(7L,
            DocumentProcessingConstants.TASK_TYPE_INDEX, 3, 3,
            "DOCUMENT_INDEXING_ERROR");
        OcDocumentTask pending = failedTask(7L,
            DocumentProcessingConstants.TASK_TYPE_INDEX, 3, 4, null);
        pending.setStatus(DocumentProcessingConstants.STATUS_PENDING);
        when(indexStateService.resetFailedIndexTaskManually(eq(failed),
            matches("index-107-manual-[0-9a-f]{32}"), eq("admin")))
            .thenReturn(true);
        when(taskMapper.selectTaskById(7L)).thenReturn(pending);

        assertTrue(retryService.scheduleManualRetry(failed, "admin"));

        verify(outboxService).enqueue(pending);
    }

    @Test
    void shouldMarkInterruptedTaskFailedWhenBudgetIsExhausted()
    {
        OcDocumentTask running = runningTask(8L,
            DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX, 3, 3);

        assertTrue(retryService.failInterruptedIfExhausted(running));

        verify(indexStateService).failDeleteTask(8L, "TASK_INTERRUPTED",
            "Java进程中断且任务已达到最大执行次数", "rabbitmq");
        verifyNoInteractions(parseStateService, outboxService, taskMapper);
    }

    private OcDocumentTask runningTask(Long taskId, String taskType,
                                       int attemptCount, int maxAttempts)
    {
        OcDocumentTask task = new OcDocumentTask();
        task.setTaskId(taskId);
        task.setDocumentVersionId(taskId + 100L);
        task.setTaskType(taskType);
        task.setStatus(DocumentProcessingConstants.STATUS_RUNNING);
        task.setAttemptCount(attemptCount);
        task.setMaxAttempts(maxAttempts);
        return task;
    }

    private OcDocumentTask failedTask(Long taskId, String taskType,
                                      int attemptCount, int maxAttempts,
                                      String errorCode)
    {
        OcDocumentTask task = runningTask(taskId, taskType, attemptCount,
            maxAttempts);
        task.setStatus(DocumentProcessingConstants.STATUS_FAILED);
        task.setErrorCode(errorCode);
        return task;
    }
}
