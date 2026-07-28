CREATE TABLE `auth_login_throttle` (
  `throttle_key` char(66) COLLATE utf8mb4_bin NOT NULL,
  `failure_count` int NOT NULL DEFAULT '0',
  `window_started_at` datetime(6) NOT NULL,
  `blocked_until` datetime(6) DEFAULT NULL,
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`throttle_key`),
  KEY `idx_auth_login_throttle_updated`
    (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='跨Pod登录失败限流';
