<template>
  <el-alert
    title="创建后会生成一次性临时密码；请通过安全渠道交给用户，用户首次登录必须修改。"
    type="info"
    :closable="false"
    show-icon
    class="temporary-password-notice"
  />
</template>

<style scoped>
.temporary-password-notice {
  margin-bottom: 18px;
}
</style>
