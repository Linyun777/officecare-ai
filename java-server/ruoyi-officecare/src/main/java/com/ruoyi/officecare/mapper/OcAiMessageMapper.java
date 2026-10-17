package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcAiMessage;

/**
 * AI助手消息数据访问层。
 *
 * @author officecare
 */
public interface OcAiMessageMapper
{
    public List<OcAiMessage> selectMessageListByConversationId(
        Long conversationId);

    /** 按时间正序返回指定会话最近的消息，用于初始化Agent Checkpoint。 */
    public List<OcAiMessage> selectRecentMessageListByConversationId(
        @Param("conversationId") Long conversationId,
        @Param("limit") int limit);

    /** 查询指定会话中已经持久化的同一轮用户与助手消息。 */
    public List<OcAiMessage> selectMessageListByConversationIdAndTurnId(
        @Param("conversationId") Long conversationId,
        @Param("turnId") String turnId);

    public int insertMessage(OcAiMessage message);

    public int updateMessageFeedback(
        @Param("messageId") Long messageId,
        @Param("userId") Long userId,
        @Param("feedbackType") String feedbackType,
        @Param("feedbackComment") String feedbackComment,
        @Param("updateBy") String updateBy);
}
