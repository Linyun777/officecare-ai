package com.ruoyi.officecare.domain.dto;

import java.util.List;

/** Java调用FastAPI智能排障Agent的请求。 */
public class AiAgentTroubleshootRequest
{
    private String issueDescription;

    private Long knowledgeBaseId;

    private List<Long> documentVersionIds;

    private DeviceContext device;

    private Integer topK;

    private Double scoreThreshold;

    public String getIssueDescription()
    {
        return issueDescription;
    }

    public void setIssueDescription(String issueDescription)
    {
        this.issueDescription = issueDescription;
    }

    public Long getKnowledgeBaseId()
    {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId)
    {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public List<Long> getDocumentVersionIds()
    {
        return documentVersionIds;
    }

    public void setDocumentVersionIds(List<Long> documentVersionIds)
    {
        this.documentVersionIds = documentVersionIds;
    }

    public DeviceContext getDevice()
    {
        return device;
    }

    public void setDevice(DeviceContext device)
    {
        this.device = device;
    }

    public Integer getTopK()
    {
        return topK;
    }

    public void setTopK(Integer topK)
    {
        this.topK = topK;
    }

    public Double getScoreThreshold()
    {
        return scoreThreshold;
    }

    public void setScoreThreshold(Double scoreThreshold)
    {
        this.scoreThreshold = scoreThreshold;
    }

    /** Java校验后传给FastAPI的设备快照。 */
    public static class DeviceContext
    {
        private Long deviceId;
        private Long customerOrgId;
        private String customerName;
        private String deviceName;
        private String serialNumber;
        private Long brandId;
        private String brandName;
        private Long modelId;
        private String modelName;
        private String installLocation;
        private String warrantyEndDate;

        public Long getDeviceId()
        {
            return deviceId;
        }

        public void setDeviceId(Long deviceId)
        {
            this.deviceId = deviceId;
        }

        public Long getCustomerOrgId()
        {
            return customerOrgId;
        }

        public void setCustomerOrgId(Long customerOrgId)
        {
            this.customerOrgId = customerOrgId;
        }

        public String getCustomerName()
        {
            return customerName;
        }

        public void setCustomerName(String customerName)
        {
            this.customerName = customerName;
        }

        public String getDeviceName()
        {
            return deviceName;
        }

        public void setDeviceName(String deviceName)
        {
            this.deviceName = deviceName;
        }

        public String getSerialNumber()
        {
            return serialNumber;
        }

        public void setSerialNumber(String serialNumber)
        {
            this.serialNumber = serialNumber;
        }

        public Long getBrandId()
        {
            return brandId;
        }

        public void setBrandId(Long brandId)
        {
            this.brandId = brandId;
        }

        public String getBrandName()
        {
            return brandName;
        }

        public void setBrandName(String brandName)
        {
            this.brandName = brandName;
        }

        public Long getModelId()
        {
            return modelId;
        }

        public void setModelId(Long modelId)
        {
            this.modelId = modelId;
        }

        public String getModelName()
        {
            return modelName;
        }

        public void setModelName(String modelName)
        {
            this.modelName = modelName;
        }

        public String getInstallLocation()
        {
            return installLocation;
        }

        public void setInstallLocation(String installLocation)
        {
            this.installLocation = installLocation;
        }

        public String getWarrantyEndDate()
        {
            return warrantyEndDate;
        }

        public void setWarrantyEndDate(String warrantyEndDate)
        {
            this.warrantyEndDate = warrantyEndDate;
        }
    }
}
