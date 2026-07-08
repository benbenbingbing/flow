<template>
  <el-card class="search-card">
    <el-form :model="form" inline>
      <!-- 默认显示前4个查询字段 -->
      <el-form-item v-for="field in visibleQueryFields" :key="field.fieldCode" :label="field.fieldName">
        <!-- BETWEEN 范围查询 -->
        <template v-if="field.queryType === 'BETWEEN' && useListConfig">
          <el-date-picker v-if="field.fieldType === 'DATE' || field.fieldType === 'DATETIME'"
            v-model="form[field.fieldCode + '_start']" type="date" :placeholder="`开始${field.fieldName}`" style="width: 140px" value-format="YYYY-MM-DD" />
          <el-input v-else v-model="form[field.fieldCode + '_start']" :placeholder="`开始${field.fieldName}`" style="width: 120px" />
          <span style="margin: 0 4px">~</span>
          <el-date-picker v-if="field.fieldType === 'DATE' || field.fieldType === 'DATETIME'"
            v-model="form[field.fieldCode + '_end']" type="date" :placeholder="`结束${field.fieldName}`" style="width: 140px" value-format="YYYY-MM-DD" />
          <el-input v-else v-model="form[field.fieldCode + '_end']" :placeholder="`结束${field.fieldName}`" style="width: 120px" />
        </template>
        <!-- 普通查询 -->
        <FormFieldRenderer
          v-else
          v-model="form[field.fieldCode]"
          :field="field"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <el-button v-if="fields.length > 4" link type="primary" @click="searchExpanded = !searchExpanded">
          <span>{{ searchExpanded ? '收起' : '展开' }}</span>
          <el-icon><ArrowUp v-if="searchExpanded" /><ArrowDown v-else /></el-icon>
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'

const props = defineProps<{
  fields: any[]
  useListConfig?: boolean
}>()

const form = defineModel<Record<string, any>>('form', { required: true })

const emit = defineEmits<{
  search: []
  reset: []
}>()

const searchExpanded = ref(false)

// 可见的查询字段（默认只显示前4个，展开后显示全部）
const visibleQueryFields = computed(() => {
  if (searchExpanded.value || props.fields.length <= 4) {
    return props.fields
  }
  return props.fields.slice(0, 4)
})

const onSearch = () => {
  emit('search')
}

const onReset = () => {
  emit('reset')
}
</script>

<style scoped lang="scss">
.search-card {
  margin-bottom: 10px;
}
</style>
