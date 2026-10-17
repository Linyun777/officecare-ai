package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcWorkOrder;
import com.ruoyi.officecare.domain.dto.OcWorkOrderAssigneeRequest;
import com.ruoyi.officecare.domain.dto.OcWorkOrderRecordRequest;
import com.ruoyi.officecare.domain.dto.OcWorkOrderStatusRequest;
import com.ruoyi.officecare.domain.vo.OcAssigneeOption;
import com.ruoyi.officecare.domain.vo.OcWorkOrderAiSuggestion;

/** OfficeCare售后工单服务。 */
public interface IOcWorkOrderService
{
    public List<OcWorkOrder> selectWorkOrderList(OcWorkOrder workOrder);

    public OcWorkOrder selectWorkOrderById(Long workOrderId);

    public List<OcAssigneeOption> selectAssigneeOptions();

    public int insertWorkOrder(OcWorkOrder workOrder, Long userId,
        String username);

    public int updateWorkOrder(OcWorkOrder workOrder, Long userId,
        String username);

    public int assignWorkOrder(Long workOrderId,
        OcWorkOrderAssigneeRequest request, Long userId, String username);

    public int transitionStatus(Long workOrderId,
        OcWorkOrderStatusRequest request, Long userId, String username);

    public int addHandlingRecord(Long workOrderId,
        OcWorkOrderRecordRequest request, Long userId, String username);

    public int deleteWorkOrderByIds(Long[] workOrderIds, String username);

    public OcWorkOrderAiSuggestion generateAiSuggestion(Long workOrderId);
}
