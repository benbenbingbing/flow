# 清单 Schema 怎么使用

`extension.schema.json` 是所有注册 JSON 的格式依据，构建器和编辑器使用同一份定义。各配置项含义和类型限制统一在 [manifests 使用手册](../manifests/README.md) 查询。

## 编辑器补全与校验

在清单顶层填写 `$schema`，路径相对该 JSON 文件，而 implementation.path 始终相对 workflow-web。常见路径如下：

| 清单所在目录 | $schema 值 |
| --- | --- |
| manifests/platform/fields 或 manifests/common/validators | ../../../schemas/extension.schema.json |
| manifests/business/project/fields 或 manifests/examples/demo/forms | ../../../../schemas/extension.schema.json |

例如在 common/validators 下的 JSON 顶层添加 `"$schema": "../../../schemas/extension.schema.json"`，支持 JSON Schema 的编辑器即可提示属性和枚举。普通开发只使用 Schema，不为每个新组件修改它。

## 检查文件

从 `workflow-web/` 执行 `npm run extensions:check`。它除格式外还检查源码文件、type/kind、专属属性和名字冲突；编辑器补全不能替代这些检查。`npm run build` 再验证实际导出和 Vue 编译。

JSON 注释只使用顶层 `$comment`，参数说明使用 `metadata.configSchema[].description`；不要插入 //、块注释或自行增加 `_comment` 属性。当前协议不允许在 implementation、metadata 内随意加注释键。

## 平台维护者修改协议

确需新增协议能力时，同步更新此 Schema、build/extensions/validate.mjs 的业务约束、适配器/消费者、manifests 手册和测试。校验器只实现当前 Schema 用到的关键字，增加新关键字时必须确认 Node 校验器也会执行。已有 schemaVersion=1 清单应继续可读；新增一个已有类型的实现不需要改 schemaVersion。
