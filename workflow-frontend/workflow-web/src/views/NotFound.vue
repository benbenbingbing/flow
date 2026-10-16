<template>
  <main class="not-found">
    <el-result
      icon="warning"
      title="页面不存在"
      sub-title="访问地址可能已失效，或该功能已被迁移。"
    >
      <template #extra>
        <el-button type="primary" @click="router.replace('/home')">返回首页</el-button>
        <el-button @click="router.back()">返回上一页</el-button>
      </template>
    </el-result>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f5f7fa;
}
</style>
