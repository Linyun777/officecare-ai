import request from '@/utils/request'

// 查询知识文档列表
export function listDocument(query) {
  return request({
    url: '/officecare/document/list',
    method: 'get',
    params: query
  })
}

// 查询知识文档详情
export function getDocument(documentId) {
  return request({
    url: '/officecare/document/' + documentId,
    method: 'get'
  })
}

// 查询文档版本历史
export function listDocumentVersions(documentId) {
  return request({
    url: `/officecare/document/${documentId}/versions`,
    method: 'get'
  })
}

// 查询指定版本的解析任务
export function getDocumentProcessingTask(documentVersionId) {
  return request({
    url: `/officecare/document/versions/${documentVersionId}/task`,
    method: 'get'
  })
}

// 启动或重试指定版本的解析任务
export function processDocumentVersion(documentVersionId) {
  return request({
    url: `/officecare/document/versions/${documentVersionId}/parse`,
    method: 'post'
  })
}

// 查询指定版本的向量索引任务
export function getDocumentIndexTask(documentVersionId) {
  return request({
    url: `/officecare/document/versions/${documentVersionId}/index-task`,
    method: 'get'
  })
}

// 启动或重试指定版本的向量索引任务
export function indexDocumentVersion(documentVersionId) {
  return request({
    url: `/officecare/document/versions/${documentVersionId}/index`,
    method: 'post'
  })
}

// 上传新文档
export function uploadDocument(data) {
  return request({
    url: '/officecare/document/upload',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    },
    timeout: 60000
  })
}

// 上传文档新版本
export function uploadDocumentVersion(documentId, data) {
  return request({
    url: `/officecare/document/${documentId}/versions`,
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    },
    timeout: 60000
  })
}

// 修改文档元数据
export function updateDocument(data) {
  return request({
    url: '/officecare/document',
    method: 'put',
    data
  })
}

// 删除文档
export function delDocument(documentIds) {
  return request({
    url: '/officecare/document/' + documentIds,
    method: 'delete'
  })
}

// 下载指定文档版本
export function downloadDocumentVersion(documentVersionId) {
  return request({
    url: `/officecare/document/versions/${documentVersionId}/download`,
    method: 'get',
    responseType: 'blob',
    timeout: 60000
  })
}
