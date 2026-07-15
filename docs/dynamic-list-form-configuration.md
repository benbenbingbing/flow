# 动态列表与表单配置架构

## 目标

- 常规列表、查询、列展示、表单布局、字段校验和模式权限通过配置完成。
- 高定制需求通过显式注册的后端提供者或前端组件实现。
- 配置不允许执行 JavaScript、Groovy 或自由 SQL。
- 配置设计、运行时渲染和服务端校验使用同一份扩展元数据。

## 分层模型

### 列表

1. 实体字段：`ENTITY_FIELD`。
2. 声明式虚拟列：例如 `FIELD_TEMPLATE`。
3. 自定义数据提供者：`ListFieldDataProvider`。
4. 自定义单元格：`registerCellComponent`。
5. 整页自定义列表：`registerCustomListComponent`。

### 表单

1. 内置字段组件与结构化配置。
2. 自定义字段组件：`registerFormFieldComponent`。
3. 整表单组件：`registerCustomFormComponent`。

## 配置存储

- `entity_list_config.view_config`
  - 查询区域、表格、分页、自定义列表组件参数。
- `entity_list_field.column_config`
  - 固定位置、最小宽度、溢出提示。
- `entity_list_field.query_config`
  - 查询组件、占位提示、默认值。
- `entity_list_field.render_config`
  - 单元格组件参数。
- `entity_form.view_config`
  - 标签宽度、自定义表单组件参数。
- `entity_form_field.validation_rules`
  - 长度、数值范围、预设格式。
- `entity_form_field.extension_config`
  - 新增、编辑、审批、查看四种模式的显隐和编辑权限。

公共、高频、需要索引或查询的属性继续使用结构化列；低频扩展属性使用受校验的 JSON。

## 虚拟查询

列表服务先根据字段配置拆分条件：

- 实体字段条件进入动态 SQL。
- 扩展字段条件不会进入 SQL。
- 扩展提供者补充值后，由 `ListFieldConditionEvaluator` 使用白名单操作符过滤。

数据权限在扩展计算前已执行。扩展查询的数据源未注册时拒绝查询，不能退化为未过滤结果。

## 扩展元数据

前后端扩展都声明：

- 稳定编码。
- 管理员可见名称和说明。
- 是否支持虚拟字段、查询或特定运行模式。
- `configSchema`。
- 可选能力描述。

设计器依据元数据生成配置界面；保存时后端再次校验。

## 兼容策略

- 原注册方法签名继续可用，新增 metadata 参数为可选。
- 原列表和表单配置字段继续保留。
- 未配置高级 JSON 时使用默认值。
- 历史未注册展示型数据源可以继续打开列表，但不能用于查询；再次保存时必须选择有效提供者。
- 自定义组件未注册时回退默认动态渲染。

## 安全约束

- 扩展编码使用安全标识符。
- JSON 限制长度、数组数量、对象数量和最大深度。
- 拒绝 `__proto__`、`prototype`、`constructor` 等危险键。
- 列表扩展失败不允许绕过数据过滤。
- 前端能力只用于展示，编辑、删除、审批仍由后端重新校验。

## 验收范围

- 实体列、隐藏查询列、虚拟展示列、虚拟查询列。
- 查询操作符、空值、数值范围和 IN。
- 列固定、宽度、溢出、表格尺寸、分页配置。
- 新增、编辑、审批、查看四种字段模式。
- 结构化校验、自定义字段组件、整表单组件。
- 未注册扩展、非法 JSON、重复编码、错误模式和错误类型。
