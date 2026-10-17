package com.ruoyi.officecare.domain.dto;

import java.util.List;

/**
 * FastAPI文档解析成功响应。
 *
 * @author officecare
 */
public class AiDocumentParseResponse
{
    private String fileName;

    private String fileType;

    private String contentType;

    private Long fileSize;

    private String fileHash;

    private Integer pageCount;

    private Integer blockCount;

    private Integer characterCount;

    private List<Block> blocks;

    private List<String> warnings;

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFileType()
    {
        return fileType;
    }

    public void setFileType(String fileType)
    {
        this.fileType = fileType;
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

    public Integer getPageCount()
    {
        return pageCount;
    }

    public void setPageCount(Integer pageCount)
    {
        this.pageCount = pageCount;
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

    public List<Block> getBlocks()
    {
        return blocks;
    }

    public void setBlocks(List<Block> blocks)
    {
        this.blocks = blocks;
    }

    public List<String> getWarnings()
    {
        return warnings;
    }

    public void setWarnings(List<String> warnings)
    {
        this.warnings = warnings;
    }

    /**
     * 解析后的有序文本块。
     */
    public static class Block
    {
        private Integer sequence;

        private Integer pageNumber;

        private String blockType;

        private String text;

        public Integer getSequence()
        {
            return sequence;
        }

        public void setSequence(Integer sequence)
        {
            this.sequence = sequence;
        }

        public Integer getPageNumber()
        {
            return pageNumber;
        }

        public void setPageNumber(Integer pageNumber)
        {
            this.pageNumber = pageNumber;
        }

        public String getBlockType()
        {
            return blockType;
        }

        public void setBlockType(String blockType)
        {
            this.blockType = blockType;
        }

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }
    }
}

