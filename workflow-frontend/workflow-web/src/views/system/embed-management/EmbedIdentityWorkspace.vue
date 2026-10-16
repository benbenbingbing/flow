<template>
  <div class="identity-workspace">
    <el-alert
      title="外部 Subject 仅在创建或精确查找 Binding 时短暂出现，管理端不回显原文，也不会写入 localStorage 或日志。"
      type="info"
      show-icon
      :closable="false"
      class="identity-alert"
    />
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="Identity Providers" name="providers">
        <EmbedProviderPanel />
      </el-tab-pane>
      <el-tab-pane label="External Identity Bindings" name="bindings">
        <EmbedBindingPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import EmbedBindingPanel from './EmbedBindingPanel.vue'
import EmbedProviderPanel from './EmbedProviderPanel.vue'

const activeTab = ref('providers')
</script>

<style scoped>
.identity-alert {
  margin: 4px 0 12px;
}
</style>
