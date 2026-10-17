<template>
  <div class="app-container assistant-page">
    <el-card class="assistant-header" shadow="never">
      <div class="header-content">
        <div>
          <div class="eyebrow">OFFICECARE KNOWLEDGE COPILOT</div>
          <h1>打印机售后知识助手</h1>
          <p>助手会记住本次会话，逐步澄清现象并给出一项可验证的排查建议。</p>
        </div>
        <div class="header-tags">
          <el-tag type="success" effect="dark" size="large">LangChain Agent</el-tag>
          <el-tag type="warning" effect="dark" size="large">PostgreSQL 短期记忆</el-tag>
          <el-tag effect="dark" size="large">会话可追溯</el-tag>
        </div>
      </div>
    </el-card>

    <el-row :gutter="18">
      <el-col :xs="24" :lg="7">
        <el-card class="session-card" shadow="never">
          <template #header>
            <div class="session-heading">
              <div>
                <div class="card-title">我的会话</div>
                <span>仅显示当前登录用户的最近 100 条</span>
              </div>
              <el-button type="primary" icon="Plus" @click="openConversationDialog">
                新会话
              </el-button>
            </div>
          </template>

          <div v-loading="conversationLoading" class="session-list">
            <el-empty
              v-if="!conversationLoading && conversations.length === 0"
              description="还没有会话，请先创建"
              :image-size="72"
            />

            <div
              v-for="item in conversations"
              :key="item.conversationId"
              class="session-item"
              :class="{ active: item.conversationId === activeConversationId }"
              @click="selectConversation(item)"
            >
              <div class="session-main">
                <div class="session-title">{{ item.title }}</div>
                <div class="session-scope">
                  {{ item.knowledgeBaseName }}
                  <span v-if="item.modelName"> · {{ item.modelName }}</span>
                </div>
                <div class="session-time">
                  {{ formatTime(item.lastMessageTime || item.createTime) }}
                </div>
              </div>
              <div class="session-actions">
                <el-button
                  link
                  icon="Edit"
                  title="重命名"
                  @click.stop="renameConversation(item)"
                />
                <el-button
                  link
                  type="danger"
                  icon="Delete"
                  title="删除"
                  @click.stop="removeConversation(item)"
                />
              </div>
            </div>
          </div>

          <div v-if="activeConversation" class="scope-panel">
            <div class="scope-label">当前检索范围</div>
            <strong>{{ activeConversation.knowledgeBaseName }}</strong>
            <span>
              {{ activeConversation.brandName || '未设置品牌' }}
              ·
              {{ activeConversation.modelName || '未设置型号' }}
            </span>
            <p>知识库在创建会话时绑定，本会话中不可切换。</p>
          </div>

          <el-divider />

          <div class="example-title">可以这样问</div>
          <button
            v-for="example in examples"
            :key="example"
            class="example-question"
            type="button"
            :disabled="answering || !activeConversation"
            @click="useExample(example)"
          >
            {{ example }}
          </button>

          <el-alert
            class="safety-alert"
            title="涉及拆机、带电或高温部件时，请以原厂手册安全要求为准。"
            type="warning"
            :closable="false"
            show-icon
          />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="17">
        <el-card class="conversation-card" shadow="never">
          <template #header>
            <div class="conversation-header">
              <div>
                <div class="card-title">
                  {{ activeConversation?.title || 'AI 知识问答' }}
                </div>
                <span v-if="activeConversation">
                  {{ activeConversation.knowledgeBaseName }} · 多轮诊断对话
                </span>
                <span v-else>创建或选择一个会话后开始提问</span>
              </div>
              <div v-if="activeConversation" class="conversation-actions">
                <el-button
                  plain
                  icon="Edit"
                  :disabled="answering"
                  @click="renameConversation(activeConversation)"
                >
                  重命名
                </el-button>
                <el-button
                  plain
                  type="danger"
                  icon="Delete"
                  :disabled="answering"
                  @click="removeConversation(activeConversation)"
                >
                  删除会话
                </el-button>
              </div>
            </div>
          </template>

          <div
            ref="messageContainerRef"
            v-loading="messageLoading"
            class="message-container"
          >
            <el-empty
              v-if="!activeConversation"
              description="请先从左侧创建或选择会话"
              :image-size="96"
            />
            <el-empty
              v-else-if="!messageLoading && messages.length === 0"
              description="输入一个设备售后问题，开始本次会话"
              :image-size="96"
            />

            <div
              v-for="message in messages"
              :key="message.messageId"
              class="message-row"
              :class="`is-${message.role}`"
            >
              <div class="message-avatar">
                {{ message.role === 'user' ? '你' : 'AI' }}
              </div>
              <div class="message-body">
                <div class="message-meta">
                  <span>{{ message.role === 'user' ? '当前用户' : 'OfficeCare 助手' }}</span>
                  <span v-if="message.createTime">{{ formatTime(message.createTime) }}</span>
                  <el-tag
                    v-if="message.role === 'assistant' && message.requestFailed"
                    type="danger"
                    size="small"
                  >
                    请求失败
                  </el-tag>
                  <el-tag
                    v-else-if="message.role === 'assistant' && message.responseType"
                    :type="responseTypeTag(message.responseType).type"
                    size="small"
                  >
                    {{ responseTypeTag(message.responseType).label }}
                  </el-tag>
                  <el-tag
                    v-else-if="message.role === 'assistant' && message.refusalReason"
                    type="warning"
                    size="small"
                  >
                    证据不足
                  </el-tag>
                </div>
                <div class="message-text">{{ message.content }}</div>

                <div
                  v-if="message.role === 'assistant' && message.responseType"
                  class="agent-signals"
                >
                  <el-tag size="small" effect="plain">
                    {{ diagnosticStatusLabel(message.diagnosticStatus) }}
                  </el-tag>
                  <el-tag
                    v-if="message.requiresUserReply"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    等待你的反馈
                  </el-tag>
                  <el-tag
                    v-if="message.requiresOnSiteService"
                    size="small"
                    type="danger"
                    effect="plain"
                  >
                    建议转人工处理
                  </el-tag>
                </div>
                <div
                  v-if="message.role === 'assistant' && message.nextQuestion && message.nextQuestion !== message.content"
                  class="next-question"
                >
                  <strong>请继续反馈：</strong>{{ message.nextQuestion }}
                </div>

                <div
                  v-if="message.role === 'assistant' && message.citations?.length"
                  class="citations"
                >
                  <div class="citation-heading">资料来源</div>
                  <el-collapse>
                    <el-collapse-item
                      v-for="citation in message.citations"
                      :key="`${message.messageId}-${citation.citationIndex}`"
                      :name="`${message.messageId}-${citation.citationIndex}`"
                    >
                      <template #title>
                        <div class="citation-title">
                          <span>
                            [资料{{ citation.citationIndex }}]
                            {{ citation.documentTitle }}
                          </span>
                          <el-tag size="small" effect="plain">
                            {{ citation.pageNumber ? `第 ${citation.pageNumber} 页` : '页码未知' }}
                          </el-tag>
                          <el-tag size="small" type="info" effect="plain">
                            综合相关度 {{ formatScore(citation.score) }}
                          </el-tag>
                        </div>
                      </template>
                      <div class="citation-detail">
                        <div>
                          文件：{{ citation.originalFileName }}
                          <span v-if="citation.heading"> · {{ citation.heading }}</span>
                        </div>
                        <blockquote>{{ citation.quote }}</blockquote>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </div>

                <div v-if="message.role === 'assistant'" class="answer-footer">
                  <div class="model-note">
                    <span v-if="message.modelName">生成模型：{{ message.modelName }}</span>
                    <span v-if="message.traceId"> · Trace：{{ message.traceId }}</span>
                  </div>
                  <div
                    v-if="!isTemporaryMessage(message)"
                    class="feedback-actions"
                  >
                    <span>这个回答有帮助吗？</span>
                    <el-button
                      link
                      icon="CaretTop"
                      :type="message.feedbackType === '1' ? 'primary' : ''"
                      :loading="isFeedbackSubmitting(message.messageId)"
                      @click="submitFeedback(message, '1')"
                    >
                      有帮助
                    </el-button>
                    <el-button
                      link
                      icon="CaretBottom"
                      :type="message.feedbackType === '2' ? 'danger' : ''"
                      :loading="isFeedbackSubmitting(message.messageId)"
                      @click="submitFeedback(message, '2')"
                    >
                      无帮助
                    </el-button>
                  </div>
                </div>
                <div
                  v-if="message.role === 'assistant' && message.feedbackType === '2' && message.feedbackComment"
                  class="feedback-comment"
                >
                  反馈说明：{{ message.feedbackComment }}
                </div>
              </div>
            </div>

            <div v-if="answering" class="message-row is-assistant">
              <div class="message-avatar">AI</div>
              <div class="message-body loading-message">
                <span></span><span></span><span></span>
                正在结合会话记忆分析并检索资料
              </div>
            </div>
          </div>

          <div class="composer">
            <el-input
              v-model="question"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              resize="none"
              placeholder="描述故障，或继续回复上一步排查结果"
              :disabled="answering || !activeConversation"
              @keydown.enter.exact.prevent="submitQuestion"
            />
            <div class="composer-footer">
              <span>
                {{ activeConversation ? 'Enter 发送，Shift + Enter 换行' : '请先创建或选择会话' }}
              </span>
              <el-button
                type="primary"
                icon="Promotion"
                :loading="answering"
                :disabled="!canSubmit"
                @click="submitQuestion"
              >
                发送问题
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="conversationDialogVisible"
      title="创建 AI 会话"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="知识库" required>
          <el-select
            v-model="newConversationForm.knowledgeBaseId"
            :loading="optionsLoading"
            placeholder="请选择本次会话使用的知识库"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in knowledgeBases"
              :key="item.knowledgeBaseId"
              :label="item.knowledgeBaseName"
              :value="item.knowledgeBaseId"
            >
              <div class="knowledge-option">
                <span>{{ item.knowledgeBaseName }}</span>
                <small>{{ item.brandName }} · {{ item.modelName }}</small>
              </div>
            </el-option>
          </el-select>
          <div class="form-tip">创建后不可切换知识库，避免同一会话混用不同设备资料。</div>
        </el-form-item>
        <el-form-item label="会话标题">
          <el-input
            v-model="newConversationForm.title"
            maxlength="120"
            show-word-limit
            placeholder="可不填，首次提问后会自动生成"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="conversationDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="creatingConversation"
          @click="createConversation"
        >
          创建并进入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  answerAiConversation,
  createAiConversation,
  deleteAiConversation,
  listAiConversationMessages,
  listAiConversations,
  listAssistantKnowledgeBases,
  updateAiConversation,
  updateAiMessageFeedback
} from '@/api/officecare/assistant'

const optionsLoading = ref(false)
const conversationLoading = ref(false)
const messageLoading = ref(false)
const creatingConversation = ref(false)
const answering = ref(false)
const knowledgeBases = ref([])
const conversations = ref([])
const activeConversationId = ref()
const messages = ref([])
const question = ref('')
const messageContainerRef = ref()
const conversationDialogVisible = ref(false)
const feedbackSubmittingIds = ref([])
const newConversationForm = reactive({
  knowledgeBaseId: undefined,
  title: ''
})

const examples = [
  '打印页面出现重复斑点，请帮我逐步排查。',
  '设备提示无法打印，请一步一步告诉我该检查什么。',
  '打印机发生卡纸后应该如何安全处理？'
]

const responseTypeTags = {
  ANSWER: { label: '知识回答', type: 'success' },
  CLARIFICATION: { label: '需要补充信息', type: 'info' },
  DIAGNOSTIC_STEP: { label: '下一步排查', type: 'primary' },
  RESOLUTION: { label: '问题已解决', type: 'success' },
  ESCALATION: { label: '建议转人工', type: 'danger' },
  REFUSAL: { label: '证据不足', type: 'warning' }
}

const diagnosticStatusLabels = {
  IDLE: '等待问题',
  COLLECTING_INFORMATION: '正在收集信息',
  AWAITING_STEP_RESULT: '等待排查结果',
  RESOLVED: '诊断完成',
  ESCALATE: '需要人工处理',
  INSUFFICIENT_EVIDENCE: '知识证据不足'
}

const activeConversation = computed(() => {
  return conversations.value.find(
    item => item.conversationId === activeConversationId.value
  )
})

const canSubmit = computed(() => {
  return Boolean(activeConversation.value)
    && question.value.trim().length >= 2
    && !answering.value
})

async function loadKnowledgeBases() {
  optionsLoading.value = true
  try {
    const response = await listAssistantKnowledgeBases()
    knowledgeBases.value = response.data || []
  } catch (error) {
    ElMessage.error('知识库加载失败，请确认当前账号权限和后端服务。')
  } finally {
    optionsLoading.value = false
  }
}

async function loadConversations() {
  conversationLoading.value = true
  try {
    const response = await listAiConversations()
    conversations.value = response.data || []
    const activeStillExists = conversations.value.some(
      item => item.conversationId === activeConversationId.value
    )
    if (!activeStillExists) {
      activeConversationId.value = conversations.value[0]?.conversationId
    }
  } catch (error) {
    conversations.value = []
    activeConversationId.value = undefined
    ElMessage.error('会话列表加载失败，请确认已执行第 009 号数据库脚本。')
  } finally {
    conversationLoading.value = false
  }
}

async function loadMessages(conversationId) {
  if (!conversationId) {
    messages.value = []
    return
  }
  messageLoading.value = true
  try {
    const response = await listAiConversationMessages(conversationId)
    if (conversationId === activeConversationId.value) {
      messages.value = (response.data || []).map(hydrateHistoricalMessage)
      await scrollToBottom()
    }
  } catch (error) {
    if (conversationId === activeConversationId.value) {
      messages.value = []
    }
    ElMessage.error('会话消息加载失败，请稍后重试。')
  } finally {
    messageLoading.value = false
  }
}

function hydrateHistoricalMessage(message) {
  if (message.role !== 'assistant' || !message.agentResult) {
    return message
  }
  return {
    ...message,
    ...message.agentResult,
    content: message.content,
    citations: message.citations || message.agentResult.citations || [],
    traceId: message.traceId,
    feedbackType: message.feedbackType,
    feedbackComment: message.feedbackComment
  }
}

async function selectConversation(conversation) {
  if (conversation.conversationId === activeConversationId.value) {
    return
  }
  if (answering.value) {
    ElMessage.warning('当前问题正在回答，请完成后再切换会话。')
    return
  }
  activeConversationId.value = conversation.conversationId
  messages.value = []
  await loadMessages(conversation.conversationId)
}

function openConversationDialog() {
  newConversationForm.knowledgeBaseId =
    activeConversation.value?.knowledgeBaseId
    || (knowledgeBases.value.length === 1
      ? knowledgeBases.value[0].knowledgeBaseId
      : undefined)
  newConversationForm.title = ''
  conversationDialogVisible.value = true
}

async function createConversation() {
  if (!newConversationForm.knowledgeBaseId) {
    ElMessage.warning('请选择本次会话使用的知识库。')
    return
  }
  creatingConversation.value = true
  try {
    const title = newConversationForm.title.trim()
    const response = await createAiConversation({
      knowledgeBaseId: newConversationForm.knowledgeBaseId,
      title: title || undefined
    })
    activeConversationId.value = response.data.conversationId
    conversationDialogVisible.value = false
    await loadConversations()
    await loadMessages(activeConversationId.value)
    ElMessage.success('会话创建成功。')
  } catch (error) {
    ElMessage.error('会话创建失败，请检查知识库状态后重试。')
  } finally {
    creatingConversation.value = false
  }
}

async function renameConversation(conversation) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入新的会话标题',
      '重命名会话',
      {
        inputValue: conversation.title,
        inputPlaceholder: '1 至 120 个字符',
        inputValidator: value => {
          const title = String(value || '').trim()
          if (!title) return '会话标题不能为空'
          if (title.length > 120) return '会话标题不能超过 120 个字符'
          return true
        },
        confirmButtonText: '保存',
        cancelButtonText: '取消'
      }
    )
    const title = value.trim()
    await updateAiConversation(conversation.conversationId, { title })
    conversation.title = title
    ElMessage.success('会话标题已更新。')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('会话标题更新失败。')
    }
  }
}

async function removeConversation(conversation) {
  try {
    await ElMessageBox.confirm(
      `确定删除会话“${conversation.title}”吗？消息和引用将保留用于审计，但页面中不再显示。`,
      '删除会话',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
    await deleteAiConversation(conversation.conversationId)
    if (activeConversationId.value === conversation.conversationId) {
      activeConversationId.value = undefined
      messages.value = []
    }
    await loadConversations()
    await loadMessages(activeConversationId.value)
    ElMessage.success('会话已删除。')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('会话删除失败。')
    }
  }
}

function useExample(example) {
  question.value = example
}

function createTemporaryMessageId() {
  return `temp-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function createTurnId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, value => {
    const random = Math.floor(Math.random() * 16)
    const digit = value === 'x' ? random : ((random & 0x3) | 0x8)
    return digit.toString(16)
  })
}

function responseTypeTag(responseType) {
  return responseTypeTags[responseType] || { label: 'AI回复', type: 'info' }
}

function diagnosticStatusLabel(status) {
  return diagnosticStatusLabels[status] || '会话处理中'
}

function isTemporaryMessage(message) {
  return String(message.messageId || '').startsWith('temp-')
}

async function scrollToBottom() {
  await nextTick()
  if (messageContainerRef.value) {
    messageContainerRef.value.scrollTop = messageContainerRef.value.scrollHeight
  }
}

async function submitQuestion() {
  if (!activeConversation.value) {
    ElMessage.warning('请先创建或选择会话。')
    return
  }
  const normalizedQuestion = question.value.trim()
  if (normalizedQuestion.length < 2) {
    ElMessage.warning('请输入至少 2 个字符的问题。')
    return
  }

  const conversationId = activeConversationId.value
  const turnId = createTurnId()
  const temporaryUserMessage = {
    messageId: createTemporaryMessageId(),
    conversationId,
    turnId,
    role: 'user',
    content: normalizedQuestion
  }
  messages.value.push(temporaryUserMessage)
  question.value = ''
  answering.value = true
  await scrollToBottom()

  try {
    const response = await answerAiConversation(conversationId, {
      turnId,
      question: normalizedQuestion
    })
    const data = response.data
    temporaryUserMessage.messageId = data.userMessageId
    messages.value.push({
      messageId: data.assistantMessageId,
      conversationId,
      turnId: data.turnId || turnId,
      role: 'assistant',
      content: data.result.answer,
      answered: data.result.answered,
      refusalReason: data.result.refusalReason,
      responseType: data.result.responseType,
      diagnosticStatus: data.result.diagnosticStatus,
      issueSummary: data.result.issueSummary,
      attemptedSteps: data.result.attemptedSteps || [],
      observations: data.result.observations || [],
      nextQuestion: data.result.nextQuestion,
      requiresUserReply: data.result.requiresUserReply,
      requiresOnSiteService: data.result.requiresOnSiteService,
      citations: data.result.citations || [],
      modelName: data.result.modelName,
      traceId: data.traceId,
      feedbackType: '0'
    })
    const current = conversations.value.find(
      item => item.conversationId === conversationId
    )
    if (current) {
      current.title = data.conversationTitle
    }
    await loadConversations()
  } catch (error) {
    messages.value.push({
      messageId: createTemporaryMessageId(),
      conversationId,
      role: 'assistant',
      content: '本次分析失败，请检查文档索引、FastAPI、PostgreSQL记忆、Milvus和大模型配置后重试。',
      requestFailed: true,
      answered: false,
      citations: []
    })
  } finally {
    answering.value = false
    await scrollToBottom()
  }
}

function isFeedbackSubmitting(messageId) {
  return feedbackSubmittingIds.value.includes(messageId)
}

async function submitFeedback(message, requestedType) {
  if (isFeedbackSubmitting(message.messageId)) {
    return
  }
  const feedbackType = message.feedbackType === requestedType ? '0' : requestedType
  let feedbackComment

  if (feedbackType === '2') {
    try {
      const { value } = await ElMessageBox.prompt(
        '可以补充说明答案哪里不准确、缺少哪些信息；也可以留空直接提交。',
        '这条回答哪里需要改进？',
        {
          inputType: 'textarea',
          inputValue: message.feedbackComment || '',
          inputPlaceholder: '可选，最多 500 个字符',
          inputValidator: value => {
            return String(value || '').length <= 500 || '反馈说明不能超过 500 个字符'
          },
          confirmButtonText: '提交反馈',
          cancelButtonText: '取消'
        }
      )
      feedbackComment = String(value || '').trim() || undefined
    } catch (error) {
      return
    }
  }

  feedbackSubmittingIds.value.push(message.messageId)
  try {
    await updateAiMessageFeedback(message.messageId, {
      feedbackType,
      feedbackComment: feedbackType === '2' ? feedbackComment : undefined
    })
    message.feedbackType = feedbackType
    message.feedbackComment = feedbackType === '2' ? feedbackComment : undefined
    ElMessage.success(feedbackType === '0' ? '反馈已清除。' : '感谢你的反馈。')
  } catch (error) {
    ElMessage.error('反馈提交失败，请稍后重试。')
  } finally {
    feedbackSubmittingIds.value = feedbackSubmittingIds.value.filter(
      id => id !== message.messageId
    )
  }
}

function formatScore(score) {
  const value = Number(score)
  return Number.isFinite(value) ? `${(value * 100).toFixed(1)}%` : '-'
}

function formatTime(value) {
  if (!value) return '暂无消息'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  await Promise.all([loadKnowledgeBases(), loadConversations()])
  await loadMessages(activeConversationId.value)
})
</script>

<style scoped lang="scss">
.assistant-page {
  min-height: calc(100vh - 84px);
  // 不再写死浅灰底，跟随全局页面底色并自动适配暗色
}

.assistant-header {
  position: relative;
  margin-bottom: 18px;
  overflow: hidden;
  color: #fff;
  border: none;
  // 与工作台使用同一套 hero 材质，保证两个页面是"同一个产品"
  background:
    radial-gradient(120% 140% at 88% 10%, rgba(110, 139, 240, 0.45), transparent 54%),
    linear-gradient(135deg, var(--oc-neutral-900) 0%, var(--oc-accent-900) 100%);
  box-shadow: var(--oc-shadow-md);
}

.assistant-header::after {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 1px;
  content: '';
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.22), transparent);
}

.header-content,
.session-heading,
.conversation-header,
.session-item,
.composer-footer,
.answer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-content h1 {
  margin: 7px 0 10px;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.header-content p {
  max-width: 64ch;
  margin: 0;
  color: rgba(255, 255, 255, 0.72);
  line-height: 1.7;
}

.header-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.eyebrow {
  color: var(--oc-accent-300);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.18em;
}

.session-card,
.conversation-card {
  margin-bottom: 18px;
  border: 1px solid var(--el-border-color-lighter);
}

.card-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.session-heading span,
.conversation-header span {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.session-list {
  min-height: 130px;
  max-height: 320px;
  margin: -4px -6px 16px;
  padding: 4px 6px;
  overflow-y: auto;
}

.session-item {
  margin-bottom: 8px;
  padding: 11px 10px 11px 12px;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--oc-radius-md);
  transition:
    transform var(--oc-dur-press) var(--oc-ease-out),
    background-color var(--oc-dur-fast) var(--oc-ease-out),
    border-color var(--oc-dur-fast) var(--oc-ease-out),
    box-shadow var(--oc-dur-fast) var(--oc-ease-out);
}

@media (hover: hover) and (pointer: fine) {
  .session-item:hover {
    background: var(--el-fill-color-light);
    border-color: var(--oc-accent-200);
  }
}

.session-item:active {
  transform: scale(0.99);
}

.session-item.active {
  background: var(--oc-accent-soft);
  border-color: var(--oc-accent-300);
  box-shadow: inset 3px 0 var(--oc-accent-600);
}

.session-main {
  min-width: 0;
}

.session-title,
.session-scope {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.session-scope,
.session-time {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.session-actions {
  display: flex;
  flex: 0 0 auto;
}

.scope-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  background: var(--oc-accent-soft);
  border: 1px solid var(--oc-accent-200);
  border-radius: var(--oc-radius-md);
}

html.dark .scope-panel {
  border-color: rgba(110, 139, 240, 0.3);
}

.scope-label,
.example-title {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.scope-panel p {
  margin: 4px 0 0;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.6;
}

.example-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.example-question {
  width: 100%;
  margin-bottom: 8px;
  padding: 10px 12px;
  color: var(--el-text-color-regular);
  font-family: inherit;
  font-size: 13px;
  text-align: left;
  line-height: 1.5;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--oc-radius-sm);
  transition:
    transform var(--oc-dur-press) var(--oc-ease-out),
    color var(--oc-dur-fast) var(--oc-ease-out),
    background-color var(--oc-dur-fast) var(--oc-ease-out),
    border-color var(--oc-dur-fast) var(--oc-ease-out);
}

@media (hover: hover) and (pointer: fine) {
  .example-question:hover:not(:disabled) {
    color: var(--el-color-primary);
    background: var(--oc-accent-soft);
    border-color: var(--oc-accent-300);
  }
}

.example-question:active:not(:disabled) {
  transform: scale(0.99);
}

.example-question:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.safety-alert {
  margin-top: 14px;
}

.conversation-actions {
  display: flex;
  gap: 8px;
}

.message-container {
  height: 540px;
  padding: 4px 4px 18px;
  overflow-y: auto;
  // 滚动时不触发父级链式滚动，聊天区滚动更"干净"
  overscroll-behavior: contain;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin: 18px 0;
  // 新消息"落下"而不是"弹现"，这是聊天界面手感的关键
  animation: oc-fade-up var(--oc-dur-slow) var(--oc-ease-out) both;
}

.message-row.is-user {
  flex-direction: row-reverse;
}

.message-avatar {
  display: flex;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  color: var(--oc-accent-soft-fg);
  font-size: 12px;
  font-weight: 600;
  background: var(--oc-accent-soft);
  border-radius: var(--oc-radius-md);
}

.is-user .message-avatar {
  color: #fff;
  background: var(--oc-accent-600);
}

.message-body {
  max-width: min(84%, 780px);
  padding: 13px 15px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--oc-radius-xs) var(--oc-radius-lg) var(--oc-radius-lg);
}

.is-user .message-body {
  color: #fff;
  background: var(--oc-accent-600);
  border: none;
  border-radius: var(--oc-radius-lg) var(--oc-radius-xs) var(--oc-radius-lg) var(--oc-radius-lg);
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.is-user .message-meta {
  color: rgba(255, 255, 255, 0.78);
}

.message-text {
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.citations {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.agent-signals {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.next-question {
  margin-top: 10px;
  padding: 9px 11px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  background: var(--oc-accent-soft);
  border-left: 3px solid var(--oc-accent-400);
  border-radius: 0 var(--oc-radius-xs) var(--oc-radius-xs) 0;
}

.citation-heading {
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.citation-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.citation-title > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-detail {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.citation-detail blockquote {
  margin: 10px 0 0;
  padding: 10px 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-color-primary-light-7);
}

.answer-footer {
  margin-top: 11px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.model-note {
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}

.feedback-actions {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.feedback-comment {
  margin-top: 8px;
  padding: 8px 10px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: var(--oc-radius-sm);
}

.loading-message {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--el-text-color-regular);
}

.loading-message span {
  width: 6px;
  height: 6px;
  background: var(--el-color-primary);
  border-radius: 50%;
  animation: oc-typing-pulse 1.2s infinite var(--oc-ease-in-out);
}

.loading-message span:nth-child(2) {
  animation-delay: 0.15s;
}

.loading-message span:nth-child(3) {
  margin-right: 6px;
  animation-delay: 0.3s;
}

.composer {
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.composer-footer {
  margin-top: 10px;
}

.composer-footer span,
.form-tip {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.form-tip {
  margin-top: 7px;
  line-height: 1.5;
}

.knowledge-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.knowledge-option small {
  color: var(--el-text-color-secondary);
}

// 原名为 pulse，过于通用，容易和其它组件的全局 keyframes 撞名
@keyframes oc-typing-pulse {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 768px) {
  .header-content,
  .conversation-header,
  .composer-footer,
  .answer-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .header-tags {
    justify-content: flex-start;
  }

  .message-container {
    height: 460px;
  }

  .message-body {
    max-width: calc(100% - 48px);
  }

  .citation-title {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .message-row {
    animation: none;
  }

  .loading-message span {
    animation: none;
    opacity: 1;
    transform: none;
  }
}
</style>
