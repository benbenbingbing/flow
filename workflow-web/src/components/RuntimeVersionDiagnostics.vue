<template>
  <div class="runtime-version-diagnostics">
    <div
      class="runtime-version-diagnostics__trigger"
      @click="handleTriggerClick"
    >
      <slot />
    </div>
    <transition name="runtime-version-diagnostics">
      <div
        v-if="visible && normalizedEntries.length > 0"
        class="runtime-version-diagnostics__panel"
        role="status"
        aria-live="polite"
        @click.stop
      >
        <div class="runtime-version-diagnostics__entries">
          <span
            v-for="entry in normalizedEntries"
            :key="`${entry.label}:${entry.value}`"
            class="runtime-version-diagnostics__entry"
          >
            <span class="runtime-version-diagnostics__label">{{ entry.label }}</span>
            <code
              class="runtime-version-diagnostics__value"
              :title="entry.value"
            >{{ entry.value }}</code>
          </span>
        </div>
        <el-button
          v-if="copyText"
          class="runtime-version-diagnostics__copy"
          link
          size="small"
          @click="copyDiagnostics"
        >
          复制排障信息
        </el-button>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  buildRuntimeDiagnosticText,
  createRuntimeDiagnosticTrigger,
  normalizeRuntimeDiagnosticEntries
} from '@/shared/runtime-diagnostics'

const AUTO_HIDE_MS = 10 * 60 * 1000

const props = defineProps({
  entries: { type: Array, default: () => [] },
  copyEntries: { type: Array, default: null },
  copyTitle: { type: String, default: '运行版本排障信息' },
  resetKey: { type: [String, Number], default: '' }
})

const visible = ref(false)
const trigger = createRuntimeDiagnosticTrigger()
let autoHideTimer = null

const normalizedEntries = computed(() =>
  normalizeRuntimeDiagnosticEntries(props.entries)
)
const copyText = computed(() => buildRuntimeDiagnosticText(
  props.copyEntries || props.entries,
  props.copyTitle
))

function clearAutoHideTimer() {
  if (autoHideTimer == null) return
  globalThis.clearTimeout(autoHideTimer)
  autoHideTimer = null
}

function resetDiagnostics() {
  clearAutoHideTimer()
  trigger.reset()
  visible.value = false
}

/**
 * 诊断信息仅在本组件生命周期内短暂显示；三击再次关闭，长时间停留也会自动收起。
 */
function handleTriggerClick() {
  const result = trigger.recordClick()
  if (!result.toggled) return
  clearAutoHideTimer()
  visible.value = result.visible
  if (visible.value) {
    autoHideTimer = globalThis.setTimeout(resetDiagnostics, AUTO_HIDE_MS)
  }
}

async function copyDiagnostics() {
  try {
    const clipboard = globalThis.navigator?.clipboard
    if (clipboard?.writeText) {
      await clipboard.writeText(copyText.value)
    } else {
      const document = globalThis.document
      if (!document?.body || typeof document.execCommand !== 'function') {
        throw new Error('当前浏览器不支持剪贴板写入')
      }
      const textarea = document.createElement('textarea')
      textarea.value = copyText.value
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      let copied = false
      try {
        textarea.select()
        copied = document.execCommand('copy')
      } finally {
        textarea.remove()
      }
      if (!copied) throw new Error('浏览器拒绝剪贴板写入')
    }
    ElMessage.success('排障信息已复制')
  } catch (error) {
    console.error('复制排障信息失败:', error)
    ElMessage.error('复制失败，请重试')
  }
}

watch(() => props.resetKey, resetDiagnostics)
onBeforeUnmount(resetDiagnostics)

defineExpose({ reset: resetDiagnostics })
</script>

<style scoped>
.runtime-version-diagnostics {
  min-width: 0;
}

.runtime-version-diagnostics__trigger {
  width: fit-content;
  max-width: 100%;
  user-select: none;
}

.runtime-version-diagnostics__panel {
  display: flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  max-width: 100%;
  margin-top: 6px;
  padding: 5px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
}

.runtime-version-diagnostics__entries {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.runtime-version-diagnostics__entry {
  display: inline-flex;
  min-width: 0;
  align-items: baseline;
  gap: 4px;
}

.runtime-version-diagnostics__label::after {
  content: ':';
}

.runtime-version-diagnostics__value {
  display: inline-block;
  max-width: min(360px, 48vw);
  overflow: hidden;
  color: var(--el-text-color-regular);
  font-family: var(--el-font-family);
  font-size: inherit;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-version-diagnostics__copy {
  flex: 0 0 auto;
  padding: 0;
  font-size: 12px;
}

.runtime-version-diagnostics-enter-active,
.runtime-version-diagnostics-leave-active {
  transition: opacity 0.16s ease;
}

.runtime-version-diagnostics-enter-from,
.runtime-version-diagnostics-leave-to {
  opacity: 0;
}

@media (max-width: 760px) {
  .runtime-version-diagnostics__panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .runtime-version-diagnostics__entries {
    flex-direction: column;
  }
}
</style>
