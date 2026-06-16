<template>
  <div class="list-button-config-panel">
    <div class="toolbar-actions">
      <el-dropdown split-button type="primary" size="small" @click="addCustom">
        添加自定义按钮
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="opt in builtinOptions"
              :key="opt.key"
              :disabled="isBuiltinAdded(opt.key)"
              @click="addBuiltin(opt.key)"
            >
              {{ opt.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-table :data="sortedButtons" size="small" border>
      <el-table-column label="排序" width="80" align="center">
        <template #default="{ row }">
          <el-input-number v-model="row.sort" :min="0" :max="999" controls-position="right" size="small" style="width: 70px" />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="60" align="center">
        <template #default="{ row }">
          <el-checkbox v-model="row.enabled" />
        </template>
      </el-table-column>
      <el-table-column label="按钮名称" width="130">
        <template #default="{ row }">
          <el-input v-model="row.label" size="small" placeholder="按钮名称" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-select v-model="row.type" size="small" style="width: 100px">
            <el-option label="内置" value="built-in" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="内置类型 / 执行器" width="160">
        <template #default="{ row }">
          <el-select v-if="row.type === 'built-in'" v-model="row.key" size="small" style="width: 150px">
            <el-option v-for="opt in builtinOptions" :key="opt.key" :label="opt.label" :value="opt.key" />
          </el-select>
          <template v-else>
            <el-input v-model="row.customHandler" size="small" placeholder="执行器/组件名" style="width: 150px" />
          </template>
        </template>
      </el-table-column>
      <el-table-column label="自定义模式" width="110" v-if="type === 'toolbar'">
        <template #default="{ row }">
          <el-select v-if="row.type === 'custom'" v-model="row.customMode" size="small" style="width: 100px">
            <el-option label="函数" value="handler" />
            <el-option label="组件" value="component" />
          </el-select>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="图标" width="110">
        <template #default="{ row }">
          <el-input v-model="row.icon" size="small" placeholder="Element 图标名" />
        </template>
      </el-table-column>
      <el-table-column label="样式" width="110">
        <template #default="{ row }">
          <el-select v-model="row.buttonType" size="small" style="width: 100px">
            <el-option label="默认" value="default" />
            <el-option label="主要" value="primary" />
            <el-option label="成功" value="success" />
            <el-option label="警告" value="warning" />
            <el-option label="危险" value="danger" />
            <el-option label="信息" value="info" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="Link" width="70" align="center" v-if="type === 'row'">
        <template #default="{ row }">
          <el-checkbox v-model="row.link" />
        </template>
      </el-table-column>
      <el-table-column label="权限码" min-width="180">
        <template #default="{ row }">
          <el-select
            v-model="row.perm"
            size="small"
            filterable
            allow-create
            clearable
            placeholder="选择或输入权限码"
            style="width: 100%"
          >
            <el-option v-for="perm in permOptions" :key="perm" :label="perm" :value="perm" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70" align="center" fixed="right">
        <template #default="{ $index }">
          <el-button link type="danger" size="small" @click="remove($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPermsByEntityCode } from '@/api/system/menu'

const props = defineProps({
  type: {
    type: String,
    default: 'toolbar' // 'toolbar' | 'row'
  },
  entityCode: {
    type: String,
    default: ''
  },
  modelValue: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const buttons = computed({
  get: () => props.modelValue || [],
  set: (val) => emit('update:modelValue', val)
})

const sortedButtons = computed(() => {
  return [...buttons.value].sort((a, b) => (a.sort || 0) - (b.sort || 0))
})

const permOptions = ref([])

const TOOLBAR_BUILTIN = {
  create: { key: 'create', type: 'built-in', label: '新增数据', icon: 'Plus', buttonType: 'primary', sort: 1, enabled: true, perm: '' },
  exportSelected: { key: 'exportSelected', type: 'built-in', label: '导出选中', icon: 'Download', buttonType: 'default', sort: 2, enabled: true, perm: '' },
  exportAll: { key: 'exportAll', type: 'built-in', label: '导出全部', icon: 'Download', buttonType: 'default', sort: 3, enabled: true, perm: '' },
  batchDelete: { key: 'batchDelete', type: 'built-in', label: '批量删除', icon: 'Delete', buttonType: 'danger', sort: 4, enabled: true, perm: '' }
}

const ROW_BUILTIN = {
  view: { key: 'view', type: 'built-in', label: '查看', buttonType: 'primary', link: true, sort: 1, enabled: true, perm: '' },
  edit: { key: 'edit', type: 'built-in', label: '编辑', buttonType: 'primary', link: true, sort: 2, enabled: true, perm: '' },
  approve: { key: 'approve', type: 'built-in', label: '审批', buttonType: 'warning', link: true, sort: 3, enabled: true, perm: '' },
  delete: { key: 'delete', type: 'built-in', label: '删除', buttonType: 'danger', link: true, sort: 4, enabled: true, perm: '' }
}

const builtinPresets = computed(() => props.type === 'toolbar' ? TOOLBAR_BUILTIN : ROW_BUILTIN)
const builtinOptions = computed(() => Object.values(builtinPresets.value))

function isBuiltinAdded(key) {
  return buttons.value.some(b => b.type === 'built-in' && b.key === key)
}

function addBuiltin(key) {
  if (isBuiltinAdded(key)) {
    ElMessage.warning('该内置按钮已添加')
    return
  }
  const preset = builtinPresets.value[key]
  if (!preset) return
  buttons.value.push({ ...preset })
}

function addCustom() {
  buttons.value.push({
    key: 'custom_' + Date.now(),
    type: 'custom',
    customMode: 'handler',
    label: '自定义按钮',
    icon: '',
    buttonType: 'default',
    sort: buttons.value.length + 1,
    enabled: true,
    perm: '',
    customHandler: '',
    link: props.type === 'row'
  })
}

function remove(index) {
  const sortedIndex = sortedButtons.value.indexOf(buttons.value[index])
  const actualIndex = buttons.value.indexOf(sortedButtons.value[sortedIndex])
  buttons.value.splice(actualIndex, 1)
}

async function loadPermOptions() {
  if (!props.entityCode) return
  try {
    permOptions.value = await getPermsByEntityCode(props.entityCode) || []
  } catch (e) {
    console.error('加载权限码失败:', e)
    permOptions.value = []
  }
}

onMounted(() => {
  loadPermOptions()
})

watch(() => props.entityCode, () => {
  loadPermOptions()
})
</script>

<style scoped>
.list-button-config-panel {
  .toolbar-actions {
    margin-bottom: 12px;
  }
}
</style>
