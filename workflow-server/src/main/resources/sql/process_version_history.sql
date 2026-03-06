-- 流程版本历史表
CREATE TABLE IF NOT EXISTS process_version_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    process_config_id BIGINT NOT NULL COMMENT '流程定义ID',
    process_key VARCHAR(100) NOT NULL COMMENT '流程标识',
    process_name VARCHAR(200) NOT NULL COMMENT '流程名称',
    version INT NOT NULL COMMENT '版本号',
    version_description VARCHAR(500) COMMENT '版本描述/发布说明',
    bpmn_xml TEXT COMMENT 'BPMN XML内容',
    published_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    published_by VARCHAR(64) COMMENT '发布人ID',
    deployment_id VARCHAR(64) COMMENT 'Flowable部署ID',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-有效，ARCHIVED-已归档',
    INDEX idx_process_config_id (process_config_id),
    INDEX idx_process_key (process_key),
    INDEX idx_version (version),
    INDEX idx_deployment_id (deployment_id),
    UNIQUE KEY uk_process_version (process_config_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程版本历史表';
