CREATE TABLE IF NOT EXISTS `auth_refresh_session` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刷新会话ID，同时写入Access Token的sid声明',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话所属用户ID',
  `refresh_token_hash` char(64) COLLATE utf8mb4_bin NOT NULL COMMENT 'Refresh Token的SHA-256十六进制摘要',
  `token_version` bigint NOT NULL COMMENT '创建会话时的用户全局令牌版本',
  `create_time` datetime(6) NOT NULL COMMENT '会话创建时间',
  `last_used_at` datetime(6) NOT NULL COMMENT '最近一次成功刷新时间',
  `idle_expires_at` datetime(6) NOT NULL COMMENT '空闲超时时间',
  `absolute_expires_at` datetime(6) NOT NULL COMMENT '会话绝对过期时间',
  `revoked_at` datetime(6) DEFAULT NULL COMMENT '会话撤销时间',
  `revoked_reason` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话撤销原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_refresh_session_token_hash` (`refresh_token_hash`),
  KEY `idx_auth_refresh_session_user` (`user_id`, `revoked_at`),
  KEY `idx_auth_refresh_session_expiry` (`idle_expires_at`, `absolute_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='浏览器刷新会话';
