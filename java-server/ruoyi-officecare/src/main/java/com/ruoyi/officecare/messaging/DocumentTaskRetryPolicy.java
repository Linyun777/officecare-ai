package com.ruoyi.officecare.messaging;

import java.util.Locale;
import java.util.Set;

/**
 * 区分可恢复的基础设施故障与重试也无法修复的数据/配置错误。
 *
 * @author officecare
 */
public class DocumentTaskRetryPolicy
{
    private static final Set<String> RETRYABLE_CODES = Set.of(
        "AI_EMPTY_RESPONSE",
        "AI_SERVICE_UNAVAILABLE",
        "AI_SERVICE_REQUEST_FAILED",
        "DOCUMENT_STORAGE_ERROR",
        "DOCUMENT_PROCESSING_ERROR",
        "DOCUMENT_INDEXING_ERROR",
        "DOCUMENT_INDEX_DELETE_ERROR"
    );

    public boolean isRetryable(String errorCode)
    {
        if (errorCode == null || errorCode.isBlank())
        {
            return true;
        }
        String normalized = errorCode.trim().toUpperCase(Locale.ROOT);
        if (RETRYABLE_CODES.contains(normalized))
        {
            return true;
        }
        return normalized.contains("TIMEOUT")
            || normalized.contains("UNAVAILABLE")
            || normalized.contains("RATE_LIMIT")
            || normalized.contains("CONNECTION")
            || normalized.contains("NETWORK")
            || normalized.endsWith("_DOWNLOAD_FAILED")
            || normalized.endsWith("_WRITE_FAILED")
            || normalized.endsWith("_REQUEST_FAILED");
    }
}
