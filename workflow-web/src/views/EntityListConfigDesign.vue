<template>
  <div
    v-loading="discardDraftLoading"
    class="entity-list-config-design"
  >
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <span>{{ configInfo.listName || '新建列表' }}<template v-if="configInfo.listKey">（{{ configInfo.listKey }}）</template></span>
        <el-tag size="small" type="info">{{ entityName }}</el-tag>
        <el-tag :type="draftStatus.type" effect="plain">
          {{ draftStatus.label }}
        </el-tag>
        <el-button
          v-if="canDiscardDraft"
          link
          type="danger"
          :loading="discardDraftLoading"
          :disabled="discardDraftLoading || savingAll || pageLoading"
          @click="handleDiscardDraft"
        >
          撤销
        </el-button>
        <el-tooltip
          v-if="isDirty"
          placement="bottom-start"
          :show-after="200"
          :popper-style="{ maxWidth: '360px' }"
        >
          <template #content>
            <div class="unsaved-items-tooltip">
              <div class="unsaved-items-tooltip__title">以下内容尚未保存</div>
              <div
                v-for="item in unsavedItems"
                :key="item.key"
                class="unsaved-items-tooltip__item"
              >
                {{ item.label }}
              </div>
            </div>
          </template>
          <el-tag class="unsaved-status-tag" type="danger" effect="plain" tabindex="0">
            {{ unsavedSummary }}
          </el-tag>
        </el-tooltip>
      </div>
      <div class="header-actions">
        <el-button
          link
          :loading="runtimeCodeLoading"
          :disabled="pageLoading"
          @click="openRuntimeCode"
        >
          <el-icon><Document /></el-icon>代码
        </el-button>
        <el-button link :disabled="pageLoading" @click="showReleaseHistory">版本</el-button>
        <el-button
          link
          :disabled="!entityCode || !configInfo.listKey || pageLoading"
          @click="openPreview"
        >
          预览
        </el-button>
        <el-button
          v-if="!isSystemEntity"
          :disabled="!configInfo.id || discardDraftLoading || pageLoading"
          @click="openListEventBindings"
        >
          事件绑定
        </el-button>
        <el-badge
          v-if="!isSystemEntity"
          :value="relatedContentCount"
          :hidden="relatedContentCount === 0"
          class="related-content-entry"
        >
          <el-button
            :disabled="!configInfo.id || discardDraftLoading || pageLoading"
            @click="openRelatedContent"
          >
            <el-icon><Connection /></el-icon>关联内容
          </el-button>
        </el-badge>
        <el-button
          :loading="savingAll"
          :disabled="discardDraftLoading || pageLoading"
          type="primary"
          @click="saveAll"
        >
          保存全部
        </el-button>
        <el-button
          type="success"
          plain
          :disabled="discardDraftLoading || pageLoading"
          @click="handlePublish"
        >发布生效</el-button>
      </div>
    </div>
    <el-alert
      v-if="isSystemEntity"
      title="平台系统表结构只读。列表仅配置查询条件、显示列、排序、分页、格式化、选择模式和查看操作。"
      type="warning"
      :closable="false"
      show-icon
      class="system-config-alert"
    />
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      class="page-error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="loadData">重新加载</el-button>
      </template>
    </el-alert>
    <div
      v-loading="pageLoading || discardDraftLoading"
      class="design-container"
    >
      <div ref="configPanelRef" class="config-panel">
        <el-card shadow="never" class="config-card">
          <el-tabs v-model="activeConfigTab" class="config-tabs">
            <el-tab-pane label="字段配置" name="fields">
              <div class="field-toolbar">
                <el-alert
                  title="查询字段可以不显示在列表；虚拟列必须选择支持虚拟字段的数据源。"
                  type="info"
                  :closable="false"
                  show-icon
                />
                <el-button v-if="!isSystemEntity" type="primary" plain @click="addVirtualField">
                  <el-icon><Plus /></el-icon>添加虚拟列
                </el-button>
              </div>
              <el-table
                ref="fieldTableRef"
                :data="fieldConfigList"
                row-key="fieldId"
                class="field-config-table"
                size="small"
                border
              >
                <el-table-column label="排序" width="48" align="center">
                  <template #default>
                    <el-icon class="drag-handle"><Rank /></el-icon>
                  </template>
                </el-table-column>
                <el-table-column label="字段名称" width="148">
                  <template #default="{ row }">
                    <el-input v-model="row.fieldName" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="字段编码" width="168">
                  <template #default="{ row }">
                    <el-input v-model="row.fieldCode" size="small" :disabled="!isVirtualField(row)" />
                  </template>
                </el-table-column>
                <el-table-column label="用途" width="144">
                  <template #default="{ row }">
                    <div class="field-purpose-controls">
                      <el-checkbox v-model="row.showInList">列表</el-checkbox>
                      <el-checkbox v-model="row.isQuery" :disabled="!supportsQuery(row)">查询</el-checkbox>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="列宽（px）" width="140">
                  <template #header>
                    <ConfigHelpLabel
                      label="列宽（px）"
                      help-key="entityList.columnWidth"
                    />
                  </template>
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.width"
                      :aria-label="`${row.fieldName}列宽（px）`"
                      :min="0"
                      :max="500"
                      :disabled="!row.showInList"
                      size="small"
                      controls-position="right"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="当前配置" min-width="320">
                  <template #default="{ row }">
                    <span class="field-config-summary" :title="fieldConfigSummary(row)">
                      {{ fieldConfigSummary(row) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="138" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openFieldConfig(row)">设置</el-button>
                    <el-button
                      link
                      type="success"
                      :loading="row._saving"
                      @click="saveCurrentField(row)"
                    >保存</el-button>
                    <el-button
                      v-if="isVirtualField(row)"
                      link
                      type="danger"
                      :icon="Delete"
                      aria-label="删除虚拟列"
                      title="删除虚拟列"
                      @click="removeVirtualField(row)"
                    />
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="列表设置" name="view">
              <div class="field-toolbar">
                <el-alert
                  title="列表设置可独立保存；列和按钮的修改不会被一并覆盖。"
                  type="info"
                  :closable="false"
                  show-icon
                />
                <el-button
                  type="success"
                  plain
                  @click="saveListMetadata"
                >
                  保存列表设置
                </el-button>
              </div>
              <el-form label-width="120px" size="small" class="view-config-form">
                <SettingsSection
                  title="显示配置"
                  description="查询区域、表格样式和分页设置保存后可在实际列表页面确认"
                  :default-expanded="true"
                  class="display-config-section"
                  primary
                >
                  <div class="display-config-grid">
                    <el-form-item label="收起时显示条件数">
                      <el-input-number v-model="viewConfig.search.defaultVisibleCount" :min="1" :max="20" />
                    </el-form-item>
                    <el-form-item label="启用查询区折叠">
                      <el-switch v-model="viewConfig.search.collapsible" />
                    </el-form-item>
                    <el-form-item label="查询区标签宽度">
                      <el-input-number v-model="viewConfig.search.labelWidth" :min="60" :max="240" />
                      <span class="unit-text">px</span>
                    </el-form-item>
                    <el-form-item label="表格样式">
                      <el-checkbox v-model="viewConfig.table.stripe">斑马纹</el-checkbox>
                      <el-checkbox v-model="viewConfig.table.border">边框</el-checkbox>
                      <el-checkbox v-model="viewConfig.table.showIndex">序号列</el-checkbox>
                    </el-form-item>
                    <el-form-item label="表格尺寸">
                      <el-radio-group v-model="viewConfig.table.size">
                        <el-radio-button value="small">紧凑</el-radio-button>
                        <el-radio-button value="default">默认</el-radio-button>
                        <el-radio-button value="large">宽松</el-radio-button>
                      </el-radio-group>
                    </el-form-item>
                    <el-form-item label="默认排序字段" for="">
                      <template #label>
                        <ConfigHelpLabel
                          label="默认排序字段"
                          help-key="entityList.defaultSort"
                        />
                      </template>
                      <div class="default-sort-controls">
                        <el-select
                          v-model="viewConfig.table.defaultSortField"
                          class="default-sort-field"
                          clearable
                          filterable
                          aria-label="默认排序字段"
                          placeholder="使用平台默认顺序"
                        >
                          <el-option
                            v-for="field in entityFields"
                            :key="field.fieldCode"
                            :label="field.fieldName || field.fieldCode"
                            :value="field.fieldCode"
                          />
                        </el-select>
                        <el-radio-group
                          v-if="viewConfig.table.defaultSortField"
                          v-model="viewConfig.table.defaultSortDirection"
                          class="default-sort-direction"
                          aria-label="默认排序方向"
                        >
                          <el-radio-button value="ASC">升序</el-radio-button>
                          <el-radio-button value="DESC">降序</el-radio-button>
                        </el-radio-group>
                      </div>
                    </el-form-item>
                    <el-form-item label="默认每页">
                      <el-select v-model="viewConfig.pagination.pageSize" style="width: 160px">
                        <el-option
                          v-for="size in viewConfig.pagination.pageSizes"
                          :key="size"
                          :label="`${size} 条`"
                          :value="size"
                        />
                      </el-select>
                    </el-form-item>
                  </div>
                </SettingsSection>
                <SettingsSection
                  class="access-scope-section"
                  title="访问范围"
                  description="配置访问权限、数据范围规则，以及始终生效的固定查询条件"
                  :default-expanded="true"
                >
                  <template #summary>
                    数据范围与固定条件共同生效
                  </template>
                  <el-form-item label="数据范围模式">
                    <template #label>
                      <ConfigHelpLabel
                        label="数据范围模式"
                        help-key="entityList.dataScopeMode"
                      />
                    </template>
                    <el-select v-model="configInfo.dataScopeMode" style="width: 420px" disabled>
                      <el-option label="仅使用本列表绑定的规则" value="INHERIT" />
                    </el-select>
                    <div class="form-tip">数据范围只认本列表绑定的规则，不再继承实体默认范围。</div>
                  </el-form-item>
                  <el-form-item v-if="!isSystemEntity" label="未绑定允许规则时" class="view-config-item--full">
                    <el-select v-model="scopeDefault.unboundPolicy" style="width: 420px">
                      <el-option label="拒绝全部数据（推荐）" value="DENY_ALL" />
                      <el-option label="仅本人创建或提交" value="PERSONAL" />
                      <el-option label="全部数据（高风险，需确认）" value="EXPLICIT_ALL" />
                    </el-select>
                    <el-tag
                      v-if="scopeDefault.enforcementMode === 'OBSERVE'"
                      type="danger"
                      effect="dark"
                      style="margin-left: 8px"
                    >存量观察期</el-tag>
                    <el-button
                      v-if="scopeDefault.unboundPolicy === 'EXPLICIT_ALL' && !scopeDefault.confirmed"
                      type="danger"
                      plain
                      style="margin-left: 8px"
                      @click="confirmExplicitAll"
                    >确认继续全量可见</el-button>
                    <div class="form-tip">该策略仅在本列表没有任何启用的 ALLOW 绑定时生效。</div>
                  </el-form-item>
                  <el-form-item v-if="!isSystemEntity" label="绑定数据规则" class="view-config-item--full">
                    <el-select
                      v-model="boundPolicyIds"
                      multiple
                      collapse-tags
                      collapse-tags-tooltip
                      placeholder="不选时执行上方安全默认策略"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="rule in scopePolicies"
                        :key="rule.policyId"
                        :label="`${rule.ruleName} · ${rule.filterType || rule.presetCode}`"
                        :value="rule.policyId"
                      />
                    </el-select>
                    <el-alert
                      v-if="!boundPolicyIds.length"
                      :type="scopeDefaultAlertType"
                      :closable="false"
                      style="margin-top: 8px"
                      :title="scopeDefaultAlertTitle"
                    />
                  </el-form-item>
                  <el-form-item label="访问权限码">
                    <el-input
                      v-model="configInfo.accessPermissionCode"
                      placeholder="留空继承 entity:{code}:list"
                      style="width: 420px"
                    />
                  </el-form-item>
                  <el-form-item label="固定条件" class="view-config-item--full">
                    <template #label>
                      <ConfigHelpLabel label="固定条件" help-key="entityList.fixedFilters" />
                    </template>
                    <ListFixedFilterEditor
                      v-model="fixedFilterRows"
                      :fields="entityFields"
                      :system-entity="isSystemEntity"
                    />
                  </el-form-item>
                </SettingsSection>
                <SettingsSection
                  title="选择行为"
                  class="selection-behavior-section"
                  description="可选择时允许勾选多条；执行所需条数由各工具栏按钮决定"
                  :default-expanded="true"
                >
                  <template #summary>
                    {{ configInfo.selectionMode === 'NONE' ? '不可选择' : '可选择' }}
                  </template>
                  <el-form-item label="选择模式">
                    <template #label>
                      <ConfigHelpLabel
                        label="选择模式"
                        help-key="entityList.selectionMode"
                      />
                    </template>
                    <el-radio-group v-model="configInfo.selectionMode">
                      <el-radio-button value="NONE" :disabled="toolbarRequiresSelection">不可选择</el-radio-button>
                      <el-radio-button value="MULTIPLE">可选择</el-radio-button>
                    </el-radio-group>
                    <div v-if="toolbarRequiresSelection" class="field-help">已启用的工具栏按钮需要选择数据，已自动开启可选择。</div>
                  </el-form-item>
                  <el-form-item v-if="configInfo.selectionMode !== 'NONE'" label="返回值字段">
                    <el-select v-model="configInfo.selectionValueField" filterable style="width: 420px">
                      <el-option label="主键 ID" value="id" />
                      <el-option
                        v-for="field in entityFields"
                        :key="field.fieldCode"
                        :label="`${field.fieldName} (${field.fieldCode})`"
                        :value="field.fieldCode"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item
                    v-if="configInfo.selectionMode !== 'NONE'"
                    class="view-config-item--full"
                    label="附加返回字段"
                  >
                    <template #label>
                      <ConfigHelpLabel
                        label="附加返回字段"
                        help-key="entityList.selectionReturnMappings"
                      />
                    </template>
                    <SelectionReturnMappingEditor
                      v-model="configInfo.selectionReturnMappingsText"
                      :fields="entityFields"
                      :system-entity="isSystemEntity"
                    />
                  </el-form-item>
                </SettingsSection>
                <SettingsSection
                  v-if="!isSystemEntity"
                  title="扩展渲染"
                  description="仅在默认动态列表无法满足展示需求时配置"
                >
                  <template #summary>
                    {{ configInfo.customComponent ? '已启用自定义组件' : '默认动态列表' }}
                  </template>
                  <el-form-item label="自定义列表组件">
                    <ExtensionCapabilityPicker
                      v-model="configInfo.customComponent"
                      placeholder="留空使用默认动态列表"
                      capability-type="UI_LIST"
                      :local-options="customListOptions"
                      :current-option="selectedCustomListCatalogOption"
                      style="width: 420px"
                    />
                  </el-form-item>
                  <el-form-item
                    v-if="selectedCustomListSchema.length"
                    label="组件参数"
                    class="view-config-item--full"
                  >
                    <ConfigSchemaEditor
                      v-model="viewConfig.customComponentProps"
                      :schema="selectedCustomListSchema"
                    />
                  </el-form-item>
                </SettingsSection>
              </el-form>
            </el-tab-pane>
            <el-tab-pane v-if="!isSystemEntity" label="工具栏按钮" name="toolbar">
              <ListButtonConfigPanel
                type="toolbar"
                v-model="toolbarButtons"
                :related-contents="relatedContents"
                @configure-related-content="openRelatedContent"
                :entityCode="entityCode"
                :entity-id="entityId"
                :entityFields="entityFields"
                :owner-id="configInfo.id || configId"
                @events-changed="handleEventBindingsChanged"
                @save="saveListAction($event, 'TOOLBAR')"
                @reorder="reorderListAction($event, 'TOOLBAR')"
                @remove="removeListAction($event, 'TOOLBAR')"
              />
            </el-tab-pane>
            <el-tab-pane v-if="!isSystemEntity" label="操作列按钮" name="rowActions">
              <ListButtonConfigPanel
                type="row"
                v-model="rowActionButtons"
                :mapping-fields="fieldConfigList"
                :related-contents="relatedContents"
                @configure-related-content="openRelatedContent"
                :entityCode="entityCode"
                :entity-id="entityId"
                :entityFields="entityFields"
                :owner-id="configInfo.id || configId"
                @events-changed="handleEventBindingsChanged"
                @save="saveListAction($event, 'ROW')"
                @reorder="reorderListAction($event, 'ROW')"
                @remove="removeListAction($event, 'ROW')"
              />
            </el-tab-pane>
            <el-tab-pane label="输入参数" name="input-parameters">
              <PageInputParameterSettings v-model="viewConfig" kind="LIST" :fields="fieldConfigList" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </div>
    </div>
    <el-dialog
      v-model="previewDialogVisible"
      title="列表预览"
      width="92%"
      top="5vh"
      destroy-on-close
    >
      <div class="preview-content">
        <div class="preview-toolbar">
          <el-text type="info">
            使用当前页面的字段与显示配置，数据通过列表运行时接口加载。
          </el-text>
          <el-radio-group v-model="previewViewport" size="small">
            <el-radio-button value="desktop">桌面</el-radio-button>
            <el-radio-button value="tablet">平板</el-radio-button>
          </el-radio-group>
        </div>
        <div class="preview-viewport" :class="`is-${previewViewport}`">
          <el-alert
            v-if="previewError"
            :title="previewError"
            type="error"
            :closable="false"
            show-icon
            class="preview-error"
          >
            <template #default>
              <el-button size="small" type="danger" plain @click="loadPreviewData">
                重试预览
              </el-button>
            </template>
          </el-alert>
          <EntityDataSearchForm
            v-if="previewQueryFields.length > 0"
            v-model:form="previewQueryForm"
            :fields="previewQueryFields"
            :use-list-config="true"
            :view-config="viewConfig"
            @search="handlePreviewSearch"
            @reset="handlePreviewReset"
          />
          <el-table
            v-loading="previewLoading"
            :data="previewDataList"
            :stripe="viewConfig.table.stripe !== false"
            :border="viewConfig.table.border === true"
            :size="viewConfig.table.size || 'small'"
          >
            <el-table-column
              v-if="viewConfig.table.showIndex !== false"
              type="index"
              width="50"
            />
            <el-table-column
              v-for="field in previewListFields"
              :key="field.fieldCode"
              :label="field.fieldName"
              :width="field.width > 0 ? field.width : undefined"
              :align="field.align"
              :fixed="safeParseConfig(field.columnConfig).fixed || undefined"
              :min-width="field.width > 0
                ? undefined
                : (safeParseConfig(field.columnConfig).minWidth || 100)"
              :show-overflow-tooltip="
                safeParseConfig(field.columnConfig).showOverflowTooltip !== false
              "
            >
              <template #default="{ row }">
                <ListQuickCopyCell
                  :enabled="safeParseConfig(field.columnConfig).quickCopy === true"
                  :value="row.data?.[field.fieldCode] ?? row[field.fieldCode] ?? '-'"
                  :field-name="field.fieldName"
                >
                  <ListCellRenderer
                    v-if="
                      field.renderComponent
                      || (field.dataSourceType && field.dataSourceType !== 'ENTITY_FIELD')
                    "
                    :row="row"
                    :field="field"
                  />
                  <span v-else>
                    {{ row.data?.[field.fieldCode] ?? row[field.fieldCode] ?? '-' }}
                  </span>
                </ListQuickCopyCell>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="previewTotal > 0" class="preview-pagination">
            <el-pagination
              v-model:current-page="previewPageNum"
              v-model:page-size="previewPageSize"
              :total="previewTotal"
              :page-sizes="viewConfig.pagination.pageSizes"
              layout="total, sizes, prev, pager, next"
              small
              @size-change="loadPreviewData"
              @current-change="loadPreviewData"
            />
          </div>
          <el-empty
            v-if="!previewLoading && !previewError && previewDataList.length === 0"
            description="当前条件下暂无预览数据"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="previewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="fieldConfigDialogVisible"
      :title="`字段高级配置：${editingField?.fieldName || ''}`"
      width="720px"
      destroy-on-close
    >
      <el-tabs v-if="editingField" v-model="activeFieldConfigTab" class="field-config-tabs">
        <el-tab-pane label="常用" name="common">
          <SettingsSection
            title="查询项"
            description="配置字段作为查询条件时的输入方式"
            :collapsible="false"
            primary
          >
            <el-form label-width="110px" size="small">
              <el-form-item label="查询组件">
                <el-select v-model="editingQueryConfig.componentType" clearable placeholder="自动匹配字段类型" style="width: 100%">
                  <el-option
                    v-for="option in queryComponentOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="查询方式">
                <template #label>
                  <ConfigHelpLabel
                    label="查询方式"
                    help-key="entityList.queryType"
                  />
                </template>
                <el-select
                  v-model="editingField.queryType"
                  :disabled="!editingField.isQuery"
                  style="width: 100%"
                >
                  <el-option
                    v-for="option in availableQueryTypeOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="占位提示">
                <el-input v-model="editingQueryConfig.placeholder" />
              </el-form-item>
              <el-form-item label="默认值">
                <el-input v-model="editingQueryConfig.defaultValue" />
              </el-form-item>
            </el-form>
          </SettingsSection>
          <SettingsSection
            title="列展示"
            description="配置列宽和对齐方式等常用展示项"
            :collapsible="false"
          >
            <el-form label-width="110px" size="small">
              <el-form-item label="列宽" for="">
                <template #label>
                  <ConfigHelpLabel
                    label="列宽"
                    help-key="entityList.columnWidth"
                  />
                </template>
                <el-input-number
                  v-model="editingField.width"
                  aria-label="列宽"
                  :min="0"
                  :max="500"
                  :disabled="!editingField.showInList"
                />
                <span class="unit-text">0 表示自动</span>
              </el-form-item>
              <el-form-item label="对齐">
                <el-select
                  v-model="editingField.align"
                  :disabled="!editingField.showInList"
                  style="width: 100%"
                >
                  <el-option label="左对齐" value="left" />
                  <el-option label="居中" value="center" />
                  <el-option label="右对齐" value="right" />
                </el-select>
              </el-form-item>
              <el-form-item label="快捷复制">
                <el-switch
                  v-model="editingColumnConfig.quickCopy"
                  :disabled="!editingField.showInList"
                  inline-prompt
                  active-text="是"
                  inactive-text="否"
                />
              </el-form-item>
            </el-form>
          </SettingsSection>
          <SettingsSection
            title="高级列布局"
            description="仅在需要冻结列或精细控制宽度时配置"
            :default-expanded="false"
          >
            <el-form label-width="110px" size="small">
              <el-form-item label="固定位置">
                <el-select v-model="editingColumnConfig.fixed" clearable placeholder="不固定" style="width: 100%">
                  <el-option label="左侧" value="left" />
                  <el-option label="右侧" value="right" />
                </el-select>
              </el-form-item>
              <el-form-item label="最小宽度" for="">
                <template #label>
                  <ConfigHelpLabel
                    label="最小宽度"
                    help-key="entityList.columnMinWidth"
                  />
                </template>
                <el-input-number v-model="editingColumnConfig.minWidth" aria-label="最小宽度" :min="60" :max="1000" />
              </el-form-item>
              <el-form-item label="溢出提示">
                <el-switch v-model="editingColumnConfig.showOverflowTooltip" />
              </el-form-item>
            </el-form>
          </SettingsSection>
        </el-tab-pane>
        <el-tab-pane label="数据与显示" name="data-render">
          <SettingsSection
            title="数据与显示"
            description="集中配置字段取值来源、单元格组件和显示参数"
            :collapsible="false"
            primary
          >
            <div class="field-config-subsection">
              <div class="field-config-subsection__title">数据来源</div>
              <el-alert
                :title="selectedDataSourceOption?.description || '实体字段无需额外配置'"
                type="info"
                :closable="false"
                style="margin-bottom: 12px"
              />
              <ConfigSchemaEditor
                v-model="editingDataSourceConfig"
                :schema="selectedDataSourceOption?.configSchema || []"
              />
              <el-form label-width="110px" size="small">
                <el-form-item label="字段数据源">
                  <template #label>
                    <ConfigHelpLabel
                      label="字段数据源"
                      help-key="entityList.dataSourceType"
                    />
                  </template>
                  <el-select
                    v-model="editingField.dataSourceType"
                    :disabled="!isVirtualField(editingField)"
                    style="width: 100%"
                    @change="handleDataSourceChange(editingField)"
                  >
                    <el-option
                      v-for="option in selectableDataSourceOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                      :disabled="isVirtualField(editingField) && option.supportsVirtualField === false"
                    />
                  </el-select>
                  <div class="form-tip">只列出当前实体可用的数据源。没写适用范围的数据源对全部实体可见。</div>
                </el-form-item>
                <el-form-item label="扩展接口">
                  <template #label>
                    <ConfigHelpLabel
                      label="扩展接口"
                      help-key="entityList.interfaceExtension"
                    />
                  </template>
                  <el-select
                    v-model="editingField.interfaceExtensionId"
                    clearable
                    filterable
                    placeholder="可选：LIST_COLUMN 扩展接口"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in listColumnInterfaces"
                      :key="item.extensionId"
                      :label="`${item.displayName} (${item.extensionKey})`"
                      :value="item.extensionId"
                    />
                  </el-select>
                </el-form-item>
              </el-form>
            </div>
            <div class="field-config-subsection">
              <div class="field-config-subsection__title">单元格显示</div>
              <el-form label-width="110px" size="small">
                <el-form-item label="渲染组件">
                  <template #label>
                    <ConfigHelpLabel
                      label="渲染组件"
                      help-key="entityList.renderComponent"
                    />
                  </template>
                  <el-select
                    v-model="editingField.renderComponent"
                    clearable
                    placeholder="自动匹配"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="option in selectableCellComponentOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                  <div class="form-tip">只列出当前实体可用的扩展。没写适用范围的扩展对全部实体可见。</div>
                </el-form-item>
              </el-form>
              <ConfigSchemaEditor
                v-model="editingRenderConfig"
                :schema="selectedCellDescriptor?.configSchema || []"
              />
            </div>
          </SettingsSection>
          <SettingsSection
            title="模板初始化"
            description="选择后把模板配置复制到当前列；保存后独立，后续模板修改不会影响本列"
            :default-expanded="false"
          >
            <el-form label-width="110px" size="small">
              <el-form-item label="初始化模板">
                <el-select
                  v-model="selectedListTemplateId"
                  clearable
                  filterable
                  placeholder="选择模板并复制配置"
                  style="width: 100%"
                  @change="applyListColumnTemplate"
                >
                  <el-option
                    v-for="template in listTemplates"
                    :key="template.id"
                    :label="template.templateName"
                    :value="template.id"
                  />
                </el-select>
                <div class="field-help">
                  只复制数据源、查询和显示配置，不保存模板关联，也不会自动跟随模板更新。
                </div>
              </el-form-item>
            </el-form>
          </SettingsSection>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="fieldConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveFieldAdvancedConfig">保存当前列</el-button>
      </template>
    </el-dialog>
    <UiConfigPublishDialog
      v-model="publishDialogVisible"
      config-type="LIST"
      :config-id="String(configId)"
      config-label="列表"
      @published="handlePublished"
    />
    <EventBindingDialog
      ref="eventBindingDialogRef"
      owner-type="LIST"
      :owner-id="String(configInfo.id || configId)"
      owner-label="列表"
      :field-options="eventFieldOptions"
      :button-options="eventButtonOptions"
      @changed="handleEventBindingsChanged"
    />
    <UiConfigReleaseHistoryDialog
      ref="releaseHistoryDialogRef"
      config-type="LIST"
      :config-id="configId"
      config-label="列表"
      @changed="handleReleaseChanged"
    />
    <RuntimeCodeViewerDialog ref="runtimeCodeDialogRef" />
    <RelatedContentPanel
      ref="relatedContentPanelRef"
      owner-type="LIST"
      :owner-id="configInfo.id || configId"
      :source-entity="entityDefinition"
      :source-fields="entityFields"
      :source-content-fields="fieldConfigList"
      @count-change="relatedContentCount = $event"
      @loaded="relatedContents = $event"
      @configure-buttons="activeConfigTab = $event"
      @changed="handleRelatedContentChanged"
    />
  </div>
</template>
<script setup>
import PageInputParameterSettings from '@/components/page-parameters/PageInputParameterSettings.vue'
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Delete, Document, Rank, Plus } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityApi } from '@/api/entity'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import { entityListScopeRuleApi } from '@/api/entityListScopeRule'
import ListCellRenderer from '@/components/ListCellRenderer.vue'
import ListQuickCopyCell from '@/components/ListQuickCopyCell.vue'
import ListFixedFilterEditor from '@/components/ListFixedFilterEditor.vue'
import SelectionReturnMappingEditor from '@/components/SelectionReturnMappingEditor.vue'
import { validateSelectionReturnMappings } from '@/shared/selection-return-mapping-editor'
import { readFixedFilterRows, writeFixedFilterRows } from '@/shared/list-fixed-filters'
import ListButtonConfigPanel from '@/components/ListButtonConfigPanel.vue'
import { cellMappingConflict, mappedFieldCode, supportsCellAction } from '@/shared/list-cell-action'
import EntityDataSearchForm from '@/views/entity/components/EntityDataSearchForm.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import ExtensionCapabilityPicker from '@/components/ExtensionCapabilityPicker.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import UiConfigPublishDialog from '@/components/UiConfigPublishDialog.vue'
import EventBindingDialog from '@/components/ui-config/EventBindingDialog.vue'
import { listButtonEventOptions } from '@/components/ui-config/listButtonEventTargets'
import UiConfigReleaseHistoryDialog from '@/components/ui-config/UiConfigReleaseHistoryDialog.vue'
import RuntimeCodeViewerDialog from '@/components/RuntimeCodeViewerDialog.vue'
import RelatedContentPanel from '@/components/related-content/RelatedContentPanel.vue'
import { findButtonRelatedContent, isRelatedContentButton } from '@/shared/list-related-content'
import { isSelectionToolbarButton, normalizeListSelectionMode } from '@/shared/list-selection'
import { getCellComponentOptions, getCellDescriptor } from '@/extensions/core/registries/listCellRegistry.js'
import { filterOptionsByEntity } from '@/shared/extension-entity-scope'
import { getCustomListComponentOptions, getCustomListDescriptor } from '@/extensions/core/registries/customComponentRegistry.js'
import { getFormFieldComponentOptions } from '@/extensions/core/registries/formFieldRegistry.js'
import {
  applySchemaDefaults,
  safeParseConfig,
  stringifyConfig
} from '@/shared/config-runtime'
import { filterEntityFieldsByLifecycle } from '@/shared/entity-design'
import {
  calculateListActionOrderKey as localActionOrderKey,
  describeListPublishChanges as describePublishChanges,
  listActionBaselineKey as actionBaselineKey,
  listActionFingerprint as actionFingerprint,
  listMetadataDetailEntries,
  listMetadataFingerprint,
  listScopeBindingFingerprint,
  normalizeListActionForSave as normalizeActionForSave,
  resolveListButtonType,
  withListButtonTypeDefault
} from '@/shared/list-config-design'
import {
  buildListDraftRuntimeSnapshot,
  buildRuntimeCodeArtifact,
  selectRuntimeRelease
} from '@/shared/runtime-code-generator'
import {
  uiConfigDraftApi,
  uiEventBindingApi,
  uiComponentTemplateApi,
  uiExtensionApi
} from '@/api/uiConfig'
import {
  normalizeInterfaceExtensions,
  resolveInterfaceExtensionId
} from '@/components/ui-config/interfaceExtensionModel'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import { useBreadcrumbParents } from '@/composables/useBreadcrumbParents'
import { applyListColumnTemplateSnapshot } from '@/shared/list-column-template'
import {
  buildUiConfigDraftDiscardRequest,
  canDiscardUiConfigDraft,
  isUiConfigDraftDiscardConflict,
  resolveUiConfigDraftStatus
} from '@/shared/ui-config-draft'
const route = useRoute()
const configId = route.params.id
// 配置信息
const configInfo = ref({})
const fixedFilterRows = ref([])
// 未填完的新条件也属于未保存修改，不能因尚未序列化而漏掉离页提醒。
const metadataForm = computed(() => ({ ...configInfo.value, fixedFilterConfig: fixedFilterRows.value }))
const entityName = ref('')
const entityCode = ref('')
const scopePolicies = ref([])
const boundPolicyIds = ref([])
const scopeDefault = ref({
  unboundPolicy: 'DENY_ALL',
  enforcementMode: 'ENFORCE',
  confirmed: false,
  confirmationNote: ''
})
const entityId = ref('')
useBreadcrumbParents(() => [{
  id: 'entity-list-config',
  menuName: '实体列表配置',
  // 使用配置自身的实体归属，直接打开设计页时也能返回对应配置列表。
  path: entityId.value ? `/entity-list-config/${encodeURIComponent(entityId.value)}` : ''
}])
const entityFields = ref([])
const entityDefinition = ref({})
const isSystemEntity = computed(() => entityDefinition.value?.storageMode === 'SYSTEM')
const publishDialogVisible = ref(false)
const eventBindingDialogRef = ref(null)
const releaseHistoryDialogRef = ref(null)
const runtimeCodeDialogRef = ref(null)
const relatedContentPanelRef = ref(null)
const relatedContentCount = ref(0)
const relatedContents = ref([])
const runtimeCodeLoading = ref(false)
const diffInfo = ref({ changed: true, changedSections: [] })
const diffLoadSucceeded = ref(false)
const discardDraftLoading = ref(false)
const canDiscardDraft = computed(() => canDiscardUiConfigDraft({
  diffLoadSucceeded: diffLoadSucceeded.value,
  diff: diffInfo.value,
  serverCanDiscardDraft: diffInfo.value.canDiscardDraft === true,
  activeReleaseId: configInfo.value.activeReleaseId
}))
const draftStatus = computed(() => resolveUiConfigDraftStatus({
  diffLoadSucceeded: diffLoadSucceeded.value,
  diff: diffInfo.value
}))
const pageLoading = ref(false)
const loadError = ref('')
const previewDialogVisible = ref(false)
const previewError = ref('')
const previewViewport = ref('desktop')
const previewQueryForm = ref({})
const previewDataList = ref([])
const previewLoading = ref(false)
const previewPageNum = ref(1)
const previewPageSize = ref(10)
const previewTotal = ref(0)
const savingAll = ref(false)
const availableListColumnInterfaces = ref([])
const eventFieldOptions = computed(() =>
  entityFields.value
    .filter(field => field.uiConfigurable !== false)
    .map(field => ({
      label: field.fieldName || field.fieldCode,
      value: field.fieldCode
    }))
)
const listTemplates = ref([])
const selectedListTemplateId = ref('')
const dataSourceOptions = ref([
  {
    value: 'ENTITY_FIELD',
    label: '实体字段',
    description: '直接读取实体系统字段或自定义字段。',
    supportsVirtualField: false,
    supportsQuery: true,
    configSchema: []
  }
])
const cellComponentOptions = getCellComponentOptions()
const customListOptions = getCustomListComponentOptions()
const selectedCustomListCatalogOption = computed(() => {
  const option = customListOptions.find(item =>
    item.value === configInfo.value.customComponent)
  return option
    ? {
        key: option.value,
        displayName: option.label,
        description: option.description
      }
    : null
})
const queryComponentOptions = getFormFieldComponentOptions()
const queryTypeOptions = [
  { label: '等于', value: 'EQ' },
  { label: '不等于', value: 'NE' },
  { label: '包含', value: 'LIKE' },
  { label: '不包含', value: 'NOT_LIKE' },
  { label: '大于', value: 'GT' },
  { label: '大于等于', value: 'GE' },
  { label: '小于', value: 'LT' },
  { label: '小于等于', value: 'LE' },
  { label: '范围', value: 'BETWEEN' },
  { label: '包含于', value: 'IN' },
  { label: '不包含于', value: 'NOT_IN' },
  { label: '为空', value: 'EMPTY' },
  { label: '非空', value: 'NOT_EMPTY' }
]
const systemQueryTypeValues = new Set([
  'EQ',
  'NE',
  'LIKE',
  'IN',
  'BETWEEN',
  'GT',
  'GE',
  'LT',
  'LE',
  'IS_NULL'
])
const availableQueryTypeOptions = computed(() => {
  if (!isSystemEntity.value) return queryTypeOptions
  return [
    ...queryTypeOptions.filter(option =>
      systemQueryTypeValues.has(option.value)
    ),
    { label: '为空', value: 'IS_NULL' }
  ]
})
const createDefaultViewConfig = () => ({
  search: {
    defaultVisibleCount: 4,
    collapsible: true,
    labelWidth: 100
  },
  table: {
    stripe: true,
    border: false,
    showIndex: true,
    size: 'default',
    defaultSortField: '',
    defaultSortDirection: 'ASC'
  },
  pagination: {
    pageSize: 10,
    pageSizes: [10, 20, 50, 100]
  },
  customComponentProps: {}
})
const viewConfig = ref(createDefaultViewConfig())
// 字段配置列表
const fieldConfigList = ref([])
const configPanelRef = ref(null)
const fieldTableRef = ref(null)
let sortableInstance = null
let configResizeObserver = null
let fieldLayoutFrame = 0
let observedConfigWidth = 0
// 配置 Tab
const activeConfigTab = ref('fields')
const selectedCustomListSchema = computed(() =>
  getCustomListDescriptor(configInfo.value.customComponent)?.configSchema || []
)
const fieldConfigDialogVisible = ref(false)
const activeFieldConfigTab = ref('common')
const editingField = ref(null)
// 下拉按 supportedEntityCodes 收窄到当前实体；当前已选值始终保留
const selectableDataSourceOptions = computed(() =>
  filterOptionsByEntity(
    dataSourceOptions.value,
    entityCode.value,
    editingField.value?.dataSourceType
  )
)
const selectableCellComponentOptions = computed(() =>
  filterOptionsByEntity(
    cellComponentOptions,
    entityCode.value,
    editingField.value?.renderComponent
  )
)
const editingDataSourceConfig = ref({})
const editingRenderConfig = ref({})
const editingQueryConfig = ref({})
const editingColumnConfig = ref({})
const selectedDataSourceOption = computed(() =>
  dataSourceOptions.value.find(option => option.value === editingField.value?.dataSourceType)
)
const selectedCellDescriptor = computed(() =>
  getCellDescriptor(editingField.value?.renderComponent || 'DefaultText')
)
const listColumnInterfaces = computed(() =>
  normalizeInterfaceExtensions(availableListColumnInterfaces.value)
)
// 工具栏按钮配置
const toolbarButtons = ref([])
const toolbarRequiresSelection = computed(() => toolbarButtons.value.some(button =>
  button.enabled !== false && isSelectionToolbarButton(button)))
// 按钮可单独保存；运行时也据按钮显示勾选列，避免列表设置尚未保存时没有选择入口。
watch([toolbarRequiresSelection, () => configInfo.value.selectionMode], ([required, mode]) => {
  if (required && mode !== 'MULTIPLE') configInfo.value.selectionMode = 'MULTIPLE'
})
// 操作列按钮配置
const rowActionButtons = ref([])
const eventButtonOptions = computed(() =>
  listButtonEventOptions(toolbarButtons.value, rowActionButtons.value))
const baselinesReady = ref(false)
const metadataBaseline = ref('')
const scopeBindingBaseline = ref('[]')
const scopeDefaultBaseline = ref('')
const metadataDetailBaselines = ref(new Map())
const fieldBaselines = ref(new Map())
const actionBaselines = ref(new Map())
const previewQueryFields = computed(() =>
  fieldConfigList.value
    .filter(field => field.isQuery)
    .map(field => {
      const originField = entityFields.value.find(item => item.id === field.fieldId)
      const queryConfig = safeParseConfig(field.queryConfig)
      return {
        ...field,
        componentType: queryConfig.componentType || field.componentType,
        placeholder: queryConfig.placeholder || field.placeholder,
        fieldType: originField?.fieldType || field.fieldType || 'STRING',
        optionsJson: originField?.optionsJson || field.optionsJson
      }
    })
)
const previewListFields = computed(() =>
  fieldConfigList.value.filter(field => field.showInList)
)
function rememberScopeBindingBaseline() {
  scopeBindingBaseline.value = listScopeBindingFingerprint(boundPolicyIds.value)
  scopeDefaultBaseline.value = JSON.stringify({
    unboundPolicy: scopeDefault.value.unboundPolicy,
    enforcementMode: scopeDefault.value.enforcementMode,
    confirmed: scopeDefault.value.confirmed
  })
}

function rememberMetadataBaseline() {
  metadataBaseline.value = listMetadataFingerprint(metadataForm.value, viewConfig.value)
  metadataDetailBaselines.value = new Map(
    listMetadataDetailEntries(metadataForm.value, viewConfig.value)
      .map(item => [item.key, JSON.stringify(item.value)])
  )
}

function fieldFingerprint(field) {
  return JSON.stringify(normalizeFieldForSave(field))
}
function rememberFieldBaseline(field) {
  fieldBaselines.value.set(field.fieldId, fieldFingerprint(field))
}
function rememberActionBaseline(button, position) {
  actionBaselines.value.set(
    actionBaselineKey(button, position),
    actionFingerprint(button, position)
  )
}
function rememberAllBaselines() {
  rememberMetadataBaseline()
  rememberScopeBindingBaseline()
  fieldBaselines.value = new Map()
  fieldConfigList.value.forEach(rememberFieldBaseline)
  actionBaselines.value = new Map()
  toolbarButtons.value.forEach(button => rememberActionBaseline(button, 'TOOLBAR'))
  rowActionButtons.value.forEach(button => rememberActionBaseline(button, 'ROW'))
  baselinesReady.value = true
}
const metadataDirty = computed(() =>
  baselinesReady.value
    && metadataBaseline.value !== listMetadataFingerprint(metadataForm.value, viewConfig.value)
)
const scopeBindingDirty = computed(() =>
  baselinesReady.value
    && !isSystemEntity.value
    && scopeBindingBaseline.value !== listScopeBindingFingerprint(boundPolicyIds.value)
)
const scopeDefaultDirty = computed(() =>
  baselinesReady.value
    && !isSystemEntity.value
    && scopeDefaultBaseline.value !== JSON.stringify({
      unboundPolicy: scopeDefault.value.unboundPolicy,
      enforcementMode: scopeDefault.value.enforcementMode,
      confirmed: scopeDefault.value.confirmed
    })
)
const scopeDefaultAlertType = computed(() => {
  if (scopeDefault.value.enforcementMode === 'OBSERVE') return 'error'
  return scopeDefault.value.unboundPolicy === 'EXPLICIT_ALL' ? 'warning' : 'info'
})
const scopeDefaultAlertTitle = computed(() => {
  if (scopeDefault.value.enforcementMode === 'OBSERVE') {
    return '存量观察期仍会全部放行，请尽快选择安全策略或显式确认全量可见'
  }
  if (scopeDefault.value.unboundPolicy === 'PERSONAL') {
    return '未绑定数据规则时，仅返回本人创建或提交的数据'
  }
  if (scopeDefault.value.unboundPolicy === 'EXPLICIT_ALL') {
    return scopeDefault.value.confirmed
      ? '管理员已显式确认：未绑定数据规则时允许查看全部数据'
      : '全量可见尚未确认，后端将按拒绝全部处理'
  }
  return '未绑定数据规则时默认拒绝全部数据'
})
const dirtyMetadataItems = computed(() => {
  if (!metadataDirty.value) return []
  const items = listMetadataDetailEntries(metadataForm.value, viewConfig.value)
    .filter(item =>
      metadataDetailBaselines.value.get(item.key) !== JSON.stringify(item.value)
    )
    .map(item => ({ key: `metadata:${item.key}`, label: item.label }))
  return items.length > 0
    ? items
    : [{ key: 'metadata:other', label: '列表设置：其他配置' }]
})
const dirtyFields = computed(() =>
  baselinesReady.value
    ? fieldConfigList.value.filter(field =>
        fieldBaselines.value.get(field.fieldId) !== fieldFingerprint(field)
      )
    : []
)
const dirtyActions = computed(() => {
  if (!baselinesReady.value) return []
  return [
    ...toolbarButtons.value.map(button => ({ button, position: 'TOOLBAR' })),
    ...rowActionButtons.value.map(button => ({ button, position: 'ROW' }))
  ].filter(({ button, position }) =>
    actionBaselines.value.get(actionBaselineKey(button, position))
      !== actionFingerprint(button, position)
  )
})
const isDirty = computed(() =>
  metadataDirty.value
    || scopeBindingDirty.value
    || scopeDefaultDirty.value
    || dirtyFields.value.length > 0
    || dirtyActions.value.length > 0
)
const unsavedItems = computed(() => [
  ...dirtyMetadataItems.value,
  ...(scopeBindingDirty.value
    ? [{ key: 'scope-bindings', label: '列表设置：数据规则绑定' }]
    : []),
  ...(scopeDefaultDirty.value
    ? [{ key: 'scope-default', label: '列表设置：未绑定规则安全策略' }]
    : []),
  ...dirtyFields.value.map(field => ({
    key: `field:${field.fieldId}`,
    label: `字段配置：${field.fieldName || field.fieldCode || '未命名字段'}`
  })),
  ...dirtyActions.value.map(({ button, position }) => ({
    key: `action:${actionBaselineKey(button, position)}`,
    label: `${position === 'TOOLBAR' ? '工具栏按钮' : '操作列按钮'}：${button.label || button.key || '未命名按钮'}`
  }))
])
const unsavedSummary = computed(() => {
  return `${unsavedItems.value.length} 项未保存`
})
useUnsavedChangesGuard(isDirty, {
  message: '列表设置、数据规则绑定、查询接口、字段或按钮有未保存修改，离开后这些修改将丢失。'
})
function refreshFieldTableLayout() {
  if (fieldLayoutFrame && typeof cancelAnimationFrame === 'function') {
    cancelAnimationFrame(fieldLayoutFrame)
  }
  const runLayout = () => {
    fieldLayoutFrame = 0
    fieldTableRef.value?.doLayout?.()
  }
  fieldLayoutFrame = typeof requestAnimationFrame === 'function'
    ? requestAnimationFrame(runLayout)
    : 0
  if (!fieldLayoutFrame) {
    runLayout()
  }
}
function observeConfigPanelWidth() {
  configResizeObserver?.disconnect()
  configResizeObserver = null
  observedConfigWidth = 0
  if (!configPanelRef.value || typeof ResizeObserver === 'undefined') return
  configResizeObserver = new ResizeObserver(([entry]) => {
    const nextWidth = Math.round(entry?.contentRect?.width || 0)
    if (!nextWidth || nextWidth === observedConfigWidth) return
    observedConfigWidth = nextWidth
    refreshFieldTableLayout()
  })
  configResizeObserver.observe(configPanelRef.value)
}
onMounted(async () => {
  await loadData()
  await nextTick()
  observeConfigPanelWidth()
  refreshFieldTableLayout()
  openLinkedListEventBindings()
})
onBeforeUnmount(() => {
  configResizeObserver?.disconnect()
  if (fieldLayoutFrame && typeof cancelAnimationFrame === 'function') {
    cancelAnimationFrame(fieldLayoutFrame)
  }
  sortableInstance?.destroy()
})
watch(activeConfigTab, async (tab) => {
  if (tab !== 'fields') return
  await nextTick()
  refreshFieldTableLayout()
  initSortable()
})
async function loadScopeBindings() {
  if (!entityCode.value || !configInfo.value.listKey) {
    scopePolicies.value = []
    boundPolicyIds.value = []
    return
  }
  const configuration = await entityListScopeRuleApi.getConfiguration(entityCode.value)
  scopePolicies.value = (configuration?.policies || []).map(policy => ({
    policyId: policy.id,
    ruleName: policy.policyName,
    presetCode: policy.presetCode,
    filterType: policy.filterConfig?.type || policy.presetCode
  }))
  boundPolicyIds.value = (configuration?.bindings || [])
    .filter(binding => binding.listKey === configInfo.value.listKey && binding.policyId)
    .map(binding => binding.policyId)
  const configuredDefault = configuration?.listDefaults?.[configInfo.value.listKey]
  scopeDefault.value = {
    unboundPolicy: configuredDefault?.unboundPolicy || 'DENY_ALL',
    enforcementMode: configuredDefault?.enforcementMode || 'ENFORCE',
    confirmed: configuredDefault?.confirmed === true,
    confirmationNote: configuredDefault?.confirmationNote || ''
  }
  rememberScopeBindingBaseline()
}

async function loadData(options = {}) {
  // 撤销成功或前置条件冲突后使用严格模式，任一快照分区失败都不建立新本地基线。
  const strict = options?.strict === true
  let completed = false
  pageLoading.value = true
  loadError.value = ''
  baselinesReady.value = false
  diffLoadSucceeded.value = false
  try {
    const [extensionOptions, templates] = await Promise.all([
      strict
        ? entityListConfigApi.getExtensionOptions()
        : entityListConfigApi.getExtensionOptions().catch(() => []),
      strict
        ? uiComponentTemplateApi.list({ templateType: 'LIST_COLUMN_GROUP' })
        : uiComponentTemplateApi.list({ templateType: 'LIST_COLUMN_GROUP' }).catch(() => [])
    ])
    listTemplates.value = Array.isArray(templates) ? templates : []
    if (Array.isArray(extensionOptions) && extensionOptions.length > 0) {
      dataSourceOptions.value = extensionOptions
    }
    // 加载列表配置
    const configRes = await entityListConfigApi.getById(configId)
    if (configRes) {
      configInfo.value = configRes
      configInfo.value.dataScopeMode = configRes.dataScopeMode || 'INHERIT'
      const selectionConfig = safeJsonParse(configRes.selectionConfig) || {}
      configInfo.value.selectionMode = normalizeListSelectionMode(selectionConfig.selectionMode)
      configInfo.value.selectionValueField = selectionConfig.valueField || 'id'
      configInfo.value.selectionReturnMappingsText = JSON.stringify(
        selectionConfig.returnMappings || [],
        null,
        2
      )
      entityId.value = configRes.entityId
      entityCode.value = configRes.entityCode
      viewConfig.value = mergeViewConfig(safeParseConfig(configRes.viewConfig))
      await loadDiff({ strict })
    }
    const columnRequest = uiExtensionApi.availableInterfaces({
      ownerType: 'LIST',
      ownerId: configId,
      bindingCode: 'LIST_COLUMN'
    })
    const columnInterfaces = await (strict ? columnRequest : columnRequest.catch(() => []))
    availableListColumnInterfaces.value = normalizeInterfaceExtensions(columnInterfaces)
    // 加载实体信息
    const entityRes = await entityApi.getById(entityId.value)
    if (entityRes) {
      entityDefinition.value = entityRes
      entityName.value = entityRes.entityName
      entityCode.value = entityRes.entityCode
      entityFields.value = filterEntityFieldsByLifecycle(
        entityRes,
        entityRes.fields || []
      ).filter(field =>
        field.uiConfigurable !== false
        && String(field.fieldType || '').toUpperCase() !== 'SUB_LIST'
      )
      if (isSystemEntity.value) {
        configInfo.value.dataScopeMode = 'INHERIT'
        configInfo.value.customComponent = ''
        configInfo.value.queryProviderCode = ''
        dataSourceOptions.value = dataSourceOptions.value.filter(option =>
          ['ENTITY_FIELD', 'REFERENCE'].includes(option.value)
        )
      }
    }

    fixedFilterRows.value = readFixedFilterRows(configRes?.fixedFilterConfig, {
      systemEntity: isSystemEntity.value
    })
    // 合并字段配置
    if (entityCode.value && !isSystemEntity.value) {
      await loadScopeBindings()
    }
    mergeFieldConfig(configRes?.fields || [])
    // 解析按钮配置
    parseButtonConfig(configRes)
    await nextTick()
    rememberAllBaselines()
    refreshFieldTableLayout()
    initSortable()
    completed = true
  } catch (e) {
    console.error('加载数据失败:', e)
    loadError.value = e?.message || '列表配置加载失败，请重试'
    if (strict) throw e
  } finally {
    // 严格重载失败时继续锁住设计区，但保留上方错误提示中的“重新加载”入口。
    if (!strict || completed) pageLoading.value = false
  }
}

function mergeFieldConfig(savedFields) {
  // 以实体字段为基准
  const merged = entityFields.value.map((ef, index) => {
    const saved = savedFields.find(sf => sf.fieldId === ef.id)
    return {
      id: saved?.id,
      revision: saved?.revision || 0,
      orderKey: saved?.orderKey || (index + 1) * 1000000,
      fieldId: ef.id,
      fieldCode: ef.fieldCode,
      fieldName: saved?.fieldName || ef.fieldName,
      fieldType: ef.fieldType,
      optionsJson: ef.optionsJson,
      showInList: saved ? saved.showInList : ef.showInList,
      isQuery: saved ? saved.isQuery : ef.isQuery,
      queryType: saved?.queryType || 'LIKE',
      width: saved?.width || 0,
      align: saved?.align || 'left',
      dataSourceType: saved?.dataSourceType || 'ENTITY_FIELD',
      dataSourceConfig: saved?.dataSourceConfig || '',
      interfaceExtensionId: resolveInterfaceExtensionId({
        extensionId: saved?.interfaceExtensionId,
        dataSourceId: saved?.dataSourceId,
        operationCode: saved?.dataSourceOperationCode
      }, availableListColumnInterfaces.value),
      templateId: saved?.templateId,
      templateVersion: saved?.templateVersion,
      localOverridesDocument: saved?.localOverridesDocument || '',
      renderComponent: saved?.renderComponent || '',
      formatter: saved?.formatter || '',
      columnConfig: saved?.columnConfig || '',
      queryConfig: saved?.queryConfig || '',
      renderConfig: saved?.renderConfig || '',
      sortOrder: saved?.sortOrder ?? index
    }
  })
  savedFields
    .filter(() => !isSystemEntity.value)
    .filter(saved => !entityFields.value.some(entityField => String(entityField.id) === String(saved.fieldId)))
    .forEach((saved, index) => {
      merged.push({
        ...saved,
        id: saved.id,
        revision: saved.revision || 0,
        orderKey: saved.orderKey || (entityFields.value.length + index + 1) * 1000000,
        fieldId: saved.fieldId || `virtual_${Date.now()}_${index}`,
        fieldCode: saved.fieldCode || `virtual_${index + 1}`,
        fieldName: saved.fieldName || '虚拟列',
        fieldType: saved.fieldType || 'STRING',
        showInList: saved.showInList !== false,
        isQuery: saved.isQuery === true,
        queryType: saved.queryType || 'EQ',
        width: saved.width || 0,
        align: saved.align || 'left',
        dataSourceType: saved.dataSourceType || 'FIELD_TEMPLATE',
        dataSourceConfig: saved.dataSourceConfig || '',
        renderComponent: saved.renderComponent || '',
        formatter: saved.formatter || '',
        columnConfig: saved.columnConfig || '',
        queryConfig: saved.queryConfig || '',
        renderConfig: saved.renderConfig || '',
        sortOrder: saved.sortOrder ?? entityFields.value.length + index
      })
    })
  // 按 sortOrder 排序
  merged.sort((a, b) => a.sortOrder - b.sortOrder)
  fieldConfigList.value = merged
}
function mergeViewConfig(saved) {
  const defaults = createDefaultViewConfig()
  return {
    ...defaults,
    ...saved,
    search: { ...defaults.search, ...(saved.search || {}) },
    table: { ...defaults.table, ...(saved.table || {}) },
    pagination: { ...defaults.pagination, ...(saved.pagination || {}) },
    customComponentProps: saved.customComponentProps || {}
  }
}
function isVirtualField(field) {
  return String(field?.fieldId || '').startsWith('virtual_')
}
function supportsQuery(field) {
  const option = dataSourceOptions.value.find(item => item.value === field.dataSourceType)
  return option?.supportsQuery !== false
}
function addVirtualField() {
  const timestamp = Date.now()
  const defaultSource = filterOptionsByEntity(dataSourceOptions.value, entityCode.value)
    .find(option => option.supportsVirtualField !== false)
  fieldConfigList.value.push({
    fieldId: `virtual_${timestamp}`,
    fieldCode: `virtual_${timestamp}`,
    fieldName: '虚拟列',
    fieldType: 'STRING',
    showInList: true,
    isQuery: false,
    queryType: 'EQ',
    width: 0,
    align: 'left',
    dataSourceType: defaultSource?.value || 'FIELD_TEMPLATE',
    dataSourceConfig: '',
    renderComponent: 'DefaultText',
    formatter: '',
    columnConfig: '',
    queryConfig: '',
    renderConfig: '',
    sortOrder: fieldConfigList.value.length,
    orderKey: (fieldConfigList.value.length + 1) * 1000000,
    revision: 0,
    interfaceExtensionId: ''
  })
}
async function removeVirtualField(field) {
  try {
    await ElMessageBox.confirm(
      `删除虚拟列“${field.fieldName || field.fieldCode}”后，它会从列表草稿中移除；发布后运行时不再展示。`,
      '删除虚拟列',
      {
        type: 'warning',
        confirmButtonText: '删除虚拟列',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  if (field.id && field.revision > 0) {
    try {
      await entityListConfigApi.deleteField(
        configId,
        field.id,
        field.revision
      )
    } catch (error) {
      handleRevisionConflict(error, field)
      return
    }
  }
  fieldConfigList.value = fieldConfigList.value.filter(item => item !== field)
  fieldBaselines.value.delete(field.fieldId)
  await refreshConfigRevision()
  await loadDiff()
}
function handleDataSourceChange(field) {
  const option = dataSourceOptions.value.find(item => item.value === field.dataSourceType)
  if (option?.supportsQuery === false) {
    field.isQuery = false
  }
  const schema = option?.configSchema || []
  field.dataSourceConfig = stringifyConfig(applySchemaDefaults(
    schema,
    safeParseConfig(field.dataSourceConfig)
  ))
}
function openFieldConfig(field) {
  editingField.value = field
  syncFieldConfigEditors(field)
  selectedListTemplateId.value = ''
  activeFieldConfigTab.value = 'common'
  fieldConfigDialogVisible.value = true
}
function syncFieldConfigEditors(field) {
  editingDataSourceConfig.value = applySchemaDefaults(
    dataSourceOptions.value.find(item => item.value === field.dataSourceType)?.configSchema || [],
    safeParseConfig(field.dataSourceConfig)
  )
  editingRenderConfig.value = applySchemaDefaults(
    getCellDescriptor(field.renderComponent || 'DefaultText')?.configSchema || [],
    safeParseConfig(field.renderConfig)
  )
  editingQueryConfig.value = {
    componentType: '',
    placeholder: '',
    defaultValue: '',
    ...safeParseConfig(field.queryConfig)
  }
  editingColumnConfig.value = {
    fixed: '',
    minWidth: 100,
    showOverflowTooltip: true,
    quickCopy: false,
    ...safeParseConfig(field.columnConfig)
  }
}
async function applyListColumnTemplate(templateId) {
  if (!editingField.value || !templateId) return
  const template = listTemplates.value.find(item => item.id === templateId)
  try {
    const snapshot = await uiComponentTemplateApi.snapshot(templateId)
    if (!snapshot || typeof snapshot !== 'object') {
      ElMessage.warning('模板配置不存在，请刷新后重试')
      return
    }
    applyListColumnTemplateSnapshot(editingField.value, snapshot)
    syncFieldConfigEditors(editingField.value)
    ElMessage.success(`已复制模板“${template?.templateName || '未命名模板'}”，保存后与模板互不影响`)
  } catch (error) {
    ElMessage.error(error?.message || '复制模板配置失败')
  } finally {
    selectedListTemplateId.value = ''
  }
}
async function saveFieldAdvancedConfig() {
  if (!editingField.value) return
  editingField.value.dataSourceConfig = stringifyConfig(editingDataSourceConfig.value)
  editingField.value.renderConfig = stringifyConfig(editingRenderConfig.value)
  editingField.value.queryConfig = stringifyConfig(editingQueryConfig.value)
  editingField.value.columnConfig = stringifyConfig(editingColumnConfig.value)
  await saveCurrentField(editingField.value)
  fieldConfigDialogVisible.value = false
}
const DEFAULT_TOOLBAR_BUTTONS = [
  { key: 'create', type: 'built-in', label: '新增数据', icon: 'Plus', buttonType: 'primary', sort: 1, enabled: true, perm: '' },
  { key: 'exportSelected', type: 'built-in', label: '导出选中', icon: 'Download', buttonType: 'default', sort: 2, enabled: true, perm: '' },
  { key: 'exportAll', type: 'built-in', label: '导出全部', icon: 'Download', buttonType: 'default', sort: 3, enabled: true, perm: '' },
  { key: 'batchDelete', type: 'built-in', label: '批量删除', icon: 'Delete', buttonType: 'danger', sort: 4, enabled: true, perm: '' }
]
const DEFAULT_ROW_ACTION_BUTTONS = [
  { key: 'view', type: 'built-in', label: '查看', buttonType: 'primary', link: true, sort: 1, enabled: true, perm: '' },
  { key: 'edit', type: 'built-in', label: '编辑', buttonType: 'primary', link: true, sort: 2, enabled: true, perm: '' },
  { key: 'approve', type: 'built-in', label: '审批', buttonType: 'warning', link: true, sort: 3, enabled: true, perm: '' },
  { key: 'delete', type: 'built-in', label: '删除', buttonType: 'danger', link: true, sort: 4, enabled: true, perm: '' }
]
function safeJsonParse(text) {
  if (!text) return null
  if (typeof text === 'object') return text
  try {
    return JSON.parse(text)
  } catch (e) {
    return null
  }
}
function parseButtonConfig(configRes) {
  if (isSystemEntity.value) {
    toolbarButtons.value = []
    rowActionButtons.value = [
      {
        key: 'view',
        type: 'built-in',
        label: '查看',
        buttonType: 'primary',
        link: true,
        sort: 1,
        enabled: true,
        perm: ''
      }
    ]
    return
  }
  const toolbar = safeJsonParse(configRes?.toolbarConfig)
  toolbarButtons.value = toolbar && toolbar.length > 0
    ? toolbar.map(withListButtonTypeDefault)
    : DEFAULT_TOOLBAR_BUTTONS.map(b => ({ ...b }))
  const rowActions = safeJsonParse(configRes?.rowActionConfig)
  rowActionButtons.value = rowActions && rowActions.length > 0
    ? rowActions.map(withListButtonTypeDefault)
    : DEFAULT_ROW_ACTION_BUTTONS.map(b => ({ ...b }))
}
async function refreshListActions() {
  const latest = await entityListConfigApi.getById(configId)
  if (!latest) return
  configInfo.value.revision = latest.revision
  configInfo.value.activeReleaseId = latest.activeReleaseId
  configInfo.value.publishedVersion = latest.publishedVersion
  parseButtonConfig(latest)
  toolbarButtons.value.forEach(button => rememberActionBaseline(button, 'TOOLBAR'))
  rowActionButtons.value.forEach(button => rememberActionBaseline(button, 'ROW'))
}
function applySavedAction(button, saved) {
  const params = safeJsonParse(saved?.actionParamsDocument) || {}
  const availabilityRule = safeJsonParse(saved?.availabilityRuleDocument)
  Object.assign(button, params, {
    id: saved.id,
    revision: saved.revision,
    orderKey: saved.orderKey,
    key: saved.buttonKey,
    type: saved.buttonType,
    label: saved.buttonLabel,
    icon: saved.icon || '',
    buttonType: resolveListButtonType({
      key: saved.buttonKey,
      buttonType: saved.styleType
    }),
    link: saved.linkMode === true,
    customMode: saved.customMode || '',
    customHandler: saved.handlerCode || '',
    perm: saved.permissionCode || '',
    sort: saved.sortOrder ?? 0,
    enabled: saved.enabled !== false,
    // 权威响应中缺失表示已解绑，不能保留 Object.assign 前的旧映射。
    mappedFieldCode: params.mappedFieldCode || '',
    hideWhenMapped: params.hideWhenMapped === true,
    // 关系型响应是保存后的权威值；null 表示用户已经显式清空条件。
    availabilityRule: availabilityRule || null,
    templateId: saved.templateId || null,
    templateVersion: saved.templateVersion || null,
    localOverridesDocument: saved.localOverridesDocument || null
  })
}
async function saveListAction(button, position, options = {}) {
  if (position === 'ROW' && supportsCellAction(button) && mappedFieldCode(button)) {
    const conflict = cellMappingConflict(button, rowActionButtons.value)
    if (conflict) {
      ElMessage.warning(`字段已映射到“${conflict.label || conflict.key}”，请先解除原映射`)
      return false
    }
  }
  if (button.enabled !== false && isRelatedContentButton(button)
      && !findButtonRelatedContent(button, relatedContents.value)) {
    ElMessage.warning('请选择当前列表中已启用且以弹窗、抽屉或页面显示的关联内容')
    return false
  }
  button._saving = true
  try {
    const payload = normalizeActionForSave(button, position)
    let saved
    if (button.id && button.revision > 0) {
      saved = await entityListConfigApi.patchAction(configId, button.id, payload)
    } else {
      saved = await entityListConfigApi.createAction(configId, payload)
    }
    applySavedAction(button, saved)
    await refreshConfigRevision()
    rememberActionBaseline(button, position)
    await loadDiff()
    if (!options.silent) {
      ElMessage.success('当前按钮已保存，尚未发布')
    }
    return true
  } catch (error) {
    handleRevisionConflict(error)
    await refreshListActions().catch(() => {})
    return false
  } finally {
    button._saving = false
  }
}
async function reorderListAction({ oldIndex, newIndex }, position) {
  const target = position === 'TOOLBAR' ? toolbarButtons : rowActionButtons
  if (
    oldIndex == null
    || newIndex == null
    || oldIndex === newIndex
    || !target.value[oldIndex]
  ) {
    return
  }
  const reordered = [...target.value]
  const [button] = reordered.splice(oldIndex, 1)
  reordered.splice(newIndex, 0, button)
  target.value = reordered
  if (!button.id || button.revision <= 0) {
    button.sort = newIndex + 1
    button.orderKey = localActionOrderKey(reordered, newIndex)
    return
  }
  button._saving = true
  try {
    const previousId = reordered
      .slice(0, newIndex)
      .reverse()
      .find(item => item.id)?.id || null
    const nextId = reordered
      .slice(newIndex + 1)
      .find(item => item.id)?.id || null
    const saved = await entityListConfigApi.reorderAction(
      configId,
      button.id,
      {
        expectedRevision: button.revision,
        previousId,
        nextId
      }
    )
    button.revision = saved.revision
    button.orderKey = saved.orderKey
    await refreshConfigRevision()
    rememberActionBaseline(button, position)
    await loadDiff()
    ElMessage.success('按钮顺序已调整，尚未发布')
  } catch (error) {
    handleRevisionConflict(error, button)
    await refreshListActions().catch(() => {})
  } finally {
    button._saving = false
  }
}
async function removeListAction(button, position) {
  try {
    await ElMessageBox.confirm(
      `删除按钮“${button.label || button.key}”后，它会从列表草稿中移除；发布后用户将不能再使用该操作。`,
      '删除列表按钮',
      {
        type: 'warning',
        confirmButtonText: '删除按钮',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  try {
    if (button.id && button.revision > 0) {
      await entityListConfigApi.deleteAction(configId, button.id, button.revision)
      await refreshConfigRevision()
    }
    const target = position === 'TOOLBAR' ? toolbarButtons : rowActionButtons
    target.value = target.value.filter(item => item !== button)
    actionBaselines.value.delete(actionBaselineKey(button, position))
    await loadDiff()
    ElMessage.success('当前按钮已删除，尚未发布')
  } catch (error) {
    handleRevisionConflict(error)
    await refreshListActions().catch(() => {})
  }
}
function initSortable() {
  const tableEl = fieldTableRef.value?.$el?.querySelector('.el-table__body-wrapper tbody')
  if (!tableEl) return
  if (sortableInstance) {
    sortableInstance.destroy()
  }
  sortableInstance = new Sortable(tableEl, {
    handle: '.drag-handle',
    animation: 150,
    onEnd: async (evt) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex === newIndex) return
      const item = fieldConfigList.value.splice(oldIndex, 1)[0]
      fieldConfigList.value.splice(newIndex, 0, item)
      if (item.id && item.revision > 0) {
        try {
          const saved = await entityListConfigApi.reorderField(
            configId,
            item.id,
            {
              expectedRevision: item.revision,
              previousId: fieldConfigList.value[newIndex - 1]?.id || null,
              nextId: fieldConfigList.value[newIndex + 1]?.id || null
            }
          )
          Object.assign(item, saved)
          await refreshConfigRevision()
          await loadDiff()
        } catch (error) {
          handleRevisionConflict(error, item)
          await loadData()
        }
      }
    }
  })
}
function normalizeFieldForSave(field, index = fieldConfigList.value.indexOf(field)) {
  return {
    id: field.id,
    fieldId: field.fieldId,
    fieldCode: field.fieldCode,
    fieldName: field.fieldName,
    showInList: field.showInList,
    isQuery: field.isQuery,
    queryType: field.queryType,
    width: field.width,
    align: field.align,
    dataSourceType: field.dataSourceType || 'ENTITY_FIELD',
    dataSourceConfig: field.dataSourceConfig || '',
    interfaceExtensionId: field.interfaceExtensionId || null,
    renderComponent: field.renderComponent || '',
    formatter: field.formatter || '',
    columnConfig: field.columnConfig || '',
    queryConfig: field.queryConfig || '',
    renderConfig: field.renderConfig || '',
    sortOrder: Math.max(0, index),
    orderKey: field.orderKey || (Math.max(0, index) + 1) * 1000000,
    templateId: null,
    templateVersion: null,
    localOverridesDocument: null
  }
}
function fieldConfigSummary(field) {
  const parts = []
  if (field.isQuery) {
    const queryLabel = {
      EQ: '等于',
      NE: '不等于',
      LIKE: '包含',
      NOT_LIKE: '不包含',
      GT: '大于',
      GE: '大于等于',
      LT: '小于',
      LE: '小于等于',
      BETWEEN: '范围',
      IN: '包含于',
      NOT_IN: '不包含于',
      EMPTY: '为空',
      NOT_EMPTY: '非空'
    }[field.queryType] || '默认查询'
    parts.push(`查询：${queryLabel}`)
  }
  if (field.showInList) {
    const renderer = cellComponentOptions.find(option => option.value === field.renderComponent)?.label
      || '自动渲染'
    parts.push(renderer)
    if (Number(field.width) > 0) parts.push(`${field.width}px`)
    if (field.align && field.align !== 'left') {
      parts.push(field.align === 'center' ? '居中' : '右对齐')
    }
    if (safeParseConfig(field.columnConfig).quickCopy === true) {
      parts.push('快捷复制')
    }
  }
  if (isVirtualField(field)) {
    const source = dataSourceOptions.value.find(option => option.value === field.dataSourceType)?.label
    if (source) parts.push(source)
  }
  return parts.join(' · ') || '未启用'
}
function isRevisionConflict(error) {
  return error?.status === 409
    || error?.errorCode === 'CONFIG_REVISION_CONFLICT'
}
function handleRevisionConflict(error, target) {
  if (isRevisionConflict(error)) {
    ElMessage.warning('配置已被其他人修改，已切换为服务器当前版本')
    if (target && error.currentData) {
      Object.assign(target, error.currentData)
    }
    return true
  }
  ElMessage.error(error?.message || '保存失败')
  return false
}
async function saveCurrentField(field, options = {}) {
  if (!field) return
  if (field.interfaceExtensionId && !listColumnInterfaces.value.some(item =>
    item.extensionId === field.interfaceExtensionId
  )) {
    ElMessage.warning('请选择当前列表可用的扩展接口')
    return false
  }
  field._saving = true
  try {
    const payload = normalizeFieldForSave(field)
    const saved = field.id && field.revision > 0
      ? await entityListConfigApi.patchField(
          configId,
          field.id,
          field.revision,
          payload
        )
      : await entityListConfigApi.createField(configId, payload)
    Object.assign(field, saved)
    await refreshConfigRevision()
    rememberFieldBaseline(field)
    await loadDiff()
    if (!options.silent) {
      ElMessage.success('当前列已保存，尚未发布')
    }
    return true
  } catch (error) {
    handleRevisionConflict(error, field)
    return false
  } finally {
    field._saving = false
  }
}
async function refreshConfigRevision() {
  const latest = await entityListConfigApi.getById(configId)
  if (latest) {
    configInfo.value.revision = latest.revision
    configInfo.value.activeReleaseId = latest.activeReleaseId
    configInfo.value.publishedVersion = latest.publishedVersion
  }
}

async function saveListMetadata(options = {}) {
  try {
    const fixedFilterConfig = writeFixedFilterRows(fixedFilterRows.value, {
      systemEntity: isSystemEntity.value, fields: entityFields.value
    })
    const saved = await entityListConfigApi.patchMetadata(configId, {
      expectedRevision: configInfo.value.revision,
      listName: configInfo.value.listName,
      description: configInfo.value.description,
      isDefault: configInfo.value.isDefault,
      customComponent: isSystemEntity.value ? '' : configInfo.value.customComponent,
      dataScopeMode: isSystemEntity.value
        ? 'INHERIT'
        : configInfo.value.dataScopeMode || 'INHERIT',
      accessPermissionCode: configInfo.value.accessPermissionCode || '',
      selectionConfig: {
        selectionMode: toolbarRequiresSelection.value ? 'MULTIPLE' : normalizeListSelectionMode(configInfo.value.selectionMode),
        valueField: configInfo.value.selectionValueField || 'id',
        returnMappings: validateSelectionReturnMappings(configInfo.value.selectionReturnMappingsText)
      },
      fixedFilterConfig,
      viewConfig: viewConfig.value,
      queryProviderCode: isSystemEntity.value
        ? ''
        : configInfo.value.queryProviderCode || ''
    })
    if (entityCode.value && configInfo.value.listKey && !isSystemEntity.value) {
      await saveScopeBindings({ silent: true })
    }
    configInfo.value.revision = saved.revision
    configInfo.value.fixedFilterConfig = fixedFilterConfig
    rememberMetadataBaseline()
    await loadDiff()
    if (!options.silent) {
      ElMessage.success('列表设置已保存。数据规则绑定已立即生效；其余列表配置仍需点「发布生效」')
    }
    return true
  } catch (error) {
    handleRevisionConflict(error, configInfo.value)
    return false
  }
}

async function saveScopeBindings(options = {}) {
  if (!entityCode.value || !configInfo.value.listKey || isSystemEntity.value) {
    rememberScopeBindingBaseline()
    return true
  }
  const includeDefault = scopeDefaultDirty.value || options.forceDefault === true
  let defaultPolicy = {}
  if (includeDefault) {
    defaultPolicy = { unboundPolicy: scopeDefault.value.unboundPolicy }
    if (scopeDefault.value.unboundPolicy === 'EXPLICIT_ALL'
      && !scopeDefault.value.confirmed) {
      let confirmation
      try {
        confirmation = await ElMessageBox.prompt(
          '全量可见会扩大数据访问范围，请填写业务原因（至少 5 个字符）',
          '确认未绑定规则时允许查看全部数据',
          {
            confirmButtonText: '确认并立即生效',
            cancelButtonText: '取消',
            inputPattern: /^.{5,}$/,
            inputErrorMessage: '业务原因至少需要 5 个字符',
            type: 'warning'
          }
        )
      } catch {
        return false
      }
      defaultPolicy.confirmExplicitAll = true
      defaultPolicy.confirmationNote = confirmation.value.trim()
    }
  }
  try {
    await entityListScopeRuleApi.replaceListBindings(
      entityCode.value,
      configInfo.value.listKey,
      boundPolicyIds.value,
      defaultPolicy
    )
    if (includeDefault) {
      scopeDefault.value.enforcementMode = 'ENFORCE'
      scopeDefault.value.confirmed =
        scopeDefault.value.unboundPolicy === 'EXPLICIT_ALL'
    }
    rememberScopeBindingBaseline()
    if (!options.silent) {
      ElMessage.success('数据规则绑定已保存并立即生效')
    }
    return true
  } catch (error) {
    ElMessage.error(error?.message || '保存数据规则绑定失败')
    return false
  }
}

async function confirmExplicitAll() {
  await saveScopeBindings({ forceDefault: true })
}

async function saveAll() {
  if (!isDirty.value) {
    ElMessage.info('当前没有未保存修改')
    return
  }
  savingAll.value = true
  let savedCount = 0
  try {
    if (metadataDirty.value) {
      if (!await saveListMetadata({ silent: true })) return
      savedCount += 1
    } else if (scopeBindingDirty.value || scopeDefaultDirty.value) {
      if (!await saveScopeBindings({ silent: true })) return
      savedCount += 1
    }
    for (const field of [...dirtyFields.value]) {
      if (!await saveCurrentField(field, { silent: true })) return
      savedCount += 1
    }
    if (!isSystemEntity.value) {
      for (const { button, position } of [...dirtyActions.value]) {
        if (!await saveListAction(button, position, { silent: true })) return
        savedCount += 1
      }
    }
    ElMessage.success(`列表草稿保存完成，共保存 ${savedCount} 项`)
  } finally {
    savingAll.value = false
  }
}
async function loadDiff({ strict = false } = {}) {
  diffLoadSucceeded.value = false
  try {
    diffInfo.value = await entityListConfigApi.getDiff(configId)
    diffLoadSucceeded.value = true
  } catch (error) {
    diffInfo.value = { changed: true, changedSections: [] }
    if (strict) throw error
  }
}
async function handleDiscardDraft() {
  if (discardDraftLoading.value || !canDiscardDraft.value) return
  try {
    await ElMessageBox.confirm(
      '撤销后将恢复到当前发布版本。自当前发布版本以来所有已保存但未发布的修改，以及当前页面尚未保存的编辑，都会被覆盖且不可恢复。列表中已即时生效的数据范围绑定不会被撤销。确定继续吗？',
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
      revision: configInfo.value.revision,
      diffLoadSucceeded: diffLoadSucceeded.value,
      diff: diffInfo.value,
      serverCanDiscardDraft: diffInfo.value.canDiscardDraft === true,
      activeReleaseId: configInfo.value.activeReleaseId
    })
    await uiConfigDraftApi.discard('LIST', configId, preconditions)
    discardCommitted = true
    await loadData({ strict: true })
    if (loadError.value) {
      ElMessage.warning('撤销已完成，但页面重新加载失败，请点击“重新加载”')
      return
    }
    if (diffInfo.value.changed) {
      ElMessage.warning('本地草稿已撤销，但当前仍存在依赖版本差异，请检查继承事件或引用配置')
      return
    }
    ElMessage.success('已撤销未发布修改，并恢复到当前发布版本')
  } catch (error) {
    if (discardCommitted) {
      ElMessage.warning('撤销已完成，但页面重新加载失败，请点击“重新加载”')
      return
    }
    if (isUiConfigDraftDiscardConflict(error)) {
      try {
        await loadData({ strict: true })
      } catch {
        ElMessage.warning('配置状态已变化，但页面重新加载失败，请点击“重新加载”')
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
async function handlePublish() {
  const listUiDirty = metadataDirty.value
    || dirtyFields.value.length > 0
    || dirtyActions.value.length > 0
  if (listUiDirty) {
    ElMessage.warning('页面仍有未保存修改，请先保存全部后再发布')
    return
  }
  // 数据规则绑定不在列表界面快照里，保存时已经发布。
  // 只改绑定再点发布，不能拿列表草稿对比结果当成失败。
  if (scopeBindingDirty.value) {
    if (!await saveScopeBindings({ silent: true })) return
    ElMessage.success('数据规则绑定已发布生效')
    return
  }
  const diff = await entityListConfigApi.getDiff(configId)
  if (!diff.changed) {
    ElMessage.success('数据规则绑定已生效。列表界面配置没有需要发布的修改。')
    return
  }
  publishDialogVisible.value = true
}
async function handlePublished() {
  await refreshConfigRevision()
  await loadDiff()
}
async function openPreview() {
  previewPageSize.value = viewConfig.value.pagination.pageSize || 10
  previewDialogVisible.value = true
  await nextTick()
  await loadPreviewData()
}
async function loadPreviewData() {
  if (!entityCode.value || !configInfo.value?.listKey) return
  previewLoading.value = true
  previewError.value = ''
  try {
    const filters = { ...previewQueryForm.value }
    previewQueryFields.value.forEach((field) => {
      const code = field.fieldCode
      if (code && filters[code] !== undefined && field.queryType) {
        filters[`${code}_op`] = field.queryType
      }
    })
    const result = await entityListRuntimeApi.query(
      entityCode.value,
      configInfo.value.listKey,
      {
        pageNum: previewPageNum.value,
        pageSize: previewPageSize.value,
        scene: 'PAGE',
        filters
      }
    )
    previewDataList.value = result?.records || result?.list || []
    previewTotal.value = Number(result?.total || previewDataList.value.length)
  } catch (error) {
    console.error('加载列表预览失败:', error)
    previewDataList.value = []
    previewTotal.value = 0
    previewError.value = error?.message || '预览数据加载失败，请重试'
  } finally {
    previewLoading.value = false
  }
}
function handlePreviewSearch() {
  previewPageNum.value = 1
  loadPreviewData()
}
function handlePreviewReset() {
  previewQueryForm.value = {}
  previewPageNum.value = 1
  loadPreviewData()
}
async function showReleaseHistory() {
  await releaseHistoryDialogRef.value?.open()
}

async function openRuntimeCode() {
  if (pageLoading.value) {
    ElMessage.info('列表配置仍在加载，请稍候')
    return
  }
  runtimeCodeLoading.value = true
  try {
    const ownerId = String(configInfo.value.id || configId || '')
    const [savedBindings, releases] = await Promise.all([
      ownerId
        ? uiEventBindingApi.list('LIST', ownerId).catch(() => [])
        : Promise.resolve([]),
      ownerId
        ? entityListConfigApi.getReleases(ownerId).catch(() => [])
        : Promise.resolve([])
    ])
    const eventBindings = Array.isArray(savedBindings)
      ? savedBindings
      : []
    const runtimeAction = (button, position) => {
      const {
        expectedRevision,
        clearFields,
        ...action
      } = normalizeActionForSave(button, position)
      return action
    }
    const draftSnapshot = buildListDraftRuntimeSnapshot({
      list: {
        ...configInfo.value,
        fixedFilterConfig: writeFixedFilterRows(fixedFilterRows.value, {
          systemEntity: isSystemEntity.value, fields: entityFields.value
        })
      },
      viewConfig: viewConfig.value,
      fields: fieldConfigList.value.map(normalizeFieldForSave),
      toolbarActions: toolbarButtons.value.map(button =>
        runtimeAction(button, 'TOOLBAR')
      ),
      rowActions: rowActionButtons.value.map(button =>
        runtimeAction(button, 'ROW')
      ),
      eventBindings
    })
    const releaseList = Array.isArray(releases)
      ? releases
      : Array.isArray(releases?.data)
        ? releases.data
        : []
    const activeRelease = selectRuntimeRelease(
      releaseList,
      configInfo.value.activeReleaseId
    )
    const published = activeRelease?.snapshotDocument
      ? buildRuntimeCodeArtifact({
          configType: 'LIST',
          configLabel: configInfo.value.listName
            || configInfo.value.listKey
            || '列表',
          source: 'PUBLISHED',
          version: activeRelease.version,
          snapshot: safeParseConfig(activeRelease.snapshotDocument)
        })
      : null
    runtimeCodeDialogRef.value?.open({
      type: 'LIST',
      label: configInfo.value.listName
        || configInfo.value.listKey
        || '列表',
      draft: buildRuntimeCodeArtifact({
        configType: 'LIST',
        configLabel: configInfo.value.listName
          || configInfo.value.listKey
          || '列表',
        source: 'DRAFT',
        snapshot: draftSnapshot
      }),
      published,
      dirty: isDirty.value,
      changed: diffInfo.value.changed === true
    })
  } catch (error) {
    console.error('生成列表最终代码失败:', error)
    ElMessage.error(error?.message || '生成列表最终代码失败')
  } finally {
    runtimeCodeLoading.value = false
  }
}

function openListEventBindings() {
  eventBindingDialogRef.value?.openOwner(configInfo.value.listName || '')
}

/**
 * 兼容历史书签中的事件深链，直接落到当前列表唯一的事件编辑入口。
 */
function openLinkedListEventBindings() {
  if (String(route.query.events || '') !== '1' || isSystemEntity.value) return
  const targetType = String(route.query.targetType || 'OWNER').toUpperCase()
  const targetKey = String(route.query.targetKey || '')
  if (targetType === 'BUTTON' && targetKey) {
    eventBindingDialogRef.value?.openButton({ key: targetKey, label: targetKey })
    return
  }
  openListEventBindings()
}

function openRelatedContent() {
  relatedContentPanelRef.value?.open()
}

async function handleEventBindingsChanged() {
  await loadDiff()
}

async function handleRelatedContentChanged(event) {
  // 关联内容独立保存到列表草稿，差异状态由宿主列表统一发布和撤销。
  if (Number.isInteger(Number(event?.ownerRevision))) {
    configInfo.value.revision = Number(event.ownerRevision)
  }
  await loadDiff()
}

async function handleReleaseChanged(event) {
  if (event?.action === 'RESTORE_DRAFT') {
    await loadData()
    await relatedContentPanelRef.value?.load()
    return
  }
  await refreshConfigRevision()
  await loadDiff()
}
</script>
<style scoped>
.entity-list-config-design {
  display: flex;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  /* 填满导航下方的内容区，避免 100vh 撑出外层滚动并带走标题栏。 */
  height: 100%;
  overflow: hidden;
}

.related-content-entry {
  display: inline-flex;
}
.page-header {
  display: flex;
  flex: 0 0 auto;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fff;
}
.unsaved-status-tag {
  cursor: help;
}
.unsaved-items-tooltip {
  max-height: 240px;
  overflow-y: auto;
}
.unsaved-items-tooltip__title {
  margin-bottom: 6px;
  font-weight: 600;
}
.unsaved-items-tooltip__item {
  line-height: 22px;
  overflow-wrap: anywhere;
}
.page-error {
  margin: 12px 12px 0;
}
.page-error :deep(.el-alert__content) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.view-config-form {
  width: 100%;
}

/* 按配置分组的实际可用宽度换列，避免侧栏占宽后控件被挤出单元格。 */
.display-config-section {
  container-type: inline-size;
}
.display-config-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: start;
  column-gap: 24px;
}
.display-config-grid > .el-form-item,
.display-config-grid :deep(.el-form-item__content) {
  min-width: 0;
}
.display-config-grid :deep(.el-form-item__content) {
  gap: 8px;
}
.display-config-grid :deep(.el-select) {
  max-width: 100%;
}
.display-config-grid :deep(.el-checkbox) {
  margin-right: 0;
}
/* 下拉框使用按钮之外的剩余空间，保证升降序始终在同一行且不被压缩。 */
.default-sort-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
.default-sort-field {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
}
.default-sort-direction {
  flex: 0 0 auto;
  flex-wrap: nowrap;
}
@container (max-width: 1050px) {
  .display-config-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@container (max-width: 680px) {
  .display-config-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.view-config-form :deep(.settings-section__body > .el-form-item:last-child),
.field-config-tabs :deep(.settings-section__body > .el-form:last-child .el-form-item:last-child) {
  margin-bottom: 10px;
}
@media (min-width: 1440px) {
  .view-config-form > :deep(.settings-section:not(.display-config-section) > .settings-section__body) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
    column-gap: 32px;
    padding-right: 20px;
    padding-left: 20px;
  }
  .view-config-form > :deep(.settings-section > .settings-section__body > .el-form-item) {
    min-width: 0;
  }
  .view-config-form > :deep(.settings-section > .settings-section__body > .view-config-item--full) {
    grid-column: 1 / -1;
  }
}
/* 能力包装层需跨满分组网格，给条件行和返回映射表留出完整编辑宽度。 */
.view-config-form :deep(.access-scope-section > .settings-section__body > .settings-capability),
.view-config-form :deep(.selection-behavior-section > .settings-section__body > .settings-capability) {
  grid-column: 1 / -1;
}
.view-config-form :deep(.access-scope-section .form-tip) {
  flex-basis: 100%;
}
.field-help {
  width: 100%;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.field-config-subsection + .field-config-subsection {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.field-config-subsection__title {
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}
.field-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.field-toolbar :deep(.el-alert) {
  flex: 1;
}
.field-config-table {
  width: 100%;
}
.field-purpose-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}
.field-purpose-controls :deep(.el-checkbox) {
  height: 22px;
  margin-right: 0;
}
.field-config-summary {
  display: block;
  overflow: hidden;
  color: #606266;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-content {
  min-height: 320px;
  max-height: 72vh;
  overflow: auto;
}
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.preview-viewport {
  width: 100%;
  margin: 0 auto;
}
.preview-viewport.is-tablet {
  max-width: 820px;
}
.preview-error {
  margin-bottom: 12px;
}
.preview-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.config-card {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  border: 0;
}
/* 把滚动限制在 Tab 内容区，标题栏和页签始终留在可见区域。 */
.config-card > :deep(.el-card__body) {
  display: flex;
  flex: 1;
  min-height: 0;
  padding: 0 20px 16px;
}
.config-tabs {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}
.config-tabs > :deep(.el-tabs__header) {
  flex: 0 0 auto;
}
.config-tabs > :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.config-tabs :deep(.el-tabs__nav-scroll) {
  overflow-x: auto;
  scrollbar-width: thin;
}
.config-tabs,
.config-tabs :deep(.el-tabs__content),
.config-tabs :deep(.el-tab-pane),
.config-panel :deep(.el-card),
.config-panel :deep(.el-card__body) {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}
.config-tabs :deep(.el-tabs__nav) {
  min-width: max-content;
}
.config-tabs :deep(.el-tabs__item) {
  white-space: nowrap;
}
.option-description,
.unit-text {
  color: #909399;
  font-size: 12px;
}
.unit-text {
  margin-left: 6px;
}
.header-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 500;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-actions > .el-button {
  margin-left: 0;
}
.system-config-alert {
  margin: 12px 12px 0;
}
.design-container {
  display: flex;
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 6px 0 12px;
  overflow: hidden;
}
.config-panel {
  display: flex;
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 100%;
  min-width: 0;
}
.drag-handle {
  cursor: move;
  color: #909399;
}
.drag-handle:hover {
  color: #409eff;
}
@media (max-width: 1280px) {
  .page-header,
  .header-left,
  .header-actions,
  .field-toolbar {
    flex-wrap: wrap;
  }
  .design-container {
    padding-bottom: 8px;
  }
  .field-toolbar {
    align-items: stretch;
  }
}

@media (max-width: 960px) {
  .header-left,
  .header-actions {
    width: 100%;
  }
  .view-config-form :deep(.el-form-item__content) {
    min-width: 0;
  }
  .view-config-form :deep(.el-select),
  .view-config-form :deep(.el-input) {
    max-width: 100%;
  }
}
</style>
