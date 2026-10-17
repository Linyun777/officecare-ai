<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
      label-width="76px"
    >
      <el-form-item label="知识库编码" prop="knowledgeBaseCode">
        <el-input
          v-model="queryParams.knowledgeBaseCode"
          placeholder="请输入知识库编码"
          clearable
          style="width: 210px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="知识库名称" prop="knowledgeBaseName">
        <el-input
          v-model="queryParams.knowledgeBaseName"
          placeholder="请输入知识库名称"
          clearable
          style="width: 210px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所属品牌" prop="brandId">
        <el-select
          v-model="queryParams.brandId"
          placeholder="请选择品牌"
          clearable
          filterable
          style="width: 190px"
        >
          <el-option
            v-for="brand in brandOptions"
            :key="brand.brandId"
            :label="brand.brandName"
            :value="brand.brandId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
          <el-option
            v-for="dict in sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
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
          v-hasPermi="['officecare:knowledge-base:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['officecare:knowledge-base:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['officecare:knowledge-base:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshPage" />
    </el-row>

    <el-table v-loading="loading" :data="knowledgeBaseList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="知识库编码" prop="knowledgeBaseCode" min-width="175" show-overflow-tooltip />
      <el-table-column label="知识库名称" prop="knowledgeBaseName" min-width="200" show-overflow-tooltip />
      <el-table-column label="品牌" prop="brandName" min-width="140" show-overflow-tooltip />
      <el-table-column label="产品型号" prop="modelName" min-width="190" show-overflow-tooltip />
      <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180" align="center">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime || scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['officecare:knowledge-base:edit']"
          >修改</el-button>
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['officecare:knowledge-base:remove']"
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

    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="knowledgeBaseRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="知识库编码" prop="knowledgeBaseCode">
              <el-input
                v-model="form.knowledgeBaseCode"
                placeholder="例如 BROTHER_DCP_L2550DW"
                maxlength="64"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="知识库名称" prop="knowledgeBaseName">
              <el-input
                v-model="form.knowledgeBaseName"
                placeholder="请输入知识库名称"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属品牌" prop="brandId">
              <el-select
                v-model="form.brandId"
                placeholder="请选择品牌"
                filterable
                style="width: 100%"
                @change="handleFormBrandChange"
              >
                <el-option
                  v-for="brand in brandOptions"
                  :key="brand.brandId"
                  :label="brand.brandName"
                  :value="brand.brandId"
                  :disabled="brand.status === '1'"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品型号" prop="modelId">
              <el-select
                v-model="form.modelId"
                placeholder="请先选择品牌"
                filterable
                style="width: 100%"
                :disabled="!form.brandId"
              >
                <el-option
                  v-for="model in modelOptions"
                  :key="model.modelId"
                  :label="model.modelName"
                  :value="model.modelId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="显示顺序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio
                  v-for="dict in sys_normal_disable"
                  :key="dict.value"
                  :value="dict.value"
                >{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="知识库说明" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明知识库收录的资料范围"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确定</el-button>
          <el-button @click="cancel">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="OfficeCareKnowledgeBase">
import { listBrandOptions } from '@/api/officecare/brand'
import { listProductModelOptions } from '@/api/officecare/productModel'
import {
  addKnowledgeBase,
  delKnowledgeBase,
  getKnowledgeBase,
  listKnowledgeBase,
  updateKnowledgeBase
} from '@/api/officecare/knowledgeBase'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict('sys_normal_disable')

const knowledgeBaseList = ref([])
const brandOptions = ref([])
const modelOptions = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    knowledgeBaseCode: undefined,
    knowledgeBaseName: undefined,
    brandId: undefined,
    status: undefined
  },
  rules: {
    knowledgeBaseCode: [
      { required: true, message: '知识库编码不能为空', trigger: 'blur' },
      { max: 64, message: '知识库编码长度不能超过64个字符', trigger: 'blur' }
    ],
    knowledgeBaseName: [
      { required: true, message: '知识库名称不能为空', trigger: 'blur' },
      { max: 100, message: '知识库名称长度不能超过100个字符', trigger: 'blur' }
    ],
    brandId: [{ required: true, message: '所属品牌不能为空', trigger: 'change' }],
    modelId: [{ required: true, message: '产品型号不能为空', trigger: 'change' }],
    sortOrder: [{ required: true, message: '显示顺序不能为空', trigger: 'change' }],
    status: [{ required: true, message: '知识库状态不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function loadBrandOptions() {
  return listBrandOptions().then(response => {
    brandOptions.value = response.data
  })
}

function loadModelOptions(brandId) {
  if (!brandId) {
    modelOptions.value = []
    return Promise.resolve()
  }
  return listProductModelOptions(brandId).then(response => {
    modelOptions.value = response.data
  })
}

function getList() {
  loading.value = true
  listKnowledgeBase(queryParams.value).then(response => {
    knowledgeBaseList.value = response.rows
    total.value = response.total
  }).finally(() => {
    loading.value = false
  })
}

function refreshPage() {
  loadBrandOptions()
  getList()
}

function reset() {
  form.value = {
    knowledgeBaseId: undefined,
    knowledgeBaseCode: undefined,
    knowledgeBaseName: undefined,
    brandId: undefined,
    modelId: undefined,
    description: undefined,
    sortOrder: 0,
    status: '0',
    remark: undefined
  }
  modelOptions.value = []
  proxy.resetForm('knowledgeBaseRef')
}

function cancel() {
  open.value = false
  reset()
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
  ids.value = selection.map(item => item.knowledgeBaseId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function handleFormBrandChange() {
  form.value.modelId = undefined
  loadModelOptions(form.value.brandId)
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增知识库'
}

function handleUpdate(row) {
  reset()
  const knowledgeBaseId = row?.knowledgeBaseId || ids.value[0]
  getKnowledgeBase(knowledgeBaseId).then(async response => {
    form.value = response.data
    await loadModelOptions(form.value.brandId)
    open.value = true
    title.value = '修改知识库'
  })
}

function submitForm() {
  proxy.$refs.knowledgeBaseRef.validate(valid => {
    if (!valid) return
    const request = form.value.knowledgeBaseId
      ? updateKnowledgeBase(form.value)
      : addKnowledgeBase(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.knowledgeBaseId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const knowledgeBaseIds = row?.knowledgeBaseId || ids.value
  proxy.$modal.confirm('是否确认删除选中的知识库？存在文档的知识库不能删除。').then(() => {
    return delKnowledgeBase(knowledgeBaseIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

loadBrandOptions()
getList()
</script>
