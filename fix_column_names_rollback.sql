-- 回滚：将系统表的 created_at/updated_at 改回 create_time/update_time
-- 以匹配后端实体类的默认驼峰映射

ALTER TABLE `sys_user`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_at` `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_menu`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_at` `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_organization`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_at` `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_role`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_at` `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_role_menu`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `sys_dict`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_at` `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_dict_item`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_at` `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
