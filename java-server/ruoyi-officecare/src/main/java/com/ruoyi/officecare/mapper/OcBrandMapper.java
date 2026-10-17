package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcBrand;

/**
 * OfficeCare品牌数据访问层
 *
 * @author officecare
 */
public interface OcBrandMapper
{
    public List<OcBrand> selectBrandList(OcBrand brand);

    public List<OcBrand> selectBrandOptions();

    public OcBrand selectBrandById(Long brandId);

    public OcBrand selectBrandByCode(String brandCode);

    public long selectBrandCount();

    public int insertBrand(OcBrand brand);

    public int updateBrand(OcBrand brand);

    public int deleteBrandByIds(@Param("brandIds") Long[] brandIds, @Param("updateBy") String updateBy);
}
