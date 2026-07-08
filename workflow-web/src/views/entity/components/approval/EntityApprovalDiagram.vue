<template>
  <div style="height: 400px;">
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
