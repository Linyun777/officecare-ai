package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** RAG缓存主动失效请求。 */
public class AiRagCacheInvalidateRequest
{
    @NotNull(message = "知识库ID不能为空")
    @Positive(message = "知识库ID必须为正数")
    private Long knowledgeBaseId;

    @NotNull(message = "缓存失效原因不能为空")
    @Size(min = 1, max = 100, message = "缓存失效原因长度必须在1到100之间")
    @Pattern(regexp = "^[A-Za-z0-9_:-]+$",
        message = "缓存失效原因只能包含字母、数字、冒号、下划线或连字符")
    private String reason;

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
