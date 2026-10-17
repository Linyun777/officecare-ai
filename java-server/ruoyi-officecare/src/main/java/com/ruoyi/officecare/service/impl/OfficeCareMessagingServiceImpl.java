package com.ruoyi.officecare.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.constant.DocumentProcessingConstants;
import com.ruoyi.officecare.constant.OutboxConstants;
import com.ruoyi.officecare.constant.OutboxConsumeConstants;
import com.ruoyi.officecare.domain.OcDocumentTask;
import com.ruoyi.officecare.domain.OcOutboxEvent;
import com.ruoyi.officecare.domain.vo.OfficeCareMqDeadEvent;
import com.ruoyi.officecare.mapper.OcDocumentTaskMapper;
import com.ruoyi.officecare.mapper.OcOutboxEventMapper;
import com.ruoyi.officecare.messaging.DocumentTaskRetryService;
import com.ruoyi.officecare.service.IOfficeCareMessagingService;

/** 基于Outbox审计记录完成异常消息查询和人工重投。 */
@Service
public class OfficeCareMessagingServiceImpl implements IOfficeCareMessagingService
{
    private static final int RECENT_DEAD_EVENT_LIMIT = 20;

    private final OcOutboxEventMapper outboxMapper;

    private final OcDocumentTaskMapper taskMapper;

    private final DocumentTaskRetryService retryService;

    public OfficeCareMessagingServiceImpl(OcOutboxEventMapper outboxMapper,
                                          OcDocumentTaskMapper taskMapper,
                                          DocumentTaskRetryService retryService)
    {
        this.outboxMapper = outboxMapper;
        this.taskMapper = taskMapper;
        this.retryService = retryService;
    }

    @Override
    public List<OfficeCareMqDeadEvent> selectRecentDeadEvents()
    {
        return outboxMapper.selectRecentDeadEvents(RECENT_DEAD_EVENT_LIMIT);
    }

    @Override
    @Transactional
    public void replayDeadEvent(Long outboxId, String username)
    {
        if (outboxId == null)
        {
            throw new ServiceException("Outbox事件ID不能为空");
        }
        OcOutboxEvent event = outboxMapper.selectOutboxEventById(outboxId);
        if (event == null)
        {
            throw new ServiceException("异常消息不存在");
        }
        String operator = StringUtils.defaultIfEmpty(username, "system");
        if (isPublishDead(event))
        {
            replayPublishFailure(event, operator);
            return;
        }
        if (OutboxConsumeConstants.STATUS_DEAD.equals(event.getConsumeStatus()))
        {
            replayConsumerFailure(event, operator);
            return;
        }
        throw new ServiceException("该消息当前不处于可重新投递状态");
    }

    private void replayPublishFailure(OcOutboxEvent event, String operator)
    {
        OcDocumentTask task = requireTask(event);
        if (!DocumentProcessingConstants.STATUS_PENDING.equals(task.getStatus())
            || !event.getTaskAttempt().equals(task.getAttemptCount()))
        {
            throw new ServiceException("任务状态已变化，不能重新发布原消息");
        }
        if (outboxMapper.reactivatePublishDead(event.getOutboxId(), operator) == 0)
        {
            throw new ServiceException("消息已被其他操作处理，请刷新后重试");
        }
    }

    private void replayConsumerFailure(OcOutboxEvent event, String operator)
    {
        OcDocumentTask task = requireTask(event);
        if (!DocumentProcessingConstants.STATUS_FAILED.equals(task.getStatus())
            || task.getAttemptCount() == null
            || event.getTaskAttempt() == null
            || task.getAttemptCount() != event.getTaskAttempt() + 1)
        {
            throw new ServiceException("任务状态或执行轮次已变化，不能人工重投");
        }
        if (!retryService.scheduleManualRetry(task, operator))
        {
            throw new ServiceException("失败任务未能恢复为待处理状态");
        }
        if (outboxMapper.markConsumerDeadReplayed(event.getOutboxId(), operator) == 0)
        {
            throw new ServiceException("消息已被其他操作处理，请刷新后重试");
        }
    }

    private OcDocumentTask requireTask(OcOutboxEvent event)
    {
        OcDocumentTask task = taskMapper.selectTaskById(event.getTaskId());
        if (task == null)
        {
            throw new ServiceException("关联文档任务不存在，不能重新投递");
        }
        return task;
    }

    private boolean isPublishDead(OcOutboxEvent event)
    {
        return OutboxConstants.STATUS_DEAD.equals(event.getStatus())
            && OutboxConsumeConstants.STATUS_PENDING.equals(
                event.getConsumeStatus());
    }
}
