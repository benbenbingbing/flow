<template>
  <div class="user-selector">
    <el-select
      v-model="selectedValue"
      :placeholder="placeholder"
      clearable
      filterable
      remote
      :remote-method="searchUsers"
      :loading="loading"
      style="width: 100%"
    >
      <el-option
        v-for="user in userList"
        :key="user.id"
        :label="user.nickname || user.username"
        :value="user.id"
      >
        <div class="user-option">
          <el-avatar :size="24" :src="user.avatar" />
          <span class="user-name">{{ user.nickname || user.username }}</span>
          <span class="user-dept" v-if="user.deptName">({{ user.deptName }})</span>
        </div>
      </el-option>
    </el-select>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getUserList } from '@/api/system/user'

const props = defineProps({
  modelValue: String,
  placeholder: {
    type: String,
    default: '请选择用户'
  }
})

const emit = defineEmits(['update:modelValue'])

const selectedValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const userList = ref([])

// 搜索用户
const searchUsers = async (keyword) => {
  if (!keyword || keyword.length < 1) {
    userList.value = []
    return
  }
  
  loading.value = true
  try {
    const res = await getUserList({ keyword, pageSize: 20 })
    userList.value = res.records || []
  } catch (error) {
    console.error('搜索用户失败:', error)
  } finally {
    loading.value = false
  }
}

// 初始加载
watch(() => props.modelValue, async (val) => {
  if (val && !userList.value.length) {
    // 如果有值但列表为空，根据ID加载用户信息
    // 这里简化处理，实际可能需要根据ID查询用户详情
  }
}, { immediate: true })
</script>

<style scoped lang="scss">
.user-selector {
  .user-option {
    display: flex;
    align-items: center;
    gap: 8px;
    
    .user-name {
      flex: 1;
    }
    
    .user-dept {
      color: #909399;
      font-size: 12px;
    }
  }
}
</style>
