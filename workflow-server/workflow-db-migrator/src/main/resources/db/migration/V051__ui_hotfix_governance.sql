-- UI HOTFIX 受控申请、双人复核、发布窗口与观察状态。
CREATE TABLE `ui_config_hotfix_request` (
    `id` VARCHAR(64) NOT NULL COMMENT 'HOTFIX申请ID',
    `config_type` VARCHAR(20) NOT NULL COMMENT 'FORM/LIST',
    `config_id` VARCHAR(64) NOT NULL COMMENT '配置ID',
    `draft_hash` CHAR(64) NOT NULL COMMENT '申请时草稿哈希',
    `active_release_id` VARCHAR(64) NOT NULL COMMENT '申请时激活发布ID',
    `target_hash` VARCHAR(255) NOT NULL COMMENT '影响目标摘要',
    `impact_token_hash` CHAR(64) NOT NULL COMMENT '预检令牌摘要',
    `risk_level` VARCHAR(20) NOT NULL COMMENT 'SAFE/REVIEW',
    `reason` VARCHAR(1000) NOT NULL COMMENT '变更原因',
    `ticket_ref` VARCHAR(255) NOT NULL COMMENT '关联工单',
    `impact_document` LONGTEXT NOT NULL COMMENT '申请时影响预览JSON',
    `applicant_id` VARCHAR(64) NOT NULL COMMENT '申请人ID',
    `applicant_name` VARCHAR(100) DEFAULT NULL COMMENT '申请人姓名',
    `window_start` DATETIME NOT NULL COMMENT '允许发布时间窗口开始',
    `window_end` DATETIME NOT NULL COMMENT '允许发布时间窗口结束',
    `review_required` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要独立复核',
    `status` VARCHAR(30) NOT NULL COMMENT 'PENDING_REVIEW/APPROVED/PUBLISHING/OBSERVING/OBSERVED_OK/OBSERVED_ALERT/REJECTED/ROLLED_BACK/CANCELLED',
    `open_slot` TINYINT GENERATED ALWAYS AS (
      CASE WHEN `status` IN ('PENDING_REVIEW','APPROVED','PUBLISHING') THEN 1 ELSE NULL END
    ) STORED COMMENT '每个配置仅允许一个开放申请',
    `reviewer_id` VARCHAR(64) DEFAULT NULL COMMENT '复核人ID',
    `reviewer_name` VARCHAR(100) DEFAULT NULL COMMENT '复核人姓名',
    `review_comment` VARCHAR(1000) DEFAULT NULL COMMENT '复核意见',
    `reviewed_at` DATETIME DEFAULT NULL COMMENT '复核时间',
    `release_id` VARCHAR(64) DEFAULT NULL COMMENT '实际发布ID',
    `published_at` DATETIME DEFAULT NULL COMMENT '实际发布时间',
    `observation_start` DATETIME DEFAULT NULL COMMENT '观察窗口开始',
    `observation_end` DATETIME DEFAULT NULL COMMENT '观察窗口结束',
    `observation_status` VARCHAR(20) DEFAULT NULL COMMENT 'OBSERVING/OK/ALERT',
    `rolled_back_by` VARCHAR(64) DEFAULT NULL COMMENT '回滚人ID',
    `rolled_back_at` DATETIME DEFAULT NULL COMMENT '回滚时间',
    `rollback_reason` VARCHAR(1000) DEFAULT NULL COMMENT '回滚原因',
    `cancelled_by` VARCHAR(64) DEFAULT NULL COMMENT '取消人ID',
    `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
    `cancel_reason` VARCHAR(1000) DEFAULT NULL COMMENT '取消原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ui_hotfix_request_open` (`config_type`,`config_id`,`open_slot`),
    UNIQUE KEY `uk_ui_hotfix_request_release` (`release_id`),
    KEY `idx_ui_hotfix_request_status` (`status`,`window_end`),
    KEY `idx_ui_hotfix_request_config` (`config_type`,`config_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI HOTFIX受控申请';

CREATE TABLE `ui_hotfix_observation_metric` (
    `id` VARCHAR(64) NOT NULL COMMENT '指标ID',
    `request_id` VARCHAR(64) NOT NULL COMMENT 'HOTFIX申请ID',
    `release_id` VARCHAR(64) NOT NULL COMMENT 'HOTFIX发布ID',
    `metric_code` VARCHAR(40) NOT NULL COMMENT 'FORM_LOAD/FORM_SUBMIT/PROCESS_TASK',
    `total_count` BIGINT NOT NULL DEFAULT 0 COMMENT '观察总次数',
    `failure_count` BIGINT NOT NULL DEFAULT 0 COMMENT '失败次数',
    `last_error` VARCHAR(1000) DEFAULT NULL COMMENT '最近失败摘要',
    `last_observed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近观察时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ui_hotfix_observation_metric` (`request_id`,`metric_code`),
    KEY `idx_ui_hotfix_observation_release` (`release_id`,`metric_code`),
    CONSTRAINT `fk_ui_hotfix_metric_request`
      FOREIGN KEY (`request_id`) REFERENCES `ui_config_hotfix_request` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI HOTFIX观察指标';

-- 将申请、复核、观察和回滚拆分为独立权限，满足职责分离要求。
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`)
VALUES ('entity_ui_hotfix_review_permission','0','UI热修复独立复核','F',NULL,92,NULL,NULL,'entity:ui-config:hotfix:review','0','1','0','0',NULL,'0','1','HOTFIX双人复核',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL)
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `perm`=VALUES(`perm`), `remark`=VALUES(`remark`), `update_time`=CURRENT_TIMESTAMP;

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`)
VALUES ('entity_ui_hotfix_rollback_permission','0','UI热修复受控回滚','F',NULL,93,NULL,NULL,'entity:ui-config:hotfix:rollback','0','1','0','0',NULL,'0','1','HOTFIX专用回滚权限',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL)
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `perm`=VALUES(`perm`), `remark`=VALUES(`remark`), `update_time`=CURRENT_TIMESTAMP;

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `is_frame`, `is_cache`, `query`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `entity_code`, `resource_type`, `list_key`)
VALUES ('entity_ui_hotfix_observe_permission','0','UI热修复观察指标','F',NULL,94,NULL,NULL,'entity:ui-config:hotfix:observe','0','1','0','0',NULL,'0','1','HOTFIX运行观察指标上报',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,NULL,NULL,NULL)
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `perm`=VALUES(`perm`), `remark`=VALUES(`remark`), `update_time`=CURRENT_TIMESTAMP;
