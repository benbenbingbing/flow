-- MySQL 存量库升级：在目标业务库执行本文件，连接字符集使用 utf8mb4。
-- 仅补充动作目录种子，不修改表结构；需要已存在 process_action_definition 及其主键、唯一索引。
-- 手动执行后仍可由 Flyway 正常应用并登记 V132，无需手动修改迁移历史。
INSERT INTO process_action_definition (
    id, action_code, display_name, description, handler_name, visibility_scope,
    enabled, created_by, create_time, update_time, deleted
)
SELECT
    'flow_action_definition_field_assignment',
    'entityFieldAssignmentFlowActionHandler',
    '实体字段赋值',
    '实际经过连线时给当前实体的已发布普通字段赋固定值或清空；事务内执行，失败回滚。',
    'entityFieldAssignmentFlowActionHandler',
    'GLOBAL', 1, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
-- 同一编码和处理器已经配置时保留整条记录，包括管理员生成的 ID、停用及逻辑删除设置。
-- Bean 名称区分大小写；二进制比较避免 MySQL 默认不区分大小写的排序规则放过错误处理器。
WHERE NOT EXISTS (
    SELECT 1 FROM process_action_definition
    WHERE CAST(action_code AS BINARY(200)) = CAST('entityFieldAssignmentFlowActionHandler' AS BINARY(200))
      AND CAST(handler_name AS BINARY(200)) = CAST('entityFieldAssignmentFlowActionHandler' AS BINARY(200))
)
-- 即使正确目录已存在，稳定 ID 或编码被其他记录占用也必须报错。
-- 让主键或唯一索引拒绝本次 INSERT，避免把目录冲突当作已成功升级。
OR EXISTS (
    SELECT 1 FROM process_action_definition
    WHERE (id = 'flow_action_definition_field_assignment'
        OR action_code = 'entityFieldAssignmentFlowActionHandler'
        OR handler_name = 'entityFieldAssignmentFlowActionHandler')
      AND (CAST(action_code AS BINARY(200)) <> CAST('entityFieldAssignmentFlowActionHandler' AS BINARY(200))
        OR CAST(handler_name AS BINARY(200)) <> CAST('entityFieldAssignmentFlowActionHandler' AS BINARY(200)))
);
