package com.ruoyi.officecare.domain.vo;

import java.io.InputStream;
import com.ruoyi.officecare.domain.OcDocumentVersion;

/**
 * 文档下载流及其元数据。
 *
 * @author officecare
 */
public class DocumentDownload
{
    private final OcDocumentVersion version;

    private final InputStream inputStream;

    public DocumentDownload(OcDocumentVersion version, InputStream inputStream)
    {
        this.version = version;
        this.inputStream = inputStream;
    }

    public OcDocumentVersion getVersion()
    {
        return version;
    }

    public InputStream getInputStream()
    {
        return inputStream;
    }
}
