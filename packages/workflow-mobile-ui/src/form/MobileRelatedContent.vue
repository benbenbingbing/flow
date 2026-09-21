<template>
  <section class="mobile-related"><VanCell :title="title" is-link clickable @click="opened = !opened" />
    <div v-if="opened" class="related-body">
      <VanLoading v-if="loading" size="20" />
      <p v-else-if="error" class="related-error" role="alert">{{ error }}<VanButton size="small" @click="load">重试</VanButton></p>
      <VanEmpty v-else-if="!result || result.resolved.matchNone" description="暂无关联内容" :image-size="48" />
      <MobileRecordList v-else-if="result.resolved.targetContentType === 'LIST'" :identity="identity" :load-page="loadList" :services="services" />
      <MobileFormRenderer v-else-if="result.form && result.record" :form="result.form" :model-value="result.record" readonly mode="view" :context="targetContext" :services="services" :data-source-runtime="targetRuntime" />
      <VanEmpty v-else description="暂无关联记录" :image-size="48" />
      <p v-if="requiresWrite" class="related-message">此关联内容的编辑操作请在电脑端完成。</p>
    </div>
  </section>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { Cell as VanCell, Loading as VanLoading, Empty as VanEmpty, Button as VanButton } from 'vant'
import { buildRelatedContentResolveInput } from '@flow/workflow-core/related-content-runtime'
import { mapPageParameters } from '@flow/workflow-core/page-parameters'
import MobileFormRenderer from './MobileFormRenderer.vue'
import MobileRecordList from './MobileRecordList.vue'
const props = defineProps({ composition: Object, form: Object, context: Object, services: Object, dataSourceRuntime: Object })
const title = computed(() => props.composition.config?.presentation?.title || props.composition.compositionName || props.composition.name || '关联内容')
const requiresWrite = computed(() => (Array.isArray(props.composition.config?.actions) ? props.composition.config.actions : []).some(action => !['VIEW', 'SELECT'].includes(action)))
const opened = ref(false), result = ref(null), loading = ref(false), error = ref('')
let revision = 0
const input = computed(() => buildRelatedContentResolveInput({ ownerType: 'FORM', ownerId: props.form.id, releaseId: props.form.runtimeReleaseId, releaseVersion: props.form.runtimeReleaseVersion, compositionKey: props.composition.compositionKey, sourceRecordId: props.context.recordId || props.context.record?.id, traversalContextToken: props.context.viewCompositionTraversalToken, releaseResolutionToken: props.form.releaseResolutionToken || props.context.releaseResolutionToken }))
const parameters = computed(() => mapPageParameters(props.composition.config?.parameterMappings, { data: props.context.getFormData?.() || props.context.record, recordId: input.value.recordId, params: props.context.params || props.context.parameters }))
const identity = computed(() => JSON.stringify([input.value, parameters.value]))
const targetContext = computed(() => ({ mode: 'view', readonly: true, form: result.value.form, entityCode: result.value.resolved.targetEntityCode, record: result.value.record, recordId: result.value.record?.id, parameters: result.value.parameters, params: result.value.parameters, releaseResolutionToken: result.value.form?.releaseResolutionToken, viewCompositionTraversalToken: result.value.resolved.traversalContextToken, relatedDepth: Number(props.context.relatedDepth || 0) + 1 }))
const targetRuntime = computed(() => props.dataSourceRuntime?.withContext(() => ({ ...targetContext.value, record: result.value.record, context: targetContext.value })))
/** 关联查询由服务端解析关系和签名凭证；失败不降级为普通实体查询。 */
async function load() {
  const current = ++revision; result.value = null; error.value = ''
  if (!opened.value || !input.value.recordId) return
  loading.value = true
  try {
    if (Number(props.context.relatedDepth || 0) >= 5) throw new Error('关联内容层级较深，请在电脑端查看')
    if (!props.services?.resolveRelatedContent) throw new Error('关联内容服务不可用')
    const resolved = await props.services.resolveRelatedContent(input.value, parameters.value)
    if (current === revision) result.value = resolved
  } catch (cause) { if (current === revision) error.value = cause.message }
  finally { if (current === revision) loading.value = false }
}
function loadList(pagination) {
  const resolved = result.value.resolved
  return props.services.loadPublishedList({ entityCode: resolved.targetEntityCode, listKey: resolved.targetContentKey, releaseId: resolved.targetReleaseId, releaseVersion: resolved.targetReleaseVersion, viewCompositionContextToken: resolved.listContextToken, scene: 'EMBEDDED', context: { parameters: parameters.value }, ...pagination })
}
watch(() => [opened.value, identity.value], load)
onBeforeUnmount(() => revision++)
</script>
<style scoped>.mobile-related { border: 1px solid var(--flow-mobile-border); border-radius: 10px; overflow: hidden; margin: 12px 0; }.related-body { padding: 8px 12px; }.related-error { color: #bc4738; font-size: 12px; }.related-message { color: var(--flow-mobile-muted); font-size: 12px; }</style>
