<template>
  <div class="entity-list-config-design">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button link aria-label="返回列表配置" title="返回列表配置" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span>列表配置设计：{{ configInfo.listName }}</span>
        <el-tag size="small" type="info">{{ entityName }}</el-tag>
        <el-tag :type="diffInfo.changed ? 'warning' : 'success'" effect="plain">
          {{ diffInfo.changed ? '草稿有未发布修改' : '已发布' }}
        </el-tag>
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
          v-if="!isSystemEntity"
          :disabled="!configInfo.id"
          @click="openListEventBindings"
        >
          事件绑定
        </el-button>
        <el-button :loading="savingAll" type="primary" @click="saveAll">
          保存全部
        </el-button>
        <el-button
          :loading="runtimeCodeLoading"
          :disabled="pageLoading"
          @click="openRuntimeCode"
        >
          <el-icon><Document /></el-icon>查看最终代码
        </el-button>
        <el-button @click="showReleaseHistory">版本</el-button>
        <el-button
          :disabled="!entityCode || !configInfo.listKey"
          @click="openPreview"
        >
          预览
        </el-button>
        <el-button type="success" plain @click="handlePublish">发布生效</el-button>
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

    <div v-loading="pageLoading" class="design-container">
      <div ref="configPanelRef" class="config-panel">
        <el-card shadow="never">
          <el-tabs v-model="activeConfigTab" type="border-card" class="config-tabs">
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
                  title="列表设置可独立保存；列、按钮和场景的修改不会被一并覆盖。"
                  type="info"
                  :closable="false"
                  show-icon
                />
                <el-button
                  type="success"
                  plain
                  :loading="queryBindingSaving"
                  @click="saveListMetadata"
                >
                  保存列表设置
                </el-button>
              </div>
              <el-form label-width="120px" size="small" class="view-config-form">
                <SettingsSection
                  title="常用体验"
                  description="查询区域、表格样式和分页设置保存后可在实际列表页面确认"
                  :collapsible="false"
                  primary
                >
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
                  <el-form-item label="默认排序字段">
                    <el-select
                      v-model="viewConfig.table.defaultSortField"
                      clearable
                      filterable
                      placeholder="使用平台默认顺序"
                      style="width: 240px"
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
                      style="margin-left: 12px"
                    >
                      <el-radio-button value="ASC">升序</el-radio-button>
                      <el-radio-button value="DESC">降序</el-radio-button>
                    </el-radio-group>
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
                </SettingsSection>

                <SettingsSection
                  title="访问范围"
                  description="配置列表的权限边界、实体数据范围和可用场景"
                  :default-expanded="true"
                >
                  <template #summary>
                    {{ configInfo.dataScopeMode === 'INHERIT' ? '继承实体范围' : '列表自定义范围' }}
                  </template>
                  <el-form-item label="数据范围模式">
                    <template #label>
                      <ConfigHelpLabel
                        label="数据范围模式"
                        help-key="entityList.dataScopeMode"
                      />
                    </template>
                    <el-select v-model="configInfo.dataScopeMode" style="width: 420px">
                      <el-option label="继承实体默认范围" value="INHERIT" />
                      <el-option v-if="!isSystemEntity" label="在实体范围内缩小" value="NARROW" />
                      <el-option v-if="!isSystemEntity" label="使用列表独立范围（高风险）" value="OVERRIDE" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="访问权限码">
                    <el-input
                      v-model="configInfo.accessPermissionCode"
                      placeholder="留空继承 entity:{code}:list"
                      style="width: 420px"
                    />
                  </el-form-item>
                  <el-form-item label="允许场景" class="view-config-item--full">
                    <div class="scene-options">
                      <el-checkbox
                        v-for="scene in sceneOptions"
                        :key="scene.value"
                        :model-value="isSceneEnabled(scene.value)"
                        :disabled="sceneSavingCodes.has(scene.value)"
                        @change="toggleScene(scene.value, $event)"
                      >
                        {{ scene.label }}
                      </el-checkbox>
                    </div>
                    <el-text type="info" size="small">勾选后仅保存当前场景，发布后运行时生效。</el-text>
                  </el-form-item>
                </SettingsSection>

                <SettingsSection
                  title="选择行为"
                  description="配置列表是否允许选择，以及选择结果如何返回"
                  :default-expanded="true"
                >
                  <template #summary>
                    {{ configInfo.selectionMode === 'NONE' ? '仅浏览' : '返回选择结果' }}
                  </template>
                  <el-form-item label="选择模式">
                    <template #label>
                      <ConfigHelpLabel
                        label="选择模式"
                        help-key="entityList.selectionMode"
                      />
                    </template>
                    <el-radio-group v-model="configInfo.selectionMode">
                      <el-radio-button value="NONE">不选择</el-radio-button>
                      <el-radio-button value="SINGLE">单选</el-radio-button>
                      <el-radio-button value="MULTIPLE">多选</el-radio-button>
                    </el-radio-group>
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
                    label="返回映射 JSON"
                  >
                    <template #label>
                      <JsonConfigLabel
                        label="返回映射 JSON"
                        help-key="entityList.selectionReturnMappings"
                      />
                    </template>
                    <el-input
                      v-model="configInfo.selectionReturnMappingsText"
                      type="textarea"
                      :rows="3"
                      :placeholder="`例如 ${selectionReturnMappingExampleCompactText}`"
                    />
                  </el-form-item>
                </SettingsSection>

                <SettingsSection
                  title="查询实现"
                  description="配置固定条件、可信上下文，以及替代平台查询的接口服务"
                  :default-expanded="false"
                >
                  <template #summary>
                    {{ queryInterfaceSummary }}
                  </template>
                  <el-form-item label="固定条件 JSON">
                    <template #label>
                      <JsonConfigLabel
                        label="固定条件 JSON"
                        help-key="entityList.fixedFilters"
                      />
                    </template>
                    <el-input
                      v-model="configInfo.fixedFilterConfig"
                      type="textarea"
                      :rows="3"
                      placeholder='例如 {"status":"RUNNING","status_op":"EQ"}'
                    />
                  </el-form-item>
                  <el-form-item label="上下文绑定 JSON">
                    <template #label>
                      <JsonConfigLabel
                        label="上下文绑定 JSON"
                        help-key="entityList.contextBinding"
                      />
                    </template>
                    <el-input
                      v-model="configInfo.contextBindingConfig"
                      type="textarea"
                      :rows="3"
                      placeholder='扩展读取时例如 {"parentField":"project_id"}；默认查询请保持 {}'
                    />
                  </el-form-item>
                  <div
                    v-if="!isSystemEntity"
                    class="view-config-item--full list-query-interface"
                  >
                    <div class="list-query-interface__heading">
                      <div>
                        <div class="list-query-interface__title">列表查询接口</div>
                        <div class="list-query-interface__description">
                          直接配置会保存为 LIST_LOAD 的替代平台处理步骤。
                        </div>
                      </div>
                      <el-button @click="openListEventBindings">
                        高级事件链
                      </el-button>
                    </div>

                    <el-alert
                      v-if="listQueryBindingComplex"
                      title="当前 LIST_LOAD 使用了复杂事件链"
                      description="包含继承、多个步骤、条件或非标准执行位置，简化配置不会覆盖它，请进入高级事件链修改。"
                      type="warning"
                      :closable="false"
                      show-icon
                    />
                    <template v-else>
                      <el-alert
                        v-if="legacyQueryConfigured"
                        :title="legacyQueryConflict
                          ? '事件绑定已优先生效，仍检测到旧查询配置'
                          : '当前仍在使用旧查询配置'"
                        description="选择接口服务和查询操作后保存，将迁移为统一事件绑定并清除旧配置。"
                        type="warning"
                        :closable="false"
                        show-icon
                      >
                        <template #default>
                          <el-button
                            v-if="configInfo.queryDataSourceId"
                            size="small"
                            type="warning"
                            plain
                            @click="migrateLegacyQuerySource"
                          >
                            填入旧接口服务
                          </el-button>
                        </template>
                      </el-alert>
                      <el-alert
                        v-else-if="resolvedQuerySource === 'INHERITED' && !listQueryBinding"
                        title="当前继承实体级 LIST_LOAD 配置"
                        description="在这里选择接口后，将创建列表级覆盖；清空本级配置后恢复继承。"
                        type="info"
                        :closable="false"
                        show-icon
                      />

                      <div class="list-query-interface__grid">
                        <el-form-item label="接口服务">
                          <el-select
                            v-model="listQueryEditor.serviceId"
                            clearable
                            filterable
                            placeholder="留空使用继承配置或平台默认查询"
                            @change="handleQueryServiceChange"
                          >
                            <el-option
                              v-for="source in listQuerySources"
                              :key="source.id"
                              :label="`${source.sourceName} (${source.sourceCode})`"
                              :value="source.id"
                            />
                          </el-select>
                        </el-form-item>
                        <el-form-item label="查询操作">
                          <el-select
                            v-model="listQueryEditor.operationCode"
                            :disabled="!listQueryEditor.serviceId"
                            filterable
                            placeholder="选择只读查询操作"
                          >
                            <el-option
                              v-for="operation in queryOperationOptions"
                              :key="operation.code"
                              :label="`${operation.name} (${operation.code})`"
                              :value="operation.code"
                            />
                          </el-select>
                        </el-form-item>
                      </div>

                      <el-collapse
                        v-if="listQueryEditor.serviceId"
                        class="list-query-interface__mappings"
                      >
                        <el-collapse-item title="输入参数映射" name="input">
                          <div class="list-query-mapping-grid">
                            <label>查询条件</label>
                            <el-input
                              v-model="listQueryEditor.inputTargets.filters"
                              placeholder="接口参数名，例如 filters"
                            />
                            <label>当前页</label>
                            <el-input
                              v-model="listQueryEditor.inputTargets.pageNum"
                              placeholder="接口参数名，例如 pageNum"
                            />
                            <label>每页条数</label>
                            <el-input
                              v-model="listQueryEditor.inputTargets.pageSize"
                              placeholder="接口参数名，例如 pageSize"
                            />
                            <label>使用场景</label>
                            <el-input
                              v-model="listQueryEditor.inputTargets.scene"
                              placeholder="接口参数名，例如 scene"
                            />
                            <label>运行上下文</label>
                            <el-input
                              v-model="listQueryEditor.inputTargets.context"
                              placeholder="接口参数名，例如 context"
                            />
                          </div>
                        </el-collapse-item>
                        <el-collapse-item title="分页结果映射" name="output">
                          <el-text
                            class="list-query-mapping-hint"
                            type="info"
                            size="small"
                          >
                            接口已返回 records、total、pageNum、pageSize 时全部留空；仅在字段名不同或结果嵌套时配置。
                          </el-text>
                          <div class="list-query-mapping-grid">
                            <label>数据列表路径</label>
                            <el-input
                              v-model="listQueryEditor.outputPaths.records"
                              placeholder="例如 data.records 或 data.rows"
                            />
                            <label>总记录数路径</label>
                            <el-input
                              v-model="listQueryEditor.outputPaths.total"
                              placeholder="例如 data.total"
                            />
                            <label>当前页路径</label>
                            <el-input
                              v-model="listQueryEditor.outputPaths.pageNum"
                              placeholder="例如 data.pageNum 或 data.current"
                            />
                            <label>每页条数路径</label>
                            <el-input
                              v-model="listQueryEditor.outputPaths.pageSize"
                              placeholder="例如 data.pageSize 或 data.size"
                            />
                          </div>
                        </el-collapse-item>
                      </el-collapse>
                    </template>
                  </div>
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
                :entityCode="entityCode"
                :entity-id="entityId"
                :entityFields="entityFields"
                :owner-id="configInfo.id || configId"
                :templates="buttonTemplates"
                @save="saveListAction($event, 'TOOLBAR')"
                @reorder="reorderListAction($event, 'TOOLBAR')"
                @upgrade-template="upgradeButtonTemplate($event, 'TOOLBAR')"
                @remove="removeListAction($event, 'TOOLBAR')"
              />
            </el-tab-pane>
            <el-tab-pane v-if="!isSystemEntity" label="操作列按钮" name="rowActions">
              <ListButtonConfigPanel
                type="row"
                v-model="rowActionButtons"
                :entityCode="entityCode"
                :entity-id="entityId"
                :entityFields="entityFields"
                :owner-id="configInfo.id || configId"
                :templates="buttonTemplates"
                @save="saveListAction($event, 'ROW')"
                @reorder="reorderListAction($event, 'ROW')"
                @upgrade-template="upgradeButtonTemplate($event, 'ROW')"
                @remove="removeListAction($event, 'ROW')"
              />
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
              <el-form-item label="列宽">
                <el-input-number
                  v-model="editingField.width"
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
              <el-form-item label="最小宽度">
                <el-input-number v-model="editingColumnConfig.minWidth" :min="60" :max="1000" />
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
                      v-for="option in dataSourceOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                      :disabled="isVirtualField(editingField) && option.supportsVirtualField === false"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="统一数据源">
                  <el-select
                    v-model="editingField.dataSourceId"
                    clearable
                    filterable
                    placeholder="可选：LIST_COLUMN 数据源"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="source in listColumnSources"
                      :key="source.id"
                      :label="`${source.sourceName} (${source.sourceType})`"
                      :value="source.id"
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
                      v-for="option in cellComponentOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </el-form-item>
              </el-form>
              <ConfigSchemaEditor
                v-model="editingRenderConfig"
                :schema="selectedCellDescriptor?.configSchema || []"
              />
            </div>
          </SettingsSection>

          <SettingsSection
            title="高级模板"
            description="锁定模板版本，升级时保留本地覆盖"
            :default-expanded="false"
          >
            <el-form label-width="110px" size="small">
              <el-form-item label="模板">
                <div class="template-selector">
                  <el-select
                    v-model="editingField.templateId"
                    clearable
                    filterable
                    placeholder="复制后独立"
                    style="width: 100%"
                    @change="handleListTemplateChange"
                  >
                    <el-option
                      v-for="template in listTemplates"
                      :key="template.id"
                      :label="`${template.templateName} (v${template.currentVersion})`"
                      :value="template.id"
                    />
                  </el-select>
                  <el-button
                    v-if="editingField.templateId"
                    link
                    type="primary"
                    @click="upgradeListTemplate"
                  >
                    检查模板升级（当前 v{{ editingField.templateVersion || 1 }}）
                  </el-button>
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
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Document, Rank, Plus } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityApi } from '@/api/entity'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import ListCellRenderer from '@/components/ListCellRenderer.vue'
import ListButtonConfigPanel from '@/components/ListButtonConfigPanel.vue'
import EntityDataSearchForm from '@/views/entity/components/EntityDataSearchForm.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import ExtensionCapabilityPicker from '@/components/ExtensionCapabilityPicker.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import JsonConfigLabel from '@/components/JsonConfigLabel.vue'
import UiConfigPublishDialog from '@/components/UiConfigPublishDialog.vue'
import EventBindingDialog from '@/components/ui-config/EventBindingDialog.vue'
import UiConfigReleaseHistoryDialog from '@/components/ui-config/UiConfigReleaseHistoryDialog.vue'
import RuntimeCodeViewerDialog from '@/components/RuntimeCodeViewerDialog.vue'
import { getCellComponentOptions, getCellDescriptor } from '@/utils/listCellRegistry'
import { getCustomListComponentOptions, getCustomListDescriptor } from '@/utils/customComponentRegistry'
import { getFormFieldComponentOptions } from '@/components/form-fields'
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
  normalizeListActionForSave as normalizeActionForSave,
  resolveListButtonType,
  withListButtonTypeDefault
} from '@/shared/list-config-design'
import {
  buildListQueryBindingPayload,
  createListQueryEditor,
  findListLoadBinding,
  isSimpleListQueryBinding,
  listQueryEditorFingerprint
} from '@/shared/list-query-binding'
import {
  buildListDraftRuntimeSnapshot,
  buildRuntimeCodeArtifact,
  selectRuntimeRelease
} from '@/shared/runtime-code-generator'
import {
  uiDataSourceApi,
  uiEventBindingApi,
  uiComponentTemplateApi
} from '@/api/uiConfig'
import { serviceOperations } from '@/components/ui-config/interfaceServiceModel'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import { parseJsonConfig } from '@/utils/jsonConfig'
import {
  SELECTION_RETURN_MAPPING_EXAMPLE_COMPACT_TEXT
} from '@/utils/selectionReturnMappings'

const route = useRoute()
const router = useRouter()
const configId = route.params.id
const selectionReturnMappingExampleCompactText =
  SELECTION_RETURN_MAPPING_EXAMPLE_COMPACT_TEXT

// 配置信息
const configInfo = ref({})
const entityName = ref('')
const entityCode = ref('')
const entityId = ref('')
const entityFields = ref([])
const entityDefinition = ref({})
const isSystemEntity = computed(() => entityDefinition.value?.storageMode === 'SYSTEM')
const publishDialogVisible = ref(false)
const eventBindingDialogRef = ref(null)
const releaseHistoryDialogRef = ref(null)
const runtimeCodeDialogRef = ref(null)
const runtimeCodeLoading = ref(false)
const diffInfo = ref({ changed: true, changedSections: [] })
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
const unifiedDataSources = ref([])
const listQueryBinding = ref(null)
const resolvedListQueryBinding = ref({})
const listQueryEditor = ref(createListQueryEditor())
const listQueryBindingBaseline = ref('')
const queryBindingSaving = ref(false)
const eventFieldOptions = computed(() =>
  entityFields.value
    .filter(field => field.uiConfigurable !== false)
    .map(field => ({
      label: field.fieldName || field.fieldCode,
      value: field.fieldCode
    }))
)
const listTemplates = ref([])
const buttonTemplates = ref([])
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

const listColumnSources = computed(() =>
  unifiedDataSources.value.filter(source => source.enabled !== false)
)

const listQuerySources = computed(() =>
  unifiedDataSources.value.filter(source =>
    source.enabled !== false
      && serviceOperations(source).some(operation =>
        String(operation.kind || 'READ').toUpperCase() === 'READ'
      )
  )
)

const selectedQueryService = computed(() =>
  listQuerySources.value.find(source =>
    String(source.id) === String(listQueryEditor.value.serviceId))
)

const queryOperationOptions = computed(() =>
  selectedQueryService.value
    ? serviceOperations(selectedQueryService.value)
        .filter(operation =>
          String(operation.kind || 'READ').toUpperCase() === 'READ')
    : []
)

const listQueryBindingComplex = computed(() =>
  Boolean(listQueryBinding.value)
    && !isSimpleListQueryBinding(listQueryBinding.value)
)

const resolvedQuerySource = computed(() =>
  resolvedListQueryBinding.value?.source || 'PLATFORM'
)

const legacyQueryConfigured = computed(() =>
  Boolean(
    configInfo.value.queryDataSourceId
      || configInfo.value.queryProviderCode
  )
)

const legacyQueryConflict = computed(() =>
  legacyQueryConfigured.value
    && Boolean(resolvedListQueryBinding.value?.hasReplace)
)

const queryInterfaceSummary = computed(() => {
  if (listQueryBindingComplex.value) return '高级事件链'
  if (listQueryEditor.value.serviceId) {
    const operation = queryOperationOptions.value.find(item =>
      item.code === listQueryEditor.value.operationCode)
    return operation?.name
      || selectedQueryService.value?.sourceName
      || '自定义查询接口'
  }
  if (legacyQueryConfigured.value) return '旧查询配置'
  if (resolvedQuerySource.value === 'INHERITED') return '继承实体查询'
  return '平台默认查询'
})

// 工具栏按钮配置
const toolbarButtons = ref([])

// 操作列按钮配置
const rowActionButtons = ref([])
const sceneItems = ref([])
const sceneSavingCodes = ref(new Set())
const sceneSortCache = new Map()
const baselinesReady = ref(false)
const metadataBaseline = ref('')
const metadataDetailBaselines = ref(new Map())
const fieldBaselines = ref(new Map())
const actionBaselines = ref(new Map())
const sceneOptions = [
  { value: 'MENU', label: '菜单' },
  { value: 'PAGE', label: '页面' },
  { value: 'DIALOG', label: '弹窗' },
  { value: 'DRAWER', label: '抽屉' },
  { value: 'EMBEDDED', label: '页面嵌入' },
  { value: 'FORM_PICKER', label: '表单选择器' },
  { value: 'SUB_TABLE', label: '子表选择' }
]

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

function rememberMetadataBaseline() {
  metadataBaseline.value = listMetadataFingerprint(configInfo.value, viewConfig.value)
  metadataDetailBaselines.value = new Map(
    listMetadataDetailEntries(configInfo.value, viewConfig.value)
      .map(item => [item.key, JSON.stringify(item.value)])
  )
}

function rememberListQueryBindingBaseline() {
  listQueryBindingBaseline.value = listQueryEditorFingerprint(
    listQueryEditor.value,
    listQueryBindingComplex.value
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
  rememberListQueryBindingBaseline()
  fieldBaselines.value = new Map()
  fieldConfigList.value.forEach(rememberFieldBaseline)
  actionBaselines.value = new Map()
  toolbarButtons.value.forEach(button => rememberActionBaseline(button, 'TOOLBAR'))
  rowActionButtons.value.forEach(button => rememberActionBaseline(button, 'ROW'))
  baselinesReady.value = true
}

const metadataDirty = computed(() =>
  baselinesReady.value
    && metadataBaseline.value !== listMetadataFingerprint(configInfo.value, viewConfig.value)
)
const queryBindingDirty = computed(() =>
  baselinesReady.value
    && listQueryBindingBaseline.value !== listQueryEditorFingerprint(
      listQueryEditor.value,
      listQueryBindingComplex.value
    )
)
const dirtyMetadataItems = computed(() => {
  if (!metadataDirty.value) return []
  const items = listMetadataDetailEntries(configInfo.value, viewConfig.value)
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
    || queryBindingDirty.value
    || dirtyFields.value.length > 0
    || dirtyActions.value.length > 0
)
const unsavedItems = computed(() => [
  ...dirtyMetadataItems.value,
  ...(queryBindingDirty.value
    ? [{
        key: 'query-binding:LIST_LOAD',
        label: '列表设置：列表查询接口'
      }]
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
  message: '列表设置、查询接口、字段或按钮有未保存修改，离开后这些修改将丢失。'
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

async function loadData() {
  pageLoading.value = true
  loadError.value = ''
  baselinesReady.value = false
  try {
    const [extensionOptions, sourceCatalog, templates, buttons] = await Promise.all([
      entityListConfigApi.getExtensionOptions().catch(() => []),
      uiDataSourceApi.list().catch(() => []),
      uiComponentTemplateApi.list({ templateType: 'LIST_COLUMN_GROUP' }).catch(() => []),
      uiComponentTemplateApi.list({ templateType: 'BUTTON_GROUP' }).catch(() => [])
    ])
    unifiedDataSources.value = Array.isArray(sourceCatalog) ? sourceCatalog : []
    listTemplates.value = Array.isArray(templates) ? templates : []
    buttonTemplates.value = Array.isArray(buttons) ? buttons : []
    if (Array.isArray(extensionOptions) && extensionOptions.length > 0) {
      dataSourceOptions.value = extensionOptions
    }

    // 加载列表配置
    const [configRes, scenes] = await Promise.all([
      entityListConfigApi.getById(configId),
      entityListConfigApi.getScenes(configId).catch(() => [])
    ])
    sceneItems.value = Array.isArray(scenes) ? scenes : []
    sceneItems.value.forEach(scene => {
      sceneSortCache.set(scene.sceneCode, scene.sortOrder)
    })
    if (configRes) {
      configInfo.value = configRes
      configInfo.value.dataScopeMode = configRes.dataScopeMode || 'INHERIT'
      configInfo.value.fixedFilterConfig = JSON.stringify(
        configRes.fixedFilterConfig || {},
        null,
        2
      )
      configInfo.value.contextBindingConfig = JSON.stringify(
        configRes.contextBindingConfig || {},
        null,
        2
      )
      configInfo.value.allowedSceneValues = sceneItems.value.length > 0
        ? sceneItems.value.map(scene => scene.sceneCode)
        : safeJsonParse(configRes.allowedScenes)
          || ['MENU', 'PAGE', 'DIALOG', 'DRAWER', 'EMBEDDED', 'FORM_PICKER', 'SUB_TABLE']
      const selectionConfig = safeJsonParse(configRes.selectionConfig) || {}
      configInfo.value.selectionMode = selectionConfig.selectionMode || 'NONE'
      configInfo.value.selectionValueField = selectionConfig.valueField || 'id'
      configInfo.value.selectionReturnMappingsText = JSON.stringify(
        selectionConfig.returnMappings || [],
        null,
        2
      )
      entityId.value = configRes.entityId
      entityCode.value = configRes.entityCode
      viewConfig.value = mergeViewConfig(safeParseConfig(configRes.viewConfig))
      await loadDiff()
    }

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
        configInfo.value.queryDataSourceId = null
        dataSourceOptions.value = dataSourceOptions.value.filter(option =>
          ['ENTITY_FIELD', 'REFERENCE'].includes(option.value)
        )
      }
    }

    await loadListQueryBinding()

    // 合并字段配置
    mergeFieldConfig(configRes?.fields || [])

    // 解析按钮配置
    parseButtonConfig(configRes)
    await nextTick()
    rememberAllBaselines()
    refreshFieldTableLayout()
    initSortable()

  } catch (e) {
    console.error('加载数据失败:', e)
    loadError.value = e?.message || '列表配置加载失败，请重试'
  } finally {
    pageLoading.value = false
  }
}

async function loadListQueryBinding(options = {}) {
  const ownerId = String(configInfo.value.id || configId || '')
  if (!ownerId || isSystemEntity.value) {
    listQueryBinding.value = null
    resolvedListQueryBinding.value = { source: 'PLATFORM', steps: [] }
    listQueryEditor.value = createListQueryEditor()
    if (options.rememberBaseline) rememberListQueryBindingBaseline()
    return
  }
  const [bindings, resolved] = await Promise.all([
    uiEventBindingApi.list('LIST', ownerId).catch(() => []),
    uiEventBindingApi.resolveDraft('LIST', ownerId, 'LIST_LOAD')
      .catch(() => ({ source: 'PLATFORM', steps: [] }))
  ])
  listQueryBinding.value = findListLoadBinding(
    Array.isArray(bindings) ? bindings : []
  )
  resolvedListQueryBinding.value = resolved || {
    source: 'PLATFORM',
    steps: []
  }
  listQueryEditor.value = createListQueryEditor(listQueryBinding.value)
  if (options.rememberBaseline) rememberListQueryBindingBaseline()
}

function handleQueryServiceChange(serviceId) {
  if (!serviceId) {
    listQueryEditor.value.operationCode = ''
    return
  }
  const operations = queryOperationOptions.value
  listQueryEditor.value.operationCode = operations.length === 1
    ? operations[0].code
    : ''
}

function migrateLegacyQuerySource() {
  const source = listQuerySources.value.find(item =>
    String(item.id) === String(configInfo.value.queryDataSourceId))
  if (!source) {
    ElMessage.warning('旧接口服务不存在或未启用，请重新选择接口服务')
    return
  }
  listQueryEditor.value.serviceId = source.id
  const operations = serviceOperations(source)
    .filter(operation =>
      String(operation.kind || 'READ').toUpperCase() === 'READ')
  listQueryEditor.value.operationCode = operations.length === 1
    ? operations[0].code
    : ''
  if (operations.length > 1) {
    ElMessage.info('旧接口服务包含多个查询操作，请选择本列表使用的操作')
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
      dataSourceId: saved?.dataSourceId || '',
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
  const defaultSource = dataSourceOptions.value.find(option => option.supportsVirtualField !== false)
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
    dataSourceId: ''
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
    ...safeParseConfig(field.columnConfig)
  }
  activeFieldConfigTab.value = 'common'
  fieldConfigDialogVisible.value = true
}

async function handleListTemplateChange(templateId) {
  if (!editingField.value || !templateId) {
    if (editingField.value) {
      editingField.value.templateVersion = null
      editingField.value.localOverridesDocument = ''
    }
    return
  }
  const template = listTemplates.value.find(item => item.id === templateId)
  const versions = await uiComponentTemplateApi.versions(templateId)
  const latest = versions.find(item => item.version === template?.currentVersion)
    || versions[0]
  if (!latest) return
  const snapshot = safeParseConfig(latest.snapshotDocument)
  Object.assign(editingField.value, snapshot.field || snapshot)
  editingField.value.templateVersion = latest.version
  editingField.value.localOverridesDocument = stringifyConfig({})
  ElMessage.success(`已锁定列模板 v${latest.version}`)
}

async function upgradeListTemplate() {
  const field = editingField.value
  if (!field?.templateId) return
  const template = listTemplates.value.find(item => item.id === field.templateId)
  if (!template || template.currentVersion === field.templateVersion) {
    ElMessage.info('当前已是最新模板版本')
    return
  }
  const result = await uiComponentTemplateApi.upgrade(field.templateId, {
    fromVersion: field.templateVersion,
    toVersion: template.currentVersion,
    currentSnapshot: normalizeFieldForSave(field),
    localOverrides: safeParseConfig(field.localOverridesDocument)
  })
  if (result.requiresConfirmation) {
    try {
      await ElMessageBox.confirm(
        `以下列配置同时被模板和本地修改：${result.conflicts.join('、')}。继续后保留本地列配置。`,
        '确认列模板升级',
        {
          type: 'warning',
          confirmButtonText: '保留本地配置并升级',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
  }
  Object.assign(field, result.mergedSnapshot?.field || result.mergedSnapshot || {})
  field.templateId = template.id
  field.templateVersion = template.currentVersion
  await saveCurrentField(field)
  ElMessage.success(`已保存列模板升级 v${template.currentVersion}`)
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

function isSceneEnabled(sceneCode) {
  return sceneItems.value.some(scene => scene.sceneCode === sceneCode)
}

async function toggleScene(sceneCode, enabled) {
  sceneSavingCodes.value = new Set([...sceneSavingCodes.value, sceneCode])
  try {
    const current = sceneItems.value.find(scene => scene.sceneCode === sceneCode)
    if (enabled && !current) {
      await entityListConfigApi.createScene(configId, {
        sceneCode,
        sortOrder: sceneSortCache.get(sceneCode)
          ?? sceneOptions.findIndex(scene => scene.value === sceneCode)
      })
    } else if (!enabled && current) {
      sceneSortCache.set(sceneCode, current.sortOrder)
      await entityListConfigApi.deleteScene(configId, current.id, current.revision)
    }
    sceneItems.value = await entityListConfigApi.getScenes(configId)
    sceneItems.value.forEach(item => {
      sceneSortCache.set(item.sceneCode, item.sortOrder)
    })
    configInfo.value.allowedSceneValues = sceneItems.value.map(scene => scene.sceneCode)
    await refreshConfigRevision()
    await loadDiff()
    ElMessage.success(`场景“${sceneOptions.find(scene => scene.value === sceneCode)?.label || sceneCode}”已保存，尚未发布`)
  } catch (error) {
    handleRevisionConflict(error)
    sceneItems.value = await entityListConfigApi.getScenes(configId).catch(() => sceneItems.value)
  } finally {
    const next = new Set(sceneSavingCodes.value)
    next.delete(sceneCode)
    sceneSavingCodes.value = next
  }
}

async function upgradeButtonTemplate(button, position) {
  if (!button?.templateId) return
  const template = buttonTemplates.value.find(item => item.id === button.templateId)
  if (!template || template.currentVersion === button.templateVersion) {
    ElMessage.info('当前已是最新按钮模板版本')
    return
  }
  const currentSnapshot = {
    ...button
  }
  delete currentSnapshot.id
  delete currentSnapshot.revision
  delete currentSnapshot.orderKey
  delete currentSnapshot._saving
  const result = await uiComponentTemplateApi.upgrade(button.templateId, {
    fromVersion: button.templateVersion,
    toVersion: template.currentVersion,
    currentSnapshot,
    localOverrides: safeJsonParse(
      button.localOverridesDocument || button.localOverrides
    ) || {}
  })
  if (result.requiresConfirmation) {
    try {
      await ElMessageBox.confirm(
        `以下按钮配置同时被模板和本地修改：${result.conflicts.join('、')}。继续后保留本地按钮配置。`,
        '确认按钮模板升级',
        {
          type: 'warning',
          confirmButtonText: '保留本地配置并升级',
          cancelButtonText: '取消'
        }
      )
    } catch {
      return
    }
  }
  Object.assign(button, result.mergedSnapshot?.button || result.mergedSnapshot || {})
  button.templateId = template.id
  button.templateVersion = template.currentVersion
  await saveListAction(button, position)
  ElMessage.success(`已保存按钮模板升级 v${template.currentVersion}`)
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
    availabilityRule: availabilityRule || button.availabilityRule || {},
    templateId: saved.templateId || null,
    templateVersion: saved.templateVersion || null,
    localOverridesDocument: saved.localOverridesDocument || null
  })
}

async function saveListAction(button, position, options = {}) {
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
    dataSourceId: field.dataSourceId || null,
    renderComponent: field.renderComponent || '',
    formatter: field.formatter || '',
    columnConfig: field.columnConfig || '',
    queryConfig: field.queryConfig || '',
    renderConfig: field.renderConfig || '',
    sortOrder: Math.max(0, index),
    orderKey: field.orderKey || (Math.max(0, index) + 1) * 1000000,
    templateId: field.templateId || null,
    templateVersion: field.templateVersion || null,
    localOverridesDocument: field.localOverridesDocument || null
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
  }
  if (isVirtualField(field)) {
    const source = dataSourceOptions.value.find(option => option.value === field.dataSourceType)?.label
    if (source) parts.push(source)
  }
  return parts.join(' · ') || '未启用'
}

function handleRevisionConflict(error, target) {
  if (error?.status === 409 || error?.errorCode === 'CONFIG_REVISION_CONFLICT') {
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

async function saveListQueryBinding(options = {}) {
  if (listQueryBindingComplex.value) {
    ElMessage.warning('复杂 LIST_LOAD 执行链只能在高级事件绑定中修改')
    return false
  }
  queryBindingSaving.value = true
  try {
    const ownerId = String(configInfo.value.id || configId)
    if (listQueryEditor.value.serviceId) {
      if (!listQueryEditor.value.operationCode) {
        ElMessage.warning('请选择查询操作')
        return false
      }
      const payload = buildListQueryBindingPayload(
        listQueryEditor.value,
        ownerId,
        listQueryBinding.value
      )
      listQueryBinding.value = listQueryBinding.value?.id
        ? await uiEventBindingApi.update(
            listQueryBinding.value.id,
            payload
          )
        : await uiEventBindingApi.create(payload)
    } else if (listQueryBinding.value?.id) {
      await uiEventBindingApi.remove(
        listQueryBinding.value.id,
        listQueryBinding.value.revision
      )
      listQueryBinding.value = null
    }
    configInfo.value.queryDataSourceId = null
    configInfo.value.queryProviderCode = ''
    const resolved = await uiEventBindingApi.resolveDraft(
      'LIST',
      ownerId,
      'LIST_LOAD'
    ).catch(() => ({ source: 'PLATFORM', steps: [] }))
    resolvedListQueryBinding.value = resolved
    listQueryEditor.value = createListQueryEditor(
      listQueryBinding.value
    )
    rememberListQueryBindingBaseline()
    if (!options.silent) {
      ElMessage.success('列表查询接口已保存，尚未发布')
    }
    return true
  } catch (error) {
    handleRevisionConflict(error, listQueryBinding.value)
    await loadListQueryBinding({ rememberBaseline: true })
      .catch(() => {})
    return false
  } finally {
    queryBindingSaving.value = false
  }
}

async function saveListMetadata(options = {}) {
  try {
    if (queryBindingDirty.value) {
      if (!await saveListQueryBinding({ silent: true })) return false
    }
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
        selectionMode: configInfo.value.selectionMode || 'NONE',
        valueField: configInfo.value.selectionValueField || 'id',
        returnMappings: parseJsonConfig(
          configInfo.value.selectionReturnMappingsText,
          {
            fieldName: '返回映射',
            expectedType: 'array',
            emptyValue: []
          }
        )
      },
      fixedFilterConfig: parseJsonConfig(configInfo.value.fixedFilterConfig, {
        fieldName: '固定条件'
      }),
      contextBindingConfig: parseJsonConfig(configInfo.value.contextBindingConfig, {
        fieldName: '上下文绑定'
      }),
      viewConfig: viewConfig.value,
      queryProviderCode: isSystemEntity.value
        ? ''
        : configInfo.value.queryProviderCode || '',
      queryDataSourceId: isSystemEntity.value
        ? null
        : configInfo.value.queryDataSourceId || null
    })
    configInfo.value.revision = saved.revision
    rememberMetadataBaseline()
    await loadDiff()
    if (!options.silent) {
      ElMessage.success('列表设置已保存，尚未发布')
    }
    return true
  } catch (error) {
    handleRevisionConflict(error, configInfo.value)
    return false
  }
}

async function saveAll() {
  if (!isDirty.value) {
    ElMessage.info('当前没有未保存修改')
    return
  }
  savingAll.value = true
  let savedCount = 0
  try {
    if (metadataDirty.value || queryBindingDirty.value) {
      if (!await saveListMetadata({ silent: true })) return
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

async function loadDiff() {
  try {
    diffInfo.value = await entityListConfigApi.getDiff(configId)
  } catch {
    diffInfo.value = { changed: true, changedSections: [] }
  }
}

async function handlePublish() {
  if (isDirty.value) {
    ElMessage.warning('页面仍有未保存修改，请先保存全部后再发布')
    return
  }
  const diff = await entityListConfigApi.getDiff(configId)
  if (!diff.changed) {
    ElMessage.info('当前草稿与已发布版本一致')
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
    const eventBindings = mergeCurrentListQueryBinding(
      Array.isArray(savedBindings) ? savedBindings : [],
      ownerId
    )
    const runtimeAction = (button, position) => {
      const {
        expectedRevision,
        clearFields,
        ...action
      } = normalizeActionForSave(button, position)
      return action
    }
    const draftSnapshot = buildListDraftRuntimeSnapshot({
      list: configInfo.value,
      viewConfig: viewConfig.value,
      fields: fieldConfigList.value.map(normalizeFieldForSave),
      toolbarActions: toolbarButtons.value.map(button =>
        runtimeAction(button, 'TOOLBAR')
      ),
      rowActions: rowActionButtons.value.map(button =>
        runtimeAction(button, 'ROW')
      ),
      scenes: sceneItems.value,
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

function mergeCurrentListQueryBinding(bindings, ownerId) {
  if (!queryBindingDirty.value || listQueryBindingComplex.value) {
    return bindings
  }
  const currentIndex = bindings.findIndex(binding =>
    binding === findListLoadBinding(bindings)
  )
  if (!listQueryEditor.value.serviceId) {
    return currentIndex < 0
      ? bindings
      : bindings.filter((_, index) => index !== currentIndex)
  }
  const draftBinding = {
    ...(currentIndex >= 0 ? bindings[currentIndex] : {}),
    ...buildListQueryBindingPayload(
      listQueryEditor.value,
      ownerId,
      currentIndex >= 0 ? bindings[currentIndex] : null
    )
  }
  if (currentIndex < 0) return [...bindings, draftBinding]
  return bindings.map((binding, index) =>
    index === currentIndex ? draftBinding : binding
  )
}

function openListEventBindings() {
  if (queryBindingDirty.value) {
    ElMessage.warning('请先保存列表查询接口，再进入高级事件链')
    return
  }
  eventBindingDialogRef.value?.openOwner(configInfo.value.listName || '')
}

async function handleEventBindingsChanged() {
  await loadListQueryBinding({ rememberBaseline: true })
  await loadDiff()
}

async function handleReleaseChanged() {
  await refreshConfigRevision()
  await loadDiff()
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.entity-list-config-design {
  display: flex;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}
.page-header {
  display: flex;
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

.list-query-interface {
  min-width: 0;
  padding-top: 4px;
}

.list-query-interface__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.list-query-interface__title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.list-query-interface__description {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.list-query-interface :deep(.el-alert) {
  margin-bottom: 12px;
}

.list-query-interface__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
}

.list-query-interface__grid :deep(.el-select) {
  width: 100%;
}

.list-query-interface__mappings {
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.list-query-mapping-grid {
  display: grid;
  grid-template-columns: 112px minmax(180px, 1fr) 112px minmax(180px, 1fr);
  align-items: center;
  gap: 10px 12px;
  padding: 4px 0 8px;
}

.list-query-mapping-hint {
  display: block;
  margin-bottom: 10px;
}

.list-query-mapping-grid label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  text-align: right;
}

.view-config-form :deep(.settings-section__body > .el-form-item:last-child),
.field-config-tabs :deep(.settings-section__body > .el-form:last-child .el-form-item:last-child) {
  margin-bottom: 10px;
}

@media (min-width: 1440px) {
  .view-config-form > :deep(.settings-section > .settings-section__body) {
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

.template-selector {
  display: flex;
  width: 100%;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
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
.scene-options {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  width: 100%;
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

.system-config-alert {
  margin: 12px 12px 0;
}

.design-container {
  flex: 1;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 12px;
  overflow: auto;
}
.config-panel {
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
    display: block;
    overflow: auto;
    padding: 8px;
  }

  .config-panel {
    width: 100%;
    min-width: 0;
    overflow: visible;
  }

  .field-toolbar {
    align-items: stretch;
  }

  .list-query-interface__grid,
  .list-query-mapping-grid {
    grid-template-columns: 1fr;
  }

  .list-query-mapping-grid label {
    text-align: left;
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
