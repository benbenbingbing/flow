<template>
  <div class="related-content-page-settings">
    <SettingsSection :title="editor.config.target.contentType === 'LIST' ? '子列表配置' : '子表单配置'" :default-expanded="true" primary>
      <el-form label-width="110px" size="small">
        <el-form-item label="当前目标"><span>{{ editor.config.target.contentName || '请先在关联配置中选择目标页面' }}</span></el-form-item>
        <template v-if="editor.config.target.contentType === 'LIST'">
          <el-form-item v-for="option in switches" :key="option.key" :label="option.label">
            <el-switch :model-value="listOptions[option.key] !== false" @update:model-value="updateList(option.key, $event)" />
          </el-form-item>
          <el-form-item label="每页条数"><el-input-number :model-value="listOptions.pageSize || 20" :min="1" :max="200" @update:model-value="updateList('pageSize', $event)" /></el-form-item>
          <el-form-item label="列表最大高度"><el-input-number :model-value="listOptions.maxHeight" :min="120" :max="2000" :step="20" @update:model-value="updateList('maxHeight', $event)" /><span>px</span></el-form-item>
        </template>
        <el-form-item v-if="editor.config.target.contentType === 'FORM'" label="显示边框">
          <el-switch :model-value="editor.config.presentation.showBorder !== false" @update:model-value="editor.config.presentation.showBorder = $event" />
        </el-form-item>
        <el-form-item label="加载方式"><el-radio-group v-model="editor.config.presentation.loadMode"><el-radio value="ON_DEMAND">按需加载</el-radio><el-radio value="IMMEDIATE">立即加载</el-radio></el-radio-group></el-form-item>
        <el-form-item label="空状态提示"><el-input v-model="editor.config.presentation.emptyText" placeholder="未找到关联数据" maxlength="500" /></el-form-item>
      </el-form>
      <PageParameterMappingEditor v-model="editor.config.parameterMappings" :schema="parameterSchema" :source-fields="sourceFields" />
    </SettingsSection>
    <SettingsSection title="内置按钮外观" :default-expanded="true">
      <BuiltInButtonAppearanceEditor
        :model-value="editor.config.presentation.actionBar || {}"
        :actions="buttonActions"
        @update:model-value="editor.config.presentation.actionBar = $event"
      />
    </SettingsSection>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import BuiltInButtonAppearanceEditor from '@/components/BuiltInButtonAppearanceEditor.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import PageParameterMappingEditor from '@/components/page-parameters/PageParameterMappingEditor.vue'
const props = defineProps({ editor: { type: Object, required: true }, parameterSchema: { type: Object, default: () => ({}) }, sourceFields: { type: Array, default: () => [] } })
const switches = [{ key: 'showSearch', label: '显示查询' }, { key: 'showPagination', label: '显示分页' }, { key: 'showToolbar', label: '显示工具栏' }, { key: 'showRowActions', label: '显示操作列' }]
// 配置入口随页面形态展示实际按钮；运行时是否可用仍只由原有业务能力决定。
const buttonActions = computed(() => {
  const config = props.editor.config
  const inline = ['INLINE', 'TAB', 'ROW_EXPAND'].includes(config.presentation.position || 'INLINE')
  const defaults = { buttonType: 'primary', buttonAppearance: 'LINK' }
  const result = []
  if (config.target.contentType === 'FORM') {
    result.push({ key: 'save', label: '保存子表单', sort: 10, ...defaults })
  } else if (config.target.contentType === 'LIST') {
    if (inline) result.push({ key: 'refresh', label: '刷新', sort: 0, ...defaults })
    if (config.actions?.includes('LINK')) {
      result.push({ key: 'link', label: '选择并建立关联', sort: 20, ...defaults })
    }
  }
  if (!inline) {
    const title = config.presentation.title || config.name || config.target.contentName || '关联内容'
    result.push({ key: 'open', label: `${config.presentation.position === 'PAGE' ? '打开' : '查看'}${title}`, scope: 'trigger', ...defaults })
  }
  return result
})
const listOptions = computed(() => props.editor.config.presentation.listOptions || {})
// 子列表展示选项只覆盖当前引用实例，权限仍由目标发布配置及关联动作共同校验。
function updateList(key, value) {
  const next = { ...listOptions.value }
  if (value == null) delete next[key]
  else next[key] = value
  props.editor.config.presentation.listOptions = next
}
</script>
