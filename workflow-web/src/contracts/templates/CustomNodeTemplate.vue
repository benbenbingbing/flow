<!--
  只读摘要节点模板，读取整个 modelValue。无需为了显示几个字段重写整表单。
  注册为 NODE 并声明 nodeTypes/supportedBindings；config.fieldCodes 控制显示字段。
  若增加编辑交互，应遵守字段权限并发送整个新对象，参见 form-node.js。
-->
<template>
  <section>
    <h4>{{ config.title || node.props?.label || '数据摘要' }}</h4>
    <dl>
      <template v-for="fieldCode in fieldCodes" :key="fieldCode">
        <dt>{{ config.labels?.[fieldCode] || fieldCode }}</dt>
        <dd>{{ modelValue[fieldCode] ?? config.emptyText ?? '—' }}</dd>
      </template>
    </dl>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { formNodeProps, formNodeEmits } from '@/contracts/form-node.js'

const props = defineProps(formNodeProps)
defineEmits(formNodeEmits)
const fieldCodes = computed(() => Array.isArray(props.config.fieldCodes) ? props.config.fieldCodes : [])
</script>
