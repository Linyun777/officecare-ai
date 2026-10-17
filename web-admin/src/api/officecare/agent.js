import request from '@/utils/request'

// 调用受控Agent分析设备故障并保存待确认草稿
export function analyzeDeviceIssue(data) {
  return request({
    url: '/officecare/agent/analyze',
    method: 'post',
    data,
    // Agent包含多轮工具调用和一次RAG生成，不能沿用全局10秒超时。
    // Java侧最长等待240秒，前端多预留60秒用于网络和草稿持久化。
    timeout: 300000
  })
}

// 查询当前用户的智能报修草稿
export function listAgentDraft(query) {
  return request({
    url: '/officecare/agent/draft/list',
    method: 'get',
    params: query
  })
}

// 查询当前用户的一条草稿及完整审计信息
export function getAgentDraft(draftId) {
  return request({
    url: `/officecare/agent/draft/${draftId}`,
    method: 'get'
  })
}

// 查询可指派售后人员
export function listAgentAssigneeOptions() {
  return request({
    url: '/officecare/agent/assignee-options',
    method: 'get'
  })
}

// 保存人工修改后的待确认草稿
export function updateAgentDraft(draftId, data) {
  return request({
    url: `/officecare/agent/draft/${draftId}`,
    method: 'put',
    data
  })
}

// 人工显式确认并幂等创建正式工单
export function confirmAgentDraft(draftId, data) {
  return request({
    url: `/officecare/agent/draft/${draftId}/confirm`,
    method: 'post',
    data
  })
}

// 软删除当前用户的待确认草稿，后端保留审计记录
export function deleteAgentDraft(draftIds) {
  return request({
    url: `/officecare/agent/draft/${draftIds}`,
    method: 'delete'
  })
}
