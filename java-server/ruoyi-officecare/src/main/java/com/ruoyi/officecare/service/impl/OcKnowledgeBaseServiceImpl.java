package com.ruoyi.officecare.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.officecare.cache.OfficeCareReferenceCache;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.domain.OcProductModel;
import com.ruoyi.officecare.mapper.OcBrandMapper;
import com.ruoyi.officecare.mapper.OcDocumentMapper;
import com.ruoyi.officecare.mapper.OcKnowledgeBaseMapper;
import com.ruoyi.officecare.mapper.OcProductModelMapper;
import com.ruoyi.officecare.service.IOcKnowledgeBaseService;

/**
 * OfficeCare知识库服务实现。
 *
 * @author officecare
 */
@Service
public class OcKnowledgeBaseServiceImpl implements IOcKnowledgeBaseService {
    private static final String NORMAL_STATUS = "0";

    private final OcKnowledgeBaseMapper knowledgeBaseMapper;

    private final OcBrandMapper brandMapper;

    private final OcProductModelMapper productModelMapper;

    private final OcDocumentMapper documentMapper;

    private final OfficeCareReferenceCache referenceCache;

    public OcKnowledgeBaseServiceImpl(OcKnowledgeBaseMapper knowledgeBaseMapper,
                                      OcBrandMapper brandMapper, OcProductModelMapper productModelMapper,
                                      OcDocumentMapper documentMapper,
                                      OfficeCareReferenceCache referenceCache) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.brandMapper = brandMapper;
        this.productModelMapper = productModelMapper;
        this.documentMapper = documentMapper;
        this.referenceCache = referenceCache;
    }

    @Override
    public List<OcKnowledgeBase> selectKnowledgeBaseList(OcKnowledgeBase knowledgeBase) {
        return knowledgeBaseMapper.selectKnowledgeBaseList(knowledgeBase);
    }

    @Override
    public List<OcKnowledgeBase> selectKnowledgeBaseOptions() {
        return referenceCache.getKnowledgeBaseOptions(
                knowledgeBaseMapper::selectKnowledgeBaseOptions);
    }

    @Override
    public OcKnowledgeBase selectKnowledgeBaseById(Long knowledgeBaseId) {
        return referenceCache.getKnowledgeBase(knowledgeBaseId,
                () -> knowledgeBaseMapper.selectKnowledgeBaseById(knowledgeBaseId));
    }

    @Override
    public int insertKnowledgeBase(OcKnowledgeBase knowledgeBase) {
        normalizeKnowledgeBase(knowledgeBase);
        validateProductRelation(knowledgeBase.getBrandId(), knowledgeBase.getModelId(), true);
        validateCodeUnique(knowledgeBase);
        int rows = knowledgeBaseMapper.insertKnowledgeBase(knowledgeBase);
        if (rows > 0) {
            referenceCache.invalidateKnowledgeBases();
        }
        return rows;
    }

    @Override
    public int updateKnowledgeBase(OcKnowledgeBase knowledgeBase) {
        if (knowledgeBase.getKnowledgeBaseId() == null) {
            throw new ServiceException("知识库ID不能为空");
        }
        OcKnowledgeBase existing = knowledgeBaseMapper.selectKnowledgeBaseById(
                knowledgeBase.getKnowledgeBaseId());
        if (existing == null) {
            throw new ServiceException("知识库不存在或已被删除");
        }

        normalizeKnowledgeBase(knowledgeBase);
        boolean relationChanged = !existing.getBrandId().equals(knowledgeBase.getBrandId())
                || !existing.getModelId().equals(knowledgeBase.getModelId());
        validateProductRelation(knowledgeBase.getBrandId(), knowledgeBase.getModelId(), relationChanged);
        validateCodeUnique(knowledgeBase);
        int rows = knowledgeBaseMapper.updateKnowledgeBase(knowledgeBase);
        if (rows > 0) {
            referenceCache.invalidateKnowledgeBases();
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteKnowledgeBaseByIds(Long[] knowledgeBaseIds, String updateBy) {
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            OcKnowledgeBase knowledgeBase = knowledgeBaseMapper.selectKnowledgeBaseById(knowledgeBaseId);
            if (knowledgeBase == null) {
                continue;
            }
            if (documentMapper.countDocumentByKnowledgeBaseId(knowledgeBaseId) > 0) {
                throw new ServiceException("知识库“" + knowledgeBase.getKnowledgeBaseName()
                        + "”下存在文档，不能删除");
            }
        }
        int rows = knowledgeBaseMapper.deleteKnowledgeBaseByIds(knowledgeBaseIds, updateBy);
        if (rows > 0) {
            referenceCache.invalidateKnowledgeBases();
        }
        return rows;
    }

    private void normalizeKnowledgeBase(OcKnowledgeBase knowledgeBase) {
        knowledgeBase.setKnowledgeBaseCode(StringUtils.trim(knowledgeBase.getKnowledgeBaseCode()));
        knowledgeBase.setKnowledgeBaseName(StringUtils.trim(knowledgeBase.getKnowledgeBaseName()));
        knowledgeBase.setDescription(StringUtils.trim(knowledgeBase.getDescription()));
        knowledgeBase.setRemark(StringUtils.trim(knowledgeBase.getRemark()));
        if (StringUtils.isEmpty(knowledgeBase.getStatus())) {
            knowledgeBase.setStatus(NORMAL_STATUS);
        }
        if (knowledgeBase.getSortOrder() == null) {
            knowledgeBase.setSortOrder(0);
        }
    }

    private void validateProductRelation(Long brandId, Long modelId, boolean requireActive) {
        OcBrand brand = brandMapper.selectBrandById(brandId);
        if (brand == null) {
            throw new ServiceException("所属品牌不存在或已被删除");
        }
        OcProductModel productModel = productModelMapper.selectProductModelById(modelId);
        if (productModel == null) {
            throw new ServiceException("所属产品型号不存在或已被删除");
        }
        if (!brandId.equals(productModel.getBrandId())) {
            throw new ServiceException("所选产品型号不属于该品牌");
        }
        if (requireActive && (!NORMAL_STATUS.equals(brand.getStatus())
                || !NORMAL_STATUS.equals(productModel.getStatus()))) {
            throw new ServiceException("新增或更换关联时，品牌和产品型号必须处于正常状态");
        }
    }

    private void validateCodeUnique(OcKnowledgeBase knowledgeBase) {
        OcKnowledgeBase existing = knowledgeBaseMapper.selectKnowledgeBaseByCode(
                knowledgeBase.getKnowledgeBaseCode());
        if (existing != null && !existing.getKnowledgeBaseId().equals(
                knowledgeBase.getKnowledgeBaseId())) {
            throw new ServiceException("知识库编码“" + knowledgeBase.getKnowledgeBaseCode()
                    + "”已存在或曾被使用");
        }
    }
}
