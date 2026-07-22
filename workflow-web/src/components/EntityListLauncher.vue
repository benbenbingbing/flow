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
import EntityDataList from '@/views/entity/EntityDataList.vue'

const props = withDefaults(defineProps<{
  entityCode: string
  listKey: string
  presentation?: 'DIALOG' | 'DRAWER'
  selectionMode?: 'NONE' | 'SINGLE' | 'MULTIPLE'
  context?: Record<string, any>
  title?: string
  buttonText?: string
  width?: string
}>(), {
  presentation: 'DIALOG',
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

function open() {
  visible.value = true
}

function handleConfirm(rows: any[]) {
  emit('confirm', rows)
  visible.value = false
}

defineExpose({ open })
</script>
