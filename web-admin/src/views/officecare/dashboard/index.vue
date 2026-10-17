<template>
  <div class="app-container officecare-dashboard">
    <el-card class="hero-card" shadow="never">
      <div class="hero-content">
        <div>
          <div class="eyebrow">OFFICECARE AI</div>
          <h1>售后智能服务工作台</h1>
          <p>知识问答、客户设备档案与可追溯的售后工单闭环已经接入。</p>
        </div>
        <el-tag :type="statusType" effect="dark" size="large">
          {{ statusText }}
        </el-tag>
      </div>
    </el-card>

    <el-alert
      v-if="loadError"
      class="load-alert"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
    />

    <el-row v-loading="loading" :gutter="16" class="metric-row oc-stagger">
      <el-col
        v-for="metric in metrics"
        :key="metric.key"
        :xs="24"
        :sm="12"
        :lg="8"
      >
        <el-card class="metric-card oc-lift" shadow="never">
          <div class="metric-label">{{ metric.label }}</div>
          <div class="metric-value">{{ metric.value }}</div>
          <div class="metric-note">{{ metric.note }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="mq-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">RabbitMQ 文档任务</div>
            <div class="card-subtitle">解析、索引和删索引的可靠投递与消费状态</div>
          </div>
          <el-tag :type="mqMetrics.enabled ? 'success' : 'info'">
            {{ mqMetrics.enabled ? 'MQ 模式' : '本地兼容模式' }}
          </el-tag>
        </div>
      </template>

      <div class="mq-metrics">
        <div class="mq-metric">
          <span>待处理任务</span>
          <strong>{{ mqMetrics.pendingTaskCount }}</strong>
        </div>
        <div class="mq-metric">
          <span>处理中任务</span>
          <strong>{{ mqMetrics.runningTaskCount }}</strong>
        </div>
        <div class="mq-metric mq-metric-warning">
          <span>失败任务</span>
          <strong>{{ mqMetrics.failedTaskCount }}</strong>
        </div>
        <div class="mq-metric mq-metric-danger">
          <span>待处理死信</span>
          <strong>{{ mqMetrics.deadLetterCount }}</strong>
        </div>
      </div>

      <el-descriptions :column="4" border class="mq-observability">
        <el-descriptions-item label="平均等待">
          {{ formatDuration(mqMetrics.averageWaitTimeMs) }}
        </el-descriptions-item>
        <el-descriptions-item label="平均处理">
          {{ formatDuration(mqMetrics.averageProcessTimeMs) }}
        </el-descriptions-item>
        <el-descriptions-item label="任务重试">
          {{ mqMetrics.taskRetryCount }} 次
        </el-descriptions-item>
        <el-descriptions-item label="发布重试">
          {{ mqMetrics.publishRetryCount }} 次
        </el-descriptions-item>
      </el-descriptions>

      <el-table
        v-if="deadMessages.length"
        :data="deadMessages"
        class="dead-table"
        size="small"
      >
        <el-table-column label="阶段" width="92">
          <template #default="scope">
            <el-tag :type="scope.row.failureStage === 'PUBLISH' ? 'warning' : 'danger'">
              {{ scope.row.failureStage === 'PUBLISH' ? '发布失败' : '消费死信' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务ID" width="90" />
        <el-table-column label="任务类型" width="105">
          <template #default="scope">{{ taskTypeText(scope.row.taskType) }}</template>
        </el-table-column>
        <el-table-column prop="taskAttempt" label="执行轮次" width="90" />
        <el-table-column prop="errorMessage" label="最近错误" min-width="240" show-overflow-tooltip />
        <el-table-column prop="traceId" label="Trace ID" min-width="220" show-overflow-tooltip />
        <el-table-column prop="deadTime" label="发生时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button
              v-hasPermi="['officecare:document:parse']"
              link
              type="primary"
              :loading="replayingId === scope.row.outboxId"
              @click="handleReplay(scope.row)"
            >
              重新投递
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="当前没有待人工处理的异常消息" :image-size="72" />
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <div class="card-title">业务服务链路</div>
              <el-button link type="primary" :loading="loading" @click="loadSummary">
                刷新数据
              </el-button>
            </div>
          </template>
          <el-steps direction="vertical" :active="5" finish-status="success">
            <el-step title="产品与设备档案" description="统一维护品牌、型号、客户和在用设备信息" />
            <el-step title="知识文档治理" description="完成文档版本、解析任务和向量索引管理" />
            <el-step title="AI 辅助诊断" description="结合设备型号检索资料并提供可追溯回答" />
            <el-step title="智能报修" description="通过多轮沟通生成可由售后人员编辑的工单草稿" />
            <el-step title="工单处理闭环" description="人工确认建单，持续记录分派、处理和完成过程" />
          </el-steps>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card class="quick-card" shadow="never">
          <template #header>
            <div class="card-title">常用操作</div>
          </template>
          <div class="quick-actions">
            <button
              v-hasPermi="['officecare:ai:chat']"
              class="quick-action"
              type="button"
              @click="goTo('/officecare/assistant')"
            >
              <span class="quick-action-icon"><svg-icon icon-class="message" /></span>
              <span class="quick-action-copy">
                <strong>AI 助手</strong>
                <small>查询设备知识并获得可追溯回答</small>
              </span>
            </button>
            <button
              v-hasPermi="['officecare:document:list']"
              class="quick-action"
              type="button"
              @click="goTo('/officecare/knowledge/document')"
            >
              <span class="quick-action-icon"><svg-icon icon-class="documentation" /></span>
              <span class="quick-action-copy">
                <strong>文档管理</strong>
                <small>上传、解析并索引产品资料</small>
              </span>
            </button>
            <button
              v-hasPermi="['officecare:agent:list']"
              class="quick-action"
              type="button"
              @click="goTo('/officecare/service/smart-report')"
            >
              <span class="quick-action-icon"><svg-icon icon-class="question" /></span>
              <span class="quick-action-copy">
                <strong>智能报修</strong>
                <small>分析故障并生成待确认工单草稿</small>
              </span>
            </button>
            <button
              v-hasPermi="['officecare:workorder:list']"
              class="quick-action"
              type="button"
              @click="goTo('/officecare/service/work-order')"
            >
              <span class="quick-action-icon"><svg-icon icon-class="list" /></span>
              <span class="quick-action-copy">
                <strong>售后工单</strong>
                <small>查询工单并跟踪处理进度</small>
              </span>
            </button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getDashboardSummary,
  getMqDeadLetters,
  replayMqDeadLetter
} from '@/api/officecare/dashboard'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const deadMessages = ref([])
const replayingId = ref(null)
const summary = ref({
  status: 'LOADING',
  brandCount: 0,
  productModelCount: 0,
  knowledgeBaseCount: 0,
  pendingDocumentCount: 0,
  pendingWorkOrderCount: 0,
  messageQueue: {
    enabled: false,
    pendingTaskCount: 0,
    runningTaskCount: 0,
    failedTaskCount: 0,
    deadLetterCount: 0,
    taskRetryCount: 0,
    publishRetryCount: 0,
    averageWaitTimeMs: 0,
    averageProcessTimeMs: 0
  }
})

const mqMetrics = computed(() => summary.value.messageQueue || {})

const metrics = computed(() => [
  { key: 'brand', label: '品牌', value: summary.value.brandCount, note: '产品资料实时统计' },
  { key: 'model', label: '产品型号', value: summary.value.productModelCount, note: '产品资料实时统计' },
  { key: 'knowledge', label: '知识库', value: summary.value.knowledgeBaseCount, note: 'RAG 基础数据' },
  { key: 'document', label: '待处理文档', value: summary.value.pendingDocumentCount, note: '文档处理闭环' },
  { key: 'workOrder', label: '待处理工单', value: summary.value.pendingWorkOrderCount, note: '售后业务阶段' }
])

const statusText = computed(() => {
  if (summary.value.status === 'PRODUCT_CATALOG_READY') return '产品资料已就绪'
  if (summary.value.status === 'DOCUMENT_STORAGE_READY') return '文档存储已就绪'
  if (summary.value.status === 'DOCUMENT_PARSING_READY') return '文档解析已就绪'
  if (summary.value.status === 'VECTOR_INDEXING_READY') return '向量索引已就绪'
  if (summary.value.status === 'LANGCHAIN_RAG_READY') return '知识问答已就绪'
  if (summary.value.status === 'AFTER_SALES_MVP_READY') return '售后闭环已就绪'
  if (summary.value.status === 'INITIALIZED') return '系统已初始化'
  if (summary.value.status === 'LOADING') return '正在加载'
  return summary.value.status || '未知状态'
})

const statusType = computed(() => {
  return ['INITIALIZED', 'PRODUCT_CATALOG_READY', 'DOCUMENT_STORAGE_READY', 'DOCUMENT_PARSING_READY', 'VECTOR_INDEXING_READY', 'LANGCHAIN_RAG_READY', 'AFTER_SALES_MVP_READY'].includes(summary.value.status)
    ? 'success'
    : 'info'
})

function goTo(path) {
  router.push(path)
}

async function loadSummary() {
  loading.value = true
  loadError.value = ''
  try {
    const [summaryResponse, deadResponse] = await Promise.all([
      getDashboardSummary(),
      getMqDeadLetters()
    ])
    summary.value = {
      ...summary.value,
      ...summaryResponse.data,
      messageQueue: {
        ...summary.value.messageQueue,
        ...(summaryResponse.data.messageQueue || {})
      }
    }
    deadMessages.value = deadResponse.data || []
  } catch (error) {
    loadError.value = '工作台状态加载失败，请确认后端服务和当前账号权限。'
  } finally {
    loading.value = false
  }
}

function formatDuration(value) {
  const milliseconds = Number(value || 0)
  if (milliseconds < 1000) return `${milliseconds} ms`
  if (milliseconds < 60000) return `${(milliseconds / 1000).toFixed(1)} s`
  return `${(milliseconds / 60000).toFixed(1)} min`
}

function taskTypeText(taskType) {
  if (taskType === 'parse') return '文档解析'
  if (taskType === 'index') return '向量索引'
  if (taskType === 'delete_index') return '删除索引'
  return taskType || '-'
}

async function handleReplay(row) {
  try {
    await ElMessageBox.confirm(
      `确认重新投递任务 ${row.taskId}？请先确认导致失败的配置、网络或文档问题已经处理。`,
      '人工重新投递',
      { type: 'warning', confirmButtonText: '重新投递', cancelButtonText: '取消' }
    )
    replayingId.value = row.outboxId
    await replayMqDeadLetter(row.outboxId)
    ElMessage.success('消息已重新进入可靠投递链路')
    await loadSummary()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.msg || '重新投递失败')
    }
  } finally {
    replayingId.value = null
  }
}

onMounted(loadSummary)
</script>

<style scoped lang="scss">
.officecare-dashboard {
  // 不再写死浅灰底：跟随全局页面底色，并自动适配暗色模式
  min-height: calc(100vh - 84px);
}

.hero-card {
  position: relative;
  margin-bottom: 16px;
  overflow: hidden;
  color: #fff;
  border: none;
  background:
    radial-gradient(120% 140% at 88% 10%, rgba(110, 139, 240, 0.45), transparent 54%),
    linear-gradient(135deg, var(--oc-neutral-900) 0%, var(--oc-accent-900) 100%);
  box-shadow: var(--oc-shadow-md);
}

// 顶部一道极淡高光，模拟材质受光。用户不会注意到，但整体会显得更"实"
.hero-card::after {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 1px;
  content: '';
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.22), transparent);
}

.hero-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.hero-content h1 {
  margin: 6px 0 10px;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.hero-content p {
  max-width: 64ch;
  margin: 0;
  color: rgba(255, 255, 255, 0.72);
  line-height: 1.7;
}

.eyebrow {
  color: var(--oc-accent-300);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.18em;
}

.load-alert {
  margin-bottom: 16px;
}

.metric-row {
  min-height: 148px;
}

.metric-card {
  margin-bottom: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}

.metric-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-value {
  margin: 10px 0 6px;
  color: var(--el-text-color-primary);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.metric-note {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.mq-card {
  margin-bottom: 16px;
}

.card-subtitle {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mq-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.mq-metric {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 15px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--oc-radius-md);
}

.mq-metric strong {
  color: var(--el-text-color-primary);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
}

.mq-metric-warning strong {
  color: var(--el-color-warning);
}

.mq-metric-danger strong {
  color: var(--el-color-danger);
}

.mq-observability {
  margin-bottom: 16px;
}

.dead-table {
  margin-top: 8px;
}

.card-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.quick-card {
  min-height: 100%;
}

.quick-actions {
  display: grid;
  gap: 10px;
}

.quick-action {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 13px 15px;
  color: var(--el-text-color-primary);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--oc-radius-md);
  transition:
    transform var(--oc-dur-press) var(--oc-ease-out),
    background-color var(--oc-dur-fast) var(--oc-ease-out),
    border-color var(--oc-dur-fast) var(--oc-ease-out),
    box-shadow var(--oc-dur-fast) var(--oc-ease-out);
}

// hover 位移只在精确指针设备生效，触屏点击不会误触发"悬停"
@media (hover: hover) and (pointer: fine) {
  .quick-action:hover {
    background: var(--el-bg-color);
    border-color: var(--oc-accent-200);
    box-shadow: var(--oc-shadow-sm);
    transform: translateY(-1px);
  }

  .quick-action:hover .quick-action-icon {
    color: #fff;
    background: var(--oc-accent-600);
  }
}

.quick-action:active {
  transform: scale(0.99);
}

@media (prefers-reduced-motion: reduce) {
  .quick-action:active,
  .quick-action:hover {
    transform: none;
  }
}

.quick-action-icon {
  display: inline-flex;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-right: 12px;
  color: var(--oc-accent-soft-fg);
  font-size: 18px;
  background: var(--oc-accent-soft);
  border-radius: var(--oc-radius-sm);
  transition:
    color var(--oc-dur-fast) var(--oc-ease-out),
    background-color var(--oc-dur-fast) var(--oc-ease-out);
}

.quick-action-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.quick-action-copy strong {
  font-size: 14px;
  font-weight: 600;
}

.quick-action-copy small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .hero-content {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-content h1 {
    font-size: 22px;
  }

  .mq-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
