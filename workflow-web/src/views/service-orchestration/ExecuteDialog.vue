<template>
  <el-dialog
    v-model="visible"
    :title="'执行服务: ' + (service?.serviceName || '')"
    width="600px"
    :close-on-click-modal="false"
  >
    <el-form label-width="100px">
      <el-form-item label="输入参数">
        <el-input
          v-model="inputParams"
          type="textarea"
          rows="6"
          placeholder='{"key": "value"}'
        />
      </el-form-item>
    </el-form>
    
    <!-- 执行结果 -->
    <div v-if="result" class="execute-result" :class="result.success ? 'success' : 'error'">
      <div class="result-header">
        <el-icon v-if="result.success" size="24" color="#67c23a"><CircleCheck /></el-icon>
        <el-icon v-else size="24" color="#f56c6c"><CircleClose /></el-icon>
        <span>{{ result.success ? '执行成功' : '执行失败' }}</span>
      </div>
      <div class="result-content">
        <p>执行ID: {{ result.executionId }}</p>
        <p>执行耗时: {{ result.durationMs }}ms</p>
        <p v-if="!result.success">错误信息: {{ result.message }}</p>
        <pre v-else>{{ JSON.stringify(result.output, null, 2) }}</pre>
      </div>
    </div>
    
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="executing" @click="handleExecute">
        执行
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { executeService } from '@/api/service-orchestration'

const props = defineProps({
  modelValue: Boolean,
  service: Object
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const inputParams = ref('{}')
const executing = ref(false)
const result = ref(null)

// 执行
const handleExecute = async () => {
  if (!props.service?.id) return
  
  executing.value = true
  result.value = null
  
  try {
    let params = {}
    try {
      params = JSON.parse(inputParams.value)
    } catch {
      ElMessage.warning('输入参数JSON格式不正确')
      return
    }
    
    const res = await executeService(props.service.id, params)
    result.value = res
    
    if (res.success) {
      emit('success')
    }
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
  } finally {
    executing.value = false
  }
}
</script>

<style scoped lang="scss">
.execute-result {
  margin-top: 20px;
  padding: 15px;
  border-radius: 4px;
  
  &.success {
    background: #f0f9eb;
    border: 1px solid #e1f3d8;
  }
  
  &.error {
    background: #fef0f0;
    border: 1px solid #fde2e2;
  }
  
  .result-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 15px;
    font-weight: bold;
  }
  
  .result-content {
    p {
      margin: 5px 0;
    }
    
    pre {
      background: #f5f7fa;
      padding: 10px;
      border-radius: 4px;
      overflow-x: auto;
    }
  }
}
</style>
