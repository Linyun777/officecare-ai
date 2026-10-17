<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="品牌编码" prop="brandCode">
        <el-input
          v-model="queryParams.brandCode"
          placeholder="请输入品牌编码"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="品牌名称" prop="brandName">
        <el-input
          v-model="queryParams.brandName"
          placeholder="请输入品牌名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 180px">
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
          v-hasPermi="['officecare:brand:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['officecare:brand:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['officecare:brand:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="brandList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="品牌编码" prop="brandCode" min-width="140" show-overflow-tooltip />
      <el-table-column label="品牌名称" prop="brandName" min-width="160" show-overflow-tooltip />
      <el-table-column label="说明" prop="description" min-width="220" show-overflow-tooltip />
      <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="180" align="center">
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
            v-hasPermi="['officecare:brand:edit']"
          >修改</el-button>
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['officecare:brand:remove']"
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

    <el-dialog :title="title" v-model="open" width="620px" append-to-body>
      <el-form ref="brandRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="品牌编码" prop="brandCode">
              <el-input
                v-model="form.brandCode"
                placeholder="例如 LENOVO"
                maxlength="50"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌名称" prop="brandName">
              <el-input
                v-model="form.brandName"
                placeholder="请输入品牌名称"
                maxlength="100"
                show-word-limit
              />
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
        <el-form-item label="品牌说明" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请输入品牌说明"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="请输入备注"
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

<script setup name="OfficeCareBrand">
import { addBrand, delBrand, getBrand, listBrand, updateBrand } from '@/api/officecare/brand'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict('sys_normal_disable')

const brandList = ref([])
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
    brandCode: undefined,
    brandName: undefined,
    status: undefined
  },
  rules: {
    brandCode: [
      { required: true, message: '品牌编码不能为空', trigger: 'blur' },
      { max: 50, message: '品牌编码长度不能超过50个字符', trigger: 'blur' }
    ],
    brandName: [
      { required: true, message: '品牌名称不能为空', trigger: 'blur' },
      { max: 100, message: '品牌名称长度不能超过100个字符', trigger: 'blur' }
    ],
    sortOrder: [{ required: true, message: '显示顺序不能为空', trigger: 'change' }],
    status: [{ required: true, message: '品牌状态不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listBrand(queryParams.value).then(response => {
    brandList.value = response.rows
    total.value = response.total
  }).finally(() => {
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    brandId: undefined,
    brandCode: undefined,
    brandName: undefined,
    description: undefined,
    sortOrder: 0,
    status: '0',
    remark: undefined
  }
  proxy.resetForm('brandRef')
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
  ids.value = selection.map(item => item.brandId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增品牌'
}

function handleUpdate(row) {
  reset()
  const brandId = row?.brandId || ids.value[0]
  getBrand(brandId).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改品牌'
  })
}

function submitForm() {
  proxy.$refs.brandRef.validate(valid => {
    if (!valid) return
    const request = form.value.brandId ? updateBrand(form.value) : addBrand(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.brandId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const brandIds = row?.brandId || ids.value
  proxy.$modal.confirm('是否确认删除选中的品牌？存在产品型号的品牌不能删除。').then(() => {
    return delBrand(brandIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
