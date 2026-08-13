ALTER TABLE `entity_field_file_item`
  ADD COLUMN `item_key` varchar(64) DEFAULT NULL
    COMMENT '附件项不可变业务标识'
    AFTER `field_id`,
  ADD COLUMN `name_aliases` longtext DEFAULT NULL
    COMMENT '历史附件项名称 JSON 数组'
    AFTER `item_name`;

UPDATE `entity_field_file_item`
SET `item_key` = CONCAT('afi_', REPLACE(UUID(), '-', ''))
WHERE `item_key` IS NULL OR `item_key` = '';

ALTER TABLE `entity_field_file_item`
  MODIFY COLUMN `item_key` varchar(64) NOT NULL
    COMMENT '附件项不可变业务标识',
  ADD UNIQUE KEY `uk_entity_field_file_item_key` (`field_id`, `item_key`);
