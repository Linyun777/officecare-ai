package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcAgentDraft;

/** 智能报修草稿数据访问层。 */
public interface OcAgentDraftMapper
{
    public List<OcAgentDraft> selectDraftList(OcAgentDraft draft);

    public OcAgentDraft selectDraftByIdAndUserId(
        @Param("draftId") Long draftId, @Param("userId") Long userId);

    public OcAgentDraft selectDraftForUpdate(
        @Param("draftId") Long draftId, @Param("userId") Long userId);

    public int insertDraft(OcAgentDraft draft);

    public int updateEditableDraft(OcAgentDraft draft);

    public int markDraftConfirmed(@Param("draftId") Long draftId,
        @Param("userId") Long userId,
        @Param("workOrderId") Long workOrderId,
        @Param("updateBy") String updateBy);

    public int cancelDrafts(@Param("draftIds") Long[] draftIds,
        @Param("userId") Long userId,
        @Param("updateBy") String updateBy);
}
