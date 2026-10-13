<template>
  <el-collapse class="excel-binding" @change="ensureLoaded"><el-collapse-item name="excel" title="Excel 操作快捷配置">
    <el-alert title="使用现有自定义按钮事件，绑定后需发布所属表单或列表。列与格式统一在实体中维护。" type="info" :closable="false" />
    <el-form label-width="100px" v-loading="loading" :disabled="saving">
      <el-form-item v-if="targetType !== 'BUTTON'" label="目标按钮"><el-select v-model="buttonIdentity" @change="loadBinding"><el-option v-for="b in buttonOptions" :key="`${b.eventCode}:${b.key}`" :value="`${b.eventCode}:${b.key}`" :label="`${b.label || b.key}（${b.eventCode === 'ROW_BUTTON_CLICK' ? '行' : '工具栏'}）`" /></el-select></el-form-item>
      <el-form-item v-if="ownerType === 'LIST' && targetType === 'BUTTON' && allowedEvents.length !== 1" label="按钮位置"><el-radio-group v-model="eventCode" @change="loadBinding"><el-radio-button value="TOOLBAR_BUTTON_CLICK">工具栏</el-radio-button><el-radio-button value="ROW_BUTTON_CLICK">行按钮</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="操作"><el-select v-model="config.operation" @change="changeOperation"><el-option v-if="eventCode !== 'ROW_BUTTON_CLICK'" label="Excel 导入（批量新增）" value="IMPORT" /><el-option label="Excel 导出" value="EXPORT" /><el-option label="下载导入模板" value="TEMPLATE" /></el-select></el-form-item>
      <el-form-item label="方案选择"><el-radio-group v-model="config.schemeRef.mode"><el-radio value="DEFAULT">跟随实体默认</el-radio><el-radio value="EXPLICIT">指定方案</el-radio></el-radio-group></el-form-item>
      <el-form-item v-if="config.schemeRef.mode === 'EXPLICIT'" label="指定方案" required><el-select v-model="config.schemeRef.schemeCode" filterable><el-option v-for="s in availableSchemes" :key="s.schemeCode" :value="s.schemeCode" :label="s.name" /></el-select></el-form-item>
      <el-form-item v-if="config.operation === 'EXPORT'" label="数据范围"><el-select v-model="config.dataScope" @change="changeScope"><el-option v-if="ownerType === 'FORM' || eventCode === 'ROW_BUTTON_CLICK'" label="当前已保存记录" value="CURRENT_RECORD" /><template v-if="eventCode !== 'ROW_BUTTON_CLICK'"><el-option label="选中记录" value="SELECTED" /><el-option label="当前筛选结果" value="FILTERED" /></template></el-select></el-form-item>
      <template v-if="ownerType === 'FORM' && config.operation === 'EXPORT' && config.dataScope !== 'CURRENT_RECORD'">
        <el-form-item label="来源列表" required><el-select v-model="config.listKey" filterable placeholder="选择同实体已发布列表"><el-option v-for="list in lists" :key="list.id" :value="list.listKey" :label="list.listName || list.configName || list.listKey" /></el-select></el-form-item>
        <el-form-item label="选择来源"><el-tag>点击按钮后打开此列表，选择记录或设置筛选</el-tag></el-form-item>
      </template>
      <el-form-item><el-button type="primary" :loading="saving" :disabled="!bindingKey || !entityCode || Boolean(loadError)" @click="save">保存 Excel 事件绑定</el-button></el-form-item>
    </el-form>
    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />
    <el-alert v-if="ownerType === 'FORM' && config.operation === 'IMPORT'" type="info" title="请在按钮基本配置中关闭“执行前校验表单”；批量新增不依赖当前表单未保存的内容。" :closable="false" />
  </el-collapse-item></el-collapse>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { entityExcelApi } from '@/api/entityExcel'
import { entityApi } from '@/api/entity'
import { getFormById } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiEventBindingApi, uiExtensionApi } from '@/api/uiConfig'
import { showRequestError } from '@/utils/request'
import { isBuiltinExcelStep } from '@/components/ui-config/eventStepModel'
import { configOf, rowsOf, supportsOperation } from './excelModel'
const props = defineProps({ ownerType: { type: String, required: true }, ownerId: { type: [String,Number], required: true }, targetType: { type: String, default: 'BUTTON' }, targetKey: { type: [String,Number], default: '' }, targetName: { type: String, default: '' }, buttonOptions: { type: Array, default: () => [] }, allowedEvents: { type: Array, default: () => [] } })
const emit = defineEmits(['changed'])
const config = ref({ operation: 'EXPORT', schemeRef: { mode: 'DEFAULT' }, dataScope: props.ownerType === 'FORM' ? 'CURRENT_RECORD' : 'FILTERED' })
const eventCode = ref(props.allowedEvents.length === 1 ? props.allowedEvents[0] : props.ownerType === 'FORM' ? 'FORM_BUTTON_CLICK' : 'TOOLBAR_BUTTON_CLICK'), buttonIdentity = ref(''), entityCode = ref(''), schemes = ref([]), lists = ref([]), loading = ref(false), saving = ref(false), loadError = ref(''), binding = ref(null), extension = ref(null)
const bindingKey = computed(() => props.targetType === 'BUTTON' ? String(props.targetKey) : buttonIdentity.value.split(':').slice(1).join(':'))
const availableSchemes = computed(() => schemes.value.filter(s => supportsOperation(s, config.value.operation)))
let sequence = 0
async function ensureLoaded(active) {
  if (!active.includes('excel') || entityCode.value) return
  loading.value = true; loadError.value = ''
  try {
    const owner = props.ownerType === 'FORM' ? await getFormById(props.ownerId) : await entityListConfigApi.getById(props.ownerId)
    const entity = owner.entityCode ? owner : await entityApi.getById(owner.entityId)
    entityCode.value = entity.entityCode; schemes.value = rowsOf(await entityExcelApi.schemes(entityCode.value))
    if (props.ownerType === 'FORM') lists.value = rowsOf(await entityListConfigApi.getByEntityId(owner.entityId || entity.id)).filter(item => item.activeReleaseId || item.publishedVersion > 0)
    if (bindingKey.value) await loadBinding()
  } catch (e) { loadError.value = e.message || '读取 Excel 绑定失败'; entityCode.value = '' }
  finally { loading.value = false }
}
const extensionId = value => String(value?.extensionId || value?.id || '')
const stepsOf = value => value ? configOf(value.stepsDocument || value.steps || []) : []
const isExcelInterface = value => value?.extensionType === 'INTERFACE'
  && value.implementationType === 'REGISTERED_PROVIDER' && value.providerCode === 'entity_excel_action'
  && value.providerOperationCode === 'prepare'

/**
 * 新配置直接读取按钮步骤中的参数。旧绑定只读取其 Excel 接口，保存时迁移当前按钮；
 * 不改写或删除旧接口，以保留其他按钮和已发布快照的执行能力。
 */
async function readBindingState() {
  const bindings = rowsOf(await uiEventBindingApi.list(props.ownerType, String(props.ownerId)))
  const item = bindings.find(b => b.eventCode === eventCode.value && b.targetType === 'BUTTON' && String(b.targetKey) === bindingKey.value) || null
  const masters = stepsOf(item).filter(s => s.strategy === 'REPLACE')
  const master = masters[0]
  let ext = null
  if (master && !isBuiltinExcelStep(master)) {
    // 此处读取已绑定接口身份；范围已按实体管理，不再用单个表单/列表过滤历史绑定。
    const extensions = rowsOf(await uiExtensionApi.list({ extensionType: 'INTERFACE' }))
    ext = extensions.find(e => extensionId(e) === String(master.extensionId)) || null
  }
  if (masters.length > 1 || master && !isBuiltinExcelStep(master) && !isExcelInterface(ext)) {
    throw new Error('当前按钮已有业务主处理，请先在事件链编辑器中调整，快捷配置不会覆盖它。')
  }
  return { binding: item, extension: ext, master }
}

/** 快捷配置只编辑其拥有的 Excel 主步骤；已有业务主处理必须先在事件链中明确调整。 */
async function loadBinding() {
  const current = ++sequence
  loading.value = true; loadError.value = ''; binding.value = null; extension.value = null
  if (props.targetType !== 'BUTTON') eventCode.value = buttonIdentity.value.split(':')[0]
  try {
    const state = await readBindingState()
    if (current !== sequence) return
    binding.value = state.binding; extension.value = state.extension
    if (state.master) {
      config.value = structuredClone(isBuiltinExcelStep(state.master) ? state.master.actionConfig : configOf(state.extension.implementationConfigDocument || state.extension.implementationConfig))
    } else { config.value = { operation: 'EXPORT', schemeRef: { mode: 'DEFAULT' }, dataScope: props.ownerType === 'FORM' || eventCode.value === 'ROW_BUTTON_CLICK' ? 'CURRENT_RECORD' : 'FILTERED' } }
  } catch (e) { if (current === sequence) loadError.value = e.message }
  finally { if (current === sequence) loading.value = false }
}
function changeOperation() { if (config.value.operation === 'EXPORT') config.value.dataScope = props.ownerType === 'FORM' || eventCode.value === 'ROW_BUTTON_CLICK' ? 'CURRENT_RECORD' : 'FILTERED' }
function changeScope() {
  if (props.ownerType === 'FORM' && config.value.dataScope !== 'CURRENT_RECORD') config.value.selectionSource = { type: 'LIST_DIALOG' }
}
/** 操作参数随事件绑定一次保存、随页面发布冻结；此处不创建接口或触发导入导出。 */
async function save() {
  if (saving.value || loading.value || !bindingKey.value || loadError.value) return
  if (config.value.schemeRef.mode === 'EXPLICIT' && !availableSchemes.value.some(s => s.schemeCode === config.value.schemeRef.schemeCode)) return ElMessage.warning('请选择支持此操作的已发布方案')
  if (props.ownerType === 'FORM' && config.value.operation === 'EXPORT' && config.value.dataScope !== 'CURRENT_RECORD' && !lists.value.some(item => item.listKey === config.value.listKey)) return ElMessage.warning('批量导出必须选择同实体的已发布列表')
  saving.value = true
  try {
    // 同一面板可通过下方事件表删除绑定，保存前重新核对身份；已有配置被他人修改时仍保留乐观锁保护。
    const state = await readBindingState()
    const trackedExtension = state.extension && extensionId(state.extension) === extensionId(extension.value) ? state.extension : null
    if (state.binding && (!binding.value || state.binding.id !== binding.value.id || state.binding.revision !== binding.value.revision)
      || trackedExtension && extension.value && trackedExtension.revision !== extension.value.revision) {
      throw new Error('Excel 事件配置已变更，请重新打开配置后重试')
    }
    binding.value = state.binding
    const actionConfig = { operation: config.value.operation, schemeRef: config.value.schemeRef.mode === 'DEFAULT' ? { mode: 'DEFAULT' } : { mode: 'EXPLICIT', schemeCode: config.value.schemeRef.schemeCode }, ...(config.value.operation === 'EXPORT' ? { dataScope: config.value.dataScope } : {}) }
    if (props.ownerType === 'FORM' && config.value.operation === 'EXPORT' && config.value.dataScope !== 'CURRENT_RECORD') {
      actionConfig.listKey = config.value.listKey
      actionConfig.selectionSource = { type: 'LIST_DIALOG' }
    }
    const oldSteps = binding.value ? configOf(binding.value.stepsDocument || binding.value.steps || []) : []
    const sideSteps = oldSteps.filter(step => step.strategy !== 'REPLACE')
    const occupiedOrders = new Set(sideSteps.map(step => step.order))
    const occupiedCodes = new Set(sideSteps.map(step => step.stepCode).filter(Boolean))
    // 通用编辑器会重新编号；保留当前主步骤的位置和标识，不能固定写 10 而撞上前置步骤。
    // 仅有前后置步骤时挑选未占用编号；不重排业务步骤，避免改变它们的执行顺序。
    let order = Number.isInteger(state.master?.order) ? state.master.order : 10
    while (occupiedOrders.has(order)) order += 10
    const baseCode = state.master?.stepCode || 'excel_action'
    let stepCode = baseCode, suffix = 2
    while (occupiedCodes.has(stepCode)) stepCode = `${baseCode}_${suffix++}`
    const mainStep = { stepCode, strategy: 'REPLACE', actionType: 'ENTITY_EXCEL', actionConfig, order, failurePolicy: 'STOP' }
    const steps = state.master
      ? oldSteps.map(step => step.strategy === 'REPLACE' ? mainStep : step)
      : [...sideSteps.filter(step => step.strategy === 'BEFORE'), mainStep, ...sideSteps.filter(step => step.strategy !== 'BEFORE')]
    const data = { ownerType: props.ownerType, ownerId: String(props.ownerId), targetType: 'BUTTON', targetKey: bindingKey.value, eventCode: eventCode.value, inheritanceMode: 'REPLACE', enabled: true, steps, expectedRevision: binding.value?.revision }
    if (binding.value?.id) await uiEventBindingApi.update(binding.value.id, data); else await uiEventBindingApi.create(data)
    await loadBinding(); emit('changed'); ElMessage.success('Excel 事件已保存，请发布所属表单或列表')
  } catch (e) { showRequestError(e, e.message || '保存 Excel 绑定失败') }
  finally { saving.value = false }
}
</script>
<style scoped>.excel-binding { margin: 12px 0; } .el-form { margin-top: 16px; } .el-select { min-width: 240px; }</style>
