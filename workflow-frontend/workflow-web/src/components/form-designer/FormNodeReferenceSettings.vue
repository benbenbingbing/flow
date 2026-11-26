<template>
  <el-form label-width="110px" size="small" @submit.prevent>
    <el-form-item label="引用类型">
      <el-select
        v-model="field.refEntityType"
        :disabled="Boolean(field.fieldId)"
        placeholder="选择引用类型"
        style="width: 100%"
      >
        <el-option label="用户自定义实体" value="CUSTOM" />
        <el-option label="系统用户" value="USER" />
        <el-option label="系统部门" value="DEPT" />
        <el-option label="系统角色" value="ROLE" />
        <el-option label="系统用户组" value="GROUP" />
        <el-option label="系统菜单" value="MENU" />
        <el-option label="系统字典" value="DICT" />
        <el-option label="系统字典项" value="DICT_ITEM" />
      </el-select>
      <div v-if="field.fieldId" class="form-tip">引用类型和目标实体继承自实体字段。</div>
    </el-form-item>
    <SettingsFormItem
      :disabled="!canMapParameters"
      disabled-reason="仅用户自定义实体引用支持此配置"
      label="目标实体"
    >
      <EntityDefinitionPicker
        class="reference-entity-picker"
        :model-value="field.refEntityId"
        :disabled="Boolean(field.fieldId)"
        placeholder="选择目标实体"
        value-key="id"
        title="选择目标实体"
        :query="{ status: 'PUBLISHED' }"
        @selected="changeEntity"
        @resolved="resolveEntity"
      />
    </SettingsFormItem>
    <SettingsFormItem
      :disabled="!canMapParameters"
      disabled-reason="仅用户自定义实体引用支持此配置"
      label="选择列表"
    >
      <el-select
        :model-value="field.refListKey"
        clearable
        placeholder="留空使用默认选择器"
        style="width: 100%"
        @update:model-value="changeList"
      >
        <el-option
          v-for="list in lists"
          :key="list.listKey"
          :label="`${list.listName || list.listKey} (${list.listKey})`"
          :value="list.listKey"
        />
      </el-select>
      <div class="form-tip">选中列表后，选择器使用该列表的列、查询范围、排序和选择配置。</div>
    </SettingsFormItem>
    <el-form-item
      v-if="canMapParameters && field.refListKey"
      label="列表参数映射"
      class="reference-list-parameter-item"
    >
      <el-alert v-if="parameterError" :title="parameterError" type="warning" :closable="false" />
      <div v-if="parameterLoading" class="form-tip">正在读取选择列表的已发布参数…</div>
      <PageParameterMappingEditor
        v-else
        :model-value="field.refListParameterMappings || []"
        :schema="parameterSchema"
        :source-fields="sourceFields"
        :entity-fields="sourceEntityFields"
        reference-list
        @update:model-value="field.refListParameterMappings = $event"
      />
    </el-form-item>
  </el-form>
</template>

<script setup>
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import PageParameterMappingEditor from '@/components/page-parameters/PageParameterMappingEditor.vue'
import SettingsFormItem from '@/components/SettingsFormItem.vue'

const props = defineProps({
  field: { type: Object, required: true },
  settings: { type: Object, required: true }
})

const {
  lists, canMapParameters, sourceFields, sourceEntityFields, parameterSchema, parameterLoading, parameterError,
  changeList, changeEntity, resolveEntity
} = props.settings
</script>

<style scoped>
.form-tip {
  width: 100%;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.reference-list-parameter-item :deep(.el-form-item__content) {
  min-width: 0;
  gap: 8px;
}

/* 保持小尺寸配置表单的控件高度，目标实体的名称和编码同排显示。 */
.reference-entity-picker :deep(.picker-trigger) {
  min-height: 24px;
  padding: 0 7px;
}

.reference-entity-picker :deep(.trigger-content) {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
</style>
