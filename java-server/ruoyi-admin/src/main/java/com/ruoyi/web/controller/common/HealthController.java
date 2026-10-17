package com.ruoyi.web.controller.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * 应用存活检查。
 *
 * <p>该接口不访问数据库、Redis或第三方服务，只用于容器编排判断
 * Spring Boot进程是否已经完成HTTP端口启动。</p>
 *
 * @author officecare
 */
@Anonymous
@RestController
public class HealthController
{
    @GetMapping("/health")
    public AjaxResult health()
    {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("service", "officecare-server");
        return AjaxResult.success(data);
    }
}
