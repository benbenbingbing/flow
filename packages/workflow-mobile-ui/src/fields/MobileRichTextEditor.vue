<template>
  <div class="rich-field">
    <div class="rich-label">{{ label }}</div>
    <div class="rich-toolbar" role="toolbar" aria-label="文字格式"><button v-for="item in commands" :key="item.command" type="button" :aria-label="item.label" @mousedown.prevent @click="format(item.command)">{{ item.text }}</button></div>
    <div ref="editor" class="rich-content" contenteditable="true" role="textbox" aria-multiline="true" :aria-label="label" @input="change" @blur="$emit('blur')" @focus="$emit('focus')" @paste="paste" />
  </div>
</template>
<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
const props = defineProps({ modelValue: { type: String, default: '' }, label: String })
const emit = defineEmits(['update:modelValue', 'blur', 'focus'])
const editor = ref()
const commands = [{ command: 'bold', text: 'B', label: '加粗' }, { command: 'italic', text: 'I', label: '斜体' }, { command: 'insertUnorderedList', text: '• 列表', label: '项目列表' }, { command: 'removeFormat', text: '清除格式', label: '清除格式' }]
function sync() {
  const html = DOMPurify.sanitize(props.modelValue || '')
  if (editor.value && editor.value.innerHTML !== html) editor.value.innerHTML = html
}
function change() { emit('update:modelValue', DOMPurify.sanitize(editor.value?.innerHTML || '')) }
/** 浏览器原生编辑保留段落与选区；只执行固定格式命令，不把任意 HTML 当作脚本。 */
function format(command) { editor.value?.focus(); document.execCommand(command, false); change() }
function paste(event) {
  event.preventDefault()
  const html = event.clipboardData?.getData('text/html')
  if (html) document.execCommand('insertHTML', false, DOMPurify.sanitize(html))
  else document.execCommand('insertText', false, event.clipboardData?.getData('text/plain') || '')
  change()
}
onMounted(sync)
watch(() => props.modelValue, async () => { await nextTick(); sync() })
</script>
<style scoped>
.rich-field { padding: 14px 0; }.rich-label { color: var(--flow-mobile-muted); font-size: 14px; margin-bottom: 10px; }.rich-toolbar { display: flex; gap: 4px; background: var(--flow-mobile-inset); border-radius: 8px 8px 0 0; border: 1px solid var(--flow-mobile-border); }.rich-toolbar button { min-height: 44px; min-width: 44px; border: 0; background: transparent; color: var(--flow-mobile-accent-text); }.rich-content { min-height: 140px; max-height: 45dvh; overflow: auto; padding: 12px; border: 1px solid var(--flow-mobile-border); border-top: 0; border-radius: 0 0 8px 8px; line-height: 1.6; overflow-wrap: anywhere; }.rich-content :deep(img) { max-width: 100%; }
</style>
