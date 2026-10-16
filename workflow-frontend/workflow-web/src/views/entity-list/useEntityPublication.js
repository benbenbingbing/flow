
import { showRequestError } from '@/shared/request'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { entityApi } from '@/api/entity'
import { entityVersionDiffApi } from '@/api/entityVersionDiff'
import { schemaOperationApi } from '@/api/schemaOperation'
import { generateMigrationTag } from '@/utils/migrationTag'

/**
 * 发布工作区拥有预览、风险确认与结构重试状态；字段编辑、发布历史仍独立管理。
 * publish 成功才刷新实体目录，retry 只恢复待重试操作并重新加载差异，不视为发布成功。
 */
export function useEntityPublication({ fetchData }) {
  // ========== 发布差异预览相关 ==========
  const publishDiffDialogVisible = ref(false)

  const publishDiffLoading = ref(false)

  const publishDiffData = ref(null)

  const publishTargetEntity = ref(null)

  const schemaRetryLoading = ref(false)

  const publishMigrationForm = ref({
    versionDescription: '',
    markForExport: true,
    migrationTag: generateMigrationTag(),
    confirmHighRiskSchemaChange: false
  })

  const schemaRiskTag = (risk) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' })[risk] || 'info'

  const schemaStatusText = (status) => ({
    METADATA_SAVED: '元数据已保存',
    DDL_PENDING: 'DDL 待执行',
    DDL_RUNNING: 'DDL 执行中',
    SCHEMA_CONSISTENT: '结构一致',
    DDL_FAILED: 'DDL 失败',
    TERMINATED: '已终止'
  })[status] || status

  // 发布实体（先显示差异预览）
  const handlePublish = async (row) => {
    publishTargetEntity.value = row
    publishMigrationForm.value = {
      versionDescription: '',
      markForExport: true,
      migrationTag: generateMigrationTag(),
      confirmHighRiskSchemaChange: false
    }
    publishDiffDialogVisible.value = true
    publishDiffLoading.value = true
    try {
      const diff = await entityVersionDiffApi.getPendingPublishDiff(row.id)
      publishDiffData.value = diff
    } catch (error) {
      console.error(error)
      showRequestError(error, '获取发布预览失败')
    } finally {
      publishDiffLoading.value = false
    }
  }

  // 首次发布与重新发布都先请求服务端差异，服务端决定版本与结构变更类型。
  const handleRepublish = handlePublish

  // 确认发布
  const confirmPublish = async () => {
    if (!publishTargetEntity.value) return
    if (publishMigrationForm.value.markForExport && !publishMigrationForm.value.migrationTag.trim()) {
      ElMessage.warning('加入待导出清单时必须填写迁移标记')
      return
    }
    const schemaOperation = publishDiffData.value?.schemaOperation
    if (schemaOperation?.uniqueConflicts?.length) {
      ElMessage.error('唯一约束扫描存在冲突，请先清理重复数据')
      return
    }
    if (schemaOperation?.riskLevel === 'HIGH' && !publishMigrationForm.value.confirmHighRiskSchemaChange) {
      ElMessage.warning('请先确认高风险结构变更')
      return
    }
    publishDiffLoading.value = true
    try {
      await entityApi.publish(publishTargetEntity.value.id, { ...publishMigrationForm.value })
      ElMessage.success(publishDiffData.value?.isFirstPublish ? '发布成功' : '重新发布成功，表结构已更新')
      publishDiffDialogVisible.value = false
      fetchData()
    } catch (error) {
      console.error(error)
      showRequestError(error, error.response?.data?.message || '发布失败')
    } finally {
      publishDiffLoading.value = false
    }
  }

  const retrySchemaOperation = async () => {
    if (!publishTargetEntity.value) return
    schemaRetryLoading.value = true
    try {
      await schemaOperationApi.retry(publishTargetEntity.value.id)
      publishDiffData.value = await entityVersionDiffApi.getPendingPublishDiff(publishTargetEntity.value.id)
      ElMessage.success('结构操作已恢复为待重试，请确认后重新发布')
    } catch (error) {
      showRequestError(error, error.response?.data?.message || error.message || '恢复结构操作失败')
    } finally {
      schemaRetryLoading.value = false
    }
  }

  return {
    publishDiffDialogVisible,
    publishDiffLoading,
    publishDiffData,
    schemaRetryLoading,
    publishMigrationForm,
    schemaRiskTag,
    schemaStatusText,
    handlePublish,
    handleRepublish,
    confirmPublish,
    retrySchemaOperation
  }
}
