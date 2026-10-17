package com.ruoyi.officecare.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
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
import com.ruoyi.officecare.service.IOcAgentDraftService;
import com.ruoyi.officecare.service.IOcWorkOrderService;

/** 智能报修草稿服务实现。 */
@Service
public class OcAgentDraftServiceImpl implements IOcAgentDraftService
{
    private static final List<String> REQUIRED_TOOLS = List.of(
        "inspect_device_context", "search_product_knowledge",
        "build_work_order_draft");

    private final OcAgentDraftMapper draftMapper;
    private final OcDeviceMapper deviceMapper;
    private final OcCustomerOrgMapper customerOrgMapper;
    private final OcKnowledgeBaseMapper knowledgeBaseMapper;
    private final OcDocumentMapper documentMapper;
    private final OfficeCareAiDocumentClient aiClient;
    private final OfficeCareAiProperties aiProperties;
    private final IOcWorkOrderService workOrderService;
    private final ObjectMapper objectMapper;

    public OcAgentDraftServiceImpl(OcAgentDraftMapper draftMapper,
        OcDeviceMapper deviceMapper, OcCustomerOrgMapper customerOrgMapper,
        OcKnowledgeBaseMapper knowledgeBaseMapper,
        OcDocumentMapper documentMapper, OfficeCareAiDocumentClient aiClient,
        OfficeCareAiProperties aiProperties,
        IOcWorkOrderService workOrderService, ObjectMapper objectMapper)
    {
        this.draftMapper = draftMapper;
        this.deviceMapper = deviceMapper;
        this.customerOrgMapper = customerOrgMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.aiClient = aiClient;
        this.aiProperties = aiProperties;
        this.workOrderService = workOrderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<OcAgentDraft> selectDraftList(OcAgentDraft filter, Long userId)
    {
        filter.setUserId(userId);
        List<OcAgentDraft> drafts = draftMapper.selectDraftList(filter);
        drafts.forEach(this::hydrateJsonFields);
        return drafts;
    }

    @Override
    public OcAgentDraft selectDraftById(Long draftId, Long userId)
    {
        return hydrateJsonFields(requireOwnedDraft(draftId, userId));
    }

    @Override
    public OcAgentDraft analyze(OcAgentAnalyzeRequest request,
        Long userId, String username)
    {
        OcDevice device = deviceMapper.selectDeviceById(request.getDeviceId());
        if (device == null || !"0".equals(device.getStatus()))
        {
            throw new ServiceException("设备不存在或已停用");
        }
        OcCustomerOrg customer = customerOrgMapper.selectCustomerOrgById(
            device.getCustomerOrgId());
        if (customer == null || !"0".equals(customer.getStatus()))
        {
            throw new ServiceException("设备所属客户不存在或已停用");
        }
        OcKnowledgeBase knowledgeBase = knowledgeBaseMapper
            .selectActiveKnowledgeBaseByModelId(device.getModelId());
        if (knowledgeBase == null)
        {
            throw new ServiceException("当前设备型号没有可用知识库");
        }
        List<Long> versionIds = documentMapper.selectCurrentIndexedVersionIds(
            knowledgeBase.getKnowledgeBaseId());
        if (versionIds == null || versionIds.isEmpty())
        {
            throw new ServiceException("当前设备型号知识库没有已成功索引的文档");
        }

        String issueDescription = StringUtils.trim(request.getIssueDescription());
        AiAgentTroubleshootRequest aiRequest = buildAiRequest(device, customer,
            knowledgeBase, versionIds, issueDescription);
        String traceId = "agent-" + UUID.randomUUID();
        AiAgentTroubleshootResponse response = aiClient.troubleshoot(
            aiRequest, traceId);
        validateAgentResponse(response, aiRequest);

        OcAgentDraft draft = buildDraft(device, customer, knowledgeBase,
            issueDescription, response, userId, username);
        if (draftMapper.insertDraft(draft) != 1)
        {
            throw new ServiceException("保存智能报修草稿失败");
        }
        return selectDraftById(draft.getDraftId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OcAgentDraft updateDraft(Long draftId,
        OcAgentDraftEditRequest request, Long userId, String username)
    {
        OcAgentDraft existing = requireOwnedDraft(draftId, userId);
        requirePending(existing);
        applyEditableFields(existing, request, username);
        if (draftMapper.updateEditableDraft(existing) != 1)
        {
            throw new ServiceException("草稿已被其他操作修改，请刷新后重试");
        }
        return selectDraftById(draftId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OcWorkOrder confirmDraft(Long draftId,
        OcAgentDraftEditRequest request, Long userId, String username)
    {
        OcAgentDraft draft = draftMapper.selectDraftForUpdate(draftId, userId);
        if (draft == null)
        {
            throw new ServiceException("智能报修草稿不存在");
        }
        if (AgentDraftConstants.STATUS_CONFIRMED.equals(draft.getStatus()))
        {
            if (draft.getWorkOrderId() == null)
            {
                throw new ServiceException("草稿确认状态异常，请联系管理员");
            }
            return requireWorkOrder(draft.getWorkOrderId());
        }
        requirePending(draft);
        applyEditableFields(draft, request, username);
        if (draftMapper.updateEditableDraft(draft) != 1)
        {
            throw new ServiceException("草稿已被其他操作修改，请刷新后重试");
        }

        OcWorkOrder workOrder = new OcWorkOrder();
        workOrder.setCustomerOrgId(draft.getCustomerOrgId());
        workOrder.setDeviceId(draft.getDeviceId());
        workOrder.setTitle(draft.getDraftTitle());
        workOrder.setFaultDescription(draft.getDraftFaultDescription());
        workOrder.setPriority(draft.getDraftPriority());
        workOrder.setAssigneeUserId(draft.getAssigneeUserId());
        workOrder.setRemark(draft.getWorkOrderRemark());
        workOrderService.insertWorkOrder(workOrder, userId, username);

        if (draftMapper.markDraftConfirmed(draftId, userId,
            workOrder.getWorkOrderId(), username) != 1)
        {
            throw new ServiceException("确认草稿失败，正式工单未创建");
        }
        return requireWorkOrder(workOrder.getWorkOrderId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelDrafts(Long[] draftIds, Long userId, String username)
    {
        if (draftIds == null || draftIds.length == 0)
        {
            throw new ServiceException("请选择要取消的草稿");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long draftId : draftIds)
        {
            if (draftId == null || !uniqueIds.add(draftId))
            {
                throw new ServiceException("草稿ID不能为空或重复");
            }
            requirePending(requireOwnedDraft(draftId, userId));
        }
        Long[] ids = uniqueIds.toArray(Long[]::new);
        int rows = draftMapper.cancelDrafts(ids, userId, username);
        if (rows != ids.length)
        {
            throw new ServiceException("部分草稿状态已变化，请刷新后重试");
        }
        return rows;
    }

    private AiAgentTroubleshootRequest buildAiRequest(OcDevice device,
        OcCustomerOrg customer, OcKnowledgeBase knowledgeBase,
        List<Long> versionIds, String issueDescription)
    {
        AiAgentTroubleshootRequest.DeviceContext context =
            new AiAgentTroubleshootRequest.DeviceContext();
        context.setDeviceId(device.getDeviceId());
        context.setCustomerOrgId(device.getCustomerOrgId());
        context.setCustomerName(customer.getCustomerName());
        context.setDeviceName(device.getDeviceName());
        context.setSerialNumber(device.getSerialNumber());
        context.setBrandId(device.getBrandId());
        context.setBrandName(device.getBrandName());
        context.setModelId(device.getModelId());
        context.setModelName(device.getModelName());
        context.setInstallLocation(device.getInstallLocation());
        if (device.getWarrantyEndDate() != null)
        {
            context.setWarrantyEndDate(new SimpleDateFormat("yyyy-MM-dd")
                .format(device.getWarrantyEndDate()));
        }

        AiAgentTroubleshootRequest request = new AiAgentTroubleshootRequest();
        request.setIssueDescription(issueDescription);
        request.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        request.setDocumentVersionIds(versionIds);
        request.setDevice(context);
        request.setTopK(aiProperties.getRagTopK());
        request.setScoreThreshold(aiProperties.getRagScoreThreshold());
        return request;
    }

    private OcAgentDraft buildDraft(OcDevice device, OcCustomerOrg customer,
        OcKnowledgeBase knowledgeBase, String issueDescription,
        AiAgentTroubleshootResponse response, Long userId, String username)
    {
        OcAgentDraft draft = new OcAgentDraft();
        draft.setUserId(userId);
        draft.setCustomerOrgId(device.getCustomerOrgId());
        draft.setDeviceId(device.getDeviceId());
        draft.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        draft.setCustomerName(customer.getCustomerName());
        draft.setDeviceName(device.getDeviceName());
        draft.setSerialNumber(device.getSerialNumber());
        draft.setBrandName(device.getBrandName());
        draft.setModelName(device.getModelName());
        draft.setKnowledgeBaseName(knowledgeBase.getKnowledgeBaseName());
        draft.setIssueDescription(issueDescription);
        draft.setAnswered(Boolean.TRUE.equals(response.getAnswered()));
        draft.setSummary(response.getSummary());
        draft.setTroubleshootingStepsJson(writeJson(response.getTroubleshootingSteps()));
        draft.setSafetyWarningsJson(writeJson(response.getSafetyWarnings()));
        draft.setRequiresOnSiteService(Boolean.TRUE.equals(
            response.getRequiresOnSiteService()));
        draft.setRefusalReason(response.getRefusalReason());
        draft.setDraftTitle(StringUtils.trim(response.getDraft().getTitle()));
        draft.setDraftFaultDescription(StringUtils.trim(
            response.getDraft().getFaultDescription()));
        draft.setDraftPriority(response.getDraft().getPriority());
        draft.setCitationsJson(writeJson(response.getCitations()));
        draft.setToolTraceJson(writeJson(response.getToolTrace()));
        draft.setModelNameUsed(response.getModelName());
        draft.setStatus(AgentDraftConstants.STATUS_PENDING);
        draft.setCreateBy(username);
        return draft;
    }

    private void validateAgentResponse(AiAgentTroubleshootResponse response,
        AiAgentTroubleshootRequest request)
    {
        if (response == null || response.getDraft() == null
            || response.getAnswered() == null
            || response.getRequiresOnSiteService() == null
            || !Boolean.TRUE.equals(response.getRequiresHumanReview()))
        {
            throw new ServiceException("FastAPI返回的Agent草稿协议不完整");
        }
        if (StringUtils.isEmpty(StringUtils.trim(response.getSummary()))
            || StringUtils.isEmpty(StringUtils.trim(response.getDraft().getTitle()))
            || StringUtils.isEmpty(StringUtils.trim(
                response.getDraft().getFaultDescription()))
            || response.getSummary().length() > 1000
            || response.getDraft().getTitle().length() > 150
            || response.getDraft().getFaultDescription().length() > 2000
            || response.getDraft().getPriority() == null
            || !response.getDraft().getPriority().matches("^[0123]$"))
        {
            throw new ServiceException("FastAPI返回的Agent草稿字段无效");
        }
        List<AiAgentTroubleshootResponse.ToolTrace> traces = response.getToolTrace();
        int position = -1;
        for (String requiredTool : REQUIRED_TOOLS)
        {
            position = nextSuccessfulTool(traces, requiredTool, position + 1);
            if (position < 0)
            {
                throw new ServiceException("FastAPI未完成规定的Agent工具调用顺序");
            }
        }
        List<AiRagAnswerResponse.Citation> citations = response.getCitations();
        if (Boolean.TRUE.equals(response.getAnswered())
            && (citations == null || citations.isEmpty()))
        {
            throw new ServiceException("FastAPI声称已回答但没有返回可核验引用");
        }
        if (!Boolean.TRUE.equals(response.getAnswered())
            && citations != null && !citations.isEmpty())
        {
            throw new ServiceException("FastAPI拒答响应不应包含引用");
        }
        if (!Boolean.TRUE.equals(response.getAnswered())
            && response.getTroubleshootingSteps() != null
            && !response.getTroubleshootingSteps().isEmpty())
        {
            throw new ServiceException("FastAPI拒答响应不应包含排障步骤");
        }
        if (Boolean.TRUE.equals(response.getAnswered()))
        {
            validateCitationScope(citations, request);
        }
    }

    private void validateCitationScope(
        List<AiRagAnswerResponse.Citation> citations,
        AiAgentTroubleshootRequest request)
    {
        for (AiRagAnswerResponse.Citation citation : citations)
        {
            if (citation == null
                || !request.getKnowledgeBaseId().equals(
                    citation.getKnowledgeBaseId())
                || !request.getDevice().getBrandId().equals(
                    citation.getBrandId())
                || !request.getDevice().getModelId().equals(
                    citation.getModelId())
                || citation.getDocumentId() == null
                || citation.getDocumentId() <= 0
                || citation.getDocumentVersionId() == null
                || !request.getDocumentVersionIds().contains(
                    citation.getDocumentVersionId()))
            {
                throw new ServiceException("FastAPI返回了当前设备知识范围之外的引用");
            }
        }
    }

    private int nextSuccessfulTool(
        List<AiAgentTroubleshootResponse.ToolTrace> traces,
        String toolName, int start)
    {
        if (traces == null)
        {
            return -1;
        }
        for (int index = start; index < traces.size(); index++)
        {
            AiAgentTroubleshootResponse.ToolTrace trace = traces.get(index);
            if (toolName.equals(trace.getToolName())
                && Boolean.TRUE.equals(trace.getSuccess()))
            {
                return index;
            }
        }
        return -1;
    }

    private void applyEditableFields(OcAgentDraft draft,
        OcAgentDraftEditRequest request, String username)
    {
        draft.setDraftTitle(StringUtils.trim(request.getTitle()));
        draft.setDraftFaultDescription(StringUtils.trim(
            request.getFaultDescription()));
        draft.setDraftPriority(request.getPriority());
        draft.setAssigneeUserId(request.getAssigneeUserId());
        draft.setWorkOrderRemark(StringUtils.trim(request.getWorkOrderRemark()));
        draft.setUpdateBy(username);
    }

    private OcAgentDraft requireOwnedDraft(Long draftId, Long userId)
    {
        OcAgentDraft draft = draftMapper.selectDraftByIdAndUserId(draftId, userId);
        if (draft == null)
        {
            throw new ServiceException("智能报修草稿不存在");
        }
        return draft;
    }

    private void requirePending(OcAgentDraft draft)
    {
        if (!AgentDraftConstants.STATUS_PENDING.equals(draft.getStatus()))
        {
            throw new ServiceException("只有待确认草稿可以执行此操作");
        }
    }

    private OcWorkOrder requireWorkOrder(Long workOrderId)
    {
        OcWorkOrder workOrder = workOrderService.selectWorkOrderById(workOrderId);
        if (workOrder == null)
        {
            throw new ServiceException("草稿关联的正式工单不存在");
        }
        return workOrder;
    }

    private String writeJson(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value == null
                ? List.of() : value);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("保存Agent审计结果失败");
        }
    }

    private OcAgentDraft hydrateJsonFields(OcAgentDraft draft)
    {
        draft.setTroubleshootingSteps(readJson(draft.getTroubleshootingStepsJson(),
            new TypeReference<List<String>>() { }));
        draft.setSafetyWarnings(readJson(draft.getSafetyWarningsJson(),
            new TypeReference<List<String>>() { }));
        draft.setCitations(readJson(draft.getCitationsJson(),
            new TypeReference<List<AiRagAnswerResponse.Citation>>() { }));
        draft.setToolTrace(readJson(draft.getToolTraceJson(),
            new TypeReference<List<AiAgentTroubleshootResponse.ToolTrace>>() { }));
        return draft;
    }

    private <T> List<T> readJson(String json,
        TypeReference<List<T>> typeReference)
    {
        if (StringUtils.isEmpty(json))
        {
            return new ArrayList<>();
        }
        try
        {
            return objectMapper.readValue(json, typeReference);
        }
        catch (JsonProcessingException exception)
        {
            throw new ServiceException("智能报修草稿的审计数据无法读取");
        }
    }
}
