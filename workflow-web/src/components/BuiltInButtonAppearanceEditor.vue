<template>
  <div class="built-in-button-appearance-editor">
    <p class="appearance-help">可修改按钮名称、图标和主题颜色；按钮功能、权限和校验规则保持不变。</p>
    <el-form label-width="100px" size="small">
      <el-form-item label="操作栏位置">
        <el-select :model-value="draft.position || 'DEFAULT'" @update:model-value="updateBar('position', $event)">
          <el-option v-for="item in positions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作栏对齐">
        <el-select :model-value="draft.align || 'DEFAULT'" @update:model-value="updateBar('align', $event)">
          <el-option v-for="item in aligns" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="按钮间距">
        <el-input-number :model-value="draft.gap" :min="0" :max="40" placeholder="默认" @update:model-value="updateBar('gap', $event)" /><span class="unit">px</span>
      </el-form-item>
    </el-form>
    <p v-if="actions.some(action => action.scope)" class="appearance-help">操作栏位置仅影响区域操作按钮；行内按钮跟随数据行，打开入口保留在原位置。</p>
    <el-collapse v-model="expandedActions">
      <el-collapse-item v-for="action in actions" :key="action.key" :name="action.key">
        <template #title>
          <strong>{{ action.label }}</strong><el-tag class="fixed-tag" size="small" type="info">内置动作</el-tag>
          <span class="appearance-edit-hint"><el-icon v-if="!expandedActions.includes(action.key)"><Edit /></el-icon>{{ expandedActions.includes(action.key) ? '收起外观设置' : '编辑名称和颜色' }}</span>
        </template>
        <el-form label-width="100px" size="small">
          <el-form-item label="执行功能"><span>{{ action.label }}（系统内置）</span></el-form-item>
          <el-form-item label="按钮名称"><el-input :model-value="overrides(action.key).label" :placeholder="action.label" maxlength="80" clearable @update:model-value="updateButton(action.key, 'label', $event)" /></el-form-item>
          <el-form-item label="按钮图标">
            <el-select :model-value="overrides(action.key).icon === '' ? '__NONE__' : (overrides(action.key).icon ?? '__DEFAULT__')" filterable @update:model-value="updateButton(action.key, 'icon', $event === '__DEFAULT__' ? undefined : ($event === '__NONE__' ? '' : $event))">
              <el-option label="跟随默认" value="__DEFAULT__" /><el-option label="无图标" value="__NONE__" />
              <el-option v-for="icon in BUILT_IN_BUTTON_ICONS" :key="icon" :label="icon" :value="icon" />
            </el-select>
          </el-form-item>
          <el-form-item label="按钮样式">
            <el-select :model-value="overrides(action.key).buttonAppearance ?? '__DEFAULT__'" @update:model-value="updateButton(action.key, 'buttonAppearance', $event === '__DEFAULT__' ? undefined : $event)">
              <el-option label="跟随默认" value="__DEFAULT__" />
              <el-option v-for="item in appearances" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="主题颜色">
            <el-select :model-value="overrides(action.key).buttonType ?? '__DEFAULT__'" @update:model-value="updateButton(action.key, 'buttonType', $event === '__DEFAULT__' ? undefined : $event)">
              <el-option label="跟随默认" value="__DEFAULT__" />
              <el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="按钮尺寸">
            <el-select :model-value="overrides(action.key).size ?? '__DEFAULT__'" @update:model-value="updateButton(action.key, 'size', $event === '__DEFAULT__' ? undefined : $event)">
              <el-option label="跟随默认" value="__DEFAULT__" /><el-option label="小" value="small" /><el-option label="标准" value="default" /><el-option label="大" value="large" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!action.scope" label="按钮排序"><el-input-number :model-value="overrides(action.key).sort" :min="0" :max="1000" placeholder="默认" @update:model-value="updateButton(action.key, 'sort', $event)" /></el-form-item>
        </el-form>
        <el-button size="small" @click="resetButton(action.key)">恢复此按钮默认外观</el-button>
      </el-collapse-item>
    </el-collapse>
    <div class="appearance-preview" aria-label="按钮外观预览" :style="builtInActionBarStyle(draft)">
      <BuiltInActionButton v-for="action in actions" :key="action.key" :action-key="action.key" :config="draft" :defaults="action" />
    </div>
    <el-button size="small" @click="emit('update:modelValue', {})">恢复全部默认外观</el-button>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import BuiltInActionButton from './BuiltInActionButton.vue'
import { BUILT_IN_BUTTON_ICONS, builtInActionBarStyle, normalizeBuiltInActionBar } from '@flow/workflow-core/builtin-button-appearance'
const props = defineProps({ modelValue: { type: Object, default: () => ({}) }, actions: { type: Array, required: true } })
const emit = defineEmits(['update:modelValue'])
// 默认折叠，通过标题栏的编辑入口展开；修改外观时保留用户当前的展开状态。
const expandedActions = ref([])
// 暂时切换展示方式时，保留未呈现入口的外观（例如从弹窗改为嵌入后再改回）。
const draft = computed(() => normalizeBuiltInActionBar(props.modelValue))
const positions = [{ value: 'DEFAULT', label: '跟随默认' }, { value: 'TOP', label: '顶部' }, { value: 'BOTTOM', label: '底部' }, { value: 'BOTH', label: '顶部和底部' }]
const aligns = [{ value: 'DEFAULT', label: '跟随默认' }, { value: 'LEFT', label: '左对齐' }, { value: 'CENTER', label: '居中' }, { value: 'RIGHT', label: '右对齐' }]
const appearances = [{ value: 'DEFAULT', label: '实心' }, { value: 'PLAIN', label: '朴素' }, { value: 'TEXT', label: '文字' }, { value: 'LINK', label: '链接' }, { value: 'ROUND', label: '圆角' }, { value: 'CIRCLE', label: '圆形图标' }]
const types = [{ value: 'default', label: '默认' }, { value: 'primary', label: '主要' }, { value: 'success', label: '成功' }, { value: 'warning', label: '警告' }, { value: 'danger', label: '危险' }, { value: 'info', label: '信息' }]
const overrides = key => draft.value.buttons?.[key] || {}
function updateBar(key, value) {
  const next = { ...draft.value }
  if (value == null) delete next[key]
  else next[key] = value
  emit('update:modelValue', next)
}
/** 留空恢复宿主默认值；空图标是显式无图标，需与未配置区分。 */
function updateButton(key, field, value) {
  const button = { ...overrides(key) }
  // 旧配置仍可回显；用户重新选择主题或样式时，清除对应的旧覆盖值，避免已移除的设置盖住新选择。
  if (field === 'buttonType') {
    delete button.textColor
    delete button.backgroundColor
    delete button.borderColor
  }
  if (field === 'buttonAppearance') delete button.borderRadius
  if (value == null || (field !== 'icon' && typeof value === 'string' && !value.trim())) delete button[field]
  else button[field] = value
  emit('update:modelValue', normalizeBuiltInActionBar({ ...draft.value, buttons: { ...draft.value.buttons, [key]: button } }))
}
function resetButton(key) {
  const buttons = { ...draft.value.buttons }
  delete buttons[key]
  emit('update:modelValue', normalizeBuiltInActionBar({ ...draft.value, buttons }))
}
</script>

<style scoped>
.appearance-help { margin: 0 0 12px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.fixed-tag, .unit { margin-left: 8px; }
.appearance-edit-hint { display: inline-flex; align-items: center; gap: 4px; margin-left: auto; padding: 4px 8px; border: 1px solid var(--el-color-primary-light-7); border-radius: 4px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 12px; font-weight: 500; line-height: 20px; white-space: nowrap; }
.appearance-preview { display: flex; flex-wrap: wrap; align-items: center; padding: 16px; margin: 12px 0; border: 1px dashed var(--el-border-color); border-radius: 4px; }
.appearance-preview :deep(.el-button + .el-button) { margin-left: 0; }
</style>
