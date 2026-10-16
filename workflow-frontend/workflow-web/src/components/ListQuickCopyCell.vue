<template>
  <div class="list-quick-copy-cell" :class="{ 'is-enabled': enabled }">
    <div ref="contentRef" class="list-quick-copy-cell__content">
      <slot />
    </div>
    <el-tooltip v-if="enabled" content="复制当前单元格" placement="top" :show-after="300">
      <el-button
        class="list-quick-copy-cell__button"
        type="primary"
        link
        :icon="CopyDocument"
        :aria-label="`复制${fieldName || '当前单元格'}`"
        @click.stop="copyCurrentCell"
      />
    </el-tooltip>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import { normalizeClipboardText, writeClipboardText } from '@/shared/clipboard'

const props = defineProps({
  enabled: { type: Boolean, default: false },
  value: { default: '' },
  fieldName: { type: String, default: '' }
})

const contentRef = ref(null)

/**
 * 复制用户实际看到的单元格文本；无可见文本时再使用字段展示值兜底。
 * 这样状态标签、自定义渲染组件等内容不会退化为原始编码。
 */
async function copyCurrentCell() {
  const renderedText = contentRef.value?.innerText?.trim()
  const text = renderedText || normalizeClipboardText(props.value)
  try {
    await writeClipboardText(text)
    ElMessage.success(`${props.fieldName || '当前单元格'}已复制`)
  } catch (error) {
    console.error('复制单元格内容失败:', error)
    ElMessage.error('复制失败，请重试')
  }
}
</script>

<style scoped>
.list-quick-copy-cell {
  min-width: 0;
}

.list-quick-copy-cell.is-enabled {
  display: flex;
  align-items: center;
  gap: 6px;
}

.list-quick-copy-cell__content {
  min-width: 0;
}

.is-enabled .list-quick-copy-cell__content {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-quick-copy-cell__button {
  flex: 0 0 auto;
  padding: 2px;
}
</style>
