-- Oracle Database 19c 平台基础表空库初始化脚本。
-- 基于项目数据库导出的 MySQL 平台 DDL 与系统种子数据转换；仅用于预先创建的空 schema。
-- 包含 187 张平台及 Flowable 表、表和字段注释、索引、约束与系统初始化数据。
-- 不含 biz_* 动态业务表、业务记录及普通用户；admin 初始禁用，需通过 Bootstrap 设置密码后激活。
-- 每次导入随机生成独立的配置迁移签名密钥；本文件是独立初始化脚本，不属于 Flyway 迁移。

-- 表结构与表级约束。
CREATE TABLE "ACT_APP_APPDEF" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "NAME_" varchar2(255),
  "KEY_" varchar2(255) NOT NULL,
  "VERSION_" number(10) NOT NULL,
  "CATEGORY_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "RESOURCE_NAME_" varchar2(4000),
  "DESCRIPTION_" varchar2(4000),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_APP_APPDEF_ACT_561297D9" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_APP_DEPLOYMENT" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "KEY_" varchar2(255),
  "DEPLOY_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_APP_DEPLOYMENT_D1A62A5E" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_APP_DEPLOYMENT_RESOURCE" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "RESOURCE_BYTES_" blob,
  CONSTRAINT "PK_ACT_APP_DEPLOYMENT_3F9B6D38" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_CASEDEF" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "NAME_" varchar2(255),
  "KEY_" varchar2(255) NOT NULL,
  "VERSION_" number(10) NOT NULL,
  "CATEGORY_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "RESOURCE_NAME_" varchar2(4000),
  "DESCRIPTION_" varchar2(4000),
  "HAS_GRAPHICAL_NOTATION_" number(1),
  "TENANT_ID_" varchar2(255),
  "DGRM_RESOURCE_NAME_" varchar2(4000),
  "HAS_START_FORM_KEY_" number(1),
  CONSTRAINT "PK_ACT_CMMN_CASEDEF_A_C4C47D42" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_DEPLOYMENT" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "KEY_" varchar2(255),
  "DEPLOY_TIME_" timestamp(3),
  "PARENT_DEPLOYMENT_ID_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_CMMN_DEPLOYMEN_283C7760" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_DEPLOYMENT_RESOURCE" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "RESOURCE_BYTES_" blob,
  "GENERATED_" number(1),
  CONSTRAINT "PK_ACT_CMMN_DEPLOYMEN_58077DD3" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_HI_CASE_INST" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "BUSINESS_KEY_" varchar2(255),
  "NAME_" varchar2(255),
  "PARENT_ID_" varchar2(255),
  "CASE_DEF_ID_" varchar2(255),
  "STATE_" varchar2(255),
  "START_TIME_" timestamp(3),
  "END_TIME_" timestamp(3),
  "START_USER_ID_" varchar2(255),
  "CALLBACK_ID_" varchar2(255),
  "CALLBACK_TYPE_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "REFERENCE_ID_" varchar2(255),
  "REFERENCE_TYPE_" varchar2(255),
  "LAST_REACTIVATION_TIME_" timestamp(3),
  "LAST_REACTIVATION_USER_ID_" varchar2(255),
  "BUSINESS_STATUS_" varchar2(255),
  CONSTRAINT "PK_ACT_CMMN_HI_CASE_I_77500D4E" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_HI_MIL_INST" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "NAME_" varchar2(255) NOT NULL,
  "TIME_STAMP_" timestamp(3),
  "CASE_INST_ID_" varchar2(255) NOT NULL,
  "CASE_DEF_ID_" varchar2(255) NOT NULL,
  "ELEMENT_ID_" varchar2(255) NOT NULL,
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_CMMN_HI_MIL_IN_A758CE97" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_HI_PLAN_ITEM_INST" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "NAME_" varchar2(255),
  "STATE_" varchar2(255),
  "CASE_DEF_ID_" varchar2(255),
  "CASE_INST_ID_" varchar2(255),
  "STAGE_INST_ID_" varchar2(255),
  "IS_STAGE_" number(1),
  "ELEMENT_ID_" varchar2(255),
  "ITEM_DEFINITION_ID_" varchar2(255),
  "ITEM_DEFINITION_TYPE_" varchar2(255),
  "CREATE_TIME_" timestamp(3),
  "LAST_AVAILABLE_TIME_" timestamp(3),
  "LAST_ENABLED_TIME_" timestamp(3),
  "LAST_DISABLED_TIME_" timestamp(3),
  "LAST_STARTED_TIME_" timestamp(3),
  "LAST_SUSPENDED_TIME_" timestamp(3),
  "COMPLETED_TIME_" timestamp(3),
  "OCCURRED_TIME_" timestamp(3),
  "TERMINATED_TIME_" timestamp(3),
  "EXIT_TIME_" timestamp(3),
  "ENDED_TIME_" timestamp(3),
  "LAST_UPDATED_TIME_" timestamp(3),
  "START_USER_ID_" varchar2(255),
  "REFERENCE_ID_" varchar2(255),
  "REFERENCE_TYPE_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "ENTRY_CRITERION_ID_" varchar2(255),
  "EXIT_CRITERION_ID_" varchar2(255),
  "SHOW_IN_OVERVIEW_" number(1),
  "EXTRA_VALUE_" varchar2(255),
  "DERIVED_CASE_DEF_ID_" varchar2(255),
  "LAST_UNAVAILABLE_TIME_" timestamp(3),
  "ASSIGNEE_" varchar2(255),
  "COMPLETED_BY_" varchar2(255),
  CONSTRAINT "PK_ACT_CMMN_HI_PLAN_I_07F2ED0C" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_RU_CASE_INST" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "BUSINESS_KEY_" varchar2(255),
  "NAME_" varchar2(255),
  "PARENT_ID_" varchar2(255),
  "CASE_DEF_ID_" varchar2(255),
  "STATE_" varchar2(255),
  "START_TIME_" timestamp(3),
  "START_USER_ID_" varchar2(255),
  "CALLBACK_ID_" varchar2(255),
  "CALLBACK_TYPE_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "LOCK_TIME_" timestamp(3),
  "IS_COMPLETEABLE_" number(1),
  "REFERENCE_ID_" varchar2(255),
  "REFERENCE_TYPE_" varchar2(255),
  "LOCK_OWNER_" varchar2(255),
  "LAST_REACTIVATION_TIME_" timestamp(3),
  "LAST_REACTIVATION_USER_ID_" varchar2(255),
  "BUSINESS_STATUS_" varchar2(255),
  CONSTRAINT "PK_ACT_CMMN_RU_CASE_I_6D49D702" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_RU_MIL_INST" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255) NOT NULL,
  "TIME_STAMP_" timestamp(3),
  "CASE_INST_ID_" varchar2(255) NOT NULL,
  "CASE_DEF_ID_" varchar2(255) NOT NULL,
  "ELEMENT_ID_" varchar2(255) NOT NULL,
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_CMMN_RU_MIL_IN_D9FC68B8" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_RU_PLAN_ITEM_INST" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "CASE_DEF_ID_" varchar2(255),
  "CASE_INST_ID_" varchar2(255),
  "STAGE_INST_ID_" varchar2(255),
  "IS_STAGE_" number(1),
  "ELEMENT_ID_" varchar2(255),
  "NAME_" varchar2(255),
  "STATE_" varchar2(255),
  "CREATE_TIME_" timestamp(3),
  "START_USER_ID_" varchar2(255),
  "REFERENCE_ID_" varchar2(255),
  "REFERENCE_TYPE_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "ITEM_DEFINITION_ID_" varchar2(255),
  "ITEM_DEFINITION_TYPE_" varchar2(255),
  "IS_COMPLETEABLE_" number(1),
  "IS_COUNT_ENABLED_" number(1),
  "VAR_COUNT_" number(10),
  "SENTRY_PART_INST_COUNT_" number(10),
  "LAST_AVAILABLE_TIME_" timestamp(3),
  "LAST_ENABLED_TIME_" timestamp(3),
  "LAST_DISABLED_TIME_" timestamp(3),
  "LAST_STARTED_TIME_" timestamp(3),
  "LAST_SUSPENDED_TIME_" timestamp(3),
  "COMPLETED_TIME_" timestamp(3),
  "OCCURRED_TIME_" timestamp(3),
  "TERMINATED_TIME_" timestamp(3),
  "EXIT_TIME_" timestamp(3),
  "ENDED_TIME_" timestamp(3),
  "ENTRY_CRITERION_ID_" varchar2(255),
  "EXIT_CRITERION_ID_" varchar2(255),
  "EXTRA_VALUE_" varchar2(255),
  "DERIVED_CASE_DEF_ID_" varchar2(255),
  "LAST_UNAVAILABLE_TIME_" timestamp(3),
  "ASSIGNEE_" varchar2(255),
  "COMPLETED_BY_" varchar2(255),
  CONSTRAINT "PK_ACT_CMMN_RU_PLAN_I_595B8E0E" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_CMMN_RU_SENTRY_PART_INST" (
  "ID_" varchar2(255) NOT NULL,
  "REV_" number(10) NOT NULL,
  "CASE_DEF_ID_" varchar2(255),
  "CASE_INST_ID_" varchar2(255),
  "PLAN_ITEM_INST_ID_" varchar2(255),
  "ON_PART_ID_" varchar2(255),
  "IF_PART_ID_" varchar2(255),
  "TIME_STAMP_" timestamp(3),
  CONSTRAINT "PK_ACT_CMMN_RU_SENTRY_8E57F4C3" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_DMN_DECISION" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "VERSION_" number(10),
  "KEY_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "RESOURCE_NAME_" varchar2(255),
  "DESCRIPTION_" varchar2(255),
  "DECISION_TYPE_" varchar2(255),
  CONSTRAINT "PK_ACT_DMN_DECISION_A_9B068D15" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_DMN_DEPLOYMENT" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "DEPLOY_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  "PARENT_DEPLOYMENT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_DMN_DEPLOYMENT_E3936FBF" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_DMN_DEPLOYMENT_RESOURCE" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "RESOURCE_BYTES_" blob,
  CONSTRAINT "PK_ACT_DMN_DEPLOYMENT_44B9BF58" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_DMN_HI_DECISION_EXECUTION" (
  "ID_" varchar2(255) NOT NULL,
  "DECISION_DEFINITION_ID_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "START_TIME_" timestamp(3),
  "END_TIME_" timestamp(3),
  "INSTANCE_ID_" varchar2(255),
  "EXECUTION_ID_" varchar2(255),
  "ACTIVITY_ID_" varchar2(255),
  "FAILED_" number(1) DEFAULT 0,
  "TENANT_ID_" varchar2(255),
  "EXECUTION_JSON_" clob,
  "SCOPE_TYPE_" varchar2(255),
  CONSTRAINT "PK_ACT_DMN_HI_DECISIO_3BEC0298" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_EVT_LOG" (
  "LOG_NR_" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "TYPE_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "EXECUTION_ID_" varchar2(64),
  "TASK_ID_" varchar2(64),
  "TIME_STAMP_" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "USER_ID_" varchar2(255),
  "DATA_" blob,
  "LOCK_OWNER_" varchar2(255),
  "LOCK_TIME_" timestamp(3),
  "IS_PROCESSED_" number(3) DEFAULT '0',
  CONSTRAINT "PK_ACT_EVT_LOG_ACT_EVT_LOG" PRIMARY KEY ("LOG_NR_")
);

CREATE TABLE "ACT_GE_BYTEARRAY" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "NAME_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(64),
  "BYTES_" blob,
  "GENERATED_" number(3),
  CONSTRAINT "PK_ACT_GE_BYTEARRAY_A_240C4B7B" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_GE_PROPERTY" (
  "NAME_" varchar2(64) NOT NULL,
  "VALUE_" varchar2(300),
  "REV_" number(10),
  CONSTRAINT "PK_ACT_GE_PROPERTY_AC_EBECAC76" PRIMARY KEY ("NAME_")
);

CREATE TABLE "ACT_HI_ACTINST" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10) DEFAULT '1',
  "PROC_DEF_ID_" varchar2(64) NOT NULL,
  "PROC_INST_ID_" varchar2(64) NOT NULL,
  "EXECUTION_ID_" varchar2(64) NOT NULL,
  "ACT_ID_" varchar2(255) NOT NULL,
  "TASK_ID_" varchar2(64),
  "CALL_PROC_INST_ID_" varchar2(64),
  "ACT_NAME_" varchar2(255),
  "ACT_TYPE_" varchar2(255) NOT NULL,
  "ASSIGNEE_" varchar2(255),
  "START_TIME_" timestamp(3) NOT NULL,
  "END_TIME_" timestamp(3),
  "TRANSACTION_ORDER_" number(10),
  "DURATION_" number(19),
  "DELETE_REASON_" varchar2(4000),
  "TENANT_ID_" varchar2(255),
  "COMPLETED_BY_" varchar2(255),
  CONSTRAINT "PK_ACT_HI_ACTINST_ACT_88A5D05B" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_ATTACHMENT" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "USER_ID_" varchar2(255),
  "NAME_" varchar2(255),
  "DESCRIPTION_" varchar2(4000),
  "TYPE_" varchar2(255),
  "TASK_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "URL_" varchar2(4000),
  "CONTENT_ID_" varchar2(64),
  "TIME_" timestamp(3),
  CONSTRAINT "PK_ACT_HI_ATTACHMENT__0ECE9CBD" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_COMMENT" (
  "ID_" varchar2(64) NOT NULL,
  "TYPE_" varchar2(255),
  "TIME_" timestamp(3) NOT NULL,
  "USER_ID_" varchar2(255),
  "TASK_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "ACTION_" varchar2(255),
  "MESSAGE_" varchar2(4000),
  "FULL_MSG_" blob,
  CONSTRAINT "PK_ACT_HI_COMMENT_ACT_D59B5986" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_DETAIL" (
  "ID_" varchar2(64) NOT NULL,
  "TYPE_" varchar2(255) NOT NULL,
  "PROC_INST_ID_" varchar2(64),
  "EXECUTION_ID_" varchar2(64),
  "TASK_ID_" varchar2(64),
  "ACT_INST_ID_" varchar2(64),
  "NAME_" varchar2(255) NOT NULL,
  "VAR_TYPE_" varchar2(255),
  "REV_" number(10),
  "TIME_" timestamp(3) NOT NULL,
  "BYTEARRAY_ID_" varchar2(64),
  "DOUBLE_" binary_double,
  "LONG_" number(19),
  "TEXT_" varchar2(4000),
  "TEXT2_" varchar2(4000),
  CONSTRAINT "PK_ACT_HI_DETAIL_ACT_HI_DETAIL" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_ENTITYLINK" (
  "ID_" varchar2(64) NOT NULL,
  "LINK_TYPE_" varchar2(255),
  "CREATE_TIME_" timestamp(3),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "PARENT_ELEMENT_ID_" varchar2(255),
  "REF_SCOPE_ID_" varchar2(255),
  "REF_SCOPE_TYPE_" varchar2(255),
  "REF_SCOPE_DEFINITION_ID_" varchar2(255),
  "ROOT_SCOPE_ID_" varchar2(255),
  "ROOT_SCOPE_TYPE_" varchar2(255),
  "HIERARCHY_TYPE_" varchar2(255),
  CONSTRAINT "PK_ACT_HI_ENTITYLINK__C45A08EE" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_IDENTITYLINK" (
  "ID_" varchar2(64) NOT NULL,
  "GROUP_ID_" varchar2(255),
  "TYPE_" varchar2(255),
  "USER_ID_" varchar2(255),
  "TASK_ID_" varchar2(64),
  "CREATE_TIME_" timestamp(3),
  "PROC_INST_ID_" varchar2(64),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_HI_IDENTITYLIN_33672492" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_PROCINST" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10) DEFAULT '1',
  "PROC_INST_ID_" varchar2(64) NOT NULL,
  "BUSINESS_KEY_" varchar2(255),
  "PROC_DEF_ID_" varchar2(64) NOT NULL,
  "START_TIME_" timestamp(3) NOT NULL,
  "END_TIME_" timestamp(3),
  "DURATION_" number(19),
  "START_USER_ID_" varchar2(255),
  "START_ACT_ID_" varchar2(255),
  "END_ACT_ID_" varchar2(255),
  "SUPER_PROCESS_INSTANCE_ID_" varchar2(64),
  "DELETE_REASON_" varchar2(4000),
  "TENANT_ID_" varchar2(255),
  "NAME_" varchar2(255),
  "CALLBACK_ID_" varchar2(255),
  "CALLBACK_TYPE_" varchar2(255),
  "REFERENCE_ID_" varchar2(255),
  "REFERENCE_TYPE_" varchar2(255),
  "PROPAGATED_STAGE_INST_ID_" varchar2(255),
  "BUSINESS_STATUS_" varchar2(255),
  CONSTRAINT "PK_ACT_HI_PROCINST_AC_60BEA6F3" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_TASKINST" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10) DEFAULT '1',
  "PROC_DEF_ID_" varchar2(64),
  "TASK_DEF_ID_" varchar2(64),
  "TASK_DEF_KEY_" varchar2(255),
  "PROC_INST_ID_" varchar2(64),
  "EXECUTION_ID_" varchar2(64),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "PROPAGATED_STAGE_INST_ID_" varchar2(255),
  "NAME_" varchar2(255),
  "PARENT_TASK_ID_" varchar2(64),
  "DESCRIPTION_" varchar2(4000),
  "OWNER_" varchar2(255),
  "ASSIGNEE_" varchar2(255),
  "START_TIME_" timestamp(3) NOT NULL,
  "CLAIM_TIME_" timestamp(3),
  "END_TIME_" timestamp(3),
  "DURATION_" number(19),
  "DELETE_REASON_" varchar2(4000),
  "PRIORITY_" number(10),
  "DUE_DATE_" timestamp(3),
  "FORM_KEY_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "LAST_UPDATED_TIME_" timestamp(3),
  "STATE_" varchar2(255),
  "IN_PROGRESS_TIME_" timestamp(3),
  "IN_PROGRESS_STARTED_BY_" varchar2(255),
  "CLAIMED_BY_" varchar2(255),
  "SUSPENDED_TIME_" timestamp(3),
  "SUSPENDED_BY_" varchar2(255),
  "COMPLETED_BY_" varchar2(255),
  "IN_PROGRESS_DUE_DATE_" timestamp(3),
  CONSTRAINT "PK_ACT_HI_TASKINST_AC_1ECEFA12" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_TSK_LOG" (
  "ID_" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "TYPE_" varchar2(64),
  "TASK_ID_" varchar2(64) NOT NULL,
  "TIME_STAMP_" timestamp(3) NOT NULL,
  "USER_ID_" varchar2(255),
  "DATA_" varchar2(4000),
  "EXECUTION_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "SCOPE_ID_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_HI_TSK_LOG_ACT_1EEC1BFF" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_HI_VARINST" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10) DEFAULT '1',
  "PROC_INST_ID_" varchar2(64),
  "EXECUTION_ID_" varchar2(64),
  "TASK_ID_" varchar2(64),
  "NAME_" varchar2(255) NOT NULL,
  "VAR_TYPE_" varchar2(100),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "BYTEARRAY_ID_" varchar2(64),
  "DOUBLE_" binary_double,
  "LONG_" number(19),
  "TEXT_" varchar2(4000),
  "TEXT2_" varchar2(4000),
  "CREATE_TIME_" timestamp(3),
  "LAST_UPDATED_TIME_" timestamp(3),
  "META_INFO_" varchar2(4000),
  CONSTRAINT "PK_ACT_HI_VARINST_ACT_DA8BA62B" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_ID_BYTEARRAY" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "NAME_" varchar2(255),
  "BYTES_" blob,
  CONSTRAINT "PK_ACT_ID_BYTEARRAY_A_C707EFDC" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_ID_GROUP" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "NAME_" varchar2(255),
  "TYPE_" varchar2(255),
  CONSTRAINT "PK_ACT_ID_GROUP_ACT_ID_GROUP" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_ID_INFO" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "USER_ID_" varchar2(64),
  "TYPE_" varchar2(64),
  "KEY_" varchar2(255),
  "VALUE_" varchar2(255),
  "PASSWORD_" blob,
  "PARENT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_ID_INFO_ACT_ID_INFO" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_ID_MEMBERSHIP" (
  "USER_ID_" varchar2(64) NOT NULL,
  "GROUP_ID_" varchar2(64) NOT NULL,
  CONSTRAINT "PK_ACT_ID_MEMBERSHIP__9E1541DF" PRIMARY KEY ("USER_ID_", "GROUP_ID_")
);

CREATE TABLE "ACT_ID_PRIV" (
  "ID_" varchar2(64) NOT NULL,
  "NAME_" varchar2(255) NOT NULL,
  CONSTRAINT "PK_ACT_ID_PRIV_ACT_ID_PRIV" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_ID_PRIV_MAPPING" (
  "ID_" varchar2(64) NOT NULL,
  "PRIV_ID_" varchar2(64) NOT NULL,
  "USER_ID_" varchar2(255),
  "GROUP_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_ID_PRIV_MAPPIN_9CD9C649" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_ID_PROPERTY" (
  "NAME_" varchar2(64) NOT NULL,
  "VALUE_" varchar2(300),
  "REV_" number(10),
  CONSTRAINT "PK_ACT_ID_PROPERTY_AC_B9EFC249" PRIMARY KEY ("NAME_")
);

CREATE TABLE "ACT_ID_TOKEN" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "TOKEN_VALUE_" varchar2(255),
  "TOKEN_DATE_" timestamp(3),
  "IP_ADDRESS_" varchar2(255),
  "USER_AGENT_" varchar2(255),
  "USER_ID_" varchar2(255),
  "TOKEN_DATA_" varchar2(2000),
  CONSTRAINT "PK_ACT_ID_TOKEN_ACT_ID_TOKEN" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_ID_USER" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "FIRST_" varchar2(255),
  "LAST_" varchar2(255),
  "DISPLAY_NAME_" varchar2(255),
  "EMAIL_" varchar2(255),
  "PWD_" varchar2(255),
  "PICTURE_ID_" varchar2(64),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_ID_USER_ACT_ID_USER" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_PROCDEF_INFO" (
  "ID_" varchar2(64) NOT NULL,
  "PROC_DEF_ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "INFO_JSON_ID_" varchar2(64),
  CONSTRAINT "PK_ACT_PROCDEF_INFO_A_583708D9" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RE_DEPLOYMENT" (
  "ID_" varchar2(64) NOT NULL,
  "NAME_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "KEY_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "DEPLOY_TIME_" timestamp(3),
  "DERIVED_FROM_" varchar2(64),
  "DERIVED_FROM_ROOT_" varchar2(64),
  "PARENT_DEPLOYMENT_ID_" varchar2(255),
  "ENGINE_VERSION_" varchar2(255),
  CONSTRAINT "PK_ACT_RE_DEPLOYMENT__58810E7F" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RE_MODEL" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "NAME_" varchar2(255),
  "KEY_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "CREATE_TIME_" timestamp(3),
  "LAST_UPDATE_TIME_" timestamp(3),
  "VERSION_" number(10),
  "META_INFO_" varchar2(4000),
  "DEPLOYMENT_ID_" varchar2(64),
  "EDITOR_SOURCE_VALUE_ID_" varchar2(64),
  "EDITOR_SOURCE_EXTRA_VALUE_ID_" varchar2(64),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RE_MODEL_ACT_RE_MODEL" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RE_PROCDEF" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "CATEGORY_" varchar2(255),
  "NAME_" varchar2(255),
  "KEY_" varchar2(255) NOT NULL,
  "VERSION_" number(10) NOT NULL,
  "DEPLOYMENT_ID_" varchar2(64),
  "RESOURCE_NAME_" varchar2(4000),
  "DGRM_RESOURCE_NAME_" varchar2(4000),
  "DESCRIPTION_" varchar2(4000),
  "HAS_START_FORM_KEY_" number(3),
  "HAS_GRAPHICAL_NOTATION_" number(3),
  "SUSPENSION_STATE_" number(10),
  "TENANT_ID_" varchar2(255),
  "ENGINE_VERSION_" varchar2(255),
  "DERIVED_FROM_" varchar2(64),
  "DERIVED_FROM_ROOT_" varchar2(64),
  "DERIVED_VERSION_" number(10) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_ACT_RE_PROCDEF_ACT_E098D119" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_ACTINST" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10) DEFAULT '1',
  "PROC_DEF_ID_" varchar2(64) NOT NULL,
  "PROC_INST_ID_" varchar2(64) NOT NULL,
  "EXECUTION_ID_" varchar2(64) NOT NULL,
  "ACT_ID_" varchar2(255) NOT NULL,
  "TASK_ID_" varchar2(64),
  "CALL_PROC_INST_ID_" varchar2(64),
  "ACT_NAME_" varchar2(255),
  "ACT_TYPE_" varchar2(255) NOT NULL,
  "ASSIGNEE_" varchar2(255),
  "START_TIME_" timestamp(3) NOT NULL,
  "END_TIME_" timestamp(3),
  "DURATION_" number(19),
  "TRANSACTION_ORDER_" number(10),
  "DELETE_REASON_" varchar2(4000),
  "TENANT_ID_" varchar2(255),
  "COMPLETED_BY_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_ACTINST_ACT_63D654B5" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_DEADLETTER_JOB" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "CATEGORY_" varchar2(255),
  "TYPE_" varchar2(255) NOT NULL,
  "EXCLUSIVE_" number(3),
  "EXECUTION_ID_" varchar2(64),
  "PROCESS_INSTANCE_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "ELEMENT_ID_" varchar2(255),
  "ELEMENT_NAME_" varchar2(255),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "CORRELATION_ID_" varchar2(255),
  "EXCEPTION_STACK_ID_" varchar2(64),
  "EXCEPTION_MSG_" varchar2(4000),
  "DUEDATE_" timestamp(3),
  "REPEAT_" varchar2(255),
  "HANDLER_TYPE_" varchar2(255),
  "HANDLER_CFG_" varchar2(4000),
  "CUSTOM_VALUES_ID_" varchar2(64),
  "CREATE_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_DEADLETTER__32D43BD7" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_ENTITYLINK" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "CREATE_TIME_" timestamp(3),
  "LINK_TYPE_" varchar2(255),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "PARENT_ELEMENT_ID_" varchar2(255),
  "REF_SCOPE_ID_" varchar2(255),
  "REF_SCOPE_TYPE_" varchar2(255),
  "REF_SCOPE_DEFINITION_ID_" varchar2(255),
  "ROOT_SCOPE_ID_" varchar2(255),
  "ROOT_SCOPE_TYPE_" varchar2(255),
  "HIERARCHY_TYPE_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_ENTITYLINK__170EC813" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_EVENT_SUBSCR" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "EVENT_TYPE_" varchar2(255) NOT NULL,
  "EVENT_NAME_" varchar2(255),
  "EXECUTION_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "ACTIVITY_ID_" varchar2(64),
  "CONFIGURATION_" varchar2(255),
  "CREATED_" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "PROC_DEF_ID_" varchar2(64),
  "SUB_SCOPE_ID_" varchar2(64),
  "SCOPE_ID_" varchar2(64),
  "SCOPE_DEFINITION_ID_" varchar2(64),
  "SCOPE_TYPE_" varchar2(64),
  "LOCK_TIME_" timestamp(3),
  "LOCK_OWNER_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "SCOPE_DEFINITION_KEY_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_EVENT_SUBSC_73AA5CAA" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_EXECUTION" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "PROC_INST_ID_" varchar2(64),
  "BUSINESS_KEY_" varchar2(255),
  "PARENT_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "SUPER_EXEC_" varchar2(64),
  "ROOT_PROC_INST_ID_" varchar2(64),
  "ACT_ID_" varchar2(255),
  "IS_ACTIVE_" number(3),
  "IS_CONCURRENT_" number(3),
  "IS_SCOPE_" number(3),
  "IS_EVENT_SCOPE_" number(3),
  "IS_MI_ROOT_" number(3),
  "SUSPENSION_STATE_" number(10),
  "CACHED_ENT_STATE_" number(10),
  "TENANT_ID_" varchar2(255),
  "NAME_" varchar2(255),
  "START_ACT_ID_" varchar2(255),
  "START_TIME_" timestamp(3),
  "START_USER_ID_" varchar2(255),
  "LOCK_TIME_" timestamp(3),
  "LOCK_OWNER_" varchar2(255),
  "IS_COUNT_ENABLED_" number(3),
  "EVT_SUBSCR_COUNT_" number(10),
  "TASK_COUNT_" number(10),
  "JOB_COUNT_" number(10),
  "TIMER_JOB_COUNT_" number(10),
  "SUSP_JOB_COUNT_" number(10),
  "DEADLETTER_JOB_COUNT_" number(10),
  "EXTERNAL_WORKER_JOB_COUNT_" number(10),
  "VAR_COUNT_" number(10),
  "ID_LINK_COUNT_" number(10),
  "CALLBACK_ID_" varchar2(255),
  "CALLBACK_TYPE_" varchar2(255),
  "REFERENCE_ID_" varchar2(255),
  "REFERENCE_TYPE_" varchar2(255),
  "PROPAGATED_STAGE_INST_ID_" varchar2(255),
  "BUSINESS_STATUS_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_EXECUTION_A_73033FA3" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_EXTERNAL_JOB" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "CATEGORY_" varchar2(255),
  "TYPE_" varchar2(255) NOT NULL,
  "LOCK_EXP_TIME_" timestamp(3),
  "LOCK_OWNER_" varchar2(255),
  "EXCLUSIVE_" number(3),
  "EXECUTION_ID_" varchar2(64),
  "PROCESS_INSTANCE_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "ELEMENT_ID_" varchar2(255),
  "ELEMENT_NAME_" varchar2(255),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "CORRELATION_ID_" varchar2(255),
  "RETRIES_" number(10),
  "EXCEPTION_STACK_ID_" varchar2(64),
  "EXCEPTION_MSG_" varchar2(4000),
  "DUEDATE_" timestamp(3),
  "REPEAT_" varchar2(255),
  "HANDLER_TYPE_" varchar2(255),
  "HANDLER_CFG_" varchar2(4000),
  "CUSTOM_VALUES_ID_" varchar2(64),
  "CREATE_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_EXTERNAL_JO_D406A02F" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_HISTORY_JOB" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "LOCK_EXP_TIME_" timestamp(3),
  "LOCK_OWNER_" varchar2(255),
  "RETRIES_" number(10),
  "EXCEPTION_STACK_ID_" varchar2(64),
  "EXCEPTION_MSG_" varchar2(4000),
  "HANDLER_TYPE_" varchar2(255),
  "HANDLER_CFG_" varchar2(4000),
  "CUSTOM_VALUES_ID_" varchar2(64),
  "ADV_HANDLER_CFG_ID_" varchar2(64),
  "CREATE_TIME_" timestamp(3),
  "SCOPE_TYPE_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_HISTORY_JOB_46775C22" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_IDENTITYLINK" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "GROUP_ID_" varchar2(255),
  "TYPE_" varchar2(255),
  "USER_ID_" varchar2(255),
  "TASK_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_IDENTITYLIN_F42C6F24" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_JOB" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "CATEGORY_" varchar2(255),
  "TYPE_" varchar2(255) NOT NULL,
  "LOCK_EXP_TIME_" timestamp(3),
  "LOCK_OWNER_" varchar2(255),
  "EXCLUSIVE_" number(3),
  "EXECUTION_ID_" varchar2(64),
  "PROCESS_INSTANCE_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "ELEMENT_ID_" varchar2(255),
  "ELEMENT_NAME_" varchar2(255),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "CORRELATION_ID_" varchar2(255),
  "RETRIES_" number(10),
  "EXCEPTION_STACK_ID_" varchar2(64),
  "EXCEPTION_MSG_" varchar2(4000),
  "DUEDATE_" timestamp(3),
  "REPEAT_" varchar2(255),
  "HANDLER_TYPE_" varchar2(255),
  "HANDLER_CFG_" varchar2(4000),
  "CUSTOM_VALUES_ID_" varchar2(64),
  "CREATE_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_JOB_ACT_RU_JOB" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_SUSPENDED_JOB" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "CATEGORY_" varchar2(255),
  "TYPE_" varchar2(255) NOT NULL,
  "EXCLUSIVE_" number(3),
  "EXECUTION_ID_" varchar2(64),
  "PROCESS_INSTANCE_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "ELEMENT_ID_" varchar2(255),
  "ELEMENT_NAME_" varchar2(255),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "CORRELATION_ID_" varchar2(255),
  "RETRIES_" number(10),
  "EXCEPTION_STACK_ID_" varchar2(64),
  "EXCEPTION_MSG_" varchar2(4000),
  "DUEDATE_" timestamp(3),
  "REPEAT_" varchar2(255),
  "HANDLER_TYPE_" varchar2(255),
  "HANDLER_CFG_" varchar2(4000),
  "CUSTOM_VALUES_ID_" varchar2(64),
  "CREATE_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_SUSPENDED_J_BC4C8A8B" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_TASK" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "EXECUTION_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "TASK_DEF_ID_" varchar2(64),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "PROPAGATED_STAGE_INST_ID_" varchar2(255),
  "NAME_" varchar2(255),
  "PARENT_TASK_ID_" varchar2(64),
  "DESCRIPTION_" varchar2(4000),
  "TASK_DEF_KEY_" varchar2(255),
  "OWNER_" varchar2(255),
  "ASSIGNEE_" varchar2(255),
  "DELEGATION_" varchar2(64),
  "PRIORITY_" number(10),
  "CREATE_TIME_" timestamp(3),
  "DUE_DATE_" timestamp(3),
  "CATEGORY_" varchar2(255),
  "SUSPENSION_STATE_" number(10),
  "TENANT_ID_" varchar2(255),
  "FORM_KEY_" varchar2(255),
  "CLAIM_TIME_" timestamp(3),
  "IS_COUNT_ENABLED_" number(3),
  "VAR_COUNT_" number(10),
  "ID_LINK_COUNT_" number(10),
  "SUB_TASK_COUNT_" number(10),
  "STATE_" varchar2(255),
  "IN_PROGRESS_TIME_" timestamp(3),
  "IN_PROGRESS_STARTED_BY_" varchar2(255),
  "CLAIMED_BY_" varchar2(255),
  "SUSPENDED_TIME_" timestamp(3),
  "SUSPENDED_BY_" varchar2(255),
  "IN_PROGRESS_DUE_DATE_" timestamp(3),
  CONSTRAINT "PK_ACT_RU_TASK_ACT_RU_TASK" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_TIMER_JOB" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "CATEGORY_" varchar2(255),
  "TYPE_" varchar2(255) NOT NULL,
  "LOCK_EXP_TIME_" timestamp(3),
  "LOCK_OWNER_" varchar2(255),
  "EXCLUSIVE_" number(3),
  "EXECUTION_ID_" varchar2(64),
  "PROCESS_INSTANCE_ID_" varchar2(64),
  "PROC_DEF_ID_" varchar2(64),
  "ELEMENT_ID_" varchar2(255),
  "ELEMENT_NAME_" varchar2(255),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "SCOPE_DEFINITION_ID_" varchar2(255),
  "CORRELATION_ID_" varchar2(255),
  "RETRIES_" number(10),
  "EXCEPTION_STACK_ID_" varchar2(64),
  "EXCEPTION_MSG_" varchar2(4000),
  "DUEDATE_" timestamp(3),
  "REPEAT_" varchar2(255),
  "HANDLER_TYPE_" varchar2(255),
  "HANDLER_CFG_" varchar2(4000),
  "CUSTOM_VALUES_ID_" varchar2(64),
  "CREATE_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_ACT_RU_TIMER_JOB_A_C0675D77" PRIMARY KEY ("ID_")
);

CREATE TABLE "ACT_RU_VARIABLE" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "TYPE_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255) NOT NULL,
  "EXECUTION_ID_" varchar2(64),
  "PROC_INST_ID_" varchar2(64),
  "TASK_ID_" varchar2(64),
  "SCOPE_ID_" varchar2(255),
  "SUB_SCOPE_ID_" varchar2(255),
  "SCOPE_TYPE_" varchar2(255),
  "BYTEARRAY_ID_" varchar2(64),
  "DOUBLE_" binary_double,
  "LONG_" number(19),
  "TEXT_" varchar2(4000),
  "TEXT2_" varchar2(4000),
  "META_INFO_" varchar2(4000),
  CONSTRAINT "PK_ACT_RU_VARIABLE_AC_9B00957F" PRIMARY KEY ("ID_")
);

CREATE TABLE "AUTH_LOGIN_THROTTLE" (
  "THROTTLE_KEY" char(66) NOT NULL,
  "FAILURE_COUNT" number(10) NOT NULL DEFAULT '0',
  "WINDOW_STARTED_AT" timestamp(6) NOT NULL,
  "BLOCKED_UNTIL" timestamp(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_AUTH_LOGIN_THROTTL_5E6821C2" PRIMARY KEY ("THROTTLE_KEY")
);

CREATE TABLE "AUTH_REFRESH_SESSION" (
  "ID" varchar2(64) NOT NULL,
  "USER_ID" varchar2(64) NOT NULL,
  "REFRESH_TOKEN_HASH" char(64) NOT NULL,
  "TOKEN_VERSION" number(19) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL,
  "LAST_USED_AT" timestamp(6) NOT NULL,
  "IDLE_EXPIRES_AT" timestamp(6) NOT NULL,
  "ABSOLUTE_EXPIRES_AT" timestamp(6) NOT NULL,
  "REVOKED_AT" timestamp(6),
  "REVOKED_REASON" varchar2(64),
  CONSTRAINT "PK_AUTH_REFRESH_SESSI_4F22B168" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_ASSET_BASELINE" (
  "ID" varchar2(64) NOT NULL,
  "ASSET_TYPE" varchar2(20) NOT NULL,
  "BUSINESS_KEY" varchar2(100) NOT NULL,
  "SCOPE_KEY" varchar2(80) NOT NULL DEFAULT 'FULL',
  "SOURCE_VERSION" number(10) NOT NULL,
  "SOURCE_HASH" varchar2(64) NOT NULL,
  "TARGET_VERSION" number(10),
  "TARGET_HASH" varchar2(64) NOT NULL,
  "IMPORT_PACKAGE_ID" varchar2(64) NOT NULL,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_CONFIG_ASSET_BASEL_DC415895" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_ENVIRONMENT_MAPPING" (
  "ID" varchar2(64) NOT NULL,
  "SOURCE_TYPE" varchar2(30) NOT NULL,
  "SOURCE_KEY" varchar2(200) NOT NULL,
  "TARGET_KEY" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_CONFIG_ENVIRONMENT_CC45F0D9" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_EXPORT_PACKAGE" (
  "ID" varchar2(64) NOT NULL,
  "PACKAGE_NO" varchar2(100) NOT NULL,
  "MIGRATION_TAG" varchar2(100) NOT NULL,
  "FILE_NAME" varchar2(255) NOT NULL,
  "CHECKSUM" varchar2(64) NOT NULL,
  "SIGNATURE_VALUE" varchar2(128),
  "STATUS" varchar2(20) NOT NULL DEFAULT 'READY',
  "ASSET_COUNT" number(10) NOT NULL DEFAULT '0',
  "PACKAGE_DATA" blob NOT NULL,
  "CREATED_BY" varchar2(100),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DOWNLOAD_COUNT" number(10) NOT NULL DEFAULT '0',
  "LAST_DOWNLOAD_AT" timestamp,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_CONFIG_EXPORT_PACK_53B6A821" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_EXPORT_PACKAGE_ITEM" (
  "ID" varchar2(64) NOT NULL,
  "PACKAGE_ID" varchar2(64) NOT NULL,
  "ASSET_ID" varchar2(64) NOT NULL,
  "ASSET_TYPE" varchar2(20) NOT NULL,
  "BUSINESS_KEY" varchar2(100) NOT NULL,
  "SOURCE_VERSION" number(10) NOT NULL,
  "CONTENT_HASH" varchar2(64) NOT NULL,
  "SELECTION_JSON" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_CONFIG_EXPORT_PACK_CE591972" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_IMPORT_ITEM" (
  "ID" varchar2(64) NOT NULL,
  "IMPORT_PACKAGE_ID" varchar2(64) NOT NULL,
  "ASSET_TYPE" varchar2(20) NOT NULL,
  "BUSINESS_KEY" varchar2(100) NOT NULL,
  "ASSET_NAME" varchar2(200) NOT NULL,
  "SOURCE_VERSION" number(10) NOT NULL,
  "SOURCE_HASH" varchar2(64) NOT NULL,
  "TARGET_BEFORE_VERSION" number(10),
  "TARGET_BEFORE_HASH" varchar2(64),
  "TARGET_AFTER_VERSION" number(10),
  "TARGET_AFTER_HASH" varchar2(64),
  "COMPARISON_STATUS" varchar2(30) NOT NULL DEFAULT 'NEW',
  "MAPPING_STATUS" varchar2(20) NOT NULL DEFAULT 'RESOLVED',
  "PUBLISH_STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "SNAPSHOT_JSON" clob NOT NULL,
  "DEPENDENCIES_JSON" clob,
  "ERROR_MESSAGE" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_CONFIG_IMPORT_ITEM_C859BB1C" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_IMPORT_PACKAGE" (
  "ID" varchar2(64) NOT NULL,
  "PACKAGE_NO" varchar2(100) NOT NULL,
  "SOURCE_ENVIRONMENT" varchar2(100),
  "MIGRATION_TAG" varchar2(100) NOT NULL,
  "FILE_NAME" varchar2(255) NOT NULL,
  "CHECKSUM" varchar2(64) NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'UPLOADED',
  "VALIDATION_REPORT_JSON" clob,
  "PACKAGE_DATA" blob NOT NULL,
  "IMPORTED_BY" varchar2(100),
  "IMPORTED_AT" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "PUBLISHED_BY" varchar2(100),
  "PUBLISHED_AT" timestamp,
  "ERROR_MESSAGE" clob,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  "SIGNATURE_STATUS" varchar2(32) NOT NULL DEFAULT 'UNKNOWN',
  "SIGNATURE_CONFIRMED_BY" varchar2(100),
  "SIGNATURE_CONFIRMED_AT" timestamp(6),
  CONSTRAINT "PK_CONFIG_IMPORT_PACK_F4F68B2D" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_MIGRATION_ASSET" (
  "ID" varchar2(64) NOT NULL,
  "ASSET_TYPE" varchar2(20) NOT NULL,
  "BUSINESS_KEY" varchar2(100) NOT NULL,
  "ASSET_NAME" varchar2(200) NOT NULL,
  "SOURCE_HISTORY_ID" varchar2(64) NOT NULL,
  "SOURCE_VERSION" number(10) NOT NULL,
  "VERSION_DESCRIPTION" varchar2(500),
  "MIGRATION_TAG" varchar2(100) NOT NULL,
  "MARK_FOR_EXPORT" number(3) NOT NULL DEFAULT '1',
  "SNAPSHOT_COMPLETENESS" varchar2(20) NOT NULL DEFAULT 'COMPLETE',
  "SNAPSHOT_SCHEMA_VERSION" number(10) NOT NULL DEFAULT '1',
  "SNAPSHOT_JSON" clob NOT NULL,
  "CONTENT_HASH" varchar2(64) NOT NULL,
  "DEPENDENCIES_JSON" clob,
  "DEPENDENCY_COUNT" number(10) NOT NULL DEFAULT '0',
  "MISSING_DEPENDENCY_COUNT" number(10) NOT NULL DEFAULT '0',
  "EXPORT_STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "PUBLISHED_AT" timestamp,
  "PUBLISHED_BY" varchar2(100),
  "LAST_EXPORT_AT" timestamp,
  "EXPORT_COUNT" number(10) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_CONFIG_MIGRATION_A_4B33CD86" PRIMARY KEY ("ID")
);

CREATE TABLE "CONFIG_MIGRATION_ASSET_DEPENDENCY" (
  "ID" varchar2(64) NOT NULL,
  "ASSET_ID" varchar2(64) NOT NULL,
  "DEPENDENCY_TYPE" varchar2(50) NOT NULL,
  "DEPENDENCY_KEY" varchar2(300) NOT NULL,
  "REQUIRED" number(3) NOT NULL DEFAULT '1',
  "SOURCE_DESCRIPTION" varchar2(500),
  "DEPENDENCY_DOCUMENT" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "SOURCE_ASSET_TYPE" varchar2(20),
  "SOURCE_BUSINESS_KEY" varchar2(100),
  "SOURCE_VERSION" number(10),
  "REFERENCE_LOCATION" varchar2(500),
  "DEPENDENCY_STRENGTH" varchar2(20) NOT NULL DEFAULT 'HARD',
  "PARSE_STATUS" varchar2(20) NOT NULL DEFAULT 'RESOLVED',
  "EXTRACTED_AT" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT "PK_CONFIG_MIGRATION_A_A9A9B5EB" PRIMARY KEY ("ID")
);

CREATE TABLE "EMBED_ALLOWED_ORIGIN" (
  "GRANT_ID" varchar2(64) NOT NULL,
  "ORIGIN" varchar2(255) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_ALLOWED_ORIG_2D9ECA2B" PRIMARY KEY ("GRANT_ID", "ORIGIN"),
  CONSTRAINT "CK_EMBED_ALLOWED_ORIG_ED77270F" CHECK (regexp_like("ORIGIN",'^https://[^/?#]+$'))
);

CREATE TABLE "EMBED_APPLICATION_GRANT" (
  "ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "VIEW_ID" varchar2(64) NOT NULL,
  "IDENTITY_PROVIDER_ID" varchar2(64) NOT NULL,
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ACTIVE',
  "TRUSTED_SUBJECT_ASSERTION" number(3) NOT NULL DEFAULT '0',
  "REVISION_MODE" varchar2(16) NOT NULL DEFAULT 'FOLLOW_ACTIVE',
  "PINNED_REVISION" number(19),
  "CAPABILITY_CEILING_JSON" clob NOT NULL,
  "MAX_ACTIVE_SESSIONS_PER_USER" number(10) NOT NULL DEFAULT '1',
  "MAX_SESSION_SECONDS" number(10) NOT NULL DEFAULT '1800',
  "LAUNCH_LIMIT_PER_MINUTE" number(10) NOT NULL DEFAULT '60',
  "RUNTIME_LIMIT_PER_MINUTE" number(10) NOT NULL DEFAULT '600',
  "MAX_CONCURRENCY" number(10) NOT NULL DEFAULT '10',
  "EXPIRES_AT" timestamp(6),
  "LOCK_VERSION" number(19) NOT NULL DEFAULT '1',
  "SECURITY_VERSION" number(19) NOT NULL DEFAULT '1',
  "CREATE_BY" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_BY" varchar2(64) NOT NULL,
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "REVOKED_BY" varchar2(64),
  "REVOKED_AT" timestamp(6),
  CONSTRAINT "PK_EMBED_APPLICATION__A9F69EA8" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_APPLICATION__8EF9BF34" CHECK (("CAPABILITY_CEILING_JSON" IS JSON and (length("CAPABILITY_CEILING_JSON") <= 65536))),
  CONSTRAINT "CK_EMBED_APPLICATION__1B759C12" CHECK ((("MAX_ACTIVE_SESSIONS_PER_USER" between 1 and 10000) and ("MAX_SESSION_SECONDS" between 60 and 86400) and ("LAUNCH_LIMIT_PER_MINUTE" between 1 and 10000) and ("RUNTIME_LIMIT_PER_MINUTE" between 1 and 100000) and ("MAX_CONCURRENCY" between 1 and 1000))),
  CONSTRAINT "CK_EMBED_APPLICATION__FD067064" CHECK (((("REVISION_MODE" = 'FOLLOW_ACTIVE') and ("PINNED_REVISION" is null)) or (("REVISION_MODE" = 'PINNED') and ("PINNED_REVISION" > 0)))),
  CONSTRAINT "CK_EMBED_APPLICATION__1BC8C20A" CHECK (((("STATUS" = 'REVOKED') and ("REVOKED_BY" is not null) and ("REVOKED_AT" is not null)) or (("STATUS" <> 'REVOKED') and ("REVOKED_BY" is null) and ("REVOKED_AT" is null)))),
  CONSTRAINT "CK_EMBED_APPLICATION__018B15C4" CHECK (("STATUS" in ('ACTIVE','DISABLED','REVOKED'))),
  CONSTRAINT "CK_EMBED_APPLICATION__04156111" CHECK (("TRUSTED_SUBJECT_ASSERTION" in (0,1))),
  CONSTRAINT "CK_EMBED_APPLICATION__59C369D6" CHECK ((("LOCK_VERSION" > 0) and ("SECURITY_VERSION" > 0)))
);

CREATE TABLE "EMBED_ASSERTION_REPLAY" (
  "PROVIDER_ID" varchar2(64) NOT NULL,
  "JTI_DIGEST" char(64) NOT NULL,
  "EXPIRES_AT" timestamp(6) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_ASSERTION_RE_8A485FE4" PRIMARY KEY ("PROVIDER_ID", "JTI_DIGEST"),
  CONSTRAINT "CK_EMBED_ASSERTION_RE_8CD440A4" CHECK (regexp_like("JTI_DIGEST",'^[0-9a-f]{64}$')),
  CONSTRAINT "CK_EMBED_ASSERTION_RE_2CAFAE41" CHECK (("EXPIRES_AT" > "CREATE_TIME"))
);

CREATE TABLE "EMBED_EXTERNAL_IDENTITY_BINDING" (
  "ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "IDENTITY_PROVIDER_ID" varchar2(64) NOT NULL,
  "SUBJECT_DIGEST" char(64) NOT NULL,
  "SUBJECT_DIGEST_KEY_VERSION" varchar2(64) NOT NULL,
  "SUBJECT_HINT" varchar2(128) NOT NULL,
  "FLOW_USER_ID" varchar2(64) NOT NULL,
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ACTIVE',
  "BINDING_VERSION" number(19) NOT NULL DEFAULT '1',
  "EFFECTIVE_AT" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "EXPIRES_AT" timestamp(6),
  "CREATE_BY" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_BY" varchar2(64) NOT NULL,
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "REVOKED_BY" varchar2(64),
  "REVOKED_AT" timestamp(6),
  CONSTRAINT "PK_EMBED_EXTERNAL_IDE_0FA97D79" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_EXTERNAL_IDE_4D9AD403" CHECK (regexp_like("SUBJECT_DIGEST",'^[0-9a-f]{64}$')),
  CONSTRAINT "CK_EMBED_EXTERNAL_IDE_9D0024D5" CHECK (((("STATUS" = 'REVOKED') and ("REVOKED_BY" is not null) and ("REVOKED_AT" is not null)) or (("STATUS" <> 'REVOKED') and ("REVOKED_BY" is null) and ("REVOKED_AT" is null)))),
  CONSTRAINT "CK_EMBED_EXTERNAL_IDE_50173D6B" CHECK (("STATUS" in ('ACTIVE','DISABLED','REVOKED'))),
  CONSTRAINT "CK_EMBED_EXTERNAL_IDE_19F7CC6D" CHECK (("BINDING_VERSION" > 0)),
  CONSTRAINT "CK_EMBED_EXTERNAL_IDE_778D7CE6" CHECK ((("EXPIRES_AT" is null) or ("EXPIRES_AT" > "EFFECTIVE_AT")))
);

CREATE TABLE "EMBED_IDENTITY_PROVIDER" (
  "ID" varchar2(64) NOT NULL,
  "NAME" varchar2(128) NOT NULL,
  "TYPE" varchar2(32) NOT NULL,
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ACTIVE',
  "ISSUER" varchar2(500),
  "ISSUER_UNIQUENESS_KEY" varchar2(500) GENERATED ALWAYS AS (coalesce("ISSUER",'<trusted-external-id>')) VIRTUAL,
  "SUBJECT_NAMESPACE" varchar2(128) NOT NULL,
  "AUDIENCES_JSON" clob NOT NULL,
  "ALGORITHMS_JSON" clob NOT NULL,
  "JWKS_MODE" varchar2(32),
  "JWKS_JSON" clob,
  "JWKS_URL" varchar2(2048),
  "CLOCK_SKEW_SECONDS" number(10) NOT NULL DEFAULT '30',
  "MAX_ASSERTION_LIFETIME_SECONDS" number(10) NOT NULL DEFAULT '60',
  "KEY_VERSION" number(19) NOT NULL DEFAULT '1',
  "LOCK_VERSION" number(19) NOT NULL DEFAULT '1',
  "SECURITY_VERSION" number(19) NOT NULL DEFAULT '1',
  "CREATE_BY" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_BY" varchar2(64) NOT NULL,
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "REVOKED_BY" varchar2(64),
  "REVOKED_AT" timestamp(6),
  CONSTRAINT "PK_EMBED_IDENTITY_PRO_9BE1546A" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_C523722B" CHECK (("AUDIENCES_JSON" IS JSON and "ALGORITHMS_JSON" IS JSON and (("JWKS_JSON" is null) or "JWKS_JSON" IS JSON) and (length("AUDIENCES_JSON") <= 65536) and (length("ALGORITHMS_JSON") <= 65536) and (("JWKS_JSON" is null) or (length("JWKS_JSON") <= 262144)))),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_5BDCB654" CHECK ((("JWKS_MODE" is null) or ("JWKS_MODE" in ('STATIC_JWK_SET','REMOTE_JWKS')))),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_43EDB3A3" CHECK (((("TYPE" = 'SIGNED_JWT') and ("ISSUER" is not null) and ((("JWKS_MODE" = 'STATIC_JWK_SET') and ("JWKS_JSON" is not null) and ("JWKS_URL" is null)) or (("JWKS_MODE" = 'REMOTE_JWKS') and ("JWKS_JSON" is null) and ("JWKS_URL" like 'https://%')))) or (("TYPE" = 'TRUSTED_EXTERNAL_ID') and ("ISSUER" is null) and ("JWKS_MODE" is null) and ("JWKS_JSON" is null) and ("JWKS_URL" is null)))),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_531EB890" CHECK (((("STATUS" = 'REVOKED') and ("REVOKED_BY" is not null) and ("REVOKED_AT" is not null)) or (("STATUS" <> 'REVOKED') and ("REVOKED_BY" is null) and ("REVOKED_AT" is null)))),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_9620E074" CHECK (("STATUS" in ('ACTIVE','DISABLED','REVOKED'))),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_011859B0" CHECK ((("CLOCK_SKEW_SECONDS" between 0 and 300) and ("MAX_ASSERTION_LIFETIME_SECONDS" between 1 and 300))),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_5A0D72AA" CHECK (("TYPE" in ('SIGNED_JWT','TRUSTED_EXTERNAL_ID'))),
  CONSTRAINT "CK_EMBED_IDENTITY_PRO_77288BA5" CHECK ((("KEY_VERSION" > 0) and ("LOCK_VERSION" > 0) and ("SECURITY_VERSION" > 0)))
);

CREATE TABLE "EMBED_LAUNCH" (
  "ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "GRANT_ID" varchar2(64) NOT NULL,
  "VIEW_ID" varchar2(64) NOT NULL,
  "VIEW_RELEASE_ID" varchar2(64) NOT NULL,
  "IDENTITY_PROVIDER_ID" varchar2(64) NOT NULL,
  "PROVIDER_SECURITY_VERSION" number(19) NOT NULL,
  "APPLICATION_VERSION" number(19) NOT NULL,
  "GRANT_SECURITY_VERSION" number(19) NOT NULL,
  "VIEW_SECURITY_VERSION" number(19) NOT NULL,
  "FLOW_USER_ID" varchar2(64) NOT NULL,
  "IDENTITY_BINDING_ID" varchar2(64) NOT NULL,
  "BINDING_VERSION" number(19) NOT NULL,
  "SUBJECT_DIGEST" char(64) NOT NULL,
  "SUBJECT_DIGEST_KEY_VERSION" varchar2(64) NOT NULL,
  "PARENT_ORIGIN" varchar2(255) NOT NULL,
  "CHANNEL_ID" varchar2(128) NOT NULL,
  "ENTRY_MODE" varchar2(16) NOT NULL,
  "RECORD_ID" varchar2(64),
  "CONTEXT_CIPHERTEXT" clob NOT NULL,
  "CONTEXT_CIPHER_KEY_VERSION" varchar2(64) NOT NULL,
  "CONTEXT_DIGEST" char(64) NOT NULL,
  "CONTEXT_DIGEST_KEY_VERSION" varchar2(64) NOT NULL,
  "UI_LOCALE" varchar2(35) NOT NULL DEFAULT 'zh-CN',
  "UI_THEME" varchar2(16) NOT NULL DEFAULT 'light',
  "UI_FORM_PRESENTATION" varchar2(16) NOT NULL DEFAULT 'seamless',
  "LAUNCH_CODE_DIGEST" char(64) NOT NULL,
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ISSUED',
  "EXPIRES_AT" timestamp(6) NOT NULL,
  "CONSUMED_AT" timestamp(6),
  "CONSUMED_SESSION_ID" varchar2(64),
  "REVOKED_AT" timestamp(6),
  "TRACE_ID" varchar2(128),
  "REQUEST_ID" varchar2(128),
  "SOURCE_IP_DIGEST" char(64),
  "SOURCE_IP_DIGEST_KEY_VERSION" varchar2(64),
  "USER_AGENT_DIGEST" char(64),
  "USER_AGENT_DIGEST_KEY_VERSION" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_LAUNCH_EMBED_LAUNCH" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_084B2EA8" CHECK (("CONTEXT_CIPHERTEXT" IS JSON and (length("CONTEXT_CIPHERTEXT") <= 65536) and (char_length("CONTEXT_CIPHER_KEY_VERSION") > 0) and (char_length("CONTEXT_DIGEST_KEY_VERSION") > 0) and (char_length("SUBJECT_DIGEST_KEY_VERSION") > 0))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_DEF0F287" CHECK ((((("SOURCE_IP_DIGEST" is null) and ("SOURCE_IP_DIGEST_KEY_VERSION" is null)) or (("SOURCE_IP_DIGEST" is not null) and ("SOURCE_IP_DIGEST_KEY_VERSION" is not null))) and ((("USER_AGENT_DIGEST" is null) and ("USER_AGENT_DIGEST_KEY_VERSION" is null)) or (("USER_AGENT_DIGEST" is not null) and ("USER_AGENT_DIGEST_KEY_VERSION" is not null))))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_02AA23AA" CHECK ((regexp_like("SUBJECT_DIGEST",'^[0-9a-f]{64}$') and regexp_like("CONTEXT_DIGEST",'^[0-9a-f]{64}$') and regexp_like("LAUNCH_CODE_DIGEST",'^[0-9a-f]{64}$') and (("SOURCE_IP_DIGEST" is null) or regexp_like("SOURCE_IP_DIGEST",'^[0-9a-f]{64}$')) and (("USER_AGENT_DIGEST" is null) or regexp_like("USER_AGENT_DIGEST",'^[0-9a-f]{64}$')))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_2A9FC6A6" CHECK (((("ENTRY_MODE" in ('LIST','CREATE')) and ("RECORD_ID" is null)) or (("ENTRY_MODE" in ('VIEW','EDIT')) and ("RECORD_ID" is not null)))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_4FEE047B" CHECK (("EXPIRES_AT" > "CREATE_TIME")),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_674E066C" CHECK (("UI_FORM_PRESENTATION" in ('seamless','dialog'))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_D5F210D9" CHECK (((("STATUS" = 'ISSUED') and ("CONSUMED_AT" is null) and ("CONSUMED_SESSION_ID" is null) and ("REVOKED_AT" is null)) or (("STATUS" = 'CONSUMED') and ("CONSUMED_AT" is not null) and ("CONSUMED_SESSION_ID" is not null) and ("REVOKED_AT" is null)) or (("STATUS" = 'EXPIRED') and ("CONSUMED_AT" is null) and ("CONSUMED_SESSION_ID" is null) and ("REVOKED_AT" is null)) or (("STATUS" = 'REVOKED') and ("CONSUMED_AT" is null) and ("CONSUMED_SESSION_ID" is null) and ("REVOKED_AT" is not null)))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_27A1D71D" CHECK (regexp_like("PARENT_ORIGIN",'^https://[^/?#]+$')),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_2484A249" CHECK (("STATUS" in ('ISSUED','CONSUMED','EXPIRED','REVOKED'))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_A9787CBE" CHECK (("UI_THEME" in ('light','dark','system'))),
  CONSTRAINT "CK_EMBED_LAUNCH_CHK_E_AEF8EF09" CHECK ((("PROVIDER_SECURITY_VERSION" > 0) and ("APPLICATION_VERSION" >= 0) and ("GRANT_SECURITY_VERSION" > 0) and ("VIEW_SECURITY_VERSION" > 0) and ("BINDING_VERSION" > 0)))
);

CREATE TABLE "EMBED_OPERATION_RECEIPT" (
  "ID" varchar2(64) NOT NULL,
  "IDEMPOTENCY_RECORD_ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "OPERATION" varchar2(64) NOT NULL,
  "ACTOR_SCOPE_DIGEST" char(64) NOT NULL,
  "VIEW_KEY" varchar2(100) NOT NULL,
  "TARGET_TYPE" varchar2(64) NOT NULL,
  "TARGET_ID" varchar2(128) NOT NULL,
  "OUTCOME_CODE" varchar2(64) NOT NULL,
  "RECORD_VERSION" number(19),
  "RESULT_SUMMARY_JSON" clob NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_OPERATION_RE_FC544645" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_OPERATION_RE_021BB43E" CHECK (regexp_like("ACTOR_SCOPE_DIGEST",'^[0-9a-f]{64}$')),
  CONSTRAINT "CK_EMBED_OPERATION_RE_FEBDCCC9" CHECK (("OPERATION" in ('EMBED_RECORD_CREATE','EMBED_RECORD_UPDATE','EMBED_ACTION_EXECUTE'))),
  CONSTRAINT "CK_EMBED_OPERATION_RE_19AFDA32" CHECK ((("RECORD_VERSION" is null) or ("RECORD_VERSION" >= 0))),
  CONSTRAINT "CK_EMBED_OPERATION_RE_74953D72" CHECK (("RESULT_SUMMARY_JSON" IS JSON and (length("RESULT_SUMMARY_JSON") <= 8192)))
);

CREATE TABLE "EMBED_SESSION" (
  "ID" varchar2(64) NOT NULL,
  "SESSION_TOKEN_DIGEST" char(64) NOT NULL,
  "LAUNCH_ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "GRANT_ID" varchar2(64) NOT NULL,
  "VIEW_ID" varchar2(64) NOT NULL,
  "VIEW_RELEASE_ID" varchar2(64) NOT NULL,
  "IDENTITY_PROVIDER_ID" varchar2(64) NOT NULL,
  "PROVIDER_SECURITY_VERSION" number(19) NOT NULL,
  "FLOW_USER_ID" varchar2(64) NOT NULL,
  "IDENTITY_BINDING_ID" varchar2(64) NOT NULL,
  "BINDING_VERSION" number(19) NOT NULL,
  "PARENT_ORIGIN" varchar2(255) NOT NULL,
  "CHANNEL_ID" varchar2(128) NOT NULL,
  "ENTRY_MODE" varchar2(16) NOT NULL,
  "RECORD_ID" varchar2(64),
  "PARENT_NONCE_DIGEST" char(64) NOT NULL,
  "CHILD_NONCE_DIGEST" char(64) NOT NULL,
  "CONTEXT_CIPHERTEXT" clob NOT NULL,
  "CONTEXT_CIPHER_KEY_VERSION" varchar2(64) NOT NULL,
  "CONTEXT_DIGEST" char(64) NOT NULL,
  "CONTEXT_DIGEST_KEY_VERSION" varchar2(64) NOT NULL,
  "UI_LOCALE" varchar2(35) NOT NULL DEFAULT 'zh-CN',
  "UI_THEME" varchar2(16) NOT NULL DEFAULT 'light',
  "UI_FORM_PRESENTATION" varchar2(16) NOT NULL DEFAULT 'seamless',
  "CAPABILITY_SNAPSHOT_JSON" clob NOT NULL,
  "APPLICATION_VERSION" number(19) NOT NULL,
  "GRANT_SECURITY_VERSION" number(19) NOT NULL,
  "VIEW_SECURITY_VERSION" number(19) NOT NULL,
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ACTIVE',
  "SLOT_RELEASED" number(3) NOT NULL DEFAULT '0',
  "SLOT_RELEASED_AT" timestamp(6),
  "ISSUED_AT" timestamp(6) NOT NULL,
  "LAST_SEEN_AT" timestamp(6) NOT NULL,
  "IDLE_EXPIRES_AT" timestamp(6) NOT NULL,
  "ABSOLUTE_EXPIRES_AT" timestamp(6) NOT NULL,
  "REVOKED_AT" timestamp(6),
  "REVOKE_REASON" varchar2(128),
  "SOURCE_IP_DIGEST" char(64),
  "SOURCE_IP_DIGEST_KEY_VERSION" varchar2(64),
  "USER_AGENT_DIGEST" char(64),
  "USER_AGENT_DIGEST_KEY_VERSION" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_SESSION_EMBED_SESSION" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_SESSION_CHK__ACB07F51" CHECK (("CONTEXT_CIPHERTEXT" IS JSON and "CAPABILITY_SNAPSHOT_JSON" IS JSON and (length("CONTEXT_CIPHERTEXT") <= 65536) and (length("CAPABILITY_SNAPSHOT_JSON") <= 65536) and (char_length("CONTEXT_CIPHER_KEY_VERSION") > 0) and (char_length("CONTEXT_DIGEST_KEY_VERSION") > 0))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__6B5E76A9" CHECK ((((("SOURCE_IP_DIGEST" is null) and ("SOURCE_IP_DIGEST_KEY_VERSION" is null)) or (("SOURCE_IP_DIGEST" is not null) and ("SOURCE_IP_DIGEST_KEY_VERSION" is not null))) and ((("USER_AGENT_DIGEST" is null) and ("USER_AGENT_DIGEST_KEY_VERSION" is null)) or (("USER_AGENT_DIGEST" is not null) and ("USER_AGENT_DIGEST_KEY_VERSION" is not null))))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__0C8C959D" CHECK ((regexp_like("SESSION_TOKEN_DIGEST",'^[0-9a-f]{64}$') and regexp_like("PARENT_NONCE_DIGEST",'^[0-9a-f]{64}$') and regexp_like("CHILD_NONCE_DIGEST",'^[0-9a-f]{64}$') and regexp_like("CONTEXT_DIGEST",'^[0-9a-f]{64}$') and (("SOURCE_IP_DIGEST" is null) or regexp_like("SOURCE_IP_DIGEST",'^[0-9a-f]{64}$')) and (("USER_AGENT_DIGEST" is null) or regexp_like("USER_AGENT_DIGEST",'^[0-9a-f]{64}$')))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__2E8214C9" CHECK (((("ENTRY_MODE" in ('LIST','CREATE')) and ("RECORD_ID" is null)) or (("ENTRY_MODE" in ('VIEW','EDIT')) and ("RECORD_ID" is not null)))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__364A690E" CHECK (("UI_FORM_PRESENTATION" in ('seamless','dialog'))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__D34E9E8D" CHECK (regexp_like("PARENT_ORIGIN",'^https://[^/?#]+$')),
  CONSTRAINT "CK_EMBED_SESSION_CHK__948BAB67" CHECK (((("STATUS" = 'ACTIVE') and ("SLOT_RELEASED" = 0) and ("SLOT_RELEASED_AT" is null)) or (("STATUS" in ('LOGGED_OUT','EXPIRED')) and ("SLOT_RELEASED" = 1) and ("SLOT_RELEASED_AT" is not null) and ("REVOKED_AT" is null) and ("REVOKE_REASON" is null)) or (("STATUS" = 'REVOKED') and ("SLOT_RELEASED" = 1) and ("SLOT_RELEASED_AT" is not null) and ("REVOKED_AT" is not null) and ("REVOKE_REASON" is not null)))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__59CC938C" CHECK (("STATUS" in ('ACTIVE','LOGGED_OUT','EXPIRED','REVOKED'))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__220B6413" CHECK (("UI_THEME" in ('light','dark','system'))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__6E48F7AF" CHECK ((("ISSUED_AT" <= "LAST_SEEN_AT") and ("ISSUED_AT" < "IDLE_EXPIRES_AT") and ("IDLE_EXPIRES_AT" <= "ABSOLUTE_EXPIRES_AT"))),
  CONSTRAINT "CK_EMBED_SESSION_CHK__4B8EB921" CHECK ((("PROVIDER_SECURITY_VERSION" > 0) and ("APPLICATION_VERSION" >= 0) and ("GRANT_SECURITY_VERSION" > 0) and ("VIEW_SECURITY_VERSION" > 0) and ("BINDING_VERSION" > 0)))
);

CREATE TABLE "EMBED_SESSION_COUNTER" (
  "GRANT_ID" varchar2(64) NOT NULL,
  "FLOW_USER_ID" varchar2(64) NOT NULL,
  "ACTIVE_COUNT" number(10) NOT NULL DEFAULT '0',
  "LOCK_VERSION" number(19) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_SESSION_COUN_F95F74A8" PRIMARY KEY ("GRANT_ID", "FLOW_USER_ID"),
  CONSTRAINT "CK_EMBED_SESSION_COUN_A20AFC8C" CHECK (("ACTIVE_COUNT" >= 0)),
  CONSTRAINT "CK_EMBED_SESSION_COUN_E2906829" CHECK (("LOCK_VERSION" >= 0))
);

CREATE TABLE "EMBED_VIEW" (
  "ID" varchar2(64) NOT NULL,
  "VIEW_KEY" varchar2(100) NOT NULL,
  "NAME" varchar2(128) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "SURFACE_TYPE" varchar2(16) NOT NULL,
  "STATUS" varchar2(16) NOT NULL DEFAULT 'DRAFT',
  "DRAFT_CONFIG_JSON" clob NOT NULL,
  "DRAFT_REVISION" number(19) NOT NULL DEFAULT '1',
  "PUBLISHED_RELEASE_ID" varchar2(64),
  "LOCK_VERSION" number(19) NOT NULL DEFAULT '1',
  "SECURITY_VERSION" number(19) NOT NULL DEFAULT '1',
  "CREATE_BY" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_BY" varchar2(64) NOT NULL,
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_VIEW_EMBED_VIEW" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_VIEW_CHK_EMB_1EE48C8B" CHECK (("DRAFT_CONFIG_JSON" IS JSON and (length("DRAFT_CONFIG_JSON") <= 262144))),
  CONSTRAINT "CK_EMBED_VIEW_CHK_EMB_04CABB0F" CHECK (("STATUS" in ('DRAFT','ACTIVE','DISABLED','RETIRED'))),
  CONSTRAINT "CK_EMBED_VIEW_CHK_EMB_2C0B51E3" CHECK (("SURFACE_TYPE" in ('LIST','FORM'))),
  CONSTRAINT "CK_EMBED_VIEW_CHK_EMB_5977F27F" CHECK ((("DRAFT_REVISION" > 0) and ("LOCK_VERSION" > 0) and ("SECURITY_VERSION" > 0)))
);

CREATE TABLE "EMBED_VIEW_RELEASE" (
  "ID" varchar2(64) NOT NULL,
  "VIEW_ID" varchar2(64) NOT NULL,
  "REVISION" number(19) NOT NULL,
  "SURFACE_TYPE" varchar2(16) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "LIST_KEY" varchar2(100),
  "DEFAULT_FORM_ID" varchar2(64),
  "LIST_RELEASE_ID" varchar2(64),
  "LIST_RELEASE_VERSION" number(19),
  "FORM_RELEASE_ID" varchar2(64),
  "FORM_RELEASE_VERSION" number(19),
  "ENTRY_MODES_JSON" clob NOT NULL,
  "CAPABILITIES_JSON" clob NOT NULL,
  "FIELD_POLICY_JSON" clob NOT NULL,
  "ACTION_POLICY_JSON" clob NOT NULL,
  "CONTEXT_SCHEMA_JSON" clob NOT NULL,
  "CONTEXT_BINDINGS_JSON" clob NOT NULL,
  "UI_CONFIG_JSON" clob NOT NULL,
  "CONFIG_JSON" clob NOT NULL,
  "CONFIG_HASH" char(64) NOT NULL,
  "RELEASE_NOTE" varchar2(500),
  "PUBLISHED_BY" varchar2(64) NOT NULL,
  "PUBLISHED_AT" timestamp(6) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_EMBED_VIEW_RELEASE_CA72C7F4" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_EMBED_VIEW_RELEASE_E7E699F8" CHECK (((("FORM_RELEASE_ID" is null) and ("FORM_RELEASE_VERSION" is null)) or (("FORM_RELEASE_ID" is not null) and ("FORM_RELEASE_VERSION" > 0)))),
  CONSTRAINT "CK_EMBED_VIEW_RELEASE_8D4EB4C0" CHECK (regexp_like("CONFIG_HASH",'^[0-9a-f]{64}$')),
  CONSTRAINT "CK_EMBED_VIEW_RELEASE_9D8FBA7E" CHECK (("ENTRY_MODES_JSON" IS JSON and "CAPABILITIES_JSON" IS JSON and "FIELD_POLICY_JSON" IS JSON and "ACTION_POLICY_JSON" IS JSON and "CONTEXT_SCHEMA_JSON" IS JSON and "CONTEXT_BINDINGS_JSON" IS JSON and "UI_CONFIG_JSON" IS JSON and "CONFIG_JSON" IS JSON and (length("ENTRY_MODES_JSON") <= 65536) and (length("CAPABILITIES_JSON") <= 65536) and (length("FIELD_POLICY_JSON") <= 262144) and (length("ACTION_POLICY_JSON") <= 262144) and (length("CONTEXT_SCHEMA_JSON") <= 262144) and (length("CONTEXT_BINDINGS_JSON") <= 262144) and (length("UI_CONFIG_JSON") <= 262144) and (length("CONFIG_JSON") <= 262144))),
  CONSTRAINT "CK_EMBED_VIEW_RELEASE_E155F96C" CHECK (((("LIST_RELEASE_ID" is null) and ("LIST_RELEASE_VERSION" is null)) or (("LIST_RELEASE_ID" is not null) and ("LIST_RELEASE_VERSION" > 0)))),
  CONSTRAINT "CK_EMBED_VIEW_RELEASE_04E84378" CHECK (("REVISION" > 0)),
  CONSTRAINT "CK_EMBED_VIEW_RELEASE_C0F650F3" CHECK (("SURFACE_TYPE" in ('LIST','FORM'))),
  CONSTRAINT "CK_EMBED_VIEW_RELEASE_FD96CF75" CHECK (((("SURFACE_TYPE" = 'LIST') and ("LIST_KEY" is not null) and ("LIST_RELEASE_ID" is not null) and ("LIST_RELEASE_VERSION" is not null) and (("DEFAULT_FORM_ID" is null) or ("FORM_RELEASE_ID" is not null))) or (("SURFACE_TYPE" = 'FORM') and ("LIST_KEY" is null) and ("LIST_RELEASE_ID" is null) and ("LIST_RELEASE_VERSION" is null) and ("FORM_RELEASE_ID" is not null) and ("FORM_RELEASE_VERSION" is not null))))
);

CREATE TABLE "ENTITY_CODE_RULE" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "PREFIX" varchar2(20),
  "DATE_FORMAT" varchar2(20) DEFAULT 'yyyyMMdd',
  "SEQ_LENGTH" number(10) DEFAULT '6',
  "SEQ_TYPE" varchar2(20) DEFAULT 'DAY',
  "CURRENT_SEQ" number(10) DEFAULT '0',
  "SEQ_DATE" varchar2(20),
  "EXAMPLE" varchar2(100),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "GENERATION_MODE" varchar2(20) NOT NULL DEFAULT 'RULE',
  "GENERATOR_CODE" varchar2(64),
  "GENERATOR_CONFIG" clob,
  CONSTRAINT "PK_ENTITY_CODE_RULE_E_984CFB2E" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_DEFINITION" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "ENTITY_NAME" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "PROCESS_DEFINITION_ID" varchar2(64),
  "STATUS" varchar2(20) DEFAULT 'DRAFT',
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "TABLE_NAME" varchar2(100),
  "LIFECYCLE_MODE" varchar2(20) NOT NULL DEFAULT 'STANDALONE',
  "STORAGE_MODE" varchar2(20) NOT NULL DEFAULT 'DYNAMIC',
  "DELETED" number(3) DEFAULT '0',
  "UPDATED_BY" varchar2(64),
  "TEAM_VISIBILITY_ENABLED" number(3) NOT NULL DEFAULT '0',
  "TEAM_VISIBILITY_LEVEL" varchar2(30) NOT NULL DEFAULT 'ADDITIVE',
  "ACTIVE_PROCESS_DEFINITION_KEY" number(19) GENERATED ALWAYS AS ((case when ((coalesce("DELETED",0) = 0) and regexp_like(trim("PROCESS_DEFINITION_ID"),'^[0-9]+$') and (nullif(trim(leading '0' from trim("PROCESS_DEFINITION_ID")),'') is not null) and ((char_length(trim(leading '0' from trim("PROCESS_DEFINITION_ID"))) < 19) or ((char_length(trim(leading '0' from trim("PROCESS_DEFINITION_ID"))) = 19) and (trim(leading '0' from trim("PROCESS_DEFINITION_ID")) <= '9223372036854775807')))) then cast(trim(leading '0' from trim("PROCESS_DEFINITION_ID")) as number(19)) else NULL end)) VIRTUAL,
  CONSTRAINT "PK_ENTITY_DEFINITION__C27B7B72" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_FIELD" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "ENTITY_ID" number(19) NOT NULL,
  "FIELD_CODE" varchar2(100) NOT NULL,
  "FIELD_NAME" varchar2(200) NOT NULL,
  "FIELD_TYPE" varchar2(50) NOT NULL,
  "DB_TYPE" varchar2(50),
  "FIELD_LENGTH" number(10),
  "IS_REQUIRED" number(3) DEFAULT '0',
  "IS_UNIQUE" number(3) DEFAULT '0',
  "DEFAULT_VALUE" varchar2(500),
  "OPTIONS_JSON" clob,
  "VALIDATE_RULES" clob,
  "SORT_ORDER" number(10) DEFAULT '0',
  "IS_SYSTEM" number(3) DEFAULT '0',
  "IS_PUBLISHED" number(3) DEFAULT '0',
  "EDITABLE" number(3) DEFAULT '1',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "FIELD_PRECISION" number(10),
  "DB_COLUMN_NAME" varchar2(100),
  "FILE_TYPES" varchar2(500),
  "FILE_MAX_SIZE" number(10),
  "FILE_MAX_COUNT" number(10),
  "REF_ENTITY_TYPE" varchar2(20),
  "REF_ENTITY_ID" varchar2(64),
  "REF_FIELD_CODE" varchar2(100),
  "REF_LIST_KEY" varchar2(100),
  "FIELD_ID" varchar2(100),
  "DICT_TYPE" varchar2(100),
  "VALUE_STORAGE" varchar2(20) DEFAULT 'SCALAR',
  "DELETED" number(3) DEFAULT '0',
  CONSTRAINT "PK_ENTITY_FIELD_ENTITY_FIELD" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_FIELD_FILE_ITEM" (
  "ID" varchar2(64) NOT NULL,
  "FIELD_ID" varchar2(64) NOT NULL,
  "ITEM_KEY" varchar2(64) NOT NULL,
  "ITEM_NAME" varchar2(200) NOT NULL,
  "NAME_ALIASES" clob,
  "IS_REQUIRED" number(3) NOT NULL DEFAULT '0',
  "FILE_TYPES" varchar2(500),
  "MAX_SIZE" number(10),
  "MAX_COUNT" number(10),
  "SORT_ORDER" number(10) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_FIELD_FILE__84AB7E68" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_FIELD_OPTION" (
  "ID" varchar2(64) NOT NULL,
  "FIELD_ID" varchar2(64) NOT NULL,
  "OPTION_VALUE" varchar2(500) NOT NULL,
  "OPTION_LABEL" varchar2(500) NOT NULL,
  "STYLE_TYPE" varchar2(50),
  "DISABLED" number(3) NOT NULL DEFAULT '0',
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "OPTION_DOCUMENT" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_FIELD_OPTIO_67296A05" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_FORM" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_ID" varchar2(64) NOT NULL,
  "FORM_NAME" varchar2(100) NOT NULL,
  "FORM_KEY" varchar2(100) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "LAYOUT_TYPE" varchar2(20) DEFAULT 'vertical',
  "STATUS" number(3) DEFAULT '1',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) DEFAULT '0',
  "IS_DEFAULT" number(3) DEFAULT '0',
  "CUSTOM_COMPONENT" varchar2(100),
  "CREATED_BY" varchar2(64),
  "UPDATED_BY" varchar2(64),
  "VIEW_CONFIG" clob,
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "ACTIVE_RELEASE_ID" varchar2(64),
  "DRAFT_HASH" varchar2(64),
  "CUSTOM_COMPONENT_VERSION" number(10),
  "CUSTOM_COMPONENT_SNAPSHOT_VERSION" number(10),
  "DATA_SOURCE_BINDINGS_DOCUMENT" clob,
  CONSTRAINT "PK_ENTITY_FORM_ENTITY_FORM" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_FORM_NODE" (
  "ID" varchar2(64) NOT NULL,
  "FORM_ID" varchar2(64) NOT NULL,
  "PARENT_ID" varchar2(64),
  "NODE_KEY" varchar2(100) NOT NULL,
  "ACTIVE_NODE_KEY" varchar2(100) GENERATED ALWAYS AS ((case when ("DELETED" = 0) then "NODE_KEY" else NULL end)) VIRTUAL,
  "NODE_TYPE" varchar2(30) NOT NULL,
  "BINDING_TYPE" varchar2(30) NOT NULL DEFAULT 'NONE',
  "BINDING_REF" varchar2(200),
  "PROPS_DOCUMENT" clob,
  "RULES_DOCUMENT" clob,
  "DATA_SOURCE_BINDINGS_DOCUMENT" clob,
  "LEGACY_PROPS_DOCUMENT" clob,
  "ORDER_KEY" number(19) NOT NULL DEFAULT '1000000',
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  "COMPONENT_NAME" varchar2(100),
  "COMPONENT_VERSION" number(10),
  "SNAPSHOT_VERSION" number(10),
  CONSTRAINT "PK_ENTITY_FORM_NODE_E_F6547B54" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_FORM_UNIQUE_CLAIM" (
  "CONSTRAINT_KEY" varchar2(255) NOT NULL,
  "VALUE_HASH" char(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "FORM_ID" varchar2(64) NOT NULL,
  "RULE_ID" varchar2(100) NOT NULL,
  "FIELD_CODE" varchar2(100) NOT NULL,
  "NORMALIZED_VALUE" varchar2(1000) NOT NULL,
  "RECORD_ID" varchar2(64) NOT NULL,
  "RELEASE_ID" varchar2(64),
  "RELEASE_VERSION" number(10),
  "EFFECTIVE_RELEASE_ID" varchar2(64) NOT NULL,
  "EFFECTIVE_CONTENT_HASH" char(64),
  "HOTFIX_TARGET_ID" varchar2(64),
  "CREATED_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATED_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_FORM_UNIQUE_46DA43B2" PRIMARY KEY ("CONSTRAINT_KEY", "VALUE_HASH")
);

CREATE TABLE "ENTITY_FORM_UNIQUE_VALUE_GATE" (
  "SCOPE_KEY" varchar2(255) NOT NULL,
  "VALUE_HASH" char(64) NOT NULL,
  "CREATED_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATED_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_FORM_UNIQUE_E01E468D" PRIMARY KEY ("SCOPE_KEY", "VALUE_HASH")
);

CREATE TABLE "ENTITY_LIST_ACTION" (
  "ID" varchar2(64) NOT NULL,
  "LIST_CONFIG_ID" varchar2(64) NOT NULL,
  "POSITION" varchar2(20) NOT NULL,
  "BUTTON_KEY" varchar2(100) NOT NULL,
  "BUTTON_TYPE" varchar2(30) NOT NULL DEFAULT 'built-in',
  "BUTTON_LABEL" varchar2(200) NOT NULL,
  "ICON" varchar2(100),
  "STYLE_TYPE" varchar2(30),
  "LINK_MODE" number(3) NOT NULL DEFAULT '0',
  "CUSTOM_MODE" varchar2(30),
  "HANDLER_CODE" varchar2(200),
  "PERMISSION_CODE" varchar2(200),
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "UNAVAILABLE_BEHAVIOR" varchar2(20),
  "ACTION_PARAMS_DOCUMENT" clob,
  "AVAILABILITY_RULE_DOCUMENT" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "ORDER_KEY" number(19) NOT NULL DEFAULT '1000000',
  "TEMPLATE_ID" varchar2(64),
  "TEMPLATE_VERSION" number(10),
  "LOCAL_OVERRIDES_DOCUMENT" clob,
  CONSTRAINT "PK_ENTITY_LIST_ACTION_476EF710" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_CONFIG" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "LIST_KEY" varchar2(100) NOT NULL,
  "LIST_NAME" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "IS_DEFAULT" number(3) DEFAULT '0',
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "CUSTOM_COMPONENT" varchar2(100),
  "TOOLBAR_CONFIG" clob,
  "ROW_ACTION_CONFIG" clob,
  "VIEW_CONFIG" clob,
  "DATA_SCOPE_MODE" varchar2(20) NOT NULL DEFAULT 'INHERIT',
  "UNBOUND_SCOPE_POLICY" varchar2(30) NOT NULL DEFAULT 'DENY_ALL',
  "SCOPE_ENFORCEMENT_MODE" varchar2(20) NOT NULL DEFAULT 'ENFORCE',
  "SCOPE_DEFAULT_CONFIRMED" number(3) NOT NULL DEFAULT '0',
  "SCOPE_DEFAULT_CONFIRMED_BY" varchar2(64),
  "SCOPE_DEFAULT_CONFIRMED_AT" timestamp,
  "SCOPE_DEFAULT_CONFIRMATION_NOTE" varchar2(500),
  "ACCESS_PERMISSION_CODE" varchar2(200),
  "ALLOWED_SCENES" clob,
  "SELECTION_CONFIG" clob,
  "FIXED_FILTER_CONFIG" clob,
  "QUERY_PROVIDER_CODE" varchar2(100),
  "PUBLISHED_VERSION" number(10) NOT NULL DEFAULT '0',
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "ACTIVE_RELEASE_ID" varchar2(64),
  "DRAFT_HASH" varchar2(64),
  "QUERY_INTERFACE_EXTENSION_ID" varchar2(64),
  CONSTRAINT "PK_ENTITY_LIST_CONFIG_1BD667D6" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_FIELD" (
  "ID" varchar2(64) NOT NULL,
  "LIST_CONFIG_ID" varchar2(64) NOT NULL,
  "FIELD_ID" varchar2(64) NOT NULL,
  "FIELD_CODE" varchar2(100) NOT NULL,
  "FIELD_NAME" varchar2(200) NOT NULL,
  "SORT_ORDER" number(10) DEFAULT '0',
  "WIDTH" number(10) DEFAULT '0',
  "SHOW_IN_LIST" number(3) DEFAULT '1',
  "IS_QUERY" number(3) DEFAULT '1',
  "QUERY_TYPE" varchar2(50) DEFAULT 'LIKE',
  "ALIGN" varchar2(20) DEFAULT 'left',
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DATA_SOURCE_TYPE" varchar2(32) DEFAULT 'ENTITY_FIELD',
  "DATA_SOURCE_CONFIG" clob,
  "RENDER_COMPONENT" varchar2(64),
  "FORMATTER" varchar2(255),
  "COLUMN_CONFIG" clob,
  "QUERY_CONFIG" clob,
  "RENDER_CONFIG" clob,
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "ORDER_KEY" number(19) NOT NULL DEFAULT '1000000',
  "INTERFACE_EXTENSION_ID" varchar2(64),
  "TEMPLATE_ID" varchar2(64),
  "TEMPLATE_VERSION" number(10),
  "LOCAL_OVERRIDES_DOCUMENT" clob,
  CONSTRAINT "PK_ENTITY_LIST_FIELD__A5D55EE6" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_SCENE" (
  "ID" varchar2(64) NOT NULL,
  "LIST_CONFIG_ID" varchar2(64) NOT NULL,
  "SCENE_CODE" varchar2(30) NOT NULL,
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "REVISION" number(10) NOT NULL DEFAULT '1',
  CONSTRAINT "PK_ENTITY_LIST_SCENE__DF5E922B" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_SCOPE_AUDIT_LOG" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "LIST_KEY" varchar2(100),
  "USER_ID" varchar2(64),
  "OPERATION" varchar2(50) NOT NULL,
  "RESULT" varchar2(20) NOT NULL,
  "DETAIL_JSON" clob,
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_LIST_SCOPE__2643CDEC" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_SCOPE_BINDING" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "POLICY_ID" varchar2(64) NOT NULL,
  "LIST_KEY" varchar2(100),
  "MATCH_CONFIG" clob NOT NULL,
  "RULE_EFFECT" varchar2(20) NOT NULL DEFAULT 'ALLOW',
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "EFFECTIVE_START_TIME" timestamp,
  "EFFECTIVE_END_TIME" timestamp,
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_ENTITY_LIST_SCOPE__9D1FC970" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_SCOPE_DELEGATION" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100),
  "FROM_USER_ID" varchar2(64) NOT NULL,
  "TO_USER_ID" varchar2(64) NOT NULL,
  "DELEGATE_SCOPE" varchar2(50) NOT NULL DEFAULT 'PERSONAL',
  "POLICY_ID" varchar2(64),
  "DELEGATE_CONFIG" clob,
  "START_TIME" timestamp,
  "END_TIME" timestamp,
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_ENTITY_LIST_SCOPE__81E72202" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_SCOPE_POLICY" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "POLICY_KEY" varchar2(100) NOT NULL,
  "POLICY_NAME" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "PRESET_CODE" varchar2(50),
  "FILTER_CONFIG" clob NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'DRAFT',
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "VERSION" number(10) NOT NULL DEFAULT '1',
  "REVIEW_REQUIRED" number(3) NOT NULL DEFAULT '0',
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_ENTITY_LIST_SCOPE__50DF7ACB" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_LIST_SCOPE_RELEASE" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "VERSION" number(10) NOT NULL,
  "SNAPSHOT_JSON" clob NOT NULL,
  "CONTENT_HASH" varchar2(64) NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'ACTIVE',
  "DESCRIPTION" varchar2(500),
  "PUBLISHED_BY" varchar2(64),
  "PUBLISHED_AT" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_LIST_SCOPE__095A0789" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_MUTATION_RECEIPT" (
  "ID" varchar2(64) NOT NULL,
  "IDEMPOTENCY_KEY" varchar2(200) NOT NULL,
  "COMMAND_HASH" varchar2(64) NOT NULL,
  "OPERATION_ID" varchar2(200) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "RECORD_ID" varchar2(64),
  "OPERATION_TYPE" varchar2(30) NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "RESULT_DOCUMENT" clob,
  "VERSION_NO" number(10),
  "VERSION_SCENARIO_CODE" varchar2(100),
  "CHANGED" number(3) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_MUTATION_RE_896BD8F5" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_PROCESS_LINK" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(63) NOT NULL,
  "ENTITY_RECORD_ID" varchar2(64) NOT NULL,
  "GENERATION" number(10) NOT NULL DEFAULT '1',
  "PROCESS_DEFINITION_KEY" varchar2(255) NOT NULL,
  "PROCESS_INSTANCE_ID" varchar2(64),
  "STATE" varchar2(20) NOT NULL,
  "REQUEST_ID" varchar2(64) NOT NULL,
  "ENTITY_STATUS" varchar2(50),
  "ENDED_AT" timestamp(6),
  "VERSION" number(19) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "END_TYPE" varchar2(20),
  CONSTRAINT "PK_ENTITY_PROCESS_LIN_8468E332" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_PUBLISH_HISTORY" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "ENTITY_NAME" varchar2(200) NOT NULL,
  "VERSION" number(10) NOT NULL,
  "VERSION_DESCRIPTION" varchar2(500),
  "FIELDS_SNAPSHOT" clob,
  "RELATIONS_SNAPSHOT" clob,
  "TABLE_DDL" clob,
  "PUBLISH_TYPE" varchar2(20) DEFAULT 'CREATE',
  "CHANGES_DESCRIPTION" varchar2(500),
  "PUBLISHED_AT" timestamp DEFAULT CURRENT_TIMESTAMP,
  "PUBLISHED_BY" varchar2(64),
  "PUBLISHED_BY_NAME" varchar2(100),
  "STATUS" varchar2(20) DEFAULT 'ACTIVE',
  "PROCESS_DEFINITION_ID" varchar2(64),
  "LIFECYCLE_MODE" varchar2(20) NOT NULL DEFAULT 'STANDALONE',
  "TEAM_VISIBILITY_ENABLED" number(3) NOT NULL DEFAULT '0',
  "TEAM_VISIBILITY_LEVEL" varchar2(30) NOT NULL DEFAULT 'ADDITIVE',
  CONSTRAINT "PK_ENTITY_PUBLISH_HIS_9769F0EF" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_RECORD_VERSION" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "RECORD_ID" varchar2(64) NOT NULL,
  "VERSION_NO" number(10) NOT NULL,
  "VERSION_TITLE" varchar2(300),
  "SCENARIO_CODE" varchar2(100) NOT NULL,
  "SCENARIO_NAME" varchar2(200) NOT NULL,
  "OPERATION_TYPE" varchar2(30) NOT NULL,
  "SOURCE_TYPE" varchar2(30) NOT NULL,
  "SOURCE_ID" varchar2(200),
  "BUSINESS_INTENT_CODE" varchar2(100) NOT NULL,
  "BUSINESS_INTENT_NAME" varchar2(200) NOT NULL,
  "SOURCE_ENTITY_CODE" varchar2(100),
  "SOURCE_RECORD_ID" varchar2(64),
  "PROCESS_DEFINITION_ID" varchar2(100),
  "PROCESS_INSTANCE_ID" varchar2(64),
  "TASK_ID" varchar2(64),
  "OPERATOR_ID" varchar2(64),
  "OPERATOR_NAME" varchar2(100),
  "BUSINESS_TRACE_KEY" varchar2(160),
  "IDEMPOTENCY_KEY" varchar2(200) NOT NULL,
  "ENTITY_RELEASE_ID" varchar2(64),
  "ENTITY_RELEASE_VERSION" number(10),
  "SCHEMA_VERSION" number(10) NOT NULL DEFAULT '1',
  "CONFIG_RELEASE_ID" varchar2(64),
  "CONFIG_RELEASE_VERSION" number(10),
  "DATA_HASH" varchar2(64),
  "PRESENTATION_HASH" varchar2(64),
  "SCOPE_HASH" varchar2(64),
  "REQUEST_HASH" varchar2(64),
  "DATASET_COUNT" number(10) NOT NULL DEFAULT '0',
  "SNAPSHOT_ROW_COUNT" number(10) NOT NULL DEFAULT '1',
  "SNAPSHOT_SIZE_BYTES" number(19) NOT NULL DEFAULT '0',
  "COMPLETENESS" varchar2(20) NOT NULL DEFAULT 'COMPLETE',
  "SNAPSHOT_HASH" varchar2(64) NOT NULL,
  "SNAPSHOT_DOCUMENT" clob NOT NULL,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_RECORD_VERS_95B090E7" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_RECORD_VERSION_COUNTER" (
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "RECORD_ID" varchar2(64) NOT NULL,
  "LAST_VERSION_NO" number(10) NOT NULL DEFAULT '0',
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_RECORD_VERS_453E1434" PRIMARY KEY ("ENTITY_CODE", "RECORD_ID")
);

CREATE TABLE "ENTITY_RECORD_VERSION_DATASET" (
  "ID" varchar2(64) NOT NULL,
  "VERSION_ID" varchar2(64) NOT NULL,
  "NODE_CODE" varchar2(100) NOT NULL,
  "NODE_KIND" varchar2(20) NOT NULL DEFAULT 'RELATION',
  "RELATION_CODE" varchar2(100) NOT NULL,
  "RELATION_NAME" varchar2(200) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "ENTITY_NAME" varchar2(200) NOT NULL,
  "ENTITY_RELEASE_ID" varchar2(64),
  "ENTITY_RELEASE_VERSION" number(10),
  "SELECTOR_DOCUMENT" clob NOT NULL,
  "PRESENTATION_DOCUMENT" clob NOT NULL,
  "DATA_HASH" varchar2(64) NOT NULL,
  "PRESENTATION_HASH" varchar2(64) NOT NULL,
  "SCOPE_HASH" varchar2(64) NOT NULL,
  "ROW_COUNT" number(10) NOT NULL DEFAULT '0',
  "COMPLETE" number(3) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_RECORD_VERS_FBFC372A" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_RECORD_VERSION_DATASET_ROW" (
  "ID" varchar2(64) NOT NULL,
  "DATASET_ID" varchar2(64) NOT NULL,
  "RECORD_ID" varchar2(64) NOT NULL,
  "RECORD_TITLE" varchar2(500),
  "ROW_ORDER" number(10) NOT NULL DEFAULT '0',
  "ROW_HASH" varchar2(64) NOT NULL,
  "VALUES_DOCUMENT" clob NOT NULL,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_RECORD_VERS_C6E5C632" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_RELATION" (
  "ID" varchar2(64) NOT NULL,
  "PARENT_ENTITY_ID" varchar2(64) NOT NULL,
  "PARENT_ENTITY_CODE" varchar2(100) NOT NULL,
  "PARENT_FIELD_ID" varchar2(64),
  "PARENT_FIELD_CODE" varchar2(100),
  "RELATION_CODE" varchar2(100) NOT NULL,
  "RELATION_NAME" varchar2(200),
  "DATA_KEY" varchar2(100),
  "CHILD_ENTITY_ID" varchar2(64) NOT NULL,
  "CHILD_ENTITY_CODE" varchar2(100) NOT NULL,
  "CHILD_REF_FIELD_CODE" varchar2(100) NOT NULL,
  "RELATION_TYPE" varchar2(20) NOT NULL DEFAULT 'ONE_TO_MANY',
  "OWNERSHIP_TYPE" varchar2(20) NOT NULL DEFAULT 'COMPOSITION',
  "CASCADE_DELETE" number(3) DEFAULT '1',
  "REQUIRED" number(3) DEFAULT '0',
  "SORT_ORDER" number(10) DEFAULT '0',
  "ENABLED" number(3) DEFAULT '1',
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_RELATION_EN_FAD04F45" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_SCHEMA_OPERATION" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "STATUS" varchar2(32) NOT NULL,
  "PLAN_HASH" char(64) NOT NULL,
  "IDEMPOTENCY_KEY" varchar2(160) NOT NULL,
  "PLAN_JSON" clob NOT NULL,
  "TARGET_FINGERPRINT" char(64) NOT NULL,
  "ACTUAL_FINGERPRINT" char(64),
  "DRIFT_JSON" clob,
  "UNIQUE_CONFLICT_JSON" clob,
  "RISK_LEVEL" varchar2(16) NOT NULL DEFAULT 'LOW',
  "RISK_REASON" varchar2(1000),
  "ESTIMATED_ROWS" number(19) NOT NULL DEFAULT '0',
  "LOCK_RISK" varchar2(16) NOT NULL DEFAULT 'LOW',
  "RELEASE_WINDOW" varchar2(255),
  "ATTEMPT_COUNT" number(10) NOT NULL DEFAULT '0',
  "ERROR_MESSAGE" clob,
  "STARTED_AT" timestamp,
  "FINISHED_AT" timestamp,
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "OPERATION_SOURCE" varchar2(32) NOT NULL DEFAULT 'ENTITY_PUBLISH',
  "SOURCE_REFERENCE_ID" varchar2(64),
  CONSTRAINT "PK_ENTITY_SCHEMA_OPER_FF132E63" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_SCHEMA_OPERATION_EVENT" (
  "ID" varchar2(64) NOT NULL,
  "OPERATION_ID" varchar2(64) NOT NULL,
  "FROM_STATUS" varchar2(32),
  "TO_STATUS" varchar2(32) NOT NULL,
  "MESSAGE" varchar2(1000),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_SCHEMA_OPER_C8DFE08D" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_STATUS" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "STATUS_CODE" varchar2(50) NOT NULL,
  "STATUS_NAME" varchar2(100) NOT NULL,
  "STATUS_CATEGORY" varchar2(50),
  "SORT_ORDER" number(10) DEFAULT '0',
  "DESCRIPTION" varchar2(500),
  "COLOR" varchar2(20),
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_STATUS_ENTITY_STATUS" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_UNIQUE_VALUE" (
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "FIELD_CODE" varchar2(100) NOT NULL,
  "VALUE_HASH" char(64) NOT NULL,
  "NORMALIZED_VALUE" varchar2(1000) NOT NULL,
  "RECORD_ID" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_UNIQUE_VALU_32204427" PRIMARY KEY ("ENTITY_CODE", "FIELD_CODE", "VALUE_HASH")
);

CREATE TABLE "ENTITY_VERSION_CONFIG" (
  "ID" varchar2(64) NOT NULL,
  "ENTITY_ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "ENABLED" number(3) NOT NULL DEFAULT '0',
  "CONTRACT_VERSION" number(10) NOT NULL DEFAULT '1',
  "DRAFT_DOCUMENT" clob,
  "CONFIG_DOCUMENT" clob,
  "MIGRATION_STATE" varchar2(30) NOT NULL DEFAULT 'REVIEW_REQUIRED',
  "ACTIVE_RELEASE_ID" varchar2(64),
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "STATUS" varchar2(20) NOT NULL DEFAULT 'DRAFT',
  "CREATE_BY" varchar2(64),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_BY" varchar2(64),
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_ENTITY_VERSION_CON_5C50BE2C" PRIMARY KEY ("ID")
);

CREATE TABLE "ENTITY_VERSION_CONFIG_RELEASE" (
  "ID" varchar2(64) NOT NULL,
  "CONFIG_ID" varchar2(64) NOT NULL,
  "VERSION" number(10) NOT NULL,
  "CONTRACT_VERSION" number(10) NOT NULL DEFAULT '1',
  "CONFIG_DOCUMENT" clob NOT NULL,
  "SCOPE_HASH" varchar2(64),
  "PUBLISHED_BY" varchar2(64),
  "PUBLISHED_BY_NAME" varchar2(100),
  "PUBLISH_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_ENTITY_VERSION_CON_29F46897" PRIMARY KEY ("ID")
);

CREATE TABLE "FLW_CHANNEL_DEFINITION" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "VERSION_" number(10),
  "KEY_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "CREATE_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  "RESOURCE_NAME_" varchar2(255),
  "DESCRIPTION_" varchar2(255),
  "TYPE_" varchar2(255),
  "IMPLEMENTATION_" varchar2(255),
  CONSTRAINT "PK_FLW_CHANNEL_DEFINI_EEF901DD" PRIMARY KEY ("ID_")
);

CREATE TABLE "FLW_EVENT_DEFINITION" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "VERSION_" number(10),
  "KEY_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "TENANT_ID_" varchar2(255),
  "RESOURCE_NAME_" varchar2(255),
  "DESCRIPTION_" varchar2(255),
  CONSTRAINT "PK_FLW_EVENT_DEFINITI_12FA98BB" PRIMARY KEY ("ID_")
);

CREATE TABLE "FLW_EVENT_DEPLOYMENT" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "CATEGORY_" varchar2(255),
  "DEPLOY_TIME_" timestamp(3),
  "TENANT_ID_" varchar2(255),
  "PARENT_DEPLOYMENT_ID_" varchar2(255),
  CONSTRAINT "PK_FLW_EVENT_DEPLOYME_6D1F32B5" PRIMARY KEY ("ID_")
);

CREATE TABLE "FLW_EVENT_RESOURCE" (
  "ID_" varchar2(255) NOT NULL,
  "NAME_" varchar2(255),
  "DEPLOYMENT_ID_" varchar2(255),
  "RESOURCE_BYTES_" blob,
  CONSTRAINT "PK_FLW_EVENT_RESOURCE_C32F43D8" PRIMARY KEY ("ID_")
);

CREATE TABLE "FLW_RU_BATCH" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "TYPE_" varchar2(64) NOT NULL,
  "SEARCH_KEY_" varchar2(255),
  "SEARCH_KEY2_" varchar2(255),
  "CREATE_TIME_" timestamp(3) NOT NULL,
  "COMPLETE_TIME_" timestamp(3),
  "STATUS_" varchar2(255),
  "BATCH_DOC_ID_" varchar2(64),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_FLW_RU_BATCH_FLW_RU_BATCH" PRIMARY KEY ("ID_")
);

CREATE TABLE "FLW_RU_BATCH_PART" (
  "ID_" varchar2(64) NOT NULL,
  "REV_" number(10),
  "BATCH_ID_" varchar2(64),
  "TYPE_" varchar2(64) NOT NULL,
  "SCOPE_ID_" varchar2(64),
  "SUB_SCOPE_ID_" varchar2(64),
  "SCOPE_TYPE_" varchar2(64),
  "SEARCH_KEY_" varchar2(255),
  "SEARCH_KEY2_" varchar2(255),
  "CREATE_TIME_" timestamp(3) NOT NULL,
  "COMPLETE_TIME_" timestamp(3),
  "STATUS_" varchar2(255),
  "RESULT_DOC_ID_" varchar2(64),
  "TENANT_ID_" varchar2(255),
  CONSTRAINT "PK_FLW_RU_BATCH_PART__A4896B0F" PRIMARY KEY ("ID_")
);

CREATE TABLE "INTEGRATION_API_REQUEST_LEASE" (
  "LEASE_ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "SCOPE_KEY" varchar2(128) NOT NULL,
  "EXPIRES_AT" timestamp(6) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_INTEGRATION_API_RE_6FB1F708" PRIMARY KEY ("LEASE_ID")
);

CREATE TABLE "INTEGRATION_APPLICATION" (
  "ID" varchar2(64) NOT NULL,
  "CLIENT_ID" varchar2(128) NOT NULL,
  "APPLICATION_NAME" varchar2(128) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "OWNER_ORGANIZATION_ID" varchar2(64),
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ACTIVE',
  "RATE_LIMIT_PER_MINUTE" number(10) NOT NULL DEFAULT '60',
  "MAX_CONCURRENCY" number(10) NOT NULL DEFAULT '10',
  "ALLOWED_SOURCE_CIDRS" clob,
  "EXPIRES_AT" timestamp(6),
  "VERSION" number(19) NOT NULL DEFAULT '0',
  "CREATED_BY" varchar2(64) NOT NULL,
  "UPDATED_BY" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_INTEGRATION_APPLIC_ECD95AF6" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_INTEGRATION_APPLIC_2ABFED08" CHECK ((("ALLOWED_SOURCE_CIDRS" is null) or "ALLOWED_SOURCE_CIDRS" IS JSON)),
  CONSTRAINT "CK_INTEGRATION_APPLIC_D9FD7748" CHECK (("MAX_CONCURRENCY" between 1 and 1000)),
  CONSTRAINT "CK_INTEGRATION_APPLIC_43473CC7" CHECK (("RATE_LIMIT_PER_MINUTE" between 1 and 10000)),
  CONSTRAINT "CK_INTEGRATION_APPLIC_449F7A68" CHECK (("STATUS" in ('ACTIVE','DISABLED','REVOKED')))
);

CREATE TABLE "INTEGRATION_APPLICATION_CREDENTIAL" (
  "ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "SECRET_HASH" varchar2(255) NOT NULL,
  "CREDENTIAL_HINT" varchar2(12) NOT NULL,
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ACTIVE',
  "CREDENTIAL_VERSION" number(19) NOT NULL,
  "EXPIRES_AT" timestamp(6),
  "LAST_USED_AT" timestamp(6),
  "CREATED_BY" varchar2(64) NOT NULL,
  "REVOKED_BY" varchar2(64),
  "REVOKED_AT" timestamp(6),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "ACTIVE_APPLICATION_ID" varchar2(64) GENERATED ALWAYS AS ((case when ("STATUS" = 'ACTIVE') then "APPLICATION_ID" else NULL end)) VIRTUAL,
  CONSTRAINT "PK_INTEGRATION_APPLIC_892ABB56" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_INTEGRATION_APPLIC_449488C2" CHECK (((("STATUS" = 'ACTIVE') and ("REVOKED_AT" is null) and ("REVOKED_BY" is null)) or (("STATUS" = 'REVOKED') and ("REVOKED_AT" is not null) and ("REVOKED_BY" is not null)))),
  CONSTRAINT "CK_INTEGRATION_APPLIC_17F6C034" CHECK (("STATUS" in ('ACTIVE','REVOKED')))
);

CREATE TABLE "INTEGRATION_IDEMPOTENCY_RECORD" (
  "ID" varchar2(64) NOT NULL,
  "APPLICATION_ID" varchar2(64) NOT NULL,
  "OPERATION" varchar2(64) NOT NULL,
  "IDEMPOTENCY_KEY" varchar2(128) NOT NULL,
  "REQUEST_HASH" char(64) NOT NULL,
  "STATUS" varchar2(24) NOT NULL,
  "RESOURCE_TYPE" varchar2(64),
  "RESOURCE_ID" varchar2(128),
  "RESPONSE_STATUS" number(5),
  "RESPONSE_BODY" clob,
  "FENCING_TOKEN" number(19) NOT NULL DEFAULT '1',
  "PROCESSING_STARTED_AT" timestamp(6) NOT NULL,
  "EXPIRES_AT" timestamp(6) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_INTEGRATION_IDEMPO_F867D3EE" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_INTEGRATION_IDEMPO_FD11A66D" CHECK (("FENCING_TOKEN" > 0)),
  CONSTRAINT "CK_INTEGRATION_IDEMPO_00237E38" CHECK (regexp_like("REQUEST_HASH",'^[0-9a-f]{64}$')),
  CONSTRAINT "CK_INTEGRATION_IDEMPO_7D0592C9" CHECK (((("STATUS" = 'SUCCEEDED') and ("RESOURCE_TYPE" is not null) and ("RESOURCE_ID" is not null) and ("RESPONSE_STATUS" between 200 and 299) and ("RESPONSE_BODY" is not null) and "RESPONSE_BODY" IS JSON) or (("STATUS" <> 'SUCCEEDED') and ("RESPONSE_STATUS" is null) and ("RESPONSE_BODY" is null)))),
  CONSTRAINT "CK_INTEGRATION_IDEMPO_5316B8F6" CHECK (("STATUS" in ('PROCESSING','SUCCEEDED','FAILED_RETRYABLE')))
);

CREATE TABLE "INTEGRATION_RATE_LIMIT_BUCKET" (
  "BUCKET_KEY" char(64) NOT NULL,
  "WINDOW_EPOCH" number(19) NOT NULL,
  "REQUEST_COUNT" number(10) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_INTEGRATION_RATE_L_CE968096" PRIMARY KEY ("BUCKET_KEY", "WINDOW_EPOCH"),
  CONSTRAINT "CK_INTEGRATION_RATE_L_EE677C67" CHECK (("REQUEST_COUNT" >= 0))
);

CREATE TABLE "PROCESS_ACTION" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_CONFIG_ID" varchar2(64) NOT NULL,
  "SCOPE_TYPE" varchar2(20) DEFAULT 'SEQUENCE_FLOW',
  "ELEMENT_ID" varchar2(100),
  "TRIGGER_TIMING" varchar2(50) DEFAULT 'TRANSITION_TAKEN',
  "EXECUTION_MODE" varchar2(30) DEFAULT 'IN_TRANSACTION',
  "FAILURE_POLICY" varchar2(20) DEFAULT 'ROLLBACK',
  "RETRY_CONFIG" clob,
  "ACTION_DEFINITION_ID" varchar2(64),
  "ACTION_NAME" varchar2(100) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "INTERFACE_NAME" varchar2(200) NOT NULL,
  "PARAMS_JSON" clob,
  "SORT_ORDER" number(10) DEFAULT '0',
  "ENABLED" number(3) DEFAULT '1',
  "STATUS" varchar2(20) DEFAULT 'DRAFT',
  "VERSION_ID" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "CREATED_BY" varchar2(64),
  "DELETED" number(10) DEFAULT '0',
  "FAILURE_STRATEGY_CODE" varchar2(64),
  "FAILURE_STRATEGY_VERSION" varchar2(32),
  "FAILURE_STRATEGY_CONFIG" clob,
  CONSTRAINT "PK_PROCESS_ACTION_PRO_15F6A2ED" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_ACTION_DEFINITION" (
  "ID" varchar2(64) NOT NULL,
  "ACTION_CODE" varchar2(200) NOT NULL,
  "DISPLAY_NAME" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(1000),
  "HANDLER_NAME" varchar2(200) NOT NULL,
  "VISIBILITY_SCOPE" varchar2(20) NOT NULL DEFAULT 'ENTITY',
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_PROCESS_ACTION_DEF_7BC8845F" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_ACTION_DEFINITION_ENTITY" (
  "ID" varchar2(64) NOT NULL,
  "ACTION_DEFINITION_ID" varchar2(64) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_ACTION_DEF_DB20829A" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_ACTION_EXECUTION" (
  "ID" varchar2(64) NOT NULL,
  "ACTION_ID" varchar2(64) NOT NULL,
  "ACTION_NAME" varchar2(200),
  "HANDLER_NAME" varchar2(200),
  "HANDLER_DISPLAY_NAME" varchar2(200),
  "VERSION_ID" varchar2(64),
  "PROCESS_INSTANCE_ID" varchar2(64) NOT NULL,
  "PROCESS_DEFINITION_ID" varchar2(128),
  "EXECUTION_ID" varchar2(64),
  "TASK_ID" varchar2(64),
  "ENTITY_CODE" varchar2(100),
  "SCOPE_TYPE" varchar2(20) NOT NULL,
  "ELEMENT_ID" varchar2(100),
  "TRIGGER_TIMING" varchar2(50) NOT NULL,
  "IDEMPOTENCY_KEY" varchar2(128) NOT NULL,
  "PAYLOAD_JSON" clob,
  "RESOLVED_PARAMS_JSON" clob,
  "RESULT_JSON" clob,
  "EXECUTION_TRACE_JSON" clob,
  "STATUS" varchar2(20) NOT NULL,
  "OWNER_ID" varchar2(128),
  "LEASE_TOKEN" number(19) NOT NULL DEFAULT '0',
  "LEASE_UNTIL" timestamp(6),
  "RETRY_COUNT" number(10) DEFAULT '0',
  "MAX_RETRIES" number(10) DEFAULT '5',
  "NEXT_RETRY_TIME" timestamp,
  "ERROR_MESSAGE" clob,
  "ERROR_STACK" clob,
  "STARTED_AT" timestamp,
  "FINISHED_AT" timestamp,
  "DURATION_MS" number(19),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "FAILURE_STRATEGY_SNAPSHOT" clob,
  "ATTEMPT_NO" number(10) NOT NULL DEFAULT '0',
  "ATTEMPT_LEASE_TOKEN" number(19),
  "TERMINATION_REASON" varchar2(64),
  "RESOLUTION_STATUS" varchar2(20),
  "REPLAY_ROOT_ID" varchar2(64),
  "REPLAY_OF_ID" varchar2(64),
  "HANDLER_IDEMPOTENCY_KEY" varchar2(128),
  CONSTRAINT "PK_PROCESS_ACTION_EXE_2991996B" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_ASSIGNEE_INCIDENT" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_CONFIG_ID" varchar2(64),
  "PROCESS_DEFINITION_ID" varchar2(128),
  "PROCESS_INSTANCE_ID" varchar2(128),
  "TASK_ID" varchar2(128),
  "NODE_ID" varchar2(200) NOT NULL,
  "NODE_NAME" varchar2(300),
  "POLICY" varchar2(32) NOT NULL,
  "STATUS" varchar2(32) NOT NULL,
  "EMPTY_REASON_CODE" varchar2(100) NOT NULL,
  "EMPTY_REASON_MESSAGE" varchar2(1000),
  "RESOLVER_CODE" varchar2(200),
  "RESOLVER_EXTRA_PARAMS_JSON" clob,
  "FALLBACK_USER" varchar2(100),
  "FALLBACK_GROUP" varchar2(100),
  "RESPONSIBILITY_OWNER" varchar2(100) NOT NULL,
  "RETRY_COUNT" number(10) NOT NULL DEFAULT '0',
  "MAX_RETRIES" number(10) NOT NULL DEFAULT '0',
  "INITIAL_DELAY_SECONDS" number(10),
  "BACKOFF_MULTIPLIER" number(8,3),
  "NEXT_RETRY_AT" timestamp,
  "RESOLUTION_ACTION" varchar2(64),
  "RESOLVED_BY" varchar2(100),
  "RESOLVED_AT" timestamp,
  "DETAIL_JSON" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "OPEN_SLOT" varchar2(128) GENERATED ALWAYS AS ((case when ("STATUS" in ('OPEN','RETRY_SCHEDULED','MANUAL_REQUIRED')) then concat(coalesce("TASK_ID","PROCESS_INSTANCE_ID",'NO_INSTANCE'),':',"NODE_ID") else NULL end)) VIRTUAL,
  CONSTRAINT "PK_PROCESS_ASSIGNEE_I_CE84E08D" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_ASSIGNEE_INCIDENT_ACTION" (
  "ID" varchar2(64) NOT NULL,
  "INCIDENT_ID" varchar2(64) NOT NULL,
  "REQUEST_ID" varchar2(128) NOT NULL,
  "ACTION_TYPE" varchar2(64) NOT NULL,
  "STATUS" varchar2(24) NOT NULL,
  "OPERATOR" varchar2(100) NOT NULL,
  "REQUEST_JSON" clob,
  "RESULT_JSON" clob,
  "ERROR_MESSAGE" varchar2(1500),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "FINISHED_AT" timestamp,
  CONSTRAINT "PK_PROCESS_ASSIGNEE_I_BCB8FC21" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_CC_RECORD" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_INSTANCE_ID" varchar2(64) NOT NULL,
  "PROCESS_DEFINITION_ID" varchar2(64),
  "PROCESS_KEY" varchar2(100),
  "PROCESS_NAME" varchar2(200),
  "DATA_NAME" varchar2(500),
  "BUSINESS_KEY" varchar2(200),
  "NODE_ID" varchar2(100),
  "NODE_NAME" varchar2(200),
  "CC_USER_ID" varchar2(64),
  "CC_USER_NAME" varchar2(100),
  "CC_TYPE" varchar2(20) DEFAULT 'AUTO',
  "CC_TIMING" varchar2(20),
  "OPERATOR_ID" varchar2(64),
  "OPERATOR_NAME" varchar2(100),
  "COMMENT" varchar2(1000),
  "SOURCE_TASK_ID" varchar2(64),
  "SOURCE_TYPE" varchar2(20),
  "RECIPIENT_RULE_SNAPSHOT" clob,
  "UNIQUE_KEY" varchar2(255),
  "READ_STATUS" varchar2(20) DEFAULT 'UNREAD',
  "READ_TIME" timestamp,
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_CC_RECORD__520122D3" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_DEFINITION_CONFIG" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "PROCESS_KEY" varchar2(100) NOT NULL,
  "PROCESS_NAME" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "CATEGORY" varchar2(100),
  "VERSION" number(10) DEFAULT '1',
  "STATUS" varchar2(20) DEFAULT 'DRAFT',
  "BPMN_XML" clob,
  "DRAFT_REVISION" number(19) NOT NULL DEFAULT '1',
  "PUBLISHED_REVISION" number(19) NOT NULL DEFAULT '0',
  "DRAFT_HASH" char(64),
  "PUBLISHED_DRAFT_HASH" char(64),
  "BASE_PUBLISHED_VERSION" number(10) NOT NULL DEFAULT '0',
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(10) DEFAULT '0',
  "ENTITY_ID" varchar2(64),
  "UPDATED_BY" varchar2(64),
  CONSTRAINT "PK_PROCESS_DEFINITION_0F3B1830" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_ENTITY_STATUS_MAPPING" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_CONFIG_ID" varchar2(64) NOT NULL,
  "PROCESS_KEY" varchar2(100) NOT NULL,
  "ENTITY_CODE" varchar2(100) NOT NULL,
  "SEQUENCE_FLOW_ID" varchar2(100),
  "SOURCE_NODE_ID" varchar2(100) NOT NULL,
  "SOURCE_NODE_NAME" varchar2(200),
  "TARGET_NODE_ID" varchar2(100) NOT NULL,
  "TARGET_NODE_NAME" varchar2(200),
  "ENTITY_STATUS_CODE" varchar2(50) NOT NULL,
  "CONDITION_EXPRESSION" varchar2(500),
  "SORT_ORDER" number(10) DEFAULT '0',
  "DESCRIPTION" varchar2(500),
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "ENTITY_STATUS" varchar2(100) NOT NULL,
  "STATUS_CATEGORY" varchar2(50),
  CONSTRAINT "PK_PROCESS_ENTITY_STA_185BBE36" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_FORM_CONFIG" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "NODE_CONFIG_ID" number(19) NOT NULL,
  "FORM_NAME" varchar2(200) NOT NULL,
  "FORM_KEY" varchar2(100) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "IS_READONLY" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "ENTITY_FORM_ID" varchar2(64),
  "DELETED" number(3) DEFAULT '0',
  CONSTRAINT "PK_PROCESS_FORM_CONFI_7E1462EC" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_FORM_FIELD_CONFIG" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "FORM_CONFIG_ID" number(19) NOT NULL,
  "FIELD_NAME" varchar2(200) NOT NULL,
  "FIELD_KEY" varchar2(100) NOT NULL,
  "FIELD_TYPE" varchar2(50) NOT NULL,
  "IS_REQUIRED" number(3) DEFAULT '0',
  "DEFAULT_VALUE" varchar2(500),
  "OPTIONS_JSON" clob,
  "VALIDATE_RULES" clob,
  "SORT_ORDER" number(10) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) DEFAULT '0',
  CONSTRAINT "PK_PROCESS_FORM_FIELD_B8B2C0A2" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_NODE_APPROVAL" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_CONFIG_ID" varchar2(64) NOT NULL,
  "NODE_ID" varchar2(100) NOT NULL,
  "NODE_NAME" varchar2(100),
  "ENABLED" number(3) DEFAULT '1',
  "COMMENT_LABEL" varchar2(100) DEFAULT '审批意见',
  "OPTIONS_JSON" clob,
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_NODE_APPRO_7D45DF30" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_NODE_APPROVAL_OPTION" (
  "ID" varchar2(64) NOT NULL,
  "APPROVAL_CONFIG_ID" varchar2(64) NOT NULL,
  "OPTION_VALUE" varchar2(100) NOT NULL,
  "OPTION_LABEL" varchar2(200) NOT NULL,
  "STYLE_TYPE" varchar2(50),
  "SHOW_COMMENT" number(3) NOT NULL DEFAULT '1',
  "REMARK_REQUIRED" number(3) NOT NULL DEFAULT '0',
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "OPTION_DOCUMENT" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_NODE_APPRO_4C33D287" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_NODE_ASSIGNEE" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "NODE_CONFIG_ID" number(19) NOT NULL,
  "ASSIGNEE_TYPE" varchar2(50) NOT NULL,
  "ASSIGNEE_VALUE" varchar2(200) NOT NULL,
  "ASSIGNEE_NAME" varchar2(200),
  "PRIORITY" number(10) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) DEFAULT '0',
  CONSTRAINT "PK_PROCESS_NODE_ASSIG_CE1A6F33" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_NODE_CONFIG" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "NODE_ID" varchar2(100) NOT NULL,
  "NODE_NAME" varchar2(200) NOT NULL,
  "NODE_TYPE" varchar2(50) NOT NULL,
  "PROCESS_CONFIG_ID" number(19) NOT NULL,
  "CONFIG_JSON" clob,
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "SKIP_NODE" number(3) DEFAULT '0',
  "DELETED" number(3) DEFAULT '0',
  CONSTRAINT "PK_PROCESS_NODE_CONFI_E428AED5" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_NODE_FORM" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_CONFIG_ID" varchar2(64) NOT NULL,
  "NODE_ID" varchar2(100) NOT NULL,
  "NODE_NAME" varchar2(100),
  "FORM_ID" varchar2(64) NOT NULL,
  "IS_READONLY" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "SORT_ORDER" number(10) DEFAULT '0',
  CONSTRAINT "PK_PROCESS_NODE_FORM__EB8ED6D7" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_OPERATION_LOG" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_INSTANCE_ID" varchar2(64) NOT NULL,
  "TASK_ID" varchar2(64),
  "OPERATION_TYPE" varchar2(50) NOT NULL,
  "OPERATOR_ID" varchar2(64),
  "OPERATOR_NAME" varchar2(100),
  "OPERATION_TIME" timestamp,
  "OPERATION_COMMENT" clob,
  "OLD_VALUE" clob,
  "NEW_VALUE" clob,
  "IP_ADDRESS" varchar2(50),
  "USER_AGENT" clob,
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "OLD_VALUE_FORMAT" varchar2(20) NOT NULL DEFAULT 'JSON',
  "NEW_VALUE_FORMAT" varchar2(20) NOT NULL DEFAULT 'JSON',
  CONSTRAINT "PK_PROCESS_OPERATION__E2AFC6A1" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_PERSON_RESOLVER_DEFINITION" (
  "ID" varchar2(64) NOT NULL,
  "RESOLVER_CODE" varchar2(100) NOT NULL,
  "DISPLAY_NAME" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(1000),
  "BEAN_NAME" varchar2(200) NOT NULL,
  "IMPLEMENTATION_VERSION" number(10) NOT NULL DEFAULT '1',
  "CONTRACT_VERSION" number(10) NOT NULL DEFAULT '1',
  "SUPPORTED_USAGES_DOCUMENT" clob,
  "EXTRA_PARAM_SCHEMA_DOCUMENT" clob,
  "DYNAMIC_EXTRA_PARAMS" number(3) NOT NULL DEFAULT '0',
  "ENABLED" number(3) NOT NULL DEFAULT '0',
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_PROCESS_PERSON_RES_3D1B8281" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_STATUS_SYNC_EVENT" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_INSTANCE_ID" varchar2(64) NOT NULL,
  "EVENT_TYPE" varchar2(50) NOT NULL,
  "EVENT_SEQUENCE" varchar2(128) NOT NULL,
  "ENTITY_CODE" varchar2(63) NOT NULL,
  "ENTITY_RECORD_ID" varchar2(64) NOT NULL,
  "TARGET_STATUS" varchar2(100),
  "STATUS_CATEGORY" varchar2(30),
  "STATE" varchar2(20) NOT NULL,
  "APPLIED_AT" timestamp(6),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_PROCESS_STATUS_SYN_6B593979" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "PROCESS_INSTANCE_ID" varchar2(64) NOT NULL,
  "PROCESS_DEFINITION_ID" varchar2(64) NOT NULL,
  "PROCESS_KEY" varchar2(64) NOT NULL,
  "PROCESS_NAME" varchar2(128),
  "NODE_ID" varchar2(64) NOT NULL,
  "NODE_NAME" varchar2(128),
  "NODE_TYPE" varchar2(32),
  "TASK_ID" varchar2(64),
  "BUSINESS_KEY" varchar2(64),
  "ENTITY_CODE" varchar2(64),
  "ENTITY_DATA_ID" varchar2(64),
  "ASSIGNEE_ID" varchar2(64),
  "ASSIGNEE_NAME" clob,
  "ASSIGNEE_TYPE" varchar2(32),
  "FORM_KEY" varchar2(128),
  "FORM_DATA" clob,
  "STATUS" varchar2(20) DEFAULT 'todo',
  "ACTION" varchar2(32),
  "ACTION_LABEL" varchar2(200),
  "COMMENT" clob,
  "START_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "END_TIME" timestamp,
  "DUE_TIME" timestamp,
  "SLA_STATUS" varchar2(20),
  "RESPONSE_DUE_TIME" timestamp(6),
  "DURATION" number(19),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) DEFAULT '0',
  "TIMEOUT_HOURS" number(10),
  "TIMEOUT_ACTION" varchar2(50),
  "TIMEOUT_HANDLED" number(3) DEFAULT '0',
  "PRIORITY" number(10) DEFAULT '0',
  "START_USER_ID" varchar2(100),
  "BUSINESS_NAME" clob,
  "BUSINESS_CODE" clob,
  "BUSINESS_DATA_NAME" clob,
  "BUSINESS_CURRENT_TASK_NAME" clob,
  "BUSINESS_STATUS" clob,
  "INBOX_SUMMARY_READY" number(3) NOT NULL DEFAULT '0',
  "INBOX_IDENTITY_READY" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_PROCESS_TASK_PROCESS_TASK" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK_ADD_SIGN" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_INSTANCE_ID" varchar2(64) NOT NULL,
  "SOURCE_TASK_ID" varchar2(64) NOT NULL,
  "NODE_ID" varchar2(100),
  "OPERATION_TYPE" varchar2(20) NOT NULL DEFAULT 'PARALLEL',
  "OPERATOR_ID" varchar2(64) NOT NULL,
  "COMMENT" varchar2(1000),
  "STATUS" varchar2(20) NOT NULL DEFAULT 'ACTIVE',
  "ENGINE_EXECUTION_ID" varchar2(64),
  "SOURCE_COMPLETED" number(3) NOT NULL DEFAULT '0',
  "SOURCE_ACTION" varchar2(100),
  "SOURCE_ACTION_LABEL" varchar2(200),
  "SOURCE_COMMENT" varchar2(1000),
  "SOURCE_FORM_DATA" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "COMPLETE_TIME" timestamp,
  CONSTRAINT "PK_PROCESS_TASK_ADD_S_E3F21243" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK_ADD_SIGN_USER" (
  "ID" varchar2(64) NOT NULL,
  "ADD_SIGN_ID" varchar2(64) NOT NULL,
  "USER_ID" varchar2(64) NOT NULL,
  "USER_NAME_SNAPSHOT" varchar2(100),
  "GENERATED_TASK_ID" varchar2(64) NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'TODO',
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "COMPLETE_TIME" timestamp,
  CONSTRAINT "PK_PROCESS_TASK_ADD_S_DDEEF8CB" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK_CANDIDATE_GROUP" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_TASK_ID" number(19) NOT NULL,
  "GROUP_CODE" varchar2(100) NOT NULL,
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_TASK_CANDI_E0136D6A" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK_CANDIDATE_USER" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_TASK_ID" number(19) NOT NULL,
  "USER_ID" varchar2(100) NOT NULL,
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_TASK_CANDI_A2A4FCB0" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK_SLA" (
  "ID" varchar2(64) NOT NULL,
  "TASK_ID" varchar2(64) NOT NULL,
  "PROCESS_INSTANCE_ID" varchar2(64) NOT NULL,
  "PROCESS_DEFINITION_ID" varchar2(100),
  "PROCESS_KEY" varchar2(100),
  "NODE_ID" varchar2(100) NOT NULL,
  "NODE_NAME" varchar2(200),
  "BUSINESS_KEY" varchar2(200),
  "ENTITY_CODE" varchar2(100),
  "ENTITY_DATA_ID" varchar2(64),
  "POLICY_CODE" varchar2(100) NOT NULL,
  "POLICY_VERSION" number(10) NOT NULL,
  "POLICY_SNAPSHOT_JSON" clob NOT NULL,
  "CALENDAR_CODE" varchar2(100),
  "CALENDAR_VERSION" number(10),
  "CALENDAR_SNAPSHOT_JSON" clob,
  "TIMEZONE_ID" varchar2(100) NOT NULL,
  "CURRENT_ASSIGNEE_ID" varchar2(100),
  "STARTED_AT" timestamp(6) NOT NULL,
  "RESPONDED_AT" timestamp(6),
  "COMPLETED_AT" timestamp(6),
  "RESPONSE_DUE_AT" timestamp(6),
  "COMPLETION_DUE_AT" timestamp(6) NOT NULL,
  "RESPONSE_REMAINING_MINUTES" number(10),
  "COMPLETION_REMAINING_MINUTES" number(10),
  "RESPONSE_STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "COMPLETION_STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "OVERALL_STATUS" varchar2(20) NOT NULL DEFAULT 'RUNNING',
  "PAUSE_STARTED_AT" timestamp(6),
  "VERSION" number(10) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_PROCESS_TASK_SLA_P_F682EB6D" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK_SLA_EVENT" (
  "ID" varchar2(64) NOT NULL,
  "SLA_ID" varchar2(64) NOT NULL,
  "TASK_ID" varchar2(64) NOT NULL,
  "STEP_ID" varchar2(64),
  "EVENT_TYPE" varchar2(30) NOT NULL,
  "METRIC_TYPE" varchar2(20) NOT NULL,
  "TRIGGER_AT" timestamp(6) NOT NULL,
  "ACTION_TYPE" varchar2(30) NOT NULL,
  "ACTION_CONFIG_SNAPSHOT" clob,
  "EXECUTION_NO" number(10) NOT NULL DEFAULT '1',
  "MAX_EXECUTIONS" number(10) NOT NULL DEFAULT '1',
  "STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "ATTEMPTS" number(10) NOT NULL DEFAULT '0',
  "MAX_RETRIES" number(10) NOT NULL DEFAULT '5',
  "NEXT_RETRY_TIME" timestamp(6),
  "OWNER_ID" varchar2(128),
  "LEASE_TOKEN" number(19) NOT NULL DEFAULT '0',
  "LEASE_UNTIL" timestamp(6),
  "IDEMPOTENCY_KEY" varchar2(255) NOT NULL,
  "RESULT_JSON" clob,
  "ERROR_MESSAGE" varchar2(4000),
  "STARTED_AT" timestamp(6),
  "FINISHED_AT" timestamp(6),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_PROCESS_TASK_SLA_E_C5492F4A" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_TASK_SLA_PAUSE" (
  "ID" varchar2(64) NOT NULL,
  "SLA_ID" varchar2(64) NOT NULL,
  "TASK_ID" varchar2(64) NOT NULL,
  "PAUSE_TYPE" varchar2(30) NOT NULL,
  "REASON" varchar2(1000) NOT NULL,
  "OPERATOR_ID" varchar2(100),
  "STARTED_AT" timestamp(6) NOT NULL,
  "RESUMED_AT" timestamp(6),
  "DURATION_SECONDS" number(19),
  "RESPONSE_REMAINING_MINUTES" number(10),
  "COMPLETION_REMAINING_MINUTES" number(10),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_PROCESS_TASK_SLA_P_656CEA49" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_UI_RELEASE_BINDING" (
  "ID" varchar2(64) NOT NULL,
  "PROCESS_VERSION_HISTORY_ID" varchar2(64) NOT NULL,
  "PROCESS_CONFIG_ID" varchar2(64) NOT NULL,
  "PROCESS_KEY" varchar2(100) NOT NULL,
  "PROCESS_VERSION" number(10) NOT NULL,
  "DEPLOYMENT_ID" varchar2(100),
  "NODE_ID" varchar2(100) NOT NULL,
  "NODE_NAME" varchar2(200),
  "CONFIG_TYPE" varchar2(20) NOT NULL DEFAULT 'FORM',
  "CONFIG_ID" varchar2(64) NOT NULL,
  "PINNED_RELEASE_ID" varchar2(64) NOT NULL,
  "PINNED_RELEASE_VERSION" number(10) NOT NULL,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_UI_RELEASE_1F8AC905" PRIMARY KEY ("ID")
);

CREATE TABLE "PROCESS_VERSION_HISTORY" (
  "ID" number(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  "PROCESS_CONFIG_ID" number(19) NOT NULL,
  "PROCESS_KEY" varchar2(100) NOT NULL,
  "PROCESS_NAME" varchar2(200) NOT NULL,
  "VERSION" number(10) NOT NULL,
  "VERSION_DESCRIPTION" varchar2(500),
  "BPMN_XML" clob,
  "PUBLISHED_AT" timestamp DEFAULT CURRENT_TIMESTAMP,
  "PUBLISHED_BY" varchar2(64),
  "DEPLOYMENT_ID" varchar2(64),
  "STATUS" varchar2(20) DEFAULT 'ACTIVE',
  "DELETED" number(10) DEFAULT '0',
  "NODE_FORMS_SNAPSHOT" clob,
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_PROCESS_VERSION_HI_22D54CEB" PRIMARY KEY ("ID")
);

CREATE TABLE "STORAGE_FILE_OBJECT" (
  "ID" varchar2(32) NOT NULL,
  "STORAGE_URL" varchar2(1024) NOT NULL,
  "STORAGE_KEY" varchar2(512) NOT NULL,
  "OWNER_USER_ID" varchar2(64) NOT NULL,
  "IDEMPOTENCY_KEY" varchar2(128),
  "REQUEST_HASH" char(64),
  "ORIGINAL_NAME" varchar2(512),
  "CONTENT_TYPE" varchar2(255),
  "CONTENT_LENGTH" number(19) NOT NULL DEFAULT '0',
  "DELETED" number(3) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp(6) NOT NULL,
  "UPDATE_TIME" timestamp(6) NOT NULL,
  CONSTRAINT "PK_STORAGE_FILE_OBJEC_2303BEEE" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_STORAGE_FILE_OBJEC_80FDF8B3" CHECK (((("IDEMPOTENCY_KEY" is null) and ("REQUEST_HASH" is null)) or (("IDEMPOTENCY_KEY" is not null) and regexp_like("REQUEST_HASH",'^[0-9a-f]{64}$'))))
);

CREATE TABLE "SYS_DICT" (
  "ID" varchar2(64) NOT NULL,
  "DICT_CODE" varchar2(100) NOT NULL,
  "DICT_NAME" varchar2(100) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "STATUS" char(1) DEFAULT '0',
  "SORT" number(10) DEFAULT '0',
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_SYS_DICT_SYS_DICT" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_DICT_ITEM" (
  "ID" varchar2(64) NOT NULL,
  "DICT_ID" varchar2(64) NOT NULL,
  "DICT_CODE" varchar2(100) NOT NULL,
  "PARENT_ID" varchar2(64) DEFAULT '0',
  "ITEM_CODE" varchar2(100) NOT NULL,
  "ITEM_LABEL" varchar2(100) NOT NULL,
  "ITEM_VALUE" varchar2(200) NOT NULL,
  "SORT" number(10) DEFAULT '0',
  "STATUS" char(1) DEFAULT '0',
  "REMARK" varchar2(500),
  "DELETED" number(3) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_SYS_DICT_ITEM_SYS_DICT_ITEM" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_EXTERNAL_SYSTEM" (
  "ID" varchar2(64) NOT NULL,
  "SYSTEM_NAME" varchar2(100) NOT NULL,
  "SYSTEM_CODE" varchar2(100) NOT NULL,
  "STATUS" char(1) NOT NULL DEFAULT '0',
  "ADDRESS" varchar2(500) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "VERSION" number(19) NOT NULL DEFAULT '0',
  "CREATED_BY" varchar2(64),
  "UPDATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_SYS_EXTERNAL_SYSTE_565D750B" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_SYS_EXTERNAL_SYSTE_7F741955" CHECK (("DELETED" in (0,1))),
  CONSTRAINT "CK_SYS_EXTERNAL_SYSTE_BC2B85FD" CHECK (("STATUS" in ('0','1'))),
  CONSTRAINT "CK_SYS_EXTERNAL_SYSTE_E09481EB" CHECK (("VERSION" >= 0))
);

CREATE TABLE "SYS_EXTERNAL_SYSTEM_PARAMETER" (
  "ID" varchar2(64) NOT NULL,
  "EXTERNAL_SYSTEM_ID" varchar2(64) NOT NULL,
  "PARAMETER_NAME_ZH" varchar2(100) NOT NULL,
  "PARAMETER_NAME_EN" varchar2(100) NOT NULL,
  "PARAMETER_VALUE" clob NOT NULL,
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "CREATED_BY" varchar2(64),
  "UPDATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "DELETED" number(3) NOT NULL DEFAULT '0',
  "ACTIVE_PARAMETER_NAME_EN" varchar2(100) GENERATED ALWAYS AS ((case when ("DELETED" = 0) then "PARAMETER_NAME_EN" else NULL end)) VIRTUAL,
  CONSTRAINT "PK_SYS_EXTERNAL_SYSTE_338D1109" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_SYS_EXTERNAL_SYSTE_AAD04CCD" CHECK (("DELETED" in (0,1))),
  CONSTRAINT "CK_SYS_EXTERNAL_SYSTE_FEC5AB59" CHECK (("SORT_ORDER" >= 0))
);

CREATE TABLE "SYS_GLOBAL_SETTING" (
  "ID" varchar2(64) NOT NULL,
  "SCOPE_TYPE" varchar2(16) NOT NULL,
  "OWNER_ID" varchar2(64) NOT NULL,
  "SETTING_KEY" varchar2(160) NOT NULL,
  "NAME" varchar2(100) NOT NULL,
  "SETTING_VALUE_TYPE" varchar2(16) NOT NULL,
  "SETTING_VALUE" clob NOT NULL,
  "REMARK" varchar2(500),
  "VERSION" number(19) NOT NULL DEFAULT '0',
  "CREATED_BY" varchar2(64),
  "UPDATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_SYS_GLOBAL_SETTING_98D24A79" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_SYS_GLOBAL_SETTING_E61039BF" CHECK ((char_length(trim("SETTING_KEY")) > 0)),
  CONSTRAINT "CK_SYS_GLOBAL_SETTING_BA61D3A3" CHECK ((char_length(trim("NAME")) > 0)),
  CONSTRAINT "CK_SYS_GLOBAL_SETTING_F91D56C3" CHECK (((("SCOPE_TYPE" = 'SYSTEM') and ("OWNER_ID" = '0')) or (("SCOPE_TYPE" = 'USER') and (char_length(trim("OWNER_ID")) > 0) and ("OWNER_ID" <> '0')))),
  CONSTRAINT "CK_SYS_GLOBAL_SETTING_5A3A3811" CHECK (("SETTING_VALUE_TYPE" in ('BOOLEAN','NUMBER','STRING','JSON'))),
  CONSTRAINT "CK_SYS_GLOBAL_SETTING_13E5992C" CHECK (("VERSION" >= 0))
);

CREATE TABLE "SYS_GROUP" (
  "ID" varchar2(64) NOT NULL,
  "GROUP_NAME" varchar2(50) NOT NULL,
  "GROUP_CODE" varchar2(50) NOT NULL,
  "DESCRIPTION" varchar2(200),
  "SORT" number(10) DEFAULT '0',
  "STATUS" char(1) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) DEFAULT '0',
  "PARENT_ID" varchar2(64),
  "SORT_ORDER" number(10) DEFAULT '0',
  CONSTRAINT "PK_SYS_GROUP_SYS_GROUP" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_MENU" (
  "ID" varchar2(64) NOT NULL,
  "PARENT_ID" varchar2(64) DEFAULT '0',
  "MENU_NAME" varchar2(100) NOT NULL,
  "MENU_TYPE" char(1) DEFAULT 'M',
  "ICON" varchar2(100),
  "SORT" number(10) DEFAULT '0',
  "PATH" varchar2(200),
  "COMPONENT" varchar2(255),
  "PERM" varchar2(200),
  "STATUS" char(1) DEFAULT '1',
  "VISIBLE" char(1) DEFAULT '1',
  "KEEP_ALIVE" char(1) DEFAULT '0',
  "BREADCRUMB" char(1) DEFAULT '1',
  "REMARK" varchar2(500),
  "DELETED" number(10) DEFAULT '0',
  "CREATE_BY" varchar2(64),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_BY" varchar2(64),
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "IS_FRAME" char(1) DEFAULT '0',
  "IS_CACHE" char(1) DEFAULT '0',
  "QUERY" varchar2(255),
  "ENTITY_CODE" varchar2(100),
  "RESOURCE_TYPE" varchar2(30),
  "LIST_KEY" varchar2(100),
  CONSTRAINT "PK_SYS_MENU_SYS_MENU" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_ORGANIZATION" (
  "ID" varchar2(64) NOT NULL,
  "ORG_CODE" varchar2(100) NOT NULL,
  "ORG_NAME" varchar2(100) NOT NULL,
  "TYPE" varchar2(20) NOT NULL,
  "BUSINESS_LEVEL_CODE" varchar2(100),
  "PARENT_ID" varchar2(64) DEFAULT '0',
  "LEVEL" number(10) DEFAULT '0',
  "PATH" varchar2(500) DEFAULT '/',
  "SORT_ORDER" number(10) DEFAULT '0',
  "LEADER_ID" varchar2(64),
  "LEADER_NAME" varchar2(100),
  "PHONE" varchar2(50),
  "EMAIL" varchar2(100),
  "ADDRESS" varchar2(200),
  "STATUS" varchar2(10) DEFAULT '0',
  "DESCRIPTION" varchar2(500),
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(10) DEFAULT '0',
  CONSTRAINT "PK_SYS_ORGANIZATION_S_44BEBB18" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_POSITION" (
  "ID" varchar2(64) NOT NULL,
  "POSITION_CODE" varchar2(100) NOT NULL,
  "POSITION_NAME" varchar2(100) NOT NULL,
  "APPLICABLE_UNIT_TYPE" varchar2(16) NOT NULL,
  "HOLDER_MODE" varchar2(16) NOT NULL,
  "BUILT_IN" number(3) NOT NULL DEFAULT '0',
  "STATUS" varchar2(16) NOT NULL DEFAULT 'ENABLED',
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "DESCRIPTION" varchar2(500),
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "CREATED_BY" varchar2(64),
  "UPDATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_SYS_POSITION_SYS_POSITION" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_SYS_POSITION_CHK_S_AE589CD3" CHECK ((("BUILT_IN" in (0,1)) and ("DELETED" in (0,1)))),
  CONSTRAINT "CK_SYS_POSITION_CHK_S_B341DD2A" CHECK (("HOLDER_MODE" in ('SINGLE','MULTIPLE'))),
  CONSTRAINT "CK_SYS_POSITION_CHK_S_37343EF4" CHECK (("REVISION" >= 1)),
  CONSTRAINT "CK_SYS_POSITION_CHK_S_8B981B2E" CHECK (("STATUS" in ('ENABLED','DISABLED'))),
  CONSTRAINT "CK_SYS_POSITION_CHK_S_B4B285AE" CHECK (("APPLICABLE_UNIT_TYPE" in ('ORG','DEPT','ANY')))
);

CREATE TABLE "SYS_POSITION_ASSIGNMENT" (
  "ID" varchar2(64) NOT NULL,
  "POSITION_ID" varchar2(64) NOT NULL,
  "ORGANIZATION_UNIT_ID" varchar2(64) NOT NULL,
  "USER_ID" varchar2(64) NOT NULL,
  "IS_PRIMARY" number(3) NOT NULL DEFAULT '0',
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "EFFECTIVE_FROM" timestamp(6) NOT NULL,
  "EFFECTIVE_TO" timestamp(6),
  "REVOKED_AT" timestamp(6),
  "REVOKED_BY" varchar2(64),
  "REVOKE_REASON" varchar2(500),
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "CREATED_BY" varchar2(64),
  "UPDATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_SYS_POSITION_ASSIG_A2BD0BD3" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_SYS_POSITION_ASSIG_9969EAA2" CHECK ((("EFFECTIVE_TO" is null) or ("EFFECTIVE_TO" > "EFFECTIVE_FROM"))),
  CONSTRAINT "CK_SYS_POSITION_ASSIG_FE67BF54" CHECK (("IS_PRIMARY" in (0,1))),
  CONSTRAINT "CK_SYS_POSITION_ASSIG_86D0DB9B" CHECK (("REVISION" >= 1)),
  CONSTRAINT "CK_SYS_POSITION_ASSIG_325B0E11" CHECK ((("REVOKED_AT" is null) or ("REVOKED_AT" >= "EFFECTIVE_FROM")))
);

CREATE TABLE "SYS_POSITION_ASSIGNMENT_BATCH" (
  "ID" varchar2(64) NOT NULL,
  "IDEMPOTENCY_KEY" varchar2(128) NOT NULL,
  "REQUEST_HASH" char(64) NOT NULL,
  "ASSIGNMENT_IDS_JSON" clob NOT NULL,
  "CREATED_BY" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_SYS_POSITION_ASSIG_B8769B21" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_SYS_POSITION_ASSIG_1B260826" CHECK (regexp_like("REQUEST_HASH",'^[0-9a-f]{64}$')),
  CONSTRAINT "CK_SYS_POSITION_ASSIG_0C43D7C5" CHECK ("ASSIGNMENT_IDS_JSON" IS JSON)
);

CREATE TABLE "SYS_ROLE" (
  "ID" varchar2(64) NOT NULL,
  "ROLE_NAME" varchar2(50) NOT NULL,
  "ROLE_CODE" varchar2(50) NOT NULL,
  "DESCRIPTION" varchar2(200),
  "SORT" number(10) DEFAULT '0',
  "STATUS" char(1) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) DEFAULT '0',
  "SORT_ORDER" number(10) DEFAULT '0',
  CONSTRAINT "PK_SYS_ROLE_SYS_ROLE" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_ROLE_MENU" (
  "ID" varchar2(64) NOT NULL,
  "ROLE_ID" varchar2(64) NOT NULL,
  "MENU_ID" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_SYS_ROLE_MENU_SYS_ROLE_MENU" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_USER" (
  "ID" varchar2(64) NOT NULL,
  "USERNAME" varchar2(50) NOT NULL,
  "NICKNAME" varchar2(50),
  "PASSWORD" varchar2(100) NOT NULL,
  "EMAIL" varchar2(100),
  "PHONE" varchar2(20),
  "AVATAR" varchar2(255),
  "STATUS" char(1) DEFAULT '0',
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) DEFAULT '0',
  "ORG_ID" varchar2(64),
  "DEPT_ID" varchar2(64),
  "PASSWORD_RESET_REQUIRED" number(3) NOT NULL DEFAULT '0',
  "TOKEN_VERSION" number(19) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_SYS_USER_SYS_USER" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_USER_GROUP" (
  "ID" varchar2(64) NOT NULL,
  "USER_ID" varchar2(64) NOT NULL,
  "GROUP_ID" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_SYS_USER_GROUP_SYS_54C0C20F" PRIMARY KEY ("ID")
);

CREATE TABLE "SYS_USER_ROLE" (
  "ID" varchar2(64) NOT NULL,
  "USER_ID" varchar2(64) NOT NULL,
  "ROLE_ID" varchar2(64) NOT NULL,
  "CREATE_TIME" timestamp DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_SYS_USER_ROLE_SYS_USER_ROLE" PRIMARY KEY ("ID")
);

CREATE TABLE "SYSTEM_OPERATION_LOG" (
  "ID" varchar2(64) NOT NULL,
  "EVENT_ID" varchar2(64) NOT NULL,
  "OPERATION_ID" varchar2(128),
  "TRACE_ID" varchar2(64),
  "PARENT_OPERATION_ID" varchar2(128),
  "SOURCE_SYSTEM" varchar2(32),
  "SOURCE_TYPE" varchar2(64),
  "SOURCE_ID" varchar2(128),
  "SOURCE_EVENT_ID" varchar2(128),
  "MODULE_CODE" varchar2(32) NOT NULL,
  "OPERATION_CODE" varchar2(64) NOT NULL,
  "OPERATION_NAME" varchar2(128) NOT NULL,
  "RISK_LEVEL" varchar2(16) NOT NULL,
  "RESULT" varchar2(16) NOT NULL,
  "OPERATOR_ID" varchar2(64),
  "OPERATOR_NAME" varchar2(100),
  "OPERATOR_IP" varchar2(64),
  "USER_AGENT" varchar2(512),
  "REQUEST_METHOD" varchar2(16),
  "REQUEST_PATH" varchar2(512),
  "TARGET_TYPE" varchar2(64),
  "TARGET_ID" varchar2(128),
  "TARGET_NAME" varchar2(255),
  "SUMMARY" varchar2(1000),
  "BEFORE_JSON" clob,
  "AFTER_JSON" clob,
  "CHANGED_FIELDS_JSON" clob,
  "PAYLOAD_TRUNCATED" number(3) NOT NULL DEFAULT '0',
  "ERROR_CODE" varchar2(100),
  "ERROR_MESSAGE" varchar2(1000),
  "DURATION_MS" number(19),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_SYSTEM_OPERATION_L_52E6FD66" PRIMARY KEY ("ID")
);

CREATE TABLE "TASK_SLA_ESCALATION_STEP" (
  "ID" varchar2(64) NOT NULL,
  "POLICY_ID" varchar2(64) NOT NULL,
  "STEP_NAME" varchar2(200) NOT NULL,
  "METRIC_TYPE" varchar2(20) NOT NULL,
  "TRIGGER_TYPE" varchar2(20) NOT NULL,
  "OFFSET_MINUTES" number(10) NOT NULL DEFAULT '0',
  "REPEAT_INTERVAL_MINUTES" number(10),
  "MAX_EXECUTIONS" number(10) NOT NULL DEFAULT '1',
  "ACTION_TYPE" varchar2(30) NOT NULL,
  "TEMPLATE_CODE" varchar2(100),
  "RECIPIENT_CONFIG_JSON" clob,
  "TARGET_CONFIG_JSON" clob,
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_TASK_SLA_ESCALATIO_29E6E017" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_TASK_SLA_ESCALATIO_F9D30FB3" CHECK (("MAX_EXECUTIONS" > 0)),
  CONSTRAINT "CK_TASK_SLA_ESCALATIO_CE3BFDD4" CHECK ((("REPEAT_INTERVAL_MINUTES" is null) or ("REPEAT_INTERVAL_MINUTES" > 0)))
);

CREATE TABLE "TASK_SLA_POLICY" (
  "ID" varchar2(64) NOT NULL,
  "POLICY_CODE" varchar2(100) NOT NULL,
  "POLICY_NAME" varchar2(200) NOT NULL,
  "DESCRIPTION" varchar2(1000),
  "VERSION" number(10) NOT NULL DEFAULT '1',
  "RESPONSE_TARGET_MINUTES" number(10),
  "COMPLETION_TARGET_MINUTES" number(10) NOT NULL,
  "RESPONSE_TIME_BASIS" varchar2(20) NOT NULL DEFAULT 'WORKING_TIME',
  "COMPLETION_TIME_BASIS" varchar2(20) NOT NULL DEFAULT 'WORKING_TIME',
  "ALLOW_MANUAL_PAUSE" number(3) NOT NULL DEFAULT '0',
  "PAUSE_ON_PROCESS_SUSPEND" number(3) NOT NULL DEFAULT '1',
  "MAX_PAUSE_MINUTES" number(10),
  "STATUS" varchar2(20) NOT NULL DEFAULT 'DRAFT',
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATED_BY" varchar2(64),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_TASK_SLA_POLICY_TA_4DEEE568" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_TASK_SLA_POLICY_CH_C5FCB71D" CHECK (("COMPLETION_TARGET_MINUTES" > 0)),
  CONSTRAINT "CK_TASK_SLA_POLICY_CH_9C4C044C" CHECK ((("RESPONSE_TARGET_MINUTES" is null) or ("RESPONSE_TARGET_MINUTES" > 0)))
);

CREATE TABLE "UI_COMPONENT_TEMPLATE" (
  "ID" varchar2(64) NOT NULL,
  "TEMPLATE_KEY" varchar2(100) NOT NULL,
  "TEMPLATE_NAME" varchar2(200) NOT NULL,
  "TEMPLATE_TYPE" varchar2(30) NOT NULL,
  "CURRENT_VERSION" number(10) NOT NULL DEFAULT '1',
  "STATUS" varchar2(20) NOT NULL DEFAULT 'ACTIVE',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_UI_COMPONENT_TEMPL_0E1321F4" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_COMPONENT_TEMPLATE_VERSION" (
  "ID" varchar2(64) NOT NULL,
  "TEMPLATE_ID" varchar2(64) NOT NULL,
  "VERSION" number(10) NOT NULL,
  "SNAPSHOT_DOCUMENT" clob NOT NULL,
  "CONTENT_HASH" varchar2(64) NOT NULL,
  "DESCRIPTION" varchar2(500),
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_UI_COMPONENT_TEMPL_3A3C0865" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_CONFIG_HOTFIX_REQUEST" (
  "ID" varchar2(64) NOT NULL,
  "CONFIG_TYPE" varchar2(20) NOT NULL,
  "CONFIG_ID" varchar2(64) NOT NULL,
  "DRAFT_HASH" char(64) NOT NULL,
  "ACTIVE_RELEASE_ID" varchar2(64) NOT NULL,
  "TARGET_HASH" varchar2(255) NOT NULL,
  "IMPACT_TOKEN_HASH" char(64) NOT NULL,
  "RISK_LEVEL" varchar2(20) NOT NULL,
  "REASON" varchar2(1000) NOT NULL,
  "TICKET_REF" varchar2(255) NOT NULL,
  "IMPACT_DOCUMENT" clob NOT NULL,
  "APPLICANT_ID" varchar2(64) NOT NULL,
  "APPLICANT_NAME" varchar2(100),
  "WINDOW_START" timestamp NOT NULL,
  "WINDOW_END" timestamp NOT NULL,
  "REVIEW_REQUIRED" number(3) NOT NULL DEFAULT '0',
  "STATUS" varchar2(30) NOT NULL,
  "OPEN_SLOT" number(3) GENERATED ALWAYS AS ((case when ("STATUS" in ('PENDING_REVIEW','APPROVED','PUBLISHING')) then 1 else NULL end)) VIRTUAL,
  "REVIEWER_ID" varchar2(64),
  "REVIEWER_NAME" varchar2(100),
  "REVIEW_COMMENT" varchar2(1000),
  "REVIEWED_AT" timestamp,
  "RELEASE_ID" varchar2(64),
  "PUBLISHED_AT" timestamp,
  "OBSERVATION_START" timestamp,
  "OBSERVATION_END" timestamp,
  "OBSERVATION_STATUS" varchar2(20),
  "ROLLED_BACK_BY" varchar2(64),
  "ROLLED_BACK_AT" timestamp,
  "ROLLBACK_REASON" varchar2(1000),
  "CANCELLED_BY" varchar2(64),
  "CANCELLED_AT" timestamp,
  "CANCEL_REASON" varchar2(1000),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_UI_CONFIG_HOTFIX_R_857C56FD" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_CONFIG_HOTFIX_TARGET" (
  "ID" varchar2(64) NOT NULL,
  "HOTFIX_RELEASE_ID" varchar2(64) NOT NULL,
  "CONFIG_TYPE" varchar2(20) NOT NULL,
  "CONFIG_ID" varchar2(64) NOT NULL,
  "PROCESS_VERSION_HISTORY_ID" varchar2(64) NOT NULL,
  "PINNED_RELEASE_ID" varchar2(64) NOT NULL,
  "PINNED_RELEASE_VERSION" number(10) NOT NULL,
  "PREVIOUS_TARGET_ID" varchar2(64),
  "EFFECTIVE_SNAPSHOT_DOCUMENT" clob NOT NULL,
  "EFFECTIVE_CONTENT_HASH" varchar2(64) NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'ACTIVE',
  "ACTIVE_SLOT" number(3) GENERATED ALWAYS AS ((case when ("STATUS" = 'ACTIVE') then 1 else NULL end)) VIRTUAL,
  "ACTIVATED_BY" varchar2(64),
  "ACTIVATED_AT" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "ROLLED_BACK_BY" varchar2(64),
  "ROLLED_BACK_AT" timestamp,
  CONSTRAINT "PK_UI_CONFIG_HOTFIX_T_F2C27152" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_CONFIG_RELEASE" (
  "ID" varchar2(64) NOT NULL,
  "CONFIG_TYPE" varchar2(20) NOT NULL,
  "CONFIG_ID" varchar2(64) NOT NULL,
  "VERSION" number(10) NOT NULL,
  "SNAPSHOT_DOCUMENT" clob NOT NULL,
  "CONTENT_HASH" varchar2(64) NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'INACTIVE',
  "ACTIVE_SLOT" number(3) GENERATED ALWAYS AS ((case when ("STATUS" = 'ACTIVE') then 1 else NULL end)) VIRTUAL,
  "DESCRIPTION" varchar2(500),
  "PUBLISHED_BY" varchar2(64),
  "PUBLISHED_AT" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "RELEASE_MODE" varchar2(20) NOT NULL DEFAULT 'STANDARD',
  "BASE_RELEASE_ID" varchar2(64),
  "RISK_LEVEL" varchar2(20) NOT NULL DEFAULT 'SAFE',
  "ROLLOUT_SCOPE" varchar2(30),
  "PATCH_DOCUMENT" clob,
  "OVERRIDE_RISK" number(3) NOT NULL DEFAULT '0',
  "OVERRIDE_REASON" varchar2(1000),
  CONSTRAINT "PK_UI_CONFIG_RELEASE__CC2EA188" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_CONFIG_RELEASE_AUDIT" (
  "ID" varchar2(64) NOT NULL,
  "CONFIG_TYPE" varchar2(20) NOT NULL,
  "CONFIG_ID" varchar2(64) NOT NULL,
  "RELEASE_ID" varchar2(64),
  "OPERATION" varchar2(40) NOT NULL,
  "RISK_LEVEL" varchar2(20),
  "ACTOR_ID" varchar2(64),
  "ACTOR_NAME" varchar2(100),
  "REASON" varchar2(1000),
  "TRACE_ID" varchar2(100),
  "DETAIL_DOCUMENT" clob,
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_UI_CONFIG_RELEASE__3771E800" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_EVENT_BINDING" (
  "ID" varchar2(64) NOT NULL,
  "OWNER_TYPE" varchar2(20) NOT NULL,
  "OWNER_ID" varchar2(64) NOT NULL,
  "TARGET_TYPE" varchar2(20) NOT NULL DEFAULT 'OWNER',
  "TARGET_KEY" varchar2(100) NOT NULL,
  "EVENT_CODE" varchar2(50) NOT NULL,
  "INHERITANCE_MODE" varchar2(20) NOT NULL DEFAULT 'INHERIT',
  "STEPS_DOCUMENT" clob,
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "ENABLED" number(3) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_UI_EVENT_BINDING_U_4CEBA760" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_EXTENSION_DEFINITION" (
  "ID" varchar2(64) NOT NULL,
  "EXTENSION_TYPE" varchar2(20) NOT NULL,
  "EXTENSION_KEY" varchar2(255) NOT NULL,
  "DISPLAY_NAME" varchar2(200) NOT NULL,
  "VERSION" number(10) NOT NULL,
  "SNAPSHOT_VERSION" number(10) NOT NULL DEFAULT '1',
  "VISIBILITY_SCOPE" varchar2(20) NOT NULL DEFAULT 'GLOBAL',
  "ENTITY_CODES_DOCUMENT" clob,
  "SUPPORTED_MODES_DOCUMENT" clob,
  "SUPPORTED_NODE_TYPES_DOCUMENT" clob,
  "SUPPORTED_BINDINGS_DOCUMENT" clob,
  "CONFIG_SCHEMA_DOCUMENT" clob,
  "CAPABILITIES_DOCUMENT" clob,
  "IMPLEMENTATION_TYPE" varchar2(30),
  "PROVIDER_CODE" varchar2(100),
  "SCOPE_TYPE" varchar2(20),
  "SCOPE_ID" varchar2(64),
  "IMPLEMENTATION_CONFIG_DOCUMENT" clob,
  "EXECUTION_POLICY_DOCUMENT" clob,
  "INPUT_SCHEMA_DOCUMENT" clob,
  "OUTPUT_SCHEMA_DOCUMENT" clob,
  "INTERFACE_KIND" varchar2(20),
  "INTERFACE_CONTEXT_TYPE" varchar2(20),
  "PROVIDER_OPERATION_CODE" varchar2(100),
  "LEGACY_SERVICE_ID" varchar2(64),
  "STATUS" varchar2(20) NOT NULL DEFAULT 'ACTIVE',
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_UI_EXTENSION_DEFIN_BF8114D9" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_HOTFIX_OBSERVATION_METRIC" (
  "ID" varchar2(64) NOT NULL,
  "REQUEST_ID" varchar2(64) NOT NULL,
  "RELEASE_ID" varchar2(64) NOT NULL,
  "METRIC_CODE" varchar2(40) NOT NULL,
  "TOTAL_COUNT" number(19) NOT NULL DEFAULT '0',
  "FAILURE_COUNT" number(19) NOT NULL DEFAULT '0',
  "LAST_ERROR" varchar2(1000),
  "LAST_OBSERVED_AT" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "PK_UI_HOTFIX_OBSERVAT_778603FE" PRIMARY KEY ("ID")
);

CREATE TABLE "UI_VIEW_COMPOSITION" (
  "ID" varchar2(64) NOT NULL,
  "OWNER_TYPE" varchar2(20) NOT NULL,
  "OWNER_ID" varchar2(64) NOT NULL,
  "COMPOSITION_KEY" varchar2(100) NOT NULL,
  "ANCHOR_TYPE" varchar2(32) NOT NULL DEFAULT 'OWNER',
  "ANCHOR_KEY" varchar2(160),
  "CONFIG_DOCUMENT" clob NOT NULL,
  "ORDER_KEY" number(19) NOT NULL DEFAULT '1000',
  "REVISION" number(10) NOT NULL DEFAULT '1',
  "CREATE_TIME" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "UPDATE_TIME" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  "DELETED" number(3) NOT NULL DEFAULT '0',
  "ACTIVE_COMPOSITION_KEY" varchar2(100) GENERATED ALWAYS AS ((case when ("DELETED" = 0) then "COMPOSITION_KEY" else NULL end)) VIRTUAL,
  CONSTRAINT "PK_UI_VIEW_COMPOSITIO_1EFA898A" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_UI_VIEW_COMPOSITIO_F8D4EA4B" CHECK (("DELETED" in (0,1))),
  CONSTRAINT "CK_UI_VIEW_COMPOSITIO_4E9C169F" CHECK (("OWNER_TYPE" in ('FORM','LIST'))),
  CONSTRAINT "CK_UI_VIEW_COMPOSITIO_273A58E9" CHECK (("REVISION" >= 1))
);

CREATE TABLE "WORK_CALENDAR" (
  "ID" varchar2(64) NOT NULL,
  "CALENDAR_CODE" varchar2(100) NOT NULL,
  "CALENDAR_NAME" varchar2(200) NOT NULL,
  "TIMEZONE_ID" varchar2(100) NOT NULL,
  "DESCRIPTION" varchar2(1000),
  "VERSION" number(10) NOT NULL DEFAULT '1',
  "DEFAULT_FLAG" number(3) NOT NULL DEFAULT '0',
  "STATUS" varchar2(20) NOT NULL DEFAULT 'DRAFT',
  "EFFECTIVE_FROM" date,
  "EFFECTIVE_TO" date,
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATED_BY" varchar2(64),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_WORK_CALENDAR_WORK_CALENDAR" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_WORK_CALENDAR_CHK__6434B90B" CHECK (("VERSION" > 0))
);

CREATE TABLE "WORK_CALENDAR_BINDING" (
  "ID" varchar2(64) NOT NULL,
  "SCOPE_TYPE" varchar2(30) NOT NULL,
  "SCOPE_KEY" varchar2(100) NOT NULL,
  "CALENDAR_ID" varchar2(64) NOT NULL,
  "PRIORITY" number(10) NOT NULL DEFAULT '0',
  "EFFECTIVE_FROM" date,
  "EFFECTIVE_TO" date,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'ENABLED',
  "CREATED_BY" varchar2(64),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATED_BY" varchar2(64),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "DELETED" number(3) NOT NULL DEFAULT '0',
  CONSTRAINT "PK_WORK_CALENDAR_BIND_04530132" PRIMARY KEY ("ID")
);

CREATE TABLE "WORK_CALENDAR_EXCEPTION" (
  "ID" varchar2(64) NOT NULL,
  "CALENDAR_ID" varchar2(64) NOT NULL,
  "EXCEPTION_DATE" date NOT NULL,
  "EXCEPTION_TYPE" varchar2(20) NOT NULL,
  "EXCEPTION_NAME" varchar2(200),
  "DESCRIPTION" varchar2(1000),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_WORK_CALENDAR_EXCE_80EA2AFD" PRIMARY KEY ("ID")
);

CREATE TABLE "WORK_CALENDAR_EXCEPTION_PERIOD" (
  "ID" varchar2(64) NOT NULL,
  "EXCEPTION_ID" varchar2(64) NOT NULL,
  "START_MINUTE" number(5) NOT NULL,
  "END_MINUTE" number(5) NOT NULL,
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_WORK_CALENDAR_EXCE_B6074715" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_WORK_CALENDAR_EXCE_D142556B" CHECK ((("START_MINUTE" >= 0) and ("END_MINUTE" <= 1440) and ("START_MINUTE" < "END_MINUTE")))
);

CREATE TABLE "WORK_CALENDAR_PERIOD" (
  "ID" varchar2(64) NOT NULL,
  "CALENDAR_ID" varchar2(64) NOT NULL,
  "DAY_OF_WEEK" number(3) NOT NULL,
  "START_MINUTE" number(5) NOT NULL,
  "END_MINUTE" number(5) NOT NULL,
  "SORT_ORDER" number(10) NOT NULL DEFAULT '0',
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_WORK_CALENDAR_PERI_3E3998A4" PRIMARY KEY ("ID"),
  CONSTRAINT "CK_WORK_CALENDAR_PERI_B0E067B2" CHECK (("DAY_OF_WEEK" between 1 and 7)),
  CONSTRAINT "CK_WORK_CALENDAR_PERI_402180E9" CHECK ((("START_MINUTE" >= 0) and ("END_MINUTE" <= 1440) and ("START_MINUTE" < "END_MINUTE")))
);

CREATE TABLE "WORKFLOW_BOOTSTRAP_JOB" (
  "JOB_NAME" varchar2(100) NOT NULL,
  "COMPLETED_VERSION" number(10) NOT NULL DEFAULT '0',
  "OWNER_ID" varchar2(128),
  "COMPLETED_AT" timestamp(6),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT "PK_WORKFLOW_BOOTSTRAP_9EC50F61" PRIMARY KEY ("JOB_NAME")
);

CREATE TABLE "WORKFLOW_OUTBOX_EVENT" (
  "ID" varchar2(64) NOT NULL,
  "TOPIC" varchar2(100) NOT NULL,
  "EVENT_KEY" varchar2(200) NOT NULL,
  "AGGREGATE_TYPE" varchar2(100),
  "AGGREGATE_ID" varchar2(128),
  "PAYLOAD_DOCUMENT" clob NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "OWNER_ID" varchar2(128),
  "LEASE_TOKEN" number(19) NOT NULL DEFAULT '0',
  "LEASE_UNTIL" timestamp(6),
  "RETRY_COUNT" number(10) NOT NULL DEFAULT '0',
  "MAX_RETRIES" number(10) NOT NULL DEFAULT '8',
  "NEXT_RETRY_TIME" timestamp,
  "ERROR_MESSAGE" varchar2(1000),
  "CREATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "UPDATE_TIME" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "PROCESSED_TIME" timestamp,
  CONSTRAINT "PK_WORKFLOW_OUTBOX_EV_1326468D" PRIMARY KEY ("ID")
);

CREATE TABLE "WORKFLOW_SCHEMA_CHANGE" (
  "ID" varchar2(36) NOT NULL,
  "DDL_HASH" char(64) NOT NULL,
  "ACTIVE_HASH" char(64),
  "DDL_STATEMENT" clob NOT NULL,
  "STATUS" varchar2(20) NOT NULL DEFAULT 'PENDING',
  "ATTEMPT" number(10) NOT NULL DEFAULT '0',
  "OWNER_ID" varchar2(128),
  "LEASE_TOKEN" number(19) NOT NULL DEFAULT '0',
  "LEASE_UNTIL" timestamp(6),
  "NEXT_ATTEMPT_AT" timestamp(6) NOT NULL,
  "LAST_ERROR" varchar2(1000),
  "CREATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "UPDATE_TIME" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  "COMPLETED_TIME" timestamp(6),
  CONSTRAINT "PK_WORKFLOW_SCHEMA_CH_FC321CA4" PRIMARY KEY ("ID")
);

-- 普通索引和唯一索引。
CREATE UNIQUE INDEX "UQ_ACT_APP_APPDEF_ACT_7B8D2CFD" ON "ACT_APP_APPDEF" ("KEY_", "VERSION_", "TENANT_ID_");
CREATE INDEX "IX_ACT_APP_APPDEF_ACT_F9621757" ON "ACT_APP_APPDEF" ("DEPLOYMENT_ID_");
CREATE INDEX "IX_ACT_APP_DEPLOYMENT_59812A81" ON "ACT_APP_DEPLOYMENT_RESOURCE" ("DEPLOYMENT_ID_");
CREATE UNIQUE INDEX "UQ_ACT_CMMN_CASEDEF_A_61516D8B" ON "ACT_CMMN_CASEDEF" ("KEY_", "VERSION_", "TENANT_ID_");
CREATE INDEX "IX_ACT_CMMN_CASEDEF_A_C3FA5E93" ON "ACT_CMMN_CASEDEF" ("DEPLOYMENT_ID_");
CREATE INDEX "IX_ACT_CMMN_DEPLOYMEN_D1044D6F" ON "ACT_CMMN_DEPLOYMENT_RESOURCE" ("DEPLOYMENT_ID_");
CREATE INDEX "IX_ACT_CMMN_HI_CASE_I_B540B55E" ON "ACT_CMMN_HI_CASE_INST" ("END_TIME_");
CREATE INDEX "IX_ACT_CMMN_HI_PLAN_I_0252D322" ON "ACT_CMMN_HI_PLAN_ITEM_INST" ("CASE_INST_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_CASE_I_13ACD2AB" ON "ACT_CMMN_RU_CASE_INST" ("CASE_DEF_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_CASE_I_44555E19" ON "ACT_CMMN_RU_CASE_INST" ("PARENT_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_CASE_I_990E4C80" ON "ACT_CMMN_RU_CASE_INST" ("REFERENCE_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_MIL_IN_AFDB8B3F" ON "ACT_CMMN_RU_MIL_INST" ("CASE_DEF_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_MIL_IN_6D31BFD6" ON "ACT_CMMN_RU_MIL_INST" ("CASE_INST_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_PLAN_I_CC9E7A26" ON "ACT_CMMN_RU_PLAN_ITEM_INST" ("CASE_DEF_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_PLAN_I_C940632F" ON "ACT_CMMN_RU_PLAN_ITEM_INST" ("CASE_INST_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_PLAN_I_24D7FC34" ON "ACT_CMMN_RU_PLAN_ITEM_INST" ("STAGE_INST_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_SENTRY_5DF80BAB" ON "ACT_CMMN_RU_SENTRY_PART_INST" ("CASE_DEF_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_SENTRY_8D13E080" ON "ACT_CMMN_RU_SENTRY_PART_INST" ("CASE_INST_ID_");
CREATE INDEX "IX_ACT_CMMN_RU_SENTRY_2B173941" ON "ACT_CMMN_RU_SENTRY_PART_INST" ("PLAN_ITEM_INST_ID_");
CREATE UNIQUE INDEX "UQ_ACT_DMN_DECISION_A_6D52538A" ON "ACT_DMN_DECISION" ("KEY_", "VERSION_", "TENANT_ID_");
CREATE INDEX "IX_ACT_DMN_DEPLOYMENT_2A2B39E0" ON "ACT_DMN_DEPLOYMENT_RESOURCE" ("DEPLOYMENT_ID_");
CREATE INDEX "IX_ACT_DMN_HI_DECISIO_F003E3A5" ON "ACT_DMN_HI_DECISION_EXECUTION" ("INSTANCE_ID_");
CREATE INDEX "IX_ACT_GE_BYTEARRAY_A_0619DC8A" ON "ACT_GE_BYTEARRAY" ("DEPLOYMENT_ID_");
CREATE INDEX "IX_ACT_HI_ACTINST_ACT_1BDED7E4" ON "ACT_HI_ACTINST" ("START_TIME_");
CREATE INDEX "IX_ACT_HI_ACTINST_ACT_0912BA93" ON "ACT_HI_ACTINST" ("END_TIME_");
CREATE INDEX "IX_ACT_HI_ACTINST_ACT_12BD2E51" ON "ACT_HI_ACTINST" ("PROC_INST_ID_", "ACT_ID_");
CREATE INDEX "IX_ACT_HI_ACTINST_ACT_BBCC5335" ON "ACT_HI_ACTINST" ("EXECUTION_ID_", "ACT_ID_");
CREATE INDEX "IX_ACT_HI_DETAIL_ACT__1546BD15" ON "ACT_HI_DETAIL" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_HI_DETAIL_ACT__D7359BC6" ON "ACT_HI_DETAIL" ("ACT_INST_ID_");
CREATE INDEX "IX_ACT_HI_DETAIL_ACT__171A44ED" ON "ACT_HI_DETAIL" ("TIME_");
CREATE INDEX "IX_ACT_HI_DETAIL_ACT__10519668" ON "ACT_HI_DETAIL" ("NAME_");
CREATE INDEX "IX_ACT_HI_DETAIL_ACT__2BE8B1A8" ON "ACT_HI_DETAIL" ("TASK_ID_");
CREATE INDEX "IX_ACT_HI_ENTITYLINK__1C6AF4C9" ON "ACT_HI_ENTITYLINK" ("SCOPE_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_HI_ENTITYLINK__7D936FFE" ON "ACT_HI_ENTITYLINK" ("REF_SCOPE_ID_", "REF_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_HI_ENTITYLINK__49E1024D" ON "ACT_HI_ENTITYLINK" ("ROOT_SCOPE_ID_", "ROOT_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_HI_ENTITYLINK__04F2133B" ON "ACT_HI_ENTITYLINK" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_HI_IDENTITYLIN_F26F5561" ON "ACT_HI_IDENTITYLINK" ("USER_ID_");
CREATE INDEX "IX_ACT_HI_IDENTITYLIN_910D1E2F" ON "ACT_HI_IDENTITYLINK" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_IDENTITYLIN_98960920" ON "ACT_HI_IDENTITYLINK" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_IDENTITYLIN_736305E9" ON "ACT_HI_IDENTITYLINK" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_IDENTITYLIN_0808738A" ON "ACT_HI_IDENTITYLINK" ("TASK_ID_");
CREATE INDEX "IX_ACT_HI_IDENTITYLIN_18D4AC98" ON "ACT_HI_IDENTITYLINK" ("PROC_INST_ID_");
CREATE UNIQUE INDEX "UQ_ACT_HI_PROCINST_PR_324566A6" ON "ACT_HI_PROCINST" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_HI_PROCINST_AC_5CA8751A" ON "ACT_HI_PROCINST" ("END_TIME_");
CREATE INDEX "IX_ACT_HI_PROCINST_AC_6AA5180F" ON "ACT_HI_PROCINST" ("BUSINESS_KEY_");
CREATE INDEX "IX_ACT_HI_PROCINST_AC_D0635A48" ON "ACT_HI_PROCINST" ("SUPER_PROCESS_INSTANCE_ID_");
CREATE INDEX "IX_ACT_HI_TASKINST_AC_A8130F86" ON "ACT_HI_TASKINST" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_TASKINST_AC_C45A4684" ON "ACT_HI_TASKINST" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_TASKINST_AC_B733A3B9" ON "ACT_HI_TASKINST" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_TASKINST_AC_0C4A8060" ON "ACT_HI_TASKINST" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_HI_TSK_LOG_ACT_BD9CDED6" ON "ACT_HI_TSK_LOG" ("TASK_ID_");
CREATE INDEX "IX_ACT_HI_VARINST_ACT_79993A00" ON "ACT_HI_VARINST" ("NAME_", "VAR_TYPE_");
CREATE INDEX "IX_ACT_HI_VARINST_ACT_82E3B912" ON "ACT_HI_VARINST" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_VARINST_ACT_D755AE2F" ON "ACT_HI_VARINST" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_HI_VARINST_ACT_6E67C74D" ON "ACT_HI_VARINST" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_HI_VARINST_ACT_500FC592" ON "ACT_HI_VARINST" ("TASK_ID_");
CREATE INDEX "IX_ACT_HI_VARINST_ACT_DAD811FA" ON "ACT_HI_VARINST" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_ID_MEMBERSHIP__F2804A82" ON "ACT_ID_MEMBERSHIP" ("GROUP_ID_");
CREATE UNIQUE INDEX "UQ_ACT_ID_PRIV_ACT_UN_8798E938" ON "ACT_ID_PRIV" ("NAME_");
CREATE INDEX "IX_ACT_ID_PRIV_MAPPIN_161A8D24" ON "ACT_ID_PRIV_MAPPING" ("PRIV_ID_");
CREATE INDEX "IX_ACT_ID_PRIV_MAPPIN_74689E92" ON "ACT_ID_PRIV_MAPPING" ("USER_ID_");
CREATE INDEX "IX_ACT_ID_PRIV_MAPPIN_F9A6D4F2" ON "ACT_ID_PRIV_MAPPING" ("GROUP_ID_");
CREATE UNIQUE INDEX "UQ_ACT_PROCDEF_INFO_A_8A6B8AC4" ON "ACT_PROCDEF_INFO" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_PROCDEF_INFO_A_102BF73D" ON "ACT_PROCDEF_INFO" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_PROCDEF_INFO_A_C3FF3275" ON "ACT_PROCDEF_INFO" ("INFO_JSON_ID_");
CREATE INDEX "IX_ACT_RE_MODEL_ACT_F_1882CF59" ON "ACT_RE_MODEL" ("EDITOR_SOURCE_VALUE_ID_");
CREATE INDEX "IX_ACT_RE_MODEL_ACT_F_93BDFBD3" ON "ACT_RE_MODEL" ("EDITOR_SOURCE_EXTRA_VALUE_ID_");
CREATE INDEX "IX_ACT_RE_MODEL_ACT_F_6B0C49BA" ON "ACT_RE_MODEL" ("DEPLOYMENT_ID_");
CREATE UNIQUE INDEX "UQ_ACT_RE_PROCDEF_ACT_42CE2678" ON "ACT_RE_PROCDEF" ("KEY_", "VERSION_", "DERIVED_VERSION_", "TENANT_ID_");
CREATE INDEX "IX_ACT_RU_ACTINST_ACT_5E384778" ON "ACT_RU_ACTINST" ("START_TIME_");
CREATE INDEX "IX_ACT_RU_ACTINST_ACT_004AA7A9" ON "ACT_RU_ACTINST" ("END_TIME_");
CREATE INDEX "IX_ACT_RU_ACTINST_ACT_024A432B" ON "ACT_RU_ACTINST" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_RU_ACTINST_ACT_8F96A6BC" ON "ACT_RU_ACTINST" ("PROC_INST_ID_", "ACT_ID_");
CREATE INDEX "IX_ACT_RU_ACTINST_ACT_B182C2F7" ON "ACT_RU_ACTINST" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_ACTINST_ACT_C0329022" ON "ACT_RU_ACTINST" ("EXECUTION_ID_", "ACT_ID_");
CREATE INDEX "IX_ACT_RU_ACTINST_ACT_C1F8AD38" ON "ACT_RU_ACTINST" ("TASK_ID_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__0302EC30" ON "ACT_RU_DEADLETTER_JOB" ("EXCEPTION_STACK_ID_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__3ED00749" ON "ACT_RU_DEADLETTER_JOB" ("CUSTOM_VALUES_ID_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__D9F498BF" ON "ACT_RU_DEADLETTER_JOB" ("CORRELATION_ID_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__BFC40F51" ON "ACT_RU_DEADLETTER_JOB" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__9CBBFD1F" ON "ACT_RU_DEADLETTER_JOB" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__FA3FF0C6" ON "ACT_RU_DEADLETTER_JOB" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__49E8C9E2" ON "ACT_RU_DEADLETTER_JOB" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__247BE760" ON "ACT_RU_DEADLETTER_JOB" ("PROCESS_INSTANCE_ID_");
CREATE INDEX "IX_ACT_RU_DEADLETTER__7F91116D" ON "ACT_RU_DEADLETTER_JOB" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_RU_ENTITYLINK__E6856B8F" ON "ACT_RU_ENTITYLINK" ("SCOPE_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_RU_ENTITYLINK__0B3CB5F7" ON "ACT_RU_ENTITYLINK" ("REF_SCOPE_ID_", "REF_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_RU_ENTITYLINK__9ACF7B57" ON "ACT_RU_ENTITYLINK" ("ROOT_SCOPE_ID_", "ROOT_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_RU_ENTITYLINK__018C4C4E" ON "ACT_RU_ENTITYLINK" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX "IX_ACT_RU_EVENT_SUBSC_CE60EF1B" ON "ACT_RU_EVENT_SUBSCR" ("CONFIGURATION_");
CREATE INDEX "IX_ACT_RU_EVENT_SUBSC_9472BE09" ON "ACT_RU_EVENT_SUBSCR" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_EVENT_SUBSC_095F3EAE" ON "ACT_RU_EVENT_SUBSCR" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_EVENT_SUBSC_DAA6AEC0" ON "ACT_RU_EVENT_SUBSCR" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_RU_EXECUTION_A_DB7A47A8" ON "ACT_RU_EXECUTION" ("BUSINESS_KEY_");
CREATE INDEX "IX_ACT_RU_EXECUTION_A_219E1C6E" ON "ACT_RU_EXECUTION" ("ROOT_PROC_INST_ID_");
CREATE INDEX "IX_ACT_RU_EXECUTION_A_F71ADEEF" ON "ACT_RU_EXECUTION" ("REFERENCE_ID_");
CREATE INDEX "IX_ACT_RU_EXECUTION_A_265D5354" ON "ACT_RU_EXECUTION" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_RU_EXECUTION_A_C42E612C" ON "ACT_RU_EXECUTION" ("PARENT_ID_");
CREATE INDEX "IX_ACT_RU_EXECUTION_A_66130C58" ON "ACT_RU_EXECUTION" ("SUPER_EXEC_");
CREATE INDEX "IX_ACT_RU_EXECUTION_A_EBB7F7B9" ON "ACT_RU_EXECUTION" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_RU_EXTERNAL_JO_EEA9404D" ON "ACT_RU_EXTERNAL_JOB" ("EXCEPTION_STACK_ID_");
CREATE INDEX "IX_ACT_RU_EXTERNAL_JO_7476E10F" ON "ACT_RU_EXTERNAL_JOB" ("CUSTOM_VALUES_ID_");
CREATE INDEX "IX_ACT_RU_EXTERNAL_JO_459C4349" ON "ACT_RU_EXTERNAL_JOB" ("CORRELATION_ID_");
CREATE INDEX "IX_ACT_RU_EXTERNAL_JO_D4B7EB99" ON "ACT_RU_EXTERNAL_JOB" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_EXTERNAL_JO_41D6EEBC" ON "ACT_RU_EXTERNAL_JOB" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_EXTERNAL_JO_E5A7FC77" ON "ACT_RU_EXTERNAL_JOB" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_57D9E108" ON "ACT_RU_IDENTITYLINK" ("USER_ID_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_459DDF73" ON "ACT_RU_IDENTITYLINK" ("GROUP_ID_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_A8EAB1B5" ON "ACT_RU_IDENTITYLINK" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_B3976935" ON "ACT_RU_IDENTITYLINK" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_53B7EAE1" ON "ACT_RU_IDENTITYLINK" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_85D1C529" ON "ACT_RU_IDENTITYLINK" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_2373A011" ON "ACT_RU_IDENTITYLINK" ("TASK_ID_");
CREATE INDEX "IX_ACT_RU_IDENTITYLIN_1009ABCD" ON "ACT_RU_IDENTITYLINK" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_IDX_F8B5C7DD" ON "ACT_RU_JOB" ("EXCEPTION_STACK_ID_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_IDX_F9942A7F" ON "ACT_RU_JOB" ("CUSTOM_VALUES_ID_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_IDX_9C00BBA6" ON "ACT_RU_JOB" ("CORRELATION_ID_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_IDX_445F2028" ON "ACT_RU_JOB" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_IDX_C9EFA42E" ON "ACT_RU_JOB" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_IDX_2A749B1E" ON "ACT_RU_JOB" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_FK__AD00B151" ON "ACT_RU_JOB" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_FK__C4E8D222" ON "ACT_RU_JOB" ("PROCESS_INSTANCE_ID_");
CREATE INDEX "IX_ACT_RU_JOB_ACT_FK__7650D139" ON "ACT_RU_JOB" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_F544A6EC" ON "ACT_RU_SUSPENDED_JOB" ("EXCEPTION_STACK_ID_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_0B4E9A74" ON "ACT_RU_SUSPENDED_JOB" ("CUSTOM_VALUES_ID_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_E3BB755F" ON "ACT_RU_SUSPENDED_JOB" ("CORRELATION_ID_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_15A4E36F" ON "ACT_RU_SUSPENDED_JOB" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_3C72F79F" ON "ACT_RU_SUSPENDED_JOB" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_7A56E4C5" ON "ACT_RU_SUSPENDED_JOB" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_56E95245" ON "ACT_RU_SUSPENDED_JOB" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_A592EBFE" ON "ACT_RU_SUSPENDED_JOB" ("PROCESS_INSTANCE_ID_");
CREATE INDEX "IX_ACT_RU_SUSPENDED_J_AC65AB01" ON "ACT_RU_SUSPENDED_JOB" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_RU_TASK_ACT_ID_1C5FD04F" ON "ACT_RU_TASK" ("CREATE_TIME_");
CREATE INDEX "IX_ACT_RU_TASK_ACT_ID_13623136" ON "ACT_RU_TASK" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_TASK_ACT_ID_751E3FB0" ON "ACT_RU_TASK" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_TASK_ACT_ID_CAC761F8" ON "ACT_RU_TASK" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_TASK_ACT_FK_TASK_EXE" ON "ACT_RU_TASK" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_TASK_ACT_FK_E85B3DEA" ON "ACT_RU_TASK" ("PROC_INST_ID_");
CREATE INDEX "IX_ACT_RU_TASK_ACT_FK_6422E0BD" ON "ACT_RU_TASK" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_0E6EF1F8" ON "ACT_RU_TIMER_JOB" ("EXCEPTION_STACK_ID_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_C754AE65" ON "ACT_RU_TIMER_JOB" ("CUSTOM_VALUES_ID_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_5FC2342E" ON "ACT_RU_TIMER_JOB" ("CORRELATION_ID_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_7559DD30" ON "ACT_RU_TIMER_JOB" ("DUEDATE_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_E5ED05F6" ON "ACT_RU_TIMER_JOB" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_55ED4E60" ON "ACT_RU_TIMER_JOB" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_4FCB6004" ON "ACT_RU_TIMER_JOB" ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_468FC62C" ON "ACT_RU_TIMER_JOB" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_FFECA628" ON "ACT_RU_TIMER_JOB" ("PROCESS_INSTANCE_ID_");
CREATE INDEX "IX_ACT_RU_TIMER_JOB_A_7CD2D2C7" ON "ACT_RU_TIMER_JOB" ("PROC_DEF_ID_");
CREATE INDEX "IX_ACT_RU_VARIABLE_AC_1A29C9C1" ON "ACT_RU_VARIABLE" ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_VARIABLE_AC_29993E3C" ON "ACT_RU_VARIABLE" ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX "IX_ACT_RU_VARIABLE_AC_02638374" ON "ACT_RU_VARIABLE" ("BYTEARRAY_ID_");
CREATE INDEX "IX_ACT_RU_VARIABLE_AC_64BB4342" ON "ACT_RU_VARIABLE" ("TASK_ID_");
CREATE INDEX "IX_ACT_RU_VARIABLE_AC_7F24F382" ON "ACT_RU_VARIABLE" ("EXECUTION_ID_");
CREATE INDEX "IX_ACT_RU_VARIABLE_AC_FB6D616E" ON "ACT_RU_VARIABLE" ("PROC_INST_ID_");
CREATE INDEX "IX_AUTH_LOGIN_THROTTL_A5D6639A" ON "AUTH_LOGIN_THROTTLE" ("UPDATE_TIME");
CREATE UNIQUE INDEX "UQ_AUTH_REFRESH_SESSI_E043D577" ON "AUTH_REFRESH_SESSION" ("REFRESH_TOKEN_HASH");
CREATE INDEX "IX_AUTH_REFRESH_SESSI_644D477C" ON "AUTH_REFRESH_SESSION" ("USER_ID", "REVOKED_AT");
CREATE INDEX "IX_AUTH_REFRESH_SESSI_0B5C3D7F" ON "AUTH_REFRESH_SESSION" ("IDLE_EXPIRES_AT", "ABSOLUTE_EXPIRES_AT");
CREATE UNIQUE INDEX "UQ_CONFIG_ASSET_BASEL_B9922E29" ON "CONFIG_ASSET_BASELINE" ("ASSET_TYPE", "BUSINESS_KEY", "SCOPE_KEY");
CREATE INDEX "IX_CONFIG_ASSET_BASEL_DEEF8DBF" ON "CONFIG_ASSET_BASELINE" ("IMPORT_PACKAGE_ID");
CREATE UNIQUE INDEX "UQ_CONFIG_ENVIRONMENT_E51AB449" ON "CONFIG_ENVIRONMENT_MAPPING" ("SOURCE_TYPE", "SOURCE_KEY");
CREATE UNIQUE INDEX "UQ_CONFIG_EXPORT_PACK_11E84D40" ON "CONFIG_EXPORT_PACKAGE" ("PACKAGE_NO");
CREATE INDEX "IX_CONFIG_EXPORT_PACK_9B3BCBA4" ON "CONFIG_EXPORT_PACKAGE" ("MIGRATION_TAG");
CREATE INDEX "IX_CONFIG_EXPORT_PACK_8510675B" ON "CONFIG_EXPORT_PACKAGE" ("CREATE_TIME");
CREATE UNIQUE INDEX "UQ_CONFIG_EXPORT_PACK_D381ECCB" ON "CONFIG_EXPORT_PACKAGE_ITEM" ("PACKAGE_ID", "ASSET_ID");
CREATE INDEX "IX_CONFIG_EXPORT_PACK_782FAF20" ON "CONFIG_EXPORT_PACKAGE_ITEM" ("PACKAGE_ID");
CREATE UNIQUE INDEX "UQ_CONFIG_IMPORT_ITEM_97C06CC8" ON "CONFIG_IMPORT_ITEM" ("IMPORT_PACKAGE_ID", "ASSET_TYPE", "BUSINESS_KEY", "SOURCE_VERSION");
CREATE INDEX "IX_CONFIG_IMPORT_ITEM_BCE1734E" ON "CONFIG_IMPORT_ITEM" ("IMPORT_PACKAGE_ID");
CREATE INDEX "IX_CONFIG_IMPORT_ITEM_FDDCD01F" ON "CONFIG_IMPORT_ITEM" ("COMPARISON_STATUS", "PUBLISH_STATUS");
CREATE UNIQUE INDEX "UQ_CONFIG_IMPORT_PACK_CD64E212" ON "CONFIG_IMPORT_PACKAGE" ("CHECKSUM");
CREATE INDEX "IX_CONFIG_IMPORT_PACK_F0E09D37" ON "CONFIG_IMPORT_PACKAGE" ("MIGRATION_TAG");
CREATE INDEX "IX_CONFIG_IMPORT_PACK_C8A4CA12" ON "CONFIG_IMPORT_PACKAGE" ("STATUS", "IMPORTED_AT");
CREATE UNIQUE INDEX "UQ_CONFIG_MIGRATION_A_1912FF0E" ON "CONFIG_MIGRATION_ASSET" ("ASSET_TYPE", "SOURCE_HISTORY_ID");
CREATE INDEX "IX_CONFIG_MIGRATION_A_578F3602" ON "CONFIG_MIGRATION_ASSET" ("ASSET_TYPE", "BUSINESS_KEY", "SOURCE_VERSION");
CREATE INDEX "IX_CONFIG_MIGRATION_A_F8508DC8" ON "CONFIG_MIGRATION_ASSET" ("MIGRATION_TAG");
CREATE INDEX "IX_CONFIG_MIGRATION_A_7676A496" ON "CONFIG_MIGRATION_ASSET" ("MARK_FOR_EXPORT", "EXPORT_STATUS", "SNAPSHOT_COMPLETENESS");
CREATE UNIQUE INDEX "UQ_CONFIG_MIGRATION_A_553637FC" ON "CONFIG_MIGRATION_ASSET_DEPENDENCY" ("ASSET_ID", "DEPENDENCY_TYPE", "DEPENDENCY_KEY");
CREATE INDEX "IX_CONFIG_MIGRATION_A_B11F8305" ON "CONFIG_MIGRATION_ASSET_DEPENDENCY" ("DEPENDENCY_TYPE", "DEPENDENCY_KEY");
CREATE INDEX "IX_CONFIG_MIGRATION_A_84402DDF" ON "CONFIG_MIGRATION_ASSET_DEPENDENCY" ("SOURCE_ASSET_TYPE", "SOURCE_BUSINESS_KEY", "SOURCE_VERSION");
CREATE INDEX "IX_CONFIG_MIGRATION_A_012A96C0" ON "CONFIG_MIGRATION_ASSET_DEPENDENCY" ("PARSE_STATUS", "EXTRACTED_AT");
CREATE UNIQUE INDEX "UQ_EMBED_APPLICATION__412BD72A" ON "EMBED_APPLICATION_GRANT" ("APPLICATION_ID", "VIEW_ID");
CREATE INDEX "IX_EMBED_APPLICATION__336C49CF" ON "EMBED_APPLICATION_GRANT" ("STATUS", "EXPIRES_AT");
CREATE INDEX "IX_EMBED_APPLICATION__D64774C5" ON "EMBED_APPLICATION_GRANT" ("IDENTITY_PROVIDER_ID", "STATUS");
CREATE INDEX "IX_EMBED_APPLICATION__0984EFE8" ON "EMBED_APPLICATION_GRANT" ("VIEW_ID");
CREATE INDEX "IX_EMBED_ASSERTION_RE_A24E5269" ON "EMBED_ASSERTION_REPLAY" ("EXPIRES_AT");
CREATE UNIQUE INDEX "UQ_EMBED_EXTERNAL_IDE_5B95ED83" ON "EMBED_EXTERNAL_IDENTITY_BINDING" ("APPLICATION_ID", "IDENTITY_PROVIDER_ID", "SUBJECT_DIGEST");
CREATE INDEX "IX_EMBED_EXTERNAL_IDE_EBC8E825" ON "EMBED_EXTERNAL_IDENTITY_BINDING" ("FLOW_USER_ID", "STATUS");
CREATE INDEX "IX_EMBED_EXTERNAL_IDE_0B79AAB1" ON "EMBED_EXTERNAL_IDENTITY_BINDING" ("STATUS", "EXPIRES_AT");
CREATE INDEX "IX_EMBED_EXTERNAL_IDE_D87F5C79" ON "EMBED_EXTERNAL_IDENTITY_BINDING" ("IDENTITY_PROVIDER_ID");
CREATE UNIQUE INDEX "UQ_EMBED_IDENTITY_PRO_E8AD7F1B" ON "EMBED_IDENTITY_PROVIDER" ("TYPE", "ISSUER_UNIQUENESS_KEY", "SUBJECT_NAMESPACE");
CREATE INDEX "IX_EMBED_IDENTITY_PRO_6297979D" ON "EMBED_IDENTITY_PROVIDER" ("STATUS", "UPDATE_TIME");
CREATE UNIQUE INDEX "UQ_EMBED_LAUNCH_UK_EM_1000BB22" ON "EMBED_LAUNCH" ("LAUNCH_CODE_DIGEST");
CREATE UNIQUE INDEX "UQ_EMBED_LAUNCH_UK_EM_D94B79B4" ON "EMBED_LAUNCH" ("CONSUMED_SESSION_ID");
CREATE INDEX "IX_EMBED_LAUNCH_IDX_E_16166F39" ON "EMBED_LAUNCH" ("STATUS", "EXPIRES_AT");
CREATE INDEX "IX_EMBED_LAUNCH_IDX_E_C8E13261" ON "EMBED_LAUNCH" ("STATUS", "UPDATE_TIME", "ID");
CREATE INDEX "IX_EMBED_LAUNCH_IDX_E_B909EA31" ON "EMBED_LAUNCH" ("APPLICATION_ID", "VIEW_ID", "CREATE_TIME");
CREATE INDEX "IX_EMBED_LAUNCH_IDX_E_3957638F" ON "EMBED_LAUNCH" ("IDENTITY_BINDING_ID", "STATUS");
CREATE INDEX "IX_EMBED_LAUNCH_FK_EM_E85B031D" ON "EMBED_LAUNCH" ("GRANT_ID");
CREATE INDEX "IX_EMBED_LAUNCH_FK_EM_91A17E89" ON "EMBED_LAUNCH" ("VIEW_ID");
CREATE INDEX "IX_EMBED_LAUNCH_FK_EM_F68CE3DB" ON "EMBED_LAUNCH" ("VIEW_RELEASE_ID");
CREATE INDEX "IX_EMBED_LAUNCH_FK_EM_DAEF2DED" ON "EMBED_LAUNCH" ("IDENTITY_PROVIDER_ID");
CREATE INDEX "IX_EMBED_LAUNCH_FK_EM_33B8A6F6" ON "EMBED_LAUNCH" ("FLOW_USER_ID");
CREATE UNIQUE INDEX "UQ_EMBED_OPERATION_RE_1BF0140D" ON "EMBED_OPERATION_RECEIPT" ("IDEMPOTENCY_RECORD_ID");
CREATE INDEX "IX_EMBED_OPERATION_RE_F890C52D" ON "EMBED_OPERATION_RECEIPT" ("CREATE_TIME", "ID");
CREATE INDEX "IX_EMBED_OPERATION_RE_FA54E04B" ON "EMBED_OPERATION_RECEIPT" ("APPLICATION_ID", "OPERATION", "CREATE_TIME");
CREATE INDEX "IX_EMBED_OPERATION_RE_23FF5AA1" ON "EMBED_OPERATION_RECEIPT" ("TARGET_TYPE", "TARGET_ID", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_EMBED_SESSION_UK_E_FE4388C9" ON "EMBED_SESSION" ("SESSION_TOKEN_DIGEST");
CREATE UNIQUE INDEX "UQ_EMBED_SESSION_UK_E_66361938" ON "EMBED_SESSION" ("LAUNCH_ID");
CREATE INDEX "IX_EMBED_SESSION_IDX__AA97B824" ON "EMBED_SESSION" ("STATUS", "IDLE_EXPIRES_AT", "ABSOLUTE_EXPIRES_AT");
CREATE INDEX "IX_EMBED_SESSION_IDX__D02884B5" ON "EMBED_SESSION" ("STATUS", "SLOT_RELEASED", "GRANT_ID", "FLOW_USER_ID");
CREATE INDEX "IX_EMBED_SESSION_IDX__41BA5ACC" ON "EMBED_SESSION" ("STATUS", "SLOT_RELEASED_AT", "ID");
CREATE INDEX "IX_EMBED_SESSION_IDX__079B1E4F" ON "EMBED_SESSION" ("APPLICATION_ID", "STATUS");
CREATE INDEX "IX_EMBED_SESSION_IDX__B8630367" ON "EMBED_SESSION" ("VIEW_ID", "STATUS");
CREATE INDEX "IX_EMBED_SESSION_IDX__3E0FC4DB" ON "EMBED_SESSION" ("FLOW_USER_ID", "STATUS");
CREATE INDEX "IX_EMBED_SESSION_FK_E_AD76E387" ON "EMBED_SESSION" ("GRANT_ID");
CREATE INDEX "IX_EMBED_SESSION_FK_E_652A36DE" ON "EMBED_SESSION" ("VIEW_RELEASE_ID");
CREATE INDEX "IX_EMBED_SESSION_FK_E_34FDCACA" ON "EMBED_SESSION" ("IDENTITY_PROVIDER_ID");
CREATE INDEX "IX_EMBED_SESSION_FK_E_3E3DB150" ON "EMBED_SESSION" ("IDENTITY_BINDING_ID");
CREATE INDEX "IX_EMBED_SESSION_COUN_DE91BB3C" ON "EMBED_SESSION_COUNTER" ("FLOW_USER_ID");
CREATE UNIQUE INDEX "UQ_EMBED_VIEW_UK_EMBE_3522A08E" ON "EMBED_VIEW" ("VIEW_KEY");
CREATE INDEX "IX_EMBED_VIEW_IDX_EMB_E70860A5" ON "EMBED_VIEW" ("STATUS", "UPDATE_TIME");
CREATE UNIQUE INDEX "UQ_EMBED_VIEW_RELEASE_F42CB2C3" ON "EMBED_VIEW_RELEASE" ("VIEW_ID", "REVISION");
CREATE INDEX "IX_EMBED_VIEW_RELEASE_11321CA4" ON "EMBED_VIEW_RELEASE" ("VIEW_ID", "PUBLISHED_AT");
CREATE UNIQUE INDEX "UQ_ENTITY_CODE_RULE_U_F5389A10" ON "ENTITY_CODE_RULE" ("ENTITY_CODE");
CREATE UNIQUE INDEX "UQ_ENTITY_DEFINITION__127824ED" ON "ENTITY_DEFINITION" ("ENTITY_CODE");
CREATE INDEX "IX_ENTITY_DEFINITION__904E810C" ON "ENTITY_DEFINITION" ("ENTITY_CODE");
CREATE INDEX "IX_ENTITY_DEFINITION__2B21A936" ON "ENTITY_DEFINITION" ("STATUS");
CREATE INDEX "IX_ENTITY_DEFINITION__4820E214" ON "ENTITY_DEFINITION" ("LIFECYCLE_MODE");
CREATE INDEX "IX_ENTITY_DEFINITION__E1C5C9E2" ON "ENTITY_DEFINITION" ("STORAGE_MODE");
CREATE INDEX "IX_ENTITY_DEFINITION__D2FEF9CD" ON "ENTITY_DEFINITION" ("ACTIVE_PROCESS_DEFINITION_KEY");
CREATE UNIQUE INDEX "UQ_ENTITY_FIELD_UK_EN_2CD3AF37" ON "ENTITY_FIELD" ("ENTITY_ID", "FIELD_CODE");
CREATE INDEX "IX_ENTITY_FIELD_IDX_ENTITY_ID" ON "ENTITY_FIELD" ("ENTITY_ID");
CREATE INDEX "IX_ENTITY_FIELD_IDX_FIELD_CODE" ON "ENTITY_FIELD" ("FIELD_CODE");
CREATE UNIQUE INDEX "UQ_ENTITY_FIELD_FILE__97F58568" ON "ENTITY_FIELD_FILE_ITEM" ("FIELD_ID", "ITEM_KEY");
CREATE INDEX "IX_ENTITY_FIELD_FILE__920B355F" ON "ENTITY_FIELD_FILE_ITEM" ("FIELD_ID");
CREATE INDEX "IX_ENTITY_FIELD_FILE__2A159F07" ON "ENTITY_FIELD_FILE_ITEM" ("SORT_ORDER");
CREATE UNIQUE INDEX "UQ_ENTITY_FIELD_OPTIO_C4A72EA3" ON "ENTITY_FIELD_OPTION" ("FIELD_ID", "OPTION_VALUE");
CREATE INDEX "IX_ENTITY_FIELD_OPTIO_50AB82AF" ON "ENTITY_FIELD_OPTION" ("FIELD_ID", "SORT_ORDER");
CREATE UNIQUE INDEX "UQ_ENTITY_FORM_UK_ENT_D5C8CEDE" ON "ENTITY_FORM" ("ENTITY_ID", "FORM_KEY");
CREATE INDEX "IX_ENTITY_FORM_IDX_ENTITY_ID" ON "ENTITY_FORM" ("ENTITY_ID");
CREATE INDEX "IX_ENTITY_FORM_IDX_STATUS" ON "ENTITY_FORM" ("STATUS");
CREATE INDEX "IX_ENTITY_FORM_IDX_DELETED" ON "ENTITY_FORM" ("DELETED");
CREATE UNIQUE INDEX "UQ_ENTITY_FORM_NODE_U_F2526C1F" ON "ENTITY_FORM_NODE" ("FORM_ID", "ACTIVE_NODE_KEY");
CREATE INDEX "IX_ENTITY_FORM_NODE_I_AF2AA3F3" ON "ENTITY_FORM_NODE" ("FORM_ID", "PARENT_ID", "ORDER_KEY", "DELETED");
CREATE INDEX "IX_ENTITY_FORM_NODE_I_B0189FE3" ON "ENTITY_FORM_NODE" ("FORM_ID", "BINDING_TYPE", "BINDING_REF", "DELETED");
CREATE INDEX "IX_ENTITY_FORM_NODE_I_94A1432C" ON "ENTITY_FORM_NODE" ("FORM_ID", "NODE_KEY");
CREATE UNIQUE INDEX "UQ_ENTITY_FORM_UNIQUE_93DD1BBA" ON "ENTITY_FORM_UNIQUE_CLAIM" ("CONSTRAINT_KEY", "RECORD_ID");
CREATE INDEX "IX_ENTITY_FORM_UNIQUE_73015A3E" ON "ENTITY_FORM_UNIQUE_CLAIM" ("ENTITY_CODE", "RECORD_ID");
CREATE INDEX "IX_ENTITY_FORM_UNIQUE_AE07397E" ON "ENTITY_FORM_UNIQUE_CLAIM" ("FORM_ID", "RULE_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_LIST_ACTION_7EAF09DA" ON "ENTITY_LIST_ACTION" ("LIST_CONFIG_ID", "POSITION", "BUTTON_KEY", "DELETED");
CREATE INDEX "IX_ENTITY_LIST_ACTION_87CA6CA9" ON "ENTITY_LIST_ACTION" ("LIST_CONFIG_ID", "POSITION", "ENABLED", "DELETED");
CREATE UNIQUE INDEX "UQ_ENTITY_LIST_CONFIG_C4B2199E" ON "ENTITY_LIST_CONFIG" ("ENTITY_ID", "LIST_KEY", "DELETED");
CREATE INDEX "IX_ENTITY_LIST_CONFIG_50909479" ON "ENTITY_LIST_CONFIG" ("ENTITY_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_LIST_FIELD__1CF43AF7" ON "ENTITY_LIST_FIELD" ("LIST_CONFIG_ID", "FIELD_ID", "DELETED");
CREATE INDEX "IX_ENTITY_LIST_FIELD__2C0C4222" ON "ENTITY_LIST_FIELD" ("LIST_CONFIG_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_LIST_SCENE__B4DFD3CA" ON "ENTITY_LIST_SCENE" ("LIST_CONFIG_ID", "SCENE_CODE");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__D27C3970" ON "ENTITY_LIST_SCOPE_AUDIT_LOG" ("ENTITY_CODE", "LIST_KEY", "CREATE_TIME");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__506414B1" ON "ENTITY_LIST_SCOPE_AUDIT_LOG" ("USER_ID", "CREATE_TIME");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__E1614AA1" ON "ENTITY_LIST_SCOPE_BINDING" ("ENTITY_CODE", "LIST_KEY", "ENABLED", "DELETED");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__D4FFA6DE" ON "ENTITY_LIST_SCOPE_BINDING" ("POLICY_ID");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__7EF40D66" ON "ENTITY_LIST_SCOPE_DELEGATION" ("TO_USER_ID", "ENTITY_CODE", "ENABLED", "DELETED");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__DF3504A9" ON "ENTITY_LIST_SCOPE_DELEGATION" ("FROM_USER_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_LIST_SCOPE__B2C82952" ON "ENTITY_LIST_SCOPE_POLICY" ("ENTITY_CODE", "POLICY_KEY", "DELETED");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__AD6A6751" ON "ENTITY_LIST_SCOPE_POLICY" ("ENTITY_CODE", "STATUS", "ENABLED", "DELETED");
CREATE UNIQUE INDEX "UQ_ENTITY_LIST_SCOPE__009F9F46" ON "ENTITY_LIST_SCOPE_RELEASE" ("ENTITY_CODE", "VERSION");
CREATE INDEX "IX_ENTITY_LIST_SCOPE__B5C36DC1" ON "ENTITY_LIST_SCOPE_RELEASE" ("ENTITY_CODE", "STATUS");
CREATE UNIQUE INDEX "UQ_ENTITY_MUTATION_RE_71E929E2" ON "ENTITY_MUTATION_RECEIPT" ("IDEMPOTENCY_KEY");
CREATE INDEX "IX_ENTITY_MUTATION_RE_3A71C245" ON "ENTITY_MUTATION_RECEIPT" ("ENTITY_CODE", "RECORD_ID", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_ENTITY_PROCESS_LIN_E340A5E0" ON "ENTITY_PROCESS_LINK" ("ENTITY_CODE", "ENTITY_RECORD_ID", "GENERATION");
CREATE UNIQUE INDEX "UQ_ENTITY_PROCESS_LIN_00CD7696" ON "ENTITY_PROCESS_LINK" ("REQUEST_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_PROCESS_LIN_BD519BAE" ON "ENTITY_PROCESS_LINK" ("PROCESS_INSTANCE_ID");
CREATE INDEX "IX_ENTITY_PROCESS_LIN_B978857D" ON "ENTITY_PROCESS_LINK" ("STATE", "UPDATE_TIME");
CREATE UNIQUE INDEX "UQ_ENTITY_PUBLISH_HIS_195B03F7" ON "ENTITY_PUBLISH_HISTORY" ("ENTITY_ID", "VERSION");
CREATE INDEX "IX_ENTITY_PUBLISH_HIS_C9103DA5" ON "ENTITY_PUBLISH_HISTORY" ("ENTITY_CODE");
CREATE INDEX "IX_ENTITY_PUBLISH_HIS_6F050498" ON "ENTITY_PUBLISH_HISTORY" ("PUBLISH_TYPE");
CREATE INDEX "IX_ENTITY_PUBLISH_HIS_559A5761" ON "ENTITY_PUBLISH_HISTORY" ("STATUS");
CREATE INDEX "IX_ENTITY_PUBLISH_HIS_44C7C3B9" ON "ENTITY_PUBLISH_HISTORY" ("PUBLISHED_AT");
CREATE UNIQUE INDEX "UQ_ENTITY_RECORD_VERS_D3BE92B0" ON "ENTITY_RECORD_VERSION" ("ENTITY_CODE", "RECORD_ID", "VERSION_NO");
CREATE UNIQUE INDEX "UQ_ENTITY_RECORD_VERS_4056B114" ON "ENTITY_RECORD_VERSION" ("ENTITY_CODE", "RECORD_ID", "IDEMPOTENCY_KEY");
CREATE INDEX "IX_ENTITY_RECORD_VERS_68271089" ON "ENTITY_RECORD_VERSION" ("ENTITY_CODE", "RECORD_ID", "CREATE_TIME");
CREATE INDEX "IX_ENTITY_RECORD_VERS_C6AAC288" ON "ENTITY_RECORD_VERSION" ("PROCESS_INSTANCE_ID");
CREATE INDEX "IX_ENTITY_RECORD_VERS_99E69131" ON "ENTITY_RECORD_VERSION" ("CONFIG_RELEASE_ID");
CREATE INDEX "IX_ENTITY_RECORD_VERS_14A0799F" ON "ENTITY_RECORD_VERSION" ("SCHEMA_VERSION", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_ENTITY_RECORD_VERS_7AD04D99" ON "ENTITY_RECORD_VERSION_DATASET" ("VERSION_ID", "NODE_CODE");
CREATE INDEX "IX_ENTITY_RECORD_VERS_461B6D25" ON "ENTITY_RECORD_VERSION_DATASET" ("RELATION_CODE", "ENTITY_CODE");
CREATE UNIQUE INDEX "UQ_ENTITY_RECORD_VERS_D38AACC2" ON "ENTITY_RECORD_VERSION_DATASET_ROW" ("DATASET_ID", "RECORD_ID");
CREATE INDEX "IX_ENTITY_RECORD_VERS_DB4A842F" ON "ENTITY_RECORD_VERSION_DATASET_ROW" ("DATASET_ID", "ROW_ORDER", "RECORD_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_RELATION_UK_270E60C0" ON "ENTITY_RELATION" ("PARENT_ENTITY_ID", "RELATION_CODE");
CREATE UNIQUE INDEX "UQ_ENTITY_RELATION_UK_7A8DC997" ON "ENTITY_RELATION" ("PARENT_ENTITY_ID", "PARENT_FIELD_CODE");
CREATE UNIQUE INDEX "UQ_ENTITY_RELATION_UK_94E46BB7" ON "ENTITY_RELATION" ("PARENT_ENTITY_ID", "DATA_KEY");
CREATE INDEX "IX_ENTITY_RELATION_ID_9ECDBCF6" ON "ENTITY_RELATION" ("PARENT_ENTITY_ID", "ENABLED", "DELETED");
CREATE INDEX "IX_ENTITY_RELATION_ID_EEE92DF5" ON "ENTITY_RELATION" ("PARENT_ENTITY_CODE", "ENABLED", "DELETED");
CREATE INDEX "IX_ENTITY_RELATION_ID_733E6D6F" ON "ENTITY_RELATION" ("CHILD_ENTITY_ID");
CREATE INDEX "IX_ENTITY_RELATION_ID_DCA719AA" ON "ENTITY_RELATION" ("RELATION_CODE");
CREATE INDEX "IX_ENTITY_RELATION_ID_0A3C585E" ON "ENTITY_RELATION" ("CHILD_ENTITY_ID", "CHILD_REF_FIELD_CODE");
CREATE UNIQUE INDEX "UQ_ENTITY_SCHEMA_OPER_4A30650B" ON "ENTITY_SCHEMA_OPERATION" ("ENTITY_ID", "PLAN_HASH");
CREATE UNIQUE INDEX "UQ_ENTITY_SCHEMA_OPER_A7F24476" ON "ENTITY_SCHEMA_OPERATION" ("IDEMPOTENCY_KEY");
CREATE INDEX "IX_ENTITY_SCHEMA_OPER_2DC313A4" ON "ENTITY_SCHEMA_OPERATION" ("ENTITY_ID", "CREATE_TIME");
CREATE INDEX "IX_ENTITY_SCHEMA_OPER_6882B597" ON "ENTITY_SCHEMA_OPERATION" ("STATUS", "UPDATE_TIME");
CREATE INDEX "IX_ENTITY_SCHEMA_OPER_43A17A86" ON "ENTITY_SCHEMA_OPERATION" ("OPERATION_SOURCE", "SOURCE_REFERENCE_ID");
CREATE INDEX "IX_ENTITY_SCHEMA_OPER_D693EAFE" ON "ENTITY_SCHEMA_OPERATION_EVENT" ("OPERATION_ID", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_ENTITY_STATUS_UK_E_FA089F88" ON "ENTITY_STATUS" ("ENTITY_CODE", "STATUS_CODE", "DELETED");
CREATE INDEX "IX_ENTITY_STATUS_IDX__607C55D2" ON "ENTITY_STATUS" ("ENTITY_CODE");
CREATE INDEX "IX_ENTITY_STATUS_IDX__C09AD5C6" ON "ENTITY_STATUS" ("STATUS_CATEGORY");
CREATE INDEX "IX_ENTITY_UNIQUE_VALU_36D70ED1" ON "ENTITY_UNIQUE_VALUE" ("ENTITY_CODE", "RECORD_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_VERSION_CON_35DBD5F8" ON "ENTITY_VERSION_CONFIG" ("ENTITY_CODE", "DELETED");
CREATE INDEX "IX_ENTITY_VERSION_CON_93D258E3" ON "ENTITY_VERSION_CONFIG" ("ACTIVE_RELEASE_ID");
CREATE UNIQUE INDEX "UQ_ENTITY_VERSION_CON_897D2BF1" ON "ENTITY_VERSION_CONFIG_RELEASE" ("CONFIG_ID", "VERSION");
CREATE UNIQUE INDEX "UQ_FLW_CHANNEL_DEFINI_E7F3DDE4" ON "FLW_CHANNEL_DEFINITION" ("KEY_", "VERSION_", "TENANT_ID_");
CREATE UNIQUE INDEX "UQ_FLW_EVENT_DEFINITI_939A344A" ON "FLW_EVENT_DEFINITION" ("KEY_", "VERSION_", "TENANT_ID_");
CREATE INDEX "IX_FLW_EVENT_RESOURCE_E2BB9A60" ON "FLW_EVENT_RESOURCE" ("DEPLOYMENT_ID_");
CREATE INDEX "IX_FLW_RU_BATCH_PART__21AD3312" ON "FLW_RU_BATCH_PART" ("BATCH_ID_");
CREATE INDEX "IX_INTEGRATION_API_RE_1D6004C7" ON "INTEGRATION_API_REQUEST_LEASE" ("APPLICATION_ID", "EXPIRES_AT");
CREATE INDEX "IX_INTEGRATION_API_RE_951CFD99" ON "INTEGRATION_API_REQUEST_LEASE" ("EXPIRES_AT");
CREATE INDEX "IX_INTEGRATION_API_RE_29E79A64" ON "INTEGRATION_API_REQUEST_LEASE" ("APPLICATION_ID", "SCOPE_KEY", "EXPIRES_AT");
CREATE UNIQUE INDEX "UQ_INTEGRATION_APPLIC_CA1AC163" ON "INTEGRATION_APPLICATION" ("CLIENT_ID");
CREATE INDEX "IX_INTEGRATION_APPLIC_9636DC34" ON "INTEGRATION_APPLICATION" ("OWNER_ORGANIZATION_ID", "STATUS");
CREATE INDEX "IX_INTEGRATION_APPLIC_B1621C98" ON "INTEGRATION_APPLICATION" ("STATUS", "EXPIRES_AT");
CREATE UNIQUE INDEX "UQ_INTEGRATION_APPLIC_9A741D77" ON "INTEGRATION_APPLICATION_CREDENTIAL" ("APPLICATION_ID", "CREDENTIAL_VERSION");
CREATE UNIQUE INDEX "UQ_INTEGRATION_APPLIC_0550178A" ON "INTEGRATION_APPLICATION_CREDENTIAL" ("ACTIVE_APPLICATION_ID");
CREATE INDEX "IX_INTEGRATION_APPLIC_A7864EF3" ON "INTEGRATION_APPLICATION_CREDENTIAL" ("APPLICATION_ID", "STATUS", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_INTEGRATION_IDEMPO_C57F65E3" ON "INTEGRATION_IDEMPOTENCY_RECORD" ("APPLICATION_ID", "OPERATION", "IDEMPOTENCY_KEY");
CREATE INDEX "IX_INTEGRATION_IDEMPO_1B6972F4" ON "INTEGRATION_IDEMPOTENCY_RECORD" ("EXPIRES_AT", "STATUS");
CREATE INDEX "IX_INTEGRATION_IDEMPO_3B23D6E6" ON "INTEGRATION_IDEMPOTENCY_RECORD" ("APPLICATION_ID", "RESOURCE_TYPE", "RESOURCE_ID");
CREATE INDEX "IX_INTEGRATION_RATE_L_18DF6742" ON "INTEGRATION_RATE_LIMIT_BUCKET" ("UPDATE_TIME");
CREATE INDEX "IX_PROCESS_ACTION_IDX_69BB0F5F" ON "PROCESS_ACTION" ("PROCESS_CONFIG_ID");
CREATE INDEX "IX_PROCESS_ACTION_IDX_VERSION" ON "PROCESS_ACTION" ("VERSION_ID");
CREATE INDEX "IX_PROCESS_ACTION_IDX_STATUS" ON "PROCESS_ACTION" ("STATUS");
CREATE INDEX "IX_PROCESS_ACTION_IDX_164013A6" ON "PROCESS_ACTION" ("PROCESS_CONFIG_ID", "SCOPE_TYPE", "ELEMENT_ID", "TRIGGER_TIMING", "STATUS", "DELETED");
CREATE INDEX "IX_PROCESS_ACTION_IDX_24B9E8EE" ON "PROCESS_ACTION" ("VERSION_ID", "SCOPE_TYPE", "ELEMENT_ID", "TRIGGER_TIMING", "STATUS", "DELETED");
CREATE INDEX "IX_PROCESS_ACTION_IDX_1AD9EC41" ON "PROCESS_ACTION" ("ACTION_DEFINITION_ID", "STATUS", "DELETED");
CREATE UNIQUE INDEX "UQ_PROCESS_ACTION_DEF_AA5EE867" ON "PROCESS_ACTION_DEFINITION" ("ACTION_CODE");
CREATE UNIQUE INDEX "UQ_PROCESS_ACTION_DEF_CC0025D1" ON "PROCESS_ACTION_DEFINITION" ("HANDLER_NAME");
CREATE INDEX "IX_PROCESS_ACTION_DEF_8F7DEAD7" ON "PROCESS_ACTION_DEFINITION" ("VISIBILITY_SCOPE", "ENABLED", "DELETED");
CREATE UNIQUE INDEX "UQ_PROCESS_ACTION_DEF_E6EF1FEE" ON "PROCESS_ACTION_DEFINITION_ENTITY" ("ACTION_DEFINITION_ID", "ENTITY_CODE");
CREATE INDEX "IX_PROCESS_ACTION_DEF_AEC9BA2D" ON "PROCESS_ACTION_DEFINITION_ENTITY" ("ENTITY_CODE", "ACTION_DEFINITION_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_ACTION_EXE_B2B87052" ON "PROCESS_ACTION_EXECUTION" ("IDEMPOTENCY_KEY");
CREATE INDEX "IX_PROCESS_ACTION_EXE_D4003D3F" ON "PROCESS_ACTION_EXECUTION" ("STATUS", "NEXT_RETRY_TIME", "CREATE_TIME");
CREATE INDEX "IX_PROCESS_ACTION_EXE_91603AC7" ON "PROCESS_ACTION_EXECUTION" ("PROCESS_INSTANCE_ID", "CREATE_TIME");
CREATE INDEX "IX_PROCESS_ACTION_EXE_011EFC4E" ON "PROCESS_ACTION_EXECUTION" ("ACTION_ID", "CREATE_TIME");
CREATE INDEX "IX_PROCESS_ACTION_EXE_17B36C70" ON "PROCESS_ACTION_EXECUTION" ("ENTITY_CODE", "PROCESS_INSTANCE_ID", "CREATE_TIME");
CREATE INDEX "IX_PROCESS_ACTION_EXE_2D4CB4DA" ON "PROCESS_ACTION_EXECUTION" ("STATUS", "LEASE_UNTIL");
CREATE UNIQUE INDEX "UQ_PROCESS_ASSIGNEE_I_45E5AC0F" ON "PROCESS_ASSIGNEE_INCIDENT" ("OPEN_SLOT");
CREATE INDEX "IX_PROCESS_ASSIGNEE_I_D4A3AC0F" ON "PROCESS_ASSIGNEE_INCIDENT" ("STATUS", "NEXT_RETRY_AT");
CREATE INDEX "IX_PROCESS_ASSIGNEE_I_974EE888" ON "PROCESS_ASSIGNEE_INCIDENT" ("PROCESS_INSTANCE_ID", "CREATE_TIME");
CREATE INDEX "IX_PROCESS_ASSIGNEE_I_2D21F152" ON "PROCESS_ASSIGNEE_INCIDENT" ("RESPONSIBILITY_OWNER", "STATUS");
CREATE UNIQUE INDEX "UQ_PROCESS_ASSIGNEE_I_24BABC26" ON "PROCESS_ASSIGNEE_INCIDENT_ACTION" ("INCIDENT_ID", "REQUEST_ID");
CREATE INDEX "IX_PROCESS_ASSIGNEE_I_7DE1EA54" ON "PROCESS_ASSIGNEE_INCIDENT_ACTION" ("INCIDENT_ID", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_PROCESS_CC_RECORD__6E646782" ON "PROCESS_CC_RECORD" ("UNIQUE_KEY");
CREATE INDEX "IX_PROCESS_CC_RECORD__BABF30C8" ON "PROCESS_CC_RECORD" ("PROCESS_INSTANCE_ID");
CREATE INDEX "IX_PROCESS_CC_RECORD__06DF7E95" ON "PROCESS_CC_RECORD" ("CC_USER_ID", "READ_STATUS");
CREATE INDEX "IX_PROCESS_CC_RECORD__16683A1D" ON "PROCESS_CC_RECORD" ("PROCESS_KEY");
CREATE INDEX "IX_PROCESS_CC_RECORD__5EC4E06B" ON "PROCESS_CC_RECORD" ("DELETED");
CREATE INDEX "IX_PROCESS_CC_RECORD__5B6389D8" ON "PROCESS_CC_RECORD" ("CREATE_TIME");
CREATE INDEX "IX_PROCESS_CC_RECORD__21B443B1" ON "PROCESS_CC_RECORD" ("SOURCE_TASK_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_DEFINITION_131348A0" ON "PROCESS_DEFINITION_CONFIG" ("PROCESS_KEY");
CREATE INDEX "IX_PROCESS_DEFINITION_719DF41D" ON "PROCESS_DEFINITION_CONFIG" ("PROCESS_KEY");
CREATE INDEX "IX_PROCESS_DEFINITION_047E32BF" ON "PROCESS_DEFINITION_CONFIG" ("STATUS");
CREATE INDEX "IX_PROCESS_DEFINITION_C75046B3" ON "PROCESS_DEFINITION_CONFIG" ("CATEGORY");
CREATE UNIQUE INDEX "UQ_PROCESS_ENTITY_STA_1FB78D2A" ON "PROCESS_ENTITY_STATUS_MAPPING" ("PROCESS_CONFIG_ID", "SOURCE_NODE_ID", "TARGET_NODE_ID", "DELETED");
CREATE INDEX "IX_PROCESS_ENTITY_STA_CC101395" ON "PROCESS_ENTITY_STATUS_MAPPING" ("PROCESS_CONFIG_ID");
CREATE INDEX "IX_PROCESS_ENTITY_STA_180CF69A" ON "PROCESS_ENTITY_STATUS_MAPPING" ("PROCESS_KEY");
CREATE INDEX "IX_PROCESS_ENTITY_STA_C7FBD97E" ON "PROCESS_ENTITY_STATUS_MAPPING" ("ENTITY_CODE");
CREATE INDEX "IX_PROCESS_ENTITY_STA_4DA8F2C3" ON "PROCESS_ENTITY_STATUS_MAPPING" ("SOURCE_NODE_ID");
CREATE INDEX "IX_PROCESS_FORM_CONFI_B66FE37D" ON "PROCESS_FORM_CONFIG" ("NODE_CONFIG_ID");
CREATE INDEX "IX_PROCESS_FORM_CONFI_8A926022" ON "PROCESS_FORM_CONFIG" ("FORM_KEY");
CREATE INDEX "IX_PROCESS_FORM_FIELD_7F178836" ON "PROCESS_FORM_FIELD_CONFIG" ("FORM_CONFIG_ID");
CREATE INDEX "IX_PROCESS_FORM_FIELD_6A493E75" ON "PROCESS_FORM_FIELD_CONFIG" ("FIELD_KEY");
CREATE INDEX "IX_PROCESS_NODE_APPRO_241E397D" ON "PROCESS_NODE_APPROVAL" ("PROCESS_CONFIG_ID", "NODE_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_NODE_APPRO_6CF07F51" ON "PROCESS_NODE_APPROVAL_OPTION" ("APPROVAL_CONFIG_ID", "OPTION_VALUE");
CREATE INDEX "IX_PROCESS_NODE_APPRO_AB61785E" ON "PROCESS_NODE_APPROVAL_OPTION" ("APPROVAL_CONFIG_ID", "SORT_ORDER");
CREATE INDEX "IX_PROCESS_NODE_ASSIG_F9B36282" ON "PROCESS_NODE_ASSIGNEE" ("NODE_CONFIG_ID");
CREATE INDEX "IX_PROCESS_NODE_CONFI_D08F49AD" ON "PROCESS_NODE_CONFIG" ("PROCESS_CONFIG_ID");
CREATE INDEX "IX_PROCESS_NODE_CONFI_578EF820" ON "PROCESS_NODE_CONFIG" ("NODE_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_NODE_FORM__D2DA1573" ON "PROCESS_NODE_FORM" ("PROCESS_CONFIG_ID", "NODE_ID");
CREATE INDEX "IX_PROCESS_NODE_FORM__919EA0F7" ON "PROCESS_NODE_FORM" ("PROCESS_CONFIG_ID");
CREATE INDEX "IX_PROCESS_NODE_FORM__AB4A1D9E" ON "PROCESS_NODE_FORM" ("FORM_ID");
CREATE INDEX "IX_PROCESS_OPERATION__4ED6EC23" ON "PROCESS_OPERATION_LOG" ("PROCESS_INSTANCE_ID", "OPERATION_TIME");
CREATE INDEX "IX_PROCESS_OPERATION__7500B962" ON "PROCESS_OPERATION_LOG" ("OPERATOR_ID", "OPERATION_TIME");
CREATE UNIQUE INDEX "UQ_PROCESS_PERSON_RES_4A909D2A" ON "PROCESS_PERSON_RESOLVER_DEFINITION" ("RESOLVER_CODE", "DELETED");
CREATE INDEX "IX_PROCESS_PERSON_RES_84F07CBA" ON "PROCESS_PERSON_RESOLVER_DEFINITION" ("ENABLED", "DELETED");
CREATE UNIQUE INDEX "UQ_PROCESS_STATUS_SYN_3CB765D1" ON "PROCESS_STATUS_SYNC_EVENT" ("PROCESS_INSTANCE_ID", "EVENT_TYPE", "EVENT_SEQUENCE");
CREATE INDEX "IX_PROCESS_STATUS_SYN_3E257ADA" ON "PROCESS_STATUS_SYNC_EVENT" ("ENTITY_CODE", "ENTITY_RECORD_ID", "CREATE_TIME");
CREATE INDEX "IX_PROCESS_STATUS_SYN_E17583B1" ON "PROCESS_STATUS_SYNC_EVENT" ("STATE", "UPDATE_TIME");
CREATE UNIQUE INDEX "UQ_PROCESS_TASK_TASK_ID" ON "PROCESS_TASK" ("TASK_ID");
CREATE INDEX "IX_PROCESS_TASK_IDX_P_7AE744CD" ON "PROCESS_TASK" ("PROCESS_INSTANCE_ID");
CREATE INDEX "IX_PROCESS_TASK_IDX_ASSIGNEE" ON "PROCESS_TASK" ("ASSIGNEE_ID", "STATUS");
CREATE INDEX "IX_PROCESS_TASK_IDX_STATUS" ON "PROCESS_TASK" ("STATUS");
CREATE INDEX "IX_PROCESS_TASK_IDX_B_4D536D0D" ON "PROCESS_TASK" ("BUSINESS_KEY");
CREATE INDEX "IX_PROCESS_TASK_IDX_P_78ACCA61" ON "PROCESS_TASK" ("SLA_STATUS", "DUE_TIME");
CREATE INDEX "IX_PROCESS_TASK_IDX_T_695DC88E" ON "PROCESS_TASK" ("ASSIGNEE_ID", "STATUS", "DELETED", "END_TIME", "ID");
CREATE INDEX "IX_PROCESS_TASK_IDX_T_DFDF131A" ON "PROCESS_TASK" ("STATUS", "DELETED", "CREATE_TIME", "ID");
CREATE INDEX "IX_PROCESS_TASK_IDX_T_8C25C184" ON "PROCESS_TASK" ("ENTITY_CODE", "ENTITY_DATA_ID", "ID");
CREATE INDEX "IX_PROCESS_TASK_ADD_S_176043A8" ON "PROCESS_TASK_ADD_SIGN" ("SOURCE_TASK_ID", "STATUS");
CREATE INDEX "IX_PROCESS_TASK_ADD_S_3D387698" ON "PROCESS_TASK_ADD_SIGN" ("PROCESS_INSTANCE_ID", "STATUS");
CREATE UNIQUE INDEX "UQ_PROCESS_TASK_ADD_S_02F3077D" ON "PROCESS_TASK_ADD_SIGN_USER" ("ADD_SIGN_ID", "USER_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_TASK_ADD_S_7F1DFFE5" ON "PROCESS_TASK_ADD_SIGN_USER" ("GENERATED_TASK_ID");
CREATE INDEX "IX_PROCESS_TASK_ADD_S_EB6E17DE" ON "PROCESS_TASK_ADD_SIGN_USER" ("USER_ID", "STATUS");
CREATE UNIQUE INDEX "UQ_PROCESS_TASK_CANDI_77F0F254" ON "PROCESS_TASK_CANDIDATE_GROUP" ("PROCESS_TASK_ID", "GROUP_CODE");
CREATE INDEX "IX_PROCESS_TASK_CANDI_68F3C594" ON "PROCESS_TASK_CANDIDATE_GROUP" ("GROUP_CODE", "PROCESS_TASK_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_TASK_CANDI_F8776AD5" ON "PROCESS_TASK_CANDIDATE_USER" ("PROCESS_TASK_ID", "USER_ID");
CREATE INDEX "IX_PROCESS_TASK_CANDI_EC050E17" ON "PROCESS_TASK_CANDIDATE_USER" ("USER_ID", "PROCESS_TASK_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_TASK_SLA_U_444F27B1" ON "PROCESS_TASK_SLA" ("TASK_ID");
CREATE INDEX "IX_PROCESS_TASK_SLA_I_EFC42058" ON "PROCESS_TASK_SLA" ("PROCESS_INSTANCE_ID", "NODE_ID");
CREATE INDEX "IX_PROCESS_TASK_SLA_I_0A56FF31" ON "PROCESS_TASK_SLA" ("RESPONSE_STATUS", "RESPONSE_DUE_AT");
CREATE INDEX "IX_PROCESS_TASK_SLA_I_596905FD" ON "PROCESS_TASK_SLA" ("COMPLETION_STATUS", "COMPLETION_DUE_AT");
CREATE INDEX "IX_PROCESS_TASK_SLA_I_3ED787D2" ON "PROCESS_TASK_SLA" ("CURRENT_ASSIGNEE_ID", "OVERALL_STATUS");
CREATE UNIQUE INDEX "UQ_PROCESS_TASK_SLA_E_C70ECDAD" ON "PROCESS_TASK_SLA_EVENT" ("IDEMPOTENCY_KEY");
CREATE INDEX "IX_PROCESS_TASK_SLA_E_1A4BB9B3" ON "PROCESS_TASK_SLA_EVENT" ("STATUS", "TRIGGER_AT", "NEXT_RETRY_TIME");
CREATE INDEX "IX_PROCESS_TASK_SLA_E_0E450A19" ON "PROCESS_TASK_SLA_EVENT" ("STATUS", "LEASE_UNTIL");
CREATE INDEX "IX_PROCESS_TASK_SLA_E_91B6BAA8" ON "PROCESS_TASK_SLA_EVENT" ("SLA_ID", "CREATE_TIME");
CREATE INDEX "IX_PROCESS_TASK_SLA_P_27E8BF40" ON "PROCESS_TASK_SLA_PAUSE" ("SLA_ID", "STARTED_AT", "RESUMED_AT");
CREATE INDEX "IX_PROCESS_TASK_SLA_P_F0918DD9" ON "PROCESS_TASK_SLA_PAUSE" ("TASK_ID", "RESUMED_AT");
CREATE UNIQUE INDEX "UQ_PROCESS_UI_RELEASE_BBF54802" ON "PROCESS_UI_RELEASE_BINDING" ("PROCESS_VERSION_HISTORY_ID", "NODE_ID", "CONFIG_TYPE", "CONFIG_ID");
CREATE INDEX "IX_PROCESS_UI_RELEASE_55E9406B" ON "PROCESS_UI_RELEASE_BINDING" ("CONFIG_TYPE", "CONFIG_ID", "PROCESS_VERSION_HISTORY_ID");
CREATE INDEX "IX_PROCESS_UI_RELEASE_307A0869" ON "PROCESS_UI_RELEASE_BINDING" ("PINNED_RELEASE_ID", "PROCESS_VERSION_HISTORY_ID");
CREATE INDEX "IX_PROCESS_UI_RELEASE_54A8B6D4" ON "PROCESS_UI_RELEASE_BINDING" ("DEPLOYMENT_ID");
CREATE UNIQUE INDEX "UQ_PROCESS_VERSION_HI_B135D8B0" ON "PROCESS_VERSION_HISTORY" ("PROCESS_CONFIG_ID", "VERSION");
CREATE INDEX "IX_PROCESS_VERSION_HI_FE64BC92" ON "PROCESS_VERSION_HISTORY" ("PROCESS_CONFIG_ID");
CREATE INDEX "IX_PROCESS_VERSION_HI_FF7E7681" ON "PROCESS_VERSION_HISTORY" ("PROCESS_KEY");
CREATE INDEX "IX_PROCESS_VERSION_HI_8E1D61DF" ON "PROCESS_VERSION_HISTORY" ("VERSION");
CREATE INDEX "IX_PROCESS_VERSION_HI_B058BDE0" ON "PROCESS_VERSION_HISTORY" ("DEPLOYMENT_ID");
CREATE UNIQUE INDEX "UQ_STORAGE_FILE_OBJEC_83021A1D" ON "STORAGE_FILE_OBJECT" (SUBSTR("STORAGE_URL",1,768));
CREATE UNIQUE INDEX "UQ_STORAGE_FILE_OBJEC_43D31CAD" ON "STORAGE_FILE_OBJECT" ("OWNER_USER_ID", "IDEMPOTENCY_KEY");
CREATE INDEX "IX_STORAGE_FILE_OBJEC_89E5337A" ON "STORAGE_FILE_OBJECT" ("OWNER_USER_ID", "DELETED", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_SYS_DICT_UK_DICT_CODE" ON "SYS_DICT" ("DICT_CODE", "DELETED");
CREATE INDEX "IX_SYS_DICT_ITEM_IDX_DICT_ID" ON "SYS_DICT_ITEM" ("DICT_ID");
CREATE INDEX "IX_SYS_DICT_ITEM_IDX_DICT_CODE" ON "SYS_DICT_ITEM" ("DICT_CODE");
CREATE INDEX "IX_SYS_DICT_ITEM_IDX_PARENT_ID" ON "SYS_DICT_ITEM" ("PARENT_ID");
CREATE INDEX "IX_SYS_DICT_ITEM_IDX__2532F5AD" ON "SYS_DICT_ITEM" ("DICT_CODE", "ITEM_CODE", "DELETED");
CREATE UNIQUE INDEX "UQ_SYS_EXTERNAL_SYSTE_D2BF0FF2" ON "SYS_EXTERNAL_SYSTEM" ("SYSTEM_CODE");
CREATE INDEX "IX_SYS_EXTERNAL_SYSTE_6F9EBEBB" ON "SYS_EXTERNAL_SYSTEM" ("DELETED", "STATUS", "SYSTEM_NAME");
CREATE UNIQUE INDEX "UQ_SYS_EXTERNAL_SYSTE_AC117148" ON "SYS_EXTERNAL_SYSTEM_PARAMETER" ("EXTERNAL_SYSTEM_ID", "ACTIVE_PARAMETER_NAME_EN");
CREATE INDEX "IX_SYS_EXTERNAL_SYSTE_5B74D69C" ON "SYS_EXTERNAL_SYSTEM_PARAMETER" ("EXTERNAL_SYSTEM_ID", "DELETED", "SORT_ORDER", "ID");
CREATE UNIQUE INDEX "UQ_SYS_GLOBAL_SETTING_EB9CE9E2" ON "SYS_GLOBAL_SETTING" ("SCOPE_TYPE", "OWNER_ID", "SETTING_KEY");
CREATE UNIQUE INDEX "UQ_SYS_GROUP_UK_GROUP_CODE" ON "SYS_GROUP" ("GROUP_CODE");
CREATE INDEX "IX_SYS_GROUP_IDX_STATUS" ON "SYS_GROUP" ("STATUS");
CREATE INDEX "IX_SYS_GROUP_IDX_DELETED" ON "SYS_GROUP" ("DELETED");
CREATE INDEX "IX_SYS_MENU_IDX_PARENT_ID" ON "SYS_MENU" ("PARENT_ID");
CREATE INDEX "IX_SYS_MENU_IDX_SORT" ON "SYS_MENU" ("SORT");
CREATE INDEX "IX_SYS_MENU_IDX_STATUS" ON "SYS_MENU" ("STATUS");
CREATE INDEX "IX_SYS_MENU_IDX_DELETED" ON "SYS_MENU" ("DELETED");
CREATE INDEX "IX_SYS_MENU_IDX_ENTITY_CODE" ON "SYS_MENU" ("ENTITY_CODE");
CREATE UNIQUE INDEX "UQ_SYS_ORGANIZATION_U_7D784E3F" ON "SYS_ORGANIZATION" ("ORG_CODE");
CREATE INDEX "IX_SYS_ORGANIZATION_I_337A2EAF" ON "SYS_ORGANIZATION" ("PARENT_ID");
CREATE INDEX "IX_SYS_ORGANIZATION_IDX_TYPE" ON "SYS_ORGANIZATION" ("TYPE");
CREATE INDEX "IX_SYS_ORGANIZATION_IDX_PATH" ON "SYS_ORGANIZATION" ("PATH");
CREATE INDEX "IX_SYS_ORGANIZATION_IDX_STATUS" ON "SYS_ORGANIZATION" ("STATUS");
CREATE INDEX "IX_SYS_ORGANIZATION_I_DDA6485A" ON "SYS_ORGANIZATION" ("DELETED");
CREATE INDEX "IX_SYS_ORGANIZATION_I_7AC69A9A" ON "SYS_ORGANIZATION" ("BUSINESS_LEVEL_CODE", "STATUS", "DELETED");
CREATE UNIQUE INDEX "UQ_SYS_POSITION_UK_SY_49BB9FD7" ON "SYS_POSITION" ("POSITION_CODE");
CREATE INDEX "IX_SYS_POSITION_IDX_S_E6A44A92" ON "SYS_POSITION" ("STATUS", "DELETED", "SORT_ORDER");
CREATE UNIQUE INDEX "UQ_SYS_POSITION_ASSIG_50B12B4D" ON "SYS_POSITION_ASSIGNMENT" ("POSITION_ID", "ORGANIZATION_UNIT_ID", "USER_ID", "EFFECTIVE_FROM");
CREATE INDEX "IX_SYS_POSITION_ASSIG_2EC0F960" ON "SYS_POSITION_ASSIGNMENT" ("POSITION_ID", "ORGANIZATION_UNIT_ID", "EFFECTIVE_FROM", "EFFECTIVE_TO", "REVOKED_AT");
CREATE INDEX "IX_SYS_POSITION_ASSIG_C9BCDAF2" ON "SYS_POSITION_ASSIGNMENT" ("USER_ID", "EFFECTIVE_FROM", "EFFECTIVE_TO", "REVOKED_AT");
CREATE INDEX "IX_SYS_POSITION_ASSIG_82F62A15" ON "SYS_POSITION_ASSIGNMENT" ("ORGANIZATION_UNIT_ID", "POSITION_ID");
CREATE UNIQUE INDEX "UQ_SYS_POSITION_ASSIG_FF99F6FD" ON "SYS_POSITION_ASSIGNMENT_BATCH" ("CREATED_BY", "IDEMPOTENCY_KEY");
CREATE INDEX "IX_SYS_POSITION_ASSIG_7DA97680" ON "SYS_POSITION_ASSIGNMENT_BATCH" ("CREATE_TIME");
CREATE UNIQUE INDEX "UQ_SYS_ROLE_UK_ROLE_CODE" ON "SYS_ROLE" ("ROLE_CODE");
CREATE INDEX "IX_SYS_ROLE_IDX_STATUS" ON "SYS_ROLE" ("STATUS");
CREATE INDEX "IX_SYS_ROLE_IDX_DELETED" ON "SYS_ROLE" ("DELETED");
CREATE UNIQUE INDEX "UQ_SYS_ROLE_MENU_UK_ROLE_MENU" ON "SYS_ROLE_MENU" ("ROLE_ID", "MENU_ID");
CREATE INDEX "IX_SYS_ROLE_MENU_IDX_ROLE_ID" ON "SYS_ROLE_MENU" ("ROLE_ID");
CREATE INDEX "IX_SYS_ROLE_MENU_IDX_MENU_ID" ON "SYS_ROLE_MENU" ("MENU_ID");
CREATE UNIQUE INDEX "UQ_SYS_USER_UK_USERNAME" ON "SYS_USER" ("USERNAME");
CREATE INDEX "IX_SYS_USER_IDX_STATUS" ON "SYS_USER" ("STATUS");
CREATE INDEX "IX_SYS_USER_IDX_DELETED" ON "SYS_USER" ("DELETED");
CREATE INDEX "IX_SYS_USER_IDX_ORG_ID" ON "SYS_USER" ("ORG_ID");
CREATE INDEX "IX_SYS_USER_IDX_DEPT_ID" ON "SYS_USER" ("DEPT_ID");
CREATE UNIQUE INDEX "UQ_SYS_USER_GROUP_UK__165F1951" ON "SYS_USER_GROUP" ("USER_ID", "GROUP_ID");
CREATE INDEX "IX_SYS_USER_GROUP_IDX_USER_ID" ON "SYS_USER_GROUP" ("USER_ID");
CREATE INDEX "IX_SYS_USER_GROUP_IDX_GROUP_ID" ON "SYS_USER_GROUP" ("GROUP_ID");
CREATE UNIQUE INDEX "UQ_SYS_USER_ROLE_UK_USER_ROLE" ON "SYS_USER_ROLE" ("USER_ID", "ROLE_ID");
CREATE INDEX "IX_SYS_USER_ROLE_IDX_USER_ID" ON "SYS_USER_ROLE" ("USER_ID");
CREATE INDEX "IX_SYS_USER_ROLE_IDX_ROLE_ID" ON "SYS_USER_ROLE" ("ROLE_ID");
CREATE UNIQUE INDEX "UQ_SYSTEM_OPERATION_L_E3B727ED" ON "SYSTEM_OPERATION_LOG" ("EVENT_ID");
CREATE INDEX "IX_SYSTEM_OPERATION_L_5C149D66" ON "SYSTEM_OPERATION_LOG" ("CREATE_TIME");
CREATE INDEX "IX_SYSTEM_OPERATION_L_2D2C41FA" ON "SYSTEM_OPERATION_LOG" ("OPERATOR_ID", "CREATE_TIME");
CREATE INDEX "IX_SYSTEM_OPERATION_L_E2DF6430" ON "SYSTEM_OPERATION_LOG" ("MODULE_CODE", "OPERATION_CODE", "CREATE_TIME");
CREATE INDEX "IX_SYSTEM_OPERATION_L_56DC06CF" ON "SYSTEM_OPERATION_LOG" ("TARGET_TYPE", "TARGET_ID");
CREATE INDEX "IX_SYSTEM_OPERATION_L_11E9F8E5" ON "SYSTEM_OPERATION_LOG" ("RESULT", "CREATE_TIME");
CREATE INDEX "IX_SYSTEM_OPERATION_L_116E594D" ON "SYSTEM_OPERATION_LOG" ("TRACE_ID");
CREATE INDEX "IX_SYSTEM_OPERATION_L_BB6719E2" ON "SYSTEM_OPERATION_LOG" ("OPERATION_ID", "CREATE_TIME");
CREATE INDEX "IX_SYSTEM_OPERATION_L_F2E3C6AE" ON "SYSTEM_OPERATION_LOG" ("SOURCE_TYPE", "SOURCE_ID", "CREATE_TIME");
CREATE INDEX "IX_TASK_SLA_ESCALATIO_A97DF254" ON "TASK_SLA_ESCALATION_STEP" ("POLICY_ID", "ENABLED", "SORT_ORDER");
CREATE UNIQUE INDEX "UQ_TASK_SLA_POLICY_UK_2654ECC2" ON "TASK_SLA_POLICY" ("POLICY_CODE", "VERSION", "DELETED");
CREATE INDEX "IX_TASK_SLA_POLICY_ID_69CB3692" ON "TASK_SLA_POLICY" ("POLICY_CODE", "STATUS", "DELETED", "VERSION");
CREATE UNIQUE INDEX "UQ_UI_COMPONENT_TEMPL_17FB1226" ON "UI_COMPONENT_TEMPLATE" ("TEMPLATE_KEY", "DELETED");
CREATE UNIQUE INDEX "UQ_UI_COMPONENT_TEMPL_7FFBAA34" ON "UI_COMPONENT_TEMPLATE_VERSION" ("TEMPLATE_ID", "VERSION");
CREATE UNIQUE INDEX "UQ_UI_CONFIG_HOTFIX_R_05C5D648" ON "UI_CONFIG_HOTFIX_REQUEST" ("CONFIG_TYPE", "CONFIG_ID", "OPEN_SLOT");
CREATE UNIQUE INDEX "UQ_UI_CONFIG_HOTFIX_R_5938694F" ON "UI_CONFIG_HOTFIX_REQUEST" ("RELEASE_ID");
CREATE INDEX "IX_UI_CONFIG_HOTFIX_R_99511377" ON "UI_CONFIG_HOTFIX_REQUEST" ("STATUS", "WINDOW_END");
CREATE INDEX "IX_UI_CONFIG_HOTFIX_R_05B134A8" ON "UI_CONFIG_HOTFIX_REQUEST" ("CONFIG_TYPE", "CONFIG_ID", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_UI_CONFIG_HOTFIX_T_DE572317" ON "UI_CONFIG_HOTFIX_TARGET" ("CONFIG_TYPE", "CONFIG_ID", "PROCESS_VERSION_HISTORY_ID", "ACTIVE_SLOT");
CREATE INDEX "IX_UI_CONFIG_HOTFIX_T_CA1D2D2F" ON "UI_CONFIG_HOTFIX_TARGET" ("HOTFIX_RELEASE_ID", "STATUS");
CREATE INDEX "IX_UI_CONFIG_HOTFIX_T_1DF36053" ON "UI_CONFIG_HOTFIX_TARGET" ("PINNED_RELEASE_ID", "STATUS");
CREATE INDEX "IX_UI_CONFIG_HOTFIX_T_A9FCF5AC" ON "UI_CONFIG_HOTFIX_TARGET" ("PROCESS_VERSION_HISTORY_ID", "STATUS");
CREATE UNIQUE INDEX "UQ_UI_CONFIG_RELEASE__82318E99" ON "UI_CONFIG_RELEASE" ("CONFIG_TYPE", "CONFIG_ID", "VERSION");
CREATE UNIQUE INDEX "UQ_UI_CONFIG_RELEASE__013F3A38" ON "UI_CONFIG_RELEASE" ("CONFIG_TYPE", "CONFIG_ID", "ACTIVE_SLOT");
CREATE INDEX "IX_UI_CONFIG_RELEASE__0027BB24" ON "UI_CONFIG_RELEASE" ("CONFIG_TYPE", "CONFIG_ID", "STATUS");
CREATE INDEX "IX_UI_CONFIG_RELEASE__8DA5FA68" ON "UI_CONFIG_RELEASE_AUDIT" ("CONFIG_TYPE", "CONFIG_ID", "CREATE_TIME");
CREATE INDEX "IX_UI_CONFIG_RELEASE__E531C467" ON "UI_CONFIG_RELEASE_AUDIT" ("RELEASE_ID", "CREATE_TIME");
CREATE INDEX "IX_UI_CONFIG_RELEASE__27369C0E" ON "UI_CONFIG_RELEASE_AUDIT" ("OPERATION", "CREATE_TIME");
CREATE UNIQUE INDEX "UQ_UI_EVENT_BINDING_U_4605FA62" ON "UI_EVENT_BINDING" ("OWNER_TYPE", "OWNER_ID", "TARGET_TYPE", "TARGET_KEY", "EVENT_CODE", "DELETED");
CREATE INDEX "IX_UI_EVENT_BINDING_I_44C07048" ON "UI_EVENT_BINDING" ("OWNER_TYPE", "OWNER_ID", "ENABLED", "DELETED");
CREATE UNIQUE INDEX "UQ_UI_EXTENSION_DEFIN_DA55ED55" ON "UI_EXTENSION_DEFINITION" ("EXTENSION_TYPE", "EXTENSION_KEY", "VERSION", "DELETED");
CREATE UNIQUE INDEX "UQ_UI_EXTENSION_DEFIN_C58FC8D3" ON "UI_EXTENSION_DEFINITION" ("LEGACY_SERVICE_ID", "PROVIDER_OPERATION_CODE", "DELETED");
CREATE INDEX "IX_UI_EXTENSION_DEFIN_E14FB433" ON "UI_EXTENSION_DEFINITION" ("EXTENSION_TYPE", "EXTENSION_KEY", "STATUS", "DELETED");
CREATE UNIQUE INDEX "UQ_UI_HOTFIX_OBSERVAT_5F004B19" ON "UI_HOTFIX_OBSERVATION_METRIC" ("REQUEST_ID", "METRIC_CODE");
CREATE INDEX "IX_UI_HOTFIX_OBSERVAT_9BE31EFE" ON "UI_HOTFIX_OBSERVATION_METRIC" ("RELEASE_ID", "METRIC_CODE");
CREATE UNIQUE INDEX "UQ_UI_VIEW_COMPOSITIO_A4593135" ON "UI_VIEW_COMPOSITION" ("OWNER_TYPE", "OWNER_ID", "ACTIVE_COMPOSITION_KEY");
CREATE INDEX "IX_UI_VIEW_COMPOSITIO_AD0BEED8" ON "UI_VIEW_COMPOSITION" ("OWNER_TYPE", "OWNER_ID", "DELETED", "ORDER_KEY");
CREATE UNIQUE INDEX "UQ_WORK_CALENDAR_UK_W_C803D2FA" ON "WORK_CALENDAR" ("CALENDAR_CODE", "VERSION", "DELETED");
CREATE INDEX "IX_WORK_CALENDAR_IDX__E01E3AF0" ON "WORK_CALENDAR" ("STATUS", "DEFAULT_FLAG", "DELETED");
CREATE INDEX "IX_WORK_CALENDAR_BIND_4965B439" ON "WORK_CALENDAR_BINDING" ("SCOPE_TYPE", "SCOPE_KEY", "STATUS", "DELETED", "PRIORITY");
CREATE INDEX "IX_WORK_CALENDAR_BIND_12CEA758" ON "WORK_CALENDAR_BINDING" ("CALENDAR_ID", "DELETED");
CREATE UNIQUE INDEX "UQ_WORK_CALENDAR_EXCE_0E0BE2E8" ON "WORK_CALENDAR_EXCEPTION" ("CALENDAR_ID", "EXCEPTION_DATE");
CREATE INDEX "IX_WORK_CALENDAR_EXCE_1A27032A" ON "WORK_CALENDAR_EXCEPTION" ("CALENDAR_ID", "EXCEPTION_DATE", "EXCEPTION_TYPE");
CREATE UNIQUE INDEX "UQ_WORK_CALENDAR_EXCE_88A63637" ON "WORK_CALENDAR_EXCEPTION_PERIOD" ("EXCEPTION_ID", "START_MINUTE", "END_MINUTE");
CREATE INDEX "IX_WORK_CALENDAR_EXCE_8A778B25" ON "WORK_CALENDAR_EXCEPTION_PERIOD" ("EXCEPTION_ID", "SORT_ORDER");
CREATE UNIQUE INDEX "UQ_WORK_CALENDAR_PERI_CF4D017B" ON "WORK_CALENDAR_PERIOD" ("CALENDAR_ID", "DAY_OF_WEEK", "START_MINUTE", "END_MINUTE");
CREATE INDEX "IX_WORK_CALENDAR_PERI_BE859F60" ON "WORK_CALENDAR_PERIOD" ("CALENDAR_ID", "DAY_OF_WEEK", "SORT_ORDER");
CREATE UNIQUE INDEX "UQ_WORKFLOW_OUTBOX_EV_4BA83A8F" ON "WORKFLOW_OUTBOX_EVENT" ("TOPIC", "EVENT_KEY");
CREATE INDEX "IX_WORKFLOW_OUTBOX_EV_9B299384" ON "WORKFLOW_OUTBOX_EVENT" ("STATUS", "NEXT_RETRY_TIME", "CREATE_TIME");
CREATE INDEX "IX_WORKFLOW_OUTBOX_EV_642F648A" ON "WORKFLOW_OUTBOX_EVENT" ("AGGREGATE_TYPE", "AGGREGATE_ID");
CREATE INDEX "IX_WORKFLOW_OUTBOX_EV_C1538A7D" ON "WORKFLOW_OUTBOX_EVENT" ("STATUS", "LEASE_UNTIL");
CREATE INDEX "IX_WORKFLOW_OUTBOX_EV_979A78E8" ON "WORKFLOW_OUTBOX_EVENT" ("STATUS", "PROCESSED_TIME", "ID");
CREATE UNIQUE INDEX "UQ_WORKFLOW_SCHEMA_CH_CEAA4EC1" ON "WORKFLOW_SCHEMA_CHANGE" ("ACTIVE_HASH");
CREATE INDEX "IX_WORKFLOW_SCHEMA_CH_E820EA38" ON "WORKFLOW_SCHEMA_CHANGE" ("STATUS", "NEXT_ATTEMPT_AT", "LEASE_UNTIL", "CREATE_TIME");
CREATE INDEX "IX_WORKFLOW_SCHEMA_CH_3CCE1118" ON "WORKFLOW_SCHEMA_CHANGE" ("DDL_HASH");

-- 表与字段注释。
COMMENT ON TABLE "ACT_APP_APPDEF" IS 'Flowable 应用模型应用定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_APP_APPDEF"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_APP_APPDEF"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_APP_APPDEF"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_APP_APPDEF"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_APP_APPDEF"."VERSION_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "ACT_APP_APPDEF"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_APP_APPDEF"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_APP_APPDEF"."RESOURCE_NAME_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "ACT_APP_APPDEF"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "ACT_APP_APPDEF"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_APP_DEPLOYMENT" IS 'Flowable 应用模型部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT"."DEPLOY_TIME_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_APP_DEPLOYMENT_RESOURCE" IS 'Flowable 应用模型部署包资源文件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT_RESOURCE"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT_RESOURCE"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT_RESOURCE"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_APP_DEPLOYMENT_RESOURCE"."RESOURCE_BYTES_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON TABLE "ACT_CMMN_CASEDEF" IS 'Flowable 案例管理案例定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."VERSION_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."RESOURCE_NAME_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."HAS_GRAPHICAL_NOTATION_" IS 'HAS_GRAPHICAL_NOTATION_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."DGRM_RESOURCE_NAME_" IS 'DGRM_RESOURCE_NAME_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_CASEDEF"."HAS_START_FORM_KEY_" IS 'HAS_START_FORM_KEY_ 引擎属性，供 Flowable 案例管理案例定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_CMMN_DEPLOYMENT" IS 'Flowable 案例管理部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT"."DEPLOY_TIME_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT"."PARENT_DEPLOYMENT_ID_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_CMMN_DEPLOYMENT_RESOURCE" IS 'Flowable 案例管理部署包资源文件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT_RESOURCE"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT_RESOURCE"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT_RESOURCE"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT_RESOURCE"."RESOURCE_BYTES_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON COLUMN "ACT_CMMN_DEPLOYMENT_RESOURCE"."GENERATED_" IS 'GENERATED_ 引擎属性，供 Flowable 案例管理部署包资源文件表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_CMMN_HI_CASE_INST" IS 'Flowable 案例管理HI案例实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."BUSINESS_KEY_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."PARENT_ID_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."CASE_DEF_ID_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."STATE_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."END_TIME_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."START_USER_ID_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."CALLBACK_ID_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."CALLBACK_TYPE_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."REFERENCE_ID_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."REFERENCE_TYPE_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."LAST_REACTIVATION_TIME_" IS 'LAST_REACTIVATION 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."LAST_REACTIVATION_USER_ID_" IS 'LAST_REACTIVATION_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_CASE_INST"."BUSINESS_STATUS_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "ACT_CMMN_HI_MIL_INST" IS 'Flowable 案例管理HI里程碑实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."TIME_STAMP_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."CASE_INST_ID_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."CASE_DEF_ID_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_MIL_INST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_CMMN_HI_PLAN_ITEM_INST" IS 'Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."STATE_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."CASE_DEF_ID_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."CASE_INST_ID_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."STAGE_INST_ID_" IS 'STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."IS_STAGE_" IS 'IS_STAGE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."ITEM_DEFINITION_ID_" IS 'ITEM_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."ITEM_DEFINITION_TYPE_" IS 'ITEM_DEFINITION_TYPE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."LAST_AVAILABLE_TIME_" IS 'LAST_AVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."LAST_ENABLED_TIME_" IS 'LAST_ENABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."LAST_DISABLED_TIME_" IS 'LAST_DISABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."LAST_STARTED_TIME_" IS 'LAST_STARTED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."LAST_SUSPENDED_TIME_" IS 'LAST_SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."COMPLETED_TIME_" IS 'COMPLETED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."OCCURRED_TIME_" IS 'OCCURRED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."TERMINATED_TIME_" IS 'TERMINATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."EXIT_TIME_" IS 'EXIT 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."ENDED_TIME_" IS 'ENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."LAST_UPDATED_TIME_" IS 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."START_USER_ID_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."REFERENCE_ID_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."REFERENCE_TYPE_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."ENTRY_CRITERION_ID_" IS 'ENTRY_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."EXIT_CRITERION_ID_" IS 'EXIT_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."SHOW_IN_OVERVIEW_" IS 'SHOW_IN_OVERVIEW_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."EXTRA_VALUE_" IS 'EXTRA_VALUE_ 引擎属性，供 Flowable 案例管理HI计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."DERIVED_CASE_DEF_ID_" IS 'DERIVED_CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."LAST_UNAVAILABLE_TIME_" IS 'LAST_UNAVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."ASSIGNEE_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "ACT_CMMN_HI_PLAN_ITEM_INST"."COMPLETED_BY_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "ACT_CMMN_RU_CASE_INST" IS 'Flowable 案例管理RU案例实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."BUSINESS_KEY_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."PARENT_ID_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."CASE_DEF_ID_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."STATE_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."START_USER_ID_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."CALLBACK_ID_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."CALLBACK_TYPE_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."LOCK_TIME_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."IS_COMPLETEABLE_" IS 'IS_COMPLETEABLE_ 引擎属性，供 Flowable 案例管理RU案例实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."REFERENCE_ID_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."REFERENCE_TYPE_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."LAST_REACTIVATION_TIME_" IS 'LAST_REACTIVATION 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."LAST_REACTIVATION_USER_ID_" IS 'LAST_REACTIVATION_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_CASE_INST"."BUSINESS_STATUS_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "ACT_CMMN_RU_MIL_INST" IS 'Flowable 案例管理RU里程碑实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_RU_MIL_INST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_RU_MIL_INST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_RU_MIL_INST"."TIME_STAMP_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_MIL_INST"."CASE_INST_ID_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_MIL_INST"."CASE_DEF_ID_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_MIL_INST"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_MIL_INST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_CMMN_RU_PLAN_ITEM_INST" IS 'Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."CASE_DEF_ID_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."CASE_INST_ID_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."STAGE_INST_ID_" IS 'STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."IS_STAGE_" IS 'IS_STAGE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."STATE_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."START_USER_ID_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."REFERENCE_ID_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."REFERENCE_TYPE_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."ITEM_DEFINITION_ID_" IS 'ITEM_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."ITEM_DEFINITION_TYPE_" IS 'ITEM_DEFINITION_TYPE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."IS_COMPLETEABLE_" IS 'IS_COMPLETEABLE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."IS_COUNT_ENABLED_" IS '计数开关，控制是否维护关联对象数量';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."VAR_COUNT_" IS 'VAR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."SENTRY_PART_INST_COUNT_" IS 'SENTRY_PART_INST 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."LAST_AVAILABLE_TIME_" IS 'LAST_AVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."LAST_ENABLED_TIME_" IS 'LAST_ENABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."LAST_DISABLED_TIME_" IS 'LAST_DISABLED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."LAST_STARTED_TIME_" IS 'LAST_STARTED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."LAST_SUSPENDED_TIME_" IS 'LAST_SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."COMPLETED_TIME_" IS 'COMPLETED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."OCCURRED_TIME_" IS 'OCCURRED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."TERMINATED_TIME_" IS 'TERMINATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."EXIT_TIME_" IS 'EXIT 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."ENDED_TIME_" IS 'ENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."ENTRY_CRITERION_ID_" IS 'ENTRY_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."EXIT_CRITERION_ID_" IS 'EXIT_CRITERION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."EXTRA_VALUE_" IS 'EXTRA_VALUE_ 引擎属性，供 Flowable 案例管理RU计划项目实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."DERIVED_CASE_DEF_ID_" IS 'DERIVED_CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."LAST_UNAVAILABLE_TIME_" IS 'LAST_UNAVAILABLE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."ASSIGNEE_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "ACT_CMMN_RU_PLAN_ITEM_INST"."COMPLETED_BY_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "ACT_CMMN_RU_SENTRY_PART_INST" IS 'Flowable 案例管理RU入口条件条件部分实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."CASE_DEF_ID_" IS 'CASE_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."CASE_INST_ID_" IS 'CASE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."PLAN_ITEM_INST_ID_" IS 'PLAN_ITEM_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."ON_PART_ID_" IS 'ON_PART 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."IF_PART_ID_" IS 'IF_PART 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_CMMN_RU_SENTRY_PART_INST"."TIME_STAMP_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON TABLE "ACT_DMN_DECISION" IS 'Flowable 决策规则决策定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_DMN_DECISION"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_DMN_DECISION"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_DMN_DECISION"."VERSION_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "ACT_DMN_DECISION"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_DMN_DECISION"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_DMN_DECISION"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_DMN_DECISION"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_DMN_DECISION"."RESOURCE_NAME_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "ACT_DMN_DECISION"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "ACT_DMN_DECISION"."DECISION_TYPE_" IS 'DECISION_TYPE_ 引擎属性，供 Flowable 决策规则决策定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_DMN_DEPLOYMENT" IS 'Flowable 决策规则部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT"."DEPLOY_TIME_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT"."PARENT_DEPLOYMENT_ID_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "ACT_DMN_DEPLOYMENT_RESOURCE" IS 'Flowable 决策规则部署包资源文件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT_RESOURCE"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT_RESOURCE"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT_RESOURCE"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_DMN_DEPLOYMENT_RESOURCE"."RESOURCE_BYTES_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON TABLE "ACT_DMN_HI_DECISION_EXECUTION" IS 'Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."DECISION_DEFINITION_ID_" IS 'DECISION_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."END_TIME_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."INSTANCE_ID_" IS 'INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."ACTIVITY_ID_" IS 'ACTIVITY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."FAILED_" IS 'FAILED_ 引擎属性，供 Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."EXECUTION_JSON_" IS 'EXECUTION_JSON_ 引擎属性，供 Flowable 决策规则HI决策定义执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_DMN_HI_DECISION_EXECUTION"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON TABLE "ACT_EVT_LOG" IS 'Flowable 事件事件日志表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_EVT_LOG"."LOG_NR_" IS 'LOG_NR_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_EVT_LOG"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_EVT_LOG"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_EVT_LOG"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_EVT_LOG"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_EVT_LOG"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_EVT_LOG"."TIME_STAMP_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "ACT_EVT_LOG"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_EVT_LOG"."DATA_" IS 'DATA_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_EVT_LOG"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_EVT_LOG"."LOCK_TIME_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_EVT_LOG"."IS_PROCESSED_" IS 'IS_PROCESSED_ 引擎属性，供 Flowable 事件事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_GE_BYTEARRAY" IS 'Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_GE_BYTEARRAY"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_GE_BYTEARRAY"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_GE_BYTEARRAY"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_GE_BYTEARRAY"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_GE_BYTEARRAY"."BYTES_" IS 'BYTES_ 引擎属性，供 Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_GE_BYTEARRAY"."GENERATED_" IS 'GENERATED_ 引擎属性，供 Flowable 通用资源二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_GE_PROPERTY" IS 'Flowable 通用资源引擎属性表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_GE_PROPERTY"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_GE_PROPERTY"."VALUE_" IS '属性值，供引擎读取配置和版本信息';
COMMENT ON COLUMN "ACT_GE_PROPERTY"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON TABLE "ACT_HI_ACTINST" IS 'Flowable 历史记录活动实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_ACTINST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_ACTINST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_HI_ACTINST"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_HI_ACTINST"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_ACTINST"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_HI_ACTINST"."ACT_ID_" IS 'ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ACTINST"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_HI_ACTINST"."CALL_PROC_INST_ID_" IS 'CALL_PROC_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ACTINST"."ACT_NAME_" IS 'ACT_NAME_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_ACTINST"."ACT_TYPE_" IS 'ACT_TYPE_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_ACTINST"."ASSIGNEE_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "ACT_HI_ACTINST"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_HI_ACTINST"."END_TIME_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "ACT_HI_ACTINST"."TRANSACTION_ORDER_" IS 'TRANSACTION_ORDER_ 引擎属性，供 Flowable 历史记录活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_ACTINST"."DURATION_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "ACT_HI_ACTINST"."DELETE_REASON_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "ACT_HI_ACTINST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_HI_ACTINST"."COMPLETED_BY_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "ACT_HI_ATTACHMENT" IS 'Flowable 历史记录附件表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."URL_" IS 'URL_ 引擎属性，供 Flowable 历史记录附件表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."CONTENT_ID_" IS 'CONTENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ATTACHMENT"."TIME_" IS '发生时间，供引擎事件排序和历史追踪';
COMMENT ON TABLE "ACT_HI_COMMENT" IS 'Flowable 历史记录批注表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_COMMENT"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_COMMENT"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_HI_COMMENT"."TIME_" IS '发生时间，供引擎事件排序和历史追踪';
COMMENT ON COLUMN "ACT_HI_COMMENT"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_COMMENT"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_HI_COMMENT"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_COMMENT"."ACTION_" IS 'ACTION_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_COMMENT"."MESSAGE_" IS 'MESSAGE_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_COMMENT"."FULL_MSG_" IS 'FULL_MSG_ 引擎属性，供 Flowable 历史记录批注表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_HI_DETAIL" IS 'Flowable 历史记录变量详情表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_DETAIL"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_DETAIL"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_HI_DETAIL"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_DETAIL"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_HI_DETAIL"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_HI_DETAIL"."ACT_INST_ID_" IS 'ACT_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_DETAIL"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_HI_DETAIL"."VAR_TYPE_" IS 'VAR_TYPE_ 引擎属性，供 Flowable 历史记录变量详情表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_DETAIL"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_HI_DETAIL"."TIME_" IS '发生时间，供引擎事件排序和历史追踪';
COMMENT ON COLUMN "ACT_HI_DETAIL"."BYTEARRAY_ID_" IS 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_DETAIL"."DOUBLE_" IS '浮点变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_HI_DETAIL"."LONG_" IS '整数变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_HI_DETAIL"."TEXT_" IS '文本变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_HI_DETAIL"."TEXT2_" IS '第二段文本值，保存补充变量或序列化内容';
COMMENT ON TABLE "ACT_HI_ENTITYLINK" IS 'Flowable 历史记录实体关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."LINK_TYPE_" IS 'LINK_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."PARENT_ELEMENT_ID_" IS 'PARENT_ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."REF_SCOPE_ID_" IS 'REF_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."REF_SCOPE_TYPE_" IS 'REF_SCOPE_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."REF_SCOPE_DEFINITION_ID_" IS 'REF_SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."ROOT_SCOPE_ID_" IS 'ROOT_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."ROOT_SCOPE_TYPE_" IS 'ROOT_SCOPE_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_ENTITYLINK"."HIERARCHY_TYPE_" IS 'HIERARCHY_TYPE_ 引擎属性，供 Flowable 历史记录实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_HI_IDENTITYLINK" IS 'Flowable 历史记录参与者关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."GROUP_ID_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_HI_IDENTITYLINK"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "ACT_HI_PROCINST" IS 'Flowable 历史记录流程实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_PROCINST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_PROCINST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_HI_PROCINST"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_PROCINST"."BUSINESS_KEY_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "ACT_HI_PROCINST"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_HI_PROCINST"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_HI_PROCINST"."END_TIME_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "ACT_HI_PROCINST"."DURATION_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "ACT_HI_PROCINST"."START_USER_ID_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."START_ACT_ID_" IS 'START_ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."END_ACT_ID_" IS 'END_ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."SUPER_PROCESS_INSTANCE_ID_" IS 'SUPER_PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."DELETE_REASON_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "ACT_HI_PROCINST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_HI_PROCINST"."CALLBACK_ID_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."CALLBACK_TYPE_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "ACT_HI_PROCINST"."REFERENCE_ID_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."REFERENCE_TYPE_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "ACT_HI_PROCINST"."PROPAGATED_STAGE_INST_ID_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_PROCINST"."BUSINESS_STATUS_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "ACT_HI_TASKINST" IS 'Flowable 历史记录任务实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_TASKINST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_TASKINST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_HI_TASKINST"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_HI_TASKINST"."TASK_DEF_ID_" IS 'TASK_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TASKINST"."TASK_DEF_KEY_" IS 'TASK_DEF_KEY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_TASKINST"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_HI_TASKINST"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TASKINST"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TASKINST"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_HI_TASKINST"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TASKINST"."PROPAGATED_STAGE_INST_ID_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TASKINST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_HI_TASKINST"."PARENT_TASK_ID_" IS 'PARENT_TASK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TASKINST"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "ACT_HI_TASKINST"."OWNER_" IS 'OWNER_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."ASSIGNEE_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "ACT_HI_TASKINST"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_HI_TASKINST"."CLAIM_TIME_" IS 'CLAIM 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_HI_TASKINST"."END_TIME_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "ACT_HI_TASKINST"."DURATION_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "ACT_HI_TASKINST"."DELETE_REASON_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "ACT_HI_TASKINST"."PRIORITY_" IS 'PRIORITY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."DUE_DATE_" IS 'DUE_DATE_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."FORM_KEY_" IS 'FORM_KEY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_HI_TASKINST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_HI_TASKINST"."LAST_UPDATED_TIME_" IS 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_HI_TASKINST"."STATE_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "ACT_HI_TASKINST"."IN_PROGRESS_TIME_" IS 'IN_PROGRESS 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_HI_TASKINST"."IN_PROGRESS_STARTED_BY_" IS 'IN_PROGRESS_STARTED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."CLAIMED_BY_" IS 'CLAIMED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."SUSPENDED_TIME_" IS 'SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_HI_TASKINST"."SUSPENDED_BY_" IS 'SUSPENDED_BY_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TASKINST"."COMPLETED_BY_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON COLUMN "ACT_HI_TASKINST"."IN_PROGRESS_DUE_DATE_" IS 'IN_PROGRESS_DUE_DATE_ 引擎属性，供 Flowable 历史记录任务实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_HI_TSK_LOG" IS 'Flowable 历史记录任务事件日志表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."TIME_STAMP_" IS '事件时间戳，用于排序和审计追踪';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."DATA_" IS 'DATA_ 引擎属性，供 Flowable 历史记录任务事件日志表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_HI_TSK_LOG"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_HI_VARINST" IS 'Flowable 历史记录变量实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_HI_VARINST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_HI_VARINST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_HI_VARINST"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_HI_VARINST"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_HI_VARINST"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_HI_VARINST"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_HI_VARINST"."VAR_TYPE_" IS 'VAR_TYPE_ 引擎属性，供 Flowable 历史记录变量实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_HI_VARINST"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_VARINST"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_VARINST"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_HI_VARINST"."BYTEARRAY_ID_" IS 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_HI_VARINST"."DOUBLE_" IS '浮点变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_HI_VARINST"."LONG_" IS '整数变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_HI_VARINST"."TEXT_" IS '文本变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_HI_VARINST"."TEXT2_" IS '第二段文本值，保存补充变量或序列化内容';
COMMENT ON COLUMN "ACT_HI_VARINST"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_HI_VARINST"."LAST_UPDATED_TIME_" IS 'LAST_UPDATED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_HI_VARINST"."META_INFO_" IS '模型元数据，保存编辑器和发布需要的补充信息';
COMMENT ON TABLE "ACT_ID_BYTEARRAY" IS 'Flowable 身份管理二进制资源表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_BYTEARRAY"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_ID_BYTEARRAY"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_ID_BYTEARRAY"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_ID_BYTEARRAY"."BYTES_" IS 'BYTES_ 引擎属性，供 Flowable 身份管理二进制资源表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_ID_GROUP" IS 'Flowable 身份管理用户组表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_GROUP"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_ID_GROUP"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_ID_GROUP"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_ID_GROUP"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON TABLE "ACT_ID_INFO" IS 'Flowable 身份管理用户资料表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_INFO"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_ID_INFO"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_ID_INFO"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_ID_INFO"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_ID_INFO"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_ID_INFO"."VALUE_" IS '属性值，供引擎读取配置和版本信息';
COMMENT ON COLUMN "ACT_ID_INFO"."PASSWORD_" IS 'PASSWORD_ 引擎属性，供 Flowable 身份管理用户资料表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_INFO"."PARENT_ID_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "ACT_ID_MEMBERSHIP" IS 'Flowable 身份管理用户组成员表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_MEMBERSHIP"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_ID_MEMBERSHIP"."GROUP_ID_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "ACT_ID_PRIV" IS 'Flowable 身份管理权限表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_PRIV"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_ID_PRIV"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON TABLE "ACT_ID_PRIV_MAPPING" IS 'Flowable 身份管理权限权限映射表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_PRIV_MAPPING"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_ID_PRIV_MAPPING"."PRIV_ID_" IS 'PRIV 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_ID_PRIV_MAPPING"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_ID_PRIV_MAPPING"."GROUP_ID_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "ACT_ID_PROPERTY" IS 'Flowable 身份管理引擎属性表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_PROPERTY"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_ID_PROPERTY"."VALUE_" IS '属性值，供引擎读取配置和版本信息';
COMMENT ON COLUMN "ACT_ID_PROPERTY"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON TABLE "ACT_ID_TOKEN" IS 'Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_TOKEN"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_ID_TOKEN"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_ID_TOKEN"."TOKEN_VALUE_" IS 'TOKEN_VALUE_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_TOKEN"."TOKEN_DATE_" IS 'TOKEN_DATE_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_TOKEN"."IP_ADDRESS_" IS 'IP_ADDRESS_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_TOKEN"."USER_AGENT_" IS 'USER_AGENT_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_TOKEN"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_ID_TOKEN"."TOKEN_DATA_" IS 'TOKEN_DATA_ 引擎属性，供 Flowable 身份管理身份令牌表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_ID_USER" IS 'Flowable 身份管理用户表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_ID_USER"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_ID_USER"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_ID_USER"."FIRST_" IS 'FIRST_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_USER"."LAST_" IS 'LAST_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_USER"."DISPLAY_NAME_" IS 'DISPLAY_NAME_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_USER"."EMAIL_" IS 'EMAIL_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_USER"."PWD_" IS 'PWD_ 引擎属性，供 Flowable 身份管理用户表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_ID_USER"."PICTURE_ID_" IS 'PICTURE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_ID_USER"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_PROCDEF_INFO" IS 'Flowable 流程引擎用户资料表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_PROCDEF_INFO"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_PROCDEF_INFO"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_PROCDEF_INFO"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_PROCDEF_INFO"."INFO_JSON_ID_" IS 'INFO_JSON 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "ACT_RE_DEPLOYMENT" IS 'Flowable 模型仓库部署包表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."DEPLOY_TIME_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."DERIVED_FROM_" IS 'DERIVED_FROM_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."DERIVED_FROM_ROOT_" IS 'DERIVED_FROM_ROOT_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."PARENT_DEPLOYMENT_ID_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RE_DEPLOYMENT"."ENGINE_VERSION_" IS 'ENGINE_VERSION_ 引擎属性，供 Flowable 模型仓库部署包表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_RE_MODEL" IS 'Flowable 模型仓库模型表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RE_MODEL"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RE_MODEL"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RE_MODEL"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_RE_MODEL"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_RE_MODEL"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RE_MODEL"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RE_MODEL"."LAST_UPDATE_TIME_" IS 'LAST_UPDATE 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RE_MODEL"."VERSION_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "ACT_RE_MODEL"."META_INFO_" IS '模型元数据，保存编辑器和发布需要的补充信息';
COMMENT ON COLUMN "ACT_RE_MODEL"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_RE_MODEL"."EDITOR_SOURCE_VALUE_ID_" IS 'EDITOR_SOURCE_VALUE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RE_MODEL"."EDITOR_SOURCE_EXTRA_VALUE_ID_" IS 'EDITOR_SOURCE_EXTRA_VALUE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RE_MODEL"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_RE_PROCDEF" IS 'Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."VERSION_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."RESOURCE_NAME_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."DGRM_RESOURCE_NAME_" IS 'DGRM_RESOURCE_NAME_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."HAS_START_FORM_KEY_" IS 'HAS_START_FORM_KEY_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."HAS_GRAPHICAL_NOTATION_" IS 'HAS_GRAPHICAL_NOTATION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."SUSPENSION_STATE_" IS '挂起状态，控制定义或实例能否继续执行';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."ENGINE_VERSION_" IS 'ENGINE_VERSION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."DERIVED_FROM_" IS 'DERIVED_FROM_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."DERIVED_FROM_ROOT_" IS 'DERIVED_FROM_ROOT_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RE_PROCDEF"."DERIVED_VERSION_" IS 'DERIVED_VERSION_ 引擎属性，供 Flowable 模型仓库流程定义表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_RU_ACTINST" IS 'Flowable 运行时活动实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_ACTINST"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_ACTINST"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_ACTINST"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_ACTINST"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_RU_ACTINST"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_ACTINST"."ACT_ID_" IS 'ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ACTINST"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_RU_ACTINST"."CALL_PROC_INST_ID_" IS 'CALL_PROC_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ACTINST"."ACT_NAME_" IS 'ACT_NAME_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_ACTINST"."ACT_TYPE_" IS 'ACT_TYPE_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_ACTINST"."ASSIGNEE_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "ACT_RU_ACTINST"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_RU_ACTINST"."END_TIME_" IS '结束时间，用于判定实例完成和历史统计';
COMMENT ON COLUMN "ACT_RU_ACTINST"."DURATION_" IS '持续时长，供历史查询和统计分析';
COMMENT ON COLUMN "ACT_RU_ACTINST"."TRANSACTION_ORDER_" IS 'TRANSACTION_ORDER_ 引擎属性，供 Flowable 运行时活动实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_ACTINST"."DELETE_REASON_" IS '删除或取消原因，保留在历史记录中供审计';
COMMENT ON COLUMN "ACT_RU_ACTINST"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_RU_ACTINST"."COMPLETED_BY_" IS '完成人标识，供历史记录追溯责任人';
COMMENT ON TABLE "ACT_RU_DEADLETTER_JOB" IS 'Flowable 运行时死信作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."EXCLUSIVE_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."PROCESS_INSTANCE_ID_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."ELEMENT_NAME_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."CORRELATION_ID_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."EXCEPTION_STACK_ID_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."EXCEPTION_MSG_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."DUEDATE_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."REPEAT_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."HANDLER_TYPE_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."HANDLER_CFG_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."CUSTOM_VALUES_ID_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_DEADLETTER_JOB"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_RU_ENTITYLINK" IS 'Flowable 运行时实体关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."LINK_TYPE_" IS 'LINK_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."PARENT_ELEMENT_ID_" IS 'PARENT_ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."REF_SCOPE_ID_" IS 'REF_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."REF_SCOPE_TYPE_" IS 'REF_SCOPE_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."REF_SCOPE_DEFINITION_ID_" IS 'REF_SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."ROOT_SCOPE_ID_" IS 'ROOT_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."ROOT_SCOPE_TYPE_" IS 'ROOT_SCOPE_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_ENTITYLINK"."HIERARCHY_TYPE_" IS 'HIERARCHY_TYPE_ 引擎属性，供 Flowable 运行时实体关联表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_RU_EVENT_SUBSCR" IS 'Flowable 运行时事件订阅表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."EVENT_TYPE_" IS 'EVENT_TYPE_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."EVENT_NAME_" IS 'EVENT_NAME_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."ACTIVITY_ID_" IS 'ACTIVITY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."CONFIGURATION_" IS 'CONFIGURATION_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."CREATED_" IS 'CREATED_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."LOCK_TIME_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_RU_EVENT_SUBSCR"."SCOPE_DEFINITION_KEY_" IS 'SCOPE_DEFINITION_KEY_ 引擎属性，供 Flowable 运行时事件订阅表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_RU_EXECUTION" IS 'Flowable 运行时执行实例表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."BUSINESS_KEY_" IS '业务键，供应用按业务记录查找流程实例';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."PARENT_ID_" IS 'PARENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."SUPER_EXEC_" IS 'SUPER_EXEC_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."ROOT_PROC_INST_ID_" IS 'ROOT_PROC_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."ACT_ID_" IS 'ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."IS_ACTIVE_" IS 'IS_ACTIVE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."IS_CONCURRENT_" IS 'IS_CONCURRENT_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."IS_SCOPE_" IS 'IS_SCOPE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."IS_EVENT_SCOPE_" IS 'IS_EVENT_SCOPE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."IS_MI_ROOT_" IS 'IS_MI_ROOT_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."SUSPENSION_STATE_" IS '挂起状态，控制定义或实例能否继续执行';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."CACHED_ENT_STATE_" IS 'CACHED_ENT_STATE_ 引擎属性，供 Flowable 运行时执行实例表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."START_ACT_ID_" IS 'START_ACT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."START_TIME_" IS '开始时间，用于计算执行和处理时长';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."START_USER_ID_" IS 'START_USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."LOCK_TIME_" IS 'LOCK 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."IS_COUNT_ENABLED_" IS '计数开关，控制是否维护关联对象数量';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."EVT_SUBSCR_COUNT_" IS 'EVT_SUBSCR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."TASK_COUNT_" IS 'TASK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."JOB_COUNT_" IS 'JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."TIMER_JOB_COUNT_" IS 'TIMER_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."SUSP_JOB_COUNT_" IS 'SUSP_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."DEADLETTER_JOB_COUNT_" IS 'DEADLETTER_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."EXTERNAL_WORKER_JOB_COUNT_" IS 'EXTERNAL_WORKER_JOB 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."VAR_COUNT_" IS 'VAR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."ID_LINK_COUNT_" IS 'ID_LINK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."CALLBACK_ID_" IS 'CALLBACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."CALLBACK_TYPE_" IS '回调类型，决定异步结果处理方式';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."REFERENCE_ID_" IS 'REFERENCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."REFERENCE_TYPE_" IS '引用目标类型，供引擎解析关联对象';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."PROPAGATED_STAGE_INST_ID_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXECUTION"."BUSINESS_STATUS_" IS '业务状态，供引擎和应用同步实例进度';
COMMENT ON TABLE "ACT_RU_EXTERNAL_JOB" IS 'Flowable 运行时外部作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."LOCK_EXP_TIME_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."EXCLUSIVE_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."PROCESS_INSTANCE_ID_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."ELEMENT_NAME_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."CORRELATION_ID_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."RETRIES_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."EXCEPTION_STACK_ID_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."EXCEPTION_MSG_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."DUEDATE_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."REPEAT_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."HANDLER_TYPE_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."HANDLER_CFG_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."CUSTOM_VALUES_ID_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_EXTERNAL_JOB"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_RU_HISTORY_JOB" IS 'Flowable 运行时历史作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."LOCK_EXP_TIME_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."RETRIES_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."EXCEPTION_STACK_ID_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."EXCEPTION_MSG_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."HANDLER_TYPE_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."HANDLER_CFG_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."CUSTOM_VALUES_ID_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."ADV_HANDLER_CFG_ID_" IS 'ADV_HANDLER_CFG 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_HISTORY_JOB"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_RU_IDENTITYLINK" IS 'Flowable 运行时参与者关联表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."GROUP_ID_" IS 'GROUP 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."USER_ID_" IS 'USER 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_IDENTITYLINK"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "ACT_RU_JOB" IS 'Flowable 运行时作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_JOB"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_JOB"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_JOB"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RU_JOB"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_RU_JOB"."LOCK_EXP_TIME_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_JOB"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_RU_JOB"."EXCLUSIVE_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "ACT_RU_JOB"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_JOB"."PROCESS_INSTANCE_ID_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_JOB"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."ELEMENT_NAME_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "ACT_RU_JOB"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_JOB"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."CORRELATION_ID_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."RETRIES_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "ACT_RU_JOB"."EXCEPTION_STACK_ID_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."EXCEPTION_MSG_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "ACT_RU_JOB"."DUEDATE_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "ACT_RU_JOB"."REPEAT_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "ACT_RU_JOB"."HANDLER_TYPE_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "ACT_RU_JOB"."HANDLER_CFG_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "ACT_RU_JOB"."CUSTOM_VALUES_ID_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_JOB"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_JOB"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_RU_SUSPENDED_JOB" IS 'Flowable 运行时挂起作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."EXCLUSIVE_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."PROCESS_INSTANCE_ID_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."ELEMENT_NAME_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."CORRELATION_ID_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."RETRIES_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."EXCEPTION_STACK_ID_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."EXCEPTION_MSG_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."DUEDATE_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."REPEAT_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."HANDLER_TYPE_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."HANDLER_CFG_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."CUSTOM_VALUES_ID_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_SUSPENDED_JOB"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_RU_TASK" IS 'Flowable 运行时任务表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_TASK"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_TASK"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_TASK"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_TASK"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_RU_TASK"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_TASK"."TASK_DEF_ID_" IS 'TASK_DEF 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TASK"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TASK"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TASK"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_TASK"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TASK"."PROPAGATED_STAGE_INST_ID_" IS 'PROPAGATED_STAGE_INST 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TASK"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_RU_TASK"."PARENT_TASK_ID_" IS 'PARENT_TASK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TASK"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "ACT_RU_TASK"."TASK_DEF_KEY_" IS 'TASK_DEF_KEY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."OWNER_" IS 'OWNER_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."ASSIGNEE_" IS '办理人标识，供任务分派和待办查询';
COMMENT ON COLUMN "ACT_RU_TASK"."DELEGATION_" IS 'DELEGATION_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."PRIORITY_" IS 'PRIORITY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_TASK"."DUE_DATE_" IS 'DUE_DATE_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RU_TASK"."SUSPENSION_STATE_" IS '挂起状态，控制定义或实例能否继续执行';
COMMENT ON COLUMN "ACT_RU_TASK"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "ACT_RU_TASK"."FORM_KEY_" IS 'FORM_KEY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."CLAIM_TIME_" IS 'CLAIM 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_TASK"."IS_COUNT_ENABLED_" IS '计数开关，控制是否维护关联对象数量';
COMMENT ON COLUMN "ACT_RU_TASK"."VAR_COUNT_" IS 'VAR 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_TASK"."ID_LINK_COUNT_" IS 'ID_LINK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_TASK"."SUB_TASK_COUNT_" IS 'SUB_TASK 数量，供 Flowable 引擎统计运行状态';
COMMENT ON COLUMN "ACT_RU_TASK"."STATE_" IS '引擎状态，决定实例或作业后续可执行操作';
COMMENT ON COLUMN "ACT_RU_TASK"."IN_PROGRESS_TIME_" IS 'IN_PROGRESS 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_TASK"."IN_PROGRESS_STARTED_BY_" IS 'IN_PROGRESS_STARTED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."CLAIMED_BY_" IS 'CLAIMED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."SUSPENDED_TIME_" IS 'SUSPENDED 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_TASK"."SUSPENDED_BY_" IS 'SUSPENDED_BY_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON COLUMN "ACT_RU_TASK"."IN_PROGRESS_DUE_DATE_" IS 'IN_PROGRESS_DUE_DATE_ 引擎属性，供 Flowable 运行时任务表，保存引擎内部状态和关联数据在模型管理或实例执行时使用';
COMMENT ON TABLE "ACT_RU_TIMER_JOB" IS 'Flowable 运行时定时作业表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."LOCK_EXP_TIME_" IS 'LOCK_EXP 时间，供 Flowable 引擎排序和历史追踪';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."LOCK_OWNER_" IS '锁持有者，防止同一作业被多个执行器重复处理';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."EXCLUSIVE_" IS '独占执行标记，控制同一流程实例的作业并发';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."PROCESS_INSTANCE_ID_" IS 'PROCESS_INSTANCE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."PROC_DEF_ID_" IS '流程定义ID，关联已部署的流程模型';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."ELEMENT_ID_" IS 'ELEMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."ELEMENT_NAME_" IS '模型元素名称，供引擎历史记录展示';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."SCOPE_DEFINITION_ID_" IS 'SCOPE_DEFINITION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."CORRELATION_ID_" IS 'CORRELATION 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."RETRIES_" IS '剩余重试次数，失败后用于决定是否继续调度';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."EXCEPTION_STACK_ID_" IS 'EXCEPTION_STACK 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."EXCEPTION_MSG_" IS '最近异常信息，供失败作业排查与重试';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."DUEDATE_" IS '到期时间，用于定时作业调度';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."REPEAT_" IS '重复周期，控制定时作业的后续触发';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."HANDLER_TYPE_" IS '处理器类型，决定作业调用的执行逻辑';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."HANDLER_CFG_" IS '处理器配置，向作业执行逻辑传递参数';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."CUSTOM_VALUES_ID_" IS 'CUSTOM_VALUES 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "ACT_RU_TIMER_JOB"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "ACT_RU_VARIABLE" IS 'Flowable 运行时变量表，保存引擎内部状态和关联数据';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."EXECUTION_ID_" IS '执行实例ID，定位流程中的运行路径';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."PROC_INST_ID_" IS '流程实例ID，关联当前或历史流程';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."TASK_ID_" IS '任务ID，关联办理任务或任务历史';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."BYTEARRAY_ID_" IS 'BYTEARRAY 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."DOUBLE_" IS '浮点变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."LONG_" IS '整数变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."TEXT_" IS '文本变量值，供流程表达式和任务处理读取';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."TEXT2_" IS '第二段文本值，保存补充变量或序列化内容';
COMMENT ON COLUMN "ACT_RU_VARIABLE"."META_INFO_" IS '模型元数据，保存编辑器和发布需要的补充信息';
COMMENT ON TABLE "AUTH_LOGIN_THROTTLE" IS '登录失败限流表';
COMMENT ON COLUMN "AUTH_LOGIN_THROTTLE"."THROTTLE_KEY" IS '限流键。';
COMMENT ON COLUMN "AUTH_LOGIN_THROTTLE"."FAILURE_COUNT" IS '失败数量。';
COMMENT ON COLUMN "AUTH_LOGIN_THROTTLE"."WINDOW_STARTED_AT" IS '窗口开始时间。';
COMMENT ON COLUMN "AUTH_LOGIN_THROTTLE"."BLOCKED_UNTIL" IS '阻断截止时间。';
COMMENT ON COLUMN "AUTH_LOGIN_THROTTLE"."UPDATE_TIME" IS '更新时间。';
COMMENT ON TABLE "AUTH_REFRESH_SESSION" IS '浏览器刷新会话表';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."ID" IS '主键ID：刷新会话ID，同时写入Access Token的sid声明。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."USER_ID" IS '会话所属用户ID。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."REFRESH_TOKEN_HASH" IS '刷新令牌哈希：Refresh Token的SHA-256十六进制摘要。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."TOKEN_VERSION" IS '令牌版本：创建会话时的用户全局令牌版本。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."CREATE_TIME" IS '创建时间：会话创建时间。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."LAST_USED_AT" IS '最近使用时间：最近一次成功刷新时间。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."IDLE_EXPIRES_AT" IS '空闲过期时间：空闲超时时间。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."ABSOLUTE_EXPIRES_AT" IS '绝对过期时间：会话绝对过期时间。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."REVOKED_AT" IS '撤销时间：会话撤销时间。';
COMMENT ON COLUMN "AUTH_REFRESH_SESSION"."REVOKED_REASON" IS '会话撤销原因。';
COMMENT ON TABLE "CONFIG_ASSET_BASELINE" IS '配置资产环境基线表';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."ID" IS '主键ID：配置资产迁移基线。 记录某次成功导入发布后，源环境与目标环境的资产版本/内容哈希对照关系， 用于后续导入时判断生产环境是否相对基线发生了本地修改，从而识别冲突或增量更新。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."ASSET_TYPE" IS '资产类型。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."BUSINESS_KEY" IS '业务键。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."SCOPE_KEY" IS '范围键。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."SOURCE_VERSION" IS '来源版本。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."SOURCE_HASH" IS '来源哈希。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."TARGET_VERSION" IS '目标版本。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."TARGET_HASH" IS '目标哈希。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."IMPORT_PACKAGE_ID" IS '导入配置包ID。';
COMMENT ON COLUMN "CONFIG_ASSET_BASELINE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "CONFIG_ENVIRONMENT_MAPPING" IS '配置环境映射表';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."ID" IS '主键ID：配置环境映射。 记录源环境与目标环境之间的资源编码映射关系(如用户名、角色、部门、数据源等)， 在导入分析阶段用于将源环境的依赖键解析为目标环境对应的键。';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."SOURCE_TYPE" IS '来源类型。';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."SOURCE_KEY" IS '来源标识。';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."TARGET_KEY" IS '目标标识。';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."DESCRIPTION" IS '说明。';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "CONFIG_ENVIRONMENT_MAPPING"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "CONFIG_EXPORT_PACKAGE" IS '配置导出包表';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."ID" IS '主键ID：配置导出包。 记录从当前环境打包生成的 wfpack 发布包元数据与二进制内容， 包括包编号、校验和、HMAC 签名、资产数量与下载统计等。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."PACKAGE_NO" IS '配置包编号。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."MIGRATION_TAG" IS '迁移标记。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."FILE_NAME" IS '文件名称。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."CHECKSUM" IS '校验和：内容校验和，用于检测迁移或配置包内容变化；不同表算法以所属服务为准。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."SIGNATURE_VALUE" IS '签名值。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."STATUS" IS '状态。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."ASSET_COUNT" IS '资产数量。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."PACKAGE_DATA" IS '配置包数据。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."DOWNLOAD_COUNT" IS '下载数量。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."LAST_DOWNLOAD_AT" IS '最近下载时间。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "CONFIG_EXPORT_PACKAGE_ITEM" IS '配置导出包条目表';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."ID" IS '主键ID：配置导出包条目。 记录一个导出包中包含的每个迁移资产及其在导出时使用的选择配置， 用于追溯导出包的资产清单与快照选择范围。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."PACKAGE_ID" IS '配置包ID。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."ASSET_ID" IS '资产ID。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."ASSET_TYPE" IS '资产类型。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."BUSINESS_KEY" IS '业务键。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."SOURCE_VERSION" IS '来源版本。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."CONTENT_HASH" IS '内容哈希：内容摘要，用于检查配置或快照内容是否一致。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."SELECTION_JSON" IS '选择JSON。';
COMMENT ON COLUMN "CONFIG_EXPORT_PACKAGE_ITEM"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "CONFIG_IMPORT_ITEM" IS '配置导入条目表';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."ID" IS '主键ID：配置导入条目。 记录导入批次中单个资产的导入全过程状态，包括源/目标版本对照、 比较结果(NEW/CONSISTENT/CONFLICT等)、依赖映射状态、发布状态及异常信息。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."IMPORT_PACKAGE_ID" IS '导入配置包ID。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."ASSET_TYPE" IS '资产类型。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."BUSINESS_KEY" IS '业务键。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."ASSET_NAME" IS '资产名称。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."SOURCE_VERSION" IS '来源版本。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."SOURCE_HASH" IS '来源哈希。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."TARGET_BEFORE_VERSION" IS '目标之前版本。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."TARGET_BEFORE_HASH" IS '目标之前哈希。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."TARGET_AFTER_VERSION" IS '目标之后版本。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."TARGET_AFTER_HASH" IS '目标之后哈希。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."COMPARISON_STATUS" IS '比较状态。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."MAPPING_STATUS" IS '映射状态。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."PUBLISH_STATUS" IS '发布状态。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."SNAPSHOT_JSON" IS '快照JSON。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."DEPENDENCIES_JSON" IS '依赖集合JSON。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."ERROR_MESSAGE" IS '错误消息。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "CONFIG_IMPORT_ITEM"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "CONFIG_IMPORT_PACKAGE" IS '配置导入包表';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."ID" IS '主键ID：配置导入批次。 记录从源环境导出的 wfpack 发布包导入到目标环境后的批次信息， 包括原始包内容、校验结果、分析/发布/回滚状态流转以及操作人记录。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."PACKAGE_NO" IS '配置包编号。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."SOURCE_ENVIRONMENT" IS '来源环境。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."MIGRATION_TAG" IS '迁移标记。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."FILE_NAME" IS '文件名称。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."CHECKSUM" IS '校验和：内容校验和，用于检测迁移或配置包内容变化；不同表算法以所属服务为准。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."STATUS" IS '状态。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."VALIDATION_REPORT_JSON" IS '校验报告JSON。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."PACKAGE_DATA" IS '配置包数据。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."IMPORTED_BY" IS '导入人员。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."IMPORTED_AT" IS '导入时间。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."PUBLISHED_BY" IS '发布人。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."PUBLISHED_AT" IS '发布时间。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."ERROR_MESSAGE" IS '错误消息。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."DELETED" IS '逻辑删除标记。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."SIGNATURE_STATUS" IS '来源验签状态：导入时记录来源校验结论：VERIFIED 表示签名通过，MISMATCH_CONFIRMED 表示签名不一致但已人工确认来源，UNKNOWN 表示历史记录没有验签证据。与分析、发布状态分别保存，后续查看导入批次时据此说明包的来源可信程度。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."SIGNATURE_CONFIRMED_BY" IS '来源确认人：签名不一致时，由服务端记录确认来源的导入账号；用于追溯谁接受了该配置包。签名通过的正常导入不填写。';
COMMENT ON COLUMN "CONFIG_IMPORT_PACKAGE"."SIGNATURE_CONFIRMED_AT" IS '来源确认时间：与来源确认人同时记录，保留人工确认的时间；后续重新分析配置差异不会覆盖这条确认记录。';
COMMENT ON TABLE "CONFIG_MIGRATION_ASSET" IS '配置迁移发布资产表';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."ID" IS '主键ID：配置迁移资产。 记录实体/流程配置在每次发布时生成的可迁移快照版本，包含快照内容、内容哈希、 依赖清单、快照完整度(PARTIAL/COMPLETE)以及导出标记与统计，是配置迁移的核心实体。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."ASSET_TYPE" IS '资产类型。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."BUSINESS_KEY" IS '业务键。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."ASSET_NAME" IS '资产名称。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."SOURCE_HISTORY_ID" IS '来源历史ID。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."SOURCE_VERSION" IS '来源版本。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."VERSION_DESCRIPTION" IS '版本说明。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."MIGRATION_TAG" IS '迁移标记。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."MARK_FOR_EXPORT" IS '标记用于导出。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."SNAPSHOT_COMPLETENESS" IS '快照完整性。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."SNAPSHOT_SCHEMA_VERSION" IS '快照结构版本。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."SNAPSHOT_JSON" IS '快照JSON。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."CONTENT_HASH" IS '内容哈希：内容摘要，用于检查配置或快照内容是否一致。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."DEPENDENCIES_JSON" IS '依赖集合JSON。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."DEPENDENCY_COUNT" IS '依赖数量。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."MISSING_DEPENDENCY_COUNT" IS '缺失依赖数量。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."EXPORT_STATUS" IS '导出状态。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."PUBLISHED_AT" IS '发布时间。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."PUBLISHED_BY" IS '发布人。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."LAST_EXPORT_AT" IS '最近导出时间。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."EXPORT_COUNT" IS '导出数量。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "CONFIG_MIGRATION_ASSET_DEPENDENCY" IS '配置迁移资产依赖表';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."ID" IS '主键ID：配置迁移资产依赖。 记录某个迁移资产在导入/导出时依赖的其他资产或资源(实体、流程、表单、用户、字典等)， 用于依赖解析、阻断项分析与导出包完整性校验。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."ASSET_ID" IS '资产ID。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."DEPENDENCY_TYPE" IS '依赖类型。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."DEPENDENCY_KEY" IS '依赖键。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."REQUIRED" IS '是否必需。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."SOURCE_DESCRIPTION" IS '来源说明。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."DEPENDENCY_DOCUMENT" IS '依赖扩展JSON文档。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."SOURCE_ASSET_TYPE" IS '来源资产类型冗余。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."SOURCE_BUSINESS_KEY" IS '来源资产稳定标识冗余。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."SOURCE_VERSION" IS '来源发布版本。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."REFERENCE_LOCATION" IS '引用在配置中的稳定位置。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."DEPENDENCY_STRENGTH" IS '依赖强度。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."PARSE_STATUS" IS '解析状态。';
COMMENT ON COLUMN "CONFIG_MIGRATION_ASSET_DEPENDENCY"."EXTRACTED_AT" IS '引用抽取时间。';
COMMENT ON TABLE "EMBED_ALLOWED_ORIGIN" IS '嵌入父页面来源表';
COMMENT ON COLUMN "EMBED_ALLOWED_ORIGIN"."GRANT_ID" IS '授权ID。';
COMMENT ON COLUMN "EMBED_ALLOWED_ORIGIN"."ORIGIN" IS '来源。';
COMMENT ON COLUMN "EMBED_ALLOWED_ORIGIN"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "EMBED_APPLICATION_GRANT" IS '嵌入视图应用授权表';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."VIEW_ID" IS '视图ID。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."IDENTITY_PROVIDER_ID" IS '身份提供方ID。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."STATUS" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."TRUSTED_SUBJECT_ASSERTION" IS '是否信任人员断言；CHECK 枚举：0,1。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."REVISION_MODE" IS '修订号模式。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."PINNED_REVISION" IS '固定修订号。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."CAPABILITY_CEILING_JSON" IS '能力上限JSON。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."MAX_ACTIVE_SESSIONS_PER_USER" IS '每用户最大活跃会话数。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."MAX_SESSION_SECONDS" IS '会话最长秒数。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."LAUNCH_LIMIT_PER_MINUTE" IS '每分钟启动上限。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."RUNTIME_LIMIT_PER_MINUTE" IS '每分钟运行请求上限。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."MAX_CONCURRENCY" IS '最大并发数。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."LOCK_VERSION" IS '锁版本。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."SECURITY_VERSION" IS '安全版本。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."CREATE_BY" IS '创建人。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."UPDATE_BY" IS '修改人。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."REVOKED_BY" IS '撤销人。';
COMMENT ON COLUMN "EMBED_APPLICATION_GRANT"."REVOKED_AT" IS '撤销时间。';
COMMENT ON TABLE "EMBED_ASSERTION_REPLAY" IS '人员断言防重放表';
COMMENT ON COLUMN "EMBED_ASSERTION_REPLAY"."PROVIDER_ID" IS '提供方ID。';
COMMENT ON COLUMN "EMBED_ASSERTION_REPLAY"."JTI_DIGEST" IS 'jti摘要。';
COMMENT ON COLUMN "EMBED_ASSERTION_REPLAY"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "EMBED_ASSERTION_REPLAY"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_ASSERTION_REPLAY"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "EMBED_EXTERNAL_IDENTITY_BINDING" IS '外部主体用户绑定表';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."IDENTITY_PROVIDER_ID" IS '身份提供方ID。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."SUBJECT_DIGEST" IS '主体摘要。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."SUBJECT_DIGEST_KEY_VERSION" IS '主体摘要键版本。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."SUBJECT_HINT" IS '主体提示。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."FLOW_USER_ID" IS 'Flow用户ID。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."STATUS" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."BINDING_VERSION" IS '绑定版本。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."EFFECTIVE_AT" IS '生效时间。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."CREATE_BY" IS '创建人。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."UPDATE_BY" IS '修改人。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."REVOKED_BY" IS '撤销人。';
COMMENT ON COLUMN "EMBED_EXTERNAL_IDENTITY_BINDING"."REVOKED_AT" IS '撤销时间。';
COMMENT ON TABLE "EMBED_IDENTITY_PROVIDER" IS '嵌入身份提供方表';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."NAME" IS '名称。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."TYPE" IS '类型；CHECK 枚举：\\''SIGNED_JWT\\'',\\''TRUSTED_EXTERNAL_ID\\''。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."STATUS" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."ISSUER" IS '发行者。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."ISSUER_UNIQUENESS_KEY" IS '发行者唯一性键；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."SUBJECT_NAMESPACE" IS '主体命名空间。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."AUDIENCES_JSON" IS '受众集合JSON。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."ALGORITHMS_JSON" IS '算法集合JSON。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."JWKS_MODE" IS 'JWKS模式；CHECK 枚举：\\''STATIC_JWK_SET\\'',\\''REMOTE_JWKS\\''。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."JWKS_JSON" IS 'JWKSJSON。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."JWKS_URL" IS 'JWKSURL。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."CLOCK_SKEW_SECONDS" IS '允许时钟偏差秒数。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."MAX_ASSERTION_LIFETIME_SECONDS" IS '断言最长有效秒数。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."KEY_VERSION" IS '键版本。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."LOCK_VERSION" IS '锁版本。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."SECURITY_VERSION" IS '安全版本。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."CREATE_BY" IS '创建人。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."UPDATE_BY" IS '修改人。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."REVOKED_BY" IS '撤销人。';
COMMENT ON COLUMN "EMBED_IDENTITY_PROVIDER"."REVOKED_AT" IS '撤销时间。';
COMMENT ON TABLE "EMBED_LAUNCH" IS '嵌入一次性启动凭据表';
COMMENT ON COLUMN "EMBED_LAUNCH"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."GRANT_ID" IS '授权ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."VIEW_ID" IS '视图ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."VIEW_RELEASE_ID" IS '视图发布ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."IDENTITY_PROVIDER_ID" IS '身份提供方ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."PROVIDER_SECURITY_VERSION" IS '提供方安全版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."APPLICATION_VERSION" IS '应用版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."GRANT_SECURITY_VERSION" IS '授权安全版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."VIEW_SECURITY_VERSION" IS '视图安全版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."FLOW_USER_ID" IS 'Flow用户ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."IDENTITY_BINDING_ID" IS '身份绑定ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."BINDING_VERSION" IS '绑定版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."SUBJECT_DIGEST" IS '主体摘要。';
COMMENT ON COLUMN "EMBED_LAUNCH"."SUBJECT_DIGEST_KEY_VERSION" IS '主体摘要键版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."PARENT_ORIGIN" IS '父级来源。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CHANNEL_ID" IS '通道ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."ENTRY_MODE" IS '入口模式；CHECK 枚举：\\''LIST\\'',\\''CREATE\\'' / \\''VIEW\\'',\\''EDIT\\''。';
COMMENT ON COLUMN "EMBED_LAUNCH"."RECORD_ID" IS '记录ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CONTEXT_CIPHERTEXT" IS '上下文密文。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CONTEXT_CIPHER_KEY_VERSION" IS '上下文加密键版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CONTEXT_DIGEST" IS '上下文摘要。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CONTEXT_DIGEST_KEY_VERSION" IS '上下文摘要键版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."UI_LOCALE" IS '界面语言区域。';
COMMENT ON COLUMN "EMBED_LAUNCH"."UI_THEME" IS '界面主题；CHECK 枚举：\\''light\\'',\\''dark\\'',\\''system\\''。';
COMMENT ON COLUMN "EMBED_LAUNCH"."UI_FORM_PRESENTATION" IS '表单展示方式：seamless 或 dialog，默认 seamless；CHECK 枚举：\\''seamless\\'',\\''dialog\\''；迁移声明排序规则 utf8mb4_bin，标准迁移器完成后再统一为 utf8mb4_unicode_ci。';
COMMENT ON COLUMN "EMBED_LAUNCH"."LAUNCH_CODE_DIGEST" IS '启动编码摘要。';
COMMENT ON COLUMN "EMBED_LAUNCH"."STATUS" IS '状态；CHECK 枚举：\\''ISSUED\\'',\\''CONSUMED\\'',\\''EXPIRED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "EMBED_LAUNCH"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CONSUMED_AT" IS '消费时间。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CONSUMED_SESSION_ID" IS '消费会话ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."REVOKED_AT" IS '撤销时间。';
COMMENT ON COLUMN "EMBED_LAUNCH"."TRACE_ID" IS '追踪ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."REQUEST_ID" IS '请求ID。';
COMMENT ON COLUMN "EMBED_LAUNCH"."SOURCE_IP_DIGEST" IS '来源IP摘要。';
COMMENT ON COLUMN "EMBED_LAUNCH"."SOURCE_IP_DIGEST_KEY_VERSION" IS '来源IP摘要键版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."USER_AGENT_DIGEST" IS 'User-Agent摘要。';
COMMENT ON COLUMN "EMBED_LAUNCH"."USER_AGENT_DIGEST_KEY_VERSION" IS '用户代理信息摘要键版本。';
COMMENT ON COLUMN "EMBED_LAUNCH"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_LAUNCH"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "EMBED_OPERATION_RECEIPT" IS '嵌入写操作回执表';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."IDEMPOTENCY_RECORD_ID" IS '幂等记录ID。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."OPERATION" IS '操作；CHECK 枚举： \\''EMBED_RECORD_CREATE\\'',\\''EMBED_RECORD_UPDATE\\'',\\''EMBED_ACTION_EXECUTE\\''。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."ACTOR_SCOPE_DIGEST" IS '操作主体范围摘要。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."VIEW_KEY" IS '视图键。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."TARGET_TYPE" IS '目标类型。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."TARGET_ID" IS '目标ID。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."OUTCOME_CODE" IS '结果编码。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."RECORD_VERSION" IS '记录版本。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."RESULT_SUMMARY_JSON" IS '结果摘要JSON。';
COMMENT ON COLUMN "EMBED_OPERATION_RECEIPT"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "EMBED_SESSION" IS '嵌入运行会话表';
COMMENT ON COLUMN "EMBED_SESSION"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_SESSION"."SESSION_TOKEN_DIGEST" IS '会话令牌摘要。';
COMMENT ON COLUMN "EMBED_SESSION"."LAUNCH_ID" IS '启动ID。';
COMMENT ON COLUMN "EMBED_SESSION"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "EMBED_SESSION"."GRANT_ID" IS '授权ID。';
COMMENT ON COLUMN "EMBED_SESSION"."VIEW_ID" IS '视图ID。';
COMMENT ON COLUMN "EMBED_SESSION"."VIEW_RELEASE_ID" IS '视图发布ID。';
COMMENT ON COLUMN "EMBED_SESSION"."IDENTITY_PROVIDER_ID" IS '身份提供方ID。';
COMMENT ON COLUMN "EMBED_SESSION"."PROVIDER_SECURITY_VERSION" IS '提供方安全版本。';
COMMENT ON COLUMN "EMBED_SESSION"."FLOW_USER_ID" IS 'Flow用户ID。';
COMMENT ON COLUMN "EMBED_SESSION"."IDENTITY_BINDING_ID" IS '身份绑定ID。';
COMMENT ON COLUMN "EMBED_SESSION"."BINDING_VERSION" IS '绑定版本。';
COMMENT ON COLUMN "EMBED_SESSION"."PARENT_ORIGIN" IS '父级来源。';
COMMENT ON COLUMN "EMBED_SESSION"."CHANNEL_ID" IS '通道ID。';
COMMENT ON COLUMN "EMBED_SESSION"."ENTRY_MODE" IS '入口模式；CHECK 枚举：\\''LIST\\'',\\''CREATE\\'' / \\''VIEW\\'',\\''EDIT\\''。';
COMMENT ON COLUMN "EMBED_SESSION"."RECORD_ID" IS '记录ID。';
COMMENT ON COLUMN "EMBED_SESSION"."PARENT_NONCE_DIGEST" IS '父级随机数摘要。';
COMMENT ON COLUMN "EMBED_SESSION"."CHILD_NONCE_DIGEST" IS '子级随机数摘要。';
COMMENT ON COLUMN "EMBED_SESSION"."CONTEXT_CIPHERTEXT" IS '上下文密文。';
COMMENT ON COLUMN "EMBED_SESSION"."CONTEXT_CIPHER_KEY_VERSION" IS '上下文加密键版本。';
COMMENT ON COLUMN "EMBED_SESSION"."CONTEXT_DIGEST" IS '上下文摘要。';
COMMENT ON COLUMN "EMBED_SESSION"."CONTEXT_DIGEST_KEY_VERSION" IS '上下文摘要键版本。';
COMMENT ON COLUMN "EMBED_SESSION"."UI_LOCALE" IS '界面语言区域。';
COMMENT ON COLUMN "EMBED_SESSION"."UI_THEME" IS '界面主题；CHECK 枚举：\\''light\\'',\\''dark\\'',\\''system\\''。';
COMMENT ON COLUMN "EMBED_SESSION"."UI_FORM_PRESENTATION" IS '表单展示方式：seamless 或 dialog，默认 seamless；CHECK 枚举：\\''seamless\\'',\\''dialog\\''；迁移声明排序规则 utf8mb4_bin，标准迁移器完成后再统一为 utf8mb4_unicode_ci。';
COMMENT ON COLUMN "EMBED_SESSION"."CAPABILITY_SNAPSHOT_JSON" IS '能力快照JSON。';
COMMENT ON COLUMN "EMBED_SESSION"."APPLICATION_VERSION" IS '应用版本。';
COMMENT ON COLUMN "EMBED_SESSION"."GRANT_SECURITY_VERSION" IS '授权安全版本。';
COMMENT ON COLUMN "EMBED_SESSION"."VIEW_SECURITY_VERSION" IS '视图安全版本。';
COMMENT ON COLUMN "EMBED_SESSION"."STATUS" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''LOGGED_OUT\\'',\\''EXPIRED\\'',\\''REVOKED\\'' / \\''LOGGED_OUT\\'',\\''EXPIRED\\''。';
COMMENT ON COLUMN "EMBED_SESSION"."SLOT_RELEASED" IS '会话名额是否已释放。';
COMMENT ON COLUMN "EMBED_SESSION"."SLOT_RELEASED_AT" IS '会话名额释放时间。';
COMMENT ON COLUMN "EMBED_SESSION"."ISSUED_AT" IS '签发时间。';
COMMENT ON COLUMN "EMBED_SESSION"."LAST_SEEN_AT" IS '最近活动时间。';
COMMENT ON COLUMN "EMBED_SESSION"."IDLE_EXPIRES_AT" IS '空闲过期时间。';
COMMENT ON COLUMN "EMBED_SESSION"."ABSOLUTE_EXPIRES_AT" IS '绝对过期时间。';
COMMENT ON COLUMN "EMBED_SESSION"."REVOKED_AT" IS '撤销时间。';
COMMENT ON COLUMN "EMBED_SESSION"."REVOKE_REASON" IS '撤销原因。';
COMMENT ON COLUMN "EMBED_SESSION"."SOURCE_IP_DIGEST" IS '来源IP摘要。';
COMMENT ON COLUMN "EMBED_SESSION"."SOURCE_IP_DIGEST_KEY_VERSION" IS '来源IP摘要键版本。';
COMMENT ON COLUMN "EMBED_SESSION"."USER_AGENT_DIGEST" IS 'User-Agent摘要。';
COMMENT ON COLUMN "EMBED_SESSION"."USER_AGENT_DIGEST_KEY_VERSION" IS '用户代理信息摘要键版本。';
COMMENT ON COLUMN "EMBED_SESSION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_SESSION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "EMBED_SESSION_COUNTER" IS '嵌入活跃会话计数表';
COMMENT ON COLUMN "EMBED_SESSION_COUNTER"."GRANT_ID" IS '授权ID。';
COMMENT ON COLUMN "EMBED_SESSION_COUNTER"."FLOW_USER_ID" IS 'Flow用户ID。';
COMMENT ON COLUMN "EMBED_SESSION_COUNTER"."ACTIVE_COUNT" IS '活跃数量。';
COMMENT ON COLUMN "EMBED_SESSION_COUNTER"."LOCK_VERSION" IS '锁版本。';
COMMENT ON COLUMN "EMBED_SESSION_COUNTER"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_SESSION_COUNTER"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "EMBED_VIEW" IS '嵌入视图草稿表';
COMMENT ON COLUMN "EMBED_VIEW"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_VIEW"."VIEW_KEY" IS '视图键。';
COMMENT ON COLUMN "EMBED_VIEW"."NAME" IS '名称。';
COMMENT ON COLUMN "EMBED_VIEW"."DESCRIPTION" IS '说明。';
COMMENT ON COLUMN "EMBED_VIEW"."SURFACE_TYPE" IS '界面类型；CHECK 枚举：\\''LIST\\'',\\''FORM\\''。';
COMMENT ON COLUMN "EMBED_VIEW"."STATUS" IS '状态；CHECK 枚举：\\''DRAFT\\'',\\''ACTIVE\\'',\\''DISABLED\\'',\\''RETIRED\\''。';
COMMENT ON COLUMN "EMBED_VIEW"."DRAFT_CONFIG_JSON" IS '草稿配置JSON。';
COMMENT ON COLUMN "EMBED_VIEW"."DRAFT_REVISION" IS '草稿修订号。';
COMMENT ON COLUMN "EMBED_VIEW"."PUBLISHED_RELEASE_ID" IS '发布发布ID。';
COMMENT ON COLUMN "EMBED_VIEW"."LOCK_VERSION" IS '锁版本。';
COMMENT ON COLUMN "EMBED_VIEW"."SECURITY_VERSION" IS '安全版本。';
COMMENT ON COLUMN "EMBED_VIEW"."CREATE_BY" IS '创建人。';
COMMENT ON COLUMN "EMBED_VIEW"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_VIEW"."UPDATE_BY" IS '修改人。';
COMMENT ON COLUMN "EMBED_VIEW"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "EMBED_VIEW_RELEASE" IS '嵌入视图发布快照表';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."ID" IS '主键ID。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."VIEW_ID" IS '视图ID。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."REVISION" IS '修订号。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."SURFACE_TYPE" IS '界面类型；CHECK 枚举：\\''LIST\\'',\\''FORM\\''。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."LIST_KEY" IS '列表键。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."DEFAULT_FORM_ID" IS '默认表单ID。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."LIST_RELEASE_ID" IS '列表发布ID。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."LIST_RELEASE_VERSION" IS '列表发布版本。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."FORM_RELEASE_ID" IS '表单发布ID。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."FORM_RELEASE_VERSION" IS '表单发布版本。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."ENTRY_MODES_JSON" IS '入口模式JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."CAPABILITIES_JSON" IS '能力集合JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."FIELD_POLICY_JSON" IS '字段策略JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."ACTION_POLICY_JSON" IS '动作策略JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."CONTEXT_SCHEMA_JSON" IS '上下文结构JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."CONTEXT_BINDINGS_JSON" IS '上下文绑定集合JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."UI_CONFIG_JSON" IS '界面配置JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."CONFIG_JSON" IS '配置JSON。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."CONFIG_HASH" IS '配置哈希。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."RELEASE_NOTE" IS '发布说明。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."PUBLISHED_BY" IS '发布人。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."PUBLISHED_AT" IS '发布时间。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "EMBED_VIEW_RELEASE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "ENTITY_CODE_RULE" IS '实体业务编码规则表';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."PREFIX" IS '编码前缀，如：CG、DD。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."DATE_FORMAT" IS '日期格式，如：yyyyMMdd、yyyy-MM-dd。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."SEQ_LENGTH" IS '序号长度：序列号位数，如：6表示000001。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."SEQ_TYPE" IS '序号重置类型：序列号重置周期：DAY按天、MONTH按月、YEAR按年、NEVER不重置。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."CURRENT_SEQ" IS '当前序号：当前序列号值。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."SEQ_DATE" IS '序号所属日期：当前序列号对应的日期（用于判断重置）。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."EXAMPLE" IS '编码示例。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."GENERATION_MODE" IS '编码生成模式：RULE 使用内置规则，CUSTOM 调用登记的生成器；运行时按此选择策略';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."GENERATOR_CODE" IS '自定义生成器编码，CUSTOM 模式下用于定位扩展实现';
COMMENT ON COLUMN "ENTITY_CODE_RULE"."GENERATOR_CONFIG" IS '自定义生成器参数文档，调用扩展实现时作为输入并由应用校验';
COMMENT ON TABLE "ENTITY_DEFINITION" IS '实体定义表';
COMMENT ON COLUMN "ENTITY_DEFINITION"."ID" IS '主键ID，供实体定义表中的记录关联；由数据库分配';
COMMENT ON COLUMN "ENTITY_DEFINITION"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."ENTITY_NAME" IS '实体名称。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."DESCRIPTION" IS '说明：实体描述。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."PROCESS_DEFINITION_ID" IS '关联流程定义ID。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."STATUS" IS '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."TABLE_NAME" IS '数据库表名。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."LIFECYCLE_MODE" IS '实体生命周期模式：STANDALONE/WORKFLOW。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."STORAGE_MODE" IS '存储模式：DYNAMIC/SYSTEM。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."UPDATED_BY" IS '修改人：更新人。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."TEAM_VISIBILITY_ENABLED" IS '团队可见性开关：是否允许数据参与团队查看记录；团队可见性的旧开关不再参与权限引擎计算；当前改用列表 TEAM 规则，字段仍被保存和发布快照复制。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."TEAM_VISIBILITY_LEVEL" IS '团队可见性级别：参与团队权限级别：ADDITIVE/OVERRIDE_SCOPE/ABSOLUTE；团队可见性的旧开关不再参与权限引擎计算；当前改用列表 TEAM 规则，字段仍被保存和发布快照复制。';
COMMENT ON COLUMN "ENTITY_DEFINITION"."ACTIVE_PROCESS_DEFINITION_KEY" IS '有效流程绑定索引键；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "ENTITY_FIELD" IS '实体字段定义表';
COMMENT ON COLUMN "ENTITY_FIELD"."ID" IS '主键ID，供实体字段定义表中的记录关联；由数据库分配';
COMMENT ON COLUMN "ENTITY_FIELD"."ENTITY_ID" IS '所属实体ID。';
COMMENT ON COLUMN "ENTITY_FIELD"."FIELD_CODE" IS '字段编码。';
COMMENT ON COLUMN "ENTITY_FIELD"."FIELD_NAME" IS '字段名称。';
COMMENT ON COLUMN "ENTITY_FIELD"."FIELD_TYPE" IS '字段类型。';
COMMENT ON COLUMN "ENTITY_FIELD"."DB_TYPE" IS '数据库字段类型。';
COMMENT ON COLUMN "ENTITY_FIELD"."FIELD_LENGTH" IS '字段长度。';
COMMENT ON COLUMN "ENTITY_FIELD"."IS_REQUIRED" IS '是否必填。';
COMMENT ON COLUMN "ENTITY_FIELD"."IS_UNIQUE" IS '是否唯一。';
COMMENT ON COLUMN "ENTITY_FIELD"."DEFAULT_VALUE" IS '默认值。';
COMMENT ON COLUMN "ENTITY_FIELD"."OPTIONS_JSON" IS '选项配置JSON。';
COMMENT ON COLUMN "ENTITY_FIELD"."VALIDATE_RULES" IS '验证规则JSON。';
COMMENT ON COLUMN "ENTITY_FIELD"."SORT_ORDER" IS '排序号：排序顺序。';
COMMENT ON COLUMN "ENTITY_FIELD"."IS_SYSTEM" IS '是否系统字段：0-否 1-是（系统自动添加的字段，不可删除）。';
COMMENT ON COLUMN "ENTITY_FIELD"."IS_PUBLISHED" IS '是否已发布到数据库表。';
COMMENT ON COLUMN "ENTITY_FIELD"."EDITABLE" IS '是否可编辑：0-否 1-是。';
COMMENT ON COLUMN "ENTITY_FIELD"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_FIELD"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_FIELD"."FIELD_PRECISION" IS '小数位数（精度）。';
COMMENT ON COLUMN "ENTITY_FIELD"."DB_COLUMN_NAME" IS '数据库列名（下划线命名）。';
COMMENT ON COLUMN "ENTITY_FIELD"."FILE_TYPES" IS '文件类型限制（用于附件类型，如：.jpg,.png,.pdf）。';
COMMENT ON COLUMN "ENTITY_FIELD"."FILE_MAX_SIZE" IS '文件大小限制（MB，用于附件类型）。';
COMMENT ON COLUMN "ENTITY_FIELD"."FILE_MAX_COUNT" IS '文件数量限制（用于附件类型）。';
COMMENT ON COLUMN "ENTITY_FIELD"."REF_ENTITY_TYPE" IS '引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）。';
COMMENT ON COLUMN "ENTITY_FIELD"."REF_ENTITY_ID" IS '关联实体ID。';
COMMENT ON COLUMN "ENTITY_FIELD"."REF_FIELD_CODE" IS '关联字段编码。';
COMMENT ON COLUMN "ENTITY_FIELD"."REF_LIST_KEY" IS '子列表或实体引用默认使用的已发布列表编码。';
COMMENT ON COLUMN "ENTITY_FIELD"."FIELD_ID" IS '旧字段编码（兼容保留）；旧字段编码；迁移注释明确为兼容保留，不是该行主键。';
COMMENT ON COLUMN "ENTITY_FIELD"."DICT_TYPE" IS '绑定的系统代码表编码。';
COMMENT ON COLUMN "ENTITY_FIELD"."VALUE_STORAGE" IS '值存储方式：字段值存储：SCALAR/MULTI_TABLE。';
COMMENT ON COLUMN "ENTITY_FIELD"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "ENTITY_FIELD_FILE_ITEM" IS '实体字段附件项表';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."FIELD_ID" IS '关联字段ID（entity_field.id）。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."ITEM_KEY" IS '附件项不可变业务标识。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."ITEM_NAME" IS '附件项名称。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."NAME_ALIASES" IS '历史附件项名称 JSON 数组；用于历史附件项名称归并到稳定 item_key，仍有业务用途。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."IS_REQUIRED" IS '是否必填：该附件项是否必填。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."FILE_TYPES" IS '允许的文件类型。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."MAX_SIZE" IS '单文件大小限制（MB）。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."MAX_COUNT" IS '文件数量限制。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_FIELD_FILE_ITEM"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_FIELD_OPTION" IS '实体字段静态选项表';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."FIELD_ID" IS '所属字段ID。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."OPTION_VALUE" IS '选项值（提交时存储的实际值）。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."OPTION_LABEL" IS '选项标签（界面显示文案）。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."STYLE_TYPE" IS '选项样式类型（如 primary/success/danger 等）。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."DISABLED" IS '是否禁用（true-禁用不可选）。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."OPTION_DOCUMENT" IS '选项扩展JSON文档。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_FIELD_OPTION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_FORM" IS '实体表单定义表';
COMMENT ON COLUMN "ENTITY_FORM"."ID" IS '主键ID：表单ID。';
COMMENT ON COLUMN "ENTITY_FORM"."ENTITY_ID" IS '实体ID。';
COMMENT ON COLUMN "ENTITY_FORM"."FORM_NAME" IS '表单名称。';
COMMENT ON COLUMN "ENTITY_FORM"."FORM_KEY" IS '表单标识。';
COMMENT ON COLUMN "ENTITY_FORM"."DESCRIPTION" IS '说明：描述。';
COMMENT ON COLUMN "ENTITY_FORM"."LAYOUT_TYPE" IS '布局类型：vertical-垂直 horizontal-水平 grid-网格。';
COMMENT ON COLUMN "ENTITY_FORM"."STATUS" IS '状态：0-禁用 1-启用。';
COMMENT ON COLUMN "ENTITY_FORM"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_FORM"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_FORM"."DELETED" IS '逻辑删除标记：删除标志。';
COMMENT ON COLUMN "ENTITY_FORM"."IS_DEFAULT" IS '是否默认表单。';
COMMENT ON COLUMN "ENTITY_FORM"."CUSTOM_COMPONENT" IS '自定义表单组件注册名。';
COMMENT ON COLUMN "ENTITY_FORM"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "ENTITY_FORM"."UPDATED_BY" IS '修改人：更新人。';
COMMENT ON COLUMN "ENTITY_FORM"."VIEW_CONFIG" IS '表单视图配置JSON：布局、自定义组件参数。';
COMMENT ON COLUMN "ENTITY_FORM"."REVISION" IS '修订号：草稿元数据修订号。';
COMMENT ON COLUMN "ENTITY_FORM"."ACTIVE_RELEASE_ID" IS '当前激活发布快照ID。';
COMMENT ON COLUMN "ENTITY_FORM"."DRAFT_HASH" IS '当前草稿内容哈希。';
COMMENT ON COLUMN "ENTITY_FORM"."CUSTOM_COMPONENT_VERSION" IS '自定义整页表单组件锁定版本。';
COMMENT ON COLUMN "ENTITY_FORM"."CUSTOM_COMPONENT_SNAPSHOT_VERSION" IS '自定义整页表单配置快照版本。';
COMMENT ON COLUMN "ENTITY_FORM"."DATA_SOURCE_BINDINGS_DOCUMENT" IS '数据源绑定文档：表单级统一数据源绑定JSON文档。';
COMMENT ON TABLE "ENTITY_FORM_NODE" IS '实体表单递归节点表';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."ID" IS '主键ID：稳定节点ID。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."FORM_ID" IS '表单ID。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."PARENT_ID" IS '父节点ID。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."NODE_KEY" IS '表单内稳定节点编码。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."ACTIVE_NODE_KEY" IS '仅活动节点参与表单内节点编码唯一约束；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."NODE_TYPE" IS '节点类型。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."BINDING_TYPE" IS '绑定类型。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."BINDING_REF" IS '字段、关系或上下文引用。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."PROPS_DOCUMENT" IS '节点显式属性JSON文档。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."RULES_DOCUMENT" IS '校验、显隐和权限规则JSON文档。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."DATA_SOURCE_BINDINGS_DOCUMENT" IS '数据源绑定文档：节点数据源绑定JSON文档。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."LEGACY_PROPS_DOCUMENT" IS '无法识别的历史属性JSON文档；仍保存历史或当前节点类型不适用的属性，并参与恢复；不是已废弃的空列。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."ORDER_KEY" IS '稀疏排序键。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."REVISION" IS '修订号：节点草稿修订号。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."DELETED" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."COMPONENT_NAME" IS '节点扩展组件注册名。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."COMPONENT_VERSION" IS '节点扩展组件锁定版本。';
COMMENT ON COLUMN "ENTITY_FORM_NODE"."SNAPSHOT_VERSION" IS '节点扩展配置快照版本。';
COMMENT ON TABLE "ENTITY_FORM_UNIQUE_CLAIM" IS '表单唯一值原子占位表';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."CONSTRAINT_KEY" IS '唯一约束命名空间，格式 FORM:{formId}:{snapshotIdentity}:{ruleId}。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."VALUE_HASH" IS '规范化值的 SHA-256。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."FORM_ID" IS '规则所属表单ID。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."RULE_ID" IS '跨发布版本稳定的规则ID。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."FIELD_CODE" IS '规则字段编码。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."NORMALIZED_VALUE" IS '截断后的规范化值，仅用于排障。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."RECORD_ID" IS '占位所属业务记录ID。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."RELEASE_ID" IS '最近一次维护占位的表单发布ID，仅用于审计。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."RELEASE_VERSION" IS '最近一次维护占位的表单发布版本，仅用于审计。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."EFFECTIVE_RELEASE_ID" IS '规则实际来源发布ID；热修复时为热修复发布ID。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."EFFECTIVE_CONTENT_HASH" IS '规则实际有效快照哈希，仅用于审计和完整性追踪。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."HOTFIX_TARGET_ID" IS '热修复目标ID；非热修复为空。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."CREATED_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_CLAIM"."UPDATED_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_FORM_UNIQUE_VALUE_GATE" IS '表单唯一值事务门闩表';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_VALUE_GATE"."SCOPE_KEY" IS '稳定值锁作用域，格式 ENTITY:{entityCode}:{fieldCode}。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_VALUE_GATE"."VALUE_HASH" IS '规范化值的 SHA-256。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_VALUE_GATE"."CREATED_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_FORM_UNIQUE_VALUE_GATE"."UPDATED_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_LIST_ACTION" IS '实体列表按钮配置表';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."LIST_CONFIG_ID" IS '列表配置ID。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."POSITION" IS '职务。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."BUTTON_KEY" IS '稳定按钮编码。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."BUTTON_TYPE" IS '按钮类型。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."BUTTON_LABEL" IS '按钮名称。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."ICON" IS '图标。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."STYLE_TYPE" IS '按钮样式。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."LINK_MODE" IS '是否链接按钮。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."CUSTOM_MODE" IS '自定义模式。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."HANDLER_CODE" IS '处理器或组件编码。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."PERMISSION_CODE" IS '功能权限码。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."UNAVAILABLE_BEHAVIOR" IS '历史不可用行为：历史字段；v2 显示/启用规则不再读取，待数据库清理时移除。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."ACTION_PARAMS_DOCUMENT" IS '按钮扩展参数JSON文档。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."AVAILABILITY_RULE_DOCUMENT" IS '按钮适用条件JSON文档。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."DELETED" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."REVISION" IS '修订号：按钮草稿修订号。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."ORDER_KEY" IS '稀疏排序键。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."TEMPLATE_ID" IS '来源模板ID。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."TEMPLATE_VERSION" IS '锁定模板版本。';
COMMENT ON COLUMN "ENTITY_LIST_ACTION"."LOCAL_OVERRIDES_DOCUMENT" IS '模板实例本地覆盖JSON文档。';
COMMENT ON TABLE "ENTITY_LIST_CONFIG" IS '实体列表配置表';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."ID" IS '列表配置 ID：主键。普通创建由 MyBatis-Plus `ASSIGN_UUID` 生成，不是数据库自增 ID';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."ENTITY_ID" IS '所属实体 ID：逻辑关联 `entity_definition.id`。注意目标主键是 `bigint`，本表以字符串保存';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."ENTITY_CODE" IS '所属实体编码：冗余保存实体编码，服务于按编码定位列表及运行时权限解析；业务上应与 `entity_id` 指向同一实体';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."LIST_KEY" IS '列表标识：实体内的稳定编码，如 `default`、`picker`。保存校验格式为 `[A-Za-z][A-Za-z0-9_-]{0,99}`';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."LIST_NAME" IS '列表名称：面向管理员与使用者的显示名称，可修改。数据库非空约束不等于禁止空字符串';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."DESCRIPTION" IS '列表说明：说明列表用途与适用范围';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."IS_DEFAULT" IS '是否默认列表：`1` 为默认，`0` 为非默认。未指定列表标识时优先选择默认列表；当前未强制一个实体只能有一个默认列表';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."DELETED" IS '逻辑删除标记：`0` 为正常，`1` 为已删除；MyBatis-Plus 使用 `@TableLogic`，常规查询筛选 `deleted = 0`';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."CREATE_TIME" IS '创建时间：数据库提供插入默认时间，普通保存流程也会设置创建时间';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."UPDATE_TIME" IS '更新时间：含 `ON UPDATE CURRENT_TIMESTAMP`，应用更新时也会设置；不能当作发布时间或修订号';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."CUSTOM_COMPONENT" IS '自定义列表组件：前端已注册的整页列表组件名；为空时使用平台动态列表。组件名是注册标识，不是可执行脚本';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."TOOLBAR_CONFIG" IS '工具栏按钮配置：JSON 数组，保存工具栏按钮配置；与按钮关系表的读取优先级见 2.4.2';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."ROW_ACTION_CONFIG" IS '行内操作配置：JSON 数组，保存每行操作按钮配置；与按钮关系表的读取优先级见 2.4.2';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."VIEW_CONFIG" IS '列表视图配置：JSON 对象，保存查询区、表格、分页与自定义组件参数；具体列定义在字段子表';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."DATA_SCOPE_MODE" IS '数据范围模式：允许 `INHERIT`、`NARROW`、`OVERRIDE`；保留的模式标识。当前执行语义的限制见 2.4.4，不能仅凭字段名认定存在三套范围合并算法';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."UNBOUND_SCOPE_POLICY" IS '未绑定允许规则时的策略：没有已启用且在有效期内的 ALLOW 绑定时，采用拒绝全部、本人数据或显式全量可见的默认范围';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."SCOPE_ENFORCEMENT_MODE" IS '默认策略执行阶段：`OBSERVE` 为存量观察期；`ENFORCE` 为执行安全默认策略';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."SCOPE_DEFAULT_CONFIRMED" IS '全量可见是否已确认：`1` 表示已显式确认；执行阶段下 `EXPLICIT_ALL` 需要该确认';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."SCOPE_DEFAULT_CONFIRMED_BY" IS '全量可见确认人：记录确认操作的当前用户 ID；不代表本条列表配置的创建人';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."SCOPE_DEFAULT_CONFIRMED_AT" IS '全量可见确认时间：记录显式确认发生的时间';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."SCOPE_DEFAULT_CONFIRMATION_NOTE" IS '全量可见确认原因：记录放开数据范围的业务原因；首次确认时应用要求至少 5 个字符';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."ACCESS_PERMISSION_CODE" IS '列表访问权限码：控制进入列表的权限；空值回退为 `entity:{entity_code}:list`。访问权限与可见记录范围分别校验';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."ALLOWED_SCENES" IS '历史场景配置：功能已移除，应用不再读写';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."SELECTION_CONFIG" IS '选数配置：JSON 对象，描述是否允许选数、主值字段及选中记录的返回映射';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."FIXED_FILTER_CONFIG" IS '固定查询条件：JSON 对象。无论是否绑定数据范围规则均生效；与权限范围取交集，省略 `_op` 时按 `EQ`，用户筛选和自定义查询不能放宽条件';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."QUERY_PROVIDER_CODE" IS '查询提供者编码：已注册 `EntityListDataProvider` 的编码，用于自定义列表查询；不能与接口扩展查询同时配置';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."PUBLISHED_VERSION" IS '当前界面发布版本号：`0` 为尚未发布；发布后对应当前激活的 LIST 快照版本。不是草稿修订号，也不是数据权限发布版本';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."REVISION" IS '草稿修订号：列表编辑的并发控制版本。普通元数据更新校验 `expectedRevision`，成功后递增；字段、按钮变更也会触碰主表修订号';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."ACTIVE_RELEASE_ID" IS '当前激活快照 ID：逻辑关联 `ui_config_release.id`；对应快照必须属于本列表且为 LIST 类型。普通运行入口要求其与当前 ACTIVE 快照一致';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."DRAFT_HASH" IS '草稿内容哈希：保存规范化快照的 SHA-256 十六进制哈希。草稿编辑通常将其清空，发布时写入；需要比较完整草稿内容，不能只凭此字段判断有无变更';
COMMENT ON COLUMN "ENTITY_LIST_CONFIG"."QUERY_INTERFACE_EXTENSION_ID" IS '查询接口扩展 ID：历史查询槽位，逻辑关联 `ui_extension_definition.id`；V094 将存量草稿迁入 `LIST_LOAD` 替代步骤后清空，保留字段以读取旧发布';
COMMENT ON TABLE "ENTITY_LIST_FIELD" IS '实体列表字段配置表';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."LIST_CONFIG_ID" IS '所属列表配置ID。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."FIELD_ID" IS '实体字段ID（关联entity_field）。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."FIELD_CODE" IS '字段编码。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."FIELD_NAME" IS '字段名称（快照）。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."SORT_ORDER" IS '排序号：列排序号；当前主要按 order_key 排序，本列仍作为次级排序和兼容值。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."WIDTH" IS '列宽度（0表示自适应）。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."SHOW_IN_LIST" IS '是否显示在列表。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."IS_QUERY" IS '是否作为查询条件。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."QUERY_TYPE" IS '查询方式：EQ/NE/LIKE/GT/LT/BETWEEN/IN。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."ALIGN" IS '对齐方式：left/center/right。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."DATA_SOURCE_TYPE" IS '数据源类型：ENTITY_FIELD(实体字段)/REFERENCE(关联查询)/AGGREGATE(聚合统计)/CUSTOM_PROVIDER(自定义处理器)。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."DATA_SOURCE_CONFIG" IS '数据源配置JSON。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."RENDER_COMPONENT" IS '前端渲染组件名。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."FORMATTER" IS '简单格式化表达式（如 yyyy-MM-dd、#0.00）。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."COLUMN_CONFIG" IS '列展示配置JSON。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."QUERY_CONFIG" IS '查询组件配置JSON。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."RENDER_CONFIG" IS '单元格渲染配置JSON。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."REVISION" IS '修订号：字段草稿修订号。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."ORDER_KEY" IS '稀疏排序键。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."INTERFACE_EXTENSION_ID" IS '列接口扩展 ID：逻辑关联一条可用于 `LIST_COLUMN` 的 `INTERFACE` 扩展；无需再保存操作编码。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."TEMPLATE_ID" IS '来源模板ID。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."TEMPLATE_VERSION" IS '锁定模板版本。';
COMMENT ON COLUMN "ENTITY_LIST_FIELD"."LOCAL_OVERRIDES_DOCUMENT" IS '模板实例本地覆盖JSON文档。';
COMMENT ON TABLE "ENTITY_LIST_SCENE" IS '实体列表场景配置表';
COMMENT ON COLUMN "ENTITY_LIST_SCENE"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCENE"."LIST_CONFIG_ID" IS '所属列表配置ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCENE"."SCENE_CODE" IS '场景编码（如 pc/mobile/picker 等）。';
COMMENT ON COLUMN "ENTITY_LIST_SCENE"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "ENTITY_LIST_SCENE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCENE"."REVISION" IS '修订号：场景草稿修订号。';
COMMENT ON TABLE "ENTITY_LIST_SCOPE_AUDIT_LOG" IS '列表数据范围审计表';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."LIST_KEY" IS '列表编码。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."USER_ID" IS '操作或被校验用户。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."OPERATION" IS '操作。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."RESULT" IS '结果。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."DETAIL_JSON" IS '结构化详情。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_AUDIT_LOG"."CREATE_TIME" IS '创建时间：记录时间。';
COMMENT ON TABLE "ENTITY_LIST_SCOPE_BINDING" IS '列表数据范围绑定表';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."POLICY_ID" IS '数据范围方案ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."LIST_KEY" IS '列表编码，空表示实体默认范围。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."MATCH_CONFIG" IS '适用用户结构化条件。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."RULE_EFFECT" IS '规则效果。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."EFFECTIVE_START_TIME" IS '生效时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."EFFECTIVE_END_TIME" IS '失效时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_BINDING"."DELETED" IS '逻辑删除标记：逻辑删除。';
COMMENT ON TABLE "ENTITY_LIST_SCOPE_DELEGATION" IS '列表数据范围委派表';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."ENTITY_CODE" IS '实体编码，空表示全部实体。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."FROM_USER_ID" IS '委托方用户ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."TO_USER_ID" IS '受托方用户ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."DELEGATE_SCOPE" IS '委派范围。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."POLICY_ID" IS '指定方案ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."DELEGATE_CONFIG" IS '附加结构化条件。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."START_TIME" IS '开始时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."END_TIME" IS '结束时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_DELEGATION"."DELETED" IS '逻辑删除标记：逻辑删除。';
COMMENT ON TABLE "ENTITY_LIST_SCOPE_POLICY" IS '列表数据范围方案表';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."POLICY_KEY" IS '方案稳定编码。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."POLICY_NAME" IS '方案名称。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."DESCRIPTION" IS '说明：方案说明。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."PRESET_CODE" IS '内置模板编码。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."FILTER_CONFIG" IS '结构化数据条件。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."STATUS" IS '状态。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."VERSION" IS '版本号：配置版本。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."REVIEW_REQUIRED" IS '是否需要人工复核：旧复杂规则是否需要人工确认。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_POLICY"."DELETED" IS '逻辑删除标记：逻辑删除。';
COMMENT ON TABLE "ENTITY_LIST_SCOPE_RELEASE" IS '列表数据范围发布表';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."VERSION" IS '版本号：发布版本。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."SNAPSHOT_JSON" IS '方案、绑定和列表模式完整快照。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."CONTENT_HASH" IS '内容哈希：内容摘要，用于检查配置或快照内容是否一致。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."STATUS" IS '状态。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."DESCRIPTION" IS '说明：发布说明。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."PUBLISHED_BY" IS '发布人。';
COMMENT ON COLUMN "ENTITY_LIST_SCOPE_RELEASE"."PUBLISHED_AT" IS '发布时间。';
COMMENT ON TABLE "ENTITY_MUTATION_RECEIPT" IS '实体变更幂等回执表';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."ID" IS '主键ID：变更回执ID。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."IDEMPOTENCY_KEY" IS '幂等键：全局幂等键。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."COMMAND_HASH" IS '变更命令摘要。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."OPERATION_ID" IS '操作ID。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."RECORD_ID" IS '记录ID。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."OPERATION_TYPE" IS '操作类型。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."STATUS" IS '状态。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."RESULT_DOCUMENT" IS '首次成功执行结果JSON。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."VERSION_NO" IS '版本编号。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."VERSION_SCENARIO_CODE" IS '版本场景编码。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."CHANGED" IS '是否发生变化。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_MUTATION_RECEIPT"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_PROCESS_LINK" IS '实体与流程实例关联表';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."ENTITY_RECORD_ID" IS '实体记录ID。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."GENERATION" IS '代次。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."PROCESS_DEFINITION_KEY" IS '流程定义键。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."STATE" IS '处理状态：关联状态：PENDING 等待发起、ACTIVE 已关联运行实例、ENDED 已结束；与 end_type 配合区分生命周期和结束原因。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."REQUEST_ID" IS '请求ID。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."ENTITY_STATUS" IS '实体状态。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."ENDED_AT" IS '结束时间。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."VERSION" IS '版本号。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."UPDATE_TIME" IS '更新时间。';
COMMENT ON COLUMN "ENTITY_PROCESS_LINK"."END_TYPE" IS '流程结束类型：在流程结束同步时记录 COMPLETED（正常结束）、TERMINATED（终止）或 WITHDRAWN（撤回），用于区分结束原因。关联状态统一为 ENDED，业务审批是否通过仍看业务状态，不能仅凭 COMPLETED 判断。未结束或历史原因未确认时可为空。';
COMMENT ON TABLE "ENTITY_PUBLISH_HISTORY" IS '实体发布历史表';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."ENTITY_ID" IS '实体定义ID。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."ENTITY_NAME" IS '实体名称。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."VERSION" IS '版本号。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."VERSION_DESCRIPTION" IS '版本说明。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."FIELDS_SNAPSHOT" IS '字段定义快照JSON。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."RELATIONS_SNAPSHOT" IS '发布时实体关系定义快照JSON，NULL为旧发布。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."TABLE_DDL" IS '表结构DDL。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."PUBLISH_TYPE" IS '发布类型：CREATE首次创建/ALTER修改结构。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."CHANGES_DESCRIPTION" IS '变更内容描述。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."PUBLISHED_AT" IS '发布时间。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."PUBLISHED_BY" IS '发布人ID。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."PUBLISHED_BY_NAME" IS '发布人名称：发布人姓名。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."STATUS" IS '状态：ACTIVE有效/ROLLBACK已回滚。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."PROCESS_DEFINITION_ID" IS '发布时绑定流程定义ID。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."LIFECYCLE_MODE" IS '发布时实体生命周期模式。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."TEAM_VISIBILITY_ENABLED" IS '团队可见性开关：发布时是否允许数据参与团队查看记录；保留旧发布的团队可见性信息，当前权限计算不依赖这两个开关。';
COMMENT ON COLUMN "ENTITY_PUBLISH_HISTORY"."TEAM_VISIBILITY_LEVEL" IS '团队可见性级别：发布时参与团队权限级别；保留旧发布的团队可见性信息，当前权限计算不依赖这两个开关。';
COMMENT ON TABLE "ENTITY_RECORD_VERSION" IS '实体记录版本表';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."ID" IS '主键ID：业务版本ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."RECORD_ID" IS '记录ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."VERSION_NO" IS '同一记录从1递增。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."VERSION_TITLE" IS '版本标题。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SCENARIO_CODE" IS '场景编码。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SCENARIO_NAME" IS '场景名称。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."OPERATION_TYPE" IS '操作类型。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SOURCE_TYPE" IS '来源类型。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SOURCE_ID" IS '来源ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."BUSINESS_INTENT_CODE" IS '业务意图编码。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."BUSINESS_INTENT_NAME" IS '业务意图名称。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SOURCE_ENTITY_CODE" IS '来源实体编码。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SOURCE_RECORD_ID" IS '来源记录ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."PROCESS_DEFINITION_ID" IS '流程定义ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."TASK_ID" IS '任务ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."OPERATOR_ID" IS '操作人ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."OPERATOR_NAME" IS '操作人名称。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."BUSINESS_TRACE_KEY" IS '业务追踪键。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."IDEMPOTENCY_KEY" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."ENTITY_RELEASE_ID" IS '实体发布ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."ENTITY_RELEASE_VERSION" IS '实体发布版本。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SCHEMA_VERSION" IS '快照契约版本：1/2。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."CONFIG_RELEASE_ID" IS '旧版本策略发布ID：仅供滚动发布期间的旧 Pod 写入；业务快照不再依赖此值。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."CONFIG_RELEASE_VERSION" IS '旧版本策略发布版本：仅供滚动发布期间的旧 Pod 写入；新代码不使用。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."DATA_HASH" IS '原始业务数据摘要。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."PRESENTATION_HASH" IS '冻结中文展示语义摘要。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SCOPE_HASH" IS '固化范围摘要。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."REQUEST_HASH" IS '幂等请求摘要。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."DATASET_COUNT" IS '关系数据集数量。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SNAPSHOT_ROW_COUNT" IS '根记录与关系记录总数。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SNAPSHOT_SIZE_BYTES" IS 'V2快照序列化字节数。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."COMPLETENESS" IS '完整性：COMPLETE；V2禁止截断。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SNAPSHOT_HASH" IS '快照哈希。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."SNAPSHOT_DOCUMENT" IS '快照文档。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "ENTITY_RECORD_VERSION_COUNTER" IS '记录版本计数器表';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_COUNTER"."ENTITY_CODE" IS '同一实体记录的事务级版本号计数器。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_COUNTER"."RECORD_ID" IS '记录ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_COUNTER"."LAST_VERSION_NO" IS '最近版本编号。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_COUNTER"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_RECORD_VERSION_DATASET" IS '记录版本关系数据集表';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."ID" IS '主键ID：数据集ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."VERSION_ID" IS '业务版本ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."NODE_CODE" IS '稳定范围节点编码。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."NODE_KIND" IS '节点种类。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."RELATION_CODE" IS '冻结关系编码。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."RELATION_NAME" IS '冻结关系中文名称。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."ENTITY_CODE" IS '子实体编码。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."ENTITY_NAME" IS '冻结子实体中文名称。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."ENTITY_RELEASE_ID" IS '实体发布ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."ENTITY_RELEASE_VERSION" IS '实体发布版本。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."SELECTOR_DOCUMENT" IS '冻结关系、过滤和排序选择器JSON。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."PRESENTATION_DOCUMENT" IS '冻结中文表单展示定义JSON。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."DATA_HASH" IS '数据哈希。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."PRESENTATION_HASH" IS '展示哈希。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."SCOPE_HASH" IS '范围哈希。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."ROW_COUNT" IS '行数量。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."COMPLETE" IS 'V2必须完整，禁止静默截断。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "ENTITY_RECORD_VERSION_DATASET_ROW" IS '记录版本数据集行表';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."ID" IS '主键ID：数据集行ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."DATASET_ID" IS '数据集ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."RECORD_ID" IS '子记录稳定ID。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."RECORD_TITLE" IS '冻结业务中文标题。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."ROW_ORDER" IS '冻结顺序。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."ROW_HASH" IS '行哈希。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."VALUES_DOCUMENT" IS '值集合文档：fieldCode到FrozenValue的JSON。';
COMMENT ON COLUMN "ENTITY_RECORD_VERSION_DATASET_ROW"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "ENTITY_RELATION" IS '实体关系定义表';
COMMENT ON COLUMN "ENTITY_RELATION"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_RELATION"."PARENT_ENTITY_ID" IS '主实体ID。';
COMMENT ON COLUMN "ENTITY_RELATION"."PARENT_ENTITY_CODE" IS '主实体编码。';
COMMENT ON COLUMN "ENTITY_RELATION"."PARENT_FIELD_ID" IS '主实体关系字段ID；关系已与 SUB_FORM 字段生命周期解耦，保留旧父字段关联用于回退或兼容。';
COMMENT ON COLUMN "ENTITY_RELATION"."PARENT_FIELD_CODE" IS '旧版承载关系的父字段编码，仅兼容使用；关系已与 SUB_FORM 字段生命周期解耦，保留旧父字段关联用于回退或兼容。';
COMMENT ON COLUMN "ENTITY_RELATION"."RELATION_CODE" IS '关系编码。';
COMMENT ON COLUMN "ENTITY_RELATION"."RELATION_NAME" IS '关系名称。';
COMMENT ON COLUMN "ENTITY_RELATION"."DATA_KEY" IS '聚合数据中的稳定属性名。';
COMMENT ON COLUMN "ENTITY_RELATION"."CHILD_ENTITY_ID" IS '子实体ID。';
COMMENT ON COLUMN "ENTITY_RELATION"."CHILD_ENTITY_CODE" IS '子实体编码。';
COMMENT ON COLUMN "ENTITY_RELATION"."CHILD_REF_FIELD_CODE" IS '子实体回填主数据ID字段。';
COMMENT ON COLUMN "ENTITY_RELATION"."RELATION_TYPE" IS '关系类型：ONE_TO_ONE/ONE_TO_MANY。';
COMMENT ON COLUMN "ENTITY_RELATION"."OWNERSHIP_TYPE" IS '关系所有权：COMPOSITION/ASSOCIATION。';
COMMENT ON COLUMN "ENTITY_RELATION"."CASCADE_DELETE" IS '主数据删除时是否级联删除子数据。';
COMMENT ON COLUMN "ENTITY_RELATION"."REQUIRED" IS '是否必需：是否必填。';
COMMENT ON COLUMN "ENTITY_RELATION"."SORT_ORDER" IS '排序号：排序。';
COMMENT ON COLUMN "ENTITY_RELATION"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "ENTITY_RELATION"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "ENTITY_RELATION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_RELATION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_SCHEMA_OPERATION" IS '实体结构发布操作表';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."ID" IS '主键ID：操作ID。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."ENTITY_ID" IS '实体ID。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."STATUS" IS '状态。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."PLAN_HASH" IS '不可变DDL计划摘要。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."IDEMPOTENCY_KEY" IS '幂等键。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."PLAN_JSON" IS '不可变DDL计划JSON。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."TARGET_FINGERPRINT" IS '目标结构指纹。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."ACTUAL_FINGERPRINT" IS '实际结构指纹。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."DRIFT_JSON" IS '结构漂移JSON。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."UNIQUE_CONFLICT_JSON" IS '唯一性冲突扫描结果JSON。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."RISK_LEVEL" IS '风险级别。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."RISK_REASON" IS '风险原因。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."ESTIMATED_ROWS" IS '预估数据行数。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."LOCK_RISK" IS '锁表风险。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."RELEASE_WINDOW" IS '建议发布窗口。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."ATTEMPT_COUNT" IS '执行次数。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."ERROR_MESSAGE" IS '最近失败信息。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."STARTED_AT" IS '开始时间。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."FINISHED_AT" IS '完成时间。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."OPERATION_SOURCE" IS '操作来源；INDEX_ADVISOR 来源仅保留历史记录；实体发布来源仍在使用。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION"."SOURCE_REFERENCE_ID" IS '索引建议等来源记录ID；INDEX_ADVISOR 来源仅保留历史记录；实体发布来源仍在使用。';
COMMENT ON TABLE "ENTITY_SCHEMA_OPERATION_EVENT" IS '实体结构操作事件表';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION_EVENT"."ID" IS '主键ID：事件ID。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION_EVENT"."OPERATION_ID" IS '操作ID。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION_EVENT"."FROM_STATUS" IS '原状态。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION_EVENT"."TO_STATUS" IS '目标状态。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION_EVENT"."MESSAGE" IS '状态说明。';
COMMENT ON COLUMN "ENTITY_SCHEMA_OPERATION_EVENT"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "ENTITY_STATUS" IS '实体状态定义表';
COMMENT ON COLUMN "ENTITY_STATUS"."ID" IS '主键ID。';
COMMENT ON COLUMN "ENTITY_STATUS"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_STATUS"."STATUS_CODE" IS '状态编码（系统标识）。';
COMMENT ON COLUMN "ENTITY_STATUS"."STATUS_NAME" IS '状态名称（显示用）。';
COMMENT ON COLUMN "ENTITY_STATUS"."STATUS_CATEGORY" IS '状态分类：NEW-新建、PROCESSING-审批中、COMPLETED-已完成、TERMINATED-终止。';
COMMENT ON COLUMN "ENTITY_STATUS"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "ENTITY_STATUS"."DESCRIPTION" IS '说明：状态说明。';
COMMENT ON COLUMN "ENTITY_STATUS"."COLOR" IS '状态颜色（如：#67C23A）。';
COMMENT ON COLUMN "ENTITY_STATUS"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "ENTITY_STATUS"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_STATUS"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_UNIQUE_VALUE" IS '实体字段唯一值预留表';
COMMENT ON COLUMN "ENTITY_UNIQUE_VALUE"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_UNIQUE_VALUE"."FIELD_CODE" IS '字段编码。';
COMMENT ON COLUMN "ENTITY_UNIQUE_VALUE"."VALUE_HASH" IS '规范化值摘要。';
COMMENT ON COLUMN "ENTITY_UNIQUE_VALUE"."NORMALIZED_VALUE" IS '规范化值审计副本。';
COMMENT ON COLUMN "ENTITY_UNIQUE_VALUE"."RECORD_ID" IS '占用该值的记录ID。';
COMMENT ON COLUMN "ENTITY_UNIQUE_VALUE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_UNIQUE_VALUE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "ENTITY_VERSION_CONFIG" IS '实体数据版本策略表';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."ID" IS '主键ID：配置ID。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."ENTITY_ID" IS '实体定义ID。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."ENABLED" IS '是否启用数据版本。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."CONTRACT_VERSION" IS '旧配置契约版本：供兼容桥和尚未升级的旧 Pod 使用。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."DRAFT_DOCUMENT" IS '旧草稿JSON：供兼容路由、双写桥和尚未升级的旧 Pod 使用；新核心运行时不读取。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."CONFIG_DOCUMENT" IS '当前生效配置JSON：V2 触发器、范围和比较策略；保存后立即参与后续版本捕获。过渡期可空，以兼容旧 Pod 创建未发布配置。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."MIGRATION_STATE" IS '旧迁移状态：供兼容桥和旧 Pod 保持旧契约。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."ACTIVE_RELEASE_ID" IS '旧运行发布ID：由兼容桥维护，供尚未升级的旧 Pod 定位发布快照。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."REVISION" IS '修订号：当前配置修订号，用于 If-Match 乐观锁；不代表可回退的配置版本。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."STATUS" IS '旧发布状态：供兼容路由和旧 Pod 区分草稿、已发布。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."CREATE_BY" IS '创建人。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."UPDATE_BY" IS '修改人。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "ENTITY_VERSION_CONFIG_RELEASE" IS '实体版本策略发布兼容表';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."ID" IS '主键ID：旧发布快照ID。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."CONFIG_ID" IS '版本配置ID。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."VERSION" IS '版本号：旧发布版本号。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."CONTRACT_VERSION" IS '配置契约版本：旧配置契约版本。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."CONFIG_DOCUMENT" IS '旧不可变配置JSON：由兼容桥写入，供尚未升级的旧 Pod 读取。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."SCOPE_HASH" IS '发布时范围摘要：旧发布时冻结范围摘要。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."PUBLISHED_BY" IS '发布人：旧发布人。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."PUBLISHED_BY_NAME" IS '发布人名称：旧发布人名称。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."PUBLISH_TIME" IS '发布时间：旧发布时间。';
COMMENT ON COLUMN "ENTITY_VERSION_CONFIG_RELEASE"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "FLW_CHANNEL_DEFINITION" IS 'Flowable 事件注册或批处理事件通道定义表，保存引擎定义与运行状态';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."VERSION_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."RESOURCE_NAME_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "FLW_CHANNEL_DEFINITION"."IMPLEMENTATION_" IS 'IMPLEMENTATION_ 引擎属性，供 Flowable 事件注册或批处理事件通道定义表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON TABLE "FLW_EVENT_DEFINITION" IS 'Flowable 事件注册或批处理事件定义表，保存引擎定义与运行状态';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."VERSION_" IS '定义版本，供引擎选择和历史追踪';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."KEY_" IS '稳定业务键，用于查找对应的定义或实例';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."RESOURCE_NAME_" IS '资源文件名，定位部署包内的模型内容';
COMMENT ON COLUMN "FLW_EVENT_DEFINITION"."DESCRIPTION_" IS '说明文本，供定义管理和历史查询展示';
COMMENT ON TABLE "FLW_EVENT_DEPLOYMENT" IS 'Flowable 事件注册或批处理事件部署包表，保存引擎定义与运行状态';
COMMENT ON COLUMN "FLW_EVENT_DEPLOYMENT"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "FLW_EVENT_DEPLOYMENT"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "FLW_EVENT_DEPLOYMENT"."CATEGORY_" IS '分类标识，供模型和定义按业务类型检索';
COMMENT ON COLUMN "FLW_EVENT_DEPLOYMENT"."DEPLOY_TIME_" IS '部署时间，用于模型版本管理和历史追踪';
COMMENT ON COLUMN "FLW_EVENT_DEPLOYMENT"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON COLUMN "FLW_EVENT_DEPLOYMENT"."PARENT_DEPLOYMENT_ID_" IS 'PARENT_DEPLOYMENT 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON TABLE "FLW_EVENT_RESOURCE" IS 'Flowable 事件注册或批处理事件资源表，保存引擎定义与运行状态';
COMMENT ON COLUMN "FLW_EVENT_RESOURCE"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "FLW_EVENT_RESOURCE"."NAME_" IS '名称，供引擎管理界面展示';
COMMENT ON COLUMN "FLW_EVENT_RESOURCE"."DEPLOYMENT_ID_" IS '部署包ID，关联模型及其资源';
COMMENT ON COLUMN "FLW_EVENT_RESOURCE"."RESOURCE_BYTES_" IS '资源二进制内容，保存部署时上传的模型文件';
COMMENT ON TABLE "FLW_RU_BATCH" IS 'Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态';
COMMENT ON COLUMN "FLW_RU_BATCH"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "FLW_RU_BATCH"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "FLW_RU_BATCH"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "FLW_RU_BATCH"."SEARCH_KEY_" IS 'SEARCH_KEY_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "FLW_RU_BATCH"."SEARCH_KEY2_" IS 'SEARCH_KEY2_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "FLW_RU_BATCH"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "FLW_RU_BATCH"."COMPLETE_TIME_" IS '完成时间，用于批处理结果和耗时统计';
COMMENT ON COLUMN "FLW_RU_BATCH"."STATUS_" IS '批处理状态，决定后续调度或结果查询';
COMMENT ON COLUMN "FLW_RU_BATCH"."BATCH_DOC_ID_" IS 'BATCH_DOC 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "FLW_RU_BATCH"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "FLW_RU_BATCH_PART" IS 'Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."ID_" IS '引擎记录主键，供内部表关联和状态更新';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."REV_" IS '乐观锁版本，防止并发覆盖引擎状态';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."BATCH_ID_" IS 'BATCH 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."TYPE_" IS '记录类型，决定引擎采用的处理方式';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."SCOPE_ID_" IS 'SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."SUB_SCOPE_ID_" IS 'SUB_SCOPE 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."SCOPE_TYPE_" IS '作用域类型，区分流程、案例等引擎上下文';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."SEARCH_KEY_" IS 'SEARCH_KEY_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."SEARCH_KEY2_" IS 'SEARCH_KEY2_ 引擎属性，供 Flowable 事件注册或批处理运行时批处理分片表，保存引擎定义与运行状态在模型管理或实例执行时使用';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."CREATE_TIME_" IS '创建时间，用于引擎记录排序和追踪';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."COMPLETE_TIME_" IS '完成时间，用于批处理结果和耗时统计';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."STATUS_" IS '批处理状态，决定后续调度或结果查询';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."RESULT_DOC_ID_" IS 'RESULT_DOC 标识，关联 Flowable 引擎中的对应记录';
COMMENT ON COLUMN "FLW_RU_BATCH_PART"."TENANT_ID_" IS '租户标识，用于隔离不同租户的引擎记录';
COMMENT ON TABLE "INTEGRATION_API_REQUEST_LEASE" IS 'Embed 请求并发租约表';
COMMENT ON COLUMN "INTEGRATION_API_REQUEST_LEASE"."LEASE_ID" IS '租约ID。';
COMMENT ON COLUMN "INTEGRATION_API_REQUEST_LEASE"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "INTEGRATION_API_REQUEST_LEASE"."SCOPE_KEY" IS '并发配额范围键：区分请求类别的内部键，不表示 OAuth Scope。';
COMMENT ON COLUMN "INTEGRATION_API_REQUEST_LEASE"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "INTEGRATION_API_REQUEST_LEASE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "INTEGRATION_API_REQUEST_LEASE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "INTEGRATION_APPLICATION" IS '外部集成应用表';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."ID" IS '主键ID。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."CLIENT_ID" IS '客户端ID。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."APPLICATION_NAME" IS '应用名称。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."DESCRIPTION" IS '说明。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."OWNER_ORGANIZATION_ID" IS '所有者组织ID。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."STATUS" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''DISABLED\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."RATE_LIMIT_PER_MINUTE" IS '每分钟请求上限。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."MAX_CONCURRENCY" IS '最大并发数。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."ALLOWED_SOURCE_CIDRS" IS '允许的来源网段集合。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."VERSION" IS '版本号。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."UPDATED_BY" IS '修改人。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "INTEGRATION_APPLICATION_CREDENTIAL" IS '集成应用凭证表';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."ID" IS '主键ID。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."SECRET_HASH" IS '密钥哈希：应用凭证哈希，用于验证凭证，不能反推出原始凭证。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."CREDENTIAL_HINT" IS '凭证提示。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."STATUS" IS '状态；CHECK 枚举：\\''ACTIVE\\'',\\''REVOKED\\''。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."CREDENTIAL_VERSION" IS '凭证版本。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."LAST_USED_AT" IS '最近使用时间。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."REVOKED_BY" IS '撤销人。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."REVOKED_AT" IS '撤销时间。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "INTEGRATION_APPLICATION_CREDENTIAL"."ACTIVE_APPLICATION_ID" IS '活跃应用ID；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "INTEGRATION_IDEMPOTENCY_RECORD" IS 'Embed 写入幂等记录表';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."ID" IS '主键ID。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."APPLICATION_ID" IS '应用ID。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."OPERATION" IS '操作。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."IDEMPOTENCY_KEY" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."REQUEST_HASH" IS '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."STATUS" IS '状态；CHECK 枚举： \\''PROCESSING\\'',\\''SUCCEEDED\\'',\\''FAILED_RETRYABLE\\'' 。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."RESOURCE_TYPE" IS '资源类型。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."RESOURCE_ID" IS '资源ID。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."RESPONSE_STATUS" IS '响应状态。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."RESPONSE_BODY" IS '响应正文。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."FENCING_TOKEN" IS '执行隔离代次。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."PROCESSING_STARTED_AT" IS '处理开始时间。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."EXPIRES_AT" IS '过期时间。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "INTEGRATION_IDEMPOTENCY_RECORD"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "INTEGRATION_RATE_LIMIT_BUCKET" IS '集成应用限流桶表';
COMMENT ON COLUMN "INTEGRATION_RATE_LIMIT_BUCKET"."BUCKET_KEY" IS '桶键。';
COMMENT ON COLUMN "INTEGRATION_RATE_LIMIT_BUCKET"."WINDOW_EPOCH" IS '窗口时间戳。';
COMMENT ON COLUMN "INTEGRATION_RATE_LIMIT_BUCKET"."REQUEST_COUNT" IS '请求数量。';
COMMENT ON COLUMN "INTEGRATION_RATE_LIMIT_BUCKET"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "INTEGRATION_RATE_LIMIT_BUCKET"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "PROCESS_ACTION" IS '流程动作绑定表';
COMMENT ON COLUMN "PROCESS_ACTION"."ID" IS '主键ID：流程动作配置 用于流程、节点和顺序流上配置的接口动作。';
COMMENT ON COLUMN "PROCESS_ACTION"."PROCESS_CONFIG_ID" IS '流程定义配置ID。';
COMMENT ON COLUMN "PROCESS_ACTION"."SCOPE_TYPE" IS '作用域：PROCESS/NODE/SEQUENCE_FLOW。';
COMMENT ON COLUMN "PROCESS_ACTION"."ELEMENT_ID" IS 'BPMN元素ID，流程级为空。';
COMMENT ON COLUMN "PROCESS_ACTION"."TRIGGER_TIMING" IS '业务触发时机。';
COMMENT ON COLUMN "PROCESS_ACTION"."EXECUTION_MODE" IS '执行方式：IN_TRANSACTION/AFTER_COMMIT。';
COMMENT ON COLUMN "PROCESS_ACTION"."FAILURE_POLICY" IS '失败策略：ROLLBACK/CONTINUE/RETRY/IGNORE。';
COMMENT ON COLUMN "PROCESS_ACTION"."RETRY_CONFIG" IS '重试配置JSON。';
COMMENT ON COLUMN "PROCESS_ACTION"."ACTION_DEFINITION_ID" IS '动作定义目录ID。';
COMMENT ON COLUMN "PROCESS_ACTION"."ACTION_NAME" IS '动作名称。';
COMMENT ON COLUMN "PROCESS_ACTION"."DESCRIPTION" IS '说明：动作描述。';
COMMENT ON COLUMN "PROCESS_ACTION"."INTERFACE_NAME" IS '接口名称（Spring Bean或类名）。';
COMMENT ON COLUMN "PROCESS_ACTION"."PARAMS_JSON" IS '参数JSON。';
COMMENT ON COLUMN "PROCESS_ACTION"."SORT_ORDER" IS '排序号：执行顺序。';
COMMENT ON COLUMN "PROCESS_ACTION"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "PROCESS_ACTION"."STATUS" IS '状态：DRAFT/PUBLISHED/DISABLED。';
COMMENT ON COLUMN "PROCESS_ACTION"."VERSION_ID" IS '所属版本ID。';
COMMENT ON COLUMN "PROCESS_ACTION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_ACTION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_ACTION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "PROCESS_ACTION"."DELETED" IS '逻辑删除标记：是否删除 0-未删除 1-已删除。';
COMMENT ON COLUMN "PROCESS_ACTION"."FAILURE_STRATEGY_CODE" IS '失败策略编码，动作执行失败时用于选择对应处置逻辑';
COMMENT ON COLUMN "PROCESS_ACTION"."FAILURE_STRATEGY_VERSION" IS '失败策略版本，保证发布配置绑定稳定的策略实现';
COMMENT ON COLUMN "PROCESS_ACTION"."FAILURE_STRATEGY_CONFIG" IS '失败策略参数文档，供重试、终止或人工处置逻辑解析';
COMMENT ON TABLE "PROCESS_ACTION_DEFINITION" IS '流程动作处理器目录表';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."ID" IS '主键ID：主键。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."ACTION_CODE" IS '稳定动作编码，默认使用Spring Bean名称。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."DISPLAY_NAME" IS '动作中文名称。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."DESCRIPTION" IS '说明：动作用途说明。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."HANDLER_NAME" IS '处理器名称：FlowActionHandler Bean名称。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."VISIBILITY_SCOPE" IS '可见范围：GLOBAL/ENTITY。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."ENABLED" IS '是否启用：是否允许在流程设计器中选择。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION"."DELETED" IS '逻辑删除标记：逻辑删除标识：0-未删除，1-已删除。';
COMMENT ON TABLE "PROCESS_ACTION_DEFINITION_ENTITY" IS '流程动作可见实体表';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION_ENTITY"."ID" IS '主键ID：主键（UUID）。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION_ENTITY"."ACTION_DEFINITION_ID" IS '动作定义 ID。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION_ENTITY"."ENTITY_CODE" IS '可见的实体编码。';
COMMENT ON COLUMN "PROCESS_ACTION_DEFINITION_ENTITY"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "PROCESS_ACTION_EXECUTION" IS '流程动作执行与重试表';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ID" IS '主键ID：执行记录主键。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ACTION_ID" IS '动作ID：关联的 process_action 动作配置 ID。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ACTION_NAME" IS '动作名称快照。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."HANDLER_NAME" IS '处理器Bean名称快照。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."HANDLER_DISPLAY_NAME" IS '处理器中文名称快照。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."VERSION_ID" IS '所属流程发布版本 ID。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."PROCESS_INSTANCE_ID" IS '流程实例 ID。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."PROCESS_DEFINITION_ID" IS 'Flowable 流程定义 ID。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."EXECUTION_ID" IS 'Flowable 执行实例 ID。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."TASK_ID" IS '任务 ID（任务级动作）。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ENTITY_CODE" IS '实体编码快照。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."SCOPE_TYPE" IS '作用域类型：PROCESS、NODE、SEQUENCE_FLOW。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ELEMENT_ID" IS '绑定的 BPMN 元素 ID。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."TRIGGER_TIMING" IS '触发时机编码。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."IDEMPOTENCY_KEY" IS '幂等键，防止同一动作重复执行。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."PAYLOAD_JSON" IS '触发事件序列化 JSON（执行上下文载荷）。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."RESOLVED_PARAMS_JSON" IS '表达式解析后的动作参数。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."RESULT_JSON" IS '动作执行结果。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."EXECUTION_TRACE_JSON" IS '结构化执行步骤轨迹。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."STATUS" IS '状态：执行状态，对应 Status。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."OWNER_ID" IS '持有者ID：当前执行租约所有者。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."LEASE_TOKEN" IS '租约代次：单调递增 fencing token。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."LEASE_UNTIL" IS '租约截止时间：当前执行租约到期时间（数据库 UTC）。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."RETRY_COUNT" IS '已重试次数。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."MAX_RETRIES" IS '最大重试次数。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."NEXT_RETRY_TIME" IS '下次重试时间。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ERROR_MESSAGE" IS '错误信息（截断）。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ERROR_STACK" IS '异常堆栈。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."STARTED_AT" IS '开始执行时间。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."FINISHED_AT" IS '完成时间。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."DURATION_MS" IS '执行耗时毫秒。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."UPDATE_TIME" IS '更新时间。';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."FAILURE_STRATEGY_SNAPSHOT" IS '执行时的失败策略快照，后续重试沿用触发时的配置';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ATTEMPT_NO" IS '执行尝试序号，每次重试递增，供审计和幂等判断';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."ATTEMPT_LEASE_TOKEN" IS '尝试租约令牌，防止过期执行器提交重试结果';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."TERMINATION_REASON" IS '执行终止原因，区分重试耗尽、人工停止等结束路径';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."RESOLUTION_STATUS" IS '失败处置状态，跟踪自动重试或人工处理结果';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."REPLAY_ROOT_ID" IS '重放链根执行ID，用于聚合同一原始动作的后续执行';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."REPLAY_OF_ID" IS '被重放的执行ID，用于追踪当前执行的来源';
COMMENT ON COLUMN "PROCESS_ACTION_EXECUTION"."HANDLER_IDEMPOTENCY_KEY" IS '处理器幂等键，跨重试和重放避免重复副作用';
COMMENT ON TABLE "PROCESS_ASSIGNEE_INCIDENT" IS '空办理人阻断事件表';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."PROCESS_CONFIG_ID" IS '流程配置ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."PROCESS_DEFINITION_ID" IS '流程定义ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."TASK_ID" IS '任务ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."NODE_ID" IS '节点ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."NODE_NAME" IS '节点名称。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."POLICY" IS '策略。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."STATUS" IS '状态。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."EMPTY_REASON_CODE" IS '为空原因编码。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."EMPTY_REASON_MESSAGE" IS '为空原因消息。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."RESOLVER_CODE" IS '解析器编码。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."RESOLVER_EXTRA_PARAMS_JSON" IS '解析器额外参数JSON。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."FALLBACK_USER" IS '后备用户。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."FALLBACK_GROUP" IS '后备用户组。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."RESPONSIBILITY_OWNER" IS '责任所有者。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."RETRY_COUNT" IS '重试数量。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."MAX_RETRIES" IS '最大重试次数。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."INITIAL_DELAY_SECONDS" IS '初始延迟秒数。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."BACKOFF_MULTIPLIER" IS '退避倍数。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."NEXT_RETRY_AT" IS '下一次重试时间。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."RESOLUTION_ACTION" IS '处置动作。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."RESOLVED_BY" IS '解析完成人员。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."RESOLVED_AT" IS '解析完成时间。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."DETAIL_JSON" IS '详情JSON。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT"."OPEN_SLOT" IS '开放占位；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "PROCESS_ASSIGNEE_INCIDENT_ACTION" IS '空办理人处置审计表';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."INCIDENT_ID" IS '事件ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."REQUEST_ID" IS '幂等请求ID。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."ACTION_TYPE" IS '动作类型。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."STATUS" IS '状态。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."OPERATOR" IS '操作人。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."REQUEST_JSON" IS '请求JSON。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."RESULT_JSON" IS '结果JSON。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."ERROR_MESSAGE" IS '错误消息。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_ASSIGNEE_INCIDENT_ACTION"."FINISHED_AT" IS '完成时间。';
COMMENT ON TABLE "PROCESS_CC_RECORD" IS '流程抄送记录表';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."PROCESS_DEFINITION_ID" IS '流程定义ID。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."PROCESS_KEY" IS '流程Key。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."PROCESS_NAME" IS '流程名称。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."DATA_NAME" IS '流程数据名称快照：创建知会时保存业务数据当时的名称，供知会列表和通知展示。后续业务数据改名不更新此值；历史记录保持为空，不回查当前业务表补写。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."BUSINESS_KEY" IS '业务Key。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."NODE_ID" IS '节点ID。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."NODE_NAME" IS '节点名称。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."CC_USER_ID" IS '抄送人ID。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."CC_USER_NAME" IS '抄送人名称。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."CC_TYPE" IS '抄送类型：AUTO自动/MANUAL手动。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."CC_TIMING" IS '抄送时机。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."OPERATOR_ID" IS '操作人ID。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."OPERATOR_NAME" IS '操作人名称。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."COMMENT" IS '知会备注。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."SOURCE_TASK_ID" IS '来源任务ID。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."SOURCE_TYPE" IS '来源类型。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."RECIPIENT_RULE_SNAPSHOT" IS '收件人规则快照。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."UNIQUE_KEY" IS '幂等键。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."READ_STATUS" IS '阅读状态：UNREAD未读/READ已读。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."READ_TIME" IS '阅读时间。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_CC_RECORD"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "PROCESS_DEFINITION_CONFIG" IS '流程定义配置表';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."ID" IS '主键ID，供流程定义配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."PROCESS_KEY" IS '流程标识。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."PROCESS_NAME" IS '流程名称。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."DESCRIPTION" IS '说明：流程描述。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."CATEGORY" IS '流程分类。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."VERSION" IS '版本号。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."STATUS" IS '状态：DRAFT草稿/PUBLISHED已发布/DISABLED已禁用。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."BPMN_XML" IS 'BPMN XML内容。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."DRAFT_REVISION" IS '草稿修订号：流程草稿修订号。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."PUBLISHED_REVISION" IS '发布修订号：最近发布对应的草稿修订号。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."DRAFT_HASH" IS '当前流程草稿 SHA-256。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."PUBLISHED_DRAFT_HASH" IS '最近发布流程草稿 SHA-256。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."BASE_PUBLISHED_VERSION" IS '当前草稿基于的已发布版本。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."DELETED" IS '逻辑删除标记：是否删除 0-未删除 1-已删除。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."ENTITY_ID" IS '绑定实体ID。';
COMMENT ON COLUMN "PROCESS_DEFINITION_CONFIG"."UPDATED_BY" IS '修改人：更新人。';
COMMENT ON TABLE "PROCESS_ENTITY_STATUS_MAPPING" IS '实体流程状态映射表';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."PROCESS_CONFIG_ID" IS '流程定义配置ID。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."PROCESS_KEY" IS '流程标识。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."SEQUENCE_FLOW_ID" IS '连线ID（BPMN中的sequenceFlowId）。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."SOURCE_NODE_ID" IS '源节点ID。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."SOURCE_NODE_NAME" IS '源节点名称。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."TARGET_NODE_ID" IS '目标节点ID。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."TARGET_NODE_NAME" IS '目标节点名称。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."ENTITY_STATUS_CODE" IS '实体状态编码（关联entity_status表）。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."CONDITION_EXPRESSION" IS '条件表达式。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."DESCRIPTION" IS '说明描述。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."ENTITY_STATUS" IS '实体数据状态值（如:审批中、已通过、已驳回）。';
COMMENT ON COLUMN "PROCESS_ENTITY_STATUS_MAPPING"."STATUS_CATEGORY" IS '状态分类：NEW-新建流程状态、PROCESSING-审批中流程状态、COMPLETED-已完成流程状态、TERMINATED-终止流程状态。';
COMMENT ON TABLE "PROCESS_FORM_CONFIG" IS '流程节点表单配置表';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."ID" IS '主键ID，供流程节点表单配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."NODE_CONFIG_ID" IS '所属节点配置ID。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."FORM_NAME" IS '表单名称。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."FORM_KEY" IS '表单标识。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."DESCRIPTION" IS '说明：表单描述。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."IS_READONLY" IS '是否只读。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."ENTITY_FORM_ID" IS '实体表单ID。';
COMMENT ON COLUMN "PROCESS_FORM_CONFIG"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "PROCESS_FORM_FIELD_CONFIG" IS '流程节点表单字段表';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."ID" IS '主键ID，供流程节点表单字段表中的记录关联；由数据库分配';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."FORM_CONFIG_ID" IS '所属表单配置ID。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."FIELD_NAME" IS '字段名称。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."FIELD_KEY" IS '字段标识。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."FIELD_TYPE" IS '字段类型。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."IS_REQUIRED" IS '是否必填。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."DEFAULT_VALUE" IS '默认值。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."OPTIONS_JSON" IS '选项配置JSON。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."VALIDATE_RULES" IS '验证规则JSON。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."SORT_ORDER" IS '排序号：排序顺序。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_FORM_FIELD_CONFIG"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "PROCESS_NODE_APPROVAL" IS '流程节点审批配置表';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."ID" IS '主键ID：流程节点审批配置。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."PROCESS_CONFIG_ID" IS '流程配置ID。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."NODE_ID" IS '节点ID（bpmn元素ID）。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."NODE_NAME" IS '节点名称。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."ENABLED" IS '是否启用审批意见：0-否 1-是。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."COMMENT_LABEL" IS '审批意见标签。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."OPTIONS_JSON" IS '选项配置JSON：审批选项JSON。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "PROCESS_NODE_APPROVAL_OPTION" IS '流程节点审批选项表';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."APPROVAL_CONFIG_ID" IS '审批配置ID：关联的审批配置ID。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."OPTION_VALUE" IS '选项值（如 approve/reject/return）。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."OPTION_LABEL" IS '选项显示名称。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."STYLE_TYPE" IS '按钮样式类型（如 primary/success/danger）。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."SHOW_COMMENT" IS '是否显示意见输入：是否显示审批意见输入框。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."REMARK_REQUIRED" IS '是否要求备注：是否强制要求填写备注。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."OPTION_DOCUMENT" IS '审批项扩展JSON文档。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_NODE_APPROVAL_OPTION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "PROCESS_NODE_ASSIGNEE" IS '流程节点办理人配置表';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."ID" IS '主键ID，供流程节点办理人配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."NODE_CONFIG_ID" IS '所属节点配置ID。';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."ASSIGNEE_TYPE" IS '审批人类型。';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."ASSIGNEE_VALUE" IS '审批人值。';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."ASSIGNEE_NAME" IS '审批人显示名称。';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."PRIORITY" IS '优先级。';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_NODE_ASSIGNEE"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "PROCESS_NODE_CONFIG" IS '流程节点配置表';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."ID" IS '主键ID，供流程节点配置表中的记录关联；由数据库分配';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."NODE_ID" IS '节点ID。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."NODE_NAME" IS '节点名称。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."NODE_TYPE" IS '节点类型。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."PROCESS_CONFIG_ID" IS '所属流程配置ID。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."CONFIG_JSON" IS '扩展配置JSON。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."SKIP_NODE" IS '是否跳过节点。';
COMMENT ON COLUMN "PROCESS_NODE_CONFIG"."DELETED" IS '逻辑删除标记：是否删除。';
COMMENT ON TABLE "PROCESS_NODE_FORM" IS '流程节点实体表单绑定表';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."PROCESS_CONFIG_ID" IS '流程配置ID。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."NODE_ID" IS '节点ID（bpmn元素ID）。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."NODE_NAME" IS '节点名称。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."FORM_ID" IS '表单ID。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."IS_READONLY" IS '是否只读：0-否 1-是。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_NODE_FORM"."SORT_ORDER" IS '排序号。';
COMMENT ON TABLE "PROCESS_OPERATION_LOG" IS '流程操作日志表';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."TASK_ID" IS '关联的任务ID。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."OPERATION_TYPE" IS '操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."OPERATOR_ID" IS '操作人ID。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."OPERATOR_NAME" IS '操作人姓名。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."OPERATION_TIME" IS '操作时间。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."OPERATION_COMMENT" IS '操作备注/审批意见。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."OLD_VALUE" IS '旧值（JSON）。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."NEW_VALUE" IS '新值（JSON）。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."IP_ADDRESS" IS '操作来源IP地址。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."USER_AGENT" IS 'User-Agent：操作来源客户端User-Agent。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."OLD_VALUE_FORMAT" IS '原值格式。';
COMMENT ON COLUMN "PROCESS_OPERATION_LOG"."NEW_VALUE_FORMAT" IS '新值格式。';
COMMENT ON TABLE "PROCESS_PERSON_RESOLVER_DEFINITION" IS '受控人员解析器目录表';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."ID" IS '主键ID：人员解析器定义ID。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."RESOLVER_CODE" IS '稳定解析器编码。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."DISPLAY_NAME" IS '中文名称。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."DESCRIPTION" IS '说明：用途说明。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."BEAN_NAME" IS 'Spring Bean名称。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."IMPLEMENTATION_VERSION" IS '实现版本。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."CONTRACT_VERSION" IS '平台契约版本。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."SUPPORTED_USAGES_DOCUMENT" IS '支持的用途集合文档。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."EXTRA_PARAM_SCHEMA_DOCUMENT" IS '额外参数结构文档。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."DYNAMIC_EXTRA_PARAMS" IS '是否允许动态extraParams。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."ENABLED" IS '是否启用：是否允许在流程配置中选择。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."REVISION" IS '修订号：目录修订号。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_PERSON_RESOLVER_DEFINITION"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "PROCESS_STATUS_SYNC_EVENT" IS '流程实体状态同步事件表';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."EVENT_TYPE" IS '事件类型。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."EVENT_SEQUENCE" IS '事件序列。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."ENTITY_RECORD_ID" IS '实体记录ID。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."TARGET_STATUS" IS '目标状态。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."STATUS_CATEGORY" IS '状态分类。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."STATE" IS '处理状态。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."APPLIED_AT" IS '应用时间。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_STATUS_SYNC_EVENT"."UPDATE_TIME" IS '更新时间。';
COMMENT ON TABLE "PROCESS_TASK" IS '平台流程任务表';
COMMENT ON COLUMN "PROCESS_TASK"."ID" IS '主键ID，供平台流程任务表中的记录关联；由数据库分配';
COMMENT ON COLUMN "PROCESS_TASK"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "PROCESS_TASK"."PROCESS_DEFINITION_ID" IS '流程定义ID。';
COMMENT ON COLUMN "PROCESS_TASK"."PROCESS_KEY" IS '流程标识。';
COMMENT ON COLUMN "PROCESS_TASK"."PROCESS_NAME" IS '流程名称。';
COMMENT ON COLUMN "PROCESS_TASK"."NODE_ID" IS '节点ID。';
COMMENT ON COLUMN "PROCESS_TASK"."NODE_NAME" IS '节点名称。';
COMMENT ON COLUMN "PROCESS_TASK"."NODE_TYPE" IS '节点类型。';
COMMENT ON COLUMN "PROCESS_TASK"."TASK_ID" IS 'Flowable任务ID。';
COMMENT ON COLUMN "PROCESS_TASK"."BUSINESS_KEY" IS '业务主键。';
COMMENT ON COLUMN "PROCESS_TASK"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "PROCESS_TASK"."ENTITY_DATA_ID" IS '实体数据ID。';
COMMENT ON COLUMN "PROCESS_TASK"."ASSIGNEE_ID" IS '执行人ID。';
COMMENT ON COLUMN "PROCESS_TASK"."ASSIGNEE_NAME" IS '执行人姓名。';
COMMENT ON COLUMN "PROCESS_TASK"."ASSIGNEE_TYPE" IS '执行人类型: user/group/role。';
COMMENT ON COLUMN "PROCESS_TASK"."FORM_KEY" IS '表单标识。';
COMMENT ON COLUMN "PROCESS_TASK"."FORM_DATA" IS '表单数据。';
COMMENT ON COLUMN "PROCESS_TASK"."STATUS" IS '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回。';
COMMENT ON COLUMN "PROCESS_TASK"."ACTION" IS '操作: approve/reject/transfer/skip。';
COMMENT ON COLUMN "PROCESS_TASK"."ACTION_LABEL" IS '操作显示文本，如"同意，需要会签"。';
COMMENT ON COLUMN "PROCESS_TASK"."COMMENT" IS '审批意见。';
COMMENT ON COLUMN "PROCESS_TASK"."START_TIME" IS '任务开始时间。';
COMMENT ON COLUMN "PROCESS_TASK"."END_TIME" IS '任务结束时间。';
COMMENT ON COLUMN "PROCESS_TASK"."DUE_TIME" IS '截止时间。';
COMMENT ON COLUMN "PROCESS_TASK"."SLA_STATUS" IS 'SLA综合状态。';
COMMENT ON COLUMN "PROCESS_TASK"."RESPONSE_DUE_TIME" IS '响应截止时间：首次响应截止时间。';
COMMENT ON COLUMN "PROCESS_TASK"."DURATION" IS '处理耗时(毫秒)。';
COMMENT ON COLUMN "PROCESS_TASK"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_TASK"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "PROCESS_TASK"."DELETED" IS '逻辑删除标记：删除标记: 0-正常 1-删除。';
COMMENT ON COLUMN "PROCESS_TASK"."TIMEOUT_HOURS" IS '超时时间。';
COMMENT ON COLUMN "PROCESS_TASK"."TIMEOUT_ACTION" IS '超时策略。';
COMMENT ON COLUMN "PROCESS_TASK"."TIMEOUT_HANDLED" IS '是否已处理超时。';
COMMENT ON COLUMN "PROCESS_TASK"."PRIORITY" IS '优先级。';
COMMENT ON COLUMN "PROCESS_TASK"."START_USER_ID" IS '发起人身份，兼容用户ID和用户名';
COMMENT ON COLUMN "PROCESS_TASK"."BUSINESS_NAME" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "PROCESS_TASK"."BUSINESS_CODE" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "PROCESS_TASK"."BUSINESS_DATA_NAME" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "PROCESS_TASK"."BUSINESS_CURRENT_TASK_NAME" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "PROCESS_TASK"."BUSINESS_STATUS" IS '任务列表业务摘要，与业务当前值同步';
COMMENT ON COLUMN "PROCESS_TASK"."INBOX_SUMMARY_READY" IS '摘要已回填；0时保留旧读取语义';
COMMENT ON COLUMN "PROCESS_TASK"."INBOX_IDENTITY_READY" IS '候选身份已从引擎最终状态同步';
COMMENT ON TABLE "PROCESS_TASK_ADD_SIGN" IS '运行时加签记录表';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."SOURCE_TASK_ID" IS '触发加签的源任务ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."NODE_ID" IS '加签所在节点ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."OPERATION_TYPE" IS '加签类型（如 before前加签/after后加签/parallel并行加签）。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."OPERATOR_ID" IS '操作人ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."COMMENT" IS '加签操作备注。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."STATUS" IS '状态：加签状态：PENDING-进行中，COMPLETED-已完成，CANCELED-已取消。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."ENGINE_EXECUTION_ID" IS 'Flowable引擎执行实例ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."SOURCE_COMPLETED" IS '原任务是否已提交。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."SOURCE_ACTION" IS '原任务提交动作。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."SOURCE_ACTION_LABEL" IS '原任务动作名称。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."SOURCE_COMMENT" IS '原任务审批意见。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."SOURCE_FORM_DATA" IS '原任务表单数据JSON。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN"."COMPLETE_TIME" IS '加签完成时间。';
COMMENT ON TABLE "PROCESS_TASK_ADD_SIGN_USER" IS '运行时加签人员表';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."ADD_SIGN_ID" IS '加签记录ID：关联的加签操作ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."USER_ID" IS '被加签的用户ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."USER_NAME_SNAPSHOT" IS '用户名称快照：加签时的用户姓名快照。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."GENERATED_TASK_ID" IS '生成的任务ID：加签生成的Flowable任务ID。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."STATUS" IS '状态：用户任务状态：PENDING-待处理，COMPLETED-已完成。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."SORT_ORDER" IS '排序号（控制串行加签顺序）。';
COMMENT ON COLUMN "PROCESS_TASK_ADD_SIGN_USER"."COMPLETE_TIME" IS '用户处理完成时间。';
COMMENT ON TABLE "PROCESS_TASK_CANDIDATE_GROUP" IS '任务候选组表';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_GROUP"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_GROUP"."PROCESS_TASK_ID" IS '平台任务主键，关联 process_task.id，供候选组查询与清理';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_GROUP"."GROUP_CODE" IS '候选组编码（角色/部门等）。';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_GROUP"."SORT_ORDER" IS '排序号，控制候选组处理顺序。';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_GROUP"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "PROCESS_TASK_CANDIDATE_USER" IS '任务候选用户表';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_USER"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_USER"."PROCESS_TASK_ID" IS '平台任务主键，关联 process_task.id，供候选用户查询与清理';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_USER"."USER_ID" IS '候选用户ID。';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_USER"."SORT_ORDER" IS '排序号，控制候选用户处理顺序。';
COMMENT ON COLUMN "PROCESS_TASK_CANDIDATE_USER"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "PROCESS_TASK_SLA" IS '任务时效运行台账表';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."TASK_ID" IS '任务ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."PROCESS_INSTANCE_ID" IS '流程实例ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."PROCESS_DEFINITION_ID" IS '流程定义ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."PROCESS_KEY" IS '流程键。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."NODE_ID" IS '节点ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."NODE_NAME" IS '节点名称。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."BUSINESS_KEY" IS '业务键。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."ENTITY_CODE" IS '实体编码。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."ENTITY_DATA_ID" IS '实体数据ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."POLICY_CODE" IS '策略编码。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."POLICY_VERSION" IS '策略版本。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."POLICY_SNAPSHOT_JSON" IS '策略快照JSON。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."CALENDAR_CODE" IS '日历编码。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."CALENDAR_VERSION" IS '日历版本。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."CALENDAR_SNAPSHOT_JSON" IS '日历快照JSON。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."TIMEZONE_ID" IS '时区ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."CURRENT_ASSIGNEE_ID" IS '当前办理人ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."STARTED_AT" IS '开始时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."RESPONDED_AT" IS '已响应时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."COMPLETED_AT" IS '完成时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."RESPONSE_DUE_AT" IS '响应截止时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."COMPLETION_DUE_AT" IS '完成截止时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."RESPONSE_REMAINING_MINUTES" IS '剩余响应分钟数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."COMPLETION_REMAINING_MINUTES" IS '剩余完成分钟数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."RESPONSE_STATUS" IS '响应状态。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."COMPLETION_STATUS" IS '完成状态。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."OVERALL_STATUS" IS '整体状态。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."PAUSE_STARTED_AT" IS '暂停开始时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."VERSION" IS '版本号。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "PROCESS_TASK_SLA_EVENT" IS '时效到期执行事件表';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."SLA_ID" IS '时效ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."TASK_ID" IS '任务ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."STEP_ID" IS '步骤ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."EVENT_TYPE" IS '事件类型。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."METRIC_TYPE" IS '指标类型。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."TRIGGER_AT" IS '触发时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."ACTION_TYPE" IS '动作类型。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."ACTION_CONFIG_SNAPSHOT" IS '动作配置快照。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."EXECUTION_NO" IS '执行编号。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."MAX_EXECUTIONS" IS '最大执行次数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."STATUS" IS '状态。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."ATTEMPTS" IS '尝试次数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."MAX_RETRIES" IS '最大重试次数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."NEXT_RETRY_TIME" IS '下一次重试时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."OWNER_ID" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."LEASE_TOKEN" IS '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."LEASE_UNTIL" IS '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."IDEMPOTENCY_KEY" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."RESULT_JSON" IS '结果JSON。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."ERROR_MESSAGE" IS '错误消息。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."STARTED_AT" IS '开始时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."FINISHED_AT" IS '完成时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_EVENT"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "PROCESS_TASK_SLA_PAUSE" IS '任务时效暂停历史表';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."ID" IS '主键ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."SLA_ID" IS '时效ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."TASK_ID" IS '任务ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."PAUSE_TYPE" IS '暂停类型。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."REASON" IS '原因。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."OPERATOR_ID" IS '操作人ID。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."STARTED_AT" IS '开始时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."RESUMED_AT" IS '恢复时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."DURATION_SECONDS" IS '耗时秒数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."RESPONSE_REMAINING_MINUTES" IS '剩余响应分钟数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."COMPLETION_REMAINING_MINUTES" IS '剩余完成分钟数。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_TASK_SLA_PAUSE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "PROCESS_UI_RELEASE_BINDING" IS '流程与界面发布绑定表';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."ID" IS '主键ID：绑定记录ID。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."PROCESS_VERSION_HISTORY_ID" IS '流程发布历史ID。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."PROCESS_CONFIG_ID" IS '流程配置ID。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."PROCESS_KEY" IS '流程标识。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."PROCESS_VERSION" IS '流程版本号。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."DEPLOYMENT_ID" IS 'Flowable部署ID。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."NODE_ID" IS '流程节点ID。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."NODE_NAME" IS '流程节点名称。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."CONFIG_TYPE" IS '配置类型。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."CONFIG_ID" IS '表单或列表配置ID。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."PINNED_RELEASE_ID" IS '流程发布时固定的UI发布ID。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."PINNED_RELEASE_VERSION" IS '流程发布时固定的UI版本号。';
COMMENT ON COLUMN "PROCESS_UI_RELEASE_BINDING"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "PROCESS_VERSION_HISTORY" IS '流程发布历史表';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."ID" IS '主键ID，供流程发布历史表中的记录关联；由数据库分配';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."PROCESS_CONFIG_ID" IS '流程定义ID。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."PROCESS_KEY" IS '流程标识。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."PROCESS_NAME" IS '流程名称。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."VERSION" IS '版本号。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."VERSION_DESCRIPTION" IS '版本描述/发布说明。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."BPMN_XML" IS 'BPMN XML内容。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."PUBLISHED_AT" IS '发布时间。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."PUBLISHED_BY" IS '发布人ID。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."DEPLOYMENT_ID" IS 'Flowable部署ID。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."STATUS" IS '状态：ACTIVE-有效，ARCHIVED-已归档。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."DELETED" IS '逻辑删除标记：是否删除 0-未删除 1-已删除。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."NODE_FORMS_SNAPSHOT" IS '节点表单绑定快照JSON。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "PROCESS_VERSION_HISTORY"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "STORAGE_FILE_OBJECT" IS '文件对象归属表';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."ID" IS '主键ID。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."STORAGE_URL" IS '存储URL。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."STORAGE_KEY" IS '存储键。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."OWNER_USER_ID" IS '所有者用户ID。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."IDEMPOTENCY_KEY" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."REQUEST_HASH" IS '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."ORIGINAL_NAME" IS '原始名称。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."CONTENT_TYPE" IS '内容类型。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."CONTENT_LENGTH" IS '内容长度。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."DELETED" IS '逻辑删除标记。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "STORAGE_FILE_OBJECT"."UPDATE_TIME" IS '更新时间。';
COMMENT ON TABLE "SYS_DICT" IS '系统字典类型表';
COMMENT ON COLUMN "SYS_DICT"."ID" IS '主键ID。';
COMMENT ON COLUMN "SYS_DICT"."DICT_CODE" IS '字典编码。';
COMMENT ON COLUMN "SYS_DICT"."DICT_NAME" IS '字典名称。';
COMMENT ON COLUMN "SYS_DICT"."DESCRIPTION" IS '说明：描述。';
COMMENT ON COLUMN "SYS_DICT"."STATUS" IS '状态：0-启用 1-禁用。';
COMMENT ON COLUMN "SYS_DICT"."SORT" IS '排序。';
COMMENT ON COLUMN "SYS_DICT"."DELETED" IS '逻辑删除标记：逻辑删除。';
COMMENT ON COLUMN "SYS_DICT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_DICT"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "SYS_DICT_ITEM" IS '系统字典明细表';
COMMENT ON COLUMN "SYS_DICT_ITEM"."ID" IS '主键ID。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."DICT_ID" IS '所属字典ID。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."DICT_CODE" IS '冗余：字典编码（便于直接查询）。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."PARENT_ID" IS '父项ID，0表示顶级。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."ITEM_CODE" IS '项编码。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."ITEM_LABEL" IS '项标签/显示文本。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."ITEM_VALUE" IS '项值。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."SORT" IS '排序。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."STATUS" IS '状态：0-启用 1-禁用。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."REMARK" IS '备注。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."DELETED" IS '逻辑删除标记：逻辑删除。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_DICT_ITEM"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "SYS_EXTERNAL_SYSTEM" IS '外部系统基础信息表';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."ID" IS '记录ID：外部系统ID';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."SYSTEM_NAME" IS '系统名称：外部系统名称';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."SYSTEM_CODE" IS '系统编码：外部系统稳定编码，删除后也不得复用';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."STATUS" IS '状态：0-启用 1-禁用';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."ADDRESS" IS '系统地址：外部系统地址';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."DESCRIPTION" IS '说明：描述';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."VERSION" IS '版本号：乐观锁版本号';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."CREATED_BY" IS '创建人';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."UPDATED_BY" IS '更新人';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."CREATE_TIME" IS '创建时间';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."UPDATE_TIME" IS '更新时间；更新时自动刷新。';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM"."DELETED" IS '逻辑删除标记：逻辑删除：0-正常 1-删除';
COMMENT ON TABLE "SYS_EXTERNAL_SYSTEM_PARAMETER" IS '外部系统扩展参数表';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."ID" IS '记录ID：参数ID';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."EXTERNAL_SYSTEM_ID" IS '外部系统ID';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."PARAMETER_NAME_ZH" IS '参数中文名';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."PARAMETER_NAME_EN" IS '参数英文名';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."PARAMETER_VALUE" IS '参数值：普通配置参数值，敏感凭据应使用受控密钥存储';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."SORT_ORDER" IS '显示顺序';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."CREATED_BY" IS '创建人';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."UPDATED_BY" IS '更新人';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."CREATE_TIME" IS '创建时间';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."UPDATE_TIME" IS '更新时间；更新时自动刷新。';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."DELETED" IS '逻辑删除标记：逻辑删除：0-正常 1-删除';
COMMENT ON COLUMN "SYS_EXTERNAL_SYSTEM_PARAMETER"."ACTIVE_PARAMETER_NAME_EN" IS '活动参数英文名：仅活动参数参与英文名唯一约束；生成表达式：``(case when (`deleted` = 0) then `parameter_name_en` else NULL end)``';
COMMENT ON TABLE "SYS_GLOBAL_SETTING" IS '全局设置与个人偏好表';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."ID" IS '设置记录ID：由应用分配。修改、恢复默认时与 version 一起定位原记录；删除后重建使用新 ID，防止旧请求覆盖新设置。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."SCOPE_TYPE" IS '设置作用域：SYSTEM 为系统值，USER 为个人覆盖值；服务端按注册规则限制某项设置允许使用的作用域。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."OWNER_ID" IS '设置归属：SYSTEM 固定为字符串 0；USER 保存 sys_user.id。个人接口从当前登录身份获取此值，不接受客户端指定其他用户。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."SETTING_KEY" IS '稳定设置键：程序按此键读取设置，例如 ui.layout.tabs_enabled。键须在 GlobalSettingRegistry 注册，单独向表中插入一个键不会自动增加功能。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."NAME" IS '设置名称：在管理界面展示该设置的用途。由注册定义提供，不能代替 setting_key 参与业务匹配。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."SETTING_VALUE_TYPE" IS '设置值类型：BOOLEAN、NUMBER、STRING 或 JSON；后端据此校验值，前端据此选择输入控件。同一设置键的系统值和个人值必须使用注册类型。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."SETTING_VALUE" IS '设置值文本：应用按 JSON 格式序列化和解析，例如 true、20、双引号包裹的字符串或对象。不是数据库原生 JSON 列；拒绝顶层 null、类型不符及超过 16 KiB 的文本，合法的 false、0 和空字符串不能当成缺省。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."REMARK" IS '生效规则说明：展示取值含义、作用范围、继承关系和生效时机。说明本身不执行逻辑，实际行为由注册定义及设置使用方实现。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."VERSION" IS '乐观锁版本：更新、删除按 ID 和旧版本共同匹配，成功更新后递增；版本不符时拒绝覆盖，避免多个页面互相覆盖偏好。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."CREATED_BY" IS '创建人：创建设置记录的用户 ID，由服务端填写；迁移初始化等无操作用户的场景可为空。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."UPDATED_BY" IS '最近修改人：最近一次修改设置的用户 ID，用于定位维护人员；历史操作仍由系统审计记录。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."CREATE_TIME" IS '创建时间：记录首次建立时间，修改设置值时保留。';
COMMENT ON COLUMN "SYS_GLOBAL_SETTING"."UPDATE_TIME" IS '更新时间：更新时自动刷新，用于查看最后维护时间；并发控制使用 version。';
COMMENT ON TABLE "SYS_GROUP" IS '系统用户组表';
COMMENT ON COLUMN "SYS_GROUP"."ID" IS '主键ID：组ID。';
COMMENT ON COLUMN "SYS_GROUP"."GROUP_NAME" IS '组名称。';
COMMENT ON COLUMN "SYS_GROUP"."GROUP_CODE" IS '组编码。';
COMMENT ON COLUMN "SYS_GROUP"."DESCRIPTION" IS '说明：描述。';
COMMENT ON COLUMN "SYS_GROUP"."SORT" IS '排序。';
COMMENT ON COLUMN "SYS_GROUP"."STATUS" IS '状态（0启用 1禁用）。';
COMMENT ON COLUMN "SYS_GROUP"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_GROUP"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "SYS_GROUP"."DELETED" IS '逻辑删除标记：删除标志。';
COMMENT ON COLUMN "SYS_GROUP"."PARENT_ID" IS '父组ID。';
COMMENT ON COLUMN "SYS_GROUP"."SORT_ORDER" IS '排序号。';
COMMENT ON TABLE "SYS_MENU" IS '菜单与功能权限表';
COMMENT ON COLUMN "SYS_MENU"."ID" IS '主键ID：菜单ID。';
COMMENT ON COLUMN "SYS_MENU"."PARENT_ID" IS '父菜单ID，0为顶级菜单。';
COMMENT ON COLUMN "SYS_MENU"."MENU_NAME" IS '菜单名称。';
COMMENT ON COLUMN "SYS_MENU"."MENU_TYPE" IS '菜单类型：M-目录 C-菜单 F-按钮。';
COMMENT ON COLUMN "SYS_MENU"."ICON" IS '菜单图标。';
COMMENT ON COLUMN "SYS_MENU"."SORT" IS '显示排序。';
COMMENT ON COLUMN "SYS_MENU"."PATH" IS '路由地址。';
COMMENT ON COLUMN "SYS_MENU"."COMPONENT" IS '组件路径。';
COMMENT ON COLUMN "SYS_MENU"."PERM" IS '权限标识，如：system:user:list。';
COMMENT ON COLUMN "SYS_MENU"."STATUS" IS '状态：0-禁用 1-启用。';
COMMENT ON COLUMN "SYS_MENU"."VISIBLE" IS '显示状态：0-隐藏 1-显示。';
COMMENT ON COLUMN "SYS_MENU"."KEEP_ALIVE" IS '页面保活标记：是否缓存：0-不缓存 1-缓存。';
COMMENT ON COLUMN "SYS_MENU"."BREADCRUMB" IS '面包屑标记：是否显示面包屑：0-否 1-是。';
COMMENT ON COLUMN "SYS_MENU"."REMARK" IS '备注。';
COMMENT ON COLUMN "SYS_MENU"."DELETED" IS '逻辑删除标记：是否删除：0-未删除 1-已删除。';
COMMENT ON COLUMN "SYS_MENU"."CREATE_BY" IS '创建人：创建者。';
COMMENT ON COLUMN "SYS_MENU"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_MENU"."UPDATE_BY" IS '修改人：更新者。';
COMMENT ON COLUMN "SYS_MENU"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "SYS_MENU"."IS_FRAME" IS '是否外链：0-否 1-是。';
COMMENT ON COLUMN "SYS_MENU"."IS_CACHE" IS '缓存标记：是否缓存：0-缓存 1-不缓存。';
COMMENT ON COLUMN "SYS_MENU"."QUERY" IS '路由参数。';
COMMENT ON COLUMN "SYS_MENU"."ENTITY_CODE" IS '关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表。';
COMMENT ON COLUMN "SYS_MENU"."RESOURCE_TYPE" IS '菜单资源类型，ENTITY_LIST 表示动态实体列表。';
COMMENT ON COLUMN "SYS_MENU"."LIST_KEY" IS '实体列表稳定编码。';
COMMENT ON TABLE "SYS_ORGANIZATION" IS '组织部门表';
COMMENT ON COLUMN "SYS_ORGANIZATION"."ID" IS '主键ID。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."ORG_CODE" IS '组织编码（唯一）。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."ORG_NAME" IS '组织名称。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."TYPE" IS '类型：org-组织，dept-部门。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."BUSINESS_LEVEL_CODE" IS '稳定业务层级编码，来源于 organization_business_level 字典。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."PARENT_ID" IS '父级ID（顶级为0）。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."LEVEL" IS '层级（0为顶级）。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."PATH" IS '完整路径，如：/0/1/5/10/。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."LEADER_ID" IS '负责人ID。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."LEADER_NAME" IS '负责人名称（冗余）。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."PHONE" IS '联系电话。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."EMAIL" IS '邮箱。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."ADDRESS" IS '地址。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."STATUS" IS '状态：0-启用，1-禁用。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."DESCRIPTION" IS '说明：描述。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "SYS_ORGANIZATION"."DELETED" IS '逻辑删除标记：是否删除：0-未删除 1-已删除。';
COMMENT ON TABLE "SYS_POSITION" IS '全局职务定义表';
COMMENT ON COLUMN "SYS_POSITION"."ID" IS '主键ID：全局职务定义；职务本身不携带组织范围，也不产生系统权限。';
COMMENT ON COLUMN "SYS_POSITION"."POSITION_CODE" IS '职务编码。';
COMMENT ON COLUMN "SYS_POSITION"."POSITION_NAME" IS '职务名称。';
COMMENT ON COLUMN "SYS_POSITION"."APPLICABLE_UNIT_TYPE" IS '适用单元类型；CHECK 枚举：\\''ORG\\'',\\''DEPT\\'',\\''ANY\\''。';
COMMENT ON COLUMN "SYS_POSITION"."HOLDER_MODE" IS '任职者模式；CHECK 枚举：\\''SINGLE\\'',\\''MULTIPLE\\''。';
COMMENT ON COLUMN "SYS_POSITION"."BUILT_IN" IS '内置内置；CHECK 枚举：0,1。';
COMMENT ON COLUMN "SYS_POSITION"."STATUS" IS '状态；CHECK 枚举：\\''ENABLED\\'',\\''DISABLED\\''。';
COMMENT ON COLUMN "SYS_POSITION"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "SYS_POSITION"."DESCRIPTION" IS '说明。';
COMMENT ON COLUMN "SYS_POSITION"."REVISION" IS '修订号。';
COMMENT ON COLUMN "SYS_POSITION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "SYS_POSITION"."UPDATED_BY" IS '修改人。';
COMMENT ON COLUMN "SYS_POSITION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_POSITION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "SYS_POSITION"."DELETED" IS '逻辑删除标记；CHECK 枚举：0,1。';
COMMENT ON TABLE "SYS_POSITION_ASSIGNMENT" IS '组织职务任职表';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."ID" IS '主键ID：用户在组织节点担任职务的一段不可覆盖任职事实。 有效区间统一为 [effectiveFrom, effectiveTo)，撤销通过 revoked 字段保留历史，重新任职必须新增记录。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."POSITION_ID" IS '职务ID。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."ORGANIZATION_UNIT_ID" IS '组织单元ID。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."USER_ID" IS '用户ID。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."IS_PRIMARY" IS '是否主职；CHECK 枚举：0,1。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."EFFECTIVE_FROM" IS '生效起始时间。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."EFFECTIVE_TO" IS '生效结束时间。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."REVOKED_AT" IS '撤销时间。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."REVOKED_BY" IS '撤销人。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."REVOKE_REASON" IS '撤销原因。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."REVISION" IS '修订号。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."UPDATED_BY" IS '修改人。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "SYS_POSITION_ASSIGNMENT_BATCH" IS '职务批量任命回执表';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT_BATCH"."ID" IS '主键ID：已成功提交的批量任命幂等结果。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT_BATCH"."IDEMPOTENCY_KEY" IS '幂等键：标识同一业务请求的幂等键，具体唯一性范围见本表索引。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT_BATCH"."REQUEST_HASH" IS '请求哈希：请求内容摘要，用于核验幂等重试是否携带相同内容。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT_BATCH"."ASSIGNMENT_IDS_JSON" IS '任职ID集合JSON。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT_BATCH"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "SYS_POSITION_ASSIGNMENT_BATCH"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "SYS_ROLE" IS '系统角色表';
COMMENT ON COLUMN "SYS_ROLE"."ID" IS '主键ID：角色ID。';
COMMENT ON COLUMN "SYS_ROLE"."ROLE_NAME" IS '角色名称。';
COMMENT ON COLUMN "SYS_ROLE"."ROLE_CODE" IS '角色编码。';
COMMENT ON COLUMN "SYS_ROLE"."DESCRIPTION" IS '说明：描述。';
COMMENT ON COLUMN "SYS_ROLE"."SORT" IS '显示排序。';
COMMENT ON COLUMN "SYS_ROLE"."STATUS" IS '状态（0启用 1禁用）。';
COMMENT ON COLUMN "SYS_ROLE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_ROLE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "SYS_ROLE"."DELETED" IS '逻辑删除标记：删除标志（0正常 1删除）。';
COMMENT ON COLUMN "SYS_ROLE"."SORT_ORDER" IS '排序号。';
COMMENT ON TABLE "SYS_ROLE_MENU" IS '角色菜单权限关联表';
COMMENT ON COLUMN "SYS_ROLE_MENU"."ID" IS '主键ID。';
COMMENT ON COLUMN "SYS_ROLE_MENU"."ROLE_ID" IS '角色ID。';
COMMENT ON COLUMN "SYS_ROLE_MENU"."MENU_ID" IS '菜单ID。';
COMMENT ON COLUMN "SYS_ROLE_MENU"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "SYS_USER" IS '系统用户表';
COMMENT ON COLUMN "SYS_USER"."ID" IS '主键ID：用户ID。';
COMMENT ON COLUMN "SYS_USER"."USERNAME" IS '用户名。';
COMMENT ON COLUMN "SYS_USER"."NICKNAME" IS '昵称。';
COMMENT ON COLUMN "SYS_USER"."PASSWORD" IS '密码。';
COMMENT ON COLUMN "SYS_USER"."EMAIL" IS '邮箱。';
COMMENT ON COLUMN "SYS_USER"."PHONE" IS '手机号。';
COMMENT ON COLUMN "SYS_USER"."AVATAR" IS '头像。';
COMMENT ON COLUMN "SYS_USER"."STATUS" IS '状态（0启用 1禁用）。';
COMMENT ON COLUMN "SYS_USER"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "SYS_USER"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "SYS_USER"."DELETED" IS '逻辑删除标记：删除标志（0正常 1删除）。';
COMMENT ON COLUMN "SYS_USER"."ORG_ID" IS '组织ID。';
COMMENT ON COLUMN "SYS_USER"."DEPT_ID" IS '部门ID。';
COMMENT ON COLUMN "SYS_USER"."PASSWORD_RESET_REQUIRED" IS '是否必须在继续使用系统前修改密码。';
COMMENT ON COLUMN "SYS_USER"."TOKEN_VERSION" IS '令牌版本：用户令牌撤销版本；递增后此前签发的会话需按安全校验失效。';
COMMENT ON TABLE "SYS_USER_GROUP" IS '用户用户组关联表';
COMMENT ON COLUMN "SYS_USER_GROUP"."ID" IS '主键ID。';
COMMENT ON COLUMN "SYS_USER_GROUP"."USER_ID" IS '用户ID。';
COMMENT ON COLUMN "SYS_USER_GROUP"."GROUP_ID" IS '组ID。';
COMMENT ON COLUMN "SYS_USER_GROUP"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "SYS_USER_ROLE" IS '用户角色关联表';
COMMENT ON COLUMN "SYS_USER_ROLE"."ID" IS '主键ID。';
COMMENT ON COLUMN "SYS_USER_ROLE"."USER_ID" IS '用户ID。';
COMMENT ON COLUMN "SYS_USER_ROLE"."ROLE_ID" IS '角色ID。';
COMMENT ON COLUMN "SYS_USER_ROLE"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "SYSTEM_OPERATION_LOG" IS '系统关键操作审计表';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."ID" IS '主键ID：只追加的系统关键操作审计日志。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."EVENT_ID" IS '事件ID。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."OPERATION_ID" IS '业务操作ID；同一次跨模块操作共享。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."TRACE_ID" IS '追踪ID。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."PARENT_OPERATION_ID" IS '父业务操作ID。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."SOURCE_SYSTEM" IS '权威来源系统/模块。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."SOURCE_TYPE" IS '权威来源记录类型。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."SOURCE_ID" IS '权威来源记录ID。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."SOURCE_EVENT_ID" IS '权威来源事件ID。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."MODULE_CODE" IS '模块编码。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."OPERATION_CODE" IS '操作编码。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."OPERATION_NAME" IS '操作名称。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."RISK_LEVEL" IS '风险级别。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."RESULT" IS '结果。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."OPERATOR_ID" IS '操作人ID。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."OPERATOR_NAME" IS '操作人名称。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."OPERATOR_IP" IS '操作人IP。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."USER_AGENT" IS 'User-Agent。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."REQUEST_METHOD" IS '请求方法。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."REQUEST_PATH" IS '请求路径。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."TARGET_TYPE" IS '目标类型。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."TARGET_ID" IS '目标ID。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."TARGET_NAME" IS '目标名称。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."SUMMARY" IS '摘要。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."BEFORE_JSON" IS '之前JSON。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."AFTER_JSON" IS '之后JSON。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."CHANGED_FIELDS_JSON" IS '变更标记字段集合JSON。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."PAYLOAD_TRUNCATED" IS '载荷是否被截断。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."ERROR_CODE" IS '错误编码。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."ERROR_MESSAGE" IS '错误消息。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."DURATION_MS" IS '耗时毫秒。';
COMMENT ON COLUMN "SYSTEM_OPERATION_LOG"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "TASK_SLA_ESCALATION_STEP" IS '时效提醒升级步骤表';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."ID" IS '主键ID。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."POLICY_ID" IS '策略ID。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."STEP_NAME" IS '步骤名称。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."METRIC_TYPE" IS '指标类型。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."TRIGGER_TYPE" IS '触发类型。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."OFFSET_MINUTES" IS '偏移分钟数。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."REPEAT_INTERVAL_MINUTES" IS '重复间隔分钟数。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."MAX_EXECUTIONS" IS '最大执行次数。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."ACTION_TYPE" IS '动作类型。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."TEMPLATE_CODE" IS '模板编码。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."RECIPIENT_CONFIG_JSON" IS '接收人配置JSON。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."TARGET_CONFIG_JSON" IS '目标配置JSON。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "TASK_SLA_ESCALATION_STEP"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON TABLE "TASK_SLA_POLICY" IS '用户任务时效策略表';
COMMENT ON COLUMN "TASK_SLA_POLICY"."ID" IS '主键ID。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."POLICY_CODE" IS '策略编码。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."POLICY_NAME" IS '策略名称。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."DESCRIPTION" IS '说明。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."VERSION" IS '版本号。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."RESPONSE_TARGET_MINUTES" IS '响应目标分钟数。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."COMPLETION_TARGET_MINUTES" IS '完成目标分钟数。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."RESPONSE_TIME_BASIS" IS '响应时间口径。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."COMPLETION_TIME_BASIS" IS '完成时间口径。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."ALLOW_MANUAL_PAUSE" IS '允许手动暂停。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."PAUSE_ON_PROCESS_SUSPEND" IS '暂停时间流程挂起。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."MAX_PAUSE_MINUTES" IS '最大暂停分钟数。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."STATUS" IS '状态。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."UPDATED_BY" IS '修改人。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "TASK_SLA_POLICY"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "UI_COMPONENT_TEMPLATE" IS 'UI组件模板目录表';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."ID" IS '主键ID：模板ID。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."TEMPLATE_KEY" IS '稳定模板编码。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."TEMPLATE_NAME" IS '模板名称。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."TEMPLATE_TYPE" IS '模板类型。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."CURRENT_VERSION" IS '当前版本。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."STATUS" IS '状态。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE"."DELETED" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON TABLE "UI_COMPONENT_TEMPLATE_VERSION" IS 'UI组件模板版本表';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."ID" IS '主键ID：模板版本ID。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."TEMPLATE_ID" IS '模板ID。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."VERSION" IS '版本号。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."SNAPSHOT_DOCUMENT" IS '不可变模板快照JSON文档。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."CONTENT_HASH" IS 'SHA-256内容哈希。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."DESCRIPTION" IS '说明：版本说明。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "UI_COMPONENT_TEMPLATE_VERSION"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "UI_CONFIG_HOTFIX_REQUEST" IS 'UI热修复发布与观察记录表';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."ID" IS '主键ID：HOTFIX申请ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."CONFIG_TYPE" IS '配置类型。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."CONFIG_ID" IS '配置ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."DRAFT_HASH" IS '申请时草稿哈希。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."ACTIVE_RELEASE_ID" IS '申请时激活发布ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."TARGET_HASH" IS '影响目标摘要。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."IMPACT_TOKEN_HASH" IS '预检令牌摘要。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."RISK_LEVEL" IS '风险级别。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."REASON" IS '变更原因。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."TICKET_REF" IS '关联工单。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."IMPACT_DOCUMENT" IS '申请时影响预览JSON。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."APPLICANT_ID" IS '申请人ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."APPLICANT_NAME" IS '申请人姓名。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."WINDOW_START" IS '允许发布时间窗口开始。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."WINDOW_END" IS '允许发布时间窗口结束。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."REVIEW_REQUIRED" IS '是否需要人工复核：是否需要独立复核；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."STATUS" IS '状态。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."OPEN_SLOT" IS '每个配置仅允许一个开放申请；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."REVIEWER_ID" IS '复核人ID；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."REVIEWER_NAME" IS '复核人名称：复核人姓名；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."REVIEW_COMMENT" IS '复核意见；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."REVIEWED_AT" IS '复核时间；V065 退役独立人工复核流程；字段保留历史审计，新直接发布不再等待审核。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."RELEASE_ID" IS '实际发布ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."PUBLISHED_AT" IS '发布时间：实际发布时间。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."OBSERVATION_START" IS '观察窗口开始。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."OBSERVATION_END" IS '观察窗口结束。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."OBSERVATION_STATUS" IS '观察状态。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."ROLLED_BACK_BY" IS '回滚人ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."ROLLED_BACK_AT" IS '回滚时间。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."ROLLBACK_REASON" IS '回滚原因。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."CANCELLED_BY" IS '取消人ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."CANCELLED_AT" IS '取消时间。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."CANCEL_REASON" IS '取消原因。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_REQUEST"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON TABLE "UI_CONFIG_HOTFIX_TARGET" IS 'UI热修复目标快照表';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."ID" IS '主键ID：热修复目标ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."HOTFIX_RELEASE_ID" IS '热修复发布ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."CONFIG_TYPE" IS '配置类型。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."CONFIG_ID" IS '配置ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."PROCESS_VERSION_HISTORY_ID" IS '目标流程发布历史ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."PINNED_RELEASE_ID" IS '目标原始钉定发布ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."PINNED_RELEASE_VERSION" IS '目标原始钉定版本号。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."PREVIOUS_TARGET_ID" IS '上一有效热修复目标ID。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."EFFECTIVE_SNAPSHOT_DOCUMENT" IS '目标有效完整快照JSON文档。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."EFFECTIVE_CONTENT_HASH" IS '目标有效快照SHA-256。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."STATUS" IS '状态。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."ACTIVE_SLOT" IS '保证同一流程版本只有一个有效目标；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."ACTIVATED_BY" IS '激活人。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."ACTIVATED_AT" IS '激活时间。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."ROLLED_BACK_BY" IS '撤回人。';
COMMENT ON COLUMN "UI_CONFIG_HOTFIX_TARGET"."ROLLED_BACK_AT" IS '撤回时间。';
COMMENT ON TABLE "UI_CONFIG_RELEASE" IS '表单列表发布快照表';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."ID" IS '主键ID：发布快照ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."CONFIG_TYPE" IS '配置类型。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."CONFIG_ID" IS '表单或列表配置ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."VERSION" IS '版本号：不可变版本号。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."SNAPSHOT_DOCUMENT" IS '完整运行时快照JSON文档。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."CONTENT_HASH" IS 'SHA-256内容哈希。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."STATUS" IS '状态。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."ACTIVE_SLOT" IS '保证同一配置只有一个激活版本；数据库生成，表达式见本表实现说明。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."DESCRIPTION" IS '说明：发布说明。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."PUBLISHED_BY" IS '发布人。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."PUBLISHED_AT" IS '发布时间。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."RELEASE_MODE" IS '发布模式。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."BASE_RELEASE_ID" IS '热修复基线发布ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."RISK_LEVEL" IS '风险级别。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."ROLLOUT_SCOPE" IS '发布策略范围。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."PATCH_DOCUMENT" IS '稳定ID语义补丁JSON文档。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."OVERRIDE_RISK" IS '是否经授权覆盖REVIEW风险。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE"."OVERRIDE_REASON" IS '风险覆盖原因。';
COMMENT ON TABLE "UI_CONFIG_RELEASE_AUDIT" IS 'UI发布审计表';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."ID" IS '主键ID：审计ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."CONFIG_TYPE" IS '配置类型。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."CONFIG_ID" IS '配置ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."RELEASE_ID" IS '关联发布ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."OPERATION" IS '操作。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."RISK_LEVEL" IS '风险级别。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."ACTOR_ID" IS '操作人ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."ACTOR_NAME" IS '操作人名称。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."REASON" IS '发布或覆盖原因。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."TRACE_ID" IS '业务追踪ID。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."DETAIL_DOCUMENT" IS '影响范围与差异JSON文档。';
COMMENT ON COLUMN "UI_CONFIG_RELEASE_AUDIT"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "UI_EVENT_BINDING" IS '统一UI事件绑定表';
COMMENT ON COLUMN "UI_EVENT_BINDING"."ID" IS '主键ID：事件绑定链ID。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."OWNER_TYPE" IS '所有者类型。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."OWNER_ID" IS '持有者ID：所属实体、表单或列表ID。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."TARGET_TYPE" IS '目标类型。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."TARGET_KEY" IS '目标标识：字段节点或按钮稳定编码，OWNER为空串。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."EVENT_CODE" IS '统一业务事件编码。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."INHERITANCE_MODE" IS '继承模式。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."STEPS_DOCUMENT" IS '步骤集合文档：BEFORE/REPLACE/AFTER有序步骤JSON数组。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."REVISION" IS '修订号：草稿修订号。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."ENABLED" IS '是否启用。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "UI_EVENT_BINDING"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "UI_EXTENSION_DEFINITION" IS '统一扩展定义目录表';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."ID" IS '主键ID：扩展定义ID。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."EXTENSION_TYPE" IS '扩展类型：本表取值为 `FORM`/`NODE`/`FIELD`/`LIST`/`INTERFACE`；管理层展示时把前四类投影为 `UI_*`，并聚合另表中的流程动作和人员解析器。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."EXTENSION_KEY" IS '扩展稳定编码：完整能力的唯一稳定编码。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."DISPLAY_NAME" IS '显示名称。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."VERSION" IS '版本号：扩展实现版本。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."SNAPSHOT_VERSION" IS '配置快照协议版本。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."VISIBILITY_SCOPE" IS '适用范围：GLOBAL/ENTITY。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."ENTITY_CODES_DOCUMENT" IS '指定适用实体编码JSON数组。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."SUPPORTED_MODES_DOCUMENT" IS '支持的运行模式JSON数组。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."SUPPORTED_NODE_TYPES_DOCUMENT" IS '支持的节点类型JSON数组。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."SUPPORTED_BINDINGS_DOCUMENT" IS '支持的绑定类型JSON数组。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."CONFIG_SCHEMA_DOCUMENT" IS '配置Schema JSON文档。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."CAPABILITIES_DOCUMENT" IS '扩展能力声明JSON文档。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."IMPLEMENTATION_TYPE" IS '接口实现类型：字典、静态数据、Provider、运行时上下文或结构化计算。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."PROVIDER_CODE" IS 'Provider编码：已注册 Provider 的稳定编码。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."SCOPE_TYPE" IS '接口作用域：`GLOBAL`/`ENTITY`/`FORM`/`LIST`。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."SCOPE_ID" IS '作用域资源ID：非全局接口对应的实体、表单或列表 ID。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."IMPLEMENTATION_CONFIG_DOCUMENT" IS '接口实现配置：受控实现配置 JSON。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."EXECUTION_POLICY_DOCUMENT" IS '接口执行策略：超时、缓存、失败回退等策略 JSON。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."INPUT_SCHEMA_DOCUMENT" IS '输入 Schema：接口输入 JSON Schema。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."OUTPUT_SCHEMA_DOCUMENT" IS '输出 Schema：接口输出 JSON Schema。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."INTERFACE_KIND" IS '接口读写类型：`READ`/`WRITE`。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."INTERFACE_CONTEXT_TYPE" IS '调用上下文：`FORM`/`LIST`/`ENTITY`。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."PROVIDER_OPERATION_CODE" IS 'Provider内部路由：仅供后端实现分派，不是设计器的第二层选项。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."LEGACY_SERVICE_ID" IS '历史服务ID：仅用于不可变历史快照兼容，新契约不输出。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."STATUS" IS '状态。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."REVISION" IS '修订号：定义修订号。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP。';
COMMENT ON COLUMN "UI_EXTENSION_DEFINITION"."DELETED" IS '逻辑删除标记：逻辑删除标志（0-未删除 1-已删除）。';
COMMENT ON TABLE "UI_HOTFIX_OBSERVATION_METRIC" IS 'UI热修复观察指标表';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."ID" IS '主键ID：指标ID。';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."REQUEST_ID" IS 'HOTFIX申请ID。';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."RELEASE_ID" IS 'HOTFIX发布ID。';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."METRIC_CODE" IS '指标编码。';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."TOTAL_COUNT" IS '观察总次数。';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."FAILURE_COUNT" IS '失败次数。';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."LAST_ERROR" IS '最近失败摘要。';
COMMENT ON COLUMN "UI_HOTFIX_OBSERVATION_METRIC"."LAST_OBSERVED_AT" IS '最近观察时间。';
COMMENT ON TABLE "UI_VIEW_COMPOSITION" IS '表单列表关联内容表';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."ID" IS '主键ID：表单或列表设计器中的“关联内容”草稿记录。 目标内容、关联方式、允许操作和特殊处理统一保存在经过严格校验的 config_document 中；独立记录使关联内容可以稳定排序、乐观并发更新， 并能作为宿主发布快照的一部分参与差异比较和撤销。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."OWNER_TYPE" IS '所有者类型；CHECK 枚举：\\''FORM\\'', \\''LIST\\''。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."OWNER_ID" IS '持有者ID：表单或列表配置ID。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."COMPOSITION_KEY" IS '宿主内稳定业务标识。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."ANCHOR_TYPE" IS '锚点类型。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."ANCHOR_KEY" IS '非OWNER挂载点的稳定标识。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."CONFIG_DOCUMENT" IS '经白名单校验的关联内容配置JSON。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."ORDER_KEY" IS '同一挂载点内的稳定排序键。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."REVISION" IS '修订号：乐观锁修订号。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."UPDATE_TIME" IS '更新时间。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."DELETED" IS '逻辑删除标记；CHECK 枚举：0, 1。';
COMMENT ON COLUMN "UI_VIEW_COMPOSITION"."ACTIVE_COMPOSITION_KEY" IS '仅活动记录参与宿主内编码唯一约束；数据库生成，表达式见本表实现说明。';
COMMENT ON TABLE "WORK_CALENDAR" IS '工作日历表';
COMMENT ON COLUMN "WORK_CALENDAR"."ID" IS '主键ID。';
COMMENT ON COLUMN "WORK_CALENDAR"."CALENDAR_CODE" IS '日历编码。';
COMMENT ON COLUMN "WORK_CALENDAR"."CALENDAR_NAME" IS '日历名称。';
COMMENT ON COLUMN "WORK_CALENDAR"."TIMEZONE_ID" IS '时区ID。';
COMMENT ON COLUMN "WORK_CALENDAR"."DESCRIPTION" IS '说明。';
COMMENT ON COLUMN "WORK_CALENDAR"."VERSION" IS '版本号。';
COMMENT ON COLUMN "WORK_CALENDAR"."DEFAULT_FLAG" IS '是否默认日历。';
COMMENT ON COLUMN "WORK_CALENDAR"."STATUS" IS '状态。';
COMMENT ON COLUMN "WORK_CALENDAR"."EFFECTIVE_FROM" IS '生效起始时间。';
COMMENT ON COLUMN "WORK_CALENDAR"."EFFECTIVE_TO" IS '生效结束时间。';
COMMENT ON COLUMN "WORK_CALENDAR"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "WORK_CALENDAR"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "WORK_CALENDAR"."UPDATED_BY" IS '修改人。';
COMMENT ON COLUMN "WORK_CALENDAR"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "WORK_CALENDAR"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "WORK_CALENDAR_BINDING" IS '工作日历作用域绑定表';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."ID" IS '主键ID。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."SCOPE_TYPE" IS '范围类型。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."SCOPE_KEY" IS '范围键。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."CALENDAR_ID" IS '日历ID。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."PRIORITY" IS '优先级。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."EFFECTIVE_FROM" IS '生效起始时间。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."EFFECTIVE_TO" IS '生效结束时间。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."STATUS" IS '状态。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."CREATED_BY" IS '创建人。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."UPDATED_BY" IS '修改人。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "WORK_CALENDAR_BINDING"."DELETED" IS '逻辑删除标记。';
COMMENT ON TABLE "WORK_CALENDAR_EXCEPTION" IS '日历特殊日期表';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION"."ID" IS '主键ID。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION"."CALENDAR_ID" IS '日历ID。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION"."EXCEPTION_DATE" IS '例外日期。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION"."EXCEPTION_TYPE" IS '例外类型。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION"."EXCEPTION_NAME" IS '例外名称。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION"."DESCRIPTION" IS '说明。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "WORK_CALENDAR_EXCEPTION_PERIOD" IS '特殊日期工作时段表';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION_PERIOD"."ID" IS '主键ID。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION_PERIOD"."EXCEPTION_ID" IS '例外ID。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION_PERIOD"."START_MINUTE" IS '起始分钟。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION_PERIOD"."END_MINUTE" IS '结束分钟。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION_PERIOD"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "WORK_CALENDAR_EXCEPTION_PERIOD"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "WORK_CALENDAR_PERIOD" IS '每周工作时段表';
COMMENT ON COLUMN "WORK_CALENDAR_PERIOD"."ID" IS '主键ID。';
COMMENT ON COLUMN "WORK_CALENDAR_PERIOD"."CALENDAR_ID" IS '日历ID。';
COMMENT ON COLUMN "WORK_CALENDAR_PERIOD"."DAY_OF_WEEK" IS '星期。';
COMMENT ON COLUMN "WORK_CALENDAR_PERIOD"."START_MINUTE" IS '起始分钟。';
COMMENT ON COLUMN "WORK_CALENDAR_PERIOD"."END_MINUTE" IS '结束分钟。';
COMMENT ON COLUMN "WORK_CALENDAR_PERIOD"."SORT_ORDER" IS '排序号。';
COMMENT ON COLUMN "WORK_CALENDAR_PERIOD"."CREATE_TIME" IS '创建时间。';
COMMENT ON TABLE "WORKFLOW_BOOTSTRAP_JOB" IS '启动初始化任务表';
COMMENT ON COLUMN "WORKFLOW_BOOTSTRAP_JOB"."JOB_NAME" IS '任务名称。';
COMMENT ON COLUMN "WORKFLOW_BOOTSTRAP_JOB"."COMPLETED_VERSION" IS '完成版本。';
COMMENT ON COLUMN "WORKFLOW_BOOTSTRAP_JOB"."OWNER_ID" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "WORKFLOW_BOOTSTRAP_JOB"."COMPLETED_AT" IS '完成时间。';
COMMENT ON COLUMN "WORKFLOW_BOOTSTRAP_JOB"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "WORKFLOW_BOOTSTRAP_JOB"."UPDATE_TIME" IS '更新时间。';
COMMENT ON TABLE "WORKFLOW_OUTBOX_EVENT" IS '事务发件箱事件表';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."ID" IS '主键ID：通用数据库 Outbox 持久化记录。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."TOPIC" IS '主题。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."EVENT_KEY" IS '事件键。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."AGGREGATE_TYPE" IS '聚合根类型。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."AGGREGATE_ID" IS '聚合根ID。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."PAYLOAD_DOCUMENT" IS '载荷文档。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."STATUS" IS '状态。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."OWNER_ID" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."LEASE_TOKEN" IS '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."LEASE_UNTIL" IS '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."RETRY_COUNT" IS '重试数量。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."MAX_RETRIES" IS '最大重试次数。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."NEXT_RETRY_TIME" IS '下一次重试时间。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."ERROR_MESSAGE" IS '错误消息。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."UPDATE_TIME" IS '更新时间。';
COMMENT ON COLUMN "WORKFLOW_OUTBOX_EVENT"."PROCESSED_TIME" IS '处理时间。';
COMMENT ON TABLE "WORKFLOW_SCHEMA_CHANGE" IS '数据库结构执行队列表';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."ID" IS '主键ID。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."DDL_HASH" IS 'DDL哈希。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."ACTIVE_HASH" IS '活跃结构请求哈希。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."DDL_STATEMENT" IS 'DDL语句。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."STATUS" IS '状态。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."ATTEMPT" IS '尝试次数。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."OWNER_ID" IS '持有者ID：当前记录对应的持有者或执行者标识，具体职责见本表业务说明。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."LEASE_TOKEN" IS '租约代次：领取租约的代次，用于识别过期执行者；不是客户端登录令牌。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."LEASE_UNTIL" IS '租约截止时间：当前执行租约到期时间，过期后可按领取规则重新调度。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."NEXT_ATTEMPT_AT" IS '下一次尝试次数时间。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."LAST_ERROR" IS '最近错误。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."CREATE_TIME" IS '创建时间。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."UPDATE_TIME" IS '更新时间；更新时自动设置为 CURRENT_TIMESTAMP(6)。';
COMMENT ON COLUMN "WORKFLOW_SCHEMA_CHANGE"."COMPLETED_TIME" IS '完成时间。';

-- 系统元数据、菜单权限与禁用的超级管理员。
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('app.schema.version','7.2.0.2',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('cfg.execution-related-entities-count','true',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('cfg.task-related-entities-count','true',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('cmmn.schema.version','7.2.0.2',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('common.schema.version','7.2.0.2',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('dmn.schema.version','7.2.0.2',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('eventregistry.schema.version','7.2.0.2',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('next.dbid','1',1);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('schema.history','upgrade(6.8.0.0->7.2.0.2)',2);
INSERT INTO "ACT_GE_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('schema.version','7.2.0.2',2);
INSERT INTO "ACT_ID_PROPERTY" ("NAME_", "VALUE_", "REV_") VALUES ('schema.version','7.2.0.2',1);
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (315408474423554179,'sys_dict','字典类型','平台系统表目录：sys_dict',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (319550136508222867,'sys_menu','菜单权限','平台系统表目录：sys_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (429188770482109985,'sys_user','系统用户','平台系统表目录：sys_user',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (540477245371031459,'sys_role_menu','角色菜单关系','平台系统表目录：sys_role_menu',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role_menu','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (543966161995942233,'sys_user_role','用户角色关系','平台系统表目录：sys_user_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (687980787977785014,'sys_dict_item','字典明细','平台系统表目录：sys_dict_item',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_dict_item','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (695805941702569049,'sys_role','系统角色','平台系统表目录：sys_role',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_role','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (704307435534855320,'sys_user_group','用户组成员关系','平台系统表目录：sys_user_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_user_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (869084506871349004,'sys_organization','组织部门','平台系统表目录：sys_organization',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_organization','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_DEFINITION" ("ID", "ENTITY_CODE", "ENTITY_NAME", "DESCRIPTION", "PROCESS_DEFINITION_ID", "STATUS", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "TABLE_NAME", "LIFECYCLE_MODE", "STORAGE_MODE", "DELETED", "UPDATED_BY", "TEAM_VISIBILITY_ENABLED", "TEAM_VISIBILITY_LEVEL") VALUES (924525185085388686,'sys_group','用户组','平台系统表目录：sys_group',NULL,'PUBLISHED','system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'sys_group','STANDALONE','SYSTEM',0,NULL,0,'ADDITIVE');
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (302983935847141633,315408474423554179,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (463024980901796770,315408474423554179,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (349900140693140919,315408474423554179,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (668047102303117425,315408474423554179,'dict_code','字典编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (683413866831122108,315408474423554179,'dict_name','字典名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (660514776571799460,315408474423554179,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1041515214726648952,315408474423554179,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (835260617712650971,315408474423554179,'status','状态：0-启用 1-禁用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (392848417810309094,315408474423554179,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (343176658012303195,319550136508222867,'breadcrumb','是否显示面包屑：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'breadcrumb',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (558990094873955059,319550136508222867,'component','组件路径','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'component',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (175753738162967087,319550136508222867,'create_by','创建者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (950655553882382674,319550136508222867,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (205676883257074073,319550136508222867,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (138193370562438585,319550136508222867,'entity_code','关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,23,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'entity_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (407584881042156273,319550136508222867,'icon','菜单图标','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'icon',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (58574189954682540,319550136508222867,'id','菜单ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1138396868793173976,319550136508222867,'is_cache','是否缓存：0-缓存 1-不缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,21,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_cache',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (776238513957782207,319550136508222867,'is_frame','是否外链：0-否 1-是','STRING','char(1)',1,0,0,NULL,NULL,NULL,20,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'is_frame',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (111764993240200811,319550136508222867,'keep_alive','是否缓存：0-不缓存 1-缓存','STRING','char(1)',1,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'keep_alive',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (119578562633124551,319550136508222867,'list_key','实体列表稳定编码','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,25,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'list_key',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1002235088452601204,319550136508222867,'menu_name','菜单名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (198475160699508098,319550136508222867,'menu_type','菜单类型：M-目录 C-菜单 F-按钮','STRING','char(1)',1,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (573255979784869818,319550136508222867,'parent_id','父菜单ID，0为顶级菜单','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (426434390452136951,319550136508222867,'path','路由地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (908207397228493680,319550136508222867,'perm','权限标识，如：system:user:list','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'perm',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (544552761361520586,319550136508222867,'query','路由参数','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,22,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'query',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (158131531689092062,319550136508222867,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (509277996761328404,319550136508222867,'resource_type','菜单资源类型，ENTITY_LIST 表示动态实体列表','STRING','varchar(30)',30,0,0,NULL,NULL,NULL,24,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'resource_type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (323682773251959664,319550136508222867,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (628997735940863871,319550136508222867,'status','状态：0-禁用 1-启用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (658300988401276707,319550136508222867,'update_by','更新者','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_by',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (531863267937602399,319550136508222867,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,19,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (555037528202687605,319550136508222867,'visible','显示状态：0-隐藏 1-显示','STRING','char(1)',1,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'visible',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (852378610239470992,429188770482109985,'avatar','头像','STRING','varchar(255)',255,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'avatar',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (23541322679314285,429188770482109985,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (361679855788775973,429188770482109985,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (10101577987500861,429188770482109985,'dept_id','部门ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dept_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (326932484964000364,429188770482109985,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1134826313312664922,429188770482109985,'id','用户ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (745051415557763972,429188770482109985,'nickname','昵称','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'nickname',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1148301704392575944,429188770482109985,'org_id','组织ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (317101019809934504,429188770482109985,'password','密码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'password',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1039751570117789095,429188770482109985,'password_reset_required','password_reset_required','BOOLEAN','tinyint',NULL,1,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'password_reset_required',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (475192751198870445,429188770482109985,'phone','手机号','STRING','varchar(20)',20,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (59576534536847010,429188770482109985,'status','状态（0启用 1禁用）','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (42152131250552875,429188770482109985,'token_version','Incremented to revoke all previously issued sessions','LONG','bigint',NULL,1,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'token_version',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (736587934404834291,429188770482109985,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (184566779876080086,429188770482109985,'username','用户名','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'username',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (761157259593380615,540477245371031459,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (480775900607645235,540477245371031459,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (561900151098621684,540477245371031459,'menu_id','菜单ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'menu_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (919996913633728290,540477245371031459,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (979647368025597245,543966161995942233,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (728367720794403925,543966161995942233,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (75963321277264675,543966161995942233,'role_id','角色ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1082238976243929409,543966161995942233,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (290109203980610604,687980787977785014,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (902908459287977551,687980787977785014,'deleted','逻辑删除','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1112820714573490757,687980787977785014,'dict_code','冗余：字典编码（便于直接查询）','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (845110899938467101,687980787977785014,'dict_id','所属字典ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'dict_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (85937474892350588,687980787977785014,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (978970058829541345,687980787977785014,'item_code','项编码','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (458794463769860611,687980787977785014,'item_label','项标签/显示文本','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_label',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (293542286014999959,687980787977785014,'item_value','项值','STRING','varchar(200)',200,1,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'item_value',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (160051157971634527,687980787977785014,'parent_id','父项ID，0表示顶级','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1042513892081176326,687980787977785014,'remark','备注','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'remark',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (290497113002851889,687980787977785014,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (23540013034190091,687980787977785014,'status','状态：0-启用 1-禁用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (541437194327279494,687980787977785014,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (125004887893384772,695805941702569049,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (50302947400312544,695805941702569049,'deleted','删除标志（0正常 1删除）','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (464945732470729299,695805941702569049,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (39605060466058778,695805941702569049,'id','角色ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (582801574103340315,695805941702569049,'role_code','角色编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (846037797095146960,695805941702569049,'role_name','角色名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'role_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (469870220014538885,695805941702569049,'sort','显示排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (511818241961063640,695805941702569049,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (249397106466806541,695805941702569049,'status','状态（0启用 1禁用）','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (990484312511420209,695805941702569049,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (106998218957706418,704307435534855320,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (957745245096590288,704307435534855320,'group_id','组ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (460851437342127427,704307435534855320,'id','ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1137657801046065632,704307435534855320,'user_id','用户ID','STRING','varchar(64)',64,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'user_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (933634782907475287,869084506871349004,'address','地址','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,13,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'address',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (730434704510267566,869084506871349004,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,16,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (79125910432577094,869084506871349004,'deleted','是否删除：0-未删除 1-已删除','INTEGER','int',NULL,0,0,NULL,NULL,NULL,18,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (78753772494339085,869084506871349004,'description','描述','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,15,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (378782243158581413,869084506871349004,'email','邮箱','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,12,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'email',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (971589645354457971,869084506871349004,'id','主键ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (978131954777805791,869084506871349004,'leader_id','负责人ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (507492914926553510,869084506871349004,'leader_name','负责人名称（冗余）','STRING','varchar(100)',100,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'leader_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (538220663657061167,869084506871349004,'level','层级（0为顶级）','INTEGER','int',NULL,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'level',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (49047951767170137,869084506871349004,'org_code','组织编码（唯一）','STRING','varchar(100)',100,1,1,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (103503443431748381,869084506871349004,'org_name','组织名称','STRING','varchar(100)',100,1,0,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'org_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (756767862978652736,869084506871349004,'parent_id','父级ID（顶级为0）','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (689704407809393946,869084506871349004,'path','完整路径，如：/0/1/5/10/','STRING','varchar(500)',500,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'path',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (34676674937238359,869084506871349004,'phone','联系电话','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'phone',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (799314663885053329,869084506871349004,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1046028949155177888,869084506871349004,'status','状态：0-启用，1-禁用','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,14,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (46123295334207598,869084506871349004,'type','类型：org-组织，dept-部门','STRING','varchar(20)',20,1,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'type',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (923457855008240877,869084506871349004,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,17,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (221052828124990695,924525185085388686,'create_time','创建时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,7,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'create_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (276971412500557125,924525185085388686,'deleted','删除标志','BOOLEAN','tinyint',NULL,0,0,NULL,NULL,NULL,9,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'deleted',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (147276096293704262,924525185085388686,'description','描述','STRING','varchar(200)',200,0,0,NULL,NULL,NULL,4,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'description',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (954151322232061889,924525185085388686,'group_code','组编码','STRING','varchar(50)',50,1,1,NULL,NULL,NULL,3,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_code',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (324844729973447637,924525185085388686,'group_name','组名称','STRING','varchar(50)',50,1,0,NULL,NULL,NULL,2,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'group_name',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (1039028238930600397,924525185085388686,'id','组ID','STRING','varchar(64)',64,1,1,NULL,NULL,NULL,1,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (22985037036471449,924525185085388686,'parent_id','父组ID','STRING','varchar(64)',64,0,0,NULL,NULL,NULL,10,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'parent_id',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (332274980659204946,924525185085388686,'sort','排序','INTEGER','int',NULL,0,0,NULL,NULL,NULL,5,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (836913778987896460,924525185085388686,'sort_order','排序号','INTEGER','int',NULL,0,0,NULL,NULL,NULL,11,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'sort_order',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (370552990036906948,924525185085388686,'status','状态（0启用 1禁用）','STRING','varchar(50)',50,0,0,NULL,NULL,NULL,6,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'status',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "ENTITY_FIELD" ("ID", "ENTITY_ID", "FIELD_CODE", "FIELD_NAME", "FIELD_TYPE", "DB_TYPE", "FIELD_LENGTH", "IS_REQUIRED", "IS_UNIQUE", "DEFAULT_VALUE", "OPTIONS_JSON", "VALIDATE_RULES", "SORT_ORDER", "IS_SYSTEM", "IS_PUBLISHED", "EDITABLE", "CREATE_TIME", "UPDATE_TIME", "FIELD_PRECISION", "DB_COLUMN_NAME", "FILE_TYPES", "FILE_MAX_SIZE", "FILE_MAX_COUNT", "REF_ENTITY_TYPE", "REF_ENTITY_ID", "REF_FIELD_CODE", "REF_LIST_KEY", "FIELD_ID", "DICT_TYPE", "VALUE_STORAGE", "DELETED") VALUES (335370829110629664,924525185085388686,'update_time','更新时间','DATETIME','datetime',NULL,0,0,NULL,NULL,NULL,8,1,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL,'update_time',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'SCALAR',0);
INSERT INTO "PROCESS_ACTION_DEFINITION" ("ID", "ACTION_CODE", "DISPLAY_NAME", "DESCRIPTION", "HANDLER_NAME", "VISIBILITY_SCOPE", "ENABLED", "CREATED_BY", "CREATE_TIME", "UPDATE_TIME", "DELETED") VALUES ('flow_action_definition_notify','sendNotificationHandler','发送流程通知','发送待办、完成、撤回等流程通知；推荐使用提交后执行。','sendNotificationHandler','GLOBAL',1,'system',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "PROCESS_PERSON_RESOLVER_DEFINITION" ("ID", "RESOLVER_CODE", "DISPLAY_NAME", "DESCRIPTION", "BEAN_NAME", "IMPLEMENTATION_VERSION", "CONTRACT_VERSION", "SUPPORTED_USAGES_DOCUMENT", "EXTRA_PARAM_SCHEMA_DOCUMENT", "DYNAMIC_EXTRA_PARAMS", "ENABLED", "REVISION", "CREATE_TIME", "UPDATE_TIME", "DELETED") VALUES ('person_resolver_entity_user_reference_001','entityUserReferenceField','实体用户关系字段','从流程绑定实体的已发布用户单选或多选关系字段读取人员','entityUserReferenceFieldPersonResolver',1,1,'[\"ASSIGNEE\",\"CANDIDATE\",\"MULTI_INSTANCE\"]','{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"schemaVersion\",\"entityCode\",\"fieldCode\"],\"properties\":{\"schemaVersion\":{\"const\":1},\"entityCode\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":128},\"fieldCode\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":100,\"pattern\":\"^[A-Za-z][A-Za-z0-9_]{0,99}$\"}}}',0,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "PROCESS_PERSON_RESOLVER_DEFINITION" ("ID", "RESOLVER_CODE", "DISPLAY_NAME", "DESCRIPTION", "BEAN_NAME", "IMPLEMENTATION_VERSION", "CONTRACT_VERSION", "SUPPORTED_USAGES_DOCUMENT", "EXTRA_PARAM_SCHEMA_DOCUMENT", "DYNAMIC_EXTRA_PARAMS", "ENABLED", "REVISION", "CREATE_TIME", "UPDATE_TIME", "DELETED") VALUES ('person_resolver_relative_position_001','relativeOrgPosition','相对组织职务','按流程发起人冻结组织链查询节点激活时的有效职务任职人','relativeOrgPositionPersonResolver',1,1,'[\"CANDIDATE\",\"ASSIGNEE\",\"MULTI_INSTANCE\"]','{\"multipleMatchPolicies\":[\"ERROR\",\"PRIMARY_OR_ERROR\",\"ALL\"],\"subject\":[\"PROCESS_INITIATOR\"],\"schemaVersion\":1,\"lookupModes\":[\"SELF\",\"FIXED_ANCESTOR\",\"NEAREST_WITH_HOLDER\",\"BUSINESS_LEVEL\"],\"anchor\":[\"DEPARTMENT\",\"ORGANIZATION\"],\"type\":\"object\"}',0,1,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "SYS_DICT" ("ID", "DICT_CODE", "DICT_NAME", "DESCRIPTION", "STATUS", "SORT", "DELETED", "CREATE_TIME", "UPDATE_TIME") VALUES ('dict_org_business_level_001','organization_business_level','组织业务层级','独立于物理树深度的稳定组织业务层级','0',10,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_DICT_ITEM" ("ID", "DICT_ID", "DICT_CODE", "PARENT_ID", "ITEM_CODE", "ITEM_LABEL", "ITEM_VALUE", "SORT", "STATUS", "REMARK", "DELETED", "CREATE_TIME", "UPDATE_TIME") VALUES ('dict_org_level_center_001','dict_org_business_level_001','organization_business_level','0','CENTER','中心','CENTER',30,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_DICT_ITEM" ("ID", "DICT_ID", "DICT_CODE", "PARENT_ID", "ITEM_CODE", "ITEM_LABEL", "ITEM_VALUE", "SORT", "STATUS", "REMARK", "DELETED", "CREATE_TIME", "UPDATE_TIME") VALUES ('dict_org_level_company_001','dict_org_business_level_001','organization_business_level','0','COMPANY','公司','COMPANY',20,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_DICT_ITEM" ("ID", "DICT_ID", "DICT_CODE", "PARENT_ID", "ITEM_CODE", "ITEM_LABEL", "ITEM_VALUE", "SORT", "STATUS", "REMARK", "DELETED", "CREATE_TIME", "UPDATE_TIME") VALUES ('dict_org_level_dept1_001','dict_org_business_level_001','organization_business_level','0','FIRST_LEVEL_DEPT','一级部门','FIRST_LEVEL_DEPT',40,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_DICT_ITEM" ("ID", "DICT_ID", "DICT_CODE", "PARENT_ID", "ITEM_CODE", "ITEM_LABEL", "ITEM_VALUE", "SORT", "STATUS", "REMARK", "DELETED", "CREATE_TIME", "UPDATE_TIME") VALUES ('dict_org_level_dept2_001','dict_org_business_level_001','organization_business_level','0','SECOND_LEVEL_DEPT','二级部门','SECOND_LEVEL_DEPT',50,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_DICT_ITEM" ("ID", "DICT_ID", "DICT_CODE", "PARENT_ID", "ITEM_CODE", "ITEM_LABEL", "ITEM_VALUE", "SORT", "STATUS", "REMARK", "DELETED", "CREATE_TIME", "UPDATE_TIME") VALUES ('dict_org_level_group_001','dict_org_business_level_001','organization_business_level','0','GROUP','集团','GROUP',10,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_DICT_ITEM" ("ID", "DICT_ID", "DICT_CODE", "PARENT_ID", "ITEM_CODE", "ITEM_LABEL", "ITEM_VALUE", "SORT", "STATUS", "REMARK", "DELETED", "CREATE_TIME", "UPDATE_TIME") VALUES ('dict_org_level_team_001','dict_org_business_level_001','organization_business_level','0','TEAM','团队','TEAM',60,'0',NULL,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_GLOBAL_SETTING" ("ID", "SCOPE_TYPE", "OWNER_ID", "SETTING_KEY", "NAME", "SETTING_VALUE_TYPE", "SETTING_VALUE", "REMARK", "VERSION", "CREATED_BY", "UPDATED_BY", "CREATE_TIME", "UPDATE_TIME") VALUES ('99777a7f-819f-4830-885e-eb36e7607b19','SYSTEM','0','ui.user_preferences','用户界面偏好','JSON','{\"fieldTypesCollapsed\":false,\"sidebarCollapsed\":false,\"tabsEnabled\":false}','字段类型面板收起、左侧主菜单收起和顶部多标签页统一存储；按字段优先使用个人配置，缺失字段继承系统默认值。',0,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('300','0','配置管理','M','Box',3,'/config',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('400','0','系统管理','M','Setting',4,'/system',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('403','300','流程用户组','C','FolderOpened',3,'/config/process-user-groups',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('assignee_incident_handle_001','assignee_incident_menu_001','处置空办理人事件','F',NULL,1,'','','process:assignee-incident:handle','0','0','0','1','补充办理人、重试解析器、转兜底组或终止实例',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('assignee_incident_menu_001','0','空办理人事件','C','Warning',78,'/system/assignee-incidents','system/AssigneeIncidentManagement','process:assignee-incident:list','0','0','0','1','查看空办理人告警、重试和人工恢复记录',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_analyze_001','config_migration_menu_001','分析配置','F',NULL,4,'','','config-migration:analyze','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_download_001','config_migration_menu_001','下载发布包','F',NULL,2,'','','config-migration:download','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_export_001','config_migration_menu_001','导出配置','F',NULL,1,'','','config-migration:export','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_import_001','config_migration_menu_001','导入配置','F',NULL,3,'','','config-migration:import','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_list_001','config_migration_menu_001','查看配置迁移','F',NULL,0,'','','config-migration:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_menu_001','400','配置迁移','C','FolderOpened',90,'/system/config-migration','system/ConfigMigration','config-migration:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_publish_001','config_migration_menu_001','发布配置','F',NULL,5,'','','config-migration:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('config_migration_rollback_001','config_migration_menu_001','回滚配置','F',NULL,6,'','','config-migration:rollback','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('custom_form_guide','flow_setting_menu_001','自定义表单组件','C','Document',4,'/dev/manual/custom-form','/views/system/CustomFormGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('custom_list_guide','flow_setting_menu_001','自定义列表组件','C','Document',3,'/dev/manual/custom-list','/views/system/CustomListGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('dev_guide_dir','0','定制开发','M','Document',5,'/dev',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('dev_guide_list','flow_setting_menu_001','列表字段扩展','C','Document',1,'/dev/manual/list-field-extension','/views/system/DevGuide.vue','system:dev:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('embed_management_menu_001','0','嵌入集成','C','Monitor',76,'/system/embed-management','system/EmbedManagement','system:embed:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('embed_perm_identity_manage','embed_management_menu_001','维护嵌入身份映射','F',NULL,4,NULL,NULL,'system:embed:identity-manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('embed_perm_manage','embed_management_menu_001','维护嵌入视图与授权','F',NULL,2,NULL,NULL,'system:embed:manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('embed_perm_publish','embed_management_menu_001','发布嵌入视图','F',NULL,3,NULL,NULL,'system:embed:publish','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('embed_perm_session_revoke','embed_management_menu_001','撤销嵌入会话','F',NULL,5,NULL,NULL,'system:embed:session-revoke','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('embed_perm_view','embed_management_menu_001','查看嵌入运行时','F',NULL,1,NULL,NULL,'system:embed:view','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_scope_explicit_all_permission_001','0','允许全量数据放行','F',NULL,0,'','','entity:list-scope:explicit-all','0','1','0','1','允许在列表数据范围配置中显式确认全量可见的高风险权限',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_ui_hotfix_observe_permission','0','UI热修复观察指标','F',NULL,94,NULL,NULL,'entity:ui-config:hotfix:observe','0','1','0','1','HOTFIX运行观察指标上报',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_ui_hotfix_override_permission','300','UI配置热修复风险覆盖','F',NULL,91,NULL,NULL,'entity:ui-config:hotfix:override','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_ui_hotfix_publish_permission','300','UI配置兼容热修复','F',NULL,90,NULL,NULL,'entity:ui-config:hotfix','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_ui_hotfix_rollback_permission','0','UI热修复受控回滚','F',NULL,93,NULL,NULL,'entity:ui-config:hotfix:rollback','0','1','0','1','HOTFIX专用回滚权限',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_version_config_list_001','entity_version_management_001','查看数据版本配置','F',NULL,1,'','','entity:version:config:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_version_config_publish_001','entity_version_management_001','发布数据版本配置','F',NULL,3,'','','entity:version:config:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_version_config_update_001','entity_version_management_001','维护数据版本配置','F',NULL,2,'','','entity:version:config:update','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_version_management_001','0','数据版本','C','Clock',74,'/system/entity-versions','system/EntityVersionManagement','entity:version:config:list','0','0','0','1','实体数据版本策略、发布与比较',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_version_record_capture_001','entity_version_management_001','手工固化记录版本','F',NULL,5,'','','entity:version:record:capture','0','0','0','1','手工生成记录检查点',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('entity_version_record_view_001','entity_version_management_001','查看记录版本','F',NULL,4,'','','entity:version:record:view','0','0','0','1','查看有数据权限的记录历史版本',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('extension_list_permission_001','extension_management_menu_001','查看扩展','F',NULL,1,'','','system:extension:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('extension_management_menu_001','dev_guide_dir','扩展管理','C','Setting',2,'/dev/extensions','system/ExtensionManagement',NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('extension_test_permission_001','extension_management_menu_001','测试扩展','F',NULL,3,'','','system:extension:test','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('extension_update_permission_001','extension_management_menu_001','维护扩展','F',NULL,2,'','','system:extension:update','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('external_system_manage_permission_001','external_system_menu_001','维护外部系统','F',NULL,2,'','','system:external-system:manage','0','0','0','1','新增、编辑、启停和删除外部系统及其参数',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('external_system_menu_001','400','外部系统','C','Connection',14,'/system/external-systems','system/ExternalSystem',NULL,'0','0','0','1','维护外部系统基础资料和对接参数；具体接口由业务扩展实现',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('external_system_view_permission_001','external_system_menu_001','查看外部系统','F',NULL,1,'','','system:external-system:view','0','0','0','1','查看外部系统基本信息列表',0,'migration-v079',CURRENT_TIMESTAMP,'migration-v079',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('flow_action_guide_menu_001','flow_setting_menu_001','流程动作','C','Notebook',5,'/dev/manual/flow-actions','system/FlowActionGuide','system:flowAction:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('flow_setting_menu_001','dev_guide_dir','开发手册','M','Notebook',1,'/dev/manual',NULL,NULL,'0','0','0','1','定制开发相关配置与扩展手册',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('global_settings_manage','global_settings_menu','维护全局设置','F',NULL,2,'','','system:setting:manage','0','0','0','1','修改或恢复系统默认值',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('global_settings_menu','400','全局设置','C','Setting',15,'/system/settings','system/GlobalSettings',NULL,'0','0','0','1','设置系统默认值，用户个人偏好可单独覆盖',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('global_settings_view','global_settings_menu','查看全局设置','F',NULL,1,'','','system:setting:view','0','0','0','1','查看系统设置及逻辑说明',0,'migration-v090',CURRENT_TIMESTAMP,'migration-v090',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('integration_management_menu_001','0','开放集成','C','Connection',75,'/system/open-integration','system/OpenIntegration','system:integration:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('integration_perm_manage','integration_management_menu_001','维护接入应用','F',NULL,2,NULL,NULL,'system:integration:manage','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('integration_perm_secret_rotate','integration_management_menu_001','轮换应用凭据','F',NULL,3,NULL,NULL,'system:integration:secret-rotate','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('integration_perm_view','integration_management_menu_001','查看开放集成','F',NULL,1,NULL,NULL,'system:integration:view','0','1','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('list_column_template_manage_001','list_column_template_menu_001','维护列表列模板','F',NULL,1,'','','system:list-column-template:manage','0','0','0','1','新增、编辑和复制列表列模板',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('list_column_template_menu_001','300','列表列模板','C','Grid',4,'/config/list-column-templates','system/ListColumnTemplateManagement','system:list-column-template:view','0','0','0','1','可视化维护用于一次性初始化的列表列模板',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('list_field_guide_v2_001','flow_setting_menu_001','列表字段扩展2','C','Notebook',2,'/dev/manual/list-field-extension-v2','system/ListFieldExtensionGuide','system:dev:list','0','0','0','1','列表字段扩展开发手册：单元格组件、虚拟列与 ListFieldDataProvider',0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('position_assign_permission_001','position_management_menu_001','维护组织任职','F',NULL,2,'','','system:position:assign','0','0','0','1','任命、转任、撤销和调整任职有效期',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('position_manage_permission_001','position_management_menu_001','维护职务定义','F',NULL,1,'','','system:position:manage','0','0','0','1','新增、编辑、启停职务定义',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('position_management_menu_001','400','职务管理','C','Briefcase',3,'/system/position','system/Position','system:position:view','0','0','0','1','全局职务定义与组织任职管理',0,'migration-v070',CURRENT_TIMESTAMP,'migration-v070',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_dict_manage','0','Manage dictionaries','F',NULL,0,NULL,NULL,'system:dictionary:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_dict_view','0','View dictionaries','F',NULL,0,NULL,NULL,'system:dictionary:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_entity_manage','0','Manage entity definitions','F',NULL,0,NULL,NULL,'entity:definition:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_entity_publish','0','Publish entity definitions','F',NULL,0,NULL,NULL,'entity:definition:publish','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_entity_view','0','View entity definitions','F',NULL,0,NULL,NULL,'entity:definition:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_file_delete','0','Delete files','F',NULL,0,NULL,NULL,'storage:file:delete','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_file_read','0','Read files','F',NULL,0,NULL,NULL,'storage:file:read','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_file_write','0','Upload files','F',NULL,0,NULL,NULL,'storage:file:write','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_menu_manage','0','Manage menus','F',NULL,0,NULL,NULL,'system:menu:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_menu_view','0','View menus','F',NULL,0,NULL,NULL,'system:menu:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_org_manage','0','Manage organizations','F',NULL,0,NULL,NULL,'system:organization:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_org_view','0','View organizations','F',NULL,0,NULL,NULL,'system:organization:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_process_manage','0','Manage process definitions','F',NULL,0,NULL,NULL,'process:definition:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_process_publish','0','Publish process definitions','F',NULL,0,NULL,NULL,'process:definition:publish','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_process_signal','0','Trigger process receive tasks','F',NULL,0,NULL,NULL,'process:instance:signal','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_process_view','0','View process definitions','F',NULL,0,NULL,NULL,'process:definition:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_role_manage','0','Manage roles','F',NULL,0,NULL,NULL,'system:role:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_role_view','0','View roles','F',NULL,0,NULL,NULL,'system:role:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_user_manage','0','Manage users','F',NULL,0,NULL,NULL,'system:user:manage','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_user_reset','0','Reset user password','F',NULL,0,NULL,NULL,'system:user:reset-password','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('security_perm_user_view','0','View users','F',NULL,0,NULL,NULL,'system:user:view','0','1','0','1',NULL,1,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('sla_management_dir_001','400','SLA管理','M','Timer',8,'/system/sla',NULL,NULL,'0','0','0','1','SLA策略与运行监控',0,'migration-v075',CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('system_audit_detail_perm_001','system_audit_menu_001','系统日志详情','F','',990,'','','system:audit:detail','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('system_audit_export_perm_001','system_audit_menu_001','系统日志导出','F','',990,'','','system:audit:export','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('system_audit_list_perm_001','system_audit_menu_001','系统日志查询','F','',990,'','','system:audit:list','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('system_audit_menu_001','400','系统日志','C','Document',80,'/system/audit-logs','system/SystemAudit',NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('task_sla_monitor_menu_001','sla_management_dir_001','SLA监控','C','DataAnalysis',2,'/system/sla/monitor','process/TaskSlaMonitor','process:sla:monitor','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('task_sla_policy_manage_perm_001','task_sla_policy_menu_001','维护SLA策略','F',NULL,1,'','','process:sla-policy:manage','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('task_sla_policy_menu_001','sla_management_dir_001','SLA策略','C','Timer',1,'/system/sla/policies','process/TaskSlaPolicyManagement','process:sla-policy:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v076',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('task_sla_policy_publish_perm_001','task_sla_policy_menu_001','发布SLA策略','F',NULL,2,'','','process:sla-policy:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('user_manual_dir_001','0','用户手册','M','Notebook',6,'/manual',NULL,NULL,'0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('user_manual_embed_integration_001','user_manual_dir_001','嵌入集成','C','Monitor',4,'/manual/embed-integration','manual/EmbedIntegrationManual','user-manual:embed-integration:view','0','0','0','1','Embed View、应用授权、外部用户映射、Launch 与宿主 SDK 使用手册',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('user_manual_entity_001','user_manual_dir_001','实体配置','C','Document',1,'/manual/entity','manual/EntityManual','user-manual:entity:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('user_manual_open_integration_001','user_manual_dir_001','集成应用与 Embed','C','Link',3,'/manual/open-integration','manual/OpenIntegrationManual','user-manual:open-integration:view','0','0','0','1','集成应用、Client Credential、OAuth 与 Embed 接入说明',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('user_manual_process_001','user_manual_dir_001','流程管理','C','Connection',2,'/manual/process','manual/ProcessManual','user-manual:process:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('user_manual_quick_start_001','user_manual_dir_001','快速开始','C','Guide',0,'/manual/quick-start','manual/QuickStartManual','user-manual:quick-start:view','0','0','0','1','从配置流程、实体表单和列表到绑定流程、配置菜单的入门步骤',0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0',NULL,NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('work_calendar_manage_perm_001','work_calendar_menu_001','维护工作日历','F',NULL,1,'','','system:work-calendar:manage','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('work_calendar_menu_001','400','工作日历','C','Calendar',7,'/system/work-calendars','system/WorkCalendarManagement','system:work-calendar:view','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,'migration-v075',CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_MENU" ("ID", "PARENT_ID", "MENU_NAME", "MENU_TYPE", "ICON", "SORT", "PATH", "COMPONENT", "PERM", "STATUS", "VISIBLE", "KEEP_ALIVE", "BREADCRUMB", "REMARK", "DELETED", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "IS_FRAME", "IS_CACHE", "QUERY", "ENTITY_CODE", "RESOURCE_TYPE", "LIST_KEY") VALUES ('work_calendar_publish_perm_001','work_calendar_menu_001','发布工作日历','F',NULL,2,'','','system:work-calendar:publish','0','0','0','1',NULL,0,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,'0','0','',NULL,NULL,NULL);
INSERT INTO "SYS_POSITION" ("ID", "POSITION_CODE", "POSITION_NAME", "APPLICABLE_UNIT_TYPE", "HOLDER_MODE", "BUILT_IN", "STATUS", "SORT_ORDER", "DESCRIPTION", "REVISION", "CREATED_BY", "UPDATED_BY", "CREATE_TIME", "UPDATE_TIME", "DELETED") VALUES ('position_unit_leader_001','UNIT_LEADER','负责人','ANY','SINGLE',1,'ENABLED',10,'组织或部门负责人；旧 leader 字段的权威任职来源',1,'migration-v069','migration-v069',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0);
INSERT INTO "SYS_ROLE" ("ID", "ROLE_NAME", "ROLE_CODE", "DESCRIPTION", "SORT", "STATUS", "CREATE_TIME", "UPDATE_TIME", "DELETED", "SORT_ORDER") VALUES ('1','已更新角色','super_admin','更新后的描述',0,'0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('00e39edf5c50bf3c7a691e3abd740378','1','list_column_template_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('03289d58b1fc7178ff46d6dff1476e9d','1','position_assign_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('0738c3d0253bc328872fbb37bf678ea2','1','global_settings_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('08122bbb3eea6a2141bc5b2c0a0a65f7','1','work_calendar_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('09d1a148efc935e59f85cff8e0077917','1','security_perm_menu_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('0a446aa774fce4e90e322621695b54ab','1','external_system_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('0fd6e9fdba7cdf617d1f50011543ca8e','1','position_manage_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('1354fb8ab53c1ed349577b7ba14226e9','1','security_perm_role_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('149312798634ce6f7a0f1bae0b45ebe9','1','security_perm_entity_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('18a946f6332387169ab762dbc3720556','1','security_perm_user_reset',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('1dec8f0ec49c31c224d02337a721ca9b','1','security_perm_org_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054120787970','1','300',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054129176579','1','400',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054141759490','1','403',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054158536706','1','config_migration_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054162731010','1','config_migration_list_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054166925314','1','config_migration_export_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054171119618','1','config_migration_download_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054175313922','1','config_migration_import_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054179508226','1','config_migration_analyze_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054183702529','1','config_migration_publish_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054187896834','1','config_migration_rollback_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054192091137','1','dev_guide_dir',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054192091138','1','dev_guide_list',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054196285441','1','flow_setting_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054196285442','1','flow_action_guide_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054200479745','1','custom_list_guide',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054208868353','1','custom_form_guide',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054213062657','1','user_manual_dir_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054217256961','1','user_manual_entity_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2080586054221451265','1','user_manual_process_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2645fafb4cb2d4ab1718a522021b88ee','1','embed_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('265953de88d211f1b9e8f79d2d09a523','1','extension_list_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('265955b488d211f1b9e8f79d2d09a523','1','extension_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2659562288d211f1b9e8f79d2d09a523','1','extension_test_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2659568688d211f1b9e8f79d2d09a523','1','extension_update_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2b58ccf45f7952af77d9c628903ea6d3','1','sla_management_dir_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2babeffb7ff59b9f58563d683709f417','1','security_perm_user_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('2fb4ac5920a5efb0e15a9243b154eb1a','1','security_perm_dict_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('30c5b440794425d37f8b4fd1505d5c13','1','assignee_incident_handle_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('328867562617bb929d1481789903f792','1','external_system_view_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('34e3f2279ce59857ed96eab6ca8917aa','1','security_perm_entity_publish',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('4876190b55d2da7be050d12676b793bda59ae3e9a68300f3ef858f3e91e1b120','1','entity_version_record_view_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('48ae6519ae82ea9129f204ec89ea3377','1','global_settings_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('4e62eabc457af7dcdc56ca45c430e4d6','1','integration_perm_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('4fdaa562a5bc238a088ebdd1d789154d','1','security_perm_process_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('50420884b78cfa827cc3ab9f38e0512c','1','task_sla_policy_publish_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('5247a13fc8e62351a49d983553d2f7f5','1','work_calendar_manage_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('525610baf86ec1e3adeac8a92aba7a8d','1','security_perm_process_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('546a07e11c06c471218e654589401463','1','position_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('55feae72f9d8990c5baad24327202c9b66f354c061bb8fa12b9ce73919d7ea60','1','entity_version_record_capture_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('5db73118888f11f1a02e52aa5ed9252f','1','system_audit_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('5fd0a378842fbd966c85b20284dc95b9','1','security_perm_role_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('60c5b627c72a07be37c86100c55c5ffd','1','integration_management_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('629d8bdc88f561028367b9db30a06f24','1','entity_version_management_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('6a9d1a1a3a1f1690896b5d6afc27dd09','1','work_calendar_publish_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('6f72a8c786ff1f30d5a03784b1db7342','1','user_manual_embed_integration_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('73437b56888811f1a02e52aa5ed9252f','1','system_audit_detail_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('73438326888811f1a02e52aa5ed9252f','1','system_audit_export_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('73438452888811f1a02e52aa5ed9252f','1','system_audit_list_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('77274acfbca78ed28e32315ff1e8bfe9','1','global_settings_menu',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('774ad263cdffd3a42e880733db29d618','1','security_perm_user_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('77a93d3f2f55b8d29cb9787ef3dee1e5','1','entity_scope_explicit_all_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('77ca7cb65b7f2138c812aa04bea1000b','1','integration_perm_secret_rotate',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('7a932ae42063ffa3a0568ca6b02fa3b1','1','assignee_incident_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('7f2db854d19c52af67b69b54ff08d5e7','1','list_column_template_manage_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('80b73a9fa2b2a876bfe18a0e0fa92570','1','security_perm_process_signal',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('83762738c6a8df0fc9f6265ecf170827','1','security_perm_entity_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('848bb511a33540d3afd1a32290a09cac','1','external_system_manage_permission_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('85de4afcadd2f28ed310d60560c5f833','1','task_sla_monitor_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('891042c416d95c26ec89827cfc2cb595','1','security_perm_process_publish',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('92606bb7c82d2688538c3d9aef521190','1','entity_version_config_update_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('9965553b538c05de8ed97b0f8a505711','1','security_perm_file_delete',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('a3759a57d5a664580a13c93dd46a4dc2','1','security_perm_dict_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('a7f105a87027044cb40d5d2286e2a529','1','task_sla_policy_menu_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('ad752fac598351a4b7aff641a592c0c9','1','embed_perm_publish',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('b0d591c126e47108bfe48b6cce23f906','1','integration_perm_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('b3e0385475f59111486b8ee903d983e7','1','embed_perm_session_revoke',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('bfc91a13fe9e230578fdb666565e32f9','1','task_sla_policy_manage_perm_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('c4e997351779bfbfe41190a34cb64a59','1','embed_perm_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('c7efe78a77b873a80d7695bdaa622de4','1','security_perm_menu_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('d8585f0e7fb4b0e1d5b65c17ec0d5a8d','1','entity_version_config_list_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('d8c7fae434846be9955701bca8e8b82c','1','security_perm_file_read',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('dec00d4a36f9fc8cc09c13eee43bf5dc','1','user_manual_quick_start_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('e079416319a588aeec38398f930c1ce5','1','user_manual_open_integration_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('f2ddc5d96a9b707f7f7987bbe00a7b24','1','entity_version_config_publish_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('f4df0f29d3b790164aec97b3fd36d645','1','list_field_guide_v2_001',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('f6f6487da585d22f311fb7cab03006ea','1','security_perm_file_write',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('f7502878d9a1bf8273184abc648a8e14','1','security_perm_org_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('fa85456c4c2efdd7e4e03b9cddce27a7','1','embed_perm_identity_manage',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('fca60e3e87e011f1a02e52aa5ed9252f','1','entity_ui_hotfix_publish_permission',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('fca636d487e011f1a02e52aa5ed9252f','1','entity_ui_hotfix_override_permission',CURRENT_TIMESTAMP);
INSERT INTO "SYS_ROLE_MENU" ("ID", "ROLE_ID", "MENU_ID", "CREATE_TIME") VALUES ('fffc1112adfbecab7f3f747beab1ad10','1','embed_perm_view',CURRENT_TIMESTAMP);
INSERT INTO "SYS_GLOBAL_SETTING" ("ID", "SCOPE_TYPE", "OWNER_ID", "SETTING_KEY", "NAME", "SETTING_VALUE_TYPE", "SETTING_VALUE", "REMARK") VALUES ('setting_migration_signing_key', 'SYSTEM', '0', 'config.migration.signing_key', '配置迁移签名密钥', 'STRING', '"' || LOWER(RAWTOHEX(SYS_GUID()) || RAWTOHEX(SYS_GUID())) || '"', '用于迁移包 HMAC-SHA256 签名与验签；每个新库独立生成，不应跨库复用。');
INSERT INTO "SYS_USER" ("ID", "USERNAME", "NICKNAME", "PASSWORD", "EMAIL", "PHONE", "AVATAR", "STATUS", "CREATE_TIME", "UPDATE_TIME", "DELETED", "ORG_ID", "DEPT_ID", "PASSWORD_RESET_REQUIRED", "TOKEN_VERSION") VALUES ('1', 'admin', '超级管理员', '$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6', 'admin@workflow.com', NULL, NULL, '1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, NULL, NULL, 1, 0);
INSERT INTO "SYS_USER_ROLE" ("ID", "USER_ID", "ROLE_ID", "CREATE_TIME") VALUES ('bootstrap_admin_role_001', '1', '1', CURRENT_TIMESTAMP);

-- 初始数据载入后建立外键，避免跨表种子数据的顺序依赖。
ALTER TABLE "ACT_APP_APPDEF" ADD CONSTRAINT "FK_ACT_APP_APPDEF_ACT_563ADE7E" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "ACT_APP_DEPLOYMENT" ("ID_");
ALTER TABLE "ACT_APP_DEPLOYMENT_RESOURCE" ADD CONSTRAINT "FK_ACT_APP_DEPLOYMENT_3AA2366B" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "ACT_APP_DEPLOYMENT" ("ID_");
ALTER TABLE "ACT_CMMN_CASEDEF" ADD CONSTRAINT "FK_ACT_CMMN_CASEDEF_A_6F64F5A3" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "ACT_CMMN_DEPLOYMENT" ("ID_");
ALTER TABLE "ACT_CMMN_DEPLOYMENT_RESOURCE" ADD CONSTRAINT "FK_ACT_CMMN_DEPLOYMEN_CDA75FAE" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "ACT_CMMN_DEPLOYMENT" ("ID_");
ALTER TABLE "ACT_CMMN_RU_CASE_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_CASE_I_F20A2E1A" FOREIGN KEY ("CASE_DEF_ID_") REFERENCES "ACT_CMMN_CASEDEF" ("ID_");
ALTER TABLE "ACT_CMMN_RU_MIL_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_MIL_IN_7F7A7BE1" FOREIGN KEY ("CASE_DEF_ID_") REFERENCES "ACT_CMMN_CASEDEF" ("ID_");
ALTER TABLE "ACT_CMMN_RU_MIL_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_MIL_IN_C50F7054" FOREIGN KEY ("CASE_INST_ID_") REFERENCES "ACT_CMMN_RU_CASE_INST" ("ID_");
ALTER TABLE "ACT_CMMN_RU_PLAN_ITEM_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_PLAN_I_1809BACC" FOREIGN KEY ("CASE_DEF_ID_") REFERENCES "ACT_CMMN_CASEDEF" ("ID_");
ALTER TABLE "ACT_CMMN_RU_PLAN_ITEM_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_PLAN_I_60B55BED" FOREIGN KEY ("CASE_INST_ID_") REFERENCES "ACT_CMMN_RU_CASE_INST" ("ID_");
ALTER TABLE "ACT_CMMN_RU_SENTRY_PART_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_SENTRY_24F7B2C5" FOREIGN KEY ("CASE_DEF_ID_") REFERENCES "ACT_CMMN_CASEDEF" ("ID_");
ALTER TABLE "ACT_CMMN_RU_SENTRY_PART_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_SENTRY_57EB0A9E" FOREIGN KEY ("CASE_INST_ID_") REFERENCES "ACT_CMMN_RU_CASE_INST" ("ID_");
ALTER TABLE "ACT_CMMN_RU_SENTRY_PART_INST" ADD CONSTRAINT "FK_ACT_CMMN_RU_SENTRY_1A95FE70" FOREIGN KEY ("PLAN_ITEM_INST_ID_") REFERENCES "ACT_CMMN_RU_PLAN_ITEM_INST" ("ID_");
ALTER TABLE "ACT_DMN_DEPLOYMENT_RESOURCE" ADD CONSTRAINT "FK_ACT_DMN_DEPLOYMENT_DB70EE34" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "ACT_DMN_DEPLOYMENT" ("ID_");
ALTER TABLE "ACT_GE_BYTEARRAY" ADD CONSTRAINT "FK_ACT_GE_BYTEARRAY_A_644247DE" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "ACT_RE_DEPLOYMENT" ("ID_");
ALTER TABLE "ACT_ID_MEMBERSHIP" ADD CONSTRAINT "FK_ACT_ID_MEMBERSHIP__515E1E5C" FOREIGN KEY ("GROUP_ID_") REFERENCES "ACT_ID_GROUP" ("ID_");
ALTER TABLE "ACT_ID_MEMBERSHIP" ADD CONSTRAINT "FK_ACT_ID_MEMBERSHIP__7B189513" FOREIGN KEY ("USER_ID_") REFERENCES "ACT_ID_USER" ("ID_");
ALTER TABLE "ACT_ID_PRIV_MAPPING" ADD CONSTRAINT "FK_ACT_ID_PRIV_MAPPIN_42C49366" FOREIGN KEY ("PRIV_ID_") REFERENCES "ACT_ID_PRIV" ("ID_");
ALTER TABLE "ACT_PROCDEF_INFO" ADD CONSTRAINT "FK_ACT_PROCDEF_INFO_A_A60D199D" FOREIGN KEY ("INFO_JSON_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_PROCDEF_INFO" ADD CONSTRAINT "FK_ACT_PROCDEF_INFO_A_38F4CA07" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RE_MODEL" ADD CONSTRAINT "FK_ACT_RE_MODEL_ACT_F_518DE067" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "ACT_RE_DEPLOYMENT" ("ID_");
ALTER TABLE "ACT_RE_MODEL" ADD CONSTRAINT "FK_ACT_RE_MODEL_ACT_F_DE4331FF" FOREIGN KEY ("EDITOR_SOURCE_VALUE_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RE_MODEL" ADD CONSTRAINT "FK_ACT_RE_MODEL_ACT_F_F9B181A2" FOREIGN KEY ("EDITOR_SOURCE_EXTRA_VALUE_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_DEADLETTER_JOB" ADD CONSTRAINT "FK_ACT_RU_DEADLETTER__78002A66" FOREIGN KEY ("CUSTOM_VALUES_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_DEADLETTER_JOB" ADD CONSTRAINT "FK_ACT_RU_DEADLETTER__11C70D3E" FOREIGN KEY ("EXCEPTION_STACK_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_DEADLETTER_JOB" ADD CONSTRAINT "FK_ACT_RU_DEADLETTER__274880EE" FOREIGN KEY ("EXECUTION_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_DEADLETTER_JOB" ADD CONSTRAINT "FK_ACT_RU_DEADLETTER__51E5481D" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RU_DEADLETTER_JOB" ADD CONSTRAINT "FK_ACT_RU_DEADLETTER__B0C487E8" FOREIGN KEY ("PROCESS_INSTANCE_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_EVENT_SUBSCR" ADD CONSTRAINT "FK_ACT_RU_EVENT_SUBSC_70F02A37" FOREIGN KEY ("EXECUTION_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_EXECUTION" ADD CONSTRAINT "FK_ACT_RU_EXECUTION_A_4E8CF531" FOREIGN KEY ("PARENT_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_") ON DELETE CASCADE;
ALTER TABLE "ACT_RU_EXECUTION" ADD CONSTRAINT "FK_ACT_RU_EXECUTION_A_F884D514" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RU_EXECUTION" ADD CONSTRAINT "FK_ACT_RU_EXECUTION_A_E609EFC7" FOREIGN KEY ("PROC_INST_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_") ON DELETE CASCADE;
ALTER TABLE "ACT_RU_EXECUTION" ADD CONSTRAINT "FK_ACT_RU_EXECUTION_A_66D79015" FOREIGN KEY ("SUPER_EXEC_") REFERENCES "ACT_RU_EXECUTION" ("ID_") ON DELETE CASCADE;
ALTER TABLE "ACT_RU_EXTERNAL_JOB" ADD CONSTRAINT "FK_ACT_RU_EXTERNAL_JO_713D265C" FOREIGN KEY ("CUSTOM_VALUES_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_EXTERNAL_JOB" ADD CONSTRAINT "FK_ACT_RU_EXTERNAL_JO_C0A379D0" FOREIGN KEY ("EXCEPTION_STACK_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_IDENTITYLINK" ADD CONSTRAINT "FK_ACT_RU_IDENTITYLIN_DE26768F" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RU_IDENTITYLINK" ADD CONSTRAINT "FK_ACT_RU_IDENTITYLIN_F4978EBA" FOREIGN KEY ("PROC_INST_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_IDENTITYLINK" ADD CONSTRAINT "FK_ACT_RU_IDENTITYLIN_29742545" FOREIGN KEY ("TASK_ID_") REFERENCES "ACT_RU_TASK" ("ID_");
ALTER TABLE "ACT_RU_JOB" ADD CONSTRAINT "FK_ACT_RU_JOB_ACT_FK__0C304C7F" FOREIGN KEY ("CUSTOM_VALUES_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_JOB" ADD CONSTRAINT "FK_ACT_RU_JOB_ACT_FK__86D4C258" FOREIGN KEY ("EXCEPTION_STACK_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_JOB" ADD CONSTRAINT "FK_ACT_RU_JOB_ACT_FK__942B6E3E" FOREIGN KEY ("EXECUTION_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_JOB" ADD CONSTRAINT "FK_ACT_RU_JOB_ACT_FK__F09DA815" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RU_JOB" ADD CONSTRAINT "FK_ACT_RU_JOB_ACT_FK__615E6B2C" FOREIGN KEY ("PROCESS_INSTANCE_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_SUSPENDED_JOB" ADD CONSTRAINT "FK_ACT_RU_SUSPENDED_J_2E8B3451" FOREIGN KEY ("CUSTOM_VALUES_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_SUSPENDED_JOB" ADD CONSTRAINT "FK_ACT_RU_SUSPENDED_J_DD233636" FOREIGN KEY ("EXCEPTION_STACK_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_SUSPENDED_JOB" ADD CONSTRAINT "FK_ACT_RU_SUSPENDED_J_67E8F8B6" FOREIGN KEY ("EXECUTION_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_SUSPENDED_JOB" ADD CONSTRAINT "FK_ACT_RU_SUSPENDED_J_4E193404" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RU_SUSPENDED_JOB" ADD CONSTRAINT "FK_ACT_RU_SUSPENDED_J_5BE406F5" FOREIGN KEY ("PROCESS_INSTANCE_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_TASK" ADD CONSTRAINT "FK_ACT_RU_TASK_ACT_FK_TASK_EXE" FOREIGN KEY ("EXECUTION_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_TASK" ADD CONSTRAINT "FK_ACT_RU_TASK_ACT_FK_7B0D93CD" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RU_TASK" ADD CONSTRAINT "FK_ACT_RU_TASK_ACT_FK_DBC121D9" FOREIGN KEY ("PROC_INST_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_TIMER_JOB" ADD CONSTRAINT "FK_ACT_RU_TIMER_JOB_A_92790297" FOREIGN KEY ("CUSTOM_VALUES_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_TIMER_JOB" ADD CONSTRAINT "FK_ACT_RU_TIMER_JOB_A_CB2819B7" FOREIGN KEY ("EXCEPTION_STACK_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_TIMER_JOB" ADD CONSTRAINT "FK_ACT_RU_TIMER_JOB_A_CC204CF1" FOREIGN KEY ("EXECUTION_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_TIMER_JOB" ADD CONSTRAINT "FK_ACT_RU_TIMER_JOB_A_3B24F6E7" FOREIGN KEY ("PROC_DEF_ID_") REFERENCES "ACT_RE_PROCDEF" ("ID_");
ALTER TABLE "ACT_RU_TIMER_JOB" ADD CONSTRAINT "FK_ACT_RU_TIMER_JOB_A_7337A41B" FOREIGN KEY ("PROCESS_INSTANCE_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_VARIABLE" ADD CONSTRAINT "FK_ACT_RU_VARIABLE_AC_61B34AAF" FOREIGN KEY ("BYTEARRAY_ID_") REFERENCES "ACT_GE_BYTEARRAY" ("ID_");
ALTER TABLE "ACT_RU_VARIABLE" ADD CONSTRAINT "FK_ACT_RU_VARIABLE_AC_E1195BEF" FOREIGN KEY ("EXECUTION_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "ACT_RU_VARIABLE" ADD CONSTRAINT "FK_ACT_RU_VARIABLE_AC_E1C7CDDF" FOREIGN KEY ("PROC_INST_ID_") REFERENCES "ACT_RU_EXECUTION" ("ID_");
ALTER TABLE "EMBED_ALLOWED_ORIGIN" ADD CONSTRAINT "FK_EMBED_ALLOWED_ORIG_4B5A25DB" FOREIGN KEY ("GRANT_ID") REFERENCES "EMBED_APPLICATION_GRANT" ("ID");
ALTER TABLE "EMBED_APPLICATION_GRANT" ADD CONSTRAINT "FK_EMBED_APPLICATION__C67101FE" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID");
ALTER TABLE "EMBED_APPLICATION_GRANT" ADD CONSTRAINT "FK_EMBED_APPLICATION__E4155FB0" FOREIGN KEY ("IDENTITY_PROVIDER_ID") REFERENCES "EMBED_IDENTITY_PROVIDER" ("ID");
ALTER TABLE "EMBED_APPLICATION_GRANT" ADD CONSTRAINT "FK_EMBED_APPLICATION__1CCB8CC0" FOREIGN KEY ("VIEW_ID") REFERENCES "EMBED_VIEW" ("ID");
ALTER TABLE "EMBED_ASSERTION_REPLAY" ADD CONSTRAINT "FK_EMBED_ASSERTION_RE_7D9D30B6" FOREIGN KEY ("PROVIDER_ID") REFERENCES "EMBED_IDENTITY_PROVIDER" ("ID");
ALTER TABLE "EMBED_EXTERNAL_IDENTITY_BINDING" ADD CONSTRAINT "FK_EMBED_EXTERNAL_IDE_EDE1D8EF" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID");
ALTER TABLE "EMBED_EXTERNAL_IDENTITY_BINDING" ADD CONSTRAINT "FK_EMBED_EXTERNAL_IDE_073AE45C" FOREIGN KEY ("FLOW_USER_ID") REFERENCES "SYS_USER" ("ID");
ALTER TABLE "EMBED_EXTERNAL_IDENTITY_BINDING" ADD CONSTRAINT "FK_EMBED_EXTERNAL_IDE_DF1A61F8" FOREIGN KEY ("IDENTITY_PROVIDER_ID") REFERENCES "EMBED_IDENTITY_PROVIDER" ("ID");
ALTER TABLE "EMBED_LAUNCH" ADD CONSTRAINT "FK_EMBED_LAUNCH_FK_EM_90198CB7" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID");
ALTER TABLE "EMBED_LAUNCH" ADD CONSTRAINT "FK_EMBED_LAUNCH_FK_EM_C36932D7" FOREIGN KEY ("IDENTITY_BINDING_ID") REFERENCES "EMBED_EXTERNAL_IDENTITY_BINDING" ("ID");
ALTER TABLE "EMBED_LAUNCH" ADD CONSTRAINT "FK_EMBED_LAUNCH_FK_EM_023D3CD9" FOREIGN KEY ("FLOW_USER_ID") REFERENCES "SYS_USER" ("ID");
ALTER TABLE "EMBED_LAUNCH" ADD CONSTRAINT "FK_EMBED_LAUNCH_FK_EM_AEB844ED" FOREIGN KEY ("GRANT_ID") REFERENCES "EMBED_APPLICATION_GRANT" ("ID");
ALTER TABLE "EMBED_LAUNCH" ADD CONSTRAINT "FK_EMBED_LAUNCH_FK_EM_34118AD3" FOREIGN KEY ("IDENTITY_PROVIDER_ID") REFERENCES "EMBED_IDENTITY_PROVIDER" ("ID");
ALTER TABLE "EMBED_LAUNCH" ADD CONSTRAINT "FK_EMBED_LAUNCH_FK_EM_91A344B6" FOREIGN KEY ("VIEW_RELEASE_ID") REFERENCES "EMBED_VIEW_RELEASE" ("ID");
ALTER TABLE "EMBED_LAUNCH" ADD CONSTRAINT "FK_EMBED_LAUNCH_FK_EM_E233432E" FOREIGN KEY ("VIEW_ID") REFERENCES "EMBED_VIEW" ("ID");
ALTER TABLE "EMBED_OPERATION_RECEIPT" ADD CONSTRAINT "FK_EMBED_OPERATION_RE_EEA75022" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_5ACBF0A3" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_9AE74319" FOREIGN KEY ("IDENTITY_BINDING_ID") REFERENCES "EMBED_EXTERNAL_IDENTITY_BINDING" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_2946FD25" FOREIGN KEY ("FLOW_USER_ID") REFERENCES "SYS_USER" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_1808ACE9" FOREIGN KEY ("GRANT_ID") REFERENCES "EMBED_APPLICATION_GRANT" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_E90230D1" FOREIGN KEY ("LAUNCH_ID") REFERENCES "EMBED_LAUNCH" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_5B995E47" FOREIGN KEY ("IDENTITY_PROVIDER_ID") REFERENCES "EMBED_IDENTITY_PROVIDER" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_427B0787" FOREIGN KEY ("VIEW_RELEASE_ID") REFERENCES "EMBED_VIEW_RELEASE" ("ID");
ALTER TABLE "EMBED_SESSION" ADD CONSTRAINT "FK_EMBED_SESSION_FK_E_4EED505F" FOREIGN KEY ("VIEW_ID") REFERENCES "EMBED_VIEW" ("ID");
ALTER TABLE "EMBED_SESSION_COUNTER" ADD CONSTRAINT "FK_EMBED_SESSION_COUN_13C48FA8" FOREIGN KEY ("GRANT_ID") REFERENCES "EMBED_APPLICATION_GRANT" ("ID");
ALTER TABLE "EMBED_SESSION_COUNTER" ADD CONSTRAINT "FK_EMBED_SESSION_COUN_7714B061" FOREIGN KEY ("FLOW_USER_ID") REFERENCES "SYS_USER" ("ID");
ALTER TABLE "EMBED_VIEW_RELEASE" ADD CONSTRAINT "FK_EMBED_VIEW_RELEASE_C1F0E557" FOREIGN KEY ("VIEW_ID") REFERENCES "EMBED_VIEW" ("ID");
ALTER TABLE "ENTITY_RECORD_VERSION" ADD CONSTRAINT "FK_ENTITY_RECORD_VERS_C41B5BDC" FOREIGN KEY ("CONFIG_RELEASE_ID") REFERENCES "ENTITY_VERSION_CONFIG_RELEASE" ("ID");
ALTER TABLE "ENTITY_RECORD_VERSION_DATASET" ADD CONSTRAINT "FK_ENTITY_RECORD_VERS_2FE2997C" FOREIGN KEY ("VERSION_ID") REFERENCES "ENTITY_RECORD_VERSION" ("ID") ON DELETE CASCADE;
ALTER TABLE "ENTITY_RECORD_VERSION_DATASET_ROW" ADD CONSTRAINT "FK_ENTITY_RECORD_VERS_F2633DB6" FOREIGN KEY ("DATASET_ID") REFERENCES "ENTITY_RECORD_VERSION_DATASET" ("ID") ON DELETE CASCADE;
ALTER TABLE "ENTITY_SCHEMA_OPERATION_EVENT" ADD CONSTRAINT "FK_ENTITY_SCHEMA_OPER_5709BB1D" FOREIGN KEY ("OPERATION_ID") REFERENCES "ENTITY_SCHEMA_OPERATION" ("ID") ON DELETE CASCADE;
ALTER TABLE "FLW_EVENT_RESOURCE" ADD CONSTRAINT "FK_FLW_EVENT_RESOURCE_4EFD66C4" FOREIGN KEY ("DEPLOYMENT_ID_") REFERENCES "FLW_EVENT_DEPLOYMENT" ("ID_");
ALTER TABLE "FLW_RU_BATCH_PART" ADD CONSTRAINT "FK_FLW_RU_BATCH_PART__72C4C9A8" FOREIGN KEY ("BATCH_ID_") REFERENCES "FLW_RU_BATCH" ("ID_");
ALTER TABLE "INTEGRATION_API_REQUEST_LEASE" ADD CONSTRAINT "FK_INTEGRATION_API_RE_8AB99D33" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID") ON DELETE CASCADE;
ALTER TABLE "INTEGRATION_APPLICATION_CREDENTIAL" ADD CONSTRAINT "FK_INTEGRATION_APPLIC_6FC270F5" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID");
ALTER TABLE "INTEGRATION_IDEMPOTENCY_RECORD" ADD CONSTRAINT "FK_INTEGRATION_IDEMPO_2D50F863" FOREIGN KEY ("APPLICATION_ID") REFERENCES "INTEGRATION_APPLICATION" ("ID");
ALTER TABLE "SYS_EXTERNAL_SYSTEM_PARAMETER" ADD CONSTRAINT "FK_SYS_EXTERNAL_SYSTE_B377C32E" FOREIGN KEY ("EXTERNAL_SYSTEM_ID") REFERENCES "SYS_EXTERNAL_SYSTEM" ("ID");
ALTER TABLE "SYS_POSITION_ASSIGNMENT" ADD CONSTRAINT "FK_SYS_POSITION_ASSIG_AEF062B0" FOREIGN KEY ("ORGANIZATION_UNIT_ID") REFERENCES "SYS_ORGANIZATION" ("ID");
ALTER TABLE "SYS_POSITION_ASSIGNMENT" ADD CONSTRAINT "FK_SYS_POSITION_ASSIG_66555C9E" FOREIGN KEY ("POSITION_ID") REFERENCES "SYS_POSITION" ("ID");
ALTER TABLE "SYS_POSITION_ASSIGNMENT" ADD CONSTRAINT "FK_SYS_POSITION_ASSIG_CEB06C81" FOREIGN KEY ("USER_ID") REFERENCES "SYS_USER" ("ID");
ALTER TABLE "SYS_POSITION_ASSIGNMENT_BATCH" ADD CONSTRAINT "FK_SYS_POSITION_ASSIG_EB5EFE6A" FOREIGN KEY ("CREATED_BY") REFERENCES "SYS_USER" ("ID");
ALTER TABLE "UI_HOTFIX_OBSERVATION_METRIC" ADD CONSTRAINT "FK_UI_HOTFIX_OBSERVAT_8B4149E6" FOREIGN KEY ("REQUEST_ID") REFERENCES "UI_CONFIG_HOTFIX_REQUEST" ("ID") ON DELETE CASCADE;

-- MySQL ON UPDATE 的字段在本方言中由行级触发器维护。
CREATE OR REPLACE TRIGGER "TR_CONFIG_ASSET_BASEL_AB1141E9"
BEFORE UPDATE ON "CONFIG_ASSET_BASELINE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_CONFIG_ENVIRONMENT_C3ED2843"
BEFORE UPDATE ON "CONFIG_ENVIRONMENT_MAPPING" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_CONFIG_IMPORT_ITEM_A13CB4C7"
BEFORE UPDATE ON "CONFIG_IMPORT_ITEM" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_CONFIG_MIGRATION_A_300F837C"
BEFORE UPDATE ON "CONFIG_MIGRATION_ASSET" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_APPLICATION__B9C6F926"
BEFORE UPDATE ON "EMBED_APPLICATION_GRANT" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_ASSERTION_RE_5C4DC665"
BEFORE UPDATE ON "EMBED_ASSERTION_REPLAY" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_EXTERNAL_IDE_DCA73660"
BEFORE UPDATE ON "EMBED_EXTERNAL_IDENTITY_BINDING" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_IDENTITY_PRO_D26BF4DC"
BEFORE UPDATE ON "EMBED_IDENTITY_PROVIDER" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_LAUNCH_UPDATE_TIME"
BEFORE UPDATE ON "EMBED_LAUNCH" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_SESSION_UPDATE_TIME"
BEFORE UPDATE ON "EMBED_SESSION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_SESSION_COUN_9196C40B"
BEFORE UPDATE ON "EMBED_SESSION_COUNTER" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_VIEW_UPDATE_TIME"
BEFORE UPDATE ON "EMBED_VIEW" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_EMBED_VIEW_RELEASE_C8EA4B06"
BEFORE UPDATE ON "EMBED_VIEW_RELEASE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_CODE_RULE_U_BD0FA242"
BEFORE UPDATE ON "ENTITY_CODE_RULE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_DEFINITION__9E0BEA67"
BEFORE UPDATE ON "ENTITY_DEFINITION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_FIELD_UPDATE_TIME"
BEFORE UPDATE ON "ENTITY_FIELD" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_FIELD_FILE__0FE2B8F1"
BEFORE UPDATE ON "ENTITY_FIELD_FILE_ITEM" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_FIELD_OPTIO_049BEFB9"
BEFORE UPDATE ON "ENTITY_FIELD_OPTION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_FORM_UPDATE_TIME"
BEFORE UPDATE ON "ENTITY_FORM" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_FORM_NODE_U_3B1744DD"
BEFORE UPDATE ON "ENTITY_FORM_NODE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_FORM_UNIQUE_D165F016"
BEFORE UPDATE ON "ENTITY_FORM_UNIQUE_CLAIM" FOR EACH ROW
BEGIN
  IF :NEW."UPDATED_TIME" = :OLD."UPDATED_TIME" OR (:NEW."UPDATED_TIME" IS NULL AND :OLD."UPDATED_TIME" IS NULL) THEN
    :NEW."UPDATED_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_FORM_UNIQUE_1F98C817"
BEFORE UPDATE ON "ENTITY_FORM_UNIQUE_VALUE_GATE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATED_TIME" = :OLD."UPDATED_TIME" OR (:NEW."UPDATED_TIME" IS NULL AND :OLD."UPDATED_TIME" IS NULL) THEN
    :NEW."UPDATED_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_LIST_ACTION_7006388D"
BEFORE UPDATE ON "ENTITY_LIST_ACTION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_LIST_CONFIG_CD43249C"
BEFORE UPDATE ON "ENTITY_LIST_CONFIG" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_LIST_FIELD__9C5E4401"
BEFORE UPDATE ON "ENTITY_LIST_FIELD" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_LIST_SCOPE__18D08F1E"
BEFORE UPDATE ON "ENTITY_LIST_SCOPE_BINDING" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_LIST_SCOPE__B288DD52"
BEFORE UPDATE ON "ENTITY_LIST_SCOPE_DELEGATION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_LIST_SCOPE__8E3571C0"
BEFORE UPDATE ON "ENTITY_LIST_SCOPE_POLICY" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_MUTATION_RE_28B8AF1A"
BEFORE UPDATE ON "ENTITY_MUTATION_RECEIPT" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_RECORD_VERS_34B32ED4"
BEFORE UPDATE ON "ENTITY_RECORD_VERSION_COUNTER" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_RELATION_UPDATE_TIME"
BEFORE UPDATE ON "ENTITY_RELATION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_SCHEMA_OPER_7CDFFF88"
BEFORE UPDATE ON "ENTITY_SCHEMA_OPERATION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_STATUS_UPDATE_TIME"
BEFORE UPDATE ON "ENTITY_STATUS" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_UNIQUE_VALU_A0056E6B"
BEFORE UPDATE ON "ENTITY_UNIQUE_VALUE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_ENTITY_VERSION_CON_AF3EC821"
BEFORE UPDATE ON "ENTITY_VERSION_CONFIG" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_INTEGRATION_API_RE_67807B82"
BEFORE UPDATE ON "INTEGRATION_API_REQUEST_LEASE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_INTEGRATION_APPLIC_173546D3"
BEFORE UPDATE ON "INTEGRATION_APPLICATION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_INTEGRATION_APPLIC_B90D05D9"
BEFORE UPDATE ON "INTEGRATION_APPLICATION_CREDENTIAL" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_INTEGRATION_IDEMPO_392EBB7D"
BEFORE UPDATE ON "INTEGRATION_IDEMPOTENCY_RECORD" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_INTEGRATION_RATE_L_125A960F"
BEFORE UPDATE ON "INTEGRATION_RATE_LIMIT_BUCKET" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_ACTION_UPDATE_TIME"
BEFORE UPDATE ON "PROCESS_ACTION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_ACTION_DEF_607A9853"
BEFORE UPDATE ON "PROCESS_ACTION_DEFINITION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_ASSIGNEE_I_1CA9802A"
BEFORE UPDATE ON "PROCESS_ASSIGNEE_INCIDENT" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_CC_RECORD__E9B56170"
BEFORE UPDATE ON "PROCESS_CC_RECORD" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_DEFINITION_36539419"
BEFORE UPDATE ON "PROCESS_DEFINITION_CONFIG" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_ENTITY_STA_601A673C"
BEFORE UPDATE ON "PROCESS_ENTITY_STATUS_MAPPING" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_FORM_CONFI_19069F46"
BEFORE UPDATE ON "PROCESS_FORM_CONFIG" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_FORM_FIELD_E385731A"
BEFORE UPDATE ON "PROCESS_FORM_FIELD_CONFIG" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_NODE_APPRO_0161DD19"
BEFORE UPDATE ON "PROCESS_NODE_APPROVAL" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_NODE_APPRO_6305A2FA"
BEFORE UPDATE ON "PROCESS_NODE_APPROVAL_OPTION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_NODE_ASSIG_1C1F5D4C"
BEFORE UPDATE ON "PROCESS_NODE_ASSIGNEE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_NODE_CONFI_B7C6C3C7"
BEFORE UPDATE ON "PROCESS_NODE_CONFIG" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_NODE_FORM__9AF62EF5"
BEFORE UPDATE ON "PROCESS_NODE_FORM" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_PERSON_RES_10F9DBE5"
BEFORE UPDATE ON "PROCESS_PERSON_RESOLVER_DEFINITION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_TASK_UPDATE_TIME"
BEFORE UPDATE ON "PROCESS_TASK" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_TASK_SLA_U_87D94FAB"
BEFORE UPDATE ON "PROCESS_TASK_SLA" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_TASK_SLA_E_E4D2FAFC"
BEFORE UPDATE ON "PROCESS_TASK_SLA_EVENT" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_TASK_SLA_P_AB4CED3A"
BEFORE UPDATE ON "PROCESS_TASK_SLA_PAUSE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_PROCESS_VERSION_HI_C27DF356"
BEFORE UPDATE ON "PROCESS_VERSION_HISTORY" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_DICT_UPDATE_TIME"
BEFORE UPDATE ON "SYS_DICT" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_DICT_ITEM_UPDATE_TIME"
BEFORE UPDATE ON "SYS_DICT_ITEM" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_EXTERNAL_SYSTE_D78C7323"
BEFORE UPDATE ON "SYS_EXTERNAL_SYSTEM" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_EXTERNAL_SYSTE_11D5B0F9"
BEFORE UPDATE ON "SYS_EXTERNAL_SYSTEM_PARAMETER" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_GLOBAL_SETTING_ABACBEFF"
BEFORE UPDATE ON "SYS_GLOBAL_SETTING" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_GROUP_UPDATE_TIME"
BEFORE UPDATE ON "SYS_GROUP" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_MENU_UPDATE_TIME"
BEFORE UPDATE ON "SYS_MENU" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_ORGANIZATION_U_7888FEBB"
BEFORE UPDATE ON "SYS_ORGANIZATION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_POSITION_UPDATE_TIME"
BEFORE UPDATE ON "SYS_POSITION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_POSITION_ASSIG_A2FB1F96"
BEFORE UPDATE ON "SYS_POSITION_ASSIGNMENT" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_ROLE_UPDATE_TIME"
BEFORE UPDATE ON "SYS_ROLE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_SYS_USER_UPDATE_TIME"
BEFORE UPDATE ON "SYS_USER" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_TASK_SLA_ESCALATIO_180D49DB"
BEFORE UPDATE ON "TASK_SLA_ESCALATION_STEP" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_TASK_SLA_POLICY_UPDATE_TIME"
BEFORE UPDATE ON "TASK_SLA_POLICY" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_UI_COMPONENT_TEMPL_E50E61D0"
BEFORE UPDATE ON "UI_COMPONENT_TEMPLATE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_UI_CONFIG_HOTFIX_R_EFC399C2"
BEFORE UPDATE ON "UI_CONFIG_HOTFIX_REQUEST" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_UI_EVENT_BINDING_U_6815E63D"
BEFORE UPDATE ON "UI_EVENT_BINDING" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_UI_EXTENSION_DEFIN_5E396603"
BEFORE UPDATE ON "UI_EXTENSION_DEFINITION" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_WORK_CALENDAR_UPDATE_TIME"
BEFORE UPDATE ON "WORK_CALENDAR" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_WORK_CALENDAR_BIND_976AE861"
BEFORE UPDATE ON "WORK_CALENDAR_BINDING" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
CREATE OR REPLACE TRIGGER "TR_WORKFLOW_SCHEMA_CH_6D784027"
BEFORE UPDATE ON "WORKFLOW_SCHEMA_CHANGE" FOR EACH ROW
BEGIN
  IF :NEW."UPDATE_TIME" = :OLD."UPDATE_TIME" OR (:NEW."UPDATE_TIME" IS NULL AND :OLD."UPDATE_TIME" IS NULL) THEN
    :NEW."UPDATE_TIME" := CURRENT_TIMESTAMP;
  END IF;
END;
/
