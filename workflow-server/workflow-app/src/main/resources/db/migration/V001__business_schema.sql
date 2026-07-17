-- Business schema baseline.
-- Flowable engine tables are managed by Flowable.

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignee_config` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `node_config_id` varchar(64) NOT NULL COMMENT '节点配置ID',
  `assignee_type` varchar(20) DEFAULT 'user' COMMENT '指定类型',
  `assignee_value` varchar(200) DEFAULT NULL COMMENT '执行人值',
  `assignee_name` varchar(200) DEFAULT NULL COMMENT '执行人显示名称',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_node_config` (`node_config_id`),
  KEY `idx_assignee_type` (`assignee_type`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执行人配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_code_rule` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `prefix` varchar(20) DEFAULT '' COMMENT '编码前缀，如：CG、DD',
  `date_format` varchar(20) DEFAULT 'yyyyMMdd' COMMENT '日期格式，如：yyyyMMdd、yyyy-MM-dd',
  `seq_length` int DEFAULT '6' COMMENT '序列号位数，如：6表示000001',
  `seq_type` varchar(20) DEFAULT 'DAY' COMMENT '序列号重置周期：DAY按天、MONTH按月、YEAR按年、NEVER不重置',
  `current_seq` int DEFAULT '0' COMMENT '当前序列号值',
  `seq_date` varchar(20) DEFAULT '' COMMENT '当前序列号对应的日期（用于判断重置）',
  `example` varchar(100) DEFAULT '' COMMENT '编码示例',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_code` (`entity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体编码规则配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_data` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `data_no` varchar(100) DEFAULT NULL COMMENT '数据编号',
  `title` varchar(500) DEFAULT NULL COMMENT '数据标题',
  `submitter_id` varchar(64) DEFAULT NULL COMMENT '提交人ID',
  `submitter_name` varchar(100) DEFAULT NULL COMMENT '提交人名称',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `data_json` longtext COMMENT '数据内容JSON',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `process_status` varchar(20) DEFAULT 'draft' COMMENT '流程状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_no` (`data_no`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_submitter` (`submitter_id`),
  KEY `idx_process_status` (`process_status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_definition` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `entity_name` varchar(200) NOT NULL COMMENT '实体名称',
  `description` text COMMENT '实体描述',
  `table_name` varchar(100) DEFAULT NULL COMMENT '数据库表名',
  `enable_process` tinyint DEFAULT '0' COMMENT '是否启用流程',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '绑定流程定义ID',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_code` (`entity_code`),
  KEY `idx_process_def` (`process_definition_id`),
  KEY `idx_enable_process` (`enable_process`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_id` varchar(64) NOT NULL COMMENT '实体定义ID',
  `field_id` varchar(100) DEFAULT NULL COMMENT '旧字段编码（兼容保留）',
  `field_code` varchar(100) DEFAULT NULL COMMENT '字段编码',
  `field_name` varchar(200) NOT NULL COMMENT '字段名称',
  `field_type` varchar(50) NOT NULL COMMENT '字段类型',
  `db_type` varchar(100) DEFAULT NULL COMMENT '数据库字段类型',
  `field_length` int DEFAULT NULL COMMENT '字段长度',
  `length` int DEFAULT NULL COMMENT '长度',
  `precision` int DEFAULT NULL COMMENT '精度',
  `is_required` tinyint DEFAULT '0' COMMENT '是否必填',
  `is_unique` tinyint DEFAULT '0' COMMENT '是否唯一',
  `is_searchable` tinyint DEFAULT '0' COMMENT '是否可搜索',
  `is_list_show` tinyint DEFAULT '1' COMMENT '是否列表显示',
  `default_value` varchar(200) DEFAULT NULL COMMENT '默认值',
  `options_json` text COMMENT '选项配置JSON',
  `validate_rules` text COMMENT '验证规则JSON',
  `dict_type` varchar(100) DEFAULT NULL COMMENT '字典类型',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `is_system` tinyint DEFAULT '0' COMMENT '是否系统字段',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `ref_entity_id` varchar(64) DEFAULT NULL COMMENT '关联实体ID（用于子表单）',
  `ref_entity_type` varchar(20) DEFAULT NULL COMMENT '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）',
  `display_mode` varchar(20) DEFAULT 'embedded' COMMENT '显示方式：embedded-嵌入, tab-Tab页（用于子表单）',
  `ref_field_code` varchar(100) DEFAULT NULL COMMENT '关联字段编码（用于子表单数据关联）',
  `file_types` varchar(500) DEFAULT NULL COMMENT '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）',
  `file_max_size` int DEFAULT NULL COMMENT '文件大小限制（MB，用于附件类型）',
  `file_max_count` int DEFAULT NULL COMMENT '文件数量限制（用于附件类型）',
  `field_precision` int DEFAULT NULL COMMENT '小数位数（精度）',
  `db_column_name` varchar(100) DEFAULT NULL COMMENT '数据库列名（下划线命名）',
  `editable` tinyint DEFAULT '1' COMMENT '是否可编辑',
  `is_published` tinyint DEFAULT '0' COMMENT '是否已发布到数据表',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_field` (`entity_id`,`field_id`),
  KEY `idx_entity_id` (`entity_id`),
  KEY `idx_field_type` (`field_type`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体字段表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_field_file_item` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `field_id` varchar(64) NOT NULL COMMENT '关联字段ID（entity_field.id）',
  `item_name` varchar(200) NOT NULL COMMENT '附件项名称（如：项目章程、需求文档）',
  `file_types` varchar(500) DEFAULT NULL COMMENT '允许的文件类型（逗号分隔，如：.pdf,.doc,.docx）',
  `max_size` int DEFAULT NULL COMMENT '单文件大小限制（MB）',
  `max_count` int DEFAULT NULL COMMENT '文件数量限制',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_field_id` (`field_id`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体字段附件项配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_flow_status_mapping` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `process_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义配置ID',
  `process_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标识',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `sequence_flow_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '连线ID（BPMN中的sequenceFlowId）',
  `source_node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源节点ID（如:startEvent1, userTask1）',
  `source_node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源节点名称',
  `target_node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标节点ID',
  `target_node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标节点名称',
  `entity_status` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体数据状态值（如:审批中、已通过、已驳回）',
  `entity_status_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实体数据状态编码（用于系统识别）',
  `status_category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类：NEW-新建流程状态、PROCESSING-审批中流程状态、COMPLETED-已完成流程状态、TERMINATED-终止流程状态',
  `condition_expression` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条件表达式',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明描述',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除：0-否 1-是',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_source_target` (`process_config_id`,`source_node_id`,`target_node_id`,`deleted`),
  KEY `idx_process_config` (`process_config_id`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_source_node` (`source_node_id`),
  KEY `idx_target_node` (`target_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体流程状态映射表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_id` varchar(64) NOT NULL COMMENT '实体定义ID',
  `form_name` varchar(200) NOT NULL COMMENT '表单名称',
  `form_key` varchar(100) NOT NULL COMMENT '表单标识',
  `description` varchar(500) DEFAULT NULL COMMENT '表单描述',
  `layout_type` varchar(20) DEFAULT 'vertical' COMMENT '布局类型',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认表单：0-否 1-是',
  `status` tinyint DEFAULT '1' COMMENT '状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `custom_component` varchar(100) DEFAULT NULL COMMENT '自定义表单组件注册名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_form` (`entity_id`,`form_key`),
  KEY `idx_entity_id` (`entity_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体表单表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_form_field` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `form_id` varchar(64) NOT NULL COMMENT '实体表单ID',
  `field_id` varchar(100) NOT NULL COMMENT '字段编码',
  `field_name` varchar(200) DEFAULT NULL COMMENT '字段名称',
  `field_label` varchar(200) DEFAULT NULL COMMENT '字段标签',
  `field_type` varchar(50) DEFAULT NULL COMMENT '字段类型',
  `component_type` varchar(50) DEFAULT NULL COMMENT '组件类型',
  `is_required` tinyint DEFAULT '0' COMMENT '是否必填',
  `is_readonly` tinyint DEFAULT '0' COMMENT '是否只读',
  `is_hidden` tinyint DEFAULT '0' COMMENT '是否隐藏',
  `default_value` varchar(200) DEFAULT NULL COMMENT '默认值',
  `placeholder` varchar(200) DEFAULT NULL COMMENT '占位提示',
  `component_props` text COMMENT '组件属性JSON',
  `grid_span` int DEFAULT '24' COMMENT '栅格宽度',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_form_field` (`form_id`,`field_id`),
  KEY `idx_form_id` (`form_id`),
  KEY `idx_field_id` (`field_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体表单字段表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体定义ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `list_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表标识（唯一，如：default、myList）',
  `list_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '列表名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认列表',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `custom_component` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义列表组件注册名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_key` (`entity_id`,`list_key`,`deleted`),
  KEY `idx_entity_id` (`entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_field` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `list_config_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属列表配置ID',
  `field_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体字段ID（关联entity_field）',
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段编码',
  `field_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称（快照）',
  `sort_order` int DEFAULT '0' COMMENT '列排序号',
  `width` int DEFAULT '0' COMMENT '列宽度（0表示自适应）',
  `show_in_list` tinyint DEFAULT '1' COMMENT '是否显示在列表',
  `is_query` tinyint DEFAULT '1' COMMENT '是否作为查询条件',
  `query_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'LIKE' COMMENT '查询方式：EQ/NE/LIKE/GT/LT/BETWEEN/IN',
  `align` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'left' COMMENT '对齐方式：left/center/right',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `data_source_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'ENTITY_FIELD' COMMENT '数据源类型：ENTITY_FIELD(实体字段)/REFERENCE(关联查询)/AGGREGATE(聚合统计)/CUSTOM_PROVIDER(自定义处理器)',
  `data_source_config` text COLLATE utf8mb4_unicode_ci COMMENT '数据源配置JSON',
  `render_component` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端渲染组件名',
  `formatter` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '简单格式化表达式（如 yyyy-MM-dd、#0.00）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_field` (`list_config_id`,`field_id`,`deleted`),
  KEY `idx_list_config_id` (`list_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体列表字段配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_permission` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `rule_name` varchar(200) NOT NULL COMMENT '规则名称',
  `priority` int DEFAULT '0' COMMENT '优先级，数字越大越优先',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用（0否/1是）',
  `match_config` json DEFAULT NULL COMMENT '匹配条件配置JSON',
  `filter_config` json DEFAULT NULL COMMENT '数据过滤配置JSON',
  `combine_mode` varchar(20) DEFAULT 'UNION' COMMENT '规则叠加方式：UNION(并集)/INTERSECT(交集)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除（0否/1是）',
  PRIMARY KEY (`id`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体列表数据权限规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_list_permission_delegate` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) DEFAULT NULL COMMENT '实体编码（为空表示全部实体）',
  `from_user_id` varchar(64) NOT NULL COMMENT '委托方用户ID',
  `to_user_id` varchar(64) NOT NULL COMMENT '受托方用户ID',
  `delegate_scope` varchar(50) DEFAULT 'PERSONAL' COMMENT '委托范围：ALL(全部)/PERSONAL(仅本人数据)/CONDITION(按条件)',
  `delegate_config` json DEFAULT NULL COMMENT '委托范围配置JSON',
  `start_time` datetime DEFAULT NULL COMMENT '委托开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '委托结束时间',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用（0否/1是）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_from_user` (`from_user_id`),
  KEY `idx_to_user` (`to_user_id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据权限委托表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_publish_history` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `entity_id` varchar(64) NOT NULL COMMENT '实体定义ID',
  `entity_code` varchar(100) NOT NULL COMMENT '实体编码',
  `entity_name` varchar(200) NOT NULL COMMENT '实体名称',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '发布时绑定流程定义ID',
  `version` int NOT NULL COMMENT '版本号',
  `version_description` varchar(500) DEFAULT NULL COMMENT '版本描述',
  `fields_snapshot` longtext COMMENT '字段定义快照JSON',
  `table_ddl` longtext COMMENT '表结构DDL',
  `publish_type` varchar(20) NOT NULL COMMENT '发布类型',
  `changes_description` varchar(1000) DEFAULT NULL COMMENT '变更描述',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人ID',
  `published_by_name` varchar(100) DEFAULT NULL COMMENT '发布人姓名',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态',
  PRIMARY KEY (`id`),
  KEY `idx_entity_publish_history_entity` (`entity_id`,`version`),
  KEY `idx_entity_publish_history_code` (`entity_code`),
  KEY `idx_entity_publish_history_process` (`process_definition_id`),
  KEY `idx_entity_publish_history_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体发布版本历史';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_relation` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `parent_entity_id` varchar(64) NOT NULL COMMENT '主实体ID',
  `parent_entity_code` varchar(100) NOT NULL COMMENT '主实体编码',
  `parent_field_id` varchar(64) DEFAULT NULL COMMENT '主实体关系字段ID',
  `parent_field_code` varchar(100) NOT NULL COMMENT '主实体关系字段编码',
  `relation_code` varchar(100) NOT NULL COMMENT '关系编码',
  `relation_name` varchar(200) DEFAULT NULL COMMENT '关系名称',
  `child_entity_id` varchar(64) NOT NULL COMMENT '子实体ID',
  `child_entity_code` varchar(100) NOT NULL COMMENT '子实体编码',
  `child_ref_field_code` varchar(100) NOT NULL COMMENT '子实体回填主数据ID字段',
  `relation_type` varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY' COMMENT '关系类型：ONE_TO_ONE/ONE_TO_MANY',
  `cascade_delete` tinyint DEFAULT '1' COMMENT '主数据删除时是否级联删除子数据',
  `required` tinyint DEFAULT '0' COMMENT '是否必填',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_field` (`parent_entity_id`,`parent_field_code`),
  KEY `idx_parent_entity` (`parent_entity_id`,`enabled`,`deleted`),
  KEY `idx_parent_code` (`parent_entity_code`,`enabled`,`deleted`),
  KEY `idx_child_entity` (`child_entity_id`),
  KEY `idx_relation_code` (`relation_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实体关系定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_status` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `status_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态编码（系统标识）',
  `status_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态名称（显示用）',
  `status_category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态分类：NEW-新建、PROCESSING-审批中、COMPLETED-已完成、TERMINATED-终止',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态说明',
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态颜色（如：#67C23A）',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_status` (`entity_code`,`status_code`,`deleted`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_status_category` (`status_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体状态定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_status_history` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID',
  `entity_data_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体数据ID',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实体编码',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例ID',
  `from_status` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更前状态',
  `to_status` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更后状态',
  `from_node_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源节点ID',
  `to_node_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标节点ID',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型：AUTO-自动流转, MANUAL-人工审批',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_entity_data` (`entity_data_id`),
  KEY `idx_entity_code` (`entity_code`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体数据状态历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flow_action` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_config_id` varchar(64) NOT NULL COMMENT '流程定义配置ID',
  `sequence_flow_id` varchar(64) NOT NULL COMMENT '顺序流ID（bpmn元素ID）',
  `action_name` varchar(100) NOT NULL COMMENT '动作名称',
  `description` varchar(500) DEFAULT NULL COMMENT '动作描述',
  `interface_name` varchar(200) NOT NULL COMMENT '接口名称（Spring Bean或类名）',
  `method_name` varchar(50) DEFAULT 'execute' COMMENT '方法名',
  `params_json` text COMMENT '参数JSON',
  `sort_order` int DEFAULT '0' COMMENT '执行顺序',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿 PUBLISHED已发布 DISABLED已禁用',
  `version_id` varchar(64) DEFAULT NULL COMMENT '所属版本ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `deleted` int DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_process_config` (`process_config_id`),
  KEY `idx_sequence_flow` (`process_config_id`,`sequence_flow_id`),
  KEY `idx_version` (`version_id`),
  KEY `idx_status` (`status`),
  KEY `idx_action_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程动作配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `form_config` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `node_config_id` varchar(64) NOT NULL COMMENT '节点配置ID',
  `form_name` varchar(200) DEFAULT NULL COMMENT '表单名称',
  `form_key` varchar(100) DEFAULT NULL COMMENT '表单标识',
  `entity_form_id` varchar(64) DEFAULT NULL COMMENT '实体表单ID',
  `is_readonly` tinyint DEFAULT '0' COMMENT '是否只读',
  `description` varchar(500) DEFAULT NULL COMMENT '表单描述',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_node_config` (`node_config_id`),
  KEY `idx_entity_form` (`entity_form_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表单配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `form_field_config` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `form_config_id` varchar(64) NOT NULL COMMENT '表单配置ID',
  `field_name` varchar(100) DEFAULT NULL COMMENT '字段名称',
  `field_key` varchar(100) DEFAULT NULL COMMENT '字段标识',
  `field_type` varchar(50) DEFAULT NULL COMMENT '字段类型',
  `is_required` tinyint DEFAULT '0' COMMENT '是否必填',
  `default_value` varchar(200) DEFAULT NULL COMMENT '默认值',
  `options_json` text COMMENT '选项JSON',
  `validate_rules` varchar(500) DEFAULT NULL COMMENT '验证规则JSON',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_form_config` (`form_config_id`),
  KEY `idx_field_key` (`field_key`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表单字段配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `node_config` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_config_id` varchar(64) NOT NULL COMMENT '流程定义配置ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点ID',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(50) DEFAULT NULL COMMENT '节点类型',
  `config_json` text COMMENT '配置JSON',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `skip_node` tinyint DEFAULT '0' COMMENT '是否跳过此节点（仅第一个用户任务节点可设置）：0-不跳过，1-跳过',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node` (`process_config_id`,`node_id`),
  KEY `idx_node_id` (`node_id`),
  KEY `idx_node_type` (`node_type`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_skip_node` (`skip_node`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_cc_record` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_instance_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '流程定义ID',
  `process_key` varchar(100) DEFAULT NULL COMMENT '流程Key',
  `process_name` varchar(200) DEFAULT NULL COMMENT '流程名称',
  `business_key` varchar(200) DEFAULT NULL COMMENT '业务Key',
  `node_id` varchar(100) DEFAULT NULL COMMENT '节点ID',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `cc_user_id` varchar(64) DEFAULT NULL COMMENT '抄送人ID',
  `cc_user_name` varchar(100) DEFAULT NULL COMMENT '抄送人名称',
  `cc_type` varchar(20) DEFAULT 'AUTO' COMMENT '抄送类型：AUTO自动/MANUAL手动',
  `cc_timing` varchar(20) DEFAULT NULL COMMENT '抄送时机',
  `read_status` varchar(20) DEFAULT 'UNREAD' COMMENT '阅读状态：UNREAD未读/READ已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_cc_user` (`cc_user_id`,`read_status`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程抄送记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_common_opinion` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `opinion_content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '意见内容',
  `opinion_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'APPROVE' COMMENT '意见类型：APPROVE同意/REJECT驳回/TRANSFER转办',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `use_count` int DEFAULT '0' COMMENT '使用次数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='常用审批意见';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_definition_config` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程标识',
  `process_name` varchar(200) NOT NULL COMMENT '流程名称',
  `description` text COMMENT '流程描述',
  `category` varchar(100) DEFAULT NULL COMMENT '流程分类',
  `version` int DEFAULT '0' COMMENT '当前版本号',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态',
  `bpmn_xml` longtext COMMENT 'BPMN XML',
  `entity_id` varchar(64) DEFAULT NULL COMMENT '绑定实体ID',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_key` (`process_key`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`),
  KEY `idx_entity_id` (`entity_id`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程定义配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_draft` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `draft_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '草稿编码',
  `process_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID',
  `process_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程名称',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体编码',
  `entity_data_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体数据ID（临时数据）',
  `business_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务主键',
  `form_data` json NOT NULL COMMENT '表单数据',
  `draft_title` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '草稿标题',
  `draft_summary` text COLLATE utf8mb4_unicode_ci COMMENT '草稿摘要',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人ID',
  `user_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/SUBMITTED已提交/DELETED已删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `draft_code` (`draft_code`),
  KEY `idx_user` (`user_id`,`status`,`updated_at`),
  KEY `idx_process` (`process_definition_id`),
  KEY `idx_entity` (`entity_code`,`entity_data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程草稿箱';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_approval` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_config_id` varchar(64) NOT NULL COMMENT '流程配置ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点ID',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用',
  `comment_label` varchar(100) DEFAULT '审批意见' COMMENT '意见标签',
  `options_json` text COMMENT '选项JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_node_approval` (`process_config_id`,`node_id`),
  KEY `idx_process_config` (`process_config_id`),
  KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点审批配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_node_form` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_config_id` varchar(64) NOT NULL COMMENT '流程配置ID',
  `node_id` varchar(100) NOT NULL COMMENT '节点ID',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `form_id` varchar(64) NOT NULL COMMENT '实体表单ID',
  `is_readonly` tinyint DEFAULT '0' COMMENT '是否只读',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_process_node` (`process_config_id`,`node_id`),
  KEY `idx_form_id` (`form_id`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点表单绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_operation_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_time` datetime DEFAULT NULL COMMENT '操作时间',
  `operation_comment` text COLLATE utf8mb4_unicode_ci,
  `old_value` text COLLATE utf8mb4_unicode_ci COMMENT '旧值（JSON）',
  `new_value` text COLLATE utf8mb4_unicode_ci COMMENT '新值（JSON）',
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_process` (`process_instance_id`,`operation_time`),
  KEY `idx_operator` (`operator_id`,`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_instance_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '流程定义ID',
  `process_key` varchar(100) DEFAULT NULL COMMENT '流程标识',
  `process_name` varchar(200) DEFAULT NULL COMMENT '流程名称',
  `business_key` varchar(100) DEFAULT NULL COMMENT '业务主键',
  `task_id` varchar(64) NOT NULL COMMENT 'Flowable任务ID',
  `node_id` varchar(100) DEFAULT NULL COMMENT '节点ID',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(50) DEFAULT NULL COMMENT '节点类型',
  `entity_code` varchar(100) DEFAULT NULL COMMENT '实体编码',
  `entity_data_id` varchar(64) DEFAULT NULL COMMENT '实体数据ID',
  `form_key` varchar(100) DEFAULT NULL COMMENT '表单标识',
  `assignee_id` varchar(64) DEFAULT NULL COMMENT '执行人ID',
  `assignee_name` varchar(100) DEFAULT NULL COMMENT '执行人名称',
  `assignee_type` varchar(20) DEFAULT 'user' COMMENT '执行人类型',
  `form_data` json DEFAULT NULL COMMENT '表单数据',
  `status` varchar(20) DEFAULT 'todo' COMMENT '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回',
  `action` varchar(20) DEFAULT NULL COMMENT '处理动作',
  `comment` text COMMENT '处理意见',
  `start_time` datetime DEFAULT NULL COMMENT '任务开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '任务结束时间',
  `due_time` datetime DEFAULT NULL COMMENT '截止时间',
  `duration` bigint DEFAULT NULL COMMENT '处理时长',
  `timeout_hours` int DEFAULT NULL COMMENT '超时时间',
  `timeout_action` varchar(50) DEFAULT NULL COMMENT '超时策略',
  `timeout_handled` tinyint DEFAULT '0' COMMENT '是否已处理超时',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_process_def` (`process_definition_id`),
  KEY `idx_assignee` (`assignee_id`),
  KEY `idx_status` (`status`),
  KEY `idx_entity` (`entity_code`,`entity_data_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_task_instance` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务实例ID',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Flowable任务ID',
  `task_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务节点Key',
  `task_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务名称',
  `process_definition_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程定义ID',
  `process_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程名称',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体编码',
  `entity_data_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体数据ID',
  `business_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务主键',
  `assignee_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被指派人ID',
  `assignee_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '被指派人姓名',
  `owner_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务所有人ID',
  `candidate_users` text COLLATE utf8mb4_unicode_ci COMMENT '候选人ID列表（JSON）',
  `candidate_groups` text COLLATE utf8mb4_unicode_ci COMMENT '候选组列表（JSON）',
  `task_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务类型：TODO待办/DONE已办/DRAFT草稿/CC抄送',
  `action_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型：SUBMIT/APPROVE/REJECT/TRANSFER/RETURN/DELEGATE',
  `action_comment` text COLLATE utf8mb4_unicode_ci COMMENT '处理意见',
  `form_data` json DEFAULT NULL COMMENT '表单数据快照',
  `due_time` datetime DEFAULT NULL COMMENT '截止时间',
  `priority` int DEFAULT '50' COMMENT '优先级 0-100',
  `is_read` tinyint DEFAULT '0' COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `start_time` datetime DEFAULT NULL COMMENT '任务开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '任务结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '处理耗时（毫秒）',
  `parent_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父任务ID（用于会签）',
  `root_task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '根任务ID',
  `delegation_state` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '委托状态：PENDING/RESOLVED',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_process_instance` (`process_instance_id`),
  KEY `idx_assignee_type` (`assignee_id`,`task_type`),
  KEY `idx_entity` (`entity_code`,`entity_data_id`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_due_time` (`due_time`),
  KEY `idx_task_type` (`task_type`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程任务实例表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_version_history` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `process_config_id` varchar(64) NOT NULL COMMENT '流程定义配置ID',
  `process_key` varchar(100) NOT NULL COMMENT '流程标识',
  `process_name` varchar(200) NOT NULL COMMENT '流程名称',
  `version` int NOT NULL COMMENT '版本号',
  `version_description` varchar(500) DEFAULT NULL COMMENT '版本说明',
  `bpmn_xml` longtext COMMENT 'BPMN XML',
  `node_forms_snapshot` longtext COMMENT '节点表单绑定快照JSON',
  `published_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人ID',
  `deployment_id` varchar(64) DEFAULT NULL COMMENT 'Flowable部署ID',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_version` (`process_config_id`,`version`),
  KEY `idx_process_key` (`process_key`),
  KEY `idx_deployment_id` (`deployment_id`),
  KEY `idx_status` (`status`),
  KEY `idx_version_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程版本历史表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_category` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '0',
  `category_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `category_code` (`category_code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表分类';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_dataset` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `report_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dataset_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dataset_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dataset_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据集类型：SQL/ENTITY/API',
  `source_config` json NOT NULL COMMENT '数据源配置',
  `field_mappings` json DEFAULT NULL COMMENT '字段映射',
  `cache_config` json DEFAULT NULL COMMENT '缓存配置',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表数据集';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报表ID',
  `report_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报表编码',
  `report_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报表名称',
  `report_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报表类型：TABLE表格/CHART图表/DASHBOARD大屏/PRINT打印',
  `category_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报表分类ID',
  `dataset_config` json DEFAULT NULL COMMENT '数据集配置（支持多数据集）',
  `layout_config` json NOT NULL COMMENT '报表布局配置',
  `params_config` json DEFAULT NULL COMMENT '报表参数配置',
  `style_config` json DEFAULT NULL COMMENT '样式配置',
  `permission_config` json DEFAULT NULL COMMENT '权限配置',
  `version` int DEFAULT '1' COMMENT '版本号',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `report_code` (`report_code`),
  KEY `idx_category` (`category_id`),
  KEY `idx_code` (`report_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_schedule` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `report_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `schedule_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cron_expression` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `param_values` json DEFAULT NULL COMMENT '参数值',
  `export_format` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'EXCEL' COMMENT '导出格式',
  `notify_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'EMAIL' COMMENT '通知类型',
  `notify_targets` text COLLATE utf8mb4_unicode_ci COMMENT '通知目标',
  `last_run_time` datetime DEFAULT NULL,
  `next_run_time` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ENABLED',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report` (`report_id`),
  KEY `idx_next_run` (`next_run_time`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表定时任务';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_category` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '0',
  `category_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `category_code` (`category_code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务分类表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务ID',
  `service_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务编码',
  `service_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务名称',
  `service_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ORCHESTRATION' COMMENT '服务类型：ORCHESTRATION编排/SCRIPT脚本/PROXY代理',
  `category_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类ID',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '服务描述',
  `input_params` json DEFAULT NULL COMMENT '输入参数定义',
  `output_params` json DEFAULT NULL COMMENT '输出参数定义',
  `flow_config` json DEFAULT NULL COMMENT '流程配置（DAG）',
  `variables` json DEFAULT NULL COMMENT '变量定义',
  `timeout_ms` int DEFAULT '30000' COMMENT '超时时间（毫秒）',
  `retry_config` json DEFAULT NULL COMMENT '重试配置',
  `exception_handler` json DEFAULT NULL COMMENT '异常处理配置',
  `version` int DEFAULT '1' COMMENT '版本号',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `service_code` (`service_code`),
  KEY `idx_code` (`service_code`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_execution_log` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务ID',
  `execution_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行实例ID',
  `trigger_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发类型：MANUAL手动/SCHEDULE定时/EVENT事件/API接口',
  `trigger_source` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发来源',
  `input_params` text COLLATE utf8mb4_unicode_ci COMMENT '输入参数',
  `output_result` text COLLATE utf8mb4_unicode_ci COMMENT '输出结果',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：RUNNING运行中/SUCCESS成功/FAILED失败/TIMEOUT超时',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` int DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `node_executions` json DEFAULT NULL COMMENT '节点执行详情',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_service` (`service_id`,`start_time`),
  KEY `idx_execution` (`execution_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务执行记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `service_node` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务ID',
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点唯一标识',
  `node_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型：START/END/ENTITY_CRUD/HTTP/SQL/SCRIPT/CONDITION/PARALLEL/JOIN/LOOP/SUBFLOW/PROCESS/MESSAGE/DELAY/MAPPING/LOG',
  `node_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节点名称',
  `position_x` decimal(10,2) DEFAULT NULL COMMENT '画布X坐标',
  `position_y` decimal(10,2) DEFAULT NULL COMMENT '画布Y坐标',
  `config` json NOT NULL COMMENT '节点配置',
  `input_mapping` json DEFAULT NULL COMMENT '输入参数映射',
  `output_mapping` json DEFAULT NULL COMMENT '输出参数映射',
  `next_nodes` json DEFAULT NULL COMMENT '下游节点ID列表',
  `condition_expression` text COLLATE utf8mb4_unicode_ci COMMENT '条件表达式',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_node` (`service_id`,`node_id`),
  KEY `idx_service` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务节点表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `dict_code` varchar(100) NOT NULL COMMENT '字典编码',
  `dict_name` varchar(100) NOT NULL COMMENT '字典名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `status` char(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
  `sort` int DEFAULT '0' COMMENT '排序',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `dict_id` varchar(64) NOT NULL COMMENT '所属字典ID',
  `dict_code` varchar(100) NOT NULL COMMENT '冗余：字典编码（便于直接查询）',
  `parent_id` varchar(64) DEFAULT '0' COMMENT '父项ID，0表示顶级',
  `item_code` varchar(100) NOT NULL COMMENT '项编码',
  `item_label` varchar(100) NOT NULL COMMENT '项标签/显示文本',
  `item_value` varchar(200) NOT NULL COMMENT '项值',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` char(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dict_id` (`dict_id`),
  KEY `idx_dict_code` (`dict_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_group` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `group_code` varchar(100) NOT NULL COMMENT '组编码',
  `group_name` varchar(200) NOT NULL COMMENT '组名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `parent_id` varchar(64) DEFAULT NULL COMMENT '父组ID',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` tinyint DEFAULT '0' COMMENT '状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_code` (`group_code`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户组表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` varchar(64) NOT NULL COMMENT '菜单ID',
  `parent_id` varchar(64) DEFAULT '0' COMMENT '父菜单ID，0为顶级菜单',
  `menu_name` varchar(100) NOT NULL COMMENT '菜单名称',
  `menu_type` char(1) DEFAULT 'M' COMMENT '菜单类型：M-目录 C-菜单 F-按钮',
  `icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `path` varchar(200) DEFAULT NULL COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `perm` varchar(200) DEFAULT NULL COMMENT '权限标识，如：system:user:list',
  `status` char(1) DEFAULT '0' COMMENT '状态：0-启用 1-禁用',
  `visible` char(1) DEFAULT '0' COMMENT '显示状态：0-显示 1-隐藏',
  `is_frame` char(1) DEFAULT '0' COMMENT '是否外链：0-否 1-是',
  `is_cache` char(1) DEFAULT '0' COMMENT '是否缓存：0-缓存 1-不缓存',
  `query` varchar(255) DEFAULT NULL COMMENT '路由参数',
  `keep_alive` char(1) DEFAULT '0' COMMENT '是否缓存：0-不缓存 1-缓存',
  `breadcrumb` char(1) DEFAULT '1' COMMENT '是否显示面包屑：0-否 1-是',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` int DEFAULT '0' COMMENT '是否删除：0-未删除 1-已删除',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `entity_code` varchar(100) DEFAULT NULL COMMENT '关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_sort` (`sort`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_entity_code` (`entity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_organization` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `org_code` varchar(100) NOT NULL COMMENT '组织编码（唯一）',
  `org_name` varchar(100) NOT NULL COMMENT '组织名称',
  `type` varchar(20) NOT NULL COMMENT '类型：org-组织，dept-部门',
  `parent_id` varchar(64) DEFAULT '0' COMMENT '父级ID（顶级为0）',
  `level` int DEFAULT '0' COMMENT '层级（0为顶级）',
  `path` varchar(500) DEFAULT '/' COMMENT '完整路径，如：/0/1/5/10/',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `leader_id` varchar(64) DEFAULT NULL COMMENT '负责人ID',
  `leader_name` varchar(100) DEFAULT NULL COMMENT '负责人名称（冗余）',
  `phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `status` varchar(10) DEFAULT '0' COMMENT '状态：0-启用，1-禁用',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '是否删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_code` (`org_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_type` (`type`),
  KEY `idx_path` (`path`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织部门表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `role_code` varchar(100) NOT NULL COMMENT '角色编码',
  `role_name` varchar(200) NOT NULL COMMENT '角色名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `status` tinyint DEFAULT '0' COMMENT '状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `role_id` varchar(64) NOT NULL COMMENT '角色ID',
  `menu_id` varchar(64) NOT NULL COMMENT '菜单ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(100) DEFAULT NULL COMMENT '密码',
  `nickname` varchar(100) DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(200) DEFAULT NULL COMMENT '头像URL',
  `status` tinyint DEFAULT '0' COMMENT '状态',
  `deleted` tinyint DEFAULT '0' COMMENT '是否删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `org_id` varchar(64) DEFAULT NULL COMMENT '组织ID',
  `dept_id` varchar(64) DEFAULT NULL COMMENT '部门ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_group` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `group_id` varchar(64) NOT NULL COMMENT '组ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_group` (`user_id`,`group_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户组关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `role_id` varchar(64) NOT NULL COMMENT '角色ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `view_button_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `view_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `button_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `button_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `button_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '按钮位置：TOOLBAR工具栏/ROW行操作/BATCH批量操作',
  `action_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动作类型',
  `action_config` json DEFAULT NULL COMMENT '动作配置',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `style` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '样式',
  `sort_order` int DEFAULT '0',
  `visible_condition` text COLLATE utf8mb4_unicode_ci COMMENT '显示条件',
  `permission_code` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限标识',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_view` (`view_id`,`button_type`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视图按钮配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `view_definition` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图ID',
  `view_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图编码',
  `view_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图名称',
  `view_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图类型：LIST列表/CHART图表/DASHBOARD看板/DETAIL详情',
  `entity_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联实体编码',
  `data_source_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ENTITY' COMMENT '数据源类型：ENTITY/SQL/API',
  `data_source_config` json DEFAULT NULL COMMENT '数据源配置',
  `layout_config` json DEFAULT NULL COMMENT '布局配置',
  `style_config` json DEFAULT NULL COMMENT '样式配置',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认视图',
  `version` int DEFAULT '1' COMMENT '版本号',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `view_code` (`view_code`),
  KEY `idx_entity` (`entity_code`),
  KEY `idx_code` (`view_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视图定义表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `view_field_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `view_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段编码',
  `field_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '字段显示名',
  `field_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '字段类型',
  `sort_order` int DEFAULT '0' COMMENT '显示顺序',
  `width` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列宽',
  `align` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'left' COMMENT '对齐方式',
  `is_show` tinyint DEFAULT '1' COMMENT '是否显示',
  `is_sortable` tinyint DEFAULT '0' COMMENT '是否可排序',
  `is_searchable` tinyint DEFAULT '0' COMMENT '是否可搜索',
  `formatter_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '格式化类型',
  `formatter_config` json DEFAULT NULL COMMENT '格式化配置',
  `fixed` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '固定列',
  `show_in_list` tinyint DEFAULT '1',
  `show_in_detail` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_view` (`view_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视图字段配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `view_query_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `view_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `field_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `query_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '查询类型：EQ/LIKE/BETWEEN等',
  `component_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件类型',
  `component_config` json DEFAULT NULL COMMENT '组件配置',
  `default_value` text COLLATE utf8mb4_unicode_ci COMMENT '默认值',
  `placeholder` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `is_advanced` tinyint DEFAULT '0' COMMENT '是否高级查询',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_view` (`view_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视图查询条件配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workbench_config` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `config_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置编码',
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户ID（为空表示系统默认）',
  `layout_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'GRID' COMMENT '布局类型：GRID/FREE',
  `layout_config` json NOT NULL COMMENT '布局配置',
  `widgets_config` json DEFAULT NULL COMMENT '组件配置列表',
  `is_default` tinyint DEFAULT '0' COMMENT '是否默认',
  `is_system` tinyint DEFAULT '0' COMMENT '是否系统预设',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_code` (`config_code`),
  KEY `idx_user` (`user_id`),
  KEY `idx_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作台配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workbench_shortcut` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shortcut_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shortcut_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型：MENU/URL/ENTITY',
  `target_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标ID（菜单ID或URL）',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快捷入口表';
/*!40101 SET character_set_client = @saved_cs_client */;
