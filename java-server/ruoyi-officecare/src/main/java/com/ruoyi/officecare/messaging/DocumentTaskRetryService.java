package com.ruoyi.officecare.messaging;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.service.impl.OcDocumentIndexTaskStateService;
import com.ruoyi.officecare.service.impl.OcDocumentTaskStateService;

/**
 * 在同一事务中重置失败任务并写入下一次Outbox事件。
 *
 * @author officecare
 */
@Service
public class DocumentTaskRetryService
{
    private static final String SYSTEM_USER = "rabbitmq";

    private final OcDocumentTaskMapper taskMapper;

    private final OcDocumentTaskStateService parseStateService;

    private final OcDocumentIndexTaskStateService indexStateService;

    private final DocumentTaskOutboxService outboxService;

    private final OfficeCareRabbitProperties properties;

    private final DocumentTaskRetryPolicy retryPolicy = new DocumentTaskRetryPolicy();

    public DocumentTaskRetryService(OcDocumentTaskMapper taskMapper,
                                    OcDocumentTaskStateService parseStateService,
                                    OcDocumentIndexTaskStateService indexStateService,
                                    DocumentTaskOutboxService outboxService,
                                    OfficeCareRabbitProperties properties)
    {
        this.taskMapper = taskMapper;
        this.parseStateService = parseStateService;
        this.indexStateService = indexStateService;
        this.outboxService = outboxService;
        this.properties = properties;
    }

    @Transactional
    public boolean scheduleRetry(OcDocumentTask failedTask)
    {
        if (!canRetry(failedTask) || !retryPolicy.isRetryable(failedTask.getErrorCode()))
        {
            return false;
        }
        String traceId = retryTraceId(failedTask);
        boolean reset = resetFailed(failedTask, traceId);
        if (!reset)
        {
            return false;
        }
        OcDocumentTask pendingTask = taskMapper.selectTaskById(failedTask.getTaskId());
        outboxService.enqueue(pendingTask,
            properties.retryDelaySeconds(failedTask.getAttemptCount()));
        return true;
    }

    /**
     * Rabbit消息重新投递且数据库仍为处理中，说明上次消费进程中断。
     * 恢复为待处理后由当前消费者立即继续执行。
     */
    @Transactional
    public boolean recoverInterrupted(OcDocumentTask runningTask)
    {
        if (!hasAttemptsRemaining(runningTask))
        {
            return false;
        }
        String traceId = retryTraceId(runningTask);
        return switch (runningTask.getTaskType())
        {
            case DocumentProcessingConstants.TASK_TYPE_PARSE ->
                parseStateService.resetInterruptedTask(
                    runningTask, traceId, SYSTEM_USER);
            case DocumentProcessingConstants.TASK_TYPE_INDEX ->
                indexStateService.resetInterruptedIndexTask(
                    runningTask, traceId, SYSTEM_USER);
            case DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX ->
                indexStateService.resetInterruptedDeleteTask(
                    runningTask, traceId, SYSTEM_USER);
            default -> false;
        };
    }

    /**
     * 达到最大尝试次数的运行中任务无法恢复，先落为失败再进入死信闭环。
     */
    @Transactional
    public boolean failInterruptedIfExhausted(OcDocumentTask runningTask)
    {
        if (runningTask == null || hasAttemptsRemaining(runningTask))
        {
            return false;
        }
        String message = "Java进程中断且任务已达到最大执行次数";
        switch (runningTask.getTaskType())
        {
            case DocumentProcessingConstants.TASK_TYPE_PARSE ->
                parseStateService.failTask(runningTask.getTaskId(),
                    runningTask.getDocumentVersionId(), "TASK_INTERRUPTED",
                    message, SYSTEM_USER);
            case DocumentProcessingConstants.TASK_TYPE_INDEX ->
                indexStateService.failIndexTask(runningTask.getTaskId(),
                    runningTask.getDocumentVersionId(), "TASK_INTERRUPTED",
                    message, SYSTEM_USER);
            case DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX ->
                indexStateService.failDeleteTask(runningTask.getTaskId(),
                    "TASK_INTERRUPTED", message, SYSTEM_USER);
            default ->
            {
                return false;
            }
        }
        return true;
    }

    /** 人工确认故障已经排除后，为失败任务增加一次执行预算并重新入队。 */
    @Transactional
    public boolean scheduleManualRetry(OcDocumentTask failedTask, String username)
    {
        if (failedTask == null
            || !DocumentProcessingConstants.STATUS_FAILED.equals(
                failedTask.getStatus()))
        {
            return false;
        }
        String traceId = manualTraceId(failedTask);
        boolean reset = switch (failedTask.getTaskType())
        {
            case DocumentProcessingConstants.TASK_TYPE_PARSE ->
                parseStateService.resetFailedTaskManually(
                    failedTask, traceId, username);
            case DocumentProcessingConstants.TASK_TYPE_INDEX ->
                indexStateService.resetFailedIndexTaskManually(
                    failedTask, traceId, username);
            case DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX ->
                indexStateService.resetFailedDeleteTaskManually(
                    failedTask, traceId, username);
            default -> false;
        };
        if (!reset)
        {
            return false;
        }
        outboxService.enqueue(taskMapper.selectTaskById(failedTask.getTaskId()));
        return true;
    }

    private boolean resetFailed(OcDocumentTask task, String traceId)
    {
        return switch (task.getTaskType())
        {
            case DocumentProcessingConstants.TASK_TYPE_PARSE ->
                parseStateService.resetFailedTask(task, traceId, SYSTEM_USER);
            case DocumentProcessingConstants.TASK_TYPE_INDEX ->
                indexStateService.resetFailedIndexTask(task, traceId, SYSTEM_USER);
            case DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX ->
                indexStateService.resetFailedDeleteTask(task, traceId, SYSTEM_USER);
            default -> false;
        };
    }

    private boolean canRetry(OcDocumentTask task)
    {
        return task != null
            && DocumentProcessingConstants.STATUS_FAILED.equals(task.getStatus())
            && hasAttemptsRemaining(task);
    }

    private boolean hasAttemptsRemaining(OcDocumentTask task)
    {
        return task.getAttemptCount() != null && task.getMaxAttempts() != null
            && task.getAttemptCount() < task.getMaxAttempts();
    }

    private String retryTraceId(OcDocumentTask task)
    {
        return task.getTaskType() + "-" + task.getDocumentVersionId()
            + "-retry-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String manualTraceId(OcDocumentTask task)
    {
        return task.getTaskType() + "-" + task.getDocumentVersionId()
            + "-manual-" + UUID.randomUUID().toString().replace("-", "");
    }
}
