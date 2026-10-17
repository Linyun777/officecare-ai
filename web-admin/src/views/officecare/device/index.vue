<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
      label-width="82px"
    >
      <el-form-item label="客户单位" prop="customerOrgId">
        <el-select
          v-model="queryParams.customerOrgId"
          placeholder="请选择客户"
          clearable
          filterable
          style="width: 220px"
        >
          <el-option
            v-for="customer in customerOptions"
            :key="customer.customerOrgId"
            :label="customer.customerName"
            :value="customer.customerOrgId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="品牌" prop="brandId">
        <el-select
          v-model="queryParams.brandId"
          placeholder="请选择品牌"
          clearable
          filterable
          style="width: 170px"
          @change="handleQueryBrandChange"
        >
          <el-option
            v-for="brand in brandOptions"
            :key="brand.brandId"
            :label="brand.brandName"
            :value="brand.brandId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="产品型号" prop="modelId">
        <el-select
          v-model="queryParams.modelId"
          placeholder="请选择型号"
          clearable
          filterable
          style="width: 200px"
        >
          <el-option
            v-for="model in filteredQueryModelOptions"
            :key="model.modelId"
            :label="model.modelName"
            :value="model.modelId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="设备名称" prop="deviceName">
        <el-input
          v-model="queryParams.deviceName"
          placeholder="请输入设备名称"
          clearable
          style="width: 190px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="序列号" prop="serialNumber">
        <el-input
          v-model="queryParams.serialNumber"
          placeholder="请输入序列号"
          clearable
          style="width: 190px"
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
          v-hasPermi="['officecare:device:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['officecare:device:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['officecare:device:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshPage" />
    </el-row>

    <el-table
      v-loading="loading"
      :data="deviceList"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="客户单位" prop="customerName" min-width="180" show-overflow-tooltip />
      <el-table-column label="设备名称" prop="deviceName" min-width="150" show-overflow-tooltip />
      <el-table-column label="序列号" prop="serialNumber" min-width="150" show-overflow-tooltip />
      <el-table-column label="品牌" prop="brandName" width="120" show-overflow-tooltip />
      <el-table-column label="产品型号" prop="modelName" min-width="140" show-overflow-tooltip />
      <el-table-column label="安装位置" prop="installLocation" min-width="170" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.installLocation || '-' }}</template>
      </el-table-column>
      <el-table-column label="保修状态" width="110" align="center">
        <template #default="scope">
          <el-tag :type="warrantyTagType(scope.row.warrantyEndDate)" effect="plain">
            {{ warrantyText(scope.row.warrantyEndDate) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="保修截止" prop="warrantyEndDate" width="120" align="center">
        <template #default="scope">{{ scope.row.warrantyEndDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['officecare:device:edit']"
          >修改</el-button>
          <el-button
            link
            type="primary"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['officecare:device:remove']"
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

    <el-dialog :title="title" v-model="open" width="820px" append-to-body>
      <el-form ref="deviceRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="客户单位" prop="customerOrgId">
          <el-select
            v-model="form.customerOrgId"
            placeholder="请选择设备所属客户单位"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.customerOrgId"
              :label="`${customer.customerName}（${customer.customerCode}）`"
              :value="customer.customerOrgId"
              :disabled="customer.status === '1'"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="品牌" prop="brandId">
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
                  v-for="model in filteredFormModelOptions"
                  :key="model.modelId"
                  :label="`${model.modelName}（${model.modelCode}）`"
                  :value="model.modelId"
                  :disabled="model.status === '1'"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备名称" prop="deviceName">
              <el-input
                v-model="form.deviceName"
                placeholder="例如：财务室打印机"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备序列号" prop="serialNumber">
              <el-input
                v-model="form.serialNumber"
                placeholder="请输入机身序列号"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="安装位置" prop="installLocation">
          <el-input
            v-model="form.installLocation"
            placeholder="例如：A座3层财务室打印区"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="购买日期" prop="purchaseDate">
              <el-date-picker
                v-model="form.purchaseDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="安装日期" prop="installDate">
              <el-date-picker
                v-model="form.installDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="保修截止" prop="warrantyEndDate">
              <el-date-picker
                v-model="form.warrantyEndDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
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
            placeholder="可记录设备资产编号、使用部门或维护注意事项"
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

<script setup name="OfficeCareDevice">
import { listBrandOptions } from '@/api/officecare/brand'
import { listCustomerOrgOptions } from '@/api/officecare/customerOrg'
import {
  addDevice,
  delDevice,
  getDevice,
  listDevice,
  updateDevice
} from '@/api/officecare/device'
import { listProductModelOptions } from '@/api/officecare/productModel'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict('sys_normal_disable')

const deviceList = ref([])
const customerOptions = ref([])
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
    customerOrgId: undefined,
    brandId: undefined,
    modelId: undefined,
    deviceName: undefined,
    serialNumber: undefined,
    status: undefined
  },
  rules: {
    customerOrgId: [{ required: true, message: '所属客户不能为空', trigger: 'change' }],
    brandId: [{ required: true, message: '所属品牌不能为空', trigger: 'change' }],
    modelId: [{ required: true, message: '产品型号不能为空', trigger: 'change' }],
    deviceName: [
      { required: true, message: '设备名称不能为空', trigger: 'blur' },
      { max: 100, message: '设备名称长度不能超过100个字符', trigger: 'blur' }
    ],
    serialNumber: [
      { required: true, message: '设备序列号不能为空', trigger: 'blur' },
      { max: 100, message: '设备序列号长度不能超过100个字符', trigger: 'blur' }
    ],
    installLocation: [{ max: 255, message: '安装位置长度不能超过255个字符', trigger: 'blur' }],
    warrantyEndDate: [{ validator: validateWarrantyDate, trigger: 'change' }],
    status: [{ required: true, message: '设备状态不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

const filteredQueryModelOptions = computed(() => {
  if (!queryParams.value.brandId) return modelOptions.value
  return modelOptions.value.filter(
    model => model.brandId === queryParams.value.brandId
  )
})

const filteredFormModelOptions = computed(() => {
  if (!form.value.brandId) return []
  return modelOptions.value.filter(model => model.brandId === form.value.brandId)
})

function validateWarrantyDate(rule, value, callback) {
  if (!value) {
    callback()
    return
  }
  if (form.value.purchaseDate && form.value.purchaseDate > value) {
    callback(new Error('保修截止日期不能早于购买日期'))
    return
  }
  if (form.value.installDate && form.value.installDate > value) {
    callback(new Error('保修截止日期不能早于安装日期'))
    return
  }
  callback()
}

function loadOptions() {
  return Promise.all([
    listCustomerOrgOptions(),
    listBrandOptions(),
    listProductModelOptions()
  ]).then(([customers, brands, models]) => {
    customerOptions.value = customers.data || []
    brandOptions.value = brands.data || []
    modelOptions.value = models.data || []
  })
}

function getList() {
  loading.value = true
  listDevice(queryParams.value).then(response => {
    deviceList.value = response.rows
    total.value = response.total
  }).finally(() => {
    loading.value = false
  })
}

function refreshPage() {
  loadOptions()
  getList()
}

function reset() {
  form.value = {
    deviceId: undefined,
    customerOrgId: undefined,
    brandId: undefined,
    modelId: undefined,
    deviceName: undefined,
    serialNumber: undefined,
    installLocation: undefined,
    purchaseDate: undefined,
    installDate: undefined,
    warrantyEndDate: undefined,
    status: '0',
    remark: undefined
  }
  proxy.resetForm('deviceRef')
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
  handleQueryBrandChange()
  handleQuery()
}

function handleQueryBrandChange() {
  const selected = modelOptions.value.find(
    model => model.modelId === queryParams.value.modelId
  )
  if (selected && selected.brandId !== queryParams.value.brandId) {
    queryParams.value.modelId = undefined
  }
}

function handleFormBrandChange() {
  const selected = modelOptions.value.find(
    model => model.modelId === form.value.modelId
  )
  if (!selected || selected.brandId !== form.value.brandId) {
    form.value.modelId = undefined
  }
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.deviceId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function handleAdd() {
  reset()
  loadOptions().then(() => {
    open.value = true
    title.value = '新增设备档案'
  })
}

function handleUpdate(row) {
  reset()
  const deviceId = row?.deviceId || ids.value[0]
  Promise.all([getDevice(deviceId), loadOptions()]).then(([response]) => {
    ensureCurrentOptions(response.data)
    form.value = response.data
    open.value = true
    title.value = '修改设备档案'
  })
}

function ensureCurrentOptions(device) {
  if (!customerOptions.value.some(
    item => item.customerOrgId === device.customerOrgId
  )) {
    customerOptions.value.push({
      customerOrgId: device.customerOrgId,
      customerCode: device.customerCode,
      customerName: device.customerName,
      status: '1'
    })
  }
  if (!modelOptions.value.some(item => item.modelId === device.modelId)) {
    modelOptions.value.push({
      modelId: device.modelId,
      brandId: device.brandId,
      modelCode: device.modelCode,
      modelName: device.modelName,
      status: '1'
    })
  }
}

function submitForm() {
  proxy.$refs.deviceRef.validate(valid => {
    if (!valid) return
    const editing = Boolean(form.value.deviceId)
    const request = editing ? updateDevice(form.value) : addDevice(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(editing ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const deviceIds = row?.deviceId || ids.value
  proxy.$modal.confirm('是否确认删除选中的设备档案？').then(() => {
    return delDevice(deviceIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function warrantyText(warrantyEndDate) {
  if (!warrantyEndDate) return '未设置'
  return new Date(`${warrantyEndDate}T23:59:59`) >= new Date()
    ? '保修中'
    : '已过保'
}

function warrantyTagType(warrantyEndDate) {
  if (!warrantyEndDate) return 'info'
  return warrantyText(warrantyEndDate) === '保修中' ? 'success' : 'warning'
}

loadOptions()
getList()
</script>
