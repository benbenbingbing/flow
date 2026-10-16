/** 为当前实体提供业务权限候选；实际授权由服务端执行。 */
export default ({ entityCode }) => entityCode ? [{
    code: `entity:${String(entityCode).toLowerCase()}:custom:contract-example`,
    label: '契约示例权限', description: '需要对应后端权限实现', category: 'CUSTOM'
  }] : []
