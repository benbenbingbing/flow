<template>
  <main class="mobile-shell">
    <RouterView v-slot="{ Component, route }">
      <KeepAlive include="MobileInbox"><component :is="Component" :key="route.name === 'detail' ? route.fullPath : route.name" /></KeepAlive>
    </RouterView>
  </main>
</template>
