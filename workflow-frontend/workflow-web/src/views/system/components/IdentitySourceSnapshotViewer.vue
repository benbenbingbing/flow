<template>
  <div v-if="canRead && targetId" class="source-snapshot">
    <el-button link type="primary" @click="open">来源原始数据</el-button>
    <el-dialog v-model="visible" title="来源原始数据" width="min(900px, 94vw)" append-to-body destroy-on-close>
      <div v-loading="loading">
        <el-alert v-if="error" :title="error" type="error" :closable="false">
          <el-button link type="primary" @click="load">重新加载</el-button>
        </el-alert>
        <template v-else-if="snapshot">
          <el-alert v-if="snapshot.state === 'UNAVAILABLE'" title="尚未留存原始数据，后续同步提供原始记录后可查看。" type="info" :closable="false" />
          <el-alert v-else-if="snapshot.state === 'STALE'" title="此原文已过期：当前业务资料更新时未提供原文，以下内容对应上一次留存。" type="warning" :closable="false" />
          <el-tag v-else type="success">对应当前业务资料</el-tag>
          <el-descriptions :column="2" border class="snapshot-metadata">
            <el-descriptions-item label="来源">{{ snapshot.sourceCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="外部 ID">{{ snapshot.externalId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前来源版本">{{ snapshot.currentSourceVersion ?? '无版本' }}</el-descriptions-item>
            <el-descriptions-item label="留存来源版本">{{ snapshot.recordedSourceVersion ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="留存时间">{{ snapshot.recordedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="留存操作人">{{ snapshot.recordedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="UTF-8 字节数">{{ snapshot.rawBytes ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="SHA-256"><span class="snapshot-hash">{{ snapshot.rawHash || '-' }}</span></el-descriptions-item>
          </el-descriptions>
          <!-- 直接按文本显示，保留原始数字、空白和转义；不解析大整数，也不执行原文中的 HTML。 -->
          <pre v-if="snapshot.rawDataJson != null" class="snapshot-json">{{ snapshot.rawDataJson }}</pre>
        </template>
      </div>
      <template #footer><el-button @click="visible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'

const props = defineProps({ targetType: { type: String, required: true }, targetId: { type: String, default: '' } })
const userStore = useUserStore()
const canRead = computed(() => {
  const prefix = props.targetType === 'USER' ? 'system:user:' : 'system:organization:'
  return userStore.isSuperAdmin || userStore.permissions.includes('*')
    || ['view', 'manage'].every(action => userStore.permissions.includes(prefix + action))
})
const visible = ref(false)
const loading = ref(false)
const error = ref('')
const snapshot = ref(null)
let generation = 0

/** 只在主动展开时请求大文本；关闭、切换对象或丢失权限后清理内容并丢弃在途响应。 */
function clear() {
  generation++
  snapshot.value = null
  error.value = ''
  loading.value = false
}
async function open() {
  visible.value = true
  await load()
}
async function load() {
  if (!canRead.value || !props.targetId) return
  const current = ++generation
  loading.value = true
  error.value = ''
  snapshot.value = null
  try {
    const result = await request.get('/system/identity/source-snapshots/' + props.targetType + '/' + encodeURIComponent(props.targetId))
    if (current === generation) snapshot.value = result
  } catch {
    if (current === generation) error.value = '原始数据加载失败，请重试。'
  } finally {
    if (current === generation) loading.value = false
  }
}
watch([() => props.targetType, () => props.targetId, canRead], () => { visible.value = false; clear() })
watch(visible, value => { if (!value) clear() })
onBeforeUnmount(clear)
</script>

<style scoped>
.source-snapshot { margin: 12px 0; }
.snapshot-metadata { margin: 16px 0; }
.snapshot-hash { overflow-wrap: anywhere; }
.snapshot-json { max-height: 55vh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 16px; background: var(--el-fill-color-light); font-family: monospace; }
</style>
