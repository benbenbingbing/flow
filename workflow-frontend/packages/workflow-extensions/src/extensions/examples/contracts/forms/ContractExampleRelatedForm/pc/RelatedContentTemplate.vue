<!--
  独立扩展页模板：在 SAME_RECORD / INTERFACE_SERVICE 配置中指定对应 FORM/LIST 注册组件。
  示例只读显示宿主目标记录和发布能力；LIST 数据请显式调用 runtime.query 并管理分页。
  扩展表单通过 update:modelValue 更新宿主草稿，再由内置保存入口提交；普通关联沿用目标 PAGE 组件。
  独立扩展页可调用 runtime.dispatch(action, { targetRecordIds })；调用前需检查该方法存在。
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
