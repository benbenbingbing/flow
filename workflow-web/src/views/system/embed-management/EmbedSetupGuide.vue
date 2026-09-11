<template>
  <section class="setup-guide" :aria-busy="loading">
    <div class="guide-toolbar">
      <div>
        <h3>接入向导</h3>
        <p>按已保存配置检查 {{ view.name || view.viewKey }}，修改仍在原配置面板完成。</p>
      </div>
      <el-button :loading="loading" @click="refresh">重新检查</el-button>
    </div>

    <el-form label-position="top" class="application-form">
      <el-form-item label="接入应用">
        <el-select
          v-model="applicationId"
          filterable
          remote
          clearable
          :remote-method="searchApplications"
          :loading="applicationsLoading"
          placeholder="搜索并选择应用名称"
          style="width: 100%"
        >
          <el-option
            v-for="application in applicationOptions"
            :key="optionId(application)"
            :value="optionId(application)"
            :label="applicationName(application)"
          >
            <span>{{ applicationName(application) }}</span>
            <small class="option-status">{{ application.status === 'ACTIVE' ? '启用' : '未启用' }}</small>
          </el-option>
        </el-select>
      </el-form-item>
      <el-button v-if="hasMoreApplications" link :loading="applicationsLoading" @click="loadMoreApplications">
        加载更多应用
      </el-button>
      <p v-if="applicationsError" class="load-error" role="alert">{{ applicationsError }}</p>
    </el-form>

    <el-steps :active="activeStep" align-center class="guide-steps">
      <el-step
        v-for="(step, index) in steps"
        :key="step.key"
        :title="step.title"
        :status="step.state === 'complete' ? 'success' : index === activeStep ? 'process' : 'wait'"
        @click="activeStep = index"
      />
    </el-steps>

    <div class="step-grid">
      <article
        v-for="(step, index) in steps"
        :key="step.key"
        class="step-card"
        :class="{ 'is-current': activeStep === index }"
      >
        <button class="step-heading" type="button" :aria-current="activeStep === index ? 'step' : undefined" @click="activeStep = index">
          <strong>{{ index + 1 }}. {{ step.title }}</strong>
          <el-tag :type="statusType(step.state)" effect="plain" size="small">{{ step.key === 'check' && step.state === 'complete' ? '可开始联调' : stateLabel(step.state) }}</el-tag>
        </button>
        <p>{{ step.summary }}</p>
        <ul v-if="step.reasons.length" class="step-reasons">
          <li v-for="reason in step.reasons" :key="reason">{{ reason }}</li>
        </ul>
        <div class="step-actions">
          <el-button v-if="step.key === 'target' && canManage" link type="primary" @click="navigate('draft')">配置目标页面</el-button>
          <el-button
            v-if="step.key === 'grant' && canManage && matchingGrant?.status !== 'REVOKED'"
            link
            type="primary"
            :disabled="!applicationId"
            @click="navigate('grants')"
          >配置应用授权</el-button>
          <el-button
            v-if="step.key === 'binding' && canManageIdentity"
            link
            type="primary"
            :disabled="!applicationId || !providerId"
            @click="navigate('bindings')"
          >查看用户映射</el-button>
          <router-link v-if="step.key === 'check'" class="manual-link" to="/manual/embed-integration">查看接入手册</router-link>
        </div>
      </article>
    </div>
    <el-alert
      :type="readyToIntegrate ? 'success' : 'info'"
      :title="readyToIntegrate ? '可开始联调' : '完成上方检查后，再从宿主系统开始联调'"
      description="检查只覆盖当前配置、应用授权与已有测试用户映射；实际页面访问、用户权限和数据范围仍需通过宿主系统验证。"
      show-icon
      :closable="false"
    />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { embedManagementApi } from '@/api/system/embedManagement'
import { describeEmbedManagementError } from './embedManagementModel'

const props = defineProps({
  view: { type: Object, required: true },
  canManage: { type: Boolean, default: false },
  canManageIdentity: { type: Boolean, default: false }
})
const emit = defineEmits(['navigate'])
const activeStep = ref(0)
const applicationId = ref('')
const applicationOptions = ref([])
const selectedApplication = ref(null)
const applicationsLoading = ref(false)
const applicationsError = ref('')
const applicationKeyword = ref('')
const applicationPage = ref(1)
const hasMoreApplications = ref(false)
const loading = ref(false)
const draft = ref(null)
const draftError = ref('')
const validation = ref(null)
const validationError = ref('')
const grants = ref([])
const grantsError = ref('')
const provider = ref(null)
const providerError = ref('')
const bindings = ref([])
const bindingsError = ref('')
const bindingsTruncated = ref(false)
const identityLoading = ref(false)
const now = ref(Date.now())
let refreshSequence = 0
let applicationSequence = 0
let identitySequence = 0
let clockTimer

const matchingGrant = computed(() => grants.value.find(item =>
  String(item.applicationId) === applicationId.value
) || null)
const providerId = computed(() => String(matchingGrant.value?.identityProviderId || ''))
const readyToIntegrate = computed(() => prerequisiteSteps.value.every(step => step.state === 'complete'))

function optionId(option) {
  return String(option?.id || option?.applicationId || option?.identityProviderId || '')
}

function applicationName(application) {
  return application?.name || application?.applicationName || optionId(application)
}

function pageRows(page) {
  return Array.isArray(page) ? page : page?.records || page?.list || page?.items || []
}

function hasMore(page, pageNum, rows) {
  if (Array.isArray(page)) return false
  const total = Number(page?.total)
  return Number.isFinite(total) ? pageNum * 100 < total : rows.length === 100
}

function usableTime(value, comparison) {
  if (!value) return true
  // Grant/Binding 管理响应使用 UTC LocalDateTime，Application 则使用带时区 Instant。
  // 对无时区文本补 Z，避免浏览器按本地时区解释而提前或延后判定生效/过期。
  const text = String(value)
  const timestamp = new Date(
    /[zZ]|[+-]\d\d:\d\d$/.test(text) ? text : `${text}Z`
  ).getTime()
  return Number.isFinite(timestamp) && comparison(timestamp, now.value)
}

function available(record) {
  return record?.status === 'ACTIVE'
    && usableTime(record.effectiveAt, (value, current) => value <= current)
    && usableTime(record.expiresAt, (value, current) => value > current)
}

function availabilityReasons(record, label) {
  if (!record) return [`未找到${label}`]
  const reasons = []
  if (record.status !== 'ACTIVE') reasons.push(`${label}尚未启用`)
  if (!usableTime(record.effectiveAt, (value, current) => value <= current)) reasons.push(`${label}尚未生效或生效时间无效`)
  if (!usableTime(record.expiresAt, (value, current) => value > current)) reasons.push(`${label}已过期或过期时间无效`)
  return reasons
}

function stepResult(key, title, summary, reasons = [], unknown = false, pending = false) {
  return { key, title, summary, reasons, state: pending ? 'loading' : unknown ? 'unknown' : reasons.length ? 'incomplete' : 'complete' }
}

// 完成状态只使用服务端已保存值与后端实时校验；选择步骤和打开编辑器都不改变结果。
const prerequisiteSteps = computed(() => {
  const targetReasons = []
  if (validation.value && validation.value.viewStatus !== 'ACTIVE') {
    targetReasons.push('嵌入配置尚未启用')
  }
  if (validation.value?.valid === false) {
    const violations = Array.isArray(validation.value.violations)
      ? validation.value.violations.map(item => item?.message).filter(Boolean)
      : []
    targetReasons.push(...(violations.length ? violations : ['当前配置未通过服务端校验']))
  }
  const targetUnknown = validationError.value
    || (!loading.value && (!validation.value || !validation.value.viewStatus))
  const targetStep = stepResult('target', '目标页面', '确认已保存的页面目标和入口。',
    targetUnknown ? [validationError.value || '当前配置校验结果不可用'] : targetReasons,
    Boolean(targetUnknown), loading.value)

  const grantReasons = []
  const grantUnknown = draftError.value || grantsError.value
    || (applicationId.value && applicationsError.value) || providerError.value
  if (!applicationId.value) grantReasons.push('请选择需要接入的应用')
  else {
    grantReasons.push(...availabilityReasons(selectedApplication.value, '所选应用'))
    if (selectedApplication.value && !selectedApplication.value.embedLaunchReady) {
      grantReasons.push('接入应用缺少有效凭据')
    }
    grantReasons.push(...availabilityReasons(matchingGrant.value, '当前应用授权'))
    if (matchingGrant.value) {
      if (!matchingGrant.value.allowedOrigins?.length) grantReasons.push('授权尚未配置允许嵌入的 Origin')
      const modeCapability = { LIST: 'LIST_QUERY', CREATE: 'RECORD_CREATE', VIEW: 'RECORD_VIEW' }
      const usableEntry = (draft.value?.entryModes || []).some(mode =>
        (draft.value?.capabilities || []).includes(modeCapability[mode])
          && (matchingGrant.value.capabilityCeiling || []).includes(modeCapability[mode]))
      if (!usableEntry) grantReasons.push('应用授权与页面入口没有可用的能力交集')
      grantReasons.push(...availabilityReasons(provider.value, '授权关联的身份提供方'))
      if (provider.value?.type === 'TRUSTED_EXTERNAL_ID' && !matchingGrant.value.trustedSubjectAssertion) {
        grantReasons.push('当前身份提供方需要在授权中允许可信外部用户 ID')
      }
    }
  }
  const grantStep = stepResult('grant', '应用授权', '检查当前应用的精确授权、来源与身份提供方。',
    grantUnknown ? [grantUnknown] : grantReasons, Boolean(grantUnknown), loading.value || applicationsLoading.value || identityLoading.value)

  let bindingReasons = []
  let bindingUnknown = false
  if (!props.canManageIdentity) {
    bindingReasons = ['当前账号没有身份管理权限，请身份管理员检查测试用户映射']
    bindingUnknown = true
  } else if (!applicationId.value || !providerId.value) {
    bindingReasons = ['先选择应用并配置关联身份提供方的授权']
  } else if (bindingsError.value) {
    bindingReasons = [bindingsError.value]
    bindingUnknown = true
  } else if (!bindings.value.some(available)) {
    bindingReasons = [bindingsTruncated.value
      ? '当前页未找到生效映射，更多结果需在用户映射面板检查'
      : '当前应用与身份提供方下没有已生效且未过期的测试用户映射']
    bindingUnknown = bindingsTruncated.value
  } else if (!bindings.value.some(item => available(item) && item.flowUserReady === true)) {
    bindingReasons = [bindingsTruncated.value
      ? '当前页的生效映射均关联不可用 Flow 用户，更多结果需在用户映射面板检查'
      : '生效映射关联的 Flow 用户已停用、已删除或需要重置密码']
    bindingUnknown = bindingsTruncated.value
  }
  return [targetStep, grantStep, stepResult('binding', '测试用户映射', '检查当前应用与身份提供方下至少一条有效映射。',
    bindingReasons, bindingUnknown, props.canManageIdentity && (loading.value || identityLoading.value))]
})

const steps = computed(() => {
  const prerequisites = prerequisiteSteps.value
  const incomplete = prerequisites.filter(step => step.state !== 'complete')
  return [...prerequisites, stepResult('check', '接入检查', readyToIntegrate.value
    ? '配置检查已通过，可开始联调。' : '汇总前置检查，再到宿主系统验证实际访问。',
  incomplete.map(step => `${step.title}：${stateLabel(step.state)}`),
  incomplete.some(step => step.state === 'unknown'), incomplete.some(step => step.state === 'loading'))]
})

function stateLabel(state) {
  return { complete: '已完成', incomplete: '未完成', unknown: '无法检查', loading: '检查中' }[state]
}

function statusType(state) {
  return { complete: 'success', incomplete: 'warning', unknown: 'info', loading: 'info' }[state]
}

function navigate(tab) {
  emit('navigate', tab, { applicationId: applicationId.value, providerId: providerId.value })
}

/** 刷新只读取管理投影，不发起 Launch，也不替用户保存配置。 */
async function refresh() {
  const sequence = ++refreshSequence
  loading.value = true
  draftError.value = ''
  validation.value = null
  validationError.value = ''
  grantsError.value = ''
  const results = await Promise.allSettled([
    embedManagementApi.views.draft(props.view.id),
    embedManagementApi.views.validation(props.view.id),
    embedManagementApi.grants.list(props.view.id),
    // 重新检查已有选择时按标识查回最新投影，不能沿用搜索缓存中的启用状态。
    loadApplications(applicationId.value || '', 1)
  ])
  if (sequence !== refreshSequence) return
  if (results[0].status === 'fulfilled') draft.value = results[0].value?.draft || {}
  else draftError.value = `目标配置读取失败：${describeEmbedManagementError(results[0].reason)}`
  if (results[1].status === 'fulfilled') validation.value = results[1].value
  else validationError.value = `当前配置校验失败：${describeEmbedManagementError(results[1].reason)}`
  if (results[2].status === 'fulfilled') grants.value = results[2].value || []
  else {
    grants.value = []
    grantsError.value = `应用授权读取失败：${describeEmbedManagementError(results[2].reason)}`
  }
  if (results[3].status === 'rejected') applicationsError.value = describeEmbedManagementError(results[3].reason)
  now.value = Date.now()
  loading.value = false
}

/** 应用按名称分页检索，保留选中项，避免搜索结果变化丢失当前检查范围。 */
async function loadApplications(keyword = '', pageNum = 1) {
  const sequence = ++applicationSequence
  applicationsLoading.value = true
  applicationsError.value = ''
  applicationKeyword.value = keyword
  try {
    const result = await embedManagementApi.options.applications({ keyword, status: undefined, pageNum, pageSize: 100 })
    if (sequence !== applicationSequence) return
    const rows = pageRows(result)
    const options = pageNum === 1 ? rows : [...applicationOptions.value, ...rows]
    const selected = options.find(item => optionId(item) === applicationId.value)
    if (selected) selectedApplication.value = selected
    if (applicationId.value && keyword === applicationId.value && !selected) {
      applicationsError.value = '无法核实所选应用的最新状态，请重新搜索并选择应用'
    }
    if (!selected && selectedApplication.value) options.unshift(selectedApplication.value)
    applicationOptions.value = [...new Map(options.map(item => [optionId(item), item])).values()]
    applicationPage.value = pageNum
    hasMoreApplications.value = hasMore(result, pageNum, rows)
  } catch (error) {
    if (sequence === applicationSequence) applicationsError.value = `应用列表读取失败：${describeEmbedManagementError(error)}`
  } finally {
    if (sequence === applicationSequence) applicationsLoading.value = false
  }
}

function searchApplications(keyword) { return loadApplications(keyword, 1) }
function loadMoreApplications() { return loadApplications(applicationKeyword.value, applicationPage.value + 1) }

/** 分页找到授权关联的 Provider，不能把“第一页没有”当作资源不存在。 */
async function findProvider(id, sequence) {
  for (let pageNum = 1; pageNum <= 100; pageNum += 1) {
    const result = await embedManagementApi.options.identityProviders({ keyword: id, status: undefined, pageNum, pageSize: 100 })
    if (sequence !== identitySequence) return null
    const rows = pageRows(result)
    const found = rows.find(item => optionId(item) === id)
    if (found) return found
    if (!hasMore(result, pageNum, rows)) return null
  }
  throw new Error('身份提供方结果过多，请在授权面板核对关联配置')
}

async function loadIdentity() {
  const sequence = ++identitySequence
  provider.value = null
  providerError.value = ''
  bindings.value = []
  bindingsError.value = ''
  bindingsTruncated.value = false
  identityLoading.value = false
  if (!applicationId.value || !providerId.value) return
  const selectedProviderId = providerId.value
  const selectedApplicationId = applicationId.value
  identityLoading.value = true
  const results = await Promise.allSettled([
    findProvider(selectedProviderId, sequence),
    props.canManageIdentity ? embedManagementApi.bindings.page({
      applicationId: selectedApplicationId, identityProviderId: selectedProviderId,
      status: 'ACTIVE', pageNum: 1, pageSize: 100
    }) : Promise.resolve(null)
  ])
  if (sequence !== identitySequence) return
  if (results[0].status === 'fulfilled') provider.value = results[0].value
  else providerError.value = `身份提供方无法检查：${describeEmbedManagementError(results[0].reason)}`
  if (results[1].status === 'fulfilled') {
    // 即使接口已按范围筛选，也只用当前应用和 Provider 的结果判断映射就绪。
    const rows = pageRows(results[1].value)
    bindings.value = rows.filter(item => String(item.applicationId) === selectedApplicationId
      && String(item.identityProviderId) === selectedProviderId)
    bindingsTruncated.value = hasMore(results[1].value, 1, rows)
  } else bindingsError.value = `测试用户映射无法检查：${describeEmbedManagementError(results[1].reason)}`
  identityLoading.value = false
}

watch(applicationId, value => {
  selectedApplication.value = applicationOptions.value.find(item => optionId(item) === value) || null
})
watch([matchingGrant, () => props.canManageIdentity], loadIdentity)
watch(() => props.view.id, () => {
  applicationId.value = ''
  activeStep.value = 0
  draft.value = null
  validation.value = null
  grants.value = []
  refresh()
}, { immediate: true })
onMounted(() => { clockTimer = globalThis.setInterval(() => { now.value = Date.now() }, 30_000) })
onBeforeUnmount(() => {
  refreshSequence += 1
  applicationSequence += 1
  identitySequence += 1
  globalThis.clearInterval(clockTimer)
})
defineExpose({ refresh })
</script>

<style scoped>
.setup-guide { min-width: 0; }
.guide-toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.guide-toolbar h3 { margin: 0 0 8px; }
.guide-toolbar p, .step-card p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.65; }
.application-form { max-width: 560px; margin-top: 20px; }
.application-form :deep(.el-form-item) { margin-bottom: 6px; }
.option-status { margin-left: 16px; color: var(--el-text-color-secondary); }
.load-error { color: var(--el-color-danger); font-size: 13px; }
.guide-steps { margin: 24px 0; }
.guide-steps :deep(.el-step) { cursor: pointer; }
.step-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.step-card { min-width: 0; padding: 16px; border: 1px solid var(--el-border-color); border-radius: 8px; }
.step-card.is-current { border-color: var(--el-color-primary); }
.step-heading { display: flex; width: 100%; justify-content: space-between; align-items: center; gap: 8px; padding: 0; margin-bottom: 10px; color: var(--el-text-color-primary); border: 0; background: none; text-align: left; cursor: pointer; }
.step-heading:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 4px; }
.step-reasons { margin: 10px 0 0; padding-left: 18px; color: var(--el-text-color-regular); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.step-actions { display: flex; align-items: center; gap: 12px; margin-top: 12px; min-height: 24px; }
.manual-link { color: var(--el-color-primary); font-size: 14px; text-decoration: none; }
@media (max-width: 720px) {
  .step-grid { grid-template-columns: 1fr; }
  .guide-toolbar { flex-wrap: wrap; }
  .guide-steps { overflow-x: auto; padding-bottom: 8px; }
  .guide-steps :deep(.el-step) { min-width: 100px; }
}
</style>
