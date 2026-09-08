<template>
  <section class="entity-default-event-panel" aria-labelledby="entity-default-event-title">
    <div class="event-panel-card">
      <header class="event-panel-heading">
        <div>
          <h2 id="entity-default-event-title">实体默认事件</h2>
          <p v-if="entityName">{{ entityName }}</p>
        </div>
        <el-tag type="info" effect="plain">实体级</el-tag>
      </header>

      <el-alert
        title="实体默认事件可作为该实体表单和列表的上级事件链"
        description="同名事件默认继承并追加这里的步骤；如需差异化处理，请在具体表单或列表中选择“替换上级”或“禁用自定义”。是否替代平台默认处理由步骤的执行位置决定。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        title="保存后需重新发布相关表单和列表"
        description="已发布页面继续使用原有事件快照，不会因实体默认事件变更而自动更新。"
        type="warning"
        :closable="false"
        show-icon
      />

      <EventBindingEditor
        owner-type="ENTITY"
        :owner-id="String(entityId || '')"
        target-type="OWNER"
        target-key=""
        :field-options="fieldOptions"
        title="实体默认事件执行链"
      />
    </div>
  </section>
</template>

<script setup>
import EventBindingEditor from '@/components/ui-config/EventBindingEditor.vue'

defineProps({
  entityId: { type: [String, Number], required: true },
  entityName: { type: String, default: '' },
  fieldOptions: { type: Array, default: () => [] }
})
</script>

<style scoped>
.entity-default-event-panel {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
}

.event-panel-card {
  min-width: 0;
  padding: 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.event-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.event-panel-heading h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.event-panel-heading p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.event-panel-card :deep(.el-alert) {
  margin-bottom: 12px;
}

.event-panel-card :deep(.event-binding-editor) {
  margin-top: 18px;
}
</style>
