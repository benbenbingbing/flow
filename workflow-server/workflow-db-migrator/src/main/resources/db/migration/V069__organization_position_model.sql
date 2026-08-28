-- 全局职务、组织任职事实与组织业务层级。
-- sys_position_assignment 是负责人等业务身份的权威来源；
-- sys_organization.leader_id/leader_name 在过渡期仅保留为兼容投影。

ALTER TABLE `sys_organization`
  ADD COLUMN `business_level_code` varchar(100)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL
    COMMENT '稳定业务层级编码，来源于 organization_business_level 字典'
    AFTER `type`,
  ADD KEY `idx_sys_org_business_level`
    (`business_level_code`,`status`,`deleted`);

CREATE TABLE `sys_position` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `position_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `position_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `applicable_unit_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `holder_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `built_in` tinyint NOT NULL DEFAULT 0,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED',
  `sort_order` int NOT NULL DEFAULT 0,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `revision` int NOT NULL DEFAULT 1,
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_position_code` (`position_code`),
  KEY `idx_sys_position_status_sort` (`status`,`deleted`,`sort_order`),
  CONSTRAINT `chk_sys_position_unit_type`
    CHECK (`applicable_unit_type` IN ('ORG','DEPT','ANY')),
  CONSTRAINT `chk_sys_position_holder_mode`
    CHECK (`holder_mode` IN ('SINGLE','MULTIPLE')),
  CONSTRAINT `chk_sys_position_status`
    CHECK (`status` IN ('ENABLED','DISABLED')),
  CONSTRAINT `chk_sys_position_flags`
    CHECK (`built_in` IN (0,1) AND `deleted` IN (0,1)),
  CONSTRAINT `chk_sys_position_revision`
    CHECK (`revision` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='全局职务定义';

CREATE TABLE `sys_position_assignment` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `position_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `organization_unit_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `is_primary` tinyint NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `effective_from` datetime(6) NOT NULL,
  `effective_to` datetime(6) DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `revoke_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `revision` int NOT NULL DEFAULT 1,
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_position_assignment_fact`
    (`position_id`,`organization_unit_id`,`user_id`,`effective_from`),
  KEY `idx_sys_position_assignment_lookup`
    (`position_id`,`organization_unit_id`,`effective_from`,`effective_to`,`revoked_at`),
  KEY `idx_sys_position_assignment_user`
    (`user_id`,`effective_from`,`effective_to`,`revoked_at`),
  KEY `idx_sys_position_assignment_unit`
    (`organization_unit_id`,`position_id`),
  CONSTRAINT `fk_sys_position_assignment_position`
    FOREIGN KEY (`position_id`) REFERENCES `sys_position` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_sys_position_assignment_org`
    FOREIGN KEY (`organization_unit_id`) REFERENCES `sys_organization` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_sys_position_assignment_user`
    FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_sys_position_assignment_primary`
    CHECK (`is_primary` IN (0,1)),
  CONSTRAINT `chk_sys_position_assignment_period`
    CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),
  CONSTRAINT `chk_sys_position_assignment_revoked_at`
    CHECK (`revoked_at` IS NULL OR `revoked_at` >= `effective_from`),
  CONSTRAINT `chk_sys_position_assignment_revision`
    CHECK (`revision` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='组织职务任职事实（有效区间为半开区间）';

-- 幂等键按操作人隔离。服务会先按固定顺序锁定职务、组织和操作人，
-- 再读取本表，从而让同一操作人的并发重复批次安全串行化。
CREATE TABLE `sys_position_assignment_batch` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `assignment_ids_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_position_batch_idempotency`
    (`created_by`,`idempotency_key`),
  KEY `idx_sys_position_batch_time` (`create_time`),
  CONSTRAINT `fk_sys_position_batch_actor`
    FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_sys_position_batch_hash`
    CHECK (`request_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_sys_position_batch_result_json`
    CHECK (JSON_VALID(`assignment_ids_json`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='职务批量任命幂等结果';

INSERT INTO `sys_dict` (
  `id`,`dict_code`,`dict_name`,`description`,`status`,`sort`,`deleted`,
  `create_time`,`update_time`
) VALUES (
  'dict_org_business_level_001','organization_business_level',
  '组织业务层级','独立于物理树深度的稳定组织业务层级','0',10,0,
  CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
);

INSERT INTO `sys_dict_item` (
  `id`,`dict_id`,`dict_code`,`parent_id`,`item_code`,`item_label`,
  `item_value`,`sort`,`status`,`remark`,`deleted`,`create_time`,`update_time`
) VALUES
  ('dict_org_level_group_001','dict_org_business_level_001','organization_business_level','0','GROUP','集团','GROUP',10,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('dict_org_level_company_001','dict_org_business_level_001','organization_business_level','0','COMPANY','公司','COMPANY',20,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('dict_org_level_center_001','dict_org_business_level_001','organization_business_level','0','CENTER','中心','CENTER',30,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('dict_org_level_dept1_001','dict_org_business_level_001','organization_business_level','0','FIRST_LEVEL_DEPT','一级部门','FIRST_LEVEL_DEPT',40,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('dict_org_level_dept2_001','dict_org_business_level_001','organization_business_level','0','SECOND_LEVEL_DEPT','二级部门','SECOND_LEVEL_DEPT',50,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('dict_org_level_team_001','dict_org_business_level_001','organization_business_level','0','TEAM','团队','TEAM',60,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);

INSERT INTO `sys_position` (
  `id`,`position_code`,`position_name`,`applicable_unit_type`,`holder_mode`,
  `built_in`,`status`,`sort_order`,`description`,`revision`,
  `created_by`,`updated_by`,`create_time`,`update_time`,`deleted`
) VALUES (
  'position_unit_leader_001','UNIT_LEADER','负责人','ANY','SINGLE',
  1,'ENABLED',10,'组织或部门负责人；旧 leader 字段的权威任职来源',1,
  'migration-v069','migration-v069',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),0
);

-- 旧模型没有负责人任职开始时间，只能以迁移执行时刻建立首段任职事实。
-- BusinessMigrationPreflight 会在本 DDL 前拒绝悬空 leader_id，禁止静默漏回填。
INSERT INTO `sys_position_assignment` (
  `id`,`position_id`,`organization_unit_id`,`user_id`,`is_primary`,`sort_order`,
  `effective_from`,`effective_to`,`revoked_at`,`revision`,
  `created_by`,`updated_by`,`create_time`,`update_time`
)
SELECT MD5(CONCAT('position-unit-leader:', organization_unit.`id`)),
       'position_unit_leader_001', organization_unit.`id`,
       organization_unit.`leader_id`, 1, 0,
       UTC_TIMESTAMP(6), NULL, NULL, 1,
       'migration-v069','migration-v069',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
FROM `sys_organization` organization_unit
WHERE organization_unit.`leader_id` IS NOT NULL
  AND TRIM(organization_unit.`leader_id`) <> ''
  AND organization_unit.`deleted` = 0;
