package com.ruoyi.officecare.domain.dto;

import java.util.List;

/**
 * Java提交给FastAPI的文档向量索引请求。
 *
 * @author officecare
 */
public class AiDocumentIndexRequest
{
    private Long knowledgeBaseId;

    private Long brandId;

    private Long modelId;

    private Long documentId;

    private Long documentVersionId;

    private Integer versionNo;

    private String documentTitle;

    private String originalFileName;

    private String fileHash;

    private Integer blockCount;

    private Integer characterCount;

    private List<AiDocumentParseResponse.Block> blocks;

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getBrandId()
    {
        return brandId;
    }

    public void setBrandId(Long brandId)
    {
        this.brandId = brandId;
    }

    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
    }

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

    public Integer getVersionNo()
    {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo)
    {
        this.versionNo = versionNo;
    }

    public String getDocumentTitle()
    {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle)
    {
        this.documentTitle = documentTitle;
    }

    public String getOriginalFileName()
    {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName)
    {
        this.originalFileName = originalFileName;
    }

    public String getFileHash()
    {
        return fileHash;
    }

    public void setFileHash(String fileHash)
    {
        this.fileHash = fileHash;
    }

    public Integer getBlockCount()
    {
        return blockCount;
    }

    public void setBlockCount(Integer blockCount)
    {
        this.blockCount = blockCount;
    }

    public Integer getCharacterCount()
    {
        return characterCount;
    }

    public void setCharacterCount(Integer characterCount)
    {
        this.characterCount = characterCount;
    }

    public List<AiDocumentParseResponse.Block> getBlocks()
    {
        return blocks;
    }

    public void setBlocks(List<AiDocumentParseResponse.Block> blocks)
    {
        this.blocks = blocks;
    }
}
