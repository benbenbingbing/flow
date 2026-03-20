-- ========================================================  
-- 实体流程状态映射表
-- 说明: 存储流程节点流转时对应的实体数据状态
-- 用于在流程审批过程中自动更新实体数据的状态字段
-- ========================================================

DROP TABLE IF EXISTS `entity_flow_status_mapping`;

CREATE TABLE `entity_flow_status_mapping` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_config_id` VARCHAR(64) NOT NULL COMMENT '流程定义配置ID',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程标识',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `sequence_flow_id` VARCHAR(100) COMMENT '连线ID（BPMN中的sequenceFlowId）',
    `source_node_id` VARCHAR(100) NOT NULL COMMENT '源节点ID（如:startEvent1, userTask1）',
    `source_node_name` VARCHAR(200) COMMENT '源节点名称',
    `target_node_id` VARCHAR(100) NOT NULL COMMENT '目标节点ID',
    `target_node_name` VARCHAR(200) COMMENT '目标节点名称',
    `entity_status` VARCHAR(100) NOT NULL COMMENT '实体数据状态值（如:审批中、已通过、已驳回）',
    `entity_status_code` VARCHAR(50) COMMENT '实体数据状态编码（用于系统识别）',
    `status_category` VARCHAR(50) COMMENT '状态分类：NEW-新建流程状态、PROCESSING-审批中流程状态、COMPLETED-已完成流程状态、TERMINATED-终止流程状态',
    `condition_expression` VARCHAR(500) COMMENT '条件表达式（如:${action=='approve'}）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `description` VARCHAR(500) COMMENT '说明描述',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_config` (`process_config_id`),
    KEY `idx_process_key` (`process_key`),
    KEY `idx_entity_code` (`entity_code`),
    KEY `idx_source_node` (`source_node_id`),
    KEY `idx_target_node` (`target_node_id`),
    UNIQUE KEY `uk_process_source_target` (`process_config_id`, `source_node_id`, `target_node_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体流程状态映射表';

-- ========================================================  
-- 实体数据状态历史记录表（可选，用于追踪状态变更历史）
-- ========================================================

DROP TABLE IF EXISTS `entity_status_history`;

CREATE TABLE `entity_status_history` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_data_id` VARCHAR(64) NOT NULL COMMENT '实体数据ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `process_instance_id` VARCHAR(64) COMMENT '流程实例ID',
    `from_status` VARCHAR(100) COMMENT '变更前状态',
    `to_status` VARCHAR(100) NOT NULL COMMENT '变更后状态',
    `from_node_id` VARCHAR(100) COMMENT '来源节点ID',
    `to_node_id` VARCHAR(100) COMMENT '目标节点ID',
    `operator_id` VARCHAR(64) COMMENT '操作人ID',
    `operator_name` VARCHAR(100) COMMENT '操作人姓名',
    `operation_type` VARCHAR(50) COMMENT '操作类型：AUTO-自动流转, MANUAL-人工审批',
    `remark` VARCHAR(500) COMMENT '备注说明',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_entity_data` (`entity_data_id`),
    KEY `idx_entity_code` (`entity_code`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体数据状态历史记录表';
