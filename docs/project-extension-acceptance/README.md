# 项目扩展能力验收

本验收场景用于集中验证实体、表单、列表、流程和后端扩展点。初始化脚本可重复执行，
同名配置存在时会更新，不会重复创建同一套实体、表单、列表和流程。
脚本使用隐藏字段 `acceptance_batch` 区分当前样本与历史样本：当前批次固定为
`CURRENT`，旧流程记录保留用于审计但会标记为 `ARCHIVED`，因此验收列表始终只
展示当前三条样本。

## 当前验收入口

- 前端：`http://127.0.0.1:3002`
- 后端 API：`http://127.0.0.1:8082/api`
- 首选列表：
  `http://127.0.0.1:3002/entity-list/project_extension_acceptance/project_extension_acceptance_matrix`
- 应用账号：`codex_acceptance_admin`
- 当前数据：三条 `CURRENT` 样本，其中“全扩展链路验收单”位于
  “技术扩展验收”审批节点。

账号密码不写入仓库。验收浏览器已保持登录状态并停留在技术扩展验收对话框。

最新页面证据：

- `screenshots/14-current-batch-three-records.png`
- `screenshots/15-fresh-technical-review.png`

## 执行前提

1. 后端必须使用当前工作区代码重新启动，确保 `workflow-project` 中新增的 Spring Bean
   已进入运行时。
2. 本机 Flyway 仅按要求处理 V001 历史校验值。其他迁移校验问题需要单独确认，不能由
   本脚本绕过。
3. 准备一个具备超级管理员权限、已完成首次改密的测试账号。
4. 前端使用当前 `workflow-web/src/project` 代码启动或构建。

## 初始化命令

可以使用账号密码、一次性令牌文件或一次性凭据文件。脚本读取令牌或凭据文件后会
立即删除文件，避免认证信息长期留在磁盘。

在仓库根目录使用账号密码执行：

```bash
TEST_USERNAME='<管理员账号>' \
TEST_PASSWORD='<当前密码>' \
node workflow-server/workflow-project/tools/real-project-extension-acceptance.mjs
```

使用一次性令牌文件：

```bash
TEST_TOKEN_FILE='/private/tmp/project-extension-acceptance.token' \
node workflow-server/workflow-project/tools/real-project-extension-acceptance.mjs
```

使用一次性凭据文件时，文件内容为：

```json
{
  "username": "<管理员账号>",
  "password": "<当前密码>"
}
```

执行命令：

```bash
TEST_CREDENTIAL_FILE='/private/tmp/project-extension-acceptance.credentials.json' \
node workflow-server/workflow-project/tools/real-project-extension-acceptance.mjs
```

接口地址不是默认的 `http://127.0.0.1:8080/api` 时，再增加：

```bash
WORKFLOW_API_BASE='http://127.0.0.1:8080/api'
```

执行成功后会生成：

```text
docs/project-extension-acceptance/latest.json
```

该文件记录实体、表单、列表、流程、数据源、动作处理器和样例数据 ID，可用于定位
配置是否真实落库并发布。

脚本最后会做运行时硬校验，而不只是保存配置：

- 查询标准列表、Schema Provider 列表、查询 Provider 列表和 `LIST_QUERY`
  统一数据源列表，并核对各自的真实返回记录。
- 重新读取表单发布状态、整表单数据源绑定、底部按钮、动作插槽、自定义字段、
  自定义节点以及字段、表单、实体事件链。
- 重新读取已发布流程及 7 个流程动作，核对动作均启用且绑定到动作定义目录。
- 校验通过后才生成 `latest.json`；任一契约缺失都会生成
  `latest-failed.json` 并退出失败。

## 初始化内容

### 实体

- 实体编码：`project_extension_acceptance`
- 实体名称：`项目扩展验收单`
- 样例数据：
  - `EXT-ACCEPT-001`：启动完整流程
  - `EXT-ACCEPT-002`：表单扩展验证
  - `EXT-ACCEPT-003`：列表扩展验证

### 表单

| 表单 | 用途 | 重点验收项 |
| --- | --- | --- |
| 项目扩展验收整表单 | 完全自定义表单组件 | 自定义加载、统一数据源回填、保存、流程提交 |
| 扩展验收节点矩阵表单 | 平台标准表单 | 自定义字段、自定义节点、初始化器、字段事件、底部按钮、动作插槽按钮 |
| 扩展验收最终只读表单 | 流程末节点 | 后端流程动作回写结果、轨迹、作用范围和触发时机 |

### 列表

| 列表 | 路由 | 重点验收项 |
| --- | --- | --- |
| 标准扩展矩阵 | `/entity-list/project_extension_acceptance/project_extension_acceptance_matrix` | 自定义单元格、虚拟列 Provider、统一列数据源、工具栏、行按钮、权限和规则 |
| 完全自定义看板 | `/entity-list/project_extension_acceptance/project_extension_acceptance_board` | 自定义列表组件、查询、分页、新增、查看、编辑、审批 |
| 后端 Schema 扩展列表 | `/entity-list/project_extension_acceptance/project_extension_acceptance_schema` | `PROJECT_CUSTOM_LIST_SCHEMA` 增强 `viewConfig` 后复用项目看板组件 |
| 查询 Provider 列表 | `/entity-list/project_extension_acceptance/project_extension_acceptance_provider` | `PROJECT_CUSTOM_LIST_QUERY` 返回一条不落库演示记录 |
| LIST 统一数据源列表 | `/entity-list/project_extension_acceptance/project_extension_acceptance_unified` | `PROJECT_CUSTOM_UI_LIST` 的 `LIST_QUERY` 返回演示记录 |

### 流程

- 流程编码：`project_extension_acceptance_process`
- 流程名称：`项目扩展能力验收流程`
- 节点顺序：技术评审 -> 业务评审 -> 最终确认
- 技术评审使用完全自定义表单。
- 业务评审使用标准节点矩阵表单。
- 最终确认使用只读表单。
- 流程级、节点级、连线级动作都绑定
  `projectExtensionAcceptanceFlowActionHandler`。
- 审批人使用 `projectCustomPersonResolver`。

## 验收步骤

1. 打开“标准扩展矩阵”列表，确认三条落库样例数据正常展示。
2. 确认“字段 Provider 虚拟列”显示 `项目扩展:<数据编号>`。
3. 确认自定义评分单元格按分数展示通过或待复核状态。
4. 执行工具栏自定义按钮、选择结果按钮、行自定义按钮和组件按钮，确认页面提示与
   浏览器控制台日志；“打开 Provider 列表”还会调用
   `projectCustomRelation` 可信上下文解析器。
5. 打开“后端 Schema 扩展列表”，确认看板正常显示，并在接口返回的
   `viewConfig.projectCustomSchema` 和后端日志中看到 Provider 执行标记。
6. 打开“查询 Provider 列表”，确认显示 `EXT-PROVIDER-001`。
7. 打开“LIST 统一数据源列表”，确认显示 `EXT-UI-LIST-001`。
8. 新建或编辑表单，确认自定义评分字段、复核级别 Provider 选项及默认值、摘要
   节点和初始化默认值生效；点击评分右侧连接图标，确认
   `FIELD_BUTTON_CLICK`、字段回填和连接器日志。
9. 修改评分字段，分别点击底部“记录表单验收日志”和节点内“执行插槽验收日志”，
   确认字段变化事件、按钮权限、二次确认和连接器日志。
10. 打开 `EXT-ACCEPT-001` 对应流程，依次完成技术评审、业务评审和最终确认。
11. 回到详情或只读表单，确认 `extension_result`、`backend_trace`、
    `last_action_scope`、`last_action_timing`、`last_action_element` 已回写。

## 日志定位

前端控制台统一检索：

```text
[ProjectExtensionAcceptance]
```

后端日志可检索：

```text
ProjectExtensionAcceptanceFlowActionHandler
PROJECT_CUSTOM_FIELD
PROJECT_CUSTOM_LIST_QUERY
PROJECT_CUSTOM_UI_ENTITY
PROJECT_CUSTOM_UI_FORM
PROJECT_CUSTOM_UI_LIST
PROJECT_CUSTOM_LOG_CONNECTOR
projectCustomPersonResolver
```

验收虚拟列配置时，后端应打印 `fieldCode`、`labelPrefix`、`recordCount`。验收统一
数据源时，后端应打印 `providerCode`、`recommendedScope`、`usage`、发布版本和
返回数量。验收流程动作时，后端应打印作用范围、触发时机、执行元素、实体记录和
回写字段。

## 自动检查

前端：

```bash
cd workflow-web
node src/project/__tests__/acceptanceExtensions.spec.js
npm run build
```

后端：

```bash
cd workflow-server
mvn -pl workflow-project -am \
  -Dtest=ProjectCustomBackendExtensionsTest,ProjectExtensionAcceptanceFlowActionHandlerTest,OutboxProcessorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
