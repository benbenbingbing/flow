# 前端扩展注册配置手册

仓库根目录的 `extensions/manifests/` 是唯一手工维护的注册声明入口，供 PC、移动端与共享元数据生成共用。一个 `*.extension.json` 声明一个类型、名称和版本；构建器递归发现所有子目录，不需要 index.json、import 表或业务注册函数。本手册统一覆盖 platform、common、business 和 examples 各子目录。

## 一、如何注册并使用

1. 完成 Vue 或 JS 实现，遵守 [契约](../../workflow-web/src/extensions/contracts/README.md)；可从 [模板](../../workflow-web/src/extensions/templates/README.md) 复制。
2. 按归属选择目录，在对应类型目录写 `名称.v1.extension.json`，填写稳定 name 和真实实现路径。
3. 从 `workflow-web/` 运行 `npm run extensions:check`。修改平台字段映射时先运行 `npm run extensions:generate`。
4. 从仓库根目录运行 `npm run dev:web` 或 `npm run dev:mobile`，同时监听公共包和清单；生产部署运行根目录 `npm run build`，重新部署构建产物。
5. 在实体表单、列表或按钮设计器选择/填写注册名，再填写实际参数、保存和发布。注册只让实现可用，不会自动为某个实体绑定，也不会生成业务数据。

## 二、文件夹怎么选

| 目录 | 用途与安装归属 | 实现通常放在哪里 |
| --- | --- | --- |
| platform/fields、platform/list-cells | 平台字段、内置单元格；origin=PLATFORM | extensions/builtin |
| common/validators 等 | 跨业务复用；origin=COMMON | extensions/common |
| business/<module>/fields 等 | 一个业务模块；origin=BUSINESS，module 来自目录名 | extensions/business/<module> 或 src/modules/<module> |
| examples/demo | 三个现有 Demo；origin=EXAMPLE | extensions/examples/demo |
| examples/contracts | 默认禁用的契约/模板示例；origin=EXAMPLE | extensions/templates、extensions/examples/contracts |

注册文件夹建议为 forms、fields、nodes、lists、list-cells、buttons、actions、validators、rules、permissions，只创建用到的类型。业务归属必须放在模块目录下，例如 `business/project/fields/`。实现路径可与清单目录不同。

`origin`、`module`、`sourceFile` 由构建器生成，不在 JSON 中填写。目录不是运行权限边界。

## 三、顶层配置项

“可选”表示可以省略；显式填写时仍必须符合类型和限制。

| 属性 | 必填 | 含义、默认行为和填写要求 |
| --- | --- | --- |
| `$schema` | 否，推荐 | 编辑器补全所用 Schema 路径，相对当前 JSON；不是组件路径。普通三层清单用 `../../../schemas/extension.schema.json`，如 common/validators；模块四层用 `../../../../schemas/extension.schema.json`，如 business/project/fields |
| `$comment` | 否，推荐 | 面向开发者的注释字符串，说明用途、绑定位置和特殊约束；不会执行业务逻辑 |
| `schemaVersion` | 是 | 清单协议版本，当前只能为数字 1；新增普通组件不需要提升 |
| `type` | 是 | 下表十类之一，决定安装适配器和宿主契约 |
| `name` | 是 | 业务配置引用的稳定注册名。字母开头，后续允许字母、数字、下划线、点、冒号、连字符，总长不超过 100；移动文件不要改 name |
| `label` | 是 | 非空显示名称，供支持元数据的设计器和扩展目录展示 |
| `description` | 否 | 面向使用者的用途说明；省略时按空说明处理，不替代组件逻辑 |
| `version` | 是 | 正整数实现版本，通常从 1 开始；与 schemaVersion、snapshotVersion 分开 |
| `enabled` | 否 | 省略表示启用；false 时不生成该项的静态导入、不安装。禁用清单仍检查格式和路径，不可用它隐藏失效引用 |
| `implementation` | 是 | 实现文件、导出名和加载形态，见下一节 |
| `metadata` | 否 | 参数定义、适用范围和能力描述，见第五节；省略相当于无额外元数据 |
| `targets` | 仅 LIST_ACTION 必填 | 非空数组，取 TOOLBAR、ROW，可同时填写；其他类型禁止填写 |
| `defaultConfig` | 否，仅 ACTION_CONDITION | 新建条件的默认参数对象，每次深复制；默认空对象；与 hooks.createDefault 互斥 |
| `aliases` | 否，仅平台 FIELD | 历史组件别名数组；按小写匹配，不能与其他活动字段名称/别名冲突 |
| `defaultForFieldTypes` | 否，仅平台 FIELD | 新增哪些实体字段类型时默认选本控件，例如 INTEGER、DECIMAL；同一类型只能有一个默认控件 |
| `hooks` | 否 | 可执行函数的源码引用；当前仅 createDefault、migrateConfig，见第七节 |

### type 与加载形态

| type | implementation.kind | 用途、配置位置 |
| --- | --- | --- |
| FORM | COMPONENT | 实体整表单组件；普通页面 PAGE，关联内容另声明 RELATED_CONTENT |
| FIELD | COMPONENT | 单字段控件；设计器保存注册名，参数从 field.componentProps 读取 |
| NODE | COMPONENT | 表单节点；用 nodeTypes、supportedBindings 限定宿主 |
| LIST | COMPONENT | 整列表呈现；数据、分页和动作由列表宿主传入 |
| LIST_CELL | COMPONENT | 列 renderComponent；renderConfig 作为组件 config |
| LIST_BUTTON | COMPONENT | 自定义/组件按钮；customHandler 填 name |
| LIST_ACTION | FUNCTION | 自定义/函数按钮；customHandler 或 selectionHandler 填 name |
| VALIDATOR | OBJECT / CLASS / FACTORY | 字段数据校验中的自定义规则；必须绑定规则才执行 |
| ACTION_CONDITION | COMPONENT | 按钮操作条件参数编辑器，需后端支持相同条件 type |
| PERMISSION_PROVIDER | FUNCTION | 向权限编辑器提供候选项，实际授权仍在后端 |

## 四、implementation 的三个字段

| 属性 | 必填 | 如何填写 |
| --- | --- | --- |
| `path` | 是 | 相对 workflow-web 的真实源码文件，必须以 src/ 开头，以 .vue/.js/.mjs 结尾；不支持远程地址、`..`、反斜杠或越界符号链接，也不能引用扩展注册基础设施 |
| `export` | 是 | `default` 或 JS 具名导出标识符，例如 AmountValidator；不是函数调用表达式 |
| `kind` | 是 | 按下表选择，并与 type 匹配；不能把处理函数写成工厂 |

| kind | 模块应导出什么 | 注册阶段的行为 |
| --- | --- | --- |
| COMPONENT | Vue 组件对象或函数 | 安装组件引用 |
| FUNCTION | 动作、权限提供器或钩子函数 | 保存函数引用，注册时不执行；由对应宿主在需要时调用 |
| OBJECT | 有 validate 方法的对象 | 直接安装为校验器 |
| CLASS | 无参构造的校验器类 | 执行 new Implementation()，实例必须有 validate 方法 |
| FACTORY | 同步工厂函数 | 调用 factory({services})，返回有 validate 方法的对象；不能返回 Promise |

工厂只组装实例，不请求业务接口或启动任务；每次字段参数由 validate 的 context.params 提供。需要异步校验时让 validate 返回 Promise，而不是让工厂异步。

## 五、metadata 配置项

| 属性 | 默认/限制 | 含义和使用方式 |
| --- | --- | --- |
| `snapshotVersion` | 正整数，默认 1 | 组件实例配置的结构版本；NODE 的旧配置是否调用 migrateConfig 由此判断，不是实现版本 |
| `supportedModes` | 数组，默认空 | create/edit/approve/view 中的支持模式声明；不会自动实现只读或权限控制 |
| `supportedEntityCodes` | 字符串数组，默认空 | 实体编码范围，空数组或 `["*"]` 表示不限；* 不能与其他值混用。供现有消费者筛选/校验，不是实体 ID 或授权结果 |
| `supportedFieldTypes` | 字符串数组，默认空 | 字段/校验器/单元格等消费者使用的兼容类型，例如 STRING、DECIMAL；请按实际实现填写，空范围的具体处理由宿主决定 |
| `nodeTypes` | 仅 NODE | 可用节点类型，例如 `["FIELD"]`；省略/空数组不限制此维度；运行解析时检查 |
| `supportedBindings` | 仅 NODE | 可用绑定类型，例如 `["ENTITY_FIELD"]`；省略/空数组不限制此维度；运行解析时检查 |
| `usageContexts` | 仅 FORM/LIST，非空数组 | PAGE（默认）是普通页面；RELATED_CONTENT 是关联内容。候选、选版和运行装载使用相同过滤；两套契约不同 |
| `configSchema` | 参数定义数组，默认空 | 声明设计器可填写哪些参数，见下一节；不是当前实体的参数值 |
| `capabilities` | 对象，默认空 | 实现支持的能力描述，例如 supportedEvents、supportsReadonly；允许业务能力键，不会自动产生事件、校验方法或动作 |
| `permissions` | 不重复的非空字符串数组，默认空 | 权限能力说明；仅声明不会赋权，也不能代替宿主/后端检查 |
| `artifactDigest` | 仅 FORM/LIST，可选 | 64 位小写十六进制制品摘要；未写时沿用注册表的自动摘要算法。已发布摘要锁定仍按原规则校验 |

除 capabilities 这样的开放业务对象外，不要随意增加未定义属性。新增范围或能力描述也不会自动给一个新宿主实现消费逻辑。

### configSchema 与实例值的关系

| 扩展类型 | 实例实际读取位置 |
| --- | --- |
| FIELD | field.componentProps |
| NODE | 宿主解析节点组件参数后传入 config |
| PAGE FORM / LIST | viewConfig.customComponentProps → config |
| RELATED_CONTENT FORM / LIST | 关联配置 specialHandling.customComponent.props → config |
| LIST_CELL | renderConfig → config |
| VALIDATOR | 某字段 rules[].params → context.params |

LIST_BUTTON、LIST_ACTION、ACTION_CONDITION、PERMISSION_PROVIDER 按各自契约读取上下文或条件配置；为它们写 configSchema 不会自动增加一套参数编辑/注入机制。

## 六、configSchema 中每个参数怎么写

每个数组元素描述一个参数，key 必须唯一。

| 属性 | 必填/适用 | 含义 |
| --- | --- | --- |
| `key` | 必填，非空 | 实现读取的参数键，例如 passScore；建议使用简单 JS 标识符，校验器还会校验其命名格式 |
| `label` | 必填，非空 | 参数在编辑器中的名称 |
| `type` | 必填 | text / textarea / number / boolean / select / json |
| `defaultValue` | 可选 | 新配置补默认值时使用的值；类型应与参数一致；修改默认值不会重写已保存实例 |
| `description` | 可选 | 参数用途、单位、边界及影响范围；用于文档和支持说明显示的编辑器 |
| `required` | 可选 boolean | 编辑器中的必填声明；组件或校验器仍应检查实际值，不等于服务端业务必填 |
| `options` | select 使用 | `[{"label":"显示名","value":"实际值"}]`；每项 label/value 必填，仅允许这两个键 |
| `min` / `max` | number 使用 | 数字输入的上下界；不自动约束实体数据或后端接口 |
| `rows` | textarea / json 使用 | 编辑框行数，正整数；省略时由编辑器选择默认行数 |
| `jsonShape` | json 使用 | object 或 array，限定编辑内容的结构 |
| `example` | 可选 | 示例值或结构，帮助理解；不是隐式默认值 |
| `helpKey` | 可选，非空 | 已存在的帮助文档键，供编辑器查找说明；不会自动创建帮助文档 |

例如下面是参数定义片段：

```json
{
  "key": "passScore",
  "label": "通过分数",
  "type": "number",
  "min": 0,
  "max": 100,
  "defaultValue": 60,
  "description": "组件用当前评分与此阈值比较来显示等级；每个字段/列可独立设置。"
}
```

某个表单实际设置为 80 时，保存的是 `componentProps: {"passScore":80}`，而不是把整份参数定义复制进去。

## 七、类型专属项和函数钩子

### 动作 targets

LIST_ACTION 必须写 `"targets":["TOOLBAR"]`、`["ROW"]` 或两者。工具栏处理器不保证有 row，行处理器按当前记录执行；选择回调通过 rows/selectedRows 传入所选记录，参照 [list-action 契约](../../workflow-web/src/extensions/contracts/list-action.js)。

### 条件 defaultConfig / createDefault

简单条件写 defaultConfig，例如 `{"field":"status","operator":"EQ","value":"approved"}`，每次新建都深复制，宿主补 type。需要计算默认参数时，改用 hooks.createDefault 引用函数；二者不可同时填写。函数无参返回新的参数对象，实际条件判定需要对应后端实现。

### 节点 migrateConfig

hooks.migrateConfig 仅用于 NODE。每个钩子都有必填的 path、export、kind，路径/导出规则同 implementation，kind 必须为 FUNCTION。例如已有示例使用：

```json
{
  "migrateConfig": {
    "path": "src/extensions/examples/contracts/nodeConfig.js",
    "export": "migrateSummaryConfig",
    "kind": "FUNCTION"
  }
}
```

上面是 hooks 的值。宿主传入 `{fromVersion,toVersion,config}`；钩子返回新配置，不改旧快照、不请求业务接口。配套 `metadata.snapshotVersion=2` 的完整清单见 [ContractExampleNode](examples/contracts/nodes/ContractExampleNode.v1.extension.json)。

### 平台 FIELD 的 aliases / defaultForFieldTypes

aliases 描述同一实现的旧组件名；defaultForFieldTypes 描述新增实体字段时选哪个控件；supportedFieldTypes 描述兼容范围，三者不可混用。只有 platform 的 FIELD 可声明前两项，且不能冲突。变更后运行 extensions:generate 更新派生文件。

## 八、已有 JSON 怎么参考和使用

| 需求 | 直接参考的 JSON | 绑定或试用方式 |
| --- | --- | --- |
| 自定义字段及参数 | [项目评分字段](business/project/fields/project_acceptance_score.v1.extension.json) | 数值字段选 project_acceptance_score，配置 passScore |
| 整表单 | [成员变更表单](business/project/forms/ProjectMemberChangeForm.v1.extension.json) | 实体表单选择 ProjectMemberChangeForm，需要项目业务字段/接口 |
| 整列表 | [项目看板](business/project/lists/ProjectAcceptanceBoardList.v1.extension.json) | 列表组件选择 ProjectAcceptanceBoardList，配置搜索提示等 |
| 单元格 | [风险进度 Demo](examples/demo/list-cells/DemoRiskProgressCell.v1.extension.json) | 启用 Demo 后在数值列选择 DemoRiskProgressCell |
| 按钮组件 | [验收按钮](business/project/buttons/ProjectAcceptanceInspectButton.v1.extension.json) | customMode=component，customHandler=ProjectAcceptanceInspectButton |
| 双位置函数动作 | [选择回调](business/project/actions/projectAcceptanceSelectionAction.v1.extension.json) | 工具栏/行处理器或 selectionHandler 填该 name |
| 类校验器 | [amount](common/validators/amount.v1.extension.json) | 字段绑定 amount@1，params.maxAmount 配当前额度 |
| 对象校验器 | [必填示例](examples/contracts/validators/contract_required.v1.extension.json) | 启用此清单和 Demo，再绑定 contract_required@1 |
| 同步工厂 | [文本长度示例](examples/contracts/validators/contract_text_length.v1.extension.json) | 启用后绑定规则，设置 maxLength |
| 条件编辑器 | [项目条件](business/project/rules/PROJECT-CUSTOM_CONDITION.v1.extension.json) | 操作条件选 PROJECT:CUSTOM_CONDITION，并保证后端支持 |
| 权限候选提供器 | [项目权限](business/project/permissions/projectPermissionOptions.v1.extension.json) | 权限编辑器按当前实体自动查询候选；不会授予权限 |
| 关联内容 | [关联表单模板](examples/contracts/forms/ContractExampleRelatedForm.v1.extension.json) | 启用后在关联内容自定义呈现中选择 |
| 平台字段别名 | [number](platform/fields/number.v1.extension.json) | 平台维护者查看别名、兼容类型和默认映射 |

复制已有清单注册新扩展时，修改 name、label、implementation、范围与参数；不要留下原名，否则与现有启用项冲突。示例转正式业务时，把实现及清单复制到 business/<module> 或 common，设置 enabled=true，即不再依赖 Demo。

## 九、启用、版本和管理边界

- 普通清单：enabled 省略或为 true 即参与安装。
- examples 清单：还需要应用的 enableDemo=true。现有主应用和 Embed 在 `npm run dev` 时默认开启；生产演示构建使用 `VITE_ENABLE_DEMO_EXTENSIONS=true npm run build`。contracts 示例需另把对应清单 enabled 改为 true。
- enabled=false 影响构建安装；数据库目录启停、表单/列表发布和引用检查是另一条链路，JSON 不绕过它们。
- FORM/LIST/NODE/VALIDATOR 支持同名不同版本并存；其他类型同名只允许一个活动版本。字段名按小写、条件名按大写判冲突，别名也检查冲突。
- 管理页可查看十类。服务端目前纳管 FORM/LIST/NODE/FIELD；平台必需字段和其他类型显示“仅构建注册”，没有凭空增加后端纳管接口。
- 删除或禁用仍被发布配置引用的实现会导致运行缺失。名称/版本不变也不代表自动制品摘要保持不变，摘要锁定按原有协议检查。

## 十、JSON 注释怎么写、报错怎么查

标准 JSON 不支持 // 或块注释。这里使用顶层 `$comment` 说明本文件，`description` 说明扩展用途，`metadata.configSchema[].description` 说明每个参数。不要增加 `_comment`，也不要在 implementation 或 metadata 中插入未被 Schema 接受的注释键。

当前文件已经补充这两类注释。复制时同步更新文字；不能写“支持只读”却不在实现中遵守 disabled/readonly。

| 报错/现象 | 检查位置 |
| --- | --- |
| 未知属性、类型不合法 | 对照本手册及 schemas/extension.schema.json，不要用字符串代替数字/布尔值 |
| 实现路径不存在 | path 从 workflow-web/src 起写；不要相对清单目录拼实现路径 |
| 模块没有导出 | export 是否与 default/具名导出一致；运行 build 验证 |
| 冲突 | 检查同 type/name/version、字段别名、默认字段映射；不要靠安装顺序覆盖 |
| 组件在设计器不可见 | 检查 enabled、Demo、实体/字段兼容范围、usageContexts 以及页面纳管要求 |
| 参数显示但不起作用 | 实现是否从正确位置读取；Schema 只定义编辑参数，不实现逻辑 |
| 字段策略过期 | 修改源 JSON 后运行 extensions:generate，再运行 extensions:check |

常规验证命令从 workflow-web 执行：`npm run extensions:check`、`npm run test:extensions`、`npm run build`；验收开发监听时使用 `npm run test:extensions:browser`。
