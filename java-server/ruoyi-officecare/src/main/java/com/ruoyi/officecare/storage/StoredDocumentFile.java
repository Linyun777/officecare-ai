package com.ruoyi.officecare.storage;

/**
 * 文档写入存储后的结果。
 *
 * @author officecare
 */
public class StoredDocumentFile
{
    private final String storageType;

    private final String objectKey;

    private final long fileSize;

    private final String fileHash;

    public StoredDocumentFile(String storageType, String objectKey, long fileSize, String fileHash)
    {
        this.storageType = storageType;
        this.objectKey = objectKey;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
    }

    public String getStorageType()
    {
        return storageType;
    }

    public String getObjectKey()
    {
        return objectKey;
    }

    public long getFileSize()
    {
        return fileSize;
    }

    public String getFileHash()
    {
        return fileHash;
    }
}
