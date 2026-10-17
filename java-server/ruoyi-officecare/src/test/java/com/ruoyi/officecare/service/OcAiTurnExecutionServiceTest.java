package com.ruoyi.officecare.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.ruoyi.officecare.config.OfficeCareAiProperties;
import com.ruoyi.officecare.domain.OcAiTurnExecution;
import com.ruoyi.officecare.mapper.OcAiTurnExecutionMapper;
import com.ruoyi.officecare.service.impl.OcAiTurnExecutionService;
import com.ruoyi.officecare.service.impl.OcAiTurnExecutionService.ClaimResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 单轮执行状态机的并发边界测试。
 *
 * 真实原子性由MySQL唯一索引和条件UPDATE保证；这里验证Java状态流转。
 *
 * @author officecare
 */
class OcAiTurnExecutionServiceTest
{
    private static final Long CONVERSATION_ID = 15L;

    private static final String TURN_ID =
        "e1feae17-65b9-46ad-b3dd-356676686a41";

    private static final String QUESTION_HASH = "hash-question-a";

    @Test
    void shouldClaimNewTurnOnlyOnce()
    {
        Fixture fixture = new Fixture();

        ClaimResult first = fixture.claim("owner-1", QUESTION_HASH);
        ClaimResult second = fixture.claim("owner-2", QUESTION_HASH);

        assertEquals(ClaimResult.Type.CLAIMED, first.getType());
        assertEquals(ClaimResult.Type.BUSY, second.getType());
        assertEquals(1, fixture.mapper.row.getAttemptCount());
        assertEquals("owner-1", fixture.mapper.row.getOwnerToken());
    }

    @Test
    void shouldConflictWhenTurnIdReusedForDifferentQuestion()
    {
        Fixture fixture = new Fixture();
        fixture.claim("owner-1", QUESTION_HASH);

        ClaimResult conflict = fixture.claim("owner-2", "hash-question-b");

        assertEquals(ClaimResult.Type.CONFLICT, conflict.getType());
        assertEquals(QUESTION_HASH, fixture.mapper.row.getQuestionHash());
    }

    @Test
    void shouldReplaySucceededTurn()
    {
        Fixture fixture = new Fixture();
        fixture.claim("owner-1", QUESTION_HASH);
        fixture.mapper.row.setStatus(OcAiTurnExecutionService.STATUS_SUCCEEDED);
        fixture.mapper.row.setResultPayload("{\"answer\":\"done\"}");
        fixture.mapper.row.setAssistantMessageId(102L);
        fixture.mapper.row.setUserMessageId(101L);

        ClaimResult replay = fixture.claim("owner-2", QUESTION_HASH);

        assertEquals(ClaimResult.Type.REPLAY, replay.getType());
        assertEquals(102L, replay.getExecution().getAssistantMessageId());
    }

    @Test
    void shouldFinishReadyTurnWithoutCallingAgentAgain()
    {
        Fixture fixture = new Fixture();
        fixture.claim("owner-1", QUESTION_HASH);
        fixture.mapper.row.setStatus(OcAiTurnExecutionService.STATUS_READY);
        fixture.mapper.row.setResultPayload("{\"answer\":\"ready\"}");

        ClaimResult finish = fixture.claim("owner-2", QUESTION_HASH);

        assertEquals(ClaimResult.Type.FINISH, finish.getType());
        assertEquals(OcAiTurnExecutionService.STATUS_PERSISTING,
            fixture.mapper.row.getStatus());
        assertEquals("owner-2", fixture.mapper.row.getOwnerToken());
    }

    @Test
    void shouldTakeOverExpiredProcessingTurn()
    {
        Fixture fixture = new Fixture();
        fixture.claim("owner-1", QUESTION_HASH);
        fixture.mapper.row.setLeaseUntil(pastDate());

        ClaimResult takeover = fixture.claim("owner-2", QUESTION_HASH);

        assertEquals(ClaimResult.Type.CLAIMED, takeover.getType());
        assertEquals(2, fixture.mapper.row.getAttemptCount());
        assertEquals("owner-2", fixture.mapper.row.getOwnerToken());
    }

    @Test
    void shouldNotTakeOverActiveProcessingTurn()
    {
        Fixture fixture = new Fixture();
        fixture.claim("owner-1", QUESTION_HASH);
        fixture.mapper.row.setLeaseUntil(futureDate());

        ClaimResult busy = fixture.claim("owner-2", QUESTION_HASH);

        assertEquals(ClaimResult.Type.BUSY, busy.getType());
        assertEquals("owner-1", fixture.mapper.row.getOwnerToken());
    }

    @Test
    void shouldResumeExpiredPersistingTurnWithoutReclaimingAgent()
    {
        Fixture fixture = new Fixture();
        fixture.claim("owner-1", QUESTION_HASH);
        fixture.mapper.row.setStatus(OcAiTurnExecutionService.STATUS_PERSISTING);
        fixture.mapper.row.setResultPayload("{\"answer\":\"ready\"}");
        fixture.mapper.row.setLeaseUntil(pastDate());

        ClaimResult finish = fixture.claim("owner-2", QUESTION_HASH);

        assertEquals(ClaimResult.Type.FINISH, finish.getType());
        assertEquals(OcAiTurnExecutionService.STATUS_PERSISTING,
            fixture.mapper.row.getStatus());
        assertEquals("owner-2", fixture.mapper.row.getOwnerToken());
    }

    @Test
    void shouldRetryFailedTurnWithSameQuestion()
    {
        Fixture fixture = new Fixture();
        fixture.claim("owner-1", QUESTION_HASH);
        fixture.mapper.row.setStatus(OcAiTurnExecutionService.STATUS_FAILED);

        ClaimResult retry = fixture.claim("owner-2", QUESTION_HASH);

        assertEquals(ClaimResult.Type.CLAIMED, retry.getType());
        assertEquals(2, fixture.mapper.row.getAttemptCount());
    }

    private static Date pastDate()
    {
        return new Date(System.currentTimeMillis() - 60_000L);
    }

    private static Date futureDate()
    {
        return new Date(System.currentTimeMillis() + 60_000L);
    }

    private static class Fixture
    {
        private final FakeExecutionMapper mapper = new FakeExecutionMapper();

        private final OcAiTurnExecutionService service;

        Fixture()
        {
            OfficeCareAiProperties properties = new OfficeCareAiProperties();
            properties.setTurnLease(java.time.Duration.ofMinutes(6));
            service = new OcAiTurnExecutionService(mapper, properties);
        }

        ClaimResult claim(String ownerToken, String questionHash)
        {
            return service.claim(
                CONVERSATION_ID, TURN_ID, 7L, questionHash,
                ownerToken, "assistant-1-" + ownerToken);
        }
    }

    private static class FakeExecutionMapper
        implements OcAiTurnExecutionMapper
    {
        private final Map<String, OcAiTurnExecution> rows = new HashMap<>();

        private OcAiTurnExecution row;

        private long nextId = 200L;

        @Override
        public synchronized OcAiTurnExecution selectByConversationIdAndTurnId(
            Long conversationId,
            String turnId)
        {
            return copy(row);
        }

        @Override
        public synchronized int insertExecution(
            OcAiTurnExecution execution,
            long leaseSeconds)
        {
            String key = execution.getConversationId() + ":" + execution.getTurnId();
            if (rows.containsKey(key))
            {
                throw new DuplicateKeyException("duplicate turn execution");
            }
            execution.setExecutionId(nextId++);
            execution.setLeaseUntil(leaseDate(leaseSeconds));
            row = copy(execution);
            rows.put(key, row);
            return 1;
        }

        @Override
        public synchronized int takeOver(
            Long conversationId,
            String turnId,
            String questionHash,
            String ownerToken,
            String traceId,
            long leaseSeconds)
        {
            if (row == null || !questionHash.equals(row.getQuestionHash()))
            {
                return 0;
            }
            boolean retryable = OcAiTurnExecutionService.STATUS_FAILED.equals(
                row.getStatus())
                || (OcAiTurnExecutionService.STATUS_PROCESSING.equals(
                    row.getStatus()) && isExpired(row));
            if (!retryable)
            {
                return 0;
            }
            row.setStatus(OcAiTurnExecutionService.STATUS_PROCESSING);
            row.setOwnerToken(ownerToken);
            row.setTraceId(traceId);
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setLeaseUntil(leaseDate(leaseSeconds));
            return 1;
        }

        @Override
        public synchronized int takeOverPersisting(
            Long conversationId,
            String turnId,
            String ownerToken,
            long leaseSeconds)
        {
            if (row == null
                || !OcAiTurnExecutionService.STATUS_PERSISTING.equals(
                    row.getStatus())
                || !isExpired(row))
            {
                return 0;
            }
            row.setOwnerToken(ownerToken);
            row.setAttemptCount(row.getAttemptCount() + 1);
            row.setLeaseUntil(leaseDate(leaseSeconds));
            return 1;
        }

        @Override
        public synchronized int beginPersist(
            Long conversationId,
            String turnId,
            String ownerToken,
            long leaseSeconds)
        {
            if (row == null
                || !OcAiTurnExecutionService.STATUS_READY.equals(row.getStatus()))
            {
                return 0;
            }
            row.setStatus(OcAiTurnExecutionService.STATUS_PERSISTING);
            row.setOwnerToken(ownerToken);
            row.setLeaseUntil(leaseDate(leaseSeconds));
            return 1;
        }

        @Override
        public synchronized int markReady(
            Long executionId,
            String ownerToken,
            String resultPayload,
            long leaseSeconds)
        {
            if (!owns(executionId, ownerToken)
                || !OcAiTurnExecutionService.STATUS_PROCESSING.equals(
                    row.getStatus()))
            {
                return 0;
            }
            row.setStatus(OcAiTurnExecutionService.STATUS_READY);
            row.setResultPayload(resultPayload);
            row.setLeaseUntil(leaseDate(leaseSeconds));
            return 1;
        }

        @Override
        public synchronized int markSucceeded(
            Long executionId,
            String ownerToken,
            Long userMessageId,
            Long assistantMessageId)
        {
            if (!owns(executionId, ownerToken)
                || !OcAiTurnExecutionService.STATUS_PERSISTING.equals(
                    row.getStatus()))
            {
                return 0;
            }
            row.setStatus(OcAiTurnExecutionService.STATUS_SUCCEEDED);
            row.setUserMessageId(userMessageId);
            row.setAssistantMessageId(assistantMessageId);
            return 1;
        }

        @Override
        public synchronized int markFailed(
            Long executionId,
            String ownerToken,
            String errorCode,
            String errorMessage)
        {
            if (!owns(executionId, ownerToken)
                || !OcAiTurnExecutionService.STATUS_PROCESSING.equals(
                    row.getStatus()))
            {
                return 0;
            }
            row.setStatus(OcAiTurnExecutionService.STATUS_FAILED);
            row.setErrorCode(errorCode);
            row.setErrorMessage(errorMessage);
            return 1;
        }

        @Override
        public synchronized int markPersistFailed(
            Long executionId,
            String ownerToken,
            String errorCode,
            String errorMessage)
        {
            if (!owns(executionId, ownerToken)
                || !OcAiTurnExecutionService.STATUS_PERSISTING.equals(
                    row.getStatus()))
            {
                return 0;
            }
            row.setStatus(OcAiTurnExecutionService.STATUS_FAILED);
            row.setErrorCode(errorCode);
            row.setErrorMessage(errorMessage);
            return 1;
        }

        @Override
        public synchronized int resetToReady(
            Long executionId,
            String ownerToken,
            String errorCode,
            String errorMessage,
            long leaseSeconds)
        {
            if (!owns(executionId, ownerToken)
                || !OcAiTurnExecutionService.STATUS_PERSISTING.equals(
                    row.getStatus()))
            {
                return 0;
            }
            row.setStatus(OcAiTurnExecutionService.STATUS_READY);
            row.setErrorCode(errorCode);
            row.setErrorMessage(errorMessage);
            row.setLeaseUntil(leaseDate(leaseSeconds));
            return 1;
        }

        private boolean owns(Long executionId, String ownerToken)
        {
            return row != null
                && executionId.equals(row.getExecutionId())
                && ownerToken.equals(row.getOwnerToken());
        }

        private static boolean isExpired(OcAiTurnExecution execution)
        {
            return execution.getLeaseUntil() == null
                || execution.getLeaseUntil().before(new Date());
        }

        private static Date leaseDate(long leaseSeconds)
        {
            return new Date(System.currentTimeMillis() + leaseSeconds * 1000L);
        }

        private static OcAiTurnExecution copy(OcAiTurnExecution source)
        {
            if (source == null)
            {
                return null;
            }
            OcAiTurnExecution target = new OcAiTurnExecution();
            target.setExecutionId(source.getExecutionId());
            target.setConversationId(source.getConversationId());
            target.setTurnId(source.getTurnId());
            target.setUserId(source.getUserId());
            target.setQuestionHash(source.getQuestionHash());
            target.setStatus(source.getStatus());
            target.setOwnerToken(source.getOwnerToken());
            target.setAttemptCount(source.getAttemptCount());
            target.setLeaseUntil(source.getLeaseUntil());
            target.setTraceId(source.getTraceId());
            target.setResultPayload(source.getResultPayload());
            target.setUserMessageId(source.getUserMessageId());
            target.setAssistantMessageId(source.getAssistantMessageId());
            target.setErrorCode(source.getErrorCode());
            target.setErrorMessage(source.getErrorMessage());
            return target;
        }
    }
}