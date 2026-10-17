package com.ruoyi.officecare.mapper;

import java.io.IOException;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI会话Mapper XML解析测试，提前发现命名空间、类型或SQL节点配置错误。
 *
 * @author officecare
 */
class OcAiMapperXmlTest
{
    private static final String[] MAPPER_RESOURCES = {
        "mapper/officecare/OcAiConversationMapper.xml",
        "mapper/officecare/OcAiMessageMapper.xml",
        "mapper/officecare/OcAiMessageCitationMapper.xml"
    };

    @Test
    void aiConversationMapperXmlShouldBeParsed() throws IOException
    {
        Configuration configuration = new Configuration();
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
            "com.ruoyi.officecare.mapper.OcAiConversationMapper"
                + ".selectConversationListByUserId"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcAiMessageMapper"
                + ".updateMessageFeedback"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcAiMessageMapper"
                + ".selectRecentMessageListByConversationId"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcAiMessageMapper"
                + ".selectMessageListByConversationIdAndTurnId"));
        assertTrue(configuration.hasStatement(
            "com.ruoyi.officecare.mapper.OcAiMessageCitationMapper"
                + ".selectCitationListByConversation"));
    }
}
