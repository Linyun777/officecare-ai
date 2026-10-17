package com.ruoyi.officecare.service.impl;

import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.domain.OcWorkOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 售后工单AI建议检索问句测试。 */
class OcWorkOrderServiceImplTest
{
    @Test
    void aiQuestionShouldUseOnlyFaultDescription()
    {
        OcWorkOrder workOrder = new OcWorkOrder();
        workOrder.setBrandName("Brother");
        workOrder.setModelName("DCP-L2550DW");
        workOrder.setTitle("Brother DCP-L2550DW打印质量问题");
        workOrder.setFaultDescription("打印页面每隔94毫米出现重复斑点");

        String question = OcWorkOrderServiceImpl.buildAiQuestion(workOrder);

        assertEquals("打印页面每隔94毫米出现重复斑点", question);
        assertFalse(question.contains("Brother"));
        assertFalse(question.contains("DCP-L2550DW"));
        assertFalse(question.contains("请仅依据"));
    }

    @Test
    void aiQuestionShouldKeepFirstFiveHundredFaultCharacters()
    {
        OcWorkOrder workOrder = new OcWorkOrder();
        workOrder.setFaultDescription("  94毫米" + "故障".repeat(300) + "  ");

        String question = OcWorkOrderServiceImpl.buildAiQuestion(workOrder);

        assertEquals(500, question.length());
        assertEquals("94毫米", question.substring(0, 4));
    }
}
