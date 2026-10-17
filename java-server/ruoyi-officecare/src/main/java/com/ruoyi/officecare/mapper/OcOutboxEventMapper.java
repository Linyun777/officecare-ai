package com.ruoyi.officecare.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcOutboxEvent;
import com.ruoyi.officecare.domain.vo.OfficeCareMqDeadEvent;
import com.ruoyi.officecare.domain.vo.OfficeCareMqMetrics;

/**
 * OfficeCare Transactional Outbox数据访问层。
 *
 * @author officecare
 */
public interface OcOutboxEventMapper
{
    public int insertOutboxEvent(OcOutboxEvent event);

    public int reactivateDeadEvent(OcOutboxEvent event);

    public List<OcOutboxEvent> selectReadyEvents(@Param("limit") int limit);

    public int claimEvent(@Param("outboxId") Long outboxId);

    public int markSent(@Param("outboxId") Long outboxId);

    public int markRetry(@Param("outboxId") Long outboxId,
        @Param("retryCount") int retryCount,
        @Param("nextRetryTime") Date nextRetryTime,
        @Param("lastError") String lastError);

    public int markDead(@Param("outboxId") Long outboxId,
        @Param("retryCount") int retryCount,
        @Param("lastError") String lastError);

    public int recoverStalePublishing(@Param("cutoffTime") Date cutoffTime);

    public OcOutboxEvent selectOutboxEventById(@Param("outboxId") Long outboxId);

    public int markConsumeStarted(@Param("eventId") String eventId,
        @Param("redelivered") boolean redelivered);

    public int markConsumeSuccess(@Param("eventId") String eventId);

    public int markConsumeRetry(@Param("eventId") String eventId,
        @Param("consumeError") String consumeError);

    public int markConsumeDead(@Param("eventId") String eventId,
        @Param("consumeError") String consumeError);

    public int markConsumeIgnored(@Param("eventId") String eventId,
        @Param("consumeError") String consumeError);

    public int markDeadLetterReceived(@Param("eventId") String eventId);

    public OfficeCareMqMetrics selectMqMetrics();

    public List<OfficeCareMqDeadEvent> selectRecentDeadEvents(
        @Param("limit") int limit);

    public int reactivatePublishDead(@Param("outboxId") Long outboxId,
        @Param("replayedBy") String replayedBy);

    public int markConsumerDeadReplayed(@Param("outboxId") Long outboxId,
        @Param("replayedBy") String replayedBy);
}
