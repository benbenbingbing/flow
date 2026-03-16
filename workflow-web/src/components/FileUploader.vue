<template>
  <div class="file-uploader">
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
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Plus, Upload, Document } from '@element-plus/icons-vue'
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

// 上传地址
const uploadUrl = '/api/file/upload'

// 上传请求头
const uploadHeaders = computed(() => {
  return {
    Authorization: `Bearer ${userStore.token}`
  }
})

// 解析文件类型限制
const acceptTypes = computed(() => {
  if (props.field.fileTypes) {
    // 如果已经是字符串，直接使用
    if (typeof props.field.fileTypes === 'string') {
      return props.field.fileTypes
    }
    // 如果是数组，合并
    if (Array.isArray(props.field.fileTypes)) {
      return props.field.fileTypes.join(',')
    }
  }
  // 默认图片类型
  if (props.isImage) {
    return '.jpg,.jpeg,.png,.gif,.bmp,.webp'
  }
  return ''
})

// 文件大小限制（MB）
const maxSize = computed(() => {
  return props.field.fileMaxSize || 10
})

// 文件数量限制
const maxCount = computed(() => {
  return props.field.fileMaxCount || (props.isImage ? 9 : 5)
})

// 是否显示提示
const showTips = computed(() => {
  return acceptTypes.value || maxSize.value || maxCount.value > 1
})

// 文件列表
const fileList = ref([])

// 初始化文件列表
const initFileList = () => {
  const value = props.modelValue
  if (!value) {
    fileList.value = []
    return
  }
  
  // 处理字符串（单个文件URL）
  if (typeof value === 'string') {
    fileList.value = [{
      name: getFileNameFromUrl(value),
      url: value,
      response: { data: value }
    }]
    return
  }
  
  // 处理数组
  if (Array.isArray(value)) {
    fileList.value = value.map(url => ({
      name: getFileNameFromUrl(url),
      url: url,
      response: { data: url }
    }))
    return
  }
  
  // 处理对象（包含文件信息的数组）
  if (Array.isArray(value.files)) {
    fileList.value = value.files.map(file => ({
      name: file.fileName || file.name,
      url: file.url || file.fileUrl,
      response: { data: file }
    }))
  }
}

// 从URL获取文件名
const getFileNameFromUrl = (url) => {
  if (!url) return '未知文件'
  const parts = url.split('/')
  return parts[parts.length - 1] || '未知文件'
}

// 上传前检查
const beforeUpload = (file) => {
  // 检查文件类型
  if (acceptTypes.value) {
    const fileExt = '.' + file.name.split('.').pop().toLowerCase()
    const allowedTypes = acceptTypes.value.split(',').map(t => t.trim().toLowerCase())
    if (!allowedTypes.includes(fileExt)) {
      ElMessage.error(`不支持该文件类型，请上传 ${acceptTypes.value} 格式的文件`)
      return false
    }
  }
  
  // 检查文件大小
  const maxBytes = maxSize.value * 1024 * 1024
  if (file.size > maxBytes) {
    ElMessage.error(`文件大小超过限制，最大允许 ${maxSize.value}MB`)
    return false
  }
  
  return true
}

// 上传成功
const handleSuccess = (response, file, uploadFiles) => {
  if (response.code === 200) {
    updateModelValue(uploadFiles)
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

// 上传失败
const handleError = (error, file) => {
  console.error('上传失败:', error)
  ElMessage.error('文件上传失败，请重试')
}

// 超出数量限制
const handleExceed = () => {
  ElMessage.warning(`最多只能上传 ${maxCount.value} 个文件`)
}

// 删除文件
const handleRemove = (file, uploadFiles) => {
  updateModelValue(uploadFiles)
}

// 删除文件（非上传组件调用）
const removeFile = (index) => {
  fileList.value.splice(index, 1)
  updateModelValue(fileList.value)
}

// 更新值
const updateModelValue = (uploadFiles) => {
  const files = uploadFiles
    .filter(f => f.status === 'success')
    .map(f => {
      const response = f.response
      if (response && response.data) {
        return response.data
      }
      return f.url
    })
  
  if (maxCount.value === 1) {
    // 单文件模式
    emit('update:modelValue', files[0] || '')
  } else {
    // 多文件模式
    emit('update:modelValue', files)
  }
}

// 监听值变化
watch(() => props.modelValue, () => {
  initFileList()
}, { immediate: true })
</script>

<style scoped>
.file-uploader {
  width: 100%;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.file-list {
  margin-top: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 8px;
  border-bottom: 1px solid #ebeef5;
}

.file-item:last-child {
  border-bottom: none;
}

.file-item .el-icon {
  margin-right: 8px;
  color: #409eff;
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
