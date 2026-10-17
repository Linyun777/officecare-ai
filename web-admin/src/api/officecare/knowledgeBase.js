import request from '@/utils/request'

// 查询知识库列表
export function listKnowledgeBase(query) {
  return request({
    url: '/officecare/knowledge-base/list',
    method: 'get',
    params: query
  })
}

// 查询启用的知识库选项
export function listKnowledgeBaseOptions() {
  return request({
    url: '/officecare/knowledge-base/options',
    method: 'get'
  })
}

// 查询知识库详情
export function getKnowledgeBase(knowledgeBaseId) {
  return request({
    url: '/officecare/knowledge-base/' + knowledgeBaseId,
    method: 'get'
  })
}

// 新增知识库
export function addKnowledgeBase(data) {
  return request({
    url: '/officecare/knowledge-base',
    method: 'post',
    data
  })
}

// 修改知识库
export function updateKnowledgeBase(data) {
  return request({
    url: '/officecare/knowledge-base',
    method: 'put',
    data
  })
}

// 删除知识库
export function delKnowledgeBase(knowledgeBaseIds) {
  return request({
    url: '/officecare/knowledge-base/' + knowledgeBaseIds,
    method: 'delete'
  })
}
