package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcAiMessageCitation;

/**
 * AI消息引用数据访问层。
 *
 * @author officecare
 */
public interface OcAiMessageCitationMapper
{
    public List<OcAiMessageCitation> selectCitationListByConversation(
        @Param("conversationId") Long conversationId,
        @Param("userId") Long userId);

    public int insertCitation(OcAiMessageCitation citation);
}
