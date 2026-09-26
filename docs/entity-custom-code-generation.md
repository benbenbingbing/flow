# 实体自定义编码接入

编码规则支持 `RULE`（内置规则，默认）和 `CUSTOM`（业务生成器）。管理入口为实体列表的“数据编码规则配置”。选择自定义生成器后配置参数；仅影响之后创建的记录，编辑、审批和流程重提不重新取号。

## 保存顺序与上下文

1. 校验表单字段及权限，分配记录 ID。
2. 构造 `EntityCodeGenerationContext`，调用生成器。
3. 校验完整编码及该字段的发布规则，并在数据库中预留唯一值。
4. INSERT 当前记录，保存子表等关联数据，提交业务事务。

**调用 `generate` 时，`recordId` 已分配，但当前记录尚未 INSERT，按该 ID 查询业务表得不到记录。** 直接使用 `context.data()` 读取表单字段。字段名是发布定义的逻辑字段编码，不是数据库列名。数据是递归只读快照，不包含子表单内部令牌；主记录的多值输入也会传入。

| 上下文字段 | 使用说明 |
| --- | --- |
| `entityCode` / `recordId` | 本次创建的实体与预分配 ID；ID 不代表保存成功 |
| `data` | 当前记录业务字段，含 `name`；不含嵌套关系、客户端编号或可信身份 |
| `operatorId` / `deptId` | 可信变更身份及其部门，非表单同名字段；系统操作可为空 |
| `parentEntityCode` / `parentRecordId` | 子记录所属父记录，根记录为空 |
| `parentData` | 同一事务内读取的父记录标量字段快照，附带父记录 `id`、`code`；父记录可能尚未提交 |
| `generationTime` | 本次调用时间，使用服务端时区 |
| `idempotencyKey` | 上层变更幂等键加实体及提交路径；没有变更上下文时为空 |

父记录快照不包含尚未写入关联表的多值字段。扩展若需要这些值，应通过明确的业务输入设计提供，不依赖远程回查未提交记录。

## 实现接口

接口在 `workflow-spi`：`com.workflow.contracts.entity.code.spi.EntityCodeGeneratorProvider`。业务模块实现后注册为 Spring Bean，平台自动发现。一个生成器可以被多个实体选择；`supportedEntityCodes()` 可限制适用实体。

```java
@Component
public class OrderCodeGenerator implements EntityCodeGeneratorProvider {
    public String getCode() { return "ORDER_CODE"; }
    public String getDisplayName() { return "订单业务编号"; }
    public Set<String> supportedEntityCodes() { return Set.of("order"); }

    /** 当前订单还未入库，使用上下文中的业务类型与预分配 ID。 */
    public String generate(EntityCodeGenerationContext context, Map<String, Object> configuration) {
        Object type = context.data().get("orderType");
        if (type == null) throw new IllegalArgumentException("订单类型不能为空");
        return type + "-" + context.recordId();
    }
}
```

生成器返回**完整编号**，平台不再追加前缀、日期或流水。结果必须非空、无首尾空白或控制字符，且不超过 100 个字符；失败时抛异常，保存随之失败，不降级到内置规则。

`getCode()` 使用大写字母开头的字母、数字、下划线，最长 64 位。它是配置引用的稳定标识，不能随意重命名。重复标识在启动时失败。接口实现只做计算或取号，不得递归保存实体、修改传入数据或发送业务通知。

可选扩展点：

- `configurationSchema()`：参数 JSON Schema，支持现有平台校验器的 object/string/number/integer/boolean/array、required、enum 等能力。页面将顶层属性映射为参数编辑器，嵌套对象和数组通过 JSON 参数编辑。
- `validateConfiguration(entityCode, configuration)`：校验 Schema 无法表达的业务约束；必须无副作用。
- `preview(context, configuration)`：返回格式样例，禁止消耗流水或执行外部写入。默认不支持预览，页面显示“保存新记录时生成”。预览没有真实记录 ID、表单值和用户身份，不能调用正式 `generate()` 兜底。

可运行示例：`biz-project/.../custom/ProjectEntityCodeGenerator.java`，标识 `PROJECT_RECORD_ID`，适用于 `project`。按配置前缀与记录 ID 生成，使用 `{"prefix":"XM"}` 即可验证接入。

## 事务、并发与重试

自定义实现加入当前业务事务。因此生成子表编号时可以读取同事务中的父记录，但外部系统通常无法看到该父记录。内置规则的序列分配仍通过独立 Spring Bean 的 `REQUIRES_NEW` 提交，业务回滚允许跳号。

最终编号使用 `entity_unique_value` 的 `$record_code` 专属命名空间预留，按实体、去首尾空白并转小写后的编号区分唯一性。占用与业务 INSERT 同事务提交或回滚，通用字段更新和删除不释放成功使用过的编号。取号还会检查没有建立占用的历史记录；启用自定义模式前检查存量重复编号，发现冲突时要求先处理，平台不会改写历史编号。

子表 `CUSTOM` 模式始终使用生成器结果，传入非空 `code` 也无法绕过。`RULE` 模式保留既有子表显式传码的兼容行为，但同样经过最终值校验与唯一性预留。编辑主表或子表都不能改写已有编号。

远程取号应在生成器内部使用受控客户端设置连接/读取超时，凭证从部署配置取得，不保存在生成器参数中。平台不会自动重试自定义生成；只有远端支持幂等且上层重试沿用稳定键时，业务实现才可安全重试。

`idempotencyKey` 不自动保证整次 HTTP 请求幂等：调用方换键，或变更子行顺序/提交路径，就属于不同取号请求。不要用本次随机分配的 `recordId` 代替跨请求幂等键。远端已经分配的号码不会随本地数据库事务回滚，不保证连续无缺号。

## 配置接口与迁移

现有接口继续使用 `/api/entity-code-rule`；新增 `GET /api/entity-code-rule/generators?entityCode=project` 获取可选实现及 Schema。均沿用实体定义管理权限。

保存示例：

```json
{
  "entityCode": "project",
  "generationMode": "CUSTOM",
  "generatorCode": "PROJECT_RECORD_ID",
  "generatorConfig": { "prefix": "XM" }
}
```

切换为自定义时保留内置格式和流水状态。保存规则只更新配置列，不覆盖并发取号产生的序号。配置导出包含生成器标识和参数，不导出 `currentSeq`、`seqDate`；导入分析和实际应用前均校验实现是否存在、实体适用范围及参数是否合法。

新增 Flyway 文件：`V107__entity_custom_code_generator.sql`，增加 `generation_mode`、`generator_code`、`generator_config`，旧记录默认为 `RULE`。未修改、删除或重命名历史迁移。

升级顺序：执行增量迁移，升级全部后端节点并安装业务实现，再开放自定义配置。混用不认识 `CUSTOM` 的旧节点会导致行为不一致，不能提前启用。回退应用前先把已启用实体切回可用的规则生成模式。

## 验证入口

- 后端：`EntityCodeGeneratorServiceTest`、`EntityCodeContextFactoryTest`、`EntityCustomCodeTransactionTest`、`EntityRelationRuntimeUniqueClaimTest`、`EntityDataDynamicServiceSubFormTest`、`ConfigMigrationAnalysisPersistenceTest`。
- 前端模型：`node src/shared/__tests__/entity-code-rule.spec.js`。
- 真实弹窗浏览器回归：在 `workflow-web` 执行 `npm run test:entity-code-rule:browser`。使用模拟 API、独立临时 Chrome 配置，不修改业务数据。
