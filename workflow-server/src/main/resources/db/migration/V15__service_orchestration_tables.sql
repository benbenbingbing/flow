-- 服务定义表
CREATE TABLE IF NOT EXISTS service_definition (
    id VARCHAR(64) PRIMARY KEY COMMENT '服务ID',
    service_code VARCHAR(100) UNIQUE NOT NULL COMMENT '服务编码',
    service_name VARCHAR(200) NOT NULL COMMENT '服务名称',
    service_type VARCHAR(20) DEFAULT 'ORCHESTRATION' COMMENT '服务类型：ORCHESTRATION编排/SCRIPT脚本/PROXY代理',
    category_id VARCHAR(64) COMMENT '分类ID',
    description TEXT COMMENT '服务描述',
    input_params JSON COMMENT '输入参数定义',
    output_params JSON COMMENT '输出参数定义',
    flow_config JSON COMMENT '流程配置（DAG）',
    variables JSON COMMENT '变量定义',
    timeout_ms INT DEFAULT 30000 COMMENT '超时时间（毫秒）',
    retry_config JSON COMMENT '重试配置',
    exception_handler JSON COMMENT '异常处理配置',
    version INT DEFAULT 1 COMMENT '版本号',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (service_code),
    INDEX idx_category (category_id)
) COMMENT='服务定义表';

-- 服务节点定义表
CREATE TABLE IF NOT EXISTS service_node (
    id VARCHAR(64) PRIMARY KEY,
    service_id VARCHAR(64) NOT NULL COMMENT '服务ID',
    node_id VARCHAR(100) NOT NULL COMMENT '节点唯一标识',
    node_type VARCHAR(50) NOT NULL COMMENT '节点类型：START/END/ENTITY_CRUD/HTTP/SQL/SCRIPT/CONDITION/PARALLEL/JOIN/LOOP/SUBFLOW/PROCESS/MESSAGE/DELAY/MAPPING/LOG',
    node_name VARCHAR(200) COMMENT '节点名称',
    position_x DECIMAL(10,2) COMMENT '画布X坐标',
    position_y DECIMAL(10,2) COMMENT '画布Y坐标',
    config JSON NOT NULL COMMENT '节点配置',
    input_mapping JSON COMMENT '输入参数映射',
    output_mapping JSON COMMENT '输出参数映射',
    next_nodes JSON COMMENT '下游节点ID列表',
    condition_expression TEXT COMMENT '条件表达式',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service (service_id),
    UNIQUE KEY uk_service_node (service_id, node_id)
) COMMENT='服务节点表';

-- 服务执行记录表
CREATE TABLE IF NOT EXISTS service_execution_log (
    id VARCHAR(64) PRIMARY KEY,
    service_id VARCHAR(64) NOT NULL COMMENT '服务ID',
    execution_id VARCHAR(100) NOT NULL COMMENT '执行实例ID',
    trigger_type VARCHAR(20) COMMENT '触发类型：MANUAL手动/SCHEDULE定时/EVENT事件/API接口',
    trigger_source VARCHAR(200) COMMENT '触发来源',
    input_params TEXT COMMENT '输入参数',
    output_result TEXT COMMENT '输出结果',
    status VARCHAR(20) NOT NULL COMMENT '状态：RUNNING运行中/SUCCESS成功/FAILED失败/TIMEOUT超时',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration_ms INT COMMENT '执行耗时（毫秒）',
    node_executions JSON COMMENT '节点执行详情',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service (service_id, start_time),
    INDEX idx_execution (execution_id),
    INDEX idx_status (status)
) COMMENT='服务执行记录表';

-- 服务分类表
CREATE TABLE IF NOT EXISTS service_category (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64) DEFAULT '0',
    category_code VARCHAR(100) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id)
) COMMENT='服务分类表';

-- 初始化服务分类
INSERT INTO service_category (id, parent_id, category_code, category_name, sort_order) VALUES
('1', '0', 'DEFAULT', '默认分类', 1),
('2', '0', 'BUSINESS', '业务服务', 2),
('3', '0', 'INTEGRATION', '集成服务', 3),
('4', '0', 'UTILITY', '工具服务', 4);
