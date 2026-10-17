package com.ruoyi.officecare.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档对象存储抽象。
 *
 * 后续接入OSS时只新增实现，不改变文档业务服务。
 *
 * @author officecare
 */
public interface DocumentStorageService
{
    public StoredDocumentFile store(MultipartFile file, String objectKey) throws IOException;

    public StoredDocumentFile storeGenerated(byte[] content, String objectKey) throws IOException;

    public InputStream open(String objectKey) throws IOException;

    public void delete(String objectKey) throws IOException;
}
