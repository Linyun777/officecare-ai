package com.ruoyi.officecare.domain.vo;

/**
 * OfficeCare工作台摘要
 *
 * @author officecare
 */
public class OfficeCareDashboardSummary {
    /**
     * 模块名称
     */
    private String moduleName;

    /**
     * 模块版本
     */
    private String moduleVersion;

    /**
     * 模块状态
     */
    private String status;

    /**
     * 品牌数量
     */
    private long brandCount;

    /**
     * 产品型号数量
     */
    private long productModelCount;

    /**
     * 知识库数量
     */
    private long knowledgeBaseCount;

    /**
     * 待处理文档数量
     */
    private long pendingDocumentCount;

    /**
     * 待处理工单数量
     */
    private long pendingWorkOrderCount;

    /** RabbitMQ文档任务指标 */
    private OfficeCareMqMetrics messageQueue;

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    public void setModuleVersion(String moduleVersion) {
        this.moduleVersion = moduleVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getBrandCount() {
        return brandCount;
    }

    public void setBrandCount(long brandCount) {
        this.brandCount = brandCount;
    }

    public long getProductModelCount() {
        return productModelCount;
    }

    public void setProductModelCount(long productModelCount) {
        this.productModelCount = productModelCount;
    }

    public long getKnowledgeBaseCount() {
        return knowledgeBaseCount;
    }

    public void setKnowledgeBaseCount(long knowledgeBaseCount) {
        this.knowledgeBaseCount = knowledgeBaseCount;
    }

    public long getPendingDocumentCount() {
        return pendingDocumentCount;
    }

    public void setPendingDocumentCount(long pendingDocumentCount) {
        this.pendingDocumentCount = pendingDocumentCount;
    }

    public long getPendingWorkOrderCount() {
        return pendingWorkOrderCount;
    }

    public void setPendingWorkOrderCount(long pendingWorkOrderCount) {
        this.pendingWorkOrderCount = pendingWorkOrderCount;
    }

    public OfficeCareMqMetrics getMessageQueue() {
        return messageQueue;
    }

    public void setMessageQueue(OfficeCareMqMetrics messageQueue) {
        this.messageQueue = messageQueue;
    }
}
