package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcProductModel;

/**
 * OfficeCare产品型号服务
 *
 * @author officecare
 */
public interface IOcProductModelService
{
    public List<OcProductModel> selectProductModelList(OcProductModel productModel);

    public List<OcProductModel> selectProductModelOptions(Long brandId);

    public OcProductModel selectProductModelById(Long modelId);

    public int insertProductModel(OcProductModel productModel);

    public int updateProductModel(OcProductModel productModel);

    public int deleteProductModelByIds(Long[] modelIds, String updateBy);
}
