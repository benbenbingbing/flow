<template>
  <main class="change-password-page">
    <section class="change-password-panel" aria-labelledby="change-password-title">
      <header>
        <el-icon size="38" color="#409eff"><Lock /></el-icon>
        <div>
          <h1 id="change-password-title">修改登录密码</h1>
          <p v-if="mustChangePassword">临时密码仅用于首次登录，请设置一个只有你知道的新密码。</p>
          <p v-else>更新当前账号的登录密码。</p>
        </div>
      </header>

      <el-alert
        v-if="mustChangePassword"
        title="完成改密后才能进入系统"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="submit"
      >
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input
            v-model="form.currentPassword"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
          <div class="password-help">10 至 72 位，必须同时包含大写字母、小写字母和数字。</div>
        </el-form-item>
        <el-form-item label="再次输入新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <div class="actions">
          <el-button @click="signOut">退出登录</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">保存新密码并继续</el-button>
        </div>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { changePassword, logout } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const submitting = ref(false)
const mustChangePassword = computed(() => Boolean(userStore.userInfo?.passwordResetRequired))
const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const validateConfirmation = (_rule, value, callback) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}
const rules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 10, max: 72, message: '新密码长度必须为10到72位', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/,
      message: '新密码必须同时包含大写字母、小写字母和数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmation, trigger: 'blur' }
  ]
}

const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    await changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    userStore.setUserInfo({
      ...(userStore.userInfo || {}),
      passwordResetRequired: false
    })
    ElMessage.success('密码已更新')
    await router.replace('/home')
  } finally {
    submitting.value = false
  }
}

const signOut = async () => {
  try {
    await logout()
  } catch {
    // 本地退出不依赖服务端响应。
  }
  userStore.logout()
  await router.replace('/login')
}
</script>

<style scoped>
.change-password-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f3f5f7;
}

.change-password-panel {
  width: min(100%, 480px);
  padding: 32px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgb(31 45 61 / 10%);
}

header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

h1 {
  margin: 0 0 6px;
  color: #303133;
  font-size: 24px;
}

p,
.password-help {
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.el-alert {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 560px) {
  .change-password-page {
    align-items: start;
    padding: 0;
    background: #fff;
  }

  .change-password-panel {
    min-height: 100vh;
    padding: 28px 20px;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .actions {
    flex-direction: column-reverse;
  }

  .actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
