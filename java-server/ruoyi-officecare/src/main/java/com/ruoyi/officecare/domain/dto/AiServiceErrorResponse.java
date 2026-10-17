package com.ruoyi.officecare.domain.dto;

/**
 * FastAPI统一错误响应。
 *
 * @author officecare
 */
public class AiServiceErrorResponse
{
    private String code;

    private String message;

    private String traceId;

    private Object details;

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public Object getDetails()
    {
        return details;
    }

    public void setDetails(Object details)
    {
        this.details = details;
    }
}
