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
        <el-button
          :loading="runtimeCodeLoading"
          :disabled="initializing"
          @click="openRuntimeCode"
        >
          <el-icon><Document /></el-icon>查看最终代码
        </el-button>
        <el-button @click="showPreview = true">
          <el-icon><View /></el-icon>预览
        </el-button>
        <el-button @click="showReleaseHistory">版本</el-button>
        <el-button type="success" plain @click="handlePublish" :disabled="!isEdit">
          发布
        </el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          <el-icon><Check /></el-icon>保存全部草稿
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="isSystemEntity"
      title="平台系统表结构只读。这里仅配置详情查看布局、中文标签、分组、页签、格式化与显隐。"
      type="warning"
      :closable="false"
      show-icon
      class="system-config-alert"
    />

    <div class="design-body">
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

      <div class="canvas-panel">
        <div class="panel-title">
          <span>表单设计（所见即所得）</span>
          <div class="layout-selector">
            <el-radio-group v-model="form.layoutType" size="small">
              <el-radio-button value="vertical">垂直</el-radio-button>
              <el-radio-button value="horizontal">水平</el-radio-button>
              <el-radio-button value="grid">网格</el-radio-button>
            </el-radio-group>
            <el-dropdown trigger="click" @command="handleAddNodeCommand">
              <el-button type="primary" size="small" style="margin-left: 12px">
                <el-icon><Plus /></el-icon>添加节点
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="SECTION_TITLE">节</el-dropdown-item>
                  <el-dropdown-item command="SECTION">区块</el-dropdown-item>
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
            <el-button size="small" @click="openFormSettings('basic')">
              <el-icon><Setting /></el-icon>表单设置
            </el-button>
          </div>
        </div>

        <div class="form-canvas-wrapper">
          <div class="form-canvas" :class="form.layoutType">
            <div v-if="formFields.length" class="form-drag-guide">
              <el-icon><Rank /></el-icon>
              <span>拖拽节点右上角手柄调整顺序，或移动到兼容容器；位置保存到草稿，发布后生效。</span>
            </div>
            <div v-if="formFields.length === 0" class="empty-tip">
              <el-empty description="点击左侧字段添加到表单">
                <template #image>
                  <el-icon :size="60" color="#dcdfe6"><DocumentAdd /></el-icon>
                </template>
              </el-empty>
            </div>
            
            <el-form v-else :label-width="formLabelWidth" :label-position="formLabelPosition" class="design-form">
              <FormNodeDraggableList
                :items="rootDesignNodes"
                parent-id=""
                :can-drop="canDropNode"
                :disabled="reorderingNode"
                zone-class="root-design-drop-zone"
                @drop="handleNodeDrop"
              >
                <template #item="{ element: field, index }">
                  <FormNodeDesignItem
                    :node="field"
                    :sibling-index="index"
                    :sibling-count="rootDesignNodes.length"
                    :selected-node-id="selectedField?.id"
                    :layout-type="form.layoutType"
                    :children-for="designChildrenFor"
                    :node-span-for="getNodeSpan"
                    :node-style-for="getNodeDesignStyle"
                    :legacy-node-type="legacyNodeType"
                    :node-label="nodeLabel"
                    :can-drop-node="canDropNode"
                    :drag-disabled="reorderingNode"
                    @select="selectField"
                    @open-properties="openFieldProperties"
                    @move="moveNode"
                    @remove="removeNode"
                    @drop="handleNodeDrop"
                  />
                </template>
              </FormNodeDraggableList>
            </el-form>
          </div>
        </div>
      </div>

      <el-drawer
        v-model="propertyDrawerVisible"
        title="节点属性"
        direction="rtl"
        size="33.333333vw"
        append-to-body
        class="node-property-drawer"
      >
        <template v-if="selectedField">
          <div class="node-summary">
            <div class="node-summary-heading">
              <strong>{{ selectedField.fieldLabel || selectedField.fieldName || selectedField.fieldCode }}</strong>
              <el-tag size="small" effect="plain">{{ selectedNodeTypeLabel }}</el-tag>
              <el-tag
                size="small"
                :type="selectedNodeDirty ? 'warning' : 'success'"
                effect="plain"
              >
                {{ selectedNodeDirty ? '未保存' : '已保存' }}
              </el-tag>
            </div>
            <dl class="node-summary-meta">
              <div>
                <dt>绑定</dt>
                <dd>{{ selectedNodeBindingLabel }}</dd>
              </div>
              <div>
                <dt>编码</dt>
                <dd>{{ selectedField.fieldCode || selectedField.nodeKey || '-' }}</dd>
              </div>
              <div>
                <dt>父级</dt>
                <dd>{{ selectedNodeParentLabel }}</dd>
              </div>
            </dl>
            <p>{{ selectedNodeLockMessage }}</p>
          </div>
          <el-tabs
            v-model="activeNodeSettingsTab"
            class="node-settings-tabs"
            stretch
          >
            <el-tab-pane
              v-for="tab in availableNodeSettingsTabs"
              :key="tab.value"
              :label="tab.label"
              :name="tab.value"
            />
          </el-tabs>
          
          <el-scrollbar height="calc(100vh - 250px)">
            <el-form label-width="90px" size="small" class="property-form">
              <SettingsSection
                v-show="activeNodeSettingsTab === 'basic'"
                title="基础属性"
                description="当前节点最常修改的显示、组件、占位和层级配置"
                :collapsible="false"
                primary
              >
                <template #summary>
                  <el-tag size="small" type="primary">{{ selectedNodeTypeLabel }}</el-tag>
                </template>

                <el-form-item v-if="canEditNodeLabel" :label="isSelectedSection ? '节标题' : '显示标签'">
                  <el-input v-model="selectedField.fieldLabel" />
                </el-form-item>

                <el-form-item
                  v-if="selectedNodeType === 'TEXT'"
                  :label="isSectionTitleNode ? '节名称' : '说明内容'"
                >
                  <el-input
                    :model-value="selectedNodeConfig.text || selectedNodeConfig.content || ''"
                    :type="isSectionTitleNode ? 'text' : 'textarea'"
                    :rows="isSectionTitleNode ? undefined : 4"
                    :placeholder="isSectionTitleNode ? '请输入节名称' : '请输入说明内容'"
                    @update:model-value="updateSelectedNodeConfig('text', $event)"
                  />
                </el-form-item>

                <template v-if="isFieldNode">
                  <el-form-item label="组件类型">
                    <el-select v-model="selectedField.componentType" style="width: 100%" @change="handleCompatibleComponentChange">
                      <el-option
                        v-for="option in availableFormFieldComponentOptions"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="占位提示">
                    <el-input v-model="selectedField.placeholder" placeholder="提示文字" />
                  </el-form-item>
                </template>

                <el-form-item label="栅格宽度" v-if="canEditGridSpan">
                  <el-slider v-model="selectedField.gridSpan" :min="1" :max="24" show-stops />
                  <span class="slider-value">{{ selectedField.gridSpan }}/24</span>
                </el-form-item>

                <el-form-item
                  :label="isTabNode ? '所属 Tab 集合' : '父容器'"
                  :required="isTabNode"
                >
                  <el-select
                    :model-value="selectedParentValue"
                    :placeholder="isTabNode ? '请选择 Tab 集合' : '请选择父容器'"
                    filterable
                    style="width: 100%"
                    no-data-text="没有可用的父容器"
                    @change="handleParentChange"
                  >
                    <el-option
                      v-if="canMoveSelectedNodeToRoot"
                      label="表单根节点"
                      :value="ROOT_PARENT_VALUE"
                    />
                    <el-option
                      v-for="parent in availableParentNodes"
                      :key="parent.id"
                      :label="parent.label"
                      :value="parent.id"
                    />
                  </el-select>
                  <div class="form-tip">
                    {{ selectedParentHelp }}
                  </div>
                </el-form-item>
              </SettingsSection>

              <SettingsSection
                v-if="hasNodeSpecificConfig"
                v-show="activeNodeSettingsTab === 'basic'"
                title="布局与层级"
                description="栅格参数和容器展示方式"
                :default-expanded="isTabNode || ['GRID', 'TAB_SET', 'COLLAPSE'].includes(selectedNodeType)"
              >
                <template #summary>
                  <el-tag size="small" type="info">{{ selectedNodeTypeLabel }}</el-tag>
                </template>

                <el-form-item v-if="selectedNodeType === 'GRID'" label="列间距">
                  <el-input-number
                    :model-value="Number(selectedNodeConfig.gutter || 16)"
                    :min="0"
                    :max="48"
                    @update:model-value="updateSelectedNodeConfig('gutter', $event)"
                  />
                </el-form-item>
                <el-form-item v-if="selectedNodeType === 'GRID'" label="默认跨度">
                  <el-input-number
                    :model-value="Number(selectedNodeConfig.defaultSpan || 12)"
                    :min="1"
                    :max="24"
                    @update:model-value="updateSelectedNodeConfig('defaultSpan', $event)"
                  />
                </el-form-item>
                <el-form-item v-if="selectedNodeType === 'TAB_SET'" label="页签位置">
                  <el-select
                    :model-value="selectedNodeConfig.tabPosition || 'top'"
                    @update:model-value="updateSelectedNodeConfig('tabPosition', $event)"
                  >
                    <el-option label="顶部" value="top" />
                    <el-option label="左侧" value="left" />
                    <el-option label="右侧" value="right" />
                    <el-option label="底部" value="bottom" />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="selectedNodeType === 'COLLAPSE'" label="默认展开">
                  <el-switch
                    :model-value="selectedNodeConfig.defaultExpanded !== false"
                    @update:model-value="updateSelectedNodeConfig('defaultExpanded', $event)"
                  />
                </el-form-item>
                <el-form-item v-if="selectedNodeType === 'COLLAPSE'" label="手风琴模式">
                  <el-switch
                    :model-value="selectedNodeConfig.accordion === true"
                    @update:model-value="updateSelectedNodeConfig('accordion', $event)"
                  />
                </el-form-item>
                <div class="form-tip">
                  同级排序请在画布中调整，技术标识和节点类型不可直接修改。
                </div>
              </SettingsSection>

              <FormNodeDataSettings />

              <SettingsSection
                v-if="isFieldNode"
                v-show="activeNodeSettingsTab === 'rules'"
                title="默认状态"
                description="定义没有模式覆盖或联动条件时的基础状态"
                :collapsible="false"
                primary
              >
                <el-form-item label="字段状态">
                  <div class="checkbox-group">
                    <el-checkbox
                      v-model="selectedField.isRequired"
                      :true-label="1"
                      :false-label="0"
                    >
                      必填
                    </el-checkbox>
                    <el-checkbox
                      v-model="selectedField.isReadonly"
                      :true-label="1"
                      :false-label="0"
                    >
                      只读
                    </el-checkbox>
                    <el-checkbox
                      v-model="selectedField.isHidden"
                      :true-label="1"
                      :false-label="0"
                    >
                      隐藏
                    </el-checkbox>
                  </div>
                </el-form-item>
                <el-alert
                  type="info"
                  :closable="false"
                  title="显示：默认隐藏 → 模式显示权限 → 条件显示；编辑：整表只读 → 查看模式 → 默认只读 → 模式编辑权限 → 条件禁用；必填：配置条件必填时使用条件结果，否则使用默认必填。"
                />
                <div class="rule-bridge">
                  <div>
                    <strong>条件状态</strong>
                    <p>条件显示、条件禁用和条件必填在统一联动编辑器中维护。</p>
                  </div>
                  <el-button
                    type="primary"
                    plain
                    @click="openNodeInteractionTab('state')"
                  >
                    配置条件状态
                  </el-button>
                </div>
              </SettingsSection>

              <SettingsSection
                v-if="canConfigureSelectedNodeValidation"
                v-show="activeNodeSettingsTab === 'rules'"
                title="校验规则"
                description="仅显示当前字段数据类型支持的结构化规则"
              >
                <template #summary>
                  <el-tag size="small" :type="selectedValidationRuleCount ? 'success' : 'info'">
                    {{ selectedValidationRuleCount ? `${selectedValidationRuleCount} 项规则` : '未配置' }}
                  </el-tag>
                </template>

                <el-form-item
                  v-if="selectedValidationCapabilities.length"
                  label="最小长度"
                >
                  <el-input-number
                    :model-value="selectedValidationConfig.minLength"
                    :min="0"
                    :max="20000"
                    @update:model-value="updateValidationConfig('minLength', $event)"
                  />
                </el-form-item>
                <el-form-item
                  v-if="selectedValidationCapabilities.length"
                  label="最大长度"
                >
                  <el-input-number
                    :model-value="selectedValidationConfig.maxLength"
                    :min="0"
                    :max="20000"
                    @update:model-value="updateValidationConfig('maxLength', $event)"
                  />
                </el-form-item>
                <el-form-item
                  v-if="canConfigureSelectedWordLimit"
                  label="显示字数"
                >
                  <el-switch
                    :model-value="selectedWordLimitVisible"
                    @update:model-value="updateSelectedNodeConfig('showWordLimit', $event)"
                  />
                </el-form-item>
                <el-form-item
                  v-if="selectedValidationCapabilities.range"
                  label="最小值"
                >
                  <el-input-number
                    :model-value="selectedValidationConfig.min"
                    @update:model-value="updateValidationConfig('min', $event)"
                  />
                </el-form-item>
                <el-form-item
                  v-if="selectedValidationCapabilities.range"
                  label="最大值"
                >
                  <el-input-number
                    :model-value="selectedValidationConfig.max"
                    @update:model-value="updateValidationConfig('max', $event)"
                  />
                </el-form-item>
                <el-form-item
                  v-if="selectedValidationCapabilities.format"
                  label="格式"
                >
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
                <el-form-item
                  v-if="selectedValidationCapabilities.pattern"
                  :error="selectedPatternError"
                >
                  <template #label>
                    <ConfigHelpLabel
                      label="正则"
                      content="输入 JavaScript/Java 通用的正则表达式本体，不要添加 / 包裹。需要校验完整内容时请使用 ^ 和 $；与“格式”同时配置时必须全部通过。"
                    />
                  </template>
                  <el-input
                    :model-value="selectedValidationConfig.pattern || ''"
                    clearable
                    :maxlength="500"
                    placeholder="例如：^[A-Z][A-Z0-9_]*$"
                    @update:model-value="updateValidationConfig('pattern', $event)"
                  />
                </el-form-item>
              </SettingsSection>

              <SettingsSection
                v-if="canConfigureSelectedNodeModeAccess"
                v-show="activeNodeSettingsTab === 'rules'"
                title="运行模式权限"
                description="分别控制新增、编辑、审批和查看模式下的显示与编辑"
              >
                <template #summary>
                  <el-tag size="small" type="info">4 种运行模式</el-tag>
                </template>

                <div class="mode-access-grid">
                  <div v-for="modeOption in modeOptions" :key="modeOption.value" class="mode-access-row">
                    <span>{{ modeOption.label }}</span>
                    <el-checkbox
                      :model-value="getModeAccessValue(modeOption.value, 'visible')"
                      @change="updateModeAccess(modeOption.value, 'visible', $event)"
                    >显示</el-checkbox>
                    <el-checkbox
                      v-if="modeOption.editable !== false"
                      :model-value="getModeAccessValue(modeOption.value, 'editable')"
                      @change="updateModeAccess(modeOption.value, 'editable', $event)"
                    >可编辑</el-checkbox>
                  </div>
                </div>
                <div class="mode-access-tip">
                  审批可编辑：字段在审批办理时的默认编辑权限，流程节点开启“强制整表只读”后本配置不生效。
                  查看模式固定只读，仅控制字段是否显示。
                </div>
              </SettingsSection>

              <SettingsSection
                v-if="isFieldNode"
                v-show="activeNodeSettingsTab === 'interaction'"
                title="联动与事件"
                description="集中配置状态、值、选择回填和事件执行链"
                :collapsible="false"
                primary
              >
                <template #summary>
                  <el-tag
                    size="small"
                    :type="hasEventConfig ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ hasEventConfig ? '已配置事件' : '按需配置' }}
                  </el-tag>
                </template>

                <el-tabs
                  v-model="activeNodeInteractionTab"
                  class="node-interaction-tabs"
                >
                  <el-tab-pane label="状态联动" name="state">
                    <div class="interaction-entry">
                      <p>根据其他字段动态控制当前字段显示、禁用或必填。</p>
                      <el-button
                        type="primary"
                        plain
                        @click="openLinkageConfig('display-state')"
                      >
                        配置状态联动
                      </el-button>
                    </div>
                  </el-tab-pane>
                  <el-tab-pane label="值与计算" name="value">
                    <div class="interaction-entry">
                      <p>配置字段值映射、计算公式、选项联动和历史兼容接口。</p>
                      <el-button
                        type="primary"
                        plain
                        @click="openLinkageConfig('value-calculation')"
                      >
                        配置值与计算
                      </el-button>
                    </div>
                  </el-tab-pane>
                  <el-tab-pane
                    label="选择与回填"
                    name="selection"
                    :disabled="!isSingleEntityReferenceField"
                  >
                    <div class="interaction-entry">
                      <p>
                        使用 ENTITY_SELECTED 将关联记录字段回填到当前表单，也可以继续追加完整接口执行链。
                      </p>
                      <div class="interaction-actions">
                        <el-button
                          type="primary"
                          plain
                          @click="openEntitySelectionMapping"
                        >
                          配置快捷回填
                        </el-button>
                        <el-button
                          plain
                          @click="openUnifiedEventBindings('FIELD')"
                        >
                          配置选择事件链
                        </el-button>
                      </div>
                    </div>
                  </el-tab-pane>
                  <el-tab-pane label="事件执行链" name="events">
                    <div class="interaction-entry">
                      <p>
                        旧字段事件和统一接口链可以并存；接口链支持前置、替代、后置、条件、映射和失败策略。
                      </p>
                      <div class="interaction-actions">
                        <el-button plain @click="openEventConfig">
                          配置字段事件
                        </el-button>
                        <el-button
                          type="primary"
                          plain
                          @click="openUnifiedEventBindings('FIELD')"
                        >
                          配置接口链
                        </el-button>
                      </div>
                    </div>
                  </el-tab-pane>
                </el-tabs>
              </SettingsSection>

              <SettingsSection
                v-if="canConfigureNodeExtension || isEditableFieldNode || isFieldNode"
                v-show="activeNodeSettingsTab === 'extension'"
                title="复用与扩展"
                description="节点扩展、组件模板和组件参数"
                :default-expanded="!!selectedField.componentName || !!selectedField.templateId"
              >
                <template #summary>
                  <el-tag
                    size="small"
                    :type="selectedField.componentName || selectedField.templateId ? 'success' : 'info'"
                  >
                    {{ selectedField.componentName || selectedField.templateId ? '已配置' : '未配置' }}
                  </el-tag>
                </template>

                <el-form-item v-if="canConfigureNodeExtension" label="节点扩展">
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

                <template v-if="isEditableFieldNode">
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
                </template>

                <template v-if="isFieldNode && selectedComponentSchema.length">
                  <div class="property-subheading">组件参数</div>
                  <ConfigSchemaEditor
                    v-model="selectedComponentConfig"
                    :schema="selectedComponentSchema"
                  />
                </template>
              </SettingsSection>
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
        <template #footer>
          <div v-if="selectedField" class="node-property-actions">
            <span>只保存当前节点，其他未保存修改继续保留。</span>
            <el-button
              type="primary"
              :loading="savingNode"
              @click="saveSelectedNode"
            >
              <el-icon><Check /></el-icon>保存当前节点
            </el-button>
          </div>
        </template>
      </el-drawer>
    </div>

    <FormDesignerSettingsDrawer
      v-model="showFormSettings"
      v-model:active-tab="activeFormSettingsTab"
    />

    <el-dialog v-model="showPreview" title="表单预览" width="900px" destroy-on-close>
      <div class="preview-mode-toolbar">
        <span>预览模式</span>
        <el-segmented
          v-model="previewMode"
          :options="previewModeOptions"
        />
      </div>
      <div class="preview-container">
        <FormPreviewLinkage
          :form="previewForm"
          :mode="previewMode"
          :readonly="previewMode === 'view' || isSystemEntity"
          :entity-code="entityInfo.entityCode || ''"
          :entity-definition="entityInfo"
          :entity-fields="entityFields"
          :form-actions="previewActions"
          @form-action="handlePreviewAction"
        />
      </div>
      <template #footer>
        <FormActionBar
          :actions="previewFooterActions"
          @action="handlePreviewAction"
        />
      </template>
    </el-dialog>
    
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
        :all-fields="entityFields.filter(f => f.uiConfigurable !== false)"
        :initial-tab="linkageInitialTab"
        @save="handleSaveLinkage"
      />
    </el-dialog>

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
        <el-button type="primary" @click="showFormExtensionConfig = false">关闭扩展设置</el-button>
      </template>
    </el-dialog>

    <FormDataSourceCompatDialog
      ref="formDataSourceDialogRef"
      :form="form"
      :data-sources="dataSources"
      @saved="handleFormDataSourceSaved"
      @error="handleRevisionConflict"
    />

    <UiConfigPublishDialog
      v-model="publishDialogVisible"
      config-type="FORM"
      :config-id="form.id || ''"
      config-label="表单"
      @published="handlePublished"
    />

    <EventBindingDialog
      ref="eventBindingDialogRef"
      owner-type="FORM"
      :owner-id="form.id || ''"
      owner-label="表单"
      :field-options="eventFieldOptions"
    />
    <EntitySelectionMappingDialog
      ref="selectionMappingDialogRef"
      :form-id="form.id || ''"
      :form-fields="formFields"
      @changed="loadDiff"
    />
    <UiConfigReleaseHistoryDialog
      ref="releaseHistoryDialogRef"
      config-type="FORM"
      :config-id="form.id || ''"
      config-label="表单"
      @changed="handleReleaseChanged"
    />
    <RuntimeCodeViewerDialog ref="runtimeCodeDialogRef" />

  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, View, Search, Document, Edit, DocumentAdd, Plus, Connection, Rank, Setting } from '@element-plus/icons-vue'
import FormNodeDesignItem from '@/components/FormNodeDesignItem.vue'
import FormNodeDraggableList from '@/components/FormNodeDraggableList.vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import FormActionBar from '@/components/FormActionBar.vue'
import LinkageConfigPanel from '@/components/LinkageConfigPanel.vue'
import EventConfigPanel from '@/components/EventConfigPanel.vue'
import EventBindingDialog from '@/components/ui-config/EventBindingDialog.vue'
import EntitySelectionMappingDialog from '@/components/ui-config/EntitySelectionMappingDialog.vue'
import FormDataSourceCompatDialog from '@/components/ui-config/FormDataSourceCompatDialog.vue'
import UiConfigReleaseHistoryDialog from '@/components/ui-config/UiConfigReleaseHistoryDialog.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import UiConfigPublishDialog from '@/components/UiConfigPublishDialog.vue'
import FormDesignerSettingsDrawer from '@/components/form-designer/FormDesignerSettingsDrawer.vue'
import FormNodeDataSettings from '@/components/form-designer/FormNodeDataSettings.vue'
import RuntimeCodeViewerDialog from '@/components/RuntimeCodeViewerDialog.vue'
import { FORM_DESIGNER_CONTEXT_KEY } from '@/components/form-designer/context'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
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
import {
  FORM_NODE_MAX_DEPTH,
  FORM_NODE_ORDER_STEP,
  canContainFormNode,
  canPlaceFormNodeAtRoot,
  formNodeTypeLabel,
  normalizeFormNodeType
} from '@/shared/form-node-hierarchy'
import {
  buildFormNodeDropPlan,
  getFormNodeDepth as getSharedFormNodeDepth,
  getFormNodeSubtreeHeight,
  orderFormNodesParentFirst,
  validateFormNodeDrop
} from '@/shared/form-node-drag'
import {
  buildFormNodePayload,
  extractFormNodeComponentConfig,
  formNodeSupports,
  getFormFieldValidationCapabilities,
  getFormNodeDataSourceUsages,
  getFormNodePropertySchema,
  mergeFormNodeFieldMetadata,
  normalizeFormFieldValidation,
  resolveFormNodeBinding
} from '@/shared/form-node-property-schema'
import {
  getDefaultFormFieldComponentType as getDefaultComponentType
} from '@/shared/form-field-component-policy'
import {
  getRuntimeRegexPatternError,
  safeParseConfig,
  stringifyConfig
} from '@/shared/config-runtime'
import { parseJsonConfig } from '@/utils/jsonConfig'
import {
  filterEntityFieldsByLifecycle,
  getEntityReferenceSelectionHint,
  isWorkflowReady
} from '@/shared/entity-design'
import {
  isParentEntityReferenceTarget,
  isPublishedSubListOption,
  isSubListTargetFieldWritable,
  normalizeSubListDisplayConfig,
  normalizeSubListParameterContract,
  SUB_LIST_ACTION_DISPLAY_VERSION
} from '@/shared/sub-list'
import {
  FORM_ACTION_MODES,
  emptyFormActionBar,
  footerFormActions,
  normalizeFormActionBar,
  resolveLocalFormActions
} from '@/shared/form-actions'
import {
  buildFormDraftRuntimeSnapshot,
  buildRuntimeCodeArtifact,
  selectRuntimeRelease
} from '@/shared/runtime-code-generator'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
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
  replaceFormNodes,
  getFormDiff,
  getFormReleases
} from '@/api/entityForm'
import {
  uiDataSourceApi,
  uiComponentTemplateApi,
  uiExtensionApi,
  uiEventBindingApi
} from '@/api/uiConfig'

const route = useRoute()
const router = useRouter()
const formId = route.params.id
const entityId = route.query.entityId || ''

const isEdit = ref(!!formId)
const initializing = ref(true)
const saving = ref(false)
const savingNode = ref(false)
const reorderingNode = ref(false)
const nodeBaselines = ref(new Map())
const formBaseline = ref('')
const showPreview = ref(false)
const showFormSettings = ref(false)
const activeFormSettingsTab = ref('basic')
const previewMode = ref('create')
const propertyDrawerVisible = ref(false)
const showLinkageConfig = ref(false)
const linkageInitialTab = ref('display-state')
const showEventConfig = ref(false)
const showFormExtensionConfig = ref(false)
const formDataSourceDialogRef = ref(null)
const eventBindingDialogRef = ref(null)
const selectionMappingDialogRef = ref(null)
const releaseHistoryDialogRef = ref(null)
const runtimeCodeDialogRef = ref(null)
const runtimeCodeLoading = ref(false)
const currentEventField = ref(null)
const activeNodeSettingsTab = ref('basic')
const activeNodeInteractionTab = ref('state')
const publishDialogVisible = ref(false)
const diffInfo = ref({ changed: true, changedSections: [] })
const dataSources = ref([])
const extensionDefinitions = ref([])
const selectableCustomFormCatalogOptions = ref([])
const formNodes = ref([])
const lastCustomFormComponent = ref('')
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
const selectedCustomFormCatalogOption = computed(() => {
  const option = customFormOptions.value.find(item =>
    item.value === form.value.customComponent)
  return option
    ? {
        key: option.value,
        displayName: option.label,
        description: option.description
      }
    : null
})
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
  { value: 'view', label: '查看', editable: false }
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
const ROOT_PARENT_VALUE = '__FORM_ROOT__'
const formDataSourceUsages = [
  { value: 'FIELD_OPTIONS', label: '字段选项' },
  { value: 'FIELD_DEFAULT', label: '字段默认值' },
  { value: 'FIELD_COMPUTE', label: '字段计算' },
  { value: 'SUBFORM_ROWS', label: '子表行数据' },
  { value: 'AFTER_LOAD', label: '加载后处理' },
  { value: 'BEFORE_SUBMIT', label: '提交前处理' }
]
const viewConfig = ref({
  labelWidth: 120,
  customComponentProps: {},
  actionBar: emptyFormActionBar()
})

const entityInfo = ref({})
const isSystemEntity = computed(() => entityInfo.value?.storageMode === 'SYSTEM')
watch(isSystemEntity, value => {
  if (!value) return
  previewMode.value = 'view'
  const actionBar = normalizeFormActionBar(viewConfig.value.actionBar)
  viewConfig.value.actionBar = {
    version: 1,
    builtInOverrides: actionBar.builtInOverrides.close
      ? { close: actionBar.builtInOverrides.close }
      : {},
    customButtons: []
  }
})
const entityFields = ref([])
const formFields = ref([])
const selectedField = ref(null)
const fieldSearch = ref('')
const entityNameById = ref({})
const entityCodeById = ref({})
const formListByEntity = ref([])
const childFormReleases = ref([])
const childFormReleaseLoading = ref(false)
const referenceListOptions = ref([])
const subListOptions = ref([])
const subListTargetFields = ref([])
const subListTargetFieldsLoading = ref(false)
let subListTargetFieldLoadSequence = 0
const eventFieldOptions = computed(() =>
  entityFields.value
    .filter(field => field.uiConfigurable !== false)
    .map(field => ({
      label: field.fieldName || field.fieldCode,
      value: field.fieldCode
    }))
)
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
const customFormButtonCount = computed(() =>
  viewConfig.value.actionBar?.customButtons?.filter(button =>
    button.enabled !== false
  ).length || 0
)

const availableNodeExtensionOptions = computed(() => {
  const nodeType = String(selectedField.value?.nodeType || '').toUpperCase()
  const { bindingType } = resolveFormNodeBinding(
    selectedField.value,
    nodeType
  )
  return nodeExtensionOptions.value.filter(option =>
    (!option.nodeTypes?.length || option.nodeTypes.includes(nodeType))
      && (!option.supportedBindings?.length
        || option.supportedBindings
          .map(value => String(value).toUpperCase())
          .includes(bindingType))
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
    lastCustomFormComponent.value = componentName
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

function openFormSettings(tab = 'basic') {
  activeFormSettingsTab.value = tab
  showFormSettings.value = true
}

function handleFormRendererModeChange(mode) {
  if (mode === 'DEFAULT') {
    if (form.value.customComponent) {
      lastCustomFormComponent.value = form.value.customComponent
    }
    form.value.customComponent = ''
    return
  }
  const selectableKeys = new Set(
    selectableCustomFormCatalogOptions.value.map(option => option.key)
  )
  const fallback = selectableKeys.has(lastCustomFormComponent.value)
    ? lastCustomFormComponent.value
    : (selectableCustomFormCatalogOptions.value[0]?.key || '')
  if (!fallback) {
    ElMessage.info('当前实体暂无可用的自定义表单组件')
    return
  }
  form.value.customComponent = fallback
}

function handleCustomFormCatalogLoaded(options = []) {
  selectableCustomFormCatalogOptions.value = options
}

function openExtensionManagement() {
  router.push({
    path: '/system/extensions',
    query: { type: 'UI_FORM' }
  })
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
const canConfigureSelectedWordLimit = computed(() =>
  ['input', 'textarea'].includes(
    String(selectedField.value?.componentType || '').toLowerCase()
  )
)
const selectedWordLimitVisible = computed(() =>
  selectedComponentConfig.value.showWordLimit !== false
)

const selectedNodeType = computed(() =>
  String(selectedField.value?.nodeType || legacyNodeType(selectedField.value) || 'FIELD').toUpperCase()
)
const selectedNodePropertySchema = computed(() =>
  getFormNodePropertySchema(selectedNodeType.value)
)
const hasNodeSpecificConfig = computed(() =>
  selectedNodePropertySchema.value.configKeys.length > 0
)
const isEditableFieldNode = computed(() =>
  ['FIELD', 'SUB_FORM', 'REPEATER'].includes(selectedNodeType.value)
)
const isFieldNode = computed(() => selectedNodeType.value === 'FIELD')
const isTabNode = computed(() => selectedNodeType.value === 'TAB')
const canEditNodeLabel = computed(() =>
  ['SECTION', 'TAB', 'COLLAPSE', 'FIELD', 'SUB_FORM', 'REPEATER'].includes(
    selectedNodeType.value
  )
)
const canEditGridSpan = computed(() =>
  selectedNodePropertySchema.value.gridSpan
    && (
      form.value.layoutType === 'grid'
      || nodeTypeOf(nodeById(selectedField.value?.parentId)) === 'GRID'
    )
)
const canConfigureNodeExtension = computed(() =>
  selectedNodePropertySchema.value.nodeExtension
)
const selectedNodeConfig = computed(() =>
  safeParseConfig(selectedField.value?.componentProps)
)
const isSectionTitleNode = computed(() =>
  selectedNodeType.value === 'TEXT'
    && String(selectedNodeConfig.value.textStyle || '').toUpperCase() === 'SECTION_TITLE'
)
const selectedNodeTypeLabel = computed(() => {
  if (isSectionTitleNode.value) return '节'
  return nodeTypeOptions.find(option => option.value === selectedNodeType.value)?.label
    || selectedNodeType.value
})
const selectedNodeHasLockedBinding = computed(() => {
  if (!isEditableFieldNode.value) return false
  const bindingType = String(selectedField.value?.bindingType || '').toUpperCase()
  return Boolean(
    selectedField.value?.fieldId
    || selectedField.value?.relationCode
    || (bindingType && bindingType !== 'NONE')
  )
})
const selectedNodeLockMessage = computed(() => {
  if (isTabNode.value) {
    return 'TAB 页可在下方选择所属 Tab 集合；节点类型、同级排序和技术标识不可直接修改。'
  }
  if (selectedNodeHasLockedBinding.value) {
    return '已绑定业务数据：可调整合法父容器；节点类型、字段绑定和技术标识已锁定。'
  }
  return '可调整合法父容器；节点类型、同级排序和技术标识由画布结构控制。'
})
const availableNodeDataSourceUsages = computed(() => {
  const allowed = new Set(getFormNodeDataSourceUsages(selectedNodeType.value))
  return formDataSourceUsages.filter(usage => allowed.has(usage.value))
})

const availableParentNodes = computed(() =>
  formFields.value
    .filter(field => isValidParentCandidate(field, selectedField.value))
    .map(field => ({
      id: field.id,
      label: formatParentOptionLabel(field)
    }))
)
const availableTabSetNodes = computed(() =>
  formFields.value
    .filter(field =>
      String(field.nodeType || legacyNodeType(field)).toUpperCase() === 'TAB_SET'
    )
    .map(field => ({
      id: field.id,
      label: field.fieldLabel || field.fieldName || field.fieldCode
    }))
)
const canMoveSelectedNodeToRoot = computed(() =>
  canPlaceFormNodeAtRoot(selectedNodeType.value)
    && getSubtreeHeight(selectedField.value?.id) <= FORM_NODE_MAX_DEPTH
)
const selectedParentValue = computed(() =>
  selectedField.value?.parentId || ROOT_PARENT_VALUE
)
const selectedParentHelp = computed(() => {
  if (isTabNode.value) {
    return 'Tab 页只能位于 Tab 集合下；候选项已排除非法类型、循环引用和超过 8 层的目标。'
  }
  if (selectedNodeType.value === 'TAB_SET') {
    return 'Tab 集合可放在根节点或普通容器内，但其直接子节点只能是 Tab 页。'
  }
  return '可放在根节点或兼容容器内；不能直接放入 Tab 集合，候选项已排除自身、后代和超过 8 层的目标。'
})

const selectedValidationConfig = computed(() =>
  safeParseConfig(selectedField.value?.validationRules)
)
const selectedValidationCapabilities = computed(() =>
  getFormFieldValidationCapabilities(selectedField.value?.fieldType)
)
const hasSelectedValidationCapabilities = computed(() =>
  Object.values(selectedValidationCapabilities.value).some(Boolean)
)
const selectedValidationRuleCount = computed(() =>
  ['minLength', 'maxLength', 'min', 'max', 'format', 'pattern'].filter(key => {
    const value = selectedValidationConfig.value[key]
    return value !== undefined && value !== null && value !== ''
  }).length
)
const selectedPatternError = computed(() =>
  getRuntimeRegexPatternError(selectedValidationConfig.value.pattern)
)
const canConfigureSelectedNodeDataSource = computed(() =>
  selectedNodePropertySchema.value.dataSourceUsages.length > 0
)
const canConfigureSelectedNodeValidation = computed(() =>
  selectedNodePropertySchema.value.rules
    && hasSelectedValidationCapabilities.value
)
const canConfigureSelectedNodeModeAccess = computed(() =>
  selectedNodePropertySchema.value.editable.includes('modeAccess')
)
const isReferenceFieldNode = computed(() =>
  ['REFERENCE', 'MULTI_REFERENCE'].includes(
    String(selectedField.value?.componentType || '').toUpperCase()
  )
)
const isSingleEntityReferenceField = computed(() => {
  if (!isFieldNode.value || !selectedField.value) return false
  const fieldType = String(
    selectedField.value.fieldType || ''
  ).toUpperCase()
  const componentType = String(
    selectedField.value.componentType || ''
  ).toUpperCase()
  if (fieldType === 'MULTI_REFERENCE'
      || componentType === 'MULTI_REFERENCE') {
    return false
  }
  return [
    'REFERENCE',
    'USER',
    'DEPT',
    'ROLE',
    'GROUP'
  ].includes(fieldType)
    || componentType === 'REFERENCE'
})
const canConfigureSelectedNodeRelations = computed(() =>
  selectedNodePropertySchema.value.childForm
    || isSubListField(selectedField.value)
    || isReferenceFieldNode.value
)
const canConfigureSelectedNodeExtension = computed(() =>
  canConfigureNodeExtension.value
    || isEditableFieldNode.value
    || (isFieldNode.value && selectedComponentSchema.value.length > 0)
)
const availableNodeSettingsTabs = computed(() => {
  const tabs = [{ value: 'basic', label: '基础与布局' }]
  if (isFieldNode.value
      && (canConfigureSelectedNodeValidation.value
        || canConfigureSelectedNodeModeAccess.value)) {
    tabs.push({ value: 'rules', label: '状态与校验' })
  }
  if (isFieldNode.value
      || canConfigureSelectedNodeDataSource.value
      || canConfigureSelectedNodeRelations.value) {
    tabs.push({ value: 'data', label: '数据与关系' })
  }
  if (isFieldNode.value) {
    tabs.push({ value: 'interaction', label: '联动与事件' })
  }
  if (canConfigureSelectedNodeExtension.value) {
    tabs.push({ value: 'extension', label: '复用与扩展' })
  }
  return tabs
})
const selectedNodeDirty = computed(() => {
  const field = selectedField.value
  if (!field?.revision) return true
  return nodeBaselines.value.get(field.id) !== nodeFingerprint(field)
})
const selectedNodeBindingLabel = computed(() => {
  const field = selectedField.value
  if (!field) return '-'
  const { bindingType: type } = resolveFormNodeBinding(field)
  if (type === 'RELATION') {
    return field.relationName || field.relationCode || '实体关系'
  }
  if (type === 'ENTITY_FIELD' || field.fieldId) {
    return field.fieldName || field.fieldCode || '实体字段'
  }
  return '无业务绑定'
})
const selectedNodeParentLabel = computed(() => {
  const parentId = selectedField.value?.parentId
  if (!parentId) return '表单根节点'
  return nodeLabel(parentId)
})
const selectedNodeDataSourceBindingCount = computed(() => {
  const field = selectedField.value
  if (!field) return 0
  const bindings = { ...parseDocument(field.dataSourceBindings) }
  const usage = String(field.dataSourceUsage || '').toUpperCase()
  if (usage) {
    if (field.dataSourceId) {
      bindings[usage] = {
        ...(typeof bindings[usage] === 'object' ? bindings[usage] : {}),
        sourceId: field.dataSourceId
      }
    } else {
      delete bindings[usage]
    }
  }
  return Object.values(bindings).filter(Boolean).length
})
const selectedNodeDataSourceUsageLabel = computed(() =>
  availableNodeDataSourceUsages.value.find(
    item => item.value === selectedField.value?.dataSourceUsage
  )?.label || '当前用途'
)

watch(
  [() => selectedField.value?.id, availableNodeSettingsTabs],
  () => {
    if (!availableNodeSettingsTabs.value.some(
      tab => tab.value === activeNodeSettingsTab.value
    )) {
      activeNodeSettingsTab.value = 'basic'
    }
    if (!isSingleEntityReferenceField.value
        && activeNodeInteractionTab.value === 'selection') {
      activeNodeInteractionTab.value = 'state'
    }
  },
  { immediate: true }
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

provide(FORM_DESIGNER_CONTEXT_KEY, {
  form, viewConfig, isEdit, isSystemEntity,
  customFormButtonCount, entityInfo, entityFields, formFields,
  formDataSourceBindingCount, eventFieldOptions,
  selectedCustomFormSchema, customFormOptions,
  selectedCustomFormCatalogOption, showFormExtensionConfig,
  openFormDataSourceConfig, handleFormRendererModeChange,
  handleCustomFormCatalogLoaded,
  openExtensionManagement, refreshExtensionCatalog,
  selectedField, activeNodeSettingsTab, isFieldNode,
  canConfigureSelectedNodeDataSource,
  selectedNodeDataSourceBindingCount, availableNodeDataSourceUsages,
  isNodeDataSourceUsageConfigured, selectNodeDataSourceUsage,
  selectedNodeDataSourceUsageLabel, dataSources,
  clearSelectedNodeDataSourceBinding, canConfigureSelectedNodeRelations,
  isSubFormField, isSubListField, getEntityNameById, formListByEntity,
  handleChildFormChange, childFormReleases, childFormReleaseLoading,
  handleChildFormReleaseChange, formatChildFormReleaseLabel,
  subListOptions, subListTargetFields, subListTargetFieldsLoading,
  handleSubListChange,
  isReferenceFieldNode, handleReferenceEntitySelected,
  rememberEntityOption, getEntityReferenceSelectionHint,
  referenceListOptions,
  openLinkageConfig
})

// 预览数据
const previewForm = computed(() => {
  const sortedFields = [...formFields.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  const previewNodes = sortedFields.map((field, index) =>
    fieldToNodeEntity({
      ...field,
      dataSourceBindings: { ...(field.dataSourceBindings || {}) },
      legacyProps: { ...(field.legacyProps || {}) },
      localOverrides: { ...(field.localOverrides || {}) }
    }, index)
  )
  return {
    ...form.value,
    viewConfig: viewConfig.value,
    fields: sortedFields,
    nodes: previewNodes
  }
})
const previewModeOptions = computed(() =>
  (isSystemEntity.value
    ? FORM_ACTION_MODES.filter(mode => mode.value === 'view')
    : FORM_ACTION_MODES
  ).map(mode => ({ label: mode.label, value: mode.value }))
)
const previewActions = computed(() =>
  resolveLocalFormActions(previewForm.value, {
    mode: previewMode.value,
    workflowReady: isWorkflowReady(entityInfo.value),
    hasProcessInstance: false,
    canApprove: true,
    systemEntity: isSystemEntity.value
  })
)
const previewFooterActions = computed(() =>
  footerFormActions(previewActions.value)
)

function handlePreviewAction(action) {
  ElMessage.info(`预览模式不执行“${action?.label || '按钮'}”`)
}

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
  const configurableFields = entityFields.value.filter(
    field => field.uiConfigurable !== false
  )
  if (!fieldSearch.value) return configurableFields
  return configurableFields.filter(f =>
    f.fieldName.includes(fieldSearch.value) || 
    f.fieldCode.includes(fieldSearch.value)
  )
})

// 检查字段是否已在表单中
function isFieldInForm(entityField) {
  return formFields.value.some(f => f.fieldId === entityField.id)
}

function getNodeSpan(field, fallback = 24) {
  return Number(field?.gridSpan || fallback || 24)
}

function getNodeDesignStyle(field) {
  const nodeType = String(field?.nodeType || legacyNodeType(field)).toUpperCase()
  if (['SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE', 'TEXT', 'ACTION_SLOT'].includes(nodeType)) {
    return { width: '100%' }
  }
  const span = form.value.layoutType === 'vertical'
    ? 24
    : form.value.layoutType === 'horizontal'
      ? 12
      : getNodeSpan(field)
  const width = `${(span / 24) * 100}%`
  return {
    width,
    flex: `0 0 ${width}`
  }
}

// 加载实体信息
async function loadEntityInfo() {
  if (!entityId) return
  try {
    const data = await entityApi.getById(entityId)
    entityInfo.value = data
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
  return entityNameById.value[String(id)] || String(id)
}

function rememberEntityOption(entity) {
  if (!entity || Array.isArray(entity) || !entity.id) return
  entityNameById.value = {
    ...entityNameById.value,
    [String(entity.id)]: entity.entityName || entity.entityCode || String(entity.id)
  }
  entityCodeById.value = {
    ...entityCodeById.value,
    [String(entity.id)]: entity.entityCode || ''
  }
}

async function resolveReferencedEntityNames() {
  const ids = [...new Set(
    formFields.value
      .flatMap(field => [field.refEntityId, field.childEntityId])
      .map(value => String(value || ''))
      .filter(Boolean)
  )]
  if (!ids.length) return
  const options = await entityApi.resolveOptions({ ids }).catch(() => [])
  entityNameById.value = {
    ...entityNameById.value,
    ...Object.fromEntries(
      (options || []).map(item => [
        String(item.id),
        item.entityName || item.entityCode || String(item.id)
      ])
    )
  }
  entityCodeById.value = {
    ...entityCodeById.value,
    ...Object.fromEntries(
      (options || []).map(item => [
        String(item.id),
        item.entityCode || ''
      ])
    )
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
  return ['SUB_FORM', 'REPEATER'].includes(nodeType)
    || componentType === 'SUB_FORM'
}

function isSubListField(field) {
  const fieldType = String(field?.fieldType || '').toUpperCase()
  const componentType = String(field?.componentType || '').toLowerCase()
  return fieldType === 'SUB_LIST'
    || componentType === 'sub_list'
}

async function loadSubListOptions(targetEntityId, targetField = selectedField.value) {
  if (!targetEntityId) {
    subListOptions.value = []
    if (targetField === selectedField.value) {
      subListTargetFields.value = []
    }
    return []
  }
  try {
    const options = await entityApi.resolveOptions({
      ids: [String(targetEntityId)]
    }).catch(() => [])
    const entity = options?.[0]
    rememberEntityOption(entity)
    if (targetField) {
      targetField.refEntityCode =
        entity?.entityCode
        || entityCodeById.value[String(targetEntityId)]
        || targetField.refEntityCode
        || ''
    }
    const response = await entityListConfigApi.getByEntityId(targetEntityId)
    const lists = Array.isArray(response)
      ? response
      : response?.records || response?.list || response?.data || []
    const published = lists.filter(isPublishedSubListOption)
    if (targetField === selectedField.value) {
      subListOptions.value = published
    }
    return published
  } catch (error) {
    if (targetField === selectedField.value) {
      subListOptions.value = []
    }
    console.error('加载子列表配置失败:', error)
    return []
  }
}

async function loadSubListTargetFields(
  targetEntityId,
  listRef,
  targetField = selectedField.value
) {
  const shouldUpdateUi =
    Boolean(targetField) && targetField === selectedField.value
  const sequence = shouldUpdateUi
    ? ++subListTargetFieldLoadSequence
    : subListTargetFieldLoadSequence
  if (shouldUpdateUi) {
    subListTargetFieldsLoading.value = true
  }
  const list = listRef && typeof listRef === 'object'
    ? listRef
    : { id: listRef }
  if (!targetEntityId || (!list.id && !list.listKey)) {
    if (shouldUpdateUi) {
      subListTargetFields.value = []
      subListTargetFieldsLoading.value = false
    }
    return []
  }
  try {
    const targetEntityCode =
      list.entityCode
      || targetField?.refEntityCode
      || entityCodeById.value[String(targetEntityId)]
      || ''
    const listConfigRequest =
      targetEntityCode && list.listKey
        ? entityListRuntimeApi.getSchema(
            targetEntityCode,
            list.listKey,
            'EMBEDDED'
          )
        : entityListConfigApi.getById(list.id)
    const [targetFieldsResponse, listConfig] = await Promise.all([
      getEntityFields(targetEntityId),
      listConfigRequest
    ])
    const targetFields = Array.isArray(targetFieldsResponse)
      ? targetFieldsResponse
      : targetFieldsResponse?.records
        || targetFieldsResponse?.list
        || targetFieldsResponse?.data
        || []
    const queryable = new Set(
      (listConfig?.fields || [])
        .filter(item => item.isQuery === true || item.isQuery === 1)
        .map(item => String(item.fieldCode || '').trim())
        .filter(Boolean)
    )
    const targetByCode = new Map(
      targetFields
        .filter(item => item.fieldCode)
        .map(item => [String(item.fieldCode), item])
    )
    ;(listConfig?.fields || []).forEach(item => {
      const code = String(item.fieldCode || '').trim()
      if (code && !targetByCode.has(code)) {
        targetByCode.set(code, item)
      }
    })
    const options = [...targetByCode.values()].map(item => ({
      ...item,
      fieldCode: String(item.fieldCode || '').trim(),
      fieldName: item.fieldName || item.fieldLabel || item.fieldCode,
      queryable: queryable.has(String(item.fieldCode || '').trim()),
      writable: isSubListTargetFieldWritable(item)
    }))
    if (shouldUpdateUi
        && sequence === subListTargetFieldLoadSequence) {
      subListTargetFields.value = options
    }
    return options
  } catch (error) {
    if (shouldUpdateUi
        && sequence === subListTargetFieldLoadSequence) {
      subListTargetFields.value = []
    }
    console.error('加载子列表目标字段失败:', error)
    return []
  } finally {
    if (shouldUpdateUi
        && sequence === subListTargetFieldLoadSequence) {
      subListTargetFieldsLoading.value = false
    }
  }
}

async function handleSubListChange(listKey) {
  const field = selectedField.value
  if (!field) return
  const selected = subListOptions.value.find(item =>
    item.listKey === listKey
  )
  field.refListKey = selected?.listKey || ''
  field.refListId = selected?.id || ''
  field.refListReleaseId = selected?.activeReleaseId || ''
  field.refListReleaseVersion = selected?.publishedVersion == null
    ? null
    : Number(selected.publishedVersion)
  await loadSubListTargetFields(
    field.refEntityId,
    selected,
    field
  )
}

async function ensureSubListBinding(field) {
  if (!isSubListField(field)) return
  const targetEntityId = field.refEntityId
  if (!targetEntityId) {
    throw new Error('子列表必须选择目标实体')
  }
  if (!field.refListKey) {
    throw new Error('子列表必须选择一个已发布列表')
  }
  const lists = await loadSubListOptions(targetEntityId, field)
  const selected = lists.find(item =>
    item.listKey === field.refListKey
  )
  if (!selected) {
    throw new Error('子列表引用的列表不存在、尚未发布或已失效')
  }
  field.refEntityCode =
    selected.entityCode
    || entityCodeById.value[String(targetEntityId)]
    || field.refEntityCode
    || ''
  if (!field.refEntityCode) {
    throw new Error('子列表目标实体编码解析失败')
  }
  field.refListId = selected.id || ''
  field.refListReleaseId = selected.activeReleaseId || ''
  field.refListReleaseVersion = selected.publishedVersion == null
    ? null
    : Number(selected.publishedVersion)

  const componentProps = safeParseConfig(field.componentProps)
  const contract = normalizeSubListParameterContract(
    componentProps.subListConfig?.parameterContract
  )
  if (contract.mappings.length === 0) return

  const targets = await loadSubListTargetFields(
    targetEntityId,
    selected,
    null
  )
  const targetByCode = new Map(
    targets.map(item => [item.fieldCode, item])
  )
  const seenTargets = new Set()
  for (const mapping of contract.mappings) {
    if (seenTargets.has(mapping.targetField)) {
      throw new Error(
        `子列表参数“${mapping.targetFieldName || mapping.targetField}”重复配置`
      )
    }
    seenTargets.add(mapping.targetField)
    const target = targetByCode.get(mapping.targetField)
    if (!target) {
      throw new Error(
        `子列表参数目标字段不存在: ${mapping.targetField}`
      )
    }
    if (!target.queryable && !target.writable) {
      throw new Error(
        `目标字段“${target.fieldName}”未启用查询且不可新增，不能配置子列表参数`
      )
    }
    if (isParentEntityReferenceTarget(
      target,
      form.value.entityId || entityId
    ) && (
      mapping.source !== 'parent.recordId'
      || mapping.operator !== 'EQ'
      || mapping.required !== true
      || mapping.useForQuery !== true
    )) {
      throw new Error(
        `目标字段“${target.fieldName}”指向当前主实体，必须使用父记录ID并以“等于”方式参与查询`
      )
    }
    if (!mapping.useForQuery && !mapping.useForCreate) {
      throw new Error(
        `子列表参数“${mapping.targetFieldName || mapping.targetField}”至少选择查询或新增一种用途`
      )
    }
    if (mapping.useForQuery && !target.queryable) {
      throw new Error(
        `目标列表字段“${target.fieldName}”未启用查询，不能作为子列表参数过滤条件`
      )
    }
    if (mapping.useForCreate && !target.writable) {
      throw new Error(
        `目标实体字段“${target.fieldName}”不可写，不能作为新增初始值`
      )
    }
    const source = mapping.source
    if (typeof source === 'string' && !source.trim()) {
      throw new Error(
        `子列表参数“${mapping.targetFieldName || mapping.targetField}”未选择来源`
      )
    }
    if (typeof source === 'string' && source.startsWith('parent.data.')) {
      const parentFieldCode = source.slice('parent.data.'.length)
      if (!entityFields.value.some(item =>
        item.fieldCode === parentFieldCode)) {
        throw new Error(
          `子列表参数引用的父字段不存在: ${parentFieldCode}`
        )
      }
    }
  }
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
    const detailedFields = Array.isArray(entityInfo.value?.fields)
      ? entityInfo.value.fields
      : []
    const loadedFields = detailedFields.length > 0
      ? detailedFields
      : await getEntityFields(eid)
    entityFields.value = filterEntityFieldsByLifecycle(
      entityInfo.value,
      loadedFields
    ).filter(field => field.uiConfigurable !== false)
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
    const parsedViewConfig = safeParseConfig(data.viewConfig)
    viewConfig.value = {
      labelWidth: 120,
      customComponentProps: {},
      ...parsedViewConfig,
      actionBar: normalizeFormActionBar(parsedViewConfig.actionBar)
    }
    if (data.entityId && !entityId) {
      form.value.entityId = data.entityId
    }
    rememberFormBaseline()
    await loadDiff()
  } catch (e) {
    console.error('加载表单信息失败:', e)
  }
}

function parseDocument(value) {
  return safeParseConfig(value)
}

function isNodeDataSourceUsageConfigured(usage) {
  const field = selectedField.value
  if (!field) return false
  if (field.dataSourceUsage === usage) {
    return Boolean(field.dataSourceId)
  }
  const binding = parseDocument(field.dataSourceBindings)[usage]
  return Boolean(
    typeof binding === 'string'
      ? binding
      : binding?.sourceId || binding?.id
  )
}

function syncNodeDataSourceBinding(field, { throwOnError = false } = {}) {
  if (!field) return true
  const usage = String(field.dataSourceUsage || '').trim().toUpperCase()
  if (!usage) return true
  try {
    const bindings = { ...parseDocument(field.dataSourceBindings) }
    if (!field.dataSourceId) {
      delete bindings[usage]
    } else {
      const existing = bindings[usage]
      bindings[usage] = {
        ...(existing
          && typeof existing === 'object'
          && !Array.isArray(existing)
          ? existing
          : {}),
        sourceId: field.dataSourceId,
        inputMapping: parseJsonConfig(field.dataSourceInputMappingText, {
          fieldName: `${field.fieldLabel || field.fieldCode || '当前节点'}数据源输入映射`
        }),
        outputMapping: parseJsonConfig(field.dataSourceOutputMappingText, {
          fieldName: `${field.fieldLabel || field.fieldCode || '当前节点'}数据源输出映射`
        })
      }
    }
    field.dataSourceBindings = bindings
    return true
  } catch (error) {
    if (throwOnError) throw error
    ElMessage.error(error.message || '数据源映射格式不正确')
    return false
  }
}

function loadNodeDataSourceUsage(field, usage) {
  if (!field) return
  const binding = parseDocument(field.dataSourceBindings)[usage]
  const normalized = typeof binding === 'string'
    ? { sourceId: binding }
    : (binding || {})
  field.dataSourceUsage = usage
  field.dataSourceId = normalized.sourceId || normalized.id || ''
  field.dataSourceInputMappingText = stringifyConfig(
    normalized.inputMapping || {}
  )
  field.dataSourceOutputMappingText = stringifyConfig(
    normalized.outputMapping || {}
  )
}

function selectNodeDataSourceUsage(usage) {
  const field = selectedField.value
  if (!field || field.dataSourceUsage === usage) return
  if (!syncNodeDataSourceBinding(field)) return
  loadNodeDataSourceUsage(field, usage)
}

function clearSelectedNodeDataSourceBinding() {
  const field = selectedField.value
  if (!field?.dataSourceUsage) return
  const bindings = { ...parseDocument(field.dataSourceBindings) }
  delete bindings[field.dataSourceUsage]
  field.dataSourceBindings = bindings
  field.dataSourceId = ''
  field.dataSourceInputMappingText = '{}'
  field.dataSourceOutputMappingText = '{}'
}

function openFormDataSourceConfig() {
  formDataSourceDialogRef.value?.open()
}

async function handleFormDataSourceSaved(updated) {
  form.value = { ...form.value, ...updated }
  await loadDiff()
}

function legacyNodeType(field) {
  const fieldType = String(field?.fieldType || '').toUpperCase()
  const componentType = String(field?.componentType || '').toUpperCase()
  if (fieldType === 'SECTION' || componentType === 'SECTION') return 'SECTION'
  if (fieldType === 'SUB_FORM' || componentType === 'SUB_FORM') return 'SUB_FORM'
  return 'FIELD'
}

function nodeTypeOf(field) {
  return normalizeFormNodeType(field?.nodeType || legacyNodeType(field))
}

function nodeLabel(nodeId) {
  const node = formFields.value.find(item => item.id === nodeId)
  return node?.fieldLabel || node?.fieldName || node?.fieldCode || nodeId
}

function nodeById(nodeId) {
  return formFields.value.find(item => String(item.id) === String(nodeId))
}

function getNodeDepth(nodeId) {
  return getSharedFormNodeDepth(formFields.value, nodeId)
}

function getSubtreeHeight(nodeId) {
  return getFormNodeSubtreeHeight(formFields.value, nodeId)
}

function isValidParentCandidate(parent, child) {
  if (!parent || !child) return false
  return validateFormNodeDrop(
    formFields.value,
    child,
    parent.id
  ).valid
}

function nodePathLabels(node) {
  const labels = []
  const visited = new Set()
  let current = node
  while (current) {
    const currentId = String(current.id)
    if (visited.has(currentId)) break
    visited.add(currentId)
    labels.unshift(
      current.fieldLabel || current.fieldName || current.fieldCode || currentId
    )
    current = current.parentId ? nodeById(current.parentId) : null
  }
  return labels
}

function formatParentOptionLabel(parent) {
  const path = nodePathLabels(parent)
  const parentLabel = path.pop()
  const location = path.length ? `（${path.join(' / ')} 下）` : ''
  return `${formNodeTypeLabel(nodeTypeOf(parent))} · ${parentLabel}${location}`
}

function nextNodePlacement(parentId, excludeId = '') {
  const siblings = designChildrenFor(parentId)
    .filter(item => String(item.id) !== String(excludeId))
  const maxOrderKey = siblings.reduce(
    (maximum, item) => Math.max(maximum, Number(item.orderKey || 0)),
    0
  )
  return {
    orderKey: maxOrderKey + FORM_NODE_ORDER_STEP,
    sortOrder: siblings.length
  }
}

function resolveDefaultParentId(nodeType) {
  const normalizedType = normalizeFormNodeType(nodeType)
  const selected = selectedField.value
  if (selected) {
    if (canContainFormNode(nodeTypeOf(selected), normalizedType)
        && getNodeDepth(selected.id) + 1 <= FORM_NODE_MAX_DEPTH) {
      return selected.id
    }
    const selectedParent = selected.parentId
      ? nodeById(selected.parentId)
      : null
    if (selectedParent
        && canContainFormNode(nodeTypeOf(selectedParent), normalizedType)
        && getNodeDepth(selectedParent.id) + 1 <= FORM_NODE_MAX_DEPTH) {
      return selectedParent.id
    }
    if (normalizedType === 'TAB') {
      let ancestor = selectedParent
      while (ancestor) {
        if (nodeTypeOf(ancestor) === 'TAB_SET') return ancestor.id
        ancestor = ancestor.parentId ? nodeById(ancestor.parentId) : null
      }
    }
  }
  if (normalizedType === 'TAB' && availableTabSetNodes.value.length === 1) {
    return availableTabSetNodes.value[0].id
  }
  return ''
}

function handleParentChange(value) {
  if (!selectedField.value) return
  const parentId = value === ROOT_PARENT_VALUE ? '' : value
  if (!parentId && !canPlaceFormNodeAtRoot(selectedNodeType.value)) {
    ElMessage.warning('Tab 页必须选择所属 Tab 集合')
    return
  }
  if (parentId) {
    const parent = nodeById(parentId)
    if (!isValidParentCandidate(parent, selectedField.value)) {
      ElMessage.warning('该父容器与当前节点不兼容，或移动后会形成循环/超过 8 层')
      return
    }
  }
  const placement = nextNodePlacement(parentId, selectedField.value.id)
  selectedField.value.parentId = parentId
  selectedField.value.orderKey = placement.orderKey
  selectedField.value.sortOrder = placement.sortOrder
  const targetLabel = parentId
    ? formatParentOptionLabel(nodeById(parentId))
    : '表单根节点'
  ElMessage.success(`已移动到${targetLabel}，保存草稿后写入服务器`)
}


function nodeToField(node, legacyField) {
  const props = parseDocument(node.propsDocument)
  const rules = parseDocument(node.rulesDocument)
  const bindings = parseDocument(node.dataSourceBindingsDocument)
  const nodeType = normalizeFormNodeType(node.nodeType)
  const sourceField = mergeFormNodeFieldMetadata(
    entityFields.value,
    legacyField,
    props,
    node.nodeKey
  )
  const allowedDataSourceUsages = getFormNodeDataSourceUsages(nodeType)
  const firstBinding = Object.entries(bindings)
    .find(([usage]) =>
      allowedDataSourceUsages.includes(String(usage).toUpperCase())
    ) || []
  const componentConfig = extractFormNodeComponentConfig(nodeType, props)
  const rulesSupported = formNodeSupports(nodeType, 'rules')
  const isChildFormNode = ['SUB_FORM', 'REPEATER'].includes(nodeType)
  const field = {
    ...sourceField,
    id: node.id,
    nodeId: node.id,
    formId: node.formId,
    parentId: node.parentId || '',
    nodeType,
    nodeKey: node.nodeKey,
    bindingType: node.bindingType || 'NONE',
    bindingRef: node.bindingRef || '',
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
    fieldId: props.fieldId ?? sourceField.fieldId ?? sourceField.id,
    fieldCode: props.fieldCode || node.nodeKey,
    fieldName: props.fieldName || props.label || sourceField.fieldName || node.nodeKey,
    fieldLabel: props.label || sourceField.fieldLabel || sourceField.fieldName || node.nodeKey,
    fieldType: isChildFormNode
      ? 'SUB_FORM'
      : (props.fieldType || sourceField.fieldType || node.nodeType),
    componentType: isChildFormNode
      ? 'sub_form'
      : (props.componentType || sourceField.componentType || node.nodeType.toLowerCase()),
    placeholder: props.placeholder ?? sourceField.placeholder,
    defaultValue: props.defaultValue ?? sourceField.defaultValue,
    gridSpan: props.gridSpan ?? sourceField.gridSpan ?? 24,
    childFormId:
      props.childFormId
      || props.refFormId
      || props.publishedFormId
      || sourceField.childFormId
      || sourceField.refFormId
      || '',
    childFormReleaseId:
      props.childFormReleaseId
      || props.refFormReleaseId
      || props.publishedFormReleaseId
      || sourceField.childFormReleaseId
      || '',
    childFormReleaseVersion:
      props.childFormReleaseVersion
      ?? props.refFormReleaseVersion
      ?? props.publishedFormReleaseVersion
      ?? sourceField.childFormReleaseVersion
      ?? null,
    isRequired: Object.hasOwn(props, 'required')
      ? (props.required === true ? 1 : 0)
      : (sourceField.isRequired || 0),
    isReadonly: Object.hasOwn(props, 'readonly')
      ? (props.readonly === true ? 1 : 0)
      : (sourceField.isReadonly || 0),
    isHidden: Object.hasOwn(props, 'hidden')
      ? (props.hidden === true ? 1 : 0)
      : (sourceField.isHidden || 0),
    componentProps: stringifyConfig(componentConfig),
    validationRules: rulesSupported
      ? stringifyConfig(rules.validation || rules)
      : '',
    extensionConfig: rulesSupported
      ? stringifyConfig(rules.extension || {})
      : '',
    dataSourceUsage: firstBinding[0] || allowedDataSourceUsages[0] || '',
    dataSourceId: firstBinding[1]?.sourceId || firstBinding[1] || '',
    dataSourceInputMappingText: stringifyConfig(
      firstBinding[1]?.inputMapping || {}
    ),
    dataSourceOutputMappingText: stringifyConfig(
      firstBinding[1]?.outputMapping || {}
    )
  }
  const normalizedBinding = resolveFormNodeBinding(field, nodeType)
  field.bindingType = normalizedBinding.bindingType
  field.bindingRef = normalizedBinding.bindingRef || ''
  restoreFieldConfig(field)
  return field
}

function fieldToNodePayload(field, options = {}) {
  return buildFormNodePayload(
    {
      ...field,
      nodeType: field.nodeType || legacyNodeType(field)
    },
    {
      componentProps: buildSerializedFieldComponentProps(field),
      forPatch: options.forPatch === true
    }
  )
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

function formFingerprint() {
  return JSON.stringify({
    formName: form.value.formName || '',
    description: form.value.description || '',
    layoutType: form.value.layoutType || 'vertical',
    isDefault: Boolean(form.value.isDefault),
    status: form.value.status,
    customComponent: form.value.customComponent || '',
    customComponentVersion: form.value.customComponentVersion || null,
    customComponentSnapshotVersion:
      form.value.customComponentSnapshotVersion || null,
    initConfig: safeParseConfig(form.value.initConfig),
    viewConfig: viewConfig.value
  })
}

function rememberFormBaseline() {
  formBaseline.value = formFingerprint()
}

function hasUnsavedLocalChanges() {
  if (formBaseline.value && formBaseline.value !== formFingerprint()) {
    return true
  }
  return formFields.value.some(field =>
    !field.revision
      || nodeBaselines.value.get(field.id) !== nodeFingerprint(field)
  )
}

useUnsavedChangesGuard(() => hasUnsavedLocalChanges(), {
  message: '表单画布或属性有未保存修改，离开后这些修改将丢失。'
})

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
    if (compProps.subListConfig) {
      const subListConfig = normalizeSubListDisplayConfig(
        compProps.subListConfig
      )
      field.refEntityId =
        subListConfig.targetEntityId
        || field.refEntityId
        || ''
      field.refEntityCode =
        subListConfig.targetEntityCode
        || field.refEntityCode
        || ''
      field.refListKey =
        subListConfig.listKey
        || field.refListKey
        || ''
      field.refListId = subListConfig.listId || ''
      field.refListReleaseId = subListConfig.listReleaseId || ''
      field.refListReleaseVersion =
        subListConfig.listReleaseVersion ?? null
      field.subListShowSearch = subListConfig.showSearch
      field.subListShowPagination = subListConfig.showPagination
      field.subListShowToolbar = subListConfig.showToolbar
      field.subListShowRowActions = subListConfig.showRowActions
      field.subListPageSize = subListConfig.pageSize
      field.subListMaxHeight =
        Number(subListConfig.maxHeight) >= 120
          ? Number(subListConfig.maxHeight)
          : 420
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

// 将子表单和事件配置纯函数序列化到 componentProps
function buildSerializedFieldComponentProps(field) {
  try {
    const compProps = field.componentProps
      ? (typeof field.componentProps === 'string'
        ? JSON.parse(field.componentProps)
        : JSON.parse(JSON.stringify(field.componentProps)))
      : {}

    // 序列化子表单配置
    if (isSubFormField(field)) {
      const childFormId = field.childFormId || field.refFormId || ''
      const childFormReleaseId = field.childFormReleaseId || ''
      const childFormReleaseVersion = field.childFormReleaseVersion == null
        ? null
        : Number(field.childFormReleaseVersion)
      const subFormConfig = {
        ...(compProps.subFormConfig || {})
      }
      compProps.subFormConfig = {
        ...subFormConfig,
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
    if (isSubListField(field)) {
      compProps.subListConfig = {
        ...(compProps.subListConfig || {}),
        targetEntityId: field.refEntityId || '',
        targetEntityCode: field.refEntityCode || '',
        listId: field.refListId || '',
        listKey: field.refListKey || '',
        listReleaseId: field.refListReleaseId || '',
        listReleaseVersion: field.refListReleaseVersion == null
          ? null
          : Number(field.refListReleaseVersion),
        actionDisplayVersion: SUB_LIST_ACTION_DISPLAY_VERSION,
        showSearch: field.subListShowSearch !== false,
        showPagination: field.subListShowPagination !== false,
        showToolbar: field.subListShowToolbar !== false,
        showRowActions: field.subListShowRowActions !== false,
        pageSize: Number(field.subListPageSize) || 10,
        maxHeight: Number(field.subListMaxHeight) || 420
      }
      delete compProps.subFormConfig
    }
    // 序列化实体引用配置
    if ((field.componentType || '').toUpperCase() === 'REFERENCE' || (field.componentType || '').toUpperCase() === 'MULTI_REFERENCE') {
      compProps.refConfig = {
        refEntityType: field.refEntityType || '',
        refEntityId: field.refEntityId || '',
        entityCode: field.refEntityCode || '',
        listKey: field.refListKey || '',
        apiUrl: ''
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

    return compProps
  } catch (e) {
    console.error('序列化字段配置失败:', e)
    return parseDocument(field.componentProps)
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
    await resolveReferencedEntityNames()
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
  const nodeType = entityField.fieldType === 'SUB_FORM'
    ? 'SUB_FORM'
    : 'FIELD'
  const initialBinding = resolveFormNodeBinding(entityField, nodeType)
  const parentId = resolveDefaultParentId(nodeType)
  const placement = nextNodePlacement(parentId)
  const newField = {
    id: stableId,
    nodeId: stableId,
    nodeKey: entityField.fieldCode,
    nodeType,
    parentId,
    revision: 0,
    orderKey: placement.orderKey,
    formId: formId,
    fieldId: entityField.id,
    bindingType: initialBinding.bindingType,
    bindingRef: initialBinding.bindingRef || '',
    relationCode: entityField.relationCode || '',
    fieldCode: entityField.fieldCode,
    fieldName: entityField.fieldName,
    fieldLabel: entityField.fieldName,
    fieldType: entityField.fieldType,
    componentType: getDefaultComponentType(entityField.fieldType),
    isRequired: entityField.isRequired ? 1 : 0,
    isReadonly: isSystemEntity.value ? 1 : 0,
    isHidden: 0,
    validationRules: '',
    extensionConfig: '',
    gridSpan: 24,
    sortOrder: placement.sortOrder
  }

  // 复制实体引用配置（统一将 refEntityId 转为字符串，避免 el-select 类型不匹配）
  if (entityField.refEntityId) {
    newField.refEntityId = String(entityField.refEntityId)
  }
  if (entityField.refEntityType) {
    newField.refEntityType = entityField.refEntityType
  }
  if (entityField.refListKey) {
    newField.refListKey = entityField.refListKey
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
    newField.repeatable = newField.relationType !== 'ONE_TO_ONE'
    if (newField.refEntityId) {
      loadFormListByEntity(newField.refEntityId)
    }
  } else if (isSubListField(newField)) {
    newField.componentType = 'sub_list'
    newField.refListKey = entityField.refListKey || ''
    newField.subListShowSearch = true
    newField.subListShowPagination = true
    newField.subListShowToolbar = true
    newField.subListShowRowActions = true
    newField.subListPageSize = 10
    newField.subListMaxHeight = 420
    if (newField.refEntityId) {
      loadSubListOptions(newField.refEntityId, newField)
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
  openFieldProperties(newField)
  if (['REFERENCE', 'MULTI_REFERENCE'].includes((newField.componentType || '').toUpperCase())) {
    loadReferenceLists(newField.refEntityId, false)
  }
  ElMessage.success('字段已添加')
}

// 判断是否为节字段
function isSectionField(field) {
  return (field?.fieldType || '').toUpperCase() === 'SECTION' ||
    (field?.componentType || '').toLowerCase() === 'section'
}

// 添加节
function addSection() {
  addContainerNode('TEXT', {
    label: '新节',
    componentProps: {
      text: '新节',
      textStyle: 'SECTION_TITLE'
    }
  })
}

function handleAddNodeCommand(command) {
  if (command === 'SECTION_TITLE') {
    addSection()
    return
  }
  addContainerNode(command)
}

function addContainerNode(nodeType, options = {}) {
  const tabSetNodes = availableTabSetNodes.value
  if (nodeType === 'TAB' && tabSetNodes.length === 0) {
    ElMessage.warning('请先创建 Tab 集合，再添加 Tab 页')
    return
  }
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
  const nodeLabel = options.label || typeLabels[nodeType] || '新节点'
  const componentProps = nodeType === 'TEXT'
    ? {
        text: nodeLabel,
        ...(options.componentProps || {})
      }
    : (options.componentProps || {})
  const stableId = `node_${nodeType.toLowerCase()}_${ts}`
  const parentId = resolveDefaultParentId(nodeType)
  const placement = nextNodePlacement(parentId)
  const node = {
    id: stableId,
    nodeId: stableId,
    nodeKey: stableId,
    nodeType,
    parentId,
    revision: 0,
    orderKey: placement.orderKey,
    formId: formId,
    fieldId: null,
    fieldCode: stableId,
    fieldName: nodeLabel,
    fieldLabel: nodeLabel,
    fieldType: nodeType === 'REPEATER' ? 'SUB_FORM' : nodeType,
    componentType: nodeType === 'REPEATER'
      ? 'sub_form'
      : nodeType.toLowerCase(),
    bindingType: 'NONE',
    bindingRef: null,
    isRequired: 0,
    isReadonly: 1,
    isHidden: 0,
    componentProps: stringifyConfig(componentProps),
    validationRules: '',
    extensionConfig: '',
    gridSpan: 24,
    sortOrder: placement.sortOrder
  }
  formFields.value.push(node)
  openFieldProperties(node)
  if (nodeType === 'TAB' && !node.parentId) {
    ElMessage.info('请选择“所属 Tab 集合”后再保存当前 Tab 页')
  } else {
    ElMessage.success(`${nodeLabel}已添加`)
  }
}

// 选择字段
function selectField(field) {
  selectedField.value = field
  if (propertyDrawerVisible.value) {
    prepareFieldProperties(field)
  }
}

// 双击已有节点或新增节点后打开属性
function openFieldProperties(field) {
  selectedField.value = field
  propertyDrawerVisible.value = true
  prepareFieldProperties(field)
}

function prepareFieldProperties(field) {
  childFormReleases.value = []
  subListOptions.value = []
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
  } else if (field && isSubListField(field)) {
    field.componentType = 'sub_list'
    field.refListKey = field.refListKey || ''
    field.subListShowSearch = field.subListShowSearch !== false
    field.subListShowPagination =
      field.subListShowPagination !== false
    field.subListShowToolbar =
      field.subListShowToolbar !== false
    field.subListShowRowActions =
      field.subListShowRowActions !== false
    field.subListPageSize = Number(field.subListPageSize) || 10
    field.subListMaxHeight = Number(field.subListMaxHeight) || 420
    if (field.refEntityId) {
      loadSubListOptions(field.refEntityId, field).then(lists => {
        const selected = lists.find(item =>
          item.listKey === field.refListKey
        )
        if (selected) {
          loadSubListTargetFields(
            field.refEntityId,
            selected,
            field
          )
        } else if (field === selectedField.value) {
          subListTargetFields.value = []
        }
      })
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
  const children = designChildrenFor(field.id)
  if (children.length) {
    ElMessage.warning(`当前节点包含 ${children.length} 个直接子节点，请先移动或删除子节点`)
    return
  }
  const index = formFields.value.findIndex(item => item.id === field.id)
  if (index >= 0) await removeField(index)
}

async function moveNode({ node, direction }) {
  const siblings = designChildrenFor(node.parentId || '')
  const siblingIndex = siblings.findIndex(item => item.id === node.id)
  const targetIndex = siblingIndex + direction
  if (siblingIndex < 0 || targetIndex < 0 || targetIndex >= siblings.length) return
  await handleNodeDrop({
    node,
    newParentId: node.parentId || '',
    newIndex: targetIndex
  })
}

function canDropNode(node, targetParentId) {
  return !reorderingNode.value
    && validateFormNodeDrop(formFields.value, node, targetParentId).valid
}

async function handleNodeDrop({ node, newParentId, newIndex }) {
  if (!node || reorderingNode.value) return
  const plan = buildFormNodeDropPlan(
    formFields.value,
    node,
    newParentId,
    newIndex
  )
  if (!plan.valid) {
    ElMessage.warning(plan.message || '该节点不能移动到目标容器')
    return
  }
  const currentSiblings = designChildrenFor(node.parentId || '')
  const currentIndex = currentSiblings.findIndex(item =>
    String(item.id) === String(node.id)
  )
  if (String(node.parentId || '') === String(plan.parentId)
      && currentIndex === plan.targetIndex) {
    return
  }

  node.parentId = plan.parentId
  plan.orderedSiblings.forEach((item, index) => {
    item.sortOrder = index
  })
  applyLocalSiblingOrder(node, plan.targetIndex, plan.orderedSiblings)

  if (!form.value.id || !node.revision) {
    ElMessage.success('节点位置已调整，保存草稿后写入服务器')
    return
  }

  const selectedNodeId = selectedField.value?.id
  reorderingNode.value = true
  const saved = await persistNodeOrder(
    node,
    plan.targetIndex,
    plan.orderedSiblings
  )
  await loadFormFields()
  if (selectedNodeId) {
    selectedField.value = nodeById(selectedNodeId) || null
  }
  reorderingNode.value = false
  if (saved) {
    ElMessage.success('节点位置已保存到草稿，发布后生效')
  }
}

function applyLocalSiblingOrder(node, targetIndex, orderedSiblings) {
  const previous = orderedSiblings[targetIndex - 1]
  const next = orderedSiblings[targetIndex + 1]
  const previousOrder = Number(previous?.orderKey || 0)
  const nextOrder = Number(next?.orderKey || 0)
  if (!previous && nextOrder > 1) {
    node.orderKey = Math.max(1, Math.floor(nextOrder / 2))
    return
  }
  if (previous && !next) {
    node.orderKey = previousOrder + FORM_NODE_ORDER_STEP
    return
  }
  if (previous && next && nextOrder - previousOrder > 1) {
    node.orderKey = previousOrder + Math.floor((nextOrder - previousOrder) / 2)
    return
  }
  orderedSiblings.forEach((item, index) => {
    item.orderKey = (index + 1) * FORM_NODE_ORDER_STEP
  })
}

async function persistNodeOrder(field, newIndex, orderedSiblings = formFields.value) {
  if (!form.value.id || !field?.revision) return false
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
    rememberNodeBaseline(field)
    const latest = await getFormById(form.value.id)
    form.value.revision = latest.revision
    await loadDiff()
    return true
  } catch (error) {
    handleRevisionConflict(error, field)
    return false
  }
}

// 打开事件配置弹框
function openEventConfig() {
  if (!selectedField.value) return
  currentEventField.value = selectedField.value
  showEventConfig.value = true
}

function openLinkageConfig(tab = 'display-state') {
  linkageInitialTab.value = tab
  showLinkageConfig.value = true
}

function openNodeInteractionTab(tab = 'state') {
  activeNodeSettingsTab.value = 'interaction'
  activeNodeInteractionTab.value = tab
}

function openUnifiedEventBindings(targetType) {
  if (!form.value.id) {
    ElMessage.warning('请先保存表单草稿')
    return
  }
  if (targetType === 'FIELD') {
    if (!selectedField.value?.fieldCode) {
      ElMessage.warning('当前节点没有稳定字段编码，无法绑定字段事件')
      return
    }
    eventBindingDialogRef.value?.openField(selectedField.value)
  } else {
    eventBindingDialogRef.value?.openOwner(form.value.formName || '')
  }
}

function openEntitySelectionMapping() {
  selectionMappingDialogRef.value?.open(selectedField.value)
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
  selectedField.value.validationRules = stringifyConfig(
    normalizeFormFieldValidation(
      selectedField.value.fieldType,
      {
        ...selectedValidationConfig.value,
        [key]: value
      }
    )
  )
}

function validateNodeValidationRules(field) {
  const config = safeParseConfig(field?.validationRules)
  const patternError = getRuntimeRegexPatternError(config.pattern)
  if (!patternError) return
  const label =
    field?.fieldLabel || field?.fieldName || field?.fieldCode || '当前字段'
  throw new Error(`“${label}”${patternError}`)
}

function updateSelectedNodeConfig(key, value) {
  if (!selectedField.value) return
  selectedField.value.componentProps = stringifyConfig({
    ...selectedNodeConfig.value,
    [key]: value
  })
}

function handleCompatibleComponentChange() {
  if (!selectedField.value) return
  const descriptor = getFormFieldComponentDescriptor(selectedField.value.componentType)
  const fieldType = String(selectedField.value.fieldType || '').toUpperCase()
  const supported = descriptor?.supportedFieldTypes || []
  if (supported.length
      && !supported.map(type => String(type).toUpperCase()).includes(fieldType)) {
    ElMessage.warning('该组件与当前字段类型不兼容，已恢复默认组件')
    selectedField.value.componentType = getDefaultComponentType(fieldType)
  }
  selectedField.value.componentProps = '{}'
  selectedField.value.validationRules = '{}'
  selectedField.value.dataSourceBindings = {}
  selectedField.value.dataSourceId = ''
  selectedField.value.dataSourceInputMappingText = '{}'
  selectedField.value.dataSourceOutputMappingText = '{}'
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

async function handleReferenceEntitySelected(entity) {
  rememberEntityOption(entity)
  const targetEntityId = entity?.id || ''
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
  if (selectedField.value && !selectedField.value.refEntityCode) {
    const options = await entityApi.resolveOptions({ ids: [String(targetEntityId)] }).catch(() => [])
    const entity = options?.[0]
    rememberEntityOption(entity)
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
  if (isTabNode.value && !selectedField.value.parentId) {
    ElMessage.warning('请选择所属 Tab 集合后再保存 Tab 页')
    return
  }
  if (selectedField.value.parentId) {
    const parent = nodeById(selectedField.value.parentId)
    if (!isValidParentCandidate(parent, selectedField.value)) {
      ElMessage.warning('当前父容器不兼容，或节点树会形成循环/超过 8 层')
      return
    }
  } else if (!canPlaceFormNodeAtRoot(selectedNodeType.value)
      || getSubtreeHeight(selectedField.value.id) > FORM_NODE_MAX_DEPTH) {
    ElMessage.warning('当前节点不能放在根节点，或节点树超过 8 层')
    return
  }
  try {
    validateNodeValidationRules(selectedField.value)
  } catch (error) {
    ElMessage.warning(error.message)
    return
  }
  savingNode.value = true
  try {
    const currentFormId = await ensureFormMetadata()
    await ensureChildFormReleaseBinding(selectedField.value)
    await ensureSubListBinding(selectedField.value)
    validateNodeDataSourceMappings(selectedField.value)
    const payload = fieldToNodePayload(selectedField.value, {
      forPatch: selectedField.value.revision > 0
    })
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

function validateNodeDataSourceMappings(field) {
  if (!field) return
  syncNodeDataSourceBinding(field, { throwOnError: true })
  const label = field.fieldLabel || field.fieldName || field.fieldCode || '当前节点'
  Object.entries(parseDocument(field.dataSourceBindings)).forEach(
    ([usage, binding]) => {
      if (!binding || typeof binding === 'string') return
      parseJsonConfig(binding.inputMapping || {}, {
        fieldName: `${label}${usage}输入映射`
      })
      parseJsonConfig(binding.outputMapping || {}, {
        fieldName: `${label}${usage}输出映射`
      })
    }
  )
}

async function handlePublish() {
  if (!form.value.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  if (hasUnsavedLocalChanges()) {
    ElMessage.warning('画布或属性仍有未保存修改，请先保存草稿后再发布')
    return
  }
  const diff = await getFormDiff(form.value.id)
  if (!diff.changed) {
    ElMessage.info('当前草稿与已发布版本一致')
    return
  }
  publishDialogVisible.value = true
}

async function handlePublished(release) {
  await loadFormInfo()
  await loadDiff()
  if (release?.releaseMode === 'STANDARD'
      && entityInfo.value?.lifecycleMode === 'WORKFLOW') {
    ElMessage.info({
      message: '普通发布不会修改运行中实例；请重新发布流程后再新增流程数据；历史实例继续使用原版本。',
      duration: 7000
    })
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
  await releaseHistoryDialogRef.value?.open()
}

async function openRuntimeCode() {
  if (initializing.value) {
    ElMessage.info('表单配置仍在加载，请稍候')
    return
  }
  runtimeCodeLoading.value = true
  try {
    let orderedFields = [...formFields.value].sort((left, right) =>
      Number(left.orderKey || left.sortOrder || 0)
        - Number(right.orderKey || right.sortOrder || 0)
    )
    try {
      orderedFields = orderFormNodesParentFirst(formFields.value)
    } catch {
      // Invalid local hierarchy is still inspectable in its current order.
    }
    const currentFormId = String(form.value.id || '')
    const [eventBindings, releases] = await Promise.all([
      currentFormId
        ? uiEventBindingApi.list('FORM', currentFormId).catch(() => [])
        : Promise.resolve([]),
      currentFormId
        ? getFormReleases(currentFormId)
            .then(normalizeReleaseList)
            .catch(() => [])
        : Promise.resolve([])
    ])
    const draftSnapshot = buildFormDraftRuntimeSnapshot({
      form: {
        ...form.value,
        entityId: form.value.entityId || entityId,
        initConfig: safeParseConfig(form.value.initConfig),
        viewConfig: viewConfig.value
      },
      legacyFields: orderedFields.map((field, index) =>
        fieldToNodeEntity(field, index)
      ),
      nodes: orderedFields.map(field => fieldToNodePayload(field)),
      eventBindings: Array.isArray(eventBindings) ? eventBindings : []
    })
    const activeRelease = selectRuntimeRelease(
      releases,
      form.value.activeReleaseId
    )
    const published = activeRelease?.snapshotDocument
      ? buildRuntimeCodeArtifact({
          configType: 'FORM',
          configLabel: form.value.formName || form.value.formKey || '表单',
          source: 'PUBLISHED',
          version: activeRelease.version,
          snapshot: safeParseConfig(activeRelease.snapshotDocument)
        })
      : null
    runtimeCodeDialogRef.value?.open({
      type: 'FORM',
      label: form.value.formName || form.value.formKey || '新建表单',
      draft: buildRuntimeCodeArtifact({
        configType: 'FORM',
        configLabel: form.value.formName || form.value.formKey || '表单',
        source: 'DRAFT',
        snapshot: draftSnapshot
      }),
      published,
      dirty: hasUnsavedLocalChanges(),
      changed: diffInfo.value.changed === true
    })
  } catch (error) {
    console.error('生成表单最终代码失败:', error)
    ElMessage.error(error?.message || '生成表单最终代码失败')
  } finally {
    runtimeCodeLoading.value = false
  }
}

async function handleReleaseChanged() {
  await loadFormInfo()
  await loadDiff()
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

  let orderedFields
  try {
    for (const field of formFields.value) {
      const label =
        field.fieldLabel || field.fieldName || field.fieldCode || field.id
      validateNodeValidationRules(field)
      if (field.parentId) {
        const parent = nodeById(field.parentId)
        if (!isValidParentCandidate(parent, field)) {
          throw new Error(`“${label}”的父容器不兼容`)
        }
      } else if (!canPlaceFormNodeAtRoot(nodeTypeOf(field))
          || getSubtreeHeight(field.id) > FORM_NODE_MAX_DEPTH) {
        throw new Error(`“${label}”不能放在表单根节点`)
      }
    }
    orderedFields = orderFormNodesParentFirst(formFields.value)
  } catch (error) {
    ElMessage.warning(error.message || '表单节点父子关系无效')
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
        customComponent: isSystemEntity.value ? '' : form.value.customComponent,
        customComponentVersion: isSystemEntity.value
          ? null
          : form.value.customComponentVersion,
        customComponentSnapshotVersion:
          isSystemEntity.value
            ? null
            : form.value.customComponentSnapshotVersion,
        initConfig: isSystemEntity.value
          ? null
          : safeParseConfig(form.value.initConfig),
        viewConfig: viewConfig.value
      })
      form.value = { ...form.value, ...updated }
      currentFormId = form.value.id
      draftChanged = true
    } else {
      currentFormId = await ensureFormMetadata()
      draftChanged = true
    }

    for (const field of orderedFields) {
      if (isSystemEntity.value && field.fieldId) {
        field.isReadonly = 1
      }
      await ensureChildFormReleaseBinding(field)
      await ensureSubListBinding(field)
      validateNodeDataSourceMappings(field)
    }
    await replaceFormNodes(
      currentFormId,
      form.value.revision,
      orderedFields.map(fieldToNodeEntity)
    )
    draftChanged = true
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
  try {
    await loadEntityInfo()
    await loadFormInfo()
    await loadEntityFields()
    await loadFormFields()
    await loadDataSources()
    await loadComponentTemplates()
    await loadExtensionDefinitions()
    await loadDiff()
  } finally {
    initializing.value = false
  }
})
</script>

<style scoped>
.entity-form-design {
  height: 100vh;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f5f7fa;
}

.system-config-alert {
  flex: 0 0 auto;
  margin: 12px 16px 0;
}

.design-header {
  flex: 0 0 auto;
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

.design-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

/* 左侧字段面板 */
.field-panel {
  flex: 0 0 260px;
  min-height: 0;
  width: 260px;
  border-right: 1px solid #dcdfe6;
  background-color: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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

.mode-access-tip {
  color: #606266;
  font-size: 12px;
  line-height: 1.7;
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
  min-height: 0;
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

.form-canvas-wrapper {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow: auto;
  background-color: #f0f2f5;
}

.form-canvas {
  min-height: 400px;
  background-color: #fff;
  border-radius: 4px;
  padding: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.form-drag-guide {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 14px;
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  font-size: 12px;
}

/* 设计表单样式 */
.design-form {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
}

.root-design-drop-zone {
  display: flex;
  flex: 1 1 100%;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 12px;
  min-width: 0;
  min-height: 120px;
}

.empty-tip {
  padding: 80px 0;
}

.property-form {
  padding: 16px;
}

.node-summary {
  padding: 0 16px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.node-summary-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.node-summary-heading strong {
  min-width: 0;
  margin-right: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-summary-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 12px;
  margin: 10px 0 0;
}

.node-summary-meta div {
  min-width: 0;
}

.node-summary-meta dt,
.node-summary-meta dd {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
}

.node-summary-meta dt {
  color: var(--el-text-color-secondary);
}

.node-summary-meta dd {
  overflow: hidden;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-summary p {
  margin-top: 4px;
  margin-bottom: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.node-property-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.node-property-actions span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  text-align: left;
}

.node-settings-tabs {
  padding: 0 12px;
}

.node-settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.node-settings-tabs :deep(.el-tabs__item) {
  min-width: 0;
  padding: 0 6px;
  font-size: 12px;
}

.node-settings-tabs :deep(.el-tabs__content) {
  display: none;
}

.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.property-subheading {
  margin: 14px 0 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.interaction-entry {
  padding: 8px 0 4px;
}

.rule-bridge {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}

.rule-bridge strong {
  display: block;
  margin-bottom: 4px;
  color: var(--el-text-color-primary);
}

.rule-bridge p,
.interaction-entry p {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.rule-bridge p {
  margin-bottom: 0;
}

.interaction-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.node-interaction-tabs :deep(.el-tabs__item) {
  padding: 0 8px;
  font-size: 12px;
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
    height: 100vh;
    min-height: 0;
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

  .field-panel {
    flex-basis: 240px;
    width: 240px;
    min-width: 0;
    min-height: 0;
  }

  .canvas-panel {
    min-height: 0;
    overflow: hidden;
  }

  .form-canvas-wrapper {
    min-height: 0;
    padding: 12px;
  }

  .form-canvas {
    padding: 16px;
  }
}

@media (max-width: 900px) {
  .design-body {
    flex-direction: column;
  }

  .field-panel {
    flex: 0 0 auto;
    width: 100%;
    min-height: 0;
    max-height: 340px;
    border-right: 0;
    border-bottom: 1px solid #dcdfe6;
  }

  .canvas-panel {
    flex: 1 1 auto;
    min-height: 0;
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
