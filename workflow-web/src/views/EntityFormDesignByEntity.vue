<template>
  <div class="entity-form-design">
    <div class="design-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="title">表单设计 - {{ form.formName || '新建表单' }}</span>
      </div>
      <div class="header-right">
        <el-tag :type="diffInfo.changed ? 'warning' : 'success'" effect="plain">
          {{ diffInfo.changed ? '草稿有未发布修改' : '已与发布版本一致' }}
        </el-tag>
        <el-button @click="showPreview = true">
          <el-icon><View /></el-icon>预览
        </el-button>
        <el-button @click="showReleaseHistory">版本</el-button>
        <el-button @click="extensionManagerVisible = true">扩展清单</el-button>
        <el-button type="success" plain @click="handlePublish" :disabled="!isEdit">
          发布
        </el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          <el-icon><Check /></el-icon>保存草稿
        </el-button>
      </div>
    </div>

    <div class="design-body">
      <!-- 左侧：实体字段 -->
      <div class="field-panel">
        <div class="panel-title">实体字段</div>
        <div class="field-search">
          <el-input v-model="fieldSearch" placeholder="搜索字段" size="small" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div class="field-list">
          <div
            v-for="field in filteredEntityFields"
            :key="field.id"
            class="field-item"
            :class="{ disabled: isFieldInForm(field) }"
            @click="addField(field)"
          >
            <el-icon><Document /></el-icon>
            <div class="field-info">
              <div class="field-name">{{ field.fieldName }}</div>
              <div class="field-code">{{ field.fieldCode }}</div>
            </div>
            <div class="field-tags">
              <el-tag v-if="isFieldInForm(field)" type="info" size="small" class="added-tag">已添加</el-tag>
              <el-tag size="small" class="type-tag">{{ field.fieldType }}</el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 中间：表单画布 -->
      <div class="canvas-panel">
        <div class="panel-title">
          <span>表单设计（所见即所得）</span>
          <div class="layout-selector">
            <el-radio-group v-model="form.layoutType" size="small">
              <el-radio-button label="vertical">垂直</el-radio-button>
              <el-radio-button label="horizontal">水平</el-radio-button>
              <el-radio-button label="grid">网格</el-radio-button>
            </el-radio-group>
            <el-button type="primary" size="small" style="margin-left: 12px" @click="addSection">
              <el-icon><Plus /></el-icon>添加节
            </el-button>
            <el-dropdown trigger="click" @command="addContainerNode">
              <el-button size="small" style="margin-left: 8px">
                添加容器节点
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="GRID">栅格</el-dropdown-item>
                  <el-dropdown-item command="TAB_SET">Tab 集合</el-dropdown-item>
                  <el-dropdown-item command="TAB">Tab 页</el-dropdown-item>
                  <el-dropdown-item command="COLLAPSE">折叠面板</el-dropdown-item>
                  <el-dropdown-item command="TEXT">说明文本</el-dropdown-item>
                  <el-dropdown-item command="REPEATER">明细表</el-dropdown-item>
                  <el-dropdown-item command="ACTION_SLOT">动作插槽</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
        
        <!-- 表单基本信息 -->
        <div class="form-basic-info">
          <el-form inline size="small">
            <el-form-item label="表单名称">
              <el-input v-model="form.formName" placeholder="请输入表单名称" style="width: 200px" />
            </el-form-item>
            <el-form-item label="表单标识">
              <el-input
                v-model="form.formKey"
                placeholder="表单标识"
                :disabled="isEdit"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="自定义组件">
              <el-select
                v-model="form.customComponent"
                placeholder="留空使用默认动态表单"
                filterable
                allow-create
                clearable
                style="width: 260px"
              >
                <el-option
                  v-for="option in customFormOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.customComponent" label="组件版本">
              <el-tag>
                v{{ form.customComponentVersion || 1 }}
                / 快照 v{{ form.customComponentSnapshotVersion || 1 }}
              </el-tag>
            </el-form-item>
            <el-form-item label="标签宽度">
              <el-input-number v-model="viewConfig.labelWidth" :min="60" :max="240" />
            </el-form-item>
            <el-form-item v-if="selectedCustomFormSchema.length" label="组件参数">
              <el-button @click="showFormExtensionConfig = true">配置参数</el-button>
            </el-form-item>
            <el-form-item label="表单数据源">
              <el-button
                :disabled="!form.id"
                @click="openFormDataSourceConfig"
              >
                配置绑定
              </el-button>
              <el-tag
                v-if="formDataSourceBindingCount"
                type="success"
                style="margin-left: 8px"
              >
                {{ formDataSourceBindingCount }} 项
              </el-tag>
            </el-form-item>
          </el-form>
        </div>

        <!-- 表单画布 - 所见即所得 -->
        <div class="form-canvas-wrapper">
          <div class="form-canvas" :class="form.layoutType">
            <div v-if="formFields.length === 0" class="empty-tip">
              <el-empty description="点击左侧字段添加到表单">
                <template #image>
                  <el-icon :size="60" color="#dcdfe6"><DocumentAdd /></el-icon>
                </template>
              </el-empty>
            </div>
            
            <!-- 使用 el-form 包裹，与预览保持一致 -->
            <el-form v-else :label-width="formLabelWidth" :label-position="formLabelPosition" class="design-form">
              <FormNodeDesignItem
                v-for="(field, index) in rootDesignNodes"
                :key="field.id"
                :node="field"
                :sibling-index="index"
                :sibling-count="rootDesignNodes.length"
                :selected-node-id="selectedField?.id"
                :layout-type="form.layoutType"
                :children-for="designChildrenFor"
                :grid-style-for="getGridStyle"
                :is-section-node="isSectionField"
                :is-tab-sub-form="isTabSubForm"
                :legacy-node-type="legacyNodeType"
                :node-label="nodeLabel"
                @select="selectField"
                @move="moveNode"
                @remove="removeNode"
              />

              <!-- Tab 子表单预览 -->
              <div v-if="formFields.filter(f => isTabSubForm(f)).length > 0" class="tab-subforms-preview" style="width: 100%; margin-top: 16px;">
                <el-tabs v-model="activeDesignTab" type="border-card">
                  <el-tab-pane
                    v-for="field in formFields.filter(f => isTabSubForm(f))"
                    :key="field.id || field.fieldId"
                    :label="field.fieldLabel || field.fieldName"
                    :name="field.fieldCode || field.fieldId || field.id"
                  >
                    <div 
                      @click="selectField(field)" 
                      class="tab-form-field-wrapper"
                      :class="{ active: selectedField?.id === field.id }"
                    >
                      <FormFieldRenderer :field="field" :disabled="true" />
                    </div>
                  </el-tab-pane>
                </el-tabs>
              </div>
            </el-form>
          </div>
        </div>
      </div>

      <!-- 右侧：属性配置 -->
      <div class="property-panel">
        <div class="panel-title">属性配置</div>
        
        <template v-if="selectedField">
          <!-- 添加联动配置按钮 -->
          <div class="linkage-config-header">
            <el-button
              type="success"
              size="small"
              :loading="savingNode"
              @click="saveSelectedNode"
            >
              保存当前节点
            </el-button>
            <el-button type="primary" size="small" @click="showLinkageConfig = true">
              <el-icon><Connection /></el-icon> 字段联动配置
            </el-button>
          </div>
          
          <el-scrollbar height="calc(100vh - 180px)">
            <el-form label-width="90px" size="small" class="property-form">
              <el-form-item label="节点类型">
                <el-select v-model="selectedField.nodeType" style="width: 100%">
                  <el-option
                    v-for="type in nodeTypeOptions"
                    :key="type.value"
                    :label="type.label"
                    :value="type.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="父容器">
                <el-select
                  v-model="selectedField.parentId"
                  clearable
                  placeholder="根节点"
                  style="width: 100%"
                >
                  <el-option
                    v-for="option in availableParentNodes"
                    :key="option.id"
                    :label="option.label"
                    :value="option.id"
                  />
                </el-select>
                <div class="form-tip">递归嵌套最多 8 层，保存时校验循环引用。</div>
              </el-form-item>
              <el-form-item label="节点扩展">
                <el-select
                  v-model="selectedField.componentName"
                  clearable
                  filterable
                  placeholder="使用内置节点"
                  style="width: 100%"
                  @change="handleNodeExtensionChange"
                >
                  <el-option
                    v-for="option in availableNodeExtensionOptions"
                    :key="option.value"
                    :label="`${option.label} (v${option.version})`"
                    :value="option.value"
                  />
                </el-select>
                <div v-if="selectedField.componentName" class="form-tip">
                  锁定实现 v{{ selectedField.componentVersion || 1 }}，
                  配置快照 v{{ selectedField.snapshotVersion || 1 }}
                </div>
              </el-form-item>
              <el-form-item :label="isSelectedSection ? '节标题' : '字段名称'">
                <el-input v-model="selectedField.fieldName" :disabled="!isSelectedSection" />
              </el-form-item>
              <el-form-item label="显示标签">
                <el-input v-model="selectedField.fieldLabel" />
              </el-form-item>
              <template v-if="!isSelectedSection">
                <el-form-item label="组件类型">
                  <el-select v-model="selectedField.componentType" style="width: 100%">
                    <el-option
                      v-for="option in availableFormFieldComponentOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="属性">
                  <div class="checkbox-group">
                    <el-checkbox v-model="selectedField.isRequired" :true-label="1" :false-label="0">必填</el-checkbox>
                    <el-checkbox v-model="selectedField.isReadonly" :true-label="1" :false-label="0">只读</el-checkbox>
                    <el-checkbox v-model="selectedField.isHidden" :true-label="1" :false-label="0">隐藏</el-checkbox>
                  </div>
                </el-form-item>
                <el-form-item label="默认值">
                  <el-input v-model="selectedField.defaultValue" placeholder="默认值" />
                </el-form-item>
                <el-form-item label="占位提示">
                  <el-input v-model="selectedField.placeholder" placeholder="提示文字" />
                </el-form-item>

                <el-divider>统一数据源</el-divider>
                <el-form-item label="绑定位置">
                  <el-select v-model="selectedField.dataSourceUsage" style="width: 100%">
                    <el-option
                      v-for="usage in formDataSourceUsages"
                      :key="usage.value"
                      :label="usage.label"
                      :value="usage.value"
                    />
                  </el-select>
                </el-form-item>

                <el-divider>组件模板</el-divider>
                <el-form-item label="锁定模板">
                  <el-select
                    v-model="selectedField.templateId"
                    clearable
                    filterable
                    placeholder="复制后独立"
                    style="width: 100%"
                    @change="handleTemplateChange"
                  >
                    <el-option
                      v-for="template in componentTemplates"
                      :key="template.id"
                      :label="`${template.templateName} (v${template.currentVersion})`"
                      :value="template.id"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="selectedField.templateId" label="模板版本">
                  <el-tag>v{{ selectedField.templateVersion || 1 }}</el-tag>
                  <el-button
                    link
                    type="primary"
                    style="margin-left: 8px"
                    @click="upgradeSelectedTemplate"
                  >检查升级</el-button>
                </el-form-item>
                <el-form-item label="数据源">
                  <el-select
                    v-model="selectedField.dataSourceId"
                    clearable
                    filterable
                    placeholder="不绑定"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="source in dataSources"
                      :key="source.id"
                      :label="`${source.sourceName} (${source.sourceType})`"
                      :value="source.id"
                    />
                  </el-select>
                  <div class="form-tip">仅可选择受控实体、字典、Provider 或 Connector。</div>
                </el-form-item>
                <el-form-item label="输入映射">
                  <el-input
                    v-model="selectedField.dataSourceInputMappingText"
                    type="textarea"
                    :rows="3"
                    placeholder='{"filters.ownerId":"data.ownerId"}'
                  />
                  <div class="form-tip">目标路径映射到 data/context/input 路径；也可使用 {"literal": 值}。</div>
                </el-form-item>
                <el-form-item label="输出映射">
                  <el-input
                    v-model="selectedField.dataSourceOutputMappingText"
                    type="textarea"
                    :rows="3"
                    placeholder='{"assigneeName":"data.user.name"}'
                  />
                  <div class="form-tip">目标字段映射到数据源返回路径；留空时使用原始返回值。</div>
                </el-form-item>

                <template v-if="selectedComponentSchema.length">
                  <el-divider>组件参数</el-divider>
                  <ConfigSchemaEditor
                    v-model="selectedComponentConfig"
                    :schema="selectedComponentSchema"
                  />
                </template>

                <el-divider>结构化校验</el-divider>
                <el-form-item label="最小长度">
                  <el-input-number
                    :model-value="selectedValidationConfig.minLength"
                    :min="0"
                    :max="20000"
                    @update:model-value="updateValidationConfig('minLength', $event)"
                  />
                </el-form-item>
                <el-form-item label="最大长度">
                  <el-input-number
                    :model-value="selectedValidationConfig.maxLength"
                    :min="0"
                    :max="20000"
                    @update:model-value="updateValidationConfig('maxLength', $event)"
                  />
                </el-form-item>
                <el-form-item label="最小值">
                  <el-input-number
                    :model-value="selectedValidationConfig.min"
                    @update:model-value="updateValidationConfig('min', $event)"
                  />
                </el-form-item>
                <el-form-item label="最大值">
                  <el-input-number
                    :model-value="selectedValidationConfig.max"
                    @update:model-value="updateValidationConfig('max', $event)"
                  />
                </el-form-item>
                <el-form-item label="格式">
                  <el-select
                    :model-value="selectedValidationConfig.format || ''"
                    clearable
                    style="width: 100%"
                    @update:model-value="updateValidationConfig('format', $event)"
                  >
                    <el-option label="邮箱" value="EMAIL" />
                    <el-option label="手机号" value="PHONE" />
                    <el-option label="URL" value="URL" />
                  </el-select>
                </el-form-item>

                <el-divider>运行模式权限</el-divider>
                <div class="mode-access-grid">
                  <div v-for="modeOption in modeOptions" :key="modeOption.value" class="mode-access-row">
                    <span>{{ modeOption.label }}</span>
                    <el-checkbox
                      :model-value="getModeAccessValue(modeOption.value, 'visible')"
                      @change="updateModeAccess(modeOption.value, 'visible', $event)"
                    >显示</el-checkbox>
                    <el-checkbox
                      :model-value="getModeAccessValue(modeOption.value, 'editable')"
                      @change="updateModeAccess(modeOption.value, 'editable', $event)"
                    >可编辑</el-checkbox>
                  </div>
                </div>
              </template>
              <el-form-item label="栅格宽度" v-if="form.layoutType === 'grid'">
                <el-slider v-model="selectedField.gridSpan" :min="1" :max="24" show-stops />
                <span class="slider-value">{{ selectedField.gridSpan }}/24</span>
              </el-form-item>
              
              <!-- 子表单特殊配置 -->
              <template v-if="isSubFormField(selectedField)">
                <el-divider>子表单</el-divider>

                <div class="relation-summary">
                  <div>
                    <span>子实体</span>
                    <strong>{{ getEntityNameById(selectedField.childEntityId || selectedField.refEntityId) || '-' }}</strong>
                  </div>
                  <div>
                    <span>关系</span>
                    <strong>{{ selectedField.relationType === 'ONE_TO_ONE' ? '一对一' : '一对多' }}</strong>
                  </div>
                  <div>
                    <span>外键</span>
                    <strong>{{ selectedField.childRefFieldCode || selectedField.refFieldCode || '-' }}</strong>
                  </div>
                </div>

                <el-form-item label="显示">
                  <el-radio-group v-model="selectedField.displayMode">
                    <el-radio-button label="embedded">嵌入</el-radio-button>
                    <el-radio-button label="tab">页签</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <el-form-item label="布局">
                  <el-radio-group v-model="selectedField.layout">
                    <el-radio-button label="form">分行</el-radio-button>
                    <el-radio-button label="table">表格</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <el-form-item label="子表表单">
                  <el-select
                    v-model="selectedField.refFormId"
                    placeholder="默认表单"
                    clearable
                    style="width: 100%"
                    @change="handleChildFormChange"
                  >
                    <el-option
                      v-for="fm in formListByEntity"
                      :key="fm.id"
                      :label="fm.formName"
                      :value="fm.id"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="发布版本">
                  <el-select
                    v-model="selectedField.childFormReleaseId"
                    placeholder="选择已发布版本"
                    clearable
                    filterable
                    :disabled="!selectedField.refFormId"
                    :loading="childFormReleaseLoading"
                    style="width: 100%"
                    @change="handleChildFormReleaseChange"
                  >
                    <el-option
                      v-for="release in childFormReleases"
                      :key="release.id"
                      :label="formatChildFormReleaseLabel(release)"
                      :value="release.id"
                    />
                  </el-select>
                  <div class="form-tip">
                    运行时固定读取所选 release 快照；子表单草稿不会影响已发布父表单。
                  </div>
                </el-form-item>
              </template>

              <!-- 实体引用字段配置 -->
              <template v-if="(selectedField.componentType || '').toUpperCase() === 'REFERENCE' || (selectedField.componentType || '').toUpperCase() === 'MULTI_REFERENCE'">
                <el-divider>实体引用配置</el-divider>
                <el-form-item label="引用类型">
                  <el-select v-model="selectedField.refEntityType" :disabled="!!selectedField.fieldId" placeholder="选择引用类型" style="width: 100%">
                    <el-option label="用户自定义实体" value="CUSTOM" />
                    <el-option label="系统用户" value="USER" />
                    <el-option label="系统部门" value="DEPT" />
                    <el-option label="系统角色" value="ROLE" />
                    <el-option label="系统用户组" value="GROUP" />
                  </el-select>
                </el-form-item>
                <el-form-item label="关联实体" v-if="(selectedField.refEntityType || '').toUpperCase() === 'CUSTOM'">
                  <el-select
                    v-model="selectedField.refEntityId"
                    :disabled="!!selectedField.fieldId"
                    placeholder="选择关联实体"
                    style="width: 100%"
                    @change="handleReferenceEntityChange"
                  >
                    <el-option
                      v-for="ent in entityList"
                      :key="ent.id"
                      :label="ent.entityName"
                      :value="ent.id"
                    />
                  </el-select>
                  <div v-if="selectedField.refEntityId" class="form-tip">
                    当前关联：{{ getEntityNameById(selectedField.refEntityId) }}
                  </div>
                </el-form-item>
                <el-form-item label="选择列表" v-if="(selectedField.refEntityType || '').toUpperCase() === 'CUSTOM'">
                  <el-select
                    v-model="selectedField.refListKey"
                    clearable
                    placeholder="留空使用旧选择器"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="list in referenceListOptions"
                      :key="list.listKey"
                      :label="`${list.listName || list.listKey} (${list.listKey})`"
                      :value="list.listKey"
                    />
                  </el-select>
                  <div class="form-tip">配置后使用统一列表运行时，字段、范围、排序和选择模式均继承该 listKey。</div>
                </el-form-item>
                <el-form-item label="数据接口">
                  <el-input model-value="请使用上方统一数据源" disabled />
                  <div class="form-tip">不再允许配置任意 URL，外部调用必须引用受控 Connector。</div>
                </el-form-item>
              </template>

              <!-- 字段事件配置 -->
              <template v-if="selectedField">
                <el-divider>事件配置</el-divider>
                <el-form-item>
                  <el-button type="primary" text @click="openEventConfig">
                    <el-icon><Edit /></el-icon>
                    配置字段事件
                  </el-button>
                  <el-tag v-if="hasEventConfig" type="success" size="small" style="margin-left: 8px">已配置</el-tag>
                </el-form-item>
              </template>
            </el-form>
          </el-scrollbar>
        </template>
        
        <div v-else class="empty-property">
          <el-empty description="点击字段进行配置">
            <template #image>
              <el-icon :size="48" color="#dcdfe6"><Edit /></el-icon>
            </template>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- 预览弹窗 - 所见即所得 -->
    <el-dialog v-model="showPreview" title="表单预览" width="800px" destroy-on-close>
      <div class="preview-container">
        <FormPreviewLinkage :form="previewForm" />
      </div>
    </el-dialog>
    
    <!-- 联动配置弹窗 -->
    <el-dialog
      v-model="showLinkageConfig"
      title="字段联动配置"
      width="700px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <LinkageConfigPanel
        v-if="selectedField"
        :field="selectedField"
        :all-fields="entityFields.filter(f => !f.isSystem)"
        @save="handleSaveLinkage"
      />
    </el-dialog>

    <!-- 事件配置弹窗 -->
    <EventConfigPanel
      v-model:visible="showEventConfig"
      :model-value="currentEventValues"
      @save="handleSaveEvent"
    />

    <el-dialog v-model="showFormExtensionConfig" title="自定义表单组件参数" width="640px">
      <ConfigSchemaEditor
        v-model="viewConfig.customComponentProps"
        :schema="selectedCustomFormSchema"
      />
      <template #footer>
        <el-button type="primary" @click="showFormExtensionConfig = false">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="formDataSourceDialogVisible"
      title="表单级统一数据源"
      width="920px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="form-data-source-toolbar">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="FORM_INIT 用于初始化整表数据；AFTER_LOAD 用于加载后处理；BEFORE_SUBMIT 默认只在后端执行。"
        />
        <el-button type="primary" plain @click="addFormDataSourceBinding">
          添加绑定
        </el-button>
      </div>
      <el-empty
        v-if="formDataSourceRows.length === 0"
        description="尚未配置表单级数据源"
      />
      <div
        v-for="(binding, index) in formDataSourceRows"
        :key="binding.rowKey"
        class="form-data-source-row"
      >
        <div class="form-data-source-row-header">
          <strong>绑定 {{ index + 1 }}</strong>
          <el-button
            link
            type="danger"
            @click="removeFormDataSourceBinding(index)"
          >
            删除
          </el-button>
        </div>
        <el-form label-width="96px" size="small">
          <div class="form-data-source-grid">
            <el-form-item label="绑定位置">
              <el-select v-model="binding.usage" style="width: 100%">
                <el-option
                  v-for="usage in formLevelDataSourceUsages"
                  :key="usage.value"
                  :label="usage.label"
                  :value="usage.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源">
              <el-select
                v-model="binding.sourceId"
                clearable
                filterable
                placeholder="选择受控数据源"
                style="width: 100%"
              >
                <el-option
                  v-for="source in dataSources"
                  :key="source.id"
                  :label="`${source.sourceName} (${source.sourceType})`"
                  :value="source.id"
                />
              </el-select>
            </el-form-item>
          </div>
          <div
            v-if="binding.usage === 'BEFORE_SUBMIT'"
            class="form-data-source-prevalidate"
          >
            <el-checkbox v-model="binding.clientPrevalidate">
              浏览器预校验
            </el-checkbox>
            <el-checkbox
              v-model="binding.sideEffectFree"
              :disabled="!binding.clientPrevalidate"
            >
              无副作用
            </el-checkbox>
            <span>只有两项同时开启时浏览器才会执行；后端始终是最终权威。</span>
          </div>
          <div class="form-data-source-grid">
            <el-form-item label="输入映射">
              <el-input
                v-model="binding.inputMappingText"
                type="textarea"
                :rows="4"
                placeholder='{"filters.ownerId":"data.ownerId"}'
              />
            </el-form-item>
            <el-form-item label="输出映射">
              <el-input
                v-model="binding.outputMappingText"
                type="textarea"
                :rows="4"
                placeholder='{"ownerName":"data.user.name"}'
              />
            </el-form-item>
          </div>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="formDataSourceDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="formDataSourceSaving"
          @click="saveFormDataSourceBindings"
        >
          保存表单数据源
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="releaseDialogVisible" title="表单发布版本" width="760px">
      <el-table :data="releases" size="small">
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="contentHash" label="内容哈希" min-width="220" show-overflow-tooltip />
        <el-table-column prop="publishedBy" label="发布人" width="120" />
        <el-table-column prop="publishedAt" label="发布时间" width="180" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status === 'ACTIVE'"
              @click="activateRelease(row)"
            >激活</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="extensionManagerVisible"
      title="UI 扩展清单"
      width="920px"
      destroy-on-close
    >
      <UiExtensionManager @changed="refreshExtensionCatalog" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, View, Search, Document, Edit, DocumentAdd, Plus, Connection } from '@element-plus/icons-vue'
import FormNodeDesignItem from '@/components/FormNodeDesignItem.vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import LinkageConfigPanel from '@/components/LinkageConfigPanel.vue'
import EventConfigPanel from '@/components/EventConfigPanel.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import UiExtensionManager from '@/components/UiExtensionManager.vue'
import {
  getFormFieldComponentDescriptor,
  getFormFieldComponentOptions
} from '@/components/form-fields'
import {
  getCustomFormComponentOptions,
  getCustomFormDescriptor
} from '@/utils/customComponentRegistry'
import {
  getFormNodeComponentOptions
} from '@/utils/formNodeRegistry'
import { safeParseConfig, stringifyConfig } from '@/shared/config-runtime'
import { filterEntityFieldsByLifecycle } from '@/shared/entity-design'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import {
  getFormById,
  createForm,
  getEntityFields,
  getFormFields,
  patchFormMetadata,
  getFormNodes,
  createFormNode,
  patchFormNode,
  deleteFormNode,
  reorderFormNode,
  getFormDiff,
  publishForm,
  getFormReleases,
  activateFormRelease
} from '@/api/entityForm'
import {
  uiDataSourceApi,
  uiComponentTemplateApi,
  uiExtensionApi
} from '@/api/uiConfig'

const route = useRoute()
const router = useRouter()
const formId = route.params.id
const entityId = route.query.entityId || ''

const isEdit = ref(!!formId)
const saving = ref(false)
const savingNode = ref(false)
const nodeBaselines = ref(new Map())
const showPreview = ref(false)
const showLinkageConfig = ref(false)
const showEventConfig = ref(false)
const showFormExtensionConfig = ref(false)
const formDataSourceDialogVisible = ref(false)
const formDataSourceSaving = ref(false)
const formDataSourceRows = ref([])
let formDataSourceRowSequence = 0
const currentEventField = ref(null)
const activeDesignTab = ref('')
const releaseDialogVisible = ref(false)
const extensionManagerVisible = ref(false)
const releases = ref([])
const diffInfo = ref({ changed: true, changedSections: [] })
const dataSources = ref([])
const extensionDefinitions = ref([])
const formNodes = ref([])
const designChildrenMap = computed(() => {
  const result = new Map()
  formFields.value.forEach(node => {
    const parentId = node.parentId || ''
    if (!result.has(parentId)) result.set(parentId, [])
    result.get(parentId).push(node)
  })
  result.forEach(nodes => nodes.sort((left, right) =>
    Number(left.orderKey || left.sortOrder || 0)
      - Number(right.orderKey || right.sortOrder || 0)
  ))
  return result
})
const rootDesignNodes = computed(() => designChildrenFor(''))
const componentTemplates = ref([])
const formFieldComponentOptions = getFormFieldComponentOptions()
const localCustomFormOptions = getCustomFormComponentOptions()
const localNodeExtensionOptions = getFormNodeComponentOptions()
const activeExtensionMap = computed(() => {
  const result = new Map()
  extensionDefinitions.value
    .filter(item => item.status === 'ACTIVE')
    .sort((left, right) => Number(right.version) - Number(left.version))
    .forEach(item => {
      const key = `${item.extensionType}:${item.extensionKey}`
      if (!result.has(key)) result.set(key, item)
    })
  return result
})
const customFormOptions = computed(() =>
  localCustomFormOptions.map(option => {
    const definition = activeExtensionMap.value.get(`FORM:${option.value}`)
    return {
      ...option,
      version: definition?.version || option.version || 1,
      snapshotVersion:
        definition?.snapshotVersion || option.snapshotVersion || 1,
      manifestRegistered: Boolean(definition)
    }
  })
)
const nodeExtensionOptions = computed(() =>
  localNodeExtensionOptions.map(option => {
    const definition = activeExtensionMap.value.get(`NODE:${option.value}`)
    return {
      ...option,
      version: definition?.version || option.version || 1,
      snapshotVersion:
        definition?.snapshotVersion || option.snapshotVersion || 1,
      manifestRegistered: Boolean(definition)
    }
  })
)
const modeOptions = [
  { value: 'create', label: '新增' },
  { value: 'edit', label: '编辑' },
  { value: 'approve', label: '审批' },
  { value: 'view', label: '查看' }
]
const nodeTypeOptions = [
  { value: 'SECTION', label: '区块' },
  { value: 'GRID', label: '栅格' },
  { value: 'TAB_SET', label: 'Tab 集合' },
  { value: 'TAB', label: 'Tab 页' },
  { value: 'COLLAPSE', label: '折叠面板' },
  { value: 'TEXT', label: '说明文本' },
  { value: 'FIELD', label: '实体字段' },
  { value: 'SUB_FORM', label: '子表单' },
  { value: 'REPEATER', label: '明细表' },
  { value: 'ACTION_SLOT', label: '动作插槽' }
]
const containerNodeTypes = new Set([
  'SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE', 'SUB_FORM', 'REPEATER'
])
const formDataSourceUsages = [
  { value: 'FIELD_OPTIONS', label: '字段选项' },
  { value: 'FIELD_DEFAULT', label: '字段默认值' },
  { value: 'FIELD_COMPUTE', label: '字段计算' },
  { value: 'SUBFORM_ROWS', label: '子表行数据' },
  { value: 'AFTER_LOAD', label: '加载后处理' },
  { value: 'BEFORE_SUBMIT', label: '提交前处理' }
]
const formLevelDataSourceUsages = [
  { value: 'FORM_INIT', label: '表单初始化' },
  { value: 'AFTER_LOAD', label: '整表加载后处理' },
  { value: 'BEFORE_SUBMIT', label: '整表提交前处理' }
]
const viewConfig = ref({
  labelWidth: 120,
  customComponentProps: {}
})

// 判断是否为 Tab 模式的子表单
function isTabSubForm(field) {
  const type = (field.componentType || field.fieldType || '').toUpperCase()
  if (!['SUB_FORM', 'SUB_FORM_LIST'].includes(type)) return false
  if (field.displayMode === 'tab') return true
  if (field.componentProps) {
    try {
      const compProps = typeof field.componentProps === 'string'
        ? JSON.parse(field.componentProps)
        : field.componentProps
      return compProps.subFormConfig?.displayMode === 'tab'
    } catch (e) {}
  }
  return false
}

const entityInfo = ref({})
const entityFields = ref([])
const formFields = ref([])
const selectedField = ref(null)
const fieldSearch = ref('')
const entityList = ref([])
const formListByEntity = ref([])
const childFormReleases = ref([])
const childFormReleaseLoading = ref(false)
const referenceListOptions = ref([])

// Tab 模式的子表单字段（必须在 formFields 定义之后）
const tabSubFormFields = ref([])
watch(formFields, (fields) => {
  tabSubFormFields.value = fields.filter(f => isTabSubForm(f))
}, { deep: true, immediate: true })

const form = ref({
  id: formId,
  entityId: entityId,
  formName: '',
  formKey: '',
  layoutType: 'vertical',
  status: 1,
  initConfig: null,
  dataSourceBindingsDocument: null,
  customComponent: '',
  viewConfig: ''
})

const selectedCustomFormSchema = computed(() =>
  getCustomFormDescriptor(form.value.customComponent)?.configSchema || []
)

const formDataSourceBindingCount = computed(() =>
  Object.values(parseDocument(form.value.dataSourceBindingsDocument))
    .reduce((total, value) =>
      total + (Array.isArray(value) ? value.length : (value ? 1 : 0)), 0)
)

const availableNodeExtensionOptions = computed(() => {
  const nodeType = String(selectedField.value?.nodeType || '').toUpperCase()
  return nodeExtensionOptions.value.filter(option =>
    !option.nodeTypes?.length || option.nodeTypes.includes(nodeType)
  )
})

watch(
  () => form.value.customComponent,
  componentName => {
    if (!componentName) {
      form.value.customComponentVersion = null
      form.value.customComponentSnapshotVersion = null
      return
    }
    const descriptor = customFormOptions.value.find(
      option => option.value === componentName
    ) || getCustomFormDescriptor(componentName)
    form.value.customComponentVersion = descriptor?.version || 1
    form.value.customComponentSnapshotVersion =
      descriptor?.snapshotVersion || 1
  }
)

function handleNodeExtensionChange(componentName) {
  const descriptor = nodeExtensionOptions.value.find(
    option => option.value === componentName
  )
  selectedField.value.componentVersion = descriptor?.version || null
  selectedField.value.snapshotVersion = descriptor?.snapshotVersion || null
}

function refreshExtensionCatalog() {
  loadExtensionDefinitions()
}

async function loadExtensionDefinitions() {
  try {
    extensionDefinitions.value = await uiExtensionApi.list()
  } catch {
    extensionDefinitions.value = []
  }
}

const selectedComponentDescriptor = computed(() =>
  getFormFieldComponentDescriptor(selectedField.value?.componentType)
)

const availableFormFieldComponentOptions = computed(() => {
  const fieldType = String(selectedField.value?.fieldType || '').toUpperCase()
  return formFieldComponentOptions.filter(option =>
    !option.supportedFieldTypes?.length
    || option.supportedFieldTypes.map(type => String(type).toUpperCase()).includes(fieldType)
  )
})

const selectedComponentSchema = computed(() =>
  selectedComponentDescriptor.value?.configSchema || []
)

const selectedComponentConfig = computed({
  get() {
    return safeParseConfig(selectedField.value?.componentProps)
  },
  set(value) {
    if (selectedField.value) {
      selectedField.value.componentProps = stringifyConfig(value)
    }
  }
})

const availableParentNodes = computed(() =>
  formFields.value
    .filter(field =>
      field.id !== selectedField.value?.id
      && containerNodeTypes.has(field.nodeType || legacyNodeType(field))
    )
    .map(field => ({
      id: field.id,
      label: field.fieldLabel || field.fieldName || field.fieldCode
    }))
)

const selectedValidationConfig = computed(() =>
  safeParseConfig(selectedField.value?.validationRules)
)

// 当前选中字段的事件配置值
const currentEventValues = computed(() => {
  if (!currentEventField.value) return {}
  const result = {}
  // 读取所有以 eventOn 开头的根属性
  Object.keys(currentEventField.value).forEach(key => {
    if (key.startsWith('eventOn')) {
      const eventName = 'on' + key.slice(7)
      result[eventName] = currentEventField.value[key] || ''
    }
  })
  // 再从 componentProps 解析补充
  if (currentEventField.value.componentProps) {
    try {
      const compProps = JSON.parse(currentEventField.value.componentProps)
      if (compProps.events) {
        Object.keys(compProps.events).forEach(key => {
          if (!result[key]) {
            result[key] = compProps.events[key] || ''
          }
        })
      }
    } catch (e) {}
  }
  return result
})

// 当前选中字段是否已配置事件
const hasEventConfig = computed(() => {
  if (!selectedField.value) return false
  return Object.keys(selectedField.value).some(key => key.startsWith('eventOn') && selectedField.value[key])
})

// 预览数据
const previewForm = computed(() => {
  enrichFieldCodes()
  const sortedFields = [...formFields.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  return {
    ...form.value,
    viewConfig: viewConfig.value,
    fields: sortedFields
  }
})

// 表单标签宽度 - 与预览保持一致
const formLabelWidth = computed(() => {
  switch (form.value.layoutType) {
    case 'horizontal':
      return '120px'
    case 'vertical':
      return 'auto'
    default:
      return '120px'
  }
})

// 表单标签位置 - 与预览保持一致
const formLabelPosition = computed(() => {
  switch (form.value.layoutType) {
    case 'horizontal':
      return 'right'
    case 'vertical':
      return 'top'
    default:
      return 'right'
  }
})

// 当前选中的是否为节
const isSelectedSection = computed(() => isSectionField(selectedField.value))

// 过滤后的字段
const filteredEntityFields = computed(() => {
  if (!fieldSearch.value) return entityFields.value
  return entityFields.value.filter(f => 
    f.fieldName.includes(fieldSearch.value) || 
    f.fieldCode.includes(fieldSearch.value)
  )
})

// 检查字段是否已在表单中
function isFieldInForm(entityField) {
  return formFields.value.some(f => f.fieldId === entityField.id)
}

// 获取栅格样式
function getGridStyle(field) {
  if (form.value.layoutType === 'grid') {
    const span = field.gridSpan || 24
    return {
      width: `${(span / 24) * 100}%`,
      flex: `0 0 ${(span / 24) * 100}%`
    }
  }
  return {}
}

// 加载实体信息
async function loadEntityInfo() {
  if (!entityId) return
  try {
    const data = await entityApi.getById(entityId)
    entityInfo.value = data
    if (data.storageMode === 'SYSTEM') {
      ElMessage.warning('平台系统实体不支持动态表单设计')
      router.replace('/entity')
      return
    }
    if (!isEdit.value) {
      form.value.formName = data.entityName + '表单'
      form.value.formKey = data.entityCode + '_form'
    }
  } catch (e) {
    console.error('加载实体信息失败:', e)
  }
}

// 根据实体ID获取实体名称
function getEntityNameById(id) {
  if (!id) return '-'
  const ent = entityList.value.find(e => String(e.id) === String(id))
  return ent?.entityName || String(id)
}

// 加载所有实体列表（用于子表单引用选择）
async function loadEntityList() {
  try {
    const list = await entityApi.getAll()
    // 统一将 id 转为字符串，避免 el-select value 类型不匹配显示 raw value
    entityList.value = list
      .filter(ent => ent.storageMode !== 'SYSTEM')
      .map(ent => ({ ...ent, id: String(ent.id) }))
  } catch (e) {
    console.error('加载实体列表失败:', e)
  }
}

// 加载指定实体的表单列表（排除当前正在编辑的表单）
async function loadFormListByEntity(targetEntityId) {
  if (!targetEntityId) {
    formListByEntity.value = []
    return
  }
  try {
    const res = await entityApi.getEntityForms(targetEntityId)
    // 兼容直接返回数组或 { data: [...] } 两种格式
    const list = Array.isArray(res) ? res : (Array.isArray(res.data) ? res.data : [])
    // 排除当前正在编辑的表单（避免循环引用）
    formListByEntity.value = list.filter(fm => String(fm.id) !== String(formId))
  } catch (e) {
    console.error('加载表单列表失败:', e)
    formListByEntity.value = []
  }
}

function normalizeReleaseList(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.records)) return response.records
  return []
}

async function fetchChildFormReleases(childFormId) {
  const formId = String(childFormId || '')
  if (!formId) return []
  return normalizeReleaseList(await getFormReleases(formId))
    .filter(release =>
      release?.id
      && Number.isInteger(Number(release.version))
      && release.snapshotDocument
    )
    .sort((left, right) => Number(right.version) - Number(left.version))
}

async function loadChildFormReleases(
  childFormId,
  targetField = selectedField.value,
  autoPinLegacy = false
) {
  if (!childFormId) {
    childFormReleases.value = []
    return []
  }
  childFormReleaseLoading.value = true
  try {
    const releases = await fetchChildFormReleases(childFormId)
    if (targetField === selectedField.value) {
      childFormReleases.value = releases
    }
    let selectedRelease = releases.find(
      release => String(release.id) === String(targetField?.childFormReleaseId)
    )
    if (!selectedRelease && autoPinLegacy) {
      selectedRelease = releases.find(
        release => String(release.status).toUpperCase() === 'ACTIVE'
      )
    }
    if (selectedRelease && targetField) {
      targetField.childFormId = String(childFormId)
      targetField.refFormId = String(childFormId)
      targetField.childFormReleaseId = selectedRelease.id
      targetField.childFormReleaseVersion = Number(selectedRelease.version)
    }
    return releases
  } catch (error) {
    if (targetField === selectedField.value) {
      childFormReleases.value = []
    }
    console.error('加载子表单发布版本失败:', error)
    return []
  } finally {
    childFormReleaseLoading.value = false
  }
}

async function handleChildFormChange(childFormId) {
  const field = selectedField.value
  if (!field) return
  field.childFormId = childFormId || ''
  field.refFormId = childFormId || ''
  field.childFormReleaseId = ''
  field.childFormReleaseVersion = null
  childFormReleases.value = []
  if (!childFormId) return
  const releases = await loadChildFormReleases(childFormId, field, true)
  if (!field.childFormReleaseId) {
    if (releases.length === 0) {
      ElMessage.warning('所选子表单尚无可用发布版本，请先发布子表单')
    } else {
      ElMessage.warning('请选择子表单发布版本')
    }
  }
}

function handleChildFormReleaseChange(releaseId) {
  const field = selectedField.value
  if (!field) return
  const release = childFormReleases.value.find(
    item => String(item.id) === String(releaseId)
  )
  field.childFormReleaseId = release?.id || ''
  field.childFormReleaseVersion = release
    ? Number(release.version)
    : null
}

function formatChildFormReleaseLabel(release) {
  const status = String(release?.status || '').toUpperCase() === 'ACTIVE'
    ? '当前激活'
    : '历史版本'
  return `v${release?.version} · ${status}`
}

function isSubFormField(field) {
  const nodeType = String(field?.nodeType || '').toUpperCase()
  const componentType = String(
    field?.componentType || field?.fieldType || ''
  ).toUpperCase()
  return ['SUB_FORM', 'REPEATER', 'SUB_FORM_LIST'].includes(nodeType)
    || ['SUB_FORM', 'SUB_FORM_LIST'].includes(componentType)
}

async function ensureChildFormReleaseBinding(field) {
  if (!isSubFormField(field)) return
  const childFormId = field.childFormId || field.refFormId
  if (!childFormId) return
  const releases = await fetchChildFormReleases(childFormId)
  let release = releases.find(
    item => String(item.id) === String(field.childFormReleaseId)
  )
  if (!release && !field.childFormReleaseId) {
    release = releases.find(
      item => String(item.status).toUpperCase() === 'ACTIVE'
    )
  }
  if (!release) {
    throw new Error(
      field.childFormReleaseId
        ? '已选择的子表单发布版本不存在，请重新选择'
        : '子表单必须选择一个已发布版本'
    )
  }
  if (field.childFormReleaseVersion != null
      && Number(field.childFormReleaseVersion) !== Number(release.version)) {
    throw new Error('子表单发布版本号与 release 不匹配，请重新选择')
  }
  field.childFormId = String(childFormId)
  field.refFormId = String(childFormId)
  field.childFormReleaseId = release.id
  field.childFormReleaseVersion = Number(release.version)
}

// 检查字段的 componentProps 中是否已有选项
function hasOptionsInComponentProps(field) {
  if (!field.componentProps) return false
  try {
    const compProps = typeof field.componentProps === 'string'
      ? JSON.parse(field.componentProps)
      : field.componentProps
    return compProps && compProps.options && compProps.options.length > 0
  } catch (e) {
    return false
  }
}

// 给表单字段补充 fieldCode 和选项数据
function enrichFieldCodes() {
  if (entityFields.value.length === 0 || formFields.value.length === 0) return
  formFields.value.forEach(field => {
    if (!field.fieldCode && field.fieldId) {
      // 使用字符串比较，避免数字/字符串类型不匹配
      const fieldIdStr = String(field.fieldId)
      const entityField = entityFields.value.find(ef => String(ef.id) === fieldIdStr)
      if (entityField && entityField.fieldCode) {
        field.fieldCode = entityField.fieldCode
      }
    }
    // 补充选项数据（用于选项联动等）
    if (!field.optionsJson && !field.options && !hasOptionsInComponentProps(field) && field.fieldId) {
      const fieldIdStr = String(field.fieldId)
      const entityField = entityFields.value.find(ef => String(ef.id) === fieldIdStr)
      if (entityField) {
        if (entityField.optionsJson) field.optionsJson = entityField.optionsJson
        if (entityField.componentProps) field.componentProps = entityField.componentProps
        if (entityField.options) field.options = entityField.options
      }
    }
  })
}

// 加载实体字段
async function loadEntityFields() {
  const eid = entityId || form.value.entityId
  if (!eid) return

  try {
    const loadedFields = await getEntityFields(eid)
    entityFields.value = filterEntityFieldsByLifecycle(entityInfo.value, loadedFields)
    enrichFieldCodes()
  } catch (e) {
    console.error('加载实体字段失败:', e)
  }
}

// 加载表单信息
async function loadFormInfo() {
  if (!isEdit.value) return
  
  try {
    const data = await getFormById(formId)
    form.value = { ...form.value, ...data }
    viewConfig.value = {
      labelWidth: 120,
      customComponentProps: {},
      ...safeParseConfig(data.viewConfig)
    }
    if (data.entityId && !entityId) {
      form.value.entityId = data.entityId
    }
    await loadDiff()
  } catch (e) {
    console.error('加载表单信息失败:', e)
  }
}

function parseDocument(value) {
  return safeParseConfig(value)
}

function openFormDataSourceConfig() {
  if (!form.value.id) {
    ElMessage.warning('请先保存表单草稿')
    return
  }
  const bindings = parseDocument(
    form.value.dataSourceBindingsDocument
  )
  const rows = []
  Object.entries(bindings).forEach(([usage, configured]) => {
    const values = Array.isArray(configured)
      ? configured
      : [configured]
    values.filter(Boolean).forEach(value => {
      const normalized = typeof value === 'string'
        ? { sourceId: value }
        : { ...value }
      const {
        sourceId,
        id,
        inputMapping,
        outputMapping,
        clientPrevalidate,
        sideEffectFree,
        usage: ignoredUsage,
        ...extra
      } = normalized
      rows.push({
        rowKey: `form_source_${++formDataSourceRowSequence}`,
        usage,
        sourceId: sourceId || id || '',
        inputMappingText: stringifyConfig(inputMapping || {}),
        outputMappingText: stringifyConfig(outputMapping || {}),
        clientPrevalidate: clientPrevalidate === true,
        sideEffectFree: sideEffectFree === true,
        extra
      })
    })
  })
  formDataSourceRows.value = rows
  formDataSourceDialogVisible.value = true
}

function addFormDataSourceBinding() {
  formDataSourceRows.value.push({
    rowKey: `form_source_${++formDataSourceRowSequence}`,
    usage: 'FORM_INIT',
    sourceId: '',
    inputMappingText: '{}',
    outputMappingText: '{}',
    clientPrevalidate: false,
    sideEffectFree: false,
    extra: {}
  })
}

function removeFormDataSourceBinding(index) {
  formDataSourceRows.value.splice(index, 1)
}

function serializeFormDataSourceBindings() {
  const bindings = {}
  formDataSourceRows.value.forEach(row => {
    if (!row.sourceId) {
      throw new Error('表单级数据源不能为空')
    }
    if (row.usage === 'BEFORE_SUBMIT'
      && row.clientPrevalidate
      && !row.sideEffectFree) {
      throw new Error('浏览器预校验必须同时标记为无副作用')
    }
    const binding = {
      ...(row.extra || {}),
      sourceId: row.sourceId,
      inputMapping: parseDocument(row.inputMappingText),
      outputMapping: parseDocument(row.outputMappingText)
    }
    if (row.usage === 'BEFORE_SUBMIT') {
      binding.clientPrevalidate = row.clientPrevalidate === true
      binding.sideEffectFree = row.sideEffectFree === true
    }
    if (!bindings[row.usage]) bindings[row.usage] = []
    bindings[row.usage].push(binding)
  })
  Object.keys(bindings).forEach(usage => {
    if (bindings[usage].length === 1) {
      bindings[usage] = bindings[usage][0]
    }
  })
  return bindings
}

async function saveFormDataSourceBindings() {
  if (!form.value.id) return
  formDataSourceSaving.value = true
  try {
    const bindings = serializeFormDataSourceBindings()
    await Promise.all(
      formDataSourceRows.value.map(row =>
        uiDataSourceApi.validateBinding(
          row.sourceId,
          row.usage
        )
      )
    )
    const updated = await patchFormMetadata(
      form.value.id,
      {
        expectedRevision: form.value.revision,
        dataSourceBindings: bindings
      }
    )
    form.value = { ...form.value, ...updated }
    formDataSourceDialogVisible.value = false
    await loadDiff()
    ElMessage.success('表单级数据源草稿已保存，发布后生效')
  } catch (error) {
    handleRevisionConflict(error)
  } finally {
    formDataSourceSaving.value = false
  }
}

function legacyNodeType(field) {
  const fieldType = String(field?.fieldType || '').toUpperCase()
  const componentType = String(field?.componentType || '').toUpperCase()
  if (fieldType === 'SECTION' || componentType === 'SECTION') return 'SECTION'
  if (fieldType === 'SUB_FORM_LIST') return 'REPEATER'
  if (fieldType === 'SUB_FORM' || componentType === 'SUB_FORM') return 'SUB_FORM'
  return 'FIELD'
}

function nodeLabel(nodeId) {
  const node = formFields.value.find(item => item.id === nodeId)
  return node?.fieldLabel || node?.fieldName || node?.fieldCode || nodeId
}

function nodeToField(node, legacyField) {
  const props = parseDocument(node.propsDocument)
  const rules = parseDocument(node.rulesDocument)
  const bindings = parseDocument(node.dataSourceBindingsDocument)
  const firstBinding = Object.entries(bindings)[0] || []
  const field = {
    ...(legacyField || {}),
    id: node.id,
    nodeId: node.id,
    formId: node.formId,
    parentId: node.parentId || '',
    nodeType: node.nodeType,
    nodeKey: node.nodeKey,
    revision: node.revision,
    orderKey: node.orderKey,
    templateId: node.templateId,
    templateVersion: node.templateVersion,
    componentName: node.componentName || '',
    componentVersion: node.componentVersion,
    snapshotVersion: node.snapshotVersion,
    localOverrides: parseDocument(node.localOverridesDocument),
    legacyProps: parseDocument(node.legacyPropsDocument),
    dataSourceBindings: bindings,
    fieldId: props.fieldId ?? legacyField?.fieldId,
    fieldCode: props.fieldCode || node.nodeKey,
    fieldName: props.fieldName || props.label || legacyField?.fieldName || node.nodeKey,
    fieldLabel: props.label || legacyField?.fieldLabel || node.nodeKey,
    fieldType: props.fieldType || legacyField?.fieldType || node.nodeType,
    componentType: props.componentType || legacyField?.componentType || node.nodeType.toLowerCase(),
    placeholder: props.placeholder ?? legacyField?.placeholder,
    defaultValue: props.defaultValue ?? legacyField?.defaultValue,
    gridSpan: props.gridSpan ?? legacyField?.gridSpan ?? 24,
    childFormId:
      props.childFormId
      || props.refFormId
      || props.publishedFormId
      || legacyField?.childFormId
      || legacyField?.refFormId
      || '',
    childFormReleaseId:
      props.childFormReleaseId
      || props.refFormReleaseId
      || props.publishedFormReleaseId
      || legacyField?.childFormReleaseId
      || '',
    childFormReleaseVersion:
      props.childFormReleaseVersion
      ?? props.refFormReleaseVersion
      ?? props.publishedFormReleaseVersion
      ?? legacyField?.childFormReleaseVersion
      ?? null,
    isRequired: props.required ? 1 : (legacyField?.isRequired || 0),
    isReadonly: props.readonly ? 1 : (legacyField?.isReadonly || 0),
    isHidden: props.hidden ? 1 : (legacyField?.isHidden || 0),
    componentProps: stringifyConfig(props.componentProps || props),
    validationRules: stringifyConfig(rules.validation || rules),
    extensionConfig: stringifyConfig(rules.extension || {}),
    dataSourceUsage: firstBinding[0] || 'FIELD_OPTIONS',
    dataSourceId: firstBinding[1]?.sourceId || firstBinding[1] || '',
    dataSourceInputMappingText: stringifyConfig(
      firstBinding[1]?.inputMapping || {}
    ),
    dataSourceOutputMappingText: stringifyConfig(
      firstBinding[1]?.outputMapping || {}
    )
  }
  restoreFieldConfig(field)
  return field
}

function fieldToNodePayload(field) {
  serializeFieldConfig(field)
  const componentProps = parseDocument(field.componentProps)
  const subFormNode = isSubFormField(field)
  const childFormId = field.childFormId || field.refFormId || ''
  const childFormReleaseId = field.childFormReleaseId || ''
  const childFormReleaseVersion = field.childFormReleaseVersion == null
    ? null
    : Number(field.childFormReleaseVersion)
  const childFormBindingProps = subFormNode && childFormId
    ? {
        childFormId,
        refFormId: childFormId,
        publishedFormId: childFormId,
        childFormReleaseId,
        refFormReleaseId: childFormReleaseId,
        publishedFormReleaseId: childFormReleaseId,
        childFormReleaseVersion,
        refFormReleaseVersion: childFormReleaseVersion,
        publishedFormReleaseVersion: childFormReleaseVersion
      }
    : {}
  const dataSourceBindings = {
    ...(field.dataSourceBindings || {})
  }
  const usage = field.dataSourceUsage || 'FIELD_OPTIONS'
  if (field.dataSourceId) {
    dataSourceBindings[usage] = {
      sourceId: field.dataSourceId,
      inputMapping: parseDocument(field.dataSourceInputMappingText),
      outputMapping: parseDocument(field.dataSourceOutputMappingText)
    }
  } else {
    delete dataSourceBindings[usage]
  }
  return {
    id: field.id,
    parentId: field.parentId || null,
    nodeKey: field.nodeKey || field.fieldCode || `node_${field.id}`,
    nodeType: field.nodeType || legacyNodeType(field),
    bindingType: field.relationCode
      ? 'RELATION'
      : (field.fieldId ? 'ENTITY_FIELD' : 'NONE'),
    bindingRef: field.relationCode || field.fieldCode || null,
    componentName: field.componentName || null,
    componentVersion: field.componentVersion || null,
    snapshotVersion: field.snapshotVersion || null,
    childFormId: childFormId || null,
    childFormReleaseId: childFormReleaseId || null,
    childFormReleaseVersion,
    props: {
      fieldId: field.fieldId,
      fieldCode: field.fieldCode,
      fieldName: field.fieldName,
      label: field.fieldLabel,
      fieldType: field.fieldType,
      componentType: field.componentType,
      placeholder: field.placeholder,
      defaultValue: field.defaultValue,
      gridSpan: field.gridSpan,
      required: field.isRequired === 1,
      readonly: field.isReadonly === 1,
      hidden: field.isHidden === 1,
      componentProps,
      ...childFormBindingProps
    },
    rules: {
      validation: parseDocument(field.validationRules),
      extension: parseDocument(field.extensionConfig)
    },
    dataSourceBindings,
    legacyProps: field.legacyProps || {},
    orderKey: field.orderKey,
    templateId: field.templateId,
    templateVersion: field.templateVersion,
    localOverrides: field.localOverrides || {}
  }
}

function nodeFingerprint(field) {
  return JSON.stringify(fieldToNodePayload({
    ...field,
    componentProps: field.componentProps,
    validationRules: field.validationRules,
    extensionConfig: field.extensionConfig
  }))
}

function rememberNodeBaseline(field) {
  if (field?.id && field?.revision > 0) {
    nodeBaselines.value.set(field.id, nodeFingerprint(field))
  }
}

function fieldToNodeEntity(field, index) {
  const payload = fieldToNodePayload(field)
  return {
    id: payload.id,
    formId: form.value.id,
    parentId: payload.parentId,
    nodeKey: payload.nodeKey,
    nodeType: payload.nodeType,
    bindingType: payload.bindingType,
    bindingRef: payload.bindingRef,
    componentName: payload.componentName,
    componentVersion: payload.componentVersion,
    snapshotVersion: payload.snapshotVersion,
    propsDocument: stringifyConfig(payload.props),
    rulesDocument: stringifyConfig(payload.rules),
    dataSourceBindingsDocument: stringifyConfig(payload.dataSourceBindings),
    legacyPropsDocument: stringifyConfig(payload.legacyProps),
    orderKey: payload.orderKey || (index + 1) * 1000000,
    revision: field.revision || 1,
    templateId: payload.templateId,
    templateVersion: payload.templateVersion,
    localOverridesDocument: stringifyConfig(payload.localOverrides)
  }
}

// 从 componentProps 恢复子表单和事件配置
function restoreFieldConfig(field) {
  if (!field.componentProps) return
  try {
    const compProps = typeof field.componentProps === 'string'
      ? JSON.parse(field.componentProps)
      : field.componentProps

    // 恢复子表单配置
    if (compProps.subFormConfig) {
      const subFormConfig = compProps.subFormConfig
      field.displayMode = subFormConfig.displayMode || 'embedded'
      field.layout = subFormConfig.layout || 'form'
      field.refEntityId = subFormConfig.refEntityId || field.childEntityId || field.refEntityId || ''
      field.childFormId = field.childFormId
        || subFormConfig.childFormId
        || subFormConfig.refFormId
        || subFormConfig.publishedFormId
        || ''
      field.refFormId = field.childFormId
      field.childFormReleaseId = field.childFormReleaseId
        || subFormConfig.childFormReleaseId
        || subFormConfig.refFormReleaseId
        || subFormConfig.publishedFormReleaseId
        || ''
      field.childFormReleaseVersion = field.childFormReleaseVersion
        ?? subFormConfig.childFormReleaseVersion
        ?? subFormConfig.refFormReleaseVersion
        ?? subFormConfig.publishedFormReleaseVersion
        ?? null
      field.repeatable = field.relationType !== 'ONE_TO_ONE'
      field.childEntityId = field.childEntityId || field.refEntityId || ''
      field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
    }
    // 恢复实体引用配置
    if (compProps.refConfig) {
      field.refEntityType = compProps.refConfig.refEntityType || ''
      field.refEntityId = String(compProps.refConfig.refEntityId || '')
      field.apiUrl = compProps.refConfig.apiUrl || ''
      field.refEntityCode = compProps.refConfig.entityCode || ''
      field.refListKey = compProps.refConfig.listKey || ''
    }

    // 恢复事件配置
    if (compProps.events) {
      Object.keys(compProps.events).forEach(key => {
        const rootKey = 'eventOn' + key.charAt(2).toUpperCase() + key.slice(3)
        field[rootKey] = compProps.events[key] || ''
      })
    }
  } catch (e) {
    // 忽略解析错误
  }
}

// 将子表单和事件配置序列化到 componentProps
function serializeFieldConfig(field) {
  try {
    const compProps = field.componentProps
      ? (typeof field.componentProps === 'string' ? JSON.parse(field.componentProps) : field.componentProps)
      : {}

    // 序列化子表单配置
    if (isSubFormField(field)) {
      const childFormId = field.childFormId || field.refFormId || ''
      const childFormReleaseId = field.childFormReleaseId || ''
      const childFormReleaseVersion = field.childFormReleaseVersion == null
        ? null
        : Number(field.childFormReleaseVersion)
      compProps.subFormConfig = {
        ...(compProps.subFormConfig || {}),
        displayMode: field.displayMode || 'embedded',
        layout: field.layout || 'form',
        refEntityId: field.childEntityId || field.refEntityId || '',
        childFormId,
        refFormId: childFormId,
        publishedFormId: childFormId,
        childFormReleaseId,
        refFormReleaseId: childFormReleaseId,
        publishedFormReleaseId: childFormReleaseId,
        childFormReleaseVersion,
        refFormReleaseVersion: childFormReleaseVersion,
        publishedFormReleaseVersion: childFormReleaseVersion,
        repeatable: field.relationType !== 'ONE_TO_ONE',
        relationType: field.relationType || 'ONE_TO_MANY',
        childRefFieldCode: field.childRefFieldCode || field.refFieldCode || ''
      }
      delete compProps.fields
      delete compProps.subFields
    }
    // 序列化实体引用配置
    if ((field.componentType || '').toUpperCase() === 'REFERENCE' || (field.componentType || '').toUpperCase() === 'MULTI_REFERENCE') {
      compProps.refConfig = {
        refEntityType: field.refEntityType || '',
        refEntityId: field.refEntityId || '',
        entityCode: field.refEntityCode || '',
        listKey: field.refListKey || '',
        apiUrl: field.apiUrl || ''
      }
    }

    // 序列化事件配置
    const events = {}
    Object.keys(field).forEach(key => {
      if (key.startsWith('eventOn') && field[key]) {
        const eventName = 'on' + key.slice(7)
        events[eventName] = field[key]
      }
    })
    if (Object.keys(events).length > 0) {
      compProps.events = events
    } else {
      delete compProps.events
    }

    // 序列化选项配置（optionsJson → componentProps.options）
    if (field.optionsJson) {
      try {
        const options = JSON.parse(field.optionsJson)
        if (Array.isArray(options) && options.length > 0) {
          compProps.options = options
        }
      } catch (e) {}
    }

    field.componentProps = JSON.stringify(compProps)
  } catch (e) {
    console.error('序列化字段配置失败:', e)
  }
}

// 加载表单字段
async function loadFormFields() {
  if (!isEdit.value) return

  try {
    const [legacyFields, nodes] = await Promise.all([
      getFormFields(formId),
      getFormNodes(formId).catch(() => [])
    ])
    formNodes.value = Array.isArray(nodes) ? nodes : []
    if (formNodes.value.length > 0) {
      const legacyById = new Map(
        (legacyFields || []).map(field => [String(field.id), field])
      )
      const legacyByCode = new Map(
        (legacyFields || []).map(field => [field.fieldCode, field])
      )
      formFields.value = formNodes.value.map(node =>
        nodeToField(
          node,
          legacyById.get(String(node.id)) || legacyByCode.get(node.nodeKey)
        )
      )
    } else {
      formFields.value = legacyFields || []
      formFields.value.forEach((field, index) => {
        field.id = field.id || `legacy_${Date.now()}_${index}`
        field.nodeId = field.id
        field.nodeKey = field.fieldCode
        field.nodeType = legacyNodeType(field)
        field.revision = 0
        field.orderKey = (index + 1) * 1000000
        field.parentId = ''
      })
    }
    // 统一将 refEntityId 转为字符串，避免 el-select 类型不匹配显示原始值
    formFields.value.forEach(field => {
      if (field.refEntityId != null) {
        field.refEntityId = String(field.refEntityId)
      }
      if (field.childEntityId != null) {
        field.childEntityId = String(field.childEntityId)
      }
      if (isSubFormField(field)) {
        field.childEntityId = field.childEntityId || field.refEntityId || ''
        field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
        field.relationType = field.relationType || 'ONE_TO_MANY'
        field.repeatable = field.relationType !== 'ONE_TO_ONE'
      }
    })
    formFields.value.forEach(restoreFieldConfig)
    enrichFieldCodes()
    nodeBaselines.value = new Map()
    formFields.value.forEach(rememberNodeBaseline)
  } catch (e) {
    console.error('加载表单字段失败:', e)
  }
}

// 添加字段到表单
function addField(entityField) {
  // 检查是否已存在
  if (isFieldInForm(entityField)) {
    ElMessage.warning('该字段已添加到表单')
    return
  }
  
  const stableId = `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const newField = {
    id: stableId,
    nodeId: stableId,
    nodeKey: entityField.fieldCode,
    nodeType: ['SUB_FORM', 'SUB_FORM_LIST'].includes(entityField.fieldType)
      ? (entityField.fieldType === 'SUB_FORM_LIST' ? 'REPEATER' : 'SUB_FORM')
      : 'FIELD',
    parentId: '',
    revision: 0,
    orderKey: (formFields.value.length + 1) * 1000000,
    formId: formId,
    fieldId: entityField.id,
    fieldCode: entityField.fieldCode,
    fieldName: entityField.fieldName,
    fieldLabel: entityField.fieldName,
    fieldType: entityField.fieldType,
    componentType: getDefaultComponentType(entityField.fieldType),
    isRequired: entityField.isRequired ? 1 : 0,
    isReadonly: 0,
    isHidden: 0,
    validationRules: '',
    extensionConfig: '',
    gridSpan: 24,
    sortOrder: formFields.value.length
  }

  // 复制实体引用配置（统一将 refEntityId 转为字符串，避免 el-select 类型不匹配）
  if (entityField.refEntityId) {
    newField.refEntityId = String(entityField.refEntityId)
  }
  if (entityField.refEntityType) {
    newField.refEntityType = entityField.refEntityType
  }
  if (entityField.apiUrl) {
    newField.apiUrl = entityField.apiUrl
  }
  if (entityField.childEntityId) {
    newField.childEntityId = String(entityField.childEntityId)
    newField.refEntityId = String(entityField.childEntityId)
  }
  if (entityField.childRefFieldCode) {
    newField.childRefFieldCode = entityField.childRefFieldCode
    newField.refFieldCode = entityField.childRefFieldCode
  }
  if (entityField.relationType) {
    newField.relationType = entityField.relationType
  }
  // 子表单默认展示
  if (isSubFormField(newField)) {
    newField.layout = 'form'
    newField.displayMode = 'embedded'
    newField.repeatable = newField.relationType !== 'ONE_TO_ONE'
    if (newField.refEntityId) {
      loadFormListByEntity(newField.refEntityId)
    }
  }

  // 复制选项数据（用于选项联动等）
  if (entityField.optionsJson) {
    newField.optionsJson = entityField.optionsJson
  }
  if (entityField.componentProps) {
    newField.componentProps = entityField.componentProps
  }
  if (entityField.options) {
    newField.options = entityField.options
  }

  formFields.value.push(newField)
  selectedField.value = newField
  if (['REFERENCE', 'MULTI_REFERENCE'].includes((newField.componentType || '').toUpperCase())) {
    loadReferenceLists(newField.refEntityId, false)
  }
  ElMessage.success('字段已添加')
}

// 获取默认组件类型
function getDefaultComponentType(fieldType) {
  const typeMap = {
    'STRING': 'input',
    'TEXT': 'textarea',
    'INTEGER': 'number',
    'LONG': 'number',
    'DOUBLE': 'number',
    'DECIMAL': 'number',
    'DATE': 'date',
    'DATETIME': 'datetime',
    'BOOLEAN': 'switch',
    'SUB_FORM': 'sub_form',
    'SUB_FORM_LIST': 'sub_form',
    'REFERENCE': 'reference',
    'MULTI_REFERENCE': 'multi_reference',
    'SELECT': 'select',
    'MULTI_SELECT': 'select_multiple',
    'RADIO': 'radio',
    'CHECKBOX': 'checkbox'
  }
  return typeMap[fieldType] || 'input'
}

// 判断是否为节字段
function isSectionField(field) {
  return (field?.fieldType || '').toUpperCase() === 'SECTION' ||
    (field?.componentType || '').toLowerCase() === 'section'
}

// 添加节
function addSection() {
  addContainerNode('SECTION')
}

function addContainerNode(nodeType) {
  const ts = Date.now()
  const typeLabels = {
    SECTION: '新区块',
    GRID: '新栅格',
    TAB_SET: '新 Tab 集合',
    TAB: '新 Tab',
    COLLAPSE: '新折叠面板',
    TEXT: '说明文本',
    REPEATER: '新明细表',
    ACTION_SLOT: '动作插槽'
  }
  const stableId = `node_${nodeType.toLowerCase()}_${ts}`
  const section = {
    id: stableId,
    nodeId: stableId,
    nodeKey: stableId,
    nodeType,
    parentId: '',
    revision: 0,
    orderKey: (formFields.value.length + 1) * 1000000,
    formId: formId,
    fieldId: null,
    fieldCode: stableId,
    fieldName: typeLabels[nodeType] || '新节点',
    fieldLabel: typeLabels[nodeType] || '新节点',
    fieldType: nodeType,
    componentType: nodeType.toLowerCase(),
    isRequired: 0,
    isReadonly: 1,
    isHidden: 0,
    validationRules: '',
    extensionConfig: '',
    gridSpan: 24,
    sortOrder: formFields.value.length
  }
  formFields.value.push(section)
  selectedField.value = section
  ElMessage.success(`${typeLabels[nodeType] || '节点'}已添加`)
}

// 选择字段
function selectField(field) {
  selectedField.value = field
  childFormReleases.value = []
  if (field && isSubFormField(field)) {
    field.childEntityId = field.childEntityId || field.refEntityId || ''
    field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
    field.relationType = field.relationType || 'ONE_TO_MANY'
    field.repeatable = field.relationType !== 'ONE_TO_ONE'
    const refEntityId = field.childEntityId || field.refEntityId || entityInfo.value.id
    loadFormListByEntity(refEntityId)
    if (field.childFormId || field.refFormId) {
      loadChildFormReleases(
        field.childFormId || field.refFormId,
        field,
        true
      )
    }
  }
  if (field && ['REFERENCE', 'MULTI_REFERENCE'].includes((field.componentType || '').toUpperCase())) {
    loadReferenceLists(field.refEntityId, false)
  }
}

function designChildrenFor(parentId) {
  return designChildrenMap.value.get(parentId || '') || []
}

// 移除字段
async function removeField(index) {
  const field = formFields.value[index]
  if (field?.revision > 0 && form.value.id) {
    try {
      await deleteFormNode(form.value.id, field.id, field.revision)
    } catch (error) {
      handleRevisionConflict(error, field)
      return
    }
  }
  formFields.value.splice(index, 1)
  if (selectedField.value && !formFields.value.includes(selectedField.value)) {
    selectedField.value = null
  }
  if (form.value.id) {
    const latest = await getFormById(form.value.id)
    form.value.revision = latest.revision
  }
  await loadDiff()
}

async function removeNode(field) {
  const index = formFields.value.findIndex(item => item.id === field.id)
  if (index >= 0) await removeField(index)
}

async function moveNode({ node, direction }) {
  const siblings = designChildrenFor(node.parentId || '')
  const siblingIndex = siblings.findIndex(item => item.id === node.id)
  const targetIndex = siblingIndex + direction
  if (siblingIndex < 0 || targetIndex < 0 || targetIndex >= siblings.length) return

  const reorderedSiblings = [...siblings]
  reorderedSiblings.splice(siblingIndex, 1)
  reorderedSiblings.splice(targetIndex, 0, node)
  const target = siblings[targetIndex]
  const currentGlobalIndex = formFields.value.findIndex(item => item.id === node.id)
  const targetGlobalIndex = formFields.value.findIndex(item => item.id === target.id)
  if (currentGlobalIndex < 0 || targetGlobalIndex < 0) return

  formFields.value.splice(currentGlobalIndex, 1)
  const insertionIndex = formFields.value.findIndex(item => item.id === target.id)
  formFields.value.splice(
    direction < 0 ? insertionIndex : insertionIndex + 1,
    0,
    node
  )
  reorderedSiblings.forEach((item, index) => {
    item.sortOrder = index
  })
  await persistNodeOrder(node, targetIndex, reorderedSiblings)
}

// 上移
async function moveUp(index) {
  if (index === 0) return
  const temp = formFields.value[index]
  formFields.value[index] = formFields.value[index - 1]
  formFields.value[index - 1] = temp
  formFields.value.forEach((f, i) => f.sortOrder = i)
  await persistNodeOrder(temp, index - 1)
}

// 下移
async function moveDown(index) {
  if (index === formFields.value.length - 1) return
  const temp = formFields.value[index]
  formFields.value[index] = formFields.value[index + 1]
  formFields.value[index + 1] = temp
  formFields.value.forEach((f, i) => f.sortOrder = i)
  await persistNodeOrder(temp, index + 1)
}

async function persistNodeOrder(field, newIndex, orderedSiblings = formFields.value) {
  if (!form.value.id || !field?.revision) return
  const previous = orderedSiblings[newIndex - 1]
  const next = orderedSiblings[newIndex + 1]
  try {
    const saved = await reorderFormNode(form.value.id, field.id, {
      expectedRevision: field.revision,
      parentId: field.parentId || null,
      previousNodeId: previous?.parentId === field.parentId ? previous.id : null,
      nextNodeId: next?.parentId === field.parentId ? next.id : null
    })
    Object.assign(field, {
      revision: saved.revision,
      orderKey: saved.orderKey,
      parentId: saved.parentId || ''
    })
    const latest = await getFormById(form.value.id)
    form.value.revision = latest.revision
    await loadDiff()
  } catch (error) {
    handleRevisionConflict(error, field)
    await loadFormFields()
  }
}

// 打开事件配置弹框
function openEventConfig() {
  if (!selectedField.value) return
  currentEventField.value = selectedField.value
  showEventConfig.value = true
}

// 保存事件配置
function handleSaveEvent(events) {
  if (!currentEventField.value) return
  // 清除旧的事件根属性
  Object.keys(currentEventField.value).forEach(key => {
    if (key.startsWith('eventOn')) {
      delete currentEventField.value[key]
    }
  })
  // 保存所有事件（包括自定义事件）
  Object.keys(events).forEach(key => {
    if (events[key]) {
      const rootKey = 'eventOn' + key.charAt(2).toUpperCase() + key.slice(3)
      currentEventField.value[rootKey] = events[key]
    }
  })
  ElMessage.success('事件配置已保存')
}

// 保存联动配置
function handleSaveLinkage(linkageRules) {
  if (selectedField.value) {
    // 先清除旧的联动规则根属性，避免切换类型后残留
    const allRuleKeys = ['visibilityRule', 'disabledRule', 'requiredRule', 'calculationFormula',
      'calculationPrecision', 'calculationEditable', 'optionsLinkage', 'valueFormula', 'valueMapping', 'valueApi']
    allRuleKeys.forEach(key => {
      delete selectedField.value[key]
    })

    selectedField.value.linkageRules = linkageRules
    // 将联动规则展开到字段根属性，便于引擎直接读取
    Object.keys(linkageRules).forEach(key => {
      selectedField.value[key] = linkageRules[key]
    })
    // 将联动规则保存到扩展属性中（持久化到数据库）
    selectedField.value.componentProps = JSON.stringify({
      ...parseComponentProps(selectedField.value.componentProps),
      linkageRules
    })
    ElMessage.success('联动配置已保存到字段')
    showLinkageConfig.value = false
  }
}

// 解析 componentProps
function parseComponentProps(propsStr) {
  if (!propsStr) return {}
  try {
    return JSON.parse(propsStr)
  } catch (e) {
    return {}
  }
}

function updateValidationConfig(key, value) {
  if (!selectedField.value) return
  selectedField.value.validationRules = stringifyConfig({
    ...selectedValidationConfig.value,
    [key]: value
  })
}

function getModeAccessValue(mode, key) {
  const extension = safeParseConfig(selectedField.value?.extensionConfig)
  const value = extension?.modes?.[mode]?.[key]
  return value !== false
}

function updateModeAccess(mode, key, value) {
  if (!selectedField.value) return
  const extension = safeParseConfig(selectedField.value.extensionConfig)
  selectedField.value.extensionConfig = stringifyConfig({
    ...extension,
    modes: {
      ...(extension.modes || {}),
      [mode]: {
        ...(extension.modes?.[mode] || {}),
        [key]: value
      }
    }
  })
}

// 引用实体变化时加载表单列表
function handleRefEntityChange(entityId) {
  loadFormListByEntity(entityId || entityInfo.value.id)
}

async function handleReferenceEntityChange(targetEntityId) {
  const entity = entityList.value.find(item => String(item.id) === String(targetEntityId))
  if (selectedField.value) {
    selectedField.value.refEntityCode = entity?.entityCode || ''
    selectedField.value.refListKey = ''
  }
  await loadReferenceLists(targetEntityId)
}

async function loadReferenceLists(targetEntityId, reset = true) {
  if (reset && selectedField.value) {
    selectedField.value.refListKey = ''
  }
  if (!targetEntityId) {
    referenceListOptions.value = []
    return
  }
  const entity = entityList.value.find(item => String(item.id) === String(targetEntityId))
  if (selectedField.value && !selectedField.value.refEntityCode) {
    selectedField.value.refEntityCode = entity?.entityCode || ''
  }
  try {
    const response = await entityListConfigApi.getByEntityId(targetEntityId)
    referenceListOptions.value = Array.isArray(response)
      ? response
      : response?.records || response?.list || response?.data || []
  } catch (error) {
    console.error('加载实体引用列表失败:', error)
    referenceListOptions.value = []
  }
}

async function loadDataSources() {
  try {
    const [globalSources, formSources, entitySources] = await Promise.all([
      uiDataSourceApi.list({ scopeType: 'GLOBAL' }).catch(() => []),
      form.value.id
        ? uiDataSourceApi.list({ scopeType: 'FORM', scopeId: form.value.id }).catch(() => [])
        : Promise.resolve([]),
      form.value.entityId
        ? uiDataSourceApi.list({ scopeType: 'ENTITY', scopeId: form.value.entityId }).catch(() => [])
        : Promise.resolve([])
    ])
    const unique = new Map()
    ;[...globalSources, ...formSources, ...entitySources].forEach(source => {
      if (source?.enabled !== false) unique.set(source.id, source)
    })
    dataSources.value = [...unique.values()]
  } catch (error) {
    console.error('加载统一数据源失败:', error)
    dataSources.value = []
  }
}

async function loadComponentTemplates() {
  try {
    componentTemplates.value = await uiComponentTemplateApi.list()
  } catch {
    componentTemplates.value = []
  }
}

async function handleTemplateChange(templateId) {
  if (!selectedField.value || !templateId) {
    if (selectedField.value) {
      selectedField.value.templateVersion = null
      selectedField.value.localOverrides = {}
    }
    return
  }
  const template = componentTemplates.value.find(item => item.id === templateId)
  const versions = await uiComponentTemplateApi.versions(templateId)
  const latest = versions.find(item => item.version === template?.currentVersion)
    || versions[0]
  if (!latest) return
  const snapshot = parseDocument(latest.snapshotDocument)
  const props = snapshot.props || snapshot
  Object.assign(selectedField.value, props)
  selectedField.value.templateVersion = latest.version
  selectedField.value.localOverrides = {}
  ElMessage.success(`已锁定模板 v${latest.version}，不会自动跟随升级`)
}

async function upgradeSelectedTemplate() {
  const field = selectedField.value
  if (!field?.templateId) return
  const template = componentTemplates.value.find(item => item.id === field.templateId)
  if (!template || template.currentVersion === field.templateVersion) {
    ElMessage.info('当前已是最新模板版本')
    return
  }
  const result = await uiComponentTemplateApi.upgrade(field.templateId, {
    fromVersion: field.templateVersion,
    toVersion: template.currentVersion,
    currentSnapshot: fieldToNodePayload(field).props,
    localOverrides: field.localOverrides || {}
  })
  if (result.requiresConfirmation) {
    try {
      await ElMessageBox.confirm(
        `以下配置同时被模板和本地修改：${result.conflicts.join('、')}。继续后保留当前节点的本地值。`,
        '确认模板升级',
        {
          type: 'warning',
          confirmButtonText: '保留本地值并升级',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
  }
  Object.assign(field, result.mergedSnapshot?.props || result.mergedSnapshot || {})
  field.templateId = template.id
  field.templateVersion = template.currentVersion
  await saveSelectedNode()
  ElMessage.success(`已保存模板升级 v${template.currentVersion}`)
}

async function loadDiff() {
  if (!form.value.id) {
    diffInfo.value = { changed: true, changedSections: ['form', 'nodes'] }
    return
  }
  try {
    diffInfo.value = await getFormDiff(form.value.id)
  } catch {
    diffInfo.value = { changed: true, changedSections: [] }
  }
}

function handleRevisionConflict(error, field) {
  if (error?.status === 409 || error?.errorCode === 'CONFIG_REVISION_CONFLICT') {
    ElMessage.warning('配置已被其他人修改，已保留服务器当前版本，请重新确认')
    if (field && error.currentData) {
      const refreshed = nodeToField(error.currentData, field)
      Object.assign(field, refreshed)
    }
    return true
  }
  ElMessage.error(error?.message || '保存失败')
  return false
}

async function refreshDraftStateAfterSaveFailure() {
  if (!form.value.id) return
  try {
    await loadFormInfo()
    await loadFormFields()
    await loadDiff()
  } catch (error) {
    console.error('保存失败后刷新草稿状态失败:', error)
  }
}

async function ensureFormMetadata() {
  if (form.value.id) return form.value.id
  const eid = entityId || form.value.entityId
  if (!form.value.formName || !form.value.formKey || !eid) {
    throw new Error('请先填写表单名称、标识和实体')
  }
  const created = await createForm({
    ...form.value,
    entityId: eid,
    viewConfig: stringifyConfig(viewConfig.value)
  })
  form.value = { ...form.value, ...created }
  isEdit.value = true
  return created.id
}

async function saveSelectedNode() {
  if (!selectedField.value) return
  savingNode.value = true
  try {
    const currentFormId = await ensureFormMetadata()
    await ensureChildFormReleaseBinding(selectedField.value)
    const payload = fieldToNodePayload(selectedField.value)
    let saved
    if (selectedField.value.revision > 0) {
      saved = await patchFormNode(
        currentFormId,
        selectedField.value.id,
        {
          expectedRevision: selectedField.value.revision,
          ...payload
        }
      )
    } else {
      saved = await createFormNode(currentFormId, payload)
    }
    const refreshed = nodeToField(saved, selectedField.value)
    Object.assign(selectedField.value, refreshed)
    rememberNodeBaseline(selectedField.value)
    const latest = await getFormById(currentFormId)
    form.value.revision = latest.revision
    await loadDiff()
    ElMessage.success('当前节点已保存，尚未发布')
  } catch (error) {
    handleRevisionConflict(error, selectedField.value)
  } finally {
    savingNode.value = false
  }
}

async function handlePublish() {
  if (!form.value.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  try {
    const diff = await getFormDiff(form.value.id)
    if (!diff.changed) {
      ElMessage.info('当前草稿与已发布版本一致')
      return
    }
    await ElMessageBox.confirm(
      `将发布 ${describePublishChanges(diff)}，运行时会原子切换到新版本。`,
      '发布表单',
      { type: 'warning' }
    )
    await publishForm(form.value.id, '设计器发布')
    await loadDiff()
    ElMessage.success('表单发布成功')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '发布失败')
    }
  }
}

function describePublishChanges(diff) {
  const labels = (diff.changedItems || [])
    .slice(0, 8)
    .map(item => `${changeTypeLabel(item.changeType)}${item.label || item.id}`)
  if (labels.length) {
    const remaining = Math.max(0, (diff.changedItems?.length || 0) - labels.length)
    return `${labels.join('、')}${remaining ? `等 ${remaining + labels.length} 项` : ''}`
  }
  return diff.changedSections?.join('、') || '当前草稿'
}

function changeTypeLabel(changeType) {
  return {
    ADDED: '新增：',
    UPDATED: '修改：',
    MOVED: '移动：',
    REMOVED: '删除：'
  }[changeType] || '修改：'
}

async function showReleaseHistory() {
  if (!form.value.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  releases.value = await getFormReleases(form.value.id)
  releaseDialogVisible.value = true
}

async function activateRelease(release) {
  await ElMessageBox.confirm(
    `确认激活历史版本 v${release.version}？`,
    '回滚发布版本',
    { type: 'warning' }
  )
  await activateFormRelease(form.value.id, release.id)
  await showReleaseHistory()
  await loadDiff()
  ElMessage.success('历史版本已激活')
}

// 保存表单
async function handleSave() {
  if (!form.value.formName) {
    ElMessage.warning('请输入表单名称')
    return
  }
  if (!form.value.formKey) {
    ElMessage.warning('请输入表单标识')
    return
  }
  
  const eid = entityId || form.value.entityId
  if (!form.value.entityId && eid) {
    form.value.entityId = eid
  }
  
  if (formFields.value.length === 0) {
    ElMessage.warning('请至少添加一个字段')
    return
  }
  
  saving.value = true
  let draftChanged = false
  try {
    let currentFormId
    if (form.value.id) {
      const updated = await patchFormMetadata(form.value.id, {
        expectedRevision: form.value.revision,
        formName: form.value.formName,
        description: form.value.description,
        layoutType: form.value.layoutType,
        isDefault: form.value.isDefault,
        status: form.value.status,
        customComponent: form.value.customComponent,
        customComponentVersion: form.value.customComponentVersion,
        customComponentSnapshotVersion:
          form.value.customComponentSnapshotVersion,
        initConfig: safeParseConfig(form.value.initConfig),
        viewConfig: viewConfig.value
      })
      form.value = { ...form.value, ...updated }
      currentFormId = form.value.id
      draftChanged = true
    } else {
      currentFormId = await ensureFormMetadata()
      draftChanged = true
    }

    for (const field of formFields.value) {
      await ensureChildFormReleaseBinding(field)
      const payload = fieldToNodePayload(field)
      let saved = null
      if (!field.revision) {
        saved = await createFormNode(currentFormId, payload)
      } else if (nodeBaselines.value.get(field.id) !== nodeFingerprint(field)) {
        saved = await patchFormNode(currentFormId, field.id, {
          expectedRevision: field.revision,
          ...payload
        })
      }
      if (saved) {
        Object.assign(field, nodeToField(saved, field))
        rememberNodeBaseline(field)
        draftChanged = true
      }
    }
    await loadFormInfo()
    await loadFormFields()
    await loadDiff()
    ElMessage.success('草稿保存成功，发布后运行时生效')
  } catch (e) {
    console.error('保存失败:', {
      message: e?.message,
      status: e?.status,
      errorCode: e?.errorCode,
      source: e?.source
    })
    if (draftChanged) {
      await refreshDraftStateAfterSaveFailure()
    }
    handleRevisionConflict(e)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadEntityInfo()
  await loadFormInfo()
  await loadEntityFields()
  // 先加载实体列表，确保 el-select 有选项后再加载并恢复表单字段
  await loadEntityList()
  await loadFormFields()
  await loadDataSources()
  await loadComponentTemplates()
  await loadExtensionDefinitions()
  await loadDiff()
})
</script>

<style scoped>
.entity-form-design {
  height: 100vh;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

.design-header {
  min-height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #dcdfe6;
  background-color: #fff;
}

.header-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 15px;
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.title {
  font-size: 16px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-data-source-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.form-data-source-toolbar :deep(.el-alert) {
  flex: 1;
}

.form-data-source-row {
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fafafa;
}

.form-data-source-row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.form-data-source-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-data-source-prevalidate {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 0 12px 96px;
  color: #606266;
  font-size: 12px;
}

.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧字段面板 */
.field-panel {
  width: 260px;
  border-right: 1px solid #dcdfe6;
  background-color: #fff;
  display: flex;
  flex-direction: column;
}

.panel-title {
  height: 44px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  font-weight: 500;
  font-size: 14px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #f5f7fa;
}

.field-search {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.field-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 6px;
  background-color: #fff;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #e4e7ed;
}

.field-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.field-item.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background-color: #f5f7fa;
}

.field-item.disabled:hover {
  border-color: #e4e7ed;
  box-shadow: none;
}

.mode-access-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.mode-access-row {
  display: grid;
  grid-template-columns: 64px 1fr 1fr;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

.field-info {
  flex: 1;
  min-width: 0;
}

.field-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.field-code {
  font-size: 11px;
  color: #909399;
}

.field-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}

.field-tags .el-tag {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 16px;
}

/* 中间画布 */
.canvas-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.canvas-panel > .panel-title {
  height: auto;
  min-height: 44px;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.layout-selector {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.form-basic-info {
  padding: 12px 20px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.form-canvas-wrapper {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: #f0f2f5;
}

.form-canvas {
  min-height: 400px;
  background-color: #fff;
  border-radius: 4px;
  padding: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

/* 设计表单样式 */
.design-form {
  display: flex;
  flex-wrap: wrap;
}

.form-field-wrapper {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  margin-bottom: 8px;
  background-color: #fff;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.form-field-wrapper:hover {
  border-color: #c0c4cc;
}

.form-field-wrapper.active {
  border-color: #409eff;
  background-color: #f5f7fa;
}

/* 垂直布局 */
.form-canvas.vertical .form-field-wrapper {
  width: 100%;
}

/* 水平布局 */
.form-canvas.horizontal .design-form {
  gap: 20px;
}

.form-canvas.horizontal .form-field-wrapper {
  width: calc(50% - 10px);
}

/* 网格布局 */
.form-canvas.grid .design-form {
  gap: 0;
}

.form-canvas.grid .form-field-wrapper.grid-item {
  padding: 8px;
  margin-bottom: 0;
}

.field-order {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #409eff;
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  margin-right: 12px;
  flex-shrink: 0;
  margin-top: 8px;
}

.field-content {
  flex: 1;
  min-width: 0;
}

.node-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #909399;
  font-size: 11px;
}

/* 节字段在画布中占满一行，且不显示表单项标签 */
.form-field-wrapper.section-field-wrapper {
  width: 100% !important;
  align-items: center;
}

.form-field-wrapper.section-field-wrapper .field-content {
  flex: 1;
}

.design-form-item {
  margin-bottom: 0 !important;
}

.design-form-item :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}

.field-actions {
  margin-left: 12px;
  opacity: 0;
  transition: opacity 0.2s;
  padding-top: 4px;
}

.form-field-wrapper:hover .field-actions,
.form-field-wrapper.active .field-actions {
  opacity: 1;
}

.empty-tip {
  padding: 80px 0;
}

/* 右侧属性面板 */
.property-panel {
  width: 280px;
  border-left: 1px solid #dcdfe6;
  background-color: #fff;
  display: flex;
  flex-direction: column;
}

.property-form {
  padding: 16px;
}

.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.relation-summary {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  padding: 8px 0 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.relation-summary div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  line-height: 20px;
}

.relation-summary span {
  color: #909399;
}

.relation-summary strong {
  max-width: 150px;
  overflow: hidden;
  color: #303133;
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slider-value {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.empty-property {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 1300px) {
  .entity-form-design {
    height: auto;
    min-height: 100vh;
  }

  .design-header {
    padding: 10px 12px;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .header-left,
  .header-right {
    width: 100%;
  }

  .header-right {
    justify-content: flex-start;
  }

  .design-body {
    display: grid;
    grid-template-columns: minmax(190px, 220px) minmax(0, 1fr);
    grid-template-areas:
      "fields canvas"
      "properties properties";
    overflow: visible;
  }

  .field-panel {
    grid-area: fields;
    width: auto;
    min-width: 0;
    min-height: 560px;
  }

  .canvas-panel {
    grid-area: canvas;
    overflow: visible;
  }

  .property-panel {
    grid-area: properties;
    width: auto;
    min-width: 0;
    border-top: 1px solid #dcdfe6;
    border-left: 0;
  }

  .property-panel :deep(.el-scrollbar) {
    height: auto !important;
    max-height: none !important;
  }

  .form-basic-info {
    padding: 12px;
  }

  .form-basic-info :deep(.el-form--inline .el-form-item) {
    margin-right: 12px;
  }

  .form-canvas-wrapper {
    padding: 12px;
  }

  .form-canvas {
    padding: 16px;
  }
}

@media (max-width: 900px) {
  .design-body {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      "fields"
      "canvas"
      "properties";
  }

  .field-panel {
    min-height: 0;
    max-height: 340px;
    border-right: 0;
    border-bottom: 1px solid #dcdfe6;
  }

  .layout-selector {
    justify-content: flex-start;
  }
}

/* 预览容器 */
.preview-container {
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}
</style>
