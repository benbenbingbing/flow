import { normalizeGroupMemberIds } from '../../shared/group-management.js'

/**
 * 创建用户组 API。传输层可注入，以便在不发起网络请求的情况下验证完整接口契约。
 */
export function createGroupApi(transport) {
  if (!transport?.get || !transport?.post) {
    throw new TypeError('用户组 API 需要可用的 get/post 传输层')
  }

  return {
    getGroupList: () => transport.get('/system/group/list'),
    getEnabledGroups: () => transport.get('/system/group/enabled'),
    getGroupById: id => transport.get(groupPath(id)),
    createGroup: data => transport.post('/system/group', requireGroupPayload(data)),
    updateGroup: (id, data) => transport.post(
      `${groupPath(id)}/update`,
      requireGroupPayload(data)
    ),
    deleteGroup: id => transport.post(`${groupPath(id)}/delete`),
    updateGroupStatus: (id, status) => transport.post(
      `${groupPath(id)}/status`,
      { status: normalizeGroupStatus(status) }
    ),
    saveGroupUsers: (id, userIds) => {
      // 只有显式空数组才代表清空成员，避免调用方漏传参数时误删全部关系。
      if (!Array.isArray(userIds)) {
        throw new TypeError('用户组成员必须是数组，清空成员请显式传空数组')
      }
      return transport.post(
        `${groupPath(id)}/users`,
        normalizeGroupMemberIds(userIds)
      )
    },
    getUsers: () => transport.get('/system/group/users')
  }
}

/** 路径参数必须作为单个 URL segment 编码，避免特殊字符改变路由含义。 */
export function encodeGroupId(id) {
  const value = id == null ? '' : String(id).trim()
  if (!value) throw new TypeError('用户组 ID 不能为空')
  return encodeURIComponent(value)
}

function groupPath(id) {
  return `/system/group/${encodeGroupId(id)}`
}

/** 新增和更新只接受 JSON 对象，阻断空字符串等错误请求体进入后端。 */
function requireGroupPayload(data) {
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    throw new TypeError('用户组请求体必须是对象')
  }
  return data
}

/** 前端状态契约与数据库约定保持一致，尽早拒绝未知状态。 */
function normalizeGroupStatus(status) {
  const value = status == null ? '' : String(status).trim()
  if (value !== '0' && value !== '1') {
    throw new TypeError('用户组状态只能为0（启用）或1（禁用）')
  }
  return value
}
