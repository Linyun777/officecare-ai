package com.ruoyi.officecare.domain.dto;

/**
 * 会话问答持久化结果。
 *
 * @author officecare
 */
public class OcAiConversationAnswerResponse
{
    private Long conversationId;

    private String conversationTitle;

    private String turnId;

    private Long userMessageId;

    private Long assistantMessageId;

    private String traceId;

    private AiAssistantTurnResponse result;

    public Long getConversationId()
    {
        return conversationId;
    }

    public void setConversationId(Long conversationId)
    {
        this.conversationId = conversationId;
    }

    public String getConversationTitle()
    {
        return conversationTitle;
    }

    public void setConversationTitle(String conversationTitle)
    {
        this.conversationTitle = conversationTitle;
    }

    public String getTurnId()
    {
        return turnId;
    }

    public void setTurnId(String turnId)
    {
        this.turnId = turnId;
    }

    public Long getUserMessageId()
    {
        return userMessageId;
    }

    public void setUserMessageId(Long userMessageId)
    {
        this.userMessageId = userMessageId;
    }

    public Long getAssistantMessageId()
    {
        return assistantMessageId;
    }

    public void setAssistantMessageId(Long assistantMessageId)
    {
        this.assistantMessageId = assistantMessageId;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public AiAssistantTurnResponse getResult()
    {
        return result;
    }

    public void setResult(AiAssistantTurnResponse result)
    {
        this.result = result;
    }
}
