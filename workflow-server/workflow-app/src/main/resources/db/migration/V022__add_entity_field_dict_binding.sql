ALTER TABLE `entity_field`
    MODIFY COLUMN `dict_type` varchar(100) DEFAULT NULL COMMENT '绑定的系统代码表编码',
    ADD COLUMN `value_storage` varchar(20) DEFAULT 'SCALAR' COMMENT '字段值存储：SCALAR/MULTI_TABLE' AFTER `dict_type`;

ALTER TABLE `sys_dict_item`
    ADD KEY `idx_dict_item_lookup` (`dict_code`, `item_code`, `deleted`);
