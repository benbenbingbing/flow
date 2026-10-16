<template>
  <el-card shadow="never" class="code-card">
    <template #header>
      <div class="code-header">
        <span>{{ title }}</span>
        <el-tag size="small">{{ language }}</el-tag>
      </div>
    </template>
    <slot />
  </el-card>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  language: { type: String, default: 'Code' }
})
</script>

<style scoped>
.code-card {
  margin: 14px 0;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(pre) {
  margin: 0;
  overflow-x: auto;
  color: #d8dee9;
  font-size: 13px;
  line-height: 1.65;
  background: #20242c;
  border-radius: 6px;
  padding: 16px;
}
</style>
