-- ========================================================  
-- 初始化数据：创建默认管理员用户
-- 说明: admin 用户密码为 admin
-- ========================================================

-- 检查并创建 admin 用户（如果不存在）
INSERT IGNORE INTO sys_user (id, username, password, nickname, email, phone, status, deleted, created_at, updated_at)
VALUES (
  '1',
  'admin', 
  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO',  -- BCrypt加密后的'admin'
  '系统管理员',
  'admin@example.com',
  '13800138000',
  '0',
  0,
  NOW(),
  NOW()
);

-- 创建一些示例用户（可选）
INSERT IGNORE INTO sys_user (id, username, password, nickname, email, status, deleted, created_at, updated_at) VALUES
('2', 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '张三', 'zhangsan@example.com', '0', 0, NOW(), NOW()),
('3', 'lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '李四', 'lisi@example.com', '0', 0, NOW(), NOW()),
('4', 'wangwu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '王五', 'wangwu@example.com', '0', 0, NOW(), NOW());
