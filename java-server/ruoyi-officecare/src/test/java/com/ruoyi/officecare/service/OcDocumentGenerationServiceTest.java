package com.ruoyi.officecare.service;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.domain.OcDocument;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.OcDocumentIndexGeneration;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.domain.dto.AiGenerationIndexRequest;
import com.ruoyi.officecare.mapper.OcDocumentIndexGenerationMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;
import com.ruoyi.officecare.service.impl.OcDocumentGenerationService;
import com.ruoyi.officecare.service.impl.OcDocumentPreviewService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OcDocumentGenerationServiceTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, OcDocumentIndexGeneration> rows = new HashMap<>();
    private OcDocumentIndexGenerationMapper mapper;
    private OcDocumentVersionMapper versions;
    private OcDocumentPreviewService artifacts;
    private OfficeCareAiDocumentClient ai;
    private OcDocumentGenerationService service;
    private OcDocumentPreviewService.ParsedDocument source;

    @BeforeEach
    void setup()
    {
        mapper = mock(OcDocumentIndexGenerationMapper.class);
        versions = mock(OcDocumentVersionMapper.class);
        artifacts = mock(OcDocumentPreviewService.class);
        ai = mock(OfficeCareAiDocumentClient.class);
        service = new OcDocumentGenerationService(mapper, versions, artifacts, ai);
        OcDocumentVersion version = new OcDocumentVersion();
        version.setDocumentVersionId(3L);
        version.setDocumentId(2L);
        version.setVersionNo(1);
        version.setParsedObjectKey("documents/2/versions/1/parsed/parse-result.json");
        OcDocument document = new OcDocument();
        document.setDocumentId(2L);
        document.setDocumentTitle("合成手册");
        OcKnowledgeBase kb = new OcKnowledgeBase();
        kb.setKnowledgeBaseId(1L);
        kb.setBrandId(4L);
        kb.setModelId(5L);
        source = new OcDocumentPreviewService.ParsedDocument(version, kb, new AiDocumentParseResponse(), "b".repeat(64), document);
        when(artifacts.prepare(3L)).thenReturn(source);
        when(mapper.insertGeneration(any())).thenAnswer(call -> {
            OcDocumentIndexGeneration generation = call.getArgument(0);
            generation.setStatus("BUILDING");
            rows.put(generation.getGenerationId(), generation);
            return 1;
        });
        when(mapper.selectById(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.completeGeneration(anyString(), anyString())).thenAnswer(call -> {
            OcDocumentIndexGeneration generation = rows.get(call.getArgument(0));
            generation.setStatus("READY");
            generation.setManifestJson(call.getArgument(1));
            return 1;
        });
        when(ai.buildGeneration(any(), anyString())).thenAnswer(call -> {
            AiGenerationIndexRequest request = call.getArgument(0);
            return json.createObjectNode().put("generationId", request.generationId())
                .put("parseId", request.parseId()).put("documentId", 2L).put("documentVersionId", 3L)
                .put("profile", "structured".equals(request.profile()) ? "structure-preview-v1" : "legacy-v1")
                .put("chunkCount", 2).put("embeddingDimension", 3).put("embeddingModel", "deterministic-test")
                .put("collectionName", "m2_test_generations").put("indexContentHash", "c".repeat(64));
        });
    }

    @Test
    void separateBuildsKeepTheirParseIdentityAndHideStoragePaths() throws Exception
    {
        OcDocumentIndexGeneration first = service.build(3L, "structured", "tester");
        OcDocumentIndexGeneration second = service.build(3L, "legacy", "tester");
        assertNotEquals(first.getGenerationId(), second.getGenerationId());
        assertEquals("READY", first.getStatus());
        assertEquals("b".repeat(64), first.getParseId());
        assertEquals(1L, first.getKnowledgeBaseId());
        assertEquals(4L, first.getBrandId());
        assertEquals(5L, first.getModelId());
        assertEquals(2, rows.size());
        String output = json.writeValueAsString(first);
        assertFalse(output.contains("parsedObjectKey"));
        assertEquals(2, json.readTree(output).path("manifest").path("chunkCount").asInt());
        verify(artifacts, times(2)).assertCurrent(source);
        verifyNoInteractions(versions);
    }

    @Test
    void changedSourceDiscardsLateVectorsAndCannotCompleteTheCandidate()
    {
        doThrow(new ServiceException("解析结果已变化")).when(artifacts).assertCurrent(source);
        assertThrows(ServiceException.class, () -> service.build(3L, "structured", "tester"));
        verify(mapper, never()).completeGeneration(anyString(), anyString());
        verify(mapper).failGeneration(anyString(), contains("变化"));
        verify(ai).deleteGeneration(anyString(), anyString());
    }

    @Test
    void failedProviderAndDeletedVersionsStayUnavailable()
    {
        doThrow(new ServiceException("模拟超时")).when(ai).buildGeneration(any(), anyString());
        assertThrows(ServiceException.class, () -> service.build(3L, "structured", "tester"));
        verify(mapper).failGeneration(anyString(), contains("超时"));
        verify(mapper, never()).completeGeneration(anyString(), anyString());
        assertThrows(ServiceException.class, () -> service.list(3L));
        service.cleanupVersion(3L, "delete-test");
        verify(ai).deleteVersionGenerations(3L, "delete-test");
        verify(mapper).deleteByVersionId(3L);
    }
}
