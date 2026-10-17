package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcCustomerOrg;

/**
 * OfficeCare客户单位服务。
 *
 * @author officecare
 */
public interface IOcCustomerOrgService
{
    public List<OcCustomerOrg> selectCustomerOrgList(OcCustomerOrg customerOrg);

    public List<OcCustomerOrg> selectCustomerOrgOptions();

    public OcCustomerOrg selectCustomerOrgById(Long customerOrgId);

    public int insertCustomerOrg(OcCustomerOrg customerOrg);

    public int updateCustomerOrg(OcCustomerOrg customerOrg);

    public int deleteCustomerOrgByIds(Long[] customerOrgIds, String updateBy);
}
