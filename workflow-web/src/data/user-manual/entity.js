const fieldColumns = [
  { key: 'field', label: '配置项' },
  { key: 'meaning', label: '含义' },
  { key: 'defaultLimit', label: '默认值 / 限制' },
  { key: 'effect', label: '运行效果' },
  { key: 'publish', label: '发布注意事项' }
]

const optionColumns = [
  { key: 'option', label: '选项' },
  { key: 'meaning', label: '适用场景与运行效果' },
  { key: 'notes', label: '限制 / 注意事项' }
]

const entityFieldTypes = [
  { option: '文本 STRING', meaning: '短文本、名称、编号片段等；默认表单组件为单行输入框。', notes: '可配置 VARCHAR 长度 1–4000；未填写时按后端默认长度处理，UI 提示为 200。' },
  { option: '长文本 TEXT', meaning: '说明、备注等较长文本；默认表单组件为多行文本。', notes: '实体设计仍显示“字段长度”，但数据库可能映射为文本类型；大段内容避免建立唯一约束。' },
  { option: '富文本 RICH_TEXT', meaning: '带格式的说明、正文；表单可使用富文本编辑器。', notes: '发布前确认存储长度和内容安全策略；列表中建议只展示摘要。' },
  { option: '整数 INTEGER', meaning: '数量、序号、等级等整数值。', notes: '表单可配置最小值、最大值、步长；默认组件为数字输入。' },
  { option: '小数 DECIMAL', meaning: '金额、比率、测量值等定点小数。', notes: '总位数 1–65，UI 提示默认 18；小数位 0–30，UI 提示默认 2。业务上应保证小数位不大于总位数。' },
  { option: '日期 DATE', meaning: '只记录年月日。', notes: '运行时不含时分秒；列表可使用日期格式组件。' },
  { option: '日期时间 DATETIME', meaning: '记录完整时间点。', notes: '跨时区部署时统一约定服务端与用户时区。' },
  { option: '布尔 BOOLEAN', meaning: '是/否、开/关标记；默认表单组件为开关。', notes: '默认值应使用运行时可识别的 true/false 语义。' },
  { option: '选择 SELECT', meaning: '下拉单选，存储选项 value，显示 label。', notes: '选项每行使用 value:label；默认值必须填 value，不要填 label。' },
  { option: '选择（多选）MULTI_SELECT', meaning: '下拉多选，适合标签、多个分类。', notes: '选项同样使用 value:label；需要确认后端存储格式与导出格式。' },
  { option: '选择（单选框）RADIO', meaning: '少量互斥选项直接平铺显示。', notes: '字段类型面板不单独展示 RADIO/CHECKBOX，但可在字段类型下拉中选择。' },
  { option: '选择（复选框）CHECKBOX', meaning: '少量可多选项直接平铺显示。', notes: '选项 value 应稳定，发布后不要随意复用旧 value 表达新含义。' },
  { option: '文件 FILE', meaning: '上传、预览、下载通用附件。', notes: '通过“附件项”配置类型、单文件大小和数量；保存后再发布实体。' },
  { option: '图片 IMAGE', meaning: '上传图片并支持预览。', notes: '附件限制与 FILE 相同；应结合存储容量和图片安全扫描策略。' },
  { option: '用户 USER', meaning: '选择系统用户。', notes: '可配置字符串长度；用户停用后历史值仍需可回显。' },
  { option: '部门 DEPT', meaning: '选择系统组织部门。', notes: '组织调整可能影响数据权限和历史显示，发布前确认部门字段映射。' },
  { option: '单选实体 REFERENCE', meaning: '引用一个自定义实体或系统用户、部门、角色、用户组。', notes: '自定义实体必须选择关联实体；表单层可选填定制查询接口。' },
  { option: '多选实体 MULTI_REFERENCE', meaning: '引用多个实体记录。', notes: '列表查询、导出和权限过滤要考虑多值结构。' },
  { option: '子表单 SUB_FORM', meaning: '父记录关联一个子记录，默认关系为一对一。', notes: '必须选择子实体与子表外键；默认级联删除开启。' },
  { option: '子表单列表 SUB_FORM_LIST', meaning: '父记录关联多条子记录，默认关系为一对多。', notes: '必须选择子实体与子表外键；大量明细需要控制一次加载数量。' }
]

export default {
  eyebrow: 'USER MANUAL · ENTITY',
  title: '实体配置用户手册',
  subtitle: '覆盖实体创建、字段与关系、附件和引用、编码、状态、数据权限、表单、初始化、联动事件、列表、动态字段、按钮权限以及发布版本的完整配置闭环。',
  version: '当前 UI 配置基线',
  updatedAt: '2026-07-16',
  intro: [
    {
      title: '推荐顺序',
      type: 'success',
      text: '先建实体与字段，再配置状态、表单、列表和权限；需要流程时先完成流程设计，再回到实体绑定流程；最后统一发布并检查版本快照。'
    },
    {
      title: '保存不等于发布',
      type: 'warning',
      text: '实体设计、表单设计和列表设计中的“保存”只更新配置。首次发布会创建物理表，重新发布会同步新增字段并记录版本；发布前必须查看差异和 DDL 预览。'
    }
  ],
  sections: [
    {
      id: 'entity-overview',
      index: '01',
      title: '入口与配置闭环',
      summary: '先理解实体列表、设计器、表单、列表配置和发布之间的关系。',
      topics: [
        {
          id: 'entity-overview-lifecycle',
          title: '完整配置流程',
          lead: '一个可投入使用的实体通常需要完成“定义—体验—权限—发布—验证”五个阶段。',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '创建实体', text: '填写实体名称、稳定的实体编码和说明；系统自动补充标准字段。' },
                { title: '设计字段', text: '配置业务字段、关系、附件和引用，保存实体定义。' },
                { title: '配置体验', text: '创建默认表单和列表，设置初始化、联动、事件、查询、动态列和按钮。' },
                { title: '配置治理', text: '维护状态、数据权限、按钮功能权限与适用条件；需要审批时绑定流程。' },
                { title: '发布验证', text: '查看字段差异、DDL、版本说明和迁移标记，发布后用真实角色验证新增、编辑、查询、导出、审批。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '配置依赖',
              text: '流程节点要选择实体表单，必须先由实体绑定流程并至少存在一个启用表单。列表按钮的状态条件依赖实体状态配置；数据权限中的“适用列表”依赖列表配置已保存。'
            }
          ]
        },
        {
          id: 'entity-overview-system-fields',
          title: '系统标准字段',
          lead: '新建实体后平台自动创建标准字段，这些字段用于运行时、流程关联和权限判断。',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'field', label: '字段' },
                { key: 'meaning', label: '用途' },
                { key: 'behavior', label: '编辑与运行规则' }
              ],
              rows: [
                { field: 'name / 数据名称', meaning: '记录的通用标题。', behavior: '系统字段；可调整名称、必填、默认值和排序，但编码与类型不可改。' },
                { field: 'code / 数据编码', meaning: '记录的稳定编码。', behavior: '默认唯一；可由编码规则生成。编码与类型不可改。' },
                { field: 'status / 状态', meaning: '实体当前业务状态。', behavior: '默认 DRAFT，由流程连线或业务操作维护。' },
                { field: 'processInstanceId', meaning: '关联流程实例。', behavior: '流程启动后写入，通常不在编辑表单中开放。' },
                { field: 'processStartTime / processEndTime', meaning: '流程开始与结束时间。', behavior: '运行时维护，适合列表展示或审计。' },
                { field: 'submitterId / submitterName', meaning: '流程提交人。', behavior: '流程启动时写入，可用于按钮条件和数据权限。' },
                { field: 'deptId / 所属部门', meaning: '记录归属部门。', behavior: '系统部门引用字段，可参与本部门、本部门及子部门权限。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'entity-list-entry',
      index: '02',
      title: '实体列表与创建',
      summary: '覆盖查询、分页、新建、绑定、版本和删除等列表入口。',
      topics: [
        {
          id: 'entity-list-search',
          title: '查询与列表字段',
          lead: '实体管理页用于定位配置对象，并展示流程绑定与发布状态。',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '实体名称', meaning: '按实体名称或编码模糊搜索。', defaultLimit: '默认空；按回车或“查询”执行。', effect: '返回匹配实体并将页码重置为 1。', publish: '无发布影响。' },
                { field: '状态', meaning: '筛选草稿、已发布、已禁用。', defaultLimit: '默认全部。', effect: '只显示对应配置状态。', publish: '已发布不代表表单、列表后续修改已形成新快照。' },
                { field: '启用流程', meaning: '筛选是否已开启流程能力。', defaultLimit: '是 / 否 / 全部。', effect: '帮助区分纯数据实体与流程实体。', publish: '绑定流程会自动启用流程。' },
                { field: '分页', meaning: '控制当前页与每页数量。', defaultLimit: '默认 10；可选 10/20/50/100。', effect: '服务端分页加载。', publish: '无发布影响。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '列表展示实体名称、实体编码、描述、是否启用流程、绑定流程、状态。',
                '“重置”恢复全部筛选并回到第 1 页。',
                '“设计、发布/重新发布、列表、表单”为高频入口；状态配置、绑定流程、版本历史和删除位于更多菜单。'
              ]
            }
          ]
        },
        {
          id: 'entity-create-fields',
          title: '新建实体字段',
          lead: '实体编码会进入接口、权限码、物理表、迁移包和流程关联，必须按长期稳定标识设计。',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '实体名称', meaning: '用户看到的业务名称。', defaultLimit: '必填；无固定格式。', effect: '显示在菜单、列表、表单和设计器。', publish: '名称可改，但版本说明中应记录业务改名。' },
                { field: '实体编码', meaning: '实体的唯一技术标识。', defaultLimit: '必填；必须以字母开头，只能包含字母、数字、下划线；创建后 UI 禁止修改，后端按不区分大小写校验唯一。', effect: '参与表名、API、权限码 entity:{code}:action 和迁移业务键。', publish: '上线后不得通过数据库直接改名，否则流程、菜单权限和列表配置会失联。' },
                { field: '描述', meaning: '说明实体用途和边界。', defaultLimit: '可选，多行文本。', effect: '帮助管理员识别配置。', publish: '建议写明数据负责人和适用流程。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '删除实体',
              text: '删除属于高风险操作。实体定义、字段和关系会被删除或停用相关权限；已存在业务数据、流程实例、菜单或迁移资产时，不应直接删除。生产环境建议先禁用入口并完成数据归档。'
            }
          ]
        },
        {
          id: 'entity-list-operations',
          title: '列表操作说明',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '设计', meaning: '进入实体字段设计器，维护字段、关系、附件、引用、编码和数据权限。', notes: '保存只更新定义；首次发布前字段可删除，发布后已有字段不能删除。' },
                { option: '发布 / 重新发布', meaning: '预览版本差异与 DDL，创建或同步物理表，并生成版本。', notes: '发布前先保存实体、表单和列表；迁移标记在加入待导出清单时必填。' },
                { option: '列表', meaning: '维护一个实体的多个列表方案与默认列表。', notes: '列表标识一旦被菜单或页面引用，不要随意修改。' },
                { option: '表单', meaning: '维护一个实体的多个表单、默认表单和初始化配置。', notes: '流程节点选择的表单被删除或禁用会影响运行。' },
                { option: '状态配置', meaning: '维护状态分类、编码、名称、说明和排序。', notes: '状态编码被流程连线、按钮条件、权限状态限制引用后应保持稳定。' },
                { option: '绑定流程', meaning: '从未绑定流程及当前流程中选择一个流程，绑定后启用流程。', notes: '实体已有流程数据时，后端禁止切换到其他流程。' },
                { option: '版本历史', meaning: '查看版本、发布说明、字段快照、DDL 与相邻版本差异。', notes: '版本是审计依据，不应通过数据库直接改写。' },
                { option: '删除', meaning: '删除实体配置。', notes: '先确认无数据、无流程实例、无菜单和无迁移依赖。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'entity-field-design',
      index: '03',
      title: '字段设计',
      summary: '覆盖字段类型、公共属性、数据库映射、排序和发布后的限制。',
      topics: [
        {
          id: 'entity-field-types',
          title: '字段类型全集',
          lead: '左侧字段类型可点击或拖拽添加；RADIO 和 CHECKBOX 不在快捷面板中单列，但可在属性中的字段类型下拉选择。',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: entityFieldTypes
            }
          ]
        },
        {
          id: 'entity-field-common',
          title: '公共字段属性',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '字段名称', meaning: '业务显示名。', defaultLimit: '必填。', effect: '用于表单标签、列表列名和条件选择。', publish: '可改名；建议在版本说明记录语义变化。' },
                { field: '字段编码', meaning: '实体内唯一技术标识。', defaultLimit: '必填；建议字母开头、字母数字下划线；同一实体内不能重复。系统字段或已发布字段禁改。', effect: '映射数据库列、表单字段、列表字段、表达式和导入导出。', publish: '发布后不可改编码；需要改名时新增字段并迁移数据。' },
                { field: '数据库列名', meaning: '由字段编码自动转为下划线小写。', defaultLimit: '只读，例如 projectName → project_name。', effect: '物理表列名。', publish: '禁止手工在数据库改列名。' },
                { field: '字段类型', meaning: '决定数据库类型和默认表单组件。', defaultLimit: '必填；系统字段或已发布字段禁改。', effect: '影响校验、查询、渲染和序列化。', publish: '类型变更应通过新增字段实现，避免历史数据不可转换。' },
                { field: '是否必填', meaning: '业务录入是否必须提供值。', defaultLimit: '默认关闭。', effect: '表单与后端校验会拒绝空值。', publish: '已有空数据时不要直接改为必填，先补数。' },
                { field: '是否唯一', meaning: '要求字段值唯一。', defaultLimit: '默认关闭；code 系统字段默认唯一。', effect: '保存重复值时失败。', publish: '上线前清理重复数据；长文本和多值字段不建议唯一。' },
                { field: '默认值', meaning: '新增记录的初始值。', defaultLimit: '默认空；选项字段必须填写 value。', effect: '表单未覆盖时使用默认值。', publish: '默认值改变只影响后续新增，不回填历史数据。' },
                { field: '选项配置', meaning: '定义 SELECT、MULTI_SELECT、RADIO、CHECKBOX 的 value 和 label。', defaultLimit: '每行 value:label；空行忽略。', effect: '存 value、显 label。', publish: '不要复用已使用的 value 表达新含义；删除选项前处理历史值。' },
                { field: '验证规则', meaning: '扩展 JSON 校验规则。', defaultLimit: '必须是合法 JSON。', effect: '由运行时或扩展组件解释。', publish: '先在预览和真实新增/编辑模式验证兼容性。' },
                { field: '排序', meaning: '字段在实体设计和默认生成场景的顺序。', defaultLimit: '通过上下箭头调整。', effect: '影响字段展示和后续表单初始化顺序。', publish: '只改元数据，不改物理列顺序。' }
              ]
            }
          ]
        },
        {
          id: 'entity-field-length',
          title: '长度、精度与发布锁定',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '字段长度', meaning: '字符串字段的 VARCHAR 长度。', defaultLimit: '1–4000；UI 提示默认 200；适用于 STRING、TEXT、SELECT、RADIO、MULTI_SELECT、CHECKBOX、USER、DEPT、REFERENCE。', effect: '限制数据库列可存储字符长度。', publish: '缩短长度可能截断历史数据；当前发布差异主要对新增列生成 DDL，修改长度需确认数据库同步能力。' },
                { field: '总位数', meaning: 'DECIMAL precision。', defaultLimit: '1–65；UI 提示默认 18。', effect: '决定整数位与小数位总和。', publish: '缩小总位数前检查历史最大值。' },
                { field: '小数位数', meaning: 'DECIMAL scale。', defaultLimit: '0–30；UI 提示默认 2。', effect: '控制小数精度和舍入。', publish: '财务字段应明确舍入规则并保持跨环境一致。' },
                { field: '已发布标记', meaning: '表示物理表已包含该字段。', defaultLimit: '系统在发布或同步成功后维护。', effect: '已发布字段的编码和类型被锁定，删除按钮隐藏。', publish: '不要手工修改标记；以版本和数据库结构为准。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '已发布实体的限制',
              text: '后端不允许删除已发布实体中的既有非系统字段。新增字段可以保存并在重新发布时同步；如果已发布状态下保存触发自动同步失败，页面可能仍保存元数据，因此必须检查日志、物理表和重新发布差异。'
            }
          ]
        }
      ]
    },
    {
      id: 'entity-relations',
      index: '04',
      title: '关系、附件与引用',
      summary: '配置父子实体、附件约束和系统/自定义实体引用。',
      topics: [
        {
          id: 'entity-subform-relation',
          title: '子表单关系',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '类型', meaning: '父子关系基数。', defaultLimit: 'SUB_FORM 默认 ONE_TO_ONE；SUB_FORM_LIST 默认 ONE_TO_MANY。', effect: '决定父记录对应一条还是多条子记录。', publish: '变更关系类型前评估已有子数据。' },
                { field: '子实体', meaning: '关系目标实体。', defaultLimit: '必填；不能选择当前实体。', effect: '运行时加载目标实体的表单和数据。', publish: '目标实体应先完成字段与表单配置，并建议先发布。' },
                { field: '子表外键', meaning: '子实体中保存父记录引用的字段。', defaultLimit: '选择子实体字段；必填。', effect: '用于新增、更新、查询和级联删除子数据。', publish: '外键字段编码发布后应保持稳定；确认类型可容纳父 ID。' },
                { field: '级联删除', meaning: '删除父记录时是否删除关联子记录。', defaultLimit: '默认开启。', effect: '开启后删除父记录会清理子数据。', publish: '审计型明细慎用；关闭时要处理孤儿数据。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '实体保存时会重建当前实体的关系定义；字段信息不完整会阻止保存。',
                '关系编码默认可由“父实体编码_父字段编码”推导，关系名称默认使用字段名称。',
                '表单层还需配置子表单的显示方式、布局和可选子表表单。'
              ]
            }
          ]
        },
        {
          id: 'entity-attachments',
          title: '文件与图片附件项',
          lead: 'FILE 和 IMAGE 字段可以拆成多个附件项，每项独立限制类型、大小和数量。',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '项名称', meaning: '附件分类名称，例如“项目章程”。', defaultLimit: '新字段首次选中时默认使用字段名称或“附件”。', effect: '运行时按附件项分组展示。', publish: '名称可改，但应保留历史语义。' },
                { field: '文件类型', meaning: '允许的扩展名组合。', defaultLimit: '可多选图片、文档、表格、文本、压缩包；不选表示允许所有类型。', effect: '上传时限制文件扩展名。', publish: '扩展名校验不能替代 MIME、内容安全和病毒扫描。' },
                { field: '单文件大小', meaning: '单个文件最大体积。', defaultLimit: '1–100 MB；默认 10 MB。', effect: '超过限制时拒绝上传。', publish: '不得超过网关、应用和存储服务的全局限制。' },
                { field: '数量限制', meaning: '该附件项最多文件数。', defaultLimit: '1–20；默认 5。', effect: '达到上限后不能继续上传。', publish: '缩小数量不会自动删除已有文件。' },
                { field: '添加 / 删除附件项', meaning: '维护多个独立附件分类。', defaultLimit: '至少保留业务所需项；可全部删除。', effect: '改变运行时附件区域结构。', publish: '删除附件项前确认历史附件仍可访问与迁移。' }
              ]
            }
          ]
        },
        {
          id: 'entity-references',
          title: '实体引用',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '用户自定义实体 CUSTOM', meaning: '引用平台内另一个业务实体；必须选择“关联实体”。', notes: '目标实体删除或编码变化会导致引用失效。' },
                { option: '系统用户 USER', meaning: '引用系统用户。', notes: '用户停用不应导致历史数据无法回显。' },
                { option: '系统部门 DEPT', meaning: '引用组织部门。', notes: '可与数据权限中的部门范围结合。' },
                { option: '系统角色 ROLE', meaning: '引用系统角色。', notes: '角色编码和授权变化会影响后续业务判断。' },
                { option: '系统用户组 GROUP', meaning: '引用用户组。', notes: '用户组成员变化会影响动态人员范围。' }
              ]
            },
            {
              type: 'callout',
              tone: 'info',
              title: '单选与多选',
              text: 'REFERENCE 只保存一个目标，MULTI_REFERENCE 保存多个目标。表单设计中引用类型和关联实体对来源于实体字段的字段会被锁定；可额外填写“数据接口”定制候选数据范围，留空使用默认查询。'
            }
          ]
        }
      ]
    },
    {
      id: 'entity-code-status',
      index: '05',
      title: '编码、状态与流程绑定',
      summary: '定义业务单号生成方式、状态字典和实体所使用的流程。',
      topics: [
        {
          id: 'entity-code-rule',
          title: '数据编码规则',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '编码前缀', meaning: '业务单号固定前缀。', defaultLimit: '最多 20 字符；无规则时默认尝试使用实体编码大写。', effect: '生成结果最前部分。', publish: '上线后修改会导致新旧编码格式并存。' },
                { field: '日期格式', meaning: '编码中的日期片段。', defaultLimit: 'yyyyMMdd、yyyy-MM-dd、yyyy/MM/dd、yyyyMM、yyMMdd；默认 yyyyMMdd。', effect: '按生成时日期拼接。', publish: '确认分隔符是否符合外部系统和数据库长度要求。' },
                { field: '序列号位数', meaning: '递增序号补零长度。', defaultLimit: '3–10；默认 6。', effect: '例如 1 生成 000001。', publish: '位数过小可能在高并发周期内耗尽。' },
                { field: '重置周期', meaning: '序列何时从 1 重新开始。', defaultLimit: 'DAY 默认；可选 MONTH、YEAR、NEVER。', effect: '按天、月、年或永不重置。', publish: '编码唯一性依赖前缀、日期和周期组合；修改周期前评估重复风险。' },
                { field: '编码示例', meaning: '根据当前配置预览。', defaultLimit: '只读；可点击刷新。', effect: '帮助确认格式。', publish: '预览不代表并发生成结果，仍需数据库唯一约束。' }
              ]
            }
          ]
        },
        {
          id: 'entity-status-config',
          title: '实体状态配置',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '状态分类', meaning: '归一化流程阶段。', defaultLimit: 'NEW、PROCESSING、COMPLETED、TERMINATED、WITHDRAWN。', effect: '供按钮条件、权限过滤、流程状态和标签展示使用。', publish: '分类变化会改变所有基于分类的规则。' },
                { field: '状态编码', meaning: '业务状态稳定标识。', defaultLimit: '必填；示例 PENDING。', effect: '实际存入实体 status 字段，并被流程连线引用。', publish: '被引用后不要改；需要新语义时新增编码。' },
                { field: '状态名称', meaning: '用户显示文本。', defaultLimit: '必填。', effect: '列表、审批和条件配置显示。', publish: '可改显示名，建议记录版本说明。' },
                { field: '说明', meaning: '解释进入该状态的条件。', defaultLimit: '可选。', effect: '帮助设计人员选状态。', publish: '建议写清允许的按钮和后续动作。' },
                { field: '拖拽排序', meaning: '控制状态选项顺序。', defaultLimit: '任意排序。', effect: '影响配置界面的展示顺序。', publish: '不改变状态值。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '状态删除',
              text: '删除前必须搜索流程连线、按钮适用条件、数据权限状态限制和历史数据。历史记录仍保存旧编码时，运行时可能只能显示原始编码。'
            }
          ]
        },
        {
          id: 'entity-bind-process',
          title: '绑定流程',
          lead: '绑定后实体成为流程业务数据载体，节点表单、状态映射和审批入口都依赖这条关系。',
          blocks: [
            {
              type: 'bullets',
              items: [
                '选择范围包含所有未被其他实体绑定的流程，以及当前已绑定流程。',
                '确认绑定后 processDefinitionId 更新并自动将“启用流程”设为是。',
                '若实体已经存在流程数据，后端会阻止切换到另一个流程，避免历史实例与新定义错配。',
                '流程设计器中的节点表单来源依赖绑定实体；未绑定时只能选择自定义表单或无表单。',
                '建议先发布实体、完成表单，再绑定并发布流程；若顺序相反，流程节点可能找不到表单。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'entity-data-permission',
      index: '06',
      title: '数据权限',
      summary: '决定“谁能看到哪些数据”，支持列表范围、用户/角色/组/组织、允许与拒绝、嵌套条件组和扩展 Provider。',
      topics: [
        {
          id: 'entity-permission-default',
          title: '默认行为与规则评估',
          blocks: [
            {
              type: 'callout',
              tone: 'warning',
              title: '无规则默认仅本人',
              text: '当前实体没有配置任何命中规则时，列表默认仅允许查看本人创建的数据。SQL 预览中若无命中规则，也会显示默认“仅本人”条件。'
            },
            {
              type: 'steps',
              items: [
                { title: '按优先级评估', text: '数字越大越先执行；命中停止只终止更低优先级 ALLOW，所有匹配的 DENY 始终评估。' },
                { title: '判断适用人群', text: '根据全部用户、用户、角色、用户组、部门或组织，以及 OR/AND 逻辑决定规则是否命中。' },
                { title: '生成数据范围', text: '简单范围直接选择创建人、提交人、当前办理人、部门或部门树；复杂范围使用嵌套结构化条件组。' },
                { title: '合并允许与拒绝', text: 'ALLOW 规则按 UNION/INTERSECT 形成允许范围，所有 DENY 范围最后统一扣除，避免拒绝规则反向扩大权限。' },
                { title: '异常时默认拒绝', text: '损坏或非法的 ALLOW 不会扩大权限；损坏或非法的 DENY 按拒绝全部处理，避免配置异常造成越权。' },
                { title: '附加状态限制', text: '可只允许或排除指定实体状态。' }
              ]
            }
          ]
        },
        {
          id: 'entity-permission-basic',
          title: '规则基础配置',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '规则名称', meaning: '描述适用人群和数据范围。', defaultLimit: '必填。', effect: '用于列表和 SQL 预览识别。', publish: '建议包含角色/部门、范围和例外。' },
                { field: '适用列表', meaning: '只对某个列表配置生效。', defaultLimit: '默认全部列表；可选择一个已保存列表。', effect: '同一实体可按页面场景采用不同数据范围。', publish: '删除列表前先清理对应规则。' },
                { field: '优先级', meaning: '规则评估顺序。', defaultLimit: '0–9999，默认 0；越大越高。', effect: '影响命中停止和组合顺序。', publish: '修改优先级要重新预览最终 SQL。' },
                { field: '规则效果', meaning: 'ALLOW 放行，DENY 排除。', defaultLimit: '默认 ALLOW。', effect: '决定规则结果加入允许集还是拒绝集。', publish: 'DENY 适合明确例外，不要用复杂拒绝规则替代清晰允许规则。' },
                { field: '合并方式', meaning: 'UNION 并集或 INTERSECT 交集。', defaultLimit: '默认 UNION。', effect: '控制多条范围 SQL 使用 OR 还是 AND。', publish: 'INTERSECT 可能导致空结果，必须以目标角色测试。' },
                { field: '命中停止', meaning: '命中后停止更低优先级的 ALLOW 规则。', defaultLimit: '默认关闭；不影响任何 DENY。', effect: '实现允许范围的高优先级覆盖，同时避免跳过拒绝规则。', publish: '所有匹配 DENY 始终在允许范围和委托范围之后统一扣除。' },
                { field: '是否启用', meaning: '规则开关。', defaultLimit: '默认启用。', effect: '关闭后不参与评估。', publish: '切换立即影响运行，无需实体发布；变更前应告知业务。' }
              ]
            }
          ]
        },
        {
          id: 'entity-permission-match',
          title: '匹配配置：谁适用',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '逻辑关系 OR / AND', meaning: 'OR 满足任一条件，AND 必须全部满足。默认 OR。', notes: '空条件通常不会命中预期人群，应至少配置一条。' },
                { option: '全部用户 ALL_USERS', meaning: '所有登录用户都适用。', notes: '适合基础允许规则，通常配合更严格的数据范围。' },
                { option: '指定用户 USER', meaning: '多选具体用户。', notes: '保存的是用户 ID；人员离职后需维护规则。' },
                { option: '指定角色 ROLE', meaning: '多选角色，并选择 ANY 任一角色或 ALL 全部角色。', notes: '默认新增条件类型为角色、操作符 ANY。' },
                { option: '指定用户组 GROUP', meaning: '多选系统用户组，并选择满足任一组或全部组。', notes: '用户组成员调整会立即改变规则命中结果。' },
                { option: '指定部门 DEPT', meaning: '多选部门，可开启包含子部门。', notes: '组织树调整会改变实际命中人员。' },
                { option: '指定组织 ORG', meaning: '按用户所属组织匹配，可包含下级组织。', notes: '组织与部门使用独立的用户属性，不要混选。' }
              ]
            }
          ]
        },
        {
          id: 'entity-permission-filter',
          title: '过滤配置：能看什么',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '全部数据 ALL', meaning: '不追加用户或部门范围。', notes: '只应授予明确需要全量查看的人群。' },
                { option: '当前用户是创建人 PERSONAL', meaning: '按创建人字段匹配当前用户；默认数据范围。', notes: '默认用户字段 create_by，可通过字段映射调整。' },
                { option: '当前用户是提交人 SUBMITTER', meaning: '按 submitter_id 匹配当前用户。', notes: '适合流程发起人与数据创建人不同的业务。' },
                { option: '当前用户是当前办理人 CURRENT_ASSIGNEE', meaning: '按 current_task_assignee 匹配用户 ID 或用户名。', notes: '流程任务完成后当前办理人会变化或清空。' },
                { option: '本部门 DEPT', meaning: '按部门字段匹配当前用户部门。', notes: '默认部门字段 dept_id；确认实体实际列映射。' },
                { option: '本部门及子部门 DEPT_TREE', meaning: '匹配当前部门和所有下级部门。', notes: '大组织树需要关注查询性能。' },
                { option: '结构化条件组 RULE', meaning: '使用 AND/OR 嵌套组配置用户关系、流程状态、状态编码/分类、当前用户属性和实体字段条件。', notes: '后端只编译白名单字段与操作符，不开放脚本或自由 SQL。' }
              ]
            },
            {
              type: 'table',
              title: '结构化条件类型',
              columns: optionColumns,
              rows: [
                { option: '当前用户关系 RELATION', meaning: '创建人、提交人、当前办理人、同部门。', notes: '关系值由后端从登录用户与记录系统字段计算。' },
                { option: '流程状态 PROCESS_STATE', meaning: '未发起、进行中、已完成、已终止、已撤回。', notes: '终止和撤回结合实体状态分类判断。' },
                { option: '状态编码 / 状态分类', meaning: '按具体状态编码或 NEW、PROCESSING、COMPLETED、TERMINATED、WITHDRAWN 分类过滤。', notes: '状态分类会展开为当前实体配置的状态编码。' },
                { option: '数据字段 FIELD', meaning: '比较系统字段或实体自定义字段。', notes: '支持等于、不等于、IN、包含、为空、大小比较等白名单操作符。' },
                { option: '当前用户属性 USER_FIELD', meaning: '按用户 ID、用户名、部门、组织或角色集合决定条件是否成立。', notes: '结果编译为恒真或恒假条件，不允许访问任意对象。' },
                { option: '自定义 Provider', meaning: '业务模块可注册命名空间条件类型。', notes: '前后端及目标环境必须注册相同类型；Provider 输出仍经过平台统一执行。' }
              ]
            },
            {
              type: 'table',
              title: '字段映射与状态限制',
              columns: fieldColumns,
              rows: [
                { field: '用户字段', meaning: '记录所属用户列。', defaultLimit: '默认 create_by。', effect: 'PERSONAL 范围使用。', publish: '字段必须真实存在。' },
                { field: '部门字段', meaning: '记录所属部门列。', defaultLimit: '默认 dept_id。', effect: 'DEPT / DEPT_TREE 使用。', publish: '字段值必须与组织 ID 一致。' },
                { field: '状态字段', meaning: '状态限制使用的列。', defaultLimit: '默认 status。', effect: '附加状态 IN / NOT IN 条件。', publish: '自定义状态列需保证同步维护。' },
                { field: '状态限制', meaning: '开启后按状态过滤。', defaultLimit: '默认关闭；模式默认 IN；状态可多选。', effect: 'IN 只允许所选状态，NOT_IN 排除所选状态。', publish: '删除状态编码前先清理规则。' }
              ]
            }
          ]
        },
        {
          id: 'entity-permission-preview',
          title: 'SQL 预览与验证',
          blocks: [
            {
              type: 'bullets',
              items: [
                '“预览 SQL”查看当前用户最终命中的规则、每条规则 SQL 和最终合并 SQL；不包含外层 deleted=0。',
                '“预览规则 SQL”只查看单条规则独立生效时的片段，适合排查字段映射和状态条件。',
                '至少使用普通用户、部门负责人、管理员三类账号验证；不要只用 super_admin。',
                '验证列表特定规则时，从对应列表入口进入，确认 listConfigId 生效。',
                '标准实体字段查询会先在数据库中应用数据权限和查询条件，再执行 COUNT 与 LIMIT；分页总数不会包含无权访问的数据。',
                '使用计算型扩展字段作为查询条件时，系统会回退到授权数据内计算后分页；数据量较大时应由自定义 Provider 提供可下推查询能力。',
                '结构化字段、部门树和状态分类规则上线前仍需检查索引和执行计划，避免权限过滤成为全表扫描。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'entity-form-management',
      index: '07',
      title: '表单管理',
      summary: '维护多个表单、默认表单、状态、复制、预览和初始化配置。',
      topics: [
        {
          id: 'entity-form-list',
          title: '表单列表与操作',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '表单名称', meaning: '业务显示名。', defaultLimit: '必填。', effect: '流程节点选择和预览时显示。', publish: '名称可改，流程绑定按表单 ID 不受影响。' },
                { field: '表单标识', meaning: '稳定技术标识。', defaultLimit: '必填；以字母开头，可含字母、数字、下划线；后端还允许短横线，最大 100 字符。', effect: '用于扩展和配置迁移。', publish: '被外部组件引用后保持稳定。' },
                { field: '布局类型', meaning: '垂直、水平或网格。', defaultLimit: '默认 vertical。', effect: '决定标签位置和字段排列。', publish: '改布局后检查移动端和长标签。' },
                { field: '状态', meaning: '启用或禁用。', defaultLimit: '默认启用 1。', effect: '禁用表单不应作为新运行节点选择。', publish: '流程已绑定表单时不要直接禁用。' },
                { field: '描述', meaning: '说明使用场景。', defaultLimit: '可选。', effect: '便于区分新增、审批、查看等表单。', publish: '建议注明适用节点和角色。' }
              ]
            },
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '设计', meaning: '进入所见即所得表单设计器。', notes: '保存字段后再预览真实运行模式。' },
                { option: '编辑', meaning: '修改名称、标识、布局、状态、描述。', notes: '表单标识变更可能影响扩展。' },
                { option: '设为默认', meaning: '作为实体默认表单；流程节点无显式表单时会尝试使用。', notes: '一个实体应保持一个明确默认表单。' },
                { option: '复制', meaning: '复制现有表单作为新方案。', notes: '复制后检查表单标识、事件脚本和初始化接口。' },
                { option: '预览', meaning: '加载表单和字段并使用联动运行时预览。', notes: '预览不替代权限和真实接口验证。' },
                { option: '初始化', meaning: '配置打开新增表单时的初始数据来源。', notes: '详见表单初始化章节。' },
                { option: '删除', meaning: '删除表单。', notes: '先检查流程节点、子表单和默认表单引用。' }
              ]
            }
          ]
        },
        {
          id: 'entity-form-default',
          title: '默认表单与流程节点',
          blocks: [
            {
              type: 'callout',
              tone: 'info',
              title: '默认选择逻辑',
              text: '流程已绑定实体时，如果开始事件或用户任务没有保存表单配置，设计器会尝试加载实体默认表单，并把默认表单 ID、只读标记和实体编码写入 BPMN 扩展属性。'
            },
            {
              type: 'checklist',
              items: [
                '默认表单处于启用状态。',
                '默认表单至少有一个字段。',
                '流程节点需要编辑时未开启只读。',
                '审批节点需要的字段在 approve 模式下可见且可编辑。',
                '复制表单后没有重复的业务标识或过期接口。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'entity-form-designer',
      index: '08',
      title: '表单设计器',
      summary: '覆盖字段添加、布局、组件、校验、运行模式、子表单和实体引用。',
      topics: [
        {
          id: 'entity-form-designer-layout',
          title: '画布与表单级配置',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '搜索字段', meaning: '按实体字段名称或编码过滤左侧字段。', defaultLimit: '默认空。', effect: '快速定位字段；已添加字段显示“已添加”。', publish: '无发布影响。' },
                { field: '垂直布局', meaning: '标签在字段上方，字段单列排列。', defaultLimit: '新表单默认。', effect: '适合移动端和长标签。', publish: '检查页面长度和节分组。' },
                { field: '水平布局', meaning: '标签在左，字段通常双列展示。', defaultLimit: '可选。', effect: '适合桌面端紧凑录入。', publish: '窄屏下注意拥挤。' },
                { field: '网格布局', meaning: '按 24 栅格控制字段宽度。', defaultLimit: '字段 gridSpan 1–24，默认 24。', effect: '可构建多列复杂表单。', publish: '所有断点都要验证，单表单最多 300 个项目。' },
                { field: '添加节', meaning: '插入分组标题，不绑定实体字段。', defaultLimit: '默认标题“新节”，只读，占满 24 栅格。', effect: '对长表单分组。', publish: '节编码必须保持合法且不能重复。' },
                { field: '自定义组件', meaning: '使用已注册的自定义表单组件替代默认动态表单。', defaultLimit: '默认空；支持筛选、手工输入和清空；标识需以字母开头，可含字母数字点下划线短横线，最大 100。', effect: '运行时整体表单由扩展组件渲染。', publish: '组件必须已在目标环境注册，并兼容运行时 props 与 validate 契约。' },
                { field: '标签宽度', meaning: '动态表单标签宽度。', defaultLimit: '60–240，默认 120。', effect: '影响水平和网格布局对齐。', publish: '长标签需要实际预览。' },
                { field: '组件参数', meaning: '按自定义组件 configSchema 生成结构化参数。', defaultLimit: '仅组件声明 schema 时显示。', effect: '作为 viewConfig.customComponentProps 传入组件。', publish: '目标环境组件版本必须支持相同参数。' }
              ]
            }
          ]
        },
        {
          id: 'entity-form-field-properties',
          title: '字段属性与组件',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '字段名称 / 节标题', meaning: '实体字段名称只读；节标题可编辑。', defaultLimit: '实体字段不可在表单层改名。', effect: '节标题直接显示，普通字段保留实体语义。', publish: '改实体字段名称应回实体设计。' },
                { field: '显示标签', meaning: '当前表单中的标签文本。', defaultLimit: '默认实体字段名称。', effect: '只影响此表单显示。', publish: '适合按场景简化名称。' },
                { field: '组件类型', meaning: '选择与字段类型兼容的渲染组件。', defaultLimit: '按字段类型过滤可用组件。', effect: '改变输入交互，不改变实体字段数据库类型。', publish: '组件必须在目标环境注册。' },
                { field: '必填', meaning: '当前表单场景要求填写。', defaultLimit: '从实体字段继承，可单独调整。', effect: '运行时表单校验。', publish: '不要与模式权限的不可编辑配置冲突。' },
                { field: '只读', meaning: '字段显示但不可编辑。', defaultLimit: '默认关闭。', effect: '运行时禁用输入。', publish: '只读不等于隐藏，敏感数据仍可见。' },
                { field: '隐藏', meaning: '字段不显示。', defaultLimit: '默认关闭。', effect: '运行时不渲染或不占布局。', publish: '隐藏字段仍可能携带默认值或初始化值。' },
                { field: '默认值', meaning: '该表单中的初始值。', defaultLimit: '默认空。', effect: '覆盖或补充实体默认值。', publish: '选项字段填写 value。' },
                { field: '占位提示', meaning: '输入为空时的提示。', defaultLimit: '默认空。', effect: '指导用户输入格式。', publish: '不要把关键校验只写在占位提示。' },
                { field: '栅格宽度', meaning: '网格布局占用列数。', defaultLimit: '1–24，默认 24。', effect: '24 为整行，12 为半行。', publish: '后端会校验范围。' }
              ]
            },
            {
              type: 'table',
              title: '内置组件及参数',
              columns: optionColumns,
              rows: [
                { option: '文本输入 input', meaning: 'STRING；参数 maxlength 1–10000、showWordLimit 默认 true。', notes: '最大长度不要超过实体字段长度。' },
                { option: '多行文本 textarea', meaning: 'STRING/TEXT；rows 2–20 默认 3，maxlength 1–20000。', notes: '长文本建议使用 TEXT。' },
                { option: '富文本 rich_text', meaning: 'TEXT；height 120–1000 默认 200。', notes: '注意内容安全与大字段性能。' },
                { option: '数字 number', meaning: 'INTEGER/LONG/DECIMAL/DOUBLE；min、max、precision 0–10 默认 0、step 默认 1、controls 默认 true。', notes: 'precision 应与实体 DECIMAL 小数位一致。' },
                { option: '日期 / 日期时间', meaning: 'DATE 使用 date，DATETIME 使用 datetime。', notes: '确认时区与格式。' },
                { option: '下拉、单选、复选', meaning: 'select、select_multiple、radio、checkbox 使用实体选项。', notes: '值联动和选项联动依赖稳定 value。' },
                { option: '开关 switch', meaning: 'BOOLEAN；activeText、inactiveText 可选。', notes: '显示文本不改变实际布尔值。' },
                { option: '文件 / 图片', meaning: 'FILE/IMAGE 使用上传组件。', notes: '限制来自实体附件项配置。' },
                { option: '级联 cascader', meaning: 'STRING/MULTI_SELECT；cascaderOptions 为 JSON。', notes: '不要依赖组件内示例数据，生产必须配置真实选项。' },
                { option: '实体引用 / 子表单 / 分组标题', meaning: 'reference、multi_reference、sub_form、section。', notes: '详细参数见对应主题。' }
              ]
            }
          ]
        },
        {
          id: 'entity-form-validation-mode',
          title: '结构化校验与运行模式权限',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '最小长度 / 最大长度', meaning: '字符串长度校验。', defaultLimit: '0–20000；可留空；最小不能大于最大。', effect: '提交前阻止不符合长度的数据。', publish: '应小于等于数据库字段长度。' },
                { field: '最小值 / 最大值', meaning: '数字范围校验。', defaultLimit: '可留空；最小不能大于最大。', effect: '限制数字输入。', publish: '与业务单位和小数精度一致。' },
                { field: '格式', meaning: '预置格式校验。', defaultLimit: 'EMAIL、PHONE、URL 或空。', effect: '校验常见文本格式。', publish: '格式仅校验形态，不验证邮箱/手机号真实存在。' },
                { field: '新增 create', meaning: '新增记录模式。', defaultLimit: '显示、可编辑默认均为 true。', effect: '控制新增表单字段。', publish: '关键创建字段不可隐藏或只读。' },
                { field: '编辑 edit', meaning: '编辑记录模式。', defaultLimit: '显示、可编辑默认均为 true。', effect: '控制编辑页面字段。', publish: '编码、流程字段等通常应只读。' },
                { field: '审批 approve', meaning: '审批办理模式。', defaultLimit: '显示、可编辑默认均为 true。', effect: '控制节点表单办理体验。', publish: '结合节点只读开关和审批字段权限测试。' },
                { field: '查看 view', meaning: '查看详情模式。', defaultLimit: '显示、可编辑默认均为 true，但页面通常整体只读。', effect: '控制详情可见性。', publish: '敏感字段需要关闭 visible，而不仅是 editable。' }
              ]
            }
          ]
        },
        {
          id: 'entity-form-subform-reference',
          title: '表单中的子表单与引用',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '子实体 / 关系 / 外键', meaning: '从实体关系只读回显。', defaultLimit: '由实体字段决定。', effect: '防止表单层破坏数据模型。', publish: '需要改关系时返回实体设计。' },
                { field: '显示', meaning: 'embedded 嵌入或 tab 页签。', defaultLimit: '默认 embedded。', effect: '页签模式在画布底部独立 Tab 展示。', publish: '移动端页签过多会降低可用性。' },
                { field: '布局', meaning: 'form 分行或 table 表格。', defaultLimit: '默认 form。', effect: '一对多明细可使用表格。', publish: '字段较多或含附件时优先分行。' },
                { field: '子表表单', meaning: '指定子实体表单。', defaultLimit: '留空使用默认表单；不能选择当前正在编辑表单。', effect: '决定子记录字段与布局。', publish: '被选表单必须启用且在目标环境存在。' },
                { field: '引用类型 / 关联实体', meaning: '来源于实体字段，已有 fieldId 时禁改。', defaultLimit: 'CUSTOM、USER、DEPT、ROLE、GROUP。', effect: '决定候选数据源。', publish: '需要改来源时返回实体设计。' },
                { field: '数据接口', meaning: '定制引用候选查询 URL。', defaultLimit: '可选；空时使用默认查询。', effect: '限制或扩展引用可选数据。', publish: '接口必须鉴权、分页并在目标环境可访问。' }
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'entity-form-init-linkage',
      index: '09',
      title: '表单初始化、联动与事件',
      summary: '定义打开表单时的数据来源，以及字段间动态行为和脚本事件。',
      topics: [
        {
          id: 'entity-form-init',
          title: '表单初始化配置',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '无', meaning: '不额外加载初始化数据。', notes: '使用实体默认值、表单默认值和路由已有数据。' },
                { option: 'API', meaning: '请求地址；GET/POST；响应路径；Query 参数 JSON；请求体 JSON；字段映射 JSON。', notes: '默认 GET；响应路径留空取根。支持示例模板 {{routeQuery.projectId}}，接口必须鉴权并返回稳定结构。' },
                { option: '实体', meaning: '选择目标实体、取第几条、过滤参数 JSON、字段映射 JSON。', notes: 'index 0–100，默认 0；应保证过滤结果排序稳定，否则“第几条”不确定。' },
                { option: '静态', meaning: '填写一个 JSON 对象作为固定初始值。', notes: '适合状态、类型等固定默认值；必须是合法 JSON。' },
                { option: '自定义', meaning: '填写已注册初始化器名称和参数 JSON。', notes: '目标环境必须部署同名初始化器。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: 'JSON 输入',
              text: '当前界面对 JSON 使用安全解析，解析失败可能退化为空对象。保存前应在外部校验 JSON，并用真实路由参数、权限和异常响应测试。初始化只应填充用户有权看到的数据。'
            }
          ]
        },
        {
          id: 'entity-linkage-visibility-value',
          title: '显隐与值联动',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '显隐控制', meaning: '满足条件时显示当前字段。', defaultLimit: '默认关闭；条件可选字段、==、!=、>、<、>=、<=、contains、empty、notEmpty；组合默认 AND，可选 OR。', effect: '表单值变化时动态显示/隐藏。', publish: '被依赖字段编码必须稳定；隐藏字段是否清值需按运行时验证。' },
                { field: '值联动：字段值', meaning: '源字段值映射到当前字段目标值。', defaultLimit: '默认关闭；来源默认 field；可配置多条 sourceValue → targetValue。', effect: '源字段命中映射后自动填值。', publish: '映射使用存储值，不是显示 label。' },
                { field: '值联动：API', meaning: '通过接口查询当前字段值。', defaultLimit: '接口地址、请求参数 JSON 字符串、结果字段路径。', effect: '源字段变化后调用接口并取结果。', publish: '当前联动规则持久化重点覆盖公式和映射；API 配置上线前必须确认运行时已实现并实际保存。' },
                { field: '值联动：公式', meaning: '根据其他字段计算。', defaultLimit: '支持 + - * / ( )，使用 ${fieldCode}。', effect: '字段变化时重新计算。', publish: '空值、除零和字符串转数字必须测试。' }
              ]
            }
          ]
        },
        {
          id: 'entity-linkage-options-calc',
          title: '选项联动、计算、禁用与必填',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '选项联动', meaning: '依赖字段不同值时，只显示指定选项。', defaultLimit: '默认关闭；配置依赖字段和多条 dependValue → allowedOptions。', effect: '动态过滤下拉、单选或复选选项。', publish: 'allowedOptions 使用选项 value；删除 value 前维护规则。' },
                { field: '计算字段', meaning: '按公式自动计算当前字段。', defaultLimit: '默认关闭；精度默认 2，范围 0–10；可编辑默认关闭。', effect: '自动写入计算结果；可编辑关闭时用户不能覆盖。', publish: '公式字段应与实体类型、精度和后端计算保持一致。' },
                { field: '禁用条件', meaning: '表达式为真时禁用当前字段。', defaultLimit: '默认关闭；示例 ${status} == \'locked\'。', effect: '字段可见但不可编辑。', publish: '不能把安全控制只放前端，后端仍需校验。' },
                { field: '必填条件', meaning: '表达式为真时动态必填。', defaultLimit: '默认关闭。', effect: '提交时要求填写。', publish: '条件字段隐藏或为空时要验证逻辑。' },
                { field: '保存 / 重置', meaning: '保存将规则展开到字段并写入 componentProps；重置清空当前面板配置。', defaultLimit: '重置不代表已持久化，仍需保存表单。', effect: '表单预览即时读取联动规则。', publish: '配置弹窗保存后还必须点击页面顶部“保存”。' }
              ]
            }
          ]
        },
        {
          id: 'entity-field-events',
          title: '字段事件',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'onChange', meaning: '字段值变化时触发。', notes: '适合轻量联动和提示；复杂数据联动优先使用结构化联动。' },
                { option: 'onBlur', meaning: '字段失焦时触发。', notes: '适合延迟校验或格式化。' },
                { option: 'onFocus', meaning: '字段获得焦点时触发。', notes: '避免执行耗时请求。' },
                { option: '自定义事件', meaning: '添加任意事件名与描述，建议以 on 开头。', notes: '只有渲染组件真正触发该事件时脚本才会运行。' },
                { option: '脚本变量', meaning: '代码中可使用 value 当前值和 field 字段配置。', notes: '事件脚本是前端代码，不能作为唯一权限或数据完整性保障；禁止写入敏感密钥。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '事件发布检查',
              text: '复制表单会连同事件配置一起复制。发布前逐个检查脚本是否引用了旧字段、旧接口和不存在的全局对象，并在新增、编辑、审批、查看四种模式下验证。'
            }
          ]
        }
      ]
    },
    {
      id: 'entity-list-config',
      index: '10',
      title: '列表配置',
      summary: '维护列表方案、查询区域、表格、分页、字段、动态列和预览。',
      topics: [
        {
          id: 'entity-list-config-manage',
          title: '列表方案管理',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '列表名称', meaning: '业务显示名。', defaultLimit: '必填。', effect: '菜单或页面选择时显示。', publish: '可改名，不影响 listKey 引用。' },
                { field: '列表标识', meaning: '稳定技术标识。', defaultLimit: '创建后禁改；后端要求字母开头，可含字母、数字、下划线、短横线，最大 100。', effect: 'URL、菜单和运行时选择列表配置。', publish: '上线后不要改。' },
                { field: '说明', meaning: '描述适用角色和数据范围。', defaultLimit: '可选。', effect: '帮助管理员区分列表。', publish: '建议注明配套权限规则。' },
                { field: '默认列表', meaning: '实体的默认展示方案。', defaultLimit: '开关；一个实体应有一个默认方案。', effect: '未指定 listKey 时优先使用。', publish: '切换默认会影响通用入口。' }
              ]
            },
            {
              type: 'bullets',
              items: [
                '“设计”进入列表设计器；“编辑”只改方案基本信息；“删除”前检查菜单、权限规则和页面引用。',
                '列表配置保存后会影响运行时列表，无需重新发布实体才能看到 UI 变化；迁移到其他环境仍应通过配置发布包。'
              ]
            }
          ]
        },
        {
          id: 'entity-list-view-settings',
          title: '列表设置',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '自定义列表组件', meaning: '使用注册组件替代默认动态列表。', defaultLimit: '默认空；可筛选、手工输入、清空；标识格式与扩展名规则一致。', effect: '整体列表由组件渲染。', publish: '目标环境必须注册同名组件并兼容运行时契约。' },
                { field: '组件参数', meaning: '按组件 configSchema 编辑。', defaultLimit: '组件声明 schema 时显示。', effect: '传入 viewConfig.customComponentProps。', publish: '迁移时确保组件版本一致。' },
                { field: '默认显示条件', meaning: '查询区初始展开的条件数量。', defaultLimit: '1–20，默认 4。', effect: '多余条件可折叠。', publish: '高频条件排在前面。' },
                { field: '允许展开收起', meaning: '查询区是否可折叠。', defaultLimit: '默认开启。', effect: '节省页面空间。', publish: '关闭时所有查询项常驻。' },
                { field: '标签宽度', meaning: '查询项标签宽度。', defaultLimit: '60–240 px，默认 100。', effect: '控制条件对齐。', publish: '长标签与移动端需验证。' },
                { field: '表格样式', meaning: '斑马纹、边框、序号列。', defaultLimit: '默认斑马纹开、边框关、序号列开。', effect: '改变表格视觉。', publish: '数据密集页面建议紧凑且保留溢出提示。' },
                { field: '表格尺寸', meaning: 'small/default/large。', defaultLimit: '默认 default。', effect: '控制行高和密度。', publish: '移动端建议 small。' },
                { field: '默认每页', meaning: '初始 pageSize。', defaultLimit: '默认 10；可从 10/20/50/100 中选。', effect: '控制首次请求数量。', publish: '大页会增加接口和渲染压力。' }
              ]
            }
          ]
        },
        {
          id: 'entity-list-fields',
          title: '字段与查询方式',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '字段名称', meaning: '当前列表列名或查询名。', defaultLimit: '默认实体字段名称，可单独改。', effect: '只影响此列表显示。', publish: '语义改变时同步文档。' },
                { field: '字段编码', meaning: '取值和查询键。', defaultLimit: '实体字段禁改；虚拟列可改且必须字母开头、字母数字下划线，最大 100；列表内不区分大小写唯一。', effect: '用于数据源、列渲染和查询参数。', publish: '虚拟列编码被自定义组件引用后保持稳定。' },
                { field: '加入列表', meaning: '是否显示为列。', defaultLimit: '按实体字段默认配置；虚拟列默认开启。', effect: '关闭后仍可作为查询条件。', publish: '敏感字段不要仅通过关闭列隐藏，还要配置权限。' },
                { field: '查询条件', meaning: '是否进入查询区。', defaultLimit: '数据源不支持查询时禁用。', effect: '生成查询控件和操作符参数。', publish: '单列表字段最多 200 个，不宜配置过多查询项。' },
                { field: '查询方式', meaning: '决定后端比较操作。', defaultLimit: 'EQ、NE、LIKE、NOT_LIKE、GT、GE、LT、LE、BETWEEN、IN、NOT_IN、EMPTY、NOT_EMPTY。实体字段默认 LIKE，虚拟列默认 EQ。', effect: '请求会附加 fieldCode_op。', publish: '操作符必须与字段类型和数据源能力匹配。' },
                { field: '宽度', meaning: '固定列宽。', defaultLimit: '0–500；0 表示使用最小宽度。', effect: '显示列按指定像素宽度。', publish: '过窄依赖溢出提示。' },
                { field: '对齐', meaning: 'left/center/right。', defaultLimit: '默认 left。', effect: '数字通常右对齐，状态居中。', publish: '无数据影响。' },
                { field: '拖拽排序', meaning: '调整查询项和列顺序。', defaultLimit: '任意。', effect: '保存时写入 sortOrder。', publish: '高频查询和关键列靠前。' }
              ]
            }
          ]
        },
        {
          id: 'entity-list-dynamic-fields',
          title: '动态字段与虚拟列',
          lead: '动态列通过数据源提供器补充当前行数据，虚拟列不对应实体物理字段。',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: 'ENTITY_FIELD', meaning: '直接读取系统字段或自定义实体字段；支持查询，不支持虚拟列。', notes: '实体字段自动使用该数据源，数据源配置为空。' },
                { option: 'FIELD_TEMPLATE', meaning: '使用 ${fieldCode} 占位符安全组合当前行字段，不执行脚本。支持虚拟列和查询。', notes: '组合模板必填，默认 ${dataNo} - ${name}；空值替代默认“-”。可读取 data、extData 及 id/dataNo/name/title/status/submitterName。' },
                { option: '自定义数据源', meaning: '由后端 ListFieldDataProvider 注册，可声明是否支持虚拟列、查询和 configSchema。', notes: '目标环境必须注册同一编码；未注册会导致保存校验或运行加载失败。' }
              ]
            },
            {
              type: 'table',
              title: '虚拟列操作',
              columns: fieldColumns,
              rows: [
                { field: '添加虚拟列', meaning: '创建不对应实体字段的新列。', defaultLimit: '默认名称“虚拟列”、编码 virtual_时间戳、STRING、显示开启、查询关闭、EQ、左对齐、DefaultText。', effect: '选择第一个支持虚拟列的数据源。', publish: '修改为稳定编码，并配置数据源必填参数。' },
                { field: '数据源高级配置', meaning: '按数据源 schema 编辑参数。', defaultLimit: 'ENTITY_FIELD 无额外参数。', effect: '决定动态值如何生成。', publish: '必须是合法 JSON 对象；必填项不可为空。' },
                { field: '渲染组件', meaning: 'DefaultText、StatusBadge、DateFormatter 或扩展组件。', defaultLimit: '留空自动；虚拟列默认 DefaultText。', effect: '控制单元格展示。', publish: '组件必须在目标环境注册。' },
                { field: '查询组件', meaning: '覆盖自动匹配的表单组件。', defaultLimit: '默认空；还可配置占位提示和默认值。', effect: '改变查询区输入控件。', publish: '组件必须支持字段类型和查询值格式。' },
                { field: '列展示', meaning: '固定左/右、最小宽度、溢出提示。', defaultLimit: '默认不固定、最小宽 100、溢出提示开启；最小宽 60–1000。', effect: '控制滚动和长文本提示。', publish: '左右固定列过多会压缩内容区。' }
              ]
            },
            {
              type: 'table',
              title: '内置单元格组件参数',
              columns: optionColumns,
              rows: [
                { option: 'DefaultText', meaning: '安全显示文本、数字和通用值。', notes: 'emptyText 默认“-”。' },
                { option: 'StatusBadge', meaning: '标签显示状态。', notes: 'size 默认 small；labelMap 和 statusMap 为 JSON 映射。' },
                { option: 'DateFormatter', meaning: '按安全模板格式化日期。', notes: 'pattern 默认 yyyy-MM-dd HH:mm:ss，不执行表达式。' }
              ]
            }
          ]
        },
        {
          id: 'entity-list-preview',
          title: '实时预览',
          blocks: [
            {
              type: 'bullets',
              items: [
                '预览按当前未保存配置生成查询区、表格列、动态单元格和分页，便于即时检查。',
                'BETWEEN 对 DATE 使用起止日期，其他类型使用两个输入框；SELECT 读取实体选项；其他类型使用输入框。',
                '预览接口会加载带列表配置扩展的数据，页面再进行内存分页；真实运行页面可能使用不同分页策略。',
                '预览无数据时先确认实体已发布、有数据、数据权限允许、动态数据源未报错。',
                '预览通过后仍需点击“保存配置”。'
              ]
            }
          ]
        }
      ]
    },
    {
      id: 'entity-list-buttons',
      index: '11',
      title: '列表按钮、权限与适用条件',
      summary: '分别配置工具栏按钮和行操作按钮，并同时控制功能权限与记录级可操作条件。',
      topics: [
        {
          id: 'entity-button-common',
          title: '按钮公共配置',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '排序', meaning: '按钮显示顺序。', defaultLimit: '0–999。', effect: '按数字升序显示。', publish: '无数据影响。' },
                { field: '启用', meaning: '按钮开关。', defaultLimit: '新增按钮默认启用。', effect: '关闭后不显示或不参与运行。', publish: '可作为临时下线手段。' },
                { field: '按钮名称', meaning: '用户看到的文字。', defaultLimit: '可编辑。', effect: '改变按钮文案。', publish: '内置功能改名不会改变 key。' },
                { field: '类型', meaning: 'built-in 内置或 custom 自定义。', defaultLimit: '按添加方式决定。', effect: '内置走平台逻辑，自定义走执行器或组件。', publish: '不要把内置按钮改成自定义却保留旧 key。' },
                { field: '内置类型 / 执行器', meaning: '内置选择固定 key；自定义填写处理器或组件名。', defaultLimit: '自定义必填执行器/组件名。', effect: '决定点击行为。', publish: '目标环境必须注册对应扩展。' },
                { field: '自定义模式', meaning: '工具栏自定义按钮选择 handler 函数或 component 组件。', defaultLimit: '默认 handler；行按钮不显示该列。', effect: '决定运行时扩展方式。', publish: '组件模式需兼容列表上下文。' },
                { field: '图标', meaning: 'Element Plus 图标名。', defaultLimit: '可空。', effect: '按钮显示图标。', publish: '图标名错误时通常只缺图标。' },
                { field: '样式', meaning: 'default、primary、success、warning、danger、info。', defaultLimit: '自定义默认 default。', effect: '改变颜色语义。', publish: '危险操作应使用 danger 并确认。' },
                { field: 'Link', meaning: '行按钮是否使用链接样式。', defaultLimit: '行自定义默认开启。', effect: '减少操作列视觉重量。', publish: '无权限影响。' },
                { field: '权限码', meaning: '功能级授权标识。', defaultLimit: '可从标准/自定义权限选择，也可手工输入；内置按钮会自动归一为标准权限。', effect: '用户无权限时按钮不可用或不显示。', publish: '角色必须被授予相应 F 类型权限资源。' },
                { field: '适用条件', meaning: '记录级或选择集级条件。', defaultLimit: '默认始终可操作，部分内置按钮有预设规则。', effect: '条件不满足时隐藏或禁用并说明。', publish: '与权限码、数据权限三者同时生效。' }
              ]
            }
          ]
        },
        {
          id: 'entity-button-builtins',
          title: '内置按钮与标准权限',
          blocks: [
            {
              type: 'table',
              columns: [
                { key: 'location', label: '位置' },
                { key: 'button', label: '内置按钮' },
                { key: 'permission', label: '自动权限码' },
                { key: 'defaultRule', label: '默认适用条件' }
              ],
              rows: [
                { location: '工具栏', button: '新增数据 create', permission: 'entity:{code}:create', defaultRule: '无；只检查功能权限和数据范围。' },
                { location: '工具栏', button: '导出选中 exportSelected', permission: 'entity:{code}:export', defaultRule: '无。' },
                { location: '工具栏', button: '导出全部 exportAll', permission: 'entity:{code}:export-all', defaultRule: '无；高风险权限应谨慎授权。' },
                { location: '工具栏', button: '批量删除 batchDelete', permission: 'entity:{code}:batch-delete', defaultRule: '当前用户是创建人或提交人，且为未发起+NEW 或 WITHDRAWN；不满足时禁用并提示选中数据中存在不可删除数据。' },
                { location: '行操作', button: '查看 view', permission: 'entity:{code}:view', defaultRule: '无。' },
                { location: '行操作', button: '编辑 edit', permission: 'entity:{code}:update', defaultRule: '无；可按业务补充草稿限制。' },
                { location: '行操作', button: '审批 approve', permission: 'entity:{code}:approve', defaultRule: '当前用户是当前办理人，且流程状态 RUNNING；不满足时隐藏。' },
                { location: '行操作', button: '删除 delete', permission: 'entity:{code}:delete', defaultRule: '当前用户是创建人或提交人，且为未发起+NEW 或 WITHDRAWN；不满足时隐藏。' }
              ]
            }
          ]
        },
        {
          id: 'entity-button-rules',
          title: '适用条件编辑器',
          blocks: [
            {
              type: 'table',
              columns: optionColumns,
              rows: [
                { option: '常用预设', meaning: '始终可操作、仅本人、仅本人未流转草稿、本人草稿或已撤回、当前办理人、流程进行中、本部门、指定状态。', notes: '应用预设会重建规则根节点，但保留“不满足时”行为。' },
                { option: '不满足时 HIDE', meaning: '直接隐藏按钮。', notes: '适合无意义或不应暴露的操作。' },
                { option: '不满足时 DISABLE', meaning: '保留按钮但禁用，并显示提示原因。', notes: '适合批量操作和需要解释规则的场景。' },
                { option: '提示原因', meaning: '用户无法操作时看到的说明。', notes: '写清主体、状态和下一步，不要只写“无权限”。' },
                { option: '条件组', meaning: 'AND 全部满足 / OR 任一满足，可嵌套到 5 层。', notes: '复杂规则优先拆分为多个有语义的小组。' }
              ]
            },
            {
              type: 'table',
              title: '条件类型',
              columns: optionColumns,
              rows: [
                { option: '当前用户关系 RELATION', meaning: '创建人、提交人、当前办理人、同部门。', notes: '依赖记录系统字段与当前任务信息。' },
                { option: '流程状态 PROCESS_STATE', meaning: 'NOT_STARTED、RUNNING、COMPLETED、TERMINATED、WITHDRAWN。', notes: '支持 EQ/NE。' },
                { option: '状态编码 STATUS_CODE', meaning: '按实体状态编码比较。', notes: '支持 EQ、NE、IN、NOT_IN；集合操作时多选。' },
                { option: '状态分类 STATUS_CATEGORY', meaning: 'NEW、PROCESSING、COMPLETED、TERMINATED、WITHDRAWN。', notes: '状态编码的分类变化会影响规则。' },
                { option: '当前用户属性 USER_FIELD', meaning: 'id、username、deptId、orgId、roleIds。', notes: '可用等于、不等于、集合、包含、空值和大小比较。' },
                { option: '数据字段 FIELD', meaning: '系统字段和实体自定义字段。', notes: '操作符包括 EQ、NE、IN、NOT_IN、CONTAINS、NOT_CONTAINS、EMPTY、NOT_EMPTY、GT、GTE、LT、LTE；多个值按逗号输入。' },
                { option: '扩展条件', meaning: '由前端注册自定义条件组件。', notes: '目标环境必须注册同一类型，后端也必须能评估。' }
              ]
            }
          ]
        },
        {
          id: 'entity-button-validation',
          title: '权限与按钮验证顺序',
          blocks: [
            {
              type: 'steps',
              items: [
                { title: '菜单/功能权限', text: '用户角色先具备 entity:{code}:action 权限。' },
                { title: '数据权限', text: '记录必须出现在用户可访问的数据范围内。' },
                { title: '适用条件', text: '再根据记录、流程、状态和当前用户关系判断按钮是否可操作。' },
                { title: '后端能力校验', text: '真正执行删除、审批、导出等操作时，后端仍会做业务校验。' }
              ]
            },
            {
              type: 'callout',
              tone: 'warning',
              title: '不要只看按钮是否显示',
              text: '按钮隐藏是用户体验，不是安全边界。生产验收必须直接调用对应操作验证后端拒绝无权限、非办理人、错误状态和越权数据。'
            }
          ]
        }
      ]
    },
    {
      id: 'entity-publish',
      index: '12',
      title: '发布、版本与迁移',
      summary: '把草稿配置转为物理表和不可变版本，并可加入配置迁移待导出清单。',
      topics: [
        {
          id: 'entity-publish-preview',
          title: '发布差异预览',
          blocks: [
            {
              type: 'bullets',
              items: [
                '首次发布：当前版本 V0 → V1；所有字段列为新增，并预览 CREATE TABLE DDL。',
                '重新发布：比较最新已发布字段快照和当前字段，显示新增、修改、无变更以及理论上的删除字段。',
                'DDL 预览主要针对创建表和新增列；字段名称、必填、唯一、默认值、长度、精度等元数据变化也会出现在修改说明中。',
                '已发布字段禁止在 UI 删除；如果差异中出现删除字段，应暂停发布并排查历史数据或手工改库。',
                '发布前确认表单、列表、权限和状态已经保存；实体版本快照会收集关联配置用于迁移。'
              ]
            }
          ]
        },
        {
          id: 'entity-publish-fields',
          title: '发布与迁移字段',
          blocks: [
            {
              type: 'table',
              columns: fieldColumns,
              rows: [
                { field: '发布说明', meaning: '描述本次实体、表单或列表变更。', defaultLimit: '可选，建议必填。', effect: '写入版本历史。', publish: '使用可审计内容，例如需求号、字段、影响页面和兼容策略。' },
                { field: '待导出清单', meaning: '发布后生成不可变迁移快照。', defaultLimit: '默认开启。', effect: '资产出现在“系统管理 / 配置迁移”。', publish: '不需要跨环境时也建议保留可追溯快照。' },
                { field: '迁移标记', meaning: '关联同一批发布资产。', defaultLimit: '加入待导出时必填；示例 REL-20260716-001。', effect: '用于筛选和组装发布包。', publish: '实体与配套流程应使用同一发布批次标记。' },
                { field: '确认发布 / 重新发布', meaning: '执行物理表同步、标记字段已发布、创建版本历史和迁移资产。', defaultLimit: '发布期间按钮加载锁定。', effect: '实体状态变为 PUBLISHED，版本递增。', publish: '数据库 DDL 失败时不要重复盲点，先核查事务、表结构和日志。' }
              ]
            }
          ]
        },
        {
          id: 'entity-version-history',
          title: '版本历史与差异',
          blocks: [
            {
              type: 'bullets',
              items: [
                '时间线展示版本号、首次发布/结构变更、发布说明、发布人、发布时间和变更摘要。',
                '字段详情包含编码、名称、类型、数据库类型、必填和系统字段标记。',
                '点击非最早版本可查看与上一版本的新增、修改、删除和无变更字段。',
                '版本历史是审计和配置迁移依据，不等于自动数据库回滚能力。',
                '版本中保存的流程绑定会随最新实体发布历史更新；切换流程前仍要遵守已有数据限制。'
              ]
            }
          ]
        },
        {
          id: 'entity-release-checklist',
          title: '上线检查清单',
          blocks: [
            {
              type: 'checklist',
              items: [
                '实体编码、字段编码、状态编码和列表/表单标识均稳定且无重复。',
                '已发布字段没有尝试删除或改类型；新增必填字段已有历史数据补值方案。',
                '关系子实体和外键存在，级联删除策略经过业务确认。',
                '附件大小、数量与全局网关、应用、存储限制一致。',
                '默认表单、默认列表已设置，表单四种模式和移动端已验证。',
                '初始化接口、引用接口、事件脚本和自定义组件在目标环境可用。',
                '数据权限 SQL 由普通角色验证，无规则默认仅本人符合预期。',
                '按钮权限已授予角色，适用条件与后端操作校验一致。',
                '流程绑定、节点表单和实体状态连线配置完整。',
                '发布差异、DDL、版本说明、迁移标记和配置包依赖均已复核。'
              ]
            }
          ]
        }
      ]
    }
  ]
}
