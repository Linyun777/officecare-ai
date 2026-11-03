package com.ruoyi.officecare.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.domain.OcDocument;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentPreviewRequest;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.storage.DocumentStorageService;
import com.ruoyi.officecare.storage.DocumentArtifactKeys;

/** Reads immutable parse artifacts; never submits parse or index tasks. */
@Service
public class OcDocumentPreviewService
{
    private static final int MAX_PARSE_BYTES = 64 * 1024 * 1024;

    private final OcDocumentVersionMapper versions;
    private final OcDocumentMapper documents;
    private final OcKnowledgeBaseMapper knowledgeBases;
    private final DocumentStorageService storage;
    private final OfficeCareAiDocumentClient ai;
    private final ObjectMapper json;

    public OcDocumentPreviewService(OcDocumentVersionMapper versions,
                                   OcDocumentMapper documents,
                                   OcKnowledgeBaseMapper knowledgeBases,
                                   DocumentStorageService storage,
                                   OfficeCareAiDocumentClient ai,
                                   ObjectMapper json)
    {
        this.versions = versions;
        this.documents = documents;
        this.knowledgeBases = knowledgeBases;
        this.storage = storage;
        this.ai = ai;
        this.json = json;
    }

    public JsonNode preview(Long versionId, String view, int pageNum, int pageSize)
    {
        if (versionId == null || versionId <= 0
                || view == null || !Set.of("blocks", "legacy", "structured").contains(view)
                || pageNum < 1 || pageNum > 1000000 || pageSize < 1 || pageSize > 100)
        {
            throw new ServiceException("预览参数无效");
        }
        Source source = requireSource(versionId);
        OcDocumentVersion version = source.version();
        try (InputStream input = storage.open(version.getParsedObjectKey()))
        {
            byte[] content = input.readNBytes(MAX_PARSE_BYTES + 1);
            if (content.length > MAX_PARSE_BYTES)
            {
                throw new ServiceException("解析结果过大，暂不支持在线预览");
            }
            AiDocumentParseResponse parsed = json.readValue(content, AiDocumentParseResponse.class);
            validateArtifact(version, parsed);
            String parseId = DocumentArtifactKeys.digest(content);
            if (version.getParsedObjectKey().matches(".*/parsed/parse-[a-f0-9]{64}\\.json")
                    && !version.getParsedObjectKey().endsWith("parse-" + parseId + ".json"))
            {
                throw new ServiceException("解析结果内容与存储摘要不一致");
            }
            AiDocumentPreviewRequest request = new AiDocumentPreviewRequest(
                versionId, version.getFileHash(), parseId, parsed,
                view, pageNum, pageSize, source.knowledgeBase().getModelName());
            JsonNode response = ai.preview(request, "document-preview-" + UUID.randomUUID());
            if (!response.path("documentVersionId").canConvertToLong()
                    || response.path("documentVersionId").asLong() != versionId
                    || !parseId.equals(response.path("parseId").asText())
                    || !view.equals(response.path("view").asText())
                    || response.path("pageNum").asInt(-1) != pageNum
                    || response.path("pageSize").asInt(-1) != pageSize
                    || !response.path("total").canConvertToInt()
                    || response.path("total").asInt() < 0
                    || !response.path("items").isArray()
                    || response.path("items").size() > pageSize)
            {
                throw new ServiceException("预览响应与请求的版本或解析结果不一致");
            }
            Source current = requireSource(versionId);
            if (!Objects.equals(current.version().getParsedObjectKey(), version.getParsedObjectKey())
                    || !Objects.equals(current.version().getFileHash(), version.getFileHash())
                    || !Objects.equals(current.knowledgeBase().getKnowledgeBaseId(),
                                       source.knowledgeBase().getKnowledgeBaseId())
                    || !Objects.equals(current.knowledgeBase().getModelId(),
                                       source.knowledgeBase().getModelId()))
            {
                throw new ServiceException("解析结果或文档范围已变化，请重新打开预览");
            }
            return response;
        }
        catch (IOException exception)
        {
            throw new ServiceException("解析结果无法读取或格式无效");
        }
    }

    private Source requireSource(Long versionId)
    {
        OcDocumentVersion version = versions.selectVersionById(versionId);
        if (version == null)
        {
            throw new ServiceException("文档版本不存在或已被删除");
        }
        OcDocument document = documents.selectDocumentById(version.getDocumentId());
        if (document == null || !"0".equals(document.getStatus()))
        {
            throw new ServiceException("文档不存在、已停用或已被删除");
        }
        OcKnowledgeBase knowledgeBase = knowledgeBases.selectKnowledgeBaseById(
            document.getKnowledgeBaseId());
        if (knowledgeBase == null || !"0".equals(knowledgeBase.getStatus()))
        {
            throw new ServiceException("知识库不存在、已停用或已被删除");
        }
        if (!"2".equals(version.getProcessStatus())
                || StringUtils.isEmpty(version.getParsedObjectKey()))
        {
            throw new ServiceException("文档尚未解析成功，不能预览");
        }
        return new Source(version, knowledgeBase);
    }

    private void validateArtifact(OcDocumentVersion version, AiDocumentParseResponse parsed)
    {
        if (parsed.getSchemaVersion() == null || !Set.of(1, 2).contains(parsed.getSchemaVersion())
                || parsed.getFileHash() == null
                || !parsed.getFileHash().equalsIgnoreCase(version.getFileHash())
                || !Objects.equals(parsed.getFileSize(), version.getFileSize())
                || parsed.getBlocks() == null || parsed.getBlockCount() == null
                || parsed.getBlockCount() != parsed.getBlocks().size())
        {
            throw new ServiceException("解析结果的版本、文件身份或块统计不一致");
        }
        long characters = 0;
        int previous = 0;
        for (AiDocumentParseResponse.Block block : parsed.getBlocks())
        {
            if (block.getSequence() == null || block.getSequence() <= previous
                    || block.getText() == null)
            {
                throw new ServiceException("解析块序号或文字无效");
            }
            previous = block.getSequence();
            characters += block.getText().codePointCount(0, block.getText().length());
        }
        if (parsed.getCharacterCount() == null || characters != parsed.getCharacterCount())
        {
            throw new ServiceException("解析结果的字符统计不一致");
        }
    }

    private record Source(OcDocumentVersion version, OcKnowledgeBase knowledgeBase)
    {
    }
}
