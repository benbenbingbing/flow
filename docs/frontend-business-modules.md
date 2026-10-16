# 前端业务模块开发

产品提供通用表单、列表、流程和接入接口；业务包维护专属规则、API、PC 和移动组件；
应用从清单装配选中的业务。PC 和移动端共同使用的业务规则仍属于业务包，不放入产品核心。

## 目录和命名

前端工作区根目录为 `workflow-frontend/`。除明确标注仓库根的启动/容器命令外，
本文路径与 npm 命令均以该目录为基准；仓库的 `docs/`、`deploy/`、`start.sh` 留在外层。

```text
flow/workflow-frontend/
├── config/frontend-modules.json           # 默认启用的业务模块
├── extensions/
│   ├── manifests/
│   │   ├── platform/                     # 产品内置声明
│   │   ├── common/                       # 跨业务通用声明
│   │   ├── examples/                     # 产品示例声明
│   │   └── biz-project/                  # 项目业务声明
│   └── schemas/                          # 清单格式规范
├── packages/
│   ├── workflow-core/                    # 通用规则、校验、表单与流程运行逻辑
│   ├── workflow-api/                     # 通用实体、流程、文件 API
│   ├── workflow-extension-sdk/           # 对业务公开的契约和宿主注入接口
│   ├── workflow-mobile-ui/               # 通用移动组件
│   └── biz-project/
│       ├── package.json                  # 显式依赖和各端公开入口
│       ├── src/
│       │   ├── shared/                   # 项目成员变更、验收模型
│       │   ├── api/                      # 项目 API 的组合和业务查询规则
│       │   ├── pc/                       # PC 表单、字段、列表、动作等
│       │   └── mobile/                   # 移动表单和字段
│       └── tests/
├── workflow-web/src/
│   ├── adapters/extensionHost.js         # PC/Embed 宿主能力
│   └── extensions/                       # 产品扩展、示例和统一安装入口
├── workflow-mobile/src/
│   ├── adapters/extensionHost.js         # 移动宿主能力
│   └── extensions/                       # 通用扩展及产品示例
└── scripts/
    ├── extensions/                       # 清单扫描、校验、代码生成
    ├── check-business-boundaries.mjs      # 业务与产品源码依赖检查
    └── workflow-boundary.mjs              # 构建模块图和平台边界检查
```

实现包与清单目录一一对应：`packages/biz-project` 对应
`extensions/manifests/biz-project`。新业务同样使用 `biz-名称`，不再增加 `business/` 层。
业务包内部目录按实际需要创建。前端工作区的 `extensions` 只保存声明和规范，实际页面参数仍保存在业务配置中。

## 选择交付模块

默认配置是 `config/frontend-modules.json`：

```json
{ "enabledModules": ["biz-project"] }
```

环境变量覆盖配置文件，适用于开发启动或单次 CI 构建：

```bash
# 从仓库根进入唯一 npm 工作区
cd workflow-frontend
# 默认包含项目业务，构建产品包、PC、Embed 和移动端
npm run build
# 基础产品：不生成任何业务模块的导入
FLOW_FRONTEND_MODULES=none npm run build
# 已创建多个业务时，用逗号分隔名称
FLOW_FRONTEND_MODULES=biz-project npm run dev:web
```

未选择的业务不会进入应用产物。最终构建模块图还会检查传递依赖，禁止已启用业务
间接带入未启用业务。名称拼错、空环境变量、重复模块名均会报错。要长期禁用所有业务，
将配置设为 `{ "enabledModules": [] }`。

这是构建选择，不是数据库中的组件启停开关，也不是运行时卸载。`npm run dev:web/mobile`
会在配置变化后串行重建选中的包并重启开发服务；仓库根的 `./start.sh` 在一次启动中固定模块快照，
更改模块后回到仓库根执行 `./start.sh restart`。生产环境重新构建部署。
禁用业务不会删除表单配置；已有配置仍引用缺失扩展时，运行时沿用缺失扩展提示和操作限制，
不会自动回退到其他业务组件。产品 Demo 开关独立于业务模块选择，产品示例不能依赖业务包。

容器使用 `workflow-frontend/` 作为构建上下文，默认读取其中的同一份配置。
以下命令从仓库根执行；临时交付可指定构建参数：

```bash
docker build -f workflow-frontend/workflow-web/Dockerfile --build-arg FLOW_FRONTEND_MODULES=none -t flow-web:base workflow-frontend
FLOW_FRONTEND_MODULES=none docker compose build web
```

GitHub CI/部署/镜像安全检查使用可选变量 `vars.FLOW_FRONTEND_MODULES`；未设置时读取仓库配置。
构建阶段复制所有 workspace 包描述及源码、清单和 `config`，前端工作区的 `.dockerignore` 排除宿主的
`node_modules`、`dist`、环境文件和日志，运行镜像仅包含构建后的静态文件。

## 依赖边界

- 产品核心、通用 API、SDK 和移动 UI 不得导入业务包。应用页面也不直接导入业务包，
  只有清单生成的 PC/Embed 和移动虚拟模块负责装配。
- 业务使用 SDK 契约、公开产品包入口和注入的宿主能力，不引用 `@/components`、
  应用 store、`workflow-web/src` 或 `workflow-mobile/src` 私有文件。
- `shared` 和 `api` 不依赖 PC/移动 UI。PC 不导入移动组件或 Vant，移动端不导入 PC 或 Element Plus。
  包不提供同时导入两端的根入口，清单直接引用各端的公开子路径。
- 业务使用其他包时，必须声明 `dependencies`、`peerDependencies` 或 `optionalDependencies`，
  并使用目标包 `exports` 公开的入口。跨业务依赖遵循相同规则，不允许通过相对路径访问私有文件。
- 业务的动态导入使用明确的字符串入口；计算式 `import()`、`require()`、`import.meta.glob`
  不用于业务装配。新增依赖需要能被源码和构建模块图共同检查。
- Vue 业务样式使用 `scoped` 或业务专属容器，不能覆盖产品全局样式。

源码检查解析 JS/TS/Vue 的 AST，覆盖 import、export、require 和懒加载，不把注释中的示例当作依赖。
构建检查使用实际解析后的模块图，覆盖 alias、间接依赖和动态分块；所有生产入口都执行检查。

## 使用 SDK 和宿主能力

业务契约从 `@flow/workflow-extension-sdk/contracts/*` 导入。需要宿主能力时：

```js
import { useExtensionHost } from '@flow/workflow-extension-sdk/host'
import { createMemberChangeApi } from '@flow/biz-project/api/memberChange'

// 在组件 setup 中取当前应用的客户端，继承会话、请求错误和权限处理。
const host = useExtensionHost()
const memberChangeApi = createMemberChangeApi(host.entityDataApi)
```

PC/Embed 宿主提供 `entityDataApi`、`uiEventBindingApi`、`showRequestError` 和
`components.FormFieldRendererLinkage`；移动宿主提供 `entityDataApi`，移动表单使用
`@flow/workflow-mobile-ui` 的公共组件。项目专属服务由业务自行创建，通用 `formServices`
不再增加项目成员变更方法。

宿主在挂载前调用 `provideExtensionHost(app, host)`，能力按 Vue 应用隔离，不保存全局服务单例。
独立预览/组件测试也需要提供宿主；缺失时显式报错，不回退到应用私有代码。
尚未公开的产品能力先补充 SDK 契约和宿主适配，再由业务使用。

## 新增业务扩展

1. 创建 `packages/biz-order/package.json`，命名为 `@flow/biz-order`；声明实际依赖，
   在 `exports` 中分别公开 `./shared/*`、`./api/*`、`./pc/*`、`./mobile/*` 中需要接入的具体入口。
2. 在 `src` 中实现业务。可从产品扩展模板复制，复制后将应用私有导入替换为 SDK 契约/宿主能力。
   业务模块不自行注册，不修改产品注册表或产品页面。
3. 在 `extensions/manifests/biz-order/<类型>/` 新增清单。业务实现使用 `implementation.package`：

```json
{
  "implementation": {
    "package": "@flow/biz-order/pc/fields/OrderNoteField",
    "export": "default",
    "kind": "COMPONENT"
  },
  "platforms": {
    "mobile": {
      "implementation": {
        "package": "@flow/biz-order/mobile/fields/OrderNoteField",
        "export": "default",
        "kind": "COMPONENT"
      }
    }
  }
}
```

上面只展示实现入口，完整清单仍需要 `schemaVersion/type/name/version/metadata` 等字段，见
[清单手册](../workflow-frontend/extensions/manifests/README.md)。`package` 和应用内 `path` 二选一；
业务入口必须属于同名业务包，路径和平台受校验。未实现移动端时不声明 `platforms.mobile`，不会回退加载 PC。

4. 将 `biz-order` 加入启用配置，完成 workspace 安装和构建；在设计器中绑定稳定注册名、填写参数、发布。
5. 验证新增、编辑、只读、审批和校验行为，以及禁用该模块后的基础产品构建。业务名、版本、配置参数
   不能因为文件搬迁而随意更改，历史表单继续依赖这些稳定标识。

## 项目业务样板

成员变更/验收模型在 `src/shared`，项目查询组装在 `src/api/memberChange.js`；
PC 和移动表单各自维护视图，使用同一套模型和 API。PC 还提供评分单元格、项目看板、检查按钮、
列表动作、操作条件和权限候选项。内部辅助组件无需单独编写 JSON，只对需要注册的入口写清单。

既有名称保留，包括 `ProjectMemberChangeForm`、`ProjectExtensionAcceptanceForm`、
`project_acceptance_score`、`project_acceptance_level`、`ProjectAcceptanceSummaryNode`、
`ProjectAcceptanceBoardList`、`ProjectAcceptanceScoreCell` 和 `ProjectAcceptanceInspectButton`。
后台业务验收步骤见 [项目扩展验收](project-extension-acceptance/README.md)。

## 本地已发布配置的升级

注册名、实现版本、配置快照版本和参数结构保持不变；制品摘要仍严格校验。
FORM/LIST 的自动 `artifactDigest` 包含组件函数和组件属性，源码搬迁或构建变化可能改变摘要，
它不是只由名称/版本计算的标识。普通表单/列表的名称和版本绑定与显式锁定制品摘要的配置应区别处理。

本次未在正式环境使用这些项目自定义表单/列表，因此不增加旧摘要兼容映射，也不修改数据库快照。
本地测试中若关联内容或自定义表单按钮保存了旧 `artifactDigest`，需在新构建的设计器中
重新选择对应组件（必要时先清空再选择）并重新发布，使草稿与发布快照锁定新制品；不要手工清空
发布配置中的摘要。关联内容配置在已有摘要非空时不会自动替换它，单纯打开并保存不会完成更新。

`snapshotVersion` 及字段配置迁移只处理参数格式，不会授权不匹配的制品；不要通过降低校验
或把旧摘要写到新组件来规避失配。以后正式交付的组件更新应同时规划版本及已发布配置的升级。

## 验证命令

在 `workflow-frontend/` 执行：

```bash
npm run check:business-boundaries
npm run test:architecture
npm run test:packages
npm run test --workspace @flow/biz-project
npm run extensions:check --workspace workflow-web
npm run test:mobile
npm run build
FLOW_FRONTEND_MODULES=none npm run build
```

构建输出的 `module-boundary.json` 可核对实际进入每端产物的模块。新增复杂规则、边界处理、
状态流转和宿主能力时补充说明业务约束的中文注释，并更新相关测试。

本次数据库迁移：新增无、修改无、删除无。
