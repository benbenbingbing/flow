CREATE TABLE `process_cc_outbox` (
  `id` varchar(64) NOT NULL,
  `cc_record_id` varchar(64) NOT NULL,
  `channel` varchar(20) NOT NULL DEFAULT 'IN_APP',
  `payload` longtext DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_time` datetime DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `sent_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cc_outbox_record_channel` (`cc_record_id`,`channel`),
  KEY `idx_cc_outbox_pending` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程知会发送Outbox';
