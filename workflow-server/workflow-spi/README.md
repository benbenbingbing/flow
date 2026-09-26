# workflow-spi

后端业务/插件扩展契约，与 `workflow-port` 共同替代原 `workflow-contracts`。
保留 `com.workflow.contracts.<domain>[.<feature>].spi` 命名空间，公开扩展接口统一以 Provider 结尾。

## 职责与依赖

- Provider 由业务或插件实现，平台负责集合发现、编码校验、目录治理、授权、版本校验和调度。
- 本模块只依赖 workflow-port 和编译期 Lombok，不引用 Spring、MyBatis、Jackson 或领域实现。
- 扩展需要使用平台能力时调用明确暴露的 Port；workflow-port 禁止反向依赖本模块。
- Provider 专用描述符、请求和结果放所属 model 包；携带运行时访问能力的上下文放 context。
- 模型不依赖 port/spi；上下文可以使用 Port，例如 FlowActionContext 持有 FlowActionRuntimePort。
- Registry、Bean 实现、持久化对象和 Controller DTO 仍留在各宿主模块。
- 前端 props/emits、校验器和 JSON 清单继续在 workflow-web 的扩展契约体系中维护。

## 接口改名

| 旧接口 | 新接口 |
| --- | --- |
| FlowActionHandler | FlowActionProvider |
| TypedFlowActionHandler | TypedFlowActionProvider |
| PersonResolver | PersonResolverProvider |
| PersonResolverConfigurationValidator | PersonResolverConfigurationValidationProvider |
| EntityListContextResolver | EntityListContextResolverProvider |
| CcRecipientResolver | CcRecipientProvider |
| CcNotificationChannel | CcNotificationChannelProvider |
| entity.code.EntityCodeGenerator | entity.code.spi.EntityCodeGeneratorProvider |
| storage.application.port.FileStorageStrategy | storage.spi.FileStorageProvider |
| outbox.api.OutboxEventHandler | outbox.spi.OutboxEventHandlerProvider |

前八项原有能力包保持不变，实体编码接口增加 spi 角色包。后两项的新完整包名前缀为
`com.workflow.contracts`。具体实现类不因接口改名而改名，已有 Bean 名和配置标识保持不变。

## 补齐的扩展边界

| Provider | 配套模型 | 宿主 |
| --- | --- | --- |
| entity.list.spi.ListFieldDataProvider | entity.list.model.ListFieldDataRecord / ListFieldDataConfig | workflow-entity 的 Registry 与列表服务 |
| entity.permission.spi.EntityPermissionOptionProvider | entity.permission.model.EntityPermissionOption | workflow-entity 的权限目录与配置服务 |
| storage.spi.FileStorageProvider | storage.model.FileUpload / StoredFile | workflow-storage 的工厂与上传控制器 |
| outbox.spi.OutboxEventHandlerProvider | outbox.model.OutboxEvent | workflow-outbox 的 Processor |

列表扩展不再接收 EntityDataDTO 或持久化 EntityListField。宿主生成配置/行快照，扩展成功后
只合并 data/extData，保留行数、顺序、身份、状态、权限和发布上下文；写入对象不会持久化。
Provider 只能补充当前页展示值，不允许扩展列参与查询和排序。历史未注册源的处理规则保持不变。

权限候选项由宿主转换为 API DTO，保留原 JSON 字段和按权限码去重规则。提供候选项不代表授权。
存储上传使用框架无关的 FileUpload：内容延迟打开，Provider 必须关闭其打开的流，不能在
HTTP 请求结束后异步读取。StoredFile 的消费方同样需要关闭返回流。
OutboxEventHandlerProvider 仍按 topic 唯一路由，业务处理按 eventKey 幂等；异常、retryable
以及重试规则由宿主保持。扩展通过 workflow-port 的 OutboxPublishPort 发布事件。

权限和知会 SPI 使用独立业务投影：SysUser 改为 IdentityUser，动作/条件中的 EntityDataDTO
改为 EntityRecordData，知会渠道参数使用 CcNotification。规则树与配置包 JSON 协议保持不变。

## 发布边界

EntityListActionProvider 与 DataScopePredicateProvider 是 incubating 预留接口，当前没有
宿主 Registry/自动路由；实现或注册 Bean 不会使其生效，不能承诺为正式可用扩展。
其他 Provider 的配置编码、Bean 名、topic、storageType、版本号不会因 Java 接口改名自动改变。

每个 canonical 类型只保留一份定义，不提供 deprecated bridge、旧包别名或模型镜像。
外部扩展需同步依赖、import 和参数签名并重新编译。UI Provider 继续沿用
UiProviderArtifactIdentity 的默认制品摘要算法；纯 Maven 拆分不改变 Java 包名。
如重新编译导致已钉定实现的 class 字节发生变化，必须核对发布快照的版本与制品摘要，
必要时新增实现版本并保留历史版本可执行，不能改写旧摘要绕过检查。

实现示例见 [biz-project/custom](../biz-project/src/main/java/com/workflow/biz/project/custom/README.md)。
依赖规则与验证入口见 [workflow-port](../workflow-port/README.md)。
