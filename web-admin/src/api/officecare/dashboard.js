import request from '@/utils/request'

// 查询OfficeCare工作台摘要
export function getDashboardSummary() {
  return request({
    url: '/officecare/dashboard/summary',
    method: 'get'
  })
}

// 查询最近待人工处理的RabbitMQ异常消息
export function getMqDeadLetters() {
  return request({
    url: '/officecare/dashboard/mq/dead-letters',
    method: 'get'
  })
}

// 人工重新投递发布失败或消费死信
export function replayMqDeadLetter(outboxId) {
  return request({
    url: `/officecare/dashboard/mq/dead-letters/${outboxId}/replay`,
    method: 'post'
  })
}
