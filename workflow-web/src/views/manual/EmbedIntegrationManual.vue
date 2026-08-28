<template>
  <UserManualPage :manual="embedIntegrationManual" />
</template>

<script setup>
import UserManualPage from '@/components/user-manual/UserManualPage.vue'
import embedIntegrationManual from '@/data/user-manual/embedIntegration'
</script>
