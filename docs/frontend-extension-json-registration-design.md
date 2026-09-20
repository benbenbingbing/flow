# 前端扩展集中管理与 JSON 自动注册设计

设计日期：2026-09-20。状态：设计方案，尚未实施。

依据当前工作区代码（含未提交修改）设计。目标是统一前端扩展的声明、发现、注册与查询：新增已有类型的扩展时，开发者只需完成实现并新增一个 JSON 文件；实现可以留在所属业务模块。新增一种运行时扩展类型仍需平台提供相应适配器和宿主支持。

## 设计结论

采用“集中 JSON 清单 + 分布式实现 + 构建时发现 + 启动时统一注册”。

- 注册清单全部放在 `src/extensions/manifests/`，按平台内置、通用、业务、示例分组。
- 注册机制、注册表、契约和模板归入 `src/extensions/`。已有业务实现继续放在 `src/project/`，未来模块可使用 `src/modules/<module>/`。
- 平台内置和通用扩展实现分别归入 `src/extensions/builtin/`、`src/extensions/common/`。
- JSON 描述注册名、类型、实现文件、导出方式、配置 Schema 和适用范围。函数、Vue 组件及业务逻辑保留在代码文件中。
- 构建工具读取清单并生成明确的静态 import；浏览器使用生成结果完成注册。新增实现需要重新构建部署，配置界面只管理已安装实现的使用方式。
- 主应用和 Embed 使用相同的清单编译及注册入口，沿用 Embed 原有运行限制。

## 目录安排

以下为目标结构；空分类不必预建。先统一机制和清单，再按迁移顺序移动原文件。

```text
workflow-web/
├── build/extensions/
│   ├── discover.mjs                 # 递归发现 *.extension.json
│   ├── validate.mjs                 # 清单结构、文件路径、名称与版本冲突检查
│   ├── generate.mjs                 # 生成静态导入及标准描述
│   └── vite-plugin.mjs              # 开发/构建共用，清单变化刷新目录
├── scripts/
│   └── check-extensions.mjs         # CI/本地检查入口，复用 build/extensions
└── src/
    ├── extensions/
    │   ├── index.js                # 公共入口
    │   ├── register.js             # 应用唯一安装入口
    │   ├── manifest.js             # 兼容现有目录调用的导出
    │   ├── core/
    │   │   ├── installer.js        # 初始化、类型检查、幂等安装
    │   │   ├── catalog.js          # 统一查询元数据和安装状态
    │   │   ├── adapters/           # 按类型调用注册 API
    │   │   └── registries/         # 迁入现有注册表，保留各类型契约
    │   ├── schemas/
    │   │   └── extension.schema.json
    │   ├── manifests/
    │   │   ├── platform/           # 平台必需：标准字段、内置单元格等
    │   │   ├── common/             # 跨业务复用的可选扩展
    │   │   ├── business/
    │   │   │   └── project/        # 项目模块清单，内部按类型分组
    │   │   └── examples/           # 演示扩展，由现有 Demo 开关控制
    │   ├── builtin/
    │   │   ├── fields/             # 文本、数字、选择、附件、实体引用、子表等
    │   │   └── list-cells/         # DefaultText、StatusBadge、DateFormatter
    │   ├── common/                 # 已实现的通用扩展，按下面的类型目录存放
    │   ├── examples/               # 已有 demo 实现
    │   ├── contracts/              # 从 src/contracts 迁入契约和运行帮助入口
    │   ├── templates/              # 开发模板，不参与自动注册
    │   └── __tests__/              # 注册、目录、兼容性验证
    ├── project/                    # 现有业务实现可以保持原位
    │   ├── forms/
    │   ├── fields/
    │   ├── nodes/
    │   ├── lists/
    │   ├── list-cells/
    │   ├── buttons/
    │   ├── actions/
    │   ├── validators/
    │   ├── rules/
    │   └── permissions/
    └── modules/<module>/           # 后续业务模块可采用同样分类
```

`manifests/platform/`、`manifests/common/`、`manifests/business/project/`、`manifests/examples/` 内均按类型使用 `forms/`、`fields/` 等子目录。推荐一个扩展身份和版本一个文件，例如 `fields/acceptance-score.v1.extension.json`。不维护需要人工追加的总清单 `index.json`。

清单目录表达维护归属，实现路径表达代码归属，两者无须一致。例如通用校验器迁移初期可以仍引用 `src/project/validators/AmountValidator.js`，待移入 `common/validators/` 后仅更新清单路径，保留注册名和业务绑定。

## 纳入统一管理的十种类型

| 类型 | 分类目录 | 实现形态 | 现有接入方式及配置用途 |
| --- | --- | --- | --- |
| `FORM` | `forms/` | Vue 组件 | `registerCustomFormComponent`；实体整表单，流程通过实体表单复用 |
| `FIELD` | `fields/` | Vue 组件 | `registerFormFieldComponent`；单字段控件，包括平台内置字段 |
| `NODE` | `nodes/` | Vue 组件 | `registerFormNodeComponent`；表单节点扩展、跨字段摘要和特殊区块 |
| `LIST` | `lists/` | Vue 组件 | `registerCustomListComponent`；看板、卡片或整列表 |
| `LIST_CELL` | `list-cells/` | Vue 组件 | `registerCellComponent`；单列渲染，包括平台内置单元格 |
| `LIST_BUTTON` | `buttons/` | Vue 组件 | `registerListButtonComponent`；列表工具栏或行按钮组件 |
| `LIST_ACTION` | `actions/` | JS 函数 | 工具栏和行动作注册表；点击处理、列表选择结果回调 |
| `VALIDATOR` | `validators/` | 对象、类或工厂 | `registerCustomValidator`；字段同步/异步、跨字段校验 |
| `ACTION_CONDITION` | `rules/` | Vue 编辑器及默认配置 | `registerEntityActionRuleCondition`；操作条件编辑，需匹配后端判定 |
| `PERMISSION_PROVIDER` | `permissions/` | JS 函数 | `registerEntityPermissionOptionProvider`；提供权限候选项，需后端授权支持 |

统一类型使用的是清单分类；不修改已有业务配置中的组件名、处理器名和条件类型。例如 `ACTION_CONDITION` 是清单类型，`PROJECT:CUSTOM_CONDITION` 仍是该条件的运行时名称。

关联内容沿用 `FORM`/`LIST`，通过 `metadata.usageContexts` 区分 `PAGE` 与 `RELATED_CONTENT`，使用不同注册名。两种宿主的 props/runtime 不相同；注册适配器、设计器筛选和发布校验必须共同识别这个属性，不能只增加元数据标签。

表单按钮、字段脚本、联动规则、数据源绑定、字段模板和列模板继续属于实体/表单/列表配置。可在统一文档中索引，但不额外发明前端注册类型。新建模板文件本身不会自动安装成扩展。

普通系统页面、布局、通用弹窗，以及表单渲染器内部的布局分支，不因它们是 Vue 文件就自动成为扩展。内置 GRID/TAB 等节点若要完全组件化，需单独拆解宿主渲染逻辑；本次 JSON 注册设计不改变其行为。

## JSON 协议

一个字段扩展的完整示例，放入 `manifests/business/project/fields/acceptance-score.v1.extension.json`：

```json
{
  "schemaVersion": 1,
  "type": "FIELD",
  "name": "project_acceptance_score",
  "label": "项目·验收评分",
  "description": "使用评分滑块展示及编辑验收分数",
  "version": 1,
  "enabled": true,
  "implementation": {
    "path": "src/project/fields/ProjectAcceptanceScoreField.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "snapshotVersion": 1,
    "supportedEntityCodes": ["*"],
    "supportedFieldTypes": ["INTEGER", "LONG", "DECIMAL", "DOUBLE"],
    "configSchema": [
      {
        "key": "passScore",
        "label": "通过分数",
        "type": "number",
        "min": 0,
        "max": 100,
        "defaultValue": 60
      }
    ],
    "capabilities": {
      "supportedEvents": ["FIELD_BUTTON_CLICK"],
      "supportsReadonly": true
    }
  }
}
```

协议约定：

- `schemaVersion` 是清单协议版本；`version` 是实现版本；`snapshotVersion` 是配置结构版本，三者分开管理。
- `name` 是持久化的稳定注册名。迁移现有实现时保持大小写、命名和版本不变。
- `implementation.path` 相对 `workflow-web/`，仅允许工程 `src/` 内明确存在的 `.vue`、`.js`、`.mjs` 文件；不读取服务器路径、远程 URL 或由页面配置传入的模块路径。
- `export` 支持 default 或具名导出；导出缺失必须在构建或安装验证中明确报错。
- `kind` 必须显式区分 `COMPONENT`、`FUNCTION`、`OBJECT`、`CLASS`、`FACTORY`。函数处理器在注册时不可被误执行；`CLASS` 使用无参构造，需依赖注入时使用显式同步 `FACTORY`。工厂只组装实例，不发请求或启动业务操作。
- 归属由 `manifests/` 下的分组推导：`PLATFORM`、`COMMON`、`BUSINESS`、`EXAMPLE`；业务模块由路径推导。页面显示归属不再依赖实现文件名猜测。
- `enabled` 表示当前构建是否安装。它不同于数据库目录的启停状态，也不能替代权限检查；不得移除仍被历史发布配置引用的实现版本。
- `metadata` 使用按类型约束的 Schema，未知属性报错，避免错拼字段被静默忽略。
- `configSchema` 是组件可配置参数的定义；某个表单实际使用的 `passScore` 等值仍保存在该表单中。修改默认值不隐式改写历史实例配置。
- `supportedEntityCodes` 默认沿用现有全部实体语义，实际适用范围按组件需要收窄；凡新增范围控制，必须在选择器与运行时一起落实。
- `capabilities` 只描述能力，不会替组件实现事件、校验方法或权限。

校验器类示例，复用当前实现和注册名：

```json
{
  "schemaVersion": 1,
  "type": "VALIDATOR",
  "name": "amount",
  "label": "金额校验",
  "version": 1,
  "implementation": {
    "path": "src/project/validators/AmountValidator.js",
    "export": "AmountValidator",
    "kind": "CLASS"
  },
  "metadata": {
    "supportedEntityCodes": ["*"],
    "supportedFieldTypes": ["INTEGER", "LONG", "DECIMAL", "DOUBLE", "NUMBER", "STRING"],
    "configSchema": [
      { "key": "maxAmount", "label": "金额上限", "type": "number", "required": true, "min": 0, "defaultValue": 1000 }
    ]
  }
}
```

非组件扩展的特殊参数使用各自的类型 Schema：

| 类型 | 额外声明 | 适配行为 |
| --- | --- | --- |
| `LIST_ACTION` | `targets: ["TOOLBAR", "ROW"]` | 一份函数按目标注册到两类已有动作表；选择回调也引用该名称 |
| `LIST_BUTTON` | 支持的按钮位置元数据 | 安装组件；页面继续传递当前行、禁用态和运行上下文 |
| `ACTION_CONDITION` | `defaultConfig` | 每次编辑新条件时深复制默认配置，生成原 `createDefault()` 行为 |
| `ACTION_CONDITION` / `NODE` | 可选 `hooks.createDefault` / `hooks.migrateConfig` | 钩子采用同样的 path/export 引用，按既有契约调用，不在 JSON 中保存 JS 字符串 |
| `FIELD` 平台项 | `aliases`、`defaultForFieldTypes` | 集中生成旧别名映射和字段默认组件策略；别名解析与字段类型默认值分开处理 |

不能把参数 Schema、实体范围等新元数据只写入清单，却让现有注册函数丢弃。类型适配器明确转换旧字段，统一目录保留完整描述；适用范围由需要的宿主实际校验。

## 自动注册流程

```text
源码中的 *.extension.json
  → 构建插件递归发现
  → 校验协议、路径、冲突与部署分组
  → 生成 virtual:flow-extension-manifest（静态 import + 清单）
  → 启动前校验导出形态、钩子及工厂结果
  → 类型适配器安装到对应注册表
  → 统一目录供设计器与扩展管理查询
  → 实体/流程配置按原注册名使用
```

采用生成静态导入的虚拟模块，工程中不需人工维护 componentMap 或每个新模块的 import。构建插件同时加入 `vite.config.js` 与 `vite.embed.config.js`，提取一份共享插件配置。

Vite 官方支持通过插件生成虚拟模块；直接 `import.meta.glob()` 的匹配参数必须是字面量，不能在浏览器中读取 JSON 后把路径数组传入它。因此这里选择构建阶段读取 JSON 并生成 import，确保具体实现可分散在工程各模块。参考：[Vite 虚拟模块](https://vite.dev/guide/api-plugin.html#virtual-modules-convention)、[Glob 导入限制](https://vite.dev/guide/features.html#glob-import-caveats)。

一期维持现有同步注册模式：在页面查询注册表和应用挂载前完成安装；不同时引入所有控件懒加载。复杂组件需要缩小首屏体积时，在实现内部拆分昂贵依赖。

安装器按标准身份排序，先验证完整安装计划，再调用适配器。注册过程不得读写业务数据。失败时阻止应用继续挂载并显示文件、类型和名称；一期不承诺旧注册表的事务回滚，失败后通过整页重载重新初始化。

清单增加、修改、删除时，开发插件重新生成虚拟模块并触发整页刷新，避免权限 Provider 追加注册、全局 Symbol 字段表及版本表的重复安装问题。普通 Vue 模板修改仍保留正常 HMR；禁止把“忽略重复注册”作为清单热更新策略。

新增现有类型的实现后，日常步骤为：写实现 → 写一个 JSON → 检查/构建 → 部署 → 在设计器选择、配置及发布。无需改 `main.js`、注册入口或业务模块 index。

## 统一目录与数据库配置的关系

- 代码清单负责“本构建安装了什么”，注册表负责“怎样找到可执行实现”。
- 统一目录是注册描述与安装状态的查询投影，不新建另一份保存组件的独立注册表。
- 数据库仍负责目录启停、表单/列表实际绑定、发布快照与引用关系；现有的目录未纳管、禁用、缺失状态不能因 JSON 存在而跳过。
- 先让统一目录展示所有十类；服务端目前只统一治理四类 UI 扩展，其他类型标记为“仅构建注册”，不能直接假装已有发布/停用 API。
- 如果后续需要所有类型都支持后台治理，另行扩展后端类型、权限、发布校验及迁移协议，不能只修改前端枚举。
- 示例通过现有 `enableDemo`/环境开关安装。保留原开关语义；若以后要让生产包连示例代码都不包含，再增加构建分组裁剪。
- 主应用和 Embed 安装同一套受支持实现。组件运行权限、请求通道和脚本禁用仍由各自宿主约束。

## 迁移现有代码

| 现有位置 | 目标处理 |
| --- | --- |
| `src/project/index.js` | 将 import + register 调用转成 business/project 清单；实现保留原位，现有常量导出暂保留兼容 |
| `src/project/validators/index.js` | amount 转为 JSON；若确认规则完全通用，可归入 common，注册名和参数不变 |
| `src/demo/index.js` | 转为 examples 清单，保留现有 Demo 环境开关 |
| `src/utils/*Registry.js` | 迁入 extensions/core/registries；旧路径只 re-export，避免生成第二份 Map |
| `src/contracts/validator-registry.js` | 迁入同一注册表目录，旧路径 re-export 同一实例 |
| `src/components/form-fields/index.js` | 将内置描述、别名及默认组件声明转到 platform 清单；渲染解析器保留行为和兼容导出 |
| `src/utils/listCellRegistry.js` | 移除导入时自动安装的三个内置单元格，由 platform 清单安装 |
| `src/contracts/` | 契约移到 extensions/contracts，模板移到 extensions/templates，旧导入路径暂兼容 |
| `src/extensions/manifest.js` | 改为统一目录投影，同时保留现有调用方需要的函数签名 |

内置字段需要重点保留：文本/多行文本的默认差异、USER/DEPT 等别名、实体引用特殊处理、子表单/子列表优先解析、组件直接导出及字段类型兼容规则。不能只把它们扔进自定义 Map 就认为迁移完成。

注册表模块只提供注册/查找，不 import 自动安装入口；实施时检查“清单 → 组件 → 契约/注册表 → 清单”的循环依赖。工厂和元数据查询也不得在模块导入阶段触发安装。

## 版本及兼容策略

统一清单不等于所有旧类型立即获得多版本运行能力。

- `FORM`、`LIST`、`NODE`、`VALIDATOR`：沿用现有版本能力，迁移时不改变已发布注册名、版本和配置结构。FORM/LIST 的既有摘要行为保持兼容；不因文件搬家或新生成器任意重算已发布身份。
- `FIELD`、`LIST_CELL`、`LIST_ACTION`、`LIST_BUTTON`、`ACTION_CONDITION`、`PERMISSION_PROVIDER`：初期仍按现有单版本/追加式 API 安装，同一运行时名称不能配置多个活动版本；检查器必须拒绝静默覆盖。
- 旧代码中各宿主是否把版本传入查找函数并不完全一致。要承诺全部宿主精确锁定实现，必须另行补齐配置存储、发布快照和运行查找链路。
- 名称冲突检查按最终注册规则进行，例如字段名和条件类型的大小写归一、字段别名及动作 targets。业务扩展不能依靠加载顺序覆盖平台项。
- 后续保留的旧注册入口只是兼容层；应用正常启动只有 JSON 安装器一个入口，同一实现不能同时执行旧 index 注册和清单注册。

## 实施顺序与验收

1. 建立十种类型的清单 Schema、发现器、静态导入生成器、安装适配器和统一查询目录。
2. 将已有 project、validators、demo 注册声明转为 JSON；保持实现、运行时契约和数据库绑定不变。
3. 将标准字段和内置单元格纳入 platform 清单，处理旧别名、默认映射与导入副作用。
4. 归拢注册表、契约、模板及通用实现，保留旧路径导出；统一管理页和设计器逐步改用目录查询。
5. 扩展到后端全类型治理或统一多版本运行时作为独立后续变更，不夹带在文件迁移中。

验收覆盖：

- 新增组件和 JSON 后无需修改任何注册 JS，主应用与 Embed 均可使用；清单新增/删除在开发服务器中生效。
- 组件默认/具名导出、处理函数、校验器对象/类/工厂、条件默认配置、迁移钩子均正确安装。
- 缺失模块、导出错误、无效参数 Schema、别名冲突、重复身份和未支持的多版本会给出可定位错误。
- Demo 开关、重复初始化及清单刷新不造成重复权限候选或版本冲突。
- 现有字段、列表、按钮、关联内容、校验器以及流程办理表单保持原行为，发布历史仍能解析。
- 参数 Schema 与名称通过静态检查；实际组件及导出通过 admin/embed 构建和契约测试验证。

本次交付只有设计文档。数据库迁移文件：新增无、修改无、删除无。
