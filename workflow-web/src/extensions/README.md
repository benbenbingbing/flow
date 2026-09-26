# 前端扩展中心

这里统一维护前端扩展的契约、实现和 JSON 注册声明。新增已有类型的扩展，完成实现并新增 JSON 即可；业务实现也可位于自己的 `src/modules/<module>/`。主应用与 Embed 已共用安装入口，不需要新增注册代码。

## 按用途阅读

README 只保留在本目录和下一层目录，子模块用法合并到对应说明中。

| 说明 | 什么时候看 |
| --- | --- |
| [manifests](../../../extensions/manifests/README.md) | 注册配置项完整说明、十类类型、填写示例、启用和排错 |
| [templates](templates/README.md) | 选择和复制组件模板，完成从模板到注册的步骤 |
| [contracts](contracts/README.md) | 导入 props/emits、调用校验方法、处理宿主上下文 |
| [builtin](builtin/README.md) | 使用平台字段和单元格，复用 useFormField |
| [common](common/README.md) | 复用金额校验，添加通用实现 |
| [business](business/README.md) | 新增业务模块、使用现有项目组件和动作 |
| [examples](examples/README.md) | 启用 Demo、使用契约示例、复制成正式业务实现 |
| [core](core/README.md) | 平台开发者查询目录、调用注册表和维护适配器 |
| [schemas](../../../extensions/schemas/README.md) | 编辑器补全、清单格式校验和协议维护 |
| [generated](generated/README.md) | 更新和读取平台字段的派生数据 |
| [__tests__](__tests__/README.md) | 按改动范围选择测试、运行浏览器验收 |

## 从模板新增一个字段

以下命令均从 `workflow-web/` 执行。示例使用文本模板真实支持的 suffix 参数。

```sh
mkdir -p src/extensions/common/fields ../extensions/manifests/common/fields
cp src/extensions/templates/CustomFieldTemplate.vue src/extensions/common/fields/NoteField.vue
```

在仓库根目录 `extensions/manifests/common/fields/note_field.v1.extension.json` 写入：

```json
{
  "$schema": "../../../schemas/extension.schema.json",
  "$comment": "通用备注字段。name 是设计器保存的稳定名称；suffix 是每个字段独立填写的展示参数。",
  "schemaVersion": 1,
  "type": "FIELD",
  "name": "note_field",
  "label": "备注输入",
  "version": 1,
  "implementation": {
    "path": "src/extensions/common/fields/NoteField.vue",
    "export": "default",
    "kind": "COMPONENT"
  },
  "metadata": {
    "supportedFieldTypes": ["STRING", "TEXT"],
    "configSchema": [{
      "key": "suffix", "label": "输入框后缀", "type": "text", "defaultValue": "备注",
      "description": "模板从 field.componentProps.suffix 读取，只改变展示，不写入字段值。"
    }]
  }
}
```

然后运行：

```sh
npm run extensions:check
npm run test:extensions
npm run dev
```

进入实体表单设计器，选择字符串或文本字段，在组件选项中选“备注输入”，填写后缀。若页面要求纳管，先按现有扩展管理流程纳管；保存并预览，检查输入同步、后缀和只读状态。发布后，引用该实体表单的流程也可使用。生产部署前运行 `npm run build`。

不用修改 `main.js`、业务 index 或手工 import 表。开发服务器监听清单新增/修改/删除并整页刷新；生产新增实现需要重新构建部署。模板文件本身不会自动注册。

## 已有扩展如何配置

| type | 设计器中的用途 | 当前实例参数读取位置 |
| --- | --- | --- |
| FORM | 实体整表单 | form.viewConfig.customComponentProps → config |
| FIELD | 表单字段组件 | field.componentProps |
| NODE | 表单节点扩展 | 节点组件参数 → config |
| LIST | 整列表布局 | viewConfig.customComponentProps → config |
| LIST_CELL | 列渲染组件 | 列 renderConfig → config |
| LIST_BUTTON | 列表自定义/组件按钮 | mode/row/disabled/reason/context；不保证收到 config |
| LIST_ACTION | 自定义函数按钮、选择回调 | handler(context)，context.config 是按钮配置 |
| VALIDATOR | 字段自定义校验 | validate(value, context)，context.params 是规则参数 |
| ACTION_CONDITION | 操作条件编辑器 | modelValue 是条件参数，需要配套后端判定 |
| PERMISSION_PROVIDER | 权限候选项 | provider(context) 返回选项，需要后端授权支持 |

JSON 的 `metadata.configSchema` 是参数定义，上表中的值才是某个表单或列表的实际配置。参数声明不会自动实现业务行为。

## 入口文件怎么用

- `register.js`：应用启动时安装扩展。现有主应用和 Embed 已调用，业务页面不要再次调用。
- `manifest.js`：应用初始化后读取 `getBundledExtensionManifest()`；需要服务端可治理的四类时使用 `getManagedExtensionManifest()`。
- `index.js`：对外汇总上述接口；普通组件优先直接导入契约或所需查询模块，避免把启动依赖带入实现。

FORM/LIST 用 usageContexts 区分 PAGE 和 RELATED_CONTENT。支持多版本的类型、清单与数据库启停的区别，以及示例开关详见 [注册配置手册](../../../extensions/manifests/README.md)。历史绑定使用稳定名称；制品摘要锁定仍遵守原有检查。
