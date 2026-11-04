# 数据选择器：自定义接口选数与回填

`data_picker` 用于从自定义接口选择一条记录，实体属性保存稳定的字符串业务键。PC 使用表格弹窗，移动端使用底部面板和记录卡片；两端共用字段契约、分页、回显、确认与回填逻辑。

适用于 ERP 客户编码、物料编码等外部业务键。组件支持 `STRING`、`SELECT` 实体属性，推荐为外部编码使用 `STRING`。已有平台实体引用继续使用 `reference` 与其选择列表，不把外部键登记成平台实体关系。

## 配置步骤

1. 在接口扩展中注册三个 `FORM` 上下文、`READ` 类型的接口，分别返回字段描述、分页记录、按键解析记录。可以使用同一个 `UiDataSourceProvider`，通过 `context.usage()` 分派。
2. 在表单设计器中，为实体属性选择“数据选择器”。
3. 在字段的“扩展 → 数据源绑定”中配置“选择器字段描述”“选择器分页查询”“选择器按键解析”。三个接口都要绑定在这个字段上；输入映射分别配置，输出映射留空。
4. 保存当前字段，再在“数据选择器与回填”中点击“获取字段描述”。若描述接口有业务参数，可以填写测试表单数据；预览使用已保存草稿的绑定权限。
5. 配置来源字段、当前表单目标属性、覆盖和空值策略，再次保存字段并发布表单。

组件基础参数支持面板标题、每页条数（1～200）、清空选择时是否处理回填字段。配置保存在现有节点 JSON 与表单发布快照中，不需要数据库迁移。

## 接口契约

三种用途均复用 `/api/ui-runtime/extensions/execute`，接口 ID、表单和字段位置由当前发布绑定校验。Provider 依赖当前已认证上下文和数据范围计划执行查询，业务参数不代表认证身份。

| usage | 输入协议 | 返回值 |
| --- | --- | --- |
| `FIELD_PICKER_SCHEMA` | `input.picker = {}` | 字段描述对象 |
| `FIELD_PICKER_QUERY` | `input.picker = {pageNum, pageSize, keyword}` | `{records, total}` |
| `FIELD_PICKER_RESOLVE` | `input.picker = {values: ["C001"]}` | 记录数组 |

额外业务参数通过每条绑定的输入映射传入，例如：

```json
{
  "companyCode": "data.company_code"
}
```

`data` 是当前表单或当前子表行的数据，其他映射来源沿用已有数据源运行时的 `context`、`input`、`parent`、`params`、`row`。`picker` 是组件生成的协议对象，输入映射不会覆盖它。查询页码从 1 开始，每页最多 200 条；解析支持最多 200 个不重复字符串键。当前单选组件每次解析一个键。

### 字段描述

```json
{
  "schemaVersion": "1",
  "valueField": "customerCode",
  "labelField": "customerName",
  "fields": [
    {"code": "customerCode", "label": "客户编码", "type": "STRING"},
    {"code": "customerName", "label": "客户名称", "type": "STRING"},
    {"code": "contact", "label": "联系人", "type": "STRING"},
    {"code": "phone", "label": "联系电话", "type": "STRING"}
  ],
  "columns": [
    {"field": "customerCode", "width": 150},
    {"field": "customerName", "minWidth": 200}
  ]
}
```

`fields` 定义可读取、回填的字段，`columns` 定义显示的子集，字段可以不出现在表格中。支持 `STRING/TEXT/INTEGER/LONG/DECIMAL/DOUBLE/NUMBER/BOOLEAN/DATE/DATETIME`；日期使用字符串，数值使用 JSON number，布尔使用 JSON boolean。字段编码必须为普通标识符，不能用嵌套路径或原型链键。

主值字段必须为 `STRING`，业务键保持原始值，例如 `"001"` 不会转为 `1`。同一数据源范围内的主值必须唯一。业务范围变化时，不同范围里的同名键不能相互解析。

字段描述接口的输出 Schema 至少声明以下属性类型：

```json
{
  "type": "object",
  "properties": {
    "schemaVersion": {"type": "string"},
    "valueField": {"type": "string"},
    "labelField": {"type": "string"},
    "fields": {"type": "array", "items": {"type": "object"}},
    "columns": {"type": "array", "items": {"type": "object"}}
  }
}
```

获取到的字段描述随表单发布。运行时请求描述并检查版本、主值、显示字段、字段编码和类型；结构变化会停止加载，要求重新获取字段并发布。标题、列宽可动态调整。

### 分页与按键解析

分页返回：

```json
{
  "records": [
    {"customerCode": "C001", "customerName": "华东客户", "contact": "张三", "phone": "13800000000"}
  ],
  "total": 1
}
```

查询输出 Schema 根类型为 `object`，必须声明 `records: array`、`total: integer`。`records` 不能超过本次 pageSize，total 是相同筛选与权限范围下的记录总数。

解析接口返回与 records 相同形状的数组，输出 Schema 根类型为 `array`。它必须按业务键查询，不能复用只查第一页的逻辑。删除或无权访问的键不返回记录；历史停用记录可以返回 `disabled: true`，用于显示原名称，但不能再确认选择。

接口返回值中的未声明业务字段不会进入回填上下文。权限检查必须在服务端 Provider 中完成，展示列本身不构成字段权限。

## 回填与更新行为

回填规则保存在字段 `componentProps.pickerMappings`，使用现有 `FIELD_MAPPING` 的覆盖策略执行器：

```json
{
  "sourcePath": "selection.data.contact",
  "targetPath": "form.contact_name",
  "overwrite": "IF_EMPTY",
  "clearOnEmpty": false
}
```

- `ALWAYS`：始终覆盖；`IF_EMPTY`：目标为空才覆盖；`CONFIRM`：覆盖非空旧值前确认。
- `clearOnEmpty=false`：来源为空时保留目标；`true` 则参与清空处理。数值 `0` 和布尔 `false` 不视为空。
- 清空选择默认只清空当前属性。开启 `pickerClearOnClear` 后，按各映射的覆盖与空值策略处理其它字段；不会自动识别某个值是否曾由用户手工编辑。
- 回填只写当前表单/子表行可见且可编辑的属性，遵守宿主的字段状态限制。目标不能是当前选择属性、重复目标或结构节点。

面板选择草稿与表单数据独立。确认时重新调用解析接口，使用最新记录生成回填草稿；覆盖确认完成后，主值与回填字段通过一次 `patch-form` 进入宿主更新、联动、事件与校验链。取消面板、解析失败或会话过期都不应用补丁。

编辑/查看时只解析并回显名称，不重新回填已保存字段。保存的键不可访问时保留原值并显示原因，允许用户重新选择或清空。切换记录、查询参数或表单版本时关闭旧草稿，迟到响应不能写入新上下文。

回填是表单交互能力。价格、额度等会影响服务端业务结果的值，应在提交处理里按业务键重新查询、校验或计算。此组件不会建立外部数据的数据库外键，也不自动保存整条接口记录。

首期只支持单选；多选聚合、子表行生成和自定义筛选条件编辑器不在本次范围内，业务筛选参数可通过输入映射传递。

## 验证入口

- 共享行为：`node --test packages/workflow-core/tests/data-picker.test.mjs`。
- PC/移动端真实面板：在 `workflow-web` 下运行 `node scripts/data-picker-browser-test.mjs`，使用本机 Chrome 与临时内存接口，不读写业务库。
- 后端发布及执行边界：`DataPickerConfigurationPolicyTest`、`DataPickerBoundInterfaceExecutionTest`。

迁移文件：无新增、修改或删除。
