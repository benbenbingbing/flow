<template>
  <div class="organization-selector">
    <el-input :model-value="displayText" :placeholder="placeholder" :disabled="disabled" readonly :title="displayText" :aria-label="title" @click="openPicker">
      <template #suffix>
        <el-button v-if="values.length" link :disabled="disabled" aria-label="清空组织选择" @click.stop="commit([])"><el-icon><CircleClose /></el-icon></el-button>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>
    <div v-if="resolveError" class="selection-error" role="alert">
      {{ resolveError }} <el-button link type="primary" @click="resolveValues">重试</el-button>
    </div>
    <el-dialog v-model="visible" :title="title" width="min(720px, 94vw)" append-to-body destroy-on-close>
      <OrganizationNavigationTree v-if="visible" :key="type" selection :selectable-type="type" :selectable-root="false"
        :model-value="focusedId" searchable @select="selectNode" />
      <div v-if="multiple || confirm" class="selected-nodes">
        <span>已选 {{ draft.length }} 项</span>
        <el-tag v-for="node in draft" :key="node.id" closable @close="removeDraft(node)">{{ node.orgName }}</el-tag>
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-if="multiple || confirm" type="primary" @click="commit(draft)">确认选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { CircleClose, Search } from '@element-plus/icons-vue'
import { resolveOrganizationSelection } from '@/api/system/org'
import OrganizationNavigationTree from '@/views/system/components/OrganizationNavigationTree.vue'

const props = defineProps({
  modelValue: { type: [String, Number, Array], default: '' },
  multiple: Boolean,
  confirm: Boolean,
  disabled: Boolean,
  type: { type: String, default: '' },
  valueKey: { type: String, default: 'id', validator: value => ['id', 'code'].includes(value) },
  placeholder: { type: String, default: '请选择组织或部门' },
  title: { type: String, default: '选择组织或部门' },
  // 调用方已持有的节点可直接回显，锁定组织的弹窗无需再访问目录。
  knownNodes: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'change', 'selected', 'resolved'])
const visible = ref(false)
const draft = ref([])
const focusedId = ref('0')
const cache = ref(new Map())
const resolveError = ref('')
let resolveSequence = 0
const values = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : [props.modelValue])
  .filter(value => value != null && value !== '').map(String))
const nodeValue = node => String(props.valueKey === 'code' ? node.orgCode || node.id : node.id)
const nodeMap = computed(() => new Map([...cache.value, ...props.knownNodes.map(node => [nodeValue(node), node])]))
const selectedNodes = computed(() => values.value.map(value => nodeMap.value.get(value) || { id: value, orgCode: value, orgName: value }))
const displayText = computed(() => selectedNodes.value.map(node => node.orgName).join('、'))

/** 仅查询已选值；分批上限 100，不通过目录查询或遍历全部分页来补名称。迟到响应不能覆盖新值。 */
async function resolveValues() {
  const sequence = ++resolveSequence
  resolveError.value = ''
  const missing = [...new Set(values.value.filter(value => !nodeMap.value.has(value)))]
  try {
    const resolved = []
    for (let offset = 0; offset < missing.length; offset += 100) {
      resolved.push(...await resolveOrganizationSelection(missing.slice(offset, offset + 100), props.valueKey))
      if (sequence !== resolveSequence) return
    }
    cache.value = new Map([...cache.value, ...resolved.map(node => [nodeValue(node), node])])
    emit('resolved', props.multiple ? selectedNodes.value : selectedNodes.value[0] || null)
  } catch (error) {
    if (sequence === resolveSequence) resolveError.value = error?.message || '已选组织回显失败'
  }
}

/** 候选目录仅在打开时挂载；多选和查询筛选确认后提交，普通单选点击提交，取消始终保留原值。 */
async function openPicker() {
  if (props.disabled) return
  await resolveValues()
  if (props.disabled) return
  draft.value = [...selectedNodes.value]
  focusedId.value = draft.value[0]?.id || '0'
  visible.value = true
}

function selectNode(node) {
  focusedId.value = node.id
  if (!props.multiple) {
    draft.value = [node]
    if (!props.confirm) commit([node])
    return
  }
  const index = draft.value.findIndex(item => nodeValue(item) === nodeValue(node))
  if (index < 0) draft.value.push(node)
  else draft.value.splice(index, 1)
}

function removeDraft(node) {
  draft.value = draft.value.filter(item => nodeValue(item) !== nodeValue(node))
}

/** 按调用场景提交 ID 或稳定业务编码；先补本地回显再通知父级，避免选中后短暂显示原始值。 */
function commit(nodes) {
  if (props.disabled) return
  cache.value = new Map([...cache.value, ...nodes.map(node => [nodeValue(node), node])])
  const keys = nodes.map(nodeValue)
  const value = props.multiple ? keys : keys[0] || ''
  emit('update:modelValue', value)
  emit('change', value)
  emit('selected', props.multiple ? nodes : nodes[0] || null)
  visible.value = false
}

watch(() => [props.modelValue, props.valueKey], ([, valueKey], previous) => {
  if (previous && previous[1] !== valueKey) cache.value = new Map()
  visible.value = false
  resolveValues()
}, { immediate: true, deep: true })
onBeforeUnmount(() => { resolveSequence++ })
</script>

<style scoped>
.organization-selector { width: 100%; min-width: 0; }
.organization-selector :deep(input[readonly]) { cursor: pointer; }
.selected-nodes { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.selection-error { color: var(--el-color-danger); font-size: 12px; }
</style>
