package com.ruoyi.officecare.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexDeleteResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexResponse;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;

/**
 * 在短事务中维护向量索引任务与文档版本索引状态。
 *
 * @author officecare
 */
@Service
public class OcDocumentIndexTaskStateService
{
    private final OcDocumentTaskMapper taskMapper;

    private final OcDocumentVersionMapper versionMapper;

    public OcDocumentIndexTaskStateService(OcDocumentTaskMapper taskMapper,
                                           OcDocumentVersionMapper versionMapper)
    {
        this.taskMapper = taskMapper;
        this.versionMapper = versionMapper;
    }

    @Transactional
    public boolean claimIndexTask(Long taskId, String updateBy)
    {
        if (taskMapper.claimPendingTask(taskId, updateBy) == 0)
        {
            return false;
        }
        OcDocumentTask task = taskMapper.selectTaskById(taskId);
        if (task == null || versionMapper.updateVersionIndexProcessing(
                task.getDocumentVersionId(), "正在生成向量并写入Milvus") == 0)
        {
            throw new ServiceException("文档版本不存在、尚未解析成功或已被删除");
        }
        return true;
    }

    @Transactional
    public boolean claimDeleteTask(Long taskId, String updateBy)
    {
        return taskMapper.claimPendingTask(taskId, updateBy) > 0;
    }

    @Transactional
    public void completeIndexTask(OcDocumentTask task,
                                  AiDocumentIndexResponse response,
                                  String message,
                                  String updateBy)
    {
        if (versionMapper.updateVersionIndexSuccess(
                task.getDocumentVersionId(),
                response.getIndexContentHash(),
                response.getChunkCount(),
                response.getEmbeddingModel(),
                response.getEmbeddingDimension(),
                response.getCollectionName(),
                message) == 0)
        {
            throw new ServiceException("文档版本不存在、尚未解析成功或已被删除");
        }
        if (taskMapper.updateTaskSuccess(
                task.getTaskId(),
                response.getIndexContentHash(),
                null,
                response.getChunkCount(),
                response.getIndexedCharacterCount(),
                updateBy) == 0)
        {
            throw new ServiceException("文档索引任务状态已发生变化");
        }
    }

    @Transactional
    public void completeDeleteTask(OcDocumentTask task,
                                   AiDocumentIndexDeleteResponse response,
                                   String updateBy)
    {
        if (taskMapper.updateTaskSuccess(
                task.getTaskId(),
                null,
                null,
                response.getDeletedCount(),
                null,
                updateBy) == 0)
        {
            throw new ServiceException("文档索引删除任务状态已发生变化");
        }
    }

    @Transactional
    public boolean resetFailedIndexTask(OcDocumentTask task,
                                        String traceId,
                                        String updateBy)
    {
        if (taskMapper.resetFailedTask(task.getTaskId(), traceId, updateBy) == 0)
        {
            return false;
        }
        if (versionMapper.updateVersionIndexPending(
                task.getDocumentVersionId(), "等待重新生成向量索引") == 0)
        {
            throw new ServiceException("文档版本不存在、尚未解析成功或已被删除");
        }
        return true;
    }

    @Transactional
    public boolean resetFailedDeleteTask(OcDocumentTask task,
                                         String traceId,
                                         String updateBy)
    {
        return taskMapper.resetFailedTask(task.getTaskId(), traceId, updateBy) > 0;
    }

    @Transactional
    public boolean resetInterruptedIndexTask(OcDocumentTask task,
                                             String traceId,
                                             String updateBy)
    {
        if (taskMapper.resetRunningTask(task.getTaskId(), traceId, updateBy) == 0)
        {
            return false;
        }
        if (versionMapper.updateVersionIndexPending(
                task.getDocumentVersionId(), "服务中断，等待恢复向量索引") == 0)
        {
            throw new ServiceException("文档版本不存在、尚未解析成功或已被删除");
        }
        return true;
    }

    @Transactional
    public boolean resetInterruptedDeleteTask(OcDocumentTask task,
                                              String traceId,
                                              String updateBy)
    {
        return taskMapper.resetRunningTask(task.getTaskId(), traceId, updateBy) > 0;
    }

    @Transactional
    public boolean resetFailedIndexTaskManually(OcDocumentTask task,
                                                String traceId,
                                                String updateBy)
    {
        if (taskMapper.resetFailedTaskManually(task.getTaskId(), traceId,
                updateBy) == 0)
        {
            return false;
        }
        if (versionMapper.updateVersionIndexPending(task.getDocumentVersionId(),
                "人工重新投递，等待生成向量索引") == 0)
        {
            throw new ServiceException("文档版本不存在、尚未解析成功或已被删除");
        }
        return true;
    }

    @Transactional
    public boolean resetFailedDeleteTaskManually(OcDocumentTask task,
                                                 String traceId,
                                                 String updateBy)
    {
        return taskMapper.resetFailedTaskManually(task.getTaskId(), traceId,
            updateBy) > 0;
    }

    @Transactional
    public void failIndexTask(Long taskId,
                              Long documentVersionId,
                              String errorCode,
                              String errorMessage,
                              String updateBy)
    {
        taskMapper.updateTaskFailed(taskId, errorCode, errorMessage, updateBy);
        versionMapper.updateVersionIndexFailed(documentVersionId, errorMessage);
    }

    @Transactional
    public void failDeleteTask(Long taskId,
                               String errorCode,
                               String errorMessage,
                               String updateBy)
    {
        taskMapper.updateTaskFailed(taskId, errorCode, errorMessage, updateBy);
    }
}
