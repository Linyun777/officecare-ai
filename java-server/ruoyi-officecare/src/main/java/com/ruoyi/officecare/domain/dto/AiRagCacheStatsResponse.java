package com.ruoyi.officecare.domain.dto;

/** RAG缓存运行统计，不包含连接地址、凭据或缓存内容。 */
public class AiRagCacheStatsResponse
{
    private Boolean enabled;
    private Boolean redisReachable;
    private String namespace;
    private Integer ttlSeconds;
    private String metricsScope;
    private Long knowledgeBaseId;
    private Long revision;
    private Long hits;
    private Long misses;
    private Long writes;
    private Long bypasses;
    private Long errors;
    private Long invalidEntries;
    private Long invalidations;
    private Double hitRate;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean value) { this.enabled = value; }
    public Boolean getRedisReachable() { return redisReachable; }
    public void setRedisReachable(Boolean value) { this.redisReachable = value; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String value) { this.namespace = value; }
    public Integer getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(Integer value) { this.ttlSeconds = value; }
    public String getMetricsScope() { return metricsScope; }
    public void setMetricsScope(String value) { this.metricsScope = value; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long value) { this.knowledgeBaseId = value; }
    public Long getRevision() { return revision; }
    public void setRevision(Long value) { this.revision = value; }
    public Long getHits() { return hits; }
    public void setHits(Long value) { this.hits = value; }
    public Long getMisses() { return misses; }
    public void setMisses(Long value) { this.misses = value; }
    public Long getWrites() { return writes; }
    public void setWrites(Long value) { this.writes = value; }
    public Long getBypasses() { return bypasses; }
    public void setBypasses(Long value) { this.bypasses = value; }
    public Long getErrors() { return errors; }
    public void setErrors(Long value) { this.errors = value; }
    public Long getInvalidEntries() { return invalidEntries; }
    public void setInvalidEntries(Long value) { this.invalidEntries = value; }
    public Long getInvalidations() { return invalidations; }
    public void setInvalidations(Long value) { this.invalidations = value; }
    public Double getHitRate() { return hitRate; }
    public void setHitRate(Double value) { this.hitRate = value; }
}
