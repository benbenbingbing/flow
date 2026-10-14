<template>
  <section v-loading="loading" class="excel-settings">
    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" /><el-button v-if="loadError" @click="load">重新加载</el-button>
    <template v-else>
      <div class="scheme-heading"><h3>方案管理</h3><el-button @click="openNew(false)">新增方案</el-button><el-button type="primary" @click="openNew(true)">按实体字段生成方案</el-button></div>
      <el-table :data="schemes" border>
        <el-table-column prop="name" label="方案名称" min-width="160" /><el-table-column prop="schemeCode" label="编码" min-width="150" />
        <el-table-column label="当前发布能力" min-width="160"><template #default="{ row }"><el-tag v-if="supportsOperation(row, 'IMPORT')">导入</el-tag> <el-tag v-if="supportsOperation(row, 'EXPORT')">导出</el-tag><el-tag v-if="!row.publishedConfig" type="info">未发布</el-tag><el-tag v-if="row.status === 'DISABLED'" type="warning">已停用</el-tag></template></el-table-column>
        <el-table-column label="默认" width="140"><template #default="{ row }"><el-tag v-if="row.defaultImport">默认导入</el-tag><el-tag v-if="row.defaultExport">默认导出</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="300"><template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link @click="copy(row)">复制</el-button><el-button link type="primary" :disabled="saving" @click="publish(row)">发布</el-button><el-button link :disabled="saving" @click="toggle(row)">{{ row.status === 'DISABLED' ? '启用' : '停用' }}</el-button><el-button link @click="references(row)">引用</el-button><el-button link type="danger" :disabled="saving" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </template>
    <el-dialog v-model="editorVisible" :title="selected.id ? '编辑 Excel 方案' : '新增 Excel 方案'" width="min(1280px, 95vw)" append-to-body :close-on-click-modal="false" destroy-on-close>
      <el-form label-width="140px">
        <div class="editor-grid">
          <el-form-item label="方案编码" required><el-input v-model="editor.schemeCode" :disabled="Boolean(selected.id)" /></el-form-item>
          <el-form-item label="方案名称" required><el-input v-model="editor.name" /></el-form-item>
          <el-form-item label="说明"><el-input v-model="editor.description" /></el-form-item>
        </div>
        <el-tabs v-model="editorOperation" class="operation-tabs">
          <el-tab-pane label="导入设置" name="IMPORT">
            <div class="operation-switches">
              <el-checkbox v-model="editor.importConfig.enabled" @change="enabled => { if (!enabled) defaultSelection.import = false }">启用导入</el-checkbox>
              <el-checkbox v-model="defaultSelection.import" :disabled="!editor.importConfig.enabled">是否默认</el-checkbox>
            </div>
            <template v-if="editor.importConfig.enabled">
              <el-divider content-position="left">导入文件</el-divider>
              <div class="editor-grid">
                <el-form-item label="导入 Sheet"><el-input v-model="editor.file.sheetName" /></el-form-item>
                <el-form-item label="表头行数"><el-input-number v-model="editor.file.headRowNumber" :min="1" :max="1" /></el-form-item>
                <el-form-item label="最多数据行"><el-input-number v-model="editor.importConfig.maxRows" :min="1" :max="1000" /></el-form-item>
                <el-form-item label="文件大小限制">
                  <template #label><ConfigHelpLabel label="文件大小限制" content="限制每次导入或预校验的 Excel 文件大小，默认 10 MB，可设置 1～50 MB。保存并发布后生效，选择文件和服务端接收时都会校验。1 MB 按 1024 × 1024 字节计算。" /></template>
                  <el-input-number v-model="editor.importConfig.maxFileSizeMb" aria-label="文件大小限制" :min="1" :max="50" :precision="0" /><span class="file-size-unit">MB</span>
                </el-form-item>
              </div>
              <el-divider content-position="left">批量新增</el-divider>
              <el-alert title="按提交条数分批保存，已成功提交的数据会保留；遇到错误按失败策略处理。表单未配置的字段按未知列策略处理，实体保存约束仍生效。保存并发布后按新的分批设置执行。" type="info" :closable="false" />
              <div class="editor-grid import-options">
                <el-form-item label="写入表单" required>
                  <template #label>
                    <ConfigHelpLabel
                      label="写入表单"
                      content="决定 Excel 每行数据按哪个表单的规则新增保存。导入会应用该表单的静态默认值，执行服务端必填、跨字段、唯一性等校验及提交前只读处理；表单未配置的导入字段按“未知列”策略处理，实体自身的保存约束仍生效。请选择当前实体下已启用、已发布且适合新增记录的表单，例如“员工新增”。此设置仅用于导入。"
                    />
                  </template>
                  <el-select v-model="editor.importConfig.writeFormKey" filterable placeholder="选择已发布表单">
                    <el-option v-for="f in forms" :key="f.id" :value="f.formKey" :label="f.formName || f.formKey" />
                  </el-select>
                </el-form-item>
                <el-form-item label="未知列">
                  <template #label>
                    <ConfigHelpLabel
                      label="未知列"
                      content="指实体中存在、已在 Excel 方案导入列中映射，但所选写入表单未配置的字段。直接插入（默认）：按 Excel 方案转换和校验后保存，不执行该字段的表单规则，实体必填、唯一等保存约束仍执行。拒绝并报告：发现这些列就报告错误并阻止导入。忽略额外列：忽略这些列的 Excel 输入，实体默认值及保存约束仍生效。文件中未映射到 Excel 方案的额外表头不属于此处的未知列。"
                    />
                  </template>
                  <el-select v-model="editor.importConfig.unknownColumnPolicy">
                    <el-option label="直接插入" value="DIRECT" />
                    <el-option label="拒绝并报告" value="REJECT" />
                    <el-option label="忽略额外列" value="IGNORE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="校验表头">
                  <template #label><ConfigHelpLabel label="校验表头" content="打开时，文件表头的列标题、数量和顺序必须与当前方案的导入模板一致，不一致则提示导入错误。关闭时不检查标题，数据按方案中的列位置读取，请确认文件列顺序正确。" /></template>
                  <el-switch v-model="editor.importConfig.validateHeader" aria-label="校验表头" />
                </el-form-item>
                <el-form-item label="空行截止">
                  <template #label><ConfigHelpLabel label="空行截止" content="默认打开。从表头后的数据行开始，遇到第一条整行为空的数据行即截止，后续即使有数据也不再导入。关闭时跳过空行并继续读取后面的数据。" /></template>
                  <el-switch v-model="editor.importConfig.stopAtEmptyRow" aria-label="空行截止" />
                </el-form-item>
                <el-form-item label="返回未导入数据">
                  <template #label><ConfigHelpLabel label="返回未导入数据" content="打开后可下载未导入数据，保留原文件整行数据，并追加“行数”和“原因”两列。包括校验或保存失败，以及因空行截止或停止策略未处理的数据；已成功保存的行不会出现在报告中。关闭时仍可查看错误提示，但不生成此文件。" /></template>
                  <el-switch v-model="editor.importConfig.returnUnimported" aria-label="返回未导入数据" />
                </el-form-item>
                <el-form-item label="启用预校验">
                  <template #label><ConfigHelpLabel label="启用预校验" content="打开后，导入面板显示“预校验”按钮，仅检查文件、Excel 方案、实体及写入表单规则，不保存业务数据。预校验通过后仍需点击“上传并导入”，正式导入时会重新校验。" /></template>
                  <el-switch v-model="editor.importConfig.precheckEnabled" aria-label="启用预校验" />
                </el-form-item>
                <el-form-item label="提交条数">
                  <template #label><ConfigHelpLabel label="提交条数" content="每累计这些待保存记录提交一次，范围为 1～1000，最后不足一批也会提交。已提交的批次会保留；停止返回不会撤销之前成功提交的数据。" /></template>
                  <el-input-number v-model="editor.importConfig.batchSize" aria-label="提交条数" :min="1" :max="1000" :precision="0" />
                </el-form-item>
                <el-form-item label="失败策略">
                  <template #label><ConfigHelpLabel label="失败策略" content="停止返回（默认）：遇到第一条数据校验或保存错误后停止处理，返回已有结果；继续执行：记录错误并继续处理其他数据，最后统一返回结果。文件无法读取、表头不匹配等文件级错误均会立即停止。" /></template>
                  <el-select v-model="editor.importConfig.failurePolicy" aria-label="失败策略"><el-option label="停止返回" value="STOP" /><el-option label="继续执行" value="CONTINUE" /></el-select>
                </el-form-item>
              </div>
              <el-divider content-position="left">导入列 · 拖拽序号调整模板列顺序</el-divider>
              <ExcelColumnEditor v-model="editor.importColumns" :fields="fields" :entity-code="entityCode" operation="IMPORT" />
            </template>
          </el-tab-pane>
          <el-tab-pane label="导出设置" name="EXPORT">
            <div class="operation-switches">
              <el-checkbox v-model="editor.exportConfig.enabled" @change="enabled => { if (!enabled) defaultSelection.export = false }">启用导出</el-checkbox>
              <el-checkbox v-model="defaultSelection.export" :disabled="!editor.exportConfig.enabled">是否默认</el-checkbox>
            </div>
            <template v-if="editor.exportConfig.enabled">
              <el-divider content-position="left">导出文件</el-divider>
              <div class="editor-grid">
                <el-form-item label="文件名规则"><el-input v-model="editor.exportConfig.fileNamePattern" placeholder="数据_{yyyyMMddHHmmss}" /></el-form-item>
                <el-form-item label="导出 Sheet"><el-input v-model="editor.exportConfig.sheetName" /></el-form-item>
              </div>
              <el-divider content-position="left">导出列 · 拖拽序号调整文件列顺序</el-divider>
              <ExcelColumnEditor v-model="editor.exportColumns" :fields="fields" :entity-code="entityCode" operation="EXPORT" />
            </template>
          </el-tab-pane>
        </el-tabs>
        <el-collapse v-if="editor.inactiveColumns.length" class="inactive-columns">
          <el-collapse-item :title="`未启用的历史列（${editor.inactiveColumns.length}）`" name="inactive">
            <p>这些旧列尚未用于导入或导出，可移除或选定用途后继续编辑。</p>
            <el-table :data="editor.inactiveColumns" border>
              <el-table-column prop="fieldCode" label="实体字段" min-width="160" />
              <el-table-column prop="header" label="列标题" min-width="160" />
              <el-table-column label="操作" width="280">
                <template #default="{ $index }">
                  <el-button link type="primary" @click="restoreInactiveColumn($index, 'IMPORT')">用于导入</el-button>
                  <el-button link type="primary" @click="restoreInactiveColumn($index, 'EXPORT')">用于导出</el-button>
                  <el-button link type="danger" @click="editor.inactiveColumns.splice($index, 1)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveScheme">保存草稿</el-button></template>
    </el-dialog>
    <el-dialog v-model="referencesVisible" title="方案引用" append-to-body width="700px"><el-table :data="referenceRows"><el-table-column label="来源"><template #default="{ row }">{{ ({ FORM: '表单', LIST: '列表', ENTITY: '实体默认设置' })[row.ownerType] || row.ownerType }}</template></el-table-column><el-table-column prop="ownerId" label="来源标识" /><el-table-column label="操作"><template #default="{ row }">{{ ({ IMPORT: '导入', EXPORT: '导出', TEMPLATE: '模板下载' })[row.operation] || row.operation }}</template></el-table-column><el-table-column label="引用模式"><template #default="{ row }">{{ row.mode === 'DEFAULT' ? '实体默认' : '指定方案' }}</template></el-table-column><el-table-column label="状态"><template #default="{ row }">{{ row.published ? '已发布' : '草稿' }}</template></el-table-column></el-table></el-dialog>
  </section>
</template>
<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityExcelApi as api } from '@/api/entityExcel'
import { getFormsByEntity } from '@/api/entityForm'
import { showRequestError } from '@/utils/request'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import ExcelColumnEditor from './ExcelColumnEditor.vue'
import { configOf, createScheme, rowsOf, supportsOperation, validateScheme, schemePayload, splitSchemeForEditor } from './excelModel'
const props = defineProps({ entityCode: { type: String, required: true }, entityId: { type: [String, Number], required: true }, fields: { type: Array, default: () => [] } })
const loading = ref(false), saving = ref(false), loadError = ref(''), schemes = ref([]), settings = ref({}), forms = ref([])
// 默认选择属于实体入口指针，单独保存编辑状态，不能混入方案的列/格式文档或复制给新方案。
const defaultSelection = ref({ import: false, export: false }), settingsRevision = ref(0)
const selected = ref({}), editor = ref(splitSchemeForEditor(createScheme())), editorOperation = ref('IMPORT'), editorVisible = ref(false), referencesVisible = ref(false), referenceRows = ref([])
let loadSequence = 0
/** 切换实体时丢弃旧响应；方案和默认指针一起刷新，避免显示过期默认标记。 */
async function load() {
  const sequence = ++loadSequence
  loading.value = true; loadError.value = ''
  try {
    const [ss, st, fs] = await Promise.all([api.schemes(props.entityCode), api.settings(props.entityCode), getFormsByEntity(props.entityId)])
    if (sequence !== loadSequence) return
    schemes.value = rowsOf(ss); settings.value = st
    forms.value = rowsOf(fs).filter(f => f.activeReleaseId || f.publishedVersion > 0)
  } catch (e) { if (sequence === loadSequence) loadError.value = e.message || '读取 Excel 设置失败' }
  finally { if (sequence === loadSequence) loading.value = false }
}
async function run(action, message) { saving.value = true; try { await action(); ElMessage.success(message); await load() } catch (e) { showRequestError(e, '操作失败') } finally { saving.value = false } }
/** 两个页签使用独立列对象；仅保存时合并为兼容已有方案和按钮引用的服务端文档。 */
function openNew(generate) {
  selected.value = {}; editor.value = splitSchemeForEditor(createScheme(generate ? props.fields : []))
  defaultSelection.value = { import: false, export: false }; settingsRevision.value = settings.value.revision
  editorOperation.value = 'IMPORT'; editorVisible.value = true
}
/** 详情返回与草稿一致的默认指针版本；不能使用打开列表时的旧版本覆盖其他人的默认选择。 */
async function edit(row) {
  try {
    const item = await api.scheme(props.entityCode, row.schemeCode)
    selected.value = item; editor.value = splitSchemeForEditor(configOf(item.draftConfig))
    defaultSelection.value = { import: item.draftDefaultImport === true, export: item.draftDefaultExport === true }
    settingsRevision.value = item.settingsRevision
    editorOperation.value = editor.value.importConfig.enabled ? 'IMPORT' : 'EXPORT'; editorVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}
/** 旧版双禁用列也可能是未完成草稿，提供恢复入口，避免隐藏错误列使用户无法修正或发布。 */
function restoreInactiveColumn(index, operation) {
  const column = editor.value.inactiveColumns[index]
  const importing = operation === 'IMPORT'
  const target = importing ? editor.value.importColumns : editor.value.exportColumns
  if (column.fieldCode && target.some(item => item.fieldCode === column.fieldCode)) {
    ElMessage.warning(`该字段已在${importing ? '导入' : '导出'}列中，请编辑已有映射或移除此历史列`)
    return
  }
  target.push({ ...column, importEnabled: importing, exportEnabled: !importing })
  editor.value.inactiveColumns.splice(index, 1)
  editor.value[importing ? 'importConfig' : 'exportConfig'].enabled = true
  editorOperation.value = operation
}
async function copy(row) { await edit(row); if (!editorVisible.value) return; selected.value = {}; defaultSelection.value = { import: false, export: false }; editor.value.schemeCode = `${editor.value.schemeCode}_copy`; editor.value.name += '（副本）' }
/** 新增和复制以修订号 0 声明新建；编辑保留读取时的修订号，由服务端阻止覆盖并发修改。 */
async function saveScheme() {
  const error = validateScheme(editor.value, { draft: true })
  if (error) return ElMessage.warning(error)
  await run(async () => {
    await api.saveScheme(props.entityCode, {
      id: selected.value.id,
      expectedRevision: selected.value.id ? selected.value.revision : 0,
      expectedSettingsRevision: settingsRevision.value,
      defaultImport: editor.value.importConfig.enabled && defaultSelection.value.import,
      defaultExport: editor.value.exportConfig.enabled && defaultSelection.value.export,
      schemeCode: editor.value.schemeCode,
      name: editor.value.name,
      config: schemePayload(editor.value)
    })
    editorVisible.value = false
  }, '方案草稿已保存，请发布后使用')
}
/** 方案和它的默认选择在服务端同事务发布，无需再单独发布实体默认设置。 */
async function publish(row) { await run(() => api.publishScheme(props.entityCode, row.schemeCode, { expectedRevision: row.revision, expectedSettingsRevision: row.settingsRevision }), '方案及默认选择已发布') }
async function toggle(row) { await run(() => api.status(props.entityCode, row.schemeCode, { expectedRevision: row.revision, status: row.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED' }), '方案状态已更新') }
async function references(row) { try { referenceRows.value = rowsOf(await api.references(props.entityCode, row.schemeCode)); referencesVisible.value = true } catch (e) { ElMessage.error(e.message) } }
async function remove(row) { try { await ElMessageBox.confirm(`删除方案“${row.name}”？已被引用的方案需要先解除引用。`, '删除方案', { type: 'warning' }) } catch { return } await run(() => api.remove(props.entityCode, row.schemeCode, { expectedRevision: row.revision }), '方案已删除') }
watch(() => props.entityCode, load, { immediate: true })
</script>
<style scoped>
.excel-settings { padding: 20px; overflow: auto; flex: 1; }
.excel-settings p { color: var(--el-text-color-secondary); }
.scheme-heading { display: flex; gap: 12px; align-items: center; }
.scheme-heading h3 { margin-right: auto; }
.editor-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
.import-options { margin-top: 16px; }
.file-size-unit { margin-left: 8px; color: var(--el-text-color-secondary); }
.operation-tabs :deep(.el-tabs__item) { font-weight: 600; }
.operation-switches { display: flex; align-items: center; gap: 24px; }
.operation-switches :deep(.el-checkbox) { margin-right: 0; }
.inactive-columns { margin-top: 20px; }
@media(max-width: 700px) { .editor-grid { grid-template-columns: 1fr; } }
</style>
