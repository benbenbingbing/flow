-- 实体关系从 SUB_FORM 字段生命周期中解耦。
-- data_key 是聚合数据中的稳定属性名；parent_field_* 仅保留给旧配置兼容。
-- BusinessMigrationPreflight 在 Flyway 写迁移历史前检查存量重复编码并给出冲突样例。
ALTER TABLE `entity_relation`
  ADD COLUMN `data_key` varchar(100) DEFAULT NULL COMMENT '聚合数据中的稳定属性名' AFTER `relation_name`,
  ADD COLUMN `ownership_type` varchar(20) NOT NULL DEFAULT 'COMPOSITION' COMMENT '关系所有权：COMPOSITION/ASSOCIATION' AFTER `relation_type`,
  MODIFY COLUMN `parent_field_code` varchar(100) DEFAULT NULL COMMENT '旧版承载关系的父字段编码，仅兼容使用';

UPDATE `entity_relation`
SET `data_key` = COALESCE(NULLIF(TRIM(`parent_field_code`), ''), `relation_code`),
    `ownership_type` = 'COMPOSITION'
WHERE `data_key` IS NULL OR TRIM(`data_key`) = '';

ALTER TABLE `entity_relation`
  ADD UNIQUE KEY `uk_entity_relation_code` (`parent_entity_id`, `relation_code`),
  ADD UNIQUE KEY `uk_entity_relation_data_key` (`parent_entity_id`, `data_key`),
  ADD KEY `idx_entity_relation_child_ref` (`child_entity_id`, `child_ref_field_code`);

-- 新发布会冻结完整关系列表；NULL 明确表示 V043 之前的旧发布，运行时需回退当前关系表。
ALTER TABLE `entity_publish_history`
  ADD COLUMN `relations_snapshot` longtext DEFAULT NULL COMMENT '发布时实体关系定义快照JSON，NULL为旧发布' AFTER `fields_snapshot`;

-- data_key 本阶段故意保持可空：迁移先执行而旧 runtime Pod 仍在服务时，
-- 旧二进制不会写该列；新运行时按 data_key -> parent_field_code -> relation_code 回退。
