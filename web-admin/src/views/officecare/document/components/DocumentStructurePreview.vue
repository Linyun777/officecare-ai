<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${version.originalFileName || '文档'} · V${version.versionNo || ''} 结构与分块预览`"
    width="min(1080px, 94vw)"
    append-to-body
    @close="$emit('update:modelValue', false)"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert
      title="仅预览已保存的解析结果，不重新解析，也不修改当前问答使用的知识内容。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-radio-group v-model="view" class="view-switch" @change="changeView">
      <el-radio-button value="blocks">原文块</el-radio-button>
      <el-radio-button value="legacy">现有规则分块</el-radio-button>
      <el-radio-button value="structured">结构化规则分块</el-radio-button>
    </el-radio-group>
    <el-skeleton v-if="loading" :rows="5" animated />
    <div v-else-if="error" class="preview-error" role="alert">
      <p>{{ error }}</p>
      <el-button @click="loadPreview">重新加载</el-button>
    </div>
    <template v-else-if="result">
      <p class="result-summary">
        共 {{ result.total }} 项 · 解析结果 {{ result.parseId.slice(0, 12) }}
        <span v-if="view !== 'blocks'"> · 字符预算 {{ result.chunkSize }}</span>
      </p>
      <el-alert
        v-for="warning in result.warnings"
        :key="warning"
        :title="warning"
        type="warning"
        :closable="false"
        class="preview-warning"
      />
      <el-empty v-if="!result.items.length" description="此页暂无可预览内容" />
      <div v-else class="preview-items">
        <article v-for="item in result.items" :key="item.candidateId" class="preview-item">
          <div class="item-heading">
            <strong>#{{ item.ordinal + 1 }} · {{ kindLabel(item.kind) }}</strong>
            <span>{{ item.pageNumber == null ? '页码未知' : `原文第 ${item.pageNumber} 页` }}</span>
            <el-tag :type="item.scopeStatus === 'unknown' ? 'warning' : 'info'" size="small">
              {{ scopeLabel(item) }}
            </el-tag>
          </div>
          <p v-if="item.headingPath.length" class="heading-path">{{ item.headingPath.join(' / ') }}</p>
          <p v-if="item.eligibleForSelectedModel === false" class="preview-warning">
            本项不适用于当前知识库型号。
          </p>
          <p v-if="item.continuationOf != null">续接源块 {{ item.continuationOf }}，复用来源见下方。</p>
          <div v-if="item.table?.rows.length" class="table-scroll">
            <p v-for="(caption, index) in item.table.captions" :key="index">{{ caption }}</p>
            <table class="source-table">
              <tbody>
                <tr v-for="row in tableRows(item.table)" :key="row.rowIndex">
                  <component
                    :is="cell.header ? 'th' : 'td'"
                    v-for="cell in row.cells"
                    :key="cell.columnIndex"
                    :rowspan="cell.rowSpan"
                    :colspan="cell.columnSpan"
                    :title="`原表第 ${row.rowIndex + 1} 行，第 ${cell.columnIndex + 1} 列`"
                  >{{ cell.text || '—' }}</component>
                </tr>
              </tbody>
            </table>
            <p v-for="(footnote, index) in item.table.footnotes" :key="index">{{ footnote }}</p>
          </div>
          <pre v-else class="preview-text">{{ item.text }}</pre>
          <p v-for="warning in item.warnings" :key="warning" class="preview-warning">{{ warning }}</p>
          <details class="source-details">
            <summary>来源与范围依据（{{ item.sources.length }} 处来源）</summary>
            <ul>
              <li v-for="(source, index) in item.sources" :key="index">{{ sourceLabel(source) }}</li>
            </ul>
            <p v-if="item.scopeSources.length">型号范围依据：</p>
            <ul v-if="item.scopeSources.length">
              <li v-for="(source, index) in item.scopeSources" :key="index">{{ sourceLabel(source) }}</li>
            </ul>
            <p v-else>没有独立的型号声明来源；未知范围不代表通用。</p>
            <p class="candidate-id">候选标识：{{ item.candidateId }}</p>
          </details>
        </article>
      </div>
      <el-pagination
        v-if="result.total > pageSize"
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="result.total"
        layout="total, prev, pager, next"
        class="preview-pagination"
        @current-change="loadPreview"
      />
    </template>
  </el-dialog>
</template>

<script setup>
import { getDocumentPreview } from '@/api/officecare/document'

const props = defineProps({
  modelValue: Boolean,
  version: { type: Object, default: () => ({}) }
})
defineEmits(['update:modelValue'])

const view = ref('blocks')
const pageNum = ref(1)
const pageSize = 20
const loading = ref(false)
const error = ref('')
const result = ref(null)
let requestSequence = 0

async function loadPreview() {
  const sequence = ++requestSequence
  const versionId = props.version.documentVersionId
  const requestedView = view.value
  const requestedPage = pageNum.value
  loading.value = true
  error.value = ''
  result.value = null
  try {
    const response = await getDocumentPreview(versionId, {
      view: requestedView, pageNum: requestedPage, pageSize
    })
    if (sequence !== requestSequence) return
    const data = response.data
    if (data.documentVersionId !== versionId || data.view !== requestedView || data.pageNum !== requestedPage) {
      throw new Error('预览来源与当前版本不一致，请重新加载')
    }
    result.value = data
  } catch (cause) {
    if (sequence === requestSequence) error.value = cause.message || '预览加载失败，请重试'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function changeView() {
  pageNum.value = 1
  loadPreview()
}

watch(() => [props.modelValue, props.version.documentVersionId], ([open]) => {
  requestSequence++
  result.value = null
  error.value = ''
  if (open) {
    view.value = 'blocks'
    pageNum.value = 1
    loadPreview()
  }
}, { immediate: true })
onBeforeUnmount(() => requestSequence++)

function kindLabel(kind) {
  return {
    paragraph: '文字', heading: '标题', table: '表格', table_row: '表格行',
    table_footnote: '表格脚注', legacy_chunk: '现有规则分块'
  }[kind] || '文字片段'
}

function scopeLabel(item) {
  if (item.scopeStatus === 'unknown') return '型号范围未知'
  if (item.scopeStatus === 'shared') {
    return ['shared_declaration', 'shared_heading'].includes(item.scopeOrigin)
      ? '明确通用' : '共享范围（沿用原规则）'
  }
  return `限定型号：${item.modelScope.join('、')}`
}

function sourceLabel(source) {
  const labels = [`源块 ${source.blockSequence}`]
  labels.push(source.pageNumber == null ? '页码未知' : `原文第 ${source.pageNumber} 页`)
  if (source.sourcePartIndex != null) labels.push(`文件分片 ${source.sourcePartIndex + 1}`)
  if (source.sourceItemIndex != null) labels.push(`抽取条目 ${source.sourceItemIndex + 1}`)
  if (source.rowIndex != null) labels.push(`原表第 ${source.rowIndex + 1} 行`)
  if (source.columnIndex != null) labels.push(`第 ${source.columnIndex + 1} 列`)
  if (source.characterStart != null) labels.push(`字符区间 [${source.characterStart}, ${source.characterEnd})`)
  const roles = { table_header: '表头', caption: '表题', footnote: '脚注', model_scope: '型号范围依据' }
  if (roles[source.role]) labels.push(roles[source.role])
  return labels.join(' · ')
}

// 空缺网格保留占位；被合并单元格覆盖的位置不再生成原文单元格。
function tableRows(table) {
  const columns = Math.max(...table.rows.flatMap(row => row.cells.map(cell => cell.columnIndex + cell.columnSpan)))
  const occupied = new Set()
  return table.rows.map(row => {
    const originals = new Map(row.cells.map(cell => [cell.columnIndex, cell]))
    const cells = []
    for (let column = 0; column < columns;) {
      const cell = originals.get(column)
      if (cell) {
        cells.push(cell)
        for (let r = row.rowIndex; r < row.rowIndex + cell.rowSpan; r++) {
          for (let c = column; c < column + cell.columnSpan; c++) occupied.add(`${r}:${c}`)
        }
        column += cell.columnSpan
      } else {
        if (!occupied.has(`${row.rowIndex}:${column}`)) {
          cells.push({ columnIndex: column, rowSpan: 1, columnSpan: 1, text: '结构未知' })
        }
        column++
      }
    }
    return { rowIndex: row.rowIndex, cells }
  })
}
</script>

<style scoped>
.view-switch { margin: 18px 0; }
.result-summary, .heading-path { color: var(--el-text-color-secondary); }
.preview-items { max-height: 56vh; overflow-y: auto; }
.preview-item { border: 1px solid var(--el-border-color); border-radius: 6px; padding: 16px; margin: 12px 0; }
.item-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.preview-text { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; line-height: 1.7; }
.table-scroll { overflow-x: auto; }
.source-table { width: 100%; border-collapse: collapse; margin: 12px 0; }
.source-table td, .source-table th { border: 1px solid var(--el-border-color); padding: 8px; white-space: pre-wrap; min-width: 100px; }
.source-table th { background: var(--el-fill-color-light); text-align: left; }
.preview-warning, .preview-error { color: var(--el-color-warning-dark-2); margin: 10px 0; }
.source-details { margin-top: 12px; font-size: 13px; }
.source-details summary { cursor: pointer; }
.candidate-id { overflow-wrap: anywhere; color: var(--el-text-color-secondary); }
.preview-pagination { margin-top: 16px; }
</style>
