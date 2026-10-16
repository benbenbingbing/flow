<template>
  <el-dialog
    v-model="visible"
    title="新建代码表并绑定字段"
    width="640px"
    style="max-width: calc(100vw - 32px)"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
  >
    <el-form label-width="100px" :disabled="saving" @submit.prevent>
      <el-form-item label="代码表名称" required>
        <el-input v-model="form.dictName" placeholder="例如：报销类型" />
      </el-form-item>
      <el-form-item label="代码表编码" required>
        <el-input v-model="form.dictCode" placeholder="例如：expense_type" />
      </el-form-item>
      <el-form-item label="代码项" required>
        <div class="dict-items-editor">
          <el-radio-group :model-value="inputMode" aria-label="代码项录入方式" @update:model-value="switchInputMode">
            <el-radio-button value="rows">逐行添加</el-radio-button>
            <el-radio-button value="text">批量录入</el-radio-button>
          </el-radio-group>
          <template v-if="inputMode === 'rows'">
            <el-table :data="itemRows" row-key="key" border max-height="280">
              <el-table-column type="index" label="序号" width="54" align="center" />
              <el-table-column label="编码" min-width="130">
                <template #default="{ row, $index }">
                  <el-input v-model="row.itemCode" placeholder="例如：1" :aria-label="`第 ${$index + 1} 行编码`" />
                </template>
              </el-table-column>
              <el-table-column label="名称" min-width="150">
                <template #default="{ row, $index }">
                  <el-input v-model="row.itemLabel" placeholder="例如：交通费" :aria-label="`第 ${$index + 1} 行名称`" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="64" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link :aria-label="`删除第 ${$index + 1} 行`" @click="removeItemRow($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button class="add-item-button" plain @click="addItemRow">+ 添加一行</el-button>
          </template>
          <template v-else>
            <el-input v-model="itemsText" type="textarea" :rows="6" placeholder="每行格式：编码:名称" aria-label="批量代码项" />
            <div class="input-hint">每行一个代码项，例如：1:交通费。可切换到逐行添加继续编辑。</div>
          </template>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="createAndBindDict">创建并绑定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createDictWithItems } from '@/api/system/dict'

const props = defineProps({
  modelValue: Boolean,
  dictName: { type: String, default: '' },
  dictCode: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'created'])
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
const saving = ref(false)
const form = ref({ dictName: '', dictCode: '' })
const inputMode = ref('rows')
const itemsText = ref('')
const itemRows = ref([])
let nextRowKey = 0

const createItemRow = (itemCode = '', itemLabel = '') => ({ key: ++nextRowKey, itemCode, itemLabel })
const addItemRow = () => itemRows.value.push(createItemRow())

// 每次打开都按当前字段初始化，避免把上一次取消的代码项带入另一个字段。
watch(() => props.modelValue, value => {
  if (!value) return
  form.value = { dictName: props.dictName, dictCode: props.dictCode }
  inputMode.value = 'rows'
  itemsText.value = ''
  itemRows.value = [createItemRow()]
}, { immediate: true })

/** 删除指定草稿行；最后一行删除后保留空白输入，方便继续录入。 */
function removeItemRow(index) {
  itemRows.value.splice(index, 1)
  if (!itemRows.value.length) addItemRow()
}

/**
 * 批量文本只按首个英文冒号拆分，保留名称中的冒号。
 * 无分隔符的行保留为未填名称的草稿，交由提交校验提示，避免切换模式时静默丢项。
 */
function parseItemsText(text) {
  return text.split(/\r?\n/).map(line => {
    const separator = line.indexOf(':')
    return separator < 0
      ? { itemCode: line, itemLabel: '' }
      : { itemCode: line.slice(0, separator), itemLabel: line.slice(separator + 1) }
  })
}

/** 切换录入方式时同步当前草稿，未填写完整的行也保留供继续编辑。 */
function switchInputMode(mode) {
  if (mode === inputMode.value) return
  if (mode === 'rows') {
    itemRows.value = parseItemsText(itemsText.value).map(item => createItemRow(item.itemCode, item.itemLabel))
  } else {
    // 批量格式无法表达编码中的冒号或字段中的换行；保留逐行草稿供直接提交。
    if (itemRows.value.some(item => /[:\r\n]/.test(item.itemCode) || /[\r\n]/.test(item.itemLabel))) {
      ElMessage.warning('编码含冒号或代码项含换行，无法切换为批量录入，请继续逐行编辑')
      return
    }
    itemsText.value = itemRows.value.map(item =>
      item.itemCode || item.itemLabel ? `${item.itemCode}:${item.itemLabel}` : ''
    ).join('\n')
  }
  inputMode.value = mode
}

/**
 * 统一校验两种录入方式后创建代码表，成功时向父页面返回代码表以绑定当前字段。
 * 全空行不提交；半填行和去除首尾空白后的重复编码必须修正，防止选项被遗漏或创建失败。
 */
async function createAndBindDict() {
  if (saving.value) return
  const dictName = form.value.dictName.trim()
  const dictCode = form.value.dictCode.trim()
  if (!dictName || !dictCode) {
    ElMessage.warning('请填写代码表名称和编码')
    return
  }
  const draftItems = inputMode.value === 'rows' ? itemRows.value : parseItemsText(itemsText.value)
  const items = []
  const itemCodes = new Set()
  for (const [index, item] of draftItems.entries()) {
    const itemCode = item.itemCode.trim()
    const itemLabel = item.itemLabel.trim()
    if (!itemCode && !itemLabel) continue
    if (!itemCode || !itemLabel) {
      ElMessage.warning(`第 ${index + 1} 行代码项的编码和名称不能为空`)
      return
    }
    if (itemCodes.has(itemCode)) {
      ElMessage.warning(`第 ${index + 1} 行代码项编码重复：${itemCode}`)
      return
    }
    itemCodes.add(itemCode)
    items.push({ itemCode, itemLabel })
  }
  if (!items.length) {
    ElMessage.warning('请至少填写一个代码项')
    return
  }
  saving.value = true
  try {
    const dict = await createDictWithItems({ dict: { dictName, dictCode, status: '0' }, items })
    emit('created', dict)
    visible.value = false
    ElMessage.success('代码表已创建并绑定')
  } catch (error) {
    console.error(error)
    ElMessage.error('创建代码表失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dict-items-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.add-item-button {
  align-self: flex-start;
}

.input-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
