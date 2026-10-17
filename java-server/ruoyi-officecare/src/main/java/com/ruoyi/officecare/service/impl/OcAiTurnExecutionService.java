package com.ruoyi.officecare.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.domain.OcAiTurnExecution;
import com.ruoyi.officecare.mapper.OcAiTurnExecutionMapper;

/**
 * AI助手单轮执行状态服务。
 *
 * 通过唯一索引和条件UPDATE保证同一会话的同一turnId只有一个执行者能调用Agent。
 * 状态流转：PROCESSING（推理中）-> READY（结果已保存）-> PERSISTING（落库中）
 * -> SUCCEEDED（完成）；推理失败进入FAILED，可由同一turnId重试。
 *
 * @author officecare
 */
@Service
public class OcAiTurnExecutionService
{
    private static final Logger log = LoggerFactory.getLogger(
        OcAiTurnExecutionService.class);

    public static final String STATUS_PROCESSING = "PROCESSING";

    public static final String STATUS_READY = "READY";

    public static final String STATUS_PERSISTING = "PERSISTING";

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    public static final String STATUS_FAILED = "FAILED";

    private final OcAiTurnExecutionMapper executionMapper;

    private final OfficeCareAiProperties aiProperties;

    public OcAiTurnExecutionService(
        OcAiTurnExecutionMapper executionMapper,
        OfficeCareAiProperties aiProperties)
    {
        this.executionMapper = executionMapper;
        this.aiProperties = aiProperties;
    }

    /**
     * 原子占用本轮执行权。
     *
     * 返回CLAIMED表示调用方负责执行Agent；FINISH表示Agent结果已存在，
     * 只需补落库；REPLAY表示已完成；BUSY表示其他执行者仍在处理；
     * CONFLICT表示同一turnId被用于不同问题。
     */
    public ClaimResult claim(
        Long conversationId,
        String turnId,
        Long userId,
        String questionHash,
        String ownerToken,
        String traceId)
    {
        OcAiTurnExecution existing = executionMapper
            .selectByConversationIdAndTurnId(conversationId, turnId);
        if (existing != null)
        {
            return resolveExisting(existing, questionHash, ownerToken, traceId);
        }

        OcAiTurnExecution execution = new OcAiTurnExecution();
        execution.setConversationId(conversationId);
        execution.setTurnId(turnId);
        execution.setUserId(userId);
        execution.setQuestionHash(questionHash);
        execution.setStatus(STATUS_PROCESSING);
        execution.setOwnerToken(ownerToken);
        execution.setAttemptCount(1);
        execution.setTraceId(traceId);
        try
        {
            executionMapper.insertExecution(execution, leaseSeconds());
            return ClaimResult.claimed(execution);
        }
        catch (DuplicateKeyException exception)
        {
            existing = executionMapper.selectByConversationIdAndTurnId(
                conversationId, turnId);
            if (existing == null)
            {
                throw new ServiceException("创建单轮执行状态失败");
            }
            return resolveExisting(existing, questionHash, ownerToken, traceId);
        }
    }

    public OcAiTurnExecution find(Long conversationId, String turnId)
    {
        return executionMapper.selectByConversationIdAndTurnId(
            conversationId, turnId);
    }

    public boolean markReady(
        Long executionId,
        String ownerToken,
        String resultPayload)
    {
        return executionMapper.markReady(
            executionId, ownerToken, resultPayload, leaseSeconds()) == 1;
    }

    public boolean beginPersist(
        Long conversationId,
        String turnId,
        String ownerToken)
    {
        return executionMapper.beginPersist(
            conversationId, turnId, ownerToken, leaseSeconds()) == 1;
    }

    public boolean takeOverPersisting(
        Long conversationId,
        String turnId,
        String ownerToken)
    {
        return executionMapper.takeOverPersisting(
            conversationId, turnId, ownerToken, leaseSeconds()) == 1;
    }

    /**
     * 推理失败时释放租约；记录失败本身不能再覆盖原始业务异常。
     */
    public void markFailed(
        Long executionId,
        String ownerToken,
        String errorCode,
        String errorMessage)
    {
        try
        {
            int updated = executionMapper.markFailed(
                executionId, ownerToken,
                truncate(errorCode, 64), truncate(errorMessage, 500));
            if (updated != 1)
            {
                log.warn(
                    "AI turn execution ownership changed before failure mark. executionId={}",
                    executionId);
            }
        }
        catch (RuntimeException exception)
        {
            log.error(
                "Failed to persist AI turn failure state. executionId={}",
                executionId, exception);
        }
    }

    /**
     * 结果快照损坏时终止本轮，避免等待者反复重试。
     */
    public void markPersistFailed(
        Long executionId,
        String ownerToken,
        String errorCode,
        String errorMessage)
    {
        try
        {
            int updated = executionMapper.markPersistFailed(
                executionId, ownerToken,
                truncate(errorCode, 64), truncate(errorMessage, 500));
            if (updated != 1)
            {
                log.warn(
                    "AI turn execution ownership changed before persist failure mark. executionId={}",
                    executionId);
            }
        }
        catch (RuntimeException exception)
        {
            log.error(
                "Failed to persist AI turn persist failure state. executionId={}",
                executionId, exception);
        }
    }

    /**
     * 落库事务失败后回到READY，让同一turnId的重试只补落库。
     */
    public boolean resetToReady(
        Long executionId,
        String ownerToken,
        String errorCode,
        String errorMessage)
    {
        try
        {
            return executionMapper.resetToReady(
                executionId, ownerToken,
                truncate(errorCode, 64), truncate(errorMessage, 500),
                leaseSeconds()) == 1;
        }
        catch (RuntimeException exception)
        {
            log.error(
                "Failed to reset AI turn execution to READY. executionId={}",
                executionId, exception);
            return false;
        }
    }

    private ClaimResult resolveExisting(
        OcAiTurnExecution existing,
        String questionHash,
        String ownerToken,
        String traceId)
    {
        if (!questionHash.equals(existing.getQuestionHash()))
        {
            return ClaimResult.conflict(existing);
        }

        String status = existing.getStatus();
        if (STATUS_SUCCEEDED.equals(status))
        {
            return ClaimResult.replay(existing);
        }
        if (STATUS_READY.equals(status))
        {
            if (beginPersist(
                existing.getConversationId(), existing.getTurnId(), ownerToken))
            {
                existing.setStatus(STATUS_PERSISTING);
                existing.setOwnerToken(ownerToken);
                return ClaimResult.finish(existing);
            }
            return ClaimResult.busy(existing);
        }
        if (STATUS_PERSISTING.equals(status))
        {
            if (takeOverPersisting(
                existing.getConversationId(), existing.getTurnId(), ownerToken))
            {
                existing.setOwnerToken(ownerToken);
                return ClaimResult.finish(existing);
            }
            return ClaimResult.busy(existing);
        }
        if (STATUS_PROCESSING.equals(status) || STATUS_FAILED.equals(status))
        {
            if (executionMapper.takeOver(
                existing.getConversationId(), existing.getTurnId(),
                questionHash, ownerToken, traceId, leaseSeconds()) == 1)
            {
                existing.setStatus(STATUS_PROCESSING);
                existing.setOwnerToken(ownerToken);
                existing.setTraceId(traceId);
                return ClaimResult.claimed(existing);
            }
            return ClaimResult.busy(existing);
        }

        log.warn(
            "Unknown AI turn execution status. conversationId={} turnId={} status={}",
            existing.getConversationId(), existing.getTurnId(), status);
        return ClaimResult.busy(existing);
    }

    private long leaseSeconds()
    {
        return Math.max(1L, aiProperties.getTurnLease().getSeconds());
    }

    private String truncate(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** 单轮占用结果。 */
    public static final class ClaimResult
    {
        public enum Type
        {
            CLAIMED,
            FINISH,
            REPLAY,
            BUSY,
            CONFLICT
        }

        private final Type type;

        private final OcAiTurnExecution execution;

        private ClaimResult(Type type, OcAiTurnExecution execution)
        {
            this.type = type;
            this.execution = execution;
        }

        public static ClaimResult claimed(OcAiTurnExecution execution)
        {
            return new ClaimResult(Type.CLAIMED, execution);
        }

        public static ClaimResult finish(OcAiTurnExecution execution)
        {
            return new ClaimResult(Type.FINISH, execution);
        }

        public static ClaimResult replay(OcAiTurnExecution execution)
        {
            return new ClaimResult(Type.REPLAY, execution);
        }

        public static ClaimResult busy(OcAiTurnExecution execution)
        {
            return new ClaimResult(Type.BUSY, execution);
        }

        public static ClaimResult conflict(OcAiTurnExecution execution)
        {
            return new ClaimResult(Type.CONFLICT, execution);
        }

        public Type getType()
        {
            return type;
        }

        public OcAiTurnExecution getExecution()
        {
            return execution;
        }
    }
}