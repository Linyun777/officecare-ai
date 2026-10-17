package com.ruoyi.officecare.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcWorkOrder;
import com.ruoyi.officecare.domain.vo.OcAssigneeOption;

/** OfficeCare售后工单数据访问层。 */
public interface OcWorkOrderMapper
{
    public List<OcWorkOrder> selectWorkOrderList(OcWorkOrder workOrder);

    public OcWorkOrder selectWorkOrderById(Long workOrderId);

    public OcWorkOrder selectWorkOrderByNo(String workOrderNo);

    public List<OcAssigneeOption> selectAssigneeOptions();

    public OcAssigneeOption selectAssigneeById(Long userId);

    public long selectPendingWorkOrderCount();

    public int countWorkOrderByDeviceId(Long deviceId);

    public int insertWorkOrder(OcWorkOrder workOrder);

    public int updateWorkOrder(OcWorkOrder workOrder);

    public int updateAssignee(@Param("workOrderId") Long workOrderId,
        @Param("assigneeUserId") Long assigneeUserId,
        @Param("updateBy") String updateBy);

    public int updateStatus(@Param("workOrderId") Long workOrderId,
        @Param("currentStatus") String currentStatus,
        @Param("targetStatus") String targetStatus,
        @Param("completionTime") Date completionTime,
        @Param("closeTime") Date closeTime,
        @Param("updateBy") String updateBy);

    public int deleteWorkOrderByIds(@Param("workOrderIds") Long[] workOrderIds,
        @Param("updateBy") String updateBy);
}
