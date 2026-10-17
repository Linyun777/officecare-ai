package com.ruoyi.officecare.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * AI回答反馈请求。
 *
 * @author officecare
 */
public class OcAiFeedbackRequest
{
    /** 0清除反馈，1有帮助，2无帮助。 */
    @NotBlank(message = "反馈类型不能为空")
    @Pattern(regexp = "^[012]$", message = "反馈类型值不正确")
    private String feedbackType;

    @Size(max = 500, message = "反馈说明长度不能超过500个字符")
    private String feedbackComment;

    public String getFeedbackType()
    {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType)
    {
        this.feedbackType = feedbackType;
    }

    public String getFeedbackComment()
    {
        return feedbackComment;
    }

    public void setFeedbackComment(String feedbackComment)
    {
        this.feedbackComment = feedbackComment;
    }
}
