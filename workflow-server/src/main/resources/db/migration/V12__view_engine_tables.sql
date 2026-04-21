-- 视图定义表
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

-- 视图字段配置表
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

-- 视图查询条件配置表
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

-- 视图按钮配置表
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
