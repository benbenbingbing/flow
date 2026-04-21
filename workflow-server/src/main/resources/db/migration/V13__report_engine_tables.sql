-- 报表定义表
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

-- 报表分类表
CREATE TABLE IF NOT EXISTS report_category (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64) DEFAULT '0',
    category_code VARCHAR(100) UNIQUE NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id)
) COMMENT='报表分类';

-- 报表数据集定义
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

-- 报表订阅/定时任务
CREATE TABLE IF NOT EXISTS report_schedule (
    id VARCHAR(64) PRIMARY KEY,
    report_id VARCHAR(64) NOT NULL,
    schedule_name VARCHAR(200),
    cron_expression VARCHAR(100) NOT NULL,
    param_values JSON COMMENT '参数值',
    export_format VARCHAR(20) DEFAULT 'EXCEL' COMMENT '导出格式',
    notify_type VARCHAR(20) DEFAULT 'EMAIL' COMMENT '通知类型',
    notify_targets TEXT COMMENT '通知目标',
    last_run_time DATETIME,
    next_run_time DATETIME,
    status VARCHAR(20) DEFAULT 'ENABLED',
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_next_run (next_run_time, status)
) COMMENT='报表定时任务';

-- 初始化报表分类
INSERT INTO report_category (id, parent_id, category_code, category_name, sort_order) VALUES
('1', '0', 'DEFAULT', '默认分类', 1),
('2', '0', 'FINANCE', '财务报表', 2),
('3', '0', 'SALES', '销售报表', 3),
('4', '0', 'OPERATION', '运营报表', 4);
