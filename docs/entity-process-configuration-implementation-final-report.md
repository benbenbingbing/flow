# 实体配置与流程配置实施最终报告

## 1. 结论

本次保留交付范围已完成实现并通过自动化回归，覆盖：

- P0：安全性和发布可靠性；
- P1-4.2：空办理人策略；
- P1-4.3：节点操作矩阵；
- P1-4.5：统一配置测试中心，Flyway 迁移固定使用 V056；
- P2：平台竞争力增强的五个保留工作包；列表个人/共享视图与聚合多视图已从本次交付移除。

最终审计逐项检查保留范围，均获得代码、数据库迁移或自动化测试证据。Flyway 迁移从 V001 到 V060 连续存在，共 60 个文件，无缺号、无重复。

## 2. P0：安全性和发布可靠性

### 2.1 发布预检和不可绕过发布门

- 流程发布前执行 BPMN 结构、表达式、办理人、节点操作、表单版本和依赖预检。
- 报告包含稳定问题编码、严重级别、元素定位和修复建议。
- 发布提交重新校验草稿 revision、hash 和预检令牌，草稿变化后旧结果失效。
- 发布候选统一编排跨模块依赖、幂等续跑、失败停止、补偿边界和执行报告。

### 2.2 并发、权限和结构变更安全

- 流程草稿使用 revision/hash/CAS，阻止多人或多标签页静默覆盖。
- 实体列表数据范围使用安全默认值，未明确授权时不自动放大可见数据。
- 表单 HOTFIX 引入风险治理、独立复核和审计，禁止申请人自审绕过。
- 实体结构变更通过受控状态机执行，支持预览、风险评估、唯一值冲突检查、失败状态和安全重试。
- 配置引用进入发布影响分析，硬依赖缺失可阻断高风险发布。

## 3. P1 指定范围

### 3.1 空办理人策略

已实现并测试五种策略：

| 策略 | 运行行为 |
| --- | --- |
| `BLOCK_PUBLISH` | 发布阶段阻断无有效办理人的节点 |
| `CREATE_INCIDENT` | 创建可查询、可告警、可处置的 incident 并暂停节点 |
| `FALLBACK_GROUP` | 分配到经校验的兜底组 |
| `FALLBACK_USER` | 分配到经校验的兜底用户 |
| `WAIT_AND_RETRY` | 按退避策略重试，超过上限后进入 incident |

流程级默认策略与节点级覆盖会随流程版本形成快照；补充办理人、重试、转兜底和终止动作均保留审计，重试具备幂等边界。

### 3.2 节点操作矩阵

统一服务端决策矩阵覆盖：审批、驳回、转办、加签、手工抄送、撤回和终止。每项操作支持启用状态、权限码、条件表达式、原因必填、目标范围和操作专属参数。

前端仅展示服务端决策结果；直接调用任务操作 API 仍会执行同一决策服务，不能绕过权限或条件。历史实例按启动时绑定的部署版本读取策略，不受新版本配置漂移影响。

### 3.3 统一配置测试中心

统一配置测试中心数据库迁移为：

`V056__unified_config_test_center.sql`

能力包括：

- 测试套件、用例、运行、结果、证据、历史和发布门禁；
- 实体、表单、列表、流程、空办理人、节点操作矩阵和通用 JSON 检查；
- `SCENARIO` 隔离端到端场景、`ACTION` Mock 动作、`TIME_ADVANCE` 冻结时间推进和 `SLA` 模拟；
- 模拟身份、组织、固定时间、路径覆盖、节点覆盖和操作覆盖证据；
- 默认仅允许 `MOCK`、`STUB`、`ISOLATED` 适配器；
- 明确阻断生产环境、真实外部副作用、真实通知和生产业务写入；
- 测试中心、发布候选和运行时策略复用权威校验器，避免规则漂移。

前端提供隔离场景模板，发布候选可把指定套件全部通过设置为发布门禁。

## 4. P2：平台竞争力增强

### 4.1 字段索引向导

- 从已发布列表的过滤和排序字段生成普通或联合索引候选。
- 输出受控采样选择性、预估扫描行数、写入成本和解释性建议。
- 对重复索引、低选择性字段和长文本字段给出 `NOT_RECOMMENDED` 反建议。
- 支持人工拒绝并记录拒绝人、原因、时间和 revision。
- 执行前不接受调用方 DDL，索引计划进入 P0 实体结构状态机。
- 执行前后保存 `EXPLAIN` 证据；失败进入 `DDL_FAILED` 并可重试。

### 4.2 配置协作

- 配置工作区、分支、CAS 保存、三方合并、稳定路径评论和评审。
- 审批绑定内容 hash，审批后内容变化会使审批失效。
- 定时发布绑定真实发布候选的 revision/hash，计划任务执行前重新预检。
- 提交人与评审人独立，评论、审批、合并和发布均可审计。

### 4.3 流程实例版本迁移

- 支持批次、实例清单、活动节点映射、变量覆盖、表单 release 映射和幂等键。
- 干运行检查实例版本、活动节点、挂起状态、多实例、作业、事件订阅和 Flowable 原生迁移验证结果。
- 使用独立迁移锁表阻止同一实例跨批次并发迁移，锁具有过期接管边界。
- 分批执行，单实例失败隔离，不影响未开始条目。
- 迁移后校验目标流程定义和活动节点，并记录表单映射应用证据。
- 支持暂停、失败或阻断条目续跑、受限回迁和完整审计。

### 4.4 配置使用关系

- 记录来源资产类型、来源稳定业务键、来源版本、目标类型和目标键。
- 记录引用位置、依赖强度、解析状态和抽取时间。
- 支持正向、反向、路径和影响范围查询。
- 动态引用无法解析时标记为未知，不把“未解析”误判为“未引用”。

### 4.5 TAB_SET 默认激活页

- 使用稳定 `defaultActiveTabKey`，不依赖页签显示顺序。
- 运行时按“配置默认页、首个可见且有权限页、空状态”安全降级。
- 创建、编辑、查看和审批模式复用相同选择规则。
- 历史配置缺少该字段时保持兼容行为。

## 5. 自动化验证结果

### 5.1 后端定向验证

执行跨模块定向测试并编译依赖模块：

```text
ConfigTestCaseExecutorTest                         5 passed
ProcessMigrationExecutionPolicyTest               2 passed
EntityIndexAdvisorServiceTest                     1 passed
ConfigMigrationAssetDependencyServiceTest         1 passed
合计                                                9 passed
```

移除列表视图与聚合实现后，实体模块执行完整测试：224 个测试全部通过；其中保留的 `EntityIndexAdvisorServiceTest` 通过。

JSON 顺序不稳定的既有断言已改为 Jackson 树语义比较，`UiConfigDraftMetadataServiceTest` 定向回归通过。

### 5.2 后端全量回归

全 Reactor 回归结果：

- 17 个 Reactor 模块全部 `SUCCESS`；
- `workflow-app`：807 个测试，0 失败，0 错误，1 跳过；
- `workflow-process`：247 个测试全部通过；
- `workflow-open-api`：136 个测试全部通过；
- `workflow-migration`：32 个测试全部通过；
- `workflow-http`：26 个测试全部通过，包括需要回环端口的 `PinnedHttpTransportTest`；
- 架构边界测试 10 个全部通过。
- Flyway 文件序列与基础结构契约 `SchemaRequiredTablesTest`：16 个测试全部通过。
- `workflow-db-migrator clean test`：14 个测试无失败或错误；其中 11 个真实 MySQL Testcontainers 用例因本机无 Docker 自动跳过。

本机没有可用 Docker 守护进程，因此全量命令明确排除以下 Testcontainers 端到端类：

- `OpenIntegrationTwoNodeEndToEndTest`；
- `OpenIntegrationDatabaseEndToEndTest`；
- `IntegrationSecretDatabaseEndToEndTest`。

这三个类属于环境性剩余验证，不影响本轮源码编译、单元测试、H2/Flowable 集成测试和架构测试结论；在具备 Docker 的 CI 环境应继续执行。

数据库迁移模块的 `IntegrationApplicationMigrationTest` 同样属于环境性剩余验证，需在具备 Docker 的 CI 环境补跑其 11 个真实 MySQL 用例。

### 5.3 前端验证

- `npm run test:functional`：通过；
- `npm run test:page-config`：通过；
- `npm run test:integration`：通过；
- `npm run build`：通过；
- Vite 8.1.5 成功转换 2402 个模块并生成生产构建产物。

## 6. Flyway 迁移审计

### 6.1 序列结果

- 文件数量：60；
- 版本范围：V001-V060；
- 缺失版本：无；
- 重复版本：无；
- 历史迁移删除或重命名：无；
- `V001__business_schema.sql`：未修改；
- `flyway repair`：未使用。

### 6.2 本计划涉及的迁移

| 迁移 | 主题 |
| --- | --- |
| `V048__process_draft_revision.sql` | 流程草稿 revision/hash/CAS |
| `V049__entity_list_scope_secure_defaults.sql` | 列表数据范围安全默认值 |
| `V050__entity_schema_operation_and_unique_value.sql` | 实体结构状态机与唯一值治理 |
| `V051__ui_hotfix_governance.sql` | UI HOTFIX 治理 |
| `V052__entity_list_scope_inventory.sql` | 存量数据范围盘点 |
| `V053__release_candidate_orchestration.sql` | 发布候选编排 |
| `V054__empty_assignee_policy_incident.sql` | 空办理人策略和 incident |
| `V056__unified_config_test_center.sql` | 统一配置测试中心 |
| `V057__configuration_intelligence_center.sql` | 配置智能与节点操作相关平台能力 |
| `V058__list_experience_and_config_references.sql` | 原始列表体验、索引建议和配置引用（已执行，不可变） |
| `V059__configuration_collaboration_and_instance_migration.sql` | 配置协作和实例迁移 |
| `V060__remove_list_saved_view_feature.sql` | 前向撤除列表保存视图表和权限 |

V055 属于已有独立迁移，本计划未修改。统一配置测试中心严格使用 V056，没有改用其他版本。

V058 已在数据库执行，本轮按原文件名和 checksum `854403987` 保持字节级不变；列表视图表和权限改由新增 V060 前向撤除。V059 增加迁移后校验、表单映射应用标记和跨批次实例锁。没有修改、删除或重命名任何已执行迁移，也未使用 `flyway repair`。

## 7. 发布建议和剩余环境验证

1. 在具备 MySQL 和 Docker 的 CI 环境执行全部 Flyway 升级与所有 Testcontainers 端到端类。
2. 灰度启用数据范围安全默认、发布预检强制和实例迁移能力，观察拒绝率、incident 和迁移失败率。
3. 索引建议先在预生产环境核对真实业务查询的 `EXPLAIN`，高写入成本建议安排低峰窗口。
4. 实例迁移先限定白名单流程结构和小批量，外部副作用已发生的实例继续采用补偿方案而非承诺全局回滚。

## 8. 最终验收矩阵

| 范围 | 实现证据 | 测试证据 | 状态 |
| --- | --- | --- | --- |
| P0 安全与发布可靠性 | 预检、CAS、安全默认、HOTFIX、结构状态机、发布候选 | 安全、并发、发布、架构回归 | 通过 |
| P1 空办理人 | 五策略、incident、重试和审计 | 策略正反向与幂等测试 | 通过 |
| P1 节点操作矩阵 | 七类操作、统一服务端决策、版本策略 | 权限、条件、原因和参数测试 | 通过 |
| P1 测试中心 | V056、隔离沙箱、Mock、冻结时间、SLA、门禁 | 5 个执行器定向测试及全量回归 | 通过 |
| P2 索引向导 | 选择性、反建议、拒绝、受控 DDL、EXPLAIN | 索引候选与 clean 编译测试 | 通过 |
| P2 配置协作 | 分支、合并、评论、评审、定时发布 | 协作策略和发布候选测试 | 通过 |
| P2 实例迁移 | 干运行、锁、映射、分批、续跑、回迁 | 安全策略和执行策略测试 | 通过 |
| P2 配置引用 | 版本、稳定键、位置、强度、解析状态 | 依赖持久化与图分析测试 | 通过 |
| P2 TAB_SET | 稳定默认页和权限降级 | runtime-form-tabs 测试 | 通过 |

最终状态：保留交付范围实现完成，自动化回归通过，可进入具备 MySQL/Docker 的 CI 环境执行剩余环境验收。
