<template>
  <section class="interface-service-usage">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="此处只读展示接口服务在事件执行链中的引用。前往配置会定位到配置对象或字段/按钮；具体事件仍需在设计器中选择，并需具备目标对象权限。"
    />

    <div class="usage-toolbar">
      <div class="usage-filters">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索服务、配置对象、事件或操作"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="filters.serviceId"
          clearable
          filterable
          placeholder="请先选择一个接口服务"
          aria-label="接口服务"
        >
          <el-option
            v-for="service in services"
            :key="service.id"
            :label="`${service.sourceName} (${service.sourceCode})`"
            :value="String(service.id)"
          />
        </el-select>
        <el-select v-model="filters.ownerType" aria-label="配置层级">
          <el-option
            v-for="option in interfaceServiceReferenceOwnerOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select v-model="filters.lifecycleStatus" aria-label="发布状态">
          <el-option
            v-for="option in interfaceServiceReferenceLifecycleOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-segmented
          v-model="filters.referenceState"
          :options="interfaceServiceReferenceStateOptions"
          aria-label="引用状态"
        />
      </div>
      <el-button
        :loading="loading"
        :disabled="!filters.serviceId"
        title="刷新所选服务的事件使用情况"
        @click="load"
      >
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>

    <div v-loading="loading" class="usage-content">
      <el-empty
        v-if="!filters.serviceId"
        description="请先选择一个接口服务，再查看它的事件引用"
      />
      <el-result
        v-else-if="selectedServiceFailed"
        icon="warning"
        title="事件引用加载失败"
        sub-title="无法判断该服务是否存在引用，请重新加载后再进行下线或删除判断。"
      >
        <template #extra>
          <el-button type="primary" @click="load">重新加载</el-button>
        </template>
      </el-result>
      <template v-else>
        <el-empty
          v-if="!loading && filteredGroups.length === 0"
          description="没有符合当前筛选条件的服务引用"
        />

        <article
          v-for="group in filteredGroups"
          :key="group.serviceId"
          class="service-usage-card"
        >
        <header class="service-card-heading">
          <div>
            <div class="service-title-line">
              <span class="service-name">{{ group.service.sourceName }}</span>
              <el-tag effect="plain" size="small">
                {{ sourceTypeLabel(group.service.sourceType) }}
              </el-tag>
              <el-tag
                :type="group.service.enabled === false ? 'info' : 'success'"
                size="small"
              >
                {{ group.service.enabled === false ? '服务停用' : '服务启用' }}
              </el-tag>
            </div>
            <div class="service-code">{{ group.service.sourceCode }}</div>
          </div>
          <el-tag type="primary" effect="plain">
            {{ group.visibleReferenceCount }} 条事件引用
          </el-tag>
        </header>

        <el-empty
          v-if="group.references.length === 0"
          :image-size="54"
          description="该服务尚未被事件执行链引用"
        />
        <el-table
          v-else
          :data="group.references"
          :row-key="referenceRowKey"
          border
        >
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="effective-chain-detail">
                <div class="detail-title">最终生效链</div>
                <el-alert
                  v-if="row.contexts.length === 0"
                  type="info"
                  :closable="false"
                  title="暂无可展开的发布上下文；顶层发布与生效摘要仍可用于排查。"
                />
                <section
                  v-for="(context, contextIndex) in row.contexts"
                  :key="context.releaseId || `${context.configType}:${context.configId}:${contextIndex}`"
                  class="effective-context"
                >
                  <div class="context-heading">
                    <span class="primary-text">{{ contextName(context) }}</span>
                    <el-tag
                      :type="lifecycleStatusTagType(context.publicationStatus)"
                      size="small"
                    >
                      {{ lifecycleStatusLabel(context.publicationStatus) }}
                    </el-tag>
                    <el-tag
                      :type="effectiveContextTagType(context)"
                      size="small"
                      effect="plain"
                    >
                      {{ effectiveContextLabel(context) }}
                    </el-tag>
                  </div>
                  <div
                    v-if="isEffectiveChainAvailable(context)"
                    class="effective-chain"
                  >
                    <template
                      v-for="(item, itemIndex) in buildEffectiveChainItems(context, row.eventCode)"
                      :key="`${item.kind}:${item.bindingId || 'platform'}:${item.stepIndex ?? itemIndex}`"
                    >
                      <span v-if="itemIndex" class="chain-arrow">→</span>
                      <span class="chain-step">
                        <el-tag :type="chainItemTagType(item)" effect="plain">
                          {{ item.label }}
                        </el-tag>
                        <span v-if="item.kind !== 'PLATFORM'" class="secondary-text">
                          {{ stepStrategyLabel(item.stepStrategy, row.eventCode) }}
                          · {{ inheritanceSourceLabel(item.inheritanceSource || item.ownerType) }}
                          <template v-if="item.operationCode">· {{ item.operationCode }}</template>
                        </span>
                      </span>
                    </template>
                  </div>
                  <div v-else class="secondary-text chain-unavailable">
                    {{ effectiveChainUnavailableReason(context) }}
                  </div>
                  <div
                    v-if="context.effectiveReason || context.publicationReason"
                    class="secondary-text context-reason"
                  >
                    {{ context.effectiveReason || context.publicationReason }}
                  </div>
                </section>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="配置位置" min-width="220">
            <template #default="{ row }">
              <div class="primary-text">{{ row.ownerName || row.ownerId }}</div>
              <div class="tag-line">
                <el-tag size="small" effect="plain">
                  {{ ownerTypeLabel(row.ownerType) }}
                </el-tag>
                <span class="secondary-text">{{ targetTypeLabel(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="触发事件" min-width="170">
            <template #default="{ row }">
              <div class="primary-text">{{ eventLabel(row.eventCode) }}</div>
              <div class="secondary-text">{{ row.eventCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="接口操作" min-width="200">
            <template #default="{ row }">
              <div class="primary-text">
                {{ row.operationName || row.operationCode || '-' }}
              </div>
              <div v-if="row.operationCode" class="secondary-text">
                {{ row.operationCode }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="执行步骤" min-width="170">
            <template #default="{ row }">
              <div>{{ row.stepName || `步骤 ${row.stepIndex + 1}` }}</div>
              <div class="secondary-text">
                {{ stepStrategyLabel(row.stepStrategy, row.eventCode) }}
                <template v-if="row.stepOrder != null"> · 顺序 {{ row.stepOrder }}</template>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="发布状态" width="150">
            <template #default="{ row }">
              <el-tag
                :type="lifecycleStatusTagType(row.lifecycleStatus)"
                size="small"
              >
                {{ lifecycleStatusLabel(row.lifecycleStatus) }}
              </el-tag>
              <div
                v-if="row.publicationReason"
                class="secondary-text effective-reason"
                :title="row.publicationReason"
              >
                {{ row.publicationReason }}
              </div>
              <div v-if="row.activeReleaseVersion != null" class="secondary-text">
                生效版本 v{{ row.activeReleaseVersion }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="继承与生效" min-width="210">
            <template #default="{ row }">
              <el-tag effect="plain" size="small">
                {{ inheritanceModeLabel(row.inheritanceMode, row) }}
              </el-tag>
              <div
                class="effective-state"
              >
                <el-tag :type="effectiveStateTagType(row)" size="small">
                  {{ effectiveStateLabel(row) }}
                </el-tag>
              </div>
              <div
                v-if="row.effectiveReason"
                class="secondary-text effective-contexts"
                :title="row.effectiveReason"
              >
                {{ row.effectiveReason }}
              </div>
              <div
                v-if="formatEffectiveContexts(row.effectiveContexts)"
                class="secondary-text effective-contexts"
                :title="formatEffectiveContexts(row.effectiveContexts)"
              >
                有效上下文：{{ formatEffectiveContexts(row.effectiveContexts) }}
              </div>
              <div
                v-if="formatEffectiveContexts(row.contexts)"
                class="secondary-text effective-contexts"
                :title="formatEffectiveContexts(row.contexts)"
              >
                相关上下文：{{ formatEffectiveContexts(row.contexts) }}
              </div>
              <div
                v-if="row.inheritanceSource"
                class="secondary-text effective-contexts"
                :title="inheritanceSourceLabel(row.inheritanceSource)"
              >
                继承来源：{{ inheritanceSourceLabel(row.inheritanceSource) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="116" fixed="right" align="center">
            <template #default="{ row }">
              <el-tooltip :content="configurationActionHint(row)" placement="top">
                <span>
                  <el-button
                    link
                    type="primary"
                    :disabled="!canOpenReferenceConfiguration(row)"
                    @click="emit('configure', row)"
                  >
                    前往配置
                  </el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        </article>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { uiDataSourceApi } from '@/api/uiConfig'
import { sourceTypeOptions } from './interfaceServiceModel'
import {
  buildInterfaceServiceReferenceRoute,
  buildEffectiveChainItems,
  effectiveChainUnavailableReason,
  effectiveStateLabel,
  effectiveStateTagType,
  effectiveStatusLabel,
  eventLabel,
  filterInterfaceServiceReferenceGroups,
  formatEffectiveContexts,
  groupInterfaceServiceReferences,
  inheritanceModeLabel,
  inheritanceSourceLabel,
  isEffectiveChainAvailable,
  interfaceServiceReferenceLifecycleOptions,
  interfaceServiceReferenceOwnerOptions,
  interfaceServiceReferenceStateOptions,
  lifecycleStatusLabel,
  lifecycleStatusTagType,
  omitInterfaceServiceReferenceCache,
  ownerTypeLabel,
  stepStrategyLabel,
  targetTypeLabel
} from './interfaceServiceUsageModel'

const props = defineProps({
  services: { type: Array, default: () => [] },
  canConfigureEntityEvents: { type: Boolean, default: false }
})
const emit = defineEmits(['configure'])

const loading = ref(false)
const referencesByService = ref({})
const failedServices = ref([])
const filters = reactive({
  keyword: '',
  serviceId: '',
  ownerType: '',
  lifecycleStatus: '',
  referenceState: 'ALL'
})
let loadSequence = 0

const selectedService = computed(() => props.services.find(service =>
  String(service.id) === String(filters.serviceId)) || null)
const groups = computed(() => groupInterfaceServiceReferences(
  selectedService.value ? [selectedService.value] : [],
  referencesByService.value
))
const filteredGroups = computed(() =>
  filterInterfaceServiceReferenceGroups(groups.value, filters))
const selectedServiceFailed = computed(() => failedServices.value.some(service =>
  String(service.id) === String(filters.serviceId)))

/**
 * 引用查询包含发布快照解析，只在用户选定一个服务后发起单次请求，
 * 避免打开页签时按服务数量执行全目录扫描。
 */
async function load() {
  const sequence = ++loadSequence
  const service = selectedService.value
  if (!service) {
    failedServices.value = []
    loading.value = false
    return
  }

  loading.value = true
  failedServices.value = []
  try {
    const references = await uiDataSourceApi.references(service.id)
    if (sequence !== loadSequence) return
    referencesByService.value = {
      ...referencesByService.value,
      [String(service.id)]: references
    }
  } catch (error) {
    if (sequence !== loadSequence) return
    referencesByService.value = omitInterfaceServiceReferenceCache(
      referencesByService.value,
      service.id
    )
    failedServices.value = [service]
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function sourceTypeLabel(type) {
  return sourceTypeOptions.find(option => option.value === type)?.label || type || '-'
}

function referenceRowKey(row) {
  return `${row.serviceId}:${row.referenceId}`
}

function contextName(context) {
  const base = context.configName || context.configKey || context.configId || '未知配置'
  const version = context.releaseVersion == null ? '' : ` · v${context.releaseVersion}`
  return `${ownerTypeLabel(context.configType)} · ${base}${version}`
}

function effectiveContextLabel(context) {
  return effectiveStatusLabel(context.effectiveStatus)
}

function effectiveContextTagType(context) {
  return effectiveStateTagType({
    bindingEnabled: true,
    effectiveStatus: context.effectiveStatus
  })
}

function chainItemTagType(item) {
  if (item.kind === 'PLATFORM') return 'success'
  if (item.kind === 'REPLACE') return 'warning'
  return 'info'
}

function canOpenReferenceConfiguration(reference) {
  if (!buildInterfaceServiceReferenceRoute(reference)) return false
  return reference.ownerType !== 'ENTITY' || props.canConfigureEntityEvents
}

function configurationActionHint(reference) {
  if (!buildInterfaceServiceReferenceRoute(reference)) {
    return '引用缺少可定位的配置对象'
  }
  if (reference.ownerType === 'ENTITY' && !props.canConfigureEntityEvents) {
    return '缺少实体定义查看或维护权限，无法进入实体默认事件配置'
  }
  return `打开对应目标的事件配置；进入后请选择“${eventLabel(reference.eventCode)}”事件`
}

watch(() => filters.serviceId, load)
watch(() => props.services, services => {
  if (filters.serviceId && !services.some(service =>
    String(service.id) === String(filters.serviceId))) {
    filters.serviceId = ''
  }
})
defineExpose({ reload: load })
</script>

<style scoped>
.interface-service-usage {
  display: grid;
  gap: 14px;
}

.usage-toolbar,
.service-card-heading,
.service-title-line,
.tag-line {
  display: flex;
  align-items: center;
}

.usage-toolbar,
.service-card-heading {
  justify-content: space-between;
  gap: 12px;
}

.usage-filters {
  display: grid;
  grid-template-columns: minmax(250px, 1fr) minmax(200px, 260px) 150px 170px auto;
  gap: 10px;
  min-width: 0;
}

.usage-content {
  display: grid;
  min-height: 240px;
  gap: 14px;
}

.service-usage-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.service-card-heading {
  padding: 13px 16px;
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.service-title-line,
.tag-line {
  flex-wrap: wrap;
  gap: 7px;
}

.service-name,
.primary-text {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.service-code,
.secondary-text {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.tag-line .secondary-text {
  margin-top: 0;
}

.effective-reason {
  max-width: 128px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.effective-contexts {
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.effective-state {
  margin-top: 6px;
}

.effective-chain-detail {
  display: grid;
  gap: 10px;
  padding: 8px 18px 14px 58px;
}

.detail-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.effective-context {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.context-heading,
.effective-chain,
.chain-step {
  display: flex;
  align-items: center;
}

.context-heading,
.effective-chain {
  flex-wrap: wrap;
  gap: 7px;
}

.chain-step {
  flex-direction: column;
  align-items: flex-start;
}

.chain-step .secondary-text {
  margin-top: 4px;
}

.chain-arrow {
  color: var(--el-text-color-placeholder);
}

.context-reason {
  margin-top: 0;
}

.chain-unavailable {
  margin-top: 0;
  color: var(--el-text-color-placeholder);
}

@media (max-width: 1180px) {
  .usage-toolbar {
    align-items: flex-start;
  }

  .usage-filters {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (max-width: 760px) {
  .usage-filters {
    grid-template-columns: 1fr;
  }
}
</style>
