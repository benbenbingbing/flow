<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>工作日历</h2>
        <p>维护工作时段、节假日和组织部门绑定，所有时间按 IANA 时区计算。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建日历</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="calendarName" label="日历名称" min-width="180" />
      <el-table-column prop="calendarCode" label="编码" min-width="150" />
      <el-table-column prop="timezoneId" label="时区" min-width="150" />
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column label="默认" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.defaultFlag" type="success">是</el-tag>
          <span v-else>否</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">配置</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="success"
            @click="publish(row)"
          >
            发布
          </el-button>
          <el-button
            v-if="row.status === 'PUBLISHED' && !row.defaultFlag"
            link
            type="warning"
            @click="disable(row)"
          >
            停用
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '配置工作日历' : '新建工作日历'"
      width="min(1040px, 94vw)"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px">
        <div class="form-grid">
          <el-form-item label="日历编码" required>
            <el-input v-model="form.calendarCode" :disabled="Boolean(editingId)" />
          </el-form-item>
          <el-form-item label="日历名称" required>
            <el-input v-model="form.calendarName" />
          </el-form-item>
          <el-form-item label="IANA 时区" required>
            <el-select v-model="form.timezoneId" filterable allow-create style="width: 100%">
              <el-option label="中国标准时间 Asia/Shanghai" value="Asia/Shanghai" />
              <el-option label="协调世界时 UTC" value="UTC" />
              <el-option label="美国东部 America/New_York" value="America/New_York" />
              <el-option label="美国西部 America/Los_Angeles" value="America/Los_Angeles" />
            </el-select>
          </el-form-item>
          <el-form-item label="系统默认">
            <el-switch v-model="form.defaultFlag" />
          </el-form-item>
          <el-form-item label="生效日期">
            <el-date-picker
              v-model="effectiveRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="form.description" />
          </el-form-item>
        </div>

        <section class="editor-section">
          <div class="section-header">
            <div>
              <h3>每周工作时段</h3>
              <span>支持同一天配置多个班次和午休分段。</span>
            </div>
            <el-button :icon="Plus" @click="addPeriod">添加时段</el-button>
          </div>
          <el-table :data="form.periods" border>
            <el-table-column label="星期" width="150">
              <template #default="{ row }">
                <el-select v-model="row.dayOfWeek" style="width: 100%">
                  <el-option
                    v-for="item in weekOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="开始" min-width="150">
              <template #default="{ row }">
                <el-time-select v-model="row.start" start="00:00" step="00:30" end="23:30" />
              </template>
            </el-table-column>
            <el-table-column label="结束" min-width="150">
              <template #default="{ row }">
                <el-time-select v-model="row.end" start="00:30" step="00:30" end="24:00" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="64" align="center">
              <template #default="{ $index }">
                <el-button
                  text
                  circle
                  :icon="Delete"
                  aria-label="删除工作时段"
                  title="删除工作时段"
                  @click="form.periods.splice($index, 1)"
                />
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="editor-section">
          <div class="section-header">
            <div>
              <h3>特殊日期</h3>
              <span>节假日覆盖每周规则；补班日可指定工作时段。</span>
            </div>
            <el-button :icon="Plus" @click="addException">添加日期</el-button>
          </div>
          <el-table :data="form.exceptions" border>
            <el-table-column label="日期" width="170">
              <template #default="{ row }">
                <el-date-picker v-model="row.date" value-format="YYYY-MM-DD" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.type">
                  <el-option label="休息日" value="NON_WORKING" />
                  <el-option label="补班日" value="WORKING" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="名称" min-width="150">
              <template #default="{ row }"><el-input v-model="row.name" /></template>
            </el-table-column>
            <el-table-column label="补班时段" min-width="220">
              <template #default="{ row }">
                <el-input
                  v-model="row.periodText"
                  :disabled="row.type !== 'WORKING'"
                  placeholder="09:00-12:00,13:00-18:00"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="64" align="center">
              <template #default="{ $index }">
                <el-button
                  text
                  circle
                  :icon="Delete"
                  aria-label="删除特殊日期"
                  title="删除特殊日期"
                  @click="form.exceptions.splice($index, 1)"
                />
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="editor-section">
          <div class="section-header">
            <div>
              <h3>作用域绑定</h3>
              <span>部门绑定优先于组织绑定，同范围按优先级取最高值。</span>
            </div>
            <el-button :icon="Plus" @click="addBinding">添加绑定</el-button>
          </div>
          <el-table :data="form.bindings" border>
            <el-table-column label="范围类型" width="150">
              <template #default="{ row }">
                <el-select v-model="row.scopeType">
                  <el-option label="部门" value="DEPARTMENT" />
                  <el-option label="组织" value="ORGANIZATION" />
                </el-select>
              </template>
              <template #header>
                <ConfigHelpLabel
                  label="范围类型"
                  help-key="workCalendar.scopeType"
                />
              </template>
            </el-table-column>
            <el-table-column label="范围 ID" min-width="190">
              <template #default="{ row }"><el-input v-model="row.scopeKey" /></template>
            </el-table-column>
            <el-table-column label="优先级" width="120">
              <template #default="{ row }"><el-input-number v-model="row.priority" :min="0" /></template>
            </el-table-column>
            <el-table-column label="生效开始" width="170">
              <template #default="{ row }">
                <el-date-picker v-model="row.effectiveFrom" value-format="YYYY-MM-DD" />
              </template>
            </el-table-column>
            <el-table-column label="生效结束" width="170">
              <template #default="{ row }">
                <el-date-picker v-model="row.effectiveTo" value-format="YYYY-MM-DD" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="64" align="center">
              <template #default="{ $index }">
                <el-button
                  text
                  circle
                  :icon="Delete"
                  aria-label="删除日历绑定"
                  title="删除日历绑定"
                  @click="form.bindings.splice($index, 1)"
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
import { onMounted, reactive, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workCalendarApi } from '@/api/sla'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const dialogVisible = ref(false)
const editingId = ref('')
const effectiveRange = ref([])
const weekOptions = [
  { value: 1, label: '星期一' },
  { value: 2, label: '星期二' },
  { value: 3, label: '星期三' },
  { value: 4, label: '星期四' },
  { value: 5, label: '星期五' },
  { value: 6, label: '星期六' },
  { value: 7, label: '星期日' }
]

const emptyForm = () => ({
  calendarCode: '',
  calendarName: '',
  timezoneId: 'Asia/Shanghai',
  description: '',
  defaultFlag: false,
  periods: weekdays(),
  exceptions: [],
  bindings: []
})
const form = reactive(emptyForm())

function weekdays() {
  return [1, 2, 3, 4, 5].flatMap(day => [
    { dayOfWeek: day, start: '09:00', end: '12:00' },
    { dayOfWeek: day, start: '13:00', end: '18:00' }
  ])
}

async function load() {
  loading.value = true
  try {
    rows.value = await workCalendarApi.list()
  } finally {
    loading.value = false
  }
}

function reset(value = emptyForm()) {
  Object.assign(form, value)
}

function openCreate() {
  editingId.value = ''
  effectiveRange.value = []
  reset()
  dialogVisible.value = true
}

async function openEdit(row) {
  const detail = await workCalendarApi.get(row.id)
  const snapshot = detail.snapshot
  const periods = []
  Object.entries(snapshot.weeklyPeriods || {}).forEach(([day, values]) => {
    values.forEach(period => periods.push({
      dayOfWeek: Number(day),
      start: minuteText(period.startMinute),
      end: minuteText(period.endMinute)
    }))
  })
  const exceptions = Object.entries(snapshot.exceptions || {}).map(([date, item]) => ({
    date,
    type: item.type,
    name: item.name || '',
    description: '',
    periodText: (item.periods || [])
      .map(period => `${minuteText(period.startMinute)}-${minuteText(period.endMinute)}`)
      .join(',')
  }))
  reset({
    calendarCode: detail.calendar.calendarCode,
    calendarName: detail.calendar.calendarName,
    timezoneId: detail.calendar.timezoneId,
    description: detail.calendar.description || '',
    defaultFlag: detail.calendar.defaultFlag,
    periods,
    exceptions,
    bindings: (detail.bindings || []).map(item => ({ ...item }))
  })
  effectiveRange.value = [
    detail.calendar.effectiveFrom,
    detail.calendar.effectiveTo
  ].filter(Boolean)
  editingId.value = row.id
  dialogVisible.value = true
}

function addPeriod() {
  form.periods.push({ dayOfWeek: 1, start: '09:00', end: '18:00' })
}

function addException() {
  form.exceptions.push({
    date: '',
    type: 'NON_WORKING',
    name: '',
    description: '',
    periodText: ''
  })
}

function addBinding() {
  form.bindings.push({
    scopeType: 'DEPARTMENT',
    scopeKey: '',
    priority: 0,
    effectiveFrom: null,
    effectiveTo: null
  })
}

async function save() {
  if (!form.calendarCode || !form.calendarName || !form.timezoneId) {
    ElMessage.warning('请填写日历编码、名称和时区')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      effectiveFrom: effectiveRange.value?.[0] || null,
      effectiveTo: effectiveRange.value?.[1] || null,
      periods: form.periods.map(item => ({
        dayOfWeek: item.dayOfWeek,
        startMinute: textMinute(item.start),
        endMinute: textMinute(item.end)
      })),
      exceptions: form.exceptions.map(item => ({
        date: item.date,
        type: item.type,
        name: item.name,
        description: item.description,
        periods: parsePeriods(item.periodText)
      })),
      bindings: form.bindings
    }
    if (editingId.value) {
      await workCalendarApi.update(editingId.value, payload)
    } else {
      await workCalendarApi.create(payload)
    }
    ElMessage.success('工作日历草稿已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function publish(row) {
  await ElMessageBox.confirm('发布后可被流程节点引用，确认发布吗？', '发布工作日历')
  await workCalendarApi.publish(row.id)
  ElMessage.success('工作日历已发布')
  await load()
}

async function disable(row) {
  await ElMessageBox.confirm('停用后不会影响已冻结的在途任务，确认停用吗？', '停用工作日历')
  await workCalendarApi.disable(row.id)
  ElMessage.success('工作日历已停用')
  await load()
}

function textMinute(value) {
  if (!value) return 0
  const [hour, minute] = value.split(':').map(Number)
  return hour * 60 + minute
}

function minuteText(value) {
  const hour = Math.floor(value / 60)
  const minute = value % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

function parsePeriods(value) {
  if (!value) return []
  return value.split(',').map(item => {
    const [start, end] = item.trim().split('-')
    return { startMinute: textMinute(start), endMinute: textMinute(end) }
  })
}

function statusText(status) {
  return ({
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    SUPERSEDED: '已替代',
    DISABLED: '已停用'
  })[status] || status
}

function statusType(status) {
  return ({
    DRAFT: 'warning',
    PUBLISHED: 'success',
    SUPERSEDED: 'info',
    DISABLED: 'info'
  })[status] || 'info'
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
