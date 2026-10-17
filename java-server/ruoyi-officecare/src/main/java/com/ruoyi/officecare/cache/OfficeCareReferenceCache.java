package com.ruoyi.officecare.cache;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.officecare.config.OfficeCareCacheProperties;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.OcProductModel;

/**
 * OfficeCare低频变化基础数据缓存。
 *
 * <p>缓存键包含区域修订号。业务数据变化时只递增修订号，不扫描Redis，
 * 旧修订号下的数据由TTL自然淘汰。</p>
 *
 * @author officecare
 */
@Component
public class OfficeCareReferenceCache {
    private static final Logger log = LoggerFactory.getLogger(OfficeCareReferenceCache.class);

    private static final String KEY_PREFIX = "officecare:java:reference:v1";

    private static final int LOCK_STRIPE_COUNT = 32;

    private final RedisCache redisCache;

    private final OfficeCareCacheProperties properties;

    private final Object[] loadLocks = new Object[LOCK_STRIPE_COUNT];

    /**
     * 某次失效失败后，在当前进程中绕过对应区域，直到Redis恢复并成功换代。
     */
    private final boolean[] invalidationPending = new boolean[CacheRegion.values().length];

    public OfficeCareReferenceCache(RedisCache redisCache, OfficeCareCacheProperties properties) {
        this.redisCache = redisCache;
        this.properties = properties;
        for (int index = 0; index < loadLocks.length; index++) {
            loadLocks[index] = new Object();
        }
    }

    public List<OcBrand> getBrandOptions(Supplier<List<OcBrand>> loader) {
        return getOrLoad(CacheRegion.BRAND, "options", List.class, loader);
    }

    public OcBrand getBrand(Long brandId, Supplier<OcBrand> loader) {
        return getOrLoad(CacheRegion.BRAND, "detail:" + brandId, OcBrand.class, loader);
    }

    public List<OcProductModel> getProductModelOptions(Long brandId,
                                                       Supplier<List<OcProductModel>> loader) {
        String scope = brandId == null ? "all" : "brand:" + brandId;
        return getOrLoad(CacheRegion.PRODUCT_MODEL, "options:" + scope, List.class, loader);
    }

    public OcProductModel getProductModel(Long modelId, Supplier<OcProductModel> loader) {
        return getOrLoad(CacheRegion.PRODUCT_MODEL, "detail:" + modelId,
                OcProductModel.class, loader);
    }

    public List<OcKnowledgeBase> getKnowledgeBaseOptions(
            Supplier<List<OcKnowledgeBase>> loader) {
        return getOrLoad(CacheRegion.KNOWLEDGE_BASE, "options", List.class, loader);
    }

    public OcKnowledgeBase getKnowledgeBase(Long knowledgeBaseId,
                                            Supplier<OcKnowledgeBase> loader) {
        return getOrLoad(CacheRegion.KNOWLEDGE_BASE, "detail:" + knowledgeBaseId,
                OcKnowledgeBase.class, loader);
    }

    public List<OcCustomerOrg> getCustomerOrgOptions(
            Supplier<List<OcCustomerOrg>> loader) {
        return getOrLoad(CacheRegion.CUSTOMER_ORG, "options", List.class, loader);
    }

    public OcCustomerOrg getCustomerOrg(Long customerOrgId,
                                        Supplier<OcCustomerOrg> loader) {
        return getOrLoad(CacheRegion.CUSTOMER_ORG, "detail:" + customerOrgId,
                OcCustomerOrg.class, loader);
    }

    public List<OcDevice> getDeviceOptions(Long customerOrgId,
                                           Supplier<List<OcDevice>> loader) {
        String scope = customerOrgId == null ? "all" : "customer-org:" + customerOrgId;
        return getOrLoad(CacheRegion.DEVICE, "options:" + scope, List.class, loader);
    }

    public OcDevice getDevice(Long deviceId, Supplier<OcDevice> loader) {
        return getOrLoad(CacheRegion.DEVICE, "detail:" + deviceId,
                OcDevice.class, loader);
    }

    /**
     * 品牌名称或状态会影响型号、知识库和设备的关联展示。
     */
    public void invalidateBrands() {
        invalidateAfterCommit(CacheRegion.BRAND, CacheRegion.PRODUCT_MODEL,
                CacheRegion.KNOWLEDGE_BASE, CacheRegion.DEVICE);
    }

    /**
     * 型号名称或状态会影响知识库和设备的关联展示。
     */
    public void invalidateProductModels() {
        invalidateAfterCommit(CacheRegion.PRODUCT_MODEL, CacheRegion.KNOWLEDGE_BASE,
                CacheRegion.DEVICE);
    }

    public void invalidateKnowledgeBases() {
        invalidateAfterCommit(CacheRegion.KNOWLEDGE_BASE);
    }

    /**
     * 客户名称或状态会出现在设备关联结果中，因此同时换代设备区域。
     */
    public void invalidateCustomerOrgs() {
        invalidateAfterCommit(CacheRegion.CUSTOMER_ORG, CacheRegion.DEVICE);
    }

    public void invalidateDevices() {
        invalidateAfterCommit(CacheRegion.DEVICE);
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrLoad(CacheRegion region, String logicalKey,
                            Class<?> expectedType, Supplier<T> loader) {
        if (!properties.isEnabled()) {
            return loader.get();
        }

        Long revision = resolveRevision(region);
        if (revision == null) {
            return loader.get();
        }

        String cacheKey = dataKey(region, revision, logicalKey);
        CacheLookup firstLookup = read(cacheKey, expectedType);
        if (firstLookup.hit()) {
            return (T) firstLookup.value();
        }
        if (!firstLookup.redisAvailable()) {
            return loader.get();
        }

        synchronized (lockFor(cacheKey)) {
            CacheLookup secondLookup = read(cacheKey, expectedType);
            if (secondLookup.hit()) {
                return (T) secondLookup.value();
            }

            T value = loader.get();
            if (value != null) {
                write(cacheKey, value);
            }
            return value;
        }
    }

    private CacheLookup read(String key, Class<?> expectedType) {
        try {
            Object value = redisCache.getCacheObject(key);
            if (value == null) {
                log.debug("OfficeCare reference cache miss. key={}", key);
                return CacheLookup.miss(true);
            }
            if (!expectedType.isInstance(value)) {
                log.warn("Discarding an incompatible OfficeCare reference cache entry. key={}, type={}",
                        key, value.getClass().getName());
                safeDelete(key);
                return CacheLookup.miss(true);
            }
            log.debug("OfficeCare reference cache hit. key={}", key);
            return CacheLookup.hit(value);
        } catch (RuntimeException exception) {
            log.warn("OfficeCare reference cache read failed; falling back to MySQL. key={}, error={}",
                    key, exception.getClass().getSimpleName());
            return CacheLookup.miss(false);
        }
    }

    private void write(String key, Object value) {
        try {
            long ttlSeconds = properties.getReferenceTtl().getSeconds();
            redisCache.setCacheObject(key, value, Math.toIntExact(ttlSeconds), TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            log.warn("OfficeCare reference cache write failed; returning MySQL data. key={}, error={}",
                    key, exception.getClass().getSimpleName());
        }
    }

    private Long resolveRevision(CacheRegion region) {
        if (isInvalidationPending(region)) {
            return retryPendingInvalidation(region);
        }
        try {
            Object value = redisCache.getCacheObject(revisionKey(region));
            if (value == null) {
                return 0L;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            log.warn("OfficeCare reference cache revision is invalid; bypassing cache. region={}, type={}",
                    region.keySegment, value.getClass().getName());
            markInvalidationPending(region);
            return null;
        } catch (RuntimeException exception) {
            log.warn("OfficeCare reference cache revision read failed; falling back to MySQL. region={}, error={}",
                    region.keySegment, exception.getClass().getSimpleName());
            return null;
        }
    }

    private Long retryPendingInvalidation(CacheRegion region) {
        try {
            long revision = redisCache.increment(revisionKey(region));
            clearInvalidationPending(region);
            log.info("OfficeCare reference cache recovered and changed revision. region={}, revision={}",
                    region.keySegment, revision);
            return revision;
        } catch (RuntimeException exception) {
            log.warn("OfficeCare reference cache invalidation is still pending. region={}, error={}",
                    region.keySegment, exception.getClass().getSimpleName());
            return null;
        }
    }

    private void invalidateAfterCommit(CacheRegion... regions) {
        CacheRegion[] regionsToInvalidate = regions.clone();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            invalidateNow(regionsToInvalidate);
                        }
                    });
            return;
        }
        invalidateNow(regionsToInvalidate);
    }

    private void invalidateNow(CacheRegion... regions) {
        if (!properties.isEnabled()) {
            return;
        }
        for (CacheRegion region : regions) {
            try {
                long revision = redisCache.increment(revisionKey(region));
                clearInvalidationPending(region);
                log.debug("OfficeCare reference cache invalidated. region={}, revision={}",
                        region.keySegment, revision);
            } catch (RuntimeException exception) {
                markInvalidationPending(region);
                log.warn("OfficeCare reference cache invalidation failed; cache region will be bypassed. region={}, error={}",
                        region.keySegment, exception.getClass().getSimpleName());
            }
        }
    }

    private void safeDelete(String key) {
        try {
            redisCache.deleteObject(key);
        } catch (RuntimeException exception) {
            log.warn("OfficeCare invalid cache entry could not be deleted. key={}, error={}",
                    key, exception.getClass().getSimpleName());
        }
    }

    private Object lockFor(String key) {
        return loadLocks[(key.hashCode() & Integer.MAX_VALUE) % loadLocks.length];
    }

    private String revisionKey(CacheRegion region) {
        return KEY_PREFIX + ":revision:" + region.keySegment;
    }

    private String dataKey(CacheRegion region, long revision, String logicalKey) {
        return KEY_PREFIX + ":" + region.keySegment + ":r" + revision + ":" + logicalKey;
    }

    private synchronized boolean isInvalidationPending(CacheRegion region) {
        return invalidationPending[region.ordinal()];
    }

    private synchronized void markInvalidationPending(CacheRegion region) {
        invalidationPending[region.ordinal()] = true;
    }

    private synchronized void clearInvalidationPending(CacheRegion region) {
        invalidationPending[region.ordinal()] = false;
    }

    private enum CacheRegion {
        BRAND("brand"),
        PRODUCT_MODEL("product-model"),
        KNOWLEDGE_BASE("knowledge-base"),
        CUSTOMER_ORG("customer-org"),
        DEVICE("device");

        private final String keySegment;

        CacheRegion(String keySegment) {
            this.keySegment = keySegment;
        }
    }

    private record CacheLookup(boolean hit, boolean redisAvailable, Object value) {
        private static CacheLookup hit(Object value) {
            return new CacheLookup(true, true, value);
        }

        private static CacheLookup miss(boolean redisAvailable) {
            return new CacheLookup(false, redisAvailable, null);
        }
    }
}
