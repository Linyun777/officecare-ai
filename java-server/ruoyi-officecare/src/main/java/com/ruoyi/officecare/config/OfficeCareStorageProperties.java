package com.ruoyi.officecare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OfficeCare文档存储配置。
 *
 * @author officecare
 */
@Component
@ConfigurationProperties(prefix = "officecare.storage")
public class OfficeCareStorageProperties
{
    /** 存储类型，第一版只支持local。 */
    private String type = "local";

    /** 本地存储根目录。 */
    private String root = "D:/officecare-data/documents";

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getRoot()
    {
        return root;
    }

    public void setRoot(String root)
    {
        this.root = root;
    }
}
