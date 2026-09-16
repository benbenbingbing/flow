<!--
  整列表最小展示模板：只复用宿主已读取的数据、可读列、行查看权限和分页。
  用于 EntityDataList；关联内容的 LIST 请用 RelatedContentTemplate，二者 runtime 不同。
  查询/新增/编辑等按 list.js 的 runtime 方法增补；列显示复用 ListCellRenderer。
-->
<template>
  <section>
    <h3>{{ config.title || entityName }}</h3>
    <el-button :loading="loading || tableLoading" @click="runtime.reload()">刷新</el-button>
    <el-table :data="dataList" v-loading="loading || tableLoading">
      <el-table-column
        v-for="field in listFields"
        :key="field.fieldCode || field.id"
        :label="field.fieldLabel || field.fieldName || field.fieldCode"
      >
        <template #default="{ row }">
          <ListCellRenderer
            :row="row"
            :field="field"
            :context="{ entityCode, entityDefinition, listConfig, entityStatusMap, refresh: runtime.reload }"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button
            link
            :disabled="!runtime.canAction(row, 'view')"
            :title="runtime.getActionReason(row, 'view')"
            @click="runtime.view(row)"
          >查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      :disabled="loading || tableLoading"
      :page-sizes="runtime.viewConfig?.pagination?.pageSizes || [10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="emit('sizeChange', $event)"
      @current-change="emit('pageChange', $event)"
    />
  </section>
</template>

<script setup>
import ListCellRenderer from '@/components/ListCellRenderer.vue'
import { customListProps, customListEmits } from '@/contracts/list.js'

defineProps(customListProps)
const emit = defineEmits(customListEmits)
</script>
