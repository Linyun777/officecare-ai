package com.ruoyi.officecare.domain.dto;

/**
 * FastAPI删除文档版本向量响应。
 *
 * @author officecare
 */
public class AiDocumentIndexDeleteResponse
{
    private Long documentVersionId;

    private String collectionName;

    private Integer deletedCount;

    public Long getDocumentVersionId()
    {
        return documentVersionId;
    }

    public void setDocumentVersionId(Long documentVersionId)
    {
        this.documentVersionId = documentVersionId;
    }

    public String getCollectionName()
    {
        return collectionName;
    }

    public void setCollectionName(String collectionName)
    {
        this.collectionName = collectionName;
    }

    public Integer getDeletedCount()
    {
        return deletedCount;
    }

    public void setDeletedCount(Integer deletedCount)
    {
        this.deletedCount = deletedCount;
    }
}
