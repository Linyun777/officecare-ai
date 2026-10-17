package com.ruoyi.officecare.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * OfficeCare文档处理任务对象 oc_document_task。
 *
 * @author officecare
 */
public class OcDocumentTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 文档ID */
    private Long documentId;

    /** 文档版本ID */
    private Long documentVersionId;

    /** 任务类型 */
    private String taskType;

    /** 状态（0待处理 1处理中 2成功 3失败） */
    private String status;

    /** 已执行次数 */
    private Integer attemptCount;

    /** 最大执行次数 */
    private Integer maxAttempts;

    /** 跨服务追踪ID */
    private String traceId;

    /** 请求文件SHA-256 */
    private String requestFileHash;

    /** FastAPI返回文件SHA-256 */
    private String resultFileHash;

    /** 页数 */
    private Integer pageCount;

    /** 文本块数量 */
    private Integer blockCount;

    /** 字符数量 */
    private Integer characterCount;

    /** 最近错误码 */
    private String errorCode;

    /** 最近错误说明 */
    private String errorMessage;

    /** 最近开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedTime;

    /** 最近完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedTime;

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
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

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(String taskType)
    {
        this.taskType = taskType;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getAttemptCount()
    {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount)
    {
        this.attemptCount = attemptCount;
    }

    public Integer getMaxAttempts()
    {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts)
    {
        this.maxAttempts = maxAttempts;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public String getRequestFileHash()
    {
        return requestFileHash;
    }

    public void setRequestFileHash(String requestFileHash)
    {
        this.requestFileHash = requestFileHash;
    }

    public String getResultFileHash()
    {
        return resultFileHash;
    }

    public void setResultFileHash(String resultFileHash)
    {
        this.resultFileHash = resultFileHash;
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

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode(String errorCode)
    {
        this.errorCode = errorCode;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public Date getStartedTime()
    {
        return startedTime;
    }

    public void setStartedTime(Date startedTime)
    {
        this.startedTime = startedTime;
    }

    public Date getFinishedTime()
    {
        return finishedTime;
    }

    public void setFinishedTime(Date finishedTime)
    {
        this.finishedTime = finishedTime;
    }
}

