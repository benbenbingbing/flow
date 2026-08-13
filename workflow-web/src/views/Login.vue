<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <el-icon size="48" color="#409EFF">
          <Connection />
        </el-icon>
        <h1 class="title">流程配置系统</h1>
        <p class="subtitle">Workflow Configuration System</p>
      </div>
      
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            clearable
            @keyup.enter="focusPassword"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            ref="passwordInputRef"
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            :disabled="!hasCredentials"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Connection } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { login, getPermissions } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const passwordInputRef = ref(null)
const loading = ref(false)
const loginForm = reactive({
  username: '',
  password: ''
})
const hasCredentials = computed(() => (
  loginForm.username.trim().length > 0
  && loginForm.password.length > 0
))

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

function focusPassword() {
  passwordInputRef.value?.focus()
}

async function handleLogin() {
  if (
    !loginFormRef.value
    || !hasCredentials.value
    || loading.value
  ) {
    return
  }
  
  try {
    await loginFormRef.value.validate()
    
    loading.value = true
    const res = await login(loginForm)
    
    // 保存登录信息
    userStore.applySession(res)
    
    if (res.passwordResetRequired) {
      userStore.setPermissions([])
    } else {
      try {
        const perms = await getPermissions()
        userStore.setPermissions(perms || [])
      } catch (e) {
        console.error('加载权限失败:', e)
        userStore.setPermissions([])
      }
    }
    
    ElMessage.success('登录成功')
    
    router.push(res.passwordResetRequired ? '/change-password' : '/')
  } catch (error) {
    console.error('登录失败:', error)
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
  background: #eef1f4;
}

.login-box {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.title {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin-top: 16px;
  margin-bottom: 8px;
}

.subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.login-form {
  margin-top: 20px;
}

.login-btn {
  width: 100%;
}

:deep(.el-input__inner) {
  height: 44px;
}

@media (max-width: 520px) {
  .login-container {
    align-items: flex-start;
    padding: 0;
    background: #fff;
  }

  .login-box {
    width: 100%;
    min-height: 100vh;
    padding: 48px 20px;
    box-shadow: none;
  }
}
</style>
