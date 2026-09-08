<template>
  <EmbedShell v-if="entryConfig" :entry-config="entryConfig" />
  <EmbedErrorState v-else :error="entryError" />
</template>

<script setup>
import { shallowRef } from 'vue'
import EmbedShell from './EmbedShell.vue'
import EmbedErrorState from './runtime/EmbedErrorState.vue'
import {
  clearEmbedEntryFragment,
  resolveEmbedEntryConfig
} from './entry/entryConfig.js'

const entryConfig = shallowRef(null)
const entryError = shallowRef(null)

try {
  // 任何 fragment 都在请求与错误上报初始化前清除，避免一次性凭据进入浏览器历史。
  clearEmbedEntryFragment()
  entryConfig.value = resolveEmbedEntryConfig()
} catch (error) {
  entryError.value = Object.freeze({
    errorCode: String(error?.errorCode || 'EMBED_ENTRY_INVALID'),
    message: 'Embed 入口无效，请从宿主系统重新打开',
    traceId: null,
    category: 'SESSION',
    recoverable: false,
    relaunchRequired: true
  })
}
</script>

<style>
html,
body,
#app {
  min-height: 100%;
  margin: 0;
}

body {
  overflow-x: hidden;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}
</style>
