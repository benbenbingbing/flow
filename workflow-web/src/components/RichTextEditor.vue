<template>
  <div class="rich-text-editor">
    <div class="toolbar">
      <!-- 字体样式 -->
      <el-button-group>
        <el-button size="small" @click="exec('bold')" :class="{ active: isActive('bold') }" title="粗体">
          <b>B</b>
        </el-button>
        <el-button size="small" @click="exec('italic')" :class="{ active: isActive('italic') }" title="斜体">
          <i>I</i>
        </el-button>
        <el-button size="small" @click="exec('underline')" :class="{ active: isActive('underline') }" title="下划线">
          <u>U</u>
        </el-button>
        <el-button size="small" @click="exec('strikeThrough')" :class="{ active: isActive('strikeThrough') }" title="删除线">
          <s>S</s>
        </el-button>
      </el-button-group>

      <el-divider direction="vertical" />

      <!-- 标题 -->
      <el-select v-model="headingValue" size="small" style="width: 80px" @change="setHeading" placeholder="正文">
        <el-option label="正文" value="p" />
        <el-option label="H1" value="h1" />
        <el-option label="H2" value="h2" />
        <el-option label="H3" value="h3" />
        <el-option label="H4" value="h4" />
      </el-select>

      <el-divider direction="vertical" />

      <!-- 字号 -->
      <el-select v-model="fontSizeValue" size="small" style="width: 70px" @change="setFontSize" placeholder="字号">
        <el-option label="12px" value="1" />
        <el-option label="14px" value="2" />
        <el-option label="16px" value="3" />
        <el-option label="18px" value="4" />
        <el-option label="20px" value="5" />
        <el-option label="24px" value="6" />
        <el-option label="32px" value="7" />
      </el-select>

      <el-divider direction="vertical" />

      <!-- 字体颜色 -->
      <el-color-picker v-model="color" size="small" show-alpha @change="setColor" title="字体颜色" />

      <!-- 背景色 -->
      <el-color-picker v-model="bgColor" size="small" show-alpha @change="setBgColor" title="背景色" />

      <el-divider direction="vertical" />

      <!-- 对齐 -->
      <el-button-group>
        <el-button size="small" @click="exec('justifyLeft')" :class="{ active: isActive('justifyLeft') }" title="左对齐">
          <el-icon><List /></el-icon>
        </el-button>
        <el-button size="small" @click="exec('justifyCenter')" :class="{ active: isActive('justifyCenter') }" title="居中">
          <el-icon><ScaleToOriginal /></el-icon>
        </el-button>
        <el-button size="small" @click="exec('justifyRight')" :class="{ active: isActive('justifyRight') }" title="右对齐">
          <el-icon><Right /></el-icon>
        </el-button>
      </el-button-group>

      <el-divider direction="vertical" />

      <!-- 列表 -->
      <el-button-group>
        <el-button size="small" @click="exec('insertUnorderedList')" :class="{ active: isActive('insertUnorderedList') }" title="无序列表">
          <el-icon><Menu /></el-icon>
        </el-button>
        <el-button size="small" @click="exec('insertOrderedList')" :class="{ active: isActive('insertOrderedList') }" title="有序列表">
          <el-icon><Sort /></el-icon>
        </el-button>
      </el-button-group>

      <el-divider direction="vertical" />

      <!-- 缩进 -->
      <el-button-group>
        <el-button size="small" @click="exec('outdent')" title="减少缩进">
          <el-icon><DArrowLeft /></el-icon>
        </el-button>
        <el-button size="small" @click="exec('indent')" title="增加缩进">
          <el-icon><DArrowRight /></el-icon>
        </el-button>
      </el-button-group>

      <el-divider direction="vertical" />

      <!-- 插入 -->
      <el-button-group>
        <el-button size="small" @click="insertLink" title="插入链接">
          <el-icon><Link /></el-icon>
        </el-button>
        <el-button size="small" @click="insertImage" title="插入图片">
          <el-icon><Picture /></el-icon>
        </el-button>
        <el-button size="small" @click="insertHorizontalRule" title="插入分割线">
          <el-icon><Minus /></el-icon>
        </el-button>
      </el-button-group>

      <el-divider direction="vertical" />

      <!-- 清除格式 -->
      <el-button size="small" @click="clearFormat" title="清除格式">
        <el-icon><Delete /></el-icon> 清除
      </el-button>

      <el-divider direction="vertical" />

      <!-- 撤销重做 -->
      <el-button-group>
        <el-button size="small" @click="exec('undo')" title="撤销">
          <el-icon><Back /></el-icon>
        </el-button>
        <el-button size="small" @click="exec('redo')" title="重做">
          <el-icon><Right /></el-icon>
        </el-button>
      </el-button-group>

      <el-divider direction="vertical" />

      <!-- 全屏 -->
      <el-button size="small" @click="toggleFullscreen" :type="isFullscreen ? 'primary' : ''" title="全屏编辑">
        <el-icon><FullScreen /></el-icon>
      </el-button>
    </div>

    <div
      ref="editorRef"
      class="editor-content"
      contenteditable="true"
      v-html="innerValue"
      @input="onInput"
      @blur="onBlur"
      @keydown="onKeydown"
      :style="{ minHeight: isFullscreen ? 'calc(100vh - 120px)' : height + 'px' }"
      :class="{ fullscreen: isFullscreen }"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import {
  List, ScaleToOriginal, Right, Menu, Sort,
  DArrowLeft, DArrowRight, Link, Picture, Minus,
  Delete, Back, FullScreen
} from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  height: {
    type: Number,
    default: 250
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const editorRef = ref(null)
const innerValue = ref(props.modelValue)
const color = ref('#000000')
const bgColor = ref('#ffffff')
const headingValue = ref('p')
const fontSizeValue = ref('3')
const isFullscreen = ref(false)

watch(() => props.modelValue, (val) => {
  if (val !== editorRef.value?.innerHTML) {
    innerValue.value = val
  }
})

const onInput = () => {
  const html = editorRef.value.innerHTML
  emit('update:modelValue', html)
  emit('change', html)
}

const onBlur = () => {
  const html = editorRef.value.innerHTML
  emit('update:modelValue', html)
}

const onKeydown = (e) => {
  // Tab 键插入 4 个空格
  if (e.key === 'Tab') {
    e.preventDefault()
    exec('insertText', '    ')
  }
}

const exec = (command, value = null) => {
  if (props.disabled) return
  document.execCommand(command, false, value)
  editorRef.value?.focus()
  onInput()
}

const isActive = (command) => {
  try {
    return document.queryCommandState(command)
  } catch (e) {
    return false
  }
}

const setHeading = (tag) => {
  exec('formatBlock', tag)
}

const setFontSize = (size) => {
  exec('fontSize', size)
}

const setColor = (val) => {
  exec('foreColor', val)
}

const setBgColor = (val) => {
  exec('hiliteColor', val)
}

const insertLink = () => {
  if (props.disabled) return
  const url = window.prompt('请输入链接地址:', 'https://')
  if (url) {
    exec('createLink', url)
  }
}

const insertImage = () => {
  if (props.disabled) return
  const url = window.prompt('请输入图片地址:', 'https://')
  if (url) {
    exec('insertImage', url)
  }
}

const insertHorizontalRule = () => {
  exec('insertHorizontalRule')
}

const clearFormat = () => {
  exec('removeFormat')
  exec('formatBlock', 'p')
}

const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
}

watch(() => props.disabled, (val) => {
  if (editorRef.value) {
    editorRef.value.contentEditable = val ? 'false' : 'true'
  }
}, { immediate: true })
</script>

<style scoped>
.rich-text-editor {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.toolbar {
  padding: 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.toolbar .el-button {
  padding: 5px 8px;
  font-size: 13px;
}

.toolbar .el-button.active {
  background-color: #409eff;
  color: #fff;
}

.toolbar .el-divider {
  margin: 0 4px;
}

.editor-content {
  padding: 12px;
  outline: none;
  line-height: 1.6;
  font-size: 14px;
  color: #606266;
  min-height: 250px;
  background: #fff;
  overflow-y: auto;
}

.editor-content:empty:before {
  content: attr(placeholder);
  color: #c0c4cc;
}

.editor-content:focus {
  border-color: #409eff;
}

.editor-content.fullscreen {
  position: fixed;
  top: 60px;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  min-height: calc(100vh - 60px);
  background: #fff;
}

/* 富文本内容样式 */
.editor-content :deep(h1) { font-size: 32px; font-weight: bold; margin: 16px 0; }
.editor-content :deep(h2) { font-size: 24px; font-weight: bold; margin: 14px 0; }
.editor-content :deep(h3) { font-size: 18px; font-weight: bold; margin: 12px 0; }
.editor-content :deep(h4) { font-size: 16px; font-weight: bold; margin: 10px 0; }
.editor-content :deep(p) { margin: 8px 0; }

.editor-content :deep(b),
.editor-content :deep(strong) {
  font-weight: bold;
}

.editor-content :deep(i),
.editor-content :deep(em) {
  font-style: italic;
}

.editor-content :deep(u) {
  text-decoration: underline;
}

.editor-content :deep(s),
.editor-content :deep(strike) {
  text-decoration: line-through;
}

.editor-content :deep(a) {
  color: #409eff;
  text-decoration: underline;
}

.editor-content :deep(ul) {
  list-style: disc;
  padding-left: 24px;
  margin: 8px 0;
}

.editor-content :deep(ol) {
  list-style: decimal;
  padding-left: 24px;
  margin: 8px 0;
}

.editor-content :deep(li) {
  margin: 4px 0;
}

.editor-content :deep(hr) {
  border: none;
  border-top: 1px solid #dcdfe6;
  margin: 16px 0;
}

.editor-content :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 8px 0;
}

.editor-content :deep(blockquote) {
  border-left: 4px solid #dcdfe6;
  padding-left: 12px;
  margin: 8px 0;
  color: #606266;
}

.editor-content :deep(pre) {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: monospace;
  font-size: 13px;
}

.editor-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.editor-content :deep(td),
.editor-content :deep(th) {
  border: 1px solid #dcdfe6;
  padding: 8px;
}

.editor-content :deep(th) {
  background: #f5f7fa;
  font-weight: bold;
}
</style>
