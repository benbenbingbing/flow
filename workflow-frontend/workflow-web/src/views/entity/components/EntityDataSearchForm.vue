<template>
  <el-card class="search-card">
    <el-form :model="form" inline :label-width="`${searchConfig.labelWidth || 100}px`">
      <el-form-item v-for="field in visibleQueryFields" :key="field.fieldCode" :label="field.fieldName">
        <!-- BETWEEN 范围查询：与单值查询共用字段渲染链路，查询组件覆盖、字典选项和 DATETIME 时分秒格式均一致生效 -->
        <template v-if="field.queryType === 'BETWEEN' && useListConfig">
          <div class="range-query-control">
            <FormFieldRenderer
              class="query-field-control"
              :form-data="form"
              mode="edit"
              @patch-form="applyQueryPatch"
              v-model="form[field.fieldCode + '_start']"
              :field="rangeEndField(field, 'start')"
            />
            <span>~</span>
            <FormFieldRenderer
              class="query-field-control"
              :form-data="form"
              mode="edit"
              @patch-form="applyQueryPatch"
              v-model="form[field.fieldCode + '_end']"
              :field="rangeEndField(field, 'end')"
            />
          </div>
        </template>
        <!-- 普通查询 -->
        <FormFieldRenderer
          v-else
          class="query-field-control"
              :form-data="form"
              mode="edit"
              @patch-form="applyQueryPatch"
          v-model="form[field.fieldCode]"
          :field="field"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <el-button
          v-if="searchConfig.collapsible !== false && fields.length > visibleCount"
          link
          type="primary"
          @click="searchExpanded = !searchExpanded"
        >
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
  viewConfig?: any
}>()

const form = defineModel<Record<string, any>>('form', { required: true })

const emit = defineEmits<{
  search: []
  reset: []
}>()

const searchExpanded = ref(false)

const searchConfig = computed(() => props.viewConfig?.search || {})
const visibleCount = computed(() => Math.max(1, Number(searchConfig.value.defaultVisibleCount) || 4))

// 收起时按配置数量展示查询字段，展开后显示全部。
const visibleQueryFields = computed(() => {
  if (searchExpanded.value || searchConfig.value.collapsible === false || props.fields.length <= visibleCount.value) {
    return props.fields
  }
  return props.fields.slice(0, visibleCount.value)
})

// 区间查询起止控件共享字段定义，仅覆盖占位提示；组件解析交给 FormFieldRenderer，保证与单值查询一致。
function rangeEndField(field: any, position: 'start' | 'end') {
  return {
    ...field,
    placeholder: `${position === 'start' ? '开始' : '结束'}${field.fieldName}`
  }
}

/** 查询组件只能改当前查询模型，不能回写业务记录或加入未知参数。 */
function applyQueryPatch(patch: Record<string, any>) {
  if (!patch || typeof patch !== 'object' || Array.isArray(patch)) return
  const keys = new Set(props.fields.flatMap(field => field.queryType === 'BETWEEN'
    ? [field.fieldCode + '_start', field.fieldCode + '_end'] : [field.fieldCode]))
  for (const [key, value] of Object.entries(patch)) {
    if (keys.has(key) && !['__proto__', 'constructor', 'prototype'].includes(key)) form.value[key] = value
  }
}

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

.query-field-control {
  width: 200px;
  min-width: 200px;
}

/* 区间查询起止两个控件横向排布，宽度与原先写死的日期选择器保持一致。 */
.range-query-control {
  display: flex;
  align-items: center;
  gap: 4px;
}

.range-query-control .form-field-renderer {
  width: 140px;
  min-width: 140px;
}

@media (max-width: 768px) {
  .search-card :deep(.el-form--inline .el-form-item) {
    display: flex;
    width: 100%;
    margin-right: 0;
  }

  .search-card :deep(.el-form--inline .el-form-item__content) {
    flex: 1;
    min-width: 0;
  }

  .query-field-control {
    width: 100%;
    min-width: 0;
  }
}
</style>
