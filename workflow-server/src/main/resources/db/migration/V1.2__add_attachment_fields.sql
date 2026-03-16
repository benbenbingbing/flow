-- 添加附件相关字段到 entity_field 表
ALTER TABLE `entity_field`
ADD COLUMN IF NOT EXISTS `file_types` VARCHAR(500) COMMENT '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）',
ADD COLUMN IF NOT EXISTS `file_max_size` INT COMMENT '文件大小限制（MB，用于附件类型）',
ADD COLUMN IF NOT EXISTS `file_max_count` INT COMMENT '文件数量限制（用于附件类型）';
