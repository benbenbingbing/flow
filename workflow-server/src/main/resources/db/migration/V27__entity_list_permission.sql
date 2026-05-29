-- ============================================================
-- 实体列表数据权限表
-- 支持：全部、本人、部门、部门树、角色、自定义表达式、委托
-- ============================================================

CREATE TABLE IF NOT EXISTS entity_list_permission (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    entity_code VARCHAR(100) NOT NULL COMMENT '实体编码',
    rule_name VARCHAR(200) NOT NULL COMMENT '规则名称',
    priority INT DEFAULT 0 COMMENT '优先级，数字越大越优先',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用（0否/1是）',

    -- 匹配条件：决定哪些人适用这条规则
    match_config JSON COMMENT '匹配条件配置JSON',

    -- 过滤配置：决定这些人能看到什么数据
    filter_config JSON COMMENT '数据过滤配置JSON',

    -- 多规则叠加方式
    combine_mode VARCHAR(20) DEFAULT 'UNION' COMMENT '规则叠加方式：UNION(并集)/INTERSECT(交集)',

    created_by VARCHAR(64) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除（0否/1是）',

    INDEX idx_entity_code (entity_code),
    INDEX idx_enabled (enabled),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体列表数据权限规则表';

-- ============================================================
-- 数据权限委托表
-- ============================================================

CREATE TABLE IF NOT EXISTS entity_list_permission_delegate (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    entity_code VARCHAR(100) COMMENT '实体编码（为空表示全部实体）',
    from_user_id VARCHAR(64) NOT NULL COMMENT '委托方用户ID',
    to_user_id VARCHAR(64) NOT NULL COMMENT '受托方用户ID',
    delegate_scope VARCHAR(50) DEFAULT 'PERSONAL' COMMENT '委托范围：ALL(全部)/PERSONAL(仅本人数据)/CONDITION(按条件)',
    delegate_config JSON COMMENT '委托范围配置JSON',
    start_time DATETIME COMMENT '委托开始时间',
    end_time DATETIME COMMENT '委托结束时间',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用（0否/1是）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_entity_code (entity_code),
    INDEX idx_from_user (from_user_id),
    INDEX idx_to_user (to_user_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限委托表';
