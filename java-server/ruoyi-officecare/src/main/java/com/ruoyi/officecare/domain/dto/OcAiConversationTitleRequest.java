package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改AI会话标题请求。
 *
 * @author officecare
 */
public class OcAiConversationTitleRequest
{
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 120, message = "会话标题长度不能超过120个字符")
    private String title;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }
}
