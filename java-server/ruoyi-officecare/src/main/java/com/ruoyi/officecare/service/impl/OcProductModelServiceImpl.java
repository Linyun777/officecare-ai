package com.ruoyi.officecare.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.cache.OfficeCareReferenceCache;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.domain.OcProductModel;
import com.ruoyi.officecare.mapper.OcBrandMapper;
import com.ruoyi.officecare.mapper.OcDeviceMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.mapper.OcProductModelMapper;
import com.ruoyi.officecare.service.IOcProductModelService;

/**
 * OfficeCare产品型号服务实现
 *
 * @author officecare
 */
@Service
public class OcProductModelServiceImpl implements IOcProductModelService
{
    private static final String NORMAL_STATUS = "0";

    private final OcProductModelMapper productModelMapper;

    private final OcBrandMapper brandMapper;

    private final OcKnowledgeBaseMapper knowledgeBaseMapper;

    private final OcDeviceMapper deviceMapper;

    private final OfficeCareReferenceCache referenceCache;

    public OcProductModelServiceImpl(OcProductModelMapper productModelMapper,
            OcBrandMapper brandMapper, OcKnowledgeBaseMapper knowledgeBaseMapper,
            OcDeviceMapper deviceMapper, OfficeCareReferenceCache referenceCache)
    {
        this.productModelMapper = productModelMapper;
        this.brandMapper = brandMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.deviceMapper = deviceMapper;
        this.referenceCache = referenceCache;
    }

    @Override
    public List<OcProductModel> selectProductModelList(OcProductModel productModel)
    {
        return productModelMapper.selectProductModelList(productModel);
    }

    @Override
    public List<OcProductModel> selectProductModelOptions(Long brandId)
    {
        return referenceCache.getProductModelOptions(brandId,
            () -> productModelMapper.selectProductModelOptions(brandId));
    }

    @Override
    public OcProductModel selectProductModelById(Long modelId)
    {
        return referenceCache.getProductModel(modelId,
            () -> productModelMapper.selectProductModelById(modelId));
    }

    @Override
    public int insertProductModel(OcProductModel productModel)
    {
        normalizeProductModel(productModel);
        validateSelectableBrand(productModel.getBrandId());
        validateModelCodeUnique(productModel);
        int rows = productModelMapper.insertProductModel(productModel);
        if (rows > 0)
        {
            referenceCache.invalidateProductModels();
        }
        return rows;
    }

    @Override
    public int updateProductModel(OcProductModel productModel)
    {
        if (productModel.getModelId() == null)
        {
            throw new ServiceException("产品型号ID不能为空");
        }
        OcProductModel existing = productModelMapper.selectProductModelById(productModel.getModelId());
        if (existing == null)
        {
            throw new ServiceException("产品型号不存在或已被删除");
        }

        normalizeProductModel(productModel);
        if (!existing.getBrandId().equals(productModel.getBrandId()))
        {
            if (deviceMapper.countDeviceByModelId(productModel.getModelId()) > 0)
            {
                throw new ServiceException("产品型号“" + existing.getModelName()
                    + "”已关联设备档案，不能更换所属品牌");
            }
            validateSelectableBrand(productModel.getBrandId());
        }
        else if (brandMapper.selectBrandById(productModel.getBrandId()) == null)
        {
            throw new ServiceException("所属品牌不存在或已被删除");
        }
        validateModelCodeUnique(productModel);
        int rows = productModelMapper.updateProductModel(productModel);
        if (rows > 0)
        {
            referenceCache.invalidateProductModels();
        }
        return rows;
    }

    @Override
    public int deleteProductModelByIds(Long[] modelIds, String updateBy)
    {
        for (Long modelId : modelIds)
        {
            OcProductModel productModel = productModelMapper.selectProductModelById(modelId);
            if (productModel != null && knowledgeBaseMapper.countKnowledgeBaseByModelId(modelId) > 0)
            {
                throw new ServiceException("产品型号“" + productModel.getModelName()
                    + "”已关联知识库，不能删除");
            }
            if (productModel != null && deviceMapper.countDeviceByModelId(modelId) > 0)
            {
                throw new ServiceException("产品型号“" + productModel.getModelName()
                    + "”已关联设备档案，不能删除");
            }
        }
        int rows = productModelMapper.deleteProductModelByIds(modelIds, updateBy);
        if (rows > 0)
        {
            referenceCache.invalidateProductModels();
        }
        return rows;
    }

    private void normalizeProductModel(OcProductModel productModel)
    {
        productModel.setModelCode(StringUtils.trim(productModel.getModelCode()));
        productModel.setModelName(StringUtils.trim(productModel.getModelName()));
        productModel.setSeriesName(StringUtils.trim(productModel.getSeriesName()));
        productModel.setDescription(StringUtils.trim(productModel.getDescription()));
        productModel.setRemark(StringUtils.trim(productModel.getRemark()));
        if (StringUtils.isEmpty(productModel.getStatus()))
        {
            productModel.setStatus(NORMAL_STATUS);
        }
        if (productModel.getSortOrder() == null)
        {
            productModel.setSortOrder(0);
        }
    }

    private void validateSelectableBrand(Long brandId)
    {
        OcBrand brand = brandMapper.selectBrandById(brandId);
        if (brand == null)
        {
            throw new ServiceException("所属品牌不存在或已被删除");
        }
        if (!NORMAL_STATUS.equals(brand.getStatus()))
        {
            throw new ServiceException("所属品牌已停用，请先启用品牌");
        }
    }

    private void validateModelCodeUnique(OcProductModel productModel)
    {
        OcProductModel existing = productModelMapper.selectProductModelByCode(
            productModel.getBrandId(), productModel.getModelCode());
        if (existing != null && !existing.getModelId().equals(productModel.getModelId()))
        {
            throw new ServiceException("该品牌下的型号编码“" + productModel.getModelCode() + "”已存在或曾被使用");
        }
    }
}
