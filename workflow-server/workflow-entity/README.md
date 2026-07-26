# workflow-entity

实体模块负责实体定义、字段与关系、表单、列表、数据权限、发布快照和运行态数据。

## 包边界

- `com.workflow.entity.api`：实体模块对外应用接口和请求适配。
- `com.workflow.entity.definition`：实体、字段、关系和编码规则。
- `com.workflow.entity.form`：表单、初始化配置、联动和事件配置。
- `com.workflow.entity.list`：列表、动态字段和按钮配置。
- `com.workflow.entity.policy`：数据权限、按钮权限和适用条件。
- `com.workflow.entity.publish`：发布快照、版本和兼容校验。
- `com.workflow.entity.runtime`：实体数据运行时。
- `com.workflow.entity.compatibility`：迁移期间的旧接口适配，不允许新增业务规则。

现有 `com.workflow.controller`、`com.workflow.service`、`com.workflow.mapper` 作为兼容结构逐步迁移。
新代码应进入对应能力包；跨模块访问必须通过 `workflow-contracts` 中的端口。

## 跨模块端口

- 实体运行态通过 `ProcessRuntimePort` 发起流程，不直接依赖流程模块实现。
- 流程模块通过 `EntityRecordPort` 更新实体流程字段和活动记录。
- 流程表单解析通过 `EntityFormRuntimePort` 读取实体表单上下文。
- 发布资产通过 `MigrationAssetHandler` 登记到迁移模块。
