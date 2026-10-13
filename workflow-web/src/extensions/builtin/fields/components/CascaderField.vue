<template>
  <div class="cascader-field" :aria-busy="loading">
    <el-cascader
      :key="revision"
      :model-value="selectedPath"
      :options="displayOptions"
      :props="{ emitPath: true, multiple: false, checkStrictly: false }"
      :placeholder="placeholder"
      :disabled="isDisabled"
      style="width: 100%"
      clearable
      v-on="customEventListeners"
      @expand-change="expandBranch"
      @change="selectPath"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    <div v-if="loading" class="cascader-status" role="status">正在加载级联数据…</div>
    <div v-if="error" class="cascader-error" role="alert">
      {{ error }}
      <el-button text type="primary" :disabled="loading" @click="retryLoad">重试</el-button>
    </div>
    <div v-if="savedValueUnavailable" class="cascader-status">
      已保存值：{{ savedValueText }}（当前选项中不可用）
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formFieldProps } from '@flow/workflow-core/extensions/contracts/form-field'
import { cascaderPath } from '@flow/workflow-core/cascader-runtime'
import { useFormField } from '@flow/workflow-core/vue/useFormField'
import { useCascaderFieldRuntime } from '@flow/workflow-core/vue/useCascaderFieldRuntime'

const props = defineProps(formFieldProps)
const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])
const { placeholder, isDisabled, handleChange, handleBlur, handleFocus, customEventListeners } = useFormField(props, emit)
const { options, loading, error, revision, isLazy, loadChildren, resolvePath, retry, clearError } = useCascaderFieldRuntime(props)
const selectedPath = computed(() => cascaderPath(props.modelValue))

// 非末级节点在请求完成前保留禁用占位项，接口失败不能让父节点变成可选叶子。
const displayOptions = computed(() => {
  function project(nodes, path = []) {
    return nodes.map(node => {
      const nextPath = [...path, node.value]
      return {
        ...node,
        ...(node.children ? { children: project(node.children, nextPath) }
          : !node.leaf ? { children: [{ value: JSON.stringify(['cascader-loading', nextPath]),
            label: '展开后加载下级', disabled: true, leaf: true }] } : {})
      }
    })
  }
  return project(options.value)
})
const savedResolution = computed(() => { options.value; return resolvePath(selectedPath.value) })
const savedValueUnavailable = computed(() => selectedPath.value.length > 0 && !loading.value && !savedResolution.value.complete)
const savedValueText = computed(() => selectedPath.value.map((value, index) => savedResolution.value.labels[index] ?? String(value)).join(' / '))

async function expandBranch(path) {
  if (isDisabled.value) return
  clearError()
  if (!isLazy.value) return
  try { await loadChildren(path) } catch { /* 统一展示可重试错误，保持原值。 */ }
}

function selectPath(value) {
  if (isDisabled.value) return
  const path = cascaderPath(value)
  // 选项刷新期间 UI 可能找不到旧选中节点，不将其当作用户清空；change 仅接受完整叶路径。
  const resolved = resolvePath(path)
  if (path.length && (!resolved.complete || !resolved.nodes.at(-1)?.leaf || resolved.nodes.some(node => node.disabled))) return
  clearError()
  handleChange(path)
}

async function retryLoad() {
  try { await retry() } catch { /* 错误由共享运行时保留。 */ }
}
</script>

<style scoped>
.cascader-field { width: 100%; }
.cascader-status { color: var(--el-text-color-secondary); font-size: 12px; line-height: 20px; }
.cascader-error { color: var(--el-color-danger); font-size: 12px; line-height: 20px; }
</style>
