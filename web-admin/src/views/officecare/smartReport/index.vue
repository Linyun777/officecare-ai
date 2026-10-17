<template>
  <div class="app-container smart-report-page">
    <el-alert
      title="AI只负责分析和生成草稿，正式工单必须由你检查并确认后才会创建。涉及拆机、带电或高温部件时，请遵循原厂安全手册。"
      type="warning"
      :closable="false"
      show-icon
      class="page-alert"
    />

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="智能分析" name="analyze">
        <el-card shadow="never" class="analysis-card">
          <template #header>
            <div class="card-title">
              <div>
                <h3>描述设备故障</h3>
                <p>系统会根据设备型号自动限定知识库与已索引文档，不会跨型号检索。</p>
              </div>
            </div>
          </template>

          <el-form ref="analyzeRef" :model="analyzeForm" :rules="analyzeRules" label-width="92px">
            <el-row :gutter="18">
              <el-col :xs="24" :md="12">
                <el-form-item label="客户单位" prop="customerOrgId">
                  <el-select
                    v-model="analyzeForm.customerOrgId"
                    placeholder="请选择客户单位"
                    filterable
                    style="width: 100%"
                    @change="handleCustomerChange"
                  >
                    <el-option
                      v-for="customer in customerOptions"
                      :key="customer.customerOrgId"
                      :label="customer.customerName"
                      :value="customer.customerOrgId"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="报修设备" prop="deviceId">
                  <el-select
                    v-model="analyzeForm.deviceId"
                    placeholder="请先选择客户单位"
                    filterable
                    :disabled="!analyzeForm.customerOrgId"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="device in filteredDevices"
                      :key="device.deviceId"
                      :label="deviceLabel(device)"
                      :value="device.deviceId"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="故障现象" prop="issueDescription">
              <el-input
                v-model="analyzeForm.issueDescription"
                type="textarea"
                :rows="5"
                maxlength="2000"
                show-word-limit
                placeholder="请描述错误信息、发生频率、当前状态和已经尝试过的操作，例如：打印页面每隔94毫米出现重复斑点。"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                icon="MagicStick"
                :loading="analyzing"
                @click="submitAnalyze"
                v-hasPermi="['officecare:agent:analyze']"
              >开始智能分析</el-button>
              <span class="action-hint">分析可能需要几十秒，请勿重复提交。</span>
            </el-form-item>
          </el-form>
        </el-card>

        <template v-if="currentDraft.draftId">
          <el-card shadow="never" class="result-card oc-enter">
            <template #header>
              <div class="result-heading">
                <div>
                  <h3>分析结果</h3>
                  <p>{{ currentDraft.brandName }} {{ currentDraft.modelName }} · {{ currentDraft.deviceName }}</p>
                </div>
                <div class="result-tags">
                  <el-tag :type="currentDraft.answered ? 'success' : 'warning'">
                    {{ currentDraft.answered ? '证据充分' : '证据不足' }}
                  </el-tag>
                  <el-tag v-if="currentDraft.requiresOnSiteService" type="danger">建议上门</el-tag>
                  <el-tag type="info">必须人工确认</el-tag>
                </div>
              </div>
            </template>

            <div class="summary-block">{{ currentDraft.summary }}</div>

            <el-row :gutter="18" class="evidence-row">
              <el-col :xs="24" :lg="12">
                <h4>排障步骤</h4>
                <ol v-if="currentDraft.troubleshootingSteps?.length" class="text-list">
                  <li v-for="(step, index) in currentDraft.troubleshootingSteps" :key="index">{{ step }}</li>
                </ol>
                <el-empty v-else description="证据不足，未给出排障步骤" :image-size="60" />
              </el-col>
              <el-col :xs="24" :lg="12">
                <h4>安全提示</h4>
                <ul v-if="currentDraft.safetyWarnings?.length" class="warning-list">
                  <li v-for="(warning, index) in currentDraft.safetyWarnings" :key="index">{{ warning }}</li>
                </ul>
                <el-empty v-else description="没有额外安全提示" :image-size="60" />
              </el-col>
            </el-row>

            <div v-if="currentDraft.citations?.length" class="citation-section oc-stagger">
              <h4>知识来源</h4>
              <el-card
                v-for="citation in currentDraft.citations"
                :key="`${citation.documentVersionId}-${citation.chunkIndex}`"
                shadow="never"
                class="citation-card"
              >
                <div class="citation-heading">
                  <strong>[资料{{ citation.citationIndex }}] {{ citation.documentTitle }}</strong>
                  <span>第 {{ citation.pageNumber || '-' }} 页 · 相似度 {{ formatScore(citation.score) }}</span>
                </div>
                <blockquote>{{ citation.quote }}</blockquote>
              </el-card>
            </div>

            <el-collapse class="trace-section">
              <el-collapse-item title="查看Agent工具调用审计" name="trace">
                <el-steps :active="currentDraft.toolTrace?.length" finish-status="success" simple>
                  <el-step
                    v-for="trace in currentDraft.toolTrace || []"
                    :key="`${trace.sequence}-${trace.toolName}`"
                    :title="toolLabel(trace.toolName)"
                    :status="trace.success ? 'success' : 'error'"
                  />
                </el-steps>
                <p class="trace-note">这里只显示受控只读工具轨迹；Agent没有创建正式工单的工具。</p>
              </el-collapse-item>
            </el-collapse>
          </el-card>

          <el-card shadow="never" class="draft-card oc-enter">
            <template #header>
              <div class="card-title">
                <div>
                  <h3>人工检查工单草稿</h3>
                  <p>标题、描述、优先级、负责人和备注均可修改，知识引用不可由前端改写。</p>
                </div>
                <el-tag :type="draftStatusMeta(currentDraft.status).type">
                  {{ draftStatusMeta(currentDraft.status).label }}
                </el-tag>
              </div>
            </template>

            <el-form ref="draftRef" :model="draftForm" :rules="draftRules" label-width="92px">
              <el-form-item label="工单标题" prop="title">
                <el-input v-model="draftForm.title" maxlength="150" show-word-limit :disabled="!isPending" />
              </el-form-item>
              <el-form-item label="故障描述" prop="faultDescription">
                <el-input
                  v-model="draftForm.faultDescription"
                  type="textarea"
                  :rows="6"
                  maxlength="2000"
                  show-word-limit
                  :disabled="!isPending"
                />
              </el-form-item>
              <el-row :gutter="18">
                <el-col :xs="24" :md="12">
                  <el-form-item label="优先级" prop="priority">
                    <el-radio-group v-model="draftForm.priority" :disabled="!isPending">
                      <el-radio v-for="item in priorityOptions" :key="item.value" :value="item.value">
                        {{ item.label }}
                      </el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="负责人">
                    <el-select
                      v-model="draftForm.assigneeUserId"
                      placeholder="可暂不指派"
                      clearable
                      filterable
                      :disabled="!isPending"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="user in assigneeOptions"
                        :key="user.userId"
                        :label="assigneeLabel(user)"
                        :value="user.userId"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="工单备注" prop="workOrderRemark">
                <el-input
                  v-model="draftForm.workOrderRemark"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  :disabled="!isPending"
                />
              </el-form-item>
              <el-form-item v-if="isPending">
                <el-button
                  icon="DocumentChecked"
                  :loading="saving"
                  @click="saveDraft"
                  v-hasPermi="['officecare:agent:edit']"
                >保存草稿</el-button>
                <el-button
                  type="danger"
                  plain
                  icon="Delete"
                  :loading="deletingDraftId === currentDraft.draftId"
                  @click="deleteCurrentDraft"
                  v-hasPermi="['officecare:agent:remove']"
                >删除草稿</el-button>
                <el-button
                  type="primary"
                  icon="CircleCheck"
                  :loading="confirming"
                  @click="confirmDraft"
                  v-hasPermi="['officecare:agent:confirm']"
                >确认并创建正式工单</el-button>
              </el-form-item>
              <el-form-item v-else-if="currentDraft.status === '1'">
                <el-result
                  icon="success"
                  title="正式工单已创建"
                  :sub-title="`工单编号：${currentDraft.workOrderNo || '-'}`"
                  class="inline-result"
                >
                  <template #extra>
                    <el-button type="primary" @click="openWorkOrders">前往售后工单</el-button>
                  </template>
                </el-result>
              </el-form-item>
            </el-form>
          </el-card>
        </template>
      </el-tab-pane>

      <el-tab-pane label="我的草稿" name="history">
        <el-form ref="queryRef" :model="queryParams" :inline="true" label-width="72px">
          <el-form-item label="草稿标题" prop="draftTitle">
            <el-input v-model="queryParams.draftTitle" clearable placeholder="标题关键词" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="设备名称" prop="deviceName">
            <el-input v-model="queryParams.deviceName" clearable placeholder="设备名称" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 130px">
              <el-option v-for="item in draftStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="historyLoading" :data="draftList">
          <el-table-column label="草稿标题" prop="draftTitle" min-width="220" show-overflow-tooltip />
          <el-table-column label="客户 / 设备" min-width="220">
            <template #default="scope">
              <div class="table-device">
                <strong>{{ scope.row.deviceName }}</strong>
                <span>{{ scope.row.customerName }} · {{ scope.row.modelName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="证据" width="100" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.answered ? 'success' : 'warning'" effect="plain">
                {{ scope.row.answered ? '充分' : '不足' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="scope">
              <el-tag :type="draftStatusMeta(scope.row.status).type">
                {{ draftStatusMeta(scope.row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="正式工单" prop="workOrderNo" width="190" show-overflow-tooltip />
          <el-table-column label="创建时间" prop="createTime" width="165" />
          <el-table-column label="操作" width="230" align="center">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="viewDraft(scope.row)" v-hasPermi="['officecare:agent:query']">
                查看
              </el-button>
              <el-button
                v-if="scope.row.status === '0'"
                link
                type="danger"
                icon="Delete"
                :loading="deletingDraftId === scope.row.draftId"
                @click="deleteDraft(scope.row)"
                v-hasPermi="['officecare:agent:remove']"
              >删除草稿</el-button>
              <el-button
                v-if="scope.row.status === '1'"
                link type="success" icon="Tickets" @click="openWorkOrders"
                v-hasPermi="['officecare:workorder:list']"
              >工单</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getDraftList"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup name="OfficeCareSmartReport">
import { useRouter } from 'vue-router'
import { listCustomerOrgOptions } from '@/api/officecare/customerOrg'
import { listDeviceOptions } from '@/api/officecare/device'
import {
  analyzeDeviceIssue,
  confirmAgentDraft,
  deleteAgentDraft,
  getAgentDraft,
  listAgentAssigneeOptions,
  listAgentDraft,
  updateAgentDraft
} from '@/api/officecare/agent'

const { proxy } = getCurrentInstance()
const router = useRouter()

const priorityOptions = [
  { value: '0', label: '低' },
  { value: '1', label: '中' },
  { value: '2', label: '高' },
  { value: '3', label: '紧急' }
]
const draftStatusOptions = [
  { value: '0', label: '待确认', type: 'warning' },
  { value: '1', label: '已确认', type: 'success' },
  { value: '2', label: '已删除', type: 'info' }
]

const activeTab = ref('analyze')
const customerOptions = ref([])
const deviceOptions = ref([])
const assigneeOptions = ref([])
const currentDraft = ref({})
const draftList = ref([])
const analyzing = ref(false)
const saving = ref(false)
const confirming = ref(false)
const deletingDraftId = ref()
const historyLoading = ref(false)
const total = ref(0)

const data = reactive({
  analyzeForm: {
    customerOrgId: undefined,
    deviceId: undefined,
    issueDescription: ''
  },
  draftForm: {
    title: '',
    faultDescription: '',
    priority: '1',
    assigneeUserId: undefined,
    workOrderRemark: ''
  },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    draftTitle: undefined,
    deviceName: undefined,
    status: undefined
  }
})

const { analyzeForm, draftForm, queryParams } = toRefs(data)
const analyzeRules = {
  customerOrgId: [{ required: true, message: '客户单位不能为空', trigger: 'change' }],
  deviceId: [{ required: true, message: '报修设备不能为空', trigger: 'change' }],
  issueDescription: [
    { required: true, message: '故障现象不能为空', trigger: 'blur' },
    { min: 2, max: 2000, message: '故障描述长度必须在2到2000个字符之间', trigger: 'blur' }
  ]
}
const draftRules = {
  title: [
    { required: true, message: '工单标题不能为空', trigger: 'blur' },
    { max: 150, message: '标题长度不能超过150个字符', trigger: 'blur' }
  ],
  faultDescription: [
    { required: true, message: '故障描述不能为空', trigger: 'blur' },
    { max: 2000, message: '故障描述不能超过2000个字符', trigger: 'blur' }
  ],
  priority: [{ required: true, message: '优先级不能为空', trigger: 'change' }],
  workOrderRemark: [{ max: 500, message: '备注不能超过500个字符', trigger: 'blur' }]
}

const filteredDevices = computed(() => deviceOptions.value.filter(
  item => item.customerOrgId === analyzeForm.value.customerOrgId
))
const isPending = computed(() => currentDraft.value.status === '0')

function loadOptions() {
  return Promise.all([
    listCustomerOrgOptions(),
    listDeviceOptions(),
    listAgentAssigneeOptions()
  ]).then(([customers, devices, assignees]) => {
    customerOptions.value = customers.data || []
    deviceOptions.value = devices.data || []
    assigneeOptions.value = assignees.data || []
  })
}

function handleCustomerChange() {
  const selected = deviceOptions.value.find(item => item.deviceId === analyzeForm.value.deviceId)
  if (!selected || selected.customerOrgId !== analyzeForm.value.customerOrgId) {
    analyzeForm.value.deviceId = undefined
  }
}

function submitAnalyze() {
  proxy.$refs.analyzeRef.validate(valid => {
    if (!valid) return
    analyzing.value = true
    analyzeDeviceIssue({
      deviceId: analyzeForm.value.deviceId,
      issueDescription: analyzeForm.value.issueDescription
    }).then(response => {
      setCurrentDraft(response.data)
      proxy.$modal.msgSuccess('分析完成，已生成待确认草稿')
      getDraftList()
    }).finally(() => {
      analyzing.value = false
    })
  })
}

function setCurrentDraft(draft) {
  currentDraft.value = draft || {}
  draftForm.value = {
    title: draft?.draftTitle || '',
    faultDescription: draft?.draftFaultDescription || '',
    priority: draft?.draftPriority || '1',
    assigneeUserId: draft?.assigneeUserId,
    workOrderRemark: draft?.workOrderRemark || ''
  }
  nextTick(() => proxy.$refs.draftRef?.clearValidate())
}

function validateDraft(callback) {
  proxy.$refs.draftRef.validate(valid => {
    if (valid) callback()
  })
}

function saveDraft() {
  validateDraft(() => {
    saving.value = true
    updateAgentDraft(currentDraft.value.draftId, draftForm.value).then(response => {
      setCurrentDraft(response.data)
      proxy.$modal.msgSuccess('草稿已保存')
      getDraftList()
    }).finally(() => {
      saving.value = false
    })
  })
}

function confirmDraft() {
  validateDraft(() => {
    proxy.$modal.confirm('确认已人工检查草稿，并创建正式售后工单吗？此操作不可撤销。').then(() => {
      confirming.value = true
      return confirmAgentDraft(currentDraft.value.draftId, draftForm.value)
    }).then(response => {
      const workOrder = response.data || {}
      currentDraft.value = {
        ...currentDraft.value,
        status: '1',
        workOrderId: workOrder.workOrderId,
        workOrderNo: workOrder.workOrderNo
      }
      proxy.$modal.msgSuccess(`正式工单 ${workOrder.workOrderNo || ''} 已创建`)
      getDraftList()
    }).finally(() => {
      confirming.value = false
    }).catch(() => {})
  })
}

function getDraftList() {
  historyLoading.value = true
  listAgentDraft(queryParams.value).then(response => {
    draftList.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    historyLoading.value = false
  })
}

function viewDraft(row) {
  getAgentDraft(row.draftId).then(response => {
    setCurrentDraft(response.data)
    activeTab.value = 'analyze'
  })
}

function deleteCurrentDraft() {
  deleteDraft(currentDraft.value)
}

function deleteDraft(row) {
  const title = row.draftTitle || draftForm.value.title || `#${row.draftId}`
  proxy.$modal.confirm(
    `确认删除草稿“${title}”吗？删除后不能恢复，但系统会保留审计记录。`
  ).then(() => {
    deletingDraftId.value = row.draftId
    return deleteAgentDraft(row.draftId)
  }).then(() => {
    proxy.$modal.msgSuccess('草稿已删除')
    if (currentDraft.value.draftId === row.draftId) {
      setCurrentDraft({})
    }
    getDraftList()
  }).finally(() => {
    deletingDraftId.value = undefined
  }).catch(() => {})
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getDraftList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleTabChange(name) {
  if (name === 'history') getDraftList()
}

function openWorkOrders() {
  router.push('/officecare/service/work-order')
}

function deviceLabel(device) {
  return `${device.deviceName}（${device.serialNumber}）· ${device.brandName} ${device.modelName}`
}

function assigneeLabel(user) {
  const name = user.nickName || user.userName
  return user.deptName ? `${name} · ${user.deptName}` : name
}

function draftStatusMeta(value) {
  return draftStatusOptions.find(item => item.value === value) || { label: '未知', type: 'info' }
}

function toolLabel(name) {
  return ({
    inspect_device_context: '核验设备',
    search_product_knowledge: '检索知识库',
    build_work_order_draft: '生成草稿'
  })[name] || name
}

function formatScore(score) {
  return typeof score === 'number' ? `${(score * 100).toFixed(1)}%` : '-'
}

loadOptions()
getDraftList()
</script>

<style scoped lang="scss">
.smart-report-page {
  max-width: 1380px;
  margin: 0 auto;
}

.page-alert {
  margin-bottom: 16px;
}

.analysis-card,
.result-card,
.draft-card {
  margin-bottom: 18px;
}

.card-title,
.result-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.card-title h3,
.result-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.card-title p,
.result-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.action-hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

// 分析结论：3px 细色条 + 柔和底色，替代原来的 4px 硬边
.summary-block {
  padding: 15px 18px;
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 0 var(--oc-radius-sm) var(--oc-radius-sm) 0;
  line-height: 1.75;
  white-space: pre-wrap;
}

.evidence-row {
  margin-top: 20px;
}

.evidence-row h4,
.citation-section h4 {
  margin: 4px 0 12px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.text-list,
.warning-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
}

.text-list li + li,
.warning-list li + li {
  margin-top: 6px;
}

// 暗色下 Element 的 warning-dark-2 对比度偏低，改用模式感知的 warning 色
.warning-list {
  color: var(--el-color-warning);
}

.citation-section {
  margin-top: 22px;
}

.citation-card {
  margin-bottom: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}

.citation-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.citation-heading strong {
  font-size: 13px;
  font-weight: 600;
}

.citation-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.citation-card blockquote {
  margin: 10px 0 0;
  padding-left: 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.7;
  border-left: 3px solid var(--el-color-primary-light-7);
}

.trace-section {
  margin-top: 18px;
}

.trace-note {
  margin-bottom: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.inline-result {
  width: 100%;
  padding: 12px 0;
}

.table-device {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.table-device span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

// 引用是"证据"，轻微抬起能让人更愿意逐条读
@media (hover: hover) and (pointer: fine) {
  .citation-card {
    transition:
      transform var(--oc-dur-base) var(--oc-ease-out),
      border-color var(--oc-dur-base) var(--oc-ease-out),
      box-shadow var(--oc-dur-base) var(--oc-ease-out);
  }

  .citation-card:hover {
    border-color: var(--oc-accent-200);
    box-shadow: var(--oc-shadow-sm);
    transform: translateY(-1px);
  }
}

@media (max-width: 768px) {
  .card-title,
  .result-heading,
  .citation-heading {
    flex-direction: column;
  }

  .result-tags {
    justify-content: flex-start;
  }

  .action-hint {
    display: block;
    margin: 10px 0 0;
  }
}
</style>
