-- PostgreSQL 16 平台基础表空库初始化脚本。
-- 基于项目数据库导出的 MySQL 平台 DDL 与系统种子数据转换；仅用于预先创建的空 schema。
-- 包含 187 张平台及 Flowable 表、表和字段注释、索引、约束与系统初始化数据。
-- 不含 biz_* 动态业务表、业务记录及普通用户；admin 初始禁用，需通过 Bootstrap 设置密码后激活。
-- 每次导入随机生成独立的配置迁移签名密钥；本文件是独立初始化脚本，不属于 Flyway 迁移。

-- JSON 合法性用于与 MySQL JSON_VALID CHECK 保持一致；错误输入返回 false。
CREATE OR REPLACE FUNCTION flow_sql_is_json(p_value text) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
  PERFORM p_value::jsonb;
  RETURN true;
EXCEPTION WHEN others THEN
  RETURN false;
END;
$$;

-- 表结构与表级约束。
CREATE TABLE "act_app_appdef" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "name_" varchar(255),
  "key_" varchar(255) NOT NULL,
  "version_" integer NOT NULL,
  "category_" varchar(255),
  "deployment_id_" varchar(255),
  "resource_name_" varchar(4000),
  "description_" varchar(4000),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_app_appdef_act_app_appdef" PRIMARY KEY ("id_")
);

CREATE TABLE "act_app_deployment" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "category_" varchar(255),
  "key_" varchar(255),
  "deploy_time_" timestamp(3),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_app_deployment_act_app_deployment" PRIMARY KEY ("id_")
);

CREATE TABLE "act_app_deployment_resource" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "deployment_id_" varchar(255),
  "resource_bytes_" bytea,
  CONSTRAINT "pk_act_app_deployment_resource_act_app_deployment_resource" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_casedef" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "name_" varchar(255),
  "key_" varchar(255) NOT NULL,
  "version_" integer NOT NULL,
  "category_" varchar(255),
  "deployment_id_" varchar(255),
  "resource_name_" varchar(4000),
  "description_" varchar(4000),
  "has_graphical_notation_" smallint,
  "tenant_id_" varchar(255) DEFAULT '',
  "dgrm_resource_name_" varchar(4000),
  "has_start_form_key_" smallint,
  CONSTRAINT "pk_act_cmmn_casedef_act_cmmn_casedef" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_deployment" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "category_" varchar(255),
  "key_" varchar(255),
  "deploy_time_" timestamp(3),
  "parent_deployment_id_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_cmmn_deployment_act_cmmn_deployment" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_deployment_resource" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "deployment_id_" varchar(255),
  "resource_bytes_" bytea,
  "generated_" smallint,
  CONSTRAINT "pk_act_cmmn_deployment_resource_act_cmmn_deployment_resource" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_hi_case_inst" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "business_key_" varchar(255),
  "name_" varchar(255),
  "parent_id_" varchar(255),
  "case_def_id_" varchar(255),
  "state_" varchar(255),
  "start_time_" timestamp(3),
  "end_time_" timestamp(3),
  "start_user_id_" varchar(255),
  "callback_id_" varchar(255),
  "callback_type_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  "reference_id_" varchar(255),
  "reference_type_" varchar(255),
  "last_reactivation_time_" timestamp(3),
  "last_reactivation_user_id_" varchar(255),
  "business_status_" varchar(255),
  CONSTRAINT "pk_act_cmmn_hi_case_inst_act_cmmn_hi_case_inst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_hi_mil_inst" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "name_" varchar(255) NOT NULL,
  "time_stamp_" timestamp(3),
  "case_inst_id_" varchar(255) NOT NULL,
  "case_def_id_" varchar(255) NOT NULL,
  "element_id_" varchar(255) NOT NULL,
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_cmmn_hi_mil_inst_act_cmmn_hi_mil_inst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_hi_plan_item_inst" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "name_" varchar(255),
  "state_" varchar(255),
  "case_def_id_" varchar(255),
  "case_inst_id_" varchar(255),
  "stage_inst_id_" varchar(255),
  "is_stage_" smallint,
  "element_id_" varchar(255),
  "item_definition_id_" varchar(255),
  "item_definition_type_" varchar(255),
  "create_time_" timestamp(3),
  "last_available_time_" timestamp(3),
  "last_enabled_time_" timestamp(3),
  "last_disabled_time_" timestamp(3),
  "last_started_time_" timestamp(3),
  "last_suspended_time_" timestamp(3),
  "completed_time_" timestamp(3),
  "occurred_time_" timestamp(3),
  "terminated_time_" timestamp(3),
  "exit_time_" timestamp(3),
  "ended_time_" timestamp(3),
  "last_updated_time_" timestamp(3),
  "start_user_id_" varchar(255),
  "reference_id_" varchar(255),
  "reference_type_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  "entry_criterion_id_" varchar(255),
  "exit_criterion_id_" varchar(255),
  "show_in_overview_" smallint,
  "extra_value_" varchar(255),
  "derived_case_def_id_" varchar(255),
  "last_unavailable_time_" timestamp(3),
  "assignee_" varchar(255),
  "completed_by_" varchar(255),
  CONSTRAINT "pk_act_cmmn_hi_plan_item_inst_act_cmmn_hi_plan_item_inst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_ru_case_inst" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "business_key_" varchar(255),
  "name_" varchar(255),
  "parent_id_" varchar(255),
  "case_def_id_" varchar(255),
  "state_" varchar(255),
  "start_time_" timestamp(3),
  "start_user_id_" varchar(255),
  "callback_id_" varchar(255),
  "callback_type_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  "lock_time_" timestamp(3),
  "is_completeable_" smallint,
  "reference_id_" varchar(255),
  "reference_type_" varchar(255),
  "lock_owner_" varchar(255),
  "last_reactivation_time_" timestamp(3),
  "last_reactivation_user_id_" varchar(255),
  "business_status_" varchar(255),
  CONSTRAINT "pk_act_cmmn_ru_case_inst_act_cmmn_ru_case_inst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_ru_mil_inst" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255) NOT NULL,
  "time_stamp_" timestamp(3),
  "case_inst_id_" varchar(255) NOT NULL,
  "case_def_id_" varchar(255) NOT NULL,
  "element_id_" varchar(255) NOT NULL,
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_cmmn_ru_mil_inst_act_cmmn_ru_mil_inst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_ru_plan_item_inst" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "case_def_id_" varchar(255),
  "case_inst_id_" varchar(255),
  "stage_inst_id_" varchar(255),
  "is_stage_" smallint,
  "element_id_" varchar(255),
  "name_" varchar(255),
  "state_" varchar(255),
  "create_time_" timestamp(3),
  "start_user_id_" varchar(255),
  "reference_id_" varchar(255),
  "reference_type_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  "item_definition_id_" varchar(255),
  "item_definition_type_" varchar(255),
  "is_completeable_" smallint,
  "is_count_enabled_" smallint,
  "var_count_" integer,
  "sentry_part_inst_count_" integer,
  "last_available_time_" timestamp(3),
  "last_enabled_time_" timestamp(3),
  "last_disabled_time_" timestamp(3),
  "last_started_time_" timestamp(3),
  "last_suspended_time_" timestamp(3),
  "completed_time_" timestamp(3),
  "occurred_time_" timestamp(3),
  "terminated_time_" timestamp(3),
  "exit_time_" timestamp(3),
  "ended_time_" timestamp(3),
  "entry_criterion_id_" varchar(255),
  "exit_criterion_id_" varchar(255),
  "extra_value_" varchar(255),
  "derived_case_def_id_" varchar(255),
  "last_unavailable_time_" timestamp(3),
  "assignee_" varchar(255),
  "completed_by_" varchar(255),
  CONSTRAINT "pk_act_cmmn_ru_plan_item_inst_act_cmmn_ru_plan_item_inst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_cmmn_ru_sentry_part_inst" (
  "id_" varchar(255) NOT NULL,
  "rev_" integer NOT NULL,
  "case_def_id_" varchar(255),
  "case_inst_id_" varchar(255),
  "plan_item_inst_id_" varchar(255),
  "on_part_id_" varchar(255),
  "if_part_id_" varchar(255),
  "time_stamp_" timestamp(3),
  CONSTRAINT "pk_act_cmmn_ru_sentry_part_inst_act_cmmn_ru_sentry_part_inst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_dmn_decision" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "version_" integer,
  "key_" varchar(255),
  "category_" varchar(255),
  "deployment_id_" varchar(255),
  "tenant_id_" varchar(255),
  "resource_name_" varchar(255),
  "description_" varchar(255),
  "decision_type_" varchar(255),
  CONSTRAINT "pk_act_dmn_decision_act_dmn_decision" PRIMARY KEY ("id_")
);

CREATE TABLE "act_dmn_deployment" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "category_" varchar(255),
  "deploy_time_" timestamp(3),
  "tenant_id_" varchar(255),
  "parent_deployment_id_" varchar(255),
  CONSTRAINT "pk_act_dmn_deployment_act_dmn_deployment" PRIMARY KEY ("id_")
);

CREATE TABLE "act_dmn_deployment_resource" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "deployment_id_" varchar(255),
  "resource_bytes_" bytea,
  CONSTRAINT "pk_act_dmn_deployment_resource_act_dmn_deployment_resource" PRIMARY KEY ("id_")
);

CREATE TABLE "act_dmn_hi_decision_execution" (
  "id_" varchar(255) NOT NULL,
  "decision_definition_id_" varchar(255),
  "deployment_id_" varchar(255),
  "start_time_" timestamp(3),
  "end_time_" timestamp(3),
  "instance_id_" varchar(255),
  "execution_id_" varchar(255),
  "activity_id_" varchar(255),
  "failed_" smallint DEFAULT 0,
  "tenant_id_" varchar(255),
  "execution_json_" text,
  "scope_type_" varchar(255),
  CONSTRAINT "pk_act_dmn_hi_decision_execution_act_dmn_hi_decision_execution" PRIMARY KEY ("id_")
);

CREATE TABLE "act_evt_log" (
  "log_nr_" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "type_" varchar(64),
  "proc_def_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "execution_id_" varchar(64),
  "task_id_" varchar(64),
  "time_stamp_" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "user_id_" varchar(255),
  "data_" bytea,
  "lock_owner_" varchar(255),
  "lock_time_" timestamp(3),
  "is_processed_" smallint DEFAULT '0',
  CONSTRAINT "pk_act_evt_log_act_evt_log" PRIMARY KEY ("log_nr_")
);

CREATE TABLE "act_ge_bytearray" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "name_" varchar(255),
  "deployment_id_" varchar(64),
  "bytes_" bytea,
  "generated_" smallint,
  CONSTRAINT "pk_act_ge_bytearray_act_ge_bytearray" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ge_property" (
  "name_" varchar(64) NOT NULL,
  "value_" varchar(300),
  "rev_" integer,
  CONSTRAINT "pk_act_ge_property_act_ge_property" PRIMARY KEY ("name_")
);

CREATE TABLE "act_hi_actinst" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer DEFAULT '1',
  "proc_def_id_" varchar(64) NOT NULL,
  "proc_inst_id_" varchar(64) NOT NULL,
  "execution_id_" varchar(64) NOT NULL,
  "act_id_" varchar(255) NOT NULL,
  "task_id_" varchar(64),
  "call_proc_inst_id_" varchar(64),
  "act_name_" varchar(255),
  "act_type_" varchar(255) NOT NULL,
  "assignee_" varchar(255),
  "start_time_" timestamp(3) NOT NULL,
  "end_time_" timestamp(3),
  "transaction_order_" integer,
  "duration_" bigint,
  "delete_reason_" varchar(4000),
  "tenant_id_" varchar(255) DEFAULT '',
  "completed_by_" varchar(255),
  CONSTRAINT "pk_act_hi_actinst_act_hi_actinst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_attachment" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "user_id_" varchar(255),
  "name_" varchar(255),
  "description_" varchar(4000),
  "type_" varchar(255),
  "task_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "url_" varchar(4000),
  "content_id_" varchar(64),
  "time_" timestamp(3),
  CONSTRAINT "pk_act_hi_attachment_act_hi_attachment" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_comment" (
  "id_" varchar(64) NOT NULL,
  "type_" varchar(255),
  "time_" timestamp(3) NOT NULL,
  "user_id_" varchar(255),
  "task_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "action_" varchar(255),
  "message_" varchar(4000),
  "full_msg_" bytea,
  CONSTRAINT "pk_act_hi_comment_act_hi_comment" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_detail" (
  "id_" varchar(64) NOT NULL,
  "type_" varchar(255) NOT NULL,
  "proc_inst_id_" varchar(64),
  "execution_id_" varchar(64),
  "task_id_" varchar(64),
  "act_inst_id_" varchar(64),
  "name_" varchar(255) NOT NULL,
  "var_type_" varchar(255),
  "rev_" integer,
  "time_" timestamp(3) NOT NULL,
  "bytearray_id_" varchar(64),
  "double_" double precision,
  "long_" bigint,
  "text_" varchar(4000),
  "text2_" varchar(4000),
  CONSTRAINT "pk_act_hi_detail_act_hi_detail" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_entitylink" (
  "id_" varchar(64) NOT NULL,
  "link_type_" varchar(255),
  "create_time_" timestamp(3),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "parent_element_id_" varchar(255),
  "ref_scope_id_" varchar(255),
  "ref_scope_type_" varchar(255),
  "ref_scope_definition_id_" varchar(255),
  "root_scope_id_" varchar(255),
  "root_scope_type_" varchar(255),
  "hierarchy_type_" varchar(255),
  CONSTRAINT "pk_act_hi_entitylink_act_hi_entitylink" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_identitylink" (
  "id_" varchar(64) NOT NULL,
  "group_id_" varchar(255),
  "type_" varchar(255),
  "user_id_" varchar(255),
  "task_id_" varchar(64),
  "create_time_" timestamp(3),
  "proc_inst_id_" varchar(64),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  CONSTRAINT "pk_act_hi_identitylink_act_hi_identitylink" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_procinst" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer DEFAULT '1',
  "proc_inst_id_" varchar(64) NOT NULL,
  "business_key_" varchar(255),
  "proc_def_id_" varchar(64) NOT NULL,
  "start_time_" timestamp(3) NOT NULL,
  "end_time_" timestamp(3),
  "duration_" bigint,
  "start_user_id_" varchar(255),
  "start_act_id_" varchar(255),
  "end_act_id_" varchar(255),
  "super_process_instance_id_" varchar(64),
  "delete_reason_" varchar(4000),
  "tenant_id_" varchar(255) DEFAULT '',
  "name_" varchar(255),
  "callback_id_" varchar(255),
  "callback_type_" varchar(255),
  "reference_id_" varchar(255),
  "reference_type_" varchar(255),
  "propagated_stage_inst_id_" varchar(255),
  "business_status_" varchar(255),
  CONSTRAINT "pk_act_hi_procinst_act_hi_procinst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_taskinst" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer DEFAULT '1',
  "proc_def_id_" varchar(64),
  "task_def_id_" varchar(64),
  "task_def_key_" varchar(255),
  "proc_inst_id_" varchar(64),
  "execution_id_" varchar(64),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "propagated_stage_inst_id_" varchar(255),
  "name_" varchar(255),
  "parent_task_id_" varchar(64),
  "description_" varchar(4000),
  "owner_" varchar(255),
  "assignee_" varchar(255),
  "start_time_" timestamp(3) NOT NULL,
  "claim_time_" timestamp(3),
  "end_time_" timestamp(3),
  "duration_" bigint,
  "delete_reason_" varchar(4000),
  "priority_" integer,
  "due_date_" timestamp(3),
  "form_key_" varchar(255),
  "category_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  "last_updated_time_" timestamp(3),
  "state_" varchar(255),
  "in_progress_time_" timestamp(3),
  "in_progress_started_by_" varchar(255),
  "claimed_by_" varchar(255),
  "suspended_time_" timestamp(3),
  "suspended_by_" varchar(255),
  "completed_by_" varchar(255),
  "in_progress_due_date_" timestamp(3),
  CONSTRAINT "pk_act_hi_taskinst_act_hi_taskinst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_tsk_log" (
  "id_" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "type_" varchar(64),
  "task_id_" varchar(64) NOT NULL,
  "time_stamp_" timestamp(3) NOT NULL,
  "user_id_" varchar(255),
  "data_" varchar(4000),
  "execution_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "scope_id_" varchar(255),
  "scope_definition_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_hi_tsk_log_act_hi_tsk_log" PRIMARY KEY ("id_")
);

CREATE TABLE "act_hi_varinst" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer DEFAULT '1',
  "proc_inst_id_" varchar(64),
  "execution_id_" varchar(64),
  "task_id_" varchar(64),
  "name_" varchar(255) NOT NULL,
  "var_type_" varchar(100),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "bytearray_id_" varchar(64),
  "double_" double precision,
  "long_" bigint,
  "text_" varchar(4000),
  "text2_" varchar(4000),
  "create_time_" timestamp(3),
  "last_updated_time_" timestamp(3),
  "meta_info_" varchar(4000),
  CONSTRAINT "pk_act_hi_varinst_act_hi_varinst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_id_bytearray" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "name_" varchar(255),
  "bytes_" bytea,
  CONSTRAINT "pk_act_id_bytearray_act_id_bytearray" PRIMARY KEY ("id_")
);

CREATE TABLE "act_id_group" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "name_" varchar(255),
  "type_" varchar(255),
  CONSTRAINT "pk_act_id_group_act_id_group" PRIMARY KEY ("id_")
);

CREATE TABLE "act_id_info" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "user_id_" varchar(64),
  "type_" varchar(64),
  "key_" varchar(255),
  "value_" varchar(255),
  "password_" bytea,
  "parent_id_" varchar(255),
  CONSTRAINT "pk_act_id_info_act_id_info" PRIMARY KEY ("id_")
);

CREATE TABLE "act_id_membership" (
  "user_id_" varchar(64) NOT NULL,
  "group_id_" varchar(64) NOT NULL,
  CONSTRAINT "pk_act_id_membership_act_id_membership" PRIMARY KEY ("user_id_", "group_id_")
);

CREATE TABLE "act_id_priv" (
  "id_" varchar(64) NOT NULL,
  "name_" varchar(255) NOT NULL,
  CONSTRAINT "pk_act_id_priv_act_id_priv" PRIMARY KEY ("id_")
);

CREATE TABLE "act_id_priv_mapping" (
  "id_" varchar(64) NOT NULL,
  "priv_id_" varchar(64) NOT NULL,
  "user_id_" varchar(255),
  "group_id_" varchar(255),
  CONSTRAINT "pk_act_id_priv_mapping_act_id_priv_mapping" PRIMARY KEY ("id_")
);

CREATE TABLE "act_id_property" (
  "name_" varchar(64) NOT NULL,
  "value_" varchar(300),
  "rev_" integer,
  CONSTRAINT "pk_act_id_property_act_id_property" PRIMARY KEY ("name_")
);

CREATE TABLE "act_id_token" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "token_value_" varchar(255),
  "token_date_" timestamp(3),
  "ip_address_" varchar(255),
  "user_agent_" varchar(255),
  "user_id_" varchar(255),
  "token_data_" varchar(2000),
  CONSTRAINT "pk_act_id_token_act_id_token" PRIMARY KEY ("id_")
);

CREATE TABLE "act_id_user" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "first_" varchar(255),
  "last_" varchar(255),
  "display_name_" varchar(255),
  "email_" varchar(255),
  "pwd_" varchar(255),
  "picture_id_" varchar(64),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_id_user_act_id_user" PRIMARY KEY ("id_")
);

CREATE TABLE "act_procdef_info" (
  "id_" varchar(64) NOT NULL,
  "proc_def_id_" varchar(64) NOT NULL,
  "rev_" integer,
  "info_json_id_" varchar(64),
  CONSTRAINT "pk_act_procdef_info_act_procdef_info" PRIMARY KEY ("id_")
);

CREATE TABLE "act_re_deployment" (
  "id_" varchar(64) NOT NULL,
  "name_" varchar(255),
  "category_" varchar(255),
  "key_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  "deploy_time_" timestamp(3),
  "derived_from_" varchar(64),
  "derived_from_root_" varchar(64),
  "parent_deployment_id_" varchar(255),
  "engine_version_" varchar(255),
  CONSTRAINT "pk_act_re_deployment_act_re_deployment" PRIMARY KEY ("id_")
);

CREATE TABLE "act_re_model" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "name_" varchar(255),
  "key_" varchar(255),
  "category_" varchar(255),
  "create_time_" timestamp(3),
  "last_update_time_" timestamp(3),
  "version_" integer,
  "meta_info_" varchar(4000),
  "deployment_id_" varchar(64),
  "editor_source_value_id_" varchar(64),
  "editor_source_extra_value_id_" varchar(64),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_re_model_act_re_model" PRIMARY KEY ("id_")
);

CREATE TABLE "act_re_procdef" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "category_" varchar(255),
  "name_" varchar(255),
  "key_" varchar(255) NOT NULL,
  "version_" integer NOT NULL,
  "deployment_id_" varchar(64),
  "resource_name_" varchar(4000),
  "dgrm_resource_name_" varchar(4000),
  "description_" varchar(4000),
  "has_start_form_key_" smallint,
  "has_graphical_notation_" smallint,
  "suspension_state_" integer,
  "tenant_id_" varchar(255) DEFAULT '',
  "engine_version_" varchar(255),
  "derived_from_" varchar(64),
  "derived_from_root_" varchar(64),
  "derived_version_" integer NOT NULL DEFAULT '0',
  CONSTRAINT "pk_act_re_procdef_act_re_procdef" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_actinst" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer DEFAULT '1',
  "proc_def_id_" varchar(64) NOT NULL,
  "proc_inst_id_" varchar(64) NOT NULL,
  "execution_id_" varchar(64) NOT NULL,
  "act_id_" varchar(255) NOT NULL,
  "task_id_" varchar(64),
  "call_proc_inst_id_" varchar(64),
  "act_name_" varchar(255),
  "act_type_" varchar(255) NOT NULL,
  "assignee_" varchar(255),
  "start_time_" timestamp(3) NOT NULL,
  "end_time_" timestamp(3),
  "duration_" bigint,
  "transaction_order_" integer,
  "delete_reason_" varchar(4000),
  "tenant_id_" varchar(255) DEFAULT '',
  "completed_by_" varchar(255),
  CONSTRAINT "pk_act_ru_actinst_act_ru_actinst" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_deadletter_job" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "category_" varchar(255),
  "type_" varchar(255) NOT NULL,
  "exclusive_" smallint,
  "execution_id_" varchar(64),
  "process_instance_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "element_id_" varchar(255),
  "element_name_" varchar(255),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "correlation_id_" varchar(255),
  "exception_stack_id_" varchar(64),
  "exception_msg_" varchar(4000),
  "duedate_" timestamp(3),
  "repeat_" varchar(255),
  "handler_type_" varchar(255),
  "handler_cfg_" varchar(4000),
  "custom_values_id_" varchar(64),
  "create_time_" timestamp(3),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_ru_deadletter_job_act_ru_deadletter_job" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_entitylink" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "create_time_" timestamp(3),
  "link_type_" varchar(255),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "parent_element_id_" varchar(255),
  "ref_scope_id_" varchar(255),
  "ref_scope_type_" varchar(255),
  "ref_scope_definition_id_" varchar(255),
  "root_scope_id_" varchar(255),
  "root_scope_type_" varchar(255),
  "hierarchy_type_" varchar(255),
  CONSTRAINT "pk_act_ru_entitylink_act_ru_entitylink" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_event_subscr" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "event_type_" varchar(255) NOT NULL,
  "event_name_" varchar(255),
  "execution_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "activity_id_" varchar(64),
  "configuration_" varchar(255),
  "created_" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "proc_def_id_" varchar(64),
  "sub_scope_id_" varchar(64),
  "scope_id_" varchar(64),
  "scope_definition_id_" varchar(64),
  "scope_type_" varchar(64),
  "lock_time_" timestamp(3),
  "lock_owner_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  "scope_definition_key_" varchar(255),
  CONSTRAINT "pk_act_ru_event_subscr_act_ru_event_subscr" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_execution" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "proc_inst_id_" varchar(64),
  "business_key_" varchar(255),
  "parent_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "super_exec_" varchar(64),
  "root_proc_inst_id_" varchar(64),
  "act_id_" varchar(255),
  "is_active_" smallint,
  "is_concurrent_" smallint,
  "is_scope_" smallint,
  "is_event_scope_" smallint,
  "is_mi_root_" smallint,
  "suspension_state_" integer,
  "cached_ent_state_" integer,
  "tenant_id_" varchar(255) DEFAULT '',
  "name_" varchar(255),
  "start_act_id_" varchar(255),
  "start_time_" timestamp(3),
  "start_user_id_" varchar(255),
  "lock_time_" timestamp(3),
  "lock_owner_" varchar(255),
  "is_count_enabled_" smallint,
  "evt_subscr_count_" integer,
  "task_count_" integer,
  "job_count_" integer,
  "timer_job_count_" integer,
  "susp_job_count_" integer,
  "deadletter_job_count_" integer,
  "external_worker_job_count_" integer,
  "var_count_" integer,
  "id_link_count_" integer,
  "callback_id_" varchar(255),
  "callback_type_" varchar(255),
  "reference_id_" varchar(255),
  "reference_type_" varchar(255),
  "propagated_stage_inst_id_" varchar(255),
  "business_status_" varchar(255),
  CONSTRAINT "pk_act_ru_execution_act_ru_execution" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_external_job" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "category_" varchar(255),
  "type_" varchar(255) NOT NULL,
  "lock_exp_time_" timestamp(3),
  "lock_owner_" varchar(255),
  "exclusive_" smallint,
  "execution_id_" varchar(64),
  "process_instance_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "element_id_" varchar(255),
  "element_name_" varchar(255),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "correlation_id_" varchar(255),
  "retries_" integer,
  "exception_stack_id_" varchar(64),
  "exception_msg_" varchar(4000),
  "duedate_" timestamp(3),
  "repeat_" varchar(255),
  "handler_type_" varchar(255),
  "handler_cfg_" varchar(4000),
  "custom_values_id_" varchar(64),
  "create_time_" timestamp(3),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_ru_external_job_act_ru_external_job" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_history_job" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "lock_exp_time_" timestamp(3),
  "lock_owner_" varchar(255),
  "retries_" integer,
  "exception_stack_id_" varchar(64),
  "exception_msg_" varchar(4000),
  "handler_type_" varchar(255),
  "handler_cfg_" varchar(4000),
  "custom_values_id_" varchar(64),
  "adv_handler_cfg_id_" varchar(64),
  "create_time_" timestamp(3),
  "scope_type_" varchar(255),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_ru_history_job_act_ru_history_job" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_identitylink" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "group_id_" varchar(255),
  "type_" varchar(255),
  "user_id_" varchar(255),
  "task_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  CONSTRAINT "pk_act_ru_identitylink_act_ru_identitylink" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_job" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "category_" varchar(255),
  "type_" varchar(255) NOT NULL,
  "lock_exp_time_" timestamp(3),
  "lock_owner_" varchar(255),
  "exclusive_" smallint,
  "execution_id_" varchar(64),
  "process_instance_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "element_id_" varchar(255),
  "element_name_" varchar(255),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "correlation_id_" varchar(255),
  "retries_" integer,
  "exception_stack_id_" varchar(64),
  "exception_msg_" varchar(4000),
  "duedate_" timestamp(3),
  "repeat_" varchar(255),
  "handler_type_" varchar(255),
  "handler_cfg_" varchar(4000),
  "custom_values_id_" varchar(64),
  "create_time_" timestamp(3),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_ru_job_act_ru_job" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_suspended_job" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "category_" varchar(255),
  "type_" varchar(255) NOT NULL,
  "exclusive_" smallint,
  "execution_id_" varchar(64),
  "process_instance_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "element_id_" varchar(255),
  "element_name_" varchar(255),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "correlation_id_" varchar(255),
  "retries_" integer,
  "exception_stack_id_" varchar(64),
  "exception_msg_" varchar(4000),
  "duedate_" timestamp(3),
  "repeat_" varchar(255),
  "handler_type_" varchar(255),
  "handler_cfg_" varchar(4000),
  "custom_values_id_" varchar(64),
  "create_time_" timestamp(3),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_ru_suspended_job_act_ru_suspended_job" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_task" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "execution_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "task_def_id_" varchar(64),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "propagated_stage_inst_id_" varchar(255),
  "name_" varchar(255),
  "parent_task_id_" varchar(64),
  "description_" varchar(4000),
  "task_def_key_" varchar(255),
  "owner_" varchar(255),
  "assignee_" varchar(255),
  "delegation_" varchar(64),
  "priority_" integer,
  "create_time_" timestamp(3),
  "due_date_" timestamp(3),
  "category_" varchar(255),
  "suspension_state_" integer,
  "tenant_id_" varchar(255) DEFAULT '',
  "form_key_" varchar(255),
  "claim_time_" timestamp(3),
  "is_count_enabled_" smallint,
  "var_count_" integer,
  "id_link_count_" integer,
  "sub_task_count_" integer,
  "state_" varchar(255),
  "in_progress_time_" timestamp(3),
  "in_progress_started_by_" varchar(255),
  "claimed_by_" varchar(255),
  "suspended_time_" timestamp(3),
  "suspended_by_" varchar(255),
  "in_progress_due_date_" timestamp(3),
  CONSTRAINT "pk_act_ru_task_act_ru_task" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_timer_job" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "category_" varchar(255),
  "type_" varchar(255) NOT NULL,
  "lock_exp_time_" timestamp(3),
  "lock_owner_" varchar(255),
  "exclusive_" smallint,
  "execution_id_" varchar(64),
  "process_instance_id_" varchar(64),
  "proc_def_id_" varchar(64),
  "element_id_" varchar(255),
  "element_name_" varchar(255),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "scope_definition_id_" varchar(255),
  "correlation_id_" varchar(255),
  "retries_" integer,
  "exception_stack_id_" varchar(64),
  "exception_msg_" varchar(4000),
  "duedate_" timestamp(3),
  "repeat_" varchar(255),
  "handler_type_" varchar(255),
  "handler_cfg_" varchar(4000),
  "custom_values_id_" varchar(64),
  "create_time_" timestamp(3),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_act_ru_timer_job_act_ru_timer_job" PRIMARY KEY ("id_")
);

CREATE TABLE "act_ru_variable" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "type_" varchar(255) NOT NULL,
  "name_" varchar(255) NOT NULL,
  "execution_id_" varchar(64),
  "proc_inst_id_" varchar(64),
  "task_id_" varchar(64),
  "scope_id_" varchar(255),
  "sub_scope_id_" varchar(255),
  "scope_type_" varchar(255),
  "bytearray_id_" varchar(64),
  "double_" double precision,
  "long_" bigint,
  "text_" varchar(4000),
  "text2_" varchar(4000),
  "meta_info_" varchar(4000),
  CONSTRAINT "pk_act_ru_variable_act_ru_variable" PRIMARY KEY ("id_")
);

CREATE TABLE "auth_login_throttle" (
  "throttle_key" char(66) NOT NULL,
  "failure_count" integer NOT NULL DEFAULT '0',
  "window_started_at" timestamp(6) NOT NULL,
  "blocked_until" timestamp(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_auth_login_throttle_auth_login_throttle" PRIMARY KEY ("throttle_key")
);

CREATE TABLE "auth_refresh_session" (
  "id" varchar(64) NOT NULL,
  "user_id" varchar(64) NOT NULL,
  "refresh_token_hash" char(64) NOT NULL,
  "token_version" bigint NOT NULL,
  "create_time" timestamp(6) NOT NULL,
  "last_used_at" timestamp(6) NOT NULL,
  "idle_expires_at" timestamp(6) NOT NULL,
  "absolute_expires_at" timestamp(6) NOT NULL,
  "revoked_at" timestamp(6),
  "revoked_reason" varchar(64),
  CONSTRAINT "pk_auth_refresh_session_auth_refresh_session" PRIMARY KEY ("id")
);

CREATE TABLE "config_asset_baseline" (
  "id" varchar(64) NOT NULL,
  "asset_type" varchar(20) NOT NULL,
  "business_key" varchar(100) NOT NULL,
  "scope_key" varchar(80) NOT NULL DEFAULT 'FULL',
  "source_version" integer NOT NULL,
  "source_hash" varchar(64) NOT NULL,
  "target_version" integer,
  "target_hash" varchar(64) NOT NULL,
  "import_package_id" varchar(64) NOT NULL,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_config_asset_baseline_config_asset_baseline" PRIMARY KEY ("id")
);

CREATE TABLE "config_environment_mapping" (
  "id" varchar(64) NOT NULL,
  "source_type" varchar(30) NOT NULL,
  "source_key" varchar(200) NOT NULL,
  "target_key" varchar(200) NOT NULL,
  "description" varchar(500),
  "enabled" smallint NOT NULL DEFAULT '1',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_config_environment_mapping_config_environment_mapping" PRIMARY KEY ("id")
);

CREATE TABLE "config_export_package" (
  "id" varchar(64) NOT NULL,
  "package_no" varchar(100) NOT NULL,
  "migration_tag" varchar(100) NOT NULL,
  "file_name" varchar(255) NOT NULL,
  "checksum" varchar(64) NOT NULL,
  "signature_value" varchar(128),
  "status" varchar(20) NOT NULL DEFAULT 'READY',
  "asset_count" integer NOT NULL DEFAULT '0',
  "package_data" bytea NOT NULL,
  "created_by" varchar(100),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "download_count" integer NOT NULL DEFAULT '0',
  "last_download_at" timestamp,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_config_export_package_config_export_package" PRIMARY KEY ("id")
);

CREATE TABLE "config_export_package_item" (
  "id" varchar(64) NOT NULL,
  "package_id" varchar(64) NOT NULL,
  "asset_id" varchar(64) NOT NULL,
  "asset_type" varchar(20) NOT NULL,
  "business_key" varchar(100) NOT NULL,
  "source_version" integer NOT NULL,
  "content_hash" varchar(64) NOT NULL,
  "selection_json" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_config_export_package_item_config_export_package_item" PRIMARY KEY ("id")
);

CREATE TABLE "config_import_item" (
  "id" varchar(64) NOT NULL,
  "import_package_id" varchar(64) NOT NULL,
  "asset_type" varchar(20) NOT NULL,
  "business_key" varchar(100) NOT NULL,
  "asset_name" varchar(200) NOT NULL,
  "source_version" integer NOT NULL,
  "source_hash" varchar(64) NOT NULL,
  "target_before_version" integer,
  "target_before_hash" varchar(64),
  "target_after_version" integer,
  "target_after_hash" varchar(64),
  "comparison_status" varchar(30) NOT NULL DEFAULT 'NEW',
  "mapping_status" varchar(20) NOT NULL DEFAULT 'RESOLVED',
  "publish_status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "snapshot_json" text NOT NULL,
  "dependencies_json" text,
  "error_message" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_config_import_item_config_import_item" PRIMARY KEY ("id")
);

CREATE TABLE "config_import_package" (
  "id" varchar(64) NOT NULL,
  "package_no" varchar(100) NOT NULL,
  "source_environment" varchar(100),
  "migration_tag" varchar(100) NOT NULL,
  "file_name" varchar(255) NOT NULL,
  "checksum" varchar(64) NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'UPLOADED',
  "validation_report_json" text,
  "package_data" bytea NOT NULL,
  "imported_by" varchar(100),
  "imported_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "published_by" varchar(100),
  "published_at" timestamp,
  "error_message" text,
  "deleted" smallint NOT NULL DEFAULT '0',
  "signature_status" varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  "signature_confirmed_by" varchar(100),
  "signature_confirmed_at" timestamp(6),
  CONSTRAINT "pk_config_import_package_config_import_package" PRIMARY KEY ("id")
);

CREATE TABLE "config_migration_asset" (
  "id" varchar(64) NOT NULL,
  "asset_type" varchar(20) NOT NULL,
  "business_key" varchar(100) NOT NULL,
  "asset_name" varchar(200) NOT NULL,
  "source_history_id" varchar(64) NOT NULL,
  "source_version" integer NOT NULL,
  "version_description" varchar(500),
  "migration_tag" varchar(100) NOT NULL,
  "mark_for_export" smallint NOT NULL DEFAULT '1',
  "snapshot_completeness" varchar(20) NOT NULL DEFAULT 'COMPLETE',
  "snapshot_schema_version" integer NOT NULL DEFAULT '1',
  "snapshot_json" text NOT NULL,
  "content_hash" varchar(64) NOT NULL,
  "dependencies_json" text,
  "dependency_count" integer NOT NULL DEFAULT '0',
  "missing_dependency_count" integer NOT NULL DEFAULT '0',
  "export_status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "published_at" timestamp,
  "published_by" varchar(100),
  "last_export_at" timestamp,
  "export_count" integer NOT NULL DEFAULT '0',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_config_migration_asset_config_migration_asset" PRIMARY KEY ("id")
);

CREATE TABLE "config_migration_asset_dependency" (
  "id" varchar(64) NOT NULL,
  "asset_id" varchar(64) NOT NULL,
  "dependency_type" varchar(50) NOT NULL,
  "dependency_key" varchar(300) NOT NULL,
  "required" smallint NOT NULL DEFAULT '1',
  "source_description" varchar(500),
  "dependency_document" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "source_asset_type" varchar(20),
  "source_business_key" varchar(100),
  "source_version" integer,
  "reference_location" varchar(500),
  "dependency_strength" varchar(20) NOT NULL DEFAULT 'HARD',
  "parse_status" varchar(20) NOT NULL DEFAULT 'RESOLVED',
  "extracted_at" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT "pk_config_migration_asset_dependency_config_migration_4b442a3e" PRIMARY KEY ("id")
);

CREATE TABLE "embed_allowed_origin" (
  "grant_id" varchar(64) NOT NULL,
  "origin" varchar(255) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_allowed_origin_embed_allowed_origin" PRIMARY KEY ("grant_id", "origin"),
  CONSTRAINT "ck_embed_allowed_origin_chk_embed_allowed_origin_format" CHECK (("origin" ~ '^https://[^/?#]+$'))
);

CREATE TABLE "embed_application_grant" (
  "id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "view_id" varchar(64) NOT NULL,
  "identity_provider_id" varchar(64) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'ACTIVE',
  "trusted_subject_assertion" smallint NOT NULL DEFAULT '0',
  "revision_mode" varchar(16) NOT NULL DEFAULT 'FOLLOW_ACTIVE',
  "pinned_revision" bigint,
  "capability_ceiling_json" text NOT NULL,
  "max_active_sessions_per_user" integer NOT NULL DEFAULT '1',
  "max_session_seconds" integer NOT NULL DEFAULT '1800',
  "launch_limit_per_minute" integer NOT NULL DEFAULT '60',
  "runtime_limit_per_minute" integer NOT NULL DEFAULT '600',
  "max_concurrency" integer NOT NULL DEFAULT '10',
  "expires_at" timestamp(6),
  "lock_version" bigint NOT NULL DEFAULT '1',
  "security_version" bigint NOT NULL DEFAULT '1',
  "create_by" varchar(64) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_by" varchar(64) NOT NULL,
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "revoked_by" varchar(64),
  "revoked_at" timestamp(6),
  CONSTRAINT "pk_embed_application_grant_embed_application_grant" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_application_grant_chk_embed_grant_capabilities" CHECK ((flow_sql_is_json("capability_ceiling_json") and (length("capability_ceiling_json") <= 65536))),
  CONSTRAINT "ck_embed_application_grant_chk_embed_grant_limits" CHECK ((("max_active_sessions_per_user" between 1 and 10000) and ("max_session_seconds" between 60 and 86400) and ("launch_limit_per_minute" between 1 and 10000) and ("runtime_limit_per_minute" between 1 and 100000) and ("max_concurrency" between 1 and 1000))),
  CONSTRAINT "ck_embed_application_grant_chk_embed_grant_revision_mode" CHECK (((("revision_mode" = 'FOLLOW_ACTIVE') and ("pinned_revision" is null)) or (("revision_mode" = 'PINNED') and ("pinned_revision" > 0)))),
  CONSTRAINT "ck_embed_application_grant_chk_embed_grant_revocation" CHECK (((("status" = 'REVOKED') and ("revoked_by" is not null) and ("revoked_at" is not null)) or (("status" <> 'REVOKED') and ("revoked_by" is null) and ("revoked_at" is null)))),
  CONSTRAINT "ck_embed_application_grant_chk_embed_grant_status" CHECK (("status" in ('ACTIVE','DISABLED','REVOKED'))),
  CONSTRAINT "ck_embed_application_grant_chk_embed_grant_trusted_subject" CHECK (("trusted_subject_assertion" in (0,1))),
  CONSTRAINT "ck_embed_application_grant_chk_embed_grant_versions" CHECK ((("lock_version" > 0) and ("security_version" > 0)))
);

CREATE TABLE "embed_assertion_replay" (
  "provider_id" varchar(64) NOT NULL,
  "jti_digest" char(64) NOT NULL,
  "expires_at" timestamp(6) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_assertion_replay_embed_assertion_replay" PRIMARY KEY ("provider_id", "jti_digest"),
  CONSTRAINT "ck_embed_assertion_replay_chk_embed_assertion_replay_digest" CHECK (("jti_digest" ~ '^[0-9a-f]{64}$')),
  CONSTRAINT "ck_embed_assertion_replay_chk_embed_assertion_replay_expiry" CHECK (("expires_at" > "create_time"))
);

CREATE TABLE "embed_external_identity_binding" (
  "id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "identity_provider_id" varchar(64) NOT NULL,
  "subject_digest" char(64) NOT NULL,
  "subject_digest_key_version" varchar(64) NOT NULL,
  "subject_hint" varchar(128) NOT NULL,
  "flow_user_id" varchar(64) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'ACTIVE',
  "binding_version" bigint NOT NULL DEFAULT '1',
  "effective_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "expires_at" timestamp(6),
  "create_by" varchar(64) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_by" varchar(64) NOT NULL,
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "revoked_by" varchar(64),
  "revoked_at" timestamp(6),
  CONSTRAINT "pk_embed_external_identity_binding_embed_external_ide_87243232" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_external_identity_binding_chk_embed_binding_digest" CHECK (("subject_digest" ~ '^[0-9a-f]{64}$')),
  CONSTRAINT "ck_embed_external_identity_binding_chk_embed_binding__4b01a806" CHECK (((("status" = 'REVOKED') and ("revoked_by" is not null) and ("revoked_at" is not null)) or (("status" <> 'REVOKED') and ("revoked_by" is null) and ("revoked_at" is null)))),
  CONSTRAINT "ck_embed_external_identity_binding_chk_embed_binding_status" CHECK (("status" in ('ACTIVE','DISABLED','REVOKED'))),
  CONSTRAINT "ck_embed_external_identity_binding_chk_embed_binding_version" CHECK (("binding_version" > 0)),
  CONSTRAINT "ck_embed_external_identity_binding_chk_embed_binding_window" CHECK ((("expires_at" is null) or ("expires_at" > "effective_at")))
);

CREATE TABLE "embed_identity_provider" (
  "id" varchar(64) NOT NULL,
  "name" varchar(128) NOT NULL,
  "type" varchar(32) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'ACTIVE',
  "issuer" varchar(500),
  "issuer_uniqueness_key" varchar(500) GENERATED ALWAYS AS (coalesce("issuer",'<trusted-external-id>')) STORED,
  "subject_namespace" varchar(128) NOT NULL,
  "audiences_json" text NOT NULL,
  "algorithms_json" text NOT NULL,
  "jwks_mode" varchar(32),
  "jwks_json" text,
  "jwks_url" varchar(2048),
  "clock_skew_seconds" integer NOT NULL DEFAULT '30',
  "max_assertion_lifetime_seconds" integer NOT NULL DEFAULT '60',
  "key_version" bigint NOT NULL DEFAULT '1',
  "lock_version" bigint NOT NULL DEFAULT '1',
  "security_version" bigint NOT NULL DEFAULT '1',
  "create_by" varchar(64) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_by" varchar(64) NOT NULL,
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "revoked_by" varchar(64),
  "revoked_at" timestamp(6),
  CONSTRAINT "pk_embed_identity_provider_embed_identity_provider" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_json" CHECK ((flow_sql_is_json("audiences_json") and flow_sql_is_json("algorithms_json") and (("jwks_json" is null) or flow_sql_is_json("jwks_json")) and (length("audiences_json") <= 65536) and (length("algorithms_json") <= 65536) and (("jwks_json" is null) or (length("jwks_json") <= 262144)))),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_jwks_mode" CHECK ((("jwks_mode" is null) or ("jwks_mode" in ('STATIC_JWK_SET','REMOTE_JWKS')))),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_material" CHECK (((("type" = 'SIGNED_JWT') and ("issuer" is not null) and ((("jwks_mode" = 'STATIC_JWK_SET') and ("jwks_json" is not null) and ("jwks_url" is null)) or (("jwks_mode" = 'REMOTE_JWKS') and ("jwks_json" is null) and ("jwks_url" like 'https://%')))) or (("type" = 'TRUSTED_EXTERNAL_ID') and ("issuer" is null) and ("jwks_mode" is null) and ("jwks_json" is null) and ("jwks_url" is null)))),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_revocation" CHECK (((("status" = 'REVOKED') and ("revoked_by" is not null) and ("revoked_at" is not null)) or (("status" <> 'REVOKED') and ("revoked_by" is null) and ("revoked_at" is null)))),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_status" CHECK (("status" in ('ACTIVE','DISABLED','REVOKED'))),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_timing" CHECK ((("clock_skew_seconds" between 0 and 300) and ("max_assertion_lifetime_seconds" between 1 and 300))),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_type" CHECK (("type" in ('SIGNED_JWT','TRUSTED_EXTERNAL_ID'))),
  CONSTRAINT "ck_embed_identity_provider_chk_embed_provider_versions" CHECK ((("key_version" > 0) and ("lock_version" > 0) and ("security_version" > 0)))
);

CREATE TABLE "embed_launch" (
  "id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "grant_id" varchar(64) NOT NULL,
  "view_id" varchar(64) NOT NULL,
  "view_release_id" varchar(64) NOT NULL,
  "identity_provider_id" varchar(64) NOT NULL,
  "provider_security_version" bigint NOT NULL,
  "application_version" bigint NOT NULL,
  "grant_security_version" bigint NOT NULL,
  "view_security_version" bigint NOT NULL,
  "flow_user_id" varchar(64) NOT NULL,
  "identity_binding_id" varchar(64) NOT NULL,
  "binding_version" bigint NOT NULL,
  "subject_digest" char(64) NOT NULL,
  "subject_digest_key_version" varchar(64) NOT NULL,
  "parent_origin" varchar(255) NOT NULL,
  "channel_id" varchar(128) NOT NULL,
  "entry_mode" varchar(16) NOT NULL,
  "record_id" varchar(64),
  "context_ciphertext" text NOT NULL,
  "context_cipher_key_version" varchar(64) NOT NULL,
  "context_digest" char(64) NOT NULL,
  "context_digest_key_version" varchar(64) NOT NULL,
  "ui_locale" varchar(35) NOT NULL DEFAULT 'zh-CN',
  "ui_theme" varchar(16) NOT NULL DEFAULT 'light',
  "ui_form_presentation" varchar(16) NOT NULL DEFAULT 'seamless',
  "launch_code_digest" char(64) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'ISSUED',
  "expires_at" timestamp(6) NOT NULL,
  "consumed_at" timestamp(6),
  "consumed_session_id" varchar(64),
  "revoked_at" timestamp(6),
  "trace_id" varchar(128),
  "request_id" varchar(128),
  "source_ip_digest" char(64),
  "source_ip_digest_key_version" varchar(64),
  "user_agent_digest" char(64),
  "user_agent_digest_key_version" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_launch_embed_launch" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_context" CHECK ((flow_sql_is_json("context_ciphertext") and (length("context_ciphertext") <= 65536) and (char_length("context_cipher_key_version") > 0) and (char_length("context_digest_key_version") > 0) and (char_length("subject_digest_key_version") > 0))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_digest_keys" CHECK ((((("source_ip_digest" is null) and ("source_ip_digest_key_version" is null)) or (("source_ip_digest" is not null) and ("source_ip_digest_key_version" is not null))) and ((("user_agent_digest" is null) and ("user_agent_digest_key_version" is null)) or (("user_agent_digest" is not null) and ("user_agent_digest_key_version" is not null))))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_digests" CHECK ((("subject_digest" ~ '^[0-9a-f]{64}$') and ("context_digest" ~ '^[0-9a-f]{64}$') and ("launch_code_digest" ~ '^[0-9a-f]{64}$') and (("source_ip_digest" is null) or ("source_ip_digest" ~ '^[0-9a-f]{64}$')) and (("user_agent_digest" is null) or ("user_agent_digest" ~ '^[0-9a-f]{64}$')))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_entry" CHECK (((("entry_mode" in ('LIST','CREATE')) and ("record_id" is null)) or (("entry_mode" in ('VIEW','EDIT')) and ("record_id" is not null)))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_expiry" CHECK (("expires_at" > "create_time")),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_form_presentation" CHECK (("ui_form_presentation" in ('seamless','dialog'))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_lifecycle" CHECK (((("status" = 'ISSUED') and ("consumed_at" is null) and ("consumed_session_id" is null) and ("revoked_at" is null)) or (("status" = 'CONSUMED') and ("consumed_at" is not null) and ("consumed_session_id" is not null) and ("revoked_at" is null)) or (("status" = 'EXPIRED') and ("consumed_at" is null) and ("consumed_session_id" is null) and ("revoked_at" is null)) or (("status" = 'REVOKED') and ("consumed_at" is null) and ("consumed_session_id" is null) and ("revoked_at" is not null)))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_origin" CHECK (("parent_origin" ~ '^https://[^/?#]+$')),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_status" CHECK (("status" in ('ISSUED','CONSUMED','EXPIRED','REVOKED'))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_theme" CHECK (("ui_theme" in ('light','dark','system'))),
  CONSTRAINT "ck_embed_launch_chk_embed_launch_versions" CHECK ((("provider_security_version" > 0) and ("application_version" >= 0) and ("grant_security_version" > 0) and ("view_security_version" > 0) and ("binding_version" > 0)))
);

CREATE TABLE "embed_operation_receipt" (
  "id" varchar(64) NOT NULL,
  "idempotency_record_id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "operation" varchar(64) NOT NULL,
  "actor_scope_digest" char(64) NOT NULL,
  "view_key" varchar(100) NOT NULL,
  "target_type" varchar(64) NOT NULL,
  "target_id" varchar(128) NOT NULL,
  "outcome_code" varchar(64) NOT NULL,
  "record_version" bigint,
  "result_summary_json" text NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_operation_receipt_embed_operation_receipt" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_operation_receipt_chk_embed_receipt_actor_digest" CHECK (("actor_scope_digest" ~ '^[0-9a-f]{64}$')),
  CONSTRAINT "ck_embed_operation_receipt_chk_embed_receipt_operation" CHECK (("operation" in ('EMBED_RECORD_CREATE','EMBED_RECORD_UPDATE','EMBED_ACTION_EXECUTE'))),
  CONSTRAINT "ck_embed_operation_receipt_chk_embed_receipt_record_version" CHECK ((("record_version" is null) or ("record_version" >= 0))),
  CONSTRAINT "ck_embed_operation_receipt_chk_embed_receipt_summary" CHECK ((flow_sql_is_json("result_summary_json") and (length("result_summary_json") <= 8192)))
);

CREATE TABLE "embed_session" (
  "id" varchar(64) NOT NULL,
  "session_token_digest" char(64) NOT NULL,
  "launch_id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "grant_id" varchar(64) NOT NULL,
  "view_id" varchar(64) NOT NULL,
  "view_release_id" varchar(64) NOT NULL,
  "identity_provider_id" varchar(64) NOT NULL,
  "provider_security_version" bigint NOT NULL,
  "flow_user_id" varchar(64) NOT NULL,
  "identity_binding_id" varchar(64) NOT NULL,
  "binding_version" bigint NOT NULL,
  "parent_origin" varchar(255) NOT NULL,
  "channel_id" varchar(128) NOT NULL,
  "entry_mode" varchar(16) NOT NULL,
  "record_id" varchar(64),
  "parent_nonce_digest" char(64) NOT NULL,
  "child_nonce_digest" char(64) NOT NULL,
  "context_ciphertext" text NOT NULL,
  "context_cipher_key_version" varchar(64) NOT NULL,
  "context_digest" char(64) NOT NULL,
  "context_digest_key_version" varchar(64) NOT NULL,
  "ui_locale" varchar(35) NOT NULL DEFAULT 'zh-CN',
  "ui_theme" varchar(16) NOT NULL DEFAULT 'light',
  "ui_form_presentation" varchar(16) NOT NULL DEFAULT 'seamless',
  "capability_snapshot_json" text NOT NULL,
  "application_version" bigint NOT NULL,
  "grant_security_version" bigint NOT NULL,
  "view_security_version" bigint NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'ACTIVE',
  "slot_released" smallint NOT NULL DEFAULT '0',
  "slot_released_at" timestamp(6),
  "issued_at" timestamp(6) NOT NULL,
  "last_seen_at" timestamp(6) NOT NULL,
  "idle_expires_at" timestamp(6) NOT NULL,
  "absolute_expires_at" timestamp(6) NOT NULL,
  "revoked_at" timestamp(6),
  "revoke_reason" varchar(128),
  "source_ip_digest" char(64),
  "source_ip_digest_key_version" varchar(64),
  "user_agent_digest" char(64),
  "user_agent_digest_key_version" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_session_embed_session" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_session_chk_embed_session_context" CHECK ((flow_sql_is_json("context_ciphertext") and flow_sql_is_json("capability_snapshot_json") and (length("context_ciphertext") <= 65536) and (length("capability_snapshot_json") <= 65536) and (char_length("context_cipher_key_version") > 0) and (char_length("context_digest_key_version") > 0))),
  CONSTRAINT "ck_embed_session_chk_embed_session_digest_keys" CHECK ((((("source_ip_digest" is null) and ("source_ip_digest_key_version" is null)) or (("source_ip_digest" is not null) and ("source_ip_digest_key_version" is not null))) and ((("user_agent_digest" is null) and ("user_agent_digest_key_version" is null)) or (("user_agent_digest" is not null) and ("user_agent_digest_key_version" is not null))))),
  CONSTRAINT "ck_embed_session_chk_embed_session_digests" CHECK ((("session_token_digest" ~ '^[0-9a-f]{64}$') and ("parent_nonce_digest" ~ '^[0-9a-f]{64}$') and ("child_nonce_digest" ~ '^[0-9a-f]{64}$') and ("context_digest" ~ '^[0-9a-f]{64}$') and (("source_ip_digest" is null) or ("source_ip_digest" ~ '^[0-9a-f]{64}$')) and (("user_agent_digest" is null) or ("user_agent_digest" ~ '^[0-9a-f]{64}$')))),
  CONSTRAINT "ck_embed_session_chk_embed_session_entry" CHECK (((("entry_mode" in ('LIST','CREATE')) and ("record_id" is null)) or (("entry_mode" in ('VIEW','EDIT')) and ("record_id" is not null)))),
  CONSTRAINT "ck_embed_session_chk_embed_session_form_presentation" CHECK (("ui_form_presentation" in ('seamless','dialog'))),
  CONSTRAINT "ck_embed_session_chk_embed_session_origin" CHECK (("parent_origin" ~ '^https://[^/?#]+$')),
  CONSTRAINT "ck_embed_session_chk_embed_session_slot" CHECK (((("status" = 'ACTIVE') and ("slot_released" = 0) and ("slot_released_at" is null)) or (("status" in ('LOGGED_OUT','EXPIRED')) and ("slot_released" = 1) and ("slot_released_at" is not null) and ("revoked_at" is null) and ("revoke_reason" is null)) or (("status" = 'REVOKED') and ("slot_released" = 1) and ("slot_released_at" is not null) and ("revoked_at" is not null) and ("revoke_reason" is not null)))),
  CONSTRAINT "ck_embed_session_chk_embed_session_status" CHECK (("status" in ('ACTIVE','LOGGED_OUT','EXPIRED','REVOKED'))),
  CONSTRAINT "ck_embed_session_chk_embed_session_theme" CHECK (("ui_theme" in ('light','dark','system'))),
  CONSTRAINT "ck_embed_session_chk_embed_session_time_window" CHECK ((("issued_at" <= "last_seen_at") and ("issued_at" < "idle_expires_at") and ("idle_expires_at" <= "absolute_expires_at"))),
  CONSTRAINT "ck_embed_session_chk_embed_session_versions" CHECK ((("provider_security_version" > 0) and ("application_version" >= 0) and ("grant_security_version" > 0) and ("view_security_version" > 0) and ("binding_version" > 0)))
);

CREATE TABLE "embed_session_counter" (
  "grant_id" varchar(64) NOT NULL,
  "flow_user_id" varchar(64) NOT NULL,
  "active_count" integer NOT NULL DEFAULT '0',
  "lock_version" bigint NOT NULL DEFAULT '0',
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_session_counter_embed_session_counter" PRIMARY KEY ("grant_id", "flow_user_id"),
  CONSTRAINT "ck_embed_session_counter_chk_embed_session_counter_count" CHECK (("active_count" >= 0)),
  CONSTRAINT "ck_embed_session_counter_chk_embed_session_counter_lock" CHECK (("lock_version" >= 0))
);

CREATE TABLE "embed_view" (
  "id" varchar(64) NOT NULL,
  "view_key" varchar(100) NOT NULL,
  "name" varchar(128) NOT NULL,
  "description" varchar(500),
  "surface_type" varchar(16) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'DRAFT',
  "draft_config_json" text NOT NULL,
  "draft_revision" bigint NOT NULL DEFAULT '1',
  "published_release_id" varchar(64),
  "lock_version" bigint NOT NULL DEFAULT '1',
  "security_version" bigint NOT NULL DEFAULT '1',
  "create_by" varchar(64) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_by" varchar(64) NOT NULL,
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_view_embed_view" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_view_chk_embed_view_draft_json" CHECK ((flow_sql_is_json("draft_config_json") and (length("draft_config_json") <= 262144))),
  CONSTRAINT "ck_embed_view_chk_embed_view_status" CHECK (("status" in ('DRAFT','ACTIVE','DISABLED','RETIRED'))),
  CONSTRAINT "ck_embed_view_chk_embed_view_surface" CHECK (("surface_type" in ('LIST','FORM'))),
  CONSTRAINT "ck_embed_view_chk_embed_view_versions" CHECK ((("draft_revision" > 0) and ("lock_version" > 0) and ("security_version" > 0)))
);

CREATE TABLE "embed_view_release" (
  "id" varchar(64) NOT NULL,
  "view_id" varchar(64) NOT NULL,
  "revision" bigint NOT NULL,
  "surface_type" varchar(16) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "list_key" varchar(100),
  "default_form_id" varchar(64),
  "list_release_id" varchar(64),
  "list_release_version" bigint,
  "form_release_id" varchar(64),
  "form_release_version" bigint,
  "entry_modes_json" text NOT NULL,
  "capabilities_json" text NOT NULL,
  "field_policy_json" text NOT NULL,
  "action_policy_json" text NOT NULL,
  "context_schema_json" text NOT NULL,
  "context_bindings_json" text NOT NULL,
  "ui_config_json" text NOT NULL,
  "config_json" text NOT NULL,
  "config_hash" char(64) NOT NULL,
  "release_note" varchar(500),
  "published_by" varchar(64) NOT NULL,
  "published_at" timestamp(6) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_embed_view_release_embed_view_release" PRIMARY KEY ("id"),
  CONSTRAINT "ck_embed_view_release_chk_embed_release_form_pair" CHECK (((("form_release_id" is null) and ("form_release_version" is null)) or (("form_release_id" is not null) and ("form_release_version" > 0)))),
  CONSTRAINT "ck_embed_view_release_chk_embed_release_hash" CHECK (("config_hash" ~ '^[0-9a-f]{64}$')),
  CONSTRAINT "ck_embed_view_release_chk_embed_release_json" CHECK ((flow_sql_is_json("entry_modes_json") and flow_sql_is_json("capabilities_json") and flow_sql_is_json("field_policy_json") and flow_sql_is_json("action_policy_json") and flow_sql_is_json("context_schema_json") and flow_sql_is_json("context_bindings_json") and flow_sql_is_json("ui_config_json") and flow_sql_is_json("config_json") and (length("entry_modes_json") <= 65536) and (length("capabilities_json") <= 65536) and (length("field_policy_json") <= 262144) and (length("action_policy_json") <= 262144) and (length("context_schema_json") <= 262144) and (length("context_bindings_json") <= 262144) and (length("ui_config_json") <= 262144) and (length("config_json") <= 262144))),
  CONSTRAINT "ck_embed_view_release_chk_embed_release_list_pair" CHECK (((("list_release_id" is null) and ("list_release_version" is null)) or (("list_release_id" is not null) and ("list_release_version" > 0)))),
  CONSTRAINT "ck_embed_view_release_chk_embed_release_revision" CHECK (("revision" > 0)),
  CONSTRAINT "ck_embed_view_release_chk_embed_release_surface" CHECK (("surface_type" in ('LIST','FORM'))),
  CONSTRAINT "ck_embed_view_release_chk_embed_release_target" CHECK (((("surface_type" = 'LIST') and ("list_key" is not null) and ("list_release_id" is not null) and ("list_release_version" is not null) and (("default_form_id" is null) or ("form_release_id" is not null))) or (("surface_type" = 'FORM') and ("list_key" is null) and ("list_release_id" is null) and ("list_release_version" is null) and ("form_release_id" is not null) and ("form_release_version" is not null))))
);

CREATE TABLE "entity_code_rule" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "prefix" varchar(20) DEFAULT '',
  "date_format" varchar(20) DEFAULT 'yyyyMMdd',
  "seq_length" integer DEFAULT '6',
  "seq_type" varchar(20) DEFAULT 'DAY',
  "current_seq" integer DEFAULT '0',
  "seq_date" varchar(20) DEFAULT '',
  "example" varchar(100) DEFAULT '',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "generation_mode" varchar(20) NOT NULL DEFAULT 'RULE',
  "generator_code" varchar(64),
  "generator_config" text,
  CONSTRAINT "pk_entity_code_rule_entity_code_rule" PRIMARY KEY ("id")
);

CREATE TABLE "entity_definition" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "entity_name" varchar(200) NOT NULL,
  "description" varchar(500),
  "process_definition_id" varchar(64),
  "status" varchar(20) DEFAULT 'DRAFT',
  "created_by" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "table_name" varchar(100),
  "lifecycle_mode" varchar(20) NOT NULL DEFAULT 'STANDALONE',
  "storage_mode" varchar(20) NOT NULL DEFAULT 'DYNAMIC',
  "deleted" smallint DEFAULT '0',
  "updated_by" varchar(64),
  "team_visibility_enabled" smallint NOT NULL DEFAULT '0',
  "team_visibility_level" varchar(30) NOT NULL DEFAULT 'ADDITIVE',
  "active_process_definition_key" bigint GENERATED ALWAYS AS ((case when ((coalesce("deleted",0) = 0) and (trim("process_definition_id") ~ '^[0-9]+$') and (nullif(trim(leading '0' from trim("process_definition_id")),'') is not null) and ((char_length(trim(leading '0' from trim("process_definition_id"))) < 19) or ((char_length(trim(leading '0' from trim("process_definition_id"))) = 19) and (trim(leading '0' from trim("process_definition_id")) <= '9223372036854775807')))) then cast(trim(leading '0' from trim("process_definition_id")) as bigint) else NULL end)) STORED,
  CONSTRAINT "pk_entity_definition_entity_definition" PRIMARY KEY ("id")
);

CREATE TABLE "entity_field" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "entity_id" bigint NOT NULL,
  "field_code" varchar(100) NOT NULL,
  "field_name" varchar(200) NOT NULL,
  "field_type" varchar(50) NOT NULL,
  "db_type" varchar(50),
  "field_length" integer,
  "is_required" smallint DEFAULT '0',
  "is_unique" smallint DEFAULT '0',
  "default_value" varchar(500),
  "options_json" text,
  "validate_rules" text,
  "sort_order" integer DEFAULT '0',
  "is_system" smallint DEFAULT '0',
  "is_published" smallint DEFAULT '0',
  "editable" smallint DEFAULT '1',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "field_precision" integer,
  "db_column_name" varchar(100),
  "file_types" varchar(500),
  "file_max_size" integer,
  "file_max_count" integer,
  "ref_entity_type" varchar(20),
  "ref_entity_id" varchar(64),
  "ref_field_code" varchar(100),
  "ref_list_key" varchar(100),
  "field_id" varchar(100),
  "dict_type" varchar(100),
  "value_storage" varchar(20) DEFAULT 'SCALAR',
  "deleted" smallint DEFAULT '0',
  CONSTRAINT "pk_entity_field_entity_field" PRIMARY KEY ("id")
);

CREATE TABLE "entity_field_file_item" (
  "id" varchar(64) NOT NULL,
  "field_id" varchar(64) NOT NULL,
  "item_key" varchar(64) NOT NULL,
  "item_name" varchar(200) NOT NULL,
  "name_aliases" text,
  "is_required" smallint NOT NULL DEFAULT '0',
  "file_types" varchar(500),
  "max_size" integer,
  "max_count" integer,
  "sort_order" integer DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_field_file_item_entity_field_file_item" PRIMARY KEY ("id")
);

CREATE TABLE "entity_field_option" (
  "id" varchar(64) NOT NULL,
  "field_id" varchar(64) NOT NULL,
  "option_value" varchar(500) NOT NULL,
  "option_label" varchar(500) NOT NULL,
  "style_type" varchar(50),
  "disabled" smallint NOT NULL DEFAULT '0',
  "sort_order" integer NOT NULL DEFAULT '0',
  "option_document" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_field_option_entity_field_option" PRIMARY KEY ("id")
);

CREATE TABLE "entity_form" (
  "id" varchar(64) NOT NULL,
  "entity_id" varchar(64) NOT NULL,
  "form_name" varchar(100) NOT NULL,
  "form_key" varchar(100) NOT NULL,
  "description" varchar(500) DEFAULT '',
  "layout_type" varchar(20) DEFAULT 'vertical',
  "status" smallint DEFAULT '1',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint DEFAULT '0',
  "is_default" smallint DEFAULT '0',
  "custom_component" varchar(100),
  "created_by" varchar(64),
  "updated_by" varchar(64),
  "view_config" text,
  "revision" integer NOT NULL DEFAULT '1',
  "active_release_id" varchar(64),
  "draft_hash" varchar(64),
  "custom_component_version" integer,
  "custom_component_snapshot_version" integer,
  "data_source_bindings_document" text,
  CONSTRAINT "pk_entity_form_entity_form" PRIMARY KEY ("id")
);

CREATE TABLE "entity_form_node" (
  "id" varchar(64) NOT NULL,
  "form_id" varchar(64) NOT NULL,
  "parent_id" varchar(64),
  "node_key" varchar(100) NOT NULL,
  "active_node_key" varchar(100) GENERATED ALWAYS AS ((case when ("deleted" = 0) then "node_key" else NULL end)) STORED,
  "node_type" varchar(30) NOT NULL,
  "binding_type" varchar(30) NOT NULL DEFAULT 'NONE',
  "binding_ref" varchar(200),
  "props_document" text,
  "rules_document" text,
  "data_source_bindings_document" text,
  "legacy_props_document" text,
  "order_key" bigint NOT NULL DEFAULT '1000000',
  "revision" integer NOT NULL DEFAULT '1',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  "component_name" varchar(100),
  "component_version" integer,
  "snapshot_version" integer,
  CONSTRAINT "pk_entity_form_node_entity_form_node" PRIMARY KEY ("id")
);

CREATE TABLE "entity_form_unique_claim" (
  "constraint_key" varchar(255) NOT NULL,
  "value_hash" char(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "form_id" varchar(64) NOT NULL,
  "rule_id" varchar(100) NOT NULL,
  "field_code" varchar(100) NOT NULL,
  "normalized_value" varchar(1000) NOT NULL,
  "record_id" varchar(64) NOT NULL,
  "release_id" varchar(64),
  "release_version" integer,
  "effective_release_id" varchar(64) NOT NULL,
  "effective_content_hash" char(64),
  "hotfix_target_id" varchar(64),
  "created_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_form_unique_claim_entity_form_unique_claim" PRIMARY KEY ("constraint_key", "value_hash")
);

CREATE TABLE "entity_form_unique_value_gate" (
  "scope_key" varchar(255) NOT NULL,
  "value_hash" char(64) NOT NULL,
  "created_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_form_unique_value_gate_entity_form_unique_value_gate" PRIMARY KEY ("scope_key", "value_hash")
);

CREATE TABLE "entity_list_action" (
  "id" varchar(64) NOT NULL,
  "list_config_id" varchar(64) NOT NULL,
  "position" varchar(20) NOT NULL,
  "button_key" varchar(100) NOT NULL,
  "button_type" varchar(30) NOT NULL DEFAULT 'built-in',
  "button_label" varchar(200) NOT NULL,
  "icon" varchar(100),
  "style_type" varchar(30),
  "link_mode" smallint NOT NULL DEFAULT '0',
  "custom_mode" varchar(30),
  "handler_code" varchar(200),
  "permission_code" varchar(200),
  "sort_order" integer NOT NULL DEFAULT '0',
  "enabled" smallint NOT NULL DEFAULT '1',
  "unavailable_behavior" varchar(20),
  "action_params_document" text,
  "availability_rule_document" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  "revision" integer NOT NULL DEFAULT '1',
  "order_key" bigint NOT NULL DEFAULT '1000000',
  "template_id" varchar(64),
  "template_version" integer,
  "local_overrides_document" text,
  CONSTRAINT "pk_entity_list_action_entity_list_action" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_config" (
  "id" varchar(64) NOT NULL,
  "entity_id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "list_key" varchar(100) NOT NULL,
  "list_name" varchar(200) NOT NULL,
  "description" varchar(500),
  "is_default" smallint DEFAULT '0',
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "custom_component" varchar(100),
  "toolbar_config" text,
  "row_action_config" text,
  "view_config" text,
  "data_scope_mode" varchar(20) NOT NULL DEFAULT 'INHERIT',
  "unbound_scope_policy" varchar(30) NOT NULL DEFAULT 'DENY_ALL',
  "scope_enforcement_mode" varchar(20) NOT NULL DEFAULT 'ENFORCE',
  "scope_default_confirmed" smallint NOT NULL DEFAULT '0',
  "scope_default_confirmed_by" varchar(64),
  "scope_default_confirmed_at" timestamp,
  "scope_default_confirmation_note" varchar(500),
  "access_permission_code" varchar(200),
  "allowed_scenes" text,
  "selection_config" text,
  "fixed_filter_config" text,
  "query_provider_code" varchar(100),
  "published_version" integer NOT NULL DEFAULT '0',
  "revision" integer NOT NULL DEFAULT '1',
  "active_release_id" varchar(64),
  "draft_hash" varchar(64),
  "query_interface_extension_id" varchar(64),
  CONSTRAINT "pk_entity_list_config_entity_list_config" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_field" (
  "id" varchar(64) NOT NULL,
  "list_config_id" varchar(64) NOT NULL,
  "field_id" varchar(64) NOT NULL,
  "field_code" varchar(100) NOT NULL,
  "field_name" varchar(200) NOT NULL,
  "sort_order" integer DEFAULT '0',
  "width" integer DEFAULT '0',
  "show_in_list" smallint DEFAULT '1',
  "is_query" smallint DEFAULT '1',
  "query_type" varchar(50) DEFAULT 'LIKE',
  "align" varchar(20) DEFAULT 'left',
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "data_source_type" varchar(32) DEFAULT 'ENTITY_FIELD',
  "data_source_config" text,
  "render_component" varchar(64),
  "formatter" varchar(255),
  "column_config" text,
  "query_config" text,
  "render_config" text,
  "revision" integer NOT NULL DEFAULT '1',
  "order_key" bigint NOT NULL DEFAULT '1000000',
  "interface_extension_id" varchar(64),
  "template_id" varchar(64),
  "template_version" integer,
  "local_overrides_document" text,
  CONSTRAINT "pk_entity_list_field_entity_list_field" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_scene" (
  "id" varchar(64) NOT NULL,
  "list_config_id" varchar(64) NOT NULL,
  "scene_code" varchar(30) NOT NULL,
  "sort_order" integer NOT NULL DEFAULT '0',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "revision" integer NOT NULL DEFAULT '1',
  CONSTRAINT "pk_entity_list_scene_entity_list_scene" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_scope_audit_log" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "list_key" varchar(100),
  "user_id" varchar(64),
  "operation" varchar(50) NOT NULL,
  "result" varchar(20) NOT NULL,
  "detail_json" text,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_list_scope_audit_log_entity_list_scope_audit_log" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_scope_binding" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "policy_id" varchar(64) NOT NULL,
  "list_key" varchar(100),
  "match_config" text NOT NULL,
  "rule_effect" varchar(20) NOT NULL DEFAULT 'ALLOW',
  "enabled" smallint NOT NULL DEFAULT '1',
  "effective_start_time" timestamp,
  "effective_end_time" timestamp,
  "created_by" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_entity_list_scope_binding_entity_list_scope_binding" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_scope_delegation" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100),
  "from_user_id" varchar(64) NOT NULL,
  "to_user_id" varchar(64) NOT NULL,
  "delegate_scope" varchar(50) NOT NULL DEFAULT 'PERSONAL',
  "policy_id" varchar(64),
  "delegate_config" text,
  "start_time" timestamp,
  "end_time" timestamp,
  "enabled" smallint NOT NULL DEFAULT '1',
  "created_by" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_entity_list_scope_delegation_entity_list_scope_delegation" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_scope_policy" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "policy_key" varchar(100) NOT NULL,
  "policy_name" varchar(200) NOT NULL,
  "description" varchar(500),
  "preset_code" varchar(50),
  "filter_config" text NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'DRAFT',
  "enabled" smallint NOT NULL DEFAULT '1',
  "version" integer NOT NULL DEFAULT '1',
  "review_required" smallint NOT NULL DEFAULT '0',
  "created_by" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_entity_list_scope_policy_entity_list_scope_policy" PRIMARY KEY ("id")
);

CREATE TABLE "entity_list_scope_release" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "version" integer NOT NULL,
  "snapshot_json" text NOT NULL,
  "content_hash" varchar(64) NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'ACTIVE',
  "description" varchar(500),
  "published_by" varchar(64),
  "published_at" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_list_scope_release_entity_list_scope_release" PRIMARY KEY ("id")
);

CREATE TABLE "entity_mutation_receipt" (
  "id" varchar(64) NOT NULL,
  "idempotency_key" varchar(200) NOT NULL,
  "command_hash" varchar(64) NOT NULL,
  "operation_id" varchar(200) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "record_id" varchar(64),
  "operation_type" varchar(30) NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "result_document" text,
  "version_no" integer,
  "version_scenario_code" varchar(100),
  "changed" smallint NOT NULL DEFAULT '0',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_mutation_receipt_entity_mutation_receipt" PRIMARY KEY ("id")
);

CREATE TABLE "entity_process_link" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(63) NOT NULL,
  "entity_record_id" varchar(64) NOT NULL,
  "generation" integer NOT NULL DEFAULT '1',
  "process_definition_key" varchar(255) NOT NULL,
  "process_instance_id" varchar(64),
  "state" varchar(20) NOT NULL,
  "request_id" varchar(64) NOT NULL,
  "entity_status" varchar(50),
  "ended_at" timestamp(6),
  "version" bigint NOT NULL DEFAULT '0',
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "end_type" varchar(20),
  CONSTRAINT "pk_entity_process_link_entity_process_link" PRIMARY KEY ("id")
);

CREATE TABLE "entity_publish_history" (
  "id" varchar(64) NOT NULL,
  "entity_id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "entity_name" varchar(200) NOT NULL,
  "version" integer NOT NULL,
  "version_description" varchar(500),
  "fields_snapshot" text,
  "relations_snapshot" text,
  "table_ddl" text,
  "publish_type" varchar(20) DEFAULT 'CREATE',
  "changes_description" varchar(500),
  "published_at" timestamp DEFAULT CURRENT_TIMESTAMP,
  "published_by" varchar(64),
  "published_by_name" varchar(100),
  "status" varchar(20) DEFAULT 'ACTIVE',
  "process_definition_id" varchar(64),
  "lifecycle_mode" varchar(20) NOT NULL DEFAULT 'STANDALONE',
  "team_visibility_enabled" smallint NOT NULL DEFAULT '0',
  "team_visibility_level" varchar(30) NOT NULL DEFAULT 'ADDITIVE',
  CONSTRAINT "pk_entity_publish_history_entity_publish_history" PRIMARY KEY ("id")
);

CREATE TABLE "entity_record_version" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "record_id" varchar(64) NOT NULL,
  "version_no" integer NOT NULL,
  "version_title" varchar(300),
  "scenario_code" varchar(100) NOT NULL,
  "scenario_name" varchar(200) NOT NULL,
  "operation_type" varchar(30) NOT NULL,
  "source_type" varchar(30) NOT NULL,
  "source_id" varchar(200),
  "business_intent_code" varchar(100) NOT NULL,
  "business_intent_name" varchar(200) NOT NULL,
  "source_entity_code" varchar(100),
  "source_record_id" varchar(64),
  "process_definition_id" varchar(100),
  "process_instance_id" varchar(64),
  "task_id" varchar(64),
  "operator_id" varchar(64),
  "operator_name" varchar(100),
  "business_trace_key" varchar(160),
  "idempotency_key" varchar(200) NOT NULL,
  "entity_release_id" varchar(64),
  "entity_release_version" integer,
  "schema_version" integer NOT NULL DEFAULT '1',
  "config_release_id" varchar(64),
  "config_release_version" integer,
  "data_hash" varchar(64),
  "presentation_hash" varchar(64),
  "scope_hash" varchar(64),
  "request_hash" varchar(64),
  "dataset_count" integer NOT NULL DEFAULT '0',
  "snapshot_row_count" integer NOT NULL DEFAULT '1',
  "snapshot_size_bytes" bigint NOT NULL DEFAULT '0',
  "completeness" varchar(20) NOT NULL DEFAULT 'COMPLETE',
  "snapshot_hash" varchar(64) NOT NULL,
  "snapshot_document" text NOT NULL,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_record_version_entity_record_version" PRIMARY KEY ("id")
);

CREATE TABLE "entity_record_version_counter" (
  "entity_code" varchar(100) NOT NULL,
  "record_id" varchar(64) NOT NULL,
  "last_version_no" integer NOT NULL DEFAULT '0',
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_record_version_counter_entity_record_version_counter" PRIMARY KEY ("entity_code", "record_id")
);

CREATE TABLE "entity_record_version_dataset" (
  "id" varchar(64) NOT NULL,
  "version_id" varchar(64) NOT NULL,
  "node_code" varchar(100) NOT NULL,
  "node_kind" varchar(20) NOT NULL DEFAULT 'RELATION',
  "relation_code" varchar(100) NOT NULL,
  "relation_name" varchar(200) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "entity_name" varchar(200) NOT NULL,
  "entity_release_id" varchar(64),
  "entity_release_version" integer,
  "selector_document" text NOT NULL,
  "presentation_document" text NOT NULL,
  "data_hash" varchar(64) NOT NULL,
  "presentation_hash" varchar(64) NOT NULL,
  "scope_hash" varchar(64) NOT NULL,
  "row_count" integer NOT NULL DEFAULT '0',
  "complete" smallint NOT NULL DEFAULT '1',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_record_version_dataset_entity_record_version_dataset" PRIMARY KEY ("id")
);

CREATE TABLE "entity_record_version_dataset_row" (
  "id" varchar(64) NOT NULL,
  "dataset_id" varchar(64) NOT NULL,
  "record_id" varchar(64) NOT NULL,
  "record_title" varchar(500),
  "row_order" integer NOT NULL DEFAULT '0',
  "row_hash" varchar(64) NOT NULL,
  "values_document" text NOT NULL,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_record_version_dataset_row_entity_record_ve_cee13e99" PRIMARY KEY ("id")
);

CREATE TABLE "entity_relation" (
  "id" varchar(64) NOT NULL,
  "parent_entity_id" varchar(64) NOT NULL,
  "parent_entity_code" varchar(100) NOT NULL,
  "parent_field_id" varchar(64),
  "parent_field_code" varchar(100),
  "relation_code" varchar(100) NOT NULL,
  "relation_name" varchar(200),
  "data_key" varchar(100),
  "child_entity_id" varchar(64) NOT NULL,
  "child_entity_code" varchar(100) NOT NULL,
  "child_ref_field_code" varchar(100) NOT NULL,
  "relation_type" varchar(20) NOT NULL DEFAULT 'ONE_TO_MANY',
  "ownership_type" varchar(20) NOT NULL DEFAULT 'COMPOSITION',
  "cascade_delete" smallint DEFAULT '1',
  "required" smallint DEFAULT '0',
  "sort_order" integer DEFAULT '0',
  "enabled" smallint DEFAULT '1',
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_relation_entity_relation" PRIMARY KEY ("id")
);

CREATE TABLE "entity_schema_operation" (
  "id" varchar(64) NOT NULL,
  "entity_id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "status" varchar(32) NOT NULL,
  "plan_hash" char(64) NOT NULL,
  "idempotency_key" varchar(160) NOT NULL,
  "plan_json" text NOT NULL,
  "target_fingerprint" char(64) NOT NULL,
  "actual_fingerprint" char(64),
  "drift_json" text,
  "unique_conflict_json" text,
  "risk_level" varchar(16) NOT NULL DEFAULT 'LOW',
  "risk_reason" varchar(1000),
  "estimated_rows" bigint NOT NULL DEFAULT '0',
  "lock_risk" varchar(16) NOT NULL DEFAULT 'LOW',
  "release_window" varchar(255),
  "attempt_count" integer NOT NULL DEFAULT '0',
  "error_message" text,
  "started_at" timestamp,
  "finished_at" timestamp,
  "created_by" varchar(64),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "operation_source" varchar(32) NOT NULL DEFAULT 'ENTITY_PUBLISH',
  "source_reference_id" varchar(64),
  CONSTRAINT "pk_entity_schema_operation_entity_schema_operation" PRIMARY KEY ("id")
);

CREATE TABLE "entity_schema_operation_event" (
  "id" varchar(64) NOT NULL,
  "operation_id" varchar(64) NOT NULL,
  "from_status" varchar(32),
  "to_status" varchar(32) NOT NULL,
  "message" varchar(1000),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_schema_operation_event_entity_schema_operation_event" PRIMARY KEY ("id")
);

CREATE TABLE "entity_status" (
  "id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "status_code" varchar(50) NOT NULL,
  "status_name" varchar(100) NOT NULL,
  "status_category" varchar(50),
  "sort_order" integer DEFAULT '0',
  "description" varchar(500),
  "color" varchar(20),
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_status_entity_status" PRIMARY KEY ("id")
);

CREATE TABLE "entity_unique_value" (
  "entity_code" varchar(100) NOT NULL,
  "field_code" varchar(100) NOT NULL,
  "value_hash" char(64) NOT NULL,
  "normalized_value" varchar(1000) NOT NULL,
  "record_id" varchar(64) NOT NULL,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_unique_value_entity_unique_value" PRIMARY KEY ("entity_code", "field_code", "value_hash")
);

CREATE TABLE "entity_version_config" (
  "id" varchar(64) NOT NULL,
  "entity_id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "enabled" smallint NOT NULL DEFAULT '0',
  "contract_version" integer NOT NULL DEFAULT '1',
  "draft_document" text,
  "config_document" text,
  "migration_state" varchar(30) NOT NULL DEFAULT 'REVIEW_REQUIRED',
  "active_release_id" varchar(64),
  "revision" integer NOT NULL DEFAULT '1',
  "status" varchar(20) NOT NULL DEFAULT 'DRAFT',
  "create_by" varchar(64),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64),
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_entity_version_config_entity_version_config" PRIMARY KEY ("id")
);

CREATE TABLE "entity_version_config_release" (
  "id" varchar(64) NOT NULL,
  "config_id" varchar(64) NOT NULL,
  "version" integer NOT NULL,
  "contract_version" integer NOT NULL DEFAULT '1',
  "config_document" text NOT NULL,
  "scope_hash" varchar(64),
  "published_by" varchar(64),
  "published_by_name" varchar(100),
  "publish_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_entity_version_config_release_entity_version_config_release" PRIMARY KEY ("id")
);

CREATE TABLE "flw_channel_definition" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "version_" integer,
  "key_" varchar(255),
  "category_" varchar(255),
  "deployment_id_" varchar(255),
  "create_time_" timestamp(3),
  "tenant_id_" varchar(255),
  "resource_name_" varchar(255),
  "description_" varchar(255),
  "type_" varchar(255),
  "implementation_" varchar(255),
  CONSTRAINT "pk_flw_channel_definition_flw_channel_definition" PRIMARY KEY ("id_")
);

CREATE TABLE "flw_event_definition" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "version_" integer,
  "key_" varchar(255),
  "category_" varchar(255),
  "deployment_id_" varchar(255),
  "tenant_id_" varchar(255),
  "resource_name_" varchar(255),
  "description_" varchar(255),
  CONSTRAINT "pk_flw_event_definition_flw_event_definition" PRIMARY KEY ("id_")
);

CREATE TABLE "flw_event_deployment" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "category_" varchar(255),
  "deploy_time_" timestamp(3),
  "tenant_id_" varchar(255),
  "parent_deployment_id_" varchar(255),
  CONSTRAINT "pk_flw_event_deployment_flw_event_deployment" PRIMARY KEY ("id_")
);

CREATE TABLE "flw_event_resource" (
  "id_" varchar(255) NOT NULL,
  "name_" varchar(255),
  "deployment_id_" varchar(255),
  "resource_bytes_" bytea,
  CONSTRAINT "pk_flw_event_resource_flw_event_resource" PRIMARY KEY ("id_")
);

CREATE TABLE "flw_ru_batch" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "type_" varchar(64) NOT NULL,
  "search_key_" varchar(255),
  "search_key2_" varchar(255),
  "create_time_" timestamp(3) NOT NULL,
  "complete_time_" timestamp(3),
  "status_" varchar(255),
  "batch_doc_id_" varchar(64),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_flw_ru_batch_flw_ru_batch" PRIMARY KEY ("id_")
);

CREATE TABLE "flw_ru_batch_part" (
  "id_" varchar(64) NOT NULL,
  "rev_" integer,
  "batch_id_" varchar(64),
  "type_" varchar(64) NOT NULL,
  "scope_id_" varchar(64),
  "sub_scope_id_" varchar(64),
  "scope_type_" varchar(64),
  "search_key_" varchar(255),
  "search_key2_" varchar(255),
  "create_time_" timestamp(3) NOT NULL,
  "complete_time_" timestamp(3),
  "status_" varchar(255),
  "result_doc_id_" varchar(64),
  "tenant_id_" varchar(255) DEFAULT '',
  CONSTRAINT "pk_flw_ru_batch_part_flw_ru_batch_part" PRIMARY KEY ("id_")
);

CREATE TABLE "integration_api_request_lease" (
  "lease_id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "scope_key" varchar(128) NOT NULL DEFAULT '',
  "expires_at" timestamp(6) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_integration_api_request_lease_integration_api_request_lease" PRIMARY KEY ("lease_id")
);

CREATE TABLE "integration_application" (
  "id" varchar(64) NOT NULL,
  "client_id" varchar(128) NOT NULL,
  "application_name" varchar(128) NOT NULL,
  "description" varchar(500),
  "owner_organization_id" varchar(64),
  "status" varchar(16) NOT NULL DEFAULT 'ACTIVE',
  "rate_limit_per_minute" integer NOT NULL DEFAULT '60',
  "max_concurrency" integer NOT NULL DEFAULT '10',
  "allowed_source_cidrs" text,
  "expires_at" timestamp(6),
  "version" bigint NOT NULL DEFAULT '0',
  "created_by" varchar(64) NOT NULL,
  "updated_by" varchar(64) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_integration_application_integration_application" PRIMARY KEY ("id"),
  CONSTRAINT "ck_integration_application_chk_integration_application_cidrs" CHECK ((("allowed_source_cidrs" is null) or flow_sql_is_json("allowed_source_cidrs"))),
  CONSTRAINT "ck_integration_application_chk_integration_applicatio_2f15421e" CHECK (("max_concurrency" between 1 and 1000)),
  CONSTRAINT "ck_integration_application_chk_integration_applicatio_ba38d02b" CHECK (("rate_limit_per_minute" between 1 and 10000)),
  CONSTRAINT "ck_integration_application_chk_integration_application_status" CHECK (("status" in ('ACTIVE','DISABLED','REVOKED')))
);

CREATE TABLE "integration_application_credential" (
  "id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "secret_hash" varchar(255) NOT NULL,
  "credential_hint" varchar(12) NOT NULL,
  "status" varchar(16) NOT NULL DEFAULT 'ACTIVE',
  "credential_version" bigint NOT NULL,
  "expires_at" timestamp(6),
  "last_used_at" timestamp(6),
  "created_by" varchar(64) NOT NULL,
  "revoked_by" varchar(64),
  "revoked_at" timestamp(6),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "active_application_id" varchar(64) GENERATED ALWAYS AS ((case when ("status" = 'ACTIVE') then "application_id" else NULL end)) STORED,
  CONSTRAINT "pk_integration_application_credential_integration_app_07181ac9" PRIMARY KEY ("id"),
  CONSTRAINT "ck_integration_application_credential_chk_integration_fe46ec5f" CHECK (((("status" = 'ACTIVE') and ("revoked_at" is null) and ("revoked_by" is null)) or (("status" = 'REVOKED') and ("revoked_at" is not null) and ("revoked_by" is not null)))),
  CONSTRAINT "ck_integration_application_credential_chk_integration_07e0aa0a" CHECK (("status" in ('ACTIVE','REVOKED')))
);

CREATE TABLE "integration_idempotency_record" (
  "id" varchar(64) NOT NULL,
  "application_id" varchar(64) NOT NULL,
  "operation" varchar(64) NOT NULL,
  "idempotency_key" varchar(128) NOT NULL,
  "request_hash" char(64) NOT NULL,
  "status" varchar(24) NOT NULL,
  "resource_type" varchar(64),
  "resource_id" varchar(128),
  "response_status" smallint,
  "response_body" text,
  "fencing_token" bigint NOT NULL DEFAULT '1',
  "processing_started_at" timestamp(6) NOT NULL,
  "expires_at" timestamp(6) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_integration_idempotency_record_integration_idempot_c04e1b13" PRIMARY KEY ("id"),
  CONSTRAINT "ck_integration_idempotency_record_chk_integration_ide_b71d40e2" CHECK (("fencing_token" > 0)),
  CONSTRAINT "ck_integration_idempotency_record_chk_integration_ide_49352946" CHECK (("request_hash" ~ '^[0-9a-f]{64}$')),
  CONSTRAINT "ck_integration_idempotency_record_chk_integration_ide_dac761d0" CHECK (((("status" = 'SUCCEEDED') and ("resource_type" is not null) and ("resource_id" is not null) and ("response_status" between 200 and 299) and ("response_body" is not null) and flow_sql_is_json("response_body")) or (("status" <> 'SUCCEEDED') and ("response_status" is null) and ("response_body" is null)))),
  CONSTRAINT "ck_integration_idempotency_record_chk_integration_ide_537b87b2" CHECK (("status" in ('PROCESSING','SUCCEEDED','FAILED_RETRYABLE')))
);

CREATE TABLE "integration_rate_limit_bucket" (
  "bucket_key" char(64) NOT NULL,
  "window_epoch" bigint NOT NULL,
  "request_count" integer NOT NULL DEFAULT '0',
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_integration_rate_limit_bucket_integration_rate_limit_bucket" PRIMARY KEY ("bucket_key", "window_epoch"),
  CONSTRAINT "ck_integration_rate_limit_bucket_chk_integration_rate_2e040ca5" CHECK (("request_count" >= 0))
);

CREATE TABLE "process_action" (
  "id" varchar(64) NOT NULL,
  "process_config_id" varchar(64) NOT NULL,
  "scope_type" varchar(20) DEFAULT 'SEQUENCE_FLOW',
  "element_id" varchar(100),
  "trigger_timing" varchar(50) DEFAULT 'TRANSITION_TAKEN',
  "execution_mode" varchar(30) DEFAULT 'IN_TRANSACTION',
  "failure_policy" varchar(20) DEFAULT 'ROLLBACK',
  "retry_config" text,
  "action_definition_id" varchar(64),
  "action_name" varchar(100) NOT NULL,
  "description" varchar(500),
  "interface_name" varchar(200) NOT NULL,
  "params_json" text,
  "sort_order" integer DEFAULT '0',
  "enabled" smallint DEFAULT '1',
  "status" varchar(20) DEFAULT 'DRAFT',
  "version_id" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "created_by" varchar(64),
  "deleted" integer DEFAULT '0',
  "failure_strategy_code" varchar(64),
  "failure_strategy_version" varchar(32),
  "failure_strategy_config" text,
  CONSTRAINT "pk_process_action_process_action" PRIMARY KEY ("id")
);

CREATE TABLE "process_action_definition" (
  "id" varchar(64) NOT NULL,
  "action_code" varchar(200) NOT NULL,
  "display_name" varchar(200) NOT NULL,
  "description" varchar(1000),
  "handler_name" varchar(200) NOT NULL,
  "visibility_scope" varchar(20) NOT NULL DEFAULT 'ENTITY',
  "enabled" smallint NOT NULL DEFAULT '1',
  "created_by" varchar(64),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_process_action_definition_process_action_definition" PRIMARY KEY ("id")
);

CREATE TABLE "process_action_definition_entity" (
  "id" varchar(64) NOT NULL,
  "action_definition_id" varchar(64) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_action_definition_entity_process_action_de_8dfe002d" PRIMARY KEY ("id")
);

CREATE TABLE "process_action_execution" (
  "id" varchar(64) NOT NULL,
  "action_id" varchar(64) NOT NULL,
  "action_name" varchar(200),
  "handler_name" varchar(200),
  "handler_display_name" varchar(200),
  "version_id" varchar(64),
  "process_instance_id" varchar(64) NOT NULL,
  "process_definition_id" varchar(128),
  "execution_id" varchar(64),
  "task_id" varchar(64),
  "entity_code" varchar(100),
  "scope_type" varchar(20) NOT NULL,
  "element_id" varchar(100),
  "trigger_timing" varchar(50) NOT NULL,
  "idempotency_key" varchar(128) NOT NULL,
  "payload_json" text,
  "resolved_params_json" text,
  "result_json" text,
  "execution_trace_json" text,
  "status" varchar(20) NOT NULL,
  "owner_id" varchar(128),
  "lease_token" bigint NOT NULL DEFAULT '0',
  "lease_until" timestamp(6),
  "retry_count" integer DEFAULT '0',
  "max_retries" integer DEFAULT '5',
  "next_retry_time" timestamp,
  "error_message" text,
  "error_stack" text,
  "started_at" timestamp,
  "finished_at" timestamp,
  "duration_ms" bigint,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "failure_strategy_snapshot" text,
  "attempt_no" integer NOT NULL DEFAULT '0',
  "attempt_lease_token" bigint,
  "termination_reason" varchar(64),
  "resolution_status" varchar(20),
  "replay_root_id" varchar(64),
  "replay_of_id" varchar(64),
  "handler_idempotency_key" varchar(128),
  CONSTRAINT "pk_process_action_execution_process_action_execution" PRIMARY KEY ("id")
);

CREATE TABLE "process_assignee_incident" (
  "id" varchar(64) NOT NULL,
  "process_config_id" varchar(64),
  "process_definition_id" varchar(128),
  "process_instance_id" varchar(128),
  "task_id" varchar(128),
  "node_id" varchar(200) NOT NULL,
  "node_name" varchar(300),
  "policy" varchar(32) NOT NULL,
  "status" varchar(32) NOT NULL,
  "empty_reason_code" varchar(100) NOT NULL,
  "empty_reason_message" varchar(1000),
  "resolver_code" varchar(200),
  "resolver_extra_params_json" text,
  "fallback_user" varchar(100),
  "fallback_group" varchar(100),
  "responsibility_owner" varchar(100) NOT NULL,
  "retry_count" integer NOT NULL DEFAULT '0',
  "max_retries" integer NOT NULL DEFAULT '0',
  "initial_delay_seconds" integer,
  "backoff_multiplier" numeric(8,3),
  "next_retry_at" timestamp,
  "resolution_action" varchar(64),
  "resolved_by" varchar(100),
  "resolved_at" timestamp,
  "detail_json" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "open_slot" varchar(128) GENERATED ALWAYS AS ((case when ("status" in ('OPEN','RETRY_SCHEDULED','MANUAL_REQUIRED')) then (coalesce("task_id","process_instance_id",'NO_INSTANCE') || ':' || "node_id") else NULL end)) STORED,
  CONSTRAINT "pk_process_assignee_incident_process_assignee_incident" PRIMARY KEY ("id")
);

CREATE TABLE "process_assignee_incident_action" (
  "id" varchar(64) NOT NULL,
  "incident_id" varchar(64) NOT NULL,
  "request_id" varchar(128) NOT NULL,
  "action_type" varchar(64) NOT NULL,
  "status" varchar(24) NOT NULL,
  "operator" varchar(100) NOT NULL,
  "request_json" text,
  "result_json" text,
  "error_message" varchar(1500),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "finished_at" timestamp,
  CONSTRAINT "pk_process_assignee_incident_action_process_assignee__f8956f67" PRIMARY KEY ("id")
);

CREATE TABLE "process_cc_record" (
  "id" varchar(64) NOT NULL,
  "process_instance_id" varchar(64) NOT NULL,
  "process_definition_id" varchar(64),
  "process_key" varchar(100),
  "process_name" varchar(200),
  "data_name" varchar(500),
  "business_key" varchar(200),
  "node_id" varchar(100),
  "node_name" varchar(200),
  "cc_user_id" varchar(64),
  "cc_user_name" varchar(100),
  "cc_type" varchar(20) DEFAULT 'AUTO',
  "cc_timing" varchar(20),
  "operator_id" varchar(64),
  "operator_name" varchar(100),
  "comment" varchar(1000),
  "source_task_id" varchar(64),
  "source_type" varchar(20),
  "recipient_rule_snapshot" text,
  "unique_key" varchar(255),
  "read_status" varchar(20) DEFAULT 'UNREAD',
  "read_time" timestamp,
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_cc_record_process_cc_record" PRIMARY KEY ("id")
);

CREATE TABLE "process_definition_config" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "process_key" varchar(100) NOT NULL,
  "process_name" varchar(200) NOT NULL,
  "description" varchar(500),
  "category" varchar(100),
  "version" integer DEFAULT '1',
  "status" varchar(20) DEFAULT 'DRAFT',
  "bpmn_xml" text,
  "draft_revision" bigint NOT NULL DEFAULT '1',
  "published_revision" bigint NOT NULL DEFAULT '0',
  "draft_hash" char(64),
  "published_draft_hash" char(64),
  "base_published_version" integer NOT NULL DEFAULT '0',
  "created_by" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" integer DEFAULT '0',
  "entity_id" varchar(64),
  "updated_by" varchar(64),
  CONSTRAINT "pk_process_definition_config_process_definition_config" PRIMARY KEY ("id")
);

CREATE TABLE "process_entity_status_mapping" (
  "id" varchar(64) NOT NULL,
  "process_config_id" varchar(64) NOT NULL,
  "process_key" varchar(100) NOT NULL,
  "entity_code" varchar(100) NOT NULL,
  "sequence_flow_id" varchar(100),
  "source_node_id" varchar(100) NOT NULL,
  "source_node_name" varchar(200),
  "target_node_id" varchar(100) NOT NULL,
  "target_node_name" varchar(200),
  "entity_status_code" varchar(50) NOT NULL,
  "condition_expression" varchar(500),
  "sort_order" integer DEFAULT '0',
  "description" varchar(500),
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "entity_status" varchar(100) NOT NULL,
  "status_category" varchar(50),
  CONSTRAINT "pk_process_entity_status_mapping_process_entity_status_mapping" PRIMARY KEY ("id")
);

CREATE TABLE "process_form_config" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "node_config_id" bigint NOT NULL,
  "form_name" varchar(200) NOT NULL,
  "form_key" varchar(100) NOT NULL,
  "description" varchar(500),
  "is_readonly" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "entity_form_id" varchar(64),
  "deleted" smallint DEFAULT '0',
  CONSTRAINT "pk_process_form_config_process_form_config" PRIMARY KEY ("id")
);

CREATE TABLE "process_form_field_config" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "form_config_id" bigint NOT NULL,
  "field_name" varchar(200) NOT NULL,
  "field_key" varchar(100) NOT NULL,
  "field_type" varchar(50) NOT NULL,
  "is_required" smallint DEFAULT '0',
  "default_value" varchar(500),
  "options_json" text,
  "validate_rules" text,
  "sort_order" integer DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint DEFAULT '0',
  CONSTRAINT "pk_process_form_field_config_process_form_field_config" PRIMARY KEY ("id")
);

CREATE TABLE "process_node_approval" (
  "id" varchar(64) NOT NULL,
  "process_config_id" varchar(64) NOT NULL,
  "node_id" varchar(100) NOT NULL,
  "node_name" varchar(100),
  "enabled" smallint DEFAULT '1',
  "comment_label" varchar(100) DEFAULT '审批意见',
  "options_json" text,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_node_approval_process_node_approval" PRIMARY KEY ("id")
);

CREATE TABLE "process_node_approval_option" (
  "id" varchar(64) NOT NULL,
  "approval_config_id" varchar(64) NOT NULL,
  "option_value" varchar(100) NOT NULL,
  "option_label" varchar(200) NOT NULL,
  "style_type" varchar(50),
  "show_comment" smallint NOT NULL DEFAULT '1',
  "remark_required" smallint NOT NULL DEFAULT '0',
  "sort_order" integer NOT NULL DEFAULT '0',
  "option_document" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_node_approval_option_process_node_approval_option" PRIMARY KEY ("id")
);

CREATE TABLE "process_node_assignee" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "node_config_id" bigint NOT NULL,
  "assignee_type" varchar(50) NOT NULL,
  "assignee_value" varchar(200) NOT NULL,
  "assignee_name" varchar(200),
  "priority" integer DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint DEFAULT '0',
  CONSTRAINT "pk_process_node_assignee_process_node_assignee" PRIMARY KEY ("id")
);

CREATE TABLE "process_node_config" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "node_id" varchar(100) NOT NULL,
  "node_name" varchar(200) NOT NULL,
  "node_type" varchar(50) NOT NULL,
  "process_config_id" bigint NOT NULL,
  "config_json" text,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "skip_node" smallint DEFAULT '0',
  "deleted" smallint DEFAULT '0',
  CONSTRAINT "pk_process_node_config_process_node_config" PRIMARY KEY ("id")
);

CREATE TABLE "process_node_form" (
  "id" varchar(64) NOT NULL,
  "process_config_id" varchar(64) NOT NULL,
  "node_id" varchar(100) NOT NULL,
  "node_name" varchar(100) DEFAULT '',
  "form_id" varchar(64) NOT NULL,
  "is_readonly" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "sort_order" integer DEFAULT '0',
  CONSTRAINT "pk_process_node_form_process_node_form" PRIMARY KEY ("id")
);

CREATE TABLE "process_operation_log" (
  "id" varchar(64) NOT NULL,
  "process_instance_id" varchar(64) NOT NULL,
  "task_id" varchar(64),
  "operation_type" varchar(50) NOT NULL,
  "operator_id" varchar(64),
  "operator_name" varchar(100),
  "operation_time" timestamp,
  "operation_comment" text,
  "old_value" text,
  "new_value" text,
  "ip_address" varchar(50),
  "user_agent" text,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "old_value_format" varchar(20) NOT NULL DEFAULT 'JSON',
  "new_value_format" varchar(20) NOT NULL DEFAULT 'JSON',
  CONSTRAINT "pk_process_operation_log_process_operation_log" PRIMARY KEY ("id")
);

CREATE TABLE "process_person_resolver_definition" (
  "id" varchar(64) NOT NULL,
  "resolver_code" varchar(100) NOT NULL,
  "display_name" varchar(200) NOT NULL,
  "description" varchar(1000),
  "bean_name" varchar(200) NOT NULL,
  "implementation_version" integer NOT NULL DEFAULT '1',
  "contract_version" integer NOT NULL DEFAULT '1',
  "supported_usages_document" text,
  "extra_param_schema_document" text,
  "dynamic_extra_params" smallint NOT NULL DEFAULT '0',
  "enabled" smallint NOT NULL DEFAULT '0',
  "revision" integer NOT NULL DEFAULT '1',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_process_person_resolver_definition_process_person__3f85b2fc" PRIMARY KEY ("id")
);

CREATE TABLE "process_status_sync_event" (
  "id" varchar(64) NOT NULL,
  "process_instance_id" varchar(64) NOT NULL,
  "event_type" varchar(50) NOT NULL,
  "event_sequence" varchar(128) NOT NULL,
  "entity_code" varchar(63) NOT NULL,
  "entity_record_id" varchar(64) NOT NULL,
  "target_status" varchar(100),
  "status_category" varchar(30),
  "state" varchar(20) NOT NULL,
  "applied_at" timestamp(6),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_process_status_sync_event_process_status_sync_event" PRIMARY KEY ("id")
);

CREATE TABLE "process_task" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "process_instance_id" varchar(64) NOT NULL,
  "process_definition_id" varchar(64) NOT NULL,
  "process_key" varchar(64) NOT NULL,
  "process_name" varchar(128),
  "node_id" varchar(64) NOT NULL,
  "node_name" varchar(128),
  "node_type" varchar(32),
  "task_id" varchar(64),
  "business_key" varchar(64),
  "entity_code" varchar(64),
  "entity_data_id" varchar(64),
  "assignee_id" varchar(64),
  "assignee_name" text,
  "assignee_type" varchar(32),
  "form_key" varchar(128),
  "form_data" text,
  "status" varchar(20) DEFAULT 'todo',
  "action" varchar(32),
  "action_label" varchar(200),
  "comment" text,
  "start_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "end_time" timestamp,
  "due_time" timestamp,
  "sla_status" varchar(20),
  "response_due_time" timestamp(6),
  "duration" bigint,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint DEFAULT '0',
  "timeout_hours" integer,
  "timeout_action" varchar(50),
  "timeout_handled" smallint DEFAULT '0',
  "priority" integer DEFAULT '0',
  "start_user_id" varchar(100),
  "business_name" text,
  "business_code" text,
  "business_data_name" text,
  "business_current_task_name" text,
  "business_status" text,
  "inbox_summary_ready" smallint NOT NULL DEFAULT '0',
  "inbox_identity_ready" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_process_task_process_task" PRIMARY KEY ("id")
);

CREATE TABLE "process_task_add_sign" (
  "id" varchar(64) NOT NULL,
  "process_instance_id" varchar(64) NOT NULL,
  "source_task_id" varchar(64) NOT NULL,
  "node_id" varchar(100),
  "operation_type" varchar(20) NOT NULL DEFAULT 'PARALLEL',
  "operator_id" varchar(64) NOT NULL,
  "comment" varchar(1000),
  "status" varchar(20) NOT NULL DEFAULT 'ACTIVE',
  "engine_execution_id" varchar(64),
  "source_completed" smallint NOT NULL DEFAULT '0',
  "source_action" varchar(100),
  "source_action_label" varchar(200),
  "source_comment" varchar(1000),
  "source_form_data" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "complete_time" timestamp,
  CONSTRAINT "pk_process_task_add_sign_process_task_add_sign" PRIMARY KEY ("id")
);

CREATE TABLE "process_task_add_sign_user" (
  "id" varchar(64) NOT NULL,
  "add_sign_id" varchar(64) NOT NULL,
  "user_id" varchar(64) NOT NULL,
  "user_name_snapshot" varchar(100),
  "generated_task_id" varchar(64) NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'TODO',
  "sort_order" integer NOT NULL DEFAULT '0',
  "complete_time" timestamp,
  CONSTRAINT "pk_process_task_add_sign_user_process_task_add_sign_user" PRIMARY KEY ("id")
);

CREATE TABLE "process_task_candidate_group" (
  "id" varchar(64) NOT NULL,
  "process_task_id" bigint NOT NULL,
  "group_code" varchar(100) NOT NULL,
  "sort_order" integer NOT NULL DEFAULT '0',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_task_candidate_group_process_task_candidate_group" PRIMARY KEY ("id")
);

CREATE TABLE "process_task_candidate_user" (
  "id" varchar(64) NOT NULL,
  "process_task_id" bigint NOT NULL,
  "user_id" varchar(100) NOT NULL,
  "sort_order" integer NOT NULL DEFAULT '0',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_task_candidate_user_process_task_candidate_user" PRIMARY KEY ("id")
);

CREATE TABLE "process_task_sla" (
  "id" varchar(64) NOT NULL,
  "task_id" varchar(64) NOT NULL,
  "process_instance_id" varchar(64) NOT NULL,
  "process_definition_id" varchar(100),
  "process_key" varchar(100),
  "node_id" varchar(100) NOT NULL,
  "node_name" varchar(200),
  "business_key" varchar(200),
  "entity_code" varchar(100),
  "entity_data_id" varchar(64),
  "policy_code" varchar(100) NOT NULL,
  "policy_version" integer NOT NULL,
  "policy_snapshot_json" text NOT NULL,
  "calendar_code" varchar(100),
  "calendar_version" integer,
  "calendar_snapshot_json" text,
  "timezone_id" varchar(100) NOT NULL,
  "current_assignee_id" varchar(100),
  "started_at" timestamp(6) NOT NULL,
  "responded_at" timestamp(6),
  "completed_at" timestamp(6),
  "response_due_at" timestamp(6),
  "completion_due_at" timestamp(6) NOT NULL,
  "response_remaining_minutes" integer,
  "completion_remaining_minutes" integer,
  "response_status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "completion_status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "overall_status" varchar(20) NOT NULL DEFAULT 'RUNNING',
  "pause_started_at" timestamp(6),
  "version" integer NOT NULL DEFAULT '1',
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_process_task_sla_process_task_sla" PRIMARY KEY ("id")
);

CREATE TABLE "process_task_sla_event" (
  "id" varchar(64) NOT NULL,
  "sla_id" varchar(64) NOT NULL,
  "task_id" varchar(64) NOT NULL,
  "step_id" varchar(64),
  "event_type" varchar(30) NOT NULL,
  "metric_type" varchar(20) NOT NULL,
  "trigger_at" timestamp(6) NOT NULL,
  "action_type" varchar(30) NOT NULL,
  "action_config_snapshot" text,
  "execution_no" integer NOT NULL DEFAULT '1',
  "max_executions" integer NOT NULL DEFAULT '1',
  "status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "attempts" integer NOT NULL DEFAULT '0',
  "max_retries" integer NOT NULL DEFAULT '5',
  "next_retry_time" timestamp(6),
  "owner_id" varchar(128),
  "lease_token" bigint NOT NULL DEFAULT '0',
  "lease_until" timestamp(6),
  "idempotency_key" varchar(255) NOT NULL,
  "result_json" text,
  "error_message" varchar(4000),
  "started_at" timestamp(6),
  "finished_at" timestamp(6),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_process_task_sla_event_process_task_sla_event" PRIMARY KEY ("id")
);

CREATE TABLE "process_task_sla_pause" (
  "id" varchar(64) NOT NULL,
  "sla_id" varchar(64) NOT NULL,
  "task_id" varchar(64) NOT NULL,
  "pause_type" varchar(30) NOT NULL,
  "reason" varchar(1000) NOT NULL,
  "operator_id" varchar(100),
  "started_at" timestamp(6) NOT NULL,
  "resumed_at" timestamp(6),
  "duration_seconds" bigint,
  "response_remaining_minutes" integer,
  "completion_remaining_minutes" integer,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_process_task_sla_pause_process_task_sla_pause" PRIMARY KEY ("id")
);

CREATE TABLE "process_ui_release_binding" (
  "id" varchar(64) NOT NULL,
  "process_version_history_id" varchar(64) NOT NULL,
  "process_config_id" varchar(64) NOT NULL,
  "process_key" varchar(100) NOT NULL,
  "process_version" integer NOT NULL,
  "deployment_id" varchar(100),
  "node_id" varchar(100) NOT NULL,
  "node_name" varchar(200),
  "config_type" varchar(20) NOT NULL DEFAULT 'FORM',
  "config_id" varchar(64) NOT NULL,
  "pinned_release_id" varchar(64) NOT NULL,
  "pinned_release_version" integer NOT NULL,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_ui_release_binding_process_ui_release_binding" PRIMARY KEY ("id")
);

CREATE TABLE "process_version_history" (
  "id" bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "process_config_id" bigint NOT NULL,
  "process_key" varchar(100) NOT NULL,
  "process_name" varchar(200) NOT NULL,
  "version" integer NOT NULL,
  "version_description" varchar(500),
  "bpmn_xml" text,
  "published_at" timestamp DEFAULT CURRENT_TIMESTAMP,
  "published_by" varchar(64),
  "deployment_id" varchar(64),
  "status" varchar(20) DEFAULT 'ACTIVE',
  "deleted" integer DEFAULT '0',
  "node_forms_snapshot" text,
  "created_by" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_process_version_history_process_version_history" PRIMARY KEY ("id")
);

CREATE TABLE "storage_file_object" (
  "id" varchar(32) NOT NULL,
  "storage_url" varchar(1024) NOT NULL,
  "storage_key" varchar(512) NOT NULL,
  "owner_user_id" varchar(64) NOT NULL,
  "idempotency_key" varchar(128),
  "request_hash" char(64),
  "original_name" varchar(512),
  "content_type" varchar(255),
  "content_length" bigint NOT NULL DEFAULT '0',
  "deleted" smallint NOT NULL DEFAULT '0',
  "create_time" timestamp(6) NOT NULL,
  "update_time" timestamp(6) NOT NULL,
  CONSTRAINT "pk_storage_file_object_storage_file_object" PRIMARY KEY ("id"),
  CONSTRAINT "ck_storage_file_object_chk_storage_file_idempotency" CHECK (((("idempotency_key" is null) and ("request_hash" is null)) or (("idempotency_key" is not null) and ("request_hash" ~ '^[0-9a-f]{64}$'))))
);

CREATE TABLE "sys_dict" (
  "id" varchar(64) NOT NULL,
  "dict_code" varchar(100) NOT NULL,
  "dict_name" varchar(100) NOT NULL,
  "description" varchar(500),
  "status" char(1) DEFAULT '0',
  "sort" integer DEFAULT '0',
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_sys_dict_sys_dict" PRIMARY KEY ("id")
);

CREATE TABLE "sys_dict_item" (
  "id" varchar(64) NOT NULL,
  "dict_id" varchar(64) NOT NULL,
  "dict_code" varchar(100) NOT NULL,
  "parent_id" varchar(64) DEFAULT '0',
  "item_code" varchar(100) NOT NULL,
  "item_label" varchar(100) NOT NULL,
  "item_value" varchar(200) NOT NULL,
  "sort" integer DEFAULT '0',
  "status" char(1) DEFAULT '0',
  "remark" varchar(500),
  "deleted" smallint DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_sys_dict_item_sys_dict_item" PRIMARY KEY ("id")
);

CREATE TABLE "sys_external_system" (
  "id" varchar(64) NOT NULL,
  "system_name" varchar(100) NOT NULL,
  "system_code" varchar(100) NOT NULL,
  "status" char(1) NOT NULL DEFAULT '0',
  "address" varchar(500) NOT NULL,
  "description" varchar(500),
  "version" bigint NOT NULL DEFAULT '0',
  "created_by" varchar(64),
  "updated_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_sys_external_system_sys_external_system" PRIMARY KEY ("id"),
  CONSTRAINT "ck_sys_external_system_chk_sys_external_system_deleted" CHECK (("deleted" in (0,1))),
  CONSTRAINT "ck_sys_external_system_chk_sys_external_system_status" CHECK (("status" in ('0','1'))),
  CONSTRAINT "ck_sys_external_system_chk_sys_external_system_version" CHECK (("version" >= 0))
);

CREATE TABLE "sys_external_system_parameter" (
  "id" varchar(64) NOT NULL,
  "external_system_id" varchar(64) NOT NULL,
  "parameter_name_zh" varchar(100) NOT NULL,
  "parameter_name_en" varchar(100) NOT NULL,
  "parameter_value" text NOT NULL,
  "sort_order" integer NOT NULL DEFAULT '0',
  "created_by" varchar(64),
  "updated_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "deleted" smallint NOT NULL DEFAULT '0',
  "active_parameter_name_en" varchar(100) GENERATED ALWAYS AS ((case when ("deleted" = 0) then "parameter_name_en" else NULL end)) STORED,
  CONSTRAINT "pk_sys_external_system_parameter_sys_external_system_parameter" PRIMARY KEY ("id"),
  CONSTRAINT "ck_sys_external_system_parameter_chk_sys_external_sys_b0b92baf" CHECK (("deleted" in (0,1))),
  CONSTRAINT "ck_sys_external_system_parameter_chk_sys_external_sys_d4b1fa20" CHECK (("sort_order" >= 0))
);

CREATE TABLE "sys_global_setting" (
  "id" varchar(64) NOT NULL,
  "scope_type" varchar(16) NOT NULL,
  "owner_id" varchar(64) NOT NULL,
  "setting_key" varchar(160) NOT NULL,
  "name" varchar(100) NOT NULL,
  "setting_value_type" varchar(16) NOT NULL,
  "setting_value" text NOT NULL,
  "remark" varchar(500),
  "version" bigint NOT NULL DEFAULT '0',
  "created_by" varchar(64),
  "updated_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_sys_global_setting_sys_global_setting" PRIMARY KEY ("id"),
  CONSTRAINT "ck_sys_global_setting_chk_sys_global_setting_key" CHECK ((char_length(trim("setting_key")) > 0)),
  CONSTRAINT "ck_sys_global_setting_chk_sys_global_setting_name" CHECK ((char_length(trim("name")) > 0)),
  CONSTRAINT "ck_sys_global_setting_chk_sys_global_setting_scope_owner" CHECK (((("scope_type" = 'SYSTEM') and ("owner_id" = '0')) or (("scope_type" = 'USER') and (char_length(trim("owner_id")) > 0) and ("owner_id" <> '0')))),
  CONSTRAINT "ck_sys_global_setting_chk_sys_global_setting_value_type" CHECK (("setting_value_type" in ('BOOLEAN','NUMBER','STRING','JSON'))),
  CONSTRAINT "ck_sys_global_setting_chk_sys_global_setting_version" CHECK (("version" >= 0))
);

CREATE TABLE "sys_group" (
  "id" varchar(64) NOT NULL,
  "group_name" varchar(50) NOT NULL,
  "group_code" varchar(50) NOT NULL,
  "description" varchar(200) DEFAULT '',
  "sort" integer DEFAULT '0',
  "status" char(1) DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint DEFAULT '0',
  "parent_id" varchar(64),
  "sort_order" integer DEFAULT '0',
  CONSTRAINT "pk_sys_group_sys_group" PRIMARY KEY ("id")
);

CREATE TABLE "sys_menu" (
  "id" varchar(64) NOT NULL,
  "parent_id" varchar(64) DEFAULT '0',
  "menu_name" varchar(100) NOT NULL,
  "menu_type" char(1) DEFAULT 'M',
  "icon" varchar(100),
  "sort" integer DEFAULT '0',
  "path" varchar(200),
  "component" varchar(255),
  "perm" varchar(200),
  "status" char(1) DEFAULT '1',
  "visible" char(1) DEFAULT '1',
  "keep_alive" char(1) DEFAULT '0',
  "breadcrumb" char(1) DEFAULT '1',
  "remark" varchar(500),
  "deleted" integer DEFAULT '0',
  "create_by" varchar(64),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_by" varchar(64),
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "is_frame" char(1) DEFAULT '0',
  "is_cache" char(1) DEFAULT '0',
  "query" varchar(255) DEFAULT '',
  "entity_code" varchar(100),
  "resource_type" varchar(30),
  "list_key" varchar(100),
  CONSTRAINT "pk_sys_menu_sys_menu" PRIMARY KEY ("id")
);

CREATE TABLE "sys_organization" (
  "id" varchar(64) NOT NULL,
  "org_code" varchar(100) NOT NULL,
  "org_name" varchar(100) NOT NULL,
  "type" varchar(20) NOT NULL,
  "business_level_code" varchar(100),
  "parent_id" varchar(64) DEFAULT '0',
  "level" integer DEFAULT '0',
  "path" varchar(500) DEFAULT '/',
  "sort_order" integer DEFAULT '0',
  "leader_id" varchar(64),
  "leader_name" varchar(100),
  "phone" varchar(50),
  "email" varchar(100),
  "address" varchar(200),
  "status" varchar(10) DEFAULT '0',
  "description" varchar(500),
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" integer DEFAULT '0',
  CONSTRAINT "pk_sys_organization_sys_organization" PRIMARY KEY ("id")
);

CREATE TABLE "sys_position" (
  "id" varchar(64) NOT NULL,
  "position_code" varchar(100) NOT NULL,
  "position_name" varchar(100) NOT NULL,
  "applicable_unit_type" varchar(16) NOT NULL,
  "holder_mode" varchar(16) NOT NULL,
  "built_in" smallint NOT NULL DEFAULT '0',
  "status" varchar(16) NOT NULL DEFAULT 'ENABLED',
  "sort_order" integer NOT NULL DEFAULT '0',
  "description" varchar(500),
  "revision" integer NOT NULL DEFAULT '1',
  "created_by" varchar(64),
  "updated_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_sys_position_sys_position" PRIMARY KEY ("id"),
  CONSTRAINT "ck_sys_position_chk_sys_position_flags" CHECK ((("built_in" in (0,1)) and ("deleted" in (0,1)))),
  CONSTRAINT "ck_sys_position_chk_sys_position_holder_mode" CHECK (("holder_mode" in ('SINGLE','MULTIPLE'))),
  CONSTRAINT "ck_sys_position_chk_sys_position_revision" CHECK (("revision" >= 1)),
  CONSTRAINT "ck_sys_position_chk_sys_position_status" CHECK (("status" in ('ENABLED','DISABLED'))),
  CONSTRAINT "ck_sys_position_chk_sys_position_unit_type" CHECK (("applicable_unit_type" in ('ORG','DEPT','ANY')))
);

CREATE TABLE "sys_position_assignment" (
  "id" varchar(64) NOT NULL,
  "position_id" varchar(64) NOT NULL,
  "organization_unit_id" varchar(64) NOT NULL,
  "user_id" varchar(64) NOT NULL,
  "is_primary" smallint NOT NULL DEFAULT '0',
  "sort_order" integer NOT NULL DEFAULT '0',
  "effective_from" timestamp(6) NOT NULL,
  "effective_to" timestamp(6),
  "revoked_at" timestamp(6),
  "revoked_by" varchar(64),
  "revoke_reason" varchar(500),
  "revision" integer NOT NULL DEFAULT '1',
  "created_by" varchar(64),
  "updated_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_sys_position_assignment_sys_position_assignment" PRIMARY KEY ("id"),
  CONSTRAINT "ck_sys_position_assignment_chk_sys_position_assignment_period" CHECK ((("effective_to" is null) or ("effective_to" > "effective_from"))),
  CONSTRAINT "ck_sys_position_assignment_chk_sys_position_assignment_primary" CHECK (("is_primary" in (0,1))),
  CONSTRAINT "ck_sys_position_assignment_chk_sys_position_assignmen_6100d38d" CHECK (("revision" >= 1)),
  CONSTRAINT "ck_sys_position_assignment_chk_sys_position_assignmen_cd53b659" CHECK ((("revoked_at" is null) or ("revoked_at" >= "effective_from")))
);

CREATE TABLE "sys_position_assignment_batch" (
  "id" varchar(64) NOT NULL,
  "idempotency_key" varchar(128) NOT NULL,
  "request_hash" char(64) NOT NULL,
  "assignment_ids_json" text NOT NULL,
  "created_by" varchar(64) NOT NULL,
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_sys_position_assignment_batch_sys_position_assignment_batch" PRIMARY KEY ("id"),
  CONSTRAINT "ck_sys_position_assignment_batch_chk_sys_position_batch_hash" CHECK (("request_hash" ~ '^[0-9a-f]{64}$')),
  CONSTRAINT "ck_sys_position_assignment_batch_chk_sys_position_bat_c04c04f7" CHECK (flow_sql_is_json("assignment_ids_json"))
);

CREATE TABLE "sys_role" (
  "id" varchar(64) NOT NULL,
  "role_name" varchar(50) NOT NULL,
  "role_code" varchar(50) NOT NULL,
  "description" varchar(200) DEFAULT '',
  "sort" integer DEFAULT '0',
  "status" char(1) DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint DEFAULT '0',
  "sort_order" integer DEFAULT '0',
  CONSTRAINT "pk_sys_role_sys_role" PRIMARY KEY ("id")
);

CREATE TABLE "sys_role_menu" (
  "id" varchar(64) NOT NULL,
  "role_id" varchar(64) NOT NULL,
  "menu_id" varchar(64) NOT NULL,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_sys_role_menu_sys_role_menu" PRIMARY KEY ("id")
);

CREATE TABLE "sys_user" (
  "id" varchar(64) NOT NULL,
  "username" varchar(50) NOT NULL,
  "nickname" varchar(50) DEFAULT '',
  "password" varchar(100) NOT NULL,
  "email" varchar(100) DEFAULT '',
  "phone" varchar(20) DEFAULT '',
  "avatar" varchar(255) DEFAULT '',
  "status" char(1) DEFAULT '0',
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint DEFAULT '0',
  "org_id" varchar(64),
  "dept_id" varchar(64),
  "password_reset_required" smallint NOT NULL DEFAULT '0',
  "token_version" bigint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_sys_user_sys_user" PRIMARY KEY ("id")
);

CREATE TABLE "sys_user_group" (
  "id" varchar(64) NOT NULL,
  "user_id" varchar(64) NOT NULL,
  "group_id" varchar(64) NOT NULL,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_sys_user_group_sys_user_group" PRIMARY KEY ("id")
);

CREATE TABLE "sys_user_role" (
  "id" varchar(64) NOT NULL,
  "user_id" varchar(64) NOT NULL,
  "role_id" varchar(64) NOT NULL,
  "create_time" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_sys_user_role_sys_user_role" PRIMARY KEY ("id")
);

CREATE TABLE "system_operation_log" (
  "id" varchar(64) NOT NULL,
  "event_id" varchar(64) NOT NULL,
  "operation_id" varchar(128),
  "trace_id" varchar(64),
  "parent_operation_id" varchar(128),
  "source_system" varchar(32),
  "source_type" varchar(64),
  "source_id" varchar(128),
  "source_event_id" varchar(128),
  "module_code" varchar(32) NOT NULL,
  "operation_code" varchar(64) NOT NULL,
  "operation_name" varchar(128) NOT NULL,
  "risk_level" varchar(16) NOT NULL,
  "result" varchar(16) NOT NULL,
  "operator_id" varchar(64),
  "operator_name" varchar(100),
  "operator_ip" varchar(64),
  "user_agent" varchar(512),
  "request_method" varchar(16),
  "request_path" varchar(512),
  "target_type" varchar(64),
  "target_id" varchar(128),
  "target_name" varchar(255),
  "summary" varchar(1000),
  "before_json" text,
  "after_json" text,
  "changed_fields_json" text,
  "payload_truncated" smallint NOT NULL DEFAULT '0',
  "error_code" varchar(100),
  "error_message" varchar(1000),
  "duration_ms" bigint,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_system_operation_log_system_operation_log" PRIMARY KEY ("id")
);

CREATE TABLE "task_sla_escalation_step" (
  "id" varchar(64) NOT NULL,
  "policy_id" varchar(64) NOT NULL,
  "step_name" varchar(200) NOT NULL,
  "metric_type" varchar(20) NOT NULL,
  "trigger_type" varchar(20) NOT NULL,
  "offset_minutes" integer NOT NULL DEFAULT '0',
  "repeat_interval_minutes" integer,
  "max_executions" integer NOT NULL DEFAULT '1',
  "action_type" varchar(30) NOT NULL,
  "template_code" varchar(100),
  "recipient_config_json" text,
  "target_config_json" text,
  "sort_order" integer NOT NULL DEFAULT '0',
  "enabled" smallint NOT NULL DEFAULT '1',
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_task_sla_escalation_step_task_sla_escalation_step" PRIMARY KEY ("id"),
  CONSTRAINT "ck_task_sla_escalation_step_chk_task_sla_step_executions" CHECK (("max_executions" > 0)),
  CONSTRAINT "ck_task_sla_escalation_step_chk_task_sla_step_repeat" CHECK ((("repeat_interval_minutes" is null) or ("repeat_interval_minutes" > 0)))
);

CREATE TABLE "task_sla_policy" (
  "id" varchar(64) NOT NULL,
  "policy_code" varchar(100) NOT NULL,
  "policy_name" varchar(200) NOT NULL,
  "description" varchar(1000),
  "version" integer NOT NULL DEFAULT '1',
  "response_target_minutes" integer,
  "completion_target_minutes" integer NOT NULL,
  "response_time_basis" varchar(20) NOT NULL DEFAULT 'WORKING_TIME',
  "completion_time_basis" varchar(20) NOT NULL DEFAULT 'WORKING_TIME',
  "allow_manual_pause" smallint NOT NULL DEFAULT '0',
  "pause_on_process_suspend" smallint NOT NULL DEFAULT '1',
  "max_pause_minutes" integer,
  "status" varchar(20) NOT NULL DEFAULT 'DRAFT',
  "created_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "updated_by" varchar(64),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_task_sla_policy_task_sla_policy" PRIMARY KEY ("id"),
  CONSTRAINT "ck_task_sla_policy_chk_task_sla_policy_completion" CHECK (("completion_target_minutes" > 0)),
  CONSTRAINT "ck_task_sla_policy_chk_task_sla_policy_response" CHECK ((("response_target_minutes" is null) or ("response_target_minutes" > 0)))
);

CREATE TABLE "ui_component_template" (
  "id" varchar(64) NOT NULL,
  "template_key" varchar(100) NOT NULL,
  "template_name" varchar(200) NOT NULL,
  "template_type" varchar(30) NOT NULL,
  "current_version" integer NOT NULL DEFAULT '1',
  "status" varchar(20) NOT NULL DEFAULT 'ACTIVE',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_ui_component_template_ui_component_template" PRIMARY KEY ("id")
);

CREATE TABLE "ui_component_template_version" (
  "id" varchar(64) NOT NULL,
  "template_id" varchar(64) NOT NULL,
  "version" integer NOT NULL,
  "snapshot_document" text NOT NULL,
  "content_hash" varchar(64) NOT NULL,
  "description" varchar(500),
  "created_by" varchar(64),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ui_component_template_version_ui_component_template_version" PRIMARY KEY ("id")
);

CREATE TABLE "ui_config_hotfix_request" (
  "id" varchar(64) NOT NULL,
  "config_type" varchar(20) NOT NULL,
  "config_id" varchar(64) NOT NULL,
  "draft_hash" char(64) NOT NULL,
  "active_release_id" varchar(64) NOT NULL,
  "target_hash" varchar(255) NOT NULL,
  "impact_token_hash" char(64) NOT NULL,
  "risk_level" varchar(20) NOT NULL,
  "reason" varchar(1000) NOT NULL,
  "ticket_ref" varchar(255) NOT NULL,
  "impact_document" text NOT NULL,
  "applicant_id" varchar(64) NOT NULL,
  "applicant_name" varchar(100),
  "window_start" timestamp NOT NULL,
  "window_end" timestamp NOT NULL,
  "review_required" smallint NOT NULL DEFAULT '0',
  "status" varchar(30) NOT NULL,
  "open_slot" smallint GENERATED ALWAYS AS ((case when ("status" in ('PENDING_REVIEW','APPROVED','PUBLISHING')) then 1 else NULL end)) STORED,
  "reviewer_id" varchar(64),
  "reviewer_name" varchar(100),
  "review_comment" varchar(1000),
  "reviewed_at" timestamp,
  "release_id" varchar(64),
  "published_at" timestamp,
  "observation_start" timestamp,
  "observation_end" timestamp,
  "observation_status" varchar(20),
  "rolled_back_by" varchar(64),
  "rolled_back_at" timestamp,
  "rollback_reason" varchar(1000),
  "cancelled_by" varchar(64),
  "cancelled_at" timestamp,
  "cancel_reason" varchar(1000),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ui_config_hotfix_request_ui_config_hotfix_request" PRIMARY KEY ("id")
);

CREATE TABLE "ui_config_hotfix_target" (
  "id" varchar(64) NOT NULL,
  "hotfix_release_id" varchar(64) NOT NULL,
  "config_type" varchar(20) NOT NULL,
  "config_id" varchar(64) NOT NULL,
  "process_version_history_id" varchar(64) NOT NULL,
  "pinned_release_id" varchar(64) NOT NULL,
  "pinned_release_version" integer NOT NULL,
  "previous_target_id" varchar(64),
  "effective_snapshot_document" text NOT NULL,
  "effective_content_hash" varchar(64) NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'ACTIVE',
  "active_slot" smallint GENERATED ALWAYS AS ((case when ("status" = 'ACTIVE') then 1 else NULL end)) STORED,
  "activated_by" varchar(64),
  "activated_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "rolled_back_by" varchar(64),
  "rolled_back_at" timestamp,
  CONSTRAINT "pk_ui_config_hotfix_target_ui_config_hotfix_target" PRIMARY KEY ("id")
);

CREATE TABLE "ui_config_release" (
  "id" varchar(64) NOT NULL,
  "config_type" varchar(20) NOT NULL,
  "config_id" varchar(64) NOT NULL,
  "version" integer NOT NULL,
  "snapshot_document" text NOT NULL,
  "content_hash" varchar(64) NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'INACTIVE',
  "active_slot" smallint GENERATED ALWAYS AS ((case when ("status" = 'ACTIVE') then 1 else NULL end)) STORED,
  "description" varchar(500),
  "published_by" varchar(64),
  "published_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "release_mode" varchar(20) NOT NULL DEFAULT 'STANDARD',
  "base_release_id" varchar(64),
  "risk_level" varchar(20) NOT NULL DEFAULT 'SAFE',
  "rollout_scope" varchar(30),
  "patch_document" text,
  "override_risk" smallint NOT NULL DEFAULT '0',
  "override_reason" varchar(1000),
  CONSTRAINT "pk_ui_config_release_ui_config_release" PRIMARY KEY ("id")
);

CREATE TABLE "ui_config_release_audit" (
  "id" varchar(64) NOT NULL,
  "config_type" varchar(20) NOT NULL,
  "config_id" varchar(64) NOT NULL,
  "release_id" varchar(64),
  "operation" varchar(40) NOT NULL,
  "risk_level" varchar(20),
  "actor_id" varchar(64),
  "actor_name" varchar(100),
  "reason" varchar(1000),
  "trace_id" varchar(100),
  "detail_document" text,
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ui_config_release_audit_ui_config_release_audit" PRIMARY KEY ("id")
);

CREATE TABLE "ui_event_binding" (
  "id" varchar(64) NOT NULL,
  "owner_type" varchar(20) NOT NULL,
  "owner_id" varchar(64) NOT NULL,
  "target_type" varchar(20) NOT NULL DEFAULT 'OWNER',
  "target_key" varchar(100) NOT NULL DEFAULT '',
  "event_code" varchar(50) NOT NULL,
  "inheritance_mode" varchar(20) NOT NULL DEFAULT 'INHERIT',
  "steps_document" text,
  "revision" integer NOT NULL DEFAULT '1',
  "enabled" smallint NOT NULL DEFAULT '1',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_ui_event_binding_ui_event_binding" PRIMARY KEY ("id")
);

CREATE TABLE "ui_extension_definition" (
  "id" varchar(64) NOT NULL,
  "extension_type" varchar(20) NOT NULL,
  "extension_key" varchar(255) NOT NULL,
  "display_name" varchar(200) NOT NULL,
  "version" integer NOT NULL,
  "snapshot_version" integer NOT NULL DEFAULT '1',
  "visibility_scope" varchar(20) NOT NULL DEFAULT 'GLOBAL',
  "entity_codes_document" text,
  "supported_modes_document" text,
  "supported_node_types_document" text,
  "supported_bindings_document" text,
  "config_schema_document" text,
  "capabilities_document" text,
  "implementation_type" varchar(30),
  "provider_code" varchar(100),
  "scope_type" varchar(20),
  "scope_id" varchar(64),
  "implementation_config_document" text,
  "execution_policy_document" text,
  "input_schema_document" text,
  "output_schema_document" text,
  "interface_kind" varchar(20),
  "interface_context_type" varchar(20),
  "provider_operation_code" varchar(100),
  "legacy_service_id" varchar(64),
  "status" varchar(20) NOT NULL DEFAULT 'ACTIVE',
  "revision" integer NOT NULL DEFAULT '1',
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_ui_extension_definition_ui_extension_definition" PRIMARY KEY ("id")
);

CREATE TABLE "ui_hotfix_observation_metric" (
  "id" varchar(64) NOT NULL,
  "request_id" varchar(64) NOT NULL,
  "release_id" varchar(64) NOT NULL,
  "metric_code" varchar(40) NOT NULL,
  "total_count" bigint NOT NULL DEFAULT '0',
  "failure_count" bigint NOT NULL DEFAULT '0',
  "last_error" varchar(1000),
  "last_observed_at" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "pk_ui_hotfix_observation_metric_ui_hotfix_observation_metric" PRIMARY KEY ("id")
);

CREATE TABLE "ui_view_composition" (
  "id" varchar(64) NOT NULL,
  "owner_type" varchar(20) NOT NULL,
  "owner_id" varchar(64) NOT NULL,
  "composition_key" varchar(100) NOT NULL,
  "anchor_type" varchar(32) NOT NULL DEFAULT 'OWNER',
  "anchor_key" varchar(160),
  "config_document" text NOT NULL,
  "order_key" bigint NOT NULL DEFAULT '1000',
  "revision" integer NOT NULL DEFAULT '1',
  "create_time" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "update_time" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "deleted" smallint NOT NULL DEFAULT '0',
  "active_composition_key" varchar(100) GENERATED ALWAYS AS ((case when ("deleted" = 0) then "composition_key" else NULL end)) STORED,
  CONSTRAINT "pk_ui_view_composition_ui_view_composition" PRIMARY KEY ("id"),
  CONSTRAINT "ck_ui_view_composition_chk_ui_view_composition_deleted" CHECK (("deleted" in (0,1))),
  CONSTRAINT "ck_ui_view_composition_chk_ui_view_composition_owner" CHECK (("owner_type" in ('FORM','LIST'))),
  CONSTRAINT "ck_ui_view_composition_chk_ui_view_composition_revision" CHECK (("revision" >= 1))
);

CREATE TABLE "work_calendar" (
  "id" varchar(64) NOT NULL,
  "calendar_code" varchar(100) NOT NULL,
  "calendar_name" varchar(200) NOT NULL,
  "timezone_id" varchar(100) NOT NULL,
  "description" varchar(1000),
  "version" integer NOT NULL DEFAULT '1',
  "default_flag" smallint NOT NULL DEFAULT '0',
  "status" varchar(20) NOT NULL DEFAULT 'DRAFT',
  "effective_from" date,
  "effective_to" date,
  "created_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "updated_by" varchar(64),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_work_calendar_work_calendar" PRIMARY KEY ("id"),
  CONSTRAINT "ck_work_calendar_chk_work_calendar_version" CHECK (("version" > 0))
);

CREATE TABLE "work_calendar_binding" (
  "id" varchar(64) NOT NULL,
  "scope_type" varchar(30) NOT NULL,
  "scope_key" varchar(100) NOT NULL,
  "calendar_id" varchar(64) NOT NULL,
  "priority" integer NOT NULL DEFAULT '0',
  "effective_from" date,
  "effective_to" date,
  "status" varchar(20) NOT NULL DEFAULT 'ENABLED',
  "created_by" varchar(64),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "updated_by" varchar(64),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "deleted" smallint NOT NULL DEFAULT '0',
  CONSTRAINT "pk_work_calendar_binding_work_calendar_binding" PRIMARY KEY ("id")
);

CREATE TABLE "work_calendar_exception" (
  "id" varchar(64) NOT NULL,
  "calendar_id" varchar(64) NOT NULL,
  "exception_date" date NOT NULL,
  "exception_type" varchar(20) NOT NULL,
  "exception_name" varchar(200),
  "description" varchar(1000),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_work_calendar_exception_work_calendar_exception" PRIMARY KEY ("id")
);

CREATE TABLE "work_calendar_exception_period" (
  "id" varchar(64) NOT NULL,
  "exception_id" varchar(64) NOT NULL,
  "start_minute" smallint NOT NULL,
  "end_minute" smallint NOT NULL,
  "sort_order" integer NOT NULL DEFAULT '0',
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_work_calendar_exception_period_work_calendar_excep_64f8b53e" PRIMARY KEY ("id"),
  CONSTRAINT "ck_work_calendar_exception_period_chk_work_calendar_e_cce8bccb" CHECK ((("start_minute" >= 0) and ("end_minute" <= 1440) and ("start_minute" < "end_minute")))
);

CREATE TABLE "work_calendar_period" (
  "id" varchar(64) NOT NULL,
  "calendar_id" varchar(64) NOT NULL,
  "day_of_week" smallint NOT NULL,
  "start_minute" smallint NOT NULL,
  "end_minute" smallint NOT NULL,
  "sort_order" integer NOT NULL DEFAULT '0',
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_work_calendar_period_work_calendar_period" PRIMARY KEY ("id"),
  CONSTRAINT "ck_work_calendar_period_chk_work_calendar_period_day" CHECK (("day_of_week" between 1 and 7)),
  CONSTRAINT "ck_work_calendar_period_chk_work_calendar_period_minutes" CHECK ((("start_minute" >= 0) and ("end_minute" <= 1440) and ("start_minute" < "end_minute")))
);

CREATE TABLE "workflow_bootstrap_job" (
  "job_name" varchar(100) NOT NULL,
  "completed_version" integer NOT NULL DEFAULT '0',
  "owner_id" varchar(128),
  "completed_at" timestamp(6),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "pk_workflow_bootstrap_job_workflow_bootstrap_job" PRIMARY KEY ("job_name")
);

CREATE TABLE "workflow_outbox_event" (
  "id" varchar(64) NOT NULL,
  "topic" varchar(100) NOT NULL,
  "event_key" varchar(200) NOT NULL,
  "aggregate_type" varchar(100),
  "aggregate_id" varchar(128),
  "payload_document" text NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "owner_id" varchar(128),
  "lease_token" bigint NOT NULL DEFAULT '0',
  "lease_until" timestamp(6),
  "retry_count" integer NOT NULL DEFAULT '0',
  "max_retries" integer NOT NULL DEFAULT '8',
  "next_retry_time" timestamp,
  "error_message" varchar(1000),
  "create_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "processed_time" timestamp,
  CONSTRAINT "pk_workflow_outbox_event_workflow_outbox_event" PRIMARY KEY ("id")
);

CREATE TABLE "workflow_schema_change" (
  "id" varchar(36) NOT NULL,
  "ddl_hash" char(64) NOT NULL,
  "active_hash" char(64),
  "ddl_statement" text NOT NULL,
  "status" varchar(20) NOT NULL DEFAULT 'PENDING',
  "attempt" integer NOT NULL DEFAULT '0',
  "owner_id" varchar(128),
  "lease_token" bigint NOT NULL DEFAULT '0',
  "lease_until" timestamp(6),
  "next_attempt_at" timestamp(6) NOT NULL,
  "last_error" varchar(1000),
  "create_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "update_time" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "completed_time" timestamp(6),
  CONSTRAINT "pk_workflow_schema_change_workflow_schema_change" PRIMARY KEY ("id")
);

-- 普通索引和唯一索引。
CREATE UNIQUE INDEX "uq_act_app_appdef_act_idx_app_def_uniq" ON "act_app_appdef" ("key_", "version_", "tenant_id_");
CREATE INDEX "ix_act_app_appdef_act_idx_app_def_dply" ON "act_app_appdef" ("deployment_id_");
CREATE INDEX "ix_act_app_deployment_resource_act_idx_app_rsrc_dpl" ON "act_app_deployment_resource" ("deployment_id_");
CREATE UNIQUE INDEX "uq_act_cmmn_casedef_act_idx_case_def_uniq" ON "act_cmmn_casedef" ("key_", "version_", "tenant_id_");
CREATE INDEX "ix_act_cmmn_casedef_act_idx_case_def_dply" ON "act_cmmn_casedef" ("deployment_id_");
CREATE INDEX "ix_act_cmmn_deployment_resource_act_idx_cmmn_rsrc_dpl" ON "act_cmmn_deployment_resource" ("deployment_id_");
CREATE INDEX "ix_act_cmmn_hi_case_inst_act_idx_hi_case_inst_end" ON "act_cmmn_hi_case_inst" ("end_time_");
CREATE INDEX "ix_act_cmmn_hi_plan_item_inst_act_idx_hi_plan_item_inst_case" ON "act_cmmn_hi_plan_item_inst" ("case_inst_id_");
CREATE INDEX "ix_act_cmmn_ru_case_inst_act_idx_case_inst_case_def" ON "act_cmmn_ru_case_inst" ("case_def_id_");
CREATE INDEX "ix_act_cmmn_ru_case_inst_act_idx_case_inst_parent" ON "act_cmmn_ru_case_inst" ("parent_id_");
CREATE INDEX "ix_act_cmmn_ru_case_inst_act_idx_case_inst_ref_id_" ON "act_cmmn_ru_case_inst" ("reference_id_");
CREATE INDEX "ix_act_cmmn_ru_mil_inst_act_idx_mil_case_def" ON "act_cmmn_ru_mil_inst" ("case_def_id_");
CREATE INDEX "ix_act_cmmn_ru_mil_inst_act_idx_mil_case_inst" ON "act_cmmn_ru_mil_inst" ("case_inst_id_");
CREATE INDEX "ix_act_cmmn_ru_plan_item_inst_act_idx_plan_item_case_def" ON "act_cmmn_ru_plan_item_inst" ("case_def_id_");
CREATE INDEX "ix_act_cmmn_ru_plan_item_inst_act_idx_plan_item_case_inst" ON "act_cmmn_ru_plan_item_inst" ("case_inst_id_");
CREATE INDEX "ix_act_cmmn_ru_plan_item_inst_act_idx_plan_item_stage_inst" ON "act_cmmn_ru_plan_item_inst" ("stage_inst_id_");
CREATE INDEX "ix_act_cmmn_ru_sentry_part_inst_act_idx_sentry_case_def" ON "act_cmmn_ru_sentry_part_inst" ("case_def_id_");
CREATE INDEX "ix_act_cmmn_ru_sentry_part_inst_act_idx_sentry_case_inst" ON "act_cmmn_ru_sentry_part_inst" ("case_inst_id_");
CREATE INDEX "ix_act_cmmn_ru_sentry_part_inst_act_idx_sentry_plan_item" ON "act_cmmn_ru_sentry_part_inst" ("plan_item_inst_id_");
CREATE UNIQUE INDEX "uq_act_dmn_decision_act_idx_dmn_dec_uniq" ON "act_dmn_decision" ("key_", "version_", "tenant_id_");
CREATE INDEX "ix_act_dmn_deployment_resource_act_idx_dmn_rsrc_dpl" ON "act_dmn_deployment_resource" ("deployment_id_");
CREATE INDEX "ix_act_dmn_hi_decision_execution_act_idx_dmn_instance_id" ON "act_dmn_hi_decision_execution" ("instance_id_");
CREATE INDEX "ix_act_ge_bytearray_act_idx_bytear_depl" ON "act_ge_bytearray" ("deployment_id_");
CREATE INDEX "ix_act_hi_actinst_act_idx_hi_act_inst_start" ON "act_hi_actinst" ("start_time_");
CREATE INDEX "ix_act_hi_actinst_act_idx_hi_act_inst_end" ON "act_hi_actinst" ("end_time_");
CREATE INDEX "ix_act_hi_actinst_act_idx_hi_act_inst_procinst" ON "act_hi_actinst" ("proc_inst_id_", "act_id_");
CREATE INDEX "ix_act_hi_actinst_act_idx_hi_act_inst_exec" ON "act_hi_actinst" ("execution_id_", "act_id_");
CREATE INDEX "ix_act_hi_detail_act_idx_hi_detail_proc_inst" ON "act_hi_detail" ("proc_inst_id_");
CREATE INDEX "ix_act_hi_detail_act_idx_hi_detail_act_inst" ON "act_hi_detail" ("act_inst_id_");
CREATE INDEX "ix_act_hi_detail_act_idx_hi_detail_time" ON "act_hi_detail" ("time_");
CREATE INDEX "ix_act_hi_detail_act_idx_hi_detail_name" ON "act_hi_detail" ("name_");
CREATE INDEX "ix_act_hi_detail_act_idx_hi_detail_task_id" ON "act_hi_detail" ("task_id_");
CREATE INDEX "ix_act_hi_entitylink_act_idx_hi_ent_lnk_scope" ON "act_hi_entitylink" ("scope_id_", "scope_type_", "link_type_");
CREATE INDEX "ix_act_hi_entitylink_act_idx_hi_ent_lnk_ref_scope" ON "act_hi_entitylink" ("ref_scope_id_", "ref_scope_type_", "link_type_");
CREATE INDEX "ix_act_hi_entitylink_act_idx_hi_ent_lnk_root_scope" ON "act_hi_entitylink" ("root_scope_id_", "root_scope_type_", "link_type_");
CREATE INDEX "ix_act_hi_entitylink_act_idx_hi_ent_lnk_scope_def" ON "act_hi_entitylink" ("scope_definition_id_", "scope_type_", "link_type_");
CREATE INDEX "ix_act_hi_identitylink_act_idx_hi_ident_lnk_user" ON "act_hi_identitylink" ("user_id_");
CREATE INDEX "ix_act_hi_identitylink_act_idx_hi_ident_lnk_scope" ON "act_hi_identitylink" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_hi_identitylink_act_idx_hi_ident_lnk_sub_scope" ON "act_hi_identitylink" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_hi_identitylink_act_idx_hi_ident_lnk_scope_def" ON "act_hi_identitylink" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_hi_identitylink_act_idx_hi_ident_lnk_task" ON "act_hi_identitylink" ("task_id_");
CREATE INDEX "ix_act_hi_identitylink_act_idx_hi_ident_lnk_procinst" ON "act_hi_identitylink" ("proc_inst_id_");
CREATE UNIQUE INDEX "uq_act_hi_procinst_proc_inst_id_" ON "act_hi_procinst" ("proc_inst_id_");
CREATE INDEX "ix_act_hi_procinst_act_idx_hi_pro_inst_end" ON "act_hi_procinst" ("end_time_");
CREATE INDEX "ix_act_hi_procinst_act_idx_hi_pro_i_buskey" ON "act_hi_procinst" ("business_key_");
CREATE INDEX "ix_act_hi_procinst_act_idx_hi_pro_super_procinst" ON "act_hi_procinst" ("super_process_instance_id_");
CREATE INDEX "ix_act_hi_taskinst_act_idx_hi_task_scope" ON "act_hi_taskinst" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_hi_taskinst_act_idx_hi_task_sub_scope" ON "act_hi_taskinst" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_hi_taskinst_act_idx_hi_task_scope_def" ON "act_hi_taskinst" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_hi_taskinst_act_idx_hi_task_inst_procinst" ON "act_hi_taskinst" ("proc_inst_id_");
CREATE INDEX "ix_act_hi_tsk_log_act_idx_act_hi_tsk_log_task" ON "act_hi_tsk_log" ("task_id_");
CREATE INDEX "ix_act_hi_varinst_act_idx_hi_procvar_name_type" ON "act_hi_varinst" ("name_", "var_type_");
CREATE INDEX "ix_act_hi_varinst_act_idx_hi_var_scope_id_type" ON "act_hi_varinst" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_hi_varinst_act_idx_hi_var_sub_id_type" ON "act_hi_varinst" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_hi_varinst_act_idx_hi_procvar_proc_inst" ON "act_hi_varinst" ("proc_inst_id_");
CREATE INDEX "ix_act_hi_varinst_act_idx_hi_procvar_task_id" ON "act_hi_varinst" ("task_id_");
CREATE INDEX "ix_act_hi_varinst_act_idx_hi_procvar_exe" ON "act_hi_varinst" ("execution_id_");
CREATE INDEX "ix_act_id_membership_act_fk_memb_group" ON "act_id_membership" ("group_id_");
CREATE UNIQUE INDEX "uq_act_id_priv_act_uniq_priv_name" ON "act_id_priv" ("name_");
CREATE INDEX "ix_act_id_priv_mapping_act_fk_priv_mapping" ON "act_id_priv_mapping" ("priv_id_");
CREATE INDEX "ix_act_id_priv_mapping_act_idx_priv_user" ON "act_id_priv_mapping" ("user_id_");
CREATE INDEX "ix_act_id_priv_mapping_act_idx_priv_group" ON "act_id_priv_mapping" ("group_id_");
CREATE UNIQUE INDEX "uq_act_procdef_info_act_uniq_info_procdef" ON "act_procdef_info" ("proc_def_id_");
CREATE INDEX "ix_act_procdef_info_act_idx_info_procdef" ON "act_procdef_info" ("proc_def_id_");
CREATE INDEX "ix_act_procdef_info_act_fk_info_json_ba" ON "act_procdef_info" ("info_json_id_");
CREATE INDEX "ix_act_re_model_act_fk_model_source" ON "act_re_model" ("editor_source_value_id_");
CREATE INDEX "ix_act_re_model_act_fk_model_source_extra" ON "act_re_model" ("editor_source_extra_value_id_");
CREATE INDEX "ix_act_re_model_act_fk_model_deployment" ON "act_re_model" ("deployment_id_");
CREATE UNIQUE INDEX "uq_act_re_procdef_act_uniq_procdef" ON "act_re_procdef" ("key_", "version_", "derived_version_", "tenant_id_");
CREATE INDEX "ix_act_ru_actinst_act_idx_ru_acti_start" ON "act_ru_actinst" ("start_time_");
CREATE INDEX "ix_act_ru_actinst_act_idx_ru_acti_end" ON "act_ru_actinst" ("end_time_");
CREATE INDEX "ix_act_ru_actinst_act_idx_ru_acti_proc" ON "act_ru_actinst" ("proc_inst_id_");
CREATE INDEX "ix_act_ru_actinst_act_idx_ru_acti_proc_act" ON "act_ru_actinst" ("proc_inst_id_", "act_id_");
CREATE INDEX "ix_act_ru_actinst_act_idx_ru_acti_exec" ON "act_ru_actinst" ("execution_id_");
CREATE INDEX "ix_act_ru_actinst_act_idx_ru_acti_exec_act" ON "act_ru_actinst" ("execution_id_", "act_id_");
CREATE INDEX "ix_act_ru_actinst_act_idx_ru_acti_task" ON "act_ru_actinst" ("task_id_");
CREATE INDEX "ix_act_ru_deadletter_job_act_idx_deadletter_job_excep_ff41e1d0" ON "act_ru_deadletter_job" ("exception_stack_id_");
CREATE INDEX "ix_act_ru_deadletter_job_act_idx_deadletter_job_custo_5ab71fa4" ON "act_ru_deadletter_job" ("custom_values_id_");
CREATE INDEX "ix_act_ru_deadletter_job_act_idx_deadletter_job_correlation_id" ON "act_ru_deadletter_job" ("correlation_id_");
CREATE INDEX "ix_act_ru_deadletter_job_act_idx_djob_scope" ON "act_ru_deadletter_job" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_deadletter_job_act_idx_djob_sub_scope" ON "act_ru_deadletter_job" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_deadletter_job_act_idx_djob_scope_def" ON "act_ru_deadletter_job" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_ru_deadletter_job_act_fk_deadletter_job_execution" ON "act_ru_deadletter_job" ("execution_id_");
CREATE INDEX "ix_act_ru_deadletter_job_act_fk_deadletter_job_proces_e107386f" ON "act_ru_deadletter_job" ("process_instance_id_");
CREATE INDEX "ix_act_ru_deadletter_job_act_fk_deadletter_job_proc_def" ON "act_ru_deadletter_job" ("proc_def_id_");
CREATE INDEX "ix_act_ru_entitylink_act_idx_ent_lnk_scope" ON "act_ru_entitylink" ("scope_id_", "scope_type_", "link_type_");
CREATE INDEX "ix_act_ru_entitylink_act_idx_ent_lnk_ref_scope" ON "act_ru_entitylink" ("ref_scope_id_", "ref_scope_type_", "link_type_");
CREATE INDEX "ix_act_ru_entitylink_act_idx_ent_lnk_root_scope" ON "act_ru_entitylink" ("root_scope_id_", "root_scope_type_", "link_type_");
CREATE INDEX "ix_act_ru_entitylink_act_idx_ent_lnk_scope_def" ON "act_ru_entitylink" ("scope_definition_id_", "scope_type_", "link_type_");
CREATE INDEX "ix_act_ru_event_subscr_act_idx_event_subscr_config_" ON "act_ru_event_subscr" ("configuration_");
CREATE INDEX "ix_act_ru_event_subscr_act_idx_event_subscr_scoperef_" ON "act_ru_event_subscr" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_event_subscr_act_idx_event_subscr_exec_id" ON "act_ru_event_subscr" ("execution_id_");
CREATE INDEX "ix_act_ru_event_subscr_act_idx_event_subscr_proc_id" ON "act_ru_event_subscr" ("proc_inst_id_");
CREATE INDEX "ix_act_ru_execution_act_idx_exec_buskey" ON "act_ru_execution" ("business_key_");
CREATE INDEX "ix_act_ru_execution_act_idc_exec_root" ON "act_ru_execution" ("root_proc_inst_id_");
CREATE INDEX "ix_act_ru_execution_act_idx_exec_ref_id_" ON "act_ru_execution" ("reference_id_");
CREATE INDEX "ix_act_ru_execution_act_fk_exe_procinst" ON "act_ru_execution" ("proc_inst_id_");
CREATE INDEX "ix_act_ru_execution_act_fk_exe_parent" ON "act_ru_execution" ("parent_id_");
CREATE INDEX "ix_act_ru_execution_act_fk_exe_super" ON "act_ru_execution" ("super_exec_");
CREATE INDEX "ix_act_ru_execution_act_fk_exe_procdef" ON "act_ru_execution" ("proc_def_id_");
CREATE INDEX "ix_act_ru_external_job_act_idx_external_job_exception_stack_id" ON "act_ru_external_job" ("exception_stack_id_");
CREATE INDEX "ix_act_ru_external_job_act_idx_external_job_custom_values_id" ON "act_ru_external_job" ("custom_values_id_");
CREATE INDEX "ix_act_ru_external_job_act_idx_external_job_correlation_id" ON "act_ru_external_job" ("correlation_id_");
CREATE INDEX "ix_act_ru_external_job_act_idx_ejob_scope" ON "act_ru_external_job" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_external_job_act_idx_ejob_sub_scope" ON "act_ru_external_job" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_external_job_act_idx_ejob_scope_def" ON "act_ru_external_job" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_ru_identitylink_act_idx_ident_lnk_user" ON "act_ru_identitylink" ("user_id_");
CREATE INDEX "ix_act_ru_identitylink_act_idx_ident_lnk_group" ON "act_ru_identitylink" ("group_id_");
CREATE INDEX "ix_act_ru_identitylink_act_idx_ident_lnk_scope" ON "act_ru_identitylink" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_identitylink_act_idx_ident_lnk_sub_scope" ON "act_ru_identitylink" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_identitylink_act_idx_ident_lnk_scope_def" ON "act_ru_identitylink" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_ru_identitylink_act_idx_athrz_procedef" ON "act_ru_identitylink" ("proc_def_id_");
CREATE INDEX "ix_act_ru_identitylink_act_fk_tskass_task" ON "act_ru_identitylink" ("task_id_");
CREATE INDEX "ix_act_ru_identitylink_act_fk_idl_procinst" ON "act_ru_identitylink" ("proc_inst_id_");
CREATE INDEX "ix_act_ru_job_act_idx_job_exception_stack_id" ON "act_ru_job" ("exception_stack_id_");
CREATE INDEX "ix_act_ru_job_act_idx_job_custom_values_id" ON "act_ru_job" ("custom_values_id_");
CREATE INDEX "ix_act_ru_job_act_idx_job_correlation_id" ON "act_ru_job" ("correlation_id_");
CREATE INDEX "ix_act_ru_job_act_idx_job_scope" ON "act_ru_job" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_job_act_idx_job_sub_scope" ON "act_ru_job" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_job_act_idx_job_scope_def" ON "act_ru_job" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_ru_job_act_fk_job_execution" ON "act_ru_job" ("execution_id_");
CREATE INDEX "ix_act_ru_job_act_fk_job_process_instance" ON "act_ru_job" ("process_instance_id_");
CREATE INDEX "ix_act_ru_job_act_fk_job_proc_def" ON "act_ru_job" ("proc_def_id_");
CREATE INDEX "ix_act_ru_suspended_job_act_idx_suspended_job_excepti_f6597139" ON "act_ru_suspended_job" ("exception_stack_id_");
CREATE INDEX "ix_act_ru_suspended_job_act_idx_suspended_job_custom_values_id" ON "act_ru_suspended_job" ("custom_values_id_");
CREATE INDEX "ix_act_ru_suspended_job_act_idx_suspended_job_correlation_id" ON "act_ru_suspended_job" ("correlation_id_");
CREATE INDEX "ix_act_ru_suspended_job_act_idx_sjob_scope" ON "act_ru_suspended_job" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_suspended_job_act_idx_sjob_sub_scope" ON "act_ru_suspended_job" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_suspended_job_act_idx_sjob_scope_def" ON "act_ru_suspended_job" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_ru_suspended_job_act_fk_suspended_job_execution" ON "act_ru_suspended_job" ("execution_id_");
CREATE INDEX "ix_act_ru_suspended_job_act_fk_suspended_job_process_instance" ON "act_ru_suspended_job" ("process_instance_id_");
CREATE INDEX "ix_act_ru_suspended_job_act_fk_suspended_job_proc_def" ON "act_ru_suspended_job" ("proc_def_id_");
CREATE INDEX "ix_act_ru_task_act_idx_task_create" ON "act_ru_task" ("create_time_");
CREATE INDEX "ix_act_ru_task_act_idx_task_scope" ON "act_ru_task" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_task_act_idx_task_sub_scope" ON "act_ru_task" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_task_act_idx_task_scope_def" ON "act_ru_task" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_ru_task_act_fk_task_exe" ON "act_ru_task" ("execution_id_");
CREATE INDEX "ix_act_ru_task_act_fk_task_procinst" ON "act_ru_task" ("proc_inst_id_");
CREATE INDEX "ix_act_ru_task_act_fk_task_procdef" ON "act_ru_task" ("proc_def_id_");
CREATE INDEX "ix_act_ru_timer_job_act_idx_timer_job_exception_stack_id" ON "act_ru_timer_job" ("exception_stack_id_");
CREATE INDEX "ix_act_ru_timer_job_act_idx_timer_job_custom_values_id" ON "act_ru_timer_job" ("custom_values_id_");
CREATE INDEX "ix_act_ru_timer_job_act_idx_timer_job_correlation_id" ON "act_ru_timer_job" ("correlation_id_");
CREATE INDEX "ix_act_ru_timer_job_act_idx_timer_job_duedate" ON "act_ru_timer_job" ("duedate_");
CREATE INDEX "ix_act_ru_timer_job_act_idx_tjob_scope" ON "act_ru_timer_job" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_timer_job_act_idx_tjob_sub_scope" ON "act_ru_timer_job" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_timer_job_act_idx_tjob_scope_def" ON "act_ru_timer_job" ("scope_definition_id_", "scope_type_");
CREATE INDEX "ix_act_ru_timer_job_act_fk_timer_job_execution" ON "act_ru_timer_job" ("execution_id_");
CREATE INDEX "ix_act_ru_timer_job_act_fk_timer_job_process_instance" ON "act_ru_timer_job" ("process_instance_id_");
CREATE INDEX "ix_act_ru_timer_job_act_fk_timer_job_proc_def" ON "act_ru_timer_job" ("proc_def_id_");
CREATE INDEX "ix_act_ru_variable_act_idx_ru_var_scope_id_type" ON "act_ru_variable" ("scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_variable_act_idx_ru_var_sub_id_type" ON "act_ru_variable" ("sub_scope_id_", "scope_type_");
CREATE INDEX "ix_act_ru_variable_act_fk_var_bytearray" ON "act_ru_variable" ("bytearray_id_");
CREATE INDEX "ix_act_ru_variable_act_idx_variable_task_id" ON "act_ru_variable" ("task_id_");
CREATE INDEX "ix_act_ru_variable_act_fk_var_exe" ON "act_ru_variable" ("execution_id_");
CREATE INDEX "ix_act_ru_variable_act_fk_var_procinst" ON "act_ru_variable" ("proc_inst_id_");
CREATE INDEX "ix_auth_login_throttle_idx_auth_login_throttle_updated" ON "auth_login_throttle" ("update_time");
CREATE UNIQUE INDEX "uq_auth_refresh_session_uk_auth_refresh_session_token_hash" ON "auth_refresh_session" ("refresh_token_hash");
CREATE INDEX "ix_auth_refresh_session_idx_auth_refresh_session_user" ON "auth_refresh_session" ("user_id", "revoked_at");
CREATE INDEX "ix_auth_refresh_session_idx_auth_refresh_session_expiry" ON "auth_refresh_session" ("idle_expires_at", "absolute_expires_at");
CREATE UNIQUE INDEX "uq_config_asset_baseline_uk_asset_baseline_scope" ON "config_asset_baseline" ("asset_type", "business_key", "scope_key");
CREATE INDEX "ix_config_asset_baseline_idx_asset_baseline_package" ON "config_asset_baseline" ("import_package_id");
CREATE UNIQUE INDEX "uq_config_environment_mapping_uk_environment_mapping" ON "config_environment_mapping" ("source_type", "source_key");
CREATE UNIQUE INDEX "uq_config_export_package_uk_export_package_no" ON "config_export_package" ("package_no");
CREATE INDEX "ix_config_export_package_idx_export_package_tag" ON "config_export_package" ("migration_tag");
CREATE INDEX "ix_config_export_package_idx_export_package_created" ON "config_export_package" ("create_time");
CREATE UNIQUE INDEX "uq_config_export_package_item_uk_export_package_asset" ON "config_export_package_item" ("package_id", "asset_id");
CREATE INDEX "ix_config_export_package_item_idx_export_item_package" ON "config_export_package_item" ("package_id");
CREATE UNIQUE INDEX "uq_config_import_item_uk_import_item_asset" ON "config_import_item" ("import_package_id", "asset_type", "business_key", "source_version");
CREATE INDEX "ix_config_import_item_idx_import_item_package" ON "config_import_item" ("import_package_id");
CREATE INDEX "ix_config_import_item_idx_import_item_compare" ON "config_import_item" ("comparison_status", "publish_status");
CREATE UNIQUE INDEX "uq_config_import_package_uk_import_checksum" ON "config_import_package" ("checksum");
CREATE INDEX "ix_config_import_package_idx_import_package_tag" ON "config_import_package" ("migration_tag");
CREATE INDEX "ix_config_import_package_idx_import_package_status" ON "config_import_package" ("status", "imported_at");
CREATE UNIQUE INDEX "uq_config_migration_asset_uk_migration_asset_history" ON "config_migration_asset" ("asset_type", "source_history_id");
CREATE INDEX "ix_config_migration_asset_idx_migration_asset_key" ON "config_migration_asset" ("asset_type", "business_key", "source_version");
CREATE INDEX "ix_config_migration_asset_idx_migration_asset_tag" ON "config_migration_asset" ("migration_tag");
CREATE INDEX "ix_config_migration_asset_idx_migration_asset_export" ON "config_migration_asset" ("mark_for_export", "export_status", "snapshot_completeness");
CREATE UNIQUE INDEX "uq_config_migration_asset_dependency_uk_config_asset__bb4d5cae" ON "config_migration_asset_dependency" ("asset_id", "dependency_type", "dependency_key");
CREATE INDEX "ix_config_migration_asset_dependency_idx_config_depen_5e3fd228" ON "config_migration_asset_dependency" ("dependency_type", "dependency_key");
CREATE INDEX "ix_config_migration_asset_dependency_idx_config_refer_04932a1e" ON "config_migration_asset_dependency" ("source_asset_type", "source_business_key", "source_version");
CREATE INDEX "ix_config_migration_asset_dependency_idx_config_refer_aaeac515" ON "config_migration_asset_dependency" ("parse_status", "extracted_at");
CREATE UNIQUE INDEX "uq_embed_application_grant_uk_embed_grant_application_view" ON "embed_application_grant" ("application_id", "view_id");
CREATE INDEX "ix_embed_application_grant_idx_embed_grant_status_expiry" ON "embed_application_grant" ("status", "expires_at");
CREATE INDEX "ix_embed_application_grant_idx_embed_grant_provider_status" ON "embed_application_grant" ("identity_provider_id", "status");
CREATE INDEX "ix_embed_application_grant_fk_embed_grant_view" ON "embed_application_grant" ("view_id");
CREATE INDEX "ix_embed_assertion_replay_idx_embed_assertion_replay_expiry" ON "embed_assertion_replay" ("expires_at");
CREATE UNIQUE INDEX "uq_embed_external_identity_binding_uk_embed_binding_subject" ON "embed_external_identity_binding" ("application_id", "identity_provider_id", "subject_digest");
CREATE INDEX "ix_embed_external_identity_binding_idx_embed_binding_flow_user" ON "embed_external_identity_binding" ("flow_user_id", "status");
CREATE INDEX "ix_embed_external_identity_binding_idx_embed_binding_expiry" ON "embed_external_identity_binding" ("status", "expires_at");
CREATE INDEX "ix_embed_external_identity_binding_fk_embed_binding_provider" ON "embed_external_identity_binding" ("identity_provider_id");
CREATE UNIQUE INDEX "uq_embed_identity_provider_uk_embed_identity_provider_e68ab5f2" ON "embed_identity_provider" ("type", "issuer_uniqueness_key", "subject_namespace");
CREATE INDEX "ix_embed_identity_provider_idx_embed_identity_provider_status" ON "embed_identity_provider" ("status", "update_time");
CREATE UNIQUE INDEX "uq_embed_launch_uk_embed_launch_code_digest" ON "embed_launch" ("launch_code_digest");
CREATE UNIQUE INDEX "uq_embed_launch_uk_embed_launch_consumed_session" ON "embed_launch" ("consumed_session_id");
CREATE INDEX "ix_embed_launch_idx_embed_launch_expiry" ON "embed_launch" ("status", "expires_at");
CREATE INDEX "ix_embed_launch_idx_embed_launch_cleanup" ON "embed_launch" ("status", "update_time", "id");
CREATE INDEX "ix_embed_launch_idx_embed_launch_application_view" ON "embed_launch" ("application_id", "view_id", "create_time");
CREATE INDEX "ix_embed_launch_idx_embed_launch_binding_status" ON "embed_launch" ("identity_binding_id", "status");
CREATE INDEX "ix_embed_launch_fk_embed_launch_grant" ON "embed_launch" ("grant_id");
CREATE INDEX "ix_embed_launch_fk_embed_launch_view" ON "embed_launch" ("view_id");
CREATE INDEX "ix_embed_launch_fk_embed_launch_release" ON "embed_launch" ("view_release_id");
CREATE INDEX "ix_embed_launch_fk_embed_launch_provider" ON "embed_launch" ("identity_provider_id");
CREATE INDEX "ix_embed_launch_fk_embed_launch_flow_user" ON "embed_launch" ("flow_user_id");
CREATE UNIQUE INDEX "uq_embed_operation_receipt_uk_embed_receipt_idempotency" ON "embed_operation_receipt" ("idempotency_record_id");
CREATE INDEX "ix_embed_operation_receipt_idx_embed_receipt_cleanup" ON "embed_operation_receipt" ("create_time", "id");
CREATE INDEX "ix_embed_operation_receipt_idx_embed_receipt_application" ON "embed_operation_receipt" ("application_id", "operation", "create_time");
CREATE INDEX "ix_embed_operation_receipt_idx_embed_receipt_target" ON "embed_operation_receipt" ("target_type", "target_id", "create_time");
CREATE UNIQUE INDEX "uq_embed_session_uk_embed_session_token_digest" ON "embed_session" ("session_token_digest");
CREATE UNIQUE INDEX "uq_embed_session_uk_embed_session_launch" ON "embed_session" ("launch_id");
CREATE INDEX "ix_embed_session_idx_embed_session_expiry" ON "embed_session" ("status", "idle_expires_at", "absolute_expires_at");
CREATE INDEX "ix_embed_session_idx_embed_session_counter_reconcile" ON "embed_session" ("status", "slot_released", "grant_id", "flow_user_id");
CREATE INDEX "ix_embed_session_idx_embed_session_terminal_cleanup" ON "embed_session" ("status", "slot_released_at", "id");
CREATE INDEX "ix_embed_session_idx_embed_session_application" ON "embed_session" ("application_id", "status");
CREATE INDEX "ix_embed_session_idx_embed_session_view" ON "embed_session" ("view_id", "status");
CREATE INDEX "ix_embed_session_idx_embed_session_user" ON "embed_session" ("flow_user_id", "status");
CREATE INDEX "ix_embed_session_fk_embed_session_grant" ON "embed_session" ("grant_id");
CREATE INDEX "ix_embed_session_fk_embed_session_release" ON "embed_session" ("view_release_id");
CREATE INDEX "ix_embed_session_fk_embed_session_provider" ON "embed_session" ("identity_provider_id");
CREATE INDEX "ix_embed_session_fk_embed_session_binding" ON "embed_session" ("identity_binding_id");
CREATE INDEX "ix_embed_session_counter_fk_embed_session_counter_user" ON "embed_session_counter" ("flow_user_id");
CREATE UNIQUE INDEX "uq_embed_view_uk_embed_view_key" ON "embed_view" ("view_key");
CREATE INDEX "ix_embed_view_idx_embed_view_status" ON "embed_view" ("status", "update_time");
CREATE UNIQUE INDEX "uq_embed_view_release_uk_embed_view_release_revision" ON "embed_view_release" ("view_id", "revision");
CREATE INDEX "ix_embed_view_release_idx_embed_view_release_published" ON "embed_view_release" ("view_id", "published_at");
CREATE UNIQUE INDEX "uq_entity_code_rule_uk_entity_code" ON "entity_code_rule" ("entity_code");
CREATE UNIQUE INDEX "uq_entity_definition_entity_code" ON "entity_definition" ("entity_code");
CREATE INDEX "ix_entity_definition_idx_entity_code" ON "entity_definition" ("entity_code");
CREATE INDEX "ix_entity_definition_idx_status" ON "entity_definition" ("status");
CREATE INDEX "ix_entity_definition_idx_lifecycle_mode" ON "entity_definition" ("lifecycle_mode");
CREATE INDEX "ix_entity_definition_idx_storage_mode" ON "entity_definition" ("storage_mode");
CREATE INDEX "ix_entity_definition_idx_entity_definition_process_binding" ON "entity_definition" ("active_process_definition_key");
CREATE UNIQUE INDEX "uq_entity_field_uk_entity_field" ON "entity_field" ("entity_id", "field_code");
CREATE INDEX "ix_entity_field_idx_entity_id" ON "entity_field" ("entity_id");
CREATE INDEX "ix_entity_field_idx_field_code" ON "entity_field" ("field_code");
CREATE UNIQUE INDEX "uq_entity_field_file_item_uk_entity_field_file_item_key" ON "entity_field_file_item" ("field_id", "item_key");
CREATE INDEX "ix_entity_field_file_item_idx_field_id" ON "entity_field_file_item" ("field_id");
CREATE INDEX "ix_entity_field_file_item_idx_sort_order" ON "entity_field_file_item" ("sort_order");
CREATE UNIQUE INDEX "uq_entity_field_option_uk_entity_field_option" ON "entity_field_option" ("field_id", "option_value");
CREATE INDEX "ix_entity_field_option_idx_entity_field_option_sort" ON "entity_field_option" ("field_id", "sort_order");
CREATE UNIQUE INDEX "uq_entity_form_uk_entity_form_key" ON "entity_form" ("entity_id", "form_key");
CREATE INDEX "ix_entity_form_idx_entity_id" ON "entity_form" ("entity_id");
CREATE INDEX "ix_entity_form_idx_status" ON "entity_form" ("status");
CREATE INDEX "ix_entity_form_idx_deleted" ON "entity_form" ("deleted");
CREATE UNIQUE INDEX "uq_entity_form_node_uk_entity_form_node_active_key" ON "entity_form_node" ("form_id", "active_node_key");
CREATE INDEX "ix_entity_form_node_idx_entity_form_node_tree" ON "entity_form_node" ("form_id", "parent_id", "order_key", "deleted");
CREATE INDEX "ix_entity_form_node_idx_entity_form_node_binding" ON "entity_form_node" ("form_id", "binding_type", "binding_ref", "deleted");
CREATE INDEX "ix_entity_form_node_idx_entity_form_node_form_key" ON "entity_form_node" ("form_id", "node_key");
CREATE UNIQUE INDEX "uq_entity_form_unique_claim_uk_form_unique_claim_record" ON "entity_form_unique_claim" ("constraint_key", "record_id");
CREATE INDEX "ix_entity_form_unique_claim_idx_form_unique_claim_record" ON "entity_form_unique_claim" ("entity_code", "record_id");
CREATE INDEX "ix_entity_form_unique_claim_idx_form_unique_claim_form_rule" ON "entity_form_unique_claim" ("form_id", "rule_id");
CREATE UNIQUE INDEX "uq_entity_list_action_uk_entity_list_action" ON "entity_list_action" ("list_config_id", "position", "button_key", "deleted");
CREATE INDEX "ix_entity_list_action_idx_entity_list_action_runtime" ON "entity_list_action" ("list_config_id", "position", "enabled", "deleted");
CREATE UNIQUE INDEX "uq_entity_list_config_uk_entity_list_key" ON "entity_list_config" ("entity_id", "list_key", "deleted");
CREATE INDEX "ix_entity_list_config_idx_entity_id" ON "entity_list_config" ("entity_id");
CREATE UNIQUE INDEX "uq_entity_list_field_uk_list_field" ON "entity_list_field" ("list_config_id", "field_id", "deleted");
CREATE INDEX "ix_entity_list_field_idx_list_config_id" ON "entity_list_field" ("list_config_id");
CREATE UNIQUE INDEX "uq_entity_list_scene_uk_entity_list_scene" ON "entity_list_scene" ("list_config_id", "scene_code");
CREATE INDEX "ix_entity_list_scope_audit_log_idx_entity_list_scope_audit" ON "entity_list_scope_audit_log" ("entity_code", "list_key", "create_time");
CREATE INDEX "ix_entity_list_scope_audit_log_idx_entity_list_scope__6fbecace" ON "entity_list_scope_audit_log" ("user_id", "create_time");
CREATE INDEX "ix_entity_list_scope_binding_idx_entity_list_scope_bi_0efa01da" ON "entity_list_scope_binding" ("entity_code", "list_key", "enabled", "deleted");
CREATE INDEX "ix_entity_list_scope_binding_idx_entity_list_scope_bi_920f4273" ON "entity_list_scope_binding" ("policy_id");
CREATE INDEX "ix_entity_list_scope_delegation_idx_entity_list_scope_6efa088c" ON "entity_list_scope_delegation" ("to_user_id", "entity_code", "enabled", "deleted");
CREATE INDEX "ix_entity_list_scope_delegation_idx_entity_list_scope_8636949f" ON "entity_list_scope_delegation" ("from_user_id");
CREATE UNIQUE INDEX "uq_entity_list_scope_policy_uk_entity_list_scope_policy" ON "entity_list_scope_policy" ("entity_code", "policy_key", "deleted");
CREATE INDEX "ix_entity_list_scope_policy_idx_entity_list_scope_pol_4244dcc4" ON "entity_list_scope_policy" ("entity_code", "status", "enabled", "deleted");
CREATE UNIQUE INDEX "uq_entity_list_scope_release_uk_entity_list_scope_release" ON "entity_list_scope_release" ("entity_code", "version");
CREATE INDEX "ix_entity_list_scope_release_idx_entity_list_scope_re_d7413cb7" ON "entity_list_scope_release" ("entity_code", "status");
CREATE UNIQUE INDEX "uq_entity_mutation_receipt_uk_entity_mutation_receipt_key" ON "entity_mutation_receipt" ("idempotency_key");
CREATE INDEX "ix_entity_mutation_receipt_idx_entity_mutation_receipt_record" ON "entity_mutation_receipt" ("entity_code", "record_id", "create_time");
CREATE UNIQUE INDEX "uq_entity_process_link_uk_entity_process_generation" ON "entity_process_link" ("entity_code", "entity_record_id", "generation");
CREATE UNIQUE INDEX "uq_entity_process_link_uk_entity_process_request" ON "entity_process_link" ("request_id");
CREATE UNIQUE INDEX "uq_entity_process_link_uk_entity_process_instance" ON "entity_process_link" ("process_instance_id");
CREATE INDEX "ix_entity_process_link_idx_entity_process_state" ON "entity_process_link" ("state", "update_time");
CREATE UNIQUE INDEX "uq_entity_publish_history_uk_entity_version" ON "entity_publish_history" ("entity_id", "version");
CREATE INDEX "ix_entity_publish_history_idx_entity_code" ON "entity_publish_history" ("entity_code");
CREATE INDEX "ix_entity_publish_history_idx_publish_type" ON "entity_publish_history" ("publish_type");
CREATE INDEX "ix_entity_publish_history_idx_status" ON "entity_publish_history" ("status");
CREATE INDEX "ix_entity_publish_history_idx_published_at" ON "entity_publish_history" ("published_at");
CREATE UNIQUE INDEX "uq_entity_record_version_uk_entity_record_version_no" ON "entity_record_version" ("entity_code", "record_id", "version_no");
CREATE UNIQUE INDEX "uq_entity_record_version_uk_entity_record_version_idempotent" ON "entity_record_version" ("entity_code", "record_id", "idempotency_key");
CREATE INDEX "ix_entity_record_version_idx_entity_record_version_time" ON "entity_record_version" ("entity_code", "record_id", "create_time");
CREATE INDEX "ix_entity_record_version_idx_entity_record_version_process" ON "entity_record_version" ("process_instance_id");
CREATE INDEX "ix_entity_record_version_idx_entity_record_version_release" ON "entity_record_version" ("config_release_id");
CREATE INDEX "ix_entity_record_version_idx_entity_record_version_schema" ON "entity_record_version" ("schema_version", "create_time");
CREATE UNIQUE INDEX "uq_entity_record_version_dataset_uk_entity_record_ver_a3ceaf16" ON "entity_record_version_dataset" ("version_id", "node_code");
CREATE INDEX "ix_entity_record_version_dataset_idx_entity_record_ve_6ee06e5b" ON "entity_record_version_dataset" ("relation_code", "entity_code");
CREATE UNIQUE INDEX "uq_entity_record_version_dataset_row_uk_entity_record_65820634" ON "entity_record_version_dataset_row" ("dataset_id", "record_id");
CREATE INDEX "ix_entity_record_version_dataset_row_idx_entity_recor_8076356a" ON "entity_record_version_dataset_row" ("dataset_id", "row_order", "record_id");
CREATE UNIQUE INDEX "uq_entity_relation_uk_entity_relation_code" ON "entity_relation" ("parent_entity_id", "relation_code");
CREATE UNIQUE INDEX "uq_entity_relation_uk_parent_field" ON "entity_relation" ("parent_entity_id", "parent_field_code");
CREATE UNIQUE INDEX "uq_entity_relation_uk_entity_relation_data_key" ON "entity_relation" ("parent_entity_id", "data_key");
CREATE INDEX "ix_entity_relation_idx_parent_entity" ON "entity_relation" ("parent_entity_id", "enabled", "deleted");
CREATE INDEX "ix_entity_relation_idx_parent_code" ON "entity_relation" ("parent_entity_code", "enabled", "deleted");
CREATE INDEX "ix_entity_relation_idx_child_entity" ON "entity_relation" ("child_entity_id");
CREATE INDEX "ix_entity_relation_idx_relation_code" ON "entity_relation" ("relation_code");
CREATE INDEX "ix_entity_relation_idx_entity_relation_child_ref" ON "entity_relation" ("child_entity_id", "child_ref_field_code");
CREATE UNIQUE INDEX "uq_entity_schema_operation_uk_entity_schema_operation_plan" ON "entity_schema_operation" ("entity_id", "plan_hash");
CREATE UNIQUE INDEX "uq_entity_schema_operation_uk_entity_schema_operation_293864c8" ON "entity_schema_operation" ("idempotency_key");
CREATE INDEX "ix_entity_schema_operation_idx_entity_schema_operation_latest" ON "entity_schema_operation" ("entity_id", "create_time");
CREATE INDEX "ix_entity_schema_operation_idx_entity_schema_operation_status" ON "entity_schema_operation" ("status", "update_time");
CREATE INDEX "ix_entity_schema_operation_idx_schema_operation_source" ON "entity_schema_operation" ("operation_source", "source_reference_id");
CREATE INDEX "ix_entity_schema_operation_event_idx_entity_schema_op_92e8c044" ON "entity_schema_operation_event" ("operation_id", "create_time");
CREATE UNIQUE INDEX "uq_entity_status_uk_entity_status" ON "entity_status" ("entity_code", "status_code", "deleted");
CREATE INDEX "ix_entity_status_idx_entity_code" ON "entity_status" ("entity_code");
CREATE INDEX "ix_entity_status_idx_status_category" ON "entity_status" ("status_category");
CREATE INDEX "ix_entity_unique_value_idx_entity_unique_value_record" ON "entity_unique_value" ("entity_code", "record_id");
CREATE UNIQUE INDEX "uq_entity_version_config_uk_entity_version_config_code" ON "entity_version_config" ("entity_code", "deleted");
CREATE INDEX "ix_entity_version_config_idx_entity_version_config_release" ON "entity_version_config" ("active_release_id");
CREATE UNIQUE INDEX "uq_entity_version_config_release_uk_entity_version_co_d7356502" ON "entity_version_config_release" ("config_id", "version");
CREATE UNIQUE INDEX "uq_flw_channel_definition_act_idx_channel_def_uniq" ON "flw_channel_definition" ("key_", "version_", "tenant_id_");
CREATE UNIQUE INDEX "uq_flw_event_definition_act_idx_event_def_uniq" ON "flw_event_definition" ("key_", "version_", "tenant_id_");
CREATE INDEX "ix_flw_event_resource_flw_idx_event_rsrc_dpl" ON "flw_event_resource" ("deployment_id_");
CREATE INDEX "ix_flw_ru_batch_part_flw_idx_batch_part" ON "flw_ru_batch_part" ("batch_id_");
CREATE INDEX "ix_integration_api_request_lease_idx_integration_api__af207486" ON "integration_api_request_lease" ("application_id", "expires_at");
CREATE INDEX "ix_integration_api_request_lease_idx_integration_api__b877dca7" ON "integration_api_request_lease" ("expires_at");
CREATE INDEX "ix_integration_api_request_lease_idx_integration_api__53e6a8aa" ON "integration_api_request_lease" ("application_id", "scope_key", "expires_at");
CREATE UNIQUE INDEX "uq_integration_application_uk_integration_application_b091f38f" ON "integration_application" ("client_id");
CREATE INDEX "ix_integration_application_idx_integration_application_owner" ON "integration_application" ("owner_organization_id", "status");
CREATE INDEX "ix_integration_application_idx_integration_applicatio_6d796752" ON "integration_application" ("status", "expires_at");
CREATE UNIQUE INDEX "uq_integration_application_credential_uk_integration__1664adce" ON "integration_application_credential" ("application_id", "credential_version");
CREATE UNIQUE INDEX "uq_integration_application_credential_uk_integration__035a3d95" ON "integration_application_credential" ("active_application_id");
CREATE INDEX "ix_integration_application_credential_idx_integration_9d0bc3e8" ON "integration_application_credential" ("application_id", "status", "create_time");
CREATE UNIQUE INDEX "uq_integration_idempotency_record_uk_integration_idem_86c00c6a" ON "integration_idempotency_record" ("application_id", "operation", "idempotency_key");
CREATE INDEX "ix_integration_idempotency_record_idx_integration_ide_167d8249" ON "integration_idempotency_record" ("expires_at", "status");
CREATE INDEX "ix_integration_idempotency_record_idx_integration_ide_f910cd72" ON "integration_idempotency_record" ("application_id", "resource_type", "resource_id");
CREATE INDEX "ix_integration_rate_limit_bucket_idx_integration_rate_b4e59aa9" ON "integration_rate_limit_bucket" ("update_time");
CREATE INDEX "ix_process_action_idx_process_config" ON "process_action" ("process_config_id");
CREATE INDEX "ix_process_action_idx_version" ON "process_action" ("version_id");
CREATE INDEX "ix_process_action_idx_status" ON "process_action" ("status");
CREATE INDEX "ix_process_action_idx_process_action_binding" ON "process_action" ("process_config_id", "scope_type", "element_id", "trigger_timing", "status", "deleted");
CREATE INDEX "ix_process_action_idx_process_action_version_binding" ON "process_action" ("version_id", "scope_type", "element_id", "trigger_timing", "status", "deleted");
CREATE INDEX "ix_process_action_idx_process_action_definition_id" ON "process_action" ("action_definition_id", "status", "deleted");
CREATE UNIQUE INDEX "uq_process_action_definition_uk_process_action_definition_code" ON "process_action_definition" ("action_code");
CREATE UNIQUE INDEX "uq_process_action_definition_uk_process_action_defini_583052bf" ON "process_action_definition" ("handler_name");
CREATE INDEX "ix_process_action_definition_idx_process_action_defin_0aeec3d3" ON "process_action_definition" ("visibility_scope", "enabled", "deleted");
CREATE UNIQUE INDEX "uq_process_action_definition_entity_uk_process_action_0a4385c4" ON "process_action_definition_entity" ("action_definition_id", "entity_code");
CREATE INDEX "ix_process_action_definition_entity_idx_process_actio_a7dccc54" ON "process_action_definition_entity" ("entity_code", "action_definition_id");
CREATE UNIQUE INDEX "uq_process_action_execution_uk_process_action_executi_4724c658" ON "process_action_execution" ("idempotency_key");
CREATE INDEX "ix_process_action_execution_idx_process_action_execution_ready" ON "process_action_execution" ("status", "next_retry_time", "create_time");
CREATE INDEX "ix_process_action_execution_idx_process_action_execut_65059e05" ON "process_action_execution" ("process_instance_id", "create_time");
CREATE INDEX "ix_process_action_execution_idx_process_action_execut_dbbaf5a3" ON "process_action_execution" ("action_id", "create_time");
CREATE INDEX "ix_process_action_execution_idx_process_action_execut_577f4bee" ON "process_action_execution" ("entity_code", "process_instance_id", "create_time");
CREATE INDEX "ix_process_action_execution_idx_process_action_execution_lease" ON "process_action_execution" ("status", "lease_until");
CREATE UNIQUE INDEX "uq_process_assignee_incident_uk_process_assignee_incident_open" ON "process_assignee_incident" ("open_slot");
CREATE INDEX "ix_process_assignee_incident_idx_process_assignee_inc_6f2a72e2" ON "process_assignee_incident" ("status", "next_retry_at");
CREATE INDEX "ix_process_assignee_incident_idx_process_assignee_inc_2e83d8a5" ON "process_assignee_incident" ("process_instance_id", "create_time");
CREATE INDEX "ix_process_assignee_incident_idx_process_assignee_inc_005a0726" ON "process_assignee_incident" ("responsibility_owner", "status");
CREATE UNIQUE INDEX "uq_process_assignee_incident_action_uk_assignee_incid_cd87c782" ON "process_assignee_incident_action" ("incident_id", "request_id");
CREATE INDEX "ix_process_assignee_incident_action_idx_assignee_inci_a40c922c" ON "process_assignee_incident_action" ("incident_id", "create_time");
CREATE UNIQUE INDEX "uq_process_cc_record_uk_process_cc_unique_key" ON "process_cc_record" ("unique_key");
CREATE INDEX "ix_process_cc_record_idx_process_instance" ON "process_cc_record" ("process_instance_id");
CREATE INDEX "ix_process_cc_record_idx_cc_user" ON "process_cc_record" ("cc_user_id", "read_status");
CREATE INDEX "ix_process_cc_record_idx_process_key" ON "process_cc_record" ("process_key");
CREATE INDEX "ix_process_cc_record_idx_deleted" ON "process_cc_record" ("deleted");
CREATE INDEX "ix_process_cc_record_idx_create_time" ON "process_cc_record" ("create_time");
CREATE INDEX "ix_process_cc_record_idx_process_cc_source_task" ON "process_cc_record" ("source_task_id");
CREATE UNIQUE INDEX "uq_process_definition_config_process_key" ON "process_definition_config" ("process_key");
CREATE INDEX "ix_process_definition_config_idx_process_key" ON "process_definition_config" ("process_key");
CREATE INDEX "ix_process_definition_config_idx_status" ON "process_definition_config" ("status");
CREATE INDEX "ix_process_definition_config_idx_category" ON "process_definition_config" ("category");
CREATE UNIQUE INDEX "uq_process_entity_status_mapping_uk_process_source_target" ON "process_entity_status_mapping" ("process_config_id", "source_node_id", "target_node_id", "deleted");
CREATE INDEX "ix_process_entity_status_mapping_idx_process_config" ON "process_entity_status_mapping" ("process_config_id");
CREATE INDEX "ix_process_entity_status_mapping_idx_process_key" ON "process_entity_status_mapping" ("process_key");
CREATE INDEX "ix_process_entity_status_mapping_idx_entity_code" ON "process_entity_status_mapping" ("entity_code");
CREATE INDEX "ix_process_entity_status_mapping_idx_source_node" ON "process_entity_status_mapping" ("source_node_id");
CREATE INDEX "ix_process_form_config_idx_node_config_id" ON "process_form_config" ("node_config_id");
CREATE INDEX "ix_process_form_config_idx_form_key" ON "process_form_config" ("form_key");
CREATE INDEX "ix_process_form_field_config_idx_form_config_id" ON "process_form_field_config" ("form_config_id");
CREATE INDEX "ix_process_form_field_config_idx_field_key" ON "process_form_field_config" ("field_key");
CREATE INDEX "ix_process_node_approval_idx_process_node" ON "process_node_approval" ("process_config_id", "node_id");
CREATE UNIQUE INDEX "uq_process_node_approval_option_uk_process_approval_option" ON "process_node_approval_option" ("approval_config_id", "option_value");
CREATE INDEX "ix_process_node_approval_option_idx_process_approval__9d0e8775" ON "process_node_approval_option" ("approval_config_id", "sort_order");
CREATE INDEX "ix_process_node_assignee_idx_node_config_id" ON "process_node_assignee" ("node_config_id");
CREATE INDEX "ix_process_node_config_idx_process_config_id" ON "process_node_config" ("process_config_id");
CREATE INDEX "ix_process_node_config_idx_node_id" ON "process_node_config" ("node_id");
CREATE UNIQUE INDEX "uq_process_node_form_uk_process_node" ON "process_node_form" ("process_config_id", "node_id");
CREATE INDEX "ix_process_node_form_idx_process_config_id" ON "process_node_form" ("process_config_id");
CREATE INDEX "ix_process_node_form_idx_form_id" ON "process_node_form" ("form_id");
CREATE INDEX "ix_process_operation_log_idx_process" ON "process_operation_log" ("process_instance_id", "operation_time");
CREATE INDEX "ix_process_operation_log_idx_operator" ON "process_operation_log" ("operator_id", "operation_time");
CREATE UNIQUE INDEX "uq_process_person_resolver_definition_uk_person_resolver_code" ON "process_person_resolver_definition" ("resolver_code", "deleted");
CREATE INDEX "ix_process_person_resolver_definition_idx_person_reso_4b049c8e" ON "process_person_resolver_definition" ("enabled", "deleted");
CREATE UNIQUE INDEX "uq_process_status_sync_event_uk_process_status_sync_event" ON "process_status_sync_event" ("process_instance_id", "event_type", "event_sequence");
CREATE INDEX "ix_process_status_sync_event_idx_process_status_sync_entity" ON "process_status_sync_event" ("entity_code", "entity_record_id", "create_time");
CREATE INDEX "ix_process_status_sync_event_idx_process_status_sync_state" ON "process_status_sync_event" ("state", "update_time");
CREATE UNIQUE INDEX "uq_process_task_task_id" ON "process_task" ("task_id");
CREATE INDEX "ix_process_task_idx_process_instance" ON "process_task" ("process_instance_id");
CREATE INDEX "ix_process_task_idx_assignee" ON "process_task" ("assignee_id", "status");
CREATE INDEX "ix_process_task_idx_status" ON "process_task" ("status");
CREATE INDEX "ix_process_task_idx_business_key" ON "process_task" ("business_key");
CREATE INDEX "ix_process_task_idx_process_task_sla_status" ON "process_task" ("sla_status", "due_time");
CREATE INDEX "ix_process_task_idx_task_done_page" ON "process_task" ("assignee_id", "status", "deleted", "end_time", "id");
CREATE INDEX "ix_process_task_idx_task_todo_page" ON "process_task" ("status", "deleted", "create_time", "id");
CREATE INDEX "ix_process_task_idx_task_business_summary" ON "process_task" ("entity_code", "entity_data_id", "id");
CREATE INDEX "ix_process_task_add_sign_idx_add_sign_source_task" ON "process_task_add_sign" ("source_task_id", "status");
CREATE INDEX "ix_process_task_add_sign_idx_add_sign_process" ON "process_task_add_sign" ("process_instance_id", "status");
CREATE UNIQUE INDEX "uq_process_task_add_sign_user_uk_add_sign_user" ON "process_task_add_sign_user" ("add_sign_id", "user_id");
CREATE UNIQUE INDEX "uq_process_task_add_sign_user_uk_add_sign_generated_task" ON "process_task_add_sign_user" ("generated_task_id");
CREATE INDEX "ix_process_task_add_sign_user_idx_add_sign_user_status" ON "process_task_add_sign_user" ("user_id", "status");
CREATE UNIQUE INDEX "uq_process_task_candidate_group_uk_process_task_candi_8a0586d6" ON "process_task_candidate_group" ("process_task_id", "group_code");
CREATE INDEX "ix_process_task_candidate_group_idx_task_candidate_gr_7818397b" ON "process_task_candidate_group" ("group_code", "process_task_id");
CREATE UNIQUE INDEX "uq_process_task_candidate_user_uk_process_task_candidate_user" ON "process_task_candidate_user" ("process_task_id", "user_id");
CREATE INDEX "ix_process_task_candidate_user_idx_task_candidate_user_lookup" ON "process_task_candidate_user" ("user_id", "process_task_id");
CREATE UNIQUE INDEX "uq_process_task_sla_uk_process_task_sla_task" ON "process_task_sla" ("task_id");
CREATE INDEX "ix_process_task_sla_idx_process_task_sla_process" ON "process_task_sla" ("process_instance_id", "node_id");
CREATE INDEX "ix_process_task_sla_idx_process_task_sla_response" ON "process_task_sla" ("response_status", "response_due_at");
CREATE INDEX "ix_process_task_sla_idx_process_task_sla_completion" ON "process_task_sla" ("completion_status", "completion_due_at");
CREATE INDEX "ix_process_task_sla_idx_process_task_sla_assignee" ON "process_task_sla" ("current_assignee_id", "overall_status");
CREATE UNIQUE INDEX "uq_process_task_sla_event_uk_process_task_sla_event_key" ON "process_task_sla_event" ("idempotency_key");
CREATE INDEX "ix_process_task_sla_event_idx_process_task_sla_event_ready" ON "process_task_sla_event" ("status", "trigger_at", "next_retry_time");
CREATE INDEX "ix_process_task_sla_event_idx_process_task_sla_event_lease" ON "process_task_sla_event" ("status", "lease_until");
CREATE INDEX "ix_process_task_sla_event_idx_process_task_sla_event_sla" ON "process_task_sla_event" ("sla_id", "create_time");
CREATE INDEX "ix_process_task_sla_pause_idx_process_task_sla_pause" ON "process_task_sla_pause" ("sla_id", "started_at", "resumed_at");
CREATE INDEX "ix_process_task_sla_pause_idx_process_task_sla_pause_task" ON "process_task_sla_pause" ("task_id", "resumed_at");
CREATE UNIQUE INDEX "uq_process_ui_release_binding_uk_process_ui_release_binding" ON "process_ui_release_binding" ("process_version_history_id", "node_id", "config_type", "config_id");
CREATE INDEX "ix_process_ui_release_binding_idx_process_ui_binding_config" ON "process_ui_release_binding" ("config_type", "config_id", "process_version_history_id");
CREATE INDEX "ix_process_ui_release_binding_idx_process_ui_binding_release" ON "process_ui_release_binding" ("pinned_release_id", "process_version_history_id");
CREATE INDEX "ix_process_ui_release_binding_idx_process_ui_binding__4641dfb6" ON "process_ui_release_binding" ("deployment_id");
CREATE UNIQUE INDEX "uq_process_version_history_uk_process_version" ON "process_version_history" ("process_config_id", "version");
CREATE INDEX "ix_process_version_history_idx_process_config_id" ON "process_version_history" ("process_config_id");
CREATE INDEX "ix_process_version_history_idx_process_key" ON "process_version_history" ("process_key");
CREATE INDEX "ix_process_version_history_idx_version" ON "process_version_history" ("version");
CREATE INDEX "ix_process_version_history_idx_deployment_id" ON "process_version_history" ("deployment_id");
CREATE UNIQUE INDEX "uq_storage_file_object_uk_storage_file_url" ON "storage_file_object" (left("storage_url",768));
CREATE UNIQUE INDEX "uq_storage_file_object_uk_storage_file_owner_idempotency" ON "storage_file_object" ("owner_user_id", "idempotency_key");
CREATE INDEX "ix_storage_file_object_idx_storage_file_owner" ON "storage_file_object" ("owner_user_id", "deleted", "create_time");
CREATE UNIQUE INDEX "uq_sys_dict_uk_dict_code" ON "sys_dict" ("dict_code", "deleted");
CREATE INDEX "ix_sys_dict_item_idx_dict_id" ON "sys_dict_item" ("dict_id");
CREATE INDEX "ix_sys_dict_item_idx_dict_code" ON "sys_dict_item" ("dict_code");
CREATE INDEX "ix_sys_dict_item_idx_parent_id" ON "sys_dict_item" ("parent_id");
CREATE INDEX "ix_sys_dict_item_idx_dict_item_lookup" ON "sys_dict_item" ("dict_code", "item_code", "deleted");
CREATE UNIQUE INDEX "uq_sys_external_system_uk_sys_external_system_code" ON "sys_external_system" ("system_code");
CREATE INDEX "ix_sys_external_system_idx_sys_external_system_status_name" ON "sys_external_system" ("deleted", "status", "system_name");
CREATE UNIQUE INDEX "uq_sys_external_system_parameter_uk_sys_external_syst_0963e8bc" ON "sys_external_system_parameter" ("external_system_id", "active_parameter_name_en");
CREATE INDEX "ix_sys_external_system_parameter_idx_sys_external_sys_6a21257c" ON "sys_external_system_parameter" ("external_system_id", "deleted", "sort_order", "id");
CREATE UNIQUE INDEX "uq_sys_global_setting_uk_sys_global_setting_owner_key" ON "sys_global_setting" ("scope_type", "owner_id", "setting_key");
CREATE UNIQUE INDEX "uq_sys_group_uk_group_code" ON "sys_group" ("group_code");
CREATE INDEX "ix_sys_group_idx_status" ON "sys_group" ("status");
CREATE INDEX "ix_sys_group_idx_deleted" ON "sys_group" ("deleted");
CREATE INDEX "ix_sys_menu_idx_parent_id" ON "sys_menu" ("parent_id");
CREATE INDEX "ix_sys_menu_idx_sort" ON "sys_menu" ("sort");
CREATE INDEX "ix_sys_menu_idx_status" ON "sys_menu" ("status");
CREATE INDEX "ix_sys_menu_idx_deleted" ON "sys_menu" ("deleted");
CREATE INDEX "ix_sys_menu_idx_entity_code" ON "sys_menu" ("entity_code");
CREATE UNIQUE INDEX "uq_sys_organization_uk_org_code" ON "sys_organization" ("org_code");
CREATE INDEX "ix_sys_organization_idx_parent_id" ON "sys_organization" ("parent_id");
CREATE INDEX "ix_sys_organization_idx_type" ON "sys_organization" ("type");
CREATE INDEX "ix_sys_organization_idx_path" ON "sys_organization" ("path");
CREATE INDEX "ix_sys_organization_idx_status" ON "sys_organization" ("status");
CREATE INDEX "ix_sys_organization_idx_deleted" ON "sys_organization" ("deleted");
CREATE INDEX "ix_sys_organization_idx_sys_org_business_level" ON "sys_organization" ("business_level_code", "status", "deleted");
CREATE UNIQUE INDEX "uq_sys_position_uk_sys_position_code" ON "sys_position" ("position_code");
CREATE INDEX "ix_sys_position_idx_sys_position_status_sort" ON "sys_position" ("status", "deleted", "sort_order");
CREATE UNIQUE INDEX "uq_sys_position_assignment_uk_sys_position_assignment_fact" ON "sys_position_assignment" ("position_id", "organization_unit_id", "user_id", "effective_from");
CREATE INDEX "ix_sys_position_assignment_idx_sys_position_assignment_lookup" ON "sys_position_assignment" ("position_id", "organization_unit_id", "effective_from", "effective_to", "revoked_at");
CREATE INDEX "ix_sys_position_assignment_idx_sys_position_assignment_user" ON "sys_position_assignment" ("user_id", "effective_from", "effective_to", "revoked_at");
CREATE INDEX "ix_sys_position_assignment_idx_sys_position_assignment_unit" ON "sys_position_assignment" ("organization_unit_id", "position_id");
CREATE UNIQUE INDEX "uq_sys_position_assignment_batch_uk_sys_position_batc_cc38dd75" ON "sys_position_assignment_batch" ("created_by", "idempotency_key");
CREATE INDEX "ix_sys_position_assignment_batch_idx_sys_position_batch_time" ON "sys_position_assignment_batch" ("create_time");
CREATE UNIQUE INDEX "uq_sys_role_uk_role_code" ON "sys_role" ("role_code");
CREATE INDEX "ix_sys_role_idx_status" ON "sys_role" ("status");
CREATE INDEX "ix_sys_role_idx_deleted" ON "sys_role" ("deleted");
CREATE UNIQUE INDEX "uq_sys_role_menu_uk_role_menu" ON "sys_role_menu" ("role_id", "menu_id");
CREATE INDEX "ix_sys_role_menu_idx_role_id" ON "sys_role_menu" ("role_id");
CREATE INDEX "ix_sys_role_menu_idx_menu_id" ON "sys_role_menu" ("menu_id");
CREATE UNIQUE INDEX "uq_sys_user_uk_username" ON "sys_user" ("username");
CREATE INDEX "ix_sys_user_idx_status" ON "sys_user" ("status");
CREATE INDEX "ix_sys_user_idx_deleted" ON "sys_user" ("deleted");
CREATE INDEX "ix_sys_user_idx_org_id" ON "sys_user" ("org_id");
CREATE INDEX "ix_sys_user_idx_dept_id" ON "sys_user" ("dept_id");
CREATE UNIQUE INDEX "uq_sys_user_group_uk_user_group" ON "sys_user_group" ("user_id", "group_id");
CREATE INDEX "ix_sys_user_group_idx_user_id" ON "sys_user_group" ("user_id");
CREATE INDEX "ix_sys_user_group_idx_group_id" ON "sys_user_group" ("group_id");
CREATE UNIQUE INDEX "uq_sys_user_role_uk_user_role" ON "sys_user_role" ("user_id", "role_id");
CREATE INDEX "ix_sys_user_role_idx_user_id" ON "sys_user_role" ("user_id");
CREATE INDEX "ix_sys_user_role_idx_role_id" ON "sys_user_role" ("role_id");
CREATE UNIQUE INDEX "uq_system_operation_log_uk_system_operation_event" ON "system_operation_log" ("event_id");
CREATE INDEX "ix_system_operation_log_idx_system_operation_created" ON "system_operation_log" ("create_time");
CREATE INDEX "ix_system_operation_log_idx_system_operation_operator" ON "system_operation_log" ("operator_id", "create_time");
CREATE INDEX "ix_system_operation_log_idx_system_operation_module" ON "system_operation_log" ("module_code", "operation_code", "create_time");
CREATE INDEX "ix_system_operation_log_idx_system_operation_target" ON "system_operation_log" ("target_type", "target_id");
CREATE INDEX "ix_system_operation_log_idx_system_operation_result" ON "system_operation_log" ("result", "create_time");
CREATE INDEX "ix_system_operation_log_idx_system_operation_trace" ON "system_operation_log" ("trace_id");
CREATE INDEX "ix_system_operation_log_idx_system_operation_operation" ON "system_operation_log" ("operation_id", "create_time");
CREATE INDEX "ix_system_operation_log_idx_system_operation_source" ON "system_operation_log" ("source_type", "source_id", "create_time");
CREATE INDEX "ix_task_sla_escalation_step_idx_task_sla_step_policy" ON "task_sla_escalation_step" ("policy_id", "enabled", "sort_order");
CREATE UNIQUE INDEX "uq_task_sla_policy_uk_task_sla_policy_version" ON "task_sla_policy" ("policy_code", "version", "deleted");
CREATE INDEX "ix_task_sla_policy_idx_task_sla_policy_status" ON "task_sla_policy" ("policy_code", "status", "deleted", "version");
CREATE UNIQUE INDEX "uq_ui_component_template_uk_ui_component_template_key" ON "ui_component_template" ("template_key", "deleted");
CREATE UNIQUE INDEX "uq_ui_component_template_version_uk_ui_component_temp_11045c22" ON "ui_component_template_version" ("template_id", "version");
CREATE UNIQUE INDEX "uq_ui_config_hotfix_request_uk_ui_hotfix_request_open" ON "ui_config_hotfix_request" ("config_type", "config_id", "open_slot");
CREATE UNIQUE INDEX "uq_ui_config_hotfix_request_uk_ui_hotfix_request_release" ON "ui_config_hotfix_request" ("release_id");
CREATE INDEX "ix_ui_config_hotfix_request_idx_ui_hotfix_request_status" ON "ui_config_hotfix_request" ("status", "window_end");
CREATE INDEX "ix_ui_config_hotfix_request_idx_ui_hotfix_request_config" ON "ui_config_hotfix_request" ("config_type", "config_id", "create_time");
CREATE UNIQUE INDEX "uq_ui_config_hotfix_target_uk_ui_hotfix_target_active" ON "ui_config_hotfix_target" ("config_type", "config_id", "process_version_history_id", "active_slot");
CREATE INDEX "ix_ui_config_hotfix_target_idx_ui_hotfix_target_release" ON "ui_config_hotfix_target" ("hotfix_release_id", "status");
CREATE INDEX "ix_ui_config_hotfix_target_idx_ui_hotfix_target_pinned" ON "ui_config_hotfix_target" ("pinned_release_id", "status");
CREATE INDEX "ix_ui_config_hotfix_target_idx_ui_hotfix_target_process" ON "ui_config_hotfix_target" ("process_version_history_id", "status");
CREATE UNIQUE INDEX "uq_ui_config_release_uk_ui_config_release_version" ON "ui_config_release" ("config_type", "config_id", "version");
CREATE UNIQUE INDEX "uq_ui_config_release_uk_ui_config_release_active" ON "ui_config_release" ("config_type", "config_id", "active_slot");
CREATE INDEX "ix_ui_config_release_idx_ui_config_release_active" ON "ui_config_release" ("config_type", "config_id", "status");
CREATE INDEX "ix_ui_config_release_audit_idx_ui_release_audit_config" ON "ui_config_release_audit" ("config_type", "config_id", "create_time");
CREATE INDEX "ix_ui_config_release_audit_idx_ui_release_audit_release" ON "ui_config_release_audit" ("release_id", "create_time");
CREATE INDEX "ix_ui_config_release_audit_idx_ui_release_audit_operation" ON "ui_config_release_audit" ("operation", "create_time");
CREATE UNIQUE INDEX "uq_ui_event_binding_uk_ui_event_binding_scope" ON "ui_event_binding" ("owner_type", "owner_id", "target_type", "target_key", "event_code", "deleted");
CREATE INDEX "ix_ui_event_binding_idx_ui_event_binding_owner" ON "ui_event_binding" ("owner_type", "owner_id", "enabled", "deleted");
CREATE UNIQUE INDEX "uq_ui_extension_definition_uk_ui_extension_version" ON "ui_extension_definition" ("extension_type", "extension_key", "version", "deleted");
CREATE UNIQUE INDEX "uq_ui_extension_definition_uk_ui_extension_legacy_interface" ON "ui_extension_definition" ("legacy_service_id", "provider_operation_code", "deleted");
CREATE INDEX "ix_ui_extension_definition_idx_ui_extension_catalog" ON "ui_extension_definition" ("extension_type", "extension_key", "status", "deleted");
CREATE UNIQUE INDEX "uq_ui_hotfix_observation_metric_uk_ui_hotfix_observat_9e65e146" ON "ui_hotfix_observation_metric" ("request_id", "metric_code");
CREATE INDEX "ix_ui_hotfix_observation_metric_idx_ui_hotfix_observa_a3b321a9" ON "ui_hotfix_observation_metric" ("release_id", "metric_code");
CREATE UNIQUE INDEX "uq_ui_view_composition_uk_ui_view_composition_active_key" ON "ui_view_composition" ("owner_type", "owner_id", "active_composition_key");
CREATE INDEX "ix_ui_view_composition_idx_ui_view_composition_owner_order" ON "ui_view_composition" ("owner_type", "owner_id", "deleted", "order_key");
CREATE UNIQUE INDEX "uq_work_calendar_uk_work_calendar_code_version" ON "work_calendar" ("calendar_code", "version", "deleted");
CREATE INDEX "ix_work_calendar_idx_work_calendar_status" ON "work_calendar" ("status", "default_flag", "deleted");
CREATE INDEX "ix_work_calendar_binding_idx_work_calendar_binding_scope" ON "work_calendar_binding" ("scope_type", "scope_key", "status", "deleted", "priority");
CREATE INDEX "ix_work_calendar_binding_idx_work_calendar_binding_calendar" ON "work_calendar_binding" ("calendar_id", "deleted");
CREATE UNIQUE INDEX "uq_work_calendar_exception_uk_work_calendar_exception" ON "work_calendar_exception" ("calendar_id", "exception_date");
CREATE INDEX "ix_work_calendar_exception_idx_work_calendar_exception_date" ON "work_calendar_exception" ("calendar_id", "exception_date", "exception_type");
CREATE UNIQUE INDEX "uq_work_calendar_exception_period_uk_work_calendar_ex_5e166939" ON "work_calendar_exception_period" ("exception_id", "start_minute", "end_minute");
CREATE INDEX "ix_work_calendar_exception_period_idx_work_calendar_e_1529dbd2" ON "work_calendar_exception_period" ("exception_id", "sort_order");
CREATE UNIQUE INDEX "uq_work_calendar_period_uk_work_calendar_period" ON "work_calendar_period" ("calendar_id", "day_of_week", "start_minute", "end_minute");
CREATE INDEX "ix_work_calendar_period_idx_work_calendar_period_day" ON "work_calendar_period" ("calendar_id", "day_of_week", "sort_order");
CREATE UNIQUE INDEX "uq_workflow_outbox_event_uk_workflow_outbox_topic_event" ON "workflow_outbox_event" ("topic", "event_key");
CREATE INDEX "ix_workflow_outbox_event_idx_workflow_outbox_ready" ON "workflow_outbox_event" ("status", "next_retry_time", "create_time");
CREATE INDEX "ix_workflow_outbox_event_idx_workflow_outbox_aggregate" ON "workflow_outbox_event" ("aggregate_type", "aggregate_id");
CREATE INDEX "ix_workflow_outbox_event_idx_workflow_outbox_lease" ON "workflow_outbox_event" ("status", "lease_until");
CREATE INDEX "ix_workflow_outbox_event_idx_workflow_outbox_retention" ON "workflow_outbox_event" ("status", "processed_time", "id");
CREATE UNIQUE INDEX "uq_workflow_schema_change_uk_schema_change_active_hash" ON "workflow_schema_change" ("active_hash");
CREATE INDEX "ix_workflow_schema_change_idx_schema_change_claim" ON "workflow_schema_change" ("status", "next_attempt_at", "lease_until", "create_time");
CREATE INDEX "ix_workflow_schema_change_idx_schema_change_hash" ON "workflow_schema_change" ("ddl_hash");

-- 表与字段注释。
COMMENT ON TABLE "act_app_appdef" IS 'Flowable 应用模型应用定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_app_appdef"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_app_appdef"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_app_appdef"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_app_appdef"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_app_appdef"."version_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "act_app_appdef"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_app_appdef"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_app_appdef"."resource_name_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "act_app_appdef"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "act_app_appdef"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_app_deployment" IS 'Flowable 应用模型部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_app_deployment"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_app_deployment"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_app_deployment"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_app_deployment"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_app_deployment"."deploy_time_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "act_app_deployment"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_app_deployment_resource" IS 'Flowable 应用模型部署包资源文件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_app_deployment_resource"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_app_deployment_resource"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_app_deployment_resource"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_app_deployment_resource"."resource_bytes_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON TABLE "act_cmmn_casedef" IS 'Flowable 案例管理案例定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_casedef"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_casedef"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_cmmn_casedef"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_casedef"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_cmmn_casedef"."version_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "act_cmmn_casedef"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_cmmn_casedef"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_cmmn_casedef"."resource_name_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "act_cmmn_casedef"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "act_cmmn_casedef"."has_graphical_notation_" IS 'HAS_GRAPHICAL_NOTATION_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_casedef"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_cmmn_casedef"."dgrm_resource_name_" IS 'DGRM_RESOURCE_NAME_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_casedef"."has_start_form_key_" IS 'HAS_START_FORM_KEY_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_cmmn_deployment" IS 'Flowable 案例管理部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_deployment"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_deployment"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_deployment"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_cmmn_deployment"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_cmmn_deployment"."deploy_time_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "act_cmmn_deployment"."parent_deployment_id_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_deployment"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_cmmn_deployment_resource" IS 'Flowable 案例管理部署包资源文件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_deployment_resource"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_deployment_resource"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_deployment_resource"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_cmmn_deployment_resource"."resource_bytes_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON COLUMN "act_cmmn_deployment_resource"."generated_" IS 'GENERATED_ 引擎属性，供 Flowable 案例管理部署包资源文件表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_cmmn_hi_case_inst" IS 'Flowable 案例管理HI案例实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."business_key_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."parent_id_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."case_def_id_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."state_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."end_time_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."start_user_id_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."callback_id_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."callback_type_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."reference_id_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."reference_type_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."last_reactivation_time_" IS 'LAST_REACTIVATION 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."last_reactivation_user_id_" IS 'LAST_REACTIVATION_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_case_inst"."business_status_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "act_cmmn_hi_mil_inst" IS 'Flowable 案例管理HI里程碑实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."time_stamp_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."case_inst_id_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."case_def_id_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_mil_inst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_cmmn_hi_plan_item_inst" IS 'Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."state_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."case_def_id_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."case_inst_id_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."stage_inst_id_" IS 'STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."is_stage_" IS 'IS_STAGE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."item_definition_id_" IS 'ITEM_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."item_definition_type_" IS 'ITEM_DEFINITION_TYPE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."last_available_time_" IS 'LAST_AVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."last_enabled_time_" IS 'LAST_ENABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."last_disabled_time_" IS 'LAST_DISABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."last_started_time_" IS 'LAST_STARTED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."last_suspended_time_" IS 'LAST_SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."completed_time_" IS 'COMPLETED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."occurred_time_" IS 'OCCURRED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."terminated_time_" IS 'TERMINATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."exit_time_" IS 'EXIT 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."ended_time_" IS 'ENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."last_updated_time_" IS 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."start_user_id_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."reference_id_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."reference_type_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."entry_criterion_id_" IS 'ENTRY_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."exit_criterion_id_" IS 'EXIT_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."show_in_overview_" IS 'SHOW_IN_OVERVIEW_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."extra_value_" IS 'EXTRA_VALUE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."derived_case_def_id_" IS 'DERIVED_CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."last_unavailable_time_" IS 'LAST_UNAVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."assignee_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "act_cmmn_hi_plan_item_inst"."completed_by_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "act_cmmn_ru_case_inst" IS 'Flowable 案例管理RU案例实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."business_key_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."parent_id_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."case_def_id_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."state_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."start_user_id_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."callback_id_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."callback_type_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."lock_time_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."is_completeable_" IS 'IS_COMPLETEABLE_ 引擎属性，供 Flowable 案例管理RU案例实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."reference_id_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."reference_type_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."last_reactivation_time_" IS 'LAST_REACTIVATION 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."last_reactivation_user_id_" IS 'LAST_REACTIVATION_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_case_inst"."business_status_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "act_cmmn_ru_mil_inst" IS 'Flowable 案例管理RU里程碑实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_ru_mil_inst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_ru_mil_inst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_ru_mil_inst"."time_stamp_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "act_cmmn_ru_mil_inst"."case_inst_id_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_mil_inst"."case_def_id_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_mil_inst"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_mil_inst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_cmmn_ru_plan_item_inst" IS 'Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."case_def_id_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."case_inst_id_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."stage_inst_id_" IS 'STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."is_stage_" IS 'IS_STAGE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."state_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."start_user_id_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."reference_id_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."reference_type_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."item_definition_id_" IS 'ITEM_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."item_definition_type_" IS 'ITEM_DEFINITION_TYPE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."is_completeable_" IS 'IS_COMPLETEABLE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."is_count_enabled_" IS '计数开关，控制是否维护关联对象数量';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."var_count_" IS 'VAR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."sentry_part_inst_count_" IS 'SENTRY_PART_INST 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."last_available_time_" IS 'LAST_AVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."last_enabled_time_" IS 'LAST_ENABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."last_disabled_time_" IS 'LAST_DISABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."last_started_time_" IS 'LAST_STARTED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."last_suspended_time_" IS 'LAST_SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."completed_time_" IS 'COMPLETED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."occurred_time_" IS 'OCCURRED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."terminated_time_" IS 'TERMINATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."exit_time_" IS 'EXIT 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."ended_time_" IS 'ENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."entry_criterion_id_" IS 'ENTRY_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."exit_criterion_id_" IS 'EXIT_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."extra_value_" IS 'EXTRA_VALUE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."derived_case_def_id_" IS 'DERIVED_CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."last_unavailable_time_" IS 'LAST_UNAVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."assignee_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "act_cmmn_ru_plan_item_inst"."completed_by_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "act_cmmn_ru_sentry_part_inst" IS 'Flowable 案例管理RU入口条件条件部分实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."case_def_id_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."case_inst_id_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."plan_item_inst_id_" IS 'PLAN_ITEM_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."on_part_id_" IS 'ON_PART 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."if_part_id_" IS 'IF_PART 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_cmmn_ru_sentry_part_inst"."time_stamp_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON TABLE "act_dmn_decision" IS 'Flowable 决策规则决策定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_dmn_decision"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_dmn_decision"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_dmn_decision"."version_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "act_dmn_decision"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_dmn_decision"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_dmn_decision"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_dmn_decision"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_dmn_decision"."resource_name_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "act_dmn_decision"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "act_dmn_decision"."decision_type_" IS 'DECISION_TYPE_ 引擎属性，供 Flowable 决策规则决策定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_dmn_deployment" IS 'Flowable 决策规则部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_dmn_deployment"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_dmn_deployment"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_dmn_deployment"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_dmn_deployment"."deploy_time_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "act_dmn_deployment"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_dmn_deployment"."parent_deployment_id_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "act_dmn_deployment_resource" IS 'Flowable 决策规则部署包资源文件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_dmn_deployment_resource"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_dmn_deployment_resource"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_dmn_deployment_resource"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_dmn_deployment_resource"."resource_bytes_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON TABLE "act_dmn_hi_decision_execution" IS 'Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."decision_definition_id_" IS 'DECISION_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."end_time_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."instance_id_" IS 'INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."activity_id_" IS 'ACTIVITY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."failed_" IS 'FAILED_ 引擎属性，供 Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."execution_json_" IS 'EXECUTION_JSON_ 引擎属性，供 Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_dmn_hi_decision_execution"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON TABLE "act_evt_log" IS 'Flowable 事件事件日志表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_evt_log"."log_nr_" IS 'LOG_NR_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_evt_log"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_evt_log"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_evt_log"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_evt_log"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_evt_log"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_evt_log"."time_stamp_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "act_evt_log"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_evt_log"."data_" IS 'DATA_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_evt_log"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_evt_log"."lock_time_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_evt_log"."is_processed_" IS 'IS_PROCESSED_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_ge_bytearray" IS 'Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ge_bytearray"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ge_bytearray"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ge_bytearray"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_ge_bytearray"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_ge_bytearray"."bytes_" IS 'BYTES_ 引擎属性，供 Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ge_bytearray"."generated_" IS 'GENERATED_ 引擎属性，供 Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_ge_property" IS 'Flowable 通用资源引擎属性表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ge_property"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_ge_property"."value_" IS '属性值，供引擎读取配置和版本信息';
COMMENT ON COLUMN "act_ge_property"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON TABLE "act_hi_actinst" IS 'Flowable 历史记录活动实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_actinst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_actinst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_hi_actinst"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_hi_actinst"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_actinst"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_hi_actinst"."act_id_" IS 'ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_actinst"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_hi_actinst"."call_proc_inst_id_" IS 'CALL_PROC_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_actinst"."act_name_" IS 'ACT_NAME_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_actinst"."act_type_" IS 'ACT_TYPE_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_actinst"."assignee_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "act_hi_actinst"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_hi_actinst"."end_time_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "act_hi_actinst"."transaction_order_" IS 'TRANSACTION_ORDER_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_actinst"."duration_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "act_hi_actinst"."delete_reason_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "act_hi_actinst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_hi_actinst"."completed_by_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "act_hi_attachment" IS 'Flowable 历史记录附件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_attachment"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_attachment"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_hi_attachment"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_attachment"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_hi_attachment"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "act_hi_attachment"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_hi_attachment"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_hi_attachment"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_attachment"."url_" IS 'URL_ 引擎属性，供 Flowable 历史记录附件表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_attachment"."content_id_" IS 'CONTENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_attachment"."time_" IS '发生时间，供引擎事件排序和历史追踪';
COMMENT ON TABLE "act_hi_comment" IS 'Flowable 历史记录批注表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_comment"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_comment"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_hi_comment"."time_" IS '发生时间，供引擎事件排序和历史追踪';
COMMENT ON COLUMN "act_hi_comment"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_comment"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_hi_comment"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_comment"."action_" IS 'ACTION_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_comment"."message_" IS 'MESSAGE_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_comment"."full_msg_" IS 'FULL_MSG_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_hi_detail" IS 'Flowable 历史记录变量详情表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_detail"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_detail"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_hi_detail"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_detail"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_hi_detail"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_hi_detail"."act_inst_id_" IS 'ACT_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_detail"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_hi_detail"."var_type_" IS 'VAR_TYPE_ 引擎属性，供 Flowable 历史记录变量详情表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_detail"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_hi_detail"."time_" IS '发生时间，供引擎事件排序和历史追踪';
COMMENT ON COLUMN "act_hi_detail"."bytearray_id_" IS 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_detail"."double_" IS '浮点变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_hi_detail"."long_" IS '整数变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_hi_detail"."text_" IS '文本变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_hi_detail"."text2_" IS '第二段文本值，保存补充变量或序列化内容';
COMMENT ON TABLE "act_hi_entitylink" IS 'Flowable 历史记录实体关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_entitylink"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_entitylink"."link_type_" IS 'LINK_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_entitylink"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_hi_entitylink"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_entitylink"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_entitylink"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_hi_entitylink"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_entitylink"."parent_element_id_" IS 'PARENT_ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_entitylink"."ref_scope_id_" IS 'REF_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_entitylink"."ref_scope_type_" IS 'REF_SCOPE_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_entitylink"."ref_scope_definition_id_" IS 'REF_SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_entitylink"."root_scope_id_" IS 'ROOT_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_entitylink"."root_scope_type_" IS 'ROOT_SCOPE_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_entitylink"."hierarchy_type_" IS 'HIERARCHY_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_hi_identitylink" IS 'Flowable 历史记录参与者关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_identitylink"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_identitylink"."group_id_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_identitylink"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_hi_identitylink"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_identitylink"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_hi_identitylink"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_hi_identitylink"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_identitylink"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_identitylink"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_identitylink"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_hi_identitylink"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "act_hi_procinst" IS 'Flowable 历史记录流程实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_procinst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_procinst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_hi_procinst"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_procinst"."business_key_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "act_hi_procinst"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_hi_procinst"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_hi_procinst"."end_time_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "act_hi_procinst"."duration_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "act_hi_procinst"."start_user_id_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_procinst"."start_act_id_" IS 'START_ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_procinst"."end_act_id_" IS 'END_ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_procinst"."super_process_instance_id_" IS 'SUPER_PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_procinst"."delete_reason_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "act_hi_procinst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_hi_procinst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_hi_procinst"."callback_id_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_procinst"."callback_type_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "act_hi_procinst"."reference_id_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_procinst"."reference_type_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "act_hi_procinst"."propagated_stage_inst_id_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_procinst"."business_status_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "act_hi_taskinst" IS 'Flowable 历史记录任务实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_taskinst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_taskinst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_hi_taskinst"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_hi_taskinst"."task_def_id_" IS 'TASK_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_taskinst"."task_def_key_" IS 'TASK_DEF_KEY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_taskinst"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_hi_taskinst"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_taskinst"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_taskinst"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_hi_taskinst"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_taskinst"."propagated_stage_inst_id_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_taskinst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_hi_taskinst"."parent_task_id_" IS 'PARENT_TASK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_taskinst"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "act_hi_taskinst"."owner_" IS 'OWNER_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."assignee_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "act_hi_taskinst"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_hi_taskinst"."claim_time_" IS 'CLAIM 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_hi_taskinst"."end_time_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "act_hi_taskinst"."duration_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "act_hi_taskinst"."delete_reason_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "act_hi_taskinst"."priority_" IS 'PRIORITY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."due_date_" IS 'DUE_DATE_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."form_key_" IS 'FORM_KEY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_hi_taskinst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_hi_taskinst"."last_updated_time_" IS 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_hi_taskinst"."state_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "act_hi_taskinst"."in_progress_time_" IS 'IN_PROGRESS 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_hi_taskinst"."in_progress_started_by_" IS 'IN_PROGRESS_STARTED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."claimed_by_" IS 'CLAIMED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."suspended_time_" IS 'SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_hi_taskinst"."suspended_by_" IS 'SUSPENDED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_taskinst"."completed_by_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON COLUMN "act_hi_taskinst"."in_progress_due_date_" IS 'IN_PROGRESS_DUE_DATE_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_hi_tsk_log" IS 'Flowable 历史记录任务事件日志表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_tsk_log"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_tsk_log"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_hi_tsk_log"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_hi_tsk_log"."time_stamp_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "act_hi_tsk_log"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_tsk_log"."data_" IS 'DATA_ 引擎属性，供 Flowable 历史记录任务事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_tsk_log"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_hi_tsk_log"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_tsk_log"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_hi_tsk_log"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_tsk_log"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_tsk_log"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_tsk_log"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_hi_tsk_log"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_hi_varinst" IS 'Flowable 历史记录变量实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_hi_varinst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_hi_varinst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_hi_varinst"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_hi_varinst"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_hi_varinst"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_hi_varinst"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_hi_varinst"."var_type_" IS 'VAR_TYPE_ 引擎属性，供 Flowable 历史记录变量实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_hi_varinst"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_varinst"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_varinst"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_hi_varinst"."bytearray_id_" IS 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_hi_varinst"."double_" IS '浮点变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_hi_varinst"."long_" IS '整数变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_hi_varinst"."text_" IS '文本变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_hi_varinst"."text2_" IS '第二段文本值，保存补充变量或序列化内容';
COMMENT ON COLUMN "act_hi_varinst"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_hi_varinst"."last_updated_time_" IS 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_hi_varinst"."meta_info_" IS '模型元数据，保存编辑器和发布需要的补充信息';
COMMENT ON TABLE "act_id_bytearray" IS 'Flowable 身份管理二进制资源表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_bytearray"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_id_bytearray"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_id_bytearray"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_id_bytearray"."bytes_" IS 'BYTES_ 引擎属性，供 Flowable 身份管理二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_id_group" IS 'Flowable 身份管理用户组表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_group"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_id_group"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_id_group"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_id_group"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON TABLE "act_id_info" IS 'Flowable 身份管理用户资料表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_info"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_id_info"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_id_info"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_id_info"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_id_info"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_id_info"."value_" IS '属性值，供引擎读取配置和版本信息';
COMMENT ON COLUMN "act_id_info"."password_" IS 'PASSWORD_ 引擎属性，供 Flowable 身份管理用户资料表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_info"."parent_id_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "act_id_membership" IS 'Flowable 身份管理用户组成员表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_membership"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_id_membership"."group_id_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "act_id_priv" IS 'Flowable 身份管理权限表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_priv"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_id_priv"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON TABLE "act_id_priv_mapping" IS 'Flowable 身份管理权限权限映射表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_priv_mapping"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_id_priv_mapping"."priv_id_" IS 'PRIV 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_id_priv_mapping"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_id_priv_mapping"."group_id_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "act_id_property" IS 'Flowable 身份管理引擎属性表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_property"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_id_property"."value_" IS '属性值，供引擎读取配置和版本信息';
COMMENT ON COLUMN "act_id_property"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON TABLE "act_id_token" IS 'Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_token"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_id_token"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_id_token"."token_value_" IS 'TOKEN_VALUE_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_token"."token_date_" IS 'TOKEN_DATE_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_token"."ip_address_" IS 'IP_ADDRESS_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_token"."user_agent_" IS 'USER_AGENT_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_token"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_id_token"."token_data_" IS 'TOKEN_DATA_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_id_user" IS 'Flowable 身份管理用户表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_id_user"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_id_user"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_id_user"."first_" IS 'FIRST_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_user"."last_" IS 'LAST_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_user"."display_name_" IS 'DISPLAY_NAME_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_user"."email_" IS 'EMAIL_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_user"."pwd_" IS 'PWD_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_id_user"."picture_id_" IS 'PICTURE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_id_user"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_procdef_info" IS 'Flowable 流程引擎用户资料表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_procdef_info"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_procdef_info"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_procdef_info"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_procdef_info"."info_json_id_" IS 'INFO_JSON 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "act_re_deployment" IS 'Flowable 模型仓库部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_re_deployment"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_re_deployment"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_re_deployment"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_re_deployment"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_re_deployment"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_re_deployment"."deploy_time_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "act_re_deployment"."derived_from_" IS 'DERIVED_FROM_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_deployment"."derived_from_root_" IS 'DERIVED_FROM_ROOT_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_deployment"."parent_deployment_id_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_re_deployment"."engine_version_" IS 'ENGINE_VERSION_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_re_model" IS 'Flowable 模型仓库模型表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_re_model"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_re_model"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_re_model"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_re_model"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_re_model"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_re_model"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_re_model"."last_update_time_" IS 'LAST_UPDATE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_re_model"."version_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "act_re_model"."meta_info_" IS '模型元数据，保存编辑器和发布需要的补充信息';
COMMENT ON COLUMN "act_re_model"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_re_model"."editor_source_value_id_" IS 'EDITOR_SOURCE_VALUE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_re_model"."editor_source_extra_value_id_" IS 'EDITOR_SOURCE_EXTRA_VALUE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_re_model"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_re_procdef" IS 'Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_re_procdef"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_re_procdef"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_re_procdef"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_re_procdef"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_re_procdef"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "act_re_procdef"."version_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "act_re_procdef"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "act_re_procdef"."resource_name_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "act_re_procdef"."dgrm_resource_name_" IS 'DGRM_RESOURCE_NAME_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_procdef"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "act_re_procdef"."has_start_form_key_" IS 'HAS_START_FORM_KEY_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_procdef"."has_graphical_notation_" IS 'HAS_GRAPHICAL_NOTATION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_procdef"."suspension_state_" IS '挂起状态，控制定义或实例能否继续执行';
COMMENT ON COLUMN "act_re_procdef"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_re_procdef"."engine_version_" IS 'ENGINE_VERSION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_procdef"."derived_from_" IS 'DERIVED_FROM_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_procdef"."derived_from_root_" IS 'DERIVED_FROM_ROOT_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_re_procdef"."derived_version_" IS 'DERIVED_VERSION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_ru_actinst" IS 'Flowable 运行时活动实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_actinst"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_actinst"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_actinst"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_actinst"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_ru_actinst"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_actinst"."act_id_" IS 'ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_actinst"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_ru_actinst"."call_proc_inst_id_" IS 'CALL_PROC_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_actinst"."act_name_" IS 'ACT_NAME_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_actinst"."act_type_" IS 'ACT_TYPE_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_actinst"."assignee_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "act_ru_actinst"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_ru_actinst"."end_time_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "act_ru_actinst"."duration_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "act_ru_actinst"."transaction_order_" IS 'TRANSACTION_ORDER_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_actinst"."delete_reason_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "act_ru_actinst"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_ru_actinst"."completed_by_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "act_ru_deadletter_job" IS 'Flowable 运行时死信作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_deadletter_job"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_deadletter_job"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_deadletter_job"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_ru_deadletter_job"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_ru_deadletter_job"."exclusive_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "act_ru_deadletter_job"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_deadletter_job"."process_instance_id_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_deadletter_job"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."element_name_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "act_ru_deadletter_job"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_deadletter_job"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."correlation_id_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."exception_stack_id_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."exception_msg_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "act_ru_deadletter_job"."duedate_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "act_ru_deadletter_job"."repeat_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "act_ru_deadletter_job"."handler_type_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "act_ru_deadletter_job"."handler_cfg_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "act_ru_deadletter_job"."custom_values_id_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_deadletter_job"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_deadletter_job"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_ru_entitylink" IS 'Flowable 运行时实体关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_entitylink"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_entitylink"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_entitylink"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_entitylink"."link_type_" IS 'LINK_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_entitylink"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_entitylink"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_entitylink"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_entitylink"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_entitylink"."parent_element_id_" IS 'PARENT_ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_entitylink"."ref_scope_id_" IS 'REF_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_entitylink"."ref_scope_type_" IS 'REF_SCOPE_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_entitylink"."ref_scope_definition_id_" IS 'REF_SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_entitylink"."root_scope_id_" IS 'ROOT_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_entitylink"."root_scope_type_" IS 'ROOT_SCOPE_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_entitylink"."hierarchy_type_" IS 'HIERARCHY_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_ru_event_subscr" IS 'Flowable 运行时事件订阅表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_event_subscr"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_event_subscr"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_event_subscr"."event_type_" IS 'EVENT_TYPE_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_event_subscr"."event_name_" IS 'EVENT_NAME_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_event_subscr"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_event_subscr"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_ru_event_subscr"."activity_id_" IS 'ACTIVITY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_event_subscr"."configuration_" IS 'CONFIGURATION_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_event_subscr"."created_" IS 'CREATED_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_event_subscr"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_event_subscr"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_event_subscr"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_event_subscr"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_event_subscr"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_event_subscr"."lock_time_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_event_subscr"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_ru_event_subscr"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_ru_event_subscr"."scope_definition_key_" IS 'SCOPE_DEFINITION_KEY_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_ru_execution" IS 'Flowable 运行时执行实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_execution"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_execution"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_execution"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_ru_execution"."business_key_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "act_ru_execution"."parent_id_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_execution"."super_exec_" IS 'SUPER_EXEC_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_execution"."root_proc_inst_id_" IS 'ROOT_PROC_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."act_id_" IS 'ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."is_active_" IS 'IS_ACTIVE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_execution"."is_concurrent_" IS 'IS_CONCURRENT_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_execution"."is_scope_" IS 'IS_SCOPE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_execution"."is_event_scope_" IS 'IS_EVENT_SCOPE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_execution"."is_mi_root_" IS 'IS_MI_ROOT_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_execution"."suspension_state_" IS '挂起状态，控制定义或实例能否继续执行';
COMMENT ON COLUMN "act_ru_execution"."cached_ent_state_" IS 'CACHED_ENT_STATE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_execution"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_ru_execution"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_ru_execution"."start_act_id_" IS 'START_ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."start_time_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "act_ru_execution"."start_user_id_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."lock_time_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_execution"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_ru_execution"."is_count_enabled_" IS '计数开关，控制是否维护关联对象数量';
COMMENT ON COLUMN "act_ru_execution"."evt_subscr_count_" IS 'EVT_SUBSCR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."task_count_" IS 'TASK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."job_count_" IS 'JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."timer_job_count_" IS 'TIMER_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."susp_job_count_" IS 'SUSP_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."deadletter_job_count_" IS 'DEADLETTER_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."external_worker_job_count_" IS 'EXTERNAL_WORKER_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."var_count_" IS 'VAR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."id_link_count_" IS 'ID_LINK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_execution"."callback_id_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."callback_type_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "act_ru_execution"."reference_id_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."reference_type_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "act_ru_execution"."propagated_stage_inst_id_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_execution"."business_status_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "act_ru_external_job" IS 'Flowable 运行时外部作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_external_job"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_external_job"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_external_job"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_ru_external_job"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_ru_external_job"."lock_exp_time_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_external_job"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_ru_external_job"."exclusive_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "act_ru_external_job"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_external_job"."process_instance_id_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_external_job"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."element_name_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "act_ru_external_job"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_external_job"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."correlation_id_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."retries_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "act_ru_external_job"."exception_stack_id_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."exception_msg_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "act_ru_external_job"."duedate_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "act_ru_external_job"."repeat_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "act_ru_external_job"."handler_type_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "act_ru_external_job"."handler_cfg_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "act_ru_external_job"."custom_values_id_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_external_job"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_external_job"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_ru_history_job" IS 'Flowable 运行时历史作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_history_job"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_history_job"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_history_job"."lock_exp_time_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_history_job"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_ru_history_job"."retries_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "act_ru_history_job"."exception_stack_id_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_history_job"."exception_msg_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "act_ru_history_job"."handler_type_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "act_ru_history_job"."handler_cfg_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "act_ru_history_job"."custom_values_id_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_history_job"."adv_handler_cfg_id_" IS 'ADV_HANDLER_CFG 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_history_job"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_history_job"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_history_job"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_ru_identitylink" IS 'Flowable 运行时参与者关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_identitylink"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_identitylink"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_identitylink"."group_id_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_identitylink"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_ru_identitylink"."user_id_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_identitylink"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_ru_identitylink"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_ru_identitylink"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_identitylink"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_identitylink"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_identitylink"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_identitylink"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "act_ru_job" IS 'Flowable 运行时作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_job"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_job"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_job"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_ru_job"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_ru_job"."lock_exp_time_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_job"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_ru_job"."exclusive_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "act_ru_job"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_job"."process_instance_id_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_job"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."element_name_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "act_ru_job"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_job"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."correlation_id_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."retries_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "act_ru_job"."exception_stack_id_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."exception_msg_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "act_ru_job"."duedate_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "act_ru_job"."repeat_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "act_ru_job"."handler_type_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "act_ru_job"."handler_cfg_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "act_ru_job"."custom_values_id_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_job"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_job"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_ru_suspended_job" IS 'Flowable 运行时挂起作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_suspended_job"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_suspended_job"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_suspended_job"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_ru_suspended_job"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_ru_suspended_job"."exclusive_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "act_ru_suspended_job"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_suspended_job"."process_instance_id_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_suspended_job"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."element_name_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "act_ru_suspended_job"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_suspended_job"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."correlation_id_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."retries_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "act_ru_suspended_job"."exception_stack_id_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."exception_msg_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "act_ru_suspended_job"."duedate_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "act_ru_suspended_job"."repeat_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "act_ru_suspended_job"."handler_type_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "act_ru_suspended_job"."handler_cfg_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "act_ru_suspended_job"."custom_values_id_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_suspended_job"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_suspended_job"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_ru_task" IS 'Flowable 运行时任务表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_task"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_task"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_task"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_task"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_ru_task"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_task"."task_def_id_" IS 'TASK_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_task"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_task"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_task"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_task"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_task"."propagated_stage_inst_id_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_task"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_ru_task"."parent_task_id_" IS 'PARENT_TASK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_task"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "act_ru_task"."task_def_key_" IS 'TASK_DEF_KEY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."owner_" IS 'OWNER_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."assignee_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "act_ru_task"."delegation_" IS 'DELEGATION_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."priority_" IS 'PRIORITY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_task"."due_date_" IS 'DUE_DATE_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_ru_task"."suspension_state_" IS '挂起状态，控制定义或实例能否继续执行';
COMMENT ON COLUMN "act_ru_task"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "act_ru_task"."form_key_" IS 'FORM_KEY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."claim_time_" IS 'CLAIM 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_task"."is_count_enabled_" IS '计数开关，控制是否维护关联对象数量';
COMMENT ON COLUMN "act_ru_task"."var_count_" IS 'VAR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_task"."id_link_count_" IS 'ID_LINK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_task"."sub_task_count_" IS 'SUB_TASK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "act_ru_task"."state_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "act_ru_task"."in_progress_time_" IS 'IN_PROGRESS 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_task"."in_progress_started_by_" IS 'IN_PROGRESS_STARTED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."claimed_by_" IS 'CLAIMED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."suspended_time_" IS 'SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_task"."suspended_by_" IS 'SUSPENDED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "act_ru_task"."in_progress_due_date_" IS 'IN_PROGRESS_DUE_DATE_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "act_ru_timer_job" IS 'Flowable 运行时定时作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_timer_job"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_timer_job"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_timer_job"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "act_ru_timer_job"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_ru_timer_job"."lock_exp_time_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "act_ru_timer_job"."lock_owner_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "act_ru_timer_job"."exclusive_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "act_ru_timer_job"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_timer_job"."process_instance_id_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."proc_def_id_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "act_ru_timer_job"."element_id_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."element_name_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "act_ru_timer_job"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_timer_job"."scope_definition_id_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."correlation_id_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."retries_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "act_ru_timer_job"."exception_stack_id_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."exception_msg_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "act_ru_timer_job"."duedate_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "act_ru_timer_job"."repeat_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "act_ru_timer_job"."handler_type_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "act_ru_timer_job"."handler_cfg_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "act_ru_timer_job"."custom_values_id_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_timer_job"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "act_ru_timer_job"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "act_ru_variable" IS 'Flowable 运行时变量表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "act_ru_variable"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "act_ru_variable"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "act_ru_variable"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "act_ru_variable"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "act_ru_variable"."execution_id_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "act_ru_variable"."proc_inst_id_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "act_ru_variable"."task_id_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "act_ru_variable"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_variable"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_variable"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "act_ru_variable"."bytearray_id_" IS 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "act_ru_variable"."double_" IS '浮点变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_ru_variable"."long_" IS '整数变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_ru_variable"."text_" IS '文本变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "act_ru_variable"."text2_" IS '第二段文本值，保存补充变量或序列化内容';
COMMENT ON COLUMN "act_ru_variable"."meta_info_" IS '模型元数据，保存编辑器和发布需要的补充信息';
COMMENT ON TABLE "auth_login_throttle" IS '登录失败限流表';
COMMENT ON COLUMN "auth_login_throttle"."throttle_key" IS '限流键。';
COMMENT ON COLUMN "auth_login_throttle"."failure_count" IS '失败数量。';
COMMENT ON COLUMN "auth_login_throttle"."window_started_at" IS '窗口开始时间。';
COMMENT ON COLUMN "auth_login_throttle"."blocked_until" IS '阻断截止时间。';
COMMENT ON COLUMN "auth_login_throttle"."update_time" IS '更新时间。';
COMMENT ON TABLE "auth_refresh_session" IS '浏览器刷新会话表';
COMMENT ON COLUMN "auth_refresh_session"."id" IS '主键ID：刷新会话ID，同时写入Access Token的sid声明。';
COMMENT ON COLUMN "auth_refresh_session"."user_id" IS '会话所属用户ID。';
COMMENT ON COLUMN "auth_refresh_session"."refresh_token_hash" IS '刷新令牌哈希：Refresh Token的SHA-256十六进制摘要。';
COMMENT ON COLUMN "auth_refresh_session"."token_version" IS '令牌版本：创建会话时的用户全局令牌版本。';
COMMENT ON COLUMN "auth_refresh_session"."create_time" IS '创建时间：会话创建时间。';
COMMENT ON COLUMN "auth_refresh_session"."last_used_at" IS '最近使用时间：最近一次成功刷新时间。';
COMMENT ON COLUMN "auth_refresh_session"."idle_expires_at" IS '空闲过期时间：空闲超时时间。';
COMMENT ON COLUMN "auth_refresh_session"."absolute_expires_at" IS '绝对过期时间：会话绝对过期时间。';
COMMENT ON COLUMN "auth_refresh_session"."revoked_at" IS '撤销时间：会话撤销时间。';
COMMENT ON COLUMN "auth_refresh_session"."revoked_reason" IS '会话撤销原因。';
COMMENT ON TABLE "config_asset_baseline" IS '配置资产环境基线表';
COMMENT ON COLUMN "config_asset_baseline"."id" IS '主键ID：配置资产迁移基线。 记录某次成功导入发布后，源环境与目标环境的资产版本/内容哈希对照关系， 用于后续导入时判断生产环境是否相对基线发生了本地修改，从而识别冲突或增量更新。';
COMMENT ON COLUMN "config_asset_baseline"."asset_type" IS '资产类型。';
COMMENT ON COLUMN "config_asset_baseline"."business_key" IS '业务键。';
COMMENT ON COLUMN "config_asset_baseline"."scope_key" IS '范围键。';
COMMENT ON COLUMN "config_asset_baseline"."source_version" IS '来源版本。';
COMMENT ON COLUMN "config_asset_baseline"."source_hash" IS '来源哈希。';
COMMENT ON COLUMN "config_asset_baseline"."target_version" IS '目标版本。';
COMMENT ON COLUMN "config_asset_baseline"."target_hash" IS '目标哈希。';
COMMENT ON COLUMN "config_asset_baseline"."import_package_id" IS '导入配置包ID。';
COMMENT ON COLUMN "config_asset_baseline"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "config_environment_mapping" IS '配置环境映射表';
COMMENT ON COLUMN "config_environment_mapping"."id" IS '主键ID：配置环境映射。 记录源环境与目标环境之间的资源编码映射关系(如用户名、角色、部门、数据源等)， 在导入分析阶段用于将源环境的依赖键解析为目标环境对应的键。';
COMMENT ON COLUMN "config_environment_mapping"."source_type" IS '来源类型。';
COMMENT ON COLUMN "config_environment_mapping"."source_key" IS '来源标识。';
COMMENT ON COLUMN "config_environment_mapping"."target_key" IS '目标标识。';
COMMENT ON COLUMN "config_environment_mapping"."description" IS '说明。';
COMMENT ON COLUMN "config_environment_mapping"."enabled" IS '是否启用。';
COMMENT ON COLUMN "config_environment_mapping"."create_time" IS '创建时间。';
COMMENT ON COLUMN "config_environment_mapping"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "config_export_package" IS '配置导出包表';
COMMENT ON COLUMN "config_export_package"."id" IS '主键ID：配置导出包。 记录从当前环境打包生成的 wfpack 发布包元数据与二进制内容， 包括包编号、校验和、HMAC 签名、资产数量与下载统计等。';
COMMENT ON COLUMN "config_export_package"."package_no" IS '配置包编号。';
COMMENT ON COLUMN "config_export_package"."migration_tag" IS '迁移标记。';
COMMENT ON COLUMN "config_export_package"."file_name" IS '文件名称。';
COMMENT ON COLUMN "config_export_package"."checksum" IS '校验和：内容校验和，用于检测迁移或配置包内容变化；不同表算法以所属服务为准。';
COMMENT ON COLUMN "config_export_package"."signature_value" IS '签名值。';
COMMENT ON COLUMN "config_export_package"."status" IS '状态。';
COMMENT ON COLUMN "config_export_package"."asset_count" IS '资产数量。';
COMMENT ON COLUMN "config_export_package"."package_data" IS '配置包数据。';
COMMENT ON COLUMN "config_export_package"."created_by" IS '创建人。';
COMMENT ON COLUMN "config_export_package"."create_time" IS '创建时间。';
COMMENT ON COLUMN "config_export_package"."download_count" IS '下载数量。';
COMMENT ON COLUMN "config_export_package"."last_download_at" IS '最近下载时间。';
COMMENT ON COLUMN "config_export_package"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "config_export_package_item" IS '配置导出包条目表';
COMMENT ON COLUMN "config_export_package_item"."id" IS '主键ID：配置导出包条目。 记录一个导出包中包含的每个迁移资产及其在导出时使用的选择配置， 用于追溯导出包的资产清单与快照选择范围。';
COMMENT ON COLUMN "config_export_package_item"."package_id" IS '配置包ID。';
COMMENT ON COLUMN "config_export_package_item"."asset_id" IS '资产ID。';
COMMENT ON COLUMN "config_export_package_item"."asset_type" IS '资产类型。';
COMMENT ON COLUMN "config_export_package_item"."business_key" IS '业务键。';
COMMENT ON COLUMN "config_export_package_item"."source_version" IS '来源版本。';
COMMENT ON COLUMN "config_export_package_item"."content_hash" IS '内容哈希：内容摘要，用于检查配置或快照内容是否一致。';
COMMENT ON COLUMN "config_export_package_item"."selection_json" IS '选择JSON。';
COMMENT ON COLUMN "config_export_package_item"."create_time" IS '创建时间。';
COMMENT ON TABLE "config_import_item" IS '配置导入条目表';
COMMENT ON COLUMN "config_import_item"."id" IS '主键ID：配置导入条目。 记录导入批次中单个资产的导入全过程状态，包括源/目标版本对照、 比较结果(NEW/CONSISTENT/CONFLICT等)、依赖映射状态、发布状态及异常信息。';
COMMENT ON COLUMN "config_import_item"."import_package_id" IS '导入配置包ID。';
COMMENT ON COLUMN "config_import_item"."asset_type" IS '资产类型。';
COMMENT ON COLUMN "config_import_item"."business_key" IS '业务键。';
COMMENT ON COLUMN "config_import_item"."asset_name" IS '资产名称。';
COMMENT ON COLUMN "config_import_item"."source_version" IS '来源版本。';
COMMENT ON COLUMN "config_import_item"."source_hash" IS '来源哈希。';
COMMENT ON COLUMN "config_import_item"."target_before_version" IS '目标之前版本。';
COMMENT ON COLUMN "config_import_item"."target_before_hash" IS '目标之前哈希。';
COMMENT ON COLUMN "config_import_item"."target_after_version" IS '目标之后版本。';
COMMENT ON COLUMN "config_import_item"."target_after_hash" IS '目标之后哈希。';
COMMENT ON COLUMN "config_import_item"."comparison_status" IS '比较状态。';
COMMENT ON COLUMN "config_import_item"."mapping_status" IS '映射状态。';
COMMENT ON COLUMN "config_import_item"."publish_status" IS '发布状态。';
COMMENT ON COLUMN "config_import_item"."snapshot_json" IS '快照JSON。';
COMMENT ON COLUMN "config_import_item"."dependencies_json" IS '依赖集合JSON。';
COMMENT ON COLUMN "config_import_item"."error_message" IS '错误消息。';
COMMENT ON COLUMN "config_import_item"."create_time" IS '创建时间。';
COMMENT ON COLUMN "config_import_item"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "config_import_package" IS '配置导入包表';
COMMENT ON COLUMN "config_import_package"."id" IS '主键ID：配置导入批次。 记录从源环境导出的 wfpack 发布包导入到目标环境后的批次信息， 包括原始包内容、校验结果、分析/发布/回滚状态流转以及操作人记录。';
COMMENT ON COLUMN "config_import_package"."package_no" IS '配置包编号。';
COMMENT ON COLUMN "config_import_package"."source_environment" IS '来源环境。';
COMMENT ON COLUMN "config_import_package"."migration_tag" IS '迁移标记。';
COMMENT ON COLUMN "config_import_package"."file_name" IS '文件名称。';
COMMENT ON COLUMN "config_import_package"."checksum" IS '校验和：内容校验和，用于检测迁移或配置包内容变化；不同表算法以所属服务为准。';
COMMENT ON COLUMN "config_import_package"."status" IS '状态。';
COMMENT ON COLUMN "config_import_package"."validation_report_json" IS '校验报告JSON。';
COMMENT ON COLUMN "config_import_package"."package_data" IS '配置包数据。';
COMMENT ON COLUMN "config_import_package"."imported_by" IS '导入人员。';
COMMENT ON COLUMN "config_import_package"."imported_at" IS '导入时间。';
COMMENT ON COLUMN "config_import_package"."published_by" IS '发布人。';
COMMENT ON COLUMN "config_import_package"."published_at" IS '发布时间。';
COMMENT ON COLUMN "config_import_package"."error_message" IS '错误消息。';
COMMENT ON COLUMN "config_import_package"."deleted" IS '逻辑删除标记。';
COMMENT ON COLUMN "config_import_package"."signature_status" IS '来源验签状态：导入时记录来源校验结论：VERIFIED 表示签名通过，MISMATCH_CONFIRMED 表示签名不一致但已人工确认来源，UNKNOWN 表示历史记录没有验签证据。与分析、发布状态分别保存，后续查看导入批次时据此说明包的来源可信程度。';
COMMENT ON COLUMN "config_import_package"."signature_confirmed_by" IS '来源确认人：签名不一致时，由服务端记录确认来源的导入账号；用于追溯谁接受了该配置包。签名通过的正常导入不填写。';
COMMENT ON COLUMN "config_import_package"."signature_confirmed_at" IS '来源确认时间：与来源确认人同时记录，保留人工确认的时间；后续重新分析配置差异不会覆盖这条确认记录。';
COMMENT ON TABLE "config_migration_asset" IS '配置迁移发布资产表';
COMMENT ON COLUMN "config_migration_asset"."id" IS '主键ID：配置迁移资产。 记录实体/流程配置在每次发布时生成的可迁移快照版本，包含快照内容、内容哈希、 依赖清单、快照完整度(PARTIAL/COMPLETE)以及导出标记与统计，是配置迁移的核心实体。';
COMMENT ON COLUMN "config_migration_asset"."asset_type" IS '资产类型。';
COMMENT ON COLUMN "config_migration_asset"."business_key" IS '业务键。';
COMMENT ON COLUMN "config_migration_asset"."asset_name" IS '资产名称。';
COMMENT ON COLUMN "config_migration_asset"."source_history_id" IS '来源历史ID。';
COMMENT ON COLUMN "config_migration_asset"."source_version" IS '来源版本。';
COMMENT ON COLUMN "config_migration_asset"."version_description" IS '版本说明。';
COMMENT ON COLUMN "config_migration_asset"."migration_tag" IS '迁移标记。';
COMMENT ON COLUMN "config_migration_asset"."mark_for_export" IS '标记用于导出。';
COMMENT ON COLUMN "config_migration_asset"."snapshot_completeness" IS '快照完整性。';
COMMENT ON COLUMN "config_migration_asset"."snapshot_schema_version" IS '快照结构版本。';
COMMENT ON COLUMN "config_migration_asset"."snapshot_json" IS '快照JSON。';
COMMENT ON COLUMN "config_migration_asset"."content_hash" IS '内容哈希：内容摘要，用于检查配置或快照内容是否一致。';
COMMENT ON COLUMN "config_migration_asset"."dependencies_json" IS '依赖集合JSON。';
COMMENT ON COLUMN "config_migration_asset"."dependency_count" IS '依赖数量。';
COMMENT ON COLUMN "config_migration_asset"."missing_dependency_count" IS '缺失依赖数量。';
COMMENT ON COLUMN "config_migration_asset"."export_status" IS '导出状态。';
COMMENT ON COLUMN "config_migration_asset"."published_at" IS '发布时间。';
COMMENT ON COLUMN "config_migration_asset"."published_by" IS '发布人。';
COMMENT ON COLUMN "config_migration_asset"."last_export_at" IS '最近导出时间。';
COMMENT ON COLUMN "config_migration_asset"."export_count" IS '导出数量。';
COMMENT ON COLUMN "config_migration_asset"."create_time" IS '创建时间。';
COMMENT ON COLUMN "config_migration_asset"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "config_migration_asset"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "config_migration_asset_dependency" IS '配置迁移资产依赖表';
COMMENT ON COLUMN "config_migration_asset_dependency"."id" IS '主键ID：配置迁移资产依赖。 记录某个迁移资产在导入/导出时依赖的其他资产或资源(实体、流程、表单、用户、字典等)， 用于依赖解析、阻断项分析与导出包完整性校验。';
COMMENT ON COLUMN "config_migration_asset_dependency"."asset_id" IS '资产ID。';
COMMENT ON COLUMN "config_migration_asset_dependency"."dependency_type" IS '依赖类型。';
COMMENT ON COLUMN "config_migration_asset_dependency"."dependency_key" IS '依赖键。';
COMMENT ON COLUMN "config_migration_asset_dependency"."required" IS '是否必需。';
COMMENT ON COLUMN "config_migration_asset_dependency"."source_description" IS '来源说明。';
COMMENT ON COLUMN "config_migration_asset_dependency"."dependency_document" IS '依赖扩展JSON文档。';
COMMENT ON COLUMN "config_migration_asset_dependency"."create_time" IS '创建时间。';
COMMENT ON COLUMN "config_migration_asset_dependency"."source_asset_type" IS '来源资产类型冗余。';
COMMENT ON COLUMN "config_migration_asset_dependency"."source_business_key" IS '来源资产稳定标识冗余。';
COMMENT ON COLUMN "config_migration_asset_dependency"."source_version" IS '来源发布版本。';
COMMENT ON COLUMN "config_migration_asset_dependency"."reference_location" IS '引用在配置中的稳定位置。';
COMMENT ON COLUMN "config_migration_asset_dependency"."dependency_strength" IS '依赖强度。';
COMMENT ON COLUMN "config_migration_asset_dependency"."parse_status" IS '解析状态。';
COMMENT ON COLUMN "config_migration_asset_dependency"."extracted_at" IS '引用抽取时间。';
COMMENT ON TABLE "embed_allowed_origin" IS '嵌入父页面来源表';
COMMENT ON COLUMN "embed_allowed_origin"."grant_id" IS '授权ID。';
COMMENT ON COLUMN "embed_allowed_origin"."origin" IS '来源。';
COMMENT ON COLUMN "embed_allowed_origin"."create_time" IS '创建时间。';
COMMENT ON TABLE "embed_application_grant" IS '嵌入视图应用授权表';
COMMENT ON COLUMN "embed_application_grant"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_application_grant"."application_id" IS '应用ID。';
COMMENT ON COLUMN "embed_application_grant"."view_id" IS '视图ID。';
COMMENT ON COLUMN "embed_application_grant"."identity_provider_id" IS '身份提供方ID。';
COMMENT ON COLUMN "embed_application_grant"."status" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "embed_application_grant"."trusted_subject_assertion" IS '是否信任人员断言；CHECK 枚举：0,1。';
COMMENT ON COLUMN "embed_application_grant"."revision_mode" IS '修订号模式。';
COMMENT ON COLUMN "embed_application_grant"."pinned_revision" IS '固定修订号。';
COMMENT ON COLUMN "embed_application_grant"."capability_ceiling_json" IS '能力上限JSON。';
COMMENT ON COLUMN "embed_application_grant"."max_active_sessions_per_user" IS '每用户最大活跃会话数。';
COMMENT ON COLUMN "embed_application_grant"."max_session_seconds" IS '会话最长秒数。';
COMMENT ON COLUMN "embed_application_grant"."launch_limit_per_minute" IS '每分钟启动上限。';
COMMENT ON COLUMN "embed_application_grant"."runtime_limit_per_minute" IS '每分钟运行请求上限。';
COMMENT ON COLUMN "embed_application_grant"."max_concurrency" IS '最大并发数。';
COMMENT ON COLUMN "embed_application_grant"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "embed_application_grant"."lock_version" IS '锁版本。';
COMMENT ON COLUMN "embed_application_grant"."security_version" IS '安全版本。';
COMMENT ON COLUMN "embed_application_grant"."create_by" IS '创建人。';
COMMENT ON COLUMN "embed_application_grant"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_application_grant"."update_by" IS '修改人。';
COMMENT ON COLUMN "embed_application_grant"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "embed_application_grant"."revoked_by" IS '撤销人。';
COMMENT ON COLUMN "embed_application_grant"."revoked_at" IS '撤销时间。';
COMMENT ON TABLE "embed_assertion_replay" IS '人员断言防重放表';
COMMENT ON COLUMN "embed_assertion_replay"."provider_id" IS '提供方ID。';
COMMENT ON COLUMN "embed_assertion_replay"."jti_digest" IS 'jti摘要。';
COMMENT ON COLUMN "embed_assertion_replay"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "embed_assertion_replay"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_assertion_replay"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "embed_external_identity_binding" IS '外部主体用户绑定表';
COMMENT ON COLUMN "embed_external_identity_binding"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_external_identity_binding"."application_id" IS '应用ID。';
COMMENT ON COLUMN "embed_external_identity_binding"."identity_provider_id" IS '身份提供方ID。';
COMMENT ON COLUMN "embed_external_identity_binding"."subject_digest" IS '主体摘要。';
COMMENT ON COLUMN "embed_external_identity_binding"."subject_digest_key_version" IS '主体摘要键版本。';
COMMENT ON COLUMN "embed_external_identity_binding"."subject_hint" IS '主体提示。';
COMMENT ON COLUMN "embed_external_identity_binding"."flow_user_id" IS 'Flow用户ID。';
COMMENT ON COLUMN "embed_external_identity_binding"."status" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "embed_external_identity_binding"."binding_version" IS '绑定版本。';
COMMENT ON COLUMN "embed_external_identity_binding"."effective_at" IS '生效时间。';
COMMENT ON COLUMN "embed_external_identity_binding"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "embed_external_identity_binding"."create_by" IS '创建人。';
COMMENT ON COLUMN "embed_external_identity_binding"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_external_identity_binding"."update_by" IS '修改人。';
COMMENT ON COLUMN "embed_external_identity_binding"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "embed_external_identity_binding"."revoked_by" IS '撤销人。';
COMMENT ON COLUMN "embed_external_identity_binding"."revoked_at" IS '撤销时间。';
COMMENT ON TABLE "embed_identity_provider" IS '嵌入身份提供方表';
COMMENT ON COLUMN "embed_identity_provider"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_identity_provider"."name" IS '名称。';
COMMENT ON COLUMN "embed_identity_provider"."type" IS '类型；CHECK 枚举：\\''SIGNED_JWT\\'',\\''TRUSTED_EXTERNAL_ID\\''。';
COMMENT ON COLUMN "embed_identity_provider"."status" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "embed_identity_provider"."issuer" IS '发行者。';
COMMENT ON COLUMN "embed_identity_provider"."issuer_uniqueness_key" IS '发行者唯一性键；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "embed_identity_provider"."subject_namespace" IS '主体命名空间。';
COMMENT ON COLUMN "embed_identity_provider"."audiences_json" IS '受众集合JSON。';
COMMENT ON COLUMN "embed_identity_provider"."algorithms_json" IS '算法集合JSON。';
COMMENT ON COLUMN "embed_identity_provider"."jwks_mode" IS 'JWKS模式；CHECK 枚举：\\''STATIC_JWK_SET\\'',\\''REMOTE_JWKS\\''。';
COMMENT ON COLUMN "embed_identity_provider"."jwks_json" IS 'JWKSJSON。';
COMMENT ON COLUMN "embed_identity_provider"."jwks_url" IS 'JWKSURL。';
COMMENT ON COLUMN "embed_identity_provider"."clock_skew_seconds" IS '允许时钟偏差秒数。';
COMMENT ON COLUMN "embed_identity_provider"."max_assertion_lifetime_seconds" IS '断言最长有效秒数。';
COMMENT ON COLUMN "embed_identity_provider"."key_version" IS '键版本。';
COMMENT ON COLUMN "embed_identity_provider"."lock_version" IS '锁版本。';
COMMENT ON COLUMN "embed_identity_provider"."security_version" IS '安全版本。';
COMMENT ON COLUMN "embed_identity_provider"."create_by" IS '创建人。';
COMMENT ON COLUMN "embed_identity_provider"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_identity_provider"."update_by" IS '修改人。';
COMMENT ON COLUMN "embed_identity_provider"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "embed_identity_provider"."revoked_by" IS '撤销人。';
COMMENT ON COLUMN "embed_identity_provider"."revoked_at" IS '撤销时间。';
COMMENT ON TABLE "embed_launch" IS '嵌入一次性启动凭据表';
COMMENT ON COLUMN "embed_launch"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_launch"."application_id" IS '应用ID。';
COMMENT ON COLUMN "embed_launch"."grant_id" IS '授权ID。';
COMMENT ON COLUMN "embed_launch"."view_id" IS '视图ID。';
COMMENT ON COLUMN "embed_launch"."view_release_id" IS '视图发布ID。';
COMMENT ON COLUMN "embed_launch"."identity_provider_id" IS '身份提供方ID。';
COMMENT ON COLUMN "embed_launch"."provider_security_version" IS '提供方安全版本。';
COMMENT ON COLUMN "embed_launch"."application_version" IS '应用版本。';
COMMENT ON COLUMN "embed_launch"."grant_security_version" IS '授权安全版本。';
COMMENT ON COLUMN "embed_launch"."view_security_version" IS '视图安全版本。';
COMMENT ON COLUMN "embed_launch"."flow_user_id" IS 'Flow用户ID。';
COMMENT ON COLUMN "embed_launch"."identity_binding_id" IS '身份绑定ID。';
COMMENT ON COLUMN "embed_launch"."binding_version" IS '绑定版本。';
COMMENT ON COLUMN "embed_launch"."subject_digest" IS '主体摘要。';
COMMENT ON COLUMN "embed_launch"."subject_digest_key_version" IS '主体摘要键版本。';
COMMENT ON COLUMN "embed_launch"."parent_origin" IS '父级来源。';
COMMENT ON COLUMN "embed_launch"."channel_id" IS '通道ID。';
COMMENT ON COLUMN "embed_launch"."entry_mode" IS '入口模式；CHECK 枚举：\\''LIST\\'',\\''CREATE\\'' / \\''VIEW\\'',\\''EDIT\\''。';
COMMENT ON COLUMN "embed_launch"."record_id" IS '记录ID。';
COMMENT ON COLUMN "embed_launch"."context_ciphertext" IS '上下文密文。';
COMMENT ON COLUMN "embed_launch"."context_cipher_key_version" IS '上下文加密键版本。';
COMMENT ON COLUMN "embed_launch"."context_digest" IS '上下文摘要。';
COMMENT ON COLUMN "embed_launch"."context_digest_key_version" IS '上下文摘要键版本。';
COMMENT ON COLUMN "embed_launch"."ui_locale" IS '界面语言区域。';
COMMENT ON COLUMN "embed_launch"."ui_theme" IS '界面主题；CHECK 枚举：\\''light\\'',\\''dark\\'',\\''system\\''。';
COMMENT ON COLUMN "embed_launch"."ui_form_presentation" IS '表单展示方式：seamless 或 dialog，默认 seamless；CHECK 枚举：\\''seamless\\'',\\''dialog\\''；迁移声明排序规则 utf8mb4_bin，标准迁移器完成后再统一为 utf8mb4_unicode_ci。';
COMMENT ON COLUMN "embed_launch"."launch_code_digest" IS '启动编码摘要。';
COMMENT ON COLUMN "embed_launch"."status" IS '状态；CHECK 枚举：\\''ISSUED\\'',\\''CONSUMED\\'',\\''EXPIRED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "embed_launch"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "embed_launch"."consumed_at" IS '消费时间。';
COMMENT ON COLUMN "embed_launch"."consumed_session_id" IS '消费会话ID。';
COMMENT ON COLUMN "embed_launch"."revoked_at" IS '撤销时间。';
COMMENT ON COLUMN "embed_launch"."trace_id" IS '追踪ID。';
COMMENT ON COLUMN "embed_launch"."request_id" IS '请求ID。';
COMMENT ON COLUMN "embed_launch"."source_ip_digest" IS '来源IP摘要。';
COMMENT ON COLUMN "embed_launch"."source_ip_digest_key_version" IS '来源IP摘要键版本。';
COMMENT ON COLUMN "embed_launch"."user_agent_digest" IS 'User-Agent摘要。';
COMMENT ON COLUMN "embed_launch"."user_agent_digest_key_version" IS '用户代理信息摘要键版本。';
COMMENT ON COLUMN "embed_launch"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_launch"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "embed_operation_receipt" IS '嵌入写操作回执表';
COMMENT ON COLUMN "embed_operation_receipt"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_operation_receipt"."idempotency_record_id" IS '幂等记录ID。';
COMMENT ON COLUMN "embed_operation_receipt"."application_id" IS '应用ID。';
COMMENT ON COLUMN "embed_operation_receipt"."operation" IS '操作；CHECK 枚举： \\''EMBED_RECORD_CREATE\\'',\\''EMBED_RECORD_UPDATE\\'',\\''EMBED_ACTION_EXECUTE\\''。';
COMMENT ON COLUMN "embed_operation_receipt"."actor_scope_digest" IS '操作主体范围摘要。';
COMMENT ON COLUMN "embed_operation_receipt"."view_key" IS '视图键。';
COMMENT ON COLUMN "embed_operation_receipt"."target_type" IS '目标类型。';
COMMENT ON COLUMN "embed_operation_receipt"."target_id" IS '目标ID。';
COMMENT ON COLUMN "embed_operation_receipt"."outcome_code" IS '结果编码。';
COMMENT ON COLUMN "embed_operation_receipt"."record_version" IS '记录版本。';
COMMENT ON COLUMN "embed_operation_receipt"."result_summary_json" IS '结果摘要JSON。';
COMMENT ON COLUMN "embed_operation_receipt"."create_time" IS '创建时间。';
COMMENT ON TABLE "embed_session" IS '嵌入运行会话表';
COMMENT ON COLUMN "embed_session"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_session"."session_token_digest" IS '会话令牌摘要。';
COMMENT ON COLUMN "embed_session"."launch_id" IS '启动ID。';
COMMENT ON COLUMN "embed_session"."application_id" IS '应用ID。';
COMMENT ON COLUMN "embed_session"."grant_id" IS '授权ID。';
COMMENT ON COLUMN "embed_session"."view_id" IS '视图ID。';
COMMENT ON COLUMN "embed_session"."view_release_id" IS '视图发布ID。';
COMMENT ON COLUMN "embed_session"."identity_provider_id" IS '身份提供方ID。';
COMMENT ON COLUMN "embed_session"."provider_security_version" IS '提供方安全版本。';
COMMENT ON COLUMN "embed_session"."flow_user_id" IS 'Flow用户ID。';
COMMENT ON COLUMN "embed_session"."identity_binding_id" IS '身份绑定ID。';
COMMENT ON COLUMN "embed_session"."binding_version" IS '绑定版本。';
COMMENT ON COLUMN "embed_session"."parent_origin" IS '父级来源。';
COMMENT ON COLUMN "embed_session"."channel_id" IS '通道ID。';
COMMENT ON COLUMN "embed_session"."entry_mode" IS '入口模式；CHECK 枚举：\\''LIST\\'',\\''CREATE\\'' / \\''VIEW\\'',\\''EDIT\\''。';
COMMENT ON COLUMN "embed_session"."record_id" IS '记录ID。';
COMMENT ON COLUMN "embed_session"."parent_nonce_digest" IS '父级随机数摘要。';
COMMENT ON COLUMN "embed_session"."child_nonce_digest" IS '子级随机数摘要。';
COMMENT ON COLUMN "embed_session"."context_ciphertext" IS '上下文密文。';
COMMENT ON COLUMN "embed_session"."context_cipher_key_version" IS '上下文加密键版本。';
COMMENT ON COLUMN "embed_session"."context_digest" IS '上下文摘要。';
COMMENT ON COLUMN "embed_session"."context_digest_key_version" IS '上下文摘要键版本。';
COMMENT ON COLUMN "embed_session"."ui_locale" IS '界面语言区域。';
COMMENT ON COLUMN "embed_session"."ui_theme" IS '界面主题；CHECK 枚举：\\''light\\'',\\''dark\\'',\\''system\\''。';
COMMENT ON COLUMN "embed_session"."ui_form_presentation" IS '表单展示方式：seamless 或 dialog，默认 seamless；CHECK 枚举：\\''seamless\\'',\\''dialog\\''；迁移声明排序规则 utf8mb4_bin，标准迁移器完成后再统一为 utf8mb4_unicode_ci。';
COMMENT ON COLUMN "embed_session"."capability_snapshot_json" IS '能力快照JSON。';
COMMENT ON COLUMN "embed_session"."application_version" IS '应用版本。';
COMMENT ON COLUMN "embed_session"."grant_security_version" IS '授权安全版本。';
COMMENT ON COLUMN "embed_session"."view_security_version" IS '视图安全版本。';
COMMENT ON COLUMN "embed_session"."status" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''LOGGED_OUT\\'',\\''EXPIRED\\'',\\''REVOKED\\'' / \\''LOGGED_OUT\\'',\\''EXPIRED\\''。';
COMMENT ON COLUMN "embed_session"."slot_released" IS '会话名额是否已释放。';
COMMENT ON COLUMN "embed_session"."slot_released_at" IS '会话名额释放时间。';
COMMENT ON COLUMN "embed_session"."issued_at" IS '签发时间。';
COMMENT ON COLUMN "embed_session"."last_seen_at" IS '最近活动时间。';
COMMENT ON COLUMN "embed_session"."idle_expires_at" IS '空闲过期时间。';
COMMENT ON COLUMN "embed_session"."absolute_expires_at" IS '绝对过期时间。';
COMMENT ON COLUMN "embed_session"."revoked_at" IS '撤销时间。';
COMMENT ON COLUMN "embed_session"."revoke_reason" IS '撤销原因。';
COMMENT ON COLUMN "embed_session"."source_ip_digest" IS '来源IP摘要。';
COMMENT ON COLUMN "embed_session"."source_ip_digest_key_version" IS '来源IP摘要键版本。';
COMMENT ON COLUMN "embed_session"."user_agent_digest" IS 'User-Agent摘要。';
COMMENT ON COLUMN "embed_session"."user_agent_digest_key_version" IS '用户代理信息摘要键版本。';
COMMENT ON COLUMN "embed_session"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_session"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "embed_session_counter" IS '嵌入活跃会话计数表';
COMMENT ON COLUMN "embed_session_counter"."grant_id" IS '授权ID。';
COMMENT ON COLUMN "embed_session_counter"."flow_user_id" IS 'Flow用户ID。';
COMMENT ON COLUMN "embed_session_counter"."active_count" IS '活跃数量。';
COMMENT ON COLUMN "embed_session_counter"."lock_version" IS '锁版本。';
COMMENT ON COLUMN "embed_session_counter"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_session_counter"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "embed_view" IS '嵌入视图草稿表';
COMMENT ON COLUMN "embed_view"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_view"."view_key" IS '视图键。';
COMMENT ON COLUMN "embed_view"."name" IS '名称。';
COMMENT ON COLUMN "embed_view"."description" IS '说明。';
COMMENT ON COLUMN "embed_view"."surface_type" IS '界面类型；CHECK 枚举：\\''LIST\\'',\\''FORM\\''。';
COMMENT ON COLUMN "embed_view"."status" IS '状态；CHECK 枚举：\\''DRAFT\\'',\\''ACTIVE\\'',\\''DISABLED\\'',\\''RETIRED\\''。';
COMMENT ON COLUMN "embed_view"."draft_config_json" IS '草稿配置JSON。';
COMMENT ON COLUMN "embed_view"."draft_revision" IS '草稿修订号。';
COMMENT ON COLUMN "embed_view"."published_release_id" IS '发布发布ID。';
COMMENT ON COLUMN "embed_view"."lock_version" IS '锁版本。';
COMMENT ON COLUMN "embed_view"."security_version" IS '安全版本。';
COMMENT ON COLUMN "embed_view"."create_by" IS '创建人。';
COMMENT ON COLUMN "embed_view"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_view"."update_by" IS '修改人。';
COMMENT ON COLUMN "embed_view"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "embed_view_release" IS '嵌入视图发布快照表';
COMMENT ON COLUMN "embed_view_release"."id" IS '主键ID。';
COMMENT ON COLUMN "embed_view_release"."view_id" IS '视图ID。';
COMMENT ON COLUMN "embed_view_release"."revision" IS '修订号。';
COMMENT ON COLUMN "embed_view_release"."surface_type" IS '界面类型；CHECK 枚举：\\''LIST\\'',\\''FORM\\''。';
COMMENT ON COLUMN "embed_view_release"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "embed_view_release"."list_key" IS '列表键。';
COMMENT ON COLUMN "embed_view_release"."default_form_id" IS '默认表单ID。';
COMMENT ON COLUMN "embed_view_release"."list_release_id" IS '列表发布ID。';
COMMENT ON COLUMN "embed_view_release"."list_release_version" IS '列表发布版本。';
COMMENT ON COLUMN "embed_view_release"."form_release_id" IS '表单发布ID。';
COMMENT ON COLUMN "embed_view_release"."form_release_version" IS '表单发布版本。';
COMMENT ON COLUMN "embed_view_release"."entry_modes_json" IS '入口模式JSON。';
COMMENT ON COLUMN "embed_view_release"."capabilities_json" IS '能力集合JSON。';
COMMENT ON COLUMN "embed_view_release"."field_policy_json" IS '字段策略JSON。';
COMMENT ON COLUMN "embed_view_release"."action_policy_json" IS '动作策略JSON。';
COMMENT ON COLUMN "embed_view_release"."context_schema_json" IS '上下文结构JSON。';
COMMENT ON COLUMN "embed_view_release"."context_bindings_json" IS '上下文绑定集合JSON。';
COMMENT ON COLUMN "embed_view_release"."ui_config_json" IS '界面配置JSON。';
COMMENT ON COLUMN "embed_view_release"."config_json" IS '配置JSON。';
COMMENT ON COLUMN "embed_view_release"."config_hash" IS '配置哈希。';
COMMENT ON COLUMN "embed_view_release"."release_note" IS '发布说明。';
COMMENT ON COLUMN "embed_view_release"."published_by" IS '发布人。';
COMMENT ON COLUMN "embed_view_release"."published_at" IS '发布时间。';
COMMENT ON COLUMN "embed_view_release"."create_time" IS '创建时间。';
COMMENT ON COLUMN "embed_view_release"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "entity_code_rule" IS '实体业务编码规则表';
COMMENT ON COLUMN "entity_code_rule"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_code_rule"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_code_rule"."prefix" IS '编码前缀，如：CG、DD。';
COMMENT ON COLUMN "entity_code_rule"."date_format" IS '日期格式，如：yyyyMMdd、yyyy-MM-dd。';
COMMENT ON COLUMN "entity_code_rule"."seq_length" IS '序号长度：序列号位数，如：6表示000001。';
COMMENT ON COLUMN "entity_code_rule"."seq_type" IS '序号重置类型：序列号重置周期：DAY按天、MONTH按月、YEAR按年、NEVER不重置。';
COMMENT ON COLUMN "entity_code_rule"."current_seq" IS '当前序号：当前序列号值。';
COMMENT ON COLUMN "entity_code_rule"."seq_date" IS '序号所属日期：当前序列号对应的日期（用于判断重置）。';
COMMENT ON COLUMN "entity_code_rule"."example" IS '编码示例。';
COMMENT ON COLUMN "entity_code_rule"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_code_rule"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_code_rule"."generation_mode" IS '编码生成模式：RULE 使用内置规则，CUSTOM 调用登记的生成器；运行时按此选择策略';
COMMENT ON COLUMN "entity_code_rule"."generator_code" IS '自定义生成器编码，CUSTOM 模式下用于定位扩展实现';
COMMENT ON COLUMN "entity_code_rule"."generator_config" IS '自定义生成器参数文档，调用扩展实现时作为输入并由应用校验';
COMMENT ON TABLE "entity_definition" IS '实体定义表';
COMMENT ON COLUMN "entity_definition"."id" IS '主键ID，供实体定义表中的记录关联；由数据库分配';
COMMENT ON COLUMN "entity_definition"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_definition"."entity_name" IS '实体名称。';
COMMENT ON COLUMN "entity_definition"."description" IS '说明：实体描述。';
COMMENT ON COLUMN "entity_definition"."process_definition_id" IS '关联流程定义ID。';
COMMENT ON COLUMN "entity_definition"."status" IS '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用。';
COMMENT ON COLUMN "entity_definition"."created_by" IS '创建人。';
COMMENT ON COLUMN "entity_definition"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_definition"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_definition"."table_name" IS '数据库表名。';
COMMENT ON COLUMN "entity_definition"."lifecycle_mode" IS '实体生命周期模式：STANDALONE/WORKFLOW。';
COMMENT ON COLUMN "entity_definition"."storage_mode" IS '存储模式：DYNAMIC/SYSTEM。';
COMMENT ON COLUMN "entity_definition"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "entity_definition"."updated_by" IS '修改人：更新人。';
COMMENT ON COLUMN "entity_definition"."team_visibility_enabled" IS '团队可见性开关：是否允许数据参与团队查看记录；团队可见性的旧开关不再参与权限引擎计算；当前改用列表 TEAM 规则，字段仍被保存和发布快照复制。';
COMMENT ON COLUMN "entity_definition"."team_visibility_level" IS '团队可见性级别：参与团队权限级别：ADDITIVE/OVERRIDE_SCOPE/ABSOLUTE；团队可见性的旧开关不再参与权限引擎计算；当前改用列表 TEAM 规则，字段仍被保存和发布快照复制。';
COMMENT ON COLUMN "entity_definition"."active_process_definition_key" IS '有效流程绑定索引键；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "entity_field" IS '实体字段定义表';
COMMENT ON COLUMN "entity_field"."id" IS '主键ID，供实体字段定义表中的记录关联；由数据库分配';
COMMENT ON COLUMN "entity_field"."entity_id" IS '所属实体ID。';
COMMENT ON COLUMN "entity_field"."field_code" IS '字段编码。';
COMMENT ON COLUMN "entity_field"."field_name" IS '字段名称。';
COMMENT ON COLUMN "entity_field"."field_type" IS '字段类型。';
COMMENT ON COLUMN "entity_field"."db_type" IS '数据库字段类型。';
COMMENT ON COLUMN "entity_field"."field_length" IS '字段长度。';
COMMENT ON COLUMN "entity_field"."is_required" IS '是否必填。';
COMMENT ON COLUMN "entity_field"."is_unique" IS '是否唯一。';
COMMENT ON COLUMN "entity_field"."default_value" IS '默认值。';
COMMENT ON COLUMN "entity_field"."options_json" IS '选项配置JSON。';
COMMENT ON COLUMN "entity_field"."validate_rules" IS '验证规则JSON。';
COMMENT ON COLUMN "entity_field"."sort_order" IS '排序号：排序顺序。';
COMMENT ON COLUMN "entity_field"."is_system" IS '是否系统字段：0-否 1-是（系统自动添加的字段，不可删除）。';
COMMENT ON COLUMN "entity_field"."is_published" IS '是否已发布到数据库表。';
COMMENT ON COLUMN "entity_field"."editable" IS '是否可编辑：0-否 1-是。';
COMMENT ON COLUMN "entity_field"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_field"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_field"."field_precision" IS '小数位数（精度）。';
COMMENT ON COLUMN "entity_field"."db_column_name" IS '数据库列名（下划线命名）。';
COMMENT ON COLUMN "entity_field"."file_types" IS '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）。';
COMMENT ON COLUMN "entity_field"."file_max_size" IS '文件大小限制（MB，用于附件类型）。';
COMMENT ON COLUMN "entity_field"."file_max_count" IS '文件数量限制（用于附件类型）。';
COMMENT ON COLUMN "entity_field"."ref_entity_type" IS '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）。';
COMMENT ON COLUMN "entity_field"."ref_entity_id" IS '关联实体ID。';
COMMENT ON COLUMN "entity_field"."ref_field_code" IS '关联字段编码。';
COMMENT ON COLUMN "entity_field"."ref_list_key" IS '子列表或实体引用默认使用的已发布列表编码。';
COMMENT ON COLUMN "entity_field"."field_id" IS '旧字段编码（兼容保留）；旧字段编码；迁移注释明确为兼容保留，不是该行主键。';
COMMENT ON COLUMN "entity_field"."dict_type" IS '绑定的系统代码表编码。';
COMMENT ON COLUMN "entity_field"."value_storage" IS '值存储方式：字段值存储：SCALAR/MULTI_TABLE。';
COMMENT ON COLUMN "entity_field"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "entity_field_file_item" IS '实体字段附件项表';
COMMENT ON COLUMN "entity_field_file_item"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_field_file_item"."field_id" IS '关联字段ID（entity_field.id）。';
COMMENT ON COLUMN "entity_field_file_item"."item_key" IS '附件项不可变业务标识。';
COMMENT ON COLUMN "entity_field_file_item"."item_name" IS '附件项名称。';
COMMENT ON COLUMN "entity_field_file_item"."name_aliases" IS '历史附件项名称 JSON 数组；用于历史附件项名称归并到稳定 item_key，仍有业务用途。';
COMMENT ON COLUMN "entity_field_file_item"."is_required" IS '是否必填：该附件项是否必填。';
COMMENT ON COLUMN "entity_field_file_item"."file_types" IS '允许的文件类型。';
COMMENT ON COLUMN "entity_field_file_item"."max_size" IS '单文件大小限制（MB）。';
COMMENT ON COLUMN "entity_field_file_item"."max_count" IS '文件数量限制。';
COMMENT ON COLUMN "entity_field_file_item"."sort_order" IS '排序号。';
COMMENT ON COLUMN "entity_field_file_item"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_field_file_item"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_field_option" IS '实体字段静态选项表';
COMMENT ON COLUMN "entity_field_option"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_field_option"."field_id" IS '所属字段ID。';
COMMENT ON COLUMN "entity_field_option"."option_value" IS '选项值（提交时存储的实际值）。';
COMMENT ON COLUMN "entity_field_option"."option_label" IS '选项标签（界面显示文案）。';
COMMENT ON COLUMN "entity_field_option"."style_type" IS '选项样式类型（如 primary/success/danger 等）。';
COMMENT ON COLUMN "entity_field_option"."disabled" IS '是否禁用（true-禁用不可选）。';
COMMENT ON COLUMN "entity_field_option"."sort_order" IS '排序号。';
COMMENT ON COLUMN "entity_field_option"."option_document" IS '选项扩展JSON文档。';
COMMENT ON COLUMN "entity_field_option"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_field_option"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_form" IS '实体表单定义表';
COMMENT ON COLUMN "entity_form"."id" IS '主键ID：表单ID。';
COMMENT ON COLUMN "entity_form"."entity_id" IS '实体ID。';
COMMENT ON COLUMN "entity_form"."form_name" IS '表单名称。';
COMMENT ON COLUMN "entity_form"."form_key" IS '表单标识。';
COMMENT ON COLUMN "entity_form"."description" IS '说明：描述。';
COMMENT ON COLUMN "entity_form"."layout_type" IS '布局类型：vertical-垂直 horizontal-水平 grid-网格。';
COMMENT ON COLUMN "entity_form"."status" IS '状态：0-禁用 1-启用。';
COMMENT ON COLUMN "entity_form"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_form"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_form"."deleted" IS '逻辑删除标记：删除标志。';
COMMENT ON COLUMN "entity_form"."is_default" IS '是否默认表单。';
COMMENT ON COLUMN "entity_form"."custom_component" IS '自定义表单组件注册名。';
COMMENT ON COLUMN "entity_form"."created_by" IS '创建人。';
COMMENT ON COLUMN "entity_form"."updated_by" IS '修改人：更新人。';
COMMENT ON COLUMN "entity_form"."view_config" IS '表单视图配置JSON：布局、自定义组件参数。';
COMMENT ON COLUMN "entity_form"."revision" IS '修订号：草稿元数据修订号。';
COMMENT ON COLUMN "entity_form"."active_release_id" IS '当前激活发布快照ID。';
COMMENT ON COLUMN "entity_form"."draft_hash" IS '当前草稿内容哈希。';
COMMENT ON COLUMN "entity_form"."custom_component_version" IS '自定义整页表单组件锁定版本。';
COMMENT ON COLUMN "entity_form"."custom_component_snapshot_version" IS '自定义整页表单配置快照版本。';
COMMENT ON COLUMN "entity_form"."data_source_bindings_document" IS '数据源绑定文档：表单级统一数据源绑定JSON文档。';
COMMENT ON TABLE "entity_form_node" IS '实体表单递归节点表';
COMMENT ON COLUMN "entity_form_node"."id" IS '主键ID：稳定节点ID。';
COMMENT ON COLUMN "entity_form_node"."form_id" IS '表单ID。';
COMMENT ON COLUMN "entity_form_node"."parent_id" IS '父节点ID。';
COMMENT ON COLUMN "entity_form_node"."node_key" IS '表单内稳定节点编码。';
COMMENT ON COLUMN "entity_form_node"."active_node_key" IS '仅活动节点参与表单内节点编码唯一约束；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "entity_form_node"."node_type" IS '节点类型。';
COMMENT ON COLUMN "entity_form_node"."binding_type" IS '绑定类型。';
COMMENT ON COLUMN "entity_form_node"."binding_ref" IS '字段、关系或上下文引用。';
COMMENT ON COLUMN "entity_form_node"."props_document" IS '节点显式属性JSON文档。';
COMMENT ON COLUMN "entity_form_node"."rules_document" IS '校验、显隐和权限规则JSON文档。';
COMMENT ON COLUMN "entity_form_node"."data_source_bindings_document" IS '数据源绑定文档：节点数据源绑定JSON文档。';
COMMENT ON COLUMN "entity_form_node"."legacy_props_document" IS '无法识别的历史属性JSON文档；仍保存历史或当前节点类型不适用的属性，并参与恢复；不是已废弃的空列。';
COMMENT ON COLUMN "entity_form_node"."order_key" IS '稀疏排序键。';
COMMENT ON COLUMN "entity_form_node"."revision" IS '修订号：节点草稿修订号。';
COMMENT ON COLUMN "entity_form_node"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_form_node"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_form_node"."deleted" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON COLUMN "entity_form_node"."component_name" IS '节点扩展组件注册名。';
COMMENT ON COLUMN "entity_form_node"."component_version" IS '节点扩展组件锁定版本。';
COMMENT ON COLUMN "entity_form_node"."snapshot_version" IS '节点扩展配置快照版本。';
COMMENT ON TABLE "entity_form_unique_claim" IS '表单唯一值原子占位表';
COMMENT ON COLUMN "entity_form_unique_claim"."constraint_key" IS '唯一约束命名空间，格式 FORM:{formId}:{snapshotIdentity}:{ruleId}。';
COMMENT ON COLUMN "entity_form_unique_claim"."value_hash" IS '规范化值的 SHA-256。';
COMMENT ON COLUMN "entity_form_unique_claim"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_form_unique_claim"."form_id" IS '规则所属表单ID。';
COMMENT ON COLUMN "entity_form_unique_claim"."rule_id" IS '跨发布版本稳定的规则ID。';
COMMENT ON COLUMN "entity_form_unique_claim"."field_code" IS '规则字段编码。';
COMMENT ON COLUMN "entity_form_unique_claim"."normalized_value" IS '截断后的规范化值，仅用于排障。';
COMMENT ON COLUMN "entity_form_unique_claim"."record_id" IS '占位所属业务记录ID。';
COMMENT ON COLUMN "entity_form_unique_claim"."release_id" IS '最近一次维护占位的表单发布ID，仅用于审计。';
COMMENT ON COLUMN "entity_form_unique_claim"."release_version" IS '最近一次维护占位的表单发布版本，仅用于审计。';
COMMENT ON COLUMN "entity_form_unique_claim"."effective_release_id" IS '规则实际来源发布ID；热修复时为热修复发布ID。';
COMMENT ON COLUMN "entity_form_unique_claim"."effective_content_hash" IS '规则实际有效快照哈希，仅用于审计和完整性追踪。';
COMMENT ON COLUMN "entity_form_unique_claim"."hotfix_target_id" IS '热修复目标ID；非热修复为空。';
COMMENT ON COLUMN "entity_form_unique_claim"."created_time" IS '创建时间。';
COMMENT ON COLUMN "entity_form_unique_claim"."updated_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_form_unique_value_gate" IS '表单唯一值事务门闩表';
COMMENT ON COLUMN "entity_form_unique_value_gate"."scope_key" IS '稳定值锁作用域，格式 ENTITY:{entityCode}:{fieldCode}。';
COMMENT ON COLUMN "entity_form_unique_value_gate"."value_hash" IS '规范化值的 SHA-256。';
COMMENT ON COLUMN "entity_form_unique_value_gate"."created_time" IS '创建时间。';
COMMENT ON COLUMN "entity_form_unique_value_gate"."updated_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_list_action" IS '实体列表按钮配置表';
COMMENT ON COLUMN "entity_list_action"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_action"."list_config_id" IS '列表配置ID。';
COMMENT ON COLUMN "entity_list_action"."position" IS '职务。';
COMMENT ON COLUMN "entity_list_action"."button_key" IS '稳定按钮编码。';
COMMENT ON COLUMN "entity_list_action"."button_type" IS '按钮类型。';
COMMENT ON COLUMN "entity_list_action"."button_label" IS '按钮名称。';
COMMENT ON COLUMN "entity_list_action"."icon" IS '图标。';
COMMENT ON COLUMN "entity_list_action"."style_type" IS '按钮样式。';
COMMENT ON COLUMN "entity_list_action"."link_mode" IS '是否链接按钮。';
COMMENT ON COLUMN "entity_list_action"."custom_mode" IS '自定义模式。';
COMMENT ON COLUMN "entity_list_action"."handler_code" IS '处理器或组件编码。';
COMMENT ON COLUMN "entity_list_action"."permission_code" IS '功能权限码。';
COMMENT ON COLUMN "entity_list_action"."sort_order" IS '排序号。';
COMMENT ON COLUMN "entity_list_action"."enabled" IS '是否启用。';
COMMENT ON COLUMN "entity_list_action"."unavailable_behavior" IS '历史不可用行为：历史字段；v2 显示/启用规则不再读取，待数据库清理时移除。';
COMMENT ON COLUMN "entity_list_action"."action_params_document" IS '按钮扩展参数JSON文档。';
COMMENT ON COLUMN "entity_list_action"."availability_rule_document" IS '按钮适用条件JSON文档。';
COMMENT ON COLUMN "entity_list_action"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_list_action"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_list_action"."deleted" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON COLUMN "entity_list_action"."revision" IS '修订号：按钮草稿修订号。';
COMMENT ON COLUMN "entity_list_action"."order_key" IS '稀疏排序键。';
COMMENT ON COLUMN "entity_list_action"."template_id" IS '来源模板ID。';
COMMENT ON COLUMN "entity_list_action"."template_version" IS '锁定模板版本。';
COMMENT ON COLUMN "entity_list_action"."local_overrides_document" IS '模板实例本地覆盖JSON文档。';
COMMENT ON TABLE "entity_list_config" IS '实体列表配置表';
COMMENT ON COLUMN "entity_list_config"."id" IS '列表配置 ID：主键。普通创建由 MyBatis-Plus `ASSIGN_UUID` 生成，不是数据库自增 ID';
COMMENT ON COLUMN "entity_list_config"."entity_id" IS '所属实体 ID：逻辑关联 `entity_definition.id`。注意目标主键是 `bigint`，本表以字符串保存';
COMMENT ON COLUMN "entity_list_config"."entity_code" IS '所属实体编码：冗余保存实体编码，服务于按编码定位列表及运行时权限解析；业务上应与 `entity_id` 指向同一实体';
COMMENT ON COLUMN "entity_list_config"."list_key" IS '列表标识：实体内的稳定编码，如 `default`、`picker`。保存校验格式为 `[A-Za-z][A-Za-z0-9_-]{0,99}`';
COMMENT ON COLUMN "entity_list_config"."list_name" IS '列表名称：面向管理员与使用者的显示名称，可修改。数据库非空约束不等于禁止空字符串';
COMMENT ON COLUMN "entity_list_config"."description" IS '列表说明：说明列表用途与适用范围';
COMMENT ON COLUMN "entity_list_config"."is_default" IS '是否默认列表：`1` 为默认，`0` 为非默认。未指定列表标识时优先选择默认列表；当前未强制一个实体只能有一个默认列表';
COMMENT ON COLUMN "entity_list_config"."deleted" IS '逻辑删除标记：`0` 为正常，`1` 为已删除；MyBatis-Plus 使用 `@TableLogic`，常规查询筛选 `deleted = 0`';
COMMENT ON COLUMN "entity_list_config"."create_time" IS '创建时间：数据库提供插入默认时间，普通保存流程也会设置创建时间';
COMMENT ON COLUMN "entity_list_config"."update_time" IS '更新时间：含 `ON UPDATE CURRENT_TIMESTAMP`，应用更新时也会设置；不能当作发布时间或修订号';
COMMENT ON COLUMN "entity_list_config"."custom_component" IS '自定义列表组件：前端已注册的整页列表组件名；为空时使用平台动态列表。组件名是注册标识，不是可执行脚本';
COMMENT ON COLUMN "entity_list_config"."toolbar_config" IS '工具栏按钮配置：JSON 数组，保存工具栏按钮配置；与按钮关系表的读取优先级见 2.4.2';
COMMENT ON COLUMN "entity_list_config"."row_action_config" IS '行内操作配置：JSON 数组，保存每行操作按钮配置；与按钮关系表的读取优先级见 2.4.2';
COMMENT ON COLUMN "entity_list_config"."view_config" IS '列表视图配置：JSON 对象，保存查询区、表格、分页与自定义组件参数；具体列定义在字段子表';
COMMENT ON COLUMN "entity_list_config"."data_scope_mode" IS '数据范围模式：允许 `INHERIT`、`NARROW`、`OVERRIDE`；保留的模式标识。当前执行语义的限制见 2.4.4，不能仅凭字段名认定存在三套范围合并算法';
COMMENT ON COLUMN "entity_list_config"."unbound_scope_policy" IS '未绑定允许规则时的策略：没有已启用且在有效期内的 ALLOW 绑定时，采用拒绝全部、本人数据或显式全量可见的默认范围';
COMMENT ON COLUMN "entity_list_config"."scope_enforcement_mode" IS '默认策略执行阶段：`OBSERVE` 为存量观察期；`ENFORCE` 为执行安全默认策略';
COMMENT ON COLUMN "entity_list_config"."scope_default_confirmed" IS '全量可见是否已确认：`1` 表示已显式确认；执行阶段下 `EXPLICIT_ALL` 需要该确认';
COMMENT ON COLUMN "entity_list_config"."scope_default_confirmed_by" IS '全量可见确认人：记录确认操作的当前用户 ID；不代表本条列表配置的创建人';
COMMENT ON COLUMN "entity_list_config"."scope_default_confirmed_at" IS '全量可见确认时间：记录显式确认发生的时间';
COMMENT ON COLUMN "entity_list_config"."scope_default_confirmation_note" IS '全量可见确认原因：记录放开数据范围的业务原因；首次确认时应用要求至少 5 个字符';
COMMENT ON COLUMN "entity_list_config"."access_permission_code" IS '列表访问权限码：控制进入列表的权限；空值回退为 `entity:{entity_code}:list`。访问权限与可见记录范围分别校验';
COMMENT ON COLUMN "entity_list_config"."allowed_scenes" IS '历史场景配置：功能已移除，应用不再读写';
COMMENT ON COLUMN "entity_list_config"."selection_config" IS '选数配置：JSON 对象，描述是否允许选数、主值字段及选中记录的返回映射';
COMMENT ON COLUMN "entity_list_config"."fixed_filter_config" IS '固定查询条件：JSON 对象。无论是否绑定数据范围规则均生效；与权限范围取交集，省略 `_op` 时按 `EQ`，用户筛选和自定义查询不能放宽条件';
COMMENT ON COLUMN "entity_list_config"."query_provider_code" IS '查询提供者编码：已注册 `EntityListDataProvider` 的编码，用于自定义列表查询；不能与接口扩展查询同时配置';
COMMENT ON COLUMN "entity_list_config"."published_version" IS '当前界面发布版本号：`0` 为尚未发布；发布后对应当前激活的 LIST 快照版本。不是草稿修订号，也不是数据权限发布版本';
COMMENT ON COLUMN "entity_list_config"."revision" IS '草稿修订号：列表编辑的并发控制版本。普通元数据更新校验 `expectedRevision`，成功后递增；字段、按钮变更也会触碰主表修订号';
COMMENT ON COLUMN "entity_list_config"."active_release_id" IS '当前激活快照 ID：逻辑关联 `ui_config_release.id`；对应快照必须属于本列表且为 LIST 类型。普通运行入口要求其与当前 ACTIVE 快照一致';
COMMENT ON COLUMN "entity_list_config"."draft_hash" IS '草稿内容哈希：保存规范化快照的 SHA-256 十六进制哈希。草稿编辑通常将其清空，发布时写入；需要比较完整草稿内容，不能只凭此字段判断有无变更';
COMMENT ON COLUMN "entity_list_config"."query_interface_extension_id" IS '查询接口扩展 ID：历史查询槽位，逻辑关联 `ui_extension_definition.id`；V094 将存量草稿迁入 `LIST_LOAD` 替代步骤后清空，保留字段以读取旧发布';
COMMENT ON TABLE "entity_list_field" IS '实体列表字段配置表';
COMMENT ON COLUMN "entity_list_field"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_field"."list_config_id" IS '所属列表配置ID。';
COMMENT ON COLUMN "entity_list_field"."field_id" IS '实体字段ID（关联entity_field）。';
COMMENT ON COLUMN "entity_list_field"."field_code" IS '字段编码。';
COMMENT ON COLUMN "entity_list_field"."field_name" IS '字段名称（快照）。';
COMMENT ON COLUMN "entity_list_field"."sort_order" IS '排序号：列排序号；当前主要按 order_key 排序，本列仍作为次级排序和兼容值。';
COMMENT ON COLUMN "entity_list_field"."width" IS '列宽度（0表示自适应）。';
COMMENT ON COLUMN "entity_list_field"."show_in_list" IS '是否显示在列表。';
COMMENT ON COLUMN "entity_list_field"."is_query" IS '是否作为查询条件。';
COMMENT ON COLUMN "entity_list_field"."query_type" IS '查询方式：EQ/NE/LIKE/GT/LT/BETWEEN/IN。';
COMMENT ON COLUMN "entity_list_field"."align" IS '对齐方式：left/center/right。';
COMMENT ON COLUMN "entity_list_field"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "entity_list_field"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_list_field"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_list_field"."data_source_type" IS '数据源类型：ENTITY_FIELD(实体字段)/REFERENCE(关联查询)/AGGREGATE(聚合统计)/CUSTOM_PROVIDER(自定义处理器)。';
COMMENT ON COLUMN "entity_list_field"."data_source_config" IS '数据源配置JSON。';
COMMENT ON COLUMN "entity_list_field"."render_component" IS '前端渲染组件名。';
COMMENT ON COLUMN "entity_list_field"."formatter" IS '简单格式化表达式（如 yyyy-MM-dd、#0.00）。';
COMMENT ON COLUMN "entity_list_field"."column_config" IS '列展示配置JSON。';
COMMENT ON COLUMN "entity_list_field"."query_config" IS '查询组件配置JSON。';
COMMENT ON COLUMN "entity_list_field"."render_config" IS '单元格渲染配置JSON。';
COMMENT ON COLUMN "entity_list_field"."revision" IS '修订号：字段草稿修订号。';
COMMENT ON COLUMN "entity_list_field"."order_key" IS '稀疏排序键。';
COMMENT ON COLUMN "entity_list_field"."interface_extension_id" IS '列接口扩展 ID：逻辑关联一条可用于 `LIST_COLUMN` 的 `INTERFACE` 扩展；无需再保存操作编码。';
COMMENT ON COLUMN "entity_list_field"."template_id" IS '来源模板ID。';
COMMENT ON COLUMN "entity_list_field"."template_version" IS '锁定模板版本。';
COMMENT ON COLUMN "entity_list_field"."local_overrides_document" IS '模板实例本地覆盖JSON文档。';
COMMENT ON TABLE "entity_list_scene" IS '实体列表场景配置表';
COMMENT ON COLUMN "entity_list_scene"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_scene"."list_config_id" IS '所属列表配置ID。';
COMMENT ON COLUMN "entity_list_scene"."scene_code" IS '场景编码（如 pc/mobile/picker 等）。';
COMMENT ON COLUMN "entity_list_scene"."sort_order" IS '排序号。';
COMMENT ON COLUMN "entity_list_scene"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_list_scene"."revision" IS '修订号：场景草稿修订号。';
COMMENT ON TABLE "entity_list_scope_audit_log" IS '列表数据范围审计表';
COMMENT ON COLUMN "entity_list_scope_audit_log"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_scope_audit_log"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_list_scope_audit_log"."list_key" IS '列表编码。';
COMMENT ON COLUMN "entity_list_scope_audit_log"."user_id" IS '操作或被校验用户。';
COMMENT ON COLUMN "entity_list_scope_audit_log"."operation" IS '操作。';
COMMENT ON COLUMN "entity_list_scope_audit_log"."result" IS '结果。';
COMMENT ON COLUMN "entity_list_scope_audit_log"."detail_json" IS '结构化详情。';
COMMENT ON COLUMN "entity_list_scope_audit_log"."create_time" IS '创建时间：记录时间。';
COMMENT ON TABLE "entity_list_scope_binding" IS '列表数据范围绑定表';
COMMENT ON COLUMN "entity_list_scope_binding"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_scope_binding"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_list_scope_binding"."policy_id" IS '数据范围方案ID。';
COMMENT ON COLUMN "entity_list_scope_binding"."list_key" IS '列表编码，空表示实体默认范围。';
COMMENT ON COLUMN "entity_list_scope_binding"."match_config" IS '适用用户结构化条件。';
COMMENT ON COLUMN "entity_list_scope_binding"."rule_effect" IS '规则效果。';
COMMENT ON COLUMN "entity_list_scope_binding"."enabled" IS '是否启用。';
COMMENT ON COLUMN "entity_list_scope_binding"."effective_start_time" IS '生效时间。';
COMMENT ON COLUMN "entity_list_scope_binding"."effective_end_time" IS '失效时间。';
COMMENT ON COLUMN "entity_list_scope_binding"."created_by" IS '创建人。';
COMMENT ON COLUMN "entity_list_scope_binding"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_list_scope_binding"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_list_scope_binding"."deleted" IS '逻辑删除标记：逻辑删除。';
COMMENT ON TABLE "entity_list_scope_delegation" IS '列表数据范围委派表';
COMMENT ON COLUMN "entity_list_scope_delegation"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_scope_delegation"."entity_code" IS '实体编码，空表示全部实体。';
COMMENT ON COLUMN "entity_list_scope_delegation"."from_user_id" IS '委托方用户ID。';
COMMENT ON COLUMN "entity_list_scope_delegation"."to_user_id" IS '受托方用户ID。';
COMMENT ON COLUMN "entity_list_scope_delegation"."delegate_scope" IS '委派范围。';
COMMENT ON COLUMN "entity_list_scope_delegation"."policy_id" IS '指定方案ID。';
COMMENT ON COLUMN "entity_list_scope_delegation"."delegate_config" IS '附加结构化条件。';
COMMENT ON COLUMN "entity_list_scope_delegation"."start_time" IS '开始时间。';
COMMENT ON COLUMN "entity_list_scope_delegation"."end_time" IS '结束时间。';
COMMENT ON COLUMN "entity_list_scope_delegation"."enabled" IS '是否启用。';
COMMENT ON COLUMN "entity_list_scope_delegation"."created_by" IS '创建人。';
COMMENT ON COLUMN "entity_list_scope_delegation"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_list_scope_delegation"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_list_scope_delegation"."deleted" IS '逻辑删除标记：逻辑删除。';
COMMENT ON TABLE "entity_list_scope_policy" IS '列表数据范围方案表';
COMMENT ON COLUMN "entity_list_scope_policy"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_scope_policy"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_list_scope_policy"."policy_key" IS '方案稳定编码。';
COMMENT ON COLUMN "entity_list_scope_policy"."policy_name" IS '方案名称。';
COMMENT ON COLUMN "entity_list_scope_policy"."description" IS '说明：方案说明。';
COMMENT ON COLUMN "entity_list_scope_policy"."preset_code" IS '内置模板编码。';
COMMENT ON COLUMN "entity_list_scope_policy"."filter_config" IS '结构化数据条件。';
COMMENT ON COLUMN "entity_list_scope_policy"."status" IS '状态。';
COMMENT ON COLUMN "entity_list_scope_policy"."enabled" IS '是否启用。';
COMMENT ON COLUMN "entity_list_scope_policy"."version" IS '版本号：配置版本。';
COMMENT ON COLUMN "entity_list_scope_policy"."review_required" IS '是否需要人工复核：旧复杂规则是否需要人工确认。';
COMMENT ON COLUMN "entity_list_scope_policy"."created_by" IS '创建人。';
COMMENT ON COLUMN "entity_list_scope_policy"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_list_scope_policy"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_list_scope_policy"."deleted" IS '逻辑删除标记：逻辑删除。';
COMMENT ON TABLE "entity_list_scope_release" IS '列表数据范围发布表';
COMMENT ON COLUMN "entity_list_scope_release"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_list_scope_release"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_list_scope_release"."version" IS '版本号：发布版本。';
COMMENT ON COLUMN "entity_list_scope_release"."snapshot_json" IS '方案、绑定和列表模式完整快照。';
COMMENT ON COLUMN "entity_list_scope_release"."content_hash" IS '内容哈希：内容摘要，用于检查配置或快照内容是否一致。';
COMMENT ON COLUMN "entity_list_scope_release"."status" IS '状态。';
COMMENT ON COLUMN "entity_list_scope_release"."description" IS '说明：发布说明。';
COMMENT ON COLUMN "entity_list_scope_release"."published_by" IS '发布人。';
COMMENT ON COLUMN "entity_list_scope_release"."published_at" IS '发布时间。';
COMMENT ON TABLE "entity_mutation_receipt" IS '实体变更幂等回执表';
COMMENT ON COLUMN "entity_mutation_receipt"."id" IS '主键ID：变更回执ID。';
COMMENT ON COLUMN "entity_mutation_receipt"."idempotency_key" IS '幂等键：全局幂等键。';
COMMENT ON COLUMN "entity_mutation_receipt"."command_hash" IS '变更命令摘要。';
COMMENT ON COLUMN "entity_mutation_receipt"."operation_id" IS '操作ID。';
COMMENT ON COLUMN "entity_mutation_receipt"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_mutation_receipt"."record_id" IS '记录ID。';
COMMENT ON COLUMN "entity_mutation_receipt"."operation_type" IS '操作类型。';
COMMENT ON COLUMN "entity_mutation_receipt"."status" IS '状态。';
COMMENT ON COLUMN "entity_mutation_receipt"."result_document" IS '首次成功执行结果JSON。';
COMMENT ON COLUMN "entity_mutation_receipt"."version_no" IS '版本编号。';
COMMENT ON COLUMN "entity_mutation_receipt"."version_scenario_code" IS '版本场景编码。';
COMMENT ON COLUMN "entity_mutation_receipt"."changed" IS '是否发生变化。';
COMMENT ON COLUMN "entity_mutation_receipt"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_mutation_receipt"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_process_link" IS '实体与流程实例关联表';
COMMENT ON COLUMN "entity_process_link"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_process_link"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_process_link"."entity_record_id" IS '实体记录ID。';
COMMENT ON COLUMN "entity_process_link"."generation" IS '代次。';
COMMENT ON COLUMN "entity_process_link"."process_definition_key" IS '流程定义键。';
COMMENT ON COLUMN "entity_process_link"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "entity_process_link"."state" IS '处理状态：关联状态：PENDING 等待发起、ACTIVE 已关联运行实例、ENDED 已结束；与 end_type 配合区分生命周期和结束原因。';
COMMENT ON COLUMN "entity_process_link"."request_id" IS '请求ID。';
COMMENT ON COLUMN "entity_process_link"."entity_status" IS '实体状态。';
COMMENT ON COLUMN "entity_process_link"."ended_at" IS '结束时间。';
COMMENT ON COLUMN "entity_process_link"."version" IS '版本号。';
COMMENT ON COLUMN "entity_process_link"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_process_link"."update_time" IS '更新时间。';
COMMENT ON COLUMN "entity_process_link"."end_type" IS '流程结束类型：在流程结束同步时记录 COMPLETED（正常结束）、TERMINATED（终止）或 WITHDRAWN（撤回），用于区分结束原因。关联状态统一为 ENDED，业务审批是否通过仍看业务状态，不能仅凭 COMPLETED 判断。未结束或历史原因未确认时可为空。';
COMMENT ON TABLE "entity_publish_history" IS '实体发布历史表';
COMMENT ON COLUMN "entity_publish_history"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_publish_history"."entity_id" IS '实体定义ID。';
COMMENT ON COLUMN "entity_publish_history"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_publish_history"."entity_name" IS '实体名称。';
COMMENT ON COLUMN "entity_publish_history"."version" IS '版本号。';
COMMENT ON COLUMN "entity_publish_history"."version_description" IS '版本说明。';
COMMENT ON COLUMN "entity_publish_history"."fields_snapshot" IS '字段定义快照JSON。';
COMMENT ON COLUMN "entity_publish_history"."relations_snapshot" IS '发布时实体关系定义快照JSON，NULL为旧发布。';
COMMENT ON COLUMN "entity_publish_history"."table_ddl" IS '表结构DDL。';
COMMENT ON COLUMN "entity_publish_history"."publish_type" IS '发布类型：CREATE首次创建/ALTER修改结构。';
COMMENT ON COLUMN "entity_publish_history"."changes_description" IS '变更内容描述。';
COMMENT ON COLUMN "entity_publish_history"."published_at" IS '发布时间。';
COMMENT ON COLUMN "entity_publish_history"."published_by" IS '发布人ID。';
COMMENT ON COLUMN "entity_publish_history"."published_by_name" IS '发布人名称：发布人姓名。';
COMMENT ON COLUMN "entity_publish_history"."status" IS '状态：ACTIVE有效/ROLLBACK已回滚。';
COMMENT ON COLUMN "entity_publish_history"."process_definition_id" IS '发布时绑定流程定义ID。';
COMMENT ON COLUMN "entity_publish_history"."lifecycle_mode" IS '发布时实体生命周期模式。';
COMMENT ON COLUMN "entity_publish_history"."team_visibility_enabled" IS '团队可见性开关：发布时是否允许数据参与团队查看记录；保留旧发布的团队可见性信息，当前权限计算不依赖这两个开关。';
COMMENT ON COLUMN "entity_publish_history"."team_visibility_level" IS '团队可见性级别：发布时参与团队权限级别；保留旧发布的团队可见性信息，当前权限计算不依赖这两个开关。';
COMMENT ON TABLE "entity_record_version" IS '实体记录版本表';
COMMENT ON COLUMN "entity_record_version"."id" IS '主键ID：业务版本ID。';
COMMENT ON COLUMN "entity_record_version"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_record_version"."record_id" IS '记录ID。';
COMMENT ON COLUMN "entity_record_version"."version_no" IS '同一记录从1递增。';
COMMENT ON COLUMN "entity_record_version"."version_title" IS '版本标题。';
COMMENT ON COLUMN "entity_record_version"."scenario_code" IS '场景编码。';
COMMENT ON COLUMN "entity_record_version"."scenario_name" IS '场景名称。';
COMMENT ON COLUMN "entity_record_version"."operation_type" IS '操作类型。';
COMMENT ON COLUMN "entity_record_version"."source_type" IS '来源类型。';
COMMENT ON COLUMN "entity_record_version"."source_id" IS '来源ID。';
COMMENT ON COLUMN "entity_record_version"."business_intent_code" IS '业务意图编码。';
COMMENT ON COLUMN "entity_record_version"."business_intent_name" IS '业务意图名称。';
COMMENT ON COLUMN "entity_record_version"."source_entity_code" IS '来源实体编码。';
COMMENT ON COLUMN "entity_record_version"."source_record_id" IS '来源记录ID。';
COMMENT ON COLUMN "entity_record_version"."process_definition_id" IS '流程定义ID。';
COMMENT ON COLUMN "entity_record_version"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "entity_record_version"."task_id" IS '任务ID。';
COMMENT ON COLUMN "entity_record_version"."operator_id" IS '操作人ID。';
COMMENT ON COLUMN "entity_record_version"."operator_name" IS '操作人名称。';
COMMENT ON COLUMN "entity_record_version"."business_trace_key" IS '业务追踪键。';
COMMENT ON COLUMN "entity_record_version"."idempotency_key" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "entity_record_version"."entity_release_id" IS '实体发布ID。';
COMMENT ON COLUMN "entity_record_version"."entity_release_version" IS '实体发布版本。';
COMMENT ON COLUMN "entity_record_version"."schema_version" IS '快照契约版本：1/2。';
COMMENT ON COLUMN "entity_record_version"."config_release_id" IS '旧版本策略发布ID：仅供滚动发布期间的旧 Pod 写入；业务快照不再依赖此值。';
COMMENT ON COLUMN "entity_record_version"."config_release_version" IS '旧版本策略发布版本：仅供滚动发布期间的旧 Pod 写入；新代码不使用。';
COMMENT ON COLUMN "entity_record_version"."data_hash" IS '原始业务数据摘要。';
COMMENT ON COLUMN "entity_record_version"."presentation_hash" IS '冻结中文展示语义摘要。';
COMMENT ON COLUMN "entity_record_version"."scope_hash" IS '固化范围摘要。';
COMMENT ON COLUMN "entity_record_version"."request_hash" IS '幂等请求摘要。';
COMMENT ON COLUMN "entity_record_version"."dataset_count" IS '关系数据集数量。';
COMMENT ON COLUMN "entity_record_version"."snapshot_row_count" IS '根记录与关系记录总数。';
COMMENT ON COLUMN "entity_record_version"."snapshot_size_bytes" IS 'V2快照序列化字节数。';
COMMENT ON COLUMN "entity_record_version"."completeness" IS '完整性：COMPLETE；V2禁止截断。';
COMMENT ON COLUMN "entity_record_version"."snapshot_hash" IS '快照哈希。';
COMMENT ON COLUMN "entity_record_version"."snapshot_document" IS '快照文档。';
COMMENT ON COLUMN "entity_record_version"."create_time" IS '创建时间。';
COMMENT ON TABLE "entity_record_version_counter" IS '记录版本计数器表';
COMMENT ON COLUMN "entity_record_version_counter"."entity_code" IS '同一实体记录的事务级版本号计数器。';
COMMENT ON COLUMN "entity_record_version_counter"."record_id" IS '记录ID。';
COMMENT ON COLUMN "entity_record_version_counter"."last_version_no" IS '最近版本编号。';
COMMENT ON COLUMN "entity_record_version_counter"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_record_version_dataset" IS '记录版本关系数据集表';
COMMENT ON COLUMN "entity_record_version_dataset"."id" IS '主键ID：数据集ID。';
COMMENT ON COLUMN "entity_record_version_dataset"."version_id" IS '业务版本ID。';
COMMENT ON COLUMN "entity_record_version_dataset"."node_code" IS '稳定范围节点编码。';
COMMENT ON COLUMN "entity_record_version_dataset"."node_kind" IS '节点种类。';
COMMENT ON COLUMN "entity_record_version_dataset"."relation_code" IS '冻结关系编码。';
COMMENT ON COLUMN "entity_record_version_dataset"."relation_name" IS '冻结关系中文名称。';
COMMENT ON COLUMN "entity_record_version_dataset"."entity_code" IS '子实体编码。';
COMMENT ON COLUMN "entity_record_version_dataset"."entity_name" IS '冻结子实体中文名称。';
COMMENT ON COLUMN "entity_record_version_dataset"."entity_release_id" IS '实体发布ID。';
COMMENT ON COLUMN "entity_record_version_dataset"."entity_release_version" IS '实体发布版本。';
COMMENT ON COLUMN "entity_record_version_dataset"."selector_document" IS '冻结关系、过滤和排序选择器JSON。';
COMMENT ON COLUMN "entity_record_version_dataset"."presentation_document" IS '冻结中文表单展示定义JSON。';
COMMENT ON COLUMN "entity_record_version_dataset"."data_hash" IS '数据哈希。';
COMMENT ON COLUMN "entity_record_version_dataset"."presentation_hash" IS '展示哈希。';
COMMENT ON COLUMN "entity_record_version_dataset"."scope_hash" IS '范围哈希。';
COMMENT ON COLUMN "entity_record_version_dataset"."row_count" IS '行数量。';
COMMENT ON COLUMN "entity_record_version_dataset"."complete" IS 'V2必须完整，禁止静默截断。';
COMMENT ON COLUMN "entity_record_version_dataset"."create_time" IS '创建时间。';
COMMENT ON TABLE "entity_record_version_dataset_row" IS '记录版本数据集行表';
COMMENT ON COLUMN "entity_record_version_dataset_row"."id" IS '主键ID：数据集行ID。';
COMMENT ON COLUMN "entity_record_version_dataset_row"."dataset_id" IS '数据集ID。';
COMMENT ON COLUMN "entity_record_version_dataset_row"."record_id" IS '子记录稳定ID。';
COMMENT ON COLUMN "entity_record_version_dataset_row"."record_title" IS '冻结业务中文标题。';
COMMENT ON COLUMN "entity_record_version_dataset_row"."row_order" IS '冻结顺序。';
COMMENT ON COLUMN "entity_record_version_dataset_row"."row_hash" IS '行哈希。';
COMMENT ON COLUMN "entity_record_version_dataset_row"."values_document" IS '值集合文档：fieldCode到FrozenValue的JSON。';
COMMENT ON COLUMN "entity_record_version_dataset_row"."create_time" IS '创建时间。';
COMMENT ON TABLE "entity_relation" IS '实体关系定义表';
COMMENT ON COLUMN "entity_relation"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_relation"."parent_entity_id" IS '主实体ID。';
COMMENT ON COLUMN "entity_relation"."parent_entity_code" IS '主实体编码。';
COMMENT ON COLUMN "entity_relation"."parent_field_id" IS '主实体关系字段ID；关系已与 SUB_FORM 字段生命周期解耦，保留旧父字段关联用于回退或兼容。';
COMMENT ON COLUMN "entity_relation"."parent_field_code" IS '旧版承载关系的父字段编码，仅兼容使用；关系已与 SUB_FORM 字段生命周期解耦，保留旧父字段关联用于回退或兼容。';
COMMENT ON COLUMN "entity_relation"."relation_code" IS '关系编码。';
COMMENT ON COLUMN "entity_relation"."relation_name" IS '关系名称。';
COMMENT ON COLUMN "entity_relation"."data_key" IS '聚合数据中的稳定属性名。';
COMMENT ON COLUMN "entity_relation"."child_entity_id" IS '子实体ID。';
COMMENT ON COLUMN "entity_relation"."child_entity_code" IS '子实体编码。';
COMMENT ON COLUMN "entity_relation"."child_ref_field_code" IS '子实体回填主数据ID字段。';
COMMENT ON COLUMN "entity_relation"."relation_type" IS '关系类型：ONE_TO_ONE/ONE_TO_MANY。';
COMMENT ON COLUMN "entity_relation"."ownership_type" IS '关系所有权：COMPOSITION/ASSOCIATION。';
COMMENT ON COLUMN "entity_relation"."cascade_delete" IS '主数据删除时是否级联删除子数据。';
COMMENT ON COLUMN "entity_relation"."required" IS '是否必需：是否必填。';
COMMENT ON COLUMN "entity_relation"."sort_order" IS '排序号：排序。';
COMMENT ON COLUMN "entity_relation"."enabled" IS '是否启用。';
COMMENT ON COLUMN "entity_relation"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "entity_relation"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_relation"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_schema_operation" IS '实体结构发布操作表';
COMMENT ON COLUMN "entity_schema_operation"."id" IS '主键ID：操作ID。';
COMMENT ON COLUMN "entity_schema_operation"."entity_id" IS '实体ID。';
COMMENT ON COLUMN "entity_schema_operation"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_schema_operation"."status" IS '状态。';
COMMENT ON COLUMN "entity_schema_operation"."plan_hash" IS '不可变DDL计划摘要。';
COMMENT ON COLUMN "entity_schema_operation"."idempotency_key" IS '幂等键。';
COMMENT ON COLUMN "entity_schema_operation"."plan_json" IS '不可变DDL计划JSON。';
COMMENT ON COLUMN "entity_schema_operation"."target_fingerprint" IS '目标结构指纹。';
COMMENT ON COLUMN "entity_schema_operation"."actual_fingerprint" IS '实际结构指纹。';
COMMENT ON COLUMN "entity_schema_operation"."drift_json" IS '结构漂移JSON。';
COMMENT ON COLUMN "entity_schema_operation"."unique_conflict_json" IS '唯一性冲突扫描结果JSON。';
COMMENT ON COLUMN "entity_schema_operation"."risk_level" IS '风险级别。';
COMMENT ON COLUMN "entity_schema_operation"."risk_reason" IS '风险原因。';
COMMENT ON COLUMN "entity_schema_operation"."estimated_rows" IS '预估数据行数。';
COMMENT ON COLUMN "entity_schema_operation"."lock_risk" IS '锁表风险。';
COMMENT ON COLUMN "entity_schema_operation"."release_window" IS '建议发布窗口。';
COMMENT ON COLUMN "entity_schema_operation"."attempt_count" IS '执行次数。';
COMMENT ON COLUMN "entity_schema_operation"."error_message" IS '最近失败信息。';
COMMENT ON COLUMN "entity_schema_operation"."started_at" IS '开始时间。';
COMMENT ON COLUMN "entity_schema_operation"."finished_at" IS '完成时间。';
COMMENT ON COLUMN "entity_schema_operation"."created_by" IS '创建人。';
COMMENT ON COLUMN "entity_schema_operation"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_schema_operation"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_schema_operation"."operation_source" IS '操作来源；INDEX_ADVISOR 来源仅保留历史记录；实体发布来源仍在使用。';
COMMENT ON COLUMN "entity_schema_operation"."source_reference_id" IS '索引建议等来源记录ID；INDEX_ADVISOR 来源仅保留历史记录；实体发布来源仍在使用。';
COMMENT ON TABLE "entity_schema_operation_event" IS '实体结构操作事件表';
COMMENT ON COLUMN "entity_schema_operation_event"."id" IS '主键ID：事件ID。';
COMMENT ON COLUMN "entity_schema_operation_event"."operation_id" IS '操作ID。';
COMMENT ON COLUMN "entity_schema_operation_event"."from_status" IS '原状态。';
COMMENT ON COLUMN "entity_schema_operation_event"."to_status" IS '目标状态。';
COMMENT ON COLUMN "entity_schema_operation_event"."message" IS '状态说明。';
COMMENT ON COLUMN "entity_schema_operation_event"."create_time" IS '创建时间。';
COMMENT ON TABLE "entity_status" IS '实体状态定义表';
COMMENT ON COLUMN "entity_status"."id" IS '主键ID。';
COMMENT ON COLUMN "entity_status"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_status"."status_code" IS '状态编码（系统标识）。';
COMMENT ON COLUMN "entity_status"."status_name" IS '状态名称（显示用）。';
COMMENT ON COLUMN "entity_status"."status_category" IS '状态分类：NEW-新建、PROCESSING-审批中、COMPLETED-已完成、TERMINATED-终止。';
COMMENT ON COLUMN "entity_status"."sort_order" IS '排序号。';
COMMENT ON COLUMN "entity_status"."description" IS '说明：状态说明。';
COMMENT ON COLUMN "entity_status"."color" IS '状态颜色（如：#67C23A）。';
COMMENT ON COLUMN "entity_status"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "entity_status"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_status"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_unique_value" IS '实体字段唯一值预留表';
COMMENT ON COLUMN "entity_unique_value"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_unique_value"."field_code" IS '字段编码。';
COMMENT ON COLUMN "entity_unique_value"."value_hash" IS '规范化值摘要。';
COMMENT ON COLUMN "entity_unique_value"."normalized_value" IS '规范化值审计副本。';
COMMENT ON COLUMN "entity_unique_value"."record_id" IS '占用该值的记录ID。';
COMMENT ON COLUMN "entity_unique_value"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_unique_value"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "entity_version_config" IS '实体数据版本策略表';
COMMENT ON COLUMN "entity_version_config"."id" IS '主键ID：配置ID。';
COMMENT ON COLUMN "entity_version_config"."entity_id" IS '实体定义ID。';
COMMENT ON COLUMN "entity_version_config"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "entity_version_config"."enabled" IS '是否启用数据版本。';
COMMENT ON COLUMN "entity_version_config"."contract_version" IS '旧配置契约版本：供兼容桥和尚未升级的旧 Pod 使用。';
COMMENT ON COLUMN "entity_version_config"."draft_document" IS '旧草稿JSON：供兼容路由、双写桥和尚未升级的旧 Pod 使用；新核心运行时不读取。';
COMMENT ON COLUMN "entity_version_config"."config_document" IS '当前生效配置JSON：V2 触发器、范围和比较策略；保存后立即参与后续版本捕获。过渡期可空，以兼容旧 Pod 创建未发布配置。';
COMMENT ON COLUMN "entity_version_config"."migration_state" IS '旧迁移状态：供兼容桥和旧 Pod 保持旧契约。';
COMMENT ON COLUMN "entity_version_config"."active_release_id" IS '旧运行发布ID：由兼容桥维护，供尚未升级的旧 Pod 定位发布快照。';
COMMENT ON COLUMN "entity_version_config"."revision" IS '修订号：当前配置修订号，用于 If-Match 乐观锁；不代表可回退的配置版本。';
COMMENT ON COLUMN "entity_version_config"."status" IS '旧发布状态：供兼容路由和旧 Pod 区分草稿、已发布。';
COMMENT ON COLUMN "entity_version_config"."create_by" IS '创建人。';
COMMENT ON COLUMN "entity_version_config"."create_time" IS '创建时间。';
COMMENT ON COLUMN "entity_version_config"."update_by" IS '修改人。';
COMMENT ON COLUMN "entity_version_config"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "entity_version_config"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "entity_version_config_release" IS '实体版本策略发布兼容表';
COMMENT ON COLUMN "entity_version_config_release"."id" IS '主键ID：旧发布快照ID。';
COMMENT ON COLUMN "entity_version_config_release"."config_id" IS '版本配置ID。';
COMMENT ON COLUMN "entity_version_config_release"."version" IS '版本号：旧发布版本号。';
COMMENT ON COLUMN "entity_version_config_release"."contract_version" IS '配置契约版本：旧配置契约版本。';
COMMENT ON COLUMN "entity_version_config_release"."config_document" IS '旧不可变配置JSON：由兼容桥写入，供尚未升级的旧 Pod 读取。';
COMMENT ON COLUMN "entity_version_config_release"."scope_hash" IS '发布时范围摘要：旧发布时冻结范围摘要。';
COMMENT ON COLUMN "entity_version_config_release"."published_by" IS '发布人：旧发布人。';
COMMENT ON COLUMN "entity_version_config_release"."published_by_name" IS '发布人名称：旧发布人名称。';
COMMENT ON COLUMN "entity_version_config_release"."publish_time" IS '发布时间：旧发布时间。';
COMMENT ON COLUMN "entity_version_config_release"."create_time" IS '创建时间。';
COMMENT ON TABLE "flw_channel_definition" IS 'Flowable 事件注册或批处理事件通道定义表，保存引擎定义与运行状态';
COMMENT ON COLUMN "flw_channel_definition"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "flw_channel_definition"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "flw_channel_definition"."version_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "flw_channel_definition"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "flw_channel_definition"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "flw_channel_definition"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "flw_channel_definition"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "flw_channel_definition"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "flw_channel_definition"."resource_name_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "flw_channel_definition"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "flw_channel_definition"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "flw_channel_definition"."implementation_" IS 'IMPLEMENTATION_ 引擎属性，供 Flowable 事件注册或批处理事件通道定义表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON TABLE "flw_event_definition" IS 'Flowable 事件注册或批处理事件定义表，保存引擎定义与运行状态';
COMMENT ON COLUMN "flw_event_definition"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "flw_event_definition"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "flw_event_definition"."version_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "flw_event_definition"."key_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "flw_event_definition"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "flw_event_definition"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "flw_event_definition"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "flw_event_definition"."resource_name_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "flw_event_definition"."description_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON TABLE "flw_event_deployment" IS 'Flowable 事件注册或批处理事件部署包表，保存引擎定义与运行状态';
COMMENT ON COLUMN "flw_event_deployment"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "flw_event_deployment"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "flw_event_deployment"."category_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "flw_event_deployment"."deploy_time_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "flw_event_deployment"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "flw_event_deployment"."parent_deployment_id_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "flw_event_resource" IS 'Flowable 事件注册或批处理事件资源表，保存引擎定义与运行状态';
COMMENT ON COLUMN "flw_event_resource"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "flw_event_resource"."name_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "flw_event_resource"."deployment_id_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "flw_event_resource"."resource_bytes_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON TABLE "flw_ru_batch" IS 'Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态';
COMMENT ON COLUMN "flw_ru_batch"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "flw_ru_batch"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "flw_ru_batch"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "flw_ru_batch"."search_key_" IS 'SEARCH_KEY_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "flw_ru_batch"."search_key2_" IS 'SEARCH_KEY2_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "flw_ru_batch"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "flw_ru_batch"."complete_time_" IS '完成时间，用于批处理结果和耗时统计';
COMMENT ON COLUMN "flw_ru_batch"."status_" IS '批处理状态，决定后续调度或结果查询';
COMMENT ON COLUMN "flw_ru_batch"."batch_doc_id_" IS 'BATCH_DOC 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "flw_ru_batch"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "flw_ru_batch_part" IS 'Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态';
COMMENT ON COLUMN "flw_ru_batch_part"."id_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "flw_ru_batch_part"."rev_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "flw_ru_batch_part"."batch_id_" IS 'BATCH 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "flw_ru_batch_part"."type_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "flw_ru_batch_part"."scope_id_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "flw_ru_batch_part"."sub_scope_id_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "flw_ru_batch_part"."scope_type_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "flw_ru_batch_part"."search_key_" IS 'SEARCH_KEY_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "flw_ru_batch_part"."search_key2_" IS 'SEARCH_KEY2_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "flw_ru_batch_part"."create_time_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "flw_ru_batch_part"."complete_time_" IS '完成时间，用于批处理结果和耗时统计';
COMMENT ON COLUMN "flw_ru_batch_part"."status_" IS '批处理状态，决定后续调度或结果查询';
COMMENT ON COLUMN "flw_ru_batch_part"."result_doc_id_" IS 'RESULT_DOC 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "flw_ru_batch_part"."tenant_id_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "integration_api_request_lease" IS 'Embed 请求并发租约表';
COMMENT ON COLUMN "integration_api_request_lease"."lease_id" IS '租约ID。';
COMMENT ON COLUMN "integration_api_request_lease"."application_id" IS '应用ID。';
COMMENT ON COLUMN "integration_api_request_lease"."scope_key" IS '并发配额范围键：区分请求类别的内部键，不表示 OAuth Scope。';
COMMENT ON COLUMN "integration_api_request_lease"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "integration_api_request_lease"."create_time" IS '创建时间。';
COMMENT ON COLUMN "integration_api_request_lease"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "integration_application" IS '外部集成应用表';
COMMENT ON COLUMN "integration_application"."id" IS '主键ID。';
COMMENT ON COLUMN "integration_application"."client_id" IS '客户端ID。';
COMMENT ON COLUMN "integration_application"."application_name" IS '应用名称。';
COMMENT ON COLUMN "integration_application"."description" IS '说明。';
COMMENT ON COLUMN "integration_application"."owner_organization_id" IS '所有者组织ID。';
COMMENT ON COLUMN "integration_application"."status" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "integration_application"."rate_limit_per_minute" IS '每分钟请求上限。';
COMMENT ON COLUMN "integration_application"."max_concurrency" IS '最大并发数。';
COMMENT ON COLUMN "integration_application"."allowed_source_cidrs" IS '允许的来源网段集合。';
COMMENT ON COLUMN "integration_application"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "integration_application"."version" IS '版本号。';
COMMENT ON COLUMN "integration_application"."created_by" IS '创建人。';
COMMENT ON COLUMN "integration_application"."updated_by" IS '修改人。';
COMMENT ON COLUMN "integration_application"."create_time" IS '创建时间。';
COMMENT ON COLUMN "integration_application"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "integration_application_credential" IS '集成应用凭证表';
COMMENT ON COLUMN "integration_application_credential"."id" IS '主键ID。';
COMMENT ON COLUMN "integration_application_credential"."application_id" IS '应用ID。';
COMMENT ON COLUMN "integration_application_credential"."secret_hash" IS '密钥哈希：应用凭证哈希，用于验证凭证，不能反推出原始凭证。';
COMMENT ON COLUMN "integration_application_credential"."credential_hint" IS '凭证提示。';
COMMENT ON COLUMN "integration_application_credential"."status" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "integration_application_credential"."credential_version" IS '凭证版本。';
COMMENT ON COLUMN "integration_application_credential"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "integration_application_credential"."last_used_at" IS '最近使用时间。';
COMMENT ON COLUMN "integration_application_credential"."created_by" IS '创建人。';
COMMENT ON COLUMN "integration_application_credential"."revoked_by" IS '撤销人。';
COMMENT ON COLUMN "integration_application_credential"."revoked_at" IS '撤销时间。';
COMMENT ON COLUMN "integration_application_credential"."create_time" IS '创建时间。';
COMMENT ON COLUMN "integration_application_credential"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "integration_application_credential"."active_application_id" IS '活跃应用ID；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "integration_idempotency_record" IS 'Embed 写入幂等记录表';
COMMENT ON COLUMN "integration_idempotency_record"."id" IS '主键ID。';
COMMENT ON COLUMN "integration_idempotency_record"."application_id" IS '应用ID。';
COMMENT ON COLUMN "integration_idempotency_record"."operation" IS '操作。';
COMMENT ON COLUMN "integration_idempotency_record"."idempotency_key" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "integration_idempotency_record"."request_hash" IS '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。';
COMMENT ON COLUMN "integration_idempotency_record"."status" IS '状态；CHECK 枚举： \\''PROCESSING\\'',\\''SUCCEEDED\\'',\\''FAILED_RETRYABLE\\'' 。';
COMMENT ON COLUMN "integration_idempotency_record"."resource_type" IS '资源类型。';
COMMENT ON COLUMN "integration_idempotency_record"."resource_id" IS '资源ID。';
COMMENT ON COLUMN "integration_idempotency_record"."response_status" IS '响应状态。';
COMMENT ON COLUMN "integration_idempotency_record"."response_body" IS '响应正文。';
COMMENT ON COLUMN "integration_idempotency_record"."fencing_token" IS '执行隔离代次。';
COMMENT ON COLUMN "integration_idempotency_record"."processing_started_at" IS '处理开始时间。';
COMMENT ON COLUMN "integration_idempotency_record"."expires_at" IS '过期时间。';
COMMENT ON COLUMN "integration_idempotency_record"."create_time" IS '创建时间。';
COMMENT ON COLUMN "integration_idempotency_record"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "integration_rate_limit_bucket" IS '集成应用限流桶表';
COMMENT ON COLUMN "integration_rate_limit_bucket"."bucket_key" IS '桶键。';
COMMENT ON COLUMN "integration_rate_limit_bucket"."window_epoch" IS '窗口时间戳。';
COMMENT ON COLUMN "integration_rate_limit_bucket"."request_count" IS '请求数量。';
COMMENT ON COLUMN "integration_rate_limit_bucket"."create_time" IS '创建时间。';
COMMENT ON COLUMN "integration_rate_limit_bucket"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "process_action" IS '流程动作绑定表';
COMMENT ON COLUMN "process_action"."id" IS '主键ID：流程动作配置 用于流程、节点和顺序流上配置的接口动作。';
COMMENT ON COLUMN "process_action"."process_config_id" IS '流程定义配置ID。';
COMMENT ON COLUMN "process_action"."scope_type" IS '作用域：PROCESS/NODE/SEQUENCE_FLOW。';
COMMENT ON COLUMN "process_action"."element_id" IS 'BPMN元素ID，流程级为空。';
COMMENT ON COLUMN "process_action"."trigger_timing" IS '业务触发时机。';
COMMENT ON COLUMN "process_action"."execution_mode" IS '执行方式：IN_TRANSACTION/AFTER_COMMIT。';
COMMENT ON COLUMN "process_action"."failure_policy" IS '失败策略：ROLLBACK/CONTINUE/RETRY/IGNORE。';
COMMENT ON COLUMN "process_action"."retry_config" IS '重试配置JSON。';
COMMENT ON COLUMN "process_action"."action_definition_id" IS '动作定义目录ID。';
COMMENT ON COLUMN "process_action"."action_name" IS '动作名称。';
COMMENT ON COLUMN "process_action"."description" IS '说明：动作描述。';
COMMENT ON COLUMN "process_action"."interface_name" IS '接口名称（Spring Bean或类名）。';
COMMENT ON COLUMN "process_action"."params_json" IS '参数JSON。';
COMMENT ON COLUMN "process_action"."sort_order" IS '排序号：执行顺序。';
COMMENT ON COLUMN "process_action"."enabled" IS '是否启用。';
COMMENT ON COLUMN "process_action"."status" IS '状态：DRAFT/PUBLISHED/DISABLED。';
COMMENT ON COLUMN "process_action"."version_id" IS '所属版本ID。';
COMMENT ON COLUMN "process_action"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_action"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_action"."created_by" IS '创建人。';
COMMENT ON COLUMN "process_action"."deleted" IS '逻辑删除标记：是否删除 0-未删除 1-已删除。';
COMMENT ON COLUMN "process_action"."failure_strategy_code" IS '失败策略编码，动作执行失败时用于选择对应处置逻辑';
COMMENT ON COLUMN "process_action"."failure_strategy_version" IS '失败策略版本，保证发布配置绑定稳定的策略实现';
COMMENT ON COLUMN "process_action"."failure_strategy_config" IS '失败策略参数文档，供重试、终止或人工处置逻辑解析';
COMMENT ON TABLE "process_action_definition" IS '流程动作处理器目录表';
COMMENT ON COLUMN "process_action_definition"."id" IS '主键ID：主键。';
COMMENT ON COLUMN "process_action_definition"."action_code" IS '稳定动作编码，默认使用Spring Bean名称。';
COMMENT ON COLUMN "process_action_definition"."display_name" IS '动作中文名称。';
COMMENT ON COLUMN "process_action_definition"."description" IS '说明：动作用途说明。';
COMMENT ON COLUMN "process_action_definition"."handler_name" IS '处理器名称：FlowActionHandler Bean名称。';
COMMENT ON COLUMN "process_action_definition"."visibility_scope" IS '可见范围：GLOBAL/ENTITY。';
COMMENT ON COLUMN "process_action_definition"."enabled" IS '是否启用：是否允许在流程设计器中选择。';
COMMENT ON COLUMN "process_action_definition"."created_by" IS '创建人。';
COMMENT ON COLUMN "process_action_definition"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_action_definition"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_action_definition"."deleted" IS '逻辑删除标记：逻辑删除标识：0-未删除，1-已删除。';
COMMENT ON TABLE "process_action_definition_entity" IS '流程动作可见实体表';
COMMENT ON COLUMN "process_action_definition_entity"."id" IS '主键ID：主键（UUID）。';
COMMENT ON COLUMN "process_action_definition_entity"."action_definition_id" IS '动作定义 ID。';
COMMENT ON COLUMN "process_action_definition_entity"."entity_code" IS '可见的实体编码。';
COMMENT ON COLUMN "process_action_definition_entity"."create_time" IS '创建时间。';
COMMENT ON TABLE "process_action_execution" IS '流程动作执行与重试表';
COMMENT ON COLUMN "process_action_execution"."id" IS '主键ID：执行记录主键。';
COMMENT ON COLUMN "process_action_execution"."action_id" IS '动作ID：关联的 process_action 动作配置 ID。';
COMMENT ON COLUMN "process_action_execution"."action_name" IS '动作名称快照。';
COMMENT ON COLUMN "process_action_execution"."handler_name" IS '处理器Bean名称快照。';
COMMENT ON COLUMN "process_action_execution"."handler_display_name" IS '处理器中文名称快照。';
COMMENT ON COLUMN "process_action_execution"."version_id" IS '所属流程发布版本 ID。';
COMMENT ON COLUMN "process_action_execution"."process_instance_id" IS '流程实例 ID。';
COMMENT ON COLUMN "process_action_execution"."process_definition_id" IS 'Flowable 流程定义 ID。';
COMMENT ON COLUMN "process_action_execution"."execution_id" IS 'Flowable 执行实例 ID。';
COMMENT ON COLUMN "process_action_execution"."task_id" IS '任务 ID（任务级动作）。';
COMMENT ON COLUMN "process_action_execution"."entity_code" IS '实体编码快照。';
COMMENT ON COLUMN "process_action_execution"."scope_type" IS '作用域类型：PROCESS、NODE、SEQUENCE_FLOW。';
COMMENT ON COLUMN "process_action_execution"."element_id" IS '绑定的 BPMN 元素 ID。';
COMMENT ON COLUMN "process_action_execution"."trigger_timing" IS '触发时机编码。';
COMMENT ON COLUMN "process_action_execution"."idempotency_key" IS '幂等键，防止同一动作重复执行。';
COMMENT ON COLUMN "process_action_execution"."payload_json" IS '触发事件序列化 JSON（执行上下文载荷）。';
COMMENT ON COLUMN "process_action_execution"."resolved_params_json" IS '表达式解析后的动作参数。';
COMMENT ON COLUMN "process_action_execution"."result_json" IS '动作执行结果。';
COMMENT ON COLUMN "process_action_execution"."execution_trace_json" IS '结构化执行步骤轨迹。';
COMMENT ON COLUMN "process_action_execution"."status" IS '状态：执行状态，对应 Status。';
COMMENT ON COLUMN "process_action_execution"."owner_id" IS '持有者ID：当前执行租约所有者。';
COMMENT ON COLUMN "process_action_execution"."lease_token" IS '租约代次：单调递增 fencing token。';
COMMENT ON COLUMN "process_action_execution"."lease_until" IS '租约截止时间：当前执行租约到期时间（数据库 UTC）。';
COMMENT ON COLUMN "process_action_execution"."retry_count" IS '已重试次数。';
COMMENT ON COLUMN "process_action_execution"."max_retries" IS '最大重试次数。';
COMMENT ON COLUMN "process_action_execution"."next_retry_time" IS '下次重试时间。';
COMMENT ON COLUMN "process_action_execution"."error_message" IS '错误信息（截断）。';
COMMENT ON COLUMN "process_action_execution"."error_stack" IS '异常堆栈。';
COMMENT ON COLUMN "process_action_execution"."started_at" IS '开始执行时间。';
COMMENT ON COLUMN "process_action_execution"."finished_at" IS '完成时间。';
COMMENT ON COLUMN "process_action_execution"."duration_ms" IS '执行耗时毫秒。';
COMMENT ON COLUMN "process_action_execution"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_action_execution"."update_time" IS '更新时间。';
COMMENT ON COLUMN "process_action_execution"."failure_strategy_snapshot" IS '执行时的失败策略快照，后续重试沿用触发时的配置';
COMMENT ON COLUMN "process_action_execution"."attempt_no" IS '执行尝试序号，每次重试递增，供审计和幂等判断';
COMMENT ON COLUMN "process_action_execution"."attempt_lease_token" IS '尝试租约令牌，防止过期执行器提交重试结果';
COMMENT ON COLUMN "process_action_execution"."termination_reason" IS '执行终止原因，区分重试耗尽、人工停止等结束路径';
COMMENT ON COLUMN "process_action_execution"."resolution_status" IS '失败处置状态，跟踪自动重试或人工处理结果';
COMMENT ON COLUMN "process_action_execution"."replay_root_id" IS '重放链根执行ID，用于聚合同一原始动作的后续执行';
COMMENT ON COLUMN "process_action_execution"."replay_of_id" IS '被重放的执行ID，用于追踪当前执行的来源';
COMMENT ON COLUMN "process_action_execution"."handler_idempotency_key" IS '处理器幂等键，跨重试和重放避免重复副作用';
COMMENT ON TABLE "process_assignee_incident" IS '空办理人阻断事件表';
COMMENT ON COLUMN "process_assignee_incident"."id" IS '主键ID。';
COMMENT ON COLUMN "process_assignee_incident"."process_config_id" IS '流程配置ID。';
COMMENT ON COLUMN "process_assignee_incident"."process_definition_id" IS '流程定义ID。';
COMMENT ON COLUMN "process_assignee_incident"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "process_assignee_incident"."task_id" IS '任务ID。';
COMMENT ON COLUMN "process_assignee_incident"."node_id" IS '节点ID。';
COMMENT ON COLUMN "process_assignee_incident"."node_name" IS '节点名称。';
COMMENT ON COLUMN "process_assignee_incident"."policy" IS '策略。';
COMMENT ON COLUMN "process_assignee_incident"."status" IS '状态。';
COMMENT ON COLUMN "process_assignee_incident"."empty_reason_code" IS '为空原因编码。';
COMMENT ON COLUMN "process_assignee_incident"."empty_reason_message" IS '为空原因消息。';
COMMENT ON COLUMN "process_assignee_incident"."resolver_code" IS '解析器编码。';
COMMENT ON COLUMN "process_assignee_incident"."resolver_extra_params_json" IS '解析器额外参数JSON。';
COMMENT ON COLUMN "process_assignee_incident"."fallback_user" IS '后备用户。';
COMMENT ON COLUMN "process_assignee_incident"."fallback_group" IS '后备用户组。';
COMMENT ON COLUMN "process_assignee_incident"."responsibility_owner" IS '责任所有者。';
COMMENT ON COLUMN "process_assignee_incident"."retry_count" IS '重试数量。';
COMMENT ON COLUMN "process_assignee_incident"."max_retries" IS '最大重试次数。';
COMMENT ON COLUMN "process_assignee_incident"."initial_delay_seconds" IS '初始延迟秒数。';
COMMENT ON COLUMN "process_assignee_incident"."backoff_multiplier" IS '退避倍数。';
COMMENT ON COLUMN "process_assignee_incident"."next_retry_at" IS '下一次重试时间。';
COMMENT ON COLUMN "process_assignee_incident"."resolution_action" IS '处置动作。';
COMMENT ON COLUMN "process_assignee_incident"."resolved_by" IS '解析完成人员。';
COMMENT ON COLUMN "process_assignee_incident"."resolved_at" IS '解析完成时间。';
COMMENT ON COLUMN "process_assignee_incident"."detail_json" IS '详情JSON。';
COMMENT ON COLUMN "process_assignee_incident"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_assignee_incident"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_assignee_incident"."open_slot" IS '开放占位；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "process_assignee_incident_action" IS '空办理人处置审计表';
COMMENT ON COLUMN "process_assignee_incident_action"."id" IS '主键ID。';
COMMENT ON COLUMN "process_assignee_incident_action"."incident_id" IS '事件ID。';
COMMENT ON COLUMN "process_assignee_incident_action"."request_id" IS '幂等请求ID。';
COMMENT ON COLUMN "process_assignee_incident_action"."action_type" IS '动作类型。';
COMMENT ON COLUMN "process_assignee_incident_action"."status" IS '状态。';
COMMENT ON COLUMN "process_assignee_incident_action"."operator" IS '操作人。';
COMMENT ON COLUMN "process_assignee_incident_action"."request_json" IS '请求JSON。';
COMMENT ON COLUMN "process_assignee_incident_action"."result_json" IS '结果JSON。';
COMMENT ON COLUMN "process_assignee_incident_action"."error_message" IS '错误消息。';
COMMENT ON COLUMN "process_assignee_incident_action"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_assignee_incident_action"."finished_at" IS '完成时间。';
COMMENT ON TABLE "process_cc_record" IS '流程抄送记录表';
COMMENT ON COLUMN "process_cc_record"."id" IS '主键ID。';
COMMENT ON COLUMN "process_cc_record"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "process_cc_record"."process_definition_id" IS '流程定义ID。';
COMMENT ON COLUMN "process_cc_record"."process_key" IS '流程Key。';
COMMENT ON COLUMN "process_cc_record"."process_name" IS '流程名称。';
COMMENT ON COLUMN "process_cc_record"."data_name" IS '流程数据名称快照：创建知会时保存业务数据当时的名称，供知会列表和通知展示。后续业务数据改名不更新此值；历史记录保持为空，不回查当前业务表补写。';
COMMENT ON COLUMN "process_cc_record"."business_key" IS '业务Key。';
COMMENT ON COLUMN "process_cc_record"."node_id" IS '节点ID。';
COMMENT ON COLUMN "process_cc_record"."node_name" IS '节点名称。';
COMMENT ON COLUMN "process_cc_record"."cc_user_id" IS '抄送人ID。';
COMMENT ON COLUMN "process_cc_record"."cc_user_name" IS '抄送人名称。';
COMMENT ON COLUMN "process_cc_record"."cc_type" IS '抄送类型：AUTO自动/MANUAL手动。';
COMMENT ON COLUMN "process_cc_record"."cc_timing" IS '抄送时机。';
COMMENT ON COLUMN "process_cc_record"."operator_id" IS '操作人ID。';
COMMENT ON COLUMN "process_cc_record"."operator_name" IS '操作人名称。';
COMMENT ON COLUMN "process_cc_record"."comment" IS '知会备注。';
COMMENT ON COLUMN "process_cc_record"."source_task_id" IS '来源任务ID。';
COMMENT ON COLUMN "process_cc_record"."source_type" IS '来源类型。';
COMMENT ON COLUMN "process_cc_record"."recipient_rule_snapshot" IS '收件人规则快照。';
COMMENT ON COLUMN "process_cc_record"."unique_key" IS '幂等键。';
COMMENT ON COLUMN "process_cc_record"."read_status" IS '阅读状态：UNREAD未读/READ已读。';
COMMENT ON COLUMN "process_cc_record"."read_time" IS '阅读时间。';
COMMENT ON COLUMN "process_cc_record"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "process_cc_record"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_cc_record"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "process_definition_config" IS '流程定义配置表';
COMMENT ON COLUMN "process_definition_config"."id" IS '主键ID，供流程定义配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "process_definition_config"."process_key" IS '流程标识。';
COMMENT ON COLUMN "process_definition_config"."process_name" IS '流程名称。';
COMMENT ON COLUMN "process_definition_config"."description" IS '说明：流程描述。';
COMMENT ON COLUMN "process_definition_config"."category" IS '流程分类。';
COMMENT ON COLUMN "process_definition_config"."version" IS '版本号。';
COMMENT ON COLUMN "process_definition_config"."status" IS '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用。';
COMMENT ON COLUMN "process_definition_config"."bpmn_xml" IS 'BPMN XML内容。';
COMMENT ON COLUMN "process_definition_config"."draft_revision" IS '草稿修订号：流程草稿修订号。';
COMMENT ON COLUMN "process_definition_config"."published_revision" IS '发布修订号：最近发布对应的草稿修订号。';
COMMENT ON COLUMN "process_definition_config"."draft_hash" IS '当前流程草稿 SHA-256。';
COMMENT ON COLUMN "process_definition_config"."published_draft_hash" IS '最近发布流程草稿 SHA-256。';
COMMENT ON COLUMN "process_definition_config"."base_published_version" IS '当前草稿基于的已发布版本。';
COMMENT ON COLUMN "process_definition_config"."created_by" IS '创建人。';
COMMENT ON COLUMN "process_definition_config"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_definition_config"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_definition_config"."deleted" IS '逻辑删除标记：是否删除 0-未删除 1-已删除。';
COMMENT ON COLUMN "process_definition_config"."entity_id" IS '绑定实体ID。';
COMMENT ON COLUMN "process_definition_config"."updated_by" IS '修改人：更新人。';
COMMENT ON TABLE "process_entity_status_mapping" IS '实体流程状态映射表';
COMMENT ON COLUMN "process_entity_status_mapping"."id" IS '主键ID。';
COMMENT ON COLUMN "process_entity_status_mapping"."process_config_id" IS '流程定义配置ID。';
COMMENT ON COLUMN "process_entity_status_mapping"."process_key" IS '流程标识。';
COMMENT ON COLUMN "process_entity_status_mapping"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "process_entity_status_mapping"."sequence_flow_id" IS '连线ID（BPMN中的sequenceFlowId）。';
COMMENT ON COLUMN "process_entity_status_mapping"."source_node_id" IS '源节点ID。';
COMMENT ON COLUMN "process_entity_status_mapping"."source_node_name" IS '源节点名称。';
COMMENT ON COLUMN "process_entity_status_mapping"."target_node_id" IS '目标节点ID。';
COMMENT ON COLUMN "process_entity_status_mapping"."target_node_name" IS '目标节点名称。';
COMMENT ON COLUMN "process_entity_status_mapping"."entity_status_code" IS '实体状态编码（关联entity_status表）。';
COMMENT ON COLUMN "process_entity_status_mapping"."condition_expression" IS '条件表达式。';
COMMENT ON COLUMN "process_entity_status_mapping"."sort_order" IS '排序号。';
COMMENT ON COLUMN "process_entity_status_mapping"."description" IS '说明描述。';
COMMENT ON COLUMN "process_entity_status_mapping"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "process_entity_status_mapping"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_entity_status_mapping"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_entity_status_mapping"."entity_status" IS '实体数据状态值（如:审批中、已通过、已驳回）。';
COMMENT ON COLUMN "process_entity_status_mapping"."status_category" IS '状态分类：NEW-新建流程状态、PROCESSING-审批中流程状态、COMPLETED-已完成流程状态、TERMINATED-终止流程状态。';
COMMENT ON TABLE "process_form_config" IS '流程节点表单配置表';
COMMENT ON COLUMN "process_form_config"."id" IS '主键ID，供流程节点表单配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "process_form_config"."node_config_id" IS '所属节点配置ID。';
COMMENT ON COLUMN "process_form_config"."form_name" IS '表单名称。';
COMMENT ON COLUMN "process_form_config"."form_key" IS '表单标识。';
COMMENT ON COLUMN "process_form_config"."description" IS '说明：表单描述。';
COMMENT ON COLUMN "process_form_config"."is_readonly" IS '是否只读。';
COMMENT ON COLUMN "process_form_config"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_form_config"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_form_config"."entity_form_id" IS '实体表单ID。';
COMMENT ON COLUMN "process_form_config"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "process_form_field_config" IS '流程节点表单字段表';
COMMENT ON COLUMN "process_form_field_config"."id" IS '主键ID，供流程节点表单字段表中的记录关联；由数据库分配';
COMMENT ON COLUMN "process_form_field_config"."form_config_id" IS '所属表单配置ID。';
COMMENT ON COLUMN "process_form_field_config"."field_name" IS '字段名称。';
COMMENT ON COLUMN "process_form_field_config"."field_key" IS '字段标识。';
COMMENT ON COLUMN "process_form_field_config"."field_type" IS '字段类型。';
COMMENT ON COLUMN "process_form_field_config"."is_required" IS '是否必填。';
COMMENT ON COLUMN "process_form_field_config"."default_value" IS '默认值。';
COMMENT ON COLUMN "process_form_field_config"."options_json" IS '选项配置JSON。';
COMMENT ON COLUMN "process_form_field_config"."validate_rules" IS '验证规则JSON。';
COMMENT ON COLUMN "process_form_field_config"."sort_order" IS '排序号：排序顺序。';
COMMENT ON COLUMN "process_form_field_config"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_form_field_config"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_form_field_config"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "process_node_approval" IS '流程节点审批配置表';
COMMENT ON COLUMN "process_node_approval"."id" IS '主键ID：流程节点审批配置。';
COMMENT ON COLUMN "process_node_approval"."process_config_id" IS '流程配置ID。';
COMMENT ON COLUMN "process_node_approval"."node_id" IS '节点ID（bpmn元素ID）。';
COMMENT ON COLUMN "process_node_approval"."node_name" IS '节点名称。';
COMMENT ON COLUMN "process_node_approval"."enabled" IS '是否启用审批意见：0-否 1-是。';
COMMENT ON COLUMN "process_node_approval"."comment_label" IS '审批意见标签。';
COMMENT ON COLUMN "process_node_approval"."options_json" IS '选项配置JSON：审批选项JSON。';
COMMENT ON COLUMN "process_node_approval"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_node_approval"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "process_node_approval_option" IS '流程节点审批选项表';
COMMENT ON COLUMN "process_node_approval_option"."id" IS '主键ID。';
COMMENT ON COLUMN "process_node_approval_option"."approval_config_id" IS '审批配置ID：关联的审批配置ID。';
COMMENT ON COLUMN "process_node_approval_option"."option_value" IS '选项值（如 approve/reject/return）。';
COMMENT ON COLUMN "process_node_approval_option"."option_label" IS '选项显示名称。';
COMMENT ON COLUMN "process_node_approval_option"."style_type" IS '按钮样式类型（如 primary/success/danger）。';
COMMENT ON COLUMN "process_node_approval_option"."show_comment" IS '是否显示意见输入：是否显示审批意见输入框。';
COMMENT ON COLUMN "process_node_approval_option"."remark_required" IS '是否要求备注：是否强制要求填写备注。';
COMMENT ON COLUMN "process_node_approval_option"."sort_order" IS '排序号。';
COMMENT ON COLUMN "process_node_approval_option"."option_document" IS '审批项扩展JSON文档。';
COMMENT ON COLUMN "process_node_approval_option"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_node_approval_option"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "process_node_assignee" IS '流程节点办理人配置表';
COMMENT ON COLUMN "process_node_assignee"."id" IS '主键ID，供流程节点办理人配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "process_node_assignee"."node_config_id" IS '所属节点配置ID。';
COMMENT ON COLUMN "process_node_assignee"."assignee_type" IS '审批人类型。';
COMMENT ON COLUMN "process_node_assignee"."assignee_value" IS '审批人值。';
COMMENT ON COLUMN "process_node_assignee"."assignee_name" IS '审批人显示名称。';
COMMENT ON COLUMN "process_node_assignee"."priority" IS '优先级。';
COMMENT ON COLUMN "process_node_assignee"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_node_assignee"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_node_assignee"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "process_node_config" IS '流程节点配置表';
COMMENT ON COLUMN "process_node_config"."id" IS '主键ID，供流程节点配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "process_node_config"."node_id" IS '节点ID。';
COMMENT ON COLUMN "process_node_config"."node_name" IS '节点名称。';
COMMENT ON COLUMN "process_node_config"."node_type" IS '节点类型。';
COMMENT ON COLUMN "process_node_config"."process_config_id" IS '所属流程配置ID。';
COMMENT ON COLUMN "process_node_config"."config_json" IS '扩展配置JSON。';
COMMENT ON COLUMN "process_node_config"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_node_config"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_node_config"."skip_node" IS '是否跳过节点。';
COMMENT ON COLUMN "process_node_config"."deleted" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "process_node_form" IS '流程节点实体表单绑定表';
COMMENT ON COLUMN "process_node_form"."id" IS '主键ID。';
COMMENT ON COLUMN "process_node_form"."process_config_id" IS '流程配置ID。';
COMMENT ON COLUMN "process_node_form"."node_id" IS '节点ID（bpmn元素ID）。';
COMMENT ON COLUMN "process_node_form"."node_name" IS '节点名称。';
COMMENT ON COLUMN "process_node_form"."form_id" IS '表单ID。';
COMMENT ON COLUMN "process_node_form"."is_readonly" IS '是否只读：0-否 1-是。';
COMMENT ON COLUMN "process_node_form"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_node_form"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_node_form"."sort_order" IS '排序号。';
COMMENT ON TABLE "process_operation_log" IS '流程操作日志表';
COMMENT ON COLUMN "process_operation_log"."id" IS '主键ID。';
COMMENT ON COLUMN "process_operation_log"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "process_operation_log"."task_id" IS '关联的任务ID。';
COMMENT ON COLUMN "process_operation_log"."operation_type" IS '操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC。';
COMMENT ON COLUMN "process_operation_log"."operator_id" IS '操作人ID。';
COMMENT ON COLUMN "process_operation_log"."operator_name" IS '操作人姓名。';
COMMENT ON COLUMN "process_operation_log"."operation_time" IS '操作时间。';
COMMENT ON COLUMN "process_operation_log"."operation_comment" IS '操作备注/审批意见。';
COMMENT ON COLUMN "process_operation_log"."old_value" IS '旧值（JSON）。';
COMMENT ON COLUMN "process_operation_log"."new_value" IS '新值（JSON）。';
COMMENT ON COLUMN "process_operation_log"."ip_address" IS '操作来源IP地址。';
COMMENT ON COLUMN "process_operation_log"."user_agent" IS 'User-Agent：操作来源客户端User-Agent。';
COMMENT ON COLUMN "process_operation_log"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_operation_log"."old_value_format" IS '原值格式。';
COMMENT ON COLUMN "process_operation_log"."new_value_format" IS '新值格式。';
COMMENT ON TABLE "process_person_resolver_definition" IS '受控人员解析器目录表';
COMMENT ON COLUMN "process_person_resolver_definition"."id" IS '主键ID：人员解析器定义ID。';
COMMENT ON COLUMN "process_person_resolver_definition"."resolver_code" IS '稳定解析器编码。';
COMMENT ON COLUMN "process_person_resolver_definition"."display_name" IS '中文名称。';
COMMENT ON COLUMN "process_person_resolver_definition"."description" IS '说明：用途说明。';
COMMENT ON COLUMN "process_person_resolver_definition"."bean_name" IS 'Spring Bean名称。';
COMMENT ON COLUMN "process_person_resolver_definition"."implementation_version" IS '实现版本。';
COMMENT ON COLUMN "process_person_resolver_definition"."contract_version" IS '平台契约版本。';
COMMENT ON COLUMN "process_person_resolver_definition"."supported_usages_document" IS '支持的用途集合文档。';
COMMENT ON COLUMN "process_person_resolver_definition"."extra_param_schema_document" IS '额外参数结构文档。';
COMMENT ON COLUMN "process_person_resolver_definition"."dynamic_extra_params" IS '是否允许动态extraParams。';
COMMENT ON COLUMN "process_person_resolver_definition"."enabled" IS '是否启用：是否允许在流程配置中选择。';
COMMENT ON COLUMN "process_person_resolver_definition"."revision" IS '修订号：目录修订号。';
COMMENT ON COLUMN "process_person_resolver_definition"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_person_resolver_definition"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_person_resolver_definition"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "process_status_sync_event" IS '流程实体状态同步事件表';
COMMENT ON COLUMN "process_status_sync_event"."id" IS '主键ID。';
COMMENT ON COLUMN "process_status_sync_event"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "process_status_sync_event"."event_type" IS '事件类型。';
COMMENT ON COLUMN "process_status_sync_event"."event_sequence" IS '事件序列。';
COMMENT ON COLUMN "process_status_sync_event"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "process_status_sync_event"."entity_record_id" IS '实体记录ID。';
COMMENT ON COLUMN "process_status_sync_event"."target_status" IS '目标状态。';
COMMENT ON COLUMN "process_status_sync_event"."status_category" IS '状态分类。';
COMMENT ON COLUMN "process_status_sync_event"."state" IS '处理状态。';
COMMENT ON COLUMN "process_status_sync_event"."applied_at" IS '应用时间。';
COMMENT ON COLUMN "process_status_sync_event"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_status_sync_event"."update_time" IS '更新时间。';
COMMENT ON TABLE "process_task" IS '平台流程任务表';
COMMENT ON COLUMN "process_task"."id" IS '主键ID，供平台流程任务表中的记录关联；由数据库分配';
COMMENT ON COLUMN "process_task"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "process_task"."process_definition_id" IS '流程定义ID。';
COMMENT ON COLUMN "process_task"."process_key" IS '流程标识。';
COMMENT ON COLUMN "process_task"."process_name" IS '流程名称。';
COMMENT ON COLUMN "process_task"."node_id" IS '节点ID。';
COMMENT ON COLUMN "process_task"."node_name" IS '节点名称。';
COMMENT ON COLUMN "process_task"."node_type" IS '节点类型。';
COMMENT ON COLUMN "process_task"."task_id" IS 'Flowable任务ID。';
COMMENT ON COLUMN "process_task"."business_key" IS '业务主键。';
COMMENT ON COLUMN "process_task"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "process_task"."entity_data_id" IS '实体数据ID。';
COMMENT ON COLUMN "process_task"."assignee_id" IS '执行人ID。';
COMMENT ON COLUMN "process_task"."assignee_name" IS '执行人姓名。';
COMMENT ON COLUMN "process_task"."assignee_type" IS '执行人类型: user/group/role。';
COMMENT ON COLUMN "process_task"."form_key" IS '表单标识。';
COMMENT ON COLUMN "process_task"."form_data" IS '表单数据。';
COMMENT ON COLUMN "process_task"."status" IS '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回。';
COMMENT ON COLUMN "process_task"."action" IS '操作: approve/reject/transfer/skip。';
COMMENT ON COLUMN "process_task"."action_label" IS '操作显示文本，如"同意，需要会签"。';
COMMENT ON COLUMN "process_task"."comment" IS '审批意见。';
COMMENT ON COLUMN "process_task"."start_time" IS '任务开始时间。';
COMMENT ON COLUMN "process_task"."end_time" IS '任务结束时间。';
COMMENT ON COLUMN "process_task"."due_time" IS '截止时间。';
COMMENT ON COLUMN "process_task"."sla_status" IS 'SLA综合状态。';
COMMENT ON COLUMN "process_task"."response_due_time" IS '响应截止时间：首次响应截止时间。';
COMMENT ON COLUMN "process_task"."duration" IS '处理耗时(毫秒)。';
COMMENT ON COLUMN "process_task"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_task"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "process_task"."deleted" IS '逻辑删除标记：删除标记: 0-正常 1-删除。';
COMMENT ON COLUMN "process_task"."timeout_hours" IS '超时时间。';
COMMENT ON COLUMN "process_task"."timeout_action" IS '超时策略。';
COMMENT ON COLUMN "process_task"."timeout_handled" IS '是否已处理超时。';
COMMENT ON COLUMN "process_task"."priority" IS '优先级。';
COMMENT ON COLUMN "process_task"."start_user_id" IS '发起人身份，兼容用户ID和用户名';
COMMENT ON COLUMN "process_task"."business_name" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "process_task"."business_code" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "process_task"."business_data_name" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "process_task"."business_current_task_name" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "process_task"."business_status" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "process_task"."inbox_summary_ready" IS '摘要已回填；0时保留旧读取语义';
COMMENT ON COLUMN "process_task"."inbox_identity_ready" IS '候选身份已从引擎最终状态同步';
COMMENT ON TABLE "process_task_add_sign" IS '运行时加签记录表';
COMMENT ON COLUMN "process_task_add_sign"."id" IS '主键ID。';
COMMENT ON COLUMN "process_task_add_sign"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "process_task_add_sign"."source_task_id" IS '触发加签的源任务ID。';
COMMENT ON COLUMN "process_task_add_sign"."node_id" IS '加签所在节点ID。';
COMMENT ON COLUMN "process_task_add_sign"."operation_type" IS '加签类型（如 before前加签/after后加签/parallel并行加签）。';
COMMENT ON COLUMN "process_task_add_sign"."operator_id" IS '操作人ID。';
COMMENT ON COLUMN "process_task_add_sign"."comment" IS '加签操作备注。';
COMMENT ON COLUMN "process_task_add_sign"."status" IS '状态：加签状态：PENDING-进行中，COMPLETED-已完成，CANCELED-已取消。';
COMMENT ON COLUMN "process_task_add_sign"."engine_execution_id" IS 'Flowable引擎执行实例ID。';
COMMENT ON COLUMN "process_task_add_sign"."source_completed" IS '原任务是否已提交。';
COMMENT ON COLUMN "process_task_add_sign"."source_action" IS '原任务提交动作。';
COMMENT ON COLUMN "process_task_add_sign"."source_action_label" IS '原任务动作名称。';
COMMENT ON COLUMN "process_task_add_sign"."source_comment" IS '原任务审批意见。';
COMMENT ON COLUMN "process_task_add_sign"."source_form_data" IS '原任务表单数据JSON。';
COMMENT ON COLUMN "process_task_add_sign"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_task_add_sign"."complete_time" IS '加签完成时间。';
COMMENT ON TABLE "process_task_add_sign_user" IS '运行时加签人员表';
COMMENT ON COLUMN "process_task_add_sign_user"."id" IS '主键ID。';
COMMENT ON COLUMN "process_task_add_sign_user"."add_sign_id" IS '加签记录ID：关联的加签操作ID。';
COMMENT ON COLUMN "process_task_add_sign_user"."user_id" IS '被加签的用户ID。';
COMMENT ON COLUMN "process_task_add_sign_user"."user_name_snapshot" IS '用户名称快照：加签时的用户姓名快照。';
COMMENT ON COLUMN "process_task_add_sign_user"."generated_task_id" IS '生成的任务ID：加签生成的Flowable任务ID。';
COMMENT ON COLUMN "process_task_add_sign_user"."status" IS '状态：用户任务状态：PENDING-待处理，COMPLETED-已完成。';
COMMENT ON COLUMN "process_task_add_sign_user"."sort_order" IS '排序号（控制串行加签顺序）。';
COMMENT ON COLUMN "process_task_add_sign_user"."complete_time" IS '用户处理完成时间。';
COMMENT ON TABLE "process_task_candidate_group" IS '任务候选组表';
COMMENT ON COLUMN "process_task_candidate_group"."id" IS '主键ID。';
COMMENT ON COLUMN "process_task_candidate_group"."process_task_id" IS '平台任务主键，关联 process_task.id，供候选组查询与清理';
COMMENT ON COLUMN "process_task_candidate_group"."group_code" IS '候选组编码（角色/部门等）。';
COMMENT ON COLUMN "process_task_candidate_group"."sort_order" IS '排序号，控制候选组处理顺序。';
COMMENT ON COLUMN "process_task_candidate_group"."create_time" IS '创建时间。';
COMMENT ON TABLE "process_task_candidate_user" IS '任务候选用户表';
COMMENT ON COLUMN "process_task_candidate_user"."id" IS '主键ID。';
COMMENT ON COLUMN "process_task_candidate_user"."process_task_id" IS '平台任务主键，关联 process_task.id，供候选用户查询与清理';
COMMENT ON COLUMN "process_task_candidate_user"."user_id" IS '候选用户ID。';
COMMENT ON COLUMN "process_task_candidate_user"."sort_order" IS '排序号，控制候选用户处理顺序。';
COMMENT ON COLUMN "process_task_candidate_user"."create_time" IS '创建时间。';
COMMENT ON TABLE "process_task_sla" IS '任务时效运行台账表';
COMMENT ON COLUMN "process_task_sla"."id" IS '主键ID。';
COMMENT ON COLUMN "process_task_sla"."task_id" IS '任务ID。';
COMMENT ON COLUMN "process_task_sla"."process_instance_id" IS '流程实例ID。';
COMMENT ON COLUMN "process_task_sla"."process_definition_id" IS '流程定义ID。';
COMMENT ON COLUMN "process_task_sla"."process_key" IS '流程键。';
COMMENT ON COLUMN "process_task_sla"."node_id" IS '节点ID。';
COMMENT ON COLUMN "process_task_sla"."node_name" IS '节点名称。';
COMMENT ON COLUMN "process_task_sla"."business_key" IS '业务键。';
COMMENT ON COLUMN "process_task_sla"."entity_code" IS '实体编码。';
COMMENT ON COLUMN "process_task_sla"."entity_data_id" IS '实体数据ID。';
COMMENT ON COLUMN "process_task_sla"."policy_code" IS '策略编码。';
COMMENT ON COLUMN "process_task_sla"."policy_version" IS '策略版本。';
COMMENT ON COLUMN "process_task_sla"."policy_snapshot_json" IS '策略快照JSON。';
COMMENT ON COLUMN "process_task_sla"."calendar_code" IS '日历编码。';
COMMENT ON COLUMN "process_task_sla"."calendar_version" IS '日历版本。';
COMMENT ON COLUMN "process_task_sla"."calendar_snapshot_json" IS '日历快照JSON。';
COMMENT ON COLUMN "process_task_sla"."timezone_id" IS '时区ID。';
COMMENT ON COLUMN "process_task_sla"."current_assignee_id" IS '当前办理人ID。';
COMMENT ON COLUMN "process_task_sla"."started_at" IS '开始时间。';
COMMENT ON COLUMN "process_task_sla"."responded_at" IS '已响应时间。';
COMMENT ON COLUMN "process_task_sla"."completed_at" IS '完成时间。';
COMMENT ON COLUMN "process_task_sla"."response_due_at" IS '响应截止时间。';
COMMENT ON COLUMN "process_task_sla"."completion_due_at" IS '完成截止时间。';
COMMENT ON COLUMN "process_task_sla"."response_remaining_minutes" IS '剩余响应分钟数。';
COMMENT ON COLUMN "process_task_sla"."completion_remaining_minutes" IS '剩余完成分钟数。';
COMMENT ON COLUMN "process_task_sla"."response_status" IS '响应状态。';
COMMENT ON COLUMN "process_task_sla"."completion_status" IS '完成状态。';
COMMENT ON COLUMN "process_task_sla"."overall_status" IS '整体状态。';
COMMENT ON COLUMN "process_task_sla"."pause_started_at" IS '暂停开始时间。';
COMMENT ON COLUMN "process_task_sla"."version" IS '版本号。';
COMMENT ON COLUMN "process_task_sla"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_task_sla"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "process_task_sla_event" IS '时效到期执行事件表';
COMMENT ON COLUMN "process_task_sla_event"."id" IS '主键ID。';
COMMENT ON COLUMN "process_task_sla_event"."sla_id" IS '时效ID。';
COMMENT ON COLUMN "process_task_sla_event"."task_id" IS '任务ID。';
COMMENT ON COLUMN "process_task_sla_event"."step_id" IS '步骤ID。';
COMMENT ON COLUMN "process_task_sla_event"."event_type" IS '事件类型。';
COMMENT ON COLUMN "process_task_sla_event"."metric_type" IS '指标类型。';
COMMENT ON COLUMN "process_task_sla_event"."trigger_at" IS '触发时间。';
COMMENT ON COLUMN "process_task_sla_event"."action_type" IS '动作类型。';
COMMENT ON COLUMN "process_task_sla_event"."action_config_snapshot" IS '动作配置快照。';
COMMENT ON COLUMN "process_task_sla_event"."execution_no" IS '执行编号。';
COMMENT ON COLUMN "process_task_sla_event"."max_executions" IS '最大执行次数。';
COMMENT ON COLUMN "process_task_sla_event"."status" IS '状态。';
COMMENT ON COLUMN "process_task_sla_event"."attempts" IS '尝试次数。';
COMMENT ON COLUMN "process_task_sla_event"."max_retries" IS '最大重试次数。';
COMMENT ON COLUMN "process_task_sla_event"."next_retry_time" IS '下一次重试时间。';
COMMENT ON COLUMN "process_task_sla_event"."owner_id" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "process_task_sla_event"."lease_token" IS '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。';
COMMENT ON COLUMN "process_task_sla_event"."lease_until" IS '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。';
COMMENT ON COLUMN "process_task_sla_event"."idempotency_key" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "process_task_sla_event"."result_json" IS '结果JSON。';
COMMENT ON COLUMN "process_task_sla_event"."error_message" IS '错误消息。';
COMMENT ON COLUMN "process_task_sla_event"."started_at" IS '开始时间。';
COMMENT ON COLUMN "process_task_sla_event"."finished_at" IS '完成时间。';
COMMENT ON COLUMN "process_task_sla_event"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_task_sla_event"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "process_task_sla_pause" IS '任务时效暂停历史表';
COMMENT ON COLUMN "process_task_sla_pause"."id" IS '主键ID。';
COMMENT ON COLUMN "process_task_sla_pause"."sla_id" IS '时效ID。';
COMMENT ON COLUMN "process_task_sla_pause"."task_id" IS '任务ID。';
COMMENT ON COLUMN "process_task_sla_pause"."pause_type" IS '暂停类型。';
COMMENT ON COLUMN "process_task_sla_pause"."reason" IS '原因。';
COMMENT ON COLUMN "process_task_sla_pause"."operator_id" IS '操作人ID。';
COMMENT ON COLUMN "process_task_sla_pause"."started_at" IS '开始时间。';
COMMENT ON COLUMN "process_task_sla_pause"."resumed_at" IS '恢复时间。';
COMMENT ON COLUMN "process_task_sla_pause"."duration_seconds" IS '耗时秒数。';
COMMENT ON COLUMN "process_task_sla_pause"."response_remaining_minutes" IS '剩余响应分钟数。';
COMMENT ON COLUMN "process_task_sla_pause"."completion_remaining_minutes" IS '剩余完成分钟数。';
COMMENT ON COLUMN "process_task_sla_pause"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_task_sla_pause"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "process_ui_release_binding" IS '流程与界面发布绑定表';
COMMENT ON COLUMN "process_ui_release_binding"."id" IS '主键ID：绑定记录ID。';
COMMENT ON COLUMN "process_ui_release_binding"."process_version_history_id" IS '流程发布历史ID。';
COMMENT ON COLUMN "process_ui_release_binding"."process_config_id" IS '流程配置ID。';
COMMENT ON COLUMN "process_ui_release_binding"."process_key" IS '流程标识。';
COMMENT ON COLUMN "process_ui_release_binding"."process_version" IS '流程版本号。';
COMMENT ON COLUMN "process_ui_release_binding"."deployment_id" IS 'Flowable部署ID。';
COMMENT ON COLUMN "process_ui_release_binding"."node_id" IS '流程节点ID。';
COMMENT ON COLUMN "process_ui_release_binding"."node_name" IS '流程节点名称。';
COMMENT ON COLUMN "process_ui_release_binding"."config_type" IS '配置类型。';
COMMENT ON COLUMN "process_ui_release_binding"."config_id" IS '表单或列表配置ID。';
COMMENT ON COLUMN "process_ui_release_binding"."pinned_release_id" IS '流程发布时固定的UI发布ID。';
COMMENT ON COLUMN "process_ui_release_binding"."pinned_release_version" IS '流程发布时固定的UI版本号。';
COMMENT ON COLUMN "process_ui_release_binding"."create_time" IS '创建时间。';
COMMENT ON TABLE "process_version_history" IS '流程发布历史表';
COMMENT ON COLUMN "process_version_history"."id" IS '主键ID，供流程发布历史表中的记录关联；由数据库分配';
COMMENT ON COLUMN "process_version_history"."process_config_id" IS '流程定义ID。';
COMMENT ON COLUMN "process_version_history"."process_key" IS '流程标识。';
COMMENT ON COLUMN "process_version_history"."process_name" IS '流程名称。';
COMMENT ON COLUMN "process_version_history"."version" IS '版本号。';
COMMENT ON COLUMN "process_version_history"."version_description" IS '版本描述/发布说明。';
COMMENT ON COLUMN "process_version_history"."bpmn_xml" IS 'BPMN XML内容。';
COMMENT ON COLUMN "process_version_history"."published_at" IS '发布时间。';
COMMENT ON COLUMN "process_version_history"."published_by" IS '发布人ID。';
COMMENT ON COLUMN "process_version_history"."deployment_id" IS 'Flowable部署ID。';
COMMENT ON COLUMN "process_version_history"."status" IS '状态：ACTIVE-有效，ARCHIVED-已归档。';
COMMENT ON COLUMN "process_version_history"."deleted" IS '逻辑删除标记：是否删除 0-未删除 1-已删除。';
COMMENT ON COLUMN "process_version_history"."node_forms_snapshot" IS '节点表单绑定快照JSON。';
COMMENT ON COLUMN "process_version_history"."created_by" IS '创建人。';
COMMENT ON COLUMN "process_version_history"."create_time" IS '创建时间。';
COMMENT ON COLUMN "process_version_history"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "storage_file_object" IS '文件对象归属表';
COMMENT ON COLUMN "storage_file_object"."id" IS '主键ID。';
COMMENT ON COLUMN "storage_file_object"."storage_url" IS '存储URL。';
COMMENT ON COLUMN "storage_file_object"."storage_key" IS '存储键。';
COMMENT ON COLUMN "storage_file_object"."owner_user_id" IS '所有者用户ID。';
COMMENT ON COLUMN "storage_file_object"."idempotency_key" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "storage_file_object"."request_hash" IS '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。';
COMMENT ON COLUMN "storage_file_object"."original_name" IS '原始名称。';
COMMENT ON COLUMN "storage_file_object"."content_type" IS '内容类型。';
COMMENT ON COLUMN "storage_file_object"."content_length" IS '内容长度。';
COMMENT ON COLUMN "storage_file_object"."deleted" IS '逻辑删除标记。';
COMMENT ON COLUMN "storage_file_object"."create_time" IS '创建时间。';
COMMENT ON COLUMN "storage_file_object"."update_time" IS '更新时间。';
COMMENT ON TABLE "sys_dict" IS '系统字典类型表';
COMMENT ON COLUMN "sys_dict"."id" IS '主键ID。';
COMMENT ON COLUMN "sys_dict"."dict_code" IS '字典编码。';
COMMENT ON COLUMN "sys_dict"."dict_name" IS '字典名称。';
COMMENT ON COLUMN "sys_dict"."description" IS '说明：描述。';
COMMENT ON COLUMN "sys_dict"."status" IS '状态：0-启用 1-禁用。';
COMMENT ON COLUMN "sys_dict"."sort" IS '排序。';
COMMENT ON COLUMN "sys_dict"."deleted" IS '逻辑删除标记：逻辑删除。';
COMMENT ON COLUMN "sys_dict"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_dict"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "sys_dict_item" IS '系统字典明细表';
COMMENT ON COLUMN "sys_dict_item"."id" IS '主键ID。';
COMMENT ON COLUMN "sys_dict_item"."dict_id" IS '所属字典ID。';
COMMENT ON COLUMN "sys_dict_item"."dict_code" IS '冗余：字典编码（便于直接查询）。';
COMMENT ON COLUMN "sys_dict_item"."parent_id" IS '父项ID，0表示顶级。';
COMMENT ON COLUMN "sys_dict_item"."item_code" IS '项编码。';
COMMENT ON COLUMN "sys_dict_item"."item_label" IS '项标签/显示文本。';
COMMENT ON COLUMN "sys_dict_item"."item_value" IS '项值。';
COMMENT ON COLUMN "sys_dict_item"."sort" IS '排序。';
COMMENT ON COLUMN "sys_dict_item"."status" IS '状态：0-启用 1-禁用。';
COMMENT ON COLUMN "sys_dict_item"."remark" IS '备注。';
COMMENT ON COLUMN "sys_dict_item"."deleted" IS '逻辑删除标记：逻辑删除。';
COMMENT ON COLUMN "sys_dict_item"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_dict_item"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "sys_external_system" IS '外部系统基础信息表';
COMMENT ON COLUMN "sys_external_system"."id" IS '记录ID：外部系统ID';
COMMENT ON COLUMN "sys_external_system"."system_name" IS '系统名称：外部系统名称';
COMMENT ON COLUMN "sys_external_system"."system_code" IS '系统编码：外部系统稳定编码，删除后也不得复用';
COMMENT ON COLUMN "sys_external_system"."status" IS '状态：0-启用 1-禁用';
COMMENT ON COLUMN "sys_external_system"."address" IS '系统地址：外部系统地址';
COMMENT ON COLUMN "sys_external_system"."description" IS '说明：描述';
COMMENT ON COLUMN "sys_external_system"."version" IS '版本号：乐观锁版本号';
COMMENT ON COLUMN "sys_external_system"."created_by" IS '创建人';
COMMENT ON COLUMN "sys_external_system"."updated_by" IS '更新人';
COMMENT ON COLUMN "sys_external_system"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_external_system"."update_time" IS '更新时间；更新时自动刷新。';
COMMENT ON COLUMN "sys_external_system"."deleted" IS '逻辑删除标记：逻辑删除：0-正常 1-删除';
COMMENT ON TABLE "sys_external_system_parameter" IS '外部系统扩展参数表';
COMMENT ON COLUMN "sys_external_system_parameter"."id" IS '记录ID：参数ID';
COMMENT ON COLUMN "sys_external_system_parameter"."external_system_id" IS '外部系统ID';
COMMENT ON COLUMN "sys_external_system_parameter"."parameter_name_zh" IS '参数中文名';
COMMENT ON COLUMN "sys_external_system_parameter"."parameter_name_en" IS '参数英文名';
COMMENT ON COLUMN "sys_external_system_parameter"."parameter_value" IS '参数值：普通配置参数值，敏感凭据应使用受控密钥存储';
COMMENT ON COLUMN "sys_external_system_parameter"."sort_order" IS '显示顺序';
COMMENT ON COLUMN "sys_external_system_parameter"."created_by" IS '创建人';
COMMENT ON COLUMN "sys_external_system_parameter"."updated_by" IS '更新人';
COMMENT ON COLUMN "sys_external_system_parameter"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_external_system_parameter"."update_time" IS '更新时间；更新时自动刷新。';
COMMENT ON COLUMN "sys_external_system_parameter"."deleted" IS '逻辑删除标记：逻辑删除：0-正常 1-删除';
COMMENT ON COLUMN "sys_external_system_parameter"."active_parameter_name_en" IS '活动参数英文名：仅活动参数参与英文名唯一约束；生成表达式：``(case when (`deleted` = 0) then `parameter_name_en` else NULL end)``';
COMMENT ON TABLE "sys_global_setting" IS '全局设置与个人偏好表';
COMMENT ON COLUMN "sys_global_setting"."id" IS '设置记录ID：由应用分配。修改、恢复默认时与 version 一起定位原记录；删除后重建使用新 ID，防止旧请求覆盖新设置。';
COMMENT ON COLUMN "sys_global_setting"."scope_type" IS '设置作用域：SYSTEM 为系统值，USER 为个人覆盖值；服务端按注册规则限制某项设置允许使用的作用域。';
COMMENT ON COLUMN "sys_global_setting"."owner_id" IS '设置归属：SYSTEM 固定为字符串 0；USER 保存 sys_user.id。个人接口从当前登录身份获取此值，不接受客户端指定其他用户。';
COMMENT ON COLUMN "sys_global_setting"."setting_key" IS '稳定设置键：程序按此键读取设置，例如 ui.layout.tabs_enabled。键须在 GlobalSettingRegistry 注册，单独向表中插入一个键不会自动增加功能。';
COMMENT ON COLUMN "sys_global_setting"."name" IS '设置名称：在管理界面展示该设置的用途。由注册定义提供，不能代替 setting_key 参与业务匹配。';
COMMENT ON COLUMN "sys_global_setting"."setting_value_type" IS '设置值类型：BOOLEAN、NUMBER、STRING 或 JSON；后端据此校验值，前端据此选择输入控件。同一设置键的系统值和个人值必须使用注册类型。';
COMMENT ON COLUMN "sys_global_setting"."setting_value" IS '设置值文本：应用按 JSON 格式序列化和解析，例如 true、20、双引号包裹的字符串或对象。不是数据库原生 JSON 列；拒绝顶层 null、类型不符及超过 16 KiB 的文本，合法的 false、0 和空字符串不能当成缺省。';
COMMENT ON COLUMN "sys_global_setting"."remark" IS '生效规则说明：展示取值含义、作用范围、继承关系和生效时机。说明本身不执行逻辑，实际行为由注册定义及设置使用方实现。';
COMMENT ON COLUMN "sys_global_setting"."version" IS '乐观锁版本：更新、删除按 ID 和旧版本共同匹配，成功更新后递增；版本不符时拒绝覆盖，避免多个页面互相覆盖偏好。';
COMMENT ON COLUMN "sys_global_setting"."created_by" IS '创建人：创建设置记录的用户 ID，由服务端填写；迁移初始化等无操作用户的场景可为空。';
COMMENT ON COLUMN "sys_global_setting"."updated_by" IS '最近修改人：最近一次修改设置的用户 ID，用于定位维护人员；历史操作仍由系统审计记录。';
COMMENT ON COLUMN "sys_global_setting"."create_time" IS '创建时间：记录首次建立时间，修改设置值时保留。';
COMMENT ON COLUMN "sys_global_setting"."update_time" IS '更新时间：更新时自动刷新，用于查看最后维护时间；并发控制使用 version。';
COMMENT ON TABLE "sys_group" IS '系统用户组表';
COMMENT ON COLUMN "sys_group"."id" IS '主键ID：组ID。';
COMMENT ON COLUMN "sys_group"."group_name" IS '组名称。';
COMMENT ON COLUMN "sys_group"."group_code" IS '组编码。';
COMMENT ON COLUMN "sys_group"."description" IS '说明：描述。';
COMMENT ON COLUMN "sys_group"."sort" IS '排序。';
COMMENT ON COLUMN "sys_group"."status" IS '状态（0启用 1禁用）。';
COMMENT ON COLUMN "sys_group"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_group"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "sys_group"."deleted" IS '逻辑删除标记：删除标志。';
COMMENT ON COLUMN "sys_group"."parent_id" IS '父组ID。';
COMMENT ON COLUMN "sys_group"."sort_order" IS '排序号。';
COMMENT ON TABLE "sys_menu" IS '菜单与功能权限表';
COMMENT ON COLUMN "sys_menu"."id" IS '主键ID：菜单ID。';
COMMENT ON COLUMN "sys_menu"."parent_id" IS '父菜单ID，0为顶级菜单。';
COMMENT ON COLUMN "sys_menu"."menu_name" IS '菜单名称。';
COMMENT ON COLUMN "sys_menu"."menu_type" IS '菜单类型：M-目录 C-菜单 F-按钮。';
COMMENT ON COLUMN "sys_menu"."icon" IS '菜单图标。';
COMMENT ON COLUMN "sys_menu"."sort" IS '显示排序。';
COMMENT ON COLUMN "sys_menu"."path" IS '路由地址。';
COMMENT ON COLUMN "sys_menu"."component" IS '组件路径。';
COMMENT ON COLUMN "sys_menu"."perm" IS '权限标识，如：system:user:list。';
COMMENT ON COLUMN "sys_menu"."status" IS '状态：0-禁用 1-启用。';
COMMENT ON COLUMN "sys_menu"."visible" IS '显示状态：0-隐藏 1-显示。';
COMMENT ON COLUMN "sys_menu"."keep_alive" IS '页面保活标记：是否缓存：0-不缓存 1-缓存。';
COMMENT ON COLUMN "sys_menu"."breadcrumb" IS '面包屑标记：是否显示面包屑：0-否 1-是。';
COMMENT ON COLUMN "sys_menu"."remark" IS '备注。';
COMMENT ON COLUMN "sys_menu"."deleted" IS '逻辑删除标记：是否删除：0-未删除 1-已删除。';
COMMENT ON COLUMN "sys_menu"."create_by" IS '创建人：创建者。';
COMMENT ON COLUMN "sys_menu"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_menu"."update_by" IS '修改人：更新者。';
COMMENT ON COLUMN "sys_menu"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "sys_menu"."is_frame" IS '是否外链：0-否 1-是。';
COMMENT ON COLUMN "sys_menu"."is_cache" IS '缓存标记：是否缓存：0-缓存 1-不缓存。';
COMMENT ON COLUMN "sys_menu"."query" IS '路由参数。';
COMMENT ON COLUMN "sys_menu"."entity_code" IS '关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表。';
COMMENT ON COLUMN "sys_menu"."resource_type" IS '菜单资源类型，ENTITY_LIST 表示动态实体列表。';
COMMENT ON COLUMN "sys_menu"."list_key" IS '实体列表稳定编码。';
COMMENT ON TABLE "sys_organization" IS '组织部门表';
COMMENT ON COLUMN "sys_organization"."id" IS '主键ID。';
COMMENT ON COLUMN "sys_organization"."org_code" IS '组织编码（唯一）。';
COMMENT ON COLUMN "sys_organization"."org_name" IS '组织名称。';
COMMENT ON COLUMN "sys_organization"."type" IS '类型：org-组织，dept-部门。';
COMMENT ON COLUMN "sys_organization"."business_level_code" IS '稳定业务层级编码，来源于 organization_business_level 字典。';
COMMENT ON COLUMN "sys_organization"."parent_id" IS '父级ID（顶级为0）。';
COMMENT ON COLUMN "sys_organization"."level" IS '层级（0为顶级）。';
COMMENT ON COLUMN "sys_organization"."path" IS '完整路径，如：/0/1/5/10/。';
COMMENT ON COLUMN "sys_organization"."sort_order" IS '排序号。';
COMMENT ON COLUMN "sys_organization"."leader_id" IS '负责人ID。';
COMMENT ON COLUMN "sys_organization"."leader_name" IS '负责人名称（冗余）。';
COMMENT ON COLUMN "sys_organization"."phone" IS '联系电话。';
COMMENT ON COLUMN "sys_organization"."email" IS '邮箱。';
COMMENT ON COLUMN "sys_organization"."address" IS '地址。';
COMMENT ON COLUMN "sys_organization"."status" IS '状态：0-启用，1-禁用。';
COMMENT ON COLUMN "sys_organization"."description" IS '说明：描述。';
COMMENT ON COLUMN "sys_organization"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_organization"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "sys_organization"."deleted" IS '逻辑删除标记：是否删除：0-未删除 1-已删除。';
COMMENT ON TABLE "sys_position" IS '全局职务定义表';
COMMENT ON COLUMN "sys_position"."id" IS '主键ID：全局职务定义；职务本身不携带组织范围，也不产生系统权限。';
COMMENT ON COLUMN "sys_position"."position_code" IS '职务编码。';
COMMENT ON COLUMN "sys_position"."position_name" IS '职务名称。';
COMMENT ON COLUMN "sys_position"."applicable_unit_type" IS '适用单元类型；CHECK 枚举：\\''ORG\\'',\\''DEPT\\'',\\''ANY\\''。';
COMMENT ON COLUMN "sys_position"."holder_mode" IS '任职者模式；CHECK 枚举：\\''SINGLE\\'',\\''MULTIPLE\\''。';
COMMENT ON COLUMN "sys_position"."built_in" IS '内置内置；CHECK 枚举：0,1。';
COMMENT ON COLUMN "sys_position"."status" IS '状态；CHECK 枚举：\\''ENABLED\\'',\\''DISABLED\\''。';
COMMENT ON COLUMN "sys_position"."sort_order" IS '排序号。';
COMMENT ON COLUMN "sys_position"."description" IS '说明。';
COMMENT ON COLUMN "sys_position"."revision" IS '修订号。';
COMMENT ON COLUMN "sys_position"."created_by" IS '创建人。';
COMMENT ON COLUMN "sys_position"."updated_by" IS '修改人。';
COMMENT ON COLUMN "sys_position"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_position"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "sys_position"."deleted" IS '逻辑删除标记；CHECK 枚举：0,1。';
COMMENT ON TABLE "sys_position_assignment" IS '组织职务任职表';
COMMENT ON COLUMN "sys_position_assignment"."id" IS '主键ID：用户在组织节点担任职务的一段不可覆盖任职事实。 有效区间统一为 [effectiveFrom, effectiveTo)，撤销通过 revoked 字段保留历史，重新任职必须新增记录。';
COMMENT ON COLUMN "sys_position_assignment"."position_id" IS '职务ID。';
COMMENT ON COLUMN "sys_position_assignment"."organization_unit_id" IS '组织单元ID。';
COMMENT ON COLUMN "sys_position_assignment"."user_id" IS '用户ID。';
COMMENT ON COLUMN "sys_position_assignment"."is_primary" IS '是否主职；CHECK 枚举：0,1。';
COMMENT ON COLUMN "sys_position_assignment"."sort_order" IS '排序号。';
COMMENT ON COLUMN "sys_position_assignment"."effective_from" IS '生效起始时间。';
COMMENT ON COLUMN "sys_position_assignment"."effective_to" IS '生效结束时间。';
COMMENT ON COLUMN "sys_position_assignment"."revoked_at" IS '撤销时间。';
COMMENT ON COLUMN "sys_position_assignment"."revoked_by" IS '撤销人。';
COMMENT ON COLUMN "sys_position_assignment"."revoke_reason" IS '撤销原因。';
COMMENT ON COLUMN "sys_position_assignment"."revision" IS '修订号。';
COMMENT ON COLUMN "sys_position_assignment"."created_by" IS '创建人。';
COMMENT ON COLUMN "sys_position_assignment"."updated_by" IS '修改人。';
COMMENT ON COLUMN "sys_position_assignment"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_position_assignment"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "sys_position_assignment_batch" IS '职务批量任命回执表';
COMMENT ON COLUMN "sys_position_assignment_batch"."id" IS '主键ID：已成功提交的批量任命幂等结果。';
COMMENT ON COLUMN "sys_position_assignment_batch"."idempotency_key" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "sys_position_assignment_batch"."request_hash" IS '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。';
COMMENT ON COLUMN "sys_position_assignment_batch"."assignment_ids_json" IS '任职ID集合JSON。';
COMMENT ON COLUMN "sys_position_assignment_batch"."created_by" IS '创建人。';
COMMENT ON COLUMN "sys_position_assignment_batch"."create_time" IS '创建时间。';
COMMENT ON TABLE "sys_role" IS '系统角色表';
COMMENT ON COLUMN "sys_role"."id" IS '主键ID：角色ID。';
COMMENT ON COLUMN "sys_role"."role_name" IS '角色名称。';
COMMENT ON COLUMN "sys_role"."role_code" IS '角色编码。';
COMMENT ON COLUMN "sys_role"."description" IS '说明：描述。';
COMMENT ON COLUMN "sys_role"."sort" IS '显示排序。';
COMMENT ON COLUMN "sys_role"."status" IS '状态（0启用 1禁用）。';
COMMENT ON COLUMN "sys_role"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_role"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "sys_role"."deleted" IS '逻辑删除标记：删除标志（0正常 1删除）。';
COMMENT ON COLUMN "sys_role"."sort_order" IS '排序号。';
COMMENT ON TABLE "sys_role_menu" IS '角色菜单权限关联表';
COMMENT ON COLUMN "sys_role_menu"."id" IS '主键ID。';
COMMENT ON COLUMN "sys_role_menu"."role_id" IS '角色ID。';
COMMENT ON COLUMN "sys_role_menu"."menu_id" IS '菜单ID。';
COMMENT ON COLUMN "sys_role_menu"."create_time" IS '创建时间。';
COMMENT ON TABLE "sys_user" IS '系统用户表';
COMMENT ON COLUMN "sys_user"."id" IS '主键ID：用户ID。';
COMMENT ON COLUMN "sys_user"."username" IS '用户名。';
COMMENT ON COLUMN "sys_user"."nickname" IS '昵称。';
COMMENT ON COLUMN "sys_user"."password" IS '密码。';
COMMENT ON COLUMN "sys_user"."email" IS '邮箱。';
COMMENT ON COLUMN "sys_user"."phone" IS '手机号。';
COMMENT ON COLUMN "sys_user"."avatar" IS '头像。';
COMMENT ON COLUMN "sys_user"."status" IS '状态（0启用 1禁用）。';
COMMENT ON COLUMN "sys_user"."create_time" IS '创建时间。';
COMMENT ON COLUMN "sys_user"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "sys_user"."deleted" IS '逻辑删除标记：删除标志（0正常 1删除）。';
COMMENT ON COLUMN "sys_user"."org_id" IS '组织ID。';
COMMENT ON COLUMN "sys_user"."dept_id" IS '部门ID。';
COMMENT ON COLUMN "sys_user"."password_reset_required" IS '是否必须在继续使用系统前修改密码。';
COMMENT ON COLUMN "sys_user"."token_version" IS '令牌版本：用户令牌撤销版本；递增后此前签发的会话需按安全校验失效。';
COMMENT ON TABLE "sys_user_group" IS '用户用户组关联表';
COMMENT ON COLUMN "sys_user_group"."id" IS '主键ID。';
COMMENT ON COLUMN "sys_user_group"."user_id" IS '用户ID。';
COMMENT ON COLUMN "sys_user_group"."group_id" IS '组ID。';
COMMENT ON COLUMN "sys_user_group"."create_time" IS '创建时间。';
COMMENT ON TABLE "sys_user_role" IS '用户角色关联表';
COMMENT ON COLUMN "sys_user_role"."id" IS '主键ID。';
COMMENT ON COLUMN "sys_user_role"."user_id" IS '用户ID。';
COMMENT ON COLUMN "sys_user_role"."role_id" IS '角色ID。';
COMMENT ON COLUMN "sys_user_role"."create_time" IS '创建时间。';
COMMENT ON TABLE "system_operation_log" IS '系统关键操作审计表';
COMMENT ON COLUMN "system_operation_log"."id" IS '主键ID：只追加的系统关键操作审计日志。';
COMMENT ON COLUMN "system_operation_log"."event_id" IS '事件ID。';
COMMENT ON COLUMN "system_operation_log"."operation_id" IS '业务操作ID；同一次跨模块操作共享。';
COMMENT ON COLUMN "system_operation_log"."trace_id" IS '追踪ID。';
COMMENT ON COLUMN "system_operation_log"."parent_operation_id" IS '父业务操作ID。';
COMMENT ON COLUMN "system_operation_log"."source_system" IS '权威来源系统/模块。';
COMMENT ON COLUMN "system_operation_log"."source_type" IS '权威来源记录类型。';
COMMENT ON COLUMN "system_operation_log"."source_id" IS '权威来源记录ID。';
COMMENT ON COLUMN "system_operation_log"."source_event_id" IS '权威来源事件ID。';
COMMENT ON COLUMN "system_operation_log"."module_code" IS '模块编码。';
COMMENT ON COLUMN "system_operation_log"."operation_code" IS '操作编码。';
COMMENT ON COLUMN "system_operation_log"."operation_name" IS '操作名称。';
COMMENT ON COLUMN "system_operation_log"."risk_level" IS '风险级别。';
COMMENT ON COLUMN "system_operation_log"."result" IS '结果。';
COMMENT ON COLUMN "system_operation_log"."operator_id" IS '操作人ID。';
COMMENT ON COLUMN "system_operation_log"."operator_name" IS '操作人名称。';
COMMENT ON COLUMN "system_operation_log"."operator_ip" IS '操作人IP。';
COMMENT ON COLUMN "system_operation_log"."user_agent" IS 'User-Agent。';
COMMENT ON COLUMN "system_operation_log"."request_method" IS '请求方法。';
COMMENT ON COLUMN "system_operation_log"."request_path" IS '请求路径。';
COMMENT ON COLUMN "system_operation_log"."target_type" IS '目标类型。';
COMMENT ON COLUMN "system_operation_log"."target_id" IS '目标ID。';
COMMENT ON COLUMN "system_operation_log"."target_name" IS '目标名称。';
COMMENT ON COLUMN "system_operation_log"."summary" IS '摘要。';
COMMENT ON COLUMN "system_operation_log"."before_json" IS '之前JSON。';
COMMENT ON COLUMN "system_operation_log"."after_json" IS '之后JSON。';
COMMENT ON COLUMN "system_operation_log"."changed_fields_json" IS '变更标记字段集合JSON。';
COMMENT ON COLUMN "system_operation_log"."payload_truncated" IS '载荷是否被截断。';
COMMENT ON COLUMN "system_operation_log"."error_code" IS '错误编码。';
COMMENT ON COLUMN "system_operation_log"."error_message" IS '错误消息。';
COMMENT ON COLUMN "system_operation_log"."duration_ms" IS '耗时毫秒。';
COMMENT ON COLUMN "system_operation_log"."create_time" IS '创建时间。';
COMMENT ON TABLE "task_sla_escalation_step" IS '时效提醒升级步骤表';
COMMENT ON COLUMN "task_sla_escalation_step"."id" IS '主键ID。';
COMMENT ON COLUMN "task_sla_escalation_step"."policy_id" IS '策略ID。';
COMMENT ON COLUMN "task_sla_escalation_step"."step_name" IS '步骤名称。';
COMMENT ON COLUMN "task_sla_escalation_step"."metric_type" IS '指标类型。';
COMMENT ON COLUMN "task_sla_escalation_step"."trigger_type" IS '触发类型。';
COMMENT ON COLUMN "task_sla_escalation_step"."offset_minutes" IS '偏移分钟数。';
COMMENT ON COLUMN "task_sla_escalation_step"."repeat_interval_minutes" IS '重复间隔分钟数。';
COMMENT ON COLUMN "task_sla_escalation_step"."max_executions" IS '最大执行次数。';
COMMENT ON COLUMN "task_sla_escalation_step"."action_type" IS '动作类型。';
COMMENT ON COLUMN "task_sla_escalation_step"."template_code" IS '模板编码。';
COMMENT ON COLUMN "task_sla_escalation_step"."recipient_config_json" IS '接收人配置JSON。';
COMMENT ON COLUMN "task_sla_escalation_step"."target_config_json" IS '目标配置JSON。';
COMMENT ON COLUMN "task_sla_escalation_step"."sort_order" IS '排序号。';
COMMENT ON COLUMN "task_sla_escalation_step"."enabled" IS '是否启用。';
COMMENT ON COLUMN "task_sla_escalation_step"."create_time" IS '创建时间。';
COMMENT ON COLUMN "task_sla_escalation_step"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "task_sla_policy" IS '用户任务时效策略表';
COMMENT ON COLUMN "task_sla_policy"."id" IS '主键ID。';
COMMENT ON COLUMN "task_sla_policy"."policy_code" IS '策略编码。';
COMMENT ON COLUMN "task_sla_policy"."policy_name" IS '策略名称。';
COMMENT ON COLUMN "task_sla_policy"."description" IS '说明。';
COMMENT ON COLUMN "task_sla_policy"."version" IS '版本号。';
COMMENT ON COLUMN "task_sla_policy"."response_target_minutes" IS '响应目标分钟数。';
COMMENT ON COLUMN "task_sla_policy"."completion_target_minutes" IS '完成目标分钟数。';
COMMENT ON COLUMN "task_sla_policy"."response_time_basis" IS '响应时间口径。';
COMMENT ON COLUMN "task_sla_policy"."completion_time_basis" IS '完成时间口径。';
COMMENT ON COLUMN "task_sla_policy"."allow_manual_pause" IS '允许手动暂停。';
COMMENT ON COLUMN "task_sla_policy"."pause_on_process_suspend" IS '暂停时间流程挂起。';
COMMENT ON COLUMN "task_sla_policy"."max_pause_minutes" IS '最大暂停分钟数。';
COMMENT ON COLUMN "task_sla_policy"."status" IS '状态。';
COMMENT ON COLUMN "task_sla_policy"."created_by" IS '创建人。';
COMMENT ON COLUMN "task_sla_policy"."create_time" IS '创建时间。';
COMMENT ON COLUMN "task_sla_policy"."updated_by" IS '修改人。';
COMMENT ON COLUMN "task_sla_policy"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "task_sla_policy"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "ui_component_template" IS 'UI组件模板目录表';
COMMENT ON COLUMN "ui_component_template"."id" IS '主键ID：模板ID。';
COMMENT ON COLUMN "ui_component_template"."template_key" IS '稳定模板编码。';
COMMENT ON COLUMN "ui_component_template"."template_name" IS '模板名称。';
COMMENT ON COLUMN "ui_component_template"."template_type" IS '模板类型。';
COMMENT ON COLUMN "ui_component_template"."current_version" IS '当前版本。';
COMMENT ON COLUMN "ui_component_template"."status" IS '状态。';
COMMENT ON COLUMN "ui_component_template"."create_time" IS '创建时间。';
COMMENT ON COLUMN "ui_component_template"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ui_component_template"."deleted" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON TABLE "ui_component_template_version" IS 'UI组件模板版本表';
COMMENT ON COLUMN "ui_component_template_version"."id" IS '主键ID：模板版本ID。';
COMMENT ON COLUMN "ui_component_template_version"."template_id" IS '模板ID。';
COMMENT ON COLUMN "ui_component_template_version"."version" IS '版本号。';
COMMENT ON COLUMN "ui_component_template_version"."snapshot_document" IS '不可变模板快照JSON文档。';
COMMENT ON COLUMN "ui_component_template_version"."content_hash" IS 'SHA-256内容哈希。';
COMMENT ON COLUMN "ui_component_template_version"."description" IS '说明：版本说明。';
COMMENT ON COLUMN "ui_component_template_version"."created_by" IS '创建人。';
COMMENT ON COLUMN "ui_component_template_version"."create_time" IS '创建时间。';
COMMENT ON TABLE "ui_config_hotfix_request" IS 'UI热修复发布与观察记录表';
COMMENT ON COLUMN "ui_config_hotfix_request"."id" IS '主键ID：HOTFIX申请ID。';
COMMENT ON COLUMN "ui_config_hotfix_request"."config_type" IS '配置类型。';
COMMENT ON COLUMN "ui_config_hotfix_request"."config_id" IS '配置ID。';
COMMENT ON COLUMN "ui_config_hotfix_request"."draft_hash" IS '申请时草稿哈希。';
COMMENT ON COLUMN "ui_config_hotfix_request"."active_release_id" IS '申请时激活发布ID。';
COMMENT ON COLUMN "ui_config_hotfix_request"."target_hash" IS '影响目标摘要。';
COMMENT ON COLUMN "ui_config_hotfix_request"."impact_token_hash" IS '预检令牌摘要。';
COMMENT ON COLUMN "ui_config_hotfix_request"."risk_level" IS '风险级别。';
COMMENT ON COLUMN "ui_config_hotfix_request"."reason" IS '变更原因。';
COMMENT ON COLUMN "ui_config_hotfix_request"."ticket_ref" IS '关联工单。';
COMMENT ON COLUMN "ui_config_hotfix_request"."impact_document" IS '申请时影响预览JSON。';
COMMENT ON COLUMN "ui_config_hotfix_request"."applicant_id" IS '申请人ID。';
COMMENT ON COLUMN "ui_config_hotfix_request"."applicant_name" IS '申请人姓名。';
COMMENT ON COLUMN "ui_config_hotfix_request"."window_start" IS '允许发布时间窗口开始。';
COMMENT ON COLUMN "ui_config_hotfix_request"."window_end" IS '允许发布时间窗口结束。';
COMMENT ON COLUMN "ui_config_hotfix_request"."review_required" IS '是否需要人工复核：是否需要独立复核；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "ui_config_hotfix_request"."status" IS '状态。';
COMMENT ON COLUMN "ui_config_hotfix_request"."open_slot" IS '每个配置仅允许一个开放申请；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "ui_config_hotfix_request"."reviewer_id" IS '复核人ID；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "ui_config_hotfix_request"."reviewer_name" IS '复核人名称：复核人姓名；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "ui_config_hotfix_request"."review_comment" IS '复核意见；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "ui_config_hotfix_request"."reviewed_at" IS '复核时间；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "ui_config_hotfix_request"."release_id" IS '实际发布ID。';
COMMENT ON COLUMN "ui_config_hotfix_request"."published_at" IS '发布时间：实际发布时间。';
COMMENT ON COLUMN "ui_config_hotfix_request"."observation_start" IS '观察窗口开始。';
COMMENT ON COLUMN "ui_config_hotfix_request"."observation_end" IS '观察窗口结束。';
COMMENT ON COLUMN "ui_config_hotfix_request"."observation_status" IS '观察状态。';
COMMENT ON COLUMN "ui_config_hotfix_request"."rolled_back_by" IS '回滚人ID。';
COMMENT ON COLUMN "ui_config_hotfix_request"."rolled_back_at" IS '回滚时间。';
COMMENT ON COLUMN "ui_config_hotfix_request"."rollback_reason" IS '回滚原因。';
COMMENT ON COLUMN "ui_config_hotfix_request"."cancelled_by" IS '取消人ID。';
COMMENT ON COLUMN "ui_config_hotfix_request"."cancelled_at" IS '取消时间。';
COMMENT ON COLUMN "ui_config_hotfix_request"."cancel_reason" IS '取消原因。';
COMMENT ON COLUMN "ui_config_hotfix_request"."create_time" IS '创建时间。';
COMMENT ON COLUMN "ui_config_hotfix_request"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ui_config_hotfix_target" IS 'UI热修复目标快照表';
COMMENT ON COLUMN "ui_config_hotfix_target"."id" IS '主键ID：热修复目标ID。';
COMMENT ON COLUMN "ui_config_hotfix_target"."hotfix_release_id" IS '热修复发布ID。';
COMMENT ON COLUMN "ui_config_hotfix_target"."config_type" IS '配置类型。';
COMMENT ON COLUMN "ui_config_hotfix_target"."config_id" IS '配置ID。';
COMMENT ON COLUMN "ui_config_hotfix_target"."process_version_history_id" IS '目标流程发布历史ID。';
COMMENT ON COLUMN "ui_config_hotfix_target"."pinned_release_id" IS '目标原始钉定发布ID。';
COMMENT ON COLUMN "ui_config_hotfix_target"."pinned_release_version" IS '目标原始钉定版本号。';
COMMENT ON COLUMN "ui_config_hotfix_target"."previous_target_id" IS '上一有效热修复目标ID。';
COMMENT ON COLUMN "ui_config_hotfix_target"."effective_snapshot_document" IS '目标有效完整快照JSON文档。';
COMMENT ON COLUMN "ui_config_hotfix_target"."effective_content_hash" IS '目标有效快照SHA-256。';
COMMENT ON COLUMN "ui_config_hotfix_target"."status" IS '状态。';
COMMENT ON COLUMN "ui_config_hotfix_target"."active_slot" IS '保证同一流程版本只有一个有效目标；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "ui_config_hotfix_target"."activated_by" IS '激活人。';
COMMENT ON COLUMN "ui_config_hotfix_target"."activated_at" IS '激活时间。';
COMMENT ON COLUMN "ui_config_hotfix_target"."rolled_back_by" IS '撤回人。';
COMMENT ON COLUMN "ui_config_hotfix_target"."rolled_back_at" IS '撤回时间。';
COMMENT ON TABLE "ui_config_release" IS '表单列表发布快照表';
COMMENT ON COLUMN "ui_config_release"."id" IS '主键ID：发布快照ID。';
COMMENT ON COLUMN "ui_config_release"."config_type" IS '配置类型。';
COMMENT ON COLUMN "ui_config_release"."config_id" IS '表单或列表配置ID。';
COMMENT ON COLUMN "ui_config_release"."version" IS '版本号：不可变版本号。';
COMMENT ON COLUMN "ui_config_release"."snapshot_document" IS '完整运行时快照JSON文档。';
COMMENT ON COLUMN "ui_config_release"."content_hash" IS 'SHA-256内容哈希。';
COMMENT ON COLUMN "ui_config_release"."status" IS '状态。';
COMMENT ON COLUMN "ui_config_release"."active_slot" IS '保证同一配置只有一个激活版本；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "ui_config_release"."description" IS '说明：发布说明。';
COMMENT ON COLUMN "ui_config_release"."published_by" IS '发布人。';
COMMENT ON COLUMN "ui_config_release"."published_at" IS '发布时间。';
COMMENT ON COLUMN "ui_config_release"."release_mode" IS '发布模式。';
COMMENT ON COLUMN "ui_config_release"."base_release_id" IS '热修复基线发布ID。';
COMMENT ON COLUMN "ui_config_release"."risk_level" IS '风险级别。';
COMMENT ON COLUMN "ui_config_release"."rollout_scope" IS '发布策略范围。';
COMMENT ON COLUMN "ui_config_release"."patch_document" IS '稳定ID语义补丁JSON文档。';
COMMENT ON COLUMN "ui_config_release"."override_risk" IS '是否经授权覆盖REVIEW风险。';
COMMENT ON COLUMN "ui_config_release"."override_reason" IS '风险覆盖原因。';
COMMENT ON TABLE "ui_config_release_audit" IS 'UI发布审计表';
COMMENT ON COLUMN "ui_config_release_audit"."id" IS '主键ID：审计ID。';
COMMENT ON COLUMN "ui_config_release_audit"."config_type" IS '配置类型。';
COMMENT ON COLUMN "ui_config_release_audit"."config_id" IS '配置ID。';
COMMENT ON COLUMN "ui_config_release_audit"."release_id" IS '关联发布ID。';
COMMENT ON COLUMN "ui_config_release_audit"."operation" IS '操作。';
COMMENT ON COLUMN "ui_config_release_audit"."risk_level" IS '风险级别。';
COMMENT ON COLUMN "ui_config_release_audit"."actor_id" IS '操作人ID。';
COMMENT ON COLUMN "ui_config_release_audit"."actor_name" IS '操作人名称。';
COMMENT ON COLUMN "ui_config_release_audit"."reason" IS '发布或覆盖原因。';
COMMENT ON COLUMN "ui_config_release_audit"."trace_id" IS '业务追踪ID。';
COMMENT ON COLUMN "ui_config_release_audit"."detail_document" IS '影响范围与差异JSON文档。';
COMMENT ON COLUMN "ui_config_release_audit"."create_time" IS '创建时间。';
COMMENT ON TABLE "ui_event_binding" IS '统一UI事件绑定表';
COMMENT ON COLUMN "ui_event_binding"."id" IS '主键ID：事件绑定链ID。';
COMMENT ON COLUMN "ui_event_binding"."owner_type" IS '所有者类型。';
COMMENT ON COLUMN "ui_event_binding"."owner_id" IS '持有者ID：所属实体、表单或列表ID。';
COMMENT ON COLUMN "ui_event_binding"."target_type" IS '目标类型。';
COMMENT ON COLUMN "ui_event_binding"."target_key" IS '目标标识：字段节点或按钮稳定编码，OWNER为空串。';
COMMENT ON COLUMN "ui_event_binding"."event_code" IS '统一业务事件编码。';
COMMENT ON COLUMN "ui_event_binding"."inheritance_mode" IS '继承模式。';
COMMENT ON COLUMN "ui_event_binding"."steps_document" IS '步骤集合文档：BEFORE/REPLACE/AFTER有序步骤JSON数组。';
COMMENT ON COLUMN "ui_event_binding"."revision" IS '修订号：草稿修订号。';
COMMENT ON COLUMN "ui_event_binding"."enabled" IS '是否启用。';
COMMENT ON COLUMN "ui_event_binding"."create_time" IS '创建时间。';
COMMENT ON COLUMN "ui_event_binding"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ui_event_binding"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "ui_extension_definition" IS '统一扩展定义目录表';
COMMENT ON COLUMN "ui_extension_definition"."id" IS '主键ID：扩展定义ID。';
COMMENT ON COLUMN "ui_extension_definition"."extension_type" IS '扩展类型：本表取值为 `FORM`/`NODE`/`FIELD`/`LIST`/`INTERFACE`；管理层展示时把前四类投影为 `UI_*`，并聚合另表中的流程动作和人员解析器。';
COMMENT ON COLUMN "ui_extension_definition"."extension_key" IS '扩展稳定编码：完整能力的唯一稳定编码。';
COMMENT ON COLUMN "ui_extension_definition"."display_name" IS '显示名称。';
COMMENT ON COLUMN "ui_extension_definition"."version" IS '版本号：扩展实现版本。';
COMMENT ON COLUMN "ui_extension_definition"."snapshot_version" IS '配置快照协议版本。';
COMMENT ON COLUMN "ui_extension_definition"."visibility_scope" IS '适用范围：GLOBAL/ENTITY。';
COMMENT ON COLUMN "ui_extension_definition"."entity_codes_document" IS '指定适用实体编码JSON数组。';
COMMENT ON COLUMN "ui_extension_definition"."supported_modes_document" IS '支持的运行模式JSON数组。';
COMMENT ON COLUMN "ui_extension_definition"."supported_node_types_document" IS '支持的节点类型JSON数组。';
COMMENT ON COLUMN "ui_extension_definition"."supported_bindings_document" IS '支持的绑定类型JSON数组。';
COMMENT ON COLUMN "ui_extension_definition"."config_schema_document" IS '配置Schema JSON文档。';
COMMENT ON COLUMN "ui_extension_definition"."capabilities_document" IS '扩展能力声明JSON文档。';
COMMENT ON COLUMN "ui_extension_definition"."implementation_type" IS '接口实现类型：字典、静态数据、Provider、运行时上下文或结构化计算。';
COMMENT ON COLUMN "ui_extension_definition"."provider_code" IS 'Provider编码：已注册 Provider 的稳定编码。';
COMMENT ON COLUMN "ui_extension_definition"."scope_type" IS '接口作用域：`GLOBAL`/`ENTITY`/`FORM`/`LIST`。';
COMMENT ON COLUMN "ui_extension_definition"."scope_id" IS '作用域资源ID：非全局接口对应的实体、表单或列表 ID。';
COMMENT ON COLUMN "ui_extension_definition"."implementation_config_document" IS '接口实现配置：受控实现配置 JSON。';
COMMENT ON COLUMN "ui_extension_definition"."execution_policy_document" IS '接口执行策略：超时、缓存、失败回退等策略 JSON。';
COMMENT ON COLUMN "ui_extension_definition"."input_schema_document" IS '输入 Schema：接口输入 JSON Schema。';
COMMENT ON COLUMN "ui_extension_definition"."output_schema_document" IS '输出 Schema：接口输出 JSON Schema。';
COMMENT ON COLUMN "ui_extension_definition"."interface_kind" IS '接口读写类型：`READ`/`WRITE`。';
COMMENT ON COLUMN "ui_extension_definition"."interface_context_type" IS '调用上下文：`FORM`/`LIST`/`ENTITY`。';
COMMENT ON COLUMN "ui_extension_definition"."provider_operation_code" IS 'Provider内部路由：仅供后端实现分派，不是设计器的第二层选项。';
COMMENT ON COLUMN "ui_extension_definition"."legacy_service_id" IS '历史服务ID：仅用于不可变历史快照兼容，新契约不输出。';
COMMENT ON COLUMN "ui_extension_definition"."status" IS '状态。';
COMMENT ON COLUMN "ui_extension_definition"."revision" IS '修订号：定义修订号。';
COMMENT ON COLUMN "ui_extension_definition"."create_time" IS '创建时间。';
COMMENT ON COLUMN "ui_extension_definition"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ui_extension_definition"."deleted" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON TABLE "ui_hotfix_observation_metric" IS 'UI热修复观察指标表';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."id" IS '主键ID：指标ID。';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."request_id" IS 'HOTFIX申请ID。';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."release_id" IS 'HOTFIX发布ID。';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."metric_code" IS '指标编码。';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."total_count" IS '观察总次数。';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."failure_count" IS '失败次数。';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."last_error" IS '最近失败摘要。';
COMMENT ON COLUMN "ui_hotfix_observation_metric"."last_observed_at" IS '最近观察时间。';
COMMENT ON TABLE "ui_view_composition" IS '表单列表关联内容表';
COMMENT ON COLUMN "ui_view_composition"."id" IS '主键ID：表单或列表设计器中的“关联内容”草稿记录。 目标内容、关联方式、允许操作和特殊处理统一保存在经过严格校验的 config_document 中；独立记录使关联内容可以稳定排序、乐观并发更新， 并能作为宿主发布快照的一部分参与差异比较和撤销。';
COMMENT ON COLUMN "ui_view_composition"."owner_type" IS '所有者类型；CHECK 枚举：\\''FORM\\'', \\''LIST\\''。';
COMMENT ON COLUMN "ui_view_composition"."owner_id" IS '持有者ID：表单或列表配置ID。';
COMMENT ON COLUMN "ui_view_composition"."composition_key" IS '宿主内稳定业务标识。';
COMMENT ON COLUMN "ui_view_composition"."anchor_type" IS '锚点类型。';
COMMENT ON COLUMN "ui_view_composition"."anchor_key" IS '非OWNER挂载点的稳定标识。';
COMMENT ON COLUMN "ui_view_composition"."config_document" IS '经白名单校验的关联内容配置JSON。';
COMMENT ON COLUMN "ui_view_composition"."order_key" IS '同一挂载点内的稳定排序键。';
COMMENT ON COLUMN "ui_view_composition"."revision" IS '修订号：乐观锁修订号。';
COMMENT ON COLUMN "ui_view_composition"."create_time" IS '创建时间。';
COMMENT ON COLUMN "ui_view_composition"."update_time" IS '更新时间。';
COMMENT ON COLUMN "ui_view_composition"."deleted" IS '逻辑删除标记；CHECK 枚举：0, 1。';
COMMENT ON COLUMN "ui_view_composition"."active_composition_key" IS '仅活动记录参与宿主内编码唯一约束；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "work_calendar" IS '工作日历表';
COMMENT ON COLUMN "work_calendar"."id" IS '主键ID。';
COMMENT ON COLUMN "work_calendar"."calendar_code" IS '日历编码。';
COMMENT ON COLUMN "work_calendar"."calendar_name" IS '日历名称。';
COMMENT ON COLUMN "work_calendar"."timezone_id" IS '时区ID。';
COMMENT ON COLUMN "work_calendar"."description" IS '说明。';
COMMENT ON COLUMN "work_calendar"."version" IS '版本号。';
COMMENT ON COLUMN "work_calendar"."default_flag" IS '是否默认日历。';
COMMENT ON COLUMN "work_calendar"."status" IS '状态。';
COMMENT ON COLUMN "work_calendar"."effective_from" IS '生效起始时间。';
COMMENT ON COLUMN "work_calendar"."effective_to" IS '生效结束时间。';
COMMENT ON COLUMN "work_calendar"."created_by" IS '创建人。';
COMMENT ON COLUMN "work_calendar"."create_time" IS '创建时间。';
COMMENT ON COLUMN "work_calendar"."updated_by" IS '修改人。';
COMMENT ON COLUMN "work_calendar"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "work_calendar"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "work_calendar_binding" IS '工作日历作用域绑定表';
COMMENT ON COLUMN "work_calendar_binding"."id" IS '主键ID。';
COMMENT ON COLUMN "work_calendar_binding"."scope_type" IS '范围类型。';
COMMENT ON COLUMN "work_calendar_binding"."scope_key" IS '范围键。';
COMMENT ON COLUMN "work_calendar_binding"."calendar_id" IS '日历ID。';
COMMENT ON COLUMN "work_calendar_binding"."priority" IS '优先级。';
COMMENT ON COLUMN "work_calendar_binding"."effective_from" IS '生效起始时间。';
COMMENT ON COLUMN "work_calendar_binding"."effective_to" IS '生效结束时间。';
COMMENT ON COLUMN "work_calendar_binding"."status" IS '状态。';
COMMENT ON COLUMN "work_calendar_binding"."created_by" IS '创建人。';
COMMENT ON COLUMN "work_calendar_binding"."create_time" IS '创建时间。';
COMMENT ON COLUMN "work_calendar_binding"."updated_by" IS '修改人。';
COMMENT ON COLUMN "work_calendar_binding"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "work_calendar_binding"."deleted" IS '逻辑删除标记。';
COMMENT ON TABLE "work_calendar_exception" IS '日历特殊日期表';
COMMENT ON COLUMN "work_calendar_exception"."id" IS '主键ID。';
COMMENT ON COLUMN "work_calendar_exception"."calendar_id" IS '日历ID。';
COMMENT ON COLUMN "work_calendar_exception"."exception_date" IS '例外日期。';
COMMENT ON COLUMN "work_calendar_exception"."exception_type" IS '例外类型。';
COMMENT ON COLUMN "work_calendar_exception"."exception_name" IS '例外名称。';
COMMENT ON COLUMN "work_calendar_exception"."description" IS '说明。';
COMMENT ON COLUMN "work_calendar_exception"."create_time" IS '创建时间。';
COMMENT ON TABLE "work_calendar_exception_period" IS '特殊日期工作时段表';
COMMENT ON COLUMN "work_calendar_exception_period"."id" IS '主键ID。';
COMMENT ON COLUMN "work_calendar_exception_period"."exception_id" IS '例外ID。';
COMMENT ON COLUMN "work_calendar_exception_period"."start_minute" IS '起始分钟。';
COMMENT ON COLUMN "work_calendar_exception_period"."end_minute" IS '结束分钟。';
COMMENT ON COLUMN "work_calendar_exception_period"."sort_order" IS '排序号。';
COMMENT ON COLUMN "work_calendar_exception_period"."create_time" IS '创建时间。';
COMMENT ON TABLE "work_calendar_period" IS '每周工作时段表';
COMMENT ON COLUMN "work_calendar_period"."id" IS '主键ID。';
COMMENT ON COLUMN "work_calendar_period"."calendar_id" IS '日历ID。';
COMMENT ON COLUMN "work_calendar_period"."day_of_week" IS '星期。';
COMMENT ON COLUMN "work_calendar_period"."start_minute" IS '起始分钟。';
COMMENT ON COLUMN "work_calendar_period"."end_minute" IS '结束分钟。';
COMMENT ON COLUMN "work_calendar_period"."sort_order" IS '排序号。';
COMMENT ON COLUMN "work_calendar_period"."create_time" IS '创建时间。';
COMMENT ON TABLE "workflow_bootstrap_job" IS '启动初始化任务表';
COMMENT ON COLUMN "workflow_bootstrap_job"."job_name" IS '任务名称。';
COMMENT ON COLUMN "workflow_bootstrap_job"."completed_version" IS '完成版本。';
COMMENT ON COLUMN "workflow_bootstrap_job"."owner_id" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "workflow_bootstrap_job"."completed_at" IS '完成时间。';
COMMENT ON COLUMN "workflow_bootstrap_job"."create_time" IS '创建时间。';
COMMENT ON COLUMN "workflow_bootstrap_job"."update_time" IS '更新时间。';
COMMENT ON TABLE "workflow_outbox_event" IS '事务发件箱事件表';
COMMENT ON COLUMN "workflow_outbox_event"."id" IS '主键ID：通用数据库 Outbox 持久化记录。';
COMMENT ON COLUMN "workflow_outbox_event"."topic" IS '主题。';
COMMENT ON COLUMN "workflow_outbox_event"."event_key" IS '事件键。';
COMMENT ON COLUMN "workflow_outbox_event"."aggregate_type" IS '聚合根类型。';
COMMENT ON COLUMN "workflow_outbox_event"."aggregate_id" IS '聚合根ID。';
COMMENT ON COLUMN "workflow_outbox_event"."payload_document" IS '载荷文档。';
COMMENT ON COLUMN "workflow_outbox_event"."status" IS '状态。';
COMMENT ON COLUMN "workflow_outbox_event"."owner_id" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "workflow_outbox_event"."lease_token" IS '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。';
COMMENT ON COLUMN "workflow_outbox_event"."lease_until" IS '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。';
COMMENT ON COLUMN "workflow_outbox_event"."retry_count" IS '重试数量。';
COMMENT ON COLUMN "workflow_outbox_event"."max_retries" IS '最大重试次数。';
COMMENT ON COLUMN "workflow_outbox_event"."next_retry_time" IS '下一次重试时间。';
COMMENT ON COLUMN "workflow_outbox_event"."error_message" IS '错误消息。';
COMMENT ON COLUMN "workflow_outbox_event"."create_time" IS '创建时间。';
COMMENT ON COLUMN "workflow_outbox_event"."update_time" IS '更新时间。';
COMMENT ON COLUMN "workflow_outbox_event"."processed_time" IS '处理时间。';
COMMENT ON TABLE "workflow_schema_change" IS '数据库结构执行队列表';
COMMENT ON COLUMN "workflow_schema_change"."id" IS '主键ID。';
COMMENT ON COLUMN "workflow_schema_change"."ddl_hash" IS 'DDL哈希。';
COMMENT ON COLUMN "workflow_schema_change"."active_hash" IS '活跃结构请求哈希。';
COMMENT ON COLUMN "workflow_schema_change"."ddl_statement" IS 'DDL语句。';
COMMENT ON COLUMN "workflow_schema_change"."status" IS '状态。';
COMMENT ON COLUMN "workflow_schema_change"."attempt" IS '尝试次数。';
COMMENT ON COLUMN "workflow_schema_change"."owner_id" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "workflow_schema_change"."lease_token" IS '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。';
COMMENT ON COLUMN "workflow_schema_change"."lease_until" IS '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。';
COMMENT ON COLUMN "workflow_schema_change"."next_attempt_at" IS '下一次尝试次数时间。';
COMMENT ON COLUMN "workflow_schema_change"."last_error" IS '最近错误。';
COMMENT ON COLUMN "workflow_schema_change"."create_time" IS '创建时间。';
COMMENT ON COLUMN "workflow_schema_change"."update_time" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "workflow_schema_change"."completed_time" IS '完成时间。';

-- 系统元数据、菜单权限与禁用的超级管理员。
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('app.schema.version','7.2.0.2',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('cfg.execution-related-entities-count','true',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('cfg.task-related-entities-count','true',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('cmmn.schema.version','7.2.0.2',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('common.schema.version','7.2.0.2',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('dmn.schema.version','7.2.0.2',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('eventregistry.schema.version','7.2.0.2',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('next.dbid','1',1);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('schema.history','upgrade(6.8.0.0->7.2.0.2)',2);
INSERT INTO "act_ge_property" ("name_", "value_", "rev_") VALUES ('schema.version','7.2.0.2',2);
INSERT INTO "act_id_property" ("name_", "value_", "rev_") VALUES ('schema.version','7.2.0.2',1);
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (315408474423554179,'sys_dict','字典类型','平台系统表目录：sys_dict',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (319550136508222867,'sys_menu','菜单权限','平台系统表目录：sys_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (429188770482109985,'sys_user','系统用户','平台系统表目录：sys_user',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (540477245371031459,'sys_role_menu','角色菜单关系','平台系统表目录：sys_role_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (543966161995942233,'sys_user_role','用户角色关系','平台系统表目录：sys_user_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (687980787977785014,'sys_dict_item','字典明细','平台系统表目录：sys_dict_item',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict_item','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (695805941702569049,'sys_role','系统角色','平台系统表目录：sys_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (704307435534855320,'sys_user_group','用户组成员关系','平台系统表目录：sys_user_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (869084506871349004,'sys_organization','组织部门','平台系统表目录：sys_organization',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_organization','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_definition" ("id", "entity_code", "entity_name", "description", "process_definition_id", "status", "created_by", "create_time", "update_time", "table_name", "lifecycle_mode", "storage_mode", "deleted", "updated_by", "team_visibility_enabled", "team_visibility_level") VALUES (924525185085388686,'sys_group','用户组','平台系统表目录：sys_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (302983935847141633,315408474423554179,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (463024980901796770,315408474423554179,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (349900140693140919,315408474423554179,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (668047102303117425,315408474423554179,'dict_code','字典编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (683413866831122108,315408474423554179,'dict_name','字典名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (660514776571799460,315408474423554179,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1041515214726648952,315408474423554179,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (835260617712650971,315408474423554179,'status','状态：0-启用 1-禁用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (392848417810309094,315408474423554179,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (343176658012303195,319550136508222867,'breadcrumb','是否显示面包屑：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'breadcrumb',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (558990094873955059,319550136508222867,'component','组件路径','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'component',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (175753738162967087,319550136508222867,'create_by','创建者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (950655553882382674,319550136508222867,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (205676883257074073,319550136508222867,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (138193370562438585,319550136508222867,'entity_code','关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,23,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'entity_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (407584881042156273,319550136508222867,'icon','菜单图标','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'icon',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (58574189954682540,319550136508222867,'id','菜单ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1138396868793173976,319550136508222867,'is_cache','是否缓存：0-缓存 1-不缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,21,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_cache',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (776238513957782207,319550136508222867,'is_frame','是否外链：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,20,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_frame',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (111764993240200811,319550136508222867,'keep_alive','是否缓存：0-不缓存 1-缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'keep_alive',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (119578562633124551,319550136508222867,'list_key','实体列表稳定编码','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,25,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'list_key',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1002235088452601204,319550136508222867,'menu_name','菜单名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (198475160699508098,319550136508222867,'menu_type','菜单类型：M-目录 C-菜单 F-按钮','STRING','char(1)',1,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (573255979784869818,319550136508222867,'parent_id','父菜单ID，0为顶级菜单','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (426434390452136951,319550136508222867,'path','路由地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (908207397228493680,319550136508222867,'perm','权限标识，如：system:user:list','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'perm',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (544552761361520586,319550136508222867,'query','路由参数','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,22,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'query',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (158131531689092062,319550136508222867,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (509277996761328404,319550136508222867,'resource_type','菜单资源类型，ENTITY_LIST 表示动态实体列表','STRING','varchar(30)',30,0,0,NULL,NULL,NULL,24,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'resource_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (323682773251959664,319550136508222867,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (628997735940863871,319550136508222867,'status','状态：0-禁用 1-启用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (658300988401276707,319550136508222867,'update_by','更新者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (531863267937602399,319550136508222867,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,19,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (555037528202687605,319550136508222867,'visible','显示状态：0-隐藏 1-显示','STRING','char(1)',1,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'visible',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (852378610239470992,429188770482109985,'avatar','头像','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'avatar',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (23541322679314285,429188770482109985,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (361679855788775973,429188770482109985,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (10101577987500861,429188770482109985,'dept_id','部门ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dept_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (326932484964000364,429188770482109985,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1134826313312664922,429188770482109985,'id','用户ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (745051415557763972,429188770482109985,'nickname','昵称','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'nickname',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1148301704392575944,429188770482109985,'org_id','组织ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (317101019809934504,429188770482109985,'password','密码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'password',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1039751570117789095,429188770482109985,'password_reset_required','password_reset_required','BOOLEAN','tinyint',NULL,1,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'password_reset_required',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (475192751198870445,429188770482109985,'phone','手机号','STRING','varchar(20)',20,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (59576534536847010,429188770482109985,'status','状态（0启用 1禁用）','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (42152131250552875,429188770482109985,'token_version','Incremented to revoke all previously issued sessions','LONG','bigint',NULL,1,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'token_version',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (736587934404834291,429188770482109985,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (184566779876080086,429188770482109985,'username','用户名','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'username',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (761157259593380615,540477245371031459,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (480775900607645235,540477245371031459,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (561900151098621684,540477245371031459,'menu_id','菜单ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (919996913633728290,540477245371031459,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (979647368025597245,543966161995942233,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (728367720794403925,543966161995942233,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (75963321277264675,543966161995942233,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1082238976243929409,543966161995942233,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (290109203980610604,687980787977785014,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (902908459287977551,687980787977785014,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1112820714573490757,687980787977785014,'dict_code','冗余：字典编码（便于直接查询）','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (845110899938467101,687980787977785014,'dict_id','所属字典ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (85937474892350588,687980787977785014,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (978970058829541345,687980787977785014,'item_code','项编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (458794463769860611,687980787977785014,'item_label','项标签/显示文本','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_label',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (293542286014999959,687980787977785014,'item_value','项值','STRING','varchar(200)',200,1,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_value',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (160051157971634527,687980787977785014,'parent_id','父项ID，0表示顶级','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1042513892081176326,687980787977785014,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (290497113002851889,687980787977785014,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (23540013034190091,687980787977785014,'status','状态：0-启用 1-禁用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (541437194327279494,687980787977785014,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (125004887893384772,695805941702569049,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (50302947400312544,695805941702569049,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (464945732470729299,695805941702569049,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (39605060466058778,695805941702569049,'id','角色ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (582801574103340315,695805941702569049,'role_code','角色编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (846037797095146960,695805941702569049,'role_name','角色名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (469870220014538885,695805941702569049,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (511818241961063640,695805941702569049,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (249397106466806541,695805941702569049,'status','状态（0启用 1禁用）','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (990484312511420209,695805941702569049,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (106998218957706418,704307435534855320,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (957745245096590288,704307435534855320,'group_id','组ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (460851437342127427,704307435534855320,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1137657801046065632,704307435534855320,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (933634782907475287,869084506871349004,'address','地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'address',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (730434704510267566,869084506871349004,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (79125910432577094,869084506871349004,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (78753772494339085,869084506871349004,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (378782243158581413,869084506871349004,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (971589645354457971,869084506871349004,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (978131954777805791,869084506871349004,'leader_id','负责人ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (507492914926553510,869084506871349004,'leader_name','负责人名称（冗余）','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (538220663657061167,869084506871349004,'level','层级（0为顶级）','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'level',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (49047951767170137,869084506871349004,'org_code','组织编码（唯一）','STRING','varchar(100)',100,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (103503443431748381,869084506871349004,'org_name','组织名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (756767862978652736,869084506871349004,'parent_id','父级ID（顶级为0）','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (689704407809393946,869084506871349004,'path','完整路径，如：/0/1/5/10/','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (34676674937238359,869084506871349004,'phone','联系电话','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (799314663885053329,869084506871349004,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1046028949155177888,869084506871349004,'status','状态：0-启用，1-禁用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (46123295334207598,869084506871349004,'type','类型：org-组织，dept-部门','STRING','varchar(20)',20,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (923457855008240877,869084506871349004,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (221052828124990695,924525185085388686,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (276971412500557125,924525185085388686,'deleted','删除标志','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (147276096293704262,924525185085388686,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (954151322232061889,924525185085388686,'group_code','组编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (324844729973447637,924525185085388686,'group_name','组名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (1039028238930600397,924525185085388686,'id','组ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (22985037036471449,924525185085388686,'parent_id','父组ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (332274980659204946,924525185085388686,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (836913778987896460,924525185085388686,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (370552990036906948,924525185085388686,'status','状态（0启用 1禁用）','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "entity_field" ("id", "entity_id", "field_code", "field_name", "field_type", "db_type", "field_length", "is_required", "is_unique", "default_value", "options_json", "validate_rules", "sort_order", "is_system", "is_published", "editable", "create_time", "update_time", "field_precision", "db_column_name", "file_types", "file_max_size", "file_max_count", "ref_entity_type", "ref_entity_id", "ref_field_code", "ref_list_key", "field_id", "dict_type", "value_storage", "deleted") VALUES (335370829110629664,924525185085388686,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "process_action_definition" ("id", "action_code", "display_name", "description", "handler_name", "visibility_scope", "enabled", "created_by", "create_time", "update_time", "deleted") VALUES ('flow_action_definition_notify','sendNotificationHandler','发送流程通知','发送待办、完成、撤回等流程通知；推荐使用提交后执行。','sendNotificationHandler','GLOBAL',1,'system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "process_person_resolver_definition" ("id", "resolver_code", "display_name", "description", "bean_name", "implementation_version", "contract_version", "supported_usages_document", "extra_param_schema_document", "dynamic_extra_params", "enabled", "revision", "create_time", "update_time", "deleted") VALUES ('person_resolver_entity_user_reference_001','entityUserReferenceField','实体用户关系字段','从流程绑定实体的已发布用户单选或多选关系字段读取人员','entityUserReferenceFieldPersonResolver',1,1,'[\"ASSIGNEE\",\"CANDIDATE\",\"MULTI_INSTANCE\"]','{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"schemaVersion\",\"entityCode\",\"fieldCode\"],\"properties\":{\"schemaVersion\":{\"const\":1},\"entityCode\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":128},\"fieldCode\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":100,\"pattern\":\"^[A-Za-z][A-Za-z0-9_]{0,99}$\"}}}',0,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "process_person_resolver_definition" ("id", "resolver_code", "display_name", "description", "bean_name", "implementation_version", "contract_version", "supported_usages_document", "extra_param_schema_document", "dynamic_extra_params", "enabled", "revision", "create_time", "update_time", "deleted") VALUES ('person_resolver_relative_position_001','relativeOrgPosition','相对组织职务','按流程发起人冻结组织链查询节点激活时的有效职务任职人','relativeOrgPositionPersonResolver',1,1,'[\"CANDIDATE\",\"ASSIGNEE\",\"MULTI_INSTANCE\"]','{\"multipleMatchPolicies\":[\"ERROR\",\"PRIMARY_OR_ERROR\",\"ALL\"],\"subject\":[\"PROCESS_INITIATOR\"],\"schemaVersion\":1,\"lookupModes\":[\"SELF\",\"FIXED_ANCESTOR\",\"NEAREST_WITH_HOLDER\",\"BUSINESS_LEVEL\"],\"anchor\":[\"DEPARTMENT\",\"ORGANIZATION\"],\"type\":\"object\"}',0,1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "sys_dict" ("id", "dict_code", "dict_name", "description", "status", "sort", "deleted", "create_time", "update_time") VALUES ('dict_org_business_level_001','organization_business_level','组织业务层级','独立于物理树深度的稳定组织业务层级','0',10,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_dict_item" ("id", "dict_id", "dict_code", "parent_id", "item_code", "item_label", "item_value", "sort", "status", "remark", "deleted", "create_time", "update_time") VALUES ('dict_org_level_center_001','dict_org_business_level_001','organization_business_level','0','CENTER','中心','CENTER',30,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_dict_item" ("id", "dict_id", "dict_code", "parent_id", "item_code", "item_label", "item_value", "sort", "status", "remark", "deleted", "create_time", "update_time") VALUES ('dict_org_level_company_001','dict_org_business_level_001','organization_business_level','0','COMPANY','公司','COMPANY',20,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_dict_item" ("id", "dict_id", "dict_code", "parent_id", "item_code", "item_label", "item_value", "sort", "status", "remark", "deleted", "create_time", "update_time") VALUES ('dict_org_level_dept1_001','dict_org_business_level_001','organization_business_level','0','FIRST_LEVEL_DEPT','一级部门','FIRST_LEVEL_DEPT',40,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_dict_item" ("id", "dict_id", "dict_code", "parent_id", "item_code", "item_label", "item_value", "sort", "status", "remark", "deleted", "create_time", "update_time") VALUES ('dict_org_level_dept2_001','dict_org_business_level_001','organization_business_level','0','SECOND_LEVEL_DEPT','二级部门','SECOND_LEVEL_DEPT',50,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_dict_item" ("id", "dict_id", "dict_code", "parent_id", "item_code", "item_label", "item_value", "sort", "status", "remark", "deleted", "create_time", "update_time") VALUES ('dict_org_level_group_001','dict_org_business_level_001','organization_business_level','0','GROUP','集团','GROUP',10,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_dict_item" ("id", "dict_id", "dict_code", "parent_id", "item_code", "item_label", "item_value", "sort", "status", "remark", "deleted", "create_time", "update_time") VALUES ('dict_org_level_team_001','dict_org_business_level_001','organization_business_level','0','TEAM','团队','TEAM',60,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_global_setting" ("id", "scope_type", "owner_id", "setting_key", "name", "setting_value_type", "setting_value", "remark", "version", "created_by", "updated_by", "create_time", "update_time") VALUES ('99777a7f-819f-4830-885e-eb36e7607b19','SYSTEM','0','ui.user_preferences','用户界面偏好','JSON','{\"fieldTypesCollapsed\":false,\"sidebarCollapsed\":false,\"tabsEnabled\":false}','字段类型面板收起、左侧主菜单收起和顶部多标签页统一存储；按字段优先使用个人配置，缺失字段继承系统默认值。',0,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('300','0','配置管理','M','Box',3,'/config',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('400','0','系统管理','M','Setting',4,'/system',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('403','300','流程用户组','C','FolderOpened',3,'/config/process-user-groups',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('assignee_incident_handle_001','assignee_incident_menu_001','处置空办理人事件','F',NULL,1,'','','process:assignee-incident:handle','0','0','0','1','补充办理人、重试解析器、转兜底组或终止实例',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('assignee_incident_menu_001','0','空办理人事件','C','Warning',78,'/system/assignee-incidents','system/AssigneeIncidentManagement','process:assignee-incident:list','0','0','0','1','查看空办理人告警、重试和人工恢复记录',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_analyze_001','config_migration_menu_001','分析配置','F',NULL,4,'','','config-migration:analyze','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_download_001','config_migration_menu_001','下载发布包','F',NULL,2,'','','config-migration:download','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_export_001','config_migration_menu_001','导出配置','F',NULL,1,'','','config-migration:export','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_import_001','config_migration_menu_001','导入配置','F',NULL,3,'','','config-migration:import','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_list_001','config_migration_menu_001','查看配置迁移','F',NULL,0,'','','config-migration:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_menu_001','400','配置迁移','C','FolderOpened',90,'/system/config-migration','system/ConfigMigration','config-migration:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_publish_001','config_migration_menu_001','发布配置','F',NULL,5,'','','config-migration:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('config_migration_rollback_001','config_migration_menu_001','回滚配置','F',NULL,6,'','','config-migration:rollback','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('custom_form_guide','flow_setting_menu_001','自定义表单组件','C','Document',4,'/dev/manual/custom-form','/views/system/CustomFormGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('custom_list_guide','flow_setting_menu_001','自定义列表组件','C','Document',3,'/dev/manual/custom-list','/views/system/CustomListGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('dev_guide_dir','0','定制开发','M','Document',5,'/dev',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('dev_guide_list','flow_setting_menu_001','列表字段扩展','C','Document',1,'/dev/manual/list-field-extension','/views/system/DevGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('embed_management_menu_001','0','嵌入集成','C','Monitor',76,'/system/embed-management','system/EmbedManagement','system:embed:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('embed_perm_identity_manage','embed_management_menu_001','维护嵌入身份映射','F',NULL,4,NULL,NULL,'system:embed:identity-manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('embed_perm_manage','embed_management_menu_001','维护嵌入视图与授权','F',NULL,2,NULL,NULL,'system:embed:manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('embed_perm_publish','embed_management_menu_001','发布嵌入视图','F',NULL,3,NULL,NULL,'system:embed:publish','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('embed_perm_session_revoke','embed_management_menu_001','撤销嵌入会话','F',NULL,5,NULL,NULL,'system:embed:session-revoke','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('embed_perm_view','embed_management_menu_001','查看嵌入运行时','F',NULL,1,NULL,NULL,'system:embed:view','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_scope_explicit_all_permission_001','0','允许全量数据放行','F',NULL,0,'','','entity:list-scope:explicit-all','0','1','0','1','允许在列表数据范围配置中显式确认全量可见的高风险权限',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_ui_hotfix_observe_permission','0','UI热修复观察指标','F',NULL,94,NULL,NULL,'entity:ui-config:hotfix:observe','0','1','0','1','HOTFIX运行观察指标上报',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_ui_hotfix_override_permission','300','UI配置热修复风险覆盖','F',NULL,91,NULL,NULL,'entity:ui-config:hotfix:override','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_ui_hotfix_publish_permission','300','UI配置兼容热修复','F',NULL,90,NULL,NULL,'entity:ui-config:hotfix','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_ui_hotfix_rollback_permission','0','UI热修复受控回滚','F',NULL,93,NULL,NULL,'entity:ui-config:hotfix:rollback','0','1','0','1','HOTFIX专用回滚权限',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_version_config_list_001','entity_version_management_001','查看数据版本配置','F',NULL,1,'','','entity:version:config:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_version_config_publish_001','entity_version_management_001','发布数据版本配置','F',NULL,3,'','','entity:version:config:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_version_config_update_001','entity_version_management_001','维护数据版本配置','F',NULL,2,'','','entity:version:config:update','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_version_management_001','0','数据版本','C','Clock',74,'/system/entity-versions','system/EntityVersionManagement','entity:version:config:list','0','0','0','1','实体数据版本策略、发布与比较',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_version_record_capture_001','entity_version_management_001','手工固化记录版本','F',NULL,5,'','','entity:version:record:capture','0','0','0','1','手工生成记录检查点',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('entity_version_record_view_001','entity_version_management_001','查看记录版本','F',NULL,4,'','','entity:version:record:view','0','0','0','1','查看有数据权限的记录历史版本',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('extension_list_permission_001','extension_management_menu_001','查看扩展','F',NULL,1,'','','system:extension:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('extension_management_menu_001','dev_guide_dir','扩展管理','C','Setting',2,'/dev/extensions','system/ExtensionManagement',NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('extension_test_permission_001','extension_management_menu_001','测试扩展','F',NULL,3,'','','system:extension:test','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('extension_update_permission_001','extension_management_menu_001','维护扩展','F',NULL,2,'','','system:extension:update','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('external_system_manage_permission_001','external_system_menu_001','维护外部系统','F',NULL,2,'','','system:external-system:manage','0','0','0','1','新增、编辑、启停和删除外部系统及其参数',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('external_system_menu_001','400','外部系统','C','Connection',14,'/system/external-systems','system/ExternalSystem',NULL,'0','0','0','1','维护外部系统基础资料和对接参数；具体接口由业务扩展实现',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('external_system_view_permission_001','external_system_menu_001','查看外部系统','F',NULL,1,'','','system:external-system:view','0','0','0','1','查看外部系统基本信息列表',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('flow_action_guide_menu_001','flow_setting_menu_001','流程动作','C','Notebook',5,'/dev/manual/flow-actions','system/FlowActionGuide','system:flowAction:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('flow_setting_menu_001','dev_guide_dir','开发手册','M','Notebook',1,'/dev/manual',NULL,NULL,'0','0','0','1','定制开发相关配置与扩展手册',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('global_settings_manage','global_settings_menu','维护全局设置','F',NULL,2,'','','system:setting:manage','0','0','0','1','修改或恢复系统默认值',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('global_settings_menu','400','全局设置','C','Setting',15,'/system/settings','system/GlobalSettings',NULL,'0','0','0','1','设置系统默认值，用户个人偏好可单独覆盖',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('global_settings_view','global_settings_menu','查看全局设置','F',NULL,1,'','','system:setting:view','0','0','0','1','查看系统设置及逻辑说明',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('integration_management_menu_001','0','开放集成','C','Connection',75,'/system/open-integration','system/OpenIntegration','system:integration:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('integration_perm_manage','integration_management_menu_001','维护接入应用','F',NULL,2,NULL,NULL,'system:integration:manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('integration_perm_secret_rotate','integration_management_menu_001','轮换应用凭据','F',NULL,3,NULL,NULL,'system:integration:secret-rotate','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('integration_perm_view','integration_management_menu_001','查看开放集成','F',NULL,1,NULL,NULL,'system:integration:view','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('list_column_template_manage_001','list_column_template_menu_001','维护列表列模板','F',NULL,1,'','','system:list-column-template:manage','0','0','0','1','新增、编辑和复制列表列模板',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('list_column_template_menu_001','300','列表列模板','C','Grid',4,'/config/list-column-templates','system/ListColumnTemplateManagement','system:list-column-template:view','0','0','0','1','可视化维护用于一次性初始化的列表列模板',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('list_field_guide_v2_001','flow_setting_menu_001','列表字段扩展2','C','Notebook',2,'/dev/manual/list-field-extension-v2','system/ListFieldExtensionGuide','system:dev:list','0','0','0','1','列表字段扩展开发手册：单元格组件、虚拟列与 ListFieldDataProvider',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('position_assign_permission_001','position_management_menu_001','维护组织任职','F',NULL,2,'','','system:position:assign','0','0','0','1','任命、转任、撤销和调整任职有效期',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('position_manage_permission_001','position_management_menu_001','维护职务定义','F',NULL,1,'','','system:position:manage','0','0','0','1','新增、编辑、启停职务定义',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('position_management_menu_001','400','职务管理','C','Briefcase',3,'/system/position','system/Position','system:position:view','0','0','0','1','全局职务定义与组织任职管理',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_dict_manage','0','Manage dictionaries','F',NULL,0,NULL,NULL,'system:dictionary:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_dict_view','0','View dictionaries','F',NULL,0,NULL,NULL,'system:dictionary:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_entity_manage','0','Manage entity definitions','F',NULL,0,NULL,NULL,'entity:definition:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_entity_publish','0','Publish entity definitions','F',NULL,0,NULL,NULL,'entity:definition:publish','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_entity_view','0','View entity definitions','F',NULL,0,NULL,NULL,'entity:definition:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_file_delete','0','Delete files','F',NULL,0,NULL,NULL,'storage:file:delete','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_file_read','0','Read files','F',NULL,0,NULL,NULL,'storage:file:read','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_file_write','0','Upload files','F',NULL,0,NULL,NULL,'storage:file:write','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_menu_manage','0','Manage menus','F',NULL,0,NULL,NULL,'system:menu:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_menu_view','0','View menus','F',NULL,0,NULL,NULL,'system:menu:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_org_manage','0','Manage organizations','F',NULL,0,NULL,NULL,'system:organization:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_org_view','0','View organizations','F',NULL,0,NULL,NULL,'system:organization:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_process_manage','0','Manage process definitions','F',NULL,0,NULL,NULL,'process:definition:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_process_publish','0','Publish process definitions','F',NULL,0,NULL,NULL,'process:definition:publish','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_process_signal','0','Trigger process receive tasks','F',NULL,0,NULL,NULL,'process:instance:signal','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_process_view','0','View process definitions','F',NULL,0,NULL,NULL,'process:definition:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_role_manage','0','Manage roles','F',NULL,0,NULL,NULL,'system:role:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_role_view','0','View roles','F',NULL,0,NULL,NULL,'system:role:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_user_manage','0','Manage users','F',NULL,0,NULL,NULL,'system:user:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_user_reset','0','Reset user password','F',NULL,0,NULL,NULL,'system:user:reset-password','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('security_perm_user_view','0','View users','F',NULL,0,NULL,NULL,'system:user:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('sla_management_dir_001','400','SLA管理','M','Timer',8,'/system/sla',NULL,NULL,'0','0','0','1','SLA策略与运行监控',0,'migration-v075',CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('system_audit_detail_perm_001','system_audit_menu_001','系统日志详情','F','',990,'','','system:audit:detail','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('system_audit_export_perm_001','system_audit_menu_001','系统日志导出','F','',990,'','','system:audit:export','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('system_audit_list_perm_001','system_audit_menu_001','系统日志查询','F','',990,'','','system:audit:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('system_audit_menu_001','400','系统日志','C','Document',80,'/system/audit-logs','system/SystemAudit',NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('task_sla_monitor_menu_001','sla_management_dir_001','SLA监控','C','DataAnalysis',2,'/system/sla/monitor','process/TaskSlaMonitor','process:sla:monitor','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('task_sla_policy_manage_perm_001','task_sla_policy_menu_001','维护SLA策略','F',NULL,1,'','','process:sla-policy:manage','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('task_sla_policy_menu_001','sla_management_dir_001','SLA策略','C','Timer',1,'/system/sla/policies','process/TaskSlaPolicyManagement','process:sla-policy:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('task_sla_policy_publish_perm_001','task_sla_policy_menu_001','发布SLA策略','F',NULL,2,'','','process:sla-policy:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('user_manual_dir_001','0','用户手册','M','Notebook',6,'/manual',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('user_manual_embed_integration_001','user_manual_dir_001','嵌入集成','C','Monitor',4,'/manual/embed-integration','manual/EmbedIntegrationManual','user-manual:embed-integration:view','0','0','0','1','Embed View、应用授权、外部用户映射、Launch 与宿主 SDK 使用手册',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('user_manual_entity_001','user_manual_dir_001','实体配置','C','Document',1,'/manual/entity','manual/EntityManual','user-manual:entity:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('user_manual_open_integration_001','user_manual_dir_001','集成应用与 Embed','C','Link',3,'/manual/open-integration','manual/OpenIntegrationManual','user-manual:open-integration:view','0','0','0','1','集成应用、Client Credential、OAuth 与 Embed 接入说明',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('user_manual_process_001','user_manual_dir_001','流程管理','C','Connection',2,'/manual/process','manual/ProcessManual','user-manual:process:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('user_manual_quick_start_001','user_manual_dir_001','快速开始','C','Guide',0,'/manual/quick-start','manual/QuickStartManual','user-manual:quick-start:view','0','0','0','1','从配置流程、实体表单和列表到绑定流程、配置菜单的入门步骤',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('work_calendar_manage_perm_001','work_calendar_menu_001','维护工作日历','F',NULL,1,'','','system:work-calendar:manage','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('work_calendar_menu_001','400','工作日历','C','Calendar',7,'/system/work-calendars','system/WorkCalendarManagement','system:work-calendar:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v075',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_menu" ("id", "parent_id", "menu_name", "menu_type", "icon", "sort", "path", "component", "perm", "status", "visible", "keep_alive", "breadcrumb", "remark", "deleted", "create_by", "create_time", "update_by", "update_time", "is_frame", "is_cache", "query", "entity_code", "resource_type", "list_key") VALUES ('work_calendar_publish_perm_001','work_calendar_menu_001','发布工作日历','F',NULL,2,'','','system:work-calendar:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "sys_position" ("id", "position_code", "position_name", "applicable_unit_type", "holder_mode", "built_in", "status", "sort_order", "description", "revision", "created_by", "updated_by", "create_time", "update_time", "deleted") VALUES ('position_unit_leader_001','UNIT_LEADER','负责人','ANY','SINGLE',1,'ENABLED',10,'组织或部门负责人；旧 leader 字段的权威任职来源',1,'migration-v069','migration-v069',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "sys_role" ("id", "role_name", "role_code", "description", "sort", "status", "create_time", "update_time", "deleted", "sort_order") VALUES ('1','已更新角色','super_admin','更新后的描述',0,'0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('00e39edf5c50bf3c7a691e3abd740378','1','list_column_template_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('03289d58b1fc7178ff46d6dff1476e9d','1','position_assign_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('0738c3d0253bc328872fbb37bf678ea2','1','global_settings_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('08122bbb3eea6a2141bc5b2c0a0a65f7','1','work_calendar_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('09d1a148efc935e59f85cff8e0077917','1','security_perm_menu_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('0a446aa774fce4e90e322621695b54ab','1','external_system_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('0fd6e9fdba7cdf617d1f50011543ca8e','1','position_manage_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('1354fb8ab53c1ed349577b7ba14226e9','1','security_perm_role_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('149312798634ce6f7a0f1bae0b45ebe9','1','security_perm_entity_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('18a946f6332387169ab762dbc3720556','1','security_perm_user_reset',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('1dec8f0ec49c31c224d02337a721ca9b','1','security_perm_org_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054120787970','1','300',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054129176579','1','400',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054141759490','1','403',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054158536706','1','config_migration_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054162731010','1','config_migration_list_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054166925314','1','config_migration_export_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054171119618','1','config_migration_download_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054175313922','1','config_migration_import_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054179508226','1','config_migration_analyze_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054183702529','1','config_migration_publish_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054187896834','1','config_migration_rollback_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054192091137','1','dev_guide_dir',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054192091138','1','dev_guide_list',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054196285441','1','flow_setting_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054196285442','1','flow_action_guide_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054200479745','1','custom_list_guide',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054208868353','1','custom_form_guide',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054213062657','1','user_manual_dir_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054217256961','1','user_manual_entity_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2080586054221451265','1','user_manual_process_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2645fafb4cb2d4ab1718a522021b88ee','1','embed_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('265953de88d211f1b9e8f79d2d09a523','1','extension_list_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('265955b488d211f1b9e8f79d2d09a523','1','extension_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2659562288d211f1b9e8f79d2d09a523','1','extension_test_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2659568688d211f1b9e8f79d2d09a523','1','extension_update_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2b58ccf45f7952af77d9c628903ea6d3','1','sla_management_dir_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2babeffb7ff59b9f58563d683709f417','1','security_perm_user_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('2fb4ac5920a5efb0e15a9243b154eb1a','1','security_perm_dict_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('30c5b440794425d37f8b4fd1505d5c13','1','assignee_incident_handle_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('328867562617bb929d1481789903f792','1','external_system_view_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('34e3f2279ce59857ed96eab6ca8917aa','1','security_perm_entity_publish',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('4876190b55d2da7be050d12676b793bda59ae3e9a68300f3ef858f3e91e1b120','1','entity_version_record_view_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('48ae6519ae82ea9129f204ec89ea3377','1','global_settings_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('4e62eabc457af7dcdc56ca45c430e4d6','1','integration_perm_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('4fdaa562a5bc238a088ebdd1d789154d','1','security_perm_process_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('50420884b78cfa827cc3ab9f38e0512c','1','task_sla_policy_publish_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('5247a13fc8e62351a49d983553d2f7f5','1','work_calendar_manage_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('525610baf86ec1e3adeac8a92aba7a8d','1','security_perm_process_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('546a07e11c06c471218e654589401463','1','position_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('55feae72f9d8990c5baad24327202c9b66f354c061bb8fa12b9ce73919d7ea60','1','entity_version_record_capture_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('5db73118888f11f1a02e52aa5ed9252f','1','system_audit_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('5fd0a378842fbd966c85b20284dc95b9','1','security_perm_role_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('60c5b627c72a07be37c86100c55c5ffd','1','integration_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('629d8bdc88f561028367b9db30a06f24','1','entity_version_management_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('6a9d1a1a3a1f1690896b5d6afc27dd09','1','work_calendar_publish_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('6f72a8c786ff1f30d5a03784b1db7342','1','user_manual_embed_integration_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('73437b56888811f1a02e52aa5ed9252f','1','system_audit_detail_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('73438326888811f1a02e52aa5ed9252f','1','system_audit_export_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('73438452888811f1a02e52aa5ed9252f','1','system_audit_list_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('77274acfbca78ed28e32315ff1e8bfe9','1','global_settings_menu',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('774ad263cdffd3a42e880733db29d618','1','security_perm_user_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('77a93d3f2f55b8d29cb9787ef3dee1e5','1','entity_scope_explicit_all_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('77ca7cb65b7f2138c812aa04bea1000b','1','integration_perm_secret_rotate',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('7a932ae42063ffa3a0568ca6b02fa3b1','1','assignee_incident_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('7f2db854d19c52af67b69b54ff08d5e7','1','list_column_template_manage_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('80b73a9fa2b2a876bfe18a0e0fa92570','1','security_perm_process_signal',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('83762738c6a8df0fc9f6265ecf170827','1','security_perm_entity_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('848bb511a33540d3afd1a32290a09cac','1','external_system_manage_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('85de4afcadd2f28ed310d60560c5f833','1','task_sla_monitor_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('891042c416d95c26ec89827cfc2cb595','1','security_perm_process_publish',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('92606bb7c82d2688538c3d9aef521190','1','entity_version_config_update_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('9965553b538c05de8ed97b0f8a505711','1','security_perm_file_delete',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('a3759a57d5a664580a13c93dd46a4dc2','1','security_perm_dict_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('a7f105a87027044cb40d5d2286e2a529','1','task_sla_policy_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('ad752fac598351a4b7aff641a592c0c9','1','embed_perm_publish',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('b0d591c126e47108bfe48b6cce23f906','1','integration_perm_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('b3e0385475f59111486b8ee903d983e7','1','embed_perm_session_revoke',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('bfc91a13fe9e230578fdb666565e32f9','1','task_sla_policy_manage_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('c4e997351779bfbfe41190a34cb64a59','1','embed_perm_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('c7efe78a77b873a80d7695bdaa622de4','1','security_perm_menu_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('d8585f0e7fb4b0e1d5b65c17ec0d5a8d','1','entity_version_config_list_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('d8c7fae434846be9955701bca8e8b82c','1','security_perm_file_read',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('dec00d4a36f9fc8cc09c13eee43bf5dc','1','user_manual_quick_start_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('e079416319a588aeec38398f930c1ce5','1','user_manual_open_integration_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('f2ddc5d96a9b707f7f7987bbe00a7b24','1','entity_version_config_publish_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('f4df0f29d3b790164aec97b3fd36d645','1','list_field_guide_v2_001',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('f6f6487da585d22f311fb7cab03006ea','1','security_perm_file_write',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('f7502878d9a1bf8273184abc648a8e14','1','security_perm_org_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('fa85456c4c2efdd7e4e03b9cddce27a7','1','embed_perm_identity_manage',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('fca60e3e87e011f1a02e52aa5ed9252f','1','entity_ui_hotfix_publish_permission',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('fca636d487e011f1a02e52aa5ed9252f','1','entity_ui_hotfix_override_permission',CURRENT_TIMESTAMP);
INSERT INTO "sys_role_menu" ("id", "role_id", "menu_id", "create_time") VALUES ('fffc1112adfbecab7f3f747beab1ad10','1','embed_perm_view',CURRENT_TIMESTAMP);
INSERT INTO "sys_global_setting" ("id", "scope_type", "owner_id", "setting_key", "name", "setting_value_type", "setting_value", "remark") VALUES ('setting_migration_signing_key', 'SYSTEM', '0', 'config.migration.signing_key', '配置迁移签名密钥', 'STRING', to_json(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''))::text, '用于迁移包 HMAC-SHA256 签名与验签；每个新库独立生成，不应跨库复用。');
INSERT INTO "sys_user" ("id", "username", "nickname", "password", "email", "phone", "avatar", "status", "create_time", "update_time", "deleted", "org_id", "dept_id", "password_reset_required", "token_version") VALUES ('1', 'admin', '超级管理员', '$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6', 'admin@workflow.com', NULL, NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, NULL, NULL, 1, 0);
INSERT INTO "sys_user_role" ("id", "user_id", "role_id", "create_time") VALUES ('bootstrap_admin_role_001', '1', '1', CURRENT_TIMESTAMP);

-- 初始数据载入后建立外键，避免跨表种子数据的顺序依赖。
ALTER TABLE "act_app_appdef" ADD CONSTRAINT "fk_act_app_appdef_act_fk_app_def_dply" FOREIGN KEY ("deployment_id_") REFERENCES "act_app_deployment" ("id_");
ALTER TABLE "act_app_deployment_resource" ADD CONSTRAINT "fk_act_app_deployment_resource_act_fk_app_rsrc_dpl" FOREIGN KEY ("deployment_id_") REFERENCES "act_app_deployment" ("id_");
ALTER TABLE "act_cmmn_casedef" ADD CONSTRAINT "fk_act_cmmn_casedef_act_fk_case_def_dply" FOREIGN KEY ("deployment_id_") REFERENCES "act_cmmn_deployment" ("id_");
ALTER TABLE "act_cmmn_deployment_resource" ADD CONSTRAINT "fk_act_cmmn_deployment_resource_act_fk_cmmn_rsrc_dpl" FOREIGN KEY ("deployment_id_") REFERENCES "act_cmmn_deployment" ("id_");
ALTER TABLE "act_cmmn_ru_case_inst" ADD CONSTRAINT "fk_act_cmmn_ru_case_inst_act_fk_case_inst_case_def" FOREIGN KEY ("case_def_id_") REFERENCES "act_cmmn_casedef" ("id_");
ALTER TABLE "act_cmmn_ru_mil_inst" ADD CONSTRAINT "fk_act_cmmn_ru_mil_inst_act_fk_mil_case_def" FOREIGN KEY ("case_def_id_") REFERENCES "act_cmmn_casedef" ("id_");
ALTER TABLE "act_cmmn_ru_mil_inst" ADD CONSTRAINT "fk_act_cmmn_ru_mil_inst_act_fk_mil_case_inst" FOREIGN KEY ("case_inst_id_") REFERENCES "act_cmmn_ru_case_inst" ("id_");
ALTER TABLE "act_cmmn_ru_plan_item_inst" ADD CONSTRAINT "fk_act_cmmn_ru_plan_item_inst_act_fk_plan_item_case_def" FOREIGN KEY ("case_def_id_") REFERENCES "act_cmmn_casedef" ("id_");
ALTER TABLE "act_cmmn_ru_plan_item_inst" ADD CONSTRAINT "fk_act_cmmn_ru_plan_item_inst_act_fk_plan_item_case_inst" FOREIGN KEY ("case_inst_id_") REFERENCES "act_cmmn_ru_case_inst" ("id_");
ALTER TABLE "act_cmmn_ru_sentry_part_inst" ADD CONSTRAINT "fk_act_cmmn_ru_sentry_part_inst_act_fk_sentry_case_def" FOREIGN KEY ("case_def_id_") REFERENCES "act_cmmn_casedef" ("id_");
ALTER TABLE "act_cmmn_ru_sentry_part_inst" ADD CONSTRAINT "fk_act_cmmn_ru_sentry_part_inst_act_fk_sentry_case_inst" FOREIGN KEY ("case_inst_id_") REFERENCES "act_cmmn_ru_case_inst" ("id_");
ALTER TABLE "act_cmmn_ru_sentry_part_inst" ADD CONSTRAINT "fk_act_cmmn_ru_sentry_part_inst_act_fk_sentry_plan_item" FOREIGN KEY ("plan_item_inst_id_") REFERENCES "act_cmmn_ru_plan_item_inst" ("id_");
ALTER TABLE "act_dmn_deployment_resource" ADD CONSTRAINT "fk_act_dmn_deployment_resource_act_fk_dmn_rsrc_dpl" FOREIGN KEY ("deployment_id_") REFERENCES "act_dmn_deployment" ("id_");
ALTER TABLE "act_ge_bytearray" ADD CONSTRAINT "fk_act_ge_bytearray_act_fk_bytearr_depl" FOREIGN KEY ("deployment_id_") REFERENCES "act_re_deployment" ("id_");
ALTER TABLE "act_id_membership" ADD CONSTRAINT "fk_act_id_membership_act_fk_memb_group" FOREIGN KEY ("group_id_") REFERENCES "act_id_group" ("id_");
ALTER TABLE "act_id_membership" ADD CONSTRAINT "fk_act_id_membership_act_fk_memb_user" FOREIGN KEY ("user_id_") REFERENCES "act_id_user" ("id_");
ALTER TABLE "act_id_priv_mapping" ADD CONSTRAINT "fk_act_id_priv_mapping_act_fk_priv_mapping" FOREIGN KEY ("priv_id_") REFERENCES "act_id_priv" ("id_");
ALTER TABLE "act_procdef_info" ADD CONSTRAINT "fk_act_procdef_info_act_fk_info_json_ba" FOREIGN KEY ("info_json_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_procdef_info" ADD CONSTRAINT "fk_act_procdef_info_act_fk_info_procdef" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_re_model" ADD CONSTRAINT "fk_act_re_model_act_fk_model_deployment" FOREIGN KEY ("deployment_id_") REFERENCES "act_re_deployment" ("id_");
ALTER TABLE "act_re_model" ADD CONSTRAINT "fk_act_re_model_act_fk_model_source" FOREIGN KEY ("editor_source_value_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_re_model" ADD CONSTRAINT "fk_act_re_model_act_fk_model_source_extra" FOREIGN KEY ("editor_source_extra_value_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_deadletter_job" ADD CONSTRAINT "fk_act_ru_deadletter_job_act_fk_deadletter_job_custom_values" FOREIGN KEY ("custom_values_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_deadletter_job" ADD CONSTRAINT "fk_act_ru_deadletter_job_act_fk_deadletter_job_exception" FOREIGN KEY ("exception_stack_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_deadletter_job" ADD CONSTRAINT "fk_act_ru_deadletter_job_act_fk_deadletter_job_execution" FOREIGN KEY ("execution_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_deadletter_job" ADD CONSTRAINT "fk_act_ru_deadletter_job_act_fk_deadletter_job_proc_def" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_ru_deadletter_job" ADD CONSTRAINT "fk_act_ru_deadletter_job_act_fk_deadletter_job_proces_245a4ce8" FOREIGN KEY ("process_instance_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_event_subscr" ADD CONSTRAINT "fk_act_ru_event_subscr_act_fk_event_exec" FOREIGN KEY ("execution_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_execution" ADD CONSTRAINT "fk_act_ru_execution_act_fk_exe_parent" FOREIGN KEY ("parent_id_") REFERENCES "act_ru_execution" ("id_") ON DELETE CASCADE;
ALTER TABLE "act_ru_execution" ADD CONSTRAINT "fk_act_ru_execution_act_fk_exe_procdef" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_ru_execution" ADD CONSTRAINT "fk_act_ru_execution_act_fk_exe_procinst" FOREIGN KEY ("proc_inst_id_") REFERENCES "act_ru_execution" ("id_") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "act_ru_execution" ADD CONSTRAINT "fk_act_ru_execution_act_fk_exe_super" FOREIGN KEY ("super_exec_") REFERENCES "act_ru_execution" ("id_") ON DELETE CASCADE;
ALTER TABLE "act_ru_external_job" ADD CONSTRAINT "fk_act_ru_external_job_act_fk_external_job_custom_values" FOREIGN KEY ("custom_values_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_external_job" ADD CONSTRAINT "fk_act_ru_external_job_act_fk_external_job_exception" FOREIGN KEY ("exception_stack_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_identitylink" ADD CONSTRAINT "fk_act_ru_identitylink_act_fk_athrz_procedef" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_ru_identitylink" ADD CONSTRAINT "fk_act_ru_identitylink_act_fk_idl_procinst" FOREIGN KEY ("proc_inst_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_identitylink" ADD CONSTRAINT "fk_act_ru_identitylink_act_fk_tskass_task" FOREIGN KEY ("task_id_") REFERENCES "act_ru_task" ("id_");
ALTER TABLE "act_ru_job" ADD CONSTRAINT "fk_act_ru_job_act_fk_job_custom_values" FOREIGN KEY ("custom_values_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_job" ADD CONSTRAINT "fk_act_ru_job_act_fk_job_exception" FOREIGN KEY ("exception_stack_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_job" ADD CONSTRAINT "fk_act_ru_job_act_fk_job_execution" FOREIGN KEY ("execution_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_job" ADD CONSTRAINT "fk_act_ru_job_act_fk_job_proc_def" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_ru_job" ADD CONSTRAINT "fk_act_ru_job_act_fk_job_process_instance" FOREIGN KEY ("process_instance_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_suspended_job" ADD CONSTRAINT "fk_act_ru_suspended_job_act_fk_suspended_job_custom_values" FOREIGN KEY ("custom_values_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_suspended_job" ADD CONSTRAINT "fk_act_ru_suspended_job_act_fk_suspended_job_exception" FOREIGN KEY ("exception_stack_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_suspended_job" ADD CONSTRAINT "fk_act_ru_suspended_job_act_fk_suspended_job_execution" FOREIGN KEY ("execution_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_suspended_job" ADD CONSTRAINT "fk_act_ru_suspended_job_act_fk_suspended_job_proc_def" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_ru_suspended_job" ADD CONSTRAINT "fk_act_ru_suspended_job_act_fk_suspended_job_process_instance" FOREIGN KEY ("process_instance_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_task" ADD CONSTRAINT "fk_act_ru_task_act_fk_task_exe" FOREIGN KEY ("execution_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_task" ADD CONSTRAINT "fk_act_ru_task_act_fk_task_procdef" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_ru_task" ADD CONSTRAINT "fk_act_ru_task_act_fk_task_procinst" FOREIGN KEY ("proc_inst_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_timer_job" ADD CONSTRAINT "fk_act_ru_timer_job_act_fk_timer_job_custom_values" FOREIGN KEY ("custom_values_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_timer_job" ADD CONSTRAINT "fk_act_ru_timer_job_act_fk_timer_job_exception" FOREIGN KEY ("exception_stack_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_timer_job" ADD CONSTRAINT "fk_act_ru_timer_job_act_fk_timer_job_execution" FOREIGN KEY ("execution_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_timer_job" ADD CONSTRAINT "fk_act_ru_timer_job_act_fk_timer_job_proc_def" FOREIGN KEY ("proc_def_id_") REFERENCES "act_re_procdef" ("id_");
ALTER TABLE "act_ru_timer_job" ADD CONSTRAINT "fk_act_ru_timer_job_act_fk_timer_job_process_instance" FOREIGN KEY ("process_instance_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_variable" ADD CONSTRAINT "fk_act_ru_variable_act_fk_var_bytearray" FOREIGN KEY ("bytearray_id_") REFERENCES "act_ge_bytearray" ("id_");
ALTER TABLE "act_ru_variable" ADD CONSTRAINT "fk_act_ru_variable_act_fk_var_exe" FOREIGN KEY ("execution_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "act_ru_variable" ADD CONSTRAINT "fk_act_ru_variable_act_fk_var_procinst" FOREIGN KEY ("proc_inst_id_") REFERENCES "act_ru_execution" ("id_");
ALTER TABLE "embed_allowed_origin" ADD CONSTRAINT "fk_embed_allowed_origin_fk_embed_allowed_origin_grant" FOREIGN KEY ("grant_id") REFERENCES "embed_application_grant" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_application_grant" ADD CONSTRAINT "fk_embed_application_grant_fk_embed_grant_application" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_application_grant" ADD CONSTRAINT "fk_embed_application_grant_fk_embed_grant_provider" FOREIGN KEY ("identity_provider_id") REFERENCES "embed_identity_provider" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_application_grant" ADD CONSTRAINT "fk_embed_application_grant_fk_embed_grant_view" FOREIGN KEY ("view_id") REFERENCES "embed_view" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_assertion_replay" ADD CONSTRAINT "fk_embed_assertion_replay_fk_embed_assertion_replay_provider" FOREIGN KEY ("provider_id") REFERENCES "embed_identity_provider" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_external_identity_binding" ADD CONSTRAINT "fk_embed_external_identity_binding_fk_embed_binding_a_7be2241a" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_external_identity_binding" ADD CONSTRAINT "fk_embed_external_identity_binding_fk_embed_binding_flow_user" FOREIGN KEY ("flow_user_id") REFERENCES "sys_user" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_external_identity_binding" ADD CONSTRAINT "fk_embed_external_identity_binding_fk_embed_binding_provider" FOREIGN KEY ("identity_provider_id") REFERENCES "embed_identity_provider" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_launch" ADD CONSTRAINT "fk_embed_launch_fk_embed_launch_application" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_launch" ADD CONSTRAINT "fk_embed_launch_fk_embed_launch_binding" FOREIGN KEY ("identity_binding_id") REFERENCES "embed_external_identity_binding" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_launch" ADD CONSTRAINT "fk_embed_launch_fk_embed_launch_flow_user" FOREIGN KEY ("flow_user_id") REFERENCES "sys_user" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_launch" ADD CONSTRAINT "fk_embed_launch_fk_embed_launch_grant" FOREIGN KEY ("grant_id") REFERENCES "embed_application_grant" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_launch" ADD CONSTRAINT "fk_embed_launch_fk_embed_launch_provider" FOREIGN KEY ("identity_provider_id") REFERENCES "embed_identity_provider" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_launch" ADD CONSTRAINT "fk_embed_launch_fk_embed_launch_release" FOREIGN KEY ("view_release_id") REFERENCES "embed_view_release" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_launch" ADD CONSTRAINT "fk_embed_launch_fk_embed_launch_view" FOREIGN KEY ("view_id") REFERENCES "embed_view" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_operation_receipt" ADD CONSTRAINT "fk_embed_operation_receipt_fk_embed_receipt_application" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_application" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_binding" FOREIGN KEY ("identity_binding_id") REFERENCES "embed_external_identity_binding" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_flow_user" FOREIGN KEY ("flow_user_id") REFERENCES "sys_user" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_grant" FOREIGN KEY ("grant_id") REFERENCES "embed_application_grant" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_launch" FOREIGN KEY ("launch_id") REFERENCES "embed_launch" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_provider" FOREIGN KEY ("identity_provider_id") REFERENCES "embed_identity_provider" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_release" FOREIGN KEY ("view_release_id") REFERENCES "embed_view_release" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session" ADD CONSTRAINT "fk_embed_session_fk_embed_session_view" FOREIGN KEY ("view_id") REFERENCES "embed_view" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session_counter" ADD CONSTRAINT "fk_embed_session_counter_fk_embed_session_counter_grant" FOREIGN KEY ("grant_id") REFERENCES "embed_application_grant" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_session_counter" ADD CONSTRAINT "fk_embed_session_counter_fk_embed_session_counter_user" FOREIGN KEY ("flow_user_id") REFERENCES "sys_user" ("id") ON DELETE RESTRICT;
ALTER TABLE "embed_view_release" ADD CONSTRAINT "fk_embed_view_release_fk_embed_view_release_view" FOREIGN KEY ("view_id") REFERENCES "embed_view" ("id") ON DELETE RESTRICT;
ALTER TABLE "entity_record_version" ADD CONSTRAINT "fk_entity_record_version_fk_entity_record_version_con_3f70e46b" FOREIGN KEY ("config_release_id") REFERENCES "entity_version_config_release" ("id");
ALTER TABLE "entity_record_version_dataset" ADD CONSTRAINT "fk_entity_record_version_dataset_fk_entity_record_ver_5a65f691" FOREIGN KEY ("version_id") REFERENCES "entity_record_version" ("id") ON DELETE CASCADE;
ALTER TABLE "entity_record_version_dataset_row" ADD CONSTRAINT "fk_entity_record_version_dataset_row_fk_entity_record_8dc14fff" FOREIGN KEY ("dataset_id") REFERENCES "entity_record_version_dataset" ("id") ON DELETE CASCADE;
ALTER TABLE "entity_schema_operation_event" ADD CONSTRAINT "fk_entity_schema_operation_event_fk_entity_schema_ope_0af82119" FOREIGN KEY ("operation_id") REFERENCES "entity_schema_operation" ("id") ON DELETE CASCADE;
ALTER TABLE "flw_event_resource" ADD CONSTRAINT "fk_flw_event_resource_flw_fk_event_rsrc_dpl" FOREIGN KEY ("deployment_id_") REFERENCES "flw_event_deployment" ("id_");
ALTER TABLE "flw_ru_batch_part" ADD CONSTRAINT "fk_flw_ru_batch_part_flw_fk_batch_part_parent" FOREIGN KEY ("batch_id_") REFERENCES "flw_ru_batch" ("id_");
ALTER TABLE "integration_api_request_lease" ADD CONSTRAINT "fk_integration_api_request_lease_fk_integration_api_l_51a38891" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE CASCADE;
ALTER TABLE "integration_application_credential" ADD CONSTRAINT "fk_integration_application_credential_fk_integration__3f714238" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE RESTRICT;
ALTER TABLE "integration_idempotency_record" ADD CONSTRAINT "fk_integration_idempotency_record_fk_integration_idem_5cd40e8b" FOREIGN KEY ("application_id") REFERENCES "integration_application" ("id") ON DELETE RESTRICT;
ALTER TABLE "sys_external_system_parameter" ADD CONSTRAINT "fk_sys_external_system_parameter_fk_sys_external_syst_2fbf8273" FOREIGN KEY ("external_system_id") REFERENCES "sys_external_system" ("id") ON DELETE RESTRICT;
ALTER TABLE "sys_position_assignment" ADD CONSTRAINT "fk_sys_position_assignment_fk_sys_position_assignment_org" FOREIGN KEY ("organization_unit_id") REFERENCES "sys_organization" ("id") ON DELETE RESTRICT;
ALTER TABLE "sys_position_assignment" ADD CONSTRAINT "fk_sys_position_assignment_fk_sys_position_assignment_position" FOREIGN KEY ("position_id") REFERENCES "sys_position" ("id") ON DELETE RESTRICT;
ALTER TABLE "sys_position_assignment" ADD CONSTRAINT "fk_sys_position_assignment_fk_sys_position_assignment_user" FOREIGN KEY ("user_id") REFERENCES "sys_user" ("id") ON DELETE RESTRICT;
ALTER TABLE "sys_position_assignment_batch" ADD CONSTRAINT "fk_sys_position_assignment_batch_fk_sys_position_batch_actor" FOREIGN KEY ("created_by") REFERENCES "sys_user" ("id") ON DELETE RESTRICT;
ALTER TABLE "ui_hotfix_observation_metric" ADD CONSTRAINT "fk_ui_hotfix_observation_metric_fk_ui_hotfix_metric_request" FOREIGN KEY ("request_id") REFERENCES "ui_config_hotfix_request" ("id") ON DELETE CASCADE;

-- 保持 MySQL ON UPDATE CURRENT_TIMESTAMP 的更新时间行为。
CREATE OR REPLACE FUNCTION flow_sql_touch_update_time() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW."update_time" IS NOT DISTINCT FROM OLD."update_time" AND NEW IS DISTINCT FROM OLD THEN
    NEW."update_time" := CURRENT_TIMESTAMP;
  END IF;
  RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION flow_sql_touch_updated_time() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW."updated_time" IS NOT DISTINCT FROM OLD."updated_time" AND NEW IS DISTINCT FROM OLD THEN
    NEW."updated_time" := CURRENT_TIMESTAMP;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER "tr_config_asset_baseline_update_time" BEFORE UPDATE ON "config_asset_baseline" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_config_environment_mapping_update_time" BEFORE UPDATE ON "config_environment_mapping" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_config_import_item_update_time" BEFORE UPDATE ON "config_import_item" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_config_migration_asset_update_time" BEFORE UPDATE ON "config_migration_asset" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_application_grant_update_time" BEFORE UPDATE ON "embed_application_grant" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_assertion_replay_update_time" BEFORE UPDATE ON "embed_assertion_replay" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_external_identity_binding_update_time" BEFORE UPDATE ON "embed_external_identity_binding" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_identity_provider_update_time" BEFORE UPDATE ON "embed_identity_provider" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_launch_update_time" BEFORE UPDATE ON "embed_launch" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_session_update_time" BEFORE UPDATE ON "embed_session" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_session_counter_update_time" BEFORE UPDATE ON "embed_session_counter" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_view_update_time" BEFORE UPDATE ON "embed_view" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_embed_view_release_update_time" BEFORE UPDATE ON "embed_view_release" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_code_rule_update_time" BEFORE UPDATE ON "entity_code_rule" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_definition_update_time" BEFORE UPDATE ON "entity_definition" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_field_update_time" BEFORE UPDATE ON "entity_field" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_field_file_item_update_time" BEFORE UPDATE ON "entity_field_file_item" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_field_option_update_time" BEFORE UPDATE ON "entity_field_option" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_form_update_time" BEFORE UPDATE ON "entity_form" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_form_node_update_time" BEFORE UPDATE ON "entity_form_node" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_form_unique_claim_updated_time" BEFORE UPDATE ON "entity_form_unique_claim" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_updated_time();
CREATE TRIGGER "tr_entity_form_unique_value_gate_updated_time" BEFORE UPDATE ON "entity_form_unique_value_gate" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_updated_time();
CREATE TRIGGER "tr_entity_list_action_update_time" BEFORE UPDATE ON "entity_list_action" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_list_config_update_time" BEFORE UPDATE ON "entity_list_config" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_list_field_update_time" BEFORE UPDATE ON "entity_list_field" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_list_scope_binding_update_time" BEFORE UPDATE ON "entity_list_scope_binding" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_list_scope_delegation_update_time" BEFORE UPDATE ON "entity_list_scope_delegation" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_list_scope_policy_update_time" BEFORE UPDATE ON "entity_list_scope_policy" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_mutation_receipt_update_time" BEFORE UPDATE ON "entity_mutation_receipt" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_record_version_counter_update_time" BEFORE UPDATE ON "entity_record_version_counter" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_relation_update_time" BEFORE UPDATE ON "entity_relation" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_schema_operation_update_time" BEFORE UPDATE ON "entity_schema_operation" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_status_update_time" BEFORE UPDATE ON "entity_status" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_unique_value_update_time" BEFORE UPDATE ON "entity_unique_value" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_entity_version_config_update_time" BEFORE UPDATE ON "entity_version_config" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_integration_api_request_lease_update_time" BEFORE UPDATE ON "integration_api_request_lease" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_integration_application_update_time" BEFORE UPDATE ON "integration_application" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_integration_application_credential_update_time" BEFORE UPDATE ON "integration_application_credential" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_integration_idempotency_record_update_time" BEFORE UPDATE ON "integration_idempotency_record" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_integration_rate_limit_bucket_update_time" BEFORE UPDATE ON "integration_rate_limit_bucket" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_action_update_time" BEFORE UPDATE ON "process_action" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_action_definition_update_time" BEFORE UPDATE ON "process_action_definition" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_assignee_incident_update_time" BEFORE UPDATE ON "process_assignee_incident" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_cc_record_update_time" BEFORE UPDATE ON "process_cc_record" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_definition_config_update_time" BEFORE UPDATE ON "process_definition_config" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_entity_status_mapping_update_time" BEFORE UPDATE ON "process_entity_status_mapping" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_form_config_update_time" BEFORE UPDATE ON "process_form_config" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_form_field_config_update_time" BEFORE UPDATE ON "process_form_field_config" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_node_approval_update_time" BEFORE UPDATE ON "process_node_approval" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_node_approval_option_update_time" BEFORE UPDATE ON "process_node_approval_option" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_node_assignee_update_time" BEFORE UPDATE ON "process_node_assignee" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_node_config_update_time" BEFORE UPDATE ON "process_node_config" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_node_form_update_time" BEFORE UPDATE ON "process_node_form" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_person_resolver_definition_update_time" BEFORE UPDATE ON "process_person_resolver_definition" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_task_update_time" BEFORE UPDATE ON "process_task" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_task_sla_update_time" BEFORE UPDATE ON "process_task_sla" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_task_sla_event_update_time" BEFORE UPDATE ON "process_task_sla_event" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_task_sla_pause_update_time" BEFORE UPDATE ON "process_task_sla_pause" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_process_version_history_update_time" BEFORE UPDATE ON "process_version_history" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_dict_update_time" BEFORE UPDATE ON "sys_dict" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_dict_item_update_time" BEFORE UPDATE ON "sys_dict_item" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_external_system_update_time" BEFORE UPDATE ON "sys_external_system" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_external_system_parameter_update_time" BEFORE UPDATE ON "sys_external_system_parameter" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_global_setting_update_time" BEFORE UPDATE ON "sys_global_setting" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_group_update_time" BEFORE UPDATE ON "sys_group" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_menu_update_time" BEFORE UPDATE ON "sys_menu" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_organization_update_time" BEFORE UPDATE ON "sys_organization" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_position_update_time" BEFORE UPDATE ON "sys_position" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_position_assignment_update_time" BEFORE UPDATE ON "sys_position_assignment" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_role_update_time" BEFORE UPDATE ON "sys_role" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_sys_user_update_time" BEFORE UPDATE ON "sys_user" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_task_sla_escalation_step_update_time" BEFORE UPDATE ON "task_sla_escalation_step" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_task_sla_policy_update_time" BEFORE UPDATE ON "task_sla_policy" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_ui_component_template_update_time" BEFORE UPDATE ON "ui_component_template" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_ui_config_hotfix_request_update_time" BEFORE UPDATE ON "ui_config_hotfix_request" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_ui_event_binding_update_time" BEFORE UPDATE ON "ui_event_binding" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_ui_extension_definition_update_time" BEFORE UPDATE ON "ui_extension_definition" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_work_calendar_update_time" BEFORE UPDATE ON "work_calendar" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_work_calendar_binding_update_time" BEFORE UPDATE ON "work_calendar_binding" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
CREATE TRIGGER "tr_workflow_schema_change_update_time" BEFORE UPDATE ON "workflow_schema_change" FOR EACH ROW EXECUTE FUNCTION flow_sql_touch_update_time();
