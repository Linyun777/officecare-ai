package com.ruoyi.officecare.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 自动重试错误分类测试。 */
class DocumentTaskRetryPolicyTest
{
    private final DocumentTaskRetryPolicy policy = new DocumentTaskRetryPolicy();

    @Test
    void shouldRetryTransientInfrastructureErrors()
    {
        assertTrue(policy.isRetryable("AI_SERVICE_UNAVAILABLE"));
        assertTrue(policy.isRetryable("MINERU_REQUEST_TIMEOUT"));
        assertTrue(policy.isRetryable("EMBEDDING_RATE_LIMITED"));
        assertTrue(policy.isRetryable("MILVUS_CONNECTION_FAILED"));
    }

    @Test
    void shouldNotRetryInvalidDataOrCredentials()
    {
        assertFalse(policy.isRetryable("FILE_HASH_MISMATCH"));
        assertFalse(policy.isRetryable("MINERU_TOKEN_INVALID"));
        assertFalse(policy.isRetryable("INVALID_INDEX_RESPONSE"));
    }
}
