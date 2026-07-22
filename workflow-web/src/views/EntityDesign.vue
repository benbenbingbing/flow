<template>
  <div class="entity-design">
    <div class="design-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="entity-name">{{ entityData.entityName || '实体设计' }}</span>
        <el-tag :type="isWorkflowEntityMode ? 'success' : 'info'" effect="plain">
          {{ isWorkflowEntityMode ? '流程实体' : '独立业务实体' }}
        </el-tag>
        <el-tag v-if="isSystemEntity" type="warning" effect="plain">平台系统表</el-tag>
      </div>
      <div class="header-right">
        <el-button v-if="!isSystemEntity" @click="codeRuleVisible = true">
          <el-icon><Ticket /></el-icon>编码规则
        </el-button>
        <el-button v-if="!isSystemEntity" @click="permissionVisible = true">
          <el-icon><Lock /></el-icon>数据权限
        </el-button>
        <el-button v-if="!isSystemEntity" type="primary" @click="handleSave">
          <el-icon><Check /></el-icon>保存
        </el-button>
      </div>
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

    <div class="design-body">
      <!-- 字段类型面板 -->
      <div v-if="!isSystemEntity" class="field-types-panel">
        <div class="panel-title">字段类型</div>
        <div class="field-type-list">
          <div
            v-for="type in fieldTypes.filter(t => !['RADIO', 'CHECKBOX'].includes(t.value))"
            :key="type.value"
            class="field-type-item"
            draggable="true"
            @dragstart="handleDragStart(type)"
            @click="handleAddField(type)"
          >
            <el-icon><component :is="type.icon" /></el-icon>
            <span>{{ type.label }}</span>
          </div>
        </div>
      </div>

      <!-- 字段列表 -->
      <div class="fields-panel">
        <div class="panel-title">
          字段列表
          <el-button v-if="!isSystemEntity" type="primary" size="small" @click="handleAddField()">
            <el-icon><Plus /></el-icon>添加
          </el-button>
        </div>
        <div class="fields-list">
          <div
            v-for="(field, index) in displayFields"
            :key="field.id || index"
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
            <div v-if="!isSystemEntity" class="field-actions">
              <el-icon class="action-btn" @click.stop="moveField(field, -1)"><ArrowUp /></el-icon>
              <el-icon class="action-btn" @click.stop="moveField(field, 1)"><ArrowDown /></el-icon>
              <el-icon 
                v-if="!field.isSystem && !field.isPublished" 
                class="action-btn delete" 
                @click.stop="deleteField(field)"
                title="删除字段"
              ><Delete /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 字段属性配置 -->
      <div class="property-panel" :class="{ 'readonly-panel': isSystemEntity }">
        <div class="panel-title">属性配置</div>
        <el-form v-if="selectedField" :model="selectedField" label-width="90px" size="small">
          <el-form-item label="字段名称" required>
            <el-input v-model="selectedField.fieldName" placeholder="请输入字段名称" />
          </el-form-item>
          <el-form-item label="字段编码" required>
            <el-input
              v-model="selectedField.fieldCode"
              placeholder="请输入字段编码"
              :disabled="selectedField.isPublished || selectedField.isSystem"
            />
            <div v-if="selectedField.isPublished" class="form-tip text-warning">
              已发布字段的编码不能修改
            </div>
            <div v-else-if="selectedField.isSystem" class="form-tip text-warning">
              系统字段的编码不能修改
            </div>
          </el-form-item>
          <el-form-item label="数据库列名">
            <el-input 
              :model-value="formatDbColumnName(selectedField.fieldCode)" 
              disabled
            />
          </el-form-item>
          <el-form-item label="字段类型" required>
            <el-select
              v-model="selectedField.fieldType"
              placeholder="选择类型"
              style="width: 100%"
              :disabled="selectedField.isPublished || selectedField.isSystem"
            >
              <el-option
                v-for="type in fieldTypes"
                :key="type.value"
                :label="type.label"
                :value="type.value"
              />
            </el-select>
            <div v-if="selectedField.isPublished" class="form-tip text-warning">
              已发布字段的类型不能修改
            </div>
            <div v-else-if="selectedField.isSystem" class="form-tip text-warning">
              系统字段的类型不能修改
            </div>
          </el-form-item>
          
          <!-- 字段长度配置（文本等字符串类型） -->
          <el-form-item label="字段长度" v-if="showFieldLength">
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
          
          <el-form-item label="是否必填">
            <el-switch v-model="selectedField.isRequired" />
          </el-form-item>
          <el-form-item label="是否唯一">
            <el-switch v-model="selectedField.isUnique" />
          </el-form-item>
          <el-form-item label="默认值">
            <el-input v-model="selectedField.defaultValue" :placeholder="showOptions ? '请输入选项的 value 值（如 1）' : '请输入默认值'" />
            <div v-if="showOptions" class="form-tip">默认值应填写选项的 value（key），而非显示文本 label</div>
          </el-form-item>
          <template v-if="showOptions">
            <el-form-item label="选项来源" required>
              <el-radio-group v-model="selectedField.optionSource">
                <el-radio-button label="DICT">系统代码表</el-radio-button>
                <el-radio-button label="LEGACY_INLINE" disabled>旧内嵌选项</el-radio-button>
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
          <el-form-item label="验证规则">
            <el-input
              v-model="selectedField.validateRules"
              type="textarea"
              rows="2"
              placeholder="JSON格式验证规则"
            />
          </el-form-item>
          
          <!-- 子表单配置 -->
          <template v-if="isSubForm">
            <el-divider>关系</el-divider>
            <el-form-item label="类型" required>
              <el-radio-group v-model="selectedField.relationType">
                <el-radio-button label="ONE_TO_ONE">一对一</el-radio-button>
                <el-radio-button label="ONE_TO_MANY">一对多</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="子实体" required>
              <el-select 
                v-model="selectedField.childEntityId"
                placeholder="选择实体"
                style="width: 100%"
                @change="onChildEntityChange"
              >
                <el-option
                  v-for="entity in availableEntities"
                  :key="entity.id"
                  :label="entity.entityName || entity.entityCode"
                  :value="entity.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="子表外键" v-if="selectedField.childEntityId">
              <el-select 
                v-model="selectedField.childRefFieldCode"
                placeholder="选择字段"
                style="width: 100%"
                @change="syncRelationRefs"
              >
                <el-option
                  v-for="field in refEntityFields"
                  :key="field.fieldCode"
                  :label="`${field.fieldName || field.fieldCode} / ${field.fieldCode}`"
                  :value="field.fieldCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="级联删除">
              <el-switch v-model="selectedField.cascadeDelete" />
            </el-form-item>
          </template>
          
          <!-- 附件配置 -->
          <template v-if="isAttachment">
            <el-divider>附件配置</el-divider>
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
              <el-form-item label="文件类型">
                <el-select 
                  v-model="item.fileTypes" 
                  multiple 
                  placeholder="选择允许的文件类型"
                  style="width: 100%"
                >
                  <el-option label="图片 (.jpg, .jpeg, .png, .gif)" value=".jpg,.jpeg,.png,.gif" />
                  <el-option label="文档 (.pdf, .doc, .docx)" value=".pdf,.doc,.docx" />
                  <el-option label="表格 (.xls, .xlsx)" value=".xls,.xlsx" />
                  <el-option label="文本 (.txt)" value=".txt" />
                  <el-option label="压缩包 (.zip, .rar)" value=".zip,.rar" />
                </el-select>
                <div class="form-tip">不选则表示允许所有类型</div>
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
            <el-divider>实体引用配置</el-divider>
            <el-form-item label="关联实体" required>
              <el-select 
                v-model="selectedField.refEntityId" 
                placeholder="选择关联实体"
                style="width: 100%"
                filterable
                @change="onReferenceEntityChange"
              >
                <el-option
                  v-for="entity in availableEntities"
                  :key="entity.id"
                  :label="`${entity.entityName} (${entity.entityCode})`"
                  :value="entity.id"
                />
              </el-select>
              <div class="form-tip">业务实体和平台系统实体使用同一套引用模型。</div>
            </el-form-item>
            <el-form-item label="显示字段" v-if="selectedField.refEntityId">
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
        </el-form>
        <div v-else class="empty-tip">请选择字段进行配置</div>
      </div>
    </div>

    <!-- 编码规则配置对话框 -->
    <el-dialog v-model="codeRuleVisible" title="数据编码规则配置" width="550px">
      <el-form :model="codeRule" label-width="100px" size="default">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px">
          配置实体数据的自动编码规则，默认格式：前缀 + 日期 + 序列号
        </el-alert>
        
        <el-form-item label="编码前缀">
          <el-input v-model="codeRule.prefix" placeholder="如：CG、DD、ORDER" maxlength="20" show-word-limit />
          <div class="form-tip">建议使用大写字母，如采购单用CG，订单用DD</div>
        </el-form-item>
        
        <el-form-item label="日期格式">
          <el-select v-model="codeRule.dateFormat" placeholder="选择日期格式" style="width: 100%">
            <el-option label="yyyyMMdd (如：20240101)" value="yyyyMMdd" />
            <el-option label="yyyy-MM-dd (如：2024-01-01)" value="yyyy-MM-dd" />
            <el-option label="yyyy/MM/dd (如：2024/01/01)" value="yyyy/MM/dd" />
            <el-option label="yyyyMM (如：202401)" value="yyyyMM" />
            <el-option label="yyMMdd (如：240101)" value="yyMMdd" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="序列号位数">
          <el-slider v-model="codeRule.seqLength" :min="3" :max="10" show-stops />
          <div class="form-tip">当前：{{ codeRule.seqLength }}位（格式：{{ '0'.repeat(codeRule.seqLength).replace(/0/g, '0') }}1）</div>
        </el-form-item>
        
        <el-form-item label="重置周期">
          <el-radio-group v-model="codeRule.seqType">
            <el-radio-button label="DAY">按天</el-radio-button>
            <el-radio-button label="MONTH">按月</el-radio-button>
            <el-radio-button label="YEAR">按年</el-radio-button>
            <el-radio-button label="NEVER">不重置</el-radio-button>
          </el-radio-group>
          <div class="form-tip">
            <span v-if="codeRule.seqType === 'DAY'">每天从000001开始编号</span>
            <span v-if="codeRule.seqType === 'MONTH'">每月从000001开始编号</span>
            <span v-if="codeRule.seqType === 'YEAR'">每年从000001开始编号</span>
            <span v-if="codeRule.seqType === 'NEVER'">永远不重置，持续递增</span>
          </div>
        </el-form-item>
        
        <el-divider />
        
        <el-form-item label="编码示例">
          <el-input v-model="codeRule.example" readonly>
            <template #append>
              <el-button @click="previewCode">刷新</el-button>
            </template>
          </el-input>
          <div class="form-tip">根据上述配置生成的编码示例</div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="codeRuleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCodeRule">保存</el-button>
      </template>
    </el-dialog>
  </div>

  <!-- 数据权限配置对话框 -->
  <el-dialog v-model="permissionVisible" title="数据权限配置" width="900px" :close-on-click-modal="false">
    <el-alert type="info" :closable="false" style="margin-bottom: 16px">
      配置“谁在什么列表能看到哪些数据”。保存只更新草稿，发布后才影响运行时；没有匹配的允许方案时默认拒绝全部数据。
    </el-alert>
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>数据参与团队</span>
          <el-button size="small" type="primary" @click="handleSave">保存实体配置</el-button>
        </div>
      </template>
      <el-form label-width="150px" size="small">
        <el-form-item label="参与后允许查看">
          <el-switch v-model="entityData.teamVisibilityEnabled" />
          <span class="form-tip" style="margin-left: 12px">
            所有人工操作始终形成参与事件；此开关只控制参与记录是否授予查看权限。
          </span>
        </el-form-item>
        <el-form-item v-if="entityData.teamVisibilityEnabled" label="权限覆盖级别">
          <el-select v-model="entityData.teamVisibilityLevel" style="width: 260px">
            <el-option label="附加授权（列表收窄和拒绝仍生效）" value="ADDITIVE" />
            <el-option label="覆盖普通数据范围（拒绝仍生效）" value="OVERRIDE_SCOPE" />
            <el-option label="绝对参与授权（覆盖业务拒绝）" value="ABSOLUTE" />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="entityData.teamVisibilityEnabled"
          type="warning"
          :closable="false"
          title="配置保存后还需要重新发布实体，运行时才会建表并启用新版本。该能力只授予查看，不授予编辑、删除或审批。"
        />
      </el-form>
    </el-card>
    <div class="permission-header">
      <el-button type="primary" size="small" @click="handleAddPermission">
        <el-icon><Plus /></el-icon>添加规则
      </el-button>
      <el-select
        v-model="simulationUserId"
        filterable
        clearable
        placeholder="选择模拟用户"
        size="small"
        style="width: 220px"
        @visible-change="visible => visible && loadSelectorOptions()"
      >
        <el-option v-for="opt in userOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button size="small" @click="handlePreviewPermissionSql('')">
        <el-icon><View /></el-icon>模拟默认列表
      </el-button>
      <el-button type="success" size="small" @click="publishPermissions">
        发布数据范围
      </el-button>
    </div>
    <el-table
      v-if="availableListConfigs.length"
      :data="availableListConfigs"
      border
      size="small"
      style="margin-top: 12px"
    >
      <el-table-column prop="listName" label="列表" min-width="140" />
      <el-table-column prop="listKey" label="listKey" min-width="130" />
      <el-table-column label="范围模式" width="130">
        <template #default="{ row }">
          <el-tag :type="row.dataScopeMode === 'OVERRIDE' ? 'danger' : row.dataScopeMode === 'NARROW' ? 'warning' : 'info'">
            {{ getScopeModeLabel(row.dataScopeMode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="允许绑定" width="90" align="center">
        <template #default="{ row }">{{ countListBindings(row.listKey, 'ALLOW') }}</template>
      </el-table-column>
      <el-table-column label="拒绝绑定" width="90" align="center">
        <template #default="{ row }">{{ countListBindings(row.listKey, 'DENY') }}</template>
      </el-table-column>
    </el-table>
    <el-table :data="permissionList" border size="small" style="margin-top: 12px">
      <el-table-column prop="ruleName" label="规则名称" width="140" />
      <el-table-column label="适用列表" width="120" align="center">
        <template #default="{ row }">
          <span>{{ getListConfigName(row.listKey) || '实体默认' }}</span>
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
          <el-button size="small" text @click="handlePreviewPermissionSql(row.listKey)">模拟</el-button>
          <el-button type="danger" size="small" text @click="handleDeletePermission(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <!-- 规则编辑对话框 -->
  <el-dialog v-model="permissionEditVisible" :title="permissionForm.id ? '编辑规则' : '新增规则'" width="980px" :close-on-click-modal="false">
    <el-form :model="permissionForm" label-width="100px" size="default">
      <el-form-item label="规则名称" required>
        <el-input v-model="permissionForm.ruleName" placeholder="如：部门经理查看全部门数据" />
      </el-form-item>
      <el-form-item label="适用列表">
        <el-select v-model="permissionForm.listKey" placeholder="留空表示实体默认范围" clearable style="width: 100%">
          <el-option label="实体默认范围" value="" />
          <el-option v-for="config in availableListConfigs" :key="config.listKey" :label="config.listName || config.listKey" :value="config.listKey" />
        </el-select>
      </el-form-item>
      <el-form-item label="规则效果">
        <el-radio-group v-model="permissionForm.ruleEffect">
          <el-radio-button label="ALLOW">允许（放行并附加范围）</el-radio-button>
          <el-radio-button label="DENY">拒绝（排除数据范围）</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="是否启用">
        <el-switch v-model="permissionForm.enabled" :active-value="1" :inactive-value="0" />
      </el-form-item>

      <el-divider content-position="left">匹配配置（谁适用这条规则）</el-divider>
      <el-form-item label="逻辑关系">
        <el-radio-group v-model="permissionForm.matchLogic">
          <el-radio-button label="OR">满足任一条件</el-radio-button>
          <el-radio-button label="AND">满足所有条件</el-radio-button>
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
          <el-select v-model="cond.scopeType" placeholder="选择范围类型" style="width: 100%">
            <el-option label="全部用户" value="ALL_USERS" />
            <el-option label="指定用户" value="USER" />
            <el-option label="指定角色" value="ROLE" />
            <el-option label="指定用户组" value="GROUP" />
            <el-option label="指定部门" value="DEPT" />
            <el-option label="指定组织" value="ORG" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cond.scopeType === 'USER'" label="选择用户">
          <el-select
            v-model="cond.targetIds"
            multiple
            filterable
            clearable
            placeholder="请选择用户"
            style="width: 100%"
          >
            <el-option
              v-for="opt in userOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
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
            <el-radio label="ANY">满足任一项</el-radio>
            <el-radio label="ALL">满足全部项</el-radio>
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
      </div>
      <el-button type="primary" size="small" text @click="addMatchCondition">
        <el-icon><Plus /></el-icon>添加匹配条件
      </el-button>

      <el-divider content-position="left">过滤配置（能看到什么数据）</el-divider>
      <el-form-item label="数据范围" required>
        <el-select v-model="permissionForm.filterType" placeholder="选择数据范围" style="width: 100%">
          <el-option label="全部数据" value="ALL" />
          <el-option label="当前用户是创建人" value="PERSONAL" />
          <el-option label="当前用户是提交人" value="SUBMITTER" />
          <el-option label="当前用户是当前办理人" value="CURRENT_ASSIGNEE" />
          <el-option label="本部门" value="DEPT" />
          <el-option label="本部门及子部门" value="DEPT_TREE" />
          <el-option label="结构化条件组" value="RULE" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="permissionForm.filterType === 'RULE'" label="条件规则">
        <div style="width: 100%">
          <el-alert
            type="info"
            :closable="false"
            title="条件组由后端编译为安全 SQL；不支持自由脚本或自定义 SQL。"
            style="margin-bottom: 10px"
          />
          <ActionRuleGroupEditor
            v-if="permissionForm.filterRoot"
            :node="permissionForm.filterRoot"
            :fields="permissionRuleFieldOptions"
            :statuses="availableStatuses"
          />
          <el-button v-else type="primary" text @click="createPermissionFilterRoot">添加条件组</el-button>
          <el-button v-if="permissionForm.filterRoot" type="danger" text @click="permissionForm.filterRoot = null">清空条件</el-button>
        </div>
      </el-form-item>

      <el-form-item label="状态限制">
        <el-switch v-model="permissionForm.statusLimit.enabled" />
        <span style="margin-left: 8px">启用状态过滤</span>
      </el-form-item>
      <template v-if="permissionForm.statusLimit.enabled">
        <el-form-item label="限制模式">
          <el-radio-group v-model="permissionForm.statusLimit.mode">
            <el-radio-button label="IN">允许以下状态</el-radio-button>
            <el-radio-button label="NOT_IN">排除以下状态</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态值">
          <el-select v-model="permissionForm.statusLimit.values" multiple placeholder="选择状态" style="width: 100%">
            <el-option v-for="status in availableStatuses" :key="status.statusCode" :label="status.statusName" :value="status.statusCode" />
          </el-select>
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="permissionEditVisible = false">取消</el-button>
      <el-button type="primary" @click="savePermission">保存</el-button>
    </template>
  </el-dialog>

  <!-- 权限 SQL 预览对话框 -->
  <el-dialog v-model="permissionSqlPreviewVisible" :title="permissionSqlPreviewTitle" width="700px">
    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      {{ permissionSqlPreview.matchedRules?.length === 1 && permissionSqlPreviewTitle === '规则 SQL 预览'
        ? '以下为该规则单独生效时的数据权限 SQL 片段（不含外层 deleted=0）。'
        : '以下为当前用户在该列表下最终生效的数据权限 SQL 片段（不含外层 deleted=0）。' }}
    </el-alert>
    <el-alert v-if="permissionSqlPreview.remark" type="warning" :closable="false" style="margin-bottom: 12px">
      {{ permissionSqlPreview.remark }}
    </el-alert>

    <div v-if="permissionSqlPreview.matchedRules && permissionSqlPreview.matchedRules.length > 0" class="preview-section">
      <div class="preview-section-title">命中规则明细</div>
      <el-table :data="permissionSqlPreview.matchedRules" border size="small" style="margin-bottom: 16px">
        <el-table-column prop="ruleName" label="规则名称" min-width="120" />
        <el-table-column prop="ruleEffect" label="效果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.ruleEffect === 'ALLOW' ? 'success' : 'danger'" size="small">{{ row.ruleEffect === 'ALLOW' ? '允许' : '拒绝' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="listKey" label="适用列表" width="120" align="center" />
        <el-table-column prop="sql" label="规则 SQL" min-width="250" show-overflow-tooltip />
      </el-table>
    </div>
    <div v-else class="preview-section">
      <el-alert type="warning" :closable="false">没有命中任何允许方案，运行时将拒绝全部数据。</el-alert>
    </div>

    <div class="preview-section">
      <div class="preview-section-title">最终生效 SQL</div>
      <el-input v-model="permissionSqlPreview.sql" type="textarea" :rows="4" readonly />
    </div>
  </el-dialog>

  <el-dialog v-model="quickDictVisible" title="新建代码表并绑定字段" width="560px">
    <el-form label-width="100px">
      <el-form-item label="代码表名称" required>
        <el-input v-model="quickDictForm.dictName" placeholder="例如：报销类型" />
      </el-form-item>
      <el-form-item label="代码表编码" required>
        <el-input v-model="quickDictForm.dictCode" placeholder="例如：expense_type" />
      </el-form-item>
      <el-form-item label="代码项" required>
        <el-input
          v-model="quickDictForm.itemsText"
          type="textarea"
          :rows="6"
          placeholder="每行格式：编码:名称"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="quickDictVisible = false">取消</el-button>
      <el-button type="primary" :loading="quickDictSaving" @click="createAndBindDict">
        创建并绑定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { entityApi } from '@/api/entity'
import { codeRuleApi } from '@/api/codeRule'
import { entityListScopeRuleApi } from '@/api/entityListScopeRule'
import { entityListConfigApi } from '@/api/entityListConfig'
import { getEntityStatusList } from '@/api/entityStatus'
import { getUserList } from '@/api/system/user'
import { getEnabledRoles } from '@/api/system/role'
import { getEnabledOrgList } from '@/api/system/org'
import { getEnabledGroups } from '@/api/system/group'
import { getDictList, createDictWithItems } from '@/api/system/dict'
import ActionRuleGroupEditor from '@/components/ActionRuleGroupEditor.vue'
import {
  ENTITY_FIELD_TYPES,
  WORKFLOW_SYSTEM_FIELD_CODES,
  filterEntityFieldsByLifecycle,
  getEntityFieldTypeLabel,
  getEntityFieldTypeTag
} from '@/shared/entity-design'

const route = useRoute()
const router = useRouter()
const entityId = route.params.id

// 字段类型定义
const fieldTypes = ENTITY_FIELD_TYPES

const entityData = ref({})
const fields = ref([])
const isSystemEntity = computed(() => entityData.value?.storageMode === 'SYSTEM')
const isWorkflowEntityMode = computed(() => entityData.value?.lifecycleMode === 'WORKFLOW')
const displayFields = computed(() =>
  filterEntityFieldsByLifecycle(entityData.value, fields.value)
)
const selectedField = ref(null)
const draggedType = ref(null)
const optionsText = ref('')
const refEntityFields = ref([])
const dictOptions = ref([])
const quickDictVisible = ref(false)
const quickDictSaving = ref(false)
const quickDictForm = ref({ dictName: '', dictCode: '', itemsText: '' })

// 编码规则配置
const codeRuleVisible = ref(false)
const codeRule = ref({
  entityCode: '',
  prefix: '',
  dateFormat: 'yyyyMMdd',
  seqLength: 6,
  seqType: 'DAY',
  example: ''
})

// 数据权限配置
const permissionVisible = ref(false)
const permissionList = ref([])
const permissionEditVisible = ref(false)
const permissionForm = ref(createEmptyPermissionForm())
const availableStatuses = ref([])
const availableListConfigs = ref([])
const permissionSqlPreview = ref({ sql: '', matchedRules: [], hasPermission: true, needFilter: false })
const permissionSqlPreviewVisible = ref(false)
const permissionSqlPreviewTitle = ref('权限 SQL 预览')
const simulationUserId = ref('')
const userOptions = ref([])
const roleOptions = ref([])
const groupOptions = ref([])
const deptOptions = ref([])
const organizationOptions = ref([])

const permissionSystemFields = computed(() => [
  { label: '数据名称', value: 'name' },
  { label: '数据编码', value: 'code' },
  { label: '业务单号', value: 'dataNo' },
  { label: '状态', value: 'status' },
  { label: '创建人', value: 'createdBy' },
  { label: '提交人', value: 'submitterId' },
  { label: '所属部门', value: 'deptId' },
  { label: '流程实例', value: 'processInstanceId' },
  { label: '当前办理人', value: 'currentTaskAssignee' },
  { label: '创建时间', value: 'createdAt' },
  { label: '更新时间', value: 'updatedAt' }
].filter(item => isWorkflowEntityMode.value || !WORKFLOW_SYSTEM_FIELD_CODES.has(item.value)))

const permissionRuleFieldOptions = computed(() => [
  ...permissionSystemFields.value,
  ...(fields.value || [])
    .filter(field => field.fieldCode && !['SUB_FORM', 'SUB_FORM_LIST'].includes(field.fieldType))
    .filter(field => !permissionSystemFields.value.some(item => item.value === field.fieldCode))
    .map(field => ({
      label: `${field.fieldName} (${field.fieldCode})`,
      value: field.fieldCode
    }))
])

const loadSelectorOptions = async () => {
  try {
    const [users, roles, groups, orgs] = await Promise.all([
      getUserList().catch(() => []),
      getEnabledRoles().catch(() => []),
      getEnabledGroups().catch(() => []),
      getEnabledOrgList().catch(() => [])
    ])
    userOptions.value = (users || []).map(u => ({ label: `${u.nickname || u.username} (${u.username})`, value: u.id }))
    roleOptions.value = (roles || []).map(r => ({ label: r.roleName || r.roleCode, value: r.id }))
    groupOptions.value = (groups || []).map(group => ({ label: group.groupName || group.groupCode, value: group.id }))
    deptOptions.value = (orgs || [])
      .filter(org => String(org.type || '').toLowerCase() === 'dept')
      .map(org => ({ label: org.orgName, value: org.id }))
    organizationOptions.value = (orgs || [])
      .filter(org => String(org.type || '').toLowerCase() === 'org')
      .map(org => ({ label: org.orgName, value: org.id }))
  } catch (error) {
    console.error('加载选择数据失败:', error)
  }
}

function createEmptyPermissionForm() {
  return {
    id: null,
    policyId: null,
    policyKey: '',
    entityCode: '',
    ruleName: '',
    enabled: 1,
    listKey: '',
    ruleEffect: 'ALLOW',
    matchLogic: 'OR',
    matchConditions: [{
      scopeType: 'ALL_USERS',
      targetIds: [],
      operator: 'ANY',
      includeSubDept: false
    }],
    matchRoot: null,
    filterType: 'PERSONAL',
    filterRoot: null,
    legacyUnsafeConfig: false,
    fieldMapping: { userField: 'create_by', deptField: 'dept_id', statusField: 'status' },
    statusLimit: { enabled: false, mode: 'IN', values: [] }
  }
}

// 是否显示选项配置
const showOptions = computed(() => {
  return selectedField.value && ['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(selectedField.value.fieldType)
})

// 是否显示字段长度配置（字符串相关类型）
const showFieldLength = computed(() => {
  return selectedField.value && ['STRING', 'TEXT', 'SELECT', 'RADIO', 'MULTI_SELECT', 'CHECKBOX', 'USER', 'DEPT', 'REFERENCE'].includes(selectedField.value.fieldType)
})

// 驼峰转下划线
const formatDbColumnName = (fieldCode) => {
  if (!fieldCode) return ''
  return fieldCode.replace(/([a-z])([A-Z]+)/g, '$1_$2').toLowerCase()
}

// 是否显示子表单配置
const isSubForm = computed(() => {
  return selectedField.value && ['SUB_FORM', 'SUB_FORM_LIST'].includes(selectedField.value.fieldType)
})

// 是否显示附件配置
const isAttachment = computed(() => {
  return selectedField.value && ['FILE', 'IMAGE'].includes(selectedField.value.fieldType)
})

// 是否显示实体引用配置
const isReference = computed(() => {
  return selectedField.value && ['REFERENCE', 'MULTI_REFERENCE'].includes(selectedField.value.fieldType)
})

// 可选的实体列表（排除当前实体）
const availableEntities = ref([])

// 加载可选实体列表
const loadAvailableEntities = async () => {
  try {
    const entities = await entityApi.getAll()
    availableEntities.value = entities.filter(item => String(item.id) !== String(entityId))
  } catch (error) {
    console.error('加载实体列表失败:', error)
  }
}

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
  try {
    const data = await entityApi.getById(entityId)
    entityData.value = data
    fields.value = (data.fields || []).map(f => {
      const field = {
        ...f,
        valueStorage: ['MULTI_SELECT', 'CHECKBOX', 'MULTI_REFERENCE'].includes(f.fieldType)
          ? 'MULTI_TABLE'
          : (f.valueStorage || 'SCALAR'),
        childEntityId: f.childEntityId || f.refEntityId || '',
        childRefFieldCode: f.childRefFieldCode || f.refFieldCode || '',
        relationType: f.relationType || (f.fieldType === 'SUB_FORM' ? 'ONE_TO_ONE' : f.fieldType === 'SUB_FORM_LIST' ? 'ONE_TO_MANY' : undefined),
        cascadeDelete: f.cascadeDelete !== false,
        // fileTypes 在数据库中是逗号分隔字符串，但 el-select multiple 需要数组
        fileTypes: f.fileTypes ? (typeof f.fileTypes === 'string' ? f.fileTypes.split(',') : f.fileTypes) : []
      }
      // fileItems 中的 fileTypes 同样需要转换
      if (field.fileItems && field.fileItems.length > 0) {
        field.fileItems = field.fileItems.map(item => ({
          ...item,
          fileTypes: item.fileTypes ? (typeof item.fileTypes === 'string' ? item.fileTypes.split(',') : item.fileTypes) : []
        }))
      }
      return field
    })
    // 设置编码规则的实体编码
    if (data.entityCode) {
      codeRule.value.entityCode = data.entityCode
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('加载失败')
  }
}

// 加载编码规则
const loadCodeRule = async () => {
  try {
    const data = await codeRuleApi.getByEntityCode(entityData.value.entityCode)
    if (data) {
      codeRule.value = { ...codeRule.value, ...data }
    } else {
      // 使用默认配置
      codeRule.value.prefix = entityData.value.entityCode?.toUpperCase() || ''
      codeRule.value.dateFormat = 'yyyyMMdd'
      codeRule.value.seqLength = 6
      codeRule.value.seqType = 'DAY'
      previewCode()
    }
  } catch (error) {
    console.error('加载编码规则失败:', error)
  }
}

// 预览编码
const previewCode = async () => {
  try {
    const preview = await codeRuleApi.preview(codeRule.value)
    codeRule.value.example = preview
  } catch (error) {
    // 本地计算示例
    const date = new Date()
    const format = codeRule.value.dateFormat || 'yyyyMMdd'
    const dateStr = format
      .replace('yyyy', date.getFullYear())
      .replace('MM', String(date.getMonth() + 1).padStart(2, '0'))
      .replace('dd', String(date.getDate()).padStart(2, '0'))
      .replace(/-/g, '')
      .replace(/\//g, '')
    const seqStr = '1'.padStart(codeRule.value.seqLength || 6, '0')
    codeRule.value.example = (codeRule.value.prefix || '') + dateStr + seqStr
  }
}

// 保存编码规则
const saveCodeRule = async () => {
  try {
    await codeRuleApi.save(codeRule.value)
    ElMessage.success('编码规则保存成功')
    codeRuleVisible.value = false
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
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
  if (['SUB_FORM', 'SUB_FORM_LIST'].includes(newField.fieldType)) {
    newField.relationType = newField.fieldType === 'SUB_FORM' ? 'ONE_TO_ONE' : 'ONE_TO_MANY'
    newField.cascadeDelete = true
    newField.refEntityType = 'CUSTOM'
    newField.childEntityId = ''
    newField.childRefFieldCode = ''
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
      itemName: field.fieldName || '附件',
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
  
  // 如果是子表单字段，加载关联实体的字段
  if (isSubForm.value) {
    field.childEntityId = field.childEntityId || field.refEntityId || ''
    field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
    field.relationType = field.relationType || (field.fieldType === 'SUB_FORM' ? 'ONE_TO_ONE' : 'ONE_TO_MANY')
    field.cascadeDelete = field.cascadeDelete !== false
    syncRelationRefs()
    if (field.childEntityId) {
      onRefEntityChange(field.childEntityId)
    }
  } else if (isReference.value && field.refEntityId) {
    field.refEntityType = 'CUSTOM'
    onRefEntityChange(field.refEntityId)
  }
}

const openQuickDictDialog = () => {
  const fieldCode = selectedField.value?.fieldCode || ''
  quickDictForm.value = {
    dictName: selectedField.value?.fieldName || '',
    dictCode: fieldCode ? `${entityData.value.entityCode}_${fieldCode}`.toLowerCase() : '',
    itemsText: ''
  }
  quickDictVisible.value = true
}

const createAndBindDict = async () => {
  const form = quickDictForm.value
  const items = form.itemsText.split('\n').map(line => {
    const separator = line.indexOf(':')
    if (separator < 1) return null
    const itemCode = line.slice(0, separator).trim()
    const itemLabel = line.slice(separator + 1).trim()
    return itemCode && itemLabel ? { itemCode, itemLabel } : null
  }).filter(Boolean)
  if (!form.dictName || !form.dictCode || !items.length) {
    ElMessage.warning('请填写代码表名称、编码和至少一个有效代码项')
    return
  }
  quickDictSaving.value = true
  try {
    const dict = await createDictWithItems({
      dict: {
        dictName: form.dictName,
        dictCode: form.dictCode,
        status: '0'
      },
      items
    })
    await loadDictOptions()
    selectedField.value.optionSource = 'DICT'
    selectedField.value.dictType = dict.dictCode
    selectedField.value.optionsJson = null
    quickDictVisible.value = false
    ElMessage.success('代码表已创建并绑定')
  } catch (error) {
    console.error(error)
    ElMessage.error('创建代码表失败')
  } finally {
    quickDictSaving.value = false
  }
}

const onChildEntityChange = async (value) => {
  if (!selectedField.value) return
  selectedField.value.refEntityId = value
  selectedField.value.refEntityType = 'CUSTOM'
  selectedField.value.childRefFieldCode = ''
  selectedField.value.refFieldCode = ''
  await onRefEntityChange(value)
}

const syncRelationRefs = () => {
  if (!selectedField.value) return
  selectedField.value.refEntityId = selectedField.value.childEntityId || selectedField.value.refEntityId || ''
  selectedField.value.refFieldCode = selectedField.value.childRefFieldCode || selectedField.value.refFieldCode || ''
  selectedField.value.refEntityType = 'CUSTOM'
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
    displayMode: field.displayMode,
    refFieldCode: field.refFieldCode,
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
const handleSave = async () => {
  if (isSystemEntity.value) {
    ElMessage.warning('平台系统实体字段由数据库自动同步，不能在设计器中修改')
    return
  }
  // 验证字段
  for (const field of fields.value) {
    if (!field.fieldName || !field.fieldCode) {
      ElMessage.warning('请完善字段信息')
      return
    }
    if (['SUB_FORM', 'SUB_FORM_LIST'].includes(field.fieldType)) {
      if (!field.childEntityId && !field.refEntityId) {
        ElMessage.warning(`请选择子实体：${field.fieldName}`)
        return
      }
      if (!field.childRefFieldCode && !field.refFieldCode) {
        ElMessage.warning(`请选择子表外键：${field.fieldName}`)
        return
      }
    }
    if (['SELECT', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(field.fieldType)
        && !field.dictType) {
      ElMessage.warning(`请选择代码表：${field.fieldName}`)
      return
    }
    if (['REFERENCE', 'MULTI_REFERENCE'].includes(field.fieldType) && !field.refEntityId) {
      ElMessage.warning(`请选择关联实体：${field.fieldName}`)
      return
    }
  }

  try {
    await entityApi.update(entityId, {
      ...entityData.value,
      fields: fields.value.map(f => ({
        ...f,
        childEntityId: f.childEntityId || f.refEntityId || '',
        childRefFieldCode: f.childRefFieldCode || f.refFieldCode || '',
        refEntityId: f.refEntityId || f.childEntityId || '',
        refFieldCode: f.refFieldCode || f.childRefFieldCode || '',
        relationType: f.relationType || (f.fieldType === 'SUB_FORM' ? 'ONE_TO_ONE' : f.fieldType === 'SUB_FORM_LIST' ? 'ONE_TO_MANY' : undefined),
        cascadeDelete: f.cascadeDelete !== false,
        // 移除临时ID
        id: f.id?.startsWith('temp_') ? null : f.id,
        // fileTypes 是数组，需要转为逗号分隔字符串传给后端
        fileTypes: Array.isArray(f.fileTypes) ? f.fileTypes.join(',') : f.fileTypes,
        // fileItems 中的 fileTypes 同样需要转换
        fileItems: f.fileItems ? f.fileItems.map(item => ({
          ...item,
          fileTypes: Array.isArray(item.fileTypes) ? item.fileTypes.join(',') : item.fileTypes
        })) : []
      }))
    })
    ElMessage.success('保存成功')
    loadEntity()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
}

// ============ 数据权限方法 ============
const loadPermissions = async () => {
  if (!entityData.value.entityCode) return
  try {
    const [permissionData, listConfigData] = await Promise.all([
      entityListScopeRuleApi.getByEntityCode(entityData.value.entityCode),
      entityListConfigApi.getByEntityId(entityId)
    ])
    availableListConfigs.value = listConfigData || []
    permissionList.value = (permissionData || []).map(item => {
      const match = parseJson(item.matchConfig, { logic: 'OR', conditions: [] })
      const filter = parseJson(item.filterConfig, { type: 'PERSONAL', fieldMapping: {}, statusLimit: {}, root: null })
      const legacyUnsafeConfig = (match.conditions || []).some(condition => condition.scopeType === 'EXPRESSION')
        || ['EXPRESSION', 'CUSTOM_SQL'].includes(filter.type)
      return {
        ...item,
        listKey: item.listKey || '',
        ruleEffect: item.ruleEffect || 'ALLOW',
        matchLogic: match.logic || 'OR',
        matchConditions: (match.conditions || []).map(c => ({
          ...c,
          targetIds: Array.isArray(c.targetIds) ? c.targetIds.map(id => String(id)) : []
        })),
        matchRoot: match.root || null,
        filterType: ['EXPRESSION', 'CUSTOM_SQL'].includes(filter.type) ? 'PERSONAL' : (filter.type || 'PERSONAL'),
        filterRoot: filter.root || null,
        legacyUnsafeConfig,
        fieldMapping: filter.fieldMapping || { userField: 'create_by', deptField: 'dept_id', statusField: 'status' },
        statusLimit: filter.statusLimit || { enabled: false, mode: 'IN', values: [] }
      }
    })
  } catch (error) {
    console.error('加载权限规则失败:', error)
  }
}

const loadAvailableStatuses = async () => {
  if (!entityData.value.entityCode) return
  try {
    const data = await getEntityStatusList(entityData.value.entityCode)
    availableStatuses.value = data || []
  } catch (error) {
    console.error('加载状态列表失败:', error)
  }
}

const parseJson = (str, defaultVal) => {
  if (!str) return defaultVal
  try {
    return JSON.parse(str)
  } catch (e) {
    return defaultVal
  }
}

const handleAddPermission = () => {
  permissionForm.value = createEmptyPermissionForm()
  permissionForm.value.entityCode = entityData.value.entityCode
  permissionEditVisible.value = true
  loadAvailableStatuses()
  loadSelectorOptions()
}

const handleEditPermission = (row) => {
  permissionForm.value = cloneValue(row)
  if (permissionForm.value.legacyUnsafeConfig) {
    ElMessage.warning('该规则包含已废弃的表达式或自定义 SQL，保存前请改为结构化条件')
  }
  permissionEditVisible.value = true
  loadAvailableStatuses()
  loadSelectorOptions()
}

const handleDeletePermission = async (row) => {
  try {
    await entityListScopeRuleApi.delete(row)
    ElMessage.success('删除成功')
    loadPermissions()
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  }
}

const togglePermission = async (row) => {
  try {
    await entityListScopeRuleApi.updateEnabled(row)
    ElMessage.success('状态更新成功')
  } catch (error) {
    console.error(error)
    ElMessage.error('状态更新失败')
    row.enabled = row.enabled === 1 ? 0 : 1
  }
}

const addMatchCondition = () => {
  permissionForm.value.matchConditions.push({
    scopeType: 'ROLE',
    targetIds: [],
    operator: 'ANY',
    includeSubDept: false
  })
}

const removeMatchCondition = (index) => {
  permissionForm.value.matchConditions.splice(index, 1)
}

const formatMatchSummary = (row) => {
  const conditions = row.matchConditions || []
  if (!conditions.length) return '-'
  const parts = conditions.map(c => {
    const map = {
      ALL_USERS: '全部用户',
      USER: '指定用户',
      ROLE: '指定角色',
      GROUP: '指定用户组',
      DEPT: '指定部门',
      ORG: '指定组织',
      EXPRESSION: '已废弃表达式'
    }
    return map[c.scopeType] || c.scopeType
  })
  const logic = row.matchLogic === 'AND' ? ' 且 ' : ' 或 '
  return parts.join(logic)
}

const getFilterTypeTag = (type) => {
  const tags = {
    ALL: 'success',
    PERSONAL: '',
    SUBMITTER: '',
    CURRENT_ASSIGNEE: 'primary',
    DEPT: 'warning',
    DEPT_TREE: 'warning',
    RULE: 'info'
  }
  return tags[type] || ''
}

const getFilterTypeLabel = (type) => {
  const labels = {
    ALL: '全部数据',
    PERSONAL: '创建人是当前用户',
    SUBMITTER: '提交人是当前用户',
    CURRENT_ASSIGNEE: '当前办理人',
    DEPT: '本部门',
    DEPT_TREE: '本部门及子部门',
    RULE: '结构化条件组',
    EXPRESSION: '已废弃表达式',
    CUSTOM_SQL: '已废弃自定义 SQL'
  }
  return labels[type] || type
}

const getScopeModeLabel = (mode) => {
  const labels = {
    INHERIT: '继承实体',
    NARROW: '缩小范围',
    OVERRIDE: '独立范围'
  }
  return labels[mode || 'INHERIT'] || mode
}

const countListBindings = (listKey, effect) => permissionList.value.filter(item =>
  item.listKey === listKey && item.ruleEffect === effect
).length

const getListConfigName = (listKey) => {
  if (!listKey) return ''
  const config = availableListConfigs.value.find(c => c.listKey === listKey)
  return config?.listName || config?.listKey || listKey
}

const handlePreviewPermissionSql = async (requestedListKey) => {
  if (!entityData.value.entityCode) return
  try {
    const targetList = requestedListKey
      || availableListConfigs.value.find(config => config.isDefault)?.listKey
      || availableListConfigs.value[0]?.listKey
    if (!targetList) {
      ElMessage.warning('请先配置并保存至少一个实体列表')
      return
    }
    const preview = await entityListScopeRuleApi.previewSql(
      entityData.value.entityCode,
      targetList,
      { userId: simulationUserId.value || undefined }
    )
    permissionSqlPreview.value = preview || { sql: '1=0', matchedRules: [], hasPermission: true, needFilter: false }
    permissionSqlPreviewTitle.value = `数据范围模拟：${getListConfigName(targetList)}`
    permissionSqlPreviewVisible.value = true
  } catch (error) {
    console.error('预览权限 SQL 失败:', error)
    ElMessage.error('预览失败')
  }
}

const publishPermissions = async () => {
  try {
    await entityListScopeRuleApi.publish(entityData.value.entityCode, '实体设计器发布数据范围')
    ElMessage.success('数据范围发布成功')
    loadPermissions()
  } catch (error) {
    console.error('发布数据范围失败:', error)
    ElMessage.error(error?.message || '发布失败')
  }
}

const createPermissionFilterRoot = () => {
  permissionForm.value.filterRoot = {
    type: 'GROUP',
    logic: 'AND',
    children: [{
      type: 'RELATION',
      relation: 'CURRENT_USER_IS_CREATOR'
    }]
  }
}

const savePermission = async () => {
  const form = permissionForm.value
  if (!form.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }

  if (!form.matchConditions?.length && !form.matchRoot) {
    ElMessage.warning('请至少配置一个适用用户条件')
    return
  }
  const invalidMatch = (form.matchConditions || []).find(condition =>
    condition.scopeType !== 'ALL_USERS' && (!condition.targetIds || condition.targetIds.length === 0)
  )
  if (invalidMatch) {
    ElMessage.warning('指定用户、角色、用户组、部门或组织时必须选择目标')
    return
  }
  if (form.filterType === 'RULE' && (!form.filterRoot?.children?.length)) {
    ElMessage.warning('结构化条件组不能为空')
    return
  }

  // 处理 matchConditions 中的 targetIds
  const matchConditions = (form.matchConditions || []).map(c => ({
    scopeType: c.scopeType,
    targetIds: Array.isArray(c.targetIds) ? c.targetIds.map(id => String(id)).filter(Boolean) : [],
    operator: c.operator,
    includeSubDept: c.includeSubDept
  }))

  const matchConfig = JSON.stringify({
    version: 1,
    logic: form.matchLogic,
    conditions: matchConditions,
    root: form.matchRoot || null
  })

  const filterConfig = JSON.stringify({
    version: 1,
    type: form.filterType,
    root: form.filterType === 'RULE' ? form.filterRoot : null,
    fieldMapping: form.fieldMapping,
    statusLimit: form.statusLimit
  })

  const payload = {
    entityCode: form.entityCode || entityData.value.entityCode,
    policyId: form.policyId,
    policyKey: form.policyKey || `scope_${Date.now()}`,
    ruleName: form.ruleName,
    enabled: form.enabled,
    listKey: form.listKey || null,
    ruleEffect: form.ruleEffect || 'ALLOW',
    filterType: form.filterType,
    matchConfig,
    filterConfig
  }

  try {
    if (form.id) {
      await entityListScopeRuleApi.update(form.id, payload)
    } else {
      await entityListScopeRuleApi.create(payload)
    }
    ElMessage.success('保存成功')
    permissionEditVisible.value = false
    loadPermissions()
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
}

const cloneValue = (value) => JSON.parse(JSON.stringify(value))

// 添加附件项
const addFileItem = () => {
  if (!selectedField.value.fileItems) {
    selectedField.value.fileItems = []
  }
  selectedField.value.fileItems.push({
    itemName: '',
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

watch(permissionVisible, (val) => {
  if (val) {
    loadPermissions()
  }
})

onMounted(() => {
  loadEntity()
  loadAvailableEntities()
  loadDictOptions()
  loadCodeRule()
})
</script>

<style scoped>
.entity-design {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

/* ===== 头部样式 ===== */
.design-header {
  height: 60px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-left :deep(.el-button) {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  backdrop-filter: blur(10px);
  transition: all 0.3s;
}

.header-left :deep(.el-button:hover) {
  background: rgba(255, 255, 255, 0.35);
  border-color: rgba(255, 255, 255, 0.5);
}

.entity-name {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.header-right {
  display: flex;
  gap: 12px;
}

.header-right :deep(.el-button) {
  border-radius: 6px;
  padding: 8px 20px;
}

.header-right :deep(.el-button:first-child) {
  background: rgba(255, 255, 255, 0.9);
  border: none;
  color: #606266;
}

.header-right :deep(.el-button:first-child:hover) {
  background: #fff;
  color: #409eff;
}

/* ===== 主体布局 ===== */
.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  padding: 16px;
  gap: 16px;
}

/* ===== 左侧字段类型面板 ===== */
.field-types-panel {
  width: 200px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  padding: 20px;
  display: flex;
  flex-direction: column;
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

.field-type-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  overflow-y: auto;
  padding-right: 4px;
}

.field-type-list::-webkit-scrollbar {
  width: 4px;
}

.field-type-list::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 2px;
}

.field-type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 8px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s ease;
}

.field-type-item:hover {
  background: #fff;
  border-color: #409eff;
  color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
  transform: translateY(-2px);
}

.field-type-item .el-icon {
  font-size: 22px;
  margin-bottom: 6px;
  transition: transform 0.2s;
}

.field-type-item:hover .el-icon {
  transform: scale(1.1);
}

.field-type-item span {
  font-size: 12px;
  font-weight: 500;
}

/* ===== 中间字段列表面板 ===== */
.fields-panel {
  flex: 1;
  min-width: 450px;
  background: #fff;
  border-radius: 12px;
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
  padding: 14px 16px;
  margin-bottom: 10px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
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
  background: linear-gradient(135deg, #ecf5ff 0%, #f5f7ff 100%);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.2);
}

.field-info {
  display: flex;
  flex-direction: row;
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
  gap: 8px;
  opacity: 1;
  align-items: center;
}

.action-btn {
  cursor: pointer;
  color: #409eff;
  font-size: 16px;
  padding: 6px;
  border-radius: 6px;
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
  width: 360px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.property-panel .panel-title {
  margin: 0;
  padding: 16px 20px;
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
  border-bottom: 1px solid #ebeef5;
}

.property-panel :deep(.el-form) {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.property-panel :deep(.el-form-item) {
  margin-bottom: 18px;
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

.empty-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
}

.empty-tip::before {
  content: '';
  display: inline-block;
  width: 60px;
  height: 60px;
  margin-bottom: 16px;
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23c0c4cc'%3E%3Cpath d='M9 2v2H7v2h2v2H7v2h2v2H7v2h2v2H7v2h2v2h6v-2h2v-2h-2v-2h2v-2h-2v-2h2V8h-2V6h2V4h-2V2H9zm2 2h2v2h-2V4zm0 4h2v2h-2V8zm0 4h2v2h-2v-2zm0 4h2v2h-2v-2z'/%3E%3C/svg%3E") no-repeat center;
  background-size: contain;
  opacity: 0.5;
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
.permission-header {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 8px;
}

.sql-variable-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sql-variable-tags .variable-tag {
  cursor: pointer;
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
@media (max-width: 1200px) {
  .property-panel {
    width: 320px;
  }
}
</style>
