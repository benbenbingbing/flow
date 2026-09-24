<template>
  <el-button type="primary" link size="small" @click="emit('edit', role)">编辑</el-button>
  <el-button type="primary" link size="small" @click="emit('assign-menu', role)">分配</el-button>
  <el-button type="primary" link size="small" @click="emit('users', role)">用户</el-button>
  <el-button
    type="danger"
    link
    size="small"
    :disabled="role.roleCode === 'super_admin'"
    @click="emit('delete', role)"
  >删除</el-button>
</template>

<script setup lang="ts">
defineProps<{ role: Record<string, unknown> }>()
const emit = defineEmits(['edit', 'assign-menu', 'users', 'delete'])
</script>
