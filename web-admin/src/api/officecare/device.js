import request from '@/utils/request'

// 查询设备档案列表
export function listDevice(query) {
  return request({
    url: '/officecare/device/list',
    method: 'get',
    params: query
  })
}

// 查询可选设备，可按客户单位过滤
export function listDeviceOptions(customerOrgId) {
  return request({
    url: '/officecare/device/options',
    method: 'get',
    params: { customerOrgId }
  })
}

// 查询设备档案详情
export function getDevice(deviceId) {
  return request({
    url: `/officecare/device/${deviceId}`,
    method: 'get'
  })
}

// 新增设备档案
export function addDevice(data) {
  return request({
    url: '/officecare/device',
    method: 'post',
    data
  })
}

// 修改设备档案
export function updateDevice(data) {
  return request({
    url: '/officecare/device',
    method: 'put',
    data
  })
}

// 删除设备档案
export function delDevice(deviceIds) {
  return request({
    url: `/officecare/device/${deviceIds}`,
    method: 'delete'
  })
}
