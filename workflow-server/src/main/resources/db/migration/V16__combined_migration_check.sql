-- 综合迁移检查脚本
-- 如果 Flyway 没有自动执行，可以手动运行此脚本
-- 注意：请先检查哪些表已经存在，避免重复创建

-- ============================================
-- V11: 流程中心表
-- ============================================

-- 流程任务实例表
CREATE TABLE IF NOT EXISTS process_task_instance (
    id VARCHAR(64) PRIMARY KEY COMMENT '任务实例ID',
    process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    task_id VARCHAR(64) COMMENT 'Flowable任务ID',
    task_key VARCHAR(100) COMMENT '任务节点Key',
    task_name VARCHAR(200) COMMENT '任务名称',
    process_definition_id VARCHAR(64) COMMENT '流程定义ID',
    process_name VARCHAR(200) COMMENT '流程名称',
    entity_code VARCHAR(100) COMMENT '关联实体编码',
    entity_data_id VARCHAR(64) COMMENT '关联实体数据ID',
    business_key VARCHAR(200) COMMENT '业务主键',
    assignee_id VARCHAR(64) COMMENT '被指派人ID',
    assignee_name VARCHAR(100) COMMENT '被指派人姓名',
    owner_id VARCHAR(64) COMMENT '任务所有人ID',
    candidate_users TEXT COMMENT '候选人ID列表（JSON）',
    candidate_groups TEXT COMMENT '候选组列表（JSON）',
    task_type VARCHAR(20) COMMENT '任务类型：TODO待办/DONE已办/DRAFT草稿/CC抄送',
    action_type VARCHAR(50) COMMENT '操作类型：SUBMIT/APPROVE/REJECT/TRANSFER/RETURN/DELEGATE',
    action_comment TEXT COMMENT '处理意见',
    form_data JSON COMMENT '表单数据快照',
    due_time DATETIME COMMENT '截止时间',
    priority INT DEFAULT 50 COMMENT '优先级 0-100',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读',
    read_time DATETIME COMMENT '阅读时间',
    start_time DATETIME COMMENT '任务开始时间',
    end_time DATETIME COMMENT '任务结束时间',
    duration_ms BIGINT COMMENT '处理耗时（毫秒）',
    parent_task_id VARCHAR(64) COMMENT '父任务ID（用于会签）',
    root_task_id VARCHAR(64) COMMENT '根任务ID',
    delegation_state VARCHAR(20) COMMENT '委托状态：PENDING/RESOLVED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_process_instance (process_instance_id),
    INDEX idx_assignee_type (assignee_id, task_type),
    INDEX idx_entity (entity_code, entity_data_id),
    INDEX idx_business_key (business_key),
    INDEX idx_start_time (start_time),
    INDEX idx_due_time (due_time),
    INDEX idx_task_type (task_type, is_read)
) COMMENT='流程任务实例表';

-- 常用审批意见表
CREATE TABLE IF NOT EXISTS process_common_opinion (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    opinion_content VARCHAR(500) NOT NULL COMMENT '意见内容',
    opinion_type VARCHAR(20) DEFAULT 'APPROVE' COMMENT '意见类型：APPROVE同意/REJECT驳回/TRANSFER转办',
    sort_order INT DEFAULT 0 COMMENT '排序',
    use_count INT DEFAULT 0 COMMENT '使用次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, sort_order)
) COMMENT='常用审批意见';

-- 流程草稿箱表
CREATE TABLE IF NOT EXISTS process_draft (
    id VARCHAR(64) PRIMARY KEY,
    draft_code VARCHAR(100) UNIQUE COMMENT '草稿编码',
    process_definition_id VARCHAR(64) COMMENT '流程定义ID',
    process_name VARCHAR(200) COMMENT '流程名称',
    entity_code VARCHAR(100) COMMENT '关联实体编码',
    entity_data_id VARCHAR(64) COMMENT '关联实体数据ID（临时数据）',
    business_key VARCHAR(200) COMMENT '业务主键',
    form_data JSON NOT NULL COMMENT '表单数据',
    draft_title VARCHAR(500) COMMENT '草稿标题',
    draft_summary TEXT COMMENT '草稿摘要',
    user_id VARCHAR(64) NOT NULL COMMENT '创建人ID',
    user_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/SUBMITTED已提交/DELETED已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, status, updated_at),
    INDEX idx_process (process_definition_id),
    INDEX idx_entity (entity_code, entity_data_id)
) COMMENT='流程草稿箱';

-- 流程操作日志表
CREATE TABLE IF NOT EXISTS process_operation_log (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC',
    operator_id VARCHAR(64) COMMENT '操作人ID',
    operator_name VARCHAR(100),
    operation_time DATETIME COMMENT '操作时间',
    operation_comment TEXT,
    old_value TEXT COMMENT '旧值（JSON）',
    new_value TEXT COMMENT '新值（JSON）',
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_process (process_instance_id, operation_time),
    INDEX idx_operator (operator_id, operation_time)
) COMMENT='流程操作日志';

-- ============================================
-- V12: 视图引擎表
-- ============================================

CREATE TABLE IF NOT EXISTS view_definition (
    id VARCHAR(64) PRIMARY KEY COMMENT '视图ID',
    view_code VARCHAR(100) UNIQUE NOT NULL COMMENT '视图编码',
    view_name VARCHAR(200) NOT NULL COMMENT '视图名称',
    view_type VARCHAR(20) NOT NULL COMMENT '视图类型：LIST列表/CHART图表/DASHBOARD看板/DETAIL详情',
    entity_code VARCHAR(100) COMMENT '关联实体编码',
    data_source_type VARCHAR(20) DEFAULT 'ENTITY' COMMENT '数据源类型：ENTITY/SQL/API',
    data_source_config JSON COMMENT '数据源配置',
    layout_config JSON COMMENT '布局配置',
    style_config JSON COMMENT '样式配置',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认视图',
    version INT DEFAULT 1 COMMENT '版本号',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_entity (entity_code),
    INDEX idx_code (view_code)
) COMMENT='视图定义表';

CREATE TABLE IF NOT EXISTS view_field_config (
    id VARCHAR(64) PRIMARY KEY,
    view_id VARCHAR(64) NOT NULL,
    field_code VARCHAR(100) NOT NULL COMMENT '字段编码',
    field_name VARCHAR(200) COMMENT '字段显示名',
    field_type VARCHAR(50) COMMENT '字段类型',
    sort_order INT DEFAULT 0 COMMENT '显示顺序',
    width VARCHAR(20) COMMENT '列宽',
    align VARCHAR(10) DEFAULT 'left' COMMENT '对齐方式',
    is_show TINYINT DEFAULT 1 COMMENT '是否显示',
    is_sortable TINYINT DEFAULT 0 COMMENT '是否可排序',
    is_searchable TINYINT DEFAULT 0 COMMENT '是否可搜索',
    formatter_type VARCHAR(50) COMMENT '格式化类型',
    formatter_config JSON COMMENT '格式化配置',
    fixed VARCHAR(10) COMMENT '固定列',
    show_in_list TINYINT DEFAULT 1,
    show_in_detail TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_view (view_id, sort_order)
) COMMENT='视图字段配置表';

CREATE TABLE IF NOT EXISTS view_query_config (
    id VARCHAR(64) PRIMARY KEY,
    view_id VARCHAR(64) NOT NULL,
    field_code VARCHAR(100) NOT NULL,
    field_name VARCHAR(200),
    query_type VARCHAR(50) COMMENT '查询类型：EQ/LIKE/BETWEEN等',
    component_type VARCHAR(50) COMMENT '组件类型',
    component_config JSON COMMENT '组件配置',
    default_value TEXT COMMENT '默认值',
    placeholder VARCHAR(200),
    sort_order INT DEFAULT 0,
    is_advanced TINYINT DEFAULT 0 COMMENT '是否高级查询',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_view (view_id, sort_order)
) COMMENT='视图查询条件配置表';

CREATE TABLE IF NOT EXISTS view_button_config (
    id VARCHAR(64) PRIMARY KEY,
    view_id VARCHAR(64) NOT NULL,
    button_code VARCHAR(100) NOT NULL,
    button_name VARCHAR(200),
    button_type VARCHAR(20) NOT NULL COMMENT '按钮位置：TOOLBAR工具栏/ROW行操作/BATCH批量操作',
    action_type VARCHAR(50) COMMENT '动作类型',
    action_config JSON COMMENT '动作配置',
    icon VARCHAR(100),
    style VARCHAR(50) COMMENT '样式',
    sort_order INT DEFAULT 0,
    visible_condition TEXT COMMENT '显示条件',
    permission_code VARCHAR(200) COMMENT '权限标识',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_view (view_id, button_type, sort_order)
) COMMENT='视图按钮配置表';

-- ============================================
-- V13: 报表引擎表
-- ============================================

CREATE TABLE IF NOT EXISTS report_definition (
    id VARCHAR(64) PRIMARY KEY COMMENT '报表ID',
    report_code VARCHAR(100) UNIQUE NOT NULL COMMENT '报表编码',
    report_name VARCHAR(200) NOT NULL COMMENT '报表名称',
    report_type VARCHAR(20) NOT NULL COMMENT '报表类型：TABLE表格/CHART图表/DASHBOARD大屏/PRINT打印',
    category_id VARCHAR(64) COMMENT '报表分类ID',
    dataset_config JSON COMMENT '数据集配置（支持多数据集）',
    layout_config JSON NOT NULL COMMENT '报表布局配置',
    params_config JSON COMMENT '报表参数配置',
    style_config JSON COMMENT '样式配置',
    permission_config JSON COMMENT '权限配置',
    version INT DEFAULT 1 COMMENT '版本号',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_code (report_code)
) COMMENT='报表定义表';

CREATE TABLE IF NOT EXISTS report_category (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64) DEFAULT '0',
    category_code VARCHAR(100) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id)
) COMMENT='报表分类';

-- 插入默认报表分类
INSERT IGNORE INTO report_category (id, parent_id, category_code, category_name, sort_order) VALUES
('1', '0', 'DEFAULT', '默认分类', 1),
('2', '0', 'FINANCE', '财务报表', 2),
('3', '0', 'SALES', '销售报表', 3),
('4', '0', 'OPERATION', '运营报表', 4);

CREATE TABLE IF NOT EXISTS report_dataset (
    id VARCHAR(64) PRIMARY KEY,
    report_id VARCHAR(64) NOT NULL,
    dataset_code VARCHAR(100) NOT NULL,
    dataset_name VARCHAR(200),
    dataset_type VARCHAR(20) COMMENT '数据集类型：SQL/ENTITY/API',
    source_config JSON NOT NULL COMMENT '数据源配置',
    field_mappings JSON COMMENT '字段映射',
    cache_config JSON COMMENT '缓存配置',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id)
) COMMENT='报表数据集';

-- ============================================
-- V14: 工作台表
-- ============================================

CREATE TABLE IF NOT EXISTS workbench_config (
    id VARCHAR(64) PRIMARY KEY,
    config_name VARCHAR(200) NOT NULL COMMENT '配置名称',
    config_code VARCHAR(100) UNIQUE COMMENT '配置编码',
    user_id VARCHAR(64) COMMENT '用户ID（为空表示系统默认）',
    layout_type VARCHAR(20) DEFAULT 'GRID' COMMENT '布局类型：GRID/FREE',
    layout_config JSON NOT NULL COMMENT '布局配置',
    widgets_config JSON COMMENT '组件配置列表',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认',
    is_system TINYINT DEFAULT 0 COMMENT '是否系统预设',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_default (is_default)
) COMMENT='工作台配置表';

-- 插入默认工作台配置
INSERT IGNORE INTO workbench_config (id, config_name, config_code, user_id, layout_type, layout_config, is_default, is_system, status) VALUES
('1', '默认工作台', 'DEFAULT', NULL, 'GRID', '[{"id": "widget-todo", "type": "TODO_LIST", "title": "待办任务", "x": 0, "y": 0, "w": 6, "h": 4}, {"id": "widget-stat", "type": "STATISTICS", "title": "数据统计", "x": 6, "y": 0, "w": 6, "h": 2}, {"id": "widget-shortcut", "type": "SHORTCUT", "title": "快捷入口", "x": 6, "y": 2, "w": 6, "h": 2}, {"id": "widget-notice", "type": "NOTICE", "title": "系统公告", "x": 0, "y": 4, "w": 4, "h": 3}, {"id": "widget-calendar", "type": "CALENDAR", "title": "工作日历", "x": 4, "y": 4, "w": 4, "h": 3}, {"id": "widget-recent", "type": "RECENT", "title": "最近使用", "x": 8, "y": 4, "w": 4, "h": 3}]', 1, 1, 'ACTIVE');

-- ============================================
-- V15: 服务编排表
-- ============================================

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

CREATE TABLE IF NOT EXISTS service_category (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64) DEFAULT '0',
    category_code VARCHAR(100) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id)
) COMMENT='服务分类表';

-- 插入默认服务分类
INSERT IGNORE INTO service_category (id, parent_id, category_code, category_name, sort_order) VALUES
('1', '0', 'DEFAULT', '默认分类', 1),
('2', '0', 'BUSINESS', '业务服务', 2),
('3', '0', 'INTEGRATION', '集成服务', 3),
('4', '0', 'UTILITY', '工具服务', 4);

-- 检查迁移完成
SELECT '所有表迁移完成' AS result;
