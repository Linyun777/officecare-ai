package com.ruoyi.web.controller.officecare;

import java.util.List;
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
import com.ruoyi.officecare.domain.OcCustomerOrg;
import com.ruoyi.officecare.service.IOcCustomerOrgService;

/**
 * OfficeCare客户单位管理。
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/customer-org")
public class OcCustomerOrgController extends BaseController
{
    private final IOcCustomerOrgService customerOrgService;

    public OcCustomerOrgController(IOcCustomerOrgService customerOrgService)
    {
        this.customerOrgService = customerOrgService;
    }

    /**
     * 分页查询客户单位。
     */
    @PreAuthorize("@ss.hasPermi('officecare:customer:list')")
    @GetMapping("/list")
    public TableDataInfo list(OcCustomerOrg customerOrg)
    {
        startPage();
        List<OcCustomerOrg> list = customerOrgService.selectCustomerOrgList(
            customerOrg);
        return getDataTable(list);
    }

    /**
     * 查询可选择的正常客户单位。
     */
    @PreAuthorize("@ss.hasPermi('officecare:customer:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return success(customerOrgService.selectCustomerOrgOptions());
    }

    /**
     * 查询客户单位详情。
     */
    @PreAuthorize("@ss.hasPermi('officecare:customer:query')")
    @GetMapping("/{customerOrgId}")
    public AjaxResult getInfo(@PathVariable Long customerOrgId)
    {
        return success(customerOrgService.selectCustomerOrgById(customerOrgId));
    }

    /**
     * 新增客户单位。
     */
    @PreAuthorize("@ss.hasPermi('officecare:customer:add')")
    @Log(title = "OfficeCare客户单位", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OcCustomerOrg customerOrg)
    {
        customerOrg.setCreateBy(getUsername());
        return toAjax(customerOrgService.insertCustomerOrg(customerOrg));
    }

    /**
     * 修改客户单位。
     */
    @PreAuthorize("@ss.hasPermi('officecare:customer:edit')")
    @Log(title = "OfficeCare客户单位", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OcCustomerOrg customerOrg)
    {
        customerOrg.setUpdateBy(getUsername());
        return toAjax(customerOrgService.updateCustomerOrg(customerOrg));
    }

    /**
     * 软删除未关联设备的客户单位。
     */
    @PreAuthorize("@ss.hasPermi('officecare:customer:remove')")
    @Log(title = "OfficeCare客户单位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{customerOrgIds}")
    public AjaxResult remove(@PathVariable Long[] customerOrgIds)
    {
        return toAjax(customerOrgService.deleteCustomerOrgByIds(
            customerOrgIds, getUsername()));
    }
}
