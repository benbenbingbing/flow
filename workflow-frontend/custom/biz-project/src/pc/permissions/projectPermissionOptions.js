/** 为当前实体提供业务权限候选；实际授权由服务端执行。 */
export default ({ entityCode }) => {
    if (!entityCode) return []
    return [{
      code: `entity:${String(entityCode).toLowerCase()}:custom:project-review`,
      label: '项目复核',
      description: '由项目模块前后端扩展共同提供的按钮权限。',
      category: 'PROJECT_CUSTOM'
    }]
  }
