package com.ruoyi.officecare.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OfficeCare知识文档对象 oc_document
 *
 * @author officecare
 */
public class OcDocument extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long documentId;

    /** 所属知识库ID */
    private Long knowledgeBaseId;

    /** 知识库名称（查询展示字段） */
    private String knowledgeBaseName;

    /** 文档标题 */
    private String documentTitle;

    /** 当前版本ID */
    private Long currentVersionId;

    /** 当前版本号（查询展示字段） */
    private Integer currentVersionNo;

    /** 当前版本原始文件名（查询展示字段） */
    private String originalFileName;

    /** 当前版本扩展名（查询展示字段） */
    private String fileExtension;

    /** 当前版本文件大小（查询展示字段） */
    private Long fileSize;

    /** 当前版本处理状态（查询展示字段） */
    private String processStatus;

    /** 当前版本处理说明（查询展示字段） */
    private String processMessage;

    /** 当前版本页数（查询展示字段） */
    private Integer pageCount;

    /** 当前版本索引状态（查询展示字段） */
    private String indexStatus;

    /** 当前版本索引说明（查询展示字段） */
    private String indexMessage;

    /** 当前版本索引切片数（查询展示字段） */
    private Integer indexedChunkCount;

    /** 文档状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getDocumentId()
    {
        return documentId;
    }

    public void setDocumentId(Long documentId)
    {
        this.documentId = documentId;
    }

    @NotNull(message = "所属知识库不能为空")
    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getKnowledgeBaseName()
    {
        return knowledgeBaseName;
    }

    public void setKnowledgeBaseName(String knowledgeBaseName)
    {
        this.knowledgeBaseName = knowledgeBaseName;
    }

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题长度不能超过200个字符")
    public String getDocumentTitle()
    {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle)
    {
        this.documentTitle = documentTitle;
    }

    public Long getCurrentVersionId()
    {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId)
    {
        this.currentVersionId = currentVersionId;
    }

    public Integer getCurrentVersionNo()
    {
        return currentVersionNo;
    }

    public void setCurrentVersionNo(Integer currentVersionNo)
    {
        this.currentVersionNo = currentVersionNo;
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

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
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

    public Integer getPageCount()
    {
        return pageCount;
    }

    public void setPageCount(Integer pageCount)
    {
        this.pageCount = pageCount;
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

    public Integer getIndexedChunkCount()
    {
        return indexedChunkCount;
    }

    public void setIndexedChunkCount(Integer indexedChunkCount)
    {
        this.indexedChunkCount = indexedChunkCount;
    }

    @NotBlank(message = "文档状态不能为空")
    @Pattern(regexp = "^[01]$", message = "文档状态值不正确")
    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("documentId", getDocumentId())
            .append("knowledgeBaseId", getKnowledgeBaseId())
            .append("knowledgeBaseName", getKnowledgeBaseName())
            .append("documentTitle", getDocumentTitle())
            .append("currentVersionId", getCurrentVersionId())
            .append("currentVersionNo", getCurrentVersionNo())
            .append("originalFileName", getOriginalFileName())
            .append("fileExtension", getFileExtension())
            .append("fileSize", getFileSize())
            .append("processStatus", getProcessStatus())
            .append("processMessage", getProcessMessage())
            .append("pageCount", getPageCount())
            .append("indexStatus", getIndexStatus())
            .append("indexMessage", getIndexMessage())
            .append("indexedChunkCount", getIndexedChunkCount())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
