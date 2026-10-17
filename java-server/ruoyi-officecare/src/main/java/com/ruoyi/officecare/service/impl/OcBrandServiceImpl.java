package com.ruoyi.officecare.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.cache.OfficeCareReferenceCache;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.mapper.OcBrandMapper;
import com.ruoyi.officecare.mapper.OcProductModelMapper;
import com.ruoyi.officecare.service.IOcBrandService;

/**
 * OfficeCare品牌服务实现
 *
 * @author officecare
 */
@Service
public class OcBrandServiceImpl implements IOcBrandService {
    private static final String NORMAL_STATUS = "0";

    private final OcBrandMapper brandMapper;

    private final OcProductModelMapper productModelMapper;

    private final OfficeCareReferenceCache referenceCache;

    public OcBrandServiceImpl(OcBrandMapper brandMapper,
            OcProductModelMapper productModelMapper,
            OfficeCareReferenceCache referenceCache) {
        this.brandMapper = brandMapper;
        this.productModelMapper = productModelMapper;
        this.referenceCache = referenceCache;
    }

    @Override
    public List<OcBrand> selectBrandList(OcBrand brand) {
        return brandMapper.selectBrandList(brand);
    }

    @Override
    public List<OcBrand> selectBrandOptions() {
        return referenceCache.getBrandOptions(brandMapper::selectBrandOptions);
    }

    @Override
    public OcBrand selectBrandById(Long brandId) {
        return referenceCache.getBrand(brandId,
                () -> brandMapper.selectBrandById(brandId));
    }

    @Override
    public int insertBrand(OcBrand brand) {
        normalizeBrand(brand);
        validateBrandCodeUnique(brand);
        int rows = brandMapper.insertBrand(brand);
        if (rows > 0) {
            referenceCache.invalidateBrands();
        }
        return rows;
    }

    @Override
    public int updateBrand(OcBrand brand) {
        if (brand.getBrandId() == null || brandMapper.selectBrandById(brand.getBrandId()) == null) {
            throw new ServiceException("品牌不存在或已被删除");
        }
        normalizeBrand(brand);
        validateBrandCodeUnique(brand);
        int rows = brandMapper.updateBrand(brand);
        if (rows > 0) {
            referenceCache.invalidateBrands();
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteBrandByIds(Long[] brandIds, String updateBy) {
        for (Long brandId : brandIds) {
            OcBrand brand = brandMapper.selectBrandById(brandId);
            if (brand == null) {
                continue;
            }
            if (productModelMapper.countProductModelByBrandId(brandId) > 0) {
                throw new ServiceException("品牌“" + brand.getBrandName() + "”下存在产品型号，不能删除");
            }
        }
        int rows = brandMapper.deleteBrandByIds(brandIds, updateBy);
        if (rows > 0) {
            referenceCache.invalidateBrands();
        }
        return rows;
    }

    private void normalizeBrand(OcBrand brand) {
        brand.setBrandCode(StringUtils.trim(brand.getBrandCode()));
        brand.setBrandName(StringUtils.trim(brand.getBrandName()));
        brand.setDescription(StringUtils.trim(brand.getDescription()));
        brand.setRemark(StringUtils.trim(brand.getRemark()));
        if (StringUtils.isEmpty(brand.getStatus())) {
            brand.setStatus(NORMAL_STATUS);
        }
        if (brand.getSortOrder() == null) {
            brand.setSortOrder(0);
        }
    }

    private void validateBrandCodeUnique(OcBrand brand) {
        OcBrand existing = brandMapper.selectBrandByCode(brand.getBrandCode());
        if (existing != null && !existing.getBrandId().equals(brand.getBrandId())) {
            throw new ServiceException("品牌编码“" + brand.getBrandCode() + "”已存在或曾被使用");
        }
    }
}
