# Flow

Flow 是一个低代码流程平台，包含流程设计、实体建模、动态表单与列表、菜单权限、流程动作、配置迁移和任务处理能力。

## 技术栈

- 后端：Java 17、Spring Boot 3.5、Flowable 7、MyBatis-Plus、Flyway、MySQL
- 前端：Vue 3、Vite、Element Plus
- 架构：多 Maven 模块的模块化单体，单应用部署

## 项目目录

```text
workflow-server/   后端 Maven 父工程
workflow-web/      前端应用
docs/              架构、部署、数据库和测试资料
start.sh           本地编译和重启脚本
```

## 后端模块

后端代码按照功能拆分，最终由 `workflow-app` 聚合为一个可执行 Spring Boot JAR。

| 模块 | 主要职责 |
|---|---|
| `workflow-common` | 统一响应、分页结果、通用异常和无业务语义的纯工具类 |
| `workflow-contracts` | 跨模块端口、稳定 DTO、发布记录接口、流程动作设计接口和第三方连接器 SPI |
| `workflow-system` | 登录认证、JWT、当前用户、用户、角色、用户组、组织、菜单和字典 |
| `workflow-storage` | 文件上传、文件访问和可替换的存储策略 |
| `workflow-entity` | 实体定义、字段、关系、状态、编码规则、动态表、表单、列表和数据权限 |
| `workflow-process` | 流程定义、发布、Flowable 部署、节点配置、节点表单、实例、任务、抄送、撤回和终止 |
| `workflow-action` | 流程动作配置、触发时机、执行器、Outbox、重试、死信、执行日志和 Handler 管理 |
| `workflow-integration` | 第三方对接扩展，当前包含通知 Handler，并预留 HTTP、Webhook、邮件和消息连接器 |
| `workflow-migration` | 发布快照、`.wfpack` 导出导入、环境映射、差异分析、发布和回滚 |
| `workflow-devtools` | Demo Handler、Flowable Delegate、脚本测试和代码生成 |
| `workflow-app` | 应用启动、全局异常、CORS、MyBatis、Flowable 监听器组装、Flyway 和最终打包 |

模块依赖方向：

```text
workflow-common
       ↑
workflow-contracts
       ↑
workflow-system / workflow-storage / workflow-entity / workflow-process
workflow-action / workflow-integration / workflow-migration / workflow-devtools
       ↑
workflow-app
```

### 模块边界规则

- `workflow-common` 不允许依赖 Spring Web、MyBatis、Flowable 或业务模块。
- 跨模块查询和操作优先通过 `workflow-contracts` 中的端口完成。
- 实体模块通过 `ProcessCatalogPort` 查询流程信息，不直接访问流程 Mapper。
- 流程发布通过 `FlowActionDesignPort` 调用动作模块，不直接依赖动作 Service。
- 实体和流程发布通过 `MigrationAssetRecorder` 记录迁移资产，不直接依赖迁移实现。
- 第三方 SDK、外部 URL、密钥解析和 HTTP Client 只能放在 `workflow-integration`。
- Flyway 迁移集中在 `workflow-app/src/main/resources/db/migration`，禁止调整历史版本号和校验和。

## 接口命名

项目尚未正式上线，流程相关接口直接使用最终规范名称，不保留旧接口兼容层：

- 流程动作：`/api/process-actions`
- 流程动作处理器：`/api/process-action-handlers`
- 流程动作执行记录：`/api/process-action-executions`
- 流程实体状态映射：`/api/process-entity-status-mappings`

请求和响应 JSON、权限码及认证 Header 不因数据库表名调整而改变。新增或调整接口时必须运行 Controller 映射和前端 API 审计测试。

## 构建

后端完整构建：

```bash
cd workflow-server
mvn clean package
```

跳过测试构建：

```bash
cd workflow-server
mvn clean package -DskipTests
```

最终产物：

```text
workflow-server/workflow-app/target/workflow-server-1.0.0.jar
```

单独验证某个模块及其依赖：

```bash
cd workflow-server
mvn -pl workflow-entity -am test
mvn -pl workflow-action -am test
```

## 启动

一键编译并重启：

```bash
./start.sh
```

手工启动：

```bash
cd workflow-server
mvn clean package -DskipTests
java -jar workflow-app/target/workflow-server-1.0.0.jar
```

前端：

```bash
cd workflow-web
npm ci
npm run dev
```

默认地址：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`

## 测试

后端：

```bash
cd workflow-server
mvn test
```

前端：

```bash
cd workflow-web
npm run test:page-config
npm run test:functional
npm run build
```

提交模块调整前至少验证：

1. 父工程 reactor 构建成功。
2. 后端完整测试通过。
3. Controller 映射与主分支一致。
4. `./start.sh` 能正常启动。
5. 前端登录、实体列表、流程设计、任务审批和配置迁移页面能够正常访问。

## 数据库

- 业务数据库由 Flyway 管理。
- Flowable 引擎表由 Flowable 管理。
- `entity_*`：实体配置和元数据。
- `biz_*`：实体发布后自动生成的独立业务表。
- 历史 `entity_data_*` 在启动时迁移为 `biz_*`；孤立旧表保留数据后
  按后缀迁移，禁止覆盖已存在的目标表。
- `runtime_*`：跨实体运行记录，例如 `runtime_entity_record`。
- `process_*`：平台自有流程配置、运行记录和流程动作。
- `sys_*`：用户、角色、组织、菜单和字典。
- `config_*`：配置迁移资产、导入导出和环境映射。
- Flowable 的 `ACT_*` 表保持引擎原始命名。
- 实体物理业务表名由 `entity_definition.table_name` 登记，运行时代码禁止自行拼接表名。
- V017 将当前开发库一次性迁移到最终命名；项目不保留旧表名和旧 API 运行兼容。

## 贡献

提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。
