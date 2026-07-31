import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const moduleRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const resourcesRoot = path.join(moduleRoot, "src/main/resources");
const entityDir = path.join(resourcesRoot, "project-config/assets/entities");
const processDir = path.join(resourcesRoot, "project-config/assets/processes");
const bpmnDir = path.join(resourcesRoot, "project-config/bpmn");

const optionJson = (items) => JSON.stringify(
  items.map(([label, value]) => ({ label, value }))
);
const linkage = (rules) => JSON.stringify({ linkageRules: rules });
const matchAll = JSON.stringify({
  version: 1,
  logic: "OR",
  conditions: [{ scopeType: "ALL_USERS", operator: "ANY" }]
});

const field = (
  fieldCode,
  fieldName,
  fieldType,
  dbType,
  isRequired,
  sortOrder,
  extra = {}
) => ({
  fieldCode,
  fieldName,
  fieldType,
  dbType,
  isRequired,
  sortOrder,
  ...extra
});

const refField = (
  fieldCode,
  fieldName,
  refEntityType,
  refEntityCode,
  isRequired,
  sortOrder,
  extra = {}
) => field(
  fieldCode,
  fieldName,
  "REFERENCE",
  "varchar(64)",
  isRequired,
  sortOrder,
  {
    fieldLength: 64,
    refEntityType,
    ...(refEntityCode ? { refEntityCode } : {}),
    ...extra
  }
);

const formField = (
  fieldCode,
  fieldName,
  fieldType,
  sortOrder,
  {
    required = false,
    readonly = false,
    hidden = false,
    componentType = "input",
    gridSpan = 12,
    placeholder,
    componentProps,
    validationRules,
    defaultValue
  } = {}
) => ({
  fieldCode,
  fieldName,
  fieldLabel: fieldName,
  fieldType,
  sortOrder,
  isRequired: required ? 1 : 0,
  isReadonly: readonly ? 1 : 0,
  isHidden: hidden ? 1 : 0,
  componentType,
  gridSpan,
  ...(placeholder ? { placeholder } : {}),
  ...(componentProps ? { componentProps } : {}),
  ...(validationRules ? { validationRules } : {}),
  ...(defaultValue !== undefined ? { defaultValue } : {})
});

const listField = (
  fieldCode,
  fieldName,
  sortOrder,
  width,
  {
    query = true,
    queryType = "EQ",
    align = "left"
  } = {}
) => ({
  fieldCode,
  fieldName,
  sortOrder,
  width,
  showInList: true,
  isQuery: query,
  queryType,
  align,
  dataSourceType: "ENTITY_FIELD"
});

const status = (statusCode, statusName, statusCategory, sortOrder, color) => ({
  statusCode,
  statusName,
  statusCategory,
  sortOrder,
  color
});

const form = (
  formName,
  formKey,
  description,
  fields,
  {
    isDefault = false,
    readonly = false,
    submitLabel,
    customComponent,
    customComponentVersion = 1,
    customComponentSnapshotVersion = 1,
    customComponentProps = {}
  } = {}
) => ({
  formName,
  formKey,
  description,
  layoutType: "grid",
  isDefault,
  status: 1,
  ...(customComponent ? {
    customComponent,
    customComponentVersion,
    customComponentSnapshotVersion
  } : {}),
  viewConfig: JSON.stringify({
    labelWidth: 140,
    columns: 2,
    ...(readonly ? { readonly: true } : {}),
    ...(submitLabel ? { submitLabel } : {}),
    ...(customComponent ? { customComponentProps } : {})
  }),
  fields,
  nodes: []
});

const defaultRowActions = [
  {
    key: "view",
    type: "built-in",
    label: "查看",
    buttonType: "primary",
    link: true,
    sort: 1,
    enabled: true
  },
  {
    key: "edit",
    type: "built-in",
    label: "编辑",
    buttonType: "primary",
    link: true,
    sort: 2,
    enabled: true
  }
];

const entityList = (
  listKey,
  listName,
  description,
  fields,
  {
    isDefault = false,
    dataScopeMode = "INHERIT",
    allowedScenes = ["PAGE", "SELECTION"],
    createLabel,
    fixedFilterConfig = {},
    rowActions = defaultRowActions,
    selectionMode = "MULTIPLE",
    contextBindingConfig = {}
  } = {}
) => ({
  listKey,
  listName,
  description,
  isDefault,
  dataScopeMode,
  allowedScenes,
  selectionConfig: {
    selectionMode,
    valueField: "id",
    returnMappings: []
  },
  toolbarConfig: createLabel
    ? [{
        key: "create",
        type: "built-in",
        label: createLabel,
        icon: "Plus",
        buttonType: "primary",
        sort: 1,
        enabled: true
      }]
    : [],
  rowActionConfig: rowActions,
  viewConfig: {
    density: "compact",
    showIndex: true,
    stickyActions: true
  },
  fixedFilterConfig,
  contextBindingConfig,
  fields
});

const policy = (
  policyKey,
  policyName,
  description,
  presetCode,
  filterConfig
) => ({
  policyKey,
  policyName,
  description,
  presetCode,
  filterConfig: JSON.stringify(filterConfig),
  enabled: 1,
  version: 1
});

const binding = (policyKey, listKey = null) => ({
  policyKey,
  listKey,
  matchConfig: matchAll,
  ruleEffect: "ALLOW",
  enabled: 1
});

const menu = (
  menuName,
  pathValue,
  perm,
  listKey,
  sort,
  icon = "List"
) => ({
  menuName,
  menuType: "C",
  icon,
  sort,
  path: pathValue,
  component: "entity/EntityListRuntime",
  perm,
  status: "0",
  visible: "0",
  isFrame: "0",
  isCache: "0",
  resourceType: "ENTITY_LIST",
  listKey,
  parentPath: "/project-management"
});

const codeRule = (prefix, example) => ({
  prefix,
  dateFormat: "yyyyMMdd",
  seqLength: 5,
  seqType: "DAY",
  currentSeq: 0,
  seqDate: "",
  example
});

const baseEntity = ({
  businessKey,
  assetName,
  description,
  lifecycleMode = "STANDALONE",
  processKey = null,
  fields,
  relations = [],
  statuses,
  codePrefix,
  codeExample,
  forms,
  lists,
  scopePolicies,
  scopeBindings,
  menus = [],
  extensions = [],
  dependencies = []
}) => ({
  schemaVersion: 1,
  assetType: "ENTITY",
  businessKey,
  assetName,
  definition: {
    entityCode: businessKey,
    entityName: assetName,
    description,
    lifecycleMode,
    storageMode: "DYNAMIC",
    processKey
  },
  fields,
  relations,
  statuses,
  codeRule: codeRule(codePrefix, codeExample),
  extensions,
  dataSources: [],
  forms,
  lists,
  scopePolicies,
  scopeBindings,
  menus,
  dependencies
});

const creatorPolicy = (entityCode, entityName) => policy(
  `${entityCode}_creator`,
  `本人创建的${entityName}`,
  `创建人可以查看本人创建的${entityName}。`,
  "CREATOR",
  { version: 1, type: "PERSONAL" }
);

const userFieldPolicy = (
  policyKey,
  policyName,
  description,
  presetCode,
  userField
) => policy(
  policyKey,
  policyName,
  description,
  presetCode,
  {
    version: 1,
    type: "PERSONAL",
    fieldMapping: {
      userField,
      deptField: "dept_id",
      statusField: "status"
    }
  }
);

const workflowCommonFields = (startOrder) => [
  refField("applicant_id", "申请人", "USER", null, true, startOrder),
  refField("applicant_dept_id", "申请部门", "DEPT", null, true, startOrder + 10),
  field("version", "业务版本", "INTEGER", "int", true, startOrder + 20, {
    defaultValue: "1"
  }),
  field("submitted_at", "业务提交时间", "DATETIME", "datetime", false, startOrder + 30),
  field("approved_at", "批准时间", "DATETIME", "datetime", false, startOrder + 40)
];

const projectGroup = baseEntity({
  businessKey: "project_group",
  assetName: "项目群",
  description: "用于归集战略、业务域和年度计划下的多个软件项目。",
  fields: [
    field("group_type", "项目群类型", "SELECT", "varchar(30)", true, 20, {
      fieldLength: 30,
      optionsJson: optionJson([
        ["战略项目群", "STRATEGIC"],
        ["业务域项目群", "BUSINESS_DOMAIN"],
        ["年度计划项目群", "ANNUAL_PLAN"]
      ])
    }),
    refField("sponsor_id", "项目群发起人", "USER", null, true, 30),
    refField("pmo_owner_id", "PMO负责人", "USER", null, true, 40),
    field("objective", "项目群目标", "TEXT", "text", true, 50),
    field("planned_start_date", "计划开始日期", "DATE", "date", true, 60),
    field("planned_end_date", "计划结束日期", "DATE", "date", true, 70)
  ],
  statuses: [
    status("PLANNING", "规划中", "NEW", 10, "#909399"),
    status("ACTIVE", "进行中", "PROCESSING", 20, "#409EFF"),
    status("CLOSED", "已关闭", "COMPLETED", 30, "#67C23A"),
    status("CANCELLED", "已取消", "TERMINATED", 40, "#606266")
  ],
  codePrefix: "PRJG",
  codeExample: "PRJG2026072800001",
  forms: [
    form("项目群维护表单", "project_group_default", "维护项目群基本信息。", [
      formField("name", "项目群名称", "STRING", 10, {
        required: true,
        gridSpan: 16
      }),
      formField("group_type", "项目群类型", "SELECT", 20, {
        required: true,
        componentType: "select",
        gridSpan: 8
      }),
      formField("sponsor_id", "项目群发起人", "USER", 30, {
        required: true,
        componentType: "user"
      }),
      formField("pmo_owner_id", "PMO负责人", "USER", 40, {
        required: true,
        componentType: "user"
      }),
      formField("objective", "项目群目标", "TEXT", 50, {
        required: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("planned_start_date", "计划开始日期", "DATE", 60, {
        required: true,
        componentType: "date"
      }),
      formField("planned_end_date", "计划结束日期", "DATE", 70, {
        required: true,
        componentType: "date"
      })
    ], { isDefault: true })
  ],
  lists: [
    entityList("all", "项目群台账", "项目群治理台账。", [
      listField("code", "项目群编号", 10, 170, { queryType: "LIKE" }),
      listField("name", "项目群名称", 20, 220, { queryType: "LIKE" }),
      listField("group_type", "项目群类型", 30, 130),
      listField("sponsor_id", "发起人", 40, 130),
      listField("pmo_owner_id", "PMO负责人", 50, 130),
      listField("planned_start_date", "计划开始", 60, 120, { queryType: "BETWEEN", align: "center" }),
      listField("planned_end_date", "计划结束", 70, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 80, 100, { queryType: "IN", align: "center" })
    ], { isDefault: true, createLabel: "新建项目群" })
  ],
  scopePolicies: [
    creatorPolicy("project_group", "项目群"),
    userFieldPolicy(
      "project_group_pmo_owner",
      "PMO负责的项目群",
      "PMO负责人字段为当前用户的数据。",
      "PMO_OWNER",
      "pmo_owner_id"
    )
  ],
  scopeBindings: [
    binding("project_group_creator"),
    binding("project_group_pmo_owner")
  ],
  menus: [
    menu(
      "项目群",
      "/entity-list/project_group/all",
      "entity:project_group:list",
      "all",
      45,
      "Collection"
    )
  ]
});

const requirementProjectLink = baseEntity({
  businessKey: "requirement_project_link",
  assetName: "需求项目关系",
  description: "记录需求被项目承接的范围、比例、计划和交付状态。",
  fields: [
    refField("requirement_id", "需求", "CUSTOM", "requirement", true, 20),
    refField("project_id", "项目", "CUSTOM", "project", true, 30),
    field("relation_role", "承接角色", "SELECT", "varchar(20)", true, 40, {
      fieldLength: 20,
      defaultValue: "PRIMARY",
      optionsJson: optionJson([
        ["主承接", "PRIMARY"],
        ["协同承接", "SUPPORTING"],
        ["依赖项目", "DEPENDENCY"]
      ])
    }),
    field("delivery_scope", "承接范围", "TEXT", "text", true, 50),
    field("allocation_percentage", "分配比例(%)", "DECIMAL", "decimal(5,2)", true, 60, {
      fieldPrecision: 2,
      validateRules: JSON.stringify({ min: 0.01, max: 100 })
    }),
    field("target_milestone_id", "目标里程碑", "STRING", "varchar(64)", false, 70, {
      fieldLength: 64
    }),
    field("target_release_id", "目标发布", "STRING", "varchar(64)", false, 80, {
      fieldLength: 64
    }),
    refField("responsible_member_id", "交付责任人", "CUSTOM", "project_member", false, 90),
    field("planned_start_date", "计划开始日期", "DATE", "date", true, 100),
    field("planned_end_date", "计划完成日期", "DATE", "date", true, 110),
    field("actual_completion_date", "实际完成日期", "DATE", "date", false, 120),
    field("completion_percentage", "完成比例(%)", "DECIMAL", "decimal(5,2)", true, 130, {
      defaultValue: "0",
      fieldPrecision: 2,
      validateRules: JSON.stringify({ min: 0, max: 100 })
    }),
    field("acceptance_record_id", "验收记录", "STRING", "varchar(64)", false, 140, {
      fieldLength: 64
    }),
    field("cancel_reason", "取消原因", "TEXT", "text", false, 150)
  ],
  statuses: [
    status("PROPOSED", "拟承接", "NEW", 10, "#909399"),
    status("APPROVED", "已批准", "COMPLETED", 20, "#67C23A"),
    status("DELIVERING", "交付中", "PROCESSING", 30, "#409EFF"),
    status("DELIVERED", "已交付", "PROCESSING", 40, "#36CFC9"),
    status("ACCEPTED", "已验收", "COMPLETED", 50, "#67C23A"),
    status("CANCELLED", "已取消", "TERMINATED", 60, "#606266")
  ],
  codePrefix: "REQPRJ",
  codeExample: "REQPRJ2026072800001",
  forms: [
    form("需求项目关系表单", "requirement_project_link_default", "项目立项时录入需求承接范围。", [
      formField("requirement_id", "需求", "REFERENCE", 10, {
        required: true,
        componentType: "reference",
        gridSpan: 12
      }),
      formField("relation_role", "承接角色", "SELECT", 20, {
        required: true,
        componentType: "select",
        gridSpan: 12
      }),
      formField("delivery_scope", "承接范围", "TEXT", 30, {
        required: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("allocation_percentage", "分配比例(%)", "DECIMAL", 40, {
        required: true,
        componentType: "number",
        gridSpan: 8
      }),
      formField("planned_start_date", "计划开始日期", "DATE", 50, {
        required: true,
        componentType: "date",
        gridSpan: 8
      }),
      formField("planned_end_date", "计划完成日期", "DATE", 60, {
        required: true,
        componentType: "date",
        gridSpan: 8
      }),
      formField("responsible_member_id", "交付责任人", "REFERENCE", 70, {
        componentType: "reference",
        gridSpan: 12
      }),
      formField("completion_percentage", "完成比例(%)", "DECIMAL", 80, {
        componentType: "number",
        gridSpan: 12
      })
    ], { isDefault: true })
  ],
  lists: [
    entityList("all", "需求项目关系台账", "按需求和项目追踪分配及交付。", [
      listField("code", "关系编号", 10, 170, { queryType: "LIKE" }),
      listField("requirement_id", "需求", 20, 200),
      listField("project_id", "项目", 30, 200),
      listField("relation_role", "承接角色", 40, 110),
      listField("allocation_percentage", "分配比例", 50, 100, { align: "right" }),
      listField("completion_percentage", "完成比例", 60, 100, { align: "right" }),
      listField("planned_end_date", "计划完成", 70, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 80, 110, { queryType: "IN", align: "center" })
    ], {
      isDefault: true,
      allowedScenes: ["PAGE", "EMBEDDED", "SELECTION"],
      contextBindingConfig: { parentField: "project_id" }
    })
  ],
  scopePolicies: [
    creatorPolicy("requirement_project_link", "需求项目关系")
  ],
  scopeBindings: [
    binding("requirement_project_link_creator")
  ],
  dependencies: [
    { type: "ENTITY", key: "requirement", required: true, reason: "承接需求" },
    { type: "ENTITY", key: "project", required: true, reason: "所属项目" },
    { type: "ENTITY", key: "project_member", required: true, reason: "交付责任人" }
  ]
});

const projectMember = baseEntity({
  businessKey: "project_member",
  assetName: "项目成员",
  description: "记录项目成员加入周期、投入比例、权限和交接状态。",
  fields: [
    refField("project_id", "项目", "CUSTOM", "project", true, 20),
    refField("user_id", "人员", "USER", null, true, 30),
    refField("source_dept_id", "来源部门", "DEPT", null, true, 40),
    field("employment_type", "人员类型", "SELECT", "varchar(20)", true, 50, {
      fieldLength: 20,
      defaultValue: "INTERNAL",
      optionsJson: optionJson([
        ["内部员工", "INTERNAL"],
        ["供应商", "VENDOR"],
        ["合同人员", "CONTRACTOR"],
        ["兼职投入", "PART_TIME"]
      ])
    }),
    field("join_date", "加入日期", "DATE", "date", true, 60),
    field("planned_leave_date", "计划退出日期", "DATE", "date", false, 70),
    field("actual_leave_date", "实际退出日期", "DATE", "date", false, 80),
    field("allocation_percentage", "投入比例(%)", "DECIMAL", "decimal(5,2)", true, 90, {
      defaultValue: "100",
      fieldPrecision: 2,
      validateRules: JSON.stringify({ min: 0, max: 100 })
    }),
    field("join_reason", "加入原因", "TEXT", "text", true, 100),
    field("leave_reason", "退出原因", "TEXT", "text", false, 110),
    refField("handover_user_id", "交接成员", "CUSTOM", "project_member", false, 120),
    field("handover_description", "交接说明", "TEXT", "text", false, 130),
    field("account_required_flag", "需要项目账号", "BOOLEAN", "tinyint", true, 140, {
      defaultValue: "true"
    }),
    field("environment_access_required_flag", "需要环境权限", "BOOLEAN", "tinyint", true, 150, {
      defaultValue: "false"
    }),
    field("environment_scope", "环境权限范围", "MULTI_SELECT", "varchar(500)", false, 160, {
      fieldLength: 500,
      optionsJson: optionJson([
        ["开发", "DEV"],
        ["测试", "TEST"],
        ["UAT", "UAT"],
        ["生产只读", "PROD_READ"],
        ["生产操作", "PROD_OPERATE"]
      ])
    }),
    field("access_revoked_flag", "权限已回收", "BOOLEAN", "tinyint", true, 170, {
      defaultValue: "false"
    }),
    field("handover_completed_flag", "交接已完成", "BOOLEAN", "tinyint", true, 180, {
      defaultValue: "false"
    }),
    field("source_process", "来源流程", "STRING", "varchar(50)", false, 190, {
      fieldLength: 50
    })
  ],
  statuses: [
    status("PENDING_JOIN", "待加入", "NEW", 10, "#909399"),
    status("ACTIVE", "在项目中", "COMPLETED", 20, "#67C23A"),
    status("SUSPENDED", "已暂停", "PROCESSING", 30, "#E6A23C"),
    status("PENDING_LEAVE", "待退出", "PROCESSING", 40, "#409EFF"),
    status("LEFT", "已退出", "TERMINATED", 50, "#606266"),
    status("REJECTED", "已驳回", "TERMINATED", 60, "#F56C6C")
  ],
  codePrefix: "PRJMEM",
  codeExample: "PRJMEM2026072800001",
  forms: [
    form("项目成员表单", "project_member_default", "维护项目成员投入和权限信息。", [
      formField("project_id", "项目", "REFERENCE", 10, {
        required: true,
        componentType: "reference"
      }),
      formField("user_id", "人员", "USER", 20, {
        required: true,
        componentType: "user"
      }),
      formField("source_dept_id", "来源部门", "DEPT", 30, {
        required: true,
        componentType: "dept"
      }),
      formField("employment_type", "人员类型", "SELECT", 40, {
        required: true,
        componentType: "select"
      }),
      formField("join_date", "加入日期", "DATE", 50, {
        required: true,
        componentType: "date"
      }),
      formField("planned_leave_date", "计划退出日期", "DATE", 60, {
        componentType: "date"
      }),
      formField("allocation_percentage", "投入比例(%)", "DECIMAL", 70, {
        required: true,
        componentType: "number"
      }),
      formField("join_reason", "加入原因", "TEXT", 80, {
        required: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("account_required_flag", "需要项目账号", "BOOLEAN", 90, {
        required: true,
        componentType: "switch",
        gridSpan: 8
      }),
      formField("environment_access_required_flag", "需要环境权限", "BOOLEAN", 100, {
        required: true,
        componentType: "switch",
        gridSpan: 8
      }),
      formField("environment_scope", "环境权限范围", "MULTI_SELECT", 110, {
        componentType: "select_multiple",
        gridSpan: 24,
        componentProps: linkage({
          visibilityRule: "${environment_access_required_flag} == true",
          requiredRule: "${environment_access_required_flag} == true"
        })
      })
    ], { isDefault: true })
  ],
  lists: [
    entityList("all", "项目成员台账", "查询项目成员投入和状态。", [
      listField("code", "成员编号", 10, 170, { queryType: "LIKE" }),
      listField("project_id", "项目", 20, 200),
      listField("user_id", "人员", 30, 140),
      listField("source_dept_id", "来源部门", 40, 150),
      listField("employment_type", "人员类型", 50, 110),
      listField("allocation_percentage", "投入比例", 60, 100, { align: "right" }),
      listField("join_date", "加入日期", 70, 120, { queryType: "BETWEEN", align: "center" }),
      listField("planned_leave_date", "计划退出", 80, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 90, 110, { queryType: "IN", align: "center" })
    ], { isDefault: true, allowedScenes: ["PAGE", "EMBEDDED", "SELECTION"] })
  ],
  scopePolicies: [
    creatorPolicy("project_member", "项目成员"),
    userFieldPolicy(
      "project_member_self",
      "本人项目成员记录",
      "人员字段为当前用户的数据。",
      "MEMBER_SELF",
      "user_id"
    )
  ],
  scopeBindings: [
    binding("project_member_creator"),
    binding("project_member_self")
  ],
  menus: [
    menu(
      "项目成员",
      "/entity-list/project_member/all",
      "entity:project_member:list",
      "all",
      65,
      "UserFilled"
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project", required: true, reason: "所属项目" }
  ]
});

const projectRoleCatalog = baseEntity({
  businessKey: "project_role_catalog",
  assetName: "项目角色目录",
  description: "定义项目角色编码、允许作用域、唯一性和审批要求。",
  fields: [
    field("role_code", "角色编码", "STRING", "varchar(50)", true, 20, {
      fieldLength: 50,
      isUnique: true
    }),
    field("allowed_scope", "允许作用域", "MULTI_SELECT", "varchar(200)", true, 30, {
      fieldLength: 200,
      optionsJson: optionJson([
        ["项目", "PROJECT"],
        ["系统", "SYSTEM"],
        ["发布", "RELEASE"]
      ])
    }),
    field("primary_unique_flag", "主负责人唯一", "BOOLEAN", "tinyint", true, 40, {
      defaultValue: "false"
    }),
    field("approval_required_flag", "需要审批", "BOOLEAN", "tinyint", true, 50, {
      defaultValue: "true"
    }),
    field("enabled_flag", "是否启用", "BOOLEAN", "tinyint", true, 60, {
      defaultValue: "true"
    }),
    field("description", "角色说明", "TEXT", "text", false, 70)
  ],
  statuses: [
    status("ENABLED", "启用", "NEW", 10, "#67C23A"),
    status("DISABLED", "停用", "TERMINATED", 20, "#909399")
  ],
  codePrefix: "PRJROLE",
  codeExample: "PRJROLE2026072800001",
  forms: [
    form("项目角色目录表单", "project_role_catalog_default", "维护角色目录。", [
      formField("name", "角色名称", "STRING", 10, {
        required: true,
        gridSpan: 12
      }),
      formField("role_code", "角色编码", "STRING", 20, {
        required: true,
        gridSpan: 12
      }),
      formField("allowed_scope", "允许作用域", "MULTI_SELECT", 30, {
        required: true,
        componentType: "select_multiple",
        gridSpan: 24
      }),
      formField("primary_unique_flag", "主负责人唯一", "BOOLEAN", 40, {
        required: true,
        componentType: "switch",
        gridSpan: 8
      }),
      formField("approval_required_flag", "需要审批", "BOOLEAN", 50, {
        required: true,
        componentType: "switch",
        gridSpan: 8
      }),
      formField("enabled_flag", "是否启用", "BOOLEAN", 60, {
        required: true,
        componentType: "switch",
        gridSpan: 8
      }),
      formField("description", "角色说明", "TEXT", 70, {
        componentType: "textarea",
        gridSpan: 24
      })
    ], { isDefault: true })
  ],
  lists: [
    entityList("all", "项目角色目录", "项目角色基础目录。", [
      listField("code", "目录编号", 10, 170, { queryType: "LIKE" }),
      listField("role_code", "角色编码", 20, 170, { queryType: "LIKE" }),
      listField("name", "角色名称", 30, 180, { queryType: "LIKE" }),
      listField("allowed_scope", "允许作用域", 40, 180, { queryType: "IN" }),
      listField("primary_unique_flag", "主负责人唯一", 50, 120, { align: "center" }),
      listField("enabled_flag", "启用", 60, 90, { align: "center" }),
      listField("status", "状态", 70, 100, { queryType: "IN", align: "center" })
    ], { isDefault: true, createLabel: "新增角色" })
  ],
  scopePolicies: [
    creatorPolicy("project_role_catalog", "项目角色目录")
  ],
  scopeBindings: [
    binding("project_role_catalog_creator")
  ],
  menus: [
    menu(
      "项目角色目录",
      "/entity-list/project_role_catalog/all",
      "entity:project_role_catalog:list",
      "all",
      75,
      "Key"
    )
  ]
});

const projectRoleAssignment = baseEntity({
  businessKey: "project_role_assignment",
  assetName: "项目角色分配",
  description: "记录项目成员在项目、系统或发布范围内承担的角色。",
  fields: [
    refField("project_id", "项目", "CUSTOM", "project", true, 20),
    refField("system_id", "系统", "CUSTOM", "system_asset", false, 30),
    field("release_id", "发布", "STRING", "varchar(64)", false, 40, {
      fieldLength: 64
    }),
    refField("member_id", "项目成员", "CUSTOM", "project_member", true, 50),
    refField("user_id", "人员", "USER", null, true, 60),
    refField("role_catalog_id", "角色目录", "CUSTOM", "project_role_catalog", true, 70),
    field("role_code", "角色编码", "STRING", "varchar(50)", true, 80, {
      fieldLength: 50
    }),
    field("role_scope", "角色作用域", "SELECT", "varchar(20)", true, 90, {
      fieldLength: 20,
      optionsJson: optionJson([
        ["项目", "PROJECT"],
        ["系统", "SYSTEM"],
        ["发布", "RELEASE"]
      ])
    }),
    field("primary_flag", "主负责人", "BOOLEAN", "tinyint", true, 100, {
      defaultValue: "false"
    }),
    field("responsibility_description", "职责说明", "TEXT", "text", true, 110),
    field("effective_from", "生效日期", "DATE", "date", true, 120),
    field("effective_to", "失效日期", "DATE", "date", false, 130),
    refField(
      "predecessor_assignment_id",
      "前任角色分配",
      "CUSTOM",
      "project_role_assignment",
      false,
      140
    ),
    field("handover_required_flag", "需要交接", "BOOLEAN", "tinyint", true, 150, {
      defaultValue: "false"
    }),
    field("handover_completed_flag", "交接已完成", "BOOLEAN", "tinyint", true, 160, {
      defaultValue: "false"
    }),
    field("source_process", "来源流程", "STRING", "varchar(50)", false, 170, {
      fieldLength: 50
    })
  ],
  statuses: [
    status("PROPOSED", "待生效", "NEW", 10, "#909399"),
    status("ACTIVE", "有效", "COMPLETED", 20, "#67C23A"),
    status("SUSPENDED", "暂停", "PROCESSING", 30, "#E6A23C"),
    status("EXPIRED", "已到期", "TERMINATED", 40, "#606266"),
    status("REVOKED", "已撤销", "TERMINATED", 50, "#F56C6C")
  ],
  codePrefix: "PRJRA",
  codeExample: "PRJRA2026072800001",
  forms: [
    form("项目角色分配表单", "project_role_assignment_default", "维护项目角色分配。", [
      formField("project_id", "项目", "REFERENCE", 10, {
        required: true,
        componentType: "reference"
      }),
      formField("member_id", "项目成员", "REFERENCE", 20, {
        required: true,
        componentType: "reference"
      }),
      formField("role_catalog_id", "角色目录", "REFERENCE", 30, {
        required: true,
        componentType: "reference"
      }),
      formField("role_scope", "角色作用域", "SELECT", 40, {
        required: true,
        componentType: "select"
      }),
      formField("system_id", "系统", "REFERENCE", 50, {
        componentType: "reference",
        componentProps: linkage({
          visibilityRule: "${role_scope} == 'SYSTEM'",
          requiredRule: "${role_scope} == 'SYSTEM'"
        })
      }),
      formField("primary_flag", "主负责人", "BOOLEAN", 60, {
        required: true,
        componentType: "switch"
      }),
      formField("effective_from", "生效日期", "DATE", 70, {
        required: true,
        componentType: "date"
      }),
      formField("effective_to", "失效日期", "DATE", 80, {
        componentType: "date"
      }),
      formField("responsibility_description", "职责说明", "TEXT", 90, {
        required: true,
        componentType: "textarea",
        gridSpan: 24
      })
    ], { isDefault: true })
  ],
  lists: [
    entityList("all", "项目角色分配台账", "查询项目及系统角色。", [
      listField("code", "分配编号", 10, 170, { queryType: "LIKE" }),
      listField("project_id", "项目", 20, 200),
      listField("system_id", "系统", 30, 190),
      listField("user_id", "人员", 40, 140),
      listField("role_code", "角色编码", 50, 160, { queryType: "LIKE" }),
      listField("role_scope", "作用域", 60, 100),
      listField("primary_flag", "主负责人", 70, 100, { align: "center" }),
      listField("effective_from", "生效日期", 80, 120, { queryType: "BETWEEN", align: "center" }),
      listField("effective_to", "失效日期", 90, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 100, 100, { queryType: "IN", align: "center" })
    ], { isDefault: true, allowedScenes: ["PAGE", "EMBEDDED", "SELECTION"] })
  ],
  scopePolicies: [
    creatorPolicy("project_role_assignment", "项目角色分配"),
    userFieldPolicy(
      "project_role_assignment_self",
      "本人角色分配",
      "人员字段为当前用户的数据。",
      "ROLE_ASSIGNEE",
      "user_id"
    )
  ],
  scopeBindings: [
    binding("project_role_assignment_creator"),
    binding("project_role_assignment_self")
  ],
  menus: [
    menu(
      "项目角色分配",
      "/entity-list/project_role_assignment/all",
      "entity:project_role_assignment:list",
      "all",
      80,
      "User"
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project", required: true, reason: "所属项目" },
    { type: "ENTITY", key: "project_member", required: true, reason: "角色承担成员" },
    { type: "ENTITY", key: "project_role_catalog", required: true, reason: "角色目录" },
    { type: "ENTITY", key: "system_asset", required: true, reason: "系统作用域" }
  ]
});

const projectSystemLink = baseEntity({
  businessKey: "project_system_link",
  assetName: "项目系统关系",
  description: "记录项目对系统的新建、改造、集成、迁移、整改或退役范围。",
  fields: [
    refField("project_id", "项目", "CUSTOM", "project", true, 20),
    refField("system_id", "系统", "CUSTOM", "system_asset", true, 30),
    field("construction_mode", "建设方式", "SELECT", "varchar(30)", true, 40, {
      fieldLength: 30,
      optionsJson: optionJson([
        ["新建", "NEW_BUILD"],
        ["增强改造", "ENHANCEMENT"],
        ["系统集成", "INTEGRATION"],
        ["迁移", "MIGRATION"],
        ["安全整改", "SECURITY_REMEDIATION"],
        ["退役", "RETIREMENT"]
      ])
    }),
    field("relation_reason", "关联原因", "TEXT", "text", true, 50),
    field("affected_modules", "影响模块", "TEXT", "text", true, 60),
    field("interface_impact", "接口影响", "TEXT", "text", false, 70),
    field("data_impact", "数据影响", "TEXT", "text", false, 80),
    field("deployment_impact", "部署影响", "TEXT", "text", false, 90),
    field("target_system_version", "目标系统版本", "STRING", "varchar(100)", false, 100, {
      fieldLength: 100
    }),
    refField(
      "project_system_lead_id",
      "项目内系统负责人",
      "CUSTOM",
      "project_member",
      false,
      110
    ),
    refField(
      "technical_lead_id",
      "项目内技术负责人",
      "CUSTOM",
      "project_member",
      false,
      120
    ),
    field("risk_level", "关系风险等级", "SELECT", "varchar(10)", true, 130, {
      fieldLength: 10,
      defaultValue: "MEDIUM",
      optionsJson: optionJson([
        ["低", "LOW"],
        ["中", "MEDIUM"],
        ["高", "HIGH"],
        ["极高", "CRITICAL"]
      ])
    }),
    field("planned_start_date", "计划开始日期", "DATE", "date", true, 140),
    field("planned_end_date", "计划完成日期", "DATE", "date", true, 150),
    field("effective_at", "生效时间", "DATETIME", "datetime", false, 160),
    field("invalid_at", "失效时间", "DATETIME", "datetime", false, 170),
    field("invalid_reason", "失效原因", "TEXT", "text", false, 180),
    field("source_process", "来源流程", "STRING", "varchar(50)", false, 190, {
      fieldLength: 50
    })
  ],
  statuses: [
    status("PROPOSED", "拟关联", "NEW", 10, "#909399"),
    status("PENDING_APPROVAL", "审批中", "PROCESSING", 20, "#409EFF"),
    status("ACTIVE", "有效", "COMPLETED", 30, "#67C23A"),
    status("INVALID", "已失效", "TERMINATED", 40, "#606266"),
    status("REJECTED", "已驳回", "TERMINATED", 50, "#F56C6C"),
    status("CANCELLED", "已取消", "TERMINATED", 60, "#909399")
  ],
  codePrefix: "PRJSYS",
  codeExample: "PRJSYS2026072800001",
  forms: [
    form("项目系统关系表单", "project_system_link_default", "维护项目系统建设范围。", [
      formField("system_id", "系统", "REFERENCE", 10, {
        required: true,
        componentType: "reference",
        gridSpan: 12
      }),
      formField("construction_mode", "建设方式", "SELECT", 20, {
        required: true,
        componentType: "select",
        gridSpan: 12
      }),
      formField("relation_reason", "关联原因", "TEXT", 30, {
        required: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("affected_modules", "影响模块", "TEXT", 40, {
        required: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("interface_impact", "接口影响", "TEXT", 50, {
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("data_impact", "数据影响", "TEXT", 60, {
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("deployment_impact", "部署影响", "TEXT", 70, {
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("risk_level", "关系风险等级", "SELECT", 80, {
        required: true,
        componentType: "select",
        gridSpan: 8
      }),
      formField("planned_start_date", "计划开始日期", "DATE", 90, {
        required: true,
        componentType: "date",
        gridSpan: 8
      }),
      formField("planned_end_date", "计划完成日期", "DATE", 100, {
        required: true,
        componentType: "date",
        gridSpan: 8
      }),
      formField("project_system_lead_id", "项目内系统负责人", "REFERENCE", 110, {
        componentType: "reference",
        gridSpan: 12
      }),
      formField("technical_lead_id", "项目内技术负责人", "REFERENCE", 120, {
        componentType: "reference",
        gridSpan: 12
      })
    ], { isDefault: true })
  ],
  lists: [
    entityList("all", "项目系统关系台账", "查询项目系统建设范围及生效状态。", [
      listField("code", "关系编号", 10, 170, { queryType: "LIKE" }),
      listField("project_id", "项目", 20, 200),
      listField("system_id", "系统", 30, 200),
      listField("construction_mode", "建设方式", 40, 130),
      listField("risk_level", "风险等级", 50, 100),
      listField("project_system_lead_id", "系统负责人", 60, 140),
      listField("technical_lead_id", "技术负责人", 70, 140),
      listField("planned_end_date", "计划完成", 80, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 90, 110, { queryType: "IN", align: "center" })
    ], {
      isDefault: true,
      allowedScenes: ["PAGE", "EMBEDDED", "SELECTION"],
      contextBindingConfig: { parentField: "project_id" }
    })
  ],
  scopePolicies: [
    creatorPolicy("project_system_link", "项目系统关系")
  ],
  scopeBindings: [
    binding("project_system_link_creator")
  ],
  menus: [
    menu(
      "项目系统关系",
      "/entity-list/project_system_link/all",
      "entity:project_system_link:list",
      "all",
      60,
      "Connection"
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project", required: true, reason: "所属项目" },
    { type: "ENTITY", key: "system_asset", required: true, reason: "建设系统" },
    { type: "ENTITY", key: "project_member", required: true, reason: "项目内责任人" }
  ]
});

const projectFields = [
  field("project_type", "项目类型", "SELECT", "varchar(30)", true, 20, {
    fieldLength: 30,
    optionsJson: optionJson([
      ["新系统建设", "NEW_SYSTEM"],
      ["系统增强", "ENHANCEMENT"],
      ["系统集成", "INTEGRATION"],
      ["数据项目", "DATA"],
      ["安全项目", "SECURITY"],
      ["系统迁移", "MIGRATION"],
      ["研究咨询", "RESEARCH"]
    ])
  }),
  field("project_level", "项目级别", "SELECT", "varchar(30)", true, 30, {
    fieldLength: 30,
    defaultValue: "DEPARTMENT",
    optionsJson: optionJson([
      ["企业级", "ENTERPRISE"],
      ["跨部门", "CROSS_DEPARTMENT"],
      ["部门级", "DEPARTMENT"],
      ["小型变更", "SMALL_CHANGE"]
    ])
  }),
  refField("project_group_id", "所属项目群", "CUSTOM", "project_group", false, 40),
  refField("sponsor_dept_id", "发起部门", "DEPT", null, true, 50),
  refField("project_sponsor_id", "项目发起人", "USER", null, true, 60),
  refField("business_owner_id", "业务负责人", "USER", null, true, 70),
  refField("project_manager_id", "项目经理", "USER", null, true, 80),
  refField("product_owner_id", "产品负责人", "USER", null, true, 90),
  field("project_background", "项目背景", "TEXT", "text", true, 100),
  field("project_objective", "项目目标", "TEXT", "text", true, 110),
  field("scope_in", "项目范围内", "TEXT", "text", true, 120),
  field("scope_out", "项目范围外", "TEXT", "text", true, 130),
  field("expected_deliverables", "预期交付物", "TEXT", "text", true, 140),
  field("success_metrics", "成功指标", "TEXT", "text", true, 150),
  field("planned_start_date", "计划开始日期", "DATE", "date", true, 160),
  field("planned_end_date", "计划结束日期", "DATE", "date", true, 170),
  field("actual_start_date", "实际开始日期", "DATE", "date", false, 180),
  field("actual_end_date", "实际结束日期", "DATE", "date", false, 190),
  field("current_baseline_version", "当前基线版本", "INTEGER", "int", true, 200, {
    defaultValue: "0"
  }),
  field("priority", "项目优先级", "SELECT", "varchar(10)", true, 210, {
    fieldLength: 10,
    defaultValue: "P2",
    optionsJson: optionJson([
      ["P0", "P0"],
      ["P1", "P1"],
      ["P2", "P2"],
      ["P3", "P3"]
    ])
  }),
  field("risk_level", "项目风险等级", "SELECT", "varchar(10)", true, 220, {
    fieldLength: 10,
    defaultValue: "MEDIUM",
    optionsJson: optionJson([
      ["低", "LOW"],
      ["中", "MEDIUM"],
      ["高", "HIGH"],
      ["极高", "CRITICAL"]
    ])
  }),
  field("cross_system_flag", "是否跨系统", "BOOLEAN", "tinyint", true, 230, {
    defaultValue: "false"
  }),
  field("security_involved_flag", "涉及安全专项", "BOOLEAN", "tinyint", true, 240, {
    defaultValue: "false"
  }),
  field("security_requirements", "安全要求", "TEXT", "text", false, 250),
  field("data_involved_flag", "涉及数据专项", "BOOLEAN", "tinyint", true, 260, {
    defaultValue: "false"
  }),
  field("data_scope", "数据范围", "TEXT", "text", false, 270),
  field("data_classification", "最高数据等级", "SELECT", "varchar(20)", false, 280, {
    fieldLength: 20,
    optionsJson: optionJson([
      ["公开", "PUBLIC"],
      ["内部", "INTERNAL"],
      ["敏感", "SENSITIVE"],
      ["高度敏感", "HIGHLY_SENSITIVE"]
    ])
  }),
  field("completion_percentage", "项目完成比例(%)", "DECIMAL", "decimal(5,2)", true, 290, {
    defaultValue: "0",
    fieldPrecision: 2,
    validateRules: JSON.stringify({ min: 0, max: 100 })
  }),
  field("pmo_review_summary", "PMO治理意见", "TEXT", "text", false, 300),
  field("initialization_completed_flag", "初始化已完成", "BOOLEAN", "tinyint", true, 310, {
    defaultValue: "false"
  }),
  field("initialization_summary", "初始化结果", "TEXT", "text", false, 320),
  field("initial_requirement_links", "初始需求范围", "SUB_FORM_LIST", "varchar(64)", false, 330, {
    fieldLength: 64,
    refEntityType: "CUSTOM",
    refEntityCode: "requirement_project_link",
    displayMode: "embedded",
    refFieldCode: "project_id"
  }),
  field("initial_system_links", "初始系统范围", "SUB_FORM_LIST", "varchar(64)", false, 340, {
    fieldLength: 64,
    refEntityType: "CUSTOM",
    refEntityCode: "project_system_link",
    displayMode: "embedded",
    refFieldCode: "project_id"
  }),
  ...workflowCommonFields(350)
];

const projectRelations = [
  {
    parentFieldCode: "initial_requirement_links",
    relationCode: "project_initial_requirements",
    relationName: "项目初始需求范围",
    childEntityCode: "requirement_project_link",
    childRefFieldCode: "project_id",
    relationType: "ONE_TO_MANY",
    cascadeDelete: false,
    required: true,
    sortOrder: 10,
    enabled: true
  },
  {
    parentFieldCode: "initial_system_links",
    relationCode: "project_initial_systems",
    relationName: "项目初始系统范围",
    childEntityCode: "project_system_link",
    childRefFieldCode: "project_id",
    relationType: "ONE_TO_MANY",
    cascadeDelete: false,
    required: false,
    sortOrder: 20,
    enabled: true
  }
];

const projectApplicationFields = [
  formField("name", "项目名称", "STRING", 10, {
    required: true,
    gridSpan: 24,
    validationRules: JSON.stringify({ minLength: 5, maxLength: 200 })
  }),
  formField("project_type", "项目类型", "SELECT", 20, {
    required: true,
    componentType: "select",
    gridSpan: 8
  }),
  formField("project_level", "项目级别", "SELECT", 30, {
    required: true,
    componentType: "select",
    gridSpan: 8
  }),
  formField("project_group_id", "所属项目群", "REFERENCE", 40, {
    componentType: "reference",
    gridSpan: 8,
    componentProps: linkage({
      visibilityRule: "${project_level} == 'ENTERPRISE'",
      requiredRule: "${project_level} == 'ENTERPRISE'"
    })
  }),
  formField("applicant_id", "申请人", "USER", 50, {
    required: true,
    componentType: "user",
    gridSpan: 8
  }),
  formField("applicant_dept_id", "申请部门", "DEPT", 60, {
    required: true,
    componentType: "dept",
    gridSpan: 8
  }),
  formField("sponsor_dept_id", "发起部门", "DEPT", 70, {
    required: true,
    componentType: "dept",
    gridSpan: 8
  }),
  formField("project_sponsor_id", "项目发起人", "USER", 80, {
    required: true,
    componentType: "user",
    gridSpan: 8
  }),
  formField("business_owner_id", "业务负责人", "USER", 90, {
    required: true,
    componentType: "user",
    gridSpan: 8
  }),
  formField("project_manager_id", "项目经理", "USER", 100, {
    required: true,
    componentType: "user",
    gridSpan: 8
  }),
  formField("product_owner_id", "产品负责人", "USER", 110, {
    required: true,
    componentType: "user",
    gridSpan: 8
  }),
  formField("priority", "项目优先级", "SELECT", 120, {
    required: true,
    componentType: "select",
    gridSpan: 8
  }),
  formField("risk_level", "风险等级", "SELECT", 130, {
    required: true,
    componentType: "select",
    gridSpan: 8
  }),
  formField("planned_start_date", "计划开始日期", "DATE", 140, {
    required: true,
    componentType: "date",
    gridSpan: 12
  }),
  formField("planned_end_date", "计划结束日期", "DATE", 150, {
    required: true,
    componentType: "date",
    gridSpan: 12
  }),
  formField("project_background", "项目背景", "TEXT", 160, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("project_objective", "项目目标", "TEXT", 170, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("scope_in", "项目范围内", "TEXT", 180, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("scope_out", "项目范围外", "TEXT", 190, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("expected_deliverables", "预期交付物", "TEXT", 200, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("success_metrics", "成功指标", "TEXT", 210, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("cross_system_flag", "是否跨系统", "BOOLEAN", 220, {
    required: true,
    componentType: "switch",
    gridSpan: 8
  }),
  formField("security_involved_flag", "涉及安全专项", "BOOLEAN", 230, {
    required: true,
    componentType: "switch",
    gridSpan: 8
  }),
  formField("data_involved_flag", "涉及数据专项", "BOOLEAN", 240, {
    required: true,
    componentType: "switch",
    gridSpan: 8
  }),
  formField("security_requirements", "安全要求", "TEXT", 250, {
    componentType: "textarea",
    gridSpan: 24,
    componentProps: linkage({
      visibilityRule: "${security_involved_flag} == true",
      requiredRule: "${security_involved_flag} == true"
    })
  }),
  formField("data_scope", "数据范围", "TEXT", 260, {
    componentType: "textarea",
    gridSpan: 16,
    componentProps: linkage({
      visibilityRule: "${data_involved_flag} == true",
      requiredRule: "${data_involved_flag} == true"
    })
  }),
  formField("data_classification", "最高数据等级", "SELECT", 270, {
    componentType: "select",
    gridSpan: 8,
    componentProps: linkage({
      visibilityRule: "${data_involved_flag} == true",
      requiredRule: "${data_involved_flag} == true"
    })
  }),
  formField("initial_requirement_links", "初始需求范围", "SUB_FORM_LIST", 280, {
    required: true,
    componentType: "sub_form_list",
    gridSpan: 24
  }),
  formField("initial_system_links", "初始系统范围", "SUB_FORM_LIST", 290, {
    componentType: "sub_form_list",
    gridSpan: 24,
    componentProps: linkage({
      visibilityRule: "${project_type} != 'RESEARCH'",
      requiredRule: "${project_type} != 'RESEARCH'"
    })
  })
];

const project = baseEntity({
  businessKey: "project",
  assetName: "项目立项",
  description: "项目立项申请、范围会签和批准后的初始治理关系建立。",
  lifecycleMode: "WORKFLOW",
  processKey: "project_initiation_process",
  fields: projectFields,
  relations: projectRelations,
  statuses: [
    status("DRAFT", "草稿", "NEW", 10, "#909399"),
    status("PENDING_APPROVAL", "立项审批中", "PROCESSING", 20, "#409EFF"),
    status("APPROVED", "立项已批准", "COMPLETED", 30, "#67C23A"),
    status("ACTIVE", "进行中", "PROCESSING", 40, "#36CFC9"),
    status("PAUSED", "已暂停", "PROCESSING", 50, "#E6A23C"),
    status("ACCEPTING", "验收中", "PROCESSING", 60, "#9B59B6"),
    status("CLOSING", "结项中", "PROCESSING", 70, "#B37FEB"),
    status("CLOSED", "已结项", "COMPLETED", 80, "#67C23A"),
    status("REJECTED", "已驳回", "TERMINATED", 90, "#F56C6C"),
    status("CANCELLED", "已取消", "TERMINATED", 100, "#606266")
  ],
  codePrefix: "PRJ",
  codeExample: "PRJ2026072800001",
  forms: [
    form(
      "项目立项申请表单",
      "project_application",
      "项目发起人录入项目范围、计划、关键人员和初始关系。",
      projectApplicationFields,
      { isDefault: true, submitLabel: "提交立项" }
    ),
    form("项目治理评审表单", "project_governance_review", "PMO及专业评审补充治理结论。", [
      formField("code", "项目编号", "STRING", 10, {
        readonly: true,
        gridSpan: 8
      }),
      formField("name", "项目名称", "STRING", 20, {
        required: true,
        readonly: true,
        gridSpan: 16
      }),
      formField("project_type", "项目类型", "SELECT", 30, {
        required: true,
        readonly: true,
        componentType: "select",
        gridSpan: 8
      }),
      formField("project_level", "项目级别", "SELECT", 40, {
        required: true,
        componentType: "select",
        gridSpan: 8
      }),
      formField("priority", "项目优先级", "SELECT", 50, {
        required: true,
        componentType: "select",
        gridSpan: 8
      }),
      formField("risk_level", "风险等级", "SELECT", 60, {
        required: true,
        componentType: "select",
        gridSpan: 8
      }),
      formField("planned_start_date", "计划开始日期", "DATE", 70, {
        required: true,
        componentType: "date",
        gridSpan: 8
      }),
      formField("planned_end_date", "计划结束日期", "DATE", 80, {
        required: true,
        componentType: "date",
        gridSpan: 8
      }),
      formField("project_objective", "项目目标", "TEXT", 90, {
        required: true,
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("scope_in", "项目范围内", "TEXT", 100, {
        required: true,
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("initial_requirement_links", "初始需求范围", "SUB_FORM_LIST", 110, {
        readonly: true,
        componentType: "sub_form_list",
        gridSpan: 24
      }),
      formField("initial_system_links", "初始系统范围", "SUB_FORM_LIST", 120, {
        readonly: true,
        componentType: "sub_form_list",
        gridSpan: 24
      }),
      formField("pmo_review_summary", "PMO治理意见", "TEXT", 130, {
        componentType: "textarea",
        gridSpan: 24
      })
    ]),
    form("项目立项详情表单", "project_detail", "审批和台账查看使用。", [
      formField("code", "项目编号", "STRING", 10, {
        readonly: true,
        gridSpan: 8
      }),
      formField("name", "项目名称", "STRING", 20, {
        readonly: true,
        gridSpan: 16
      }),
      formField("status", "状态", "STRING", 30, {
        readonly: true,
        gridSpan: 8
      }),
      ...projectApplicationFields
        .filter((item) => !["name"].includes(item.fieldCode))
        .map((item) => ({ ...item, isReadonly: 1 }))
    ], { readonly: true })
  ],
  lists: [
    entityList("all", "项目台账", "本人发起、负责或参与治理的项目。", [
      listField("code", "项目编号", 10, 170, { queryType: "LIKE" }),
      listField("name", "项目名称", 20, 240, { queryType: "LIKE" }),
      listField("project_type", "项目类型", 30, 120),
      listField("project_level", "项目级别", 40, 110),
      listField("project_manager_id", "项目经理", 50, 130),
      listField("business_owner_id", "业务负责人", 60, 130),
      listField("priority", "优先级", 70, 90),
      listField("risk_level", "风险", 80, 90),
      listField("planned_end_date", "计划结束", 90, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 100, 120, { queryType: "IN", align: "center" }),
      listField("currentTaskName", "当前环节", 110, 160, { query: false })
    ], {
      isDefault: true,
      createLabel: "申请立项",
      rowActions: [
        ...defaultRowActions,
        {
          key: "approve",
          type: "built-in",
          label: "审批",
          buttonType: "warning",
          link: true,
          sort: 3,
          enabled: true
        }
      ]
    }),
    entityList("my_pending", "我的立项待办", "当前办理人为当前用户的项目立项。", [
      listField("code", "项目编号", 10, 170, { queryType: "LIKE" }),
      listField("name", "项目名称", 20, 260, { queryType: "LIKE" }),
      listField("project_type", "项目类型", 30, 120),
      listField("project_manager_id", "项目经理", 40, 130),
      listField("currentTaskName", "当前环节", 50, 170, { query: false }),
      listField("submitterName", "提交人", 60, 120, { queryType: "LIKE" }),
      listField("submitTime", "提交时间", 70, 170, { queryType: "BETWEEN", align: "center" })
    ], {
      dataScopeMode: "NARROW",
      allowedScenes: ["PAGE"],
      selectionMode: "NONE",
      rowActions: [
        {
          key: "view",
          type: "built-in",
          label: "查看",
          buttonType: "primary",
          link: true,
          sort: 1,
          enabled: true
        },
        {
          key: "approve",
          type: "built-in",
          label: "审批",
          buttonType: "warning",
          link: true,
          sort: 2,
          enabled: true
        }
      ]
    }),
    entityList("approved", "已批准项目", "已批准及后续生命周期项目。", [
      listField("code", "项目编号", 10, 170, { queryType: "LIKE" }),
      listField("name", "项目名称", 20, 260, { queryType: "LIKE" }),
      listField("project_type", "项目类型", 30, 120),
      listField("project_manager_id", "项目经理", 40, 130),
      listField("completion_percentage", "完成比例", 50, 100, { align: "right" }),
      listField("current_baseline_version", "基线版本", 60, 100, { align: "right" }),
      listField("planned_end_date", "计划结束", 70, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 80, 120, { queryType: "IN", align: "center" })
    ], {
      dataScopeMode: "NARROW",
      fixedFilterConfig: {
        status: ["APPROVED", "ACTIVE", "PAUSED", "ACCEPTING", "CLOSING", "CLOSED"]
      },
      rowActions: [
        {
          key: "view",
          type: "built-in",
          label: "查看",
          buttonType: "primary",
          link: true,
          sort: 1,
          enabled: true
        }
      ]
    })
  ],
  scopePolicies: [
    creatorPolicy("project", "项目"),
    userFieldPolicy(
      "project_manager_scope",
      "项目经理负责的项目",
      "项目经理字段为当前用户的数据。",
      "PROJECT_MANAGER",
      "project_manager_id"
    ),
    userFieldPolicy(
      "project_business_owner_scope",
      "业务负责人项目",
      "业务负责人字段为当前用户的数据。",
      "BUSINESS_OWNER",
      "business_owner_id"
    ),
    policy(
      "project_current_assignee",
      "当前待办项目",
      "当前审批人为当前用户的数据。",
      "CURRENT_ASSIGNEE",
      {
        version: 1,
        type: "PERSONAL",
        fieldMapping: {
          userField: "current_task_assignee",
          deptField: "dept_id",
          statusField: "status"
        }
      }
    )
  ],
  scopeBindings: [
    binding("project_creator"),
    binding("project_manager_scope"),
    binding("project_business_owner_scope"),
    binding("project_current_assignee", "my_pending"),
    binding("project_manager_scope", "approved"),
    binding("project_business_owner_scope", "approved")
  ],
  menus: [
    menu(
      "项目台账",
      "/entity-list/project/all",
      "entity:project:list",
      "all",
      50,
      "FolderOpened"
    ),
    menu(
      "我的立项待办",
      "/entity-list/project/my_pending",
      "entity:project:list",
      "my_pending",
      51,
      "Clock"
    ),
    menu(
      "已批准项目",
      "/entity-list/project/approved",
      "entity:project:list",
      "approved",
      52,
      "CircleCheck"
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project_group", required: true, reason: "项目群归属" },
    { type: "ENTITY", key: "requirement_project_link", required: true, reason: "初始需求范围" },
    { type: "ENTITY", key: "project_system_link", required: true, reason: "初始系统范围" },
    { type: "ENTITY", key: "project_member", required: true, reason: "关键人员初始化" },
    { type: "ENTITY", key: "project_role_assignment", required: true, reason: "关键角色初始化" },
    { type: "PROCESS", key: "project_initiation_process", required: true, reason: "项目立项审批流程" }
  ]
});

const projectSystemChangeFields = [
  field("operation_type", "操作类型", "SELECT", "varchar(20)", true, 20, {
    fieldLength: 20,
    optionsJson: optionJson([
      ["新增关系", "ADD"],
      ["修改关系", "UPDATE"],
      ["移除关系", "REMOVE"]
    ])
  }),
  refField("project_id", "项目", "CUSTOM", "project", true, 30),
  refField("system_id", "系统", "CUSTOM", "system_asset", true, 40),
  refField(
    "project_system_link_id",
    "原项目系统关系",
    "CUSTOM",
    "project_system_link",
    false,
    50
  ),
  field("construction_mode", "建设方式", "SELECT", "varchar(30)", false, 60, {
    fieldLength: 30,
    optionsJson: optionJson([
      ["新建", "NEW_BUILD"],
      ["增强改造", "ENHANCEMENT"],
      ["系统集成", "INTEGRATION"],
      ["迁移", "MIGRATION"],
      ["安全整改", "SECURITY_REMEDIATION"],
      ["退役", "RETIREMENT"]
    ])
  }),
  field("change_reason", "变更原因", "TEXT", "text", true, 70),
  field("before_snapshot", "变更前快照", "TEXT", "longtext", false, 80),
  field("proposed_change", "拟变更内容", "TEXT", "text", true, 90),
  field("after_snapshot", "拟变更后快照", "TEXT", "longtext", false, 100),
  field("relation_reason", "关联原因", "TEXT", "text", false, 110),
  field("affected_modules", "影响模块", "TEXT", "text", false, 120),
  field("interface_impact", "接口影响", "TEXT", "text", false, 130),
  field("data_impact", "数据影响", "TEXT", "text", false, 140),
  field("deployment_impact", "部署影响", "TEXT", "text", false, 150),
  field("security_impact", "安全影响", "TEXT", "text", false, 160),
  field("security_involved_flag", "涉及安全专项", "BOOLEAN", "tinyint", true, 170, {
    defaultValue: "false"
  }),
  field("data_involved_flag", "涉及数据专项", "BOOLEAN", "tinyint", true, 180, {
    defaultValue: "false"
  }),
  field("target_system_version", "目标系统版本", "STRING", "varchar(100)", false, 190, {
    fieldLength: 100
  }),
  refField(
    "new_project_system_lead_id",
    "新项目内系统负责人",
    "CUSTOM",
    "project_member",
    false,
    200
  ),
  refField(
    "new_technical_lead_id",
    "新项目内技术负责人",
    "CUSTOM",
    "project_member",
    false,
    210
  ),
  field("risk_level", "变更风险", "SELECT", "varchar(10)", true, 220, {
    fieldLength: 10,
    defaultValue: "MEDIUM",
    optionsJson: optionJson([
      ["低", "LOW"],
      ["中", "MEDIUM"],
      ["高", "HIGH"],
      ["极高", "CRITICAL"]
    ])
  }),
  field("planned_start_date", "计划开始日期", "DATE", "date", false, 230),
  field("planned_end_date", "计划完成日期", "DATE", "date", false, 240),
  field("schedule_impact_days", "工期影响天数", "INTEGER", "int", true, 250, {
    defaultValue: "0"
  }),
  field("rebaseline_required_flag", "需要重设基线", "BOOLEAN", "tinyint", true, 260, {
    defaultValue: "true"
  }),
  field("planned_effective_date", "计划生效日期", "DATE", "date", true, 270),
  field("rollback_plan", "回滚方案", "TEXT", "text", false, 280),
  field("remove_dependency_result", "移除前置检查结果", "TEXT", "longtext", false, 290),
  field("implementation_result", "实施结果", "TEXT", "text", false, 300),
  field("effective_at", "实际生效时间", "DATETIME", "datetime", false, 310),
  refField(
    "effective_link_id",
    "生效项目系统关系",
    "CUSTOM",
    "project_system_link",
    false,
    320
  ),
  ...workflowCommonFields(330)
];

const changeApplicationFields = [
  formField("name", "变更标题", "STRING", 10, {
    required: true,
    gridSpan: 24
  }),
  formField("operation_type", "操作类型", "SELECT", 20, {
    required: true,
    componentType: "select",
    gridSpan: 8
  }),
  formField("applicant_id", "申请人", "USER", 30, {
    required: true,
    componentType: "user",
    gridSpan: 8
  }),
  formField("applicant_dept_id", "申请部门", "DEPT", 40, {
    required: true,
    componentType: "dept",
    gridSpan: 8
  }),
  formField("project_id", "项目", "REFERENCE", 50, {
    required: true,
    componentType: "reference",
    gridSpan: 12
  }),
  formField("system_id", "系统", "REFERENCE", 60, {
    required: true,
    componentType: "reference",
    gridSpan: 12
  }),
  formField("project_system_link_id", "原项目系统关系", "REFERENCE", 70, {
    componentType: "reference",
    gridSpan: 24,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'UPDATE' || ${operation_type} == 'REMOVE'",
      requiredRule: "${operation_type} == 'UPDATE' || ${operation_type} == 'REMOVE'"
    })
  }),
  formField("construction_mode", "建设方式", "SELECT", 80, {
    componentType: "select",
    gridSpan: 8,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'",
      requiredRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'"
    })
  }),
  formField("risk_level", "变更风险", "SELECT", 90, {
    required: true,
    componentType: "select",
    gridSpan: 8
  }),
  formField("planned_effective_date", "计划生效日期", "DATE", 100, {
    required: true,
    componentType: "date",
    gridSpan: 8
  }),
  formField("change_reason", "变更原因", "TEXT", 110, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("proposed_change", "拟变更内容", "TEXT", 120, {
    required: true,
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("relation_reason", "关联原因", "TEXT", 130, {
    componentType: "textarea",
    gridSpan: 24,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'",
      requiredRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'"
    })
  }),
  formField("affected_modules", "影响模块", "TEXT", 140, {
    componentType: "textarea",
    gridSpan: 24,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'",
      requiredRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'"
    })
  }),
  formField("interface_impact", "接口影响", "TEXT", 150, {
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("data_impact", "数据影响", "TEXT", 160, {
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("deployment_impact", "部署影响", "TEXT", 170, {
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("security_impact", "安全影响", "TEXT", 180, {
    componentType: "textarea",
    gridSpan: 24
  }),
  formField("security_involved_flag", "涉及安全专项", "BOOLEAN", 190, {
    required: true,
    componentType: "switch",
    gridSpan: 8
  }),
  formField("data_involved_flag", "涉及数据专项", "BOOLEAN", 200, {
    required: true,
    componentType: "switch",
    gridSpan: 8
  }),
  formField("rebaseline_required_flag", "需要重设基线", "BOOLEAN", 210, {
    required: true,
    componentType: "switch",
    gridSpan: 8
  }),
  formField("new_project_system_lead_id", "新项目内系统负责人", "REFERENCE", 220, {
    componentType: "reference",
    gridSpan: 12,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'",
      requiredRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'"
    })
  }),
  formField("new_technical_lead_id", "新项目内技术负责人", "REFERENCE", 230, {
    componentType: "reference",
    gridSpan: 12,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'",
      requiredRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'"
    })
  }),
  formField("planned_start_date", "计划开始日期", "DATE", 240, {
    componentType: "date",
    gridSpan: 12,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'",
      requiredRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'"
    })
  }),
  formField("planned_end_date", "计划完成日期", "DATE", 250, {
    componentType: "date",
    gridSpan: 12,
    componentProps: linkage({
      visibilityRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'",
      requiredRule: "${operation_type} == 'ADD' || ${operation_type} == 'UPDATE'"
    })
  }),
  formField("rollback_plan", "回滚方案", "TEXT", 260, {
    componentType: "textarea",
    gridSpan: 24,
    componentProps: linkage({
      visibilityRule: "${risk_level} == 'HIGH' || ${risk_level} == 'CRITICAL' || ${operation_type} == 'REMOVE'",
      requiredRule: "${risk_level} == 'HIGH' || ${risk_level} == 'CRITICAL' || ${operation_type} == 'REMOVE'"
    })
  })
];

const projectSystemChangeRequest = baseEntity({
  businessKey: "project_system_change_request",
  assetName: "项目系统关系变更",
  description: "项目系统关系新增、修改或移除的审批申请与生效记录。",
  lifecycleMode: "WORKFLOW",
  processKey: "project_system_change_process",
  fields: projectSystemChangeFields,
  statuses: [
    status("DRAFT", "草稿", "NEW", 10, "#909399"),
    status("IMPACT_ASSESSING", "影响检查中", "PROCESSING", 20, "#409EFF"),
    status("PENDING_APPROVAL", "审批中", "PROCESSING", 30, "#36CFC9"),
    status("APPROVED", "已批准待生效", "COMPLETED", 40, "#67C23A"),
    status("IMPLEMENTING", "实施中", "PROCESSING", 50, "#E6A23C"),
    status("EFFECTIVE", "已生效", "COMPLETED", 60, "#67C23A"),
    status("REJECTED", "已驳回", "TERMINATED", 70, "#F56C6C"),
    status("CANCELLED", "已取消", "TERMINATED", 80, "#606266"),
    status("FAILED", "实施失败", "TERMINATED", 90, "#F56C6C")
  ],
  codePrefix: "PRJSC",
  codeExample: "PRJSC2026072800001",
  forms: [
    form(
      "项目系统关系变更申请",
      "project_system_change_application",
      "项目经理提交新增、修改或移除申请。",
      changeApplicationFields,
      { isDefault: true, submitLabel: "提交变更" }
    ),
    form("项目系统变更评审表单", "project_system_change_review", "系统、技术、专业和PMO审批使用。", [
      formField("code", "变更编号", "STRING", 10, {
        readonly: true,
        gridSpan: 8
      }),
      formField("name", "变更标题", "STRING", 20, {
        readonly: true,
        gridSpan: 16
      }),
      formField("operation_type", "操作类型", "SELECT", 30, {
        readonly: true,
        componentType: "select",
        gridSpan: 8
      }),
      formField("project_id", "项目", "REFERENCE", 40, {
        readonly: true,
        componentType: "reference",
        gridSpan: 8
      }),
      formField("system_id", "系统", "REFERENCE", 50, {
        readonly: true,
        componentType: "reference",
        gridSpan: 8
      }),
      formField("project_system_link_id", "原项目系统关系", "REFERENCE", 60, {
        readonly: true,
        componentType: "reference",
        gridSpan: 24
      }),
      formField("change_reason", "变更原因", "TEXT", 70, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("proposed_change", "拟变更内容", "TEXT", 80, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("risk_level", "变更风险", "SELECT", 90, {
        componentType: "select",
        gridSpan: 8
      }),
      formField("rebaseline_required_flag", "需要重设基线", "BOOLEAN", 100, {
        componentType: "switch",
        gridSpan: 8
      }),
      formField("planned_effective_date", "计划生效日期", "DATE", 110, {
        componentType: "date",
        gridSpan: 8
      }),
      formField("before_snapshot", "变更前快照", "TEXT", 120, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("after_snapshot", "拟变更后快照", "TEXT", 130, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("remove_dependency_result", "移除前置检查结果", "TEXT", 140, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("rollback_plan", "回滚方案", "TEXT", 150, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      })
    ]),
    form("项目系统变更详情", "project_system_change_detail", "台账查看和审批追溯使用。", [
      formField("code", "变更编号", "STRING", 10, { readonly: true, gridSpan: 8 }),
      formField("name", "变更标题", "STRING", 20, { readonly: true, gridSpan: 16 }),
      formField("status", "状态", "STRING", 30, { readonly: true, gridSpan: 8 }),
      ...changeApplicationFields
        .filter((item) => item.fieldCode !== "name")
        .map((item) => ({ ...item, isReadonly: 1 })),
      formField("remove_dependency_result", "移除前置检查结果", "TEXT", 300, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("implementation_result", "实施结果", "TEXT", 310, {
        readonly: true,
        componentType: "textarea",
        gridSpan: 24
      }),
      formField("effective_at", "实际生效时间", "DATETIME", 320, {
        readonly: true,
        componentType: "datetime",
        gridSpan: 12
      }),
      formField("effective_link_id", "生效项目系统关系", "REFERENCE", 330, {
        readonly: true,
        componentType: "reference",
        gridSpan: 12
      })
    ], { readonly: true })
  ],
  lists: [
    entityList("all", "项目系统变更台账", "查询全部项目系统关系变更。", [
      listField("code", "变更编号", 10, 170, { queryType: "LIKE" }),
      listField("name", "变更标题", 20, 240, { queryType: "LIKE" }),
      listField("operation_type", "操作类型", 30, 100),
      listField("project_id", "项目", 40, 200),
      listField("system_id", "系统", 50, 190),
      listField("risk_level", "风险", 60, 90),
      listField("planned_effective_date", "计划生效", 70, 120, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 80, 130, { queryType: "IN", align: "center" }),
      listField("currentTaskName", "当前环节", 90, 170, { query: false })
    ], {
      isDefault: true,
      createLabel: "申请系统关系变更",
      rowActions: [
        ...defaultRowActions,
        {
          key: "approve",
          type: "built-in",
          label: "审批",
          buttonType: "warning",
          link: true,
          sort: 3,
          enabled: true
        }
      ]
    }),
    entityList("my_pending", "我的系统变更待办", "当前办理人为当前用户的变更。", [
      listField("code", "变更编号", 10, 170, { queryType: "LIKE" }),
      listField("name", "变更标题", 20, 260, { queryType: "LIKE" }),
      listField("operation_type", "操作类型", 30, 100),
      listField("project_id", "项目", 40, 190),
      listField("system_id", "系统", 50, 180),
      listField("currentTaskName", "当前环节", 60, 170, { query: false }),
      listField("submitTime", "提交时间", 70, 170, { queryType: "BETWEEN", align: "center" })
    ], {
      dataScopeMode: "NARROW",
      allowedScenes: ["PAGE"],
      selectionMode: "NONE",
      rowActions: [
        {
          key: "view",
          type: "built-in",
          label: "查看",
          buttonType: "primary",
          link: true,
          sort: 1,
          enabled: true
        },
        {
          key: "approve",
          type: "built-in",
          label: "审批",
          buttonType: "warning",
          link: true,
          sort: 2,
          enabled: true
        }
      ]
    }),
    entityList("effective", "已生效系统变更", "已成功写入项目系统关系的变更。", [
      listField("code", "变更编号", 10, 170, { queryType: "LIKE" }),
      listField("name", "变更标题", 20, 260, { queryType: "LIKE" }),
      listField("operation_type", "操作类型", 30, 100),
      listField("project_id", "项目", 40, 200),
      listField("system_id", "系统", 50, 190),
      listField("effective_link_id", "生效关系", 60, 190),
      listField("effective_at", "生效时间", 70, 170, { queryType: "BETWEEN", align: "center" }),
      listField("status", "状态", 80, 110, { queryType: "IN", align: "center" })
    ], {
      dataScopeMode: "NARROW",
      fixedFilterConfig: { status: ["EFFECTIVE"] },
      rowActions: [
        {
          key: "view",
          type: "built-in",
          label: "查看",
          buttonType: "primary",
          link: true,
          sort: 1,
          enabled: true
        }
      ]
    })
  ],
  scopePolicies: [
    creatorPolicy("project_system_change_request", "项目系统变更"),
    policy(
      "project_system_change_current_assignee",
      "当前待办系统变更",
      "当前审批人为当前用户的数据。",
      "CURRENT_ASSIGNEE",
      {
        version: 1,
        type: "PERSONAL",
        fieldMapping: {
          userField: "current_task_assignee",
          deptField: "dept_id",
          statusField: "status"
        }
      }
    )
  ],
  scopeBindings: [
    binding("project_system_change_request_creator"),
    binding("project_system_change_current_assignee", "my_pending"),
    binding("project_system_change_request_creator", "effective")
  ],
  menus: [
    menu(
      "项目系统变更",
      "/entity-list/project_system_change_request/all",
      "entity:project_system_change_request:list",
      "all",
      61,
      "Switch"
    ),
    menu(
      "我的系统变更待办",
      "/entity-list/project_system_change_request/my_pending",
      "entity:project_system_change_request:list",
      "my_pending",
      62,
      "Clock"
    ),
    menu(
      "已生效系统变更",
      "/entity-list/project_system_change_request/effective",
      "entity:project_system_change_request:list",
      "effective",
      63,
      "CircleCheck"
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project", required: true, reason: "目标项目" },
    { type: "ENTITY", key: "system_asset", required: true, reason: "目标系统" },
    { type: "ENTITY", key: "project_system_link", required: true, reason: "生效对象" },
    { type: "ENTITY", key: "project_member", required: true, reason: "新责任人" },
    { type: "PROCESS", key: "project_system_change_process", required: true, reason: "关系变更审批流程" }
  ]
});

const projectMemberChangeRequest = baseEntity({
  businessKey: "project_member_change_request",
  assetName: "项目成员变更申请",
  description: "项目成员加入、退出、暂停、恢复、投入调整及权限和角色交接的审批与生效记录。",
  lifecycleMode: "WORKFLOW",
  processKey: "project_member_change_process",
  fields: [
    field("operation_type", "变更类型", "SELECT", "varchar(30)", true, 20, {
      fieldLength: 30,
      optionsJson: optionJson([
        ["成员加入", "JOIN"],
        ["成员退出", "LEAVE"],
        ["暂停参与", "SUSPEND"],
        ["恢复参与", "RESUME"],
        ["调整投入", "ALLOCATION_CHANGE"]
      ])
    }),
    refField("project_id", "所属项目", "CUSTOM", "project", true, 30),
    refField("project_member_id", "目标项目成员", "CUSTOM", "project_member", false, 40),
    refField("target_user_id", "目标人员", "USER", null, false, 50),
    refField("source_dept_id", "来源部门", "DEPT", null, false, 60),
    field("employment_type", "人员类型", "SELECT", "varchar(30)", false, 70, {
      fieldLength: 30,
      optionsJson: optionJson([
        ["内部员工", "INTERNAL"],
        ["合同人员", "CONTRACTOR"],
        ["供应商人员", "VENDOR"]
      ])
    }),
    field("effective_date", "计划生效日期", "DATE", "date", true, 80),
    field("planned_leave_date", "计划退出日期", "DATE", "date", false, 90),
    field("new_allocation_percentage", "新投入比例", "DECIMAL", "decimal(5,2)", false, 100, {
      precision: 5,
      scale: 2,
      validateRules: JSON.stringify({ min: 0.01, max: 100 })
    }),
    field("change_reason", "变更原因", "TEXT", "text", true, 110),
    field("account_required_flag", "需要项目账号", "BOOLEAN", "tinyint", true, 120, {
      defaultValue: "false"
    }),
    field("environment_access_required_flag", "需要环境权限", "BOOLEAN", "tinyint", true, 130, {
      defaultValue: "false"
    }),
    field("environment_scope", "环境范围", "MULTI_SELECT", "varchar(500)", false, 140, {
      fieldLength: 500,
      optionsJson: optionJson([
        ["开发环境", "DEV"],
        ["测试环境", "TEST"],
        ["验收环境", "UAT"],
        ["生产只读", "PROD_READ"],
        ["生产操作", "PROD_OPERATE"]
      ])
    }),
    field("sensitive_access_flag", "涉及敏感权限", "BOOLEAN", "tinyint", true, 150, {
      defaultValue: "false"
    }),
    field("handover_required_flag", "必须交接", "BOOLEAN", "tinyint", true, 160, {
      defaultValue: "false"
    }),
    refField("handover_member_id", "交接成员", "CUSTOM", "project_member", false, 170),
    field("handover_description", "交接说明", "TEXT", "text", false, 180),
    field("permission_revoke_deadline", "权限回收截止日期", "DATE", "date", false, 190),
    field("access_review_required_flag", "需要权限审核", "BOOLEAN", "tinyint", true, 200, {
      defaultValue: "false"
    }),
    field("security_review_required_flag", "需要安全审核", "BOOLEAN", "tinyint", true, 210, {
      defaultValue: "false"
    }),
    field("before_snapshot", "变更前成员快照", "TEXT", "longtext", false, 220),
    field("after_snapshot", "拟变更后成员快照", "TEXT", "longtext", false, 230),
    field("conflict_check_result", "跨实体校验结果", "TEXT", "longtext", false, 240),
    field("manager_reviewed_at", "项目经理复核时间", "DATETIME", "datetime", false, 250),
    refField("manager_review_operator_id", "项目经理复核操作人", "USER", null, false, 260),
    field("decision_trace", "最终决策轨迹", "TEXT", "longtext", false, 270),
    refField("effective_member_id", "生效项目成员", "CUSTOM", "project_member", false, 280),
    field("implementation_result", "实施结果", "TEXT", "text", false, 290),
    field("transferred_role_count", "已移交角色数", "INTEGER", "int", true, 300, {
      defaultValue: "0"
    }),
    field("effective_at", "实际生效时间", "DATETIME", "datetime", false, 310),
    ...workflowCommonFields(320)
  ],
  statuses: [
    status("DRAFT", "草稿", "NEW", 10, "#909399"),
    status("PENDING_MANAGER", "待项目经理审核", "PROCESSING", 20, "#409EFF"),
    status("PENDING_DEPT", "待人员部门审核", "PROCESSING", 30, "#409EFF"),
    status("ACCESS_REVIEW", "待权限审核", "PROCESSING", 40, "#E6A23C"),
    status("SECURITY_REVIEW", "待安全审核", "PROCESSING", 50, "#F56C6C"),
    status("PENDING_PMO", "待PMO审批", "PROCESSING", 60, "#409EFF"),
    status("APPROVED", "已批准待生效", "COMPLETED", 70, "#67C23A"),
    status("EFFECTIVE", "已生效", "COMPLETED", 80, "#2E7D32"),
    status("REJECTED", "已驳回", "TERMINATED", 90, "#F56C6C"),
    status("CANCELLED", "已取消", "TERMINATED", 100, "#606266"),
    status("FAILED", "生效失败", "TERMINATED", 110, "#C62828")
  ],
  codePrefix: "PMCR",
  codeExample: "PMCR2026073000001",
  forms: [
    form(
      "项目成员变更申请",
      "project_member_change_apply",
      "覆盖成员变更、投入校验、权限路由和角色交接的自定义整表单。",
      [
        formField("name", "申请标题", "STRING", 10, {
          hidden: true
        }),
        formField("operation_type", "变更类型", "SELECT", 20, {
          required: true,
          componentType: "select"
        }),
        formField("project_id", "所属项目", "REFERENCE", 30, {
          required: true,
          componentType: "reference"
        }),
        formField("applicant_id", "申请人", "USER", 40, {
          required: true,
          componentType: "user"
        }),
        formField("applicant_dept_id", "申请部门", "DEPT", 50, {
          required: true,
          componentType: "dept"
        }),
        formField("project_member_id", "目标项目成员", "REFERENCE", 60, {
          componentType: "reference"
        }),
        formField("target_user_id", "目标人员", "USER", 70, {
          componentType: "user"
        }),
        formField("source_dept_id", "来源部门", "DEPT", 80, {
          componentType: "dept"
        }),
        formField("employment_type", "人员类型", "SELECT", 90, {
          componentType: "select"
        }),
        formField("effective_date", "计划生效日期", "DATE", 100, {
          required: true,
          componentType: "date"
        }),
        formField("planned_leave_date", "计划退出日期", "DATE", 110, {
          componentType: "date"
        }),
        formField("new_allocation_percentage", "新投入比例", "DECIMAL", 120, {
          componentType: "number"
        }),
        formField("account_required_flag", "需要项目账号", "BOOLEAN", 130, {
          componentType: "switch"
        }),
        formField("environment_access_required_flag", "需要环境权限", "BOOLEAN", 140, {
          componentType: "switch"
        }),
        formField("environment_scope", "环境范围", "MULTI_SELECT", 150, {
          componentType: "select_multiple"
        }),
        formField("sensitive_access_flag", "涉及敏感权限", "BOOLEAN", 160, {
          componentType: "switch"
        }),
        formField("handover_member_id", "交接成员", "REFERENCE", 170, {
          componentType: "reference"
        }),
        formField("permission_revoke_deadline", "权限回收截止日期", "DATE", 180, {
          componentType: "date"
        }),
        formField("handover_description", "交接说明", "TEXT", 190, {
          componentType: "textarea",
          gridSpan: 24
        }),
        formField("change_reason", "变更原因", "TEXT", 200, {
          required: true,
          componentType: "textarea",
          gridSpan: 24
        })
      ],
      {
        isDefault: true,
        submitLabel: "保存并提交审批",
        customComponent: "ProjectMemberChangeForm",
        customComponentVersion: 1,
        customComponentSnapshotVersion: 1,
        customComponentProps: {
          title: "项目成员变更申请",
          showRoutePreview: true
        }
      }
    )
  ],
  lists: [
    entityList("all", "项目成员变更台账", "查询全部成员变更申请和当前流程状态。", [
      listField("code", "申请编号", 10, 180, { queryType: "LIKE" }),
      listField("name", "申请标题", 20, 260, { queryType: "LIKE" }),
      listField("operation_type", "变更类型", 30, 120),
      listField("project_id", "所属项目", 40, 200),
      listField("target_user_id", "目标人员", 50, 150),
      listField("effective_date", "计划生效", 60, 120, {
        queryType: "BETWEEN",
        align: "center"
      }),
      listField("access_review_required_flag", "权限审核", 70, 100, {
        align: "center"
      }),
      listField("security_review_required_flag", "安全审核", 80, 100, {
        align: "center"
      }),
      listField("status", "状态", 90, 150, {
        queryType: "IN",
        align: "center"
      }),
      listField("currentTaskName", "当前环节", 100, 180, {
        query: false
      })
    ], {
      isDefault: true,
      createLabel: "申请成员变更",
      rowActions: [
        ...defaultRowActions,
        {
          key: "approve",
          type: "built-in",
          label: "审批",
          buttonType: "warning",
          link: true,
          sort: 3,
          enabled: true
        }
      ]
    }),
    entityList("my_pending", "我的成员变更待办", "当前办理人为当前用户的成员变更申请。", [
      listField("code", "申请编号", 10, 180, { queryType: "LIKE" }),
      listField("name", "申请标题", 20, 260, { queryType: "LIKE" }),
      listField("operation_type", "变更类型", 30, 120),
      listField("project_id", "所属项目", 40, 200),
      listField("target_user_id", "目标人员", 50, 150),
      listField("currentTaskName", "当前环节", 60, 180, { query: false }),
      listField("submitTime", "提交时间", 70, 170, {
        queryType: "BETWEEN",
        align: "center"
      })
    ], {
      dataScopeMode: "NARROW",
      allowedScenes: ["PAGE"],
      selectionMode: "NONE",
      rowActions: [
        {
          key: "view",
          type: "built-in",
          label: "查看",
          buttonType: "primary",
          link: true,
          sort: 1,
          enabled: true
        },
        {
          key: "approve",
          type: "built-in",
          label: "审批",
          buttonType: "warning",
          link: true,
          sort: 2,
          enabled: true
        }
      ]
    }),
    entityList("effective", "已生效成员变更", "已成功写入项目成员和角色结果的申请。", [
      listField("code", "申请编号", 10, 180, { queryType: "LIKE" }),
      listField("name", "申请标题", 20, 260, { queryType: "LIKE" }),
      listField("operation_type", "变更类型", 30, 120),
      listField("project_id", "所属项目", 40, 200),
      listField("effective_member_id", "生效成员", 50, 180),
      listField("transferred_role_count", "移交角色数", 60, 100, {
        align: "right"
      }),
      listField("effective_at", "生效时间", 70, 170, {
        queryType: "BETWEEN",
        align: "center"
      }),
      listField("status", "状态", 80, 110, {
        queryType: "IN",
        align: "center"
      })
    ], {
      dataScopeMode: "NARROW",
      fixedFilterConfig: { status: ["EFFECTIVE"] },
      rowActions: [{
        key: "view",
        type: "built-in",
        label: "查看",
        buttonType: "primary",
        link: true,
        sort: 1,
        enabled: true
      }]
    })
  ],
  scopePolicies: [
    creatorPolicy("project_member_change_request", "项目成员变更"),
    policy(
      "project_member_change_current_assignee",
      "当前待办成员变更",
      "当前审批人为当前用户的数据。",
      "CURRENT_ASSIGNEE",
      {
        version: 1,
        type: "PERSONAL",
        fieldMapping: {
          userField: "current_task_assignee",
          deptField: "dept_id",
          statusField: "status"
        }
      }
    )
  ],
  scopeBindings: [
    binding("project_member_change_request_creator"),
    binding("project_member_change_current_assignee", "my_pending"),
    binding("project_member_change_request_creator", "effective")
  ],
  menus: [
    menu(
      "项目成员变更",
      "/entity-list/project_member_change_request/all",
      "entity:project_member_change_request:list",
      "all",
      64,
      "UserFilled"
    ),
    menu(
      "我的成员变更待办",
      "/entity-list/project_member_change_request/my_pending",
      "entity:project_member_change_request:list",
      "my_pending",
      65,
      "Clock"
    ),
    menu(
      "已生效成员变更",
      "/entity-list/project_member_change_request/effective",
      "entity:project_member_change_request:list",
      "effective",
      66,
      "CircleCheck"
    )
  ],
  extensions: [{
    extensionType: "FORM",
    extensionKey: "ProjectMemberChangeForm",
    displayName: "项目·成员变更表单",
    version: 1,
    snapshotVersion: 1,
    supportedModesDocument: ["CREATE", "EDIT", "APPROVE", "VIEW"],
    supportedNodeTypesDocument: [],
    supportedBindingsDocument: [],
    configSchemaDocument: {
      type: "object",
      properties: {
        title: {
          type: "string",
          title: "表单标题"
        },
        showRoutePreview: {
          type: "boolean",
          title: "显示审批路径"
        }
      }
    },
    capabilitiesDocument: {
      exposesValidate: true,
      computesWorkflowRouteFlags: true,
      crossEntityContext: true
    },
    status: "ACTIVE"
  }],
  dependencies: [
    { type: "ENTITY", key: "project", required: true, reason: "所属项目" },
    { type: "ENTITY", key: "project_member", required: true, reason: "成员与交接对象" },
    { type: "ENTITY", key: "project_role_assignment", required: true, reason: "角色暂停和移交" },
    { type: "PROCESS", key: "project_member_change_process", required: true, reason: "成员变更审批流程" },
    { type: "UI_EXTENSION", key: "FORM:ProjectMemberChangeForm:1", required: true, reason: "自定义成员变更表单" }
  ]
});

const writeJson = (directory, fileName, value) => {
  fs.mkdirSync(directory, { recursive: true });
  fs.writeFileSync(
    path.join(directory, fileName),
    `${JSON.stringify(value, null, 2)}\n`,
    "utf8"
  );
};

const xmlEscape = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("\"", "&quot;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");

const approval = (commentLabel, options) => ({
  enabled: true,
  commentLabel,
  options: options.map(([value, label, type, remarkRequired = false]) => ({
    value,
    label,
    type,
    showComment: true,
    remarkRequired
  }))
});

const buildProcessAssets = ({
  processKey,
  processName,
  description,
  entityCode,
  fileName,
  nodes,
  flows,
  statusMappings,
  flowActions,
  dependencies
}) => {
  const incoming = new Map(nodes.map((node) => [node.id, []]));
  const outgoing = new Map(nodes.map((node) => [node.id, []]));
  for (const flowItem of flows) {
    outgoing.get(flowItem.source)?.push(flowItem.id);
    incoming.get(flowItem.target)?.push(flowItem.id);
  }

  const elementXml = nodes.map((node) => {
    const attrs = [
      `id="${node.id}"`,
      node.name ? `name="${xmlEscape(node.name)}"` : "",
      node.type === "userTask" ? `flowable:assignee="wf-user://admin"` : "",
      node.type === "userTask" ? `flowable:formKey="${xmlEscape(node.formRef)}"` : "",
      node.defaultFlow ? `default="${node.defaultFlow}"` : ""
    ].filter(Boolean).join(" ");
    const children = [];
    if (node.type === "userTask") {
      children.push(
        "      <extensionElements>",
        "        <flowable:properties>",
        `          <flowable:property name="approvalConfig" value="${xmlEscape(JSON.stringify(node.approval))}" />`,
        "        </flowable:properties>",
        "      </extensionElements>"
      );
    }
    for (const flowId of incoming.get(node.id) ?? []) {
      children.push(`      <incoming>${flowId}</incoming>`);
    }
    for (const flowId of outgoing.get(node.id) ?? []) {
      children.push(`      <outgoing>${flowId}</outgoing>`);
    }
    return [
      `    <${node.type} ${attrs}>`,
      ...children,
      `    </${node.type}>`
    ].join("\n");
  });

  const flowXml = flows.map((flowItem) => {
    if (!flowItem.condition) {
      return `    <sequenceFlow id="${flowItem.id}" sourceRef="${flowItem.source}" targetRef="${flowItem.target}" />`;
    }
    return [
      `    <sequenceFlow id="${flowItem.id}" sourceRef="${flowItem.source}" targetRef="${flowItem.target}">`,
      `      <conditionExpression xsi:type="tFormalExpression">${flowItem.condition}</conditionExpression>`,
      "    </sequenceFlow>"
    ].join("\n");
  });

  const shapeXml = nodes.map((node) => {
    const marker = node.type === "exclusiveGateway" ? " isMarkerVisible=\"true\"" : "";
    return [
      `      <bpmndi:BPMNShape id="${node.id}_di" bpmnElement="${node.id}"${marker}>`,
      `        <dc:Bounds x="${node.x}" y="${node.y}" width="${node.w}" height="${node.h}" />`,
      "      </bpmndi:BPMNShape>"
    ].join("\n");
  });

  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const center = (node) => ({
    x: node.x + node.w / 2,
    y: node.y + node.h / 2
  });
  const edgeXml = flows.map((flowItem) => {
    const source = center(nodeById.get(flowItem.source));
    const target = center(nodeById.get(flowItem.target));
    const waypoints = [];
    if (flowItem.route === "reject") {
      const bendX = source.x + 35;
      const routeY = 16;
      waypoints.push(source, { x: bendX, y: source.y }, { x: bendX, y: routeY }, {
        x: target.x,
        y: routeY
      }, target);
    } else if (flowItem.route === "skip") {
      const routeY = 330;
      waypoints.push(source, { x: source.x + 30, y: source.y }, {
        x: source.x + 30,
        y: routeY
      }, {
        x: target.x - 30,
        y: routeY
      }, {
        x: target.x - 30,
        y: target.y
      }, target);
    } else {
      waypoints.push(source, target);
    }
    return [
      `      <bpmndi:BPMNEdge id="${flowItem.id}_di" bpmnElement="${flowItem.id}">`,
      ...waypoints.map((point) => `        <di:waypoint x="${point.x}" y="${point.y}" />`),
      "      </bpmndi:BPMNEdge>"
    ].join("\n");
  });

  const bpmn = [
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
    "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"http://workflow.com/project\">",
    `  <process id="${processKey}" name="${xmlEscape(processName)}" isExecutable="true">`,
    ...elementXml,
    ...flowXml,
    "  </process>",
    `  <bpmndi:BPMNDiagram id="BPMNDiagram_${processKey}">`,
    `    <bpmndi:BPMNPlane id="BPMNPlane_${processKey}" bpmnElement="${processKey}">`,
    ...shapeXml,
    ...edgeXml,
    "    </bpmndi:BPMNPlane>",
    "  </bpmndi:BPMNDiagram>",
    "</definitions>",
    ""
  ].join("\n");

  fs.mkdirSync(bpmnDir, { recursive: true });
  fs.writeFileSync(path.join(bpmnDir, fileName), bpmn, "utf8");

  const userTasks = nodes.filter((node) => node.type === "userTask");
  const processAsset = {
    schemaVersion: 1,
    assetType: "PROCESS",
    businessKey: processKey,
    assetName: processName,
    definition: {
      processKey,
      processName,
      description,
      category: "PROJECT_MANAGEMENT"
    },
    bpmnFile: `project-config/bpmn/${fileName}`,
    nodes: userTasks.map((node) => ({
      nodeId: node.id,
      nodeName: node.name,
      nodeType: "USER_TASK",
      assignees: [{
        assigneeType: "USER",
        assigneeValue: "wf-user://admin",
        assigneeName: node.assigneeName,
        priority: 0
      }]
    })),
    nodeForms: userTasks.map((node, index) => ({
      nodeId: node.id,
      nodeName: node.name,
      formRef: node.formRef,
      isReadonly: node.readonly ? 1 : 0,
      sortOrder: (index + 1) * 10
    })),
    nodeApprovals: userTasks.map((node) => ({
      nodeId: node.id,
      nodeName: node.name,
      enabled: 1,
      commentLabel: node.approval.commentLabel,
      optionsJson: JSON.stringify(node.approval.options)
    })),
    flowActions,
    statusMappings,
    dependencies
  };
  writeJson(processDir, `${processKey}-v1.json`, processAsset);
};

const task = (
  id,
  name,
  x,
  formRef,
  assigneeName,
  approvalConfig,
  readonly = true
) => ({
  id,
  name,
  type: "userTask",
  x,
  y: 170,
  w: 112,
  h: 80,
  formRef,
  assigneeName,
  approval: approvalConfig,
  readonly
});

const gateway = (id, name, x, defaultFlow) => ({
  id,
  name,
  type: "exclusiveGateway",
  x,
  y: 185,
  w: 50,
  h: 50,
  defaultFlow
});

const startNode = (name) => ({
  id: "start",
  name,
  type: "startEvent",
  x: 45,
  y: 192,
  w: 36,
  h: 36
});

const endNode = (id, name, x, y) => ({
  id,
  name,
  type: "endEvent",
  x,
  y,
  w: 36,
  h: 36
});

const approveReject = (approveLabel, rejectLabel = "驳回") => approval(
  "审批意见",
  [
    ["approve", approveLabel, "primary", false],
    ["reject", rejectLabel, "danger", true]
  ]
);

const commonNotification = (actionName, templateCode, sortOrder) => ({
  scopeType: "PROCESS",
  elementId: null,
  triggerTiming: "PROCESS_COMPLETED",
  executionMode: "AFTER_COMMIT",
  failurePolicy: "RETRY",
  retryConfig: JSON.stringify({
    maxRetries: 3,
    initialDelaySeconds: 30,
    maxDelaySeconds: 300
  }),
  actionName,
  description: "流程完成后发送结果通知。",
  interfaceName: "sendNotificationHandler",
  methodName: "execute",
  paramsJson: JSON.stringify({
    templateCode,
    notifyType: "in_app",
    receiverExpr: "${submitterId}"
  }),
  sortOrder,
  enabled: true,
  status: "DRAFT"
});

const projectNodes = [
  startNode("提交项目立项"),
  task(
    "business_review",
    "业务负责人审核",
    145,
    "wf-form://project/project_detail",
    "模拟业务负责人",
    approveReject("确认目标与范围"),
    true
  ),
  gateway("business_result", "业务审核结果", 315, "flow_business_approve"),
  task(
    "pmo_review",
    "PMO立项评审",
    430,
    "wf-form://project/project_governance_review",
    "模拟PMO",
    approveReject("PMO审核通过"),
    false
  ),
  gateway("pmo_result", "PMO评审结果", 600, "flow_pmo_approve"),
  gateway("system_needed", "系统会签判断", 725, "flow_skip_system"),
  task(
    "system_owner_review",
    "关联系统负责人会签",
    840,
    "wf-form://project/project_detail",
    "模拟系统负责人",
    approveReject("系统范围通过"),
    true
  ),
  gateway("system_result", "系统会签结果", 1010, "flow_system_approve"),
  gateway("architecture_needed", "架构评审判断", 1135, "flow_skip_architecture"),
  task(
    "architecture_review",
    "企业架构评审",
    1250,
    "wf-form://project/project_governance_review",
    "模拟企业架构师",
    approveReject("架构方案可行"),
    false
  ),
  gateway("architecture_result", "架构评审结果", 1420, "flow_architecture_approve"),
  gateway("security_needed", "安全评审判断", 1545, "flow_skip_security"),
  task(
    "security_review",
    "安全专项评审",
    1660,
    "wf-form://project/project_governance_review",
    "模拟安全负责人",
    approveReject("安全要求通过"),
    false
  ),
  gateway("security_result", "安全评审结果", 1830, "flow_security_approve"),
  gateway("data_needed", "数据评审判断", 1955, "flow_skip_data"),
  task(
    "data_review",
    "数据专项评审",
    2070,
    "wf-form://project/project_governance_review",
    "模拟数据负责人",
    approveReject("数据方案通过"),
    false
  ),
  gateway("data_result", "数据评审结果", 2240, "flow_data_approve"),
  task(
    "sponsor_review",
    "项目发起人最终批准",
    2365,
    "wf-form://project/project_detail",
    "模拟项目发起人",
    approveReject("批准立项", "不批准立项"),
    true
  ),
  gateway("sponsor_result", "发起人审批结果", 2535, "flow_sponsor_approve"),
  endNode("end_approved", "立项已批准", 2690, 192),
  endNode("end_rejected", "立项已驳回", 2690, 16)
];

const projectFlows = [
  { id: "flow_start_business", source: "start", target: "business_review" },
  { id: "flow_business_result", source: "business_review", target: "business_result" },
  {
    id: "flow_business_reject",
    source: "business_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_business_approve", source: "business_result", target: "pmo_review" },
  { id: "flow_pmo_result", source: "pmo_review", target: "pmo_result" },
  {
    id: "flow_pmo_reject",
    source: "pmo_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_pmo_approve", source: "pmo_result", target: "system_needed" },
  {
    id: "flow_need_system",
    source: "system_needed",
    target: "system_owner_review",
    condition: "${project_type != 'RESEARCH'}"
  },
  {
    id: "flow_skip_system",
    source: "system_needed",
    target: "architecture_needed",
    route: "skip"
  },
  { id: "flow_system_result", source: "system_owner_review", target: "system_result" },
  {
    id: "flow_system_reject",
    source: "system_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_system_approve", source: "system_result", target: "architecture_needed" },
  {
    id: "flow_need_architecture",
    source: "architecture_needed",
    target: "architecture_review",
    condition: "${project_type == 'NEW_SYSTEM' || project_type == 'INTEGRATION' || project_type == 'MIGRATION' || cross_system_flag == true}"
  },
  {
    id: "flow_skip_architecture",
    source: "architecture_needed",
    target: "security_needed",
    route: "skip"
  },
  { id: "flow_architecture_result", source: "architecture_review", target: "architecture_result" },
  {
    id: "flow_architecture_reject",
    source: "architecture_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_architecture_approve", source: "architecture_result", target: "security_needed" },
  {
    id: "flow_need_security",
    source: "security_needed",
    target: "security_review",
    condition: "${security_involved_flag == true}"
  },
  {
    id: "flow_skip_security",
    source: "security_needed",
    target: "data_needed",
    route: "skip"
  },
  { id: "flow_security_result", source: "security_review", target: "security_result" },
  {
    id: "flow_security_reject",
    source: "security_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_security_approve", source: "security_result", target: "data_needed" },
  {
    id: "flow_need_data",
    source: "data_needed",
    target: "data_review",
    condition: "${data_involved_flag == true}"
  },
  {
    id: "flow_skip_data",
    source: "data_needed",
    target: "sponsor_review",
    route: "skip"
  },
  { id: "flow_data_result", source: "data_review", target: "data_result" },
  {
    id: "flow_data_reject",
    source: "data_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_data_approve", source: "data_result", target: "sponsor_review" },
  { id: "flow_sponsor_result", source: "sponsor_review", target: "sponsor_result" },
  {
    id: "flow_sponsor_reject",
    source: "sponsor_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_sponsor_approve", source: "sponsor_result", target: "end_approved" }
];

const projectStatusMappings = [
  {
    entityCode: "project",
    sequenceFlowId: "flow_start_business",
    sourceNodeId: "start",
    sourceNodeName: "提交项目立项",
    targetNodeId: "business_review",
    targetNodeName: "业务负责人审核",
    entityStatus: "PENDING_APPROVAL",
    entityStatusCode: "PENDING_APPROVAL",
    statusCategory: "PROCESSING",
    sortOrder: 10
  },
  ...[
    ["flow_business_reject", "business_result", "业务审核结果"],
    ["flow_pmo_reject", "pmo_result", "PMO评审结果"],
    ["flow_system_reject", "system_result", "系统会签结果"],
    ["flow_architecture_reject", "architecture_result", "架构评审结果"],
    ["flow_security_reject", "security_result", "安全评审结果"],
    ["flow_data_reject", "data_result", "数据评审结果"],
    ["flow_sponsor_reject", "sponsor_result", "发起人审批结果"]
  ].map(([sequenceFlowId, sourceNodeId, sourceNodeName], index) => ({
    entityCode: "project",
    sequenceFlowId,
    sourceNodeId,
    sourceNodeName,
    targetNodeId: "end_rejected",
    targetNodeName: "立项已驳回",
    entityStatus: "REJECTED",
    entityStatusCode: "REJECTED",
    statusCategory: "TERMINATED",
    sortOrder: 20 + index * 10
  })),
  {
    entityCode: "project",
    sequenceFlowId: "flow_sponsor_approve",
    sourceNodeId: "sponsor_result",
    sourceNodeName: "发起人审批结果",
    targetNodeId: "end_approved",
    targetNodeName: "立项已批准",
    entityStatus: "APPROVED",
    entityStatusCode: "APPROVED",
    statusCategory: "COMPLETED",
    sortOrder: 100
  }
];

const validationAction = (
  actionName,
  description,
  interfaceName,
  sortOrder
) => ({
  scopeType: "PROCESS",
  elementId: null,
  triggerTiming: "PROCESS_STARTED",
  executionMode: "IN_TRANSACTION",
  failurePolicy: "ROLLBACK",
  retryConfig: JSON.stringify({ maxRetries: 0 }),
  actionName,
  description,
  interfaceName,
  methodName: "execute",
  paramsJson: "{}",
  sortOrder,
  enabled: true,
  status: "DRAFT"
});

const applyAction = (
  actionName,
  description,
  interfaceName,
  sortOrder
) => ({
  scopeType: "PROCESS",
  elementId: null,
  triggerTiming: "PROCESS_COMPLETED",
  executionMode: "AFTER_COMMIT",
  failurePolicy: "RETRY",
  retryConfig: JSON.stringify({
    maxRetries: 5,
    initialDelaySeconds: 10,
    maxDelaySeconds: 300
  }),
  actionName,
  description,
  interfaceName,
  methodName: "execute",
  paramsJson: "{}",
  sortOrder,
  enabled: true,
  status: "DRAFT"
});

buildProcessAssets({
  processKey: "project_initiation_process",
  processName: "项目立项审批",
  description: "业务、PMO、系统、架构、安全、数据和项目发起人条件审批；批准后初始化项目治理关系。",
  entityCode: "project",
  fileName: "project-initiation.bpmn20.xml",
  nodes: projectNodes,
  flows: projectFlows,
  statusMappings: projectStatusMappings,
  flowActions: [
    validationAction(
      "立项跨实体门禁检查",
      "校验需求状态与分配比例、系统状态、日期和关键人员。",
      "validateProjectInitiationHandler",
      10
    ),
    applyAction(
      "批准后初始化项目治理关系",
      "激活需求和系统关系，创建关键成员及角色分配。",
      "applyProjectInitiationHandler",
      20
    ),
    commonNotification(
      "发送项目立项结果通知",
      "PROJECT_INITIATION_RESULT",
      30
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project", required: true, reason: "实体状态映射" },
    { type: "FORM", key: "wf-form://project/project_detail", required: true, reason: "审批详情表单" },
    { type: "FORM", key: "wf-form://project/project_governance_review", required: true, reason: "治理评审表单" },
    { type: "USER", key: "admin", required: true, reason: "单账号模拟审批人" },
    { type: "FLOW_ACTION_HANDLER", key: "validateProjectInitiationHandler", required: true, reason: "跨实体立项门禁" },
    { type: "FLOW_ACTION_HANDLER", key: "applyProjectInitiationHandler", required: true, reason: "批准后初始化关系" },
    { type: "FLOW_ACTION_HANDLER", key: "sendNotificationHandler", required: true, reason: "流程完成通知" }
  ]
});

const changeNodes = [
  startNode("提交项目系统变更"),
  task(
    "project_manager_review",
    "项目经理确认",
    145,
    "wf-form://project_system_change_request/project_system_change_detail",
    "模拟项目经理",
    approveReject("确认提交变更"),
    true
  ),
  gateway("manager_result", "项目经理确认结果", 315, "flow_manager_approve"),
  task(
    "system_owner_review",
    "系统负责人审批",
    430,
    "wf-form://project_system_change_request/project_system_change_review",
    "模拟系统负责人",
    approveReject("系统边界通过"),
    false
  ),
  gateway("system_result", "系统审批结果", 600, "flow_system_approve"),
  task(
    "technical_review",
    "技术负责人审批",
    725,
    "wf-form://project_system_change_request/project_system_change_review",
    "模拟技术负责人",
    approveReject("技术影响通过"),
    false
  ),
  gateway("technical_result", "技术审批结果", 895, "flow_technical_approve"),
  gateway("architecture_needed", "架构评审判断", 1020, "flow_skip_architecture"),
  task(
    "architecture_review",
    "架构影响评审",
    1135,
    "wf-form://project_system_change_request/project_system_change_review",
    "模拟企业架构师",
    approveReject("架构影响可接受"),
    false
  ),
  gateway("architecture_result", "架构评审结果", 1305, "flow_architecture_approve"),
  gateway("security_needed", "安全评审判断", 1430, "flow_skip_security"),
  task(
    "security_review",
    "安全影响评审",
    1545,
    "wf-form://project_system_change_request/project_system_change_review",
    "模拟安全负责人",
    approveReject("安全影响可接受"),
    false
  ),
  gateway("security_result", "安全评审结果", 1715, "flow_security_approve"),
  gateway("data_needed", "数据评审判断", 1840, "flow_skip_data"),
  task(
    "data_review",
    "数据影响评审",
    1955,
    "wf-form://project_system_change_request/project_system_change_review",
    "模拟数据负责人",
    approveReject("数据影响可接受"),
    false
  ),
  gateway("data_result", "数据评审结果", 2125, "flow_data_approve"),
  task(
    "pmo_review",
    "PMO纳入基线审批",
    2250,
    "wf-form://project_system_change_request/project_system_change_review",
    "模拟PMO",
    approveReject("批准并纳入基线", "不批准变更"),
    false
  ),
  gateway("pmo_result", "PMO审批结果", 2420, "flow_pmo_approve"),
  endNode("end_approved", "变更已批准", 2575, 192),
  endNode("end_rejected", "变更已驳回", 2575, 16)
];

const changeFlows = [
  { id: "flow_start_manager", source: "start", target: "project_manager_review" },
  { id: "flow_manager_result", source: "project_manager_review", target: "manager_result" },
  {
    id: "flow_manager_reject",
    source: "manager_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_manager_approve", source: "manager_result", target: "system_owner_review" },
  { id: "flow_system_result", source: "system_owner_review", target: "system_result" },
  {
    id: "flow_system_reject",
    source: "system_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_system_approve", source: "system_result", target: "technical_review" },
  { id: "flow_technical_result", source: "technical_review", target: "technical_result" },
  {
    id: "flow_technical_reject",
    source: "technical_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_technical_approve", source: "technical_result", target: "architecture_needed" },
  {
    id: "flow_need_architecture",
    source: "architecture_needed",
    target: "architecture_review",
    condition: "${risk_level == 'HIGH' || risk_level == 'CRITICAL' || operation_type == 'REMOVE'}"
  },
  {
    id: "flow_skip_architecture",
    source: "architecture_needed",
    target: "security_needed",
    route: "skip"
  },
  { id: "flow_architecture_result", source: "architecture_review", target: "architecture_result" },
  {
    id: "flow_architecture_reject",
    source: "architecture_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_architecture_approve", source: "architecture_result", target: "security_needed" },
  {
    id: "flow_need_security",
    source: "security_needed",
    target: "security_review",
    condition: "${security_involved_flag == true}"
  },
  {
    id: "flow_skip_security",
    source: "security_needed",
    target: "data_needed",
    route: "skip"
  },
  { id: "flow_security_result", source: "security_review", target: "security_result" },
  {
    id: "flow_security_reject",
    source: "security_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_security_approve", source: "security_result", target: "data_needed" },
  {
    id: "flow_need_data",
    source: "data_needed",
    target: "data_review",
    condition: "${data_involved_flag == true}"
  },
  {
    id: "flow_skip_data",
    source: "data_needed",
    target: "pmo_review",
    route: "skip"
  },
  { id: "flow_data_result", source: "data_review", target: "data_result" },
  {
    id: "flow_data_reject",
    source: "data_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_data_approve", source: "data_result", target: "pmo_review" },
  { id: "flow_pmo_result", source: "pmo_review", target: "pmo_result" },
  {
    id: "flow_pmo_reject",
    source: "pmo_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_pmo_approve", source: "pmo_result", target: "end_approved" }
];

const changeStatusMappings = [
  {
    entityCode: "project_system_change_request",
    sequenceFlowId: "flow_start_manager",
    sourceNodeId: "start",
    sourceNodeName: "提交项目系统变更",
    targetNodeId: "project_manager_review",
    targetNodeName: "项目经理确认",
    entityStatus: "IMPACT_ASSESSING",
    entityStatusCode: "IMPACT_ASSESSING",
    statusCategory: "PROCESSING",
    sortOrder: 10
  },
  {
    entityCode: "project_system_change_request",
    sequenceFlowId: "flow_manager_approve",
    sourceNodeId: "manager_result",
    sourceNodeName: "项目经理确认结果",
    targetNodeId: "system_owner_review",
    targetNodeName: "系统负责人审批",
    entityStatus: "PENDING_APPROVAL",
    entityStatusCode: "PENDING_APPROVAL",
    statusCategory: "PROCESSING",
    sortOrder: 20
  },
  ...[
    ["flow_manager_reject", "manager_result", "项目经理确认结果"],
    ["flow_system_reject", "system_result", "系统审批结果"],
    ["flow_technical_reject", "technical_result", "技术审批结果"],
    ["flow_architecture_reject", "architecture_result", "架构评审结果"],
    ["flow_security_reject", "security_result", "安全评审结果"],
    ["flow_data_reject", "data_result", "数据评审结果"],
    ["flow_pmo_reject", "pmo_result", "PMO审批结果"]
  ].map(([sequenceFlowId, sourceNodeId, sourceNodeName], index) => ({
    entityCode: "project_system_change_request",
    sequenceFlowId,
    sourceNodeId,
    sourceNodeName,
    targetNodeId: "end_rejected",
    targetNodeName: "变更已驳回",
    entityStatus: "REJECTED",
    entityStatusCode: "REJECTED",
    statusCategory: "TERMINATED",
    sortOrder: 30 + index * 10
  })),
  {
    entityCode: "project_system_change_request",
    sequenceFlowId: "flow_pmo_approve",
    sourceNodeId: "pmo_result",
    sourceNodeName: "PMO审批结果",
    targetNodeId: "end_approved",
    targetNodeName: "变更已批准",
    entityStatus: "APPROVED",
    entityStatusCode: "APPROVED",
    statusCategory: "COMPLETED",
    sortOrder: 110
  }
];

buildProcessAssets({
  processKey: "project_system_change_process",
  processName: "项目系统关系变更审批",
  description: "项目经理、系统、技术、架构、安全、数据和PMO条件审批；批准后由代码生效关系。",
  entityCode: "project_system_change_request",
  fileName: "project-system-change.bpmn20.xml",
  nodes: changeNodes,
  flows: changeFlows,
  statusMappings: changeStatusMappings,
  flowActions: [
    validationAction(
      "项目系统变更跨实体门禁",
      "校验项目系统状态、当前有效关系、责任成员和移除依赖。",
      "validateProjectSystemChangeHandler",
      10
    ),
    applyAction(
      "批准后生效项目系统关系",
      "新增、更新或失效项目系统关系，并回写生效结果。",
      "applyProjectSystemChangeHandler",
      20
    ),
    commonNotification(
      "发送项目系统变更结果通知",
      "PROJECT_SYSTEM_CHANGE_RESULT",
      30
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project_system_change_request", required: true, reason: "实体状态映射" },
    { type: "FORM", key: "wf-form://project_system_change_request/project_system_change_detail", required: true, reason: "审批详情表单" },
    { type: "FORM", key: "wf-form://project_system_change_request/project_system_change_review", required: true, reason: "专业评审表单" },
    { type: "USER", key: "admin", required: true, reason: "单账号模拟审批人" },
    { type: "FLOW_ACTION_HANDLER", key: "validateProjectSystemChangeHandler", required: true, reason: "跨实体变更门禁" },
    { type: "FLOW_ACTION_HANDLER", key: "applyProjectSystemChangeHandler", required: true, reason: "批准后生效关系" },
    { type: "FLOW_ACTION_HANDLER", key: "sendNotificationHandler", required: true, reason: "流程完成通知" }
  ]
});

const memberChangeNodes = [
  startNode("提交项目成员变更"),
  task(
    "project_manager_review",
    "项目经理审核",
    145,
    "wf-form://project_member_change_request/project_member_change_apply",
    "模拟项目经理",
    approveReject("确认资源安排"),
    true
  ),
  gateway("manager_result", "项目经理审核结果", 315, "flow_manager_approve"),
  task(
    "department_review",
    "人员部门负责人审核",
    430,
    "wf-form://project_member_change_request/project_member_change_apply",
    "模拟人员部门负责人",
    approveReject("确认人员与投入安排"),
    true
  ),
  gateway("department_result", "部门审核结果", 600, "flow_department_approve"),
  gateway("access_needed", "权限审核判断", 725, "flow_skip_access"),
  task(
    "system_owner_access_review",
    "系统负责人权限审核",
    840,
    "wf-form://project_member_change_request/project_member_change_apply",
    "模拟系统负责人",
    approveReject("确认账号与环境权限"),
    true
  ),
  gateway("access_result", "权限审核结果", 1010, "flow_access_approve"),
  gateway("security_needed", "安全审核判断", 1135, "flow_skip_security"),
  task(
    "security_review",
    "安全负责人审核",
    1250,
    "wf-form://project_member_change_request/project_member_change_apply",
    "模拟安全负责人",
    approveReject("确认敏感权限与回收安排"),
    true
  ),
  gateway("security_result", "安全审核结果", 1420, "flow_security_approve"),
  task(
    "pmo_review",
    "PMO最终审批",
    1545,
    "wf-form://project_member_change_request/project_member_change_apply",
    "模拟PMO",
    approveReject("批准成员变更", "不批准成员变更"),
    true
  ),
  gateway("pmo_result", "PMO审批结果", 1715, "flow_pmo_approve"),
  endNode("end_approved", "成员变更已批准", 1870, 192),
  endNode("end_rejected", "成员变更已驳回", 1870, 16)
];

const memberChangeFlows = [
  { id: "flow_start_manager", source: "start", target: "project_manager_review" },
  { id: "flow_manager_result", source: "project_manager_review", target: "manager_result" },
  {
    id: "flow_manager_reject",
    source: "manager_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_manager_approve", source: "manager_result", target: "department_review" },
  { id: "flow_department_result", source: "department_review", target: "department_result" },
  {
    id: "flow_department_reject",
    source: "department_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_department_approve", source: "department_result", target: "access_needed" },
  {
    id: "flow_need_access",
    source: "access_needed",
    target: "system_owner_access_review",
    condition: "${access_review_required_flag == true}"
  },
  {
    id: "flow_skip_access",
    source: "access_needed",
    target: "security_needed",
    route: "skip"
  },
  { id: "flow_access_result", source: "system_owner_access_review", target: "access_result" },
  {
    id: "flow_access_reject",
    source: "access_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_access_approve", source: "access_result", target: "security_needed" },
  {
    id: "flow_need_security",
    source: "security_needed",
    target: "security_review",
    condition: "${security_review_required_flag == true}"
  },
  {
    id: "flow_skip_security",
    source: "security_needed",
    target: "pmo_review",
    route: "skip"
  },
  { id: "flow_security_result", source: "security_review", target: "security_result" },
  {
    id: "flow_security_reject",
    source: "security_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_security_approve", source: "security_result", target: "pmo_review" },
  { id: "flow_pmo_result", source: "pmo_review", target: "pmo_result" },
  {
    id: "flow_pmo_reject",
    source: "pmo_result",
    target: "end_rejected",
    condition: "${approved == 'reject'}",
    route: "reject"
  },
  { id: "flow_pmo_approve", source: "pmo_result", target: "end_approved" }
];

const memberChangeStatusMappings = [
  {
    entityCode: "project_member_change_request",
    sequenceFlowId: "flow_start_manager",
    sourceNodeId: "start",
    sourceNodeName: "提交项目成员变更",
    targetNodeId: "project_manager_review",
    targetNodeName: "项目经理审核",
    entityStatus: "PENDING_MANAGER",
    entityStatusCode: "PENDING_MANAGER",
    statusCategory: "PROCESSING",
    sortOrder: 10
  },
  {
    entityCode: "project_member_change_request",
    sequenceFlowId: "flow_manager_approve",
    sourceNodeId: "manager_result",
    sourceNodeName: "项目经理审核结果",
    targetNodeId: "department_review",
    targetNodeName: "人员部门负责人审核",
    entityStatus: "PENDING_DEPT",
    entityStatusCode: "PENDING_DEPT",
    statusCategory: "PROCESSING",
    sortOrder: 20
  },
  {
    entityCode: "project_member_change_request",
    sequenceFlowId: "flow_need_access",
    sourceNodeId: "access_needed",
    sourceNodeName: "权限审核判断",
    targetNodeId: "system_owner_access_review",
    targetNodeName: "系统负责人权限审核",
    entityStatus: "ACCESS_REVIEW",
    entityStatusCode: "ACCESS_REVIEW",
    statusCategory: "PROCESSING",
    sortOrder: 30
  },
  {
    entityCode: "project_member_change_request",
    sequenceFlowId: "flow_need_security",
    sourceNodeId: "security_needed",
    sourceNodeName: "安全审核判断",
    targetNodeId: "security_review",
    targetNodeName: "安全负责人审核",
    entityStatus: "SECURITY_REVIEW",
    entityStatusCode: "SECURITY_REVIEW",
    statusCategory: "PROCESSING",
    sortOrder: 40
  },
  {
    entityCode: "project_member_change_request",
    sequenceFlowId: "flow_skip_security",
    sourceNodeId: "security_needed",
    sourceNodeName: "安全审核判断",
    targetNodeId: "pmo_review",
    targetNodeName: "PMO最终审批",
    entityStatus: "PENDING_PMO",
    entityStatusCode: "PENDING_PMO",
    statusCategory: "PROCESSING",
    sortOrder: 50
  },
  {
    entityCode: "project_member_change_request",
    sequenceFlowId: "flow_security_approve",
    sourceNodeId: "security_result",
    sourceNodeName: "安全审核结果",
    targetNodeId: "pmo_review",
    targetNodeName: "PMO最终审批",
    entityStatus: "PENDING_PMO",
    entityStatusCode: "PENDING_PMO",
    statusCategory: "PROCESSING",
    sortOrder: 60
  },
  ...[
    ["flow_manager_reject", "manager_result", "项目经理审核结果"],
    ["flow_department_reject", "department_result", "部门审核结果"],
    ["flow_access_reject", "access_result", "权限审核结果"],
    ["flow_security_reject", "security_result", "安全审核结果"],
    ["flow_pmo_reject", "pmo_result", "PMO审批结果"]
  ].map(([sequenceFlowId, sourceNodeId, sourceNodeName], index) => ({
    entityCode: "project_member_change_request",
    sequenceFlowId,
    sourceNodeId,
    sourceNodeName,
    targetNodeId: "end_rejected",
    targetNodeName: "成员变更已驳回",
    entityStatus: "REJECTED",
    entityStatusCode: "REJECTED",
    statusCategory: "TERMINATED",
    sortOrder: 70 + index * 10
  })),
  {
    entityCode: "project_member_change_request",
    sequenceFlowId: "flow_pmo_approve",
    sourceNodeId: "pmo_result",
    sourceNodeName: "PMO审批结果",
    targetNodeId: "end_approved",
    targetNodeName: "成员变更已批准",
    entityStatus: "APPROVED",
    entityStatusCode: "APPROVED",
    statusCategory: "COMPLETED",
    sortOrder: 120
  }
];

const memberChangeNodeAction = {
  scopeType: "NODE",
  elementId: "project_manager_review",
  triggerTiming: "NODE_COMPLETED",
  executionMode: "IN_TRANSACTION",
  failurePolicy: "ROLLBACK",
  retryConfig: JSON.stringify({ maxRetries: 0 }),
  actionName: "记录项目经理成员变更复核",
  description: "项目经理节点完成后写入复核时间和操作人。",
  interfaceName: "captureProjectMemberManagerReviewHandler",
  methodName: "execute",
  paramsJson: "{}",
  sortOrder: 20,
  enabled: true,
  status: "DRAFT"
};

const memberChangeTransitionAction = {
  scopeType: "SEQUENCE_FLOW",
  elementId: "flow_pmo_approve",
  triggerTiming: "TRANSITION_TAKEN",
  executionMode: "IN_TRANSACTION",
  failurePolicy: "ROLLBACK",
  retryConfig: JSON.stringify({ maxRetries: 0 }),
  actionName: "记录项目成员变更最终批准连线",
  description: "最终批准顺序流选中时记录来源、目标和决策编码。",
  interfaceName: "recordProjectMemberDecisionHandler",
  methodName: "execute",
  paramsJson: JSON.stringify({ decision: "APPROVED" }),
  sortOrder: 30,
  enabled: true,
  status: "DRAFT"
};

const memberChangeGlobalAuditAction = {
  scopeType: "PROCESS",
  elementId: null,
  triggerTiming: "PROCESS_COMPLETED",
  executionMode: "AFTER_COMMIT",
  failurePolicy: "RETRY",
  retryConfig: JSON.stringify({
    maxRetries: 3,
    initialDelaySeconds: 10,
    maxDelaySeconds: 120
  }),
  actionName: "记录项目生命周期全局审计",
  description: "使用全局动作目录记录F07完成后的标准审计摘要。",
  interfaceName: "projectLifecycleAuditHandler",
  methodName: "execute",
  paramsJson: JSON.stringify({
    auditCode: "F07_MEMBER_CHANGE",
    businessStage: "MEMBER_EFFECTIVE"
  }),
  sortOrder: 50,
  enabled: true,
  status: "DRAFT"
};

buildProcessAssets({
  processKey: "project_member_change_process",
  processName: "项目成员变更审批",
  description: "项目经理、人员部门负责人、系统负责人权限、安全负责人和PMO条件审批；批准后自动生效成员、投入及角色交接。",
  entityCode: "project_member_change_request",
  fileName: "project-member-change.bpmn20.xml",
  nodes: memberChangeNodes,
  flows: memberChangeFlows,
  statusMappings: memberChangeStatusMappings,
  flowActions: [
    validationAction(
      "项目成员变更跨实体门禁",
      "校验项目状态、成员状态、跨项目投入、权限范围和角色交接。",
      "validateProjectMemberChangeHandler",
      10
    ),
    memberChangeNodeAction,
    memberChangeTransitionAction,
    applyAction(
      "批准后生效项目成员变更",
      "创建或更新项目成员，暂停、恢复或移交有效项目角色。",
      "applyProjectMemberChangeHandler",
      40
    ),
    memberChangeGlobalAuditAction,
    commonNotification(
      "发送项目成员变更结果通知",
      "PROJECT_MEMBER_CHANGE_RESULT",
      60
    )
  ],
  dependencies: [
    { type: "ENTITY", key: "project_member_change_request", required: true, reason: "实体状态映射" },
    { type: "ENTITY", key: "project_member", required: true, reason: "成员生效对象" },
    { type: "ENTITY", key: "project_role_assignment", required: true, reason: "角色暂停和移交对象" },
    { type: "FORM", key: "wf-form://project_member_change_request/project_member_change_apply", required: true, reason: "自定义审批表单" },
    { type: "USER", key: "admin", required: true, reason: "单账号模拟审批人" },
    { type: "FLOW_ACTION_HANDLER", key: "validateProjectMemberChangeHandler", required: true, reason: "跨实体成员门禁" },
    { type: "FLOW_ACTION_HANDLER", key: "captureProjectMemberManagerReviewHandler", required: true, reason: "节点完成动作" },
    { type: "FLOW_ACTION_HANDLER", key: "recordProjectMemberDecisionHandler", required: true, reason: "顺序流选中动作" },
    { type: "FLOW_ACTION_HANDLER", key: "applyProjectMemberChangeHandler", required: true, reason: "批准后生效成员变更" },
    { type: "FLOW_ACTION_HANDLER", key: "projectLifecycleAuditHandler", required: true, reason: "全局项目生命周期审计" },
    { type: "FLOW_ACTION_HANDLER", key: "sendNotificationHandler", required: true, reason: "流程完成通知" },
    { type: "UI_EXTENSION", key: "FORM:ProjectMemberChangeForm:1", required: true, reason: "自定义成员变更表单" }
  ]
});

for (const entity of [
  projectGroup,
  requirementProjectLink,
  projectMember,
  projectRoleCatalog,
  projectRoleAssignment,
  projectSystemLink,
  project,
  projectSystemChangeRequest,
  projectMemberChangeRequest
]) {
  writeJson(entityDir, `${entity.businessKey}-v1.json`, entity);
}

console.log(JSON.stringify({
  generatedEntities: 9,
  generatedProcesses: 3,
  entityDir,
  processDir,
  bpmnDir
}, null, 2));
