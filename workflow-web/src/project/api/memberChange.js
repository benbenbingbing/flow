import { entityDataApi } from '@/api/entity.js'
import {
  normalizeRecords,
  summarizeAllocation
} from '../memberChangeModel.js'

export async function loadMemberChangeContext({
  projectId,
  memberId,
  targetUserId,
  requestedAllocation
}) {
  let member = null
  let roles = []
  if (memberId) {
    member = await entityDataApi.getDetail(
      'project_member',
      memberId
    )
    roles = normalizeRecords(await entityDataApi.getList(
      'project_role_assignment',
      {
        project_id: projectId,
        member_id: memberId,
        pageNum: 1,
        pageSize: 200
      }
    )).filter(item => item?.status === 'ACTIVE')
  }
  const effectiveUserId =
    targetUserId || member?.data?.user_id || ''
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
    accountRequired:
      Boolean(member?.data?.account_required_flag),
    environmentAccessRequired:
      Boolean(member?.data?.environment_access_required_flag),
    memberProjectMismatch: Boolean(
      member
      && projectId
      && String(member?.data?.project_id || '') !== String(projectId)
    ),
    allocation: summarizeAllocation(
      allocationRecords,
      memberId,
      requestedAllocation
    )
  }
}
