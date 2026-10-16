<template>
  <el-form label-width="110px">
    <el-form-item label="执行方式">
      <el-select v-model="button.customMode" @change="button.execution = {}">
        <el-option v-for="item in FORM_CUSTOM_ACTION_MODES" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="button.customMode === 'handler'" label="函数名称">
      <el-input v-model="button.execution.name" placeholder="已注册的动作函数名称" />
      <span>接收当前表单数据与记录上下文。</span>
    </el-form-item>
    <el-form-item v-if="button.customMode === 'component'" label="组件">
      <el-select v-model="button.execution.name" filterable @change="selectComponent">
        <el-option v-for="item in components" :key="item.name" :value="item.name" :label="item.label || item.name" />
      </el-select>
    </el-form-item>
    <template v-if="['open-form', 'open-list'].includes(button.customMode)">
      <el-form-item label="目标实体">
        <EntityDefinitionPicker v-model="button.execution.entityId" value-key="id" :query="{status: 'PUBLISHED'}" @selected="selectEntity" />
      </el-form-item>
      <el-form-item :label="button.customMode === 'open-form' ? '目标表单' : '目标列表'">
        <el-select v-if="button.customMode === 'open-form'" v-model="button.execution.formId" filterable :loading="loading">
          <el-option v-for="item in options" :key="item.id" :value="String(item.id)" :label="item.formName || item.name" />
        </el-select>
        <el-select v-else v-model="button.execution.listKey" filterable :loading="loading">
          <el-option v-for="item in options" :key="item.id" :value="item.listKey || item.configKey" :label="item.configName || item.listName || item.name" />
        </el-select>
      </el-form-item>
    </template>
    <template v-if="button.customMode === 'open-form'">
      <el-form-item label="打开模式">
        <el-select v-model="button.execution.formMode">
          <el-option label="新增记录" value="create" /><el-option label="编辑记录" value="edit" /><el-option label="查看记录" value="view" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="['edit','view'].includes(button.execution.formMode)" label="记录来源">
        <el-select v-model="button.execution.recordIdField" clearable placeholder="当前记录（同一实体）">
          <el-option v-for="field in fields" :key="field.fieldCode" :value="field.fieldCode" :label="field.fieldName || field.fieldCode" />
        </el-select>
      </el-form-item>
    </template>
    <el-form-item v-if="button.customMode === 'open-related-content'" label="关联内容">
      <el-select v-model="button.execution.compositionKey" filterable placeholder="选择已配置的弹窗、抽屉或全屏关联内容">
        <el-option v-for="item in overlayContents" :key="item.compositionKey" :value="item.compositionKey" :label="item.config.name || item.compositionKey" />
      </el-select>
      <span>沿用该关联的目标、关系和打开方式；先保存主表记录。</span>
    </el-form-item>
    <el-form-item v-if="button.customMode === 'open-list'" label="打开方式">
      <el-select v-model="button.execution.presentation">
        <el-option label="弹窗" value="DIALOG" /><el-option label="抽屉" value="DRAWER" />
      </el-select>
    </el-form-item>
    <el-alert v-if="button.customMode === 'event'" type="info" :closable="false" title="保存按钮后，点击表格中的“事件链”配置业务接口与执行步骤。" />
  </el-form>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import EntityDefinitionPicker from './EntityDefinitionPicker.vue'
import { FORM_CUSTOM_ACTION_MODES } from '@flow/workflow-core/form-actions'
import { getFormsByEntity } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { getCustomFormComponentOptions } from '@/extensions/core/registries/customComponentRegistry'
const props = defineProps({ button: { type: Object, required: true }, fields: { type: Array, default: () => [] }, relatedContents: { type: Array, default: () => [] } })
const components = getCustomFormComponentOptions()
const options = ref([]), loading = ref(false)
const overlayContents = computed(() => props.relatedContents.filter(item => item.config?.enabled !== false && ['DIALOG','DRAWER','PAGE'].includes(item.config?.presentation?.position)))
let sequence = 0
// 切换实体后的旧目录请求不能覆盖新目标，也不自动选中尚未发布的页面。
watch(() => [props.button.customMode, props.button.execution.entityId], async ([mode, id]) => {
  const request = ++sequence
  options.value = []
  if (!id || !['open-form','open-list'].includes(mode)) { loading.value = false; return }
  loading.value = true
  try {
    const result = mode === 'open-form' ? await getFormsByEntity(id) : await entityListConfigApi.getByEntityId(id)
    if (request !== sequence) return
    options.value = (Array.isArray(result) ? result : result?.records || []).filter(item => item.activeReleaseId || Number(item.publishedVersion) > 0 || item.status === 'PUBLISHED')
  } catch (error) { if (request === sequence) ElMessage.error(error.message || '加载目标页面失败') }
  finally { if (request === sequence) loading.value = false }
}, { immediate: true })
function selectEntity(entity) {
  Object.assign(props.button.execution, { entityId: String(entity.id), entityCode: entity.entityCode, formId: '', listKey: '', formMode: 'create', presentation: 'DIALOG' })
}
function selectComponent(name) {
  const option = components.find(item => item.name === name)
  props.button.execution.componentVersion = String(option?.version || '')
  props.button.execution.artifactDigest = String(option?.artifactDigest || '')
}
</script>
