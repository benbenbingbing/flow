-- ========================================================  
-- V10: 添加默认表单标记
-- 说明: 每个实体可以设置一个默认表单，流程节点未选择表单时使用
-- ========================================================

-- 实体表单表添加默认表单标记
ALTER TABLE `entity_form` 
ADD COLUMN `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否默认表单：0-否 1-是' AFTER `layout_type`;

-- 添加唯一索引，确保每个实体只有一个默认表单
ALTER TABLE `entity_form` 
ADD UNIQUE KEY `uk_entity_default` (`entity_id`, `is_default`) USING BTREE;
