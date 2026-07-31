# workflow-project

软件项目研发管理配置模块，当前包含：

- F01 需求申请与入池审批。
- F03 项目立项审批。
- F05 新系统申请审批。
- F06 项目系统关系变更审批。
- F07 项目成员变更审批。
- `requirement`、`project`、`system_application`、`project_system_change_request`、`project_member_change_request` 五个流程实体。
- `project_group`、`requirement_relation`、`requirement_project_link`、`requirement_system_impact`、`system_asset`、`system_dependency_link`、`project_system_link`、`project_member`、`project_role_catalog`、`project_role_assignment` 十个普通实体。

业务功能采用配置优先实现。平台通用动态实体、表单、列表、菜单、数据范围和审批页面直接消费本模块的 JSON/BPMN 配置。

## 配置目录

```text
src/main/resources/project-config/assets/entities/
src/main/resources/project-config/assets/processes/
src/main/resources/project-config/bpmn/
```

BPMN 源文件必须位于 `project-config/bpmn/`。不得放入类路径根目录的
`processes/`，否则 Spring Boot 启动时 Flowable 会绕过配置迁移发布流程，
自动部署源码并抢占最新版本。

## 生成发布包

```bash
node tools/build-project-config-package.mjs
```

生成文件：

```text
src/main/resources/project-config/packages/project-f01-f07-v3.wfpack
```

生产或共享环境使用非默认签名密钥时：

```bash
CONFIG_MIGRATION_SIGNING_KEY=... node tools/build-project-config-package.mjs
```

## 校验配置

```bash
node tools/validate-project-config.mjs
```

校验覆盖：

- 实体、字段和引用目标。
- 一对多子表关系。
- 表单字段与列表键。
- 数据范围策略及绑定。
- BPMN 用户任务和状态映射连线。
- 发布包资产、逐文件 SHA-256 和 HMAC 签名。

## 后端构建

```bash
mvn -pl workflow-project -am clean test
mvn -pl workflow-app -am clean package -DskipTests
```

涉及 BPMN 资源目录调整时必须使用 `clean`，避免旧的
`target/classes/processes/` 被增量打包带入运行 JAR。

## 环境发布

使用平台“系统管理 -> 配置迁移 -> 导入与发布”上传 `.wfpack`，依次执行：

1. 上传并校验。
2. 分析依赖。
3. 查看影响对比。
4. 发布。
5. 使用真实数据执行 F01、F03、F05、F06、F07 验收。

当前发布包编号：

```text
WFP-PROJECT-F01-F07-V3-20260730-R14
```

R14 已在本机目标环境完成上传、分析、发布和单账号模拟验收：

- F01 完成 7 个审批任务，最终进入需求池。
- F05 完成 6 个审批任务，并自动创建系统资产。
- F03 完成 7 个审批任务，批准后激活初始需求/系统关系并初始化成员、角色。
- F06 ADD 完成 7 个审批任务，创建并激活新的项目系统关系。
- F06 REMOVE 在存在有效系统级角色时被跨实体代码门禁阻断。
- F07 JOIN 和 LEAVE 各完成 5 个审批任务，创建成员、退出成员并完成 1 条项目经理角色移交。
- F07 自定义表单已验证新增、条件区块切换和审批路径预览。
- F01/F03/F05/F06 分别渲染 30/30/24/28 条 BPMN 连线。
- F07 渲染 20/20 条 BPMN 连线，共 63 个路径点。
- 当前 15 条流程动作均在已覆盖场景中执行成功。
- 干净构建重启后未发生项目 BPMN 自动部署覆盖。

真实 F03/F06 证据：

```text
docs/project-governance-e2e/project-governance-20260728060232.json
```

真实 F07 证据：

```text
docs/project-member-change-e2e/project-member-change-20260730015454.json
```

生产推广前仍需完成多账号角色、职责分离、F06 UPDATE/无阻断 REMOVE、F07 SUSPEND/RESUME/ALLOCATION_CHANGE、异常分支、数据范围和动作失败重试专项验收。

## 运行时扩展

实体、字段、表单、列表、数据范围、BPMN、节点配置和状态映射均通过配置实现。项目专用代码仅处理配置无法表达的跨实体聚合规则：

- `CreateSystemAssetHandler`：F05 创建系统资产并回写。
- `ValidateProjectInitiationHandler` / `ApplyProjectInitiationHandler`：F03 立项门禁与批准后初始化。
- `ValidateProjectSystemChangeHandler` / `ApplyProjectSystemChangeHandler`：F06 关系门禁与批准后生效。
- `ValidateProjectMemberChangeHandler` / `ApplyProjectMemberChangeHandler`：F07 成员门禁与批准后生效。
- `CaptureProjectMemberManagerReviewHandler`：记录 F07 项目经理节点动作。
- `RecordProjectMemberDecisionHandler`：记录 F07 最终批准连线动作。
- `ProjectLifecycleAuditHandler`：提供可复用的全局项目生命周期审计动作。
- `ProjectGovernanceService`：集中实现跨实体校验和幂等写入。
- `ProjectMemberChangeService`：集中实现成员、投入、权限回收和角色移交规则。

F07 前端自定义表单位于：

```text
workflow-web/src/project/forms/ProjectMemberChangeForm.vue
```

除该业务自定义表单和跨实体处理器外，F07 的实体、字段、列表、菜单、数据范围、BPMN、节点表单绑定和动作绑定均由配置实现。
