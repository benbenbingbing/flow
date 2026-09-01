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
      :release-id="releaseId"
      :release-version="releaseVersion"
      :release-resolution-token="releaseResolutionToken"
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
      :release-id="releaseId"
      :release-version="releaseVersion"
      :release-resolution-token="releaseResolutionToken"
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
  releaseId?: string
  releaseVersion?: number | null
  releaseResolutionToken?: string
  defaultFormResolved?: boolean
  defaultFormId?: string
  defaultFormReleaseId?: string
  defaultFormReleaseVersion?: number | null
  defaultFormReleaseResolutionToken?: string
  presentation?: 'DIALOG' | 'DRAWER'
  selectionMode?: 'NONE' | 'SINGLE' | 'MULTIPLE'
  context?: Record<string, any>
  title?: string
  buttonText?: string
  width?: string
}>(), {
  presentation: 'DIALOG',
  releaseId: '',
  releaseVersion: null,
  releaseResolutionToken: '',
  defaultFormResolved: false,
  defaultFormId: '',
  defaultFormReleaseId: '',
  defaultFormReleaseVersion: null,
  defaultFormReleaseResolutionToken: '',
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
let loadedDefaultFormKey = ''

function fixedDefaultFormKey() {
  if (!props.defaultFormResolved
      || !props.defaultFormId
      || !props.defaultFormReleaseId
      || !props.defaultFormReleaseVersion
      || !props.defaultFormReleaseResolutionToken) {
    return ''
  }
  return [
    props.defaultFormId,
    props.defaultFormReleaseId,
    props.defaultFormReleaseVersion,
    props.defaultFormReleaseResolutionToken
  ].join(':')
}

/**
 * Embed open-list 的目标默认表单在根 Session 内已经解析并固定；普通
 * Flow 页面没有这些坐标时仍保持原先的按当前 ACTIVE 动态解析行为。
 */
async function open() {
  const key = fixedDefaultFormKey()
  try {
    if (key && key !== loadedDefaultFormKey) {
      const release = await getFormRuntimeRelease(
        props.defaultFormId,
        props.defaultFormReleaseId,
        props.defaultFormReleaseVersion,
        props.defaultFormReleaseResolutionToken
      )
      runtimeDefaultForm.value = normalizeRuntimeFormRelease(
        release,
        props.defaultFormId,
        release?.releaseResolutionToken
          || props.defaultFormReleaseResolutionToken
      )
      loadedDefaultFormKey = key
    } else if (!key) {
      runtimeDefaultForm.value = null
      loadedDefaultFormKey = ''
    }
    visible.value = true
  } catch (error: any) {
    runtimeDefaultForm.value = null
    loadedDefaultFormKey = ''
    ElMessage.error(error?.message || '目标列表的固定表单加载失败')
  }
}

function handleConfirm(rows: any[]) {
  emit('confirm', rows)
  visible.value = false
}

defineExpose({ open })
</script>
