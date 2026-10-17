package com.ruoyi.officecare.service.impl;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.cache.OfficeCareReferenceCache;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.mapper.OcCustomerOrgMapper;
import com.ruoyi.officecare.mapper.OcDeviceMapper;
import com.ruoyi.officecare.service.IOcCustomerOrgService;

/**
 * OfficeCare客户单位服务实现。
 *
 * @author officecare
 */
@Service
public class OcCustomerOrgServiceImpl implements IOcCustomerOrgService
{
    private static final String NORMAL_STATUS = "0";

    private final OcCustomerOrgMapper customerOrgMapper;

    private final OcDeviceMapper deviceMapper;

    private final OfficeCareReferenceCache referenceCache;

    public OcCustomerOrgServiceImpl(
        OcCustomerOrgMapper customerOrgMapper,
        OcDeviceMapper deviceMapper,
        OfficeCareReferenceCache referenceCache)
    {
        this.customerOrgMapper = customerOrgMapper;
        this.deviceMapper = deviceMapper;
        this.referenceCache = referenceCache;
    }

    @Override
    public List<OcCustomerOrg> selectCustomerOrgList(OcCustomerOrg customerOrg)
    {
        return customerOrgMapper.selectCustomerOrgList(customerOrg);
    }

    @Override
    public List<OcCustomerOrg> selectCustomerOrgOptions()
    {
        return referenceCache.getCustomerOrgOptions(
            customerOrgMapper::selectCustomerOrgOptions);
    }

    @Override
    public OcCustomerOrg selectCustomerOrgById(Long customerOrgId)
    {
        return referenceCache.getCustomerOrg(customerOrgId,
            () -> customerOrgMapper.selectCustomerOrgById(customerOrgId));
    }

    @Override
    public int insertCustomerOrg(OcCustomerOrg customerOrg)
    {
        normalizeCustomerOrg(customerOrg);
        validateCustomerCodeUnique(customerOrg);
        int rows = customerOrgMapper.insertCustomerOrg(customerOrg);
        if (rows > 0)
        {
            referenceCache.invalidateCustomerOrgs();
        }
        return rows;
    }

    @Override
    public int updateCustomerOrg(OcCustomerOrg customerOrg)
    {
        if (customerOrg.getCustomerOrgId() == null
            || customerOrgMapper.selectCustomerOrgById(
                customerOrg.getCustomerOrgId()) == null)
        {
            throw new ServiceException("客户单位不存在或已被删除");
        }
        normalizeCustomerOrg(customerOrg);
        validateCustomerCodeUnique(customerOrg);
        int rows = customerOrgMapper.updateCustomerOrg(customerOrg);
        if (rows > 0)
        {
            referenceCache.invalidateCustomerOrgs();
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCustomerOrgByIds(Long[] customerOrgIds, String updateBy)
    {
        for (Long customerOrgId : customerOrgIds)
        {
            OcCustomerOrg customerOrg = customerOrgMapper.selectCustomerOrgById(
                customerOrgId);
            if (customerOrg != null
                && deviceMapper.countDeviceByCustomerOrgId(customerOrgId) > 0)
            {
                throw new ServiceException("客户单位“"
                    + customerOrg.getCustomerName() + "”下存在设备档案，不能删除");
            }
        }
        int rows = customerOrgMapper.deleteCustomerOrgByIds(customerOrgIds, updateBy);
        if (rows > 0)
        {
            referenceCache.invalidateCustomerOrgs();
        }
        return rows;
    }

    private void normalizeCustomerOrg(OcCustomerOrg customerOrg)
    {
        String customerCode = StringUtils.trim(customerOrg.getCustomerCode());
        if (customerCode != null)
        {
            customerCode = customerCode.toUpperCase(Locale.ROOT);
        }
        customerOrg.setCustomerCode(customerCode);
        customerOrg.setCustomerName(StringUtils.trim(customerOrg.getCustomerName()));
        customerOrg.setContactName(StringUtils.trim(customerOrg.getContactName()));
        customerOrg.setContactPhone(StringUtils.trim(customerOrg.getContactPhone()));
        customerOrg.setAddress(StringUtils.trim(customerOrg.getAddress()));
        customerOrg.setRemark(StringUtils.trim(customerOrg.getRemark()));
        if (StringUtils.isEmpty(customerOrg.getStatus()))
        {
            customerOrg.setStatus(NORMAL_STATUS);
        }
    }

    private void validateCustomerCodeUnique(OcCustomerOrg customerOrg)
    {
        OcCustomerOrg existing = customerOrgMapper.selectCustomerOrgByCode(
            customerOrg.getCustomerCode());
        if (existing != null
            && !existing.getCustomerOrgId().equals(customerOrg.getCustomerOrgId()))
        {
            throw new ServiceException("客户编码“" + customerOrg.getCustomerCode()
                + "”已存在或曾被使用");
        }
    }
}
