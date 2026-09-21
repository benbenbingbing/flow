# 前端扩展集中管理与 JSON 注册

实施日期：2026-09-21。状态：已实现。开发入口见 [前端扩展中心](../workflow-web/src/extensions/README.md)，验证结果见 [实施验收记录](frontend-extension-json-registration-verification.md)。

采用集中清单、构建发现、启动安装的方式。现有平台、通用、业务、演示实现已完整迁入 `src/extensions/`；未来模块的实现可以位于自己的 `src/modules/<module>/`，清单仍集中存放。新增现有类型只写实现和 JSON，不修改注册 JS。

## 文件夹与职责

```text
workflow-web/
├── build/extensions/                 # 构建插件：发现、校验、生成、监听
├── scripts/check-extensions.mjs       # 本地和 CI 共用检查入口
├── scripts/extensions-browser-test.mjs # 实际注册与清单增删改浏览器验收
└── src/extensions/
    ├── README.md                     # 开发步骤、类型与配置位置
    ├── index.js                      # 对外公共查询入口
    ├── register.js                   # 主应用与 Embed 唯一安装入口
    ├── manifest.js                   # 安装目录与服务端治理范围查询
    ├── manifests/                    # 唯一手工维护的注册声明
    │   ├── platform/                 # 标准字段、内置单元格
    │   ├── common/                   # 通用校验等跨业务扩展
    │   ├── business/project/         # 项目业务清单
    │   └── examples/                 # Demo 与默认禁用的契约示例
    ├── schemas/                      # 有属性说明的 JSON Schema
    ├── core/
    │   ├── installer.js              # 导出校验、实例化、幂等安装
    │   ├── adapters/                 # 十种类型的注册适配器
    │   ├── registries/               # 各类型唯一运行注册表
    │   ├── catalog.js                # 纯元数据目录，不保存第二份组件
    │   ├── catalogRows.js            # 管理页面投影与筛选
    │   └── fieldPolicy.js            # 字段默认及兼容规则
    ├── builtin/                      # 平台必需实现
    │   ├── fields/                   # 文本、数字、选择、附件、引用、子表等
    │   └── list-cells/               # 文本、状态、日期单元格
    ├── common/validators/            # 金额校验器
    ├── business/project/             # 已迁入的全部项目实现
    │   ├── forms/                    # 整表单
    │   ├── fields/                   # 字段控件
    │   ├── nodes/                    # 表单节点
    │   ├── lists/                    # 整列表
    │   ├── list-cells/               # 单元格
    │   ├── buttons/                  # 按钮组件
    │   ├── rules/                    # 操作条件编辑器
    │   ├── permissions/              # 权限候选项
    │   ├── api/                      # 该模块业务请求
    │   └── acceptanceRuntime.js      # 该模块列表动作函数
    ├── examples/                     # Demo、对象/工厂校验、配置升级示例
    ├── contracts/                    # props、事件、方法、运行边界
    ├── templates/                    # 可复制的组件模板
    ├── generated/                    # 平台字段纯数据策略，禁止手改
    └── __tests__/                    # 发现、校验、安装、目录、宿主边界测试
```

主要目录均有 README。清单按归属再按类型分类，例如 `manifests/business/project/fields/xxx.v1.extension.json`。无总清单 `index.json`，新增子目录也会自动发现。业务动作目前复用原模块的 `acceptanceRuntime.js`，新增较多动作时可自行建立 `actions/`。

## 集中的十种扩展

| 类型 | 清单分类 | 实现形态 | 在哪里使用 |
| --- | --- | --- | --- |
| FORM | forms | Vue 组件 | 实体整表单，流程通过绑定实体表单使用 |
| FIELD | fields | Vue 组件 | 表单字段控件，包含平台标准字段 |
| NODE | nodes | Vue 组件 | 自定义表单节点、摘要区块 |
| LIST | lists | Vue 组件 | 实体卡片列表、看板、整列表布局 |
| LIST_CELL | list-cells | Vue 组件 | 列表列渲染 |
| LIST_BUTTON | buttons | Vue 组件 | 工具栏或行按钮的外观和交互 |
| LIST_ACTION | actions | JS 函数 | 工具栏/行动作、选择结果处理器 |
| VALIDATOR | validators | 对象、类、同步工厂 | 字段同步或异步校验 |
| ACTION_CONDITION | rules | Vue 参数编辑器 | 按钮操作条件，需要配套后端判定 |
| PERMISSION_PROVIDER | permissions | JS 函数 | 权限候选项，需要后端授权支持 |

关联内容使用 FORM/LIST，声明 `metadata.usageContexts: ["RELATED_CONTENT"]`；普通页面默认 `PAGE`。设计器候选、版本候选与运行查找统一按宿主过滤。确实实现两套契约的组件可声明两个值。服务端尚未新增此属性的治理协议；前端阻止不匹配的组件装载。

表单按钮、联动规则、字段脚本、数据源绑定、字段/列模板仍是已有页面配置，按现有编辑器使用。普通系统页面、通用弹窗、GRID/TAB 等宿主内部布局分支不新增注册类型。

## 新增扩展的步骤

1. 阅读 `contracts/`，复制 `templates/` 中适合的模板到业务或通用模块。
2. 完成实现，保留契约的参数、事件、只读与校验要求。实现模块不调用注册 API。
3. 新增一个 `*.extension.json`，例如：

```json
{
  "schemaVersion": 1,
  "type": "FIELD",
  "name": "project_acceptance_score",
  "label": "项目·验收评分",
  "description": "编辑验收评分并显示等级",
  "version": 1,
  "implementation": {
    "path": "src/extensions/business/project/fields/ProjectAcceptanceScoreField.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "supportedFieldTypes": ["INTEGER", "LONG", "DECIMAL", "DOUBLE"],
    "configSchema": [
      { "key": "passScore", "label": "通过分数", "type": "number", "min": 0, "max": 100, "defaultValue": 60 }
    ]
  }
}
```

以上展示的是现有评分清单的核心字段，不要再创建同名副本。完整可运行文件位于 `manifests/business/project/fields/project_acceptance_score.v1.extension.json`。

4. 执行 `npm run extensions:check`、`npm run test:extensions` 和 `npm run build`。调整平台字段时先执行 `npm run extensions:generate`。
5. 部署构建，在实体表单/列表设计器中选择名称、填写实例参数、发布；流程复用已发布的实体表单。

新增代码需要重新构建部署。生产浏览器不读取任意上传 JSON，也不动态导入页面传来的模块路径。

## 协议约定

- `name` 是稳定配置引用；`schemaVersion` 是清单协议版本；`version` 是实现版本；`metadata.snapshotVersion` 是组件配置结构版本。
- `implementation.path` 相对 `workflow-web`，必须为 `src/` 内存在的 `.vue/.js/.mjs` 文件。`export` 指定 default 或具名导出。
- `kind` 为 COMPONENT/FUNCTION/OBJECT/CLASS/FACTORY。类无参构造；工厂同步接收 `{ services }` 并返回校验器实例；动作和权限函数在安装时不会执行。
- `metadata.configSchema` 定义可配置参数；每个实体的实际值仍保存在表单或列表配置。默认值变化不会重写旧实例。
- `LIST_ACTION.targets` 可同时声明 TOOLBAR 和 ROW；一份实现安装到两个动作位置。
- `ACTION_CONDITION.defaultConfig` 每次深复制；复杂默认值用 `hooks.createDefault` 引用 JS 函数。二者不能同时声明。
- `NODE.hooks.migrateConfig` 引用配置升级函数。可复制示例见 `examples/contracts/nodeConfig.js` 与对应节点清单。
- 平台 FIELD 的 `aliases/defaultForFieldTypes` 统一声明历史别名与默认控件规则；生成数据只是派生产物，不是第二个声明来源。
- `enabled` 是构建安装开关，与数据库启停不同。EXAMPLE 还受原有 Demo 开关控制；模板示例默认 `enabled: false`。
- JSON 使用 `$comment`、`description` 表达注释；Schema 提供中文属性说明与编辑器补全。构建校验负责类型组合、未知属性、路径、参数重复及注册冲突。

## 运行与治理

清单 → 校验/静态导入生成 → 安装前导出验证 → 类型适配器 → 注册表 → 目录和设计器。

主应用和 Embed 使用同一 Vite 插件及 `registerApplicationExtensions()`。清单增删改触发整页刷新，避免追加 Provider 和版本状态累积；同一安装计划重复调用无副作用。安装异常阻止启动，修复后整页重载，底层注册表不提供事务回滚。

管理页面可查看十种类型，来源分为平台、通用、业务、示例。只有服务端已支持的 FORM/LIST/NODE/FIELD 参与后台纳管；平台必需字段和其他六类显示“仅构建注册”，不显示未实现的纳管操作。数据库启停、权限和发布检查继续由现有链路执行。

FORM/LIST/NODE/VALIDATOR 延续原版本注册能力。其他类型同名仅允许一个活动版本。全宿主精确版本锁定、所有类型的后端治理不是本次迁移的承诺。

FORM/LIST 原有 `artifactDigest` 算法仍保留；显式摘要可在清单 metadata 中声明。源码路径与编译产物变化可能影响自动摘要，不能将“名称和版本保留”理解为“历史锁定摘要保持一致”。本次未连接真实业务数据库验证历史发布快照。

## 迁移结果

旧 `src/project`、`src/demo`、`src/contracts`、`components/form-fields`、`components/list-cells` 与散落的 `utils/*Registry.js` 已删除；使用方全部改为新路径，没有旧路径重导出或双注册过渡层。金额校验归入 common，项目 API/模型随业务实现迁入，示例统一归入 examples。

保留原注册名称、版本、别名、参数、Demo 开关及字段特殊解析顺序。既有组件导入不再触发内置项自动安装。所有生产注册调用集中在 core/adapters，应用只调用 register.js。

数据库迁移文件：新增无、修改无、删除无。
