<template>
  <div v-loading="loading" class="archive-panel">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-select v-if="!archiveId && rounds.length" v-model="selectedId" placeholder="选择历史流程" @change="loadDetail">
      <el-option v-for="round in rounds" :key="round.id" :value="round.id"
        :label="`${round.name} · ${round.sourceId} · ${resultLabel(round.result)}`" />
    </el-select>
    <el-empty v-if="!loading && !detail && !error" description="这条业务记录暂无迁入流程历史" />
    <template v-if="detail">
      <el-alert title="历史迁入 · 图中标记表示有映射记录；连线不代表旧系统实际执行路径。" type="info" :closable="false" />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="流程">{{ detail.instance.name }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ resultLabel(detail.instance.result) }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.sourceSystem }} / {{ detail.instance.sourceId }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ detail.instance.initiator }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.instance.startedAt || '未提供' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detail.instance.endedAt || '未提供' }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="流程图与审批记录" name="history">
          <div class="archive-diagram">
            <VueBpmnViewer :key="detail.id" :xml="detail.template.xml" navigated fit-viewport archive-mode
              @imported="markNodes" @node-click="selectNode" @error="error = '历史流程图加载失败，可继续查看下方审批记录'" />
          </div>
          <div class="archive-filter">
            <strong>{{ selectedNode ? `节点：${detail.template.nodes[selectedNode] || selectedNode}` : '全部审批记录' }}</strong>
            <el-button v-if="selectedNode" link type="primary" @click="selectedNode = ''">查看全部</el-button>
            <span>展示版本：{{ detail.template.name }} v{{ detail.template.version }}</span>
          </div>
          <el-table :data="visibleEvents" border empty-text="该节点没有映射的历史记录">
            <el-table-column prop="sequence" label="顺序" width="70" />
            <el-table-column prop="sourceNode" label="旧节点" min-width="110" />
            <el-table-column prop="actor" label="办理人" min-width="100" />
            <el-table-column label="操作" min-width="110"><template #default="{ row }">{{ actionLabel(row.action) }}<small v-if="row.action !== row.rawAction">（原：{{ row.rawAction }}）</small></template></el-table-column>
            <el-table-column prop="comment" label="审批意见" min-width="200" />
            <el-table-column label="时间" width="180"><template #default="{ row }">{{ row.handledAt || '未提供' }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="实体数据快照" name="data">
          <el-alert title="以下是迁入时的实体最终数据，审批记录不包含当时的逐节点表单快照。" type="info" :closable="false" />
          <el-descriptions :column="2" border>
            <el-descriptions-item v-for="field in detail.fields" :key="field.code" :label="field.name">{{ formatValue(detail.data[field.code]) }}</el-descriptions-item>
          </el-descriptions>
          <el-collapse v-if="detail.related?.length">
            <el-collapse-item v-for="record in detail.related" :key="`${record.entityCode}/${record.recordId}`" :title="`${record.name} · ${record.recordId}`">
              <el-descriptions :column="2" border>
                <el-descriptions-item v-for="field in record.fields" :key="field.code" :label="field.name">{{ formatValue(record.data[field.code]) }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import VueBpmnViewer from '@/components/VueBpmnViewer.vue'
import { historyImportApi } from '@/api/historyImport'
import { archiveNodeEvents, archiveActionLabels, archiveResultLabels } from '@/shared/history-import'
const props = defineProps({ archiveId: String, entityCode: String, recordId: String })
const loading = ref(false), error = ref(''), rounds = ref([]), selectedId = ref(''), detail = ref(null)
const selectedNode = ref(''), activeTab = ref('history')
let generation = 0
const visibleEvents = computed(() => archiveNodeEvents(detail.value?.events, selectedNode.value))
const resultLabel = result => archiveResultLabels[result] || result
const actionLabel = action => archiveActionLabels[action] || action
const formatValue = value => value == null ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value)

/** 快速切换记录时丢弃旧响应，避免显示上一个实体的历史数据。 */
async function loadDetail(id) {
  const current = ++generation
  loading.value = true; error.value = ''; detail.value = null; selectedNode.value = ''; activeTab.value = 'history'
  try {
    const value = await historyImportApi.archive(id)
    if (current === generation) detail.value = value
  } catch (failure) { if (current === generation) error.value = failure.message || '读取历史失败' }
  finally { if (current === generation) loading.value = false }
}
watch(() => [props.archiveId, props.entityCode, props.recordId], async () => {
  const current = ++generation
  detail.value = null; rounds.value = []; error.value = ''; loading.value = true
  try {
    if (props.archiveId) { await loadDetail(props.archiveId); return }
    const value = await historyImportApi.archives(props.entityCode, props.recordId)
    if (current !== generation) return
    rounds.value = value
    selectedId.value = value[0]?.id || ''
    if (selectedId.value) await loadDetail(selectedId.value)
  } catch (failure) { if (current === generation) error.value = failure.message || '读取历史失败' }
  finally { if (current === generation) loading.value = false }
}, { immediate: true })

function selectNode(node) { selectedNode.value = detail.value?.template.nodes[node.id] ? node.id : '' }
function markNodes({ viewer }) {
  const counts = new Map()
  for (const event of detail.value.events) if (event.nodeId && event.nodeId !== 'TIMELINE_ONLY')
    counts.set(event.nodeId, (counts.get(event.nodeId) || 0) + 1)
  for (const [id, count] of counts) {
    if (!viewer.get('elementRegistry').get(id)) continue
    viewer.get('canvas').addMarker(id, 'archive-recorded')
    const badge = document.createElement('span')
    badge.className = 'archive-node-count'; badge.textContent = `${count} 条记录`
    viewer.get('overlays').add(id, { position: { bottom: -10, right: -8 }, html: badge })
  }
}
</script>
<style scoped>
.archive-panel { min-height: 180px; }
.archive-panel > * + * { margin-top: 16px; }
.archive-panel > .el-select { width: 100%; }
.archive-diagram { height: 400px; border: 1px solid var(--el-border-color); }
.archive-filter { display: flex; gap: 16px; align-items: center; margin: 16px 0; flex-wrap: wrap; }
.archive-filter span, small { color: var(--el-text-color-secondary); }
:deep(.archive-recorded .djs-visual > :first-child) { stroke: #2563eb !important; stroke-width: 3px !important; }
:deep(.archive-node-count) { background: #2563eb; color: white; padding: 3px 7px; border-radius: 10px; white-space: nowrap; font-size: 12px; }
</style>
