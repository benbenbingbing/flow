<template>
  <div class="custom-renderer-workspace">
    <aside class="custom-renderer-config">
      <div class="workspace-panel-title">
        <span>自定义表单配置</span>
        <el-tag type="info" effect="plain">
          默认布局保留 {{ inactiveNodeCount }} 个节点
        </el-tag>
      </div>

      <el-scrollbar class="custom-config-scrollbar">
        <el-form label-position="top" class="custom-config-form">
          <el-form-item label="自定义组件" required>
            <ExtensionCapabilityPicker
              :model-value="customComponent"
              placeholder="请选择自定义表单组件"
              capability-type="UI_FORM"
              :context-params="formExtensionContext"
              :local-options="customFormOptions"
              :current-option="selectedCustomFormCatalogOption"
              @update:model-value="$emit('update:customComponent', $event)"
            />
          </el-form-item>

          <template v-if="customComponent">
            <el-form-item label="组件版本">
              <div class="version-tags">
                <el-tag>实现 v{{ customComponentVersion || 1 }}</el-tag>
                <el-tag type="info">
                  快照 v{{ customComponentSnapshotVersion || 1 }}
                </el-tag>
              </div>
            </el-form-item>

            <el-form-item v-if="selectedCustomFormSchema.length" label="组件参数">
              <el-button @click="$emit('open-form-extension-config')">
                <el-icon><Setting /></el-icon>
                配置参数
              </el-button>
            </el-form-item>
          </template>
        </el-form>

        <div class="custom-config-section">
          <div class="custom-config-heading">公共配置</div>
          <button
            type="button"
            class="config-entry"
            @click="$emit('open-form-settings', 'basic')"
          >
            <span>基本设置</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <button
            type="button"
            class="config-entry"
            @click="$emit('open-form-settings', 'actions')"
          >
            <span>按钮与操作</span>
            <span class="config-entry-meta">{{ customFormButtonCount }} 个自定义按钮</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <button
            type="button"
            class="config-entry"
            @click="$emit('open-form-settings', 'data-events')"
          >
            <span>数据与事件</span>
            <span class="config-entry-meta">{{ formDataSourceBindingCount }} 项数据源绑定</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>

        <div class="custom-config-section custom-extension-actions">
          <div class="custom-config-heading">扩展目录</div>
          <el-button @click="$emit('open-extension-management')">
            管理表单扩展
          </el-button>
          <el-button text type="primary" @click="$emit('refresh-extension-catalog')">
            刷新目录
          </el-button>
        </div>
      </el-scrollbar>
    </aside>

    <section class="custom-renderer-preview">
      <div class="workspace-panel-title preview-panel-title">
        <span>自定义表单预览</span>
        <el-segmented
          :model-value="previewMode"
          :options="previewModeOptions"
          @change="$emit('update:previewMode', $event)"
        />
      </div>

      <div class="inline-preview-stage">
        <el-empty
          v-if="!customComponent"
          description="请选择自定义表单组件"
        />
        <div v-else-if="!customFormAvailable" class="unavailable-preview">
          <el-alert
            title="当前前端未注册该自定义表单组件，暂时无法预览"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
        <div v-else class="inline-preview-content">
          <FormPreviewLinkage
            :form="previewForm"
            :mode="previewMode"
            :readonly="previewMode === 'view' || systemEntity"
            :entity-code="entityInfo.entityCode || ''"
            :entity-definition="entityInfo"
            :entity-fields="entityFields"
            :form-actions="previewActions"
            height="100%"
            @form-action="$emit('preview-action', $event)"
          />
          <div v-if="previewFooterActions.length" class="inline-preview-footer">
            <FormActionBar
              :actions="previewFooterActions"
              @action="$emit('preview-action', $event)"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowRight, Setting } from '@element-plus/icons-vue'
import ExtensionCapabilityPicker from '@/components/ExtensionCapabilityPicker.vue'
import FormActionBar from '@/components/FormActionBar.vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'

const props = defineProps({
  customComponent: { type: String, default: '' },
  customComponentVersion: { type: [String, Number], default: null },
  customComponentSnapshotVersion: { type: [String, Number], default: null },
  customFormOptions: { type: Array, default: () => [] },
  selectedCustomFormCatalogOption: { type: Object, default: null },
  selectedCustomFormSchema: { type: Array, default: () => [] },
  customFormAvailable: { type: Boolean, default: false },
  inactiveNodeCount: { type: Number, default: 0 },
  customFormButtonCount: { type: Number, default: 0 },
  formDataSourceBindingCount: { type: Number, default: 0 },
  previewForm: { type: Object, required: true },
  previewMode: { type: String, required: true },
  previewModeOptions: { type: Array, default: () => [] },
  previewActions: { type: Array, default: () => [] },
  previewFooterActions: { type: Array, default: () => [] },
  entityInfo: { type: Object, default: () => ({}) },
  entityFields: { type: Array, default: () => [] },
  systemEntity: { type: Boolean, default: false }
})

defineEmits([
  'update:customComponent',
  'update:previewMode',
  'open-form-settings',
  'open-form-extension-config',
  'open-extension-management',
  'refresh-extension-catalog',
  'preview-action'
])

const formExtensionContext = computed(() => ({
  entityCode: props.entityInfo?.entityCode || ''
}))
</script>

<style scoped>
.custom-renderer-workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  overflow: hidden;
  background: #f0f2f5;
}

.custom-renderer-config,
.custom-renderer-preview {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.custom-renderer-config {
  border-right: 1px solid #dcdfe6;
}

.workspace-panel-title {
  min-height: 52px;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e4e7ed;
  font-size: 14px;
  font-weight: 500;
}

.custom-config-scrollbar {
  flex: 1;
  min-height: 0;
}

.custom-config-form,
.custom-config-section {
  padding: 18px 20px;
}

.custom-config-section {
  border-top: 1px solid #ebeef5;
}

.custom-config-heading {
  margin-bottom: 12px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.version-tags,
.custom-extension-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.custom-extension-actions .custom-config-heading {
  flex: 0 0 100%;
}

.config-entry {
  width: 100%;
  min-height: 42px;
  padding: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  border: 0;
  border-bottom: 1px solid #ebeef5;
  color: #303133;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.config-entry:hover {
  color: var(--el-color-primary);
}

.config-entry-meta {
  color: #909399;
  font-size: 12px;
}

.preview-panel-title {
  background: #fff;
}

.inline-preview-stage {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow: hidden;
  background: #f0f2f5;
}

.inline-preview-stage > .el-empty,
.unavailable-preview,
.inline-preview-content {
  width: 100%;
  height: 100%;
  background: #fff;
}

.unavailable-preview {
  padding: 20px;
}

.inline-preview-content {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.inline-preview-content :deep(.linkage-form-preview) {
  flex: 1;
  min-height: 0;
  padding: 24px;
}

.inline-preview-footer {
  flex: 0 0 auto;
  padding: 12px 20px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

@media (max-width: 900px) {
  .custom-renderer-workspace {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(300px, 42%) minmax(360px, 1fr);
    overflow: auto;
  }

  .custom-renderer-config {
    border-right: 0;
    border-bottom: 1px solid #dcdfe6;
  }

  .workspace-panel-title {
    flex-wrap: wrap;
  }
}
</style>
