# 后端模块依赖边界

## 规则

- workflow-port 承载平台能力及共享模型；workflow-spi 承载 Provider 及扩展专用模型，单向依赖 workflow-port。
- 两个契约模块保留 com.workflow.contracts 包名，不引用 core 或领域实现，不引入 Spring、MyBatis、Jackson。
- database 是纯方言库；数据库执行由 core 和调用模块负责。
- app 负责装配；领域模块不能反向依赖 app。
- 新代码不得直接访问其他模块的持久化 Mapper/Record，优先使用所属领域的端口。
- 调用方直接使用的模块应在 POM 显式声明；装配依赖不能仅因没有 Java import 就删除。

## 已收口的边界

- storage 通过 CurrentActorPort / CurrentAuthorizationPort 校验资源归属，不再依赖 admin。
- 人员解析的身份展开与目录启用检查由 admin 适配器负责；原启用、删除过滤及去重语义不变。
- 流程节点表单配置归属 process；实体模块不再持有这组 process_form_* 表的 Mapper。
- 项目动作与治理服务通过 EntityRecordQueryPort 读取独立业务投影，统一变更端口保持不变。
- 权限与知会扩展使用 workflow-spi 的 Provider，宿主在边界显式投影身份、记录和通知参数。
- 字典资产的导入、停用及缓存刷新由 admin 实现 DictionaryMigrationPort，迁移模块维持外层事务编排。

## 渐进约束

CrossModulePersistenceBoundaryTest 根据生产字节码计算精确的“调用类型 → 持久化类型”依赖。
测试资源中的基线登记当前尚未收口的历史访问；新增边会失败，已解除的边必须从基线删除。
不得通过扩大包级豁免或自动更新基线消除失败。基线不是推荐用法，也不承诺这些内部类型稳定。

目前仍有身份上下文、流程表单运行服务，以及实体/流程资产迁移中的历史直接调用。
后续按业务能力迁移，保留批量读取、锁定顺序、发布快照和事务原子性，避免机械包装 Mapper。
架构门禁针对跨模块引用，不能替代 Spring 启动、数据库事务和权限行为测试。

## 兼容性

没有改动数据库表结构和 Flyway 迁移；节点表单 HTTP JSON、权限规则 JSON 和字典包协议保持不变。
Java SPI 包名和参数类型已调整，外部扩展需要按照 workflow-spi README 更新并重新编译。

契约拆分后，Outbox 发布与执行预算归入 workflow-port；列表字段、权限候选项、存储和 Outbox 消费扩展全部归入 workflow-spi。框架输入由宿主适配，不反向依赖实现类型。
