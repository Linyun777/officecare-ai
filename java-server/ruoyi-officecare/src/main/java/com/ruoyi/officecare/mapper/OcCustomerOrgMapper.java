package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcCustomerOrg;

/**
 * OfficeCare客户单位数据访问层。
 *
 * @author officecare
 */
public interface OcCustomerOrgMapper
{
    public List<OcCustomerOrg> selectCustomerOrgList(OcCustomerOrg customerOrg);

    public List<OcCustomerOrg> selectCustomerOrgOptions();

    public OcCustomerOrg selectCustomerOrgById(Long customerOrgId);

    public OcCustomerOrg selectCustomerOrgByCode(String customerCode);

    public int insertCustomerOrg(OcCustomerOrg customerOrg);

    public int updateCustomerOrg(OcCustomerOrg customerOrg);

    public int deleteCustomerOrgByIds(@Param("customerOrgIds") Long[] customerOrgIds,
        @Param("updateBy") String updateBy);
}
