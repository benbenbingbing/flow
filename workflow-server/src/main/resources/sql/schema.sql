-- 工作流数据库初始化脚本
-- 数据库: workflow
-- 字符集: utf8mb4

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS workflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE workflow;

-- 实体定义表
CREATE TABLE IF NOT EXISTS entity_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    entity_code VARCHAR(100) NOT NULL UNIQUE COMMENT '实体编码',
    entity_name VARCHAR(200) NOT NULL COMMENT '实体名称',
    description VARCHAR(500) COMMENT '实体描述',
    process_definition_id VARCHAR(64) COMMENT '关联流程定义ID',
    enable_process TINYINT(1) DEFAULT 0 COMMENT '是否启用流程',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用',
    created_by VARCHAR(64) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_entity_code (entity_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体定义表';

-- 实体字段表
CREATE TABLE IF NOT EXISTS entity_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    entity_id BIGINT NOT NULL COMMENT '所属实体ID',
    field_code VARCHAR(100) NOT NULL COMMENT '字段编码',
    field_name VARCHAR(200) NOT NULL COMMENT '字段名称',
    field_type VARCHAR(50) NOT NULL COMMENT '字段类型',
    db_type VARCHAR(50) COMMENT '数据库字段类型',
    field_length INT COMMENT '字段长度',
    is_required TINYINT(1) DEFAULT 0 COMMENT '是否必填',
    is_unique TINYINT(1) DEFAULT 0 COMMENT '是否唯一',
    default_value VARCHAR(500) COMMENT '默认值',
    options_json TEXT COMMENT '选项配置JSON',
    validate_rules TEXT COMMENT '验证规则JSON',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    show_in_list TINYINT(1) DEFAULT 1 COMMENT '是否在列表显示',
    show_in_form TINYINT(1) DEFAULT 1 COMMENT '是否在表单显示',
    is_query TINYINT(1) DEFAULT 0 COMMENT '是否查询条件',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_entity_id (entity_id),
    INDEX idx_field_code (field_code),
    UNIQUE KEY uk_entity_field (entity_id, field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体字段表';

-- 流程定义配置表
CREATE TABLE IF NOT EXISTS process_definition_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    process_key VARCHAR(100) NOT NULL UNIQUE COMMENT '流程标识',
    process_name VARCHAR(200) NOT NULL COMMENT '流程名称',
    description VARCHAR(500) COMMENT '流程描述',
    category VARCHAR(100) COMMENT '流程分类',
    version INT DEFAULT 1 COMMENT '版本号',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用',
    bpmn_xml TEXT COMMENT 'BPMN XML内容',
    created_by VARCHAR(64) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_process_key (process_key),
    INDEX idx_status (status),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义配置表';

-- 流程节点配置表
CREATE TABLE IF NOT EXISTS node_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    node_id VARCHAR(100) NOT NULL COMMENT '节点ID',
    node_name VARCHAR(200) NOT NULL COMMENT '节点名称',
    node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
    process_config_id BIGINT NOT NULL COMMENT '所属流程配置ID',
    config_json TEXT COMMENT '扩展配置JSON',
    skip_node TINYINT(1) DEFAULT 0 COMMENT '是否跳过此节点（仅第一个用户任务节点可设置）：0-不跳过，1-跳过',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_process_config_id (process_config_id),
    INDEX idx_node_id (node_id),
    INDEX idx_skip_node (skip_node)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点配置表';

-- 审批人配置表
CREATE TABLE IF NOT EXISTS assignee_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    node_config_id BIGINT NOT NULL COMMENT '所属节点配置ID',
    assignee_type VARCHAR(50) NOT NULL COMMENT '审批人类型',
    assignee_value VARCHAR(200) NOT NULL COMMENT '审批人值',
    assignee_name VARCHAR(200) COMMENT '审批人显示名称',
    priority INT DEFAULT 0 COMMENT '优先级',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_node_config_id (node_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批人配置表';

-- 表单配置表
CREATE TABLE IF NOT EXISTS form_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    node_config_id BIGINT NOT NULL COMMENT '所属节点配置ID',
    form_name VARCHAR(200) NOT NULL COMMENT '表单名称',
    form_key VARCHAR(100) NOT NULL COMMENT '表单标识',
    description VARCHAR(500) COMMENT '表单描述',
    is_readonly TINYINT(1) DEFAULT 0 COMMENT '是否只读',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_node_config_id (node_config_id),
    INDEX idx_form_key (form_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单配置表';

-- 表单字段配置表
CREATE TABLE IF NOT EXISTS form_field_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    form_config_id BIGINT NOT NULL COMMENT '所属表单配置ID',
    field_name VARCHAR(200) NOT NULL COMMENT '字段名称',
    field_key VARCHAR(100) NOT NULL COMMENT '字段标识',
    field_type VARCHAR(50) NOT NULL COMMENT '字段类型',
    is_required TINYINT(1) DEFAULT 0 COMMENT '是否必填',
    default_value VARCHAR(500) COMMENT '默认值',
    options_json TEXT COMMENT '选项配置JSON',
    validate_rules TEXT COMMENT '验证规则JSON',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_form_config_id (form_config_id),
    INDEX idx_field_key (field_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单字段配置表';

-- 实体数据表
CREATE TABLE IF NOT EXISTS entity_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    entity_code VARCHAR(100) NOT NULL COMMENT '实体编码',
    data_no VARCHAR(100) COMMENT '数据编号',
    title VARCHAR(500) COMMENT '数据标题',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态',
    process_instance_id VARCHAR(64) COMMENT '流程实例ID',
    current_task_id VARCHAR(64) COMMENT '当前任务ID',
    current_task_name VARCHAR(200) COMMENT '当前任务名称',
    data_json TEXT COMMENT '数据内容JSON',
    submitter_id VARCHAR(64) COMMENT '提交人ID',
    submitter_name VARCHAR(100) COMMENT '提交人姓名',
    submit_time DATETIME COMMENT '提交时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_entity_code (entity_code),
    INDEX idx_data_no (data_no),
    INDEX idx_process_instance_id (process_instance_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体数据表';
