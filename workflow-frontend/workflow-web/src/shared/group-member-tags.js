/** 设计器保留“用户组”交互，持久化为统一解析器；不改写无标签的历史组规则。 */
export const GROUP_TAG_RESOLVER_CODE = 'groupMembersByTag'

export function hasGroupTagFilter(form = {}) {
  return form.assigneeType === 'group' && (form.candidateGroupIds || []).some(code => (form.groupTags?.[code] || []).length > 0)
}

/** 标签按组保存；未筛选的其他组也必须参与并集，不能因启用解析器被漏掉。 */
export function projectGroupTagAssignment(form) {
  if (form.assigneeType !== 'group') return form
  if (!hasGroupTagFilter(form)) {
    return { ...form, resolverCode: '', interfaceName: '', extraParams: {}, extraParamsText: '{}', assignmentMode: '' }
  }
  const groups = (form.candidateGroupIds || []).map(groupCode => ({
    groupCode, tagIds: [...new Set(form.groupTags?.[groupCode] || [])]
  }))
  const extraParams = { groups }
  return { ...form, assigneeType: 'interface', assigneeValue: '', candidateUsers: '', candidateGroups: '',
    resolverCode: GROUP_TAG_RESOLVER_CODE, interfaceName: GROUP_TAG_RESOLVER_CODE,
    resolverDisplayName: '用户组成员标签', assignmentMode: 'CANDIDATE', extraParams,
    extraParamsText: JSON.stringify(extraParams), assignmentConfigDirty: true }
}

/** 按稳定 ID 回显已部署条件；不根据当前标签目录过滤，否则失效条件会变成全组。 */
export function restoreGroupTagAssignment(config = {}) {
  if ((config.resolverCode || config.interfaceName) !== GROUP_TAG_RESOLVER_CODE) return config
  const groups = config.extraParams?.groups
  if (!Array.isArray(groups) || !groups.length || groups.some(row => !row?.groupCode || !Array.isArray(row.tagIds))) {
    throw new Error('用户组标签配置格式非法，请修正配置后再保存')
  }
  const codes = groups.map(row => row.groupCode)
  return { ...config, assigneeType: 'group', assigneeValue: codes.join(','), candidateGroups: codes.join(','),
    groupTags: Object.fromEntries(groups.map(row => [row.groupCode, [...row.tagIds]])) }
}
