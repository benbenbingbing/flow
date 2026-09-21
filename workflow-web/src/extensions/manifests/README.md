# 注册清单

构建器递归发现所有 `*.extension.json`，无需维护总清单或 import 映射。每份文件声明一个扩展名称与版本，结构由 `../schemas/extension.schema.json` 校验。

- `platform/`：标准字段和单元格，平台必须能力；字段别名和默认类型映射只在这里定义。
- `common/`：跨业务复用扩展，例如金额校验器。
- `business/<module>/`：业务模块扩展。模块名用于目录归属，不限制实现路径。
- `examples/`：示例；与现有 Demo 开关一致，默认生产不安装。契约模板清单标记 `enabled: false`，需显式启用后才参与示例构建。

每份清单都可通过 `$schema` 获得编辑器补全。关键字段：

| 属性 | 含义 |
| --- | --- |
| `name` | 写入业务配置的稳定注册名，不能随重构改名 |
| `label/description` | 设计器中的名称及使用说明 |
| `implementation.path/export/kind` | 源码路径、导出名、加载形态 |
| `metadata.configSchema` | 可配置参数的定义，不是当前实体的参数值 |
| `metadata.supportedEntityCodes` | 实体编码范围；空数组或 `[*]` 表示全部 |
| `metadata.supportedFieldTypes` | 字段类型兼容范围 |
| `metadata.usageContexts` | FORM/LIST 的 PAGE 或 RELATED_CONTENT 宿主契约 |
| `targets` | LIST_ACTION 适用的 TOOLBAR/ROW，可同时安装 |
| `defaultConfig` | ACTION_CONDITION 新建时独立复制的参数 |
| `hooks` | createDefault/migrateConfig 的源码引用，kind 必须 FUNCTION |
| `aliases/defaultForFieldTypes` | 平台字段的历史别名、实体字段默认控件映射 |

`enabled` 是构建安装开关，不等于数据库目录启停。移除仍被已发布表单引用的实现会破坏历史配置，应保留原注册身份并通过正常版本发布升级。
