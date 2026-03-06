<template>
  <div class="icon-picker">
    <el-input
      v-model="selectedIcon"
      placeholder="点击选择图标"
      readonly
      @click="dialogVisible = true"
    >
      <template #prefix>
        <el-icon v-if="selectedIcon">
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
          <el-icon :size="24">
            <component :is="icon" />
          </el-icon>
          <span class="icon-name">{{ icon }}</span>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSelect">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits(['update:modelValue'])

const dialogVisible = ref(false)
const searchText = ref('')
const tempSelectedIcon = ref('')

const selectedIcon = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 常用图标列表
const commonIcons = [
  'HomeFilled', 'Home', 'User', 'UserFilled', 'Setting', 'Tools',
  'Document', 'Folder', 'FolderOpened', 'FolderChecked', 'FolderDelete',
  'Menu', 'Grid', 'List', 'Histogram', 'DataLine', 'TrendCharts',
  'PieChart', 'Histogram', 'Connection', 'Link', 'Share',
  'Search', 'ZoomIn', 'ZoomOut', 'FullScreen', 'Rank',
  'Plus', 'Edit', 'Delete', 'Check', 'Close', 'Refresh',
  'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown',
  'Upload', 'Download', 'Top', 'Bottom', 'Back', 'Right',
  'Warning', 'WarningFilled', 'InfoFilled', 'SuccessFilled', 'CircleCheck', 'CircleClose',
  'Message', 'Bell', 'ChatDotRound', 'ChatLineRound', 'Notification',
  'Lock', 'Unlock', 'Key', 'Unlock',
  'Calendar', 'Timer', 'Clock', 'Watch', 'AlarmClock',
  'Monitor', 'Cellphone', 'Camera', 'VideoCamera', 'Picture',
  'Goods', 'ShoppingCart', 'Sell', 'PriceTag', 'Wallet',
  'OfficeBuilding', 'School', 'Shop', 'Box', 'FirstAidKit',
  'User', 'UserFilled', 'Avatar', 'User', 'UserFilled',
  'Ship', 'Truck', 'Bicycle', 'Van', 'Promotion',
  'Sunny', 'Moon', 'Cloudy', 'Lightning', 'Pouring',
  'Star', 'StarFilled', 'Collection', 'CollectionTag',
  'Briefcase', 'Suitcase', 'Handbag', 'ShoppingBag',
  'Coffee', 'Food', 'IceCream', 'IceTea', 'Goblet',
  'MostlyCloudy', 'PartlyCloudy', 'Sunrise', 'Sunset',
  'DArrowRight', 'DArrowLeft', 'DCaret', 'CaretRight', 'CaretLeft',
  'Memo', 'Notebook', 'Tickets', 'Postcard', 'Discount',
  'Cpu', 'Mouse', 'Headset', 'Service', 'Coordinate'
]

const filteredIcons = computed(() => {
  if (!searchText.value) return commonIcons
  return Object.keys(ElementPlusIconsVue).filter(
    name => name.toLowerCase().includes(searchText.value.toLowerCase())
  )
})

const getIconComponent = (iconName: string) => {
  return (ElementPlusIconsVue as any)[iconName] || 'CircleCheck'
}

const selectIcon = (icon: string) => {
  tempSelectedIcon.value = icon
}

const confirmSelect = () => {
  selectedIcon.value = tempSelectedIcon.value
  dialogVisible.value = false
}

const clearIcon = () => {
  selectedIcon.value = ''
}

watch(() => dialogVisible.value, (val) => {
  if (val) {
    tempSelectedIcon.value = selectedIcon.value
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
