-- ========================================================
-- 工作流平台数据库表结构
-- 文件名: flow_platform_schema.sql
-- 版本: 1.0.0
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- 说明: 本文件为流程平台核心表结构，后续修改请基于此文件
-- ========================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------------------
-- 表名: process_definition_config        -- 流程定义配置表
-- 说明: 存储流程的基本信息、BPMN XML、绑定实体等
-- --------------------------------------------------------
DROP TABLE IF EXISTS `process_definition_config`;
CREATE TABLE `process_definition_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `process_key` VARCHAR(100) NOT NULL COMMENT '流程标识（唯一）',
    `process_name` VARCHAR(200) NOT NULL COMMENT '流程名称',
    `description` TEXT COMMENT '流程描述',
    `category` VARCHAR(100) COMMENT '流程分类（存储sys_dict_item.item_code）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【流程定义配置表】存储流程基本信息、BPMN XML、绑定实体等';

-- --------------------------------------------------------
-- 表名: process_version_history          -- 流程版本历史表
-- 说明: 记录流程每次发布的版本信息
-- --------------------------------------------------------
DROP TABLE IF EXISTS `process_version_history`;
CREATE TABLE `process_version_history` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【流程版本历史表】记录流程每次发布的版本信息';

-- --------------------------------------------------------
-- 表名: entity_publish_history           -- 实体发布版本历史表
-- 说明: 记录实体每次发布的版本信息和表结构快照
-- --------------------------------------------------------
DROP TABLE IF EXISTS `entity_publish_history`;
CREATE TABLE `entity_publish_history` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_id` VARCHAR(64) NOT NULL COMMENT '实体定义ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `entity_name` VARCHAR(200) NOT NULL COMMENT '实体名称',
    `version` INT NOT NULL COMMENT '版本号',
    `version_description` VARCHAR(500) COMMENT '版本说明',
    `fields_snapshot` LONGTEXT COMMENT '字段定义快照JSON',
    `table_ddl` LONGTEXT COMMENT '表结构DDL',
    `publish_type` VARCHAR(20) DEFAULT 'CREATE' COMMENT '发布类型：CREATE首次创建/ALTER修改结构',
    `changes_description` VARCHAR(500) COMMENT '变更内容描述',
    `published_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `published_by` VARCHAR(64) COMMENT '发布人ID',
    `published_by_name` VARCHAR(100) COMMENT '发布人姓名',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/ROLLBACK已回滚',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_version` (`entity_id`, `version`),
    KEY `idx_entity_code` (`entity_code`),
    KEY `idx_publish_type` (`publish_type`),
    KEY `idx_status` (`status`),
    KEY `idx_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【实体发布版本历史表】记录实体每次发布的版本信息和表结构快照';

-- --------------------------------------------------------
-- 表名: node_config                      -- 节点配置表
-- 说明: 存储流程节点的配置信息（执行人、表单等）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `node_config`;
CREATE TABLE `node_config` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【节点配置表】存储流程节点的配置信息（执行人、表单等）';

-- --------------------------------------------------------
-- 表名: assignee_config                  -- 执行人配置表
-- 说明: 存储节点的执行人配置（支持用户/组/角色/表达式/接口）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `assignee_config`;
CREATE TABLE `assignee_config` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【执行人配置表】存储节点的执行人配置（支持用户/组/角色/表达式/接口）';

-- --------------------------------------------------------
-- 表名: form_config                      -- 表单配置表
-- 说明: 存储节点绑定的表单配置
-- --------------------------------------------------------
DROP TABLE IF EXISTS `form_config`;
CREATE TABLE `form_config` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【表单配置表】存储节点绑定的表单配置';

-- --------------------------------------------------------
-- 表名: form_field_config                -- 表单字段配置表
-- 说明: 存储表单中的字段配置
-- --------------------------------------------------------
DROP TABLE IF EXISTS `form_field_config`;
CREATE TABLE `form_field_config` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【表单字段配置表】存储表单中的字段配置';

-- --------------------------------------------------------
-- 表名: entity_definition                -- 实体定义表
-- 说明: 定义业务实体（如请假单、报销单等）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `entity_definition`;
CREATE TABLE `entity_definition` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码（唯一）',
    `entity_name` VARCHAR(200) NOT NULL COMMENT '实体名称',
    `description` TEXT COMMENT '实体描述',
    `table_name` VARCHAR(100) COMMENT '对应数据库表名',
    `enable_process` TINYINT DEFAULT 0 COMMENT '是否启用流程：0否/1是',
    `process_definition_id` VARCHAR(64) COMMENT '绑定的流程定义ID',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_code` (`entity_code`),
    KEY `idx_process_def` (`process_definition_id`),
    KEY `idx_enable_process` (`enable_process`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【实体定义表】定义业务实体（如请假单、报销单等）';

-- --------------------------------------------------------
-- 表名: entity_field                     -- 实体字段表
-- 说明: 定义实体的字段
-- --------------------------------------------------------
DROP TABLE IF EXISTS `entity_field`;
CREATE TABLE `entity_field` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_id` VARCHAR(64) NOT NULL COMMENT '实体定义ID',
    `field_id` VARCHAR(100) NOT NULL COMMENT '字段编码',
    `field_name` VARCHAR(200) NOT NULL COMMENT '字段名称',
    `field_type` VARCHAR(50) NOT NULL COMMENT '字段类型：string/int/long/date/datetime/decimal/boolean/json/text等',
    `length` INT COMMENT '长度',
    `precision` INT COMMENT '精度（小数位数）',
    `is_required` TINYINT DEFAULT 0 COMMENT '是否必填',
    `is_unique` TINYINT DEFAULT 0 COMMENT '是否唯一',
    `is_searchable` TINYINT DEFAULT 0 COMMENT '是否可搜索',
    `is_list_show` TINYINT DEFAULT 1 COMMENT '是否在列表显示',
    `default_value` VARCHAR(200) COMMENT '默认值',
    `dict_type` VARCHAR(100) COMMENT '字典类型（用于枚举）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `is_published` TINYINT DEFAULT 0 COMMENT '是否已发布到数据库表（0否/1是）',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_field` (`entity_id`, `field_id`),
    KEY `idx_entity_id` (`entity_id`),
    KEY `idx_field_type` (`field_type`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【实体字段表】定义实体的字段';

-- --------------------------------------------------------
-- 表名: entity_form                      -- 实体表单表
-- 说明: 定义实体的表单布局
-- --------------------------------------------------------
DROP TABLE IF EXISTS `entity_form`;
CREATE TABLE `entity_form` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【实体表单表】定义实体的表单布局';

-- --------------------------------------------------------
-- 表名: entity_form_field                -- 实体表单字段表
-- 说明: 定义表单中包含的字段及其配置（组件类型、验证规则等）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `entity_form_field`;
CREATE TABLE `entity_form_field` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【实体表单字段表】定义表单中包含的字段及其配置（组件类型、验证规则等）';

-- --------------------------------------------------------
-- 表名: entity_data                      -- 实体数据表
-- 说明: 存储实体的业务数据（JSON格式）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `entity_data`;
CREATE TABLE `entity_data` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【实体数据表】存储实体的业务数据（JSON格式）';

-- --------------------------------------------------------
-- 表名: process_task                     -- 流程待办任务表
-- 说明: 同步Flowable的任务到本地，用于快速查询
-- --------------------------------------------------------
DROP TABLE IF EXISTS `process_task`;
CREATE TABLE `process_task` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
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
    `assignee_type` VARCHAR(20) DEFAULT 'user' COMMENT '执行人类型：user/group',
    `status` VARCHAR(20) DEFAULT 'todo' COMMENT '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回',
    `action` VARCHAR(20) COMMENT '处理动作：approve通过/reject驳回/transfer转办',
    `comment` TEXT COMMENT '处理意见',
    `start_time` DATETIME COMMENT '任务开始时间',
    `end_time` DATETIME COMMENT '任务结束时间',
    `duration` BIGINT COMMENT '处理时长（毫秒）',
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
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【流程待办任务表】同步Flowable的任务到本地，用于快速查询';

-- --------------------------------------------------------
-- 表名: flow_action                      -- 流程动作表
-- 说明: 配置流程流转时触发的接口动作（支持REST/Spring Bean/Java类）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `flow_action`;
CREATE TABLE `flow_action` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【流程动作表】配置流程流转时触发的接口动作（支持REST/Spring Bean/Java类）';

-- --------------------------------------------------------
-- 表名: sys_user                         -- 系统用户表
-- 说明: 系统用户信息
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `username` VARCHAR(100) NOT NULL COMMENT '用户名（唯一）',
    `password` VARCHAR(100) COMMENT '密码',
    `nickname` VARCHAR(100) COMMENT '昵称',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `avatar` VARCHAR(200) COMMENT '头像URL',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用/1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【系统用户表】系统用户信息';

-- --------------------------------------------------------
-- 表名: sys_group                        -- 系统用户组表
-- 说明: 用户组信息（如部门）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_group`;
CREATE TABLE `sys_group` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `group_code` VARCHAR(100) NOT NULL COMMENT '组编码（唯一）',
    `group_name` VARCHAR(200) NOT NULL COMMENT '组名称',
    `description` VARCHAR(500) COMMENT '组描述',
    `parent_id` VARCHAR(64) COMMENT '父组ID',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用/1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_code` (`group_code`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【系统用户组表】用户组信息（如部门）';

-- --------------------------------------------------------
-- 表名: sys_role                         -- 系统角色表
-- 说明: 角色信息
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `role_code` VARCHAR(100) NOT NULL COMMENT '角色编码（唯一）',
    `role_name` VARCHAR(200) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(500) COMMENT '角色描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用/1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【系统角色表】角色信息';

-- --------------------------------------------------------
-- 表名: sys_user_group                   -- 用户-组关联表
-- 说明: 用户与组的关联关系
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_group`;
CREATE TABLE `sys_user_group` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `group_id` VARCHAR(64) NOT NULL COMMENT '组ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_group` (`user_id`, `group_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【用户-组关联表】用户与组的关联关系';

-- --------------------------------------------------------
-- 表名: sys_user_role                    -- 用户-角色关联表
-- 说明: 用户与角色的关联关系
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `role_id` VARCHAR(64) NOT NULL COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【用户-角色关联表】用户与角色的关联关系';

-- --------------------------------------------------------
-- 表名: sys_dict                         -- 字典类型表
-- 说明: 定义字典分类（如流程分类、优先级、状态等）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `dict_code` VARCHAR(100) NOT NULL COMMENT '字典编码（唯一）',
    `dict_name` VARCHAR(200) NOT NULL COMMENT '字典名称',
    `description` VARCHAR(500) COMMENT '字典描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用/1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_code` (`dict_code`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【字典类型表】定义字典分类（如流程分类、优先级、状态等）';

-- --------------------------------------------------------
-- 表名: sys_dict_item                    -- 字典项表
-- 说明: 存储字典的具体项（如流程分类下的行政、财务等）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `dict_code` VARCHAR(100) NOT NULL COMMENT '字典编码（关联sys_dict）',
    `item_code` VARCHAR(100) NOT NULL COMMENT '字典项编码（同一字典内唯一）',
    `item_name` VARCHAR(200) NOT NULL COMMENT '字典项名称',
    `item_value` VARCHAR(200) COMMENT '字典项值（可选，为空时使用item_code）',
    `item_label` VARCHAR(200) COMMENT '显示标签（用于前端展示，为空时使用item_name）',
    `parent_code` VARCHAR(100) COMMENT '父项编码（支持层级字典）',
    `sort_order` INT DEFAULT 0 COMMENT '排序号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用/1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) COMMENT '更新人',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_item_code` (`dict_code`, `item_code`),
    KEY `idx_dict_code` (`dict_code`),
    KEY `idx_parent_code` (`parent_code`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='【字典项表】存储字典的具体项（如流程分类下的行政、财务等）';

SET FOREIGN_KEY_CHECKS = 1;

-- --------------------------------------------------------
-- 初始化数据
-- --------------------------------------------------------

-- 初始化系统用户（密码：admin123）
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `status`) VALUES
('1', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '系统管理员', 'admin@example.com', 1),
('2', 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '张三', 'zhangsan@example.com', 1),
('3', 'lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '李四', 'lisi@example.com', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 初始化系统角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`, `sort_order`, `status`) VALUES
('1', 'admin', '系统管理员', '拥有系统所有权限', 1, 1),
('2', 'manager', '部门经理', '部门经理，可审批部门内申请', 2, 1),
('3', 'staff', '普通员工', '普通员工，可发起申请', 3, 1)
ON DUPLICATE KEY UPDATE `role_code` = `role_code`;

-- 初始化系统用户组
INSERT INTO `sys_group` (`id`, `group_code`, `group_name`, `description`, `sort_order`, `status`) VALUES
('1', 'dev_dept', '研发部', '研发部门', 1, 1),
('2', 'hr_dept', '人事部', '人事部门', 2, 1),
('3', 'finance_dept', '财务部', '财务部门', 3, 1)
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

-- ========================================================
-- 字典数据初始化
-- ========================================================

-- 流程分类字典
INSERT INTO `sys_dict` (`id`, `dict_code`, `dict_name`, `description`, `status`) VALUES
('dict_001', 'process_category', '流程分类', '流程定义的分类，用于流程分组管理', 1)
ON DUPLICATE KEY UPDATE `dict_code` = `dict_code`;

-- 流程分类字典项
INSERT INTO `sys_dict_item` (`id`, `dict_code`, `item_code`, `item_name`, `sort_order`, `status`) VALUES
('item_001', 'process_category', 'admin', '行政办公', 1, 1),
('item_002', 'process_category', 'finance', '财务审批', 2, 1),
('item_003', 'process_category', 'hr', '人事管理', 3, 1),
('item_004', 'process_category', 'it', 'IT服务', 4, 1),
('item_005', 'process_category', 'project', '项目管理', 5, 1),
('item_006', 'process_category', 'purchase', '采购管理', 6, 1),
('item_007', 'process_category', 'contract', '合同管理', 7, 1),
('item_008', 'process_category', 'other', '其他', 99, 1)
ON DUPLICATE KEY UPDATE `dict_code` = `dict_code`;

-- 任务优先级字典
INSERT INTO `sys_dict` (`id`, `dict_code`, `dict_name`, `description`, `status`) VALUES
('dict_002', 'task_priority', '任务优先级', '流程任务的优先级设置', 1)
ON DUPLICATE KEY UPDATE `dict_code` = `dict_code`;

INSERT INTO `sys_dict_item` (`id`, `dict_code`, `item_code`, `item_name`, `sort_order`, `status`) VALUES
('item_101', 'task_priority', 'urgent', '紧急', 1, 1),
('item_102', 'task_priority', 'high', '高', 2, 1),
('item_103', 'task_priority', 'normal', '普通', 3, 1),
('item_104', 'task_priority', 'low', '低', 4, 1)
ON DUPLICATE KEY UPDATE `dict_code` = `dict_code`;

-- 审批结果字典
INSERT INTO `sys_dict` (`id`, `dict_code`, `dict_name`, `description`, `status`) VALUES
('dict_003', 'approval_result', '审批结果', '任务审批的处理结果', 1)
ON DUPLICATE KEY UPDATE `dict_code` = `dict_code`;

INSERT INTO `sys_dict_item` (`id`, `dict_code`, `item_code`, `item_name`, `sort_order`, `status`) VALUES
('item_201', 'approval_result', 'approve', '通过', 1, 1),
('item_202', 'approval_result', 'reject', '驳回', 2, 1),
('item_203', 'approval_result', 'transfer', '转办', 3, 1),
('item_204', 'approval_result', 'withdraw', '撤回', 4, 1)
ON DUPLICATE KEY UPDATE `dict_code` = `dict_code`;
