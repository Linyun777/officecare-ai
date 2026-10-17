<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
      label-width="82px"
    >
      <el-form-item label="客户编码" prop="customerCode">
        <el-input
          v-model="queryParams.customerCode"
          placeholder="请输入客户编码"
          clearable
          style="width: 190px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户名称" prop="customerName">
        <el-input
          v-model="queryParams.customerName"
          placeholder="请输入客户名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input
          v-model="queryParams.contactName"
          placeholder="请输入联系人"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input
          v-model="queryParams.contactPhone"
          placeholder="请输入联系电话"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="queryParams.status"
          placeholder="请选择状态"
          clearable
          style="width: 140px"
        >
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
          v-hasPermi="['officecare:customer:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['officecare:customer:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['officecare:customer:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table
      v-loading="loading"
      :data="customerList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="客户编码" prop="customerCode" min-width="130" show-overflow-tooltip />
      <el-table-column label="客户名称" prop="customerName" min-width="190" show-overflow-tooltip />
      <el-table-column label="联系人" prop="contactName" width="110" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.contactName || '-' }}</template>
      </el-table-column>
      <el-table-column label="联系电话" prop="contactPhone" width="150" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.contactPhone || '-' }}</template>
      </el-table-column>
      <el-table-column label="单位地址" prop="address" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.address || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170" align="center">
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
            v-hasPermi="['officecare:customer:edit']"
          >修改</el-button>
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['officecare:customer:remove']"
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
      <el-form ref="customerRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户编码" prop="customerCode">
              <el-input
                v-model="form.customerCode"
                placeholder="例如：CUST_BROTHER_001"
                maxlength="50"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称" prop="customerName">
              <el-input
                v-model="form.customerName"
                placeholder="请输入客户单位名称"
                maxlength="150"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input
                v-model="form.contactName"
                placeholder="可选"
                maxlength="50"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input
                v-model="form.contactPhone"
                placeholder="手机或座机均可"
                maxlength="30"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="单位地址" prop="address">
          <el-input
            v-model="form.address"
            placeholder="请输入设备服务地址"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in sys_normal_disable"
              :key="dict.value"
              :value="dict.value"
            >{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可记录服务时间、门禁或其他注意事项"
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

<script setup name="OfficeCareCustomerOrg">
import {
  addCustomerOrg,
  delCustomerOrg,
  getCustomerOrg,
  listCustomerOrg,
  updateCustomerOrg
} from '@/api/officecare/customerOrg'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict('sys_normal_disable')

const customerList = ref([])
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
    customerCode: undefined,
    customerName: undefined,
    contactName: undefined,
    contactPhone: undefined,
    status: undefined
  },
  rules: {
    customerCode: [
      { required: true, message: '客户编码不能为空', trigger: 'blur' },
      { pattern: /^[A-Za-z0-9_-]+$/, message: '只能包含字母、数字、下划线和短横线', trigger: 'blur' },
      { max: 50, message: '客户编码长度不能超过50个字符', trigger: 'blur' }
    ],
    customerName: [
      { required: true, message: '客户名称不能为空', trigger: 'blur' },
      { max: 150, message: '客户名称长度不能超过150个字符', trigger: 'blur' }
    ],
    contactName: [{ max: 50, message: '联系人长度不能超过50个字符', trigger: 'blur' }],
    contactPhone: [{ max: 30, message: '联系电话长度不能超过30个字符', trigger: 'blur' }],
    address: [{ max: 255, message: '单位地址长度不能超过255个字符', trigger: 'blur' }],
    status: [{ required: true, message: '客户状态不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listCustomerOrg(queryParams.value).then(response => {
    customerList.value = response.rows
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
    customerOrgId: undefined,
    customerCode: undefined,
    customerName: undefined,
    contactName: undefined,
    contactPhone: undefined,
    address: undefined,
    status: '0',
    remark: undefined
  }
  proxy.resetForm('customerRef')
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
  ids.value = selection.map(item => item.customerOrgId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增客户单位'
}

function handleUpdate(row) {
  reset()
  const customerOrgId = row?.customerOrgId || ids.value[0]
  getCustomerOrg(customerOrgId).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改客户单位'
  })
}

function submitForm() {
  proxy.$refs.customerRef.validate(valid => {
    if (!valid) return
    const editing = Boolean(form.value.customerOrgId)
    const request = editing
      ? updateCustomerOrg(form.value)
      : addCustomerOrg(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(editing ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const customerOrgIds = row?.customerOrgId || ids.value
  proxy.$modal.confirm('是否确认删除选中的客户单位？存在设备档案的客户不能删除。').then(() => {
    return delCustomerOrg(customerOrgIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
