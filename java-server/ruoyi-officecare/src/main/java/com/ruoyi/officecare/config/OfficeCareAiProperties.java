package com.ruoyi.officecare.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OfficeCare FastAPI服务配置。
 *
 * @author officecare
 */
@Component
@ConfigurationProperties(prefix = "officecare.ai")
public class OfficeCareAiProperties
{
    /** 是否启用文档解析调用。 */
    private boolean enabled = true;

    /** FastAPI基础地址。 */
    private String baseUrl = "http://localhost:8000";

    /** Java与FastAPI之间的内部共享Token。 */
    private String serviceToken;

    /** 建立连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 等待解析响应超时。 */
    private Duration readTimeout = Duration.ofSeconds(120);

    /** MinerU长文档解析读取超时。 */
    private Duration parseReadTimeout = Duration.ofMinutes(30);

    /** 长文档向量索引读取超时。 */
    private Duration indexReadTimeout = Duration.ofMinutes(30);

    /** 智能报修Agent读取超时。 */
    private Duration agentReadTimeout = Duration.ofMinutes(4);

    /** 单个任务最多人工执行次数。 */
    private int maxAttempts = 3;

    /** RAG一次返回给大模型的最大证据数量。 */
    private int ragTopK = 5;

    /** COSINE相似度最低阈值。 */
    private double ragScoreThreshold = 0.45D;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getServiceToken()
    {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken)
    {
        this.serviceToken = serviceToken;
    }

    public Duration getConnectTimeout()
    {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout()
    {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout)
    {
        this.readTimeout = readTimeout;
    }

    public Duration getParseReadTimeout()
    {
        return parseReadTimeout;
    }

    public void setParseReadTimeout(Duration parseReadTimeout)
    {
        this.parseReadTimeout = parseReadTimeout;
    }

    public Duration getIndexReadTimeout()
    {
        return indexReadTimeout;
    }

    public void setIndexReadTimeout(Duration indexReadTimeout)
    {
        this.indexReadTimeout = indexReadTimeout;
    }

    public Duration getAgentReadTimeout()
    {
        return agentReadTimeout;
    }

    public void setAgentReadTimeout(Duration agentReadTimeout)
    {
        this.agentReadTimeout = agentReadTimeout;
    }

    public int getMaxAttempts()
    {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts)
    {
        if (maxAttempts < 1 || maxAttempts > 10)
        {
            throw new IllegalArgumentException("officecare.ai.max-attempts必须在1到10之间");
        }
        this.maxAttempts = maxAttempts;
    }

    public int getRagTopK()
    {
        return ragTopK;
    }

    public void setRagTopK(int ragTopK)
    {
        if (ragTopK < 1 || ragTopK > 10)
        {
            throw new IllegalArgumentException("officecare.ai.rag-top-k必须在1到10之间");
        }
        this.ragTopK = ragTopK;
    }

    public double getRagScoreThreshold()
    {
        return ragScoreThreshold;
    }

    public void setRagScoreThreshold(double ragScoreThreshold)
    {
        if (ragScoreThreshold < -1D || ragScoreThreshold > 1D)
        {
            throw new IllegalArgumentException(
                "officecare.ai.rag-score-threshold必须在-1到1之间");
        }
        this.ragScoreThreshold = ragScoreThreshold;
    }
}
