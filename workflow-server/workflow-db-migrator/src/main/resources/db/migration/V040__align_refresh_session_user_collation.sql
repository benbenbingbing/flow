ALTER TABLE `auth_refresh_session`
  MODIFY COLUMN `user_id` varchar(64)
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci
    NOT NULL
    COMMENT '会话所属用户ID';
