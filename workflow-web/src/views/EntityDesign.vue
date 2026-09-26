<template>
  <div class="entity-design">
    <div class="design-header">
      <div class="header-left">
        <span class="entity-name">{{ entityData.entityName || '实体设计' }}</span>
        <el-tag :type="isWorkflowEntityMode ? 'success' : 'info'" effect="plain">
          {{ isWorkflowEntityMode ? '流程实体' : '独立业务实体' }}
        </el-tag>
        <el-tag v-if="isSystemEntity" type="warning" effect="plain">平台系统表</el-tag>
        <el-tag v-else-if="isDirty" type="warning" effect="plain">未保存</el-tag>
        <el-tag v-else type="success" effect="plain">已保存</el-tag>
      </div>
      <el-tabs
        v-if="!loadError"
        v-model="activeDesignTab"
        class="design-tabs"
      >
        <el-tab-pane label="字段设计" name="fields" />
        <el-tab-pane v-if="!isSystemEntity" label="数据权限" name="permissions" />
        <el-tab-pane
          :label="relationCount ? `实体关系 ${relationCount}` : '实体关系'"
          name="relations"
        />
        <el-tab-pane
          v-if="canConfigureEntityDefaultEvents"
          label="默认事件"
          name="events"
        />
      </el-tabs>
    </div>

    <el-alert
      v-if="isSystemEntity"
      title="平台系统实体仅用于统一目录和结构查看"
      description="字段来自现有 sys_* 物理表并由系统自动同步。这里不能新增字段、发布 DDL、配置通用表单列表或绑定流程。"
      type="warning"
      :closable="false"
      show-icon
      class="system-entity-alert"
    />

    <PageState
      v-if="loadError"
      type="error"
      title="实体设计加载失败"
      :description="loadError"
      retryable
      @retry="initializeEntityDesign"
    />

    <div v-show="!loadError && activeDesignTab === 'fields'" class="design-body">
      <EntityFieldTypePanel
        v-if="!isSystemEntity"
        @add-field="handleAddField"
        @drag-start="handleDragStart"
      />

      <!-- 字段列表 -->
      <div class="fields-panel">
        <div class="panel-title">
          <div>
            <span>业务字段</span>
            <span class="panel-count">{{ businessFieldCount }}</span>
          </div>
          <div class="field-list-actions">
            <el-checkbox
              v-if="!isSystemEntity && systemFieldCount"
              v-model="showSystemFields"
              size="small"
            >
              系统字段 {{ systemFieldCount }}
            </el-checkbox>
            <el-button v-if="!isSystemEntity" type="primary" size="small" @click="handleAddField()">
              <el-icon><Plus /></el-icon>添加
            </el-button>
            <el-tooltip
              v-if="!isSystemEntity"
              content="批量保存字段修改及列表增删、排序；已单独保存的属性无需重复保存"
              placement="top"
            >
              <span>
                <el-button type="primary" size="small" :disabled="!isDirty" @click="handleSave">
                  <el-icon><Check /></el-icon>保存全部字段
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
        <div class="fields-list">
          <template
            v-for="(field, index) in displayFields"
            :key="field.id || index"
          >
            <div
              v-if="field.isSystem && (index === 0 || !displayFields[index - 1]?.isSystem)"
              class="field-section-label"
            >
              系统属性 · 编码与类型锁定
            </div>
            <div
              class="field-item"
              :class="{ active: selectedField === field }"
              @click="selectField(field)"
            >
              <div class="field-info">
                <span class="field-name">{{ field.fieldName || '未命名' }}</span>
                <span class="field-code">{{ field.fieldCode || '-' }}</span>
                <el-tag size="small" :type="getFieldTypeTag(field.fieldType)">
                  {{ getFieldTypeLabel(field.fieldType) }}
                </el-tag>
                <el-tag v-if="field.isRequired" type="danger" size="small" effect="plain">必填</el-tag>
                <el-tag v-if="field.isSystem" type="info" size="small" effect="plain">系统</el-tag>
                <el-tag v-if="field.isPublished" type="success" size="small" effect="plain" title="该字段已发布到数据库">已发布</el-tag>
                <el-tag v-else-if="!field.isSystem" type="warning" size="small" effect="plain">未发布</el-tag>
              </div>
              <div v-if="!isSystemEntity && !field.isSystem" class="field-actions">
                <el-tooltip content="上移字段" placement="top">
                  <el-button class="action-btn" text circle aria-label="上移字段" @click.stop="moveField(field, -1)">
                    <el-icon><ArrowUp /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="下移字段" placement="top">
                  <el-button class="action-btn" text circle aria-label="下移字段" @click.stop="moveField(field, 1)">
                    <el-icon><ArrowDown /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip v-if="!field.isPublished" content="删除未发布字段" placement="top">
                  <el-button class="action-btn delete" text circle aria-label="删除字段" @click.stop="deleteField(field)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 字段属性配置 -->
      <div class="property-panel" :class="{ 'readonly-panel': isSystemEntity }">
        <div class="panel-title">
          <span class="property-panel__title" :title="selectedFieldTitle">{{ selectedFieldTitle }}</span>
          <div v-if="selectedField" class="property-panel__tags">
            <el-tag size="small" :type="getFieldTypeTag(selectedField.fieldType)" effect="plain">
              {{ getFieldTypeLabel(selectedField.fieldType) }}
            </el-tag>
            <el-tag
              v-if="isSelectedFieldDirty"
              type="warning"
              size="small"
              effect="plain"
            >
              当前属性未保存
            </el-tag>
            <el-tag
              v-if="isSystemEntity"
              type="info"
              size="small"
              effect="plain"
            >
              平台系统表只读
            </el-tag>
            <el-tag
              v-else-if="selectedField.isSystem"
              type="info"
              size="small"
              effect="plain"
            >
              系统属性 · 编码与类型锁定
            </el-tag>
            <el-tag
              v-else-if="selectedField.isPublished"
              type="success"
              size="small"
              effect="plain"
            >
              已发布 · 编码与类型锁定
            </el-tag>
            <el-tag v-else type="warning" size="small" effect="plain">
              未发布 · 结构可编辑
            </el-tag>
          </div>
        </div>
        <el-form v-if="selectedField" :model="selectedField" label-width="90px" size="small">
          <SettingsSection
            title="常用属性"
            description="字段识别、类型和常用录入约束"
            :collapsible="false"
            primary
          >
            <el-form-item label="字段名称" required>
              <el-input v-model="selectedField.fieldName" placeholder="请输入字段名称" />
            </el-form-item>
            <template v-if="!isSelectedFieldStructureLocked">
              <el-form-item label="字段编码" required :error="selectedFieldCodeError">
                <el-input
                  v-model="selectedField.fieldCode"
                  placeholder="小驼峰命名，如 endTime"
                />
                <div v-if="!selectedFieldCodeError" class="form-tip">以小写英文字母开头，仅支持英文字母和数字。</div>
              </el-form-item>
              <el-form-item label="字段类型" required>
                <el-select
                  v-model="selectedField.fieldType"
                  placeholder="选择类型"
                  style="width: 100%"
                  @change="handleFieldTypeChange"
                >
                  <el-option
                    v-for="type in fieldTypes"
                    :key="type.value"
                    :label="type.label"
                    :value="type.value"
                  />
                </el-select>
              </el-form-item>
            </template>
            <el-form-item label="是否必填">
              <el-switch v-model="selectedField.isRequired" />
            </el-form-item>
            <el-form-item label="是否唯一">
              <el-switch v-model="selectedField.isUnique" />
            </el-form-item>
            <el-form-item label="默认值">
              <el-input
                v-model="selectedField.defaultValue"
                :placeholder="showOptions ? '请输入选项的 value 值（如 1）' : '请输入默认值'"
              />
              <div v-if="showOptions" class="form-tip">
                默认值应填写选项的 value（key），而非显示文本 label
              </div>
            </el-form-item>
          </SettingsSection>

          <SettingsSection
            :key="`data-${selectedField.id ?? selectedField.sortOrder ?? 'new'}-${selectedField.fieldType}`"
            title="数据与约束"
            description="数据库映射、容量、选项来源和验证规则"
            :default-expanded="showOptions"
          >
            <template #summary>
              <span v-if="showOptions">需配置选项</span>
              <span v-else-if="selectedField.fieldType === 'DECIMAL'">精度约束</span>
              <span v-else>按需配置</span>
            </template>

            <el-form-item label="数据库列名">
              <el-input
                :model-value="resolveEntityFieldColumnName(selectedField)"
                disabled
              />
            </el-form-item>

            <!-- 字段长度配置（文本等字符串类型） -->
            <el-form-item v-if="showFieldLength" label="字段长度">
              <el-input-number
                v-model="selectedField.fieldLength"
                :min="1"
                :max="4000"
                placeholder="默认200"
                style="width: 100%"
              />
              <div class="form-tip">对应数据库 VARCHAR 长度</div>
            </el-form-item>

            <!-- 小数精度配置（DECIMAL 类型） -->
            <template v-if="selectedField.fieldType === 'DECIMAL'">
              <el-form-item label="总位数">
                <el-input-number
                  v-model="selectedField.fieldLength"
                  :min="1"
                  :max="65"
                  placeholder="默认18"
                  style="width: 100%"
                />
                <div class="form-tip">DECIMAL 总位数（precision）</div>
              </el-form-item>
              <el-form-item label="小数位数">
                <el-input-number
                  v-model="selectedField.fieldPrecision"
                  :min="0"
                  :max="30"
                  placeholder="默认2"
                  style="width: 100%"
                />
                <div class="form-tip">DECIMAL 小数位数（scale）</div>
              </el-form-item>
            </template>

            <template v-if="showOptions">
              <el-form-item label="选项来源" required>
                <el-radio-group v-model="selectedField.optionSource">
                  <el-radio-button value="DICT">系统代码表</el-radio-button>
                  <el-radio-button value="LEGACY_INLINE" disabled>旧内嵌选项</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="selectedField.optionSource === 'DICT'" label="代码表" required>
                <el-select
                  v-model="selectedField.dictType"
                  filterable
                  placeholder="选择系统代码表"
                  style="width: calc(100% - 88px)"
                >
                  <el-option
                    v-for="dict in dictOptions"
                    :key="dict.dictCode"
                    :label="`${dict.dictName} (${dict.dictCode})`"
                    :value="dict.dictCode"
                  />
                </el-select>
                <el-button style="margin-left: 8px" @click="openQuickDictDialog">新建</el-button>
                <div class="form-tip">数据保存代码项编码，显示名称从代码表关联解析。</div>
              </el-form-item>
              <el-form-item v-else label="旧选项">
                <el-input v-model="optionsText" type="textarea" rows="4" disabled />
                <div class="form-tip text-warning">旧内嵌选项仅用于兼容，请迁移到系统代码表。</div>
              </el-form-item>
            </template>

            <EntityValidationRuleEditor
              v-model="selectedField.validateRules"
              :field-type="selectedField.fieldType"
            />
          </SettingsSection>

          <SettingsSection
            v-if="isSubForm || isSubList || isAttachment || isReference"
            :key="`type-${selectedField.id ?? selectedField.sortOrder ?? 'new'}-${selectedField.fieldType}`"
            title="类型专属配置"
            :description="isSubForm
              ? '关系已独立管理，此字段仅保留旧版展示兼容信息'
              : isSubList
                ? '配置要嵌入的目标实体与已发布列表'
              : isAttachment
                ? '配置附件项、格式与数量限制'
                : '配置单一目标实体与记录显示字段'"
            :default-expanded="true"
          >
            <template #summary>
              <span v-if="isSubForm">旧版子表单字段</span>
              <span v-else-if="isSubList">子列表引用</span>
              <span v-else-if="isAttachment">附件规则</span>
              <span v-else>实体记录引用</span>
            </template>

            <!-- 子表单配置 -->
            <template v-if="isSubForm">
              <el-alert
                title="子表单已改为页面组件"
                description="在实体关系中定义组成关系，再从表单设计左侧添加子表单或明细编辑；数据随主表统一保存。"
                type="info"
                :closable="false"
                show-icon
              />
              <el-form-item v-if="selectedField.relationCode" label="旧关系绑定">
                <el-input
                  :model-value="`${selectedField.relationName || selectedField.relationCode} (${selectedField.relationCode})`"
                  disabled
                />
                <div class="form-tip">仅用于兼容历史 SUB_FORM 承载字段，关系配置以独立关系定义为准。</div>
              </el-form-item>
              <el-button type="primary" text @click="activeDesignTab = 'relations'">
                前往实体关系管理
              </el-button>
            </template>

            <!-- 展示组件统一在页面引用关系，旧字段仅保留识别提示。 -->
            <template v-if="isSubList">
              <el-alert title="子列表已改为页面组件" description="先在实体关系中定义关联，再到表单或列表的关联内容中选择目标列表；实体字段不再配置展示页面。" type="info" :closable="false" show-icon />
              <el-button type="primary" link @click="activeDesignTab = 'relations'">前往实体关系管理</el-button>
            </template>

            <!-- 附件配置 -->
            <template v-if="isAttachment">
              <div v-for="(item, index) in selectedField.fileItems" :key="index" class="file-item-config">
                <div class="file-item-header">
                  <span class="file-item-title">附件项 {{ index + 1 }}</span>
                  <el-button type="danger" size="small" text @click="removeFileItem(index)">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </div>
                <el-form-item label="项名称">
                  <el-input v-model="item.itemName" placeholder="如：项目章程、需求文档" />
                </el-form-item>
                <el-form-item label="是否必填">
                  <el-switch v-model="item.required" />
                  <div class="form-tip">开启后，该附件项至少需要上传一个文件。</div>
                </el-form-item>
                <el-form-item label="文件类型">
                  <el-select
                    v-model="item.fileTypes"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    placeholder="选择或输入扩展名，如 .pdf、dwg"
                    style="width: 100%"
                    @change="normalizeFileItemTypes(item)"
                  >
                    <el-option-group
                      v-for="group in attachmentFileTypeGroups"
                      :key="group.label"
                      :label="group.label"
                    >
                      <el-option
                        v-for="type in group.options"
                        :key="type"
                        :label="type"
                        :value="type"
                      />
                    </el-option-group>
                  </el-select>
                  <div class="form-tip">可直接输入自定义扩展名并回车；不选表示允许所有类型。</div>
                </el-form-item>
                <el-form-item label="单文件大小">
                  <el-input-number
                    v-model="item.maxSize"
                    :min="1"
                    :max="100"
                    placeholder="MB"
                    style="width: 150px"
                  />
                  <span class="unit-text">MB</span>
                </el-form-item>
                <el-form-item label="数量限制">
                  <el-input-number
                    v-model="item.maxCount"
                    :min="1"
                    :max="20"
                    placeholder="个"
                    style="width: 150px"
                  />
                  <span class="unit-text">个</span>
                </el-form-item>
              </div>
              <el-button type="primary" size="small" text @click="addFileItem">
                <el-icon><Plus /></el-icon> 添加附件项
              </el-button>
            </template>

            <!-- 实体引用配置 -->
            <template v-if="isReference">
              <el-form-item label="目标实体" required>
                <EntityDefinitionPicker
                  v-model="selectedField.refEntityId"
                  placeholder="选择目标实体"
                  value-key="id"
                  title="选择目标实体"
                  :query="{ status: 'PUBLISHED' }"
                  :exclude-values="[String(entityId)]"
                  @change="onReferenceEntityChange"
                />
                <div class="form-tip">{{ getEntityReferenceSelectionHint(selectedField.fieldType) }}</div>
              </el-form-item>
              <el-form-item v-if="selectedField.refEntityId" label="显示字段">
                <el-select
                  v-model="selectedField.refFieldCode"
                  placeholder="默认使用 name"
                  style="width: 100%"
                  filterable
                >
                  <el-option
                    v-for="field in refEntityFields"
                    :key="field.fieldCode"
                    :label="`${field.fieldName || field.fieldCode} (${field.fieldCode})`"
                    :value="field.fieldCode"
                  />
                </el-select>
              </el-form-item>
            </template>
          </SettingsSection>
        </el-form>
        <div v-else class="empty-guide">
          <h3>{{ entityData.entityName || '实体结构' }}</h3>
          <p>{{ businessFieldCount }} 个业务字段，{{ systemFieldCount }} 个系统字段</p>
          <el-button v-if="!isSystemEntity" type="primary" @click="handleAddField()">
            <el-icon><Plus /></el-icon>添加业务字段
          </el-button>
          <el-button v-if="!isSystemEntity" @click="activeDesignTab = 'permissions'">
            <el-icon><Lock /></el-icon>配置数据权限
          </el-button>
        </div>
        <div v-if="selectedField && !isSystemEntity" class="property-panel__footer">
          <span class="property-panel__save-hint">只保存当前字段属性，其他未保存修改继续保留。</span>
          <el-tooltip
            content="只保存当前字段属性，不会提交其他字段或实体设置中的未保存修改"
            placement="top"
          >
            <span class="property-panel__save">
              <el-button
                type="primary"
                :loading="savingSelectedField"
                :disabled="!isSelectedFieldDirty"
                @click="handleSaveSelectedField"
              >
                <el-icon><Check /></el-icon>保存当前属性
              </el-button>
            </span>
          </el-tooltip>
        </div>
      </div>
    </div>

    <EntityRelationManagement
      v-if="!loadError && entityData.id"
      v-show="activeDesignTab === 'relations'"
      :entity-id="entityId"
      :can-manage="canManageEntityDefinition"
      :readonly-entity="isSystemEntity"
      @count-change="relationCount = $event"
    />

    <EntityDefaultEventPanel
      v-if="!loadError && canConfigureEntityDefaultEvents && activeDesignTab === 'events'"
      :entity-id="String(entityData.id || entityId)"
      :entity-name="entityData.entityName || entityData.entityCode || ''"
      :field-options="entityEventFieldOptions"
    />

    <!-- 数据权限直接作为页签内容展示，滚动范围独立于顶部导航。 -->
    <section
      v-if="!loadError && !isSystemEntity && activeDesignTab === 'permissions'"
      class="entity-permission-panel"
      aria-labelledby="entity-permission-title"
    >
      <div v-loading="permissionLoading" class="permission-panel-card">
        <h2 id="entity-permission-title">数据权限</h2>
        <el-alert type="info" :closable="false" style="margin-bottom: 16px">
          这里只维护规则目录。把规则绑到哪个列表，请到该列表的「访问范围」中设置。列表绑定保存后立即生效。列表未绑定任何允许规则时，将执行该列表配置的安全默认策略；新列表默认拒绝全部数据。
        </el-alert>
        <el-alert type="warning" :closable="false" style="margin-bottom: 16px">
          相关人只认 team 表已发生的参与；存在待办只认 process_task 未完成待办。列表分别绑定。尚未生成任务的下一审批人不会进入这两条规则。
        </el-alert>
        <div class="permission-header">
          <el-button type="primary" size="small" :disabled="permissionLoading || !entityData.entityCode" @click="handleAddPermission">
            <el-icon><Plus /></el-icon>添加规则
          </el-button>
        </div>
        <PageState
          v-if="permissionError"
          type="error"
          title="规则目录加载失败"
          :description="permissionError"
          retryable
          compact
          @retry="loadPermissions"
        />
        <template v-else>
          <el-table :data="permissionList" border size="small" style="margin-top: 12px">
            <el-table-column prop="ruleName" label="规则名称" width="140" />
            <el-table-column label="已绑定列表" min-width="160">
              <template #default="{ row }">
                <span>{{ formatBoundLists(row.boundListKeys) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="匹配范围" min-width="160">
              <template #default="{ row }">
                <span>{{ formatMatchSummary(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="效果" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.ruleEffect === 'ALLOW' ? 'success' : 'danger'" size="small">{{ row.ruleEffect === 'ALLOW' ? '允许' : '拒绝' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="数据范围" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="getFilterTypeTag(row.filterType)" size="small">{{ getFilterTypeLabel(row.filterType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="togglePermission(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" text @click="handleEditPermission(row)">编辑</el-button>
                <el-button size="small" text @click="handlePreviewPermissionSql(row)">模拟</el-button>
                <el-button type="danger" size="small" text @click="handleDeletePermission(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </section>

  </div>

  <!-- 规则编辑对话框 -->
  <el-dialog
    v-model="permissionEditVisible"
    :title="permissionForm.id ? '编辑规则' : '新增规则'"
    width="min(1200px, 92vw)"
    top="4vh"
    class="entity-permission-edit-dialog"
    :close-on-click-modal="false"
  >
    <el-form class="permission-edit-form" :model="permissionForm" label-width="100px" size="default">
      <SettingsSection
        title="基本规则"
        description="配置规则名称、允许或拒绝效果。绑定列表请到列表设置中完成"
        :collapsible="false"
        primary
      >
        <el-form-item label="规则名称" required>
          <el-input v-model="permissionForm.ruleName" placeholder="如：部门经理查看全部门数据" />
        </el-form-item>
        <el-form-item label="规则效果">
          <template #label>
            <ConfigHelpLabel
              label="规则效果"
              help-key="entity.permissionRuleEffect"
            />
          </template>
          <el-radio-group v-model="permissionForm.ruleEffect">
            <el-radio-button value="ALLOW">允许（放行并附加范围）</el-radio-button>
            <el-radio-button value="DENY">拒绝（排除数据范围）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="permissionForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </SettingsSection>

      <SettingsSection
        title="适用对象"
        description="定义哪些用户命中规则，可选用户/角色/部门，或手写 SQL"
      >
        <template #summary>
          {{ permissionForm.matchConditions?.length || 0 }} 个条件 ·
          {{ permissionForm.matchLogic === 'AND' ? '全部满足' : '满足任一' }}
        </template>

        <el-form-item label="逻辑关系">
          <template #label>
            <ConfigHelpLabel
              label="逻辑关系"
              help-key="entity.permissionMatchLogic"
            />
          </template>
          <el-radio-group v-model="permissionForm.matchLogic">
            <el-radio-button value="OR">满足任一条件</el-radio-button>
            <el-radio-button value="AND">满足所有条件</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <div v-for="(cond, index) in permissionForm.matchConditions" :key="index" class="condition-card">
          <div class="condition-header">
            <span>条件 {{ index + 1 }}</span>
            <el-button type="danger" size="small" text @click="removeMatchCondition(index)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </div>
          <el-form-item label="范围类型" required>
            <template #label>
              <ConfigHelpLabel
                label="范围类型"
                help-key="entity.permissionScopeType"
              />
            </template>
            <el-select v-model="cond.scopeType" placeholder="选择范围类型" style="width: 100%">
              <el-option label="全部用户" value="ALL_USERS" />
              <el-option label="指定用户" value="USER" />
              <el-option label="指定角色" value="ROLE" />
              <el-option label="指定用户组" value="GROUP" />
              <el-option label="指定部门" value="DEPT" />
              <el-option label="指定组织" value="ORG" />
              <el-option label="自定义 SQL" value="SQL" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="cond.scopeType === 'USER'" label="选择用户">
            <UserSelector
              v-model="cond.targetIds"
              multiple
              value-key="id"
              placeholder="请选择用户"
              title="选择适用用户"
            />
          </el-form-item>
          <el-form-item v-if="cond.scopeType === 'ROLE'" label="选择角色">
            <el-select
              v-model="cond.targetIds"
              multiple
              filterable
              clearable
              placeholder="请选择角色"
              style="width: 100%"
            >
              <el-option
                v-for="opt in roleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="['ROLE', 'GROUP'].includes(cond.scopeType)" label="匹配方式">
            <el-radio-group v-model="cond.operator">
              <el-radio value="ANY">满足任一项</el-radio>
              <el-radio value="ALL">满足全部项</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="cond.scopeType === 'GROUP'" label="选择用户组">
            <el-select
              v-model="cond.targetIds"
              multiple
              filterable
              clearable
              placeholder="请选择用户组"
              style="width: 100%"
            >
              <el-option
                v-for="opt in groupOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="['DEPT', 'ORG'].includes(cond.scopeType)" :label="cond.scopeType === 'DEPT' ? '选择部门' : '选择组织'">
            <el-select
              v-model="cond.targetIds"
              multiple
              filterable
              clearable
              :placeholder="cond.scopeType === 'DEPT' ? '请选择部门' : '请选择组织'"
              style="width: 100%"
            >
              <el-option
                v-for="opt in cond.scopeType === 'DEPT' ? deptOptions : organizationOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="['DEPT', 'ORG'].includes(cond.scopeType)" :label="cond.scopeType === 'DEPT' ? '包含子部门' : '包含下级组织'">
            <el-switch v-model="cond.includeSubDept" />
          </el-form-item>
          <el-form-item v-if="cond.scopeType === 'SQL'" label="用户 SQL" required>
            <template #label>
              <ConfigHelpLabel
                label="用户 SQL"
                help-key="entity.permissionMatchSql"
              />
            </template>
            <div class="permission-sql-editor">
              <el-alert type="info" :closable="false" class="permission-sql-help">
                <div>只写判断当前用户是否命中的条件，不要写完整 SELECT 语句。</div>
                <div>这里没有当前行，不能写主表别名 <code>biz</code>。</div>
                <div>示例：<code>#{userId} IN (SELECT user_id FROM special_auditors)</code></div>
                <div>或：<code>#{deptId} = 'D001'</code></div>
              </el-alert>
              <div class="sql-variable-tags">
                <el-tag
                  v-for="item in permissionSqlPlaceholders"
                  :key="item.token"
                  class="variable-tag"
                  @click="appendPermissionSql(cond, item.token)"
                >{{ item.label }} {{ item.token }}</el-tag>
              </div>
              <el-input
                v-model="cond.sql"
                type="textarea"
                :rows="4"
                class="permission-sql-input"
                placeholder="#{userId} IN (SELECT user_id FROM special_auditors)"
              />
            </div>
          </el-form-item>
        </div>
        <el-button type="primary" size="small" text @click="addMatchCondition">
          <el-icon><Plus /></el-icon>添加匹配条件
        </el-button>
      </SettingsSection>

      <SettingsSection
        title="可见数据范围"
        description="配置命中规则后可查看的数据。手写 SQL 时主表别名统一为 biz"
      >
        <template #summary>
          {{ getFilterTypeLabel(permissionForm.filterType) }}
          <span v-if="permissionForm.statusLimit.enabled"> · 已限制状态</span>
        </template>

        <el-form-item label="数据范围" required>
          <template #label>
            <ConfigHelpLabel
              label="数据范围"
              help-key="entity.permissionFilterSql"
            />
          </template>
          <el-select v-model="permissionForm.filterType" placeholder="选择数据范围" style="width: 100%">
            <el-option label="全部数据" value="ALL" />
            <el-option label="当前用户是创建人" value="PERSONAL" />
            <el-option label="当前用户是提交人" value="SUBMITTER" />
            <el-option label="当前用户存在待办（待办表，会签逐人）" value="HAS_TODO" />
            <el-option label="当前用户是相关人（参与过该记录）" value="TEAM" />
            <el-option
              v-if="permissionForm.filterType === 'CURRENT_ASSIGNEE'"
              label="当前用户是当前办理人（已停用，请改用存在待办）"
              value="CURRENT_ASSIGNEE"
            />
            <el-option label="本部门" value="DEPT" />
            <el-option label="本部门及子部门" value="DEPT_TREE" />
            <el-option label="结构化条件组" value="RULE" />
            <el-option label="自定义 SQL" value="SQL" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="permissionForm.filterType === 'RULE'" label="条件规则">
          <div style="width: 100%">
            <el-alert
              type="info"
              :closable="false"
              title="条件组由后端编译为安全 SQL。需要手写条件时，请改用「自定义 SQL」范围。"
              style="margin-bottom: 10px"
            />
            <!-- 数据范围需编译为 SQL，不能提供仅支持按钮条件判定的扩展选项。 -->
            <ActionRuleGroupEditor
              v-if="permissionForm.filterRoot"
              :node="permissionForm.filterRoot"
              :fields="permissionRuleFieldOptions"
              :statuses="availableStatuses"
              :allow-custom-conditions="false"
            />
            <el-button v-else type="primary" text @click="createPermissionFilterRoot">添加条件组</el-button>
            <el-button v-if="permissionForm.filterRoot" type="danger" text @click="permissionForm.filterRoot = null">清空条件</el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="permissionForm.filterType === 'SQL'" label="范围 SQL" required>
          <div class="permission-sql-editor">
            <el-alert type="info" :closable="false" class="permission-sql-help">
              <div>只写 WHERE 条件，不要写 SELECT / UPDATE 完整语句。</div>
              <div>主表别名统一写 <code>biz</code>，保存后会替换成该实体物理表，例如 <code>biz.create_by = #{userId}</code>。</div>
              <div>关联其它表时用 EXISTS，当前行用 <code>biz.id</code>。不要把其它表也起名为 biz。</div>
              <div>示例：<code>EXISTS (SELECT 1 FROM extra_acl t WHERE t.record_id = biz.id AND t.user_id = #{userId})</code></div>
            </el-alert>
            <div class="sql-variable-tags">
              <el-tag class="variable-tag" @click="appendPermissionSql('filter', 'biz.')">主表别名 biz.</el-tag>
              <el-tag
                v-for="item in permissionSqlPlaceholders"
                :key="item.token"
                class="variable-tag"
                @click="appendPermissionSql('filter', item.token)"
              >{{ item.label }} {{ item.token }}</el-tag>
            </div>
            <el-input
              v-model="permissionForm.filterSql"
              type="textarea"
              :rows="5"
              class="permission-sql-input"
              placeholder="biz.create_by = #{userId}"
            />
          </div>
        </el-form-item>

        <el-form-item label="状态限制">
          <el-switch v-model="permissionForm.statusLimit.enabled" />
          <span style="margin-left: 8px">启用状态过滤</span>
        </el-form-item>
        <template v-if="permissionForm.statusLimit.enabled">
          <el-form-item label="限制模式">
            <el-radio-group v-model="permissionForm.statusLimit.mode">
              <el-radio-button value="IN">允许以下状态</el-radio-button>
              <el-radio-button value="NOT_IN">排除以下状态</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="状态值">
            <el-select v-model="permissionForm.statusLimit.values" multiple placeholder="选择状态" style="width: 100%">
              <el-option v-for="status in availableStatuses" :key="status.statusCode" :label="status.statusName" :value="status.statusCode" />
            </el-select>
          </el-form-item>
        </template>
      </SettingsSection>
    </el-form>

    <template #footer>
      <el-button @click="permissionEditVisible = false">取消</el-button>
      <el-button type="primary" @click="savePermission">保存规则草稿</el-button>
    </template>
  </el-dialog>

  <!-- 单条规则模拟不等同于列表最终权限；未绑定、停用和适用对象不匹配仍可检查条件。 -->
  <el-dialog v-model="permissionSqlPreviewVisible" :title="permissionSqlPreviewTitle" width="700px">
    <el-form label-width="90px" @submit.prevent>
      <el-form-item label="模拟用户">
        <UserSelector
          v-model="simulationUserId"
          placeholder="默认当前登录用户，可选择其他人员"
          title="选择模拟用户"
          value-key="id"
          @change="loadPermissionPreview"
        />
      </el-form-item>
    </el-form>
    <div v-loading="permissionPreviewLoading" style="min-height: 100px">
      <PageState
        v-if="permissionPreviewError"
        type="error"
        title="规则模拟失败"
        :description="permissionPreviewError"
        retryable
        compact
        @retry="loadPermissionPreview"
      />
      <template v-else-if="permissionSqlPreview">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          以下是当前规则以模拟用户生成的数据条件。列表实际可见范围还取决于绑定规则、默认策略和范围绕过权限。
        </el-alert>
        <el-alert v-if="!permissionSqlPreview.enabled" type="warning" :closable="false" style="margin-bottom: 12px">
          该规则已停用，以下 SQL 仅供检查规则配置。
        </el-alert>
        <el-alert v-if="!permissionSqlPreview.audienceMatched" type="warning" :closable="false" style="margin-bottom: 12px">
          模拟用户不符合该规则的匹配范围，以下 SQL 仅展示数据条件，不表示该规则对模拟用户生效。
        </el-alert>
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="规则名称">{{ permissionSqlPreview.ruleName }}</el-descriptions-item>
          <el-descriptions-item label="模拟用户">{{ permissionSqlPreview.username || permissionSqlPreview.userId }}</el-descriptions-item>
          <el-descriptions-item label="效果">{{ permissionSqlPreview.ruleEffect === 'DENY' ? '拒绝符合条件的数据' : '允许符合条件的数据' }}</el-descriptions-item>
          <el-descriptions-item label="匹配范围">{{ permissionSqlPreview.audienceMatched ? '模拟用户符合' : '模拟用户不符合' }}</el-descriptions-item>
        </el-descriptions>

        <div class="preview-section">
          <div class="preview-section-title">当前规则 SQL</div>
          <el-input v-model="permissionSqlPreview.sql" type="textarea" :rows="4" readonly />
        </div>
      </template>
    </div>
  </el-dialog>

  <QuickDictDialog
    v-model="quickDictVisible"
    :dict-name="quickDictForm.dictName"
    :dict-code="quickDictForm.dictCode"
    @created="handleQuickDictCreated"
  />
</template>

<script setup>
import { showRequestError } from '@/shared/request'

import { useEntityPermissions } from './entity-design/useEntityPermissions.js'

import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { entityApi } from '@/api/entity'

import { getDictList } from '@/api/system/dict'
import { useUserStore } from '@/stores/user'
import EntityFieldTypePanel from '@/views/entity/components/EntityFieldTypePanel.vue'
import ActionRuleGroupEditor from '@/components/ActionRuleGroupEditor.vue'
import UserSelector from '@/components/UserSelector.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import EntityDefaultEventPanel from '@/views/entity/components/EntityDefaultEventPanel.vue'
import EntityRelationManagement from '@/views/entity/components/EntityRelationManagement.vue'
import QuickDictDialog from '@/views/entity/components/QuickDictDialog.vue'
import EntityValidationRuleEditor from '@/components/EntityValidationRuleEditor.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import PageState from '@/components/PageState.vue'
import { useEntityFieldDraftSave } from '@/composables/useEntityFieldDraftSave'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import { useEntityValidationRules } from '@/composables/useEntityValidationRules'
import { normalizeAttachmentFileTypes } from '@flow/workflow-core/file-attachment'
import { ENTITY_DESIGN_FIELD_TYPES, filterEntityFieldsByLifecycle, resolveEntityFieldColumnName, getEntityFieldTypeLabel, getEntityFieldTypeTag, getEntityReferenceSelectionHint } from '@/shared/entity-design'

const route = useRoute()
const userStore = useUserStore()
const entityId = route.params.id

/**
 * 解析实体设计深链页签；未知值回退到字段设计，避免外部入口打开空白区域。
 */
function normalizeEntityDesignTab(value) {
  const tab = String(value || '').trim().toLowerCase()
  return ['fields', 'relations', 'events', 'permissions'].includes(tab) ? tab : 'fields'
}

const activeDesignTab = ref(normalizeEntityDesignTab(route.query.tab))
const relationCount = ref(0)
const canManageEntityDefinition = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('entity:definition:manage'))

// 字段类型定义
const fieldTypes = ENTITY_DESIGN_FIELD_TYPES

const entityData = ref({})
const fields = ref([])
const loadError = ref('')
const showSystemFields = ref(true)
const entityBaseline = ref('')
const selectedField = ref(null)
// 标题跟随当前字段及其草稿更新，未选中字段时保留通用标题。
const selectedFieldTitle = computed(() => selectedField.value
  ? `${selectedField.value.fieldName || '未命名字段'}（${selectedField.value.fieldCode || '尚未设置字段编码'}）`
  : '属性配置')
const isSystemEntity = computed(() => entityData.value?.storageMode === 'SYSTEM')
const canConfigureEntityDefaultEvents = computed(() => Boolean(entityData.value?.id)
  && canManageEntityDefinition.value
  && !isSystemEntity.value)
const { handleFieldTypeChange, validateFieldRules } =
  useEntityValidationRules(selectedField)
const {
  handleSaveSelectedField,
  isSelectedFieldDirty,
  normalizeFieldForEditing,
  normalizeFieldForSave,
  rememberAllFieldBaselines,
  savingSelectedField,
  selectedFieldCodeError,
  validateEntityField
} = useEntityFieldDraftSave({
  entityId,
  fields,
  selectedField,
  entityBaseline,
  isSystemEntity,
  validateFieldRules,
  onSaved: field => selectField(field)
})
const entityFingerprint = () => JSON.stringify({
  entity: {
    ...entityData.value,
    fields: undefined
  },
  fields: fields.value.map(normalizeFieldForSave)
})
const isDirty = computed(() =>
  Boolean(entityBaseline.value) && entityBaseline.value !== entityFingerprint()
)

useUnsavedChangesGuard(isDirty, {
  message: '实体字段或属性有未保存修改，离开后这些修改将丢失。'
})

const isWorkflowEntityMode = computed(() => entityData.value?.lifecycleMode === 'WORKFLOW')
// 实体设计展示完整字段结构，独立实体也可以查看数据库已有的流程系统列。
const businessFieldCount = computed(() => fields.value.filter(field => !field.isSystem).length)
const systemFieldCount = computed(() => fields.value.filter(field => field.isSystem).length)
// 事件绑定独立保存，只提供服务端已保存字段，避免把尚未保存的字段编码写入执行链。
const entityEventFieldOptions = computed(() => filterEntityFieldsByLifecycle(
  entityData.value,
  entityData.value?.fields || []
)
  .filter(field => field.fieldCode && !field.isSystem && field.uiConfigurable !== false)
  .map(field => ({
    label: field.fieldName || field.fieldCode,
    value: field.fieldCode
  })))
const displayFields = computed(() => {
  const businessFields = fields.value.filter(field => !field.isSystem)
  const systemFields = fields.value.filter(field => field.isSystem)
  return isSystemEntity.value || showSystemFields.value
    ? [...businessFields, ...systemFields]
    : businessFields
})
const isSelectedFieldStructureLocked = computed(() => Boolean(
  selectedField.value
  && (isSystemEntity.value || selectedField.value.isPublished || selectedField.value.isSystem)
))
const draggedType = ref(null)
const optionsText = ref('')
const refEntityFields = ref([])
const dictOptions = ref([])
const quickDictVisible = ref(false)
const quickDictForm = ref({ dictName: '', dictCode: '' })

const {
  permissionLoading,
  permissionList,
  permissionError,
  permissionEditVisible,
  permissionForm,
  availableStatuses,
  permissionSqlPreview,
  permissionSqlPreviewVisible,
  permissionSqlPreviewTitle,
  simulationUserId,
  permissionPreviewLoading,
  permissionPreviewError,
  roleOptions,
  groupOptions,
  deptOptions,
  organizationOptions,
  permissionRuleFieldOptions,
  loadPermissions,
  handleAddPermission,
  handleEditPermission,
  handleDeletePermission,
  togglePermission,
  addMatchCondition,
  permissionSqlPlaceholders,
  appendPermissionSql,
  removeMatchCondition,
  formatMatchSummary,
  getFilterTypeTag,
  getFilterTypeLabel,
  formatBoundLists,
  handlePreviewPermissionSql,
  loadPermissionPreview,
  createPermissionFilterRoot,
  savePermission
} = useEntityPermissions({ entityId, entityData, isSystemEntity, isWorkflowEntityMode, fields })

// 是否显示选项配置
const showOptions = computed(() => {
  return selectedField.value && ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(selectedField.value.fieldType)
})

// 是否显示字段长度配置（字符串相关类型）
const showFieldLength = computed(() => {
  return selectedField.value && ['STRING', 'TEXT', 'SELECT', 'RADIO', 'MULTI_SELECT', 'CHECKBOX', 'USER', 'DEPT', 'REFERENCE'].includes(selectedField.value.fieldType)
})

// 是否显示子表单配置
const isSubForm = computed(() => {
  return selectedField.value?.fieldType === 'SUB_FORM'
})

const isSubList = computed(() => {
  return selectedField.value?.fieldType === 'SUB_LIST'
})

// 是否显示附件配置
const isAttachment = computed(() => {
  return selectedField.value && ['FILE', 'IMAGE'].includes(selectedField.value.fieldType)
})

// 是否显示实体引用配置
const isReference = computed(() => {
  return selectedField.value && ['REFERENCE', 'MULTI_REFERENCE'].includes(selectedField.value.fieldType)
})

const loadDictOptions = async () => {
  try {
    dictOptions.value = await getDictList() || []
  } catch (error) {
    console.error('加载代码表失败:', error)
    dictOptions.value = []
  }
}

// 关联实体变化时加载字段
const onRefEntityChange = async (entityId) => {
  if (!entityId) {
    refEntityFields.value = []
    return
  }
  try {
    const data = await entityApi.getById(entityId)
    refEntityFields.value = data.fields || []
  } catch (error) {
    console.error('加载实体字段失败:', error)
    refEntityFields.value = []
  }
}

const onReferenceEntityChange = async (entityId) => {
  if (!selectedField.value) return
  selectedField.value.refEntityType = 'CUSTOM'
  selectedField.value.refFieldCode = ''
  await onRefEntityChange(entityId)
}

// 监听选项文本变化
watch(optionsText, (val) => {
  if (selectedField.value && showOptions.value) {
    const options = val.split('\n').map(line => {
      const [value, label] = line.split(':')
      return { value: value?.trim(), label: label?.trim() || value?.trim() }
    }).filter(opt => opt.value)
    selectedField.value.optionsJson = JSON.stringify(options)
  }
})

// 加载实体数据
const loadEntity = async () => {
  loadError.value = ''
  try {
    const data = await entityApi.getById(entityId)
    entityData.value = data
    fields.value = (data.fields || []).map(normalizeFieldForEditing)
    await nextTick()
    rememberAllFieldBaselines()
    entityBaseline.value = entityFingerprint()
  } catch (error) {
    console.error(error)
    loadError.value = error?.message || '无法读取实体结构，请检查权限或稍后重试。'
  }
}

const initializeEntityDesign = async () => {
  await loadEntity()
}

// 添加字段
const handleAddField = (type) => {
  const newField = {
    id: 'temp_' + Date.now(),
    fieldName: '',
    fieldCode: '',
    fieldType: type?.value || 'STRING',
    isRequired: false,
    isUnique: false,
    sortOrder: fields.value.length,
    optionSource: ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(type?.value) ? 'DICT' : undefined,
    dictType: ''
  }
  fields.value.push(newField)
  selectField(newField)
}

// 选择字段
const selectField = (field) => {
  selectedField.value = field
  refEntityFields.value = []
  if (showOptions.value) {
    field.optionSource = field.dictType ? 'DICT' : 'LEGACY_INLINE'
  }
  
  // FILE/IMAGE 字段自动初始化 fileItems
  if ((field.fieldType === 'FILE' || field.fieldType === 'IMAGE') && (!field.fileItems || field.fileItems.length === 0)) {
    field.fileItems = [{
      itemKey: createAttachmentItemKey(),
      itemName: field.fieldName || '附件',
      required: false,
      fileTypes: field.fileTypes || [],
      maxSize: field.fileMaxSize || 10,
      maxCount: field.fileMaxCount || 5
    }]
  }
  
  if (showOptions.value && field.optionsJson) {
    try {
      const options = JSON.parse(field.optionsJson)
      optionsText.value = options.map(opt => `${opt.value}:${opt.label}`).join('\n')
    } catch (e) {
      optionsText.value = ''
    }
  } else {
    optionsText.value = ''
  }
  
  if (isReference.value && field.refEntityId) {
    field.refEntityType = 'CUSTOM'
    onRefEntityChange(field.refEntityId)
  }
}

const openQuickDictDialog = () => {
  const fieldCode = selectedField.value?.fieldCode || ''
  quickDictForm.value = {
    dictName: selectedField.value?.fieldName || '',
    dictCode: fieldCode ? `${entityData.value.entityCode}_${fieldCode}`.toLowerCase() : ''
  }
  quickDictVisible.value = true
}

/** 新代码表创建后更新当前字段草稿，并刷新可选代码表；字段仍由原有保存流程持久化。 */
const handleQuickDictCreated = async (dict) => {
  selectedField.value.optionSource = 'DICT'
  selectedField.value.dictType = dict.dictCode
  selectedField.value.optionsJson = null
  await loadDictOptions()
}

// 删除字段
const deleteField = (field) => {
  const index = fields.value.indexOf(field)
  if (index < 0) return
  if (field.isPublished) {
    ElMessage.warning('已发布的字段不能删除，请先修改字段配置')
    return
  }
  fields.value.splice(index, 1)
  if (selectedField.value && !fields.value.find(f => f === selectedField.value)) {
    selectedField.value = null
  }
}

// 移动字段
const moveField = (field, direction) => {
  const index = fields.value.indexOf(field)
  if (index < 0) return
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= fields.value.length) return
  const temp = fields.value[index]
  fields.value[index] = fields.value[newIndex]
  fields.value[newIndex] = temp
  // 更新排序
  fields.value.forEach((f, i) => f.sortOrder = i)
}

// 获取字段类型标签
const getFieldTypeTag = (type) => {
  return getEntityFieldTypeTag(type)
}

const getFieldTypeLabel = (type) => {
  return getEntityFieldTypeLabel(type)
}

// 转换为表单字段格式
const convertToFormField = (field) => {
  return {
    fieldName: field.fieldName,
    fieldKey: field.fieldCode,
    fieldCode: field.fieldCode,
    fieldType: field.fieldType,
    isRequired: field.isRequired,
    defaultValue: field.defaultValue,
    optionsJson: field.optionsJson,
    // 子表单/实体引用相关属性
    refEntityId: field.refEntityId,
    refEntityType: field.refEntityType,
    refFieldCode: field.refFieldCode,
    refListKey: field.refListKey,
    childEntityId: field.childEntityId || field.refEntityId,
    childRefFieldCode: field.childRefFieldCode || field.refFieldCode,
    relationType: field.relationType,
    cascadeDelete: field.cascadeDelete,
    // 附件相关属性
    fileTypes: field.fileTypes,
    fileMaxSize: field.fileMaxSize,
    fileMaxCount: field.fileMaxCount
  }
}

// 保存
const handleSave = async (options = {}) => {
  const silent = options?.silent === true
  if (isSystemEntity.value) {
    ElMessage.warning('平台系统实体字段由数据库自动同步，不能在设计器中修改')
    return false
  }
  // 验证字段
  for (const field of fields.value) {
    if (!validateEntityField(field, true)) return false
  }

  try {
    await entityApi.update(entityId, {
      ...entityData.value,
      fields: fields.value.map(normalizeFieldForSave)
    })
    await loadEntity()
    if (!silent) ElMessage.success('实体配置保存成功')
    return true
  } catch (error) {
    console.error(error)
    showRequestError(error, '保存失败')
    return false
  }
}

const attachmentFileTypeGroups = [
  { label: '图片', options: ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp'] },
  { label: '文档', options: ['.pdf', '.doc', '.docx', '.ppt', '.pptx'] },
  { label: '表格', options: ['.xls', '.xlsx', '.csv'] },
  { label: '文本', options: ['.txt', '.md'] },
  { label: '压缩包', options: ['.zip', '.rar', '.7z', '.tar.gz'] }
]

const normalizeFileItemTypes = (item) => {
  item.fileTypes = normalizeAttachmentFileTypes(item.fileTypes)
}

// 添加附件项
const addFileItem = () => {
  if (!selectedField.value.fileItems) {
    selectedField.value.fileItems = []
  }
  selectedField.value.fileItems.push({
    itemKey: createAttachmentItemKey(),
    itemName: '',
    required: false,
    fileTypes: [],
    maxSize: 10,
    maxCount: 5
  })
}

// 删除附件项
const removeFileItem = (index) => {
  if (selectedField.value.fileItems) {
    selectedField.value.fileItems.splice(index, 1)
  }
}

// 拖拽开始
const handleDragStart = (type) => {
  draggedType.value = type
}

// 同时监听实体加载结果，确保直接通过 ?tab=permissions 进入时也能读取目录。
watch(
  [activeDesignTab, () => entityData.value.entityCode, isSystemEntity, loadError],
  ([tab, entityCode, systemEntity, error]) => {
    if (tab === 'permissions' && entityCode && !systemEntity && !error) {
      void loadPermissions()
    }
  },
  { immediate: true }
)

const createAttachmentItemKey = () => {
  const randomPart = typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID().replaceAll('-', '')
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`
  return `afi_${randomPart}`
}

watch(showSystemFields, (visible) => {
  if (!visible && selectedField.value?.isSystem && !isSystemEntity.value) {
    selectedField.value = null
  }
})

// 兼容页签深链；默认事件检查管理权限，系统实体不开放数据权限配置。
watch(() => route.query.tab, (value) => {
  const requestedTab = normalizeEntityDesignTab(value)
  activeDesignTab.value = entityData.value?.id
    && ((requestedTab === 'events' && !canConfigureEntityDefaultEvents.value)
      || (requestedTab === 'permissions' && isSystemEntity.value))
    ? 'fields'
    : requestedTab
})

watch(
  [() => Boolean(entityData.value?.id), canConfigureEntityDefaultEvents, isSystemEntity],
  ([entityLoaded, canConfigure, systemEntity]) => {
    if (entityLoaded && ((activeDesignTab.value === 'events' && !canConfigure)
      || (activeDesignTab.value === 'permissions' && systemEntity))) {
      activeDesignTab.value = 'fields'
    }
  }
)

onMounted(async () => {
  await Promise.all([
    initializeEntityDesign(),
    loadDictOptions()
  ])
})
</script>

<style scoped>
.entity-design {
  /* 页签内容共用上下间距，左右边界与顶部导航保持对齐。 */
  --entity-design-panel-gap: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

/* ===== 头部样式 ===== */
.design-header {
  min-height: 45px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  column-gap: 24px;
  padding: 0 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.header-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  min-width: 0;
  padding: 6px 0;
}

.entity-name {
  font-size: var(--el-font-size-base);
  font-weight: 600;
  color: #303133;
  overflow-wrap: anywhere;
}

/* ===== 主体布局 ===== */
.design-tabs {
  flex: 0 0 auto;
  max-width: 100%;
  margin-left: auto;
  background: #fff;
}

.design-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.design-tabs :deep(.el-tabs__item) {
  height: 45px;
}

.design-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.design-tabs :deep(.el-tabs__content) {
  display: none;
}

.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  padding: var(--entity-design-panel-gap) 0;
  gap: 16px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f2f5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* ===== 中间字段列表面板 ===== */
.fields-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.fields-panel .panel-title {
  margin: 0;
  padding: 16px 20px;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
  flex-wrap: wrap;
  gap: 8px;
}

.fields-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f8f9fa;
}

.field-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.field-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  transform: translateX(4px);
}

.field-item.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.2);
}

.field-info {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.field-info .field-name {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
  flex-shrink: 0;
}

.field-info .field-code {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.field-info .el-tag {
  flex-shrink: 0;
  border-radius: 4px;
  font-weight: 500;
}

.field-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  opacity: 1;
  align-items: center;
}

.action-btn {
  cursor: pointer;
  color: #409eff;
  font-size: 16px;
  padding: 6px;
  border-radius: 50%;
  transition: all 0.2s;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  min-height: 32px;
}

.action-btn:hover {
  background: #ecf5ff;
  border-color: #409eff;
  transform: scale(1.05);
}

.action-btn.delete {
  color: #f56c6c;
  background: #fef0f0;
  border-color: #fbc4c4;
}

.action-btn.delete:hover {
  background: #fde2e2;
  border-color: #f56c6c;
}

/* ===== 右侧属性面板 ===== */
.property-panel {
  /* 桌面端按视口分配三分之一宽度，剩余空间由业务字段列表自适应占用。 */
  width: calc(100vw / 3);
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.property-panel .panel-title {
  margin: 0;
  padding: 16px 20px;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.property-panel__title {
  flex: 1 1 160px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.property-panel__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  max-width: 100%;
  margin-left: auto;
  gap: 8px;
}

/* 保存操作独立于表单滚动区域，长配置内容不会将按钮推离面板底部。 */
.property-panel__footer {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.property-panel__save-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.property-panel__save {
  flex-shrink: 0;
}

.property-panel :deep(.el-form) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
}

.property-panel :deep(.el-form-item) {
  margin-bottom: 18px;
}

.property-panel :deep(.settings-section:last-child) {
  margin-bottom: 0;
}

.property-panel :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}

.system-entity-alert {
  margin: 12px 20px 0;
}

.readonly-panel :deep(.el-form) {
  pointer-events: none;
  opacity: 0.78;
}

.property-panel :deep(.el-input__inner),
.property-panel :deep(.el-textarea__inner) {
  border-radius: 8px;
  transition: all 0.3s;
}

.property-panel :deep(.el-input__inner:focus),
.property-panel :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.empty-guide {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  padding: 28px;
  text-align: center;
  color: #909399;
  font-size: 14px;
  background: #fafbfc;
}

.empty-guide h3,
.empty-guide p {
  margin: 0;
}

.empty-guide h3 {
  color: #303133;
}

.panel-count {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}

.field-list-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  max-width: 100%;
  margin-left: auto;
  gap: 8px;
}

.field-section-label {
  margin: 18px 0 10px;
  color: #909399;
  font-size: 12px;
  font-weight: 600;
}

/* ===== 滚动条美化 ===== */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #909399;
}

/* ===== 附件项配置样式 ===== */
.file-item-config {
  background: #f8f9fb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
}

.file-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.file-item-title {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

/* ===== 数据权限配置样式 ===== */
.entity-permission-panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: var(--entity-design-panel-gap) 0;
}

.permission-panel-card {
  min-width: 0;
  padding: 20px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.permission-panel-card h2 {
  margin: 0 0 16px;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.permission-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 8px;
}

.entity-permission-edit-dialog :deep(.el-dialog__body) {
  max-height: calc(92vh - 140px);
  overflow-y: auto;
}

.sql-variable-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sql-variable-tags .variable-tag {
  cursor: pointer;
}

.permission-sql-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.permission-sql-help :deep(.el-alert__description),
.permission-sql-help {
  line-height: 1.6;
}

.permission-sql-help code,
.permission-sql-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.preview-section {
  margin-bottom: 16px;
}

.preview-section-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.condition-card {
  background: #f8f9fb;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
}

.condition-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

/* ===== 响应式调整 ===== */
@media (max-width: 900px) {
  .entity-design {
    --entity-design-panel-gap: 12px;
  }

  .design-header {
    padding: 0 12px;
  }

  .header-left {
    width: 100%;
    flex-wrap: wrap;
  }

  .design-body {
    overflow: auto;
    flex-direction: column;
  }

  .design-tabs :deep(.el-tabs__item) {
    height: 33px;
  }

  .fields-panel,
  .property-panel {
    width: 100%;
    min-width: 0;
    flex-shrink: 0;
  }

  .fields-panel,
  .property-panel {
    min-height: 420px;
  }
}
</style>
