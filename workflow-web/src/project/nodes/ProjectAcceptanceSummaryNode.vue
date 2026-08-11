<template>
  <section class="acceptance-summary-node">
    <div class="summary-heading">
      <div>
        <strong>{{ config.title || '扩展执行摘要' }}</strong>
        <span>自定义节点：{{ node.nodeKey }}</span>
      </div>
      <el-tag :type="score >= 60 ? 'success' : 'warning'">
        {{ score >= 60 ? '达到验收线' : '待改进' }}
      </el-tag>
    </div>

    <el-descriptions :column="2" size="small" border>
      <el-descriptions-item label="验收单">
        {{ modelValue.name || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="验收评分">
        {{ score }}
      </el-descriptions-item>
      <el-descriptions-item label="验收场景">
        {{ modelValue.acceptance_scene || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="数据源轨迹">
        {{ modelValue.provider_trace || '-' }}
      </el-descriptions-item>
    </el-descriptions>

    <div v-if="!readonly && mode !== 'view'" class="summary-actions">
      <el-button
        :loading="loading"
        @click="executeNodeDataSource"
      >
        <el-icon><Refresh /></el-icon>
        执行节点数据源
      </el-button>
      <span>{{ modelValue.extension_summary || '等待节点执行' }}</span>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  node: { type: Object, required: true },
  modelValue: { type: Object, default: () => ({}) },
  readonly: Boolean,
  mode: { type: String, default: 'view' },
  context: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])
const loading = ref(false)
const score = computed(() => Number(props.modelValue.acceptance_score || 0))

async function executeNodeDataSource() {
  if (!props.dataSourceRuntime?.executeOwnerUsage) {
    ElMessage.warning('当前节点没有可用的数据源运行时')
    return
  }
  loading.value = true
  try {
    const results = await props.dataSourceRuntime.executeOwnerUsage(
      props.node,
      'FIELD_COMPUTE',
      {
        record: props.modelValue,
        input: {
          fieldCode: 'extension_summary',
          value: score.value
        }
      }
    )
    const first = results[0]?.data ?? results[0]
    const value = first?.value ?? first
    const next = {
      ...props.modelValue,
      extension_summary: value || '节点 Provider 已执行'
    }
    emit('update:modelValue', next)
    console.info('[ProjectExtensionAcceptance] 自定义节点数据源执行完成', {
      nodeKey: props.node.nodeKey,
      resultCount: results.length,
      value
    })
    ElMessage.success('自定义节点数据源已执行')
  } catch (error) {
    console.error('[ProjectExtensionAcceptance] 自定义节点数据源执行失败', error)
    ElMessage.error(error.message || '自定义节点数据源执行失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.acceptance-summary-node {
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-left: 4px solid var(--el-color-primary);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.summary-heading,
.summary-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary-heading {
  margin-bottom: 12px;
}

.summary-heading strong,
.summary-heading span {
  display: block;
}

.summary-heading span,
.summary-actions span {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary-actions {
  justify-content: flex-start;
  margin-top: 12px;
}
</style>
