import { registerCellComponent } from '@/utils/listCellRegistry'
import {
  registerCustomFormComponent,
  registerCustomListComponent
} from '@/utils/customComponentRegistry'
import DemoRiskProgressCell from './list-fields/DemoRiskProgressCell.vue'
import DemoProjectCardList from './lists/DemoProjectCardList.vue'
import DemoProjectForm from './forms/DemoProjectForm.vue'

export const DEMO_RISK_CELL = 'DemoRiskProgressCell'
export const DEMO_PROJECT_LIST = 'DemoProjectCardList'
export const DEMO_PROJECT_FORM = 'DemoProjectForm'

export function registerDemoExtensions() {
  registerCellComponent(DEMO_RISK_CELL, DemoRiskProgressCell, {
    label: 'Demo·风险进度',
    description: '用进度条和风险标签展示 0-100 风险评分。',
    supportedFieldTypes: ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'],
    configSchema: [
      { key: 'warningAt', label: '关注阈值', type: 'number', min: 0, max: 100, defaultValue: 40 },
      { key: 'dangerAt', label: '高危阈值', type: 'number', min: 0, max: 100, defaultValue: 70 },
      { key: 'showText', label: '显示百分比', type: 'boolean', defaultValue: true },
      { key: 'showLevel', label: '显示风险等级', type: 'boolean', defaultValue: true }
    ]
  })

  registerCustomListComponent(DEMO_PROJECT_LIST, DemoProjectCardList, {
    label: 'Demo·项目卡片列表',
    description: '项目卡片、风险进度、标准权限操作和平台分页的完整示例。',
    configSchema: [
      { key: 'columns', label: '卡片列数', type: 'number', min: 1, max: 4, defaultValue: 3 },
      { key: 'compact', label: '紧凑模式', type: 'boolean', defaultValue: false },
      { key: 'showDescription', label: '显示项目说明', type: 'boolean', defaultValue: true },
      { key: 'searchPlaceholder', label: '搜索提示', type: 'text', defaultValue: '搜索项目名称' }
    ],
    capabilities: {
      layout: 'card',
      reusesPlatformActions: true
    }
  })

  registerCustomFormComponent(DEMO_PROJECT_FORM, DemoProjectForm, {
    label: 'Demo·项目定制表单',
    description: '覆盖新增、编辑、审批、查看四种模式并暴露 validate。',
    supportedModes: ['create', 'edit', 'approve', 'view'],
    configSchema: [
      { key: 'subtitle', label: '副标题', type: 'text', defaultValue: '项目定制表单运行示例' },
      { key: 'accentColor', label: '强调色', type: 'text', defaultValue: '#409eff' },
      { key: 'showRiskHint', label: '显示风险提示', type: 'boolean', defaultValue: true }
    ]
  })
}
