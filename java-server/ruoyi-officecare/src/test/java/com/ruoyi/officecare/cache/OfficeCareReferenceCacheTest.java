package com.ruoyi.officecare.cache;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.officecare.config.OfficeCareCacheProperties;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.OcProductModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OfficeCare Java基础数据缓存测试。
 *
 * @author officecare
 */
class OfficeCareReferenceCacheTest
{
    @Test
    void repeatedBrandOptionsShouldLoadFromMysqlOnlyOnce()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger loads = new AtomicInteger();

        List<OcBrand> first = cache.getBrandOptions(
            () -> List.of(brand(1L, "Brother-" + loads.incrementAndGet())));
        List<OcBrand> second = cache.getBrandOptions(
            () -> List.of(brand(1L, "Brother-" + loads.incrementAndGet())));

        assertEquals(1, loads.get());
        assertEquals("Brother-1", first.get(0).getBrandName());
        assertEquals("Brother-1", second.get(0).getBrandName());
        assertEquals(1800, redis.lastTimeout);
        assertEquals(TimeUnit.SECONDS, redis.lastTimeUnit);
    }

    @Test
    void brandInvalidationShouldChangeAllDependentRegions()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger brandLoads = new AtomicInteger();
        AtomicInteger modelLoads = new AtomicInteger();
        AtomicInteger knowledgeBaseLoads = new AtomicInteger();
        AtomicInteger deviceLoads = new AtomicInteger();

        cache.getBrandOptions(() -> List.of(brand(1L, "B" + brandLoads.incrementAndGet())));
        cache.getProductModelOptions(null,
            () -> List.of(model(2L, "M" + modelLoads.incrementAndGet())));
        cache.getKnowledgeBaseOptions(
            () -> List.of(knowledgeBase(3L, "K" + knowledgeBaseLoads.incrementAndGet())));
        cache.getDeviceOptions(null,
            () -> List.of(device(4L, "D" + deviceLoads.incrementAndGet())));

        cache.invalidateBrands();

        cache.getBrandOptions(() -> List.of(brand(1L, "B" + brandLoads.incrementAndGet())));
        cache.getProductModelOptions(null,
            () -> List.of(model(2L, "M" + modelLoads.incrementAndGet())));
        cache.getKnowledgeBaseOptions(
            () -> List.of(knowledgeBase(3L, "K" + knowledgeBaseLoads.incrementAndGet())));
        cache.getDeviceOptions(null,
            () -> List.of(device(4L, "D" + deviceLoads.incrementAndGet())));

        assertEquals(2, brandLoads.get());
        assertEquals(2, modelLoads.get());
        assertEquals(2, knowledgeBaseLoads.get());
        assertEquals(2, deviceLoads.get());
        assertEquals(1L, redis.revision("brand"));
        assertEquals(1L, redis.revision("product-model"));
        assertEquals(1L, redis.revision("knowledge-base"));
        assertEquals(1L, redis.revision("device"));
    }

    @Test
    void customerOrgInvalidationShouldAlsoChangeDeviceRegion()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger customerLoads = new AtomicInteger();
        AtomicInteger deviceLoads = new AtomicInteger();

        cache.getCustomerOrgOptions(() -> List.of(
            customerOrg(1L, "Customer-" + customerLoads.incrementAndGet())));
        cache.getDeviceOptions(1L, () -> List.of(
            device(2L, "Device-" + deviceLoads.incrementAndGet())));

        cache.invalidateCustomerOrgs();

        cache.getCustomerOrgOptions(() -> List.of(
            customerOrg(1L, "Customer-" + customerLoads.incrementAndGet())));
        cache.getDeviceOptions(1L, () -> List.of(
            device(2L, "Device-" + deviceLoads.incrementAndGet())));

        assertEquals(2, customerLoads.get());
        assertEquals(2, deviceLoads.get());
        assertEquals(1L, redis.revision("customer-org"));
        assertEquals(1L, redis.revision("device"));
    }

    @Test
    void productModelInvalidationShouldAlsoChangeDeviceRegion()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);

        cache.invalidateProductModels();

        assertEquals(1L, redis.revision("product-model"));
        assertEquals(1L, redis.revision("knowledge-base"));
        assertEquals(1L, redis.revision("device"));
        assertEquals(0L, redis.revision("brand"));
        assertEquals(0L, redis.revision("customer-org"));
    }

    @Test
    void deviceOptionsShouldSeparateAllAndCustomerScopes()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger allLoads = new AtomicInteger();
        AtomicInteger customerLoads = new AtomicInteger();

        List<OcDevice> allFirst = cache.getDeviceOptions(null,
            () -> List.of(device(1L, "All-" + allLoads.incrementAndGet())));
        List<OcDevice> customerFirst = cache.getDeviceOptions(9L,
            () -> List.of(device(2L, "Customer-" + customerLoads.incrementAndGet())));
        cache.getDeviceOptions(null,
            () -> List.of(device(3L, "All-" + allLoads.incrementAndGet())));
        cache.getDeviceOptions(9L,
            () -> List.of(device(4L, "Customer-" + customerLoads.incrementAndGet())));

        assertEquals("All-1", allFirst.get(0).getDeviceName());
        assertEquals("Customer-1", customerFirst.get(0).getDeviceName());
        assertEquals(1, allLoads.get());
        assertEquals(1, customerLoads.get());
    }

    @Test
    void invalidationDuringLoadShouldNotPolluteNewRevision()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger loads = new AtomicInteger();

        List<OcKnowledgeBase> first = cache.getKnowledgeBaseOptions(() -> {
            int currentLoad = loads.incrementAndGet();
            cache.invalidateKnowledgeBases();
            return List.of(knowledgeBase(1L, "KB-" + currentLoad));
        });
        List<OcKnowledgeBase> second = cache.getKnowledgeBaseOptions(() ->
            List.of(knowledgeBase(1L, "KB-" + loads.incrementAndGet())));

        assertEquals("KB-1", first.get(0).getKnowledgeBaseName());
        assertEquals("KB-2", second.get(0).getKnowledgeBaseName());
        assertEquals(2, loads.get());
    }

    @Test
    void redisReadFailureShouldReturnMysqlDataWithoutBlockingBusiness()
    {
        FakeRedisCache redis = new FakeRedisCache();
        redis.failReads = true;
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger loads = new AtomicInteger();

        OcBrand first = cache.getBrand(1L,
            () -> brand(1L, "B" + loads.incrementAndGet()));
        OcBrand second = cache.getBrand(1L,
            () -> brand(1L, "B" + loads.incrementAndGet()));

        assertEquals("B1", first.getBrandName());
        assertEquals("B2", second.getBrandName());
        assertEquals(2, loads.get());
        assertTrue(redis.values.isEmpty());
    }

    @Test
    void failedInvalidationShouldBypassOldDataAndChangeRevisionAfterRecovery()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger loads = new AtomicInteger();

        cache.getKnowledgeBaseOptions(() -> List.of(
            knowledgeBase(1L, "KB-" + loads.incrementAndGet())));

        redis.failIncrements = true;
        cache.invalidateKnowledgeBases();
        redis.failIncrements = false;

        List<OcKnowledgeBase> recovered = cache.getKnowledgeBaseOptions(() -> List.of(
            knowledgeBase(1L, "KB-" + loads.incrementAndGet())));

        assertEquals("KB-2", recovered.get(0).getKnowledgeBaseName());
        assertEquals(2, loads.get());
        assertEquals(1L, redis.revision("knowledge-base"));
    }

    @Test
    void incompatibleEntryShouldBeDeletedAndReloaded()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        AtomicInteger loads = new AtomicInteger();

        cache.getBrandOptions(() -> List.of(brand(1L, "B" + loads.incrementAndGet())));
        String dataKey = redis.firstDataKey("brand");
        redis.values.put(dataKey, "not-a-list");

        List<OcBrand> result = cache.getBrandOptions(
            () -> List.of(brand(1L, "B" + loads.incrementAndGet())));

        assertEquals("B2", result.get(0).getBrandName());
        assertEquals(2, loads.get());
        assertTrue(redis.deletedKeys.containsKey(dataKey));
    }

    @Test
    void disabledCacheShouldAlwaysUseLoader()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, false);
        AtomicInteger loads = new AtomicInteger();

        cache.getProductModel(1L, () -> model(1L, "M" + loads.incrementAndGet()));
        cache.getProductModel(1L, () -> model(1L, "M" + loads.incrementAndGet()));

        assertEquals(2, loads.get());
        assertTrue(redis.values.isEmpty());
    }

    @Test
    void referenceTtlShouldRejectUnsafeValues()
    {
        OfficeCareCacheProperties properties = new OfficeCareCacheProperties();

        assertThrows(IllegalArgumentException.class,
            () -> properties.setReferenceTtl(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> properties.setReferenceTtl(Duration.ofHours(25)));

        properties.setReferenceTtl(Duration.ofMinutes(5));
        assertEquals(Duration.ofMinutes(5), properties.getReferenceTtl());
    }

    @Test
    void transactionalInvalidationShouldRunOnlyAfterCommit()
    {
        FakeRedisCache redis = new FakeRedisCache();
        OfficeCareReferenceCache cache = createCache(redis, true);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try
        {
            cache.invalidateKnowledgeBases();
            assertEquals(0L, redis.revision("knowledge-base"));

            TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

            assertEquals(1L, redis.revision("knowledge-base"));
        }
        finally
        {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OfficeCareReferenceCache createCache(FakeRedisCache redis, boolean enabled)
    {
        OfficeCareCacheProperties properties = new OfficeCareCacheProperties();
        properties.setEnabled(enabled);
        properties.setReferenceTtl(Duration.ofMinutes(30));
        return new OfficeCareReferenceCache(redis, properties);
    }

    private OcBrand brand(Long id, String name)
    {
        OcBrand value = new OcBrand();
        value.setBrandId(id);
        value.setBrandName(name);
        return value;
    }

    private OcProductModel model(Long id, String name)
    {
        OcProductModel value = new OcProductModel();
        value.setModelId(id);
        value.setModelName(name);
        return value;
    }

    private OcKnowledgeBase knowledgeBase(Long id, String name)
    {
        OcKnowledgeBase value = new OcKnowledgeBase();
        value.setKnowledgeBaseId(id);
        value.setKnowledgeBaseName(name);
        return value;
    }

    private OcCustomerOrg customerOrg(Long id, String name)
    {
        OcCustomerOrg value = new OcCustomerOrg();
        value.setCustomerOrgId(id);
        value.setCustomerName(name);
        return value;
    }

    private OcDevice device(Long id, String name)
    {
        OcDevice value = new OcDevice();
        value.setDeviceId(id);
        value.setDeviceName(name);
        return value;
    }

    private static class FakeRedisCache extends RedisCache
    {
        private final Map<String, Object> values = new HashMap<>();

        private final Map<String, Boolean> deletedKeys = new HashMap<>();

        private boolean failReads;

        private boolean failIncrements;

        private int lastTimeout;

        private TimeUnit lastTimeUnit;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(String key)
        {
            if (failReads)
            {
                throw new IllegalStateException("redis unavailable");
            }
            return (T) values.get(key);
        }

        @Override
        public <T> void setCacheObject(String key, T value, Integer timeout,
                TimeUnit timeUnit)
        {
            values.put(key, value);
            lastTimeout = timeout;
            lastTimeUnit = timeUnit;
        }

        @Override
        public long increment(String key)
        {
            if (failIncrements)
            {
                throw new IllegalStateException("redis unavailable");
            }
            long nextValue = ((Number) values.getOrDefault(key, 0L)).longValue() + 1L;
            values.put(key, nextValue);
            return nextValue;
        }

        @Override
        public boolean deleteObject(String key)
        {
            deletedKeys.put(key, Boolean.TRUE);
            return values.remove(key) != null;
        }

        private long revision(String region)
        {
            return ((Number) values.getOrDefault(
                "officecare:java:reference:v1:revision:" + region, 0L)).longValue();
        }

        private String firstDataKey(String region)
        {
            String prefix = "officecare:java:reference:v1:" + region + ":r";
            return values.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .findFirst()
                .orElseThrow();
        }
    }
}
