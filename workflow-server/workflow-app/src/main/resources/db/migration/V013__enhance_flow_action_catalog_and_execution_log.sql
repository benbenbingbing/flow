CREATE TABLE flow_action_definition (
    id varchar(64) NOT NULL,
    action_code varchar(200) NOT NULL COMMENT '稳定动作编码，默认使用Spring Bean名称',
    display_name varchar(200) NOT NULL COMMENT '动作中文名称',
    description varchar(1000) DEFAULT NULL COMMENT '动作用途说明',
    handler_name varchar(200) NOT NULL COMMENT 'FlowActionHandler Bean名称',
    visibility_scope varchar(20) NOT NULL DEFAULT 'ENTITY' COMMENT '可见范围：GLOBAL/ENTITY',
    entity_codes_json text DEFAULT NULL COMMENT 'ENTITY范围可见的实体编码JSON数组',
    enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否允许在流程设计器中选择',
    created_by varchar(64) DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_action_definition_code (action_code),
    UNIQUE KEY uk_flow_action_definition_handler (handler_name),
    KEY idx_flow_action_definition_scope (visibility_scope, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程动作处理器目录';

INSERT INTO flow_action_definition (
    id, action_code, display_name, description, handler_name,
    visibility_scope, entity_codes_json, enabled, created_by
)
SELECT
    REPLACE(UUID(), '-', ''),
    action_source.interface_name,
    COALESCE(MAX(NULLIF(action_source.action_name, '')), action_source.interface_name),
    MAX(action_source.description),
    action_source.interface_name,
    CASE WHEN COUNT(entity_definition.entity_code) = 0 THEN 'GLOBAL' ELSE 'ENTITY' END,
    CASE
        WHEN COUNT(entity_definition.entity_code) = 0 THEN NULL
        ELSE CONCAT(
            '[',
            GROUP_CONCAT(DISTINCT JSON_QUOTE(entity_definition.entity_code) ORDER BY entity_definition.entity_code SEPARATOR ','),
            ']'
        )
    END,
    1,
    'migration'
FROM flow_action action_source
LEFT JOIN entity_definition
       ON entity_definition.process_definition_id COLLATE utf8mb4_unicode_ci
        = action_source.process_config_id COLLATE utf8mb4_unicode_ci
      AND entity_definition.deleted = 0
WHERE action_source.deleted = 0
  AND action_source.interface_name IS NOT NULL
  AND action_source.interface_name <> ''
GROUP BY action_source.interface_name;

INSERT INTO flow_action_definition (
    id, action_code, display_name, description, handler_name,
    visibility_scope, entity_codes_json, enabled, created_by
)
SELECT
    'flow_action_definition_notify',
    'sendNotificationHandler',
    '发送流程通知',
    '发送待办、完成、撤回等流程通知；推荐使用提交后执行。',
    'sendNotificationHandler',
    'GLOBAL',
    NULL,
    1,
    'system'
WHERE NOT EXISTS (
    SELECT 1
    FROM flow_action_definition
    WHERE handler_name = 'sendNotificationHandler'
);

INSERT INTO flow_action_definition (
    id, action_code, display_name, description, handler_name,
    visibility_scope, entity_codes_json, enabled, created_by
)
SELECT
    REPLACE(UUID(), '-', ''),
    demo.handler_name,
    demo.display_name,
    demo.description,
    demo.handler_name,
    'ENTITY',
    '[]',
    0,
    'system'
FROM (
    SELECT 'demoSimpleActionHandler' handler_name, '演示：通用流程动作' display_name, '开发演示处理器，生产环境默认禁用。' description
    UNION ALL
    SELECT 'demoTypedActionHandler', '演示：类型化流程动作', '类型化参数开发演示，生产环境默认禁用。'
    UNION ALL
    SELECT 'demoFailingActionHandler', '演示：失败流程动作', '失败与重试测试处理器，生产环境默认禁用。'
) demo
WHERE NOT EXISTS (
    SELECT 1
    FROM flow_action_definition existing_definition
    WHERE existing_definition.handler_name COLLATE utf8mb4_unicode_ci
        = demo.handler_name COLLATE utf8mb4_unicode_ci
);

ALTER TABLE flow_action
    ADD COLUMN action_definition_id varchar(64) DEFAULT NULL COMMENT '动作定义目录ID' AFTER retry_config;

UPDATE flow_action action_config
JOIN flow_action_definition action_definition
  ON action_definition.handler_name COLLATE utf8mb4_unicode_ci
   = action_config.interface_name COLLATE utf8mb4_unicode_ci
SET action_config.action_definition_id = action_definition.id
WHERE action_config.action_definition_id IS NULL;

CREATE INDEX idx_flow_action_definition_id
    ON flow_action(action_definition_id, status, deleted);

ALTER TABLE flow_action_execution
    ADD COLUMN action_name varchar(200) DEFAULT NULL COMMENT '动作名称快照' AFTER action_id,
    ADD COLUMN handler_name varchar(200) DEFAULT NULL COMMENT '处理器Bean名称快照' AFTER action_name,
    ADD COLUMN handler_display_name varchar(200) DEFAULT NULL COMMENT '处理器中文名称快照' AFTER handler_name,
    ADD COLUMN entity_code varchar(100) DEFAULT NULL COMMENT '实体编码快照' AFTER task_id,
    ADD COLUMN resolved_params_json text DEFAULT NULL COMMENT '表达式解析后的动作参数' AFTER payload_json,
    ADD COLUMN result_json text DEFAULT NULL COMMENT '动作执行结果' AFTER resolved_params_json,
    ADD COLUMN execution_trace_json mediumtext DEFAULT NULL COMMENT '结构化执行步骤轨迹' AFTER result_json,
    ADD COLUMN error_stack mediumtext DEFAULT NULL COMMENT '异常堆栈' AFTER error_message,
    ADD COLUMN duration_ms bigint DEFAULT NULL COMMENT '执行耗时毫秒' AFTER finished_at;

CREATE INDEX idx_flow_action_execution_entity
    ON flow_action_execution(entity_code, process_instance_id, created_at);
