<template>
  <section class="mobile-sublist"><h3>{{ field.fieldLabel || field.fieldName || '关联记录' }}</h3>
    <p v-if="missing.length" class="sublist-message">请先填写主表参数：{{ missing.map(item => item.targetFieldName || item.targetField).join('、') }}</p>
    <p v-else-if="!entityCode || !listKey" class="sublist-message">子列表未绑定已发布列表，请在电脑端检查配置。</p>
    <MobileRecordList v-else :identity="identity" :load-page="loadPage" :services="services" />
    <p v-if="config.showToolbar || config.showRowActions" class="sublist-message">关联记录的新增、编辑等操作请在电脑端完成。</p>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { normalizeSubListDisplayConfig, normalizeSubListParameterContract, resolveSubListParameterContract } from '@flow/workflow-core/sub-list'
import MobileRecordList from './MobileRecordList.vue'
const props = defineProps({ field: Object, context: Object, services: Object })
const config = computed(() => normalizeSubListDisplayConfig({ ...safeParseConfig(props.field.componentProps).subListConfig, ...props.field.subListConfig }))
const resolution = computed(() => resolveSubListParameterContract(normalizeSubListParameterContract(config.value.parameterContract), props.context))
const missing = computed(() => resolution.value.missingRequired)
const entityCode = computed(() => config.value.targetEntityCode || props.field.refEntityCode)
const listKey = computed(() => config.value.listKey || props.field.refListKey)
const queryContext = computed(() => ({ ...config.value.context, ...props.context.listContext, sourceEntityCode: props.context.entityCode, sourceRecordId: props.context.recordId || props.context.record?.id, relationKey: config.value.relationKey, parameters: { ...config.value.context?.parameters, ...props.context.listContext?.parameters, ...resolution.value.parameters } }))
const identity = computed(() => JSON.stringify([entityCode.value, listKey.value, config.value.listReleaseId, resolution.value.queryFilters, queryContext.value]))
function loadPage(pagination) {
  if (missing.value.length || !props.services?.loadPublishedList) throw new Error('子列表参数或查询能力尚未就绪')
  return props.services.loadPublishedList({ entityCode: entityCode.value, listKey: listKey.value, scene: 'EMBEDDED', releaseId: config.value.listReleaseId, releaseVersion: config.value.listReleaseVersion, releaseResolutionToken: props.context.releaseResolutionToken, context: queryContext.value, filters: resolution.value.queryFilters, ...pagination })
}
</script>
<style scoped>.mobile-sublist h3 { font-size: 15px; margin: 12px 0; }.sublist-message { color: var(--flow-mobile-muted); font-size: 12px; line-height: 1.6; }</style>
