<template>
  <UserManualPage :manual="entityManual" />
</template>

<script setup>
import UserManualPage from '@/components/user-manual/UserManualPage.vue'
import entityManual from '@/data/user-manual/entity'
</script>
