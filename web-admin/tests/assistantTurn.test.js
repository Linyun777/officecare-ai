import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createAssistantTurnRequests,
  mergeAssistantTurnMessages
} from '../src/utils/assistantTurn.js'

function fixture(send = async () => ({ data: {} })) {
  let ids = 0
  return createAssistantTurnRequests({ send, createId: () => `turn-${++ids}` })
}

test('failed requests reuse the exact original turn and question', async () => {
  const calls = []
  const turns = fixture(async (conversationId, request) => {
    calls.push({ conversationId, ...request })
    if (calls.length === 1) {
      throw new Error('connection lost')
    }
    return { data: { assistantMessageId: 102 } }
  })
  const original = turns.prepare(15, '  卡纸怎么办？  ')
  await assert.rejects(turns.submit(original), /connection lost/)
  const retry = turns.prepare(15, '卡纸怎么办？')
  assert.equal(retry, original)
  await turns.submit(retry)
  assert.deepEqual(calls[0], calls[1])
  turns.complete(retry)
  assert.equal(turns.getPending(15), undefined)
})

test('simultaneous submissions share one in-flight request', async () => {
  let calls = 0
  let finish
  const turns = fixture(() => {
    calls++
    return new Promise(resolve => {
      finish = resolve
    })
  })
  const turn = turns.prepare(15, '检查打印问题')
  const first = turns.submit(turn)
  const second = turns.submit(turn)
  assert.equal(first, second)
  await Promise.resolve()
  assert.equal(calls, 1)
  finish({ data: {} })
  await Promise.all([first, second])
})

test('an unresolved turn cannot be reused for an edited question', () => {
  const turns = fixture()
  turns.prepare(15, '检查打印问题')
  assert.throws(() => turns.prepare(15, '另一个问题'), /上一条/)
})

test('conversations keep independent pending requests', async () => {
  const turns = fixture()
  const first = turns.prepare(15, '检查打印问题')
  const other = turns.prepare(16, '检查打印问题')
  assert.notEqual(first.turnId, other.turnId)
  turns.complete(first)
  assert.equal(turns.getPending(16), other)
  await turns.submit(other)
})

test('a completed question starts a new turn even with the same text', () => {
  const turns = fixture()
  const first = turns.prepare(15, '检查打印问题')
  turns.complete(first)
  assert.notEqual(turns.prepare(15, first.question).turnId, first.turnId)
})

test('response validation failure can still retry the original request', async () => {
  let calls = 0
  const turns = fixture(async () => {
    calls++
    return { data: null }
  })
  const turn = turns.prepare(15, '检查打印问题')
  await turns.submit(turn)
  assert.equal(turns.getPending(15), turn)
  await turns.submit(turn)
  assert.equal(calls, 2)
})

test('history reconciles only the matching persisted assistant turn', () => {
  const turns = fixture()
  const turn = turns.prepare(15, '检查打印问题')
  turns.reconcile(15, [{ role: 'user', turnId: turn.turnId }])
  assert.equal(turns.getPending(15), turn)
  turns.reconcile(15, [{ role: 'assistant', turnId: 'different' }])
  assert.equal(turns.getPending(15), turn)
  turns.reconcile(15, [{ role: 'assistant', turnId: turn.turnId }])
  assert.equal(turns.getPending(15), undefined)
})

test('stale completion cannot discard a newer pending request', async () => {
  const turns = fixture()
  const old = turns.prepare(15, '检查打印问题')
  turns.complete(old)
  const current = turns.prepare(15, '检查另一个问题')
  turns.complete(old)
  assert.equal(turns.getPending(15), current)
  await assert.rejects(turns.submit(old), /已完成/)
})

test('replayed results replace temporary and persisted copies once', () => {
  const turn = { turnId: 'turn-1' }
  const other = { messageId: 1, turnId: 'previous', role: 'assistant' }
  const incoming = [
    { messageId: 101, turnId: 'turn-1', role: 'user' },
    { messageId: 102, turnId: 'turn-1', role: 'assistant' }
  ]
  const messages = [other, { messageId: 'temp-1', turnId: 'turn-1' }, ...incoming]
  const result = mergeAssistantTurnMessages(messages, turn, incoming)
  assert.deepEqual(result, [other, ...incoming])
  assert.deepEqual(mergeAssistantTurnMessages(result, turn, incoming), result)
})
