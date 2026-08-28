<template>
  <div class="provider-panel">
    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="搜索名称或 Issuer"
        @keyup.enter="search"
      />
      <el-select v-model="filters.type" clearable placeholder="Provider Type">
        <el-option label="SIGNED_JWT" value="SIGNED_JWT" />
        <el-option label="TRUSTED_EXTERNAL_ID" value="TRUSTED_EXTERNAL_ID" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="状态">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="停用" value="DISABLED" />
        <el-option label="已撤销" value="REVOKED" />
      </el-select>
      <el-button :loading="loading" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <span class="filter-spacer" />
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建 Provider
      </el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="panel-alert"
    />

    <el-table v-loading="loading" :data="providers" border stripe>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="type" label="Type" min-width="160" />
      <el-table-column prop="issuer" label="Issuer" min-width="220">
        <template #default="{ row }">{{ row.issuer || '-' }}</template>
      </el-table-column>
      <el-table-column prop="subjectNamespace" label="Namespace" min-width="160" />
      <el-table-column prop="jwksMode" label="JWKS Mode" min-width="145">
        <template #default="{ row }">{{ row.jwksMode || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Key / CAS" width="110">
        <template #default="{ row }">k{{ row.keyVersion }} / v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="285">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.status === 'REVOKED'"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="canRotate(row)"
            link
            type="primary"
            @click="openRotate(row)"
          >
            轮换公钥
          </el-button>
          <el-button
            link
            :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
            :disabled="row.status === 'REVOKED'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status === 'REVOKED'"
            @click="revoke(row)"
          >
            撤销
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadProviders"
        @size-change="handlePageSizeChange"
      />
    </div>

    <el-dialog
      v-model="editorVisible"
      :title="editingProvider ? '编辑 Identity Provider' : '新建 Identity Provider'"
      width="min(820px, 96vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearEditor"
    >
      <el-alert
        v-if="editorError"
        type="error"
        :title="editorError"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-form label-position="top" autocomplete="off">
        <div class="form-grid">
          <el-form-item label="名称" required>
            <el-input v-model="providerForm.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="Type" required>
            <template #label>
              <ConfigHelpLabel label="Type" help-key="embed.provider.type" />
            </template>
            <el-select
              v-model="providerForm.type"
              :disabled="Boolean(editingProvider)"
              style="width: 100%"
              @change="handleTypeChange"
            >
              <el-option label="SIGNED_JWT" value="SIGNED_JWT" />
              <el-option label="TRUSTED_EXTERNAL_ID" value="TRUSTED_EXTERNAL_ID" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="Subject Namespace" required>
          <template #label>
            <ConfigHelpLabel
              label="Subject Namespace"
              help-key="embed.provider.subjectNamespace"
            />
          </template>
          <el-input
            v-model="providerForm.subjectNamespace"
            :disabled="Boolean(editingProvider)"
            maxlength="128"
            placeholder="例如 crm-prod"
          />
          <div class="field-help">参与 Subject 摘要的 domain separation，创建后不可修改。</div>
        </el-form-item>

        <template v-if="providerForm.type === 'SIGNED_JWT'">
          <el-form-item label="Issuer" required>
            <template #label>
              <ConfigHelpLabel label="Issuer" help-key="embed.provider.issuer" />
            </template>
            <el-input
              v-model="providerForm.issuer"
              maxlength="500"
              placeholder="https://id.example.com"
            />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="Audiences" required>
              <template #label>
                <ConfigHelpLabel
                  label="Audiences"
                  help-key="embed.provider.audiences"
                />
              </template>
              <el-input
                v-model="providerForm.audiencesText"
                type="textarea"
                :rows="4"
                placeholder="每行一个，必须包含 flow-embed-launch"
              />
            </el-form-item>
            <el-form-item label="Algorithms" required>
              <template #label>
                <ConfigHelpLabel
                  label="Algorithms"
                  help-key="embed.provider.algorithms"
                />
              </template>
              <el-select
                v-model="providerForm.algorithms"
                multiple
                style="width: 100%"
              >
                <el-option
                  v-for="algorithm in algorithmOptions"
                  :key="algorithm"
                  :label="algorithm"
                  :value="algorithm"
                />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="JWKS Mode" required>
            <template #label>
              <ConfigHelpLabel
                label="JWKS Mode"
                help-key="embed.provider.jwksMode"
              />
            </template>
            <el-radio-group v-model="providerForm.jwksMode" @change="clearJwksInput">
              <el-radio value="STATIC_JWK_SET">STATIC_JWK_SET</el-radio>
              <el-radio value="REMOTE_JWKS">REMOTE_JWKS</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item
            v-if="providerForm.jwksMode === 'STATIC_JWK_SET'"
            :label="editingProvider ? '新的公开 JWKS（留空保持当前公钥）' : '公开 JWKS'"
            :required="!editingProvider || modeChanged"
          >
            <template #label>
              <ConfigHelpLabel
                :label="editingProvider ? '新的公开 JWKS（留空保持当前公钥）' : '公开 JWKS'"
                help-key="embed.provider.publicJwks"
              />
            </template>
            <el-input
              v-model="providerForm.jwksText"
              type="textarea"
              :rows="9"
              spellcheck="false"
              autocomplete="off"
              placeholder='{"keys":[{"kty":"RSA","kid":"...","n":"...","e":"AQAB"}]}'
              class="json-editor"
            />
            <div class="field-help">只允许公钥参数；含 d、p、q、k 等私钥参数的输入会在本地被拒绝。</div>
          </el-form-item>
          <el-form-item
            v-else
            label="JWKS URL"
            required
          >
            <template #label>
              <ConfigHelpLabel label="JWKS URL" help-key="embed.provider.jwksUrl" />
            </template>
            <el-input
              v-model="providerForm.jwksUrl"
              maxlength="2048"
              placeholder="https://id.example.com/.well-known/jwks.json"
            />
          </el-form-item>
        </template>

        <div class="form-grid">
          <el-form-item label="Clock Skew (秒)">
            <template #label>
              <ConfigHelpLabel
                label="Clock Skew (秒)"
                help-key="embed.provider.clockSkew"
              />
            </template>
            <el-input-number
              v-model="providerForm.clockSkewSeconds"
              :min="0"
              :max="300"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="Assertion 最长有效期 (秒)">
            <template #label>
              <ConfigHelpLabel
                label="Assertion 最长有效期 (秒)"
                help-key="embed.provider.maxAssertionLifetime"
              />
            </template>
            <el-input-number
              v-model="providerForm.maxAssertionLifetimeSeconds"
              :min="1"
              :max="300"
              controls-position="right"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProvider">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="rotateVisible"
      title="轮换静态公钥"
      width="min(680px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearRotate"
    >
      <el-alert
        title="提交后将立即增加 keyVersion 并废弃依赖旧版验签材料的会话。"
        type="warning"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-alert
        v-if="rotateError"
        type="error"
        :title="rotateError"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-input
        v-model="rotateJwksText"
        type="textarea"
        :rows="12"
        spellcheck="false"
        autocomplete="off"
        class="json-editor"
        placeholder="粘贴仅包含公开验签材料的 JWKS"
      />
      <template #footer>
        <el-button @click="rotateVisible = false">取消</el-button>
        <el-button type="danger" :loading="rotating" @click="rotateKey">
          确认轮换
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  assertPublicJwks,
  buildExpectedVersionPayload,
  describeEmbedManagementError,
  parseJsonObject,
  parseLineValues,
  providerToEditor
} from './embedManagementModel'

const algorithmOptions = [
  'RS256', 'RS384', 'RS512',
  'PS256', 'PS384', 'PS512',
  'ES256', 'ES384', 'ES512'
]
const supportedAlgorithmSet = new Set(algorithmOptions)
const providers = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({ keyword: '', type: '', status: '' })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const editorVisible = ref(false)
const editingProvider = ref(null)
const originalJwksMode = ref(null)
const providerForm = reactive(providerToEditor())
const saving = ref(false)
const editorError = ref('')
const rotateVisible = ref(false)
const rotatingProvider = ref(null)
const rotateJwksText = ref('')
const rotateError = ref('')
const rotating = ref(false)

const modeChanged = computed(() => Boolean(
  editingProvider.value
  && originalJwksMode.value !== providerForm.jwksMode
))

onMounted(loadProviders)

async function loadProviders() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await embedManagementApi.providers.page({
      keyword: filters.keyword.trim() || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    providers.value = result?.list || result?.records || []
    pagination.total = Number(result?.total || 0)
  } catch (error) {
    loadError.value = describeEmbedManagementError(error)
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.pageNum = 1
  loadProviders()
}

function resetFilters() {
  Object.assign(filters, { keyword: '', type: '', status: '' })
  search()
}

function handlePageSizeChange() {
  pagination.pageNum = 1
  loadProviders()
}

function openCreate() {
  clearEditor()
  editorVisible.value = true
}

function openEdit(row) {
  editingProvider.value = row
  originalJwksMode.value = row.jwksMode
  Object.assign(providerForm, providerToEditor(row))
  providerForm.algorithms = normalizeSupportedAlgorithms(providerForm.algorithms)
  editorVisible.value = true
}

function clearEditor() {
  editingProvider.value = null
  originalJwksMode.value = null
  editorError.value = ''
  Object.assign(providerForm, providerToEditor())
  clearJwksInput()
}

function clearJwksInput() {
  // 验签材料仅存在于当前对话框内存，切换模式或关闭即清除。
  providerForm.jwksText = ''
}

function handleTypeChange(type) {
  if (type === 'TRUSTED_EXTERNAL_ID') {
    providerForm.issuer = ''
    providerForm.audiencesText = ''
    providerForm.algorithms = []
    providerForm.jwksUrl = ''
    clearJwksInput()
  } else {
    providerForm.audiencesText = 'flow-embed-launch'
    providerForm.algorithms = ['RS256']
    providerForm.jwksMode = 'STATIC_JWK_SET'
  }
}

/**
 * 只保留后端 V1 支持的 RSA、RSA-PSS 与 ECDSA 算法。
 * 编辑历史数据和提交前都执行一次，避免旧的不支持算法继续显示或被原样回传。
 */
function normalizeSupportedAlgorithms(algorithms) {
  const source = Array.isArray(algorithms) ? algorithms : []
  return [...new Set(source.filter(algorithm => supportedAlgorithmSet.has(algorithm)))]
}

function buildProviderPayload() {
  if (!providerForm.name.trim() || !providerForm.subjectNamespace.trim()) {
    throw new Error('名称和 Subject Namespace 为必填')
  }
  const common = {
    name: providerForm.name.trim(),
    issuer: providerForm.type === 'SIGNED_JWT'
      ? providerForm.issuer.trim()
      : null,
    audiences: providerForm.type === 'SIGNED_JWT'
      ? parseLineValues(providerForm.audiencesText)
      : [],
    subjectNamespace: providerForm.subjectNamespace.trim(),
    algorithms: providerForm.type === 'SIGNED_JWT'
      ? normalizeSupportedAlgorithms(providerForm.algorithms)
      : [],
    jwksMode: providerForm.type === 'SIGNED_JWT'
      ? providerForm.jwksMode
      : null,
    jwksUrl: providerForm.type === 'SIGNED_JWT'
      && providerForm.jwksMode === 'REMOTE_JWKS'
      ? providerForm.jwksUrl.trim()
      : null,
    clockSkewSeconds: providerForm.clockSkewSeconds,
    maxAssertionLifetimeSeconds:
      providerForm.maxAssertionLifetimeSeconds
  }
  if (providerForm.type === 'SIGNED_JWT') {
    if (!common.issuer || !common.audiences.includes('flow-embed-launch')) {
      throw new Error('SIGNED_JWT 必须配置 HTTPS Issuer 且 Audience 包含 flow-embed-launch')
    }
    if (!common.algorithms.length) {
      throw new Error('至少选择一种非对称签名算法')
    }
    if (providerForm.jwksMode === 'STATIC_JWK_SET') {
      if (providerForm.jwksText.trim()) {
        common.jwks = assertPublicJwks(
          parseJsonObject(providerForm.jwksText, 'JWKS')
        )
      } else if (!editingProvider.value || modeChanged.value) {
        throw new Error('请填写公开 JWKS')
      }
    } else if (!common.jwksUrl) {
      throw new Error('请填写受控 HTTPS JWKS URL')
    }
  }
  return editingProvider.value
    ? buildExpectedVersionPayload(editingProvider.value.version, common)
    : { type: providerForm.type, ...common }
}

async function saveProvider() {
  let payload
  try {
    payload = buildProviderPayload()
  } catch (error) {
    editorError.value = error.message
    return
  }
  saving.value = true
  editorError.value = ''
  try {
    if (editingProvider.value) {
      await embedManagementApi.providers.update(editingProvider.value.id, payload)
    } else {
      await embedManagementApi.providers.create(payload)
    }
    clearJwksInput()
    editorVisible.value = false
    ElMessage.success('Identity Provider 已保存')
    await loadProviders()
  } catch (error) {
    clearJwksInput()
    editorError.value = describeEmbedManagementError(error)
  } finally {
    saving.value = false
  }
}

function canRotate(row) {
  return row.type === 'SIGNED_JWT'
    && row.jwksMode === 'STATIC_JWK_SET'
    && row.status !== 'REVOKED'
}

function openRotate(row) {
  clearRotate()
  rotatingProvider.value = row
  rotateVisible.value = true
}

function clearRotate() {
  rotatingProvider.value = null
  rotateJwksText.value = ''
  rotateError.value = ''
}

async function rotateKey() {
  let jwks
  try {
    jwks = assertPublicJwks(parseJsonObject(rotateJwksText.value, 'JWKS'))
  } catch (error) {
    rotateError.value = error.message
    return
  }
  rotating.value = true
  rotateError.value = ''
  try {
    await embedManagementApi.providers.rotateKey(
      rotatingProvider.value.id,
      buildExpectedVersionPayload(rotatingProvider.value.version, { jwks })
    )
    rotateJwksText.value = ''
    rotateVisible.value = false
    ElMessage.success('静态公钥已轮换')
    await loadProviders()
  } catch (error) {
    rotateJwksText.value = ''
    rotateError.value = describeEmbedManagementError(error)
  } finally {
    rotating.value = false
  }
}

async function toggleStatus(row) {
  const status = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await embedManagementApi.providers.changeStatus(
      row.id,
      buildExpectedVersionPayload(row.version, {
        status,
        reason: '管理台状态变更'
      })
    )
    ElMessage.success(status === 'ACTIVE' ? 'Provider 已启用' : 'Provider 已停用')
    await loadProviders()
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

async function revoke(row) {
  let reason
  try {
    const result = await ElMessageBox.prompt(
      '撤销为终态，会废弃关联 Grant 与会话，请填写原因。',
      '撤销 Identity Provider',
      {
        type: 'warning',
        inputValidator: value => Boolean(value?.trim()) || '请填写撤销原因',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消'
      }
    )
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    await embedManagementApi.providers.revoke(
      row.id,
      buildExpectedVersionPayload(row.version, { reason })
    )
    ElMessage.success('Provider 已撤销')
    await loadProviders()
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

function statusType(status) {
  return { ACTIVE: 'success', DISABLED: 'warning', REVOKED: 'danger' }[status] || 'info'
}

function statusLabel(status) {
  return { ACTIVE: '启用', DISABLED: '停用', REVOKED: '已撤销' }[status] || status
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 4px 0 14px;
}

.filter-bar > .el-input,
.filter-bar > .el-select {
  width: 200px;
}

.filter-spacer {
  flex: 1;
}

.panel-alert {
  margin-bottom: 12px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.field-help {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}

.json-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
