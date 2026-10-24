function createTurnId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, value => {
    const random = Math.floor(Math.random() * 16)
    return (value === 'x' ? random : ((random & 0x3) | 0x8)).toString(16)
  })
}

// 未完成的原问题属于当前页面的会话状态，不能因网络失败生成另一轮请求。
export function createAssistantTurnRequests({
  send,
  pendingTurns = new Map(),
  createId = createTurnId
}) {
  const inFlight = new Map()

  function getPending(conversationId) {
    return pendingTurns.get(conversationId)
  }

  function prepare(conversationId, question) {
    const normalizedQuestion = question.trim()
    const pending = getPending(conversationId)
    if (pending) {
      if (pending.question !== normalizedQuestion) {
        throw new Error('请先重试上一条尚未完成的问题。')
      }
      return pending
    }
    const turn = Object.freeze({
      conversationId,
      turnId: createId(),
      question: normalizedQuestion
    })
    pendingTurns.set(conversationId, turn)
    return turn
  }

  function submit(turn) {
    if (getPending(turn.conversationId)?.turnId !== turn.turnId) {
      return Promise.reject(new Error('该问题已完成，请刷新会话。'))
    }
    const running = inFlight.get(turn.conversationId)
    if (running) {
      return running
    }
    const request = Promise.resolve()
      .then(() => send(turn.conversationId, {
        turnId: turn.turnId,
        question: turn.question
      }))
      .finally(() => inFlight.delete(turn.conversationId))
    inFlight.set(turn.conversationId, request)
    return request
  }

  function complete(turn) {
    if (getPending(turn.conversationId)?.turnId === turn.turnId) {
      pendingTurns.delete(turn.conversationId)
    }
  }

  function reconcile(conversationId, messages) {
    const turn = getPending(conversationId)
    if (turn && messages.some(message => (
      message.turnId === turn.turnId && message.role === 'assistant'
    ))) {
      complete(turn)
    }
  }

  function forget(conversationId) {
    pendingTurns.delete(conversationId)
  }

  return { getPending, prepare, submit, complete, reconcile, forget }
}

export function mergeAssistantTurnMessages(messages, turn, incoming) {
  const incomingIds = new Set(incoming.map(message => String(message.messageId)))
  return messages.filter(message => (
    message.turnId !== turn.turnId
    && !incomingIds.has(String(message.messageId))
  )).concat(incoming)
}
