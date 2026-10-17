package com.ruoyi.officecare.service;

import java.util.List;
import com.ruoyi.officecare.domain.OcDevice;

/**
 * OfficeCare设备档案服务。
 *
 * @author officecare
 */
public interface IOcDeviceService
{
    public List<OcDevice> selectDeviceList(OcDevice device);

    public List<OcDevice> selectDeviceOptions(Long customerOrgId);

    public OcDevice selectDeviceById(Long deviceId);

    public int insertDevice(OcDevice device);

    public int updateDevice(OcDevice device);

    public int deleteDeviceByIds(Long[] deviceIds, String updateBy);
}
