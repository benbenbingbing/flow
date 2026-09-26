<script setup>
import PageParameterMappingEditor from '@/components/page-parameters/PageParameterMappingEditor.vue'
import ConfigHelpLabel from '../ConfigHelpLabel.vue'
import EntityDefinitionPicker from '../EntityDefinitionPicker.vue'
import StepHeading from './RelatedContentStepHeading.vue'
// 仅编辑父级唯一草稿；切换目标需要父级加载目录和规范化关联，不在子组件自动保存。
defineProps({
  editor: { type: Object, required: true },
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
        <section>
          <StepHeading
            number="1"
            title="显示什么"
            description="先选择要展示的数据类型和页面位置。目标必须已经发布。"
          />
          <el-form label-width="118px" class="related-form">
            <div class="two-column-form">
              <el-form-item>
                <template #label>
                  <ConfigHelpLabel
                    label="配置名称"
                    content="是什么：便于在设计器中识别这项关联内容。何时使用：同一页面配置多个关联内容时。结果：只影响设计态名称，不改变业务数据。"
                  />
                </template>
                <el-input
                  v-model="editor.config.name"
                  maxlength="80"
                  show-word-limit
                  placeholder="例如：所属项目详情"
                />
              </el-form-item>
              <el-form-item required>
                <template #label>
                  <ConfigHelpLabel
                    label="目标实体"
                    content="是什么：要查看或操作的数据类型。何时使用：例如从需求查看项目。结果：后续只显示该实体已发布的表单和列表。"
                  />
                </template>
                <span v-if="isRelationBound" class="inherited-target">{{ editor.config.target.entityName || editor.config.target.entityCode }}（由实体关系确定）</span>
                <EntityDefinitionPicker
                  v-else
                  v-model="editor.config.target.entityId"
                  value-key="id"
                  title="选择关联内容的目标实体"
                  :query="{ status: 'PUBLISHED' }"
                  @selected="emit('handleTargetEntitySelected', $event)"
                />
              </el-form-item>
              <el-form-item required>
                <template #label>
                  <ConfigHelpLabel
                    label="显示内容"
                    content="是什么：目标实体已经发布的表单或列表。何时使用：表单适合一条数据，列表适合多条数据。结果：运行时固定使用发布时确认的内容版本。"
                  />
                </template>
                <div class="stacked-control">
                  <span v-if="isRelationBound">{{ editor.config.target.contentType === 'FORM' ? '一对一：选择关联实体的表单' : '一对多：选择关联实体的列表' }}</span>
                  <el-segmented
                    v-else
                    v-model="editor.config.target.contentType"
                    :options="contentTypeOptions"
                    @change="emit('handleContentTypeChange', $event)"
                  />
                  <el-select
                    v-model="editor.config.target.contentId"
                    filterable
                    :loading="targetCatalogLoading"
                    :placeholder="editor.config.target.contentType === 'FORM' ? '选择已发布表单' : '选择已发布列表'"
                    style="width: 100%"
                    @change="emit('handleTargetContentChange', $event)"
                  >
                    <el-option
                      v-for="option in targetContentOptions"
                      :key="option.id"
                      :label="option.displayName"
                      :value="option.id"
                      :disabled="option.published === false"
                    >
                      <div class="business-option">
                        <span>{{ option.name }}</span>
                        <small>{{ option.key || '未提供编码' }} · {{ option.published === false ? '尚未发布' : '已发布' }}</small>
                      </div>
                    </el-option>
                  </el-select>
                </div>
              </el-form-item>
              <el-form-item required>
                <template #label>
                  <ConfigHelpLabel
                    label="显示位置"
                    content="是什么：关联内容在页面中的打开方式。何时使用：高频内容建议嵌入或 Tab，临时查看建议弹窗或抽屉。结果：只改变呈现方式，不扩大数据权限。"
                  />
                </template>
                <el-select
                  v-model="editor.config.presentation.position"
                  style="width: 100%"
                  @change="emit('handlePositionChange', $event)"
                >
                  <el-option
                    v-for="option in positionOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  >
                    <div class="business-option">
                      <span>{{ option.label }}</span>
                      <small>{{ option.description }}</small>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="ownerType === 'FORM' && ['INLINE', 'TAB'].includes(editor.config.presentation.position)"
              >
                <template #label>
                  <ConfigHelpLabel
                    label="放置位置"
                    content="是什么：关联内容插入到当前表单的哪个节点之后。何时使用：需要把内容放在某个区块或字段附近时。结果：不选择时放在表单末尾。"
                  />
                </template>
                <el-select
                  v-model="editor.anchorKey"
                  clearable
                  filterable
                  :placeholder="editor.config.presentation.position === 'TAB'
                    ? '选择已有 Tab 页'
                    : '表单末尾（推荐）'"
                  style="width: 100%"
                  @change="emit('handleAnchorChange', $event)"
                >
                  <el-option
                    v-if="editor.config.presentation.position === 'INLINE'"
                    label="表单末尾（推荐）"
                    value=""
                  />
                  <el-option
                    v-for="option in availableAnchorOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="ownerType === 'LIST' && ['DIALOG', 'DRAWER', 'PAGE'].includes(editor.config.presentation.position)"
              >
                <template #label>
                  <span>按钮入口</span>
                </template>
                <el-alert type="info" :closable="false" title="保存后，到“工具栏按钮”或“操作列按钮”中添加自定义按钮，执行方式选择“打开关联内容”。" />
              </el-form-item>
              <el-form-item required>
                <template #label>
                  <ConfigHelpLabel
                    label="加载方式"
                    content="是什么：何时请求目标内容。何时使用：少量高频内容可立即加载；列表和复杂页面建议按需加载。结果：按需加载可缩短当前页面首次打开时间。"
                  />
                </template>
                <el-radio-group v-model="editor.config.presentation.loadMode">
                  <el-radio value="ON_DEMAND">按需加载（推荐）</el-radio>
                  <el-radio value="IMMEDIATE">立即加载</el-radio>
                </el-radio-group>
              </el-form-item>
            </div>
          </el-form>
          <PageParameterMappingEditor v-model="editor.config.parameterMappings" :schema="targetParameterSchema" :source-fields="sourceReadableFieldOptions.map(item => item.raw)" />
        </section>
</template>
<style scoped>
.inherited-target { overflow-wrap: anywhere; }
.two-column-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.stacked-control { display: flex; width: 100%; flex-direction: column; gap: 10px; }
.business-option { display: flex; min-width: 0; flex-direction: column; line-height: 1.35; }
.business-option small { overflow: hidden; color: var(--el-text-color-secondary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 820px) { .two-column-form { grid-template-columns: 1fr; } }
</style>
