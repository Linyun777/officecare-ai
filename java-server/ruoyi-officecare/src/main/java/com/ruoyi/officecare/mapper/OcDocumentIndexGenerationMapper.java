package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcDocumentIndexGeneration;

public interface OcDocumentIndexGenerationMapper
{
    OcDocumentIndexGeneration selectById(String generationId);

    List<OcDocumentIndexGeneration> selectByVersionId(Long versionId);

    int insertGeneration(OcDocumentIndexGeneration generation);

    int completeGeneration(@Param("generationId") String generationId,
                           @Param("manifestJson") String manifestJson);

    int failGeneration(@Param("generationId") String generationId,
                       @Param("errorMessage") String errorMessage);

    int deleteByVersionId(Long versionId);
}
