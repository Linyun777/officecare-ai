import request from '@/utils/request'

// 查询售后工单列表
export function listWorkOrder(query) {
  return request({
    url: '/officecare/work-order/list',
    method: 'get',
    params: query
  })
}

// 查询工单详情（包含处理时间线）
export function getWorkOrder(workOrderId) {
  return request({
    url: `/officecare/work-order/${workOrderId}`,
    method: 'get'
  })
}

// 查询可指派的售后人员
export function listWorkOrderAssigneeOptions() {
  return request({
    url: '/officecare/work-order/assignee-options',
    method: 'get'
  })
}

// 新增工单
export function addWorkOrder(data) {
  return request({
    url: '/officecare/work-order',
    method: 'post',
    data
  })
}

// 修改待处理工单
export function updateWorkOrder(data) {
  return request({
    url: '/officecare/work-order',
    method: 'put',
    data
  })
}

// 调整负责人
export function assignWorkOrder(workOrderId, data) {
  return request({
    url: `/officecare/work-order/${workOrderId}/assignee`,
    method: 'put',
    data
  })
}

// 流转工单状态
export function transitionWorkOrder(workOrderId, data) {
  return request({
    url: `/officecare/work-order/${workOrderId}/status`,
    method: 'put',
    data
  })
}

// 新增处理记录
export function addWorkOrderRecord(workOrderId, data) {
  return request({
    url: `/officecare/work-order/${workOrderId}/records`,
    method: 'post',
    data
  })
}

// 基于设备型号知识库生成AI处理建议
export function generateWorkOrderAiSuggestion(workOrderId) {
  return request({
    url: `/officecare/work-order/${workOrderId}/ai-suggestion`,
    method: 'post'
  })
}

// 删除待处理工单
export function delWorkOrder(workOrderIds) {
  return request({
    url: `/officecare/work-order/${workOrderIds}`,
    method: 'delete'
  })
}
