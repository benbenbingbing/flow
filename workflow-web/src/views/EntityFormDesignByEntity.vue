<template>
  <div
    v-loading="discardDraftLoading || initializing"
    class="entity-form-design"
  >
    <div class="design-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="title">表单设计 - {{ form.formName || '新建表单' }}</span>
      </div>
      <div class="header-right">
        <el-tag :type="draftStatus.type" effect="plain">
          {{ draftStatus.label }}
        </el-tag>
        <el-button
          v-if="canDiscardDraft"
          link
          type="danger"
          :loading="discardDraftLoading"
          :disabled="discardDraftLoading || saving || initializing"
          @click="handleDiscardDraft"
        >
          撤销
        </el-button>
        <el-button
          :loading="runtimeCodeLoading"
          :disabled="initializing"
          @click="openRuntimeCode"
        >
          <el-icon><Document /></el-icon>查看最终代码
        </el-button>
        <el-button
          :disabled="isCustomRendererMode && !form.customComponent"
          @click="showPreview = true"
        >
          <el-icon><View /></el-icon>预览
        </el-button>
        <el-badge
          :value="relatedContentCount"
          :hidden="relatedContentCount === 0"
          class="related-content-entry"
        >
          <el-button
            :disabled="!form.id || initializing"
            @click="openRelatedContent"
          >
            <el-icon><Connection /></el-icon>关联内容
          </el-button>
        </el-badge>
        <el-button @click="showReleaseHistory">版本</el-button>
        <el-button
          type="success"
          plain
          :disabled="!isEdit || discardDraftLoading"
          @click="handlePublish"
        >
          发布
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="discardDraftLoading"
          @click="handleSave"
        >
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

    <div v-if="!isCustomRendererMode" class="design-body">
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
                    :action-buttons="viewConfig.actionBar.customButtons"
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
        :title="selectedNodeTitle"
        direction="rtl"
        size="50vw"
        append-to-body
        class="node-property-drawer"
      >
        <template #header="{ titleId, titleClass }">
          <div class="node-property-heading">
            <span
              :id="titleId"
              :class="[titleClass, 'node-property-heading-title']"
              :title="selectedNodeTitle"
            >{{ selectedNodeTitle }}</span>
            <template v-if="selectedField">
              <el-tag size="small" effect="plain">{{ selectedNodeTypeLabel }}</el-tag>
              <el-tag
                size="small"
                :type="selectedNodeDirty ? 'warning' : 'success'"
                effect="plain"
              >
                {{ selectedNodeDirty ? '未保存' : '已保存' }}
              </el-tag>
            </template>
          </div>
        </template>
        <template v-if="selectedField">
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
          
          <el-scrollbar class="node-property-scrollbar">
            <el-form label-width="90px" size="small" class="property-form">
              <SettingsSection
                v-show="activeNodeSettingsTab === 'basic'"
                title="基础属性"
                description="当前节点最常修改的显示、组件、占位和状态配置"
                :default-expanded="true"
                primary
              >
                <template #summary>
                  <el-tag size="small" type="primary">{{ selectedNodeTypeLabel }}</el-tag>
                </template>

                <SettingsFormItem :disabled="!canEditNodeLabel" disabled-reason="当前节点不支持显示标签" :label="isSelectedSection ? '节标题' : '显示标签'">
                  <el-input v-model="selectedField.fieldLabel" />
                </SettingsFormItem>

                <SettingsCapability :disabled="!isFieldNode" reason="仅实体字段支持组件、占位提示和默认值配置">
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
                  <el-form-item label="默认值">
                    <template #label>
                      <ConfigHelpLabel
                        label="默认值"
                        content="需要从接口、实体或 Provider 动态取得默认值时，请在“数据与扩展 → 数据源绑定”中配置“字段默认值”数据源。"
                      />
                    </template>
                    <el-input
                      v-model="selectedField.defaultValue"
                      placeholder="留空表示不设置静态默认值"
                    />
                  </el-form-item>
                </SettingsCapability>

                <SettingsFormItem :disabled="!isFieldNode" disabled-reason="仅实体字段支持此配置">
                  <template #label>
                    <ConfigHelpLabel
                      label="字段状态"
                      content="显示：默认隐藏 → 模式显示权限 → 条件显示；编辑：整表只读 → 查看模式 → 默认只读 → 模式编辑权限 → 条件禁用；必填：默认必填与条件必填任一成立即生效。"
                    />
                  </template>
                  <div class="checkbox-group">
                    <el-checkbox
                      v-model="selectedField.isRequired"
                      :true-label="1"
                      :false-label="0"
                      :disabled="selectedEntityFieldRequired"
                    >必填</el-checkbox>
                    <el-checkbox v-model="selectedField.isReadonly" :true-label="1" :false-label="0">只读</el-checkbox>
                    <el-checkbox v-model="selectedField.isHidden" :true-label="1" :false-label="0">隐藏</el-checkbox>
                  </div>
                </SettingsFormItem>

                <SettingsFormItem label="栅格宽度" :disabled="!canEditGridSpan" disabled-reason="仅可设置宽度的节点在栅格布局中支持此配置">
                  <el-slider :model-value="selectedField.gridSpan" @update:model-value="canEditGridSpan && (selectedField.gridSpan = $event)" :min="1" :max="24" show-stops />
                </SettingsFormItem>
              </SettingsSection>

              <SettingsSection
                :disabled="!canConfigureSelectedNodeModeAccess"
                disabled-reason="仅实体字段支持运行模式权限"
                v-show="activeNodeSettingsTab === 'basic'"
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
                      :disabled="modeOption.editable === false"
                      :model-value="modeOption.editable !== false && getModeAccessValue(modeOption.value, 'editable')"
                      @change="updateModeAccess(modeOption.value, 'editable', $event)"
                    >可编辑</el-checkbox>
                  </div>
                </div>
                <div class="mode-access-tip">
                  审批可编辑：字段在审批办理时的默认编辑权限，流程节点开启“强制整表只读”后本配置不生效。
                  查看模式固定只读，仅控制字段是否显示。
                </div>
              </SettingsSection>

              <FormNodeStateConditions
                :disabled="!isFieldNode"
                v-show="activeNodeSettingsTab === 'basic'"
                :field="selectedField"
                :fields="entityFields"
              />

              <SettingsSection
                v-show="activeNodeSettingsTab === 'basic'"
                title="布局与层级"
                class="node-layout-settings"
                description="说明内容、父子层级、栅格参数和容器外观"
                :default-expanded="canConfigureSelectedContainerAppearance || isTabNode || ['GRID', 'TAB_SET', 'COLLAPSE', 'TEXT'].includes(selectedNodeType)"
              >
                <template #summary>
                  <el-tag size="small" type="info">{{ selectedNodeTypeLabel }}</el-tag>
                </template>

                <SettingsFormItem
                  :disabled="selectedNodeType !== 'TEXT'" disabled-reason="仅文本说明节点支持此配置"
                  :label="isSectionTitleNode ? '节名称' : '说明内容'"
                >
                  <el-input
                    :model-value="selectedNodeConfig.text || selectedNodeConfig.content || ''"
                    :type="isSectionTitleNode ? 'text' : 'textarea'"
                    :rows="isSectionTitleNode ? undefined : 4"
                    :placeholder="isSectionTitleNode ? '请输入节名称' : '请输入说明内容'"
                    @update:model-value="updateSelectedNodeConfig('text', $event)"
                  />
                </SettingsFormItem>

                <el-form-item
                  :label="isTabNode ? '所属 Tab 集合' : '父容器'"
                  :required="isTabNode"
                >
                  <template #label>
                    <ConfigHelpLabel
                      :label="isTabNode ? '所属 Tab 集合' : '父容器'"
                      :content="selectedParentHelp"
                    />
                  </template>
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
                </el-form-item>

                <SettingsFormItem :disabled="selectedNodeType !== 'GRID'" disabled-reason="仅栅格容器支持此配置" label="列间距">
                  <el-input-number
                    :model-value="Number(selectedNodeConfig.gutter || 16)"
                    :min="0"
                    :max="48"
                    @update:model-value="updateSelectedNodeConfig('gutter', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem :disabled="selectedNodeType !== 'GRID'" disabled-reason="仅栅格容器支持此配置" label="默认跨度">
                  <el-input-number
                    :model-value="Number(selectedNodeConfig.defaultSpan || 12)"
                    :min="1"
                    :max="24"
                    @update:model-value="updateSelectedNodeConfig('defaultSpan', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem :disabled="selectedNodeType !== 'TAB_SET'" disabled-reason="仅 Tab 集合支持此配置" label="页签位置">
                  <el-select
                    :model-value="selectedNodeConfig.tabPosition || 'top'"
                    @update:model-value="updateSelectedNodeConfig('tabPosition', $event)"
                  >
                    <el-option label="顶部" value="top" />
                    <el-option label="左侧" value="left" />
                    <el-option label="右侧" value="right" />
                    <el-option label="底部" value="bottom" />
                  </el-select>
                </SettingsFormItem>
                <SettingsFormItem :disabled="selectedNodeType !== 'TAB_SET'" disabled-reason="仅 Tab 集合支持此配置" label="默认页签">
                  <el-select
                    :model-value="selectedNodeConfig.defaultActiveTabKey || ''"
                    clearable
                    placeholder="首个可见页签"
                    @update:model-value="updateSelectedNodeConfig('defaultActiveTabKey', $event || undefined)"
                  >
                    <el-option
                      v-for="tab in designChildrenFor(selectedField.id).filter(item => item.nodeType === 'TAB')"
                      :key="tab.id"
                      :label="tab.fieldLabel || tab.nodeKey || tab.id"
                      :value="tab.nodeKey || tab.id"
                    />
                  </el-select>
                  <div class="form-tip">
                    使用稳定页签标识；页签删除、隐藏或无权限时自动降级到首个可见页签。
                  </div>
                </SettingsFormItem>

                <SettingsFormItem :disabled="selectedNodeType !== 'COLLAPSE'" disabled-reason="仅折叠容器支持此配置" label="默认展开">
                  <el-switch
                    :model-value="selectedNodeConfig.defaultExpanded !== false"
                    @update:model-value="updateSelectedNodeConfig('defaultExpanded', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem :disabled="selectedNodeType !== 'COLLAPSE'" disabled-reason="仅折叠容器支持此配置" label="手风琴模式">
                  <el-switch
                    :model-value="selectedNodeConfig.accordion === true"
                    @update:model-value="updateSelectedNodeConfig('accordion', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!canConfigureSelectedContainerAppearance" disabled-reason="仅容器节点支持外观配置"
                  label="保留内边距"
                >
                  <el-switch
                    aria-label="保留容器内边距"
                    :model-value="selectedContainerAppearance.showPadding"
                    @update:model-value="updateSelectedNodeConfig('showPadding', $event)"
                  />
                  <span class="field-help">
                    关闭后子节点贴合当前容器，适合多层嵌套。
                  </span>
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!canConfigureSelectedContainerAppearance" disabled-reason="仅容器节点支持外观配置"
                  label="显示边框线"
                >
                  <el-switch
                    aria-label="显示容器边框线"
                    :model-value="selectedContainerAppearance.showBorder"
                    @update:model-value="updateSelectedNodeConfig('showBorder', $event)"
                  />
                  <span class="field-help">
                    关闭后仅隐藏业务边框，选中和拖拽提示仍保留。
                  </span>
                </SettingsFormItem>
                <div class="form-tip">
                  同级排序请在画布中调整，技术标识和节点类型不可直接修改。
                </div>
              </SettingsSection>

              <SettingsSection
                :disabled="!canConfigureSelectedNodeValidation"
                disabled-reason="当前字段类型不支持长度、数值范围、格式或正则校验"
                v-show="activeNodeSettingsTab === 'rules'"
                title="校验规则"
                description="不适用于当前字段类型的规则置灰，悬停可查看原因"
              >
                <template #summary>
                  <el-tag size="small" :type="selectedValidationRuleCount ? 'success' : 'info'">
                    {{ selectedValidationRuleCount ? `${selectedValidationRuleCount} 项规则` : '未配置' }}
                  </el-tag>
                </template>

                <SettingsFormItem
                  :disabled="!(selectedValidationCapabilities.length)" disabled-reason="仅文本类型支持长度校验"
                  label="最小长度"
                >
                  <el-input-number
                    :model-value="selectedValidationConfig.minLength"
                    :min="0"
                    :max="20000"
                    @update:model-value="updateValidationConfig('minLength', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!(selectedValidationCapabilities.length)" disabled-reason="仅文本类型支持长度校验"
                  label="最大长度"
                >
                  <el-input-number
                    :model-value="selectedValidationMaxLength"
                    :min="0"
                    :max="20000"
                    @update:model-value="updateValidationConfig('maxLength', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!canConfigureSelectedWordLimit" disabled-reason="仅文本输入和多行文本组件支持显示字数"
                  label="显示字数"
                >
                  <el-switch
                    :model-value="selectedWordLimitVisible"
                    @update:model-value="updateSelectedNodeConfig('showWordLimit', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!(selectedValidationCapabilities.range)" disabled-reason="仅数值类型支持范围校验"
                  label="最小值"
                >
                  <el-input-number
                    :model-value="selectedValidationConfig.min"
                    @update:model-value="updateValidationConfig('min', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!(selectedValidationCapabilities.range)" disabled-reason="仅数值类型支持范围校验"
                  label="最大值"
                >
                  <el-input-number
                    :model-value="selectedValidationConfig.max"
                    @update:model-value="updateValidationConfig('max', $event)"
                  />
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!(selectedValidationCapabilities.format)" disabled-reason="仅文本类型支持格式校验"
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
                </SettingsFormItem>
                <SettingsFormItem
                  :disabled="!(selectedValidationCapabilities.pattern)" disabled-reason="仅文本类型支持正则校验"
                  :error="selectedPatternError"
                >
                  <template #label>
                    <ConfigHelpLabel
                      label="正则"
                      content="输入 JavaScript/Java 通用的正则表达式本体，不要添加 / 包裹。需要校验完整内容时请使用 ^ 和 $；与“格式”同时配置时必须全部通过。"
                    />
                  </template>
                  <div class="regex-validation-editor">
                    <div class="regex-pattern-row">
                      <el-input
                        :model-value="selectedValidationConfig.pattern || ''"
                        clearable
                        :maxlength="500"
                        placeholder="例如：^[A-Z][A-Z0-9_]*$"
                        @update:model-value="updateValidationConfig('pattern', $event)"
                      />
                      <el-button
                        type="primary"
                        link
                        :aria-expanded="regexTestVisible"
                        @click="toggleRegexTest"
                      >
                        test
                      </el-button>
                    </div>
                    <div v-if="regexTestVisible" class="regex-test-row">
                      <el-input
                        v-model="regexTestValue"
                        clearable
                        :disabled="isRegexTestInputDisabled"
                        placeholder="输入测试文本"
                        @update:model-value="regexTestTouched = true"
                      />
                      <el-icon
                        v-if="regexTestResult !== null"
                        :class="[
                          'regex-test-result',
                          regexTestResult ? 'is-match' : 'is-mismatch'
                        ]"
                        :title="regexTestResult ? '匹配成功' : '不匹配'"
                      >
                        <CircleCheck v-if="regexTestResult" />
                        <CircleClose v-else />
                      </el-icon>
                    </div>
                    <div
                      v-if="regexTestVisible && isRegexTestInputDisabled"
                      class="regex-test-hint"
                    >
                      请先输入有效的正则表达式
                    </div>
                  </div>
                </SettingsFormItem>
              </SettingsSection>

              <SettingsSection
                :disabled="!canConfigureSelectedNodeCrossField"
                disabled-reason="仅当前实体内的数值、日期和日期时间字段支持跨字段校验"
                v-show="activeNodeSettingsTab === 'rules'"
                title="跨字段校验"
                description="比较当前字段与同一实体中的其他字段"
              >
                <template #summary>
                  <el-tag size="small" :type="selectedCrossFieldRuleCount ? 'success' : 'info'">
                    {{ selectedCrossFieldRuleCount ? `${selectedCrossFieldRuleCount} 项规则` : '未配置' }}
                  </el-tag>
                </template>
                <FormCrossFieldRuleEditor
                  :field="selectedField"
                  :fields="crossFieldCandidateFields"
                  :model-value="selectedValidationConfig.crossField"
                  @update:model-value="updateValidationConfig('crossField', $event)"
                />
              </SettingsSection>

              <SettingsSection
                :disabled="!canConfigureSelectedNodeUniqueness"
                disabled-reason="仅绑定实体字段且类型支持比较时可配置唯一性"
                v-show="activeNodeSettingsTab === 'rules'"
                title="唯一性"
                description="规则只属于当前表单；未配置该规则的其他表单不会触发前后端校验"
              >
                <template #summary>
                  <el-tag
                    size="small"
                    :type="selectedUniquenessConfig.mode === 'NONE' ? 'info' : 'success'"
                  >
                    {{ selectedUniquenessModeLabel }}
                  </el-tag>
                </template>

                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  class="uniqueness-scope-tip"
                  title="严格按当前表单发布版生效"
                  description="其他表单未设置时不会发起预检，服务端提交也不会套用本表单规则。"
                />

                <el-form-item label="唯一模式">
                  <el-select
                    :model-value="selectedUniquenessConfig.mode"
                    style="width: 100%"
                    @update:model-value="updateSelectedUniqueness('mode', $event)"
                  >
                    <el-option label="不校验" value="NONE" />
                    <el-option label="当前字段全局唯一" value="GLOBAL" />
                    <el-option label="满足条件时唯一" value="CONDITIONAL" />
                  </el-select>
                </el-form-item>

                <template v-if="selectedUniquenessConfig.mode !== 'NONE'">
                  <el-form-item label="忽略空值">
                    <el-switch
                      :model-value="selectedUniquenessConfig.ignoreBlank"
                      @update:model-value="updateSelectedUniqueness('ignoreBlank', $event)"
                    />
                  </el-form-item>
                  <el-form-item label="错误提示">
                    <el-input
                      :model-value="selectedUniquenessConfig.message || ''"
                      maxlength="200"
                      placeholder="例如：项目名称在当前状态下已存在"
                      @update:model-value="updateSelectedUniqueness('message', $event)"
                    />
                  </el-form-item>

                  <div
                    v-if="selectedUniquenessConfig.mode === 'CONDITIONAL'"
                    class="uniqueness-condition-editor"
                  >
                    <div class="uniqueness-subtitle">唯一规则生效条件</div>
                    <div class="form-tip">
                      只有当前记录满足条件时才查重；参与比较的数据也必须满足同一条件。
                    </div>
                    <FlowConditionGroupEditor
                      :group="selectedUniquenessConditionRoot"
                      :entity-fields="uniqueConditionFields"
                      :include-approval-property="false"
                      :operator-options="uniqueConditionOperatorOptions"
                      @change="persistSelectedUniquenessCondition"
                    />
                  </div>

                  <div class="uniqueness-precheck-editor">
                    <div class="uniqueness-subtitle">提前重复检查</div>
                    <el-form-item label="启用预检">
                      <el-switch
                        :model-value="selectedUniquenessConfig.precheck.enabled"
                        @update:model-value="updateSelectedUniquenessPrecheck('enabled', $event)"
                      />
                    </el-form-item>
                    <template v-if="selectedUniquenessConfig.precheck.enabled">
                      <el-form-item label="触发时机">
                        <el-select
                          :model-value="selectedUniquenessConfig.precheck.trigger"
                          style="width: 100%"
                          @update:model-value="updateSelectedUniquenessPrecheck('trigger', $event)"
                        >
                          <el-option label="字段变化后" value="CHANGE" />
                          <el-option label="字段失焦后" value="BLUR" />
                          <el-option label="仅提交时" value="SUBMIT_ONLY" />
                        </el-select>
                      </el-form-item>
                      <el-form-item
                        v-if="selectedUniquenessConfig.precheck.trigger === 'CHANGE'"
                        label="防抖时间"
                      >
                        <el-input-number
                          :model-value="selectedUniquenessConfig.precheck.debounceMs"
                          :min="200"
                          :max="3000"
                          :step="100"
                          controls-position="right"
                          @update:model-value="updateSelectedUniquenessPrecheck('debounceMs', $event)"
                        />
                        <span class="uniqueness-unit">毫秒</span>
                      </el-form-item>
                      <el-form-item
                        v-if="selectedUniquenessConfig.mode === 'CONDITIONAL'
                          && selectedUniquenessConfig.precheck.trigger === 'CHANGE'"
                        label="监听条件字段"
                      >
                        <el-switch
                          :model-value="selectedUniquenessConfig.precheck.watchConditionFields"
                          @update:model-value="updateSelectedUniquenessPrecheck('watchConditionFields', $event)"
                        />
                      </el-form-item>
                    </template>
                  </div>
                </template>
              </SettingsSection>

              <FormNodeDataSettings />

              <SettingsSection
                v-show="activeNodeSettingsTab === 'rules'"
                title="附件项逻辑必填"
                description="条件满足时，指定附件项至少上传一份文件"
                :disabled="!selectedAttachmentItems.length"
                disabled-reason="仅配置了附件项的文件或图片字段支持此配置"
              >
                <FormNodeAttachmentConditions
                  :field="selectedField"
                  :fields="entityFields"
                  :attachment-items="selectedAttachmentItems"
                  :disabled="!selectedAttachmentItems.length"
                />
              </SettingsSection>

              <SettingsSection
                v-show="activeNodeSettingsTab === 'interaction'"
                title="值与计算"
                description="配置值联动和选项联动，随当前节点保存"
                :disabled="!isFieldNode"
                disabled-reason="仅实体字段支持值与计算"
                :default-expanded="true"
                primary
              >
                <FormNodeValueLinkage :field="selectedField" :fields="entityFields" :disabled="!isFieldNode" />
              </SettingsSection>

              <SettingsSection
                v-show="activeNodeSettingsTab === 'interaction'"
                title="事件与回填"
                :disabled="!isFieldNode"
                disabled-reason="仅实体字段支持事件与回填"
                :default-expanded="true"
              >
                <FormNodeEventBindings
                  :form-id="form.id || ''"
                  :field="selectedField"
                  :form-fields="formFields"
                  :field-options="eventFieldOptions"
                  :enabled="isFieldNode"
                  @changed="loadDiff"
                />
              </SettingsSection>

              <SettingsSection
                v-show="activeNodeSettingsTab === 'interaction'"
                title="前端脚本事件"
                description="处理浏览器中的字段交互，随当前节点保存、发布后生效"
                :disabled="!isFieldNode"
                disabled-reason="仅实体字段支持前端脚本事件"
              >
                <template #summary>
                  <el-tag :type="hasEventConfig ? 'success' : 'info'" size="small">
                    {{ hasEventConfig ? '已配置' : '未配置' }}
                  </el-tag>
                </template>
                <div class="field-script-overview">
                  <el-button plain :disabled="!isFieldNode" @click="openEventConfig">配置前端脚本</el-button>
                  <div v-if="configuredScriptEvents.length" class="field-script-overview__events" aria-label="已配置的前端事件">
                    <el-tag v-for="event in configuredScriptEvents" :key="event.name" effect="plain">
                      {{ event.label }}
                    </el-tag>
                  </div>
                  <span v-else class="field-script-overview__empty">暂未配置事件</span>
                </div>
              </SettingsSection>

              <SettingsSection
                :disabled="!(canConfigureNodeExtension || isEditableFieldNode || isFieldNode)"
                disabled-reason="当前节点不支持复用与扩展"
                v-show="activeNodeSettingsTab === 'extension'"
                title="复用与扩展"
                description="节点扩展、组件模板和组件参数"
                :default-expanded="!!selectedField.componentName || !!selectedField.fieldComponentName || !!selectedField.templateId"
              >
                <template #summary>
                  <el-tag
                    size="small"
                    :type="selectedField.componentName || selectedField.fieldComponentName || selectedField.templateId ? 'success' : 'info'"
                  >
                    {{ selectedField.componentName || selectedField.fieldComponentName || selectedField.templateId ? '已配置' : '未配置' }}
                  </el-tag>
                </template>

                <SettingsFormItem :disabled="!canConfigureNodeExtension" disabled-reason="当前节点不支持节点扩展" label="节点扩展">
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
                </SettingsFormItem>

                <SettingsCapability :disabled="!isEditableFieldNode" reason="仅字段、子表单和明细表支持锁定模板">
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
                </SettingsCapability>

                <SettingsCapability :disabled="!isFieldNode || !selectedComponentSchema.length" reason="当前组件未提供可配置参数">
                  <div class="property-subheading">组件参数</div>
                  <p v-if="!isFieldNode || !selectedComponentSchema.length" class="form-tip">当前组件未提供可配置参数</p>
                  <ConfigSchemaEditor
                    v-if="isFieldNode && selectedComponentSchema.length"
                    v-model="selectedComponentConfig"
                    :schema="selectedComponentSchema"
                  />
                </SettingsCapability>
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

    <FormCustomRendererWorkspace
      v-else
      :custom-component="form.customComponent"
      :custom-component-version="form.customComponentVersion"
      :custom-component-snapshot-version="form.customComponentSnapshotVersion"
      :selected-custom-form-catalog-option="selectedCustomFormCatalogOption"
      :selected-custom-form-schema="selectedCustomFormSchema"
      :custom-form-available="customFormAvailable"
      :inactive-node-count="formFields.length"
      :custom-form-button-count="customFormButtonCount"
      :form-data-source-binding-count="formDataSourceBindingCount"
      :preview-form="previewForm"
      :preview-mode="previewMode"
      :preview-mode-options="previewModeOptions"
      :preview-actions="previewActions"
      :preview-footer-actions="previewFooterActions"
      :entity-info="entityInfo"
      :entity-fields="entityFields"
      :system-entity="isSystemEntity"
      @update:preview-mode="previewMode = $event"
      @open-form-settings="openFormSettings"
      @open-form-extension-config="showFormExtensionConfig = true"
      @open-extension-management="openExtensionManagement"
      @refresh-extension-catalog="refreshExtensionCatalog"
      @preview-action="handlePreviewAction"
    />

    <FormDesignerSettingsDrawer
      v-model="showFormSettings"
      v-model:active-tab="activeFormSettingsTab"
      v-model:active-behavior-tab="activeFormBehaviorTab"
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
    
    <EventConfigPanel
      v-model:visible="showEventConfig"
      :model-value="currentEventValues"
      :field="currentEventField"
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

    <FormDataSourceDialog
      ref="formDataSourceDialogRef"
      :form="form"
      :interfaces-by-usage="interfacesByUsage"
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
    <RelatedContentPanel
      ref="relatedContentPanelRef"
      owner-type="FORM"
      :owner-id="form.id || ''"
      :source-entity="entityInfo"
      :source-fields="entityFields"
      :source-content-fields="formFields"
      :anchor-options="relatedContentAnchorOptions"
      @count-change="relatedContentCount = $event"
      @changed="handleRelatedContentChanged"
    />

  </div>
</template>

<script setup>
import { resolveFormLabelPosition, resolveFormLabelWidth } from '@/shared/form-layout'

import { ref, computed, watch, onMounted, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBreadcrumbParents } from '@/composables/useBreadcrumbParents'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, View, Search, Document, Edit, DocumentAdd, Plus, Connection, Rank, Setting, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import FormNodeDesignItem from '@/components/FormNodeDesignItem.vue'
import FormNodeDraggableList from '@/components/FormNodeDraggableList.vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import FormActionBar from '@/components/FormActionBar.vue'
import FormNodeValueLinkage from '@/components/form-designer/FormNodeValueLinkage.vue'
import FormNodeAttachmentConditions from '@/components/form-designer/FormNodeAttachmentConditions.vue'
import { getFieldLinkageDraftError } from '@/composables/useFieldValueLinkage'
import { getAttachmentConditionError } from '@/shared/form-field-linkage'
import EventConfigPanel from '@/components/EventConfigPanel.vue'
import { readFieldScripts, writeFieldScripts } from '@/shared/field-event-scripts'
import EventBindingDialog from '@/components/ui-config/EventBindingDialog.vue'
import FormNodeEventBindings from '@/components/form-designer/FormNodeEventBindings.vue'
import FormDataSourceDialog from '@/components/ui-config/FormDataSourceDialog.vue'
import UiConfigReleaseHistoryDialog from '@/components/ui-config/UiConfigReleaseHistoryDialog.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import SettingsCapability from '@/components/SettingsCapability.vue'
import SettingsFormItem from '@/components/SettingsFormItem.vue'
import FlowConditionGroupEditor from '@/components/FlowConditionGroupEditor.vue'
import UiConfigPublishDialog from '@/components/UiConfigPublishDialog.vue'
import FormDesignerSettingsDrawer from '@/components/form-designer/FormDesignerSettingsDrawer.vue'
import FormCustomRendererWorkspace from '@/components/form-designer/FormCustomRendererWorkspace.vue'
import FormNodeDataSettings from '@/components/form-designer/FormNodeDataSettings.vue'
import FormCrossFieldRuleEditor from '@/components/form-designer/FormCrossFieldRuleEditor.vue'
import FormNodeStateConditions from '@/components/form-designer/FormNodeStateConditions.vue'
import { getFieldStateConditionError } from '@/shared/form-field-state-conditions'
import { supportsCrossFieldValidation, validateCrossFieldConfiguration } from '@/shared/form-cross-field-validation'
import RuntimeCodeViewerDialog from '@/components/RuntimeCodeViewerDialog.vue'
import RelatedContentPanel from '@/components/related-content/RelatedContentPanel.vue'
import { FORM_DESIGNER_CONTEXT_KEY } from '@/components/form-designer/context'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import {
  getFormFieldComponentDescriptor,
  getFormFieldComponentOptions,
  hasFormFieldComponent
} from '@/components/form-fields'
import {
  getCustomFormComponentOptions,
  getCustomFormDescriptor,
  hasCustomFormComponent
} from '@/utils/customComponentRegistry'
import {
  FORM_RENDERER_MODE_CUSTOM,
  FORM_RENDERER_MODE_DEFAULT,
  resolveFormRendererMode,
  shouldPersistFormNodes
} from '@/shared/form-renderer-mode'
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
  resolveFormNodeBinding,
  resolveFormNodeLayoutSpan
} from '@/shared/form-node-property-schema'
import {
  resolveFormContainerAppearance,
  supportsFormContainerAppearance
} from '@/shared/form-container-appearance'
import {
  normalizeFormFieldUniqueness,
  supportsFormFieldUniqueness,
  validateFormFieldUniqueness
} from '@/shared/form-field-uniqueness'
import {
  createFlowConditionConfig,
  createFlowConditionGroup
} from '@/utils/flowConditionGroups'
import {
  getDefaultFormFieldComponentType as getDefaultComponentType
} from '@/shared/form-field-component-policy'
import {
  FORM_FIELD_EXTENSION_TYPE,
  resolveFormFieldExtensionName
} from '@/shared/form-field-extension'
import {
  getRuntimeRegexPatternError,
  resolveVarcharFieldLength,
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
  resolveLocalFormActions,
  validateFormActionConfiguration
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
  uiConfigDraftApi,
  uiComponentTemplateApi,
  uiExtensionApi,
  uiEventBindingApi
} from '@/api/uiConfig'
import {
  normalizeInterfaceExtensions,
  resolveInterfaceExtensionId
} from '@/components/ui-config/interfaceExtensionModel'
import {
  buildUiConfigDraftDiscardRequest,
  canDiscardUiConfigDraft,
  isUiConfigDraftDiscardConflict,
  resolveUiConfigDraftStatus
} from '@/shared/ui-config-draft'

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
// 只在服务端确认 actionBar 已持久化后递增，供按钮面板解锁事件配置。
const formActionPersistenceRevision = ref(0)
const persistedFormButtonKeys = ref([])
const showPreview = ref(false)
const showFormSettings = ref(false)
const activeFormSettingsTab = ref('basic')
const activeFormBehaviorTab = ref('data-source')
const formRendererMode = ref(FORM_RENDERER_MODE_DEFAULT)
const previewMode = ref('create')
const propertyDrawerVisible = ref(false)
const showEventConfig = ref(false)
const showFormExtensionConfig = ref(false)
const formDataSourceDialogRef = ref(null)
const eventBindingDialogRef = ref(null)
const releaseHistoryDialogRef = ref(null)
const runtimeCodeDialogRef = ref(null)
const relatedContentPanelRef = ref(null)
const relatedContentCount = ref(0)
const runtimeCodeLoading = ref(false)
const currentEventField = ref(null)
const activeNodeSettingsTab = ref('basic')
const publishDialogVisible = ref(false)
const diffInfo = ref({ changed: true, changedSections: [] })
const diffLoadSucceeded = ref(false)
const discardDraftLoading = ref(false)
const interfaceExtensions = ref([])
const interfacesByUsage = ref({})
const extensionDefinitions = ref([])
const formNodes = ref([])
const isCustomRendererMode = computed(() =>
  formRendererMode.value === FORM_RENDERER_MODE_CUSTOM
)
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
const relatedContentAnchorOptions = computed(() => formFields.value
  .filter(node => node?.id || node?.nodeKey)
  .map(node => ({
    value: String(node.id || node.nodeKey),
    label: `${nodeLabel(node)}（${formNodeTypeLabel(node.nodeType || legacyNodeType(node))}之后）`,
    nodeType: String(node.nodeType || legacyNodeType(node) || '').toUpperCase()
  })))
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
const uniqueConditionOperatorOptions = [
  { label: '等于 (==)', value: '==' },
  { label: '不等于 (!=)', value: '!=' },
  { label: '大于 (>)', value: '>' },
  { label: '小于 (<)', value: '<' },
  { label: '大于等于 (>=)', value: '>=' },
  { label: '小于等于 (<=)', value: '<=' },
  { label: '包含', value: 'contains' },
  { label: '为空', value: 'empty' },
  { label: '不为空', value: 'notEmpty' }
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
  formRendererMode.value = FORM_RENDERER_MODE_DEFAULT
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
const regexTestVisible = ref(false)
const regexTestValue = ref('')
const regexTestTouched = ref(false)
watch(() => selectedField.value?.id, resetRegexTest)
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
  layoutType: 'grid',
  status: 1,
  dataSourceBindingsDocument: null,
  customComponent: '',
  viewConfig: ''
})
useBreadcrumbParents(() => [{
  id: 'entity-form-list',
  menuName: '实体表单',
  // 接口加载后以表单归属为准，不依赖进入页面时是否携带 entityId 参数。
  path: form.value.entityId
    ? `/entity-form/list-by-entity/${encodeURIComponent(form.value.entityId)}`
    : ''
}])
const canDiscardDraft = computed(() => canDiscardUiConfigDraft({
  diffLoadSucceeded: diffLoadSucceeded.value,
  diff: diffInfo.value,
  serverCanDiscardDraft: diffInfo.value.canDiscardDraft === true,
  activeReleaseId: form.value.activeReleaseId
}))
const draftStatus = computed(() => resolveUiConfigDraftStatus({
  diffLoadSucceeded: diffLoadSucceeded.value,
  diff: diffInfo.value
}))

const selectedCustomFormSchema = computed(() =>
  getCustomFormDescriptor(form.value.customComponent)?.configSchema || []
)
const selectedAttachmentItems = computed(() =>
  attachmentItemsForField(selectedField.value)
)
const selectedEntityFieldRequired = computed(() =>
  isEntityFieldFixedRequired(selectedField.value)
)
const customFormAvailable = computed(() =>
  Boolean(form.value.customComponent)
  && hasCustomFormComponent(form.value.customComponent)
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

function handleNodeExtensionChange(componentName) {
  if (componentName && selectedField.value?.fieldComponentName) {
    selectedField.value.componentType = getDefaultComponentType(
      selectedField.value.fieldType
    )
    selectedField.value.fieldComponentName = ''
    selectedField.value.fieldComponentVersion = null
    selectedField.value.fieldComponentSnapshotVersion = null
  }
  const descriptor = nodeExtensionOptions.value.find(
    option => option.value === componentName
  )
  selectedField.value.componentVersion = descriptor?.version || null
  selectedField.value.snapshotVersion = descriptor?.snapshotVersion || null
}

function refreshExtensionCatalog() {
  loadExtensionDefinitions()
}

function openFormSettings(tab = 'basic', behaviorTab = '') {
  activeFormSettingsTab.value = tab
  if (tab === 'data-events' && behaviorTab) {
    activeFormBehaviorTab.value = behaviorTab
  }
  showFormSettings.value = true
}

function openRelatedContent() {
  relatedContentPanelRef.value?.open()
}

async function handleRelatedContentChanged(event) {
  // 关联内容属于当前表单草稿；刷新差异后，发布与撤销入口才能立即反映最新状态。
  if (Number.isInteger(Number(event?.ownerRevision))) {
    form.value.revision = Number(event.ownerRevision)
  }
  await loadDiff()
}

/**
 * 消费列表页的数据配置深链，让两个入口落到同一个设置页和同一个编辑器。
 */
function openLinkedFormSettings() {
  if (String(route.query.settings || '') !== 'data-events') return
  const requestedSection = String(route.query.section || 'data-source')
  const section = ['input-parameters', 'data-source', 'events']
    .includes(requestedSection)
    ? requestedSection
    : 'data-source'
  const targetType = String(route.query.targetType || 'OWNER').toUpperCase()
  const targetKey = String(route.query.targetKey || '')
  if (section === 'events' && targetType === 'FIELD' && targetKey) {
    const field = formFields.value.find(item => String(item.fieldCode) === targetKey)
    if (field) {
      eventBindingDialogRef.value?.openField(field)
      return
    }
  }
  if (section === 'events' && targetType === 'BUTTON' && targetKey) {
    eventBindingDialogRef.value?.openButton({ key: targetKey, label: targetKey })
    return
  }
  openFormSettings('data-events', section)
}

function validateCustomRendererSelection() {
  if (!isCustomRendererMode.value) return true
  if (!form.value.customComponent) {
    ElMessage.warning('请选择自定义表单组件')
    return false
  }
  if (!customFormAvailable.value) {
    ElMessage.warning('当前前端未注册该自定义表单组件')
    return false
  }
  return true
}

function shouldSaveCurrentFormNodes() {
  return shouldPersistFormNodes(formRendererMode.value)
}

/** 渲染方式由表单管理的编辑面板维护，设计页只读取草稿中已锁定的配置。 */
function resetRendererModeFromForm() {
  formRendererMode.value = isSystemEntity.value
    ? FORM_RENDERER_MODE_DEFAULT
    : resolveFormRendererMode(form.value.customComponent)
}

function requireDefaultFormNodes() {
  if (!shouldSaveCurrentFormNodes()) {
    return true
  }
  if (formFields.value.length === 0) {
    ElMessage.warning('请至少添加一个字段')
    return false
  }
  return true
}

function validateRendererForSave() {
  if (!validateCustomRendererSelection()) {
    return false
  }
  return requireDefaultFormNodes()
}

function validateRendererForPublish() {
  if (isCustomRendererMode.value && !validateCustomRendererSelection()) {
    return false
  }
  return true
}

/**
 * 在独立标签页打开表单扩展管理，避免离开设计器时丢失尚未保存的本地修改。
 */
function openExtensionManagement() {
  const extensionManagementRoute = router.resolve({
    path: '/dev/extensions',
    query: { type: 'UI_FORM' }
  })
  window.open(extensionManagementRoute.href, '_blank', 'noopener,noreferrer')
}

async function loadExtensionDefinitions({ strict = false } = {}) {
  try {
    extensionDefinitions.value = await uiExtensionApi.list()
  } catch (error) {
    extensionDefinitions.value = []
    if (strict) throw error
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
const canConfigureSelectedContainerAppearance = computed(() =>
  supportsFormContainerAppearance(selectedNodeType.value)
)
// 外观缺键时按节点类型回退到旧版视觉，避免打开历史表单后样式突变。
const selectedContainerAppearance = computed(() =>
  resolveFormContainerAppearance(
    selectedNodeType.value,
    selectedNodeConfig.value
  )
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
const selectedNodeTitle = computed(() => {
  const field = selectedField.value
  if (!field) return '节点属性'
  const label = field.fieldLabel || field.fieldName || selectedNodeTypeLabel.value
  const code = field.fieldCode || field.nodeKey
  return code ? `${label}（${code}）` : label
})
const availableNodeDataSourceUsages = computed(() => {
  const allowed = new Set(getFormNodeDataSourceUsages(selectedNodeType.value))
  return formDataSourceUsages.filter(usage => allowed.has(usage.value))
})

const nodeDataSourceUsageOptions = computed(() => formDataSourceUsages.map(usage => ({
  ...usage,
  disabled: !availableNodeDataSourceUsages.value.some(item => item.value === usage.value)
})))

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
const selectedUniquenessConfig = computed(() =>
  normalizeFormFieldUniqueness(
    selectedValidationConfig.value.uniqueness,
    selectedField.value?.fieldCode
      || selectedField.value?.bindingRef
      || selectedField.value?.nodeKey
  )
)
const selectedUniquenessConditionRoot = computed(() =>
  selectedUniquenessConfig.value.condition?.root || createFlowConditionGroup()
)
const selectedUniquenessModeLabel = computed(() => ({
  NONE: '未配置',
  GLOBAL: '全局唯一',
  CONDITIONAL: '条件唯一'
})[selectedUniquenessConfig.value.mode] || '未配置')
const uniqueConditionFields = computed(() => {
  const currentCode = selectedField.value?.fieldCode
    || selectedField.value?.bindingRef
    || selectedField.value?.nodeKey
  return entityFields.value.filter(field =>
    field.uiConfigurable !== false && field.fieldCode !== currentCode
  )
})
const selectedValidationMaxLength = computed(() => {
  const configuredMaxLength = selectedValidationConfig.value.maxLength
  if (configuredMaxLength !== undefined
      && configuredMaxLength !== null
      && configuredMaxLength !== '') {
    return configuredMaxLength
  }

  // 数据库列长度只用于面板初始展示，不能写入 validationRules 固化为表单规则。
  const entityField = entityFieldForFormField(selectedField.value)
  return resolveVarcharFieldLength(entityField)
    ?? resolveVarcharFieldLength(selectedField.value)
})
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
const isRegexTestInputDisabled = computed(() =>
  !selectedValidationConfig.value.pattern || Boolean(selectedPatternError.value)
)
const regexTestResult = computed(() => {
  if (!regexTestTouched.value || isRegexTestInputDisabled.value) return null
  try {
    // 与表单运行时保持相同的 JavaScript 正则语义，避免设计器测试结果与实际校验不一致。
    const pattern = new RegExp(selectedValidationConfig.value.pattern)
    pattern.lastIndex = 0
    return pattern.test(String(regexTestValue.value ?? ''))
  } catch {
    return null
  }
})
const canConfigureSelectedNodeDataSource = computed(() =>
  selectedNodePropertySchema.value.dataSourceUsages.length > 0
)
const canConfigureSelectedNodeValidation = computed(() =>
  selectedNodePropertySchema.value.rules
    && hasSelectedValidationCapabilities.value
)
const canConfigureSelectedNodeUniqueness = computed(() =>
  selectedNodePropertySchema.value.rules
    && supportsFormFieldUniqueness(selectedField.value)
)
// 只开放当前实体 FIELD；布局容器不影响作用域，子表容器内节点不进入候选列表。
const crossFieldCandidateFields = computed(() => formFields.value.filter(field => {
  if (nodeTypeOf(field) !== 'FIELD' || !entityFieldForFormField(field)) return false
  const visited = new Set()
  let parent = nodeById(field.parentId)
  while (parent && !visited.has(parent.id)) {
    if (['SUB_FORM', 'REPEATER'].includes(nodeTypeOf(parent))) return false
    visited.add(parent.id)
    parent = nodeById(parent.parentId)
  }
  return true
}).map(field => ({ ...field, fieldType: entityFieldForFormField(field).fieldType })))
const canConfigureSelectedNodeCrossField = computed(() => isFieldNode.value
  && (crossFieldCandidateFields.value.some(field => field.id === selectedField.value?.id)
    && supportsCrossFieldValidation(selectedField.value?.fieldType)
    || selectedValidationConfig.value.crossField != null))
const selectedCrossFieldRuleCount = computed(() => selectedValidationConfig.value.crossField?.rules?.length || 0)
const canConfigureSelectedNodeModeAccess = computed(() =>
  selectedNodePropertySchema.value.editable.includes('modeAccess')
)
const isReferenceFieldNode = computed(() =>
  ['REFERENCE', 'MULTI_REFERENCE'].includes(
    String(selectedField.value?.componentType || '').toUpperCase()
  )
)
const canConfigureSelectedNodeRelations = computed(() =>
  selectedNodePropertySchema.value.childForm
    || isSubListField(selectedField.value)
    || isReferenceFieldNode.value
)
// 固定配置入口，能力差异在配置区内以禁用状态展示，切换节点时不跳换 Tab。
const availableNodeSettingsTabs = [
  { value: 'basic', label: '基础与布局' },
  { value: 'rules', label: '数据校验' },
  { value: 'interaction', label: '联动与事件' },
  { value: 'child-pages', label: '子页面' },
  { value: 'extension', label: '数据与扩展' }
]
const selectedNodeDirty = computed(() => {
  const field = selectedField.value
  if (!field?.revision) return true
  return nodeBaselines.value.get(field.id) !== nodeFingerprint(field)
})
const selectedNodeDataSourceBindingCount = computed(() => {
  const field = selectedField.value
  if (!field) return 0
  const bindings = { ...parseDocument(field.dataSourceBindings) }
  const usage = String(field.dataSourceUsage || '').toUpperCase()
  if (usage) {
    if (field.interfaceExtensionId) {
      bindings[usage] = {
        ...(typeof bindings[usage] === 'object' ? bindings[usage] : {}),
        extensionId: field.interfaceExtensionId
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
const selectedNodeInterfaces = computed(() =>
  interfacesByUsage.value[selectedField.value?.dataSourceUsage] || []
)

// 草稿根属性和已保存的 componentProps.events 使用相同读取规则。
const currentEventValues = computed(() => readFieldScripts(currentEventField.value))
const scriptEventLabels = { onInput: '输入中', onChange: '值变化', onBlur: '失焦', onFocus: '聚焦' }
// 概览与编辑器共用读取规则，仅展示包含有效脚本的事件名称，不展示脚本内容。
const configuredScriptEvents = computed(() => Object.keys(readFieldScripts(selectedField.value)).map(name => ({
  name,
  label: scriptEventLabels[name] ? `${scriptEventLabels[name]}（${name}）` : name
})))
const hasEventConfig = computed(() => configuredScriptEvents.value.length > 0)

provide(FORM_DESIGNER_CONTEXT_KEY, {
  form, isCustomRendererMode,
  viewConfig, isEdit, isSystemEntity,
  customFormButtonCount, entityInfo, entityFields, formFields,
  formDataSourceBindingCount, eventFieldOptions,
  formActionPersistenceRevision,
  persistedFormButtonKeys,
  selectedCustomFormSchema, customFormOptions,
  selectedCustomFormCatalogOption, showFormExtensionConfig,
  openFormDataSourceConfig, onEventBindingsChanged: loadDiff,
  createActionSlotForButton,
  openExtensionManagement, refreshExtensionCatalog,
  selectedField, activeNodeSettingsTab, isFieldNode,
  canConfigureSelectedNodeDataSource,
  selectedNodeDataSourceBindingCount, availableNodeDataSourceUsages, nodeDataSourceUsageOptions,
  isNodeDataSourceUsageConfigured, selectNodeDataSourceUsage,
  selectedNodeDataSourceUsageLabel, selectedNodeInterfaces,
  clearSelectedNodeDataSourceBinding, canConfigureSelectedNodeRelations,
  isSubFormField, isSubListField, getEntityNameById, formListByEntity,
  handleChildFormChange, childFormReleases, childFormReleaseLoading,
  handleChildFormReleaseChange, formatChildFormReleaseLabel,
  subListOptions, subListTargetFields, subListTargetFieldsLoading,
  handleSubListChange,
  isReferenceFieldNode, handleReferenceEntitySelected,
  rememberEntityOption, getEntityReferenceSelectionHint,
  referenceListOptions
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

// 标签位置和宽度共用运行时解析规则，调整后画布立即反映表单设置。
const formLabelWidth = computed(() => resolveFormLabelWidth(form.value, viewConfig.value))
const formLabelPosition = computed(() => resolveFormLabelPosition(form.value, viewConfig.value))

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
  // 显式 GRID 容器中的子节点统一按 grid 规则计算，ACTION_SLOT 也能读取自己的宽度。
  return resolveFormNodeLayoutSpan(field, 'grid', fallback)
}

function getNodeDesignStyle(field) {
  const span = resolveFormNodeLayoutSpan(
    field,
    form.value.layoutType,
    24
  )
  const width = `${(span / 24) * 100}%`
  return {
    width,
    flex: `0 0 ${width}`
  }
}

// 加载实体信息
async function loadEntityInfo({ strict = false } = {}) {
  const eid = entityId || form.value.entityId
  if (!eid) {
    if (strict) throw new Error('表单所属实体缺失')
    return
  }
  try {
    const data = await entityApi.getById(eid)
    entityInfo.value = data
    if (!isEdit.value) {
      form.value.formName = data.entityName + '表单'
      form.value.formKey = data.entityCode + '_form'
    }
  } catch (e) {
    console.error('加载实体信息失败:', e)
    if (strict) throw e
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

async function loadSubListOptions(
  targetEntityId,
  targetField = selectedField.value,
  { propagateError = false } = {}
) {
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
    // 交互式加载可降级为空选项；保存校验必须保留真实接口错误，不能误报为引用不存在。
    if (propagateError) throw error
    return []
  }
}

async function loadSubListTargetFields(
  targetEntityId,
  listRef,
  targetField = selectedField.value,
  { propagateError = false } = {}
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
    // 保存校验依赖真实 schema；加载失败不能被折叠成“目标字段不存在”。
    if (propagateError) throw error
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
  const lists = await loadSubListOptions(targetEntityId, field, {
    propagateError: true
  })
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
    null,
    { propagateError: true }
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
    if (isEntityFieldFixedRequired(field)) {
      field.isRequired = 1
    }
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
async function loadEntityFields({ strict = false } = {}) {
  const eid = entityId || form.value.entityId
  if (!eid) {
    if (strict) throw new Error('表单所属实体缺失')
    return
  }

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
    if (strict) throw e
  }
}

// 加载表单信息
async function loadFormInfo({ strict = false } = {}) {
  if (!isEdit.value) {
    if (strict) throw new Error('当前表单尚未创建')
    return
  }
  
  try {
    const data = await getFormById(formId)
    form.value = { ...form.value, ...data, layoutType: data.layoutType || 'vertical' }
    const parsedViewConfig = safeParseConfig(data.viewConfig)
    viewConfig.value = {
      labelWidth: 120,
      customComponentProps: {},
      ...parsedViewConfig,
      actionBar: normalizeFormActionBar(parsedViewConfig.actionBar)
    }
    resetRendererModeFromForm()
    if (data.entityId && !entityId) {
      form.value.entityId = data.entityId
    }
    rememberFormBaseline()
    rememberPersistedFormButtonKeys()
    formActionPersistenceRevision.value += 1
    await loadDiff({ strict })
  } catch (e) {
    console.error('加载表单信息失败:', e)
    if (strict) throw e
  }
}

function parseDocument(value) {
  return safeParseConfig(value)
}

function isNodeDataSourceUsageConfigured(usage) {
  const field = selectedField.value
  if (!field) return false
  if (field.dataSourceUsage === usage) {
    return Boolean(field.interfaceExtensionId)
  }
  const binding = parseDocument(field.dataSourceBindings)[usage]
  return Boolean(binding?.extensionId || resolveInterfaceExtensionId(
    binding,
    interfacesByUsage.value[usage] || []
  ))
}

function syncNodeDataSourceBinding(field, { throwOnError = false } = {}) {
  if (!field) return true
  const usage = String(field.dataSourceUsage || '').trim().toUpperCase()
  if (!usage) return true
  try {
    const bindings = { ...parseDocument(field.dataSourceBindings) }
    if (!field.interfaceExtensionId) {
      delete bindings[usage]
    } else {
      const existing = bindings[usage]
      const {
        serviceId: ignoredServiceId,
        operationCode: ignoredOperationCode,
        ...cleanExisting
      } = existing && typeof existing === 'object' && !Array.isArray(existing)
        ? existing
        : {}
      bindings[usage] = {
        ...cleanExisting,
        extensionId: field.interfaceExtensionId,
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
  const normalized = binding && typeof binding === 'object'
    ? binding
    : {}
  field.dataSourceUsage = usage
  field.interfaceExtensionId = resolveInterfaceExtensionId(
    normalized,
    interfacesByUsage.value[usage] || []
  )
  field.dataSourceInputMappingText = stringifyConfig(
    normalized.inputMapping || {}
  )
  field.dataSourceOutputMappingText = stringifyConfig(
    normalized.outputMapping || {}
  )
}

function selectNodeDataSourceUsage(usage) {
  const field = selectedField.value
  if (!field || field.dataSourceUsage === usage
      || !availableNodeDataSourceUsages.value.some(item => item.value === usage)) return
  if (!syncNodeDataSourceBinding(field)) return
  loadNodeDataSourceUsage(field, usage)
}

function clearSelectedNodeDataSourceBinding() {
  const field = selectedField.value
  if (!field?.dataSourceUsage) return
  const bindings = { ...parseDocument(field.dataSourceBindings) }
  delete bindings[field.dataSourceUsage]
  field.dataSourceBindings = bindings
  field.interfaceExtensionId = ''
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


function nodeToField(node, fieldMetadata) {
  const props = parseDocument(node.propsDocument)
  const rules = parseDocument(node.rulesDocument)
  const bindings = parseDocument(node.dataSourceBindingsDocument)
  const nodeType = normalizeFormNodeType(node.nodeType)
  const sourceField = mergeFormNodeFieldMetadata(
    entityFields.value,
    fieldMetadata,
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
  const fieldComponentName = resolveFormFieldExtensionName({
    ...node,
    props
  })
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
    componentName: fieldComponentName
      ? ''
      : (node.componentName || ''),
    componentVersion: fieldComponentName
      ? null
      : node.componentVersion,
    snapshotVersion: fieldComponentName
      ? null
      : node.snapshotVersion,
    fieldComponentName,
    fieldComponentVersion: fieldComponentName
      ? node.componentVersion
      : null,
    fieldComponentSnapshotVersion: fieldComponentName
      ? node.snapshotVersion
      : null,
    componentExtensionType: fieldComponentName
      ? FORM_FIELD_EXTENSION_TYPE
      : '',
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
      : (
          fieldComponentName
          || props.componentType
          || sourceField.componentType
          || node.nodeType.toLowerCase()
        ),
    placeholder: props.placeholder ?? sourceField.placeholder,
    defaultValue: props.defaultValue ?? sourceField.defaultValue,
    // 旧发布曾使用 props.span；读取后统一投影成 gridSpan，下一次保存自动规范化。
    gridSpan: props.gridSpan ?? props.span ?? sourceField.gridSpan ?? 24,
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
    interfaceExtensionId: firstBinding[1]?.extensionId || '',
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
  const effectiveRequired = isEntityFieldFixedRequired(field)
    ? 1
    : field.isRequired
  const selectedFieldComponent =
    field.fieldComponentName
    || (
      hasFormFieldComponent(field.componentType)
        ? field.componentType
        : ''
    )
  const fieldComponentDescriptor = selectedFieldComponent
    ? getFormFieldComponentDescriptor(selectedFieldComponent)
    : null
  const fieldComponentDefinition = selectedFieldComponent
    ? activeExtensionMap.value.get(
        `FIELD:${selectedFieldComponent}`
      )
    : null
  const persistedField = selectedFieldComponent
      ? {
        ...field,
        isRequired: effectiveRequired,
        componentType: getDefaultComponentType(field.fieldType),
        componentExtensionType: FORM_FIELD_EXTENSION_TYPE,
        componentName: selectedFieldComponent,
        componentVersion:
          field.fieldComponentVersion
          || fieldComponentDefinition?.version
          || fieldComponentDescriptor?.version
          || 1,
        snapshotVersion:
          field.fieldComponentSnapshotVersion
          || fieldComponentDefinition?.snapshotVersion
          || fieldComponentDescriptor?.snapshotVersion
          || 1
      }
    : {
        ...field,
        isRequired: effectiveRequired,
        componentExtensionType: undefined
      }
  return buildFormNodePayload(
    {
      ...persistedField,
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
    rendererMode: formRendererMode.value,
    formName: form.value.formName || '',
    description: form.value.description || '',
    layoutType: form.value.layoutType || 'vertical',
    isDefault: Boolean(form.value.isDefault),
    status: form.value.status,
    customComponent: form.value.customComponent || '',
    customComponentVersion: form.value.customComponentVersion || null,
    customComponentSnapshotVersion:
      form.value.customComponentSnapshotVersion || null,
    viewConfig: viewConfig.value
  })
}

function rememberFormBaseline() {
  formBaseline.value = formFingerprint()
}

/** 记录服务端已确认的按钮 key，避免未保存按钮先创建持久化事件绑定。 */
function rememberPersistedFormButtonKeys() {
  persistedFormButtonKeys.value = normalizeFormActionBar(
    viewConfig.value.actionBar
  ).customButtons.map(button => String(button.key || '').trim()).filter(Boolean)
}

function hasUnsavedLocalChanges() {
  if (formBaseline.value && formBaseline.value !== formFingerprint()) {
    return true
  }
  return hasUnsavedNodeChanges()
}

function hasUnsavedNodeChanges() {
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

    if ((!Array.isArray(field.fileItems) || field.fileItems.length === 0)
        && Array.isArray(compProps.fileItems)) {
      field.fileItems = cloneAttachmentItems(compProps.fileItems)
    }

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

    const attachmentItems = attachmentItemsForField(field)
    if (isAttachmentField(field) && attachmentItems.length > 0) {
      compProps.fileItems = cloneAttachmentItems(attachmentItems)
    } else {
      delete compProps.fileItems
      delete compProps.attachmentItemRequiredRules
    }

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
        listKey: field.refListKey || ''
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
async function loadFormFields({ strict = false } = {}) {
  if (!isEdit.value) {
    if (strict) throw new Error('当前表单尚未创建')
    return
  }

  try {
    const [fields, nodes] = await Promise.all([
      getFormFields(formId),
      getFormNodes(formId)
    ])
    formNodes.value = Array.isArray(nodes) ? nodes : []
    // 字段接口补充实体关系元数据，设计内容始终以节点树为准。
    const fieldsById = new Map((fields || []).map(field => [String(field.id), field]))
    formFields.value = formNodes.value.map(node =>
      nodeToField(node, fieldsById.get(String(node.id)))
    )
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
    if (strict) throw e
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
  // 实体元数据以 id 标识字段，绑定推断使用表单字段的 fieldId，需在添加入口转换。
  const initialBinding = resolveFormNodeBinding({ ...entityField, fieldId: entityField.id }, nodeType)
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
    // 显式保存审批默认只读，保证设计器、运行时与后端提交校验使用同一权限语义。
    extensionConfig: stringifyConfig({
      modes: {
        approve: { editable: false }
      }
    }),
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
  if (Array.isArray(entityField.fileItems)) {
    newField.fileItems = cloneAttachmentItems(entityField.fileItems)
  }

  formFields.value.push(newField)
  // 连续添加字段时仅选中新节点，避免自动展开属性抽屉打断画布操作。
  selectField(newField)
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

/**
 * 从按钮面板创建内嵌按钮时，同步建立一个稳定动作插槽。这里只修改同一份
 * 本地草稿；随后“保存全部草稿”会一起持久化按钮定义和节点树。
 */
function createActionSlotForButton({ buttonKey, buttonLabel } = {}) {
  if (isCustomRendererMode.value) {
    ElMessage.warning('整页自定义组件请使用其受控 formActionSlots 契约展示已有动作')
    return null
  }
  const normalizedButtonKey = String(buttonKey || 'custom_action')
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, '_')
  const baseKey = `action_slot_${normalizedButtonKey}`.slice(0, 64)
  let nodeKey = baseKey
  let suffix = 2
  const usedKeys = new Set(formFields.value.map(node =>
    String(node?.nodeKey || node?.id || '')
  ))
  while (usedKeys.has(nodeKey)) {
    const suffixText = `_${suffix++}`
    nodeKey = `${baseKey.slice(0, 64 - suffixText.length)}${suffixText}`
  }
  return addContainerNode('ACTION_SLOT', {
    nodeKey,
    label: `${buttonLabel || '自定义按钮'}操作区`,
    openProperties: false,
    notify: false
  })
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
  let stableId = options.nodeKey || `node_${nodeType.toLowerCase()}_${ts}`
  let collisionIndex = 2
  while (formFields.value.some(node =>
    String(node?.id) === String(stableId)
    || String(node?.nodeKey) === String(stableId)
  )) {
    stableId = `node_${nodeType.toLowerCase()}_${ts}_${collisionIndex++}`
  }
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
  if (options.openProperties !== false) {
    openFieldProperties(node)
  }
  if (nodeType === 'TAB' && !node.parentId) {
    ElMessage.info('请选择“所属 Tab 集合”后再保存当前 Tab 页')
  } else if (options.notify !== false) {
    ElMessage.success(`${nodeLabel}已添加`)
  }
  return node
}

// 选择字段
function selectField(field) {
  selectedField.value = field
  if (propertyDrawerVisible.value) {
    prepareFieldProperties(field)
  }
}

// 手动打开节点属性或新增容器节点时展开抽屉；新增实体字段仅选中。
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

// 保存事件配置
function handleSaveEvent(events) {
  if (!currentEventField.value) return
  writeFieldScripts(currentEventField.value, events)
  ElMessage.success('脚本已更新，请保存当前节点并发布表单')
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

function isAttachmentField(field) {
  return ['FILE', 'IMAGE'].includes(String(
    field?.fieldType || field?.componentType || ''
  ).toUpperCase())
}

function entityFieldForFormField(field) {
  if (!field) return null
  return entityFields.value.find(item =>
    (field.fieldId != null && String(item.id) === String(field.fieldId))
      || (field.fieldCode && item.fieldCode === field.fieldCode)
      || (field.bindingRef && item.fieldCode === field.bindingRef)
  ) || null
}

function isEntityFieldFixedRequired(field) {
  const entityField = entityFieldForFormField(field)
  return entityField?.isRequired === true || entityField?.isRequired === 1
}

function attachmentItemsForField(field) {
  if (!field || !isAttachmentField(field)) return []
  const entityField = entityFieldForFormField(field)
  const componentItems = parseComponentProps(field.componentProps).fileItems
  return [entityField?.fileItems, field.fileItems, componentItems]
    .find(items => Array.isArray(items) && items.length > 0) || []
}

function cloneAttachmentItems(items) {
  return (Array.isArray(items) ? items : []).map((item, index) => ({
    itemKey: item.itemKey,
    itemName: item.itemName || `附件项${index + 1}`,
    nameAliases: Array.isArray(item.nameAliases)
      ? [...item.nameAliases]
      : safeParseConfig(item.nameAliases, []),
    required: item.required === true
      || item.required === 1
      || item.required === '1',
    fileTypes: Array.isArray(item.fileTypes)
      ? [...item.fileTypes]
      : item.fileTypes,
    maxSize: item.maxSize,
    maxCount: item.maxCount,
    sortOrder: item.sortOrder ?? index
  }))
}

function updateValidationConfig(key, value) {
  if (!selectedField.value) return
  // 不适用项也会挂载；忽略控件初始化的规范化事件，避免浏览配置时改动节点。
  const capability = {
    minLength: 'length', maxLength: 'length',
    min: 'range', max: 'range', format: 'format', pattern: 'pattern'
  }[key]
  if (key === 'crossField' ? !canConfigureSelectedNodeCrossField.value
    : !canConfigureSelectedNodeValidation.value || !selectedValidationCapabilities.value[capability]) return
  selectedField.value.validationRules = stringifyConfig(
    normalizeFormFieldValidation(
      selectedField.value.fieldType,
      {
        ...selectedValidationConfig.value,
        [key]: value
      },
      selectedField.value.fieldCode
        || selectedField.value.bindingRef
        || selectedField.value.nodeKey
    )
  )
}

/**
 * 更新当前字段的表单级唯一规则。唯一规则嵌入 validationRules，因而会随
 * 当前表单草稿和发布快照保存，不会修改实体字段自身的 isUnique 配置。
 */
function persistSelectedUniqueness(rule) {
  if (!selectedField.value || !canConfigureSelectedNodeUniqueness.value) return
  const fieldCode = selectedField.value.fieldCode
    || selectedField.value.bindingRef
    || selectedField.value.nodeKey
  selectedField.value.validationRules = stringifyConfig(
    normalizeFormFieldValidation(
      selectedField.value.fieldType,
      {
        ...selectedValidationConfig.value,
        uniqueness: normalizeFormFieldUniqueness(rule, fieldCode)
      },
      fieldCode
    )
  )
}

function updateSelectedUniqueness(key, value) {
  persistSelectedUniqueness({
    ...selectedUniquenessConfig.value,
    [key]: value
  })
}

function updateSelectedUniquenessPrecheck(key, value) {
  persistSelectedUniqueness({
    ...selectedUniquenessConfig.value,
    precheck: {
      ...selectedUniquenessConfig.value.precheck,
      [key]: value
    }
  })
}

function persistSelectedUniquenessCondition() {
  persistSelectedUniqueness({
    ...selectedUniquenessConfig.value,
    condition: createFlowConditionConfig(
      selectedUniquenessConditionRoot.value
    )
  })
}

/**
 * 切换纯前端的正则测试区域；测试文本和结果不写入 validationRules，保存节点时不会持久化。
 */
function toggleRegexTest() {
  regexTestVisible.value = !regexTestVisible.value
}

function resetRegexTest() {
  regexTestVisible.value = false
  regexTestValue.value = ''
  regexTestTouched.value = false
}

/** 保存节点和整表草稿前检查配置；未完成的规则定位到对应页签并抛出可读错误。 */
function validateNodeValidationRules(field) {
  const stateError = getFieldStateConditionError(field)
  if (stateError) {
    selectedField.value = field
    activeNodeSettingsTab.value = 'basic'
    propertyDrawerVisible.value = true
    const label = field.fieldLabel || field.fieldName || field.fieldCode || '当前字段'
    throw new Error(`“${label}”${stateError}`)
  }
  const linkageError = getFieldLinkageDraftError(field)
  const attachmentError = getAttachmentConditionError(field, attachmentItemsForField(field))
  if (linkageError || attachmentError) {
    selectedField.value = field
    activeNodeSettingsTab.value = linkageError ? 'interaction' : 'rules'
    propertyDrawerVisible.value = true
    const label = field.fieldLabel || field.fieldName || field.fieldCode || '当前字段'
    throw new Error(`“${label}”${linkageError || attachmentError}`)
  }
  const config = safeParseConfig(field?.validationRules)
  const patternError = getRuntimeRegexPatternError(config.pattern)
  const label =
    field?.fieldLabel || field?.fieldName || field?.fieldCode || '当前字段'
  if (patternError) throw new Error(`“${label}”${patternError}`)
  const crossFieldErrors = validateCrossFieldConfiguration(config.crossField, field, crossFieldCandidateFields.value)
  if (crossFieldErrors.length) throw new Error(`“${label}”${crossFieldErrors[0]}`)
  if (!config.uniqueness) return
  const uniquenessValidation = validateFormFieldUniqueness(
    config.uniqueness,
    field?.fieldCode || field?.bindingRef || field?.nodeKey
  )
  if (!uniquenessValidation.valid) {
    throw new Error(`“${label}”${uniquenessValidation.errors[0]}`)
  }
}

function updateSelectedNodeConfig(key, value) {
  if (!selectedField.value) return
  // 显示不适用配置不代表允许写入，控件挂载时的默认值规范化也须遵守节点能力。
  if (key === 'showWordLimit' ? !isFieldNode.value || !canConfigureSelectedWordLimit.value
    : !selectedNodePropertySchema.value.configKeys.includes(key)) return
  selectedField.value.componentProps = stringifyConfig({
    ...selectedNodeConfig.value,
    [key]: value
  })
}

function handleCompatibleComponentChange() {
  if (!selectedField.value) return
  let descriptor = getFormFieldComponentDescriptor(
    selectedField.value.componentType
  )
  const fieldType = String(selectedField.value.fieldType || '').toUpperCase()
  const supported = descriptor?.supportedFieldTypes || []
  if (supported.length
      && !supported.map(type => String(type).toUpperCase()).includes(fieldType)) {
    ElMessage.warning('该组件与当前字段类型不兼容，已恢复默认组件')
    selectedField.value.componentType = getDefaultComponentType(fieldType)
    descriptor = getFormFieldComponentDescriptor(
      selectedField.value.componentType
    )
  }
  if (hasFormFieldComponent(selectedField.value.componentType)) {
    const extensionName = selectedField.value.componentType
    const definition = activeExtensionMap.value.get(
      `FIELD:${extensionName}`
    )
    selectedField.value.fieldComponentName = extensionName
    selectedField.value.fieldComponentVersion =
      definition?.version || descriptor?.version || 1
    selectedField.value.fieldComponentSnapshotVersion =
      definition?.snapshotVersion || descriptor?.snapshotVersion || 1
    selectedField.value.componentName = ''
    selectedField.value.componentVersion = null
    selectedField.value.snapshotVersion = null
  } else {
    selectedField.value.fieldComponentName = ''
    selectedField.value.fieldComponentVersion = null
    selectedField.value.fieldComponentSnapshotVersion = null
  }
  selectedField.value.componentProps = '{}'
  selectedField.value.validationRules = '{}'
  selectedField.value.dataSourceBindings = {}
  selectedField.value.interfaceExtensionId = ''
  selectedField.value.dataSourceInputMappingText = '{}'
  selectedField.value.dataSourceOutputMappingText = '{}'
}

function getModeAccessValue(mode, key) {
  const extension = safeParseConfig(selectedField.value?.extensionConfig)
  const value = extension?.modes?.[mode]?.[key]
  return value !== false
}

function updateModeAccess(mode, key, value) {
  if (!selectedField.value || !canConfigureSelectedNodeModeAccess.value) return
  if (key === 'editable' && modeOptions.find(item => item.value === mode)?.editable === false) return
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

async function loadDataSources({ strict = false } = {}) {
  if (!form.value.id) {
    interfaceExtensions.value = []
    interfacesByUsage.value = {}
    return
  }
  try {
    const usages = [...new Set([
      'FORM_INIT',
      ...formDataSourceUsages.map(item => item.value)
    ])]
    const rows = await Promise.all(usages.map(async usage => [
      usage,
      await uiExtensionApi.availableInterfaces({
        ownerType: 'FORM',
        ownerId: form.value.id,
        bindingCode: usage
      }).catch(error => {
        if (strict) throw error
        return []
      })
    ]))
    interfacesByUsage.value = Object.fromEntries(
      rows.map(([usage, items]) => [
        usage,
        normalizeInterfaceExtensions(items)
      ])
    )
    const unique = new Map()
    Object.values(interfacesByUsage.value).flat().forEach(item => {
      unique.set(item.extensionId, item)
    })
    interfaceExtensions.value = [...unique.values()]
    // 节点先于接口目录加载；目录就绪后再把历史绑定投影到当前用途的 extensionId。
    formFields.value.forEach(field => {
      if (field.dataSourceUsage) {
        loadNodeDataSourceUsage(field, field.dataSourceUsage)
      }
    })
  } catch (error) {
    console.error('加载扩展接口失败:', error)
    interfaceExtensions.value = []
    interfacesByUsage.value = {}
    if (strict) throw error
  }
}

async function loadComponentTemplates({ strict = false } = {}) {
  try {
    componentTemplates.value = await uiComponentTemplateApi.list()
  } catch (error) {
    componentTemplates.value = []
    if (strict) throw error
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

async function loadDiff({ strict = false } = {}) {
  diffLoadSucceeded.value = false
  if (!form.value.id) {
    diffInfo.value = { changed: true, changedSections: ['form', 'nodes'] }
    return
  }
  try {
    diffInfo.value = await getFormDiff(form.value.id)
    diffLoadSucceeded.value = true
  } catch (error) {
    diffInfo.value = { changed: true, changedSections: [] }
    if (strict) throw error
  }
}

function isRevisionConflict(error) {
  return error?.status === 409
    || error?.errorCode === 'CONFIG_REVISION_CONFLICT'
}

function handleRevisionConflict(error, field) {
  if (isRevisionConflict(error)) {
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

async function refreshDraftStateAfterSaveFailure({ preserveNodes = false } = {}) {
  if (!form.value.id) return
  try {
    await loadFormInfo()
    if (!preserveNodes) {
      await loadFormFields()
    }
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
  rememberPersistedFormButtonKeys()
  formActionPersistenceRevision.value += 1
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
      if (!binding || typeof binding !== 'object') {
        throw new Error(`${label}${usage}绑定格式无效`)
      }
      if (!binding.extensionId) {
        throw new Error(`${label}${usage}必须配置扩展接口`)
      }
      parseJsonConfig(binding.inputMapping || {}, {
        fieldName: `${label}${usage}输入映射`
      })
      parseJsonConfig(binding.outputMapping || {}, {
        fieldName: `${label}${usage}输出映射`
      })
    }
  )
}

/**
 * 保存与发布共用同一套按钮校验，并始终从服务端重新读取事件绑定，避免面板
 * 缓存或并发修改让启用按钮在没有点击执行链时进入草稿/发布预检。
 */
async function validateFormActionsForPersistence() {
  let eventBindings = []
  const enabledCustomButtons = normalizeFormActionBar(
    viewConfig.value.actionBar
  ).customButtons.filter(button => button.enabled !== false)
  if (!form.value.id && enabledCustomButtons.length) {
    ElMessage.warning('新表单的自定义按钮请先停用；保存表单后配置事件链，再启用按钮')
    openFormSettings('actions')
    return false
  }
  if (form.value.id) {
    try {
      const rows = await uiEventBindingApi.list('FORM', String(form.value.id))
      eventBindings = Array.isArray(rows) ? rows : []
    } catch (error) {
      console.error('校验表单按钮事件绑定失败:', error)
      ElMessage.error('暂时无法校验按钮事件绑定，请稍后重试')
      return false
    }
  }
  const result = validateFormActionConfiguration({
    actionBar: viewConfig.value.actionBar,
    nodes: formFields.value,
    eventBindings,
    requireEventBindings: true
  })
  if (result.valid) return true

  const messages = result.errors.slice(0, 3).map(item => item.message)
  const remaining = result.errors.length - messages.length
  ElMessage.warning({
    message: `${messages.join('；')}${remaining > 0 ? `；另有 ${remaining} 项` : ''}`,
    duration: 6500
  })
  openFormSettings('actions')
  return false
}

async function handlePublish() {
  if (!form.value.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  if (!validateRendererForPublish()) return
  const hasRelevantUnsavedChanges =
    (formBaseline.value && formBaseline.value !== formFingerprint())
    || (!isCustomRendererMode.value && hasUnsavedNodeChanges())
  if (hasRelevantUnsavedChanges) {
    ElMessage.warning('当前渲染配置仍有未保存修改，请先保存草稿后再发布')
    return
  }
  if (!await validateFormActionsForPersistence()) return
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

  if (!validateRendererForSave()) return
  if (!await validateFormActionsForPersistence()) return

  const persistNodes = shouldSaveCurrentFormNodes()
  let orderedFields = []
  if (persistNodes) {
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
        // 渲染方式和组件版本由外层编辑面板保存，设计页只提交自身维护的配置。
        viewConfig: viewConfig.value
      })
      form.value = { ...form.value, ...updated }
      rememberPersistedFormButtonKeys()
      formActionPersistenceRevision.value += 1
      currentFormId = form.value.id
      draftChanged = true
    } else {
      currentFormId = await ensureFormMetadata()
      draftChanged = true
    }

    if (persistNodes) {
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
    }
    await loadFormInfo()
    if (persistNodes) {
      await loadFormFields()
    }
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
      await refreshDraftStateAfterSaveFailure({ preserveNodes: !persistNodes })
    }
    handleRevisionConflict(e)
  } finally {
    saving.value = false
  }
}

/**
 * 重新获取设计器依赖的完整草稿状态；撤销后不能只合并根表单，否则节点 revision 和本地指纹会失真。
 */
async function reloadFormDesignerData({ resetInteraction = false } = {}) {
  initializing.value = true
  const strict = resetInteraction
  let completed = false
  diffLoadSucceeded.value = false
  if (resetInteraction) {
    selectedField.value = null
    currentEventField.value = null
    propertyDrawerVisible.value = false
    showEventConfig.value = false
    formBaseline.value = ''
    nodeBaselines.value = new Map()
    // 撤销或冲突后不再保留旧画布，避免重载失败时将旧内容再次写回。
    formFields.value = []
    formNodes.value = []
  }
  try {
    await loadFormInfo({ strict })
    await loadEntityInfo({ strict })
    await loadEntityFields({ strict })
    await loadFormFields({ strict })
    await loadDataSources({ strict })
    await loadComponentTemplates({ strict })
    await loadExtensionDefinitions({ strict })
    await loadDiff({ strict })
    completed = true
  } finally {
    // 严格重载失败后保持阻塞，只允许用户刷新页面，不让不完整快照继续编辑。
    if (!strict || completed) initializing.value = false
  }
}

async function handleDiscardDraft() {
  if (discardDraftLoading.value || !canDiscardDraft.value) return
  try {
    await ElMessageBox.confirm(
      '撤销后将恢复到当前发布版本。自当前发布版本以来所有已保存但未发布的修改，以及当前页面尚未保存的编辑，都会被覆盖且不可恢复。确定继续吗？',
      '撤销未发布修改',
      {
        type: 'warning',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  if (discardDraftLoading.value || !canDiscardDraft.value) return

  discardDraftLoading.value = true
  let discardCommitted = false
  try {
    const preconditions = buildUiConfigDraftDiscardRequest({
      revision: form.value.revision,
      diffLoadSucceeded: diffLoadSucceeded.value,
      diff: diffInfo.value,
      serverCanDiscardDraft: diffInfo.value.canDiscardDraft === true,
      activeReleaseId: form.value.activeReleaseId
    })
    await uiConfigDraftApi.discard('FORM', form.value.id, preconditions)
    discardCommitted = true
    await reloadFormDesignerData({ resetInteraction: true })
    if (diffInfo.value.changed) {
      ElMessage.warning('本地草稿已撤销，但当前仍存在依赖版本差异，请检查继承事件或引用配置')
      return
    }
    ElMessage.success('已撤销未发布修改，并恢复到当前发布版本')
  } catch (error) {
    if (discardCommitted) {
      ElMessage.warning('撤销已完成，但页面重新加载失败，请手动刷新')
      return
    }
    if (isUiConfigDraftDiscardConflict(error)) {
      try {
        await reloadFormDesignerData({ resetInteraction: true })
      } catch {
        ElMessage.warning('配置状态已变化，但页面重新加载失败，请手动刷新')
        return
      }
      ElMessage.warning('草稿或发布状态已变化，已重新加载最新配置，请重新确认')
      return
    }
    ElMessage.error(error?.message || '撤销未发布修改失败')
  } finally {
    discardDraftLoading.value = false
  }
}

onMounted(async () => {
  await reloadFormDesignerData()
  openLinkedFormSettings()
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

.related-content-entry {
  display: inline-flex;
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

.regex-validation-editor {
  width: 100%;
}

.regex-pattern-row,
.regex-test-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.regex-test-row {
  margin-top: 8px;
}

.regex-pattern-row :deep(.el-input),
.regex-test-row :deep(.el-input) {
  flex: 1;
  min-width: 0;
}

.regex-test-result {
  flex: 0 0 20px;
  font-size: 20px;
}

.regex-test-result.is-match {
  color: var(--el-color-success);
}

.regex-test-result.is-mismatch {
  color: var(--el-color-danger);
}

.regex-test-hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
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

.empty-tip {
  padding: 80px 0;
}

.node-layout-settings {
  margin-top: 12px;
}

.property-form {
  padding: 16px;
}

/* 抽屉挂载在 body 下；标题下方的外边距和内容顶部内边距分别减半。 */
:global(.node-property-drawer .el-drawer__header) {
  margin-bottom: 16px;
}

:global(.node-property-drawer .el-drawer__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-top: calc(var(--el-drawer-padding-primary, 20px) / 2);
  padding-bottom: 8px;
  overflow: hidden;
}

/* 内容填满标题和固定操作栏之间的剩余高度，避免预扣固定高度后留下大块空白。 */
.node-property-scrollbar {
  flex: 1;
  min-height: 0;
}

/* 滚动到内容边界时不继续带动外层页面，标题和页签始终保持原位。 */
.node-property-scrollbar :deep(.el-scrollbar__wrap) {
  overscroll-behavior-y: contain;
}

.node-property-heading {
  display: flex;
  flex: 1;
  min-width: 0;
  margin-right: 16px;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.node-property-heading-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.field-script-overview,
.field-script-overview__events {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.field-script-overview {
  gap: 12px;
  padding-bottom: 10px;
}

.field-script-overview__events {
  min-width: 0;
}

.field-script-overview__events :deep(.el-tag) {
  max-width: 100%;
  height: auto;
  line-height: 22px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.field-script-overview__empty {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.node-settings-tabs {
  position: sticky;
  top: 0;
  z-index: 1;
  flex-shrink: 0;
  padding: 0 12px;
  background: var(--el-bg-color);
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
  align-items: center;
  flex-flow: row nowrap;
  gap: 24px;
}

.checkbox-group :deep(.el-checkbox) {
  margin-right: 0;
}

.property-subheading {
  margin: 14px 0 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.interaction-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.uniqueness-scope-tip {
  margin-bottom: 14px;
}

.uniqueness-condition-editor,
.uniqueness-precheck-editor {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.uniqueness-subtitle {
  margin-bottom: 6px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.uniqueness-condition-editor .form-tip {
  margin-bottom: 12px;
}

.uniqueness-unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
