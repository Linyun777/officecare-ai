package com.ruoyi.officecare.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.cache.OfficeCareReferenceCache;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcProductModel;
import com.ruoyi.officecare.mapper.OcBrandMapper;
import com.ruoyi.officecare.mapper.OcCustomerOrgMapper;
import com.ruoyi.officecare.mapper.OcDeviceMapper;
import com.ruoyi.officecare.mapper.OcProductModelMapper;
import com.ruoyi.officecare.mapper.OcWorkOrderMapper;
import com.ruoyi.officecare.service.IOcDeviceService;

/**
 * OfficeCare设备档案服务实现。
 *
 * @author officecare
 */
@Service
public class OcDeviceServiceImpl implements IOcDeviceService
{
    private static final String NORMAL_STATUS = "0";

    private final OcDeviceMapper deviceMapper;

    private final OcCustomerOrgMapper customerOrgMapper;

    private final OcProductModelMapper productModelMapper;

    private final OcBrandMapper brandMapper;

    private final OcWorkOrderMapper workOrderMapper;

    private final OfficeCareReferenceCache referenceCache;

    public OcDeviceServiceImpl(
        OcDeviceMapper deviceMapper,
        OcCustomerOrgMapper customerOrgMapper,
        OcProductModelMapper productModelMapper,
        OcBrandMapper brandMapper,
        OcWorkOrderMapper workOrderMapper,
        OfficeCareReferenceCache referenceCache)
    {
        this.deviceMapper = deviceMapper;
        this.customerOrgMapper = customerOrgMapper;
        this.productModelMapper = productModelMapper;
        this.brandMapper = brandMapper;
        this.workOrderMapper = workOrderMapper;
        this.referenceCache = referenceCache;
    }

    @Override
    public List<OcDevice> selectDeviceList(OcDevice device)
    {
        return deviceMapper.selectDeviceList(device);
    }

    @Override
    public List<OcDevice> selectDeviceOptions(Long customerOrgId)
    {
        return referenceCache.getDeviceOptions(customerOrgId,
            () -> deviceMapper.selectDeviceOptions(customerOrgId));
    }

    @Override
    public OcDevice selectDeviceById(Long deviceId)
    {
        return referenceCache.getDevice(deviceId,
            () -> deviceMapper.selectDeviceById(deviceId));
    }

    @Override
    public int insertDevice(OcDevice device)
    {
        normalizeDevice(device);
        validateCustomerOrg(device.getCustomerOrgId(), true);
        validateProductModel(device, true);
        validateSerialNumberUnique(device);
        validateDates(device);
        int rows = deviceMapper.insertDevice(device);
        if (rows > 0)
        {
            referenceCache.invalidateDevices();
        }
        return rows;
    }

    @Override
    public int updateDevice(OcDevice device)
    {
        if (device.getDeviceId() == null)
        {
            throw new ServiceException("设备ID不能为空");
        }
        OcDevice existing = deviceMapper.selectDeviceById(device.getDeviceId());
        if (existing == null)
        {
            throw new ServiceException("设备档案不存在或已被删除");
        }

        normalizeDevice(device);
        boolean customerChanged = !Objects.equals(
            existing.getCustomerOrgId(), device.getCustomerOrgId());
        boolean modelChanged = !Objects.equals(
            existing.getModelId(), device.getModelId());
        validateCustomerOrg(device.getCustomerOrgId(), customerChanged);
        validateProductModel(device, modelChanged);
        validateSerialNumberUnique(device);
        validateDates(device);
        int rows = deviceMapper.updateDevice(device);
        if (rows > 0)
        {
            referenceCache.invalidateDevices();
        }
        return rows;
    }

    @Override
    public int deleteDeviceByIds(Long[] deviceIds, String updateBy)
    {
        for (Long deviceId : deviceIds)
        {
            OcDevice existing = deviceMapper.selectDeviceById(deviceId);
            if (existing == null)
            {
                throw new ServiceException("设备档案不存在或已被删除");
            }
            if (workOrderMapper.countWorkOrderByDeviceId(deviceId) > 0)
            {
                throw new ServiceException("设备“" + existing.getDeviceName()
                    + "”已关联售后工单，不能删除");
            }
        }
        int rows = deviceMapper.deleteDeviceByIds(deviceIds, updateBy);
        if (rows > 0)
        {
            referenceCache.invalidateDevices();
        }
        return rows;
    }

    private void normalizeDevice(OcDevice device)
    {
        device.setDeviceName(StringUtils.trim(device.getDeviceName()));
        device.setSerialNumber(StringUtils.trim(device.getSerialNumber()));
        device.setInstallLocation(StringUtils.trim(device.getInstallLocation()));
        device.setRemark(StringUtils.trim(device.getRemark()));
        if (StringUtils.isEmpty(device.getStatus()))
        {
            device.setStatus(NORMAL_STATUS);
        }
    }

    private void validateCustomerOrg(Long customerOrgId, boolean requireActive)
    {
        OcCustomerOrg customerOrg = customerOrgMapper.selectCustomerOrgById(
            customerOrgId);
        if (customerOrg == null)
        {
            throw new ServiceException("所属客户单位不存在或已被删除");
        }
        if (requireActive && !NORMAL_STATUS.equals(customerOrg.getStatus()))
        {
            throw new ServiceException("所属客户单位已停用，请先启用客户单位");
        }
    }

    private void validateProductModel(OcDevice device, boolean requireActive)
    {
        OcProductModel productModel = productModelMapper.selectProductModelById(
            device.getModelId());
        if (productModel == null)
        {
            throw new ServiceException("产品型号不存在或已被删除");
        }
        if (!Objects.equals(productModel.getBrandId(), device.getBrandId()))
        {
            throw new ServiceException("所选产品型号不属于当前品牌");
        }
        OcBrand brand = brandMapper.selectBrandById(productModel.getBrandId());
        if (brand == null)
        {
            throw new ServiceException("所属品牌不存在或已被删除");
        }
        if (requireActive && !NORMAL_STATUS.equals(brand.getStatus()))
        {
            throw new ServiceException("所属品牌已停用，请先启用品牌");
        }
        if (requireActive && !NORMAL_STATUS.equals(productModel.getStatus()))
        {
            throw new ServiceException("产品型号已停用，请先启用产品型号");
        }
    }

    private void validateSerialNumberUnique(OcDevice device)
    {
        OcDevice existing = deviceMapper.selectDeviceBySerialNumber(
            device.getSerialNumber());
        if (existing != null && !existing.getDeviceId().equals(device.getDeviceId()))
        {
            throw new ServiceException("设备序列号“" + device.getSerialNumber()
                + "”已存在或曾被使用");
        }
    }

    private void validateDates(OcDevice device)
    {
        Date warrantyEndDate = device.getWarrantyEndDate();
        if (warrantyEndDate == null)
        {
            return;
        }
        if (isAfter(device.getPurchaseDate(), warrantyEndDate))
        {
            throw new ServiceException("保修截止日期不能早于购买日期");
        }
        if (isAfter(device.getInstallDate(), warrantyEndDate))
        {
            throw new ServiceException("保修截止日期不能早于安装日期");
        }
    }

    private boolean isAfter(Date startDate, Date endDate)
    {
        return startDate != null && startDate.after(endDate);
    }
}
