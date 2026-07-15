<template>
  <el-dialog v-model="visible" title="按钮适用条件" width="900px" :close-on-click-modal="false">
    <el-form label-width="100px">
      <el-form-item label="常用预设">
        <el-select v-model="preset" clearable placeholder="选择预设并应用" style="width: 340px" @change="applyPreset">
          <el-option v-for="item in presets" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="不满足时">
        <el-radio-group v-model="rule.unavailableBehavior">
          <el-radio-button label="HIDE">隐藏按钮</el-radio-button>
          <el-radio-button label="DISABLE">禁用并说明</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="提示原因">
        <el-input v-model="rule.message" placeholder="例如：仅本人未流转草稿可以删除" />
      </el-form-item>
      <el-form-item label="条件规则">
        <div style="width: 100%">
          <el-alert
            v-if="!rule.root"
            type="info"
            :closable="false"
            title="未配置条件时，只检查功能权限和数据范围。"
            style="margin-bottom: 10px"
          />
          <ActionRuleGroupEditor
            v-if="rule.root"
            :node="rule.root"
            :fields="fieldOptions"
            :statuses="statuses"
          />
          <el-button v-else type="primary" text @click="createRoot">添加条件</el-button>
          <el-button v-if="rule.root" type="danger" text @click="rule.root = null">清空条件</el-button>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import ActionRuleGroupEditor from './ActionRuleGroupEditor.vue'

const props = defineProps({
  entityFields: { type: Array, default: () => [] },
  statuses: { type: Array, default: () => [] }
})

const emit = defineEmits(['save'])
const visible = ref(false)
const button = ref(null)
const preset = ref('')
const rule = ref(emptyRule('HIDE'))

const systemFields = [
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
]

const fieldOptions = computed(() => [
  ...systemFields,
  ...props.entityFields
    .filter(field => field.fieldCode && !systemFields.some(item => item.value === field.fieldCode))
    .map(field => ({ label: `${field.fieldName} (${field.fieldCode})`, value: field.fieldCode }))
])

const presets = [
  { label: '始终可操作', value: 'ALWAYS' },
  { label: '仅本人数据', value: 'OWN_DATA' },
  { label: '仅本人未流转草稿', value: 'OWN_DRAFT' },
  { label: '仅本人草稿或已撤回', value: 'OWN_DRAFT_OR_WITHDRAWN' },
  { label: '仅当前任务办理人', value: 'CURRENT_ASSIGNEE' },
  { label: '仅流程进行中', value: 'RUNNING' },
  { label: '仅本部门数据', value: 'SAME_DEPT' },
  { label: '指定状态', value: 'STATUS' }
]

function open(targetButton, defaultBehavior = 'HIDE') {
  button.value = targetButton
  preset.value = ''
  rule.value = targetButton.availabilityRule
    ? cloneValue(targetButton.availabilityRule)
    : emptyRule(defaultBehavior)
  visible.value = true
}

function save() {
  emit('save', {
    button: button.value,
    rule: cloneValue(rule.value)
  })
  visible.value = false
}

function createRoot() {
  rule.value.root = group('AND', [])
}

function applyPreset(value) {
  const behavior = rule.value.unavailableBehavior
  const definitions = {
    ALWAYS: { root: null, message: '' },
    OWN_DATA: {
      root: group('OR', [relation('CURRENT_USER_IS_CREATOR'), relation('CURRENT_USER_IS_SUBMITTER')]),
      message: '仅本人数据可以操作'
    },
    OWN_DRAFT: {
      root: group('AND', [
        group('OR', [relation('CURRENT_USER_IS_CREATOR'), relation('CURRENT_USER_IS_SUBMITTER')]),
        condition('PROCESS_STATE', 'EQ', 'NOT_STARTED'),
        condition('STATUS_CATEGORY', 'EQ', 'NEW')
      ]),
      message: '仅本人未流转草稿可以操作'
    },
    OWN_DRAFT_OR_WITHDRAWN: {
      root: group('AND', [
        group('OR', [relation('CURRENT_USER_IS_CREATOR'), relation('CURRENT_USER_IS_SUBMITTER')]),
        group('OR', [
          group('AND', [
            condition('PROCESS_STATE', 'EQ', 'NOT_STARTED'),
            condition('STATUS_CATEGORY', 'EQ', 'NEW')
          ]),
          condition('STATUS_CATEGORY', 'EQ', 'WITHDRAWN')
        ])
      ]),
      message: '仅本人未流转草稿或已撤回数据可以操作'
    },
    CURRENT_ASSIGNEE: {
      root: relation('CURRENT_USER_IS_ASSIGNEE'),
      message: '仅当前任务办理人可以操作'
    },
    RUNNING: {
      root: condition('PROCESS_STATE', 'EQ', 'RUNNING'),
      message: '仅流程进行中的数据可以操作'
    },
    SAME_DEPT: {
      root: relation('CURRENT_USER_SAME_DEPT'),
      message: '仅本部门数据可以操作'
    },
    STATUS: {
      root: condition('STATUS_CODE', 'IN', []),
      message: '当前数据状态不允许操作'
    }
  }
  const definition = definitions[value]
  if (definition) {
    rule.value = {
      version: 1,
      unavailableBehavior: behavior,
      ...cloneValue(definition)
    }
  }
}

function emptyRule(behavior) {
  return { version: 1, unavailableBehavior: behavior, message: '', root: null }
}

function group(logic, children) {
  return { type: 'GROUP', logic, children }
}

function relation(value) {
  return { type: 'RELATION', relation: value }
}

function condition(type, operator, value) {
  return { type, operator, value }
}

function cloneValue(value) {
  return JSON.parse(JSON.stringify(value))
}

defineExpose({ open })
</script>
