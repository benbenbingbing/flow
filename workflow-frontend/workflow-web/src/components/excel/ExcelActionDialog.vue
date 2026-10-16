<template>
  <el-dialog v-model="visible" :title="action.operation === 'IMPORT' ? 'Excel 批量新增' : 'Excel 导出'" width="min(860px, 95vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="close" @closed="finish">
    <template #header="{ titleId, titleClass }">
      <span :id="titleId" :class="[titleClass, 'dialog-title']" @click="toggleDiagnostics">{{ action.operation === 'IMPORT' ? 'Excel 批量新增' : 'Excel 导出' }}</span>
    </template>
    <section v-if="action.operation === 'IMPORT' && showDiagnostics" class="import-diagnostics">
      <p>使用方案：{{ action.schemeName }}</p>
      <el-alert :title="importHint" type="info" :closable="false" />
    </section>
    <p v-else-if="action.operation === 'EXPORT'">使用方案：{{ action.schemeName }}</p>
    <template v-if="action.operation === 'IMPORT'">
      <div class="import-upload">
        <div class="upload-toolbar">
          <span class="upload-label">导入文件</span>
          <el-button type="primary" link :icon="Download" :disabled="busy" @click="downloadTemplate">下载导入模板</el-button>
        </div>
        <el-upload ref="upload" v-model:file-list="uploadFiles" class="excel-upload" drag :auto-upload="false" :limit="1" accept=".xlsx,.xls" :disabled="busy" :on-change="chooseFile" :on-exceed="replaceFile" :on-remove="removeFile" :before-remove="() => !busy">
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击选择文件</em></div>
          <template #tip><div class="el-upload__tip">支持 .xlsx、.xls 格式，文件大小不超过 {{ maxFileSizeMb }} MB；再次选择可替换文件。</div></template>
        </el-upload>
      </div>
      <div class="upload-actions"><el-button v-if="action.precheckEnabled !== false" :disabled="!file || busy || imported" @click="submit(true)">预校验</el-button><el-button type="primary" :loading="busy" :disabled="!file || imported" @click="submit(false)">上传并导入</el-button><el-tag v-if="imported" type="success">此文件已有记录保存，请修正未导入数据后重新选择文件</el-tag></div>
    </template>
    <section v-if="requiresSelection && !job" class="export-selection">
      <el-alert :title="action.dataScope === 'SELECTED' ? '请在指定列表中勾选需要导出的记录。' : '请在指定列表中设置筛选，导出全部匹配记录（不限当前页）。'" type="info" :closable="false" />
      <EntityDataList v-if="visible" ref="selectionList" :entity-code="action.selectionSource.entityCode" :list-key="action.selectionSource.listKey" :release-id="action.selectionSource.releaseId" :release-version="action.selectionSource.releaseVersion" :config-hash="action.selectionSource.configHash" scene="FORM_PICKER" selection-mode="MULTIPLE" embedded :show-selection-footer="false" :show-toolbar="false" :show-row-actions="false" :max-height="360" @selection-change="rows => selectionRows = rows" />
    </section>
    <el-alert v-if="action.operation === 'EXPORT' && !job && !requiresSelection" title="点击“确认导出”生成文件，完成后可下载 Excel。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <section v-if="job" class="job-result" v-loading="busy">
      <el-alert v-if="job.filesExpired || job.errorsExpired" :title="job.message || '任务文件和错误明细已超过 24 小时保留期，请重新执行。'" type="warning" :closable="false" />
      <el-alert v-else-if="job.message" :title="job.message" type="warning" :closable="false" />
      <el-alert :title="statusText" :type="isSuccess ? 'success' : isPending ? 'info' : 'warning'" :closable="false" show-icon />
      <el-descriptions v-if="action.operation === 'EXPORT'" :column="2" border><el-descriptions-item label="导出记录">{{ job.exportedRowCount ?? job.dataRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel }}</el-descriptions-item></el-descriptions>
      <el-descriptions v-else :column="3" border><el-descriptions-item label="数据行">{{ job.dataRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="通过">{{ job.validRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="失败">{{ job.invalidRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="跳过空行">{{ job.skippedRowCount ?? job.skippedEmptyRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="实际新增">{{ job.savedRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="未导入">{{ job.unimportedRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="截止未处理">{{ job.stoppedRowCount ?? 0 }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel }}</el-descriptions-item></el-descriptions>
      <div class="upload-actions"><el-button v-if="isPending" @click="refreshJob">刷新状态</el-button><el-button v-if="action.operation === 'EXPORT' && isSuccess && !job.filesExpired" type="primary" @click="downloadResult('RESULT')">下载 Excel</el-button><el-button v-if="!job.filesExpired && (job.errorFileName || job.files?.some(f => f.fileKind === 'ERROR_REPORT'))" @click="downloadResult('ERROR_REPORT')">下载未导入数据</el-button></div>
      <el-alert v-if="job.errorsTruncated" title="错误数量达到上限，以下结果已截断。" type="warning" :closable="false" />
      <el-table v-if="errors.length" :data="errors" border max-height="320"><el-table-column prop="sheetName" label="工作表" width="110" /><el-table-column prop="rowNumber" label="文件行号" width="90" /><el-table-column prop="header" label="列" width="140" /><el-table-column prop="fieldCode" label="字段" width="140" /><el-table-column prop="message" label="错误原因" min-width="220" /></el-table>
      <el-pagination v-if="errorTotal > 20" :current-page="errorPage" :page-size="20" :total="errorTotal" layout="prev,pager,next,total" @current-change="loadErrors" />
    </section>
    <template #footer>
      <el-button :disabled="busy" @click="close">关闭</el-button>
      <el-button v-if="action.operation === 'EXPORT' && !job" type="primary" :loading="busy" :disabled="busy || (requiresSelection && action.dataScope === 'SELECTED' && !selectionRows.length)" @click="confirmExport">{{ error ? '重试导出' : '确认导出' }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, ref } from 'vue'
import { genFileId } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { entityExcelApi as api } from '@/api/entityExcel'
import { rowsOf } from './excelModel'
const emit = defineEmits(['imported'])
// 动态加载复用列表选择器，避免列表宿主与Excel弹窗之间的静态循环依赖。
const EntityDataList = defineAsyncComponent(() => import('@/views/entity/EntityDataList.vue'))
const selectionList = ref(null), selectionRows = ref([])
const requiresSelection = computed(() => action.value.operation === 'EXPORT' && action.value.selectionSource?.type === 'LIST_DIALOG')
const visible = ref(false), busy = ref(false), error = ref(''), action = ref({}), file = ref(null), job = ref(null), errors = ref([]), errorPage = ref(1), errorTotal = ref(0), imported = ref(false)
const upload = ref(null), uploadFiles = ref([]), showDiagnostics = ref(false)
// 使用准备接口返回的已发布方案限制；旧接口未提供该字段时保持 10 MB 的默认值。
const maxFileSizeMb = computed(() => {
  const configured = Number(action.value.maxFileSizeMb)
  return Number.isFinite(configured) && configured > 0 ? configured : 10
})
const isSuccess = computed(() => ['SUCCEEDED','SUCCESS','COMPLETED','VALIDATED'].includes(job.value?.status))
const isPending = computed(() => ['PENDING','RUNNING','VALIDATING','SAVING','QUEUED'].includes(job.value?.status))
const statusLabel = computed(() => ({ SUCCEEDED: '执行成功', SUCCESS: '执行成功', COMPLETED: '执行完成', PARTIAL_SUCCESS: '部分导入成功', VALIDATED: '校验通过', REJECTED: '校验未通过', FAILED: '执行失败', PENDING: '等待处理', QUEUED: '等待处理', RUNNING: '处理中', VALIDATING: '校验中', SAVING: '保存中' })[job.value?.status] || '状态待确认')
const statusText = computed(() => isPending.value ? '任务处理中，可刷新查看结果' : isSuccess.value ? action.value.operation === 'EXPORT' ? '导出文件已生成' : imported.value ? `已新增 ${job.value.savedRowCount} 条记录` : '校验通过，尚未保存' : action.value.operation === 'EXPORT' ? '导出失败，未生成可下载文件' : `导入未全部完成，实际新增 ${job.value?.savedRowCount || 0} 条，未导入 ${job.value?.unimportedRowCount ?? job.value?.invalidRowCount ?? 0} 条`)
// 已发布旧方案继续按整批事务执行；新方案的提示使用后端固定的执行选项，不能从编辑草稿推断。
const importHint = computed(() => action.value.transactionPolicy === 'ATOMIC_ALL'
  ? '此方案使用整批保存，任一行失败则不保存。可在方案设置中保存并发布新的分批提交配置。'
  : `每行新增一条记录，每 ${action.value.batchSize ?? 100} 条分批提交；已成功提交的数据会保留。${action.value.failurePolicy === 'CONTINUE' ? '遇到错误继续处理其余数据，完成后统一返回结果。' : '遇到错误停止返回，后续数据不再导入。'}${action.value.stopAtEmptyRow !== false ? '遇到第一条空行后截止。' : '空行跳过后继续读取。'}`)
let requestIds = {}, resolveOpen, extra = {}, refreshed = false
const newRequestId = () => `excel_${crypto.randomUUID()}`
/** 打开导出仅展示确认面板，避免查看或关闭弹窗就生成文件；令牌仅保存在当前操作内存中。 */
async function open(effect, context = {}) {
  if (visible.value) throw new Error('请先完成当前 Excel 操作')
  const prepared = effect.payload || effect
  if (!prepared.actionContextToken || !['IMPORT','EXPORT','TEMPLATE'].includes(prepared.operation)) throw new Error('Excel 操作上下文不完整，请刷新页面')
  if (prepared.selectionSource?.type === 'LIST_DIALOG' && (!prepared.selectionSource.entityCode || !prepared.selectionSource.listKey)) throw new Error('Excel批量导出缺少指定列表，请重新发布按钮配置')
  action.value = { ...prepared }; extra = context; error.value = ''; job.value = null; file.value = null; errors.value = []; errorTotal.value = 0; imported.value = false; requestIds = {}; refreshed = false; selectionRows.value = []
  uploadFiles.value = []; showDiagnostics.value = false
  if (prepared.operation === 'TEMPLATE') return downloadTemplate()
  visible.value = true
  const completed = new Promise(resolve => { resolveOpen = resolve })
  return completed
}
/** 排障说明按需展开；使用原生连续点击计数，避免单击或双击标题时干扰普通导入。 */
function toggleDiagnostics(event) {
  if (action.value.operation === 'IMPORT' && event.detail > 0 && event.detail % 3 === 0) showDiagnostics.value = !showDiagnostics.value
}
/** 换文件后必须丢弃前一次任务与幂等标识，防止将旧校验结果或保存状态用于新文件。 */
function resetFileState() {
  file.value = null; error.value = ''; imported.value = false; refreshed = false; job.value = null; errors.value = []; errorTotal.value = 0; requestIds = {}
}
/** el-upload 仅负责本地选择和展示；实际上传仍由预校验/导入按钮调用带上下文令牌的接口。 */
function chooseFile(uploadFile) {
  if (busy.value || !uploadFile.raw) return
  const chosen = uploadFile.raw
  resetFileState()
  if (!/\.(xlsx|xls)$/i.test(chosen.name) || chosen.size > maxFileSizeMb.value * 1024 * 1024) {
    upload.value?.clearFiles(); uploadFiles.value = []
    error.value = `请选择不超过 ${maxFileSizeMb.value} MB 的 .xlsx 或 .xls 文件`
    return
  }
  file.value = chosen
}
/** 单文件模式再次选择时替换旧文件，并复用 on-change 的格式/大小校验。 */
function replaceFile(files) {
  if (busy.value || !files.length) return
  upload.value?.clearFiles()
  files[0].uid = genFileId()
  upload.value?.handleStart(files[0])
}
function removeFile() { if (!busy.value) resetFileState() }
async function acceptJob(result) {
  job.value = result; imported.value = Number(result.savedRowCount || 0) > 0
  if (imported.value && !refreshed) { refreshed = true; emit('imported') }
  errors.value = result.errors || []; errorTotal.value = result.errorCount ?? errors.value.length; errorPage.value = 1
  // 过期任务仍保留汇总，但不再申请已经清理的错误明细。
  if (result.errorsExpired) { errors.value = []; errorTotal.value = 0 }
  else if (errorTotal.value > 0 && (result.jobId || result.id)) await loadErrors(1)
}
async function submit(checkOnly) {
  if (busy.value || !file.value || imported.value || (checkOnly && action.value.precheckEnabled === false)) return
  busy.value = true; error.value = ''
  // 校验与实际保存是两个任务，各自复用稳定标识，网络错误后重试不会重复新增。
  const mode = checkOnly ? 'check' : 'import'
  requestIds[mode] ||= newRequestId()
  try { await acceptJob(await api.importFile(file.value, action.value.actionContextToken, requestIds[mode], checkOnly)) }
  catch (e) { error.value = e.message || '导入请求失败，可以重试同一文件' }
  finally { busy.value = false }
}
/** 统一导出确认入口；列表选择在确认时读取，普通导出沿用打开面板时的记录和筛选上下文。 */
async function confirmExport() {
  if (busy.value || job.value || action.value.operation !== 'EXPORT') return
  if (requiresSelection.value) await submitSelectedExport()
  else await submitExport()
}
async function submitSelectedExport() {
  if (busy.value) return
  try {
    const selection = selectionList.value.getExcelSelection()
    if (action.value.dataScope === 'SELECTED' && !selection.recordIds.length) throw new Error('请先选择记录')
    const next = action.value.dataScope === 'SELECTED' ? selection : { recordIds: [], filters: selection.filters }
    // 条件变更是一次新的导出；相同条件重试保留原requestId。
    if (JSON.stringify(extra) !== JSON.stringify(next)) delete requestIds.export
    extra = next
    await submitExport()
  } catch (e) { error.value = e.message }
}
async function submitExport() {
  if (busy.value) return
  error.value = ''; busy.value = true
  requestIds.export ||= newRequestId()
  try { await acceptJob(await api.export({ ...extra, actionContextToken: action.value.actionContextToken, requestId: requestIds.export })) }
  catch (e) { error.value = e.message || '导出失败' }
  finally { busy.value = false }
}
async function refreshJob() { try { await acceptJob(await api.job(job.value.jobId || job.value.id)) } catch (e) { error.value = e.message } }
async function loadErrors(page) { try { const data = await api.errors(job.value.jobId || job.value.id, { pageNum: page, pageSize: 20 }); errors.value = rowsOf(data); errorTotal.value = data.total ?? errors.value.length; errorPage.value = page; if (data.expired) job.value.errorsExpired = true } catch (e) { error.value = e.message } }
/** 保留服务端 Blob MIME，使用已授权任务元数据中的文件名，不回退为 CSV。 */
function saveBlob(blob, fileName) {
  if (!(blob instanceof Blob)) throw new Error('下载未返回有效文件')
  if (!fileName) throw new Error('服务端未返回文件名')
  const url = URL.createObjectURL(blob), link = document.createElement('a')
  link.href = url; link.download = fileName; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
async function downloadTemplate() { try { saveBlob(await api.template({ actionContextToken: action.value.actionContextToken }), action.value.templateFileName) } catch (e) { error.value = e.message; if (!visible.value) throw e } }
async function downloadResult(kind) { try { const metadata = job.value.files?.find?.(f => f.fileKind === kind || f.kind === kind); const name = metadata?.fileName || (kind === 'RESULT' ? job.value.fileName : job.value.errorFileName); saveBlob(await api.download(job.value.jobId || job.value.id, kind), name) } catch (e) { error.value = e.message } }
function close(done) { if (busy.value) return; visible.value = false; if (typeof done === 'function') done() }
function finish() { resolveOpen?.({ savedRowCount: job.value?.savedRowCount || 0 }); resolveOpen = undefined; action.value = {}; file.value = null; uploadFiles.value = []; showDiagnostics.value = false }
onBeforeUnmount(finish)
defineExpose({ open })
</script>
<style scoped>
.dialog-title { user-select: none; }
.import-diagnostics { margin-bottom: 20px; }
.import-diagnostics p { margin: 0 0 12px; color: var(--el-text-color-secondary); }
.upload-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.upload-label { font-weight: 600; color: var(--el-text-color-primary); }
.excel-upload :deep(.el-upload) { width: 100%; }
.excel-upload :deep(.el-upload-dragger) { padding: 28px 16px; background: var(--el-fill-color-lighter); }
.upload-icon { margin-bottom: 12px; font-size: 38px; color: var(--el-color-primary); }
.excel-upload :deep(.el-upload-list__item) { padding: 6px 10px; background: var(--el-fill-color-light); }
.excel-upload :deep(.el-upload-list__item .el-icon--close) { top: 9px; }
.upload-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin: 18px 0; }
.job-result { margin-top: 20px; }
.el-descriptions { margin-top: 12px; }
</style>
