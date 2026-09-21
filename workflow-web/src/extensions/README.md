# 前端扩展中心

前端可复用扩展的唯一声明与安装入口。所有已实现的业务、平台内置、通用与演示扩展均位于本目录；新模块的实现也可以放在自己的 `src/modules/<module>/` 中，JSON 清单仍统一放在 `manifests/`。

## 新增一个扩展

1. 从 `templates/` 选择模板，阅读对应 `contracts/` 的 props、事件和方法注释。
2. 在所属模块完成 Vue 或 JS 实现。实现文件不调用 `register*`，导入时不发业务请求。
3. 在 `manifests/<归属>/<类型>/` 新建一个 `*.extension.json`；业务归属再加模块目录。
4. 执行 `npm run extensions:check`、`npm run test:extensions` 和 `npm run build`。修改平台字段清单时先执行 `npm run extensions:generate`。
5. 部署构建后，在设计器选择注册名、设置参数并发布。FORM/NODE/FIELD/VALIDATOR 通过实体表单进入流程办理页。

不用修改入口、手写 import 表或再创建业务模块注册 index。JSON 新增、删除或修改后开发服务器整页刷新；实现更新遵循正常 Vue HMR。生产新增实现需要重新构建部署。

## 目录

| 目录 | 职责 |
| --- | --- |
| `manifests/` | 唯一手工维护的注册声明；每个身份与版本一个文件 |
| `core/` | 安装器、类型适配器、现有注册表和统一目录 |
| `schemas/` | 带中文属性说明的 JSON Schema，校验清单及参数定义 |
| `contracts/` | 组件 props/emits、动作、校验与运行时辅助契约 |
| `templates/` | 可复制的组件模板，复制或导入本身不会安装 |
| `builtin/` | 平台标准字段、单元格及其内部运行帮助 |
| `common/` | 与特定业务无关的实现，例如金额校验器 |
| `business/project/` | 从原 project 目录完整迁入的项目业务实现 |
| `examples/` | 可选 Demo 和契约使用示例 |
| `generated/` | 平台字段纯数据策略的自动生成结果，禁止手改 |
| `__tests__/` | 清单、注册行为、迁移兼容性和开发服务器验证 |

## 类型及配置位置

| type | 清单分类 | 配置位置 |
| --- | --- | --- |
| FORM | forms | 实体整表单组件 |
| FIELD | fields | 表单字段组件类型 |
| NODE | nodes | 表单节点的复用与扩展 |
| LIST | lists | 列表扩展渲染 |
| LIST_CELL | list-cells | 列渲染组件 |
| LIST_BUTTON | buttons | 列表按钮：自定义/组件 |
| LIST_ACTION | actions | 列表按钮函数或 selectionHandler |
| VALIDATOR | validators | 字段数据校验/自定义校验 |
| ACTION_CONDITION | rules | 操作条件编辑器，需要对应后端判定 |
| PERMISSION_PROVIDER | permissions | 权限候选项，需要后端授权支持 |

## 最小示例

```json
{
  "schemaVersion": 1,
  "type": "FIELD",
  "name": "business_score",
  "label": "业务评分",
  "version": 1,
  "implementation": {
    "path": "src/modules/business/ScoreField.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "supportedFieldTypes": ["DECIMAL"],
    "configSchema": [{ "key": "max", "label": "最大值", "type": "number", "defaultValue": 100 }]
  }
}
```

这是待替换模块路径的开发示意。可直接运行的真实清单见 `manifests/business/project/fields/project_acceptance_score.v1.extension.json` 和 `manifests/common/validators/amount.v1.extension.json`。

`name` 保持稳定；`schemaVersion` 是清单协议版本，`version` 是实现版本，`metadata.snapshotVersion` 是配置结构版本。参数定义在 JSON，具体实体的参数值仍保存在表单或列表配置中。JSON 不支持注释语法，使用 `$comment`、`description` 及参数的 `description` 说明用途。

## 运行与治理边界

- `implementation.kind` 明确区分 COMPONENT、FUNCTION、OBJECT、CLASS、FACTORY。CLASS 需要无参构造；FACTORY 同步返回实例，可以接收 `{ services }`，不能启动业务操作。
- 函数型动作注册时不会执行。条件默认值每次深复制；复杂默认值/节点升级通过 `hooks` 引用 JS 函数。
- FORM/LIST 的 `metadata.usageContexts` 区分 PAGE（默认）和 RELATED_CONTENT，选择器与运行时使用相同过滤；关联模板有独立契约。
- FORM/LIST/NODE/VALIDATOR 保留现有多版本能力；其余注册表只允许同名一个活动版本，构建器拒绝覆盖。组件声明版本不等于每个旧宿主都精确选版。
- JSON 决定当前构建有哪些实现；数据库目录的启停、发布和引用检查继续执行。只在构建注册的类型不能使用尚未实现的后台纳管 API。
- 主应用与 Embed 共用注册入口；Embed 原有脚本和请求限制保持不变。
- 表单按钮、联动、字段脚本、接口绑定及模板是页面配置，不额外创建注册类型。

旧 `src/project`、`src/demo`、`src/contracts` 和散落的注册表已迁移，不保留第二个注册入口。迁移时已有注册名称不变，历史实体和流程配置继续引用原名称。

## 测试与详细说明

`npm run test:extension-contracts` 编译契约、模板和示例；`npm run test:extensions:browser` 使用独立 Chrome 验证实际注册与清单增删改。浏览器路径可通过 `CHROME_PATH` 指定。完整迁移范围与验证记录见仓库 docs/frontend-extension-json-registration-design.md 和 docs/frontend-extension-json-registration-verification.md。
