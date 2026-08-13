<template>
  <div class="linkage-condition-rule">
    <div class="rule-header">
      <div>
        <div class="rule-title">{{ title }}</div>
        <div class="rule-description">{{ description }}</div>
      </div>
      <el-switch
        :model-value="enabled"
        @update:model-value="$emit('update:enabled', $event)"
      />
    </div>

    <template v-if="enabled">
      <el-alert
        v-if="parseWarning"
        type="warning"
        :closable="false"
        show-icon
        class="parse-warning"
      >
        <template #title>原条件表达式暂时无法转换为可视化条件组</template>
        <div>{{ parseWarning }}</div>
        <el-button type="warning" link @click="$emit('reset-group')">
          清空并改用条件组
        </el-button>
      </el-alert>

      <template v-else>
        <el-alert
          title="条件组可选择“全部满足”或“任一满足”，组内可以继续添加子条件组。"
          type="info"
          :closable="false"
          show-icon
          class="group-tip"
        />
        <FlowConditionGroupEditor
          :group="root"
          :entity-fields="fields"
          :include-approval-property="false"
          :operator-options="operatorOptions"
          @change="$emit('change')"
        />
      </template>
    </template>
  </div>
</template>

<script setup>
import FlowConditionGroupEditor from '@/components/FlowConditionGroupEditor.vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  enabled: { type: Boolean, default: false },
  root: { type: Object, required: true },
  fields: { type: Array, default: () => [] },
  parseWarning: { type: String, default: '' }
})

defineEmits(['update:enabled', 'change', 'reset-group'])

const operatorOptions = [
  { label: '等于 (==)', value: '==' },
  { label: '不等于 (!=)', value: '!=' },
  { label: '大于 (>)', value: '>' },
  { label: '小于 (<)', value: '<' },
  { label: '大于等于 (>=)', value: '>=' },
  { label: '小于等于 (<=)', value: '<=' },
  { label: '包含', value: 'contains' },
  { label: '为空', value: 'empty' },
  { label: '不为空', value: 'notEmpty' }
]
</script>

<style scoped>
.linkage-condition-rule {
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.rule-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.rule-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.rule-description {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.group-tip,
.parse-warning {
  margin: 14px 0 12px;
}

@media (max-width: 720px) {
  .linkage-condition-rule {
    padding: 10px 8px;
  }

  .rule-header {
    gap: 8px;
  }

  .rule-description {
    font-size: 12px;
  }

  .group-tip,
  .parse-warning {
    margin: 10px 0 8px;
  }
}
</style>
