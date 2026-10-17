package com.ruoyi.officecare.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.officecare.domain.vo.OfficeCareDashboardSummary;
import com.ruoyi.officecare.domain.vo.OfficeCareMqMetrics;
import com.ruoyi.officecare.mapper.OcBrandMapper;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;
import com.ruoyi.officecare.mapper.OcProductModelMapper;
import com.ruoyi.officecare.mapper.OcWorkOrderMapper;
import com.ruoyi.officecare.messaging.OfficeCareRabbitProperties;
import com.ruoyi.officecare.service.IOfficeCareDashboardService;

/**
 * OfficeCare工作台服务实现
 *
 * @author officecare
 */
@Service
public class OfficeCareDashboardServiceImpl implements IOfficeCareDashboardService
{
    private static final String MODULE_NAME = "OfficeCare AI";

    private static final String MODULE_VERSION = "0.8.0";

    private static final String MODULE_STATUS = "AFTER_SALES_MVP_READY";

    @Autowired
    private OcBrandMapper brandMapper;

    @Autowired
    private OcProductModelMapper productModelMapper;

    @Autowired
    private OcKnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private OcDocumentMapper documentMapper;

    @Autowired
    private OcWorkOrderMapper workOrderMapper;

    @Autowired
    private OcOutboxEventMapper outboxEventMapper;

    @Autowired
    private OfficeCareRabbitProperties rabbitProperties;

    @Override
    public OfficeCareDashboardSummary selectDashboardSummary()
    {
        OfficeCareDashboardSummary summary = new OfficeCareDashboardSummary();
        summary.setModuleName(MODULE_NAME);
        summary.setModuleVersion(MODULE_VERSION);
        summary.setStatus(MODULE_STATUS);
        summary.setBrandCount(brandMapper.selectBrandCount());
        summary.setProductModelCount(productModelMapper.selectProductModelCount());
        summary.setKnowledgeBaseCount(knowledgeBaseMapper.selectKnowledgeBaseCount());
        summary.setPendingDocumentCount(documentMapper.selectPendingDocumentCount());
        summary.setPendingWorkOrderCount(
            workOrderMapper.selectPendingWorkOrderCount());
        OfficeCareMqMetrics messageQueue = outboxEventMapper.selectMqMetrics();
        if (messageQueue == null)
        {
            messageQueue = new OfficeCareMqMetrics();
        }
        messageQueue.setEnabled(rabbitProperties.isEnabled());
        summary.setMessageQueue(messageQueue);
        return summary;
    }
}
