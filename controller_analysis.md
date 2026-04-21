# 低代码工作流平台 - 扩展引擎控制器分析

## 一、视图引擎模块

### 1.1 控制器分析（ViewEngineController）

| 功能名称 | HTTP方法 + URL | 输入参数 | 输出结果 | 核心业务逻辑 |
|---------|---------------|---------|---------|-------------|
| 分页查询视图列表 | `GET /api/view-engine/list` | 查询参数：`keyword`(String, 可选), `viewType`(String, 可选), `entityCode`(String, 可选), `pageNum`(int, 默认1), `pageSize`(int, 默认10) | `ApiResponse<Page<ViewDefinition>>` 分页结果 | 根据关键字、视图类型、实体编码筛选视图列表 |
| 根据实体查询视图列表 | `GET /api/view-engine/entity/{entityCode}` | 路径参数：`entityCode`(String) | `ApiResponse<List<ViewDefinition>>` 列表 | 查询指定实体关联的所有视图 |
| 查询默认视图 | `GET /api/view-engine/entity/{entityCode}/default` | 路径参数：`entityCode`(String) | `ApiResponse<ViewDefinition>` 单个视图 | 获取指定实体的默认视图 |
| 根据ID查询视图详情 | `GET /api/view-engine/{id}` | 路径参数：`id`(String) | `ApiResponse<ViewDefinition>` 单个视图 | 查询视图定义基本信息，404时返回错误 |
| 查询视图完整配置 | `GET /api/view-engine/{id}/config` | 路径参数：`id`(String) | `ApiResponse<ViewConfigVO>` 含视图+字段+查询条件+按钮 | 组装视图的完整配置信息 |
| 保存视图 | `POST /api/view-engine/save` | 请求体：`ViewConfigDTO`<br>- `view`: ViewDefinition<br>- `fields`: List<ViewFieldConfig><br>- `queries`: List<ViewQueryConfig><br>- `buttons`: List<ViewButtonConfig> | `ApiResponse<ViewDefinition>` 保存后的视图 | 原子性保存视图定义及其关联配置 |
| 删除视图 | `DELETE /api/view-engine/{id}` | 路径参数：`id`(String) | `ApiResponse<Void>` | 级联删除视图定义及字段/查询/按钮配置 |
| 设置默认视图 | `POST /api/view-engine/{id}/set-default` | 路径参数：`id`(String)<br>查询参数：`entityCode`(String) | `ApiResponse<Void>` | 取消该实体其他默认视图，将当前视图设为默认 |
| 生成默认视图 | `POST /api/view-engine/generate-default/{entityCode}` | 路径参数：`entityCode`(String) | `ApiResponse<ViewDefinition>` 生成的视图 | 根据实体字段自动生成默认列表视图配置 |

### 1.2 Service 分析（ViewEngineService）

- **继承**：`ServiceImpl<ViewDefinitionMapper, ViewDefinition>`
- **核心职责**：
  - `getViewList`：使用 MyBatis-Plus 分页查询视图列表
  - `saveView`：@Transactional 事务保存，自动编码生成（`VIEW_` + 时间戳），更新时级联删除旧配置再插入新配置
  - `deleteView`：事务级联删除 view_definition 及 view_field_config、view_query_config、view_button_config
  - `setDefaultView`：先取消该实体下所有默认视图，再设置指定视图为默认
  - `generateDefaultView`：基于 EntityDefinition 生成默认列表视图（编码规则：`{entityCode}_default_list`）

### 1.3 Entity 核心字段

#### view_definition（视图定义）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| viewCode | String | 视图编码 |
| viewName | String | 视图名称 |
| viewType | String | 视图类型：LIST/CHART/DASHBOARD/DETAIL |
| entityCode | String | 关联实体编码 |
| dataSourceType | String | 数据源类型：ENTITY/SQL/API |
| dataSourceConfig | String | 数据源配置JSON |
| layoutConfig | String | 布局配置JSON |
| styleConfig | String | 样式配置JSON |
| isDefault | Integer | 是否默认视图 |
| version | Integer | 版本号 |
| status | String | 状态 |

#### view_field_config（视图字段配置）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| viewId | String | 所属视图ID |
| fieldCode | String | 字段编码 |
| fieldName | String | 字段名称 |
| fieldType | String | 字段类型 |
| sortOrder | Integer | 排序号 |
| width | String | 列宽 |
| align | String | 对齐方式 |
| isShow | Integer | 是否显示 |
| isSortable | Integer | 是否可排序 |
| isSearchable | Integer | 是否可搜索 |
| formatterType | String | 格式化类型：TEXT/DATE/DATETIME/DICT/TAG/LINK/IMAGE/PROGRESS/CUSTOM |
| formatterConfig | String | 格式化配置 |
| fixed | String | 固定列配置 |
| showInList / showInDetail | Integer | 列表/详情显示控制 |

#### view_query_config（视图查询条件配置）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| viewId | String | 所属视图ID |
| fieldCode | String | 字段编码 |
| fieldName | String | 字段名称 |
| queryType | String | 查询类型：EQ/LIKE/LEFT_LIKE/RIGHT_LIKE/GT/LT/BETWEEN/IN/NULL |
| componentType | String | 组件类型：INPUT/SELECT/DATE/DATE_RANGE/NUMBER/NUMBER_RANGE/CASCADE/ENTITY_SELECT |
| componentConfig | String | 组件配置JSON |
| defaultValue | String | 默认值 |
| placeholder | String | 占位提示 |
| sortOrder | Integer | 排序号 |
| isAdvanced | Integer | 是否高级查询 |

#### view_button_config（视图按钮配置）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| viewId | String | 所属视图ID |
| buttonCode | String | 按钮编码 |
| buttonName | String | 按钮名称 |
| buttonType | String | 按钮位置：TOOLBAR/ROW/BATCH |
| actionType | String | 动作类型：ADD/EDIT/DELETE/VIEW/EXPORT/IMPORT/CUSTOM/SERVICE/JUMP |
| actionConfig | String | 动作配置JSON |
| icon | String | 图标 |
| style | String | 样式 |
| sortOrder | Integer | 排序号 |
| visibleCondition | String | 可见条件 |
| permissionCode | String | 权限编码 |

---

## 二、报表引擎模块

### 2.1 控制器分析（ReportEngineController）

| 功能名称 | HTTP方法 + URL | 输入参数 | 输出结果 | 核心业务逻辑 |
|---------|---------------|---------|---------|-------------|
| 分页查询报表列表 | `GET /api/report-engine/list` | 查询参数：`keyword`(String, 可选), `reportType`(String, 可选), `categoryId`(String, 可选), `pageNum`(int, 默认1), `pageSize`(int, 默认10) | `ApiResponse<Page<ReportDefinition>>` 分页结果 | 多条件筛选报表定义 |
| 根据ID查询报表详情 | `GET /api/report-engine/{id}` | 路径参数：`id`(String) | `ApiResponse<ReportDefinition>` 单个报表 | 查询报表定义，含分类名称 |
| 查询报表完整配置 | `GET /api/report-engine/{id}/config` | 路径参数：`id`(String) | `ApiResponse<ReportConfigVO>` 含报表+数据集 | 组装报表及数据集配置 |
| 保存报表 | `POST /api/report-engine/save` | 请求体：`ReportConfigDTO`<br>- `report`: ReportDefinition<br>- `datasets`: List<ReportDataset> | `ApiResponse<ReportDefinition>` 保存后的报表 | 事务保存报表定义及数据集 |
| 删除报表 | `DELETE /api/report-engine/{id}` | 路径参数：`id`(String) | `ApiResponse<Void>` | 级联删除报表及数据集 |
| 获取报表分类 | `GET /api/report-engine/categories` | 无 | `ApiResponse<List<ReportCategory>>` 分类列表 | 查询所有报表分类 |
| 获取报表数据 | `POST /api/report-engine/{id}/data` | 路径参数：`id`(String)<br>请求体：`Map<String, Object>` 查询参数 | `ApiResponse<Map<String, List<Map<String, Object>>>>` 数据集结果Map | 根据数据集配置执行SQL或实体查询，返回各数据集数据 |
| 执行SQL查询（测试） | `POST /api/report-engine/execute-sql` | 请求体：`SqlQueryDTO`<br>- `sql`: String<br>- `params`: Map<String, Object> | `ApiResponse<List<Map<String, Object>>>` SQL结果 | 直接执行SQL（支持`${param}`占位符替换） |

### 2.2 Service 分析（ReportEngineService）

- **继承**：`ServiceImpl<ReportDefinitionMapper, ReportDefinition>`
- **核心职责**：
  - `getReportList`：分页查询，支持关键字、报表类型、分类筛选
  - `saveReport`：事务保存，自动生成编码（`RPT_` + 时间戳），新增时 version=1，更新时 version++，并级联更新数据集
  - `getReportData`：遍历报表的数据集（ReportDataset），根据 `datasetType` 分发执行：SQL类型调用 `executeSqlQuery`，ENTITY类型目前简化返回空列表
  - `executeSqlQuery`：通过 `JdbcTemplate` 执行SQL，使用字符串替换方式处理 `${param}` 占位符参数

### 2.3 Entity 核心字段

#### report_definition（报表定义）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| reportCode | String | 报表编码 |
| reportName | String | 报表名称 |
| reportType | String | 报表类型：TABLE/CHART/DASHBOARD/PRINT |
| categoryId | String | 分类ID |
| datasetConfig | String | 数据集配置JSON |
| layoutConfig | String | 布局配置JSON |
| paramsConfig | String | 参数配置JSON |
| styleConfig | String | 样式配置JSON |
| permissionConfig | String | 权限配置JSON |
| version | Integer | 版本号 |
| status | String | 状态 |

#### report_dataset（报表数据集）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| reportId | String | 所属报表ID |
| datasetCode | String | 数据集编码 |
| datasetName | String | 数据集名称 |
| datasetType | String | 数据集类型：SQL/ENTITY/API |
| sourceConfig | String | 源配置JSON（如SQL语句） |
| fieldMappings | String | 字段映射JSON |
| cacheConfig | String | 缓存配置JSON |

#### report_category（报表分类）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| parentId | String | 父分类ID |
| categoryCode | String | 分类编码 |
| categoryName | String | 分类名称 |
| sortOrder | Integer | 排序号 |

---

## 三、服务编排模块

### 3.1 控制器分析（ServiceOrchestrationController）

| 功能名称 | HTTP方法 + URL | 输入参数 | 输出结果 | 核心业务逻辑 |
|---------|---------------|---------|---------|-------------|
| 分页查询服务列表 | `GET /api/service-orchestration/list` | 查询参数：`keyword`(String, 可选), `serviceType`(String, 可选), `categoryId`(String, 可选), `pageNum`(int, 默认1), `pageSize`(int, 默认10) | `ApiResponse<Page<ServiceDefinition>>` 分页结果 | 多条件筛选服务编排定义 |
| 根据ID查询服务详情 | `GET /api/service-orchestration/{id}` | 路径参数：`id`(String) | `ApiResponse<ServiceDefinition>` 单个服务 | 查询服务定义，含分类名称 |
| 查询服务完整配置 | `GET /api/service-orchestration/{id}/config` | 路径参数：`id`(String) | `ApiResponse<ServiceConfigVO>` 含服务+节点 | 组装服务编排定义及节点列表 |
| 保存服务 | `POST /api/service-orchestration/save` | 请求体：`ServiceConfigDTO`<br>- `service`: ServiceDefinition<br>- `nodes`: List<ServiceNode> | `ApiResponse<ServiceDefinition>` 保存后的服务 | 事务保存服务定义及节点配置 |
| 删除服务 | `DELETE /api/service-orchestration/{id}` | 路径参数：`id`(String) | `ApiResponse<Void>` | 级联删除服务及节点 |
| 执行服务 | `POST /api/service-orchestration/{id}/execute` | 路径参数：`id`(String)<br>请求体：`Map<String, Object>` 输入参数（可选） | `ApiResponse<ExecutionResult>` 执行结果 | 调用服务编排执行引擎运行服务 |
| 获取执行日志 | `GET /api/service-orchestration/{id}/logs` | 路径参数：`id`(String)<br>查询参数：`status`(String, 可选), `pageNum`(int, 默认1), `pageSize`(int, 默认10) | `ApiResponse<Page<ServiceExecutionLog>>` 分页日志 | 查询服务的执行历史日志 |
| 获取服务分类 | `GET /api/service-orchestration/categories` | 无 | `ApiResponse<List<ServiceCategory>>` 分类列表 | 查询所有服务分类 |

### 3.2 Service 分析

#### ServiceOrchestrationService（管理服务）
- **继承**：`ServiceImpl<ServiceDefinitionMapper, ServiceDefinition>`
- **核心职责**：
  - `getServiceList`：分页查询服务编排列表
  - `saveService`：事务保存，自动生成编码（`SVC_` + 时间戳），新增时 version=1，更新时 version++，级联更新节点
  - `executeService`：调用 `ServiceOrchestrationEngine.execute()` 执行服务编排
  - `getExecutionLogs`：分页查询 `service_execution_log`

#### ServiceOrchestrationEngine（执行引擎）
- **核心职责**：
  - `execute`：DAG执行入口。创建执行日志（RUNNING状态），查找START节点，递归执行下游节点，最后更新日志为SUCCESS/FAILED
  - `executeNode`：节点路由分发器，支持节点类型：START、END、ENTITY_CRUD、HTTP、SQL、SCRIPT、CONDITION、PARALLEL、MAPPING、LOG
  - `executeHttpNode`：使用 `RestTemplate` 发起HTTP请求（GET/POST），支持变量替换 `${var}`
  - `executeSqlNode`：使用 `JdbcTemplate` 执行SQL，支持变量替换
  - `executeParallelNode`：使用线程池 + Future 并发执行多个分支，支持超时控制
  - `executeScriptNode`：预留Groovy脚本执行（目前简化处理）
  - `executeMappingNode`：字段映射转换
  - `executeLogNode`：按日志级别输出日志（INFO/WARN/ERROR/DEBUG）
  - `shouldExecuteNode`：简单条件表达式解析（目前仅支持 `==` 判断）
  - `applyOutputMapping`：将节点输出按映射规则写入执行上下文

### 3.3 Entity 核心字段

#### service_definition（服务编排定义）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| serviceCode | String | 服务编码 |
| serviceName | String | 服务名称 |
| serviceType | String | 服务类型：ORCHESTRATION/SCRIPT/PROXY |
| categoryId | String | 分类ID |
| description | String | 描述 |
| inputParams | String | 入参定义JSON |
| outputParams | String | 出参定义JSON |
| flowConfig | String | 流程配置JSON |
| variables | String | 变量定义JSON |
| timeoutMs | Integer | 超时时间（毫秒） |
| retryConfig | String | 重试配置JSON |
| exceptionHandler | String | 异常处理配置JSON |
| version | Integer | 版本号 |
| status | String | 状态 |

#### service_node（服务节点定义）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| serviceId | String | 所属服务ID |
| nodeId | String | 节点唯一标识 |
| nodeType | String | 节点类型：START/END/ENTITY_CRUD/HTTP/SQL/SCRIPT/CONDITION/PARALLEL/JOIN/LOOP/SUBFLOW/PROCESS/MESSAGE/DELAY/MAPPING/LOG |
| nodeName | String | 节点名称 |
| positionX / positionY | BigDecimal | 画布坐标 |
| config | String | 节点配置JSON |
| inputMapping | String | 输入映射JSON |
| outputMapping | String | 输出映射JSON |
| nextNodes | String | 下游节点ID列表JSON |
| conditionExpression | String | 条件表达式 |

#### service_execution_log（服务执行日志）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| serviceId | String | 服务ID |
| executionId | String | 执行ID（UUID） |
| triggerType | String | 触发类型：MANUAL/SCHEDULE/EVENT/API |
| triggerSource | String | 触发来源 |
| inputParams | String | 输入参数JSON |
| outputResult | String | 输出结果JSON |
| status | String | 状态：RUNNING/SUCCESS/FAILED/TIMEOUT |
| startTime / endTime | LocalDateTime | 起止时间 |
| durationMs | Integer | 执行耗时（毫秒） |
| nodeExecutions | String | 节点执行详情JSON |
| errorMessage | String | 错误信息 |

#### service_category（服务分类）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| parentId | String | 父分类ID |
| categoryCode | String | 分类编码 |
| categoryName | String | 分类名称 |
| sortOrder | Integer | 排序号 |

---

## 四、脚本引擎模块

### 4.1 控制器分析（ScriptEngineController）

| 功能名称 | HTTP方法 + URL | 输入参数 | 输出结果 | 核心业务逻辑 |
|---------|---------------|---------|---------|-------------|
| 执行Groovy脚本 | `POST /api/script-engine/execute` | 请求体：`ScriptExecuteDTO`<br>- `script`: String（脚本内容）<br>- `context`: Map<String, Object>（上下文变量）<br>- `timeoutMs`: Long（超时毫秒，可选） | `ApiResponse<ScriptResult>` 执行结果（success/result/errorMessage/durationMs） | 调用Groovy引擎执行脚本，支持超时控制 |
| 验证脚本语法 | `POST /api/script-engine/validate` | 请求体：`ScriptValidateDTO`<br>- `script`: String | `ApiResponse<Boolean>` 是否合法 | 解析Groovy脚本，返回语法校验结果 |
| 获取脚本模板 | `GET /api/script-engine/templates` | 无 | `ApiResponse<Map<String, String>>` 模板Map | 返回4种内置脚本模板：basic/calculation/condition/loop |

### 4.2 Service 分析（GroovyScriptEngine）

- **核心职责**：
  - `execute`：创建 `GroovyShell` + `Binding` 执行脚本。上下文注入 `_util`（ScriptUtils）和 `_log`（日志对象）。支持 `timeoutMs` 超时控制，使用 `ExecutorService + Future.get(timeout)` 实现。
  - `validate`：调用 `GroovyShell.parse()` 做语法校验。
  - `ScriptUtils`：提供脚本内常用工具方法：`formatDate`、`daysBetween`、`isEmpty`、`toUpperCase`、`substring` 等。
  - `ScriptResult`：封装执行结果（success、result、errorMessage、durationMs）。

---

## 五、工作台模块

### 5.1 控制器分析（WorkbenchController）

| 功能名称 | HTTP方法 + URL | 输入参数 | 输出结果 | 核心业务逻辑 |
|---------|---------------|---------|---------|-------------|
| 获取工作台数据 | `GET /api/workbench/data` | 请求属性：`userId`(String) | `ApiResponse<Map<String, Object>>` | 聚合流程统计、待办任务（前5条）、快捷入口（mock）、系统公告（mock） |

### 5.2 Service 分析（ProcessCenterService）

- **继承**：`ServiceImpl<ProcessTaskInstanceMapper, ProcessTaskInstance>`
- **核心职责**（被 WorkbenchController 间接调用）：
  - `getStatistics(userId)`：返回 `ProcessStatisticsVO`，统计：待办数、今日已办数、未读抄送数
  - `getTodoList(userId, processKey, keyword, priority, pageNum, pageSize)`：分页查询待办任务
  - 此外还支持：已办任务、抄送任务、常用意见、草稿箱等流程中心功能

### 5.3 相关 VO/Entity

#### ProcessStatisticsVO
| 字段 | 类型 | 说明 |
|-----|------|------|
| todoCount | Long | 待办数量 |
| doneTodayCount | Long | 今日已办数量 |
| unreadCcCount | Long | 未读抄送数量 |
| draftCount | Long | 草稿数量 |

#### process_task_instance（流程任务实例）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (UUID) | 主键 |
| processInstanceId | String | 流程实例ID |
| taskId / taskKey / taskName | String | 任务标识与名称 |
| processDefinitionId / processName | String | 流程定义信息 |
| entityCode / entityDataId / businessKey | String | 业务实体关联 |
| assigneeId / assigneeName | String | 处理人 |
| ownerId | String | 任务所有人 |
| candidateUsers / candidateGroups | String | 候选人/候选组 |
| taskType | String | 任务类型：TODO/DONE/DRAFT/CC |
| actionType | String | 操作类型：SUBMIT/APPROVE/REJECT/TRANSFER/RETURN/DELEGATE |
| actionComment | String | 操作意见 |
| formData | String | 表单数据JSON |
| dueTime | LocalDateTime | 截止时间 |
| priority | Integer | 优先级 |
| isRead / readTime | Integer/LocalDateTime | 已读状态 |
| startTime / endTime / durationMs | LocalDateTime/Long | 任务起止与耗时 |

---

## 六、文件管理模块

### 6.1 控制器分析（FileController）

| 功能名称 | HTTP方法 + URL | 输入参数 | 输出结果 | 核心业务逻辑 |
|---------|---------------|---------|---------|-------------|
| 上传文件 | `POST /api/file/upload` | `MultipartFile file`（表单文件） | `Result<Map<String, String>>`<br>返回 url/filename/originalName/size | 保存到本地目录，生成 `yyyyMMddHHmmss_UUID.扩展名` 格式文件名 |
| 上传图片 | `POST /api/file/upload-image` | `MultipartFile file`<br>`maxWidth`(int, 默认1920)<br>`quality`(float, 默认0.8) | 同上 | 验证contentType为image/*，当前复用普通上传逻辑（压缩待扩展） |
| 删除文件 | `DELETE /api/file` | 查询参数：`url`(String) | `Result<Void>` | 提取安全文件名，校验路径遍历，删除物理文件 |
| 预览/下载文件 | `GET /api/file/preview` | 查询参数：`url`(String) | 直接写入 `HttpServletResponse` 输出流 | 安全检查后返回文件流，Content-Type为 `application/octet-stream` |

### 6.2 核心逻辑说明

- **配置项**：
  - `file.upload.path`：本地存储路径（默认 `./uploads`）
  - `file.access.url`：对外访问URL前缀（默认 `http://localhost:8088/uploads`）
- **安全措施**：
  - `extractSafeFilename`：从URL提取文件名，过滤 `..`、`/`、`\` 防止路径遍历
  - `isFileInsideDirectory`：使用 `getCanonicalFile()` 二次校验文件是否在允许目录内

---

## 七、认证授权模块

### 7.1 控制器分析（AuthController）

| 功能名称 | HTTP方法 + URL | 输入参数 | 输出结果 | 核心业务逻辑 |
|---------|---------------|---------|---------|-------------|
| 用户登录 | `POST /api/auth/login` | 请求体：`LoginDTO`<br>- `username`: String（@NotBlank）<br>- `password`: String（@NotBlank） | `Result<LoginUserVO>` 登录用户信息+JWT Token | 校验用户名密码，检查用户状态，生成JWT Token，admin特殊处理 |
| 获取当前登录用户信息 | `GET /api/auth/current` | 从 `UserContext` 获取当前用户ID | `Result<LoginUserVO>` 用户信息（无token） | 根据当前登录用户ID查询用户信息及角色 |
| 退出登录 | `POST /api/auth/logout` | 无 | `Result<Void>` | 后端预留日志记录点，前端清除token即可 |

### 7.2 Service 分析（SysUserService）

- **核心职责**：
  - `getByUsername`：根据用户名查询用户（用于登录）
  - `getById`：根据ID查询，并填充角色列表和组织部门信息
  - `saveUser`：保存用户，校验用户名唯一性，默认密码 `123456`（BCrypt加密）
  - `updatePassword`：更新密码字段
  - `saveUserRoles`：维护 `sys_user_role` 关联表
  - `fillUserRoles` / `fillUserOrgInfo`：填充非持久化字段（roles、orgName、deptName）

### 7.3 Entity / DTO / VO

#### sys_user（用户表）
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String (ASSIGN_ID) | 主键 |
| username | String | 用户名 |
| nickname | String | 昵称 |
| password | String | 密码（BCrypt加密） |
| email | String | 邮箱 |
| phone | String | 手机号 |
| avatar | String | 头像URL |
| status | String | 0-启用 1-禁用 |
| createTime / updateTime | LocalDateTime | 创建/更新时间 |
| deleted | Integer (@TableLogic) | 逻辑删除标志 |
| orgId / deptId | String | 组织/部门ID |

#### LoginDTO
| 字段 | 类型 | 说明 |
|-----|------|------|
| username | String | @NotBlank |
| password | String | @NotBlank |

#### LoginUserVO
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | String | 用户ID |
| username | String | 用户名 |
| nickname | String | 昵称 |
| avatar | String | 头像 |
| email | String | 邮箱 |
| phone | String | 手机号 |
| roles | List<String> | 角色编码列表 |
| token | String | JWT Token |
