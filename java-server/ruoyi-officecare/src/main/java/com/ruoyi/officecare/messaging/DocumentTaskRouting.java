package com.ruoyi.officecare.messaging;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;

/**
 * 文档任务类型与RabbitMQ路由键的唯一映射。
 *
 * @author officecare
 */
public final class DocumentTaskRouting
{
    public static final String PARSE_ROUTING_KEY = "document.parse";

    public static final String INDEX_ROUTING_KEY = "document.index";

    public static final String DELETE_INDEX_ROUTING_KEY = "document.delete-index";

    public static final String DEAD_ROUTING_KEY = "document.dead";

    private DocumentTaskRouting()
    {
    }

    public static String routingKey(String taskType)
    {
        return switch (taskType)
        {
            case DocumentProcessingConstants.TASK_TYPE_PARSE -> PARSE_ROUTING_KEY;
            case DocumentProcessingConstants.TASK_TYPE_INDEX -> INDEX_ROUTING_KEY;
            case DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX ->
                DELETE_INDEX_ROUTING_KEY;
            default -> throw new ServiceException("不支持的文档任务类型：" + taskType);
        };
    }
}
