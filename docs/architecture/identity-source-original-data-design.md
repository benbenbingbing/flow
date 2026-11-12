**用户、组织来源原始数据留存方案**

状态为已实现。设计及实现日期为 2026-11-12，结构迁移为 V131。

本方案覆盖 `IdentityWritePort` 的单条及批量用户、组织写入。业务模块在转换外部记录时同时提交该记录的原始 JSON，框架保存最近一次成功接收的原始内容，供管理页面追溯字段映射及同步结果。

首期按每个来源身份保留一份原始记录设计。新增原始记录会替换此前内容，因此该方案支持查看最近一次留存的数据。逐次同步历史、失败报文及自动重放需要另行定义留存和执行规则。

**1. 现状与设计依据**

实现前，`UserSaveModel` 和 `OrganizationSaveModel` 仅接收来源键、版本、基础快照及扩展组。`sys_identity_source_binding` 保存来源映射、版本和 `payload_hash`。该摘要由基础快照及本次携带的扩展计算，外部记录中未映射的字段无法留存；现在通过独立的 `rawDataJson` 留存。

普通来源查询使用映射表的 `SELECT *`。原始 JSON 可能远大于映射字段，因此采用独立表，以来源映射的 `source_key` 为共享主键，只在用户主动查看时读取正文。现有来源身份仍由对象类型、来源编码和外部 ID 精确定位。

**2. 调用模型与兼容方式**

为两个保存模型增加可选字段 `String rawDataJson`。公共模型继续只依赖 JDK，JSON 校验放在 admin 应用层。

```java
identities.saveUser(UserSaveModel.builder()
        .key(new ExternalIdentityKey("project-directory", externalId))
        .sourceVersion(sourceVersion)
        .base(baseData)
        .extensions(List.of())
        .rawDataJson(originalRecordJson)
        .build());
```

调用方应传入与该用户或组织对应的单条原始 JSON 对象，在字段转换前保留原文。外部接口按批返回时，由业务拆分每条记录并保留完整字段。框架保存调用方实际提供的内容。

使用字符串传递并按原文存储，保留大整数、小数、字段顺序及空白。校验器读取 JSON 后直接保存原字符串，避免经过 Map 或浏览器 Number 转换后丢失精度。`raw_hash` 按原文 UTF-8 字节计算，格式变化也会视为原始内容变化。

原有四参数构造方法保留为重载，内部将 `rawDataJson` 设置为 null。已有 Builder 调用可以省略新字段。所有模块随应用重新编译，编译检查同时覆盖原有构造调用。

输入规则如下。

| 输入 | 处理 |
| --- | --- |
| Java null | 表示本次未携带原始记录，按来源的必填配置处理 |
| 合法 JSON 对象 | 校验后原文保存，允许嵌套对象和数组字段 |
| 空字符串、JSON null、数组或标量根节点 | 返回 `INVALID_RAW_DATA` |
| 重复键、尾随额外内容或格式错误 | 返回 `INVALID_RAW_DATA`，避免不同解析器产生歧义 |
| UTF-8 字节数超限 | 返回 `RAW_DATA_TOO_LARGE` |

原始记录用于追溯，账号、组织编码、父级引用及扩展的业务含义仍以调用方提交并经框架校验的基础模型为准。框架无法通用推断各业务系统原始字段与基础模型之间的对应关系。

**3. 数据存储**

新增 `sys_identity_source_snapshot`，与 `sys_identity_source_binding` 按 `source_key` 一对一关联。沿用来源键的精确摘要值和普通关系表的方言适配方式。

| 字段 | 逻辑类型及约束 | 用途 |
| --- | --- | --- |
| `source_key` | `CHAR(64)`，非空主键 | 关联现有来源身份，保证同一用户或组织只保留一份原始记录 |
| `source_version` | `BIGINT`，可空 | 保存留存时对应的来源版本，SERIAL 模式为 NULL |
| `payload_hash` | `CHAR(64)`，非空 | 保存留存时的业务快照摘要，供追溯其与映射记录的对应关系 |
| `raw_json` | 大文本，非空 | 保存调用方传入的原始 JSON 正文 |
| `raw_hash` | `CHAR(64)`，非空 | 原文 UTF-8 字节的 SHA-256，用于原文判重 |
| `raw_bytes` | 整数，非空 | 原文 UTF-8 字节数，供大小限制及查询元信息展示使用 |
| `is_current` | `SMALLINT`，非空，默认 1 | 1 表示对应当前已接受的业务写入，0 表示后续业务更新未携带原文 |
| `recorded_at` | 时间戳，非空 | 原始记录最近一次实际写入时间，用于判断留存时点 |
| `recorded_by` | `VARCHAR(64)`，非空 | 该次写入的有效操作人，与来源审计使用同一用户 ID |

`raw_json` 在 MySQL / OceanBase MySQL 使用 LONGTEXT，在 PostgreSQL / 人大金仓使用 TEXT，在 Oracle / 达梦 / OceanBase Oracle 使用 CLOB。专用 TypeHandler 使用 JDBC 字符流读写，避免大原文被当作 VARCHAR 绑定；H2 CLOB 完成 Mapper 读写验证，MySQL 和 PostgreSQL 完成完整脚本导入及原文存储验证，其他实际数据库尚未覆盖。

该表通过主键查询即可满足首期需求。来源身份、对象类型和本地 ID 沿用映射表中的信息。

`is_current` 显式记录原始内容是否落后。SERIAL 模式中的版本均为 NULL，仅比较版本无法判断原文是否对应当前写入。即使业务内容经历修改后再次恢复，缺少原文的更新也应继续显示“已落后”。

**4. 版本、重复提交与原始记录更新**

现有 `payload_hash` 的计算范围继续是基础快照与本次携带的扩展。原始记录另用 `raw_hash` 判重，这样原文格式变化或未映射字段变化可以单独留存。

下表以来源允许省略原始记录为前提。

| 业务写入结果 | 本次原始记录 | 原始记录处理 |
| --- | --- | --- |
| CREATED / UPDATED | 已携带 | 插入或替换原文，同时记录当前业务版本、摘要及操作人，设置 `is_current=1` |
| CREATED | 未携带 | 创建来源映射，原始记录状态为“未留存” |
| UPDATED | 未携带 | 保留上一份原文和留存版本，将已有记录设置为 `is_current=0` |
| UNCHANGED | 已携带 | 允许首次补存原文，也允许更新原文变化或恢复“当前”状态 |
| UNCHANGED | 未携带 | 保留已有原文及当前状态 |
| STALE_IGNORED | 任意 | 返回旧版本忽略结果，保留已有原文 |
| 同版本业务内容冲突、来源身份退休或其他写入失败 | 任意 | 按现有规则拒绝，事务回滚 |

UNCHANGED 分支已改造原有提前返回逻辑。原文发生变化时，仅更新原始记录并记录必需审计，返回值继续使用 UNCHANGED 表示基础资料和扩展未发生写入。原文、对应版本、业务摘要及当前状态均一致时跳过实际写入，保留原 `recorded_at`。

VERSIONED 模式允许“同版本、同业务快照、原文有变化”的原文更新。这是本方案的明确选择，`sourceVersion` 冲突判断继续覆盖原有业务快照范围，原文留存按成功调用顺序更新。若业务需要对全部原始字段执行严格版本冲突判断，应另行调整版本契约。

SERIAL 模式同样支持原文变化时更新，调用方继续负责同一对象的调用顺序。

组织父级后到引发的本地路径和层级重算属于派生结构维护，子组织已留存的原始记录及其留存时间保持对应原始调用的内容。

**5. 配置与事务**

在现有 `workflow.identity` 下增加大小限制，在每个来源下增加是否要求原始记录的配置。以下为实际默认值，来源示例显式启用必传。

```yaml
workflow:
  identity:
    raw-data-max-bytes: 262144
    raw-data-max-batch-bytes: 8388608
    sources:
      project-directory:
        raw-data-required: true
```

单条上限为 256 KiB，整批原文合计上限为 8 MiB，均按 UTF-8 字节计算。批次仍遵守现有最多 100 条的限制。大小限制属于入口资源校验，也覆盖最终被判定为旧版本的输入。超限时拒绝整次写入，并使用 `rawDataJson` 或 `models[index].rawDataJson` 标明错误位置。

`raw-data-required` 默认 false，以兼容现有接入。来源完成改造后可配置 true，此时 CREATED、UPDATED、UNCHANGED 调用缺少原文均返回 `RAW_DATA_REQUIRED`。STALE_IGNORED 分支直接沿用旧版本忽略结果。已携带的原文在允许接收的分支中始终执行格式及大小校验。

原文写入复用现有 READ_COMMITTED 事务和目录行锁，与基础资料、来源映射、扩展、新用户默认角色及必需审计一起提交。原文保存失败会回滚业务写入，业务或审计失败也会回滚原文。批次大小校验先于逐条写入，批内任一条失败时整批回滚。

原文更新审计只记录来源键、本地对象、来源版本、摘要、字节数和操作人。输入错误信息避免拼接正文，JSON 正文由专用查询读取。

**6. 查询与页面展示**

已提供按需查询接口。

```text
GET /api/system/identity/source-snapshots/{target}/{localId}
```

`target` 使用 USER 或 ORGANIZATION。服务端先验证登录态、对应对象的查看及管理权限，再复用对象级组织范围校验，最后按来源映射键读取原文。来源免写权限配置只控制写入入口，原文查看仍执行上述授权。

用户查询要求 `system:user:view` 和 `system:user:manage`，组织查询要求 `system:organization:view` 和 `system:organization:manage`。原文可能含有未映射到产品页面的业务字段，因此首期将入口放在有管理权限的对象详情中。

响应包含来源编码、外部 ID、当前业务版本、原文对应版本、留存时间、操作人、字节数、原文摘要和以下状态。

| 状态 | 含义 |
| --- | --- |
| UNAVAILABLE | 该来源身份尚未留存原始记录 |
| CURRENT | 原文对应当前已接受的业务写入 |
| STALE | 原文之后的业务更新缺少原文，当前显示的是上一次留存内容 |

正文使用 JSON 字符串返回，以保留原始数字精度，两个版本字段也用字符串传输。用户查看/编辑、组织编辑页面增加“来源原始数据”只读入口，点击后加载并直接用 `<pre>` 文本插值展示原字符串，不解析、重排或执行其中内容。关闭窗口、切换对象和丢失权限时清理正文并丢弃旧响应。

列表、组织树、身份目录及一般用户详情只加载原有数据，原文通过该专用入口查询。

业务接入方应在提交前剔除口令、令牌等认证凭据。留存范围为该条用户或组织的业务原始记录。

**7. 迁移与实施顺序**

旧数据初始显示“未留存”，在下一次携带原文的成功调用中补存，包括业务内容未变化的重复调用。本地表中已有资料只能用于业务展示，原始记录由接入方再次提交。

本地停用、逻辑删除或来源退休时，原始记录随来源映射保留。物理清理来源映射时，应在同一清理事务中删除对应原始记录。

本次实施包括下列部分。

1. 增加保存模型字段及兼容构造方法，更新 `ProjectIdentityImporter` 的调用示例和来源配置注释。
2. 新增原始记录表及持久化逻辑，改造单条和批量保存的 CREATED、UPDATED、UNCHANGED 分支。
3. 增加受控查询和管理详情只读入口，展示未留存、当前及已落后三种状态。
4. 补充验证，更新现有身份写入文档、七种数据库初始化脚本及数据库字段设计文档。

实施前检查 SQL / Java 迁移的最大版本为 V130，新增 `V131__identity_source_snapshot.java`，没有修改历史迁移。七份初始化脚本均同步至 V131，共 200 表、2,792 字段；实际验证结果记录在 [SQL 说明](../sql/README.md)。

**8. 验收范围**

- 原有四参数调用及 Builder 调用继续编译，新接入可传入完整原始 JSON。
- 用户与组织、单条与批量均可留存原文，嵌套字段、中文、大整数及精确小数的原文保持完整。
- 省略、空字符串、非法 JSON、重复键、单条和批次超限均按约定处理，错误可定位到批次条目。
- UNCHANGED 可以补存和更新原文，相同原文重试保持幂等，原文更新也执行必需审计。
- 旧版本不会覆盖原文，同版本业务冲突回滚，SERIAL 按调用顺序更新原文。
- 缺少原文的业务更新保留上一份并标记已落后，后续补存能恢复为当前，业务内容恢复为旧值时仍正确展示落后状态。
- 原文写入失败、扩展失败、默认角色失败、审计失败及批次后续条目失败均保证整次事务回滚。
- 同一来源身份并发首次写入、重试及父级后到场景保留正确原文，派生组织结构更新保持原始调用信息。
- 查询检查管理权限与对象范围，普通列表查询和审计记录避免加载或输出原文。
- 实际迁移与七种初始化脚本结构一致，验证结果明确区分静态检查和目标数据库执行结果。

相关实现依据见 [身份写入设计](identity-write-and-extension-design.md)、[保存模型](../../workflow-server/workflow-port/src/main/java/com/workflow/contracts/identity/model/UserSaveModel.java)、[身份写入服务](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/provisioning/application/IdentityWriteService.java) 和 [来源访问校验](../../workflow-server/workflow-admin/src/main/java/com/workflow/admin/identity/provisioning/application/IdentityAccessService.java)。
