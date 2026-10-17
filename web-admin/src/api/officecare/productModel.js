import request from '@/utils/request'

// 查询产品型号列表
export function listProductModel(query) {
  return request({
    url: '/officecare/product-model/list',
    method: 'get',
    params: query
  })
}

// 查询产品型号选项
export function listProductModelOptions(brandId) {
  return request({
    url: '/officecare/product-model/options',
    method: 'get',
    params: { brandId }
  })
}

// 查询产品型号详情
export function getProductModel(modelId) {
  return request({
    url: '/officecare/product-model/' + modelId,
    method: 'get'
  })
}

// 新增产品型号
export function addProductModel(data) {
  return request({
    url: '/officecare/product-model',
    method: 'post',
    data
  })
}

// 修改产品型号
export function updateProductModel(data) {
  return request({
    url: '/officecare/product-model',
    method: 'put',
    data
  })
}

// 删除产品型号
export function delProductModel(modelIds) {
  return request({
    url: '/officecare/product-model/' + modelIds,
    method: 'delete'
  })
}
