<template>
  <div class="version-management">
    <header class="page-heading">
      <div>
        <h2>业务数据版本</h2>
        <p>每个业务实体维护一套固化策略；保存后立即影响后续数据变更，已有历史版本不会被改写。</p>
      </div>
    </header>

    <section class="content-panel">
      <div class="table-toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索实体名称或编码"
          @clear="handleQuery"
          @keyup.enter="handleQuery"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="status-filter-field">
          <span>启用状态</span>
          <el-select
            v-model="enabledFilter"
            class="status-filter"
            aria-label="按启用状态筛选"
            @change="handleQuery"
          >
            <el-option label="全部" value="ALL" />
            <el-option label="已启用" value="ENABLED" />
            <el-option label="未启用" value="DISABLED" />
          </el-select>
        </div>
        <el-button type="primary" @click="handleQuery">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
      </div>

      <el-table v-loading="loading" :data="configs" border stripe>
        <el-table-column label="实体" min-width="250">
          <template #default="{ row }">
            <div class="primary-text">{{ row.entityName }}</div>
            <div class="secondary-text">{{ row.entityCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="configStatus(row).type">
              {{ configStatus(row).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时机" width="100" align="center">
          <template #default="{ row }">{{ row.triggerCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="关联范围" width="100" align="center">
          <template #default="{ row }">{{ row.scopeRelationCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canView" @click="openConfig(row)">配置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageInfo.pageNum"
        v-model:page-size="pageInfo.pageSize"
        :total="pageInfo.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handlePageSizeChange"
        @current-change="loadConfigs"
      />
    </section>

    <el-drawer
      v-model="drawerVisible"
      :size="drawerSize"
      class="config-drawer"
      :before-close="handleDrawerClose"
      :close-on-click-modal="false"
    >
      <template #header>
        <div class="drawer-heading">
          <div>
            <div class="heading-line">
              <h3>{{ draft.entityName }}</h3>
              <el-tag v-if="isDirty" type="warning" effect="plain">有未保存修改</el-tag>
            </div>
            <span>{{ draft.entityCode }} · 当前配置</span>
          </div>
          <div class="drawer-actions">
            <el-form-item label="启用数据版本" class="header-switch">
              <template #label>
                <ConfigHelpLabel
                  label="启用数据版本"
                  help-key="entityVersion.enabled"
                />
              </template>
              <el-switch
                v-model="draft.enabled"
                :disabled="!canUpdate"
                inline-prompt
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
            <el-button :disabled="!canUpdate" :loading="previewLoading" @click="previewScope">范围预览</el-button>
            <el-button type="primary" :disabled="!canUpdate || !isDirty" :loading="saving" @click="saveConfig">保存并生效</el-button>
          </div>
        </div>
      </template>

      <el-alert
        class="save-effect-alert"
        type="info"
        :closable="false"
        show-icon
        title="保存后配置立即生效，只影响之后生成的数据版本；停用后不再生成新版本，停用前的历史版本仍可查看。"
      />

      <el-alert
        v-if="legacyConfig"
        class="legacy-alert"
        type="warning"
        :closable="false"
        show-icon
        title="这是由旧版策略迁移的当前配置。保存前请检查转换后的生成时机和固化范围。"
      />

      <el-tabs v-model="activeTab" class="config-tabs">
        <el-tab-pane :label="`生成时机 ${draft.triggers.length}`" name="triggers">
          <div class="section-intro">
            <div>
              <strong>什么时候生成版本</strong>
              <p>根实体变化、关联数据变化和手工固化互相独立；同时命中时按优先级只生成一次。</p>
            </div>
            <el-button type="primary" :disabled="!canUpdate" @click="editTrigger()">
              <el-icon><Plus /></el-icon>新增生成时机
            </el-button>
          </div>
          <el-table :data="sortedTriggers" border>
            <el-table-column label="名称" min-width="200">
              <template #default="{ row }">
                <div class="primary-text">{{ row.triggerName }}</div>
                <div class="secondary-text">{{ row.triggerCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="140">
              <template #default="{ row }">{{ triggerTypeText(row.triggerType) }}</template>
            </el-table-column>
            <el-table-column label="对象 / 入口" min-width="210">
              <template #default="{ row }">
                <span v-if="row.triggerType === 'RELATED_MUTATION'">{{ relationName(row.relationCode) }}</span>
                <template v-else-if="row.triggerType === 'ROOT_MUTATION'">
                  <el-tag v-for="item in row.sourceTypes" :key="item" effect="plain" size="small">
                    {{ sourceTypeText(item) }}
                  </el-tag>
                  <span v-if="!row.sourceTypes?.length">全部入口</span>
                </template>
                <span v-else>用户或流程显式固化</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="160">
              <template #default="{ row }">
                <template v-if="row.triggerType !== 'MANUAL'">
                  <el-tag v-for="item in row.operationTypes" :key="item" type="info" effect="plain" size="small">
                    {{ operationTypeText(item) }}
                  </el-tag>
                  <span v-if="!row.operationTypes?.length">全部操作</span>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === false ? 'info' : 'success'">{{ row.enabled === false ? '停用' : '启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="!canUpdate" @click="editTrigger(row, triggerIndex(row))">编辑</el-button>
                <el-button link type="danger" :disabled="!canUpdate" @click="removeTrigger(triggerIndex(row))">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`固化范围 ${draft.snapshotScope.relations.length + 1}`" name="scope">
          <div class="section-intro">
            <div>
              <strong>版本里保存什么</strong>
              <p>根实体固定保存；可以选择一层已发布关系。范围与 SUB_FORM、SUB_LIST 等展示字段类型无关。</p>
            </div>
            <el-button type="primary" :disabled="!canUpdate || !unusedRelationOptions.length" @click="editScopeRelation()">
              <el-icon><Plus /></el-icon>添加关联范围
            </el-button>
          </div>

          <article class="scope-card root-card">
            <div class="scope-card__title">
              <div>
                <strong>{{ draft.snapshotScope.root.entityName || draft.entityName }}</strong>
                <span>根实体 · 固定包含</span>
              </div>
              <el-tag type="success">A</el-tag>
            </div>
            <div class="scope-card__body">
              <el-form-item label="固化字段">
                <template #label>
                  <ConfigHelpLabel
                    label="固化字段"
                    help-key="entityVersion.scopeFields"
                  />
                </template>
                <el-radio-group v-model="draft.snapshotScope.root.fieldMode" :disabled="!canUpdate">
                  <el-radio-button value="ALL_PUBLISHED">全部已发布字段</el-radio-button>
                  <el-radio-button value="SELECTED">指定字段</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="draft.snapshotScope.root.fieldMode === 'SELECTED'" label="指定字段">
                <el-select
                  v-model="draft.snapshotScope.root.fieldCodes"
                  :disabled="!canUpdate"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="选择或输入稳定字段编码"
                >
                  <el-option v-for="field in rootFieldOptions" :key="fieldCode(field)" :label="fieldLabel(field)" :value="fieldCode(field)" />
                </el-select>
              </el-form-item>
            </div>
          </article>

          <el-table :data="draft.snapshotScope.relations" border class="scope-table">
            <el-table-column label="关联数据" min-width="230">
              <template #default="{ row }">
                <div class="primary-text">{{ row.relationName }}</div>
                <div class="secondary-text">{{ row.relationCode }} · {{ row.childEntityName || row.childEntityCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="固化字段" min-width="160">
              <template #default="{ row }">{{ row.fieldMode === 'SELECTED' ? `${row.fieldCodes.length} 个指定字段` : '全部已发布字段' }}</template>
            </el-table-column>
            <el-table-column label="固定过滤" min-width="180">
              <template #default="{ row }">{{ filterSummary(row.filter) }}</template>
            </el-table-column>
            <el-table-column label="单关系上限" width="120" align="center">
              <template #default="{ row }">{{ row.maxRows }} 行</template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row, $index }">
                <el-button link type="primary" :disabled="!canUpdate" @click="editScopeRelation(row, $index)">编辑</el-button>
                <el-button link type="danger" :disabled="!canUpdate" @click="removeScopeRelation($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="limit-panel">
            <div class="limit-panel__heading">
              <strong>版本保护上限</strong>
              <ConfigHelpLabel
                label="版本保护上限"
                help-key="entityVersion.scopeLimits"
                :show-label="false"
              />
            </div>
            <el-form inline label-width="110px">
              <el-form-item label="单关系最多">
                <el-input-number v-model="draft.snapshotScope.limits.maxRowsPerRelation" :disabled="!canUpdate" :min="1" :max="500" />
                <span>行</span>
              </el-form-item>
              <el-form-item label="整版最多">
                <el-input-number v-model="draft.snapshotScope.limits.maxRowsPerVersion" :disabled="!canUpdate" :min="1" :max="2000" />
                <span>行</span>
              </el-form-item>
              <el-form-item label="整版大小">
                <el-input-number v-model="maxSizeMb" :disabled="!canUpdate" :min="1" :max="5" />
                <span>MiB</span>
              </el-form-item>
            </el-form>
            <p>超过上限时本次固化整体失败，不会保存被截断的版本。</p>
          </div>
        </el-tab-pane>

        <el-tab-pane label="比对规则" name="diff">
          <div class="section-intro">
            <div>
              <strong>版本如何显示差异</strong>
              <p>原始值决定是否变化；版本生成时冻结的中文名称和中文显示值只负责展示。</p>
            </div>
          </div>
          <el-form label-width="160px" class="diff-form">
            <el-form-item label="打开时仅看变化">
              <template #label>
                <ConfigHelpLabel
                  label="打开时仅看变化"
                  help-key="entityVersion.diffChangedOnly"
                />
              </template>
              <el-switch v-model="draft.diffPolicy.changedOnlyDefault" :disabled="!canUpdate" />
            </el-form-item>
            <el-form-item label="追踪关联行顺序">
              <template #label>
                <ConfigHelpLabel
                  label="追踪关联行顺序"
                  help-key="entityVersion.diffTrackOrder"
                />
              </template>
              <el-switch v-model="draft.diffPolicy.trackOrder" :disabled="!canUpdate" />
              <span class="form-help">开启后，行位置变化会单独显示“移动”。</span>
            </el-form-item>
            <el-form-item label="忽略比较的字段">
              <template #label>
                <ConfigHelpLabel
                  label="忽略比较的字段"
                  help-key="entityVersion.diffIgnoredFields"
                />
              </template>
              <el-select
                v-model="draft.diffPolicy.ignoredFieldCodes"
                :disabled="!canUpdate"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入字段编码"
              >
                <el-option v-for="field in allFieldOptions" :key="fieldCode(field)" :label="fieldLabel(field)" :value="fieldCode(field)" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>

      </el-tabs>
    </el-drawer>

    <el-dialog v-model="triggerDialogVisible" :title="triggerIndexValue < 0 ? '新增生成时机' : '编辑生成时机'" width="760px" :close-on-click-modal="false">
      <el-form label-width="120px">
        <el-form-item label="名称" required><el-input v-model="triggerEditor.triggerName" /></el-form-item>
        <el-form-item label="稳定编码" required>
          <template #label>
            <ConfigHelpLabel
              label="稳定编码"
              help-key="entityVersion.triggerCode"
            />
          </template>
          <el-input v-model="triggerEditor.triggerCode" :disabled="triggerIndexValue >= 0" />
        </el-form-item>
        <el-form-item label="触发类型" required>
          <template #label>
            <ConfigHelpLabel
              label="触发类型"
              help-key="entityVersion.triggerType"
            />
          </template>
          <el-segmented v-model="triggerEditor.triggerType" :options="triggerTypeOptions" @change="onTriggerTypeChange" />
        </el-form-item>
        <el-form-item v-if="triggerEditor.triggerType === 'RELATED_MUTATION'" label="关联范围" required>
          <template #label>
            <ConfigHelpLabel
              label="关联范围"
              help-key="entityVersion.triggerRelation"
            />
          </template>
          <el-select v-model="triggerEditor.relationCode" filterable>
            <el-option v-for="item in draft.snapshotScope.relations" :key="item.relationCode" :label="item.relationName" :value="item.relationCode" />
          </el-select>
          <div v-if="!draft.snapshotScope.relations.length" class="inline-warning">请先在“固化范围”中添加关联数据。</div>
        </el-form-item>
        <template v-if="triggerEditor.triggerType !== 'MANUAL'">
          <el-form-item v-if="triggerEditor.triggerType === 'ROOT_MUTATION'" label="变更入口">
            <template #label>
              <ConfigHelpLabel
                label="变更入口"
                help-key="entityVersion.sourceTypes"
              />
            </template>
            <el-select v-model="triggerEditor.sourceTypes" multiple filterable placeholder="留空表示全部入口">
              <el-option v-for="item in sourceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作类型">
            <template #label>
              <ConfigHelpLabel
                label="操作类型"
                help-key="entityVersion.operationTypes"
              />
            </template>
            <el-select v-model="triggerEditor.operationTypes" multiple placeholder="留空表示全部操作">
              <el-option v-for="item in operationTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务意图">
            <template #label>
              <ConfigHelpLabel
                label="业务意图"
                help-key="entityVersion.businessIntents"
              />
            </template>
            <el-select v-model="triggerEditor.businessIntents" multiple filterable allow-create default-first-option placeholder="留空表示全部意图" />
          </el-form-item>
          <el-form-item label="附加条件">
            <template #label>
              <ConfigHelpLabel
                label="附加条件"
                help-key="entityVersion.triggerCondition"
              />
            </template>
            <el-input v-model="triggerEditor.conditionText" type="textarea" :rows="4" placeholder='例如：{"field":"status","operator":"EQ","value":"APPROVED"}' />
            <div class="condition-help">支持 field/operator/value，以及 all、any、not 组合；留空或 {} 表示不限制。</div>
          </el-form-item>
        </template>
        <el-form-item label="标题模板">
          <template #label>
            <ConfigHelpLabel
              label="标题模板"
              help-key="entityVersion.titleTemplate"
            />
          </template>
          <el-input v-model="triggerEditor.versionTitleTemplate" />
        </el-form-item>
        <el-form-item label="优先级">
          <template #label>
            <ConfigHelpLabel
              label="优先级"
              help-key="entityVersion.triggerPriority"
            />
          </template>
          <el-input-number v-model="triggerEditor.priority" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="triggerEditor.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="triggerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTrigger">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scopeDialogVisible" :title="scopeIndex < 0 ? '添加关联范围' : '编辑关联范围'" width="860px" :close-on-click-modal="false">
      <el-form label-width="120px">
        <el-form-item label="实体关系" required>
          <template #label>
            <ConfigHelpLabel
              label="实体关系"
              help-key="entityVersion.scopeRelation"
            />
          </template>
          <el-select v-model="scopeEditor.relationCode" :disabled="scopeIndex >= 0" filterable @change="applyRelationOption">
            <el-option
              v-for="item in scopeRelationChoices"
              :key="item.relationCode"
              :label="`${item.relationName}（${item.childEntityName || item.childEntityCode}）`"
              :value="item.relationCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="固化字段">
          <template #label>
            <ConfigHelpLabel
              label="固化字段"
              help-key="entityVersion.scopeFields"
            />
          </template>
          <el-radio-group v-model="scopeEditor.fieldMode">
            <el-radio-button value="ALL_PUBLISHED">全部已发布字段</el-radio-button>
            <el-radio-button value="SELECTED">指定字段</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="scopeEditor.fieldMode === 'SELECTED'" label="字段">
          <el-select v-model="scopeEditor.fieldCodes" multiple filterable allow-create default-first-option placeholder="选择或输入稳定字段编码">
            <el-option v-for="field in scopeFieldOptions" :key="fieldCode(field)" :label="fieldLabel(field)" :value="fieldCode(field)" />
          </el-select>
        </el-form-item>
        <el-form-item label="固定过滤">
          <template #label>
            <ConfigHelpLabel
              label="固定过滤"
              help-key="entityVersion.scopeFilter"
            />
          </template>
          <el-radio-group v-model="scopeEditor.filter.logic">
            <el-radio value="ALL">全部满足</el-radio>
            <el-radio value="ANY">任一满足</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="过滤条件">
          <div class="condition-list">
            <div v-for="(condition, index) in scopeEditor.filter.conditions" :key="index" class="condition-row">
              <el-select v-model="condition.fieldCode" filterable allow-create default-first-option placeholder="字段编码">
                <el-option v-for="field in scopeFieldOptions" :key="fieldCode(field)" :label="fieldLabel(field)" :value="fieldCode(field)" />
              </el-select>
              <el-select v-model="condition.operator" placeholder="操作符" @change="normalizeFilterValue(condition)">
                <el-option v-for="item in filterOperatorOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select
                v-if="['IN', 'NOT_IN'].includes(condition.operator)"
                v-model="condition.value"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入一个或多个固定值"
              />
              <el-input v-else-if="!['EMPTY', 'NOT_EMPTY'].includes(condition.operator)" v-model="condition.value" placeholder="固定值" />
              <span v-else class="condition-placeholder">无需填写值</span>
              <el-button text type="danger" aria-label="删除过滤条件" @click="scopeEditor.filter.conditions.splice(index, 1)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button text type="primary" @click="addFilterCondition"><el-icon><Plus /></el-icon>添加条件</el-button>
          </div>
        </el-form-item>
        <el-form-item label="最多固化">
          <template #label>
            <ConfigHelpLabel
              label="最多固化"
              help-key="entityVersion.scopeMaxRows"
            />
          </template>
          <el-input-number v-model="scopeEditor.maxRows" :min="1" :max="500" /><span>行</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scopeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveScopeRelation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="固化范围预览" width="760px">
      <div class="preview-toolbar">
        <el-input
          v-model="previewRecordId"
          clearable
          placeholder="样例记录 ID（可选；留空只校验结构）"
          @keyup.enter="previewScope"
        />
        <el-button type="primary" :loading="previewLoading" @click="previewScope">
          {{ previewedRecordId ? '重新预览' : '使用样例预览' }}
        </el-button>
      </div>
      <el-alert
        v-if="previewResult?.valid === false || previewResult?.exceedsLimit"
        type="error"
        :title="previewResult.message || (previewResult?.exceedsLimit ? '样例记录超过固化上限，正式固化会整体失败' : '当前范围不能生成完整版本')"
        :closable="false"
        show-icon
      />
      <el-alert
        v-for="warning in previewResult?.warnings || []"
        :key="warning"
        :title="warning"
        type="warning"
        :closable="false"
        show-icon
        class="preview-warning"
      />
      <el-descriptions v-if="previewResult" :column="3" border class="preview-summary">
        <el-descriptions-item label="预计总行数">{{ previewedRecordId ? (previewResult.totalRows ?? '-') : '未计算' }}</el-descriptions-item>
        <el-descriptions-item label="预计大小">{{ previewedRecordId ? formatBytes(previewResult.estimatedBytes) : '未计算' }}</el-descriptions-item>
        <el-descriptions-item label="范围状态">{{ previewResult.valid === false || previewResult.exceedsLimit ? '不可固化' : (previewedRecordId ? '样例可固化' : '结构有效') }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="previewResult?.datasets || previewResult?.relations || previewResult?.nodes || []" border>
        <el-table-column prop="relationName" label="关联数据" min-width="200" />
        <el-table-column label="行数" width="100" align="center">
          <template #default="{ row }">
            {{ previewedRecordId ? (row.rowCount ?? '-') : '未计算' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="180"><template #default="{ row }">{{ row.message || (row.exceedsLimit ? '超过上限' : (previewedRecordId ? '正常' : '结构有效')) }}</template></el-table-column>
      </el-table>
      <el-empty v-if="!previewResult" description="输入样例记录 ID 可预估行数和大小；留空则只校验范围结构" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Delete, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import { entityVersionApi } from '@/api/entityVersion'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import { useUserStore } from '@/stores/user'
import {
  createVersionDraft,
  normalizePage,
  serializeVersionDraft
} from '@/shared/entity-version-model'

const sourceTypeOptions = [
  ['FORM', '表单'], ['LIST', '列表'], ['APPROVAL_TASK', '审批'],
  ['PROCESS_RUNTIME', '流程运行态'], ['FLOW_ACTION', '流程动作'],
  ['CUSTOM_INTERFACE', '自定义接口'], ['BATCH', '批量'], ['IMPORT', '导入'],
  ['SCHEDULED_JOB', '定时任务'], ['MESSAGE_CONSUMER', '消息消费'], ['SYSTEM_TASK', '系统任务']
].map(([value, label]) => ({ value, label }))
const operationTypeOptions = [
  ['CREATE', '新增'], ['UPDATE', '修改'], ['DELETE', '删除'],
  ['STATUS_CHANGE', '状态变化'], ['APPLY_CHANGE', '变更生效'], ['UPSERT', '新增或修改']
].map(([value, label]) => ({ value, label }))
const triggerTypeOptions = [
  { label: '根实体变化', value: 'ROOT_MUTATION' },
  { label: '关联数据变化', value: 'RELATED_MUTATION' },
  { label: '手工固化', value: 'MANUAL' }
]
const filterOperatorOptions = [
  ['EQ', '等于'], ['NE', '不等于'], ['GT', '大于'], ['GTE', '大于等于'],
  ['LT', '小于'], ['LTE', '小于等于'], ['IN', '包含于'], ['NOT_IN', '不包含于'],
  ['CONTAINS', '文本包含'], ['EMPTY', '为空'], ['NOT_EMPTY', '不为空']
].map(([value, label]) => ({ value, label }))

const userStore = useUserStore()
const configs = ref([])
const keyword = ref('')
const enabledFilter = ref('ALL')
const pageInfo = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const loading = ref(false)
const drawerVisible = ref(false)
const activeTab = ref('triggers')
const saving = ref(false)
const previewLoading = ref(false)
const previewVisible = ref(false)
const previewResult = ref(null)
const previewRecordId = ref('')
const previewedRecordId = ref('')
const viewportWidth = ref(typeof window === 'undefined' ? 1280 : window.innerWidth)
const baseline = ref('')
const baselineEnabled = ref(false)
const legacyConfig = ref(false)
const draft = reactive(createVersionDraft())
let listRequestSequence = 0

const canView = computed(() => hasPermission('entity:version:config:list'))
const canUpdate = computed(() => hasPermission('entity:version:config:update'))
const drawerSize = computed(() => viewportWidth.value <= 768 ? '100%' : '66.6667%')
const serializedDraft = computed(() => serializeVersionDraft(draft))
const isDirty = computed(() => drawerVisible.value && stableJson(serializedDraft.value) !== baseline.value)
const sortedTriggers = computed(() => [...draft.triggers].sort((a, b) => Number(b.priority || 0) - Number(a.priority || 0)))
const unusedRelationOptions = computed(() => {
  const selected = new Set(draft.snapshotScope.relations.map(item => item.relationCode))
  return draft.relationOptions.filter(item => !selected.has(item.relationCode))
})
const rootFieldOptions = computed(() => draft.fieldOptions || [])
const allFieldOptions = computed(() => {
  const values = [...rootFieldOptions.value]
  draft.relationOptions.forEach(relation => values.push(...(relation.fields || [])))
  return values.filter((item, index) => values.findIndex(candidate => fieldCode(candidate) === fieldCode(item)) === index)
})
const maxSizeMb = computed({
  get: () => Math.round(Number(draft.snapshotScope.limits.maxBytesPerVersion || 0) / 1024 / 1024),
  set: value => {
    draft.snapshotScope.limits.maxBytesPerVersion = Math.min(5, Math.max(1, Number(value || 1))) * 1024 * 1024
  }
})

const triggerDialogVisible = ref(false)
const triggerIndexValue = ref(-1)
const triggerEditor = reactive(emptyTrigger())
const scopeDialogVisible = ref(false)
const scopeIndex = ref(-1)
const scopeEditor = reactive(emptyScopeRelation())
const scopeRelationChoices = computed(() => scopeIndex.value >= 0
  ? [draft.relationOptions.find(item => item.relationCode === scopeEditor.relationCode) || scopeEditor]
  : unusedRelationOptions.value)
const scopeFieldOptions = computed(() =>
  draft.relationOptions.find(item => item.relationCode === scopeEditor.relationCode)?.fields || [])

useUnsavedChangesGuard(isDirty, {
  message: '数据版本配置有未保存修改，离开后这些修改将丢失。'
})

onMounted(loadConfigs)
onMounted(() => window.addEventListener('resize', updateViewport))
onBeforeUnmount(() => window.removeEventListener('resize', updateViewport))

async function loadConfigs() {
  const requestSequence = ++listRequestSequence
  loading.value = true
  try {
    const enabled = enabledFilter.value === 'ENABLED'
      ? true
      : enabledFilter.value === 'DISABLED' ? false : undefined
    const page = normalizePage(await entityVersionApi.listConfigPage({
      keyword: keyword.value.trim() || undefined,
      ...(enabled === undefined ? {} : { enabled }),
      pageNum: pageInfo.pageNum,
      pageSize: pageInfo.pageSize
    }), pageInfo.pageSize)
    if (requestSequence !== listRequestSequence) return
    configs.value = page.records
    pageInfo.total = page.total
    pageInfo.pageNum = page.pageNum
    pageInfo.pageSize = page.pageSize
  } catch (error) {
    if (requestSequence !== listRequestSequence) return
    ElMessage.error(error?.message || '加载数据版本配置失败')
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

/** 新查询条件从第一页开始；翻页加载本身不重置当前页。 */
function handleQuery() {
  pageInfo.pageNum = 1
  loadConfigs()
}

function handlePageSizeChange() {
  pageInfo.pageNum = 1
  loadConfigs()
}

async function openConfig(row) {
  if (!canView.value) return
  try {
    const data = await entityVersionApi.getConfig(row.entityCode)
    legacyConfig.value = Number(data?.schemaVersion || 1) < 2 || (!data?.triggers && Array.isArray(data?.scenarios))
    replaceDraft(createVersionDraft(data))
    activeTab.value = 'triggers'
    drawerVisible.value = true
    markBaseline()
    previewRecordId.value = ''
    previewedRecordId.value = ''
    previewResult.value = null
  } catch (error) {
    ElMessage.error(error?.message || '加载数据版本配置失败')
  }
}

/** 校验候选配置并通过 revision 乐观锁原子保存，成功后立即成为运行配置。 */
async function saveConfig() {
  if (!canUpdate.value) return
  saving.value = true
  try {
    if (!await validateForSave()) return
    if (!await confirmDisable()) return
    const payload = serializedDraft.value
    const saved = await entityVersionApi.saveConfig(
      draft.entityCode,
      payload,
      draft.revision
    )
    const merged = saved?.snapshotScope || saved?.triggers
      ? saved
      : { ...payload, ...saved, triggers: payload.triggers, snapshotScope: payload.snapshotScope, diffPolicy: payload.diffPolicy }
    replaceDraft(createVersionDraft(merged))
    legacyConfig.value = false
    markBaseline()
    ElMessage.success(draft.enabled ? '数据版本配置已保存并启用' : '数据版本配置已保存并停用')
    await loadConfigs()
  } catch (error) {
    if ([409, 412].includes(Number(error?.status))) {
      ElMessage.error('配置已被其他人更新，当前修改尚未保存。请重新打开最新配置后再修改。')
    } else {
      ElMessage.error(error?.message || '保存数据版本配置失败')
    }
  } finally {
    saving.value = false
  }
}

async function validateForSave() {
  const error = localValidationError()
  if (error) {
    ElMessage.warning(error)
    return false
  }
  try {
    const result = await entityVersionApi.validateConfig(draft.entityCode, serializedDraft.value)
    if (result?.valid === false || result?.errors?.length) {
      await ElMessageBox.alert(
        (result.errors || [result.message]).filter(Boolean).join('\n'),
        '保存校验未通过',
        { type: 'error' }
      ).catch(() => {})
      return false
    }
    if (result?.warnings?.length) {
      try {
        await ElMessageBox.confirm(result.warnings.join('\n'), '保存校验提示', {
          type: 'warning',
          confirmButtonText: '继续保存',
          cancelButtonText: '返回修改'
        })
      } catch {
        return false
      }
    }
    return true
  } catch (error) {
    ElMessage.error(error?.message || '保存校验失败')
    return false
  }
}

/** 停用只停止后续固化，不删除已生成的数据版本。 */
async function confirmDisable() {
  if (!baselineEnabled.value || draft.enabled) return true
  try {
    await ElMessageBox.confirm(
      '停用后将不再自动或手工生成新版本；停用前的历史版本仍可查看和比较。确定保存并停用吗？',
      '停用数据版本',
      {
        type: 'warning',
        confirmButtonText: '保存并停用',
        cancelButtonText: '返回修改'
      }
    )
    return true
  } catch {
    return false
  }
}

async function previewScope() {
  previewLoading.value = true
  try {
    const recordId = previewRecordId.value.trim()
    previewResult.value = await entityVersionApi.scopePreview(
      draft.entityCode,
      serializedDraft.value,
      recordId
    )
    previewedRecordId.value = recordId
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(error?.message || '固化范围预览失败')
  } finally {
    previewLoading.value = false
  }
}

function editTrigger(row, index = -1) {
  triggerIndexValue.value = index
  Object.assign(triggerEditor, emptyTrigger(), clone(row || {}), {
    conditionText: JSON.stringify(row?.condition || {}, null, 2)
  })
  triggerDialogVisible.value = true
}

function saveTrigger() {
  if (!triggerEditor.triggerName?.trim() || !triggerEditor.triggerCode?.trim()) return ElMessage.warning('请填写名称和稳定编码')
  if (triggerEditor.triggerType === 'RELATED_MUTATION' && !triggerEditor.relationCode) return ElMessage.warning('请选择关联范围')
  const condition = parseObjectText(triggerEditor.conditionText, '附加条件')
  if (!condition) return
  const code = triggerEditor.triggerCode.trim().toUpperCase()
  const duplicate = draft.triggers.some((item, index) => item.triggerCode === code && index !== triggerIndexValue.value)
  if (duplicate) return ElMessage.warning('生成时机编码不能重复')
  const item = clone({ ...triggerEditor, triggerCode: code, triggerName: triggerEditor.triggerName.trim(), condition })
  delete item.conditionText
  if (triggerIndexValue.value >= 0) draft.triggers.splice(triggerIndexValue.value, 1, item)
  else draft.triggers.push(item)
  triggerDialogVisible.value = false
}

async function removeTrigger(index) {
  const item = draft.triggers[index]
  if (!item) return
  await ElMessageBox.confirm(`确定删除生成时机“${item.triggerName}”吗？`, '删除生成时机', { type: 'warning' })
  draft.triggers.splice(index, 1)
}

function editScopeRelation(row, index = -1) {
  scopeIndex.value = index
  Object.assign(scopeEditor, emptyScopeRelation(), clone(row || {}))
  if (index < 0 && unusedRelationOptions.value.length === 1) {
    scopeEditor.relationCode = unusedRelationOptions.value[0].relationCode
    applyRelationOption(scopeEditor.relationCode)
  }
  scopeDialogVisible.value = true
}

function applyRelationOption(code) {
  const option = draft.relationOptions.find(item => item.relationCode === code)
  if (!option) return
  Object.assign(scopeEditor, {
    nodeCode: option.relationCode,
    relationCode: option.relationCode,
    relationName: option.relationName,
    childEntityCode: option.childEntityCode,
    childEntityName: option.childEntityName,
    maxRows: Number(draft.snapshotScope.limits.maxRowsPerRelation || 500)
  })
}

function saveScopeRelation() {
  if (!scopeEditor.relationCode) return ElMessage.warning('请选择实体关系')
  if (scopeEditor.fieldMode === 'SELECTED' && !scopeEditor.fieldCodes.length) return ElMessage.warning('指定字段模式下至少选择一个字段')
  const invalid = scopeEditor.filter.conditions.some(condition => !condition.fieldCode || !condition.operator)
  if (invalid) return ElMessage.warning('请完整填写过滤字段和操作符')
  const item = clone(scopeEditor)
  if (scopeIndex.value >= 0) draft.snapshotScope.relations.splice(scopeIndex.value, 1, item)
  else draft.snapshotScope.relations.push(item)
  scopeDialogVisible.value = false
}

async function removeScopeRelation(index) {
  const relation = draft.snapshotScope.relations[index]
  if (!relation) return
  const triggerCount = draft.triggers.filter(item => item.relationCode === relation.relationCode).length
  const suffix = triggerCount ? `；同时会删除引用它的 ${triggerCount} 个关联变化触发器` : ''
  await ElMessageBox.confirm(`确定从版本范围移除“${relation.relationName}”${suffix}吗？`, '移除关联范围', { type: 'warning' })
  draft.snapshotScope.relations.splice(index, 1)
  draft.triggers = draft.triggers.filter(item => item.relationCode !== relation.relationCode)
}

function addFilterCondition() {
  scopeEditor.filter.conditions.push({ fieldCode: '', operator: 'EQ', value: '' })
}

function normalizeFilterValue(condition) {
  if (['IN', 'NOT_IN'].includes(condition.operator)) {
    if (!Array.isArray(condition.value)) {
      condition.value = condition.value === '' || condition.value == null
        ? []
        : [condition.value]
    }
    return
  }
  if (['EMPTY', 'NOT_EMPTY'].includes(condition.operator)) {
    condition.value = null
    return
  }
  if (Array.isArray(condition.value)) {
    condition.value = condition.value[0] ?? ''
  }
}

function onTriggerTypeChange(type) {
  if (type !== 'RELATED_MUTATION') triggerEditor.relationCode = ''
  if (type === 'MANUAL') {
    triggerEditor.sourceTypes = []
    triggerEditor.operationTypes = []
    triggerEditor.businessIntents = []
  }
}

async function handleDrawerClose(done) {
  if (!isDirty.value) return done()
  try {
    await ElMessageBox.confirm('当前配置有未保存修改，关闭后修改将丢失。', '放弃修改？', {
      type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑'
    })
    done()
  } catch {
    // 继续编辑。
  }
}

function localValidationError() {
  if (draft.enabled && !draft.triggers.some(item => item.enabled !== false)) return '启用数据版本时至少需要一个有效生成时机'
  if (draft.snapshotScope.root.fieldMode === 'SELECTED' && !draft.snapshotScope.root.fieldCodes.length) return '根实体指定字段模式下至少选择一个字段'
  return ''
}

function replaceDraft(value) {
  Object.keys(draft).forEach(key => delete draft[key])
  Object.assign(draft, clone(value))
}
function markBaseline() {
  baseline.value = stableJson(serializeVersionDraft(draft))
  baselineEnabled.value = draft.enabled === true
}
function stableJson(value) { return JSON.stringify(value) }
function clone(value) { return JSON.parse(JSON.stringify(value || {})) }
function parseObjectText(value, label) { try { const parsed = JSON.parse(value || '{}'); if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error(); return parsed } catch { ElMessage.warning(`${label}必须是 JSON 对象`); return null } }
function triggerIndex(row) { return draft.triggers.indexOf(row) }
function emptyTrigger() {
  return { triggerCode: '', triggerName: '', triggerType: 'ROOT_MUTATION', relationCode: '', sourceTypes: [], operationTypes: [], businessIntents: [], condition: {}, conditionText: '{}', versionTitleTemplate: 'V${versionNo} ${triggerName}', priority: 0, enabled: true }
}
function emptyScopeRelation() {
  return { nodeCode: '', relationCode: '', relationName: '', childEntityCode: '', childEntityName: '', fieldMode: 'ALL_PUBLISHED', fieldCodes: [], filter: { logic: 'ALL', conditions: [] }, maxRows: 500, enabled: true }
}
function hasPermission(permission) { return userStore.isSuperAdmin || userStore.permissions.includes('*') || userStore.permissions.includes(permission) }
function updateViewport() { viewportWidth.value = window.innerWidth }
function configStatus(row) {
  if ((row.runtimeEnabled ?? row.enabled) === true) {
    return { label: '已启用', type: 'success' }
  }
  return Number(row.revision || 0) > 0
    ? { label: '已停用', type: 'info' }
    : { label: '未配置', type: 'info' }
}
function relationName(code) { return draft.snapshotScope.relations.find(item => item.relationCode === code)?.relationName || code || '-' }
function fieldCode(field) { return field?.fieldCode || field?.code || String(field || '') }
function fieldLabel(field) { const code = fieldCode(field); return `${field?.fieldName || field?.label || code}${field?.fieldName || field?.label ? `（${code}）` : ''}` }
function filterSummary(filter) { const count = filter?.conditions?.length || 0; return count ? `${count} 个条件 · ${filter.logic === 'ANY' ? '任一满足' : '全部满足'}` : '不过滤' }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-' }
function formatBytes(value) { const bytes = Number(value); return Number.isFinite(bytes) ? `${(bytes / 1024 / 1024).toFixed(2)} MiB` : '-' }
function labelOf(options, value) { return options.find(item => item.value === value)?.label || value }
const sourceTypeText = value => labelOf(sourceTypeOptions, value)
const operationTypeText = value => labelOf(operationTypeOptions, value)
const triggerTypeText = value => labelOf(triggerTypeOptions, value)
</script>

<style scoped>
.version-management { padding: 20px; }
.page-heading, .drawer-heading, .table-toolbar, .drawer-actions, .heading-line, .scope-card__title, .limit-panel__heading { display: flex; align-items: center; }
.page-heading, .drawer-heading, .scope-card__title { justify-content: space-between; }
.page-heading { margin-bottom: 16px; }
.page-heading h2, .drawer-heading h3 { margin: 0; }
.page-heading p, .section-intro p, .limit-panel p { margin: 5px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.content-panel { padding: 16px; background: var(--el-bg-color); border: 1px solid var(--el-border-color-light); border-radius: 8px; }
.table-toolbar { flex-wrap: wrap; gap: 10px; margin-bottom: 14px; }
.table-toolbar .el-input { width: 320px; }
.status-filter-field { display: flex; align-items: center; gap: 8px; color: var(--el-text-color-regular); font-size: 14px; }
.status-filter { width: 160px; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.primary-text { color: var(--el-text-color-primary); font-weight: 600; }
.secondary-text, .drawer-heading span { color: var(--el-text-color-secondary); font-size: 13px; }
.drawer-heading { width: 100%; padding-right: 18px; gap: 16px; }
.drawer-actions, .heading-line { gap: 8px; }
.drawer-actions { flex-wrap: wrap; justify-content: flex-end; }
.header-switch { margin: 0 4px 0 0; }
.save-effect-alert, .legacy-alert { margin-bottom: 12px; }
.section-intro { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; margin: 10px 0 16px; }
.el-table .el-tag { margin: 2px 4px 2px 0; }
.scope-card { margin-bottom: 16px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); }
.root-card { border-left: 4px solid var(--el-color-primary); }
.scope-card__title { padding: 14px 16px; border-bottom: 1px solid var(--el-border-color-light); }
.scope-card__title div { display: flex; flex-direction: column; gap: 4px; }
.scope-card__title span { color: var(--el-text-color-secondary); font-size: 12px; }
.scope-card__body { display: grid; grid-template-columns: minmax(320px, auto) minmax(260px, 1fr); align-items: center; gap: 12px; padding: 16px; }
.scope-card__body .el-form-item { margin-bottom: 0; }
.scope-table { margin-bottom: 16px; }
.limit-panel { padding: 16px; background: var(--el-fill-color-lighter); border-radius: 8px; }
.limit-panel__heading { gap: 4px; }
.limit-panel .el-form { margin-top: 14px; }
.limit-panel .el-form-item span, .condition-row + span { margin-left: 6px; }
.diff-form { max-width: 900px; padding-top: 12px; }
.diff-form .el-select { width: 100%; }
.form-help { margin-left: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.inline-warning { width: 100%; margin-top: 6px; color: var(--el-color-warning); font-size: 13px; }
.condition-help { width: 100%; margin-top: 5px; color: var(--el-text-color-secondary); font-size: 12px; }
.condition-list { width: 100%; }
.condition-row { display: grid; grid-template-columns: minmax(180px, 1.2fr) 150px minmax(180px, 1fr) 36px; gap: 8px; margin-bottom: 8px; }
.condition-placeholder { display: flex; align-items: center; padding: 0 12px; color: var(--el-text-color-secondary); background: var(--el-fill-color-light); border-radius: 4px; }
.preview-toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
.preview-toolbar .el-input { flex: 1; }
.preview-warning + .preview-warning { margin-top: 8px; }
.preview-summary { margin: 14px 0; }
:deep(.config-drawer .el-drawer__body) { overflow-y: auto; padding-top: 0; }
:deep(.config-tabs .el-tabs__content) { overflow: visible; }

@media (max-width: 900px) {
  .version-management { padding: 12px; }
  .drawer-heading, .section-intro { flex-direction: column; align-items: stretch; }
  .table-toolbar .el-input, .status-filter-field { width: 100%; }
  .status-filter { flex: 1; width: auto; }
  .pagination { justify-content: center; overflow-x: auto; }
  .drawer-actions { justify-content: flex-start; }
  .scope-card__body { grid-template-columns: 1fr; }
  .condition-row { grid-template-columns: 1fr; padding-bottom: 10px; border-bottom: 1px solid var(--el-border-color-light); }
  .preview-toolbar { flex-direction: column; }
}
</style>
