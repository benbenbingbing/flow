-- ========================================================
-- V18: 添加实体列表配置表和列表字段配置表
-- 说明: 支持一个实体定义多个列表视图
-- ========================================================

-- 实体列表配置表
CREATE TABLE IF NOT EXISTS `entity_list_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_id` VARCHAR(64) NOT NULL COMMENT '实体定义ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `list_key` VARCHAR(100) NOT NULL COMMENT '列表标识（唯一，如：default、myList）',
    `list_name` VARCHAR(200) NOT NULL COMMENT '列表名称',
    `description` VARCHAR(500) COMMENT '说明',
    `is_default` TINYINT DEFAULT 0 COMMENT '是否默认列表',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_list_key` (`entity_id`, `list_key`, `deleted`),
    KEY `idx_entity_id` (`entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表配置表';

-- 实体列表字段配置表
CREATE TABLE IF NOT EXISTS `entity_list_field` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `list_config_id` VARCHAR(64) NOT NULL COMMENT '所属列表配置ID',
    `field_id` VARCHAR(64) NOT NULL COMMENT '实体字段ID（关联entity_field）',
    `field_code` VARCHAR(100) NOT NULL COMMENT '字段编码',
    `field_name` VARCHAR(200) NOT NULL COMMENT '字段名称（快照）',
    `sort_order` INT DEFAULT 0 COMMENT '列排序号',
    `width` INT DEFAULT 0 COMMENT '列宽度（0表示自适应）',
    `show_in_list` TINYINT DEFAULT 1 COMMENT '是否显示在列表',
    `is_query` TINYINT DEFAULT 1 COMMENT '是否作为查询条件',
    `query_type` VARCHAR(50) DEFAULT 'LIKE' COMMENT '查询方式：EQ/NE/LIKE/GT/LT/BETWEEN/IN',
    `align` VARCHAR(20) DEFAULT 'left' COMMENT '对齐方式：left/center/right',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_list_field` (`list_config_id`, `field_id`, `deleted`),
    KEY `idx_list_config_id` (`list_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表字段配置表';
