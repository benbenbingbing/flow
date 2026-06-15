-- 修复旧表结构：将 create_time/update_time 重命名为 created_at/updated_at
-- 以匹配后端代码的查询字段

ALTER TABLE `sys_user`
  CHANGE COLUMN `create_time` `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `update_time` `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_menu`
  CHANGE COLUMN `create_time` `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `update_time` `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_organization`
  CHANGE COLUMN `create_time` `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `update_time` `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_role`
  CHANGE COLUMN `create_time` `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `update_time` `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_role_menu`
  CHANGE COLUMN `create_time` `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `sys_dict`
  CHANGE COLUMN `create_time` `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `update_time` `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_dict_item`
  CHANGE COLUMN `create_time` `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `update_time` `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
