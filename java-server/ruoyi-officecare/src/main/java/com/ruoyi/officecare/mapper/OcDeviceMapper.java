package com.ruoyi.officecare.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.officecare.domain.OcDevice;

/**
 * OfficeCare设备档案数据访问层。
 *
 * @author officecare
 */
public interface OcDeviceMapper
{
    public List<OcDevice> selectDeviceList(OcDevice device);

    public List<OcDevice> selectDeviceOptions(Long customerOrgId);

    public OcDevice selectDeviceById(Long deviceId);

    public OcDevice selectDeviceBySerialNumber(String serialNumber);

    public int countDeviceByCustomerOrgId(Long customerOrgId);

    public int countDeviceByModelId(Long modelId);

    public int insertDevice(OcDevice device);

    public int updateDevice(OcDevice device);

    public int deleteDeviceByIds(@Param("deviceIds") Long[] deviceIds,
        @Param("updateBy") String updateBy);
}
