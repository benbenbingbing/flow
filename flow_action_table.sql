-- 流程动作配置表创建脚本
-- 在MySQL中执行此脚本创建flow_action表

CREATE TABLE IF NOT EXISTS flow_action (
    id VARCHAR(64) PRIMARY KEY,
    process_config_id VARCHAR(64) NOT NULL COMMENT '流程定义配置ID',
    sequence_flow_id VARCHAR(64) NOT NULL COMMENT '顺序流ID（bpmn元素ID）',
    action_name VARCHAR(100) NOT NULL COMMENT '动作名称',
    description VARCHAR(500) COMMENT '动作描述',
    interface_name VARCHAR(200) NOT NULL COMMENT '接口名称（Spring Bean或类名）',
    method_name VARCHAR(50) DEFAULT 'execute' COMMENT '方法名',
    params_json TEXT COMMENT '参数JSON',
    sort_order INT DEFAULT 0 COMMENT '执行顺序',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/DISABLED',
    version_id VARCHAR(64) COMMENT '所属版本ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(64) COMMENT '创建人',
    INDEX idx_process_config (process_config_id),
    INDEX idx_sequence_flow (process_config_id, sequence_flow_id),
    INDEX idx_version (version_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程动作配置表';
