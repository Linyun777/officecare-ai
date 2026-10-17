package com.ruoyi.officecare.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.domain.OcDocument;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.vo.DocumentDownload;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.service.IOcDocumentIndexingService;
import com.ruoyi.officecare.service.IOcDocumentProcessingService;
import com.ruoyi.officecare.service.IOcDocumentService;
import com.ruoyi.officecare.storage.DocumentStorageService;
import com.ruoyi.officecare.storage.StoredDocumentFile;

/**
 * OfficeCare知识文档服务实现。
 *
 * @author officecare
 */
@Service
public class OcDocumentServiceImpl implements IOcDocumentService {
    private static final String NORMAL_STATUS = "0";

    private static final String PENDING_STATUS = "0";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");

    private final OcDocumentMapper documentMapper;

    private final OcDocumentVersionMapper versionMapper;

    private final OcKnowledgeBaseMapper knowledgeBaseMapper;

    private final DocumentStorageService storageService;

    private final IOcDocumentProcessingService processingService;

    private final IOcDocumentIndexingService indexingService;

    public OcDocumentServiceImpl(OcDocumentMapper documentMapper,
                                 OcDocumentVersionMapper versionMapper,
                                 OcKnowledgeBaseMapper knowledgeBaseMapper,
                                 DocumentStorageService storageService,
                                 IOcDocumentProcessingService processingService,
                                 IOcDocumentIndexingService indexingService) {
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.storageService = storageService;
        this.processingService = processingService;
        this.indexingService = indexingService;
    }

    @Override
    public List<OcDocument> selectDocumentList(OcDocument document) {
        return documentMapper.selectDocumentList(document);
    }

    @Override
    public OcDocument selectDocumentById(Long documentId) {
        return documentMapper.selectDocumentById(documentId);
    }

    @Override
    public List<OcDocumentVersion> selectVersionList(Long documentId) {
        ensureDocumentExists(documentId);
        return versionMapper.selectVersionListByDocumentId(documentId);
    }

    /**
     * 上传新文档
     * @param knowledgeBaseId
     * @param documentTitle
     * @param remark
     * @param file
     * @param username
     * @return
     */
    @Override
    @Transactional
    public OcDocument uploadDocument(Long knowledgeBaseId, String documentTitle, String remark,
                                     MultipartFile file, String username) {

        OcKnowledgeBase knowledgeBase = requireActiveKnowledgeBase(knowledgeBaseId);
        //UploadFileInfo纪录类
        UploadFileInfo fileInfo = validateFile(file);

        OcDocument document = new OcDocument();
        document.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        document.setDocumentTitle(normalizeDocumentTitle(documentTitle, fileInfo.originalFileName()));
        document.setStatus(NORMAL_STATUS);
        document.setCreateBy(username);
        document.setRemark(StringUtils.trim(remark));
        documentMapper.insertDocument(document);

        // 上传第一个版本
        OcDocumentVersion version = null;
        try {
            version = storeVersion(document, 1, file, fileInfo, username);
            documentMapper.updateCurrentVersion(document.getDocumentId(),
                    version.getDocumentVersionId(), username);
            return documentMapper.selectDocumentById(document.getDocumentId());
        } catch (RuntimeException e) {
            if (version != null) {
                safeDelete(version.getObjectKey());
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public OcDocument uploadNewVersion(Long documentId, MultipartFile file, String username) {
        OcDocument document = documentMapper.selectDocumentByIdForUpdate(documentId);
        if (document == null) {
            throw new ServiceException("文档不存在或已被删除");
        }
        requireActiveKnowledgeBase(document.getKnowledgeBaseId());
        UploadFileInfo fileInfo = validateFile(file);
        int versionNo = versionMapper.selectNextVersionNo(documentId);
        OcDocumentVersion version = null;
        try {
            version = storeVersion(document, versionNo, file, fileInfo, username);
            documentMapper.updateCurrentVersion(documentId, version.getDocumentVersionId(), username);
            return documentMapper.selectDocumentById(documentId);
        } catch (RuntimeException e) {
            if (version != null) {
                safeDelete(version.getObjectKey());
            }
            throw e;
        }
    }

    @Override
    public int updateDocument(OcDocument document) {
        if (document.getDocumentId() == null || documentMapper.selectDocumentById(
                document.getDocumentId()) == null) {
            throw new ServiceException("文档不存在或已被删除");
        }
        document.setDocumentTitle(StringUtils.trim(document.getDocumentTitle()));
        document.setRemark(StringUtils.trim(document.getRemark()));
        if (StringUtils.isEmpty(document.getStatus())) {
            document.setStatus(NORMAL_STATUS);
        }
        return documentMapper.updateDocument(document);
    }

    @Override
    @Transactional
    public int deleteDocumentByIds(Long[] documentIds, String updateBy) {
        indexingService.prepareDeletionTasks(documentIds, updateBy);
        versionMapper.deleteVersionsByDocumentIds(documentIds, updateBy);
        return documentMapper.deleteDocumentByIds(documentIds, updateBy);
    }

    @Override
    public DocumentDownload openDocumentVersion(Long documentVersionId) {
        OcDocumentVersion version = versionMapper.selectVersionById(documentVersionId);
        if (version == null) {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        try {
            InputStream inputStream = storageService.open(version.getObjectKey());
            return new DocumentDownload(version, inputStream);
        } catch (IOException e) {
            throw storageException("读取文档文件失败", e);
        }
    }

    private OcDocumentVersion storeVersion(OcDocument document, int versionNo,
                                           MultipartFile file, UploadFileInfo fileInfo, String username) {
        String objectKey = buildObjectKey(document.getKnowledgeBaseId(),
                document.getDocumentId(), versionNo, fileInfo.extension());
        StoredDocumentFile storedFile = null;
        try {
            storedFile = storageService.store(file, objectKey);
            if (versionMapper.selectVersionByHash(document.getDocumentId(),
                    storedFile.getFileHash()) != null) {
                safeDelete(storedFile.getObjectKey());
                throw new ServiceException("该文件内容已作为此文档的历史版本上传");
            }

            OcDocumentVersion version = new OcDocumentVersion();
            version.setDocumentId(document.getDocumentId());
            version.setVersionNo(versionNo);
            version.setStorageType(storedFile.getStorageType());
            version.setObjectKey(storedFile.getObjectKey());
            version.setOriginalFileName(fileInfo.originalFileName());
            version.setFileExtension(fileInfo.extension());
            version.setContentType(normalizeContentType(file.getContentType(), fileInfo.extension()));
            version.setFileSize(storedFile.getFileSize());
            version.setFileHash(storedFile.getFileHash());
            version.setProcessStatus(PENDING_STATUS);
            version.setProcessMessage("等待文档解析");
            version.setCreateBy(username);
            versionMapper.insertDocumentVersion(version);
            processingService.createTaskAndSchedule(version, username);
            return version;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            if (storedFile != null) {
                safeDelete(storedFile.getObjectKey());
            }
            throw storageException("保存文档文件失败", e);
        }
    }

    private OcKnowledgeBase requireActiveKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            throw new ServiceException("所属知识库不能为空");
        }
        OcKnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new ServiceException("所属知识库不存在或已被删除");
        }
        if (!NORMAL_STATUS.equals(knowledgeBase.getStatus())) {
            throw new ServiceException("所属知识库已停用，不能上传文档");
        }
        return knowledgeBase;
    }

    private OcDocument ensureDocumentExists(Long documentId) {
        OcDocument document = documentMapper.selectDocumentById(documentId);
        if (document == null) {
            throw new ServiceException("文档不存在或已被删除");
        }
        return document;
    }

    private UploadFileInfo validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String originalFileName = normalizeOriginalFileName(file.getOriginalFilename());
        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == originalFileName.length() - 1) {
            throw new ServiceException("文件必须包含有效扩展名");
        }
        String extension = originalFileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException("仅支持PDF、DOCX和TXT文件");
        }
        return new UploadFileInfo(originalFileName, extension);
    }

    private String normalizeOriginalFileName(String fileName) {
        String normalized = StringUtils.trim(fileName);
        if (StringUtils.isEmpty(normalized)) {
            throw new ServiceException("原始文件名不能为空");
        }
        normalized = normalized.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (normalized.length() > 255) {
            throw new ServiceException("原始文件名长度不能超过255个字符");
        }
        return normalized;
    }

    private String normalizeDocumentTitle(String title, String originalFileName) {
        String normalized = StringUtils.trim(title);
        if (StringUtils.isEmpty(normalized)) {
            int extensionIndex = originalFileName.lastIndexOf('.');
            normalized = extensionIndex > 0
                    ? originalFileName.substring(0, extensionIndex) : originalFileName;
        }
        if (normalized.length() > 200) {
            throw new ServiceException("文档标题长度不能超过200个字符");
        }
        return normalized;
    }

    private String normalizeContentType(String contentType, String extension) {
        String normalized = StringUtils.trim(contentType);
        if (StringUtils.isNotEmpty(normalized)
                && normalized.length() <= 100
                && normalized.matches("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$")) {
            return normalized;
        }
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private String buildObjectKey(Long knowledgeBaseId, Long documentId, int versionNo,
                                  String extension) {
        return "knowledge-bases/" + knowledgeBaseId
                + "/documents/" + documentId
                + "/versions/" + versionNo
                + "/original/" + UUID.randomUUID().toString().replace("-", "")
                + "." + extension;
    }

    private void safeDelete(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (IOException ignored) {
            // 文件清理失败不覆盖原始业务异常，后续可由运维清理孤立对象。
        }
    }

    private ServiceException storageException(String message, Exception exception) {
        return new ServiceException(message).setDetailMessage(exception.getMessage());
    }

    private record UploadFileInfo(String originalFileName, String extension) {
    }
}
