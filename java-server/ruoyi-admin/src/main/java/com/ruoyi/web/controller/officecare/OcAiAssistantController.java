package com.ruoyi.web.controller.officecare;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.officecare.domain.dto.OcAiConversationCreateRequest;
import com.ruoyi.officecare.domain.dto.OcAiConversationQuestionRequest;
import com.ruoyi.officecare.domain.dto.OcAiConversationTitleRequest;
import com.ruoyi.officecare.domain.dto.OcAiFeedbackRequest;
import com.ruoyi.officecare.domain.dto.OcAiQuestionRequest;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateRequest;
import com.ruoyi.officecare.service.IOcAiAssistantService;

/**
 * OfficeCare AI知识助手接口。
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/ai")
public class OcAiAssistantController extends BaseController
{
    private final IOcAiAssistantService aiAssistantService;

    public OcAiAssistantController(IOcAiAssistantService aiAssistantService)
    {
        this.aiAssistantService = aiAssistantService;
    }

    /**
     * 查询问答页面可选择的有效知识库。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @GetMapping("/knowledge-bases")
    public AjaxResult knowledgeBases()
    {
        return success(aiAssistantService.selectAvailableKnowledgeBases());
    }

    /**
     * 在指定知识库范围内完成一次非流式RAG问答。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @Log(title = "OfficeCare AI知识问答", businessType = BusinessType.OTHER)
    @PostMapping("/answer")
    public AjaxResult answer(@Validated @RequestBody OcAiQuestionRequest request)
    {
        return success(aiAssistantService.answer(request));
    }

    /** 查询FastAPI进程内的RAG缓存指标与指定知识库修订号。 */
    @PreAuthorize("@ss.hasPermi('officecare:document:index')")
    @GetMapping("/cache/stats")
    public AjaxResult cacheStats(
        @RequestParam(required = false) Long knowledgeBaseId)
    {
        return success(aiAssistantService.selectRagCacheStats(knowledgeBaseId));
    }

    /** 主动提升知识库缓存修订号，不扫描或物理删除旧缓存键。 */
    @PreAuthorize("@ss.hasPermi('officecare:document:index')")
    @Log(title = "OfficeCare RAG缓存失效", businessType = BusinessType.CLEAN)
    @PostMapping("/cache/invalidate")
    public AjaxResult invalidateCache(
        @Validated @RequestBody AiRagCacheInvalidateRequest request)
    {
        return success(aiAssistantService.invalidateRagCache(request));
    }

    /**
     * 查询当前登录用户最近的会话。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @GetMapping("/conversations")
    public AjaxResult conversations()
    {
        return success(aiAssistantService.selectConversationList(getUserId()));
    }

    /**
     * 创建一个绑定知识库的会话。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @Log(title = "OfficeCare AI会话", businessType = BusinessType.INSERT)
    @PostMapping("/conversations")
    public AjaxResult createConversation(
        @Validated @RequestBody OcAiConversationCreateRequest request)
    {
        return success(aiAssistantService.createConversation(
            request, getUserId(), getUsername()));
    }

    /**
     * 查询本人指定会话的消息和引用。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @GetMapping("/conversations/{conversationId}/messages")
    public AjaxResult conversationMessages(@PathVariable Long conversationId)
    {
        return success(aiAssistantService.selectConversationMessages(
            conversationId, getUserId()));
    }

    /**
     * 在指定会话的固定知识库范围内完成一次问答并保存消息。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @Log(title = "OfficeCare AI会话问答", businessType = BusinessType.OTHER)
    @PostMapping("/conversations/{conversationId}/answer")
    public AjaxResult answerInConversation(
        @PathVariable Long conversationId,
        @Validated @RequestBody OcAiConversationQuestionRequest request)
    {
        return success(aiAssistantService.answerInConversation(
            conversationId, request, getUserId(), getUsername()));
    }

    /**
     * 修改本人会话标题。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @Log(title = "OfficeCare AI会话", businessType = BusinessType.UPDATE)
    @PutMapping("/conversations/{conversationId}")
    public AjaxResult updateConversation(
        @PathVariable Long conversationId,
        @Validated @RequestBody OcAiConversationTitleRequest request)
    {
        return toAjax(aiAssistantService.updateConversationTitle(
            conversationId, request.getTitle(), getUserId(), getUsername()));
    }

    /**
     * 软删除本人会话，不删除知识库文档和向量。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @Log(title = "OfficeCare AI会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/conversations/{conversationId}")
    public AjaxResult deleteConversation(@PathVariable Long conversationId)
    {
        return toAjax(aiAssistantService.deleteConversation(
            conversationId, getUserId(), getUsername()));
    }

    /**
     * 对本人会话中的AI回答提交或清除反馈。
     */
    @PreAuthorize("@ss.hasPermi('officecare:ai:chat')")
    @Log(title = "OfficeCare AI回答反馈", businessType = BusinessType.UPDATE)
    @PutMapping("/messages/{messageId}/feedback")
    public AjaxResult feedback(
        @PathVariable Long messageId,
        @Validated @RequestBody OcAiFeedbackRequest request)
    {
        return toAjax(aiAssistantService.updateMessageFeedback(
            messageId, request, getUserId(), getUsername()));
    }
}
