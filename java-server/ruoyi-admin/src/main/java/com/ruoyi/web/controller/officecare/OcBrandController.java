package com.ruoyi.web.controller.officecare;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.officecare.domain.OcBrand;
import com.ruoyi.officecare.service.IOcBrandService;

/**
 * OfficeCare品牌管理
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/brand")
public class OcBrandController extends BaseController {
    @Autowired
    private IOcBrandService brandService;

    /**
     * 分页查询品牌
     *
     * @param brand
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:brand:list')")
    @GetMapping("/list")
    public TableDataInfo list(OcBrand brand) {
        startPage();
        List<OcBrand> list = brandService.selectBrandList(brand);
        return getDataTable(list);
    }

    /**
     * 获取品牌下拉选项
     *
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:brand:list')")
    @GetMapping("/options")
    public AjaxResult options() {
        return success(brandService.selectBrandOptions());
    }

    /**
     * 根据ID查询品牌详情
     *
     * @param brandId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:brand:query')")
    @GetMapping("/{brandId}")
    public AjaxResult getInfo(@PathVariable Long brandId) {
        return success(brandService.selectBrandById(brandId));
    }

    /**
     * 新增品牌
     * @param brand
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:brand:add')")
    @Log(title = "OfficeCare品牌", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OcBrand brand) {
        brand.setCreateBy(getUsername());
        return toAjax(brandService.insertBrand(brand));
    }

    /**
     * 编辑品牌信息
     * @param brand
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:brand:edit')")
    @Log(title = "OfficeCare品牌", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OcBrand brand) {
        brand.setUpdateBy(getUsername());
        return toAjax(brandService.updateBrand(brand));
    }

    /**
     * 删除品牌
     * @param brandIds
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:brand:remove')")
    @Log(title = "OfficeCare品牌", businessType = BusinessType.DELETE)
    @DeleteMapping("/{brandIds}")
    public AjaxResult remove(@PathVariable Long[] brandIds) {
        return toAjax(brandService.deleteBrandByIds(brandIds, getUsername()));
    }
}
