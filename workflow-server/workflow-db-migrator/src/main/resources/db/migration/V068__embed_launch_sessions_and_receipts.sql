-- Embed Runtime 的一次性 Launch、浏览器 Session、会话配额和写操作回执。
-- Launch 不对 consumed_session_id 建 FK；Session 反向引用 Launch，以保持兑换插入顺序
-- 和审计保留链无环。所有 flow_user_id 与 sys_user.id 使用相同排序规则。

-- 已有 Open API 租约保持 scope_key='' 的旧语义；Embed 使用非空 scope_key
-- 把并发计数精确隔离到 Application + Grant，同时保留原有应用级索引。
ALTER TABLE `integration_api_request_lease`
  ADD COLUMN `scope_key` varchar(128) CHARACTER SET utf8mb4
    COLLATE utf8mb4_bin NOT NULL DEFAULT '' AFTER `application_id`,
  ADD KEY `idx_integration_api_lease_scope`
    (`application_id`,`scope_key`,`expires_at`);

CREATE TABLE `embed_launch` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `provider_security_version` bigint NOT NULL,
  -- integration_application.version 的初始值为 0，因此此快照允许 0。
  `application_version` bigint NOT NULL,
  `grant_security_version` bigint NOT NULL,
  `view_security_version` bigint NOT NULL,
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `identity_binding_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `binding_version` bigint NOT NULL,
  `subject_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `subject_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `parent_origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `channel_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `entry_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `context_ciphertext` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_cipher_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `ui_locale` varchar(35) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'zh-CN',
  `ui_theme` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'light',
  `launch_code_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ISSUED',
  `expires_at` datetime(6) NOT NULL,
  `consumed_at` datetime(6) DEFAULT NULL,
  -- 仅为审计/排障的反向指针，故意不建 FK。
  `consumed_session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `source_ip_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `source_ip_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `user_agent_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `user_agent_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_launch_code_digest` (`launch_code_digest`),
  UNIQUE KEY `uk_embed_launch_consumed_session` (`consumed_session_id`),
  KEY `idx_embed_launch_expiry` (`status`,`expires_at`),
  KEY `idx_embed_launch_cleanup` (`status`,`update_time`,`id`),
  KEY `idx_embed_launch_application_view` (`application_id`,`view_id`,`create_time`),
  KEY `idx_embed_launch_binding_status` (`identity_binding_id`,`status`),
  CONSTRAINT `fk_embed_launch_application`
    FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_grant`
    FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_view`
    FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_release`
    FOREIGN KEY (`view_release_id`) REFERENCES `embed_view_release` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_provider`
    FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_flow_user`
    FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_launch_binding`
    FOREIGN KEY (`identity_binding_id`) REFERENCES `embed_external_identity_binding` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_launch_status`
    CHECK (`status` IN ('ISSUED','CONSUMED','EXPIRED','REVOKED')),
  CONSTRAINT `chk_embed_launch_lifecycle`
    CHECK ((`status` = 'ISSUED'
        AND `consumed_at` IS NULL AND `consumed_session_id` IS NULL
        AND `revoked_at` IS NULL)
      OR (`status` = 'CONSUMED'
        AND `consumed_at` IS NOT NULL AND `consumed_session_id` IS NOT NULL
        AND `revoked_at` IS NULL)
      OR (`status` = 'EXPIRED'
        AND `consumed_at` IS NULL AND `consumed_session_id` IS NULL
        AND `revoked_at` IS NULL)
      OR (`status` = 'REVOKED'
        AND `consumed_at` IS NULL AND `consumed_session_id` IS NULL
        AND `revoked_at` IS NOT NULL)),
  CONSTRAINT `chk_embed_launch_entry`
    CHECK ((`entry_mode` IN ('LIST','CREATE') AND `record_id` IS NULL)
      OR (`entry_mode` IN ('VIEW','EDIT') AND `record_id` IS NOT NULL)),
  CONSTRAINT `chk_embed_launch_origin`
    CHECK (`parent_origin` REGEXP '^https://[^/?#]+$'),
  CONSTRAINT `chk_embed_launch_theme`
    CHECK (`ui_theme` IN ('light','dark','system')),
  CONSTRAINT `chk_embed_launch_versions`
    CHECK (`provider_security_version` > 0
      AND `application_version` >= 0
      AND `grant_security_version` > 0
      AND `view_security_version` > 0
      AND `binding_version` > 0),
  CONSTRAINT `chk_embed_launch_digests`
    CHECK (`subject_digest` REGEXP '^[0-9a-f]{64}$'
      AND `context_digest` REGEXP '^[0-9a-f]{64}$'
      AND `launch_code_digest` REGEXP '^[0-9a-f]{64}$'
      AND (`source_ip_digest` IS NULL
        OR `source_ip_digest` REGEXP '^[0-9a-f]{64}$')
      AND (`user_agent_digest` IS NULL
        OR `user_agent_digest` REGEXP '^[0-9a-f]{64}$')),
  CONSTRAINT `chk_embed_launch_digest_keys`
    CHECK (((`source_ip_digest` IS NULL
          AND `source_ip_digest_key_version` IS NULL)
      OR (`source_ip_digest` IS NOT NULL
          AND `source_ip_digest_key_version` IS NOT NULL))
      AND ((`user_agent_digest` IS NULL
          AND `user_agent_digest_key_version` IS NULL)
      OR (`user_agent_digest` IS NOT NULL
          AND `user_agent_digest_key_version` IS NOT NULL))),
  CONSTRAINT `chk_embed_launch_context`
    CHECK (JSON_VALID(`context_ciphertext`)
      AND OCTET_LENGTH(`context_ciphertext`) <= 65536
      AND CHAR_LENGTH(`context_cipher_key_version`) > 0
      AND CHAR_LENGTH(`context_digest_key_version`) > 0
      AND CHAR_LENGTH(`subject_digest_key_version`) > 0),
  CONSTRAINT `chk_embed_launch_expiry`
    CHECK (`expires_at` > `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 一次性启动凭据及安全快照';

CREATE TABLE `embed_session` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `session_token_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `launch_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `provider_security_version` bigint NOT NULL,
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `identity_binding_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `binding_version` bigint NOT NULL,
  `parent_origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `channel_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `entry_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `parent_nonce_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `child_nonce_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_ciphertext` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_cipher_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `ui_locale` varchar(35) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'zh-CN',
  `ui_theme` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'light',
  `capability_snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `application_version` bigint NOT NULL,
  `grant_security_version` bigint NOT NULL,
  `view_security_version` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `slot_released` tinyint NOT NULL DEFAULT '0',
  `slot_released_at` datetime(6) DEFAULT NULL,
  `issued_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `idle_expires_at` datetime(6) NOT NULL,
  `absolute_expires_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `revoke_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `source_ip_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `source_ip_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `user_agent_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `user_agent_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_session_token_digest` (`session_token_digest`),
  UNIQUE KEY `uk_embed_session_launch` (`launch_id`),
  KEY `idx_embed_session_expiry`
    (`status`,`idle_expires_at`,`absolute_expires_at`),
  KEY `idx_embed_session_counter_reconcile`
    (`status`,`slot_released`,`grant_id`,`flow_user_id`),
  KEY `idx_embed_session_terminal_cleanup`
    (`status`,`slot_released_at`,`id`),
  KEY `idx_embed_session_application` (`application_id`,`status`),
  KEY `idx_embed_session_view` (`view_id`,`status`),
  KEY `idx_embed_session_user` (`flow_user_id`,`status`),
  CONSTRAINT `fk_embed_session_launch`
    FOREIGN KEY (`launch_id`) REFERENCES `embed_launch` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_application`
    FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_grant`
    FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_view`
    FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_release`
    FOREIGN KEY (`view_release_id`) REFERENCES `embed_view_release` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_provider`
    FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_flow_user`
    FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_binding`
    FOREIGN KEY (`identity_binding_id`) REFERENCES `embed_external_identity_binding` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_session_status`
    CHECK (`status` IN ('ACTIVE','LOGGED_OUT','EXPIRED','REVOKED')),
  CONSTRAINT `chk_embed_session_slot`
    CHECK ((`status` = 'ACTIVE'
        AND `slot_released` = 0 AND `slot_released_at` IS NULL)
      OR (`status` IN ('LOGGED_OUT','EXPIRED')
        AND `slot_released` = 1 AND `slot_released_at` IS NOT NULL
        AND `revoked_at` IS NULL AND `revoke_reason` IS NULL)
      OR (`status` = 'REVOKED'
        AND `slot_released` = 1 AND `slot_released_at` IS NOT NULL
        AND `revoked_at` IS NOT NULL AND `revoke_reason` IS NOT NULL)),
  CONSTRAINT `chk_embed_session_entry`
    CHECK ((`entry_mode` IN ('LIST','CREATE') AND `record_id` IS NULL)
      OR (`entry_mode` IN ('VIEW','EDIT') AND `record_id` IS NOT NULL)),
  CONSTRAINT `chk_embed_session_origin`
    CHECK (`parent_origin` REGEXP '^https://[^/?#]+$'),
  CONSTRAINT `chk_embed_session_theme`
    CHECK (`ui_theme` IN ('light','dark','system')),
  CONSTRAINT `chk_embed_session_versions`
    CHECK (`provider_security_version` > 0
      AND `application_version` >= 0
      AND `grant_security_version` > 0
      AND `view_security_version` > 0
      AND `binding_version` > 0),
  CONSTRAINT `chk_embed_session_digests`
    CHECK (`session_token_digest` REGEXP '^[0-9a-f]{64}$'
      AND `parent_nonce_digest` REGEXP '^[0-9a-f]{64}$'
      AND `child_nonce_digest` REGEXP '^[0-9a-f]{64}$'
      AND `context_digest` REGEXP '^[0-9a-f]{64}$'
      AND (`source_ip_digest` IS NULL
        OR `source_ip_digest` REGEXP '^[0-9a-f]{64}$')
      AND (`user_agent_digest` IS NULL
        OR `user_agent_digest` REGEXP '^[0-9a-f]{64}$')),
  CONSTRAINT `chk_embed_session_digest_keys`
    CHECK (((`source_ip_digest` IS NULL
          AND `source_ip_digest_key_version` IS NULL)
      OR (`source_ip_digest` IS NOT NULL
          AND `source_ip_digest_key_version` IS NOT NULL))
      AND ((`user_agent_digest` IS NULL
          AND `user_agent_digest_key_version` IS NULL)
      OR (`user_agent_digest` IS NOT NULL
          AND `user_agent_digest_key_version` IS NOT NULL))),
  CONSTRAINT `chk_embed_session_context`
    CHECK (JSON_VALID(`context_ciphertext`)
      AND JSON_VALID(`capability_snapshot_json`)
      AND OCTET_LENGTH(`context_ciphertext`) <= 65536
      AND OCTET_LENGTH(`capability_snapshot_json`) <= 65536
      AND CHAR_LENGTH(`context_cipher_key_version`) > 0
      AND CHAR_LENGTH(`context_digest_key_version`) > 0),
  CONSTRAINT `chk_embed_session_time_window`
    CHECK (`issued_at` <= `last_seen_at`
      AND `issued_at` < `idle_expires_at`
      AND `idle_expires_at` <= `absolute_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed iframe 运行时会话及安全快照';

CREATE TABLE `embed_session_counter` (
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `active_count` int NOT NULL DEFAULT '0',
  `lock_version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`grant_id`,`flow_user_id`),
  CONSTRAINT `fk_embed_session_counter_grant`
    FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_session_counter_user`
    FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_session_counter_count`
    CHECK (`active_count` >= 0),
  CONSTRAINT `chk_embed_session_counter_lock`
    CHECK (`lock_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed Grant/用户活跃会话计数器';

CREATE TABLE `embed_operation_receipt` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  -- 不引用 integration_idempotency_record：两者保留期不同，清理应先删幂等记录再删回执。
  `idempotency_record_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `operation` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `actor_scope_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `target_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `target_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `outcome_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `record_version` bigint DEFAULT NULL,
  `result_summary_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_receipt_idempotency` (`idempotency_record_id`),
  KEY `idx_embed_receipt_cleanup` (`create_time`,`id`),
  KEY `idx_embed_receipt_application` (`application_id`,`operation`,`create_time`),
  KEY `idx_embed_receipt_target` (`target_type`,`target_id`,`create_time`),
  CONSTRAINT `fk_embed_receipt_application`
    FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_receipt_operation`
    CHECK (`operation` IN (
      'EMBED_RECORD_CREATE','EMBED_RECORD_UPDATE','EMBED_ACTION_EXECUTE')),
  CONSTRAINT `chk_embed_receipt_actor_digest`
    CHECK (`actor_scope_digest` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_embed_receipt_record_version`
    CHECK (`record_version` IS NULL OR `record_version` >= 0),
  CONSTRAINT `chk_embed_receipt_summary`
    CHECK (JSON_VALID(`result_summary_json`)
      AND OCTET_LENGTH(`result_summary_json`) <= 8192)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 写操作最小业务回执';
