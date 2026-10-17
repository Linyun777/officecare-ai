package com.ruoyi.officecare.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;
import com.ruoyi.officecare.messaging.DocumentTaskOutboxService;
import com.ruoyi.officecare.service.IOcDocumentIndexingService;
import com.ruoyi.officecare.service.IOcDocumentProcessingService;
import com.ruoyi.officecare.storage.DocumentStorageService;

/**
 * OfficeCare文档解析任务服务实现。
 *
 * @author officecare
 */
@Service
public class OcDocumentProcessingServiceImpl implements IOcDocumentProcessingService {
    private static final Logger log =
            LoggerFactory.getLogger(OcDocumentProcessingServiceImpl.class);

    private static final String SYSTEM_USER = "system";

    private static final int MAX_MESSAGE_LENGTH = 500;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    private final OcDocumentTaskMapper taskMapper;

    private final OcDocumentVersionMapper versionMapper;

    private final OcDocumentTaskStateService stateService;

    private final DocumentStorageService storageService;

    private final OfficeCareAiDocumentClient aiClient;

    private final OfficeCareAiProperties properties;

    private final ObjectMapper objectMapper;

    private final TaskExecutor taskExecutor;

    private final IOcDocumentIndexingService indexingService;

    private final DocumentTaskOutboxService outboxService;

    public OcDocumentProcessingServiceImpl(
            OcDocumentTaskMapper taskMapper,
            OcDocumentVersionMapper versionMapper,
            OcDocumentTaskStateService stateService,
            DocumentStorageService storageService,
            OfficeCareAiDocumentClient aiClient,
            OfficeCareAiProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("officeCareDocumentTaskExecutor") TaskExecutor taskExecutor,
            IOcDocumentIndexingService indexingService,
            DocumentTaskOutboxService outboxService) {
        this.taskMapper = taskMapper;
        this.versionMapper = versionMapper;
        this.stateService = stateService;
        this.storageService = storageService;
        this.aiClient = aiClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.indexingService = indexingService;
        this.outboxService = outboxService;
    }

    @Override
    public OcDocumentTask selectTaskByVersionId(Long documentVersionId) {
        requireVersion(documentVersionId);
        return taskMapper.selectTaskByVersionId(documentVersionId);
    }

    @Override
    @Transactional
    public OcDocumentTask submitVersion(Long documentVersionId, String username) {
        OcDocumentVersion version = requireVersion(documentVersionId);
        requireSupportedVersion(version);
        OcDocumentTask task = taskMapper.selectTaskByVersionId(documentVersionId);
        if (task == null) {
            return createTaskAndSchedule(version, username);
        }

        if (DocumentProcessingConstants.STATUS_PENDING.equals(task.getStatus())) {
            if (task.getAttemptCount() != null && task.getMaxAttempts() != null
                    && task.getAttemptCount() >= task.getMaxAttempts()) {
                throw new ServiceException("文档解析任务已达到最大执行次数");
            }
            dispatch(task);
            return task;
        }
        if (DocumentProcessingConstants.STATUS_FAILED.equals(task.getStatus())) {
            String traceId = buildTraceId(documentVersionId);
            if (!stateService.resetFailedTask(task, traceId, username)) {
                throw new ServiceException("文档解析任务已达到最大执行次数或状态已变化");
            }
            OcDocumentTask resetTask = taskMapper.selectTaskById(task.getTaskId());
            dispatch(resetTask);
            return resetTask;
        }
        if (DocumentProcessingConstants.STATUS_RUNNING.equals(task.getStatus())) {
            throw new ServiceException("文档正在解析，请勿重复提交");
        }
        throw new ServiceException("文档已经解析成功，无需重复提交");
    }

    @Override
    @Transactional
    public OcDocumentTask createTaskAndSchedule(OcDocumentVersion version, String username) {
        requireSupportedVersion(version);
        OcDocumentTask existing = taskMapper.selectTaskByVersionId(
                version.getDocumentVersionId());
        if (existing != null) {
            if (DocumentProcessingConstants.STATUS_PENDING.equals(existing.getStatus())) {
                dispatch(existing);
            }
            return existing;
        }

        OcDocumentTask task = new OcDocumentTask();
        task.setDocumentId(version.getDocumentId());
        task.setDocumentVersionId(version.getDocumentVersionId());
        task.setTaskType(DocumentProcessingConstants.TASK_TYPE_PARSE);
        task.setStatus(DocumentProcessingConstants.STATUS_PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(properties.getMaxAttempts());
        task.setTraceId(buildTraceId(version.getDocumentVersionId()));
        task.setRequestFileHash(version.getFileHash());
        task.setCreateBy(username);
        taskMapper.insertDocumentTask(task);
        dispatch(task);
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

    //将任务提交到专用线程池
    private void dispatchSafely(Long taskId) {
        try {
            taskExecutor.execute(() -> executeTask(taskId));
        } catch (RuntimeException exception) {
            OcDocumentTask task = taskMapper.selectTaskById(taskId);
            if (task != null) {
                fail(task, "TASK_DISPATCH_REJECTED", "文档解析任务队列暂时不可用",
                        exception);
            }
        }
    }

    @Override
    public void executeTask(Long taskId) {
        OcDocumentTask task = taskMapper.selectTaskById(taskId);
        if (task == null) {
            return;
        }

        try {
            if (!stateService.claimTask(taskId, SYSTEM_USER)) {
                return;
            }
            task = taskMapper.selectTaskById(taskId);
            OcDocumentVersion version = requireVersion(task.getDocumentVersionId());
            requireSupportedVersion(version);
            byte[] fileContent = readOriginalFile(version);
            AiDocumentParseResponse response = aiClient.parse(
                    version, fileContent, task.getTraceId());
            validateResponse(version, response);

            byte[] parsedContent = objectMapper.writeValueAsBytes(response);
            String parsedObjectKey = buildParsedObjectKey(version);
            storageService.storeGenerated(parsedContent, parsedObjectKey);
            String processMessage = successMessage(response);
            stateService.completeTask(task, response, parsedObjectKey,
                    processMessage, SYSTEM_USER);
            scheduleIndexSafely(version.getDocumentVersionId());
        } catch (AiDocumentParseException exception) {
            fail(task, exception.getErrorCode(), exception.getMessage(), exception);
        } catch (JsonProcessingException exception) {
            fail(task, "PARSED_RESULT_SERIALIZATION_FAILED",
                    "解析结果序列化失败", exception);
        } catch (IOException exception) {
            fail(task, "DOCUMENT_STORAGE_ERROR",
                    "读取原始文档或保存解析结果失败", exception);
        } catch (Exception exception) {
            fail(task, "DOCUMENT_PROCESSING_ERROR",
                    StringUtils.defaultIfEmpty(exception.getMessage(), "文档解析处理失败"),
                    exception);
        }
    }

    private byte[] readOriginalFile(OcDocumentVersion version) throws IOException {
        try (InputStream inputStream = storageService.open(version.getObjectKey())) {
            byte[] content = inputStream.readAllBytes();
            if (version.getFileSize() != null && content.length != version.getFileSize()) {
                throw new IOException("原始文件大小与版本元数据不一致");
            }
            return content;
        }
    }

    private void scheduleIndexSafely(Long documentVersionId) {
        try {
            indexingService.createTaskAndSchedule(documentVersionId, SYSTEM_USER);
        } catch (RuntimeException exception) {
            log.error("文档解析成功但创建向量索引任务失败，documentVersionId={}",
                    documentVersionId, exception);
        }
    }

    private void validateResponse(OcDocumentVersion version,
                                  AiDocumentParseResponse response) {
        if (StringUtils.isEmpty(response.getFileHash())
                || !response.getFileHash().equalsIgnoreCase(version.getFileHash())) {
            throw new AiDocumentParseException("FILE_HASH_MISMATCH",
                    "FastAPI返回的文件摘要与文档版本不一致", null);
        }
        if (response.getFileSize() == null
                || !response.getFileSize().equals(version.getFileSize())) {
            throw new AiDocumentParseException("FILE_SIZE_MISMATCH",
                    "FastAPI返回的文件大小与文档版本不一致", null);
        }
        if (response.getBlocks() == null || response.getBlockCount() == null
                || response.getBlockCount() != response.getBlocks().size()) {
            throw new AiDocumentParseException("INVALID_PARSE_RESPONSE",
                    "FastAPI返回的文本块统计不完整", null);
        }
    }

    private OcDocumentVersion requireVersion(Long documentVersionId) {
        if (documentVersionId == null) {
            throw new ServiceException("文档版本ID不能为空");
        }
        OcDocumentVersion version = versionMapper.selectVersionById(documentVersionId);
        if (version == null) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        return version;
    }

    private void requireSupportedVersion(OcDocumentVersion version) {
        String extension = StringUtils.defaultIfEmpty(
                version.getFileExtension(), "").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ServiceException("文档解析仅支持PDF、DOCX和TXT文件");
        }
    }

    private void fail(OcDocumentTask task, String errorCode, String errorMessage,
                      Exception exception) {
        String message = truncateMessage(StringUtils.defaultIfEmpty(
                StringUtils.trim(errorMessage), "文档解析失败"));
        try {
            stateService.failTask(task.getTaskId(), task.getDocumentVersionId(),
                    truncateCode(errorCode), message, SYSTEM_USER);
        } catch (RuntimeException stateException) {
            log.error("更新文档解析失败状态异常，taskId={}，traceId={}",
                    task.getTaskId(), task.getTraceId(), stateException);
        }
        log.warn("文档解析失败，taskId={}，traceId={}，errorCode={}",
                task.getTaskId(), task.getTraceId(), errorCode, exception);
    }

    private String buildParsedObjectKey(OcDocumentVersion version) {
        String objectKey = version.getObjectKey();
        int originalPathIndex = objectKey.lastIndexOf("/original/");
        if (originalPathIndex > 0) {
            return objectKey.substring(0, originalPathIndex)
                    + "/parsed/parse-result.json";
        }
        return "documents/" + version.getDocumentId()
                + "/versions/" + version.getVersionNo()
                + "/parsed/parse-result.json";
    }

    private String successMessage(AiDocumentParseResponse response) {
        StringBuilder message = new StringBuilder("解析成功，共")
                .append(response.getBlockCount()).append("个文本块");
        if (response.getWarnings() != null && !response.getWarnings().isEmpty()) {
            message.append("，包含").append(response.getWarnings().size()).append("条提示");
        }
        return truncateMessage(message.toString());
    }

    private String buildTraceId(Long documentVersionId) {
        return "document-" + documentVersionId + "-"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String truncateCode(String errorCode) {
        String normalized = StringUtils.defaultIfEmpty(errorCode, "DOCUMENT_PROCESSING_ERROR");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private String truncateMessage(String message) {
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
