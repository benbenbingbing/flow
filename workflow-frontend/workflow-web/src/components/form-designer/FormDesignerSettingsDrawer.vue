<template>
  <el-drawer
    v-model="drawerVisible"
    title="表单设置"
    direction="rtl"
    size="min(1180px, 94vw)"
    append-to-body
    destroy-on-close
    class="form-settings-drawer"
  >
    <el-tabs v-model="currentTab" class="form-settings-tabs">
      <el-tab-pane label="基本与布局" name="basic">
        <div class="form-settings-pane form-settings-pane--narrow">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="这里维护整张表单的身份和布局；字段自身的组件、规则和数据关系请在节点属性中配置。"
          />
          <el-form label-width="110px" class="form-settings-form">
            <el-form-item label="表单名称" required>
              <el-input v-model="form.formName" placeholder="请输入表单名称" />
            </el-form-item>
            <el-form-item label="表单标识" required>
              <el-input
                v-model="form.formKey"
                placeholder="请输入稳定表单标识"
                :disabled="isEdit"
              />
              <div class="form-tip">
                创建后作为发布、流程快照和运行时引用标识，不允许修改。
              </div>
            </el-form-item>
            <el-form-item label="表单说明">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="说明表单的业务用途"
              />
            </el-form-item>
            <el-form-item label="标签位置">
              <template #label>
                <ConfigHelpLabel
                  label="标签位置"
                  content="PC 端按所选方式展示。移动端选择“左对齐”或“右对齐”时，标签统一左对齐；选择“顶部”时仍显示在上方。富文本、图片和文件保持移动端的上下布局。"
                />
              </template>
              <el-segmented v-model="formLabelPosition" :options="FORM_LABEL_POSITION_OPTIONS" />
            </el-form-item>
            <el-form-item label="标签宽度">
              <el-input-number v-model="viewConfig.labelWidth" :min="60" :max="240" :disabled="formLabelPosition === 'top'" />
              <span class="field-unit">px</span>
            </el-form-item>
            <el-form-item label="长标签显示">
              <el-select v-model="formLabelOverflow" class="label-overflow-select">
                <el-option v-for="(label, value) in FORM_LABEL_OVERFLOW_LABELS" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
            <el-form-item label="组件尺寸">
              <el-segmented v-model="formComponentSize" :options="formComponentSizeOptions" />
            </el-form-item>
            <el-form-item label="默认表单">
              <el-switch v-model="form.isDefault" />
              <span class="field-help">
                设为默认后，未指定 formKey 的运行入口优先使用本表单。
              </span>
            </el-form-item>
            <el-form-item label="表单状态">
              <el-switch
                v-model="form.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane name="actions">
        <template #label>
          <span>按钮与操作</span>
          <el-tag
            v-if="customFormButtonCount"
            size="small"
            type="success"
            effect="plain"
            class="settings-tab-tag"
          >
            {{ customFormButtonCount }}
          </el-tag>
        </template>
        <div class="form-settings-pane">
          <FormButtonConfigPanel
            v-model="viewConfig.actionBar"
            :entity-code="entityInfo.entityCode || ''"
            :entity-fields="entityFields"
            :related-contents="relatedContents"
            :form-id="form.id || ''"
            :active-release-id="form.activeReleaseId || ''"
            :event-binding-revision="eventBindingRevision"
            :persistence-revision="formActionPersistenceRevision"
            :persisted-button-keys="persistedFormButtonKeys"
            :nodes="formFields"
            :create-action-slot="createActionSlotForButton"
            :allow-action-slot-create="!isCustomRendererMode"
            :system-entity="isSystemEntity"
            @changed="handleEventBindingsChanged"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="表单事件" name="events">
        <div class="form-settings-pane">
          <EventBindingEditor
            owner-type="FORM"
            :owner-id="form.id || ''"
            :lifecycle-form="form"
            @lifecycle-saved="onFormLifecycleSaved"
            owner-label="表单"
            :field-options="eventFieldOptions"
            @changed="handleEventBindingsChanged"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane name="input-parameters">
        <template #label>
          <span>输入参数</span>
          <el-tag
            v-if="inputParameterCount"
            size="small"
            type="success"
            effect="plain"
            class="settings-tab-tag"
          >
            {{ inputParameterCount }}
          </el-tag>
        </template>
        <div class="form-settings-pane">
          <PageInputParameterSettings v-model="viewConfig" :fields="formFields" />
        </div>
      </el-tab-pane>

      <el-tab-pane name="related-content">
        <template #label>
          <span>关联内容</span>
          <el-tag v-if="relatedContentCount" size="small" type="success" effect="plain" class="settings-tab-tag">
            {{ relatedContentCount }}
          </el-tag>
        </template>
        <div class="form-settings-pane">
          <slot name="related-content" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EventBindingEditor from '@/components/ui-config/EventBindingEditor.vue'
import FormButtonConfigPanel from '@/components/FormButtonConfigPanel.vue'
import PageInputParameterSettings from '@/components/page-parameters/PageInputParameterSettings.vue'
import { FORM_DESIGNER_CONTEXT_KEY } from './context'
import { FORM_LABEL_POSITION_OPTIONS, resolveFormLabelPosition } from '@/shared/form-layout'
import {
  FORM_COMPONENT_SIZE_LABELS,
  FORM_LABEL_OVERFLOW_LABELS,
  resolveFormComponentSize,
  resolveFormLabelOverflow
} from '@flow/workflow-core/config-runtime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  activeTab: { type: String, default: 'basic' },
  relatedContentCount: { type: Number, default: 0 }
})

const emit = defineEmits([
  'update:modelValue',
  'update:activeTab'
])
const context = inject(FORM_DESIGNER_CONTEXT_KEY)

if (!context) {
  throw new Error('FormDesignerSettingsDrawer requires form designer context')
}

const {
  form,
  isCustomRendererMode,
  viewConfig,
  isEdit,
  isSystemEntity,
  customFormButtonCount,
  entityInfo,
  relatedContents,
  entityFields,
  formFields,
  eventFieldOptions,
  formActionPersistenceRevision,
  persistedFormButtonKeys,
  createActionSlotForButton,
  onFormLifecycleSaved,
  onEventBindingsChanged
} = context

const eventBindingRevision = ref(0)

/** 任一事件入口保存或删除后，同步刷新按钮引用缓存和发布差异。 */
function handleEventBindingsChanged() {
  eventBindingRevision.value += 1
  return onEventBindingsChanged()
}

const inputParameterCount = computed(() =>
  Object.keys(
    viewConfig.value?.inputParameterSchema?.properties || {}
  ).length
)
// 不在打开抽屉时回写默认值，历史表单只有主动修改标签位置才产生新配置。
const formLabelPosition = computed({
  get: () => resolveFormLabelPosition(form.value, viewConfig.value),
  set: value => { viewConfig.value.labelPosition = value }
})
const formLabelOverflow = computed({
  get: () => resolveFormLabelOverflow(viewConfig.value),
  set: value => { viewConfig.value.labelOverflow = value }
})
const formComponentSizeOptions = Object.entries(FORM_COMPONENT_SIZE_LABELS)
  .map(([value, label]) => ({ value, label }))
// 缺省档位只用于展示，旧表单打开设置时不会产生配置变更。
const formComponentSize = computed({
  get: () => resolveFormComponentSize(viewConfig.value),
  set: value => { viewConfig.value.componentSize = value }
})
const drawerVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
const currentTab = computed({
  get: () => props.activeTab,
  set: value => emit('update:activeTab', value)
})
</script>

<style scoped>
.label-overflow-select { width: 220px; max-width: 100%; }

:global(.form-settings-drawer .el-drawer__body) {
  min-height: 0;
  padding-top: 8px;
  overflow: hidden;
}

.form-settings-tabs {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.form-settings-tabs > :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.form-settings-pane {
  min-width: 0;
  padding: 4px 4px 24px;
}

.form-settings-pane--narrow {
  max-width: 760px;
}

.form-settings-form {
  margin-top: 18px;
}

.form-settings-form :deep(.el-form-item__content) {
  min-width: 0;
}

.form-settings-form :deep(.el-input),
.form-settings-form :deep(.el-textarea) {
  max-width: 640px;
}

.form-tip,
.field-unit,
.field-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.form-tip {
  width: 100%;
  margin-top: 4px;
  line-height: 1.5;
}

.field-unit,
.field-help {
  margin-left: 8px;
}

.settings-tab-tag {
  margin-left: 4px;
  vertical-align: middle;
}

.behavior-entry {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 0;
}

.behavior-entry h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.behavior-entry p {
  max-width: 720px;
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .behavior-entry {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
