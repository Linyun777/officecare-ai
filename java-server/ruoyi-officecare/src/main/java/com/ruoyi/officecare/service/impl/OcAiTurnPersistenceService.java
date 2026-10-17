package com.ruoyi.officecare.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.domain.OcAiConversation;
import com.ruoyi.officecare.domain.OcAiMessage;
import com.ruoyi.officecare.domain.OcAiMessageCitation;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnResponse;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;
import com.ruoyi.officecare.mapper.OcAiConversationMapper;
import com.ruoyi.officecare.mapper.OcAiMessageCitationMapper;
import com.ruoyi.officecare.mapper.OcAiMessageMapper;
import com.ruoyi.officecare.mapper.OcAiTurnExecutionMapper;

/**
 * AI助手单轮结果落库服务。
 *
 * 只有消息、引用、会话状态和执行状态在同一个短事务内全部成功才算完成；
 * 该事务不包含任何FastAPI调用。
 *
 * @author officecare
 */
@Service
public class OcAiTurnPersistenceService
{
    private static final String ROLE_USER = "user";

    private static final String ROLE_ASSISTANT = "assistant";

    private static final String FEEDBACK_NONE = "0";

    private final OcAiConversationMapper conversationMapper;

    private final OcAiMessageMapper messageMapper;

    private final OcAiMessageCitationMapper citationMapper;

    private final OcAiTurnExecutionMapper executionMapper;

    public OcAiTurnPersistenceService(
        OcAiConversationMapper conversationMapper,
        OcAiMessageMapper messageMapper,
        OcAiMessageCitationMapper citationMapper,
        OcAiTurnExecutionMapper executionMapper)
    {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.citationMapper = citationMapper;
        this.executionMapper = executionMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersistResult persistTurn(
        OcAiConversation conversation,
        String question,
        String generatedTitle,
        String turnId,
        Long userId,
        String username,
        String traceId,
        String resultPayload,
        AiAssistantTurnResponse result,
        Long executionId,
        String ownerToken)
    {
        List<OcAiMessage> existing =
            messageMapper.selectMessageListByConversationIdAndTurnId(
                conversation.getConversationId(), turnId);
        OcAiMessage userMessage = findMessage(existing, ROLE_USER);
        OcAiMessage assistantMessage = findMessage(existing, ROLE_ASSISTANT);

        if (userMessage == null)
        {
            userMessage = insertUserMessage(
                conversation.getConversationId(), turnId, question, username);
        }
        else if (!question.equals(userMessage.getContent()))
        {
            throw new ServiceException("本轮请求ID已用于其他问题");
        }

        if (assistantMessage == null)
        {
            assistantMessage = insertAssistantMessage(
                conversation.getConversationId(), turnId, traceId,
                resultPayload, result, username);
            saveCitations(assistantMessage.getMessageId(), result.getCitations());
        }

        if (conversationMapper.touchConversation(
            conversation.getConversationId(), userId, generatedTitle,
            username) != 1)
        {
            throw new ServiceException("更新AI会话状态失败");
        }

        if (executionMapper.markSucceeded(
            executionId, ownerToken, userMessage.getMessageId(),
            assistantMessage.getMessageId()) != 1)
        {
            throw new ServiceException("更新单轮执行状态失败");
        }

        return new PersistResult(
            userMessage.getMessageId(), assistantMessage.getMessageId());
    }

    private OcAiMessage findMessage(List<OcAiMessage> messages, String role)
    {
        if (messages == null)
        {
            return null;
        }
        for (OcAiMessage message : messages)
        {
            if (role.equals(message.getRole()))
            {
                return message;
            }
        }
        return null;
    }

    private OcAiMessage insertUserMessage(
        Long conversationId,
        String turnId,
        String question,
        String username)
    {
        OcAiMessage message = new OcAiMessage();
        message.setConversationId(conversationId);
        message.setTurnId(turnId);
        message.setRole(ROLE_USER);
        message.setContent(question);
        message.setFeedbackType(FEEDBACK_NONE);
        message.setCreateBy(username);
        insertMessage(message);
        return message;
    }

    private OcAiMessage insertAssistantMessage(
        Long conversationId,
        String turnId,
        String traceId,
        String resultPayload,
        AiAssistantTurnResponse result,
        String username)
    {
        OcAiMessage message = new OcAiMessage();
        message.setConversationId(conversationId);
        message.setTurnId(turnId);
        message.setRole(ROLE_ASSISTANT);
        message.setContent(result.getAnswer());
        message.setAnswered(result.getAnswered());
        message.setRefusalReason(result.getRefusalReason());
        message.setModelName(result.getModelName());
        message.setTraceId(traceId);
        message.setAgentPayload(resultPayload);
        message.setFeedbackType(FEEDBACK_NONE);
        message.setCreateBy(username);
        insertMessage(message);
        return message;
    }

    private void insertMessage(OcAiMessage message)
    {
        if (messageMapper.insertMessage(message) != 1)
        {
            throw new ServiceException("保存AI会话消息失败");
        }
    }

    private void saveCitations(
        Long messageId,
        List<AiRagAnswerResponse.Citation> citations)
    {
        if (citations == null)
        {
            return;
        }
        for (AiRagAnswerResponse.Citation source : citations)
        {
            OcAiMessageCitation citation = new OcAiMessageCitation();
            citation.setMessageId(messageId);
            citation.setCitationIndex(source.getCitationIndex());
            citation.setKnowledgeBaseId(source.getKnowledgeBaseId());
            citation.setBrandId(source.getBrandId());
            citation.setModelId(source.getModelId());
            citation.setDocumentId(source.getDocumentId());
            citation.setDocumentVersionId(source.getDocumentVersionId());
            citation.setDocumentTitle(source.getDocumentTitle());
            citation.setOriginalFileName(source.getOriginalFileName());
            citation.setPageNumber(source.getPageNumber());
            citation.setChunkIndex(source.getChunkIndex());
            citation.setHeading(source.getHeading());
            citation.setQuote(source.getQuote());
            citation.setScore(source.getScore());
            if (citationMapper.insertCitation(citation) != 1)
            {
                throw new ServiceException("保存AI回答引用失败");
            }
        }
    }

    /** 单轮落库结果。 */
    public static final class PersistResult
    {
        private final Long userMessageId;

        private final Long assistantMessageId;

        public PersistResult(Long userMessageId, Long assistantMessageId)
        {
            this.userMessageId = userMessageId;
            this.assistantMessageId = assistantMessageId;
        }

        public Long getUserMessageId()
        {
            return userMessageId;
        }

        public Long getAssistantMessageId()
        {
            return assistantMessageId;
        }
    }
}