// 与 EntityListRuntimeService / EntityDataActionService 以及前端事件触发点保持一致。
// 业务入口和统一事件入口含义不同：后者只执行扩展步骤，不会自动查询或保存实体。
const entityDataPath = '/api/entity-data/entity/{entityCode}'
const detailPath = `${entityDataPath}/detail/{id}`
const createEndpoint = { label: '新增数据', method: 'POST', path: '/api/entity-data' }
const updateEndpoint = { label: '修改数据', method: 'POST', path: `${detailPath}/update` }

const platformDefaultDescriptions = {
  LIST_LOAD: {
    name: '加载列表',
    description: '按已发布列表配置、固定条件、用户筛选和数据范围分页查询记录，返回 records、total、pageNum、pageSize。',
    endpoints: [{ label: '列表查询入口', method: 'POST', path: '/api/entity-lists/{entityCode}/{listKey}/query' }],
    replaceable: true,
    note: '自定义查询通过“替代平台处理”步骤配置，并返回上述分页结构；前端仍调用同一个 query 入口。'
  },
  DETAIL_LOAD: {
    name: '加载详情',
    description: '校验记录访问范围和查看权限，读取指定记录的详情，供表单或详情页展示。',
    endpoints: [{ label: '详情加载入口', method: 'POST', path: `${detailPath}/load` }],
    replaceable: true,
    note: '执行事件需携带表单或列表的发布上下文。GET 详情接口仅做读取，不执行 DETAIL_LOAD 事件链。'
  },
  DATA_CREATE: {
    name: '新增数据',
    description: '校验新增权限和已发布表单规则，处理提交数据并新增实体记录；选择“保存并发起”时还会按配置启动流程。',
    endpoints: [createEndpoint],
    replaceable: true,
    note: '请求体中的 entityCode 指定实体，data 承载字段数据；表单和列表上下文决定使用哪条已发布事件链。'
  },
  DATA_UPDATE: {
    name: '修改数据',
    description: '读取并校验当前记录的编辑权限，按已发布表单规则校验和处理提交数据，再更新该实体记录。',
    endpoints: [updateEndpoint],
    replaceable: true,
    note: '路径中的 id 是当前记录 ID，提交字段放在请求体 data 中；配置替代步骤后，前端仍调用此更新入口。'
  },
  DATA_DELETE: {
    name: '删除数据',
    description: '校验当前记录的访问范围和删除权限，通过平台实体删除逻辑删除该记录，并按实体关系规则处理关联数据。',
    endpoints: [{ label: '单条删除入口', method: 'POST', path: `${detailPath}/delete` }],
    replaceable: true,
    note: '删除事件使用当前列表的发布配置，recordId 表示本次删除的记录。'
  },
  DATA_BATCH_DELETE: {
    name: '批量删除',
    description: '逐条校验所选记录的访问范围和批量删除权限，全部通过后统一删除；任一记录不可删除则整体拒绝。',
    endpoints: [{ label: '批量删除入口', method: 'POST', path: `${entityDataPath}/batch-delete` }],
    replaceable: true,
    note: '请求体 ids 是待删除记录 ID 列表；该事件按整批执行，不会逐条触发 DATA_DELETE。'
  },
  LIST_EXPORT: {
    name: '导出列表',
    description: '内置导出按当前条件或所选记录读取数据，校验导出权限，并按列表显示字段生成 CSV 文件。',
    endpoints: [{ label: '内置导出接口（不触发此事件）', method: 'POST', path: `${entityDataPath}/export` }],
    note: '当前内置导出尚未接入 LIST_EXPORT 事件链；仅配置此事件不会拦截或替代内置导出。'
  },
  FORM_OPEN: {
    name: '打开表单',
    description: '新增时准备初始值，编辑时先加载记录详情，再执行打开事件的扩展步骤，随后展示表单。',
    eventEndpoint: true,
    note: '初始化和详情加载由页面完成，事件链中没有额外的默认加载动作；“替代平台处理”不会取消已完成的初始化或详情读取。'
  },
  FORM_SAVE: {
    name: '保存表单',
    description: '内置保存先校验表单，再根据是否已有记录调用新增或修改接口，成功后关闭表单并通知页面刷新。',
    endpoints: [createEndpoint, updateEndpoint],
    note: '当前内置保存使用 DATA_CREATE / DATA_UPDATE 事件，尚未触发独立的 FORM_SAVE 事件；保存扩展请配置对应的数据事件。'
  },
  FORM_RESET: {
    name: '重置表单',
    description: '在页面中恢复到本次打开表单时的初始快照，再执行重置事件的扩展步骤并刷新联动；不会保存到数据库。',
    eventEndpoint: true,
    note: '没有独立的业务重置接口。恢复快照先于事件链执行，“替代平台处理”不会阻止或撤销这次恢复。'
  },
  FIELD_CHANGE: {
    name: '字段值变化',
    description: '页面先更新字段值并通知表单联动，再执行字段事件的扩展步骤，将返回的字段映射应用到当前表单。',
    eventEndpoint: true,
    note: '事件本身不保存记录；事件链中没有默认写入动作，“替代平台处理”不会撤销已经发生的字段变化。'
  },
  ENTITY_SELECTED: {
    name: '选择实体后',
    description: '选择器先更新当前字段的选择值；配置了回填步骤时，事件读取选择数据并按映射回填当前表单字段。',
    eventEndpoint: true,
    note: '单选实体且存在事件步骤时，服务端按引用配置补查可访问的记录。回填由配置的步骤执行，没有独立的默认保存接口。'
  },
  FIELD_BUTTON_CLICK: {
    name: '字段按钮点击',
    description: '由支持此事件的字段组件触发，执行配置的接口或字段映射步骤；组件自身动作由该组件实现决定。',
    eventEndpoint: true,
    note: '平台没有统一的字段按钮业务动作，也不会自动保存实体记录。'
  },
  SUBFORM_LOAD: {
    name: '加载子表',
    description: '子表组件展示当前表单中的子行数据；配置了子表行数据源且当前没有子行时，通过数据源补充初始行。',
    endpoints: [{ label: '子表行数据源入口（配置时调用）', method: 'POST', path: '/api/ui-runtime/extensions/execute' }],
    note: '当前子表行数据源使用 SUBFORM_ROWS，尚未自动触发 SUBFORM_LOAD 事件；两者不是同一个配置入口。'
  },
  SUBFORM_SAVE: {
    name: '保存子表',
    description: '组成关系的子表数据随主表提交，由服务端按已发布子表规则校验，并与主记录一同保存。',
    note: '没有独立的默认子表保存接口；当前主表提交尚未自动触发 SUBFORM_SAVE 事件。'
  },
  TOOLBAR_BUTTON_CLICK: {
    name: '工具栏按钮点击',
    description: '工具栏“自定义 / 业务接口”按钮执行配置的事件步骤，并处理返回的刷新列表、消息或跳转等结果。',
    eventEndpoint: true,
    note: '没有内置的新增、删除或导出动作；未配置有效步骤时，不会自动执行业务操作。'
  },
  ROW_BUTTON_CLICK: {
    name: '行按钮点击',
    description: '操作列“自定义 / 业务接口”按钮以当前记录为上下文执行事件步骤，并处理返回的页面操作结果。',
    eventEndpoint: true,
    note: '没有内置的编辑或删除动作；具体业务行为由此按钮的事件步骤决定。'
  },
  FORM_BUTTON_CLICK: {
    name: '表单按钮点击',
    description: '表单自定义按钮没有平台默认处理，只执行配置的前置、主处理和后置步骤。',
    eventEndpoint: true,
    note: '发布时最终继承链必须且只能包含一个主处理；内置“保存”按钮使用 DATA_CREATE / DATA_UPDATE。'
  }
}

/**
 * 返回指定事件的默认行为和已核对的接口信息，供弹窗与表格共用。
 * 未知事件不猜测默认动作；统一事件入口不能当作数据操作接口展示。
 */
export function getEventDefaultProcessing(eventCode) {
  const code = String(eventCode || '').trim().toUpperCase()
  const definition = platformDefaultDescriptions[code] || {
    name: code || '未选择事件',
    description: '暂无此事件的默认处理说明，请确认触发方提供的行为。'
  }
  return {
    ...definition,
    code,
    endpoints: definition.eventEndpoint
      ? [{ label: '事件扩展入口（仅执行配置步骤）', method: 'POST', path: `/api/ui-runtime/events/${code}/execute` }]
      : (definition.endpoints || []),
    execution: definition.replaceable
      ? '执行顺序：前置步骤 → 平台默认处理（或替代步骤）→ 后置步骤。替代步骤接管默认业务处理，不更换上述请求入口。'
      : ''
  }
}
