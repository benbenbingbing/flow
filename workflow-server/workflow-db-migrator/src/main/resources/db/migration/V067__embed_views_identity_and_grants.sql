-- Embed Runtime 的管理面、发布快照、人员映射和应用授权。
-- 外部稳定标识采用 utf8mb4_bin；唯一引用 sys_user.id 的 flow_user_id
-- 必须保持 utf8mb4_0900_ai_ci，以满足 MySQL 外键字符集/排序规则要求。

CREATE TABLE `embed_view` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `surface_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'DRAFT',
  `draft_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `draft_revision` bigint NOT NULL DEFAULT '1',
  -- 该指针由发布事务维护，不设 FK，避免 View <-> Release 的生命周期环。
  `published_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `lock_version` bigint NOT NULL DEFAULT '1',
  `security_version` bigint NOT NULL DEFAULT '1',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_view_key` (`view_key`),
  KEY `idx_embed_view_status` (`status`,`update_time`),
  CONSTRAINT `chk_embed_view_surface`
    CHECK (`surface_type` IN ('LIST','FORM')),
  CONSTRAINT `chk_embed_view_status`
    CHECK (`status` IN ('DRAFT','ACTIVE','DISABLED','RETIRED')),
  CONSTRAINT `chk_embed_view_draft_json`
    CHECK (JSON_VALID(`draft_config_json`)
      AND OCTET_LENGTH(`draft_config_json`) <= 262144),
  CONSTRAINT `chk_embed_view_versions`
    CHECK (`draft_revision` > 0 AND `lock_version` > 0
      AND `security_version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 可嵌入视图草稿';

CREATE TABLE `embed_view_release` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `revision` bigint NOT NULL,
  `surface_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `entity_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `list_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  -- 这些是发布快照，不能对可变的实体/列表/表单元数据建立删除级联外键。
  `default_form_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `list_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `list_release_version` bigint DEFAULT NULL,
  `form_release_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `form_release_version` bigint DEFAULT NULL,
  `entry_modes_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `capabilities_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `field_policy_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `action_policy_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_schema_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `context_bindings_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `ui_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `config_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `release_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `published_at` datetime(6) NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_view_release_revision` (`view_id`,`revision`),
  KEY `idx_embed_view_release_published` (`view_id`,`published_at`),
  CONSTRAINT `fk_embed_view_release_view`
    FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_release_surface`
    CHECK (`surface_type` IN ('LIST','FORM')),
  CONSTRAINT `chk_embed_release_revision`
    CHECK (`revision` > 0),
  CONSTRAINT `chk_embed_release_list_pair`
    CHECK ((`list_release_id` IS NULL AND `list_release_version` IS NULL)
      OR (`list_release_id` IS NOT NULL AND `list_release_version` > 0)),
  CONSTRAINT `chk_embed_release_form_pair`
    CHECK ((`form_release_id` IS NULL AND `form_release_version` IS NULL)
      OR (`form_release_id` IS NOT NULL AND `form_release_version` > 0)),
  CONSTRAINT `chk_embed_release_target`
    CHECK ((`surface_type` = 'LIST'
        AND `list_key` IS NOT NULL
        AND `list_release_id` IS NOT NULL
        AND `list_release_version` IS NOT NULL
        AND (`default_form_id` IS NULL OR `form_release_id` IS NOT NULL))
      OR (`surface_type` = 'FORM'
        AND `list_key` IS NULL
        AND `list_release_id` IS NULL
        AND `list_release_version` IS NULL
        AND `form_release_id` IS NOT NULL
        AND `form_release_version` IS NOT NULL)),
  CONSTRAINT `chk_embed_release_json`
    CHECK (JSON_VALID(`entry_modes_json`)
      AND JSON_VALID(`capabilities_json`)
      AND JSON_VALID(`field_policy_json`)
      AND JSON_VALID(`action_policy_json`)
      AND JSON_VALID(`context_schema_json`)
      AND JSON_VALID(`context_bindings_json`)
      AND JSON_VALID(`ui_config_json`)
      AND JSON_VALID(`config_json`)
      AND OCTET_LENGTH(`entry_modes_json`) <= 65536
      AND OCTET_LENGTH(`capabilities_json`) <= 65536
      AND OCTET_LENGTH(`field_policy_json`) <= 262144
      AND OCTET_LENGTH(`action_policy_json`) <= 262144
      AND OCTET_LENGTH(`context_schema_json`) <= 262144
      AND OCTET_LENGTH(`context_bindings_json`) <= 262144
      AND OCTET_LENGTH(`ui_config_json`) <= 262144
      AND OCTET_LENGTH(`config_json`) <= 262144),
  CONSTRAINT `chk_embed_release_hash`
    CHECK (`config_hash` REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 视图不可变发布快照';

CREATE TABLE `embed_identity_provider` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `issuer` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  -- MySQL UNIQUE 会把多个 NULL 视为不同值；生成非空判别值后才能原子阻止
  -- 同一 TRUSTED_EXTERNAL_ID namespace 并发创建多个 Provider。
  `issuer_uniqueness_key` varchar(500)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
    GENERATED ALWAYS AS (
      COALESCE(`issuer`, '<trusted-external-id>')
    ) STORED,
  `subject_namespace` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `audiences_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `algorithms_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `jwks_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `jwks_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `jwks_url` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `clock_skew_seconds` int NOT NULL DEFAULT '30',
  `max_assertion_lifetime_seconds` int NOT NULL DEFAULT '60',
  `key_version` bigint NOT NULL DEFAULT '1',
  `lock_version` bigint NOT NULL DEFAULT '1',
  `security_version` bigint NOT NULL DEFAULT '1',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_identity_provider_issuer_ns`
    (`type`,`issuer_uniqueness_key`,`subject_namespace`),
  KEY `idx_embed_identity_provider_status` (`status`,`update_time`),
  CONSTRAINT `chk_embed_provider_type`
    CHECK (`type` IN ('SIGNED_JWT','TRUSTED_EXTERNAL_ID')),
  CONSTRAINT `chk_embed_provider_status`
    CHECK (`status` IN ('ACTIVE','DISABLED','REVOKED')),
  CONSTRAINT `chk_embed_provider_jwks_mode`
    CHECK (`jwks_mode` IS NULL
      OR `jwks_mode` IN ('STATIC_JWK_SET','REMOTE_JWKS')),
  CONSTRAINT `chk_embed_provider_material`
    CHECK ((`type` = 'SIGNED_JWT'
        AND `issuer` IS NOT NULL
        AND ((`jwks_mode` = 'STATIC_JWK_SET'
              AND `jwks_json` IS NOT NULL AND `jwks_url` IS NULL)
          OR (`jwks_mode` = 'REMOTE_JWKS'
              AND `jwks_json` IS NULL AND `jwks_url` LIKE 'https://%')))
      OR (`type` = 'TRUSTED_EXTERNAL_ID'
        AND `issuer` IS NULL
        AND `jwks_mode` IS NULL
        AND `jwks_json` IS NULL
        AND `jwks_url` IS NULL)),
  CONSTRAINT `chk_embed_provider_json`
    CHECK (JSON_VALID(`audiences_json`)
      AND JSON_VALID(`algorithms_json`)
      AND (`jwks_json` IS NULL OR JSON_VALID(`jwks_json`))
      AND OCTET_LENGTH(`audiences_json`) <= 65536
      AND OCTET_LENGTH(`algorithms_json`) <= 65536
      AND (`jwks_json` IS NULL OR OCTET_LENGTH(`jwks_json`) <= 262144)),
  CONSTRAINT `chk_embed_provider_timing`
    CHECK (`clock_skew_seconds` BETWEEN 0 AND 300
      AND `max_assertion_lifetime_seconds` BETWEEN 1 AND 300),
  CONSTRAINT `chk_embed_provider_versions`
    CHECK (`key_version` > 0 AND `lock_version` > 0
      AND `security_version` > 0),
  CONSTRAINT `chk_embed_provider_revocation`
    CHECK ((`status` = 'REVOKED'
        AND `revoked_by` IS NOT NULL AND `revoked_at` IS NOT NULL)
      OR (`status` <> 'REVOKED'
        AND `revoked_by` IS NULL AND `revoked_at` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 外部身份提供方';

CREATE TABLE `embed_application_grant` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `view_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `trusted_subject_assertion` tinyint NOT NULL DEFAULT '0',
  `revision_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'FOLLOW_ACTIVE',
  `pinned_revision` bigint DEFAULT NULL,
  `capability_ceiling_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `max_active_sessions_per_user` int NOT NULL DEFAULT '1',
  `max_session_seconds` int NOT NULL DEFAULT '1800',
  `launch_limit_per_minute` int NOT NULL DEFAULT '60',
  `runtime_limit_per_minute` int NOT NULL DEFAULT '600',
  `max_concurrency` int NOT NULL DEFAULT '10',
  `expires_at` datetime(6) DEFAULT NULL,
  `lock_version` bigint NOT NULL DEFAULT '1',
  `security_version` bigint NOT NULL DEFAULT '1',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_grant_application_view` (`application_id`,`view_id`),
  KEY `idx_embed_grant_status_expiry` (`status`,`expires_at`),
  KEY `idx_embed_grant_provider_status` (`identity_provider_id`,`status`),
  CONSTRAINT `fk_embed_grant_application`
    FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_grant_view`
    FOREIGN KEY (`view_id`) REFERENCES `embed_view` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_grant_provider`
    FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_grant_status`
    CHECK (`status` IN ('ACTIVE','DISABLED','REVOKED')),
  CONSTRAINT `chk_embed_grant_trusted_subject`
    CHECK (`trusted_subject_assertion` IN (0,1)),
  CONSTRAINT `chk_embed_grant_revision_mode`
    CHECK ((`revision_mode` = 'FOLLOW_ACTIVE' AND `pinned_revision` IS NULL)
      OR (`revision_mode` = 'PINNED' AND `pinned_revision` > 0)),
  CONSTRAINT `chk_embed_grant_capabilities`
    CHECK (JSON_VALID(`capability_ceiling_json`)
      AND OCTET_LENGTH(`capability_ceiling_json`) <= 65536),
  CONSTRAINT `chk_embed_grant_limits`
    CHECK (`max_active_sessions_per_user` BETWEEN 1 AND 10000
      AND `max_session_seconds` BETWEEN 60 AND 86400
      AND `launch_limit_per_minute` BETWEEN 1 AND 10000
      AND `runtime_limit_per_minute` BETWEEN 1 AND 100000
      AND `max_concurrency` BETWEEN 1 AND 1000),
  CONSTRAINT `chk_embed_grant_versions`
    CHECK (`lock_version` > 0 AND `security_version` > 0),
  CONSTRAINT `chk_embed_grant_revocation`
    CHECK ((`status` = 'REVOKED'
        AND `revoked_by` IS NOT NULL AND `revoked_at` IS NOT NULL)
      OR (`status` <> 'REVOKED'
        AND `revoked_by` IS NULL AND `revoked_at` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 应用对视图的授权';

CREATE TABLE `embed_allowed_origin` (
  `grant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`grant_id`,`origin`),
  CONSTRAINT `fk_embed_allowed_origin_grant`
    FOREIGN KEY (`grant_id`) REFERENCES `embed_application_grant` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_allowed_origin_format`
    CHECK (`origin` REGEXP '^https://[^/?#]+$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 授权父页面来源';

CREATE TABLE `embed_external_identity_binding` (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `application_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `identity_provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `subject_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `subject_digest_key_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `subject_hint` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `flow_user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE',
  `binding_version` bigint NOT NULL DEFAULT '1',
  `effective_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `expires_at` datetime(6) DEFAULT NULL,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  `revoked_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_embed_binding_subject`
    (`application_id`,`identity_provider_id`,`subject_digest`),
  KEY `idx_embed_binding_flow_user` (`flow_user_id`,`status`),
  KEY `idx_embed_binding_expiry` (`status`,`expires_at`),
  CONSTRAINT `fk_embed_binding_application`
    FOREIGN KEY (`application_id`) REFERENCES `integration_application` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_binding_provider`
    FOREIGN KEY (`identity_provider_id`) REFERENCES `embed_identity_provider` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `fk_embed_binding_flow_user`
    FOREIGN KEY (`flow_user_id`) REFERENCES `sys_user` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_binding_status`
    CHECK (`status` IN ('ACTIVE','DISABLED','REVOKED')),
  CONSTRAINT `chk_embed_binding_digest`
    CHECK (`subject_digest` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_embed_binding_version`
    CHECK (`binding_version` > 0),
  CONSTRAINT `chk_embed_binding_window`
    CHECK (`expires_at` IS NULL OR `expires_at` > `effective_at`),
  CONSTRAINT `chk_embed_binding_revocation`
    CHECK ((`status` = 'REVOKED'
        AND `revoked_by` IS NOT NULL AND `revoked_at` IS NOT NULL)
      OR (`status` <> 'REVOKED'
        AND `revoked_by` IS NULL AND `revoked_at` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 外部主体到 Flow 用户的精确绑定';

CREATE TABLE `embed_assertion_replay` (
  `provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `jti_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`provider_id`,`jti_digest`),
  KEY `idx_embed_assertion_replay_expiry` (`expires_at`),
  CONSTRAINT `fk_embed_assertion_replay_provider`
    FOREIGN KEY (`provider_id`) REFERENCES `embed_identity_provider` (`id`)
    ON DELETE RESTRICT,
  CONSTRAINT `chk_embed_assertion_replay_digest`
    CHECK (`jti_digest` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_embed_assertion_replay_expiry`
    CHECK (`expires_at` > `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='Embed 人员断言 jti 防重放记录';

-- 管理面继续使用普通 Flow 登录态和 EndpointAuthorizationInterceptor。
-- 高风险权限独立拆分，避免拥有普通配置权限的账号自动获得发布、映射或撤销能力。
INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, icon, sort, path, component, perm,
  status, visible, is_frame, is_cache, keep_alive, breadcrumb, deleted,
  create_time, update_time
) VALUES (
  'embed_management_menu_001', '0', '嵌入集成', 'C', 'Monitor', 76,
  '/system/embed-management', 'system/EmbedManagement',
  'system:embed:view', '0', '0', '0', '0', '0', '1', 0,
  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, sort, perm,
  status, visible, deleted, create_time, update_time
) VALUES
  ('embed_perm_view', 'embed_management_menu_001', '查看嵌入运行时', 'F', 1,
    'system:embed:view', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('embed_perm_manage', 'embed_management_menu_001', '维护嵌入视图与授权', 'F', 2,
    'system:embed:manage', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('embed_perm_publish', 'embed_management_menu_001', '发布嵌入视图', 'F', 3,
    'system:embed:publish', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('embed_perm_identity_manage', 'embed_management_menu_001', '维护嵌入身份映射', 'F', 4,
    'system:embed:identity-manage', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('embed_perm_session_revoke', 'embed_management_menu_001', '撤销嵌入会话', 'F', 5,
    'system:embed:session-revoke', '0', '1', 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_role_menu (id, role_id, menu_id, create_time)
SELECT MD5(CONCAT('1:', id)), '1', id, CURRENT_TIMESTAMP
  FROM sys_menu
 WHERE id = 'embed_management_menu_001'
    OR id LIKE 'embed_perm_%';
