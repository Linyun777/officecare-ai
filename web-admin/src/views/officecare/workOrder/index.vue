<template>
  <div class="app-container work-order-page">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
      label-width="76px"
    >
      <el-form-item label="工单编号" prop="workOrderNo">
        <el-input
          v-model="queryParams.workOrderNo"
          placeholder="请输入工单编号"
          clearable
          style="width: 210px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="工单标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入标题关键词"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户单位" prop="customerOrgId">
        <el-select
          v-model="queryParams.customerOrgId"
          placeholder="全部客户"
          clearable
          filterable
          style="width: 210px"
          @change="handleQueryCustomerChange"
        >
          <el-option
            v-for="customer in customerOptions"
            :key="customer.customerOrgId"
            :label="customer.customerName"
            :value="customer.customerOrgId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="设备" prop="deviceId">
        <el-select
          v-model="queryParams.deviceId"
          placeholder="全部设备"
          clearable
          filterable
          style="width: 210px"
        >
          <el-option
            v-for="device in filteredQueryDevices"
            :key="device.deviceId"
            :label="deviceLabel(device)"
            :value="device.deviceId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="queryParams.priority" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人" prop="assigneeUserId">
        <el-select
          v-model="queryParams.assigneeUserId"
          placeholder="全部负责人"
          clearable
          filterable
          style="width: 180px"
        >
          <el-option
            v-for="user in assigneeOptions"
            :key="user.userId"
            :label="assigneeLabel(user)"
            :value="user.userId"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['officecare:workorder:add']"
        >新增工单</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single || selectedRows[0]?.status !== '0'"
          @click="handleUpdate()"
          v-hasPermi="['officecare:workorder:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="!canDeleteSelected"
          @click="handleDelete()"
          v-hasPermi="['officecare:workorder:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshPage" />
    </el-row>

    <el-table
      v-loading="loading"
      :data="workOrderList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工单编号" prop="workOrderNo" width="205" show-overflow-tooltip />
      <el-table-column label="标题 / 设备" min-width="220">
        <template #default="scope">
          <div class="title-cell">
            <el-link type="primary" :underline="false" @click="openDetail(scope.row)">
              {{ scope.row.title }}
            </el-link>
            <span>{{ scope.row.deviceName }} · {{ scope.row.modelName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="客户单位" prop="customerName" min-width="170" show-overflow-tooltip />
      <el-table-column label="优先级" prop="priority" width="90" align="center">
        <template #default="scope">
          <el-tag :type="priorityMeta(scope.row.priority).type" effect="plain">
            {{ priorityMeta(scope.row.priority).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template #default="scope">
          <el-tag :type="statusMeta(scope.row.status).type">
            {{ statusMeta(scope.row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="负责人" min-width="120" show-overflow-tooltip>
        <template #default="scope">
          {{ scope.row.assigneeNickName || scope.row.assigneeUserName || '未指派' }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="165" />
      <el-table-column label="操作" width="320" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="openDetail(scope.row)">详情</el-button>
          <el-button
            v-if="scope.row.status === '0'"
            link type="primary" icon="Edit" @click="handleUpdate(scope.row)"
            v-hasPermi="['officecare:workorder:edit']"
          >修改</el-button>
          <el-button
            v-if="scope.row.status === '0'"
            link type="success" icon="VideoPlay" @click="openTransition(scope.row, '1')"
            v-hasPermi="['officecare:workorder:status']"
          >开始处理</el-button>
          <el-button
            v-if="scope.row.status === '1'"
            link type="success" icon="CircleCheck" @click="openTransition(scope.row, '2')"
            v-hasPermi="['officecare:workorder:status']"
          >完成</el-button>
          <el-dropdown
            trigger="click"
            @command="command => handleMoreCommand(command, scope.row)"
            v-hasPermi="[
              'officecare:workorder:assign',
              'officecare:workorder:record',
              'officecare:workorder:ai',
              'officecare:workorder:status',
              'officecare:workorder:remove'
            ]"
          >
            <el-button link type="primary">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  command="assign"
                  :disabled="scope.row.status === '3'"
                  v-hasPermi="['officecare:workorder:assign']"
                >调整负责人</el-dropdown-item>
                <el-dropdown-item
                  command="record"
                  :disabled="scope.row.status === '3'"
                  v-hasPermi="['officecare:workorder:record']"
                >添加处理记录</el-dropdown-item>
                <el-dropdown-item
                  command="ai"
                  v-hasPermi="['officecare:workorder:ai']"
                >AI处理建议</el-dropdown-item>
                <el-dropdown-item
                  v-if="scope.row.status === '2'"
                  command="reopen"
                  v-hasPermi="['officecare:workorder:status']"
                >重新打开</el-dropdown-item>
                <el-dropdown-item
                  v-if="scope.row.status !== '3'"
                  command="close"
                  divided
                  v-hasPermi="['officecare:workorder:status']"
                >关闭工单</el-dropdown-item>
                <el-dropdown-item
                  v-if="scope.row.status === '0'"
                  command="delete"
                  divided
                  v-hasPermi="['officecare:workorder:remove']"
                >删除工单</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="formTitle" v-model="formOpen" width="820px" append-to-body>
      <el-form ref="workOrderRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户单位" prop="customerOrgId">
              <el-select
                v-model="form.customerOrgId"
                placeholder="请选择客户单位"
                filterable
                style="width: 100%"
                @change="handleFormCustomerChange"
              >
                <el-option
                  v-for="customer in customerOptions"
                  :key="customer.customerOrgId"
                  :label="customer.customerName"
                  :value="customer.customerOrgId"
                  :disabled="customer.status === '1'"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备" prop="deviceId">
              <el-select
                v-model="form.deviceId"
                placeholder="请先选择客户单位"
                filterable
                style="width: 100%"
                :disabled="!form.customerOrgId"
              >
                <el-option
                  v-for="device in filteredFormDevices"
                  :key="device.deviceId"
                  :label="deviceLabel(device)"
                  :value="device.deviceId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="工单标题" prop="title">
          <el-input v-model="form.title" maxlength="150" show-word-limit placeholder="用一句话概括故障" />
        </el-form-item>
        <el-form-item label="故障描述" prop="faultDescription">
          <el-input
            v-model="form.faultDescription"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="描述故障现象、错误信息、发生频率和已尝试的操作"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-radio-group v-model="form.priority">
                <el-radio v-for="item in priorityOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人" prop="assigneeUserId">
              <el-select v-model="form.assigneeUserId" placeholder="可暂不指派" clearable filterable style="width: 100%">
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
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确定</el-button>
          <el-button @click="formOpen = false">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" title="工单详情" size="720px" append-to-body>
      <div v-loading="detailLoading" v-if="detail.workOrderId" class="detail-body">
        <div class="detail-heading">
          <div>
            <span class="detail-no">{{ detail.workOrderNo }}</span>
            <h2>{{ detail.title }}</h2>
          </div>
          <div>
            <el-tag :type="priorityMeta(detail.priority).type" effect="plain">{{ priorityMeta(detail.priority).label }}</el-tag>
            <el-tag :type="statusMeta(detail.status).type">{{ statusMeta(detail.status).label }}</el-tag>
          </div>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户单位">{{ detail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ detail.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="品牌 / 型号">{{ detail.brandName }} / {{ detail.modelName }}</el-descriptions-item>
          <el-descriptions-item label="序列号">{{ detail.serialNumber }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.assigneeNickName || detail.assigneeUserName || '未指派' }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ detail.reporterNickName || detail.reporterUserName }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="完成 / 关闭">{{ detail.completionTime || '-' }} / {{ detail.closeTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="故障描述" :span="2"><div class="description-text">{{ detail.faultDescription }}</div></el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-button
            v-if="detail.status !== '3'"
            icon="EditPen"
            @click="openRecord(detail)"
            v-hasPermi="['officecare:workorder:record']"
          >添加记录</el-button>
          <el-button
            v-if="detail.status !== '3'"
            icon="User"
            @click="openAssign(detail)"
            v-hasPermi="['officecare:workorder:assign']"
          >调整负责人</el-button>
          <el-button
            type="primary"
            icon="MagicStick"
            @click="openAiSuggestion(detail)"
            v-hasPermi="['officecare:workorder:ai']"
          >AI处理建议</el-button>
        </div>

        <h3 class="timeline-title">处理时间线</h3>
        <el-empty v-if="!detail.records?.length" description="暂无处理记录" :image-size="70" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="record in detail.records"
            :key="record.recordId"
            :timestamp="record.createTime"
            :type="recordTimelineType(record)"
            placement="top"
          >
            <el-card shadow="never" class="record-card">
              <div class="record-heading">
                <strong>{{ actionLabel(record.actionType) }}</strong>
                <span>{{ record.operatorName }}</span>
              </div>
              <div v-if="record.fromStatus !== null && record.toStatus !== null && record.fromStatus !== record.toStatus" class="record-status">
                {{ statusMeta(record.fromStatus).label }} → {{ statusMeta(record.toStatus).label }}
              </div>
              <p>{{ record.content }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

    <el-dialog title="调整负责人" v-model="assignOpen" width="520px" append-to-body>
      <el-form ref="assignRef" :model="assignForm" label-width="82px">
        <el-form-item label="负责人">
          <el-select v-model="assignForm.assigneeUserId" clearable filterable placeholder="清空表示取消指派" style="width: 100%">
            <el-option v-for="user in assigneeOptions" :key="user.userId" :label="assigneeLabel(user)" :value="user.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="指派说明">
          <el-input v-model="assignForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAssign">确定</el-button>
        <el-button @click="assignOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="transitionTitle" v-model="transitionOpen" width="560px" append-to-body>
      <el-form ref="transitionRef" :model="transitionForm" :rules="transitionRules" label-width="88px">
        <el-alert :title="transitionHint" type="info" :closable="false" show-icon class="mb16" />
        <el-form-item label="处理说明" prop="content">
          <el-input v-model="transitionForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitTransition">确定</el-button>
        <el-button @click="transitionOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="添加处理记录" v-model="recordOpen" width="560px" append-to-body>
      <el-form ref="recordRef" :model="recordForm" :rules="recordRules" label-width="88px">
        <el-form-item label="处理内容" prop="content">
          <el-input v-model="recordForm.content" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="记录排查步骤、现场现象、客户沟通或下一步计划" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitRecord">确定</el-button>
        <el-button @click="recordOpen = false">取消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="AI处理建议" v-model="aiOpen" width="780px" append-to-body>
      <div v-loading="aiLoading" class="ai-panel">
        <template v-if="aiSuggestion.answer">
          <el-alert
            :title="`${aiSuggestion.brandName} ${aiSuggestion.modelName} · ${aiSuggestion.knowledgeBaseName}`"
            type="success"
            :closable="false"
            show-icon
          />
          <div v-if="aiSuggestion.answer.answered" class="ai-answer">{{ aiSuggestion.answer.answer }}</div>
          <el-result v-else icon="warning" title="证据不足" :sub-title="aiSuggestion.answer.answer || '当前知识库暂时无法支持这个故障问题。'" />
          <div v-if="aiSuggestion.answer.citations?.length" class="ai-citations">
            <h4>资料来源</h4>
            <el-collapse>
              <el-collapse-item
                v-for="citation in aiSuggestion.answer.citations"
                :key="citation.citationIndex"
                :name="citation.citationIndex"
              >
                <template #title>
                  [资料{{ citation.citationIndex }}] {{ citation.documentTitle }}
                  <el-tag size="small" effect="plain" class="citation-tag">{{ citation.pageNumber ? `第 ${citation.pageNumber} 页` : '页码未知' }}</el-tag>
                </template>
                <blockquote>{{ citation.quote }}</blockquote>
                <span>综合相关度 {{ formatScore(citation.score) }}</span>
              </el-collapse-item>
            </el-collapse>
          </div>
          <p class="ai-note">AI建议仅作为排查参考；涉及拆机、带电或高温部件时，必须遵循原厂安全手册。</p>
        </template>
      </div>
      <template #footer><el-button @click="aiOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="OfficeCareWorkOrder">
import { listCustomerOrgOptions } from '@/api/officecare/customerOrg'
import { listDeviceOptions } from '@/api/officecare/device'
import {
  addWorkOrder,
  addWorkOrderRecord,
  assignWorkOrder,
  delWorkOrder,
  generateWorkOrderAiSuggestion,
  getWorkOrder,
  listWorkOrder,
  listWorkOrderAssigneeOptions,
  transitionWorkOrder,
  updateWorkOrder
} from '@/api/officecare/workOrder'

const { proxy } = getCurrentInstance()

const priorityOptions = [
  { value: '0', label: '低', type: 'info' },
  { value: '1', label: '中', type: '' },
  { value: '2', label: '高', type: 'warning' },
  { value: '3', label: '紧急', type: 'danger' }
]
const statusOptions = [
  { value: '0', label: '待处理', type: 'warning' },
  { value: '1', label: '处理中', type: 'primary' },
  { value: '2', label: '已完成', type: 'success' },
  { value: '3', label: '已关闭', type: 'info' }
]

const workOrderList = ref([])
const customerOptions = ref([])
const deviceOptions = ref([])
const assigneeOptions = ref([])
const selectedRows = ref([])
const loading = ref(true)
const showSearch = ref(true)
const single = ref(true)
const total = ref(0)

const formOpen = ref(false)
const formTitle = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref({})
const assignOpen = ref(false)
const assignForm = ref({})
const transitionOpen = ref(false)
const transitionTitle = ref('')
const transitionHint = ref('')
const transitionForm = ref({})
const recordOpen = ref(false)
const recordForm = ref({})
const aiOpen = ref(false)
const aiLoading = ref(false)
const aiSuggestion = ref({})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    workOrderNo: undefined,
    title: undefined,
    customerOrgId: undefined,
    deviceId: undefined,
    priority: undefined,
    status: undefined,
    assigneeUserId: undefined
  },
  rules: {
    customerOrgId: [{ required: true, message: '客户单位不能为空', trigger: 'change' }],
    deviceId: [{ required: true, message: '设备不能为空', trigger: 'change' }],
    title: [
      { required: true, message: '工单标题不能为空', trigger: 'blur' },
      { max: 150, message: '标题长度不能超过150个字符', trigger: 'blur' }
    ],
    faultDescription: [
      { required: true, message: '故障描述不能为空', trigger: 'blur' },
      { max: 2000, message: '故障描述不能超过2000个字符', trigger: 'blur' }
    ],
    priority: [{ required: true, message: '优先级不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)
const transitionRules = {
  content: [{ required: true, message: '处理说明不能为空', trigger: 'blur' }]
}
const recordRules = {
  content: [{ required: true, message: '处理内容不能为空', trigger: 'blur' }]
}

const filteredQueryDevices = computed(() => {
  if (!queryParams.value.customerOrgId) return deviceOptions.value
  return deviceOptions.value.filter(item => item.customerOrgId === queryParams.value.customerOrgId)
})
const filteredFormDevices = computed(() => deviceOptions.value.filter(
  item => item.customerOrgId === form.value.customerOrgId
))
const canDeleteSelected = computed(() => selectedRows.value.length > 0
  && selectedRows.value.every(item => item.status === '0'))

function loadOptions() {
  return Promise.all([
    listCustomerOrgOptions(),
    listDeviceOptions(),
    listWorkOrderAssigneeOptions()
  ]).then(([customers, devices, assignees]) => {
    customerOptions.value = customers.data || []
    deviceOptions.value = devices.data || []
    assigneeOptions.value = assignees.data || []
  })
}

function getList() {
  loading.value = true
  listWorkOrder(queryParams.value).then(response => {
    workOrderList.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function refreshPage() {
  loadOptions()
  getList()
}

function resetFormData() {
  form.value = {
    workOrderId: undefined,
    customerOrgId: undefined,
    deviceId: undefined,
    title: undefined,
    faultDescription: undefined,
    priority: '1',
    assigneeUserId: undefined,
    remark: undefined
  }
  proxy.resetForm('workOrderRef')
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleQueryCustomerChange() {
  const selected = deviceOptions.value.find(item => item.deviceId === queryParams.value.deviceId)
  if (selected && selected.customerOrgId !== queryParams.value.customerOrgId) {
    queryParams.value.deviceId = undefined
  }
}

function handleFormCustomerChange() {
  const selected = deviceOptions.value.find(item => item.deviceId === form.value.deviceId)
  if (!selected || selected.customerOrgId !== form.value.customerOrgId) {
    form.value.deviceId = undefined
  }
}

function handleSelectionChange(selection) {
  selectedRows.value = selection
  single.value = selection.length !== 1
}

function handleAdd() {
  resetFormData()
  loadOptions().then(() => {
    formTitle.value = '新增售后工单'
    formOpen.value = true
  })
}

function handleUpdate(row) {
  const target = row || selectedRows.value[0]
  if (!target) return
  resetFormData()
  Promise.all([getWorkOrder(target.workOrderId), loadOptions()]).then(([response]) => {
    form.value = response.data
    formTitle.value = '修改售后工单'
    formOpen.value = true
  })
}

function submitForm() {
  proxy.$refs.workOrderRef.validate(valid => {
    if (!valid) return
    const editing = Boolean(form.value.workOrderId)
    const request = editing ? updateWorkOrder(form.value) : addWorkOrder(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(editing ? '工单修改成功' : '工单创建成功')
      formOpen.value = false
      getList()
    })
  })
}

function openDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  getWorkOrder(row.workOrderId).then(response => {
    detail.value = response.data
  }).finally(() => {
    detailLoading.value = false
  })
}

function reloadDetailIfOpen(workOrderId) {
  if (detailOpen.value && detail.value.workOrderId === workOrderId) {
    openDetail({ workOrderId })
  }
}

function openAssign(row) {
  assignForm.value = {
    workOrderId: row.workOrderId,
    assigneeUserId: row.assigneeUserId,
    content: undefined
  }
  assignOpen.value = true
}

function submitAssign() {
  assignWorkOrder(assignForm.value.workOrderId, assignForm.value).then(() => {
    proxy.$modal.msgSuccess('负责人调整成功')
    assignOpen.value = false
    getList()
    reloadDetailIfOpen(assignForm.value.workOrderId)
  })
}

function openTransition(row, targetStatus) {
  transitionForm.value = {
    workOrderId: row.workOrderId,
    targetStatus,
    content: undefined
  }
  transitionTitle.value = statusActionName(targetStatus)
  transitionHint.value = transitionHintText(targetStatus)
  transitionOpen.value = true
  nextTick(() => proxy.resetForm('transitionRef'))
}

function submitTransition() {
  proxy.$refs.transitionRef.validate(valid => {
    if (!valid) return
    const workOrderId = transitionForm.value.workOrderId
    transitionWorkOrder(workOrderId, transitionForm.value).then(() => {
      proxy.$modal.msgSuccess(`${transitionTitle.value}成功`)
      transitionOpen.value = false
      getList()
      reloadDetailIfOpen(workOrderId)
    })
  })
}

function openRecord(row) {
  recordForm.value = { workOrderId: row.workOrderId, content: undefined }
  recordOpen.value = true
  nextTick(() => proxy.resetForm('recordRef'))
}

function submitRecord() {
  proxy.$refs.recordRef.validate(valid => {
    if (!valid) return
    const workOrderId = recordForm.value.workOrderId
    addWorkOrderRecord(workOrderId, recordForm.value).then(() => {
      proxy.$modal.msgSuccess('处理记录已保存')
      recordOpen.value = false
      getList()
      reloadDetailIfOpen(workOrderId)
    })
  })
}

function openAiSuggestion(row) {
  aiOpen.value = true
  aiLoading.value = true
  aiSuggestion.value = {}
  generateWorkOrderAiSuggestion(row.workOrderId).then(response => {
    aiSuggestion.value = response.data
  }).finally(() => {
    aiLoading.value = false
  })
}

function handleDelete(row) {
  const targetRows = row ? [row] : selectedRows.value
  const ids = targetRows.map(item => item.workOrderId)
  if (!ids.length) return
  proxy.$modal.confirm(`是否确认删除选中的 ${ids.length} 个待处理工单？`).then(() => {
    return delWorkOrder(ids)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

function handleMoreCommand(command, row) {
  if (command === 'assign') openAssign(row)
  if (command === 'record') openRecord(row)
  if (command === 'ai') openAiSuggestion(row)
  if (command === 'reopen') openTransition(row, '1')
  if (command === 'close') openTransition(row, '3')
  if (command === 'delete') handleDelete(row)
}

function priorityMeta(value) {
  return priorityOptions.find(item => item.value === value) || { label: '未知', type: 'info' }
}

function statusMeta(value) {
  return statusOptions.find(item => item.value === value) || { label: '未知', type: 'info' }
}

function statusActionName(targetStatus) {
  return ({ '1': '开始 / 重新处理', '2': '完成工单', '3': '关闭工单' })[targetStatus]
}

function transitionHintText(targetStatus) {
  return ({
    '1': '工单将进入处理中；未指派时由当前用户自动领取。',
    '2': '请确认故障已经处理完成，并填写结果。',
    '3': '关闭后不能继续修改、指派或添加处理记录。'
  })[targetStatus]
}

function actionLabel(actionType) {
  return ({ CREATE: '创建工单', EDIT: '修改工单', ASSIGN: '调整负责人', STATUS: '状态流转', COMMENT: '处理记录' })[actionType] || actionType
}

function recordTimelineType(record) {
  if (record.actionType === 'STATUS' && record.toStatus === '2') return 'success'
  if (record.actionType === 'STATUS' && record.toStatus === '3') return 'info'
  if (record.actionType === 'ASSIGN') return 'primary'
  return 'warning'
}

function deviceLabel(device) {
  return `${device.deviceName}（${device.serialNumber}）`
}

function assigneeLabel(user) {
  const name = user.nickName || user.userName
  return user.deptName ? `${name} · ${user.deptName}` : name
}

function formatScore(score) {
  return typeof score === 'number' ? `${(score * 100).toFixed(1)}%` : '-'
}

loadOptions()
getList()
</script>

<style scoped lang="scss">
.title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-cell span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-body {
  padding: 0 4px 24px;
}

.detail-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.detail-heading h2 {
  margin: 8px 0 0;
  color: var(--el-text-color-primary);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.detail-heading > div:last-child {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

// 工单编号是会被念出来、抄下来的标识：等宽字体 + 表格数字
.detail-no {
  color: var(--el-text-color-secondary);
  font-family: var(--oc-font-mono);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.description-text,
.ai-answer {
  line-height: 1.75;
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  gap: 10px;
  margin: 20px 0 24px;
}

.timeline-title {
  margin: 0 0 18px;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}

.record-card {
  border: 1px solid var(--el-border-color-lighter);
  transition:
    border-color var(--oc-dur-base) var(--oc-ease-out),
    box-shadow var(--oc-dur-base) var(--oc-ease-out);
}

.record-card :deep(.el-card__body) {
  padding: 14px 16px;
}

@media (hover: hover) and (pointer: fine) {
  .record-card:hover {
    border-color: var(--oc-accent-200);
    box-shadow: var(--oc-shadow-xs);
  }
}

.record-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.record-heading strong {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.record-card p {
  margin: 8px 0 0;
  line-height: 1.6;
  white-space: pre-wrap;
}

.record-status {
  margin-top: 8px;
  color: var(--el-color-primary);
  font-size: 13px;
}

.mb16 {
  margin-bottom: 16px;
}

.ai-panel {
  min-height: 180px;
}

.ai-answer {
  margin: 20px 0;
  padding: 16px 18px;
  background: var(--el-fill-color-light);
  border-radius: var(--oc-radius-md);
}

.ai-citations {
  margin-top: 18px;
}

.citation-tag {
  margin-left: 10px;
}

.ai-citations blockquote {
  margin: 8px 0;
  padding-left: 12px;
  line-height: 1.6;
  border-left: 3px solid var(--el-color-primary-light-7);
}

// 暗色下 Element 的 warning-dark-2 对比度偏低，改用模式感知的 warning 色
.ai-note {
  margin-top: 18px;
  color: var(--el-color-warning);
  font-size: 13px;
}
</style>
