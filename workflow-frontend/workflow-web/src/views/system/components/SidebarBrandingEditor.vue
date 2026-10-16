<template>
  <div class="sidebar-branding-editor">
    <el-form label-position="top" class="branding-fields" @submit.prevent="save">
      <el-form-item label="系统名称" label-for="sidebar-brand-title">
        <el-input id="sidebar-brand-title" v-model="draft.title" maxlength="40" show-word-limit :disabled="disabled || readonly" placeholder="流程配置系统" />
      </el-form-item>
      <el-form-item label="系统图标">
        <IconPicker v-model="draft.icon" :disabled="disabled || readonly" />
      </el-form-item>
      <el-form-item label="自定义图片">
        <div class="branding-image-actions">
          <input ref="imageInput" class="branding-file-input" type="file" :accept="SIDEBAR_BRAND_IMAGE_TYPES.join(',')" :disabled="disabled || readonly" aria-label="选择侧边栏图片" @change="selectImage" />
          <el-button v-if="!readonly" :disabled="disabled" :loading="imageLoading" @click="imageInput?.click()">{{ draft.imageBase64 ? '更换图片' : '上传图片' }}</el-button>
          <el-button v-if="!readonly && draft.imageBase64" :disabled="disabled" @click="removeImage">移除图片</el-button>
          <span class="branding-image-status">{{ draft.imageBase64 ? '已配置自定义图片' : '当前使用系统图标' }}</span>
        </div>
      </el-form-item>
      <p class="branding-hint">支持 PNG、JPG、GIF、WebP，最大 32 KiB。图片优先于图标显示；移除图片后恢复图标。保存后更新侧边栏，其他页面刷新后生效。</p>
      <p v-if="imageError" class="branding-error" role="alert">{{ imageError }}</p>
      <p v-if="!draft.title.trim()" class="branding-error" role="alert">请输入系统名称</p>
      <el-button v-if="!readonly" type="primary" :disabled="disabled || imageLoading || !draft.title.trim()" :loading="saving" @click="save">保存标识</el-button>
    </el-form>
    <div class="branding-preview" aria-label="侧边栏标识预览">
      <SidebarBrand :branding="draft" />
      <div class="preview-menu"><el-icon :size="18"><HomeFilled /></el-icon><span>首页</span></div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { HomeFilled } from '@element-plus/icons-vue'
import IconPicker from '@/components/IconPicker.vue'
import SidebarBrand from '@/components/SidebarBrand.vue'
import { SIDEBAR_BRAND_IMAGE_TYPES, normalizeSidebarBranding, readSidebarBrandImageFile } from '@/shared/sidebar-branding'

const props = defineProps({ modelValue: Object, disabled: Boolean, readonly: Boolean, saving: Boolean })
const emit = defineEmits(['save'])
const draft = ref(normalizeSidebarBranding(props.modelValue))
const imageInput = ref(null)
const imageLoading = ref(false)
const imageError = ref('')
let imageRevision = 0
watch(() => props.modelValue, value => {
  cancelImageRead()
  imageError.value = ''
  draft.value = normalizeSidebarBranding(value)
}, { deep: true })
watch(() => props.disabled || props.readonly, disabled => { if (disabled) cancelImageRead() })
onBeforeUnmount(cancelImageRead)

/** 刷新配置、移除图片或离开页面时丢弃旧读取结果，避免稍后覆盖用户的新选择。 */
function cancelImageRead() {
  imageRevision += 1
  imageLoading.value = false
}

/** 上传仅改变本地预览；读取失败保留原图，明确保存后才持久化到标识 JSON。 */
async function selectImage(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || props.disabled || props.readonly) return
  const revision = ++imageRevision
  imageLoading.value = true
  imageError.value = ''
  try {
    const imageBase64 = await readSidebarBrandImageFile(file)
    if (revision === imageRevision) draft.value.imageBase64 = imageBase64
  } catch (error) {
    if (revision === imageRevision) imageError.value = error.message
  } finally {
    if (revision === imageRevision) imageLoading.value = false
  }
}

function removeImage() {
  if (props.disabled || props.readonly) return
  cancelImageRead()
  imageError.value = ''
  draft.value.imageBase64 = ''
}

/** 预览与保存分离；必须显式保存才会改变系统标识，空图标恢复原有 Connection。 */
function save() {
  if (props.disabled || props.readonly || imageLoading.value || !draft.value.title.trim()) return
  emit('save', normalizeSidebarBranding(draft.value))
}
</script>

<style scoped>
.sidebar-branding-editor { display: flex; align-items: flex-start; flex-wrap: wrap; gap: 32px; width: 100%; }
.branding-fields { flex: 1 1 260px; min-width: 0; }
.branding-fields :deep(.icon-picker) { width: 100%; }
.branding-file-input { display: none; }
.branding-image-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.branding-image-actions .el-button + .el-button { margin-left: 0; }
.branding-image-status { color: #909399; font-size: 12px; }
.branding-hint { color: #909399; font-size: 12px; line-height: 1.8; }
.branding-error { color: #c45656; font-size: 13px; }
.branding-preview { width: 240px; max-width: 100%; background: #304156; border-radius: 6px; overflow: hidden; }
.preview-menu { display: flex; align-items: center; height: 56px; padding: 0 20px; gap: 5px; color: #bfcbd9; font-size: 14px; }
.preview-menu .el-icon { width: 24px; }
</style>
