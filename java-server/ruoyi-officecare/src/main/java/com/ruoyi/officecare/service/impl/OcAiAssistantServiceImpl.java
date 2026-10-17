package com.ruoyi.officecare.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.domain.OcAiConversation;
import com.ruoyi.officecare.domain.OcAiMessage;
import com.ruoyi.officecare.domain.OcAiMessageCitation;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnRequest;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnResponse;
import com.ruoyi.officecare.domain.dto.AiRagAnswerRequest;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateRequest;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheStatsResponse;
import com.ruoyi.officecare.domain.dto.OcAiConversationAnswerResponse;
import com.ruoyi.officecare.domain.dto.OcAiConversationCreateRequest;
import com.ruoyi.officecare.domain.dto.OcAiConversationQuestionRequest;
import com.ruoyi.officecare.domain.dto.OcAiFeedbackRequest;
import com.ruoyi.officecare.domain.dto.OcAiQuestionRequest;
import com.ruoyi.officecare.mapper.OcAiConversationMapper;
import com.ruoyi.officecare.mapper.OcAiMessageCitationMapper;
import com.ruoyi.officecare.mapper.OcAiMessageMapper;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.service.IOcAiAssistantService;

/**
 * OfficeCare AI知识助手服务实现。
 *
 * Java负责用户归属、会话持久化和业务范围，FastAPI只负责检索与生成。
 *
 * @author officecare
 */
@Service
public class OcAiAssistantServiceImpl implements IOcAiAssistantService
{
    private static final Logger log = LoggerFactory.getLogger(
        OcAiAssistantServiceImpl.class);

    private static final String NORMAL_STATUS = "0";

    private static final String DEFAULT_CONVERSATION_TITLE = "新会话";

    private static final int AUTO_TITLE_MAX_LENGTH = 30;

    private static final int BOOTSTRAP_MESSAGE_LIMIT = 24;

    private static final String ROLE_USER = "user";

    private static final String ROLE_ASSISTANT = "assistant";

    private static final String FEEDBACK_NONE = "0";

    private static final String REFUSAL_REASON = "INSUFFICIENT_EVIDENCE";

    private static final String REFUSAL_TEXT =
        "根据当前知识库中的资料，暂时无法找到足够证据回答这个问题。";

    private static final Set<String> ASSISTANT_RESPONSE_TYPES = Set.of(
        "ANSWER", "CLARIFICATION", "DIAGNOSTIC_STEP", "RESOLUTION",
        "ESCALATION", "REFUSAL");

    private static final Set<String> ASSISTANT_DIAGNOSTIC_STATUSES = Set.of(
        "IDLE", "COLLECTING_INFORMATION", "AWAITING_STEP_RESULT",
        "RESOLVED", "ESCALATE", "INSUFFICIENT_EVIDENCE");

    private final OcKnowledgeBaseMapper knowledgeBaseMapper;

    private final OcDocumentMapper documentMapper;

    private final OcAiConversationMapper conversationMapper;

    private final OcAiMessageMapper messageMapper;

    private final OcAiMessageCitationMapper citationMapper;

    private final OfficeCareAiDocumentClient aiClient;

    private final OfficeCareAiProperties aiProperties;

    private final ObjectMapper objectMapper;

    public OcAiAssistantServiceImpl(
        OcKnowledgeBaseMapper knowledgeBaseMapper,
        OcDocumentMapper documentMapper,
        OcAiConversationMapper conversationMapper,
        OcAiMessageMapper messageMapper,
        OcAiMessageCitationMapper citationMapper,
        OfficeCareAiDocumentClient aiClient,
        OfficeCareAiProperties aiProperties,
        ObjectMapper objectMapper)
    {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.citationMapper = citationMapper;
        this.aiClient = aiClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<OcKnowledgeBase> selectAvailableKnowledgeBases()
    {
        return knowledgeBaseMapper.selectKnowledgeBaseOptions();
    }

    /**
     * 保留原无状态接口，兼容现有调用；新管理页面使用会话接口。
     */
    @Override
    public AiRagAnswerResponse answer(OcAiQuestionRequest request)
    {
        String normalizedQuestion = normalizeQuestion(request.getQuestion());
        OcKnowledgeBase knowledgeBase = requireAvailableKnowledgeBase(
            request.getKnowledgeBaseId());
        String traceId = createTraceId(knowledgeBase.getKnowledgeBaseId());
        return executeAnswer(knowledgeBase, normalizedQuestion, traceId);
    }

    @Override
    public AiRagCacheStatsResponse selectRagCacheStats(Long knowledgeBaseId)
    {
        if (knowledgeBaseId != null && knowledgeBaseId <= 0)
        {
            throw new ServiceException("知识库ID必须为正数");
        }
        return aiClient.ragCacheStats(
            knowledgeBaseId, "rag-cache-stats-" + UUID.randomUUID());
    }

    @Override
    public AiRagCacheInvalidateResponse invalidateRagCache(
        AiRagCacheInvalidateRequest request)
    {
        if (knowledgeBaseMapper.selectKnowledgeBaseById(
            request.getKnowledgeBaseId()) == null)
        {
            throw new ServiceException("知识库不存在或已删除");
        }
        return aiClient.invalidateRagCache(
            request, "rag-cache-invalidate-" + UUID.randomUUID());
    }

    @Override
    public List<OcAiConversation> selectConversationList(Long userId)
    {
        return conversationMapper.selectConversationListByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OcAiConversation createConversation(
        OcAiConversationCreateRequest request,
        Long userId,
        String username)
    {
        requireAvailableKnowledgeBase(request.getKnowledgeBaseId());
        String title = StringUtils.trim(request.getTitle());
        if (StringUtils.isEmpty(title))
        {
            title = DEFAULT_CONVERSATION_TITLE;
        }

        OcAiConversation conversation = new OcAiConversation();
        conversation.setKnowledgeBaseId(request.getKnowledgeBaseId());
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setCreateBy(username);
        if (conversationMapper.insertConversation(conversation) != 1)
        {
            throw new ServiceException("创建AI会话失败");
        }
        return requireOwnedConversation(conversation.getConversationId(), userId);
    }

    @Override
    public List<OcAiMessage> selectConversationMessages(
        Long conversationId,
        Long userId)
    {
        requireOwnedConversation(conversationId, userId);
        List<OcAiMessage> messages =
            messageMapper.selectMessageListByConversationId(conversationId);
        if (messages == null || messages.isEmpty())
        {
            return Collections.emptyList();
        }

        Map<Long, List<OcAiMessageCitation>> citationsByMessage = new HashMap<>();
        List<OcAiMessageCitation> citations =
            citationMapper.selectCitationListByConversation(conversationId, userId);
        if (citations != null)
        {
            for (OcAiMessageCitation citation : citations)
            {
                citationsByMessage.computeIfAbsent(
                    citation.getMessageId(), key -> new ArrayList<>()).add(citation);
            }
        }
        for (OcAiMessage message : messages)
        {
            message.setCitations(citationsByMessage.get(message.getMessageId()));
            hydrateAgentResult(message);
        }
        return messages;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OcAiConversationAnswerResponse answerInConversation(
        Long conversationId,
        OcAiConversationQuestionRequest request,
        Long userId,
        String username)
    {
        OcAiConversation conversation = requireOwnedConversation(
            conversationId, userId);
        String normalizedQuestion = normalizeQuestion(request.getQuestion());
        String turnId = normalizeTurnId(request.getTurnId());

        OcAiConversationAnswerResponse persisted = selectPersistedTurn(
            conversation, turnId, normalizedQuestion);
        if (persisted != null)
        {
            return persisted;
        }

        OcKnowledgeBase knowledgeBase = requireAvailableKnowledgeBase(
            conversation.getKnowledgeBaseId());
        String traceId = createAssistantTraceId(
            knowledgeBase.getKnowledgeBaseId());
        AiAssistantTurnResponse result = executeAssistantTurn(
            conversation, knowledgeBase, normalizedQuestion, turnId,
            userId, traceId);

        OcAiMessage userMessage = new OcAiMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setTurnId(turnId);
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(normalizedQuestion);
        userMessage.setFeedbackType(FEEDBACK_NONE);
        userMessage.setCreateBy(username);
        insertMessage(userMessage);

        OcAiMessage assistantMessage = new OcAiMessage();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setTurnId(turnId);
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent(result.getAnswer());
        assistantMessage.setAnswered(result.getAnswered());
        assistantMessage.setRefusalReason(result.getRefusalReason());
        assistantMessage.setModelName(result.getModelName());
        assistantMessage.setTraceId(traceId);
        assistantMessage.setAgentPayload(serializeAgentResult(result));
        assistantMessage.setFeedbackType(FEEDBACK_NONE);
        assistantMessage.setCreateBy(username);
        insertMessage(assistantMessage);

        saveCitations(assistantMessage.getMessageId(), result.getCitations());

        String generatedTitle = buildConversationTitle(normalizedQuestion);
        if (conversationMapper.touchConversation(
            conversationId, userId, generatedTitle, username) != 1)
        {
            throw new ServiceException("更新AI会话状态失败");
        }
        String finalTitle = DEFAULT_CONVERSATION_TITLE.equals(conversation.getTitle())
            ? generatedTitle : conversation.getTitle();

        OcAiConversationAnswerResponse response =
            new OcAiConversationAnswerResponse();
        response.setConversationId(conversationId);
        response.setConversationTitle(finalTitle);
        response.setTurnId(turnId);
        response.setUserMessageId(userMessage.getMessageId());
        response.setAssistantMessageId(assistantMessage.getMessageId());
        response.setTraceId(traceId);
        response.setResult(result);
        return response;
    }

    @Override
    public int updateConversationTitle(
        Long conversationId,
        String title,
        Long userId,
        String username)
    {
        requireOwnedConversation(conversationId, userId);
        String normalizedTitle = StringUtils.trim(title);
        if (StringUtils.isEmpty(normalizedTitle))
        {
            throw new ServiceException("会话标题不能为空");
        }
        return conversationMapper.updateConversationTitle(
            conversationId, userId, normalizedTitle, username);
    }

    @Override
    public int deleteConversation(
        Long conversationId,
        Long userId,
        String username)
    {
        requireOwnedConversation(conversationId, userId);
        aiClient.deleteAssistantThread(
            userId, conversationId,
            "assistant-delete-" + conversationId + "-" + UUID.randomUUID());
        int deleted = conversationMapper.deleteConversation(
            conversationId, userId, username);
        if (deleted != 1)
        {
            throw new ServiceException("删除AI会话失败");
        }
        return deleted;
    }

    @Override
    public int updateMessageFeedback(
        Long messageId,
        OcAiFeedbackRequest request,
        Long userId,
        String username)
    {
        String feedbackComment = StringUtils.trim(request.getFeedbackComment());
        if (FEEDBACK_NONE.equals(request.getFeedbackType()))
        {
            feedbackComment = null;
        }
        int updated = messageMapper.updateMessageFeedback(
            messageId, userId, request.getFeedbackType(),
            StringUtils.isEmpty(feedbackComment) ? null : feedbackComment,
            username);
        if (updated != 1)
        {
            throw new ServiceException("AI回答不存在或不属于当前用户");
        }
        return updated;
    }

    private AiRagAnswerResponse executeAnswer(
        OcKnowledgeBase knowledgeBase,
        String normalizedQuestion,
        String traceId)
    {
        List<Long> versionIds = documentMapper.selectCurrentIndexedVersionIds(
            knowledgeBase.getKnowledgeBaseId());
        if (versionIds == null || versionIds.isEmpty())
        {
            return refusalResponse();
        }

        AiRagAnswerRequest aiRequest = new AiRagAnswerRequest();
        aiRequest.setQuestion(normalizedQuestion);
        aiRequest.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        aiRequest.setBrandId(knowledgeBase.getBrandId());
        aiRequest.setModelId(knowledgeBase.getModelId());
        aiRequest.setBrandName(knowledgeBase.getBrandName());
        aiRequest.setModelName(knowledgeBase.getModelName());
        aiRequest.setDocumentVersionIds(versionIds);
        aiRequest.setTopK(aiProperties.getRagTopK());
        aiRequest.setScoreThreshold(aiProperties.getRagScoreThreshold());

        AiRagAnswerResponse response = aiClient.answer(aiRequest, traceId);
        validateResponse(response, knowledgeBase, versionIds);
        return response;
    }

    private AiAssistantTurnResponse executeAssistantTurn(
        OcAiConversation conversation,
        OcKnowledgeBase knowledgeBase,
        String normalizedQuestion,
        String turnId,
        Long userId,
        String traceId)
    {
        List<Long> versionIds = documentMapper.selectCurrentIndexedVersionIds(
            knowledgeBase.getKnowledgeBaseId());
        if (versionIds == null || versionIds.isEmpty())
        {
            return assistantRefusalResponse();
        }

        AiAssistantTurnRequest aiRequest = new AiAssistantTurnRequest();
        aiRequest.setConversationId(conversation.getConversationId());
        aiRequest.setUserId(userId);
        aiRequest.setTurnId(turnId);
        aiRequest.setQuestion(normalizedQuestion);
        aiRequest.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        aiRequest.setBrandId(knowledgeBase.getBrandId());
        aiRequest.setModelId(knowledgeBase.getModelId());
        aiRequest.setBrandName(knowledgeBase.getBrandName());
        aiRequest.setModelName(knowledgeBase.getModelName());
        aiRequest.setDocumentVersionIds(versionIds);
        aiRequest.setTopK(aiProperties.getRagTopK());
        aiRequest.setScoreThreshold(aiProperties.getRagScoreThreshold());
        aiRequest.setBootstrapMessages(loadBootstrapMessages(
            conversation.getConversationId()));

        AiAssistantTurnResponse response = aiClient.assistantTurn(
            aiRequest, traceId);
        validateAssistantResponse(response, knowledgeBase, versionIds);
        return response;
    }

    private List<AiAssistantTurnRequest.BootstrapMessage> loadBootstrapMessages(
        Long conversationId)
    {
        List<OcAiMessage> recentMessages =
            messageMapper.selectRecentMessageListByConversationId(
                conversationId, BOOTSTRAP_MESSAGE_LIMIT);
        if (recentMessages == null || recentMessages.isEmpty())
        {
            return Collections.emptyList();
        }

        List<AiAssistantTurnRequest.BootstrapMessage> bootstrapMessages =
            new ArrayList<>();
        for (OcAiMessage message : recentMessages)
        {
            String role = StringUtils.trim(message.getRole());
            String content = StringUtils.trim(message.getContent());
            if ((!ROLE_USER.equals(role) && !ROLE_ASSISTANT.equals(role))
                || StringUtils.isEmpty(content))
            {
                continue;
            }
            AiAssistantTurnRequest.BootstrapMessage bootstrapMessage =
                new AiAssistantTurnRequest.BootstrapMessage();
            bootstrapMessage.setRole(role);
            bootstrapMessage.setContent(content);
            bootstrapMessages.add(bootstrapMessage);
        }
        return bootstrapMessages;
    }

    private OcKnowledgeBase requireAvailableKnowledgeBase(Long knowledgeBaseId)
    {
        OcKnowledgeBase knowledgeBase =
            knowledgeBaseMapper.selectKnowledgeBaseById(knowledgeBaseId);
        if (knowledgeBase == null
            || !NORMAL_STATUS.equals(knowledgeBase.getStatus()))
        {
            throw new ServiceException("知识库不存在、已删除或已停用");
        }
        if (knowledgeBase.getBrandId() == null || knowledgeBase.getModelId() == null)
        {
            throw new ServiceException("知识库尚未正确关联品牌和产品型号");
        }
        return knowledgeBase;
    }

    private OcAiConversation requireOwnedConversation(
        Long conversationId,
        Long userId)
    {
        OcAiConversation conversation =
            conversationMapper.selectConversationByIdAndUserId(
                conversationId, userId);
        if (conversation == null)
        {
            throw new ServiceException("AI会话不存在或不属于当前用户");
        }
        return conversation;
    }

    /**
     * 已完成的Turn直接从MySQL重放，避免再次调用FastAPI和重复写消息。
     */
    private OcAiConversationAnswerResponse selectPersistedTurn(
        OcAiConversation conversation,
        String turnId,
        String normalizedQuestion)
    {
        List<OcAiMessage> persistedMessages =
            messageMapper.selectMessageListByConversationIdAndTurnId(
                conversation.getConversationId(), turnId);
        if (persistedMessages == null || persistedMessages.isEmpty())
        {
            return null;
        }

        OcAiMessage userMessage = null;
        OcAiMessage assistantMessage = null;
        for (OcAiMessage message : persistedMessages)
        {
            if (ROLE_USER.equals(message.getRole()))
            {
                userMessage = message;
            }
            else if (ROLE_ASSISTANT.equals(message.getRole()))
            {
                assistantMessage = message;
            }
        }
        if (userMessage == null || assistantMessage == null)
        {
            throw new ServiceException(
                "本轮请求已有未完成记录，请使用新的请求ID后重试");
        }
        if (!normalizedQuestion.equals(userMessage.getContent()))
        {
            throw new ServiceException("本轮请求ID已用于其他问题");
        }

        AiAssistantTurnResponse result = requireAgentResult(assistantMessage);
        OcAiConversationAnswerResponse response =
            new OcAiConversationAnswerResponse();
        response.setConversationId(conversation.getConversationId());
        response.setConversationTitle(conversation.getTitle());
        response.setTurnId(turnId);
        response.setUserMessageId(userMessage.getMessageId());
        response.setAssistantMessageId(assistantMessage.getMessageId());
        response.setTraceId(assistantMessage.getTraceId());
        response.setResult(result);
        return response;
    }

    private String serializeAgentResult(AiAssistantTurnResponse result)
    {
        try
        {
            return objectMapper.writeValueAsString(result);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("序列化AI助手结构化结果失败");
        }
    }

    private AiAssistantTurnResponse requireAgentResult(OcAiMessage message)
    {
        String payload = StringUtils.trim(message.getAgentPayload());
        if (StringUtils.isEmpty(payload))
        {
            throw new ServiceException("本轮AI助手响应快照不存在");
        }
        try
        {
            return objectMapper.readValue(payload, AiAssistantTurnResponse.class);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("本轮AI助手响应快照无法读取");
        }
    }

    /** 历史数据没有快照时保持兼容；损坏快照不影响正文和引用显示。 */
    private void hydrateAgentResult(OcAiMessage message)
    {
        if (!ROLE_ASSISTANT.equals(message.getRole())
            || StringUtils.isEmpty(StringUtils.trim(message.getAgentPayload())))
        {
            return;
        }
        try
        {
            message.setAgentResult(objectMapper.readValue(
                message.getAgentPayload(), AiAssistantTurnResponse.class));
        }
        catch (JsonProcessingException exception)
        {
            log.warn(
                "Ignoring an invalid AI assistant response snapshot. messageId={}",
                message.getMessageId());
        }
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

    private void validateResponse(
        AiRagAnswerResponse response,
        OcKnowledgeBase knowledgeBase,
        List<Long> versionIds)
    {
        if (response.getAnswered() == null
            || StringUtils.isEmpty(StringUtils.trim(response.getAnswer())))
        {
            throw new ServiceException("AI服务返回的问答结果不完整");
        }

        List<AiRagAnswerResponse.Citation> citations = response.getCitations();
        if (!Boolean.TRUE.equals(response.getAnswered()))
        {
            response.setCitations(Collections.emptyList());
            return;
        }
        if (citations == null || citations.isEmpty())
        {
            throw new ServiceException("AI服务未返回可核验的资料引用");
        }

        validateCitationScope(citations, knowledgeBase, versionIds);
    }

    private void validateAssistantResponse(
        AiAssistantTurnResponse response,
        OcKnowledgeBase knowledgeBase,
        List<Long> versionIds)
    {
        if (response == null
            || !ASSISTANT_RESPONSE_TYPES.contains(response.getResponseType())
            || !ASSISTANT_DIAGNOSTIC_STATUSES.contains(
                response.getDiagnosticStatus())
            || StringUtils.isEmpty(StringUtils.trim(response.getAnswer()))
            || response.getAnswered() == null
            || response.getRequiresUserReply() == null
            || response.getRequiresOnSiteService() == null)
        {
            throw new ServiceException("AI助手返回的结构化结果不完整");
        }

        if (response.getAttemptedSteps() == null)
        {
            response.setAttemptedSteps(Collections.emptyList());
        }
        if (response.getObservations() == null)
        {
            response.setObservations(Collections.emptyList());
        }

        List<AiRagAnswerResponse.Citation> citations = response.getCitations();
        if (!Boolean.TRUE.equals(response.getAnswered()))
        {
            response.setCitations(Collections.emptyList());
            return;
        }
        if (citations == null || citations.isEmpty())
        {
            throw new ServiceException("AI助手未返回可核验的资料引用");
        }
        validateCitationScope(citations, knowledgeBase, versionIds);
    }

    private void validateCitationScope(
        List<AiRagAnswerResponse.Citation> citations,
        OcKnowledgeBase knowledgeBase,
        List<Long> versionIds)
    {
        Set<Long> allowedVersionIds = new HashSet<>(versionIds);
        for (AiRagAnswerResponse.Citation citation : citations)
        {
            boolean scopeMatches = citation != null
                && knowledgeBase.getKnowledgeBaseId().equals(
                    citation.getKnowledgeBaseId())
                && knowledgeBase.getBrandId().equals(citation.getBrandId())
                && knowledgeBase.getModelId().equals(citation.getModelId())
                && allowedVersionIds.contains(citation.getDocumentVersionId());
            if (!scopeMatches)
            {
                throw new ServiceException(
                    "AI服务返回了超出当前知识库范围的资料引用");
            }
        }
    }

    private AiAssistantTurnResponse assistantRefusalResponse()
    {
        AiAssistantTurnResponse response = new AiAssistantTurnResponse();
        response.setResponseType("REFUSAL");
        response.setAnswer(REFUSAL_TEXT);
        response.setDiagnosticStatus("INSUFFICIENT_EVIDENCE");
        response.setIssueSummary("");
        response.setAttemptedSteps(Collections.emptyList());
        response.setObservations(Collections.emptyList());
        response.setRequiresUserReply(false);
        response.setRequiresOnSiteService(true);
        response.setAnswered(false);
        response.setRefusalReason(REFUSAL_REASON);
        response.setCitations(Collections.emptyList());
        response.setRetrievedCount(0);
        response.setScoreThreshold(aiProperties.getRagScoreThreshold());
        return response;
    }

    private AiRagAnswerResponse refusalResponse()
    {
        AiRagAnswerResponse response = new AiRagAnswerResponse();
        response.setAnswered(false);
        response.setAnswer(REFUSAL_TEXT);
        response.setRefusalReason(REFUSAL_REASON);
        response.setCitations(Collections.emptyList());
        response.setRetrievedCount(0);
        response.setScoreThreshold(aiProperties.getRagScoreThreshold());
        return response;
    }

    private String normalizeQuestion(String question)
    {
        String normalized = StringUtils.trim(question);
        if (StringUtils.isEmpty(normalized) || normalized.length() < 2)
        {
            throw new ServiceException("问题去除首尾空格后不能少于2个字符");
        }
        return normalized;
    }

    private String normalizeTurnId(String turnId)
    {
        String normalized = StringUtils.trim(turnId);
        if (StringUtils.isEmpty(normalized))
        {
            return UUID.randomUUID().toString();
        }
        try
        {
            return UUID.fromString(normalized).toString();
        }
        catch (IllegalArgumentException exception)
        {
            throw new ServiceException("本轮请求ID必须是标准UUID");
        }
    }

    private String buildConversationTitle(String question)
    {
        String singleLine = question.replaceAll("\\s+", " ").trim();
        if (singleLine.length() <= AUTO_TITLE_MAX_LENGTH)
        {
            return singleLine;
        }
        return singleLine.substring(0, AUTO_TITLE_MAX_LENGTH) + "…";
    }

    private String createTraceId(Long knowledgeBaseId)
    {
        return "rag-" + knowledgeBaseId + "-" + UUID.randomUUID();
    }

    private String createAssistantTraceId(Long knowledgeBaseId)
    {
        return "assistant-" + knowledgeBaseId + "-" + UUID.randomUUID();
    }
}
