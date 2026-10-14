<template>
  <div class="user-relation-path">
    <el-popover v-model:visible="visible" trigger="click" placement="bottom-start"
      width="min(520px, calc(100vw - 32px))" :tabindex="-1" :trigger-keys="[]"
      :hide-after="0" @after-enter="focusTree">
      <template #reference>
        <div class="path-trigger" :class="{ 'is-open': visible, 'is-error': error }">
          <button ref="trigger" type="button" class="path-input" role="combobox"
            aria-label="用户关系路径" aria-haspopup="tree" :aria-expanded="visible"
            :aria-busy="loading" :title="displayPath" @keydown.down.prevent="visible = true"
            @keydown.esc.stop.prevent="closePicker">
            <span class="path-value" :class="{ 'is-placeholder': !displayPath }">{{ displayPath || '请选择用户关系路径' }}</span>
            <el-icon :class="{ 'is-loading': loading }"><Loading v-if="loading" /><ArrowDown v-else /></el-icon>
          </button>
          <button v-if="steps.length" type="button" class="path-clear" aria-label="清空用户关系路径"
            @click.stop="selectPath([])"><el-icon><CircleClose /></el-icon></button>
        </div>
      </template>
      <div v-loading="loading" class="path-tree-panel" @keydown.capture="handleTreeKeydown"
        @keydown.esc.stop.prevent="closePicker">
        <p class="path-hint">展开关系查看下级，点击用户关系完成选择。</p>
        <el-alert v-if="treeError" :title="treeError" type="error" :closable="false" show-icon />
        <el-tree v-if="visible && !loading && processId && entityCode" ref="tree" node-key="key" lazy :load="loadChildren"
          :props="treeProps" :default-expanded-keys="expandedKeys" :current-node-key="pathKey(steps)"
          :expand-on-click-node="false" :indent="20" highlight-current
          empty-text="此实体没有可选择的关系" @node-click="selectNode">
          <template #default="{ data }">
            <span class="relation-node" :class="{ 'is-disabled': data.disabled }"
              :title="data.disabledReason || data.labels.join(' → ')">
              <span class="relation-label">{{ data.label }}</span>
              <span class="option-kind">{{ data.multiple ? '多值' : '单值' }}{{ data.terminal ? ' · 用户' : '' }}</span>
              <el-icon v-if="data.key === pathKey(steps)" class="selected-icon"><Check /></el-icon>
            </span>
          </template>
        </el-tree>
        <el-button v-if="error || treeError" text type="primary" :disabled="loading" @click="load">重新加载关系</el-button>
      </div>
    </el-popover>
    <el-alert v-if="error" class="path-error" :title="error" type="error" :closable="false" show-icon />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowDown, Check, CircleClose, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  processId: { type: String, required: true },
  entityCode: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'state', 'change'])
const MAX_DEPTH = 8
const treeProps = { label: 'label', isLeaf: 'isLeaf', disabled: 'disabled' }
const tree = ref()
const trigger = ref()
const visible = ref(false)
const labels = ref([])
const loading = ref(false)
const error = ref('')
const treeError = ref('')
let sequence = 0
let optionsCache = new Map()
const steps = computed(() => props.modelValue.schemaVersion === 2
  ? (props.modelValue.path || [])
  : props.modelValue.fieldCode ? [{ type: 'REFERENCE_FIELD', code: props.modelValue.fieldCode }] : [])
const stepKey = step => `${step.type}:${step.code}:${['REVERSE_REFERENCE', 'PARENT_RELATION'].includes(step.type) ? step.targetEntityCode || '' : ''}`
// 使用完整路径作树节点标识，同一实体在不同分支或循环关系中出现时仍能独立展开。
const pathKey = path => JSON.stringify(path.map(stepKey))
const displayPath = computed(() => labels.value.join(' → '))
const expandedKeys = computed(() => steps.value.slice(0, -1).map((_, index) => pathKey(steps.value.slice(0, index + 1))))

function closePicker() {
  visible.value = false
  trigger.value?.focus()
}

/** 弹层打开后将焦点交给树，保留方向键导航和 Enter 选择能力。 */
async function focusTree() {
  await nextTick()
  const element = tree.value?.$el
  const item = element?.querySelector('.is-current[role="treeitem"]') || element?.querySelector('[role="treeitem"]')
  // 懒加载展开时嵌套容器仍在过渡，原生自动滚动会把中间关系滚到裁剪区外。
  item?.focus({ preventScroll: true })
}

/** 左右键只展开/收起，Enter/空格才选定，避免组件默认的左右键点击行为误选用户。 */
function handleTreeKeydown(event) {
  if (!['ArrowLeft', 'ArrowRight', 'Enter', ' '].includes(event.key)) return
  const item = event.target.closest('[role="treeitem"]')
  const node = item && tree.value?.getNode(item.dataset.key)
  if (!node) return
  event.preventDefault()
  event.stopPropagation()
  if (node.data.disabled) return
  if (event.key === 'Enter' || event.key === ' ') selectNode(node.data, node)
  else if (event.key === 'ArrowRight') {
    if (!node.expanded && !node.isLeaf) node.expand()
    else item.querySelector('[role="treeitem"]')?.focus()
  } else if (node.expanded) node.collapse()
  else item.parentElement.closest('[role="treeitem"]')?.focus()
}

/** 仅确认用户终点或主动清空时更新配置，浏览其他分支不改变已保存的路径。 */
function selectPath(path) {
  closePicker()
  // 重选同一路径不会触发 model 的 watcher，因此不能先把校验状态改成加载中。
  if (props.modelValue.schemaVersion === 2 && JSON.stringify(path) === JSON.stringify(steps.value)) return
  emit('state', { valid: false, loading: true, multiple: false })
  emit('update:modelValue', { schemaVersion: 2, entityCode: props.entityCode, path })
  emit('change')
}

/** 用户节点既可作为终点，也可通过展开箭头继续进入其部门等关系。 */
function selectNode(data, node) {
  if (data.disabled) return
  if (data.terminal) selectPath(data.path)
  else if (!node.isLeaf) node.expanded ? node.collapse() : node.expand()
}

/** 按前缀缓存设计期元数据；切换流程或配置后使用新缓存，旧请求不会污染新树。 */
async function getOptions(path) {
  const cache = optionsCache
  const key = pathKey(path)
  if (cache.has(key)) return cache.get(key)
  const options = await request.get(`/process/${encodeURIComponent(props.processId)}/user-relation-path/options`, {
    params: { prefix: JSON.stringify(path) }, silentError: true
  })
  cache.set(key, options || [])
  return options || []
}

/** 按需加载展开分支，失败后允许重试；浏览失败不使原本有效的已选路径失效。 */
async function loadChildren(node, resolve, reject) {
  const current = sequence
  const prefix = node.level ? node.data.path : []
  const names = node.level ? node.data.labels : []
  try {
    const options = await getOptions(prefix)
    if (current !== sequence) return reject()
    treeError.value = ''
    resolve(options.map(option => {
      const path = [...prefix, option.step]
      // 最后一层仍须落到用户；用户关系不能被当成叶子，否则无法继续选择所属部门。
      const disabledReason = option.disabledReason || (path.length >= MAX_DEPTH && !option.terminal ? '已达到关系层数上限，请选择用户关系' : '')
      return {
        ...option, path, key: pathKey(path), labels: [...names, option.label],
        disabledReason, disabled: !!disabledReason, isLeaf: !!disabledReason || path.length >= MAX_DEPTH
      }
    }))
  } catch (failure) {
    if (current === sequence) treeError.value = failure.message || '关系加载失败，请重新展开或重试'
    // 不把请求失败当作空分支，否则树会永久标记为叶子，无法再次展开。
    reject()
  }
}

/** 回显并校验已选路径；兼容旧单字段配置，失效坐标保留供用户重新选择。 */
async function load() {
  const current = ++sequence
  optionsCache = new Map()
  loading.value = true
  error.value = ''
  treeError.value = ''
  const selected = steps.value.map(step => ({ ...step }))
  labels.value = selected.map(step => step.code)
  emit('state', { valid: false, loading: true, multiple: false })
  try {
    if (!props.processId || !props.entityCode) throw new Error('当前流程尚未绑定实体')
    if (selected.length > MAX_DEPTH) throw new Error(`关系路径最多支持 ${MAX_DEPTH} 层，请重新选择`)
    let options = await getOptions([])
    if (current !== sequence) return
    const prefix = []
    let last
    for (let index = 0; index < selected.length; index++) {
      last = options.find(item => stepKey(item.step) === stepKey(selected[index]))
      if (!last || last.disabledReason) throw new Error(`关系“${selected[index].code}”已失效：${last?.disabledReason || '字段不存在或不可访问'}`)
      labels.value[index] = last.label
      prefix.push(last.step)
      if (index < selected.length - 1) {
        options = await getOptions(prefix)
        if (current !== sequence) return
      }
    }
    let multiple = false
    if (selected.length) {
      if (!last?.terminal) throw new Error('请选择关联到用户的完整路径')
      const description = await request.get(`/process/${encodeURIComponent(props.processId)}/user-relation-path`, {
        params: { path: JSON.stringify(selected) }, silentError: true
      })
      if (current !== sequence) return
      multiple = description.multiple
    }
    emit('state', { valid: !!last?.terminal, loading: false, multiple })
  } catch (failure) {
    if (current !== sequence) return
    error.value = failure.message || '关系路径加载失败'
    emit('state', { valid: false, loading: false, multiple: false })
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(() => JSON.stringify([props.processId, props.entityCode, steps.value]), load, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>

<style scoped>
.user-relation-path { width: 100%; min-height: 32px; }
.path-trigger { display: flex; align-items: center; width: 100%; border-radius: var(--el-border-radius-base); background: var(--el-fill-color-blank); box-shadow: 0 0 0 1px var(--el-border-color) inset; }
.path-trigger:hover { box-shadow: 0 0 0 1px var(--el-border-color-hover) inset; }
.path-trigger.is-open, .path-trigger:focus-within { box-shadow: 0 0 0 1px var(--el-color-primary) inset; }
.path-trigger.is-error { box-shadow: 0 0 0 1px var(--el-color-danger) inset; }
.path-input, .path-clear { border: 0; background: transparent; color: var(--el-text-color-regular); cursor: pointer; font: inherit; }
.path-input { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; padding: 5px 12px; text-align: left; outline: none; }
.path-value { flex: 1; min-width: 0; overflow-wrap: anywhere; line-height: 22px; }
.path-value.is-placeholder, .path-input > .el-icon { color: var(--el-text-color-placeholder); }
.path-clear { display: flex; align-items: center; padding: 6px 10px 6px 0; color: var(--el-text-color-placeholder); }
.path-clear:hover { color: var(--el-text-color-secondary); }
.path-tree-panel { min-height: 64px; }
.path-tree-panel :deep(.el-tree) { max-height: 320px; overflow: auto; }
.path-tree-panel :deep(.el-tree-node__content) { min-height: 34px; height: auto; }
.path-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin: 0 0 8px; }
.relation-node { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; padding: 5px 8px 5px 0; line-height: 24px; }
.relation-label { overflow-wrap: anywhere; white-space: normal; }
.option-kind { flex-shrink: 0; color: var(--el-text-color-secondary); font-size: 12px; }
.relation-node.is-disabled { color: var(--el-text-color-disabled); cursor: not-allowed; }
.selected-icon { flex-shrink: 0; margin-left: auto; color: var(--el-color-primary); }
.path-error { margin-top: 8px; }
</style>
