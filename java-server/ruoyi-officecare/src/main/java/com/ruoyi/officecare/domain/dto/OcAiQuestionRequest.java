package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 管理端提交的OfficeCare知识问答请求。
 *
 * @author officecare
 */
public class OcAiQuestionRequest
{
    /** 本次问答限定的知识库。 */
    @NotNull(message = "知识库不能为空")
    @Positive(message = "知识库ID必须大于0")
    private Long knowledgeBaseId;

    /** 用户问题。 */
    @NotBlank(message = "问题不能为空")
    @Size(min = 2, max = 500, message = "问题长度必须在2到500个字符之间")
    private String question;

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
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
