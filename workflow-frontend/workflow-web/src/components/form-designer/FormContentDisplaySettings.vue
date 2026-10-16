<template>
  <div class="form-content-display-settings">
    <el-form-item label="显示名称">
      <el-input v-model="name" :placeholder="placeholder" :disabled="disabled" :maxlength="200" />
    </el-form-item>
    <el-form-item label="显示标题">
      <el-switch v-model="showTitle" :disabled="disabled" />
      <div class="form-tip">关闭后隐藏标题，内容和操作按钮仍显示。</div>
    </el-form-item>
  </div>
</template>

<script setup>
// 显示属性不依赖关系所有权；调用方分别写回节点或关联内容配置，保存和权限规则由原入口维护。
const name = defineModel({ type: String, default: '' })
const showTitle = defineModel('showTitle', { type: Boolean, default: true })
defineProps({
  placeholder: { type: String, default: '请输入显示名称' },
  disabled: Boolean
})
</script>

<style scoped>
.form-tip { width: 100%; margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
