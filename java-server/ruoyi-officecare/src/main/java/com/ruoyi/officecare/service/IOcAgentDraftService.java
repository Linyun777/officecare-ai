package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcAgentDraft;
import com.ruoyi.officecare.domain.OcWorkOrder;
import com.ruoyi.officecare.domain.dto.OcAgentAnalyzeRequest;
import com.ruoyi.officecare.domain.dto.OcAgentDraftEditRequest;

/** 智能报修分析、草稿和人工确认服务。 */
public interface IOcAgentDraftService
{
    public List<OcAgentDraft> selectDraftList(OcAgentDraft filter, Long userId);

    public OcAgentDraft selectDraftById(Long draftId, Long userId);

    public OcAgentDraft analyze(OcAgentAnalyzeRequest request,
        Long userId, String username);

    public OcAgentDraft updateDraft(Long draftId,
        OcAgentDraftEditRequest request, Long userId, String username);

    public OcWorkOrder confirmDraft(Long draftId,
        OcAgentDraftEditRequest request, Long userId, String username);

    public int cancelDrafts(Long[] draftIds, Long userId, String username);
}
