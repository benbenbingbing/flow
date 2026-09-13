<template>
  <el-pagination
    v-if="modelValue.total > 0"
    :current-page="modelValue.pageNum"
    :page-size="modelValue.pageSize"
    :page-sizes="pageSizes"
    :total="modelValue.total"
    layout="total, sizes, prev, pager, next, jumper"
    class="config-migration-pagination"
    @current-change="changePage"
    @size-change="changePageSize"
  />
</template>

<script setup>
import { CONFIG_MIGRATION_PAGE_SIZES } from '@/shared/config-migration-pagination'

const props = defineProps({
  modelValue: {
    type: Object,
    required: true
  },
  pageSizes: {
    type: Array,
    default: () => [...CONFIG_MIGRATION_PAGE_SIZES]
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

/** 翻页时保留当前每页条数，并通知服务端列表重新加载。 */
const changePage = (pageNum) => updatePage({ pageNum })

/** 修改每页条数后回到第一页，避免原页码超出新的总页数。 */
const changePageSize = (pageSize) => updatePage({ pageNum: 1, pageSize })

const updatePage = (changes) => {
  const page = { ...props.modelValue, ...changes }
  emit('update:modelValue', page)
  emit('change', page)
}
</script>

<style scoped>
.config-migration-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
