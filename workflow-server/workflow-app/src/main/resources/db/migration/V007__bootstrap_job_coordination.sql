CREATE TABLE `workflow_bootstrap_job` (
  `job_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `completed_version` int NOT NULL DEFAULT '0',
  `owner_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci COMMENT='多Pod启动任务版本与串行化';
