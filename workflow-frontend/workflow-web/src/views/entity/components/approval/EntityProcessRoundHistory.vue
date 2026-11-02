<template>
  <div class="process-round-history">
    <div v-if="rounds.length > 1" class="process-round-selector">
      <span>流程轮次</span>
      <el-select v-model="selectedKey" aria-label="流程轮次" style="width: 320px" @change="selectRound">
        <el-option v-for="round in rounds" :key="round.key"
          :value="round.key" :label="round.label" />
      </el-select>
    </div>
    <div v-loading="loading" class="process-round-content">
      <el-empty v-if="failed" description="该轮流程加载失败，请重新选择" />
      <ProcessArchivePanel v-else-if="selectedRound?.archiveId"
        :key="selectedRound.archiveId" :archive-id="selectedRound.archiveId" :view="view" />
      <EntityApprovalDiagram v-else-if="view === 'diagram'"
        :bpmn-xml="displayXml" :progress-data="displayProgress" :process-instance-id="selectedInstanceId" />
      <EntityApprovalHistory v-else :process-history="displayHistory" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useProcessDetail } from '@/composables/useProcessDetail'
import EntityApprovalDiagram from './EntityApprovalDiagram.vue'
import EntityApprovalHistory from './EntityApprovalHistory.vue'
import ProcessArchivePanel from '@/components/ProcessArchivePanel.vue'
import { archiveResultLabels } from '@/shared/history-import'

const props = defineProps<{
  processInstanceId: string
  bpmnXml: string
  progressData: any
  processHistory: any[]
  view: 'diagram' | 'history'
  archiveRounds?: any[]
}>()
const selectedKey = ref('')
const loading = ref(false)
const failed = ref(false)
// 历史读取有独立的组合式状态，不能替换宿主正在编辑的数据或正在办理的任务。
const historical = useProcessDetail()
interface HistoryRound {
  key: string
  label: string
  processInstanceId?: string
  archiveId?: string
}
// 引擎实例和迁入档案共用轮次选择，但档案 ID 不能传给引擎进度接口。
const rounds = computed<HistoryRound[]>(() => {
  const nativeRounds = props.processInstanceId
    ? (props.progressData?.rounds?.length ? props.progressData.rounds : [{ processInstanceId: props.processInstanceId, generation: 1 }])
    : []
  return [
    ...nativeRounds.map((round: any) => ({
      key: `native:${round.processInstanceId}`, processInstanceId: round.processInstanceId,
      label: roundLabel(round)
    })),
    ...(props.archiveRounds || []).map(round => ({
      key: `archive:${round.id}`, archiveId: round.id,
      label: `${round.name} · ${round.sourceId} · ${archiveResultLabels[round.result] || round.result}（迁入）`
    }))
  ]
})
const selectedRound = computed(() => rounds.value.find(round => round.key === selectedKey.value))
const selectedInstanceId = computed(() => selectedRound.value?.processInstanceId || '')
const isCurrent = computed(() => selectedInstanceId.value === props.processInstanceId)
const displayXml = computed(() => isCurrent.value ? props.bpmnXml : historical.bpmnXml.value)
const displayProgress = computed(() => isCurrent.value ? props.progressData : historical.progressData.value)
const displayHistory = computed(() => isCurrent.value ? props.processHistory : historical.processHistory.value)
let sequence = 0
watch(() => [props.processInstanceId, props.archiveRounds], () => {
  sequence++
  selectedKey.value = props.processInstanceId ? `native:${props.processInstanceId}` : rounds.value[0]?.key || ''
  failed.value = false
  loading.value = false
  historical.resetProcessDetail()
}, { immediate: true })

function roundLabel(round: any) {
  const endLabels: Record<string, string> = { COMPLETED: '正常结束', TERMINATED: '已终止', WITHDRAWN: '已撤回' }
  const status = round.status === 'RUNNING' ? '运行中' : endLabels[round.endType] || '已完成'
  return `第 ${round.generation} 轮 · ${status}`
}

/** 切换仅发出只读请求；快速切换时丢弃旧请求结果，也不改变提交使用的实例 ID。 */
async function selectRound(key: string) {
  const current = ++sequence
  failed.value = false
  if (key.startsWith('archive:')) {
    historical.resetProcessDetail()
    loading.value = false
    return
  }
  const instanceId = selectedInstanceId.value
  if (instanceId === props.processInstanceId) {
    historical.resetProcessDetail()
    loading.value = false
    return
  }
  loading.value = true
  const loaded = await historical.loadProcessDetail(instanceId)
  if (current !== sequence) return
  failed.value = !loaded
  loading.value = false
}
</script>

<style scoped>
.process-round-history { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.process-round-selector { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.process-round-content { flex: 1; min-height: 0; }
</style>
