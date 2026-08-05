<template>
  <UserManualPage :manual="interfaceServiceManual" />
</template>

<script setup>
import UserManualPage from '@/components/user-manual/UserManualPage.vue'
import interfaceServiceManual from '@/data/user-manual/interfaceService'
</script>
