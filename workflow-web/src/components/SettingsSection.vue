<template>
  <section
    class="settings-section"
    :class="{
      'is-expanded': expanded,
      'is-collapsible': collapsible,
      'is-primary': primary
    }"
  >
    <button
      v-if="collapsible"
      type="button"
      class="settings-section__header"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <span class="settings-section__heading">
        <strong>{{ title }}</strong>
        <small v-if="description">{{ description }}</small>
      </span>
      <span class="settings-section__summary">
        <slot name="summary" />
        <el-icon class="settings-section__arrow"><ArrowDown /></el-icon>
      </span>
    </button>

    <div v-else class="settings-section__header settings-section__header--static">
      <span class="settings-section__heading">
        <strong>{{ title }}</strong>
        <small v-if="description">{{ description }}</small>
      </span>
      <slot name="summary" />
    </div>

    <div v-show="expanded" class="settings-section__body">
      <slot />
    </div>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  defaultExpanded: {
    type: Boolean,
    default: false
  },
  collapsible: {
    type: Boolean,
    default: true
  },
  primary: {
    type: Boolean,
    default: false
  }
})

const expanded = ref(!props.collapsible || props.defaultExpanded)

watch(
  () => [props.collapsible, props.defaultExpanded],
  ([collapsible, defaultExpanded]) => {
    expanded.value = !collapsible || defaultExpanded
  }
)
</script>

<style scoped>
.settings-section {
  margin-bottom: 12px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.settings-section.is-primary {
  border-color: #d9ecff;
  background: linear-gradient(180deg, #f7fbff 0%, #fff 72px);
}

.settings-section__header {
  display: flex;
  width: 100%;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 0;
  background: #f8f9fb;
  color: #303133;
  text-align: left;
}

button.settings-section__header {
  cursor: pointer;
}

button.settings-section__header:hover {
  background: #f2f6fc;
}

button.settings-section__header:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: -2px;
}

.settings-section.is-primary .settings-section__header {
  background: transparent;
}

.settings-section__header--static {
  cursor: default;
}

.settings-section__heading {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.settings-section__heading strong {
  font-size: 14px;
  line-height: 20px;
}

.settings-section__heading small {
  overflow: hidden;
  color: #909399;
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-section__summary {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 12px;
}

.settings-section__arrow {
  transition: transform 0.2s ease;
}

.settings-section.is-expanded .settings-section__arrow {
  transform: rotate(180deg);
}

.settings-section__body {
  padding: 14px 12px 4px;
  border-top: 1px solid #ebeef5;
}

.settings-section.is-primary .settings-section__body {
  border-top-color: #d9ecff;
}
</style>
