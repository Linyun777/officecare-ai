package com.ruoyi.officecare.mapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcOutboxEvent;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** MQ Outbox与任务恢复SQL映射解析测试。 */
class OcMessagingMapperXmlTest
{
    @Test
    void messagingMapperXmlShouldBeParsed() throws IOException
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(OcDocumentTask.class);
        configuration.getTypeAliasRegistry().registerAlias(OcOutboxEvent.class);
        parse(configuration, "mapper/officecare/OcDocumentTaskMapper.xml");
        parse(configuration, "mapper/officecare/OcOutboxEventMapper.xml");

        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcDocumentTaskMapper.resetRunningTask"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcOutboxEventMapper.selectReadyEvents"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcOutboxEventMapper.reactivateDeadEvent"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcOutboxEventMapper.recoverStalePublishing"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcOutboxEventMapper.markConsumeStarted"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcOutboxEventMapper.selectMqMetrics"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcOutboxEventMapper.selectRecentDeadEvents"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcDocumentTaskMapper.resetFailedTaskManually"));
    }

    @Test
    void mqMetricsShouldExcludeDeletedParseAndIndexTasks() throws IOException
    {
        String resource = "mapper/officecare/OcOutboxEventMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            String xml = new String(inputStream.readAllBytes(),
                StandardCharsets.UTF_8);
            assertTrue(xml.contains("t.task_type = 'delete_index'"));
            assertTrue(xml.contains("d.del_flag = '0'"));
            assertTrue(xml.contains("v.del_flag = '0'"));
            assertTrue(xml.contains("activeMqDocumentTaskCondition"));
        }
    }

    private void parse(Configuration configuration, String resource) throws IOException
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                inputStream, configuration, resource, configuration.getSqlFragments());
            mapperBuilder.parse();
        }
    }
}
