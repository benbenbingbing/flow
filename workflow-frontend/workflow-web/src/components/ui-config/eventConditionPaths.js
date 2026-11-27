import { formLifecycleEvents } from './formLifecycleEventModel.js'

const formModeEvents = ['FORM_OPEN', 'FORM_RESET', 'FORM_CLOSE', 'FORM_BUTTON_CLICK', ...formLifecycleEvents]
const fieldEvents = ['FIELD_CHANGE', 'FIELD_BUTTON_CLICK', 'ENTITY_SELECTED']
const dataMutationEvents = ['DATA_CREATE', 'DATA_UPDATE']
const listButtonEvents = ['ROW_BUTTON_CLICK', 'TOOLBAR_BUTTON_CLICK']

// 列表事件使用后端重建的行 DTO，系统属性在行根部，业务字段在 data 中。
const rowProperties = new Set([
  'name', 'code', 'status', 'processStatus', 'processInstanceId', 'processStartTime', 'processEndTime',
  'currentTaskId', 'currentTaskName', 'currentTaskAssignee', 'submitterId', 'submitterName', 'submitTime',
  'deptId', 'deptName', 'create_time', 'update_time', 'create_by', 'update_by', 'deleted'
])

const option = (label, value) => ({ label, value })

/** 按事件输入结构生成执行条件和参数映射的来源选项。接口自定义结构仍可用手写路径访问。 */
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
    if (event === 'FORM_CLOSE') {
      current.push(option('关闭原因', 'input.reason'), option('保存后的记录 ID', 'input.savedRecordId'))
    }
    if (fieldEvent) current.push(option('触发字段的值', 'input.value'))
    if (event === 'ENTITY_SELECTED') current.push(option('所选实体记录 ID', 'selection.id'))
    const prefix = lifecycle ? 'data' : 'input.form'
    if (event !== 'FORM_CLOSE') {
      fieldsGroup = { label: '表单字段', options: fields.map(field => option(field.label, `${prefix}.${field.value}`)) }
    }
  } else if (dataMutationEvents.includes(event)) {
    // 新增和修改数据事件使用提交请求的 data，表单交互事件才使用当前页面的 form。
    if (event === 'DATA_UPDATE') current.push(option('当前记录 ID', 'recordId'))
    fieldsGroup = { label: '提交字段', options: fields.map(field => option(field.label, `input.data.${field.value}`)) }
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
  if (fieldsGroup) groups.push(fieldsGroup)
  // 正式提交以服务端收到的表单值为准，通过字段传递需要参与提交的参数。
  if (event !== 'BEFORE_SUBMIT' && (formMode || fieldEvent || listButton || listLoad)) {
    const prefix = lifecycle ? 'params' : 'input.params'
    groups.push({
      label: formMode || fieldEvent ? '表单参数' : '页面参数',
      options: parameters.map(item => option(item.name || item.code, `${prefix}.${item.code}`))
    })
  }
  return groups.filter(group => group.options.length > 0)
}
