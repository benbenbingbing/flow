<template>
  <el-dialog
    v-model="visible"
    title="报表预览"
    width="90%"
    :close-on-click-modal="false"
    top="5vh"
    class="report-preview-dialog"
  >
    <div v-if="report" class="report-preview">
      <!-- 报表标题 -->
      <div class="report-title">
        <h3>{{ layoutConfig.title || report.reportName }}</h3>
      </div>
      
      <!-- 数据表格 -->
      <el-table :data="tableData" v-loading="loading" stripe border height="500">
        <el-table-column 
          v-for="col in columns" 
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
        />
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getReportConfig, getReportData } from '@/api/report-engine'

const props = defineProps({
  modelValue: Boolean,
  reportId: String
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const report = ref(null)
const layoutConfig = ref({})
const tableData = ref([])
const columns = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(50)
const total = ref(0)

// 加载报表数据
const loadReportData = async () => {
  if (!props.reportId) return
  
  loading.value = true
  try {
    // 加载报表配置
    const configRes = await getReportConfig(props.reportId)
    report.value = configRes.report
    
    if (configRes.report.layoutConfig) {
      layoutConfig.value = JSON.parse(configRes.report.layoutConfig)
    }
    
    // 加载数据
    const dataRes = await getReportData(props.reportId, {})
    const datasetCode = Object.keys(dataRes)[0]
    const data = dataRes[datasetCode] || []
    
    tableData.value = data
    total.value = data.length
    
    // 生成列配置
    if (data.length > 0) {
      columns.value = Object.keys(data[0]).map(key => ({
        prop: key,
        label: key,
        width: 150
      }))
    }
  } catch (error) {
    console.error('加载报表数据失败:', error)
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (val) => {
  if (val && props.reportId) {
    loadReportData()
  }
})
</script>

<style scoped lang="scss">
.report-preview {
  .report-title {
    text-align: center;
    margin-bottom: 20px;
    
    h3 {
      margin: 0;
      font-size: 20px;
    }
  }
  
  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
