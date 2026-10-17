package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcAiConversation;

/**
 * AI助手会话数据访问层。
 *
 * @author officecare
 */
public interface OcAiConversationMapper
{
    public List<OcAiConversation> selectConversationListByUserId(Long userId);

    public OcAiConversation selectConversationByIdAndUserId(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId);

    public int insertConversation(OcAiConversation conversation);

    public int updateConversationTitle(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId,
        @Param("title") String title,
        @Param("updateBy") String updateBy);

    public int touchConversation(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId,
        @Param("generatedTitle") String generatedTitle,
        @Param("updateBy") String updateBy);

    public int deleteConversation(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId,
        @Param("updateBy") String updateBy);
}
