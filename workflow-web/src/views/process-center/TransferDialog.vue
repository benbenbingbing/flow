<template>
  <el-dialog
    v-model="visible"
    title="任务转办"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
      <el-form-item label="当前任务">
        <span v-if="task">{{ task.taskName }}</span>
      </el-form-item>
      
      <el-form-item label="转办给" prop="assigneeId">
        <UserSelector v-model="form.assigneeId" placeholder="请选择转办人" />
      </el-form-item>
      
      <el-form-item label="转办意见" prop="comment">
        <el-input
          v-model="form.comment"
          type="textarea"
          rows="3"
          placeholder="请输入转办意见（可选）"
        />
      </el-form-item>
    </el-form>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确定转办
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import UserSelector from '@/components/UserSelector.vue'

const props = defineProps({
  modelValue: Boolean,
  task: Object
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const formRef = ref()
const submitting = ref(false)

const form = reactive({
  assigneeId: '',
  comment: ''
})

const rules = {
  assigneeId: [{ required: true, message: '请选择转办人', trigger: 'change' }]
}

// 提交
const handleSubmit = async () => {
  await formRef.value.validate()
  
  submitting.value = true
  try {
    // 调用转办接口
    // await transferTask({
    //   taskId: props.task.taskId,
    //   assigneeId: form.assigneeId,
    //   comment: form.comment
    // })
    
    ElMessage.success('转办成功')
    emit('success')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message || '转办失败')
  } finally {
    submitting.value = false
  }
}
</script>
