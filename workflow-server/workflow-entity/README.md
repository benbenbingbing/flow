# workflow-entity

实体模块负责实体定义、字段与关系、表单、列表、数据权限、发布快照和运行态数据。

## 包边界

- `com.workflow.entity.definition`：实体、字段、关系和编码规则。
- `com.workflow.entity.form`：表单、生命周期数据处理、联动和事件配置。
- `com.workflow.entity.list`：列表、动态字段和按钮配置。
- `com.workflow.entity.permission`：数据权限、按钮权限和适用条件。
- `com.workflow.entity.ui`：界面配置、发布版本、扩展、事件和视图组合。
- `com.workflow.entity.data`：实体记录查询、动态表结构和运行时数据访问。
- `com.workflow.entity.mutation`：统一实体变更入口、事务执行和幂等回执。
- `com.workflow.entity.version`：记录快照、版本策略、比较与恢复。

各能力按需使用 `api`、`application`、`domain`、`infrastructure`。HTTP 请求和响应放入 `api.request/response`；内部计算结果放入 `application.model`；执行上下文放入 `application.context`；应用定义的外部能力接口放入 `application.port`。数据权限计算结果包含 SQL 条件，属于内部应用模型。
校验器归入已有的 `application.validation`。仅供单个服务使用的包内辅助类保留同包，不为分目录扩大其可见性。
实体变更调用版本捕获能力，版本能力仍负责快照和版本规则。跨模块访问使用 `workflow-contracts` 中的端口。

## 跨模块端口

- 实体运行态通过 `ProcessRuntimePort` 发起流程，不直接依赖流程模块实现。
- 流程模块通过 `EntityRecordPort` 更新实体流程字段和活动记录。
- 流程表单解析通过 `EntityFormRuntimePort` 读取实体表单上下文。
- 发布资产通过 `MigrationAssetPort` 登记到迁移模块。
