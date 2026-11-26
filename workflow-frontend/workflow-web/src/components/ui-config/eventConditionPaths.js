import { formLifecycleEvents } from './formLifecycleEventModel.js'

const formModeEvents = ['FORM_OPEN', 'FORM_RESET', 'FORM_BUTTON_CLICK', ...formLifecycleEvents]
const fieldEvents = ['FIELD_CHANGE', 'FIELD_BUTTON_CLICK', 'ENTITY_SELECTED']
const listButtonEvents = ['ROW_BUTTON_CLICK', 'TOOLBAR_BUTTON_CLICK']

// 列表事件使用后端重建的行 DTO，系统属性在行根部，业务字段在 data 中。
const rowProperties = new Set([
  'name', 'code', 'status', 'processStatus', 'processInstanceId', 'processStartTime', 'processEndTime',
  'currentTaskId', 'currentTaskName', 'currentTaskAssignee', 'submitterId', 'submitterName', 'submitTime',
  'deptId', 'deptName', 'create_time', 'update_time', 'create_by', 'update_by', 'deleted'
])

const option = (label, value) => ({ label, value })

/** 返回当前事件可识别的条件来源。接口自定义结构仍可用手写路径访问。 */
export function eventConditionPathGroups(event, fields, parameters) {
  const formMode = formModeEvents.includes(event)
  const lifecycle = formLifecycleEvents.includes(event)
  const fieldEvent = fieldEvents.includes(event)
  const listButton = listButtonEvents.includes(event)
  const listLoad = event === 'LIST_LOAD'
  const current = []
  let fieldsGroup

  if (formMode || fieldEvent) {
    current.push(option('当前记录 ID', lifecycle ? 'input.recordId' : 'recordId'))
    if (formMode) current.push(option(event === 'FORM_OPEN' ? '打开方式' : '表单模式', 'input.mode'))
    if (fieldEvent) current.push(option('触发字段的值', 'input.value'))
    if (event === 'ENTITY_SELECTED') current.push(option('所选实体记录 ID', 'selection.id'))
    const prefix = lifecycle ? 'data' : 'input.form'
    fieldsGroup = { label: '表单字段', options: fields.map(field => option(field.label, `${prefix}.${field.value}`)) }
  } else if (listButton) {
    const row = event === 'ROW_BUTTON_CLICK'
    const prefix = row ? 'input.row' : 'input.selectedRows.0'
    current.push(option(row ? '当前行记录 ID' : '首条选中记录 ID', `${prefix}.id`))
    fieldsGroup = {
      label: row ? '当前行字段' : '首条选中记录字段',
      options: fields.filter(field => field.value !== 'id').map(field =>
        option(field.label, `${prefix}.${rowProperties.has(field.value) ? '' : 'data.'}${field.value}`))
    }
  } else if (listLoad) {
    current.push(option('页码', 'input.pageNum'), option('每页条数', 'input.pageSize'))
  }

  const groups = [{ label: '当前事件', options: current }]
  // 正式提交以服务端收到的表单值为准，页面参数须先回填到字段才能用于提交条件。
  if (event !== 'BEFORE_SUBMIT' && (formMode || fieldEvent || listButton || listLoad)) {
    const prefix = lifecycle ? 'params' : 'input.params'
    groups.push({ label: '页面参数', options: parameters.map(item => option(item.name || item.code, `${prefix}.${item.code}`)) })
  }
  if (fieldsGroup) groups.push(fieldsGroup)
  return groups.filter(group => group.options.length > 0)
}
