import {
  normalizeRecords,
  summarizeAllocation
} from '../shared/memberChangeModel.js'

/**
 * 创建项目成员变更服务。entityDataApi 必须由宿主提供，以复用当前身份、权限和请求错误处理。
 * 这里只封装项目业务查询，不能自行创建 HTTP 客户端或让通用表单 services 依赖项目实体。
 * @throws {TypeError} 宿主缺少按实体查询详情或列表的能力时抛出。
 */
export function createMemberChangeApi(entityDataApi) {
  if (typeof entityDataApi?.getDetail !== 'function' || typeof entityDataApi?.getList !== 'function') {
    throw new TypeError('项目成员变更扩展需要宿主提供 entityDataApi.getDetail/getList')
  }

  /**
   * 查询待变更成员、有效角色和跨项目投入，返回两端共同用于校验及审批路由的上下文。
   * JOIN 传 targetUserId，其余操作传 memberId；requestedAllocation 是此次变更后的投入比例。
   * 任一权限或网络错误直接向上传递，由表单展示失败状态，不能当作成功的空上下文。
   */
  async function loadMemberChangeContext({ projectId, memberId, targetUserId, requestedAllocation }) {
    let member = null
    let roles = []
    if (memberId) {
      // 使用带实体编码的详情接口，保留宿主对 project_member 的权限与租户范围控制。
      member = await entityDataApi.getDetail('project_member', memberId)
      roles = normalizeRecords(await entityDataApi.getList('project_role_assignment', {
        project_id: projectId,
        member_id: memberId,
        pageNum: 1,
        pageSize: 200
      })).filter(item => item?.status === 'ACTIVE')
    }
    const effectiveUserId = targetUserId || member?.data?.user_id || ''
    const allocationRecords = effectiveUserId
      ? await entityDataApi.getList('project_member', {
        user_id: effectiveUserId,
        pageNum: 1,
        pageSize: 200
      })
      : []
    return {
      member,
      activeRoleCount: roles.length,
      activeRoles: roles,
      accountRequired: Boolean(member?.data?.account_required_flag),
      environmentAccessRequired: Boolean(member?.data?.environment_access_required_flag),
      memberProjectMismatch: Boolean(
        member && projectId && String(member?.data?.project_id || '') !== String(projectId)
      ),
      // 调整投入时先排除原成员记录，再加新投入，避免同一项目重复计入。
      allocation: summarizeAllocation(allocationRecords, memberId, requestedAllocation)
    }
  }

  return { loadMemberChangeContext }
}
