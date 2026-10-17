package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcProductModel;

/**
 * OfficeCare产品型号数据访问层
 *
 * @author officecare
 */
public interface OcProductModelMapper
{
    public List<OcProductModel> selectProductModelList(OcProductModel productModel);

    public List<OcProductModel> selectProductModelOptions(Long brandId);

    public OcProductModel selectProductModelById(Long modelId);

    public OcProductModel selectProductModelByCode(@Param("brandId") Long brandId,
        @Param("modelCode") String modelCode);

    public long selectProductModelCount();

    public int countProductModelByBrandId(Long brandId);

    public int insertProductModel(OcProductModel productModel);

    public int updateProductModel(OcProductModel productModel);

    public int deleteProductModelByIds(@Param("modelIds") Long[] modelIds,
        @Param("updateBy") String updateBy);
}
