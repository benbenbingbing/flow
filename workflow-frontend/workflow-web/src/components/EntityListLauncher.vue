<template>
  <slot :open="open">
    <el-button type="primary" @click="open">{{ buttonText }}</el-button>
  </slot>

  <el-dialog
    v-if="presentation === 'DIALOG'"
    v-model="visible"
    :title="title"
    :width="width"
    destroy-on-close
  >
    <EntityDataList
      v-if="visible"
      :entity-code="entityCode"
      :list-key="listKey"
      :config-hash="configHash"
      :default-form="runtimeDefaultForm"
      :allow-default-form-resolve="!defaultFormResolved"
      scene="DIALOG"
      :context="context"
      :selection-mode="selectionMode"
      @confirm="handleConfirm"
      @cancel="visible = false"
    />
  </el-dialog>

  <el-drawer
    v-else
    v-model="visible"
    :title="title"
    :size="width"
    destroy-on-close
  >
    <EntityDataList
      v-if="visible"
      :entity-code="entityCode"
      :list-key="listKey"
      :config-hash="configHash"
      :default-form="runtimeDefaultForm"
      :allow-default-form-resolve="!defaultFormResolved"
      scene="DRAWER"
      :context="context"
      :selection-mode="selectionMode"
      @confirm="handleConfirm"
      @cancel="visible = false"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getFormRuntimeRelease } from '@/api/entityForm'
import { normalizeRuntimeFormRelease } from '@/shared/list-button-form-runtime'
import EntityDataList from '@/views/entity/EntityDataList.vue'

const props = withDefaults(defineProps<{
  entityCode: string
  listKey: string
  configHash?: string
  defaultFormResolved?: boolean
  defaultFormId?: string
  defaultFormConfigHash?: string
  presentation?: 'DIALOG' | 'DRAWER'
  selectionMode?: 'NONE' | 'SINGLE' | 'MULTIPLE'
  context?: Record<string, any>
  title?: string
  buttonText?: string
  width?: string
}>(), {
  presentation: 'DIALOG',
  configHash: '',
  defaultFormResolved: false,
  defaultFormId: '',
  defaultFormConfigHash: '',
  selectionMode: 'SINGLE',
  context: () => ({}),
  title: '选择数据',
  buttonText: '选择',
  width: '80%'
})

const emit = defineEmits<{
  confirm: [rows: any[]]
}>()

const visible = ref(false)
const runtimeDefaultForm = ref<Record<string, any> | null>(null)

function fixedDefaultFormKey() {
  return props.defaultFormResolved && props.defaultFormId ? props.defaultFormId : ''
}

/**
 * 服务端已确定默认表单时，每次重新打开都读取当前配置；不缓存旧页面摘要。
 */
async function open() {
  const key = fixedDefaultFormKey()
  try {
    if (key) {
      const release = await getFormRuntimeRelease(props.defaultFormId, props.context?.runtimeContext || {})
      runtimeDefaultForm.value = normalizeRuntimeFormRelease(
        release,
        props.defaultFormId,
        release?.configHash
          || props.defaultFormConfigHash
      )

    } else if (!key) {
      runtimeDefaultForm.value = null

    }
    visible.value = true
  } catch (error: any) {
    runtimeDefaultForm.value = null

    ElMessage.error(error?.message || '目标列表的固定表单加载失败')
  }
}

function handleConfirm(rows: any[]) {
  emit('confirm', rows)
  visible.value = false
}

defineExpose({ open })
</script>
