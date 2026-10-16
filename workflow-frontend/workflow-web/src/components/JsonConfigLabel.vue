<template>
  <span class="json-config-label">
    <span>{{ label }}</span>
    <el-tooltip
      v-if="resolvedHelp"
      placement="top"
      :show-after="200"
      :hide-after="50"
    >
      <template #content>
        <div class="json-config-help-card">
          <div class="json-config-help-card__title">
            {{ resolvedHelp.title }}
          </div>
          <p>{{ resolvedHelp.summary }}</p>
          <p class="json-config-help-card__shape">
            格式：{{ shapeLabel }}
          </p>
          <pre>{{ exampleText }}</pre>
          <p v-if="resolvedHelp.result">
            结果：{{ resolvedHelp.result }}
          </p>
          <ul v-if="resolvedHelp.notes?.length">
            <li v-for="note in resolvedHelp.notes" :key="note">
              {{ note }}
            </li>
          </ul>
        </div>
      </template>
      <el-icon
        class="json-config-label__icon"
        tabindex="0"
        :aria-label="`查看${label}配置说明`"
      >
        <QuestionFilled />
      </el-icon>
    </el-tooltip>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import {
  getJsonConfigHelp,
  getJsonShapeLabel
} from '@/shared/json-config-help'

const props = defineProps({
  label: { type: String, required: true },
  helpKey: { type: String, default: '' },
  help: { type: Object, default: null }
})

const resolvedHelp = computed(() =>
  getJsonConfigHelp(props.helpKey, props.help)
)
const shapeLabel = computed(() =>
  getJsonShapeLabel(resolvedHelp.value?.shape)
)
const exampleText = computed(() =>
  JSON.stringify(resolvedHelp.value?.example ?? {}, null, 2)
)
</script>

<style scoped>
.json-config-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}

.json-config-label__icon {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
  cursor: help;
  font-size: 15px;
  outline: none;
  transition: color 0.2s ease;
}

.json-config-label__icon:hover,
.json-config-label__icon:focus-visible {
  color: var(--el-color-primary);
}

.json-config-help-card {
  width: min(390px, 72vw);
  max-height: 420px;
  overflow: auto;
  color: #fff;
  font-size: 12px;
  line-height: 1.55;
}

.json-config-help-card__title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
}

.json-config-help-card p {
  margin: 6px 0;
}

.json-config-help-card__shape {
  opacity: 0.82;
}

.json-config-help-card pre {
  margin: 8px 0;
  padding: 8px 10px;
  overflow: auto;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 4px;
  background: rgb(0 0 0 / 22%);
  color: #fff;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.json-config-help-card ul {
  margin: 6px 0 0;
  padding-left: 18px;
}
</style>

