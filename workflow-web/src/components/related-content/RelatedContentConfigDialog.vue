<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="min(1120px, 96vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    class="related-content-config-dialog"
  >
    <template #header>
      <div class="dialog-heading">
        <div>
          <strong>{{ dialogTitle }}</strong>
          <span>{{ isRelationBound ? '已引用实体关系，只需设置展示和操作' : '按引导完成配置，不需要编写脚本或查询语句' }}</span>
        </div>
        <el-tag type="primary" effect="plain">第 {{ stepNumber(activeStep) }} 步，共 {{ stepIds.length }} 步</el-tag>
      </div>
    </template>

    <div v-loading="catalogLoading" class="related-content-editor">
      <div class="configuration-summary">
        <div class="configuration-summary__icon">
          <el-icon><Connection /></el-icon>
        </div>
        <div>
          <span>当前配置效果</span>
          <strong>{{ naturalSummary }}</strong>
          <small>{{ relationSummary }}</small>
        </div>
      </div>

      <section v-if="isRelationBound" class="entity-relation-binding">
        <div class="relation-binding-heading">
          <strong>继承实体关系：{{ boundEntityRelation?.relationName || boundRelationCode || '请选择' }}</strong>
          <el-button link type="primary" @click="openEntityRelationManagement">去实体设计修改关系</el-button>
        </div>
        <el-select v-if="canSelectBinding" v-model="boundRelationKey" filterable placeholder="选择已定义的实体关系" style="width: 100%; margin-bottom: 12px" @change="selectBinding">
          <el-option v-for="relation in sourceRelations" :key="relationUsageKey(relation)"
            :value="relationUsageKey(relation)" :disabled="relation.enabled === false || relation.enabled === 0"
            :label="`${relation.relationName} · ${relationTarget(relation).entityName} · ${relation.direction === 'REVERSE' ? '所属记录' : relation.relationType === 'ONE_TO_ONE' ? '一对一' : '一对多'}`" />
        </el-select>
        <el-alert v-if="canSelectBinding && !catalogLoading && !sourceRelations.length && !relationBindingError" title="请先在实体设计中定义关系，再返回这里选择展示页面。" type="info" :closable="false" />
        <el-alert v-if="relationBindingError" :title="relationBindingError" type="error" :closable="false" show-icon />
        <template v-else-if="boundEntityRelation">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="关联实体">{{ relationTarget(boundEntityRelation).entityName }}</el-descriptions-item>
            <el-descriptions-item label="关联数量">{{ boundEntityRelation.direction === 'REVERSE' ? '所属记录 · 表单' : relationContentType(boundEntityRelation) === 'FORM' ? '一对一 · 表单' : '一对多 · 列表' }}</el-descriptions-item>
            <el-descriptions-item label="匹配规则" :span="2">{{ relationMatchLabel(boundEntityRelation) }}</el-descriptions-item>
          </el-descriptions>
          <p>目标实体、匹配字段和关联数量统一由实体关系决定。这里的设置只影响当前页面，发布当前页面后生效。</p>
        </template>
      </section>

      <el-steps :active="stepNumber(activeStep) - 1" align-center finish-status="success">
        <el-step title="显示什么" description="目标和位置" />
        <el-step v-if="!isRelationBound" title="数据来源" description="当前记录或扩展接口" />
        <el-step title="允许做什么" description="查看或操作范围" />
        <el-step title="特殊情况" description="复杂能力兜底" />
      </el-steps>

      <div class="step-content">
        <section v-show="activeStep === 1">
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
                  @selected="handleTargetEntitySelected"
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
                    @change="handleContentTypeChange"
                  />
                  <el-select
                    v-model="editor.config.target.contentId"
                    filterable
                    :loading="targetCatalogLoading"
                    :placeholder="editor.config.target.contentType === 'FORM' ? '选择已发布表单' : '选择已发布列表'"
                    style="width: 100%"
                    @change="handleTargetContentChange"
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
                  @change="handlePositionChange"
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
                  @change="handleAnchorChange"
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

        <section v-if="!isRelationBound" v-show="activeStep === 2">
          <StepHeading
            number="2"
            title="数据来源"
            description="扩展页面使用当前记录或受控接口取数；业务关联请通过实体关系配置。"
          />
          <el-alert
            v-if="recommendationText"
            :title="recommendationText"
            type="success"
            :closable="false"
            show-icon
            class="recommendation-alert"
          />
          <el-form label-width="126px" class="related-form">
            <el-form-item required>
              <template #label>
                <ConfigHelpLabel
                  label="数据来源"
                  content="当前记录用于同一条数据的另一张页面；扩展接口用于受控查询或计算，仍需遵守实体数据权限。"
                />
              </template>
              <div class="relation-methods">
                <button
                  v-for="option in relationOptions"
                  :key="option.value"
                  type="button"
                  class="selection-card"
                  :class="{ 'is-selected': editor.config.relation.type === option.value }"
                  @click="handleRelationTypeChange(option.value)"
                >
                  <span>
                    {{ option.label }}
                    <el-tag
                      v-if="recommendedRelation.type === option.value"
                      type="success"
                      size="small"
                      effect="plain"
                    >推荐</el-tag>
                  </span>
                  <small>{{ option.description }}</small>
                </button>
              </div>
            </el-form-item>

            <el-alert
              v-if="editor.config.relation.type === 'INTERFACE_SERVICE'"
              title="请在第 4 步选择负责查找目标数据的扩展接口。普通关联失败时不会自动调用扩展接口。"
              type="info"
              :closable="false"
              show-icon
            />
          </el-form>

          <div class="relation-preview">
            <div>
              <strong>关联结果说明</strong>
              <span>{{ relationSummary }}</span>
            </div>
            <div class="real-record-test">
              <EntitySelector
                v-if="sourceEntity.entityCode"
                v-model="sourceTestRecordId"
                entity-type="CUSTOM"
                :entity-code="sourceEntity.entityCode"
                title="选择用于测试的来源记录"
                placeholder="自动选择一条有权查看的记录"
                @change="testResult = null"
              />
              <el-button
                :loading="testing"
                :disabled="!canTest"
                @click="testWithRecord"
              >
                使用一条真实数据测试
              </el-button>
            </div>
          </div>
          <div v-if="testResult" class="real-record-test-result">
            <el-alert
              :title="testResult.title"
              :description="testResult.description"
              :type="testResult.type"
              :closable="true"
              show-icon
              @close="testResult = null"
            />
            <el-descriptions
              v-if="testResult.details"
              :column="2"
              border
              size="small"
            >
              <el-descriptions-item label="来源记录">
                {{ testSourceLabel(testResult.details.sourceRecord) }}
              </el-descriptions-item>
              <el-descriptions-item label="匹配结果">
                {{ Number(testResult.details.matchedCount || 0) }} 条有权查看的数据
              </el-descriptions-item>
              <el-descriptions-item label="实际筛选条件" :span="2">
                {{ testFilterSummary(testResult.details.filters) }}
              </el-descriptions-item>
              <el-descriptions-item label="权限检查">
                {{ testResult.details.authorized === false ? '未通过' : '已通过' }}
              </el-descriptions-item>
              <el-descriptions-item label="目标记录示例">
                {{ testTargetSummary(testResult.details.targetRecordIds) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </section>

        <section v-show="activeStep === 3">
          <StepHeading
            :number="String(stepNumber(3))"
            title="允许做什么"
            description="选择用户可以执行的动作。目标实体的权限和数据范围仍会独立校验。"
          />
          <div class="action-grid">
            <label
              v-for="option in actionOptions"
              :key="option.value"
              class="action-card"
              :class="{
                'is-selected': editor.config.actions.includes(option.value),
                'is-disabled': isActionDisabled(option.value)
              }"
            >
              <el-checkbox
                :model-value="editor.config.actions.includes(option.value)"
                :disabled="isActionDisabled(option.value)"
                @change="toggleAction(option.value, $event)"
              >
                {{ option.label }}
              </el-checkbox>
              <ConfigHelpLabel
                :label="option.label"
                :show-label="false"
                :content="`是什么：${option.description} 何时使用：仅在用户确实需要该操作时开启。结果：平台仍会重新校验目标记录与动作权限。`"
              />
              <small>{{ option.description }}</small>
            </label>
          </div>

          <div
            v-if="editor.config.actions.includes('SELECT')"
            class="action-settings-card"
          >
            <div class="action-settings-card__heading">
              <strong>选择记录后怎么处理</strong>
              <span>用户只提交选中的记录 ID，平台会重新读取数据并校验权限后再回填或建立关系。</span>
            </div>
            <el-form label-position="top">
              <div class="two-column-form">
                <el-form-item>
                  <template #label>
                    <ConfigHelpLabel
                      label="选择方式"
                      content="是什么：限制一次可以选择多少条目标记录。何时使用：普通字段回填选单条，建立多条关系时可选多条。结果：服务端只处理最终确认且仍有权访问的记录。"
                    />
                  </template>
                  <el-radio-group v-model="editor.config.actionSettings.select.mode">
                    <el-radio-button value="SINGLE">单选（推荐）</el-radio-button>
                    <el-radio-button value="MULTIPLE">多选</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item>
                  <template #label>
                    <ConfigHelpLabel
                      label="选择结果"
                      content="是什么：确认选择后平台执行的结果。何时使用：把目标信息带回当前表单时选回填字段；只维护两条记录关系时选建立关联。结果：不会直接修改未配置的字段。"
                    />
                  </template>
                  <el-radio-group v-model="editor.config.actionSettings.select.result">
                    <el-radio-button value="FILL_FIELDS">回填当前表单</el-radio-button>
                    <el-radio-button value="LINK">建立关联</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </div>
            </el-form>
            <ServiceMappingEditor
              v-if="editor.config.actionSettings.select.result === 'FILL_FIELDS'"
              v-model="editor.config.actionSettings.select.mappings"
              title="选择结果回填"
              description="只允许从目标实体的可见字段回填到当前表单的可编辑字段。"
              left-label="目标记录字段"
              right-label="当前表单字段"
              :left-options="targetReadableFieldOptions"
              :right-options="sourceEditableFieldOptions"
              empty-text="尚未配置回填字段"
            />
            <el-alert
              v-else
              title="请同时启用“建立关联”；关系会由服务端按当前关联方式处理。"
              type="info"
              :closable="false"
              show-icon
            />
          </div>

          <div
            v-if="editor.config.actions.includes('CREATE')"
            class="action-settings-card"
          >
            <div class="action-settings-card__heading">
              <strong>新增记录设置</strong>
              <span>初值由平台根据当前记录生成；保存时会重新取值并覆盖对应目标字段。</span>
            </div>
            <el-form label-position="top">
              <el-form-item>
                <template #label>
                    <ConfigHelpLabel
                      label="新增后自动建立关联"
                      content="是什么：目标记录新增成功后，同时维护它与当前记录的关系。何时使用：需要创建并关联一次完成时。结果：当前版本尚未接入同一事务，因此不能启用；可新增成功后再使用“建立关联”。"
                    />
                </template>
                <el-switch
                  v-model="editor.config.actionSettings.create.associateAfterCreate"
                  :disabled="editor.config.actionSettings.create.associateAfterCreate !== true"
                />
              </el-form-item>
            </el-form>
            <el-alert
              title="当前版本不支持新增并自动关联在同一事务完成；请保持关闭，新增后再建立关联。"
              type="info"
              :closable="false"
              show-icon
            />
            <ServiceMappingEditor
              v-model="editor.config.actionSettings.create.initialMappings"
              title="新增初值"
              description="把当前记录字段作为目标表单初值；目标保存权限和字段规则仍会再次校验。"
              left-label="当前记录字段"
              right-label="目标记录字段"
              :left-options="sourceReadableFieldOptions"
              :right-options="targetEditableFieldOptions"
              empty-text="没有额外新增初值"
            />
          </div>

          <el-alert
            v-if="editor.config.actions.includes('LINK') || editor.config.actions.includes('UNLINK')"
            title="建立或解除关联只修改关系，不会删除目标记录。平台会校验两侧记录权限、当前关系和基数限制。"
            type="info"
            :closable="false"
            show-icon
            class="save-boundary-alert"
          />
          <el-alert
            :title="saveBoundaryTitle"
            :description="saveBoundaryDescription"
            :type="editor.config.actions.includes('SAVE_WITH_FORM') ? 'error' : 'warning'"
            :closable="false"
            show-icon
            class="save-boundary-alert"
          />
          <div class="permission-summary">
            <strong>最终权限如何确定</strong>
            <span>当前页面权限、来源记录权限、目标内容权限、目标数据范围和字段权限会同时生效；关联条件只能缩小范围。</span>
          </div>
        </section>

        <section v-show="activeStep === 4">
          <StepHeading
            :number="String(stepNumber(4))"
            title="特殊情况怎么处理"
            description="普通配置无法满足时，选择开发人员已经注册的扩展接口或自定义组件。"
          />
          <el-alert
            v-if="specialMode === 'NONE'"
            title="当前配置可由平台默认能力完成，无需特殊处理。"
            type="success"
            :closable="false"
            show-icon
          />
          <el-alert v-if="isRelationBound && useDataInterfaceService" type="warning" :closable="false"
            title="此历史配置使用了取数接口。请移除接口取数，恢复按实体关系查找数据后再保存。">
            <el-button link type="primary" @click="toggleDataInterfaceService(false)">改为使用实体关系取数</el-button>
          </el-alert>
          <el-collapse v-model="advancedSections" class="special-collapse">
            <el-collapse-item name="special">
              <template #title>
                <div class="collapse-heading">
                  <span>特殊处理</span>
                  <small>复杂查询、外部能力或专业界面</small>
                </div>
              </template>

              <div class="special-choice-grid">
                <label class="special-choice" :class="{ 'is-selected': useInterfaceService }">
                  <el-checkbox
                    :model-value="useInterfaceService"
                    :disabled="editor.config.relation.type === 'INTERFACE_SERVICE'"
                    @change="toggleSpecial('INTERFACE_SERVICE', $event)"
                  >{{ isRelationBound ? '使用动作扩展接口' : '使用数据或动作扩展接口' }}</el-checkbox>
                  <small>{{ isRelationBound ? '扩展受控业务操作，数据仍按实体关系查找。' : '复杂查询、计算、聚合或受控业务操作。' }}</small>
                </label>
                <label class="special-choice" :class="{ 'is-selected': useCustomComponent }">
                  <el-checkbox
                    :model-value="useCustomComponent"
                    @change="toggleSpecial('CUSTOM_COMPONENT', $event)"
                  >使用自定义组件</el-checkbox>
                  <small>甘特图、地图、拓扑图、看板和其他专业交互。</small>
                </label>
              </div>

              <div v-if="useInterfaceService" class="special-config-card">
                <div class="special-config-card__heading">
                  <div>
                    <strong>{{ isRelationBound ? '动作扩展接口' : '数据或动作扩展接口' }}</strong>
                    <span>只能选择已注册能力，页面不能填写地址、密钥、脚本或 SQL。</span>
                  </div>
                  <el-tag effect="plain">服务端重新鉴权</el-tag>
                </div>
                <el-alert v-if="isRelationBound" type="info" :closable="false"
                  title="目标数据由实体关系确定；可扩展操作或展示组件，无需另外配置取数接口。" />
                <el-checkbox
                  v-if="!isRelationBound"
                  :model-value="useDataInterfaceService"
                  :disabled="editor.config.relation.type === 'INTERFACE_SERVICE'"
                  class="data-service-toggle"
                  @change="toggleDataInterfaceService"
                >使用接口查找目标数据</el-checkbox>
                <el-form v-if="useDataInterfaceService && !isRelationBound" label-width="104px">
                  <div class="two-column-form">
                    <el-form-item required>
                      <template #label>
                        <ConfigHelpLabel
                          label="扩展接口"
                          content="是什么：开发人员注册并声明输入输出的完整业务接口。何时使用：普通关系无法表达复杂查询、计算或外部系统调用时。结果：运行时按当前用户权限调用固定接口。"
                        />
                      </template>
                      <el-select
                        v-model="editor.config.specialHandling.interfaceService.extensionId"
                        filterable
                        placeholder="选择已注册扩展接口"
                        style="width: 100%"
                        @change="handleDataInterfaceChange"
                      >
                        <el-option
                          v-for="item in dataInterfaces"
                          :key="item.extensionId"
                          :label="item.displayName || item.extensionKey"
                          :value="item.extensionId"
                        >
                          <div class="business-option">
                            <span>{{ item.displayName || item.extensionKey }}</span>
                            <small>{{ item.extensionKey }} · {{ interfaceScopeLabel(item) }}</small>
                          </div>
                        </el-option>
                      </el-select>
                    </el-form-item>
                  </div>

                  <ServiceMappingEditor
                    v-model="editor.config.specialHandling.interfaceService.inputMappings"
                    title="输入字段映射"
                    description="把当前记录字段传给扩展接口。只有接口声明过的参数可选。"
                    :left-options="sourceFieldOptions"
                    :right-options="interfaceInputOptions"
                    left-label="当前字段"
                    right-label="接口参数"
                    empty-text="该接口暂无输入映射"
                  />
                  <ServiceMappingEditor
                    v-model="editor.config.specialHandling.interfaceService.outputMappings"
                    title="关联结果映射"
                    description="告诉平台接口返回的哪一项代表目标记录、目标筛选条件或明确的空结果。"
                    :left-options="interfaceOutputOptions"
                    :right-options="interfaceResultOptions"
                    left-label="返回结果"
                    right-label="用于查找目标数据"
                    empty-text="尚未说明如何从接口结果找到目标数据"
                    class="mapping-section"
                  />
                </el-form>

                <div class="action-service-section">
                  <div class="special-config-card__heading">
                    <div>
                      <strong>操作接口（可选）</strong>
                      <span>把一个已注册扩展接口绑定到页面操作；运行时只使用发布时固定的接口和字段映射。</span>
                    </div>
                    <el-button type="primary" plain :icon="Plus" @click="addActionService">
                      增加操作接口
                    </el-button>
                  </div>
                  <el-alert
                    title="只读操作可同步返回校验、计算或界面结果；本地写入会重新校验实体、记录和字段权限。外部写入需要异步任务能力，当前不可发布。"
                    type="info"
                    :closable="false"
                    show-icon
                  />
                  <el-card
                    v-for="(binding, bindingIndex) in editor.config.specialHandling.actionServices"
                    :key="bindingIndex"
                    shadow="never"
                    class="action-service-card"
                  >
                    <template #header>
                      <div class="special-config-card__heading">
                        <strong>操作接口 {{ bindingIndex + 1 }}</strong>
                        <el-button text type="danger" @click="removeActionService(bindingIndex)">删除</el-button>
                      </div>
                    </template>
                    <el-form label-width="104px">
                      <div class="two-column-form">
                        <el-form-item required>
                          <template #label>
                            <ConfigHelpLabel
                              label="扩展接口"
                              content="是什么：开发人员注册的完整受控业务接口。何时使用：页面标准操作不能完成复杂校验、计算或批量写入时。结果：发布后固定接口定义，不能由浏览器临时替换。"
                            />
                          </template>
                          <el-select
                            v-model="binding.extensionId"
                            filterable
                            placeholder="选择已注册扩展接口"
                            style="width: 100%"
                            @change="handleActionInterfaceChange(binding)"
                          >
                            <el-option
                              v-for="item in actionInterfaces"
                              :key="item.extensionId"
                              :label="item.displayName || item.extensionKey"
                              :value="item.extensionId"
                            >
                              <div class="business-option">
                                <span>{{ item.displayName || item.extensionKey }}</span>
                                <small>{{ actionInterfaceDescription(item) }}</small>
                              </div>
                            </el-option>
                          </el-select>
                        </el-form-item>
                        <el-form-item required>
                          <template #label>
                            <ConfigHelpLabel
                              label="用于操作"
                              content="是什么：用户执行哪个页面操作时调用该接口。何时使用：可选择已启用的标准操作，或使用扩展接口自己的业务名称作为独立操作。结果：自定义组件只能调用这里明确发布的接口。"
                            />
                          </template>
                          <el-select v-model="binding.actionKey" placeholder="选择页面操作" style="width: 100%">
                            <el-option
                              v-for="option in actionKeyOptions(binding)"
                              :key="option.value"
                              :label="option.label"
                              :value="option.value"
                            />
                          </el-select>
                        </el-form-item>
                        <el-form-item required>
                          <template #label>
                            <ConfigHelpLabel
                              label="失败时"
                              content="是什么：该接口动作失败后的处理。何时使用：只读动作可显示错误、占位或隐藏结果；写入动作必须停止并显示错误。结果：不会忽略权限或继续执行不完整写入。"
                            />
                          </template>
                          <el-select v-model="binding.failurePolicy" style="width: 100%">
                            <el-option
                              v-for="option in actionFailureOptions(binding)"
                              :key="option.value"
                              :label="option.label"
                              :value="option.value"
                            />
                          </el-select>
                        </el-form-item>
                      </div>
                      <ServiceMappingEditor
                        v-model="binding.inputMappings"
                        title="输入字段映射"
                        description="只传当前记录、已选择目标记录中明确选择的字段，不会把整行数据交给接口。"
                        :left-options="actionInputSourceOptions"
                        :right-options="actionInputOptions(binding)"
                        left-label="页面数据"
                        right-label="接口参数"
                        empty-text="未配置时只传当前记录 ID"
                      />
                      <ServiceMappingEditor
                        v-model="binding.outputMappings"
                        title="输出字段映射"
                        description="选择允许返回给页面或自定义组件的结果字段。"
                        :left-options="actionOutputOptions(binding)"
                        :right-options="actionOutputOptions(binding)"
                        left-label="接口结果"
                        right-label="页面结果"
                        empty-text="未配置时按接口已声明的输出结构返回"
                      />
                    </el-form>
                  </el-card>
                  <el-empty
                    v-if="!editor.config.specialHandling.actionServices.length"
                    description="暂无操作接口"
                    :image-size="52"
                  />
                </div>
              </div>

              <div v-if="useCustomComponent" class="special-config-card">
                <div class="special-config-card__heading">
                  <div>
                    <strong>自定义组件</strong>
                    <span>仅允许平台审核并随应用部署的组件；平台查询和动作入口仍会独立校验权限。</span>
                  </div>
                  <el-tag effect="plain">固定精确版本</el-tag>
                </div>
                <el-form label-width="104px">
                  <div class="two-column-form">
                    <el-form-item required>
                      <template #label>
                        <ConfigHelpLabel
                          label="自定义组件"
                          content="是什么：开发人员提交并经平台审核的专业展示与交互组件，不是运行任意第三方脚本的沙箱。何时使用：普通表单或列表无法呈现甘特图、地图、拓扑等界面时。结果：发布后固定已审核版本，通过平台入口查询或操作时仍会重新校验权限。"
                        />
                      </template>
                      <el-select
                        v-model="editor.config.specialHandling.customComponent.name"
                        filterable
                        placeholder="选择已注册组件"
                        style="width: 100%"
                        @change="handleCustomComponentChange"
                      >
                        <el-option
                          v-for="component in customComponentOptions"
                          :key="component.value"
                          :label="component.label"
                          :value="component.value"
                        >
                          <div class="business-option">
                            <span>{{ component.label }}</span>
                            <small><template v-if="component.description">{{ component.description }}</template></small>
                          </div>
                        </el-option>
                      </el-select>
                    </el-form-item>
                    <el-form-item required>
                      <template #label>
                        <ConfigHelpLabel
                          label="组件版本"
                          content="是什么：该组件已经注册的精确版本。何时使用：已有页面需要保持原交互时选原版本，新能力经过验证后再升级。结果：发布后不会自动跟随最新版。"
                        />
                      </template>
                      <el-select
                        v-model="editor.config.specialHandling.customComponent.version"
                        placeholder="选择精确版本"
                        style="width: 100%"
                        @change="handleCustomComponentVersionChange"
                      >
                        <el-option
                          v-for="component in customComponentVersionOptions"
                          :key="component.version"
                          :label="`版本 ${component.version}`"
                          :value="Number(component.version)"
                        />
                      </el-select>
                    </el-form-item>
                  </div>
                  <ConfigSchemaEditor
                    v-if="selectedCustomComponentSchema.length"
                    v-model="editor.config.specialHandling.customComponent.props"
                    :schema="selectedCustomComponentSchema"
                  />
                  <el-empty v-else description="该组件无需额外参数" :image-size="52" />
                </el-form>
              </div>

              <el-form v-if="specialMode !== 'NONE'" label-width="104px" class="failure-policy-form">
                <el-form-item>
                  <template #label>
                    <ConfigHelpLabel
                      label="失败时"
                      content="是什么：扩展接口或组件不可用时的页面表现。何时使用：所有特殊处理都必须明确选择。结果：只显示错误、占位或隐藏，不会退化为查询全部或忽略权限。"
                    />
                  </template>
                  <el-radio-group v-model="editor.config.specialHandling.failurePolicy">
                    <el-radio
                      v-for="option in failureOptions"
                      :key="option.value"
                      :value="option.value"
                    >{{ option.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>

          <div class="review-card">
            <div class="review-card__heading">
              <strong>保存前确认</strong>
              <el-tag :type="localValidation.valid ? 'success' : 'danger'" effect="plain">
                {{ localValidation.valid ? '配置完整' : `${localValidation.errors.length} 项待完善` }}
              </el-tag>
            </div>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="显示效果">{{ naturalSummary }}</el-descriptions-item>
              <el-descriptions-item label="数据关联">{{ relationSummary }}</el-descriptions-item>
              <el-descriptions-item label="允许操作">{{ actionSummary }}</el-descriptions-item>
              <el-descriptions-item label="特殊处理">{{ specialSummary }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="!localValidation.valid" class="validation-errors">
              <button
                v-for="error in localValidation.errors"
                :key="`${error.step}-${error.field}-${error.message}`"
                type="button"
                @click="activeStep = visibleStep(error.step)"
              >
                第 {{ stepNumber(visibleStep(error.step)) }} 步：{{ error.message }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <div>
          <el-button v-if="activeStep > 1" @click="goPrevious">上一步</el-button>
          <el-button v-if="activeStep < 4" type="primary" @click="goNext">下一步</el-button>
          <el-button
            v-if="isRelationBound || activeStep === 4"
            type="primary"
            :loading="saving"
            :disabled="catalogLoading || !!relationBindingError || (isRelationBound && !boundEntityRelation)"
            @click="save"
          >{{ isRelationBound ? '保存显示配置' : '保存关联内容' }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref, resolveComponent } from 'vue'
import { Connection, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import PageParameterMappingEditor from '@/components/page-parameters/PageParameterMappingEditor.vue'
import { validatePageParameterMappings } from '@/shared/page-parameters'
import { safeParseConfig } from '@/shared/config-runtime'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import EntitySelector from '@/components/EntitySelector.vue'
import { entityApi } from '@/api/entity'
import { getFormsByEntity, getEntityFields, getFormRuntimeRelease } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityRelationApi } from '@/api/entityRelation'
import { relationContentType, relationTarget, relationUsageKey, relationMatchLabel } from '@/shared/entity-relation'
import { applyEntityRelationBinding } from '@/shared/relation-content'
import { uiCompositionApi } from '@/api/uiComposition'
import { uiExtensionApi } from '@/api/uiConfig'
import {
  normalizeInterfaceExtension,
  normalizeInterfaceExtensions
} from '@/components/ui-config/interfaceExtensionModel'
import {
  getCustomFormComponentOptions,
  getCustomFormComponentVersionOptions,
  getCustomFormDescriptor,
  getCustomListComponentOptions,
  getCustomListComponentVersionOptions,
  getCustomListDescriptor
} from '@/extensions/core/registries/customComponentRegistry.js'
import {
  RELATED_CONTENT_ACTION_OPTIONS,
  RELATED_CONTENT_FAILURE_OPTIONS,
  RELATED_CONTENT_RELATION_OPTIONS,
  RELATED_CONTENT_TYPE_OPTIONS,
  buildRelatedContentPayload,
  createEmptyRelatedContent,
  describeRelatedContent,
  describeRelatedContentActions,
  describeRelatedContentRelation,
  describeRelatedContentSpecial,
  normalizeRelatedContent,
  relatedContentPositionOptions,
  updateRelatedContentAnchor,
  validateRelatedContent
} from '@/shared/related-content'

const StepHeading = defineComponent({
  props: {
    number: { type: String, required: true },
    title: { type: String, required: true },
    description: { type: String, default: '' }
  },
  setup(props) {
    return () => h('div', { class: 'step-heading' }, [
      h('span', { class: 'step-heading__number' }, props.number),
      h('div', [h('strong', props.title), h('small', props.description)])
    ])
  }
})

const ServiceMappingEditor = defineComponent({
  props: {
    modelValue: { type: Array, default: () => [] },
    title: { type: String, required: true },
    description: { type: String, default: '' },
    leftOptions: { type: Array, default: () => [] },
    rightOptions: { type: Array, default: () => [] },
    leftLabel: { type: String, default: '来源' },
    rightLabel: { type: String, default: '目标' },
    emptyText: { type: String, default: '暂无映射' }
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const update = (index, key, value) => emit('update:modelValue', props.modelValue.map(
      (row, rowIndex) => rowIndex === index ? { ...row, [key]: value } : row
    ))
    const remove = index => emit('update:modelValue', props.modelValue.filter((_, rowIndex) => rowIndex !== index))
    const add = () => emit('update:modelValue', [
      ...props.modelValue,
      { source: '', target: '' }
    ])
    const select = (row, index, key, options, placeholder) => h(resolveComponent('el-select'), {
      modelValue: row[key],
      filterable: true,
      placeholder,
      style: 'width: 100%',
      'onUpdate:modelValue': value => update(index, key, value)
    }, () => options.map(option => h(resolveComponent('el-option'), {
      key: option.value,
      label: option.label,
      value: option.value
    })))
    return () => h('div', { class: 'service-mapping' }, [
      h('div', { class: 'service-mapping__heading' }, [
        h('div', [h('strong', props.title), h('small', props.description)]),
        h(resolveComponent('el-button'), { icon: Plus, onClick: add }, () => '增加映射')
      ]),
      props.modelValue.length
        ? h('div', { class: 'service-mapping__rows' }, [
            h('div', { class: 'service-mapping__labels' }, [props.leftLabel, props.rightLabel, '操作'].map(label => h('span', label))),
            ...props.modelValue.map((row, index) => h('div', { class: 'service-mapping__row', key: index }, [
              select(row, index, 'source', props.leftOptions, `选择${props.leftLabel}`),
              select(row, index, 'target', props.rightOptions, `选择${props.rightLabel}`),
              h(resolveComponent('el-button'), {
                text: true,
                circle: true,
                icon: Delete,
                title: '删除映射',
                'aria-label': '删除映射',
                onClick: () => remove(index)
              })
            ]))
          ])
        : h('div', { class: 'service-mapping__empty' }, props.emptyText)
    ])
  }
})

const props = defineProps({
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], required: true },
  sourceEntity: { type: Object, default: () => ({}) },
  sourceFields: { type: Array, default: () => [] },
  sourceContentFields: { type: Array, default: () => [] },
  anchorOptions: { type: Array, default: () => [] },
  existingCount: { type: Number, default: 0 }
})

const emit = defineEmits(['saved'])
const router = useRouter()
const visible = ref(false)
const saving = ref(false)
const testing = ref(false)
const catalogLoading = ref(false)
const targetCatalogLoading = ref(false)
const activeStep = ref(1)
const advancedSections = ref([])
const targetEntity = ref(null)
const targetForms = ref([])
const targetLists = ref([])
const targetFields = ref([])
const targetContentFields = ref([])
const targetParameterSchema = ref({})
let targetSchemaSequence = 0
const sourceRelations = ref([])
const isRelationBound = ref(false)
const boundRelationCode = ref('')
const boundRelationKey = ref('')
const canSelectBinding = ref(false)
const relationBindingError = ref('')
const dataInterfaceOptions = ref([])
const actionInterfaceOptions = ref([])
const recommendedRelation = ref({ type: '' })
const recommendationText = ref('')
const testResult = ref(null)
const sourceTestRecordId = ref('')
const editor = reactive(createEmptyRelatedContent({
  ownerType: props.ownerType,
  sourceEntity: props.sourceEntity
}))

// 编辑已保存的实体关系展示时固定关系身份，不能通过通用编辑器另建一套关联条件。
const boundEntityRelation = computed(() => sourceRelations.value.find(item => boundRelationKey.value
  ? relationUsageKey(item) === boundRelationKey.value
  : item.relationCode === boundRelationCode.value
    && (item.direction || 'FORWARD') === (editor.config.relation.direction || 'FORWARD')
    && (item.direction === 'REVERSE'
      ? String(item.parentEntityId) === String(editor.config.target.entityId)
      : !item.parentEntityId || String(item.parentEntityId) === String(props.sourceEntity.id))))
const dialogTitle = computed(() => isRelationBound.value ? '关系展示设置' : '扩展页面')
const stepIds = computed(() => isRelationBound.value ? [1, 3, 4] : [1, 2, 3, 4])
const visibleStep = step => isRelationBound.value && step === 2 ? 1 : step
const stepNumber = step => stepIds.value.indexOf(step) + 1

/** 数据规则只能在实体设计中修改，新标签页保留当前展示草稿。 */
function openEntityRelationManagement() {
  const entityId = boundEntityRelation.value?.parentEntityId || props.sourceEntity.id
  if (!entityId) return
  const route = router.resolve({ path: `/entity/design/${entityId}`, query: { tab: 'relations' } })
  window.open(route.href, '_blank', 'noopener,noreferrer')
}

const selectedEntityRelation = computed(() => isRelationBound.value ? boundEntityRelation.value : null)
const contentTypeOptions = computed(() => RELATED_CONTENT_TYPE_OPTIONS.map(option => ({
  label: option.label,
  value: option.value,
  disabled: !!selectedEntityRelation.value && option.value !== relationContentType(selectedEntityRelation.value)
})))
const relationOptions = computed(() => RELATED_CONTENT_RELATION_OPTIONS.filter(option => {
  if (option.value === 'INTERFACE_SERVICE') return true
  if (option.value !== 'SAME_RECORD') return false
  return String(props.sourceEntity.id || '') === String(editor.config.target.entityId || '')
    || String(props.sourceEntity.entityCode || '') === String(editor.config.target.entityCode || '')
}))
const actionOptions = RELATED_CONTENT_ACTION_OPTIONS
const failureOptions = RELATED_CONTENT_FAILURE_OPTIONS
const positionOptions = computed(() => relatedContentPositionOptions(props.ownerType))
const availableAnchorOptions = computed(() => {
  if (editor.config.presentation.position !== 'TAB') return props.anchorOptions
  return props.anchorOptions.filter(option =>
    String(option.nodeType || '').toUpperCase() === 'TAB')
})
const targetContentOptions = computed(() => {
  const rows = editor.config.target.contentType === 'FORM'
    ? targetForms.value
    : targetLists.value
  return rows.filter(item => !isRelationBound.value || (
    (!item.entityId || String(item.entityId) === String(editor.config.target.entityId))
    && !(props.ownerType === 'FORM' && editor.config.target.contentType === 'FORM' && String(item.id) === String(props.ownerId))
  )).map(item => {
    const isForm = editor.config.target.contentType === 'FORM'
    return {
      ...item,
      name: isForm ? (item.formName || item.formKey) : (item.listName || item.listKey),
      key: isForm ? item.formKey : item.listKey,
      displayName: isForm
        ? `${item.formName || item.formKey}${item.formKey ? ` (${item.formKey})` : ''}`
        : `${item.listName || item.listKey}${item.listKey ? ` (${item.listKey})` : ''}`,
      published: isPublishedAsset(item)
    }
  })
})
const sourceFieldOptions = computed(() => fieldOptions(props.sourceFields))
const targetFieldOptions = computed(() => fieldOptions(targetFields.value))
const sourceReadableFieldOptions = computed(() => contentScopedFieldOptions(
  sourceFieldOptions.value,
  props.sourceContentFields,
  props.ownerType,
  false
))
const sourceEditableFieldOptions = computed(() => contentScopedFieldOptions(
  sourceFieldOptions.value,
  props.sourceContentFields,
  props.ownerType,
  true
))
const targetReadableFieldOptions = computed(() => contentScopedFieldOptions(
  targetFieldOptions.value,
  targetContentFields.value,
  editor.config.target.contentType,
  false
))
const targetEditableFieldOptions = computed(() => contentScopedFieldOptions(
  targetFieldOptions.value,
  targetContentFields.value,
  editor.config.target.contentType,
  true
))
const dataInterfaces = computed(() =>
  normalizeInterfaceExtensions(dataInterfaceOptions.value)
    .filter(item => item.enabled && item.interfaceKind === 'READ')
)
const actionInterfaces = computed(() =>
  normalizeInterfaceExtensions(actionInterfaceOptions.value)
    .filter(item => item.enabled)
)
const selectedDataInterface = computed(() => dataInterfaces.value.find(item =>
  String(item.extensionId) === String(
    editor.config.specialHandling.interfaceService.extensionId
  )
))
const interfaceInputOptions = computed(() =>
  schemaFieldOptions(selectedDataInterface.value?.inputSchema)
)
const interfaceOutputOptions = computed(() =>
  schemaFieldOptions(selectedDataInterface.value?.outputSchema)
)
const actionInputSourceOptions = computed(() => [
  ...sourceReadableFieldOptions.value.map(option => ({
    value: `source.${option.value}`,
    label: `当前记录 · ${option.label}`
  })),
  ...targetReadableFieldOptions.value.map(option => ({
    value: `target.${option.value}`,
    label: `选中目标 · ${option.label}`
  })),
  { value: 'targetIds', label: '全部已选择目标记录 ID' }
])
const interfaceResultOptions = computed(() => {
  const options = targetFieldOptions.value.map(option => ({
    value: `fixedFilters.${option.value}`,
    label: `按目标字段“${option.label}”筛选`
  }))
  if (editor.config.target.contentType === 'FORM') {
    options.unshift({
      value: 'targetRecordId',
      label: '作为目标记录 ID'
    })
  }
  options.push({
    value: 'matchNone',
    label: '作为“明确没有匹配数据”标记'
  })
  return options
})
const formComponentOptions = getCustomFormComponentOptions('RELATED_CONTENT')
const listComponentOptions = getCustomListComponentOptions('RELATED_CONTENT')
const customComponentOptions = computed(() => editor.config.target.contentType === 'FORM'
  ? formComponentOptions
  : listComponentOptions)
const customComponentVersionOptions = computed(() => {
  const name = editor.config.specialHandling.customComponent.name
  if (!name) return []
  return editor.config.target.contentType === 'FORM'
    ? getCustomFormComponentVersionOptions(name, 'RELATED_CONTENT')
    : getCustomListComponentVersionOptions(name, 'RELATED_CONTENT')
})
const selectedCustomComponent = computed(() => customComponentOptions.value.find(option =>
  option.value === editor.config.specialHandling.customComponent.name))
const selectedCustomComponentSchema = computed(() => {
  const name = editor.config.specialHandling.customComponent.name
  const version = editor.config.specialHandling.customComponent.version
  return resolveCustomComponentDescriptor(name, version)?.configSchema || []
})
const specialMode = computed(() => editor.config.specialHandling.mode || 'NONE')
const useInterfaceService = computed(() => ['INTERFACE_SERVICE', 'BOTH'].includes(specialMode.value))
const useDataInterfaceService = computed(() => editor.config.relation.type === 'INTERFACE_SERVICE'
  || Boolean(editor.config.specialHandling.interfaceService.extensionId))
const useCustomComponent = computed(() => ['CUSTOM_COMPONENT', 'BOTH'].includes(specialMode.value))
const supportsRelationshipMutation = computed(() => editor.config.relation.type === 'ENTITY_RELATION')
const naturalSummary = computed(() => describeRelatedContent(editor, props.sourceEntity.entityName || '当前页面'))
const relationSummary = computed(() => describeRelatedContentRelation(editor))
const actionSummary = computed(() => describeRelatedContentActions(editor))
const specialSummary = computed(() => describeRelatedContentSpecial(editor))
const localValidation = computed(() => {
  const result = validateRelatedContent(editor, props.ownerType)
  const parameterError = validatePageParameterMappings(editor.config.parameterMappings, targetParameterSchema.value, sourceReadableFieldOptions.value.map(item => item.raw))
  if (parameterError) result.errors.push({ step: 1, field: 'parameterMappings', message: parameterError })
  if (isRelationBound.value && !boundEntityRelation.value) result.errors.unshift({ step: 1, field: 'relation', message: '请选择有效的实体关系' })
  if (boundEntityRelation.value?.ownershipType === 'COMPOSITION' && editor.config.actions.some(action => action !== 'VIEW')) {
    result.errors.push({ step: 3, field: 'actions', message: '组成关系在这里仅供查看；需要编辑时请从表单左侧实体关系添加主从编辑组件' })
  }
  if (relationBindingError.value) result.errors.unshift({ step: 1, field: 'relation', message: relationBindingError.value })
  if (isRelationBound.value && useDataInterfaceService.value) {
    result.errors.push({ step: 4, field: 'interfaceService', message: '请移除接口取数，恢复使用实体关系' })
  }
  return { ...result, valid: !result.errors.length, firstStep: visibleStep(result.errors[0]?.step || 1) }
})
const canTest = computed(() => Boolean(
  props.ownerId
  && editor.config.target.entityId
  && editor.config.target.contentId
  && validateRelatedContent(editor, props.ownerType).errors.every(error => error.step !== 2)
))
const saveBoundaryTitle = computed(() => boundEntityRelation.value?.ownershipType === 'COMPOSITION' ? '组成关系只读展示' : editor.config.actions.includes('SAVE_WITH_FORM')
  ? '请改用已有组成型子表单或重复器'
  : '目标内容独立保存')
const saveBoundaryDescription = computed(() => boundEntityRelation.value?.ownershipType === 'COMPOSITION'
  ? '需要编辑子记录时，请从表单左侧实体关系添加主从编辑组件，随主表统一保存。'
  : editor.config.actions.includes('SAVE_WITH_FORM')
  ? '关联内容尚未接入宿主统一提交，该选项不能发布；取消勾选后继续配置。'
  : '在目标表单中的新增或编辑会立即独立保存，取消当前页面不会撤销目标内容。')

function fieldOptions(fields = []) {
  return (Array.isArray(fields) ? fields : [])
    .filter(field => field?.uiConfigurable !== false && field?.fieldCode)
    .map(field => ({
      value: field.fieldCode,
      label: field.fieldName || field.fieldLabel || field.fieldCode,
      raw: field
    }))
}

/**
 * 映射下拉只展示宿主或目标内容真实暴露的字段。服务端仍会在发布和执行时
 * 按精确快照复核；这里提前收窄，是为了避免出现“配置能保存、运行却不可用”。
 */
function contentScopedFieldOptions(entityOptions, contentFields, contentType, editable) {
  const rows = Array.isArray(contentFields) ? contentFields : []
  if (!rows.length) return entityOptions
  const normalizedType = String(contentType || '').toUpperCase()
  const allowed = new Set(rows.filter(field => {
    if (normalizedType === 'LIST') {
      if (field.showInList === false || Number(field.showInList) === 0) return false
      const sourceType = String(field.dataSourceType || 'ENTITY_FIELD').toUpperCase()
      return ['ENTITY_FIELD', 'REFERENCE', ''].includes(sourceType)
    }
    if (field.isHidden === true || Number(field.isHidden) === 1) return false
    if (editable && (field.isReadonly === true || Number(field.isReadonly) === 1)) {
      return false
    }
    return true
  }).map(field => String(
    field.fieldCode || field.bindingRef || field.field?.fieldCode || ''
  )).filter(Boolean))
  return entityOptions.filter(option => allowed.has(String(option.value)))
}

function schemaFieldOptions(schema) {
  const document = typeof schema === 'string' ? safeParse(schema) : (schema || {})
  return Object.entries(document?.properties || {}).map(([key, value]) => ({
    value: key,
    label: value?.title || value?.description || key
  }))
}

function safeParse(value) {
  try {
    return value ? JSON.parse(value) : {}
  } catch {
    return {}
  }
}

function isPublishedAsset(item = {}) {
  if (item.activeReleaseId || Number(item.publishedVersion || 0) > 0) return true
  // status=1 在历史表单/列表中仅表示启用，并不代表已有可钉定的发布快照。
  // 只有明确的发布信息才允许被关联内容引用，避免保存时才被服务端拒绝。
  return ['PUBLISHED', 'ACTIVE'].includes(String(item.status || '').toUpperCase())
}

function normalizeRows(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.records)) return response.records
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.list)) return response.list
  return []
}

function interfaceScopeLabel(item) {
  return {
    GLOBAL: '全部页面可用',
    ENTITY: '指定实体可用',
    FORM: '指定表单可用',
    LIST: '指定列表可用'
  }[item.scopeType] || '受控范围'
}

async function loadCommonCatalog() {
  catalogLoading.value = true
  try {
    const [relations, dataRows, actionRows] = await Promise.all([
      props.sourceEntity.id
        ? entityRelationApi.available(props.sourceEntity.id).catch(error => {
          if (isRelationBound.value) relationBindingError.value = error?.message || '实体关系加载失败，请关闭后重试'
          return []
        })
        : Promise.resolve([]),
      uiExtensionApi.availableInterfaces({
        ownerType: String(props.ownerType).toUpperCase(),
        ownerId: String(props.ownerId),
        bindingCode: 'RELATED_CONTENT_RESOLVE'
      }).catch(() => []),
      uiExtensionApi.availableInterfaces({
        ownerType: String(props.ownerType).toUpperCase(),
        ownerId: String(props.ownerId),
        bindingCode: 'RELATED_CONTENT_ACTION'
      }).catch(() => [])
    ])
    sourceRelations.value = normalizeRows(relations)
    // 两种 bindingCode 由服务端分别按上下文和读写能力筛选，不能在前端合并后串用。
    dataInterfaceOptions.value = normalizeRows(dataRows)
    actionInterfaceOptions.value = normalizeRows(actionRows)
  } finally {
    catalogLoading.value = false
  }
}

async function loadTargetCatalog(entityId, { recommend = false } = {}) {
  targetForms.value = []
  targetLists.value = []
  targetFields.value = []
  targetContentFields.value = []
  if (!entityId) return
  targetCatalogLoading.value = true
  try {
    const [formRows, listRows, fieldRows] = await Promise.all([
      getFormsByEntity(entityId).catch(() => []),
      entityListConfigApi.getByEntityId(entityId).catch(() => []),
      getEntityFields(entityId).catch(() => [])
    ])
    targetForms.value = normalizeRows(formRows)
    targetLists.value = normalizeRows(listRows)
    targetFields.value = normalizeRows(fieldRows)
    if (recommend) applyRecommendation()
  } finally {
    targetCatalogLoading.value = false
  }
}

async function loadTargetContentFields(contentId = editor.config.target.contentId) {
  const sequence = ++targetSchemaSequence
  targetContentFields.value = []
  targetParameterSchema.value = {}
  if (!contentId) return
  try {
    // 映射只消费目标已发布的参数声明，避免草稿参数在运行时不存在。
    const isForm = editor.config.target.contentType === 'FORM'
    const release = isForm
      ? await getFormRuntimeRelease(contentId)
      : (await entityListConfigApi.getReleases(contentId)).find(item => item.status === 'ACTIVE')
    if (sequence !== targetSchemaSequence) return
    const snapshot = safeParseConfig(release?.snapshotDocument)
    const content = isForm ? snapshot.form : (snapshot.config || snapshot.list)
    targetParameterSchema.value = safeParseConfig(content?.viewConfig).inputParameterSchema || {}
    targetContentFields.value = normalizeRows(isForm ? snapshot.legacyFields : (snapshot.list?.fields || snapshot.fields))
  } catch {
    // 目录加载失败时退回实体字段；发布和运行时仍按精确快照 fail-closed。
    targetContentFields.value = []
  }
}

function applyRecommendation() {
  if (isRelationBound.value) return
  const type = String(props.sourceEntity.id) === String(editor.config.target.entityId) ? 'SAME_RECORD' : 'INTERFACE_SERVICE'
  editor.config.relation = { type }
  recommendedRelation.value = { type }
  recommendationText.value = type === 'SAME_RECORD' ? '展示当前记录的另一张页面，不建立新的业务关系。' : '跨实体扩展页面由受控接口提供数据。普通业务关联请使用实体关系。'
  handleRelationTypeChange(type)
}

/** 新建展示只引用关系身份，不复制字段匹配条件；正反向使用也共享同一条定义。 */
async function selectBinding() {
  const relation = boundEntityRelation.value
  if (!relation) return
  boundRelationCode.value = relation.relationCode
  relationBindingError.value = ''
  editor.config.relation = { type: 'ENTITY_RELATION', relationCode: relation.relationCode, direction: relation.direction || 'FORWARD' }
  editor.config.target = { ...relationTarget(relation), contentType: relationContentType(relation), contentId: '', contentKey: '', contentName: '' }
  editor.config.actions = ['VIEW']
  if (!editor.config.name) editor.config.name = relation.relationName
  applyEntityRelationBinding(editor, relation)
  await loadTargetCatalog(editor.config.target.entityId)
}

async function handleTargetEntitySelected(entity) {
  targetEntity.value = entity || null
  Object.assign(editor.config.target, {
    entityId: entity?.id || '',
    entityCode: entity?.entityCode || '',
    entityName: entity?.entityName || entity?.entityCode || '',
    contentId: '',
    contentKey: '',
    contentName: ''
  })
  editor.config.relation = { type: 'INTERFACE_SERVICE' }
  await loadTargetCatalog(entity?.id, { recommend: true })
}

function handleContentTypeChange() {
  Object.assign(editor.config.target, {
    contentId: '',
    contentKey: '',
    contentName: ''
  })
  targetContentFields.value = []
  updateRelatedContentAnchor(editor, props.ownerType)
}

async function handleTargetContentChange(id) {
  const option = targetContentOptions.value.find(item => String(item.id) === String(id))
  editor.config.target.contentName = option?.name || ''
  editor.config.target.contentKey = option?.key || ''
  if (!editor.config.name && option?.name) {
    editor.config.name = `${option.name}关联内容`
  }
  await loadTargetContentFields(id)
}

function handlePositionChange() {
  if (editor.config.presentation.position === 'TAB') {
    const selected = props.anchorOptions.find(option =>
      String(option.value) === String(editor.anchorKey || ''))
    if (String(selected?.nodeType || '').toUpperCase() !== 'TAB') {
      editor.anchorKey = ''
    }
  }
  updateRelatedContentAnchor(editor, props.ownerType)
}

function handleAnchorChange(value) {
  editor.anchorKey = value || ''
  editor.anchorType = value ? 'FORM_NODE' : 'OWNER'
}

function handleRelationTypeChange(type) {
  editor.config.relation.type = type
  if (type === 'INTERFACE_SERVICE') {
    toggleSpecial('INTERFACE_SERVICE', true)
    advancedSections.value = ['special']
  }
}

function isActionDisabled(action) {
  // 实体关系的数量可能已变化，历史勾选项即使不再兼容也必须允许取消。
  if (isRelationBound.value && editor.config.actions.includes(action)) return false
  if (boundEntityRelation.value?.ownershipType === 'COMPOSITION') return action !== 'VIEW'
  if (action === 'SAVE_WITH_FORM') {
    // 新配置不能启用；历史草稿若已经勾选，仍允许用户取消勾选后保存。
    return !editor.config.actions.includes('SAVE_WITH_FORM')
  }
  if (action === 'SELECT') return editor.config.target.contentType !== 'LIST'
  if (action === 'LINK' && editor.config.target.contentType !== 'LIST') return true
  if (['CREATE', 'EDIT'].includes(action)) {
    return editor.config.target.contentType !== 'FORM'
  }
  if (['LINK', 'UNLINK'].includes(action)) return !supportsRelationshipMutation.value
  return false
}

function toggleAction(action, checked) {
  if (action === 'SAVE_WITH_FORM' && checked) return
  const values = new Set(editor.config.actions)
  if (checked) values.add(action)
  else values.delete(action)
  if (action !== 'VIEW' && checked) values.delete('VIEW')
  if (action === 'VIEW' && checked) {
    values.clear()
    values.add('VIEW')
  }
  // 聚合随宿主提交和独立写入属于不同保存边界，不能同时启用。
  if (action === 'SAVE_WITH_FORM' && checked) {
    for (const independentAction of ['CREATE', 'EDIT', 'LINK', 'UNLINK']) {
      values.delete(independentAction)
    }
  } else if (checked && ['CREATE', 'EDIT', 'LINK', 'UNLINK'].includes(action)) {
    values.delete('SAVE_WITH_FORM')
  }
  if (action === 'LINK'
    && !checked
    && editor.config.actionSettings.select.result === 'LINK') {
    editor.config.actionSettings.select.result = 'FILL_FIELDS'
  }
  editor.config.actions = Array.from(values)
}

function updateSpecialMode({ service = useInterfaceService.value, component = useCustomComponent.value }) {
  editor.config.specialHandling.mode = service && component
    ? 'BOTH'
    : service
      ? 'INTERFACE_SERVICE'
      : component
        ? 'CUSTOM_COMPONENT'
        : 'NONE'
}

function toggleSpecial(type, checked) {
  if (type === 'INTERFACE_SERVICE') {
    if (!checked) {
      Object.assign(editor.config.specialHandling.interfaceService, {
        extensionId: '',
        extensionName: '',
        inputMappings: [],
        outputMappings: []
      })
      editor.config.specialHandling.actionServices = []
    }
    updateSpecialMode({ service: checked, component: useCustomComponent.value })
  } else {
    updateSpecialMode({ service: useInterfaceService.value, component: checked })
  }
}

function toggleDataInterfaceService(checked) {
  if (checked) {
    updateSpecialMode({ service: true, component: useCustomComponent.value })
    return
  }
  Object.assign(editor.config.specialHandling.interfaceService, {
    extensionId: '',
    extensionName: '',
    inputMappings: [],
    outputMappings: []
  })
  if (isRelationBound.value) {
    // 恢复关系取数后，保留既有动作接口和展示组件；没有扩展时回到平台默认能力。
    updateSpecialMode({ service: editor.config.specialHandling.actionServices.length > 0 })
  }
}

function addActionService() {
  updateSpecialMode({ service: true, component: useCustomComponent.value })
  editor.config.specialHandling.actionServices.push({
    actionKey: '',
    extensionId: '',
    extensionName: '',
    inputMappings: [],
    outputMappings: [],
    failurePolicy: 'ERROR'
  })
}

function removeActionService(index) {
  editor.config.specialHandling.actionServices.splice(index, 1)
}

function actionInterface(binding) {
  return actionInterfaces.value.find(item =>
    String(item.extensionId) === String(binding.extensionId))
}

function actionInterfaceDescription(value) {
  const item = normalizeInterfaceExtension(value)
  if (item.interfaceKind === 'READ') {
    return '同步读取、校验或计算'
  }
  return '受控写入 · 服务端重新鉴权并校验发布能力'
}

function actionKeyOptions(binding) {
  const enabled = actionOptions.filter(option =>
    ['VIEW', 'SELECT', 'LINK', 'UNLINK'].includes(option.value)
      && editor.config.actions.includes(option.value))
  const item = actionInterface(binding)
  return [
    ...enabled,
    ...(item?.extensionKey ? [{
      value: item.extensionKey,
      label: `${item.displayName || item.extensionKey}（独立操作）`
    }] : [])
  ].filter((item, index, rows) => rows.findIndex(row => row.value === item.value) === index)
}

function actionInputOptions(binding) {
  return schemaFieldOptions(actionInterface(binding)?.inputSchema)
}

function actionOutputOptions(binding) {
  return schemaFieldOptions(actionInterface(binding)?.outputSchema)
}

function actionFailureOptions(binding) {
  return actionInterface(binding)?.interfaceKind === 'WRITE'
    ? failureOptions.filter(option => option.value === 'ERROR')
    : failureOptions
}

function handleActionInterfaceChange(binding) {
  const item = actionInterface(binding)
  Object.assign(binding, {
    extensionName: item?.displayName || item?.extensionKey || '',
    actionKey: item?.extensionKey || '',
    inputMappings: [],
    outputMappings: [],
    failurePolicy: 'ERROR'
  })
}

function handleDataInterfaceChange(id) {
  const item = dataInterfaces.value.find(value =>
    String(value.extensionId) === String(id))
  Object.assign(editor.config.specialHandling.interfaceService, {
    extensionName: item?.displayName || item?.extensionKey || '',
    inputMappings: [],
    outputMappings: []
  })
}

function handleCustomComponentChange(name) {
  const component = customComponentOptions.value.find(item => item.value === name)
  Object.assign(editor.config.specialHandling.customComponent, {
    displayName: component?.label || name || '',
    version: Number(component?.version || 1),
    artifactDigest: component?.artifactDigest || '',
    props: {}
  })
}

function handleCustomComponentVersionChange() {
  const component = resolveCustomComponentDescriptor(
    editor.config.specialHandling.customComponent.name,
    editor.config.specialHandling.customComponent.version
  )
  Object.assign(editor.config.specialHandling.customComponent, {
    displayName: component?.label
      || editor.config.specialHandling.customComponent.displayName,
    artifactDigest: component?.artifactDigest || '',
    props: {}
  })
}

function resolveCustomComponentDescriptor(name, version) {
  const descriptor = editor.config.target.contentType === 'FORM'
    ? getCustomFormDescriptor(name, version)
    : getCustomListDescriptor(name, version)
  return descriptor?.usageContexts?.includes('RELATED_CONTENT') ? descriptor : undefined
}

/** 历史草稿未保存制品摘要时，只从当前精确 name/version 注册项补齐。 */
function syncCustomComponentArtifactDigest() {
  const customComponent = editor.config.specialHandling.customComponent
  if (!customComponent?.name || customComponent.artifactDigest) return
  const descriptor = resolveCustomComponentDescriptor(
    customComponent.name,
    customComponent.version
  )
  customComponent.artifactDigest = descriptor?.artifactDigest || ''
}

function goNext() {
  const error = localValidation.value.errors.find(item => item.step === activeStep.value)
  if (error) {
    ElMessage.warning(error.message)
    return
  }
  activeStep.value = stepIds.value[Math.min(stepNumber(activeStep.value), stepIds.value.length - 1)]
}

function goPrevious() {
  activeStep.value = stepIds.value[Math.max(stepNumber(activeStep.value) - 2, 0)]
}

async function testWithRecord() {
  testing.value = true
  testResult.value = null
  try {
    const payload = buildRelatedContentPayload(editor, props.ownerType, props.ownerId)
    const result = await uiCompositionApi.test(
      props.ownerType,
      props.ownerId,
      payload.config,
      sourceTestRecordId.value
    )
    testResult.value = {
      type: result?.authorized === false ? 'warning' : 'success',
      title: result?.summary || '测试完成',
      description: result?.description
        || `已解析 ${Number(result?.matchedCount || 0)} 条目标数据；目标权限已校验。`,
      details: result || {}
    }
  } catch (error) {
    testResult.value = {
      type: 'error',
      title: '测试未通过',
      description: error?.message || '请检查关联字段、目标内容和当前用户权限。'
    }
  } finally {
    testing.value = false
  }
}

function testSourceLabel(source = {}) {
  if (!source?.id) return '当前权限范围内没有可用来源记录'
  const title = source.name || source.code || '来源记录'
  return `${title}（记录标识：${source.id}）`
}

function testFilterSummary(filters = {}) {
  const entries = Object.entries(filters || {})
  if (!entries.length) return '无需额外筛选，或已明确没有匹配数据'
  return entries.map(([key, value]) => {
    const base = key.replace(/_(op|start|end)$/i, '')
    const field = targetFieldOptions.value.find(option => option.value === base)
    const label = field?.label || base
    const suffix = key.endsWith('_op') ? '（匹配方式）' : ''
    const displayValue = Array.isArray(value) ? value.join('、') : String(value)
    return `${label}${suffix}：${displayValue}`
  }).join('；')
}

function testTargetSummary(ids = []) {
  if (!Array.isArray(ids) || !ids.length) return '无可展示记录'
  const shown = ids.slice(0, 10).join('、')
  return ids.length > 10 ? `${shown} 等 ${ids.length} 条` : shown
}

async function save() {
  if (catalogLoading.value) return
  if (isRelationBound.value) {
    if (relationBindingError.value) return
    try {
      applyEntityRelationBinding(editor, boundEntityRelation.value)
    } catch (error) {
      relationBindingError.value = error.message
      activeStep.value = 1
      return
    }
  }
  if (selectedEntityRelation.value
    && editor.config.target.contentType !== relationContentType(selectedEntityRelation.value)) {
    activeStep.value = 1
    ElMessage.warning('一对一关系请选择表单，一对多关系请选择列表')
    return
  }
  const validation = localValidation.value
  if (!validation.valid) {
    activeStep.value = validation.firstStep
    ElMessage.warning(validation.errors[0].message)
    return
  }
  saving.value = true
  try {
    updateRelatedContentAnchor(editor, props.ownerType)
    const payload = buildRelatedContentPayload(editor, props.ownerType, props.ownerId)
    const checked = await uiCompositionApi.validate(
      props.ownerType,
      props.ownerId,
      payload.config
    )
    if (checked?.normalizedConfig) payload.config = checked.normalizedConfig
    const saved = editor.id
      ? await uiCompositionApi.update(
          props.ownerType,
          props.ownerId,
          editor.id,
          payload
        )
      : await uiCompositionApi.create(props.ownerType, props.ownerId, payload)
    visible.value = false
    ElMessage.success('关联内容已保存，发布页面配置后生效')
    emit('saved', saved)
  } catch (error) {
    ElMessage.error(error?.message || '保存关联内容失败')
  } finally {
    saving.value = false
  }
}

async function open(value = null, { extension = false } = {}) {
  const next = value
    ? normalizeRelatedContent(value, { ownerType: props.ownerType, sourceEntity: props.sourceEntity })
    : createEmptyRelatedContent({ ownerType: props.ownerType, sourceEntity: props.sourceEntity })
  // 打开历史草稿时先按当前呈现方式修正挂载语义，避免旧版组合在界面上出现
  // “未选择入口”，并确保不修改任何业务字段。
  updateRelatedContentAnchor(next, props.ownerType)
  if (!value) next.orderKey = (props.existingCount + 1) * 1000000
  Object.keys(editor).forEach(key => delete editor[key])
  Object.assign(editor, next)
  isRelationBound.value = value ? !['SAME_RECORD', 'INTERFACE_SERVICE'].includes(next.config.relation.type) : !extension
  canSelectBinding.value = isRelationBound.value && (!value || next.config.relation.type !== 'ENTITY_RELATION')
  if (canSelectBinding.value) editor.config.relation = { type: 'ENTITY_RELATION', relationCode: '' }
  else if (!value) editor.config.relation = { type: 'INTERFACE_SERVICE' }
  boundRelationKey.value = ''
  boundRelationCode.value = isRelationBound.value ? next.config.relation.relationCode : ''
  sourceRelations.value = []
  relationBindingError.value = ''
  syncCustomComponentArtifactDigest()
  activeStep.value = 1
  advancedSections.value = []
  recommendationText.value = ''
  recommendedRelation.value = { type: '' }
  testResult.value = null
  sourceTestRecordId.value = ''
  visible.value = true
  await loadCommonCatalog()
  if (isRelationBound.value && !canSelectBinding.value && !relationBindingError.value) {
    try {
      applyEntityRelationBinding(editor, boundEntityRelation.value)
    } catch (error) {
      relationBindingError.value = error.message
    }
  }
  if (editor.config.target.entityId) {
    const [resolved] = await entityApi.resolveOptions({
      ids: [String(editor.config.target.entityId)]
    }).catch(() => [])
    targetEntity.value = resolved || editor.config.target
    await loadTargetCatalog(editor.config.target.entityId)
    await loadTargetContentFields(editor.config.target.contentId)
  }
  if (editor.config.specialHandling.mode !== 'NONE') {
    advancedSections.value = ['special']
  }
}

defineExpose({ open })
</script>

<style scoped>
.entity-relation-binding {
  margin: 20px 0;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.relation-binding-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.entity-relation-binding p {
  margin: 12px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.inherited-target { overflow-wrap: anywhere; }

.dialog-heading,
.dialog-footer,
.configuration-summary,
.step-heading,
.relation-preview,
.permission-summary,
.special-config-card__heading,
.review-card__heading,
.service-mapping__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.dialog-heading > div,
.configuration-summary > div,
.step-heading > div,
.special-config-card__heading > div,
.service-mapping__heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.dialog-heading span,
.configuration-summary span,
.configuration-summary small,
.step-heading small,
.collapse-heading small,
.special-config-card__heading span,
.service-mapping__heading small,
.permission-summary span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.related-content-editor {
  min-height: 550px;
}

.configuration-summary {
  justify-content: flex-start;
  margin-bottom: 22px;
  padding: 14px 18px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
}

.configuration-summary__icon {
  display: flex;
  flex: 0 0 38px;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 20px;
}

.configuration-summary strong {
  color: var(--el-text-color-primary);
}

.step-content {
  min-height: 380px;
  margin-top: 20px;
  padding: 20px 22px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.step-heading {
  justify-content: flex-start;
  margin-bottom: 20px;
}

.step-heading__number {
  display: flex;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 700;
}

.step-heading strong {
  font-size: 16px;
}

.two-column-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
}

.stacked-control {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 10px;
}

.business-option {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.35;
}

.business-option small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommendation-alert,
.field-match-alert,
.save-boundary-alert {
  margin-bottom: 18px;
}

.relation-methods {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.selection-card {
  display: flex;
  min-height: 72px;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
  text-align: left;
}

.selection-card:hover,
.selection-card.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.selection-card > span {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.selection-card small,
.action-card small,
.special-choice small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.field-match-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 36px minmax(0, 1fr);
  align-items: center;
}

.match-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}

.relation-preview,
.permission-summary {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 7px;
  background: var(--el-fill-color-light);
}

.relation-preview > div,
.permission-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.relation-preview span {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.relation-preview .real-record-test {
  width: min(460px, 100%);
  flex-direction: row;
  align-items: center;
  gap: 10px;
}

.real-record-test .entity-selector {
  min-width: 0;
  flex: 1;
}

.real-record-test-result {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.action-grid,
.special-choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.action-card,
.special-choice {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, auto) 20px;
  gap: 4px 8px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  cursor: pointer;
}

.action-card small,
.special-choice small {
  grid-column: 1 / -1;
}

.action-card.is-selected,
.special-choice.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.action-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.action-settings-card {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  background: var(--el-fill-color-extra-light);
}

.action-settings-card__heading {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 14px;
}

.action-settings-card__heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.special-collapse {
  margin-top: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  padding: 0 16px;
}

.collapse-heading {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.special-config-card {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  background: var(--el-fill-color-extra-light);
}

.special-config-card__heading {
  align-items: flex-start;
  margin-bottom: 16px;
}

.service-mapping {
  margin-top: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.service-mapping__heading {
  padding: 12px 14px;
  background: var(--el-fill-color-light);
}

.service-mapping__labels,
.service-mapping__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 44px;
  gap: 10px;
  align-items: center;
  padding: 8px 12px;
}

.service-mapping__labels {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 600;
}

.service-mapping__row {
  border-top: 1px solid var(--el-border-color-lighter);
}

.service-mapping__empty {
  padding: 18px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.mapping-section,
.failure-policy-form {
  margin-top: 14px;
}

.review-card {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
}

.review-card__heading {
  margin-bottom: 12px;
}

.validation-errors {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.validation-errors button {
  border: 0;
  background: transparent;
  color: var(--el-color-danger);
  cursor: pointer;
  font-size: 12px;
  text-decoration: underline;
}

.dialog-footer > div {
  display: flex;
  gap: 8px;
}

@media (max-width: 820px) {
  .two-column-form,
  .relation-methods,
  .action-grid,
  .special-choice-grid {
    grid-template-columns: 1fr;
  }

  .field-match-grid {
    grid-template-columns: 1fr;
  }

  .match-arrow {
    transform: rotate(90deg);
  }

  .dialog-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
