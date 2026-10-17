package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcDocumentVersion;

/**
 * OfficeCare文档版本数据访问层。
 *
 * @author officecare
 */
public interface OcDocumentVersionMapper
{
    public List<OcDocumentVersion> selectVersionListByDocumentId(Long documentId);

    public OcDocumentVersion selectVersionById(Long documentVersionId);

    public Long selectKnowledgeBaseIdByVersionId(Long documentVersionId);

    public OcDocumentVersion selectVersionByHash(@Param("documentId") Long documentId,
        @Param("fileHash") String fileHash);

    public List<OcDocumentVersion> selectVersionListByDocumentIds(
        @Param("documentIds") Long[] documentIds);

    public int selectNextVersionNo(Long documentId);

    public int insertDocumentVersion(OcDocumentVersion version);

    public int updateVersionProcessing(@Param("documentVersionId") Long documentVersionId,
        @Param("processMessage") String processMessage);

    public int updateVersionSuccess(@Param("documentVersionId") Long documentVersionId,
        @Param("parsedObjectKey") String parsedObjectKey,
        @Param("pageCount") Integer pageCount,
        @Param("processMessage") String processMessage);

    public int updateVersionFailed(@Param("documentVersionId") Long documentVersionId,
        @Param("processMessage") String processMessage);

    public int updateVersionPending(@Param("documentVersionId") Long documentVersionId,
        @Param("processMessage") String processMessage);

    public int updateVersionIndexProcessing(
        @Param("documentVersionId") Long documentVersionId,
        @Param("indexMessage") String indexMessage);

    public int updateVersionIndexSuccess(
        @Param("documentVersionId") Long documentVersionId,
        @Param("indexContentHash") String indexContentHash,
        @Param("indexedChunkCount") Integer indexedChunkCount,
        @Param("embeddingModel") String embeddingModel,
        @Param("embeddingDimension") Integer embeddingDimension,
        @Param("milvusCollection") String milvusCollection,
        @Param("indexMessage") String indexMessage);

    public int updateVersionIndexFailed(
        @Param("documentVersionId") Long documentVersionId,
        @Param("indexMessage") String indexMessage);

    public int updateVersionIndexPending(
        @Param("documentVersionId") Long documentVersionId,
        @Param("indexMessage") String indexMessage);

    public int deleteVersionsByDocumentIds(@Param("documentIds") Long[] documentIds,
        @Param("updateBy") String updateBy);
}
