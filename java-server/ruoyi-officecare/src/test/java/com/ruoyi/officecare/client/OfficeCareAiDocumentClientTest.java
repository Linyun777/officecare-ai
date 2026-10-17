package com.ruoyi.officecare.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.dto.AiAgentTroubleshootRequest;
import com.ruoyi.officecare.domain.dto.AiAgentTroubleshootResponse;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnRequest;
import com.ruoyi.officecare.domain.dto.AiAssistantTurnResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexDeleteResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexRequest;
import com.ruoyi.officecare.domain.dto.AiDocumentIndexResponse;
import com.ruoyi.officecare.domain.dto.AiDocumentParseResponse;
import com.ruoyi.officecare.domain.dto.AiRagAnswerRequest;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateRequest;
import com.ruoyi.officecare.domain.dto.AiRagCacheInvalidateResponse;
import com.ruoyi.officecare.domain.dto.AiRagCacheStatsResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java到FastAPI的multipart协议测试。
 *
 * @author officecare
 */
class OfficeCareAiDocumentClientTest
{
    private HttpServer server;

    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private final AtomicReference<String> requestToken = new AtomicReference<>();

    private final AtomicReference<String> requestTraceId = new AtomicReference<>();

    private final AtomicReference<String> requestMethod = new AtomicReference<>();

    private final AtomicReference<String> requestPath = new AtomicReference<>();

    private volatile int responseStatus;

    private volatile String responseBody;

    private volatile long responseDelayMillis;

    @BeforeEach
    void setUp() throws IOException
    {
        responseStatus = 200;
        responseDelayMillis = 0L;
        responseBody = """
            {
              "fileName": "manual.txt",
              "fileType": "txt",
              "contentType": "text/plain",
              "fileSize": 5,
              "fileHash": "test-hash",
              "pageCount": null,
              "blockCount": 1,
              "characterCount": 5,
              "blocks": [
                {
                  "sequence": 1,
                  "pageNumber": null,
                  "blockType": "paragraph",
                  "text": "hello"
                }
              ],
              "warnings": []
            }
            """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/documents/parse", this::handleRequest);
        server.createContext("/api/v1/internal/documents/index", this::handleRequest);
        server.createContext("/api/v1/internal/rag/answer", this::handleRequest);
        server.createContext("/api/v1/internal/rag/cache/stats", this::handleRequest);
        server.createContext("/api/v1/internal/rag/cache/invalidate", this::handleRequest);
        server.createContext("/api/v1/internal/agent/troubleshoot", this::handleRequest);
        server.createContext("/api/v1/internal/assistant/turn", this::handleRequest);
        server.createContext("/api/v1/internal/assistant/threads", this::handleRequest);
        server.start();
    }

    @AfterEach
    void tearDown()
    {
        server.stop(0);
    }

    @Test
    void shouldSendMultipartFileAndInternalHeaders()
    {
        OfficeCareAiDocumentClient client = createClient();
        AiDocumentParseResponse response = client.parse(
            createVersion(), "hello".getBytes(StandardCharsets.UTF_8), "trace-test-001");

        assertEquals("manual.txt", response.getFileName());
        assertEquals(1, response.getBlockCount());
        assertEquals("test-token", requestToken.get());
        assertEquals("trace-test-001", requestTraceId.get());
        assertTrue(requestBody.get().contains("name=\"file\""));
        assertTrue(requestBody.get().contains("filename=\"manual.txt\""));
        assertTrue(requestBody.get().contains("hello"));
    }

    @Test
    void shouldMapFastApiErrorResponse()
    {
        responseStatus = 422;
        responseBody = """
            {
              "code": "NO_TEXT_EXTRACTED",
              "message": "No extractable text was found.",
              "traceId": "trace-error-001",
              "details": null
            }
            """;

        AiDocumentParseException exception = assertThrows(
            AiDocumentParseException.class,
            () -> createClient().parse(
                createVersion(), "hello".getBytes(StandardCharsets.UTF_8),
                "trace-request-001"));

        assertEquals("NO_TEXT_EXTRACTED", exception.getErrorCode());
        assertEquals("trace-error-001", exception.getTraceId());
        assertEquals("No extractable text was found.", exception.getMessage());
    }

    @Test
    void shouldSendDocumentIndexJsonAndInternalHeaders()
    {
        responseBody = """
            {
              "documentId": 30,
              "documentVersionId": 40,
              "collectionName": "officecare_document_chunks_v1",
              "embeddingModel": "test-model",
              "embeddingDimension": 3,
              "chunkCount": 2,
              "indexedCharacterCount": 80,
              "indexContentHash": "index-hash",
              "indexedAt": "2026-07-29T08:00:00Z"
            }
            """;

        AiDocumentIndexResponse response = createClient().index(
            createIndexRequest(), "trace-index-001");

        assertEquals(40L, response.getDocumentVersionId());
        assertEquals(2, response.getChunkCount());
        assertEquals("POST", requestMethod.get());
        assertEquals("test-token", requestToken.get());
        assertEquals("trace-index-001", requestTraceId.get());
        assertTrue(requestBody.get().contains("\"knowledgeBaseId\":1"));
        assertTrue(requestBody.get().contains("\"documentVersionId\":40"));
    }

    @Test
    void shouldUseDedicatedLongTimeoutForDocumentIndex()
    {
        responseBody = """
            {
              "documentId": 30,
              "documentVersionId": 40,
              "collectionName": "officecare_document_chunks_v1",
              "embeddingModel": "test-model",
              "embeddingDimension": 3,
              "chunkCount": 2,
              "indexedCharacterCount": 80,
              "indexContentHash": "index-hash",
              "indexedAt": "2026-07-29T08:00:00Z"
            }
            """;
        responseDelayMillis = 150L;

        AiDocumentIndexResponse response = createClient(
            Duration.ofMillis(20), Duration.ofSeconds(1))
            .index(createIndexRequest(), "trace-index-timeout-001");

        assertEquals(40L, response.getDocumentVersionId());
        assertEquals(2, response.getChunkCount());
    }

    @Test
    void shouldDeleteDocumentVersionIndex()
    {
        responseBody = """
            {
              "documentVersionId": 40,
              "collectionName": "officecare_document_chunks_v1",
              "deletedCount": 2
            }
            """;

        AiDocumentIndexDeleteResponse response =
            createClient().deleteIndex(40L, "trace-delete-001");

        assertEquals(40L, response.getDocumentVersionId());
        assertEquals(2, response.getDeletedCount());
        assertEquals("DELETE", requestMethod.get());
        assertEquals("trace-delete-001", requestTraceId.get());
    }

    @Test
    void shouldSendRagScopeAndReadCitations()
    {
        responseBody = """
            {
              "answered": true,
              "answer": "请先关闭打印机电源。[资料1]",
              "refusalReason": null,
              "citations": [
                {
                  "citationIndex": 1,
                  "knowledgeBaseId": 1,
                  "brandId": 2,
                  "modelId": 3,
                  "documentId": 30,
                  "documentVersionId": 40,
                  "documentTitle": "维修手册",
                  "originalFileName": "manual.pdf",
                  "pageNumber": 8,
                  "chunkIndex": 2,
                  "heading": "卡纸处理",
                  "quote": "请先关闭打印机电源。",
                  "score": 0.91
                }
              ],
              "retrievedCount": 1,
              "scoreThreshold": 0.45,
              "modelName": "deepseek-v4-pro"
            }
            """;

        AiRagAnswerResponse response = createClient().answer(
            createRagRequest(), "trace-rag-001");

        assertEquals(true, response.getAnswered());
        assertEquals("deepseek-v4-pro", response.getModelName());
        assertEquals(8, response.getCitations().get(0).getPageNumber());
        assertEquals("POST", requestMethod.get());
        assertEquals("test-token", requestToken.get());
        assertEquals("trace-rag-001", requestTraceId.get());
        assertTrue(requestBody.get().contains("\"knowledgeBaseId\":1"));
        assertTrue(requestBody.get().contains("\"brandName\":\"Brother\""));
        assertTrue(requestBody.get().contains("\"modelName\":\"DCP-L2550DW\""));
        assertTrue(requestBody.get().contains("\"documentVersionIds\":[40,41]"));
        assertTrue(requestBody.get().contains("\"scoreThreshold\":0.45"));
    }

    @Test
    void shouldSendAuthorizedAgentContextAndReadDraft()
    {
        responseBody = """
            {
              "answered": true,
              "summary": "先断电，再按手册检查卡纸位置。",
              "troubleshootingSteps": ["关闭电源", "检查后部卡纸"],
              "safetyWarnings": ["等待定影单元冷却"],
              "requiresOnSiteService": false,
              "requiresHumanReview": true,
              "refusalReason": null,
              "draft": {
                "title": "打印机后部卡纸",
                "faultDescription": "用户报修描述：后部卡纸",
                "priority": "1"
              },
              "citations": [
                {
                  "citationIndex": 1,
                  "knowledgeBaseId": 1,
                  "brandId": 2,
                  "modelId": 3,
                  "documentId": 30,
                  "documentVersionId": 40,
                  "documentTitle": "维修手册",
                  "originalFileName": "manual.pdf",
                  "pageNumber": 8,
                  "chunkIndex": 2,
                  "heading": "卡纸处理",
                  "quote": "打开后盖并取出卡纸。",
                  "score": 0.91
                }
              ],
              "toolTrace": [
                {"sequence": 1, "toolName": "inspect_device_context", "success": true},
                {"sequence": 2, "toolName": "search_product_knowledge", "success": true},
                {"sequence": 3, "toolName": "build_work_order_draft", "success": true}
              ],
              "modelName": "deepseek-v4-pro"
            }
            """;

        AiAgentTroubleshootResponse response = createClient().troubleshoot(
            createAgentRequest(), "trace-agent-001");

        assertEquals("打印机后部卡纸", response.getDraft().getTitle());
        assertEquals(3, response.getToolTrace().size());
        assertEquals(true, response.getRequiresHumanReview());
        assertEquals("POST", requestMethod.get());
        assertEquals("test-token", requestToken.get());
        assertEquals("trace-agent-001", requestTraceId.get());
        assertTrue(requestBody.get().contains("\"issueDescription\":\"后部卡纸\""));
        assertTrue(requestBody.get().contains("\"knowledgeBaseId\":1"));
        assertTrue(requestBody.get().contains("\"documentVersionIds\":[40,41]"));
        assertTrue(requestBody.get().contains("\"serialNumber\":\"SN-001\""));
        assertTrue(requestBody.get().contains("\"warrantyEndDate\":\"2027-01-01\""));
    }

    @Test
    void shouldSendAssistantTurnWithBootstrapMessages()
    {
        responseBody = """
            {
              "responseType": "DIAGNOSTIC_STEP",
              "answer": "请先检查并清洁硒鼓单元。[资料1]",
              "diagnosticStatus": "AWAITING_STEP_RESULT",
              "issueSummary": "打印页面每隔94毫米出现斑点",
              "attemptedSteps": ["准备检查硒鼓单元"],
              "observations": ["斑点间隔约94毫米"],
              "nextQuestion": "清洁后斑点是否消失？",
              "requiresUserReply": true,
              "requiresOnSiteService": false,
              "answered": true,
              "refusalReason": null,
              "citations": [
                {
                  "citationIndex": 1,
                  "knowledgeBaseId": 1,
                  "brandId": 2,
                  "modelId": 3,
                  "documentId": 30,
                  "documentVersionId": 40,
                  "documentTitle": "维修手册",
                  "originalFileName": "manual.pdf",
                  "pageNumber": 402,
                  "chunkIndex": 91,
                  "heading": "改善打印质量",
                  "quote": "以94毫米的间隔重复出现斑点时，请清洁硒鼓单元。",
                  "score": 0.82
                }
              ],
              "retrievedCount": 1,
              "scoreThreshold": 0.45,
              "modelName": "deepseek-v4-pro"
            }
            """;

        AiAssistantTurnResponse response = createClient().assistantTurn(
            createAssistantTurnRequest(), "trace-assistant-001");

        assertEquals("DIAGNOSTIC_STEP", response.getResponseType());
        assertEquals("AWAITING_STEP_RESULT", response.getDiagnosticStatus());
        assertEquals(402, response.getCitations().get(0).getPageNumber());
        assertEquals("POST", requestMethod.get());
        assertEquals("/api/v1/internal/assistant/turn", requestPath.get());
        assertEquals("test-token", requestToken.get());
        assertEquals("trace-assistant-001", requestTraceId.get());
        assertTrue(requestBody.get().contains("\"conversationId\":15"));
        assertTrue(requestBody.get().contains("\"userId\":7"));
        assertTrue(requestBody.get().contains(
            "\"turnId\":\"e1feae17-65b9-46ad-b3dd-356676686a41\""));
        assertTrue(requestBody.get().contains("\"bootstrapMessages\""));
        assertTrue(requestBody.get().contains("\"role\":\"assistant\""));
    }

    @Test
    void shouldDeleteAssistantCheckpointThread()
    {
        responseStatus = 204;
        responseBody = "";

        createClient().deleteAssistantThread(
            7L, 15L, "trace-assistant-delete-001");

        assertEquals("DELETE", requestMethod.get());
        assertEquals(
            "/api/v1/internal/assistant/threads/7/15",
            requestPath.get());
        assertEquals("test-token", requestToken.get());
        assertEquals("trace-assistant-delete-001", requestTraceId.get());
    }

    @Test
    void shouldReadRagCacheStatsWithoutCredentialsInResponse()
    {
        responseBody = """
            {
              "enabled": true,
              "redisReachable": true,
              "namespace": "officecare:ai:rag-answer:v1",
              "ttlSeconds": 3600,
              "metricsScope": "PROCESS",
              "knowledgeBaseId": 1,
              "revision": 2,
              "hits": 8,
              "misses": 2,
              "writes": 2,
              "bypasses": 1,
              "errors": 0,
              "invalidEntries": 0,
              "invalidations": 2,
              "hitRate": 0.8
            }
            """;

        AiRagCacheStatsResponse response = createClient().ragCacheStats(
            1L, "trace-cache-stats-001");

        assertEquals(true, response.getEnabled());
        assertEquals(true, response.getRedisReachable());
        assertEquals(2L, response.getRevision());
        assertEquals(8L, response.getHits());
        assertEquals(0.8D, response.getHitRate());
        assertEquals("GET", requestMethod.get());
        assertEquals("trace-cache-stats-001", requestTraceId.get());
    }

    @Test
    void shouldInvalidateRagCacheRevision()
    {
        responseBody = """
            {
              "knowledgeBaseId": 1,
              "revision": 3,
              "reason": "DOCUMENT_REINDEXED",
              "invalidatedAt": "2026-08-03T12:00:00Z"
            }
            """;
        AiRagCacheInvalidateRequest request = new AiRagCacheInvalidateRequest();
        request.setKnowledgeBaseId(1L);
        request.setReason("DOCUMENT_REINDEXED");

        AiRagCacheInvalidateResponse response = createClient()
            .invalidateRagCache(request, "trace-cache-invalidate-001");

        assertEquals(3L, response.getRevision());
        assertEquals("DOCUMENT_REINDEXED", response.getReason());
        assertEquals("POST", requestMethod.get());
        assertEquals("trace-cache-invalidate-001", requestTraceId.get());
        assertTrue(requestBody.get().contains("\"knowledgeBaseId\":1"));
        assertTrue(requestBody.get().contains("\"reason\":\"DOCUMENT_REINDEXED\""));
    }

    private OfficeCareAiDocumentClient createClient()
    {
        return createClient(Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private OfficeCareAiDocumentClient createClient(
        Duration readTimeout, Duration indexReadTimeout)
    {
        OfficeCareAiProperties properties = new OfficeCareAiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setServiceToken("test-token");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(readTimeout);
        properties.setIndexReadTimeout(indexReadTimeout);
        properties.setAgentReadTimeout(Duration.ofSeconds(2));
        return new OfficeCareAiDocumentClient(properties, new ObjectMapper());
    }

    private OcDocumentVersion createVersion()
    {
        OcDocumentVersion version = new OcDocumentVersion();
        version.setOriginalFileName("manual.txt");
        version.setContentType("text/plain");
        version.setFileSize(5L);
        version.setFileHash("test-hash");
        return version;
    }

    private AiDocumentIndexRequest createIndexRequest()
    {
        AiDocumentParseResponse.Block block = new AiDocumentParseResponse.Block();
        block.setSequence(1);
        block.setPageNumber(1);
        block.setBlockType("paragraph");
        block.setText("hello");

        AiDocumentIndexRequest request = new AiDocumentIndexRequest();
        request.setKnowledgeBaseId(1L);
        request.setBrandId(2L);
        request.setModelId(3L);
        request.setDocumentId(30L);
        request.setDocumentVersionId(40L);
        request.setVersionNo(1);
        request.setDocumentTitle("Manual");
        request.setOriginalFileName("manual.txt");
        request.setFileHash("a".repeat(64));
        request.setBlockCount(1);
        request.setCharacterCount(5);
        request.setBlocks(java.util.List.of(block));
        return request;
    }

    private AiRagAnswerRequest createRagRequest()
    {
        AiRagAnswerRequest request = new AiRagAnswerRequest();
        request.setQuestion("打印机卡纸后如何处理？");
        request.setKnowledgeBaseId(1L);
        request.setBrandId(2L);
        request.setModelId(3L);
        request.setBrandName("Brother");
        request.setModelName("DCP-L2550DW");
        request.setDocumentVersionIds(java.util.List.of(40L, 41L));
        request.setTopK(5);
        request.setScoreThreshold(0.45D);
        return request;
    }

    private AiAgentTroubleshootRequest createAgentRequest()
    {
        AiAgentTroubleshootRequest.DeviceContext device =
            new AiAgentTroubleshootRequest.DeviceContext();
        device.setDeviceId(10L);
        device.setCustomerOrgId(20L);
        device.setCustomerName("示例客户");
        device.setDeviceName("一楼打印机");
        device.setSerialNumber("SN-001");
        device.setBrandId(2L);
        device.setBrandName("Brother");
        device.setModelId(3L);
        device.setModelName("DCP-L2550DW");
        device.setInstallLocation("一楼前台");
        device.setWarrantyEndDate("2027-01-01");

        AiAgentTroubleshootRequest request = new AiAgentTroubleshootRequest();
        request.setIssueDescription("后部卡纸");
        request.setKnowledgeBaseId(1L);
        request.setDocumentVersionIds(java.util.List.of(40L, 41L));
        request.setDevice(device);
        request.setTopK(5);
        request.setScoreThreshold(0.45D);
        return request;
    }

    private AiAssistantTurnRequest createAssistantTurnRequest()
    {
        AiAssistantTurnRequest.BootstrapMessage userMessage =
            new AiAssistantTurnRequest.BootstrapMessage();
        userMessage.setRole("user");
        userMessage.setContent("打印页面出现重复斑点");
        AiAssistantTurnRequest.BootstrapMessage assistantMessage =
            new AiAssistantTurnRequest.BootstrapMessage();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("请说明斑点之间的距离。");

        AiAssistantTurnRequest request = new AiAssistantTurnRequest();
        request.setConversationId(15L);
        request.setUserId(7L);
        request.setTurnId("e1feae17-65b9-46ad-b3dd-356676686a41");
        request.setQuestion("大约94毫米");
        request.setKnowledgeBaseId(1L);
        request.setBrandId(2L);
        request.setModelId(3L);
        request.setBrandName("Brother");
        request.setModelName("DCP-L2550DW");
        request.setDocumentVersionIds(java.util.List.of(40L, 41L));
        request.setTopK(5);
        request.setScoreThreshold(0.45D);
        request.setBootstrapMessages(java.util.List.of(
            userMessage, assistantMessage));
        return request;
    }

    private void handleRequest(HttpExchange exchange) throws IOException
    {
        requestMethod.set(exchange.getRequestMethod());
        requestPath.set(exchange.getRequestURI().getPath());
        requestToken.set(exchange.getRequestHeaders()
            .getFirst("X-OfficeCare-Internal-Token"));
        requestTraceId.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8));
        if (responseDelayMillis > 0)
        {
            try
            {
                Thread.sleep(responseDelayMillis);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (responseStatus == 204)
        {
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(responseStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
