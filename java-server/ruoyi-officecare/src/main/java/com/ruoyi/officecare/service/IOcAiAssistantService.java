package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcAiConversation;
import com.ruoyi.officecare.domain.OcAiMessage;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateRequest;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheStatsResponse;
import com.ruoyi.officecare.domain.dto.OcAiConversationAnswerResponse;
import com.ruoyi.officecare.domain.dto.OcAiConversationCreateRequest;
import com.ruoyi.officecare.domain.dto.OcAiConversationQuestionRequest;
import com.ruoyi.officecare.domain.dto.OcAiFeedbackRequest;
import com.ruoyi.officecare.domain.dto.OcAiQuestionRequest;

/**
 * OfficeCare AI知识助手服务。
 *
 * @author officecare
 */
public interface IOcAiAssistantService
{
    public List<OcKnowledgeBase> selectAvailableKnowledgeBases();

    public AiRagAnswerResponse answer(OcAiQuestionRequest request);

    public AiRagCacheStatsResponse selectRagCacheStats(Long knowledgeBaseId);

    public AiRagCacheInvalidateResponse invalidateRagCache(
        AiRagCacheInvalidateRequest request);

    public List<OcAiConversation> selectConversationList(Long userId);

    public OcAiConversation createConversation(OcAiConversationCreateRequest request,
        Long userId, String username);

    public List<OcAiMessage> selectConversationMessages(Long conversationId,
        Long userId);

    public OcAiConversationAnswerResponse answerInConversation(Long conversationId,
        OcAiConversationQuestionRequest request, Long userId, String username);

    public int updateConversationTitle(Long conversationId, String title,
        Long userId, String username);

    public int deleteConversation(Long conversationId, Long userId,
        String username);

    public int updateMessageFeedback(Long messageId, OcAiFeedbackRequest request,
        Long userId, String username);
}
