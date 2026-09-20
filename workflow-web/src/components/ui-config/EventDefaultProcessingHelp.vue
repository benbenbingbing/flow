<template>
  <el-tooltip
    placement="top"
    :show-after="200"
    :hide-after="50"
    :popper-style="{ maxWidth: 'min(520px, calc(100vw - 32px))', lineHeight: '1.6' }"
  >
    <template #content>
      <div class="event-default-help">
        <strong>{{ help.name }}（{{ help.code }}）</strong>
        <p>{{ help.description }}</p>
        <div v-for="endpoint in help.endpoints" :key="endpoint.path" class="event-default-endpoint">
          <span>{{ endpoint.label }}</span>
          <code>{{ endpoint.method }} {{ endpoint.path }}</code>
        </div>
        <p v-if="help.note">{{ help.note }}</p>
        <p v-if="help.execution">{{ help.execution }}</p>
      </div>
    </template>
    <button
      type="button"
      class="event-default-help-button"
      :aria-label="`查看${help.name}的平台默认处理说明`"
      @click.stop
    >
      <el-icon><QuestionFilled /></el-icon>
    </button>
  </el-tooltip>
</template>

<script setup>
import { QuestionFilled } from '@element-plus/icons-vue'

defineProps({ help: { type: Object, required: true } })
</script>

<style scoped>
.event-default-help {
  overflow-wrap: anywhere;
}
.event-default-help p {
  margin: 6px 0 0;
}
.event-default-endpoint {
  margin-top: 8px;
}
.event-default-endpoint code {
  display: block;
  white-space: normal;
  overflow-wrap: anywhere;
}
.event-default-help-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: help;
}
.event-default-help-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
