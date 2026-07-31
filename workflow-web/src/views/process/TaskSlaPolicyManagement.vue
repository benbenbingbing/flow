<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>SLA 策略</h2>
        <p>定义首次响应、办结时限以及受控提醒和升级动作。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建策略</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="policyName" label="策略名称" min-width="180" />
      <el-table-column prop="policyCode" label="编码" min-width="150" />
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column label="响应目标" width="130">
        <template #default="{ row }">{{ targetText(row.responseTargetMinutes) }}</template>
      </el-table-column>
      <el-table-column label="办结目标" width="130">
        <template #default="{ row }">{{ targetText(row.completionTargetMinutes) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">查看配置</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="success"
            @click="publish(row)"
          >
            发布
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '配置 SLA 策略' : '新建 SLA 策略'"
      width="min(1100px, 95vw)"
      destroy-on-close
    >
      <el-form :model="form" label-width="132px">
        <div class="form-grid">
          <el-form-item label="策略编码" required>
            <el-input v-model="form.policyCode" :disabled="readonlyCode" />
          </el-form-item>
          <el-form-item label="策略名称" required><el-input v-model="form.policyName" /></el-form-item>
          <el-form-item label="响应分钟">
            <template #label>
              <ConfigHelpLabel
                label="响应分钟"
                :content="slaFieldHelp.responseTargetMinutes"
              />
            </template>
            <el-input-number v-model="form.responseTargetMinutes" :min="1" :max="525600" />
          </el-form-item>
          <el-form-item label="响应计时">
            <template #label>
              <ConfigHelpLabel
                label="响应计时"
                :content="slaFieldHelp.responseTimeBasis"
              />
            </template>
            <el-segmented
              v-model="form.responseTimeBasis"
              :options="timeBasisOptions"
            />
          </el-form-item>
          <el-form-item label="办结分钟" required>
            <template #label>
              <ConfigHelpLabel
                label="办结分钟"
                :content="slaFieldHelp.completionTargetMinutes"
              />
            </template>
            <el-input-number v-model="form.completionTargetMinutes" :min="1" :max="525600" />
          </el-form-item>
          <el-form-item label="办结计时">
            <template #label>
              <ConfigHelpLabel
                label="办结计时"
                :content="slaFieldHelp.completionTimeBasis"
              />
            </template>
            <el-segmented
              v-model="form.completionTimeBasis"
              :options="timeBasisOptions"
            />
          </el-form-item>
          <el-form-item label="允许人工暂停">
            <template #label>
              <ConfigHelpLabel
                label="允许人工暂停"
                :content="slaFieldHelp.allowManualPause"
              />
            </template>
            <el-switch v-model="form.allowManualPause" />
          </el-form-item>
          <el-form-item label="流程挂起暂停">
            <template #label>
              <ConfigHelpLabel
                label="流程挂起暂停"
                :content="slaFieldHelp.pauseOnProcessSuspend"
              />
            </template>
            <el-switch v-model="form.pauseOnProcessSuspend" />
          </el-form-item>
          <el-form-item label="最长暂停分钟">
            <template #label>
              <ConfigHelpLabel
                label="最长暂停分钟"
                :content="slaFieldHelp.maxPauseMinutes"
              />
            </template>
            <el-input-number v-model="form.maxPauseMinutes" :min="1" clearable />
          </el-form-item>
          <el-form-item label="说明">
            <template #label>
              <ConfigHelpLabel
                label="说明"
                :content="slaFieldHelp.description"
              />
            </template>
            <el-input v-model="form.description" />
          </el-form-item>
        </div>

        <section class="editor-section">
          <div class="section-header">
            <div>
              <h3>提醒与升级</h3>
              <span>仅允许提醒、上级/关注人知会、转办和加签，不提供自动通过或驳回。</span>
            </div>
            <el-button :icon="Plus" @click="addStep">添加步骤</el-button>
          </div>
          <el-table :data="form.escalationSteps" border>
            <el-table-column label="步骤名称" min-width="150">
              <template #default="{ row }"><el-input v-model="row.stepName" /></template>
            </el-table-column>
            <el-table-column label="指标" width="120">
              <template #default="{ row }">
                <el-select v-model="row.metricType">
                  <el-option label="首次响应" value="RESPONSE" />
                  <el-option label="办结" value="COMPLETION" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="触发" width="130">
              <template #default="{ row }">
                <el-select v-model="row.triggerType">
                  <el-option label="到期前" value="BEFORE_DUE" />
                  <el-option label="到期时" value="AT_DUE" />
                  <el-option label="到期后" value="AFTER_DUE" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="偏移分钟" width="120">
              <template #default="{ row }"><el-input-number v-model="row.offsetMinutes" :min="0" /></template>
            </el-table-column>
            <el-table-column label="动作" width="150">
              <template #default="{ row }">
                <el-select v-model="row.actionType">
                  <el-option label="提醒当前人" value="NOTIFY" />
                  <el-option label="提醒上级" value="NOTIFY_MANAGER" />
                  <el-option label="增加知会" value="ADD_CC" />
                  <el-option label="自动转办" value="TRANSFER" />
                  <el-option label="自动加签" value="ADD_SIGN" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="执行次数" width="110">
              <template #default="{ row }"><el-input-number v-model="row.maxExecutions" :min="1" :max="20" /></template>
            </el-table-column>
            <el-table-column label="间隔分钟" width="120">
              <template #default="{ row }"><el-input-number v-model="row.repeatIntervalMinutes" :min="1" /></template>
            </el-table-column>
            <el-table-column label="动作配置" min-width="210">
              <template #default="{ row }">
                <el-input
                  v-model="row.targetConfigJson"
                  type="textarea"
                  :rows="2"
                  placeholder='如 {"targetType":"MANAGER"}'
                />
              </template>
            </el-table-column>
            <el-table-column label="接收人配置" min-width="240">
              <template #default="{ row }">
                <el-input
                  v-model="row.recipientConfigJson"
                  type="textarea"
                  :rows="2"
                  placeholder='如 {"includeAssignee":true,"channels":["IN_APP"]}'
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="64" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button
                  text
                  circle
                  :icon="Delete"
                  aria-label="删除升级步骤"
                  title="删除升级步骤"
                  @click="form.escalationSteps.splice($index, 1)"
                />
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import { taskSlaPolicyApi } from '@/api/sla'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const editingId = ref('')
const readonlyCode = ref(false)
const timeBasisOptions = [
  { label: '工作时间', value: 'WORKING_TIME' },
  { label: '自然时间', value: 'NATURAL_TIME' }
]
const slaFieldHelp = Object.freeze({
  responseTargetMinutes:
    '用户任务创建后到首次确认响应的目标时长。留空表示不考核首次响应；具体按“响应计时”选择的时间口径累计。',
  responseTimeBasis:
    '工作时间仅累计流程所用工作日历中的有效工作时段；自然时间连续计时，周末、节假日和非工作时段也计入。',
  completionTargetMinutes:
    '用户任务从创建到审批完成的目标时长。超过时限仍未完成时，办结指标标记为超时；具体按“办结计时”选择的时间口径累计。',
  completionTimeBasis:
    '工作时间仅累计流程所用工作日历中的有效工作时段；自然时间连续计时，周末、节假日和非工作时段也计入。',
  allowManualPause:
    '开启后，当前任务办理人可手动暂停和恢复 SLA 计时。暂停时保存剩余响应及办结时长，恢复后从剩余时长继续计算。',
  pauseOnProcessSuspend:
    '开启后，流程实例挂起时自动暂停活动任务的 SLA，流程恢复时自动恢复；关闭后流程挂起不影响 SLA 计时。',
  maxPauseMinutes:
    '单次暂停可停止计时的最长时长。达到上限后系统自动恢复计时；留空表示不限制单次暂停时长。',
  description:
    '用于记录策略适用范围、计时口径和管理约定，仅作为配置说明，不参与截止时间、提醒或升级计算。'
})

const emptyForm = () => ({
  policyCode: '',
  policyName: '',
  description: '',
  responseTargetMinutes: 60,
  completionTargetMinutes: 480,
  responseTimeBasis: 'WORKING_TIME',
  completionTimeBasis: 'WORKING_TIME',
  allowManualPause: false,
  pauseOnProcessSuspend: true,
  maxPauseMinutes: null,
  escalationSteps: []
})
const form = reactive(emptyForm())

async function load() {
  loading.value = true
  try {
    rows.value = await taskSlaPolicyApi.list()
  } finally {
    loading.value = false
  }
}

function reset(value = emptyForm()) {
  Object.assign(form, value)
}

function openCreate() {
  editingId.value = ''
  readonlyCode.value = false
  reset()
  dialogVisible.value = true
}

async function openEdit(row) {
  const detail = await taskSlaPolicyApi.get(row.id)
  const policy = detail.policy
  reset({
    policyCode: policy.policyCode,
    policyName: policy.policyName,
    description: policy.description || '',
    responseTargetMinutes: policy.responseTargetMinutes,
    completionTargetMinutes: policy.completionTargetMinutes,
    responseTimeBasis: policy.responseTimeBasis,
    completionTimeBasis: policy.completionTimeBasis,
    allowManualPause: policy.allowManualPause,
    pauseOnProcessSuspend: policy.pauseOnProcessSuspend,
    maxPauseMinutes: policy.maxPauseMinutes,
    escalationSteps: (detail.snapshot.escalationSteps || []).map(item => ({
      ...item,
      recipientConfigJson: item.recipientConfigJson || '{}',
      targetConfigJson: item.targetConfigJson || '{}'
    }))
  })
  editingId.value = row.id
  readonlyCode.value = true
  dialogVisible.value = true
}

function addStep() {
  form.escalationSteps.push({
    stepName: '',
    metricType: 'COMPLETION',
    triggerType: 'AT_DUE',
    offsetMinutes: 0,
    repeatIntervalMinutes: null,
    maxExecutions: 1,
    actionType: 'NOTIFY',
    templateCode: '',
    recipientConfigJson: '{"includeAssignee":true,"channels":["IN_APP"]}',
    targetConfigJson: '{}'
  })
}

async function save() {
  if (!form.policyCode || !form.policyName || !form.completionTargetMinutes) {
    ElMessage.warning('请填写策略编码、名称和办结目标')
    return
  }
  try {
    form.escalationSteps.forEach(step => {
      JSON.parse(step.recipientConfigJson || '{}')
      JSON.parse(step.targetConfigJson || '{}')
    })
  } catch {
    ElMessage.warning('升级步骤的 JSON 配置格式不正确')
    return
  }
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(form))
    if (editingId.value) {
      await taskSlaPolicyApi.update(editingId.value, payload)
    } else {
      await taskSlaPolicyApi.create(payload)
    }
    ElMessage.success('SLA策略草稿已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function publish(row) {
  await ElMessageBox.confirm('发布后流程发布会冻结此版本快照，确认发布吗？', '发布SLA策略')
  await taskSlaPolicyApi.publish(row.id)
  ElMessage.success('SLA策略已发布')
  await load()
}

function targetText(value) {
  return value ? `${value} 分钟` : '不计'
}

function statusText(status) {
  return ({ DRAFT: '草稿', PUBLISHED: '已发布', SUPERSEDED: '已替代' })[status] || status
}

function statusType(status) {
  return ({ DRAFT: 'warning', PUBLISHED: 'success', SUPERSEDED: 'info' })[status] || 'info'
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 18px; min-width: 0; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.page-header h2 { margin: 0 0 6px; font-size: 24px; }
.page-header p, .section-header span { margin: 0; color: #64748b; font-size: 13px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.editor-section { margin-top: 18px; }
.section-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 10px; }
.section-header h3 { margin: 0 0 4px; font-size: 16px; }
@media (max-width: 760px) {
  .form-grid { grid-template-columns: 1fr; }
  .page-header { align-items: stretch; flex-direction: column; }
}
</style>
