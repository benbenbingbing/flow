-- 补齐源码已经使用但历史迁移未创建的基础表。

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `role_id` VARCHAR(64) NOT NULL COMMENT '角色ID',
    `menu_id` VARCHAR(64) NOT NULL COMMENT '菜单ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单关联表';

CREATE TABLE IF NOT EXISTS `process_node_form` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_config_id` VARCHAR(64) NOT NULL COMMENT '流程配置ID',
    `node_id` VARCHAR(100) NOT NULL COMMENT '节点ID（BPMN元素ID）',
    `node_name` VARCHAR(200) COMMENT '节点名称',
    `form_id` VARCHAR(64) NOT NULL COMMENT '实体表单ID',
    `is_readonly` TINYINT DEFAULT 0 COMMENT '是否只读：0否/1是',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_node` (`process_config_id`, `node_id`),
    KEY `idx_form_id` (`form_id`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点表单绑定表';
