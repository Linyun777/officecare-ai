import request from '@/utils/request'

// 查询品牌列表
export function listBrand(query) {
  return request({
    url: '/officecare/brand/list',
    method: 'get',
    params: query
  })
}

// 查询品牌选项
export function listBrandOptions() {
  return request({
    url: '/officecare/brand/options',
    method: 'get'
  })
}

// 查询品牌详情
export function getBrand(brandId) {
  return request({
    url: '/officecare/brand/' + brandId,
    method: 'get'
  })
}

// 新增品牌
export function addBrand(data) {
  return request({
    url: '/officecare/brand',
    method: 'post',
    data
  })
}

// 修改品牌
export function updateBrand(data) {
  return request({
    url: '/officecare/brand',
    method: 'put',
    data
  })
}

// 删除品牌
export function delBrand(brandIds) {
  return request({
    url: '/officecare/brand/' + brandIds,
    method: 'delete'
  })
}
