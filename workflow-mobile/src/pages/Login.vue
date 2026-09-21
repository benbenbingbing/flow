<template>
  <section class="mobile-login">
    <div class="login-mark" aria-hidden="true">F</div>
    <h1>登录工作台</h1><p class="mobile-muted">随时处理待办，跟进你的流程</p>
    <template v-if="session.userInfo?.passwordResetRequired">
      <div class="mobile-error" role="alert">请先到电脑端修改密码后重新登录。</div>
      <VanButton block type="primary" :url="pcUrl">前往电脑端</VanButton>
      <VanButton block plain @click="leave">退出登录</VanButton>
    </template>
    <VanForm v-else @submit="submit">
      <VanField v-model="username" label="用户名" name="username" autocomplete="username" placeholder="请输入用户名" :rules="[{ required: true, message: '请输入用户名' }]" />
      <VanField v-model="password" label="密码" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" :rules="[{ required: true, message: '请输入密码' }]" />
      <p v-if="error" class="login-error" role="alert">{{ error }}</p>
      <VanButton block type="primary" native-type="submit" :loading="loading" class="login-submit">登录</VanButton>
    </VanForm>
    <footer>待办 · 已办 · 我发起的 · 知会</footer>
  </section>
</template>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Form as VanForm, Field as VanField, Button as VanButton } from 'vant'
import { login, logout, session } from '../adapters/services.js'
const router = useRouter()
const username = ref(''), password = ref(''), loading = ref(false), error = ref('')
const pcUrl = import.meta.env.VITE_PC_URL || (import.meta.env.DEV ? `${location.protocol}//${location.hostname}:${import.meta.env.VITE_WEB_PORT || 3000}/` : '/')
async function submit() {
  if (loading.value) return
  loading.value = true; error.value = ''
  try { await login({ username: username.value.trim(), password: password.value }); password.value = ''; if (!session.userInfo?.passwordResetRequired) await router.replace('/inbox/todo') }
  catch (cause) { error.value = cause.message || '登录失败，请重试' }
  finally { loading.value = false }
}
async function leave() { await logout(); error.value = ''; password.value = '' }
</script>
<style scoped>
.mobile-login { padding: max(60px, 12vh) 24px 32px; min-height: 100dvh; }
.login-mark { width: 44px; height: 44px; display: grid; place-items: center; background: var(--flow-mobile-accent); color: var(--flow-mobile-on-accent); border-radius: 12px; font-size: 26px; font-weight: 700; }
h1 { font-size: 28px; margin: 28px 0 8px; } p { font-size: 14px; line-height: 1.6; }
:deep(.van-form) { margin-top: 36px; } :deep(.van-field) { margin: 12px 0; border: 1px solid var(--flow-mobile-border); border-radius: 12px; min-height: 54px; align-items: center; }
.login-submit { margin-top: 24px; height: 48px; border-radius: 12px; } .login-error { color: #b33f35; } footer { margin-top: 48px; text-align: center; color: var(--flow-mobile-muted); font-size: 12px; }
</style>
