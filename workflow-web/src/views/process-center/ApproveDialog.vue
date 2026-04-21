<template>
  <el-dialog
    v-model="visible"
    title="审批任务"
    width="700px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-if="task" class="approve-dialog">
      <!-- 流程信息 -->
      <div class="process-info">
        <h4>{{ task.processName }}</h4>
        <p>{{ task.taskName }}</p>
      </div>
      
      <!-- 表单预览区域 -->
      <el-divider />
      <div class="form-preview">
        <h4>表单数据</h4>
        <!-- 这里根据 entityCode 和 entityDataId 加载表单数据 -->
        <p class="text-gray">表单预览区域...</p>
      </div>
      
      <el-divider />
      
      <!-- 审批意见 -->
      <el-form :model="form" label-width="80px">
        <el-form-item label="审批意见">
          <el-radio-group v-model="form.action">
            <el-radio label="APPROVE">同意</el-radio>
            <el-radio label="REJECT">驳回</el-radio>
            <el-radio label="RETURN">退回</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="常用意见">
          <div class="common-opinions">
            <el-tag
              v-for="opinion in commonOpinions"
              :key="opinion.id"
              class="opinion-tag"
              @click="selectOpinion(opinion)"
            >
              {{ opinion.opinionContent }}
            </el-tag>
            <el-button link type="primary" @click="loadCommonOpinions">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </el-form-item>
        
        <el-form-item label="详细意见">
          <el-input
            v-model="form.comment"
            type="textarea"
            rows="4"
            placeholder="请输入审批意见"
          />
        </el-form-item>
      </el-form>
    </div>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCommonOpinions } from '@/api/process-center'

const props = defineProps({
  modelValue: Boolean,
  task: Object
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const submitting = ref(false)
const commonOpinions = ref([])

const form = reactive({
  action: 'APPROVE',
  comment: ''
})

// 加载常用意见
const loadCommonOpinions = async () => {
  try {
    const res = await getCommonOpinions({ opinionType: form.action })
    commonOpinions.value = res || []
  } catch (error) {
    console.error('加载常用意见失败:', error)
  }
}

// 选择意见
const selectOpinion = (opinion) => {
  form.comment = opinion.opinionContent
}

// 提交审批
const handleSubmit = async () => {
  submitting.value = true
  try {
    // 调用审批接口
    // await approveTask({
    //   taskId: props.task.taskId,
    //   action: form.action,
    //   comment: form.comment
    // })
    
    ElMessage.success('审批成功')
    emit('success')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message || '审批失败')
  } finally {
    submitting.value = false
  }
}

// 监听弹窗打开
watch(() => props.modelValue, (val) => {
  if (val && props.task) {
    form.action = 'APPROVE'
    form.comment = ''
    loadCommonOpinions()
  }
})
</script>

<style scoped lang="scss">
.approve-dialog {
  .process-info {
    h4 {
      margin: 0 0 8px 0;
      color: #303133;
    }
    p {
      margin: 0;
      color: #909399;
    }
  }
  
  .form-preview {
    min-height: 100px;
  }
  
  .common-opinions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    
    .opinion-tag {
      cursor: pointer;
      &:hover {
        background-color: #ecf5ff;
      }
    }
  }
}
</style>
