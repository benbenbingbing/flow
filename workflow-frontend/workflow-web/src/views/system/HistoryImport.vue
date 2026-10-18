<template>
  <div class="history-import" v-loading="busy">
    <el-page-header v-if="editing" @back="back" :content="batch ? batch.name : '新建历史导入批次'" />
    <div v-else class="heading"><div><h2>历史数据导入</h2><p>导入已结束的流程、审批记录及关联业务实体。</p></div><el-button v-if="canExecute" type="primary" @click="newBatch">新建批次</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />
    <template v-if="!editing">
      <el-table :data="batches" border>
        <el-table-column prop="name" label="批次" min-width="200" />
        <el-table-column label="状态" width="180"><template #default="{ row }">{{ stateLabel(row.state) }}</template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="200" />
        <el-table-column label="操作" width="160"><template #default="{ row }"><el-button link type="primary" @click="openBatch(row.id)">查看 / 继续</el-button></template></el-table-column>
      </el-table>
      <div class="actions"><el-button :disabled="page === 1" @click="changePage(-1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="batches.length < 20" @click="changePage(1)">下一页</el-button></div>
    </template>
    <template v-else>
      <div v-if="batch" class="actions"><el-tag>{{ stateLabel(batch.state) }}</el-tag><span>批次 {{ batch.id }}</span><el-button v-if="canExecute && !running" @click="cloneBatch">复制配置新建</el-button><el-button @click="downloadReport">下载执行报告</el-button></div>
      <el-card header="1. 实体与展示流程配置">
        <el-form label-position="top" :disabled="!editable || busy">
          <div class="form-grid">
            <el-form-item label="批次名称"><el-input v-model="plan.name" maxlength="200" /></el-form-item>
            <el-form-item label="来源系统标识（后续批次保持一致）"><el-input v-model="plan.sourceSystem" :disabled="!!batch" maxlength="200" /></el-form-item>
            <el-form-item label="展示流程发布版本（仅业务数据可不选）"><el-select v-model="plan.processVersionId" clearable filterable @change="plan.nodeMappings = {}"><el-option v-for="process in catalog.processes" :key="process.id" :value="process.id" :label="`${process.name} v${process.version}`" /></el-select></el-form-item>
          </div>
          <el-alert title="模板由系统迁移字段与实体发布字段生成。关联字段可以填旧 ID，通过下方配置转换；主从记录请填写相同 groupKey。" type="info" :closable="false" />
          <div v-for="(spec, index) in plan.entities" :key="index" class="entity-config">
            <div class="form-grid">
              <el-form-item label="目标实体"><el-select v-model="spec.entityCode" filterable @change="spec.references = {}"><el-option v-for="entity in catalog.entities" :key="entity.code" :value="entity.code" :label="`${entity.name} (${entity.code})`" /></el-select></el-form-item>
              <el-form-item label="来源实体标识 / Sheet 名（固定不变）"><el-input v-model="spec.key" maxlength="26" placeholder="如 expense 或 expense_item" /></el-form-item>
              <el-form-item label="迁入后行为"><el-switch v-model="spec.readonly" active-text="历史只读" inactive-text="共享业务数据，可继续维护" /></el-form-item>
            </div>
            <div class="reference-grid">
              <el-form-item v-for="field in referenceFields(spec)" :key="field.code" :label="`${field.name} (${field.code}) 的旧 ID 来自`">
                <el-select v-model="spec.references[field.code]" clearable placeholder="不转换，已是本系统 ID">
                  <el-option v-for="target in referenceTargets(spec, field, plan, catalog.entities)" :key="target.key" :value="target.key" :label="target.key || '请填写实体标识'" :disabled="!target.key" />
                </el-select>
              </el-form-item>
            </div>
            <el-collapse><el-collapse-item :title="`模板字段（${schema(spec).fields.length}）`"><el-table :data="schema(spec).fields" size="small"><el-table-column prop="code" label="列名" /><el-table-column prop="name" label="名称" /><el-table-column prop="type" label="类型" /><el-table-column label="必填"><template #default="{ row }">{{ row.required ? '是' : '否' }}</template></el-table-column></el-table></el-collapse-item></el-collapse>
            <el-button v-if="plan.entities.length > 1" link type="danger" @click="plan.entities.splice(index, 1)">移除此实体</el-button>
          </div>
          <div class="actions"><el-button :disabled="plan.entities.length >= 20" @click="addEntity">添加关联业务实体</el-button><el-button type="primary" @click="savePlan">{{ batch ? '保存配置' : '创建批次' }}</el-button></div>
        </el-form>
      </el-card>
      <template v-if="batch">
        <el-card header="2. 下载模板并上传数据">
          <p>仅支持 XLSX，单文件不超过 10 MB；每批实体、流程概要及审批记录合计最多 2000 行，单组最多 200 条实体。ID 请按文本保存，多值用英文分号分隔，时间格式为 YYYY-MM-DD HH:mm:ss。</p>
          <div class="file-grid">
            <div v-for="fileKind in fileKinds" :key="fileKind.key" class="file-box">
              <strong>{{ fileKind.name }}</strong><el-tag v-if="batch[`${fileKind.key}Uploaded`]" type="success">已上传</el-tag>
              <div class="actions"><el-button @click="downloadTemplate(fileKind.key)">下载模板</el-button>
                <label v-if="editable" class="upload-label">上传 XLSX<input type="file" accept=".xlsx" :disabled="busy" @change="event => upload(fileKind.key, event)" /></label>
              </div>
            </div>
          </div>
          <p>流程概要的 result：APPROVED、REJECTED、WITHDRAWN、TERMINATED 或 COMPLETED_UNKNOWN；expectedEvents 必须等于该流程的实际审批行数。没有审批记录时填 0。</p>
        </el-card>
        <el-card v-if="batch.sourceNodes?.length" header="3. 旧节点与审批操作映射">
          <el-alert title="每个旧节点必须映射到新图节点，或明确选择仅时间线。多个旧节点可对应同一节点，同节点多次审批会全部保留。" type="info" :closable="false" />
          <el-form label-position="top" :disabled="!editable">
            <div class="reference-grid"><el-form-item v-for="node in batch.sourceNodes" :key="node" :label="node"><el-select v-model="plan.nodeMappings[node]" filterable placeholder="选择新节点"><el-option value="TIMELINE_ONLY" label="仅时间线（不映射到图）" /><el-option v-for="(label, id) in targetNodes" :key="id" :value="id" :label="`${label} (${id})`" /></el-select></el-form-item></div>
            <div class="reference-grid"><el-form-item v-for="action in batch.sourceActions" :key="action" :label="`原操作：${action}`"><el-select v-model="plan.actionMappings[action]" clearable placeholder="保留原始操作"><el-option v-for="(label, value) in archiveActionLabels" :key="value" :value="value" :label="label" /></el-select></el-form-item></div>
            <el-button type="primary" @click="savePlan">保存映射</el-button>
          </el-form>
        </el-card>
        <el-card header="4. 联合预检与执行">
          <el-alert title="预检不写业务数据。执行按提交组分批完成，关闭页面后不再发起下一组；重新打开可继续。已提交组不会重复写入。" type="info" :closable="false" />
          <el-alert v-if="configurationChanged" title="配置已修改，请重新联合预检后再执行。" type="warning" :closable="false" />
          <div v-if="batch.counts" class="actions"><span>实体 {{ batch.counts.entities }}</span><span>流程 {{ batch.counts.instances }}</span><span>审批记录 {{ batch.counts.events }}</span><span>提交组 {{ batch.counts.groups }}</span></div>
          <div class="actions">
            <el-button v-if="editable" type="primary" :disabled="!batch.entitiesUploaded" @click="validate">联合预检</el-button>
            <el-button v-if="canExecute && canRun" type="success" :disabled="running" @click="execute">{{ nextGroup?.state === 'FAILED' ? '重试并继续' : '执行 / 继续导入' }}</el-button>
            <el-button v-if="running" @click="stopRequested = true" :disabled="stopRequested">{{ stopRequested ? '当前组完成后暂停' : '当前组完成后暂停' }}</el-button>
            <el-button :disabled="running" @click="refreshBatch">刷新状态</el-button>
          </div>
          <el-progress v-if="batch.groups?.length" :percentage="progress" />
          <el-table :data="batch.groups || []" border>
            <el-table-column prop="key" label="提交组" min-width="140" />
            <el-table-column label="状态" width="150"><template #default="{ row }">{{ stateLabel(row.state) }}</template></el-table-column>
            <el-table-column label="结果" min-width="180"><template #default="{ row }"><span v-if="row.result">新增 {{ row.result.created }}，复用 {{ row.result.reused }}</span><span class="failure">{{ row.error }}</span></template></el-table-column>
            <el-table-column label="流程档案" min-width="120"><template #default="{ row }"><el-button v-for="(id, index) in row.result?.archiveIds || []" :key="id" link type="primary" @click="archiveId = id">历史 {{ index + 1 }}</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </template>
    </template>
    <el-dialog :model-value="!!archiveId" title="迁入流程历史" width="90%" destroy-on-close @close="archiveId = ''"><ProcessArchivePanel v-if="archiveId" :archive-id="archiveId" /></el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { historyImportApi as api } from '@/api/historyImport'
import ProcessArchivePanel from '@/components/ProcessArchivePanel.vue'
import { archiveActionLabels, importStateLabels, nextImportGroup, referenceTargets } from '@/shared/history-import'

const user = useUserStore()
const canExecute = computed(() => user.isSuperAdmin || user.permissions.includes('history-import:execute') || user.permissions.includes('*'))
const batches = ref([]), catalog = ref({ entities: [], processes: [] }), batch = ref(null), page = ref(1)
const editing = ref(false), busy = ref(false), error = ref(''), running = ref(false), stopRequested = ref(false), archiveId = ref('')
const emptyPlan = () => ({ name: '', sourceSystem: '', entities: [{ key: 'main', entityCode: '', readonly: true, references: {} }], processVersionId: '', nodeMappings: {}, actionMappings: {} })
const plan = ref(emptyPlan())
const fileKinds = [{ key: 'entities', name: '实体数据（多个 Sheet）' }, { key: 'history', name: '流程概要 + 审批记录（可选）' }]
const editable = computed(() => canExecute.value && !running.value && (!batch.value || ['DRAFT', 'UPLOADED', 'READY'].includes(batch.value.state)))
const nextGroup = computed(() => nextImportGroup(batch.value))
// 未保存配置也应使执行按钮失效，避免页面选择与服务端已冻结的导入配置不一致。
const configurationChanged = computed(() => !!batch.value && JSON.stringify(plan.value) !== JSON.stringify(batch.value.plan))
const canRun = computed(() => !configurationChanged.value && batch.value?.validationHash && ['READY', 'RUNNING', 'PARTIAL_SUCCESS', 'FAILED'].includes(batch.value.state))
const targetNodes = computed(() => catalog.value.processes.find(process => process.id === plan.value.processVersionId)?.nodes || {})
const progress = computed(() => Math.round((batch.value.groups || []).filter(group => group.state === 'COMMITTED').length * 100 / Math.max(1, batch.value.groups?.length || 0)))
const stateLabel = state => importStateLabels[state] || state
const schema = spec => catalog.value.entities.find(entity => entity.code === spec.entityCode) || { fields: [] }
const referenceFields = spec => schema(spec).fields.filter(field => referenceTargets(spec, field, plan.value, catalog.value.entities).length)
const addEntity = () => plan.value.entities.push({ key: `entity${plan.value.entities.length + 1}`, entityCode: '', readonly: true, references: {} })
function apply(value) { batch.value = value; plan.value = structuredClone(value.plan); plan.value.nodeMappings ||= {}; plan.value.actionMappings ||= {}; plan.value.entities.forEach(spec => { spec.references ||= {} }) }
async function task(work) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await work() } catch (failure) { error.value = failure.message || '操作失败，请刷新后重试' }
  finally { busy.value = false }
}
async function loadList() { batches.value = await api.list(page.value) }
function newBatch() { batch.value = null; plan.value = emptyPlan(); editing.value = true; error.value = '' }
function cloneBatch() { plan.value = JSON.parse(JSON.stringify(batch.value.plan)); plan.value.name += '（新批次）'; batch.value = null; error.value = '' }
function openBatch(id) { task(async () => { apply(await api.get(id)); editing.value = true }) }
function back() { if (running.value) { stopRequested.value = true; return }; editing.value = false; task(loadList) }
function changePage(step) { page.value += step; task(loadList) }
function savePlan() { task(async () => { apply(batch.value ? await api.save(batch.value.id, batch.value.revision, plan.value) : await api.create(plan.value)); ElMessage.success('配置已保存，文件或映射变化后需重新预检') }) }
function refreshBatch() { task(async () => apply(await api.get(batch.value.id))) }
function validate() { task(async () => {
  // 先持久化当前映射，再预检；避免用户看到的选择与服务端冻结配置不一致。
  apply(await api.save(batch.value.id, batch.value.revision, plan.value))
  apply(await api.validate(batch.value.id, batch.value.revision))
  ElMessage.success('预检通过，可以执行导入')
}) }
function saveBlob(blob, name) { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
/** 模板和解析都使用持久化配置；先保存编辑内容，避免新增 Sheet 或引用映射被上传响应覆盖。 */
async function saveChangedConfiguration() {
  if (configurationChanged.value && editable.value) apply(await api.save(batch.value.id, batch.value.revision, plan.value))
}
function downloadTemplate(kind) { task(async () => {
  await saveChangedConfiguration()
  saveBlob(await api.template(batch.value.id, kind), `history-${kind}.xlsx`)
}) }
function downloadReport() { saveBlob(new Blob([JSON.stringify(batch.value, null, 2)], { type: 'application/json' }), `history-${batch.value.id}-report.json`) }
function upload(kind, event) { const file = event.target.files?.[0]; event.target.value = ''; if (file) task(async () => {
  await saveChangedConfiguration()
  apply(await api.upload(batch.value.id, batch.value.revision, kind, file))
}) }

/** 一次仅有一个提交请求；响应不确定时停止，刷新状态后由用户重试同组。 */
async function execute() {
  if (running.value || !canRun.value) return
  running.value = true; stopRequested.value = false; error.value = ''
  const id = batch.value.id
  try {
    while (!stopRequested.value) {
      const group = nextImportGroup(batch.value)
      if (!group) break
      apply(await api.execute(id, batch.value.validationHash, group.key, group.state === 'FAILED'))
    }
    if (batch.value.state === 'SUCCEEDED') ElMessage.success('历史数据导入完成')
  } catch (failure) {
    error.value = failure.message || '执行失败，请检查提交组报告'
    try { apply(await api.get(id)) } catch { /* 原错误保留，稍后手动刷新。 */ }
  } finally { running.value = false }
}
onBeforeUnmount(() => { stopRequested.value = true })
onMounted(() => task(async () => { const results = await Promise.all([api.catalog(), api.list(1)]); catalog.value = results[0]; batches.value = results[1] }))
</script>
<style scoped>
.history-import { padding: 20px; }
.heading, .actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.heading { justify-content: space-between; }
h2 { margin: 0; }
p { color: var(--el-text-color-secondary); line-height: 1.7; }
.actions { margin: 16px 0; }
.notice, .el-card { margin: 18px 0; }
.form-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.reference-grid, .file-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.entity-config { padding: 18px 0; border-bottom: 1px solid var(--el-border-color); }
.file-box { border: 1px solid var(--el-border-color); border-radius: 6px; padding: 16px; }
.file-box strong { margin-right: 12px; }
.upload-label { border: 1px solid var(--el-color-primary); color: var(--el-color-primary); padding: 7px 14px; border-radius: 4px; cursor: pointer; }
.upload-label input { width: 1px; height: 1px; opacity: 0; position: absolute; }
.failure { color: var(--el-color-danger); white-space: pre-wrap; }
@media (max-width: 900px) { .form-grid, .reference-grid, .file-grid { grid-template-columns: 1fr; } }
</style>
