package com.ruoyi.officecare.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.client.AiDocumentParseException;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.domain.OcDocument;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexDeleteResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexRequest;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateRequest;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.messaging.DocumentTaskOutboxService;
import com.ruoyi.officecare.service.IOcDocumentIndexingService;
import com.ruoyi.officecare.storage.DocumentStorageService;

/**
 * OfficeCare文档向量索引任务服务实现。
 *
 * @author officecare
 */
@Service
public class OcDocumentIndexingServiceImpl implements IOcDocumentIndexingService {
    private static final Logger log =
            LoggerFactory.getLogger(OcDocumentIndexingServiceImpl.class);

    private static final String SYSTEM_USER = "system";

    private static final String NORMAL_STATUS = "0";

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final OcDocumentTaskMapper taskMapper;

    private final OcDocumentVersionMapper versionMapper;

    private final OcDocumentMapper documentMapper;

    private final OcKnowledgeBaseMapper knowledgeBaseMapper;

    private final OcDocumentIndexTaskStateService stateService;

    private final DocumentStorageService storageService;

    private final OfficeCareAiDocumentClient aiClient;

    private final OfficeCareAiProperties properties;

    private final ObjectMapper objectMapper;

    private final TaskExecutor taskExecutor;

    private final DocumentTaskOutboxService outboxService;

    public OcDocumentIndexingServiceImpl(
            OcDocumentTaskMapper taskMapper,
            OcDocumentVersionMapper versionMapper,
            OcDocumentMapper documentMapper,
            OcKnowledgeBaseMapper knowledgeBaseMapper,
            OcDocumentIndexTaskStateService stateService,
            DocumentStorageService storageService,
            OfficeCareAiDocumentClient aiClient,
            OfficeCareAiProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("officeCareDocumentTaskExecutor") TaskExecutor taskExecutor,
            DocumentTaskOutboxService outboxService) {
        this.taskMapper = taskMapper;
        this.versionMapper = versionMapper;
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.stateService = stateService;
        this.storageService = storageService;
        this.aiClient = aiClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.outboxService = outboxService;
    }

    @Override
    public OcDocumentTask selectTaskByVersionId(Long documentVersionId) {
        requireVersion(documentVersionId);
        return taskMapper.selectTaskByVersionIdAndType(
                documentVersionId, DocumentProcessingConstants.TASK_TYPE_INDEX);
    }

    @Override
    @Transactional
    public OcDocumentTask submitVersion(Long documentVersionId, String username) {
        OcDocumentVersion version = requireIndexableVersion(documentVersionId);
        OcDocumentTask task = taskMapper.selectTaskByVersionIdAndType(
                documentVersionId, DocumentProcessingConstants.TASK_TYPE_INDEX);
        if (task == null) {
            return createTask(version, username, true);
        }

        if (DocumentProcessingConstants.STATUS_PENDING.equals(task.getStatus())) {
            requireAttemptsAvailable(task);
            dispatch(task);
            return task;
        }
        if (DocumentProcessingConstants.STATUS_FAILED.equals(task.getStatus())) {
            String traceId = buildTraceId("index", documentVersionId);
            if (!stateService.resetFailedIndexTask(task, traceId, username)) {
                throw new ServiceException("文档索引任务已达到最大执行次数或状态已变化");
            }
            OcDocumentTask resetTask = taskMapper.selectTaskById(task.getTaskId());
            dispatch(resetTask);
            return resetTask;
        }
        if (DocumentProcessingConstants.STATUS_RUNNING.equals(task.getStatus())) {
            throw new ServiceException("文档正在生成向量索引，请勿重复提交");
        }
        throw new ServiceException("文档向量索引已经生成，无需重复提交");
    }

    @Override
    @Transactional
    public OcDocumentTask createTaskAndSchedule(Long documentVersionId, String username) {
        OcDocumentVersion version = requireIndexableVersion(documentVersionId);
        OcDocumentTask existing = taskMapper.selectTaskByVersionIdAndType(
                documentVersionId, DocumentProcessingConstants.TASK_TYPE_INDEX);
        if (existing != null) {
            if (DocumentProcessingConstants.STATUS_PENDING.equals(existing.getStatus())) {
                dispatch(existing);
            }
            return existing;
        }
        return createTask(version, username, true);
    }

    @Override
    @Transactional
    public void prepareDeletionTasks(Long[] documentIds, String username) {
        if (documentIds == null || documentIds.length == 0) {
            return;
        }
        List<OcDocumentVersion> versions =
                versionMapper.selectVersionListByDocumentIds(documentIds);
        for (OcDocumentVersion version : versions) {
            OcDocumentTask existing = taskMapper.selectTaskByVersionIdAndType(
                    version.getDocumentVersionId(),
                    DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX);
            if (existing != null) {
                if (DocumentProcessingConstants.STATUS_PENDING.equals(existing.getStatus())) {
                    dispatch(existing);
                }
                continue;
            }
            OcDocumentTask task = newTask(
                    version,
                    DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX,
                    buildTraceId("delete-index", version.getDocumentVersionId()),
                    username);
            taskMapper.insertDocumentTask(task);
            dispatch(task);
        }
    }

    private OcDocumentTask createTask(OcDocumentVersion version,
                                      String username,
                                      boolean schedule) {
        OcDocumentTask task = newTask(
                version,
                DocumentProcessingConstants.TASK_TYPE_INDEX,
                buildTraceId("index", version.getDocumentVersionId()),
                username);
        taskMapper.insertDocumentTask(task);
        if (versionMapper.updateVersionIndexPending(
                version.getDocumentVersionId(), "等待文档向量化") == 0) {
            throw new ServiceException("文档版本不存在、尚未解析成功或已被删除");
        }
        if (schedule) {
            dispatch(task);
        }
        return task;
    }

    private OcDocumentTask newTask(OcDocumentVersion version,
                                   String taskType,
                                   String traceId,
                                   String username) {
        OcDocumentTask task = new OcDocumentTask();
        task.setDocumentId(version.getDocumentId());
        task.setDocumentVersionId(version.getDocumentVersionId());
        task.setTaskType(taskType);
        task.setStatus(DocumentProcessingConstants.STATUS_PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(properties.getMaxAttempts());
        task.setTraceId(traceId);
        task.setRequestFileHash(version.getFileHash());
        task.setCreateBy(username);
        return task;
    }

    void dispatch(OcDocumentTask task) {
        if (outboxService.isEnabled()) {
            outboxService.enqueue(task);
            return;
        }
        scheduleAfterCommit(task.getTaskId());
    }

    private void scheduleAfterCommit(Long taskId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatchSafely(taskId);
                        }
                    });
        } else {
            dispatchSafely(taskId);
        }
    }

    private void dispatchSafely(Long taskId) {
        try {
            taskExecutor.execute(() -> executeTask(taskId));
        } catch (RuntimeException exception) {
            OcDocumentTask task = taskMapper.selectTaskById(taskId);
            if (task != null) {
                fail(task, "TASK_DISPATCH_REJECTED",
                        "文档索引任务队列暂时不可用", exception);
            }
        }
    }

    @Override
    public void executeTask(Long taskId) {
        OcDocumentTask task = taskMapper.selectTaskById(taskId);
        if (task == null) {
            return;
        }
        if (DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX.equals(task.getTaskType())) {
            processDeleteTask(task);
        } else if (DocumentProcessingConstants.TASK_TYPE_INDEX.equals(task.getTaskType())) {
            processIndexTask(task);
        }
    }

    private void processIndexTask(OcDocumentTask task) {
        try {
            if (!stateService.claimIndexTask(task.getTaskId(), SYSTEM_USER)) {
                return;
            }
            OcDocumentTask claimedTask = taskMapper.selectTaskById(task.getTaskId());
            if (claimedTask == null) {
                return;
            }
            task = claimedTask;
            OcDocumentVersion version =
                    requireIndexableVersion(task.getDocumentVersionId());
            AiDocumentParseResponse parseResponse = readParseResponse(version);
            validateParseResponse(version, parseResponse);
            AiDocumentIndexRequest request =
                    buildIndexRequest(version, parseResponse);
            AiDocumentIndexResponse response =
                    aiClient.index(request, task.getTraceId());
            validateIndexResponse(version, response);
            stateService.completeIndexTask(
                    task,
                    response,
                    successMessage(response),
                    SYSTEM_USER);
            invalidateRagCache(task, "DOCUMENT_INDEXED");
        } catch (AiDocumentParseException exception) {
            fail(task, exception.getErrorCode(), exception.getMessage(), exception);
        } catch (IOException exception) {
            fail(task, "PARSED_RESULT_READ_FAILED",
                    "读取或反序列化文档解析结果失败", exception);
        } catch (Exception exception) {
            fail(task, "DOCUMENT_INDEXING_ERROR",
                    StringUtils.defaultIfEmpty(exception.getMessage(),
                            "文档向量索引处理失败"),
                    exception);
        }
    }

    private void processDeleteTask(OcDocumentTask task) {
        try {
            if (!stateService.claimDeleteTask(task.getTaskId(), SYSTEM_USER)) {
                return;
            }
            OcDocumentTask claimedTask = taskMapper.selectTaskById(task.getTaskId());
            if (claimedTask == null) {
                return;
            }
            task = claimedTask;
            AiDocumentIndexDeleteResponse response = aiClient.deleteIndex(
                    task.getDocumentVersionId(), task.getTraceId());
            if (response.getDocumentVersionId() == null
                    || !response.getDocumentVersionId().equals(
                    task.getDocumentVersionId())) {
                throw new AiDocumentParseException(
                        "INVALID_INDEX_DELETE_RESPONSE",
                        "FastAPI返回的索引删除版本ID不一致",
                        task.getTraceId());
            }
            stateService.completeDeleteTask(task, response, SYSTEM_USER);
            invalidateRagCache(task, "DOCUMENT_INDEX_DELETED");
        } catch (AiDocumentParseException exception) {
            fail(task, exception.getErrorCode(), exception.getMessage(), exception);
        } catch (Exception exception) {
            fail(task, "DOCUMENT_INDEX_DELETE_ERROR",
                    StringUtils.defaultIfEmpty(exception.getMessage(),
                            "删除文档向量索引失败"),
                    exception);
        }
    }

    /**
     * Cache invalidation is best-effort and must not turn a successful Milvus
     * operation into a failed indexing task. Old revisions still expire by TTL.
     */
    private void invalidateRagCache(OcDocumentTask task, String reason)
    {
        try
        {
            Long knowledgeBaseId = versionMapper.selectKnowledgeBaseIdByVersionId(
                task.getDocumentVersionId());
            if (knowledgeBaseId == null)
            {
                log.warn("无法定位文档版本所属知识库，跳过RAG缓存失效，documentVersionId={}",
                    task.getDocumentVersionId());
                return;
            }

            AiRagCacheInvalidateRequest request = new AiRagCacheInvalidateRequest();
            request.setKnowledgeBaseId(knowledgeBaseId);
            request.setReason(reason);
            String traceId = StringUtils.defaultIfEmpty(
                task.getTraceId(), "rag-cache-" + UUID.randomUUID());
            aiClient.invalidateRagCache(request, traceId + "-cache");
        }
        catch (AiDocumentParseException exception)
        {
            if ("RAG_CACHE_DISABLED".equals(exception.getErrorCode()))
            {
                log.debug("FastAPI RAG缓存未启用，跳过主动失效");
                return;
            }
            log.warn("RAG缓存主动失效失败，documentVersionId={}，errorCode={}",
                task.getDocumentVersionId(), exception.getErrorCode());
        }
        catch (RuntimeException exception)
        {
            log.warn("RAG缓存主动失效发生未预期错误，documentVersionId={}，errorType={}",
                task.getDocumentVersionId(), exception.getClass().getSimpleName());
        }
    }

    private AiDocumentParseResponse readParseResponse(OcDocumentVersion version)
            throws IOException {
        if (StringUtils.isEmpty(version.getParsedObjectKey())) {
            throw new IOException("文档版本没有解析结果对象键");
        }
        try (InputStream inputStream =
                     storageService.open(version.getParsedObjectKey())) {
            return objectMapper.readValue(inputStream, AiDocumentParseResponse.class);
        }
    }

    private AiDocumentIndexRequest buildIndexRequest(
            OcDocumentVersion version,
            AiDocumentParseResponse parseResponse) {
        OcDocument document = documentMapper.selectDocumentById(version.getDocumentId());
        if (document == null) {
            throw new ServiceException("文档不存在或已被删除");
        }
        if (!NORMAL_STATUS.equals(document.getStatus())) {
            throw new ServiceException("文档已停用，不能生成向量索引");
        }
        OcKnowledgeBase knowledgeBase =
                knowledgeBaseMapper.selectKnowledgeBaseById(
                        document.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            throw new ServiceException("知识库不存在或已被删除");
        }
        if (!NORMAL_STATUS.equals(knowledgeBase.getStatus())) {
            throw new ServiceException("知识库已停用，不能生成向量索引");
        }

        AiDocumentIndexRequest request = new AiDocumentIndexRequest();
        request.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        request.setBrandId(knowledgeBase.getBrandId());
        request.setModelId(knowledgeBase.getModelId());
        request.setDocumentId(document.getDocumentId());
        request.setDocumentVersionId(version.getDocumentVersionId());
        request.setVersionNo(version.getVersionNo());
        request.setDocumentTitle(document.getDocumentTitle());
        request.setOriginalFileName(version.getOriginalFileName());
        request.setFileHash(version.getFileHash());
        request.setBlockCount(parseResponse.getBlockCount());
        request.setCharacterCount(parseResponse.getCharacterCount());
        request.setBlocks(parseResponse.getBlocks());
        return request;
    }

    private void validateParseResponse(OcDocumentVersion version,
                                       AiDocumentParseResponse response) {
        if (response == null
                || StringUtils.isEmpty(response.getFileHash())
                || !response.getFileHash().equalsIgnoreCase(version.getFileHash())) {
            throw new ServiceException("解析结果与文档版本文件摘要不一致");
        }
        if (response.getBlocks() == null
                || response.getBlockCount() == null
                || response.getBlockCount() != response.getBlocks().size()
                || response.getCharacterCount() == null
                || response.getCharacterCount() <= 0) {
            throw new ServiceException("解析结果的文本块统计不完整");
        }
    }

    private void validateIndexResponse(OcDocumentVersion version,
                                       AiDocumentIndexResponse response) {
        if (response.getDocumentId() == null
                || !response.getDocumentId().equals(version.getDocumentId())
                || response.getDocumentVersionId() == null
                || !response.getDocumentVersionId().equals(
                version.getDocumentVersionId())) {
            throw new AiDocumentParseException(
                    "INVALID_INDEX_RESPONSE",
                    "FastAPI返回的文档索引标识不一致",
                    null);
        }
        if (response.getChunkCount() == null
                || response.getChunkCount() <= 0
                || response.getEmbeddingDimension() == null
                || response.getEmbeddingDimension() <= 0
                || StringUtils.isEmpty(response.getEmbeddingModel())
                || StringUtils.isEmpty(response.getCollectionName())
                || StringUtils.isEmpty(response.getIndexContentHash())) {
            throw new AiDocumentParseException(
                    "INVALID_INDEX_RESPONSE",
                    "FastAPI返回的文档索引统计不完整",
                    null);
        }
    }

    private OcDocumentVersion requireVersion(Long documentVersionId) {
        if (documentVersionId == null) {
            throw new ServiceException("文档版本ID不能为空");
        }
        OcDocumentVersion version =
                versionMapper.selectVersionById(documentVersionId);
        if (version == null) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        return version;
    }

    private OcDocumentVersion requireIndexableVersion(Long documentVersionId) {
        OcDocumentVersion version = requireVersion(documentVersionId);
        if (!DocumentProcessingConstants.STATUS_SUCCESS.equals(
                version.getProcessStatus())
                || StringUtils.isEmpty(version.getParsedObjectKey())) {
            throw new ServiceException("文档尚未解析成功，不能生成向量索引");
        }
        return version;
    }

    private void requireAttemptsAvailable(OcDocumentTask task) {
        if (task.getAttemptCount() != null
                && task.getMaxAttempts() != null
                && task.getAttemptCount() >= task.getMaxAttempts()) {
            throw new ServiceException("文档索引任务已达到最大执行次数");
        }
    }

    private void fail(OcDocumentTask task,
                      String errorCode,
                      String errorMessage,
                      Exception exception) {
        String message = truncateMessage(StringUtils.defaultIfEmpty(
                StringUtils.trim(errorMessage), "文档向量索引失败"));
        try {
            if (DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX.equals(
                    task.getTaskType())) {
                stateService.failDeleteTask(
                        task.getTaskId(),
                        truncateCode(errorCode),
                        message,
                        SYSTEM_USER);
            } else {
                stateService.failIndexTask(
                        task.getTaskId(),
                        task.getDocumentVersionId(),
                        truncateCode(errorCode),
                        message,
                        SYSTEM_USER);
            }
        } catch (RuntimeException stateException) {
            log.error("更新文档索引失败状态异常，taskId={}，traceId={}",
                    task.getTaskId(), task.getTraceId(), stateException);
        }
        log.warn("文档索引任务失败，taskId={}，taskType={}，traceId={}，errorCode={}",
                task.getTaskId(), task.getTaskType(), task.getTraceId(),
                errorCode, exception);
    }

    private String successMessage(AiDocumentIndexResponse response) {
        return truncateMessage("向量索引成功，共"
                + response.getChunkCount()
                + "个文本切片，模型"
                + response.getEmbeddingModel());
    }

    private String buildTraceId(String prefix, Long documentVersionId) {
        return prefix + "-" + documentVersionId + "-"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String truncateCode(String errorCode) {
        String normalized = StringUtils.defaultIfEmpty(
                errorCode, "DOCUMENT_INDEXING_ERROR");
        return normalized.length() <= 64
                ? normalized : normalized.substring(0, 64);
    }

    private String truncateMessage(String message) {
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
