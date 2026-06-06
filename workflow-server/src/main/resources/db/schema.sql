-- ========================================================
-- 工作流平台数据库表结构
-- 支持：流程设计、实体管理、表单配置、任务审批
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4
-- ========================================================

-- --------------------------------------------------------
-- 1. 流程定义相关表
-- --------------------------------------------------------

-- 流程定义配置表
-- 存储流程的基本信息和BPMN XML
CREATE TABLE IF NOT EXISTS `process_definition_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程标识（唯一）',
    `process_name` VARCHAR(200) NOT NULL COMMENT '流程名称',
    `description` TEXT COMMENT '流程描述',
    `category` VARCHAR(100) COMMENT '流程分类',
    `version` INT DEFAULT 0 COMMENT '当前版本号（0表示从未发布）',
    `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用',
    `bpmn_xml` LONGTEXT COMMENT 'BPMN XML内容',
    `entity_id` VARCHAR(64) COMMENT '绑定的实体定义ID',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0否/1是',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_key` (`process_key`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category`),
    KEY `idx_entity_id` (`entity_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义配置表';

-- 流程版本历史表
-- 记录流程每次发布的版本信息
CREATE TABLE IF NOT EXISTS `process_version_history` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_config_id` VARCHAR(64) NOT NULL COMMENT '流程定义配置ID',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程标识',
    `process_name` VARCHAR(200) NOT NULL COMMENT '流程名称',
    `version` INT NOT NULL COMMENT '版本号',
    `version_description` VARCHAR(500) COMMENT '版本说明',
    `bpmn_xml` LONGTEXT COMMENT 'BPMN XML内容',
    `deployment_id` VARCHAR(64) COMMENT 'Flowable部署ID',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/ROLLED_BACK已回滚/DISABLED已禁用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_version` (`process_config_id`, `version`),
    KEY `idx_process_key` (`process_key`),
    KEY `idx_deployment_id` (`deployment_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程版本历史表';

-- --------------------------------------------------------
-- 2. 节点配置相关表
-- --------------------------------------------------------

-- 节点配置表
-- 存储流程节点的配置信息（执行人、表单等）
CREATE TABLE IF NOT EXISTS `node_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_config_id` VARCHAR(64) NOT NULL COMMENT '流程定义配置ID',
    `node_id` VARCHAR(100) NOT NULL COMMENT '节点ID（BPMN中的id）',
    `node_name` VARCHAR(200) COMMENT '节点名称',
    `node_type` VARCHAR(50) COMMENT '节点类型：UserTask/ServiceTask/Gateway等',
    `config_json` TEXT COMMENT '配置JSON（扩展用）',
    `skip_node` TINYINT DEFAULT 0 COMMENT '是否跳过此节点（仅第一个用户任务节点可设置）：0-不跳过，1-跳过',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_node` (`process_config_id`, `node_id`),
    KEY `idx_node_id` (`node_id`),
    KEY `idx_node_type` (`node_type`),
    KEY `idx_skip_node` (`skip_node`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点配置表';

-- 执行人配置表
-- 存储节点的执行人配置
CREATE TABLE IF NOT EXISTS `assignee_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `node_config_id` VARCHAR(64) NOT NULL COMMENT '节点配置ID',
    `assignee_type` VARCHAR(20) DEFAULT 'user' COMMENT '指定类型：user固定人员/group用户组/role角色/expression表达式/interface接口动态',
    `assignee_value` VARCHAR(200) COMMENT '执行人值（用户名/组编码/角色编码/表达式）',
    `assignee_name` VARCHAR(200) COMMENT '执行人显示名称',
    `priority` INT DEFAULT 0 COMMENT '优先级（用于多人处理顺序）',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_node_config` (`node_config_id`),
    KEY `idx_assignee_type` (`assignee_type`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行人配置表';

-- 表单配置表
-- 存储节点绑定的表单配置
CREATE TABLE IF NOT EXISTS `form_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `node_config_id` VARCHAR(64) NOT NULL COMMENT '节点配置ID',
    `form_name` VARCHAR(200) COMMENT '表单名称',
    `form_key` VARCHAR(100) COMMENT '表单标识（自定义表单用）',
    `entity_form_id` VARCHAR(64) COMMENT '实体表单ID（实体表单用）',
    `is_readonly` TINYINT DEFAULT 0 COMMENT '是否只读：0否/1是',
    `description` VARCHAR(500) COMMENT '表单描述',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_node_config` (`node_config_id`),
    KEY `idx_entity_form` (`entity_form_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单配置表';

-- 表单字段配置表
-- 存储表单中的字段配置
CREATE TABLE IF NOT EXISTS `form_field_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `form_config_id` VARCHAR(64) NOT NULL COMMENT '表单配置ID',
    `field_name` VARCHAR(100) COMMENT '字段名称',
    `field_key` VARCHAR(100) COMMENT '字段标识',
    `field_type` VARCHAR(50) COMMENT '字段类型：string/int/date/datetime/decimal/boolean/enum等',
    `is_required` TINYINT DEFAULT 0 COMMENT '是否必填：0否/1是',
    `default_value` VARCHAR(200) COMMENT '默认值',
    `options_json` TEXT COMMENT '选项JSON（枚举类型用）',
    `validate_rules` VARCHAR(500) COMMENT '验证规则JSON',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_form_config` (`form_config_id`),
    KEY `idx_field_key` (`field_key`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单字段配置表';

-- --------------------------------------------------------
-- 3. 实体管理相关表
-- --------------------------------------------------------

-- 实体定义表
-- 定义业务实体（如请假单、报销单等）
CREATE TABLE IF NOT EXISTS `entity_definition` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码（唯一）',
    `entity_name` VARCHAR(200) NOT NULL COMMENT '实体名称',
    `description` TEXT COMMENT '实体描述',
    `table_name` VARCHAR(100) COMMENT '对应数据库表名',
    `enable_process` TINYINT DEFAULT 0 COMMENT '是否启用流程：0否/1是',
    `process_definition_id` VARCHAR(64) COMMENT '绑定的流程定义ID',
    `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_code` (`entity_code`),
    KEY `idx_process_def` (`process_definition_id`),
    KEY `idx_enable_process` (`enable_process`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体定义表';

-- 实体字段表
-- 定义实体的字段
CREATE TABLE IF NOT EXISTS `entity_field` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_id` VARCHAR(64) NOT NULL COMMENT '实体定义ID',
    `field_id` VARCHAR(100) NOT NULL COMMENT '字段编码',
    `field_name` VARCHAR(200) NOT NULL COMMENT '字段名称',
    `field_type` VARCHAR(50) NOT NULL COMMENT '字段类型：string/int/long/date/datetime/decimal/boolean/json/text/sub_form/sub_form_list等',
    `length` INT COMMENT '长度',
    `precision` INT COMMENT '精度（小数位数）',
    `is_required` TINYINT DEFAULT 0 COMMENT '是否必填',
    `is_unique` TINYINT DEFAULT 0 COMMENT '是否唯一',
    `is_searchable` TINYINT DEFAULT 0 COMMENT '是否可搜索',
    `is_list_show` TINYINT DEFAULT 1 COMMENT '是否在列表显示',
    `default_value` VARCHAR(200) COMMENT '默认值',
    `dict_type` VARCHAR(100) COMMENT '字典类型（用于枚举）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `ref_entity_id` VARCHAR(64) COMMENT '关联实体ID（用于子表单/实体引用）',
    `ref_entity_type` VARCHAR(20) COMMENT '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）',
    `display_mode` VARCHAR(20) DEFAULT 'embedded' COMMENT '显示方式：embedded-嵌入, tab-Tab页（用于子表单）',
    `ref_field_code` VARCHAR(100) COMMENT '关联字段编码（用于子表单数据关联）',
    `file_types` VARCHAR(500) COMMENT '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）',
    `file_max_size` INT COMMENT '文件大小限制（MB，用于附件类型）',
    `file_max_count` INT COMMENT '文件数量限制（用于附件类型）',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_field` (`entity_id`, `field_id`),
    KEY `idx_entity_id` (`entity_id`),
    KEY `idx_field_type` (`field_type`),
    KEY `idx_ref_entity_id` (`ref_entity_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体字段表';

-- 实体表单表
-- 定义实体的表单布局
CREATE TABLE IF NOT EXISTS `entity_form` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_id` VARCHAR(64) NOT NULL COMMENT '实体定义ID',
    `form_name` VARCHAR(200) NOT NULL COMMENT '表单名称',
    `form_key` VARCHAR(100) NOT NULL COMMENT '表单标识（唯一，在同一实体下）',
    `description` VARCHAR(500) COMMENT '表单描述',
    `layout_type` VARCHAR(20) DEFAULT 'vertical' COMMENT '布局类型：vertical垂直/horizontal水平/grid网格',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用/1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_form` (`entity_id`, `form_key`),
    KEY `idx_entity_id` (`entity_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体表单表';

-- 实体表单字段表
-- 定义表单中包含的字段及其配置
CREATE TABLE IF NOT EXISTS `entity_form_field` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `form_id` VARCHAR(64) NOT NULL COMMENT '实体表单ID',
    `field_id` VARCHAR(100) NOT NULL COMMENT '字段编码（对应entity_field）',
    `field_name` VARCHAR(200) COMMENT '字段显示名称（覆盖实体字段名称）',
    `field_label` VARCHAR(200) COMMENT '字段标签',
    `field_type` VARCHAR(50) COMMENT '字段类型',
    `component_type` VARCHAR(50) COMMENT '组件类型：input/select/radio/checkbox/textarea/date/datetime/number/upload等',
    `is_required` TINYINT DEFAULT 0 COMMENT '是否必填（覆盖实体配置）',
    `is_readonly` TINYINT DEFAULT 0 COMMENT '是否只读',
    `is_hidden` TINYINT DEFAULT 0 COMMENT '是否隐藏',
    `default_value` VARCHAR(200) COMMENT '默认值',
    `placeholder` VARCHAR(200) COMMENT '占位提示',
    `component_props` TEXT COMMENT '组件属性JSON（如options、maxlength等）',
    `grid_span` INT DEFAULT 24 COMMENT '栅格宽度（24栅格制）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_form_field` (`form_id`, `field_id`),
    KEY `idx_form_id` (`form_id`),
    KEY `idx_field_id` (`field_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体表单字段表';

-- 实体数据表
-- 存储实体的业务数据
CREATE TABLE IF NOT EXISTS `entity_data` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `data_no` VARCHAR(100) COMMENT '数据编号（业务单号）',
    `title` VARCHAR(500) COMMENT '数据标题',
    `submitter_id` VARCHAR(64) COMMENT '提交人ID',
    `submitter_name` VARCHAR(100) COMMENT '提交人名称',
    `submit_time` DATETIME COMMENT '提交时间',
    `data_json` LONGTEXT COMMENT '数据内容JSON',
    `process_instance_id` VARCHAR(64) COMMENT '关联的流程实例ID',
    `process_status` VARCHAR(20) DEFAULT 'draft' COMMENT '流程状态：draft草稿/running运行中/completed已完成/terminated已终止',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_no` (`data_no`),
    KEY `idx_entity_code` (`entity_code`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_submitter` (`submitter_id`),
    KEY `idx_process_status` (`process_status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体数据表';

-- --------------------------------------------------------
-- 4. 流程任务相关表
-- --------------------------------------------------------

-- 流程待办任务表
-- 同步Flowable的任务到本地，用于快速查询
CREATE TABLE IF NOT EXISTS `process_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    `process_definition_id` VARCHAR(64) COMMENT '流程定义ID',
    `process_key` VARCHAR(100) COMMENT '流程标识',
    `process_name` VARCHAR(200) COMMENT '流程名称',
    `business_key` VARCHAR(100) COMMENT '业务主键',
    `task_id` VARCHAR(64) NOT NULL COMMENT 'Flowable任务ID',
    `node_id` VARCHAR(100) COMMENT '节点ID',
    `node_name` VARCHAR(200) COMMENT '节点名称',
    `node_type` VARCHAR(50) COMMENT '节点类型',
    `entity_code` VARCHAR(100) COMMENT '实体编码',
    `entity_data_id` VARCHAR(64) COMMENT '实体数据ID',
    `form_key` VARCHAR(100) COMMENT '表单标识',
    `assignee_id` VARCHAR(64) COMMENT '执行人ID',
    `assignee_name` VARCHAR(100) COMMENT '执行人姓名',
    `assignee_type` VARCHAR(20) DEFAULT 'user' COMMENT '执行人类型：user/group',
    `form_data` JSON COMMENT '表单数据',
    `status` VARCHAR(20) DEFAULT 'todo' COMMENT '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回',
    `action` VARCHAR(20) COMMENT '处理动作：approve通过/reject驳回/transfer转办',
    `comment` TEXT COMMENT '处理意见',
    `start_time` DATETIME COMMENT '任务开始时间',
    `end_time` DATETIME COMMENT '任务结束时间',
    `due_time` DATETIME COMMENT '截止时间',
    `duration` BIGINT COMMENT '处理时长（毫秒）',
    `timeout_hours` INT COMMENT '超时时间（小时）',
    `timeout_action` VARCHAR(50) COMMENT '超时处理策略',
    `timeout_handled` TINYINT(1) DEFAULT 0 COMMENT '是否已处理超时',
    `priority` INT DEFAULT 0 COMMENT '优先级',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_process_def` (`process_definition_id`),
    KEY `idx_assignee` (`assignee_id`),
    KEY `idx_status` (`status`),
    KEY `idx_entity` (`entity_code`, `entity_data_id`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程待办任务表';

-- --------------------------------------------------------
-- 5. 流程动作相关表
-- --------------------------------------------------------

-- 流程动作表
-- 配置流程流转时触发的接口动作
CREATE TABLE IF NOT EXISTS `flow_action` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_config_id` VARCHAR(64) NOT NULL COMMENT '流程定义配置ID',
    `process_version_id` VARCHAR(64) COMMENT '流程版本ID（发布后填充）',
    `action_name` VARCHAR(200) NOT NULL COMMENT '动作名称',
    `action_code` VARCHAR(100) COMMENT '动作编码',
    `node_id` VARCHAR(100) COMMENT '关联节点ID（顺序流用）',
    `action_type` VARCHAR(50) DEFAULT 'interface' COMMENT '动作类型：interface接口/rest/webhook/java类',
    `trigger_timing` VARCHAR(50) DEFAULT 'after_complete' COMMENT '触发时机：before_start启动前/after_complete完成后',
    `interface_type` VARCHAR(20) DEFAULT 'rest' COMMENT '接口类型：rest/spring',
    `http_method` VARCHAR(10) DEFAULT 'POST' COMMENT 'HTTP方法：GET/POST/PUT/DELETE',
    `url` VARCHAR(500) COMMENT '请求URL或服务名',
    `method_name` VARCHAR(100) COMMENT '方法名（Spring Bean用）',
    `headers` TEXT COMMENT '请求头JSON',
    `params` TEXT COMMENT '请求参数JSON',
    `body_template` TEXT COMMENT '请求体模板（支持变量替换）',
    `result_mapping` VARCHAR(200) COMMENT '结果映射（存储到流程变量的字段）',
    `error_handling` VARCHAR(20) DEFAULT 'throw' COMMENT '错误处理：throw抛出异常/continue继续/ignore忽略',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `timeout` INT DEFAULT 30 COMMENT '超时时间（秒）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用/1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_config` (`process_config_id`),
    KEY `idx_process_version` (`process_version_id`),
    KEY `idx_node_id` (`node_id`),
    KEY `idx_action_type` (`action_type`),
    KEY `idx_trigger_timing` (`trigger_timing`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程动作表';

-- --------------------------------------------------------
-- 6. 系统管理相关表（简化版）
-- --------------------------------------------------------

-- 系统用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `username` VARCHAR(100) NOT NULL COMMENT '用户名（唯一）',
    `password` VARCHAR(100) COMMENT '密码',
    `nickname` VARCHAR(100) COMMENT '昵称',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `avatar` VARCHAR(200) COMMENT '头像URL',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0启用/1禁用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `org_id` VARCHAR(64) COMMENT '组织ID',
    `dept_id` VARCHAR(64) COMMENT '部门ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 系统用户组表
CREATE TABLE IF NOT EXISTS `sys_group` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `group_code` VARCHAR(100) NOT NULL COMMENT '组编码（唯一）',
    `group_name` VARCHAR(200) NOT NULL COMMENT '组名称',
    `description` VARCHAR(500) COMMENT '组描述',
    `parent_id` VARCHAR(64) COMMENT '父组ID',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0启用/1禁用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_code` (`group_code`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户组表';

-- 系统角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `role_code` VARCHAR(100) NOT NULL COMMENT '角色编码（唯一）',
    `role_name` VARCHAR(200) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(500) COMMENT '角色描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0启用/1禁用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 用户-组关联表
CREATE TABLE IF NOT EXISTS `sys_user_group` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `group_id` VARCHAR(64) NOT NULL COMMENT '组ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_group` (`user_id`, `group_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-组关联表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `role_id` VARCHAR(64) NOT NULL COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- --------------------------------------------------------
-- 7. 初始化数据
-- --------------------------------------------------------

-- 初始化系统用户（密码：admin123）
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `status`) VALUES
('1', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '系统管理员', 'admin@example.com', 0),
('2', 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '张三', 'zhangsan@example.com', 0),
('3', 'lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '李四', 'lisi@example.com', 0)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 初始化系统角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort_order`, `status`) VALUES
('1', 'admin', '系统管理员', '拥有系统所有权限', 1, 0),
('2', 'manager', '部门经理', '部门经理，可审批部门内申请', 2, 0),
('3', 'staff', '普通员工', '普通员工，可发起申请', 3, 0)
ON DUPLICATE KEY UPDATE `role_code` = `role_code`;

-- 初始化系统用户组
INSERT INTO `sys_group` (`id`, `group_code`, `group_name`, `description`, `sort_order`, `status`) VALUES
('1', 'dev_dept', '研发部', '研发部门', 1, 0),
('2', 'hr_dept', '人事部', '人事部门', 2, 0),
('3', 'finance_dept', '财务部', '财务部门', 3, 0)
ON DUPLICATE KEY UPDATE `group_code` = `group_code`;

-- 用户-角色关联
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES
('1', '1', '1'),
('2', '2', '2'),
('3', '3', '3')
ON DUPLICATE KEY UPDATE `user_id` = `user_id`;

-- 用户-组关联
INSERT INTO `sys_user_group` (`id`, `user_id`, `group_id`) VALUES
('1', '1', '1'),
('2', '2', '1'),
('3', '3', '2')
ON DUPLICATE KEY UPDATE `user_id` = `user_id`;
