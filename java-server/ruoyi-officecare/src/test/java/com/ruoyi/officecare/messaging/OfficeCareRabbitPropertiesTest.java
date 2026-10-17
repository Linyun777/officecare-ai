package com.ruoyi.officecare.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** RabbitMQ参数和指数退避测试。 */
class OfficeCareRabbitPropertiesTest
{
    @Test
    void retryDelayShouldGrowExponentiallyAndRespectCap()
    {
        OfficeCareRabbitProperties properties = new OfficeCareRabbitProperties();
        properties.setRetryInitialDelaySeconds(30);
        properties.setRetryMaxDelaySeconds(100);

        assertEquals(30, properties.retryDelaySeconds(1));
        assertEquals(60, properties.retryDelaySeconds(2));
        assertEquals(100, properties.retryDelaySeconds(3));
        assertEquals(100, properties.retryDelaySeconds(20));
    }

    @Test
    void queueTypeAndConcurrencyShouldBeValidated()
    {
        OfficeCareRabbitProperties properties = new OfficeCareRabbitProperties();

        assertThrows(IllegalArgumentException.class,
            () -> properties.setQueueType("stream"));
        assertThrows(IllegalArgumentException.class,
            () -> properties.setConsumerConcurrency(0));
        assertThrows(IllegalArgumentException.class,
            () -> properties.setPrefetchCount(21));
    }
}
