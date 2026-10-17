package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcKnowledgeBase;

/**
 * OfficeCare知识库数据访问层。
 *
 * @author officecare
 */
public interface OcKnowledgeBaseMapper
{
    public List<OcKnowledgeBase> selectKnowledgeBaseList(OcKnowledgeBase knowledgeBase);

    public List<OcKnowledgeBase> selectKnowledgeBaseOptions();

    public OcKnowledgeBase selectKnowledgeBaseById(Long knowledgeBaseId);

    public OcKnowledgeBase selectKnowledgeBaseByCode(String knowledgeBaseCode);

    public OcKnowledgeBase selectActiveKnowledgeBaseByModelId(Long modelId);

    public long selectKnowledgeBaseCount();

    public int countKnowledgeBaseByModelId(Long modelId);

    public int insertKnowledgeBase(OcKnowledgeBase knowledgeBase);

    public int updateKnowledgeBase(OcKnowledgeBase knowledgeBase);

    public int deleteKnowledgeBaseByIds(@Param("knowledgeBaseIds") Long[] knowledgeBaseIds,
        @Param("updateBy") String updateBy);
}
