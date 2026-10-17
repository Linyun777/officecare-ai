package com.ruoyi.officecare.service;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.officecare.cache.OfficeCareReferenceCache;
import com.ruoyi.officecare.config.OfficeCareCacheProperties;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.OcProductModel;
import com.ruoyi.officecare.mapper.OcBrandMapper;
import com.ruoyi.officecare.mapper.OcCustomerOrgMapper;
import com.ruoyi.officecare.mapper.OcDeviceMapper;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.mapper.OcProductModelMapper;
import com.ruoyi.officecare.mapper.OcWorkOrderMapper;
import com.ruoyi.officecare.service.impl.OcBrandServiceImpl;
import com.ruoyi.officecare.service.impl.OcCustomerOrgServiceImpl;
import com.ruoyi.officecare.service.impl.OcDeviceServiceImpl;
import com.ruoyi.officecare.service.impl.OcKnowledgeBaseServiceImpl;
import com.ruoyi.officecare.service.impl.OcProductModelServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OfficeCare低频变化基础数据Service缓存接入测试。
 *
 * @author officecare
 */
class OcReferenceDataServiceCacheTest
{
    @Test
    void brandUpdateShouldInvalidateCachedOptions()
    {
        AtomicInteger optionQueries = new AtomicInteger();
        OcBrand existing = brand(1L, "Brother");
        OcBrandMapper brandMapper = proxy(OcBrandMapper.class, (name, args) -> {
            if ("selectBrandOptions".equals(name))
            {
                optionQueries.incrementAndGet();
                return List.of(existing);
            }
            if ("selectBrandById".equals(name))
            {
                return existing;
            }
            if ("selectBrandByCode".equals(name))
            {
                return null;
            }
            if ("updateBrand".equals(name))
            {
                return 1;
            }
            return 0;
        });
        OcProductModelMapper modelMapper = proxy(
            OcProductModelMapper.class, (name, args) -> 0);
        OcBrandServiceImpl service = new OcBrandServiceImpl(
            brandMapper, modelMapper, createCache());

        service.selectBrandOptions();
        service.selectBrandOptions();
        service.updateBrand(brand(1L, "Brother Updated"));
        service.selectBrandOptions();

        assertEquals(2, optionQueries.get());
    }

    @Test
    void productModelInsertShouldInvalidateCachedOptions()
    {
        AtomicInteger optionQueries = new AtomicInteger();
        OcProductModelMapper modelMapper = proxy(OcProductModelMapper.class,
            (name, args) -> {
                if ("selectProductModelOptions".equals(name))
                {
                    optionQueries.incrementAndGet();
                    return List.of(model(2L, 1L, "DCP-L2550DW"));
                }
                if ("selectProductModelByCode".equals(name))
                {
                    return null;
                }
                if ("insertProductModel".equals(name))
                {
                    return 1;
                }
                return 0;
            });
        OcBrandMapper brandMapper = proxy(OcBrandMapper.class,
            (name, args) -> "selectBrandById".equals(name)
                ? brand(1L, "Brother") : null);
        OcKnowledgeBaseMapper knowledgeBaseMapper = proxy(
            OcKnowledgeBaseMapper.class, (name, args) -> 0);
        OcDeviceMapper deviceMapper = proxy(OcDeviceMapper.class,
            (name, args) -> 0);
        OcProductModelServiceImpl service = new OcProductModelServiceImpl(
            modelMapper, brandMapper, knowledgeBaseMapper, deviceMapper,
            createCache());

        service.selectProductModelOptions(1L);
        service.selectProductModelOptions(1L);
        service.insertProductModel(model(null, 1L, "DCP-L2668DW"));
        service.selectProductModelOptions(1L);

        assertEquals(2, optionQueries.get());
    }

    @Test
    void knowledgeBaseInsertShouldInvalidateCachedOptions()
    {
        AtomicInteger optionQueries = new AtomicInteger();
        OcKnowledgeBaseMapper knowledgeBaseMapper = proxy(
            OcKnowledgeBaseMapper.class, (name, args) -> {
                if ("selectKnowledgeBaseOptions".equals(name))
                {
                    optionQueries.incrementAndGet();
                    return List.of(knowledgeBase(3L, 1L, 2L, "Brother KB"));
                }
                if ("selectKnowledgeBaseByCode".equals(name))
                {
                    return null;
                }
                if ("insertKnowledgeBase".equals(name))
                {
                    return 1;
                }
                return 0;
            });
        OcBrandMapper brandMapper = proxy(OcBrandMapper.class,
            (name, args) -> "selectBrandById".equals(name)
                ? brand(1L, "Brother") : null);
        OcProductModelMapper modelMapper = proxy(OcProductModelMapper.class,
            (name, args) -> "selectProductModelById".equals(name)
                ? model(2L, 1L, "DCP-L2550DW") : null);
        OcDocumentMapper documentMapper = proxy(OcDocumentMapper.class,
            (name, args) -> 0);
        OcKnowledgeBaseServiceImpl service = new OcKnowledgeBaseServiceImpl(
            knowledgeBaseMapper, brandMapper, modelMapper, documentMapper,
            createCache());

        service.selectKnowledgeBaseOptions();
        service.selectKnowledgeBaseOptions();
        service.insertKnowledgeBase(
            knowledgeBase(null, 1L, 2L, "Brother Printer KB"));
        service.selectKnowledgeBaseOptions();

        assertEquals(2, optionQueries.get());
    }

    @Test
    void customerOrgInsertShouldInvalidateCachedOptions()
    {
        AtomicInteger optionQueries = new AtomicInteger();
        OcCustomerOrgMapper customerOrgMapper = proxy(
            OcCustomerOrgMapper.class, (name, args) -> {
                if ("selectCustomerOrgOptions".equals(name))
                {
                    optionQueries.incrementAndGet();
                    return List.of(customerOrg(4L, "Demo Customer"));
                }
                if ("selectCustomerOrgByCode".equals(name))
                {
                    return null;
                }
                if ("insertCustomerOrg".equals(name))
                {
                    return 1;
                }
                return 0;
            });
        OcDeviceMapper deviceMapper = proxy(OcDeviceMapper.class,
            (name, args) -> 0);
        OcCustomerOrgServiceImpl service = new OcCustomerOrgServiceImpl(
            customerOrgMapper, deviceMapper, createCache());

        service.selectCustomerOrgOptions();
        service.selectCustomerOrgOptions();
        service.insertCustomerOrg(customerOrg(null, "New Customer"));
        service.selectCustomerOrgOptions();

        assertEquals(2, optionQueries.get());
    }

    @Test
    void deviceInsertShouldInvalidateCachedOptions()
    {
        AtomicInteger optionQueries = new AtomicInteger();
        OcDeviceMapper deviceMapper = proxy(OcDeviceMapper.class,
            (name, args) -> {
                if ("selectDeviceOptions".equals(name))
                {
                    optionQueries.incrementAndGet();
                    return List.of(device(5L, 4L, 1L, 2L, "Printer-1"));
                }
                if ("selectDeviceBySerialNumber".equals(name))
                {
                    return null;
                }
                if ("insertDevice".equals(name))
                {
                    return 1;
                }
                return 0;
            });
        OcCustomerOrgMapper customerOrgMapper = proxy(
            OcCustomerOrgMapper.class, (name, args) ->
                "selectCustomerOrgById".equals(name)
                    ? customerOrg(4L, "Demo Customer") : null);
        OcProductModelMapper modelMapper = proxy(OcProductModelMapper.class,
            (name, args) -> "selectProductModelById".equals(name)
                ? model(2L, 1L, "DCP-L2550DW") : null);
        OcBrandMapper brandMapper = proxy(OcBrandMapper.class,
            (name, args) -> "selectBrandById".equals(name)
                ? brand(1L, "Brother") : null);
        OcWorkOrderMapper workOrderMapper = proxy(OcWorkOrderMapper.class,
            (name, args) -> 0);
        OcDeviceServiceImpl service = new OcDeviceServiceImpl(
            deviceMapper, customerOrgMapper, modelMapper, brandMapper,
            workOrderMapper, createCache());

        service.selectDeviceOptions(4L);
        service.selectDeviceOptions(4L);
        service.insertDevice(device(null, 4L, 1L, 2L, "Printer-2"));
        service.selectDeviceOptions(4L);

        assertEquals(2, optionQueries.get());
    }

    private OfficeCareReferenceCache createCache()
    {
        OfficeCareCacheProperties properties = new OfficeCareCacheProperties();
        properties.setReferenceTtl(Duration.ofMinutes(30));
        return new OfficeCareReferenceCache(new FakeRedisCache(), properties);
    }

    private OcBrand brand(Long id, String name)
    {
        OcBrand value = new OcBrand();
        value.setBrandId(id);
        value.setBrandCode("BROTHER");
        value.setBrandName(name);
        value.setSortOrder(1);
        value.setStatus("0");
        return value;
    }

    private OcProductModel model(Long id, Long brandId, String name)
    {
        OcProductModel value = new OcProductModel();
        value.setModelId(id);
        value.setBrandId(brandId);
        value.setModelCode(name);
        value.setModelName(name);
        value.setSortOrder(1);
        value.setStatus("0");
        return value;
    }

    private OcKnowledgeBase knowledgeBase(Long id, Long brandId, Long modelId,
            String name)
    {
        OcKnowledgeBase value = new OcKnowledgeBase();
        value.setKnowledgeBaseId(id);
        value.setKnowledgeBaseCode("BROTHER-DCP-KB");
        value.setKnowledgeBaseName(name);
        value.setBrandId(brandId);
        value.setModelId(modelId);
        value.setSortOrder(1);
        value.setStatus("0");
        return value;
    }

    private OcCustomerOrg customerOrg(Long id, String name)
    {
        OcCustomerOrg value = new OcCustomerOrg();
        value.setCustomerOrgId(id);
        value.setCustomerCode("CUSTOMER-001");
        value.setCustomerName(name);
        value.setStatus("0");
        return value;
    }

    private OcDevice device(Long id, Long customerOrgId, Long brandId,
            Long modelId, String name)
    {
        OcDevice value = new OcDevice();
        value.setDeviceId(id);
        value.setCustomerOrgId(customerOrgId);
        value.setBrandId(brandId);
        value.setModelId(modelId);
        value.setDeviceName(name);
        value.setSerialNumber("SN-" + name);
        value.setStatus("0");
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> mapperType, MapperInvocation invocation)
    {
        return (T) Proxy.newProxyInstance(
            mapperType.getClassLoader(), new Class<?>[] { mapperType },
            (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface MapperInvocation
    {
        Object invoke(String methodName, Object[] args);
    }

    private static class FakeRedisCache extends RedisCache
    {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(String key)
        {
            return (T) values.get(key);
        }

        @Override
        public <T> void setCacheObject(String key, T value, Integer timeout,
                TimeUnit timeUnit)
        {
            values.put(key, value);
        }

        @Override
        public long increment(String key)
        {
            long nextValue = ((Number) values.getOrDefault(key, 0L)).longValue() + 1L;
            values.put(key, nextValue);
            return nextValue;
        }
    }
}
