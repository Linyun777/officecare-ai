package com.ruoyi.officecare.constant;

/**
 * Transactional Outbox状态常量。
 *
 * @author officecare
 */
public final class OutboxConstants
{
    public static final String STATUS_PENDING = "0";

    public static final String STATUS_PUBLISHING = "1";

    public static final String STATUS_SENT = "2";

    public static final String STATUS_RETRY = "3";

    public static final String STATUS_DEAD = "4";

    private OutboxConstants()
    {
    }
}
