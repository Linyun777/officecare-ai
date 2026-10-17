package com.ruoyi.officecare.service;

import com.ruoyi.officecare.domain.OcDocumentTask;

/**
 * OfficeCare文档向量索引任务服务。
 *
 * @author officecare
 */
public interface IOcDocumentIndexingService
{
    public OcDocumentTask selectTaskByVersionId(Long documentVersionId);

    public OcDocumentTask submitVersion(Long documentVersionId, String username);

    public OcDocumentTask createTaskAndSchedule(Long documentVersionId, String username);

    public void prepareDeletionTasks(Long[] documentIds, String username);

    /**
     * 执行一条已持久化的索引或删索引任务，供本地线程池和RabbitMQ消费者复用。
     *
     * @param taskId 任务ID
     */
    public void executeTask(Long taskId);
}
