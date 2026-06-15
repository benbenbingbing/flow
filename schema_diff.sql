-- 由 compare_schema.py 自动生成
-- 对比 V001__business_schema.sql 与数据库实际结构
-- 仅包含 V001 中定义但数据库中缺失的字段/表

USE `workflow`;

-- 表 assignee_config 缺失字段：
ALTER TABLE `assignee_config` ADD COLUMN `deleted` tinyint DEFAULT '0' COMMENT '是否删除';

-- 表 entity_data 缺失字段：
ALTER TABLE `entity_data` ADD COLUMN `process_status` varchar(20) DEFAULT 'draft' COMMENT '流程状态';

-- 表 entity_definition 缺失字段：
ALTER TABLE `entity_definition` ADD COLUMN `table_name` varchar(100) DEFAULT NULL COMMENT '数据库表名';
ALTER TABLE `entity_definition` ADD COLUMN `deleted` tinyint DEFAULT '0' COMMENT '是否删除';
ALTER TABLE `entity_definition` ADD COLUMN `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人';

-- 表 entity_field 缺失字段：
ALTER TABLE `entity_field` ADD COLUMN `field_id` varchar(100) DEFAULT NULL COMMENT '旧字段编码（兼容保留）';
ALTER TABLE `entity_field` ADD COLUMN `length` int DEFAULT NULL COMMENT '长度';
ALTER TABLE `entity_field` ADD COLUMN `precision` int DEFAULT NULL COMMENT '精度';
ALTER TABLE `entity_field` ADD COLUMN `is_searchable` tinyint DEFAULT '0' COMMENT '是否可搜索';
ALTER TABLE `entity_field` ADD COLUMN `is_list_show` tinyint DEFAULT '1' COMMENT '是否列表显示';
ALTER TABLE `entity_field` ADD COLUMN `dict_type` varchar(100) DEFAULT NULL COMMENT '字典类型';
ALTER TABLE `entity_field` ADD COLUMN `deleted` tinyint DEFAULT '0' COMMENT '是否删除';

-- 表 entity_flow_status_mapping 缺失字段：
ALTER TABLE `entity_flow_status_mapping` ADD COLUMN `entity_status` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体数据状态值（如:审批中、已通过、已驳回）';
ALTER TABLE `entity_flow_status_mapping` ADD COLUMN `status_category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类：NEW-新建流程状态、PROCESSING-审批中流程状态、COMPLETED-已完成流程状态、TERMINATED-终止流程状态';

-- 表 entity_form 缺失字段：
ALTER TABLE `entity_form` ADD COLUMN `created_by` varchar(64) DEFAULT NULL COMMENT '创建人';
ALTER TABLE `entity_form` ADD COLUMN `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人';

-- 表 entity_form_field 缺失字段：
ALTER TABLE `entity_form_field` ADD COLUMN `deleted` tinyint DEFAULT '0' COMMENT '是否删除';

-- 表 entity_publish_history 缺失字段：
ALTER TABLE `entity_publish_history` ADD COLUMN `process_definition_id` varchar(64) DEFAULT NULL COMMENT '发布时绑定流程定义ID';

-- 表 form_config 缺失字段：
ALTER TABLE `form_config` ADD COLUMN `entity_form_id` varchar(64) DEFAULT NULL COMMENT '实体表单ID';
ALTER TABLE `form_config` ADD COLUMN `deleted` tinyint DEFAULT '0' COMMENT '是否删除';

-- 表 form_field_config 缺失字段：
ALTER TABLE `form_field_config` ADD COLUMN `deleted` tinyint DEFAULT '0' COMMENT '是否删除';

-- 表 node_config 缺失字段：
ALTER TABLE `node_config` ADD COLUMN `deleted` tinyint DEFAULT '0' COMMENT '是否删除';

-- 表 process_definition_config 缺失字段：
ALTER TABLE `process_definition_config` ADD COLUMN `entity_id` varchar(64) DEFAULT NULL COMMENT '绑定实体ID';
ALTER TABLE `process_definition_config` ADD COLUMN `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人';

-- 表 process_node_form 缺失字段：
ALTER TABLE `process_node_form` ADD COLUMN `sort_order` int DEFAULT '0' COMMENT '排序号';

-- 表 process_task 缺失字段：
ALTER TABLE `process_task` ADD COLUMN `timeout_hours` int DEFAULT NULL COMMENT '超时时间';
ALTER TABLE `process_task` ADD COLUMN `timeout_action` varchar(50) DEFAULT NULL COMMENT '超时策略';
ALTER TABLE `process_task` ADD COLUMN `timeout_handled` tinyint DEFAULT '0' COMMENT '是否已处理超时';
ALTER TABLE `process_task` ADD COLUMN `priority` int DEFAULT '0' COMMENT '优先级';

-- 表 process_version_history 缺失字段：
ALTER TABLE `process_version_history` ADD COLUMN `node_forms_snapshot` longtext COMMENT '节点表单绑定快照JSON';
ALTER TABLE `process_version_history` ADD COLUMN `created_by` varchar(64) DEFAULT NULL COMMENT '创建人';
ALTER TABLE `process_version_history` ADD COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `process_version_history` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 表 sys_group 缺失字段：
ALTER TABLE `sys_group` ADD COLUMN `parent_id` varchar(64) DEFAULT NULL COMMENT '父组ID';
ALTER TABLE `sys_group` ADD COLUMN `sort_order` int DEFAULT '0' COMMENT '排序号';

-- 表 sys_role 缺失字段：
ALTER TABLE `sys_role` ADD COLUMN `sort_order` int DEFAULT '0' COMMENT '排序号';
