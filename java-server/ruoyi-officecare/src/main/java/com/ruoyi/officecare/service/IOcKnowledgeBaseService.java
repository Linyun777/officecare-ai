package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcKnowledgeBase;

/**
 * OfficeCare知识库服务。
 *
 * @author officecare
 */
public interface IOcKnowledgeBaseService
{
    public List<OcKnowledgeBase> selectKnowledgeBaseList(OcKnowledgeBase knowledgeBase);

    public List<OcKnowledgeBase> selectKnowledgeBaseOptions();

    public OcKnowledgeBase selectKnowledgeBaseById(Long knowledgeBaseId);

    public int insertKnowledgeBase(OcKnowledgeBase knowledgeBase);

    public int updateKnowledgeBase(OcKnowledgeBase knowledgeBase);

    public int deleteKnowledgeBaseByIds(Long[] knowledgeBaseIds, String updateBy);
}
