<template>
  <div class="capability-center">
    <header class="hero">
      <div>
        <p class="eyebrow">PLATFORM CONTROL PLANE</p>
        <h1>平台能力中心</h1>
        <p>把性能治理、配置影响分析、协作发布和在途流程迁移放进同一个可审计操作面。</p>
      </div>
      <div class="badges"><span>发布字段边界</span><span>CAS 并发保护</span><span>全链路审计</span></div>
    </header>

    <el-tabs v-model="activeTab" class="capability-tabs">
      <el-tab-pane label="索引顾问" name="index">
        <section class="workspace-grid">
          <article class="control-card">
            <Heading eyebrow="LIST PERFORMANCE" title="生成受控索引建议" />
            <el-form :model="indexForm" label-position="top">
              <div class="form-grid">
                <el-form-item label="实体编码"><el-input v-model="indexForm.entityCode" placeholder="例如 order" /></el-form-item>
                <el-form-item label="列表标识"><el-input v-model="indexForm.listKey" placeholder="例如 default" /></el-form-item>
                <el-form-item label="过滤字段"><el-input v-model="indexForm.filterFields" placeholder="status,owner_id" /></el-form-item>
                <el-form-item label="排序字段"><el-input v-model="indexForm.sortFields" placeholder="created_at" /></el-form-item>
              </div>
              <el-button type="primary" :loading="busy.index" @click="previewIndex">分析并生成建议</el-button>
              <el-button @click="loadIndexAdvice">刷新历史</el-button>
            </el-form>
          </article>
          <article class="result-card">
            <Heading eyebrow="CONTROLLED DDL" title="建议与执行状态" />
            <el-table :data="indexAdvice" empty-text="暂无索引建议">
              <el-table-column prop="entityCode" label="实体" min-width="120" />
              <el-table-column prop="columns" label="字段" min-width="180" show-overflow-tooltip />
              <el-table-column label="选择性" width="95"><template #default="scope">{{ formatPercent(scope.row.selectivityEstimate ?? scope.row.selectivity_estimate) }}</template></el-table-column>
              <el-table-column prop="estimatedRows" label="预估扫描" width="105" />
              <el-table-column prop="writeCostLevel" label="写入成本" width="95" />
              <el-table-column prop="recommendation" label="依据" min-width="220" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="110" />
              <el-table-column label="操作" width="130"><template #default="scope"><el-button link type="primary" :disabled="!['SUGGESTED', 'FAILED'].includes(scope.row.status)" @click="applyIndex(scope.row)">应用</el-button><el-button link type="danger" :disabled="['APPLIED', 'REJECTED'].includes(scope.row.status)" @click="rejectIndex(scope.row)">拒绝</el-button></template></el-table-column>
            </el-table>
          </article>
        </section>
      </el-tab-pane>

      <el-tab-pane label="配置引用" name="references">
        <section class="workspace-grid">
          <article class="control-card">
            <Heading eyebrow="IMPACT GRAPH" title="查询上下游依赖" />
            <el-form :model="referenceForm" label-position="top">
              <div class="form-grid">
                <el-form-item label="资产类型"><el-input v-model="referenceForm.assetType" placeholder="ENTITY_FORM" /></el-form-item>
                <el-form-item label="资产标识"><el-input v-model="referenceForm.assetKey" /></el-form-item>
                <el-form-item label="版本"><el-input-number v-model="referenceForm.version" :min="1" /></el-form-item>
                <el-form-item label="方向"><el-select v-model="referenceForm.direction"><el-option label="双向" value="BOTH" /><el-option label="被谁引用" value="UPSTREAM" /><el-option label="引用了谁" value="DOWNSTREAM" /></el-select></el-form-item>
              </div>
              <el-button type="primary" :loading="busy.references" @click="loadReferenceGraph">分析影响范围</el-button>
            </el-form>
          </article>
          <article class="result-card">
            <div class="metric-strip"><div><strong>{{ graph.nodes.length }}</strong><span>资产节点</span></div><div><strong>{{ graph.edges.length }}</strong><span>依赖关系</span></div><div><strong>{{ strongEdgeCount }}</strong><span>强依赖</span></div></div>
            <el-table :data="graph.edges" empty-text="输入资产信息后查询">
              <el-table-column label="来源" min-width="180"><template #default="scope">{{ scope.row.sourceType }} / {{ scope.row.sourceKey }}</template></el-table-column>
              <el-table-column label="目标" min-width="180"><template #default="scope">{{ scope.row.targetType }} / {{ scope.row.targetKey }}</template></el-table-column>
              <el-table-column prop="location" label="定位" min-width="220" show-overflow-tooltip />
              <el-table-column prop="strength" label="强度" width="90" />
            </el-table>
          </article>
        </section>
      </el-tab-pane>

      <el-tab-pane label="配置协作" name="collaboration">
        <section class="workspace-grid">
          <article class="control-card">
            <Heading eyebrow="WORKSPACE" title="创建协作空间" />
            <el-form :model="workspaceForm" label-position="top">
              <el-form-item label="资产名称"><el-input v-model="workspaceForm.assetName" /></el-form-item>
              <div class="form-grid">
                <el-form-item label="资产类型"><el-input v-model="workspaceForm.assetType" placeholder="ENTITY_FORM" /></el-form-item>
                <el-form-item label="资产 ID"><el-input v-model="workspaceForm.assetId" /></el-form-item>
              </div>
              <el-button type="primary" :loading="busy.collaboration" @click="createWorkspace">创建空间</el-button>
              <el-button @click="loadWorkspaces">刷新</el-button>
            </el-form>
            <div class="workspace-list">
              <button v-for="workspace in workspaces" :key="workspace.id" :class="{ active: selectedWorkspace?.id === workspace.id }" @click="selectWorkspace(workspace)">
                <span>{{ workspace.assetName || workspace.asset_name }}</span><small>{{ workspace.assetType || workspace.asset_type }} / {{ workspace.assetId || workspace.asset_id }}</small>
              </button>
            </div>
          </article>
          <article class="result-card">
            <Heading eyebrow="BRANCH & REVIEW" title="分支、评审与发布" />
            <el-empty v-if="!selectedWorkspace" description="先选择一个协作空间" />
            <template v-else>
              <div class="inline-actions">
                <el-input v-model="branchForm.name" placeholder="新分支名称" />
                <el-button @click="createBranch">创建分支</el-button>
                <el-select v-model="selectedBranchId" placeholder="选择分支" @change="selectBranch"><el-option v-for="branch in branches" :key="branch.id" :label="`${branch.name} · ${branch.status}`" :value="branch.id" /></el-select>
              </div>
              <el-input v-model="branchContent" type="textarea" :rows="10" placeholder="配置 JSON；保存使用 CAS 版本号" />
              <div class="action-row">
                <el-button type="primary" :disabled="!selectedBranch" @click="saveBranch">保存分支</el-button>
                <el-button :disabled="!selectedBranch" @click="mergeBranch">三方合并</el-button>
                <el-button type="success" :disabled="!selectedBranch" @click="requestReview">发起评审</el-button>
                <el-button type="success" plain :disabled="!latestReview" @click="approveReview">独立审批</el-button>
              </div>
              <el-divider />
              <div class="inline-actions">
                <el-input v-model="commentText" placeholder="评论内容" />
                <el-input v-model="commentPath" placeholder="稳定路径，如 $.fields[code=name]" />
                <el-button :disabled="!selectedBranch" @click="addComment">评论</el-button>
              </div>
              <div class="inline-actions">
                <el-date-picker v-model="scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="定时发布时间" />
                <el-input v-model="releaseCandidateId" placeholder="真实发布候选 ID" />
                <el-input v-model="scheduleIdempotencyKey" placeholder="发布幂等键" />
                <el-button type="warning" :disabled="!selectedBranch" @click="scheduleRelease">创建发布候选并定时发布</el-button>
              </div>
            </template>
          </article>
        </section>
      </el-tab-pane>

      <el-tab-pane label="实例迁移" name="migration">
        <section class="workspace-grid">
          <article class="control-card">
            <Heading eyebrow="FLOWABLE NATIVE MIGRATION" title="建立迁移批次" />
            <el-form :model="migrationForm" label-position="top">
              <el-form-item label="批次名称"><el-input v-model="migrationForm.batchName" /></el-form-item>
              <div class="form-grid">
                <el-form-item label="源流程定义 ID"><el-input v-model="migrationForm.sourceProcessDefinitionId" /></el-form-item>
                <el-form-item label="目标流程定义 ID"><el-input v-model="migrationForm.targetProcessDefinitionId" /></el-form-item>
              </div>
              <el-form-item label="实例 ID"><el-input v-model="migrationForm.processInstanceIds" type="textarea" :rows="4" placeholder="逗号或换行分隔" /></el-form-item>
              <el-form-item label="节点映射 JSON"><el-input v-model="migrationForm.activityMappings" type="textarea" :rows="3" placeholder='例如 {"oldTask":"newTask"}' /></el-form-item>
              <el-form-item label="变量覆盖 JSON"><el-input v-model="migrationForm.variableOverrides" type="textarea" :rows="3" placeholder='例如 {"riskLevel":"HIGH"}' /></el-form-item>
              <el-form-item label="表单 Release 映射 JSON"><el-input v-model="migrationForm.formReleaseMappings" type="textarea" :rows="3" placeholder='例如 {"oldReleaseId":"newReleaseId"}' /></el-form-item>
              <el-form-item label="幂等键"><el-input v-model="migrationForm.idempotencyKey" /></el-form-item>
              <el-button type="primary" :loading="busy.migration" @click="createMigration">创建批次</el-button>
              <el-button @click="loadMigrations">刷新</el-button>
            </el-form>
          </article>
          <article class="result-card">
            <el-table :data="migrationBatches" empty-text="暂无迁移批次">
              <el-table-column prop="batchNo" label="批次" min-width="160" />
              <el-table-column prop="batchName" label="批次名称" min-width="130" />
              <el-table-column prop="sourceProcessDefinitionId" label="源定义" min-width="150" show-overflow-tooltip /><el-table-column prop="targetProcessDefinitionId" label="目标定义" min-width="150" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="105" />
              <el-table-column label="操作" min-width="250"><template #default="scope"><el-button link type="primary" @click="runMigrationAction(scope.row, 'dryRun')">预检</el-button><el-button link type="success" @click="runMigrationAction(scope.row, 'execute')">执行</el-button><el-button link type="warning" @click="runMigrationAction(scope.row, 'pause')">暂停</el-button><el-button link type="danger" @click="runMigrationAction(scope.row, 'retry')">续跑失败项</el-button><el-button link @click="showMigrationDetail(scope.row)">明细</el-button></template></el-table-column>
            </el-table>
            <pre v-if="migrationDetail" class="json-result">{{ JSON.stringify(migrationDetail, null, 2) }}</pre>
          </article>
        </section>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listExperienceApi } from '@/api/listExperience'
import {
  configCollaborationApi,
  configReferenceApi,
  processInstanceMigrationApi
} from '@/api/platformGovernance'

const Heading = defineComponent({
  props: { eyebrow: String, title: String },
  setup: props => () => h('div', { class: 'section-heading' }, [h('small', props.eyebrow), h('h2', props.title)])
})
const activeTab = ref('index')
const busy = reactive({ index: false, references: false, collaboration: false, migration: false })
const asList = (result: any) => Array.isArray(result) ? result : result?.records || result?.list || result?.items || []
const splitValues = (value: string) => String(value || '').split(/[\n,]/).map(item => item.trim()).filter(Boolean)
const fail = (error: any, fallback: string) => ElMessage.error(error?.message || fallback)
const formatPercent = (value: any) => `${(Number(value || 0) * 100).toFixed(2)}%`
const indexForm = reactive({ entityCode: '', listKey: 'default', filterFields: '', sortFields: '' })
const indexAdvice = ref<any[]>([])
async function previewIndex() {
  busy.index = true
  try { indexAdvice.value = asList(await listExperienceApi.analyzeIndexes(indexForm.entityCode, indexForm.listKey, { filterFields: splitValues(indexForm.filterFields), sortFields: splitValues(indexForm.sortFields) })); ElMessage.success('索引建议已生成') }
  catch (error) { fail(error, '生成索引建议失败') } finally { busy.index = false }
}
async function loadIndexAdvice() { if (!indexForm.entityCode || !indexForm.listKey) return; try { indexAdvice.value = asList(await listExperienceApi.indexes(indexForm.entityCode, indexForm.listKey)) } catch (error) { fail(error, '读取索引建议失败') } }
async function applyIndex(row: any) { try { await listExperienceApi.applyIndex(row.id, row.revision); ElMessage.success('索引操作已进入受控执行队列'); await loadIndexAdvice() } catch (error) { fail(error, '应用索引建议失败') } }
async function rejectIndex(row: any) { try { await listExperienceApi.rejectIndex(row.id, row.revision, '平台能力中心人工拒绝'); ElMessage.success('索引建议已拒绝并保留审计信息'); await loadIndexAdvice() } catch (error) { fail(error, '拒绝索引建议失败') } }

const referenceForm = reactive({ assetType: '', assetKey: '', version: 1, direction: 'BOTH' })
const graph = reactive<any>({ nodes: [], edges: [] })
const strongEdgeCount = computed(() => graph.edges.filter((edge: any) => edge.strength === 'STRONG').length)
async function loadReferenceGraph() {
  busy.references = true
  try { const result: any = await configReferenceApi.impact({ type: referenceForm.assetType, key: referenceForm.assetKey, direction: referenceForm.direction, maxDepth: 8 }); graph.nodes = result?.nodes || []; graph.edges = result?.edges || [] }
  catch (error) { fail(error, '分析配置引用失败') } finally { busy.references = false }
}

const workspaceForm = reactive({ assetName: '', assetType: '', assetId: '' })
const workspaces = ref<any[]>([])
const selectedWorkspace = ref<any>(null)
const branches = ref<any[]>([])
const branchForm = reactive({ name: '' })
const selectedBranchId = ref('')
const selectedBranch = computed(() => branches.value.find(item => item.id === selectedBranchId.value))
const branchContent = ref('{}')
const commentText = ref('')
const commentPath = ref('$')
const scheduledAt = ref('')
const latestReview = ref<any>(null)
const releaseCandidateId = ref('')
const scheduleIdempotencyKey = ref('')
async function loadWorkspaces() { try { workspaces.value = asList(await configCollaborationApi.list()) } catch (error) { fail(error, '读取协作空间失败') } }
async function createWorkspace() { busy.collaboration = true; try { await configCollaborationApi.save({ ...workspaceForm, content: {} }); ElMessage.success('协作空间已创建'); await loadWorkspaces() } catch (error) { fail(error, '创建协作空间失败') } finally { busy.collaboration = false } }
async function selectWorkspace(workspace: any) { const detail: any = await configCollaborationApi.get(workspace.id); selectedWorkspace.value = detail; branches.value = detail?.branches || []; latestReview.value = (detail?.reviews || [])[0] || null; selectedBranchId.value = ''; branchContent.value = '{}' }
function selectBranch(id: string) { const branch: any = branches.value.find(item => item.id === id); const content = branch?.content_json ?? branch?.content ?? {}; branchContent.value = typeof content === 'string' ? content : JSON.stringify(content, null, 2) }
async function createBranch() { try { await configCollaborationApi.createBranch(selectedWorkspace.value.id, { branchKey: branchForm.name, branchName: branchForm.name }); ElMessage.success('分支已创建'); await selectWorkspace(selectedWorkspace.value) } catch (error) { fail(error, '创建分支失败') } }
async function saveBranch() { try { const content = JSON.parse(branchContent.value); await configCollaborationApi.saveBranch(selectedBranchId.value, { expectedRevision: selectedBranch.value.revision, content }); ElMessage.success('分支已按 CAS 版本保存'); await selectWorkspace(selectedWorkspace.value) } catch (error) { fail(error, '保存失败，请检查 JSON 或刷新过期版本') } }
async function mergeBranch() { try { await configCollaborationApi.merge(selectedBranchId.value, { expectedWorkspaceRevision: selectedWorkspace.value.revision }); ElMessage.success('三方合并完成'); await selectWorkspace(selectedWorkspace.value) } catch (error) { fail(error, '合并失败，可能存在需要人工处理的冲突') } }
async function requestReview() { try { latestReview.value = await configCollaborationApi.requestReview(selectedWorkspace.value.id, { branchId: selectedBranchId.value }); ElMessage.success('评审请求已创建，请由独立评审人审批') } catch (error) { fail(error, '发起评审失败') } }
async function approveReview() { try { latestReview.value = await configCollaborationApi.decideReview(latestReview.value.id, { approved: true, note: '平台能力中心审批' }); ElMessage.success('评审已绑定当前内容哈希') } catch (error) { fail(error, '评审失败，提交人与评审人必须独立') } }
async function addComment() { try { await configCollaborationApi.comment(selectedWorkspace.value.id, { branchId: selectedBranchId.value, targetKey: commentPath.value, content: commentText.value }); commentText.value = ''; ElMessage.success('评论已添加') } catch (error) { fail(error, '添加评论失败') } }
async function scheduleRelease() { try { await configCollaborationApi.schedule(selectedWorkspace.value.id, { reviewId: latestReview.value?.id, releaseCandidateId: releaseCandidateId.value, scheduledAt: scheduledAt.value, idempotencyKey: scheduleIdempotencyKey.value }); ElMessage.success('已绑定真实发布候选，发布前会执行 preflight') } catch (error) { fail(error, '创建定时发布失败') } }

const migrationForm = reactive({ batchName: '', sourceProcessDefinitionId: '', targetProcessDefinitionId: '', processInstanceIds: '', activityMappings: '{}', variableOverrides: '{}', formReleaseMappings: '{}', idempotencyKey: '' })
const migrationBatches = ref<any[]>([])
const migrationDetail = ref<any>(null)
const normalizeMigration = (row: any) => ({ ...row, batchNo: row.batchNo ?? row.id, batchName: row.batchName ?? row.batch_name, sourceProcessDefinitionId: row.sourceProcessDefinitionId ?? row.source_process_definition_id, targetProcessDefinitionId: row.targetProcessDefinitionId ?? row.target_process_definition_id, revision: row.revision ?? 0 })
async function loadMigrations() { try { migrationBatches.value = asList(await processInstanceMigrationApi.list()).map(normalizeMigration) } catch (error) { fail(error, '读取迁移批次失败') } }
async function createMigration() { busy.migration = true; try { const { activityMappings, variableOverrides, formReleaseMappings, processInstanceIds, ...base } = migrationForm; await processInstanceMigrationApi.create({ ...base, processInstanceIds: splitValues(processInstanceIds), activityMappings: JSON.parse(activityMappings || '{}'), variableOverrides: JSON.parse(variableOverrides || '{}'), formReleaseMappings: JSON.parse(formReleaseMappings || '{}') }); ElMessage.success('迁移批次已创建，请先执行预检'); await loadMigrations() } catch (error) { fail(error, '创建迁移批次失败') } finally { busy.migration = false } }
async function runMigrationAction(row: any, action: 'dryRun' | 'execute' | 'pause' | 'retry') { try { migrationDetail.value = action === 'dryRun' ? await processInstanceMigrationApi.dryRun(row.id) : action === 'execute' ? await processInstanceMigrationApi.execute(row.id, { expectedRevision: row.revision, batchSize: 50 }) : action === 'retry' ? await processInstanceMigrationApi.retry(row.id, { expectedRevision: row.revision, itemIds: [] }) : await processInstanceMigrationApi.pause(row.id); ElMessage.success(action === 'dryRun' ? '预检完成' : action === 'execute' ? '迁移已启动' : action === 'retry' ? '失败或阻断条目已重新预检' : '暂停信号已提交'); await loadMigrations() } catch (error) { fail(error, '迁移操作失败') } }
async function showMigrationDetail(row: any) { try { migrationDetail.value = await processInstanceMigrationApi.get(row.id) } catch (error) { fail(error, '读取迁移明细失败') } }

onMounted(() => Promise.all([loadIndexAdvice(), loadWorkspaces(), loadMigrations()]))
</script>

<style scoped>
.capability-center { min-height: 100%; padding: 26px; color: #18322d; background: radial-gradient(circle at 88% 3%, rgba(221,154,55,.2), transparent 30%), linear-gradient(145deg,#f2f1e9,#e6eee9); }
.hero { display:flex; justify-content:space-between; gap:30px; padding:30px 34px; border-radius:24px; color:#f5f1e6; background:linear-gradient(118deg,#143f36,#1f5d4d 58%,#8a5b23); box-shadow:0 18px 48px rgba(20,63,54,.16); }
.hero h1 { margin:0; font:700 36px/1.1 Georgia,'Times New Roman',serif; }.hero p:last-child { max-width:680px; margin:13px 0 0; color:rgba(255,255,255,.76); }.eyebrow,.section-heading small { display:block; margin:0 0 8px; letter-spacing:.18em; font:700 11px/1.2 Georgia,serif; opacity:.72; }
.badges { display:flex; align-items:flex-end; gap:8px; flex-wrap:wrap; justify-content:flex-end; }.badges span { padding:7px 11px; border:1px solid rgba(255,255,255,.25); border-radius:999px; font-size:12px; background:rgba(255,255,255,.08); }
.capability-tabs { margin-top:20px; }.workspace-grid { display:grid; grid-template-columns:minmax(290px,.82fr) minmax(520px,1.6fr); gap:18px; }.control-card,.result-card { min-width:0; padding:22px; border:1px solid rgba(37,72,63,.12); border-radius:18px; background:rgba(255,255,252,.84); box-shadow:0 10px 30px rgba(41,66,59,.07); backdrop-filter:blur(8px); }
.section-heading { margin-bottom:18px; }.section-heading h2 { margin:0; font:700 21px/1.2 Georgia,'Times New Roman',serif; }.form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 14px; }.metric-strip { display:grid; grid-template-columns:repeat(3,1fr); gap:10px; margin-bottom:18px; }.metric-strip div { display:grid; gap:3px; padding:14px; border-radius:13px; background:#edf3ee; }.metric-strip strong { color:#b1651e; font:700 25px/1 Georgia,serif; }.metric-strip span { color:#5c6d67; font-size:12px; }
.workspace-list { display:grid; gap:8px; margin-top:18px; max-height:320px; overflow:auto; }.workspace-list button { display:grid; gap:3px; width:100%; padding:12px 14px; text-align:left; color:#28443d; border:1px solid #d6e1db; border-radius:11px; background:#fff; cursor:pointer; }.workspace-list button.active { color:#fff; border-color:#285e50; background:#285e50; }.workspace-list small { opacity:.66; }.inline-actions { display:flex; gap:10px; margin-bottom:12px; }.inline-actions > * { flex:1; }.inline-actions .el-button { flex:0 0 auto; }.action-row { display:flex; gap:10px; margin-top:12px; }.json-result { max-height:260px; margin-top:18px; padding:16px; overflow:auto; color:#d9eadf; border-radius:12px; background:#17352f; font-size:12px; }
:deep(.el-tabs__item) { font-weight:700; }:deep(.el-tabs__active-bar) { background:#b76c25; }:deep(.el-tabs__item.is-active) { color:#8d5019; }
@media (max-width:900px) { .capability-center { padding:14px; }.hero { flex-direction:column; padding:24px; }.badges { justify-content:flex-start; }.workspace-grid,.form-grid { grid-template-columns:1fr; }.inline-actions { flex-wrap:wrap; }.inline-actions > * { flex:1 1 100%; } }
</style>
