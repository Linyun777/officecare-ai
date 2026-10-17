package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建AI会话请求。
 *
 * @author officecare
 */
public class OcAiConversationCreateRequest
{
    @NotNull(message = "知识库不能为空")
    @Positive(message = "知识库ID必须大于0")
    private Long knowledgeBaseId;

    @Size(max = 120, message = "会话标题长度不能超过120个字符")
    private String title;

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }
}
