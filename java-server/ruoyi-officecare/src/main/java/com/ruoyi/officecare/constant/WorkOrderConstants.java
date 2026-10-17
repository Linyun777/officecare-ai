package com.ruoyi.officecare.constant;

/**
 * OfficeCare工单状态和记录动作常量。
 *
 * @author officecare
 */
public final class WorkOrderConstants
{
    public static final String STATUS_PENDING = "0";

    public static final String STATUS_PROCESSING = "1";

    public static final String STATUS_COMPLETED = "2";

    public static final String STATUS_CLOSED = "3";

    public static final String ACTION_CREATE = "CREATE";

    public static final String ACTION_EDIT = "EDIT";

    public static final String ACTION_ASSIGN = "ASSIGN";

    public static final String ACTION_STATUS = "STATUS";

    public static final String ACTION_COMMENT = "COMMENT";

    private WorkOrderConstants()
    {
    }
}
