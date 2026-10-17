package com.ruoyi.officecare.support;

import java.util.Map;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.constant.WorkOrderConstants;

/**
 * 最小工单状态机，集中约束允许的状态流转。
 *
 * @author officecare
 */
public final class OcWorkOrderStatusPolicy
{
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        WorkOrderConstants.STATUS_PENDING,
        Set.of(WorkOrderConstants.STATUS_PROCESSING,
            WorkOrderConstants.STATUS_CLOSED),
        WorkOrderConstants.STATUS_PROCESSING,
        Set.of(WorkOrderConstants.STATUS_COMPLETED,
            WorkOrderConstants.STATUS_CLOSED),
        WorkOrderConstants.STATUS_COMPLETED,
        Set.of(WorkOrderConstants.STATUS_PROCESSING,
            WorkOrderConstants.STATUS_CLOSED),
        WorkOrderConstants.STATUS_CLOSED,
        Set.of());

    private OcWorkOrderStatusPolicy()
    {
    }

    public static boolean canTransition(String currentStatus, String targetStatus)
    {
        return TRANSITIONS.getOrDefault(currentStatus, Set.of())
            .contains(targetStatus);
    }

    public static void validate(String currentStatus, String targetStatus)
    {
        if (!canTransition(currentStatus, targetStatus))
        {
            throw new ServiceException("不允许从“" + statusName(currentStatus)
                + "”流转到“" + statusName(targetStatus) + "”");
        }
    }

    public static String statusName(String status)
    {
        if (WorkOrderConstants.STATUS_PENDING.equals(status))
        {
            return "待处理";
        }
        if (WorkOrderConstants.STATUS_PROCESSING.equals(status))
        {
            return "处理中";
        }
        if (WorkOrderConstants.STATUS_COMPLETED.equals(status))
        {
            return "已完成";
        }
        if (WorkOrderConstants.STATUS_CLOSED.equals(status))
        {
            return "已关闭";
        }
        return "未知状态";
    }
}
