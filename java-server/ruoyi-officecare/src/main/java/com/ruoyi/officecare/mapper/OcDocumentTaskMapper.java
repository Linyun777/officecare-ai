package com.ruoyi.officecare.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcDocumentTask;

/**
 * OfficeCare文档处理任务数据访问层。
 *
 * @author officecare
 */
public interface OcDocumentTaskMapper
{
    public OcDocumentTask selectTaskById(Long taskId);

    public OcDocumentTask selectTaskByVersionId(Long documentVersionId);

    public OcDocumentTask selectTaskByVersionIdAndType(
        @Param("documentVersionId") Long documentVersionId,
        @Param("taskType") String taskType);

    public int insertDocumentTask(OcDocumentTask task);

    public int claimPendingTask(@Param("taskId") Long taskId,
        @Param("updateBy") String updateBy);

    public int resetFailedTask(@Param("taskId") Long taskId,
        @Param("traceId") String traceId, @Param("updateBy") String updateBy);

    public int resetRunningTask(@Param("taskId") Long taskId,
        @Param("traceId") String traceId, @Param("updateBy") String updateBy);

    public int resetFailedTaskManually(@Param("taskId") Long taskId,
        @Param("traceId") String traceId, @Param("updateBy") String updateBy);

    public int updateTaskSuccess(@Param("taskId") Long taskId,
        @Param("resultFileHash") String resultFileHash,
        @Param("pageCount") Integer pageCount,
        @Param("blockCount") Integer blockCount,
        @Param("characterCount") Integer characterCount,
        @Param("updateBy") String updateBy);

    public int updateTaskFailed(@Param("taskId") Long taskId,
        @Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage,
        @Param("updateBy") String updateBy);
}
