package com.ruoyi.officecare.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.constant.WorkOrderConstants;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.OcWorkOrder;
import com.ruoyi.officecare.domain.OcWorkOrderRecord;
import com.ruoyi.officecare.domain.dto.OcAiQuestionRequest;
import com.ruoyi.officecare.domain.dto.OcWorkOrderAssigneeRequest;
import com.ruoyi.officecare.domain.dto.OcWorkOrderRecordRequest;
import com.ruoyi.officecare.domain.dto.OcWorkOrderStatusRequest;
import com.ruoyi.officecare.domain.vo.OcAssigneeOption;
import com.ruoyi.officecare.domain.vo.OcWorkOrderAiSuggestion;
import com.ruoyi.officecare.mapper.OcCustomerOrgMapper;
import com.ruoyi.officecare.mapper.OcDeviceMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.mapper.OcWorkOrderMapper;
import com.ruoyi.officecare.mapper.OcWorkOrderRecordMapper;
import com.ruoyi.officecare.service.IOcAiAssistantService;
import com.ruoyi.officecare.service.IOcWorkOrderService;
import com.ruoyi.officecare.support.OcWorkOrderStatusPolicy;

/**
 * OfficeCare售后工单服务实现。
 *
 * 工单业务保持为轻量四状态闭环，不引入审批流或复杂数据权限。
 */
@Service
public class OcWorkOrderServiceImpl implements IOcWorkOrderService
{
    private static final String NORMAL_STATUS = "0";

    private static final DateTimeFormatter NUMBER_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OcWorkOrderMapper workOrderMapper;

    private final OcWorkOrderRecordMapper recordMapper;

    private final OcCustomerOrgMapper customerOrgMapper;

    private final OcDeviceMapper deviceMapper;

    private final OcKnowledgeBaseMapper knowledgeBaseMapper;

    private final IOcAiAssistantService aiAssistantService;

    public OcWorkOrderServiceImpl(
        OcWorkOrderMapper workOrderMapper,
        OcWorkOrderRecordMapper recordMapper,
        OcCustomerOrgMapper customerOrgMapper,
        OcDeviceMapper deviceMapper,
        OcKnowledgeBaseMapper knowledgeBaseMapper,
        IOcAiAssistantService aiAssistantService)
    {
        this.workOrderMapper = workOrderMapper;
        this.recordMapper = recordMapper;
        this.customerOrgMapper = customerOrgMapper;
        this.deviceMapper = deviceMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.aiAssistantService = aiAssistantService;
    }

    @Override
    public List<OcWorkOrder> selectWorkOrderList(OcWorkOrder workOrder)
    {
        return workOrderMapper.selectWorkOrderList(workOrder);
    }

    @Override
    public OcWorkOrder selectWorkOrderById(Long workOrderId)
    {
        OcWorkOrder workOrder = requireWorkOrder(workOrderId);
        workOrder.setRecords(
            recordMapper.selectRecordListByWorkOrderId(workOrderId));
        return workOrder;
    }

    @Override
    public List<OcAssigneeOption> selectAssigneeOptions()
    {
        return workOrderMapper.selectAssigneeOptions();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertWorkOrder(OcWorkOrder workOrder, Long userId,
        String username)
    {
        normalizeWorkOrder(workOrder);
        validateReferences(workOrder, true);
        workOrder.setWorkOrderNo(createWorkOrderNo());
        workOrder.setStatus(WorkOrderConstants.STATUS_PENDING);
        workOrder.setReporterUserId(userId);
        workOrder.setCreateBy(username);
        int rows = workOrderMapper.insertWorkOrder(workOrder);
        if (rows != 1)
        {
            throw new ServiceException("创建工单失败");
        }

        insertRecord(workOrder.getWorkOrderId(), WorkOrderConstants.ACTION_CREATE,
            null, WorkOrderConstants.STATUS_PENDING,
            "创建工单：" + workOrder.getTitle(), userId, username);
        if (workOrder.getAssigneeUserId() != null)
        {
            OcAssigneeOption assignee = requireAssignee(
                workOrder.getAssigneeUserId());
            insertRecord(workOrder.getWorkOrderId(),
                WorkOrderConstants.ACTION_ASSIGN, null, null,
                "指派负责人：" + displayName(assignee), userId, username);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateWorkOrder(OcWorkOrder workOrder, Long userId,
        String username)
    {
        if (workOrder.getWorkOrderId() == null)
        {
            throw new ServiceException("工单ID不能为空");
        }
        OcWorkOrder existing = requireWorkOrder(workOrder.getWorkOrderId());
        if (!WorkOrderConstants.STATUS_PENDING.equals(existing.getStatus()))
        {
            throw new ServiceException("只有待处理工单可以修改基础信息");
        }

        normalizeWorkOrder(workOrder);
        validateReferences(workOrder, true);
        workOrder.setUpdateBy(username);
        int rows = workOrderMapper.updateWorkOrder(workOrder);
        if (rows != 1)
        {
            throw new ServiceException("工单已被其他操作修改，请刷新后重试");
        }
        insertRecord(workOrder.getWorkOrderId(), WorkOrderConstants.ACTION_EDIT,
            existing.getStatus(), existing.getStatus(), "修改工单基础信息",
            userId, username);
        if (!Objects.equals(existing.getAssigneeUserId(),
            workOrder.getAssigneeUserId()))
        {
            insertRecord(workOrder.getWorkOrderId(),
                WorkOrderConstants.ACTION_ASSIGN, null, null,
                buildAssignmentContent(existing.getAssigneeUserId(),
                    workOrder.getAssigneeUserId()), userId, username);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignWorkOrder(Long workOrderId,
        OcWorkOrderAssigneeRequest request, Long userId, String username)
    {
        OcWorkOrder existing = requireWorkOrder(workOrderId);
        if (WorkOrderConstants.STATUS_CLOSED.equals(existing.getStatus()))
        {
            throw new ServiceException("已关闭工单不能调整负责人");
        }
        if (request.getAssigneeUserId() != null)
        {
            requireAssignee(request.getAssigneeUserId());
        }
        if (Objects.equals(existing.getAssigneeUserId(),
            request.getAssigneeUserId()))
        {
            throw new ServiceException("工单负责人没有变化");
        }

        int rows = workOrderMapper.updateAssignee(workOrderId,
            request.getAssigneeUserId(), username);
        if (rows != 1)
        {
            throw new ServiceException("负责人调整失败，请刷新后重试");
        }
        String content = StringUtils.trim(request.getContent());
        String assignment = buildAssignmentContent(existing.getAssigneeUserId(),
            request.getAssigneeUserId());
        if (StringUtils.isNotEmpty(content))
        {
            assignment += "；说明：" + content;
        }
        insertRecord(workOrderId, WorkOrderConstants.ACTION_ASSIGN,
            null, null, assignment, userId, username);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int transitionStatus(Long workOrderId,
        OcWorkOrderStatusRequest request, Long userId, String username)
    {
        OcWorkOrder existing = requireWorkOrder(workOrderId);
        String targetStatus = request.getTargetStatus();
        OcWorkOrderStatusPolicy.validate(existing.getStatus(), targetStatus);

        if (WorkOrderConstants.STATUS_PROCESSING.equals(targetStatus)
            && existing.getAssigneeUserId() == null)
        {
            requireAssignee(userId);
            if (workOrderMapper.updateAssignee(workOrderId, userId, username) != 1)
            {
                throw new ServiceException("自动领取工单失败，请刷新后重试");
            }
            insertRecord(workOrderId, WorkOrderConstants.ACTION_ASSIGN,
                null, null, "开始处理时自动领取工单", userId, username);
        }

        Date completionTime = existing.getCompletionTime();
        Date closeTime = existing.getCloseTime();
        if (WorkOrderConstants.STATUS_PROCESSING.equals(targetStatus))
        {
            completionTime = null;
            closeTime = null;
        }
        else if (WorkOrderConstants.STATUS_COMPLETED.equals(targetStatus))
        {
            completionTime = new Date();
            closeTime = null;
        }
        else if (WorkOrderConstants.STATUS_CLOSED.equals(targetStatus))
        {
            closeTime = new Date();
        }

        int rows = workOrderMapper.updateStatus(workOrderId,
            existing.getStatus(), targetStatus, completionTime, closeTime,
            username);
        if (rows != 1)
        {
            throw new ServiceException("工单状态已发生变化，请刷新后重试");
        }
        insertRecord(workOrderId, WorkOrderConstants.ACTION_STATUS,
            existing.getStatus(), targetStatus,
            StringUtils.trim(request.getContent()), userId, username);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addHandlingRecord(Long workOrderId,
        OcWorkOrderRecordRequest request, Long userId, String username)
    {
        OcWorkOrder existing = requireWorkOrder(workOrderId);
        if (WorkOrderConstants.STATUS_CLOSED.equals(existing.getStatus()))
        {
            throw new ServiceException("已关闭工单不能继续添加处理记录");
        }
        return insertRecord(workOrderId, WorkOrderConstants.ACTION_COMMENT,
            existing.getStatus(), existing.getStatus(),
            StringUtils.trim(request.getContent()), userId, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWorkOrderByIds(Long[] workOrderIds, String username)
    {
        for (Long workOrderId : workOrderIds)
        {
            OcWorkOrder existing = requireWorkOrder(workOrderId);
            if (!WorkOrderConstants.STATUS_PENDING.equals(existing.getStatus()))
            {
                throw new ServiceException("只有待处理工单可以删除："
                    + existing.getWorkOrderNo());
            }
        }
        int rows = workOrderMapper.deleteWorkOrderByIds(workOrderIds, username);
        if (rows != workOrderIds.length)
        {
            throw new ServiceException("部分工单状态已变化，请刷新后重试");
        }
        return rows;
    }

    @Override
    public OcWorkOrderAiSuggestion generateAiSuggestion(Long workOrderId)
    {
        OcWorkOrder workOrder = requireWorkOrder(workOrderId);
        OcKnowledgeBase knowledgeBase =
            knowledgeBaseMapper.selectActiveKnowledgeBaseByModelId(
                workOrder.getModelId());
        if (knowledgeBase == null)
        {
            throw new ServiceException("当前设备型号没有可用知识库，无法生成AI建议");
        }

        OcAiQuestionRequest questionRequest = new OcAiQuestionRequest();
        questionRequest.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        questionRequest.setQuestion(buildAiQuestion(workOrder));

        OcWorkOrderAiSuggestion suggestion = new OcWorkOrderAiSuggestion();
        suggestion.setWorkOrderId(workOrderId);
        suggestion.setWorkOrderNo(workOrder.getWorkOrderNo());
        suggestion.setKnowledgeBaseId(knowledgeBase.getKnowledgeBaseId());
        suggestion.setKnowledgeBaseName(knowledgeBase.getKnowledgeBaseName());
        suggestion.setBrandName(workOrder.getBrandName());
        suggestion.setModelName(workOrder.getModelName());
        suggestion.setAnswer(aiAssistantService.answer(questionRequest));
        return suggestion;
    }

    private void normalizeWorkOrder(OcWorkOrder workOrder)
    {
        workOrder.setTitle(StringUtils.trim(workOrder.getTitle()));
        workOrder.setFaultDescription(
            StringUtils.trim(workOrder.getFaultDescription()));
        workOrder.setRemark(StringUtils.trim(workOrder.getRemark()));
        if (StringUtils.isEmpty(workOrder.getPriority()))
        {
            workOrder.setPriority("1");
        }
    }

    private void validateReferences(OcWorkOrder workOrder, boolean requireActive)
    {
        OcCustomerOrg customer = customerOrgMapper.selectCustomerOrgById(
            workOrder.getCustomerOrgId());
        if (customer == null)
        {
            throw new ServiceException("客户单位不存在或已被删除");
        }
        if (requireActive && !NORMAL_STATUS.equals(customer.getStatus()))
        {
            throw new ServiceException("客户单位已停用，不能创建或修改工单");
        }

        OcDevice device = deviceMapper.selectDeviceById(workOrder.getDeviceId());
        if (device == null)
        {
            throw new ServiceException("设备档案不存在或已被删除");
        }
        if (!Objects.equals(device.getCustomerOrgId(),
            workOrder.getCustomerOrgId()))
        {
            throw new ServiceException("所选设备不属于当前客户单位");
        }
        if (requireActive && !NORMAL_STATUS.equals(device.getStatus()))
        {
            throw new ServiceException("设备已停用，不能创建或修改工单");
        }
        if (workOrder.getAssigneeUserId() != null)
        {
            requireAssignee(workOrder.getAssigneeUserId());
        }
    }

    private OcWorkOrder requireWorkOrder(Long workOrderId)
    {
        OcWorkOrder workOrder = workOrderMapper.selectWorkOrderById(workOrderId);
        if (workOrder == null)
        {
            throw new ServiceException("工单不存在或已被删除");
        }
        return workOrder;
    }

    private OcAssigneeOption requireAssignee(Long userId)
    {
        OcAssigneeOption assignee = workOrderMapper.selectAssigneeById(userId);
        if (assignee == null)
        {
            throw new ServiceException("负责人不是正常状态的售后人员");
        }
        return assignee;
    }

    private int insertRecord(Long workOrderId, String actionType,
        String fromStatus, String toStatus, String content,
        Long userId, String username)
    {
        OcWorkOrderRecord record = new OcWorkOrderRecord();
        record.setWorkOrderId(workOrderId);
        record.setActionType(actionType);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setContent(content);
        record.setOperatorUserId(userId);
        record.setOperatorName(username);
        if (recordMapper.insertWorkOrderRecord(record) != 1)
        {
            throw new ServiceException("保存工单处理记录失败");
        }
        return 1;
    }

    private String createWorkOrderNo()
    {
        for (int attempt = 0; attempt < 3; attempt++)
        {
            String suffix = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 6).toUpperCase();
            String workOrderNo = "WO"
                + LocalDateTime.now().format(NUMBER_TIME_FORMAT) + suffix;
            if (workOrderMapper.selectWorkOrderByNo(workOrderNo) == null)
            {
                return workOrderNo;
            }
        }
        throw new ServiceException("生成工单编号失败，请重试");
    }

    private String buildAssignmentContent(Long oldAssigneeId,
        Long newAssigneeId)
    {
        String oldName = assigneeName(oldAssigneeId, "未指派");
        String newName = assigneeName(newAssigneeId, "未指派");
        return "负责人由“" + oldName + "”调整为“" + newName + "”";
    }

    private String assigneeName(Long userId, String emptyName)
    {
        if (userId == null)
        {
            return emptyName;
        }
        OcAssigneeOption assignee = workOrderMapper.selectAssigneeById(userId);
        return assignee == null ? "用户" + userId : displayName(assignee);
    }

    private String displayName(OcAssigneeOption assignee)
    {
        return StringUtils.isNotEmpty(assignee.getNickName())
            ? assignee.getNickName() + "（" + assignee.getUserName() + "）"
            : assignee.getUserName();
    }

    static String buildAiQuestion(OcWorkOrder workOrder)
    {
        // 品牌、型号、知识库和文档版本已由Java查询并作为独立条件过滤。
        // 这里只保留原始故障描述，避免固定提示、型号和标题稀释错误码、
        // 尺寸、耗材型号等精确检索词。
        String question = StringUtils.trim(workOrder.getFaultDescription());
        return question.length() <= 500 ? question : question.substring(0, 500);
    }
}
