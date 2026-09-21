<template>
  <section class="form-section">
    <div class="section-heading"><span class="section-index">02</span><div><h3>{{ operationSectionTitle }}</h3><p>{{ operationSectionDescription }}</p></div></div>
    <template v-if="isJoin">
      <div class="field-grid">
        <el-form-item label="加入人员" prop="target_user_id" required><FormFieldRendererLinkage v-model="localValue.target_user_id" :field="fieldFor('target_user_id')" :disabled="isDisabled('target_user_id')" :context="fieldContext" :data-source-runtime="dataSourceRuntime" @change="syncAndLoad" /></el-form-item>
        <el-form-item label="来源部门" prop="source_dept_id" required><FormFieldRendererLinkage v-model="localValue.source_dept_id" :field="fieldFor('source_dept_id')" :disabled="isDisabled('source_dept_id')" :context="fieldContext" :data-source-runtime="dataSourceRuntime" @change="syncValue" /></el-form-item>
        <el-form-item label="人员类型" prop="employment_type" required><el-select v-model="localValue.employment_type" :disabled="isDisabled('employment_type')" style="width: 100%" @change="syncValue"><el-option v-for="item in EMPLOYMENT_TYPES" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="计划退出日期" prop="planned_leave_date" :required="requiresPlannedLeave"><el-date-picker v-model="localValue.planned_leave_date" :disabled="isDisabled('planned_leave_date')" type="date" value-format="YYYY-MM-DD" placeholder="长期成员可不填" style="width: 100%" @change="syncValue" /></el-form-item>
      </div>
    </template>
    <template v-else>
      <el-form-item label="目标项目成员" prop="project_member_id" required><FormFieldRendererLinkage v-model="localValue.project_member_id" :field="fieldFor('project_member_id')" :disabled="isDisabled('project_member_id')" :context="fieldContext" :data-source-runtime="dataSourceRuntime" @change="syncAndLoad" /></el-form-item>
      <el-alert v-if="memberContext.memberProjectMismatch" title="所选成员不属于当前项目，请重新选择。" type="error" :closable="false" show-icon />
    </template>
    <div v-if="showsAllocation" class="allocation-row">
      <el-form-item label="新投入比例" prop="new_allocation_percentage" required><el-input-number v-model="localValue.new_allocation_percentage" :disabled="isDisabled('new_allocation_percentage')" :min="0.01" :max="100" :precision="2" :step="5" controls-position="right" style="width: 100%" @change="syncAndLoad" /></el-form-item>
      <div class="allocation-meter" :class="{ exceeded: memberContext.allocation?.exceeded }"><div><span>跨项目合计</span><strong>{{ allocationTotal }}%</strong></div><el-progress :percentage="allocationPercentage" :status="memberContext.allocation?.exceeded ? 'exception' : undefined" :stroke-width="8" /><p>现有 {{ allocationCurrent }}%，本次 {{ allocationRequested }}%，可用 {{ allocationAvailable }}%。</p></div>
    </div>
  </section>
  <section v-if="isJoin || showsAccessChange" class="form-section">
    <div class="section-heading"><span class="section-index">03</span><div><h3>账号与环境权限</h3><p>权限范围决定是否增加系统负责人和安全负责人审批。</p></div></div>
    <div class="switch-list">
      <div class="switch-item"><div><strong>项目账号</strong><span>创建项目协作、代码仓库或管理工具账号</span></div><el-switch v-model="localValue.account_required_flag" :disabled="isDisabled('account_required_flag') || !isJoin" @change="syncValue" /></div>
      <div class="switch-item"><div><strong>环境访问</strong><span>申请开发、测试、验收或生产环境权限</span></div><el-switch v-model="localValue.environment_access_required_flag" :disabled="isDisabled('environment_access_required_flag') || !isJoin" @change="syncValue" /></div>
      <div class="switch-item"><div><strong>敏感权限</strong><span>涉及敏感数据、特权操作或高权限账号</span></div><el-switch v-model="localValue.sensitive_access_flag" :disabled="isDisabled('sensitive_access_flag') || !isJoin" @change="syncValue" /></div>
    </div>
    <el-form-item v-if="localValue.environment_access_required_flag" label="环境范围" prop="environment_scope" required class="environment-field"><el-checkbox-group v-model="localValue.environment_scope" :disabled="isDisabled('environment_scope')" @change="syncValue"><el-checkbox v-for="item in ENVIRONMENT_SCOPES" :key="item.value" :value="item.value" :label="item.value" border>{{ item.label }}</el-checkbox></el-checkbox-group></el-form-item>
  </section>
  <section v-if="isLeave" class="form-section">
    <div class="section-heading"><span class="section-index">04</span><div><h3>工作与角色交接</h3><p>有效角色或账号权限存在时，必须明确同项目交接人。</p></div></div>
    <el-alert :title="handoverAlertTitle" :type="routeFlags.handover_required_flag ? 'warning' : 'info'" :closable="false" show-icon class="section-alert" />
    <div class="field-grid">
      <el-form-item label="交接成员" prop="handover_member_id" :required="routeFlags.handover_required_flag"><FormFieldRendererLinkage v-model="localValue.handover_member_id" :field="fieldFor('handover_member_id')" :disabled="isDisabled('handover_member_id')" :context="fieldContext" :data-source-runtime="dataSourceRuntime" @change="syncValue" /></el-form-item>
      <el-form-item label="权限回收截止日期" prop="permission_revoke_deadline" :required="routeFlags.handover_required_flag"><el-date-picker v-model="localValue.permission_revoke_deadline" :disabled="isDisabled('permission_revoke_deadline')" type="date" value-format="YYYY-MM-DD" placeholder="不晚于退出后 1 天" style="width: 100%" @change="syncValue" /></el-form-item>
    </div>
    <el-form-item label="交接说明" prop="handover_description" :required="routeFlags.handover_required_flag"><el-input v-model="localValue.handover_description" :disabled="isDisabled('handover_description')" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="说明工作项、角色职责、文档、风险与未完成事项的交接结果" @input="syncValue" /></el-form-item>
  </section>
  <section class="form-section final-section">
    <div class="section-heading"><span class="section-index">{{ isLeave ? '05' : '04' }}</span><div><h3>变更说明</h3><p>说明业务原因和预期影响，审批记录将作为项目人员历史保留。</p></div></div>
    <el-form-item label="变更原因" prop="change_reason" required><el-input v-model="localValue.change_reason" :disabled="isDisabled('change_reason')" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="请输入成员变更原因、工作安排和风险说明" @input="syncValue" /></el-form-item>
    <el-alert v-if="validationMessages.length" :title="validationMessages[0]" type="error" :closable="false" show-icon><template #default><ul v-if="validationMessages.length > 1" class="error-list"><li v-for="message in validationMessages.slice(1)" :key="message">{{ message }}</li></ul></template></el-alert>
    <el-alert v-else-if="contextError" :title="contextError" type="warning" :closable="false" show-icon />
  </section>
</template>

<script setup>
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import { EMPLOYMENT_TYPES, ENVIRONMENT_SCOPES } from '../memberChangeModel.js'
defineProps({
  localValue: { type: Object, required: true }, isJoin: Boolean, isLeave: Boolean,
  operationSectionTitle: String, operationSectionDescription: String,
  fieldFor: { type: Function, required: true }, isDisabled: { type: Function, required: true },
  fieldContext: { type: Object, default: () => ({}) }, dataSourceRuntime: { type: Object, default: null },
  syncAndLoad: { type: Function, required: true }, syncValue: { type: Function, required: true },
  requiresPlannedLeave: Boolean, memberContext: { type: Object, required: true }, showsAllocation: Boolean,
  allocationTotal: [String, Number], allocationPercentage: Number, allocationCurrent: [String, Number],
  allocationRequested: [String, Number], allocationAvailable: [String, Number], showsAccessChange: Boolean,
  routeFlags: { type: Object, required: true }, handoverAlertTitle: String,
  validationMessages: { type: Array, default: () => [] }, contextError: String
})
</script>
