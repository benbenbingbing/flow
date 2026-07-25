<template>
  <el-button type="primary" link size="small" @click="emit('edit', role)">编辑</el-button>
  <el-dropdown trigger="click">
    <el-button link type="info" aria-label="更多角色操作">更多</el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item @click="emit('assign-menu', role)">分配权限</el-dropdown-item>
        <el-dropdown-item @click="emit('users', role)">用户</el-dropdown-item>
        <el-dropdown-item
          divided
          :disabled="role.roleCode === 'super_admin'"
          @click="emit('delete', role)"
        >
          删除角色
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
defineProps<{ role: Record<string, unknown> }>()
const emit = defineEmits(['edit', 'assign-menu', 'users', 'delete'])
</script>
