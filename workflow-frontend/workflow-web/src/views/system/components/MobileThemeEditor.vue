<template>
  <div class="mobile-theme-editor">
    <div class="theme-fields">
      <div class="theme-presets" role="group" aria-label="主题预设"><button v-for="preset in MOBILE_THEME_PRESETS" :key="preset.id" type="button" :disabled="disabled" :aria-pressed="draft.preset === preset.id" @click="selectPreset(preset)"><span :style="{ backgroundColor: preset.primaryColor }" />{{ preset.name }}<span v-if="draft.preset === preset.id" class="preset-check">✓</span></button></div>
      <p class="theme-hint">{{ draft.preset === 'custom' ? '当前为自定义配色' : '选择预设后，可继续调整颜色' }}。修改只在右侧预览，保存后刷新移动端生效。</p>
      <div v-for="field in colorFields" :key="field.key" class="theme-color-row">
        <label :for="`mobile-theme-${field.key}`">{{ field.label }}</label>
        <input type="color" :aria-label="`${field.label}选择器`" :value="pickerColor(field.key)" :disabled="disabled" @input="updateColor(field.key, $event.target.value)" />
        <el-input :id="`mobile-theme-${field.key}`" :model-value="draft[field.key]" :aria-label="field.label" :disabled="disabled" maxlength="7" placeholder="#RRGGBB" @update:model-value="value => updateColor(field.key, value)" />
      </div>
      <p class="theme-hint">支持自定义主色；页面背景和内容背景请选择浅色。成功、警告、驳回保留独立状态色。</p>
      <p v-if="validationError" class="theme-error" role="alert">{{ validationError }}</p>
      <el-button v-if="!readonly" type="primary" :disabled="disabled || Boolean(validationError)" :loading="saving" @click="$emit('save', normalizeMobileTheme(draft))">保存主题</el-button>
    </div>
    <div class="theme-preview" :style="previewVariables" aria-label="移动端主题预览">
      <div class="preview-header"><strong>待办 <small>18</small></strong><span class="preview-avatar">用</span></div>
      <div class="preview-search">搜索流程名称或事项</div>
      <div class="preview-item"><div><strong>项目验收申请</strong><span class="preview-pending">待处理</span></div><p>全流程验收 · 部门审批</p><footer>张三 <span>09/21 10:30</span></footer></div>
      <div class="preview-tabs"><span>基本信息</span><span>流程进度</span><span>审批历史</span></div>
      <div class="preview-action">提交审批</div>
      <div class="preview-nav"><span>待办</span><span>已办</span><span>我发起的</span><span>知会</span></div>
    </div>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { DEFAULT_MOBILE_THEME, MOBILE_THEME_PRESETS, mobileThemeVariables, normalizeMobileTheme } from '@flow/workflow-core/mobile-theme'
const props = defineProps({ modelValue: Object, disabled: Boolean, readonly: Boolean, saving: Boolean })
defineEmits(['save'])
const draft = ref({ ...DEFAULT_MOBILE_THEME }), previewTheme = ref({ ...DEFAULT_MOBILE_THEME })
const colorFields = [{ key: 'primaryColor', label: '主题主色' }, { key: 'backgroundColor', label: '页面背景' }, { key: 'surfaceColor', label: '内容背景' }]
const validationError = computed(() => { try { normalizeMobileTheme(draft.value); return '' } catch (error) { return error.message } })
const previewVariables = computed(() => mobileThemeVariables(previewTheme.value))
watch(() => props.modelValue, value => { draft.value = normalizeMobileTheme(value || DEFAULT_MOBILE_THEME) }, { immediate: true, deep: true })
// 输入十六进制的中间状态不改变预览，也不能提交；合法后立即更新局部预览，不影响 PC 文档主题。
watch(draft, value => { try { previewTheme.value = normalizeMobileTheme(value) } catch { /* 保留最近一次合法预览。 */ } }, { immediate: true, deep: true })
function selectPreset(preset) { draft.value = { version: 1, preset: preset.id, primaryColor: preset.primaryColor, backgroundColor: preset.backgroundColor, surfaceColor: preset.surfaceColor } }
function updateColor(key, value) { draft.value = { ...draft.value, [key]: value, preset: 'custom' } }
function pickerColor(key) { return /^#[0-9a-f]{6}$/i.test(draft.value[key]) ? draft.value[key] : previewTheme.value[key] }
</script>
<style scoped>
.mobile-theme-editor { display: grid; grid-template-columns: minmax(280px, 1fr) 300px; gap: 32px; width: 100%; align-items: start; }.theme-presets { display: flex; gap: 8px; flex-wrap: wrap; }.theme-presets button { display: flex; align-items: center; gap: 7px; padding: 9px 12px; border: 1px solid #dcdfe6; border-radius: 7px; color: #303133; background: white; cursor: pointer; font: inherit; font-size: 13px; }.theme-presets button[aria-pressed="true"] { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }.theme-presets button > span:first-child { width: 14px; height: 14px; border-radius: 50%; }.theme-presets button:disabled { cursor: not-allowed; opacity: .6; }.preset-check { color: var(--el-color-primary); }.theme-hint { color: #909399; font-size: 12px; line-height: 1.8; margin: 12px 0 18px; }.theme-color-row { display: flex; align-items: center; gap: 10px; margin: 12px 0; }.theme-color-row label { flex: 0 0 64px; color: #606266; font-size: 14px; }.theme-color-row input[type=color] { width: 40px; height: 34px; padding: 2px; border: 1px solid #dcdfe6; border-radius: 5px; background: white; cursor: pointer; }.theme-color-row .el-input { max-width: 160px; }.theme-error { color: #c45656; font-size: 13px; }
.theme-preview { padding: 16px; border: 1px solid var(--flow-mobile-border); border-radius: 14px; background: var(--flow-mobile-background); color: var(--flow-mobile-text); font-size: 12px; }.preview-header { display: flex; justify-content: space-between; align-items: center; }.preview-header strong { font-size: 18px; }.preview-header small { font-size: 12px; color: var(--flow-mobile-accent-text); background: var(--flow-mobile-accent-soft); padding: 2px 6px; border-radius: 4px; }.preview-avatar { display: grid; place-items: center; width: 30px; height: 30px; color: var(--flow-mobile-accent-text); background: var(--flow-mobile-accent-soft); border-radius: 50%; }.preview-search { margin: 16px 0; padding: 10px; border-radius: 7px; background: var(--flow-mobile-inset); color: var(--flow-mobile-muted); }.preview-item { padding: 14px 10px; background: var(--flow-mobile-surface); border-bottom: 1px solid var(--flow-mobile-border); }.preview-item > div { display: flex; justify-content: space-between; align-items: center; }.preview-item strong { font-size: 14px; }.preview-pending { color: #a76a1a; background: #fff5df; border-radius: 4px; padding: 2px 5px; font-size: 11px; }.preview-item p, .preview-item footer { color: var(--flow-mobile-muted); }.preview-item footer { display: flex; justify-content: space-between; margin-top: 18px; }.preview-tabs { display: flex; justify-content: space-between; margin: 20px 0; color: var(--flow-mobile-muted); }.preview-tabs > span:first-child { color: var(--flow-mobile-accent-text); border-bottom: 2px solid var(--flow-mobile-accent); padding-bottom: 8px; }.preview-action { text-align: center; padding: 11px; border-radius: 7px; color: var(--flow-mobile-on-accent); background: var(--flow-mobile-accent); border: 1px solid var(--flow-mobile-accent-text); }.preview-nav { display: flex; justify-content: space-between; margin-top: 24px; padding-top: 12px; border-top: 1px solid var(--flow-mobile-border); color: var(--flow-mobile-muted); }.preview-nav > span:first-child { color: var(--flow-mobile-accent-text); }
@media (max-width: 850px) { .mobile-theme-editor { grid-template-columns: 1fr; }.theme-preview { max-width: 300px; } }
</style>
