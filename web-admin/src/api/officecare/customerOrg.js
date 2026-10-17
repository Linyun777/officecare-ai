import request from '@/utils/request'

// 查询客户单位列表
export function listCustomerOrg(query) {
  return request({
    url: '/officecare/customer-org/list',
    method: 'get',
    params: query
  })
}

// 查询正常客户单位选项
export function listCustomerOrgOptions() {
  return request({
    url: '/officecare/customer-org/options',
    method: 'get'
  })
}

// 查询客户单位详情
export function getCustomerOrg(customerOrgId) {
  return request({
    url: `/officecare/customer-org/${customerOrgId}`,
    method: 'get'
  })
}

// 新增客户单位
export function addCustomerOrg(data) {
  return request({
    url: '/officecare/customer-org',
    method: 'post',
    data
  })
}

// 修改客户单位
export function updateCustomerOrg(data) {
  return request({
    url: '/officecare/customer-org',
    method: 'put',
    data
  })
}

// 删除客户单位
export function delCustomerOrg(customerOrgIds) {
  return request({
    url: `/officecare/customer-org/${customerOrgIds}`,
    method: 'delete'
  })
}
