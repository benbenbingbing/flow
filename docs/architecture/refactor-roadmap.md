# 平台架构重构路线

## 目标

将项目调整为模块化单体。系统保持一个后端服务和一个前端应用，代码按业务能力分区，明确设计态、发布态、运行态边界。

## 核心原则

- 设计态支持变更，发布态保留版本，运行态按发布快照执行。
- Controller 只处理入参、鉴权、响应，不放业务规则。
- Service 按能力拆分，不把设计、发布、运行写在同一个类里。
- 动态表访问统一走运行时入口。
- 权限策略独立计算，查询层只消费结果。
- 前端页面只承载交互编排，运行时协议下沉到 request、runtime、renderer 层。

## 后端目标边界

```text
com.workflow
  common              响应、异常、分页、校验、上下文
  security            登录、鉴权、用户上下文、数据权限入口
  system              用户、角色、组织、菜单、字典、文件
  entity
    definition        实体定义、字段定义、关系定义
    form              表单定义、表单解析、字段运行协议
    list              列表定义、列配置、查询视图
    publish           发布、版本、快照、差异
    runtime           动态表、动态数据、状态、编码规则
  process
    definition        流程设计、BPMN、节点配置
    deploy            发布到 Flowable
    runtime           发起、任务、审批、退回、转办、进度
  audit               操作日志、登录日志、业务审计事件
```

## 前端目标边界

```text
src/shared/request        请求、错误处理、token、分页
src/shared/schema         配置结构适配
src/features/entity       实体设计、发布、数据运行
src/features/form         字段渲染器、表单运行时
src/features/list         列表运行时、筛选、排序、列权限
src/features/process      流程设计、节点配置、任务审批
src/features/system       用户、角色、组织、菜单、字典
```

## P1：后端核心边界

先拆运行时，再拆设计态和发布态。

优先处理：

- `EntityDataDynamicService`：拆出动态记录映射、关系运行时、流程发起、状态写回。
- `ProcessInstanceService`：拆出流程进度、任务视图、运行时状态同步。
- `ProcessDefinitionService`：拆出 BPMN 清理、部署、节点配置同步。
- `DataPermissionEngine`：拆出匹配规则和 SQL 条件构建。

验收：

- 核心服务单文件不超过 500 行。
- 动态数据写入、读取、关系保存、流程发起具备独立入口。
- 运行时模块可以被单独单测。
- 原有接口保持兼容。

## P2：领域模型统一

建立三层模型：

```text
Draft              草稿配置
PublishedSnapshot  发布快照
RuntimeRecord      运行数据
```

核心对象：

- `EntityDefinition`：实体定义。
- `EntityRelation`：实体关系。
- `EntityForm`：表单定义。
- `EntityListConfig`：列表视图。
- `ProcessBinding`：实体与流程绑定。
- `NodeFormBinding`：节点与表单绑定。
- `PermissionPolicy`：权限策略。

验收：

- 发起流程和审批读取发布快照，不读取草稿。
- 动态数据校验来自发布快照。
- 实体关系通过统一运行时保存和读取。
- 权限策略可复用、可测试。

## P3：前端运行时重构

先拆请求层，再拆表单和流程配置。

优先处理：

- 统一 `src/shared/request`。
- 拆 `NodeConfigPanel.vue`。
- 建字段注册器和表单运行时。
- 建列表运行时。
- 将页面里的转换逻辑移到 feature runtime。

验收：

- 大型 Vue 文件逐步降到 600 行以内。
- 新增字段类型只改字段组件和注册表。
- 列表、表单、流程节点配置职责隔离。
- 请求错误、401、分页格式统一。

## 迁移方式

- 每次只移动一个边界。
- 每次迁移先补测试，再改实现。
- 每次通过测试后提交。
- 现有接口保持兼容，内部逐步转发到新模块。
- 不删除测试数据。
