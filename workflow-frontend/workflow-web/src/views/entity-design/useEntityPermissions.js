
import { showRequestError } from '@/shared/request'
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityListScopeRuleApi } from '@/api/entityListScopeRule'
import { entityListConfigApi } from '@/api/entityListConfig'
import { getEntityStatusList } from '@/api/entityStatus'
import { getEnabledRoles } from '@/api/system/role'
import { getEnabledOrgList } from '@/api/system/org'
import { getEnabledGroups } from '@/api/system/group'
import { WORKFLOW_SYSTEM_FIELD_CODES } from '@/shared/entity-design'

/**
 * 独立管理权限规则草稿、目录和 SQL 预览；不参与实体字段保存。
 * 规则接口仍由用户显式操作触发；父级只提供实体身份和可用字段，切换页签不会重建草稿。
 */
export function useEntityPermissions({ entityId, entityData, isSystemEntity, isWorkflowEntityMode, fields }) {
  // 数据权限配置
  const permissionLoading = ref(false)

  const permissionList = ref([])

  const permissionError = ref('')

  const permissionEditVisible = ref(false)

  const permissionForm = ref(createEmptyPermissionForm())

  const availableStatuses = ref([])

  const availableListConfigs = ref([])

  const permissionSqlPreview = ref(null)

  const permissionSqlPreviewVisible = ref(false)

  const permissionSqlPreviewTitle = ref('权限 SQL 预览')

  const permissionPreviewRule = ref(null)

  const simulationUserId = ref('')

  const permissionPreviewLoading = ref(false)

  const permissionPreviewError = ref('')

  let permissionPreviewRequestId = 0

  const roleOptions = ref([])

  const groupOptions = ref([])

  const deptOptions = ref([])

  const organizationOptions = ref([])

  const permissionSystemFields = computed(() => [
    { label: '数据名称', value: 'name' },
    { label: '数据编码', value: 'code' },
    { label: '状态', value: 'status' },
    { label: '创建人', value: 'create_by' },
    { label: '提交人', value: 'submitterId' },
    { label: '所属部门', value: 'deptId' },
    { label: '流程实例', value: 'processInstanceId' },
    { label: '当前办理人', value: 'currentTaskAssignee' },
    { label: '创建时间', value: 'create_time' },
    { label: '更新时间', value: 'update_time' }
  ].filter(item => isWorkflowEntityMode.value || !WORKFLOW_SYSTEM_FIELD_CODES.has(item.value)))

  const permissionRuleFieldOptions = computed(() => [
    ...permissionSystemFields.value,
    ...(fields.value || [])
      .filter(field => field.fieldCode && !['SUB_FORM', 'SUB_LIST'].includes(field.fieldType))
      .filter(field => !permissionSystemFields.value.some(item => item.value === field.fieldCode))
      .map(field => ({
        label: `${field.fieldName} (${field.fieldCode})`,
        value: field.fieldCode
      }))
  ])

  const loadSelectorOptions = async () => {
    try {
      const [roles, groups, orgs] = await Promise.all([
        getEnabledRoles().catch(() => []),
        getEnabledGroups().catch(() => []),
        getEnabledOrgList().catch(() => [])
      ])
      roleOptions.value = (roles || []).map(r => ({ label: r.roleName || r.roleCode, value: r.id }))
      groupOptions.value = (groups || []).map(group => ({ label: group.groupName || group.groupCode, value: group.id }))
      deptOptions.value = (orgs || [])
        .filter(org => String(org.type || '').toLowerCase() === 'dept')
        .map(org => ({ label: org.orgName, value: org.id }))
      organizationOptions.value = (orgs || [])
        .filter(org => String(org.type || '').toLowerCase() === 'org')
        .map(org => ({ label: org.orgName, value: org.id }))
    } catch (error) {
      console.error('加载选择数据失败:', error)
    }
  }

  function createEmptyPermissionForm() {
    return {
      id: null,
      policyId: null,
      policyKey: '',
      entityCode: '',
      ruleName: '',
      enabled: 1,
      listKey: '',
      ruleEffect: 'ALLOW',
      matchLogic: 'OR',
      matchConditions: [{
        scopeType: 'ALL_USERS',
        targetIds: [],
        operator: 'ANY',
        includeSubDept: false,
        sql: ''
      }],
      matchRoot: null,
      filterType: 'PERSONAL',
      filterSql: '',
      filterRoot: null,
      legacyUnsafeConfig: false,
      fieldMapping: { userField: 'create_by', deptField: 'dept_id', statusField: 'status' },
      statusLimit: { enabled: false, mode: 'IN', values: [] }
    }
  }

  // ============ 数据权限方法 ============
  /** 读取规则目录及列表名称，供规则表和绑定摘要使用；失败时保留重试入口。 */
  const loadPermissions = async () => {
    if (!entityData.value.entityCode || isSystemEntity.value) return
    permissionLoading.value = true
    permissionError.value = ''
    try {
      const [permissionData, listConfigData] = await Promise.all([
        entityListScopeRuleApi.getByEntityCode(entityData.value.entityCode),
        entityListConfigApi.getByEntityId(entityId)
      ])
      availableListConfigs.value = listConfigData || []
      permissionList.value = (permissionData || []).map(item => {
        const match = parseJson(item.matchConfig, { logic: 'OR', conditions: [] })
        const filter = parseJson(item.filterConfig, { type: 'PERSONAL', fieldMapping: {}, statusLimit: {}, root: null })
        const legacyUnsafeConfig = (match.conditions || []).some(condition => condition.scopeType === 'EXPRESSION')
          || ['EXPRESSION', 'CUSTOM_SQL'].includes(filter.type)
        return {
          ...item,
          boundListKeys: item.boundListKeys || [],
          ruleEffect: item.ruleEffect || filter.ruleEffect || 'ALLOW',
          matchLogic: match.logic || 'OR',
          matchConditions: (match.conditions || []).map(c => ({
            ...c,
            targetIds: Array.isArray(c.targetIds) ? c.targetIds.map(id => String(id)) : [],
            sql: c.sql || ''
          })),
          matchRoot: match.root || null,
          filterType: ['EXPRESSION', 'CUSTOM_SQL'].includes(filter.type) ? 'PERSONAL' : (filter.type || 'PERSONAL'),
          filterSql: filter.sql || filter.customSql || '',
          filterRoot: filter.root || null,
          legacyUnsafeConfig,
          fieldMapping: filter.fieldMapping || { userField: 'create_by', deptField: 'dept_id', statusField: 'status' },
          statusLimit: filter.statusLimit || { enabled: false, mode: 'IN', values: [] }
        }
      })
    } catch (error) {
      console.error('加载权限规则失败:', error)
      permissionError.value = error?.message || '无法读取规则目录，请检查权限或稍后重试。'
    } finally {
      permissionLoading.value = false
    }
  }

  const loadAvailableStatuses = async () => {
    if (!entityData.value.entityCode) return
    try {
      const data = await getEntityStatusList(entityData.value.entityCode)
      availableStatuses.value = data || []
    } catch (error) {
      console.error('加载状态列表失败:', error)
    }
  }

  const parseJson = (str, defaultVal) => {
    if (!str) return defaultVal
    try {
      return JSON.parse(str)
    } catch (e) {
      return defaultVal
    }
  }

  const handleAddPermission = () => {
    permissionForm.value = createEmptyPermissionForm()
    permissionForm.value.entityCode = entityData.value.entityCode
    permissionEditVisible.value = true
    loadAvailableStatuses()
    loadSelectorOptions()
  }

  const handleEditPermission = (row) => {
    permissionForm.value = cloneValue(row)
    if (permissionForm.value.legacyUnsafeConfig) {
      ElMessage.warning('该规则包含已废弃的表达式或自定义 SQL，保存前请改为结构化条件')
    }
    permissionEditVisible.value = true
    loadAvailableStatuses()
    loadSelectorOptions()
  }

  const handleDeletePermission = async (row) => {
    try {
      await ElMessageBox.confirm(
        `删除规则「${row.ruleName}」后，已绑定该规则的列表将无法再引用它。若仍有列表绑定，请先到列表设置中解绑。`,
        '删除权限规则草稿',
        { type: 'warning', confirmButtonText: '确认删除' }
      )
      await entityListScopeRuleApi.delete(row)
      ElMessage.success('规则草稿已删除')
      loadPermissions()
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      console.error(error)
      showRequestError(error, '删除失败')
    }
  }

  const togglePermission = async (row) => {
    const previousEnabled = row.enabled === 1 ? 0 : 1
    try {
      const action = row.enabled === 1 ? '启用' : '停用'
      await ElMessageBox.confirm(
        `${action}规则「${row.ruleName}」会改变下一次发布的权限结果，当前已发布版本不受影响。`,
        `${action}权限规则草稿`,
        { type: row.enabled === 1 ? 'info' : 'warning', confirmButtonText: `确认${action}` }
      )
      await entityListScopeRuleApi.updateEnabled(row)
      ElMessage.success(`规则草稿已${action}`)
    } catch (error) {
      row.enabled = previousEnabled
    }
  }

  const addMatchCondition = () => {
    permissionForm.value.matchConditions.push({
      scopeType: 'ROLE',
      targetIds: [],
      operator: 'ANY',
      includeSubDept: false,
      sql: ''
    })
  }

  const permissionSqlPlaceholders = [
    { token: '#{userId}', label: '当前用户ID' },
    { token: '#{username}', label: '当前用户名' },
    { token: '#{deptId}', label: '当前部门ID' },
    { token: '#{orgId}', label: '当前组织ID' }
  ]

  const appendPermissionSql = (target, token) => {
    if (target === 'filter') {
      permissionForm.value.filterSql = `${permissionForm.value.filterSql || ''}${token}`
      return
    }
    target.sql = `${target.sql || ''}${token}`
  }

  const permissionSqlLooksUnsafe = (sql) => {
    const text = String(sql || '')
    return text.includes(';') || text.includes('--') || text.includes('/*') || text.includes('*/')
  }

  const removeMatchCondition = (index) => {
    permissionForm.value.matchConditions.splice(index, 1)
  }

  const formatMatchSummary = (row) => {
    const conditions = row.matchConditions || []
    if (!conditions.length) return '-'
    const parts = conditions.map(c => {
      const map = {
        ALL_USERS: '全部用户',
        USER: '指定用户',
        ROLE: '指定角色',
        GROUP: '指定用户组',
        DEPT: '指定部门',
        ORG: '指定组织',
        SQL: '自定义 SQL',
        EXPRESSION: '已废弃表达式'
      }
      return map[c.scopeType] || c.scopeType
    })
    const logic = row.matchLogic === 'AND' ? ' 且 ' : ' 或 '
    return parts.join(logic)
  }

  const getFilterTypeTag = (type) => {
    const tags = {
      ALL: 'success',
      PERSONAL: '',
      SUBMITTER: '',
      CURRENT_ASSIGNEE: 'primary',
      HAS_TODO: 'primary',
      TEAM: 'success',
      DEPT: 'warning',
      DEPT_TREE: 'warning',
      RULE: 'info',
      SQL: 'danger'
    }
    return tags[type] || ''
  }

  const getFilterTypeLabel = (type) => {
    const labels = {
      ALL: '全部数据',
      PERSONAL: '创建人是当前用户',
      SUBMITTER: '提交人是当前用户',
      CURRENT_ASSIGNEE: '当前办理人（实体字段）',
      HAS_TODO: '存在待办',
      TEAM: '相关人（参与过该记录）',
      DEPT: '本部门',
      DEPT_TREE: '本部门及子部门',
      RULE: '结构化条件组',
      SQL: '自定义 SQL',
      EXPRESSION: '已废弃表达式',
      CUSTOM_SQL: '已废弃自定义 SQL'
    }
    return labels[type] || type
  }

  const formatBoundLists = (listKeys) => {
    const names = (listKeys || []).map(getListConfigName).filter(Boolean)
    return names.length ? names.join('、') : '未绑定'
  }

  const getListConfigName = (listKey) => {
    if (!listKey) return ''
    const config = availableListConfigs.value.find(c => c.listKey === listKey)
    return config?.listName || config?.listKey || listKey
  }

  /** 按当前行规则模拟，禁止回退到默认列表，避免展示其他规则或范围绕过的结果。 */
  const handlePreviewPermissionSql = async (rule) => {
    permissionPreviewRule.value = rule
    permissionSqlPreviewTitle.value = `规则模拟：${rule.ruleName}`
    permissionSqlPreviewVisible.value = true
    await loadPermissionPreview()
  }

  /** 切换模拟人员后重新编译；只接受最后一次请求，避免较慢的旧用户结果覆盖当前选择。 */
  const loadPermissionPreview = async () => {
    const rule = permissionPreviewRule.value
    const policyId = rule?.policyId || rule?.id
    if (!policyId) return
    const requestId = ++permissionPreviewRequestId
    permissionPreviewLoading.value = true
    permissionPreviewError.value = ''
    permissionSqlPreview.value = null
    try {
      const preview = await entityListScopeRuleApi.previewSql(policyId, simulationUserId.value)
      if (requestId !== permissionPreviewRequestId) return
      if (!preview || typeof preview.sql !== 'string') {
        throw new Error('规则模拟未返回 SQL')
      }
      permissionSqlPreview.value = preview
      permissionSqlPreviewTitle.value = `规则模拟：${preview.ruleName || rule.ruleName}`
    } catch (error) {
      if (requestId !== permissionPreviewRequestId) return
      console.error('预览权限 SQL 失败:', error)
      permissionPreviewError.value = error?.message || '无法读取规则模拟结果，请稍后重试。'
    } finally {
      if (requestId === permissionPreviewRequestId) permissionPreviewLoading.value = false
    }
  }

  const createPermissionFilterRoot = () => {
    permissionForm.value.filterRoot = {
      type: 'GROUP',
      logic: 'AND',
      children: [{
        type: 'RELATION',
        relation: 'CURRENT_USER_IS_CREATOR'
      }]
    }
  }

  /** 校验适用对象与数据范围后保存规则；成功才关闭编辑器并重新读取规则目录。 */
  const savePermission = async () => {
    const form = permissionForm.value
    if (!form.ruleName) {
      ElMessage.warning('请输入规则名称')
      return
    }

    if (!form.matchConditions?.length && !form.matchRoot) {
      ElMessage.warning('请至少配置一个适用用户条件')
      return
    }
    const invalidMatch = (form.matchConditions || []).find(condition => {
      if (condition.scopeType === 'ALL_USERS') return false
      if (condition.scopeType === 'SQL') return !String(condition.sql || '').trim()
      return !condition.targetIds || condition.targetIds.length === 0
    })
    if (invalidMatch) {
      ElMessage.warning(invalidMatch.scopeType === 'SQL'
        ? '请填写适用对象 SQL'
        : '指定用户、角色、用户组、部门或组织时必须选择目标')
      return
    }
    const unsafeMatch = (form.matchConditions || []).find(condition =>
      condition.scopeType === 'SQL' && permissionSqlLooksUnsafe(condition.sql)
    )
    if (unsafeMatch) {
      ElMessage.warning('适用对象 SQL 不能包含分号或注释')
      return
    }
    if (form.filterType === 'RULE' && (!form.filterRoot?.children?.length)) {
      ElMessage.warning('结构化条件组不能为空')
      return
    }
    if (form.filterType === 'SQL' && !String(form.filterSql || '').trim()) {
      ElMessage.warning('请填写数据范围 SQL')
      return
    }
    if (form.filterType === 'SQL' && permissionSqlLooksUnsafe(form.filterSql)) {
      ElMessage.warning('数据范围 SQL 不能包含分号或注释')
      return
    }

    // 处理 matchConditions 中的 targetIds
    const matchConditions = (form.matchConditions || []).map(c => ({
      scopeType: c.scopeType,
      targetIds: Array.isArray(c.targetIds) ? c.targetIds.map(id => String(id)).filter(Boolean) : [],
      operator: c.operator,
      includeSubDept: c.includeSubDept,
      sql: c.scopeType === 'SQL' ? String(c.sql || '').trim() : undefined
    }))

    const matchConfig = JSON.stringify({
      version: 1,
      logic: form.matchLogic,
      conditions: matchConditions,
      root: form.matchRoot || null
    })

    const filterConfig = JSON.stringify({
      version: 1,
      type: form.filterType,
      root: form.filterType === 'RULE' ? form.filterRoot : null,
      sql: form.filterType === 'SQL' ? String(form.filterSql || '').trim() : undefined,
      fieldMapping: form.fieldMapping,
      statusLimit: form.statusLimit,
      ruleEffect: form.ruleEffect || 'ALLOW',
      audience: parseJson(matchConfig, {})
    })

    const payload = {
      entityCode: form.entityCode || entityData.value.entityCode,
      policyId: form.policyId,
      policyKey: form.policyKey || `scope_${Date.now()}`,
      ruleName: form.ruleName,
      enabled: form.enabled,
      ruleEffect: form.ruleEffect || 'ALLOW',
      filterType: form.filterType,
      matchConfig,
      filterConfig
    }

    try {
      if (form.id) {
        await entityListScopeRuleApi.update(form.id, payload)
      } else {
        await entityListScopeRuleApi.create(payload)
      }
      ElMessage.success('规则草稿已保存')
      permissionEditVisible.value = false
      loadPermissions()
    } catch (error) {
      console.error(error)
      showRequestError(error, '保存失败')
    }
  }

  const cloneValue = (value) => JSON.parse(JSON.stringify(value))

  return {
    permissionLoading,
    permissionList,
    permissionError,
    permissionEditVisible,
    permissionForm,
    availableStatuses,
    permissionSqlPreview,
    permissionSqlPreviewVisible,
    permissionSqlPreviewTitle,
    simulationUserId,
    permissionPreviewLoading,
    permissionPreviewError,
    roleOptions,
    groupOptions,
    deptOptions,
    organizationOptions,
    permissionRuleFieldOptions,
    loadPermissions,
    handleAddPermission,
    handleEditPermission,
    handleDeletePermission,
    togglePermission,
    addMatchCondition,
    permissionSqlPlaceholders,
    appendPermissionSql,
    removeMatchCondition,
    formatMatchSummary,
    getFilterTypeTag,
    getFilterTypeLabel,
    formatBoundLists,
    handlePreviewPermissionSql,
    loadPermissionPreview,
    createPermissionFilterRoot,
    savePermission
  }
}
