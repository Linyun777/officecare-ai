package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.vo.OfficeCareMqDeadEvent;

/** OfficeCare消息队列运维服务。 */
public interface IOfficeCareMessagingService
{
    public List<OfficeCareMqDeadEvent> selectRecentDeadEvents();

    public void replayDeadEvent(Long outboxId, String username);
}
