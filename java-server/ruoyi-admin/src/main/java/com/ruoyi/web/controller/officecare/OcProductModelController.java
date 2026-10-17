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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.officecare.domain.OcProductModel;
import com.ruoyi.officecare.service.IOcProductModelService;

/**
 * OfficeCare产品型号管理
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/product-model")
public class OcProductModelController extends BaseController {
    @Autowired
    private IOcProductModelService productModelService;

    /**
     * 分页查询产品型号
     * @param productModel
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:model:list')")
    @GetMapping("/list")
    public TableDataInfo list(OcProductModel productModel) {
        startPage();
        List<OcProductModel> list = productModelService.selectProductModelList(productModel);
        return getDataTable(list);
    }

    /**
     * 获取产品型号下拉选项
     * @param brandId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:model:list')")
    @GetMapping("/options")
    public AjaxResult options(@RequestParam(required = false) Long brandId) {
        return success(productModelService.selectProductModelOptions(brandId));
    }

    /**
     * 获取产品型号详情
     * @param modelId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:model:query')")
    @GetMapping("/{modelId}")
    public AjaxResult getInfo(@PathVariable Long modelId) {
        return success(productModelService.selectProductModelById(modelId));
    }

    /**
     * 新增产品型号
     * @param productModel
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:model:add')")
    @Log(title = "OfficeCare产品型号", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OcProductModel productModel) {
        productModel.setCreateBy(getUsername());
        return toAjax(productModelService.insertProductModel(productModel));
    }

    /**
     * 编辑产品型号信息
     * @param productModel
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:model:edit')")
    @Log(title = "OfficeCare产品型号", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OcProductModel productModel) {
        productModel.setUpdateBy(getUsername());
        return toAjax(productModelService.updateProductModel(productModel));
    }

    /**
     * 删除产品型号
     * @param modelIds
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:model:remove')")
    @Log(title = "OfficeCare产品型号", businessType = BusinessType.DELETE)
    @DeleteMapping("/{modelIds}")
    public AjaxResult remove(@PathVariable Long[] modelIds) {
        return toAjax(productModelService.deleteProductModelByIds(modelIds, getUsername()));
    }
}
