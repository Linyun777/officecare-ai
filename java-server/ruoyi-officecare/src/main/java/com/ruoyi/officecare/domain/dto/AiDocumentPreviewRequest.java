package com.ruoyi.officecare.domain.dto;

/** Only Java-authorized source data crosses the internal preview boundary. */
public record AiDocumentPreviewRequest(
    Long documentVersionId,
    String fileHash,
    String parseId,
    AiDocumentParseResponse parsed,
    String view,
    int pageNum,
    int pageSize,
    String selectedModel)
{
}
