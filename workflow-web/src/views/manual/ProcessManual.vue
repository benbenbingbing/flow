<template>
  <UserManualPage :manual="processManual" />
</template>

<script setup>
import UserManualPage from '@/components/user-manual/UserManualPage.vue'
import processManual from '@/data/user-manual/process'
</script>
