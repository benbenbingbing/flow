<script setup>
import RelatedContentPageSettings from './RelatedContentPageSettings.vue'
import RelatedContentLayoutSettings from './RelatedContentLayoutSettings.vue'
import RelatedContentVisibilitySettings from './RelatedContentVisibilitySettings.vue'
import SettingsSection from '../SettingsSection.vue'
import EntityDefinitionPicker from '../EntityDefinitionPicker.vue'
import StepHeading from './RelatedContentStepHeading.vue'
import FormContentDisplaySettings from '../form-designer/FormContentDisplaySettings.vue'
// 仅编辑父级唯一草稿；切换目标需要父级加载目录和规范化关联，不在子组件自动保存。
defineProps({
  editor: { type: Object, required: true },
  section: { type: String, default: 'all' },
  isRelationBound: Boolean,
  contentTypeOptions: { type: Array, default: () => [] },
  targetCatalogLoading: Boolean,
  targetContentOptions: { type: Array, default: () => [] },
  positionOptions: { type: Array, default: () => [] },
  ownerType: { type: String, required: true },
  availableAnchorOptions: { type: Array, default: () => [] },
  targetParameterSchema: { type: Object, default: () => ({}) },
  sourceReadableFieldOptions: { type: Array, default: () => [] }
})
const emit = defineEmits(['handleTargetEntitySelected', 'handleContentTypeChange', 'handleTargetContentChange', 'handlePositionChange', 'handleAnchorChange'])
</script>
<template>
  <component :is="section === 'all' ? 'section' : SettingsSection" :title="section === 'basic' ? '显示与布局' : '关联配置'" :default-expanded="true" primary>
    <StepHeading v-if="section === 'all'" number="1" title="显示什么" description="选择已发布的目标页面及展示位置。" />
    <slot name="relation-summary" />
    <el-form label-width="110px" class="related-form">
      <el-form-item v-if="section === 'all'" label="配置名称"><el-input v-model="editor.config.name" maxlength="80" /></el-form-item>
      <template v-if="['all', 'basic'].includes(section)">
        <FormContentDisplaySettings v-model="editor.config.presentation.title" v-model:show-title="editor.config.presentation.showTitle" :placeholder="editor.config.name || editor.config.target.contentName" />
        <el-form-item label="启用"><el-switch v-model="editor.config.enabled" /></el-form-item>
        <el-form-item :label="section === 'all' ? '显示位置' : '展示方式'" required>
          <el-select v-model="editor.config.presentation.position" style="width:100%" @change="emit('handlePositionChange', $event)">
            <el-option v-for="option in positionOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ownerType === 'FORM' && ['INLINE', 'TAB'].includes(editor.config.presentation.position)" label="放置位置">
          <el-select v-model="editor.anchorKey" clearable filterable :placeholder="editor.config.presentation.position === 'TAB' ? '选择已有 Tab 页' : '表单末尾'" style="width:100%" @change="emit('handleAnchorChange', $event)">
            <el-option v-for="option in availableAnchorOptions" :key="option.value" :value="option.value" :label="editor.config.presentation.anchorSide === 'BEFORE' ? option.label.replace(/之后/g, '之前') : option.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="顺序"><el-input-number v-model="editor.orderKey" :min="0" :max="2147483647" :step="1000000" /></el-form-item>
        <RelatedContentLayoutSettings :presentation="editor.config.presentation" />
        <RelatedContentVisibilitySettings :presentation="editor.config.presentation" :fields="sourceReadableFieldOptions.map(item => item.raw)" />
        <el-alert v-if="ownerType === 'LIST' && ['DIALOG', 'DRAWER', 'PAGE'].includes(editor.config.presentation.position)" type="info" :closable="false" title="入口按钮在列表的工具栏或操作列中配置。" />
      </template>
      <template v-if="['all', 'target'].includes(section)">
        <el-form-item label="目标实体" required>
          <span v-if="isRelationBound" class="inherited-target">{{ editor.config.target.entityName || editor.config.target.entityCode }}（由实体关系确定）</span>
          <EntityDefinitionPicker v-else v-model="editor.config.target.entityId" value-key="id" title="选择目标实体" :query="{ status: 'PUBLISHED' }" @selected="emit('handleTargetEntitySelected', $event)" />
        </el-form-item>
        <el-form-item :label="section === 'all' ? '显示内容' : '目标内容'" required>
          <div class="stacked-control">
            <span v-if="isRelationBound">{{ editor.config.target.contentType === 'FORM' ? '一对一：选择关联实体的表单' : '一对多：选择关联实体的列表' }}</span>
            <el-segmented v-if="!isRelationBound" v-model="editor.config.target.contentType" :options="contentTypeOptions" @change="emit('handleContentTypeChange', $event)" />
            <el-select v-model="editor.config.target.contentId" filterable :loading="targetCatalogLoading" :placeholder="editor.config.target.contentType === 'FORM' ? '选择已发布表单' : '选择已发布列表'" style="width:100%" @change="emit('handleTargetContentChange', $event)">
              <el-option v-for="option in targetContentOptions" :key="option.id" :label="option.displayName" :value="option.id" :disabled="option.published === false" />
            </el-select>
          </div>
        </el-form-item>
      </template>
    </el-form>
    <RelatedContentPageSettings v-if="section === 'all'" :editor="editor" :parameter-schema="targetParameterSchema" :source-fields="sourceReadableFieldOptions.map(item => item.raw)" />
  </component>
</template>
<style scoped>
.stacked-control { display:flex; flex-direction:column; gap:10px; width:100%; }
</style>
