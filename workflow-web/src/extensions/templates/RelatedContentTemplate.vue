<!--
  关联内容模板：在关联配置中指定对应 FORM/LIST 注册组件，而非普通列表 customComponent。
  示例只读显示宿主目标记录和发布能力；LIST 数据请显式调用 runtime.query 并管理分页。
  业务动作使用 runtime.dispatch(action, { targetRecordIds })，不能直接提交 modelValue。
-->
<template>
  <section>
    <h4>{{ config.title || '关联内容' }}</h4>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
    <el-button :loading="pending" @click="refresh">刷新关联内容</el-button>
    <dl>
      <template v-for="field in fields" :key="field.fieldCode || field.id">
        <dt>{{ field.fieldLabel || field.fieldName || field.fieldCode }}</dt>
        <dd>{{ modelValue[field.fieldCode] ?? '—' }}</dd>
      </template>
    </dl>
    <slot :record="modelValue" :runtime="runtime" :context="context" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { relatedContentProps } from '@/extensions/contracts/related-content.js'

const props = defineProps(relatedContentProps)
const pending = ref(false)
const errorMessage = ref('')

/** 由宿主重新解析目标，保留当前发布版本与关联范围；异常留在本组件内展示。 */
async function refresh() {
  if (pending.value) return
  pending.value = true
  errorMessage.value = ''
  try {
    await props.runtime.refresh()
  } catch (error) {
    errorMessage.value = error?.message || '关联内容刷新失败'
  } finally {
    pending.value = false
  }
}
</script>
