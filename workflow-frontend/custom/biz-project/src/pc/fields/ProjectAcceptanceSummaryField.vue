<template>
  <section class="acceptance-summary-node">
    <div class="summary-heading">
      <div>
        <strong>{{ config.title || '扩展执行摘要' }}</strong>
        <span>自定义组件：{{ node?.nodeKey || field.fieldCode || '-' }}</span>
      </div>
      <el-tag :type="score >= 60 ? 'success' : 'warning'">
        {{ score >= 60 ? '达到验收线' : '待改进' }}
      </el-tag>
    </div>

    <el-descriptions :column="2" size="small" border>
      <el-descriptions-item label="验收单">
        {{ formData.name || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="验收评分">
        {{ score }}
      </el-descriptions-item>
      <el-descriptions-item label="验收场景">
        {{ formData.acceptance_scene || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="数据源轨迹">
        {{ formData.provider_trace || '-' }}
      </el-descriptions-item>
      <el-descriptions-item v-if="formData.extension_summary != null" label="执行结果">
        {{ formData.extension_summary }}
      </el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { formFieldProps } from '@flow/workflow-extensions/contracts/form-field'

const props = defineProps(formFieldProps)
const score = computed(() => Number(props.formData.acceptance_score || 0))
</script>

<style scoped>
.acceptance-summary-node {
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-left: 4px solid var(--el-color-primary);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.summary-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.summary-heading strong,
.summary-heading span {
  display: block;
}

.summary-heading span {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
