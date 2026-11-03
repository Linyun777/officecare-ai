package com.ruoyi.officecare.service.impl;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.domain.OcDocumentIndexGeneration;
import com.ruoyi.officecare.domain.dto.AiGenerationIndexRequest;
import com.ruoyi.officecare.mapper.OcDocumentIndexGenerationMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;

@Service
public class OcDocumentGenerationService
{
    private static final Logger log = LoggerFactory.getLogger(OcDocumentGenerationService.class);
    private final OcDocumentIndexGenerationMapper generations;
    private final OcDocumentVersionMapper versions;
    private final OcDocumentPreviewService artifacts;
    private final OfficeCareAiDocumentClient ai;

    public OcDocumentGenerationService(OcDocumentIndexGenerationMapper generations,
                                       OcDocumentVersionMapper versions,
                                       OcDocumentPreviewService artifacts,
                                       OfficeCareAiDocumentClient ai)
    {
        this.generations = generations;
        this.versions = versions;
        this.artifacts = artifacts;
        this.ai = ai;
    }

    public List<OcDocumentIndexGeneration> list(Long versionId)
    {
        if (versions.selectVersionById(versionId) == null)
        {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        return generations.selectByVersionId(versionId);
    }

    // Each mapper write is short. Never hold a database transaction across AI HTTP.
    public OcDocumentIndexGeneration build(Long versionId, String profile, String username)
    {
        if (profile == null || !Set.of("legacy", "structured").contains(profile))
        {
            throw new ServiceException("未知分块规则");
        }
        OcDocumentPreviewService.ParsedDocument source = artifacts.prepare(versionId);
        String generationId = UUID.randomUUID().toString().replace("-", "");
        OcDocumentIndexGeneration generation = new OcDocumentIndexGeneration();
        generation.setGenerationId(generationId);
        generation.setDocumentId(source.version().getDocumentId());
        generation.setDocumentVersionId(versionId);
        generation.setKnowledgeBaseId(source.knowledgeBase().getKnowledgeBaseId());
        generation.setBrandId(source.knowledgeBase().getBrandId());
        generation.setModelId(source.knowledgeBase().getModelId());
        generation.setParseId(source.parseId());
        generation.setParsedObjectKey(source.version().getParsedObjectKey());
        generation.setProfile(profile);
        generation.setCreateBy(username);
        generations.insertGeneration(generation);
        JsonNode result = null;
        try
        {
            AiGenerationIndexRequest request = new AiGenerationIndexRequest(
                generationId, source.parseId(), profile,
                source.knowledgeBase().getKnowledgeBaseId(), source.knowledgeBase().getBrandId(),
                source.knowledgeBase().getModelId(), source.version().getDocumentId(), versionId,
                source.version().getVersionNo(), source.document().getDocumentTitle(), source.parsed());
            result = ai.buildGeneration(request, "generation-" + generationId);
            validateManifest(result, request);
            artifacts.assertCurrent(source);
            if (generations.completeGeneration(generationId, result.toString()) != 1)
            {
                throw new ServiceException("版本、解析结果或构建状态已变化，本次代次不能发布");
            }
            return generations.selectById(generationId);
        }
        catch (RuntimeException exception)
        {
            generations.failGeneration(generationId, StringUtils.substring(exception.getMessage(), 0, 500));
            if (result != null)
            {
                try
                {
                    ai.deleteGeneration(generationId, "generation-discard-" + generationId);
                }
                catch (RuntimeException cleanup)
                {
                    log.warn("Index generation cleanup deferred. generationId={}, error={}",
                             generationId, cleanup.getClass().getSimpleName());
                }
            }
            throw exception;
        }
    }

    public void cleanupVersion(Long versionId, String traceId)
    {
        ai.deleteVersionGenerations(versionId, traceId);
        generations.deleteByVersionId(versionId);
    }

    private void validateManifest(JsonNode result, AiGenerationIndexRequest request)
    {
        String expectedProfile = "structured".equals(request.profile()) ? "structure-preview-v1" : "legacy-v1";
        if (!request.generationId().equals(result.path("generationId").asText())
                || !request.parseId().equals(result.path("parseId").asText())
                || !expectedProfile.equals(result.path("profile").asText())
                || request.documentId() != result.path("documentId").asLong(-1)
                || request.documentVersionId() != result.path("documentVersionId").asLong(-1)
                || result.path("chunkCount").asInt() <= 0
                || result.path("embeddingDimension").asInt() <= 0
                || result.path("embeddingModel").asText().isBlank()
                || !result.path("collectionName").asText().endsWith("_generations")
                || !result.path("indexContentHash").asText().matches("[a-f0-9]{64}"))
        {
            throw new ServiceException("索引代次清单与版本、解析结果或构建规则不一致");
        }
    }
}
