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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.officecare.domain.OcDevice;
import com.ruoyi.officecare.service.IOcDeviceService;

/**
 * OfficeCare设备档案管理。
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/device")
public class OcDeviceController extends BaseController
{
    private final IOcDeviceService deviceService;

    public OcDeviceController(IOcDeviceService deviceService)
    {
        this.deviceService = deviceService;
    }

    /**
     * 分页查询设备档案。
     */
    @PreAuthorize("@ss.hasPermi('officecare:device:list')")
    @GetMapping("/list")
    public TableDataInfo list(OcDevice device)
    {
        startPage();
        List<OcDevice> list = deviceService.selectDeviceList(device);
        return getDataTable(list);
    }

    /**
     * 查询可选设备，可按客户单位过滤。
     */
    @PreAuthorize("@ss.hasPermi('officecare:device:list')")
    @GetMapping("/options")
    public AjaxResult options(
        @RequestParam(required = false) Long customerOrgId)
    {
        return success(deviceService.selectDeviceOptions(customerOrgId));
    }

    /**
     * 查询设备档案详情。
     */
    @PreAuthorize("@ss.hasPermi('officecare:device:query')")
    @GetMapping("/{deviceId}")
    public AjaxResult getInfo(@PathVariable Long deviceId)
    {
        return success(deviceService.selectDeviceById(deviceId));
    }

    /**
     * 新增设备档案。
     */
    @PreAuthorize("@ss.hasPermi('officecare:device:add')")
    @Log(title = "OfficeCare设备档案", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OcDevice device)
    {
        device.setCreateBy(getUsername());
        return toAjax(deviceService.insertDevice(device));
    }

    /**
     * 修改设备档案。
     */
    @PreAuthorize("@ss.hasPermi('officecare:device:edit')")
    @Log(title = "OfficeCare设备档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OcDevice device)
    {
        device.setUpdateBy(getUsername());
        return toAjax(deviceService.updateDevice(device));
    }

    /**
     * 软删除设备档案。
     */
    @PreAuthorize("@ss.hasPermi('officecare:device:remove')")
    @Log(title = "OfficeCare设备档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deviceIds}")
    public AjaxResult remove(@PathVariable Long[] deviceIds)
    {
        return toAjax(deviceService.deleteDeviceByIds(
            deviceIds, getUsername()));
    }
}
