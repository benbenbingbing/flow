<template>
  <UserManualPage :manual="quickStartManual" :show-header="false" />
</template>

<script setup>
import UserManualPage from '@/components/user-manual/UserManualPage.vue'
import quickStartManual from '@/data/user-manual/quickStart'
</script>
