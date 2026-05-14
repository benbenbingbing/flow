<template>
  <div class="file-uploader">
    <!-- 多组模式：field.fileItems 存在且非空 -->
    <template v-if="isMultiGroup">
      <!-- 顶部标签栏 -->
      <div class="group-toolbar">
        <div class="group-tags">
          <el-tooltip
            v-for="(item, index) in fileItems"
            :key="index"
            :content="getGroupTooltip(item)"
            placement="top"
          >
            <div
              class="group-tag"
              :class="{ active: activeGroupIndex === index }"
              @click="activeGroupIndex = index"
            >
              {{ item.itemName || `附件项 ${index + 1}` }}
            </div>
          </el-tooltip>
        </div>
      </div>

      <!-- 统一上传区域 -->
      <el-upload
        ref="uploadRef"
        class="multi-group-uploader"
        :action="uploadUrl"
        :headers="uploadHeaders"
        :disabled="disabled"
        :accept="activeAcceptTypes"
        :before-upload="beforeUploadActive"
        :on-success="handleSuccessActive"
        :on-error="handleError"
        :show-file-list="false"
        drag
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <div class="upload-target" v-if="activeItem">
          当前上传至：<el-tag size="small" type="primary">{{ activeItem.itemName }}</el-tag>
        </div>
      </el-upload>

      <!-- 统一的文件列表 -->
      <div class="file-list all-files" v-if="allFiles.length > 0">
        <div class="file-list-header">附件列表</div>
        <div v-for="(file, index) in allFiles" :key="index" class="file-item">
          <div class="file-info">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ file.name }}</span>
            <el-tag size="small" :type="getGroupTagType(file.groupIndex)">{{ file.groupName }}</el-tag>
          </div>
          <div class="file-actions">
            <el-button type="primary" link size="small" @click="previewFile(file.url)">
              <el-icon><View /></el-icon> 预览
            </el-button>
            <el-button type="success" link size="small" @click="downloadFile(file.url)">
              <el-icon><Download /></el-icon> 下载
            </el-button>
            <el-button type="warning" link size="small" @click="updateFile(file)">
              <el-icon><RefreshRight /></el-icon> 更新
            </el-button>
            <el-button type="danger" link size="small" @click="removeFileByIndex(file.groupIndex, file.fileIndex)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </div>
        </div>
      </div>
    </template>

    <!-- 单组模式（原有逻辑） -->
    <template v-else>
      <!-- 图片上传模式 -->
      <template v-if="isImage">
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :file-list="fileList"
          :limit="maxCount"
          :disabled="disabled"
          :accept="acceptTypes"
          :before-upload="beforeUpload"
          :on-success="handleSuccess"
          :on-remove="handleRemove"
          :on-error="handleError"
          :on-exceed="handleExceed"
          list-type="picture-card"
        >
          <el-icon><Plus /></el-icon>
          <template #tip>
            <div class="upload-tip" v-if="showTips">
              <span v-if="acceptTypes">支持格式: {{ acceptTypes }}</span>
              <span v-if="maxSize">，单文件不超过 {{ maxSize }}MB</span>
              <span v-if="maxCount > 1">，最多 {{ maxCount }} 张</span>
            </div>
          </template>
        </el-upload>
      </template>

      <!-- 文件上传模式 -->
      <template v-else>
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :file-list="fileList"
          :limit="maxCount"
          :disabled="disabled"
          :accept="acceptTypes"
          :before-upload="beforeUpload"
          :on-success="handleSuccess"
          :on-remove="handleRemove"
          :on-error="handleError"
          :on-exceed="handleExceed"
          drag
        >
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">
            拖拽文件到此处或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="upload-tip" v-if="showTips">
              <span v-if="acceptTypes">支持格式: {{ acceptTypes }}</span>
              <span v-if="maxSize">，单文件不超过 {{ maxSize }}MB</span>
              <span v-if="maxCount > 1">，最多 {{ maxCount }} 个文件</span>
            </div>
          </template>
        </el-upload>
      </template>

      <!-- 文件列表显示 -->
      <div class="file-list" v-if="!isImage && fileList.length > 0">
        <div v-for="(file, index) in fileList" :key="index" class="file-item">
          <el-icon><Document /></el-icon>
          <span class="file-name">{{ file.name || file.fileName }}</span>
          <el-button
            v-if="!disabled"
            type="danger"
            link
            size="small"
            @click="removeFile(index)"
          >
            删除
          </el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Plus, Upload, Document, View, Download, RefreshRight, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  modelValue: {
    type: [String, Array, Object],
    default: () => []
  },
  field: {
    type: Object,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  isImage: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const userStore = useUserStore()
const uploadRef = ref(null)
const activeGroupIndex = ref(0)
const pendingGroupIndex = ref(0)

// 上传地址
const uploadUrl = '/api/file/upload'

// 上传请求头
const uploadHeaders = computed(() => {
  return {
    Authorization: `Bearer ${userStore.token}`
  }
})

// ==================== 多组模式 ====================
const isMultiGroup = computed(() => {
  return props.field.fileItems && props.field.fileItems.length > 0
})

const fileItems = computed(() => {
  return props.field.fileItems || []
})

const activeItem = computed(() => {
  return fileItems.value[activeGroupIndex.value]
})

const activeAcceptTypes = computed(() => {
  if (!activeItem.value) return ''
  const ft = activeItem.value.fileTypes
  if (typeof ft === 'string') return ft
  if (Array.isArray(ft)) return ft.join(',')
  return ''
})

// 多组模式下，v-model 是对象 { itemName: [urls] }
const groupModelValue = computed(() => {
  if (props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue)) {
    return props.modelValue
  }
  return {}
})

// 所有文件合并列表
const allFiles = computed(() => {
  const result = []
  const model = groupModelValue.value
  fileItems.value.forEach((item, gIndex) => {
    const key = item.itemName || `附件项${gIndex + 1}`
    const urls = model[key] || []
    const arr = Array.isArray(urls) ? urls : (urls ? [urls] : [])
    arr.forEach((url, fIndex) => {
      result.push({
        name: getFileNameFromUrl(url),
        url: url,
        groupIndex: gIndex,
        groupName: item.itemName || `附件项${gIndex + 1}`,
        fileIndex: fIndex
      })
    })
  })
  return result
})

const triggerUpload = () => {
  if (uploadRef.value && uploadRef.value.$refs.input) {
    uploadRef.value.$refs.input.click()
  }
}

const beforeUploadActive = (file) => {
  pendingGroupIndex.value = activeGroupIndex.value
  const item = fileItems.value[activeGroupIndex.value]
  if (!item) return false

  const acceptTypes = activeAcceptTypes.value
  if (acceptTypes) {
    const fileExt = '.' + file.name.split('.').pop().toLowerCase()
    const allowedTypes = acceptTypes.split(',').map(t => t.trim().toLowerCase())
    if (!allowedTypes.includes(fileExt)) {
      ElMessage.error(`不支持该文件类型，请上传 ${acceptTypes} 格式的文件`)
      return false
    }
  }
  const maxSize = item.maxSize || 10
  const maxBytes = maxSize * 1024 * 1024
  if (file.size > maxBytes) {
    ElMessage.error(`文件大小超过限制，最大允许 ${maxSize}MB`)
    return false
  }

  // 检查数量限制
  const key = item.itemName || `附件项${activeGroupIndex.value + 1}`
  const currentUrls = groupModelValue.value[key] || []
  const currentCount = Array.isArray(currentUrls) ? currentUrls.length : (currentUrls ? 1 : 0)
  const maxCount = item.maxCount || 5
  if (currentCount >= maxCount) {
    ElMessage.warning(`【${item.itemName}】最多只能上传 ${maxCount} 个文件`)
    return false
  }

  return true
}

const handleSuccessActive = (response, file) => {
  if (response.code === 200) {
    const gIndex = pendingGroupIndex.value
    const item = fileItems.value[gIndex]
    if (!item) return
    const key = item.itemName || `附件项${gIndex + 1}`
    const url = response.data?.url || response.data
    const current = groupModelValue.value[key] || []
    const arr = Array.isArray(current) ? [...current] : (current ? [current] : [])
    arr.push(url)
    const newValue = { ...groupModelValue.value, [key]: arr }
    emit('update:modelValue', newValue)
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const removeFileByIndex = (groupIndex, fileIndex) => {
  const item = fileItems.value[groupIndex]
  if (!item) return
  const key = item.itemName || `附件项${groupIndex + 1}`
  const current = groupModelValue.value[key] || []
  const arr = Array.isArray(current) ? [...current] : (current ? [current] : [])
  arr.splice(fileIndex, 1)
  const newValue = { ...groupModelValue.value, [key]: arr }
  emit('update:modelValue', newValue)
}

const previewFile = (url) => {
  window.open(url, '_blank')
}

const downloadFile = (url) => {
  const a = document.createElement('a')
  a.href = url
  a.download = getFileNameFromUrl(url)
  a.click()
}

const updateFile = (file) => {
  // 切换到对应分组并触发上传
  activeGroupIndex.value = file.groupIndex
  triggerUpload()
}

const getGroupTagType = (index) => {
  const types = ['primary', 'success', 'warning', 'danger', 'info']
  return types[index % types.length]
}

const getGroupTooltip = (item) => {
  const parts = []
  if (item.fileTypes) {
    const ft = typeof item.fileTypes === 'string' ? item.fileTypes : item.fileTypes.join(',')
    parts.push(`类型: ${ft}`)
  }
  if (item.maxSize) {
    parts.push(`单文件不超过 ${item.maxSize}MB`)
  }
  if (item.maxCount) {
    parts.push(`最多 ${item.maxCount} 个`)
  }
  return parts.join('，') || '无限制'
}

// ==================== 单组模式（原有逻辑） ====================
const acceptTypes = computed(() => {
  if (props.field.fileTypes) {
    if (typeof props.field.fileTypes === 'string') {
      return props.field.fileTypes
    }
    if (Array.isArray(props.field.fileTypes)) {
      return props.field.fileTypes.join(',')
    }
  }
  if (props.isImage) {
    return '.jpg,.jpeg,.png,.gif,.bmp,.webp'
  }
  return ''
})

const maxSize = computed(() => {
  return props.field.fileMaxSize || 10
})

const maxCount = computed(() => {
  return props.field.fileMaxCount || (props.isImage ? 9 : 5)
})

const showTips = computed(() => {
  return acceptTypes.value || maxSize.value || maxCount.value > 1
})

const fileList = ref([])

const initFileList = () => {
  const value = props.modelValue
  if (!value) {
    fileList.value = []
    return
  }
  if (typeof value === 'string') {
    fileList.value = [{
      name: getFileNameFromUrl(value),
      url: value,
      response: { data: { url: value } }
    }]
    return
  }
  if (Array.isArray(value)) {
    fileList.value = value.map(url => ({
      name: getFileNameFromUrl(url),
      url: url,
      response: { data: { url: url } }
    }))
    return
  }
  fileList.value = []
}

const getFileNameFromUrl = (url) => {
  if (!url) return '未知文件'
  const parts = url.split('/')
  return parts[parts.length - 1] || '未知文件'
}

const beforeUpload = (file) => {
  if (acceptTypes.value) {
    const fileExt = '.' + file.name.split('.').pop().toLowerCase()
    const allowedTypes = acceptTypes.value.split(',').map(t => t.trim().toLowerCase())
    if (!allowedTypes.includes(fileExt)) {
      ElMessage.error(`不支持该文件类型，请上传 ${acceptTypes.value} 格式的文件`)
      return false
    }
  }
  const maxBytes = maxSize.value * 1024 * 1024
  if (file.size > maxBytes) {
    ElMessage.error(`文件大小超过限制，最大允许 ${maxSize.value}MB`)
    return false
  }
  return true
}

const handleSuccess = (response, file, uploadFiles) => {
  if (response.code === 200) {
    updateModelValue(uploadFiles)
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleError = (error, file) => {
  console.error('上传失败:', error)
  ElMessage.error('文件上传失败，请重试')
}

const handleExceed = () => {
  ElMessage.warning(`最多只能上传 ${maxCount.value} 个文件`)
}

const handleRemove = (file, uploadFiles) => {
  updateModelValue(uploadFiles)
}

const removeFile = (index) => {
  fileList.value.splice(index, 1)
  updateModelValue(fileList.value)
}

const updateModelValue = (uploadFiles) => {
  const files = uploadFiles
    .filter(f => f.status === 'success')
    .map(f => {
      const response = f.response
      if (response && response.data && response.data.url) {
        return response.data.url
      }
      return f.url
    })
  if (maxCount.value === 1) {
    emit('update:modelValue', files[0] || '')
  } else {
    emit('update:modelValue', files)
  }
}

watch(() => props.modelValue, () => {
  if (!isMultiGroup.value) {
    initFileList()
  }
}, { immediate: true })
</script>

<style scoped>
.file-uploader {
  width: 100%;
}

/* 多组模式：工具栏 */
.group-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}

.group-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.group-tag {
  padding: 6px 16px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: all 0.2s;
}

.group-tag:hover {
  border-color: #409eff;
  color: #409eff;
}

.group-tag.active {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}

.group-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.group-limit-tip {
  font-size: 12px;
  color: #909399;
}

/* 多组上传区域 */
.multi-group-uploader {
  margin-bottom: 16px;
}

.multi-group-uploader :deep(.el-upload-dragger) {
  width: 100%;
  padding: 24px;
}

.upload-target {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}

/* 文件列表 */
.file-list {
  margin-top: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px;
}

.file-list.all-files {
  padding: 0;
  border: none;
}

.file-list-header {
  font-weight: 600;
  color: #303133;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
  border-radius: 4px 4px 0 0;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.file-item:last-child {
  border-bottom: none;
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

/* 单组模式样式 */
.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

/* 图片上传样式调整 */
:deep(.el-upload--picture-card) {
  width: 100px;
  height: 100px;
}

:deep(.el-upload-list--picture-card .el-upload-list__item) {
  width: 100px;
  height: 100px;
}
</style>
