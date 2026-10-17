package com.ruoyi.officecare.service;

import com.ruoyi.officecare.domain.vo.OfficeCareDashboardSummary;

/**
 * OfficeCare工作台服务
 *
 * @author officecare
 */
public interface IOfficeCareDashboardService
{
    /**
     * 查询工作台摘要
     *
     * @return 工作台摘要
     */
    public OfficeCareDashboardSummary selectDashboardSummary();
}
