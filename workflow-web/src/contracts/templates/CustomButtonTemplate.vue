<!--
  按钮模板：替换 execute 中的业务步骤即可。当前仅刷新列表，没有写操作。
  自定义组件点击由组件自己处理；宿主不会接收 click 后再执行 customHandler。
-->
<template>
  <el-button
    :link="mode === 'row'"
    :disabled="disabled || pending"
    :loading="pending"
    :title="reason"
    @click="execute"
  >刷新</el-button>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listButtonProps } from '@/contracts/list-action.js'

const props = defineProps(listButtonProps)
const pending = ref(false)

/**
 * 处理点击并防止重复执行；程序调用也遵守 disabled。失败在组件内展示，
 * 因当前列表宿主没有等待并捕获自定义按钮组件内部的异步操作。
 */
async function execute() {
  if (props.disabled || pending.value) return
  pending.value = true
  try {
    await props.context.refresh?.()
  } catch (error) {
    ElMessage.error(error?.message || '刷新失败')
  } finally {
    pending.value = false
  }
}
</script>
