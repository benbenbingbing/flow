<template>
  <el-dialog
    v-model="visible"
    :title="`最终代码 · ${configLabel || configType}`"
    width="94vw"
    top="3vh"
    append-to-body
    destroy-on-close
    class="runtime-code-dialog"
  >
    <div class="runtime-code-viewer">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这里按真实草稿或发布快照生成等价 Vue 单文件组件，直接展开模板、数据、查询和事件处理，便于阅读；平台不会落地或执行此文件，实际运行仍以激活发布快照为准。"
      />

      <div class="runtime-code-toolbar">
        <div class="runtime-code-source">
          <el-radio-group v-model="activeSource">
            <el-radio-button value="DRAFT">
              当前草稿
            </el-radio-button>
            <el-radio-button
              value="PUBLISHED"
              :disabled="!publishedArtifact"
            >
              激活版本{{ publishedArtifact?.version == null
                ? ''
                : ` v${publishedArtifact.version}` }}
            </el-radio-button>
          </el-radio-group>
          <el-tag
            v-if="activeSource === 'DRAFT' && draftDirty"
            type="warning"
            effect="plain"
          >
            包含未保存修改
          </el-tag>
          <el-tag
            v-else-if="activeSource === 'DRAFT' && draftChanged"
            type="warning"
            effect="plain"
          >
            包含未发布修改
          </el-tag>
          <el-tag
            v-else-if="activeSource === 'PUBLISHED'"
            type="success"
            effect="plain"
          >
            当前实际运行
          </el-tag>
        </div>

        <div class="runtime-code-actions">
          <el-button
            :icon="CopyDocument"
            @click="copyCurrent"
          >
            复制
          </el-button>
          <el-button
            :icon="Download"
            @click="downloadCurrent"
          >
            下载
          </el-button>
        </div>
      </div>

      <div class="runtime-code-stats">
        <div
          v-for="item in currentArtifact.stats"
          :key="item.label"
          class="runtime-code-stat"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>

      <el-tabs
        v-model="activeTab"
        type="border-card"
        class="runtime-code-tabs"
      >
        <el-tab-pane label="等价 Vue SFC" name="code">
          <codemirror
            :model-value="currentArtifact.code"
            :extensions="codeExtensions"
            :style="editorStyle"
          />
        </el-tab-pane>
        <el-tab-pane label="完整运行态 JSON" name="json">
          <codemirror
            :model-value="currentArtifact.json"
            :extensions="jsonExtensions"
            :style="editorStyle"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="`逻辑索引 (${currentArtifact.logicItems.length})`"
          name="logic"
        >
          <el-table
            :data="currentArtifact.logicItems"
            border
            size="small"
            height="calc(76vh - 190px)"
          >
            <el-table-column
              prop="category"
              label="类型"
              width="100"
            />
            <el-table-column
              prop="name"
              label="对象"
              min-width="180"
            />
            <el-table-column
              prop="path"
              label="代码路径"
              min-width="260"
            >
              <template #default="{ row }">
                <code>{{ row.path }}</code>
              </template>
            </el-table-column>
            <el-table-column
              prop="summary"
              label="逻辑摘要"
              min-width="320"
              show-overflow-tooltip
            />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Download } from '@element-plus/icons-vue'
import { Codemirror } from 'vue-codemirror'
import { javascript } from '@codemirror/lang-javascript'
import { vue } from '@codemirror/lang-vue'
import { oneDark } from '@codemirror/theme-one-dark'
import { EditorState } from '@codemirror/state'
import { EditorView } from '@codemirror/view'

const visible = ref(false)
const configType = ref('')
const configLabel = ref('')
const draftArtifact = ref(emptyArtifact())
const publishedArtifact = ref(null)
const draftDirty = ref(false)
const draftChanged = ref(false)
const activeSource = ref('DRAFT')
const activeTab = ref('code')

const readonlyExtensions = [
  oneDark,
  EditorState.readOnly.of(true),
  EditorView.editable.of(false),
  EditorView.lineWrapping
]
const codeExtensions = [vue(), ...readonlyExtensions]
const jsonExtensions = [javascript(), ...readonlyExtensions]
const editorStyle = {
  height: 'calc(76vh - 190px)',
  fontSize: '13px'
}

const currentArtifact = computed(() =>
  activeSource.value === 'PUBLISHED' && publishedArtifact.value
    ? publishedArtifact.value
    : draftArtifact.value
)

function open({
  type,
  label,
  draft,
  published = null,
  dirty = false,
  changed = false
} = {}) {
  configType.value = type || ''
  configLabel.value = label || ''
  draftArtifact.value = draft || emptyArtifact()
  publishedArtifact.value = published
  draftDirty.value = dirty === true
  draftChanged.value = changed === true
  activeSource.value = 'DRAFT'
  activeTab.value = 'code'
  visible.value = true
}

async function copyCurrent() {
  const text = currentText()
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(
      activeTab.value === 'code'
        ? 'Vue 代码已复制'
        : '配置内容已复制'
    )
  } catch {
    ElMessage.error('复制失败，请使用编辑器中的全选复制')
  }
}

function downloadCurrent() {
  const text = currentText()
  const extension = activeTab.value === 'code' ? 'vue' : 'json'
  const source = activeSource.value === 'PUBLISHED'
    ? `published-v${currentArtifact.value.version || 'active'}`
    : 'draft'
  const filename = sanitizeFilename(
    `${configLabel.value || configType.value}-${source}.${extension}`
  )
  const url = URL.createObjectURL(new Blob(
    [text],
    { type: 'text/plain;charset=utf-8' }
  ))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function currentText() {
  if (activeTab.value === 'code') {
    return currentArtifact.value.code
  }
  if (activeTab.value === 'logic') {
    return JSON.stringify(currentArtifact.value.logicItems, null, 2)
  }
  return currentArtifact.value.json
}

function sanitizeFilename(value) {
  return String(value || 'runtime-config.vue')
    .replace(/[\\/:*?"<>|]+/g, '-')
}

function emptyArtifact() {
  return {
    version: null,
    code: '',
    json: '{}',
    logicItems: [],
    stats: []
  }
}

defineExpose({ open })
</script>

<style scoped>
.runtime-code-viewer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.runtime-code-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.runtime-code-source,
.runtime-code-actions,
.runtime-code-stats {
  display: flex;
  align-items: center;
  gap: 10px;
}

.runtime-code-stats {
  flex-wrap: wrap;
}

.runtime-code-stat {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 96px;
  padding: 7px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.runtime-code-stat span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.runtime-code-stat strong {
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.runtime-code-tabs {
  min-width: 0;
}

.runtime-code-tabs :deep(.el-tabs__content) {
  padding: 0;
}

.runtime-code-tabs :deep(.cm-editor) {
  height: 100%;
}

.runtime-code-tabs code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

@media (max-width: 800px) {
  .runtime-code-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .runtime-code-actions {
    justify-content: flex-end;
  }
}
</style>
