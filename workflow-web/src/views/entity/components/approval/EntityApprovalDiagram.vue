<template>
  <div class="approval-diagram-wrapper">
    <VueBpmnViewer
      v-if="bpmnXml && progressData"
      :key="processInstanceId"
      :xml="bpmnXml"
      :progress-data="progressData"
      style="height: 100%;"
    />
    <el-empty v-else description="暂无流程图" />
  </div>
</template>

<script setup lang="ts">
import VueBpmnViewer from '@/components/VueBpmnViewer.vue'

defineProps<{
  bpmnXml: string
  progressData: any
  processInstanceId: string
}>()
</script>

<style scoped lang="scss">
.approval-diagram-wrapper {
  height: 100%;
  min-height: 0;
  max-height: 75vh; 
  overflow-y: auto;
}
</style>
