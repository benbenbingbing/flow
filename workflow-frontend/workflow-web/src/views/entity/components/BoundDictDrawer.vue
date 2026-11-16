<template>
  <el-drawer
    v-model="visible"
    :title="editable ? '维护代码表' : '查看代码表'"
    size="min(860px, 94vw)"
    append-to-body
    :close-on-click-modal="false"
    :before-close="closeDrawer"
  >
    <PageState v-if="loadError" type="error" title="代码表加载失败" :description="loadError" retryable @retry="loadDict" />
    <div v-else v-loading="loading" class="bound-dict-content">
      <template v-if="dict">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="代码表名称">{{ dict.dictName }}</el-descriptions-item>
          <el-descriptions-item label="代码表编码">{{ dict.dictCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ dict.status === '0' ? '启用' : '停用' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="dict.status !== '0'"
          title="此代码表已停用，当前字段仍保留原绑定。"
          type="warning" :closable="false" show-icon
        />
        <el-alert
          v-if="editable"
          title="修改会影响所有引用此代码表的字段。代码项在此单独保存并生效，不随实体字段草稿撤销。"
          description="已有代码项的编码和存储值不可修改；不再使用的选项请停用，并保留历史数据回显。"
          type="warning" :closable="false" show-icon
        />
        <el-button v-if="editable" type="primary" class="add-dict-item" @click="openItemEditor()">新增代码项</el-button>
        <el-table :data="items" row-key="id" border default-expand-all empty-text="暂无代码项">
          <el-table-column prop="itemCode" label="编码" min-width="120" show-overflow-tooltip />
          <el-table-column prop="itemLabel" label="名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="itemValue" label="存储值" min-width="110" show-overflow-tooltip />
          <el-table-column prop="sort" label="排序" width="70" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">{{ row.status === '0' ? '启用' : '停用' }}</template>
          </el-table-column>
          <el-table-column v-if="editable" label="操作" width="70">
            <template #default="{ row }">
              <el-button type="primary" link @click="openItemEditor(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="closeDrawer()">关闭</el-button>
    </template>
  </el-drawer>

  <el-dialog
    v-model="itemEditorVisible"
    :title="itemForm.id ? '编辑代码项' : '新增代码项'"
    width="min(540px, 94vw)"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
  >
    <el-form :disabled="saving" label-width="90px" @submit.prevent>
      <el-form-item label="编码" required>
        <el-input v-model="itemForm.itemCode" :disabled="Boolean(itemForm.id)" placeholder="例如：1" />
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="itemForm.itemLabel" placeholder="例如：交通费" />
      </el-form-item>
      <el-form-item v-if="itemForm.id" label="存储值">
        <el-input v-model="itemForm.itemValue" disabled />
        <div class="form-tip">保留已有值，避免改变历史数据含义；新增代码项时值与编码保持一致。</div>
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="itemForm.sort" :precision="0" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="itemForm.status">
          <el-radio value="0">启用</el-radio>
          <el-radio value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="itemEditorVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveItem">保存代码项</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getDictByCode, getItemTreeByDictId, createDictItem, updateDictItem } from '@/api/system/dict'
import { showRequestError } from '@/shared/request'
import { useUserStore } from '@/stores/user'
import PageState from '@/components/PageState.vue'

const props = defineProps({
  modelValue: Boolean,
  dictCode: { type: String, default: '' },
  manage: Boolean
})
const emit = defineEmits(['update:modelValue', 'changed'])
const userStore = useUserStore()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const editable = computed(() => props.manage && (userStore.isSuperAdmin
  || userStore.permissions.includes('*') || userStore.permissions.includes('system:dictionary:manage')))
const dict = ref(null)
const items = ref([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const itemEditorVisible = ref(false)
const itemForm = ref({})
let loadVersion = 0

watch(() => [props.modelValue, props.dictCode], () => {
  itemEditorVisible.value = false
  if (props.modelValue) loadDict()
  else ++loadVersion
}, { immediate: true })

/** 按绑定编码独立读取详情，停用代码表也可查看；迟到的请求不能覆盖新打开的代码表。 */
async function loadDict() {
  const version = ++loadVersion
  const code = props.dictCode
  loading.value = true
  loadError.value = ''
  dict.value = null
  items.value = []
  try {
    const definition = await getDictByCode(code)
    if (!definition) throw new Error(`代码表 ${code} 不存在，请联系管理员检查绑定`)
    const tree = await getItemTreeByDictId(definition.id)
    if (version !== loadVersion) return
    dict.value = definition
    items.value = tree || []
  } catch (error) {
    if (version === loadVersion) loadError.value = error?.message || '无法读取代码表，请稍后重试'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

/** 保留原代码项的层级、备注和扩展字段，仅通过本入口维护名称、排序和状态。 */
function openItemEditor(item) {
  if (!editable.value || !dict.value || saving.value) return
  itemForm.value = item ? { ...item, children: undefined } : {
    dictId: dict.value.id, dictCode: dict.value.dictCode, parentId: '0',
    itemCode: '', itemLabel: '', itemValue: '', sort: 0, status: '0'
  }
  itemEditorVisible.value = true
}

/** 单项独立保存；失败保留输入，成功后重读包含停用项的树，避免把维护误当作字段草稿。 */
async function saveItem() {
  if (saving.value || !editable.value || !dict.value) return
  const draft = itemForm.value
  const itemCode = draft.id ? draft.itemCode : String(draft.itemCode || '').trim()
  const itemLabel = String(draft.itemLabel || '').trim()
  const itemValue = draft.id ? draft.itemValue : itemCode
  if (!itemCode || !itemLabel) {
    ElMessage.warning('请填写代码项编码和名称')
    return
  }
  const allItems = flattenItems(items.value)
  if (allItems.some(item => item.id !== draft.id && (item.itemCode === itemCode || item.itemValue === itemValue))) {
    ElMessage.warning('代码项编码或存储值已存在')
    return
  }
  saving.value = true
  try {
    // 已有值原样传回，防止去空白等转换意外改变历史记录使用的标识。
    const data = { ...draft, itemLabel,
      itemCode: draft.id ? draft.itemCode : itemCode,
      itemValue: draft.id ? draft.itemValue : itemValue }
    if (draft.id) await updateDictItem(draft.id, data)
    else await createDictItem(data)
    itemEditorVisible.value = false
    ElMessage.success('代码项已保存')
    emit('changed')
    await loadDict()
  } catch (error) {
    showRequestError(error, '保存代码项失败')
  } finally {
    saving.value = false
  }
}

/** 校验整棵树的编码和值，避免和子项或停用项重复。 */
function flattenItems(tree) {
  return tree.flatMap(item => [item, ...flattenItems(item.children || [])])
}

/** 保存期间禁止关闭外层抽屉，以免提交中的代码项失去所属代码表上下文。 */
function closeDrawer(done) {
  if (saving.value) return
  itemEditorVisible.value = false
  if (done) done()
  else visible.value = false
}
</script>

<style scoped>
.bound-dict-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 180px;
}
.add-dict-item { align-self: flex-start; }
.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
