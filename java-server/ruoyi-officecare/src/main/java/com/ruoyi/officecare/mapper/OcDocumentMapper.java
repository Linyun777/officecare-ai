package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcDocument;

/**
 * OfficeCare知识文档数据访问层。
 *
 * @author officecare
 */
public interface OcDocumentMapper
{
    public List<OcDocument> selectDocumentList(OcDocument document);

    public OcDocument selectDocumentById(Long documentId);

    public OcDocument selectDocumentByIdForUpdate(Long documentId);

    public long selectPendingDocumentCount();

    public int countDocumentByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 查询知识库下当前启用、解析成功且索引成功的文档版本。
     */
    public List<Long> selectCurrentIndexedVersionIds(Long knowledgeBaseId);

    public int insertDocument(OcDocument document);

    public int updateDocument(OcDocument document);

    public int updateCurrentVersion(@Param("documentId") Long documentId,
        @Param("currentVersionId") Long currentVersionId, @Param("updateBy") String updateBy);

    public int deleteDocumentByIds(@Param("documentIds") Long[] documentIds,
        @Param("updateBy") String updateBy);
}
