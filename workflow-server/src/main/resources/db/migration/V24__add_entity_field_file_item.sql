-- 实体字段附件项配置表
-- 用于文件类型字段配置多个独立的附件要求
CREATE TABLE IF NOT EXISTS `entity_field_file_item` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `field_id` VARCHAR(64) NOT NULL COMMENT '关联字段ID（entity_field.id）',
    `item_name` VARCHAR(200) NOT NULL COMMENT '附件项名称（如：项目章程、需求文档）',
    `file_types` VARCHAR(500) COMMENT '允许的文件类型（逗号分隔，如：.pdf,.doc,.docx）',
    `max_size` INT COMMENT '单文件大小限制（MB）',
    `max_count` INT COMMENT '文件数量限制',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_field_id` (`field_id`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体字段附件项配置表';
