package com.ruoyi.officecare.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.officecare.domain.OcDocument;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.vo.DocumentDownload;

/**
 * OfficeCare知识文档服务。
 *
 * @author officecare
 */
public interface IOcDocumentService
{
    public List<OcDocument> selectDocumentList(OcDocument document);

    public OcDocument selectDocumentById(Long documentId);

    public List<OcDocumentVersion> selectVersionList(Long documentId);

    public OcDocument uploadDocument(Long knowledgeBaseId, String documentTitle, String remark,
        MultipartFile file, String username);

    public OcDocument uploadNewVersion(Long documentId, MultipartFile file, String username);

    public int updateDocument(OcDocument document);

    public int deleteDocumentByIds(Long[] documentIds, String updateBy);

    public DocumentDownload openDocumentVersion(Long documentVersionId);
}
