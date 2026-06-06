-- 菜单管理表
CREATE TABLE IF NOT EXISTS sys_menu (
    id VARCHAR(64) PRIMARY KEY COMMENT '菜单ID',
    parent_id VARCHAR(64) DEFAULT '0' COMMENT '父菜单ID，0为顶级菜单',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    menu_type CHAR(1) DEFAULT 'M' COMMENT '菜单类型：M-目录 C-菜单 F-按钮',
    icon VARCHAR(100) COMMENT '菜单图标',
    sort INT DEFAULT 0 COMMENT '显示排序',
    path VARCHAR(200) COMMENT '路由地址',
    component VARCHAR(255) COMMENT '组件路径',
    perm VARCHAR(200) COMMENT '权限标识，如：system:user:list',
    status CHAR(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
    visible CHAR(1) DEFAULT '0' COMMENT '显示状态：0-显示 1-隐藏',
    is_frame CHAR(1) DEFAULT '0' COMMENT '是否外链：0-否 1-是',
    is_cache CHAR(1) DEFAULT '0' COMMENT '是否缓存：0-缓存 1-不缓存',
    query VARCHAR(255) COMMENT '路由参数',
    keep_alive CHAR(1) DEFAULT '0' COMMENT '是否缓存：0-不缓存 1-缓存',
    breadcrumb CHAR(1) DEFAULT '1' COMMENT '是否显示面包屑：0-否 1-是',
    remark VARCHAR(500) COMMENT '备注',
    deleted INT DEFAULT 0 COMMENT '是否删除：0-未删除 1-已删除',
    create_by VARCHAR(64) COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_parent_id (parent_id),
    INDEX idx_sort (sort),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- 插入默认菜单数据
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, sort, path, component, perm, status, visible) VALUES
('1', '0', '系统管理', 'M', 'Setting', 1, '/system', NULL, NULL, '0', '0'),
('2', '1', '菜单管理', 'C', 'Menu', 1, '/system/menu', '/views/system/Menu.vue', 'system:menu:list', '0', '0'),
('3', '2', '菜单查询', 'F', NULL, 1, NULL, NULL, 'system:menu:query', '0', '0'),
('4', '2', '菜单新增', 'F', NULL, 2, NULL, NULL, 'system:menu:add', '0', '0'),
('5', '2', '菜单编辑', 'F', NULL, 3, NULL, NULL, 'system:menu:edit', '0', '0'),
('6', '2', '菜单删除', 'F', NULL, 4, NULL, NULL, 'system:menu:delete', '0', '0'),
('7', '2', '菜单导出', 'F', NULL, 5, NULL, NULL, 'system:menu:export', '0', '0');
