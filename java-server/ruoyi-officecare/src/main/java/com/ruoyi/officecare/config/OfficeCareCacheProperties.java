package com.ruoyi.officecare.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OfficeCare Java业务缓存配置。
 *
 * @author officecare
 */
@Component
@ConfigurationProperties(prefix = "officecare.cache")
public class OfficeCareCacheProperties
{
    /** 是否启用品牌、型号和知识库基础数据缓存。 */
    private boolean enabled = true;

    /** 基础数据缓存有效期。 */
    private Duration referenceTtl = Duration.ofMinutes(30);

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public Duration getReferenceTtl()
    {
        return referenceTtl;
    }

    public void setReferenceTtl(Duration referenceTtl)
    {
        if (referenceTtl == null || referenceTtl.isZero() || referenceTtl.isNegative())
        {
            throw new IllegalArgumentException("officecare.cache.reference-ttl必须大于0");
        }
        if (referenceTtl.compareTo(Duration.ofDays(1)) > 0)
        {
            throw new IllegalArgumentException("officecare.cache.reference-ttl不能超过24小时");
        }
        this.referenceTtl = referenceTtl;
    }
}
