<template>
  <el-button
    :link="mode === 'row'"
    :type="mode === 'row' ? 'primary' : 'default'"
    @click="inspect"
  >
    <el-icon><View /></el-icon>
    {{ mode === 'row' ? '扩展检查' : '检查扩展状态' }}
  </el-button>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'

const props = defineProps({
  mode: { type: String, default: 'toolbar' },
  row: { type: Object, default: null },
  context: { type: Object, default: () => ({}) }
})

function inspect() {
  const selectedCount = props.context.selectedRows?.length || 0
  const identity = props.row?.dataNo
    || props.row?.code
    || props.row?.id
  console.info('[ProjectExtensionAcceptance] 自定义按钮组件执行', {
    mode: props.mode,
    entityCode: props.context.entityCode,
    rowId: props.row?.id,
    selectedCount
  })
  ElMessage.success(
    props.mode === 'row'
      ? `按钮组件已读取记录：${identity || '-'}`
      : `按钮组件已读取 ${selectedCount} 条选中记录`
  )
}
</script>
