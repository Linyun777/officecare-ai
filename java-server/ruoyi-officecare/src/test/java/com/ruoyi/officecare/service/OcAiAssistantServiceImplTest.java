package com.ruoyi.officecare.service;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.domain.OcAiConversation;
import com.ruoyi.officecare.domain.OcAiMessage;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnRequest;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnResponse;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;
import com.ruoyi.officecare.domain.dto.OcAiConversationAnswerResponse;
import com.ruoyi.officecare.domain.dto.OcAiConversationQuestionRequest;
import com.ruoyi.officecare.mapper.OcAiConversationMapper;
import com.ruoyi.officecare.mapper.OcAiMessageCitationMapper;
import com.ruoyi.officecare.mapper.OcAiMessageMapper;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.service.impl.OcAiAssistantServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多轮AI助手的Java业务边界测试。
 *
 * @author officecare
 */
class OcAiAssistantServiceImplTest
{
    @Test
    void shouldSendOwnedScopeBootstrapMessagesAndDeleteCheckpoint()
    {
        OcKnowledgeBase knowledgeBase = knowledgeBase();
        OcAiConversation conversation = conversation();
        List<OcAiMessage> savedMessages = new ArrayList<>();
        AtomicLong messageId = new AtomicLong(100L);

        OcKnowledgeBaseMapper knowledgeBaseMapper = proxy(
            OcKnowledgeBaseMapper.class,
            (methodName, arguments) -> "selectKnowledgeBaseById".equals(methodName)
                ? knowledgeBase : null);
        OcDocumentMapper documentMapper = proxy(
            OcDocumentMapper.class,
            (methodName, arguments) ->
                "selectCurrentIndexedVersionIds".equals(methodName)
                    ? List.of(40L, 41L) : null);
        OcAiConversationMapper conversationMapper = proxy(
            OcAiConversationMapper.class,
            (methodName, arguments) ->
            {
                if ("selectConversationByIdAndUserId".equals(methodName))
                {
                    return conversation;
                }
                if ("touchConversation".equals(methodName)
                    || "deleteConversation".equals(methodName))
                {
                    return 1;
                }
                return null;
            });
        OcAiMessageMapper messageMapper = proxy(
            OcAiMessageMapper.class,
            (methodName, arguments) ->
            {
                if ("selectRecentMessageListByConversationId".equals(methodName))
                {
                    return bootstrapMessages();
                }
                if ("insertMessage".equals(methodName))
                {
                    OcAiMessage message = (OcAiMessage) arguments[0];
                    message.setMessageId(messageId.incrementAndGet());
                    savedMessages.add(message);
                    return 1;
                }
                return null;
            });
        OcAiMessageCitationMapper citationMapper = proxy(
            OcAiMessageCitationMapper.class,
            (methodName, arguments) -> "insertCitation".equals(methodName)
                ? 1 : null);

        StubAiClient aiClient = new StubAiClient(agentResponse());
        OfficeCareAiProperties properties = new OfficeCareAiProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        OcAiAssistantServiceImpl service = new OcAiAssistantServiceImpl(
            knowledgeBaseMapper,
            documentMapper,
            conversationMapper,
            messageMapper,
            citationMapper,
            aiClient,
            properties,
            objectMapper);

        OcAiConversationQuestionRequest question =
            new OcAiConversationQuestionRequest();
        question.setTurnId("e1feae17-65b9-46ad-b3dd-356676686a41");
        question.setQuestion("大约94毫米");
        OcAiConversationAnswerResponse response = service.answerInConversation(
            15L, question, 7L, "admin");

        AiAssistantTurnRequest sent = aiClient.request;
        assertEquals(15L, sent.getConversationId());
        assertEquals(7L, sent.getUserId());
        assertEquals(1L, sent.getKnowledgeBaseId());
        assertEquals(2L, sent.getBrandId());
        assertEquals(3L, sent.getModelId());
        assertEquals(List.of(40L, 41L), sent.getDocumentVersionIds());
        assertEquals(2, sent.getBootstrapMessages().size());
        assertEquals("user", sent.getBootstrapMessages().get(0).getRole());
        assertEquals("assistant", sent.getBootstrapMessages().get(1).getRole());
        assertEquals("DIAGNOSTIC_STEP", response.getResult().getResponseType());
        assertEquals(question.getTurnId(), response.getTurnId());
        assertEquals(2, savedMessages.size());
        assertEquals("大约94毫米", savedMessages.get(0).getContent());
        assertEquals(question.getTurnId(), savedMessages.get(0).getTurnId());
        assertEquals(question.getTurnId(), savedMessages.get(1).getTurnId());
        assertNotNull(savedMessages.get(1).getAgentPayload());
        assertTrue(savedMessages.get(1).getTraceId().startsWith("assistant-1-"));

        assertEquals(1, service.deleteConversation(15L, 7L, "admin"));
        assertEquals(7L, aiClient.deletedUserId);
        assertEquals(15L, aiClient.deletedConversationId);
    }

    @Test
    void shouldReplayPersistedTurnAndRestoreAgentResult() throws Exception
    {
        OcAiConversation conversation = conversation();
        String turnId = "e1feae17-65b9-46ad-b3dd-356676686a41";
        ObjectMapper objectMapper = new ObjectMapper();
        List<OcAiMessage> persistedMessages = persistedMessages(
            turnId, objectMapper.writeValueAsString(agentResponse()));

        OcKnowledgeBaseMapper knowledgeBaseMapper = proxy(
            OcKnowledgeBaseMapper.class, (methodName, arguments) -> null);
        OcDocumentMapper documentMapper = proxy(
            OcDocumentMapper.class, (methodName, arguments) -> null);
        OcAiConversationMapper conversationMapper = proxy(
            OcAiConversationMapper.class,
            (methodName, arguments) ->
                "selectConversationByIdAndUserId".equals(methodName)
                    ? conversation : null);
        OcAiMessageMapper messageMapper = proxy(
            OcAiMessageMapper.class,
            (methodName, arguments) ->
            {
                if ("selectMessageListByConversationIdAndTurnId".equals(
                    methodName)
                    || "selectMessageListByConversationId".equals(methodName))
                {
                    return persistedMessages;
                }
                if ("insertMessage".equals(methodName))
                {
                    throw new AssertionError(
                        "A persisted turn must not be inserted again");
                }
                return null;
            });
        OcAiMessageCitationMapper citationMapper = proxy(
            OcAiMessageCitationMapper.class,
            (methodName, arguments) ->
                "selectCitationListByConversation".equals(methodName)
                    ? List.of() : null);
        StubAiClient aiClient = new StubAiClient(agentResponse());
        OcAiAssistantServiceImpl service = new OcAiAssistantServiceImpl(
            knowledgeBaseMapper,
            documentMapper,
            conversationMapper,
            messageMapper,
            citationMapper,
            aiClient,
            new OfficeCareAiProperties(),
            objectMapper);

        OcAiConversationQuestionRequest repeated =
            new OcAiConversationQuestionRequest();
        repeated.setTurnId(turnId);
        repeated.setQuestion("大约94毫米");
        OcAiConversationAnswerResponse replayed = service.answerInConversation(
            15L, repeated, 7L, "admin");

        assertEquals(turnId, replayed.getTurnId());
        assertEquals(101L, replayed.getUserMessageId());
        assertEquals(102L, replayed.getAssistantMessageId());
        assertEquals("assistant-existing-trace", replayed.getTraceId());
        assertEquals("DIAGNOSTIC_STEP",
            replayed.getResult().getResponseType());
        assertNull(aiClient.request);

        List<OcAiMessage> history = service.selectConversationMessages(15L, 7L);
        assertNotNull(history.get(1).getAgentResult());
        assertEquals("AWAITING_STEP_RESULT",
            history.get(1).getAgentResult().getDiagnosticStatus());
        assertEquals("清洁后斑点是否消失？",
            history.get(1).getAgentResult().getNextQuestion());
        String historyJson = objectMapper.writeValueAsString(history.get(1));
        assertTrue(historyJson.contains("\"agentResult\""));
        assertFalse(historyJson.contains("agentPayload"));

        OcAiConversationQuestionRequest conflicting =
            new OcAiConversationQuestionRequest();
        conflicting.setTurnId(turnId);
        conflicting.setQuestion("设备发生卡纸");
        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> service.answerInConversation(
                15L, conflicting, 7L, "admin"));
        assertEquals("本轮请求ID已用于其他问题", exception.getMessage());
    }

    private static OcKnowledgeBase knowledgeBase()
    {
        OcKnowledgeBase knowledgeBase = new OcKnowledgeBase();
        knowledgeBase.setKnowledgeBaseId(1L);
        knowledgeBase.setBrandId(2L);
        knowledgeBase.setBrandName("Brother");
        knowledgeBase.setModelId(3L);
        knowledgeBase.setModelName("DCP-L2550DW");
        knowledgeBase.setStatus("0");
        return knowledgeBase;
    }

    private static OcAiConversation conversation()
    {
        OcAiConversation conversation = new OcAiConversation();
        conversation.setConversationId(15L);
        conversation.setKnowledgeBaseId(1L);
        conversation.setUserId(7L);
        conversation.setTitle("新会话");
        conversation.setStatus("0");
        return conversation;
    }

    private static List<OcAiMessage> bootstrapMessages()
    {
        OcAiMessage user = new OcAiMessage();
        user.setRole("user");
        user.setContent("打印页面出现重复斑点");
        OcAiMessage assistant = new OcAiMessage();
        assistant.setRole("assistant");
        assistant.setContent("请说明斑点之间的距离。");
        return List.of(user, assistant);
    }

    private static List<OcAiMessage> persistedMessages(
        String turnId,
        String agentPayload)
    {
        OcAiMessage user = new OcAiMessage();
        user.setMessageId(101L);
        user.setConversationId(15L);
        user.setTurnId(turnId);
        user.setRole("user");
        user.setContent("大约94毫米");

        OcAiMessage assistant = new OcAiMessage();
        assistant.setMessageId(102L);
        assistant.setConversationId(15L);
        assistant.setTurnId(turnId);
        assistant.setRole("assistant");
        assistant.setContent("请先检查并清洁硒鼓单元。[资料1]");
        assistant.setTraceId("assistant-existing-trace");
        assistant.setAgentPayload(agentPayload);
        return List.of(user, assistant);
    }

    private static AiAssistantTurnResponse agentResponse()
    {
        AiRagAnswerResponse.Citation citation =
            new AiRagAnswerResponse.Citation();
        citation.setCitationIndex(1);
        citation.setKnowledgeBaseId(1L);
        citation.setBrandId(2L);
        citation.setModelId(3L);
        citation.setDocumentId(30L);
        citation.setDocumentVersionId(40L);
        citation.setDocumentTitle("高级说明书");
        citation.setOriginalFileName("高级说明书.pdf");
        citation.setPageNumber(402);
        citation.setChunkIndex(91);
        citation.setHeading("改善打印质量");
        citation.setQuote("以94毫米间隔出现斑点时清洁硒鼓单元。");
        citation.setScore(0.82D);

        AiAssistantTurnResponse response = new AiAssistantTurnResponse();
        response.setResponseType("DIAGNOSTIC_STEP");
        response.setAnswer("请先检查并清洁硒鼓单元。[资料1]");
        response.setDiagnosticStatus("AWAITING_STEP_RESULT");
        response.setIssueSummary("打印页面出现重复斑点");
        response.setAttemptedSteps(List.of("准备检查硒鼓单元"));
        response.setObservations(List.of("斑点间隔约94毫米"));
        response.setNextQuestion("清洁后斑点是否消失？");
        response.setRequiresUserReply(true);
        response.setRequiresOnSiteService(false);
        response.setAnswered(true);
        response.setCitations(List.of(citation));
        response.setRetrievedCount(1);
        response.setScoreThreshold(0.45D);
        response.setModelName("deepseek-v4-pro");
        return response;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, StubInvocation invocation)
    {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (proxy, method, arguments) ->
            {
                Object result = invocation.invoke(
                    method.getName(), arguments == null ? new Object[0] : arguments);
                if (result != null || !method.getReturnType().isPrimitive())
                {
                    return result;
                }
                if (method.getReturnType() == boolean.class)
                {
                    return false;
                }
                if (method.getReturnType() == long.class)
                {
                    return 0L;
                }
                return 0;
            });
    }

    @FunctionalInterface
    private interface StubInvocation
    {
        Object invoke(String methodName, Object[] arguments);
    }

    private static class StubAiClient extends OfficeCareAiDocumentClient
    {
        private final AiAssistantTurnResponse response;

        private AiAssistantTurnRequest request;

        private Long deletedUserId;

        private Long deletedConversationId;

        StubAiClient(AiAssistantTurnResponse response)
        {
            super(properties(), new ObjectMapper());
            this.response = response;
        }

        @Override
        public AiAssistantTurnResponse assistantTurn(
            AiAssistantTurnRequest request, String traceId)
        {
            this.request = request;
            return response;
        }

        @Override
        public void deleteAssistantThread(
            Long userId, Long conversationId, String traceId)
        {
            this.deletedUserId = userId;
            this.deletedConversationId = conversationId;
        }

        private static OfficeCareAiProperties properties()
        {
            OfficeCareAiProperties properties = new OfficeCareAiProperties();
            properties.setServiceToken("test-token");
            return properties;
        }
    }
}
