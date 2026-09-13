<template>
  <main class="qa-page">
    <header class="qa-heading">
      <h1>表单按钮外观验收</h1>
      <p>使用正式配置面板和正式运行时按钮组件验证四种互斥外观。</p>
    </header>

    <section class="qa-card qa-preview" aria-label="运行态预览">
      <div>
        <h2>运行态预览</h2>
        <p>当前外观：<strong data-testid="appearance-value">{{ currentAppearance }}</strong></p>
      </div>
      <FormActionBar :actions="runtimeActions" />
    </section>

    <section class="qa-card" aria-label="按钮配置">
      <FormButtonConfigPanel
        v-model="actionBar"
        entity-code=""
        form-id=""
        :nodes="qaNodes"
        :persisted-button-keys="['qa_action']"
      />
    </section>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import FormActionBar from '@/components/FormActionBar.vue'
import FormButtonConfigPanel from '@/components/FormButtonConfigPanel.vue'
import { normalizeFormActionBar } from '@/shared/form-actions'

const qaNodes = [{
  nodeType: 'ACTION_SLOT',
  nodeKey: 'qa_slot',
  label: '报告操作区'
}]

const actionBar = ref({
  version: 1,
  builtInOverrides: {},
  customButtons: [{
    key: 'qa_action',
    type: 'custom',
    label: '生成报告',
    icon: 'Document',
    buttonType: 'primary',
    buttonAppearance: 'DEFAULT',
    sort: 50,
    enabled: true,
    modes: ['create'],
    placement: 'FOOTER',
    slotKey: '',
    perm: 'entity:qa:generate',
    availabilityRule: null,
    confirm: { enabled: false, message: '' },
    validateBeforeExecute: false
  }]
})

const runtimeActions = computed(() =>
  normalizeFormActionBar(actionBar.value).customButtons.map(button => ({
    ...button,
    runtimeKey: `qa:${button.key}`,
    visible: true,
    enabled: true,
    reason: ''
  }))
)
const currentAppearance = computed(() =>
  runtimeActions.value[0]?.buttonAppearance || 'DEFAULT'
)
</script>

<style scoped>
.qa-page {
  box-sizing: border-box;
  min-height: 100vh;
  padding: 28px;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
  font-family: var(--el-font-family);
}

.qa-heading,
.qa-card {
  max-width: 1180px;
  margin: 0 auto;
}

.qa-heading {
  margin-bottom: 18px;
}

.qa-heading h1,
.qa-card h2,
.qa-heading p,
.qa-card p {
  margin: 0;
}

.qa-heading h1 {
  font-size: 24px;
  line-height: 34px;
}

.qa-heading p,
.qa-card p {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.qa-card {
  box-sizing: border-box;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.qa-card + .qa-card {
  margin-top: 18px;
}

.qa-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.qa-card h2 {
  font-size: 16px;
  line-height: 24px;
}
</style>
