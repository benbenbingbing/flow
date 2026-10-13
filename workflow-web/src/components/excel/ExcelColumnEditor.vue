<template>
  <el-table :data="modelValue" :data-operation="operation" :row-class-name="dragRowClass" border @dragover="dragOver" @drop="dropColumn">
    <el-table-column label="序号" width="84" align="center">
      <template #default="{ $index }">
        <button type="button" class="column-drag-handle" draggable="true" :aria-label="`拖拽 ${excelColumnLabel($index)} 列排序`" title="拖拽调整顺序，也可聚焦后按方向键调整" @dragstart="startDrag($event, $index)" @dragend="endDrag" @keydown.up.prevent="keyboardMove($index, -1)" @keydown.down.prevent="keyboardMove($index, 1)">
          <span class="drag-grip" aria-hidden="true">⠿</span><span class="column-letter">{{ excelColumnLabel($index) }}</span>
        </button>
      </template>
    </el-table-column>
    <el-table-column label="字段 / 列标题" min-width="190">
      <template #default="{ row }">
        <el-select v-model="row.fieldCode" filterable placeholder="选择实体字段" @change="code => selectField(row, code)">
          <el-option v-for="field in fields" :key="field.fieldCode" :value="field.fieldCode" :label="`${field.fieldName} (${field.fieldCode})`" />
        </el-select>
        <el-input v-model="row.header" aria-label="Excel 列标题" placeholder="Excel 列标题" />
      </template>
    </el-table-column>
    <el-table-column label="转换规则" min-width="260">
      <template #default="{ row }">
        <el-select v-model="row.codec.type" aria-label="转换类型" @change="type => changeCodec(row, type)">
          <el-option v-for="item in codecOptions(row)" :key="item[0]" :value="item[0]" :label="item[1]" />
        </el-select>
        <template v-if="['DATE', 'DATETIME'].includes(row.codec.type)">
          <span class="codec-label">{{ importing ? '主要日期格式' : '输出日期格式' }}</span>
          <el-input v-model="row.codec.format" :aria-label="importing ? '主要日期格式' : '输出日期格式'" :placeholder="row.codec.type === 'DATE' ? 'yyyy-MM-dd' : 'yyyy-MM-dd HH:mm:ss'" />
          <template v-if="importing">
            <el-select v-model="row.codec.readPatterns" aria-label="允许输入的日期格式" multiple filterable allow-create default-first-option placeholder="其他允许输入的日期格式" />
            <small class="codec-hint">主要格式用于解析和模板提示；其他格式留空时兼容常见日期格式。</small>
          </template>
          <template v-else>
            <el-input v-model="row.codec.zoneId" aria-label="日期显示时区" placeholder="Asia/Shanghai" />
            <small class="codec-hint">显示时区用于转换时间点；Excel 本地日期时间保持原值。</small>
          </template>
        </template>
        <template v-if="['AMOUNT', 'NUMBER'].includes(row.codec.type)">
          <span class="codec-label">小数位数 / 舍入方式</span>
          <el-input-number :model-value="numberScale(row)" :aria-label="row.codec.type === 'AMOUNT' ? '金额小数位数' : '数字小数位数'" :min="0" :max="18" @update:model-value="value => row.codec.scale = value" />
          <el-select :model-value="row.codec.roundingMode || 'UNNECESSARY'" :aria-label="row.codec.type === 'AMOUNT' ? '金额舍入方式' : '数字舍入方式'" @update:model-value="value => row.codec.roundingMode = value">
            <el-option v-for="mode in roundingModes" :key="mode.value" :value="mode.value" :label="mode.label" />
          </el-select>
          <template v-if="importing">
            <el-checkbox :model-value="row.codec.allowThousands !== false" @update:model-value="value => row.codec.allowThousands = value">允许千分位</el-checkbox>
            <el-input v-model="row.codec.currencySymbol" aria-label="允许的货币符号" placeholder="允许的货币前缀（可选，如 ¥）" />
          </template>
          <el-checkbox v-else :model-value="row.codec.writeAsText !== false" @update:model-value="value => row.codec.writeAsText = value">按文本保留精度</el-checkbox>
        </template>
        <template v-if="row.codec.type === 'STRING'">
          <el-checkbox :model-value="row.codec.trim !== false" @update:model-value="value => row.codec.trim = value">去首尾空白</el-checkbox>
          <el-checkbox :model-value="row.codec.emptyToNull !== false" @update:model-value="value => row.codec.emptyToNull = value">空文本转为空值</el-checkbox>
        </template>
        <template v-if="row.codec.type === 'DICT'">
          <el-select v-if="importing" v-model="row.codec.importMode" aria-label="字典导入方式">
            <el-option label="导入名称或代码" value="LABEL_OR_CODE" />
            <el-option label="导入代码" value="CODE" />
            <el-option label="导入名称" value="LABEL" />
          </el-select>
          <el-select v-else v-model="row.codec.exportMode" aria-label="字典导出方式">
            <el-option label="导出名称" value="LABEL" />
            <el-option label="导出代码" value="CODE" />
          </el-select>
          <small class="codec-hint">使用实体字段绑定的代码表，名称与代码自动转换。</small>
        </template>
        <template v-if="!importing && row.codec.type === 'ENTITY_STATUS'">
          <el-select :model-value="row.codec.exportMode || 'LABEL'" aria-label="实体状态导出方式" @update:model-value="value => row.codec.exportMode = value">
            <el-option label="导出名称" value="LABEL" />
            <el-option label="导出代码" value="CODE" />
          </el-select>
          <small class="codec-hint">优先使用实体配置的状态名称，未配置时使用系统内置名称，无法识别时保留原代码。</small>
        </template>
      </template>
    </el-table-column>
    <el-table-column v-if="importing" label="附加校验" min-width="170">
      <template #default="{ row }">
        <el-checkbox v-model="row.validation.required">必填</el-checkbox>
        <el-checkbox v-model="row.validation.uniqueInFile">文件内唯一</el-checkbox>
        <el-input v-model="row.validation.min" aria-label="最小值" placeholder="最小值（可选）" />
        <el-input v-model="row.validation.max" aria-label="最大值" placeholder="最大值（可选）" />
      </template>
    </el-table-column>
    <el-table-column :label="importing ? '模板宽度 / 操作' : '宽度 / 操作'" width="140">
      <template #default="{ row, $index }">
        <!-- 导入模板与数据导出都使用列宽，范围与后端一致，避免编辑时截断历史有效值。 -->
        <el-input-number v-model="row.width" :aria-label="importing ? '模板列宽' : '导出列宽'" :min="1" :max="255" controls-position="right" style="width:115px" />
        <el-button link type="danger" @click="remove($index)">移除</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-button class="add-column" @click="addColumn">添加列</el-button>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { columnCodecOptions, createColumn, excelColumnLabel, reorderColumns } from './excelModel'

const props = defineProps({
  modelValue: { type: Array, required: true },
  fields: { type: Array, default: () => [] },
  operation: { type: String, default: 'IMPORT', validator: value => ['IMPORT', 'EXPORT'].includes(value) }
})
const emit = defineEmits(['update:modelValue'])
const importing = computed(() => props.operation === 'IMPORT')
const roundingModes = [{ value: 'UNNECESSARY', label: '禁止隐式舍入' }, { value: 'HALF_UP', label: '四舍五入' }, { value: 'HALF_EVEN', label: '四舍六入五成双' }, { value: 'HALF_DOWN', label: '五舍六入' }, { value: 'DOWN', label: '截断多余小数' }, { value: 'UP', label: '有余数则远离零进位' }, { value: 'CEILING', label: '向正无穷方向取整' }, { value: 'FLOOR', label: '向负无穷方向取整' }]

function codecOptions(row) {
  return columnCodecOptions(props.fields.find(field => field.fieldCode === row.fieldCode), props.operation)
}

/** 列数组本身表示本方向的映射，通过添加/移除管理；通用列默认值不能重新启用另一方向。 */
function isolateDirection(row) {
  row.importEnabled = importing.value
  row.exportEnabled = !importing.value
  return row
}

function operationColumn(field) {
  return isolateDirection(createColumn(field, props.operation))
}

function addColumn() {
  emit('update:modelValue', [...props.modelValue, operationColumn()])
}

const draggingIndex = ref(-1), dropIndex = ref(-1)

/** 只从序号把手开始拖拽，避免编辑输入值或下拉框时意外改变列顺序。 */
function startDrag(event, index) {
  draggingIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(index))
}

function targetRowIndex(event) {
  const row = event.target.closest?.('tr.el-table__row')
  return row && event.currentTarget.contains(row) ? row.sectionRowIndex : -1
}

function dragOver(event) {
  if (draggingIndex.value < 0) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
  dropIndex.value = targetRowIndex(event)
}

function dropColumn(event) {
  if (draggingIndex.value < 0) return
  event.preventDefault()
  const target = targetRowIndex(event)
  if (target >= 0) emit('update:modelValue', reorderColumns(props.modelValue, draggingIndex.value, target))
  endDrag()
}

function endDrag() { draggingIndex.value = -1; dropIndex.value = -1 }
function dragRowClass({ rowIndex }) { return dropIndex.value === rowIndex && draggingIndex.value !== rowIndex ? 'column-drop-target' : '' }

/** 提供键盘等价操作；焦点跟随移动后的列，连续调整时不误操作原位置的另一列。 */
async function keyboardMove(index, offset) {
  const active = document.activeElement
  const table = active?.closest('.el-table')
  const next = reorderColumns(props.modelValue, index, index + offset)
  if (next === props.modelValue) return
  emit('update:modelValue', next)
  await nextTick()
  table?.querySelectorAll('.column-drag-handle')[index + offset]?.focus()
}

function remove(index) {
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
}

/** 更换字段时重新应用实体约束和转换器默认值，避免旧字段规则误用于新的映射。 */
function selectField(row, code) {
  Object.assign(row, operationColumn(props.fields.find(field => field.fieldCode === code)))
}

/** NUMBER 未显式配置精度时遵循后端的实体精度回退，避免仅打开编辑器就改变整数规则。 */
function numberScale(row) {
  if (row.codec.scale != null) return row.codec.scale
  const field = props.fields.find(item => item.fieldCode === row.fieldCode)
  return ['INTEGER', 'LONG'].includes(String(field?.fieldType).toUpperCase()) ? 0 : field?.fieldPrecision ?? 2
}

/** 切换转换器只初始化本方向用到的选项，日期主格式同时用于导入解析和模板提示。 */
function changeCodec(row, type) {
  if (['AMOUNT', 'NUMBER'].includes(type)) {
    row.codec = { type, roundingMode: 'UNNECESSARY', ...(type === 'AMOUNT' ? { scale: 2 } : {}), ...(importing.value ? { allowThousands: true } : { writeAsText: true }) }
  } else if (['DATE', 'DATETIME'].includes(type)) {
    row.codec = { type, format: type === 'DATE' ? 'yyyy-MM-dd' : 'yyyy-MM-dd HH:mm:ss', ...(importing.value ? { readPatterns: [] } : { zoneId: 'Asia/Shanghai' }) }
  } else if (type === 'DICT') {
    row.codec = { type, ...(importing.value ? { importMode: 'LABEL_OR_CODE' } : { exportMode: 'LABEL' }) }
  } else if (type === 'ENTITY_STATUS') {
    row.codec = { type, exportMode: 'LABEL' }
  } else {
    row.codec = type === 'STRING' ? { type, trim: true, emptyToNull: true } : { type }
  }
  isolateDirection(row)
}
</script>

<style scoped>
.column-drag-handle { display:inline-flex; align-items:center; gap:6px; padding:8px 3px; border:0; background:transparent; color:var(--el-text-color-regular); cursor:grab; font:inherit; }
.column-drag-handle:active { cursor:grabbing; }
.column-drag-handle:focus-visible { outline:2px solid var(--el-color-primary); border-radius:4px; }
.column-letter { font-weight:600; }
.drag-grip { color:var(--el-text-color-secondary); }
:deep(.column-drop-target > td) { background:var(--el-color-primary-light-9) !important; box-shadow:inset 0 2px var(--el-color-primary); }
.add-column { margin-top: 12px; }
:deep(.el-input), :deep(.el-select) { margin-bottom: 6px; }
.codec-label { display: block; margin: 2px 0 4px; font-size: 12px; color: var(--el-text-color-regular); }
.codec-hint { display: block; color: var(--el-text-color-secondary); line-height: 1.5; }
</style>
