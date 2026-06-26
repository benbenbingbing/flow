-- 实体列表数据权限规则增强
ALTER TABLE entity_list_permission
    ADD COLUMN list_config_id varchar(64) DEFAULT NULL COMMENT '关联列表配置ID，NULL 表示对该实体所有列表生效',
    ADD COLUMN rule_effect varchar(20) DEFAULT 'ALLOW' COMMENT '规则效果：ALLOW(放行)/DENY(拒绝)',
    ADD COLUMN stop_processing tinyint DEFAULT 0 COMMENT '命中后是否停止评估更低优先级规则：0否/1是',
    ADD KEY idx_list_config_id (list_config_id);
