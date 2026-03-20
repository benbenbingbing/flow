-- ========================================================  
-- V9: 添加实体状态定义表和流程状态映射表
-- 说明: 实体管理中配置状态，流程设计中引用
-- ========================================================

-- 实体状态定义表
CREATE TABLE IF NOT EXISTS `entity_status` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `status_code` VARCHAR(50) NOT NULL COMMENT '状态编码（系统标识）',
    `status_name` VARCHAR(100) NOT NULL COMMENT '状态名称（显示用）',
    `status_category` VARCHAR(50) COMMENT '状态分类：NEW-新建、PROCESSING-审批中、COMPLETED-已完成、TERMINATED-终止',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `description` VARCHAR(500) COMMENT '状态说明',
    `color` VARCHAR(20) COMMENT '状态颜色（如：#67C23A）',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_status` (`entity_code`, `status_code`, `deleted`),
    KEY `idx_entity_code` (`entity_code`),
    KEY `idx_status_category` (`status_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体状态定义表';

-- 实体流程状态映射表
CREATE TABLE IF NOT EXISTS `entity_flow_status_mapping` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_config_id` VARCHAR(64) NOT NULL COMMENT '流程定义配置ID',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程标识',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `sequence_flow_id` VARCHAR(100) COMMENT '连线ID（BPMN中的sequenceFlowId）',
    `source_node_id` VARCHAR(100) NOT NULL COMMENT '源节点ID',
    `source_node_name` VARCHAR(200) COMMENT '源节点名称',
    `target_node_id` VARCHAR(100) NOT NULL COMMENT '目标节点ID',
    `target_node_name` VARCHAR(200) COMMENT '目标节点名称',
    `entity_status_code` VARCHAR(50) NOT NULL COMMENT '实体状态编码（关联entity_status表）',
    `condition_expression` VARCHAR(500) COMMENT '条件表达式',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `description` VARCHAR(500) COMMENT '说明描述',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_source_target` (`process_config_id`, `source_node_id`, `target_node_id`, `deleted`),
    KEY `idx_process_config` (`process_config_id`),
    KEY `idx_process_key` (`process_key`),
    KEY `idx_entity_code` (`entity_code`),
    KEY `idx_source_node` (`source_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体流程状态映射表';
