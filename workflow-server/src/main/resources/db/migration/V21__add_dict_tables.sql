-- ============================================
-- V21: 添加字典管理表及菜单
-- ============================================

-- 字典类型表
CREATE TABLE IF NOT EXISTS sys_dict (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    dict_code VARCHAR(100) NOT NULL COMMENT '字典编码',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
    description VARCHAR(500) COMMENT '描述',
    status CHAR(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
    sort INT DEFAULT 0 COMMENT '排序',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_dict_code (dict_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 字典明细表
CREATE TABLE IF NOT EXISTS sys_dict_item (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    dict_id VARCHAR(64) NOT NULL COMMENT '所属字典ID',
    dict_code VARCHAR(100) NOT NULL COMMENT '冗余：字典编码（便于直接查询）',
    parent_id VARCHAR(64) DEFAULT '0' COMMENT '父项ID，0表示顶级',
    item_code VARCHAR(100) NOT NULL COMMENT '项编码',
    item_label VARCHAR(100) NOT NULL COMMENT '项标签/显示文本',
    item_value VARCHAR(200) NOT NULL COMMENT '项值',
    sort INT DEFAULT 0 COMMENT '排序',
    status CHAR(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_dict_id (dict_id),
    INDEX idx_dict_code (dict_code),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典明细表';

-- 在系统管理下添加「字典设置」菜单
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible, deleted)
SELECT 'dict_mgmt', id, '字典设置', 'C', 'Collection', 6, '/system/dict', '/views/system/Dict.vue', 'system:dict:list', '0', '0', 0
FROM sys_menu WHERE menu_name = '系统管理' AND deleted = 0 AND parent_id = '0';
