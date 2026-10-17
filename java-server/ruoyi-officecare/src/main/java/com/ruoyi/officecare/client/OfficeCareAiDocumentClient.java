package com.ruoyi.officecare.client;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.dto.AiAgentTroubleshootRequest;
import com.ruoyi.officecare.domain.dto.AiAgentTroubleshootResponse;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnRequest;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnResponse;
import com.ruoyi.officecare.domain.dto.AiRagAnswerRequest;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateRequest;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheStatsResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexDeleteResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexRequest;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.domain.dto.AiServiceErrorResponse;

/**
 * FastAPI内部文档解析客户端。
 *
 * @author officecare
 */
@Component
public class OfficeCareAiDocumentClient {
    private static final String TOKEN_HEADER = "X-OfficeCare-Internal-Token";

    private static final String TRACE_HEADER = "X-Trace-Id";

    private static final String PARSE_PATH = "/api/v1/internal/documents/parse";

    private static final String INDEX_PATH = "/api/v1/internal/documents/index";

    private static final String RAG_PATH = "/api/v1/internal/rag/answer";

    private static final String RAG_CACHE_STATS_PATH =
        "/api/v1/internal/rag/cache/stats";

    private static final String RAG_CACHE_INVALIDATE_PATH =
        "/api/v1/internal/rag/cache/invalidate";

    private static final String AGENT_PATH = "/api/v1/internal/agent/troubleshoot";

    private static final String ASSISTANT_TURN_PATH =
        "/api/v1/internal/assistant/turn";

    private static final String ASSISTANT_THREAD_PATH =
        "/api/v1/internal/assistant/threads/{userId}/{conversationId}";

    private final OfficeCareAiProperties properties;

    private final ObjectMapper objectMapper;

    private final RestClient restClient;

    private final RestClient parseRestClient;

    private final RestClient indexRestClient;

    private final RestClient agentRestClient;

    public OfficeCareAiDocumentClient(OfficeCareAiProperties properties,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = createRestClient(properties.getReadTimeout());
        this.parseRestClient = createRestClient(properties.getParseReadTimeout());
        this.indexRestClient = createRestClient(properties.getIndexReadTimeout());
        this.agentRestClient = createRestClient(properties.getAgentReadTimeout());
    }

    private RestClient createRestClient(java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public AiDocumentParseResponse parse(OcDocumentVersion version, byte[] fileContent,
                                         String traceId) {
        requireAvailable(traceId);
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return version.getOriginalFileName();
            }
        };
        multipart.add("file", resource);

        try {
            AiDocumentParseResponse response = parseRestClient.post()
                    .uri(PARSE_PATH)
                    .header(TOKEN_HEADER, properties.getServiceToken())
                    .header(TRACE_HEADER, traceId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(multipart)
                    .retrieve()
                    .body(AiDocumentParseResponse.class);
            if (response == null) {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                        "FastAPI返回了空响应", traceId);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw parseHttpException(exception, traceId);
        } catch (ResourceAccessException exception) {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                    "无法连接FastAPI或等待解析响应超时", traceId, exception);
        } catch (RestClientException exception) {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                    "调用FastAPI文档解析接口失败", traceId, exception);
        }
    }

    public AiDocumentIndexResponse index(AiDocumentIndexRequest request, String traceId) {
        requireAvailable(traceId);
        try {
            AiDocumentIndexResponse response = indexRestClient.post()
                    .uri(INDEX_PATH)
                    .header(TOKEN_HEADER, properties.getServiceToken())
                    .header(TRACE_HEADER, traceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiDocumentIndexResponse.class);
            if (response == null) {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                        "FastAPI返回了空索引响应", traceId);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw parseHttpException(exception, traceId);
        } catch (ResourceAccessException exception) {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                    "无法连接FastAPI或等待向量索引响应超时", traceId, exception);
        } catch (RestClientException exception) {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                    "调用FastAPI文档索引接口失败", traceId, exception);
        }
    }

    public AiDocumentIndexDeleteResponse deleteIndex(Long documentVersionId,
                                                      String traceId) {
        requireAvailable(traceId);
        try {
            AiDocumentIndexDeleteResponse response = restClient.delete()
                    .uri(INDEX_PATH + "/{documentVersionId}", documentVersionId)
                    .header(TOKEN_HEADER, properties.getServiceToken())
                    .header(TRACE_HEADER, traceId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AiDocumentIndexDeleteResponse.class);
            if (response == null) {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                        "FastAPI返回了空索引删除响应", traceId);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw parseHttpException(exception, traceId);
        } catch (ResourceAccessException exception) {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                    "无法连接FastAPI或等待索引删除响应超时", traceId, exception);
        } catch (RestClientException exception) {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                    "调用FastAPI索引删除接口失败", traceId, exception);
        }
    }

    /**
     * 使用Java确认过的知识库和版本范围调用FastAPI知识问答。
     */
    public AiRagAnswerResponse answer(AiRagAnswerRequest request, String traceId) {
        requireAvailable(traceId);
        try {
            AiRagAnswerResponse response = restClient.post()
                    .uri(RAG_PATH)
                    .header(TOKEN_HEADER, properties.getServiceToken())
                    .header(TRACE_HEADER, traceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiRagAnswerResponse.class);
            if (response == null) {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                        "FastAPI返回了空问答响应", traceId);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw parseHttpException(exception, traceId);
        } catch (ResourceAccessException exception) {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                    "无法连接FastAPI或等待知识问答响应超时", traceId, exception);
        } catch (RestClientException exception) {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                    "调用FastAPI知识问答接口失败", traceId, exception);
        }
    }

    /** 查询FastAPI RAG缓存进程指标和可选知识库修订号。 */
    public AiRagCacheStatsResponse ragCacheStats(
        Long knowledgeBaseId, String traceId)
    {
        requireAvailable(traceId);
        try
        {
            AiRagCacheStatsResponse response = restClient.get()
                .uri(uriBuilder ->
                {
                    uriBuilder.path(RAG_CACHE_STATS_PATH);
                    if (knowledgeBaseId != null)
                    {
                        uriBuilder.queryParam("knowledgeBaseId", knowledgeBaseId);
                    }
                    return uriBuilder.build();
                })
                .header(TOKEN_HEADER, properties.getServiceToken())
                .header(TRACE_HEADER, traceId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AiRagCacheStatsResponse.class);
            if (response == null)
            {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                    "FastAPI返回了空缓存统计响应", traceId);
            }
            return response;
        }
        catch (RestClientResponseException exception)
        {
            throw parseHttpException(exception, traceId);
        }
        catch (ResourceAccessException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                "无法连接FastAPI或等待缓存统计响应超时", traceId, exception);
        }
        catch (RestClientException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                "调用FastAPI缓存统计接口失败", traceId, exception);
        }
    }

    /** 提升指定知识库的RAG缓存修订号。 */
    public AiRagCacheInvalidateResponse invalidateRagCache(
        AiRagCacheInvalidateRequest request, String traceId)
    {
        requireAvailable(traceId);
        try
        {
            AiRagCacheInvalidateResponse response = restClient.post()
                .uri(RAG_CACHE_INVALIDATE_PATH)
                .header(TOKEN_HEADER, properties.getServiceToken())
                .header(TRACE_HEADER, traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiRagCacheInvalidateResponse.class);
            if (response == null)
            {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                    "FastAPI返回了空缓存失效响应", traceId);
            }
            return response;
        }
        catch (RestClientResponseException exception)
        {
            throw parseHttpException(exception, traceId);
        }
        catch (ResourceAccessException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                "无法连接FastAPI或等待缓存失效响应超时", traceId, exception);
        }
        catch (RestClientException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                "调用FastAPI缓存失效接口失败", traceId, exception);
        }
    }

    /** 调用FastAPI受控Agent生成等待人工确认的工单草稿。 */
    public AiAgentTroubleshootResponse troubleshoot(
            AiAgentTroubleshootRequest request, String traceId) {
        requireAvailable(traceId);
        try {
            AiAgentTroubleshootResponse response = agentRestClient.post()
                    .uri(AGENT_PATH)
                    .header(TOKEN_HEADER, properties.getServiceToken())
                    .header(TRACE_HEADER, traceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiAgentTroubleshootResponse.class);
            if (response == null) {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                        "FastAPI返回了空Agent响应", traceId);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw parseHttpException(exception, traceId);
        } catch (ResourceAccessException exception) {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                    "无法连接FastAPI或等待Agent响应超时", traceId, exception);
        } catch (RestClientException exception) {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                    "调用FastAPI智能排障接口失败", traceId, exception);
        }
    }

    /** 调用带PostgreSQL短期记忆的多轮AI助手。 */
    public AiAssistantTurnResponse assistantTurn(
        AiAssistantTurnRequest request, String traceId)
    {
        requireAvailable(traceId);
        try
        {
            AiAssistantTurnResponse response = agentRestClient.post()
                .uri(ASSISTANT_TURN_PATH)
                .header(TOKEN_HEADER, properties.getServiceToken())
                .header(TRACE_HEADER, traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiAssistantTurnResponse.class);
            if (response == null)
            {
                throw new AiDocumentParseException("AI_EMPTY_RESPONSE",
                    "FastAPI返回了空AI助手响应", traceId);
            }
            return response;
        }
        catch (RestClientResponseException exception)
        {
            throw parseHttpException(exception, traceId);
        }
        catch (ResourceAccessException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                "无法连接FastAPI或等待AI助手响应超时", traceId, exception);
        }
        catch (RestClientException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                "调用FastAPI多轮AI助手接口失败", traceId, exception);
        }
    }

    /** 删除指定用户会话的PostgreSQL Checkpoint。 */
    public void deleteAssistantThread(
        Long userId, Long conversationId, String traceId)
    {
        requireAvailable(traceId);
        try
        {
            agentRestClient.delete()
                .uri(ASSISTANT_THREAD_PATH, userId, conversationId)
                .header(TOKEN_HEADER, properties.getServiceToken())
                .header(TRACE_HEADER, traceId)
                .retrieve()
                .toBodilessEntity();
        }
        catch (RestClientResponseException exception)
        {
            throw parseHttpException(exception, traceId);
        }
        catch (ResourceAccessException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_UNAVAILABLE",
                "无法连接FastAPI或等待AI助手记忆删除响应超时", traceId, exception);
        }
        catch (RestClientException exception)
        {
            throw new AiDocumentParseException("AI_SERVICE_REQUEST_FAILED",
                "调用FastAPI删除AI助手记忆接口失败", traceId, exception);
        }
    }

    private void requireAvailable(String traceId) {
        if (!properties.isEnabled()) {
            throw new AiDocumentParseException("AI_SERVICE_DISABLED",
                    "FastAPI文档解析调用当前已停用", traceId);
        }
        if (StringUtils.isEmpty(StringUtils.trim(properties.getServiceToken()))) {
            throw new AiDocumentParseException("AI_SERVICE_NOT_CONFIGURED",
                    "未在application-dev.yml中配置officecare.ai.service-token", traceId);
        }
    }

    private AiDocumentParseException parseHttpException(
            RestClientResponseException exception, String fallbackTraceId) {
        try {
            AiServiceErrorResponse error = objectMapper.readValue(
                    exception.getResponseBodyAsByteArray(), AiServiceErrorResponse.class);
            String code = StringUtils.defaultIfEmpty(error.getCode(),
                    "AI_SERVICE_HTTP_" + exception.getStatusCode().value());
            String message = StringUtils.defaultIfEmpty(error.getMessage(),
                    "FastAPI返回HTTP " + exception.getStatusCode().value());
            String traceId = StringUtils.defaultIfEmpty(error.getTraceId(), fallbackTraceId);
            return new AiDocumentParseException(code, message, traceId, exception);
        } catch (IOException ignored) {
            return new AiDocumentParseException(
                    "AI_SERVICE_HTTP_" + exception.getStatusCode().value(),
                    "FastAPI返回HTTP " + exception.getStatusCode().value(),
                    fallbackTraceId, exception);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = StringUtils.trim(baseUrl);
        if (StringUtils.isEmpty(normalized)) {
            return "http://localhost:8000";
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
