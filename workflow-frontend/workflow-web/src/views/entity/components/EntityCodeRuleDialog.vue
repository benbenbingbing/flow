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
    <el-form v-else v-loading="loading" :disabled="loading || codeRuleSaving" :model="codeRule" label-width="120px" size="default">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        {{ codeRule.generationMode === 'CUSTOM' ? '保存新记录时，由所选生成器生成完整编号。' : '默认格式：前缀 + 日期 + 序列号' }}
      </el-alert>

      <el-form-item label="生成方式">
        <el-radio-group v-model="codeRule.generationMode">
          <el-radio-button value="RULE">规则生成</el-radio-button>
          <el-radio-button value="CUSTOM">自定义生成</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="codeRule.generationMode === 'CUSTOM'">
        <el-form-item label="编码生成器" required>
          <template #label>
            <ConfigHelpLabel
              label="编码生成器"
              content="在业务模块实现 com.workflow.contracts.entity.code.spi.EntityCodeGeneratorProvider 接口，并通过 @Component 注册为 Spring Bean。由 generate(context, configuration) 方法返回完整编码，可参考 ProjectEntityCodeGenerator 示例。"
            />
          </template>
          <el-select v-model="codeRule.generatorCode" placeholder="选择编码生成器" style="width: 100%" @change="changeGenerator">
            <el-option v-for="generator in generators" :key="generator.code" :value="generator.code" :label="generator.displayName" />
          </el-select>
          <div v-if="!generators.length" class="form-tip">暂无适用于此实体的生成器，请先安装业务扩展。</div>
          <div v-else-if="codeRule.generatorCode && !selectedGenerator" class="form-tip">当前生成器不可用：{{ codeRule.generatorCode }}</div>
        </el-form-item>
        <ConfigSchemaEditor v-if="selectedGenerator" v-model="codeRule.generatorConfig" :schema="generatorFields" :grouped="false" />
      </template>
      <template v-else>
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
        <div class="form-tip">当前：{{ codeRule.seqLength }}位（格式：{{ '1'.padStart(codeRule.seqLength, '0') }}）</div>
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

      </template>
      <el-divider />

      <el-form-item label="编码示例">
        <el-input v-model="codeRule.example" readonly>
          <template #append>
            <el-button :loading="previewing" @click="previewCode">刷新</el-button>
          </template>
        </el-input>
        <div class="form-tip">{{ previewHint || '示例仅展示格式，不占用正式编号。' }}</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="codeRuleSaving" @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="codeRuleSaving" :disabled="loading || Boolean(loadError) || (codeRule.generationMode === 'CUSTOM' && !selectedGenerator)" @click="saveCodeRule">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { codeRuleApi } from '@/api/codeRule'
import PageState from '@/components/PageState.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import ConfigSchemaEditor from '@/components/ConfigSchemaEditor.vue'
import { generatorSchemaFields, generatorConfigDefaults } from '@/shared/entity-code-rule'

const props = defineProps({
  entity: { type: Object, required: true }
})
const emit = defineEmits(['close'])
const loading = ref(true)
const loadError = ref('')
const codeRuleSaving = ref(false)
const previewing = ref(false)
const generators = ref([])
const previewHint = ref('')
let disposed = false

/** 未配置规则时，以实体编码大写为前缀创建独立草稿，不携带数据库主键或序列状态。 */
const createCodeRuleDraft = (entityCode = '') => ({
  entityCode,
  generationMode: 'RULE',
  generatorCode: '',
  generatorConfig: {},
  prefix: entityCode.toUpperCase(),
  dateFormat: 'yyyyMMdd',
  seqLength: 6,
  seqType: 'DAY',
  example: ''
})
const codeRule = ref(createCodeRuleDraft())
const selectedGenerator = computed(() => generators.value.find(item => item.code === codeRule.value.generatorCode))
const generatorFields = computed(() => generatorSchemaFields(selectedGenerator.value?.configurationSchema))

/** 用户切换实现时清空旧实现的参数，防止同名参数被错误复用。 */
const changeGenerator = () => {
  codeRule.value.generatorConfig = generatorConfigDefaults(selectedGenerator.value?.configurationSchema)
}

/** 只提交允许编辑的配置；规则主键和当前序列由服务端按实体编码维护。 */
const buildCodeRuleSavePayload = () => ({
  entityCode: String(props.entity.entityCode || '').trim(),
  generationMode: codeRule.value.generationMode,
  generatorCode: codeRule.value.generatorCode || null,
  generatorConfig: codeRule.value.generatorConfig || {},
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
    const [data, options] = await Promise.all([
      codeRuleApi.getByEntityCode(normalizedEntityCode),
      codeRuleApi.generators(normalizedEntityCode)
    ])
    // 弹窗关闭后丢弃旧请求结果，避免旧实体的请求影响后来打开的配置。
    if (disposed) return
    generators.value = options || []
    if (data) {
      codeRule.value = {
        ...createCodeRuleDraft(normalizedEntityCode),
        ...data,
        entityCode: normalizedEntityCode,
        generationMode: data.generationMode || 'RULE',
        generatorConfig: data.generatorConfig || {}
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

// 修改配置后使旧示例失效；在途预览也必须核对配置签名，不能显示旧生成器的结果。
watch(() => JSON.stringify(buildCodeRuleSavePayload()), () => {
  if (!loading.value) codeRule.value.example = ''
  previewHint.value = ''
})

/** 自定义预览失败明确提示；只有内置规则允许用本地格式生成展示样例。 */
const previewCode = async () => {
  if (previewing.value) return
  const payload = buildCodeRuleSavePayload()
  const signature = JSON.stringify(payload)
  const isCurrent = () => !disposed && signature === JSON.stringify(buildCodeRuleSavePayload())
  previewing.value = true
  try {
    const preview = await codeRuleApi.preview(payload)
    if (isCurrent()) {
      codeRule.value.example = preview || ''
      previewHint.value = payload.generationMode === 'CUSTOM' && !preview ? '此生成器不提供预览，编号将在保存新记录时生成。' : ''
    }
  } catch (error) {
    if (!isCurrent()) return
    if (payload.generationMode === 'CUSTOM') {
      codeRule.value.example = ''
      previewHint.value = error?.message || '自定义编码预览失败，请检查生成器配置。'
      return
    }
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
