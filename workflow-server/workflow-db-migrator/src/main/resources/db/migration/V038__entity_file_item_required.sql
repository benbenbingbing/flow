ALTER TABLE `entity_field_file_item`
  ADD COLUMN `is_required` tinyint NOT NULL DEFAULT '0'
    COMMENT '该附件项是否必填'
    AFTER `item_name`;
