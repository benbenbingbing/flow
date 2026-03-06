<template>
  <div class="form-field-renderer">
    <!-- 文本 -->
    <el-input
      v-if="field.fieldType === 'TEXT'"
      v-model="fieldValue"
      :placeholder="`请输入${field.fieldName}`"
      :disabled="disabled"
    />
    
    <!-- 多行文本 -->
    <el-input
      v-else-if="field.fieldType === 'TEXTAREA'"
      v-model="fieldValue"
      type="textarea"
      :rows="3"
      :placeholder="`请输入${field.fieldName}`"
      :disabled="disabled"
    />
    
    <!-- 数字 -->
    <el-input-number
      v-else-if="field.fieldType === 'NUMBER'"
      v-model="fieldValue"
      :placeholder="`请输入${field.fieldName}`"
      :disabled="disabled"
      style="width: 100%"
    />
    
    <!-- 日期 -->
    <el-date-picker
      v-else-if="field.fieldType === 'DATE'"
      v-model="fieldValue"
      type="date"
      :placeholder="`请选择${field.fieldName}`"
      :disabled="disabled"
      style="width: 100%"
    />
    
    <!-- 日期时间 -->
    <el-date-picker
      v-else-if="field.fieldType === 'DATETIME'"
      v-model="fieldValue"
      type="datetime"
      :placeholder="`请选择${field.fieldName}`"
      :disabled="disabled"
      style="width: 100%"
    />
    
    <!-- 下拉选择 -->
    <el-select
      v-else-if="field.fieldType === 'SELECT'"
      v-model="fieldValue"
      :placeholder="`请选择${field.fieldName}`"
      :disabled="disabled"
      style="width: 100%"
    >
      <el-option
        v-for="opt in options"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
    
    <!-- 单选 -->
    <el-radio-group
      v-else-if="field.fieldType === 'RADIO'"
      v-model="fieldValue"
      :disabled="disabled"
    >
      <el-radio
        v-for="opt in options"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-radio>
    </el-radio-group>
    
    <!-- 多选 -->
    <el-checkbox-group
      v-else-if="field.fieldType === 'CHECKBOX'"
      v-model="fieldValue"
      :disabled="disabled"
    >
      <el-checkbox
        v-for="opt in options"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-checkbox>
    </el-checkbox-group>
    
    <!-- 文件 -->
    <el-upload
      v-else-if="field.fieldType === 'FILE'"
      action="#"
      :disabled="disabled"
      :auto-upload="false"
    >
      <el-button :disabled="disabled">
        <el-icon><Upload /></el-icon>选择文件
      </el-button>
    </el-upload>
    
    <!-- 用户选择 -->
    <el-select
      v-else-if="field.fieldType === 'USER'"
      v-model="fieldValue"
      :placeholder="`请选择${field.fieldName}`"
      :disabled="disabled"
      filterable
      remote
      :remote-method="searchUsers"
      style="width: 100%"
    >
      <el-option
        v-for="user in userOptions"
        :key="user.value"
        :label="user.label"
        :value="user.value"
      />
    </el-select>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  modelValue: {
    type: [String, Number, Array, Date],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const fieldValue = computed({
  get() {
    if (props.field.fieldType === 'CHECKBOX' && !Array.isArray(props.modelValue)) {
      return props.modelValue ? [props.modelValue] : []
    }
    return props.modelValue
  },
  set(val) {
    emit('update:modelValue', val)
  }
})

const options = computed(() => {
  if (props.field.optionsJson) {
    try {
      return JSON.parse(props.field.optionsJson)
    } catch (e) {
      return []
    }
  }
  return []
})

const userOptions = ref([])

const searchUsers = (query) => {
  // 模拟用户搜索
  if (query) {
    userOptions.value = [
      { value: 'user1', label: `用户1 (${query})` },
      { value: 'user2', label: `用户2 (${query})` },
      { value: 'user3', label: `用户3 (${query})` }
    ]
  } else {
    userOptions.value = []
  }
}

// 设置默认值
watch(() => props.field.defaultValue, (val) => {
  if (val && !props.modelValue) {
    emit('update:modelValue', val)
  }
}, { immediate: true })
</script>

<style scoped>
.form-field-renderer {
  width: 100%;
}
</style>
