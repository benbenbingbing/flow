<template>
  <el-dialog
    :model-value="scenarioVisible"
    :title="scenarioIndex < 0 ? '新增版本场景' : '编辑版本场景'"
    width="720px"
    @update:model-value="emit('update:scenarioVisible', $event)"
  >
    <el-form label-width="110px">
      <el-form-item label="场景名称" required>
        <el-input v-model="scenario.scenarioName" />
      </el-form-item>
      <el-form-item label="场景编码" required>
        <el-input v-model="scenario.scenarioCode" :disabled="scenarioIndex >= 0" />
      </el-form-item>
      <el-form-item label="变更入口">
        <template #label>
          <ConfigHelpLabel
            label="变更入口"
            help-key="entityVersion.sourceTypes"
          />
        </template>
        <el-select v-model="scenario.sourceTypes" multiple filterable>
          <el-option
            v-for="item in sourceTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作类型">
        <template #label>
          <ConfigHelpLabel
            label="操作类型"
            help-key="entityVersion.operationTypes"
          />
        </template>
        <el-select v-model="scenario.operationTypes" multiple>
          <el-option
            v-for="item in operationTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务意图">
        <el-select
          v-model="scenario.businessIntents"
          multiple
          filterable
          allow-create
          default-first-option
        />
      </el-form-item>
      <el-form-item label="条件">
        <el-input v-model="scenario.conditionText" type="textarea" :rows="5" />
      </el-form-item>
      <el-form-item label="标题模板">
        <el-input v-model="scenario.versionTitleTemplate" />
      </el-form-item>
      <el-form-item label="优先级">
        <el-input-number v-model="scenario.priority" :min="0" :max="9999" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="scenario.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:scenarioVisible', false)">取消</el-button>
      <el-button type="primary" @click="emit('saveScenario')">保存场景</el-button>
    </template>
  </el-dialog>

  <el-dialog
    :model-value="stepVisible"
    :title="stepIndex < 0 ? '新增前置操作' : '编辑前置操作'"
    width="720px"
    @update:model-value="emit('update:stepVisible', $event)"
  >
    <el-form label-width="110px">
      <el-form-item label="操作名称" required>
        <el-input v-model="step.stepName" />
      </el-form-item>
      <el-form-item label="执行阶段" required>
        <template #label>
          <ConfigHelpLabel
            label="执行阶段"
            help-key="entityVersion.phase"
          />
        </template>
        <el-select v-model="step.phase" :disabled="step.stepType === 'MANAGED_INTERFACE'">
          <el-option
            v-for="item in phaseOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作类型" required>
        <template #label>
          <ConfigHelpLabel
            label="操作类型"
            help-key="entityVersion.stepType"
          />
        </template>
        <el-select v-model="step.stepType" @change="handleStepTypeChange">
          <el-option
            v-for="item in stepTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="限定场景">
        <el-select v-model="step.scenarioCode" clearable>
          <el-option
            v-for="item in scenarios"
            :key="item.scenarioCode"
            :label="item.scenarioName"
            :value="item.scenarioCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="实现">
        <div class="selector-field">
          <el-select
            v-if="step.stepType === 'BUILT_IN_RULE'"
            v-model="step.providerCode"
          >
            <el-option
              v-for="item in catalog.builtInRules || []"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
          <el-input
            v-else
            v-model="step.providerCode"
            readonly
            placeholder="选择实现"
          />
          <el-button
            v-if="['MANAGED_INTERFACE', 'JAVA_PROVIDER'].includes(step.stepType)"
            @click="emit('openPicker', step.stepType, 'step')"
          >
            选择
          </el-button>
        </div>
      </el-form-item>
      <el-form-item
        v-if="step.stepType === 'MANAGED_INTERFACE'"
        label="接口操作"
        required
      >
        <el-input
          v-model="step.operationCode"
          readonly
          placeholder="请通过上方选择接口操作"
        />
      </el-form-item>
      <el-form-item label="参数">
        <el-input v-model="step.configText" type="textarea" :rows="7" />
      </el-form-item>
      <el-form-item label="顺序">
        <el-input-number v-model="step.sortOrder" :min="0" :max="9999" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="step.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:stepVisible', false)">取消</el-button>
      <el-button type="primary" @click="emit('saveStep')">保存操作</el-button>
    </template>
  </el-dialog>

  <el-dialog
    :model-value="targetVisible"
    :title="targetIndex < 0 ? '新增变更目标' : '编辑变更目标'"
    width="760px"
    @update:model-value="emit('update:targetVisible', $event)"
  >
    <el-form label-width="120px">
      <el-form-item label="绑定名称" required>
        <el-input v-model="target.bindingName" />
      </el-form-item>
      <el-form-item label="绑定编码" required>
        <el-input v-model="target.bindingCode" :disabled="targetIndex >= 0" />
      </el-form-item>
      <el-form-item label="来源实体" required>
        <EntityDefinitionPicker
          v-model="target.sourceEntityCode"
          value-key="entityCode"
          title="选择来源实体"
          :query="{ storageMode: 'DYNAMIC' }"
          @selected="emit('sourceEntityResolved', $event)"
          @resolved="emit('sourceEntityResolved', $event)"
        />
      </el-form-item>
      <el-form-item label="目标实体">
        <el-input :model-value="`${targetEntityName} (${targetEntityCode})`" disabled />
      </el-form-item>
      <el-form-item label="解析方式" required>
        <template #label>
          <ConfigHelpLabel
            label="解析方式"
            help-key="entityVersion.resolverType"
          />
        </template>
        <el-segmented v-model="target.resolverType" :options="resolverTypeOptions" />
      </el-form-item>
      <el-form-item label="解析字段">
        <div class="selector-field">
          <el-input
            v-model="target.resolverCode"
            :readonly="target.resolverType === 'JAVA_PROVIDER'"
          />
          <el-button
            v-if="target.resolverType === 'JAVA_PROVIDER'"
            @click="emit('openPicker', 'TARGET_RESOLVER', 'target')"
          >
            选择
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="字段映射">
        <el-input v-model="target.mappingText" type="textarea" :rows="7" />
      </el-form-item>
      <el-form-item label="解析参数">
        <el-input v-model="target.resolverConfigText" type="textarea" :rows="5" />
      </el-form-item>
      <el-form-item label="生效后回写">
        <el-input v-model="target.effectivePatchText" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="失败后回写">
        <el-input v-model="target.failedPatchText" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="应用策略">
        <template #label>
          <ConfigHelpLabel
            label="应用策略"
            help-key="entityVersion.applyStrategy"
          />
        </template>
        <el-segmented
          v-model="target.applyStrategy"
          :options="[
            { label: '合并', value: 'MERGE' },
            { label: '替换', value: 'REPLACE' }
          ]"
        />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="target.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:targetVisible', false)">取消</el-button>
      <el-button type="primary" @click="emit('saveTarget')">保存目标</el-button>
    </template>
  </el-dialog>

  <el-dialog
    :model-value="pickerVisible"
    title="选择实现"
    width="720px"
    @update:model-value="emit('update:pickerVisible', $event)"
  >
    <div class="picker-toolbar">
      <el-input
        :model-value="pickerKeyword"
        clearable
        placeholder="搜索名称或编码"
        @update:model-value="emit('update:pickerKeyword', $event)"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>
    <el-table v-loading="pickerLoading" :data="pickerItems" border height="360">
      <el-table-column label="名称" min-width="230">
        <template #default="{ row }">
          <div class="primary-text">{{ row.name }}</div>
          <div class="secondary-text">{{ row.code }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="130" />
      <el-table-column label="操作" width="90" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('selectPickerItem', row)">选择</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="picker-pagination"
      :current-page="pickerPage"
      :page-size="8"
      :total="pickerTotal"
      layout="total, prev, pager, next"
      @update:current-page="emit('update:pickerPage', $event)"
    />
  </el-dialog>

  <el-dialog
    :model-value="simulationVisible"
    title="模拟匹配"
    width="760px"
    @update:model-value="emit('update:simulationVisible', $event)"
  >
    <el-form label-width="110px">
      <el-form-item label="变更入口">
        <template #label>
          <ConfigHelpLabel
            label="变更入口"
            help-key="entityVersion.sourceTypes"
          />
        </template>
        <el-select v-model="simulationModel.sourceType">
          <el-option
            v-for="item in sourceTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作类型">
        <template #label>
          <ConfigHelpLabel
            label="操作类型"
            help-key="entityVersion.operationTypes"
          />
        </template>
        <el-select v-model="simulationModel.operationType">
          <el-option
            v-for="item in operationTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务意图">
        <el-input v-model="simulationModel.businessIntentCode" />
      </el-form-item>
      <el-form-item label="写入前数据">
        <el-input v-model="simulationModel.beforeText" type="textarea" :rows="4" />
      </el-form-item>
      <el-form-item label="写入后数据">
        <el-input v-model="simulationModel.afterText" type="textarea" :rows="4" />
      </el-form-item>
      <el-form-item label="扩展参数">
        <el-input v-model="simulationModel.extraText" type="textarea" :rows="3" />
      </el-form-item>
      <el-alert
        v-if="simulationResult"
        :title="simulationResult.matched ? `命中：${simulationResult.scenario?.name}` : '未命中版本场景'"
        :type="simulationResult.matched ? 'success' : 'info'"
        show-icon
        :closable="false"
      />
    </el-form>
    <template #footer>
      <el-button @click="emit('update:simulationVisible', false)">关闭</el-button>
      <el-button type="primary" :loading="simulationLoading" @click="emit('runSimulation')">
        执行模拟
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'

const props = defineProps({
  scenarioVisible: Boolean,
  scenarioIndex: { type: Number, default: -1 },
  scenarioEditor: { type: Object, required: true },
  sourceTypeOptions: { type: Array, default: () => [] },
  operationTypeOptions: { type: Array, default: () => [] },
  stepVisible: Boolean,
  stepIndex: { type: Number, default: -1 },
  stepEditor: { type: Object, required: true },
  phaseOptions: { type: Array, default: () => [] },
  stepTypeOptions: { type: Array, default: () => [] },
  scenarios: { type: Array, default: () => [] },
  catalog: { type: Object, default: () => ({}) },
  targetVisible: Boolean,
  targetIndex: { type: Number, default: -1 },
  targetEditor: { type: Object, required: true },
  targetEntityName: { type: String, default: '' },
  targetEntityCode: { type: String, default: '' },
  resolverTypeOptions: { type: Array, default: () => [] },
  pickerVisible: Boolean,
  pickerKeyword: { type: String, default: '' },
  pickerPage: { type: Number, default: 1 },
  pickerItems: { type: Array, default: () => [] },
  pickerTotal: { type: Number, default: 0 },
  pickerLoading: Boolean,
  simulationVisible: Boolean,
  simulation: { type: Object, required: true },
  simulationResult: { type: Object, default: null },
  simulationLoading: Boolean
})

const emit = defineEmits([
  'update:scenarioVisible',
  'update:stepVisible',
  'update:targetVisible',
  'update:pickerVisible',
  'update:pickerKeyword',
  'update:pickerPage',
  'update:simulationVisible',
  'saveScenario',
  'saveStep',
  'saveTarget',
  'openPicker',
  'selectPickerItem',
  'sourceEntityResolved',
  'runSimulation'
])

const scenario = computed(() => props.scenarioEditor)
const step = computed(() => props.stepEditor)
const target = computed(() => props.targetEditor)
const simulationModel = computed(() => props.simulation)

function handleStepTypeChange(value) {
  step.value.providerCode = ''
  step.value.operationCode = ''
  if (value === 'MANAGED_INTERFACE') {
    step.value.phase = 'PREPARE'
  }
}
</script>

<style scoped>
.selector-field,
.picker-toolbar {
  display: flex;
  align-items: center;
}

.selector-field {
  width: 100%;
  gap: 8px;
}

.selector-field .el-input,
.selector-field .el-select {
  flex: 1;
}

.picker-toolbar {
  margin-bottom: 12px;
}

.picker-toolbar .el-input {
  width: 320px;
}

.picker-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}

.primary-text {
  color: #303133;
  font-weight: 600;
}

.secondary-text {
  color: #909399;
  font-size: 13px;
}
</style>
