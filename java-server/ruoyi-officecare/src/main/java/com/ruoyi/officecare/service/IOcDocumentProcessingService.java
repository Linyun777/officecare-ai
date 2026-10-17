package com.ruoyi.officecare.service;

import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcDocumentVersion;

/**
 * OfficeCare文档解析任务服务。
 *
 * @author officecare
 */
public interface IOcDocumentProcessingService
{
    public OcDocumentTask selectTaskByVersionId(Long documentVersionId);

    public OcDocumentTask submitVersion(Long documentVersionId, String username);

    public OcDocumentTask createTaskAndSchedule(OcDocumentVersion version, String username);

    /**
     * 执行一条已持久化的解析任务，供本地线程池和RabbitMQ消费者复用。
     *
     * @param taskId 任务ID
     */
    public void executeTask(Long taskId);
}
