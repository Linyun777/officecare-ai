package com.ruoyi.officecare.support;

import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工单四状态流转规则测试。 */
class OcWorkOrderStatusPolicyTest
{
    @Test
    void shouldAllowMainProcessingFlow()
    {
        assertTrue(OcWorkOrderStatusPolicy.canTransition("0", "1"));
        assertTrue(OcWorkOrderStatusPolicy.canTransition("1", "2"));
        assertTrue(OcWorkOrderStatusPolicy.canTransition("2", "3"));
    }

    @Test
    void shouldAllowReopenCompletedOrder()
    {
        assertTrue(OcWorkOrderStatusPolicy.canTransition("2", "1"));
    }

    @Test
    void shouldRejectSkippingAndClosedTransitions()
    {
        assertFalse(OcWorkOrderStatusPolicy.canTransition("0", "2"));
        assertFalse(OcWorkOrderStatusPolicy.canTransition("3", "1"));
        assertThrows(ServiceException.class,
            () -> OcWorkOrderStatusPolicy.validate("0", "2"));
    }
}
