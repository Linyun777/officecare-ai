<template>
  <div class="app-container">
    <el-alert
      title="上传后将自动完成文档解析、文本切分和 Milvus 向量索引；浏览器不会直接访问 FastAPI 或 Milvus。"
      type="info"
      show-icon
      :closable="false"
      class="storage-alert"
    />

    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
      label-width="76px"
    >
      <el-form-item label="所属知识库" prop="knowledgeBaseId">
        <el-select
          v-model="queryParams.knowledgeBaseId"
          placeholder="请选择知识库"
          clearable
          filterable
          style="width: 240px"
        >
          <el-option
            v-for="item in knowledgeBaseOptions"
            :key="item.knowledgeBaseId"
            :label="item.knowledgeBaseName"
            :value="item.knowledgeBaseId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="文档标题" prop="documentTitle">
        <el-input
          v-model="queryParams.documentTitle"
          placeholder="请输入文档标题"
          clearable
          style="width: 210px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="文件类型" prop="fileExtension">
        <el-select v-model="queryParams.fileExtension" placeholder="全部类型" clearable style="width: 140px">
          <el-option label="PDF" value="pdf" />
          <el-option label="Word DOCX" value="docx" />
          <el-option label="TXT" value="txt" />
        </el-select>
      </el-form-item>
      <el-form-item label="处理状态" prop="processStatus">
        <el-select v-model="queryParams.processStatus" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="待处理" value="0" />
          <el-option label="处理中" value="1" />
          <el-option label="处理成功" value="2" />
          <el-option label="处理失败" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="索引状态" prop="indexStatus">
        <el-select v-model="queryParams.indexStatus" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="待索引" value="0" />
          <el-option label="索引中" value="1" />
          <el-option label="索引成功" value="2" />
          <el-option label="索引失败" value="3" />
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
          icon="Upload"
          @click="handleUploadDocument"
          v-hasPermi="['officecare:document:upload']"
        >上传文档</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleEdit"
          v-hasPermi="['officecare:document:edit']"
        >修改信息</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['officecare:document:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshPage" />
    </el-row>

    <el-table v-loading="loading" :data="documentList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="文档标题" prop="documentTitle" min-width="210" show-overflow-tooltip />
      <el-table-column label="知识库" prop="knowledgeBaseName" min-width="210" show-overflow-tooltip />
      <el-table-column label="当前版本" width="95" align="center">
        <template #default="scope">V{{ scope.row.currentVersionNo }}</template>
      </el-table-column>
      <el-table-column label="文件名" prop="originalFileName" min-width="220" show-overflow-tooltip />
      <el-table-column label="类型" prop="fileExtension" width="80" align="center">
        <template #default="scope">{{ (scope.row.fileExtension || '').toUpperCase() }}</template>
      </el-table-column>
      <el-table-column label="大小" width="105" align="right">
        <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="处理状态" width="105" align="center">
        <template #default="scope">
          <el-tooltip
            :content="scope.row.processMessage || processStatusText(scope.row.processStatus)"
            placement="top"
          >
            <el-tag :type="processStatusType(scope.row.processStatus)" effect="plain">
              {{ processStatusText(scope.row.processStatus) }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="索引状态" width="105" align="center">
        <template #default="scope">
          <el-tooltip
            :content="scope.row.indexMessage || indexStatusText(scope.row.indexStatus)"
            placement="top"
          >
            <el-tag :type="processStatusType(scope.row.indexStatus)" effect="plain">
              {{ indexStatusText(scope.row.indexStatus) }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="页数" width="75" align="center">
        <template #default="scope">{{ scope.row.pageCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="切片" width="75" align="center">
        <template #default="scope">{{ scope.row.indexedChunkCount ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180" align="center">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime || scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="455" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            v-if="isProcessable(scope.row) && ['0', '3'].includes(scope.row.processStatus)"
            link
            type="primary"
            icon="RefreshRight"
            :loading="processingVersionId === scope.row.currentVersionId"
            @click="handleProcessVersion(scope.row)"
            v-hasPermi="['officecare:document:parse']"
          >{{ scope.row.processStatus === '3' ? '重试解析' : '开始解析' }}</el-button>
          <el-button
            v-if="scope.row.processStatus === '2' && ['0', '3'].includes(scope.row.indexStatus)"
            link
            type="primary"
            icon="Connection"
            :loading="indexingVersionId === scope.row.currentVersionId"
            @click="handleIndexVersion(scope.row)"
            v-hasPermi="['officecare:document:index']"
          >{{ scope.row.indexStatus === '3' ? '重试索引' : '开始索引' }}</el-button>
          <el-button
            link
            type="primary"
            icon="Download"
            @click="handleDownload(scope.row.currentVersionId, scope.row.originalFileName)"
            v-hasPermi="['officecare:document:download']"
          >下载</el-button>
          <el-button
            link
            type="primary"
            icon="Clock"
            @click="handleVersions(scope.row)"
            v-hasPermi="['officecare:document:query']"
          >版本</el-button>
          <el-button
            link
            type="primary"
            icon="Upload"
            @click="handleUploadVersion(scope.row)"
            v-hasPermi="['officecare:document:upload']"
          >新版本</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleEdit(scope.row)"
            v-hasPermi="['officecare:document:edit']"
          >修改</el-button>
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['officecare:document:remove']"
          >删除</el-button>
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

    <el-dialog :title="uploadDialogTitle" v-model="uploadOpen" width="620px" append-to-body>
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="100px">
        <template v-if="uploadMode === 'document'">
          <el-form-item label="所属知识库" prop="knowledgeBaseId">
            <el-select
              v-model="uploadForm.knowledgeBaseId"
              placeholder="请选择知识库"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="item in knowledgeBaseOptions"
                :key="item.knowledgeBaseId"
                :label="item.knowledgeBaseName"
                :value="item.knowledgeBaseId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="文档标题" prop="documentTitle">
            <el-input
              v-model="uploadForm.documentTitle"
              placeholder="可不填，默认使用文件名"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="uploadForm.remark"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </template>
        <template v-else>
          <el-descriptions :column="1" border class="version-document">
            <el-descriptions-item label="文档">{{ uploadTarget.documentTitle }}</el-descriptions-item>
            <el-descriptions-item label="当前版本">V{{ uploadTarget.currentVersionNo }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            v-model:file-list="fileList"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.docx,.txt"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">拖放文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF、DOCX、TXT，单个文件不超过 30MB。</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="uploading" @click="submitUpload">开始上传</el-button>
          <el-button :disabled="uploading" @click="cancelUpload">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="修改文档信息" v-model="editOpen" width="580px" append-to-body>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="文档标题" prop="documentTitle">
          <el-input v-model="editForm.documentTitle" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="editForm.status">
            <el-radio
              v-for="dict in sys_normal_disable"
              :key="dict.value"
              :value="dict.value"
            >{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitEdit">确定</el-button>
          <el-button @click="editOpen = false">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog :title="`${versionTarget.documentTitle || ''} - 版本历史`" v-model="versionOpen" width="1320px" append-to-body>
      <el-table v-loading="versionLoading" :data="versionList">
        <el-table-column label="版本" width="80" align="center">
          <template #default="scope">V{{ scope.row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="文件名" prop="originalFileName" min-width="220" show-overflow-tooltip />
        <el-table-column label="大小" width="110" align="right">
          <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="SHA-256" prop="fileHash" min-width="190" show-overflow-tooltip />
        <el-table-column label="处理状态" width="105" align="center">
          <template #default="scope">
            <el-tooltip
              :content="scope.row.processMessage || processStatusText(scope.row.processStatus)"
              placement="top"
            >
              <el-tag :type="processStatusType(scope.row.processStatus)" effect="plain">
                {{ processStatusText(scope.row.processStatus) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="页数" width="70" align="center">
          <template #default="scope">{{ scope.row.pageCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="索引状态" width="105" align="center">
          <template #default="scope">
            <el-tooltip
              :content="scope.row.indexMessage || indexStatusText(scope.row.indexStatus)"
              placement="top"
            >
              <el-tag :type="processStatusType(scope.row.indexStatus)" effect="plain">
                {{ indexStatusText(scope.row.indexStatus) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="切片" width="70" align="center">
          <template #default="scope">{{ scope.row.indexedChunkCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="处理说明" prop="processMessage" min-width="190" show-overflow-tooltip />
        <el-table-column label="索引说明" prop="indexMessage" min-width="210" show-overflow-tooltip />
        <el-table-column label="上传时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" width="390" align="center">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="Download"
              @click="handleDownload(scope.row.documentVersionId, scope.row.originalFileName)"
              v-hasPermi="['officecare:document:download']"
            >下载</el-button>
            <el-button
              link
              type="primary"
              icon="View"
              @click="handleTaskDetail(scope.row, 'parse')"
              v-hasPermi="['officecare:document:query']"
            >解析任务</el-button>
            <el-button
              v-if="scope.row.processStatus === '2'"
              link
              type="primary"
              icon="View"
              @click="handleTaskDetail(scope.row, 'index')"
              v-hasPermi="['officecare:document:query']"
            >索引任务</el-button>
            <el-button
              v-if="isProcessable(scope.row) && ['0', '3'].includes(scope.row.processStatus)"
              link
              type="primary"
              icon="RefreshRight"
              :loading="processingVersionId === scope.row.documentVersionId"
              @click="handleProcessVersion(scope.row)"
              v-hasPermi="['officecare:document:parse']"
            >{{ scope.row.processStatus === '3' ? '重试' : '解析' }}</el-button>
            <el-button
              v-if="scope.row.processStatus === '2' && ['0', '3'].includes(scope.row.indexStatus)"
              link
              type="primary"
              icon="Connection"
              :loading="indexingVersionId === scope.row.documentVersionId"
              @click="handleIndexVersion(scope.row)"
              v-hasPermi="['officecare:document:index']"
            >{{ scope.row.indexStatus === '3' ? '重试索引' : '索引' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog :title="taskDialogTitle" v-model="taskOpen" width="720px" append-to-body>
      <el-skeleton v-if="taskLoading" :rows="6" animated />
      <el-empty v-else-if="!taskDetail.taskId" :description="`该版本尚未创建${taskType === 'index' ? '索引' : '解析'}任务`" />
      <el-descriptions v-else :column="2" border>
        <el-descriptions-item label="任务ID">{{ taskDetail.taskId }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="processStatusType(taskDetail.status)" effect="plain">
            {{ processStatusText(taskDetail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行次数">
          {{ taskDetail.attemptCount }} / {{ taskDetail.maxAttempts }}
        </el-descriptions-item>
        <el-descriptions-item :label="taskType === 'index' ? '索引切片' : '页数'">
          {{ taskType === 'index' ? (taskDetail.blockCount ?? '-') : (taskDetail.pageCount ?? '-') }}
        </el-descriptions-item>
        <el-descriptions-item :label="taskType === 'index' ? '索引字符' : '文本块'">
          {{ taskType === 'index' ? (taskDetail.characterCount ?? '-') : (taskDetail.blockCount ?? '-') }}
        </el-descriptions-item>
        <el-descriptions-item v-if="taskType === 'parse'" label="字符数">
          {{ taskDetail.characterCount ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ taskDetail.startedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ taskDetail.finishedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Trace ID" :span="2">
          {{ taskDetail.traceId }}
        </el-descriptions-item>
        <el-descriptions-item label="结果摘要" :span="2">
          {{ taskDetail.resultFileHash || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="错误码" :span="2">
          {{ taskDetail.errorCode || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="错误说明" :span="2">
          {{ taskDetail.errorMessage || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="OfficeCareDocument">
import { saveAs } from 'file-saver'
import { listKnowledgeBaseOptions } from '@/api/officecare/knowledgeBase'
import {
  delDocument,
  downloadDocumentVersion,
  getDocumentIndexTask,
  getDocumentProcessingTask,
  getDocument,
  listDocument,
  listDocumentVersions,
  indexDocumentVersion,
  processDocumentVersion,
  updateDocument,
  uploadDocument,
  uploadDocumentVersion
} from '@/api/officecare/document'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict('sys_normal_disable')

const documentList = ref([])
const knowledgeBaseOptions = ref([])
const versionList = ref([])
const fileList = ref([])
const selectedFile = ref()
const uploadTarget = ref({})
const versionTarget = ref({})
const editForm = ref({})
const taskDetail = ref({})
const uploadOpen = ref(false)
const editOpen = ref(false)
const versionOpen = ref(false)
const taskOpen = ref(false)
const loading = ref(true)
const uploading = ref(false)
const versionLoading = ref(false)
const taskLoading = ref(false)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const uploadMode = ref('document')
const processingVersionId = ref()
const indexingVersionId = ref()
const taskType = ref('parse')
let statusRefreshTimer
let statusRefreshBudget = 0

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    knowledgeBaseId: undefined,
    documentTitle: undefined,
    fileExtension: undefined,
    processStatus: undefined,
    indexStatus: undefined
  },
  uploadForm: {
    knowledgeBaseId: undefined,
    documentTitle: undefined,
    remark: undefined
  },
  uploadRules: {
    knowledgeBaseId: [{ required: true, message: '所属知识库不能为空', trigger: 'change' }],
    documentTitle: [{ max: 200, message: '文档标题长度不能超过200个字符', trigger: 'blur' }]
  },
  editRules: {
    documentTitle: [
      { required: true, message: '文档标题不能为空', trigger: 'blur' },
      { max: 200, message: '文档标题长度不能超过200个字符', trigger: 'blur' }
    ],
    status: [{ required: true, message: '文档状态不能为空', trigger: 'change' }]
  }
})

const { queryParams, uploadForm, uploadRules, editRules } = toRefs(data)
const uploadDialogTitle = computed(() => uploadMode.value === 'document'
  ? '上传知识文档'
  : `上传新版本：${uploadTarget.value.documentTitle || ''}`)
const taskDialogTitle = computed(() => taskType.value === 'index'
  ? '文档向量索引任务'
  : '文档解析任务')

function loadKnowledgeBaseOptions() {
  return listKnowledgeBaseOptions().then(response => {
    knowledgeBaseOptions.value = response.data
  })
}

function getList() {
  loading.value = true
  listDocument(queryParams.value).then(response => {
    documentList.value = response.rows
    total.value = response.total
  }).finally(() => {
    loading.value = false
    scheduleStatusRefresh()
  })
}

function scheduleStatusRefresh() {
  clearTimeout(statusRefreshTimer)
  if (statusRefreshBudget > 0
    && documentList.value.some(item => (
      ['0', '1'].includes(item.processStatus)
      || (item.processStatus === '2' && ['0', '1'].includes(item.indexStatus))
    ))) {
    statusRefreshBudget -= 1
    statusRefreshTimer = setTimeout(getList, 2500)
  }
}

function startStatusRefresh() {
  statusRefreshBudget = 60
  getList()
}

function refreshPage() {
  loadKnowledgeBaseOptions()
  getList()
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.documentId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function resetUpload() {
  uploadForm.value = {
    knowledgeBaseId: undefined,
    documentTitle: undefined,
    remark: undefined
  }
  uploadTarget.value = {}
  fileList.value = []
  selectedFile.value = undefined
  proxy.resetForm('uploadFormRef')
}

function handleUploadDocument() {
  resetUpload()
  uploadMode.value = 'document'
  uploadOpen.value = true
}

function handleUploadVersion(row) {
  resetUpload()
  uploadMode.value = 'version'
  uploadTarget.value = row
  uploadOpen.value = true
}

function handleFileChange(uploadFile) {
  const extension = uploadFile.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'docx', 'txt'].includes(extension)) {
    proxy.$modal.msgError('仅支持 PDF、DOCX、TXT 文件')
    fileList.value = []
    selectedFile.value = undefined
    return
  }
  if (uploadFile.size > 30 * 1024 * 1024) {
    proxy.$modal.msgError('文件大小不能超过 30MB')
    fileList.value = []
    selectedFile.value = undefined
    return
  }
  selectedFile.value = uploadFile.raw
}

function handleFileRemove() {
  selectedFile.value = undefined
}

function submitUpload() {
  if (!selectedFile.value) {
    proxy.$modal.msgError('请选择要上传的文件')
    return
  }
  const executeUpload = () => {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    let request
    if (uploadMode.value === 'document') {
      formData.append('knowledgeBaseId', uploadForm.value.knowledgeBaseId)
      if (uploadForm.value.documentTitle) formData.append('documentTitle', uploadForm.value.documentTitle)
      if (uploadForm.value.remark) formData.append('remark', uploadForm.value.remark)
      request = uploadDocument(formData)
    } else {
      request = uploadDocumentVersion(uploadTarget.value.documentId, formData)
    }
    uploading.value = true
    request.then(() => {
      proxy.$modal.msgSuccess(uploadMode.value === 'document'
        ? '文档上传成功，已提交解析与索引'
        : '新版本上传成功，已提交解析与索引')
      uploadOpen.value = false
      startStatusRefresh()
    }).finally(() => {
      uploading.value = false
    })
  }

  if (uploadMode.value === 'document') {
    proxy.$refs.uploadFormRef.validate(valid => {
      if (valid) executeUpload()
    })
  } else {
    executeUpload()
  }
}

function cancelUpload() {
  uploadOpen.value = false
  resetUpload()
}

function handleEdit(row) {
  const documentId = row?.documentId || ids.value[0]
  getDocument(documentId).then(response => {
    editForm.value = response.data
    editOpen.value = true
  })
}

function submitEdit() {
  proxy.$refs.editFormRef.validate(valid => {
    if (!valid) return
    updateDocument(editForm.value).then(() => {
      proxy.$modal.msgSuccess('修改成功')
      editOpen.value = false
      getList()
    })
  })
}

function handleVersions(row) {
  versionTarget.value = row
  versionOpen.value = true
  loadVersionList()
}

function loadVersionList() {
  versionLoading.value = true
  listDocumentVersions(versionTarget.value.documentId).then(response => {
    versionList.value = response.data
  }).finally(() => {
    versionLoading.value = false
  })
}

function handleTaskDetail(row, type) {
  taskType.value = type
  taskOpen.value = true
  taskLoading.value = true
  taskDetail.value = {}
  const request = type === 'index'
    ? getDocumentIndexTask(row.documentVersionId)
    : getDocumentProcessingTask(row.documentVersionId)
  request.then(response => {
    taskDetail.value = response.data || {}
  }).finally(() => {
    taskLoading.value = false
  })
}

function handleIndexVersion(row) {
  const documentVersionId = row.documentVersionId || row.currentVersionId
  const actionText = row.indexStatus === '3' ? '重新生成' : '开始生成'
  proxy.$modal.confirm(`是否${actionText}该文档版本的向量索引？`).then(() => {
    indexingVersionId.value = documentVersionId
    return indexDocumentVersion(documentVersionId)
  }).then(() => {
    proxy.$modal.msgSuccess('向量索引任务已提交')
    startStatusRefresh()
    if (versionOpen.value) loadVersionList()
  }).catch(() => {}).finally(() => {
    indexingVersionId.value = undefined
  })
}

function handleProcessVersion(row) {
  const documentVersionId = row.documentVersionId || row.currentVersionId
  const actionText = row.processStatus === '3' ? '重新解析' : '开始解析'
  proxy.$modal.confirm(`是否${actionText}该文档版本？`).then(() => {
    processingVersionId.value = documentVersionId
    return processDocumentVersion(documentVersionId)
  }).then(() => {
    proxy.$modal.msgSuccess('解析任务已提交')
    startStatusRefresh()
    if (versionOpen.value) loadVersionList()
  }).catch(() => {}).finally(() => {
    processingVersionId.value = undefined
  })
}

function handleDownload(documentVersionId, fileName) {
  downloadDocumentVersion(documentVersionId).then(blob => {
    saveAs(new Blob([blob]), fileName)
  })
}

function handleDelete(row) {
  const documentIds = row?.documentId || ids.value
  proxy.$modal.confirm('是否确认删除选中的文档？元数据将逻辑删除，并异步清理对应 Milvus 向量；原始文件会暂时保留。').then(() => {
    return delDocument(documentIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function formatFileSize(size) {
  if (size === undefined || size === null) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function isProcessable(row) {
  return ['pdf', 'docx', 'txt'].includes((row.fileExtension || '').toLowerCase())
}

function processStatusText(status) {
  return {
    '0': '待处理',
    '1': '处理中',
    '2': '成功',
    '3': '失败'
  }[status] || '未知'
}

function processStatusType(status) {
  return {
    '0': 'warning',
    '1': 'primary',
    '2': 'success',
    '3': 'danger'
  }[status] || 'info'
}

function indexStatusText(status) {
  return {
    '0': '待索引',
    '1': '索引中',
    '2': '已索引',
    '3': '索引失败'
  }[status] || '未知'
}

loadKnowledgeBaseOptions()
getList()
onBeforeUnmount(() => clearTimeout(statusRefreshTimer))
</script>

<style scoped>
.storage-alert {
  margin-bottom: 16px;
}

.version-document {
  margin-bottom: 18px;
}
</style>
