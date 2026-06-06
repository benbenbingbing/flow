<template>
  <div class="file-field">
    <!-- 只读模式 -->
    <template v-if="isDisabled">
      <template v-if="isFileLikeValue">
        <!-- 多组模式：对象 { groupName: [urls] } -->
        <div v-if="fieldValue && typeof fieldValue === 'object' && !Array.isArray(fieldValue)" class="file-display-readonly">
          <div v-for="(urls, groupName) in fieldValue" :key="groupName" class="file-group-readonly">
            <div v-for="(itemOrUrl, idx) in (Array.isArray(urls) ? urls : [urls])" :key="idx" class="file-item-readonly">
              <div class="file-info">
                <el-icon><Document /></el-icon>
                <span class="file-name">{{ getFileName(itemOrUrl) }}</span>
                <el-tag size="small" type="primary">{{ groupName }}</el-tag>
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
        <!-- 数组模式：多文件 [urls] -->
        <div v-else-if="Array.isArray(fieldValue)" class="file-list-readonly">
          <div v-for="(itemOrUrl, idx) in fieldValue" :key="idx" class="file-item-readonly">
            <div class="file-info">
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ getFileName(itemOrUrl) }}</span>
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
        <!-- 单文件字符串 -->
        <div v-else-if="typeof fieldValue === 'string' && fieldValue.startsWith('/')" class="file-item-readonly">
          <div class="file-info">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ getFileName(fieldValue) }}</span>
          </div>
          <div class="file-actions">
            <el-button type="primary" link size="small" @click="previewFile(fieldValue)">
              <el-icon><View /></el-icon> 预览
            </el-button>
            <el-button type="success" link size="small" @click="downloadFile(fieldValue)">
              <el-icon><Download /></el-icon> 下载
            </el-button>
          </div>
        </div>
      </template>
      <span v-else class="file-empty">-</span>
    </template>

    <!-- 编辑模式 -->
    <FileUploader
      v-else
      :modelValue="fieldValue"
      :field="enrichedField"
      :disabled="isDisabled"
      :is-image="isImage"
      @update:modelValue="handleFileValueChange"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Document, View, Download } from '@element-plus/icons-vue'
import FileUploader from '@/components/FileUploader.vue'
import { useFormField } from '../composables/useFormField.js'
import request from '@/utils/request'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Array, Object], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, isDisabled, handleChange, getEventCode, executeEvent, parsedComponentProps } = useFormField(props, emit)

function handleFileValueChange(val) {
  emit('update:modelValue', val)
  executeEvent(getEventCode('onChange'), val)
  emit('change', val)
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
  async (fieldId) => {
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
  const fileItems =
    merged.fileItems ||
    (merged.config && merged.config.fileItems) ||
    (merged.extraConfig && merged.extraConfig.fileItems) ||
    (merged.attachmentConfig && merged.attachmentConfig.fileItems) ||
    apiFileItems.value ||
    []

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

// 判断字段值是否看起来像文件数据
const isFileLikeValue = computed(() => {
  const val = props.modelValue
  if (val == null) return false
  if (typeof val === 'object') {
    if (Array.isArray(val)) {
      return val.some(item => typeof item === 'string' && item.startsWith('/'))
    }
    return Object.values(val).some(v => {
      if (Array.isArray(v)) {
        return v.some(item => typeof item === 'string' && item.startsWith('/'))
      }
      return typeof v === 'string' && v.startsWith('/')
    })
  }
  if (typeof val === 'string' && val.startsWith('/')) return true
  return false
})

function resolveUrl(itemOrUrl) {
  if (!itemOrUrl) return ''
  if (typeof itemOrUrl === 'object') return itemOrUrl.url || ''
  return String(itemOrUrl)
}

function getFileName(itemOrUrl) {
  if (!itemOrUrl) return '未知文件'
  if (typeof itemOrUrl === 'object') {
    return itemOrUrl.name || itemOrUrl.originalName || getFileName(itemOrUrl.url)
  }
  const parts = String(itemOrUrl).split('/')
  return parts[parts.length - 1] || '未知文件'
}

function previewFile(itemOrUrl) {
  const url = resolveUrl(itemOrUrl)
  if (!url) return
  window.open(url, '_blank')
}

function downloadFile(itemOrUrl) {
  const url = resolveUrl(itemOrUrl)
  if (!url) return
  const a = document.createElement('a')
  a.href = url
  a.download = getFileName(itemOrUrl)
  a.click()
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
