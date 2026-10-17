package com.ruoyi.officecare.service;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.client.OfficeCareAiDocumentClient;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.constant.AgentDraftConstants;
import com.ruoyi.officecare.domain.OcAgentDraft;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.OcWorkOrder;
import com.ruoyi.officecare.domain.dto.AiAgentTroubleshootRequest;
import com.ruoyi.officecare.domain.dto.AiAgentTroubleshootResponse;
import com.ruoyi.officecare.domain.dto.AiRagAnswerResponse;
import com.ruoyi.officecare.domain.dto.OcAgentAnalyzeRequest;
import com.ruoyi.officecare.domain.dto.OcAgentDraftEditRequest;
import com.ruoyi.officecare.mapper.OcAgentDraftMapper;
import com.ruoyi.officecare.mapper.OcCustomerOrgMapper;
import com.ruoyi.officecare.mapper.OcDeviceMapper;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.service.impl.OcAgentDraftServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 智能报修安全边界和幂等确认测试。 */
class OcAgentDraftServiceImplTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void analyzeShouldUseServerAuthorizedContextAndPersistAuditData()
    {
        OcDevice device = createDevice();
        OcCustomerOrg customer = createCustomer();
        OcKnowledgeBase knowledgeBase = createKnowledgeBase();
        AtomicReference<OcAgentDraft> storedDraft = new AtomicReference<>();
        AtomicReference<AiAgentTroubleshootRequest> capturedRequest =
            new AtomicReference<>();

        OcAgentDraftMapper draftMapper = proxy(OcAgentDraftMapper.class,
            (method, args) -> switch (method)
            {
                case "insertDraft" -> {
                    OcAgentDraft draft = (OcAgentDraft) args[0];
                    draft.setDraftId(99L);
                    storedDraft.set(draft);
                    yield 1;
                }
                case "selectDraftByIdAndUserId" -> storedDraft.get();
                default -> null;
            });
        OcDeviceMapper deviceMapper = proxy(OcDeviceMapper.class,
            (method, args) -> "selectDeviceById".equals(method)
                ? device : null);
        OcCustomerOrgMapper customerMapper = proxy(OcCustomerOrgMapper.class,
            (method, args) -> "selectCustomerOrgById".equals(method)
                ? customer : null);
        OcKnowledgeBaseMapper knowledgeMapper = proxy(OcKnowledgeBaseMapper.class,
            (method, args) -> "selectActiveKnowledgeBaseByModelId".equals(method)
                ? knowledgeBase : null);
        OcDocumentMapper documentMapper = proxy(OcDocumentMapper.class,
            (method, args) -> "selectCurrentIndexedVersionIds".equals(method)
                ? List.of(40L, 41L) : null);
        StubAiClient aiClient = new StubAiClient(createAgentResponse(),
            capturedRequest);

        IOcAgentDraftService service = new OcAgentDraftServiceImpl(
            draftMapper, deviceMapper, customerMapper, knowledgeMapper,
            documentMapper, aiClient, createProperties(), emptyWorkOrderService(),
            objectMapper);
        OcAgentAnalyzeRequest request = new OcAgentAnalyzeRequest();
        request.setDeviceId(10L);
        request.setIssueDescription("  打印机后部卡纸  ");

        OcAgentDraft result = service.analyze(request, 7L, "tester");

        assertEquals(99L, result.getDraftId());
        assertEquals(7L, result.getUserId());
        assertEquals("示例客户", result.getCustomerName());
        assertEquals("Brother", result.getBrandName());
        assertEquals(AgentDraftConstants.STATUS_PENDING, result.getStatus());
        assertEquals(List.of("关闭电源", "检查后部卡纸"),
            result.getTroubleshootingSteps());
        assertEquals(1, result.getCitations().size());
        assertNotNull(capturedRequest.get());
        assertEquals(1L, capturedRequest.get().getKnowledgeBaseId());
        assertEquals(List.of(40L, 41L),
            capturedRequest.get().getDocumentVersionIds());
        assertEquals(20L,
            capturedRequest.get().getDevice().getCustomerOrgId());
        assertEquals("SN-001",
            capturedRequest.get().getDevice().getSerialNumber());
    }

    @Test
    void confirmShouldCreateOnlyOneWorkOrderWhenRepeated()
    {
        OcAgentDraft draft = new OcAgentDraft();
        draft.setDraftId(99L);
        draft.setUserId(7L);
        draft.setCustomerOrgId(20L);
        draft.setDeviceId(10L);
        draft.setStatus(AgentDraftConstants.STATUS_PENDING);
        AtomicInteger insertCount = new AtomicInteger();
        AtomicReference<OcWorkOrder> storedWorkOrder = new AtomicReference<>();

        OcAgentDraftMapper draftMapper = proxy(OcAgentDraftMapper.class,
            (method, args) -> switch (method)
            {
                case "selectDraftForUpdate", "selectDraftByIdAndUserId" -> draft;
                case "updateEditableDraft" -> 1;
                case "markDraftConfirmed" -> {
                    draft.setStatus(AgentDraftConstants.STATUS_CONFIRMED);
                    draft.setWorkOrderId((Long) args[2]);
                    yield 1;
                }
                default -> null;
            });
        IOcWorkOrderService workOrderService = proxy(IOcWorkOrderService.class,
            (method, args) -> switch (method)
            {
                case "insertWorkOrder" -> {
                    insertCount.incrementAndGet();
                    OcWorkOrder workOrder = (OcWorkOrder) args[0];
                    workOrder.setWorkOrderId(501L);
                    workOrder.setWorkOrderNo("OC202608030001");
                    storedWorkOrder.set(workOrder);
                    yield 1;
                }
                case "selectWorkOrderById" -> storedWorkOrder.get();
                default -> null;
            });
        IOcAgentDraftService service = createConfirmationService(
            draftMapper, workOrderService);
        OcAgentDraftEditRequest request = new OcAgentDraftEditRequest();
        request.setTitle("打印机后部卡纸");
        request.setFaultDescription("用户报告后部卡纸，等待售后处理。");
        request.setPriority("1");

        OcWorkOrder first = service.confirmDraft(99L, request, 7L, "tester");
        OcWorkOrder second = service.confirmDraft(99L, request, 7L, "tester");

        assertEquals(1, insertCount.get());
        assertEquals(501L, first.getWorkOrderId());
        assertEquals(first.getWorkOrderId(), second.getWorkOrderId());
        assertEquals(AgentDraftConstants.STATUS_CONFIRMED, draft.getStatus());
        assertEquals(501L, draft.getWorkOrderId());
    }

    private IOcAgentDraftService createConfirmationService(
        OcAgentDraftMapper draftMapper, IOcWorkOrderService workOrderService)
    {
        return new OcAgentDraftServiceImpl(draftMapper,
            proxy(OcDeviceMapper.class, this::emptyInvocation),
            proxy(OcCustomerOrgMapper.class, this::emptyInvocation),
            proxy(OcKnowledgeBaseMapper.class, this::emptyInvocation),
            proxy(OcDocumentMapper.class, this::emptyInvocation),
            new StubAiClient(null, new AtomicReference<>()),
            createProperties(), workOrderService, objectMapper);
    }

    private IOcWorkOrderService emptyWorkOrderService()
    {
        return proxy(IOcWorkOrderService.class, this::emptyInvocation);
    }

    private Object emptyInvocation(String method, Object[] args)
    {
        return null;
    }

    private OcDevice createDevice()
    {
        OcDevice device = new OcDevice();
        device.setDeviceId(10L);
        device.setCustomerOrgId(20L);
        device.setDeviceName("一楼打印机");
        device.setSerialNumber("SN-001");
        device.setBrandId(2L);
        device.setBrandName("Brother");
        device.setModelId(3L);
        device.setModelName("DCP-L2550DW");
        device.setStatus("0");
        return device;
    }

    private OcCustomerOrg createCustomer()
    {
        OcCustomerOrg customer = new OcCustomerOrg();
        customer.setCustomerOrgId(20L);
        customer.setCustomerName("示例客户");
        customer.setStatus("0");
        return customer;
    }

    private OcKnowledgeBase createKnowledgeBase()
    {
        OcKnowledgeBase knowledgeBase = new OcKnowledgeBase();
        knowledgeBase.setKnowledgeBaseId(1L);
        knowledgeBase.setKnowledgeBaseName("DCP-L2550DW知识库");
        return knowledgeBase;
    }

    private AiAgentTroubleshootResponse createAgentResponse()
    {
        AiAgentTroubleshootResponse.WorkOrderDraft workOrderDraft =
            new AiAgentTroubleshootResponse.WorkOrderDraft();
        workOrderDraft.setTitle("打印机后部卡纸");
        workOrderDraft.setFaultDescription("用户报修描述：打印机后部卡纸");
        workOrderDraft.setPriority("1");

        AiRagAnswerResponse.Citation citation =
            new AiRagAnswerResponse.Citation();
        citation.setCitationIndex(1);
        citation.setKnowledgeBaseId(1L);
        citation.setBrandId(2L);
        citation.setModelId(3L);
        citation.setDocumentId(30L);
        citation.setDocumentVersionId(40L);
        citation.setDocumentTitle("维修手册");
        citation.setQuote("打开后盖并取出卡纸。");

        AiAgentTroubleshootResponse response =
            new AiAgentTroubleshootResponse();
        response.setAnswered(true);
        response.setSummary("先断电，再按手册检查卡纸位置。");
        response.setTroubleshootingSteps(List.of("关闭电源", "检查后部卡纸"));
        response.setSafetyWarnings(List.of("等待定影单元冷却"));
        response.setRequiresOnSiteService(false);
        response.setRequiresHumanReview(true);
        response.setDraft(workOrderDraft);
        response.setCitations(List.of(citation));
        response.setToolTrace(List.of(
            trace(1, "inspect_device_context"),
            trace(2, "search_product_knowledge"),
            trace(3, "build_work_order_draft")));
        response.setModelName("deepseek-v4-pro");
        return response;
    }

    private AiAgentTroubleshootResponse.ToolTrace trace(int sequence,
        String toolName)
    {
        AiAgentTroubleshootResponse.ToolTrace trace =
            new AiAgentTroubleshootResponse.ToolTrace();
        trace.setSequence(sequence);
        trace.setToolName(toolName);
        trace.setSuccess(true);
        return trace;
    }

    private OfficeCareAiProperties createProperties()
    {
        OfficeCareAiProperties properties = new OfficeCareAiProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setServiceToken("test-token");
        properties.setConnectTimeout(Duration.ofMillis(10));
        return properties;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, TestInvocation invocation)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),
            new Class<?>[] {type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class)
                {
                    return method.invoke(this, args);
                }
                return invocation.invoke(method.getName(),
                    args == null ? new Object[0] : args);
            });
    }

    @FunctionalInterface
    private interface TestInvocation
    {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private static class StubAiClient extends OfficeCareAiDocumentClient
    {
        private final AiAgentTroubleshootResponse response;
        private final AtomicReference<AiAgentTroubleshootRequest> capturedRequest;

        StubAiClient(AiAgentTroubleshootResponse response,
            AtomicReference<AiAgentTroubleshootRequest> capturedRequest)
        {
            super(properties(), new ObjectMapper());
            this.response = response;
            this.capturedRequest = capturedRequest;
        }

        @Override
        public AiAgentTroubleshootResponse troubleshoot(
            AiAgentTroubleshootRequest request, String traceId)
        {
            capturedRequest.set(request);
            return response;
        }

        private static OfficeCareAiProperties properties()
        {
            OfficeCareAiProperties properties = new OfficeCareAiProperties();
            properties.setBaseUrl("http://127.0.0.1:1");
            properties.setServiceToken("test-token");
            return properties;
        }
    }
}
