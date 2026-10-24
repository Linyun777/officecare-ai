import request from '@/utils/request'

// Agent一轮可能包含澄清决策、RAG检索和模型生成，不能沿用若依全局10秒超时。
// Java侧Agent读取超时为240秒，前端额外预留60秒用于网络传输和消息持久化。
const AI_ANSWER_TIMEOUT = 300000

// 查询 AI 助手可选择的有效知识库
export function listAssistantKnowledgeBases() {
  return request({
    url: '/officecare/ai/knowledge-bases',
    method: 'get'
  })
}

// 查询当前用户的最近会话
export function listAiConversations() {
  return request({
    url: '/officecare/ai/conversations',
    method: 'get'
  })
}

// 创建一个绑定知识库的新会话
export function createAiConversation(data) {
  return request({
    url: '/officecare/ai/conversations',
    method: 'post',
    data
  })
}

// 查询当前用户指定会话的消息和引用
export function listAiConversationMessages(conversationId) {
  return request({
    url: `/officecare/ai/conversations/${conversationId}/messages`,
    method: 'get'
  })
}

// 在指定会话中执行一轮带短期记忆的Agent对话并持久化消息
export function answerAiConversation(conversationId, data) {
  return request({
    url: `/officecare/ai/conversations/${conversationId}/answer`,
    headers: { repeatSubmit: false },
    method: 'post',
    data,
    timeout: AI_ANSWER_TIMEOUT
  })
}

// 修改当前用户的会话标题
export function updateAiConversation(conversationId, data) {
  return request({
    url: `/officecare/ai/conversations/${conversationId}`,
    method: 'put',
    data
  })
}

// 软删除当前用户的会话
export function deleteAiConversation(conversationId) {
  return request({
    url: `/officecare/ai/conversations/${conversationId}`,
    method: 'delete'
  })
}

// 提交、切换或清除 AI 回答反馈
export function updateAiMessageFeedback(messageId, data) {
  return request({
    url: `/officecare/ai/messages/${messageId}/feedback`,
    method: 'put',
    data
  })
}

// 兼容原有的无状态单次问答调用
export function answerKnowledgeQuestion(data) {
  return request({
    url: '/officecare/ai/answer',
    method: 'post',
    data,
    timeout: AI_ANSWER_TIMEOUT
  })
}
