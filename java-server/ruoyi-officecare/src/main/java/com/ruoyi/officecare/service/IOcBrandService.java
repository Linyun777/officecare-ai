package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcBrand;

/**
 * OfficeCare品牌服务
 *
 * @author officecare
 */
public interface IOcBrandService
{
    public List<OcBrand> selectBrandList(OcBrand brand);

    public List<OcBrand> selectBrandOptions();

    public OcBrand selectBrandById(Long brandId);

    public int insertBrand(OcBrand brand);

    public int updateBrand(OcBrand brand);

    public int deleteBrandByIds(Long[] brandIds, String updateBy);
}
