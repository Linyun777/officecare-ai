package com.ruoyi.officecare.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.messaging.DocumentTaskOutboxService;
import com.ruoyi.officecare.service.IOcDocumentIndexingService;
import com.ruoyi.officecare.storage.DocumentStorageService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** MQ启用时，文档任务只能写入Outbox，不能再进入本地专用线程池。 */
class DocumentTaskDispatchModeTest
{
    @Test
    void parseTaskShouldUseOutboxInsteadOfLocalExecutor()
    {
        TaskExecutor executor = mock(TaskExecutor.class);
        DocumentTaskOutboxService outbox = enabledOutbox();
        OcDocumentProcessingServiceImpl service =
            new OcDocumentProcessingServiceImpl(
                mock(OcDocumentTaskMapper.class),
                mock(OcDocumentVersionMapper.class),
                mock(OcDocumentTaskStateService.class),
                mock(DocumentStorageService.class),
                mock(OfficeCareAiDocumentClient.class),
                new OfficeCareAiProperties(),
                new ObjectMapper(),
                executor,
                mock(IOcDocumentIndexingService.class),
                outbox);
        OcDocumentTask task = task(1L,
            DocumentProcessingConstants.TASK_TYPE_PARSE);

        service.dispatch(task);

        verify(outbox).enqueue(task);
        verifyNoInteractions(executor);
    }

    @Test
    void indexAndDeleteTasksShouldUseOutboxInsteadOfLocalExecutor()
    {
        TaskExecutor executor = mock(TaskExecutor.class);
        DocumentTaskOutboxService outbox = enabledOutbox();
        OcDocumentIndexingServiceImpl service = new OcDocumentIndexingServiceImpl(
            mock(OcDocumentTaskMapper.class),
            mock(OcDocumentVersionMapper.class),
            mock(OcDocumentMapper.class),
            mock(OcKnowledgeBaseMapper.class),
            mock(OcDocumentIndexTaskStateService.class),
            mock(DocumentStorageService.class),
            mock(OfficeCareAiDocumentClient.class),
            new OfficeCareAiProperties(),
            new ObjectMapper(),
            executor,
            outbox);
        OcDocumentTask indexTask = task(2L,
            DocumentProcessingConstants.TASK_TYPE_INDEX);
        OcDocumentTask deleteTask = task(3L,
            DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX);

        service.dispatch(indexTask);
        service.dispatch(deleteTask);

        verify(outbox).enqueue(indexTask);
        verify(outbox).enqueue(deleteTask);
        verifyNoInteractions(executor);
    }

    private DocumentTaskOutboxService enabledOutbox()
    {
        DocumentTaskOutboxService outbox = mock(DocumentTaskOutboxService.class);
        when(outbox.isEnabled()).thenReturn(true);
        return outbox;
    }

    private OcDocumentTask task(Long taskId, String taskType)
    {
        OcDocumentTask task = new OcDocumentTask();
        task.setTaskId(taskId);
        task.setDocumentVersionId(taskId + 100L);
        task.setTaskType(taskType);
        task.setAttemptCount(0);
        return task;
    }
}
