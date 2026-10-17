package com.ruoyi.officecare.mapper;

import java.util.List;
import com.ruoyi.officecare.domain.OcWorkOrderRecord;

/** OfficeCare工单处理记录数据访问层。 */
public interface OcWorkOrderRecordMapper
{
    public List<OcWorkOrderRecord> selectRecordListByWorkOrderId(
        Long workOrderId);

    public int insertWorkOrderRecord(OcWorkOrderRecord record);
}
