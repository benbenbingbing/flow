<template>
  <el-form-item :label="label">
    <template #label>
      <ConfigHelpLabel :label="label" help-key="entityForm.interfaceExtension" :content="help" />
    </template>
    <el-select :model-value="extensionId" :aria-label="label" :disabled="disabled" clearable filterable placeholder="不绑定"
      style="width: 100%" @update:model-value="$emit('update:extensionId', $event)">
      <el-option v-for="item in interfaces" :key="item.extensionId" :label="`${item.displayName} (${item.extensionKey})`" :value="item.extensionId" />
    </el-select>
    <div class="form-tip">仅显示当前用途和表单范围可用的完整接口。</div>
    <slot />
  </el-form-item>
  <details class="property-advanced">
    <summary>输入与输出映射</summary>
    <div class="property-advanced-body">
      <el-form-item label="输入映射">
        <template #label>
          <JsonConfigLabel label="输入映射" help-key="entityForm.dataSourceInputMapping"
            :help="cascader ? cascaderInputHelp : null" />
        </template>
        <el-input :model-value="inputMappingText" aria-label="输入映射" type="textarea" :rows="3"
          :placeholder="JSON.stringify(cascader ? cascaderInputHelp.example : { 'filters.ownerId': 'data.owner_id' })"
          @update:model-value="$emit('update:inputMappingText', $event)" />
        <div v-if="extensionId && contextParameters.length" class="context-mapping">
          <el-input v-model.trim="contextTarget" aria-label="接口入参路径" placeholder="接口入参路径" :disabled="disabled" />
          <el-select v-model="contextKey" aria-label="接口入参上下文参数" placeholder="选择上下文参数"
            filterable :disabled="disabled">
            <el-option v-for="parameter in contextParameters" :key="parameter.key"
              :value="parameter.key" :label="parameterOptionLabel(parameter.label, parameter.key)">
              <ParameterOptionLabel :label="parameter.label" :code="parameter.key" />
            </el-option>
          </el-select>
          <el-button :disabled="disabled || !contextTarget || !contextKey" @click="addContextMapping">加入映射</el-button>
          <span v-if="contextMappingError" class="el-form-item__error" role="status">{{ contextMappingError }}</span>
        </div>
        <div class="form-tip">左边填接口接收的参数名，右边填取值路径。接口使用默认入参时留空。</div>
        <details class="mapping-reference" :open="cascader">
          <summary>可用取值来源和示例</summary>
          <p v-if="cascader" class="form-tip">
            用 <code>{{ JSON.stringify(cascaderInputHelp.example) }}</code> {{ cascaderInputHelp.result }}
          </p>
          <dl class="mapping-reference__list">
            <template v-for="source in FORM_DATA_SOURCE_INPUT_SOURCES" :key="source.prefix">
              <dt><code>{{ source.prefix }}.</code></dt>
              <dd>
                {{ source.description }}
                <span class="mapping-reference__examples">
                  <code v-for="example in source.examples" :key="example">{{ example }}</code>
                </span>
              </dd>
            </template>
            <dt>固定值</dt>
            <dd>用 <code>{"pageSize":{"literal":100}}</code> 给接口传入固定的数字 100。</dd>
          </dl>
          <p class="form-tip">当前页面未提供的参数、记录或上下文信息，取值可能为空。</p>
          <p v-if="cascader" class="form-tip">{{ CASCADER_INPUT_MAPPING_NOTE }}</p>
        </details>
      </el-form-item>
      <el-form-item v-if="cascader" label="输出映射">
        <div class="form-tip">填写接口节点实际返回的字段名。留空时使用输入框提示的标准字段名。</div>
        <div class="cascader-output-fields">
          <label v-for="field in cascaderOutputFields" :key="field.key" class="cascader-output-field">
            <span>{{ field.label }}</span>
            <el-input :model-value="cascaderNodeFields[field.key]" :aria-label="field.label" :placeholder="field.key"
              :disabled="disabled" clearable @update:model-value="$emit('update:cascaderNodeField', field.key, $event)" />
          </label>
        </div>
        <div v-if="cascaderLoadMode === 'CHILDREN'" class="form-tip">
          末级字段须返回布尔值。每次只返回当前层节点。
        </div>
        <div v-else class="form-tip">
          例如接口返回 <code>id</code>、<code>name</code>、<code>nodes</code>，依次填到节点值、显示名称、下级节点。每一级使用相同字段名。
        </div>
        <div class="form-tip">禁用标记若有返回，须为布尔值。</div>
      </el-form-item>
      <el-form-item v-if="!cascader || hasResponseMapping" :label="responseMappingLabel">
        <template #label>
          <JsonConfigLabel :label="responseMappingLabel" help-key="entityForm.dataSourceOutputMapping" />
        </template>
        <el-input :model-value="outputMappingText" :aria-label="responseMappingLabel" type="textarea" :rows="3"
          :placeholder="outputPlaceholder"
          @update:model-value="$emit('update:outputMappingText', $event)" />
        <div class="form-tip">目标字段映射到数据源返回路径，留空时使用原始返回值。</div>
      </el-form-item>
    </div>
  </details>
</template>

<script setup>
import { computed, ref } from 'vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import JsonConfigLabel from '@/components/JsonConfigLabel.vue'
import ParameterOptionLabel, { parameterOptionLabel } from '@/components/page-parameters/ParameterOptionLabel.vue'
import { CASCADER_INPUT_MAPPING_NOTE, FORM_DATA_SOURCE_INPUT_SOURCES, getJsonConfigHelp } from '@/shared/json-config-help'
import { formContextParameters } from '@/shared/form-context-parameters'

const cascaderInputHelp = {
  title: '级联接口输入映射怎么填',
  example: { countryCode: 'data.country_code' },
  result: '把当前表单的 country_code 字段传给接口的 countryCode 参数。',
  notes: [CASCADER_INPUT_MAPPING_NOTE, ...getJsonConfigHelp('entityForm.dataSourceInputMapping').notes]
}
const props = defineProps({
  extensionId: { type: [String, Number], default: '' },
  inputMappingText: { type: String, default: '' },
  outputMappingText: { type: String, default: '' },
  contextParameters: { type: Array, default: () => formContextParameters },
  interfaces: { type: Array, default: () => [] },
  label: { type: String, default: '扩展接口' },
  help: { type: String, default: '' },
  outputPlaceholder: { type: String, default: '{"assigneeName":"data.user.name"}' },
  disabled: Boolean,
  cascader: Boolean,
  cascaderNodeFields: { type: Object, default: () => ({}) },
  cascaderLoadMode: { type: String, default: 'ALL' }
})
const emit = defineEmits(['update:extensionId', 'update:inputMappingText', 'update:outputMappingText', 'update:cascaderNodeField'])
const contextTarget = ref('')
const contextKey = ref('')
const contextMappingError = ref('')

/** 合并到现有映射，允许同一接口接收字段、固定值和上下文值。 */
function addContextMapping() {
  const reserved = ['__proto__', 'constructor', 'prototype']
  if (contextTarget.value.split('.').some(part => !part || part.trim() !== part || reserved.includes(part))) {
    contextMappingError.value = '请输入有效的接口入参路径'
    return
  }
  try {
    const current = JSON.parse(props.inputMappingText.trim() || '{}')
    if (!current || typeof current !== 'object' || Array.isArray(current)) throw new Error('输入映射须为 JSON 对象')
    emit('update:inputMappingText', JSON.stringify({ ...current, [contextTarget.value]: 'ctx.' + contextKey.value }, null, 2))
    contextTarget.value = ''
    contextKey.value = ''
    contextMappingError.value = ''
  } catch {
    contextMappingError.value = '请先修正现有的输入映射'
  }
}

const cascaderOutputFields = computed(() => [
  { key: 'value', label: '节点值' },
  { key: 'label', label: '显示名称' },
  props.cascaderLoadMode === 'CHILDREN'
    ? { key: 'leaf', label: '末级标记' }
    : { key: 'children', label: '下级节点' },
  { key: 'disabled', label: '禁用标记' }
])
const responseMappingLabel = computed(() => props.cascader ? '响应路径映射' : '输出映射')
// 已配置的通用映射仍可编辑。级联的新配置入口直接对应节点字段。
const hasResponseMapping = computed(() => !['', '{}'].includes(props.outputMappingText.replace(/\s/g, '')))
</script>

<style scoped>
.form-tip { width: 100%; margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.property-advanced { margin: 4px 0 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; background: var(--el-fill-color-extra-light); }
.property-advanced > summary { padding: 10px 12px; color: var(--el-text-color-regular); cursor: pointer; font-size: 12px; font-weight: 500; }
.property-advanced-body { padding: 12px 12px 0; border-top: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color); }
.context-mapping { position: relative; display: grid; grid-template-columns: minmax(80px, 1fr) minmax(100px, 1fr) auto; gap: 6px; width: 100%; margin: 6px 0; }
.context-mapping [role="status"] { position: static; grid-column: 1 / -1; }
.mapping-reference {
  width: 100%;
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.5;
}
.mapping-reference > summary { cursor: pointer; font-weight: 500; }
.mapping-reference__list {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 6px 12px;
  margin: 8px 0;
}
.mapping-reference__list dd { margin: 0; }
.mapping-reference__examples { display: inline-flex; flex-wrap: wrap; gap: 2px 8px; margin-inline-start: 6px; }
.mapping-reference code { color: var(--el-text-color-primary); overflow-wrap: anywhere; }
.cascader-output-fields {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  gap: 8px 16px;
  margin: 8px 0 4px;
}
.cascader-output-field { display: flex; align-items: center; gap: 8px; }
.cascader-output-field > span { flex: none; color: var(--el-text-color-regular); }
</style>
