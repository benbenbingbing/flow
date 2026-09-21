import { createRouter, createWebHistory } from 'vue-router'
import { loadIdentity, session, transport } from './adapters/services.js'

export const router = createRouter({
  history: createWebHistory('/m/'),
  routes: [
    { path: '/', redirect: '/inbox/todo' },
    { path: '/login', name: 'login', component: () => import('./pages/Login.vue') },
    { path: '/inbox/:kind(todo|done|started|cc)', name: 'inbox', component: () => import('./pages/Inbox.vue') },
    { path: '/process/:instanceId', name: 'detail', component: () => import('./pages/ProcessDetail.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/inbox/todo' }
  ],
  scrollBehavior(to, from, savedPosition) { return savedPosition || { top: 0 } }
})
let initialized = false
router.beforeEach(async to => {
  if (!initialized) {
    await transport.restoreAuthSession()
    initialized = true
    if (session.token && !session.userInfo?.passwordResetRequired) {
      try { await loadIdentity() } catch { session.clearAuth() }
    }
  }
  if ((!session.token || session.userInfo?.passwordResetRequired) && to.name !== 'login') return { name: 'login' }
  if (session.token && !session.userInfo?.passwordResetRequired && to.name === 'login') return '/inbox/todo'
})
