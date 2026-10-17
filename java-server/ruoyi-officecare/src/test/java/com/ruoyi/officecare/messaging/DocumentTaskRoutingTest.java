package com.ruoyi.officecare.messaging;

import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 文档任务路由测试。 */
class DocumentTaskRoutingTest
{
    @Test
    void shouldMapAllSupportedTaskTypes()
    {
        assertEquals(DocumentTaskRouting.PARSE_ROUTING_KEY,
            DocumentTaskRouting.routingKey(DocumentProcessingConstants.TASK_TYPE_PARSE));
        assertEquals(DocumentTaskRouting.INDEX_ROUTING_KEY,
            DocumentTaskRouting.routingKey(DocumentProcessingConstants.TASK_TYPE_INDEX));
        assertEquals(DocumentTaskRouting.DELETE_INDEX_ROUTING_KEY,
            DocumentTaskRouting.routingKey(
                DocumentProcessingConstants.TASK_TYPE_DELETE_INDEX));
    }

    @Test
    void shouldRejectUnknownTaskType()
    {
        assertThrows(ServiceException.class,
            () -> DocumentTaskRouting.routingKey("unknown"));
    }
}
