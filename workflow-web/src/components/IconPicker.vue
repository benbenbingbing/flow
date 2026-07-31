<template>
  <div class="icon-picker">
    <el-input
      v-model="selectedIcon"
      placeholder="点击选择图标"
      readonly
      @click="dialogVisible = true"
    >
      <template #prefix>
        <el-icon v-if="selectedIcon && getIconComponent(selectedIcon)">
          <component :is="getIconComponent(selectedIcon)" />
        </el-icon>
      </template>
      <template #suffix>
        <el-icon @click.stop="clearIcon" v-if="selectedIcon">
          <CircleClose />
        </el-icon>
      </template>
    </el-input>
    
    <el-dialog
      v-model="dialogVisible"
      title="选择图标"
      width="800px"
      append-to-body
    >
      <el-input
        v-model="searchText"
        placeholder="搜索图标"
        clearable
        style="margin-bottom: 16px"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      
      <div class="icon-grid">
        <div
          v-for="icon in filteredIcons"
          :key="icon"
          :class="['icon-item', { active: selectedIcon === icon }]"
          @click="selectIcon(icon)"
        >
          <el-icon :size="24" v-if="getIconComponent(icon)">
            <component :is="getIconComponent(icon)" />
          </el-icon>
          <span class="icon-name">{{ icon }}</span>
        </div>
      </div>
      
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  COMMON_MENU_ICON_NAMES,
  listMenuIconNames,
  normalizeMenuIconName,
  resolveMenuIcon
} from '@/utils/menuIcons'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits(['update:modelValue'])

const dialogVisible = ref(false)
const searchText = ref('')

const selectedIcon = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const allIcons = listMenuIconNames()

const filteredIcons = computed(() => {
  if (!searchText.value) return COMMON_MENU_ICON_NAMES
  return allIcons.filter(
    name => name.toLowerCase().includes(searchText.value.toLowerCase())
  )
})

const getIconComponent = (iconName: string) => resolveMenuIcon(iconName)

const selectIcon = (icon: string) => {
  selectedIcon.value = normalizeMenuIconName(icon)
  dialogVisible.value = false
}

const clearIcon = () => {
  selectedIcon.value = ''
}

watch(() => dialogVisible.value, (val) => {
  if (val) {
    searchText.value = ''
  }
})
</script>

<style scoped lang="scss">
.icon-picker {
  :deep(.el-input__prefix) {
    display: flex;
    align-items: center;
  }
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
  padding: 8px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  
  &:hover {
    background-color: #f5f7fa;
  }
  
  &.active {
    background-color: #ecf5ff;
    color: #409eff;
    border: 1px solid #409eff;
  }
  
  .icon-name {
    font-size: 12px;
    margin-top: 4px;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
