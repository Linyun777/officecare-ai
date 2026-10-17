package com.ruoyi.officecare.mapper;

import java.io.IOException;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcAgentDraft;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.OcWorkOrder;
import com.ruoyi.officecare.domain.OcWorkOrderRecord;
import com.ruoyi.officecare.domain.vo.OcAssigneeOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户单位与设备档案Mapper XML解析测试。
 *
 * @author officecare
 */
class OcAfterSalesMapperXmlTest
{
    private static final String[] MAPPER_RESOURCES = {
        "mapper/officecare/OcCustomerOrgMapper.xml",
        "mapper/officecare/OcDeviceMapper.xml",
        "mapper/officecare/OcKnowledgeBaseMapper.xml",
        "mapper/officecare/OcWorkOrderMapper.xml",
        "mapper/officecare/OcWorkOrderRecordMapper.xml",
        "mapper/officecare/OcAgentDraftMapper.xml"
    };

    @Test
    void afterSalesMapperXmlShouldBeParsed() throws IOException
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(OcCustomerOrg.class);
        configuration.getTypeAliasRegistry().registerAlias(OcDevice.class);
        configuration.getTypeAliasRegistry().registerAlias(OcKnowledgeBase.class);
        configuration.getTypeAliasRegistry().registerAlias(OcWorkOrder.class);
        configuration.getTypeAliasRegistry().registerAlias(
            OcWorkOrderRecord.class);
        configuration.getTypeAliasRegistry().registerAlias(
            OcAssigneeOption.class);
        configuration.getTypeAliasRegistry().registerAlias(OcAgentDraft.class);
        for (String resource : MAPPER_RESOURCES)
        {
            try (InputStream inputStream = Resources.getResourceAsStream(resource))
            {
                XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments());
                mapperBuilder.parse();
            }
        }

        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcCustomerOrgMapper"
                + ".selectCustomerOrgList"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcDeviceMapper.selectDeviceList"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcDeviceMapper.countDeviceByModelId"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper"
                + ".selectActiveKnowledgeBaseByModelId"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcWorkOrderMapper.selectWorkOrderList"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcWorkOrderMapper.updateStatus"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcWorkOrderRecordMapper"
                + ".selectRecordListByWorkOrderId"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcAgentDraftMapper"
                + ".selectDraftForUpdate"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcAgentDraftMapper"
                + ".markDraftConfirmed"));
    }
}
