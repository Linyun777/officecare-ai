package com.ruoyi.officecare.client;

/**
 * FastAPI文档解析调用异常。
 *
 * @author officecare
 */
public class AiDocumentParseException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final String errorCode;

    private final String traceId;

    public AiDocumentParseException(String errorCode, String message, String traceId)
    {
        super(message);
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    public AiDocumentParseException(String errorCode, String message, String traceId,
                                    Throwable cause)
    {
        super(message, cause);
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public String getTraceId()
    {
        return traceId;
    }
}

