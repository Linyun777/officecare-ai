package com.ruoyi.officecare.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

/** A build candidate; READY does not mean published. */
public class OcDocumentIndexGeneration
{
    private String generationId;
    private Long documentId;
    private Long documentVersionId;
    private Long knowledgeBaseId;
    private Long brandId;
    private Long modelId;
    private String parseId;
    private String parsedObjectKey;
    private String profile;
    private String status;
    private String manifestJson;
    private String errorMessage;
    private String createBy;
    private Date createTime;
    private Date updateTime;

    public String getGenerationId() { return generationId; }
    public void setGenerationId(String value) { generationId = value; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long value) { documentId = value; }
    public Long getDocumentVersionId() { return documentVersionId; }
    public void setDocumentVersionId(Long value) { documentVersionId = value; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long value) { knowledgeBaseId = value; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long value) { brandId = value; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long value) { modelId = value; }
    public String getParseId() { return parseId; }
    public void setParseId(String value) { parseId = value; }
    @JsonIgnore
    public String getParsedObjectKey() { return parsedObjectKey; }
    public void setParsedObjectKey(String value) { parsedObjectKey = value; }
    public String getProfile() { return profile; }
    public void setProfile(String value) { profile = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    @JsonProperty("manifest")
    @JsonRawValue
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String value) { manifestJson = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date value) { updateTime = value; }
}
