<template>
  <div v-if="entityData" class="entity-form-section">
    <template v-if="approvalNormalForm && approvalNormalForm.fields && approvalNormalForm.fields.length > 0">
      <FormPreviewLinkage
        :form="approvalNormalForm"
        :model-value="entityData"
        @update:model-value="(val) => emit('update:entityData', val)"
        :readonly="formReadonly"
        :mode="mode"
        :show-header="false"
        :no-internal-tabs="true"
      />
    </template>
    <template v-else>
      <el-form :model="entityData" label-width="100px" class="entity-form">
        <el-row :gutter="20">
          <el-col v-for="(value, key) in entityData" :key="key" :span="12">
            <el-form-item :label="key">
              <div v-if="value && typeof value === 'object' && !Array.isArray(value)" class="file-display-readonly">
                <div v-for="(urls, groupName) in value" :key="groupName" class="file-group-readonly">
                  <el-tag size="small" type="primary">{{ groupName }}</el-tag>
                  <div v-for="(url, idx) in (Array.isArray(urls) ? urls : [urls])" :key="idx" class="file-item-readonly">
                    <a :href="url" target="_blank" class="file-link">
                      <el-icon><Document /></el-icon>
                      {{ url.split('/').pop() }}
                    </a>
                  </div>
                </div>
              </div>
              <div v-else-if="Array.isArray(value)" class="file-list-readonly">
                <div v-for="(url, idx) in value" :key="idx" class="file-item-readonly">
                  <a v-if="typeof url === 'string' && url.startsWith('/')" :href="url" target="_blank" class="file-link">
                    <el-icon><Document /></el-icon>
                    {{ url.split('/').pop() }}
                  </a>
                  <span v-else>{{ url }}</span>
                </div>
              </div>
              <div v-else-if="typeof value === 'string' && value.startsWith('/')" class="file-item-readonly">
                <a :href="value" target="_blank" class="file-link">
                  <el-icon><Document /></el-icon>
                  {{ value.split('/').pop() }}
                </a>
              </div>
              <el-input v-else v-model="entityData[key]" :readonly="true" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </template>
  </div>
  <template v-if="!isViewMode && effectiveApprovalConfig.enabled !== false">
    <div class="approval-opinion-section">
      <el-divider />
      <div class="section-title">审批意见</div>
      <el-form :model="approveForm" label-width="80px">
      <el-form-item label="审批操作" required>
        <el-radio-group v-model="approveForm.action">
          <el-radio-button
            v-for="option in effectiveApprovalConfig.options"
            :key="option.value"
            :label="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        v-if="effectiveApprovalConfig.options.find(o => o.value === approveForm.action)?.showComment !== false"
        :label="effectiveApprovalConfig.commentLabel || '审批备注'"
      >
        <el-input
          v-model="approveForm.comment"
          type="textarea"
          :rows="3"
          :placeholder="`请输入${effectiveApprovalConfig.commentLabel || '审批备注'}`"
        />
      </el-form-item>
    </el-form>
    </div>
  </template>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'

const props = defineProps<{
  entityData: any
  approvalNormalForm: any
  effectiveApprovalConfig: any
  isViewMode: boolean
  formReadonly: boolean
  mode: string
}>()

const approveForm = defineModel<any>('approveForm', { required: true })

const emit = defineEmits<{
  'update:entityData': [val: any]
}>()
</script>

<style scoped lang="scss">
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 4px solid #409eff;
}

.approval-opinion-section {
  position: sticky;
  bottom: 0;
  background: #ffffff;
  padding: 8px 0 16px;
  border-top: 1px solid #e4e7ed;
  z-index: 10;
}

/* 文件只读展示样式 */
.file-display-readonly {
  width: 100%;
}

.file-list-readonly {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-group-readonly {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.file-item-readonly {
  display: flex;
  align-items: center;
}

.file-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #409eff;
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 4px;
  background-color: #ecf5ff;
  transition: all 0.3s;
}

.file-link:hover {
  background-color: #409eff;
  color: #fff;
}
</style>
