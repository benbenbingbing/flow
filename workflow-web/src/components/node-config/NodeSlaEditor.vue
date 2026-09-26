<script setup>
import { computed } from 'vue'
import SettingsSection from '../SettingsSection.vue'
import ConfigHelpLabel from '../ConfigHelpLabel.vue'
import { getProcessConditionFieldCode } from '@/shared/process-config'
// 受控编辑当前节点的 SLA 草稿；切换节点和应用到命令栈仍由父级会话负责。
const slaForm = defineModel({ type: Object, required: true })
const props = defineProps({
  slaPolicyOptions: { type: Array, default: () => [] },
  workCalendarOptions: { type: Array, default: () => [] },
  entityFields: { type: Array, default: () => [] }
})
const selectedSlaPolicyName = computed(() => props.slaPolicyOptions.find(
  item => item.policyCode === slaForm.value.policyCode)?.policyName || '')
</script>
<template>
  <SettingsSection
    title="任务 SLA"
    description="按已发布策略计算首次响应和办结时限"
    :default-expanded="slaForm.enabled"
  >
    <template #summary>
      <el-tag :type="slaForm.enabled ? 'success' : 'info'" size="small">
        {{ slaForm.enabled ? selectedSlaPolicyName || '已启用' : '未启用' }}
      </el-tag>
    </template>
    <el-form-item label="启用 SLA">
      <el-switch v-model="slaForm.enabled" />
    </el-form-item>
    <template v-if="slaForm.enabled">
      <el-form-item label="SLA 策略" required>
        <el-select
          v-model="slaForm.policyCode"
          filterable
          placeholder="选择已发布策略"
          style="width: 100%"
        >
          <el-option
            v-for="policy in slaPolicyOptions"
            :key="policy.policyCode"
            :label="`${policy.policyName}（v${policy.version}）`"
            :value="policy.policyCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="日历来源">
        <template #label>
          <ConfigHelpLabel
            label="日历来源"
            help-key="process.slaCalendarSource"
          />
        </template>
        <el-select v-model="slaForm.calendarSource" style="width: 100%">
          <el-option label="节点指定" value="NODE" />
          <el-option label="流程指定" value="PROCESS" />
          <el-option label="业务归属部门" value="BUSINESS_DEPT" />
          <el-option label="发起人部门" value="STARTER_DEPT" />
          <el-option label="系统默认" value="SYSTEM_DEFAULT" />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="slaForm.calendarSource === 'NODE'"
        label="节点日历"
        required
      >
        <el-select v-model="slaForm.calendarCode" filterable style="width: 100%">
          <el-option
            v-for="calendar in workCalendarOptions"
            :key="calendar.calendarCode"
            :label="`${calendar.calendarName}（${calendar.timezoneId}）`"
            :value="calendar.calendarCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="slaForm.calendarSource === 'PROCESS'"
        label="流程日历"
        required
      >
        <el-select v-model="slaForm.processCalendarCode" filterable style="width: 100%">
          <el-option
            v-for="calendar in workCalendarOptions"
            :key="calendar.calendarCode"
            :label="`${calendar.calendarName}（${calendar.timezoneId}）`"
            :value="calendar.calendarCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="slaForm.calendarSource === 'BUSINESS_DEPT'"
        label="部门字段"
        required
      >
        <el-select
          v-model="slaForm.businessFieldCode"
          filterable
          placeholder="选择业务数据中的部门字段"
          style="width: 100%"
        >
          <el-option
            v-for="field in entityFields"
            :key="getProcessConditionFieldCode(field)"
            :label="field.fieldName || field.fieldLabel || field.fieldCode"
            :value="getProcessConditionFieldCode(field)"
          />
        </el-select>
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="发布时冻结策略与日历快照；转办不会重新计算截止时间。"
      />
    </template>
  </SettingsSection>
</template>
