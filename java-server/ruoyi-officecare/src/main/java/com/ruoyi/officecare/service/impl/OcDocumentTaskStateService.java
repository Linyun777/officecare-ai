package com.ruoyi.officecare.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;

/**
 * 在短事务中维护任务与版本状态，网络调用不占用数据库事务。
 *
 * @author officecare
 */
@Service
public class OcDocumentTaskStateService {
    private final OcDocumentTaskMapper taskMapper;

    private final OcDocumentVersionMapper versionMapper;

    public OcDocumentTaskStateService(OcDocumentTaskMapper taskMapper,
                                      OcDocumentVersionMapper versionMapper) {
        this.taskMapper = taskMapper;
        this.versionMapper = versionMapper;
    }

    @Transactional
    public boolean claimTask(Long taskId, String updateBy) {
        if (taskMapper.claimPendingTask(taskId, updateBy) == 0) {
            return false;
        }
        OcDocumentTask task = taskMapper.selectTaskById(taskId);
        if (task == null || versionMapper.updateVersionProcessing(
                task.getDocumentVersionId(), "正在调用文档解析服务") == 0) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        return true;
    }

    @Transactional
    public void completeTask(OcDocumentTask task, AiDocumentParseResponse response,
                             String parsedObjectKey, String processMessage,
                             String updateBy) {
        if (versionMapper.updateVersionSuccess(task.getDocumentVersionId(),
                parsedObjectKey, response.getPageCount(), processMessage) == 0) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        if (taskMapper.updateTaskSuccess(task.getTaskId(), response.getFileHash(),
                response.getPageCount(), response.getBlockCount(),
                response.getCharacterCount(), updateBy) == 0) {
            throw new ServiceException("文档处理任务状态已发生变化");
        }
    }

    @Transactional
    public boolean resetFailedTask(OcDocumentTask task, String traceId, String updateBy) {
        if (taskMapper.resetFailedTask(task.getTaskId(), traceId, updateBy) == 0) {
            return false;
        }
        if (versionMapper.updateVersionPending(task.getDocumentVersionId(),
                "等待重新解析") == 0) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        return true;
    }

    @Transactional
    public boolean resetInterruptedTask(OcDocumentTask task, String traceId,
                                        String updateBy) {
        if (taskMapper.resetRunningTask(task.getTaskId(), traceId, updateBy) == 0) {
            return false;
        }
        if (versionMapper.updateVersionPending(task.getDocumentVersionId(),
                "服务中断，等待恢复解析") == 0) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        return true;
    }

    @Transactional
    public boolean resetFailedTaskManually(OcDocumentTask task, String traceId,
                                           String updateBy) {
        if (taskMapper.resetFailedTaskManually(task.getTaskId(), traceId,
                updateBy) == 0) {
            return false;
        }
        if (versionMapper.updateVersionPending(task.getDocumentVersionId(),
                "人工重新投递，等待解析") == 0) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        return true;
    }

    @Transactional
    public void failTask(Long taskId, Long documentVersionId, String errorCode,
                         String errorMessage, String updateBy) {
        taskMapper.updateTaskFailed(taskId, errorCode, errorMessage, updateBy);
        versionMapper.updateVersionFailed(documentVersionId, errorMessage);
    }
}
