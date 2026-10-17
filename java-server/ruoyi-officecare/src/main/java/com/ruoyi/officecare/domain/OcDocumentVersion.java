package com.ruoyi.officecare.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * OfficeCare文档版本对象 oc_document_version
 *
 * @author officecare
 */
public class OcDocumentVersion
{
    /** 文档版本ID */
    private Long documentVersionId;

    /** 文档ID */
    private Long documentId;

    /** 版本号 */
    private Integer versionNo;

    /** 存储类型 */
    private String storageType;

    /** OSS Bucket；本地存储时为空 */
    private String bucketName;

    /** 存储对象键 */
    private String objectKey;

    /** 用户上传时的原始文件名 */
    private String originalFileName;

    /** 文件扩展名 */
    private String fileExtension;

    /** MIME类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** SHA-256摘要 */
    private String fileHash;

    /** 解析结果对象键，解析前为空 */
    private String parsedObjectKey;

    /** 页数，解析前为空 */
    private Integer pageCount;

    /** 处理状态（0待处理 1处理中 2成功 3失败） */
    private String processStatus;

    /** 最近处理结果说明 */
    private String processMessage;

    /** 索引状态（0待处理 1处理中 2成功 3失败） */
    private String indexStatus;

    /** 最近索引结果说明 */
    private String indexMessage;

    /** 文本切片与模型配置摘要 */
    private String indexContentHash;

    /** 已写入Milvus的文本切片数 */
    private Integer indexedChunkCount;

    /** Embedding模型名称 */
    private String embeddingModel;

    /** Embedding向量维度 */
    private Integer embeddingDimension;

    /** Milvus Collection名称 */
    private String milvusCollection;

    /** 最近索引成功时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date indexedTime;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getDocumentVersionId()
    {
        return documentVersionId;
    }

    public void setDocumentVersionId(Long documentVersionId)
    {
        this.documentVersionId = documentVersionId;
    }

    public Long getDocumentId()
    {
        return documentId;
    }

    public void setDocumentId(Long documentId)
    {
        this.documentId = documentId;
    }

    public Integer getVersionNo()
    {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo)
    {
        this.versionNo = versionNo;
    }

    public String getStorageType()
    {
        return storageType;
    }

    public void setStorageType(String storageType)
    {
        this.storageType = storageType;
    }

    public String getBucketName()
    {
        return bucketName;
    }

    public void setBucketName(String bucketName)
    {
        this.bucketName = bucketName;
    }

    public String getObjectKey()
    {
        return objectKey;
    }

    public void setObjectKey(String objectKey)
    {
        this.objectKey = objectKey;
    }

    public String getOriginalFileName()
    {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName)
    {
        this.originalFileName = originalFileName;
    }

    public String getFileExtension()
    {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension)
    {
        this.fileExtension = fileExtension;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getFileHash()
    {
        return fileHash;
    }

    public void setFileHash(String fileHash)
    {
        this.fileHash = fileHash;
    }

    public String getParsedObjectKey()
    {
        return parsedObjectKey;
    }

    public void setParsedObjectKey(String parsedObjectKey)
    {
        this.parsedObjectKey = parsedObjectKey;
    }

    public Integer getPageCount()
    {
        return pageCount;
    }

    public void setPageCount(Integer pageCount)
    {
        this.pageCount = pageCount;
    }

    public String getProcessStatus()
    {
        return processStatus;
    }

    public void setProcessStatus(String processStatus)
    {
        this.processStatus = processStatus;
    }

    public String getProcessMessage()
    {
        return processMessage;
    }

    public void setProcessMessage(String processMessage)
    {
        this.processMessage = processMessage;
    }

    public String getIndexStatus()
    {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus)
    {
        this.indexStatus = indexStatus;
    }

    public String getIndexMessage()
    {
        return indexMessage;
    }

    public void setIndexMessage(String indexMessage)
    {
        this.indexMessage = indexMessage;
    }

    public String getIndexContentHash()
    {
        return indexContentHash;
    }

    public void setIndexContentHash(String indexContentHash)
    {
        this.indexContentHash = indexContentHash;
    }

    public Integer getIndexedChunkCount()
    {
        return indexedChunkCount;
    }

    public void setIndexedChunkCount(Integer indexedChunkCount)
    {
        this.indexedChunkCount = indexedChunkCount;
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

    public String getMilvusCollection()
    {
        return milvusCollection;
    }

    public void setMilvusCollection(String milvusCollection)
    {
        this.milvusCollection = milvusCollection;
    }

    public Date getIndexedTime()
    {
        return indexedTime;
    }

    public void setIndexedTime(Date indexedTime)
    {
        this.indexedTime = indexedTime;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
