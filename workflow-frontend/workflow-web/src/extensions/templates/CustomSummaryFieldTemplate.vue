<!-- 摘要和输入组件使用相同 FIELD 契约；formData 为当前表单数据，配置来自 config。 -->
<template>
  <section>
    <h4>{{ config.title || node?.props?.label || '数据摘要' }}</h4>
    <dl>
      <template v-for="fieldCode in fieldCodes" :key="fieldCode">
        <dt>{{ config.labels?.[fieldCode] || fieldCode }}</dt>
        <dd>{{ formData[fieldCode] ?? config.emptyText ?? '—' }}</dd>
      </template>
    </dl>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { formFieldProps, formFieldEmits } from '@flow/workflow-core/extensions/contracts/form-field'

const props = defineProps(formFieldProps)
defineEmits(formFieldEmits)
const fieldCodes = computed(() => Array.isArray(props.config.fieldCodes) ? props.config.fieldCodes : [])
</script>
