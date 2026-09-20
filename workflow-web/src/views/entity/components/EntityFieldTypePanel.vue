<template>
  <div class="field-types-panel" :class="{ 'is-collapsed': fieldTypesPreference.state.value }">
    <button
      type="button"
      class="panel-title field-types-toggle"
      :aria-expanded="!fieldTypesPreference.state.value"
      aria-controls="entity-field-type-list"
      :aria-label="fieldTypesPreference.state.value ? '展开字段类型' : '收起字段类型'"
      :title="fieldTypesPreference.state.error || (fieldTypesPreference.state.value ? '展开字段类型' : '收起字段类型')"
      :disabled="fieldTypesPreference.state.loading && !fieldTypesPreference.state.loaded"
      @click="fieldTypesPreference.toggle()"
    >
      <span>字段类型</span>
      <el-icon><ArrowRight v-if="fieldTypesPreference.state.value" /><ArrowLeft v-else /></el-icon>
    </button>
    <div v-if="!fieldTypesPreference.state.value" class="field-types-preference-actions">
      <span v-if="fieldTypesPreference.state.saving" role="status">保存中…</span>
      <el-button v-if="fieldTypesPreference.state.error" link type="warning" size="small" @click="fieldTypesPreference.refresh()">重试读取</el-button>
      <el-button v-else-if="fieldTypesPreference.state.overridden" link size="small" :disabled="fieldTypesPreference.state.saving" @click="fieldTypesPreference.reset()">恢复默认</el-button>
    </div>
    <div v-show="!fieldTypesPreference.state.value" id="entity-field-type-list" class="field-type-list">
      <div
        v-for="type in fieldTypes"
        :key="type.value"
        class="field-type-item"
        draggable="true"
        @dragstart="emit('drag-start', type)"
        @click="emit('add-field', type)"
      >
        <el-icon><component :is="type.icon" /></el-icon>
        <span>{{ type.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onActivated, onMounted } from 'vue'
import { useFieldTypesPreferenceStore } from '@/stores/fieldTypesPreference'
import { ENTITY_DESIGN_FIELD_TYPES } from '@/shared/entity-design'

const emit = defineEmits(['add-field', 'drag-start'])
const fieldTypes = ENTITY_DESIGN_FIELD_TYPES.filter(type => !['RADIO', 'CHECKBOX'].includes(type.value))
const fieldTypesPreference = useFieldTypesPreferenceStore()

// 重新进入实体页面时读取最新默认值；共享保存队列阻止旧响应覆盖正在保存的操作。
onMounted(() => { void fieldTypesPreference.refresh() })
onActivated(() => { void fieldTypesPreference.refresh() })
</script>

<style scoped>
/* ===== 左侧字段类型面板 ===== */
.field-types-panel {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.field-types-toggle {
  width: 100%;
  background: transparent;
  border-top: 0;
  border-left: 0;
  border-right: 0;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
}

.field-types-toggle:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 3px;
}

.field-types-toggle:disabled { cursor: wait; opacity: 0.6; }
.field-types-preference-actions { display: flex; justify-content: flex-end; gap: 8px; margin: -8px 0 10px; font-size: 12px; color: #909399; }

/* 收起后保留可聚焦的窄栏入口，释放的宽度由业务字段区域自然占用。 */
.field-types-panel.is-collapsed { width: 48px; padding: 16px 8px; }
.field-types-panel.is-collapsed .field-types-toggle { flex-direction: column-reverse; gap: 16px; border: 0; padding: 0; }
.field-types-panel.is-collapsed .field-types-toggle span { writing-mode: vertical-rl; letter-spacing: 4px; }

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f2f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.field-type-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  overflow-y: auto;
  padding-right: 4px;
}

.field-type-list::-webkit-scrollbar {
  width: 4px;
}

.field-type-list::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 2px;
}

.field-type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 8px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.25s ease;
}

.field-type-item:hover {
  background: #fff;
  border-color: #409eff;
  color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
  transform: translateY(-2px);
}

.field-type-item .el-icon {
  font-size: 22px;
  margin-bottom: 6px;
  transition: transform 0.2s;
}

.field-type-item:hover .el-icon {
  transform: scale(1.1);
}

.field-type-item span {
  font-size: 12px;
  font-weight: 500;
}

@media (max-width: 900px) {
  .field-types-panel { width: 100%; min-width: 0; max-height: 280px; }
  .field-types-panel.is-collapsed { width: 100%; padding: 16px; }
  .field-types-panel.is-collapsed .field-types-toggle { flex-direction: row; margin: 0; }
  .field-types-panel.is-collapsed .field-types-toggle span { writing-mode: horizontal-tb; letter-spacing: normal; }
}
</style>
