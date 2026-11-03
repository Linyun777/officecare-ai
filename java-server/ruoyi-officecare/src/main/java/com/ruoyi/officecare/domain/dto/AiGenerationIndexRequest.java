package com.ruoyi.officecare.domain.dto;

public record AiGenerationIndexRequest(
    String generationId,
    String parseId,
    String profile,
    Long knowledgeBaseId,
    Long brandId,
    Long modelId,
    Long documentId,
    Long documentVersionId,
    Integer versionNo,
    String documentTitle,
    AiDocumentParseResponse parsed)
{
}
