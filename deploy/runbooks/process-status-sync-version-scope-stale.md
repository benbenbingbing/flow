# 流程结束状态同步：数据版本配置过期

适用于 `PROCESS_STATUS_SYNC` 消费抛出 `ENTITY_VERSION_SCOPE_STALE`，日志提示“实体发布版本已变化，请重新保存数据版本配置后再固化”。

## 原因与行为

数据版本配置保存时冻结实体发布版本及组成关系路径。之后实体重新发布，旧冻结范围失效；流程结束同步命中版本生成场景时，快照校验拒绝写入。实体更新失败，异常继续传播使流程关联关闭及同步审计回滚。不能忽略异常或把事件直接改为 `PROCESSED`，否则会掩盖未完成的状态同步和缺失的版本。

修复后，此错误首次消费失败即进入 `DEAD`；其他可恢复故障继续按 Outbox 的指数退避及次数上限重试。对账只补建缺失的结束事件，不清零已有事件的次数、不提前重试、不复活死信。死信保留在原表并由已有队列监控告警。保存配置本身不会重放死信，需要完成下面的显式恢复。

## 定位与恢复（MySQL 示例）

1. 先部署修复到所有消费和调度实例，避免旧实例继续把失败事件清零重投。
2. 查询并留存原记录，核对 `payload_document` 中的 `entityCode`、`entityRecordId`、`processInstanceId`。下面的 ID 来自本次截图；处理其他事件必须替换为实际 ID。

```sql
SELECT id, topic, event_key, aggregate_id, status, retry_count, max_retries,
       next_retry_time, error_message, payload_document, update_time
FROM workflow_outbox_event
WHERE id = '2101924327581589505'
  AND topic = 'PROCESS_STATUS_SYNC';
```

3. 打开载荷对应实体的“数据版本配置”，确认根实体字段及全部组成关系范围符合最新发布定义后重新保存。截图中的根实体名称为“zdw-需求管理”，实际编码以载荷为准。若范围引用了已删除字段或关系，先调整配置并通过保存校验；不要直接修改冻结 JSON，也不要禁用版本生成来绕过约束。
4. 留存上述查询结果后，仅对已确认修复的这条失败事件重放。保持 ID、事件键和原始载荷不变，利用原有消费幂等机制；不修改正在处理或已经成功的记录。以下示例保留累计失败次数和错误文本，只追加最多 20 次尝试的预算，便于审计此前故障。

```sql
UPDATE workflow_outbox_event
SET status = 'PENDING',
    max_retries = GREATEST(COALESCE(max_retries, 0), COALESCE(retry_count, 0) + 20),
    next_retry_time = NULL,
    owner_id = NULL,
    lease_until = NULL,
    processed_time = NULL,
    update_time = UTC_TIMESTAMP(6)
WHERE id = '2101924327581589505'
  AND topic = 'PROCESS_STATUS_SYNC'
  AND status IN ('FAILED', 'DEAD');
```

5. 确认只更新一条记录；若更新数为 0，先重新查询状态，不扩大条件。等待正常 Outbox 轮询，确认事件为 `PROCESSED`、`process_status_sync_event.state` 为 `APPLIED`、对应 `entity_process_link.state` 为 `ENDED`，并核对业务记录状态和应生成的数据版本。如果再次失败，按新的错误原因处理，不循环执行重放 SQL。

以上步骤属于运行数据恢复，不是 Flyway 结构迁移。本次修复不新增、修改或删除迁移文件，也不执行 `flyway repair`。
