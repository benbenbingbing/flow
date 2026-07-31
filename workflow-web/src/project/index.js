import {
  registerCustomFormComponent
} from '@/utils/customComponentRegistry'
import ProjectMemberChangeForm from './forms/ProjectMemberChangeForm.vue'

export const PROJECT_MEMBER_CHANGE_FORM =
  'ProjectMemberChangeForm'

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
}
