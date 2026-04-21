-- 工作台配置表
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

-- 快捷入口表
CREATE TABLE IF NOT EXISTS workbench_shortcut (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    shortcut_name VARCHAR(200) NOT NULL,
    shortcut_type VARCHAR(50) COMMENT '类型：MENU/URL/ENTITY',
    target_id VARCHAR(200) COMMENT '目标ID（菜单ID或URL）',
    icon VARCHAR(100),
    color VARCHAR(20),
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, sort_order)
) COMMENT='快捷入口表';

-- 插入默认工作台配置
INSERT INTO workbench_config (id, config_name, config_code, user_id, layout_type, layout_config, is_default, is_system, status) VALUES
('1', '默认工作台', 'DEFAULT', NULL, 'GRID', '[
  {"id": "widget-todo", "type": "TODO_LIST", "title": "待办任务", "x": 0, "y": 0, "w": 6, "h": 4},
  {"id": "widget-stat", "type": "STATISTICS", "title": "数据统计", "x": 6, "y": 0, "w": 6, "h": 2},
  {"id": "widget-shortcut", "type": "SHORTCUT", "title": "快捷入口", "x": 6, "y": 2, "w": 6, "h": 2},
  {"id": "widget-notice", "type": "NOTICE", "title": "系统公告", "x": 0, "y": 4, "w": 4, "h": 3},
  {"id": "widget-calendar", "type": "CALENDAR", "title": "工作日历", "x": 4, "y": 4, "w": 4, "h": 3},
  {"id": "widget-recent", "type": "RECENT", "title": "最近使用", "x": 8, "y": 4, "w": 4, "h": 3}
]', 1, 1, 'ACTIVE');
