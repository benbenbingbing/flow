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
    </el-descriptions>

    <div v-if="!disabled && !readonly && mode !== 'view'" class="summary-actions">
      <el-button
        :loading="loading"
        @click="executeNodeDataSource"
      >
        <el-icon><Refresh /></el-icon>
        执行数据源
      </el-button>
      <span>{{ formData.extension_summary || '等待节点执行' }}</span>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, onBeforeUnmount } from 'vue'
import { formFieldProps, formFieldEmits } from '@flow/workflow-extension-sdk/contracts/form-field'
import { useExtensionHost } from '@flow/workflow-extension-sdk/host'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const { showRequestError } = useExtensionHost()

const props = defineProps(formFieldProps)
const emit = defineEmits(formFieldEmits)
let alive = true
onBeforeUnmount(() => { alive = false })
const loading = ref(false)
const score = computed(() => Number(props.formData.acceptance_score || 0))

/** 调用绑定的数据源后提交字段补丁，由宿主执行权限检查、字段事件和校验。 */
async function executeNodeDataSource() {
  if (loading.value || props.disabled || props.readonly || props.mode === 'view') return
  if (!props.dataSourceRuntime?.executeOwnerUsage) {
    ElMessage.warning('当前组件没有可用的数据源运行时')
    return
  }
  loading.value = true
  const record = props.formData
  const node = props.node
  try {
    const results = await props.dataSourceRuntime.executeOwnerUsage(
      props.node || props.field,
      'FIELD_COMPUTE',
      {
        record,
        input: {
          fieldCode: 'extension_summary',
          value: score.value
        }
      }
    )
    // 子表切换行或表单更新期间的旧请求不得给新的记录回填字段。
    if (!alive || props.formData !== record || props.node !== node) return
    const first = results[0]?.data ?? results[0]
    const value = first?.value ?? first
    emit('patch-form', { extension_summary: value ?? '组件 Provider 已执行' })
    console.info('[ProjectExtensionAcceptance] 自定义节点数据源执行完成', {
      nodeKey: props.node?.nodeKey || props.field.fieldCode || '-',
      resultCount: results.length,
      value
    })
    ElMessage.success('自定义节点数据源已执行')
  } catch (error) {
    console.error('[ProjectExtensionAcceptance] 自定义节点数据源执行失败', error)
    showRequestError(error, '自定义节点数据源执行失败')
  } finally {
    loading.value = false
  }
}

// 数据源尚在计算时阻止提交，避免保存时漏掉用户刚触发的结果。
defineExpose({ validate: () => !loading.value })
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
