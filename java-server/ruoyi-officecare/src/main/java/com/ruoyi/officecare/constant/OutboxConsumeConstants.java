package com.ruoyi.officecare.constant;

/** Transactional Outbox消息消费状态。 */
public final class OutboxConsumeConstants
{
    public static final String STATUS_PENDING = "0";

    public static final String STATUS_RUNNING = "1";

    public static final String STATUS_SUCCESS = "2";

    public static final String STATUS_RETRY_SCHEDULED = "3";

    public static final String STATUS_DEAD = "4";

    public static final String STATUS_IGNORED = "5";

    public static final String STATUS_REPLAYED = "6";

    private OutboxConsumeConstants()
    {
    }
}
