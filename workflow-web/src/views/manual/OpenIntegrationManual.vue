<template>
  <UserManualPage :manual="openIntegrationManual" />
</template>

<script setup>
import UserManualPage from '@/components/user-manual/UserManualPage.vue'
import openIntegrationManual from '@/data/user-manual/openIntegration'
</script>
