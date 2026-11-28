<template>
  <div class="file-field">
    <!-- 只读模式 -->
    <template v-if="isDisabled">
      <div v-if="readonlyFileGroups.length" class="file-display-readonly">
        <div v-for="group in readonlyFileGroups" :key="group.name" :class="group.name ? 'file-group-readonly' : 'file-list-readonly'">
          <div v-for="(itemOrUrl, idx) in group.files" :key="idx" class="file-item-readonly">
            <div class="file-info">
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ getFileName(itemOrUrl) }}</span>
              <el-tag v-if="group.name" size="small" type="primary">{{ group.name }}</el-tag>
            </div>
            <div class="file-actions">
              <el-button type="primary" link size="small" @click="previewFile(itemOrUrl)">
                <el-icon><View /></el-icon> 预览
              </el-button>
              <el-button type="success" link size="small" @click="downloadFile(itemOrUrl)">
                <el-icon><Download /></el-icon> 下载
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <span v-else class="file-empty">-</span>
    </template>

    <!-- 编辑模式 -->
    <FileUploader
      v-else
      :modelValue="fieldValue"
      :field="enrichedField"
      :disabled="isDisabled"
      :is-image="isImage"
      :attachment-item-required-state="attachmentItemRequiredState"
      :upload-context="uploadContext"
      @update:modelValue="handleFileValueChange"
    />
  </div>
</template>

<script setup>

// 仅声明此实现已接入的前端事件，供字段配置和运行时共用。
defineOptions({ scriptEvents: [
    { name: 'onChange', label: '值变化', description: '用户修改值后触发，完成后执行值变化事件链' }
] })
import { computed, ref, watch } from 'vue'
import { Document, View, Download } from '@element-plus/icons-vue'
import FileUploader from '@/components/FileUploader.vue'
import { useFormField } from '@flow/workflow-core/vue/useFormField'
import request from '@/utils/request'
import { resolveEntityFileUploadContext } from '@flow/workflow-core/entity-file-upload-context'
import { previewFile, downloadFile } from '@/shared/file-access'
import { normalizeAttachmentFiles } from '@flow/workflow-core/file-attachment'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Array, Object], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null },
  context: { type: Object, default: () => ({}) },
  attachmentItemRequiredState: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, isDisabled, handleChange, parsedComponentProps } = useFormField(props, emit)

function handleFileValueChange(val) {
  return handleChange(val)
}

const isImage = computed(() => {
  const type = (props.field?.componentType || props.field?.fieldType || '').toLowerCase()
  return type === 'image'
})

// 从 API 异步获取的附件项配置
const apiFileItems = ref([])
const fileItemsLoading = ref(false)

watch(
  () => props.field?.fieldId,
  async (fieldId, previousFieldId) => {
    if (fieldId !== previousFieldId) {
      apiFileItems.value = []
    }
    if (!fieldId) return
    // 如果字段本身已有 fileItems，不需要请求
    if (props.field?.fileItems?.length > 0) return
    // 如果已经加载过，避免重复请求
    if (apiFileItems.value.length > 0) return
    // 如果 componentProps 里也有 fileItems，不需要请求
    if (parsedComponentProps.value?.fileItems?.length > 0) return

    fileItemsLoading.value = true
    try {
      // request.js 已配置 baseURL: '/api'，这里不需要再加 /api 前缀
      const res = await request.get(`/entity-field-file-item/field/${fieldId}`)
      if (Array.isArray(res) && res.length > 0) {
        apiFileItems.value = res
      }
    } catch (e) {
      console.error('获取附件项配置失败:', e)
    } finally {
      fileItemsLoading.value = false
    }
  },
  { immediate: true }
)

// 将 componentProps 中的附件配置合并到 field，供 FileUploader 使用
// 设计器可能将 fileItems 存储在 componentProps、config、extraConfig 等多个位置
const enrichedField = computed(() => {
  const field = props.field || {}
  const cp = parsedComponentProps.value

  // 合并 field 和 cp，避免 cp 中的 undefined/null 覆盖 field 中的有效值
  const merged = { ...field }
  Object.keys(cp).forEach(key => {
    if (cp[key] != null) {
      merged[key] = cp[key]
    }
  })

  // 深度搜索 fileItems（支持多组附件配置）
  const fileItems = [
    merged.fileItems,
    field.fileItems,
    cp.fileItems,
    merged.config?.fileItems,
    merged.extraConfig?.fileItems,
    merged.attachmentConfig?.fileItems,
    apiFileItems.value
  ].find(items => Array.isArray(items) && items.length > 0) || []

  merged.fileItems = fileItems

  // 确保其他文件相关配置存在
  if (!merged.fileTypes && merged.fileType) {
    merged.fileTypes = merged.fileType
  }
  if (!merged.fileMaxSize) {
    merged.fileMaxSize = 10
  }
  if (!merged.fileMaxCount) {
    merged.fileMaxCount = merged.isImage ? 9 : 5
  }

  return merged
})

// 上传上下文只保留后端鉴权所需的最小坐标，避免把整份表单数据带入请求。
const uploadContext = computed(() =>
  resolveEntityFileUploadContext(props.context, enrichedField.value)
)

// 单文件、数组和分组共用引用格式；只读投影不改写保存值。
const readonlyFileGroups = computed(() => {
  const groups = new Map()
  for (const file of normalizeAttachmentFiles(fieldValue.value)) {
    if (!groups.has(file.groupName)) groups.set(file.groupName, [])
    groups.get(file.groupName).push(file)
  }
  return [...groups].map(([name, files]) => ({ name, files }))
})

function getFileName(file) {
  return file.name || file.originalName || '附件'
}

</script>

<style scoped>
.file-field {
  width: 100%;
}

.file-display-readonly {
  width: 100%;
}

.file-list-readonly {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-group-readonly {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.file-item-readonly {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #fff;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.file-info .el-icon {
  color: #409eff;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

.file-actions {
  display: flex;
  gap: 4px;
}

.file-empty {
  color: #c0c4cc;
  font-size: 14px;
}
</style>
