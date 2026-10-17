package com.ruoyi.officecare.service;

import java.lang.reflect.Proxy;
import java.sql.Date;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.officecare.cache.OfficeCareReferenceCache;
import com.ruoyi.officecare.config.OfficeCareCacheProperties;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcProductModel;
import com.ruoyi.officecare.mapper.OcBrandMapper;
import com.ruoyi.officecare.mapper.OcCustomerOrgMapper;
import com.ruoyi.officecare.mapper.OcDeviceMapper;
import com.ruoyi.officecare.mapper.OcProductModelMapper;
import com.ruoyi.officecare.mapper.OcWorkOrderMapper;
import com.ruoyi.officecare.service.impl.OcDeviceServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 设备档案关键业务规则测试。
 *
 * @author officecare
 */
class OcDeviceServiceImplTest
{
    @Test
    void insertDeviceShouldAcceptConsistentReferencesAndDates()
    {
        OcDeviceServiceImpl service = createService(null);

        assertEquals(1, service.insertDevice(createDevice()));
    }

    @Test
    void insertDeviceShouldRejectMismatchedBrandAndModel()
    {
        OcDevice device = createDevice();
        device.setBrandId(99L);

        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> createService(null).insertDevice(device));

        assertEquals("所选产品型号不属于当前品牌", exception.getMessage());
    }

    @Test
    void insertDeviceShouldRejectWarrantyBeforeInstallDate()
    {
        OcDevice device = createDevice();
        device.setWarrantyEndDate(Date.valueOf("2026-01-11"));

        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> createService(null).insertDevice(device));

        assertEquals("保修截止日期不能早于安装日期", exception.getMessage());
    }

    @Test
    void insertDeviceShouldRejectHistoricalSerialNumber()
    {
        OcDevice duplicate = new OcDevice();
        duplicate.setDeviceId(9L);

        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> createService(duplicate).insertDevice(createDevice()));

        assertEquals("设备序列号“SN-DEMO-0001”已存在或曾被使用",
            exception.getMessage());
    }

    @Test
    void deleteDeviceShouldRejectLinkedWorkOrder()
    {
        ServiceException exception = assertThrows(
            ServiceException.class,
            () -> createService(null, 1).deleteDeviceByIds(
                new Long[] { 8L }, "admin"));

        assertEquals("设备“财务室打印机”已关联售后工单，不能删除",
            exception.getMessage());
    }

    private OcDeviceServiceImpl createService(OcDevice serialNumberMatch)
    {
        return createService(serialNumberMatch, 0);
    }

    private OcDeviceServiceImpl createService(OcDevice serialNumberMatch,
        int workOrderCount)
    {
        OcCustomerOrg customerOrg = new OcCustomerOrg();
        customerOrg.setCustomerOrgId(1L);
        customerOrg.setStatus("0");

        OcProductModel productModel = new OcProductModel();
        productModel.setModelId(2L);
        productModel.setBrandId(2L);
        productModel.setStatus("0");

        OcBrand brand = new OcBrand();
        brand.setBrandId(2L);
        brand.setStatus("0");

        OcDeviceMapper deviceMapper = proxy(OcDeviceMapper.class, (name, args) -> {
            if ("selectDeviceBySerialNumber".equals(name))
            {
                return serialNumberMatch;
            }
            if ("insertDevice".equals(name))
            {
                return 1;
            }
            if ("selectDeviceById".equals(name))
            {
                OcDevice existing = createDevice();
                existing.setDeviceId((Long) args[0]);
                existing.setDeviceName("财务室打印机");
                return existing;
            }
            return null;
        });
        OcCustomerOrgMapper customerMapper = proxy(
            OcCustomerOrgMapper.class,
            (name, args) -> "selectCustomerOrgById".equals(name)
                ? customerOrg : null);
        OcProductModelMapper modelMapper = proxy(
            OcProductModelMapper.class,
            (name, args) -> "selectProductModelById".equals(name)
                ? productModel : null);
        OcBrandMapper brandMapper = proxy(
            OcBrandMapper.class,
            (name, args) -> "selectBrandById".equals(name) ? brand : null);
        OcWorkOrderMapper workOrderMapper = proxy(
            OcWorkOrderMapper.class, (name, args) ->
                "countWorkOrderByDeviceId".equals(name) ? workOrderCount : 0);

        return new OcDeviceServiceImpl(
            deviceMapper, customerMapper, modelMapper, brandMapper,
            workOrderMapper, disabledReferenceCache());
    }

    private OfficeCareReferenceCache disabledReferenceCache()
    {
        OfficeCareCacheProperties properties = new OfficeCareCacheProperties();
        properties.setEnabled(false);
        return new OfficeCareReferenceCache(null, properties);
    }

    private OcDevice createDevice()
    {
        OcDevice device = new OcDevice();
        device.setCustomerOrgId(1L);
        device.setBrandId(2L);
        device.setModelId(2L);
        device.setDeviceName(" 财务室打印机 ");
        device.setSerialNumber(" SN-DEMO-0001 ");
        device.setPurchaseDate(Date.valueOf("2026-01-10"));
        device.setInstallDate(Date.valueOf("2026-01-12"));
        device.setWarrantyEndDate(Date.valueOf("2027-01-09"));
        device.setStatus("0");
        return device;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> mapperType, MapperInvocation invocation)
    {
        return (T) Proxy.newProxyInstance(
            mapperType.getClassLoader(),
            new Class<?>[] { mapperType },
            (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface MapperInvocation
    {
        Object invoke(String methodName, Object[] args);
    }
}
