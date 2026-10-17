package com.ruoyi.officecare.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcAiTurnExecution;

/**
 * AI助手单轮执行状态数据访问层。
 *
 * 所有状态迁移都使用带条件的单条UPDATE，保证多实例并发下只有一个执行者获胜。
 *
 * @author officecare
 */
public interface OcAiTurnExecutionMapper
{
    /** 查询指定会话和turnId的执行状态。 */
    public OcAiTurnExecution selectByConversationIdAndTurnId(
        @Param("conversationId") Long conversationId,
        @Param("turnId") String turnId);

    /** 新turn首次占用，唯一索引冲突说明已有其他执行者。 */
    public int insertExecution(
        @Param("execution") OcAiTurnExecution execution,
        @Param("leaseSeconds") long leaseSeconds);

    /** 接管已失败或租约过期的PROCESSING执行。 */
    public int takeOver(
        @Param("conversationId") Long conversationId,
        @Param("turnId") String turnId,
        @Param("questionHash") String questionHash,
        @Param("ownerToken") String ownerToken,
        @Param("traceId") String traceId,
        @Param("leaseSeconds") long leaseSeconds);

    /** 接管租约过期的PERSISTING执行，只续做落库，不重新调用Agent。 */
    public int takeOverPersisting(
        @Param("conversationId") Long conversationId,
        @Param("turnId") String turnId,
        @Param("ownerToken") String ownerToken,
        @Param("leaseSeconds") long leaseSeconds);

    /** 从READY原子进入PERSISTING，只有一个请求能获得落库权。 */
    public int beginPersist(
        @Param("conversationId") Long conversationId,
        @Param("turnId") String turnId,
        @Param("ownerToken") String ownerToken,
        @Param("leaseSeconds") long leaseSeconds);

    /** Agent成功但消息尚未落库时保存结果快照。 */
    public int markReady(
        @Param("executionId") Long executionId,
        @Param("ownerToken") String ownerToken,
        @Param("resultPayload") String resultPayload,
        @Param("leaseSeconds") long leaseSeconds);

    /** 消息、引用和会话状态在同一事务内落库后标记完成。 */
    public int markSucceeded(
        @Param("executionId") Long executionId,
        @Param("ownerToken") String ownerToken,
        @Param("userMessageId") Long userMessageId,
        @Param("assistantMessageId") Long assistantMessageId);

    /** Agent调用失败，释放租约以便同一turnId重试。 */
    public int markFailed(
        @Param("executionId") Long executionId,
        @Param("ownerToken") String ownerToken,
        @Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage);

    /** Agent结果快照损坏时终止本轮，避免等待者反复重试。 */
    public int markPersistFailed(
        @Param("executionId") Long executionId,
        @Param("ownerToken") String ownerToken,
        @Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage);

    /** 落库事务失败后回到READY，重试时只补落库，不重复推理。 */
    public int resetToReady(
        @Param("executionId") Long executionId,
        @Param("ownerToken") String ownerToken,
        @Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage,
        @Param("leaseSeconds") long leaseSeconds);
}