<template>
  <el-dialog
    :model-value="true"
    title="数据编码规则配置"
    width="min(550px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!codeRuleSaving"
    :show-close="!codeRuleSaving"
    @update:model-value="visible => !visible && emit('close')"
  >
    <p class="entity-context">实体：{{ entity.entityName }}（{{ entity.entityCode }}）</p>
    <PageState
      v-if="loadError"
      type="error"
      title="编码规则加载失败"
      :description="loadError"
      retryable
      compact
      @retry="loadCodeRule(entity.entityCode)"
    />
    <el-form v-else v-loading="loading" :disabled="loading || codeRuleSaving" :model="codeRule" label-width="100px" size="default">
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
        <div class="form-tip">当前：{{ codeRule.seqLength }}位（格式：{{ '0'.repeat(codeRule.seqLength) }}1）</div>
      </el-form-item>

      <el-form-item label="重置周期">
        <el-radio-group v-model="codeRule.seqType">
          <el-radio-button value="DAY">按天</el-radio-button>
          <el-radio-button value="MONTH">按月</el-radio-button>
          <el-radio-button value="YEAR">按年</el-radio-button>
          <el-radio-button value="NEVER">不重置</el-radio-button>
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
            <el-button :loading="previewing" @click="previewCode">刷新</el-button>
          </template>
        </el-input>
        <div class="form-tip">根据上述配置生成的编码示例</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="codeRuleSaving" @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="codeRuleSaving" :disabled="loading || Boolean(loadError)" @click="saveCodeRule">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { codeRuleApi } from '@/api/codeRule'
import PageState from '@/components/PageState.vue'

const props = defineProps({
  entity: { type: Object, required: true }
})
const emit = defineEmits(['close'])
const loading = ref(true)
const loadError = ref('')
const codeRuleSaving = ref(false)
const previewing = ref(false)
let disposed = false

/** 未配置规则时，以实体编码大写为前缀创建独立草稿，不携带数据库主键或序列状态。 */
const createCodeRuleDraft = (entityCode = '') => ({
  entityCode,
  prefix: entityCode.toUpperCase(),
  dateFormat: 'yyyyMMdd',
  seqLength: 6,
  seqType: 'DAY',
  example: ''
})
const codeRule = ref(createCodeRuleDraft())

/** 只提交允许编辑的配置；规则主键和当前序列由服务端按实体编码维护。 */
const buildCodeRuleSavePayload = () => ({
  entityCode: String(props.entity.entityCode || '').trim(),
  prefix: codeRule.value.prefix,
  dateFormat: codeRule.value.dateFormat,
  seqLength: codeRule.value.seqLength,
  seqType: codeRule.value.seqType
})

/** 加载选中实体的规则；失败时阻止保存默认草稿，避免覆盖已有配置。 */
const loadCodeRule = async (entityCode) => {
  const normalizedEntityCode = String(entityCode || '').trim()
  loading.value = true
  loadError.value = ''
  codeRule.value = createCodeRuleDraft(normalizedEntityCode)
  try {
    if (!normalizedEntityCode) throw new Error('实体编码不能为空')
    const data = await codeRuleApi.getByEntityCode(normalizedEntityCode)
    // 弹窗关闭后丢弃旧请求结果，避免旧实体的请求影响后来打开的配置。
    if (disposed) return
    if (data) {
      codeRule.value = {
        ...createCodeRuleDraft(normalizedEntityCode),
        ...data,
        entityCode: normalizedEntityCode
      }
    } else {
      await previewCode()
    }
  } catch (error) {
    if (!disposed) loadError.value = error?.message || '无法读取编码规则，请稍后重试。'
  } finally {
    if (!disposed) loading.value = false
  }
}

/** 预览仅生成展示样例，不消耗序列；服务不可用时使用本地日期与首个序号兜底。 */
const previewCode = async () => {
  if (previewing.value) return
  const payload = buildCodeRuleSavePayload()
  previewing.value = true
  try {
    const preview = await codeRuleApi.preview(payload)
    if (!disposed) codeRule.value.example = preview
  } catch (error) {
    if (disposed) return
    const date = new Date()
    const dateStr = (payload.dateFormat || 'yyyyMMdd')
      .replace('yyyy', String(date.getFullYear()))
      .replace('yy', String(date.getFullYear()).slice(-2))
      .replace('MM', String(date.getMonth() + 1).padStart(2, '0'))
      .replace('dd', String(date.getDate()).padStart(2, '0'))
      .replace(/[-/]/g, '')
    const seqStr = '1'.padStart(payload.seqLength || 6, '0')
    codeRule.value.example = (payload.prefix || '') + dateStr + seqStr
  } finally {
    if (!disposed) previewing.value = false
  }
}

/** 保存当前实体的规则；保存中禁止重复提交，失败时保留草稿供用户重试。 */
const saveCodeRule = async () => {
  if (loading.value || loadError.value || codeRuleSaving.value) return
  const payload = buildCodeRuleSavePayload()
  if (!payload.entityCode) {
    ElMessage.error('实体编码不能为空')
    return
  }
  codeRuleSaving.value = true
  try {
    await codeRuleApi.save(payload, { silentError: true })
    if (disposed) return
    await loadCodeRule(payload.entityCode)
    if (disposed) return
    ElMessage.success('编码规则保存成功')
    emit('close')
  } catch (error) {
    if (!disposed) ElMessage.error(error?.message || '编码规则保存失败')
  } finally {
    if (!disposed) codeRuleSaving.value = false
  }
}

onMounted(() => { void loadCodeRule(props.entity.entityCode) })
onUnmounted(() => { disposed = true })
</script>

<style scoped>
.entity-context {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.form-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
