import {
  registerCustomFormComponent,
  registerCustomListComponent
} from '@/utils/customComponentRegistry'
import { registerCellComponent } from '@/utils/listCellRegistry'
import {
  registerListRowAction,
  registerListToolbarAction
} from '@/utils/listActionRegistry'
import {
  registerListButtonComponent
} from '@/utils/listButtonComponentRegistry'
import {
  registerFormInitializer
} from '@/utils/formInitializerRegistry'
import {
  registerEntityActionRuleCondition,
  registerEntityPermissionOptionProvider
} from '@/utils/entityActionRuleRegistry'
import { registerFormNodeComponent } from '@/utils/formNodeRegistry'
import {
  registerFormFieldComponent
} from '@/components/form-fields'
import ProjectMemberChangeForm from './forms/ProjectMemberChangeForm.vue'
import ProjectExtensionAcceptanceForm from './forms/ProjectExtensionAcceptanceForm.vue'
import ProjectAcceptanceBoardList from './lists/ProjectAcceptanceBoardList.vue'
import ProjectAcceptanceScoreField from './fields/ProjectAcceptanceScoreField.vue'
import ProjectAcceptanceLevelField from './fields/ProjectAcceptanceLevelField.vue'
import ProjectAcceptanceSummaryNode from './nodes/ProjectAcceptanceSummaryNode.vue'
import ProjectAcceptanceScoreCell from './list-cells/ProjectAcceptanceScoreCell.vue'
import ProjectAcceptanceInspectButton from './buttons/ProjectAcceptanceInspectButton.vue'
import ProjectAcceptanceRuleCondition from './rules/ProjectAcceptanceRuleCondition.vue'
import {
  projectAcceptanceInitializer,
  projectAcceptanceRowAction,
  projectAcceptanceSelectionAction,
  projectAcceptanceToolbarAction
} from './acceptanceRuntime'

export const PROJECT_MEMBER_CHANGE_FORM =
  'ProjectMemberChangeForm'
export const PROJECT_EXTENSION_ACCEPTANCE_FORM =
  'ProjectExtensionAcceptanceForm'
export const PROJECT_ACCEPTANCE_BOARD_LIST =
  'ProjectAcceptanceBoardList'
export const PROJECT_ACCEPTANCE_SCHEMA_LIST =
  'PROJECT_CUSTOM_LIST_SCHEMA'
export const PROJECT_ACCEPTANCE_SCORE_FIELD =
  'project_acceptance_score'
export const PROJECT_ACCEPTANCE_LEVEL_FIELD =
  'project_acceptance_level'
export const PROJECT_ACCEPTANCE_SUMMARY_NODE =
  'ProjectAcceptanceSummaryNode'
export const PROJECT_ACCEPTANCE_SCORE_CELL =
  'ProjectAcceptanceScoreCell'
export const PROJECT_ACCEPTANCE_INSPECT_BUTTON =
  'ProjectAcceptanceInspectButton'
export const PROJECT_ACCEPTANCE_INITIALIZER =
  'projectAcceptanceInitializer'
export const PROJECT_ACCEPTANCE_TOOLBAR_ACTION =
  'projectAcceptanceToolbarAction'
export const PROJECT_ACCEPTANCE_ROW_ACTION =
  'projectAcceptanceRowAction'
export const PROJECT_ACCEPTANCE_SELECTION_ACTION =
  'projectAcceptanceSelectionAction'
export const PROJECT_ACCEPTANCE_RULE_CONDITION =
  'PROJECT:CUSTOM_CONDITION'

export function registerProjectExtensions() {
  registerCustomFormComponent(
    PROJECT_MEMBER_CHANGE_FORM,
    ProjectMemberChangeForm,
    {
      label: '项目·成员变更表单',
      description: '支持加入、退出、暂停、恢复、投入调整及跨实体路由预判。',
      version: 1,
      snapshotVersion: 1,
      supportedModes: [
        'create',
        'edit',
        'approve',
        'view'
      ],
      configSchema: [
        {
          key: 'title',
          label: '表单标题',
          type: 'text',
          defaultValue: '项目成员变更申请'
        },
        {
          key: 'showRoutePreview',
          label: '显示审批路径',
          type: 'boolean',
          defaultValue: true
        }
      ],
      capabilities: {
        exposesValidate: true,
        computesWorkflowRouteFlags: true,
        crossEntityContext: true
      }
    }
  )

  registerCustomFormComponent(
    PROJECT_EXTENSION_ACCEPTANCE_FORM,
    ProjectExtensionAcceptanceForm,
    {
      label: '项目·扩展验收整表单',
      description: '覆盖新增、编辑、查看和审批模式，并可手工触发表单统一数据源。',
      version: 1,
      snapshotVersion: 1,
      supportedModes: ['create', 'edit', 'approve', 'view'],
      configSchema: [
        {
          key: 'title',
          label: '表单标题',
          type: 'text',
          defaultValue: '项目扩展验收单'
        },
        {
          key: 'showRuntimeTrace',
          label: '显示执行轨迹',
          type: 'boolean',
          defaultValue: true
        }
      ],
      capabilities: {
        exposesValidate: true,
        executesManagedDataSource: true,
        supportsWorkflowModes: true
      }
    }
  )

  registerCustomListComponent(
    PROJECT_ACCEPTANCE_BOARD_LIST,
    ProjectAcceptanceBoardList,
    {
      label: '项目·扩展验收看板列表',
      description: '使用平台查询、分页和权限动作的全自定义列表。',
      version: 1,
      snapshotVersion: 1,
      configSchema: [
        {
          key: 'showProviderTrace',
          label: '显示 Provider 轨迹',
          type: 'boolean',
          defaultValue: true
        },
        {
          key: 'searchPlaceholder',
          label: '搜索提示',
          type: 'text',
          defaultValue: '搜索验收单名称'
        }
      ],
      capabilities: {
        layout: 'acceptance-board',
        reusesPlatformActions: true,
        reusesPlatformPagination: true
      }
    }
  )

  registerCustomListComponent(
    PROJECT_ACCEPTANCE_SCHEMA_LIST,
    ProjectAcceptanceBoardList,
    {
      label: '项目·后端 Schema 扩展列表',
      description: '由后端 EntityListSchemaProvider 增强结构后使用项目看板渲染。',
      version: 1,
      snapshotVersion: 1,
      configSchema: [
        {
          key: 'showProviderTrace',
          label: '显示 Provider 轨迹',
          type: 'boolean',
          defaultValue: true
        },
        {
          key: 'searchPlaceholder',
          label: '搜索提示',
          type: 'text',
          defaultValue: '搜索 Schema 扩展列表'
        }
      ],
      capabilities: {
        backendSchemaProvider: true,
        reusesPlatformActions: true,
        reusesPlatformPagination: true
      }
    }
  )

  registerFormFieldComponent(
    PROJECT_ACCEPTANCE_SCORE_FIELD,
    ProjectAcceptanceScoreField,
    {
      label: '项目·验收评分',
      description: '用滑块、等级和实时结果展示自定义字段组件。',
      version: 1,
      supportedFieldTypes: ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'],
      configSchema: [
        {
          key: 'passScore',
          label: '通过分数',
          type: 'number',
          min: 0,
          max: 100,
          defaultValue: 60
        }
      ],
      capabilities: {
        emitsChange: true,
        supportsReadonly: true
      }
    }
  )

  registerFormFieldComponent(
    PROJECT_ACCEPTANCE_LEVEL_FIELD,
    ProjectAcceptanceLevelField,
    {
      label: '项目·复核级别',
      description: '从节点级统一数据源加载选项和默认值。',
      version: 1,
      supportedFieldTypes: ['SELECT', 'RADIO', 'STRING'],
      capabilities: {
        executesManagedDataSource: true,
        supportsReadonly: true
      }
    }
  )

  registerFormNodeComponent(
    PROJECT_ACCEPTANCE_SUMMARY_NODE,
    ProjectAcceptanceSummaryNode,
    {
      label: '项目·验收摘要节点',
      description: '读取整表单上下文并演示节点级统一数据源调用。',
      version: 1,
      snapshotVersion: 1,
      nodeTypes: ['FIELD'],
      supportedBindings: ['ENTITY_FIELD'],
      configSchema: [
        {
          key: 'title',
          label: '摘要标题',
          type: 'text',
          defaultValue: '扩展执行摘要'
        }
      ],
      capabilities: {
        readsWholeForm: true,
        executesManagedDataSource: true
      }
    }
  )

  registerCellComponent(
    PROJECT_ACCEPTANCE_SCORE_CELL,
    ProjectAcceptanceScoreCell,
    {
      label: '项目·验收评分单元格',
      description: '在标准列表中以进度条和等级展示验收评分。',
      version: 1,
      supportedFieldTypes: ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'],
      configSchema: [
        {
          key: 'passScore',
          label: '通过分数',
          type: 'number',
          min: 0,
          max: 100,
          defaultValue: 60
        }
      ]
    }
  )

  registerListButtonComponent(
    PROJECT_ACCEPTANCE_INSPECT_BUTTON,
    ProjectAcceptanceInspectButton
  )
  registerListToolbarAction(
    PROJECT_ACCEPTANCE_TOOLBAR_ACTION,
    projectAcceptanceToolbarAction
  )
  registerListRowAction(
    PROJECT_ACCEPTANCE_ROW_ACTION,
    projectAcceptanceRowAction
  )
  registerListToolbarAction(
    PROJECT_ACCEPTANCE_SELECTION_ACTION,
    projectAcceptanceSelectionAction
  )
  registerListRowAction(
    PROJECT_ACCEPTANCE_SELECTION_ACTION,
    projectAcceptanceSelectionAction
  )
  registerFormInitializer(
    PROJECT_ACCEPTANCE_INITIALIZER,
    projectAcceptanceInitializer
  )
  registerEntityActionRuleCondition({
    type: PROJECT_ACCEPTANCE_RULE_CONDITION,
    label: '项目扩展字段条件',
    component: ProjectAcceptanceRuleCondition,
    createDefault: () => ({
      field: 'acceptance_scene',
      operator: 'EQ',
      value: 'FULL_EXTENSION'
    })
  })
  registerEntityPermissionOptionProvider(({ entityCode }) => {
    if (!entityCode) return []
    return [{
      code: `entity:${String(entityCode).toLowerCase()}:custom:project-review`,
      label: '项目复核',
      description: '由项目模块前后端扩展共同提供的按钮权限。',
      category: 'PROJECT_CUSTOM'
    }]
  })
}
