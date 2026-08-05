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
            <el-form-item label="表单布局">
              <el-segmented v-model="form.layoutType" :options="formLayoutOptions" />
            </el-form-item>
            <el-form-item label="标签宽度">
              <el-input-number v-model="viewConfig.labelWidth" :min="60" :max="240" />
              <span class="field-unit">px</span>
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
            :form-id="form.id || ''"
            :nodes="formFields"
            :system-entity="isSystemEntity"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="数据与事件" name="data-events">
        <div class="form-settings-pane">
          <el-tabs v-model="activeBehaviorTab" class="form-behavior-tabs">
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
              <FormInputParameterEditor
                v-model="viewConfig.inputParameterSchema"
              />
            </el-tab-pane>
            <el-tab-pane name="data-source">
              <template #label>
                <span>表单数据源</span>
                <el-tag
                  v-if="formDataSourceBindingCount"
                  size="small"
                  type="success"
                  effect="plain"
                  class="settings-tab-tag"
                >
                  {{ formDataSourceBindingCount }}
                </el-tag>
              </template>
              <div class="behavior-entry">
                <div>
                  <h3>表单生命周期数据源</h3>
                  <p>
                    统一配置表单初始化、加载后处理和提交前处理，并保留输入输出映射、浏览器预校验与无副作用设置。
                  </p>
                </div>
                <el-button
                  type="primary"
                  :disabled="!form.id"
                  @click="openFormDataSourceConfig"
                >
                  配置数据源
                </el-button>
              </div>
              <el-alert
                v-if="!form.id"
                type="info"
                :closable="false"
                show-icon
                title="先保存表单草稿，再配置表单生命周期数据源。"
              />
            </el-tab-pane>
            <el-tab-pane label="表单事件" name="events">
              <EventBindingEditor
                owner-type="FORM"
                :owner-id="form.id || ''"
                owner-label="表单"
                :field-options="eventFieldOptions"
              />
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-tab-pane>

      <el-tab-pane label="渲染与扩展" name="rendering">
        <div class="form-settings-pane form-settings-pane--narrow">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="默认动态表单和自定义渲染使用同一份表单节点、数据源、按钮与事件配置。"
          />
          <el-form label-width="120px" class="form-settings-form">
            <el-form-item label="渲染方式">
              <el-segmented
                :model-value="form.customComponent ? 'CUSTOM' : 'DEFAULT'"
                :options="formRendererOptions"
                :disabled="isSystemEntity"
                @change="handleFormRendererModeChange"
              />
            </el-form-item>
            <el-form-item v-if="!isSystemEntity" label="自定义组件">
              <ExtensionCapabilityPicker
                v-model="form.customComponent"
                placeholder="请选择自定义表单组件"
                capability-type="UI_FORM"
                :context-params="formExtensionContext"
                :local-options="customFormOptions"
                :current-option="selectedCustomFormCatalogOption"
                style="width: 100%"
                @loaded="handleCustomFormCatalogLoaded"
              />
            </el-form-item>
            <el-form-item
              v-if="!isSystemEntity && form.customComponent"
              label="组件版本"
            >
              <el-tag>实现 v{{ form.customComponentVersion || 1 }}</el-tag>
              <el-tag style="margin-left: 8px">
                快照 v{{ form.customComponentSnapshotVersion || 1 }}
              </el-tag>
            </el-form-item>
            <el-form-item v-if="selectedCustomFormSchema.length" label="组件参数">
              <el-button @click="showFormExtensionConfig = true">
                配置参数
              </el-button>
            </el-form-item>
            <el-form-item v-if="!isSystemEntity" label="扩展目录">
              <el-button @click="openExtensionManagement">管理表单扩展</el-button>
              <el-button text type="primary" @click="refreshExtensionCatalog">
                刷新目录
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import EventBindingEditor from '@/components/ui-config/EventBindingEditor.vue'
import ExtensionCapabilityPicker from '@/components/ExtensionCapabilityPicker.vue'
import FormButtonConfigPanel from '@/components/FormButtonConfigPanel.vue'
import FormInputParameterEditor from './FormInputParameterEditor.vue'
import { FORM_DESIGNER_CONTEXT_KEY } from './context'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  activeTab: { type: String, default: 'basic' }
})

const emit = defineEmits(['update:modelValue', 'update:activeTab'])
const context = inject(FORM_DESIGNER_CONTEXT_KEY)

if (!context) {
  throw new Error('FormDesignerSettingsDrawer requires form designer context')
}

const {
  form,
  viewConfig,
  isEdit,
  isSystemEntity,
  customFormButtonCount,
  entityInfo,
  entityFields,
  formFields,
  formDataSourceBindingCount,
  eventFieldOptions,
  selectedCustomFormSchema,
  customFormOptions,
  selectedCustomFormCatalogOption,
  showFormExtensionConfig,
  openFormDataSourceConfig,
  handleFormRendererModeChange,
  handleCustomFormCatalogLoaded,
  openExtensionManagement,
  refreshExtensionCatalog
} = context

const activeBehaviorTab = ref('data-source')
const inputParameterCount = computed(() =>
  Object.keys(
    viewConfig.value?.inputParameterSchema?.properties || {}
  ).length
)
const formLayoutOptions = [
  { value: 'vertical', label: '垂直' },
  { value: 'horizontal', label: '水平' },
  { value: 'grid', label: '网格' }
]
const formRendererOptions = [
  { value: 'DEFAULT', label: '默认动态表单' },
  { value: 'CUSTOM', label: '自定义渲染' }
]
const formExtensionContext = computed(() => ({
  entityCode: entityInfo.value?.entityCode || ''
}))
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

.form-behavior-tabs :deep(.el-tabs__content) {
  overflow: visible;
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
