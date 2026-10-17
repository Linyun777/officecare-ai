package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 在已有会话中提问。
 *
 * @author officecare
 */
public class OcAiConversationQuestionRequest
{
    /** 客户端本轮幂等ID；旧客户端不传时由Java生成。 */
    @Size(min = 36, max = 36, message = "本轮请求ID必须是标准UUID")
    private String turnId;

    @NotBlank(message = "问题不能为空")
    @Size(min = 2, max = 500, message = "问题长度必须在2到500个字符之间")
    private String question;

    public String getTurnId()
    {
        return turnId;
    }

    public void setTurnId(String turnId)
    {
        this.turnId = turnId;
    }

    public String getQuestion()
    {
        return question;
    }

    public void setQuestion(String question)
    {
        this.question = question;
    }
}
