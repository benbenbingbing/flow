<!--
  整表单模板：复制后调整 header/footer 或外层布局即可保留标准字段、联动和校验。
  也可直接作为包装组件使用，插槽接收 config/context。注册方式见 ../README.md。
  依赖宿主提供已发布的 form；完全自绘字段时请使用 customFormProps 契约。
-->
<template>
  <section>
    <slot name="header" :config="config" :context="context">
      <h3>{{ config.title || form.formName }}</h3>
    </slot>
    <FormPreviewLinkage
      ref="formRef"
      :form="standardForm"
      :model-value="modelValue"
      :readonly="readonly"
      :mode="mode"
      :context="context"
      :entity-code="entityCode"
      :entity-definition="entityDefinition"
      :entity-fields="entityFields"
      :data-source-runtime="dataSourceRuntime"
      :form-actions="slotActions"
      :show-header="false"
      height="auto"
      @update:model-value="emit('update:modelValue', $event)"
      @form-action="triggerAction"
    />
    <slot name="footer" :config="config" :context="context" />
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import { customFormProps, customFormEmits } from '@/contracts/form.js'

const props = defineProps(customFormProps)
const emit = defineEmits(customFormEmits)
const formRef = ref(null)

// 只在本地副本清除整表单组件名，防止内部渲染器再次装载本模板造成无限递归。
// 节点/字段扩展仍保留，原发布配置不变；字段联动由标准渲染器重新计算。
const standardForm = computed(() => ({ ...props.form, customComponent: '' }))
const slotActions = computed(() => Object.values(props.formActionSlots?.slots || {}).flat())

/** 动作回交外层宿主，最终仍由当前表单的动作白名单检查；不自行执行按钮接口。 */
function triggerAction(action) {
  const key = action.runtimeKey || action.key
  if (props.formActionSlots?.trigger) props.formActionSlots.trigger(key)
  else emit('form-action', key)
}

/**
 * 宿主提交时调用的无参校验，保留标准渲染器的字段、跨字段及唯一预检。
 * 未挂载时返回 false，避免尚未准备好就提交；运行异常继续由宿主处理。
 */
async function validate() {
  if (!formRef.value) return false
  return (await formRef.value.validate()) !== false
}

defineExpose({ validate })
</script>
