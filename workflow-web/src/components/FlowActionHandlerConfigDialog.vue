<template>
  <el-dialog
    v-model="visible"
    title="流程动作处理器目录"
    width="1080px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-alert
      title="这里配置技术处理器的中文名称和可见范围。实体流程只能选择“全局”或明确包含当前实体的动作。"
      type="info"
      :closable="false"
      show-icon
      class="catalog-alert"
    />

    <el-table :data="rows" border size="small" v-loading="loading">
      <el-table-column label="处理器" min-width="210">
        <template #default="{ row }">
          <div class="handler-cell">
            <strong>{{ row.beanName }}</strong>
            <span>{{ shortClassName(row.className) }}</span>
            <el-tag v-if="!row.available" type="danger" size="small">未注册</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="中文名称" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.displayName" placeholder="如：发送待办通知" />
        </template>
      </el-table-column>
      <el-table-column label="用途说明" min-width="230">
        <template #default="{ row }">
          <el-input v-model="row.description" type="textarea" :rows="2" placeholder="说明执行内容和适用时机" />
        </template>
      </el-table-column>
      <el-table-column label="可见范围" width="130">
        <template #default="{ row }">
          <el-select v-model="row.visibilityScope" style="width: 100%">
            <el-option label="全部实体" value="GLOBAL" />
            <el-option label="指定实体" value="ENTITY" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="指定实体" min-width="230">
        <template #default="{ row }">
          <EntityDefinitionPicker
            v-model="row.entityCodes"
            multiple
            :disabled="row.visibilityScope !== 'ENTITY'"
            placeholder="选择可见实体"
            value-key="entityCode"
            value-case="lower"
            title="选择动作适用实体"
          />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" :disabled="!row.available" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :loading="savingBean === row.beanName" @click="saveRow(row)">
            保存
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { processActionApi } from '@/api/processAction'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'

const emit = defineEmits(['changed'])
const visible = ref(false)
const loading = ref(false)
const savingBean = ref('')
const rows = ref([])

async function open() {
  visible.value = true
  loading.value = true
  try {
    const handlerConfigs = await processActionApi.listHandlerConfigs()
    rows.value = (handlerConfigs || []).map(item => ({
      ...item,
      displayName: item.configured ? item.displayName : '',
      description: item.description || '',
      visibilityScope: item.visibilityScope || 'ENTITY',
      entityCodes: item.entityCodes || [],
      enabled: item.configured ? item.enabled !== false : false
    }))
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.message || '加载流程动作处理器目录失败')
  } finally {
    loading.value = false
  }
}

async function saveRow(row) {
  if (!row.displayName?.trim()) {
    ElMessage.warning('请填写动作中文名称')
    return
  }
  if (row.visibilityScope === 'ENTITY' && !row.entityCodes?.length) {
    ElMessage.warning('指定实体范围至少选择一个实体')
    return
  }
  savingBean.value = row.beanName
  try {
    const saved = await processActionApi.saveHandlerConfig(row.beanName, {
      displayName: row.displayName.trim(),
      description: row.description?.trim() || '',
      visibilityScope: row.visibilityScope,
      entityCodes: row.visibilityScope === 'ENTITY' ? row.entityCodes : [],
      enabled: row.enabled
    })
    Object.assign(row, saved)
    emit('changed')
    ElMessage.success('动作处理器配置已保存')
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.message || '保存处理器配置失败')
  } finally {
    savingBean.value = ''
  }
}

function shortClassName(value) {
  return value ? value.split('.').pop() : 'Bean 未注册'
}

defineExpose({ open })
</script>

<style scoped>
.catalog-alert {
  margin-bottom: 14px;
}

.handler-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.handler-cell span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
