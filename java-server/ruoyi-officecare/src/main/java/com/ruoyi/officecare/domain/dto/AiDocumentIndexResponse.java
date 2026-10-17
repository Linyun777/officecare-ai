package com.ruoyi.officecare.domain.dto;

/**
 * FastAPI文档向量索引成功响应。
 *
 * @author officecare
 */
public class AiDocumentIndexResponse
{
    private Long documentId;

    private Long documentVersionId;

    private String collectionName;

    private String embeddingModel;

    private Integer embeddingDimension;

    private Integer chunkCount;

    private Integer indexedCharacterCount;

    private String indexContentHash;

    private String indexedAt;

    public Long getDocumentId()
    {
        return documentId;
    }

    public void setDocumentId(Long documentId)
    {
        this.documentId = documentId;
    }

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

    public String getEmbeddingModel()
    {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel)
    {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDimension()
    {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension)
    {
        this.embeddingDimension = embeddingDimension;
    }

    public Integer getChunkCount()
    {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount)
    {
        this.chunkCount = chunkCount;
    }

    public Integer getIndexedCharacterCount()
    {
        return indexedCharacterCount;
    }

    public void setIndexedCharacterCount(Integer indexedCharacterCount)
    {
        this.indexedCharacterCount = indexedCharacterCount;
    }

    public String getIndexContentHash()
    {
        return indexContentHash;
    }

    public void setIndexContentHash(String indexContentHash)
    {
        this.indexContentHash = indexContentHash;
    }

    public String getIndexedAt()
    {
        return indexedAt;
    }

    public void setIndexedAt(String indexedAt)
    {
        this.indexedAt = indexedAt;
    }
}
