<template>
  <div class="global-settings">
    <div class="page-header">
      <div>
        <h2>全局设置</h2>
        <p>调整系统默认偏好。用户已保存的个人设置会继续优先生效。</p>
      </div>
      <el-button :loading="loading" @click="loadSettings">刷新</el-button>
    </div>
    <PageState v-if="loadError" type="error" title="全局设置加载失败" :description="loadError" retryable @retry="loadSettings" />
    <div v-else v-loading="loading" class="setting-list">
      <el-empty v-if="!loading && !settings.length" description="暂无可用设置" />
      <el-card v-for="item in settings" :key="item.settingKey" shadow="never">
        <div class="setting-heading">
          <h3>{{ item.name }}</h3>
          <el-tag size="small" type="info">{{ SETTING_VALUE_TYPE_LABELS[item.settingValueType] }}</el-tag>
          <el-tag size="small" :type="item.source === 'DEFAULT' ? 'info' : 'success'">
            {{ item.source === 'DEFAULT' ? '程序默认值' : '系统设置值' }}
          </el-tag>
        </div>
        <p class="setting-remark">{{ item.remark }}</p>
        <div class="setting-control">
          <el-switch
            v-if="item.settingValueType === 'BOOLEAN'"
            :model-value="item.value"
            :aria-label="item.name"
            :disabled="!canManage || Boolean(savingKey)"
            :loading="savingKey === item.settingKey"
            active-text="开启"
            inactive-text="关闭"
            @change="value => save(item, value)"
          />
          <template v-else>
            <el-input
              v-model="drafts[item.settingKey]"
              :type="item.settingValueType === 'JSON' ? 'textarea' : 'text'"
              :inputmode="item.settingValueType === 'NUMBER' ? 'decimal' : 'text'"
              :placeholder="item.settingValueType === 'JSON' ? '请输入 JSON 对象或数组' : item.settingValueType === 'NUMBER' ? '请输入数字' : '请输入字符串'"
              :rows="3"
              :aria-label="item.name"
              :disabled="!canManage || Boolean(savingKey)"
            />
            <el-button v-if="canManage" type="primary" :disabled="Boolean(savingKey)" @click="saveText(item)">保存</el-button>
          </template>
          <el-button v-if="canManage && item.override" :disabled="Boolean(savingKey)" @click="reset(item)">恢复程序默认值</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { useUserStore } from '@/stores/user'
import { listSystemSettings, saveSystemSetting, resetSystemSetting, settingVersion } from '@/api/system/settings'
import { SETTING_VALUE_TYPE_LABELS, serializeSettingInput, settingInputText } from '@/shared/setting-value'

const user = useUserStore()
const canManage = computed(() => user.isSuperAdmin || user.permissions.includes('*') || user.permissions.includes('system:setting:manage'))
const settings = ref([])
const drafts = ref({})
const loading = ref(false)
const loadError = ref('')
const savingKey = ref('')

/** 读取注册设置及当前系统值，缺少系统覆盖时显示程序默认值。 */
async function loadSettings() {
  if (loading.value || savingKey.value) return
  loading.value = true
  loadError.value = ''
  try {
    settings.value = await listSystemSettings()
    drafts.value = Object.fromEntries(settings.value.map(item => [item.settingKey, settingInputText(item)]))
  } catch (error) {
    loadError.value = error?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 按当前记录版本写入；发生冲突时重新读取，避免旧页面覆盖管理员的新设置。 */
async function mutate(item, operation) {
  if (!canManage.value || savingKey.value) return
  savingKey.value = item.settingKey
  let conflict = false
  try {
    const updated = await operation()
    settings.value = settings.value.map(row => row.settingKey === item.settingKey ? updated : row)
    drafts.value[item.settingKey] = settingInputText(updated)
    ElMessage.success('系统设置已保存')
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请重试')
    conflict = error?.status === 409
  } finally {
    savingKey.value = ''
  }
  if (conflict) await loadSettings()
}

function save(item, value) {
  try {
    const settingValue = serializeSettingInput(item.settingValueType, value)
    return mutate(item, () => saveSystemSetting(item.settingKey, { ...settingVersion(item), settingValue }))
  } catch (error) {
    ElMessage.warning(error.message)
  }
}

function saveText(item) {
  return save(item, drafts.value[item.settingKey])
}

function reset(item) {
  return mutate(item, () => resetSystemSetting(item.settingKey, settingVersion(item)))
}

onMounted(loadSettings)
</script>

<style scoped>
.global-settings { padding: 24px; max-width: 1100px; margin: 0 auto; }
.page-header, .setting-heading, .setting-control { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-header { margin-bottom: 24px; }
h2, h3 { margin: 0; color: #303133; }
h3 { font-size: 16px; }
p { color: #606266; line-height: 1.7; }
.setting-list { display: grid; gap: 16px; min-height: 120px; }
.setting-remark { white-space: pre-wrap; margin: 16px 0; }
.setting-control { justify-content: flex-start; flex-wrap: wrap; }
@media (max-width: 640px) {
  .global-settings { padding: 16px; }
  .setting-heading { align-items: flex-start; }
}
</style>
