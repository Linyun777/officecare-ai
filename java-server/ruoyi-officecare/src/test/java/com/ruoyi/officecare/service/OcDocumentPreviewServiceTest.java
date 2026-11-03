package com.ruoyi.officecare.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.domain.OcDocument;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentPreviewRequest;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcDocumentVersionMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.service.impl.OcDocumentPreviewService;
import com.ruoyi.officecare.storage.DocumentArtifactKeys;
import com.ruoyi.officecare.storage.DocumentStorageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OcDocumentPreviewServiceTest
{
    private final ObjectMapper json = new ObjectMapper();
    private OcDocumentVersionMapper versions;
    private OcDocumentMapper documents;
    private OcKnowledgeBaseMapper knowledgeBases;
    private DocumentStorageService storage;
    private OfficeCareAiDocumentClient ai;
    private OcDocumentPreviewService service;
    private OcDocumentVersion version;
    private byte[] artifact;

    @BeforeEach
    void setUp() throws Exception
    {
        versions = mock(OcDocumentVersionMapper.class);
        documents = mock(OcDocumentMapper.class);
        knowledgeBases = mock(OcKnowledgeBaseMapper.class);
        storage = mock(DocumentStorageService.class);
        ai = mock(OfficeCareAiDocumentClient.class);
        service = new OcDocumentPreviewService(versions, documents, knowledgeBases, storage, ai, json);
        version = new OcDocumentVersion();
        version.setDocumentVersionId(3L);
        version.setDocumentId(2L);
        version.setVersionNo(1);
        version.setFileHash("a".repeat(64));
        version.setFileSize(10L);
        version.setProcessStatus("2");
        version.setObjectKey("documents/2/versions/1/original/manual.pdf");
        version.setParsedObjectKey("documents/2/versions/1/parsed/parse-result.json");
        OcDocument document = new OcDocument();
        document.setDocumentId(2L);
        document.setKnowledgeBaseId(1L);
        document.setStatus("0");
        OcKnowledgeBase knowledgeBase = new OcKnowledgeBase();
        knowledgeBase.setKnowledgeBaseId(1L);
        knowledgeBase.setModelId(8L);
        knowledgeBase.setModelName("DCP-L2550DW");
        knowledgeBase.setStatus("0");
        when(versions.selectVersionById(3L)).thenReturn(version);
        when(documents.selectDocumentById(2L)).thenReturn(document);
        when(knowledgeBases.selectKnowledgeBaseById(1L)).thenReturn(knowledgeBase);
        AiDocumentParseResponse parsed = new AiDocumentParseResponse();
        parsed.setFileHash(version.getFileHash());
        parsed.setFileSize(10L);
        AiDocumentParseResponse.Block block = new AiDocumentParseResponse.Block();
        block.setSequence(1);
        block.setBlockType("paragraph");
        block.setText("文字😀");
        parsed.setBlocks(List.of(block));
        parsed.setBlockCount(1);
        parsed.setCharacterCount(3);
        parsed.setWarnings(List.of());
        artifact = json.writeValueAsBytes(parsed);
        when(storage.open(anyString())).thenAnswer(call -> new ByteArrayInputStream(artifact));
        when(ai.preview(any(), anyString())).thenAnswer(call -> {
            AiDocumentPreviewRequest request = call.getArgument(0);
            return json.createObjectNode()
                .put("documentVersionId", request.documentVersionId())
                .put("parseId", request.parseId())
                .put("view", request.view())
                .put("pageNum", request.pageNum())
                .put("pageSize", request.pageSize())
                .put("total", 0)
                .set("items", json.createArrayNode());
        });
    }

    @Test
    void previewsPinnedBytesWithJavaScopeAndOnlyReadOperations() throws Exception
    {
        JsonNode result = service.preview(3L, "blocks", 1, 20);
        assertEquals(DocumentArtifactKeys.digest(artifact), result.path("parseId").asText());
        verify(ai).preview(argThat(request -> request.documentVersionId().equals(3L)
            && request.selectedModel().equals("DCP-L2550DW")
            && request.parsed().getCharacterCount() == 3), startsWith("document-preview-"));
        verify(storage).open(version.getParsedObjectKey());
        verifyNoMoreInteractions(storage, ai);
        verify(versions, times(2)).selectVersionById(3L);
        verify(documents, times(2)).selectDocumentById(2L);
        verify(knowledgeBases, times(2)).selectKnowledgeBaseById(1L);
        verifyNoMoreInteractions(versions, documents, knowledgeBases);
    }

    @Test
    void wrongFileHashAndArtifactDigestNeverReachTheAi() throws Exception
    {
        version.setFileHash("c".repeat(64));
        assertThrows(ServiceException.class, () -> service.preview(3L, "blocks", 1, 20));
        version.setFileHash("a".repeat(64));
        version.setParsedObjectKey("documents/2/versions/1/parsed/parse-" + "d".repeat(64) + ".json");
        assertThrows(ServiceException.class, () -> service.preview(3L, "blocks", 1, 20));
        verifyNoInteractions(ai);
    }

    @Test
    void deletedAndPendingVersionsDoNotReadArtifacts()
    {
        when(versions.selectVersionById(3L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.preview(3L, "blocks", 1, 20));
        when(versions.selectVersionById(3L)).thenReturn(version);
        version.setProcessStatus("1");
        assertThrows(ServiceException.class, () -> service.preview(3L, "blocks", 1, 20));
        verifyNoInteractions(storage, ai);
    }

    @Test
    void rejectsVersionDeletedDuringPreviewAndWrongResponseIdentity()
    {
        when(versions.selectVersionById(3L)).thenReturn(version, (OcDocumentVersion) null);
        assertThrows(ServiceException.class, () -> service.preview(3L, "blocks", 1, 20));
        when(versions.selectVersionById(3L)).thenReturn(version);
        doReturn(json.createObjectNode().put("documentVersionId", 999L))
            .when(ai).preview(any(), anyString());
        assertThrows(ServiceException.class, () -> service.preview(3L, "blocks", 1, 20));
    }

    @Test
    void oldJsonStillReadsAndV2StructureSurvivesJavaSerialization() throws Exception
    {
        String old = "{\"fileHash\":\"a\",\"blocks\":[]}";
        assertEquals(1, json.readValue(old, AiDocumentParseResponse.class).getSchemaVersion());
        AiDocumentParseResponse parsed = json.readValue(artifact, AiDocumentParseResponse.class);
        parsed.setSchemaVersion(2);
        parsed.setNormalizationVersion("structure-v2");
        parsed.getBlocks().get(0).setHeadingLevel(2);
        parsed.getBlocks().get(0).setSourceItemIndex(7);
        parsed.getBlocks().get(0).setSourcePartIndex(1);
        parsed.getBlocks().get(0).setTable(json.readTree("{\"rows\":[{\"rowIndex\":0,\"cells\":[]}]}"));
        AiDocumentParseResponse copy = json.readValue(json.writeValueAsBytes(parsed), AiDocumentParseResponse.class);
        assertEquals(2, copy.getSchemaVersion());
        assertEquals(7, copy.getBlocks().get(0).getSourceItemIndex());
        assertEquals(1, copy.getBlocks().get(0).getSourcePartIndex());
        assertEquals(parsed.getBlocks().get(0).getTable(), copy.getBlocks().get(0).getTable());
    }

    @Test
    void contentAddressedKeysKeepDifferentParseResultsSeparate()
    {
        String first = DocumentArtifactKeys.parsedResult(version, artifact);
        String second = DocumentArtifactKeys.parsedResult(version, "different".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(first, second);
        assertEquals(first, DocumentArtifactKeys.parsedResult(version, artifact));
        assertTrue(first.startsWith("documents/2/versions/1/parsed/parse-"));
    }
}
